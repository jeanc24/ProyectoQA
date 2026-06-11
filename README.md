# ProyectoQA

Sistema de Gestión de Inventarios Empresarial — PUCMM, Aseguramiento de Calidad de Software.

Stack: Spring Boot (Gradle) · React · PostgreSQL · Keycloak · Docker

## Contribuir

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Conventional Commits, ramas, flujo de PR
- **[PLAN-PASO-A-PASO.txt](PLAN-PASO-A-PASO.txt)** — Sprint avance 16 jun

### Setup local (una vez)

```bash
# Validar formato de commits automáticamente
git config core.hooksPath .githooks
chmod +x .githooks/commit-msg

# Labels, milestone e issues del sprint en GitHub (requiere gh CLI)
./scripts/github-setup.sh
```

### Conventional Commits

```
feat(api): add product CRUD with validations
fix(security): allow CORS from frontend origin
test: add integration tests with testcontainers
ci: add build unit and integration pipeline
```

Tipos: `feat` · `fix` · `test` · `docs` · `chore` · `ci` · `refactor` · `perf` · `style`

### Ramas

`main` (protegida) ← `develop` ← `feature/*`

### Issues

Usar plantillas en GitHub: **Tarea del sprint** · **Reporte de bug** · **Feature / mejora**
