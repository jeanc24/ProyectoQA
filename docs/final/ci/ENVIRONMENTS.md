# Entornos: local / staging / production (ENV-01 · ENV-03)

## Resumen

| Aspecto | Dev (`docker` / local) | Staging (`staging`) | Production (`prod`) |
| ------- | ---------------------- | ------------------- | ------------------- |
| Compose | `docker-compose.yml` | `docker-compose.staging.yml` | `docker-compose.prod.yml` (opcional) |
| Env file | `.env` (desde `.env.example`) | `.env.staging` | `.env.production` |
| Perfil Spring | `docker` / `local` | `staging` | `prod` |
| Secrets en git | No (`.env` gitignored; compose usa `${VAR}`) | No | No |
| Swagger UI | Sí | Sí | **No** |
| Actuator | health, info, prometheus, metrics | Igual (base) | **health + prometheus** (configurable) |
| Logging | INFO | INFO | **WARN** root / INFO app |
| OTel sampling | 1.0 (demo) | 1.0 | **0.1** por defecto |
| Puertos host (API/FE/KC) | 8080 / 3000 / 8081 | 8088 / 3008 / 8181 | 8089 / 3009 / 8182 |
| Postgres publicado | Sí (red) | Sí | **127.0.0.1** only |
| Seguridad HTTP | `DockerSecurityConfig` | `DockerSecurityConfig` | `DockerSecurityConfig` (prod: sin Swagger en permitAll) |

## Development

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
```

Postgres, Keycloak, Grafana, CORS, Vite y OTel se leen desde `.env` (mismo patrón que staging/prod). Valores demo en `.env.example`; tokens personales (`NVD_API_KEY`, `SONAR_TOKEN`) solo en tu `.env` local.

## Cloud (Render + Vercel)

Guía: [`CLOUD.md`](CLOUD.md).

| Ambiente | Branch | Backend | Frontend |
| -------- | ------ | ------- | -------- |
| Staging | `develop` | Render (`render.yaml`) | Vercel |
| Production | `main` | Render (`infra/render/render.prod.yaml`) | Vercel |

**Grafana:** un solo tablero en Compose local. No se despliegan 3 Grafanas (dev/staging/prod).

Workflows: `deploy-staging.yml` / `deploy-prod.yml` (hooks + smoke). El job `staging-deploy-e2e` de DevSecOps sigue siendo staging **efímero en CI**, no el cloud.

## Política de secretos

| Capa | Qué hacer |
| ---- | --------- |
| **Git** | Nunca `.env`, `.env.staging`, `.env.production`, `.env.security` (gitignored). Solo `*.example`. |
| **Demo local / CI académico** | Defaults en [`.env.example`](../../../.env.example) + [`keycloak/inventory-realm.json`](../../../keycloak/inventory-realm.json). Scripts cargan vía [`scripts/lib/load-env.sh`](../../../scripts/lib/load-env.sh). |
| **GitHub Actions** | Opcional: Secrets `KEYCLOAK_CLIENT_SECRET`, `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`. Si están vacíos, `load-env.sh` usa demo. Obligatorios: `SONAR_TOKEN`, `NVD_API_KEY` (este último opcional). |
| **Jenkins** | Credenciales `SONAR_TOKEN` / `NVD_API_KEY`; Keycloak desde `.env.security`. |
| **Producción** | Rotar *todos* los `change-me-*`, no reutilizar el secret del realm demo, Keycloak en modo `start`, secrets en vault/PaaS. |
| **Tests** | Gradle inyecta `.env` en la JVM de test (`build.gradle`). Sin `.env`: `cp .env.example .env`. No hay clase Java con secretos demo. |
| **Postman/Newman** | [`post-deploy-smoke.collection.json`](post-deploy-smoke.collection.json): `clientSecret` / users vacíos → rellenar en Environment local, no en el JSON versionado. |

El client secret del realm importado **tiene** que vivir en `inventory-realm.json` para que Keycloak `--import-realm` funcione sin un paso extra de provisioning. Eso es aceptable en demo; en producción el realm no se versiona con secretos reales.

Stack aislado para ZAP/smoke (Jenkins):

```bash
cp .env.security.example .env.security
docker compose -f docker-compose.security.yml --env-file .env.security up -d --build
```

Perfiles Spring `local` / `docker` también leen `SPRING_DATASOURCE_*`, `POSTGRES_*`, `KEYCLOAK_*` y `CORS_ORIGINS` (defaults demo si no hay env).

## Staging (ENV-01)

```bash
cp .env.staging.example .env.staging
docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build
```

Uso: pre-producción / post-deploy tests (ENV-02) y job E2E del DevSecOps Pipeline.

## Production (ENV-03)

Plantilla **production-like** para demo local o base de un deploy real. No sustituye un cluster con TLS, secrets manager y Keycloak HA.

```bash
cp .env.production.example .env.production   # cambiar todos los change-me-*
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
```

| Servicio | URL (defaults) |
| -------- | -------------- |
| Frontend | http://localhost:3009 |
| API | http://localhost:8089 |
| Keycloak | http://localhost:8182 |
| Grafana | http://127.0.0.1:3012 |

Health: `curl -sf http://localhost:8089/actuator/health`  
Swagger debe fallar (deshabilitado): `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8089/swagger-ui.html`

### Notas de deploy real

1. **HTTPS** delante de API, frontend y Keycloak (reverse proxy / ingress).
2. **Secrets** solo en el entorno o vault (nunca en imágenes ni en git).
3. **Keycloak**: modo `start` (no `start-dev`), hostname y DB propios; rotar `KEYCLOAK_ADMIN_PASSWORD` y client secrets.
4. **CORS** limitado al dominio público del frontend (`CORS_ORIGINS`).
5. **Prometheus/Grafana** no deben ser públicos; scrape en red privada.

Archivos: [`application-prod.yml`](../../../src/main/resources/application-prod.yml), [`docker-compose.prod.yml`](../../../docker-compose.prod.yml), [`.env.production.example`](../../../.env.production.example).
