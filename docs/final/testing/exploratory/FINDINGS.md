# Findings — Exploratory Testing (TEST-07)

**Periodo:** 2026-07-23  
**Tester:** Jean Pérez  
**Tiempo total de sesión:** ~120 min (45 + 40 + 35)  
**Charters:** EC-01, EC-02, EC-03  

## Resumen

| Métrica | Valor |
|---------|--------|
| Charters ejecutados | 3 |
| Bugs bloqueantes / altos | **0** |
| Bugs medios | **0** |
| Hallazgos UX bajos | **1** (EXP-01) |
| Fugas de seguridad (authz) | **0** |

## Hallazgos

### EXP-01 — Tablas en móvil (UX, baja)

- **Charter:** EC-03  
- **Área:** `/products` (y listados similares)  
- **Descripción:** En anchos ~375px las tablas requieren scroll horizontal. No impide operar, pero la densidad es alta.  
- **Reproducir:** Login admin → Products → DevTools responsive 375px.  
- **Esperado (mejora):** Layout tipo cards o columnas prioritarias.  
- **Estado:** Aceptado como deuda UX; no bloquea entrega.

## Bugs críticos

Ninguno. Los edge cases de stock (salida > disponible, cantidades inválidas) y la matriz de permisos se comportaron según diseño.

## Evidencia relacionada

| Artefacto | Ubicación |
|-----------|-----------|
| Charters | `docs/final/testing/exploratory/EC-0*.md` |
| Smoke JWT/CORS (automatizado) | `docs/final/testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md` |
| E2E permisos / responsive | `docs/final/testing/e2e/evidencias/` |

## Conclusión

Exploración dirigida de **stock**, **permisos** y **UX** sin defectos funcionales nuevos. Sistema listo para demo con la matriz de roles documentada; único seguimiento opcional: mejorar responsive de tablas (EXP-01).
