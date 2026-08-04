# Ambientes, Compose, perfiles Spring y pipelines

Esta guía separa cuatro conceptos que suelen confundirse:

1. **Ambiente:** lugar donde corre la aplicación (local, staging o producción).
2. **Docker Compose:** receta para levantar un stack de contenedores en una máquina o runner.
3. **Perfil Spring:** configuración interna que carga la API (`docker`, `staging`, `prod`, etc.).
4. **Pipeline:** automatización que compila, prueba o despliega un ambiente.

> Un pipeline **no es** un ambiente y un Compose **no es** necesariamente un despliegue cloud.

**Historia oral:** primero **emulamos** staging/prod con Compose + perfiles Spring; después los workflows `deploy-staging.yml` / `deploy-prod.yml` **publican** a Render (API + Keycloak + Postgres) + Vercel (FE). En Render free (512 MB) Keycloak dio OOM → OBS solo en Compose local.

---

## 0. Tabla maestra — Staging/Prod: Compose vs deploy yml

| Ambiente | Emular (Compose) — FASE 1 | Perfil Spring | Qué levanta el Compose | Publicar (pipeline) — FASE 2 | Blueprint / FE cloud | Qué queda publicado |
| -------- | ------------------------- | ------------- | ---------------------- | ---------------------------- | -------------------- | ------------------- |
| **Staging** | `docker-compose.staging.yml` | `staging` (`application-staging.yml`) | Postgres, API, FE, Keycloak + OBS (Tempo, Loki, Alloy, Prometheus, Alertmanager, Grafana). **Sin** Jenkins. Puertos **8088 / 3008 / 8181**. En CI: up → smoke → Playwright → `down -v` (**efímero**) | `.github/workflows/deploy-staging.yml` (push `develop`) | `render.yaml` + Vercel (`develop`) | Persistente: DB + API + Keycloak en Render; FE en Vercel. **Sin** OBS |
| **Prod** | `docker-compose.prod.yml` | `prod` (`application-prod.yml`) | Stack production-like local (Swagger off, etc.). Solo emulación en laptop | `.github/workflows/deploy-prod.yml` (push `main`) | `infra/render/render.prod.yaml` + Vercel (`main`) | Persistente: DB + API + Keycloak prod en Render; FE prod en Vercel. **Sin** OBS |

| | Staging Compose | Staging deploy yml | Prod Compose | Prod deploy yml |
|--|-----------------|--------------------|--------------|-----------------|
| **Archivo** | `docker-compose.staging.yml` | `deploy-staging.yml` | `docker-compose.prod.yml` | `deploy-prod.yml` |
| **Para qué** | Emular / probar staging | Publicar staging cloud | Emular prod local | Publicar prod cloud |
| **Dónde corre** | Laptop o runner CI | GitHub Actions → Render + Vercel | Solo laptop | GitHub Actions → Render + Vercel |
| **Duración** | Se destruye (`down -v`) | Queda arriba | Mientras lo tengas up | Queda arriba |
| **Quién lo dispara** | DevSecOps job `staging-deploy-e2e`, Jenkins, o manual | Push a `develop` | Manual | Push a `main` |

### Todos los Compose

| Compose | Perfil | Levanta | Cuándo |
| ------- | ------ | ------- | ------ |
| `docker-compose.yml` | `docker` | Postgres, API, FE, Keycloak, Tempo, Loki, Alloy, Prometheus, Alertmanager, Grafana, **Jenkins** (8080/3000/8081/3001/8082) | Dev diario + OBS + ZAP en DevSecOps |
| `docker-compose.staging.yml` | `staging` | Postgres, API, FE, Keycloak + OBS (8088/3008/8181) | Emular staging; CI efímero |
| `docker-compose.prod.yml` | `prod` | Stack prod-like local | Emular prod; **no** es Render |
| `docker-compose.security.yml` | `docker` | Solo Postgres + Keycloak + API (8090/8091/5435) | Jenkins Security (smoke + ZAP) |

### Todos los pipelines (yml / Jenkinsfile)

| Pipeline | Archivo | Trigger | Qué hace / qué levanta |
| -------- | ------- | ------- | ---------------------- |
| **DevSecOps** | `.github/workflows/devsecops.yml` | Push/PR `develop` | Calidad: tests, JaCoCo, Sonar, Docker build. ZAP → `docker-compose.yml`. Staging E2E → `docker-compose.staging.yml` → down. Quality-gate. **No** publica Render/Vercel |
| **Deploy staging** | `.github/workflows/deploy-staging.yml` | Push `develop` | Publica: `render.yaml` (DB+API+KC) + Vercel FE. Smoke cloud opcional |
| **Deploy prod** | `.github/workflows/deploy-prod.yml` | Push `main` | Publica: `render.prod.yaml` + Vercel FE prod |
| **Conventional commits** | `.github/workflows/conventional-commits.yml` | PR develop/main | Valida mensajes de commit |
| **Legacy CI** | `.github/workflows/ci.yml` | Manual | Build + tests + Sonar |
| **Legacy Security** | `.github/workflows/security.yml` | Manual | Dep-Check + ZAP |
| **Legacy Post-deploy staging** | `.github/workflows/post-deploy-staging.yml` | Manual | Solo `docker-compose.staging.yml` + smoke + Playwright |
| **Jenkins** | `infra/jenkins/Jenkinsfile` | Manual `:8082` | Espejo DevSecOps: Security → `docker-compose.security.yml`; Staging → `docker-compose.staging.yml`. **Sin** cloud |

```text
DevSecOps:
  build-and-test → docker-images
                 → dependency-check
                 → zap-baseline        (docker-compose.yml)
                 → staging-deploy-e2e  (docker-compose.staging.yml → down)
                 → quality-gate

deploy-staging / deploy-prod:
  trigger-render → deploy-vercel → smoke-cloud (opcional)
```

---

## 1. Arquitectura actual: qué corre dónde

| Objetivo | Ejecución real | Archivos principales |
| -------- | -------------- | -------------------- |
| Desarrollo y observabilidad | Compose local | `docker-compose.yml` |
| Seguridad aislada en Jenkins | Compose local/CI | `docker-compose.security.yml` |
| Staging efímero para smoke/E2E | Runner de GitHub Actions/Jenkins | `docker-compose.staging.yml` |
| Staging persistente | Render + Vercel, branch `develop` | `render.yaml`, `deploy-staging.yml` |
| Producción persistente | Render + Vercel, branch `main` | `infra/render/render.prod.yaml`, `deploy-prod.yml` |
| Simulación local de producción | Opcional; no es el deploy cloud | `docker-compose.prod.yml` |

### Respuesta directa: ¿sobran los Compose de staging y producción?

- **`docker-compose.staging.yml` no sobra.** No representa el hosting cloud; crea un staging
  **efímero y reproducible dentro del runner de CI**. Lo usan el job
  `staging-deploy-e2e` de `devsecops.yml`, el workflow manual
  `post-deploy-staging.yml` y Jenkins. Se levanta, se prueba y se destruye con `down -v`.
- **`docker-compose.prod.yml` es opcional.** No lo usa ningún workflow activo ni Render.
  Sirve como simulación local **production-like** para comprobar el perfil `prod`
  (Swagger apagado, logging y Actuator endurecidos). Puede conservarse como evidencia y
  herramienta de validación, pero no debe presentarse como “el despliegue de producción”.
- **`docker-compose.security.yml` sí es importante para Jenkins.** Es un stack mínimo y
  aislado (Postgres + Keycloak + API) para smoke JWT/CORS y ZAP sin chocar con el stack
  local. En GitHub Actions, el job ZAP del DevSecOps usa actualmente servicios del
  `docker-compose.yml` normal; el Compose de seguridad está especializado en Jenkins.
- **`docker-compose.yml` es el stack principal local.** Levanta aplicación, Keycloak,
  Postgres y observabilidad; también se reutiliza en el job ZAP de GitHub Actions.

---

## 2. Qué hace cada Docker Compose

### `docker-compose.yml` — desarrollo local (principal)

**Dónde se usa:**

- Desarrollo y demostración local.
- Grafana, Prometheus, Loki, Tempo y Alloy.
- Job `zap-baseline` de `.github/workflows/devsecops.yml`.
- Workflow manual `.github/workflows/security.yml`.

**Qué levanta:** Postgres, API, frontend, Keycloak, Tempo, Loki, Alloy,
Prometheus, Alertmanager, Grafana y Jenkins.

**Perfil API:** `SPRING_PROFILES_ACTIVE=docker`.

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
```

### `docker-compose.security.yml` — seguridad aislada

**Dónde se usa:** stage Security de `infra/jenkins/Jenkinsfile` y ejecución manual.

**Por qué existe:**

- Solo levanta Postgres + Keycloak + API; ZAP no necesita frontend ni observabilidad.
- Usa puertos 5435/8091/8090.
- No define `container_name`, por lo que no choca con el Compose normal.
- Evita bind mounts problemáticos cuando Jenkins corre dentro de un contenedor.

**Perfil API:** `docker`, porque requiere la misma seguridad JWT de la aplicación local.
El nombre del perfil describe la configuración Spring del stack protegido, no el objetivo
del Compose.

```bash
cp .env.security.example .env.security
docker compose -f docker-compose.security.yml --env-file .env.security up -d --build
```

### `docker-compose.staging.yml` — staging efímero de CI

**Dónde se usa:**

- Job `staging-deploy-e2e` de `.github/workflows/devsecops.yml`.
- Workflow manual `.github/workflows/post-deploy-staging.yml`.
- Stage de staging/E2E de Jenkins (a través de `scripts/jenkins-host-compose.sh`).

**Qué hace:** crea una réplica temporal del stack con otros puertos (API 8088,
frontend 3008, Keycloak 8181), corre smoke y Playwright, guarda evidencias y luego
elimina contenedores y volúmenes.

**Perfil API:** `staging`.

```text
runner CI
  → compose staging up
  → wait-for-stack
  → post-deploy smoke
  → Playwright E2E
  → compose down -v
```

Esto es un **ambiente de prueba efímero**, no el staging persistente de Render/Vercel.

### `docker-compose.prod.yml` — simulación production-like local

**Dónde se usa:** solo manualmente; no lo llama un workflow activo.

**Qué valida:** perfil `prod`, Swagger deshabilitado, Actuator reducido, sampling
de trazas bajo, logs más restrictivos y Postgres limitado a loopback.

**Perfil API:** `prod`.

```bash
cp .env.production.example .env.production
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
```

No es producción real: Keycloak todavía usa `start-dev` para facilitar la demo.
La producción real se define con Render/Vercel.

---

## 3. Cloud persistente: staging y producción reales

| Ambiente | Branch | Backend / Keycloak / DB | Frontend | Perfil Spring |
| -------- | ------ | ----------------------- | -------- | ------------- |
| Staging | `develop` | Render (`render.yaml`) | Vercel | `staging` |
| Producción | `main` | Render (`infra/render/render.prod.yaml`) | Vercel | `prod` |

Los workflows `deploy-staging.yml` y `deploy-prod.yml` pueden disparar hooks de Render,
construir/desplegar Vercel y ejecutar smoke cloud. Si faltan secrets de despliegue,
algunos pasos se omiten y Render puede depender de su auto-deploy.

**Observabilidad:** Grafana/Loki/Tempo/Prometheus se demuestran en Compose local.
No se despliega una copia completa de OBS por ambiente cloud.

**Limitación del plan free usada en la demo:** una sola Postgres activa; staging y
producción cloud pueden requerir suspender un stack antes de encender el otro.

---

## 4. Perfiles Spring y todos los `application*.yml`

Spring carga siempre `application.yml` y luego superpone
`application-<perfil>.yml`, según `SPRING_PROFILES_ACTIVE`.

Ejemplo:

```text
SPRING_PROFILES_ACTIVE=staging
→ application.yml + application-staging.yml
```

| Archivo | Cuándo se carga | Responsabilidad |
| ------- | --------------- | --------------- |
| `application.yml` | Siempre | Config base: JPA, Flyway, puerto, CORS/Keycloak defaults demo, Actuator, OTel, logging con traceId y springdoc |
| `application-local.yml` | Perfil `local` | Backend ejecutado desde IDE/Gradle contra Postgres local, **sin Keycloak**; activa `SecurityConfig` abierta |
| `application-docker.yml` | Perfil `docker` | Stack local protegido y Compose security: datasource por env, issuer/JWKS, Keycloak Admin API y CORS |
| `application-staging.yml` | Perfil `staging` | Staging CI y Render staging; exige datasource, issuer/JWKS y credenciales por variables; logs INFO |
| `application-prod.yml` | Perfil `prod` | Render prod y Compose prod-like; sin defaults sensibles, Swagger apagado, Actuator mínimo, sampling 0.1 y logging endurecido |
| `application-integration.yml` | Perfil `integration` | Tests Testcontainers; DB se inyecta dinámicamente y se desactiva exportación OTel |
| `src/test/resources/application-api-test.yml` | Perfil `api-test` | Tests MockMvc: permite reemplazar beans y excluye la auto-configuración OAuth2 para usar `ApiTestSecurityConfig` |

### Relación con las clases de seguridad

| Perfil | Clase activa | Resultado |
| ------ | ------------ | --------- |
| `local` | `SecurityConfig` (`@Profile("local")`) | Endpoints abiertos para desarrollo sin Keycloak |
| `docker`, `staging`, `prod` | `DockerSecurityConfig` | OAuth2 Resource Server, JWT, roles y `@PreAuthorize` |
| `integration` | Depende del test | IT de DB usa `integration`; IT de Keycloak usa `integration` + `docker` |
| `api-test` | `ApiTestSecurityConfig` en tests | MockMvc con authorities simuladas; complementado por `src/test/resources/application-api-test.yml` |

> `DockerSecurityConfig` es un nombre histórico poco preciso: no configura Docker.
> Configura seguridad JWT para los perfiles protegidos `docker`, `staging` y `prod`.
> Un nombre más descriptivo sería `JwtResourceServerSecurityConfig`.

### Quién activa cada perfil

| Ejecutor | Perfil |
| -------- | ------ |
| `docker-compose.yml` | `docker` |
| `docker-compose.security.yml` | `docker` |
| `docker-compose.staging.yml` | `staging` |
| `docker-compose.prod.yml` | `prod` |
| `render.yaml` | `staging` |
| `infra/render/render.prod.yaml` | `prod` |
| `AbstractIntegrationTest` | `integration` |
| `AbstractKeycloakIntegrationTest` | `integration` + `docker` |

---

## 5. Flujo de ramas

```text
PR / push a develop
  ├─ DevSecOps (calidad; usa Compose local/staging)
  ├─ Deploy staging cloud (deploy-staging.yml)
  └─ Render/Vercel staging

merge / push a main
  ├─ Conventional Commits (durante el PR)
  ├─ Deploy production cloud (deploy-prod.yml)
  └─ Render/Vercel production
```

> Frase cierre: Compose staging/prod = emular. `deploy-staging.yml` / `deploy-prod.yml` = publicar. DevSecOps = calidad (usa los Compose). 512 MB free → OOM Keycloak → OBS solo local.

---

## 6. Política de secretos

| Capa | Política |
| ---- | -------- |
| Git | Nunca `.env`, `.env.staging`, `.env.production`, `.env.security`; solo `*.example` |
| Local / CI académico | Defaults demo de `.env.example` + realm importado |
| GitHub Actions | Secrets de Sonar/NVD/Keycloak/Vercel/Render según workflow |
| Render / Vercel | Variables por ambiente, nunca en la imagen |
| Producción | Rotar passwords/client secrets; CORS e issuer exactos; Keycloak `start` |

El secret demo del client vive en `inventory-realm.json` para que `--import-realm`
funcione sin provisioning adicional. Eso es aceptable para demo, no para secretos reales.
