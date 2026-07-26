# ProyectoQA

Sistema de **Gestión de Inventarios Empresarial** — PUCMM, Aseguramiento de Calidad de Software.

Monorepo con API REST (Spring Boot), interfaz web (React), base de datos PostgreSQL, autenticación OAuth2/JWT con Keycloak, auditoría con Hibernate Envers y observabilidad con Prometheus y Grafana.

[![DevSecOps](https://github.com/jeanc24/ProyectoQA/actions/workflows/devsecops.yml/badge.svg)](https://github.com/jeanc24/ProyectoQA/actions/workflows/devsecops.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jeanc24_ProyectoQA&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=jeanc24_ProyectoQA&metric=coverage)](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=jeanc24_ProyectoQA&metric=bugs)](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=jeanc24_ProyectoQA&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=jeanc24_ProyectoQA&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=jeanc24_ProyectoQA)

## Stack


| Capa           | Tecnología                                       |
| -------------- | ------------------------------------------------ |
| Backend        | Java 21, Spring Boot 4, Gradle                   |
| Frontend       | React 19, Vite, TypeScript                       |
| Base de datos  | PostgreSQL 16, Flyway, Hibernate Envers          |
| Seguridad      | Keycloak 26, OAuth2 Resource Server, JWT         |
| Infra          | Docker Compose                                   |
| Tests          | JUnit 5, Mockito, Testcontainers, Playwright     |
| CI             | GitHub Actions, Jenkins                          |
| Observabilidad | Spring Actuator, Micrometer, OpenTelemetry, Prometheus, Tempo, Loki, Alloy, Alertmanager, Grafana |


## Índice

- [Prerrequisitos](#prerrequisitos)
- [Inicio rápido](#inicio-rápido)
- [Servicios y puertos](#servicios-y-puertos)
- [Usuarios demo](#usuarios-demo)
- [Arranque del proyecto](#arranque-del-proyecto)
- [Guía por escenario](#guía-por-escenario)
- [Pruebas automatizadas](#pruebas-automatizadas)
- [CI](#ci-github-actions)
- [Troubleshooting](#troubleshooting)
- [Contribuir](#contribuir)

---

## Prerrequisitos


| Herramienta                                   | Versión mínima | Uso                                                    |
| --------------------------------------------- | -------------- | ------------------------------------------------------ |
| [Docker](https://docs.docker.com/get-docker/) | 24+            | Postgres, Keycloak, API, frontend, observabilidad      |
| [Java](https://adoptium.net/)                 | 21             | Backend local (`./gradlew bootRun`) y tests Gradle     |
| [Node.js](https://nodejs.org/)                | 20+            | Frontend en modo desarrollo y E2E                      |
| [Git](https://git-scm.com/)                   | 2.x            | Clonar y contribuir                                    |
| [gh CLI](https://cli.github.com/)             | opcional       | Scripts de issues/labels (`./scripts/github-setup.sh`) |


> Docker debe estar **en ejecución** antes de levantar servicios o correr integration tests (Testcontainers).

---

## Inicio rápido

Cinco pasos para ver la aplicación funcionando en el navegador:

```bash
# 1. Clonar
git clone https://github.com/jeanc24/ProyectoQA.git
cd ProyectoQA

# 2. Levantar infraestructura y aplicación (primera vez tarda por el build)
docker compose up --build -d

# 3. Esperar a que la API esté lista (~1–2 min en el primer arranque)
#    Comprobar: curl http://localhost:8080/actuator/health  →  {"status":"UP"}

# 4. Abrir el frontend
#    http://localhost:3000

# 5. Iniciar sesión con admin / admin → listado de productos en /products
```

Para detener todo: `docker compose down`

---

## Servicios y puertos


| Servicio       | URL / puerto (host)                            | Descripción                                        |
| -------------- | ---------------------------------------------- | -------------------------------------------------- |
| **Frontend**   | [http://localhost:3000](http://localhost:3000) | Interfaz web (contenedor nginx)                    |
| **API**        | [http://localhost:8080](http://localhost:8080) | REST + Actuator + Swagger                          |
| **Keycloak**   | [http://localhost:8081](http://localhost:8081) | IdP OAuth2, realm `inventory`                      |
| **PostgreSQL** | `localhost:5433`                               | BD `inventory` (usuario/contraseña: `inventory`)   |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | Scraping de métricas de la API                     |
| **Grafana**    | [http://localhost:3001](http://localhost:3001) | Dashboards (usuario/contraseña: `admin` / `admin`) |
| **Tempo**      | [http://localhost:3200](http://localhost:3200) | Almacén de trazas (vía Alloy OTLP)                 |
| **Loki**       | [http://localhost:3100](http://localhost:3100) | Almacén de logs                                    |
| **Alloy**      | [http://localhost:12345](http://localhost:12345) | Collector OTLP (4317/4318)                       |
| **Alertmanager** | [http://localhost:9093](http://localhost:9093) | Alertas operacionales (OBS-03)                 |
| **Jenkins**    | [http://localhost:8082](http://localhost:8082) | Pipeline CI local (opcional)                       |


Puertos internos en la red Docker (no expuestos al host): Postgres `5432`, Keycloak `8080`, API `8080`.

Credenciales de **consola Keycloak** (administración del IdP, no de la app): `admin` / `admin` en [http://localhost:8081](http://localhost:8081)

---

## Usuarios demo

Usuarios de la aplicación importados desde [`keycloak/inventory-realm.json`](keycloak/inventory-realm.json).  
Los “roles” de app son **permisos granulares** del cliente `inventory-api` (no un rol único tipo “Administrador”).

### Matriz de permisos (7)

| Permiso | Descripción |
| ------- | ----------- |
| `product:view` | Ver productos / categorías |
| `product:manage` | Crear, editar y eliminar productos |
| `stock:view` | Ver historial y niveles de stock |
| `stock:manage` | Registrar entradas, salidas y ajustes |
| `report:view` | Dashboard y reportes |
| `audit:view` | Historial de auditoría (Envers) |
| `user:manage` | Gestión de usuarios (reservado admin) |

### Usuarios

| Usuario | Contraseña | Permisos (`inventory-api`) | Qué puede hacer |
| ------- | ---------- | -------------------------- | --------------- |
| `admin` | `admin` | los 7 permisos | Acceso completo (incluye historial Envers) |
| `viewer` | `viewer` | `product:view`, `stock:view` | Solo lectura |
| `stock-manager` | `stock-manager` | `product:view`, `stock:view`, `stock:manage` | Operar stock + ver productos |

Los permisos se validan en la API (`@PreAuthorize`) y en el frontend (oculta acciones / rutas según rol).

### Refresh de sesión (JWT)

El frontend usa `keycloak-js`. Antes de cada llamada a la API, [`frontend/src/api/client.ts`](frontend/src/api/client.ts) ejecuta `keycloak.updateToken(30)`: si el access token expira en menos de 30 s, se renueva con el refresh token. Si el refresh falla, [`AuthContext`](frontend/src/auth/AuthContext.tsx) cierra sesión y redirige a `/login`.

---

## Arranque del proyecto

### Staging (ENV-01)

Réplica del stack (Postgres, Keycloak, API, frontend, Tempo/Loki/Alloy, Prometheus, Grafana, Alertmanager) con **perfil Spring `staging`**, puertos distintos al desarrollo local y secretos solo en `.env.staging` (no versionado).

```bash
cp .env.staging.example .env.staging   # editar passwords
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build
```

| Servicio   | URL (defaults del example)      |
| ---------- | ------------------------------- |
| Frontend   | http://localhost:3008           |
| API        | http://localhost:8088           |
| Keycloak   | http://localhost:8181           |
| Grafana    | http://localhost:3011           |
| Prometheus | http://localhost:9091           |

Health: `curl -sf http://localhost:8088/actuator/health`

Parar / borrar volúmenes staging:

```bash
docker compose -f docker-compose.staging.yml --env-file .env.staging down
# con volúmenes:  ... down -v
```

Archivos: [`docker-compose.staging.yml`](docker-compose.staging.yml), [`.env.staging.example`](.env.staging.example), [`application-staging.yml`](src/main/resources/application-staging.yml).

> Si Keycloak ya tenía el realm importado sin el redirect `http://localhost:3008/*`, recrea el contenedor Keycloak del proyecto `inventory-staging` tras actualizar `keycloak/inventory-realm.json`.

### Production (ENV-03)

Perfil Spring **`prod`** endurecido (sin Swagger, actuator mínimo, logging WARN, sampling OTel 0.1) y compose opcional `docker-compose.prod.yml`. Secretos solo en `.env.production` (gitignored). Comparativa staging vs prod: [`docs/final/ci/ENVIRONMENTS.md`](docs/final/ci/ENVIRONMENTS.md).

```bash
cp .env.production.example .env.production   # cambiar todos los change-me-*
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
```

| Servicio | URL (defaults)            |
| -------- | ------------------------- |
| Frontend | http://localhost:3009     |
| API      | http://localhost:8089     |
| Keycloak | http://localhost:8182     |
| Grafana  | http://127.0.0.1:3012     |

```bash
curl -sf http://localhost:8089/actuator/health
docker compose -f docker-compose.prod.yml --env-file .env.production down
```

Archivos: [`docker-compose.prod.yml`](docker-compose.prod.yml), [`.env.production.example`](.env.production.example), [`application-prod.yml`](src/main/resources/application-prod.yml).

> Esto es una plantilla **production-like** para demo. Un deploy real exige HTTPS, Keycloak en modo producción y secrets fuera del repo (detalle en `ENVIRONMENTS.md`).

### Post-deploy tests (ENV-02)

Tras levantar staging, validar el sistema **desplegado** (no solo el build):

```bash
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 FRONTEND_URL=http://localhost:3008 \
  ./scripts/wait-for-stack.sh
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 \
  ./scripts/post-deploy-smoke.sh
```

Guía completa: [`docs/final/ci/post-deploy-tests.md`](docs/final/ci/post-deploy-tests.md).  
CI: job *Deploy staging · smoke · E2E* en el workflow **DevSecOps Pipeline** (también `workflow_dispatch` en `post-deploy-staging.yml`).

### Opción A — Stack completo con Docker (recomendado)

Levanta Postgres, Keycloak, API, frontend y (si se incluyen) Prometheus y Grafana:

```bash
docker compose up --build -d
```

Solo servicios esenciales para la app:

```bash
docker compose up --build -d postgres keycloak api frontend
```

Observabilidad adicional:

```bash
docker compose up -d prometheus grafana
```

Tras cambiar código del **frontend** en Docker:

```bash
docker compose up --build frontend
```

### Opción B — Desarrollo local del frontend

Backend e infra en Docker; frontend con hot-reload de Vite:

```bash
docker compose up -d postgres keycloak api
cd frontend
cp .env.example .env    # solo la primera vez
npm install
npm run dev
```

App en [http://localhost:5173](http://localhost:5173) (variables en `[.env.example](frontend/.env.example)`).

### Opción C — API con Gradle (sin contenedor de API)

Útil para depurar el backend. Requiere Postgres y Keycloak arriba:

```bash
docker compose up -d postgres keycloak
```

```powershell
# Windows
$env:CORS_ORIGINS="http://localhost:5173,http://localhost:3000"
.\gradlew.bat bootRun --args="--spring.profiles.active=docker"
```

```bash
# Linux / macOS
CORS_ORIGINS=http://localhost:5173,http://localhost:3000 ./gradlew bootRun --args="--spring.profiles.active=docker"
```

API en [http://localhost:8080](http://localhost:8080)

---

## Guía por escenario

Busca el escenario que necesites reproducir y sigue los pasos en orden.

### Ver la aplicación en el navegador

1. [Inicio rápido](#inicio-rápido) o `docker compose up --build -d`
2. Abrir [http://localhost:3000](http://localhost:3000)
3. Login con `admin` / `admin`

### Autenticación OAuth2 / JWT (login web)

1. Stack arriba (mínimo: `postgres`, `keycloak`, `api`, `frontend`)
2. [http://localhost:3000/login](http://localhost:3000/login) → botón de inicio de sesión
3. Redirección a Keycloak (`localhost:8081`) → credenciales demo
4. Tras login exitoso → redirección a `/products` con JWT en el cliente

Flujo cubierto por E2E: `frontend/e2e/helpers/login.spec.ts`

### Permisos por usuario (smoke UI)


| Paso | `admin` | `viewer` | `stock-manager` |
| ---- | ------- | -------- | --------------- |
| Login en [http://localhost:3000](http://localhost:3000) | ✓ | ✓ | ✓ |
| Ver productos | ✓ | ✓ | ✓ |
| Crear / editar / eliminar productos | ✓ | ✗ | ✗ |
| Historial de auditoría (Envers) | ✓ | ✗ | ✗ |
| Ver `/stock` | ✓ | ✓ | ✓ |
| Registrar movimiento de stock | ✓ | ✗ | ✓ |
| Ver `/dashboard` | ✓ | ✗ | ✗ |

Para probar otro usuario: cerrar sesión y volver a entrar (p. ej. `viewer` / `viewer`).

### CRUD de productos (interfaz web)

1. Login como `admin`
2. En `/products`, clic en **Nuevo producto**
3. Completar nombre, SKU, precio, cantidad, stock mínimo → guardar
4. Verificar que la fila aparece en la tabla
5. Editar o eliminar desde las acciones de la fila

Flujo E2E de creación: `frontend/e2e/helpers/products.spec.ts`

### API REST con Swagger

Con la API en marcha (perfil `docker`):

1. Abrir [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
2. Obtener un token JWT (ver siguiente escenario)
3. En Swagger → **Authorize** → `Bearer <access_token>`
4. Probar endpoints bajo **Products** y **Audit**

Endpoints principales:


| Método   | Ruta                          | Permiso          |
| -------- | ----------------------------- | ---------------- |
| `GET`    | `/api/v1/products`            | `product:view`   |
| `GET`    | `/api/v1/products/{id}`       | `product:view`   |
| `POST`   | `/api/v1/products`            | `product:manage` |
| `PUT`    | `/api/v1/products/{id}`       | `product:manage` |
| `DELETE` | `/api/v1/products/{id}`       | `product:manage` |
| `GET`    | `/api/v1/audit/products/{id}` | `audit:view`     |


### Obtener JWT para curl o Swagger

```bash
curl -s -X POST "http://localhost:8081/realms/inventory/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=inventory-api" \
  -d "client_secret=inventory-api-secret" \
  -d "username=admin" \
  -d "password=admin"
```

Copiar `access_token` del JSON. Ejemplo — listar productos:

```bash
curl -s http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <access_token>"
```

Sin token → `401`. Con `viewer` en operaciones `product:manage` → `403`.

### Auditoría Envers (historial de cambios)

1. Crear o editar un producto como `admin`
2. `GET /api/v1/audit/products/{id}` con JWT que tenga `audit:view` (usuario `admin`), o botón **Historial** en `/products`
3. Respuesta: lista de revisiones del producto (tablas `products_audit`, `revinfo` en Postgres)

### Observabilidad (métricas, trazas y logs)

```bash
# Stack app + observabilidad (OBS-01/02)
docker compose up -d --build postgres keycloak api alloy tempo loki prometheus alertmanager grafana
```

| Recurso | URL |
| ------- | --- |
| Health check | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| Métricas Prometheus | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) |
| UI Prometheus | [http://localhost:9090](http://localhost:9090) |
| Grafana | [http://localhost:3001](http://localhost:3001) (`admin` / `admin`) |
| Tempo (query) | [http://localhost:3200](http://localhost:3200) |
| Loki | [http://localhost:3100](http://localhost:3100) |
| Alloy UI | [http://localhost:12345](http://localhost:12345) |
| OTLP HTTP (Alloy) | `http://localhost:4318` |
| Alertmanager | [http://localhost:9093](http://localhost:9093) |

Prometheus scrapea la API cada 15 s (`[infra/prometheus/prometheus.yml](infra/prometheus/prometheus.yml)`).

### OpenTelemetry (trazas + métricas OTLP)

La API está instrumentada con **OpenTelemetry** vía Micrometer (`spring-boot-starter-opentelemetry`):

- **Spans HTTP**: cada request entra como un trace (Micrometer Observation).
- **Spans JDBC/JPA**: queries y fetch instrumentados con `datasource-micrometer` (propiedad `jdbc.includes`).
- **Errores**: las excepciones quedan marcadas en el span correspondiente.
- **Logs correlacionados**: cada línea incluye `[traceId,spanId]` (`logging.pattern.correlation`).

Export por **OTLP http/protobuf** hacia **Grafana Alloy** (puerto 4318). Alloy reenvía:

- **Trazas → Tempo** (`infra/tempo/tempo.yml`)
- **Logs → Loki** (`infra/loki/loki.yml`), además scrapea logs Docker de `inventory-api`

Variables de entorno (ver `docker-compose.yml`):

| Variable | Default | Uso |
| -------- | ------- | --- |
| `OTEL_TRACES_SAMPLING` | `1.0` | % de requests muestreados (1.0 = todos) |
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | `http://alloy:4318/v1/traces` | Destino de trazas (Alloy) |
| `OTEL_METRICS_EXPORT_ENABLED` | `false` | Activa export de métricas por OTLP (Prometheus scrape sigue activo) |
| `OTEL_METRICS_EXPORT_URL` | `http://alloy:4318/v1/metrics` | Destino de métricas OTLP |

En Grafana (datasources provisionados): **Prometheus**, **Tempo** y **Loki**, con enlace Trace ↔ Log vía `traceId`.

### Cómo verificar trazas y logs

1. Genera tráfico autenticado:
   ```bash
   TOKEN=$(curl -s -X POST "http://localhost:8081/realms/inventory/protocol/openid-connect/token" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=password&client_id=inventory-api&client_secret=inventory-api-secret&username=admin&password=admin" \
     | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
   curl -sf -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/products?size=5" >/dev/null
   ```
2. Grafana → Explore → **Tempo**: Search por service `inventory-api` (o `proyecto-qa`).
3. Grafana → Explore → **Loki**: `{job="inventory-api"}` — busca líneas con `[traceId,spanId]`.
4. Ya no deberían aparecer `UnknownHostException: alloy` en los logs de la API.

### Alertas (Alertmanager — OBS-03)

Prometheus evalúa reglas en [`infra/prometheus/alerts.yml`](infra/prometheus/alerts.yml) y envía alertas a Alertmanager ([http://localhost:9093](http://localhost:9093)).

| Alerta | Condición | Severidad |
| ------ | --------- | --------- |
| `HighProcessCpu` | `process_cpu_usage` > 80% por 2m | warning |
| `HighJvmHeapMemory` | heap used/max > 85% por 2m | warning |
| `InventoryApiDown` | `up{job="inventory-api"} == 0` por 1m | critical |
| `HighHttp5xxErrorRate` | ratio 5xx > 5% (5m) por 2m | critical |
| `HighHttpLatencyP95` | p95 HTTP > 2s por 3m | warning |
| `AuthFailureSpike401` | tasa de 401 > 0.2 req/s por 2m | warning |

```bash
docker compose up -d alertmanager prometheus

# Reglas cargadas
curl -sf http://localhost:9090/api/v1/rules | python3 -m json.tool | head -40

# Disparar InventoryApiDown (evidencia para el PDF)
docker compose stop api
# espera ~1–2 min → http://localhost:9093/#/alerts
# Prometheus → http://localhost:9090/alerts
docker compose start api
```

### Pipeline CI (GitHub Actions)

Cada push o PR a `develop` ejecuta el **DevSecOps Pipeline** (build → unit → integration → API → contract → Sonar → Docker → security → staging E2E → quality gate). Guía: [`docs/final/ci/PIPELINE.md`](docs/final/ci/PIPELINE.md).

Reproducir localmente los stages de test:

```powershell
# Windows
.\gradlew.bat build -x test
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat apiTest
.\gradlew.bat contractTest
.\gradlew.bat jacocoTestReport
```

```bash
# Linux / macOS
./gradlew build -x test
./gradlew test
./gradlew integrationTest
./gradlew apiTest
./gradlew contractTest
./gradlew jacocoTestReport
```

Detalle, artefacto JaCoCo y Jenkins local: sección [CI](#ci-github-actions) y [`docs/avance-1/ci/README.md`](docs/avance-1/ci/README.md).

---

## Pruebas automatizadas

### Resumen


| Tipo                  | Comando                                                        | Requisitos                    |
| --------------------- | -------------------------------------------------------------- | ----------------------------- |
| Unitarios (backend)   | `./gradlew test`                                               | JDK 21                        |
| Integración (backend) | `./gradlew integrationTest`                                    | Docker en ejecución           |
| Performance (k6)      | `./scripts/k6-run.sh load` / `stress`                          | API :8080 + Docker            |
| Seguridad (ZAP + DC)  | `./scripts/zap-baseline.sh` / `./scripts/dependency-check.sh`  | API :8080 (ZAP) / JDK 21 (DC) |
| E2E (frontend)        | `cd frontend && npm run test:e2e`                              | Stack Docker + API en `:8080` |
| Cobertura JaCoCo      | `./gradlew test` → `build/reports/jacoco/test/html/index.html` | —                             |


### Tests unitarios (backend)

Excluyen tests con tag `integration`. Cubren servicios, controladores (MockMvc) y manejo de excepciones.

```bash
./gradlew test
```

Windows: `.\gradlew.bat test`

Umbral de cobertura: **60 %** en líneas de `icc354.pucmm.proyectoqa.application.service.`* (ver `[build.gradle](build.gradle)`).

### Tests de integración (backend)

Usan **Testcontainers** (requieren Docker). Tag JUnit: `integration`.

```bash
./gradlew integrationTest
```

| Tipo | Clase | Qué prueba |
|------|-------|------------|
| Persistencia | `ProductIntegrationTest`, `StockIntegrationTest`, … | Flyway, CRUD, stock, reportes, integridad |
| Seguridad Keycloak (TEST-01) | `KeycloakSecurityIntegrationTest` | Token real JWT → API → 401/403/200 |

`KeycloakSecurityIntegrationTest` levanta Keycloak (`quay.io/keycloak/keycloak:26.0`) importando `keycloak/inventory-realm.json`, activa el perfil `docker` (OAuth2 resource server) y valida permisos granulares con usuarios demo (`viewer`, `admin`). Los demás IT siguen solo con Postgres (perfil `integration`) para no pagar el costo de Keycloak en cada clase.

### Security testing (TEST-03) — ZAP + Dependency-Check

En CI: jobs *OWASP Dependency-Check* y *OWASP ZAP baseline* del **DevSecOps Pipeline** (también manual vía [`.github/workflows/security.yml`](.github/workflows/security.yml)). Reportes en `docs/final/testing/zap/` y `docs/final/testing/dependency-check/`.

```bash
# 1) API con seguridad real (perfil docker)
docker compose up -d --build postgres keycloak tempo loki alloy api

# 2) Evidencia JWT / CORS / permisos
./scripts/security-smoke.sh

# 3) OWASP ZAP baseline → docs/final/testing/zap/zap-report.html
./scripts/zap-baseline.sh

# 4) OWASP Dependency-Check
#    Local rápido (si ya hay DB NVD válida): ./scripts/dependency-check.sh
#    Sync NVD completa (≥10 GB libres / CI):
#    DEPENDENCY_CHECK_AUTO_UPDATE=true ./scripts/dependency-check.sh
./scripts/dependency-check.sh
```

### Performance (TEST-04) — k6 load / stress

Scripts en `tests/k6/`. Reportes en `docs/final/testing/k6/`.

| Escenario | Umbral p95 | Error rate |
|-----------|------------|------------|
| Load (`load-products.js`) | &lt; 500 ms | &lt; 1% |
| Stress (`stress-products.js`) | &lt; 2000 ms | &lt; 5% |

```bash
docker compose up -d --build postgres keycloak tempo loki alloy api
./scripts/k6-run.sh load
./scripts/k6-run.sh stress
```

### Tests E2E (Playwright)

Corren contra el frontend en **[http://localhost:3000](http://localhost:3000)** (contenedor Docker), no contra `npm run dev`.

```bash
# 1. Stack completo
docker compose up --build -d postgres keycloak api frontend

# 2. Instalar dependencias (primera vez)
cd frontend
npm install
npx playwright install chromium

# 3. Ejecutar
cd frontend
npm run test:e2e
```

Variantes:

```bash
npm run test:e2e:ui          # modo interactivo
npx playwright show-report   # tras fallo, si se generó reporte HTML
```

Escenarios automatizados:


| Test                      | Archivo                        | Qué verifica         |
| ------------------------- | ------------------------------ | -------------------- |
| Login admin → `/products` | `e2e/helpers/login.spec.ts`    | OAuth2 + redirección |
| Crear producto en tabla   | `e2e/helpers/products.spec.ts` | CRUD UI flujo feliz  |


---

## CI (GitHub Actions)

Cada **push** o **pull request** hacia `develop` ejecuta **[DevSecOps Pipeline](https://github.com/jeanc24/ProyectoQA/actions/workflows/devsecops.yml)** (CICD-01). Documento: [`docs/final/ci/PIPELINE.md`](docs/final/ci/PIPELINE.md).

Incluye: build, unit, integration, API (`apiTest`), contract (`contractTest`), SonarCloud + JaCoCo, Docker build API/frontend, OWASP Dependency-Check + ZAP, deploy staging (compose) + smoke + Playwright E2E, y un job **Quality gate** que falla si cualquier stage falla.

Los workflows `CI`, `Security` y `Post-deploy staging` quedan en **manual** (`workflow_dispatch`) para depurar un trozo. Opcional: secreto `NVD_API_KEY`.

Los PR hacia `develop` o `main` también ejecutan **[Conventional Commits](https://github.com/jeanc24/ProyectoQA/actions/workflows/conventional-commits.yml)**.

**SonarCloud (SONAR-01):** `./gradlew sonar` con JaCoCo y `sonar.qualitygate.wait=true`. Secret `SONAR_TOKEN`. Guía: [`docs/final/quality/SONARCLOUD.md`](docs/final/quality/SONARCLOUD.md).

### Ver el estado del pipeline

1. Pestaña **[Actions](https://github.com/jeanc24/ProyectoQA/actions)** → **DevSecOps Pipeline**
2. Elegir la ejecución (commit o PR)
3. Revisar jobs y el **Quality gate** final


| Resultado | Significado                                      |
| --------- | ------------------------------------------------ |
| Verde     | Todos los stages + quality gate OK               |
| Rojo      | Revisar el job/step fallido (tests, security, E2E) |


### Reporte de cobertura (JaCoCo)

1. Ejecución del workflow en Actions → **Artifacts**
2. Descargar `jacoco-report` → abrir `index.html`

### CI (Jenkins)

```bash
docker compose build jenkins
docker compose up -d jenkins   # UI en http://localhost:8082
```

Pipeline completo (CICD-02) y paridad con GHA: **[docs/final/ci/JENKINS.md](docs/final/ci/JENKINS.md)**  
Guía avance 1: **[docs/avance-1/ci/README.md](docs/avance-1/ci/README.md)**

---

## Troubleshooting

### Keycloak no está listo / login falla

- El realm `inventory` se importa al arrancar (`start-dev --import-realm`). Espera 30–60 s tras `docker compose up`.
- Comprobar: [http://localhost:8081/realms/inventory](http://localhost:8081/realms/inventory)
- Si cambiaste `keycloak/inventory-realm.json`, recrea el contenedor:  
`docker compose up --build -d keycloak`

### Puerto ocupado


| Puerto | Servicio | Acción                                                       |
| ------ | -------- | ------------------------------------------------------------ |
| 3000   | Frontend | `docker compose down` o detener el proceso que use el puerto |
| 8080   | API      | Idem                                                         |
| 8081   | Keycloak | Idem                                                         |
| 5433   | Postgres | Idem                                                         |


En Windows: `netstat -ano | findstr :8080` → terminar PID con `taskkill /PID <pid> /F`

### Error CORS en el navegador

La API lee orígenes permitidos desde `CORS_ORIGINS`. Debe incluir el origen del frontend:

- Docker frontend: `http://localhost:3000`
- Vite dev: `http://localhost:5173`

En `docker-compose.yml` ya están ambos. Con `bootRun` local, exporta la variable antes de arrancar (ver [Opción C](#opción-c--api-con-gradle-sin-contenedor-de-api)).

### API en `DOWN` o healthcheck falla

```bash
docker compose logs api --tail 50
```

Causas frecuentes: Postgres no healthy, Keycloak aún iniciando, primera compilación del imagen incompleta. Reintentar:

```bash
docker compose up --build -d api
```

### Integration tests fallan

- Verificar que **Docker Desktop** (o el daemon) está corriendo.
- En CI/Jenkins dentro de contenedor: variables `TESTCONTAINERS_`* (ver `[docs/avance-1/ci/README.md](docs/avance-1/ci/README.md)`).

### E2E fallan

- Confirmar frontend en **:3000** (`docker compose ps`)
- Confirmar API healthy: `curl http://localhost:8080/actuator/health`
- Primera vez: `npx playwright install chromium`

---

## Contribuir

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Conventional Commits, ramas, flujo de PR
- **[PLAN-PASO-A-PASO.txt](PLAN-PASO-A-PASO.txt)** — Plan del sprint y checklist del avance
- **[frontend/README.md](frontend/README.md)** — Notas específicas del frontend

### Setup local (una vez)

```bash
git config core.hooksPath .githooks
chmod +x .githooks/commit-msg

# Labels, milestone e issues (requiere gh CLI)
./scripts/github-setup.sh
```

### Ramas

`main` (protegida) ← `develop` ← `feature/*`

### Conventional Commits

```
feat(api): add product CRUD with validations
fix(security): allow CORS from frontend origin
test: add integration tests with testcontainers
docs: expand README with setup instructions
```

Tipos: `feat` · `fix` · `test` · `docs` · `chore` · `ci` · `refactor` · `perf` · `style`