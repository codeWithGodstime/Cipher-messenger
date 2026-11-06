package repository

import (
	"context"
	"errors"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type KeyRepository interface {
	CreateIdentityKey(ctx context.Context, key *model.IdentityKey) error
	GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error)
	SaveSignedPreKey(ctx context.Context, key *model.SignedPreKey) error
	ReplaceOneTimePreKeys(ctx context.Context, userID string, keys []model.OneTimePreKey) error
	// TakePreKeyBundle reads the identity key and signed prekey and removes one
	// one-time prekey. The one-time prekey is nil when the user has none left.
	// Identity or signed is nil when that row is missing, and no one-time prekey
	// is removed in that case.
	TakePreKeyBundle(ctx context.Context, userID string) (*model.IdentityKey, *model.SignedPreKey, *model.OneTimePreKey, error)
	// ListPeerPublicKeys returns identity and signed prekeys for the given users.
	// It does not remove one-time prekeys. Missing users are absent from the maps.
	ListPeerPublicKeys(ctx context.Context, userIDs []string) (map[string]model.IdentityKey, map[string]model.SignedPreKey, error)
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

// TakePreKeyBundle loads the keys a peer needs to open a Signal session.
// The one-time prekey is deleted in the same transaction so two callers cannot
// receive it. Postgres locks the chosen row; SQLite serializes writers.
func (r *keyRepository) TakePreKeyBundle(ctx context.Context, userID string) (*model.IdentityKey, *model.SignedPreKey, *model.OneTimePreKey, error) {
	var identity *model.IdentityKey
	var signed *model.SignedPreKey
	var oneTime *model.OneTimePreKey
	err := r.conn(ctx).Transaction(func(tx *gorm.DB) error {
		var idKey model.IdentityKey
		err := tx.Where("user_id = ?", userID).First(&idKey).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil
		}
		if err != nil {
			return err
		}
		identity = &idKey

		var signedKey model.SignedPreKey
		err = tx.Where("user_id = ?", userID).First(&signedKey).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil
		}
		if err != nil {
			return err
		}
		signed = &signedKey

		claimed, err := claimOneTimePreKey(tx, userID)
		if err != nil {
			return err
		}
		oneTime = claimed
		return nil
	})
	if err != nil {
		return nil, nil, nil, err
	}
	return identity, signed, oneTime, nil
}

// ListPeerPublicKeys loads reusable public keys. One-time prekeys stay in the pool.
func (r *keyRepository) ListPeerPublicKeys(ctx context.Context, userIDs []string) (map[string]model.IdentityKey, map[string]model.SignedPreKey, error) {
	identities := map[string]model.IdentityKey{}
	signed := map[string]model.SignedPreKey{}
	if len(userIDs) == 0 {
		return identities, signed, nil
	}
	var identityRows []model.IdentityKey
	if err := r.conn(ctx).Where("user_id IN ?", userIDs).Find(&identityRows).Error; err != nil {
		return nil, nil, err
	}
	var signedRows []model.SignedPreKey
	if err := r.conn(ctx).Where("user_id IN ?", userIDs).Find(&signedRows).Error; err != nil {
		return nil, nil, err
	}
	for _, row := range identityRows {
		identities[row.UserID] = row
	}
	for _, row := range signedRows {
		signed[row.UserID] = row
	}
	return identities, signed, nil
}

// claimOneTimePreKey removes the oldest one-time prekey for the user.
// Equal upload times break ties by key id.
func claimOneTimePreKey(tx *gorm.DB, userID string) (*model.OneTimePreKey, error) {
	query := tx.Where("user_id = ?", userID).Order("created_at ASC").Order("key_id ASC")
	if tx.Dialector.Name() == "postgres" {
		query = query.Clauses(clause.Locking{Strength: "UPDATE"})
	}
	var key model.OneTimePreKey
	err := query.First(&key).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if err := tx.Delete(&model.OneTimePreKey{}, "id = ?", key.ID).Error; err != nil {
		return nil, err
	}
	return &key, nil
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
