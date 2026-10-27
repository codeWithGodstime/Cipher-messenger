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

func Validation(message string) *Error {
	return &Error{Status: http.StatusBadRequest, Code: "validation_error", Message: message}
}

func InvalidCode() *Error {
	return &Error{Status: http.StatusUnauthorized, Code: "invalid_code", Message: "The code is invalid or expired."}
}

func Unauthorized() *Error {
	return &Error{Status: http.StatusUnauthorized, Code: "unauthorized", Message: "Missing or invalid access token."}
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
