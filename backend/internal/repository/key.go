package repository

import (
	"messenger/backend/internal/model"

	"context"

	"gorm.io/gorm"
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
	return r.db.WithContext(ctx).Create(key).Error
}

func (r *keyRepository) GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error) {
	var key model.IdentityKey
	if err := r.db.WithContext(ctx).Where("user_id = ?", userID).First(&key).Error; err != nil {
		return nil, err
	}
	return &key, nil
}
