#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose plugin is required" >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo ".env is missing. Copy .env.example to .env and fill production values first." >&2
  exit 1
fi

chmod 600 .env

echo "Validating compose configuration..."
docker compose --env-file .env config >/dev/null

echo "Building and starting services..."
docker compose --env-file .env up -d --build

echo "Current service status:"
docker compose --env-file .env ps

echo "Recent geo-server logs:"
docker compose --env-file .env logs --tail=80 geo-server
