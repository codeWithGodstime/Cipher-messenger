package database

import (
	"fmt"

	"gorm.io/gorm"

	"messenger/backend/internal/model"
)

// Migrate creates the auth and profile tables. The project has no migration
// tool; AutoMigrate matches the GORM connection already used by this package.
func Migrate(db *gorm.DB) error {
	if err := db.AutoMigrate(&model.User{}, &model.OTPChallenge{}, &model.Session{}); err != nil {
		return fmt.Errorf("migrate: %w", err)
	}
	return nil
}
