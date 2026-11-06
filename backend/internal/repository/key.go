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
	SaveKyberPreKey(ctx context.Context, key *model.KyberPreKey) error
	// CountOneTimePreKeys is how many one-time prekeys this user still has stored.
	CountOneTimePreKeys(ctx context.Context, userID string) (int, error)
	// AppendOneTimePreKeys adds keys to the user's pool and leaves stored keys in place.
	// A key_id already stored with the same public key is skipped. ErrDuplicate means
	// a key_id is stored with a different public key. ErrPreKeyLimit means the new
	// keys would grow the pool past MaxStoredOneTimePreKeys. Neither failure writes.
	AppendOneTimePreKeys(ctx context.Context, userID string, keys []model.OneTimePreKey) error
	// TakePreKeyBundle reads the identity key, signed prekey, and Kyber prekey,
	// and removes one one-time prekey. The one-time prekey is nil when the user
	// has none left. Identity, signed, or Kyber is nil when that row is missing,
	// and no one-time prekey is removed in that case.
	TakePreKeyBundle(ctx context.Context, userID string) (*model.IdentityKey, *model.SignedPreKey, *model.KyberPreKey, *model.OneTimePreKey, error)
	// ListPeerPublicKeys returns identity, signed, and Kyber prekeys for the given users.
	// It does not remove one-time prekeys. Missing users are absent from the maps.
	ListPeerPublicKeys(ctx context.Context, userIDs []string) (map[string]model.IdentityKey, map[string]model.SignedPreKey, map[string]model.KyberPreKey, error)
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

func (r *keyRepository) SaveKyberPreKey(ctx context.Context, key *model.KyberPreKey) error {
	// One Kyber prekey per user. A later upload from the same account replaces it.
	var existing model.KyberPreKey
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
func (r *keyRepository) TakePreKeyBundle(ctx context.Context, userID string) (*model.IdentityKey, *model.SignedPreKey, *model.KyberPreKey, *model.OneTimePreKey, error) {
	var identity *model.IdentityKey
	var signed *model.SignedPreKey
	var kyber *model.KyberPreKey
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

		var kyberKey model.KyberPreKey
		err = tx.Where("user_id = ?", userID).First(&kyberKey).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil
		}
		if err != nil {
			return err
		}
		kyber = &kyberKey

		claimed, err := claimOneTimePreKey(tx, userID)
		if err != nil {
			return err
		}
		oneTime = claimed
		return nil
	})
	if err != nil {
		return nil, nil, nil, nil, err
	}
	return identity, signed, kyber, oneTime, nil
}

// ListPeerPublicKeys loads reusable public keys. One-time prekeys stay in the pool.
func (r *keyRepository) ListPeerPublicKeys(ctx context.Context, userIDs []string) (map[string]model.IdentityKey, map[string]model.SignedPreKey, map[string]model.KyberPreKey, error) {
	identities := map[string]model.IdentityKey{}
	signed := map[string]model.SignedPreKey{}
	kyber := map[string]model.KyberPreKey{}
	if len(userIDs) == 0 {
		return identities, signed, kyber, nil
	}
	var identityRows []model.IdentityKey
	if err := r.conn(ctx).Where("user_id IN ?", userIDs).Find(&identityRows).Error; err != nil {
		return nil, nil, nil, err
	}
	var signedRows []model.SignedPreKey
	if err := r.conn(ctx).Where("user_id IN ?", userIDs).Find(&signedRows).Error; err != nil {
		return nil, nil, nil, err
	}
	var kyberRows []model.KyberPreKey
	if err := r.conn(ctx).Where("user_id IN ?", userIDs).Find(&kyberRows).Error; err != nil {
		return nil, nil, nil, err
	}
	for _, row := range identityRows {
		identities[row.UserID] = row
	}
	for _, row := range signedRows {
		signed[row.UserID] = row
	}
	for _, row := range kyberRows {
		kyber[row.UserID] = row
	}
	return identities, signed, kyber, nil
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

// ErrPreKeyLimit means an upload would leave the user with more one-time
// prekeys than MaxStoredOneTimePreKeys.
var ErrPreKeyLimit = errors.New("one-time prekey limit")

// CountOneTimePreKeys counts keys still available to claim.
func (r *keyRepository) CountOneTimePreKeys(ctx context.Context, userID string) (int, error) {
	var count int64
	err := r.conn(ctx).Model(&model.OneTimePreKey{}).Where("user_id = ?", userID).Count(&count).Error
	if err != nil {
		return 0, err
	}
	return int(count), nil
}

// AppendOneTimePreKeys adds keys to the user's pool and leaves stored keys in place.
// The count and duplicate checks run in the same transaction as the insert.
// Postgres locks the user row so two uploads cannot pass the cap together.
func (r *keyRepository) AppendOneTimePreKeys(ctx context.Context, userID string, keys []model.OneTimePreKey) error {
	if userID == "" || len(keys) == 0 {
		return errors.New("one-time prekeys require a user and at least one key")
	}
	return r.conn(ctx).Transaction(func(tx *gorm.DB) error {
		if tx.Dialector.Name() == "postgres" {
			var owner model.User
			err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
				Select("id").
				Where("id = ?", userID).
				First(&owner).Error
			if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
				return err
			}
		}
		var count int64
		if err := tx.Model(&model.OneTimePreKey{}).Where("user_id = ?", userID).Count(&count).Error; err != nil {
			return err
		}
		ids := make([]int, len(keys))
		for i, key := range keys {
			ids[i] = key.KeyID
		}
		var existing []model.OneTimePreKey
		err := tx.Where("user_id = ? AND key_id IN ?", userID, ids).Find(&existing).Error
		if err != nil {
			return err
		}
		stored := make(map[int]string, len(existing))
		for _, row := range existing {
			stored[row.KeyID] = row.PublicKey
		}
		fresh := make([]model.OneTimePreKey, 0, len(keys))
		for _, key := range keys {
			current, ok := stored[key.KeyID]
			if !ok {
				fresh = append(fresh, key)
				continue
			}
			if current != key.PublicKey {
				return ErrDuplicate
			}
		}
		if int(count)+len(fresh) > model.MaxStoredOneTimePreKeys {
			return ErrPreKeyLimit
		}
		if len(fresh) == 0 {
			return nil
		}
		if err := tx.Create(&fresh).Error; err != nil {
			if errors.Is(err, gorm.ErrDuplicatedKey) {
				return ErrDuplicate
			}
			return err
		}
		return nil
	})
}
