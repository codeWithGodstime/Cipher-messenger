package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// DevJWTSecret is the local default. It is rejected when APP_ENV=production.
const DevJWTSecret = "dev-only-not-for-production-change-me"

type Config struct {
	AppEnv         string
	Port           string
	JWTSecret      string
	SMSMode        string
	AccessTTL      time.Duration
	RefreshTTL     time.Duration
	OTPTTL         time.Duration
	OTPMaxAttempts int
	OTPMinInterval time.Duration
	OTPRateWindow  time.Duration
	OTPRateMax     int
}

func Load() (Config, error) {
	access, err := durationEnv("ACCESS_TOKEN_TTL", 15*time.Minute)
	if err != nil {
		return Config{}, err
	}
	refresh, err := durationEnv("REFRESH_TOKEN_TTL", 30*24*time.Hour)
	if err != nil {
		return Config{}, err
	}
	otpTTL, err := durationEnv("OTP_TTL", 5*time.Minute)
	if err != nil {
		return Config{}, err
	}
	minInterval, err := durationEnv("OTP_MIN_INTERVAL", time.Minute)
	if err != nil {
		return Config{}, err
	}
	rateWindow, err := durationEnv("OTP_RATE_WINDOW", time.Hour)
	if err != nil {
		return Config{}, err
	}
	attempts, err := intEnv("OTP_MAX_ATTEMPTS", 5)
	if err != nil {
		return Config{}, err
	}
	rateMax, err := intEnv("OTP_RATE_MAX", 5)
	if err != nil {
		return Config{}, err
	}

	cfg := Config{
		AppEnv:         strings.ToLower(strings.TrimSpace(env("APP_ENV", "development"))),
		Port:           env("PORT", "8080"),
		JWTSecret:      env("JWT_SECRET", DevJWTSecret),
		SMSMode:        strings.ToLower(strings.TrimSpace(os.Getenv("SMS_MODE"))),
		AccessTTL:      access,
		RefreshTTL:     refresh,
		OTPTTL:         otpTTL,
		OTPMaxAttempts: attempts,
		OTPMinInterval: minInterval,
		OTPRateWindow:  rateWindow,
		OTPRateMax:     rateMax,
	}
	if cfg.Port == "" {
		return Config{}, fmt.Errorf("PORT must not be empty")
	}
	if cfg.AppEnv == "production" && (cfg.JWTSecret == DevJWTSecret || len(cfg.JWTSecret) < 32) {
		return Config{}, fmt.Errorf("JWT_SECRET must be set to a unique value of at least 32 characters when APP_ENV=production")
	}
	if cfg.OTPMaxAttempts < 1 || cfg.OTPRateMax < 1 {
		return Config{}, fmt.Errorf("OTP attempt and rate limits must be at least 1")
	}
	return cfg, nil
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func durationEnv(key string, fallback time.Duration) (time.Duration, error) {
	v := os.Getenv(key)
	if v == "" {
		return fallback, nil
	}
	d, err := time.ParseDuration(v)
	if err != nil || d <= 0 {
		return 0, fmt.Errorf("%s must be a positive duration", key)
	}
	return d, nil
}

func intEnv(key string, fallback int) (int, error) {
	v := os.Getenv(key)
	if v == "" {
		return fallback, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil || n < 0 {
		return 0, fmt.Errorf("%s must be a non-negative integer", key)
	}
	return n, nil
}
