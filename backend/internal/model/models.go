package model

import (
	"crypto/rand"
	"fmt"
	"time"
)

const DefaultAbout = "Hey there! I am using Messenger"

type User struct {
	ID           string    `gorm:"primaryKey;size:36"`
	Phone        string    `gorm:"size:16;not null;uniqueIndex"`
	DisplayName  string    `gorm:"size:64;not null;default:''"`
	About        string    `gorm:"size:139;not null"`
	ProfilePhoto string    `gorm:"size:2048;not null;default:''"`
	CreatedAt    time.Time `gorm:"not null"`
	UpdatedAt    time.Time `gorm:"not null"`
}

func (User) TableName() string { return "users" }

type Session struct {
	ID          string    `gorm:"primaryKey;size:36"`
	UserID      string    `gorm:"size:36;not null;index"`
	RefreshHash string    `gorm:"size:64;not null;uniqueIndex"`
	ExpiresAt   time.Time `gorm:"not null;index"`
	RevokedAt   *time.Time
	CreatedAt   time.Time `gorm:"not null"`
}

func (Session) TableName() string { return "sessions" }

func NewID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("new id: %w", err)
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}
