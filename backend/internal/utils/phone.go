package utils

import (
	"fmt"
	"regexp"
	"strings"
)

// e164 is a leading plus, a non-zero country digit, then 6 to 14 more digits
// (7 to 15 digits total), which is the E.164 maximum.
var e164 = regexp.MustCompile(`^\+[1-9]\d{6,14}$`)

// NormalizePhone strips visual separators and returns a canonical E.164 number.
func NormalizePhone(input string) (string, error) {
	var b strings.Builder
	for _, r := range strings.TrimSpace(input) {
		switch r {
		case ' ', '-', '(', ')', '.':
			continue
		default:
			b.WriteRune(r)
		}
	}
	number := b.String()
	if strings.HasPrefix(number, "00") {
		number = "+" + number[2:]
	}
	if !e164.MatchString(number) {
		return "", fmt.Errorf("invalid phone")
	}
	return number, nil
}
