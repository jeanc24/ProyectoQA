# Documentación de requisitos — ProyectoQA

Documento de **requisitos funcionales (RF)** y **no funcionales (RNF)** del Sistema de Gestión de Inventarios Empresarial (PUCMM — Aseguramiento de Calidad).

| Campo | Valor |
| ----- | ----- |
| Sistema | ProyectoQA — inventario + Keycloak + observabilidad |
| Versión | Alineada a rama `main` / `develop` del monorepo |
| Actores | Administrador, Viewer, Stock-manager, Auditor (usuarios demo Keycloak) |
| Evidencia en código | Controllers, servicios, realm Keycloak, UI React |

Relacionados: [Documentación técnica](TECNICA.md) · [Guía de pruebas](GUIA-PRUEBAS.md) · [Defensa](defensa/README.md)

---

## 1. Alcance

### Incluye

- CRUD de productos y categorías
- Movimientos de stock (IN / OUT / ADJUSTMENT) con historial
- Reportes / dashboard (KPIs, low stock, top products, movimientos recientes)
- Auditoría de cambios de producto (Hibernate Envers)
- Autenticación OAuth2/OIDC (Keycloak) y autorización por permisos granulares
- Listado de usuarios vía Admin API de Keycloak
- API REST documentada (OpenAPI/Swagger en no-prod)
- UI web (React) para productos, stock, dashboard, usuarios
- Ambientes local, staging y producción (Compose + cloud)
- Pipeline CI/CD (calidad, security, deploy)
- Observabilidad local (métricas, logs, trazas, alertas)

---

## 2. Actores y permisos

Los “roles” de aplicación son **permisos** del cliente `inventory-api` en Keycloak:

| Permiso | Descripción |
| ------- | ----------- |
| `product:view` | Consultar productos y categorías |
| `product:manage` | Crear / editar / eliminar productos |
| `stock:view` | Consultar movimientos e historial de stock |
| `stock:manage` | Registrar movimientos IN/OUT/ADJUSTMENT |
| `report:view` | Ver dashboard y endpoints de reportes |
| `audit:view` | Ver historial Envers de un producto |
| `user:manage` | Listar usuarios del realm |

| Usuario demo | Permisos |
| ------------ | -------- |
| `admin` | Los 7 |
| `viewer` | `product:view`, `stock:view` |
| `stock-manager` | `product:view`, `stock:view`, `stock:manage` |
| `auditor` | `product:view`, `stock:view`, `audit:view` |

Fuente: [`keycloak/inventory-realm.json`](../../keycloak/inventory-realm.json).

---

## 3. Requisitos funcionales

### RF-01 — Autenticación

| ID | RF-01 |
| -- | ----- |
| Descripción | El usuario debe autenticarse vía Keycloak (OIDC) antes de usar la UI y la API protegida. |
| Criterios de aceptación | Login UI redirige a Keycloak; API sin token → 401; token válido → acceso según permisos. |
| Evidencia | `frontend/src/auth/*`, `DockerSecurityConfig`, E2E `login.spec.ts` |

### RF-02 — Autorización por permiso

| ID | RF-02 |
| -- | ----- |
| Descripción | Cada operación de API/UI exige el permiso correspondiente (`@PreAuthorize` + ocultación UX). |
| Criterios de aceptación | Viewer no crea productos (403); stock-manager registra stock; auditor ve historial; sin permiso → 403. |
| Evidencia | Controllers + `permissions.spec.ts` + `security-smoke.sh` + EC-02 |

### RF-03 — Gestión de productos

| ID | RF-03 |
| -- | ----- |
| Descripción | CRUD de productos: nombre, SKU único, descripción, categoría, precio, quantity, minStock, active. |
| Criterios de aceptación | POST 201; SKU duplicado 409; GET listado con filtros/paginación; PUT actualiza; DELETE 204. |
| Evidencia | `ProductController`, `ProductService`, UI `/products` |

### RF-04 — Categorías

| ID | RF-04 |
| -- | ----- |
| Descripción | Listar categorías para asociar a productos. |
| Criterios de aceptación | `GET /api/v1/categories` con `product:view` → 200. |
| Evidencia | `CategoryController` |

### RF-05 — Movimientos de stock

| ID | RF-05 |
| -- | ----- |
| Descripción | Registrar IN (suma), OUT (resta sin negativo), ADJUSTMENT (valor absoluto ≥ 0) e historial. |
| Criterios de aceptación | OUT > stock → error; IN/OUT quantity ≥ 1; ADJUSTMENT a 0 permitido; historial con before/after. |
| Evidencia | `StockService`, `StockController`, EC-01, E2E `stock.spec.ts` |

### RF-06 — Historial de stock por producto

| ID | RF-06 |
| -- | ----- |
| Descripción | Consultar movimientos de un producto concreto. |
| Criterios de aceptación | `GET /api/v1/products/{id}/stock/history` paginado con `stock:view`. |
| Evidencia | `ProductStockController` |

### RF-07 — Reportes / dashboard

| ID | RF-07 |
| -- | ----- |
| Descripción | Resumen de inventario, low stock, movimientos recientes y top productos. |
| Criterios de aceptación | Endpoints `/api/v1/reports/*` con `report:view`; UI `/dashboard` visible solo con permiso. |
| Evidencia | `ReportController`, E2E `dashboard.spec.ts` |

### RF-08 — Auditoría de productos (Envers)

| ID | RF-08 |
| -- | ----- |
| Descripción | Consultar revisiones históricas de un producto. |
| Criterios de aceptación | `GET /api/v1/audit/products/{id}` con `audit:view`; UI muestra Historial para admin/auditor. |
| Evidencia | `AuditController`, `AuditService`, `@Audited` en `Product` |

### RF-09 — Usuarios

| ID | RF-09 |
| -- | ----- |
| Descripción | Listar usuarios del realm vía Admin API de Keycloak. |
| Criterios de aceptación | `GET /api/v1/users` con `user:manage`; UI directorio de usuarios. |
| Evidencia | `UserController`, `KeycloakAdminClient` |

### RF-10 — Manejo de errores HTTP

| ID | RF-10 |
| -- | ----- |
| Descripción | Errores de negocio/validación se traducen a códigos HTTP consistentes. |
| Criterios de aceptación | 400 validación; 401/403 authz; 404 not found; 409 conflicto; cuerpo JSON homogéneo. |
| Evidencia | `GlobalExceptionHandler` |

### RF-11 — Documentación de API

| ID | RF-11 |
| -- | ----- |
| Descripción | OpenAPI/Swagger disponible en ambientes no producción. |
| Criterios de aceptación | Swagger UI en local/staging; deshabilitado o endurecido en perfil `prod`. |
| Evidencia | `OpenApiConfig`, `application-prod.yml` |

### RF-12 — Migraciones de esquema

| ID | RF-12 |
| -- | ----- |
| Descripción | El esquema de BD se aplica de forma versionada al arrancar. |
| Criterios de aceptación | Flyway ejecuta `V*.sql` al startup; app no arranca si falla migración crítica. |
| Evidencia | `src/main/resources/db/migration/` |

---

## 4. Requisitos no funcionales

### RNF-01 — Seguridad

| ID | RNF-01 |
| -- | ------ |
| Descripción | API como OAuth2 Resource Server; JWT validado contra JWKS; secretos fuera del código. |
| Métrica / evidencia | ZAP baseline, Dependency-Check, `security-smoke.sh`, perfiles `docker`/`staging`/`prod` |

### RNF-02 — Ambientes

| ID | RNF-02 |
| -- | ------ |
| Descripción | Development, Preview/Staging y Production separados (Compose y/o cloud). |
| Métrica / evidencia | `docker-compose*.yml`, `render.yaml`, `render.prod.yaml`, `ENVIRONMENTS.md` |

### RNF-03 — CI/CD

| ID | RNF-03 |
| -- | ------ |
| Descripción | Pipeline automatizado de build, tests, calidad, security y deploy. |
| Métrica / evidencia | `devsecops.yml`, `deploy-staging.yml`, `deploy-prod.yml`, Jenkinsfile |

### RNF-04 — Pruebas automatizadas

| ID | RNF-04 |
| -- | ------ |
| Descripción | Cobertura de unit, API, integration, E2E, security y performance. |
| Métrica / evidencia | ≥ 95 métodos JUnit + 12 E2E + smokes/ZAP/k6; ver [GUIA-PRUEBAS.md](GUIA-PRUEBAS.md) |

### RNF-05 — Calidad de código

| ID | RNF-05 |
| -- | ------ |
| Descripción | Quality gate SonarCloud; cobertura mínima de servicios de negocio. |
| Métrica / evidencia | JaCoCo gate servicios ≥ 60%; badge Sonar en README |

### RNF-06 — Observabilidad

| ID | RNF-06 |
| -- | ------ |
| Descripción | Métricas, logs y trazas correlacionables en ambiente local. |
| Métrica / evidencia | Actuator/Prometheus, Loki, Tempo, Alloy, Grafana, Alertmanager |

### RNF-07 — Rendimiento (baseline)

| ID | RNF-07 |
| -- | ------ |
| Descripción | Carga y estrés medibles con k6; no hay SLA contractual, sí evidencia. |
| Métrica / evidencia | `tests/k6/`, `docs/final/testing/k6/README.md` |

### RNF-08 — Portabilidad / contenedores

| ID | RNF-08 |
| -- | ------ |
| Descripción | Stack reproducible con Docker Compose; misma imagen API en local y cloud. |
| Métrica / evidencia | Dockerfiles + Compose + Render runtime docker |

### RNF-09 — Mantenibilidad

| ID | RNF-09 |
| -- | ------ |
| Descripción | Arquitectura por capas, conventional commits, documentación de defensa. |
| Métrica / evidencia | Paquetes `controller`/`service`/`domain`, `ARBOL.md`, `PREGUNTAS.md` |

### RNF-10 — Disponibilidad (demo)

| ID | RNF-10 |
| -- | ------ |
| Descripción | Healthchecks y reinicio de contenedores; free tier puede cold-start. |
| Métrica / evidencia | `actuator/health`, Compose `restart` / Render health paths |

---

## 5. Trazabilidad RF → pruebas (resumen)

| RF | Pruebas principales |
| -- | ------------------- |
| RF-01 | E2E login, Keycloak IT, security-smoke |
| RF-02 | `*ApiScenarioTest`, permissions E2E, EC-02 |
| RF-03 | `ProductServiceTest`, `ProductApiScenarioTest`, E2E products |
| RF-04 | API + integration |
| RF-05 | `StockServiceTest`, stock E2E, EC-01 |
| RF-06 | Integration / API stock history |
| RF-07 | `ReportServiceTest`, dashboard E2E |
| RF-08 | `AuditApiScenarioTest`, EC-02 auditor |
| RF-09 | User API + E2E users directory |
| RF-10 | `GlobalExceptionHandlerTest` |
| RF-11 | `OpenApiContractTest` |
| RF-12 | `DataIntegrityIntegrationTest` / Flyway en IT |

Detalle de casos: [GUIA-PRUEBAS.md](GUIA-PRUEBAS.md) y [`testing/README.md`](testing/README.md).

---

## 6. Prioridad

| Prioridad | IDs |
| --------- | --- |
| Must | RF-01…RF-08, RF-10, RF-12, RNF-01…RNF-06, RNF-08 |
| Should | RF-09, RF-11, RNF-07, RNF-09 |
| Could | RNF-10 (HA formal), mejoras UX exploratorias |
