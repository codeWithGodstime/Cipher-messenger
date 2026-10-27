package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/service"
)

type AuthHandler struct {
	auth *service.AuthService
}

func NewAuthHandler(auth *service.AuthService) *AuthHandler {
	return &AuthHandler{auth: auth}
}

func (h *AuthHandler) RequestOTP(c *gin.Context) {
	var req struct {
		Phone string `json:"phone"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	expiresIn, err := h.auth.RequestOTP(c.Request.Context(), req.Phone)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, gin.H{"expires_in": expiresIn})
}
