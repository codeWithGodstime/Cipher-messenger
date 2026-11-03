package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerContacts(r *gin.Engine, contacts *handler.ContactHandler, authenticate gin.HandlerFunc) {
	r.GET("/users/lookup", authenticate, contacts.Lookup)
	r.GET("/contacts", authenticate, contacts.List)
	r.POST("/contacts", authenticate, contacts.Add)
	r.DELETE("/contacts/:id", authenticate, contacts.Delete)
}
