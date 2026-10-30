package model

import (
	"crypto/rand"
	"fmt"
	"time"
)

const DefaultAbout = "Hey there! I am using Messenger"

type User struct {
	ID           string    `gorm:"primaryKey;size:36"`
	Username     string    `gorm:"size:32;not null;uniqueIndex"`
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

// Conversation is a 1:1 DM. DmKey is sorted "userA:userB" so open-or-create is unique.
type Conversation struct {
	ID        string    `gorm:"primaryKey;size:36"`
	DmKey     string    `gorm:"size:73;not null;uniqueIndex"`
	CreatedAt time.Time `gorm:"not null"`
	UpdatedAt time.Time `gorm:"not null;index"`
}

func (Conversation) TableName() string { return "conversations" }

type ConversationMember struct {
	ConversationID string    `gorm:"primaryKey;size:36"`
	UserID         string    `gorm:"primaryKey;size:36;index"`
	CreatedAt      time.Time `gorm:"not null"`
}

func (ConversationMember) TableName() string { return "conversation_members" }

type Message struct {
	ID             string    `gorm:"primaryKey;size:36"`
	ConversationID string    `gorm:"size:36;not null;index:idx_messages_conversation_created,priority:1"`
	SenderID       string    `gorm:"size:36;not null;index"`
	Body           string    `gorm:"size:4000;not null"`
	CreatedAt      time.Time `gorm:"not null;index:idx_messages_conversation_created,priority:2"`
}

func (Message) TableName() string { return "messages" }

// Contact is a saved peer for the owner. Unique per owner+peer pair.
type Contact struct {
	ID        string    `gorm:"primaryKey;size:36"`
	OwnerID   string    `gorm:"size:36;not null;uniqueIndex:idx_contacts_owner_peer,priority:1;index"`
	PeerID    string    `gorm:"size:36;not null;uniqueIndex:idx_contacts_owner_peer,priority:2"`
	CreatedAt time.Time `gorm:"not null"`
}

func (Contact) TableName() string { return "contacts" }

func NewID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("new id: %w", err)
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}
