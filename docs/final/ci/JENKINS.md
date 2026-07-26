# CICD-02 — Jenkins: pipeline visual completo

Pipeline declarativo: [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile)  
Imagen del agente: [`infra/jenkins/Dockerfile`](../../../infra/jenkins/Dockerfile) (JDK 21 + Docker CLI + Compose + Node 22)

Paridad funcional con **[DevSecOps Pipeline (GHA)](./PIPELINE.md)** / [`.github/workflows/devsecops.yml`](../../../.github/workflows/devsecops.yml).

## Stages (orden visual)

| # | Stage Jenkins | Comando / acción | Equivalente GHA |
| - | ------------- | ---------------- | --------------- |
| 1 | Checkout | `checkout scm` | `actions/checkout@v4` |
| 2 | Build | `./gradlew build -x test` | Build (sin tests) |
| 3 | Unit | `./gradlew test` | Unit tests |
| 4 | Integration | `./gradlew integrationTest` (Testcontainers) | Integration tests |
| 5 | API | `./gradlew apiTest contractTest` | API + Contract |
| 6 | Security | Dependency-Check + compose API + smoke JWT/CORS + ZAP | Jobs `dependency-check` + `zap-baseline` |
| 7 | Sonar | JaCoCo + `sonar` (si hay credencial `SONAR_TOKEN`) | SonarCloud en `build-and-test` |
| 8 | Docker Build | `docker build` API + frontend (tags `:ci`, sin push) | Job `docker-images` |
| 9 | Deploy Staging | `docker-compose.staging.yml` + wait + post-deploy smoke | Parte de `staging-deploy-e2e` |
| 10 | E2E | Playwright (`login` + `permissions`, chromium) | Parte de `staging-deploy-e2e` |

GHA corre Security / Docker / Staging en paralelo tras tests; Jenkins los ejecuta **en serie** para una sola barra visual de stages (requisito CICD-02).

## Prerrequisitos

- Docker Desktop (o daemon) en ejecución.
- Puerto **8082** libre (UI Jenkins).
- Puertos de staging libres durante el job: **8088**, **8181**, **3008** (y los de observabilidad del compose staging).
- **Security** usa puertos **8090** (API) / **8091** (Keycloak) / **5435** (Postgres) y **no** fija `container_name`, así puede coexistir con el stack de desarrollo (`8080`/`8081`/`5433`) y con Jenkins (`8082`).

## Levantar Jenkins

```bash
docker compose build jenkins
docker compose up -d jenkins
```

UI: [http://localhost:8082](http://localhost:8082)

Contraseña inicial (primer arranque):

```bash
docker exec inventory-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### Plugins recomendados

- **Pipeline**
- **Git**
- **Credentials Binding** (Sonar opcional)
- **JUnit**

### Credencial opcional Sonar

**Manage Jenkins → Credentials → Add** → Secret text, ID: `SONAR_TOKEN`  
Sin ella, el stage **Sonar** publica JaCoCo y omite SonarCloud (mismo criterio que GHA sin secret).

### Credencial opcional NVD (Dependency-Check)

Secret text, ID: `NVD_API_KEY`. Sin DB NVD en el agente, Jenkins usa `DEPENDENCY_CHECK_SOFT_FAIL=true` (avisa y sigue con smoke/ZAP). Con API key + `DEPENDENCY_CHECK_AUTO_UPDATE=true` se acerca a la paridad GHA.

## Crear / actualizar el job

1. **New Item** → `proyectoqa` → **Pipeline**.
2. **Pipeline script from SCM** → Git → URL del repo → branch `develop` (o la del PR).
3. **Script Path:** `infra/jenkins/Jenkinsfile`
4. **Save** → **Build Now**.

El contenedor monta `/var/run/docker.sock`, `extra_hosts: host.docker.internal` y el repo en `/host-repo` (para bind mounts de staging).

**Importante:** levantá Jenkins siempre desde la raíz del repo (`docker compose up -d --build jenkins`) para que `.:/host-repo` apunte al código correcto.

### Security sin bind mounts

El stage Security usa [`docker-compose.security.yml`](../../../docker-compose.security.yml) (postgres + keycloak con realm embebido + api). Así se evita el error `not a directory` al montar `./infra/...` desde el workspace de Jenkins, y el `Conflict` de nombres/puertos con `inventory-api` / `inventory-keycloak` del compose de desarrollo.

## Evidencia (issue #85)

Tras un build **exitoso**:

1. Abrí el build → **Pipeline Overview** (o Status con los 10 stages en verde).
2. Capturá la pantalla y guardala como:

   [`jenkins-pipeline-green.png`](./jenkins-pipeline-green.png)

3. Incluí la imagen en el PR que cierra #85.

Checklist rápido:

- [ ] `docker compose build jenkins && docker compose up -d jenkins`
- [ ] Job Pipeline → Script Path `infra/jenkins/Jenkinsfile` → **Build Now**
- [ ] Stages: Checkout → Build → Unit → Integration → API → Security → Sonar → Docker Build → Deploy Staging → E2E
- [ ] Screenshot en `docs/final/ci/jenkins-pipeline-green.png`


## Reproducir sin Jenkins (mismos comandos)

```bash
chmod +x gradlew scripts/*.sh
./gradlew build -x test && ./gradlew test && ./gradlew integrationTest
./gradlew apiTest contractTest
./scripts/dependency-check.sh
# … Security / staging / E2E: ver docs/final/ci/PIPELINE.md y post-deploy-tests.md
```

## Diferencias menores vs GHA

| Tema | Jenkins | GHA |
| ---- | ------- | --- |
| Paralelismo | Stages secuenciales | Jobs 8–11 en paralelo + quality gate |
| Quality gate job | El propio pipeline falla al primer stage rojo | Job `quality-gate` agrega check único |
| Sonar | Credencial Jenkins `SONAR_TOKEN` | Secret `SONAR_TOKEN` |
| Dependency-Check NVD | `DEPENDENCY_CHECK_AUTO_UPDATE=false` por defecto (rápido) | CI puede usar `true` + cache |
