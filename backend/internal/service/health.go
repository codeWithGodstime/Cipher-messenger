package service

import (
	"context"
	"fmt"

	"messenger/backend/internal/repository"
)

type HealthService struct {
	health repository.HealthRepository
}

func NewHealthService(health repository.HealthRepository) *HealthService {
	return &HealthService{health: health}
}

func (s *HealthService) Check(ctx context.Context) error {
	if err := s.health.Ping(ctx); err != nil {
		return fmt.Errorf("health check: %w", err)
	}
	return nil
}
