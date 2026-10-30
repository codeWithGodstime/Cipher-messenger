package handler

import (
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/service"
	"messenger/backend/internal/token"
	"messenger/backend/internal/ws"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

type WSHandler struct {
	hub      *ws.Hub
	chat     *service.ChatService
	tokens   *token.Manager
	sessions repository.SessionRepository
}

func NewWSHandler(
	hub *ws.Hub,
	chat *service.ChatService,
	tokens *token.Manager,
	sessions repository.SessionRepository,
) *WSHandler {
	return &WSHandler{hub: hub, chat: chat, tokens: tokens, sessions: sessions}
}

func (h *WSHandler) Connect(c *gin.Context) {
	raw, ok := accessToken(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	userID, sessionID, err := h.tokens.Parse(raw)
	if err != nil {
		writeError(c, apperr.Unauthorized())
		return
	}
	session, err := h.sessions.FindByID(c.Request.Context(), sessionID)
	if err != nil {
		writeError(c, err)
		return
	}
	now := time.Now().UTC()
	if session == nil || session.UserID != userID || session.RevokedAt != nil || !session.ExpiresAt.After(now) {
		writeError(c, apperr.Unauthorized())
		return
	}

	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		// Usually means a reverse proxy dropped Connection/Upgrade hop-by-hop headers.
		log.Printf(
			"websocket upgrade: %v (Connection=%q Upgrade=%q)",
			err,
			c.GetHeader("Connection"),
			c.GetHeader("Upgrade"),
		)
		return
	}
	client := ws.NewClient(h.hub, userID, h.chat, conn)
	client.Run()
}

func accessToken(c *gin.Context) (string, bool) {
	if raw, ok := bearerFromHeader(c.GetHeader("Authorization")); ok {
		return raw, true
	}
	raw := strings.TrimSpace(c.Query("access_token"))
	if raw == "" || strings.Contains(raw, " ") {
		return "", false
	}
	return raw, true
}

func bearerFromHeader(header string) (string, bool) {
	const prefix = "Bearer "
	if len(header) < len(prefix) || !strings.EqualFold(header[:len(prefix)], prefix) {
		return "", false
	}
	raw := strings.TrimSpace(header[len(prefix):])
	if raw == "" || strings.Contains(raw, " ") {
		return "", false
	}
	return raw, true
}
