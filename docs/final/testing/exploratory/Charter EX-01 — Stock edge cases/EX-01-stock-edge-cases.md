# Charter EX-01 — Stock edge cases


| Campo                 | Valor                                                         |
| --------------------- | ------------------------------------------------------------- |
| **ID**                | EX-01                                                         |
| **Tester**            | Emilio                                                        |
| **Fecha**             | *completar al ejecutar* (ej. 2026-07-23)                      |
| **Duración**          | *minutos reales* (objetivo 45–60 min)                         |
| **Entorno**           | Docker local — [http://localhost:3000](http://localhost:3000) |
| **Usuario principal** | `stock-manager` / `stock-manager`                             |
| **Usuario apoyo**     | `admin` / `admin` (si hace falta preparar datos)              |




## Misión

Explorar **movimientos de stock** (IN / OUT / ADJUSTMENT) buscando fallos en validaciones de borde: stock insuficiente, ajuste a cero, stock bajo mínimo, cantidad inválida y feedback de la UI frente a la API.

## Alcance

**Incluye**

- Página `/stock` (formulario + historial + alerta de stock mínimo)
- Tipos `IN`, `OUT`, `ADJUSTMENT`
- Mensajes 400 (insuficiente / cantidad inválida) y preview de stock en UI
- Productos activos con `quantity` y `minStock`

**No incluye**

- Performance (k6), seguridad ZAP, E2E Playwright
- Auditoría Envers / API sin UI
- Concurrencia real de dos operadores a la vez (opcional si hay tiempo)



## Preparación

1. `docker compose up --build -d`
2. Login como `admin` → `/products` → crear o editar un producto de prueba, ej.:
  - Nombre: `EX-Stock Edge`
  - SKU: `EX-STOCK-01`
  - `quantity`: **5**
  - `minStock`: **5** (así ya está en / bajo mínimo)
3. Logout → login como `stock-manager`
4. Ir a **Stock** (`/stock`)



## Pasos a explorar (script de charter)

| # | Acción | Resultado esperado | Evidencia |
|---|--------|--------------------|-----------|
| 1 | Ver alerta de stock mínimo si hay productos `belowMinStock` | Banner amarillo `low-stock-alert` | `ex01-low-stock-alert.png` |
| 2 | Seleccionar producto stock 5 → tipo **OUT** → cantidad **6** → Registrar | UI avisa “insuficiente”; API 400; mensaje error visible | `ex01-out-insufficient.png` |
| 3 | OUT con cantidad **1** (stock 5) | Éxito; historial muestra OUT; stock queda 4 | `ex01-out-ok.png` |
| 4 | **ADJUSTMENT** cantidad **0** | Éxito; stock absoluto = 0 | `ex01-adjust-zero.png` |
| 5 | **IN** cantidad **0** o negativa (forzar en input) | UI o API rechaza (≥ 1 para IN) | `ex01-in-invalid.png` |
| 6 | Ajuste a valor ≤ `minStock` (ej. 3 con min 5) | Movimiento OK + alerta low-stock | `ex01-below-min.png` |
| 7 | OUT con notas vacías vs notas largas (~500 chars) | Vacío permitido; límite 500 respetado | `ex01-notes.png` |
| 8 | Filtrar historial por producto / tipo OUT | Lista coherente, paginación si aplica | `ex01-history-filter.png` |

### Referencias en código (Ctrl+clic)

Enlaces **fuera de la tabla** (Cursor a veces no abre links dentro de celdas). Rutas relativas a este archivo. A la derecha: qué hace ese trozo de código.

**1 — Low stock alert**

- [Product.java L74–76](../../../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) — `isBelowMinStock()`: true si `quantity <= minStock`
- [Stock.tsx L66–69](../../../../../frontend/src/pages/Stock.tsx) — filtra productos activos con `belowMinStock` para el banner
- [Stock.tsx L196–215](../../../../../frontend/src/pages/Stock.tsx) — pinta el banner amarillo `low-stock-alert` con nombre/SKU/stock/mín
- [ProductResponse.java](../../../../../src/main/java/icc354/pucmm/proyectoqa/dto/ProductResponse.java) — DTO de producto incluye el flag `belowMinStock`

**2 — OUT insuficiente**

- [Stock.tsx L303–310](../../../../../frontend/src/pages/Stock.tsx) — preview: si OUT > stock, avisa “(insuficiente — la API rechazará)”
- [StockService.java L88–95](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — OUT: si `quantity > before` lanza `InsufficientStockException`
- [GlobalExceptionHandler.java L37–45](../../../../../src/main/java/icc354/pucmm/proyectoqa/controller/GlobalExceptionHandler.java) — convierte esa excepción en HTTP 400
- [Stock.tsx L163–168](../../../../../frontend/src/pages/Stock.tsx) — muestra el error 400 (u otro) en el alert rojo de la UI

**3 — OUT OK**

- [StockService.java L55–77](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — `registerMovement`: calcula before/after, actualiza producto y guarda el movimiento
- [StockService.java L88–96](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — OUT válido: exige cantidad ≥ 1 y resta del stock (`before - quantity`)
- [Stock.tsx L157–162](../../../../../frontend/src/pages/Stock.tsx) — mensaje verde de éxito y recarga productos + historial
- [Stock.tsx L420–425](../../../../../frontend/src/pages/Stock.tsx) — en la tabla del historial muestra Δ y `antes → después`

**4 — ADJUSTMENT a 0**

- [StockService.java L98–102](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — ADJUSTMENT: cantidad ≥ 0 y fija el stock absoluto (`yield quantity`)
- [Stock.tsx L140–147](../../../../../frontend/src/pages/Stock.tsx) — validación previa en UI: ajuste ≥ 0; IN/OUT ≥ 1
- [Stock.tsx L272](../../../../../frontend/src/pages/Stock.tsx) — `min` del input: 0 si es ajuste, 1 si es IN/OUT
- [Stock.tsx L312–313](../../../../../frontend/src/pages/Stock.tsx) — preview: “→ se fijará en {quantity}”

**5 — IN inválido**

- [Stock.tsx L140–147](../../../../../frontend/src/pages/Stock.tsx) — rechaza en cliente cantidad &lt; 1 para IN (mensaje de error)
- [Stock.tsx L272](../../../../../frontend/src/pages/Stock.tsx) — el input HTML no permite bajar de 1 en IN/OUT (`min={1}`)
- [StockService.java L82–85](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — IN en backend: `quantity` debe ser ≥ 1 o lanza `IllegalArgumentException`
- [StockMovementRequest.java L11](../../../../../src/main/java/icc354/pucmm/proyectoqa/dto/StockMovementRequest.java) — Bean Validation `@Min(0)` en el DTO (piso genérico; IN se endurece en el service)

**6 — Bajo mínimo**

- [StockService.java L98–102](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — el ajuste a 3 (u otro valor) se acepta si es ≥ 0; no bloquea por `minStock`
- [Product.java L74–76](../../../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) — tras bajar el stock, `quantity <= minStock` marca bajo mínimo
- [Stock.tsx L66–69](../../../../../frontend/src/pages/Stock.tsx) — vuelve a calcular la lista `lowStock` al recargar productos
- [Stock.tsx L196–215](../../../../../frontend/src/pages/Stock.tsx) — el banner refleja el producto ya por debajo del mínimo

**7 — Notas**

- [Stock.tsx L281–290](../../../../../frontend/src/pages/Stock.tsx) — campo notas opcional con `maxLength={500}` en el input
- [Stock.tsx L155](../../../../../frontend/src/pages/Stock.tsx) — si está vacío tras `trim`, envía `undefined` (sin notas)
- [StockMovementRequest.java L12](../../../../../src/main/java/icc354/pucmm/proyectoqa/dto/StockMovementRequest.java) — API valida `@Size(max = 500)` en `notes`
- [StockService.java L74](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — persiste las notas en el `StockMovement`

**8 — Filtros historial**

- [Stock.tsx L102–107](../../../../../frontend/src/pages/Stock.tsx) — al cargar, manda `productId` y `movementType` del filtro a la API
- [Stock.tsx L338–374](../../../../../frontend/src/pages/Stock.tsx) — selects de filtro Producto / Tipo en la UI del historial
- [StockController.java L56–62](../../../../../src/main/java/icc354/pucmm/proyectoqa/controller/StockController.java) — endpoint GET acepta query params `productId` y `movementType`
- [StockService.java L31–43](../../../../../src/main/java/icc354/pucmm/proyectoqa/service/StockService.java) — `findAll` aplica esos filtros vía `withFilters` y pagina el resultado`




### Reglas de negocio (referencia del backend)

- `IN` / `OUT`: cantidad ≥ 1  
- `OUT`: no puede pedir más que el stock disponible  
- `ADJUSTMENT`: cantidad ≥ 0; fija el stock absoluto  
- Low stock: `quantity <= minStock` (productos activos)



## Evidencias (archivos en `evidence/`)

- [x] `ex01-low-stock-alert.png`
- [x] `ex01-out-insufficient.png`
- [x] `ex01-out-ok.png`
- [x] `ex01-adjust-zero.png`
- [x] `ex01-in-invalid.png`
- [x] `ex01-below-min.png`
- [x] `ex01-notes.png`
- [x] `ex01-history-filter.png`