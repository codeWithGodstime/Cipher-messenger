package repository

import (
	"context"
	"errors"

	"gorm.io/gorm"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type KeyRepository interface {
	CreateIdentityKey(ctx context.Context, key *model.IdentityKey) error
	GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error)
	SaveSignedPreKey(ctx context.Context, key *model.SignedPreKey) error
	ReplaceOneTimePreKeys(ctx context.Context, userID string, keys []model.OneTimePreKey) error
}

type keyRepository struct {
	db *gorm.DB
}

func NewKeyRepository(db *gorm.DB) KeyRepository {
	return &keyRepository{db: db}
}

func (r *keyRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *keyRepository) CreateIdentityKey(ctx context.Context, key *model.IdentityKey) error {
	// One identity key per user. A later upload from the same account replaces it.
	var existing model.IdentityKey
	err := r.conn(ctx).Where("user_id = ?", key.UserID).First(&existing).Error
	if err == nil {
		return r.conn(ctx).Model(&existing).Update("public_key", key.PublicKey).Error
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return err
	}
	return r.conn(ctx).Create(key).Error
}

func (r *keyRepository) GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error) {
	var key model.IdentityKey
	err := r.conn(ctx).Where("user_id = ?", userID).First(&key).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &key, nil
}

func (r *keyRepository) SaveSignedPreKey(ctx context.Context, key *model.SignedPreKey) error {
	// One signed prekey per user. A later upload from the same account replaces it.
	var existing model.SignedPreKey
	err := r.conn(ctx).Where("user_id = ?", key.UserID).First(&existing).Error
	if err == nil {
		return r.conn(ctx).Model(&existing).Updates(map[string]any{
			"key_id":     key.KeyID,
			"public_key": key.PublicKey,
			"signature":  key.Signature,
			"created_at": key.CreatedAt,
		}).Error
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return err
	}
	return r.conn(ctx).Create(key).Error
}

// ReplaceOneTimePreKeys stores keys as the user's current one-time prekey bundle.
// Keys omitted from this upload are removed. The write is one transaction.
func (r *keyRepository) ReplaceOneTimePreKeys(ctx context.Context, userID string, keys []model.OneTimePreKey) error {
	if userID == "" || len(keys) == 0 {
		return errors.New("one-time prekeys require a user and at least one key")
	}
	return r.conn(ctx).Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("user_id = ?", userID).Delete(&model.OneTimePreKey{}).Error; err != nil {
			return err
		}
		return tx.Create(&keys).Error
	})
}
