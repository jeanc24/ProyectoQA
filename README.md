# ProyectoQA

Sistema de **Gestión de Inventarios Empresarial** — PUCMM, Aseguramiento de Calidad de Software.

Monorepo con API REST (Spring Boot), interfaz web (React), base de datos PostgreSQL, autenticación OAuth2/JWT con Keycloak, auditoría con Hibernate Envers y observabilidad con Prometheus y Grafana.

CI
Conventional Commits

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
| Observabilidad | Spring Actuator, Micrometer, Prometheus, Grafana |


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
| `admin` | `admin` | los 7 permisos | Acceso completo |
| `viewer` | `viewer` | `product:view`, `stock:view` | Solo lectura |
| `stock-manager` | `stock-manager` | `product:view`, `stock:view`, `stock:manage` | Operar stock + ver productos |
| `auditor` | `auditor` | `product:view`, `stock:view`, `audit:view` | Lectura + auditoría |

Los permisos se validan en la API (`@PreAuthorize`) y en el frontend (oculta acciones / rutas según rol).

### Refresh de sesión (JWT)

El frontend usa `keycloak-js`. Antes de cada llamada a la API, [`frontend/src/api/client.ts`](frontend/src/api/client.ts) ejecuta `keycloak.updateToken(30)`: si el access token expira en menos de 30 s, se renueva con el refresh token. Si el refresh falla, [`AuthContext`](frontend/src/auth/AuthContext.tsx) cierra sesión y redirige a `/login`.

---

## Arranque del proyecto

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


| Paso | `admin` | `viewer` | `stock-manager` | `auditor` |
| ---- | ------- | -------- | --------------- | --------- |
| Login en [http://localhost:3000](http://localhost:3000) | ✓ | ✓ | ✓ | ✓ |
| Ver productos | ✓ | ✓ | ✓ | ✓ |
| Crear / editar / eliminar productos | ✓ | ✗ | ✗ | ✗ |
| Ver `/stock` | ✓ | ✓ | ✓ | ✓ |
| Registrar movimiento de stock | ✓ | ✗ | ✓ | ✗ |
| Ver `/dashboard` | ✓ | ✗ | ✗ | ✗ |

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
2. `GET /api/v1/audit/products/{id}` con JWT que tenga `audit:view` (p. ej. usuario `auditor`)
3. Respuesta: lista de revisiones del producto (tablas `products_audit`, `revinfo` en Postgres)

### Observabilidad (métricas y dashboards)

```bash
docker compose up -d api prometheus grafana
```


| Recurso             | URL                                                                                    |
| ------------------- | -------------------------------------------------------------------------------------- |
| Health check        | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)         |
| Métricas Prometheus | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) |
| UI Prometheus       | [http://localhost:9090](http://localhost:9090)                                         |
| Grafana             | [http://localhost:3001](http://localhost:3001) (`admin` / `admin`)                     |


Prometheus scrapea la API cada 15 s (`[infra/prometheus/prometheus.yml](infra/prometheus/prometheus.yml)`).

### Pipeline CI (GitHub Actions)

Cada push o PR a `develop` ejecuta build + unit tests + integration tests.

Reproducir localmente los mismos comandos:

```powershell
# Windows

#Build sin tests
.\gradlew.bat build -x test

#Test unitario
.\gradlew.bat test

#Test de integracion
.\gradlew.bat integrationTest
```

```bash
# Linux / macOS

#Build sin tests
./gradlew build -x test

#Test unitario
./gradlew test

#Test de integracion
./gradlew integrationTest
```

Detalle del workflow, artefacto JaCoCo y Jenkins local: sección [CI](#ci-github-actions) y `[docs/avance-1/ci/README.md](docs/avance-1/ci/README.md)`.

---

## Pruebas automatizadas

### Resumen


| Tipo                  | Comando                                                        | Requisitos                    |
| --------------------- | -------------------------------------------------------------- | ----------------------------- |
| Unitarios (backend)   | `./gradlew test`                                               | JDK 21                        |
| Integración (backend) | `./gradlew integrationTest`                                    | Docker en ejecución           |
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

Usan **Testcontainers** con PostgreSQL real. Tag JUnit: `integration`.

```bash
./gradlew integrationTest
```

Clase de referencia: `src/test/java/.../integration/ProductIntegrationTest.java`

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

Cada **push** o **pull request** hacia `develop` ejecuta el workflow **[CI](https://github.com/jeanc24/ProyectoQA/actions/workflows/ci.yml)**.

Los PR hacia `develop` o `main` también ejecutan **[Conventional Commits](https://github.com/jeanc24/ProyectoQA/actions/workflows/conventional-commits.yml)**.

### Ver el estado del pipeline

1. Pestaña **[Actions](https://github.com/jeanc24/ProyectoQA/actions)** → workflow **CI**
2. Elegir la ejecución (commit o PR)
3. Revisar job **build-and-test**:
  - **Build (sin tests)** — `./gradlew build -x test`
  - **Unit tests** — `./gradlew test`
  - **Integration tests** — `./gradlew integrationTest`


| Resultado | Significado                     |
| --------- | ------------------------------- |
| Verde     | Build y tests pasaron           |
| Rojo      | Revisar el log del step fallido |


### Reporte de cobertura (JaCoCo)

1. Ejecución del workflow en Actions → **Artifacts**
2. Descargar `jacoco-report` → abrir `index.html`

### CI (Jenkins)

```bash
docker compose up -d jenkins   # UI en http://localhost:8082
```

Instrucciones del job y evidencia: **[docs/avance-1/ci/README.md](docs/avance-1/ci/README.md)**

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