package sms

import (
	"context"
	"errors"
	"log"

	"messenger/backend/internal/config"
)

// ErrNotConfigured means no delivery provider is selected.
// Request-code maps this to HTTP 503 and does not log the code.
var ErrNotConfigured = errors.New("otp delivery is not configured")

// Sender delivers a one-time code to a phone number.
//
// LogSender is for local development. It writes the code to the process log
// so a developer can finish login. It is used when APP_ENV=development (the
// default) or when SMS_MODE=log is set on purpose.
//
// A real provider implements Sender. Construct that implementation from main
// for APP_ENV=production, and read provider credentials from the environment
// there. Do not log the code. With SMS_MODE unset, production uses
// UnconfiguredSender, so request-code fails closed instead of printing codes.
type Sender interface {
	Send(ctx context.Context, phone, code string) error
}

type LogSender struct {
	logger *log.Logger
}

func (s LogSender) Send(_ context.Context, phone, code string) error {
	s.logger.Printf("dev otp sender: code for %s is %s", phone, code)
	return nil
}

type UnconfiguredSender struct{}

func (UnconfiguredSender) Send(context.Context, string, string) error {
	return ErrNotConfigured
}

func NewSender(cfg config.Config, logger *log.Logger) Sender {
	switch cfg.SMSMode {
	case "log":
		return LogSender{logger: logger}
	case "unconfigured":
		return UnconfiguredSender{}
	default:
		if cfg.AppEnv == "development" {
			return LogSender{logger: logger}
		}
		return UnconfiguredSender{}
	}
}
