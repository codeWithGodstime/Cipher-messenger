package service

import (
	"context"
	"fmt"
	"strings"
	"unicode"
	"unicode/utf8"

	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/utils"
	"messenger/backend/internal/utils/apperr"
)

const (
	displayNameMax      = 64
	aboutMax            = 139
	profilePhotoMax     = 2048
	usernameSearchLimit = 20
)

// UserCard is the public directory row used to start a chat.
// It never includes phone numbers, secrets, or profile photos.
type UserCard struct {
	ID          string
	Username    string
	DisplayName string
}

type ProfileUpdate struct {
	DisplayName  *string
	About        *string
	ProfilePhoto *string
}

type ProfileService struct {
	users repository.UserRepository
}

func NewProfileService(users repository.UserRepository) *ProfileService {
	return &ProfileService{users: users}
}

// SearchUsernames returns users whose username contains the typed fragment.
// An empty fragment returns an empty list and does not touch the database.
// The signed-in user is excluded. At most usernameSearchLimit rows are returned.
func (s *ProfileService) SearchUsernames(ctx context.Context, userID, rawQuery string) ([]UserCard, error) {
	query := utils.NormalizeUserSearch(rawQuery)
	if query == "" {
		return []UserCard{}, nil
	}
	users, err := s.users.SearchByUsername(ctx, query, userID, usernameSearchLimit)
	if err != nil {
		return nil, err
	}
	out := make([]UserCard, 0, len(users))
	for _, user := range users {
		if user.ID == "" || user.ID == userID || user.Username == "" {
			continue
		}
		out = append(out, UserCard{
			ID:          user.ID,
			Username:    user.Username,
			DisplayName: user.DisplayName,
		})
		if len(out) == usernameSearchLimit {
			break
		}
	}
	return out, nil
}

func (s *ProfileService) Get(ctx context.Context, userID string) (*model.User, error) {
	user, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return nil, fmt.Errorf("load profile: %w", err)
	}
	if user == nil {
		return nil, apperr.Unauthorized()
	}
	return user, nil
}

func (s *ProfileService) Update(ctx context.Context, userID string, in ProfileUpdate) (*model.User, error) {
	user, err := s.Get(ctx, userID)
	if err != nil {
		return nil, err
	}

	changed := false
	if in.DisplayName != nil {
		name, err := cleanText(*in.DisplayName, 1, displayNameMax, "Display name")
		if err != nil {
			return nil, err
		}
		user.DisplayName = name
		changed = true
	}
	if in.About != nil {
		about, err := cleanText(*in.About, 0, aboutMax, "About")
		if err != nil {
			return nil, err
		}
		user.About = about
		changed = true
	}
	if in.ProfilePhoto != nil {
		photo, err := cleanText(*in.ProfilePhoto, 0, profilePhotoMax, "Profile photo")
		if err != nil {
			return nil, err
		}
		user.ProfilePhoto = photo
		changed = true
	}
	if !changed {
		return user, nil
	}
	if err := s.users.UpdateProfile(ctx, user); err != nil {
		return nil, fmt.Errorf("update profile: %w", err)
	}
	return s.Get(ctx, user.ID)
}

func cleanText(value string, min, max int, label string) (string, error) {
	value = strings.TrimSpace(value)
	if hasControl(value) {
		return "", apperr.Validation(label + " contains unsupported characters.")
	}
	n := utf8.RuneCountInString(value)
	if n < min {
		return "", apperr.Validation(label + " cannot be empty.")
	}
	if n > max {
		return "", apperr.Validation(fmt.Sprintf("%s must be at most %d characters.", label, max))
	}
	return value, nil
}

func hasControl(value string) bool {
	for _, r := range value {
		if unicode.IsControl(r) {
			return true
		}
	}
	return false
}
