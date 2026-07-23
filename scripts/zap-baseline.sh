#!/usr/bin/env bash
# TEST-03 — OWASP ZAP baseline scan contra la API (staging local :8080).
# Uso:
#   docker compose up -d --build postgres keycloak tempo loki alloy api
#   ./scripts/zap-baseline.sh
#   ./scripts/zap-baseline.sh http://host.docker.internal:8080   # macOS/Windows Docker
#
# Requisitos: Docker. Genera HTML/JSON/MD en docs/final/testing/zap/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/final/testing/zap"
TARGET="${1:-}"
ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable}"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

mkdir -p "$OUT"

# Default: Swagger UI (permitAll) para que el spider explore rutas documentadas.
# Override: ./scripts/zap-baseline.sh http://localhost:8080/actuator/health
if [[ -z "$TARGET" ]]; then
  if [[ "$(uname -s)" == "Linux" ]]; then
    TARGET="http://localhost:8080/swagger-ui.html"
  else
    TARGET="http://host.docker.internal:8080/swagger-ui.html"
  fi
fi

bold "=== OWASP ZAP baseline (TEST-03) ==="
echo "Target: $TARGET"
echo "Reports: $OUT"

if ! docker info >/dev/null 2>&1; then
  red "Docker no está en ejecución."
  exit 1
fi

# En Linux CI (--network host) localhost funciona.
# En Docker Desktop, si ZAP corre en contenedor y la API en el host, usar host.docker.internal.
DOCKER_EXTRA=()
if [[ "$(uname -s)" == "Linux" ]]; then
  DOCKER_EXTRA+=(--network host)
else
  DOCKER_EXTRA+=(--add-host=host.docker.internal:host-gateway)
  # Reescribir localhost → host.docker.internal en Docker Desktop
  TARGET="${TARGET//localhost/host.docker.internal}"
fi

# -I: no fallar el proceso por WARN (el HTML/JSON son la evidencia).
# Exit codes ZAP: 0 OK, 1 warnings, 2+ failures — documentamos en el reporte.
set +e
docker run --rm \
  "${DOCKER_EXTRA[@]}" \
  -v "$OUT:/zap/wrk:rw" \
  -t "$ZAP_IMAGE" \
  zap-baseline.py \
  -t "$TARGET" \
  -r zap-report.html \
  -J zap-report.json \
  -w zap-warnings.md \
  -I
ZAP_EXIT=$?
set -e

cat > "$OUT/README.md" <<EOF
# OWASP ZAP — evidencias (TEST-03)

- **Target:** \`$TARGET\`
- **Fecha:** $(date -u +%Y-%m-%dT%H:%M:%SZ)
- **Exit code ZAP:** \`$ZAP_EXIT\` (0=OK, 1=warnings, 2+=fail; se usa \`-I\` para no bloquear por WARN)
- **Reportes:** \`zap-report.html\`, \`zap-report.json\`, \`zap-warnings.md\`

## Cómo regenerar

\`\`\`bash
docker compose up -d --build postgres keycloak tempo loki alloy api
# esperar health: curl -sf http://localhost:8080/actuator/health
./scripts/zap-baseline.sh
\`\`\`
EOF

if [[ $ZAP_EXIT -gt 2 ]]; then
  red "ZAP falló con código $ZAP_EXIT"
  exit "$ZAP_EXIT"
fi

green "ZAP baseline OK (exit=$ZAP_EXIT). Ver $OUT/zap-report.html"
exit 0
