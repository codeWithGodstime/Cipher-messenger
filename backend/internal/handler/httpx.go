package handler

import (
	"errors"
	"log"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/apperr"
)

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
