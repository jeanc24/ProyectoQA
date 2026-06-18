# CI — Jenkins (avance 1)

Pipeline declarativo del backend, alineado con [GitHub Actions CI](https://github.com/jeanc24/ProyectoQA/actions/workflows/ci.yml).

| Stage | Comando | Equivalente GHA |
|-------|---------|-----------------|
| Checkout | `checkout scm` | `actions/checkout@v4` |
| Build | `./gradlew build -x test` | Build (sin tests) |
| Unit Tests | `./gradlew test` | Unit tests |
| Integration Tests | `./gradlew integrationTest` | Integration tests |

Archivo: [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile)

## Prerrequisitos

- Docker en ejecución (Testcontainers en integration tests).
- Puerto **8082** libre para la UI de Jenkins.

## Levantar Jenkins local

Desde la raíz del repo:

```bash
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

## Crear el job

1. **New Item** → nombre `proyectoqa-backend` → tipo **Pipeline** → OK.
2. En **Pipeline**:
   - **Definition:** Pipeline script from SCM
   - **SCM:** Git
   - **Repository URL:** URL de este repositorio
   - **Branch:** `develop` (o la rama del PR)
   - **Script Path:** `infra/jenkins/Jenkinsfile`
3. **Save** → **Build Now**.

El contenedor Jenkins monta `/var/run/docker.sock` para que Testcontainers pueda levantar PostgreSQL durante `integrationTest`.

## Reproducir sin Jenkins (mismos comandos)

```bash
chmod +x gradlew
./gradlew build -x test
./gradlew test
./gradlew integrationTest
```

## Evidencia (issue #29)

Tras una ejecución exitosa:

1. Abre el build en Jenkins (número de build visible, stages en verde).
2. Captura pantalla y guárdala como:

   `docs/avance-1/ci/jenkins-build-1.png`

3. Incluye la imagen en el PR que cierra #29.

Si aún no existe el screenshot, el pipeline y esta guía bastan para implementar; la captura se agrega después del primer **Build Now** en verde.
