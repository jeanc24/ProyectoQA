# Deploy cloud — Staging & Production (Render + Vercel)

**Rama de trabajo:** `feature/cloud-deploy-staging-prod`  
**Decisión:** FE en **Vercel**, API + Postgres + Keycloak en **Render**.  
**Branches:** `develop` → staging · `main` → prod.  
**Grafana:** **uno solo en Compose local** (no hay Grafana staging/prod en cloud).

El pipeline DevSecOps sigue levantando staging **efímero en el runner** (prueba CI). Estos workflows despliegan ambientes **persistentes** en la nube.

---

## Checklist de servicios

### Render — Staging (`render.yaml`)

| Recurso | Nombre Blueprint | Plan |
| ------- | ---------------- | ---- |
| Postgres | `inventory-staging-db` | free |
| API | `inventory-api-staging` | free |
| Keycloak | `inventory-keycloak-staging` | free |

### Render — Production (`infra/render/render.prod.yaml`)

| Recurso | Nombre Blueprint | Plan |
| ------- | ---------------- | ---- |
| Postgres | `inventory-prod-db` | free |
| API | `inventory-api-prod` | free |
| Keycloak | `inventory-keycloak-prod` | free |

> **Keycloak en plan free (512 MB):** es justo (Red Hat recomienda ≥750 MB). Usa
> `start --optimized`, Postgres, `--cache=local`, heap **128 m** y Metaspace **168 m**
> (Liquibase del 1er boot falla con Metaspace 96 m). Tras mergear: Manual Deploy y
> actualizar en el dashboard `JAVA_OPTS_*` si quedaron valores viejos.

### Vercel

| Ambiente | Proyecto sugerido | Root Directory | Branch |
| -------- | ----------------- | -------------- | ------ |
| Staging | `proyectoqa-staging` | `frontend` | `develop` |
| Prod | `proyectoqa` | `frontend` | `main` |

Env build (Vercel → Settings → Environment Variables):

| Variable | Staging | Prod |
| -------- | ------- | ---- |
| `VITE_API_URL` | `https://inventory-api-staging.onrender.com` | URL API prod |
| `VITE_KEYCLOAK_URL` | `https://inventory-keycloak-staging.onrender.com` | URL KC prod |
| `VITE_KEYCLOAK_REALM` | `inventory` | `inventory` |
| `VITE_KEYCLOAK_CLIENT_ID` | `inventory-frontend` | `inventory-frontend` |

SPA: [`frontend/vercel.json`](../../../frontend/vercel.json) (rewrite → `index.html`).

---

## Setup manual (orden)

### 1. Render staging

1. [dashboard.render.com](https://dashboard.render.com) → **New** → **Blueprint**.
2. Conectar repo `jeanc24/ProyectoQA`, branch `develop`, archivo `render.yaml`.
3. Rellenar env `sync: false` cuando el wizard lo pida (puedes poner placeholders y editar después):
   - Keycloak: `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` (fuertes).
   - `KC_HOSTNAME` = URL pública final del servicio Keycloak (ej. `https://inventory-keycloak-staging.onrender.com`).
4. Aplicar Blueprint → esperar Postgres + Keycloak + API.
5. En **API**, fijar:
   - `KEYCLOAK_ISSUER_URI` = misma URL pública de Keycloak + `/realms/inventory`
   - `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` =  
     `https://<keycloak-public>/realms/inventory/protocol/openid-connect/certs`  
     (o host interno de Render si prefieres red privada: `http://inventory-keycloak-staging:8080/...` — el **issuer** debe ser el público).
   - `KEYCLOAK_ADMIN_SERVER_URL` = URL interna o pública de Keycloak (Admin API).
   - `CORS_ORIGINS` = URL del front Vercel staging.
6. Settings → **Deploy Hook** en API y Keycloak → guardar URLs en GitHub Secrets.

### 2. Vercel staging

1. Importar repo → Root `frontend` → framework Vite.
2. Variables `VITE_*` (tabla arriba).
3. Deploy. Copiar URL (`*.vercel.app`) a `CORS_ORIGINS` en Render API y a GitHub var `STAGING_FRONTEND_URL`.
4. El realm ya incluye `https://*.vercel.app/*` en redirect URIs / web origins.

### 3. GitHub (staging)

**Variables (Settings → Variables):**

| Name | Ejemplo |
| ---- | ------- |
| `STAGING_API_URL` | `https://inventory-api-staging.onrender.com` |
| `STAGING_KEYCLOAK_URL` | `https://inventory-keycloak-staging.onrender.com` |
| `STAGING_FRONTEND_URL` | `https://proyectoqa-staging.vercel.app` |

**Secrets:**

| Name | Uso |
| ---- | --- |
| `VERCEL_TOKEN` | CLI |
| `VERCEL_ORG_ID` | CLI |
| `VERCEL_PROJECT_ID_STAGING` | CLI |
| `RENDER_DEPLOY_HOOK_API_STAGING` | opcional |
| `RENDER_DEPLOY_HOOK_KEYCLOAK_STAGING` | opcional |
| `STAGING_KEYCLOAK_CLIENT_ID` | smoke (default `inventory-api`) |
| `STAGING_KEYCLOAK_CLIENT_SECRET` | smoke (realm demo o el rotado) |
| `STAGING_KEYCLOAK_ADMIN` / `_PASSWORD` | smoke admin / tokens |

### 4. Production (igual, archivo distinto)

1. Blueprint con path `infra/render/render.prod.yaml`, branch `main`.
2. Segundo proyecto Vercel (o production env del mismo).
3. Secrets/vars con prefijo `PROD_*` y `VERCEL_PROJECT_ID_PROD`.
4. Workflow: [`.github/workflows/deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml) (environment GitHub `production`).

---

## Pipelines

| Workflow | Trigger | Qué hace |
| -------- | ------- | -------- |
| [`deploy-staging.yml`](../../../.github/workflows/deploy-staging.yml) | push `develop` + manual | Hook Render + Vercel preview + smoke cloud |
| [`deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml) | push `main` + manual | Idem prod |
| [`devsecops.yml`](../../../.github/workflows/devsecops.yml) | push/PR `develop` | CI + staging Compose **efímero** (sin cloud) |

Si faltan secrets Vercel/Render, los jobs **avisan y no fallan** el pipeline (permite mergear la rama antes de crear las cuentas).

---

## Issuer Keycloak (crítico)

El JWT lleva `iss` = URL con la que el navegador habló a Keycloak.  
La API valida `KEYCLOAK_ISSUER_URI` **exactamente igual**.

```
KC_HOSTNAME=https://inventory-keycloak-staging.onrender.com
KEYCLOAK_ISSUER_URI=https://inventory-keycloak-staging.onrender.com/realms/inventory
```

Mismo patrón que el bug `localhost` vs `host.docker.internal` documentado en la defensa.

---

## Grafana (un solo tablero)

| Ambiente | Observabilidad |
| -------- | -------------- |
| Local Compose | Prometheus + Loki + Tempo + **Grafana :3001** |
| Staging / Prod cloud | Sin stack OBS (API puede dejar OTel off / sampling 0) |

Frase para el profesor: *“Un Grafana central en el entorno de desarrollo/demo; tres Grafanas (dev/staging/prod) no aportan y no son viables en free tier.”*

---

## Verificación rápida

```bash
# Health API staging
curl -sf "$STAGING_API_URL/actuator/health"

# Token + products
./scripts/wait-for-stack.sh   # con API_URL y KEYCLOAK_URL cloud
./scripts/post-deploy-smoke.sh
```

Login en el front Vercel con `admin` / `admin` (usuarios del realm importado).
