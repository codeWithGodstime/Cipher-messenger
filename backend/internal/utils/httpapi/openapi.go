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
	"gopkg.in/yaml.v2"
)

// WriteOpenAPI writes the OpenAPI document for this API.
func WriteOpenAPI(path string) error {
	spec, err := openAPI()
	if err != nil {
		return err
	}
	// MarshalYAML uses gopkg.in/yaml.v2, which wraps long scalars at 80 columns
	// when the emitter width is left unset. v2.3.0 did not wrap and v2.4.0
	// restored wrapping, so an implicit width makes this file differ between
	// machines. FutureLineWrap pins the width to unlimited (-1).
	yaml.FutureLineWrap()
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
	// request is path/query/json input reflected into parameters and body.
	request   any
	bodyOnly  bool // when true, request fields are JSON body only (legacy auth routes).
	responses []response
}

func openAPI() (*openapi31.Spec, error) {
	reflector := openapi31.NewReflector()
	reflector.DefaultOptions = append(reflector.DefaultOptions,
		jsonschema.InterceptDefName(func(_ reflect.Type, name string) string {
			return strings.TrimPrefix(name, "Httpapi")
		}),
	)
	reflector.Spec.Info.
		WithTitle("Messenger API").
		WithVersion("1.0.0").
		WithDescription("HTTP API for the Messenger app. Protected routes require an access token.")
	reflector.Spec.SetHTTPBearerTokenSecurity("bearerAuth", "JWT", "Access token from login, signup, or refresh.")

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
			method: http.MethodPost, path: "/auth/login", id: "login",
			summary:     "Log in with a username",
			description: "Returns a session when the username exists. Does not create accounts.",
			request:     new(AuthRequest),
			bodyOnly:    true,
			responses: []response{
				{http.StatusOK, new(Session), "Session for the existing username."},
				failure(http.StatusBadRequest, "The username or JSON body is invalid."),
				failure(http.StatusNotFound, "User does not exist."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/auth/signup", id: "signup",
			summary:     "Sign up with a username",
			description: "Creates an account when the username is free and returns a session.",
			request:     new(AuthRequest),
			bodyOnly:    true,
			responses: []response{
				{http.StatusOK, new(Session), "Session for the new username."},
				failure(http.StatusBadRequest, "The username or JSON body is invalid."),
				failure(http.StatusConflict, "Username is already taken."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/auth/refresh", id: "refreshSession",
			summary:     "Refresh a session",
			description: "Rotates the refresh token and returns a new access token for the same session.",
			request:     new(RefreshRequest),
			bodyOnly:    true,
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
			request:     new(ProfileUpdate),
			bodyOnly:    true,
			responses: []response{
				{http.StatusOK, new(Profile), "The updated profile."},
				failure(http.StatusBadRequest, "The JSON body or a profile field is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/conversations", id: "listConversations",
			summary:     "List conversations",
			description: "Returns 1:1 DMs for the signed-in user, newest activity first.",
			auth:        true,
			responses: []response{
				{http.StatusOK, new(ConversationList), "Conversation summaries."},
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/keys/", id: "getIdentityKey",
			summary:     "Get the identity public key",
			description: "Returns the identity public key stored for the signed-in user.",
			auth:        true,
			responses: []response{
				{http.StatusOK, new(IdentityKey), "The stored identity public key."},
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusNotFound, "This account has no identity key."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/keys/", id: "createIdentityKey",
			summary:     "Upload an identity public key",
			description: "Stores the signed-in user's libsignal identity public key. Uploading again replaces the stored key.",
			auth:        true,
			request:     new(CreateIdentityKeyRequest),
			bodyOnly:    true,
			responses: []response{
				{http.StatusNoContent, new(struct{}), "The identity public key was stored."},
				failure(http.StatusBadRequest, "The JSON body or public_key is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/keys/signed-prekey", id: "uploadSignedPreKey",
			summary:     "Upload a signed prekey",
			description: "Stores the signed-in user's current libsignal signed prekey. Uploading again replaces the stored signed prekey. The signature is stored for recipients to verify with the identity key.",
			auth:        true,
			request:     new(UploadSignedPreKeyRequest),
			bodyOnly:    true,
			responses: []response{
				{http.StatusNoContent, new(struct{}), "The signed prekey was stored."},
				failure(http.StatusBadRequest, "The JSON body, key_id, public_key, or signature is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/keys/kyber-prekey", id: "uploadKyberPreKey",
			summary:     "Upload a Kyber prekey",
			description: "Stores the signed-in user's current libsignal Kyber1024 prekey. Uploading again replaces it. Recipients need this key, with the identity key and signed prekey, to start a session.",
			auth:        true,
			request:     new(UploadKyberPreKeyRequest),
			bodyOnly:    true,
			responses: []response{
				{http.StatusNoContent, new(struct{}), "The Kyber prekey was stored."},
				failure(http.StatusBadRequest, "The JSON body, key_id, public_key, or signature is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/keys/one-time-prekeys", id: "oneTimePreKeyCount",
			summary:     "Count remaining one-time prekeys",
			description: "Returns how many one-time prekeys are still stored for the signed-in user. The client uploads more when this count is below 10. The response does not include the keys.",
			auth:        true,
			responses: []response{
				{http.StatusOK, new(OneTimePreKeyCount), "The number of one-time prekeys still available."},
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/keys/one-time-prekeys", id: "uploadOneTimePreKeys",
			summary:     "Upload one-time prekeys",
			description: "Adds one-time prekeys to the signed-in user's pool. Keys already stored stay in place. prekeys must contain 1 to 100 keys. A key_id already stored with the same public key is left unchanged. A key_id stored with a different public key is rejected and nothing is written. The account can hold at most 200 one-time prekeys.",
			auth:        true,
			request:     new(UploadOneTimePreKeysRequest),
			bodyOnly:    true,
			responses: []response{
				{http.StatusNoContent, new(struct{}), "The one-time prekeys were added. A key already stored with the same public key is left as it is."},
				failure(http.StatusBadRequest, "The JSON body is invalid, prekeys is empty or larger than 100, a key is invalid or duplicated in the list, or the account would have more than 200 one-time prekeys."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusConflict, "A key_id in prekeys is already stored for this account with a different public key."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/keys/{user_id}/bundle", id: "claimPreKeyBundle",
			summary:     "Claim a prekey bundle",
			description: "Returns another user's identity public key, signed prekey, Kyber prekey, and one one-time prekey so the caller can start a Signal session. The one-time prekey is removed from that user's pool and is omitted when none remain. A session can still start from the identity key, signed prekey, and Kyber prekey. This is not a read: repeating the call claims another one-time prekey.",
			auth:        true,
			request:     new(PreKeyBundlePath),
			responses: []response{
				{http.StatusOK, new(PreKeyBundle), "Identity key, signed prekey, Kyber prekey, and at most one one-time prekey."},
				failure(http.StatusBadRequest, "user_id is missing."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusNotFound, "User does not exist, or has not published an identity key, signed prekey, and Kyber prekey."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/users/lookup", id: "lookupUser",
			summary:     "Look up a user by username",
			description: "Finds a registered user by exact username handle.",
			auth:        true,
			request:     new(LookupUserQuery),
			responses: []response{
				{http.StatusOK, new(Profile), "The matching user profile."},
				failure(http.StatusBadRequest, "username is missing or invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusNotFound, "User does not exist."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/contacts", id: "listContacts",
			summary:     "List contacts",
			description: "Returns peers the signed-in user has saved, newest first.",
			auth:        true,
			responses: []response{
				{http.StatusOK, new(ContactList), "Saved contacts."},
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/contacts", id: "addContact",
			summary:     "Add a contact",
			description: "Saves a registered peer by user_id or username. Idempotent for the same peer.",
			auth:        true,
			request:     new(AddContact),
			bodyOnly:    true,
			responses: []response{
				{http.StatusCreated, new(Contact), "The saved contact."},
				failure(http.StatusBadRequest, "The JSON body, user_id, or username is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusNotFound, "User does not exist."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodDelete, path: "/contacts/{id}", id: "deleteContact",
			summary:     "Remove a contact",
			description: "Deletes a saved contact owned by the signed-in user.",
			auth:        true,
			request:     new(ContactIDPath),
			responses: []response{
				{http.StatusNoContent, new(struct{}), "Contact removed."},
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusNotFound, "Contact not found."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/conversations", id: "openConversation",
			summary:     "Open or create a DM",
			description: "Opens an existing 1:1 conversation with the peer, or creates one. Provide user_id or username.",
			auth:        true,
			request:     new(OpenConversation),
			bodyOnly:    true,
			responses: []response{
				{http.StatusOK, new(Conversation), "The conversation summary."},
				failure(http.StatusBadRequest, "The JSON body, user_id, or username is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusNotFound, "User does not exist."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/conversations/{id}/messages", id: "listMessages",
			summary:     "List messages",
			description: "Returns messages newest first. Optional before (RFC3339) and limit (1-100, default 50).",
			auth:        true,
			request:     new(ListMessagesQuery),
			responses: []response{
				{http.StatusOK, new(MessageList), "Messages in the conversation."},
				failure(http.StatusBadRequest, "before or limit is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusForbidden, "The signed-in user is not a member."),
				failure(http.StatusNotFound, "Conversation not found."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodPost, path: "/conversations/{id}/messages", id: "sendMessage",
			summary:     "Send a message",
			description: "Persists a message and fans it out to conversation members over WebSocket.",
			auth:        true,
			request:     new(SendMessageRequest),
			responses: []response{
				{http.StatusCreated, new(Message), "The created message."},
				failure(http.StatusBadRequest, "The JSON body or body field is invalid."),
				failure(http.StatusUnauthorized, "Missing or invalid access token."),
				failure(http.StatusForbidden, "The signed-in user is not a member."),
				failure(http.StatusNotFound, "Conversation not found."),
				failure(http.StatusInternalServerError, "Unexpected failure."),
			},
		},
		{
			method: http.MethodGet, path: "/ws", id: "websocket",
			summary: "Open a WebSocket",
			description: "Upgrades to a WebSocket. Auth via Authorization Bearer or access_token query; frames do not carry a token. " +
				"Client commands: ping, message.send (conversation_id, body), user.search (query, optional request_id). " +
				"Server events: pong, message.created (message), error (error code invalid_json, validation_error, send_failed, or unknown_type), " +
				"user.search.results (query, optional request_id, users, optional error). " +
				"user.search matches username case-insensitively as a substring, ranks prefix matches first, excludes the signed-in user, and returns at most 20 users. " +
				"Each user has id, username, and display_name. When that user has published an identity key, a signed prekey, and a Kyber prekey, the hit also includes identity_key, signed_pre_key, and kyber_pre_key. All three are omitted until all three exist. " +
				"A one-time prekey is not included; POST /keys/{user_id}/bundle claims one when a session is started. " +
				"An empty or unusable query returns users []. search_failed is retryable by sending user.search again. " +
				"Echoed request_id (1 to 64 characters, no spaces) lets the client ignore a stale response. No paging cursor: a short fragment may omit later matches. Keepalive: ping/pong. " +
				"Example command: {\"type\":\"user.search\",\"query\":\"ada\",\"request_id\":\"3\"}. " +
				"Example success: {\"type\":\"user.search.results\",\"query\":\"ada\",\"request_id\":\"3\",\"users\":[{\"id\":\"u2\",\"username\":\"ada\",\"display_name\":\"Ada\",\"identity_key\":\"AQID\",\"signed_pre_key\":{\"key_id\":1,\"public_key\":\"AQID\",\"signature\":\"AQID\"},\"kyber_pre_key\":{\"key_id\":1,\"public_key\":\"AQID\",\"signature\":\"AQID\"}}]}. " +
				"Example empty: {\"type\":\"user.search.results\",\"query\":\"\",\"request_id\":\"4\",\"users\":[]}. " +
				"Example failure: {\"type\":\"user.search.results\",\"query\":\"ada\",\"request_id\":\"3\",\"users\":[],\"error\":\"search_failed\"}.",
			responses: []response{
				{http.StatusSwitchingProtocols, new(WSInfo), "WebSocket upgrade accepted."},
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
	if item.request != nil {
		if item.bodyOnly {
			op.AddReqStructure(item.request, func(cu *openapi.ContentUnit) {
				cu.Customize = func(cor openapi.ContentOrReference) {
					body, ok := cor.(*openapi31.RequestBodyOrReference)
					if ok && body.RequestBody != nil {
						body.RequestBody.WithRequired(true)
					}
				}
			})
		} else {
			op.AddReqStructure(item.request)
		}
	}
	for _, resp := range item.responses {
		op.AddRespStructure(resp.body, status(resp.status, resp.text))
	}
	return r.AddOperation(op)
}
