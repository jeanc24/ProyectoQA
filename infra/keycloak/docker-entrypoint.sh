#!/bin/sh
# Render / PaaS: PORT + Postgres URL → Keycloak 26.
set -eu

PORT="${PORT:-8080}"
export KC_HTTP_PORT="$PORT"
export KC_HTTP_HOST="${KC_HTTP_HOST:-0.0.0.0}"

# Heap acotado: exit 137 en free tier = OOM durante el arranque Quarkus.
export JAVA_OPTS_KC_HEAP="${JAVA_OPTS_KC_HEAP:--XX:MaxRAMPercentage=65 -Xms64m -Xmx384m}"

# Render Postgres: postgres://user:pass@host:port/db → JDBC Keycloak
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

# start-dev: Compose/CI local. Cloud Render: KC_START_CMD=start (+ imagen --optimized).
MODE="${KC_START_CMD:-start-dev}"

if [ "$MODE" = "start" ]; then
  exec /opt/keycloak/bin/kc.sh start --optimized --import-realm \
    --http-port="$PORT" \
    --http-host="$KC_HTTP_HOST"
fi

exec /opt/keycloak/bin/kc.sh start-dev --import-realm \
  --http-port="$PORT" \
  --http-host="$KC_HTTP_HOST"
