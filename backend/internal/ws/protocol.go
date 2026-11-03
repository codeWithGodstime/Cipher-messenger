package ws

import "messenger/backend/internal/utils/httpapi"

const (
	EventMessageCreated = "message.created"
	EventPong           = "pong"
	EventError          = "error"

	CommandMessageSend = "message.send"
	CommandPing        = "ping"
)

// Envelope is a WebSocket JSON frame.
type Envelope struct {
	Type           string           `json:"type"`
	ConversationID string           `json:"conversation_id,omitempty"`
	Body           string           `json:"body,omitempty"`
	Message        *httpapi.Message `json:"message,omitempty"`
	Error          string           `json:"error,omitempty"`
}
