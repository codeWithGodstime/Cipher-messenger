package ws

import (
	"encoding/json"
	"sync"

	"messenger/backend/internal/model"
	"messenger/backend/internal/utils/httpapi"
)

// Hub fans messages out to a user's active WebSocket connections.
type Hub struct {
	mu      sync.RWMutex
	clients map[string]map[*Client]struct{}
}

func NewHub() *Hub {
	return &Hub{clients: make(map[string]map[*Client]struct{})}
}

func (h *Hub) Register(userID string, client *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.clients[userID] == nil {
		h.clients[userID] = make(map[*Client]struct{})
	}
	h.clients[userID][client] = struct{}{}
}

func (h *Hub) Unregister(userID string, client *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	set := h.clients[userID]
	if set == nil {
		return
	}
	delete(set, client)
	if len(set) == 0 {
		delete(h.clients, userID)
	}
}

// PublishMessage implements service.MessageBroadcaster.
func (h *Hub) PublishMessage(message model.Message, recipientIDs []string) {
	payload, err := json.Marshal(Envelope{
		Type:    EventMessageCreated,
		Message: ptrMessage(httpapi.NewMessage(&message)),
	})
	if err != nil {
		return
	}
	h.mu.RLock()
	defer h.mu.RUnlock()
	seen := make(map[*Client]struct{})
	for _, userID := range recipientIDs {
		for client := range h.clients[userID] {
			if _, ok := seen[client]; ok {
				continue
			}
			seen[client] = struct{}{}
			client.Send(payload)
		}
	}
}

func ptrMessage(message httpapi.Message) *httpapi.Message {
	return &message
}
