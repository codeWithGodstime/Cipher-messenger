package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerAuth(r *gin.Engine, auth *handler.AuthHandler, authenticate gin.HandlerFunc) {
	r.POST("/auth/login", auth.Login)
	r.POST("/auth/signup", auth.Signup)
	r.POST("/auth/refresh", auth.Refresh)
	r.POST("/auth/logout", authenticate, auth.Logout)
}
