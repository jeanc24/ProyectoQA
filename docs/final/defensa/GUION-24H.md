# Guion 24h — Defensa ProyectoQA (martes 4 ago)

Documento para **leer en voz alta** y abrir archivos al lado. Cada bloque tiene: flujo visual → 3–4 párrafos fáciles → archivos clicables → frase corta de cierre.

> **Empieza aquí si presentas mañana:** [Guion oral literal 25 minutos](#guion-oral-literal--25-minutos-cronometrado)  
> Índice de temas: [Keycloak/OAuth2](#1-keycloak--oauth2--jwt) · [Frontend](#2-frontend-react) · [Paginación](#3-paginación) · [Backend/Swagger/Flyway/Envers](#4-backend-swagger-flyway-envers) · [Flyway vs Envers](#41-flyway-vs-envers--migraciones-y-auditoría-detalle) · [Tests](#5-todos-los-tipos-de-test) · [Playwright](#6-playwright) · [Testcontainers](#7-testcontainers) · [k6](#8-k6-performance) · [JaCoCo/Sonar](#9-jacoco--sonarcloud) · [CI/CD Compose vs deploy yml](#10-cicd--compose-emular-vs-deploy-yml-publicar) · [OBS/Grafana](#11-observabilidad-grafana--prometheus--loki--tempo--alloy--todo) · [Trazas API + BD](#12-trazas-de-api-y-de-base-de-datos--sí-las-tenemos) · [Ambientes](#13-ambientes-y-deploy) · [Checklist](#14-qué-nos-faltaría--checklist-anti-blanco)

Solo tablas de archivos (sin flujo): [`ARCHIVOS.md`](ARCHIVOS.md) · [`RESUMEN-APARTADOS.md`](RESUMEN-APARTADOS.md) · [`ARBOL.md`](ARBOL.md)

---

## Guion oral literal — 25 minutos (cronometrado)

Lee esto **casi palabra por palabra**. Entre corchetes `[...]` son acciones (abrir archivo, cambiar pestaña). Los links van al repo: ábrelos con Cmd+click mientras hablas.

**Antes de empezar (stack listo):** UI `:3000`, API `:8080`, Keycloak `:8081`, Grafana `:3001`, y si puedes: `./scripts/generate-obs-traffic.sh` ya corrido.

| Minuto | Bloque |
| ------ | ------ |
| 0:00–2:00 | Intro + arquitectura |
| 2:00–7:00 | Seguridad Keycloak + OAuth2 |
| 7:00–9:30 | Frontend + paginación |
| 9:30–12:30 | Backend, Flyway, Envers, Swagger, perfiles |
| 12:30–17:30 | Pirámide de tests (unit → k6) |
| 17:30–20:30 | JaCoCo, Sonar, CI/CD, Jenkins |
| 20:30–24:00 | Grafana: Prometheus, Loki, Tempo, trazas API+BD |
| 24:00–25:00 | Ambientes/deploy + cierre |

---

### [0:00 – 2:00] Introducción

Buenos días. Este es **ProyectoQA**, un sistema de **gestión de inventarios** que desarrollamos como monorepo para la materia de aseguramiento de calidad.

En una frase: el usuario usa un **frontend React**, se autentica con **Keycloak**, llama a una **API Spring Boot**, y los datos viven en **PostgreSQL**. El esquema lo versionamos con **Flyway**, la auditoría de productos con **Hibernate Envers**, y en local tenemos un stack completo de **observabilidad** con Grafana, Prometheus, Loki y Tempo. Todo pasa por pipelines de **CI/CD** en GitHub Actions, con un espejo en **Jenkins**.

[Señala el diagrama o di el flujo en voz alta:]

Usuario → React puerto 3000 → login Keycloak → JWT → API puerto 8080 → Postgres. En paralelo, métricas y trazas van a Grafana en el 3001.

No somos microservicios: es un **monolito modular** por capas. El detalle de archivos está también en [`ARBOL.md`](ARBOL.md) y [`RESUMEN-APARTADOS.md`](RESUMEN-APARTADOS.md).

---

### [2:00 – 7:00] Seguridad: Keycloak + OAuth2 + JWT

Voy a empezar por seguridad, porque es el corazón del acceso.

Los usuarios y roles **no** están en Postgres. Están en **Keycloak**, en el realm `inventory`. Eso lo tenemos como código en el archivo [`keycloak/inventory-realm.json`](../../../keycloak/inventory-realm.json): ahí están los clients `inventory-frontend` e `inventory-api`, los siete permisos como `product:view` y `product:manage`, y los usuarios demo admin, viewer, auditor.

[ABRE `keycloak/inventory-realm.json` — enseña un usuario o un rol.]

El flujo es este: el usuario en el frontend inicia login. La instancia de Keycloak se crea en [`frontend/src/auth/keycloak.ts`](../../../frontend/src/auth/keycloak.ts) — solo url, realm y clientId. Quien orquesta el init, login y logout es [`frontend/src/auth/AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx), con `check-sso` y PKCE. El **access token** queda en memoria de `keycloak-js`; no lo persistimos nosotros en localStorage.

[ABRE `AuthContext.tsx` — señala `keycloak.init` y `login`.]

Cada llamada a la API pasa por [`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts): renueva el token si hace falta con `updateToken` y manda `Authorization: Bearer …`.

[ABRE `client.ts` — señala el header Bearer.]

La API **no** vuelve a pedir usuario y password. Es un **OAuth2 Resource Server**. Con perfil docker, en [`src/main/resources/application-docker.yml`](../../../src/main/resources/application-docker.yml) configuramos `issuer-uri` y `jwk-set-uri`: Spring descarga las claves públicas JWKS de Keycloak y **verifica la firma**, el issuer y la expiración.

[ABRE `application-docker.yml` — señala issuer-uri y jwk-set-uri.]

Eso se activa en [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java): `.oauth2ResourceServer().jwt(...)`, y un converter que saca los roles de `resource_access` del cliente `inventory-api`. Después, los controllers usan `@PreAuthorize`. Ejemplo: [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java).

[ABRE `DockerSecurityConfig.java` — señala `oauth2ResourceServer` y `extractAuthorities`.]

Regla mental: **token adulterado o sin firma válida → 401**. **Token válido pero sin el permiso → 403**. En el frontend, [`ProtectedRoute.tsx`](../../../frontend/src/components/ProtectedRoute.tsx) solo controla la UX: sin sesión te manda al home; sin rol, a unauthorized. La seguridad de verdad es el backend.

Si alguien copia un JWT del browser y en jwt.io ve `Serialized-ID` sin roles: **ese no es el access token**, es cookie de sesión de Keycloak. El bueno sale del header Authorization hacia el puerto 8080, o del curl documentado en [`ARBOL.md`](ARBOL.md) sección 11.1.1.

**Frase:** Keycloak firma; el frontend transporta; Spring valida con JWKS y aplica roles.

[DEMO 30–40 s si hay tiempo: login admin en `:3000`, luego logout y login viewer → intentar dashboard o crear producto → unauthorized / 403.]

---

### [7:00 – 9:30] Frontend y paginación

El frontend es React 19 con Vite. Las rutas están en [`frontend/src/App.tsx`](../../../frontend/src/App.tsx): products, stock, dashboard y users, cada una con el permiso de [`permissions.ts`](../../../frontend/src/auth/permissions.ts).

[ABRE `App.tsx` — señala ProtectedRoute + requiredRole.]

Productos está en [`Products.tsx`](../../../frontend/src/pages/Products.tsx). La paginación es **server-side**: el front manda `page` y `size` en [`frontend/src/api/products.ts`](../../../frontend/src/api/products.ts); el backend recibe un `Pageable` en el controller, el repositorio pagina en Postgres, y devolvemos [`PageResponse.java`](../../../src/main/java/icc354/pucmm/proyectoqa/dto/PageResponse.java) con content, totalPages y totalElements. La UI solo pinta anterior/siguiente.

[ABRE `ProductController.java` método `list` — señala `@PageableDefault`.]

Altas de usuario: la pantalla `/users` es **solo lectura** vía Admin API; crear usuarios y asignar roles se hace en la consola de Keycloak.

---

### [9:30 – 12:30] Backend, Flyway, Envers, Swagger, perfiles

La API es Spring Boot por capas: controller → service → repository → dominio. Errores normalizados en [`GlobalExceptionHandler.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/GlobalExceptionHandler.java).

**No confundir Flyway y Envers** (detalle completo en [§4.1](#41-flyway-vs-envers--migraciones-y-auditoría-detalle)):

| | **Flyway** | **Envers** |
|--|------------|------------|
| Versiona | **Estructura** (tablas, índices, secuencias) | **Datos** de producto (historial de filas) |
| Cuándo | Al **arrancar** la API | En cada create/update/delete de `Product` |
| Dónde | `db/migration/V1…V3.sql` | `@Audited` + `products_audit` / `revinfo` |

El esquema **no** lo inventa Hibernate al vuelo. **Flyway** aplica migraciones en orden; Hibernate queda en `ddl-auto: validate`. En [`application.yml`](../../../src/main/resources/application.yml): `flyway.enabled` + `locations: classpath:db/migration`.

[ABRE carpeta `db/migration` y `V1__init_schema.sql`.]

- **V1** crea `categories`, `products`, `stock_movements` y también `revinfo` + `products_audit` (tablas que Envers usará).
- **V2** crea la secuencia `revinfo_seq`.
- **V3** ajusta el incremento a 50 (alineado con Hibernate).

Al arrancar: Flyway mira `flyway_schema_history` → aplica lo pendiente → Hibernate valida entidades vs esquema.

La auditoría de **datos** es **Hibernate Envers**: [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) con `@Audited`. Cada cambio escribe en `revinfo` + snapshot en `products_audit`. Se lee con [`AuditController`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java) / [`AuditService`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) → `GET /api/v1/audit/products/{id}` (`audit:view`).

**Frase:** Flyway = Git del esquema; Envers = historial de filas de producto. Flyway crea las tablas de audit; Envers las llena en runtime.

**Swagger** en `http://localhost:8080/swagger-ui.html` con Bearer. En prod se apaga ([`application-prod.yml`](../../../src/main/resources/application-prod.yml)).

YAML: siempre [`application.yml`](../../../src/main/resources/application.yml); según `SPRING_PROFILES_ACTIVE` mergea docker/staging/prod ([`docker-compose.yml`](../../../docker-compose.yml) pone `docker`).

**Diferencia rápida:** Dockerfile = imagen; docker-compose = stack; application.yml = config Spring.

---

### [12:30 – 17:30] Pirámide de tests

Tenemos varias capas de prueba — unas **95** métodos JUnit + **12** E2E Playwright + scripts. El inventario completo está en [`docs/final/testing/README.md`](../testing/README.md). Gradle las separa por tags: `test`, `apiTest`, `contractTest`, `integrationTest` en [`build.gradle`](../../../build.gradle).

**1) Unitarios — `./gradlew test` (~36 métodos)**  
**Qué hacen:** prueban la **lógica de negocio aislada**: crear producto, SKU duplicado, stock insuficiente, reportes, mapeo de excepciones a 404/409/400.  
**Cómo están implementados:** JUnit 5 + **Mockito** en clases como [`ProductServiceTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/ProductServiceTest.java), [`StockServiceTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/StockServiceTest.java), [`ReportServiceTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/service/ReportServiceTest.java). El repositorio se mockea: no hay Postgres. También hay `@WebMvcTest` del controller ([`ProductControllerTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductControllerTest.java)) y tests del [`GlobalExceptionHandlerTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/GlobalExceptionHandlerTest.java).  
**Por qué están:** feedback en segundos, baratos, detectan bugs de reglas sin levantar infra. Son la base de la pirámide y alimentan **JaCoCo**.

**2) API scenarios — `./gradlew apiTest` (~31 métodos)**  
**Qué hacen:** prueban el **contrato HTTP + seguridad de métodos**: GET/POST productos, 401/403 según authorities, stock, reportes, audit, users.  
**Cómo:** MockMvc + `@Tag("api")` + `@WithMockUser(authorities = "product:view")` — **simulan** roles sin Keycloak. Archivos: [`ProductApiScenarioTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ProductApiScenarioTest.java), `StockApiScenarioTest`, `ReportApiScenarioTest`, `AuditApiScenarioTest`, `UserApiScenarioTest`. Security de test en [`ApiTestSecurityConfig.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ApiTestSecurityConfig.java).  
**Por qué:** validar `@PreAuthorize` y status codes de forma rápida y estable; no necesitamos Docker ni Keycloak para cada assert de 403.

**3) Contract — `./gradlew contractTest` (2 métodos)**  
**Qué hacen:** evitan que el código y la documentación/smoke se desalineen.  
**Cómo:** [`OpenApiContractTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java) comprueba que los controllers siguen exponiendo `/api/v1/products`, `/stock/movements`, `/reports`, etc., y que la colección Newman de post-deploy ([`post-deploy-smoke.collection.json`](../ci/post-deploy-smoke.collection.json)) incluye health y products.  
**Por qué:** el “contrato” mínimo entre API documentada/automatizada y el código; si alguien renombra un path, el test falla.

**4) Integración — `./gradlew integrationTest` (~26 métodos)**  
**Qué hacen:** prueban el sistema **casi real**: Flyway aplica migraciones, JPA habla con Postgres, stock/productos/reportes contra BD de verdad; y en seguridad, JWT real de Keycloak (401/403/200).  
**Cómo:** **Testcontainers**. Base [`AbstractIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java): contenedor `postgres:16-alpine`, `@SpringBootTest`, perfil `integration`, `DynamicPropertySource` inyecta el JDBC. Suites: `ProductIntegrationTest`, `StockIntegrationTest`, `ReportIntegrationTest`, `DataIntegrityIntegrationTest`. Seguridad: [`AbstractKeycloakIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) + [`KeycloakSecurityIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/KeycloakSecurityIntegrationTest.java) — Keycloak real + import del realm.  
**Por qué:** “en mi máquina con H2” miente; aquí fallan problemas de SQL, constraints, Flyway y JWT de verdad. **Requieren Docker.**

[ABRE `AbstractIntegrationTest.java` — señala `PostgreSQLContainer` y `DynamicPropertySource`.]

**5) Playwright E2E — 12 tests**  
**Qué hacen:** como un usuario en el navegador: login Keycloak por UI, crear producto, movimientos de stock, dashboard, matriz de permisos (viewer no ve dashboard, auditor ve historial, etc.).  
**Cómo:** Chromium vía [`playwright.config.ts`](../../../frontend/playwright.config.ts); login en [`e2e/helpers/auth.ts`](../../../frontend/e2e/helpers/auth.ts); specs en `frontend/e2e/` (`login`, `products`, `stock`, `dashboard`, `permissions`). Necesitan FE+API+Keycloak arriba.  
**Por qué:** capturan roturas de integración UI↔auth↔API que los tests Java no ven (menú, redirects, formularios).

**6) k6 — performance (2 scripts)**  
**Qué hacen:** miden si la API aguanta carga. Load: rampa a ~15 VUs, p95 &lt; 500 ms. Stress: ~80 VUs, umbrales más flojos.  
**Cómo:** [`helpers.js`](../../../tests/k6/helpers.js) obtiene JWT; [`load-products.js`](../../../tests/k6/load-products.js) / [`stress-products.js`](../../../tests/k6/stress-products.js) pegan `GET /api/v1/products`; runner [`k6-run.sh`](../../../scripts/k6-run.sh) (contenedor grafana/k6). VU = usuario virtual concurrente.  
**Por qué:** unit/E2E no miden latencia bajo concurrencia. k6 **no** va en DevSecOps; se demo en local.

**7) Smokes, ZAP, Dependency-Check, exploratorio**  
- [`security-smoke.sh`](../../../scripts/security-smoke.sh): curl contra API real — 401 sin token, 403 viewer, CORS, evidencia markdown.  
- [`zap-baseline.sh`](../../../scripts/zap-baseline.sh): DAST OWASP ZAP.  
- [`dependency-check.sh`](../../../scripts/dependency-check.sh): SCA de vulnerabilidades en dependencias.  
- [`post-deploy-smoke.sh`](../../../scripts/post-deploy-smoke.sh): health + JWT tras levantar staging.  
- Exploratorio: charters en `docs/final/testing/exploratory/` (permisos, UX, edge cases) — manual documentado.

**Frase de cierre del bloque:**  
Unit prueba lógica con mocks; API prueba HTTP y roles simulados; contract amarra paths; IT prueba Postgres y Keycloak reales con Testcontainers; Playwright prueba al usuario; k6 prueba la carga; ZAP y Dependency-Check cubren seguridad.

---

### [17:30 – 20:30] JaCoCo, SonarCloud, CI/CD, Compose, perfiles, Render, Jenkins

**JaCoCo** → HTML de cobertura. **SonarCloud** → quality gate. Tablas Compose vs deploy: [§10](#10-cicd--compose-emular-vs-deploy-yml-publicar).

**Historia (decir así):** primero **emulamos** staging y prod con Docker Compose y perfiles Spring (`docker` / `staging` / `prod`) para probar. Luego los pipelines **`deploy-staging.yml` / `deploy-prod.yml`** publican a **Render** + **Vercel**. En Render free (**512 MB**) Keycloak nos dio **OOM**; por eso afinamos heap y la OBS (Grafana/Tempo/Loki) **solo corre en Compose local**.

| Pipeline | Levanta / ejecuta |
| -------- | ----------------- |
| **DevSecOps** | Tests+Sonar; ZAP con **`docker-compose.yml`**; staging efímero con **`docker-compose.staging.yml`** → smoke+PW → down; quality-gate |
| **deploy-staging** | **`render.yaml`** (DB+API+KC) + Vercel FE — persistente |
| **deploy-prod** | **`render.prod.yaml`** + Vercel prod |
| **Jenkins** | Espejo: Security con **`security.yml`**, Staging con **`staging.yml`** — sin cloud |
| Legacy `ci` / `security` / `post-deploy-staging` | Trozos manuales |

**Compose / perfiles:** `docker-compose.yml` → perfil `docker` + OBS+Jenkins; `.staging.yml` → perfil `staging` (8088…); `.prod.yml` → emular prod; `.security.yml` → solo API+KC+DB para ZAP Jenkins.

[ABRE §10 tablas o Actions — 15 s.]

**Frase:** Compose para emular; deploy yml para publicar; DevSecOps para calidad; 512 MB de Render limitó Keycloak/OBS en cloud.

---

### [20:30 – 24:00] Observabilidad: Grafana, Prometheus, Loki, Tempo

Ahora observabilidad. Stack **solo en Compose local** — no en free tier de Render. UI: http://localhost:3001, usuario admin admin.

[ABRE Grafana → dashboard **Observabilidad — Métricas, Logs y Trazas** — archivo [`infra/grafana/dashboards/observability.json`](../../../infra/grafana/dashboards/observability.json).]

Grafana es la **pantalla**. No guarda sola las tres señales. Tiene datasources provisionados en [`infra/grafana/provisioning/datasources/datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml):

- **Prometheus** en `http://prometheus:9090` — métricas. Scrapea cada 15 s `/actuator/prometheus` de la API, configurado en [`infra/prometheus/prometheus.yml`](../../../infra/prometheus/prometheus.yml).
- **Tempo** en `http://tempo:3200` — trazas.
- **Loki** en `http://loki:3100` — logs.

La API exporta trazas OTLP a **Grafana Alloy** ([`infra/alloy/config.alloy`](../../../infra/alloy/config.alloy)). Alloy reparte a Tempo y también scrapea logs Docker del contenedor `inventory-api` hacia Loki. En Compose, la variable es `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://alloy:4318/v1/traces`. El `service.name` es `inventory-api` en [`application.yml`](../../../src/main/resources/application.yml).

**Sí tenemos trazas de API y de base de datos.** En Tempo, una petición a productos es una traza HTTP. Gracias a `datasource-micrometer` y `jdbc.includes: query, fetch` en `application.yml`, dentro del **waterfall** aparecen spans JDBC hijos: eso es la base de datos correlacionada con la API. No scrapamos logs crudos de Postgres a Loki; la evidencia de BD es el span JDBC.

[ABRE una traza `http get /api/v1/products` — NO actuator/health — señala span HTTP y JDBC.]

Los logs llevan `[traceId,spanId]`; Loki tiene derived field hacia Tempo, y Tempo puede volver a Loki. Las tres señales se cruzan. Para generar tráfico usamos [`scripts/generate-obs-traffic.sh`](../../../scripts/generate-obs-traffic.sh).

Otros dashboards: App, Infra, Security — este último sube 401/403 en vivo con el viewer — Negocio y API Ops. Detalle en la sección 11 de este mismo documento.

**Frase:** Prometheus métricas, Loki logs, Tempo trazas; Alloy reparte; en el waterfall HTTP arriba y JDBC abajo.

---

### [24:00 – 25:00] Ambientes, deploy y cierre

Ambientes: **local** con [`docker-compose.yml`](../../../docker-compose.yml) y perfil docker, incluyendo OBS. **Staging CI** efímero con [`docker-compose.staging.yml`](../../../docker-compose.staging.yml) — se levanta, se prueba y se tira. **Cloud** persistente: Render + Vercel según [`render.yaml`](../../../render.yaml) y [`docs/final/ci/ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md). Observabilidad completa la demostramos en local.

Para cerrar: construimos un inventario con autenticación real Keycloak, API segura OAuth2, datos versionados y auditados, una pirámide de tests completa, calidad con JaCoCo y Sonar en CI, despliegue cloud, y observabilidad de tres señales incluyendo trazas API y JDBC.

Quedo atento a sus preguntas. Gracias.

---

### Si te adelantas o te atrasas

| Vas corto (&lt; 22 min) | Añade |
| ---------------------- | ----- |
| | Demo viewer 403 + panel Security en Grafana |
| | Abrir reporte JaCoCo HTML |
| | Mencionar Alertmanager y reglas en `alerts.yml` |

| Vas largo (&gt; 25 min) | Corta |
| ---------------------- | ----- |
| | Detalle de k6 stages |
| | Lista completa de dashboards |
| | Conventional commits |

### Si te quedas en blanco

Di solo esto y retoma:

> Keycloak firma el JWT. El frontend solo lo manda. Spring valida la firma con JWKS en DockerSecurityConfig. Los tests van de unit a Playwright y k6. JaCoCo y Sonar miden calidad en DevSecOps. En Grafana, Tempo muestra la API y abajo los spans JDBC de Postgres.

---

## Mapa mental en una frase

Somos un **inventario**: React habla con una **API Spring**, que guarda datos en **Postgres**. **Keycloak** autentica personas. **Flyway** versiona el esquema. **Envers** audita productos. En local tenemos **Grafana** (métricas/logs/trazas). **CI** (GitHub Actions + Jenkins) prueba y despliega.

```text
Usuario → React (:3000) → Keycloak login → JWT
                ↓ Bearer
         Spring API (:8080) → valida JWT (OAuth2) → JPA → Postgres
                ↓ OTLP / metrics
         Alloy → Tempo + Loki | Prometheus → Grafana (:3001)
```

---

## 1. Keycloak + OAuth2 + JWT

### Flujo visual

```text
[Landing] --login--> [Keycloak :8081 realm inventory]
                            |
                     emite ACCESS TOKEN (JWT firmado)
                            |
                            v
[keycloak-js en memoria]  ←── NO es la cookie Serialized-ID
                            |
                     Authorization: Bearer …
                            v
[DockerSecurityConfig] → JwtDecoder (issuer + JWKS de application-docker.yml)
                            |
              OK → roles → @PreAuthorize → Controller
              Firma mala / sin token → 401
              Token OK sin permiso → 403
```

### Guion (léelo así)

El usuario no “entra” a la base de datos: entra a **Keycloak**. Keycloak es el IdP: ahí viven usuarios, passwords y roles del cliente `inventory-api`. Eso está versionado en el realm JSON. Cuando hace login, Keycloak genera un **access token** JWT firmado. El frontend, con la librería `keycloak-js`, recibe ese token y lo mantiene en **memoria** (la instancia de Keycloak). Ojo: el archivo `keycloak.ts` solo **crea** la instancia (url, realm, clientId); **no** guarda el token en localStorage. Quien orquesta login/logout/check-sso es `AuthContext.tsx`.

Cada vez que la UI llama a la API, `client.ts` pone el Bearer y, si hace falta, renueva el token con `updateToken`. La API **no** vuelve a pedir usuario/password: es un **OAuth2 Resource Server**. Con perfil `docker`, `application-docker.yml` define `issuer-uri` (debe ser el realm inventory) y `jwk-set-uri` (claves públicas JWKS). Spring descarga esas claves y **verifica la firma**, el issuer y la expiración. Eso se activa en `DockerSecurityConfig` con `.oauth2ResourceServer().jwt(...)`.

Si el token está adulterado y la firma no cuadra → **401** antes de llegar al controller. Si el token es válido pero el usuario no tiene el rol (ej. `viewer` crea producto) → **403** por `@PreAuthorize`. En el frontend, `ProtectedRoute` solo mira si hay sesión y roles para la UX (home o `/unauthorized`); **la seguridad de verdad es el backend**.

Trampa típica: si en el browser copias una cookie de Keycloak y en jwt.io ves `typ: Serialized-ID` sin roles, **ese no es el access token**. El bueno sale del header `Authorization` de una llamada a `:8080`, o del curl de token. jwt.io puede marcar “Invalid Signature” si no pegas la clave pública; eso no significa que la API esté mal.

### Archivos (abre y señala)

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
| Ejemplo @PreAuthorize | [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) |

### Frase de cierre

“Keycloak firma; el front solo transporta; Spring valida con JWKS y aplica roles.”

### Comando demo token

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/inventory/protocol/openid-connect/token \
  -d grant_type=password -d client_id=inventory-api -d client_secret=inventory-api-secret \
  -d username=admin -d password=admin \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "$TOKEN"
```

---

## 2. Frontend React

### Flujo visual

```text
main.tsx → AuthProvider → App.tsx (rutas)
                              |
         /products  → ProtectedRoute(product:view) → Products.tsx
         /stock     → stock:view
         /dashboard → report:view
         /users     → user:manage (lista KC, altas en consola KC)
                              |
                    api/*.ts → client.ts → API :8080
```

### Guion

La SPA es React + Vite + TypeScript. Al cargar, `AuthProvider` hace `keycloak.init({ onLoad: "check-sso" })`: si ya hay sesión en Keycloak, recupera token sin pedir password otra vez; si no, `isAuthenticated` queda false y las rutas privadas te tiran al home.

Las pantallas de negocio son productos (CRUD + filtros + paginación), stock, dashboard de KPIs (reportes) y users (solo lectura vía Admin API). Los botones y menús respetan permisos; un `viewer` no ve “Crear” y si pega `/dashboard` va a unauthorized.

El front **no** valida firma del JWT. Si modificas el token y refrescas, Keycloak puede emitir uno nuevo (sesión SSO) o perder sesión y volver al home. Si mandas un Bearer falso, la API responde 401 y Products muestra error; no “acepta” roles inventados.

### Archivos

| Pieza | Archivo |
| ----- | ------- |
| Bootstrap | [`frontend/src/main.tsx`](../../../frontend/src/main.tsx) |
| Rutas | [`frontend/src/App.tsx`](../../../frontend/src/App.tsx) |
| Productos UI | [`frontend/src/pages/Products.tsx`](../../../frontend/src/pages/Products.tsx) |
| Dashboard UI | [`frontend/src/pages/Dashboard.tsx`](../../../frontend/src/pages/Dashboard.tsx) |
| API products | [`frontend/src/api/products.ts`](../../../frontend/src/api/products.ts) |

---

## 3. Paginación

### Flujo visual

```text
Products.tsx  page=0 size=10
      → GET /api/v1/products?page=0&size=10&sort=...
      → ProductController(@PageableDefault) → ProductService
      → ProductRepository.findFiltered(..., Pageable)
      → Postgres LIMIT/OFFSET
      → PageResponse { content, totalPages, totalElements }
      → botones prev/next en UI
```

### Guion

La paginación es **server-side**. El frontend no carga todos los productos: manda `page` y `size`. Spring convierte eso en un `Pageable`. El repositorio ejecuta la query paginada en Postgres. Respondemos con un DTO `PageResponse` (contenido + totales). La UI solo pinta “1 / N” y deshabilita botones.

Default en API es size 20 (`@PageableDefault`); en la UI de productos usamos size 10. Misma idea en listados de movimientos de stock.

### Archivos

| Pieza | Archivo |
| ----- | ------- |
| Controller | [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) |
| Service | [`ProductService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java) |
| Repo | [`ProductRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/ProductRepository.java) |
| DTO | [`PageResponse.java`](../../../src/main/java/icc354/pucmm/proyectoqa/dto/PageResponse.java) |
| FE query | [`frontend/src/api/products.ts`](../../../frontend/src/api/products.ts) |
| FE UI | [`frontend/src/pages/Products.tsx`](../../../frontend/src/pages/Products.tsx) |

---

## 4. Backend, Swagger, Flyway, Envers

### Flujo visual

```text
Request → Security → Controller → Service → Repository → Postgres
                                      ↑
                               Flyway creó tablas al arrancar
                               Envers escribe *_audit en cambios Product
Swagger UI :8080/swagger-ui.html  (Authorize con Bearer)
```

### Guion corto

Monolito Spring Boot por capas. Controllers REST → services → JPA. Errores en `GlobalExceptionHandler`. **Swagger** documenta con Bearer; en prod se apaga. Perfiles: `application.yml` + `application-{docker|staging|prod}.yml` según `SPRING_PROFILES_ACTIVE`.

Flyway / Envers: ver **§4.1** (abajo) — no son lo mismo.

### Archivos

| Pieza | Archivo |
| ----- | ------- |
| Base config | [`application.yml`](../../../src/main/resources/application.yml) |
| Perfil docker | [`application-docker.yml`](../../../src/main/resources/application-docker.yml) |
| Staging / prod | [`application-staging.yml`](../../../src/main/resources/application-staging.yml) · [`application-prod.yml`](../../../src/main/resources/application-prod.yml) |
| Migraciones | [`db/migration/`](../../../src/main/resources/db/migration/) |
| Product auditado | [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) |
| Audit API | [`AuditController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java) · [`AuditService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) |
| Errores | [`GlobalExceptionHandler.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/GlobalExceptionHandler.java) |

---

## 4.1 Flyway vs Envers — migraciones y auditoría (detalle)

### Idea clave (no confundir)

| | **Flyway** | **Hibernate Envers** |
|--|------------|----------------------|
| **Qué versiona** | La **estructura** de la BD (tablas, índices, secuencias) | Los **cambios de datos** de entidades auditadas (`Product`) |
| **Cuándo corre** | Al **arrancar** la API (antes de servir tráfico) | En cada **insert/update/delete** de un `Product` |
| **Dónde vive** | SQL en `db/migration/V*.sql` | Anotación `@Audited` + tablas `*_audit` / `revinfo` |
| **Analogía** | Git del **esquema** | Historial de **filas** de producto |

---

### Flyway — migraciones de esquema

**Cómo lo tenemos**

- [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql) — `categories`, `products`, `stock_movements`, y tablas de Envers: `revinfo`, `products_audit`.
- [`V2__add_revinfo_sequence.sql`](../../../src/main/resources/db/migration/V2__add_revinfo_sequence.sql) — secuencia `revinfo_seq`.
- [`V3__fix_revinfo_sequence_increment.sql`](../../../src/main/resources/db/migration/V3__fix_revinfo_sequence_increment.sql) — `INCREMENT BY 50` (alineado con Hibernate).
- En [`application.yml`](../../../src/main/resources/application.yml): `spring.flyway.enabled: true`, `locations: classpath:db/migration`.
- Hibernate: `ddl-auto: validate` → **no** crea/altera tablas solo; debe coincidir con Flyway.

**Cómo funciona al arrancar**

```text
API arranca
  → Flyway mira la tabla flyway_schema_history
  → ¿Falta V1/V2/V3? → ejecuta el SQL en orden
  → Marca esas versiones como aplicadas
  → Hibernate valida que las entidades JPA coincidan con el esquema
  → App lista
```

**Por qué así**

1. Esquema como código — mismo SQL en local, CI, staging, prod.
2. Repetible y ordenado — no se reescribe V1; un cambio nuevo va en `V4__…`.
3. Seguro — sin `ddl-auto: update` que cambie prod a ciegas.
4. Las tablas de auditoría de Envers las crea **Flyway** en V1 (no magia de Hibernate creando DDL en runtime).

---

### Envers — auditoría de datos (productos)

**Cómo lo tenemos**

- [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) con `@Audited`.
- Categoría: `@Audited(targetAuditMode = NOT_AUDITED)` — no versiona la categoría entera, solo el vínculo.
- Config: `org.hibernate.envers.audit_table_suffix: _audit` → `products_audit`; `revision_sequence_name: revinfo_seq`.
- Lectura: [`AuditService`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) + [`AuditController`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/AuditController.java) → `GET /api/v1/audit/products/{id}` (`audit:view`).

**Cómo funciona en runtime**

```text
Admin actualiza un producto
  → JPA guarda en products
  → Envers, en la misma transacción:
       1) inserta fila en revinfo (rev + timestamp)
       2) inserta snapshot en products_audit (id + rev + revtype + campos)
  → Historial consultable por API
```

`revtype` típico: 0 = add, 1 = mod, 2 = del.

**Por qué Envers (y no solo logs):** historial consultable ligado al producto; trazabilidad de inventario; separado de Flyway (Flyway no audita updates de negocio; Envers no versiona el DDL).

---

### Cómo encajan juntos

```text
Flyway (arranque)                 Envers (cada cambio de Product)
─────────────────                 ────────────────────────────────
Crea products                     Escribe en products (dato actual)
Crea products_audit               Escribe snapshot en products_audit
Crea revinfo + revinfo_seq        Usa revinfo / revinfo_seq
```

1. Flyway prepara el escenario (tablas correctas).  
2. JPA usa `products` / `stock_movements` / etc.  
3. Envers, al mutar un `Product`, llena `products_audit` + `revinfo`.  
4. La API de audit **lee** ese historial.

**Por qué V2 y V3:** Envers necesita secuencia para `rev`. V2 la crea; V3 ajusta incremento a 50 porque Hibernate reserva IDs en bloques — si no coincide, fallan las revisiones.

### Frase de cierre §4.1

“Flyway versiona el esquema con SQL en `db/migration` y Hibernate solo valida. Envers audita cambios de `Product` en `products_audit`/`revinfo`. Las tablas de auditoría las crea Flyway; Envers las usa en cada update. Estructura vs historial de datos: dos problemas, dos herramientas.”

---

## 5. Todos los tipos de test

### Pirámide visual

```text
                    Playwright E2E (12) — usuario real
                          /              \
              API MockMvc (31)        Integration Testcontainers (26)
                     |                         |
              Contract (2)              Keycloak JWT real
                     |
              Unit Mockito (36)
   + k6 load/stress · smoke · ZAP · Dependency-Check · exploratorio
```

### Por cada capa: qué / cómo / por qué

| Capa | Comando | Qué prueba | Cómo está implementado | Por qué existe |
| ---- | ------- | ---------- | ---------------------- | -------------- |
| **Unit** | `./gradlew test` | Reglas de negocio (SKU, stock, reportes, excepciones) | JUnit + Mockito; mocks de repos; `@WebMvcTest` controller; ~36 métodos en `*ServiceTest`, `ProductControllerTest`, `GlobalExceptionHandlerTest` | Rápidos, baratos; base de JaCoCo; fallan reglas sin Docker |
| **API** | `./gradlew apiTest` | HTTP + `@PreAuthorize` (200/401/403/…) | MockMvc + `@WithMockUser(authorities=…)` + `@Tag("api")`; `ProductApiScenarioTest` etc.; `ApiTestSecurityConfig` | Seguridad de endpoints sin levantar Keycloak |
| **Contract** | `./gradlew contractTest` | Paths de controllers + colección Newman alineados | [`OpenApiContractTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java) lee `@RequestMapping` y el JSON de smoke | Evita romper contratos documentados/post-deploy |
| **Integration** | `./gradlew integrationTest` | App + Flyway + JPA + Postgres; JWT real con KC | Testcontainers: [`AbstractIntegrationTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java) (Postgres); [`AbstractKeycloakIntegrationTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) + `KeycloakSecurityIntegrationTest`; ~26 métodos | Infra real; no mentir con H2; necesita Docker |
| **Playwright** | `cd frontend && npm run test:e2e` | Login UI, CRUD, permisos por rol | Chromium; [`auth.ts`](../../../frontend/e2e/helpers/auth.ts); 12 specs en `e2e/` | Detecta roturas FE↔KC↔API |
| **k6** | `./scripts/k6-run.sh` | Latencia/errores bajo carga | `helpers.js` + load (~15 VU) / stress (~80 VU) contra `/products` | Performance; no está en DevSecOps |
| **Smoke** | `security-smoke.sh` / `post-deploy-smoke.sh` | JWT/CORS/permisos / health post-deploy | bash + curl + evidencias `.md` | Verificación rápida contra stack vivo |
| **ZAP** | `zap-baseline.sh` | DAST (vulnerabilidades HTTP) | OWASP ZAP baseline | Seguridad ofensiva automatizada |
| **Dep-Check** | `dependency-check.sh` | CVEs en dependencias | OWASP Dependency-Check | SCA |
| **Exploratorio** | manual | UX, permisos, edge cases | Charters en `testing/exploratory/` | Hallazgos que la automatización no cubre |

Detalle método a método: [`docs/final/testing/README.md`](../testing/README.md).

**En CI (DevSecOps):** unit + api + contract + integration + smoke + ZAP + Dep-Check + Playwright (staging Compose). **Fuera de GHA / demo local:** k6, Jenkins espejo, exploratorio.

---

## 6. Playwright

### Flujo visual

```text
npx playwright test
  → abre Chromium
  → helpers/auth.ts: login UI Keycloak
  → specs: login, products, permissions…
  → reporte HTML frontend/playwright-report/
```

### Guion

Playwright es prueba **end-to-end**: como un usuario en el navegador. Config en `playwright.config.ts` (baseURL, retries, traces). Los helpers hacen login real en Keycloak y luego afirman la UI (menú, crear producto, viewer sin permisos).

En CI corre contra el staging Compose después del deploy efímero. En local: stack arriba + `cd frontend && npx playwright test --reporter=html`. Si el reporte está viejo, borra `playwright-report` y regenera.

### Archivos

| Pieza | Archivo |
| ----- | ------- |
| Config | [`frontend/playwright.config.ts`](../../../frontend/playwright.config.ts) |
| Auth helper | [`frontend/e2e/helpers/auth.ts`](../../../frontend/e2e/helpers/auth.ts) |
| Specs | [`frontend/e2e/`](../../../frontend/e2e/) |

---

## 7. Testcontainers

### Flujo visual

```text
./gradlew integrationTest
  → JUnit arranca contenedor Postgres (y a veces Keycloak)
  → Spring usa JDBC/issuer dinámicos (DynamicPropertyRegistry)
  → tests corren contra infra real
  → contenedores se apagan
```

### Guion

Testcontainers evita “en mi máquina funciona” con H2 mentiroso: levanta **Postgres real** (y Keycloak real en tests de seguridad). `AbstractIntegrationTest` define el contenedor Postgres compartido; `AbstractKeycloakIntegrationTest` importa el realm y usa password grant. Necesitas Docker corriendo.

Así probamos Flyway, JPA, seguridad JWT de verdad, no mocks eternos.

### Archivos

| Pieza | Archivo |
| ----- | ------- |
| Postgres IT | [`AbstractIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java) |
| Keycloak IT | [`AbstractKeycloakIntegrationTest.java`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java) |

---

## 8. k6 (performance)

### Flujo visual

```text
./scripts/k6-run.sh load|stress
  → Docker grafana/k6
  → JWT viewer (host) → GET /api/v1/products  × muchos VUs
  → thresholds p95 / error rate
  → summary en docs/final/testing/k6/
```

### Guion

k6 es performance HTTP (equivalente a “no usamos JMeter”). Un **VU** (Virtual User) es un cliente concurrente simulado. **Load**: rampa hasta ~15 VUs, p95 &lt; 500 ms, fallos &lt; 1%. **Stress**: hasta ~80 VUs, umbrales más flojos (puede degradar, no colapsar). Auth con JWT `viewer`. No está en DevSecOps; se demo en local.

### Archivos

| Pieza | Archivo |
| ----- | ------- |
| Load | [`tests/k6/load-products.js`](../../../tests/k6/load-products.js) |
| Stress | [`tests/k6/stress-products.js`](../../../tests/k6/stress-products.js) |
| Helpers | [`tests/k6/helpers.js`](../../../tests/k6/helpers.js) |
| Runner | [`scripts/k6-run.sh`](../../../scripts/k6-run.sh) |
| Evidencias | [`docs/final/testing/k6/`](../testing/k6/) |

---

## 9. JaCoCo + SonarCloud

### Flujo visual

```text
tests Gradle → JaCoCo report HTML (cobertura %)
            → plugin sonar → SonarCloud (bugs, smells, gate)
            → DevSecOps espera quality gate
```

### Guion

**JaCoCo** mide qué líneas ejecutaron los tests; abres el HTML en `build/reports/jacoco/`. **SonarCloud** analiza calidad y puede **bloquear** el merge si el quality gate falla (cobertura, bugs, etc.). En CI va el token `SONAR_TOKEN`. Badges del README muestran el estado.

### Archivos / comandos

| Pieza | Dónde |
| ----- | ----- |
| Config Gradle | [`build.gradle`](../../../build.gradle) |
| Sonar props | [`sonar-project.properties`](../../../sonar-project.properties) |
| Doc | [`docs/final/quality/SONARCLOUD.md`](../quality/SONARCLOUD.md) |
| Local | `./gradlew test jacocoTestReport sonar` |

---

## 10. CI/CD — Compose (emular) vs deploy yml (publicar)

### Historia (oral)

Primero **emulamos** staging y prod con Docker Compose + perfiles Spring para probar. Después los workflows **`deploy-staging.yml` / `deploy-prod.yml`** publican a **Render** (API + Keycloak + Postgres) + **Vercel** (FE). En Render free (**512 MB**) Keycloak dio **OOM**; por eso OBS (Grafana/Tempo/Loki) **solo en Compose local**.

---

### Tabla maestra — Staging y Prod: Compose vs pipeline deploy

| Ambiente | Emular (Compose) — FASE 1 | Perfil Spring | Qué levanta el Compose | Publicar (pipeline) — FASE 2 | Blueprint / FE cloud | Qué queda publicado |
| -------- | ------------------------- | ------------- | ---------------------- | ---------------------------- | -------------------- | ------------------- |
| **Staging** | `docker-compose.staging.yml` | `staging` (`application-staging.yml`) | Postgres, API, frontend, Keycloak, Tempo, Loki, Alloy, Prometheus, Alertmanager, Grafana (**sin** Jenkins). Puertos **8088 / 3008 / 8181**. **Efímero** en CI (up → smoke → Playwright → down -v) | `.github/workflows/deploy-staging.yml` (push `develop`) | `render.yaml` + Vercel (branch `develop`) | Persistente: DB + API + Keycloak en Render; FE en Vercel. **Sin** OBS |
| **Prod** | `docker-compose.prod.yml` | `prod` (`application-prod.yml`) | Stack production-like local (Swagger off, etc.). Solo emulación en laptop | `.github/workflows/deploy-prod.yml` (push `main`) | `infra/render/render.prod.yaml` + Vercel (branch `main`) | Persistente: DB + API + Keycloak prod en Render; FE prod en Vercel. **Sin** OBS |

| | Staging Compose | Staging deploy yml | Prod Compose | Prod deploy yml |
|--|-----------------|--------------------|--------------|-----------------|
| **Archivo** | `docker-compose.staging.yml` | `deploy-staging.yml` | `docker-compose.prod.yml` | `deploy-prod.yml` |
| **Para qué** | Emular / probar staging | Publicar staging cloud | Emular prod local | Publicar prod cloud |
| **Dónde corre** | Laptop o runner CI | GitHub Actions → Render + Vercel | Solo laptop | GitHub Actions → Render + Vercel |
| **Duración** | Se destruye (`down -v`) | Queda arriba | Mientras lo tengas up | Queda arriba |
| **Quién lo dispara** | DevSecOps job `staging-deploy-e2e`, Jenkins, o manual | Push a `develop` | Manual | Push a `main` |

---

### Tabla — Todos los Compose

| Compose | Perfil | Levanta | Cuándo |
| ------- | ------ | ------- | ------ |
| `docker-compose.yml` | `docker` | Postgres, API, FE, Keycloak, Tempo, Loki, Alloy, Prometheus, Alertmanager, Grafana, **Jenkins** (8080/3000/8081/3001/8082) | Dev diario + OBS + ZAP en DevSecOps |
| `docker-compose.staging.yml` | `staging` | Postgres, API, FE, Keycloak + OBS (8088/3008/8181) | Emular staging; CI efímero |
| `docker-compose.prod.yml` | `prod` | Stack prod-like local | Emular prod; **no** es Render |
| `docker-compose.security.yml` | `docker` | Solo Postgres + Keycloak + API (8090/8091/5435) | Jenkins Security (smoke + ZAP) |

---

### Tabla — Todos los pipelines (yml / Jenkinsfile)

| Pipeline | Archivo | Trigger | Qué hace / qué levanta |
| -------- | ------- | ------- | ---------------------- |
| **DevSecOps** | `.github/workflows/devsecops.yml` | Push/PR `develop` | Calidad: tests, JaCoCo, Sonar, Docker build. ZAP → `docker-compose.yml`. Staging E2E → `docker-compose.staging.yml` → down. Quality-gate. **No** publica Render/Vercel |
| **Deploy staging** | `.github/workflows/deploy-staging.yml` | Push `develop` | Publica: `render.yaml` (DB+API+KC) + Vercel FE. Smoke cloud opcional |
| **Deploy prod** | `.github/workflows/deploy-prod.yml` | Push `main` | Publica: `render.prod.yaml` + Vercel FE prod |
| **Conventional commits** | `.github/workflows/conventional-commits.yml` | PR develop/main | Valida mensajes de commit |
| **Legacy CI** | `.github/workflows/ci.yml` | Manual | Build + tests + Sonar |
| **Legacy Security** | `.github/workflows/security.yml` | Manual | Dep-Check + ZAP |
| **Legacy Post-deploy staging** | `.github/workflows/post-deploy-staging.yml` | Manual | Solo `docker-compose.staging.yml` + smoke + Playwright |
| **Jenkins** | `infra/jenkins/Jenkinsfile` | Manual `:8082` | Espejo DevSecOps: Security → `docker-compose.security.yml`; Staging → `docker-compose.staging.yml`. **Sin** cloud |

```text
DevSecOps:
  build-and-test → docker-images
                 → dependency-check
                 → zap-baseline        (docker-compose.yml)
                 → staging-deploy-e2e  (docker-compose.staging.yml → down)
                 → quality-gate

deploy-staging / deploy-prod:
  trigger-render → deploy-vercel → smoke-cloud (opcional)
```

---

### Perfiles Spring

| `SPRING_PROFILES_ACTIVE` | Mergea | Lo setea |
| ------------------------ | ------ | -------- |
| `docker` | `application-docker.yml` | `docker-compose.yml`, `docker-compose.security.yml` |
| `staging` | `application-staging.yml` | `docker-compose.staging.yml`, API Render staging |
| `prod` | `application-prod.yml` | `docker-compose.prod.yml`, API Render prod |

Siempre + `application.yml`.

### Frase cierre

“Compose staging/prod = emular. deploy-staging/prod yml = publicar a Render+Vercel. DevSecOps = calidad (usa los Compose). 512 MB free → OOM Keycloak → OBS solo local.”

---

## 11. Observabilidad: Grafana + Prometheus + Loki + Tempo + Alloy (TODO)

### Respuesta corta (sí / no)

| Pregunta | Respuesta |
| -------- | --------- |
| ¿Tenemos trazas de **API** en Grafana? | **Sí** — spans HTTP (`http get /api/v1/products`, etc.) en **Tempo** |
| ¿Tenemos trazas de **base de datos**? | **Sí** — spans **JDBC** (`query` / `fetch`) **dentro** del mismo waterfall de la traza HTTP |
| ¿Tenemos logs de Postgres crudos en Loki? | **No** — scrapamos logs del contenedor **inventory-api**, no del contenedor postgres |
| ¿Dónde se ve todo? | Grafana http://localhost:3001 (`admin`/`admin`) — dashboard **Observabilidad** |

**Frase oral clave:**  
“Una petición a productos genera una traza en Tempo: el span padre es la API HTTP y debajo salen los spans JDBC de las queries a Postgres. Eso es correlación API → base de datos.”

---

### Flujo visual completo (cómo está cableado)

```text
┌──────────────────────── inventory-api (Spring) ────────────────────────┐
│  Micrometer → /actuator/prometheus                                     │
│  OpenTelemetry traces (service.name=inventory-api) → OTLP HTTP :4318 │
│  Logs stdout con [traceId,spanId]                                      │
│  datasource-micrometer → spans JDBC (query, fetch)                     │
└───────────────┬──────────────────────────┬─────────────────────────────┘
                │ scrape cada 15s          │ OTLP                        │ docker logs
                ▼                          ▼                             ▼
         ┌────────────┐            ┌─────────────┐              (mismo Alloy)
         │ Prometheus │            │    Alloy    │◄──────────────────────┘
         │   :9090    │            │ :4317/:4318 │
         └─────┬──────┘            └──────┬──────┘
               │                          │
               │                    ┌─────┴─────┐
               │                    ▼           ▼
               │               ┌────────┐  ┌────────┐
               │               │ Tempo  │  │  Loki  │
               │               │ :3200  │  │ :3100  │
               │               └────┬───┘  └───┬────┘
               │                    │          │
               └────────────┬───────┴─────┬────┘
                            ▼
                     ┌─────────────┐
                     │   Grafana   │  :3001 (host) → :3000 (container)
                     │ datasources │  Prometheus + Tempo + Loki (provisionados)
                     │ dashboards  │  Observabilidad, App, Infra, Security…
                     └─────────────┘
                            │
                     Alertmanager :9093  ← reglas Prometheus (alerts.yml)
```

---

### Qué hace cada pieza (guion oral largo)

**Prometheus** es el almacén de **métricas** (números en el tiempo: req/s, latencia, CPU, 401/403, pool Hikari…). No guarda trazas ni logs. Cada 15 segundos scrapea `http://api:8080/actuator/prometheus` (job `inventory-api` en [`infra/prometheus/prometheus.yml`](../../../infra/prometheus/prometheus.yml)). Grafana consulta Prometheus con PromQL para los paneles de arriba del dashboard.

**Tempo** es el almacén de **trazas** (distributed tracing). Cada request instrumentada es una traza con un TraceID y varios **spans** (pasos). La API exporta por OTLP a Alloy; Alloy reenvía a Tempo. En Grafana Explore o en el dashboard ves filas tipo `http get /api/v1/products`. Al hacer clic abres el **waterfall**: ahí está la API y, si hubo acceso a BD, los spans JDBC hijos.

**Loki** es el almacén de **logs** (texto). Alloy scrapea los logs Docker del contenedor llamado `inventory-api` y los empuja a Loki con label `job=inventory-api`. En los logs Spring imprime `[traceId,spanId]` (pattern de correlación en `application.yml`). Por eso desde un log puedes saltar a Tempo.

**Grafana Alloy** es el **repartidor / agente**: recibe OTLP (trazas y, si hubiera, logs OTLP), hace batch, exporta trazas a Tempo y logs a Loki; además lee el socket Docker para los logs del contenedor API. La app **no** habla directo con Tempo/Loki: habla con Alloy (`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://alloy:4318/v1/traces` en Compose). Si mañana cambiáramos Tempo por otro backend, tocaríamos Alloy, no el código Java.

**Grafana** es solo la **UI**. No guarda las tres señales ella sola: tiene **datasources provisionados** que apuntan a los servicios de la red Docker:

| Datasource | URL interna | uid | Rol |
| ---------- | ----------- | --- | --- |
| Prometheus | `http://prometheus:9090` | `prometheus` | Métricas (default) |
| Tempo | `http://tempo:3200` | `tempo` | Trazas |
| Loki | `http://loki:3100` | `loki` | Logs |

Eso está en [`infra/grafana/provisioning/datasources/datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml). Los JSON de dashboards se montan desde [`infra/grafana/dashboards/`](../../../infra/grafana/dashboards/) vía [`provisioning/dashboards/dashboard.yml`](../../../infra/grafana/provisioning/dashboards/dashboard.yml) (carpeta “Observabilidad”).

**Enlaces cruzados (lo bonito de la demo):**

1. **Loki → Tempo:** en Loki hay `derivedFields`: regex del `[traceId,…]` en el log → botón “View Trace” abre Tempo.
2. **Tempo → Loki:** Tempo tiene `tracesToLogsV2` hacia Loki filtrando por TraceID y `{job="inventory-api"}`.
3. **Tempo → Prometheus:** `serviceMap` usa Prometheus para el mapa de servicio.

Así demuestras las **tres señales** unidas, no tres herramientas sueltas.

---

### Config en la API (por qué salen trazas API + JDBC)

En [`application.yml`](../../../src/main/resources/application.yml):

- `management.tracing` + `opentelemetry.resource-attributes.service.name: inventory-api` → nombre del servicio en Tempo.
- Sampling `1.0` en demo → 100% de trazas (en prod bajaría).
- Export OTLP solo si Compose pone el endpoint a Alloy.
- `jdbc.includes: query, fetch` + dependencia `datasource-micrometer-spring-boot` en Gradle → **spans de base de datos** dentro de la traza HTTP.
- `logging.pattern.correlation: "[%X{traceId:-},%X{spanId:-}] "` → el TraceID aparece en logs → Loki puede linkear.

**Importante para explicar:** los spans JDBC **no** son una fila aparte “database” en la lista de Tempo. Son **hijos** del span HTTP. Si solo miras `/actuator/health` o `/actuator/prometheus`, casi no hay SQL → no verás JDBC. Hay que abrir una traza de **`/api/v1/products`** (u otro endpoint de negocio).

---

### Dashboards que tenemos

| Dashboard | Archivo | Qué muestra |
| --------- | ------- | ----------- |
| **Observabilidad — Métricas, Logs y Trazas** | `observability.json` | **El de la demo:** Prom + Loki + Tempo juntos |
| App | `app.json` | Throughput, latencia, 4xx/5xx |
| Infra | `infra.json` | CPU, heap, hilos, HikariCP |
| Security | `security.json` | 401, 403 (muy bueno con viewer) |
| Negocio | `business.json` | Tráfico HTTP a products/stock/reports |
| API Ops | `api-ops.json` | Vista corta del avance |

---

### Cómo llenar datos antes de la defensa

```bash
docker compose up -d --build postgres keycloak api alloy tempo loki prometheus grafana
./scripts/wait-for-stack.sh   # opcional
./scripts/generate-obs-traffic.sh
# Espera 15–30 s → http://localhost:3001  admin/admin
```

El script pide JWT y hace curls 200/401/403/404 → métricas, logs y trazas.

---

### Archivos (abre y señala)

| Pieza | Archivo |
| ----- | ------- |
| Datasources Grafana | [`infra/grafana/provisioning/datasources/datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml) |
| Provision dashboards | [`infra/grafana/provisioning/dashboards/dashboard.yml`](../../../infra/grafana/provisioning/dashboards/dashboard.yml) |
| Dashboard unificado | [`infra/grafana/dashboards/observability.json`](../../../infra/grafana/dashboards/observability.json) |
| Alloy (OTLP + docker logs) | [`infra/alloy/config.alloy`](../../../infra/alloy/config.alloy) |
| Prometheus scrape | [`infra/prometheus/prometheus.yml`](../../../infra/prometheus/prometheus.yml) |
| Alertas | [`infra/prometheus/alerts.yml`](../../../infra/prometheus/alerts.yml) |
| Tempo / Loki config | [`infra/tempo/tempo.yml`](../../../infra/tempo/tempo.yml) · [`infra/loki/loki.yml`](../../../infra/loki/loki.yml) |
| OTel + JDBC + correlation | [`application.yml`](../../../src/main/resources/application.yml) |
| Compose (servicios + OTLP endpoint) | [`docker-compose.yml`](../../../docker-compose.yml) |
| Tráfico demo | [`scripts/generate-obs-traffic.sh`](../../../scripts/generate-obs-traffic.sh) |

### Frase de cierre §11

“Grafana es la UI; Prometheus métricas, Loki logs, Tempo trazas; Alloy reparte; la API exporta OTLP y scrapea Actuator; todo provisionado en Compose local.”

---

## 12. Trazas de API y de base de datos — sí las tenemos

### Flujo visual (waterfall que debes enseñar)

```text
TraceID abc123…   service.name = inventory-api
│
├─ span: http get /api/v1/products          ← TRAZA DE API (HTTP)
│    │
│    ├─ span: JDBC query SELECT … products  ← TRAZA DE BD
│    └─ span: JDBC fetch
│
└─ (otros spans internos si aplica)
```

### Guion (léelo así mañana)

Sí tenemos trazas de API y de base de datos en Grafana/Tempo. No son dos sistemas distintos: son **una sola traza** con varios spans. El span raíz (o el principal) es la petición HTTP a la API — eso demuestra latencia y ruta (`/api/v1/products`). Como tenemos `datasource-micrometer` y `jdbc.includes: query, fetch`, cuando el controller/service/repository toca Postgres se abren spans hijos JDBC. Eso es la evidencia de base de datos que le gusta al profesor: ves cuánto tardó el SQL dentro del mismo TraceID.

No confundir con “logs de Postgres en Loki”. Loki tiene los logs de la **aplicación** (`inventory-api`), que ya llevan el TraceID. La BD se ve mejor en el **waterfall de Tempo**, no como un log del motor PostgreSQL.

Pasos exactos en la demo:

1. Genera tráfico de negocio: `./scripts/generate-obs-traffic.sh` o navega Productos logueado.
2. Grafana → dashboard **Observabilidad — Métricas, Logs y Trazas**.
3. Panel **Trazas recientes — service.name = inventory-api**.
4. Elige un TraceID cuyo **Name** sea `http get /api/v1/products` (o similar). **Evita** solo `actuator/health` o `actuator/prometheus`.
5. Clic → waterfall → señala el span HTTP y debajo los **JDBC**.
6. Bonus: panel Logs → clic en TraceID → misma traza (Loki→Tempo). Bonus 2: desde la traza, “Logs for this trace”.

Si preguntan “¿y Prometheus?”: Prometheus no guarda el waterfall; guarda contadores/histogramas de esas mismas requests (throughput, errores). Las tres señales cuentan la misma historia desde ángulos distintos.

### Archivos

| Pieza | Archivo |
| ----- | ------- |
| JDBC spans | [`application.yml`](../../../src/main/resources/application.yml) (`jdbc.includes`) |
| Dep | [`build.gradle`](../../../build.gradle) (`datasource-micrometer-spring-boot`) |
| Dashboard | [`observability.json`](../../../infra/grafana/dashboards/observability.json) |
| Link Loki↔Tempo | [`datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml) |

### Frase de cierre §12

“API y BD en la misma traza Tempo: HTTP arriba, JDBC abajo; Loki aporta el log con el mismo TraceID.”

---

## 13. Ambientes y deploy

Ver tablas de la **[§10](#10-cicd--compose-emular-vs-deploy-yml-publicar)** (Compose staging/prod vs `deploy-*.yml`, todos los Compose, todos los pipelines).

Resumen: emular con Compose → publicar con deploy yml + Render/Vercel; 512 MB free → OBS solo local; dos stagings (CI efímero vs cloud persistente).

---

## 14. Qué nos faltaría / checklist anti-blanco

### Ya cubrimos bien (di que sí)

- Auth Keycloak + OAuth2 resource server + roles  
- FE React + permisos UI  
- Backend capas + Swagger + Flyway + Envers  
- Paginación server-side  
- Unit / API / contract / IT / E2E / k6 / ZAP / smoke  
- JaCoCo + Sonar + DevSecOps + Jenkins  
- Grafana métricas + logs + trazas (incl. JDBC)  
- Staging/prod cloud Render+Vercel  

### Matiza si preguntan (honestidad = puntos)

| Tema | Qué decir |
| ---- | --------- |
| OBS en cloud | Solo Compose local (límites free tier) |
| k6 en GHA | Manual/local, no en DevSecOps |
| Alta de usuarios en la app | Solo listado; altas en consola Keycloak |
| Logs “crudos” de Postgres en Loki | No scrapamos postgres; **sí** hay trazas JDBC en Tempo (misma traza que la API) — ver §11–§12 |
| Microservicios | No; monolito modular |

### Checklist noche previa (2–3 h)

1. [ ] Stack: `docker compose up -d --build` + health API  
2. [ ] Login admin + viewer (403 / unauthorized)  
3. [ ] Curl token + Swagger Authorize + GET products  
4. [ ] `./scripts/generate-obs-traffic.sh` → Grafana Observabilidad + **waterfall JDBC**  
5. [ ] Abrir `DockerSecurityConfig` + `application-docker.yml` + `inventory-realm.json`  
6. [ ] Decir de memoria: unit vs IT vs Playwright vs k6  
7. [ ] Decir: DevSecOps vs deploy-staging vs Jenkins  
8. [ ] Envers: editar producto → audit API  
9. [ ] Flyway: enseñar carpeta `V1__`  
10. [ ] Leer en voz alta la sección 1 completa (Keycloak)

### Orden de presentación

- **25 min hablando + demos cortas:** sección **[Guion oral literal — 25 minutos](#guion-oral-literal--25-minutos-cronometrado)** al inicio de este archivo (léelo palabra por palabra).
- **Demo solo 10–12 min** (si ya explicaste código): UI permisos → Swagger JWT → Grafana JDBC → una frase CI/CD → preguntas.

---

## Cheat: una línea por herramienta

| Herramienta | Una línea |
| ----------- | --------- |
| Keycloak | Emite y firma el JWT; usuarios/roles en realm JSON |
| OAuth2 RS | API valida firma con JWKS; 401/403 |
| React | UI + manda Bearer; no valida firma |
| Flyway | Versiona esquema SQL |
| Envers | Historial de cambios de Product |
| Paginación | Pageable → PageResponse |
| Unit | Mockito, sin Docker |
| API test | MockMvc + authorities |
| IT | Testcontainers Postgres/KC |
| Playwright | E2E navegador |
| k6 | Load/stress VUs |
| JaCoCo | % cobertura |
| Sonar | Quality gate |
| Prometheus | Métricas |
| Loki | Logs |
| Tempo | Trazas (+ JDBC) |
| Alloy | Repartidor OTLP |
| Jenkins | Pipeline local |
| DevSecOps | Gate en GitHub |
| Render/Vercel | Staging/prod cloud |

---

*Si te trabas: vuelve al mapa de la sección inicial y a la frase “Keycloak firma; el front transporta; Spring valida.”*
