# Charter EX-02 — Permisos por rol

| Campo | Valor |
|-------|--------|
| **ID** | EX-02 |
| **Tester** | Emilio |
| **Fecha** | _completar al ejecutar_ |
| **Duración** | _minutos reales_ (objetivo 45–60 min) |
| **Entorno** | Docker local — http://localhost:3000 |
| **Usuarios** | `viewer`, `stock-manager`, `auditor`, `admin` (password = username) |

## Misión

Verificar que la **matriz de permisos** se refleja en la UI (menú, formularios, rutas) y que no hay escalada trivial: un rol de solo lectura no debe crear productos ni registrar movimientos; quien no tiene `report:view` no debe ver el dashboard.

## Alcance

**Incluye**

- Login con los 4 usuarios demo
- Nav: Productos / Stock / Dashboard según permiso
- Rutas protegidas: `/products`, `/stock`, `/dashboard` → `/unauthorized` si falta permiso
- Formulario de stock oculto sin `stock:manage` (“Solo lectura”)
- Acciones CRUD de productos ocultas sin `product:manage`

**No incluye**

- Consola de administración Keycloak
- `user:manage` (sin UI dedicada en el frontend actual)
- API con JWT a mano (salvo duda; la evidencia principal es UI)

## Matriz esperada (README)

| Usuario | product:view | product:manage | stock:view | stock:manage | report:view | audit:view |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| admin | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| viewer | ✓ | | ✓ | | | |
| stock-manager | ✓ | | ✓ | ✓ | | |
| auditor | ✓ | | ✓ | | | ✓ |

> En el frontend actual **no hay página `/audit`**. `audit:view` se consume vía API; en UI el auditor se comporta como lector de productos/stock (sin manage ni dashboard).

## Pasos a explorar

### A — `viewer`

| # | Acción | Esperado | Observado | Evidencia |
|---|--------|----------|-----------|-----------|
| 1 | Login viewer → `/products` | Ve listado; **sin** botones crear/editar/borrar | | `ex02-viewer-products.png` |
| 2 | Nav: ¿aparece Dashboard? | **No** (falta `report:view`) | | _(misma o)_ `ex02-viewer-nav.png` |
| 3 | Ir a `/stock` | Historial visible; texto “Solo lectura”; **sin** formulario registrar | | `ex02-viewer-stock-readonly.png` |
| 4 | Pegar URL `/dashboard` | Redirección a `/unauthorized` | | `ex02-viewer-unauthorized-dashboard.png` |

### B — `stock-manager`

| # | Acción | Esperado | Observado | Evidencia |
|---|--------|----------|-----------|-----------|
| 5 | Login → `/stock` | Formulario de movimiento **sí** visible | | `ex02-sm-stock-form.png` |
| 6 | `/products` | Solo lectura (sin manage) | | `ex02-sm-products-readonly.png` |
| 7 | URL `/dashboard` | `/unauthorized` | | `ex02-sm-unauthorized-dashboard.png` |
| 8 | Registrar un IN pequeño | Éxito (tiene `stock:manage`) | | `ex02-sm-movement-ok.png` |

### C — `auditor`

| # | Acción | Esperado | Observado | Evidencia |
|---|--------|----------|-----------|-----------|
| 9 | Login → productos + stock | Lectura OK; sin formularios manage | | `ex02-auditor-readonly.png` |
| 10 | `/dashboard` | `/unauthorized` | | `ex02-auditor-unauthorized.png` |

### D — `admin`

| # | Acción | Esperado | Observado | Evidencia |
|---|--------|----------|-----------|-----------|
| 11 | Nav completa + crear producto + movimiento + dashboard | Todo accesible | | `ex02-admin-full-access.png` |

## Notas de sesión

1. 
2. 
3. 

## Bugs / hallazgos de esta sesión

| ID | Severidad | Descripción | Evidencia |
|----|-----------|-------------|-----------|
| — | — | _Ninguno_ **o** BUG-EX02-01 … | |

## Evidencias (mínimo)

- [ ] `ex02-viewer-products.png`
- [ ] `ex02-viewer-stock-readonly.png`
- [ ] `ex02-viewer-unauthorized-dashboard.png`
- [ ] `ex02-sm-stock-form.png`
- [ ] `ex02-sm-unauthorized-dashboard.png`
- [ ] `ex02-auditor-readonly.png`
- [ ] `ex02-admin-full-access.png`
