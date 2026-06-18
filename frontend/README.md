# Frontend — Inventario

React + Vite + TypeScript. Login con Keycloak y CRUD de productos.

La documentación completa de instalación, puertos, usuarios demo, pruebas y escenarios reproducibles está en el **[README principal](../README.md)**.

## Comandos frecuentes


| Acción                 | Comando                                                        |
| ---------------------- | -------------------------------------------------------------- |
| Dev local (hot-reload) | `npm run dev` → [http://localhost:5173](http://localhost:5173) |
| Build producción       | `npm run build`                                                |
| Lint                   | `npm run lint`                                                 |
| E2E (Playwright)       | `npm run test:e2e`                                             |
| E2E interactivo        | `npm run test:e2e:ui`                                          |


## Arranque rápido (desarrollo)

Requiere Postgres, Keycloak y API en Docker (ver [README](../README.md#opción-b--desarrollo-local-del-frontend)):

```bash
docker compose up -d postgres keycloak api
cp .env.example .env
npm install
npm run dev
```

## E2E

Los tests E2E usan el frontend en **:3000** (contenedor), no `npm run dev`. Ver [Pruebas E2E](../README.md#tests-e2e-playwright) en el README principal.

## Estructura relevante

```
frontend/
├── src/           # UI: Login, Products, AuthContext
├── e2e/           # Playwright: login.spec.ts, products.spec.ts
├── .env.example   # VITE_API_URL, VITE_KEYCLOAK_*
└── playwright.config.ts
```

