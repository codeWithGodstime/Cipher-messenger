package repository

import (
	"context"
	"errors"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

type ContactRepository interface {
	ListForOwner(ctx context.Context, ownerID string) ([]model.Contact, error)
	FindByOwnerPeer(ctx context.Context, ownerID, peerID string) (*model.Contact, error)
	FindByIDForOwner(ctx context.Context, id, ownerID string) (*model.Contact, error)
	Create(ctx context.Context, contact *model.Contact) error
	Delete(ctx context.Context, id, ownerID string) (bool, error)
}

type contactRepository struct {
	db *gorm.DB
}

func NewContactRepository(db *gorm.DB) ContactRepository {
	return &contactRepository{db: db}
}

func (r *contactRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *contactRepository) ListForOwner(ctx context.Context, ownerID string) ([]model.Contact, error) {
	var contacts []model.Contact
	err := r.conn(ctx).
		Where("owner_id = ?", ownerID).
		Order("created_at DESC").
		Find(&contacts).Error
	if err != nil {
		return nil, err
	}
	return contacts, nil
}

func (r *contactRepository) FindByOwnerPeer(ctx context.Context, ownerID, peerID string) (*model.Contact, error) {
	var contact model.Contact
	err := r.conn(ctx).
		Where("owner_id = ? AND peer_id = ?", ownerID, peerID).
		First(&contact).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &contact, nil
}

func (r *contactRepository) FindByIDForOwner(ctx context.Context, id, ownerID string) (*model.Contact, error) {
	var contact model.Contact
	err := r.conn(ctx).
		Where("id = ? AND owner_id = ?", id, ownerID).
		First(&contact).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &contact, nil
}

func (r *contactRepository) Create(ctx context.Context, contact *model.Contact) error {
	err := r.conn(ctx).
		Clauses(clause.OnConflict{DoNothing: true}).
		Create(contact).Error
	if err != nil {
		return err
	}
	existing, err := r.FindByOwnerPeer(ctx, contact.OwnerID, contact.PeerID)
	if err != nil {
		return err
	}
	if existing == nil {
		return ErrNotFound
	}
	*contact = *existing
	return nil
}

func (r *contactRepository) Delete(ctx context.Context, id, ownerID string) (bool, error) {
	result := r.conn(ctx).
		Where("id = ? AND owner_id = ?", id, ownerID).
		Delete(&model.Contact{})
	if result.Error != nil {
		return false, result.Error
	}
	return result.RowsAffected > 0, nil
}
