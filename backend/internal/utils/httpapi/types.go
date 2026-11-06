package httpapi

import (
	"encoding/json"
	"fmt"
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
	Username     string    `json:"username" required:"true" description:"Unique username handle."`
	DisplayName  string    `json:"display_name" required:"true"`
	About        string    `json:"about" required:"true"`
	ProfilePhoto string    `json:"profile_photo" required:"true"`
	CreatedAt    time.Time `json:"created_at" required:"true"`
	UpdatedAt    time.Time `json:"updated_at" required:"true"`
}

// Session is issued by login and signup.
type Session struct {
	AccessToken  string  `json:"access_token" required:"true"`
	RefreshToken string  `json:"refresh_token" required:"true"`
	TokenType    string  `json:"token_type" required:"true" description:"Always Bearer."`
	ExpiresIn    int     `json:"expires_in" required:"true" description:"Access token lifetime in seconds."`
	Created      bool    `json:"created" required:"true" description:"True when this request created the account."`
	Profile      Profile `json:"profile" required:"true"`
}

// TokenPair is issued by refresh. It does not include a profile.
type TokenPair struct {
	AccessToken  string `json:"access_token" required:"true"`
	RefreshToken string `json:"refresh_token" required:"true"`
	TokenType    string `json:"token_type" required:"true" description:"Always Bearer."`
	ExpiresIn    int    `json:"expires_in" required:"true" description:"Access token lifetime in seconds."`
}

// AuthRequest is the body of POST /auth/login and POST /auth/signup.
type AuthRequest struct {
	Username string `json:"username" required:"true" description:"Unique username handle."`
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

// Message is a chat message in a conversation.
type Message struct {
	ID             string    `json:"id" required:"true"`
	ConversationID string    `json:"conversation_id" required:"true"`
	SenderID       string    `json:"sender_id" required:"true"`
	Body           string    `json:"body" required:"true"`
	CreatedAt      time.Time `json:"created_at" required:"true"`
}

// Conversation is a 1:1 DM summary for the signed-in user.
type Conversation struct {
	ID          string    `json:"id" required:"true"`
	Peer        Profile   `json:"peer" required:"true"`
	LastMessage *Message  `json:"last_message,omitempty"`
	Unread      int       `json:"unread" required:"true" description:"Unread count. Always 0 until receipts ship."`
	CreatedAt   time.Time `json:"created_at" required:"true"`
	UpdatedAt   time.Time `json:"updated_at" required:"true"`
}

// ConversationList is the body of GET /conversations.
type ConversationList struct {
	Conversations []Conversation `json:"conversations" required:"true"`
}

// OpenConversation is the body of POST /conversations.
// Provide either user_id or username (exactly one).
type OpenConversation struct {
	UserID   string `json:"user_id,omitempty" description:"Peer user id to open or create a DM with."`
	Username string `json:"username,omitempty" description:"Peer username handle."`
}

// AddContact is the body of POST /contacts.
// Provide either user_id or username (exactly one).
type AddContact struct {
	UserID   string `json:"user_id,omitempty" description:"Peer user id to save."`
	Username string `json:"username,omitempty" description:"Peer username handle."`
}

// LookupUserQuery is the query for GET /users/lookup.
type LookupUserQuery struct {
	Username string `query:"username" required:"true" description:"Username handle."`
}

// ContactIDPath is the path for DELETE /contacts/{id}.
type ContactIDPath struct {
	ID string `path:"id" required:"true" description:"Contact id."`
}

// Contact is a saved peer for the signed-in user.
type Contact struct {
	ID        string    `json:"id" required:"true"`
	Peer      Profile   `json:"peer" required:"true"`
	CreatedAt time.Time `json:"created_at" required:"true"`
}

// ContactList is the body of GET /contacts.
type ContactList struct {
	Contacts []Contact `json:"contacts" required:"true"`
}

// MessageList is the body of GET /conversations/{id}/messages.
type MessageList struct {
	Messages []Message `json:"messages" required:"true" description:"Newest first."`
}

// SendMessage is the body of POST /conversations/{id}/messages.
type SendMessage struct {
	Body string `json:"body" required:"true" description:"1 to 4000 characters."`
}

// ListMessagesQuery is the path and query for GET /conversations/{id}/messages.
type ListMessagesQuery struct {
	ID     string `path:"id" required:"true" description:"Conversation id."`
	Before string `query:"before" description:"Return messages created before this RFC3339 timestamp."`
	Limit  int    `query:"limit" description:"Page size from 1 to 100. Defaults to 50."`
}

// SendMessageRequest is the path and body for POST /conversations/{id}/messages.
type SendMessageRequest struct {
	ID   string `path:"id" required:"true" description:"Conversation id."`
	Body string `json:"body" required:"true" description:"1 to 4000 characters."`
}

// WSInfo documents the WebSocket upgrade at GET /ws.
type WSInfo struct {
	Protocol string `json:"protocol" required:"true" description:"JSON frames. Auth via Authorization Bearer or access_token query. Frames do not carry a token."`
}

// UserSearchHit is one row of a user.search.results frame.
// IdentityKey and SignedPreKey are set together when the user can be messaged.
type UserSearchHit struct {
	ID           string        `json:"id" required:"true"`
	Username     string        `json:"username" required:"true" description:"Username handle."`
	DisplayName  string        `json:"display_name" required:"true" description:"May be empty when the user has not set one."`
	IdentityKey  string        `json:"identity_key,omitempty" description:"Base64 identity public key. Present with signed_pre_key when this user can be messaged."`
	SignedPreKey *SignedPreKey `json:"signed_pre_key,omitempty" description:"Current signed prekey. Omitted, with identity_key, until both are published."`
}

type CreateIdentityKeyRequest struct {
	PublicKey string `json:"public_key" required:"true" description:"Base64-encoded libsignal identity public key (33 bytes)."`
}

// UploadSignedPreKeyRequest is the body of POST /keys/signed-prekey.
type UploadSignedPreKeyRequest struct {
	KeyID     int    `json:"key_id" required:"true" description:"libsignal signed prekey id, from 0 through 16777215."`
	PublicKey string `json:"public_key" required:"true" description:"Base64-encoded libsignal Curve25519 public key (33 bytes)."`
	Signature string `json:"signature" required:"true" description:"Base64-encoded XEdDSA signature of the public key (64 bytes)."`
}

// UnmarshalJSON requires key_id so an omitted id is not stored as 0.
func (r *UploadSignedPreKeyRequest) UnmarshalJSON(data []byte) error {
	var raw struct {
		KeyID     *int   `json:"key_id"`
		PublicKey string `json:"public_key"`
		Signature string `json:"signature"`
	}
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	if raw.KeyID == nil {
		return fmt.Errorf("key_id is required")
	}
	r.KeyID = *raw.KeyID
	r.PublicKey = raw.PublicKey
	r.Signature = raw.Signature
	return nil
}

// OneTimePreKey is one entry of POST /keys/one-time-prekeys.
type OneTimePreKey struct {
	KeyID     int    `json:"key_id" required:"true" description:"libsignal one-time prekey id, from 0 through 16777215."`
	PublicKey string `json:"public_key" required:"true" description:"Base64-encoded libsignal Curve25519 public key (33 bytes)."`
}

// UnmarshalJSON requires key_id so an omitted id is not stored as 0.
func (k *OneTimePreKey) UnmarshalJSON(data []byte) error {
	var raw struct {
		KeyID     *int   `json:"key_id"`
		PublicKey string `json:"public_key"`
	}
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	if raw.KeyID == nil {
		return fmt.Errorf("key_id is required")
	}
	k.KeyID = *raw.KeyID
	k.PublicKey = raw.PublicKey
	return nil
}

// UploadOneTimePreKeysRequest is the body of POST /keys/one-time-prekeys.
// The list replaces one-time prekeys already stored for the account.
type UploadOneTimePreKeysRequest struct {
	PreKeys []OneTimePreKey `json:"prekeys" required:"true" nullable:"false" minItems:"1" maxItems:"100" description:"1 to 100 one-time prekeys. Replaces one-time prekeys already stored for this account."`
}

// UnmarshalJSON requires prekeys so null is not treated as an empty bundle.
func (r *UploadOneTimePreKeysRequest) UnmarshalJSON(data []byte) error {
	var raw map[string]json.RawMessage
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	body, ok := raw["prekeys"]
	if !ok || string(body) == "null" {
		return fmt.Errorf("prekeys is required")
	}
	var items []OneTimePreKey
	if err := json.Unmarshal(body, &items); err != nil {
		return err
	}
	r.PreKeys = items
	return nil
}

// IdentityKey is the signed-in user's stored identity public key.
type IdentityKey struct {
	PublicKey string    `json:"public_key" required:"true" description:"Base64-encoded libsignal identity public key (33 bytes)."`
	CreatedAt time.Time `json:"created_at" required:"true"`
}

// PreKeyBundlePath is the path for POST /keys/{user_id}/bundle.
type PreKeyBundlePath struct {
	UserID string `path:"user_id" required:"true" description:"User id whose keys start a Signal session."`
}

// SignedPreKey is a libsignal signed prekey public key and its identity signature.
type SignedPreKey struct {
	KeyID     int    `json:"key_id" required:"true" description:"libsignal signed prekey id."`
	PublicKey string `json:"public_key" required:"true" description:"Base64-encoded Curve25519 public key (33 bytes)."`
	Signature string `json:"signature" required:"true" description:"Base64-encoded XEdDSA signature of the public key (64 bytes)."`
}

// PreKeyBundle is the public key material for an X3DH session with one user.
// PreKey is omitted when that user has no one-time prekeys left.
type PreKeyBundle struct {
	IdentityKey  string         `json:"identity_key" required:"true" description:"Base64-encoded libsignal identity public key (33 bytes)."`
	SignedPreKey SignedPreKey   `json:"signed_pre_key" required:"true"`
	PreKey       *OneTimePreKey `json:"pre_key,omitempty" description:"One-time prekey claimed by this request. Omitted when the user has none left."`
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
		Username:     user.Username,
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

func NewMessage(message *model.Message) Message {
	return Message{
		ID:             message.ID,
		ConversationID: message.ConversationID,
		SenderID:       message.SenderID,
		Body:           message.Body,
		CreatedAt:      message.CreatedAt,
	}
}

func NewConversation(id string, peer Profile, last *model.Message, unread int, createdAt, updatedAt time.Time) Conversation {
	out := Conversation{
		ID:        id,
		Peer:      peer,
		Unread:    unread,
		CreatedAt: createdAt,
		UpdatedAt: updatedAt,
	}
	if last != nil {
		msg := NewMessage(last)
		out.LastMessage = &msg
	}
	return out
}

func NewIdentityKey(key *model.IdentityKey) IdentityKey {
	return IdentityKey{
		PublicKey: key.PublicKey,
		CreatedAt: key.CreatedAt,
	}
}

func NewPreKeyBundle(identity *model.IdentityKey, signed *model.SignedPreKey, oneTime *model.OneTimePreKey) PreKeyBundle {
	bundle := PreKeyBundle{
		IdentityKey: identity.PublicKey,
		SignedPreKey: SignedPreKey{
			KeyID:     signed.KeyID,
			PublicKey: signed.PublicKey,
			Signature: signed.Signature,
		},
	}
	if oneTime != nil {
		bundle.PreKey = &OneTimePreKey{
			KeyID:     oneTime.KeyID,
			PublicKey: oneTime.PublicKey,
		}
	}
	return bundle
}

func NewContact(id string, peer Profile, createdAt time.Time) Contact {
	return Contact{
		ID:        id,
		Peer:      peer,
		CreatedAt: createdAt,
	}
}
