package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/middleware"
	"messenger/backend/internal/service"
	"messenger/backend/internal/utils/apperr"
	"messenger/backend/internal/utils/httpapi"
)

type KeysHandler struct {
	keyService service.KeyService
}

func NewKeysHandler(keyService service.KeyService) *KeysHandler {
	return &KeysHandler{
		keyService: keyService,
	}
}

func (h *KeysHandler) CreateIdentityKey(c *gin.Context) {
	var req httpapi.CreateIdentityKeyRequest
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	err := h.keyService.CreateIdentityKey(c.Request.Context(), userID, req.PublicKey)
	if err != nil {
		writeError(c, err)
		return
	}
	c.Status(http.StatusNoContent)
}

func (h *KeysHandler) GetIdentityKey(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	key, err := h.keyService.GetIdentityKey(c.Request.Context(), userID)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, httpapi.NewIdentityKey(key))
}
