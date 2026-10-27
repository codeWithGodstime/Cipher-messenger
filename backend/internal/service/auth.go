package service

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"math/big"
	"time"

	"golang.org/x/crypto/bcrypt"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/config"
	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
	"messenger/backend/internal/phone"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/sms"
)

type AuthService struct {
	cfg    config.Config
	otps   repository.OTPRepository
	tx     database.Transactor
	sender sms.Sender
}

func NewAuthService(cfg config.Config, otps repository.OTPRepository, tx database.Transactor, sender sms.Sender) *AuthService {
	return &AuthService{cfg: cfg, otps: otps, tx: tx, sender: sender}
}

// RequestOTP stores a hashed code for an E.164 number and asks the sender to deliver it.
// The plaintext code is not returned and is not logged here.
func (s *AuthService) RequestOTP(ctx context.Context, rawPhone string) (int, error) {
	number, err := phone.Normalize(rawPhone)
	if err != nil {
		return 0, apperr.InvalidPhone()
	}

	now := time.Now().UTC()
	since := now.Add(-s.cfg.OTPRateWindow)
	count, err := s.otps.CountSince(ctx, number, since)
	if err != nil {
		return 0, fmt.Errorf("count otp requests: %w", err)
	}
	if count >= int64(s.cfg.OTPRateMax) {
		oldest, err := s.otps.OldestSince(ctx, number, since)
		if err != nil {
			return 0, fmt.Errorf("oldest otp request: %w", err)
		}
		retry := 1
		if oldest != nil {
			retry = secondsUntil(oldest.CreatedAt.Add(s.cfg.OTPRateWindow), now)
		}
		return 0, apperr.RateLimited(retry)
	}

	latest, err := s.otps.Latest(ctx, number)
	if err != nil {
		return 0, fmt.Errorf("latest otp: %w", err)
	}
	if latest != nil && now.Sub(latest.CreatedAt) < s.cfg.OTPMinInterval {
		return 0, apperr.RateLimited(secondsUntil(latest.CreatedAt.Add(s.cfg.OTPMinInterval), now))
	}

	code, err := newOTPCode()
	if err != nil {
		return 0, err
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(code), bcrypt.DefaultCost)
	if err != nil {
		return 0, fmt.Errorf("hash otp: %w", err)
	}
	id, err := model.NewID()
	if err != nil {
		return 0, err
	}
	challenge := &model.OTPChallenge{
		ID:          id,
		Phone:       number,
		CodeHash:    string(hash),
		ExpiresAt:   now.Add(s.cfg.OTPTTL),
		MaxAttempts: s.cfg.OTPMaxAttempts,
		CreatedAt:   now,
	}

	err = s.tx.InTx(ctx, func(ctx context.Context) error {
		if err := s.otps.ConsumeActive(ctx, number, now); err != nil {
			return err
		}
		return s.otps.Create(ctx, challenge)
	})
	if err != nil {
		return 0, fmt.Errorf("store otp: %w", err)
	}

	if err := s.sender.Send(ctx, number, code); err != nil {
		if delErr := s.otps.Delete(ctx, challenge.ID); delErr != nil {
			return 0, fmt.Errorf("send otp: %w (cleanup: %v)", err, delErr)
		}
		if errors.Is(err, sms.ErrNotConfigured) {
			return 0, apperr.SMSUnavailable()
		}
		return 0, fmt.Errorf("send otp: %w", err)
	}

	return int(s.cfg.OTPTTL.Seconds()), nil
}

func newOTPCode() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(1000000))
	if err != nil {
		return "", fmt.Errorf("generate otp: %w", err)
	}
	return fmt.Sprintf("%06d", n.Int64()), nil
}

func secondsUntil(until, now time.Time) int {
	secs := int(until.Sub(now).Seconds())
	if secs < 1 {
		return 1
	}
	return secs
}
