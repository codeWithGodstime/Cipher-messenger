package handler

import (
	"messenger/backend/internal/service"

	"github.com/gin-gonic/gin"
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

}
