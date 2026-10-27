package database

import (
	"context"

	"gorm.io/gorm"
)

type txKey struct{}

// Transactor runs a function inside a database transaction.
// Repositories read the transaction from the context.
type Transactor interface {
	InTx(ctx context.Context, fn func(ctx context.Context) error) error
}

type transactor struct {
	db *gorm.DB
}

func NewTransactor(db *gorm.DB) Transactor {
	return &transactor{db: db}
}

func (t *transactor) InTx(ctx context.Context, fn func(ctx context.Context) error) error {
	return t.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		return fn(context.WithValue(ctx, txKey{}, tx))
	})
}

func WithTx(ctx context.Context, tx *gorm.DB) context.Context {
	return context.WithValue(ctx, txKey{}, tx)
}

func FromContext(ctx context.Context, db *gorm.DB) *gorm.DB {
	if tx, ok := ctx.Value(txKey{}).(*gorm.DB); ok && tx != nil {
		return tx.WithContext(ctx)
	}
	return db.WithContext(ctx)
}
