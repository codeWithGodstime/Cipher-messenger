package service

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"math/big"
	"regexp"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/config"
	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
	"messenger/backend/internal/phone"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/sms"
	"messenger/backend/internal/token"
)

var (
	otpCodePattern = regexp.MustCompile(`^\d{6}$`)
	errCodeUsed    = errors.New("otp already used")
)

type AuthService struct {
	cfg      config.Config
	users    repository.UserRepository
	otps     repository.OTPRepository
	sessions repository.SessionRepository
	tx       database.Transactor
	sender   sms.Sender
	tokens   *token.Manager
}

func NewAuthService(
	cfg config.Config,
	users repository.UserRepository,
	otps repository.OTPRepository,
	sessions repository.SessionRepository,
	tx database.Transactor,
	sender sms.Sender,
	tokens *token.Manager,
) *AuthService {
	return &AuthService{
		cfg:      cfg,
		users:    users,
		otps:     otps,
		sessions: sessions,
		tx:       tx,
		sender:   sender,
		tokens:   tokens,
	}
}

type VerifyResult struct {
	AccessToken  string
	RefreshToken string
	ExpiresIn    int
	Created      bool
	User         *model.User
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

// Verify checks a one-time code, creates the account on first success, and issues tokens.
func (s *AuthService) Verify(ctx context.Context, rawPhone, rawCode string) (*VerifyResult, error) {
	number, err := phone.Normalize(rawPhone)
	if err != nil {
		return nil, apperr.InvalidPhone()
	}
	code := strings.TrimSpace(rawCode)
	if !otpCodePattern.MatchString(code) {
		return nil, apperr.Validation("Code must be a 6-digit number.")
	}

	challenge, err := s.otps.Latest(ctx, number)
	if err != nil {
		return nil, fmt.Errorf("load otp: %w", err)
	}
	now := time.Now().UTC()
	if !challengeUsable(challenge, now) {
		return nil, apperr.InvalidCode()
	}
	if err := bcrypt.CompareHashAndPassword([]byte(challenge.CodeHash), []byte(code)); err != nil {
		if !errors.Is(err, bcrypt.ErrMismatchedHashAndPassword) {
			return nil, fmt.Errorf("compare otp: %w", err)
		}
		if _, _, incErr := s.otps.IncrementAttempts(ctx, challenge.ID, challenge.MaxAttempts); incErr != nil {
			return nil, fmt.Errorf("record otp attempt: %w", incErr)
		}
		return nil, apperr.InvalidCode()
	}

	sessionID, err := model.NewID()
	if err != nil {
		return nil, err
	}
	refreshRaw, refreshHash, err := token.NewRefresh()
	if err != nil {
		return nil, err
	}

	var (
		user    *model.User
		created bool
	)
	err = s.tx.InTx(ctx, func(ctx context.Context) error {
		existing, err := s.users.FindByPhone(ctx, number)
		if err != nil {
			return err
		}
		if existing != nil {
			user = existing
		} else {
			id, err := model.NewID()
			if err != nil {
				return err
			}
			user = &model.User{
				ID:        id,
				Phone:     number,
				About:     model.DefaultAbout,
				CreatedAt: now,
				UpdatedAt: now,
			}
			if err := s.users.Create(ctx, user); err != nil {
				if !errors.Is(err, repository.ErrDuplicate) {
					return err
				}
				existing, err = s.users.FindByPhone(ctx, number)
				if err != nil {
					return err
				}
				if existing == nil {
					return fmt.Errorf("user missing after duplicate phone")
				}
				user = existing
			} else {
				created = true
			}
		}

		session := &model.Session{
			ID:          sessionID,
			UserID:      user.ID,
			RefreshHash: refreshHash,
			ExpiresAt:   now.Add(s.cfg.RefreshTTL),
			CreatedAt:   now,
		}
		if err := s.sessions.Create(ctx, session); err != nil {
			return err
		}
		ok, err := s.otps.Consume(ctx, challenge.ID, now)
		if err != nil {
			return err
		}
		if !ok {
			return errCodeUsed
		}
		return nil
	})
	if errors.Is(err, errCodeUsed) {
		return nil, apperr.InvalidCode()
	}
	if err != nil {
		return nil, fmt.Errorf("verify otp: %w", err)
	}

	access, err := s.tokens.Sign(user.ID, sessionID, now)
	if err != nil {
		_ = s.sessions.Revoke(ctx, sessionID, time.Now().UTC())
		return nil, err
	}
	return &VerifyResult{
		AccessToken:  access,
		RefreshToken: refreshRaw,
		ExpiresIn:    int(s.cfg.AccessTTL.Seconds()),
		Created:      created,
		User:         user,
	}, nil
}

func challengeUsable(challenge *model.OTPChallenge, now time.Time) bool {
	return challenge != nil &&
		challenge.ConsumedAt == nil &&
		challenge.ExpiresAt.After(now) &&
		challenge.Attempts < challenge.MaxAttempts
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
