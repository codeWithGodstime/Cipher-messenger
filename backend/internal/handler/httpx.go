package handler

import (
	"errors"
	"log"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/httpapi"
)

func writeError(c *gin.Context, err error) {
	var apiErr *apperr.Error
	if errors.As(err, &apiErr) {
		if apiErr.RetryAfter > 0 {
			c.Header("Retry-After", strconv.Itoa(apiErr.RetryAfter))
		}
		c.JSON(apiErr.Status, httpapi.NewError(apiErr.Code, apiErr.Message, apiErr.RetryAfter))
		return
	}
	log.Printf("request failed: %v", err)
	c.JSON(http.StatusInternalServerError, httpapi.InternalError())
}
