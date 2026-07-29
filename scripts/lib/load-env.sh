#!/usr/bin/env bash
# Rellena defaults de DEMO solo cuando la variable está vacía o no definida.
# No carga archivos .env (el caller lo hace antes si hace falta), para no pisar
# `.env.security` / `.env.staging` en Jenkins o CI.
#
# Fuente de verdad del secret demo: keycloak/inventory-realm.json + .env.example.
# Producción: definir KEYCLOAK_* / POSTGRES_* en el entorno o vault.
#
# Uso:
#   set -a; [[ -f .env ]] && source .env; set +a   # opcional
#   # shellcheck source=lib/load-env.sh
#   source "$(cd "$(dirname "$0")" && pwd)/lib/load-env.sh"

_demo_default() {
  local var="$1"
  local value="$2"
  if [[ -z "${!var:-}" ]]; then
    printf -v "$var" '%s' "$value"
    export "$var"
  fi
}

# --- Demo defaults (alinear con .env.example / inventory-realm.json) ---
_demo_default KEYCLOAK_REALM inventory
_demo_default KEYCLOAK_CLIENT_ID inventory-api
_demo_default KEYCLOAK_CLIENT_SECRET inventory-api-secret
_demo_default KEYCLOAK_ADMIN admin
_demo_default KEYCLOAK_ADMIN_PASSWORD admin
_demo_default POSTGRES_USER inventory
_demo_default POSTGRES_PASSWORD inventory
_demo_default POSTGRES_DB inventory
