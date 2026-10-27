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

// RequestOTPResult is either a pending code or a completed login.
// Login is set only when OTP_REQUIRED is false.
type RequestOTPResult struct {
	ExpiresIn int
	Login     *VerifyResult
}

// RequestOTP validates an E.164 number. When OTP is required it stores a hashed
// code and asks the sender to deliver it. The plaintext code is not returned.
// When OTP is not required it creates or loads the account and issues tokens.
func (s *AuthService) RequestOTP(ctx context.Context, rawPhone string) (*RequestOTPResult, error) {
	number, err := phone.Normalize(rawPhone)
	if err != nil {
		return nil, apperr.InvalidPhone()
	}
	if !s.cfg.OTPRequired {
		result, err := s.login(ctx, number, time.Now().UTC(), nil)
		if err != nil {
			return nil, fmt.Errorf("login without otp: %w", err)
		}
		return &RequestOTPResult{Login: result}, nil
	}

	now := time.Now().UTC()
	since := now.Add(-s.cfg.OTPRateWindow)
	count, oldest, latest, err := s.otps.RequestStats(ctx, number, since)
	if err != nil {
		return nil, fmt.Errorf("count otp requests: %w", err)
	}
	if count >= int64(s.cfg.OTPRateMax) {
		retry := 1
		if !oldest.IsZero() {
			retry = secondsUntil(oldest.Add(s.cfg.OTPRateWindow), now)
		}
		return nil, apperr.RateLimited(retry)
	}
	if !latest.IsZero() && now.Sub(latest) < s.cfg.OTPMinInterval {
		return nil, apperr.RateLimited(secondsUntil(latest.Add(s.cfg.OTPMinInterval), now))
	}

	code, err := newOTPCode()
	if err != nil {
		return nil, err
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(code), bcrypt.DefaultCost)
	if err != nil {
		return nil, fmt.Errorf("hash otp: %w", err)
	}
	if err := s.otps.Save(ctx, number, string(hash), s.cfg.OTPMaxAttempts, s.cfg.OTPTTL); err != nil {
		return nil, fmt.Errorf("store otp: %w", err)
	}
	member, err := s.otps.RecordRequest(ctx, number, now, s.cfg.OTPRateWindow)
	if err != nil {
		_, _ = s.otps.Delete(ctx, number)
		return nil, fmt.Errorf("record otp request: %w", err)
	}

	if err := s.sender.Send(ctx, number, code); err != nil {
		_, _ = s.otps.Delete(ctx, number)
		_ = s.otps.ForgetRequest(ctx, number, member)
		if errors.Is(err, sms.ErrNotConfigured) {
			return nil, apperr.SMSUnavailable()
		}
		return nil, fmt.Errorf("send otp: %w", err)
	}

	return &RequestOTPResult{ExpiresIn: int(s.cfg.OTPTTL.Seconds())}, nil
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

	challenge, err := s.otps.Get(ctx, number)
	if err != nil {
		return nil, fmt.Errorf("load otp: %w", err)
	}
	now := time.Now().UTC()
	if !challengeUsable(challenge) {
		return nil, apperr.InvalidCode()
	}
	if err := bcrypt.CompareHashAndPassword([]byte(challenge.CodeHash), []byte(code)); err != nil {
		if !errors.Is(err, bcrypt.ErrMismatchedHashAndPassword) {
			return nil, fmt.Errorf("compare otp: %w", err)
		}
		if _, _, incErr := s.otps.IncrementAttempts(ctx, number); incErr != nil {
			return nil, fmt.Errorf("record otp attempt: %w", incErr)
		}
		return nil, apperr.InvalidCode()
	}

	result, err := s.login(ctx, number, now, func(ctx context.Context) error {
		ok, err := s.otps.Delete(ctx, number)
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
	return result, nil
}

// login creates the account on first use, or loads it, then issues a session.
// extra runs after the account transaction commits. Verify uses it to delete the Redis code.
func (s *AuthService) login(ctx context.Context, number string, now time.Time, extra func(ctx context.Context) error) (*VerifyResult, error) {
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
		return nil
	})
	if err != nil {
		return nil, err
	}
	if extra != nil {
		if err := extra(ctx); err != nil {
			_ = s.sessions.Revoke(ctx, sessionID, time.Now().UTC())
			return nil, err
		}
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

type TokenResult struct {
	AccessToken  string
	RefreshToken string
	ExpiresIn    int
}

// Refresh rotates the refresh token and returns a new access token for the same session.
func (s *AuthService) Refresh(ctx context.Context, raw string) (*TokenResult, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, apperr.InvalidRefresh()
	}
	session, err := s.sessions.FindByRefreshHash(ctx, token.HashRefresh(raw))
	if err != nil {
		return nil, fmt.Errorf("load session: %w", err)
	}
	now := time.Now().UTC()
	if session == nil || session.RevokedAt != nil || !session.ExpiresAt.After(now) {
		return nil, apperr.InvalidRefresh()
	}

	refreshRaw, refreshHash, err := token.NewRefresh()
	if err != nil {
		return nil, err
	}
	if err := s.sessions.UpdateRefresh(ctx, session.ID, refreshHash, now.Add(s.cfg.RefreshTTL)); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return nil, apperr.InvalidRefresh()
		}
		return nil, fmt.Errorf("rotate refresh token: %w", err)
	}
	access, err := s.tokens.Sign(session.UserID, session.ID, now)
	if err != nil {
		return nil, err
	}
	return &TokenResult{
		AccessToken:  access,
		RefreshToken: refreshRaw,
		ExpiresIn:    int(s.cfg.AccessTTL.Seconds()),
	}, nil
}

// Logout revokes the session carried by the current access token.
func (s *AuthService) Logout(ctx context.Context, sessionID string) error {
	if sessionID == "" {
		return apperr.Unauthorized()
	}
	if err := s.sessions.Revoke(ctx, sessionID, time.Now().UTC()); err != nil {
		return fmt.Errorf("revoke session: %w", err)
	}
	return nil
}

func challengeUsable(challenge *repository.Challenge) bool {
	return challenge != nil && challenge.Attempts < challenge.MaxAttempts
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
