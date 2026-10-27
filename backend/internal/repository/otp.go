package repository

import (
	"context"
	"errors"
	"time"

	"gorm.io/gorm"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type OTPRepository interface {
	CountSince(ctx context.Context, phone string, since time.Time) (int64, error)
	OldestSince(ctx context.Context, phone string, since time.Time) (*model.OTPChallenge, error)
	Latest(ctx context.Context, phone string) (*model.OTPChallenge, error)
	Create(ctx context.Context, challenge *model.OTPChallenge) error
	ConsumeActive(ctx context.Context, phone string, now time.Time) error
	IncrementAttempts(ctx context.Context, id string, max int) (int, bool, error)
	Consume(ctx context.Context, id string, now time.Time) (bool, error)
	Delete(ctx context.Context, id string) error
}

type otpRepository struct {
	db *gorm.DB
}

func NewOTPRepository(db *gorm.DB) OTPRepository {
	return &otpRepository{db: db}
}

func (r *otpRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *otpRepository) CountSince(ctx context.Context, phone string, since time.Time) (int64, error) {
	var n int64
	err := r.conn(ctx).Model(&model.OTPChallenge{}).
		Where("phone = ? AND created_at >= ?", phone, since).
		Count(&n).Error
	return n, err
}

func (r *otpRepository) OldestSince(ctx context.Context, phone string, since time.Time) (*model.OTPChallenge, error) {
	var challenge model.OTPChallenge
	err := r.conn(ctx).
		Where("phone = ? AND created_at >= ?", phone, since).
		Order("created_at ASC").
		First(&challenge).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &challenge, nil
}

func (r *otpRepository) Latest(ctx context.Context, phone string) (*model.OTPChallenge, error) {
	var challenge model.OTPChallenge
	err := r.conn(ctx).
		Where("phone = ?", phone).
		Order("created_at DESC").
		First(&challenge).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &challenge, nil
}

func (r *otpRepository) Create(ctx context.Context, challenge *model.OTPChallenge) error {
	return r.conn(ctx).Create(challenge).Error
}

func (r *otpRepository) ConsumeActive(ctx context.Context, phone string, now time.Time) error {
	return r.conn(ctx).Model(&model.OTPChallenge{}).
		Where("phone = ? AND consumed_at IS NULL", phone).
		Update("consumed_at", now).Error
}

func (r *otpRepository) IncrementAttempts(ctx context.Context, id string, max int) (int, bool, error) {
	res := r.conn(ctx).Exec(
		`UPDATE otp_challenges SET attempts = attempts + 1 WHERE id = ? AND consumed_at IS NULL AND attempts < ?`,
		id, max,
	)
	if res.Error != nil {
		return 0, false, res.Error
	}
	if res.RowsAffected == 0 {
		return 0, false, nil
	}
	var challenge model.OTPChallenge
	if err := r.conn(ctx).Where("id = ?", id).First(&challenge).Error; err != nil {
		return 0, false, err
	}
	return challenge.Attempts, true, nil
}

func (r *otpRepository) Consume(ctx context.Context, id string, now time.Time) (bool, error) {
	res := r.conn(ctx).Model(&model.OTPChallenge{}).
		Where("id = ? AND consumed_at IS NULL", id).
		Update("consumed_at", now)
	if res.Error != nil {
		return false, res.Error
	}
	return res.RowsAffected > 0, nil
}

func (r *otpRepository) Delete(ctx context.Context, id string) error {
	return r.conn(ctx).Where("id = ?", id).Delete(&model.OTPChallenge{}).Error
}
