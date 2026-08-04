#!/usr/bin/env bash
# OBS — Genera tráfico autenticado (y 401/403) para llenar Grafana:
# métricas (Prometheus), logs (Loki) y trazas (Tempo vía Alloy/OTLP).
#
# Prerrequisito:
#   docker compose up -d postgres keycloak api alloy tempo loki prometheus grafana
#
# Uso:
#   ./scripts/generate-obs-traffic.sh
#   API_URL=http://localhost:8080 KEYCLOAK_URL=http://localhost:8081 ./scripts/generate-obs-traffic.sh
#
# Luego: http://localhost:3001 (admin/admin) → dashboard Observabilidad — Métricas, Logs y Trazas
# Espera 15–30 s y refresca (scrape Prometheus + export OTLP).
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

API_URL="${API_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
REALM="${KEYCLOAK_REALM:-inventory}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-inventory-api}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-inventory-api-secret}"
ROUNDS="${OBS_TRAFFIC_ROUNDS:-25}"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }

get_token() {
  local user="$1" pass="$2"
  local body
  body=$(curl -sS -X POST \
    "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=${CLIENT_ID}" \
    -d "client_secret=${CLIENT_SECRET}" \
    -d "username=${user}" \
    -d "password=${pass}")
  echo "$body" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
}

bold "1) Health API + Keycloak"
if ! curl -sf "${API_URL}/actuator/health" >/dev/null; then
  red "API no responde en ${API_URL}/actuator/health"
  yellow "Levanta: docker compose up -d postgres keycloak api alloy tempo loki prometheus grafana"
  exit 1
fi
green "API UP"

bold "2) Tokens admin + viewer"
ADMIN_TOKEN=$(get_token admin admin)
VIEWER_TOKEN=$(get_token viewer viewer)
green "Tokens OK"

bold "3) Tráfico 200 (${ROUNDS} rondas: products + reports)"
for i in $(seq 1 "$ROUNDS"); do
  curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${API_URL}/api/v1/products?page=0&size=10" >/dev/null || true
  curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${API_URL}/api/v1/reports/inventory-summary" >/dev/null || true
  curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${API_URL}/api/v1/categories" >/dev/null || true
done
green "OK autenticado"

bold "4) 401 sin token (métricas seguridad / logs)"
for i in $(seq 1 8); do
  curl -s -o /dev/null -w "%{http_code}\n" "${API_URL}/api/v1/products" >/dev/null || true
done
green "401 generados"

bold "5) 403 viewer crea producto (sin product:manage)"
SKU="OBS-$(date +%s)"
for i in $(seq 1 5); do
  curl -s -o /dev/null \
    -H "Authorization: Bearer ${VIEWER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Obs traffic\",\"sku\":\"${SKU}-${i}\",\"price\":1.00,\"quantity\":1,\"minStock\":0,\"active\":true}" \
    "${API_URL}/api/v1/products" || true
done
green "403 generados"

bold "6) 404 autenticado (producto inexistente)"
for i in $(seq 1 3); do
  curl -s -o /dev/null \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${API_URL}/api/v1/products/999999${i}" || true
done
green "404 generados"

echo
green "Listo. Espera 15–30 s y abre Grafana:"
echo "  http://localhost:3001  (admin / admin)"
echo "  Dashboard: Observabilidad — Métricas, Logs y Trazas"
echo "  Prometheus targets: http://localhost:9090/targets"
echo
yellow "Si Tempo/Loki siguen vacíos: confirma que alloy/tempo/loki están UP"
echo "  docker compose ps api alloy tempo loki prometheus grafana"
