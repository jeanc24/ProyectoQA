#!/usr/bin/env bash
# CICD-02 — docker compose (staging) desde Jenkins-en-Docker.
#
# Los bind mounts relativos (./infra/..., ./keycloak) fallan porque el daemon
# del host no ve /var/jenkins_home/workspace/... . Este script reescribe esos
# mounts al path real del repo montado en /host-repo.
#
# Prerrequisito: servicio jenkins con volume ".:/host-repo:ro"
#
# Uso:
#   ./scripts/jenkins-host-compose.sh up -d --build postgres keycloak tempo loki alloy api frontend
#   ./scripts/jenkins-host-compose.sh down -v
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

HOST_SRC="$(docker inspect inventory-jenkins --format '{{range .Mounts}}{{if eq .Destination "/host-repo"}}{{.Source}}{{end}}{{end}}' 2>/dev/null || true)"

if [[ -z "${HOST_SRC}" ]]; then
  echo "ERROR: no hay mount /host-repo en inventory-jenkins."
  echo "Desde la raíz del repo: docker compose up -d --build jenkins"
  exit 1
fi

echo "REPO_HOST_PATH=${HOST_SRC}"

OVERRIDE="$(mktemp "${TMPDIR:-/tmp}/compose-staging-binds.XXXXXX.yml")"
cleanup() { rm -f "${OVERRIDE}"; }
trap cleanup EXIT

cat > "${OVERRIDE}" <<EOF
services:
  tempo:
    volumes: !override
      - ${HOST_SRC}/infra/tempo/tempo.yml:/etc/tempo.yml:ro
      - tempo_staging_data:/var/tempo
  loki:
    volumes: !override
      - ${HOST_SRC}/infra/loki/loki.yml:/etc/loki/loki.yml:ro
      - loki_staging_data:/loki
  alloy:
    volumes: !override
      - ${HOST_SRC}/infra/alloy/config.alloy:/etc/alloy/config.alloy:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - alloy_staging_data:/var/lib/alloy/data
  keycloak:
    volumes: !override
      - ${HOST_SRC}/keycloak:/opt/keycloak/data/import
EOF

docker compose \
  -f docker-compose.staging.yml \
  -f "${OVERRIDE}" \
  --env-file .env.staging \
  "$@"
