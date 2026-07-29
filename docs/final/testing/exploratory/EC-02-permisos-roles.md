# Charter EC-02 — Permisos por rol

| Campo | Valor |
|-------|--------|
| **ID** | EC-02 |
| **Título** | Permisos granulares: UI y API |
| **Tester** | Jean Pérez |
| **Fecha** | 2026-07-23 |
| **Duración planificada** | 40 min |
| **Duración real** | 40 min |
| **Ambiente** | Docker local + Keycloak realm `inventory` |
| **Misión** | Verificar que cada rol demo solo puede hacer lo autorizado; buscar fugas de autorización en UI y API |

## Charter (guía)

Explorar **matriz de permisos** con usuarios:

`admin`, `viewer`, `stock-manager`, `auditor`

Áreas: productos CRUD, stock, dashboard/reportes, auditoría (`audit:view` en `admin` y `auditor`).

## Matriz explorada (resultado sesión)

| Acción | admin | viewer | stock-manager | auditor |
|--------|-------|--------|---------------|---------|
| Login UI | ✓ | ✓ | ✓ | ✓ |
| Listar productos | ✓ | ✓ | ✓ | ✓ |
| Crear/editar/borrar producto | ✓ | ✗ (403/UI) | ✗ | ✗ |
| Historial Envers (botón / API) | ✓ | ✗ | ✗ | ✓ |
| Ver `/stock` | ✓ | ✓ | ✓ | ✓ |
| Registrar movimiento stock | ✓ | ✗ | ✓ | ✗ |
| Ver `/dashboard` / reports API | ✓ | ✗ 403 | ✗ | ✗ 403 |

## Notas de sesión

### 0–15 min — viewer

1. UI: no muestra crear producto ni **Historial**; no registra stock.
2. API: `POST /api/v1/products` → **403**.
3. API: `GET /api/v1/reports/inventory-summary` → **403**.
4. API: `GET /api/v1/audit/products/{id}` → **403**.
5. API: `GET /api/v1/products` → **200**.

### 15–30 min — stock-manager / admin

6. `stock-manager`: puede ver stock y registrar movimiento; no gestiona catálogo ni auditoría.
7. `admin`: CRUD productos, reportes, botón **Historial** con revisiones Envers.

### 30–40 min — sin token / token inválido

8. Sin `Authorization` → **401** en `/api/v1/products`.
9. Health `/actuator/health` sigue público **200**.

## Bugs encontrados

| ID | Severidad | Descripción | Estado |
|----|-----------|-------------|--------|
| — | — | Ninguna fuga de autorización detectada en la sesión | — |

## Observaciones

- Usuario demo `auditor` (`product:view`, `stock:view`, `audit:view`): ve Historial Envers en productos; sin manage ni dashboard.
- Matriz README coincide con comportamiento observado.
- Evidencia automatizada relacionada: `scripts/security-smoke.sh`, E2E `permissions.spec.ts`.

## Oráculos

- README “Permisos por usuario”
- `DockerSecurityConfig` + `@PreAuthorize` en controllers
- Keycloak `keycloak/inventory-realm.json`
