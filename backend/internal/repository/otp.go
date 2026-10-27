package repository

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
)

// Challenge is one active login code for a phone number.
// The plaintext code is never stored. Expiry is the Redis key TTL.
type Challenge struct {
	CodeHash    string
	Attempts    int
	MaxAttempts int
	CreatedAt   time.Time
}

// OTPRepository stores hashed login codes and request timestamps in Redis.
//
//	otp:challenge:{e164}  hash  code_hash, attempts, max_attempts, created_at
//	                      TTL   OTP_TTL (about 5 minutes)
//	otp:rate:{e164}       zset  member id, score unix milliseconds
//	                      TTL   OTP_RATE_WINDOW
type OTPRepository interface {
	RequestStats(ctx context.Context, phone string, since time.Time) (count int64, oldest, latest time.Time, err error)
	Save(ctx context.Context, phone, codeHash string, maxAttempts int, ttl time.Duration) error
	RecordRequest(ctx context.Context, phone string, at time.Time, window time.Duration) (string, error)
	ForgetRequest(ctx context.Context, phone, member string) error
	Get(ctx context.Context, phone string) (*Challenge, error)
	IncrementAttempts(ctx context.Context, phone string) (int, bool, error)
	Delete(ctx context.Context, phone string) (bool, error)
}

func challengeKey(phone string) string { return "otp:challenge:" + phone }

func rateKey(phone string) string { return "otp:rate:" + phone }

type redisOTPRepository struct {
	rdb *redis.Client
}

func NewRedisOTPRepository(rdb *redis.Client) OTPRepository {
	return &redisOTPRepository{rdb: rdb}
}

func (r *redisOTPRepository) RequestStats(ctx context.Context, phone string, since time.Time) (int64, time.Time, time.Time, error) {
	key := rateKey(phone)
	minScore := strconv.FormatInt(since.UnixMilli(), 10)
	pipe := r.rdb.TxPipeline()
	pipe.ZRemRangeByScore(ctx, key, "-inf", "("+minScore)
	countCmd := pipe.ZCount(ctx, key, minScore, "+inf")
	oldestCmd := pipe.ZRangeWithScores(ctx, key, 0, 0)
	latestCmd := pipe.ZRangeWithScores(ctx, key, -1, -1)
	if _, err := pipe.Exec(ctx); err != nil {
		return 0, time.Time{}, time.Time{}, err
	}
	return countCmd.Val(), scoreTime(oldestCmd.Val()), scoreTime(latestCmd.Val()), nil
}

func (r *redisOTPRepository) Save(ctx context.Context, phone, codeHash string, maxAttempts int, ttl time.Duration) error {
	key := challengeKey(phone)
	pipe := r.rdb.TxPipeline()
	pipe.Del(ctx, key)
	pipe.HSet(ctx, key, map[string]any{
		"code_hash":    codeHash,
		"attempts":     0,
		"max_attempts": maxAttempts,
		"created_at":   time.Now().UTC().Unix(),
	})
	pipe.Expire(ctx, key, ttl)
	_, err := pipe.Exec(ctx)
	return err
}

func (r *redisOTPRepository) RecordRequest(ctx context.Context, phone string, at time.Time, window time.Duration) (string, error) {
	member := strconv.FormatInt(at.UnixNano(), 10)
	pipe := r.rdb.TxPipeline()
	pipe.ZAdd(ctx, rateKey(phone), redis.Z{Score: float64(at.UnixMilli()), Member: member})
	pipe.Expire(ctx, rateKey(phone), window)
	_, err := pipe.Exec(ctx)
	if err != nil {
		return "", err
	}
	return member, nil
}

func (r *redisOTPRepository) ForgetRequest(ctx context.Context, phone, member string) error {
	if member == "" {
		return nil
	}
	return r.rdb.ZRem(ctx, rateKey(phone), member).Err()
}

func (r *redisOTPRepository) Get(ctx context.Context, phone string) (*Challenge, error) {
	fields, err := r.rdb.HGetAll(ctx, challengeKey(phone)).Result()
	if err != nil {
		return nil, err
	}
	if len(fields) == 0 || fields["code_hash"] == "" {
		return nil, nil
	}
	attempts, err := strconv.Atoi(fields["attempts"])
	if err != nil {
		return nil, fmt.Errorf("otp attempts: %w", err)
	}
	maxAttempts, err := strconv.Atoi(fields["max_attempts"])
	if err != nil {
		return nil, fmt.Errorf("otp max attempts: %w", err)
	}
	createdUnix, err := strconv.ParseInt(fields["created_at"], 10, 64)
	if err != nil {
		return nil, fmt.Errorf("otp created_at: %w", err)
	}
	return &Challenge{
		CodeHash:    fields["code_hash"],
		Attempts:    attempts,
		MaxAttempts: maxAttempts,
		CreatedAt:   time.Unix(createdUnix, 0).UTC(),
	}, nil
}

var incrementAttempts = redis.NewScript(`
if redis.call('EXISTS', KEYS[1]) == 0 then
  return -1
end
local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts')) or 0
local maxa = tonumber(redis.call('HGET', KEYS[1], 'max_attempts')) or 0
if attempts >= maxa then
  return -1
end
return redis.call('HINCRBY', KEYS[1], 'attempts', 1)
`)

func (r *redisOTPRepository) IncrementAttempts(ctx context.Context, phone string) (int, bool, error) {
	n, err := incrementAttempts.Run(ctx, r.rdb, []string{challengeKey(phone)}).Int()
	if err != nil {
		return 0, false, err
	}
	if n < 0 {
		return 0, false, nil
	}
	return n, true, nil
}

func (r *redisOTPRepository) Delete(ctx context.Context, phone string) (bool, error) {
	n, err := r.rdb.Del(ctx, challengeKey(phone)).Result()
	if err != nil {
		return false, err
	}
	return n > 0, nil
}

func scoreTime(values []redis.Z) time.Time {
	if len(values) == 0 {
		return time.Time{}
	}
	millis := int64(values[0].Score)
	return time.UnixMilli(millis).UTC()
}

// disabledOTPRepository is used when OTP_REQUIRED is false.
// Verify still sees no code, and request-OTP does not write one.
type disabledOTPRepository struct{}

func NewDisabledOTPRepository() OTPRepository { return disabledOTPRepository{} }

func (disabledOTPRepository) RequestStats(context.Context, string, time.Time) (int64, time.Time, time.Time, error) {
	return 0, time.Time{}, time.Time{}, fmt.Errorf("otp store is disabled")
}

func (disabledOTPRepository) Save(context.Context, string, string, int, time.Duration) error {
	return fmt.Errorf("otp store is disabled")
}

func (disabledOTPRepository) RecordRequest(context.Context, string, time.Time, time.Duration) (string, error) {
	return "", fmt.Errorf("otp store is disabled")
}

func (disabledOTPRepository) ForgetRequest(context.Context, string, string) error {
	return fmt.Errorf("otp store is disabled")
}

func (disabledOTPRepository) Get(context.Context, string) (*Challenge, error) {
	return nil, nil
}

func (disabledOTPRepository) IncrementAttempts(context.Context, string) (int, bool, error) {
	return 0, false, nil
}

func (disabledOTPRepository) Delete(context.Context, string) (bool, error) {
	return false, nil
}
