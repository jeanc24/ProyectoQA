#!/bin/sh
# Render / PaaS entrypoint: map PLATFORM env → Spring Boot.
set -e

# Render (and most PaaS) inject PORT; Spring uses server.port / SERVER_PORT.
if [ -n "${PORT:-}" ]; then
  export SERVER_PORT="$PORT"
fi

# Render Postgres: postgres://user:pass@host:port/db → JDBC + username/password
if [ -n "${DATABASE_URL:-}" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
  # Strip query string if present
  raw="${DATABASE_URL%%\?*}"
  scheme_rest="${raw#*://}"
  userinfo="${scheme_rest%%@*}"
  hostpath="${scheme_rest#*@}"
  if [ "$userinfo" != "$scheme_rest" ]; then
    export SPRING_DATASOURCE_USERNAME="${userinfo%%:*}"
    export SPRING_DATASOURCE_PASSWORD="${userinfo#*:}"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${hostpath}"
  else
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${scheme_rest}"
  fi
fi

exec java -jar /app/app.jar
