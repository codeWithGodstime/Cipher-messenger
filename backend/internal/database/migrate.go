package database

import (
	"fmt"

	"gorm.io/gorm"

	"messenger/backend/internal/model"
)

// Migrate creates the auth, profile, chat, contact, and identity-key tables.
// The project has no migration tool; AutoMigrate matches the GORM connection
// already used by this package. OTP codes live in Redis, so a leftover
// otp_challenges table is dropped on startup. Early installs keyed users by
// phone; that column is renamed to username when present.
func Migrate(db *gorm.DB) error {
	if db.Migrator().HasTable("otp_challenges") {
		if err := db.Migrator().DropTable("otp_challenges"); err != nil {
			return fmt.Errorf("drop otp_challenges: %w", err)
		}
	}
	if db.Migrator().HasTable(&model.User{}) {
		hasPhone := db.Migrator().HasColumn(&model.User{}, "phone")
		hasUsername := db.Migrator().HasColumn(&model.User{}, "username")
		if hasPhone && !hasUsername {
			if err := db.Migrator().RenameColumn(&model.User{}, "phone", "username"); err != nil {
				return fmt.Errorf("rename users.phone to username: %w", err)
			}
		}
	}
	if err := db.AutoMigrate(
		&model.User{},
		&model.Session{},
		&model.Conversation{},
		&model.ConversationMember{},
		&model.Message{},
		&model.Contact{},
		&model.IdentityKey{},
	); err != nil {
		return fmt.Errorf("migrate: %w", err)
	}
	return nil
}
