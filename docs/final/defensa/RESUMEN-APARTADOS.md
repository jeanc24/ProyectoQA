# Resumen por apartado — ProyectoQA

Texto corto (1–2 párrafos por bloque) de **qué tenemos** en cada área del proyecto. Para archivos y comandos: [`ARBOL.md`](ARBOL.md). Para detalle oral: [`README.md`](README.md).

---

## 1. Seguridad (Keycloak + OAuth2 / JWT)

La identidad y los permisos no viven en Postgres: Keycloak 26 es el IdP, con el realm `inventory` versionado en `keycloak/inventory-realm.json` (clients `inventory-frontend` / `inventory-api`, siete roles tipo `product:view` / `product:manage`, usuarios demo). El frontend inicia login con `keycloak-js` (PKCE); la API es un **OAuth2 Resource Server** (`DockerSecurityConfig`) que valida el JWT con `issuer-uri` y JWKS definidos en `application-docker.yml` (y análogos staging/prod). Las altas de usuario y la asignación de roles se hacen en la consola de Keycloak; la app solo lista usuarios en lectura vía Admin API.

En la práctica: sin Bearer válido → 401; autenticado sin el permiso del endpoint (`@PreAuthorize`) → 403. Hay smoke de JWT/CORS/permisos (`scripts/security-smoke.sh`), ZAP baseline y, en UI, rutas protegidas (`ProtectedRoute`) que redirigen a home o `/unauthorized` según sesión y rol.

---

## 2. Testing

Cubrimos varias capas: unitarios y de servicio (JUnit/Mockito), escenarios de API con MockMvc (`apiTest`), contratos OpenAPI (`contractTest`), integración con Testcontainers (Postgres + Keycloak reales), E2E Playwright contra el frontend, performance con k6 (load/stress), seguridad con ZAP y Dependency-Check, más charters exploratorios documentados. Los resultados salen en reportes HTML de Gradle, `playwright-report`, summaries k6 y evidencias en `docs/final/testing/`.

No todo corre en el mismo sitio: el quality gate de GitHub Actions (DevSecOps) concentra build, unit/integration/API/contract, Sonar, imágenes, Dependency-Check, ZAP, staging Compose + smoke + Playwright; k6 y Jenkins son refuerzo local o bajo demanda. Post-deploy smoke valida un stack ya levantado (Compose staging o cloud).

---

## 3. QA / calidad (JaCoCo + SonarCloud)

JaCoCo mide cobertura sobre los tests Gradle y genera el HTML en `build/reports/jacoco/`. SonarCloud analiza calidad (bugs, smells, vulnerabilidades, cobertura, quality gate) con el plugin Gradle y el token en CI; el badge del README refleja el estado del proyecto.

El umbral del gate (p. ej. cobertura de servicios) está pensado para bloquear merge cuando baja la calidad: en DevSecOps el análisis corre tras los tests con wait del quality gate. Localmente se puede repetir con `./gradlew test jacocoTestReport sonar`.

---

## 4. Backend (Spring Boot, JPA, Flyway, Envers, Swagger)

API REST Java 21 / Spring Boot en capas (controller → service → repository → dominio): productos, categorías, stock, reportes y auditoría. Persistencia con Spring Data JPA/Hibernate sobre PostgreSQL; el esquema lo versiona Flyway (`db/migration/V*.sql`) con `ddl-auto: validate`. Los cambios de producto quedan auditados con Hibernate Envers y se exponen por `/api/v1/audit/...`.

Swagger/OpenAPI documenta y prueba la API (`/swagger-ui.html`) con Bearer JWT. Perfiles Spring (`docker`, `staging`, `prod`, `local`) cambian datasource, seguridad e issuer según el ambiente. Errores de dominio y validación se normalizan en `GlobalExceptionHandler`.

---

## 5. Frontend (React / Vite / TypeScript)

SPA React 19 que consume la API con `fetch` + JWT (`api/client.ts` renueva token con `updateToken`). Pantallas principales: productos (CRUD, filtros, paginación server-side), stock, dashboard de KPIs de inventario y directorio de usuarios (solo lectura). La navegación y los botones respetan permisos del cliente `inventory-api`; sin sesión o sin rol se redirige a landing o unauthorized.

Build con Vite; en Docker se sirve con nginx. En cloud el front va a Vercel; en local Compose o `npm run dev`. Los E2E Playwright ejercitan login Keycloak y flujos de permisos/productos sobre esa misma UI.

---

## 6. Datos (PostgreSQL)

PostgreSQL 16 es la única base de negocio: productos, categorías, movimientos de stock, etc. En Compose local el puerto host suele ser `5433`; en cloud es el Postgres gestionado de Render. Flyway aplica migraciones al arrancar la API; no hay usuarios de negocio en tablas propias (eso es Keycloak).

La app habla con la BD solo vía JPA/repositorios (consultas parametrizadas). HikariCP gestiona el pool; en Grafana Infra se puede ver uso del pool como señal de salud.

---

## 7. Infraestructura (Docker Compose + Cloud)

En local, `docker-compose.yml` levanta API, frontend, Postgres, Keycloak y el stack de observabilidad (y opcionalmente Jenkins). Hay variantes: `docker-compose.staging.yml` (staging efímero de CI, otros puertos), `docker-compose.security.yml` (API+KC+DB aislados para smoke/ZAP en Jenkins) y compose de prod opcional solo para demo local. Variables y secretos van en `.env` / ejemplos, no hardcodeados en el compose de forma sensible.

En la nube el patrón es **Render** (API + Keycloak + Postgres) + **Vercel** (frontend), con blueprints `render.yaml` / `render.prod.yaml` y workflows `deploy-staging.yml` / `deploy-prod.yml` al pushear `develop` / `main`. La observabilidad completa (Grafana/Tempo/Loki) se demuestra en Compose local, no en free tier cloud.

---

## 8. Observabilidad

Con Actuator + Micrometer + OpenTelemetry la API expone métricas (`/actuator/prometheus`) y exporta trazas OTLP a Grafana Alloy, que reparte a Tempo (trazas) y Loki (logs, también scrapea el contenedor `inventory-api`). Prometheus scrapea métricas; Alertmanager tiene reglas; Grafana (`:3001`) es la UI.

Hay seis dashboards provisionados: **Observabilidad** (métricas + logs + trazas juntos, el de demo), **App** (throughput/latencia/errores), **Infra** (CPU/heap/JVM/Hikari), **Security** (401/403), **Negocio** (tráfico por dominio HTTP) y **API Ops** (vista corta del avance). Los spans JDBC aparecen dentro del waterfall de una traza de endpoint de negocio (`datasource-micrometer`), no como filas sueltas de “database”. Para llenar paneles: `./scripts/generate-obs-traffic.sh`.

---

## 9. CI/CD (GitHub Actions + Jenkins)

El pipeline principal de calidad es **DevSecOps** (push/PR a `develop`): build, tests, Sonar, Docker, Dependency-Check, ZAP, staging Compose con smoke + Playwright y quality gate. El **despliegue cloud** va aparte: `deploy-staging` / `deploy-prod`. Jenkins (`Jenkinsfile` + servicio en Compose) espeja stages en local sin publicar a Render/Vercel. Hay workflows legacy/manual (`ci`, `security`, `post-deploy-staging`) como respaldo.

Commits se validan con conventional commits en PR. Artefactos y logs se ven en Actions o en la UI de Jenkins (`:8082`). La idea de defensa: calidad en DevSecOps; publicar staging/prod con los deploy workflows; Jenkins para demo local del mismo flujo.

---

## 10. Documentación de defensa

Además de este resumen: guía maestra (`defensa/README.md`), árbol herramienta→archivo (`ARBOL.md`), banco de preguntas (`PREGUNTAS.md`), guion/demo (`GUIA-DEMO.md`), y entregables PDF (`REQUISITOS`, `TECNICA`, `GUIA-PRUEBAS`) bajo `docs/final/`. CI/ambientes/cloud y catálogo de tests viven en `docs/final/ci/` y `docs/final/testing/`.
