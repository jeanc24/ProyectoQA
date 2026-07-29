# Guía de defensa — ProyectoQA

Todo lo que hace el proyecto, **dónde está en el código** y **por qué se hizo así**.

| Documento | Contenido |
|---|---|
| **Este archivo** | Explicación completa: arquitectura, flujos, seguridad, datos, frontend, ambientes, testing, CI/CD, observabilidad |
| [`ARBOL.md`](ARBOL.md) | Árbol de carpetas comentado con la herramienta usada en cada archivo |
| [`PREGUNTAS.md`](PREGUNTAS.md) | Banco de 120 preguntas con respuesta, código y archivo donde demostrarla: las 58 del avance + 62 nuevas |

---

## Índice

1. [Chuleta de arranque](#1-chuleta-de-arranque)
2. [Qué hace la aplicación](#2-qué-hace-la-aplicación)
3. [Arquitectura](#3-arquitectura)
4. [El flujo completo de una petición](#4-el-flujo-completo-de-una-petición)
5. [Seguridad: Keycloak + Spring Security](#5-seguridad-keycloak--spring-security)
6. [Base de datos y persistencia](#6-base-de-datos-y-persistencia)
7. [Frontend](#7-frontend)
8. [Manejo de errores](#8-manejo-de-errores)
9. [Docker y los tres ambientes](#9-docker-y-los-tres-ambientes)
10. [Testing](#10-testing)
11. [CI/CD](#11-cicd)
12. [Observabilidad](#12-observabilidad)
13. [Puntos débiles y cómo responderlos](#13-puntos-débiles-y-cómo-responderlos)
14. [Guion de demo](#14-guion-de-demo)

---

## 1. Chuleta de arranque

### Levantar todo

```bash
cd ~/Developer/ProyectoQA
docker compose up -d --build
curl -sf http://localhost:8080/actuator/health   # espera {"status":"UP"}
```

### URLs

| Servicio | URL | Credenciales |
|---|---|---|
| Frontend | http://localhost:3000 | `admin` / `admin` |
| API + Swagger | http://localhost:8080/swagger-ui.html | Bearer JWT |
| Keycloak | http://localhost:8081 | consola: `admin` / `admin` |
| Grafana | http://localhost:3001 | `admin` / `admin` |
| Prometheus | http://localhost:9090 | — |
| Jenkins | http://localhost:8082 | tu usuario local |
| PostgreSQL | `localhost:5433` | `inventory` / `inventory` |

### Usuarios demo

| Usuario | Contraseña | Permisos |
|---|---|---|
| `admin` | `admin` | los 7 |
| `viewer` | `viewer` | `product:view`, `stock:view` |
| `stock-manager` | `stock-manager` | `product:view`, `stock:view`, `stock:manage` |

### Comandos de pruebas

```bash
./gradlew test              # 36 unitarios
./gradlew apiTest           # 31 escenarios de API
./gradlew contractTest      # 2 de contrato
./gradlew integrationTest   # 26 de integración (Testcontainers, necesita Docker)
./gradlew jacocoTestReport  # cobertura → build/reports/jacoco/test/html/index.html

cd frontend && npm run test:e2e     # 9 E2E Playwright

./scripts/security-smoke.sh         # JWT / CORS / permisos
./scripts/k6-run.sh load            # carga
./scripts/zap-baseline.sh           # DAST
```

### Token por consola (útil para demostrar la API en vivo)

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8081/realms/inventory/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=inventory-api \
  -d client_secret=inventory-api-secret \
  -d username=viewer -d password=viewer | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/products?page=0&size=5"
```

---

## 2. Qué hace la aplicación

**Sistema de gestión de inventarios.** Tres dominios:

| Dominio | Qué resuelve | Endpoints |
|---|---|---|
| **Productos** | CRUD con SKU único, categoría, precio, stock mínimo | `/api/v1/products`, `/api/v1/categories` |
| **Stock** | Entradas, salidas y ajustes con historial inmutable | `/api/v1/stock/movements`, `/api/v1/products/{id}/stock/history` |
| **Reportes / Auditoría** | KPIs del dashboard y trazabilidad de cambios | `/api/v1/reports/*`, `/api/v1/audit/products/{id}` |

La regla de negocio central está en [`StockService.calculateQuantityAfter`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/StockService.java):

```java
private int calculateQuantityAfter(MovementType type, int before, int quantity) {
    return switch (type) {
        case IN -> {
            if (quantity < 1) throw new IllegalArgumentException("IN movement quantity must be at least 1");
            yield before + quantity;
        }
        case OUT -> {
            if (quantity < 1) throw new IllegalArgumentException("OUT movement quantity must be at least 1");
            if (quantity > before) throw new InsufficientStockException(
                    "Insufficient stock: requested " + quantity + ", available " + before);
            yield before - quantity;
        }
        case ADJUSTMENT -> {
            if (quantity < 0) throw new IllegalArgumentException("ADJUSTMENT quantity cannot be negative");
            yield quantity;
        }
    };
}
```

Es un `switch` de expresión de Java 21 sobre un enum: el compilador obliga a cubrir los tres casos. Si mañana se agrega `RETURN`, el código no compila hasta manejarlo.

---

## 3. Arquitectura

### Estilo

**Monolito modular por capas**, desplegado como **dos artefactos** (API + SPA) más servicios de apoyo.

```
┌───────────────────────────────────────────────────────────┐
│ Navegador                                                 │
│  React SPA (nginx :3000)  ──►  keycloak-js (PKCE)         │
└──────────────┬────────────────────────────────────────────┘
               │ HTTPS/HTTP + Authorization: Bearer <JWT>
               ▼
┌───────────────────────────────────────────────────────────┐
│ Spring Boot API (:8080)                                   │
│  Controller  →  Service  →  Repository  →  JPA/Hibernate  │
│       ▲                                                    │
│  Security Filter Chain (valida JWT contra JWKS)           │
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

### Capas y responsabilidades

| Capa | Paquete | Responsabilidad | Qué **no** hace |
|---|---|---|---|
| **Controller** | `controller` | HTTP, códigos de estado, permisos, documentación OpenAPI | Lógica de negocio, SQL |
| **Service** | `application.service` | Reglas de negocio, transacciones, mapeo entidad→DTO | Conocer HTTP |
| **Repository** | `domain.repository` | Acceso a datos | Reglas de negocio |
| **Entity** | `domain.entity` | Modelo persistente + invariantes simples | Salir hacia el cliente |
| **DTO** | `application.dto` | Contrato de entrada/salida | Tener lógica |

**Por qué:** cada capa se prueba por separado. `ProductServiceTest` mockea el repositorio y no necesita base de datos; `ProductControllerTest` usa MockMvc y no necesita servicio real; los tests de integración usan Postgres real. Si todo estuviera en el controlador, solo se podría probar end-to-end.

### Granularidad

Fina en permisos, gruesa en despliegue.

- **7 permisos** en vez de 1 rol "administrador": `product:view`, `product:manage`, `stock:view`, `stock:manage`, `report:view`, `audit:view`, `user:manage`.
- **1 servicio desplegable** (la API), no microservicios.

**Por qué así:** con permisos granulares se pueden crear perfiles nuevos sin tocar código (ver [pregunta 28](PREGUNTAS.md)). Con microservicios habría que resolver comunicación, transacciones distribuidas y despliegue independiente, que no aporta nada a un proyecto de un dominio acotado y sí complica la evaluación de calidad.

### Patrones de Spring presentes

| Patrón | Dónde | Evidencia |
|---|---|---|
| Inyección por constructor | Todos los servicios y controladores | `ProductService(ProductRepository, CategoryRepository)` — sin `@Autowired` en campos, lo que permite instanciar en tests |
| Repository | `domain/repository` | Interfaces que extienden `JpaRepository` |
| DTO / Assembler | `application.dto` + métodos `toResponse()` | La entidad nunca se serializa directamente |
| Specification (Criteria) | `StockMovementRepository.withFilters` | Filtros combinables sin concatenar SQL |
| Advice global | `GlobalExceptionHandler` | Un solo lugar traduce excepciones a HTTP |
| Configuración por perfil | `@Profile("local")`, `@Profile({"docker","staging","prod"})`, `@Profile("!prod")` | Misma imagen, distinto comportamiento |
| Template Method en tests | `AbstractIntegrationTest` → `AbstractKeycloakIntegrationTest` | Reutiliza contenedores |

---

## 4. El flujo completo de una petición

Caso: **el usuario `viewer` abre la pantalla de productos.**

### Paso 1 — El navegador ya tiene sesión

[`AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx) inicializa keycloak-js:

```tsx
keycloak.init({
  onLoad: "check-sso",
  pkceMethod: "S256",
})
```

`check-sso` pregunta a Keycloak si ya hay sesión sin forzar login. `S256` es PKCE, obligatorio para clientes públicos.

### Paso 2 — React pide los datos

[`Products.tsx`](../../../frontend/src/pages/Products.tsx) llama a `listProducts` con el estado de filtros, orden y página:

```tsx
const pageResult = await listProducts({
  page, size,
  name: searchField === "name" && appliedSearch ? appliedSearch : undefined,
  sku:  searchField === "sku"  && appliedSearch ? appliedSearch : undefined,
  categoryId: categoryId === "" ? null : categoryId,
  active: activeFilter === "all" ? null : activeFilter === "true",
  sort: `${sortField},${sortDir}`,
});
```

### Paso 3 — El cliente HTTP añade el token

Todo pasa por [`client.ts`](../../../frontend/src/api/client.ts). **Este es "el cliente HTTP" que el profesor pide ver:**

```ts
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (keycloak.authenticated) {
    await keycloak.updateToken(30);          // renueva solo si vence en <30 s
  }
  const headers: Record<string, string> = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(options.headers as Record<string, string>),
  };
  if (keycloak.token) {
    headers.Authorization = `Bearer ${keycloak.token}`;
  }
  const response = await fetch(`${import.meta.env.VITE_API_URL}${path}`, { ...options, headers });
  ...
}
```

No se pide un token nuevo en cada request: se reutiliza el que hay en memoria y solo se renueva cerca del vencimiento.

### Paso 4 — La API valida el JWT

La petición entra a la cadena de [`DockerSecurityConfig`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java):

```java
http
    .csrf(csrf -> csrf.disable())
    .cors(Customizer.withDefaults())
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
            .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
            .anyRequest().authenticated())
    .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
```

Spring verifica **localmente** la firma con la clave pública de Keycloak (`jwk-set-uri`). No llama a Keycloak en cada petición.

### Paso 5 — Los roles del token se convierten en authorities

```java
private Collection<GrantedAuthority> extractAuthorities(Jwt jwt, String clientId) {
    Set<String> roles = new LinkedHashSet<>();

    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess != null) { /* añade realm_access.roles */ }

    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
    if (resourceAccess != null) {
        Object clientAccess = resourceAccess.get(clientId);   // "inventory-api"
        if (clientAccess instanceof Map<?, ?> clientMap) { /* añade sus roles */ }
    }
    return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
}
```

Aquí es donde `resource_access."inventory-api".roles = ["product:view","stock:view"]` se convierte en authorities de Spring.

### Paso 6 — El endpoint comprueba el permiso

[`ProductController`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java):

```java
@GetMapping
@PreAuthorize("hasAuthority('product:view')")
public PageResponse<ProductResponse> list(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String sku,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Boolean active,
        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
    return productService.findAll(name, sku, categoryId, active, pageable);
}
```

`@PreAuthorize` funciona porque `DockerSecurityConfig` está anotada con `@EnableMethodSecurity`.

### Paso 7 — Servicio y repositorio

[`ProductService.findAll`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java) convierte los filtros a patrones `LIKE` y delega:

```java
Page<ProductResponse> page = productRepository.findFiltered(
        toLikePattern(name), toLikePattern(sku), categoryId, active, pageable
).map(this::toResponse);

return PageResponse.from(page);
```

El repositorio usa SQL nativo con parámetros nombrados ([`ProductRepository`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/ProductRepository.java)):

```sql
SELECT p.* FROM products p
WHERE (CAST(:namePattern AS TEXT) IS NULL OR p.name ILIKE CAST(:namePattern AS TEXT))
  AND (CAST(:skuPattern  AS TEXT) IS NULL OR p.sku  ILIKE CAST(:skuPattern  AS TEXT))
  AND (CAST(:categoryId  AS BIGINT) IS NULL OR p.category_id = :categoryId)
  AND (CAST(:active      AS BOOLEAN) IS NULL OR p.active = :active)
```

El patrón `(:param IS NULL OR columna = :param)` permite que un mismo query sirva para cualquier combinación de filtros. Los `CAST` son necesarios porque PostgreSQL no puede inferir el tipo de un parámetro que solo aparece comparado con `NULL`.

### Paso 8 — La respuesta vuelve

`PageResponse` normaliza la paginación para que el frontend no dependa del formato interno de Spring:

```java
public record PageResponse<T>(
        List<T> content, int page, int size,
        long totalElements, int totalPages,
        boolean first, boolean last) { ... }
```

### Paso 9 — React pinta la tabla

```tsx
{products.map((product) => (
  <tr key={product.id}
      className={product.belowMinStock ? "row-low-stock" : undefined}
      data-low-stock={product.belowMinStock ? "true" : "false"}>
    <td>{product.id}</td>
    <td>{product.name}</td>
    ...
```

`belowMinStock` **no está en la base de datos**: lo calcula la entidad y se expone en el DTO.

```java
/** True si la cantidad actual está en o por debajo del stock mínimo. */
public boolean isBelowMinStock() {
    return quantity <= minStock;
}
```

### Resumen del recorrido

```
Products.tsx → api/products.ts → api/client.ts (+ Bearer)
      ↓ HTTP
SecurityFilterChain (firma JWT) → JwtAuthenticationConverter (roles → authorities)
      ↓
@PreAuthorize('product:view') → ProductController.list
      ↓
ProductService.findAll → ProductRepository.findFiltered → PostgreSQL
      ↓
Page<Product> → map(toResponse) → PageResponse<ProductResponse> → JSON → tabla
```

---

## 5. Seguridad: Keycloak + Spring Security

### 5.1 Qué es cada cosa

| Pieza | Rol |
|---|---|
| **Keycloak** | Servidor de identidad. Guarda usuarios, autentica y **firma** los JWT |
| **Spring Security (Resource Server)** | **Verifica** los JWT y aplica autorización |
| **keycloak-js** | Adaptador del navegador: login, almacenamiento y renovación del token |

El proyecto **no guarda usuarios ni contraseñas en PostgreSQL**. No existe tabla `users`. Eso está en Keycloak, ver [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql).

### 5.2 El realm como código

[`keycloak/inventory-realm.json`](../../../keycloak/inventory-realm.json) define todo y se importa automáticamente (`start-dev --import-realm`).

**Dos clientes, con propósitos distintos:**

```json
{
  "clientId": "inventory-api",
  "publicClient": false,
  "secret": "inventory-api-secret",
  "directAccessGrantsEnabled": true
},
{
  "clientId": "inventory-frontend",
  "publicClient": true,
  "standardFlowEnabled": true,
  "redirectUris": ["http://localhost:5173/*", "http://localhost:3000/*",
                   "http://localhost:3008/*", "http://localhost:3009/*"],
  "attributes": { "pkce.code.challenge.method": "S256" }
}
```

| Cliente | Tipo | Quién lo usa | Flujo |
|---|---|---|---|
| `inventory-frontend` | público | El navegador | Authorization Code + PKCE |
| `inventory-api` | confidencial | Tests, scripts, k6 | Password grant (solo para automatizar) |

**Por qué dos:** un navegador no puede guardar un secreto (el JS es visible), por eso el cliente del frontend es público y usa PKCE. Los scripts sí pueden guardar el secreto, y el password grant les evita simular un navegador.

**Los 7 permisos** son *client roles* de `inventory-api`, y los usuarios se crean con ellos asignados:

```json
"users": [
  { "username": "admin",  "clientRoles": { "inventory-api": ["product:view","product:manage","stock:view","stock:manage","report:view","audit:view","user:manage"] } },
  { "username": "viewer", "clientRoles": { "inventory-api": ["product:view","stock:view"] } },
  { "username": "stock-manager", "clientRoles": { "inventory-api": ["product:view","stock:view","stock:manage"] } }
]
```

### 5.3 Flujo de login completo

```
1. Usuario en http://localhost:3000/  → clic "Iniciar sesión"
2. AuthContext.login() → keycloak.login({ redirectUri: origin + "/management" })
3. Redirección a Keycloak con code_challenge (PKCE S256)
4. Usuario escribe usuario/contraseña EN Keycloak (la app nunca ve la contraseña)
5. Keycloak redirige de vuelta con ?code=...
6. keycloak-js intercambia code + code_verifier por access_token + refresh_token
7. React entra a /management → ProtectedRoute → redirige a /products
8. Cada llamada API lleva Authorization: Bearer <access_token>
9. Al vencer: keycloak.onTokenExpired → updateToken(30); si falla → logout
```

Puntos 2, 8 y 9 en código:

```tsx
// AuthContext.tsx
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => {
    keycloak.logout({ redirectUri: window.location.origin + "/" });
  });
};

const login = useCallback(() => {
  keycloak.login({ redirectUri: window.location.origin + "/management" });
}, []);
```

### 5.4 Autorización en dos niveles

**Nivel 1 — UI (comodidad).** [`App.tsx`](../../../frontend/src/App.tsx):

```tsx
<Route path="/dashboard" element={
  <ProtectedRoute requiredRole={PERMISSIONS.reportView}>
    <Dashboard />
  </ProtectedRoute>
} />
```

[`ProtectedRoute.tsx`](../../../frontend/src/components/ProtectedRoute.tsx):

```tsx
if (!isAuthenticated) return <Navigate to="/" replace />;
if (requiredRole && !hasRole(requiredRole)) return <Navigate to="/unauthorized" replace />;
return children;
```

`hasRole` lee el token, no una lista local:

```tsx
const hasRole = useCallback((role: string) => {
  return keycloak.hasResourceRole(role, API_CLIENT_ID);   // "inventory-api"
}, []);
```

**Nivel 2 — API (la que de verdad protege).** `@PreAuthorize` en cada endpoint.

| Endpoint | Permiso |
|---|---|
| `GET /api/v1/products` | `product:view` |
| `POST/PUT/DELETE /api/v1/products` | `product:manage` |
| `GET /api/v1/categories` | `product:view` |
| `GET /api/v1/stock/movements` | `stock:view` |
| `POST /api/v1/stock/movements` | `stock:manage` |
| `GET /api/v1/products/{id}/stock/history` | `stock:view` |
| `GET /api/v1/reports/*` | `report:view` |
| `GET /api/v1/audit/products/{id}` | `audit:view` |

**Por qué dos niveles:** ocultar un botón no es seguridad; cualquiera puede llamar la API con curl. La UI evita frustración, la API evita el acceso. El test [`permissions.spec.ts`](../../../frontend/e2e/permissions.spec.ts) demuestra exactamente eso: verifica el 403 de la API *además* de que el botón no aparezca.

### 5.5 Stateless

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

El servidor no guarda sesión. Cada petición se autentica sola con su JWT. Consecuencias:

- Se puede escalar horizontalmente sin sesiones pegajosas.
- No hay cookie de sesión, por eso **CSRF no aplica** y se desactiva explícitamente con el comentario `// NOSONAR` justificándolo.
- Cerrar sesión es cosa del cliente y de Keycloak; el token vive hasta expirar.

### 5.6 Quién ejecutó el movimiento

El backend no confía en un campo del body: toma el usuario del token.

```java
// StockController
public StockMovementResponse create(@Valid @RequestBody StockMovementRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
    return stockService.registerMovement(request, resolveUsername(jwt));
}

static String resolveUsername(Jwt jwt) {
    if (jwt == null) return "system";
    String preferred = jwt.getClaimAsString("preferred_username");
    return preferred != null && !preferred.isBlank() ? preferred : jwt.getSubject();
}
```

Ese valor se guarda en `stock_movements.performed_by`, que es `NOT NULL` en el esquema.

### 5.7 CORS

[`CorsConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/CorsConfig.java) toma los orígenes de configuración, nunca `*`:

```java
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
config.setAllowCredentials(true);
```

Y [`security-smoke.sh`](../../../scripts/security-smoke.sh) lo verifica con un origen bueno y uno malo:

```bash
CORS_BAD_ORIGIN_HDR=$(curl -s -D - -o /dev/null -X OPTIONS "$API/api/v1/products" \
  -H "Origin: http://evil.example" \
  -H "Access-Control-Request-Method: GET" | ... )
# se espera vacío
```

---

## 6. Base de datos y persistencia

### 6.1 Esquema

Definido por Flyway en [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql).

```
categories                products                       stock_movements
──────────                ────────                       ───────────────
id        BIGSERIAL PK    id          BIGSERIAL PK       id              BIGSERIAL PK
name      UNIQUE          name        NOT NULL           product_id      FK → products
description               sku         UNIQUE NOT NULL    movement_type   CHECK (IN|OUT|ADJUSTMENT)
                          description TEXT               quantity_before NOT NULL
      ▲                   category_id FK → categories    quantity_after  NOT NULL
      └───────────────────price       CHECK >= 0         quantity_delta  NOT NULL
                          quantity    CHECK >= 0         notes           TEXT
                          min_stock   CHECK >= 0         performed_by    NOT NULL
                          active      DEFAULT TRUE       created_at      TIMESTAMPTZ
                          created_at / updated_at                ▲
                                ▲                                │
                                └────────────────────────────────┘

revinfo (Envers)          products_audit (Envers)
────────                  ──────────────
rev      PK               id, rev  PK compuesta
revtstmp                  revtype (0=alta, 1=mod, 2=baja)
                          + copia de todas las columnas de products
```

Índices creados: `idx_products_name`, `idx_products_sku`, `idx_products_category`, `idx_products_active`, `idx_stock_movements_product`, `idx_stock_movements_created` (este último `DESC`, porque el historial siempre se lee por fecha descendente).

**Integridad en tres niveles:**

| Nivel | Ejemplo |
|---|---|
| Bean Validation (DTO) | `@NotBlank @Size(max = 50) String sku` |
| Regla de negocio (servicio) | `if (productRepository.existsBySku(sku)) throw new DuplicateSkuException(...)` |
| Constraint de base de datos | `sku VARCHAR(50) NOT NULL UNIQUE`, `CHECK (quantity >= 0)` |

**Por qué los tres:** la validación da mensajes útiles al usuario, la regla de negocio da el código HTTP correcto (409), y el constraint garantiza que ni un bug ni un script suelto rompan los datos. [`DataIntegrityIntegrationTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/DataIntegrityIntegrationTest.java) prueba el tercer nivel contra Postgres real.

### 6.2 Flyway y `ddl-auto: validate`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Hibernate **no** crea ni modifica tablas: solo valida que las entidades coincidan con el esquema. Si alguien agrega un campo a `Product` y olvida la migración, la aplicación **no arranca**. Eso convierte un bug silencioso en un fallo inmediato y visible en CI.

Las tres migraciones:

| Versión | Qué hace | Por qué |
|---|---|---|
| `V1` | Todo el esquema + tablas de Envers | Base |
| `V2` | `CREATE SEQUENCE revinfo_seq` | Envers necesita una secuencia para numerar revisiones |
| `V3` | `ALTER SEQUENCE revinfo_seq INCREMENT BY 50` | Hibernate reserva bloques de 50 ids; sin esto habría colisiones |

### 6.3 Paginación

**Mecanismo: offset/page**, el estándar de Spring Data.

```java
@GetMapping
public PageResponse<ProductResponse> list(..., @ParameterObject @PageableDefault(size = 20) Pageable pageable)
```

Spring construye el `Pageable` desde `?page=0&size=20&sort=name,asc` y Hibernate lo traduce a `LIMIT/OFFSET`. El frontend envía esos parámetros en [`products.ts`](../../../frontend/src/api/products.ts):

```ts
q.set("page", String(params.page ?? 0));
q.set("size", String(params.size ?? 20));
if (params.sort?.trim()) q.set("sort", params.sort.trim());
```

**Por qué offset y no cursor:** el usuario necesita saltar a una página concreta y ver "página 3 de 12"; con cursor solo hay "siguiente/anterior". El costo de offset aparece con cientos de miles de filas, que no es el caso. La respuesta honesta si preguntan por escala está en [P18](PREGUNTAS.md#p18-qué-mecanismo-de-paginación-se-utiliza).

En reportes se limita de otra forma, porque son listas cortas:

```java
public List<ProductResponse> lowStock(int limit) {
    int size = Math.max(1, Math.min(limit, 100));   // techo defensivo
    ...
}
```

Sin ese `Math.min` alguien podría pedir `?limit=999999` y tumbar la memoria.

### 6.4 Auditoría con Envers

`Product` está anotada con `@Audited`:

```java
@Entity
@Table(name = "products")
@Audited
public class Product {
    ...
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Category category;
```

Cada `INSERT`/`UPDATE`/`DELETE` genera automáticamente una fila en `products_audit`. `NOT_AUDITED` en la categoría evita auditar también el catálogo de categorías, que no cambia.

[`AuditService`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) lee ese historial con la API de Envers:

```java
List<Object[]> rows = auditReader.createQuery()
        .forRevisionsOfEntity(Product.class, false, true)
        .add(AuditEntity.id().eq(productId))
        .addOrder(AuditEntity.revisionNumber().asc())
        .getResultList();
```

Se ve en la UI con el botón **Historial** en la tabla de productos (requiere `audit:view`).

### 6.5 Dos estilos de consulta y por qué

| Repositorio | Técnica | Motivo |
|---|---|---|
| `ProductRepository.findFiltered` | SQL nativo | Usa `ILIKE`, específico de PostgreSQL, para búsqueda sin distinguir mayúsculas |
| `ProductRepository.countLowStock`, `sumInventoryValue` | JPQL | Agregados portables |
| `StockMovementRepository.withFilters` | Criteria API (Specification) | Filtros opcionales combinables construidos en Java, sin concatenar strings |

Ninguno concatena parámetros en la cadena SQL: todos usan `:parametros` nombrados, lo que elimina la inyección SQL.

---

## 7. Frontend

### 7.1 Stack y estructura

React 19 + TypeScript + Vite, empaquetado por nginx. Sin Redux ni React Query: estado local con hooks y un contexto para autenticación.

**Por qué sin librería de estado:** cada pantalla consume sus propios endpoints y no comparte datos con las demás. Añadir Redux sería complejidad sin beneficio.

### 7.2 Rutas y protección

[`App.tsx`](../../../frontend/src/App.tsx):

| Ruta | Acceso |
|---|---|
| `/`, `/login` | Pública |
| `/guide`, `/guide/:techId` | Pública (guía del stack) |
| `/management` | Autenticado → redirige a `/products` |
| `/products` | `product:view` |
| `/stock` | `stock:view` |
| `/dashboard` | `report:view` |
| `/unauthorized` | Pantalla 403 |

### 7.3 El cliente HTTP

Un solo archivo concentra token, cabeceras y errores: [`client.ts`](../../../frontend/src/api/client.ts). Los módulos `products.ts`, `stock.ts`, `reports.ts`, `audit.ts`, `categories.ts` solo declaran rutas y tipos.

```ts
export class ApiError extends Error {
  status: number;
  fieldErrors?: { field: string; message: string }[];
}
```

```ts
if (response.status === 204) return undefined as T;   // DELETE sin cuerpo

if (!response.ok) {
  const body = (await response.json().catch(() => null)) as ErrorResponse | null;
  throw new ApiError(response.status, body?.message ?? response.statusText, body?.fieldErrors);
}
```

**Por qué centralizarlo:** si mañana hay que añadir reintentos, un header de correlación o un interceptor de 401, se toca un archivo, no veinte.

### 7.4 Cómo se muestran los errores del backend

`Products.tsx` traduce el `ApiError` a estado de UI:

```tsx
function handleApiError(err: unknown) {
  if (err instanceof ApiError) {
    setError(err.message || `No se pudo completar la operación (${err.status})`);
    setFieldErrors(err.fieldErrors ?? []);
    return;
  }
  setError("Error inesperado");
}
```

Y los pinta con `role="alert"` (accesible para lectores de pantalla):

```tsx
{error && (
  <div className="alert alert-error" role="alert">
    {error}
    {fieldErrors.length > 0 && (
      <ul>{fieldErrors.map((fe) => <li key={fe.field}>{fe.field}: {fe.message}</li>)}</ul>
    )}
  </div>
)}
```

Así, un 409 por SKU duplicado o un 400 con errores de campo llegan al usuario con el detalle exacto que produjo el backend.

### 7.5 Dashboard

Cuatro llamadas en paralelo, no en cadena:

```tsx
const [s, low, recent, top] = await Promise.all([
  getInventorySummary(),
  getLowStock(20),
  getRecentMovements(15),
  getTopProducts(10),
]);
```

Cuatro peticiones secuenciales tardarían la suma de las latencias; en paralelo tardan lo que la más lenta.

### 7.6 Variables de entorno en build, no en runtime

[`frontend/Dockerfile`](../../../frontend/Dockerfile):

```dockerfile
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build
```

Vite **incrusta** las variables en el bundle al compilar. Por eso cada ambiente construye su propia imagen de frontend con sus URLs: dev apunta a `:8080/:8081`, staging a `:8088/:8181`, prod a `:8089/:8182`.

### 7.7 nginx y el SPA

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

Sin esa línea, recargar en `/products` daría 404: nginx buscaría un archivo llamado `products`. Con ella, devuelve `index.html` y React Router resuelve la ruta en el navegador.

---

## 8. Manejo de errores

Un único `@RestControllerAdvice` traduce excepciones a HTTP: [`GlobalExceptionHandler`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/GlobalExceptionHandler.java).

| Excepción | HTTP | Cuándo |
|---|---|---|
| `ResourceNotFoundException` | 404 | Producto o categoría inexistente |
| `DuplicateSkuException` | 409 | SKU ya usado |
| `InsufficientStockException` | 400 | OUT mayor que las existencias |
| `IllegalArgumentException` | 400 | Cantidad inválida en IN/OUT/ADJUSTMENT |
| `MethodArgumentNotValidException` | 400 + `fieldErrors` | Bean Validation |
| `DataIntegrityViolationException` | 409 | Constraint de la base de datos |

Los errores de validación se desglosan campo por campo:

```java
var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
        .toList();
```

Formato de respuesta ([`ErrorResponse`](../../../src/main/java/icc354/pucmm/proyectoqa/dto/ErrorResponse.java)):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-07-28T19:00:00Z",
  "fieldErrors": [{ "field": "sku", "message": "must not be blank" }]
}
```

**Los 401 y 403 no pasan por aquí**: los produce la cadena de filtros de Spring Security *antes* de llegar al controlador. Por eso su cuerpo es el estándar de Spring.

**Por qué centralizado:** ningún controlador tiene `try/catch`. Se lanza la excepción de dominio y el advice decide el código. Se prueba en [`GlobalExceptionHandlerTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/GlobalExceptionHandlerTest.java).

---

## 9. Docker y los tres ambientes

### 9.1 Dos conceptos distintos

**Perfil de Spring** = un interruptor de la API. Selecciona `application-<perfil>.yml` y los beans con `@Profile`.

**Ambiente** = el stack completo: Compose + `.env` + puertos + Keycloak + frontend + observabilidad. El perfil es una pieza del ambiente.

### 9.2 Tabla comparativa

| | Desarrollo | Staging | Production-like |
|---|---|---|---|
| Compose | [`docker-compose.yml`](../../../docker-compose.yml) | [`docker-compose.staging.yml`](../../../docker-compose.staging.yml) | [`docker-compose.prod.yml`](../../../docker-compose.prod.yml) |
| Perfil Spring | `docker` | `staging` | `prod` |
| Env file | `.env` | `.env.staging` | `.env.production` |
| Frontend | 3000 | 3008 | 3009 |
| API | 8080 | 8088 | 8089 |
| Keycloak | 8081 | 8181 | 8182 |
| Postgres | 5433 | 5434 | 5435 (solo `127.0.0.1`) |
| Grafana | 3001 | 3011 | 3012 (solo `127.0.0.1`) |
| Swagger | sí | sí | **no** |
| Actuator | health, info, prometheus, metrics | igual | health + prometheus |
| Logs | INFO | INFO | root WARN, app INFO |
| Muestreo de trazas | 100 % | 100 % | 10 % |
| Secretos en el YAML | valores por defecto cómodos | solo `${VARIABLE}` | solo `${VARIABLE}` |

Existe un cuarto stack, [`docker-compose.security.yml`](../../../docker-compose.security.yml), en puertos 8090/8091/5435, sin `container_name` ni bind mounts, que usa Jenkins para ZAP y el smoke de seguridad sin chocar con los demás.

### 9.3 Endurecimiento de producción

[`application-prod.yml`](../../../src/main/resources/application-prod.yml):

```yaml
springdoc:
  api-docs:    { enabled: false }
  swagger-ui:  { enabled: false }

management:
  endpoints:
    web:
      exposure:
        include: ${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:health,prometheus}
  endpoint:
    health:
      show-details: never
      probes: { enabled: true }
```

Y `DockerSecurityConfig` reacciona: si Swagger está apagado, sus rutas **no** entran en la lista pública.

```java
List<String> publicPaths = new ArrayList<>(List.of(
        "/actuator/health", "/actuator/health/**", "/actuator/prometheus"));
if (swaggerUiEnabled) {
    publicPaths.addAll(List.of("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**"));
}
```

El valor viene inyectado con `@Value("${springdoc.swagger-ui.enabled:true}")`, así que la configuración y la seguridad nunca se desincronizan.

En prod además se apaga el bean de OpenAPI completo: `@Profile("!prod")` en [`OpenApiConfig`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java).

### 9.4 El Dockerfile del backend

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar -x test --no-daemon \
  && BOOT_JAR="$(ls build/libs/*.jar | grep -v 'plain' | head -n 1)" \
  && cp "$BOOT_JAR" /app/application.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/application.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Cuatro decisiones que explicar:

1. **Multi-stage**: la imagen final lleva JRE, no JDK ni Gradle ni el código fuente. Menos tamaño y menos superficie de ataque.
2. **`-x test`**: los tests corren en el pipeline, no dentro del build de la imagen; si no, cada build de Docker tardaría el triple.
3. **`grep -v plain`**: Gradle puede generar dos JAR; copiar `*.jar` a ciegas produce un JAR corrupto. Por eso también `tasks.named('jar') { enabled = false }` en `build.gradle`.
4. **HEALTHCHECK**: es lo que permite que otros servicios esperen con `depends_on: condition: service_healthy`.

### 9.5 Orden de arranque

```yaml
api:
  depends_on:
    postgres:  { condition: service_healthy }
    keycloak:  { condition: service_started }
    alloy:     { condition: service_started }
```

`service_healthy` espera al healthcheck real de Postgres (`pg_isready`), no solo a que el contenedor exista.

### 9.6 El detalle del issuer

En el compose de desarrollo:

```yaml
KEYCLOAK_ISSUER_URI: http://localhost:8081/realms/inventory
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: http://keycloak:8080/realms/inventory/protocol/openid-connect/certs
```

Son **dos URLs distintas a propósito**:

- El **issuer** debe coincidir carácter por carácter con el claim `iss` del token, y el token lo pidió el navegador a `localhost:8081`.
- El **JWKS** lo descarga la API desde dentro de la red Docker, donde `localhost` sería el propio contenedor; ahí el nombre correcto es `keycloak:8080`.

En staging se refuerza con `KC_HOSTNAME` fijo, y el comentario del compose lo explica:

```yaml
# Issuer fijo = KEYCLOAK_ISSUER_URI (localhost:8181). Sin esto, tokens
# pedidos desde Jenkins (host.docker.internal:8181) llevan iss distinto → 401.
KC_HOSTNAME: ${KEYCLOAK_HOSTNAME:-http://localhost:8181}
```

Esta es una de las mejores anécdotas técnicas para contar en la defensa: un 401 que no era de permisos sino de nombres de host.

---

## 10. Testing

### 10.1 Panorama

| Capa | Herramienta | Cantidad | Comando |
|---|---|---|---|
| Unitarias | JUnit 5 + Mockito + MockMvc | 36 | `./gradlew test` |
| Escenarios de API | MockMvc + `@WithMockUser` | 31 | `./gradlew apiTest` |
| Contrato | JUnit + OpenAPI | 2 | `./gradlew contractTest` |
| Integración | Testcontainers (Postgres ± Keycloak) | 26 | `./gradlew integrationTest` |
| E2E | Playwright | 9 | `npm run test:e2e` |
| Carga y estrés | k6 | 2 scripts | `./scripts/k6-run.sh all` |
| DAST | OWASP ZAP | 1 | `./scripts/zap-baseline.sh` |
| SCA | OWASP Dependency-Check | 1 | `./scripts/dependency-check.sh` |
| Smoke seguridad / post-deploy | bash + curl | 2 | `./scripts/security-smoke.sh` |
| Exploratorias | Charters manuales | 3 | [`exploratory/`](../testing/exploratory/) |

**Total automatizado en JUnit: 95.** Catálogo caso por caso en [`docs/final/testing/README.md`](../testing/README.md).

### 10.2 Separación por tags

En [`build.gradle`](../../../build.gradle):

```groovy
tasks.named('test') {
    useJUnitPlatform { excludeTags 'integration', 'api', 'contract' }
}

tasks.register('integrationTest', Test) {
    useJUnitPlatform { includeTags 'integration' }
    timeout = java.time.Duration.ofMinutes(15)
    shouldRunAfter contractTest
}
```

**Por qué separar:** los unitarios tardan segundos y no necesitan Docker; los de integración levantan contenedores y tardan minutos. Con la separación, un desarrollador corre `./gradlew test` constantemente y el pipeline corre todo. Además el pipeline muestra en qué etapa exacta se rompió.

### 10.3 Qué hace cada tipo

| Tipo | Qué sustituye | Qué detecta | Qué no detecta |
|---|---|---|---|
| Unitaria | Repositorio mockeado | Errores de lógica y de casos límite | Errores de SQL o mapeo |
| MockMvc / API | Servidor HTTP simulado, seguridad simulada | Rutas, códigos, validación, permisos | Que el JWT real funcione |
| Integración | Nada: Postgres real (y Keycloak real) | SQL, constraints, migraciones, JWT real | Comportamiento del navegador |
| E2E | Nada: navegador real | Flujo del usuario completo | Rendimiento |
| k6 | — | Latencia y errores bajo carga | Corrección funcional |
| ZAP / Dependency-Check | — | Cabeceras y CVEs de dependencias | Fallos de lógica |

### 10.4 Unitarias

`ProductServiceTest` mockea los repositorios: prueba la regla, no la base de datos. 13 casos que incluyen SKU duplicado, categoría inexistente, filtros en blanco.

### 10.5 Escenarios de API

MockMvc con seguridad simulada. La cadena de test está en [`ApiTestSecurityConfig`](../../../src/test/java/icc354/pucmm/proyectoqa/application/controller/ApiTestSecurityConfig.java):

```java
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class ApiTestSecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain apiTestSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
```

Con `@WithMockUser(authorities = "product:view")` se prueba cada permiso sin levantar Keycloak. Rápido, y aun así valida que los `@PreAuthorize` estén bien puestos.

### 10.6 Integración con Testcontainers

Postgres real para todos ([`AbstractIntegrationTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractIntegrationTest.java)):

```java
static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("inventory").withUsername("inventory").withPassword("inventory");

static { POSTGRES.start(); }

@DynamicPropertySource
static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    ...
}
```

Docker asigna un puerto aleatorio; `@DynamicPropertySource` se lo pasa a Spring en tiempo de ejecución. El contenedor se arranca en un bloque `static` para reutilizarlo entre clases en lugar de levantar uno por test.

Y Keycloak real para seguridad ([`AbstractKeycloakIntegrationTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java)):

```java
static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
        .withExposedPorts(KEYCLOAK_PORT)
        .withCommand("start-dev", "--import-realm")
        .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("keycloak/inventory-realm.json").toAbsolutePath().toString()),
                "/opt/keycloak/data/import/inventory-realm.json")
        .waitingFor(Wait.forHttp("/realms/inventory").forPort(KEYCLOAK_PORT).forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
```

Importa **el mismo realm que producción**. `KeycloakSecurityIntegrationTest` pide tokens reales con password grant y verifica 401 sin token, 403 sin permiso y 200 con permiso. Esa es la prueba que demuestra que la integración con Keycloak funciona de verdad.

**Por qué Testcontainers y no H2:** H2 no tiene `ILIKE`, ni los mismos `CHECK`, ni el mismo comportamiento de secuencias. Probar contra H2 y desplegar en Postgres es probar otra cosa.

### 10.7 E2E con Playwright

9 tests. El login es por interfaz real ([`helpers/auth.ts`](../../../frontend/e2e/helpers/auth.ts)):

```ts
await page.goto("/login");
await page.getByTestId("login-button").click();
await page.waitForURL(new RegExp(kcHost.replace(/\./g, "\\.")));
await page.locator("#username").fill(username);
await page.locator("#password").fill(password);
await page.locator("#kc-login").click();
await expect(page).toHaveURL(/\/products$/, { timeout: 30_000 });
```

`permissions.spec.ts` combina UI y API en el mismo test: comprueba que `viewer` no ve el botón *y* que la API le responde 403.

Los selectores usan `data-testid`, no clases CSS: cambiar el diseño no rompe las pruebas.

La `baseURL` es configurable, por eso los mismos tests corren contra desarrollo (`:3000`) y contra staging (`:3008`) en el pipeline.

### 10.8 Rendimiento con k6

| Script | Escenario | Umbrales |
|---|---|---|
| [`load-products.js`](../../../tests/k6/load-products.js) | rampa 0→5→15→15→0 VUs | p95 < 500 ms, fallos < 1 %, checks > 99 % |
| [`stress-products.js`](../../../tests/k6/stress-products.js) | picos 20→50→80→80→0 VUs | p95 < 2000 ms, fallos < 5 %, checks > 95 % |

**Diferencia conceptual:** *carga* mide si el sistema cumple con el tráfico esperado; *estrés* busca el punto donde se degrada. Por eso los umbrales del segundo son más permisivos: se acepta que sea lento, no que falle.

Los umbrales son parte del script, así que k6 devuelve código de error si no se cumplen y el pipeline lo detecta.

### 10.9 Seguridad automatizada

| Herramienta | Tipo | Qué revisa |
|---|---|---|
| Dependency-Check | SCA | CVEs conocidos en las dependencias |
| ZAP baseline | DAST | Cabeceras y vulnerabilidades pasivas sobre la app corriendo |
| `security-smoke.sh` | Funcional | 401/403/200 por permiso y CORS con origen bueno y malo |

Resultados reales en [`docs/final/testing/zap/`](../testing/zap/) y [`dependency-check/`](../testing/dependency-check/).

### 10.10 Cobertura

```groovy
jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = 'CLASS'
            includes = ['icc354.pucmm.proyectoqa.application.service.*']
            excludes = ['icc354.pucmm.proyectoqa.application.service.AuditService']
            limit { counter = 'LINE'; minimum = 0.60 }
        }
    }
}
check.dependsOn jacocoTestCoverageVerification
```

El mínimo se exige **donde importa**: las clases de servicio, que son las que tienen lógica. DTOs y configuración se excluyen del cálculo en Sonar porque cubrirlos infla el número sin aportar confianza.

---

## 11. CI/CD

### 11.1 Reparto de responsabilidades

| | GitHub Actions | Jenkins |
|---|---|---|
| Cuándo | Automático en push y PR a `develop` | Manual / bajo demanda, en local |
| Dónde | Runners de GitHub | Docker en la máquina del equipo |
| Para qué | Guardián del repositorio: nada entra roto | Demostrar que el pipeline es portable y no depende de un SaaS |

No se llaman entre sí. Son dos implementaciones del mismo pipeline. Eso mismo es la evidencia de que el proceso no está atado a una herramienta.

### 11.2 GitHub Actions

[`devsecops.yml`](../../../.github/workflows/devsecops.yml), disparado por push y PR a `develop`:

```
                    ┌──────────────────────────────────────┐
                    │ build-and-test                       │
                    │ build → unit → integration → api →   │
                    │ contract → jacoco → sonar            │
                    └───────────────┬──────────────────────┘
        ┌───────────────┬───────────┴────────┬─────────────────────┐
        ▼               ▼                    ▼                     ▼
 docker-images   dependency-check      zap-baseline        staging-deploy-e2e
 (API + front)   (SCA con caché NVD)   (levanta stack,     (compose staging,
                                        smoke + ZAP)        wait, smoke, E2E)
        └───────────────┴────────────────────┴─────────────────────┘
                                    ▼
                             quality-gate
                    (falla si cualquiera no fue success)
```

Detalles que vale la pena señalar:

- **`concurrency` con `cancel-in-progress`**: si empujas dos commits seguidos, cancela el anterior y no desperdicia minutos.
- **Los 4 jobs intermedios corren en paralelo** porque solo dependen de `build-and-test`.
- **`quality-gate` con `if: always()`**: se ejecuta aunque otro job falle, para poder reportar cuál falló en lugar de quedar en gris.
- **Acciones de Docker fijadas por SHA** (`docker/build-push-action@10e90e...`), no por tag: una regla de seguridad que señaló Sonar (`githubactions:S7637`), porque un tag se puede reescribir.
- **`npm ci --ignore-scripts`**: evita ejecutar scripts de ciclo de vida de dependencias, otra recomendación de seguridad.

Los otros workflows: [`conventional-commits.yml`](../../../.github/workflows/conventional-commits.yml) valida los mensajes de commit en cada PR; `ci.yml`, `security.yml` y `post-deploy-staging.yml` quedaron como `workflow_dispatch` (manuales), porque su contenido se absorbió en el pipeline principal.

### 11.3 Jenkins

[`Jenkinsfile`](../../../infra/jenkins/Jenkinsfile), 10 stages:

| # | Stage | Qué hace |
|---|---|---|
| 1 | Checkout | `checkout scm` |
| 2 | Build | `./gradlew build -x test` |
| 3 | Unit | `./gradlew test` |
| 4 | Integration | `./gradlew integrationTest` (Testcontainers) |
| 5 | API | `./gradlew apiTest contractTest` |
| 6 | Security | Dependency-Check + levanta `docker-compose.security.yml` + smoke JWT/CORS + ZAP + teardown |
| 7 | Sonar | `jacocoTestReport` + `sonar` si existe la credencial |
| 8 | Docker Build | Imágenes de API y frontend |
| 9 | Deploy Staging | Compose staging + `wait-for-stack.sh` + `post-deploy-smoke.sh` |
| 10 | E2E | Playwright contra staging + teardown |

Tres problemas reales resueltos ahí, y son buen material de defensa:

**1. Testcontainers dentro de Jenkins en Docker.**

```groovy
environment {
    TESTCONTAINERS_RYUK_DISABLED = 'true'
    DOCKER_HOST = 'unix:///var/run/docker.sock'
    TESTCONTAINERS_HOST_OVERRIDE = 'host.docker.internal'
}
```

Jenkins corre en un contenedor y usa el Docker del host. Los contenedores hermanos no están en `localhost` de Jenkins, por eso el override de host. Y en el código de test se respeta:

```java
protected static String issuerUri() {
    return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(KEYCLOAK_PORT) + "/realms/inventory";
}
```

Se usa `getHost()` en vez de escribir `localhost`, precisamente para que funcione en los dos entornos.

**2. PKCE necesita contexto seguro.** El navegador de Playwright dentro de Jenkins accedía por `host.docker.internal`, que no es contexto seguro, y sin `crypto.subtle` PKCE no funciona. Solución: un proxy TCP en Node ([`jenkins-e2e-portforward.mjs`](../../../scripts/jenkins-e2e-portforward.mjs)) que expone `localhost:3008/8181/8088` y reenvía al host.

```groovy
node ../scripts/jenkins-e2e-portforward.mjs 3008 8181 8088 &
```

**3. Limpieza garantizada.** Cada stage con contenedores tiene `post { always { ... down -v } }`, y el `post` global vuelve a limpiar en caso de fallo. Sin eso, un build abortado deja stacks huérfanos que rompen el siguiente.

### 11.4 Calidad del código

- **JaCoCo** genera el XML que consume Sonar.
- **SonarCloud** analiza bugs, vulnerabilidades, code smells, duplicación y cobertura. Los badges están en el [README raíz](../../../README.md).
- **Limitación real y declarada:** el plan Free solo analiza la rama `main` desde CI. Por eso el workflow tolera el rechazo en `develop` en lugar de romper el pipeline:

```bash
if grep -q "Not authorized or project not found" sonar-analysis.log; then
  echo "::warning::SonarCloud rechazó el análisis desde CI (plan Free: solo rama main)."
  exit 0
fi
```

Eso está documentado en [`SONARCLOUD.md`](../quality/SONARCLOUD.md). Es mejor declararlo que esperar a que lo pregunten.

---

## 12. Observabilidad

### 12.1 Los tres pilares

| Pilar | Genera | Transporta | Almacena | Visualiza |
|---|---|---|---|---|
| **Métricas** | Micrometer + Actuator | scrape de Prometheus | Prometheus | Grafana |
| **Trazas** | OpenTelemetry | OTLP → Alloy | Tempo | Grafana |
| **Logs** | Logback | Alloy (docker.sock) | Loki | Grafana |

```
inventory-api ──/actuator/prometheus──► Prometheus ──► Grafana
      │                                     │
      │ OTLP 4318                           └──► Alertmanager
      ▼
    Alloy ──► Tempo (trazas)
      └─────► Loki  (logs)
```

### 12.2 Correlación log ↔ traza

Cada línea de log lleva el identificador de traza ([`application.yml`](../../../src/main/resources/application.yml)):

```yaml
logging:
  pattern:
    correlation: "[%X{traceId:-},%X{spanId:-}] "
```

Y Grafana convierte ese texto en un enlace ([`datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml)):

```yaml
derivedFields:
  - name: TraceID
    matcherRegex: "\\[([0-9a-fA-F]{16,32}),"
    datasourceUid: tempo
    urlDisplayLabel: "View Trace"
```

Resultado: ves un error en el log, haces clic y aparece la traza completa de esa petición, incluyendo las consultas SQL (gracias a `datasource-micrometer`, configurado con `jdbc.includes: query, fetch`).

Eso es lo que diferencia "tenemos Grafana" de "tenemos observabilidad".

### 12.3 Los cinco dashboards

| Dashboard | Archivo | Paneles principales |
|---|---|---|
| **App** | [`app.json`](../../../infra/grafana/dashboards/app.json) | Throughput, latencia media, 4xx, 5xx, throughput por URI |
| **Infra** | [`infra.json`](../../../infra/grafana/dashboards/infra.json) | CPU, heap, hilos, GC, pool HikariCP |
| **Security** | [`security.json`](../../../infra/grafana/dashboards/security.json) | 401, 403, fallos de auth, fallos por URI |
| **Business** | [`business.json`](../../../infra/grafana/dashboards/business.json) | Tráfico de productos, stock y reportes |
| **API Ops** | [`api-ops.json`](../../../infra/grafana/dashboards/api-ops.json) | Vista del primer avance |

Consultas PromQL reales que puedes mostrar y explicar:

```promql
# Throughput
sum(rate(http_server_requests_seconds_count[1m]))

# Latencia media
sum(rate(http_server_requests_seconds_sum[5m])) / sum(rate(http_server_requests_seconds_count[5m]))

# Fallos de autenticación
sum(rate(http_server_requests_seconds_count{status=~"401|403"}[5m])) or vector(0)

# Pool de conexiones
hikaricp_connections_active
```

El `or vector(0)` evita que el panel salga vacío cuando aún no hubo ni un error: muestra 0 en lugar de "No data".

El dashboard de **Security** es el más vistoso para la defensa: intentas entrar con `viewer` al dashboard, y el panel de 403 sube en vivo.

### 12.4 Alertas

Seis reglas en [`alerts.yml`](../../../infra/prometheus/alerts.yml):

| Alerta | Condición | Severidad |
|---|---|---|
| `HighProcessCpu` | CPU > 80 % por 2 min | warning |
| `HighJvmHeapMemory` | heap > 85 % por 2 min | warning |
| `InventoryApiDown` | `up == 0` por 1 min | critical |
| `HighHttp5xxErrorRate` | 5xx > 5 % por 2 min | critical |
| `HighHttpLatencyP95` | p95 > 2 s por 3 min | warning |
| `AuthFailureSpike401` | 401 > 0.2 req/s por 2 min | warning |

La última es interesante: un pico de 401 puede indicar un ataque de fuerza bruta o una configuración rota de issuer. Y el `for: 2m` evita alertar por un pico momentáneo.

Ejemplo con detalle:

```yaml
- alert: HighHttp5xxErrorRate
  expr: |
    (
      sum(rate(http_server_requests_seconds_count{job="inventory-api", status=~"5.."}[5m]))
      /
      clamp_min(sum(rate(http_server_requests_seconds_count{job="inventory-api"}[5m])), 0.001)
    ) > 0.05
  for: 2m
```

`clamp_min` evita dividir entre cero cuando no hay tráfico.

### 12.5 Muestreo de trazas

```yaml
management:
  tracing:
    sampling:
      probability: ${OTEL_TRACES_SAMPLING:1.0}   # 0.1 en prod
```

100 % en demo para que se vea todo; 10 % en producción porque guardar cada traza de un sistema con tráfico real es caro y no aporta.

---

## 13. Puntos débiles y cómo responderlos

Decirlos tú antes de que los encuentren cambia por completo el tono de la evaluación.

| Punto | Respuesta honesta |
|---|---|
| **Sin tolerancia a fallos a nivel de código** | No hay Resilience4j ni circuit breaker. La resiliencia es de infraestructura: healthchecks, `restart: unless-stopped`, `depends_on: service_healthy` y alertas de Prometheus. Con un solo backend y una sola base de datos, un circuit breaker no tendría a qué hacerle fallback. El siguiente paso natural sería un breaker entre la API y Keycloak con caché de JWKS. |
| **Keycloak en `start-dev`** | Es modo desarrollo: sin HTTPS y con base de datos embebida. Para producción real haría falta `start`, hostname fijo, TLS y base de datos propia. Está documentado en [`ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md). |
| **Producción es "production-like", no cloud** | Es una plantilla con las mismas restricciones de configuración (sin Swagger, secretos por entorno, puertos en loopback), pensada para ejecutarse local. |
| **Sonar plan Free** | Solo analiza `main` desde CI; en `develop` el análisis lo publica Automatic Analysis. Documentado y tolerado explícitamente en el workflow. |
| **Secretos en los `.example`** | Los `.env*` reales están en `.gitignore`. Los `.example` tienen valores de demo a propósito para que cualquiera pueda levantar el proyecto. En producción irían en un gestor de secretos. |
| **Cobertura baja en Sonar hasta el 28 de julio** | Sonar mostraba 34 % porque las carpetas `dto/` y `service/` no coincidían con los paquetes `application.dto` / `application.service`, y Sonar no lograba mapear la cobertura de los servicios al fuente. Además el reporte de JaCoCo ignoraba `integrationTest.exec`. Ambas cosas están corregidas: los archivos se movieron a `application/` y el reporte agrega todos los `.exec`. Ver [P97b](PREGUNTAS.md#p97b-por-qué-la-cobertura-en-sonar-estaba-en-34--y-qué-hicieron). |
| **Dependency-Check no bloquea el build** | `failBuildOnCVSS = 11` en [`build.gradle`](../../../build.gradle) y la escala CVSS llega a 10, así que ningún CVE hace fallar el pipeline. Es informativo por ahora; la mejora es bajarlo a 7 con archivo de supresiones. Ver [P103](PREGUNTAS.md#p103-qué-encontró-zap-y-qué-hicieron-con-eso). |
| **Los tableros de Grafana no tienen paneles de logs** | Los cinco son PromQL puro. Los logs se ven en **Explore** con la fuente Loki. Si piden logs, ve a Explore, no busques un panel. Ver [P50](PREGUNTAS.md#p50-cómo-se-visualizan-logs-y-métricas). |
| **`uid` del datasource con distinta capitalización** | Los tableros piden `"uid": "Prometheus"` y el provisioning declara `uid: prometheus`. Grafana lo resuelve porque también coincide con el *nombre* del datasource, pero si algún panel apareciera con "Datasource not found", la causa es esa y se arregla en [`datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml). |
| **Comentario incorrecto en `keycloak.ts`** | Dice que el token se guarda en `localStorage`, pero `keycloak-js` sin adaptador lo mantiene **en memoria**. Lo que evita repetir el login es la cookie de sesión de Keycloak en su propio dominio, que consulta `check-sso`. Ver [P87](PREGUNTAS.md#p87-dónde-se-guarda-el-token-en-el-navegador-es-seguro). |
| **Sin caché de aplicación** | No hay Caffeine ni Redis. El único punto donde tendría sentido es el JWKS, y de eso ya se encarga el Resource Server de Spring, que lo cachea internamente. |
| **k6 y ZAP no corren en cada PR** | Tardan demasiado. Se ejecutan bajo demanda y en Jenkins; sus evidencias están versionadas en `docs/final/testing/`. |

---

## 14. Guion de demo

**Duración objetivo: 12 minutos.** Preparar antes: `docker compose up -d` y verificar `health` UP.

| Min | Qué mostrar | Qué decir |
|---|---|---|
| 0–1 | Frontend en `:3000`, login con `admin` | "El login lo hace Keycloak, no nuestra aplicación. Fíjense en la URL: cambia de dominio." |
| 1–3 | Lista de productos: buscar, ordenar, paginar | Abrir DevTools → pestaña Network → mostrar `GET /api/v1/products?page=0&size=10&sort=name,asc` y la cabecera `Authorization: Bearer` |
| 3–4 | Crear producto con SKU repetido | "409 desde el backend, mostrado por el manejador global de excepciones" → abrir `GlobalExceptionHandler.java` |
| 4–5 | Botón **Historial** de un producto | "Hibernate Envers guarda cada revisión automáticamente" → mostrar `@Audited` en `Product.java` |
| 5–6 | Logout, login como `viewer` | Sin botón *Crear*, `/dashboard` redirige a *unauthorized* |
| 6–7 | Terminal: `./scripts/security-smoke.sh` | "La UI oculta el botón, pero lo que protege es la API: 403 real" |
| 7–8 | `ProductController.java` + `DockerSecurityConfig.java` | Recorrer `@PreAuthorize` → `extractAuthorities` → `resource_access` del token |
| 8–9 | Swagger en `:8080/swagger-ui.html`, botón *Authorize* | "En perfil `prod` esto no existe" → mostrar `application-prod.yml` |
| 9–10 | Grafana `:3001` → dashboard **Security** | Refrescar tras los 403 del `viewer`: el panel sube en vivo |
| 10–11 | Jenkins `:8082` → build verde, 95 tests | Recorrer los 10 stages |
| 11–12 | GitHub → pestaña Actions → DevSecOps Pipeline | Mostrar el grafo de jobs y el `quality-gate` |

**Ten abiertos en pestañas del editor:**
[`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) ·
[`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) ·
[`StockService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/StockService.java) ·
[`client.ts`](../../../frontend/src/api/client.ts) ·
[`AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx) ·
[`inventory-realm.json`](../../../keycloak/inventory-realm.json) ·
[`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql) ·
[`docker-compose.yml`](../../../docker-compose.yml) ·
[`Jenkinsfile`](../../../infra/jenkins/Jenkinsfile) ·
[`devsecops.yml`](../../../.github/workflows/devsecops.yml)

---

## Frases de cierre para memorizar

> **Arquitectura.** "Monolito modular en capas: controlador, servicio, repositorio. Un solo desplegable para el backend y una SPA aparte. La granularidad fina está en los permisos, no en los servicios."

> **Seguridad.** "Keycloak autentica y firma; Spring Security verifica la firma localmente y autoriza con `@PreAuthorize` sobre los permisos que vienen en `resource_access` del token. Es stateless: el servidor no guarda sesión."

> **Datos.** "Flyway manda sobre el esquema e Hibernate solo valida. Paginación offset con `Pageable`. Auditoría automática con Envers."

> **Calidad.** "95 pruebas JUnit separadas en cuatro tareas de Gradle, 9 E2E con Playwright, k6 para carga y estrés, ZAP para DAST y Dependency-Check para dependencias. Todo orquestado en GitHub Actions y replicado en Jenkins."

> **Observabilidad.** "Métricas con Prometheus, trazas con Tempo y logs con Loki, correlacionados por `traceId`: desde un log llego a la traza y a las consultas SQL de esa petición."
