package repository

import (
	"context"
	"errors"

	"gorm.io/gorm"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type AttachmentRepository interface {
	Find(ctx context.Context, id string) (*model.Attachment, error)
	Create(ctx context.Context, attachment *model.Attachment) error
}

type attachmentRepository struct {
	db *gorm.DB
}

func NewAttachmentRepository(db *gorm.DB) AttachmentRepository {
	return &attachmentRepository{db: db}
}

func (r *attachmentRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *attachmentRepository) Find(ctx context.Context, id string) (*model.Attachment, error) {
	var attachment model.Attachment
	err := r.conn(ctx).Where("id = ?", id).First(&attachment).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &attachment, nil
}

func (r *attachmentRepository) Create(ctx context.Context, attachment *model.Attachment) error {
	return r.conn(ctx).Create(attachment).Error
}
