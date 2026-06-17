# ProyectoQA

Sistema de Gestión de Inventarios Empresarial — PUCMM, Aseguramiento de Calidad de Software.

Stack: Spring Boot (Gradle) · React · PostgreSQL · Keycloak · Docker

![CI](https://github.com/jeanc24/ProyectoQA/actions/workflows/ci.yml/badge.svg?branch=develop)
![Conventional Commits](https://github.com/jeanc24/ProyectoQA/actions/workflows/conventional-commits.yml/badge.svg?branch=develop)

## CI (GitHub Actions)

Cada **push** o **pull request** hacia la rama `develop` ejecuta el workflow [**CI**](https://github.com/jeanc24/ProyectoQA/actions/workflows/ci.yml).

Los **pull requests** hacia `develop` o `main` también ejecutan [**Conventional Commits**](https://github.com/jeanc24/ProyectoQA/actions/workflows/conventional-commits.yml) para validar los mensajes de commit.

### Ver el estado del pipeline

1. Abre la pestaña [**Actions**](https://github.com/jeanc24/ProyectoQA/actions) del repositorio.
2. Selecciona el workflow **CI**.
3. Elige la ejecución (por commit o por PR).
4. Revisa el job **build-and-test** y sus steps:
   - **Build (sin tests)** — compila el backend con Gradle y JDK 21
   - **Unit tests** — `./gradlew test`
   - **Integration tests** — `./gradlew integrationTest` (Testcontainers + PostgreSQL en Docker)

| Resultado | Significado |
|-----------|-------------|
| Verde | Build y tests pasaron |
| Rojo | Compilación o algún test falló; revisa el log del step en rojo |

También puedes ver el estado en cada **Pull Request** hacia `develop`, en la sección **Checks** al final del PR.

### Reporte de cobertura (JaCoCo)

Tras cada ejecución, el workflow publica el artefacto **`jacoco-report`**:

1. Entra en la ejecución del workflow en Actions.
2. Baja hasta **Artifacts**.
3. Descarga `jacoco-report` y abre `index.html` en el navegador.

### Reproducir localmente (mismos comandos que CI)

```powershell
# Windows
.\gradlew.bat build -x test
.\gradlew.bat test
.\gradlew.bat integrationTest
```

```bash
# Linux / macOS / runner de GitHub
./gradlew build -x test
./gradlew test
./gradlew integrationTest
```

> **Nota:** los integration tests requieren Docker en ejecución (Testcontainers).

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
