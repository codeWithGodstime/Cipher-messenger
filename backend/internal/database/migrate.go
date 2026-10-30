package database

import (
	"fmt"

	"gorm.io/gorm"

	"messenger/backend/internal/model"
)

// Migrate creates the auth, profile, and chat tables. The project has no
// migration tool; AutoMigrate matches the GORM connection already used by
// this package. OTP codes live in Redis, so a leftover otp_challenges table
// is dropped on startup.
func Migrate(db *gorm.DB) error {
	if db.Migrator().HasTable("otp_challenges") {
		if err := db.Migrator().DropTable("otp_challenges"); err != nil {
			return fmt.Errorf("drop otp_challenges: %w", err)
		}
	}
	if err := db.AutoMigrate(
		&model.User{},
		&model.Session{},
		&model.Conversation{},
		&model.ConversationMember{},
		&model.Message{},
	); err != nil {
		return fmt.Errorf("migrate: %w", err)
	}
	return nil
}
