package httpapi

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"reflect"
	"strings"

	"github.com/swaggest/jsonschema-go"
	"github.com/swaggest/openapi-go"
	"github.com/swaggest/openapi-go/openapi31"
)

// WriteOpenAPI writes the OpenAPI document for this API.
func WriteOpenAPI(path string) error {
	spec, err := openAPI()
	if err != nil {
		return err
	}
	data, err := spec.MarshalYAML()
	if err != nil {
		return fmt.Errorf("marshal openapi: %w", err)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return fmt.Errorf("create contract directory: %w", err)
	}
	if err := os.WriteFile(path, data, 0o644); err != nil {
		return fmt.Errorf("write openapi: %w", err)
	}
	return nil
}

type response struct {
	status int
	body   any
	text   string
}

type operation struct {
	method      string
	path        string
	id          string
	summary     string
	description string
	auth        bool
	body        any
	responses   []response
}

func openAPI() (*openapi31.Spec, error) {
	reflector := openapi31.NewReflector()
	reflector.DefaultOptions = append(reflector.DefaultOptions,
		jsonschema.InterceptDefName(func(_ reflect.Type, name string) string {
			return strings.TrimPrefix(name, "Httpapi")
		}),
		jsonschema.InterceptSchema(func(params jsonschema.InterceptSchemaParams) (bool, error) {
			valueType := params.Value.Type()
			if valueType.Kind() == reflect.Pointer {
				valueType = valueType.Elem()
			}
			if valueType == reflect.TypeOf(OTPSuccess{}) {
				params.Schema.Type = nil
			}
			return false, nil
		}),
	)
	reflector.Spec.Info.
		WithTitle("Messenger API").
		WithVersion("1.0.0").
		WithDescription("HTTP API for the Messenger app. Protected routes require an access token.")
	reflector.Spec.SetHTTPBearerTokenSecurity("bearerAuth", "JWT", "Access token from login, verify, or refresh.")

	ops := []operation{
		{
			method: http.MethodGet, path: "/health", id: "getHealth",
			summary:     "Check API health",
			description: "Reports whether the API can reach its database.",
			responses: []response{
				{http.StatusOK, new(Health), "The API is ready."},
				{http.StatusServiceUnavailable, new(Health), "The API cannot reach its database."},
			},
		},
		{
			method: http.MethodPost, path: "/auth/otp", id: "requestOtp",
			summary:     "Request a one-time code",
			description: "Sends a code when OTP is required. When OTP is disabled, signs in and returns a session instead.",
			body:        new(OTPRequest),
			responses: []response{
				{http.StatusOK, new(OTPSuccess), "A pending code, or a session when OTP is disabled."},
				failure(http.StatusBadRequest, "The phone number or JSON body is invalid."),
				failure(http.StatusTooManyRequests, "Too many code requests. retry_after and the Retry-After header say how long to wait."),
				failure(http.StatusServiceUnavailable, "OTP delivery is not configured."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/auth/verify", id: "verifyOtp",
			summary:     "Verify a one-time code",
			description: "Checks the code, creates the account on first success, and returns a session.",
			body:        new(VerifyRequest),
			responses: []response{
				{http.StatusOK, new(Session), "Session for the verified phone number."},
				failure(http.StatusBadRequest, "The phone number, code, or JSON body is invalid."),
				failure(http.StatusUnauthorized, "The code is invalid or expired."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/auth/refresh", id: "refreshSession",
			summary:     "Refresh a session",
			description: "Rotates the refresh token and returns a new access token for the same session.",
			body:        new(RefreshRequest),
			responses: []response{
				{http.StatusOK, new(TokenPair), "New access and refresh tokens."},
				failure(http.StatusBadRequest, "The JSON body is invalid."),
				failure(http.StatusUnauthorized, "The refresh token is invalid or expired."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/auth/logout", id: "logout",
			summary:     "Log out",
			description: "Revokes the session carried by the access token.",
			auth:        true,
			responses: []response{
				{http.StatusNoContent, nil, "The session was revoked."},
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/me", id: "getProfile",
			summary: "Get the signed-in profile",
			auth:    true,
			responses: []response{
				{http.StatusOK, new(Profile), "The signed-in profile."},
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPatch, path: "/me", id: "updateProfile",
			summary:     "Update the signed-in profile",
			description: "Updates only the fields present in the body.",
			auth:        true,
			body:        new(ProfileUpdate),
			responses: []response{
				{http.StatusOK, new(Profile), "The updated profile."},
				failure(http.StatusBadRequest, "The JSON body or a profile field is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
	}
	for _, item := range ops {
		if err := add(reflector, item); err != nil {
			return nil, err
		}
	}
	return reflector.Spec, nil
}

func failure(code int, text string) response {
	return response{code, new(Error), text}
}

func status(code int, description string) func(*openapi.ContentUnit) {
	return func(cu *openapi.ContentUnit) {
		cu.HTTPStatus = code
		cu.Description = description
	}
}

func add(r *openapi31.Reflector, item operation) error {
	op, err := r.NewOperationContext(item.method, item.path)
	if err != nil {
		return err
	}
	op.SetID(item.id)
	op.SetTags("Messenger")
	op.SetSummary(item.summary)
	if item.description != "" {
		op.SetDescription(item.description)
	}
	if item.auth {
		op.AddSecurity("bearerAuth")
	}
	if item.body != nil {
		op.AddReqStructure(item.body, func(cu *openapi.ContentUnit) {
			cu.Customize = func(cor openapi.ContentOrReference) {
				body, ok := cor.(*openapi31.RequestBodyOrReference)
				if ok && body.RequestBody != nil {
					body.RequestBody.WithRequired(true)
				}
			}
		})
	}
	for _, resp := range item.responses {
		op.AddRespStructure(resp.body, status(resp.status, resp.text))
	}
	return r.AddOperation(op)
}
