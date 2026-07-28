# CI — Jenkins (avance 1)

> **CICD-02 (entrega final):** pipeline visual completo y paridad con GHA →  
> **[`docs/final/ci/JENKINS.md`](../../final/ci/JENKINS.md)**  
> Archivo: [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile)

Pipeline declarativo del backend. En GitHub el flujo completo es [DevSecOps Pipeline](https://github.com/jeanc24/ProyectoQA/actions/workflows/devsecops.yml) ([PIPELINE.md](../../final/ci/PIPELINE.md)).

| Stage | Comando | Equivalente GHA |
|-------|---------|-----------------|
| Checkout | `checkout scm` | `actions/checkout@v4` |
| Build | `./gradlew build -x test` | Build (sin tests) |
| Unit | `./gradlew test` | Unit tests |
| Integration | `./gradlew integrationTest` | Integration tests |
| API | `./gradlew apiTest contractTest` | API + Contract |
| Security → E2E | Ver [JENKINS.md](../../final/ci/JENKINS.md) | CICD-01 / CICD-02 |

## Prerrequisitos

- Docker en ejecución (Testcontainers en integration tests).
- Puerto **8082** libre para la UI de Jenkins.

## Levantar Jenkins local

Desde la raíz del repo:

```bash
docker compose build jenkins
docker compose up -d jenkins
```

UI: [http://localhost:8082](http://localhost:8082)

Contraseña inicial (solo primer arranque):

```bash
docker exec inventory-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### Plugins recomendados

En el asistente inicial o **Manage Jenkins → Plugins**:

- **Pipeline**
- **Git**
- **JUnit** (publicar resultados de tests)
- **Credentials Binding** (Sonar opcional)

## Crear el job

1. **New Item** → nombre `proyectoqa` → tipo **Pipeline** → OK.
2. En **Pipeline**:
   - **Definition:** Pipeline script from SCM
   - **SCM:** Git
   - **Repository URL:** URL de este repositorio
   - **Branch:** `develop` (o la rama del PR)
   - **Script Path:** `infra/jenkins/Jenkinsfile`
3. **Save** → **Build Now**.

El contenedor Jenkins monta `/var/run/docker.sock` para que Testcontainers, Security y Staging usen el daemon del host.

Variables Testcontainers (stage Integration):

- `TESTCONTAINERS_RYUK_DISABLED=true`
- `DOCKER_HOST=unix:///var/run/docker.sock`
- `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`

Ver [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile).

## Reproducir sin Jenkins (mismos comandos)

```bash
chmod +x gradlew
./gradlew build -x test
./gradlew test
./gradlew integrationTest
```

## Evidencia

- Avance 1 (#29): `docs/avance-1/ci/jenkins-build-5.png`
- Entrega final (#85 / CICD-02): `docs/final/ci/jenkins-pipeline-green.png` — ver [JENKINS.md](../../final/ci/JENKINS.md)
