#!/usr/bin/env bash
# Contract tests — valida respuestas HTTP contra /api-docs (OpenAPI / springdoc).
# Uso: ./scripts/contract-test.sh
#
# Requisitos: Docker (Testcontainers Postgres).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }

bold "=== OpenAPI contract tests (TEST-02) ==="

if ! docker info >/dev/null 2>&1; then
  red "Docker no está en ejecución (Testcontainers lo necesita)."
  exit 1
fi

./gradlew contractTest --tests "*OpenApiContractIntegrationTest*" "$@"
green "Contract tests OK"
