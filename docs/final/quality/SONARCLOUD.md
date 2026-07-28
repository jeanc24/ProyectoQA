# SonarCloud — calidad de código (SONAR-01)

## 1. Vincular el repo

1. Entra a [https://sonarcloud.io](https://sonarcloud.io) con GitHub (`jeanc24`).
2. **Analyze new project** → elige `jeanc24/ProyectoQA`.
3. Confirma:
   - **Organization:** `jeanc24` (o la que cree SonarCloud; si difiere, exporta `SONAR_ORGANIZATION`)
   - **Project key:** `jeanc24_ProyectoQA` (debe coincidir con `sonar-project.properties` / `build.gradle`)

## 2. Token CI

1. SonarCloud → **My Account → Security → Generate Tokens**
2. En GitHub: **Settings → Secrets and variables → Actions**
3. Secret: `SONAR_TOKEN` = el token generado

Opcional: `SONAR_ORGANIZATION` si tu org en SonarCloud no es `jeanc24`.

## 3. Quality Gate (bugs, vulnerabilities, smells, coverage)

En SonarCloud → proyecto → **Quality Gates**:

Usa **Sonar way** (recomendado) o crea uno con al menos:

| Condición (new code / overall) | Umbral sugerido |
|--------------------------------|-----------------|
| Coverage | ≥ 60% (alineado a JaCoCo del curso) |
| Bugs | 0 nuevos |
| Vulnerabilities | 0 nuevas |
| Code Smells / Maintainability | Rating A (o 0 blockers) |
| Security Hotspots Reviewed | 100% |

El workflow CI espera el gate con `sonar.qualitygate.wait=true`: si no pasa, el job **falla**.

## 4. Correr análisis

```bash
# Local (requiere SONAR_TOKEN)
export SONAR_TOKEN=...
./gradlew test jacocoTestReport sonar

# CI: automático en push/PR a develop (.github/workflows/ci.yml)
```

Dashboard: [https://sonarcloud.io/project/overview?id=jeanc24_ProyectoQA](https://sonarcloud.io/project/overview?id=jeanc24_ProyectoQA)

## Troubleshooting

### `QUALITY GATE STATUS: FAILED`
El análisis **sí se subió**. Falló la condición del gate (p. ej. `new_security_rating`). Revisa Issues en el dashboard. CSRF deshabilitado en API JWT se documenta con `// NOSONAR` en `SecurityConfig` / `DockerSecurityConfig`.

Para ver el resultado sin fallar el build localmente:

```bash
./gradlew test jacocoTestReport sonar
# (sin -Dsonar.qualitygate.wait=true)
```

### Automatic Analysis vs CI
Desactiva **Automatic Analysis** en Administration → Analysis Method; deja solo el análisis Gradle/CI.
