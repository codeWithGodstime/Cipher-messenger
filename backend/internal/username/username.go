package username

import (
	"errors"
	"regexp"
	"strings"
)

var (
	// ErrInvalid means the value is not a usable username.
	ErrInvalid = errors.New("invalid username")
	pattern    = regexp.MustCompile(`^[a-z][a-z0-9_]{2,31}$`)
)

// Normalize lowercases and trims a username, then checks length and charset.
// Usernames are 3–32 characters, start with a letter, and may contain letters,
// digits, and underscores.
func Normalize(raw string) (string, error) {
	value := strings.ToLower(strings.TrimSpace(raw))
	if !pattern.MatchString(value) {
		return "", ErrInvalid
	}
	return value, nil
}
