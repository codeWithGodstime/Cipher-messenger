.PHONY: build start openapi

BINARY := bin/api
ifeq ($(OS),Windows_NT)
BINARY := bin/api.exe
endif

build:
	go build -o $(BINARY) ./cmd/api

# Loads backend/.env. DATABASE_URL is required (sqlite:// locally). PORT defaults to 8080.
start:
	go run ./cmd/api

openapi:
	go run ./cmd/api -openapi ../contract/openapi.yaml
