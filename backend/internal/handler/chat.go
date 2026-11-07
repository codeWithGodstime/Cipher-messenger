package handler

import (
	"io"
	"log"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/middleware"
	"messenger/backend/internal/model"
	"messenger/backend/internal/service"
	"messenger/backend/internal/utils/apperr"
	"messenger/backend/internal/utils/httpapi"
)

type ChatHandler struct {
	chat  *service.ChatService
	media *service.AttachmentService
}

func NewChatHandler(chat *service.ChatService, media *service.AttachmentService) *ChatHandler {
	return &ChatHandler{chat: chat, media: media}
}

func (h *ChatHandler) ListConversations(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	summaries, err := h.chat.ListConversations(c.Request.Context(), userID)
	if err != nil {
		writeError(c, err)
		return
	}
	out := make([]httpapi.Conversation, 0, len(summaries))
	for _, summary := range summaries {
		out = append(out, toConversation(summary))
	}
	c.JSON(http.StatusOK, httpapi.ConversationList{Conversations: out})
}

func (h *ChatHandler) OpenConversation(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	var req httpapi.OpenConversation
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	summary, err := h.chat.OpenDM(c.Request.Context(), userID, req.UserID, req.Username)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, toConversation(*summary))
}

func (h *ChatHandler) ListMessages(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	conversationID := c.Param("id")
	var before *time.Time
	if raw := c.Query("before"); raw != "" {
		parsed, err := time.Parse(time.RFC3339Nano, raw)
		if err != nil {
			parsed, err = time.Parse(time.RFC3339, raw)
		}
		if err != nil {
			writeError(c, apperr.Validation("before must be an RFC3339 timestamp."))
			return
		}
		utc := parsed.UTC()
		before = &utc
	}
	limit := 0
	if raw := c.Query("limit"); raw != "" {
		n, err := strconv.Atoi(raw)
		if err != nil || n < 1 {
			writeError(c, apperr.Validation("limit must be a positive integer."))
			return
		}
		limit = n
	}
	messages, err := h.chat.ListMessages(c.Request.Context(), userID, conversationID, before, limit)
	if err != nil {
		writeError(c, err)
		return
	}
	out := make([]httpapi.Message, 0, len(messages))
	for i := range messages {
		out = append(out, httpapi.NewMessage(&messages[i]))
	}
	c.JSON(http.StatusOK, httpapi.MessageList{Messages: out})
}

func (h *ChatHandler) SendMessage(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	var req httpapi.SendMessage
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	message, err := h.chat.SendMessage(c.Request.Context(), userID, c.Param("id"), req.Body)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusCreated, httpapi.NewMessage(message))
}

func (h *ChatHandler) UploadAttachment(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	limited := http.MaxBytesReader(c.Writer, c.Request.Body, model.MaxAttachmentBytes)
	defer limited.Close()
	stored, created, err := h.media.Save(
		c.Request.Context(),
		userID,
		c.Param("id"),
		c.Param("attachmentId"),
		limited,
	)
	if err != nil {
		writeError(c, err)
		return
	}
	status := http.StatusOK
	if created {
		status = http.StatusCreated
	}
	c.JSON(status, httpapi.AttachmentStored{
		ID:       stored.ID,
		ByteSize: stored.ByteSize,
		Digest:   stored.Digest,
	})
}

func (h *ChatHandler) DownloadAttachment(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	stored, file, err := h.media.Open(
		c.Request.Context(),
		userID,
		c.Param("id"),
		c.Param("attachmentId"),
	)
	if err != nil {
		writeError(c, err)
		return
	}
	defer file.Close()
	c.Header("Content-Type", "application/octet-stream")
	c.Header("Content-Length", strconv.FormatInt(stored.ByteSize, 10))
	c.Header("X-Content-Digest", stored.Digest)
	c.Status(http.StatusOK)
	if _, err := io.Copy(c.Writer, file); err != nil {
		log.Printf("attachment download: %v", err)
	}
}

func toConversation(summary service.ConversationSummary) httpapi.Conversation {
	peer := httpapi.Profile{}
	if summary.Peer != nil {
		peer = httpapi.NewProfile(summary.Peer)
	} else {
		peer = httpapi.NewProfile(&model.User{})
	}
	return httpapi.NewConversation(
		summary.Conversation.ID,
		peer,
		summary.LastMessage,
		summary.Unread,
		summary.Conversation.CreatedAt,
		summary.Conversation.UpdatedAt,
	)
}
