package repository

import (
	"context"
	"errors"
	"strings"

	"gorm.io/gorm"

	"messenger/backend/internal/database"
	"messenger/backend/internal/model"
)

var (
	ErrDuplicate = errors.New("duplicate")
	ErrNotFound  = errors.New("not found")
)

type UserRepository interface {
	FindByUsername(ctx context.Context, username string) (*model.User, error)
	FindByID(ctx context.Context, id string) (*model.User, error)
	FindByIDs(ctx context.Context, ids []string) ([]model.User, error)
	// SearchByUsername returns users whose username contains query (already normalized),
	// excluding excludeID. Prefix matches come first, then username order. At most limit rows.
	// Only id, username, and display_name are loaded.
	SearchByUsername(ctx context.Context, query, excludeID string, limit int) ([]model.User, error)
	Create(ctx context.Context, user *model.User) error
	UpdateProfile(ctx context.Context, user *model.User) error
}

type userRepository struct {
	db *gorm.DB
}

func NewUserRepository(db *gorm.DB) UserRepository {
	return &userRepository{db: db}
}

func (r *userRepository) conn(ctx context.Context) *gorm.DB {
	return database.FromContext(ctx, r.db)
}

func (r *userRepository) FindByUsername(ctx context.Context, username string) (*model.User, error) {
	var user model.User
	err := r.conn(ctx).Where("username = ?", username).First(&user).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *userRepository) FindByID(ctx context.Context, id string) (*model.User, error) {
	var user model.User
	err := r.conn(ctx).Where("id = ?", id).First(&user).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *userRepository) FindByIDs(ctx context.Context, ids []string) ([]model.User, error) {
	if len(ids) == 0 {
		return nil, nil
	}
	var users []model.User
	if err := r.conn(ctx).Where("id IN ?", ids).Find(&users).Error; err != nil {
		return nil, err
	}
	return users, nil
}

func (r *userRepository) Create(ctx context.Context, user *model.User) error {
	err := r.conn(ctx).Create(user).Error
	if errors.Is(err, gorm.ErrDuplicatedKey) {
		return ErrDuplicate
	}
	return err
}

func (r *userRepository) UpdateProfile(ctx context.Context, user *model.User) error {
	return r.conn(ctx).Model(&model.User{}).Where("id = ?", user.ID).Updates(map[string]any{
		"display_name":  user.DisplayName,
		"about":         user.About,
		"profile_photo": user.ProfilePhoto,
	}).Error
}

func (r *userRepository) SearchByUsername(ctx context.Context, query, excludeID string, limit int) ([]model.User, error) {
	if query == "" || limit <= 0 {
		return []model.User{}, nil
	}
	fragment := likeFragment(query)
	contains := "%" + fragment + "%"
	prefix := fragment + "%"
	var users []model.User
	err := r.conn(ctx).Raw(
		`SELECT id, username, display_name
		 FROM users
		 WHERE id <> ? AND username LIKE ? ESCAPE ?
		 ORDER BY CASE WHEN username LIKE ? ESCAPE ? THEN 0 ELSE 1 END, username
		 LIMIT ?`,
		excludeID, contains, `\`, prefix, `\`, limit,
	).Scan(&users).Error
	if err != nil {
		return nil, err
	}
	if users == nil {
		return []model.User{}, nil
	}
	return users, nil
}

// likeFragment escapes LIKE wildcards so the query is matched literally.
// Usernames are stored lowercase, so LIKE matches on both SQLite and Postgres.
func likeFragment(query string) string {
	var b strings.Builder
	for _, r := range query {
		switch r {
		case '\\', '%', '_':
			b.WriteByte('\\')
		}
		b.WriteRune(r)
	}
	return b.String()
}
