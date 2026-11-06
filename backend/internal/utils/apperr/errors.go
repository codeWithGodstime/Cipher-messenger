package apperr

import "net/http"

// Error is a client-facing API failure with a stable code.
type Error struct {
	Status     int
	Code       string
	Message    string
	RetryAfter int
}

func (e *Error) Error() string { return e.Message }

func InvalidBody() *Error {
	return &Error{Status: http.StatusBadRequest, Code: "invalid_body", Message: "Request body must be JSON with the expected fields."}
}

func InvalidPhone() *Error {
	return &Error{Status: http.StatusBadRequest, Code: "invalid_phone", Message: "Phone number must be in E.164 format, for example +14155552671."}
}

func InvalidUsername() *Error {
	return &Error{
		Status:  http.StatusBadRequest,
		Code:    "invalid_username",
		Message: "Username must be 3–32 characters, start with a letter, and use only letters, digits, or underscores.",
	}
}

func UserDoesNotExist() *Error {
	return &Error{Status: http.StatusNotFound, Code: "user_not_found", Message: "User does not exist."}
}

func UsernameTaken() *Error {
	return &Error{Status: http.StatusConflict, Code: "username_taken", Message: "Username is already taken."}
}

func OneTimePreKeyTaken() *Error {
	return &Error{Status: http.StatusConflict, Code: "prekey_id_taken", Message: "A one-time prekey with this key_id is already stored with a different public key."}
}

func Validation(message string) *Error {
	return &Error{Status: http.StatusBadRequest, Code: "validation_error", Message: message}
}

func InvalidCode() *Error {
	return &Error{Status: http.StatusUnauthorized, Code: "invalid_code", Message: "The code is invalid or expired."}
}

func Unauthorized() *Error {
	return &Error{Status: http.StatusUnauthorized, Code: "unauthorized", Message: "Missing or invalid access token."}
}

func NotFound(message string) *Error {
	if message == "" {
		message = "Resource not found."
	}
	return &Error{Status: http.StatusNotFound, Code: "not_found", Message: message}
}

func Forbidden(message string) *Error {
	if message == "" {
		message = "You do not have access to this resource."
	}
	return &Error{Status: http.StatusForbidden, Code: "forbidden", Message: message}
}

func InvalidRefresh() *Error {
	return &Error{Status: http.StatusUnauthorized, Code: "invalid_refresh", Message: "The refresh token is invalid or expired."}
}

func RateLimited(retryAfter int) *Error {
	if retryAfter < 1 {
		retryAfter = 1
	}
	return &Error{
		Status:     http.StatusTooManyRequests,
		Code:       "rate_limited",
		Message:    "Too many code requests. Try again later.",
		RetryAfter: retryAfter,
	}
}

func SMSUnavailable() *Error {
	return &Error{Status: http.StatusServiceUnavailable, Code: "sms_unavailable", Message: "OTP delivery is not configured."}
}
