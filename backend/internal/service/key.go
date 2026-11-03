package service

import (
	"context"
	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
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
	return s.repo.CreateIdentityKey(ctx, &model.IdentityKey{UserID: userID, PublicKey: publicKey})
}

func (s *keyService) GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error) {
	return s.repo.GetIdentityKey(ctx, userID)
}
