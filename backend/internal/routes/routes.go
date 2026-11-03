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
	registerHealth(r, health)
	registerAuth(r, auth, authenticate)
	registerProfile(r, profile, authenticate)
	registerContacts(r, contacts, authenticate)
	registerChat(r, chat, authenticate)
	registerWS(r, wsHandler)
	return r
}
