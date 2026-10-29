package httpapi

import (
	"time"

	"messenger/backend/internal/model"
)

const (
	StatusOK          = "ok"
	StatusUnavailable = "unavailable"
	TokenTypeBearer   = "Bearer"
	ErrorInternal     = "internal"
	MessageInternal   = "Something went wrong."
)

// Health is the body of GET /health.
type Health struct {
	Status string `json:"status" required:"true" description:"ok when the API can reach its database, unavailable otherwise."`
}

// Profile is the signed-in user returned by auth and profile routes.
type Profile struct {
	ID           string    `json:"id" required:"true"`
	Phone        string    `json:"phone" required:"true" description:"Phone number in E.164 format."`
	DisplayName  string    `json:"display_name" required:"true"`
	About        string    `json:"about" required:"true"`
	ProfilePhoto string    `json:"profile_photo" required:"true"`
	CreatedAt    time.Time `json:"created_at" required:"true"`
	UpdatedAt    time.Time `json:"updated_at" required:"true"`
}

// Session is issued by verify and by OTP request when OTP is disabled.
type Session struct {
	AccessToken  string  `json:"access_token" required:"true"`
	RefreshToken string  `json:"refresh_token" required:"true"`
	TokenType    string  `json:"token_type" required:"true" description:"Always Bearer."`
	ExpiresIn    int     `json:"expires_in" required:"true" description:"Access token lifetime in seconds."`
	Created      bool    `json:"created" required:"true" description:"True when this login created the account."`
	Profile      Profile `json:"profile" required:"true"`
}

// TokenPair is issued by refresh. It does not include a profile.
type TokenPair struct {
	AccessToken  string `json:"access_token" required:"true"`
	RefreshToken string `json:"refresh_token" required:"true"`
	TokenType    string `json:"token_type" required:"true" description:"Always Bearer."`
	ExpiresIn    int    `json:"expires_in" required:"true" description:"Access token lifetime in seconds."`
}

// OTPPending is the body of POST /auth/otp when a code was sent.
type OTPPending struct {
	ExpiresIn int `json:"expires_in" required:"true" description:"Seconds until the code expires."`
}

// OTPSuccess is the 200 body of POST /auth/otp.
// The wire payload is either OTPPending or Session, never both.
type OTPSuccess struct{}

func (OTPSuccess) JSONSchemaOneOf() []any {
	return []any{OTPPending{}, Session{}}
}

// OTPRequest is the body of POST /auth/otp.
type OTPRequest struct {
	Phone string `json:"phone" required:"true" description:"Phone number in E.164 format."`
}

// VerifyRequest is the body of POST /auth/verify.
type VerifyRequest struct {
	Phone string `json:"phone" required:"true" description:"Phone number in E.164 format."`
	Code  string `json:"code" required:"true" description:"Six-digit one-time code."`
}

// RefreshRequest is the body of POST /auth/refresh.
type RefreshRequest struct {
	RefreshToken string `json:"refresh_token" required:"true"`
}

// ProfileUpdate is the body of PATCH /me. Omitted fields are left unchanged.
type ProfileUpdate struct {
	DisplayName  *string `json:"display_name,omitempty" description:"1 to 64 characters."`
	About        *string `json:"about,omitempty" description:"Up to 139 characters."`
	ProfilePhoto *string `json:"profile_photo,omitempty" description:"Up to 2048 characters."`
}

// Error is the JSON body of a failed request.
type Error struct {
	Code       string `json:"error" required:"true" description:"Stable machine-readable code."`
	Message    string `json:"message" required:"true"`
	RetryAfter int    `json:"retry_after,omitempty" description:"Seconds to wait before retrying. Present when error is rate_limited."`
}

func NewProfile(user *model.User) Profile {
	return Profile{
		ID:           user.ID,
		Phone:        user.Phone,
		DisplayName:  user.DisplayName,
		About:        user.About,
		ProfilePhoto: user.ProfilePhoto,
		CreatedAt:    user.CreatedAt,
		UpdatedAt:    user.UpdatedAt,
	}
}

func NewSession(accessToken, refreshToken string, expiresIn int, created bool, profile Profile) Session {
	return Session{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		TokenType:    TokenTypeBearer,
		ExpiresIn:    expiresIn,
		Created:      created,
		Profile:      profile,
	}
}

func NewTokenPair(accessToken, refreshToken string, expiresIn int) TokenPair {
	return TokenPair{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		TokenType:    TokenTypeBearer,
		ExpiresIn:    expiresIn,
	}
}

func NewError(code, message string, retryAfter int) Error {
	return Error{Code: code, Message: message, RetryAfter: retryAfter}
}

func InternalError() Error {
	return NewError(ErrorInternal, MessageInternal, 0)
}
