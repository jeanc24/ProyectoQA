# Deploy cloud — Staging & Production (Render + Vercel)

**Decisión:** FE en **Vercel**, API + Postgres + Keycloak en **Render**.  
**Branches:** `develop` → staging · `main` → prod.  
**Grafana:** **uno solo en Compose local** (no hay Grafana staging/prod en cloud).

El pipeline DevSecOps sigue levantando staging **efímero en el runner** (prueba CI). Los workflows `deploy-staging.yml` / `deploy-prod.yml` disparan ambientes **persistentes** en la nube (hooks opcionales).

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

> **Keycloak free (512 MB):** `JAVA_OPTS_APPEND=-Xms64m -Xmx384m -XX:MaxMetaspaceSize=128m`  
> (sin `JAVA_OPTS_KC_HEAP`, sin `UseSerialGC`), Postgres, `healthCheckPath: /`,  
> `KC_DB_USERNAME` / `PASSWORD` desde la DB. Si en el dashboard quedó `JAVA_OPTS_KC_HEAP`, bórralo y redespliega.

### Vercel

| Ambiente | Proyecto sugerido | Root Directory | Branch |
| -------- | ----------------- | -------------- | ------ |
| Staging | `proyecto-qastaging` | `frontend` | `develop` |
| Prod | segundo proyecto o mismo con branch `main` | `frontend` | `main` |

Env build (Vercel → Settings → Environment Variables):

| Variable | Staging | Prod |
| -------- | ------- | ---- |
| `VITE_API_URL` | `https://inventory-api-staging.onrender.com` | URL API prod |
| `VITE_KEYCLOAK_URL` | `https://inventory-keycloak-staging.onrender.com` | URL KC prod |
| `VITE_KEYCLOAK_REALM` | `inventory` | `inventory` |
| `VITE_KEYCLOAK_CLIENT_ID` | `inventory-frontend` | `inventory-frontend` |

SPA: [`frontend/vercel.json`](../../../frontend/vercel.json) (rewrite → `index.html`).

---

## Setup staging (resumen)

1. Blueprint Render → `render.yaml` / branch `develop`.
2. API: `KEYCLOAK_ISSUER_URI`, JWKS, `KEYCLOAK_ADMIN_SERVER_URL`, `CORS_ORIGINS` = URL Vercel.
3. Vercel: Root `frontend`, Vite, branch `develop`, vars `VITE_*`.
4. Keycloak client `inventory-frontend`: Valid redirect URI exacta  
   `https://proyecto-qastaging.vercel.app/*` (+ Web origin igual sin `/*`).  
   El wildcard `*.vercel.app` a menudo **no** basta.

---

## Setup production (paso a paso)

Producción es el **mismo patrón** que staging, con Blueprint y proyecto FE **separados**, branch **`main`**.

### 1. Código en `main`

1. Cuando staging esté estable: PR `develop` → `main` (o merge de release).
2. Confirma que `infra/render/render.prod.yaml` está en `main`.

### 2. Render production (Blueprint nuevo)

1. Dashboard → **New** → **Blueprint**.
2. Mismo repo; **Blueprint path** = `infra/render/render.prod.yaml`.
3. Branch = **`main`**.
4. Servicios esperados: `inventory-prod-db`, `inventory-api-prod`, `inventory-keycloak-prod`.
5. Tras el primer deploy, anota URLs (pueden llevar sufijo si el nombre estaba ocupado), p. ej.:
   - `https://inventory-keycloak-prod.onrender.com`
   - `https://inventory-api-prod.onrender.com`
6. En **Keycloak prod**:
   - `KC_HOSTNAME` = URL pública de Keycloak prod.
   - `JAVA_OPTS_APPEND=-Xms64m -Xmx384m -XX:MaxMetaspaceSize=128m`
   - Sin `JAVA_OPTS_KC_HEAP`.
   - Admin: rellenar `KC_BOOTSTRAP_ADMIN_*` / `KEYCLOAK_ADMIN*` (en prod Blueprint van `sync: false`).
7. En **API prod**:
   - `KEYCLOAK_ISSUER_URI` = `https://<kc-prod>/realms/inventory`
   - `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` =  
     `https://<kc-prod>/realms/inventory/protocol/openid-connect/certs`
   - `KEYCLOAK_ADMIN_SERVER_URL` = URL Keycloak prod
   - `CORS_ORIGINS` = URL del front Vercel **prod** (paso 3)
8. Keycloak Admin → client `inventory-frontend` → redirect URI del dominio Vercel **prod**  
   (`https://<tu-proyecto-prod>.vercel.app/*`).

### 3. Vercel production

1. **New Project** (recomendado: proyecto aparte del de staging) → Root `frontend` → Vite.
2. Production Branch = **`main`**.
3. Environment variables (Production):

| Variable | Valor |
| -------- | ----- |
| `VITE_API_URL` | URL API prod Render |
| `VITE_KEYCLOAK_URL` | URL Keycloak prod Render |
| `VITE_KEYCLOAK_REALM` | `inventory` |
| `VITE_KEYCLOAK_CLIENT_ID` | `inventory-frontend` |

4. Deploy → copiar URL → pegar en `CORS_ORIGINS` de la API prod → redeploy API.

### 4. GitHub (prod)

**Variables:**

| Name | Ejemplo |
| ---- | ------- |
| `PROD_API_URL` | URL API prod |
| `PROD_KEYCLOAK_URL` | URL Keycloak prod |
| `PROD_FRONTEND_URL` | URL Vercel prod |

**Secrets:** `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID_PROD`, hooks Render prod (opcional), credenciales Keycloak para smoke.

Workflow: [`.github/workflows/deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml) (trigger: push a `main` + `workflow_dispatch`; environment GitHub `production`).

### 5. Verificación prod

```bash
export API_URL="$PROD_API_URL"
export KEYCLOAK_URL="$PROD_KEYCLOAK_URL"
export CORS_ORIGIN="$PROD_FRONTEND_URL"
./scripts/post-deploy-smoke.sh
```

Login en el front prod con usuarios demo del realm (`admin` / `admin`, etc.).

---

## Pipelines

| Workflow | Trigger | Qué hace |
| -------- | ------- | -------- |
| [`deploy-staging.yml`](../../../.github/workflows/deploy-staging.yml) | push `develop` + manual | Hook Render + Vercel + smoke cloud |
| [`deploy-prod.yml`](../../../.github/workflows/deploy-prod.yml) | push `main` + manual | Idem prod |
| [`devsecops.yml`](../../../.github/workflows/devsecops.yml) | push/PR `develop` | CI + staging Compose **efímero** (OBS/tests; sin cloud) |

Si faltan secrets Vercel/Render, los jobs **avisan y no fallan**.

---

## Issuer Keycloak (crítico)

El JWT lleva `iss` = URL con la que el navegador habló a Keycloak.  
La API valida `KEYCLOAK_ISSUER_URI` **exactamente igual**.

```
KC_HOSTNAME=https://inventory-keycloak-staging.onrender.com
KEYCLOAK_ISSUER_URI=https://inventory-keycloak-staging.onrender.com/realms/inventory
```

Mismo patrón en prod con las URLs `-prod`.

---

## Grafana (un solo tablero)

| Ambiente | Observabilidad |
| -------- | -------------- |
| Local Compose | Prometheus + Loki + Tempo + **Grafana :3001** |
| Staging / Prod cloud | Sin stack OBS (API puede dejar OTel off / sampling bajo) |

Frase para el profesor: *“Un Grafana central en el entorno de desarrollo/demo; tres Grafanas (dev/staging/prod) no aportan y no son viables en free tier.”*

---

## Verificación rápida (staging)

```bash
curl -sf "$STAGING_API_URL/actuator/health"
API_URL=... KEYCLOAK_URL=... CORS_ORIGIN=https://proyecto-qastaging.vercel.app ./scripts/post-deploy-smoke.sh
```

Login en el front Vercel con `admin` / `admin` (usuarios del realm importado).
