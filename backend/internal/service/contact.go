package service

import (
	"context"
	"strings"
	"time"

	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/utils"
	"messenger/backend/internal/utils/apperr"
)

type ContactService struct {
	users    repository.UserRepository
	contacts repository.ContactRepository
}

func NewContactService(users repository.UserRepository, contacts repository.ContactRepository) *ContactService {
	return &ContactService{users: users, contacts: contacts}
}

type ContactSummary struct {
	Contact model.Contact
	Peer    *model.User
}

func (s *ContactService) List(ctx context.Context, ownerID string) ([]ContactSummary, error) {
	contacts, err := s.contacts.ListForOwner(ctx, ownerID)
	if err != nil {
		return nil, err
	}
	if len(contacts) == 0 {
		return nil, nil
	}
	peerIDs := make([]string, 0, len(contacts))
	for _, contact := range contacts {
		peerIDs = append(peerIDs, contact.PeerID)
	}
	peers, err := s.users.FindByIDs(ctx, peerIDs)
	if err != nil {
		return nil, err
	}
	peerByID := make(map[string]*model.User, len(peers))
	for i := range peers {
		peerByID[peers[i].ID] = &peers[i]
	}
	out := make([]ContactSummary, 0, len(contacts))
	for _, contact := range contacts {
		summary := ContactSummary{Contact: contact, Peer: peerByID[contact.PeerID]}
		out = append(out, summary)
	}
	return out, nil
}

func (s *ContactService) Add(ctx context.Context, ownerID, peerUserID, rawUsername string) (*ContactSummary, error) {
	peer, err := resolvePeer(ctx, s.users, ownerID, peerUserID, rawUsername)
	if err != nil {
		return nil, err
	}
	existing, err := s.contacts.FindByOwnerPeer(ctx, ownerID, peer.ID)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		return &ContactSummary{Contact: *existing, Peer: peer}, nil
	}
	id, err := utils.NewID()
	if err != nil {
		return nil, err
	}
	contact := &model.Contact{
		ID:        id,
		OwnerID:   ownerID,
		PeerID:    peer.ID,
		CreatedAt: time.Now().UTC(),
	}
	if err := s.contacts.Create(ctx, contact); err != nil {
		return nil, err
	}
	return &ContactSummary{Contact: *contact, Peer: peer}, nil
}

func (s *ContactService) Delete(ctx context.Context, ownerID, contactID string) error {
	contactID = strings.TrimSpace(contactID)
	if contactID == "" {
		return apperr.Validation("contact id is required.")
	}
	ok, err := s.contacts.Delete(ctx, contactID, ownerID)
	if err != nil {
		return err
	}
	if !ok {
		return apperr.NotFound("Contact not found.")
	}
	return nil
}

func (s *ContactService) LookupByUsername(ctx context.Context, ownerID, rawUsername string) (*model.User, error) {
	name, err := utils.NormalizeUsername(rawUsername)
	if err != nil {
		return nil, apperr.InvalidUsername()
	}
	peer, err := s.users.FindByUsername(ctx, name)
	if err != nil {
		return nil, err
	}
	if peer == nil {
		return nil, apperr.UserDoesNotExist()
	}
	if peer.ID == ownerID {
		return nil, apperr.Validation("Cannot look up yourself.")
	}
	return peer, nil
}

func resolvePeer(ctx context.Context, users repository.UserRepository, ownerID, peerUserID, rawUsername string) (*model.User, error) {
	peerUserID = strings.TrimSpace(peerUserID)
	rawUsername = strings.TrimSpace(rawUsername)
	if peerUserID == "" && rawUsername == "" {
		return nil, apperr.Validation("user_id or username is required.")
	}
	if peerUserID != "" && rawUsername != "" {
		return nil, apperr.Validation("Provide either user_id or username, not both.")
	}
	if peerUserID != "" {
		if peerUserID == ownerID {
			return nil, apperr.Validation("Cannot connect with yourself.")
		}
		peer, err := users.FindByID(ctx, peerUserID)
		if err != nil {
			return nil, err
		}
		if peer == nil {
			return nil, apperr.UserDoesNotExist()
		}
		return peer, nil
	}
	name, err := utils.NormalizeUsername(rawUsername)
	if err != nil {
		return nil, apperr.InvalidUsername()
	}
	peer, err := users.FindByUsername(ctx, name)
	if err != nil {
		return nil, err
	}
	if peer == nil {
		return nil, apperr.UserDoesNotExist()
	}
	if peer.ID == ownerID {
		return nil, apperr.Validation("Cannot connect with yourself.")
	}
	return peer, nil
}
