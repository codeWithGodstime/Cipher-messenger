package server

import (
	"net/http"

	"messenger/backend/internal/handler"
)

func New() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", handler.Health)
	return mux
}
