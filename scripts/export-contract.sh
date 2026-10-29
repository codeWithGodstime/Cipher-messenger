#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root/backend"
go run ./cmd/api -openapi "$root/contract/openapi.yaml"
