package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerProfile(r *gin.Engine, profile *handler.ProfileHandler, authenticate gin.HandlerFunc) {
	r.GET("/me", authenticate, profile.Get)
	r.PATCH("/me", authenticate, profile.Update)
}
