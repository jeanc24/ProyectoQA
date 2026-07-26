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
#
# Soft-fail (CI/Jenkins sin DB NVD aún):
#   DEPENDENCY_CHECK_SOFT_FAIL=true → si no hay HTML, avisa y exit 0 (smoke/ZAP siguen).
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
SOFT_FAIL="${DEPENDENCY_CHECK_SOFT_FAIL:-false}"

bold "=== OWASP Dependency-Check (TEST-03) ==="
echo "autoUpdate=$DEPENDENCY_CHECK_AUTO_UPDATE softFail=$SOFT_FAIL"

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

run_analyze() {
  ./gradlew dependencyCheckAnalyze --no-daemon || true
}

run_analyze

REPORT_HTML="$OUT/dependency-check-report.html"

# Sin DB NVD local, autoUpdate=false no genera HTML.
# Con soft-fail (Jenkins) no disparamos sync completa (30–90 min).
if [[ ! -f "$REPORT_HTML" && "$DEPENDENCY_CHECK_AUTO_UPDATE" != "true" && "$SOFT_FAIL" != "true" ]]; then
  yellow "Sin reporte HTML (¿DB NVD vacía?). Reintentando con DEPENDENCY_CHECK_AUTO_UPDATE=true…"
  export DEPENDENCY_CHECK_AUTO_UPDATE=true
  run_analyze
fi

if [[ ! -f "$REPORT_HTML" ]]; then
  yellow "No se generó HTML. Prueba sync NVD o revisa logs de Gradle."
  cat > "$OUT/README.md" <<EOF
# OWASP Dependency-Check — evidencias (TEST-03)

- **Fecha:** $(date -u +%Y-%m-%dT%H:%M:%SZ)
- **Estado:** sin reporte HTML
- **autoUpdate:** \`$DEPENDENCY_CHECK_AUTO_UPDATE\`
- **softFail:** \`$SOFT_FAIL\`
- **Modo rápido:** \`./scripts/dependency-check.sh\` (autoUpdate=false; requiere DB NVD previa)
- **Sync NVD completa:** \`DEPENDENCY_CHECK_AUTO_UPDATE=true ./scripts/dependency-check.sh\` (≥8 GB libres)
- **Tip Jenkins/CI:** credencial \`NVD_API_KEY\` + autoUpdate=true (paridad GHA)
EOF
  if [[ "$SOFT_FAIL" == "true" ]]; then
    yellow "DEPENDENCY_CHECK_SOFT_FAIL=true — se omite el gate de DC; continúa smoke/ZAP."
    exit 0
  fi
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
