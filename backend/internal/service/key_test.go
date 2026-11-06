package service

import (
	"bytes"
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"testing"
	"time"

	"github.com/glebarez/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/utils/apperr"
)

func TestOneTimePreKeysAppendAndRefill(t *testing.T) {
	ctx := context.Background()
	svc, repo := newKeyTestService(t)

	user := &model.User{
		ID:          "user-1",
		Username:    "ada",
		DisplayName: "Ada",
		About:       model.DefaultAbout,
		CreatedAt:   time.Now().UTC(),
		UpdatedAt:   time.Now().UTC(),
	}
	if err := repository.NewUserRepository(repo).Create(ctx, user); err != nil {
		t.Fatalf("create user: %v", err)
	}
	if err := svc.CreateIdentityKey(ctx, user.ID, testKey(1)); err != nil {
		t.Fatalf("identity: %v", err)
	}
	if err := svc.UploadSignedPreKey(ctx, user.ID, 1, testKey(2), testSignature()); err != nil {
		t.Fatalf("signed prekey: %v", err)
	}
	if err := svc.UploadKyberPreKey(ctx, user.ID, 1, testKyber(), testSignature()); err != nil {
		t.Fatalf("kyber prekey: %v", err)
	}

	first := []OneTimePreKeyInput{{KeyID: 1, PublicKey: testKey(3)}, {KeyID: 2, PublicKey: testKey(4)}}
	if err := svc.UploadOneTimePreKeys(ctx, user.ID, first); err != nil {
		t.Fatalf("first upload: %v", err)
	}
	if err := svc.UploadOneTimePreKeys(ctx, user.ID, []OneTimePreKeyInput{{KeyID: 3, PublicKey: testKey(5)}}); err != nil {
		t.Fatalf("second upload: %v", err)
	}
	count, err := svc.CountOneTimePreKeys(ctx, user.ID)
	if err != nil {
		t.Fatalf("count: %v", err)
	}
	if count != 3 {
		t.Fatalf("count = %d, want 3 after an append", count)
	}

	if err := svc.UploadOneTimePreKeys(ctx, user.ID, first); err != nil {
		t.Fatalf("repeat upload: %v", err)
	}
	count, err = svc.CountOneTimePreKeys(ctx, user.ID)
	if err != nil {
		t.Fatalf("count after repeat: %v", err)
	}
	if count != 3 {
		t.Fatalf("count = %d, want 3 when the same public keys are uploaded again", count)
	}

	_, _, _, claimed, err := svc.GetPreKeyBundle(ctx, user.ID)
	if err != nil {
		t.Fatalf("claim: %v", err)
	}
	if claimed == nil || claimed.KeyID != 1 {
		t.Fatalf("claimed %#v, want the oldest key id 1", claimed)
	}
	count, err = svc.CountOneTimePreKeys(ctx, user.ID)
	if err != nil {
		t.Fatalf("count after claim: %v", err)
	}
	if count != 2 {
		t.Fatalf("count = %d, want 2 after one claim", count)
	}

	err = svc.UploadOneTimePreKeys(ctx, user.ID, []OneTimePreKeyInput{{KeyID: 2, PublicKey: testKey(9)}})
	var apiErr *apperr.Error
	if !errors.As(err, &apiErr) || apiErr.Status != 409 {
		t.Fatalf("duplicate upload err = %v, want 409", err)
	}
	count, err = svc.CountOneTimePreKeys(ctx, user.ID)
	if err != nil {
		t.Fatalf("count after duplicate: %v", err)
	}
	if count != 2 {
		t.Fatalf("count = %d, want the duplicate upload to leave the pool unchanged", count)
	}
}

func TestOneTimePreKeyPoolCap(t *testing.T) {
	ctx := context.Background()
	svc, _ := newKeyTestService(t)

	for batch := 0; batch < 2; batch++ {
		keys := make([]OneTimePreKeyInput, model.MaxOneTimePreKeys)
		for i := range keys {
			id := batch*model.MaxOneTimePreKeys + i + 1
			keys[i] = OneTimePreKeyInput{KeyID: id, PublicKey: testKey(byte(id))}
		}
		if err := svc.UploadOneTimePreKeys(ctx, "user-1", keys); err != nil {
			t.Fatalf("batch %d: %v", batch, err)
		}
	}
	err := svc.UploadOneTimePreKeys(ctx, "user-1", []OneTimePreKeyInput{{KeyID: 201, PublicKey: testKey(8)}})
	var apiErr *apperr.Error
	if !errors.As(err, &apiErr) || apiErr.Status != 400 {
		t.Fatalf("over cap err = %v, want 400", err)
	}
	count, err := svc.CountOneTimePreKeys(ctx, "user-1")
	if err != nil {
		t.Fatalf("count: %v", err)
	}
	if count != model.MaxStoredOneTimePreKeys {
		t.Fatalf("count = %d, want the cap %d", count, model.MaxStoredOneTimePreKeys)
	}
}

func newKeyTestService(t *testing.T) (KeyService, *gorm.DB) {
	t.Helper()
	dsn := fmt.Sprintf("file:%s?mode=memory&cache=shared", t.Name())
	db, err := gorm.Open(sqlite.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		t.Fatalf("sql db: %v", err)
	}
	sqlDB.SetMaxOpenConns(1)
	t.Cleanup(func() { _ = sqlDB.Close() })
	if err := database.Migrate(db); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	users := repository.NewUserRepository(db)
	keys := repository.NewKeyRepository(db)
	return NewKeyService(keys, users), db
}

func testKey(fill byte) string {
	return base64.StdEncoding.EncodeToString(bytes.Repeat([]byte{fill}, 33))
}

func testSignature() string {
	return base64.StdEncoding.EncodeToString(bytes.Repeat([]byte{9}, 64))
}

func testKyber() string {
	return base64.StdEncoding.EncodeToString(bytes.Repeat([]byte{7}, 1569))
}
