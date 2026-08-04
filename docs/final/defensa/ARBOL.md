# Herramientas — implementación y archivos

Formato de cada herramienta:

1. **Cómo se implementó** — qué hace y **en qué archivo(s) está el código**
2. **Archivos** — lista completa (implementación primero)
3. **Probar / resultados** — comando y dónde ver la salida (cuando aplica)

> [`README.md`](README.md) · [`GUION-24H.md`](GUION-24H.md) · [`ARCHIVOS.md`](ARCHIVOS.md) · [`RESUMEN-APARTADOS.md`](RESUMEN-APARTADOS.md) · [`PREGUNTAS.md`](PREGUNTAS.md) · Catálogo: [`../testing/README.md`](../testing/README.md) · Demo: [`GUIA-DEMO.md`](GUIA-DEMO.md)  
> Chuleta de todos los comandos: [§ 11](#11-comandos-probar-y-ver-resultados).

---

## Índice

1. [Seguridad](#1-seguridad) — Keycloak · OAuth2
2. [Test](#2-test) — Playwright · Testcontainers · k6 · JUnit · ZAP · Dependency-Check · Exploratorio
3. [QA](#3-qa--calidad) — JaCoCo · SonarCloud
4. [Backend](#4-backend-aplicación) — Spring Boot · JPA · Flyway · Envers · Swagger
5. [Frontend](#5-frontend) — React / Vite / TypeScript
6. [Datos](#6-datos) — PostgreSQL
7. [Infraestructura](#7-infraestructura) — Docker Compose · Cloud
8. [Observabilidad](#8-observabilidad)
9. [CI/CD](#9-cicd) — Pipelines · GitHub Actions · Jenkins
10. [Documentación](#10-documentación-de-defensa)
11. [Comandos: probar y ver resultados](#11-comandos-probar-y-ver-resultados)

---

## 1. Seguridad

### 1.1 Keycloak

**Cómo se implementó**

Keycloak es el IdP: usuarios y roles no están en Postgres. El realm se define como código en [`keycloak/inventory-realm.json`](../../../keycloak/inventory-realm.json) (clients `inventory-frontend` / `inventory-api`, 7 roles, usuarios demo). El contenedor lo importa al arrancar.

En el **frontend**, la integración está en:

- [`frontend/src/auth/keycloak.ts`](../../../frontend/src/auth/keycloak.ts) — crea el cliente `keycloak-js`
- [`frontend/src/auth/AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx) — `init` con `check-sso` + PKCE, login/logout, roles en UI
- [`frontend/src/auth/permissions.ts`](../../../frontend/src/auth/permissions.ts) — nombres de permisos iguales al realm

En el **backend**, la Admin API (listado de usuarios) está en:

- [`KeycloakAdminClient.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/KeycloakAdminClient.java)
- [`UserService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/UserService.java)
- [`UserController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/UserController.java)

La imagen/cloud: [`infra/keycloak/Dockerfile`](../../../infra/keycloak/Dockerfile) + [`infra/keycloak/docker-entrypoint.sh`](../../../infra/keycloak/docker-entrypoint.sh).

| Archivo | Rol |
|---|---|
| [`keycloak/inventory-realm.json`](../../../keycloak/inventory-realm.json) | **Implementación del realm** (roles, clients, users, redirects) |
| [`frontend/src/auth/keycloak.ts`](../../../frontend/src/auth/keycloak.ts) | **Implementación** adaptador JS |
| [`frontend/src/auth/AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx) | **Implementación** sesión SSO en React |
| [`frontend/src/auth/permissions.ts`](../../../frontend/src/auth/permissions.ts) | **Implementación** constantes de permisos |
| [`KeycloakAdminClient.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/KeycloakAdminClient.java) | **Implementación** Admin REST |
| [`UserService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/UserService.java) | Caso de uso users |
| [`UserController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/UserController.java) | Endpoint `GET /api/v1/users` |
| [`infra/keycloak/Dockerfile`](../../../infra/keycloak/Dockerfile) | Imagen KC 26 |
| [`infra/keycloak/docker-entrypoint.sh`](../../../infra/keycloak/docker-entrypoint.sh) | Arranque Render / JDBC |
| `docker-compose*.yml` (servicio `keycloak`) | Orquestación local/CI |
| [`AbstractKeycloakIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) | Tests con KC real |

**Probar / resultados**

```bash
# Stack local → login UI o token
docker compose up -d --build postgres keycloak api frontend
# UI: http://localhost:3000  (admin/admin)
# Consola KC: http://localhost:8081

# Generar access_token (password grant) y mostrarlo en la terminal:
TOKEN=$(curl -s -X POST http://localhost:8081/realms/inventory/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=inventory-api \
  -d client_secret=inventory-api-secret \
  -d username=admin \
  -d password=admin \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "$TOKEN"

# Usarlo contra la API:
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/products?page=0&size=5"
```

`TOKEN=$(curl …)` pide el JWT a Keycloak; `echo "$TOKEN"` lo imprime en la terminal (copiar a Swagger / jwt.io). Otros usuarios demo: `viewer`/`viewer`, `auditor`/`auditor`.

Resultados: JWT en stdout + JSON de productos / login en navegador. IT: `./gradlew integrationTest` (clase `KeycloakSecurityIntegrationTest`).

---

### 1.2 OAuth2 / Spring Security

**Cómo se implementó**

La API es un **OAuth2 Resource Server**: valida JWT de Keycloak sin sesión. Toda la cadena de seguridad está implementada en:

- [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) — perfiles `docker` / `staging` / `prod`: `oauth2ResourceServer().jwt()`, converter de roles Keycloak → authorities, rutas públicas, `@EnableMethodSecurity`
- [`SecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/SecurityConfig.java) — perfil `local` (sin JWT, para desarrollo)
- [`CorsConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/CorsConfig.java) — CORS desde `CORS_ORIGINS`

Los permisos por endpoint están en los controladores con `@PreAuthorize`, por ejemplo:

- [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java)
- [`StockController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/StockController.java)
- [`ReportController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ReportController.java)
- [`UserController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/UserController.java)

Issuer / JWKS se configuran en:

- [`application-docker.yml`](../../../src/main/resources/application-docker.yml)
- [`application-staging.yml`](../../../src/main/resources/application-staging.yml)
- [`application-prod.yml`](../../../src/main/resources/application-prod.yml)

En el frontend, el Bearer se añade en [`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts) (`keycloak.updateToken` + header `Authorization`).

| Archivo | Rol |
|---|---|
| [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) | **Implementación principal** Resource Server + JWT → roles |
| [`SecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/SecurityConfig.java) | **Implementación** perfil local abierto |
| [`CorsConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/CorsConfig.java) | **Implementación** CORS |
| Controladores (`ProductController`, etc.) | **Implementación** `@PreAuthorize` |
| [`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts) | **Implementación** envío del JWT |
| [`application-docker.yml`](../../../src/main/resources/application-docker.yml) (y staging/prod) | Config `issuer-uri` / `jwk-set-uri` |
| [`OpenApiConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java) | Bearer en Swagger |
| [`ApiTestSecurityConfig.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ApiTestSecurityConfig.java) | Security de tests MockMvc |
| [`KeycloakSecurityIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/KeycloakSecurityIntegrationTest.java) | Prueba 401/403/200 con JWT real |

**Probar / resultados**

```bash
./scripts/security-smoke.sh          # 401/403/CORS contra API+KC
./gradlew apiTest                    # MockMvc + authorities
# Sin token → 401:
curl -i http://localhost:8080/api/v1/products
```

---

## 2. Test

### 2.1 Playwright

**Cómo se implementó**

E2E en Chromium contra el frontend real. La **configuración** está en [`frontend/playwright.config.ts`](../../../frontend/playwright.config.ts) (`baseURL`, retries, traces).

Los **helpers de implementación** (login Keycloak por UI, token, alta de producto) están en [`frontend/e2e/helpers/auth.ts`](../../../frontend/e2e/helpers/auth.ts).

Los **casos** están en:

- [`frontend/e2e/helpers/login.spec.ts`](../../../frontend/e2e/helpers/login.spec.ts)
- [`frontend/e2e/helpers/products.spec.ts`](../../../frontend/e2e/helpers/products.spec.ts)
- [`frontend/e2e/permissions.spec.ts`](../../../frontend/e2e/permissions.spec.ts)
- [`frontend/e2e/stock.spec.ts`](../../../frontend/e2e/stock.spec.ts)
- [`frontend/e2e/dashboard.spec.ts`](../../../frontend/e2e/dashboard.spec.ts)

| Archivo | Rol |
|---|---|
| [`frontend/playwright.config.ts`](../../../frontend/playwright.config.ts) | **Implementación** config Playwright |
| [`frontend/e2e/helpers/auth.ts`](../../../frontend/e2e/helpers/auth.ts) | **Implementación** helpers de auth |
| `frontend/e2e/**/*.spec.ts` | **Implementación** de los escenarios E2E |
| [`docs/final/testing/e2e/evidencias/`](../testing/e2e/evidencias/) | Capturas |

**Probar / resultados**

```bash
# Prerrequisito: FE+API+KC (puertos 3000/8080/8081)
docker compose up -d --build postgres keycloak api frontend
./scripts/wait-for-stack.sh

cd frontend
rm -rf playwright-report test-results   # evita reporte HTML viejo
npx playwright test --reporter=html --project=chromium --workers=1
npx playwright show-report              # abre frontend/playwright-report/
```

Subset CI: `npx playwright test e2e/helpers/login.spec.ts e2e/permissions.spec.ts --project=chromium`.  
Fallos: `frontend/test-results/` (screenshot/video/trace).

---

### 2.2 Testcontainers

**Cómo se implementó**

Los integration tests levantan contenedores reales. La **base compartida** está en:

- [`AbstractIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java) — `PostgreSQLContainer` + `@DynamicPropertySource`
- [`AbstractKeycloakIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) — Keycloak 26 + import del realm + password grant

Los **tests concretos** extienden esas clases:

- [`ProductIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/ProductIntegrationTest.java)
- [`StockIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/StockIntegrationTest.java)
- [`ReportIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/ReportIntegrationTest.java)
- [`DataIntegrityIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/DataIntegrityIntegrationTest.java)
- [`KeycloakSecurityIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/KeycloakSecurityIntegrationTest.java)

Dependencias y task en [`build.gradle`](../../../build.gradle) (`testcontainers`, task `integrationTest`). Perfil: [`application-integration.yml`](../../../src/main/resources/application-integration.yml).

| Archivo | Rol |
|---|---|
| [`AbstractIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java) | **Implementación** Postgres Testcontainers |
| [`AbstractKeycloakIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) | **Implementación** Keycloak Testcontainers |
| `*IntegrationTest.java` (mismo paquete) | Casos de prueba |
| [`build.gradle`](../../../build.gradle) | Dependencias + task `integrationTest` |
| [`application-integration.yml`](../../../src/main/resources/application-integration.yml) | Perfil IT |

**Probar / resultados**

```bash
cp .env.example .env    # KEYCLOAK_CLIENT_* para IT Keycloak
./gradlew integrationTest
# HTML: build/reports/tests/integrationTest/index.html
```

Requiere Docker (Testcontainers).

---

### 2.3 k6

**Cómo se implementó**

Carga/estrés autenticados. Auth compartida en [`tests/k6/helpers.js`](../../../tests/k6/helpers.js). Escenarios en:

- [`tests/k6/load-products.js`](../../../tests/k6/load-products.js) — load (~15 VU)
- [`tests/k6/stress-products.js`](../../../tests/k6/stress-products.js) — stress (picos)

Se ejecutan con [`scripts/k6-run.sh`](../../../scripts/k6-run.sh) (k6 en Docker).

| Archivo | Rol |
|---|---|
| [`tests/k6/helpers.js`](../../../tests/k6/helpers.js) | **Implementación** token + headers |
| [`tests/k6/load-products.js`](../../../tests/k6/load-products.js) | **Implementación** load |
| [`tests/k6/stress-products.js`](../../../tests/k6/stress-products.js) | **Implementación** stress |
| [`scripts/k6-run.sh`](../../../scripts/k6-run.sh) | Runner |
| [`docs/final/testing/k6/`](../testing/k6/) | Evidencias |

**Probar / resultados**

```bash
docker compose up -d --build postgres keycloak api
./scripts/k6-run.sh load
./scripts/k6-run.sh stress
# o: ./scripts/k6-run.sh all
```

Resultados: `docs/final/testing/k6/load-products-summary.txt` · `stress-products-summary.txt` (+ `.json`).

---

### 2.4 JUnit 5 + Mockito

**Cómo se implementó**

Unitarios sin Docker: Mockito en servicios, MockMvc en controladores. Implementación en `src/test/java/.../`:

- Servicios: [`ProductServiceTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/ProductServiceTest.java), [`StockServiceTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/StockServiceTest.java), [`ReportServiceTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/ReportServiceTest.java), [`KeycloakAdminClientTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/KeycloakAdminClientTest.java)
- WebMvc: [`ProductControllerTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductControllerTest.java), [`GlobalExceptionHandlerTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/GlobalExceptionHandlerTest.java)
- API con `@WithMockUser`: `*ApiScenarioTest.java` + [`ApiTestSecurityConfig.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ApiTestSecurityConfig.java)
- Contrato: [`OpenApiContractTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java)

Tasks en [`build.gradle`](../../../build.gradle): `test`, `apiTest`, `contractTest`.

| Archivo | Rol |
|---|---|
| `src/test/java/.../service/*Test.java` | **Implementación** unitarios |
| `src/test/java/.../controller/*Test.java` / `*ApiScenarioTest.java` | **Implementación** WebMvc / API |
| [`OpenApiContractTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java) | Contrato OpenAPI |
| [`build.gradle`](../../../build.gradle) | Tags y tasks |

**Probar / resultados**

```bash
./gradlew test                         # unit → build/reports/tests/test/index.html
./gradlew apiTest                      # → build/reports/tests/apiTest/index.html
./gradlew contractTest                 # OpenApiContractTest (consola / reports)
```

---

### 2.5 OWASP ZAP

**Cómo se implementó**

DAST baseline vía [`scripts/zap-baseline.sh`](../../../scripts/zap-baseline.sh). En GitHub Actions se ejecuta contra servicios del [`docker-compose.yml`](../../../docker-compose.yml); Jenkins usa el stack mínimo [`docker-compose.security.yml`](../../../docker-compose.security.yml) para evitar conflictos de puertos y bind mounts. El smoke JWT/CORS vive en [`scripts/security-smoke.sh`](../../../scripts/security-smoke.sh).

| Archivo | Rol |
|---|---|
| [`scripts/zap-baseline.sh`](../../../scripts/zap-baseline.sh) | **Implementación** del scan ZAP |
| [`docker-compose.yml`](../../../docker-compose.yml) | Stack usado por ZAP en GitHub Actions |
| [`docker-compose.security.yml`](../../../docker-compose.security.yml) | Stack aislado usado por ZAP en Jenkins |
| [`scripts/security-smoke.sh`](../../../scripts/security-smoke.sh) | Smoke JWT/CORS |
| [`docs/final/testing/zap/`](../testing/zap/) | Reportes / evidencia |

**Probar / resultados**

```bash
docker compose up -d --build postgres keycloak api
./scripts/wait-for-stack.sh
./scripts/security-smoke.sh
./scripts/zap-baseline.sh http://localhost:8080/swagger-ui.html
```

Resultados: `docs/final/testing/zap/` (`zap-report.html`, JSON) · evidencia JWT: `docs/final/testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md`.

---

### 2.6 OWASP Dependency-Check

**Cómo se implementó**

SCA configurado en el bloque `dependencyCheck { ... }` de [`build.gradle`](../../../build.gradle) y ejecutado con [`scripts/dependency-check.sh`](../../../scripts/dependency-check.sh). Salida en `docs/final/testing/dependency-check/`.

| Archivo | Rol |
|---|---|
| [`build.gradle`](../../../build.gradle) (`dependencyCheck`) | **Implementación** / config del plugin |
| [`scripts/dependency-check.sh`](../../../scripts/dependency-check.sh) | Runner |
| [`docs/final/testing/dependency-check/`](../testing/dependency-check/) | Reportes |

**Probar / resultados**

```bash
./scripts/dependency-check.sh
# HTML: docs/final/testing/dependency-check/ (ver README de esa carpeta)
```

---

### 2.7 Pruebas exploratorias

**Cómo se implementó**

Charters manuales (no código automatizado). Viven en [`docs/final/testing/exploratory/`](../testing/exploratory/) (EX-01…03 + evidencias).

| Archivo | Rol |
|---|---|
| [`docs/final/testing/exploratory/`](../testing/exploratory/) | Charters + hallazgos |

**Probar / resultados**

```bash
docker compose up -d --build
# Seguir charters EC-01…03 en docs/final/testing/exploratory/
# Hallazgos: EC-01-*.md, EC-02-*.md, EC-03-*.md (incl. EXP-01 UX)
```

---

## 3. QA / calidad

### 3.1 JaCoCo

**Cómo se implementó**

Cobertura de tests configurada en [`build.gradle`](../../../build.gradle): plugin `jacoco`, bloques `jacoco { }`, `jacocoTestReport { }` y `jacocoTestCoverageVerification { }`. El XML que genera se consume por Sonar.

| Archivo | Rol |
|---|---|
| [`build.gradle`](../../../build.gradle) (líneas `jacoco*`) | **Implementación** JaCoCo en el build |
| `build/reports/jacoco/test/html/index.html` | Reporte generado (no versionado) |
| [`docs/avance-1/testing/jacoco-report.png`](../../avance-1/testing/jacoco-report.png) | Evidencia |

**Probar / resultados**

```bash
./gradlew test apiTest jacocoTestReport
open build/reports/jacoco/test/html/index.html    # macOS
```

---

### 3.2 SonarCloud

**Cómo se implementó**

Análisis + quality gate en [`build.gradle`](../../../build.gradle) (bloque `sonar { }` + task `sonar` que depende de JaCoCo) y [`sonar-project.properties`](../../../sonar-project.properties). En CI lo dispara [`.github/workflows/devsecops.yml`](../../../.github/workflows/devsecops.yml). Guía: [`docs/final/quality/SONARCLOUD.md`](../quality/SONARCLOUD.md).

| Archivo | Rol |
|---|---|
| [`build.gradle`](../../../build.gradle) (`sonar { }`) | **Implementación** plugin + cobertura |
| [`sonar-project.properties`](../../../sonar-project.properties) | Project key / rutas |
| [`.github/workflows/devsecops.yml`](../../../.github/workflows/devsecops.yml) | Ejecución en pipeline |
| [`docs/final/quality/SONARCLOUD.md`](../quality/SONARCLOUD.md) | Documentación |

**Probar / resultados**

```bash
export SONAR_TOKEN=...   # o secret en GHA/Jenkins
./gradlew test jacocoTestReport sonar -Dsonar.qualityGate.wait=true
```

Resultados: dashboard SonarCloud del proyecto + badge del README. En CI: job DevSecOps.

---

## 4. Backend (aplicación)

### 4.1 Spring Boot / Java

**Cómo se implementó**

Entrada en [`ProyectoQaApplication.java`](../../../src/main/java/icc354/pucmm/proyectoqa/ProyectoQaApplication.java). Capas:

- Controladores: `src/main/java/.../controller/`
- Servicios: `src/main/java/.../application/service/`
- Dominio: `src/main/java/.../domain/`

Dependencias y Java 21 en [`build.gradle`](../../../build.gradle). Perfiles en `src/main/resources/application*.yml`. Imagen: [`Dockerfile`](../../../Dockerfile) + [`scripts/docker-entrypoint-api.sh`](../../../scripts/docker-entrypoint-api.sh).

| Archivo | Rol |
|---|---|
| [`ProyectoQaApplication.java`](../../../src/main/java/icc354/pucmm/proyectoqa/ProyectoQaApplication.java) | **Implementación** bootstrap |
| `controller/**`, `application/service/**`, `domain/**` | **Implementación** de la API |
| [`build.gradle`](../../../build.gradle) | Dependencias Spring Boot 4 |
| `application*.yml` | Config por perfil |
| [`Dockerfile`](../../../Dockerfile) | Empaquetado |

---

### 4.2 Spring Data JPA / Hibernate

**Cómo se implementó**

Entidades en `domain/entity/` ([`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java), etc.). Repositorios en `domain/repository/` ([`ProductRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/ProductRepository.java), [`StockMovementRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/StockMovementRepository.java)). JPA en [`application.yml`](../../../src/main/resources/application.yml) (`ddl-auto: validate`).

| Archivo | Rol |
|---|---|
| `domain/entity/**` | **Implementación** entidades |
| `domain/repository/**` | **Implementación** acceso a datos |
| [`application.yml`](../../../src/main/resources/application.yml) | Config JPA |

---

### 4.3 Flyway

**Cómo se implementó**

Migraciones SQL en `src/main/resources/db/migration/`:

- [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql)
- [`V2__add_revinfo_sequence.sql`](../../../src/main/resources/db/migration/V2__add_revinfo_sequence.sql)
- [`V3__fix_revinfo_sequence_increment.sql`](../../../src/main/resources/db/migration/V3__fix_revinfo_sequence_increment.sql)

Flyway habilitado en [`application.yml`](../../../src/main/resources/application.yml) (`spring.flyway`).

| Archivo | Rol |
|---|---|
| `db/migration/V*.sql` | **Implementación** del esquema |
| [`application.yml`](../../../src/main/resources/application.yml) | Activa Flyway |

---

### 4.4 Hibernate Envers

**Cómo se implementó**

Auditoría de productos: `@Audited` en [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java). Lectura del historial en [`AuditService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) y exposición en [`AuditController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java).

| Archivo | Rol |
|---|---|
| [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) | **Implementación** `@Audited` |
| [`AuditService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) | **Implementación** lectura de revisiones |
| [`AuditController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java) | API de auditoría |

---

### 4.5 springdoc-openapi (Swagger)

**Cómo se implementó**

Esquema Bearer JWT en [`OpenApiConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java). Anotaciones OpenAPI en los controladores. En prod se apaga en [`application-prod.yml`](../../../src/main/resources/application-prod.yml).

| Archivo | Rol |
|---|---|
| [`OpenApiConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java) | **Implementación** config Swagger/JWT |
| Controladores | Anotaciones de operaciones |
| [`application-prod.yml`](../../../src/main/resources/application-prod.yml) | Swagger off en prod |

**Probar / resultados**

```bash
# Con API en perfil docker/staging (Swagger on):
open http://localhost:8080/swagger-ui.html
./gradlew contractTest    # valida paths documentados vs controllers
```

---

## 5. Frontend

### 5.1 React + TypeScript + Vite

**Cómo se implementó**

SPA montada en [`frontend/src/main.tsx`](../../../frontend/src/main.tsx). Rutas y guards en [`frontend/src/App.tsx`](../../../frontend/src/App.tsx) + [`ProtectedRoute.tsx`](../../../frontend/src/components/ProtectedRoute.tsx). Pantallas en `frontend/src/pages/` (`Products.tsx`, `Stock.tsx`, `Dashboard.tsx`, …). API en `frontend/src/api/`. Auth: ver [Keycloak](#11-keycloak). Build: [`vite.config.ts`](../../../frontend/vite.config.ts), empaquetado [`frontend/Dockerfile`](../../../frontend/Dockerfile) + [`nginx.conf`](../../../frontend/nginx.conf).

| Archivo | Rol |
|---|---|
| [`frontend/src/main.tsx`](../../../frontend/src/main.tsx) / [`App.tsx`](../../../frontend/src/App.tsx) | **Implementación** app y rutas |
| `frontend/src/pages/**` | **Implementación** UI |
| `frontend/src/api/**` | **Implementación** cliente HTTP |
| [`ProtectedRoute.tsx`](../../../frontend/src/components/ProtectedRoute.tsx) | Guarda por permiso |
| [`vite.config.ts`](../../../frontend/vite.config.ts) / [`package.json`](../../../frontend/package.json) | Build / scripts |
| [`frontend/Dockerfile`](../../../frontend/Dockerfile) | Imagen nginx |
| [`frontend/vercel.json`](../../../frontend/vercel.json) | Rewrites cloud |

**Probar / resultados**

```bash
docker compose up -d --build frontend   # http://localhost:3000
# o hot-reload: cd frontend && npm run dev  → http://localhost:5173
cd frontend && npm run test:e2e         # E2E (ver § 2.1)
```

---

## 6. Datos

### 6.1 PostgreSQL

**Cómo se implementó**

No hay código SQL de conexión a mano: Spring lee datasource de `application*.yml` / `DATABASE_URL` ([`scripts/docker-entrypoint-api.sh`](../../../scripts/docker-entrypoint-api.sh)). El esquema lo define Flyway ([§ 4.3](#43-flyway)). El servicio `postgres` está en los `docker-compose*.yml`.

| Archivo | Rol |
|---|---|
| `docker-compose*.yml` (servicio `postgres`) | Contenedor PG 16 |
| `application*.yml` + entrypoint API | Conexión |
| `db/migration/**` | Esquema |

---

## 7. Infraestructura

### 7.1 Docker / Compose

**Cómo se implementó**

Imágenes: [`Dockerfile`](../../../Dockerfile) (API), [`frontend/Dockerfile`](../../../frontend/Dockerfile), [`infra/keycloak/Dockerfile`](../../../infra/keycloak/Dockerfile).

Los Compose **no son** el hosting cloud (Render/Vercel). Orquestan stacks en laptop o en el runner CI. Detalle: [`ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md).

| Archivo | Para qué | Perfil Spring API | Quién lo usa |
|---|---|---|---|
| [`docker-compose.yml`](../../../docker-compose.yml) | **Desarrollo local**: app + Keycloak + Postgres + observabilidad (Prometheus, Grafana, Loki, Tempo, Alloy, Alertmanager) + Jenkins | `docker` | Laptop; job ZAP de GitHub Actions (`devsecops` / `security.yml`) |
| [`docker-compose.staging.yml`](../../../docker-compose.staging.yml) | **Staging efímero**: réplica staging (puertos 3008/8088/8181) para smoke + Playwright **post-deploy** | `staging` | Job `staging-deploy-e2e` (GHA); `post-deploy-staging.yml`; Jenkins (vía `jenkins-host-compose.sh`) |
| [`docker-compose.security.yml`](../../../docker-compose.security.yml) | **Security aislado**: solo Postgres + Keycloak + API (puertos 8090/8091), sin FE ni OBS | `docker` | Stage Security de Jenkins (smoke JWT/CORS + ZAP) |
| [`docker-compose.prod.yml`](../../../docker-compose.prod.yml) | **Simulación prod-like local** (Swagger off, Actuator mínimo). No es el deploy de producción | `prod` | Manual opcional; **no** lo usa ningún workflow activo ni Render |

| Archivo auxiliar | Rol |
|---|---|
| Los 3 Dockerfiles (API, FE, Keycloak) | **Implementación** de imágenes |
| [`.dockerignore`](../../../.dockerignore) | Contexto de build |
| [`.env.example`](../../../.env.example) / `.env.staging.example` / `.env.security.example` | Plantillas de variables por stack |

**Probar / resultados**

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
curl -sf http://localhost:8080/actuator/health

# Staging efímero
cp .env.staging.example .env.staging
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 FRONTEND_URL=http://localhost:3008 \
  ./scripts/wait-for-stack.sh
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 ./scripts/post-deploy-smoke.sh

# Security aislado
cp .env.security.example .env.security
docker compose -f docker-compose.security.yml --env-file .env.security up -d --build
```

---

### 7.2 Render / Vercel (cloud)

**Cómo se implementó**

Blueprints: [`render.yaml`](../../../render.yaml) (staging), [`infra/render/render.prod.yaml`](../../../infra/render/render.prod.yaml) (prod). Front en Vercel (`frontend/` + `vercel.json`). Deploys: [`.github/workflows/deploy-staging.yml`](../../../.github/workflows/deploy-staging.yml), [`deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml). Guía: [`CLOUD.md`](../ci/CLOUD.md).

| Archivo | Rol |
|---|---|
| [`render.yaml`](../../../render.yaml) | **Implementación** Blueprint staging |
| [`infra/render/render.prod.yaml`](../../../infra/render/render.prod.yaml) | **Implementación** Blueprint prod |
| [`frontend/vercel.json`](../../../frontend/vercel.json) | SPA en Vercel |
| `deploy-*.yml` | Triggers CI cloud |
| [`CLOUD.md`](../ci/CLOUD.md) | Documentación |

**Probar / resultados**

Push a `develop` / `main` o Actions → *Deploy staging/production (cloud)*. Smoke:

```bash
# Si tienes vars STAGING_* / PROD_*:
API_URL=... KEYCLOAK_URL=... ./scripts/wait-for-stack.sh
API_URL=... KEYCLOAK_URL=... ./scripts/post-deploy-smoke.sh
```

UI staging/prod: URL Vercel. Logs: dashboard Render.

---

## 8. Observabilidad

### 8.1 OpenTelemetry / Micrometer

**Cómo se implementó**

Métricas/trazas en el bloque `management` de [`application.yml`](../../../src/main/resources/application.yml) (Actuator, Prometheus, OTel). Sampling/endurecimiento en [`application-prod.yml`](../../../src/main/resources/application-prod.yml).

| Archivo | Rol |
|---|---|
| [`application.yml`](../../../src/main/resources/application.yml) | **Implementación** / config OTel + Actuator |
| [`application-prod.yml`](../../../src/main/resources/application-prod.yml) | Sampling bajo en prod |

**Probar / resultados**

```bash
curl -sf http://localhost:8080/actuator/health
curl -sf http://localhost:8080/actuator/prometheus | head
```

---

### 8.2 Prometheus / Alertmanager

**Cómo se implementó**

Scrape y alertas en:

- [`infra/prometheus/prometheus.yml`](../../../infra/prometheus/prometheus.yml)
- [`infra/prometheus/alerts.yml`](../../../infra/prometheus/alerts.yml)
- [`infra/prometheus/alertmanager.yml`](../../../infra/prometheus/alertmanager.yml)

| Archivo | Rol |
|---|---|
| `infra/prometheus/*.yml` | **Implementación** scrape + alertas |

**Probar / resultados**

```bash
docker compose up -d prometheus alertmanager
open http://localhost:9090/targets          # inventory-api UP
open http://localhost:9090/graph            # up{job="inventory-api"}
open http://localhost:9093                  # Alertmanager
curl -sf http://localhost:9090/api/v1/rules | python3 -m json.tool | head -40
```

---

### 8.3 Grafana / Loki / Tempo / Alloy

**Cómo se implementó**

Provisionado en `infra/grafana/` (datasources + dashboards JSON). Logs: [`infra/loki/loki.yml`](../../../infra/loki/loki.yml). Trazas: [`infra/tempo/tempo.yml`](../../../infra/tempo/tempo.yml). Receptor OTLP: [`infra/alloy/config.alloy`](../../../infra/alloy/config.alloy). Solo en Compose local (no cloud).

| Archivo | Rol |
|---|---|
| [`infra/grafana/`](../../../infra/grafana/) | **Implementación** dashboards |
| [`infra/alloy/config.alloy`](../../../infra/alloy/config.alloy) | **Implementación** pipeline OTLP |
| `infra/loki/`, `infra/tempo/` | Storage logs/trazas |

**Probar / resultados**

```bash
docker compose up -d --build postgres keycloak api alloy tempo loki prometheus grafana
./scripts/wait-for-stack.sh   # opcional: esperar API + Keycloak
./scripts/generate-obs-traffic.sh
# Espera 15–30 s y abre:
open http://localhost:3001    # admin/admin → dashboard Observabilidad
```

`generate-obs-traffic.sh` pide tokens a Keycloak (`admin` / `viewer`) y dispara varios `curl` a la API: lecturas 200, 401 sin JWT, 403 del viewer al crear producto y 404 autenticado. Eso alimenta métricas (Prometheus), logs (Loki vía Alloy) y trazas OTLP (`service.name=inventory-api` → Tempo).

---

## 9. CI/CD

Detalle ampliado: [`PIPELINE.md`](../ci/PIPELINE.md).

### 9.1 Pipelines (tabla completa)

| Pipeline / archivo | Tipo | Cuándo corre | Para qué |
|---|---|---|---|
| [`.github/workflows/devsecops.yml`](../../../.github/workflows/devsecops.yml) | **Calidad / DevSecOps** (principal) | Push y PR a `develop` (+ manual) | Build, unit, integration, API, contract, JaCoCo/Sonar, build de imágenes Docker, Dependency-Check, ZAP (Compose normal), staging Compose + smoke + Playwright, quality gate |
| [`.github/workflows/deploy-staging.yml`](../../../.github/workflows/deploy-staging.yml) | **Deploy cloud staging** | Push a `develop` (+ manual) | Publica staging persistente: hooks/auto-deploy **Render** (API/KC/DB) + **Vercel** FE + smoke cloud opcional |
| [`.github/workflows/deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml) | **Deploy cloud producción** | Push a `main` (+ manual) | Igual que staging pero ambiente prod (`render.prod.yaml` + Vercel prod) |
| [`.github/workflows/conventional-commits.yml`](../../../.github/workflows/conventional-commits.yml) | **Calidad de commits** | PR a `develop` / `main` | Valida asunto `tipo(scope): mensaje` |
| [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile) | **Paridad local (Jenkins)** | Manual en Jenkins del Compose | Misma idea de stages que DevSecOps: Build → Unit → Integration → API → Security (Compose security) → Sonar → Docker → Staging Compose → E2E |
| [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml) | Legacy / manual | Solo `workflow_dispatch` | Solo build + tests + Sonar (trozo ya cubierto por DevSecOps) |
| [`.github/workflows/security.yml`](../../../.github/workflows/security.yml) | Legacy / manual | Solo manual | Solo Dependency-Check + ZAP aislados |
| [`.github/workflows/post-deploy-staging.yml`](../../../.github/workflows/post-deploy-staging.yml) | Legacy / manual | Solo manual | Solo Compose staging + smoke + Playwright |

**Frase defensa:** calidad = DevSecOps; despliegue cloud = `deploy-staging` / `deploy-prod`; Jenkins = espejo local; `ci` / `security` / `post-deploy-staging` = respaldos manuales.

**Probar / resultados**

- GitHub → Actions → *DevSecOps Pipeline* (checks + artefactos `zap-security-reports`, `post-deploy-evidence`).
- Deploy: Actions → *Deploy staging/production (cloud)*.
- Jenkins local: http://localhost:8082 → job → consola + artefactos archivados.
- Comandos locales equivalentes: [§ 11](#11-comandos-probar-y-ver-resultados).

### 9.2 GitHub Actions — scripts de apoyo

| Archivo | Rol |
|---|---|
| [`scripts/wait-for-stack.sh`](../../../scripts/wait-for-stack.sh) | Espera health de API/Keycloak/FE antes de probar |
| [`scripts/post-deploy-smoke.sh`](../../../scripts/post-deploy-smoke.sh) | Smoke post-deploy (Compose staging o cloud) |
| [`scripts/security-smoke.sh`](../../../scripts/security-smoke.sh) | Smoke JWT/CORS antes de ZAP |
| [`scripts/zap-baseline.sh`](../../../scripts/zap-baseline.sh) | DAST baseline OWASP ZAP |
| [`scripts/dependency-check.sh`](../../../scripts/dependency-check.sh) | SCA OWASP Dependency-Check |

### 9.3 Jenkins — agent y helpers

| Archivo | Rol |
|---|---|
| [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile) | **Implementación** stages del pipeline local |
| [`infra/jenkins/Dockerfile`](../../../infra/jenkins/Dockerfile) | Imagen del agent (JDK 21, Docker CLI, Node, Chromium) |
| [`scripts/jenkins-host-compose.sh`](../../../scripts/jenkins-host-compose.sh) | Levanta staging Compose reescribiendo bind mounts al path del host |
| [`scripts/jenkins-e2e-portforward.mjs`](../../../scripts/jenkins-e2e-portforward.mjs) | Proxy `localhost` → host para Playwright/PKCE dentro del agent |
| [`docs/final/ci/JENKINS.md`](../ci/JENKINS.md) | Setup operativo |

---

## 10. Documentación de defensa

| Archivo | Rol |
|---|---|
| [`../README.md`](../README.md) | Índice entregable `docs/final/` |
| [`../REQUISITOS.md`](../REQUISITOS.md) | RF / RNF (pedido PDF) |
| [`../TECNICA.md`](../TECNICA.md) | Arquitectura, instalación, mantenimiento |
| [`../GUIA-PRUEBAS.md`](../GUIA-PRUEBAS.md) | Comandos para probar todo |
| [`README.md`](README.md) | Guía maestra de defensa |
| [`ARBOL.md`](ARBOL.md) | Este catálogo |
| [`PREGUNTAS.md`](PREGUNTAS.md) | Banco de preguntas |
| [`../testing/README.md`](../testing/README.md) | Catálogo de pruebas |
| [`../ci/ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md) | Ambientes |
| [`../ci/CLOUD.md`](../ci/CLOUD.md) | Cloud |

---

## 11. Comandos: probar y ver resultados

Desde la **raíz del repo** salvo que diga `cd frontend`. Detalle oral: [`GUIA-DEMO.md`](GUIA-DEMO.md).

### 11.1 Prerrequisito stack local

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
./scripts/wait-for-stack.sh
```

### 11.1.1 JWT (Keycloak → terminal)

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/inventory/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=inventory-api \
  -d client_secret=inventory-api-secret \
  -d username=admin \
  -d password=admin \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "$TOKEN"
```

Pide un `access_token` al realm `inventory` (client confidential `inventory-api`) y lo muestra en la consola. Luego: `curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products`.

### 11.2 Tests automatizados

| Qué | Comando | Dónde ver resultados |
| --- | ------- | -------------------- |
| Unit | `./gradlew test` | `build/reports/tests/test/index.html` |
| API scenarios | `./gradlew apiTest` | `build/reports/tests/apiTest/index.html` |
| Contract | `./gradlew contractTest` | consola / `build/reports/tests/contractTest/` |
| Integration (Docker) | `./gradlew integrationTest` | `build/reports/tests/integrationTest/index.html` |
| E2E Playwright | `cd frontend && npx playwright test --reporter=html --project=chromium --workers=1` | `npx playwright show-report` → `frontend/playwright-report/` |
| Load k6 | `./scripts/k6-run.sh load` | `docs/final/testing/k6/load-products-summary.txt` |
| Stress k6 | `./scripts/k6-run.sh stress` | `docs/final/testing/k6/stress-products-summary.txt` |
| Security smoke | `./scripts/security-smoke.sh` | consola + `docs/final/testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md` |
| Post-deploy smoke | `./scripts/post-deploy-smoke.sh` | consola + `docs/final/ci/EVIDENCIA-POST-DEPLOY-SMOKE.md` |
| ZAP DAST | `./scripts/zap-baseline.sh` | `docs/final/testing/zap/zap-report.html` |
| Dependency-Check | `./scripts/dependency-check.sh` | `docs/final/testing/dependency-check/` |

### 11.3 Calidad

| Qué | Comando | Resultados |
| --- | ------- | ---------- |
| JaCoCo | `./gradlew test apiTest jacocoTestReport` | `build/reports/jacoco/test/html/index.html` |
| SonarCloud | `./gradlew test jacocoTestReport sonar` | Dashboard SonarCloud (+ badge README) |

### 11.4 Observabilidad (Compose)

| Qué | Comando / URL |
| --- | ------------- |
| Health / métricas raw | `curl -sf localhost:8080/actuator/health` · `/actuator/prometheus` |
| Tráfico demo (logs + trazas + métricas) | `./scripts/generate-obs-traffic.sh` — JWT + curls 200/401/403/404 para llenar Grafana |
| Prometheus targets | http://localhost:9090/targets |
| Grafana | http://localhost:3001 (`admin`/`admin`) — dashboard Observabilidad |
| Alertmanager | http://localhost:9093 |

### 11.5 Staging Compose (post-deploy)

```bash
cp .env.staging.example .env.staging
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build \
  postgres keycloak tempo loki alloy api frontend
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 FRONTEND_URL=http://localhost:3008 \
  ./scripts/wait-for-stack.sh
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 ./scripts/post-deploy-smoke.sh
cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3008 API_BASE=http://localhost:8088 \
  KEYCLOAK_URL=http://localhost:8181 \
  npx playwright test e2e/helpers/login.spec.ts e2e/permissions.spec.ts --project=chromium
```

### 11.6 CI

| Pipeline | Cómo ver resultados |
| -------- | ------------------- |
| DevSecOps | GitHub → Actions → run → jobs + artefactos |
| Deploy staging/prod | Actions → workflow correspondiente + URLs Render/Vercel |
| Jenkins | http://localhost:8082 → build → Console Output / Artifacts |

---

## Mapa rápido

| Herramienta | Archivo(s) de implementación principal | Probar (atajo) |
|---|---|---|
| Keycloak | `keycloak/inventory-realm.json`, `frontend/src/auth/*`, `KeycloakAdminClient.java` | §11.1.1 `TOKEN=$(curl…)` + `echo "$TOKEN"` |
| OAuth2 | `DockerSecurityConfig.java`, `@PreAuthorize`, `frontend/src/api/client.ts` | `security-smoke.sh` · `apiTest` |
| Playwright | `frontend/playwright.config.ts`, `frontend/e2e/**` | `npx playwright test --reporter=html` + `show-report` |
| Testcontainers | `AbstractIntegrationTest.java`, `AbstractKeycloakIntegrationTest.java` | `./gradlew integrationTest` |
| k6 | `tests/k6/*.js` | `./scripts/k6-run.sh load\|stress` |
| JaCoCo | `build.gradle` (`jacoco*`) | `jacocoTestReport` → HTML |
| Sonar | `build.gradle` (`sonar`), `sonar-project.properties` | `./gradlew sonar` / Actions |
| Flyway | `src/main/resources/db/migration/V*.sql` | Arranque API / IT |
| Envers | `Product.java` (`@Audited`), `AuditService.java` | Historial UI / audit API |
| Docker Compose | `docker-compose*.yml` | `docker compose up` + health |
| Pipelines | `devsecops.yml`, `deploy-*.yml`, `Jenkinsfile` | GitHub Actions / Jenkins UI |
