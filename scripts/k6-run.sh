#!/usr/bin/env bash
# TEST-04 — Ejecutar k6 load / stress contra la API (staging local :8080).
#
# Uso:
#   docker compose up -d --build postgres keycloak tempo loki alloy api
#   ./scripts/k6-run.sh load
#   ./scripts/k6-run.sh stress
#   ./scripts/k6-run.sh all
#
# Vars opcionales: BASE_URL, KEYCLOAK_URL, KEYCLOAK_CLIENT_SECRET, K6_USERNAME, K6_PASSWORD
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
# shellcheck source=lib/load-env.sh
source "$ROOT/scripts/lib/load-env.sh"
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

wait_for_api() {
  local url="$1"
  local max="${2:-60}"
  local i
  bold "Esperando API healthy en $url ..."
  for i in $(seq 1 "$max"); do
    if curl -sf "$url/actuator/health" >/dev/null 2>&1; then
      green "API UP (${i}s)"
      return 0
    fi
    sleep 2
  done
  return 1
}

# Token desde el host con issuer localhost (coincide con KEYCLOAK_ISSUER_URI de la API).
fetch_host_token() {
  local kc_url="${KEYCLOAK_TOKEN_URL:-http://localhost:8081}"
  local user="${K6_USERNAME:-viewer}"
  local pass="${K6_PASSWORD:-viewer}"
  local client_id="${KEYCLOAK_CLIENT_ID}"
  local client_secret="${KEYCLOAK_CLIENT_SECRET}"
  curl -sf -X POST "$kc_url/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=$client_id" \
    -d "client_secret=$client_secret" \
    -d "username=$user" \
    -d "password=$pass" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
}

run_one() {
  local script="$1"
  local name
  name="$(basename "$script" .js)"
  bold "=== k6 ${name} ==="
  echo "BASE_URL=$BASE_URL"

  # Health desde el host (Docker Desktop: host.docker.internal → localhost)
  local host_health="${BASE_URL//host.docker.internal/localhost}"
  if ! wait_for_api "$host_health" 60; then
    if [[ "$host_health" != "$BASE_URL" ]] && wait_for_api "$BASE_URL" 5; then
      :
    else
      red "API no responde tras ~2 min. Levanta: docker compose up -d postgres keycloak tempo loki alloy api"
      exit 1
    fi
  fi

  bold "Obteniendo JWT (issuer localhost)..."
  local token
  token="$(fetch_host_token)" || {
    red "No se pudo obtener token de Keycloak en localhost:8081"
    exit 1
  }

  docker run --rm \
    "${DOCKER_EXTRA[@]}" \
    -e BASE_URL="$BASE_URL" \
    -e KEYCLOAK_URL="$KEYCLOAK_URL" \
    -e KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
    -e KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID}" \
    -e KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET}" \
    -e K6_OUT_DIR=docs/final/testing/k6 \
    -e K6_USERNAME="${K6_USERNAME:-viewer}" \
    -e K6_PASSWORD="${K6_PASSWORD:-viewer}" \
    -e K6_ACCESS_TOKEN="$token" \
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
