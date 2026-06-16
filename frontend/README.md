# Frontend — Inventario

React + Vite + TypeScript. Login con Keycloak y CRUD de productos.

## Docker (producción)

Desde la raíz del repo:

```bash
docker compose up --build
```

Abre http://localhost:3000

Para que funcionen los productos, levanta también la API (mientras no esté en compose):

```powershell
# Windows
$env:CORS_ORIGINS="http://localhost:3000"
.\gradlew bootRun --args="--spring.profiles.active=docker"
```

```bash
# Linux / macOS
CORS_ORIGINS=http://localhost:3000 ./gradlew bootRun --args="--spring.profiles.active=docker"
```

## Desarrollo local

Requisitos: Postgres + Keycloak (`docker compose up -d postgres keycloak`) y API en http://localhost:8080.

Variables (`.env` o `.env.example`):

```env
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=inventory
VITE_KEYCLOAK_CLIENT_ID=inventory-frontend
VITE_API_URL=http://localhost:8080
```

```bash
cd frontend
npm install
npm run dev
```

App en http://localhost:5173

## Usuarios demo

| Usuario | Contraseña |
|---------|------------|
| admin   | admin      |
| viewer  | viewer     |
