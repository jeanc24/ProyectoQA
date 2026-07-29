#!/bin/sh
set -eu

PORT="${PORT:-8080}"
export KC_HTTP_PORT="$PORT"

# start-dev: staging/demo. Prod cloud: KC_START_CMD=start
MODE="${KC_START_CMD:-start-dev}"

exec /opt/keycloak/bin/kc.sh "$MODE" --import-realm --http-port="$PORT"
