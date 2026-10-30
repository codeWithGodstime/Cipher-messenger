package repository

import (
	"context"
	"time"

	"gorm.io/gorm"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type MessageRepository interface {
	Create(ctx context.Context, message *model.Message) error
	List(ctx context.Context, conversationID string, before *time.Time, limit int) ([]model.Message, error)
	LatestByConversationIDs(ctx context.Context, conversationIDs []string) (map[string]model.Message, error)
}

type messageRepository struct {
	db *gorm.DB
}

func NewMessageRepository(db *gorm.DB) MessageRepository {
	return &messageRepository{db: db}
}

func (r *messageRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *messageRepository) Create(ctx context.Context, message *model.Message) error {
	return r.conn(ctx).Create(message).Error
}

func (r *messageRepository) List(ctx context.Context, conversationID string, before *time.Time, limit int) ([]model.Message, error) {
	q := r.conn(ctx).Where("conversation_id = ?", conversationID)
	if before != nil {
		q = q.Where("created_at < ?", *before)
	}
	var messages []model.Message
	if err := q.Order("created_at DESC").Limit(limit).Find(&messages).Error; err != nil {
		return nil, err
	}
	return messages, nil
}

func (r *messageRepository) LatestByConversationIDs(ctx context.Context, conversationIDs []string) (map[string]model.Message, error) {
	out := make(map[string]model.Message, len(conversationIDs))
	if len(conversationIDs) == 0 {
		return out, nil
	}
	var messages []model.Message
	err := r.conn(ctx).
		Where("conversation_id IN ?", conversationIDs).
		Order("created_at DESC").
		Find(&messages).Error
	if err != nil {
		return nil, err
	}
	for _, message := range messages {
		if _, ok := out[message.ConversationID]; ok {
			continue
		}
		out[message.ConversationID] = message
	}
	return out, nil
}
