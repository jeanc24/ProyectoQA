# Catálogo completo de pruebas — ProyectoQA

Inventario de **todas** las pruebas del repositorio: automatizadas, scripts de smoke/seguridad, performance, SCA/DAST, E2E y exploratorias.

> Índice operativo del README raíz: [Pruebas automatizadas](../../README.md#pruebas-automatizadas).  
> Guías de estudio por herramienta: [`docs/estudio/`](../../estudio/) (si están en la rama).

---

## 1. Resumen por capa

| Capa | Herramienta | Cantidad | Cómo correr |
|------|-------------|----------|-------------|
| Unit (service + WebMvc + excepciones) | JUnit 5 + Mockito + MockMvc | **36** métodos | `./gradlew test` |
| API scenarios (`@Tag("api")`) | MockMvc + `@WithMockUser` | **31** métodos | `./gradlew apiTest` |
| Contract (`@Tag("contract")`) | JUnit + OpenAPI / Newman paths | **2** métodos | `./gradlew contractTest` |
| Integration (`@Tag("integration")`) | Testcontainers (Postgres ± Keycloak) | **26** métodos | `./gradlew integrationTest` |
| E2E | Playwright (Chromium) | **9** tests | `cd frontend && npm run test:e2e` |
| Performance | k6 | **2** scripts (load + stress) | `./scripts/k6-run.sh load\|stress\|all` |
| Security smoke (JWT/CORS/permisos) | bash + curl | **1** script (varios asserts) | `./scripts/security-smoke.sh` |
| Post-deploy smoke | bash + curl | **1** script | `./scripts/post-deploy-smoke.sh` |
| DAST | OWASP ZAP baseline | **1** script | `./scripts/zap-baseline.sh` |
| SCA | OWASP Dependency-Check | **1** script | `./scripts/dependency-check.sh` |
| Calidad / cobertura | JaCoCo → SonarCloud | reportes | `./gradlew test jacocoTestReport sonar` |
| Exploratorio (manual) | Charters EX-01…03 | **3** charters + evidencias | Ver [exploratory/](exploratory/) |

**Total métodos JUnit automatizados:** 36 + 31 + 2 + 26 = **95**  
**Total E2E Playwright:** **9**  
**Scripts no-JUnit de verificación:** k6 (2) + smokes (2) + ZAP + Dependency-Check

Tags Gradle (`build.gradle`):

| Task | Incluye | Excluye |
|------|---------|---------|
| `test` | sin tag / unit | `integration`, `api`, `contract` |
| `apiTest` | tag `api` | — |
| `contractTest` | tag `contract` | — |
| `integrationTest` | tag `integration` (hereda de `AbstractIntegrationTest`) | — |

---

## 2. Unitarios — `./gradlew test` (36)

Sin Docker. Mockito / `@WebMvcTest` / handler puro.

### 2.1 `ProductServiceTest` — 13

Archivo: `src/test/java/.../service/ProductServiceTest.java`

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `create_savesProductAndNormalizesSku` | Guarda y normaliza SKU |
| 2 | `create_throwsWhenSkuExists` | SKU duplicado |
| 3 | `create_throwsWhenCategoryNotFound` | Categoría inexistente |
| 4 | `create_withoutCategory_setsCategoryNull` | Sin categoría |
| 5 | `findById_returnsProduct` | Lectura OK |
| 6 | `findById_throwsWhenNotFound` | Not found |
| 7 | `update_appliesChanges` | Update campos |
| 8 | `update_throwsWhenProductNotFound` | Update inexistente |
| 9 | `update_throwsWhenSkuTakenByOther` | SKU de otro producto |
| 10 | `delete_removesProduct` | Delete |
| 11 | `delete_throwsWhenNotFound` | Delete inexistente |
| 12 | `findAll_appliesFiltersAndPagination` | Filtros + page |
| 13 | `findAll_blankFiltersPassNullPatterns` | Filtros en blanco → null |

### 2.2 `StockServiceTest` — 6

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `registerInMovement_increasesStock` | Entrada IN |
| 2 | `registerOutMovement_decreasesStock` | Salida OUT |
| 3 | `registerOutMovement_insufficientStockThrows` | Stock insuficiente |
| 4 | `registerAdjustment_setsAbsoluteQuantity` | Ajuste absoluto |
| 5 | `registerMovement_unknownProductThrows` | Producto desconocido |
| 6 | `findByProductId_unknownProductThrows` | Historial producto inexistente |

### 2.3 `ReportServiceTest` — 5

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `inventorySummary_aggregatesRepositoryCounts` | Summary agregado |
| 2 | `lowStock_mapsProducts` | Low stock |
| 3 | `recentMovements_mapsHistory` | Movimientos recientes |
| 4 | `topProducts_mapsAggregationRows` | Top productos |
| 5 | `inventorySummary_nullValueBecomesZero` | Null → 0 |

### 2.4 `ProductControllerTest` — 8 (`@WebMvcTest`)

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `list_returns200` | GET list |
| 2 | `get_returns200` | GET by id |
| 3 | `create_returns201` | POST |
| 4 | `create_returns400_whenInvalid` | Validación |
| 5 | `create_returns409_whenDuplicateSku` | Conflicto SKU |
| 6 | `update_returns200` | PUT |
| 7 | `delete_returns204` | DELETE |
| 8 | `get_returns404_whenNotFound` | 404 |

### 2.5 `GlobalExceptionHandlerTest` — 4

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `handleNotFound_returns404` | Not found → 404 |
| 2 | `handleDuplicateSku_returns409` | Duplicate → 409 |
| 3 | `handleValidation_returns400WithFieldErrors` | Bean validation → 400 |
| 4 | `handleDataIntegrity_returns409` | DataIntegrity → 409 |

**Soporte (no es caso de prueba):** `ApiTestSecurityConfig.java` — security de test para escenarios API.

---

## 3. API scenarios — `./gradlew apiTest` (31)

Tag `@Tag("api")`. MockMvc + `@WithMockUser(authorities = …)` (sin Keycloak real).

### 3.1 `ProductApiScenarioTest` — 13

| # | Método | Esperado |
|---|--------|----------|
| 1 | `list_withoutAuth_returns401` | 401 |
| 2 | `list_asViewer_returns200` | 200 + `product:view` |
| 3 | `create_withoutAuth_returns401` | 401 |
| 4 | `create_asViewer_returns403` | 403 |
| 5 | `create_asAdmin_returns201` | 201 + `product:manage` |
| 6 | `update_asViewerOnly_returns403` | 403 |
| 7 | `delete_asViewerOnly_returns403` | 403 |
| 8 | `create_invalidBody_returns400` | 400 |
| 9 | `get_notFound_returns404` | 404 |
| 10 | `create_duplicateSku_returns409` | 409 |
| 11 | `list_withPagination_passesPageable` | page/size |
| 12 | `list_withNameFilter_passesFilter` | filtro name |
| 13 | `list_withSkuFilter_passesFilter` | filtro sku |

### 3.2 `StockApiScenarioTest` — 8

| # | Método | Esperado |
|---|--------|----------|
| 1 | `list_withoutAuth_returns401` | 401 |
| 2 | `list_withoutStockView_returns403` | 403 |
| 3 | `list_withStockView_returns200` | 200 |
| 4 | `create_withoutStockManage_returns403` | 403 |
| 5 | `create_withStockManage_returns201` | 201 |
| 6 | `create_insufficientStock_returns400` | 400 |
| 7 | `productHistory_notFound_returns404` | 404 |
| 8 | `productHistory_withStockView_returns200` | 200 |

### 3.3 `ReportApiScenarioTest` — 6

| # | Método | Esperado |
|---|--------|----------|
| 1 | `summary_withoutAuth_returns401` | 401 |
| 2 | `summary_withoutReportView_returns403` | 403 |
| 3 | `summary_withReportView_returns200` | 200 |
| 4 | `lowStock_withReportView_returns200` | 200 |
| 5 | `recentMovements_withReportView_returns200` | 200 |
| 6 | `topProducts_withReportView_returns200` | 200 |

### 3.4 `AuditApiScenarioTest` — 4

| # | Método | Esperado |
|---|--------|----------|
| 1 | `history_withoutAuth_returns401` | 401 |
| 2 | `history_withProductViewOnly_returns403` | 403 |
| 3 | `history_withAuditView_returns200` | 200 |
| 4 | `history_notFound_returns404` | 404 |

---

## 4. Contract — `./gradlew contractTest` (2)

Archivo: `OpenApiContractTest.java` · `@Tag("contract")`

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `controllersExposeCoreApiPaths` | Controllers exponen paths core (`/api/v1/...`) |
| 2 | `newmanCollectionCoversCoreApiPaths` | Collection Newman/post-deploy cubre paths core |

---

## 5. Integration — `./gradlew integrationTest` (26)

Requieren **Docker**. Tag `integration` (en `AbstractIntegrationTest`).

### Bases abstractas (0 casos; infraestructura)

| Clase | Rol |
|-------|-----|
| `AbstractIntegrationTest` | Postgres `postgres:16-alpine` + `@DynamicPropertySource` JDBC |
| `AbstractKeycloakIntegrationTest` | + Keycloak 26 + import `keycloak/inventory-realm.json` + password grant |

### 5.1 `ProyectoQaApplicationTests` — 1

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `contextLoads` | Arranca el contexto Spring Boot |

### 5.2 `ProductIntegrationTest` — 6

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `flyway_appliesMigrations` | Migraciones aplicadas |
| 2 | `createAndFindProduct_persistsToDatabase` | CRUD create/read en BD real |
| 3 | `updateProduct_changesFields` | Update persistido |
| 4 | `deleteProduct_removesRow` | Delete |
| 5 | `createProduct_duplicateSkuThrows` | Constraint SKU |
| 6 | `updateProduct_writesAuditRow` | Envers `products_audit` |

### 5.3 `StockIntegrationTest` — 3

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `registerInMovement_persistsMovementAndUpdatesProduct` | IN + cantidad |
| 2 | `registerOutMovement_insufficientStockThrows` | OUT insuficiente |
| 3 | `findByProductId_returnsMovementHistory` | Historial |

### 5.4 `ReportIntegrationTest` — 3

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `inventorySummary_reflectsCreatedProducts` | Summary con datos reales |
| 2 | `lowStock_includesProductAtOrBelowMin` | Low stock |
| 3 | `topProducts_ranksByOutVolume` | Ranking por OUT |

### 5.5 `DataIntegrityIntegrationTest` — 6 (Data testing)

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `flyway_appliesAllMigrationsOnEmptyDatabase` | Flyway completo en BD vacía |
| 2 | `seedData_noSeedMigrationPresent` | No hay seed oculto de negocio |
| 3 | `constraint_duplicateSku_isRejected` | Unique SKU |
| 4 | `constraint_negativePrice_isRejected` | Precio negativo |
| 5 | `constraint_invalidCategoryFk_isRejected` | FK inválida |
| 6 | `constraint_validCategoryFk_isAccepted` | FK válida |

### 5.6 `KeycloakSecurityIntegrationTest` — 7 (JWT real)

| # | Método | Qué verifica |
|---|--------|--------------|
| 1 | `products_withoutToken_returns401` | Sin Bearer → 401 |
| 2 | `products_asViewer_returns200` | Viewer GET OK |
| 3 | `createProduct_asViewer_returns403` | Viewer POST → 403 |
| 4 | `reports_asViewer_returns403` | Viewer reports → 403 |
| 5 | `reports_asAdmin_returns200` | Admin reports → 200 |
| 6 | `audit_asViewer_returns403` | Viewer audit → 403 |
| 7 | `audit_asAdmin_returns200Or404` | Admin audit → 200/404 |

---

## 6. E2E Playwright — `cd frontend && npm run test:e2e` (9)

Requiere stack (FE + API + Keycloak). Config: `frontend/playwright.config.ts`. Helpers: `frontend/e2e/helpers/auth.ts`.

| # | Archivo | Test | Qué verifica |
|---|---------|------|--------------|
| 1 | `helpers/login.spec.ts` | admin login redirects to `/products` | Login Keycloak UI |
| 2 | `helpers/products.spec.ts` | successfully create a product and verify the row in the table | CRUD UI producto |
| 3 | `stock.spec.ts` | stock-manager can register an IN movement | Rol stock-manager |
| 4 | `dashboard.spec.ts` | admin with report:view sees dashboard KPIs | Dashboard |
| 5 | `dashboard.spec.ts` | mobile viewport — products page screenshot for evidence | Responsive / evidencia |
| 6 | `permissions.spec.ts` | viewer cannot manage products and is blocked from dashboard | UI permisos |
| 7 | `permissions.spec.ts` | viewer gets 403 on product:manage and audit:view APIs | API 403 |
| 8 | `permissions.spec.ts` | admin can read product audit history (audit:view) | Audit admin |
| 9 | `permissions.spec.ts` | admin UI shows Historial; viewer does not | UI Historial por rol |

Evidencias: [`e2e/evidencias/`](e2e/evidencias/).

Staging:

```bash
BASE_URL=http://localhost:3008 KEYCLOAK_URL=http://localhost:8181 npm run test:e2e
```

---

## 7. Performance k6 — `./scripts/k6-run.sh`

Scripts: `tests/k6/`. Helper auth: `tests/k6/helpers.js`. Evidencias: [`k6/`](k6/).

| Script | Escenario | VUs (aprox.) | Thresholds |
|--------|-----------|--------------|------------|
| `load-products.js` | Load `GET /api/v1/products` | rampa → ~15 | p95 &lt; 500 ms · fail &lt; 1% · checks &gt; 99% |
| `stress-products.js` | Stress mismo endpoint | rampa → ~80 | p95 &lt; 2000 ms · fail &lt; 5% · checks &gt; 95% |

```bash
./scripts/k6-run.sh load
./scripts/k6-run.sh stress
./scripts/k6-run.sh all
```

---

## 8. Security & smokes (scripts)

### 8.1 `scripts/security-smoke.sh` (TEST-03)

Contra API `:8080` + Keycloak `:8081`. Escribe [`zap/EVIDENCIA-JWT-CORS-PERMISOS.md`](zap/EVIDENCIA-JWT-CORS-PERMISOS.md).

Asserts típicos:

- Health público → 200  
- Products sin token → 401  
- Viewer lista → 200; viewer crea → 403  
- Admin reports → 200; viewer reports → 403  
- Admin/viewer audit según permiso  
- CORS: origen permitido vs no permitido  

### 8.2 `scripts/post-deploy-smoke.sh` (ENV-02)

Misma idea contra **staging** (default API `:8088`, KC `:8181`). Evidencia: [`../ci/EVIDENCIA-POST-DEPLOY-SMOKE.md`](../ci/EVIDENCIA-POST-DEPLOY-SMOKE.md). Collection relacionada: [`../ci/post-deploy-smoke.collection.json`](../ci/post-deploy-smoke.collection.json).

### 8.3 `scripts/zap-baseline.sh` (DAST)

OWASP ZAP baseline (spider + passive) → [`zap/`](zap/) (`zap-report.html`, JSON, warnings). Stack aislado opcional: `docker-compose.security.yml`.

### 8.4 `scripts/dependency-check.sh` (SCA)

OWASP Dependency-Check → [`dependency-check/`](dependency-check/). Sync NVD completa solo con `DEPENDENCY_CHECK_AUTO_UPDATE=true`.

### 8.5 Helpers de stack (no son “tests”, pero los usa la suite)

| Script | Rol |
|--------|-----|
| `scripts/wait-for-stack.sh` | Espera API + token Keycloak |
| `scripts/k6-run.sh` | Orquesta contenedor `grafana/k6` |
| `scripts/jenkins-e2e-portforward.mjs` | E2E dentro de Jenkins |

---

## 9. Exploratory testing (manual) — TEST-07

Carpeta: [`exploratory/`](exploratory/).

| Charter | Foco | Evidencias |
|---------|------|------------|
| EX-01 Stock edge cases | OUT insuficiente, below min, ajuste 0, filtros | PNGs `Charter EX-01…/` |
| EX-02 Permisos por rol | admin / viewer / stock-manager / auditor | PNGs por rol |
| EX-03 UX | Usabilidad productos/dashboard | `EX-03-ux.md` |

Hallazgos documentados: `EC-01-*.md`, `EC-02-*.md`, `EC-03-*.md`.

---

## 10. Calidad de código (no es test funcional, sí gate de calidad)

| Pieza | Comando / dónde | Qué mide |
|-------|-----------------|----------|
| JaCoCo | `./gradlew test apiTest jacocoTestReport` | Cobertura líneas (gate servicios ≥ 60%) |
| SonarCloud | `./gradlew test jacocoTestReport sonar` | Bugs, vulns, smells, coverage, duplicación |
| Guía | [`../quality/SONARCLOUD.md`](../quality/SONARCLOUD.md) | Setup token / QG |

---

## 11. Dónde corren en CI

| Workflow / job | Qué ejecuta |
|----------------|-------------|
| `.github/workflows/devsecops.yml` | `test`, `apiTest`, `contractTest`, `integrationTest`, Sonar, Dependency-Check, ZAP, Docker build, staging, smoke, E2E |
| `.github/workflows/ci.yml` | Build + tests + Sonar (subconjunto) |
| `.github/workflows/security.yml` | DC + ZAP/smoke (manual) |
| `.github/workflows/post-deploy-staging.yml` | Smoke + E2E staging |
| `infra/jenkins/Jenkinsfile` | Misma escalera (paridad) |

---

## 12. Matriz herramienta PDF → este repo

| Del enunciado | En ProyectoQA |
|---------------|---------------|
| JUnit / Mockito | Sí — unit + WebMvc |
| Testcontainers | Sí — integration |
| API / contract | MockMvc `*ApiScenarioTest` + `OpenApiContractTest` (no RestAssured / Cucumber) |
| E2E | Playwright |
| Security | ZAP + Dependency-Check + security-smoke + Keycloak IT |
| Performance | k6 (no JMeter) |
| Data testing | `DataIntegrityIntegrationTest` + Flyway |
| Exploratory | Charters EX-01…03 |

---

## 13. Comandos “correr todo” (local)

```bash
# Backend JUnit (4 stages)
./gradlew test apiTest contractTest integrationTest

# Cobertura + Sonar (requiere SONAR_TOKEN)
./gradlew test jacocoTestReport sonar

# Stack + E2E + smokes + perf + DAST (orden típico)
docker compose up -d --build
./scripts/wait-for-stack.sh
./scripts/security-smoke.sh
cd frontend && npm run test:e2e && cd ..
./scripts/k6-run.sh all
./scripts/zap-baseline.sh
./scripts/dependency-check.sh
```

Staging:

```bash
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 ./scripts/post-deploy-smoke.sh
```

---

## 14. Conteos rápidos (defensa oral)

| Pregunta típica | Respuesta corta |
|-----------------|-----------------|
| ¿Cuántos tests JUnit? | **95** métodos (`test`+`api`+`contract`+`integration`) |
| ¿Cuántos E2E? | **9** Playwright |
| ¿Herramientas de testing? | JUnit, Mockito, MockMvc, Testcontainers, Playwright, k6, ZAP, Dependency-Check, smokes curl, Sonar/JaCoCo, exploratorio |
| ¿Qué no usamos del PDF? | Cucumber, RestAssured, JMeter (equivalentes MockMvc + k6) |

*Actualizar este archivo si se agrega una clase `*Test` / `*.spec.ts` / script de verificación.*
