package routes

import (
	"github.com/gin-gonic/gin"

	"messenger/backend/internal/handler"
)

func registerKeys(r *gin.Engine, keys *handler.KeysHandler, authenticate gin.HandlerFunc) {
	r.GET("/keys/", authenticate, keys.GetIdentityKey)
	r.POST("/keys/", authenticate, keys.CreateIdentityKey)
	r.POST("/keys/signed-prekey", authenticate, keys.UploadSignedPreKey)
	r.POST("/keys/kyber-prekey", authenticate, keys.UploadKyberPreKey)
	r.GET("/keys/one-time-prekeys", authenticate, keys.OneTimePreKeyCount)
	r.POST("/keys/one-time-prekeys", authenticate, keys.UploadOneTimePreKeys)
	r.POST("/keys/:user_id/bundle", authenticate, keys.GetPreKeyBundle)
}
