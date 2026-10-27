package routes

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func New(health *handler.HealthHandler, auth *handler.AuthHandler) http.Handler {
	r := gin.Default()
	r.GET("/health", health.Get)
	r.POST("/auth/otp", auth.RequestOTP)
	return r
}
