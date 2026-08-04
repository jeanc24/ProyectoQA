# Tabla de archivos — abrir y señalar

Solo archivos clicables por tema. Guion oral: [`GUION-24H.md`](GUION-24H.md).

---

## 1. Keycloak + OAuth2 + JWT

| Pieza | Archivo |
| ----- | ------- |
| Realm, users, roles | [`keycloak/inventory-realm.json`](../../../keycloak/inventory-realm.json) |
| Instancia KC en FE | [`frontend/src/auth/keycloak.ts`](../../../frontend/src/auth/keycloak.ts) |
| init / login / logout / roles UI | [`frontend/src/auth/AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx) |
| Rutas protegidas | [`frontend/src/components/ProtectedRoute.tsx`](../../../frontend/src/components/ProtectedRoute.tsx) |
| Lista de permisos FE | [`frontend/src/auth/permissions.ts`](../../../frontend/src/auth/permissions.ts) |
| Bearer en cada fetch | [`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts) |
| issuer + JWKS | [`src/main/resources/application-docker.yml`](../../../src/main/resources/application-docker.yml) |
| Filter OAuth2 + map roles | [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) |
| Ejemplo `@PreAuthorize` | [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) |

---

## 2. Frontend React

| Pieza | Archivo |
| ----- | ------- |
| Bootstrap | [`frontend/src/main.tsx`](../../../frontend/src/main.tsx) |
| Rutas | [`frontend/src/App.tsx`](../../../frontend/src/App.tsx) |
| Productos UI | [`frontend/src/pages/Products.tsx`](../../../frontend/src/pages/Products.tsx) |
| Dashboard UI | [`frontend/src/pages/Dashboard.tsx`](../../../frontend/src/pages/Dashboard.tsx) |
| API products | [`frontend/src/api/products.ts`](../../../frontend/src/api/products.ts) |

---

## 3. Paginación

| Pieza | Archivo |
| ----- | ------- |
| Controller | [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) |
| Service | [`ProductService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java) |
| Repo | [`ProductRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/ProductRepository.java) |
| DTO | [`PageResponse.java`](../../../src/main/java/icc354/pucmm/proyectoqa/dto/PageResponse.java) |
| FE query | [`frontend/src/api/products.ts`](../../../frontend/src/api/products.ts) |
| FE UI | [`frontend/src/pages/Products.tsx`](../../../frontend/src/pages/Products.tsx) |

---

## 4. Backend, perfiles, Flyway, Envers

| Pieza | Archivo |
| ----- | ------- |
| Base config | [`application.yml`](../../../src/main/resources/application.yml) |
| Perfil docker | [`application-docker.yml`](../../../src/main/resources/application-docker.yml) |
| Staging | [`application-staging.yml`](../../../src/main/resources/application-staging.yml) |
| Prod | [`application-prod.yml`](../../../src/main/resources/application-prod.yml) |
| Migraciones (carpeta) | [`db/migration/`](../../../src/main/resources/db/migration/) |
| V1 esquema | [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql) |
| V2 secuencia revinfo | [`V2__add_revinfo_sequence.sql`](../../../src/main/resources/db/migration/V2__add_revinfo_sequence.sql) |
| V3 increment 50 | [`V3__fix_revinfo_sequence_increment.sql`](../../../src/main/resources/db/migration/V3__fix_revinfo_sequence_increment.sql) |
| Product `@Audited` | [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) |
| Audit API | [`AuditController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java) |
| Audit service | [`AuditService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) |
| Errores | [`GlobalExceptionHandler.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/GlobalExceptionHandler.java) |

---

## 5. Tests (por capa)

| Capa | Archivo / comando |
| ---- | ----------------- |
| Unit | `./gradlew test` — `*ServiceTest`, `ProductControllerTest`, `GlobalExceptionHandlerTest` |
| API | [`ProductApiScenarioTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductApiScenarioTest.java) (+ Stock/Report/Audit/User) · [`ApiTestSecurityConfig.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ApiTestSecurityConfig.java) |
| Contract | [`OpenApiContractTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java) |
| Integration | [`AbstractIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java) · [`AbstractKeycloakIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) |
| Playwright | [`frontend/playwright.config.ts`](../../../frontend/playwright.config.ts) · [`frontend/e2e/helpers/auth.ts`](../../../frontend/e2e/helpers/auth.ts) · [`frontend/e2e/`](../../../frontend/e2e/) |
| k6 | [`tests/k6/load-products.js`](../../../tests/k6/load-products.js) · [`tests/k6/stress-products.js`](../../../tests/k6/stress-products.js) · [`tests/k6/helpers.js`](../../../tests/k6/helpers.js) · [`scripts/k6-run.sh`](../../../scripts/k6-run.sh) |
| Smoke | [`scripts/security-smoke.sh`](../../../scripts/security-smoke.sh) · [`scripts/post-deploy-smoke.sh`](../../../scripts/post-deploy-smoke.sh) |
| ZAP | [`scripts/zap-baseline.sh`](../../../scripts/zap-baseline.sh) |
| Dep-Check | [`scripts/dependency-check.sh`](../../../scripts/dependency-check.sh) |
| Exploratorio | [`docs/final/testing/exploratory/`](../testing/exploratory/) |

---

## 6. JaCoCo + SonarCloud

| Pieza | Archivo |
| ----- | ------- |
| Config Gradle | [`build.gradle`](../../../build.gradle) |
| Sonar props | [`sonar-project.properties`](../../../sonar-project.properties) |
| Reporte local | `./gradlew test jacocoTestReport` → `build/reports/jacoco/test/html/index.html` |

---

## 7. CI/CD — Compose vs deploy yml

| | Staging Compose | Staging deploy yml | Prod Compose | Prod deploy yml |
|--|-----------------|--------------------|--------------|-----------------|
| **Archivo** | [`docker-compose.staging.yml`](../../../docker-compose.staging.yml) | [`deploy-staging.yml`](../../../.github/workflows/deploy-staging.yml) | [`docker-compose.prod.yml`](../../../docker-compose.prod.yml) | [`deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml) |
| **Para qué** | Emular / probar | Publicar cloud | Emular local | Publicar cloud |

| Compose | Archivo |
| ------- | ------- |
| Local + OBS + Jenkins | [`docker-compose.yml`](../../../docker-compose.yml) |
| Staging CI | [`docker-compose.staging.yml`](../../../docker-compose.staging.yml) |
| Prod-like | [`docker-compose.prod.yml`](../../../docker-compose.prod.yml) |
| Security Jenkins | [`docker-compose.security.yml`](../../../docker-compose.security.yml) |

| Pipeline | Archivo |
| -------- | ------- |
| DevSecOps | [`.github/workflows/devsecops.yml`](../../../.github/workflows/devsecops.yml) |
| Deploy staging | [`.github/workflows/deploy-staging.yml`](../../../.github/workflows/deploy-staging.yml) |
| Deploy prod | [`.github/workflows/deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml) |
| Conventional commits | [`.github/workflows/conventional-commits.yml`](../../../.github/workflows/conventional-commits.yml) |
| Legacy CI | [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml) |
| Legacy Security | [`.github/workflows/security.yml`](../../../.github/workflows/security.yml) |
| Legacy post-deploy staging | [`.github/workflows/post-deploy-staging.yml`](../../../.github/workflows/post-deploy-staging.yml) |
| Jenkins | [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile) |
| Blueprint Render staging | [`render.yaml`](../../../render.yaml) |
| Blueprint Render prod | [`infra/render/render.prod.yaml`](../../../infra/render/render.prod.yaml) |

| Perfil | Archivo Spring | Lo setea |
| ------ | -------------- | -------- |
| `docker` | [`application-docker.yml`](../../../src/main/resources/application-docker.yml) | `docker-compose.yml`, `docker-compose.security.yml` |
| `staging` | [`application-staging.yml`](../../../src/main/resources/application-staging.yml) | `docker-compose.staging.yml`, Render staging |
| `prod` | [`application-prod.yml`](../../../src/main/resources/application-prod.yml) | `docker-compose.prod.yml`, Render prod |

---

## 8. Observabilidad

| Pieza | Archivo |
| ----- | ------- |
| Datasources Grafana | [`infra/grafana/provisioning/datasources/datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml) |
| Provision dashboards | [`infra/grafana/provisioning/dashboards/dashboard.yml`](../../../infra/grafana/provisioning/dashboards/dashboard.yml) |
| Dashboard unificado (demo) | [`infra/grafana/dashboards/observability.json`](../../../infra/grafana/dashboards/observability.json) |
| Alloy (OTLP + docker logs) | [`infra/alloy/config.alloy`](../../../infra/alloy/config.alloy) |
| Prometheus scrape | [`infra/prometheus/prometheus.yml`](../../../infra/prometheus/prometheus.yml) |
| Alertas | [`infra/prometheus/alerts.yml`](../../../infra/prometheus/alerts.yml) |
| Tempo | [`infra/tempo/tempo.yml`](../../../infra/tempo/tempo.yml) |
| Loki | [`infra/loki/loki.yml`](../../../infra/loki/loki.yml) |
| OTel + JDBC + correlation | [`application.yml`](../../../src/main/resources/application.yml) |
| Compose (OTLP endpoint) | [`docker-compose.yml`](../../../docker-compose.yml) |
| Tráfico demo | [`scripts/generate-obs-traffic.sh`](../../../scripts/generate-obs-traffic.sh) |

| Dashboard | Archivo |
| --------- | ------- |
| Observabilidad (demo) | [`observability.json`](../../../infra/grafana/dashboards/observability.json) |
| App | [`app.json`](../../../infra/grafana/dashboards/app.json) |
| Infra | [`infra.json`](../../../infra/grafana/dashboards/infra.json) |
| Security | [`security.json`](../../../infra/grafana/dashboards/security.json) |
| Negocio | [`business.json`](../../../infra/grafana/dashboards/business.json) |
| API Ops | [`api-ops.json`](../../../infra/grafana/dashboards/api-ops.json) |

---

## 9. Trazas API + BD (señalar)

| Pieza | Archivo |
| ----- | ------- |
| `jdbc.includes` | [`application.yml`](../../../src/main/resources/application.yml) |
| Dep `datasource-micrometer` | [`build.gradle`](../../../build.gradle) |
| Dashboard | [`observability.json`](../../../infra/grafana/dashboards/observability.json) |
| Link Loki ↔ Tempo | [`datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml) |
