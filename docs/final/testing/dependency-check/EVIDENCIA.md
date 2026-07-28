# Evidencia Dependency-Check (TEST-03)

## Problema local

La sync NVD de OWASP Dependency-Check necesita **~2 GB de DB + margen** (≥8–10 GB libres). En esta laptop el disco libre suele caer a **&lt;3 GB**, provocando:

- `No space left on device`
- DB H2 corrupta / incompleta
- Análisis de 40–90+ min que no termina

## Estrategia del ticket

| Entorno | Qué hacer |
|---------|-----------|
| **Local** | ZAP + `security-smoke.sh` (evidencia JWT/CORS/permisos) |
| **CI** | Workflow [`security.yml`](../../../.github/workflows/security.yml) job **OWASP Dependency-Check** con cache NVD + secreto `NVD_API_KEY` |

```bash
# Local — solo si hay ≥10 GB libres y NVD_API_KEY en .env
DEPENDENCY_CHECK_AUTO_UPDATE=true ./scripts/dependency-check.sh

# Por defecto el script NO descarga NVD (evita llenar el disco)
./scripts/dependency-check.sh
```

## Cerrar el ticket sin HTML local

1. Smoke + ZAP OK → reportes en `../zap/`
2. Plugin Gradle + `scripts/dependency-check.sh` + `security.yml` en el PR
3. Tras merge a `develop`, Actions genera el artefacto `dependency-check-report`
4. Secret GitHub: `NVD_API_KEY` (misma key que en `.env`)
