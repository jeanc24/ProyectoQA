# Evidencia JWT / CORS / permisos (TEST-03)

- **API:** `http://localhost:8080`
- **Keycloak:** `http://localhost:8081`
- **Fecha:** 2026-08-04T14:40:49Z

## Resultados

| Check | Resultado |
|-------|-----------|
| Health público → 200 (esperado 200) | PASS |
| Sin JWT GET /products → 401 (esperado 401) | PASS |
| viewer GET /products → 200 (esperado 200) | PASS |
| viewer POST /products → 403 (esperado 403) | PASS |
| admin GET /reports/inventory-summary → 200 (esperado 200) | PASS |
| viewer GET /reports → 403 (esperado 403) | PASS |
| admin GET /audit/products/1 → 200 (200 o 404 si no existe) | PASS |
| viewer GET /audit/products/1 → 403 (esperado 403) | PASS |
| auditor GET /audit/products/1 → 200 (200 o 404 si no existe) | PASS |
| auditor GET /reports → 403 (esperado 403) | PASS |
| CORS preflight Origin localhost:3000 → HTTP 200 | PASS |
| Access-Control-Allow-Origin = `http://localhost:3000` (esperado http://localhost:3000) | PASS |
| Origen evil.example sin Allow-Origin (valor=`vacío`) | PASS |

## Cómo regenerar

```bash
docker compose up -d --build postgres keycloak tempo loki alloy api
./scripts/security-smoke.sh
```
