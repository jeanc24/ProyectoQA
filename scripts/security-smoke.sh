#!/usr/bin/env bash
# TEST-03 — Evidencia JWT / CORS / permisos contra API en :8080 (perfil docker).
# Uso (stack arriba): ./scripts/security-smoke.sh
# Escribe: docs/final/testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/final/testing/zap"
API="${API_URL:-http://localhost:8080}"
KC="${KEYCLOAK_URL:-http://localhost:8081}"
REALM="${KEYCLOAK_REALM:-inventory}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-inventory-api}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-inventory-api-secret}"

mkdir -p "$OUT"
EVID="$OUT/EVIDENCIA-JWT-CORS-PERMISOS.md"

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
    red "No access_token para $user (HTTP $http) en $url"
    printf '%s\n' "${body:-(respuesta vacía)}" | head -c 500 >&2
    echo >&2
    exit 1
  fi
  printf '%s' "$token"
}

http_code() {
  # args: method url [extra curl args...]
  local method="$1" url="$2"
  shift 2
  curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" "$@"
}

bold "=== Security smoke JWT/CORS/permisos (TEST-03) ==="

if ! curl -sf "$API/actuator/health" >/dev/null; then
  red "API no responde en $API/actuator/health — levanta el stack primero."
  exit 1
fi

CODE_NO_TOKEN=$(http_code GET "$API/api/v1/products")
CODE_HEALTH=$(http_code GET "$API/actuator/health")

TOKEN_VIEWER=$(get_token viewer viewer)
TOKEN_ADMIN=$(get_token admin admin)

CODE_VIEWER_LIST=$(http_code GET "$API/api/v1/products" -H "Authorization: Bearer $TOKEN_VIEWER")
CODE_VIEWER_CREATE=$(http_code POST "$API/api/v1/products" \
  -H "Authorization: Bearer $TOKEN_VIEWER" \
  -H "Content-Type: application/json" \
  -d '{"name":"x","sku":"SEC-SMOKE","price":1,"quantity":1,"minStock":0,"active":true}')
CODE_ADMIN_REPORTS=$(http_code GET "$API/api/v1/reports/inventory-summary" \
  -H "Authorization: Bearer $TOKEN_ADMIN")
CODE_VIEWER_REPORTS=$(http_code GET "$API/api/v1/reports/inventory-summary" \
  -H "Authorization: Bearer $TOKEN_VIEWER")
CODE_ADMIN_AUDIT=$(http_code GET "$API/api/v1/audit/products/1" \
  -H "Authorization: Bearer $TOKEN_ADMIN")
CODE_VIEWER_AUDIT=$(http_code GET "$API/api/v1/audit/products/1" \
  -H "Authorization: Bearer $TOKEN_VIEWER")

# CORS: origen permitido vs no permitido
CORS_OK=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$API/api/v1/products" \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: GET")
CORS_ALLOW_ORIGIN=$(curl -s -D - -o /dev/null -X OPTIONS "$API/api/v1/products" \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: GET" | tr -d '\r' | awk -F': ' 'tolower($1)=="access-control-allow-origin"{print $2; exit}')
CORS_BAD_ORIGIN_HDR=$(curl -s -D - -o /dev/null -X OPTIONS "$API/api/v1/products" \
  -H "Origin: http://evil.example" \
  -H "Access-Control-Request-Method: GET" | tr -d '\r' | awk -F': ' 'tolower($1)=="access-control-allow-origin"{print $2; exit}')

pass_fail() {
  local ok="$1" label="$2"
  if [[ "$ok" == "1" ]]; then echo "| $label | PASS |"
  else echo "| $label | FAIL |"
  fi
}

{
  echo "# Evidencia JWT / CORS / permisos (TEST-03)"
  echo
  echo "- **API:** \`$API\`"
  echo "- **Keycloak:** \`$KC\`"
  echo "- **Fecha:** $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "## Resultados"
  echo
  echo "| Check | Resultado |"
  echo "|-------|-----------|"
  pass_fail "$([[ "$CODE_HEALTH" == "200" ]] && echo 1 || echo 0)" "Health público → $CODE_HEALTH (esperado 200)"
  pass_fail "$([[ "$CODE_NO_TOKEN" == "401" ]] && echo 1 || echo 0)" "Sin JWT GET /products → $CODE_NO_TOKEN (esperado 401)"
  pass_fail "$([[ "$CODE_VIEWER_LIST" == "200" ]] && echo 1 || echo 0)" "viewer GET /products → $CODE_VIEWER_LIST (esperado 200)"
  pass_fail "$([[ "$CODE_VIEWER_CREATE" == "403" ]] && echo 1 || echo 0)" "viewer POST /products → $CODE_VIEWER_CREATE (esperado 403)"
  pass_fail "$([[ "$CODE_ADMIN_REPORTS" == "200" ]] && echo 1 || echo 0)" "admin GET /reports/inventory-summary → $CODE_ADMIN_REPORTS (esperado 200)"
  pass_fail "$([[ "$CODE_VIEWER_REPORTS" == "403" ]] && echo 1 || echo 0)" "viewer GET /reports → $CODE_VIEWER_REPORTS (esperado 403)"
  pass_fail "$([[ "$CODE_ADMIN_AUDIT" == "200" || "$CODE_ADMIN_AUDIT" == "404" ]] && echo 1 || echo 0)" "admin GET /audit/products/1 → $CODE_ADMIN_AUDIT (200 o 404 si no existe)"
  pass_fail "$([[ "$CODE_VIEWER_AUDIT" == "403" ]] && echo 1 || echo 0)" "viewer GET /audit/products/1 → $CODE_VIEWER_AUDIT (esperado 403)"
  pass_fail "$([[ "$CORS_OK" == "200" || "$CORS_OK" == "204" ]] && echo 1 || echo 0)" "CORS preflight Origin localhost:3000 → HTTP $CORS_OK"
  pass_fail "$([[ "$CORS_ALLOW_ORIGIN" == "http://localhost:3000" ]] && echo 1 || echo 0)" "Access-Control-Allow-Origin = \`$CORS_ALLOW_ORIGIN\` (esperado http://localhost:3000)"
  pass_fail "$([[ -z "$CORS_BAD_ORIGIN_HDR" ]] && echo 1 || echo 0)" "Origen evil.example sin Allow-Origin (valor=\`${CORS_BAD_ORIGIN_HDR:-vacío}\`)"
  echo
  echo "## Cómo regenerar"
  echo
  echo '```bash'
  echo 'docker compose up -d --build postgres keycloak tempo loki alloy api'
  echo './scripts/security-smoke.sh'
  echo '```'
} > "$EVID"

# Fallar si checks críticos fallan
FAILS=0
[[ "$CODE_NO_TOKEN" == "401" ]] || FAILS=$((FAILS+1))
[[ "$CODE_VIEWER_LIST" == "200" ]] || FAILS=$((FAILS+1))
[[ "$CODE_VIEWER_CREATE" == "403" ]] || FAILS=$((FAILS+1))
[[ "$CORS_ALLOW_ORIGIN" == "http://localhost:3000" ]] || FAILS=$((FAILS+1))

if [[ $FAILS -gt 0 ]]; then
  red "Security smoke: $FAILS check(s) fallaron. Ver $EVID"
  exit 1
fi

green "Security smoke OK → $EVID"
