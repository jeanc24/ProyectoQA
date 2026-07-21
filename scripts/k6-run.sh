#!/usr/bin/env bash
# TEST-04 — Ejecutar k6 load / stress contra la API (staging local :8080).
#
# Uso:
#   docker compose up -d --build postgres keycloak tempo loki alloy api
#   ./scripts/k6-run.sh load
#   ./scripts/k6-run.sh stress
#   ./scripts/k6-run.sh all
#
# Vars opcionales: BASE_URL, KEYCLOAK_URL, K6_USERNAME, K6_PASSWORD
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/docs/final/testing/k6"
MODE="${1:-load}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"

mkdir -p "$OUT"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

# Desde el contenedor k6, el host es host.docker.internal (Docker Desktop).
# En Linux CI suele usarse --network host + localhost.
BASE_URL="${BASE_URL:-http://host.docker.internal:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://host.docker.internal:8081}"

DOCKER_EXTRA=(--add-host=host.docker.internal:host-gateway)
if [[ "$(uname -s)" == "Linux" ]] && [[ "${K6_USE_HOST_NETWORK:-}" == "1" ]]; then
  DOCKER_EXTRA=(--network host)
  BASE_URL="${BASE_URL/host.docker.internal/localhost}"
  KEYCLOAK_URL="${KEYCLOAK_URL/host.docker.internal/localhost}"
fi

run_one() {
  local script="$1"
  local name
  name="$(basename "$script" .js)"
  bold "=== k6 ${name} ==="
  echo "BASE_URL=$BASE_URL"

  # Health desde el host (más fiable que desde k6 setup en algunos entornos)
  local host_health="${BASE_URL/host.docker.internal/localhost}"
  if ! curl -sf "${host_health}/actuator/health" >/dev/null 2>&1; then
    # si BASE_URL ya es localhost
    if ! curl -sf "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      red "API no responde. Levanta: docker compose up -d postgres keycloak tempo loki alloy api"
      exit 1
    fi
  fi

  docker run --rm \
    "${DOCKER_EXTRA[@]}" \
    -e BASE_URL="$BASE_URL" \
    -e KEYCLOAK_URL="$KEYCLOAK_URL" \
    -e K6_OUT_DIR=docs/final/testing/k6 \
    -e K6_USERNAME="${K6_USERNAME:-viewer}" \
    -e K6_PASSWORD="${K6_PASSWORD:-viewer}" \
    -v "$ROOT:/work" \
    -w /work \
    "$K6_IMAGE" run "tests/k6/${script}"

  green "Reporte: $OUT/${name}-summary.json / .txt"
}

case "$MODE" in
  load) run_one load-products.js ;;
  stress) run_one stress-products.js ;;
  all)
    run_one load-products.js
    run_one stress-products.js
    ;;
  *)
    yellow "Uso: $0 {load|stress|all}"
    exit 1
    ;;
esac
