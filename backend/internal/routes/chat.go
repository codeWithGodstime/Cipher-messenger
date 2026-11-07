package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerChat(r *gin.Engine, chat *handler.ChatHandler, authenticate gin.HandlerFunc) {
	r.GET("/conversations", authenticate, chat.ListConversations)
	r.POST("/conversations", authenticate, chat.OpenConversation)
	r.GET("/conversations/:id/messages", authenticate, chat.ListMessages)
	r.POST("/conversations/:id/messages", authenticate, chat.SendMessage)
	r.PUT("/conversations/:id/attachments/:attachmentId", authenticate, chat.UploadAttachment)
	r.GET("/conversations/:id/attachments/:attachmentId", authenticate, chat.DownloadAttachment)
}
