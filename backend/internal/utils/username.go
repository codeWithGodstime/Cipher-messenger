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

// NormalizeUserSearch turns a typed fragment into a username search key.
// It keeps letters, digits, and underscores, lowercased, at most 32 characters.
// A blank result means "no query" (empty input, only "@", or no usable characters).
func NormalizeUserSearch(raw string) string {
	value := strings.TrimPrefix(strings.TrimSpace(raw), "@")
	value = strings.ToLower(value)
	var b strings.Builder
	for _, r := range value {
		if (r < 'a' || r > 'z') && (r < '0' || r > '9') && r != '_' {
			continue
		}
		if b.Len() >= 32 {
			break
		}
		b.WriteRune(r)
	}
	return b.String()
}
