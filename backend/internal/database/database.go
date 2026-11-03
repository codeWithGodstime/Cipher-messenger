package database

import (
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/glebarez/sqlite"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func Open() (*gorm.DB, error) {
	dsn := strings.TrimSpace(os.Getenv("DATABASE_URL"))
	if dsn == "" {
		return nil, fmt.Errorf("DATABASE_URL is required")
	}

	dialector, sqliteFile, err := dialectorFor(dsn)
	if err != nil {
		return nil, err
	}

	db, err := gorm.Open(dialector, &gorm.Config{
		// Expected misses (open-or-create DM, etc.) should not spam "record not found".
		Logger: logger.Default.LogMode(logger.Warn),
	})
	if err != nil {
		return nil, fmt.Errorf("open database: %w", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, fmt.Errorf("sql db: %w", err)
	}

	if sqliteFile {
		// A single connection avoids SQLITE_BUSY on the local file.
		sqlDB.SetMaxOpenConns(1)
		sqlDB.SetMaxIdleConns(1)
	} else {
		sqlDB.SetMaxOpenConns(10)
		sqlDB.SetMaxIdleConns(5)
	}
	sqlDB.SetConnMaxLifetime(time.Hour)

	if err := sqlDB.Ping(); err != nil {
		_ = sqlDB.Close()
		return nil, fmt.Errorf("ping database: %w", err)
	}

	return db, nil
}

func dialectorFor(dsn string) (gorm.Dialector, bool, error) {
	lower := strings.ToLower(dsn)
	switch {
	case strings.HasPrefix(lower, "postgres://"), strings.HasPrefix(lower, "postgresql://"):
		return postgres.Open(dsn), false, nil
	case strings.HasPrefix(lower, "sqlite:"):
		fileDSN, err := sqliteFileDSN(dsn)
		if err != nil {
			return nil, false, err
		}
		return sqlite.Open(fileDSN), true, nil
	default:
		return nil, false, fmt.Errorf("DATABASE_URL must start with postgres:// or sqlite://")
	}
}

// sqliteFileDSN accepts sqlite://messenger.db and sqlite:messenger.db.
// A query string on the URL is kept. Otherwise WAL mode, a busy timeout,
// and foreign keys are applied to every connection.
func sqliteFileDSN(raw string) (string, error) {
	rest := raw
	lower := strings.ToLower(raw)
	switch {
	case strings.HasPrefix(lower, "sqlite://"):
		rest = raw[len("sqlite://"):]
	case strings.HasPrefix(lower, "sqlite:"):
		rest = raw[len("sqlite:"):]
	}
	path := rest
	extra := ""
	if i := strings.Index(rest, "?"); i >= 0 {
		path = rest[:i]
		extra = rest[i:]
	}
	if path == "" {
		return "", fmt.Errorf("DATABASE_URL sqlite path is empty")
	}
	if !strings.HasPrefix(path, "file:") {
		path = "file:" + path
	}
	if extra != "" {
		return path + extra, nil
	}
	return path + "?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)", nil
}
