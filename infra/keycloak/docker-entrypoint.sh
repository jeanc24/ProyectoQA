#!/bin/sh
# Render free (512MB): heap bajo + cache local + Postgres + start --optimized.
set -eu

PORT="${PORT:-8080}"
export KC_HTTP_PORT="$PORT"
export KC_HTTP_HOST="${KC_HTTP_HOST:-0.0.0.0}"

# En 512MB el non-heap (~150–250MB) come mucho: heap alto (384m) → OOM al login.
# Dejar ~300MB libres fuera del heap.
export JAVA_OPTS_KC_HEAP="${JAVA_OPTS_KC_HEAP:--Xms32m -Xmx192m -XX:MaxRAMPercentage=40 -XX:InitialRAMPercentage=15}"
export JAVA_OPTS_APPEND="${JAVA_OPTS_APPEND:--XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m}"

# Caches embebidos pequeños (single-node demo).
export KC_CACHE="${KC_CACHE:-local}"
export KC_CACHE_EMBEDDED_SESSIONS_MAX_COUNT="${KC_CACHE_EMBEDDED_SESSIONS_MAX_COUNT:-100}"
export KC_CACHE_EMBEDDED_CLIENT_SESSIONS_MAX_COUNT="${KC_CACHE_EMBEDDED_CLIENT_SESSIONS_MAX_COUNT:-100}"
export KC_CACHE_EMBEDDED_OFFLINE_SESSIONS_MAX_COUNT="${KC_CACHE_EMBEDDED_OFFLINE_SESSIONS_MAX_COUNT:-50}"
export KC_CACHE_EMBEDDED_OFFLINE_CLIENT_SESSIONS_MAX_COUNT="${KC_CACHE_EMBEDDED_OFFLINE_CLIENT_SESSIONS_MAX_COUNT:-50}"
export KC_CACHE_EMBEDDED_USERS_MAX_COUNT="${KC_CACHE_EMBEDDED_USERS_MAX_COUNT:-100}"
export KC_CACHE_EMBEDDED_REALMS_MAX_COUNT="${KC_CACHE_EMBEDDED_REALMS_MAX_COUNT:-10}"

# Render Postgres → JDBC Keycloak
if [ -n "${DATABASE_URL:-}" ]; then
  export KC_DB="${KC_DB:-postgres}"
  raw="${DATABASE_URL%%\?*}"
  rest="${raw#*://}"
  userinfo="${rest%%@*}"
  hostpath="${rest#*@}"
  if [ "$userinfo" != "$rest" ]; then
    export KC_DB_USERNAME="${KC_DB_USERNAME:-${userinfo%%:*}}"
    export KC_DB_PASSWORD="${KC_DB_PASSWORD:-${userinfo#*:}}"
    export KC_DB_URL="${KC_DB_URL:-jdbc:postgresql://${hostpath}}"
  else
    export KC_DB_URL="${KC_DB_URL:-jdbc:postgresql://${rest}}"
  fi
fi

# Compat: KEYCLOAK_ADMIN* (deprecated) → KC_BOOTSTRAP_ADMIN*
if [ -n "${KEYCLOAK_ADMIN:-}" ] && [ -z "${KC_BOOTSTRAP_ADMIN_USERNAME:-}" ]; then
  export KC_BOOTSTRAP_ADMIN_USERNAME="$KEYCLOAK_ADMIN"
fi
if [ -n "${KEYCLOAK_ADMIN_PASSWORD:-}" ] && [ -z "${KC_BOOTSTRAP_ADMIN_PASSWORD:-}" ]; then
  export KC_BOOTSTRAP_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD"
fi

# start-dev: Compose/CI. Cloud: KC_START_CMD=start.
MODE="${KC_START_CMD:-start-dev}"

if [ "$MODE" = "start" ]; then
  # IGNORE_EXISTING evita reimport OVERWRITE (pico de RAM) en cada redeploy.
  exec /opt/keycloak/bin/kc.sh start --optimized --cache=local --import-realm \
    --spi-import--dir--strategy=IGNORE_EXISTING \
    --http-port="$PORT" \
    --http-host="$KC_HTTP_HOST"
fi

exec /opt/keycloak/bin/kc.sh start-dev --import-realm \
  --http-port="$PORT" \
  --http-host="$KC_HTTP_HOST"
