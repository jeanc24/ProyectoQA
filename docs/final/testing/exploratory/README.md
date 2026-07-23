# Exploratory testing (TEST-07 / Issue #80)

Sesiones de **testing exploratorio** (Session-Based / charter-driven) sobre el inventario local.

| Charter | Archivo | Foco |
|---------|---------|------|
| EX-01 | [EX-01-stock-edge-cases.md](./EX-01-stock-edge-cases.md) | Stock edge cases |
| EX-02 | [EX-02-permissions.md](./EX-02-permissions.md) | Permisos por rol |
| EX-03 | [EX-03-ux.md](./EX-03-ux.md) | UX / usabilidad |
| Hallazgos | [findings.md](./findings.md) | Bugs o “ninguno” |
| Evidencias | [evidence/](./evidence/) | Screenshots PNG |

**Tester:** Emilio  
**Entorno:** Docker Compose — frontend http://localhost:3000 · API http://localhost:8080 · Keycloak http://localhost:8081  
**Commit esperado:** `docs(test): add exploratory testing charters and findings`

## Cómo ejecutar una sesión

1. Levantar stack: `docker compose up --build -d`
2. Abrir http://localhost:3000/login
3. Abrir el charter correspondiente y seguir la **misión + pasos**
4. Ir anotando en **Notas de sesión** lo que viste (no solo lo esperado)
5. Guardar screenshots en `evidence/` con los nombres indicados en cada charter
6. Actualizar duración real, fecha y [findings.md](./findings.md)

## Cómo sacar screenshots (Windows)

### Opción A — Recorte de pantalla (recomendada)

1. `Win + Shift + S` → seleccionar el área de la app (ventana del navegador).
2. Pegar en Paint (`Ctrl + V`) o en el Visor de fotos.
3. Guardar como PNG en:
   `docs/final/testing/exploratory/evidence/`
4. Usar **exactamente** el nombre del archivo listado en el charter (ej. `ex01-out-insufficient.png`).

### Opción B — Ventana completa

1. `Alt + PrtSc` (copia la ventana activa).
2. Pegar en Paint → Guardar como PNG en `evidence/`.

### Opción C — DevTools (útil para móvil / UX)

1. `F12` → icono de dispositivo (Ctrl+Shift+M).
2. Elegir iPhone / 390×844.
3. `Win + Shift + S` sobre esa vista.

### Qué debe verse en cada captura

- URL visible en la barra de direcciones (si cabe) o título de página claro.
- Usuario / sesión identificable cuando importe (ej. “Sesión: viewer”).
- El mensaje de error o éxito **completo** (alertas verdes/rojas).
- No ocultar con el cursor el dato crítico.

### Checklist mínimo de PNGs

Ver [evidence/README.md](./evidence/README.md).
