package repository

import (
	"context"
	"errors"
	"time"

	"gorm.io/gorm"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type SessionRepository interface {
	Create(ctx context.Context, session *model.Session) error
	FindByID(ctx context.Context, id string) (*model.Session, error)
	FindByRefreshHash(ctx context.Context, hash string) (*model.Session, error)
	ExtendExpiry(ctx context.Context, id string, expiresAt time.Time) error
	Revoke(ctx context.Context, id string, now time.Time) error
}

type sessionRepository struct {
	db *gorm.DB
}

func NewSessionRepository(db *gorm.DB) SessionRepository {
	return &sessionRepository{db: db}
}

func (r *sessionRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *sessionRepository) Create(ctx context.Context, session *model.Session) error {
	return r.conn(ctx).Create(session).Error
}

func (r *sessionRepository) FindByID(ctx context.Context, id string) (*model.Session, error) {
	var session model.Session
	err := r.conn(ctx).Where("id = ?", id).First(&session).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &session, nil
}

func (r *sessionRepository) FindByRefreshHash(ctx context.Context, hash string) (*model.Session, error) {
	var session model.Session
	err := r.conn(ctx).Where("refresh_hash = ?", hash).First(&session).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &session, nil
}

func (r *sessionRepository) ExtendExpiry(ctx context.Context, id string, expiresAt time.Time) error {
	res := r.conn(ctx).Model(&model.Session{}).
		Where("id = ? AND revoked_at IS NULL", id).
		Update("expires_at", expiresAt)
	if res.Error != nil {
		return res.Error
	}
	if res.RowsAffected == 0 {
		return ErrNotFound
	}
	return nil
}

func (r *sessionRepository) Revoke(ctx context.Context, id string, now time.Time) error {
	return r.conn(ctx).Model(&model.Session{}).
		Where("id = ? AND revoked_at IS NULL", id).
		Update("revoked_at", now).Error
}
