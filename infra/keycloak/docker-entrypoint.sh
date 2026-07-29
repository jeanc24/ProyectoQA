#!/bin/sh
# Render free (512MB): heap bajo + cache local + Postgres + start --optimized.
set -eu

PORT="${PORT:-8080}"
export KC_HTTP_PORT="$PORT"
export KC_HTTP_HOST="${KC_HTTP_HOST:-0.0.0.0}"

# Presupuesto ~512MB: Liquibase del 1er boot necesita Metaspace alto.
# MaxMetaspaceSize=96m → OutOfMemoryError: Metaspace a mitad del schema.
# Heap 128m deja ~380m para metaspace/native/code-cache.
export JAVA_OPTS_KC_HEAP="${JAVA_OPTS_KC_HEAP:--Xms32m -Xmx128m}"
# No tocar el GC (Keycloak ya usa G1). Metaspace generoso para el primer liquibase.
export JAVA_OPTS_APPEND="${JAVA_OPTS_APPEND:--XX:MaxMetaspaceSize=168m -XX:MetaspaceSize=96m}"

# Single-node. No limitar caches de sessions si persistent-user-sessions está off.
export KC_CACHE="${KC_CACHE:-local}"
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
