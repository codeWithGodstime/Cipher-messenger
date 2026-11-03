package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerWS(r *gin.Engine, wsHandler *handler.WSHandler) {
	r.GET("/ws", wsHandler.Connect)
}
