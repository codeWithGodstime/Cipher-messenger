package service

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"gorm.io/gorm"

	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/utils/apperr"
)

// attachmentID is a UUID. It is the on-disk file name, so anything else is rejected
// before a path is joined.
var attachmentID = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// AttachmentService stores ciphertext blobs for conversation members.
// It never sees the key that decrypts them.
type AttachmentService struct {
	conversations repository.ConversationRepository
	attachments   repository.AttachmentRepository
	dir           string
}

func NewAttachmentService(
	conversations repository.ConversationRepository,
	attachments repository.AttachmentRepository,
	dir string,
) (*AttachmentService, error) {
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return nil, err
	}
	return &AttachmentService{
		conversations: conversations,
		attachments:   attachments,
		dir:           dir,
	}, nil
}

// Save streams a ciphertext body to disk. created is false when the same
// uploader stored these exact bytes before.
func (s *AttachmentService) Save(
	ctx context.Context,
	userID, conversationID, id string,
	body io.Reader,
) (*model.Attachment, bool, error) {
	if err := validAttachmentID(id); err != nil {
		return nil, false, err
	}
	if err := s.requireMember(ctx, conversationID, userID); err != nil {
		return nil, false, err
	}

	tmp, err := os.CreateTemp(s.dir, ".upload-*")
	if err != nil {
		return nil, false, err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)

	hash := sha256.New()
	n, err := io.Copy(io.MultiWriter(tmp, hash), io.LimitReader(body, model.MaxAttachmentBytes+1))
	var tooBig *http.MaxBytesError
	if errors.As(err, &tooBig) || n > model.MaxAttachmentBytes {
		tmp.Close()
		return nil, false, apperr.PayloadTooLarge()
	}
	if err != nil {
		tmp.Close()
		return nil, false, err
	}
	if n == 0 {
		tmp.Close()
		return nil, false, apperr.Validation("Attachment body is empty.")
	}
	if err := tmp.Sync(); err != nil {
		tmp.Close()
		return nil, false, err
	}
	if err := tmp.Close(); err != nil {
		return nil, false, err
	}
	digest := hex.EncodeToString(hash.Sum(nil))

	existing, err := s.attachments.Find(ctx, id)
	if err != nil {
		return nil, false, err
	}
	if existing != nil {
		if existing.UploaderID == userID && existing.Digest == digest && existing.ByteSize == n && existing.ConversationID == conversationID {
			return existing, false, nil
		}
		return nil, false, apperr.Conflict("attachment_conflict", "This attachment id is already stored with different bytes.")
	}

	final := filepath.Join(s.dir, id)
	if filepath.Base(final) != id {
		return nil, false, apperr.Validation("attachment id must be a UUID.")
	}
	if err := os.Rename(tmpName, final); err != nil {
		_ = os.Remove(final)
		if err := os.Rename(tmpName, final); err != nil {
			return nil, false, err
		}
	}

	row := &model.Attachment{
		ID:             id,
		ConversationID: conversationID,
		UploaderID:     userID,
		ByteSize:       n,
		Digest:         digest,
		CreatedAt:      time.Now().UTC(),
	}
	if err := s.attachments.Create(ctx, row); err != nil {
		if !isDuplicate(err) {
			_ = os.Remove(final)
			return nil, false, err
		}
		again, findErr := s.attachments.Find(ctx, id)
		if findErr != nil {
			return nil, false, findErr
		}
		if again != nil && again.UploaderID == userID && again.Digest == digest && again.ConversationID == conversationID {
			return again, false, nil
		}
		return nil, false, apperr.Conflict("attachment_conflict", "This attachment id is already stored with different bytes.")
	}
	return row, true, nil
}

// Open returns the ciphertext for a member. The caller closes the reader.
func (s *AttachmentService) Open(ctx context.Context, userID, conversationID, id string) (*model.Attachment, io.ReadCloser, error) {
	if err := validAttachmentID(id); err != nil {
		return nil, nil, err
	}
	if err := s.requireMember(ctx, conversationID, userID); err != nil {
		return nil, nil, err
	}
	row, err := s.attachments.Find(ctx, id)
	if err != nil {
		return nil, nil, err
	}
	if row == nil || row.ConversationID != conversationID {
		return nil, nil, apperr.NotFound("Attachment not found.")
	}
	final := filepath.Join(s.dir, id)
	if filepath.Base(final) != id {
		return nil, nil, apperr.Validation("attachment id must be a UUID.")
	}
	file, err := os.Open(final)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil, nil, apperr.NotFound("Attachment not found.")
		}
		return nil, nil, err
	}
	return row, file, nil
}

func (s *AttachmentService) requireMember(ctx context.Context, conversationID, userID string) error {
	conversation, err := s.conversations.FindByID(ctx, conversationID)
	if err != nil {
		return err
	}
	if conversation == nil {
		return apperr.NotFound("Conversation not found.")
	}
	ok, err := s.conversations.IsMember(ctx, conversationID, userID)
	if err != nil {
		return err
	}
	if !ok {
		return apperr.Forbidden("You are not a member of this conversation.")
	}
	return nil
}

func validAttachmentID(id string) error {
	if !attachmentID.MatchString(id) || filepath.Base(id) != id || strings.Contains(id, "..") {
		return apperr.Validation("attachment id must be a UUID.")
	}
	return nil
}

func isDuplicate(err error) bool {
	if errors.Is(err, gorm.ErrDuplicatedKey) {
		return true
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "unique") || strings.Contains(msg, "constraint")
}
