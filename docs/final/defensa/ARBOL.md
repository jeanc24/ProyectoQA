# Árbol del repositorio comentado

Cada archivo con **qué herramienta usa** y **qué hace**. Todos los nombres son enlaces: haz clic para abrir el archivo real.

> Volver al índice: [`README.md`](README.md) · Preguntas: [`PREGUNTAS.md`](PREGUNTAS.md)

---

## 1. Raíz del proyecto

```
ProyectoQA/
├── build.gradle                  Gradle · Spring Boot 4 · JaCoCo · Sonar · Dependency-Check
├── settings.gradle               Gradle · nombre del proyecto
├── gradlew / gradlew.bat         Gradle Wrapper (no exige Gradle instalado)
├── gradle/wrapper/               Gradle · versión fijada del wrapper
├── Dockerfile                    Docker · imagen del backend (multi-stage JDK21 → JRE21)
├── docker-compose.yml            Docker Compose · ambiente DESARROLLO
├── docker-compose.staging.yml    Docker Compose · ambiente STAGING
├── docker-compose.prod.yml       Docker Compose · ambiente PRODUCTION-LIKE
├── docker-compose.security.yml   Docker Compose · stack aislado para ZAP/smoke en Jenkins
├── sonar-project.properties      SonarCloud · config del scanner (paralela a build.gradle)
├── .env / .env.example           Variables ambiente dev
├── .env.staging(.example)        Variables ambiente staging
├── .env.production(.example)     Variables ambiente prod
├── .env.security(.example)       Variables stack Security (ZAP/Jenkins)
├── .dockerignore                 Docker · qué NO entra al build context
├── .githooks/commit-msg          Git hook · valida Conventional Commits en local
├── README.md                     Documentación principal
└── CONTRIBUTING.md               Convenciones de ramas y commits
```

| Archivo | Herramienta | Para qué |
|---|---|---|
| [`build.gradle`](../../../build.gradle) | Gradle | Dependencias, tasks `test`/`apiTest`/`contractTest`/`integrationTest`, JaCoCo, Sonar, Dependency-Check |
| [`Dockerfile`](../../../Dockerfile) | Docker | Compila el JAR con JDK 21 y lo corre sobre JRE 21 Alpine + healthcheck |
| [`docker-compose.yml`](../../../docker-compose.yml) | Docker Compose | 10 servicios de desarrollo (API, front, Postgres, Keycloak, observabilidad, Jenkins) |
| [`docker-compose.staging.yml`](../../../docker-compose.staging.yml) | Docker Compose | Réplica en puertos 8088/3008/8181/5434 |
| [`docker-compose.prod.yml`](../../../docker-compose.prod.yml) | Docker Compose | Endurecido: sin Swagger, Postgres solo en 127.0.0.1 |
| [`docker-compose.security.yml`](../../../docker-compose.security.yml) | Docker Compose | Stack mínimo sin bind mounts para el stage Security de Jenkins |
| [`sonar-project.properties`](../../../sonar-project.properties) | SonarCloud | Project key, rutas de fuentes y del XML de JaCoCo |

---

## 2. Backend — `src/main/java/icc354/pucmm/proyectoqa/`

```
proyectoqa/
├── ProyectoQaApplication.java     Spring Boot · punto de entrada (@SpringBootApplication)
│
├── config/                        Configuración transversal
│   ├── SecurityConfig.java            Spring Security · perfil `local` (todo abierto)
│   ├── DockerSecurityConfig.java      Spring Security + OAuth2 Resource Server · docker/staging/prod
│   ├── CorsConfig.java                Spring Web · orígenes permitidos desde `app.cors.allowed-origins`
│   └── OpenApiConfig.java             springdoc-openapi · esquema Bearer JWT en Swagger (@Profile("!prod"))
│
├── controller/                    Capa web (REST)
│   ├── ProductController.java         Spring MVC + springdoc · CRUD /api/v1/products
│   ├── ProductStockController.java    Spring MVC · GET /api/v1/products/{id}/stock/history
│   ├── StockController.java           Spring MVC · /api/v1/stock/movements
│   ├── ReportController.java          Spring MVC · /api/v1/reports/*
│   ├── AuditController.java           Spring MVC · /api/v1/audit/products/{id}
│   ├── CategoryController.java        Spring MVC · /api/v1/categories
│   └── GlobalExceptionHandler.java    @RestControllerAdvice · excepciones → códigos HTTP
│
├── service/                       Casos de uso (paquete `application.service`)
│   ├── ProductService.java            Spring · reglas de producto (SKU único, categoría)
│   ├── StockService.java              Spring · IN / OUT / ADJUSTMENT + historial
│   ├── ReportService.java             Spring · KPIs del dashboard
│   └── AuditService.java              Hibernate Envers · AuditReader para revisiones
│
├── domain/
│   ├── entity/
│   │   ├── Product.java               JPA + Envers (@Audited)
│   │   ├── Category.java              JPA
│   │   └── StockMovement.java         JPA · registro inmutable de movimiento
│   ├── enums/MovementType.java        IN | OUT | ADJUSTMENT
│   ├── exception/
│   │   ├── ResourceNotFoundException.java     → 404
│   │   ├── DuplicateSkuException.java         → 409
│   │   └── InsufficientStockException.java    → 400
│   └── repository/
│       ├── ProductRepository.java          Spring Data JPA · query nativa con filtros
│       ├── CategoryRepository.java         Spring Data JPA
│       └── StockMovementRepository.java    Spring Data JPA + Specifications (Criteria API)
│
└── dto/                           Records de entrada/salida (paquete `application.dto`)
    ├── ProductRequest.java            Bean Validation (@NotBlank, @Min, @DecimalMin)
    ├── ProductResponse.java
    ├── StockMovementRequest.java / StockMovementResponse.java
    ├── InventorySummaryResponse.java / TopProductResponse.java
    ├── ProductRevisionResponse.java
    ├── CategoryResponse.java
    ├── PageResponse.java              Envoltura de paginación estable para el frontend
    └── ErrorResponse.java             Formato único de error (status, error, message, fieldErrors)
```

### Enlaces directos

| Archivo | Herramientas | Qué demostrar |
|---|---|---|
| [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) | Spring Security, OAuth2 Resource Server, Nimbus JWT | Cadena stateless, rutas públicas, conversión de roles Keycloak → authorities |
| [`SecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/SecurityConfig.java) | Spring Security | Perfil `local` sin Keycloak |
| [`CorsConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/CorsConfig.java) | Spring Web CORS | Orígenes por variable de entorno |
| [`OpenApiConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java) | springdoc-openapi | Botón *Authorize* con Bearer JWT; desactivado en `prod` |
| [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) | Spring MVC, `@PreAuthorize`, springdoc, Bean Validation | Permisos por endpoint + paginación |
| [`GlobalExceptionHandler.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/GlobalExceptionHandler.java) | `@RestControllerAdvice` | Manejo central de errores |
| [`ProductService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java) | Spring, `@Transactional` | SKU único, normalización, mapeo a DTO |
| [`StockService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/StockService.java) | Spring, `switch` de Java 21 | Cálculo de stock y validaciones |
| [`AuditService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) | Hibernate Envers | Historial de revisiones |
| [`ProductRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/ProductRepository.java) | Spring Data JPA, SQL nativo | Filtros dinámicos y agregados |
| [`StockMovementRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/StockMovementRepository.java) | Spring Data JPA, Criteria Specifications | Filtros combinables |
| [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) | JPA, Envers | `@Audited`, callbacks `@PrePersist`/`@PreUpdate` |

---

## 3. Backend — `src/main/resources/`

```
resources/
├── application.yml               Config base: datasource, JPA, Flyway, Actuator, OTel, springdoc
├── application-local.yml         Perfil `local` — Postgres en localhost:5433, sin JWT
├── application-docker.yml        Perfil `docker` — issuer y JWKS de Keycloak
├── application-staging.yml       Perfil `staging` — todo por variables de entorno
├── application-prod.yml          Perfil `prod` — Swagger off, Actuator mínimo, logs WARN
├── application-integration.yml   Perfil `integration` — usado por Testcontainers, OTel apagado
└── db/migration/                 Flyway
    ├── V1__init_schema.sql            categories, products, stock_movements, revinfo, products_audit
    ├── V2__add_revinfo_sequence.sql   Secuencia de revisiones de Envers
    └── V3__fix_revinfo_sequence_increment.sql   INCREMENT BY 50 (alineado al allocationSize)
```

| Archivo | Herramienta | Nota |
|---|---|---|
| [`application.yml`](../../../src/main/resources/application.yml) | Spring Boot, Micrometer, OpenTelemetry, springdoc | `ddl-auto: validate` — el esquema lo manda Flyway, no Hibernate |
| [`application-prod.yml`](../../../src/main/resources/application-prod.yml) | Spring Boot | Endurecimiento de producción |
| [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql) | Flyway, PostgreSQL | Tablas, CHECKs e índices |

---

## 4. Tests backend — `src/test/java/.../`

```
proyectoqa/
├── ProyectoQaApplicationTests.java        Spring Boot Test · el contexto levanta
├── application/service/                   JUnit 5 + Mockito (sin Docker)
│   ├── ProductServiceTest.java                13 casos
│   ├── StockServiceTest.java                  6 casos
│   └── ReportServiceTest.java                 5 casos
├── application/controller/                MockMvc
│   ├── ProductControllerTest.java             8 casos (@WebMvcTest)
│   ├── GlobalExceptionHandlerTest.java        4 casos
│   ├── ApiTestSecurityConfig.java             Soporte: cadena de seguridad de test
│   ├── ProductApiScenarioTest.java            13 casos (@Tag("api"))
│   ├── StockApiScenarioTest.java              @Tag("api")
│   ├── ReportApiScenarioTest.java             @Tag("api")
│   └── AuditApiScenarioTest.java              @Tag("api")
├── application/contract/
│   └── OpenApiContractTest.java           @Tag("contract") · superficie OpenAPI vs controladores
└── application/integration/               Testcontainers (requiere Docker)
    ├── AbstractIntegrationTest.java           PostgreSQLContainer + @DynamicPropertySource
    ├── AbstractKeycloakIntegrationTest.java   + Keycloak real importando el realm
    ├── ProductIntegrationTest.java
    ├── StockIntegrationTest.java
    ├── ReportIntegrationTest.java
    ├── DataIntegrityIntegrationTest.java      Constraints reales de Postgres
    └── KeycloakSecurityIntegrationTest.java   401/403/200 con JWT emitido por Keycloak
```

| Archivo | Herramientas |
|---|---|
| [`AbstractIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java) | Testcontainers, JUnit 5, Spring Boot Test |
| [`AbstractKeycloakIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) | Testcontainers (GenericContainer Keycloak), RestClient |
| [`KeycloakSecurityIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/KeycloakSecurityIntegrationTest.java) | JWT real, password grant |
| [`ProductServiceTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/ProductServiceTest.java) | JUnit 5, Mockito |
| [`ApiTestSecurityConfig.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ApiTestSecurityConfig.java) | Spring Security Test |

---

## 5. Frontend — `frontend/`

```
frontend/
├── package.json               npm · scripts dev / build / lint / test:e2e
├── vite.config.ts             Vite · bundler y dev server
├── tsconfig*.json             TypeScript
├── eslint.config.js           ESLint
├── playwright.config.ts       Playwright · baseURL, retries, trazas, screenshots
├── Dockerfile                 Docker multi-stage · Node 22 build → nginx alpine
├── nginx.conf                 nginx · SPA fallback (try_files → index.html)
├── index.html                 Punto de montaje de React
│
├── src/
│   ├── main.tsx                   React 19 · monta App dentro de AuthProvider
│   ├── App.tsx                    react-router-dom · rutas y guardas por permiso
│   ├── auth/
│   │   ├── keycloak.ts                keycloak-js · instancia del adaptador
│   │   ├── AuthContext.tsx            React Context · init check-sso + PKCE, login/logout, hasRole
│   │   └── permissions.ts             Constantes de los 7 permisos
│   ├── api/
│   │   ├── client.ts                  fetch + JWT + ApiError (cliente HTTP central)
│   │   ├── products.ts                Endpoints de productos + query string
│   │   ├── stock.ts / reports.ts / audit.ts / categories.ts
│   ├── components/
│   │   ├── ProtectedRoute.tsx         Guarda de ruta por permiso
│   │   ├── AppHeader.tsx              Navegación + usuario + logout
│   │   ├── ProductForm.tsx            Formulario controlado
│   │   ├── Unauthorized.tsx           Pantalla 403
│   │   └── TechChip.tsx / TechIcon.tsx
│   ├── pages/
│   │   ├── Landing.tsx                Pública, botón de login
│   │   ├── Products.tsx               Tabla + filtros + orden + paginación + auditoría
│   │   ├── Stock.tsx                  Registro de movimientos
│   │   ├── Dashboard.tsx              KPIs y paneles (report:view)
│   │   └── TechGuide.tsx              Guía del stack embebida en la app
│   ├── types/product.ts, report.ts    Tipos TypeScript espejo de los DTO
│   └── styles/*.css
│
└── e2e/                       Playwright
    ├── helpers/auth.ts            Login por UI, token por API, alta de producto
    ├── helpers/login.spec.ts      1 test · login admin → /products
    ├── helpers/products.spec.ts   1 test · crear producto y verlo en la tabla
    ├── permissions.spec.ts        7 tests · viewer vs admin, auditor, users (`user:manage`)
    ├── stock.spec.ts              1 test · stock-manager registra IN
    └── dashboard.spec.ts          2 tests · KPIs y captura responsive
```

| Archivo | Herramientas | Qué demostrar |
|---|---|---|
| [`src/api/client.ts`](../../../frontend/src/api/client.ts) | Fetch API, keycloak-js | **El cliente HTTP**: inyecta el Bearer y traduce errores |
| [`src/auth/AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx) | keycloak-js, React Context | `check-sso`, PKCE S256, renovación de token |
| [`src/auth/permissions.ts`](../../../frontend/src/auth/permissions.ts) | TypeScript | Los 7 permisos, iguales a los del realm |
| [`src/components/ProtectedRoute.tsx`](../../../frontend/src/components/ProtectedRoute.tsx) | react-router-dom | Guarda por permiso |
| [`src/pages/Products.tsx`](../../../frontend/src/pages/Products.tsx) | React hooks | Render de la lista, filtros, orden, paginación |
| [`src/pages/Dashboard.tsx`](../../../frontend/src/pages/Dashboard.tsx) | React, `Promise.all` | 4 llamadas en paralelo |
| [`Dockerfile`](../../../frontend/Dockerfile) | Docker, Vite, nginx | Variables `VITE_*` se inyectan **en build**, no en runtime |
| [`nginx.conf`](../../../frontend/nginx.conf) | nginx | Rutas del SPA |
| [`playwright.config.ts`](../../../frontend/playwright.config.ts) | Playwright | `baseURL` configurable → mismo test contra dev o staging |

---

## 6. Infraestructura — `infra/`

```
infra/
├── prometheus/
│   ├── prometheus.yml          Prometheus · scrape de api:8080/actuator/prometheus cada 15s
│   ├── alerts.yml              Prometheus · 6 reglas de alerta
│   └── alertmanager.yml        Alertmanager · enrutamiento de alertas
├── grafana/
│   ├── provisioning/datasources/datasources.yml   Prometheus + Tempo + Loki con correlación
│   ├── provisioning/dashboards/dashboard.yml      Carga automática de dashboards
│   └── dashboards/
│       ├── api-ops.json            Dashboard operativo (avance 1)
│       ├── app.json                Latencia, throughput, errores
│       ├── business.json           Tráfico por dominio de negocio
│       ├── infra.json              CPU, memoria, JVM, HikariCP
│       ├── security.json           401 / 403 / auth failures
│       └── observability.json     Prometheus + Loki + Tempo (demo unificada)
├── loki/loki.yml               Loki · almacenamiento de logs
├── tempo/tempo.yml             Tempo · almacenamiento de trazas
├── alloy/config.alloy          Grafana Alloy · receptor OTLP → Tempo/Loki + logs de Docker
├── jenkins/
│   ├── Dockerfile                  Imagen Jenkins con Docker CLI, Compose y Node 22
│   └── Jenkinsfile                 Pipeline declarativo de 10 stages
└── keycloak/Dockerfile         Imagen Keycloak con el realm ya copiado (usada por el stack security)
```

| Archivo | Herramienta | Nota |
|---|---|---|
| [`prometheus.yml`](../../../infra/prometheus/prometheus.yml) | Prometheus | Un solo job: `inventory-api` |
| [`alerts.yml`](../../../infra/prometheus/alerts.yml) | Prometheus | CPU, heap, `up==0`, 5xx, p95, spike de 401 |
| [`config.alloy`](../../../infra/alloy/config.alloy) | Grafana Alloy, OpenTelemetry | Recibe OTLP en 4317/4318, exporta a Tempo y Loki |
| [`datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml) | Grafana | `derivedFields` enlaza log → traza por `traceId` |
| [`Jenkinsfile`](../../../infra/jenkins/Jenkinsfile) | Jenkins | Checkout → Build → Unit → Integration → API → Security → Sonar → Docker → Staging → E2E |

---

## 7. Keycloak, scripts y pruebas externas

```
keycloak/
└── inventory-realm.json        Keycloak · realm `inventory` como código:
                                 7 client roles, 2 clients, 3 usuarios demo

scripts/
├── wait-for-stack.sh           bash + curl · espera health de API, token KC y frontend
├── post-deploy-smoke.sh        bash + curl · 8 asserts post-deploy (ENV-02)
├── security-smoke.sh           bash + curl · JWT / CORS / permisos (TEST-03)
├── zap-baseline.sh             OWASP ZAP en Docker · DAST baseline
├── dependency-check.sh         OWASP Dependency-Check · SCA
├── k6-run.sh                   k6 en Docker · load | stress | all
├── jenkins-host-compose.sh     bash · reescribe bind mounts al path del host
├── jenkins-e2e-portforward.mjs Node · proxy TCP para que el navegador use localhost (PKCE)
└── github-setup.sh             gh CLI · labels e issues

tests/k6/
├── helpers.js                  k6 · token, base URL y headers compartidos
├── load-products.js            k6 · rampa a 15 VUs, p95 < 500 ms, fallos < 1 %
└── stress-products.js          k6 · picos a 80 VUs, p95 < 2000 ms, fallos < 5 %
```

| Archivo | Herramienta |
|---|---|
| [`inventory-realm.json`](../../../keycloak/inventory-realm.json) | Keycloak (import-realm) |
| [`post-deploy-smoke.sh`](../../../scripts/post-deploy-smoke.sh) | bash, curl, python3 |
| [`security-smoke.sh`](../../../scripts/security-smoke.sh) | bash, curl |
| [`zap-baseline.sh`](../../../scripts/zap-baseline.sh) | OWASP ZAP |
| [`dependency-check.sh`](../../../scripts/dependency-check.sh) | OWASP Dependency-Check |
| [`load-products.js`](../../../tests/k6/load-products.js) | k6 |
| [`stress-products.js`](../../../tests/k6/stress-products.js) | k6 |

---

## 8. CI/CD — `.github/`

```
.github/
├── workflows/
│   ├── devsecops.yml            GitHub Actions · PIPELINE PRINCIPAL (push/PR a develop)
│   ├── ci.yml                   GitHub Actions · legacy, solo manual
│   ├── security.yml             GitHub Actions · legacy, solo manual
│   ├── post-deploy-staging.yml  GitHub Actions · legacy, solo manual
│   └── conventional-commits.yml GitHub Actions · valida mensajes de commit en PR
├── pull_request_template.md
└── ISSUE_TEMPLATE/              bug_report.yml · feature_request.yml · task.yml · config.yml
```

| Workflow | Disparador | Jobs |
|---|---|---|
| [`devsecops.yml`](../../../.github/workflows/devsecops.yml) | push y PR a `develop`, manual | `build-and-test`, `docker-images`, `dependency-check`, `zap-baseline`, `staging-deploy-e2e`, `quality-gate` |
| [`conventional-commits.yml`](../../../.github/workflows/conventional-commits.yml) | PR a `develop` y `main` | Valida `tipo(scope): mensaje` |
| [`ci.yml`](../../../.github/workflows/ci.yml) | `workflow_dispatch` | Versión reducida, quedó como respaldo |
| [`security.yml`](../../../.github/workflows/security.yml) | `workflow_dispatch` | Dependency-Check + ZAP por separado |
| [`post-deploy-staging.yml`](../../../.github/workflows/post-deploy-staging.yml) | `workflow_dispatch` | Deploy staging + smoke + E2E aislado |

---

## 9. Documentación — `docs/`

```
docs/
├── final/
│   ├── defensa/                 ← ESTA GUÍA
│   │   ├── README.md                Guía maestra
│   │   ├── ARBOL.md                 Este archivo
│   │   └── PREGUNTAS.md             Banco de preguntas y respuestas
│   ├── ci/
│   │   ├── PIPELINE.md                  Pipeline CI/CD explicado
│   │   ├── JENKINS.md                   Setup de Jenkins
│   │   ├── ENVIRONMENTS.md              Los tres ambientes
│   │   ├── post-deploy-tests.md         Pruebas post-despliegue
│   │   ├── EVIDENCIA-JENKINS.md         Evidencia de builds
│   │   ├── EVIDENCIA-POST-DEPLOY-SMOKE.md   Salida real del smoke
│   │   └── post-deploy-smoke.collection.json  Colección estilo Postman/Newman
│   ├── quality/SONARCLOUD.md    Configuración y limitaciones del plan Free
│   └── testing/
│       ├── README.md                Catálogo completo de las 95 + 9 pruebas
│       ├── e2e/evidencias/          Capturas de Playwright
│       ├── k6/                      Resúmenes de carga y estrés
│       ├── zap/                     Reportes DAST + evidencia JWT/CORS
│       ├── dependency-check/        Reportes SCA
│       └── exploratory/             Charters EX-01…03 con evidencias
└── avance-1/                    Evidencias del primer avance
```

| Documento | Para qué en la defensa |
|---|---|
| [`docs/final/testing/README.md`](../testing/README.md) | Catálogo de pruebas con nombre de cada caso |
| [`docs/final/ci/ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md) | Tabla comparativa de ambientes |
| [`docs/final/ci/PIPELINE.md`](../ci/PIPELINE.md) | Explicación del pipeline |
| [`docs/final/quality/SONARCLOUD.md`](../quality/SONARCLOUD.md) | Qué mide Sonar y qué limita el plan Free |
| [`docs/final/testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md`](../testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md) | Tabla PASS/FAIL de seguridad |

---

## 10. Resumen: herramienta → dónde vive

| Herramienta | Archivos principales |
|---|---|
| **Spring Boot 4 / Java 21** | [`build.gradle`](../../../build.gradle), `src/main/java/**` |
| **Spring Security + OAuth2** | [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java), `@PreAuthorize` en cada controlador |
| **Keycloak 26** | [`inventory-realm.json`](../../../keycloak/inventory-realm.json), servicio `keycloak` en los compose |
| **Spring Data JPA / Hibernate** | `domain/repository/**`, `domain/entity/**` |
| **Flyway** | [`db/migration/`](../../../src/main/resources/db/migration/) |
| **Hibernate Envers** | [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) (`@Audited`), [`AuditService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) |
| **springdoc-openapi (Swagger)** | [`OpenApiConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java) + anotaciones en controladores |
| **JUnit 5 + Mockito** | `src/test/java/.../service/**`, `.../controller/**` |
| **Testcontainers** | `src/test/java/.../integration/Abstract*` |
| **Playwright** | [`frontend/e2e/`](../../../frontend/e2e/), [`playwright.config.ts`](../../../frontend/playwright.config.ts) |
| **k6** | [`tests/k6/`](../../../tests/k6/), [`k6-run.sh`](../../../scripts/k6-run.sh) |
| **OWASP ZAP** | [`zap-baseline.sh`](../../../scripts/zap-baseline.sh) |
| **OWASP Dependency-Check** | [`build.gradle`](../../../build.gradle) bloque `dependencyCheck`, [`dependency-check.sh`](../../../scripts/dependency-check.sh) |
| **JaCoCo** | [`build.gradle`](../../../build.gradle) bloques `jacoco*` |
| **SonarCloud** | [`build.gradle`](../../../build.gradle) bloque `sonar`, [`sonar-project.properties`](../../../sonar-project.properties) |
| **React 19 + Vite + TS** | `frontend/src/**` |
| **keycloak-js** | [`keycloak.ts`](../../../frontend/src/auth/keycloak.ts), [`AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx) |
| **Docker / Compose** | `Dockerfile`, `frontend/Dockerfile`, los 4 `docker-compose*.yml` |
| **Prometheus / Alertmanager** | [`infra/prometheus/`](../../../infra/prometheus/) |
| **Grafana / Loki / Tempo / Alloy** | [`infra/grafana/`](../../../infra/grafana/), [`infra/loki/`](../../../infra/loki/), [`infra/tempo/`](../../../infra/tempo/), [`infra/alloy/`](../../../infra/alloy/) |
| **OpenTelemetry / Micrometer** | [`application.yml`](../../../src/main/resources/application.yml) bloque `management` |
| **GitHub Actions** | [`.github/workflows/`](../../../.github/workflows/) |
| **Jenkins** | [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile) |
