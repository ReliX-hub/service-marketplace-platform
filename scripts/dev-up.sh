#!/usr/bin/env bash
set -euo pipefail

# Check Docker is installed
if ! command -v docker &>/dev/null; then
    echo "ERROR: 'docker' command not found. Install Docker and try again." >&2
    exit 1
fi

# Check Docker daemon is running
if ! docker info &>/dev/null; then
    echo "ERROR: Docker daemon is not reachable. Start Docker and try again." >&2
    exit 1
fi

# Check Docker Compose
if ! docker compose version &>/dev/null; then
    echo "ERROR: 'docker compose' plugin is unavailable. Update Docker." >&2
    exit 1
fi

echo "Docker is ready. Starting services..."
docker compose up --build
