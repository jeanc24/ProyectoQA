# Frontend — Inventario

React + Vite + TypeScript. Login con Keycloak y CRUD de productos.

## Levantar el stack

Desde la raíz del repo:

```bash
docker compose up --build -d postgres keycloak frontend
```

Frontend: [http://localhost:3000](http://localhost:3000) · Keycloak: `:8081` · Postgres: `:5433`

API (aún fuera de compose):

```powershell
# Windows
$env:CORS_ORIGINS="http://localhost:3000"
.\gradlew bootRun --args="--spring.profiles.active=docker"
```

```bash
# Linux / macOS
CORS_ORIGINS=http://localhost:3000 ./gradlew bootRun --args="--spring.profiles.active=docker"
```

> Tras cambiar código del frontend: `docker compose up --build frontend`

## Desarrollo local

```bash
docker compose up -d postgres keycloak
cd frontend && npm install && npm run dev
```

App en [http://localhost:5173](http://localhost:5173). Variables en `.env.example`.

**Usuarios:** `admin` / `admin` (CRUD) · `viewer` / `viewer` (solo lectura)

## Pruebas E2E (Playwright)

Corren contra Docker en `:3000` (no contra `npm run dev`). Requisitos: stack arriba + API en `:8080`.

```bash
cd frontend
npm install
npx playwright install chromium   # solo la primera vez
npm run test:e2e
```

Otros: `npm run test:e2e:ui` · reporte HTML: `npx playwright test --reporter=html && npx playwright show-report`

Tests: login admin → `/products` · crear producto y verificar fila en tabla.
