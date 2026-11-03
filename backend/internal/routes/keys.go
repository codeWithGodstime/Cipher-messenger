package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerKeys(r *gin.Engine, keys *handler.KeysHandler, authenticate gin.HandlerFunc) {
	r.POST("/keys/", authenticate, keys.CreateIdentityKey)
}
