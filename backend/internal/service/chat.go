package service

import (
	"context"
	"strings"
	"time"
	"unicode/utf8"

	"messenger/backend/internal/apperr"
	"messenger/backend/internal/model"
	"messenger/backend/internal/repository"
)

const (
	maxMessageBody  = 4000
	defaultMsgLimit = 50
	maxMsgLimit     = 100
)

// MessageBroadcaster delivers a persisted message to connected clients.
type MessageBroadcaster interface {
	PublishMessage(message model.Message, recipientIDs []string)
}

type ChatService struct {
	users         repository.UserRepository
	conversations repository.ConversationRepository
	messages      repository.MessageRepository
	broadcaster   MessageBroadcaster
}

func NewChatService(
	users repository.UserRepository,
	conversations repository.ConversationRepository,
	messages repository.MessageRepository,
	broadcaster MessageBroadcaster,
) *ChatService {
	return &ChatService{
		users:         users,
		conversations: conversations,
		messages:      messages,
		broadcaster:   broadcaster,
	}
}

type ConversationSummary struct {
	Conversation model.Conversation
	Peer         *model.User
	LastMessage  *model.Message
	Unread       int
}

func (s *ChatService) ListConversations(ctx context.Context, userID string) ([]ConversationSummary, error) {
	conversations, err := s.conversations.ListForUser(ctx, userID)
	if err != nil {
		return nil, err
	}
	if len(conversations) == 0 {
		return nil, nil
	}

	ids := make([]string, 0, len(conversations))
	peerIDs := make([]string, 0, len(conversations))
	peerByConversation := make(map[string]string, len(conversations))
	for _, conversation := range conversations {
		ids = append(ids, conversation.ID)
		peerID, err := s.conversations.PeerUserID(ctx, conversation.ID, userID)
		if err != nil {
			return nil, err
		}
		if peerID != "" {
			peerIDs = append(peerIDs, peerID)
			peerByConversation[conversation.ID] = peerID
		}
	}

	peers, err := s.users.FindByIDs(ctx, peerIDs)
	if err != nil {
		return nil, err
	}
	peerByID := make(map[string]*model.User, len(peers))
	for i := range peers {
		peerByID[peers[i].ID] = &peers[i]
	}

	latest, err := s.messages.LatestByConversationIDs(ctx, ids)
	if err != nil {
		return nil, err
	}

	out := make([]ConversationSummary, 0, len(conversations))
	for _, conversation := range conversations {
		summary := ConversationSummary{Conversation: conversation, Unread: 0}
		if peerID := peerByConversation[conversation.ID]; peerID != "" {
			summary.Peer = peerByID[peerID]
		}
		if message, ok := latest[conversation.ID]; ok {
			msg := message
			summary.LastMessage = &msg
		}
		out = append(out, summary)
	}
	return out, nil
}

func (s *ChatService) OpenDM(ctx context.Context, userID, peerUserID, rawUsername string) (*ConversationSummary, error) {
	peer, err := resolvePeer(ctx, s.users, userID, peerUserID, rawUsername)
	if err != nil {
		return nil, err
	}

	dmKey := dmKeyFor(userID, peer.ID)
	existing, err := s.conversations.FindByDmKey(ctx, dmKey)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		return s.conversationSummary(ctx, userID, existing)
	}

	id, err := model.NewID()
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC()
	conversation := &model.Conversation{
		ID:        id,
		DmKey:     dmKey,
		CreatedAt: now,
		UpdatedAt: now,
	}
	members := []model.ConversationMember{
		{ConversationID: id, UserID: userID, CreatedAt: now},
		{ConversationID: id, UserID: peer.ID, CreatedAt: now},
	}
	if err := s.conversations.CreateDM(ctx, conversation, members); err != nil {
		return nil, err
	}
	return s.conversationSummary(ctx, userID, conversation)
}

func (s *ChatService) ListMessages(ctx context.Context, userID, conversationID string, before *time.Time, limit int) ([]model.Message, error) {
	if err := s.requireMember(ctx, conversationID, userID); err != nil {
		return nil, err
	}
	if limit <= 0 {
		limit = defaultMsgLimit
	}
	if limit > maxMsgLimit {
		limit = maxMsgLimit
	}
	return s.messages.List(ctx, conversationID, before, limit)
}

func (s *ChatService) SendMessage(ctx context.Context, userID, conversationID, body string) (*model.Message, error) {
	body = strings.TrimSpace(body)
	if body == "" {
		return nil, apperr.Validation("body is required.")
	}
	if utf8.RuneCountInString(body) > maxMessageBody {
		return nil, apperr.Validation("body must be at most 4000 characters.")
	}
	if err := s.requireMember(ctx, conversationID, userID); err != nil {
		return nil, err
	}

	id, err := model.NewID()
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC()
	message := &model.Message{
		ID:             id,
		ConversationID: conversationID,
		SenderID:       userID,
		Body:           body,
		CreatedAt:      now,
	}
	if err := s.messages.Create(ctx, message); err != nil {
		return nil, err
	}
	if err := s.conversations.Touch(ctx, conversationID, now); err != nil {
		return nil, err
	}

	recipients, err := s.conversations.MemberUserIDs(ctx, conversationID)
	if err != nil {
		return nil, err
	}
	if s.broadcaster != nil {
		s.broadcaster.PublishMessage(*message, recipients)
	}
	return message, nil
}

func (s *ChatService) requireMember(ctx context.Context, conversationID, userID string) error {
	conversation, err := s.conversations.FindByID(ctx, conversationID)
	if err != nil {
		return err
	}
	if conversation == nil {
		return apperr.NotFound("Conversation not found.")
	}
	ok, err := s.conversations.IsMember(ctx, conversationID, userID)
	if err != nil {
		return err
	}
	if !ok {
		return apperr.Forbidden("You are not a member of this conversation.")
	}
	return nil
}

func (s *ChatService) conversationSummary(ctx context.Context, userID string, conversation *model.Conversation) (*ConversationSummary, error) {
	peerID, err := s.conversations.PeerUserID(ctx, conversation.ID, userID)
	if err != nil {
		return nil, err
	}
	var peer *model.User
	if peerID != "" {
		peer, err = s.users.FindByID(ctx, peerID)
		if err != nil {
			return nil, err
		}
	}
	latest, err := s.messages.LatestByConversationIDs(ctx, []string{conversation.ID})
	if err != nil {
		return nil, err
	}
	summary := &ConversationSummary{Conversation: *conversation, Peer: peer, Unread: 0}
	if message, ok := latest[conversation.ID]; ok {
		msg := message
		summary.LastMessage = &msg
	}
	return summary, nil
}

func dmKeyFor(a, b string) string {
	if a < b {
		return a + ":" + b
	}
	return b + ":" + a
}
