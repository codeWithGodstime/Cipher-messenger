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

// OneTimePreKeyInput is one key in an uploaded one-time prekey bundle.
type OneTimePreKeyInput struct {
	KeyID     int
	PublicKey string
}

type KeyService interface {
	CreateIdentityKey(ctx context.Context, userID string, publicKey string) error
	GetIdentityKey(ctx context.Context, userID string) (*model.IdentityKey, error)
	UploadSignedPreKey(ctx context.Context, userID string, keyID int, publicKey, signature string) error
	UploadOneTimePreKeys(ctx context.Context, userID string, prekeys []OneTimePreKeyInput) error
}

type keyService struct {
	repo repository.KeyRepository
}

func NewKeyService(repo repository.KeyRepository) KeyService {
	return &keyService{repo: repo}
}

func (s *keyService) CreateIdentityKey(ctx context.Context, userID string, publicKey string) error {
	encoded, err := normalizeECPublicKey(publicKey, "public_key must be a base64 identity public key.")
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

func (s *keyService) UploadSignedPreKey(ctx context.Context, userID string, keyID int, publicKey, signature string) error {
	idValue, err := normalizeKeyID(keyID)
	if err != nil {
		return err
	}
	encodedKey, err := normalizeECPublicKey(publicKey, "public_key must be a base64 prekey public key.")
	if err != nil {
		return err
	}
	encodedSignature, err := normalizeSignature(signature)
	if err != nil {
		return err
	}
	rowID, err := utils.NewID()
	if err != nil {
		return err
	}
	return s.repo.SaveSignedPreKey(ctx, &model.SignedPreKey{
		ID:        rowID,
		UserID:    userID,
		KeyID:     idValue,
		PublicKey: encodedKey,
		Signature: encodedSignature,
		CreatedAt: time.Now().UTC(),
	})
}

func (s *keyService) UploadOneTimePreKeys(ctx context.Context, userID string, prekeys []OneTimePreKeyInput) error {
	if len(prekeys) < 1 || len(prekeys) > model.MaxOneTimePreKeys {
		return apperr.Validation("prekeys must contain 1 to 100 keys.")
	}
	seen := make(map[int]struct{}, len(prekeys))
	rows := make([]model.OneTimePreKey, 0, len(prekeys))
	now := time.Now().UTC()
	for _, item := range prekeys {
		idValue, err := normalizeKeyID(item.KeyID)
		if err != nil {
			return err
		}
		if _, ok := seen[idValue]; ok {
			return apperr.Validation("prekeys contains a duplicate key_id.")
		}
		seen[idValue] = struct{}{}
		encodedKey, err := normalizeECPublicKey(item.PublicKey, "public_key must be a base64 prekey public key.")
		if err != nil {
			return err
		}
		rowID, err := utils.NewID()
		if err != nil {
			return err
		}
		rows = append(rows, model.OneTimePreKey{
			ID:        rowID,
			UserID:    userID,
			KeyID:     idValue,
			PublicKey: encodedKey,
			CreatedAt: now,
		})
	}
	return s.repo.ReplaceOneTimePreKeys(ctx, userID, rows)
}

// libsignal EC public keys serialize to 33 bytes (type byte + 32-byte key).
const ecPublicKeyBytes = 33

// libsignal signed-prekey signatures are 64-byte XEdDSA signatures.
const signedPreKeySignatureBytes = 64

// libsignal prekey ids are medium integers, 0 through 0xFFFFFF.
const maxPreKeyID = 0xFFFFFF

func normalizeECPublicKey(raw, message string) (string, error) {
	encoded := strings.TrimSpace(raw)
	if encoded == "" || len(encoded) > 256 {
		return "", apperr.Validation(message)
	}
	decoded, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil || len(decoded) != ecPublicKeyBytes {
		return "", apperr.Validation(message)
	}
	return encoded, nil
}

func normalizeSignature(raw string) (string, error) {
	encoded := strings.TrimSpace(raw)
	if encoded == "" || len(encoded) > 256 {
		return "", apperr.Validation("signature must be a base64 signed prekey signature.")
	}
	decoded, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil || len(decoded) != signedPreKeySignatureBytes {
		return "", apperr.Validation("signature must be a base64 signed prekey signature.")
	}
	return encoded, nil
}

func normalizeKeyID(id int) (int, error) {
	if id < 0 || id > maxPreKeyID {
		return 0, apperr.Validation("key_id must be from 0 through 16777215.")
	}
	return id, nil
}
