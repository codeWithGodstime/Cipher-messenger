package service

import (
	"bytes"
	"context"
	"errors"
	"io"
	"os"
	"path/filepath"
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

const (
	attachOwner = "user-a"
	attachPeer  = "user-b"
	attachOther = "user-c"
	attachConv  = "conv-1"
	attachID    = "11111111-1111-4111-8111-111111111111"
)

func TestAttachmentRoundTripAndIdempotentReplay(t *testing.T) {
	ctx := context.Background()
	svc, dir := newAttachmentTest(t)
	body := []byte("ciphertext-bytes")

	stored, created, err := svc.Save(ctx, attachOwner, attachConv, attachID, bytes.NewReader(body))
	if err != nil {
		t.Fatalf("save: %v", err)
	}
	if !created {
		t.Fatal("first save created = false")
	}
	if stored.ByteSize != int64(len(body)) || len(stored.Digest) != 64 {
		t.Fatalf("stored = %+v", stored)
	}

	again, created, err := svc.Save(ctx, attachOwner, attachConv, attachID, bytes.NewReader(body))
	if err != nil {
		t.Fatalf("replay: %v", err)
	}
	if created {
		t.Fatal("replay created a second row")
	}
	if again.Digest != stored.Digest {
		t.Fatalf("replay digest = %s, want %s", again.Digest, stored.Digest)
	}

	got, read, err := svc.Open(ctx, attachPeer, attachConv, attachID)
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	defer read.Close()
	if got.Digest != stored.Digest {
		t.Fatalf("open digest = %s", got.Digest)
	}
	downloaded, err := io.ReadAll(read)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if !bytes.Equal(downloaded, body) {
		t.Fatalf("downloaded = %q", downloaded)
	}
	if _, err := os.Stat(filepath.Join(dir, attachID)); err != nil {
		t.Fatalf("blob missing: %v", err)
	}
}

func TestAttachmentConflictKeepsOriginalBytes(t *testing.T) {
	ctx := context.Background()
	svc, _ := newAttachmentTest(t)
	original := []byte("first")
	if _, _, err := svc.Save(ctx, attachOwner, attachConv, attachID, bytes.NewReader(original)); err != nil {
		t.Fatalf("save: %v", err)
	}

	_, _, err := svc.Save(ctx, attachOwner, attachConv, attachID, bytes.NewReader([]byte("second")))
	var apiErr *apperr.Error
	if !errors.As(err, &apiErr) || apiErr.Status != 409 || apiErr.Code != "attachment_conflict" {
		t.Fatalf("different bytes err = %v", err)
	}
	_, _, err = svc.Save(ctx, attachPeer, attachConv, attachID, bytes.NewReader(original))
	if !errors.As(err, &apiErr) || apiErr.Status != 409 {
		t.Fatalf("different uploader err = %v", err)
	}

	_, read, err := svc.Open(ctx, attachOwner, attachConv, attachID)
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	defer read.Close()
	downloaded, err := io.ReadAll(read)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if !bytes.Equal(downloaded, original) {
		t.Fatalf("stored bytes changed to %q", downloaded)
	}
}

func TestAttachmentRejectsNonMembersAndBadIDs(t *testing.T) {
	ctx := context.Background()
	svc, dir := newAttachmentTest(t)
	body := bytes.NewReader([]byte("secret"))

	_, _, err := svc.Save(ctx, attachOther, attachConv, attachID, body)
	assertStatus(t, err, 403)

	_, _, err = svc.Save(ctx, attachOwner, "missing-conv", attachID, bytes.NewReader([]byte("secret")))
	assertStatus(t, err, 404)

	for _, id := range []string{"../etc/passwd", "not-a-uuid", "..", "11111111-1111-4111-8111-111111111111/evil"} {
		_, _, err = svc.Save(ctx, attachOwner, attachConv, id, bytes.NewReader([]byte("secret")))
		assertStatus(t, err, 400)
	}
	_, _, err = svc.Open(ctx, attachOther, attachConv, attachID)
	assertStatus(t, err, 403)

	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("readdir: %v", err)
	}
	for _, entry := range entries {
		if entry.Name() != "." && entry.Name() != ".." {
			t.Fatalf("rejected upload left %s", entry.Name())
		}
	}
}

func TestAttachmentEmptyAndOversized(t *testing.T) {
	ctx := context.Background()
	svc, dir := newAttachmentTest(t)

	_, _, err := svc.Save(ctx, attachOwner, attachConv, attachID, bytes.NewReader(nil))
	assertStatus(t, err, 400)

	tooBig := bytes.NewReader(make([]byte, model.MaxAttachmentBytes+1))
	_, _, err = svc.Save(ctx, attachOwner, attachConv, attachID, tooBig)
	assertStatus(t, err, 413)

	if _, err := os.Stat(filepath.Join(dir, attachID)); !os.IsNotExist(err) {
		t.Fatalf("oversized upload left a blob: %v", err)
	}
}

func assertStatus(t *testing.T, err error, status int) {
	t.Helper()
	var apiErr *apperr.Error
	if !errors.As(err, &apiErr) || apiErr.Status != status {
		t.Fatalf("err = %v, want status %d", err, status)
	}
}

func newAttachmentTest(t *testing.T) (*AttachmentService, string) {
	t.Helper()
	dsn := "file:" + t.Name() + "?mode=memory&cache=shared"
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

	now := time.Now().UTC()
	for _, user := range []model.User{
		{ID: attachOwner, Username: "usera", DisplayName: "A", About: model.DefaultAbout, CreatedAt: now, UpdatedAt: now},
		{ID: attachPeer, Username: "userb", DisplayName: "B", About: model.DefaultAbout, CreatedAt: now, UpdatedAt: now},
		{ID: attachOther, Username: "userc", DisplayName: "C", About: model.DefaultAbout, CreatedAt: now, UpdatedAt: now},
	} {
		if err := db.Create(&user).Error; err != nil {
			t.Fatalf("user %s: %v", user.ID, err)
		}
	}
	if err := db.Create(&model.Conversation{ID: attachConv, DmKey: attachOwner + ":" + attachPeer, CreatedAt: now, UpdatedAt: now}).Error; err != nil {
		t.Fatalf("conversation: %v", err)
	}
	members := []model.ConversationMember{
		{ConversationID: attachConv, UserID: attachOwner, CreatedAt: now},
		{ConversationID: attachConv, UserID: attachPeer, CreatedAt: now},
	}
	if err := db.Create(&members).Error; err != nil {
		t.Fatalf("members: %v", err)
	}

	dir := t.TempDir()
	svc, err := NewAttachmentService(repository.NewConversationRepository(db), repository.NewAttachmentRepository(db), dir)
	if err != nil {
		t.Fatalf("service: %v", err)
	}
	return svc, dir
}
