package routes

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func New(health *handler.HealthHandler) http.Handler {
	r := gin.Default()
	r.GET("/health", health.Get)
	return r
}
