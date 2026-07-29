#!/usr/bin/env bash
# ENV-02 — Esperar healthchecks del stack (API + Keycloak token; frontend opcional).
# Uso:
#   ./scripts/wait-for-stack.sh
#   API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 ./scripts/wait-for-stack.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
# shellcheck source=lib/load-env.sh
source "$ROOT/scripts/lib/load-env.sh"

API_URL="${API_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
FRONTEND_URL="${FRONTEND_URL:-}"
REALM="${KEYCLOAK_REALM}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET}"
TOKEN_USER="${WAIT_TOKEN_USER:-${KEYCLOAK_ADMIN}}"
TOKEN_PASS="${WAIT_TOKEN_PASS:-${KEYCLOAK_ADMIN_PASSWORD}}"
TIMEOUT_SEC="${TIMEOUT_SEC:-300}"
SLEEP_SEC="${SLEEP_SEC:-5}"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }

deadline=$((SECONDS + TIMEOUT_SEC))

wait_http() {
  local name="$1" url="$2"
  bold "Esperando $name → $url"
  while (( SECONDS < deadline )); do
    if curl -sf "$url" >/dev/null; then
      green "OK $name"
      return 0
    fi
    echo "  … ($((deadline - SECONDS))s restantes)"
    sleep "$SLEEP_SEC"
  done
  red "Timeout esperando $name ($url)"
  return 1
}

wait_keycloak_token() {
  local url="$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token"
  bold "Esperando Keycloak token → $url"
  while (( SECONDS < deadline )); do
    local body
    body=$(curl -s -X POST "$url" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "grant_type=password" \
      -d "client_id=$CLIENT_ID" \
      -d "client_secret=$CLIENT_SECRET" \
      -d "username=$TOKEN_USER" \
      -d "password=$TOKEN_PASS" || true)
    if printf '%s' "$body" | python3 -c "import sys,json; json.load(sys.stdin)['access_token']" 2>/dev/null; then
      green "OK Keycloak access_token"
      return 0
    fi
    echo "  … ($((deadline - SECONDS))s restantes)"
    sleep "$SLEEP_SEC"
  done
  red "Timeout esperando token Keycloak"
  return 1
}

bold "=== wait-for-stack (timeout ${TIMEOUT_SEC}s) ==="
wait_http "API health" "$API_URL/actuator/health"
wait_keycloak_token
if [[ -n "$FRONTEND_URL" ]]; then
  wait_http "Frontend" "$FRONTEND_URL"
fi
green "Stack listo"
