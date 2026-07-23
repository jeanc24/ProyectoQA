# Charter EC-03 — UX productos y dashboard

| Campo | Valor |
|-------|--------|
| **ID** | EC-03 |
| **Título** | UX: listado de productos, filtros y dashboard |
| **Tester** | Jean Pérez |
| **Fecha** | 2026-07-23 |
| **Duración planificada** | 35 min |
| **Duración real** | 35 min |
| **Ambiente** | Frontend Docker http://localhost:3000 |
| **Misión** | Explorar usabilidad del flujo diario: buscar/filtrar productos, feedback de errores, claridad del dashboard |

## Charter (guía)

Explorar como **admin**:

- Búsqueda / filtros / paginación / ordenamiento en `/products`
- Feedback al crear producto con SKU duplicado o campos inválidos
- Dashboard: resumen, low stock, top products (si hay datos)
- Responsive rápido (viewport estrecho)
- Navegación y logout

## Notas de sesión

### 0–15 min — `/products`

1. Búsqueda por nombre/SKU reduce resultados de forma usable.
2. Paginación cambia de página sin perder el layout.
3. Indicación de bajo stock (si `quantity <= minStock`) es visible cuando aplica.
4. Formulario “Nuevo producto”: validación de campos requeridos en cliente/servidor.

### 15–25 min — errores y feedback

5. SKU duplicado: API responde conflicto/error; UI muestra mensaje (no pantalla en blanco).
6. Cancelar diálogo de creación no deja estado sucio evidente.

### 25–35 min — dashboard y UX general

7. `/dashboard` (admin): muestra resumen de inventario cuando hay datos; vacío razonable si no hay productos.
8. Navegación Products / Stock / Dashboard coherente con permisos.
9. Logout y re-login con otro usuario funciona (cambio de rol visible).
10. Viewport móvil (~375px): tabla/listado usable o con scroll horizontal; sin solapamiento crítico de CTA principal.

## Bugs encontrados

| ID | Severidad | Descripción | Estado |
|----|-----------|-------------|--------|
| EXP-01 | Baja (UX) | En viewport muy estrecho, tablas densas requieren scroll horizontal; esperable pero mejorable con cards | Documentado — no bloqueante |
| — | — | Sin crashes ni pérdida de datos en la sesión | — |

## Ideas de mejora (no bugs)

- Empty states más guiados en dashboard sin datos (“Crea tu primer producto”).
- Confirmación explícita al eliminar producto (si no existe ya).

## Oráculos

- F-03/F-04 UI (productos + dashboard)
- E2E Playwright productos / dashboard / responsive screenshots en `docs/final/testing/e2e/`
