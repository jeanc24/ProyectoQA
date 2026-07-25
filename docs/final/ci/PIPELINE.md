# CICD-01 — Pipeline DevSecOps (GitHub Actions)

Workflow principal: [`.github/workflows/devsecops.yml`](../../../.github/workflows/devsecops.yml)  
Nombre en Actions: **DevSecOps Pipeline**

Paridad visual en Jenkins (CICD-02): [`JENKINS.md`](./JENKINS.md) / [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile).

Se dispara en **push** / **PR** a `develop` y con `workflow_dispatch`.

## Stages

| Orden | Job / step | Qué hace | Falla el gate si… |
| ----- | ---------- | -------- | ----------------- |
| 1 | checkout | Código + depth 0 (Sonar blame) | — |
| 2 | Build | `./gradlew build -x test` | Build roto |
| 3 | Unit | `./gradlew test` (excluye tags `integration`, `api`, `contract`) | Test rojo |
| 4 | Integration | `./gradlew integrationTest` (Testcontainers) | Test rojo |
| 5 | API | `./gradlew apiTest` (`@Tag("api")` — `*ApiScenarioTest`) | Test rojo |
| 6 | Contract | `./gradlew contractTest` (`@Tag("contract")` — OpenAPI surface) | Test rojo |
| 7 | JaCoCo + Sonar | Artefacto `jacoco-report` + quality gate SonarCloud | Sonar QG fail |
| 8 | Docker images | Build local API + frontend (sin push) | Dockerfile roto |
| 9 | Dependency-Check | OWASP DC → artefacto `dependency-check-report` | Script/DC fail |
| 10 | ZAP baseline | Stack compose + smoke JWT/CORS + ZAP | Smoke/ZAP fail |
| 11 | Staging E2E | `docker-compose.staging.yml` + smoke + Playwright | Smoke/E2E fail |
| 12 | **Quality gate** | Job final: exige `success` en todos los anteriores | Cualquier stage ≠ success |

Jobs 8–11 corren en paralelo tras `build-and-test`. El job **Quality gate** es el check único que debe estar en verde para merge.

## Workflows legacy (manual)

Conservados con `workflow_dispatch` para re-ejecutar un trozo aislado:

- `.github/workflows/ci.yml` — build + tests + Sonar
- `.github/workflows/security.yml` — DC + ZAP
- `.github/workflows/post-deploy-staging.yml` — staging smoke/E2E

## Secretos / variables

| Nombre | Uso |
| ------ | --- |
| `SONAR_TOKEN` | Análisis SonarCloud + QG |
| `SONAR_ORGANIZATION` (variable) | Default `jeanc24` |
| `NVD_API_KEY` | Acelera Dependency-Check (opcional) |

## Reproducir local

```bash
./gradlew build -x test
./gradlew test
./gradlew integrationTest
./gradlew apiTest
./gradlew contractTest
./gradlew jacocoTestReport

# Imágenes
docker build -t proyectoqa-api:local .
docker build -t proyectoqa-frontend:local ./frontend

# Security / staging — ver README y docs/final/ci/post-deploy-tests.md
```
