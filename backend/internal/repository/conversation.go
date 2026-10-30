package repository

import (
	"context"
	"errors"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type ConversationRepository interface {
	FindByID(ctx context.Context, id string) (*model.Conversation, error)
	FindByDmKey(ctx context.Context, dmKey string) (*model.Conversation, error)
	CreateDM(ctx context.Context, conversation *model.Conversation, members []model.ConversationMember) error
	ListForUser(ctx context.Context, userID string) ([]model.Conversation, error)
	IsMember(ctx context.Context, conversationID, userID string) (bool, error)
	PeerUserID(ctx context.Context, conversationID, userID string) (string, error)
	MemberUserIDs(ctx context.Context, conversationID string) ([]string, error)
	Touch(ctx context.Context, conversationID string, at time.Time) error
}

type conversationRepository struct {
	db *gorm.DB
}

func NewConversationRepository(db *gorm.DB) ConversationRepository {
	return &conversationRepository{db: db}
}

func (r *conversationRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *conversationRepository) FindByID(ctx context.Context, id string) (*model.Conversation, error) {
	var conversation model.Conversation
	err := r.conn(ctx).Where("id = ?", id).First(&conversation).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &conversation, nil
}

func (r *conversationRepository) FindByDmKey(ctx context.Context, dmKey string) (*model.Conversation, error) {
	var conversation model.Conversation
	err := r.conn(ctx).Where("dm_key = ?", dmKey).First(&conversation).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &conversation, nil
}

func (r *conversationRepository) CreateDM(ctx context.Context, conversation *model.Conversation, members []model.ConversationMember) error {
	return r.conn(ctx).Transaction(func(tx *gorm.DB) error {
		if err := tx.Clauses(clause.OnConflict{DoNothing: true}).Create(conversation).Error; err != nil {
			return err
		}
		var existing model.Conversation
		if err := tx.Where("dm_key = ?", conversation.DmKey).First(&existing).Error; err != nil {
			return err
		}
		*conversation = existing
		for i := range members {
			members[i].ConversationID = existing.ID
		}
		return tx.Clauses(clause.OnConflict{DoNothing: true}).Create(&members).Error
	})
}

func (r *conversationRepository) ListForUser(ctx context.Context, userID string) ([]model.Conversation, error) {
	var conversations []model.Conversation
	err := r.conn(ctx).
		Joins("JOIN conversation_members ON conversation_members.conversation_id = conversations.id").
		Where("conversation_members.user_id = ?", userID).
		Order("conversations.updated_at DESC").
		Find(&conversations).Error
	if err != nil {
		return nil, err
	}
	return conversations, nil
}

func (r *conversationRepository) IsMember(ctx context.Context, conversationID, userID string) (bool, error) {
	var count int64
	err := r.conn(ctx).Model(&model.ConversationMember{}).
		Where("conversation_id = ? AND user_id = ?", conversationID, userID).
		Count(&count).Error
	if err != nil {
		return false, err
	}
	return count > 0, nil
}

func (r *conversationRepository) PeerUserID(ctx context.Context, conversationID, userID string) (string, error) {
	var member model.ConversationMember
	err := r.conn(ctx).
		Where("conversation_id = ? AND user_id <> ?", conversationID, userID).
		First(&member).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return "", nil
	}
	if err != nil {
		return "", err
	}
	return member.UserID, nil
}

func (r *conversationRepository) MemberUserIDs(ctx context.Context, conversationID string) ([]string, error) {
	var members []model.ConversationMember
	if err := r.conn(ctx).Where("conversation_id = ?", conversationID).Find(&members).Error; err != nil {
		return nil, err
	}
	ids := make([]string, 0, len(members))
	for _, member := range members {
		ids = append(ids, member.UserID)
	}
	return ids, nil
}

func (r *conversationRepository) Touch(ctx context.Context, conversationID string, at time.Time) error {
	return r.conn(ctx).Model(&model.Conversation{}).
		Where("id = ?", conversationID).
		Update("updated_at", at).Error
}
