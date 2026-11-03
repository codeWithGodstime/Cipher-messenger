package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/httpapi"
	"messenger/backend/internal/middleware"
	"messenger/backend/internal/service"
	"messenger/backend/internal/utils/apperr"
)

type AuthHandler struct {
	auth *service.AuthService
}

func NewAuthHandler(auth *service.AuthService) *AuthHandler {
	return &AuthHandler{auth: auth}
}

func (h *AuthHandler) Login(c *gin.Context) {
	var req httpapi.AuthRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	result, err := h.auth.Login(c.Request.Context(), req.Username)
	if err != nil {
		writeError(c, err)
		return
	}
	writeSession(c, result)
}

func (h *AuthHandler) Signup(c *gin.Context) {
	var req httpapi.AuthRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	result, err := h.auth.Signup(c.Request.Context(), req.Username)
	if err != nil {
		writeError(c, err)
		return
	}
	writeSession(c, result)
}

func writeSession(c *gin.Context, result *service.VerifyResult) {
	c.JSON(http.StatusOK, httpapi.NewSession(
		result.AccessToken,
		result.RefreshToken,
		result.ExpiresIn,
		result.Created,
		httpapi.NewProfile(result.User),
	))
}

func (h *AuthHandler) Refresh(c *gin.Context) {
	var req httpapi.RefreshRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	result, err := h.auth.Refresh(c.Request.Context(), req.RefreshToken)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, httpapi.NewTokenPair(result.AccessToken, result.RefreshToken, result.ExpiresIn))
}

func (h *AuthHandler) Logout(c *gin.Context) {
	sessionID, ok := middleware.SessionID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	if err := h.auth.Logout(c.Request.Context(), sessionID); err != nil {
		writeError(c, err)
		return
	}
	c.Status(http.StatusNoContent)
}
