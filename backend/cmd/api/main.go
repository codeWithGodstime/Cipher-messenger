package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"

	"messenger/backend/internal/config"
	"messenger/backend/internal/database"
	"messenger/backend/internal/handler"
	"messenger/backend/internal/middleware"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/routes"
	"messenger/backend/internal/service"
	"messenger/backend/internal/sms"
	"messenger/backend/internal/token"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	db, err := database.Open()
	if err != nil {
		log.Fatalf("database: %v", err)
	}
	sqlDB, err := db.DB()
	if err != nil {
		log.Fatalf("database: %v", err)
	}
	defer sqlDB.Close()

	if err := database.Migrate(db); err != nil {
		log.Fatalf("migrate: %v", err)
	}

	if cfg.AppEnv == "production" {
		gin.SetMode(gin.ReleaseMode)
	}

	health := handler.NewHealthHandler(
		service.NewHealthService(repository.NewHealthRepository(db)),
	)
	sessions := repository.NewSessionRepository(db)
	tokens := token.NewManager(cfg.JWTSecret, cfg.AccessTTL)
	auth := handler.NewAuthHandler(service.NewAuthService(
		cfg,
		repository.NewUserRepository(db),
		repository.NewOTPRepository(db),
		sessions,
		database.NewTransactor(db),
		sms.NewSender(cfg, log.Default()),
		tokens,
	))

	addr := ":" + cfg.Port

	srv := &http.Server{
		Addr:         addr,
		Handler:      routes.New(health, auth, middleware.RequireAccess(tokens, sessions)),
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	errCh := make(chan error, 1)
	go func() {
		log.Printf("listening on %s", addr)
		errCh <- srv.ListenAndServe()
	}()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	select {
	case err := <-errCh:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("server: %v", err)
		}
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := srv.Shutdown(shutdownCtx); err != nil {
			log.Fatalf("shutdown: %v", err)
		}
	}
}
