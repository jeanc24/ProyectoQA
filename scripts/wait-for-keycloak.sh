#!/usr/bin/env bash
# Espera a que Keycloak emita un access_token (password grant).
# Vars: KEYCLOAK_URL, KEYCLOAK_REALM, KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET,
#       KEYCLOAK_ADMIN, KEYCLOAK_ADMIN_PASSWORD (defaults demo vía lib/load-env.sh)
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

KC="${KEYCLOAK_URL:-http://localhost:8081}"
REALM="${KEYCLOAK_REALM}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET}"
ADMIN_USER="${KEYCLOAK_ADMIN}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD}"
MAX="${KEYCLOAK_WAIT_MAX:-60}"
TOKEN_URL="$KC/realms/$REALM/protocol/openid-connect/token"

echo "Esperando token Keycloak en $TOKEN_URL ..."
for i in $(seq 1 "$MAX"); do
  BODY=$(curl -s -X POST "$TOKEN_URL" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=$CLIENT_ID" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASS" || true)
  if echo "$BODY" | python3 -c "import sys,json; json.load(sys.stdin)['access_token']" 2>/dev/null; then
    echo "Keycloak UP (${i})"
    exit 0
  fi
  echo "waiting Keycloak ($i)..."
  sleep 5
done

echo "Keycloak no emitió access_token tras ~$((MAX * 5))s" >&2
exit 1
