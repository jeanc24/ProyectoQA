# Guía de contribución — ProyectoQA

## Flujo de trabajo

1. Crear issue en GitHub (usar plantilla **Tarea del sprint**).
2. Crear rama desde `develop`:
   ```bash
   git checkout develop
   git pull
   git checkout -b feature/nombre-corto
   ```
3. Implementar con commits **Conventional Commits** (ver abajo).
4. Abrir Pull Request hacia `develop`.
5. El compañero revisa; ningún merge sin al menos 1 approval.
6. Tras merge, cerrar el issue con `Closes #N` en el PR.

### Ramas

| Rama | Uso |
|------|-----|
| `main` | Producción / entregas. Protegida: solo vía PR + review. |
| `develop` | Integración del sprint. |
| `feature/*` | Una tarea = una rama. |
| `fix/*` | Corrección de bugs. |
| `test/*` | Solo tests, sin lógica de negocio nueva. |

---

## Conventional Commits

Formato obligatorio:

```
<tipo>(<alcance opcional>): <descripción en imperativo, minúsculas, sin punto final>
```

### Tipos permitidos

| Tipo | Cuándo usarlo | Ejemplo |
|------|---------------|---------|
| `feat` | Funcionalidad nueva | `feat(api): add product CRUD endpoints` |
| `fix` | Corrección de bug | `fix(security): allow CORS from frontend origin` |
| `test` | Tests (unit, integration, e2e) | `test(service): add SKU duplicate validation cases` |
| `docs` | Solo documentación | `docs: add docker compose setup to README` |
| `chore` | Mantenimiento, deps, config | `chore: init react frontend with vite` |
| `ci` | Pipelines (GHA, Jenkins) | `ci: add unit and integration test jobs` |
| `refactor` | Cambio interno sin cambiar comportamiento | `refactor(service): extract product mapper` |
| `perf` | Mejora de rendimiento | `perf(api): add index on products.sku` |
| `style` | Formato (sin lógica) | `style: apply spotless formatting` |

### Alcance sugerido

`api`, `security`, `frontend`, `infra`, `db`, `audit`, `obs`, `e2e`

### Reglas

- Descripción en **inglés** (consistente con el historial del repo) o español — el equipo elige uno y no mezclar.
- Máximo **72 caracteres** en la primera línea.
- Cuerpo opcional separado por línea en blanco.
- Referenciar issue: `Closes #12` o `Refs #12`.

### Ejemplos buenos

```
feat(infra): add postgres and keycloak services
feat(api): add product CRUD with validations
feat(security): enforce JWT permissions on product endpoints
test: add integration tests with testcontainers
ci: add build unit and integration pipeline
docs: expand README with service ports table
chore: add conventional commit hook and issue templates
```

### Ejemplos malos (rechazados por el hook)

```
Added product controller          # sin tipo
feat: Added stuff.                # no imperativo / con punto
FEAT(api): big change             # tipo en mayúsculas
fix bug                           # sin dos puntos
wip                               # sin formato
```

---

## Activar validación de commits (hook local)

Una vez por máquina, en la raíz del repo:

```bash
git config core.hooksPath .githooks
chmod +x .githooks/commit-msg
```

El hook valida el formato antes de completar cada commit. Para saltarlo en emergencia (no recomendado):

```bash
git commit --no-verify -m "mensaje"
```

---

## Issues y labels

- Cada tarea del sprint = **1 issue** con label `enhancement`, `testing`, `infra` o `security` según corresponda.
- Milestone: **Avance 16-Jun 2026**.
- Asignar a Jean o Emilio antes de empezar.

Labels del proyecto:

| Label | Color | Uso |
|-------|-------|-----|
| `enhancement` | `#0e8a16` | Features nuevas |
| `bug` | `#d73a4a` | Defectos |
| `documentation` | `#0075ca` | README, docs |
| `testing` | `#7057ff` | Unit, integration, E2E |
| `infra` | `#f9d0c4` | Docker, compose, CI |
| `security` | `#e99695` | Keycloak, OAuth2, permisos |
| `avance-1` | `#1d76db` | Sprint entrega 16 jun |

Crear labels e issues iniciales en GitHub:

```bash
./scripts/github-setup.sh
```

(Requiere [GitHub CLI](https://cli.github.com/) autenticado: `gh auth login`)
