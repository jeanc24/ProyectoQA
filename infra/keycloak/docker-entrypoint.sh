#!/bin/sh
# Render free (512MB). Receta alineada con Blueprint que ya funciona en el curso:
#   JAVA_OPTS_APPEND="-Xms64m -Xmx384m -XX:MaxMetaspaceSize=128m" (sin UseSerialGC, sin JAVA_OPTS_KC_HEAP)
set -eu

PORT="${PORT:-8080}"
export KC_HTTP_PORT="$PORT"
export KC_HTTP_HOST="${KC_HTTP_HOST:-0.0.0.0}"

# No fijar JAVA_OPTS_KC_HEAP: Keycloak calcula heap; APPEND fija tope como el Blueprint de referencia.
export JAVA_OPTS_APPEND="${JAVA_OPTS_APPEND:--Xms64m -Xmx384m -XX:MaxMetaspaceSize=128m}"

export KC_CACHE="${KC_CACHE:-local}"

# Render Postgres → JDBC (si el Blueprint ya inyecta KC_DB_USERNAME/PASSWORD, se respetan)
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

# Compat: KEYCLOAK_ADMIN* → KC_BOOTSTRAP_ADMIN*
if [ -n "${KEYCLOAK_ADMIN:-}" ] && [ -z "${KC_BOOTSTRAP_ADMIN_USERNAME:-}" ]; then
  export KC_BOOTSTRAP_ADMIN_USERNAME="$KEYCLOAK_ADMIN"
fi
if [ -n "${KEYCLOAK_ADMIN_PASSWORD:-}" ] && [ -z "${KC_BOOTSTRAP_ADMIN_PASSWORD:-}" ]; then
  export KC_BOOTSTRAP_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD"
fi

MODE="${KC_START_CMD:-start-dev}"

if [ "$MODE" = "start" ]; then
  exec /opt/keycloak/bin/kc.sh start --optimized --cache=local --import-realm \
    --spi-import--dir--strategy=IGNORE_EXISTING \
    --http-port="$PORT" \
    --http-host="$KC_HTTP_HOST"
fi

exec /opt/keycloak/bin/kc.sh start-dev --import-realm \
  --http-port="$PORT" \
  --http-host="$KC_HTTP_HOST"
