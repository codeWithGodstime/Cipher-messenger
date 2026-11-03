package utils

import (
	"errors"
	"regexp"
	"strings"
)

var (
	// ErrInvalidUsername means the value is not a usable username.
	ErrInvalidUsername = errors.New("invalid username")
	pattern            = regexp.MustCompile(`^[a-z][a-z0-9_]{2,31}$`)
)

// NormalizeUsername lowercases and trims a username, then checks length and charset.
// Usernames are 3–32 characters, start with a letter, and may contain letters,
// digits, and underscores.
func NormalizeUsername(raw string) (string, error) {
	value := strings.ToLower(strings.TrimSpace(raw))
	if !pattern.MatchString(value) {
		return "", ErrInvalidUsername
	}
	return value, nil
}
