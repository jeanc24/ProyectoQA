# ProyectoQA — Requisitos obligatorios

**Mapa del proyecto final (PDF V3):** qué exige el curso, dónde está en el código y cómo abrirlo en vivo.

> *No basta con que el sistema funcione: hay que demostrar calidad, seguridad, observabilidad y automatización con evidencia en el repositorio.*

[DevSecOps](https://github.com/jeanc24/ProyectoQA/actions/workflows/devsecops.yml)
[Quality Gate](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)
[Coverage](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)
[Spring Boot](https://spring.io/projects/spring-boot)
[React](https://react.dev/)
[Keycloak](https://www.keycloak.org/)

---

## Arranque rápido

```bash
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

Documentación ampliada: [defensa](docs/final/defensa/README.md) · [ARBOL](docs/final/defensa/ARBOL.md) · [PREGUNTAS](docs/final/defensa/PREGUNTAS.md)

---

## Tabla de contenidos

1. [Roles y seguridad](#1-roles-y-seguridad)
2. [Arquitectura técnica](#2-arquitectura-técnica)
3. [Full Stack Testing](#3-full-stack-testing)
4. [Entornos](#4-entornos)
5. [Observabilidad y telemetría](#5-observabilidad-y-telemetría)
6. [Calidad de código](#6-calidad-de-código)
7. [DevSecOps y CI/CD](#7-devsecops-y-cicd)
8. [Repositorio y documentación](#8-repositorio-y-documentación)
9. [Alcance funcional](#9-alcance-funcional)
10. [Referencia rápida](#10-referencia-rápida)

En cada subsección hay un bloque **Cómo está implementado**: archivo de entrada, qué define, y el árbol de flujos/archivos que cuelga de ahí.

---

## 1. Roles y seguridad

### 1.1 Modelo granular

Permisos por operación (no roles simples tipo “Administrador”).

Se notan en [http://localhost:3000/products](http://localhost:3000) (Pagina Principal), y [http://localhost:8081](http://localhost:8081) (Keycloak).


| Permiso                           | Descripción                           |
| --------------------------------- | ------------------------------------- |
| `product:view` / `product:manage` | Ver productos / crear-editar-eliminar |
| `stock:view` / `stock:manage`     | Ver historial / registrar movimientos |
| `report:view`                     | Acceso al dashboard y reportes        |
| `audit:view`                      | Consultar historial Envers            |
| `user:manage`                     | Gestionar usuarios en Keycloak        |


#### Cómo está implementado

**Fuente de verdad de los 7 permisos:** `[inventory-realm.json](keycloak/inventory-realm.json)` (roles del client `inventory-api`).

**Árbol de uso (mismo string en 3 capas):**

1. JWT claim → `[DockerSecurityConfig](src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java)` → `GrantedAuthority`
2. Controllers: `@PreAuthorize("hasAuthority('product:view')")` etc. en Product/Stock/Report/Audit/User
3. FE: `[permissions.ts](frontend/src/auth/permissions.ts)` + `[ProtectedRoute](frontend/src/components/ProtectedRoute.tsx)` (solo UX; la API sigue siendo quien deniega con 403)

### 1.2 Keycloak · OAuth2 · JWT

Login OAuth2/JWT, protección de endpoints y refresh token.

[http://localhost:3000/login](http://localhost:3000/login) (Login) · [http://localhost:8081](http://localhost:8081) (Keycloak) 


| Archivo                                                                                             | Descripción                                       |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| [inventory-realm.json](keycloak/inventory-realm.json)                                               | Realm con clients, usuarios demo y los 7 permisos |
| [Dockerfile](infra/keycloak/Dockerfile)                                                             | Imagen Docker de Keycloak                         |
| [DockerSecurityConfig.java](src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) | Valida el JWT y mapea permisos en la API          |
| [SecurityConfig.java](src/main/java/icc354/pucmm/proyectoqa/config/SecurityConfig.java)             | Seguridad abierta solo para perfil `local`        |
| [application-docker.yml](src/main/resources/application-docker.yml)                                 | `issuer-uri` / JWK para validar tokens            |
| [keycloak.ts](frontend/src/auth/keycloak.ts)                                                        | Cliente Keycloak del frontend                     |
| [AuthContext.tsx](frontend/src/auth/AuthContext.tsx)                                                | Estado de sesión, login y logout                  |
| [client.ts](frontend/src/api/client.ts)                                                             | Envía el Bearer y renueva el token                |
| [permissions.ts](frontend/src/auth/permissions.ts)                                                  | Decide qué botones/rutas mostrar según permiso    |
| [ProtectedRoute.tsx](frontend/src/components/ProtectedRoute.tsx)                                    | Bloquea rutas sin el permiso requerido            |



| Usuario         | Password        | Acceso                              |
| --------------- | --------------- | ----------------------------------- |
| `admin`         | `admin`         | Todos los permisos                  |
| `viewer`        | `viewer`        | Solo lectura                        |
| `stock-manager` | `stock-manager` | Opera stock, no manage de productos |


#### Cómo está implementado

**Entrada (IdP):** imagen `[infra/keycloak/Dockerfile](infra/keycloak/Dockerfile)` importa `[inventory-realm.json](keycloak/inventory-realm.json)`.

**Entrada (FE):** `[keycloak.ts](frontend/src/auth/keycloak.ts)` + `[AuthContext.tsx](frontend/src/auth/AuthContext.tsx)` → login → tokens → `[client.ts](frontend/src/api/client.ts)` pone Bearer / refresh.

**Entrada (API):** `issuer-uri` / JWK en `[application-docker.yml](src/main/resources/application-docker.yml)` (y staging/prod) → `[DockerSecurityConfig](src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java)`. Perfil `local` usa `[SecurityConfig](src/main/java/icc354/pucmm/proyectoqa/config/SecurityConfig.java)` abierta.

**Quién reutiliza los usuarios demo:** E2E `[auth.ts](frontend/e2e/helpers/auth.ts)`, smoke post-deploy, k6.

---

## 2. Arquitectura técnica

### 2.1 Migraciones Flyway

Esquema versionado al arrancar.

BD: `localhost:5433` (`inventory` / `inventory`)


| Archivo                                                                                                          | Descripción                                  |
| ---------------------------------------------------------------------------------------------------------------- | -------------------------------------------- |
| [V1__init_schema.sql](src/main/resources/db/migration/V1__init_schema.sql)                                       | Crea tablas de negocio y de auditoría Envers |
| [V2__add_revinfo_sequence.sql](src/main/resources/db/migration/V2__add_revinfo_sequence.sql)                     | Agrega la secuencia de revisiones Envers     |
| [V3__fix_revinfo_sequence_increment.sql](src/main/resources/db/migration/V3__fix_revinfo_sequence_increment.sql) | Corrige el incremento de esa secuencia       |
| [application.yml](src/main/resources/application.yml)                                                            | Activa Flyway al arrancar el backend         |


#### Cómo está implementado

**Entrada:** Flyway activado en `[application.yml](src/main/resources/application.yml)` al arrancar la API.

**Cadena de migraciones (en orden):**

1. `[V1__init_schema.sql](src/main/resources/db/migration/V1__init_schema.sql)` — tablas negocio + Envers
2. `[V2__add_revinfo_sequence.sql](src/main/resources/db/migration/V2__add_revinfo_sequence.sql)`
3. `[V3__fix_revinfo_sequence_increment.sql](src/main/resources/db/migration/V3__fix_revinfo_sequence_increment.sql)`

**Quién las vuelve a aplicar:** `AbstractIntegrationTest` (Testcontainers) y cualquier ambiente Compose/Render con Postgres vacío.

---

## 3. Full Stack Testing

### 3.1 Test #1 - Unit testing

Servicios y lógica de negocio.

```bash
./gradlew test
```


| Archivo                                                                                                                         | Descripción                              |
| ------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| [ProductServiceTest.java](src/test/java/icc354/pucmm/proyectoqa/application/service/ProductServiceTest.java)                    | Lógica de productos (CRUD, SKU, filtros) |
| [StockServiceTest.java](src/test/java/icc354/pucmm/proyectoqa/application/service/StockServiceTest.java)                        | Movimientos IN/OUT/ADJUSTMENT            |
| [ReportServiceTest.java](src/test/java/icc354/pucmm/proyectoqa/application/service/ReportServiceTest.java)                      | Resumen, low-stock, top products         |
| [ProductControllerTest.java](src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductControllerTest.java)           | HTTP del controller de productos         |
| [GlobalExceptionHandlerTest.java](src/test/java/icc354/pucmm/proyectoqa/application/controller/GlobalExceptionHandlerTest.java) | Mapeo de excepciones a status HTTP       |


**Suites unitarias**

- [ProductServiceTest](src/test/java/icc354/pucmm/proyectoqa/application/service/ProductServiceTest.java)
- [StockServiceTest](src/test/java/icc354/pucmm/proyectoqa/application/service/StockServiceTest.java)
- [ReportServiceTest](src/test/java/icc354/pucmm/proyectoqa/application/service/ReportServiceTest.java)
- [UserServiceTest](src/test/java/icc354/pucmm/proyectoqa/application/service/UserServiceTest.java)
- [KeycloakAdminClientTest](src/test/java/icc354/pucmm/proyectoqa/application/service/KeycloakAdminClientTest.java)
- [ProductControllerTest](src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductControllerTest.java)
- [GlobalExceptionHandlerTest](src/test/java/icc354/pucmm/proyectoqa/application/controller/GlobalExceptionHandlerTest.java)

#### Cómo está implementado

**Entrada:** task Gradle `test` en `[build.gradle](build.gradle)` (`./gradlew test`).

**Árbol de suites que cuelga:**

- Services: `ProductServiceTest`, `StockServiceTest`, `ReportServiceTest`, `UserServiceTest`, `KeycloakAdminClientTest`
- Controllers (MockMvc): `ProductControllerTest`, `GlobalExceptionHandlerTest`

**Salida hacia calidad:** cada run genera execution data JaCoCo → reporte que consume Sonar (§6).

### 3.2 Test #2 - Integration testing (Testcontainers)

BD y Keycloak reales en contenedores efímeros.

```bash
./gradlew integrationTest
```


| Archivo                                                                                                                                    | Descripción                                 |
| ------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------- |
| [AbstractIntegrationTest.java](src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java)                 | Base con PostgreSQL real vía Testcontainers |
| [AbstractKeycloakIntegrationTest.java](src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) | Base con Keycloak real + realm              |
| [KeycloakSecurityIntegrationTest.java](src/test/java/icc354/pucmm/proyectoqa/application/integration/KeycloakSecurityIntegrationTest.java) | JWT real y comprobación 401/403/200         |


**Suites de integración**

- [ProductIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/ProductIntegrationTest.java)
- [StockIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/StockIntegrationTest.java)
- [ReportIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/ReportIntegrationTest.java)
- [DataIntegrityIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/DataIntegrityIntegrationTest.java)
- [KeycloakSecurityIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/KeycloakSecurityIntegrationTest.java)

#### Cómo está implementado

**Entrada:** task `integrationTest` en `[build.gradle](build.gradle)`.

**Bases compartidas:**

- `[AbstractIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java)` → Postgres Testcontainers + Flyway
- `[AbstractKeycloakIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java)` → Keycloak + realm

**Suites que cuelgan de esas bases:** `ProductIntegrationTest`, `StockIntegrationTest`, `ReportIntegrationTest`, `DataIntegrityIntegrationTest`, `KeycloakSecurityIntegrationTest`.

### 3.3 Test #3 - API contract testing

Status codes, payloads y contrato OpenAPI.

Alineado a [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (Swagger)

```bash
./gradlew apiTest contractTest
```


| Archivo                                                                                                                 | Descripción                       |
| ----------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| [ProductApiScenarioTest.java](src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductApiScenarioTest.java) | Escenarios HTTP de productos      |
| [OpenApiContractTest.java](src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java)         | Cumplimiento del contrato OpenAPI |


**Suites API / contrato**

- [ProductApiScenarioTest](src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductApiScenarioTest.java)
- [StockApiScenarioTest](src/test/java/icc354/pucmm/proyectoqa/application/controller/StockApiScenarioTest.java)
- [ReportApiScenarioTest](src/test/java/icc354/pucmm/proyectoqa/application/controller/ReportApiScenarioTest.java)
- [AuditApiScenarioTest](src/test/java/icc354/pucmm/proyectoqa/application/controller/AuditApiScenarioTest.java)
- [UserApiScenarioTest](src/test/java/icc354/pucmm/proyectoqa/application/controller/UserApiScenarioTest.java)
- [OpenApiContractTest](src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java)

#### Cómo está implementado

**Entrada:** tasks `apiTest` y `contractTest` en `[build.gradle](build.gradle)` (`./gradlew apiTest contractTest`).

**Árbol API (escenarios HTTP):** `ProductApiScenarioTest`, `StockApiScenarioTest`, `ReportApiScenarioTest`, `AuditApiScenarioTest`, `UserApiScenarioTest`.

**Árbol contrato:** `[OpenApiContractTest](src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java)` valida contra el OpenAPI que genera springdoc (mismo contrato que Swagger UI).

### 3.4 Test #4 - E2E testing (Playwright)

Flujos UI contra el frontend en marcha.

[http://localhost:3000](http://localhost:3000) (Frontend) · [http://localhost:3000/products](http://localhost:3000/products) (Productos)

```bash
cd frontend && npm run test:e2e
```


| Archivo                                                   | Descripción                                    |
| --------------------------------------------------------- | ---------------------------------------------- |
| [playwright.config.ts](frontend/playwright.config.ts)     | Configuración baseURL, browsers y reportes E2E |
| [login.spec.ts](frontend/e2e/helpers/login.spec.ts)       | Flujo de login                                 |
| [products.spec.ts](frontend/e2e/helpers/products.spec.ts) | Listar y crear productos                       |
| [permissions.spec.ts](frontend/e2e/permissions.spec.ts)   | Restricciones admin vs viewer                  |
| [stock.spec.ts](frontend/e2e/stock.spec.ts)               | Flujos de stock                                |
| [dashboard.spec.ts](frontend/e2e/dashboard.spec.ts)       | Carga del dashboard                            |


**Suites E2E**

- [login.spec.ts](frontend/e2e/helpers/login.spec.ts)
- [products.spec.ts](frontend/e2e/helpers/products.spec.ts)
- [permissions.spec.ts](frontend/e2e/permissions.spec.ts)
- [stock.spec.ts](frontend/e2e/stock.spec.ts)
- [dashboard.spec.ts](frontend/e2e/dashboard.spec.ts)

#### Cómo está implementado

**Entrada:** `[playwright.config.ts](frontend/playwright.config.ts)` — lo arranca `npm run test:e2e` en `[package.json](frontend/package.json)` (`playwright test`).

**Qué define el config:** `testDir: "./e2e"` (carpeta de specs), `baseURL` (`PLAYWRIGHT_BASE_URL` o `http://localhost:3000`), Chromium, timeouts, retries, trace/screenshot/video.

**Árbol que cuelga de `testDir` (todos los flujos E2E):**

- Helper compartido → `[e2e/helpers/auth.ts](frontend/e2e/helpers/auth.ts)` (`loginAs`, token API, `createProductViaApi`)
- Login → `[e2e/helpers/login.spec.ts](frontend/e2e/helpers/login.spec.ts)`
- Productos → `[e2e/helpers/products.spec.ts](frontend/e2e/helpers/products.spec.ts)`
- Permisos por rol → `[e2e/permissions.spec.ts](frontend/e2e/permissions.spec.ts)`
- Stock → `[e2e/stock.spec.ts](frontend/e2e/stock.spec.ts)`
- Dashboard + evidencia móvil → `[e2e/dashboard.spec.ts](frontend/e2e/dashboard.spec.ts)`

**Quién también apunta a este config:** job E2E de `devsecops.yml` / Jenkins (staging Compose, a menudo `PLAYWRIGHT_BASE_URL=http://localhost:3008`).

### 3.5 Test #5 - Security testing

ZAP, JWT/CORS/permisos y Dependency-Check.

Evidencias: [zap/](docs/final/testing/zap/) · [dependency-check/](docs/final/testing/dependency-check/)


| Archivo                                            | Descripción                                 |
| -------------------------------------------------- | ------------------------------------------- |
| [security-smoke.sh](scripts/security-smoke.sh)     | Comprueba JWT, CORS y denegación 403        |
| [zap-baseline.sh](scripts/zap-baseline.sh)         | Escaneo DAST con OWASP ZAP                  |
| [dependency-check.sh](scripts/dependency-check.sh) | Escaneo de vulnerabilidades en dependencias |


**Pruebas de seguridad**

- Smoke JWT / CORS / permisos → [security-smoke.sh](scripts/security-smoke.sh)
- OWASP ZAP baseline → [zap-baseline.sh](scripts/zap-baseline.sh)
- Dependency-Check (SCA) → [dependency-check.sh](scripts/dependency-check.sh)

#### Cómo está implementado

**Entrada (orquestación CI):** jobs de seguridad en `[devsecops.yml](.github/workflows/devsecops.yml)` (y stages equivalentes en Jenkins).

**Scripts que cuelgan:**

- `[security-smoke.sh](scripts/security-smoke.sh)` → JWT / CORS / 403
- `[zap-baseline.sh](scripts/zap-baseline.sh)` → DAST (suele usar `[docker-compose.security.yml](docker-compose.security.yml)`)
- `[dependency-check.sh](scripts/dependency-check.sh)` → SCA

**Evidencias:** `docs/final/testing/zap/` · `docs/final/testing/dependency-check/`

### 3.6 Test #6 - Performance testing

Load/stress con k6 contra [http://localhost:8080](http://localhost:8080) (API).

Evidencia: [k6/](docs/final/testing/k6/)


| Archivo                        | Descripción                       |
| ------------------------------ | --------------------------------- |
| [k6-run.sh](scripts/k6-run.sh) | Ejecuta pruebas de carga y estrés |
| [tests/k6/](tests/k6/)         | Scripts load / stress             |


**Pruebas de rendimiento**

- Load testing → [tests/k6/](tests/k6/)
- Stress testing → [tests/k6/](tests/k6/)

#### Cómo está implementado

**Entrada:** `[scripts/k6-run.sh](scripts/k6-run.sh)` — elige load/stress, URL, JWT y escribe resumen.

**Scripts que cuelgan:** carpeta `[tests/k6/](tests/k6/)` (load y stress contra `/api/v1/...`).

**Salida:** `docs/final/testing/k6/` (json/txt). Si Prometheus está up, el tráfico también se ve en Grafana.

### 3.7 Test #7 - Data testing · Exploratory

Integridad de datos y charters exploratorios.

Evidencia: [exploratory/](docs/final/testing/exploratory/)


| Archivo                                                                                                                              | Descripción                            |
| ------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------- |
| [DataIntegrityIntegrationTest.java](src/test/java/icc354/pucmm/proyectoqa/application/integration/DataIntegrityIntegrationTest.java) | Constraints e integridad en BD         |
| [exploratory/](docs/final/testing/exploratory/)                                                                                      | Charters, bugs y escenarios explorados |


**Pruebas de datos / exploratory**

- [DataIntegrityIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/DataIntegrityIntegrationTest.java)
- Charters exploratorios → [exploratory/](docs/final/testing/exploratory/)

#### Cómo está implementado

**Entrada (automático):** `[DataIntegrityIntegrationTest](src/test/java/icc354/pucmm/proyectoqa/application/integration/DataIntegrityIntegrationTest.java)` (cuelga de `AbstractIntegrationTest` + migraciones Flyway).

**Entrada (manual documentada):** charters bajo `[docs/final/testing/exploratory/](docs/final/testing/exploratory/)` (p. ej. Charter EX-01 stock) con capturas `ex01-*.png`.

---

## 4. Entornos

### 4.1 Development · Staging · Production

Tres ambientes obligatorios. **Staging y production persistentes viven en la nube** (Render + Vercel). Los `docker-compose.staging.yml` / `docker-compose.prod.yml` ya no son el deploy “oficial” de esos ambientes: sirven para CI efímero y demo local.

Detalle: [ENVIRONMENTS.md](docs/final/ci/ENVIRONMENTS.md) · [CLOUD.md](docs/final/ci/CLOUD.md)


| Ambiente           | Dónde corre     | Branch    | Qué se usa                          |
| ------------------ | --------------- | --------- | ----------------------------------- |
| Development        | Local           | —         | Compose + perfil `docker` / `local` |
| Staging (cloud)    | Render + Vercel | `develop` | Blueprint + deploy workflow         |
| Production (cloud) | Render + Vercel | `main`    | Blueprint + deploy workflow         |



| Archivo                                                    | Descripción                                                        |
| ---------------------------------------------------------- | ------------------------------------------------------------------ |
| [docker-compose.yml](docker-compose.yml)                   | Desarrollo local (API, FE, BD, Keycloak, observabilidad)           |
| [render.yaml](render.yaml)                                 | Blueprint Render de **staging cloud** (API + Keycloak + Postgres)  |
| [render.prod.yaml](infra/render/render.prod.yaml)          | Blueprint Render de **production cloud**                           |
| [deploy-staging.yml](.github/workflows/deploy-staging.yml) | Push `develop` → hooks Render + deploy Vercel staging + smoke      |
| [deploy-prod.yml](.github/workflows/deploy-prod.yml)       | Push `main` → hooks Render + deploy Vercel prod + smoke            |
| [docker-compose.staging.yml](docker-compose.staging.yml)   | Staging **efímero** (job DevSecOps / Jenkins / post-deploy local)  |
| [docker-compose.prod.yml](docker-compose.prod.yml)         | Plantilla **production-like** local (demo); no es el prod en cloud |


Local: [http://localhost:3000](http://localhost:3000) (Frontend) · Puertos Compose: [ENVIRONMENTS.md](docs/final/ci/ENVIRONMENTS.md)

#### Cómo está implementado

**Entrada local:** `[docker-compose.yml](docker-compose.yml)` + `.env` + perfiles `[application-docker.yml](src/main/resources/application-docker.yml)` / `local`.

**Entrada staging cloud:** push `develop` → `[deploy-staging.yml](.github/workflows/deploy-staging.yml)` → Blueprint `[render.yaml](render.yaml)` (API+KC+DB) + Vercel FE.

**Entrada prod cloud:** push `main` → `[deploy-prod.yml](.github/workflows/deploy-prod.yml)` → `[render.prod.yaml](infra/render/render.prod.yaml)` + Vercel prod.

**Compose “hermanos” (no son el cloud):** `[docker-compose.staging.yml](docker-compose.staging.yml)` (CI efímero) · `[docker-compose.prod.yml](docker-compose.prod.yml)` (demo local). Perfiles Spring: `application-staging.yml` / `application-prod.yml`.

### 4.2 Tests post-deploy

Smoke/E2E tras levantar el stack (Compose efímero en CI, o URLs cloud si hay vars configuradas).

Runs: [GitHub Actions](https://github.com/jeanc24/ProyectoQA/actions)


| Archivo                                                              | Descripción                                   |
| -------------------------------------------------------------------- | --------------------------------------------- |
| [post-deploy-staging.yml](.github/workflows/post-deploy-staging.yml) | Smoke/E2E sobre staging Compose (ENV-02)      |
| [deploy-staging.yml](.github/workflows/deploy-staging.yml)           | Smoke contra staging **cloud** tras deploy    |
| [deploy-prod.yml](.github/workflows/deploy-prod.yml)                 | Smoke contra production **cloud** tras deploy |
| [post-deploy-tests.md](docs/final/ci/post-deploy-tests.md)           | Cómo reproducir wait + smoke + Playwright     |


#### Cómo está implementado

**Entrada (scripts):** `[wait-for-stack.sh](scripts/wait-for-stack.sh)` → `[post-deploy-smoke.sh](scripts/post-deploy-smoke.sh)` (± Playwright vía config §3.4).

**Quién los invoca:**

- Compose efímero → `[post-deploy-staging.yml](.github/workflows/post-deploy-staging.yml)` / job `staging-deploy-e2e` en DevSecOps
- Cloud → job `smoke-cloud` dentro de `[deploy-staging.yml](.github/workflows/deploy-staging.yml)` y `[deploy-prod.yml](.github/workflows/deploy-prod.yml)`

Guía: `[post-deploy-tests.md](docs/final/ci/post-deploy-tests.md)`.

---

## 5. Observabilidad y telemetría

### 5.1 Métricas — Prometheus

Scrape de Actuator → reglas de alerta → Alertmanager.

[http://localhost:9090](http://localhost:9090) (Prometheus) · [http://localhost:9093](http://localhost:9093) (Alertmanager)


| Archivo                                               | Descripción                                                               |
| ----------------------------------------------------- | ------------------------------------------------------------------------- |
| [prometheus.yml](infra/prometheus/prometheus.yml)     | Scrape cada 15s de `api:8080/actuator/prometheus` + enlace a Alertmanager |
| [alerts.yml](infra/prometheus/alerts.yml)             | 6 alertas: CPU, heap, API down, 5xx, latencia p95, spike 401              |
| [alertmanager.yml](infra/prometheus/alertmanager.yml) | Agrupa por alerta/servicio/severidad; inhibit warning si hay critical     |


#### Cómo está implementado

**Entrada:** `[prometheus.yml](infra/prometheus/prometheus.yml)` (montado por Compose en el servicio `prometheus`).

**Qué define:** scrape cada 15s de `api:8080/actuator/prometheus`, carga de reglas, destino Alertmanager.

**Árbol que cuelga del config:**

- Reglas → `[alerts.yml](infra/prometheus/alerts.yml)` (CPU, heap, API down, 5xx, p95, spike 401)
- Notificaciones → `[alertmanager.yml](infra/prometheus/alertmanager.yml)`
- Origen de métricas → Actuator en `[application.yml](src/main/resources/application.yml)`
- Consumidor visual → Grafana datasource Prometheus (§5.2)

### 5.2 Dashboards — Grafana

Infra, app, negocio y seguridad.

[http://localhost:3001](http://localhost:3001) (Grafana) (`admin` / `admin`)


| Archivo                                                                   | Descripción                                |
| ------------------------------------------------------------------------- | ------------------------------------------ |
| [datasources.yml](infra/grafana/provisioning/datasources/datasources.yml) | Conecta Grafana a Prometheus, Loki y Tempo |
| [app.json](infra/grafana/dashboards/app.json)                             | Paneles de JVM, latencia y throughput      |
| [api-ops.json](infra/grafana/dashboards/api-ops.json)                     | Paneles HTTP y errores 4xx/5xx             |
| [business.json](infra/grafana/dashboards/business.json)                   | Paneles de inventario y KPIs de negocio    |
| [infra.json](infra/grafana/dashboards/infra.json)                         | Paneles de CPU, memoria y servicios        |
| [security.json](infra/grafana/dashboards/security.json)                   | Paneles de autenticación y 401/403         |


#### Cómo está implementado

**Entrada:** `[datasources.yml](infra/grafana/provisioning/datasources/datasources.yml)` — Grafana provisiona Prometheus, Loki y Tempo al arrancar.

**Dashboards que cuelgan de ese provisioning** (`infra/grafana/dashboards/`):

- `[app.json](infra/grafana/dashboards/app.json)` · `[api-ops.json](infra/grafana/dashboards/api-ops.json)`
- `[business.json](infra/grafana/dashboards/business.json)` · `[infra.json](infra/grafana/dashboards/infra.json)` · `[security.json](infra/grafana/dashboards/security.json)`

Todos consultan series del job `inventory-api` scrapeado por Prometheus.

### 5.3 Trazas · logs · alertas

Tempo, Loki, Alloy (en Compose) y Alertmanager.

[http://localhost:3001](http://localhost:3001) (Grafana) · [http://localhost:9093](http://localhost:9093) (Alertmanager)


| Archivo                                               | Descripción                                               |
| ----------------------------------------------------- | --------------------------------------------------------- |
| [docker-compose.yml](docker-compose.yml)              | Servicios Tempo (trazas), Loki (logs) y Alloy (collector) |
| [alertmanager.yml](infra/prometheus/alertmanager.yml) | Enruta y gestiona las alertas de Prometheus               |


#### Cómo está implementado

**Entrada (Compose):** servicios `tempo`, `loki`, `alloy`, `alertmanager` en `[docker-compose.yml](docker-compose.yml)`.

**Entrada (app → collector):** OTLP / tracing en `[application.yml](src/main/resources/application.yml)` → Alloy → Tempo (trazas) / Loki (logs).

**Entrada (alertas):** Prometheus (§5.1) → `[alertmanager.yml](infra/prometheus/alertmanager.yml)`. Grafana Explore une las tres señales.

---

## 6. Calidad de código

SonarCloud + JaCoCo ≥ 60%.

Proyecto: [SonarCloud](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)

```bash
./gradlew test jacocoTestReport
```


| Archivo                      | Descripción                                      |
| ---------------------------- | ------------------------------------------------ |
| [build.gradle](build.gradle) | Configura cobertura JaCoCo y enlace a SonarCloud |
| [README.md](README.md)       | Badges de coverage, bugs y quality gate          |


#### Cómo está implementado

**Entrada:** bloque `jacoco` + `sonar` en `[build.gradle](build.gradle)` (`./gradlew test jacocoTestReport` / `sonar`).

**Qué cuelga:**

- Cobertura de tasks `test` (+ `apiTest`; integration si corrió) → XML JaCoCo
- Upload a SonarCloud (`sonar.projectKey=jeanc24_ProyectoQA`)
- Gate local `jacocoTestCoverageVerification` (≥ 60%)
- Badges en `[README.md](README.md)` apuntan al mismo proyecto Sonar

---

## 7. DevSecOps y CI/CD

### 7.1 GitHub Actions

Build, tests, security, coverage, staging efímero y deploys cloud.

Runs: [DevSecOps](https://github.com/jeanc24/ProyectoQA/actions/workflows/devsecops.yml)


| Archivo                                                    | Descripción                                        |
| ---------------------------------------------------------- | -------------------------------------------------- |
| [devsecops.yml](.github/workflows/devsecops.yml)           | Pipeline principal (incluye staging Compose + E2E) |
| [deploy-staging.yml](.github/workflows/deploy-staging.yml) | Deploy staging cloud (`develop` → Render + Vercel) |
| [deploy-prod.yml](.github/workflows/deploy-prod.yml)       | Deploy production cloud (`main` → Render + Vercel) |


#### Cómo está implementado

**Entrada CI:** `[devsecops.yml](.github/workflows/devsecops.yml)` — build, tests, security, coverage, staging Compose + E2E (Playwright config §3.4).

**Entrada CD cloud (cuelga por branch):**

- `develop` → `[deploy-staging.yml](.github/workflows/deploy-staging.yml)` → Render + Vercel staging
- `main` → `[deploy-prod.yml](.github/workflows/deploy-prod.yml)` → Render + Vercel prod

Otros workflows del repo (`ci.yml`, `security.yml`, …) refuerzan checks; secrets/vars alimentan Sonar, ZAP, Vercel y URLs cloud.

### 7.2 Jenkins

Pipeline visual Checkout → Deploy.

[http://localhost:8082](http://localhost:8082) (Jenkins)


| Archivo                                  | Descripción                                          |
| ---------------------------------------- | ---------------------------------------------------- |
| [Jenkinsfile](infra/jenkins/Jenkinsfile) | Stages del pipeline visual (build, tests, deploy)    |
| [Dockerfile](infra/jenkins/Dockerfile)   | Imagen del agente Jenkins con las tools del pipeline |


#### Cómo está implementado

**Entrada:** `[Jenkinsfile](infra/jenkins/Jenkinsfile)` ejecutado en el agente de `[infra/jenkins/Dockerfile](infra/jenkins/Dockerfile)` (Jenkins UI `:8082` vía Compose).

**Stages que cuelgan (alineados a Actions):** checkout → build/tests → security (`docker-compose.security.yml`) → staging (`docker-compose.staging.yml`) → smoke/E2E.

Guía: `[docs/final/ci/JENKINS.md](docs/final/ci/JENKINS.md)`.

---

## 8. Repositorio y documentación

Repo público, README, PRs y evidencias.

[Repo](https://github.com/jeanc24/ProyectoQA) · [Issues](https://github.com/jeanc24/ProyectoQA/issues) · [PRs](https://github.com/jeanc24/ProyectoQA/pulls)


| Archivo                                   | Descripción                                  |
| ----------------------------------------- | -------------------------------------------- |
| [README.md](README.md)                    | Guía de instalación, puertos y usuarios demo |
| [CONTRIBUTING.md](CONTRIBUTING.md)        | Convención de commits, ramas y pull requests |
| [README.md](docs/final/defensa/README.md) | Explicación técnica para la defensa          |
| [testing/](docs/final/testing/)           | Carpeta de evidencias y reportes de pruebas  |


#### Cómo está implementado

**Entrada (colaboración):** `[CONTRIBUTING.md](CONTRIBUTING.md)` — commits Conventional, ramas, PRs (validado por workflows tipo `conventional-commits.yml` + CI).

**Entrada (producto/docs):** `[README.md](README.md)` (arranque, puertos, badges) → defensa en `[docs/final/defensa/](docs/final/defensa/)` → evidencias en `[docs/final/testing/](docs/final/testing/)`.

Merge a `develop`/`main` dispara los deploys de §4 / §7.

---

## 9. Alcance funcional

### 9.1 Contenedores

Stack local con Docker Compose.

[http://localhost:3000](http://localhost:3000) (Frontend) · [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) (Health API)


| Archivo                                  | Descripción                                           |
| ---------------------------------------- | ----------------------------------------------------- |
| [docker-compose.yml](docker-compose.yml) | Orquesta API, frontend, BD, Keycloak y observabilidad |
| [Dockerfile](Dockerfile)                 | Build de la imagen del backend                        |
| [Dockerfile](frontend/Dockerfile)        | Build de la imagen del frontend                       |
| [Dockerfile](infra/keycloak/Dockerfile)  | Build de la imagen de Keycloak                        |
| [Dockerfile](infra/jenkins/Dockerfile)   | Build de la imagen del agente Jenkins                 |


#### Cómo está implementado

**Entrada:** [`docker-compose.yml`](docker-compose.yml) — orquesta todo el stack local.

**Imágenes que construye / referencia:**

- API → [`Dockerfile`](Dockerfile)
- FE → [`frontend/Dockerfile`](frontend/Dockerfile)
- Keycloak → [`infra/keycloak/Dockerfile`](infra/keycloak/Dockerfile)
- (pipeline) Jenkins agente → [`infra/jenkins/Dockerfile`](infra/jenkins/Dockerfile)

**Servicios que cuelgan del compose:** postgres, keycloak, api, frontend, prometheus, grafana, tempo, loki, alloy, alertmanager, jenkins…

### 9.2 Gestión de productos

CRUD con paginación, búsqueda y filtros.

[http://localhost:3000/products](http://localhost:3000/products) (Productos) · [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (Swagger)


| Archivo                                                                                              | Descripción                                  |
| ---------------------------------------------------------------------------------------------------- | -------------------------------------------- |
| [ProductController.java](src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java)    | Endpoints REST del CRUD de productos         |
| [ProductService.java](src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java) | Reglas de negocio (SKU único, precio, stock) |
| [CategoryController.java](src/main/java/icc354/pucmm/proyectoqa/controller/CategoryController.java)  | Endpoints para listar categorías             |
| [Products.tsx](frontend/src/pages/Products.tsx)                                                      | Pantalla de listado y acciones de productos  |
| [ProductForm.tsx](frontend/src/components/ProductForm.tsx)                                           | Formulario de alta y edición de productos    |


#### Cómo está implementado

**Entrada (UI):** `[Products.tsx](frontend/src/pages/Products.tsx)` + `[ProductForm.tsx](frontend/src/components/ProductForm.tsx)` (ruta `/products` en `[App.tsx](frontend/src/App.tsx)`).

**Cliente HTTP:** `[products.ts](frontend/src/api/products.ts)` / `[categories.ts](frontend/src/api/categories.ts)` → `[client.ts](frontend/src/api/client.ts)` (Bearer).

**Árbol hacia atrás (API):**

- `[ProductController](src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java)` `/api/v1/products` → `[ProductService](src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java)` → repo → tabla `products`
- `[CategoryController](src/main/java/icc354/pucmm/proyectoqa/controller/CategoryController.java)` `/api/v1/categories` → categorías
- Permisos: `product:view` / `product:manage` (`@PreAuthorize`); UI filtra con `[permissions.ts](frontend/src/auth/permissions.ts)`

### 9.3 Control de stock

Entradas/salidas, alertas de mínimo, historial y auditoría Envers.

[http://localhost:3000/stock](http://localhost:3000/stock) (Stock) · [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (Swagger / Audit)


| Archivo                                                                                                     | Descripción                                     |
| ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| [StockController.java](src/main/java/icc354/pucmm/proyectoqa/controller/StockController.java)               | Endpoints para registrar y listar movimientos   |
| [ProductStockController.java](src/main/java/icc354/pucmm/proyectoqa/controller/ProductStockController.java) | Historial de stock de un producto concreto      |
| [StockService.java](src/main/java/icc354/pucmm/proyectoqa/application/service/StockService.java)            | Cálculo IN/OUT y rechazo por stock insuficiente |
| [Stock.tsx](frontend/src/pages/Stock.tsx)                                                                   | Pantalla de movimientos y alertas de mínimo     |
| [Product.java](src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java)                            | Entidad del producto con auditoría Envers       |
| [AuditController.java](src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java)               | Endpoint del historial de cambios del producto  |
| [AuditService.java](src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java)            | Lee revisiones Envers desde `products_audit`    |


#### Cómo está implementado

**Entrada (movimientos):** `[Stock.tsx](frontend/src/pages/Stock.tsx)` → `[stock.ts](frontend/src/api/stock.ts)`.

**Árbol API stock:**

- `[StockController](src/main/java/icc354/pucmm/proyectoqa/controller/StockController.java)` `/api/v1/stock/movements` → `[StockService](src/main/java/icc354/pucmm/proyectoqa/application/service/StockService.java)` → actualiza `Product` + inserta `StockMovement`
- `[ProductStockController](src/main/java/icc354/pucmm/proyectoqa/controller/ProductStockController.java)` `/api/v1/products/{id}/stock/history`

**Entrada (auditoría Envers):** botón Historial en productos → `[audit.ts](frontend/src/api/audit.ts)` → `[AuditController](src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java)` → `[AuditService](src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java)` lee `products_audit` porque `[Product.java](src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java)` está `@Audited`.

### 9.4 API empresarial

REST documentada con OpenAPI.

[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (Swagger) · [http://localhost:3000/dashboard](http://localhost:3000/dashboard) (Dashboard)


| Archivo                                                                                            | Descripción                                     |
| -------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| [OpenApiConfig.java](src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java)              | Configuración de Swagger / OpenAPI              |
| [application.yml](src/main/resources/application.yml)                                              | Paths de springdoc, Actuator y Flyway           |
| [ReportController.java](src/main/java/icc354/pucmm/proyectoqa/controller/ReportController.java)    | Endpoints de resumen, low-stock y top productos |
| [ReportService.java](src/main/java/icc354/pucmm/proyectoqa/application/service/ReportService.java) | Calcula los KPIs que consume el dashboard       |


#### Cómo está implementado

**Entrada (docs):** `[OpenApiConfig.java](src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java)` + paths springdoc en `[application.yml](src/main/resources/application.yml)` → UI `/swagger-ui.html`.

**Entrada (reportes que alimentan el dashboard):** `[ReportController](src/main/java/icc354/pucmm/proyectoqa/controller/ReportController.java)` `/api/v1/reports/`* → `[ReportService](src/main/java/icc354/pucmm/proyectoqa/application/service/ReportService.java)`.

**Quién consume esos reportes:** `[reports.ts](frontend/src/api/reports.ts)` ← `[Dashboard.tsx](frontend/src/pages/Dashboard.tsx)`. En `prod`, Swagger se reduce vía `[application-prod.yml](src/main/resources/application-prod.yml)`.

### 9.5 Interfaz de usuario

Tablero con productos críticos, top y métricas.

[http://localhost:3000](http://localhost:3000) (Frontend) · [http://localhost:3000/dashboard](http://localhost:3000/dashboard) (Dashboard)


| Archivo                                           | Descripción                           |
| ------------------------------------------------- | ------------------------------------- |
| [Dashboard.tsx](frontend/src/pages/Dashboard.tsx) | Tablero con KPIs y productos críticos |
| [App.tsx](frontend/src/App.tsx)                   | Definición de rutas de la aplicación  |
| [Landing.tsx](frontend/src/pages/Landing.tsx)     | Pantalla de entrada / login           |


#### Cómo está implementado

**Entrada:** `[App.tsx](frontend/src/App.tsx)` — define todas las rutas y envuelve con `[ProtectedRoute](frontend/src/components/ProtectedRoute.tsx)`.

**Árbol de pantallas que cuelga:**

- `[Landing.tsx](frontend/src/pages/Landing.tsx)` / login → auth Keycloak
- `[Products.tsx](frontend/src/pages/Products.tsx)` → productos
- `[Stock.tsx](frontend/src/pages/Stock.tsx)` → stock
- `[Dashboard.tsx](frontend/src/pages/Dashboard.tsx)` → KPIs (`report:view`)
- Nav: `[AppHeader.tsx](frontend/src/components/AppHeader.tsx)` muestra/oculta links según `[permissions.ts](frontend/src/auth/permissions.ts)`

---

## 10. Referencia rápida


| Servicio   | URL                                                                            | Credenciales      |
| ---------- | ------------------------------------------------------------------------------ | ----------------- |
| Frontend   | [http://localhost:3000](http://localhost:3000)                                 | `admin` / `admin` |
| Productos  | [http://localhost:3000/products](http://localhost:3000/products)               | —                 |
| Stock      | [http://localhost:3000/stock](http://localhost:3000/stock)                     | —                 |
| Dashboard  | [http://localhost:3000/dashboard](http://localhost:3000/dashboard)             | —                 |
| Swagger    | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Bearer JWT        |
| Keycloak   | [http://localhost:8081](http://localhost:8081)                                 | `admin` / `admin` |
| Grafana    | [http://localhost:3001](http://localhost:3001)                                 | `admin` / `admin` |
| Prometheus | [http://localhost:9090](http://localhost:9090)                                 | —                 |
| Jenkins    | [http://localhost:8082](http://localhost:8082)                                 | usuario local     |
| Actions    | [GitHub Actions](https://github.com/jeanc24/ProyectoQA/actions)                | —                 |
| SonarCloud | [Proyecto](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)       | —                 |


