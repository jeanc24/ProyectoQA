# Entornos: local / staging / production (ENV-01 · ENV-03)

## Resumen

| Aspecto | Dev (`docker` / local) | Staging (`staging`) | Production (`prod`) |
| ------- | ---------------------- | ------------------- | ------------------- |
| Compose | `docker-compose.yml` | `docker-compose.staging.yml` | `docker-compose.prod.yml` (opcional) |
| Env file | `.env` | `.env.staging` | `.env.production` |
| Perfil Spring | `docker` / `local` | `staging` | `prod` |
| Secrets en git | No (`.env` gitignored) | No | No |
| Swagger UI | Sí | Sí | **No** |
| Actuator | health, info, prometheus, metrics | Igual (base) | **health + prometheus** (configurable) |
| Logging | INFO | INFO | **WARN** root / INFO app |
| OTel sampling | 1.0 (demo) | 1.0 | **0.1** por defecto |
| Puertos host (API/FE/KC) | 8080 / 3000 / 8081 | 8088 / 3008 / 8181 | 8089 / 3009 / 8182 |
| Postgres publicado | Sí (red) | Sí | **127.0.0.1** only |
| Seguridad HTTP | `DockerSecurityConfig` | `DockerSecurityConfig` | `DockerSecurityConfig` (prod: sin Swagger en permitAll) |

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
