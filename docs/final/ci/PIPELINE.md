# Pipelines CI/CD — tipos y qué hace cada uno

Respuesta corta para defensa: **tenemos varios workflows**; el principal de calidad es **DevSecOps**, los de **deploy cloud** son staging/prod, hay uno de **conventional commits**, un **espejo en Jenkins**, y tres workflows **legacy manuales**.

Archivo principal de Actions: [`.github/workflows/`](../../../.github/workflows/).
Cloud: [`CLOUD.md`](CLOUD.md) · Jenkins: [`JENKINS.md`](JENKINS.md).

---

## Tipos de pipeline (visión general)

| Tipo | Workflow / artefacto | Cuándo corre | Qué hace (en una frase) |
| ---- | -------------------- | ------------ | ------------------------ |
| **1. Calidad / DevSecOps** | `devsecops.yml` | Push/PR a `develop` (+ manual) | Build, todos los tests, Sonar, Docker, SCA, ZAP, staging Compose + E2E, quality gate |
| **2. Deploy staging (cloud)** | `deploy-staging.yml` | Push a `develop` (+ manual) | Dispara/redeploy **Render** + deploy **Vercel** staging + smoke opcional |
| **3. Deploy production (cloud)** | `deploy-prod.yml` | Push a `main` (+ manual) | Igual que staging pero ambiente **prod** (Render Blueprint prod + Vercel `main`) |
| **4. Conventional Commits** | `conventional-commits.yml` | PR a `develop` / `main` | Valida que el asunto del commit cumpla `tipo(scope): mensaje` |
| **5. Jenkins (paridad)** | `infra/jenkins/Jenkinsfile` | Manual en Jenkins local | Misma idea de stages que DevSecOps, en el agent Docker del Compose |
| **6. Legacy CI** | `ci.yml` | Solo `workflow_dispatch` | Build + tests + Sonar (trozo aislado; ya cubierto por DevSecOps) |
| **7. Legacy Security** | `security.yml` | Solo manual | Dependency-Check + ZAP aislados |
| **8. Legacy Post-deploy staging** | `post-deploy-staging.yml` | Solo manual | Compose staging + smoke + Playwright (aislado) |

### Frase para el profesor

> “El pipeline de **calidad** es DevSecOps en cada PR a `develop`. El de **despliegue** es otro: push a `develop`/`main` despliega cloud (Render + Vercel). Además validamos mensajes de commit en el PR, y Jenkins replica el DevSecOps en local. Los workflows `ci` / `security` / `post-deploy-staging` son respaldos manuales.”

---

## 1. DevSecOps Pipeline (`devsecops.yml`)

Nombre en GitHub Actions: **DevSecOps Pipeline**.

**Disparador:** push y pull request a `develop`, o `workflow_dispatch`.

**Jobs:**

```
build-and-test ──► docker-images
               ├──► dependency-check
               ├──► zap-baseline
               └──► staging-deploy-e2e
                         │
                         ▼
                   quality-gate
```

| Orden | Job / stage | Qué hace | Falla si… |
| ----- | ----------- | -------- | --------- |
| 1–6 | `build-and-test` | `build -x test`, unit, integration, api, contract | Build o test rojo |
| 7 | (mismo job) | JaCoCo + SonarCloud (`qualitygate.wait`) | Sonar QG fail / sin token según config |
| 8 | `docker-images` | Build local de imágenes API + frontend (sin push) | Dockerfile roto |
| 9 | `dependency-check` | SCA OWASP Dependency-Check | Script/DC fail |
| 10 | `zap-baseline` | Stack + smoke JWT/CORS + ZAP baseline | Smoke/ZAP fail |
| 11 | `staging-deploy-e2e` | `docker-compose.staging.yml` + smoke + Playwright | Smoke/E2E fail |
| 12 | **quality-gate** | Exige `success` en todos los anteriores | Cualquier stage ≠ success |

Jobs 8–11 corren **en paralelo** tras `build-and-test`. El **Quality gate** es el check que debe estar verde para merge.

**Mostrar:** GitHub → Actions → *DevSecOps Pipeline*.

---

## 2. Deploy staging cloud (`deploy-staging.yml`)

**Disparador:** push a `develop` (y manual).

| Job | Qué hace |
| --- | -------- |
| `trigger-render` | POST a Deploy Hooks de API/Keycloak staging (si hay secrets); si no, confía en auto-deploy del Blueprint `render.yaml` |
| `deploy-vercel` | `vercel pull --environment=preview` + `build` + `deploy` del proyecto staging con `VITE_*` desde variables `STAGING_*` |
| `smoke-cloud` | Espera stack + `post-deploy-smoke.sh` si hay `STAGING_API_URL` / `STAGING_KEYCLOAK_URL` |

**No** corre unit/integration/Sonar: eso ya lo hizo DevSecOps. Este pipeline **publica** el ambiente persistente.

---

## 3. Deploy production cloud (`deploy-prod.yml`)

Igual que staging, pero:

- Branch **`main`**
- Secrets/vars `PROD_*` y `VERCEL_PROJECT_ID_PROD`
- Blueprint [`infra/render/render.prod.yaml`](../../../infra/render/render.prod.yaml)
- Environment GitHub `production`

---

## 4. Conventional Commits (`conventional-commits.yml`)

**Disparador:** pull request hacia `develop` o `main`.

Recorre los commits del PR y exige asunto tipo:

`feat|fix|test|docs|chore|ci|refactor|perf|style(scope opcional): mensaje (3–72 chars)`

Si un commit no cumple → el check del PR falla (no mergea “sucio”).

Hook local: [`.githooks/commit-msg`](../../../.githooks/commit-msg).

---

## 5. Jenkins (`Jenkinsfile`)

Misma secuencia lógica (Build → Unit → Integration → API → Security → Sonar → Docker → Staging → E2E), pensada para demostración **local** con el servicio `jenkins` del Compose.

No sustituye a GitHub Actions en el día a día del equipo; es **paridad** CICD-02.

Guía: [`JENKINS.md`](JENKINS.md).

---

## 6–8. Workflows legacy (solo manual)

| Workflow | Para qué conservarlo |
| -------- | -------------------- |
| `ci.yml` | Re-ejecutar solo build/tests/Sonar sin ZAP ni staging |
| `security.yml` | Solo SCA + ZAP |
| `post-deploy-staging.yml` | Solo compose staging + smoke + E2E |

Todo eso ya está **dentro** de `devsecops.yml`; estos quedan por si hace falta un trozo aislado.

---

## Secretos / variables relevantes

| Nombre | Pipeline | Uso |
| ------ | -------- | --- |
| `SONAR_TOKEN` | DevSecOps / CI | SonarCloud + QG |
| `SONAR_ORGANIZATION` | DevSecOps | Org Sonar (default `jeanc24`) |
| `NVD_API_KEY` | DevSecOps / Security | Acelera Dependency-Check |
| `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID*` | Deploy staging/prod | Deploy frontend |
| `RENDER_DEPLOY_HOOK_*` | Deploy staging/prod | Redeploy API/KC (opcional) |
| Vars `STAGING_*` / `PROD_*` | Deploy + smoke | URLs API, Keycloak, frontend |

---

## Reproducir local (calidad, sin Actions)

```bash
./gradlew build -x test
./gradlew test
./gradlew integrationTest
./gradlew apiTest
./gradlew contractTest
./gradlew jacocoTestReport

docker build -t proyectoqa-api:local .
docker build -t proyectoqa-frontend:local ./frontend
```

Staging Compose + smoke/E2E: ver [`post-deploy-tests.md`](post-deploy-tests.md) y [`../defensa/GUIA-DEMO.md`](../defensa/GUIA-DEMO.md).
