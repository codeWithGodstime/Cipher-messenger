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
	// OTPRequired controls POST /auth/otp. True, the default, stores and sends a code.
	// OTP_REQUIRED=false skips the code and returns the same tokens as POST /auth/verify.
	OTPRequired bool
	// RedisURL is REDIS_URL. It is required only when OTP_REQUIRED is true.
	// When OTP is disabled the process boots without Redis.
	RedisURL string
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
	otpRequired, err := boolEnv("OTP_REQUIRED", false)
	if err != nil {
		return Config{}, err
	}
	redisURL := strings.TrimSpace(os.Getenv("REDIS_URL"))

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
		OTPRequired:    otpRequired,
		RedisURL:       redisURL,
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

func boolEnv(key string, fallback bool) (bool, error) {
	v := strings.ToLower(strings.TrimSpace(os.Getenv(key)))
	if v == "" {
		return fallback, nil
	}
	switch v {
	case "1", "true", "yes":
		return true, nil
	case "0", "false", "no":
		return false, nil
	default:
		return false, fmt.Errorf("%s must be true or false", key)
	}
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
