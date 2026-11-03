package ws

import (
	"encoding/json"
	"strings"
	"unicode"
	"unicode/utf8"

	"messenger/backend/internal/utils/httpapi"
)

const (
	EventMessageCreated    = "message.created"
	EventPong              = "pong"
	EventError             = "error"
	EventUserSearchResults = "user.search.results"

	CommandMessageSend = "message.send"
	CommandPing        = "ping"
	CommandUserSearch  = "user.search"

	searchFailed = "search_failed"
)

// Envelope is a WebSocket JSON frame.
type Envelope struct {
	Type           string           `json:"type"`
	ConversationID string           `json:"conversation_id,omitempty"`
	Body           string           `json:"body,omitempty"`
	Message        *httpapi.Message `json:"message,omitempty"`
	Error          string           `json:"error,omitempty"`
	Query          string           `json:"query,omitempty"`
	RequestID      string           `json:"request_id,omitempty"`
}

// userSearchResult is the server frame for CommandUserSearch.
// users is always present, including an empty list. error is omitted on success.
type userSearchResult struct {
	Type      string                 `json:"type"`
	Query     string                 `json:"query"`
	RequestID string                 `json:"request_id,omitempty"`
	Users     []httpapi.UserSearchHit `json:"users"`
	Error     string                 `json:"error,omitempty"`
}

// MarshalUserSearchResults encodes a user.search.results frame.
// A nil users slice is sent as []. query is the normalized fragment (may be empty).
func MarshalUserSearchResults(query, requestID, errCode string, users []httpapi.UserSearchHit) ([]byte, error) {
	if users == nil {
		users = []httpapi.UserSearchHit{}
	}
	return json.Marshal(userSearchResult{
		Type:      EventUserSearchResults,
		Query:     query,
		RequestID: cleanRequestID(requestID),
		Users:     users,
		Error:     errCode,
	})
}

// cleanRequestID keeps a short client correlation token and drops anything else.
func cleanRequestID(raw string) string {
	raw = strings.TrimSpace(raw)
	if raw == "" || utf8.RuneCountInString(raw) > 64 {
		return ""
	}
	for _, r := range raw {
		if unicode.IsControl(r) || unicode.IsSpace(r) {
			return ""
		}
	}
	return raw
}
