package handler

import (
	"errors"
	"log"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/model"
)

type profileJSON struct {
	ID           string    `json:"id"`
	Phone        string    `json:"phone"`
	DisplayName  string    `json:"display_name"`
	About        string    `json:"about"`
	ProfilePhoto string    `json:"profile_photo"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
}

func newProfileJSON(user *model.User) profileJSON {
	return profileJSON{
		ID:           user.ID,
		Phone:        user.Phone,
		DisplayName:  user.DisplayName,
		About:        user.About,
		ProfilePhoto: user.ProfilePhoto,
		CreatedAt:    user.CreatedAt,
		UpdatedAt:    user.UpdatedAt,
	}
}

func writeError(c *gin.Context, err error) {
	var apiErr *apperr.Error
	if errors.As(err, &apiErr) {
		if apiErr.RetryAfter > 0 {
			c.Header("Retry-After", strconv.Itoa(apiErr.RetryAfter))
		}
		body := gin.H{"error": apiErr.Code, "message": apiErr.Message}
		if apiErr.RetryAfter > 0 {
			body["retry_after"] = apiErr.RetryAfter
		}
		c.JSON(apiErr.Status, body)
		return
	}
	log.Printf("request failed: %v", err)
	c.JSON(http.StatusInternalServerError, gin.H{
		"error":   "internal",
		"message": "Something went wrong.",
	})
}
