package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/middleware"
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
	result, err := h.auth.RequestOTP(c.Request.Context(), req.Phone)
	if err != nil {
		writeError(c, err)
		return
	}
	if result.Login != nil {
		writeSession(c, result.Login)
		return
	}
	c.JSON(http.StatusOK, gin.H{"expires_in": result.ExpiresIn})
}

func (h *AuthHandler) Verify(c *gin.Context) {
	var req struct {
		Phone string `json:"phone"`
		Code  string `json:"code"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	result, err := h.auth.Verify(c.Request.Context(), req.Phone, req.Code)
	if err != nil {
		writeError(c, err)
		return
	}
	writeSession(c, result)
}

func writeSession(c *gin.Context, result *service.VerifyResult) {
	c.JSON(http.StatusOK, gin.H{
		"access_token":  result.AccessToken,
		"refresh_token": result.RefreshToken,
		"token_type":    "Bearer",
		"expires_in":    result.ExpiresIn,
		"created":       result.Created,
		"profile":       newProfileJSON(result.User),
	})
}

func (h *AuthHandler) Refresh(c *gin.Context) {
	var req struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	result, err := h.auth.Refresh(c.Request.Context(), req.RefreshToken)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"access_token":  result.AccessToken,
		"refresh_token": result.RefreshToken,
		"token_type":    "Bearer",
		"expires_in":    result.ExpiresIn,
	})
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
