# OWASP Dependency-Check (TEST-03)

## CI (recomendado)

Workflow **Security** → job Dependency-Check. Secret: `NVD_API_KEY`.

Artefacto: `dependency-check-report`.

## Local

```bash
# Por defecto NO descarga NVD (evita llenar el disco)
./scripts/dependency-check.sh

# Sync completa (necesita ≥10 GB libres + NVD_API_KEY en .env)
DEPENDENCY_CHECK_AUTO_UPDATE=true ./scripts/dependency-check.sh
```

Ver `EVIDENCIA.md` si el HTML no se genera en laptop.
