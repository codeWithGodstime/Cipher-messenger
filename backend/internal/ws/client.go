package ws

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"github.com/gorilla/websocket"

	"messenger/backend/internal/service"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 8192
	sendBuffer     = 64
)

type Client struct {
	hub    *Hub
	userID string
	chat   *service.ChatService
	conn   *websocket.Conn
	send   chan []byte
}

func NewClient(hub *Hub, userID string, chat *service.ChatService, conn *websocket.Conn) *Client {
	return &Client{
		hub:    hub,
		userID: userID,
		chat:   chat,
		conn:   conn,
		send:   make(chan []byte, sendBuffer),
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
	default:
		c.sendError("unknown_type")
	}
}

func (c *Client) sendError(code string) {
	payload, err := json.Marshal(Envelope{Type: EventError, Error: code})
	if err != nil {
		return
	}
	c.Send(payload)
}
