# Charter EC-01 — Stock edge cases

| Campo | Valor |
|-------|--------|
| **ID** | EC-01 |
| **Título** | Movimientos de stock: bordes e integridad |
| **Tester** | Jean Pérez |
| **Fecha** | 2026-07-23 |
| **Duración planificada** | 45 min |
| **Duración real** | 45 min |
| **Ambiente** | Docker local (`api` :8080, `frontend` :3000), perfil `docker` |
| **Misión** | Explorar entradas, salidas y ajustes de stock buscando inconsistencias de cantidad, mensajes de error y UI vs API |

## Charter (guía)

Explorar el dominio de **stock** priorizando:

- Salida mayor al stock disponible
- Cantidad 0 / negativa en IN, OUT, ADJUSTMENT
- Ajuste a 0 vs ajuste negativo
- Producto inexistente / inactivo
- Coherencia UI `/stock` vs API `POST /api/v1/stock/movements`
- Historial de movimientos tras operaciones válidas

## Datos / setup

- Login: `admin` / `admin` (UI) y JWT admin (API)
- Producto existente con `quantity > 0` (crear uno si la lista está vacía)

## Notas de sesión (time-boxed)

### 0–15 min — API bordes

1. `OUT` con `quantity` mayor al disponible → **rechazado** (`IllegalArgumentException` / 400) con mensaje `Insufficient stock: requested …, available …` (`StockService`).
2. `IN` / `OUT` con `quantity < 1` → rechazado (“must be at least 1”).
3. `ADJUSTMENT` con `quantity < 0` → rechazado (“cannot be negative”).
4. `ADJUSTMENT` a `0` → **aceptado** (stock queda en 0) — comportamiento intencional documentado en controller.

### 15–30 min — UI `/stock`

5. Formulario de movimiento: tipos IN / OUT / ADJUSTMENT visibles para `admin` y `stock-manager`.
6. Intento de salida excesiva desde UI: mensaje de error al usuario (no deja cantidad negativa en listado).
7. Tras IN válido, la cantidad del producto se actualiza en listado de productos / stock.

### 30–45 min — Consistencia

8. `GET /api/v1/stock/movements` muestra el movimiento recién creado con `quantityBefore` / `quantityAfter` coherentes.
9. `viewer` no puede registrar movimiento (403 API / UI sin acción de registro) — cruzado con EC-02.

## Bugs encontrados

| ID | Severidad | Descripción | Estado |
|----|-----------|-------------|--------|
| — | — | Ningún defecto funcional nuevo en esta sesión | — |

## Observaciones (no bug)

- Validación de stock insuficiente está en servicio (bien ubicada).
- `@Min(0)` en DTO permite 0 en request; la regla de negocio IN/OUT ≥ 1 se aplica en servicio (doble capa OK).

## Oráculos / referencias

- `StockService.calculateQuantityAfter`
- `StockController` OpenAPI notes
- README — usuarios demo y permisos
