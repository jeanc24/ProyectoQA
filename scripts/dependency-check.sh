#!/usr/bin/env bash
# TEST-03 — OWASP Dependency-Check (SCA).
#
# Uso rápido (recomendado en laptop):
#   ./scripts/dependency-check.sh
#   → NO re-descarga la NVD (autoUpdate=false). Suele tardar minutos.
#
# Sync completa NVD (lento, ~1–2 GB, 30–90 min; mejor en CI o de noche):
#   DEPENDENCY_CHECK_AUTO_UPDATE=true ./scripts/dependency-check.sh
#
# API key (opcional pero útil): https://nvd.nist.gov/developers/request-an-api-key
#   Puede vivir en .env como NVD_API_KEY=...
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/docs/final/testing/dependency-check"
mkdir -p "$OUT"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
red() { printf '\033[0;31m%s\033[0m\n' "$*"; }

# Cargar .env si existe (sin imprimir secretos)
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

# Por defecto: modo rápido local
export DEPENDENCY_CHECK_AUTO_UPDATE="${DEPENDENCY_CHECK_AUTO_UPDATE:-false}"

bold "=== OWASP Dependency-Check (TEST-03) ==="
echo "autoUpdate=$DEPENDENCY_CHECK_AUTO_UPDATE"

AVAIL_KB=$(df -k "$HOME" | awk 'NR==2{print $4}')
AVAIL_GB=$((AVAIL_KB / 1024 / 1024))
echo "Espacio libre (home): ~${AVAIL_GB} GB"

if [[ "$DEPENDENCY_CHECK_AUTO_UPDATE" == "true" && "$AVAIL_GB" -lt 8 ]]; then
  red "Sync NVD necesita ≥8 GB libres (tienes ~${AVAIL_GB} GB). Usa modo rápido o libera disco."
  yellow "Modo rápido: DEPENDENCY_CHECK_AUTO_UPDATE=false ./scripts/dependency-check.sh"
  exit 1
fi

if [[ -z "${NVD_API_KEY:-}" ]]; then
  yellow "Sin NVD_API_KEY (ok en modo rápido; recomendada para sync completa)."
else
  echo "NVD_API_KEY: presente"
fi

./gradlew dependencyCheckAnalyze --no-daemon || true

REPORT_HTML="$OUT/dependency-check-report.html"
if [[ ! -f "$REPORT_HTML" ]]; then
  yellow "No se generó HTML. Prueba modo rápido o revisa logs de Gradle."
  cat > "$OUT/README.md" <<EOF
# OWASP Dependency-Check — evidencias (TEST-03)

- **Fecha:** $(date -u +%Y-%m-%dT%H:%M:%SZ)
- **Estado:** sin reporte HTML
- **Modo rápido:** \`./scripts/dependency-check.sh\` (autoUpdate=false)
- **Sync NVD completa:** \`DEPENDENCY_CHECK_AUTO_UPDATE=true ./scripts/dependency-check.sh\` (≥8 GB libres)
EOF
  exit 1
fi

cat > "$OUT/README.md" <<EOF
# OWASP Dependency-Check — evidencias (TEST-03)

- **Fecha:** $(date -u +%Y-%m-%dT%H:%M:%SZ)
- **autoUpdate:** \`$DEPENDENCY_CHECK_AUTO_UPDATE\`
- **Reporte HTML:** \`dependency-check-report.html\`
- **Reporte JSON:** \`dependency-check-report.json\`

## Cómo regenerar

\`\`\`bash
# Rápido (local)
./scripts/dependency-check.sh

# Completo NVD (CI / máquina con ≥8–10 GB libres)
DEPENDENCY_CHECK_AUTO_UPDATE=true ./scripts/dependency-check.sh
\`\`\`
EOF

green "Dependency-Check OK. Abrir $REPORT_HTML"
