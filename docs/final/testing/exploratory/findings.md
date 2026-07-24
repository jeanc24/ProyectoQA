# Findings — Exploratory testing (TEST-07)

Resumen de hallazgos de las sesiones EX-01, EX-02 y EX-03.

| Campo | Valor |
|-------|--------|
| **Tester** | Emilio |
| **Periodo de sesiones** | _fecha(s) reales_ |
| **Tiempo total** | _suma de duraciones de EX-01 + EX-02 + EX-03_ |
| **Entorno** | Docker Compose local (frontend :3000, API :8080, Keycloak :8081) |
| **Charters** | [EX-01](./EX-01-stock-edge-cases.md), [EX-02](./EX-02-permissions.md), [EX-03](./EX-03-ux.md) |
| **Evidencias** | [evidence/](./evidence/) |

## Estado de las sesiones

| Charter | Fecha | Duración | Estado |
|---------|-------|----------|--------|
| EX-01 Stock edge cases | _pendiente_ | _min_ | Script listo — ejecutar y adjuntar PNG |
| EX-02 Permisos | _pendiente_ | _min_ | Script listo — ejecutar y adjuntar PNG |
| EX-03 UX | _pendiente_ | _min_ | Script listo — ejecutar y adjuntar PNG |

## Bugs encontrados

| ID | Charter | Severidad | Título | Descripción | Evidencia | Estado |
|----|---------|-----------|--------|-------------|-----------|--------|
| — | — | — | — | **Ninguno** (actualizar tras las sesiones; si aparece un bug, reemplaza esta fila) | Ver PNGs en `evidence/` como prueba de exploración | — |

### Cómo documentar un bug (ejemplo)

| ID | Charter | Severidad | Título | Descripción | Evidencia | Estado |
|----|---------|-----------|--------|-------------|-----------|--------|
| BUG-EX01-01 | EX-01 | Media | OUT insuficiente sin mensaje | Al pedir OUT > stock la UI no muestra error | `ex01-out-insufficient.png` | Abierto |

Severidades sugeridas: **Bloqueante** · **Alta** · **Media** · **Baja** · **Mejora UX**.

## Evidencia de “ninguno”

Si al cerrar las 3 sesiones no hay defects:

1. Deja la fila **Ninguno** en la tabla de arriba.
2. Asegura que existen al menos los PNG **mínimos** de cada charter (ver `evidence/README.md`).
3. Completa fecha, duración y notas en cada `EX-0N-*.md`.

Eso cumple el criterio del issue: *bugs documentados **o** “ninguno” con evidencia*.

## Observaciones positivas (opcional)

- 
- 

## Firmas de sesión

| Charter | Tester | Firma / inicial | Fecha |
|---------|--------|-----------------|-------|
| EX-01 | Emilio | | |
| EX-02 | Emilio | | |
| EX-03 | Emilio | | |
