package model

import (
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
	Body           string    `gorm:"type:text;not null"`
	CreatedAt      time.Time `gorm:"not null;index:idx_messages_conversation_created,priority:2"`
}

func (Message) TableName() string { return "messages" }

// MaxAttachmentBytes is the largest ciphertext blob an upload may store.
// The file key is not in this blob; it travels inside the Signal message.
const MaxAttachmentBytes = 32 << 20

// Attachment is one opaque ciphertext object for a conversation.
// The server stores the bytes and their SHA-256. It does not store a file name,
// a media type, or the key that decrypts the bytes.
type Attachment struct {
	ID             string    `gorm:"primaryKey;size:36"`
	ConversationID string    `gorm:"size:36;not null;index"`
	UploaderID     string    `gorm:"size:36;not null;index"`
	ByteSize       int64     `gorm:"not null"`
	Digest         string    `gorm:"size:64;not null"`
	CreatedAt      time.Time `gorm:"not null"`
}

func (Attachment) TableName() string { return "attachments" }

// Contact is a saved peer for the owner. Unique per owner+peer pair.
type Contact struct {
	ID        string    `gorm:"primaryKey;size:36"`
	OwnerID   string    `gorm:"size:36;not null;uniqueIndex:idx_contacts_owner_peer,priority:1;index"`
	PeerID    string    `gorm:"size:36;not null;uniqueIndex:idx_contacts_owner_peer,priority:2"`
	CreatedAt time.Time `gorm:"not null"`
}

func (Contact) TableName() string { return "contacts" }

type IdentityKey struct {
	ID        string    `gorm:"primaryKey;size:36"`
	UserID    string    `gorm:"size:36;not null;uniqueIndex"`
	PublicKey string    `gorm:"size:256;not null"`
	CreatedAt time.Time `gorm:"not null"`
}

func (IdentityKey) TableName() string { return "identity_keys" }

// MaxOneTimePreKeys is the most one-time prekeys one upload may contain.
const MaxOneTimePreKeys = 100

// MaxStoredOneTimePreKeys is the most one-time prekeys an account may have
// waiting on the server. Clients upload a batch of MaxOneTimePreKeys when
// fewer than 10 remain, so the pool stays near one batch.
const MaxStoredOneTimePreKeys = 200

// SignedPreKey is the account's current libsignal signed prekey.
// One row per user; a later upload replaces it.
type SignedPreKey struct {
	ID        string    `gorm:"primaryKey;size:36"`
	UserID    string    `gorm:"size:36;not null;uniqueIndex"`
	KeyID     int       `gorm:"not null"`
	PublicKey string    `gorm:"size:256;not null"`
	Signature string    `gorm:"size:256;not null"`
	CreatedAt time.Time `gorm:"not null"`
}

func (SignedPreKey) TableName() string { return "signed_prekeys" }

// KyberPreKey is the account's current libsignal last-resort Kyber1024 prekey.
// One row per user; a later upload replaces it. Sessions require this key.
type KyberPreKey struct {
	ID        string    `gorm:"primaryKey;size:36"`
	UserID    string    `gorm:"size:36;not null;uniqueIndex"`
	KeyID     int       `gorm:"not null"`
	PublicKey string    `gorm:"size:4096;not null"`
	Signature string    `gorm:"size:256;not null"`
	CreatedAt time.Time `gorm:"not null"`
}

func (KyberPreKey) TableName() string { return "kyber_prekeys" }

// OneTimePreKey is one libsignal one-time prekey. Uploads add keys.
// An account holds at most MaxStoredOneTimePreKeys rows. A claim deletes one.
type OneTimePreKey struct {
	ID        string    `gorm:"primaryKey;size:36"`
	UserID    string    `gorm:"size:36;not null;uniqueIndex:idx_one_time_prekeys_user_key,priority:1;index"`
	KeyID     int       `gorm:"not null;uniqueIndex:idx_one_time_prekeys_user_key,priority:2"`
	PublicKey string    `gorm:"size:256;not null"`
	CreatedAt time.Time `gorm:"not null"`
}

func (OneTimePreKey) TableName() string { return "one_time_prekeys" }
