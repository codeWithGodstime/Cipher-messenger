package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/httpapi"
	"messenger/backend/internal/middleware"
	"messenger/backend/internal/service"
)

type ProfileHandler struct {
	profiles *service.ProfileService
}

func NewProfileHandler(profiles *service.ProfileService) *ProfileHandler {
	return &ProfileHandler{profiles: profiles}
}

func (h *ProfileHandler) Get(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	user, err := h.profiles.Get(c.Request.Context(), userID)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, httpapi.NewProfile(user))
}

func (h *ProfileHandler) Update(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	var req httpapi.ProfileUpdate
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	user, err := h.profiles.Update(c.Request.Context(), userID, service.ProfileUpdate{
		DisplayName:  req.DisplayName,
		About:        req.About,
		ProfilePhoto: req.ProfilePhoto,
	})
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, httpapi.NewProfile(user))
}
