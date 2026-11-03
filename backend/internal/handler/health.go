package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/service"
	"messenger/backend/internal/utils/httpapi"
)

type HealthHandler struct {
	health *service.HealthService
}

func NewHealthHandler(health *service.HealthService) *HealthHandler {
	return &HealthHandler{health: health}
}

func (h *HealthHandler) Get(c *gin.Context) {
	if err := h.health.Check(c.Request.Context()); err != nil {
		c.JSON(http.StatusServiceUnavailable, httpapi.Health{Status: httpapi.StatusUnavailable})
		return
	}
	c.JSON(http.StatusOK, httpapi.Health{Status: httpapi.StatusOK})
}
