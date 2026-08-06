$ErrorActionPreference = 'Stop'

function Assert-Command($name) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "Command '$name' was not found. Install Docker Desktop and reopen PowerShell."
  }
}

Assert-Command docker

try {
  docker info | Out-Null
} catch {
  throw "Docker daemon is not reachable. Start Docker Desktop first, then retry."
}

try {
  docker compose version | Out-Null
} catch {
  throw "'docker compose' plugin is unavailable. Update/reinstall Docker Desktop."
}

# Ensure DOCKER_HOST is set for Testcontainers compatibility on Windows
if (-not $env:DOCKER_HOST) {
  # Check if Docker Desktop exposes the named pipe
  if (Test-Path "\\.\pipe\docker_engine") {
    $env:DOCKER_HOST = "npipe:////./pipe/docker_engine"
    Write-Host "Set DOCKER_HOST=$env:DOCKER_HOST for Testcontainers compatibility" -ForegroundColor Yellow
  }
}

Write-Host "Docker is ready. Starting services..." -ForegroundColor Green
docker compose up --build
