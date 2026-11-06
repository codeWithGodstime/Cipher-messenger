package ws

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"github.com/gorilla/websocket"

	"messenger/backend/internal/service"
	"messenger/backend/internal/utils"
	"messenger/backend/internal/utils/httpapi"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 65536
	sendBuffer     = 64
)

type Client struct {
	hub      *Hub
	userID   string
	chat     *service.ChatService
	profiles *service.ProfileService
	keys     service.KeyService
	conn     *websocket.Conn
	send     chan []byte
}

func NewClient(hub *Hub, userID string, chat *service.ChatService, profiles *service.ProfileService, keys service.KeyService, conn *websocket.Conn) *Client {
	return &Client{
		hub:      hub,
		userID:   userID,
		chat:     chat,
		profiles: profiles,
		keys:     keys,
		conn:     conn,
		send:     make(chan []byte, sendBuffer),
	}
}

func (c *Client) Send(payload []byte) {
	select {
	case c.send <- payload:
	default:
		// Drop when the client is too slow; read/write pumps will clean up.
	}
}

func (c *Client) Run() {
	c.hub.Register(c.userID, c)
	go c.writePump()
	c.readPump()
}

func (c *Client) readPump() {
	defer func() {
		c.hub.Unregister(c.userID, c)
		_ = c.conn.Close()
	}()
	c.conn.SetReadLimit(maxMessageSize)
	_ = c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		return c.conn.SetReadDeadline(time.Now().Add(pongWait))
	})
	for {
		_, data, err := c.conn.ReadMessage()
		if err != nil {
			return
		}
		c.handle(data)
	}
}

func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		_ = c.conn.Close()
	}()
	for {
		select {
		case payload, ok := <-c.send:
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				_ = c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.conn.WriteMessage(websocket.TextMessage, payload); err != nil {
				return
			}
		case <-ticker.C:
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (c *Client) handle(data []byte) {
	var env Envelope
	if err := json.Unmarshal(data, &env); err != nil {
		c.sendError("invalid_json")
		return
	}
	switch env.Type {
	case CommandPing:
		payload, err := json.Marshal(Envelope{Type: EventPong})
		if err != nil {
			return
		}
		c.Send(payload)
	case CommandMessageSend:
		if env.ConversationID == "" || env.Body == "" {
			c.sendError("validation_error")
			return
		}
		_, err := c.chat.SendMessage(context.Background(), c.userID, env.ConversationID, env.Body)
		if err != nil {
			log.Printf("ws send message: %v", err)
			c.sendError("send_failed")
			return
		}
		// Delivery is via PublishMessage (message.created) to all members including sender.
	case CommandUserSearch:
		c.searchUsers(env)
	default:
		c.sendError("unknown_type")
	}
}

func (c *Client) searchUsers(env Envelope) {
	query := utils.NormalizeUserSearch(env.Query)
	requestID := cleanRequestID(env.RequestID)
	if c.profiles == nil {
		c.sendSearch(query, requestID, searchFailed, nil)
		return
	}
	cards, err := c.profiles.SearchUsernames(context.Background(), c.userID, query)
	if err != nil {
		log.Printf("ws user search: %v", err)
		c.sendSearch(query, requestID, searchFailed, nil)
		return
	}
	if c.keys == nil {
		c.sendSearch(query, requestID, searchFailed, nil)
		return
	}
	ids := make([]string, 0, len(cards))
	for _, card := range cards {
		ids = append(ids, card.ID)
	}
	identities, signedKeys, err := c.keys.ListPeerPublicKeys(context.Background(), ids)
	if err != nil {
		log.Printf("ws user search keys: %v", err)
		c.sendSearch(query, requestID, searchFailed, nil)
		return
	}
	hits := make([]httpapi.UserSearchHit, 0, len(cards))
	for _, card := range cards {
		hit := httpapi.UserSearchHit{
			ID:          card.ID,
			Username:    card.Username,
			DisplayName: card.DisplayName,
		}
		identity, hasIdentity := identities[card.ID]
		signed, hasSigned := signedKeys[card.ID]
		if hasIdentity && hasSigned {
			hit.IdentityKey = identity.PublicKey
			hit.SignedPreKey = &httpapi.SignedPreKey{
				KeyID:     signed.KeyID,
				PublicKey: signed.PublicKey,
				Signature: signed.Signature,
			}
		}
		hits = append(hits, hit)
	}
	c.sendSearch(query, requestID, "", hits)
}

func (c *Client) sendSearch(query, requestID, errCode string, users []httpapi.UserSearchHit) {
	payload, err := MarshalUserSearchResults(query, requestID, errCode, users)
	if err != nil {
		return
	}
	c.Send(payload)
}

func (c *Client) sendError(code string) {
	payload, err := json.Marshal(Envelope{Type: EventError, Error: code})
	if err != nil {
		return
	}
	c.Send(payload)
}
