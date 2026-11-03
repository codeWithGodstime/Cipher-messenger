package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/middleware"
	"messenger/backend/internal/model"
	"messenger/backend/internal/service"
	"messenger/backend/internal/utils/apperr"
	"messenger/backend/internal/utils/httpapi"
)

type ContactHandler struct {
	contacts *service.ContactService
}

func NewContactHandler(contacts *service.ContactService) *ContactHandler {
	return &ContactHandler{contacts: contacts}
}

func (h *ContactHandler) List(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	summaries, err := h.contacts.List(c.Request.Context(), userID)
	if err != nil {
		writeError(c, err)
		return
	}
	out := make([]httpapi.Contact, 0, len(summaries))
	for _, summary := range summaries {
		out = append(out, toContact(summary))
	}
	c.JSON(http.StatusOK, httpapi.ContactList{Contacts: out})
}

func (h *ContactHandler) Add(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	var req httpapi.AddContact
	if err := c.ShouldBindJSON(&req); err != nil {
		writeError(c, apperr.InvalidBody())
		return
	}
	summary, err := h.contacts.Add(c.Request.Context(), userID, req.UserID, req.Username)
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusCreated, toContact(*summary))
}

func (h *ContactHandler) Delete(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	if err := h.contacts.Delete(c.Request.Context(), userID, c.Param("id")); err != nil {
		writeError(c, err)
		return
	}
	c.Status(http.StatusNoContent)
}

func (h *ContactHandler) Lookup(c *gin.Context) {
	userID, ok := middleware.UserID(c)
	if !ok {
		writeError(c, apperr.Unauthorized())
		return
	}
	peer, err := h.contacts.LookupByUsername(c.Request.Context(), userID, c.Query("username"))
	if err != nil {
		writeError(c, err)
		return
	}
	c.JSON(http.StatusOK, httpapi.NewProfile(peer))
}

func toContact(summary service.ContactSummary) httpapi.Contact {
	peer := httpapi.Profile{}
	if summary.Peer != nil {
		peer = httpapi.NewProfile(summary.Peer)
	} else {
		peer = httpapi.NewProfile(&model.User{})
	}
	return httpapi.NewContact(summary.Contact.ID, peer, summary.Contact.CreatedAt)
}
