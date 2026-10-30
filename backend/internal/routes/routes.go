package routes

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func New(
	health *handler.HealthHandler,
	auth *handler.AuthHandler,
	profile *handler.ProfileHandler,
	chat *handler.ChatHandler,
	contacts *handler.ContactHandler,
	wsHandler *handler.WSHandler,
	authenticate gin.HandlerFunc,
) http.Handler {
	r := gin.Default()
	r.GET("/health", health.Get)
	r.POST("/auth/otp", auth.RequestOTP)
	r.POST("/auth/verify", auth.Verify)
	r.POST("/auth/refresh", auth.Refresh)
	r.POST("/auth/logout", authenticate, auth.Logout)
	r.GET("/me", authenticate, profile.Get)
	r.PATCH("/me", authenticate, profile.Update)
	r.GET("/users/lookup", authenticate, contacts.Lookup)
	r.GET("/contacts", authenticate, contacts.List)
	r.POST("/contacts", authenticate, contacts.Add)
	r.DELETE("/contacts/:id", authenticate, contacts.Delete)
	r.GET("/conversations", authenticate, chat.ListConversations)
	r.POST("/conversations", authenticate, chat.OpenConversation)
	r.GET("/conversations/:id/messages", authenticate, chat.ListMessages)
	r.POST("/conversations/:id/messages", authenticate, chat.SendMessage)
	r.GET("/ws", wsHandler.Connect)
	return r
}
