# Documentación técnica — ProyectoQA

Incluye **diagramas de arquitectura**, **guía de instalación** y **manual de mantenimiento**.

| Documento hermano | Contenido |
| ----------------- | --------- |
| [REQUISITOS.md](REQUISITOS.md) | RF / RNF |
| [GUIA-PRUEBAS.md](GUIA-PRUEBAS.md) | Casos, resultados, defectos |
| [defensa/README.md](defensa/README.md) | Explicación profunda para oral |
| [ci/ENVIRONMENTS.md](ci/ENVIRONMENTS.md) | Compose, perfiles, cloud |
| [ci/PIPELINE.md](ci/PIPELINE.md) | Pipelines |
| [ci/CLOUD.md](ci/CLOUD.md) | Render / Vercel |

---

## 1. Arquitectura

### 1.1 Estilo

**Monolito modular por capas** desplegado como:

- API Spring Boot (Docker)
- SPA React (nginx / Vercel)
- PostgreSQL 16
- Keycloak 26 (IdP)
- Stack de observabilidad (solo Compose local)

No hay microservicios: un dominio acotado, un deployable de backend.

### 1.2 Diagrama de contexto (local / Compose)

```text
┌───────────────────────────────────────────────────────────┐
│ Navegador                                                 │
│  React SPA (nginx :3000)  ──►  keycloak-js (PKCE)         │
└──────────────┬────────────────────────────────────────────┘
               │ HTTP + Authorization: Bearer <JWT>
               ▼
┌───────────────────────────────────────────────────────────┐
│ Spring Boot API (:8080)                                   │
│  Controller → Service → Repository → JPA/Hibernate        │
│  Security Filter Chain (JWT / JWKS)                       │
└──────┬───────────────────────────┬────────────────────────┘
       │                           │
       ▼                           ▼
┌──────────────┐          ┌────────────────────┐
│ PostgreSQL16 │          │ Keycloak 26 (:8081)│
│ (:5433)      │          │ realm `inventory`  │
└──────────────┘          └────────────────────┘
       │
       ▼  métricas / trazas / logs
┌───────────────────────────────────────────────────────────┐
│ Prometheus · Alloy · Tempo · Loki · Grafana · Alertmanager │
└───────────────────────────────────────────────────────────┘
```

### 1.3 Diagrama cloud (staging / prod)

```text
develop → Deploy staging          main → Deploy prod
                │                              │
                ▼                              ▼
     ┌────────────────────┐         ┌────────────────────┐
     │ Render: API+KC+DB  │         │ Render: API+KC+DB  │
     │ (render.yaml)      │         │ (render.prod.yaml) │
     └─────────┬──────────┘         └─────────┬──────────┘
               │                              │
               ▼                              ▼
     ┌────────────────────┐         ┌────────────────────┐
     │ Vercel frontend    │         │ Vercel frontend    │
     │ (staging/preview)  │         │ (production)       │
     └────────────────────┘         └────────────────────┘

Observabilidad completa: se demuestra en Compose local (no desplegada en free tier).
```

### 1.4 Capas de la API

| Capa | Paquete | Responsabilidad |
| ---- | ------- | --------------- |
| Controller | `controller` | HTTP, status, `@PreAuthorize`, OpenAPI |
| Service | `application.service` | Negocio, transacciones, DTO |
| Repository | `domain.repository` | Acceso JPA |
| Entity | `domain.entity` | Modelo + Envers donde aplica |
| DTO | `application.dto` | Contrato entrada/salida |
| Config | `config` | Security, CORS, OpenAPI |

### 1.5 Perfiles Spring

| Perfil | Uso | Security |
| ------ | --- | -------- |
| `local` | IDE sin Keycloak | abierta |
| `docker` | Compose local / security | JWT |
| `staging` | Compose staging CI + Render staging | JWT |
| `prod` | Render prod (+ Compose prod-like) | JWT endurecido |
| `integration` / `api-test` | Tests | Testcontainers / MockMvc |

### 1.6 Docker Compose (resumen)

| Archivo | Para qué |
| ------- | -------- |
| `docker-compose.yml` | Dev + OBS + Jenkins; ZAP en GHA |
| `docker-compose.staging.yml` | Staging efímero CI (smoke + E2E) |
| `docker-compose.security.yml` | Security aislado (Jenkins) |
| `docker-compose.prod.yml` | Simulación prod-like local (opcional) |

Tabla completa: [`defensa/ARBOL.md`](defensa/ARBOL.md) § 7.1.

---

## 2. Guía de instalación

### 2.1 Prerrequisitos

| Herramienta | Versión mínima |
| ----------- | -------------- |
| Docker Desktop / Engine | 24+ |
| Java (Temurin) | 21 |
| Node.js | 20+ |
| Git | 2.x |
| (Opcional) gh CLI | última |

Docker debe estar **en ejecución** (Testcontainers y Compose lo requieren).

### 2.2 Instalación rápida (stack completo)

```bash
git clone https://github.com/jeanc24/ProyectoQA.git
cd ProyectoQA
cp .env.example .env
docker compose --env-file .env up --build -d
curl -sf http://localhost:8080/actuator/health   # {"status":"UP"}
```

Abrir: [http://localhost:3000](http://localhost:3000) → login `admin` / `admin`.

Detener: `docker compose --env-file .env down`

### 2.3 URLs locales

| Servicio | URL |
| -------- | --- |
| Frontend | http://localhost:3000 |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8081 |
| Grafana | http://localhost:3001 (`admin`/`admin`) |
| Prometheus | http://localhost:9090 |
| Jenkins | http://localhost:8082 |
| Postgres | `localhost:5433` (`inventory`/`inventory`) |

### 2.4 Variantes de instalación

**Solo app (sin OBS):**

```bash
docker compose up --build -d postgres keycloak api frontend
```

**Frontend en hot-reload (Vite):**

```bash
docker compose up -d postgres keycloak api
cd frontend && cp .env.example .env && npm install && npm run dev
# http://localhost:5173
```

**API con Gradle (debug):**

```bash
docker compose up -d postgres keycloak
CORS_ORIGINS=http://localhost:5173,http://localhost:3000 \
  ./gradlew bootRun --args="--spring.profiles.active=docker"
```

**Staging Compose (puertos 3008/8088/8181):**

```bash
cp .env.staging.example .env.staging
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build
```

Detalle ampliado: [README raíz](../../README.md) · [CLOUD.md](ci/CLOUD.md) · [JENKINS.md](ci/JENKINS.md).

### 2.5 Verificación post-instalación

```bash
./scripts/wait-for-stack.sh
./scripts/post-deploy-smoke.sh   # o security-smoke.sh en puertos default
```

---

## 3. Manual de mantenimiento

### 3.1 Operaciones cotidianas

| Tarea | Comando / acción |
| ----- | ---------------- |
| Ver logs API | `docker compose logs -f api` |
| Reiniciar API | `docker compose up -d --build api` |
| Rebuild FE tras cambio UI | `docker compose up -d --build frontend` |
| Bajar todo (conservar volúmenes) | `docker compose down` |
| Bajar y borrar volúmenes | `docker compose down -v` (borra datos PG) |
| Health | `curl -sf http://localhost:8080/actuator/health` |

### 3.2 Base de datos

- Esquema: solo vía **Flyway** (`src/main/resources/db/migration/V*.sql`).
- No editar a mano el esquema en prod/staging sin migración versionada.
- Backup lógico (local):  
  `docker compose exec postgres pg_dump -U inventory inventory > backup.sql`
- Restore:  
  `docker compose exec -T postgres psql -U inventory inventory < backup.sql`

### 3.3 Keycloak / usuarios

- Realm como código: `keycloak/inventory-realm.json` (reimport en contenedor nuevo).
- Usuarios demo: `admin`, `viewer`, `stock-manager`, `auditor` (password = username).
- Consola admin IdP: http://localhost:8081 (`admin`/`admin` en local).
- En cloud: rotar `KEYCLOAK_ADMIN*` y client secrets en el dashboard (no hardcodear en prod YAML).

### 3.4 Secretos y configuración

| Ambiente | Dónde viven |
| -------- | ----------- |
| Local | `.env` (desde `.env.example`, gitignored) |
| Staging Compose | `.env.staging` |
| Security Compose | `.env.security` |
| Render / Vercel | Env vars / secrets del dashboard + GHA `vars`/`secrets` |

Nunca commitear `.env` ni tokens Sonar/NVD/Vercel.

### 3.5 CI/CD — qué vigilar

| Pipeline | Acción de mantenimiento |
| -------- | ----------------------- |
| DevSecOps | Si falla quality gate → ver job rojo (tests/Sonar/ZAP/E2E) |
| Deploy staging/prod | Revisar Render deploy logs + Vercel; smoke cloud si hay `STAGING_*`/`PROD_*` |
| Jenkins local | Wipe workspace si compile raro; asegurar `.env` (`cp .env.example .env`) antes de Integration |

### 3.6 Observabilidad

| Señal | Dónde |
| ----- | ----- |
| Métricas | Prometheus → Grafana dashboard Observabilidad |
| Logs | Loki (vía Alloy) |
| Trazas | Tempo (OTLP vía Alloy) |
| Alertas | Prometheus rules → Alertmanager :9093 |

Si no hay datos: generar tráfico (UI o curl con JWT) y esperar scrape (~15s).

### 3.7 Actualizaciones

1. Rama feature → PR a `develop` (DevSecOps verde + conventional commits).
2. Merge a `develop` → staging cloud + calidad.
3. PR `develop` → `main` → producción cloud.
4. Dependencias: Gradle/npm; SCA con Dependency-Check en pipeline.
5. Imagen Keycloak/Postgres: pin de versiones en Compose/Dockerfiles; probar en staging Compose antes de prod.

### 3.8 Incidentes frecuentes

| Síntoma | Qué revisar |
| ------- | ----------- |
| API 401/403 inesperados | Issuer/JWKS, roles en JWT, `CORS_ORIGINS` |
| Flyway falla | Logs API; baseline en DB compartida cloud |
| Keycloak OOM en Render free | `JAVA_OPTS_APPEND`, cold start, suspender el otro ambiente |
| E2E flaky local | `wait-for-stack` + `--workers=1` |
| Jenkins Integration sin `.env` | `cp .env.example .env` en el stage |
| Playwright PKCE en Jenkins | `jenkins-e2e-portforward.mjs` |

Troubleshooting ampliado: [README](../../README.md#troubleshooting) · [defensa/README §13](defensa/README.md).

### 3.9 Contacto / ownership (académico)

| Área | Artefacto |
| ---- | --------- |
| API / dominio | `src/main/java/...` |
| UI | `frontend/` |
| IdP | `keycloak/`, `infra/keycloak/` |
| CI | `.github/workflows/`, `infra/jenkins/` |
| Docs entrega | `docs/final/` |
