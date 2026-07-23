# Exploratory Testing (TEST-07)

Pruebas exploratorias **session-based** (charters) sobre el Sistema de Inventarios.

| # | Charter | Área | Tester | Duración |
|---|---------|------|--------|----------|
| 01 | [EC-01 Stock edge cases](./EC-01-stock-edge-cases.md) | Stock API / UI | Jean Pérez | 45 min |
| 02 | [EC-02 Permisos por rol](./EC-02-permisos-roles.md) | Seguridad / UI | Jean Pérez | 40 min |
| 03 | [EC-03 UX productos y dashboard](./EC-03-ux-productos-dashboard.md) | UX frontend | Jean Pérez | 35 min |

**Resumen de hallazgos:** [FINDINGS.md](./FINDINGS.md)

## Cómo reproducir el entorno

```bash
docker compose up -d --build postgres keycloak tempo loki alloy api frontend
# Frontend: http://localhost:3000
# API:      http://localhost:8080
```

Usuarios demo: `admin`/`admin`, `viewer`/`viewer`, `stock-manager`/`stock-manager`.
