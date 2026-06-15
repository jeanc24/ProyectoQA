# Frontend — Inventario

React + Vite + TypeScript. Login Keycloak y CRUD de productos.

## Requisitos previos

Antes de arrancar el frontend, deben estar activos:

- Postgres + Keycloak (`docker compose up -d` desde la raíz del repo)
- API en http://localhost:8080 (perfil `docker`):

# Windows (PowerShell)
```bash
.\gradlew bootRun --args="--spring.profiles.active=docker"
```
# Linux / macOS
```bash
./gradlew bootRun --args="--spring.profiles.active=docker"
```
## Variables de entorno
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=inventory
VITE_KEYCLOAK_CLIENT_ID=inventory-frontend
VITE_API_URL=http://localhost:8080

## Arranque
```bash
cd frontend
npm install
npm run dev