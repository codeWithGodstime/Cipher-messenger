package service

import (
	"context"
	"encoding/base64"
	"strings"
	"time"

	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/utils"
	"messenger/backend/internal/utils/apperr"
)

type KeyService interface {
	CreateIdentityKey(ctx context.Context, userID string, publicKey string) error
	GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error)
}

type keyService struct {
	repo repository.KeyRepository
}

func NewKeyService(repo repository.KeyRepository) KeyService {
	return &keyService{repo: repo}
}

func (s *keyService) CreateIdentityKey(ctx context.Context, userID string, publicKey string) error {
	encoded, err := normalizeIdentityPublicKey(publicKey)
	if err != nil {
		return err
	}
	id, err := utils.NewID()
	if err != nil {
		return err
	}
	return s.repo.CreateIdentityKey(ctx, &model.IdentityKey{
		ID:        id,
		UserID:    userID,
		PublicKey: encoded,
		CreatedAt: time.Now().UTC(),
	})
}

// libsignal identity public keys serialize to 33 bytes (type byte + 32-byte key).
const identityPublicKeyBytes = 33

func normalizeIdentityPublicKey(raw string) (string, error) {
	encoded := strings.TrimSpace(raw)
	if encoded == "" || len(encoded) > 256 {
		return "", apperr.Validation("public_key must be a base64 identity public key.")
	}
	decoded, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil || len(decoded) != identityPublicKeyBytes {
		return "", apperr.Validation("public_key must be a base64 identity public key.")
	}
	return encoded, nil
}

func (s *keyService) GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error) {
	key, err := s.repo.GetIdentityKey(ctx, userID)
	if err != nil {
		return nil, err
	}
	if key == nil {
		return nil, apperr.NotFound("Identity key not found.")
	}
	return key, nil
}
