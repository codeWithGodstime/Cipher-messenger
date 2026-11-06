package database

import (
	"fmt"

	"gorm.io/gorm"

	"messenger/backend/internal/model"
)

// Migrate creates the auth, profile, chat, contact, and key tables.
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
		&model.SignedPreKey{},
		&model.KyberPreKey{},
		&model.OneTimePreKey{},
	); err != nil {
		return fmt.Errorf("migrate: %w", err)
	}
	if err := messageBodyAsText(db); err != nil {
		return err
	}
	return nil
}

// messageBodyAsText stores messages.body as text. AutoMigrate leaves an
// existing Postgres varchar column unchanged, so the type is altered here.
// SQLite already stores strings as text.
func messageBodyAsText(db *gorm.DB) error {
	if db.Dialector.Name() != "postgres" {
		return nil
	}
	if !db.Migrator().HasTable(&model.Message{}) {
		return nil
	}
	if err := db.Exec(`ALTER TABLE messages ALTER COLUMN body TYPE text`).Error; err != nil {
		return fmt.Errorf("messages.body as text: %w", err)
	}
	return nil
}
