# Evidencia post-deploy smoke (ENV-02)

- **API:** `http://localhost:8088`
- **Keycloak:** `http://localhost:8181`
- **Fecha:** 2026-07-25T10:05:55Z

## Resultados

| Check | Resultado |
|-------|-----------|
| Health → 200 (200) | PASS |
| Sin JWT GET /products → 401 (401) | PASS |
| viewer GET /products → 200 (200) | PASS |
| viewer POST /products → 403 (403) | PASS |
| admin GET /products → 200 (200) | PASS |
| viewer reports → 403 (403) | PASS |
| admin reports → 200 (200) | PASS |
| CORS Allow-Origin=`http://localhost:3008` (esperado http://localhost:3008) | PASS |

## Regenerar

```bash
API_URL=http://localhost:8088 KEYCLOAK_URL=http://localhost:8181 ./scripts/post-deploy-smoke.sh
```
