package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerHealth(r *gin.Engine, health *handler.HealthHandler) {
	r.GET("/health", health.Get)
}
