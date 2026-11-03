package middleware

import (
	"errors"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/repository"
	"messenger/backend/internal/token"
	"messenger/backend/internal/utils/apperr"
	"messenger/backend/internal/utils/httpapi"
)

const (
	userIDKey    = "auth_user_id"
	sessionIDKey = "auth_session_id"
)

func RequireAccess(tokens *token.Manager, sessions repository.SessionRepository) gin.HandlerFunc {
	return func(c *gin.Context) {
		raw, ok := bearerToken(c.GetHeader("Authorization"))
		if !ok {
			abortAuth(c, apperr.Unauthorized())
			return
		}
		userID, sessionID, err := tokens.Parse(raw)
		if err != nil {
			abortAuth(c, apperr.Unauthorized())
			return
		}
		session, err := sessions.FindByID(c.Request.Context(), sessionID)
		if err != nil {
			abortAuth(c, err)
			return
		}
		now := time.Now().UTC()
		if session == nil || session.UserID != userID || session.RevokedAt != nil || !session.ExpiresAt.After(now) {
			abortAuth(c, apperr.Unauthorized())
			return
		}
		c.Set(userIDKey, userID)
		c.Set(sessionIDKey, sessionID)
		c.Next()
	}
}

func UserID(c *gin.Context) (string, bool) {
	return stringValue(c, userIDKey)
}

func SessionID(c *gin.Context) (string, bool) {
	return stringValue(c, sessionIDKey)
}

func stringValue(c *gin.Context, key string) (string, bool) {
	v, ok := c.Get(key)
	if !ok {
		return "", false
	}
	s, ok := v.(string)
	if !ok || s == "" {
		return "", false
	}
	return s, true
}

func bearerToken(header string) (string, bool) {
	const prefix = "Bearer "
	if len(header) < len(prefix) || !strings.EqualFold(header[:len(prefix)], prefix) {
		return "", false
	}
	raw := strings.TrimSpace(header[len(prefix):])
	if raw == "" || strings.Contains(raw, " ") {
		return "", false
	}
	return raw, true
}

func abortAuth(c *gin.Context, err error) {
	var apiErr *apperr.Error
	if errors.As(err, &apiErr) {
		c.AbortWithStatusJSON(apiErr.Status, httpapi.NewError(apiErr.Code, apiErr.Message, apiErr.RetryAfter))
		return
	}
	log.Printf("request failed: %v", err)
	c.AbortWithStatusJSON(http.StatusInternalServerError, httpapi.InternalError())
}
