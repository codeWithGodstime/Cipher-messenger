package routes

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func New(health *handler.HealthHandler, auth *handler.AuthHandler, profile *handler.ProfileHandler, authenticate gin.HandlerFunc) http.Handler {
	r := gin.Default()
	r.GET("/health", health.Get)
	r.POST("/auth/otp", auth.RequestOTP)
	r.POST("/auth/verify", auth.Verify)
	r.POST("/auth/refresh", auth.Refresh)
	r.POST("/auth/logout", authenticate, auth.Logout)
	r.GET("/me", authenticate, profile.Get)
	r.PATCH("/me", authenticate, profile.Update)
	return r
}
