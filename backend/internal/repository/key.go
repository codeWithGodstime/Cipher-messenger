package repository

import (
	"context"
	"errors"

	"gorm.io/gorm"

	"messenger/backend/internal/model"
)

type KeyRepository interface {
	CreateIdentityKey(ctx context.Context, key *model.IdentityKey) error
	GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error)
}

type keyRepository struct {
	db *gorm.DB
}

func NewKeyRepository(db *gorm.DB) KeyRepository {
	return &keyRepository{db: db}
}

func (r *keyRepository) CreateIdentityKey(ctx context.Context, key *model.IdentityKey) error {
	// One identity key per user. A later upload from the same account replaces it.
	var existing model.IdentityKey
	err := r.db.WithContext(ctx).Where("user_id = ?", key.UserID).First(&existing).Error
	if err == nil {
		return r.db.WithContext(ctx).Model(&existing).Update("public_key", key.PublicKey).Error
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return err
	}
	return r.db.WithContext(ctx).Create(key).Error
}

func (r *keyRepository) GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error) {
	var key model.IdentityKey
	err := r.db.WithContext(ctx).Where("user_id = ?", userID).First(&key).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &key, nil
}
