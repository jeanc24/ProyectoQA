# Guía práctica — pruebas, demos de código y requests

Paso a paso para **correr cada tipo de prueba por separado**, **mostrar la implementación en el código** y **probar la API** (Swagger, Postman o curl).

> Catálogo caso a caso: [`../testing/README.md`](../testing/README.md)  
> Herramientas + archivos: [`ARBOL.md`](ARBOL.md)  
> Preguntas de defensa: [`PREGUNTAS.md`](PREGUNTAS.md)  
> **Pipelines (tipos y qué hace cada uno):** [`../ci/PIPELINE.md`](../ci/PIPELINE.md)
> **Compose, ambientes y perfiles `application*.yml`:** [`../ci/ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md)

---

## Índice

1. [Antes de empezar (stack local)](#1-antes-de-empezar-stack-local)
2. [Pruebas automatizadas (una por una)](#2-pruebas-automatizadas-una-por-una)
3. [Calidad: JaCoCo y Sonar](#3-calidad-jacoco-y-sonar)
4. [Requests manuales (Swagger / Postman / curl)](#4-requests-manuales-swagger--postman--curl)
5. [Qué abrir en el código (demos de implementación)](#5-qué-abrir-en-el-código-demos-de-implementación)
6. [UI, permisos y observabilidad](#6-ui-permisos-y-observabilidad)
7. [Pipelines — qué decir si preguntan](#7-pipelines--qué-decir-si-preguntan)
8. [Orden sugerido para la defensa](#8-orden-sugerido-para-la-defensa)

---

## 1. Antes de empezar (stack local)

Desde la raíz del repo:

```bash
cp .env.example .env   # si aún no tienes .env
docker compose --env-file .env up -d --build
```

Espera a que la API esté healthy (~1–2 min la primera vez):

```bash
curl -sf http://localhost:8080/actuator/health
# → {"status":"UP"}
```

| Servicio | URL |
| -------- | --- |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8081 |
| Frontend | http://localhost:3000 |
| Grafana | http://localhost:3001 (`admin` / `admin`) |

**Usuarios demo** (realm `inventory`):

| Usuario | Password | Uso típico |
| ------- | -------- | ---------- |
| `admin` | `admin` | Todo |
| `viewer` | `viewer` | Solo lectura productos/stock |
| `stock-manager` | `stock-manager` | Movimientos de stock |
| `auditor` | `auditor` | Auditoría |

Cliente API (password grant / Postman): `inventory-api` / secret `inventory-api-secret`.

Solo API + auth (sin OBS):

```bash
docker compose up -d --build postgres keycloak api
```

---

## 2. Pruebas automatizadas (una por una)

Ejecuta **cada bloque solo** cuando quieras demostrarlo. No hace falta correr todo junto.

### 2.1 Unitarios (JUnit + Mockito) — sin Docker

```bash
./gradlew test
```

- **Qué prueba:** servicios y WebMvc con mocks (no Postgres real).
- **Dónde están:** `src/test/java/.../service/*Test.java`, `.../controller/ProductControllerTest.java`
- **Mostrar en IDE:** un test verde de `ProductServiceTest`.

### 2.2 Escenarios API (`@Tag("api")`) — sin Keycloak real

```bash
./gradlew apiTest
```

- **Qué prueba:** MockMvc + `@WithMockUser(authorities = …)` (permisos simulados).
- **Dónde:** `ProductApiScenarioTest`, `StockApiScenarioTest`, etc.

### 2.3 Contrato OpenAPI

```bash
./gradlew contractTest
```

- **Dónde:** `OpenApiContractTest.java`

### 2.4 Integración (Testcontainers) — **necesita Docker**

```bash
./gradlew integrationTest
```

- **Qué prueba:** Postgres real (+ Keycloak real en `KeycloakSecurityIntegrationTest`).
- **Mostrar código:** `AbstractIntegrationTest.java`, `AbstractKeycloakIntegrationTest.java`, `KeycloakSecurityIntegrationTest.java`

### 2.5 E2E Playwright — necesita FE + API + Keycloak

Con el stack arriba (al menos frontend en `:3000`):

```bash
cd frontend
npm install          # primera vez
npm run test:e2e
# o interactivo:
npm run test:e2e:ui
```

- **Dónde:** `frontend/e2e/**`, config `frontend/playwright.config.ts`
- **Mostrar:** login real en Chromium + permisos viewer vs admin.

### 2.6 Performance k6 (load / stress)

API + Keycloak arriba:

```bash
./scripts/k6-run.sh load
./scripts/k6-run.sh stress
# o ambos:
./scripts/k6-run.sh all
```

- **Scripts:** `tests/k6/load-products.js`, `tests/k6/stress-products.js`
- **Evidencias:** `docs/final/testing/k6/*-summary.txt`
- **Umbrales:** load p95 &lt; 500 ms; stress 80 VUs, p95 &lt; 2000 ms

### 2.7 Smoke seguridad (JWT / CORS / permisos)

```bash
./scripts/security-smoke.sh
```

### 2.8 Smoke post-deploy

```bash
./scripts/wait-for-stack.sh
./scripts/post-deploy-smoke.sh
```

### 2.9 ZAP (DAST) y Dependency-Check (SCA)

```bash
./scripts/zap-baseline.sh
./scripts/dependency-check.sh
```

Reportes: `docs/final/testing/zap/`, `docs/final/testing/dependency-check/`.

### 2.10 Exploratorio (manual)

Sigue los charters en [`../testing/exploratory/`](../testing/exploratory/).

### Resumen rápido de comandos

| Tipo | Comando | Docker |
| ---- | ------- | ------ |
| Unit | `./gradlew test` | No |
| API scenarios | `./gradlew apiTest` | No |
| Contract | `./gradlew contractTest` | No |
| Integration | `./gradlew integrationTest` | Sí |
| E2E | `cd frontend && npm run test:e2e` | Stack up |
| Load | `./scripts/k6-run.sh load` | API+KC |
| Stress | `./scripts/k6-run.sh stress` | API+KC |
| Security smoke | `./scripts/security-smoke.sh` | API+KC |
| ZAP | `./scripts/zap-baseline.sh` | Sí |
| SCA | `./scripts/dependency-check.sh` | No* |

\* Dependency-Check puede tardar mucho la 1ª vez (NVD).

---

## 3. Calidad: JaCoCo y Sonar

### JaCoCo (reporte HTML local)

```bash
./gradlew test apiTest jacocoTestReport
open build/reports/jacoco/test/html/index.html   # macOS
# o abre el index.html a mano
```

Configuración: bloques `jacoco*` en [`build.gradle`](../../../build.gradle).

### SonarCloud

```bash
export SONAR_TOKEN=...   # no lo pegues en el chat ni lo subas al repo
./gradlew test jacocoTestReport sonar
```

Dashboard: proyecto `jeanc24_ProyectoQA` en SonarCloud.  
En defensa también puedes mostrar el job Sonar del workflow `devsecops.yml`.

---

## 4. Requests manuales (Swagger / Postman / curl)

### 4.1 Obtener un JWT (igual para Postman y curl)

**POST** `http://localhost:8081/realms/inventory/protocol/openid-connect/token`  
Body `x-www-form-urlencoded`:

| Key | Value |
| --- | ----- |
| `grant_type` | `password` |
| `client_id` | `inventory-api` |
| `client_secret` | `inventory-api-secret` |
| `username` | `admin` |
| `password` | `admin` |

Copia `access_token`.

curl:

```bash
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/inventory/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=inventory-api" \
  -d "client_secret=inventory-api-secret" \
  -d "username=admin" \
  -d "password=admin" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "$TOKEN"
```

### 4.2 Swagger UI

1. Abre http://localhost:8080/swagger-ui.html  
2. **Authorize** → pega el token (scheme Bearer).  
3. Prueba:
   - `GET /api/v1/products` → 200  
   - `POST /api/v1/products` (body JSON) → 201 con `admin`  
4. Cierra sesión Authorize, pide token de `viewer` / `viewer`, Authorize de nuevo → `POST` productos → **403**.

### 4.3 Postman (colección mínima)

**Request A — Token** (como § 4.1).

**Request B — Listar productos**

- Method: `GET`
- URL: `http://localhost:8080/api/v1/products?page=0&size=10`
- Authorization → Bearer Token → `{{access_token}}`

**Request C — Crear producto** (admin)

- Method: `POST`
- URL: `http://localhost:8080/api/v1/products`
- Headers: `Authorization: Bearer …`, `Content-Type: application/json`
- Body raw JSON:

```json
{
  "name": "Demo Postman",
  "sku": "DEMO-001",
  "description": "Creado en demo",
  "price": 19.99,
  "quantity": 5,
  "minStock": 2,
  "active": true
}
```

**Request D — Movimiento de stock** (admin o stock-manager)

- `POST http://localhost:8080/api/v1/stock/movements`
- Body ejemplo (ajusta `productId`):

```json
{
  "productId": 1,
  "movementType": "IN",
  "quantity": 3,
  "notes": "Entrada demo"
}
```

**Request E — Sin token**

- Mismo GET productos **sin** Authorization → **401**.

**Request F — Viewer sin permiso**

- Token con `viewer` / `viewer` → POST productos → **403**.

### 4.4 curl rápido (después del TOKEN)

```bash
# Listar
curl -sS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/products?page=0&size=5" | python3 -m json.tool

# Health (público)
curl -sS http://localhost:8080/actuator/health
```

### 4.5 Endpoints útiles para demo

| Método | Path | Permiso |
| ------ | ---- | ------- |
| GET | `/api/v1/products` | `product:view` |
| GET | `/api/v1/products/{id}` | `product:view` |
| POST | `/api/v1/products` | `product:manage` |
| PUT | `/api/v1/products/{id}` | `product:manage` |
| DELETE | `/api/v1/products/{id}` | `product:manage` |
| GET/POST | `/api/v1/stock/movements` | `stock:view` / `stock:manage` |
| GET | `/api/v1/reports/summary` (y demás reports) | `report:view` |
| GET | `/api/v1/audit/products/{id}` | `audit:view` |
| GET | `/api/v1/users` | `user:manage` |
| GET | `/actuator/health` | público |

---

## 5. Qué abrir en el código (demos de implementación)

Usa esto cuando digan “muéstrame en el código”.

| Tema | Archivos a abrir |
| ---- | ---------------- |
| **Resource Server + JWT → roles** | `DockerSecurityConfig.java` (`oauth2ResourceServer`, `extractAuthorities`) |
| **Issuer / JWKS** | `application-docker.yml` (`issuer-uri`, `jwk-set-uri`) |
| **`@PreAuthorize`** | `ProductController.java`, `StockController.java` |
| **Realm / users / roles** | `keycloak/inventory-realm.json` |
| **Login SPA + PKCE** | `frontend/src/auth/keycloak.ts`, `AuthContext.tsx` |
| **Bearer en cada fetch** | `frontend/src/api/client.ts` (`updateToken(30)`) |
| **Paginación** | `ProductController` + `PageResponse.java` + `Products.tsx` |
| **Flyway** | `src/main/resources/db/migration/V1__init_schema.sql` |
| **Envers** | `Product.java` (`@Audited`), `AuditService.java` |
| **Testcontainers** | `AbstractIntegrationTest.java` |
| **Playwright** | `frontend/e2e/permissions.spec.ts` |
| **k6** | `tests/k6/stress-products.js` |
| **Compose (image vs build)** | `docker-compose.yml` (`image:` vs `build:`) |
| **Import realm** | Compose: `command: start-dev --import-realm` + volumen `./keycloak` |
| **Pipeline** | `.github/workflows/devsecops.yml` |
| **Cloud** | `render.yaml`, `CLOUD.md` |

Detalle narrado: [`ARBOL.md`](ARBOL.md).

---

## 6. UI, permisos y observabilidad

### Frontend

1. http://localhost:3000 → Login → Keycloak → productos.  
2. Cerrar sesión → entrar como `viewer` → no debe poder crear.  
3. `stock-manager` → Stock sí, productos manage no.

### Grafana (OBS local)

```bash
# si no levantaste OBS:
docker compose up -d tempo loki alloy prometheus alertmanager grafana
```

1. http://localhost:3001 → `admin` / `admin`  
2. Dashboard **Observabilidad** (métricas + logs Loki + traces Tempo).  
3. Genera tráfico (Swagger o UI) y refresca.

---

## 7. Pipelines — qué decir si preguntan

Detalle completo: [`../ci/PIPELINE.md`](../ci/PIPELINE.md).

| Tipo | Archivo | Función |
| ---- | ------- | ------- |
| **Calidad** | `devsecops.yml` | Tests + Sonar + Docker + ZAP/SCA + staging Compose E2E + gate |
| **Deploy staging** | `deploy-staging.yml` | Render + Vercel (branch `develop`) |
| **Deploy prod** | `deploy-prod.yml` | Render + Vercel (branch `main`) |
| **Commits** | `conventional-commits.yml` | Valida Conventional Commits en el PR |
| **Jenkins** | `Jenkinsfile` | Misma idea de stages en local |
| **Legacy** | `ci.yml`, `security.yml`, `post-deploy-staging.yml` | Manual, trozos aislados |

En Actions: mostrar *DevSecOps Pipeline* y *Deploy staging (cloud)*.

---

## 8. Orden sugerido para la defensa

1. **Stack up** + health + Swagger Authorize + GET products.  
2. **Código seguridad:** `DockerSecurityConfig` + `@PreAuthorize` + realm JSON.  
3. **401 / 403** en Postman o Swagger (sin token / viewer).  
4. **`./gradlew test`** (rápido) + **`integrationTest`** (Docker).  
5. **Playwright** un spec de permisos.  
6. **`./scripts/k6-run.sh stress`** + abrir `stress-products-summary.txt`.  
7. **JaCoCo HTML** o Sonar.  
8. **Grafana** traces/logs (si preguntan OBS).  
9. **Actions** DevSecOps + Deploy staging + URL Vercel/Render (si preguntan pipeline/cloud).

---

## Checklist “¿está listo?”

- [ ] `curl` health = UP  
- [ ] Token admin OK  
- [ ] Swagger GET products 200  
- [ ] Viewer POST → 403  
- [ ] `./gradlew test` verde  
- [ ] `./gradlew integrationTest` verde (Docker)  
- [ ] k6 load o stress con umbrales OK  
- [ ] Sabes qué archivo abrir para JWT, roles, paginación y Compose  
- [ ] Sabes nombrar DevSecOps vs Deploy staging/prod vs Conventional Commits  

Si algo falla: Keycloak aún arrancando, issuer distinto (`KEYCLOAK_ISSUER_URI` vs URL del navegador), o token expirado (vuelve a pedir el token).
