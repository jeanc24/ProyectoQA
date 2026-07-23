# Charter EX-03 — UX / usabilidad

| Campo | Valor |
|-------|--------|
| **ID** | EX-03 |
| **Tester** | Emilio |
| **Fecha** | _completar al ejecutar_ |
| **Duración** | _minutos reales_ (objetivo 40–50 min) |
| **Entorno** | Docker local — http://localhost:3000 |
| **Usuario** | `admin` / `admin` (flujos completos); opcional `viewer` para contraste |

## Misión

Explorar la **experiencia de uso** de login, productos, stock y dashboard: claridad de mensajes, feedback de errores, navegación, estados vacíos/carga y legibilidad en viewport estrecho. Buscar fricción, textos confusos o flujos rotos (no medir performance).

## Alcance

**Incluye**

- `/login` → Keycloak → retorno a `/products`
- CRUD productos (validaciones de formulario, mensajes)
- Stock: preview, éxito/error, “Solo lectura” (con otro rol si hay tiempo)
- Dashboard: KPIs visibles y comprensibles
- Viewport ~390px (DevTools) en Productos o Stock
- Logout y re-login

**No incluye**

- Diseño visual “bonito” vs guideline de marca
- Accesibilidad WCAG formal (contraste medido, screen reader) — solo notas si algo es evidente
- Bugs de permisos ya cubiertos en EX-02 (salvo UX del `/unauthorized`)

## Pasos a explorar

| # | Acción | Qué observar | Observado | Evidencia |
|---|--------|--------------|-----------|-----------|
| 1 | Abrir `/login` → “Iniciar sesión” → Keycloak | ¿El flujo es claro? ¿vuelve a Productos? | | `ex03-login-flow.png` |
| 2 | Crear producto con campos vacíos / precio negativo | Mensajes de error útiles (campo o API) | | `ex03-product-validation.png` |
| 3 | Crear producto válido | Confirmación / aparece en lista | | `ex03-product-create-ok.png` |
| 4 | Stock: seleccionar producto y mirar **preview** antes de enviar | ¿Se entiende IN vs OUT vs ADJUSTMENT? | | `ex03-stock-preview.png` |
| 5 | Provocar error (OUT excesivo) y éxito (IN) | ¿Alertas distinguibles? ¿desaparecen bien? | | `ex03-stock-feedback.png` |
| 6 | Dashboard como admin | ¿KPIs claros (low stock, resumen)? | | `ex03-dashboard.png` |
| 7 | Página `/unauthorized` (como viewer → `/dashboard`) | Texto + acciones “Volver” / “Cerrar sesión” | | `ex03-unauthorized-ux.png` |
| 8 | DevTools móvil 390×844 en Productos | ¿Tabla usable? ¿nav usable? | | `ex03-mobile-products.png` |
| 9 | Cerrar sesión → intentar URL `/products` | Redirige a login | | `ex03-logout.png` |

## Heurísticas rápidas (anotar si fallan)

- [ ] Un solo propósito claro por pantalla
- [ ] Errores en lenguaje de usuario (no solo código HTTP crudo sin contexto)
- [ ] Acciones destructivas o irreversibles no se disparan por accidente
- [ ] Estado de carga / vacío no deja la UI “en blanco” sin explicación
- [ ] Usuario siempre sabe **quién** es la sesión activa

## Notas de sesión

1. 
2. 
3. 

## Bugs / hallazgos de esta sesión

| ID | Severidad | Descripción | Evidencia |
|----|-----------|-------------|-----------|
| — | — | _Ninguno_ **o** BUG-EX03-01 … | |

## Evidencias (mínimo)

- [ ] `ex03-login-flow.png`
- [ ] `ex03-product-validation.png`
- [ ] `ex03-stock-feedback.png`
- [ ] `ex03-dashboard.png`
- [ ] `ex03-unauthorized-ux.png`
- [ ] `ex03-mobile-products.png`
