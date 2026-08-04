# Guía de pruebas — solo comandos

Chuleta para **correr y ver resultados**. Casos/defectos detallados: [`testing/README.md`](testing/README.md) · Oral: [`defensa/GUION-24H.md`](defensa/GUION-24H.md).

Todo desde la **raíz del repo** salvo que diga `cd frontend`.

---

## 0. Prerrequisito

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
./scripts/wait-for-stack.sh
curl -sf http://localhost:8080/actuator/health
```

---

## 1. Swagger + token JWT

| Qué | URL / comando |
| --- | ------------- |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |
| API | http://localhost:8080 |
| Keycloak | http://localhost:8081 |

```bash
# Obtener access_token (admin) e imprimirlo
TOKEN=$(curl -s -X POST http://localhost:8081/realms/inventory/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=inventory-api \
  -d client_secret=inventory-api-secret \
  -d username=admin \
  -d password=admin \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "$TOKEN"

# Probar API con el token
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/products?page=0&size=5"

# En Swagger: Authorize → Bearer <pegar TOKEN>
```

Viewer (para 403): cambia `username=viewer` `password=viewer`.

---

## 2. Tests Gradle (JUnit)

```bash
# Unitarios (~36)
./gradlew test
open build/reports/tests/test/index.html

# API scenarios MockMvc (~31)
./gradlew apiTest
open build/reports/tests/apiTest/index.html

# Contract OpenAPI (~2)
./gradlew contractTest
open build/reports/tests/contractTest/index.html

# Integration Testcontainers — necesita Docker (~26)
./gradlew integrationTest
open build/reports/tests/integrationTest/index.html

# Todos los JUnit de una vez
./gradlew test apiTest contractTest integrationTest
```

---

## 3. JaCoCo (cobertura HTML)

```bash
./gradlew test apiTest jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

XML (lo consume Sonar): `build/reports/jacoco/test/jacocoTestReport.xml`

---

## 4. SonarCloud (dashboard visual)

```bash
# Requiere SONAR_TOKEN en el entorno (mismo secret que CI)
export SONAR_TOKEN=...   # o ya exportado
./gradlew test jacocoTestReport sonar -Dsonar.qualityGate.wait=true
```

| Qué | URL |
| --- | --- |
| Dashboard proyecto | https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA |
| Quality gate / badges | README del repo (badges SonarCloud) |

Sin token local: mira el análisis del último run en **GitHub → Actions → DevSecOps**.

---

## 5. Playwright (E2E)

```bash
# Stack FE+API+KC arriba (sección 0)
cd frontend
npm ci   # primera vez
npx playwright test --reporter=html --project=chromium --workers=1
npx playwright show-report
# → frontend/playwright-report/
cd ..
```

Con vars explícitas:

```bash
cd frontend
PLAYWRIGHT_BASE_URL=http://localhost:3000 \
API_BASE=http://localhost:8080 \
KEYCLOAK_URL=http://localhost:8081 \
  npx playwright test --reporter=html --project=chromium --workers=1
npx playwright show-report
cd ..
```

UI mode: `cd frontend && npx playwright test --ui`

---

## 6. k6 (performance)

```bash
# API + Keycloak arriba
./scripts/k6-run.sh load
./scripts/k6-run.sh stress
./scripts/k6-run.sh all
```

Resultados:

- `docs/final/testing/k6/load-products-summary.txt`
- `docs/final/testing/k6/stress-products-summary.txt`
- `docs/final/testing/k6/*-summary.json`

---

## 7. Security smoke / ZAP / Dependency-Check

```bash
# JWT / CORS / permisos (evidencia markdown)
./scripts/security-smoke.sh
# → docs/final/testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md

# OWASP ZAP baseline (DAST)
./scripts/zap-baseline.sh
open docs/final/testing/zap/zap-report.html

# OWASP Dependency-Check (SCA)
./scripts/dependency-check.sh
# → docs/final/testing/dependency-check/
```

---

## 8. Post-deploy smoke (staging Compose)

```bash
cp .env.staging.example .env.staging
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build \
  postgres keycloak tempo loki alloy api frontend

API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 FRONTEND_URL=http://localhost:3008 \
  ./scripts/wait-for-stack.sh

API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 \
  ./scripts/post-deploy-smoke.sh
# → docs/final/ci/EVIDENCIA-POST-DEPLOY-SMOKE.md

cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3008 API_BASE=http://localhost:8088 \
  KEYCLOAK_URL=http://localhost:8181 \
  npx playwright test e2e/helpers/login.spec.ts e2e/permissions.spec.ts --project=chromium
cd ..
```

---

## 9. Observabilidad (Grafana)

```bash
docker compose up -d postgres keycloak api alloy tempo loki prometheus grafana
./scripts/generate-obs-traffic.sh
```

| Qué | URL |
| --- | --- |
| Grafana | http://localhost:3001 (`admin` / `admin`) |
| Prometheus targets | http://localhost:9090/targets |
| Alertmanager | http://localhost:9093 |
| Health / métricas | `curl -sf localhost:8080/actuator/health` · `/actuator/prometheus` |

Dashboard demo: **Observabilidad — Métricas, Logs y Trazas** (traza `/api/v1/products` → waterfall JDBC).

---

## 10. URLs rápidas (stack local)

| Servicio | URL |
| -------- | --- |
| Frontend | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8081 |
| Grafana | http://localhost:3001 |
| Prometheus | http://localhost:9090 |
| Jenkins | http://localhost:8082 |
| SonarCloud | https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA |

---

## 11. Correr casi todo (local)

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
./scripts/wait-for-stack.sh

./gradlew test apiTest contractTest integrationTest jacocoTestReport
open build/reports/jacoco/test/html/index.html

# Sonar (si tienes SONAR_TOKEN)
# ./gradlew sonar -Dsonar.qualityGate.wait=true

./scripts/security-smoke.sh
./scripts/zap-baseline.sh
./scripts/dependency-check.sh

cd frontend && npx playwright test --reporter=html --project=chromium --workers=1 && npx playwright show-report && cd ..

./scripts/k6-run.sh load
./scripts/generate-obs-traffic.sh
```

CI oficial: GitHub → Actions → **DevSecOps Pipeline**.
