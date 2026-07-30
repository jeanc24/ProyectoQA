# Realm Keycloak — `inventory-realm.json`

JSON estricto (Keycloak no admite `//` comentarios). Este README guía los bloques del archivo importado por [`infra/keycloak/Dockerfile`](../infra/keycloak/Dockerfile).

## Mapa del archivo

| Bloque | Qué define |
| ------ | ---------- |
| `realm` / flags | Nombre `inventory`, SSL off en demo, sin self-registration |
| `roles.client.inventory-api` | Los **7 permisos**: `product:view/manage`, `stock:view/manage`, `report:view`, `audit:view`, `user:manage` |
| `clients` → `inventory-api` | Client confidencial (secret) — password grant / API |
| `clients` → `inventory-frontend` | Client público + PKCE + `redirectUris` / `webOrigins` (local, staging Vite/Vercel) |
| `users` | Demo: `admin`, `viewer`, `stock-manager`, `auditor` (password = username) con sus `clientRoles` |

## Cadena con el resto del código

```
inventory-realm.json
        │
        ├─► Dockerfile Keycloak (--import-realm)
        │
        ├─► JWT resource_access.inventory-api.roles
        │         └─► DockerSecurityConfig.extractAuthorities
        │                   └─► @PreAuthorize("hasAuthority('…')")
        │
        └─► Frontend
                  keycloak.ts (client inventory-frontend)
                  AuthContext.hasRole(..., "inventory-api")
                  permissions.ts (mismos strings)
```

## Usuarios demo (resumen)

| Usuario | Permisos en `inventory-api` |
| ------- | --------------------------- |
| `admin` | todos |
| `viewer` | `product:view`, `stock:view` |
| `stock-manager` | view productos + view/manage stock |
| `auditor` | view productos/stock + `audit:view` |
