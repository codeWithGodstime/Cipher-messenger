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
	"github.com/joho/godotenv"

	"messenger/backend/internal/config"
	"messenger/backend/internal/database"
	"messenger/backend/internal/handler"
	"messenger/backend/internal/middleware"
	"messenger/backend/internal/repository"
	"messenger/backend/internal/routes"
	"messenger/backend/internal/service"
	"messenger/backend/internal/utils/httpapi"
	"messenger/backend/internal/utils/token"
	"messenger/backend/internal/ws"
)

func main() {
	if len(os.Args) >= 2 && os.Args[1] == "-openapi" {
		if len(os.Args) != 3 {
			log.Fatal("usage: api -openapi <openapi.yaml>")
		}
		if err := httpapi.WriteOpenAPI(os.Args[2]); err != nil {
			log.Fatal(err)
		}
		return
	}

	// backend/.env holds local defaults. Variables already set in the process win.
	_ = godotenv.Load()

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
	users := repository.NewUserRepository(db)
	sessions := repository.NewSessionRepository(db)
	tokens := token.NewManager(cfg.JWTSecret, cfg.AccessTTL)
	auth := handler.NewAuthHandler(service.NewAuthService(
		cfg,
		users,
		sessions,
		database.NewTransactor(db),
		tokens,
	))
	profileService := service.NewProfileService(users)
	profile := handler.NewProfileHandler(profileService)
	keys := repository.NewKeyRepository(db)
	keyService := service.NewKeyService(keys, users)
	keysHandler := handler.NewKeysHandler(keyService)

	hub := ws.NewHub()
	conversations := repository.NewConversationRepository(db)
	messages := repository.NewMessageRepository(db)
	chatService := service.NewChatService(users, conversations, messages, hub)
	attachments, err := service.NewAttachmentService(
		conversations,
		repository.NewAttachmentRepository(db),
		cfg.MediaDir,
	)
	if err != nil {
		log.Fatalf("media: %v", err)
	}
	chat := handler.NewChatHandler(chatService, attachments)
	contacts := handler.NewContactHandler(
		service.NewContactService(users, repository.NewContactRepository(db)),
	)
	wsHandler := handler.NewWSHandler(hub, chatService, profileService, keyService, tokens, sessions)

	addr := ":" + cfg.Port

	srv := &http.Server{
		Addr: addr,
		Handler: routes.New(
			health,
			auth,
			profile,
			keysHandler,
			chat,
			contacts,
			wsHandler,
			middleware.RequireAccess(tokens, sessions),
		),
		// Headers stay on a short clock. The body timeout covers a 32 MiB ciphertext upload.
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       90 * time.Second,
		// WriteTimeout must be zero so long-lived WebSocket connections are not killed.
		WriteTimeout: 0,
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
