#!/usr/bin/env bash
# ENV-02 — Smoke API post-deploy (JWT / health / permisos) contra staging o cualquier URL.
# Equivalente liviano a Newman/RestAssured (curl + asserts).
# Uso (staging):
#   API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 ./scripts/post-deploy-smoke.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/final/ci"
API="${API_URL:-http://localhost:8088}"
KC="${KEYCLOAK_URL:-http://localhost:8181}"
REALM="${KEYCLOAK_REALM:-inventory}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-inventory-api}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-inventory-api-secret}"
CORS_ORIGIN="${CORS_ORIGIN:-http://localhost:3008}"

mkdir -p "$OUT"
EVID="$OUT/EVIDENCIA-POST-DEPLOY-SMOKE.md"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }

get_token() {
  local user="$1" pass="$2"
  local url="$KC/realms/$REALM/protocol/openid-connect/token"
  local tmp http body token
  tmp="$(mktemp)"
  http=$(curl -s -o "$tmp" -w "%{http_code}" -X POST "$url" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=$CLIENT_ID" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "username=$user" \
    -d "password=$pass" || echo "000")
  body="$(cat "$tmp")"
  rm -f "$tmp"
  token=$(printf '%s' "$body" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null || true)
  if [[ -z "$token" ]]; then
    red "No access_token para $user (HTTP $http)"
    printf '%s\n' "${body:-(vacío)}" | head -c 400 >&2
    echo >&2
    exit 1
  fi
  printf '%s' "$token"
}

http_code() {
  local method="$1" url="$2"
  shift 2
  curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" "$@"
}

bold "=== Post-deploy smoke (ENV-02) ==="
bold "API=$API  Keycloak=$KC"

CODE_HEALTH=$(http_code GET "$API/actuator/health")
CODE_NO_TOKEN=$(http_code GET "$API/api/v1/products")

TOKEN_VIEWER=$(get_token viewer viewer)
TOKEN_ADMIN=$(get_token admin admin)

CODE_VIEWER_LIST=$(http_code GET "$API/api/v1/products" -H "Authorization: Bearer $TOKEN_VIEWER")
CODE_VIEWER_CREATE=$(http_code POST "$API/api/v1/products" \
  -H "Authorization: Bearer $TOKEN_VIEWER" \
  -H "Content-Type: application/json" \
  -d '{"name":"pd","sku":"PD-SMOKE","price":1,"quantity":1,"minStock":0,"active":true}')
CODE_ADMIN_LIST=$(http_code GET "$API/api/v1/products" -H "Authorization: Bearer $TOKEN_ADMIN")
CODE_VIEWER_REPORTS=$(http_code GET "$API/api/v1/reports/inventory-summary" \
  -H "Authorization: Bearer $TOKEN_VIEWER")
CODE_ADMIN_REPORTS=$(http_code GET "$API/api/v1/reports/inventory-summary" \
  -H "Authorization: Bearer $TOKEN_ADMIN")

CORS_ALLOW=$(curl -s -D - -o /dev/null -X OPTIONS "$API/api/v1/products" \
  -H "Origin: $CORS_ORIGIN" \
  -H "Access-Control-Request-Method: GET" | tr -d '\r' | \
  awk -F': ' 'tolower($1)=="access-control-allow-origin"{print $2; exit}')

pass_fail() {
  if [[ "$1" == "1" ]]; then echo "| $2 | PASS |"
  else echo "| $2 | FAIL |"
  fi
}

{
  echo "# Evidencia post-deploy smoke (ENV-02)"
  echo
  echo "- **API:** \`$API\`"
  echo "- **Keycloak:** \`$KC\`"
  echo "- **Fecha:** $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## Resultados"
  echo
  echo "| Check | Resultado |"
  echo "|-------|-----------|"
  pass_fail "$([[ "$CODE_HEALTH" == "200" ]] && echo 1 || echo 0)" "Health → $CODE_HEALTH (200)"
  pass_fail "$([[ "$CODE_NO_TOKEN" == "401" ]] && echo 1 || echo 0)" "Sin JWT GET /products → $CODE_NO_TOKEN (401)"
  pass_fail "$([[ "$CODE_VIEWER_LIST" == "200" ]] && echo 1 || echo 0)" "viewer GET /products → $CODE_VIEWER_LIST (200)"
  pass_fail "$([[ "$CODE_VIEWER_CREATE" == "403" ]] && echo 1 || echo 0)" "viewer POST /products → $CODE_VIEWER_CREATE (403)"
  pass_fail "$([[ "$CODE_ADMIN_LIST" == "200" ]] && echo 1 || echo 0)" "admin GET /products → $CODE_ADMIN_LIST (200)"
  pass_fail "$([[ "$CODE_VIEWER_REPORTS" == "403" ]] && echo 1 || echo 0)" "viewer reports → $CODE_VIEWER_REPORTS (403)"
  pass_fail "$([[ "$CODE_ADMIN_REPORTS" == "200" ]] && echo 1 || echo 0)" "admin reports → $CODE_ADMIN_REPORTS (200)"
  pass_fail "$([[ "$CORS_ALLOW" == "$CORS_ORIGIN" ]] && echo 1 || echo 0)" "CORS Allow-Origin=\`$CORS_ALLOW\` (esperado $CORS_ORIGIN)"
  echo
  echo "## Regenerar"
  echo
  echo '```bash'
  echo 'API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 ./scripts/post-deploy-smoke.sh'
  echo '```'
} > "$EVID"

FAILS=0
[[ "$CODE_HEALTH" == "200" ]] || FAILS=$((FAILS + 1))
[[ "$CODE_NO_TOKEN" == "401" ]] || FAILS=$((FAILS + 1))
[[ "$CODE_VIEWER_LIST" == "200" ]] || FAILS=$((FAILS + 1))
[[ "$CODE_VIEWER_CREATE" == "403" ]] || FAILS=$((FAILS + 1))
[[ "$CODE_ADMIN_LIST" == "200" ]] || FAILS=$((FAILS + 1))
[[ "$CORS_ALLOW" == "$CORS_ORIGIN" ]] || FAILS=$((FAILS + 1))

if [[ $FAILS -gt 0 ]]; then
  red "Post-deploy smoke: $FAILS check(s) fallaron → $EVID"
  exit 1
fi

green "Post-deploy smoke OK → $EVID"
