# k6 performance tests (TEST-04)

## Umbrales

| Escenario | Script | VUs (pico) | p95 latencia | Error rate |
|-----------|--------|------------|--------------|------------|
| **Load** | `tests/k6/load-products.js` | 15 | **&lt; 500 ms** | **&lt; 1%** |
| **Stress** | `tests/k6/stress-products.js` | 80 | **&lt; 2000 ms** | **&lt; 5%** |

Target: `GET /api/v1/products` con JWT (`viewer`).

## Ejecutar (staging local)

```bash
docker compose up -d --build postgres keycloak tempo loki alloy api
# health: curl -sf http://localhost:8080/actuator/health

./scripts/k6-run.sh load
./scripts/k6-run.sh stress
# o ambos:
./scripts/k6-run.sh all
```

Requiere Docker (`grafana/k6`). No hace falta instalar k6 en el host.

## Reportes

Tras cada run se generan en este directorio:

- `load-products-summary.json` / `.txt`
- `stress-products-summary.json` / `.txt`
