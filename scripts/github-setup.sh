#!/usr/bin/env bash
# Crea labels, milestone e issues del sprint Avance 16-Jun en GitHub.
# Requisitos: gh CLI instalado y autenticado (gh auth login)
set -euo pipefail

REPO="${GITHUB_REPO:-jeanc24/ProyectoQA}"
MILESTONE_TITLE="Avance 16-Jun 2026"
MILESTONE_DUE="2026-06-16"

if ! command -v gh >/dev/null 2>&1; then
  echo "❌ GitHub CLI (gh) no está instalado."
  echo "   Instalar: https://cli.github.com/"
  echo "   Luego: gh auth login"
  exit 1
fi

echo "→ Repositorio: $REPO"

create_label() {
  local name="$1"
  local color="$2"
  local description="$3"
  gh label create "$name" --repo "$REPO" --color "$color" --description "$description" --force
}

echo "→ Creando labels..."
create_label "enhancement"   "0e8a16" "Nueva funcionalidad"
create_label "bug"           "d73a4a" "Algo no funciona"
create_label "documentation" "0075ca" "Solo documentación"
create_label "testing"       "7057ff" "Unit, integration, E2E"
create_label "infra"         "f9d0c4" "Docker, compose, CI base"
create_label "security"      "e99695" "Keycloak, OAuth2, permisos"
create_label "avance-1"      "1d76db" "Sprint entrega 16 jun 2026"

echo "→ Creando milestone..."
MILESTONE_NUMBER=$(gh api repos/"$REPO"/milestones \
  --method POST \
  -f title="$MILESTONE_TITLE" \
  -f due_on="${MILESTONE_DUE}T23:59:59Z" \
  -f description="Entrega avance PDF — checklist completo" \
  --jq '.number' 2>/dev/null || true)

if [ -z "${MILESTONE_NUMBER:-}" ]; then
  MILESTONE_NUMBER=$(gh api "repos/$REPO/milestones" --jq ".[] | select(.title==\"$MILESTONE_TITLE\") | .number" | head -1)
fi

echo "   Milestone #$MILESTONE_NUMBER — $MILESTONE_TITLE"

create_issue() {
  local title="$1"
  local body="$2"
  local labels="$3"
  local assignee="${4:-}"

  local args=(
    issue create
    --repo "$REPO"
    --title "$title"
    --body "$body"
    --milestone "$MILESTONE_NUMBER"
    --label "$labels"
  )

  if [ -n "$assignee" ]; then
    args+=(--assignee "$assignee")
  fi

  gh "${args[@]}"
}

echo "→ Creando issues del sprint..."

# Nota: ajustar --assignee a los usernames de GitHub de Jean y Emilio
JEAN="${JEAN_GH_USER:-jeanc24}"
EMILIO="${EMILIO_GH_USER:-}"

create_issue \
  "[INFRA] Docker Compose: postgres + Keycloak" \
  "**Área:** Infra / Docker
**Asignado:** Jean
**PDF:** Docker, Base de datos, Seguridad

**Criterios:**
- [ ] \`infra/docker-compose.yml\` con postgres:16 y keycloak:26
- [ ] Healthcheck en postgres
- [ ] \`infra/.env.example\`
- [ ] \`docker compose up -d\` sin errores

**Commit:** \`feat(infra): add postgres and keycloak services\`" \
  "infra,avance-1" \
  "$JEAN"

create_issue \
  "[API] CRUD Producto con validaciones de negocio" \
  "**Área:** Backend / API
**Asignado:** Jean
**PDF:** Funcionalidad, API REST

**Criterios:**
- [ ] Repository, Service, Controller, DTOs
- [ ] SKU único, paginación, filtros
- [ ] GlobalExceptionHandler (400, 404, 409)
- [ ] Perfil \`local\` sin auth para desarrollo

**Commit:** \`feat(api): add product CRUD with validations\`" \
  "enhancement,avance-1" \
  "$JEAN"

create_issue \
  "[FRONTEND] Skeleton React + README" \
  "**Área:** Frontend
**Asignado:** Emilio
**PDF:** GitHub

**Criterios:**
- [ ] Vite + React + TS en \`frontend/\`
- [ ] Rutas /login y /products
- [ ] README con instalación y puertos

**Commit:** \`chore: init frontend and expand README\`" \
  "enhancement,documentation,avance-1" \
  "$EMILIO"

create_issue \
  "[SECURITY] Keycloak realm + OAuth2 JWT + permisos" \
  "**Área:** Seguridad
**Asignado:** Jean
**PDF:** Seguridad

**Criterios:**
- [ ] realm-export.json (admin, viewer; product:view, product:manage)
- [ ] Spring Security OAuth2 Resource Server
- [ ] @PreAuthorize en endpoints
- [ ] 403 sin permiso, 201 con permiso

**Commit:** \`feat(security): enforce JWT permissions on product endpoints\`" \
  "security,avance-1" \
  "$JEAN"

create_issue \
  "[TEST] 15+ unit tests + JaCoCo 60%" \
  "**Área:** Testing
**Asignado:** Jean
**PDF:** Testing

**Criterios:**
- [ ] ProductServiceTest (10+ casos)
- [ ] JaCoCo en build.gradle
- [ ] \`./gradlew test\` verde, coverage >= 60%

**Commit:** \`test: add product unit tests with jacoco\`" \
  "testing,avance-1" \
  "$JEAN"

create_issue \
  "[TEST] Testcontainers + 5 integration + 10 API scenarios" \
  "**Área:** Testing
**Asignado:** Jean
**PDF:** Integration Testing, API Testing

**Criterios:**
- [ ] AbstractIntegrationTest + PostgreSQLContainer
- [ ] 5+ integration tests
- [ ] 10+ escenarios API (errores + permisos)

**Commit:** \`test: add integration tests with testcontainers\`" \
  "testing,avance-1" \
  "$JEAN"

create_issue \
  "[CI] GitHub Actions: build + unit + integration" \
  "**Área:** CI/CD
**Asignado:** Emilio
**PDF:** GitHub Actions

**Criterios:**
- [ ] \`.github/workflows/ci.yml\`
- [ ] Pipeline verde en push a develop

**Commit:** \`ci: add build unit and integration pipeline\`" \
  "infra,avance-1" \
  "$EMILIO"

create_issue \
  "[E2E] Playwright: login + CRUD producto" \
  "**Área:** Testing
**Asignado:** Emilio
**PDF:** Playwright

**Criterios:**
- [ ] login.spec.ts con admin
- [ ] products.spec.ts crear producto
- [ ] \`npm run test:e2e\` verde

**Commit:** \`test(e2e): add playwright login and product crud\`" \
  "testing,avance-1" \
  "$EMILIO"

create_issue \
  "[OBS] Grafana + Prometheus dashboard operativo" \
  "**Área:** Observabilidad
**Asignado:** Emilio
**PDF:** Observabilidad

**Criterios:**
- [ ] Servicios en compose
- [ ] Scrape /actuator/prometheus
- [ ] 1 dashboard con métricas HTTP/JVM

**Commit:** \`feat(obs): add prometheus and grafana with ops dashboard\`" \
  "infra,avance-1" \
  "$EMILIO"

create_issue \
  "[CI] Jenkins pipeline funcional" \
  "**Área:** CI/CD
**Asignado:** Jean
**PDF:** Jenkins

**Criterios:**
- [ ] \`infra/jenkins/Jenkinsfile\`
- [ ] Al menos 1 ejecución con screenshot

**Commit:** \`ci: add jenkins pipeline\`" \
  "infra,avance-1" \
  "$JEAN"

create_issue \
  "[CHORE] Conventional Commits + plantillas GitHub" \
  "**Área:** Documentación
**Asignado:** Ambos
**PDF:** GitHub

**Criterios:**
- [x] CONTRIBUTING.md
- [x] Issue templates + PR template
- [x] Hook commit-msg
- [ ] Rama develop creada
- [ ] Branch protection en main

**Commit:** \`chore: add conventional commit hook and issue templates\`" \
  "documentation,avance-1"

echo ""
echo "✅ Setup completado."
echo "   Activar hook local: git config core.hooksPath .githooks && chmod +x .githooks/commit-msg"
echo "   Crear rama develop:  git checkout -b develop && git push -u origin develop"
echo ""
echo "   Si Emilio tiene otro username GitHub, ejecutar:"
echo "   EMILIO_GH_USER=emilio-gh ./scripts/github-setup.sh"
