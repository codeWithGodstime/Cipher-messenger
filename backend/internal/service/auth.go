package service

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"messenger/backend/internal/config"
	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/token"
	"messenger/backend/internal/utils"
	"messenger/backend/internal/utils/apperr"
)

type AuthService struct {
	cfg      config.Config
	users    repository.UserRepository
	sessions repository.SessionRepository
	tx       database.Transactor
	tokens   *token.Manager
}

func NewAuthService(
	cfg config.Config,
	users repository.UserRepository,
	sessions repository.SessionRepository,
	tx database.Transactor,
	tokens *token.Manager,
) *AuthService {
	return &AuthService{
		cfg:      cfg,
		users:    users,
		sessions: sessions,
		tx:       tx,
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

// Login issues a session for an existing username.
func (s *AuthService) Login(ctx context.Context, rawUsername string) (*VerifyResult, error) {
	name, err := utils.NormalizeUsername(rawUsername)
	if err != nil {
		return nil, apperr.InvalidUsername()
	}
	existing, err := s.users.FindByUsername(ctx, name)
	if err != nil {
		return nil, fmt.Errorf("find user: %w", err)
	}
	if existing == nil {
		return nil, apperr.UserDoesNotExist()
	}
	return s.issueSession(ctx, existing, false, time.Now().UTC())
}

// Signup creates a new account when the username is free, then issues a session.
func (s *AuthService) Signup(ctx context.Context, rawUsername string) (*VerifyResult, error) {
	name, err := utils.NormalizeUsername(rawUsername)
	if err != nil {
		return nil, apperr.InvalidUsername()
	}
	now := time.Now().UTC()

	sessionID, err := utils.NewID()
	if err != nil {
		return nil, err
	}
	refreshRaw, refreshHash, err := token.NewRefresh()
	if err != nil {
		return nil, err
	}

	var user *model.User
	err = s.tx.InTx(ctx, func(ctx context.Context) error {
		existing, err := s.users.FindByUsername(ctx, name)
		if err != nil {
			return err
		}
		if existing != nil {
			return apperr.UsernameTaken()
		}
		id, err := utils.NewID()
		if err != nil {
			return err
		}
		user = &model.User{
			ID:        id,
			Username:  name,
			About:     model.DefaultAbout,
			CreatedAt: now,
			UpdatedAt: now,
		}
		if err := s.users.Create(ctx, user); err != nil {
			if errors.Is(err, repository.ErrDuplicate) {
				return apperr.UsernameTaken()
			}
			return err
		}
		session := &model.Session{
			ID:          sessionID,
			UserID:      user.ID,
			RefreshHash: refreshHash,
			ExpiresAt:   now.Add(s.cfg.RefreshTTL),
			CreatedAt:   now,
		}
		return s.sessions.Create(ctx, session)
	})
	if err != nil {
		return nil, err
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
		Created:      true,
		User:         user,
	}, nil
}

func (s *AuthService) issueSession(ctx context.Context, user *model.User, created bool, now time.Time) (*VerifyResult, error) {
	sessionID, err := utils.NewID()
	if err != nil {
		return nil, err
	}
	refreshRaw, refreshHash, err := token.NewRefresh()
	if err != nil {
		return nil, err
	}
	session := &model.Session{
		ID:          sessionID,
		UserID:      user.ID,
		RefreshHash: refreshHash,
		ExpiresAt:   now.Add(s.cfg.RefreshTTL),
		CreatedAt:   now,
	}
	if err := s.sessions.Create(ctx, session); err != nil {
		return nil, err
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
