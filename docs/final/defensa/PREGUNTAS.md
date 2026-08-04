# Banco de preguntas y respuestas

Las 58 preguntas del avance (P1–P58) + 62 adicionales (P59–P120) + **bloque del profesor (P121–P143)** sobre pipeline, despliegue, paginación, tokens, roles, k6, cobertura y observabilidad. Cada respuesta trae **el archivo donde demostrarla**.

> Guía completa: [`README.md`](README.md) · Árbol comentado: [`ARBOL.md`](ARBOL.md)

## Secciones

- [A. Arquitectura y diseño](#a-arquitectura-y-diseño) — P1–P6, P59–P66
- [B. Docker y despliegue](#b-docker-y-despliegue) — P7–P12, P67–P75
- [C. Base de datos](#c-base-de-datos) — P13–P20, P76–P84
- [D. Seguridad y Keycloak](#d-seguridad-y-keycloak) — P21–P34, P85–P95
- [E. Testing y calidad](#e-testing-y-calidad) — P35–P42, P96–P104
- [F. CI/CD](#f-cicd) — P43–P47, P105–P109
- [G. Observabilidad](#g-observabilidad) — P48–P51, P110–P113
- [H. Demostraciones prácticas](#h-demostraciones-prácticas) — P52–P58
- [I. Preguntas difíciles](#i-preguntas-difíciles) — P114–P120
- [J. Preguntas del profesor (sesión)](#j-preguntas-del-profesor-sesión) — P121–P143

---

# A. Arquitectura y diseño

### P1. ¿Qué modelo arquitectónico se utilizó?

**Arquitectura en capas (layered) con influencia de Clean Architecture**, no MVC clásico.

No es MVC porque el backend no renderiza vistas: expone JSON. La "V" vive en otro proyecto (React).

Las capas y su dependencia:

```
controller  →  application.service  →  domain.repository  →  domain.entity
   (HTTP)         (casos de uso)          (persistencia)        (modelo)
```

La influencia de Clean Architecture está en que **el dominio no depende de nada externo**: `Product`, las excepciones y las interfaces de repositorio no importan nada de web ni de seguridad. La dependencia siempre apunta hacia adentro.

Lo que **falta** para ser Clean Architecture pura: los servicios dependen directamente de las interfaces de Spring Data en lugar de puertos propios, y las entidades JPA hacen de modelo de dominio. Es un compromiso deliberado: la inversión total añadiría una capa de mapeo sin beneficio real a esta escala.

**Mostrar:** [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) → [`ProductService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java) → [`ProductRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/ProductRepository.java)

---

### P2. ¿Qué granularidad se está utilizando?

Dos respuestas, porque son dos ejes:

**Granularidad de despliegue: gruesa.** Un solo servicio backend. Todo el dominio de inventario en un desplegable.

**Granularidad de permisos: fina.** Siete permisos independientes en vez de un rol monolítico:

```json
"product:view", "product:manage", "stock:view", "stock:manage",
"report:view", "audit:view", "user:manage"
```

**Por qué:** permite perfiles como `stock-manager`, que puede mover stock pero no crear productos ni ver reportes. Con un rol único "administrador" habría que escribir código para cada matiz.

**Mostrar:** [`inventory-realm.json`](../../../keycloak/inventory-realm.json) y [`permissions.ts`](../../../frontend/src/auth/permissions.ts)

---

### P3. ¿La aplicación es monolítica o está separada por módulos/microservicios?

**Monolito modular, desplegado en dos artefactos.**

| Artefacto | Contenido |
|---|---|
| `inventory-api` | Todo el backend |
| `inventory-frontend` | SPA compilada servida por nginx |

Más servicios de apoyo de terceros: PostgreSQL, Keycloak, Prometheus, Grafana, Tempo, Loki, Alloy, Alertmanager.

Internamente hay tres módulos lógicos con frontera clara: productos, stock, reportes/auditoría. Cada uno tiene su controlador, su servicio y sus repositorios. Si mañana hubiera que extraer stock a un microservicio, la frontera ya está trazada.

**Por qué no microservicios:** un solo dominio acotado, un solo equipo, una sola base de datos. Microservicios aportarían transacciones distribuidas, descubrimiento de servicios y despliegue independiente — costos sin beneficio aquí.

---

### P4. ¿Qué stack tecnológico utiliza el proyecto?

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.0.6, Gradle |
| Persistencia | PostgreSQL 16, Spring Data JPA / Hibernate, Flyway, Hibernate Envers |
| Seguridad | Keycloak 26, OAuth2 Resource Server, JWT, keycloak-js con PKCE |
| Documentación de API | springdoc-openapi 2.8.6 (Swagger UI) |
| Frontend | React 19, TypeScript, Vite, React Router, nginx |
| Contenedores | Docker, Docker Compose (4 stacks) |
| Pruebas | JUnit 5, Mockito, MockMvc, Testcontainers, Playwright, k6, OWASP ZAP, OWASP Dependency-Check |
| Calidad | JaCoCo, SonarCloud |
| CI/CD | GitHub Actions, Jenkins |
| Observabilidad | Actuator, Micrometer, OpenTelemetry, Prometheus, Alertmanager, Grafana, Tempo, Loki, Grafana Alloy |

**Mostrar:** [`build.gradle`](../../../build.gradle) y [`frontend/package.json`](../../../frontend/package.json)

---

### P5. ¿Qué enfoque se utiliza para la presentación de datos?

**API REST que devuelve JSON + SPA que lo renderiza en el cliente.** Nada de renderizado en servidor.

Tres decisiones concretas:

1. **Nunca se serializa una entidad JPA.** Siempre un `record` DTO. Evita exponer campos internos y evita `LazyInitializationException` al serializar relaciones perezosas.
2. **Envoltura de paginación propia** (`PageResponse`) para no depender del formato interno de Spring, que ha cambiado entre versiones.
3. **Campos derivados calculados en el backend**, como `belowMinStock`, para que el frontend no repita reglas de negocio.

```java
public record PageResponse<T>(
        List<T> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {
    public static <T> PageResponse<T> from(Page<T> page) { ... }
}
```

**Mostrar:** [`PageResponse.java`](../../../src/main/java/icc354/pucmm/proyectoqa/dto/PageResponse.java) y [`ProductResponse.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/dto/ProductResponse.java)

---

### P6. ¿Qué patrones o metodologías se manejan en Spring?

| Patrón | Dónde | Beneficio concreto |
|---|---|---|
| Inyección por constructor | Todos los servicios y controladores | Dependencias explícitas, campos `final`, instanciables en tests sin Spring |
| Repository | `domain/repository` | Solo se declara la interfaz; Spring genera la implementación |
| DTO + método `toResponse()` | Servicios | La entidad no sale al exterior |
| Specification (Criteria API) | `StockMovementRepository.withFilters` | Filtros opcionales sin concatenar SQL |
| `@RestControllerAdvice` | `GlobalExceptionHandler` | Manejo de errores en un solo lugar |
| `@Profile` | `SecurityConfig`, `DockerSecurityConfig`, `OpenApiConfig` | La misma imagen se comporta distinto por ambiente |
| `@Transactional(readOnly = true)` a nivel de clase | Los tres servicios | Por defecto lectura; solo los métodos que escriben lo sobrescriben |
| Template Method | `AbstractIntegrationTest` → `AbstractKeycloakIntegrationTest` | Reutilización de contenedores |

Sobre `@Transactional(readOnly = true)`: marcar la clase entera como lectura y anotar solo `create`, `update`, `delete` con `@Transactional` da dos ventajas — Hibernate omite la comprobación de cambios en las lecturas (más rápido) y un método de escritura sin anotar falla de forma evidente en vez de escribir a medias.

---

### P59. ¿Por qué las entidades no salen directamente en la respuesta?

Cuatro razones:

1. **Seguridad:** la entidad podría ganar un campo sensible y quedaría expuesto sin que nadie lo note.
2. **Acoplamiento:** cambiar un nombre de columna rompería el contrato de la API.
3. **Carga perezosa:** serializar `Product` intentaría cargar `Category`, que es `FetchType.LAZY`, fuera de la transacción.
4. **Campos calculados:** `belowMinStock` y `categoryName` no existen como columnas.

---

### P60. ¿Por qué usan `record` para los DTO?

Son inmutables por definición, generan `equals`, `hashCode` y `toString`, y eliminan decenas de líneas de getters. Un DTO es exactamente eso: datos sin comportamiento.

```java
public record ProductRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String sku,
        String description,
        Long categoryId,
        @NotNull @DecimalMin(value = "0.00") BigDecimal price,
        @NotNull @Min(0) Integer quantity,
        @NotNull @Min(0) Integer minStock,
        @NotNull Boolean active) {}
```

**Mostrar:** [`ProductRequest.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/dto/ProductRequest.java)

---

### P61. ¿Dónde está la lógica de negocio?

En los servicios, nunca en los controladores. Un controlador solo hace: recibir, delegar, devolver.

```java
public ProductResponse create(@Valid @RequestBody ProductRequest request) {
    return productService.create(request);
}
```

La lógica está en `ProductService.create`: normaliza el SKU, verifica que no exista, resuelve la categoría y persiste.

**Prueba de que la separación es real:** `ProductServiceTest` prueba las reglas sin levantar Spring ni HTTP, solo con Mockito.

---

### P62. ¿Por qué `BigDecimal` para el precio y no `double`?

`double` es binario y no puede representar exactamente `0.1`. En operaciones monetarias eso acumula errores de centavos. `BigDecimal` es decimal exacto, y en la base de datos es `NUMERIC(12,2)`, que es el tipo correcto para dinero.

**Mostrar:** [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java) y [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql)

---

### P63. ¿Por qué `Instant` y `TIMESTAMPTZ` en vez de `LocalDateTime`?

`Instant` es un punto absoluto en el tiempo, sin zona horaria. `TIMESTAMPTZ` en PostgreSQL guarda con zona. Combinados, un movimiento registrado a las 3 p. m. en Santo Domingo se lee correctamente desde cualquier zona. Con `LocalDateTime` no se sabría a qué zona pertenece el valor guardado.

---

### P64. ¿Qué pasa si dos usuarios modifican el mismo producto a la vez?

Hoy gana el último que guarda: no hay bloqueo optimista. La mejora sería añadir `@Version` a la entidad, con lo que Hibernate lanzaría `OptimisticLockException` y se devolvería un 409.

Sí está protegida la parte crítica: los movimientos de stock ocurren dentro de `@Transactional`, y la lectura del stock previo y su actualización van en la misma transacción.

---

### P65. ¿Por qué el enum `MovementType` se guarda como texto?

```java
@Enumerated(EnumType.STRING)
@Column(name = "movement_type", nullable = false, length = 20)
private MovementType movementType;
```

Con `EnumType.ORDINAL` se guardaría 0, 1, 2. Si alguien reordena las constantes del enum, todos los datos históricos cambian de significado sin previo aviso. Con `STRING` la base guarda `"IN"`, `"OUT"`, `"ADJUSTMENT"` — legible en un `SELECT` y estable. Además la tabla tiene un `CHECK` con esos tres valores.

---

### P66. ¿Por qué `open-in-view: false`?

```yaml
spring:
  jpa:
    open-in-view: false
```

Por defecto Spring mantiene la sesión de Hibernate abierta hasta que se renderiza la respuesta. Eso permite cargar relaciones perezosas "por accidente" en la capa de presentación y genera consultas invisibles (problema N+1). Desactivarlo obliga a resolver todo dentro del servicio, dentro de su transacción. Es lo que hace que los mapeos a DTO sean explícitos.

---

# B. Docker y despliegue

### P7. ¿Sobre qué está corriendo Keycloak?

Imagen oficial `quay.io/keycloak/keycloak:26.0` en un contenedor Docker, con el comando `start-dev --import-realm`. Internamente escucha en el puerto 8080; se publica en el host en 8081 (dev), 8181 (staging), 8182 (prod) y 8091 (stack de seguridad).

**Mostrar:** servicio `keycloak` en [`docker-compose.yml`](../../../docker-compose.yml)

---

### P8. ¿Keycloak está desplegado en Docker o standalone?

En Docker, como un servicio más del Compose. Nunca se instala a mano.

Ventaja concreta: el realm se importa automáticamente desde [`inventory-realm.json`](../../../keycloak/inventory-realm.json) en cada arranque, así que cualquiera clona el repo, hace `docker compose up` y tiene los 3 usuarios y los 7 permisos ya creados. Es infraestructura como código.

En el stack de seguridad hay incluso una imagen propia, [`infra/keycloak/Dockerfile`](../../../infra/keycloak/Dockerfile), que copia el realm dentro de la imagen — necesaria porque Jenkins no puede hacer bind mounts de rutas relativas.

---

### P9. Explicar el Dockerfile

[`Dockerfile`](../../../Dockerfile), dos etapas:

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

| Línea | Por qué |
|---|---|
| Dos `FROM` | La imagen final lleva JRE, no JDK ni Gradle ni fuentes: más pequeña y con menos superficie de ataque |
| `COPY gradlew`, `gradle`, `*.gradle` antes que `src` | Aprovecha la caché de capas: si solo cambia el código, no se rebaja la capa de dependencias |
| `-x test` | Los tests corren en el pipeline; incluirlos aquí triplicaría cada build de imagen |
| `grep -v plain` | Gradle puede generar `app.jar` y `app-plain.jar`; copiar con comodín produce un JAR corrupto |
| `apk add curl` | Necesario para el HEALTHCHECK, porque `jre-alpine` no lo trae |
| `HEALTHCHECK` | Es lo que permite `depends_on: condition: service_healthy` en el Compose |

El del frontend ([`frontend/Dockerfile`](../../../frontend/Dockerfile)) también es multi-etapa: Node 22 compila, nginx sirve. Detalle clave: las variables `VITE_*` se pasan como `ARG` porque Vite las incrusta en el bundle **al compilar**, no las lee en runtime.

---

### P10. Explicar el Docker Compose

[`docker-compose.yml`](../../../docker-compose.yml), 10 servicios:

| Servicio | Imagen | Puerto host | Rol |
|---|---|---|---|
| `postgres` | postgres:16-alpine | 5433 | Base de datos |
| `keycloak` | keycloak:26.0 | 8081 | Identidad |
| `api` | build local | 8080 | Backend |
| `frontend` | build local | 3000 | SPA en nginx |
| `prometheus` | prom/prometheus:v2.53.0 | 9090 | Métricas |
| `grafana` | grafana:12.3.0 | 3001 | Dashboards |
| `tempo` | tempo:2.6.1 | 3200 | Trazas |
| `loki` | loki:3.2.1 | 3100 | Logs |
| `alloy` | alloy:v1.5.1 | 4317/4318/12345 | Collector OTLP |
| `alertmanager` | alertmanager:v0.27.0 | 9093 | Alertas |
| `jenkins` | build local | 8082 | CI local |

Los mecanismos que explicar:

**Healthcheck + dependencia condicional.**

```yaml
postgres:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U inventory -d inventory"]
    interval: 5s
api:
  depends_on:
    postgres: { condition: service_healthy }
```

Sin `condition: service_healthy`, la API arrancaría cuando el contenedor existe pero Postgres aún no acepta conexiones, y Flyway fallaría.

**Volúmenes nombrados** (`postgres_data`, `jenkins_home`, …): los datos sobreviven a `docker compose down`. Con `down -v` sí se borran.

**Volúmenes de solo lectura para configuración:**

```yaml
- ./infra/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
```

El `:ro` evita que un contenedor modifique la configuración del repositorio.

**Jenkins con el socket de Docker:**

```yaml
- /var/run/docker.sock:/var/run/docker.sock
```

Permite que Jenkins levante contenedores hermanos (Testcontainers, ZAP, stacks de Compose).

---

### P11. Revisar el archivo YAML de configuración

[`application.yml`](../../../src/main/resources/application.yml), bloque por bloque:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/inventory}
```
Sintaxis `${VARIABLE:valor_por_defecto}`: en Docker la variable existe, en local se usa el valor por defecto.

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```
Hibernate valida pero no modifica el esquema (eso es de Flyway) y no mantiene la sesión abierta en la vista.

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```
Lista blanca de endpoints de Actuator. Por defecto Spring solo expone `health`; exponer `*` sería un riesgo.

```yaml
  tracing:
    sampling:
      probability: ${OTEL_TRACES_SAMPLING:1.0}
```

```yaml
logging:
  pattern:
    correlation: "[%X{traceId:-},%X{spanId:-}] "
```
Cada línea de log lleva el id de traza: eso es lo que permite saltar de un log a su traza en Grafana.

---

### P12. ¿Tenemos definidos los tres ambientes?

Sí, y con puertos disjuntos para que puedan coexistir.

| | Desarrollo | Staging | Producción |
|---|---|---|---|
| Compose | `docker-compose.yml` | `docker-compose.staging.yml` | `docker-compose.prod.yml` |
| Perfil Spring | `docker` | `staging` | `prod` |
| Frontend / API / Keycloak | 3000 / 8080 / 8081 | 3008 / 8088 / 8181 | 3009 / 8089 / 8182 |
| Swagger | sí | sí | no |
| Logs | INFO | INFO | WARN |
| Muestreo de trazas | 100 % | 100 % | 10 % |
| Postgres publicado | red | red | solo `127.0.0.1` |

**Y no son decorativos:** el pipeline despliega staging de verdad y corre smoke y E2E contra él.

**Mostrar:** [`ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md) y el job `staging-deploy-e2e` de [`devsecops.yml`](../../../.github/workflows/devsecops.yml)

---

### P67. ¿Qué diferencia hay entre un perfil de Spring y un ambiente?

El **perfil** es una configuración de la aplicación: selecciona `application-<perfil>.yml` y activa los beans con `@Profile`.

El **ambiente** es el stack completo: Compose + `.env` + puertos + Keycloak + frontend + observabilidad.

El perfil es una pieza dentro del ambiente. La misma imagen Docker corre en los tres ambientes; lo que cambia es `SPRING_PROFILES_ACTIVE` y las variables.

---

### P68. ¿Por qué el issuer y el JWKS apuntan a hosts distintos?

```yaml
KEYCLOAK_ISSUER_URI: http://localhost:8081/realms/inventory
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: http://keycloak:8080/realms/inventory/protocol/openid-connect/certs
```

- El **issuer** debe coincidir exactamente con el claim `iss` del token, y el token lo emitió Keycloak respondiendo a una petición del navegador a `localhost:8081`.
- El **JWKS** lo descarga la API desde dentro de la red Docker, donde `localhost` es el propio contenedor de la API; ahí el nombre correcto es `keycloak:8080`.

Si se igualan, el arranque falla o todos los tokens dan 401. Es el error más difícil que se resolvió en el proyecto.

---

### P69. ¿Por qué `KC_HOSTNAME` en staging?

```yaml
KC_HOSTNAME: ${KEYCLOAK_HOSTNAME:-http://localhost:8181}
KC_HOSTNAME_STRICT: "false"
KC_HOSTNAME_BACKCHANNEL_DYNAMIC: "false"
```

Sin eso, Keycloak arma el `iss` según el `Host` de cada petición. Jenkins pide tokens por `host.docker.internal:8181` y el navegador por `localhost:8181`: dos issuers distintos para el mismo Keycloak, y la API rechaza uno de los dos con 401. Fijando `KC_HOSTNAME` siempre emite el mismo issuer.

---

### P70. ¿Por qué existe un cuarto Compose, el de seguridad?

[`docker-compose.security.yml`](../../../docker-compose.security.yml) resuelve dos problemas de Jenkins:

1. **Sin bind mounts.** Jenkins corre en contenedor usando el Docker del host; una ruta `./infra/...` se resolvería en el host y no existiría igual. Por eso este stack usa una imagen de Keycloak con el realm ya dentro.
2. **Sin `container_name`.** Si usara `inventory-api` chocaría con el stack de desarrollo si estuviera levantado.

Puertos 8090/8091/5435, distintos de todos los demás.

---

### P71. ¿Qué pasa si haces `docker compose down` vs `down -v`?

`down` borra contenedores y red, pero conserva los volúmenes: los productos siguen ahí al levantar de nuevo.
`down -v` borra también los volúmenes: base vacía y Flyway vuelve a crear el esquema desde cero.

En el pipeline siempre se usa `down -v`, porque cada ejecución debe partir de un estado limpio y reproducible.

---

### P72. ¿Cómo se manejan los secretos?

**Patrón:** variables de entorno + `*.example` en git; valores reales fuera del repo.

- Los `.env`, `.env.staging` y `.env.production` reales están en `.gitignore`; solo se versionan los `.example`.
- `application-staging.yml` y `application-prod.yml` **no tienen valores literales**: solo `${VARIABLE}`.
- Scripts (`security-smoke`, k6, waits) usan [`scripts/lib/load-env.sh`](../../../scripts/lib/load-env.sh): rellenan defaults **demo** solo si la variable está vacía.
- GitHub Actions: Secrets opcionales `KEYCLOAK_CLIENT_SECRET` / `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` (si faltan → demo). Obligatorios/recomendados: `SONAR_TOKEN`, `NVD_API_KEY`.
- Tests IT: leen `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` del entorno; Gradle carga `.env` en la JVM de test (sin clase Java con secretos).
- **Excepción:** `keycloak/inventory-realm.json` trae secret/passwords demo porque Keycloak los necesita en `--import-realm`. En producción ese JSON no se publica con secretos reales.

Limitación honesta: un deploy real usaría Vault / Secrets Manager, no archivos en disco.

Detalle: [`ENVIRONMENTS.md`](../ci/ENVIRONMENTS.md) § Política de secretos.

---

### P73. ¿Por qué el frontend necesita reconstruirse por ambiente?

Porque Vite incrusta las variables `VITE_*` en el bundle durante `npm run build`. No hay forma de cambiar la URL de la API sin recompilar.

Alternativa que no se implementó: servir un `config.json` que la aplicación lea al arrancar. Eso permitiría una sola imagen para todos los ambientes.

---

### P74. ¿Por qué nginx y no `npm start` en producción?

`npm run dev` es un servidor de desarrollo: sin optimizar, con recarga en caliente y sin pensar en concurrencia. El build genera HTML, CSS y JS estáticos, y nginx sirve archivos estáticos mucho mejor que Node. La imagen final tampoco necesita Node instalado.

---

### P75. ¿Qué hace `.dockerignore`?

Excluye del contexto de build lo que no debe entrar: `build/`, `node_modules/`, `.git/`, `.env`. Beneficio doble: el build es más rápido porque se envía menos al daemon, y no se filtran secretos ni artefactos locales dentro de la imagen.

---

# C. Base de datos

### P13. ¿Cómo se renderiza la lista de productos?

Recorrido completo:

1. `Products.tsx` mantiene el estado: página, tamaño, filtros, campo y dirección de orden.
2. `useEffect` llama a `loadProducts()` cada vez que cambia alguno.
3. `listProducts()` arma la query string.
4. `apiFetch()` añade el `Bearer` y hace `fetch`.
5. La API responde un `PageResponse`.
6. `setProducts(pageResult.content)` y React vuelve a renderizar.
7. `products.map(...)` genera las filas; `belowMinStock` pinta la fila en rojo.

```tsx
{products.map((product) => (
  <tr key={product.id}
      className={product.belowMinStock ? "row-low-stock" : undefined}
      data-low-stock={product.belowMinStock ? "true" : "false"}>
```

El atributo `data-low-stock` existe para que Playwright pueda verificarlo sin depender de las clases CSS.

**Mostrar:** [`Products.tsx`](../../../frontend/src/pages/Products.tsx)

---

### P14 / P15. ¿Cómo se consume la información desde la base de datos?

Nunca con SQL escrito a mano en la capa de servicio. Tres mecanismos según el caso:

**1. Métodos derivados del nombre.** Spring Data genera la consulta a partir del nombre del método:

```java
boolean existsBySku(String sku);
boolean existsBySkuAndIdNot(String sku, Long id);
long countByActiveTrue();
Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
```

**2. JPQL explícito** para agregados:

```java
@Query("SELECT COALESCE(SUM(p.price * p.quantity), 0) FROM Product p")
BigDecimal sumInventoryValue();
```

`COALESCE` evita `null` cuando la tabla está vacía.

**3. SQL nativo** cuando se necesita algo específico de PostgreSQL:

```sql
SELECT p.* FROM products p
WHERE (CAST(:namePattern AS TEXT) IS NULL OR p.name ILIKE CAST(:namePattern AS TEXT))
  AND (CAST(:categoryId AS BIGINT) IS NULL OR p.category_id = :categoryId)
```

`ILIKE` es de PostgreSQL: búsqueda sin distinguir mayúsculas. Los `CAST` son obligatorios porque el motor no puede deducir el tipo de un parámetro que solo se compara con `NULL`.

**4. Criteria API** para filtros combinables:

```java
static Specification<StockMovement> withFilters(Long productId, MovementType movementType,
                                                Instant from, Instant to) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        if (productId != null)    predicates.add(cb.equal(root.get("product").get("id"), productId));
        if (movementType != null) predicates.add(cb.equal(root.get("movementType"), movementType));
        if (from != null)         predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null)           predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        if (query != null)        query.orderBy(cb.desc(root.get("createdAt")));
        return cb.and(predicates.toArray(Predicate[]::new));
    };
}
```

**Todos usan parámetros nombrados. Ninguno concatena strings. Por eso no hay inyección SQL.**

---

### P16. Mostrar el cliente HTTP desde donde se consume la información

Dos clientes, según de qué lado se pregunte.

**Cliente HTTP del frontend** — [`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts):

```ts
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (keycloak.authenticated) {
    await keycloak.updateToken(30);
  }
  const headers: Record<string, string> = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(options.headers as Record<string, string>),
  };
  if (keycloak.token) {
    headers.Authorization = `Bearer ${keycloak.token}`;
  }
  const response = await fetch(`${import.meta.env.VITE_API_URL}${path}`, { ...options, headers });

  if (response.status === 204) return undefined as T;
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorResponse | null;
    throw new ApiError(response.status, body?.message ?? response.statusText, body?.fieldErrors);
  }
  return response.json() as Promise<T>;
}
```

Es `fetch` nativo, sin axios: una dependencia menos y suficiente para este caso.

**Cliente HTTP del backend hacia Keycloak** — solo en tests, con `RestClient` de Spring 6 ([`AbstractKeycloakIntegrationTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/integration/AbstractKeycloakIntegrationTest.java)). En producción la API **no** llama a Keycloak por petición: valida el JWT localmente.

---

### P17. ¿Se utiliza paginación?

Sí, en todos los listados que pueden crecer: productos, movimientos de stock e historial por producto.

```java
public PageResponse<ProductResponse> list(..., @PageableDefault(size = 20) Pageable pageable)
```

Los reportes del dashboard no usan `Pageable` porque son listas cortas por definición, pero tienen un límite defensivo:

```java
int size = Math.max(1, Math.min(limit, 100));
```

En la UI, `Products.tsx` usa `size = 10` y muestra `totalElements` y `totalPages`.

---

### P18. ¿Qué mecanismo de paginación se utiliza?

**Offset / page**, el estándar de Spring Data. El cliente envía `?page=0&size=20&sort=name,asc` y Hibernate genera `LIMIT 20 OFFSET 0`.

| Mecanismo | Ventaja | Desventaja |
|---|---|---|
| **Offset (el nuestro)** | Saltar a cualquier página, mostrar "3 de 12" | `OFFSET` grande obliga al motor a descartar filas |
| Cursor / keyset | Rendimiento constante | Solo siguiente/anterior, sin total |

**Por qué offset:** la interfaz necesita numeración de páginas y total de resultados. El costo se nota con cientos de miles de filas; a esta escala no aplica.

**Si preguntan por escala:** para un catálogo enorme se pasaría a keyset usando `WHERE (name, id) > (:lastName, :lastId) ORDER BY name, id LIMIT :size`, aprovechando el índice `idx_products_name`.

---

### P19. Mostrar el esquema de la base de datos

Está versionado en [`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql).

```sql
CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    sku           VARCHAR(50)  NOT NULL UNIQUE,
    description   TEXT,
    category_id   BIGINT REFERENCES categories(id),
    price         NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    quantity      INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    min_stock     INTEGER NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Seis tablas: `categories`, `products`, `stock_movements`, `revinfo`, `products_audit` (las dos últimas de Envers) y el historial de Flyway `flyway_schema_history`.

Relaciones: `products.category_id → categories.id` (N:1, opcional) y `stock_movements.product_id → products.id` (N:1, obligatoria).

Índices: nombre, SKU, categoría y estado en productos; producto y fecha descendente en movimientos.

**Para demostrarlo en vivo:**

```bash
docker exec -it inventory-postgres psql -U inventory -d inventory -c "\dt"
docker exec -it inventory-postgres psql -U inventory -d inventory -c "\d products"
```

---

### P20. ¿Por qué no se está versionando la API?

**Sí está versionada**, por ruta:

```java
@RequestMapping("/api/v1/products")
@RequestMapping("/api/v1/stock/movements")
@RequestMapping("/api/v1/reports")
@RequestMapping("/api/v1/audit/products")
@RequestMapping("/api/v1/categories")
```

Todos los endpoints llevan `/api/v1`.

**Por qué versionado por URI y no por cabecera:** es visible en logs, en el navegador y en Swagger, y no requiere configuración especial del cliente. Es la estrategia más común en APIs REST públicas.

**Cómo se introduciría una v2:** un `ProductV2Controller` con `@RequestMapping("/api/v2/products")` conviviendo con v1 hasta migrar a todos los clientes. La versión está en el controlador, no en el servicio, así que la lógica se reutiliza.

---

### P76. ¿Por qué Flyway y no `ddl-auto: update`?

`update` deja que Hibernate adivine los cambios. Nunca borra columnas, no sabe migrar datos y hace cosas distintas según la versión. En producción es peligroso.

Con Flyway cada cambio es un archivo SQL versionado, revisable en el pull request, que se aplica en el mismo orden en todos los ambientes. Y `ddl-auto: validate` verifica que las entidades coincidan con el esquema: si no, la aplicación **no arranca**.

---

### P77. ¿Qué pasa si alguien modifica una migración ya aplicada?

Flyway guarda un checksum de cada archivo en `flyway_schema_history`. Si cambia, falla el arranque con "Migration checksum mismatch". Es intencional: una migración aplicada es inmutable; los cambios van en una migración nueva.

---

### P78. ¿Por qué `V3` cambia el incremento de la secuencia a 50?

```sql
ALTER SEQUENCE revinfo_seq INCREMENT BY 50;
```

Hibernate reserva bloques de identificadores para no consultar la secuencia en cada inserción (`allocationSize` por defecto = 50). Si la secuencia incrementa de 1 en 1 pero Hibernate cree que tiene 50 reservados, se generan identificadores duplicados. `V3` alinea la base de datos con lo que Hibernate espera.

Fue un bug real: primero `V2` creó la secuencia y luego `V3` corrigió el incremento.

---

### P79. ¿Qué es Hibernate Envers y cómo funciona?

Un módulo de Hibernate que audita entidades automáticamente. Al anotar con `@Audited`, cada `INSERT`, `UPDATE` o `DELETE` genera una fila en `products_audit` con el número de revisión y el tipo de cambio (0 alta, 1 modificación, 2 baja).

```java
@Entity
@Table(name = "products")
@Audited
public class Product { ... }
```

Se lee con `AuditReader` en [`AuditService`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/AuditService.java) y se muestra con el botón **Historial** de la tabla de productos.

**Por qué no hacerlo a mano con triggers:** habría que mantener la tabla espejo sincronizada con cada cambio de esquema. Envers lo hace solo.

---

### P80. ¿Por qué la categoría está marcada como no auditada?

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
private Category category;
```

Sin eso, Envers exigiría que `Category` también fuera `@Audited` y crearía `categories_audit`. Las categorías son un catálogo casi estático: auditarlas añade tablas y complejidad sin valor. Se audita **qué categoría tenía el producto** (el id), no la historia de la categoría.

---

### P81. ¿Por qué los movimientos de stock guardan `quantity_before` y `quantity_after`?

Porque hacen del historial una fuente de verdad autosuficiente. Se puede auditar la evolución completa del stock sin reconstruirla sumando deltas, y si alguna vez el `products.quantity` se desincroniza, se detecta comparándolo con el último `quantity_after`.

También hace el registro inmutable: no se actualiza, solo se inserta.

---

### P82. ¿Por qué el SKU se normaliza a mayúsculas?

```java
private String normalizeSku(String sku) {
    return sku.trim().toUpperCase();
}
```

Sin normalizar, `lap-001` y `LAP-001` serían dos productos distintos y el constraint `UNIQUE` no lo impediría, porque PostgreSQL distingue mayúsculas. Normalizando, la unicidad es real. El `trim()` evita duplicados por un espacio invisible al final.

---

### P83. ¿Hay riesgo de inyección SQL?

No. Todas las consultas usan parámetros nombrados (`:namePattern`, `:categoryId`) o Criteria API. Ninguna concatena entrada del usuario en la cadena SQL. El driver JDBC envía valor y sentencia por separado, así que un valor nunca se interpreta como SQL.

Incluso los patrones `LIKE` se construyen en Java y viajan como parámetro:

```java
private static String toLikePattern(String value) {
    if (value == null || value.isBlank()) return null;
    return "%" + value.trim() + "%";
}
```

---

### P84. ¿Cómo evitan el problema N+1?

Tres medidas:

1. `open-in-view: false`, que impide cargas perezosas accidentales en la vista.
2. Los mapeos a DTO ocurren dentro del servicio, dentro de la transacción, y son explícitos.
3. Las consultas de agregación (`countLowStock`, `sumInventoryValue`, `findTopProductsByUnitsOut`) se resuelven en el motor con `SUM` y `GROUP BY`, no cargando entidades a memoria para contarlas en Java.

Se puede verificar en Grafana: las trazas incluyen los spans de JDBC gracias a `datasource-micrometer`, así que una petición que dispare demasiadas consultas se ve.

---

# D. Seguridad y Keycloak

### P21. ¿Cómo está integrado Keycloak con la aplicación?

En tres puntos, sin acoplamiento de código:

| Punto | Cómo |
|---|---|
| **Frontend** | `keycloak-js` con Authorization Code + PKCE. Redirige al login de Keycloak y guarda el token |
| **Backend** | OAuth2 Resource Server. Solo conoce dos URLs: el issuer y el JWKS |
| **Realm** | Importado desde `inventory-realm.json` al arrancar el contenedor |

El backend **no tiene el SDK de Keycloak**. Solo `spring-boot-starter-oauth2-resource-server`. Si mañana se cambia a Auth0 o Okta, se cambian dos URLs y no una línea de código Java.

**Mostrar:** [`build.gradle`](../../../build.gradle) — no hay dependencia `keycloak-spring-boot-starter`.

---

### P22. ¿Se está utilizando Keycloak integrado correctamente?

Sí, y estos son los indicadores:

| Buena práctica | Evidencia |
|---|---|
| Cliente público con PKCE para el navegador | `"pkce.code.challenge.method": "S256"` en el realm y `pkceMethod: "S256"` en `AuthContext` |
| La aplicación nunca ve la contraseña | El login ocurre en el dominio de Keycloak |
| Validación local del JWT, no introspección | `jwk-set-uri` configurado |
| Autorización por roles del token, no por lista local | `hasResourceRole` y `resource_access` |
| Realm versionado como código | `keycloak/inventory-realm.json` |
| Redirect URIs restringidas | Solo los 4 puertos del frontend |
| Verificado con Keycloak real en pruebas | `KeycloakSecurityIntegrationTest` con Testcontainers |

Lo que faltaría para producción: HTTPS, Keycloak en modo `start`, base de datos propia y rotación del secreto del cliente.

---

### P23. ¿Cómo se está manejando la seguridad del sistema?

En capas:

| Capa | Mecanismo |
|---|---|
| Transporte | CORS con lista blanca de orígenes |
| Autenticación | JWT firmado por Keycloak, verificado localmente |
| Autorización | `@PreAuthorize` por permiso en cada endpoint |
| Sesión | Stateless: sin cookies, sin estado en el servidor |
| Entrada | Bean Validation en los DTO |
| Datos | Constraints y CHECKs en PostgreSQL |
| Consultas | Parámetros nombrados, sin concatenación |
| Superficie | Swagger y Actuator reducidos en `prod` |
| Dependencias | OWASP Dependency-Check en el pipeline |
| Aplicación corriendo | OWASP ZAP baseline |
| Verificación funcional | `security-smoke.sh` con 11 asserts |

---

### P24. ¿Qué mecanismo se utiliza para proteger la API?

**OAuth2 Resource Server con JWT Bearer.**

```java
.oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
```

Por cada petición:

1. Se extrae el `Authorization: Bearer <token>`.
2. Se verifica la firma con la clave pública de Keycloak (JWKS).
3. Se validan `exp` e `iss`.
4. Los roles del token se convierten en authorities.
5. `@PreAuthorize` decide.

Rutas públicas, solo estas:

```java
List<String> publicPaths = new ArrayList<>(List.of(
        "/actuator/health", "/actuator/health/**", "/actuator/prometheus"));
if (swaggerUiEnabled) {
    publicPaths.addAll(List.of("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**"));
}
```

Todo lo demás: `.anyRequest().authenticated()`.

---

### P25. ¿En qué punto se envía la información de seguridad al cliente?

Al terminar el login, cuando keycloak-js intercambia el `code` por el token. El JWT ya viene con los roles dentro; no hay una llamada adicional del tipo "dame mis permisos".

Estructura del token de `viewer`:

```json
{
  "iss": "http://localhost:8081/realms/inventory",
  "sub": "…",
  "preferred_username": "viewer",
  "exp": 1737000000,
  "resource_access": {
    "inventory-api": { "roles": ["product:view", "stock:view"] }
  }
}
```

**Para demostrarlo en vivo:** pegar el token en jwt.io y mostrar `resource_access`. Es el momento más contundente de la defensa.

---

### P26. ¿En qué momento el servidor conoce los permisos del usuario?

En cada petición, al validar el token. No antes.

```
Petición → filtro JWT → verifica firma → extractAuthorities(jwt, "inventory-api")
        → SecurityContext con las authorities → @PreAuthorize evalúa
```

El servidor **no guarda** los permisos entre peticiones: es stateless. Los lee del token cada vez, y el token es confiable porque está firmado.

---

### P27. ¿La sesión del servidor es stateful o stateless?

**Stateless.**

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

Consecuencias:

| Aspecto | Efecto |
|---|---|
| Escalabilidad | Cualquier instancia atiende cualquier petición, sin sesiones pegajosas |
| CSRF | No aplica: no hay cookie de sesión que el navegador envíe automáticamente. Por eso se desactiva con `// NOSONAR` justificado |
| Logout | Es cosa del cliente y de Keycloak; el token sigue válido hasta expirar |
| Revocación inmediata | Es la desventaja: requeriría introspección o listas de revocación |

---

### P28. ¿Qué sucede si se crea un nuevo rol en Keycloak?

Depende de qué tipo de cambio sea:

**Caso 1 — nuevo usuario o nueva combinación de permisos existentes: cero código.** Se crea en la consola de Keycloak, se le asignan permisos, y funciona de inmediato. `extractAuthorities` lee lo que venga en el token.

**Caso 2 — un permiso completamente nuevo, por ejemplo `product:export`.** Hay que:
1. Crearlo en Keycloak.
2. Añadir `@PreAuthorize("hasAuthority('product:export')")` al endpoint.
3. Añadirlo a `permissions.ts` si la UI debe reaccionar.

**Por qué:** el sistema descubre roles dinámicamente, pero no puede inventar qué endpoint protegen. El mapeo permiso→endpoint es una decisión de negocio que vive en el código.

**Demostración en vivo, si hay tiempo:** consola de Keycloak → usuario `viewer` → asignar `report:view` → cerrar y volver a iniciar sesión → el dashboard ahora abre. Sin recompilar nada.

---

### P29. ¿Los roles se obtienen dinámicamente desde Keycloak o están hardcodeados?

**Dinámicamente, del token.** No hay ninguna lista de usuarios ni de roles en el código.

Backend:

```java
Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
Object clientAccess = resourceAccess.get(clientId);
```

Frontend:

```tsx
const hasRole = useCallback((role: string) => {
  return keycloak.hasResourceRole(role, API_CLIENT_ID);
}, []);
```

Lo único fijo son los **nombres** de los permisos en `permissions.ts`, que son constantes para evitar errores de escritura. Son el contrato entre el realm, la API y la UI.

---

### P30. ¿Cómo se visualiza Swagger en producción?

**No se visualiza. Está deshabilitado.**

Dos capas:

```yaml
# application-prod.yml
springdoc:
  api-docs:   { enabled: false }
  swagger-ui: { enabled: false }
```

```java
@Configuration
@Profile("!prod")
public class OpenApiConfig { ... }
```

Y la seguridad se adapta sola: si Swagger está apagado, sus rutas no entran en la lista pública.

```java
if (swaggerUiEnabled) {
    publicPaths.addAll(List.of("/swagger-ui/**", ...));
}
```

**Por qué:** Swagger publica el mapa completo de la API: rutas, parámetros y esquemas. Es un regalo para un atacante. En desarrollo y staging es una herramienta; en producción, superficie de ataque.

**Verificar:**

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8089/swagger-ui.html   # 404
```

---

### P31. Mostrar el flujo de Login

```
1. http://localhost:3000/  → clic "Iniciar sesión"
2. keycloak.login({ redirectUri: origin + "/management" })
3. Redirección: http://localhost:8081/realms/inventory/protocol/openid-connect/auth
                ?client_id=inventory-frontend
                &response_type=code
                &code_challenge=<hash>&code_challenge_method=S256
4. El usuario escribe sus credenciales EN Keycloak
5. Vuelve con ?code=...
6. keycloak-js: POST /token con code + code_verifier → access_token + refresh_token
7. React → /management → ProtectedRoute → /products
8. Cada llamada lleva Authorization: Bearer <access_token>
```

**Qué señalar en vivo:** que la barra de direcciones cambia de dominio en el paso 3. La aplicación nunca ve la contraseña.

**Qué es PKCE y por qué:** el cliente genera un secreto aleatorio (`code_verifier`), envía su hash (`code_challenge`) al pedir el código, y al canjearlo envía el original. Si alguien intercepta el `code`, no puede canjearlo sin el verifier. Es obligatorio para clientes públicos, que no pueden guardar un secreto fijo.

---

### P32. ¿Cómo se maneja la autenticación en el código?

**Frontend** — [`AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx):

```tsx
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => {
    keycloak.logout({ redirectUri: window.location.origin + "/" });
  });
};

keycloak.init({ onLoad: "check-sso", pkceMethod: "S256" })
  .then((authenticated) => { setIsAuthenticated(authenticated); setReady(true); })
  .catch(() => setReady(true));
```

El estado `ready` evita renderizar la aplicación antes de saber si hay sesión, lo que provocaría un parpadeo de "no autenticado".

**Backend:** no hay código de autenticación propio. Lo hace el filtro de Spring Security configurado en `DockerSecurityConfig`. Escribir autenticación a mano es exactamente lo que no se debe hacer.

---

### P33. ¿Cómo se maneja la seguridad en el código?

Cuatro archivos concentran toda la seguridad del backend:

| Archivo | Responsabilidad |
|---|---|
| [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) | Cadena de filtros, rutas públicas, conversión de roles |
| [`SecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/SecurityConfig.java) | Variante abierta para desarrollo local |
| [`CorsConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/CorsConfig.java) | Orígenes permitidos |
| Anotaciones `@PreAuthorize` | Permiso requerido por endpoint |

**Por qué así:** la seguridad está en un lugar auditable, no dispersa en `if` dentro de los controladores. Se puede revisar en una pantalla.

---

### P34. ¿Cómo maneja Spring Security el contexto para saber que soy el usuario "X"?

Con `SecurityContextHolder`, que guarda la autenticación en un `ThreadLocal` durante la petición.

Secuencia:

1. `BearerTokenAuthenticationFilter` extrae el token.
2. `JwtDecoder` verifica la firma y devuelve un objeto `Jwt`.
3. `JwtAuthenticationConverter` lo convierte en un `Authentication` con las authorities.
4. Se guarda en el `SecurityContext` del hilo.
5. `@PreAuthorize` lo consulta.
6. Al terminar la petición, se limpia (porque es stateless).

En el código se accede así:

```java
public StockMovementResponse create(@Valid @RequestBody StockMovementRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
    return stockService.registerMovement(request, resolveUsername(jwt));
}
```

`@AuthenticationPrincipal` inyecta directamente el JWT ya validado. De ahí sale el `preferred_username` que se guarda en `performed_by`.

**Punto importante:** el usuario no viene del body, viene del token. Un cliente no puede decir "esto lo hizo otro".

---

### P85. ¿Qué diferencia hay entre 401 y 403?

| Código | Significado | Cuándo lo devuelve |
|---|---|---|
| **401 Unauthorized** | No sé quién eres | Sin token, token vencido, firma inválida, issuer distinto |
| **403 Forbidden** | Sé quién eres, pero no puedes | Token válido sin el permiso requerido |

`viewer` haciendo `POST /api/v1/products` recibe **403**: está autenticado, le falta `product:manage`.

Verificado en [`security-smoke.sh`](../../../scripts/security-smoke.sh) y en `KeycloakSecurityIntegrationTest`.

---

### P86. ¿Por qué está desactivado CSRF? ¿No es una vulnerabilidad?

No, en este diseño no.

CSRF explota que el navegador **envía cookies automáticamente**. Aquí no hay cookie de sesión: el token va en la cabecera `Authorization`, que el navegador nunca añade solo. Un sitio malicioso no puede provocar una petición autenticada.

Está documentado en el código:

```java
// Stateless / no cookie session — CSRF no aplica (java:S4502)
.csrf(csrf -> csrf.disable()) // NOSONAR
```

El comentario `NOSONAR` con el código de la regla demuestra que la advertencia se analizó y se descartó con criterio, no que se ignoró.

---

### P87. ¿Dónde se guarda el token en el navegador? ¿Es seguro?

**En memoria del objeto `keycloak`**, en `keycloak.token`. Es el comportamiento por defecto de `keycloak-js` cuando no se configura un adaptador de almacenamiento, y [`keycloak.ts`](../../../frontend/src/auth/keycloak.ts) no configura ninguno:

```ts
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
});
```

**Ojo con esto, porque el profesor puede leerlo:** el comentario encima de esas líneas dice "Saves the token in the browser's local storage". Ese comentario es incorrecto. El token vive en memoria y se pierde al recargar; lo que permite no volver a escribir la contraseña es la **cookie de sesión de Keycloak** en su propio dominio, que `check-sso` consulta al arrancar. Si preguntan, la respuesta es esa, y conviene corregir el comentario.

**Riesgo real:** cualquier token accesible desde JavaScript es vulnerable a XSS. La mitigación es evitar el XSS: React escapa el contenido por defecto y no se usa `dangerouslySetInnerHTML` en ninguna parte del proyecto.

**Ventaja de tenerlo en memoria y no en `localStorage`:** en `localStorage` persiste tras cerrar la pestaña y queda expuesto a cualquier script del origen. En memoria, la superficie es menor.

**Alternativa más segura** que no se implementó: patrón BFF, con el token en una cookie `HttpOnly` que el JavaScript no puede leer. Es la respuesta correcta si preguntan cómo mejorarlo.

---

### P88. ¿Qué pasa cuando el token expira mientras el usuario trabaja?

Dos mecanismos:

**Preventivo** — antes de cada llamada:

```ts
await keycloak.updateToken(30);   // renueva si vence en menos de 30 s
```

**Reactivo** — si aun así expira:

```tsx
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => {
    keycloak.logout({ redirectUri: window.location.origin + "/" });
  });
};
```

Si el refresh token también venció, se cierra sesión limpiamente en vez de dejar la interfaz mostrando errores.

---

### P89. ¿La API pide un token a Keycloak en cada petición?

**No.** Ese es el punto clave del diseño con JWT.

- El **cliente** obtiene el token una vez, al iniciar sesión, y lo reutiliza.
- La **API** valida la firma localmente con la clave pública.
- Esa clave se descarga del `jwk-set-uri` y el Resource Server de Spring **la mantiene en caché**; no la pide en cada petición.

La alternativa sería introspección: consultar a Keycloak por cada petición. Sería más lento y convertiría a Keycloak en un cuello de botella. Se usa con tokens opacos, no con JWT.

Si se quisiera hacer explícita esa caché, se usaría Caffeine para el JWKS, pero sería reimplementar algo que Spring ya hace.

---

### P90. ¿Por qué el cliente `inventory-api` es confidencial si la API no lo usa para autenticarse?

Existe por dos motivos:

1. **Es el propietario de los permisos.** Los 7 permisos son *client roles* de `inventory-api`, y por eso aparecen en `resource_access["inventory-api"]`.
2. **Permite el password grant en automatización.** Tests, k6 y los scripts de smoke piden tokens con `client_id=inventory-api` + secreto, sin simular un navegador.

La API en sí no lo usa para autenticarse contra Keycloak: solo verifica firmas.

---

### P91. ¿Por qué el password grant si es un flujo desaconsejado?

Está desaconsejado **para usuarios finales** porque la aplicación tiene que manejar la contraseña. En este proyecto no se usa para eso: el usuario real siempre pasa por Authorization Code + PKCE.

El password grant solo aparece en automatización — tests de integración, k6 y scripts de smoke — donde no hay navegador y las credenciales son de usuarios de demostración. La alternativa sería automatizar el navegador para obtener un token, lo que haría los tests lentos y frágiles.

---

### P92. ¿Qué pasa si alguien modifica el token para darse permisos?

La validación de firma falla y la respuesta es 401.

El JWT está firmado con la clave privada de Keycloak. Cualquier cambio en el payload invalida la firma, y solo Keycloak tiene la clave privada para volver a firmarlo. La API únicamente tiene la pública, que sirve para verificar pero no para firmar.

**Demostración:** cambiar un carácter del token en el `curl` → 401.

---

### P93. ¿Por qué el usuario que ejecuta un movimiento sale del token y no del body?

Porque el body lo controla el cliente. Si `performedBy` viniera en el JSON, cualquiera podría registrar un movimiento a nombre de otra persona y la auditoría no valdría nada.

```java
static String resolveUsername(Jwt jwt) {
    if (jwt == null) return "system";
    String preferred = jwt.getClaimAsString("preferred_username");
    return preferred != null && !preferred.isBlank() ? preferred : jwt.getSubject();
}
```

El fallback a `getSubject()` cubre el caso de un token sin `preferred_username`, y `"system"` cubre el perfil `local` sin autenticación.

---

### P94. ¿Qué hace exactamente `hasAuthority` frente a `hasRole`?

`hasRole('X')` busca la authority `ROLE_X`: añade el prefijo automáticamente.
`hasAuthority('X')` busca exactamente `X`.

Como los permisos se llaman `product:view` y no `ROLE_product:view`, la anotación correcta es `hasAuthority`. Con `hasRole('product:view')` Spring buscaría `ROLE_product:view` y siempre daría 403.

---

### P95. ¿Cómo probaron que la seguridad funciona de verdad?

En cuatro niveles independientes:

| Nivel | Prueba | Qué demuestra |
|---|---|---|
| API simulada | `*ApiScenarioTest` con `@WithMockUser(authorities = …)` | Cada `@PreAuthorize` está bien puesto |
| Integración real | `KeycloakSecurityIntegrationTest` con Keycloak en Testcontainers | Tokens reales dan 401/403/200 correctos |
| E2E | `permissions.spec.ts` en navegador real | La UI y la API coinciden |
| Smoke ejecutable | `security-smoke.sh` | 11 asserts sobre el sistema desplegado, con evidencia en Markdown |

Evidencia versionada: [`EVIDENCIA-JWT-CORS-PERMISOS.md`](../testing/zap/EVIDENCIA-JWT-CORS-PERMISOS.md)

---

# E. Testing y calidad

### P35. Mostrar los tests de integración

Están en `src/test/java/.../application/integration/`. Usan **Testcontainers**: Docker levanta PostgreSQL 16 real y, para los de seguridad, Keycloak 26 real.

```java
@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory").withUsername("inventory").withPassword("inventory");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

Docker asigna un puerto aleatorio; `@DynamicPropertySource` se lo comunica a Spring en tiempo de ejecución.

Los 26 casos cubren: CRUD contra base real, movimientos de stock con transacciones, reportes con agregaciones SQL, constraints de integridad y seguridad con JWT real.

**Ejecutar:** `./gradlew integrationTest`

---

### P36 / P37. ¿Con qué herramientas se realizaron los tests?

| Herramienta | Tipo | Archivos |
|---|---|---|
| JUnit 5 | Motor de todas las pruebas Java | `src/test/java/**` |
| Mockito | Dobles de prueba en unitarias | `*ServiceTest` |
| MockMvc | Servidor HTTP simulado | `*ControllerTest`, `*ApiScenarioTest` |
| Spring Security Test | `@WithMockUser` | `*ApiScenarioTest` |
| Testcontainers | Postgres y Keycloak reales | `Abstract*IntegrationTest` |
| Playwright | E2E en navegador | `frontend/e2e/**` |
| k6 | Carga y estrés | `tests/k6/**` |
| OWASP ZAP | DAST | `scripts/zap-baseline.sh` |
| OWASP Dependency-Check | SCA | `scripts/dependency-check.sh` |
| JaCoCo | Cobertura | `build.gradle` |
| SonarCloud | Calidad estática | `build.gradle`, `sonar-project.properties` |
| bash + curl | Smokes | `scripts/*.sh` |

---

### P38. ¿Qué se modifica a nivel de base de datos y qué a nivel de código durante los tests?

**Base de datos:**

| Tipo de prueba | Base de datos |
|---|---|
| Unitarias | Ninguna. El repositorio está mockeado |
| MockMvc / API | Ninguna. El servicio está mockeado |
| Integración | PostgreSQL real y efímero en Docker; Flyway aplica las 3 migraciones; el contenedor se destruye al terminar |
| E2E | La base del ambiente que corresponda (dev o staging) |

**Nada toca la base de datos de desarrollo del programador.** Testcontainers crea una nueva y la borra.

**Código:**

| Qué se sustituye | Cómo |
|---|---|
| Repositorios | `@Mock` de Mockito |
| Seguridad | `ApiTestSecurityConfig` + `@WithMockUser` |
| URLs de conexión | `@DynamicPropertySource` |
| Exportación OTel | Desactivada en `application-integration.yml` |

Ese último detalle importa: sin apagar OpenTelemetry, cada test intentaría enviar trazas a Alloy, que no existe, y se llenaría de errores.

```yaml
management:
  tracing: { enabled: false }
  otlp:
    metrics:
      export: { enabled: false }
```

---

### P39. Explicar para qué sirve cada tipo de test

| Tipo | Pregunta que responde | Velocidad | Qué no ve |
|---|---|---|---|
| Unitaria | ¿La regla de negocio es correcta? | ms | SQL, HTTP, seguridad real |
| MockMvc / API | ¿La ruta, el código y el permiso son correctos? | ms | Que el JWT real funcione |
| Contrato | ¿La API documentada coincide con la implementada? | ms | Comportamiento |
| Integración | ¿Funciona contra Postgres y Keycloak reales? | minutos | El navegador |
| E2E | ¿El usuario puede completar su tarea? | minutos | Rendimiento |
| Carga (k6) | ¿Aguanta el tráfico esperado? | minutos | Corrección |
| Estrés (k6) | ¿Dónde se rompe? | minutos | Corrección |
| SCA | ¿Uso dependencias con CVEs? | minutos | Fallos propios |
| DAST | ¿La app expuesta tiene problemas evidentes? | minutos | Lógica de negocio |
| Exploratoria | ¿Qué no se me ocurrió automatizar? | manual | Regresión |

La pirámide se respeta: muchas rápidas abajo, pocas lentas arriba.

---

### P40. Mostrar la ejecución de los tests

```bash
./gradlew test              # 36
./gradlew apiTest           # 31
./gradlew contractTest      # 2
./gradlew integrationTest   # 26  (necesita Docker)
./gradlew jacocoTestReport  # cobertura
```

Reportes:

- HTML de JUnit: `build/reports/tests/test/index.html`
- Cobertura: `build/reports/jacoco/test/html/index.html`
- Playwright: `frontend/playwright-report/index.html`

En Jenkins se ve directamente en la pestaña *Test Result* del build.

---

### P41. Provocar una falla y demostrar cómo responden los tests

**Opción rápida y segura (recomendada).** En `StockService`, invertir la comparación:

```java
// original
if (quantity > before) throw new InsufficientStockException(...);
// romper
if (quantity < before) throw new InsufficientStockException(...);
```

`./gradlew test` → falla `registerOutMovement_decreasesStock` y también `registerOutMovement_insufficientStockThrows`. Se revierte con Cmd+Z.

**Opción de base de datos.** Añadir un campo a `Product` sin crear la migración: la aplicación no arranca porque `ddl-auto: validate` detecta la diferencia. Demuestra que el esquema está gobernado.

**Opción de seguridad.** Quitar el `@PreAuthorize` de `POST /api/v1/products`: falla el escenario de API que espera 403 para `viewer` y falla `security-smoke.sh`.

Ten el cambio preparado en un archivo aparte para no improvisar en vivo.

---

### P42. Mostrar los tests desde GitHub

Repositorio → pestaña **Actions** → **DevSecOps Pipeline** → último run.

Qué señalar:

1. El grafo de jobs: `build-and-test` arriba y cuatro jobs en paralelo debajo.
2. Dentro de `build-and-test`, los pasos separados: *Unit tests*, *Integration tests*, *API scenario tests*, *Contract tests*.
3. Los artefactos descargables: `jacoco-report`, `dependency-check-report`, `zap-security-reports`, `post-deploy-evidence`.
4. En un pull request, los checks obligatorios antes de poder mezclar.

---

### P96. ¿Por qué separar `test`, `apiTest`, `contractTest` e `integrationTest`?

```groovy
tasks.named('test') {
    useJUnitPlatform { excludeTags 'integration', 'api', 'contract' }
}
```

Tres razones:

1. **Velocidad de desarrollo.** `./gradlew test` tarda segundos y no pide Docker; se corre constantemente.
2. **Diagnóstico.** El pipeline muestra exactamente qué categoría falló.
3. **Orden.** `shouldRunAfter` ejecuta primero lo barato: si un unitario falla, no tiene sentido levantar contenedores.

---

### P97. ¿Qué cobertura tienen y por qué ese umbral?

El umbral obligatorio es 60 % de líneas **en las clases de servicio**, excluyendo `AuditService`:

```groovy
rule {
    element = 'CLASS'
    includes = ['icc354.pucmm.proyectoqa.application.service.*']
    excludes = ['icc354.pucmm.proyectoqa.application.service.AuditService']
    limit { counter = 'LINE'; minimum = 0.60 }
}
```

**Por qué solo servicios:** son las clases con lógica. Cubrir DTOs y configuración sube el porcentaje sin aumentar la confianza.

**Por qué se excluye `AuditService`:** depende de `AuditReader` de Envers, que necesita una sesión real de Hibernate. Se cubre con pruebas de integración, no unitarias.

**Por qué 60 % y no 90 %:** un umbral inalcanzable termina desactivándose o se llena de pruebas triviales. 60 % obliga a cubrir los caminos importantes y se cumple de verdad en cada build.

**Cobertura real medida:** 86.2 % de líneas y 57.3 % de ramas en el proyecto completo; los servicios están en 85.9 %, muy por encima del umbral.

---

### P97b. ¿Por qué la cobertura en Sonar estaba en 34 % y qué hicieron?

Vale la pena saber contar esta historia, porque demuestra capacidad de diagnóstico y no solo de configuración.

**El síntoma:** SonarCloud mostraba 34.4 % de cobertura mientras el reporte local de JaCoCo decía 68 %. Dos números que no cuadran significan que alguien está midiendo mal.

**Causa 1: Sonar no encontraba el fuente de los servicios.** JaCoCo reporta la cobertura por nombre de paquete. Los servicios declaraban `package icc354.pucmm.proyectoqa.application.service`, pero los archivos vivían en la carpeta `src/main/java/icc354/pucmm/proyectoqa/service/`. Sonar buscaba el `.java` en la ruta que dicta el paquete, no lo encontraba, descartaba esa cobertura y contaba las líneas como no cubiertas. Los servicios son 213 de las 465 líneas ejecutables del proyecto: perderlos explica casi todo el hueco.

**Causa 2: las pruebas de integración no contaban.** El reporte solo agregaba dos de los archivos de ejecución:

```groovy
executionData.setFrom(fileTree(...).include('test.exec', 'apiTest.exec'))
```

`integrationTest.exec` existía y era el más grande de los tres, pero quedaba fuera. Las 26 pruebas con Testcontainers levantan Postgres y Keycloak reales y recorren servicios, entidades y la configuración de seguridad, y nada de eso llegaba al número.

**Las correcciones.** Se movieron los 12 archivos para que la carpeta coincida con el paquete —sin tocar una sola línea de código, porque los `package` ya eran correctos— y el reporte pasó a agregar todos los `.exec`:

```groovy
executionData.setFrom(fileTree(layout.buildDirectory.dir('jacoco')).include('*.exec'))
```

**El resultado:**

| Métrica | Antes | Después |
|---|---|---|
| Líneas | 68.2 % | **86.2 %** |
| Ramas | 46.7 % | **57.3 %** |
| `config` (seguridad) | 0 % | **87.1 %** |
| `domain.entity` | 86.4 % | **96.3 %** |
| Estimado en Sonar | 34.4 % | **~82 %** |

`config` pasa de cero a 87 % porque `DockerSecurityConfig` solo se ejercita cuando hay un Keycloak real, y eso solo ocurre en las pruebas de integración.

---

### P98. ¿Qué son los tests de contrato?

Verifican que **la superficie HTTP publicada sigue siendo la acordada**. Son 2 casos en [`OpenApiContractTest`](../../../src/test/java/icc354/pucmm/proyectoqa/application/contract/OpenApiContractTest.java):

**1. Las rutas base no cambian.** Por reflexión, lee el `@RequestMapping` de cada controlador y comprueba el valor esperado:

```java
@Test
void controllersExposeCoreApiPaths() {
    assertPath(ProductController.class,  "/api/v1/products");
    assertPath(StockController.class,    "/api/v1/stock/movements");
    assertPath(ReportController.class,   "/api/v1/reports");
    assertPath(CategoryController.class, "/api/v1/categories");
    assertPath(AuditController.class,    "/api/v1/audit/products");
}
```

**2. La colección de post-deploy sigue alineada** con esas rutas:

```java
Path collection = Path.of("docs/final/ci/post-deploy-smoke.collection.json");
assertTrue(Files.isRegularFile(collection), "Newman collection missing: " + collection);
String json = Files.readString(collection);
assertTrue(json.contains("/actuator/health"));
assertTrue(json.contains("/api/v1/products"));
```

**Por qué importa:** si alguien renombra `/api/v1/products` a `/api/products`, el frontend, los scripts de smoke y la colección de Newman se rompen a la vez, pero cada uno fallaría en un momento distinto y lejos de la causa. Este test convierte ese cambio en un fallo inmediato del build, con un mensaje que dice exactamente qué controlador cambió.

**Alcance honesto:** valida las rutas base y la colección, no el esquema completo de cada respuesta. Un contrato más estricto compararía el `openapi.json` generado contra una versión de referencia.

---

### P99. ¿Por qué Testcontainers y no una base H2 en memoria?

H2 no es PostgreSQL. Concretamente:

| Se rompería | Motivo |
|---|---|
| `ILIKE` | No existe en H2 |
| `TIMESTAMPTZ` | Semántica distinta |
| `BIGSERIAL` y secuencias | Comportamiento diferente |
| `CHECK` con `IN (...)` | Soporte parcial |
| Migraciones de Flyway | Podrían no aplicarse igual |

Probar contra H2 y desplegar en Postgres es probar un sistema distinto del que se despliega. Testcontainers usa exactamente `postgres:16-alpine`, la misma imagen del Compose.

**Costo asumido:** los tests de integración tardan minutos y exigen Docker. Por eso están en una tarea aparte.

---

### P100. ¿Por qué los tests E2E usan `data-testid`?

```tsx
<button data-testid="create-product-button">Crear producto</button>
```

Un selector por clase CSS o por texto se rompe con cada cambio de diseño o de idioma. `data-testid` es un contrato explícito entre la interfaz y las pruebas: cambiar el color, el layout o el texto no rompe nada.

---

### P101. ¿Cómo se garantiza que los E2E no dependan entre sí?

`fullyParallel: false` en la configuración de Playwright, y cada test prepara sus propios datos:

```ts
const sku = overrides.sku ?? `E2E-API-${Date.now()}`;
```

El SKU lleva marca de tiempo, así que dos ejecuciones nunca chocan con el constraint `UNIQUE`. La preparación se hace por API, no por interfaz, porque es más rápido y no se está probando el formulario en ese momento.

---

### P102. ¿Qué diferencia hay entre la prueba de carga y la de estrés?

| | Carga | Estrés |
|---|---|---|
| Usuarios | rampa a 15 | picos hasta 80 |
| p95 | < 500 ms | < 2000 ms |
| Fallos | < 1 % | < 5 % |
| Pregunta | ¿Cumple con el tráfico esperado? | ¿Dónde empieza a degradarse? |

Los umbrales del estrés son más permisivos a propósito: se acepta que sea lento bajo un pico, no que empiece a devolver errores.

Los umbrales están dentro del script, así que k6 devuelve error si no se cumplen y el pipeline lo detecta automáticamente.

**Mostrar:** [`load-products.js`](../../../tests/k6/load-products.js) y [`stress-products.js`](../../../tests/k6/stress-products.js)

---

### P103. ¿Qué encontró ZAP y qué hicieron con eso?

Escaneo baseline contra Swagger UI: 60 reglas en PASS y 7 en WARN. Los WARN son cabeceras de endurecimiento (`Content-Security-Policy`, `X-Frame-Options`, `Permissions-Policy`) que normalmente se añaden en el proxy inverso o el ingress delante de la aplicación.

Se ejecuta con `-I`, lo que evita que un WARN tumbe el pipeline, pero el reporte queda archivado como evidencia. **Es una decisión consciente, no un descuido.**

**Mostrar:** [`docs/final/testing/zap/`](../testing/zap/)

**Y si preguntan por Dependency-Check: hoy tampoco bloquea el build.** Está en [`build.gradle`](../../../build.gradle) y conviene tenerlo claro antes de que lo lean ellos:

```groovy
dependencyCheck {
    formats = ['HTML', 'JSON']
    outputDirectory = "$rootDir/docs/final/testing/dependency-check"
    failBuildOnCVSS = 11
    failOnError = false
    autoUpdate = System.getenv('DEPENDENCY_CHECK_AUTO_UPDATE') == 'true'
}
```

La escala CVSS llega hasta 10, así que un umbral de **11 significa que ninguna vulnerabilidad hace fallar el build**: el análisis informa, no bloquea. Lo mismo pasa en Jenkins, donde [`Jenkinsfile`](../../../infra/jenkins/Jenkinsfile) fija `DEPENDENCY_CHECK_SOFT_FAIL = 'true'` porque la base NVD del agente suele estar vacía y una sincronización completa tarda entre 30 y 90 minutos.

**Cómo defenderlo:** "Es un umbral de arranque. Queríamos el reporte en cada build sin que un CVE en una dependencia transitiva de Spring bloqueara el trabajo del equipo mientras aprendíamos la herramienta. El paso siguiente, y sé exactamente cuál es, es bajarlo a `failBuildOnCVSS = 7` y mantener un archivo de supresiones para los falsos positivos justificados." Eso es mucho mejor que descubrirlo en vivo.

---

### P104. ¿Qué son las pruebas exploratorias y por qué las incluyeron?

Sesiones manuales con objetivo definido (charters), documentadas con evidencia:

| Charter | Foco |
|---|---|
| EX-01 | Casos límite de stock |
| EX-02 | Permisos por rol |
| EX-03 | Experiencia de usuario en productos y dashboard |

**Por qué:** la automatización solo comprueba lo que alguien pensó de antemano. La exploración encuentra lo que no se le ocurrió a nadie: mensajes confusos, estados intermedios raros, comportamientos inesperados. Es complementaria, no sustituta.

**Mostrar:** [`docs/final/testing/exploratory/`](../testing/exploratory/)

---

# F. CI/CD

### P43. Mostrar los workflows de GitHub Actions

Cinco archivos en [`.github/workflows/`](../../../.github/workflows/):

| Workflow | Disparador | Estado |
|---|---|---|
| `devsecops.yml` | push y PR a `develop`, manual | **Principal** |
| `conventional-commits.yml` | PR a `develop` y `main` | Activo |
| `ci.yml` | manual | Respaldo |
| `security.yml` | manual | Respaldo |
| `post-deploy-staging.yml` | manual | Respaldo |

Los tres últimos quedaron como `workflow_dispatch` porque su contenido se consolidó en el pipeline principal. Se conservan por si se necesita ejecutar una parte aislada sin correr todo.

---

### P44. Explicar los workflows definidos

**`devsecops.yml`** — seis jobs:

| Job | Depende de | Qué hace |
|---|---|---|
| `build-and-test` | — | build, unit, integration, api, contract, JaCoCo, Sonar |
| `docker-images` | build-and-test | Construye las imágenes de API y frontend con caché de GHA |
| `dependency-check` | build-and-test | SCA con caché de la base NVD |
| `zap-baseline` | build-and-test | Levanta el stack, corre smoke de seguridad y ZAP, y lo desmonta |
| `staging-deploy-e2e` | build-and-test | Despliega staging, espera, smoke post-deploy y Playwright |
| `quality-gate` | los cinco | Falla si alguno no fue `success` |

El `quality-gate` es la pieza que ata todo:

```bash
for r in "${{ needs.build-and-test.result }}" ... ; do
  if [ "$r" != "success" ]; then
    echo "::error::Quality gate FAILED (stage result=$r)"
    exit 1
  fi
done
```

Con `if: always()` corre aunque otro job falle, para poder reportar cuál fue.

**`conventional-commits.yml`** valida con una expresión regular que cada commit del PR siga `tipo(scope): mensaje`, ignorando merges y reverts. Hay un hook local equivalente en [`.githooks/commit-msg`](../../../.githooks/commit-msg) para detectarlo antes de empujar.

---

### P45. ¿Cómo interactúan GitHub Actions y Jenkins?

**No interactúan.** Son dos implementaciones independientes del mismo pipeline.

| | GitHub Actions | Jenkins |
|---|---|---|
| Disparo | Automático por push y PR | Manual |
| Ejecución | Runners en la nube | Docker local |
| Función | Guardián del repositorio | Demostración de portabilidad |

**Por qué mantener los dos:** el requisito del curso pedía ambos, y la razón de fondo es válida — un pipeline que solo funciona en un SaaS específico es un pipeline atado a un proveedor. Tener el mismo proceso corriendo en Jenkins local demuestra que el proceso es la propiedad valiosa, no la herramienta.

Ambos ejecutan los mismos scripts (`security-smoke.sh`, `post-deploy-smoke.sh`, `zap-baseline.sh`), así que la lógica está escrita una sola vez.

---

### P46. Explicar el pipeline de CI/CD completo

```
Desarrollador → rama feature/XXX
       │
       ├── hook commit-msg valida el mensaje
       ▼
   push → PR a develop
       │
       ├── conventional-commits.yml   valida todos los commits
       └── devsecops.yml
              │
              ├── build-and-test → unit → integration → api → contract → JaCoCo → Sonar
              │
              ├── docker-images        (paralelo)
              ├── dependency-check     (paralelo)
              ├── zap-baseline         (paralelo)
              └── staging-deploy-e2e   (paralelo)
                        │
                        └── quality-gate
                                 ▼
                    Merge a develop → luego PR a main
```

**Lo que hace que sea DevSecOps y no solo CI:** la seguridad no es una fase final, está distribuida. SCA y DAST corren en paralelo con las pruebas, y hay un smoke de permisos que se ejecuta contra el sistema desplegado, no solo contra código.

**Y hay despliegue de verdad:** `staging-deploy-e2e` levanta el stack completo de staging con Compose, espera a que esté sano, corre el smoke y los E2E contra él, y lo desmonta.

**Mostrar:** [`PIPELINE.md`](../ci/PIPELINE.md)

---

### P47. Validar Jenkins

Abrir http://localhost:8082 → job del proyecto → último build en verde.

Qué señalar:

1. **Los 10 stages** en la vista de etapas, con su duración.
2. **Test Result: 95 tests**, cero fallos.
3. **Artefactos archivados:** reportes de JaCoCo, Dependency-Check, ZAP y resultados de Playwright.
4. **El stage Security**, que levanta un stack completo, lo escanea y lo desmonta.
5. **El stage E2E**, con Playwright corriendo contra staging.

Evidencia versionada: [`EVIDENCIA-JENKINS.md`](../ci/EVIDENCIA-JENKINS.md) y la captura [`Jenkins Funcionando.png`](../ci/Jenkins%20Funcionando.png)

---

### P105. ¿Por qué el pipeline se dispara en `develop` y no en `main`?

Flujo de ramas: `feature/*` → `develop` → `main`.

`develop` es la rama de integración: ahí es donde puede entrar código roto y donde hay que detenerlo. Cuando `develop` está verde, el PR a `main` es una promoción de código ya validado.

`main` está protegida y exige revisión de al menos una persona.

---

### P106. ¿Qué pasa si el pipeline falla en un pull request?

El check aparece en rojo en el PR y no se puede mezclar. Hay que corregir y volver a empujar.

Excepciones conscientes: el análisis de Sonar en `develop` se tolera si el plan Free lo rechaza, y ZAP corre con `-I` para no bloquear por advertencias de cabeceras. Ambas están comentadas en el propio código del workflow, no ocultas.

---

### P107. ¿Por qué fijar las acciones de Docker por SHA en vez de por tag?

```yaml
uses: docker/build-push-action@10e90e3645eae34f1e60eeb005ba3a3d33f178e8
```

Un tag como `v6` se puede reapuntar a otro commit. Si alguien compromete el repositorio de la acción y reescribe el tag, el pipeline ejecutaría código distinto sin que nada cambie en el repo. Un SHA es inmutable.

Lo señaló Sonar con la regla `githubactions:S7637` y se aplicó.

---

### P108. ¿Cómo funciona la caché en el pipeline y por qué importa?

Tres cachés:

| Caché | Configuración | Ahorro |
|---|---|---|
| Dependencias Gradle | `cache: gradle` en `setup-java` | Evita bajar el mundo en cada run |
| Capas de Docker | `cache-from/to: type=gha` | Reutiliza capas entre builds |
| Base NVD | `actions/cache` sobre `~/.gradle/dependency-check-data` | Sin ella, Dependency-Check tarda 40–90 minutos |

La de NVD es la crítica: la base de vulnerabilidades pesa gigabytes y descargarla cada vez haría el job inviable.

---

### P109. ¿Qué son los Conventional Commits y por qué los exigen?

Formato: `tipo(scope): descripción`, con tipos `feat`, `fix`, `test`, `docs`, `chore`, `ci`, `refactor`, `perf`, `style`.

Tres beneficios: el historial se lee de un vistazo, se puede generar un changelog automáticamente, y se puede deducir el tipo de versión (una `feat` es minor, un `fix` es patch).

Se valida en dos puntos: el hook local avisa antes de empujar y el workflow bloquea el PR si algo se coló.

---

# G. Observabilidad

### P48. Validar Grafana

http://localhost:3001 con `admin` / `admin`.

Cinco dashboards provisionados automáticamente desde [`infra/grafana/dashboards/`](../../../infra/grafana/dashboards/) — no se crearon a mano en la interfaz, están versionados como JSON.

Tres fuentes de datos, también provisionadas: Prometheus, Tempo y Loki.

**El más vistoso para la defensa:** *Security — 401, 403 y auth failures*. Se generan unos 403 con `viewer` y el panel sube en vivo.

---

### P49. ¿Qué métricas se monitorean?

| Categoría | Métricas |
|---|---|
| Tráfico | `http_server_requests_seconds_count` — throughput global y por URI |
| Latencia | `http_server_requests_seconds_sum/count` (media) y `_bucket` (percentiles) |
| Errores | Mismas métricas filtradas por `status=~"4.."` y `"5.."` |
| Seguridad | Filtradas por `status="401"` y `"403"` |
| JVM | `jvm_memory_used_bytes`, `jvm_threads_live_threads`, `jvm_gc_pause_seconds_count` |
| Sistema | `process_cpu_usage`, `system_cpu_usage` |
| Base de datos | `hikaricp_connections_active/idle/pending/max` |
| Disponibilidad | `up{job="inventory-api"}` |
| Negocio | Tráfico por dominio: `uri=~"/api/v1/(products\|stock\|reports).*"` |

Las genera Micrometer automáticamente y las expone Actuator en `/actuator/prometheus`. Prometheus las recoge cada 15 segundos.

Ejemplo de consulta que puedes explicar:

```promql
sum(rate(http_server_requests_seconds_sum[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
```

Suma de tiempo dividida por número de peticiones, ambas como tasa por segundo en ventana de 5 minutos: la latencia media.

---

### P50. ¿Cómo se visualizan logs y métricas?

Todo en Grafana, con tres fuentes de datos y **correlación entre ellas**.

El truco está en dos configuraciones que se complementan:

```yaml
# application.yml — cada log lleva el id de traza
logging:
  pattern:
    correlation: "[%X{traceId:-},%X{spanId:-}] "
```

```yaml
# datasources.yml — Grafana convierte ese texto en un enlace
derivedFields:
  - name: TraceID
    matcherRegex: "\\[([0-9a-fA-F]{16,32}),"
    datasourceUid: tempo
    urlDisplayLabel: "View Trace"
```

Flujo de una investigación real:

```
Alerta de 5xx en Grafana
   → panel de errores, se identifica la URI
   → Loki: logs de esa ventana con nivel ERROR
   → clic en "View Trace" en el log
   → Tempo: traza completa con los spans de SQL
   → se ve qué consulta tardó o falló
```

Eso es observabilidad: no solo saber que algo falla, sino poder llegar a la causa sin entrar por SSH a ningún servidor.

**Para la demo con el profesor:** abre el dashboard **Observabilidad — Métricas, Logs y Trazas** ([`observability.json`](../../../infra/grafana/dashboards/observability.json)). Ahí están las tres señales juntas: Prometheus arriba, Loki (logs) en el medio y Tempo (trazas) abajo. Genera tráfico antes (UI o curl con JWT) y refresca.

Los otros cinco tableros siguen siendo principalmente PromQL (métricas). Explore sigue disponible para consultas ad hoc.

La etiqueta `level` en logs existe porque Alloy la extrae del texto:

```river
loki.process "api" {
  stage.regex {
    expression = `(?P<level>ERROR|WARN|INFO|DEBUG|TRACE)`
  }
  stage.labels {
    values = { level = "level" }
  }
}
```

En el dashboard, el panel de logs usa `{job="inventory-api"}` y el de errores `{job="inventory-api"} |~ "(?i)ERROR"`.

Los logs llegan a Loki a través de Alloy, que los lee del socket de Docker:

```
discovery.docker "containers" {
  host = "unix:///var/run/docker.sock"
  filter { name = "name"  values = ["inventory-api"] }
}
```

---

### P51. ¿Cómo manejamos la caída de un servicio?

**Respuesta honesta: la resiliencia es de infraestructura, no de código. No hay circuit breaker.**

Lo que sí hay:

| Mecanismo | Dónde | Qué logra |
|---|---|---|
| Healthcheck del contenedor | `Dockerfile`, `docker-compose.yml` | Docker sabe si la API responde |
| `depends_on: service_healthy` | Compose | La API no arranca antes que Postgres |
| `restart: unless-stopped` | staging y prod | Reinicio automático al caer |
| Alerta `InventoryApiDown` | `alerts.yml` | Avisa si `up == 0` por 1 minuto |
| Alerta `HighHttp5xxErrorRate` | `alerts.yml` | Avisa si los 5xx superan el 5 % |
| Probes de Kubernetes | `application-prod.yml` con `probes.enabled: true` | Listo para orquestadores |
| Espera activa en el pipeline | `wait-for-stack.sh` | El despliegue no continúa hasta que todo está sano |

**Qué falta y cómo lo diría:** "No implementamos Resilience4j. Con un solo backend y una sola base de datos, un circuit breaker no tendría a qué hacerle fallback: si Postgres cae, no hay respuesta degradada posible. Donde sí tendría sentido es entre la API y Keycloak — si Keycloak no responde, la validación de JWT sigue funcionando gracias a la caché de claves públicas, pero un breaker evitaría reintentos innecesarios."

Esa respuesta reconoce el límite y demuestra que se entiende el concepto.

---

### P110. ¿Qué es OpenTelemetry y qué papel juega?

Un estándar abierto para generar métricas, trazas y logs sin atarse a un proveedor. Spring Boot lo integra con `spring-boot-starter-opentelemetry`.

La API exporta por OTLP a Alloy; Alloy reenvía a Tempo y Loki. Si mañana se cambia Tempo por Jaeger o Datadog, se cambia la configuración de Alloy y la aplicación no se toca. Ese es exactamente el beneficio del estándar.

---

### P111. ¿Qué es Grafana Alloy y por qué no enviar directo a Tempo?

Es un collector: recibe en un punto y distribuye a varios destinos.

Ventajas concretas:

1. **La aplicación conoce un solo endpoint.** Cambiar el backend de trazas no la afecta.
2. **Procesa por lotes** (`otelcol.processor.batch`), lo que reduce el número de conexiones.
3. **Recoge también los logs de Docker**, algo que la aplicación no puede hacer por sí sola.
4. **Permite filtrar o enriquecer** antes de almacenar.

```
otelcol.receiver.otlp → otelcol.processor.batch → otelcol.exporter.otlp (Tempo)
                                                → otelcol.exporter.loki (Loki)
```

---

### P112. ¿Por qué el muestreo de trazas es 100 % en desarrollo y 10 % en producción?

```yaml
probability: ${OTEL_TRACES_SAMPLING:1.0}   # 0.1 en prod
```

En desarrollo y demo se quiere ver cada petición. En producción, con tráfico real, guardar el 100 % consume ancho de banda y almacenamiento sin aportar: para detectar tendencias basta una muestra. Si hace falta investigar un caso concreto, se sube temporalmente.

---

### P113. ¿Se ven las consultas SQL en las trazas?

Sí, gracias a `datasource-micrometer`:

```groovy
implementation 'net.ttddyy.observation:datasource-micrometer-spring-boot:2.2.1'
```

```yaml
jdbc:
  includes: query, fetch
```

Cada traza incluye los spans de las consultas ejecutadas. Se excluye `connection` a propósito, porque genera ruido sin información útil.

Con eso se detecta un N+1 mirando una traza: en vez de un span de SQL se ven veinte.

---

# H. Demostraciones prácticas

### P52. Mostrar cómo se renderiza la lista de datos en el frontend

Con DevTools abierto en la pestaña **Network**:

1. Ir a `/products`.
2. Señalar la petición `GET /api/v1/products?page=0&size=10&sort=name,asc`.
3. Pestaña *Headers* → `Authorization: Bearer eyJ...`.
4. Pestaña *Response* → el `PageResponse` con `content`, `totalElements`, `totalPages`.
5. Cambiar de página → nueva petición con `page=1`.
6. Hacer clic en la cabecera *Precio* → nueva petición con `sort=price,asc`.

**Lo que demuestra:** el orden y la paginación los hace la base de datos, no JavaScript filtrando un array en memoria.

---

### P53. Mostrar el flujo completo desde el frontend hasta la base de datos

Recorrer estos siete archivos en orden, con el navegador al lado:

| # | Archivo | Qué señalar |
|---|---|---|
| 1 | [`Products.tsx`](../../../frontend/src/pages/Products.tsx) | `loadProducts()` con el estado de filtros |
| 2 | [`api/products.ts`](../../../frontend/src/api/products.ts) | Construcción de la query string |
| 3 | [`api/client.ts`](../../../frontend/src/api/client.ts) | `updateToken(30)` y cabecera `Authorization` |
| 4 | [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java) | Validación del JWT y conversión de roles |
| 5 | [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java) | `@PreAuthorize` y `Pageable` |
| 6 | [`ProductService.java`](../../../src/main/java/icc354/pucmm/proyectoqa/application/service/ProductService.java) | Patrones LIKE y mapeo a DTO |
| 7 | [`ProductRepository.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/repository/ProductRepository.java) | La consulta nativa con `ILIKE` |

Y cerrar con la base de datos:

```bash
docker exec -it inventory-postgres psql -U inventory -d inventory \
  -c "SELECT id, name, sku, quantity FROM products ORDER BY name LIMIT 5;"
```

---

### P54. Mostrar cómo viajan los roles y permisos desde Keycloak hasta la aplicación

Los cuatro saltos, en orden:

**1. Definidos en el realm** — [`inventory-realm.json`](../../../keycloak/inventory-realm.json):

```json
"clientRoles": { "inventory-api": ["product:view", "stock:view"] }
```

**2. Emitidos en el token.** Pedir un token por consola y pegarlo en jwt.io:

```json
"resource_access": { "inventory-api": { "roles": ["product:view", "stock:view"] } }
```

**3. Convertidos en authorities** — `DockerSecurityConfig.extractAuthorities`:

```java
Object clientAccess = resourceAccess.get(clientId);
if (clientAccess instanceof Map<?, ?> clientMap) {
    Object clientRoles = clientMap.get("roles");
    if (clientRoles instanceof List<?> list) list.forEach(role -> roles.add(String.valueOf(role)));
}
return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
```

**4. Evaluados en el endpoint:**

```java
@PreAuthorize("hasAuthority('product:manage')")
```

**Y en el frontend, el mismo token:**

```tsx
keycloak.hasResourceRole(role, "inventory-api")
```

---

### P55. Mostrar el flujo completo de autenticación y autorización

```
AUTENTICACIÓN (¿quién eres?)
  Landing → login → Keycloak (dominio distinto) → credenciales
  → code + PKCE → access_token firmado → guardado por keycloak-js

AUTORIZACIÓN (¿qué puedes hacer?)
  UI:  ProtectedRoute + hasResourceRole → oculta o redirige
  API: filtro JWT → extractAuthorities → @PreAuthorize → 200 o 403
```

**La frase que resume:** "La UI evita frustración; la API evita el acceso. Sin la segunda, la primera no sirve de nada."

---

### P56. Demostrar el funcionamiento de Login y Seguridad

Guion de tres minutos:

1. Login como `admin` → aparece el botón *Crear producto*, el dashboard abre.
2. Cerrar sesión, entrar como `viewer` → no hay botón *Crear*, `/dashboard` redirige a *unauthorized*.
3. **La parte importante:** en terminal, demostrar que no es solo la UI.

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/inventory/protocol/openid-connect/token \
  -d grant_type=password -d client_id=inventory-api -d client_secret=inventory-api-secret \
  -d username=viewer -d password=viewer | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

# 200 — tiene product:view
curl -s -o /dev/null -w "GET  products: %{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products

# 403 — le falta product:manage
curl -s -o /dev/null -w "POST products: %{http_code}\n" -X POST \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"x","sku":"DEMO-1","price":1,"quantity":1,"minStock":0,"active":true}' \
  http://localhost:8080/api/v1/products

# 401 — sin token
curl -s -o /dev/null -w "Sin token:     %{http_code}\n" http://localhost:8080/api/v1/products
```

4. Cerrar con `./scripts/security-smoke.sh`, que hace esto y ocho comprobaciones más, y genera evidencia en Markdown.

---

### P57. Mostrar el esquema de la base de datos

Tres formas, de más a menos preparación:

**1. El archivo versionado** (la mejor, porque demuestra que el esquema es código):
[`V1__init_schema.sql`](../../../src/main/resources/db/migration/V1__init_schema.sql)

**2. En vivo con psql:**

```bash
docker exec -it inventory-postgres psql -U inventory -d inventory
\dt
\d products
\d stock_movements
SELECT * FROM flyway_schema_history;
```

Esa última consulta es un buen detalle: muestra las tres migraciones aplicadas con su checksum y fecha.

**3. Las entidades JPA:** [`Product.java`](../../../src/main/java/icc354/pucmm/proyectoqa/domain/entity/Product.java)

---

### P58. Mostrar el cliente HTTP utilizado para consumir la API

[`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts) — 67 líneas que concentran todo el consumo de la API.

Cuatro cosas que señalar:

1. **Renovación del token** antes de cada llamada, solo si hace falta.
2. **Cabecera `Authorization`** añadida en un único lugar.
3. **204 sin cuerpo** tratado aparte, porque `response.json()` fallaría.
4. **`ApiError`** que conserva el status y los `fieldErrors` del backend.

Y mostrar cómo lo usan los módulos:

```ts
export function listProducts(params: ListProductsParams = {}) {
  return apiFetch<PageResponse<ProductResponse>>(`/api/v1/products?${toQuery(params)}`);
}
```

**Por qué no axios:** `fetch` es nativo del navegador, no añade peso al bundle y cubre todo lo que se necesita. Añadir una dependencia debe justificarse.

---

# I. Preguntas difíciles

### P114. Si tuvieras que llevar esto a producción real mañana, ¿qué cambiarías?

Por orden de prioridad:

1. **HTTPS en todo**, con proxy inverso o ingress delante de API, frontend y Keycloak.
2. **Keycloak en modo `start`** con base de datos propia y hostname fijo, no `start-dev`.
3. **Secretos en un gestor** (Vault o AWS Secrets Manager) y rotación del secreto del cliente.
4. **Registro de imágenes** con etiquetas versionadas, no builds locales.
5. **Orquestador** (Kubernetes) con réplicas; las probes ya están habilitadas en `application-prod.yml`.
6. **Backups automatizados** de PostgreSQL con prueba de restauración.
7. **Bloqueo optimista** con `@Version` en `Product`.
8. **Cabeceras de seguridad** (CSP, HSTS, X-Frame-Options) en el proxy — precisamente los WARN de ZAP.
9. **Alertas con destino real** (correo, Slack); hoy Alertmanager está configurado pero sin receptor productivo.

---

### P115. ¿Qué fue lo más difícil del proyecto?

**El desajuste del issuer de Keycloak.** Los tokens daban 401 sin razón aparente.

La causa: el claim `iss` debe coincidir exactamente con el issuer configurado. El token se pedía desde el navegador a `localhost:8081`, pero desde Jenkins se pedía a `host.docker.internal:8181`. Mismo Keycloak, dos issuers distintos.

Se resolvió en tres frentes:

1. Fijar `KC_HOSTNAME` en el Compose de staging.
2. Separar issuer (nombre externo) de JWKS (nombre interno de la red Docker).
3. En los tests, usar `KEYCLOAK.getHost()` en vez de escribir `localhost`, para que funcione dentro y fuera de Jenkins.

Lo que se aprendió: en sistemas distribuidos, la identidad de red es parte del contrato de seguridad, no un detalle de configuración.

---

### P116. ¿Por qué no usaron [Quarkus / Vaadin / Cucumber / RestAssured / JMeter]?

Respuesta directa: no se usaron, y estas son las equivalencias.

| Herramienta | Qué se usó en su lugar | Razón |
|---|---|---|
| Quarkus | Spring Boot 4 | Ecosistema más grande y mejor integración con Keycloak vía Resource Server estándar |
| Vaadin | React 19 | SPA desacoplada; el frontend puede evolucionar sin tocar el backend |
| Cucumber | JUnit + Playwright | Cucumber aporta valor cuando hay stakeholders no técnicos escribiendo escenarios; aquí habría añadido una capa de traducción sin lector |
| RestAssured | MockMvc + `post-deploy-smoke.sh` | MockMvc cubre los escenarios de API en el build; el smoke cubre la API desplegada |
| JMeter | k6 | Scripts en JavaScript versionables, con umbrales dentro del código y salida en JSON para CI |

Lo importante es que **cada categoría está cubierta**, aunque con otra herramienta.

---

### P117. ¿Cómo saben que las pruebas realmente sirven?

Cuatro argumentos:

1. **Han encontrado errores reales.** El incremento de la secuencia de Envers (migración `V3`) salió de un test de integración que fallaba con identificadores duplicados.
2. **La cobertura se verifica, no solo se mide.** `check.dependsOn jacocoTestCoverageVerification` hace fallar el build si baja del 60 % en los servicios.
3. **Se puede demostrar en vivo** rompiendo una línea y viendo qué prueba falla (ver P41).
4. **Prueban en varios niveles independientes.** Un mismo permiso está verificado por un escenario de API, un test de integración con Keycloak real, un E2E en navegador y un script de smoke. Si tres coinciden, no es casualidad.

---

### P118. Si el profesor pide agregar un endpoint nuevo en vivo, ¿qué harías?

Ejemplo: `GET /api/v1/products/count`.

1. **Repositorio:** ya existe `count()` heredado de `JpaRepository`.
2. **Servicio:**

```java
public long countAll() {
    return productRepository.count();
}
```

3. **Controlador:**

```java
@GetMapping("/count")
@PreAuthorize("hasAuthority('product:view')")
@Operation(summary = "Total de productos")
public long count() {
    return productService.countAll();
}
```

4. **Prueba:** un caso en `ProductApiScenarioTest` con `@WithMockUser(authorities = "product:view")`.

Y decir en voz alta: "El permiso lo elijo según quién debería verlo; Swagger se actualiza solo porque springdoc lee las anotaciones; y no hace falta tocar Keycloak porque `product:view` ya existe."

---

### P119. ¿Cuál es la parte del código de la que estás más orgulloso?

Elige una y prepárala. Tres candidatas fuertes:

**`extractAuthorities` en `DockerSecurityConfig`.** Es el puente entre dos mundos: convierte el formato de Keycloak en el de Spring Security. Sin esas 25 líneas, `@PreAuthorize` no funcionaría con roles de cliente. Y está escrito con `instanceof` con patrón de Java 21, así que no puede lanzar `ClassCastException` con un token malformado.

**El `switch` de `calculateQuantityAfter`.** Toda la regla de negocio del stock en 20 líneas, con el compilador garantizando que no se olvide ningún tipo de movimiento.

**`apiFetch`.** Todo el consumo de la API en un solo lugar: token, cabeceras, 204 y errores. Añadir un interceptor nuevo es tocar un archivo.

---

### P120. ¿Qué harías distinto si empezaras de nuevo?

1. **Coherencia de paquetes desde el primer día.** Hoy `dto/` contiene clases del paquete `application.dto`. Compila, pero es una inconsistencia que debió corregirse al detectarla.
2. **Configuración del frontend en runtime**, con un `config.json`, para tener una sola imagen para todos los ambientes en vez de una por ambiente.
3. **`@Version` para bloqueo optimista** desde el modelado inicial; añadirlo después implica una migración.
4. **Datos de ejemplo en una migración marcada** (`R__seed.sql` o un perfil de demo) para que la demostración no dependa de crear productos a mano.
5. **Cabeceras de seguridad desde el inicio**, en lugar de dejarlas como advertencias pendientes de ZAP.

---

# J. Preguntas del profesor (sesión)

Bloque literal de lo que el profesor abarcó en la sesión previa a la defensa. Respuestas alineadas al código actual; si algo **no está hecho**, se dice con claridad.

---

### P121. Mostrar el pipeline

El pipeline principal es [`.github/workflows/devsecops.yml`](../../../.github/workflows/devsecops.yml) (**DevSecOps Pipeline**). Se dispara en push/PR a `develop` y también a mano (`workflow_dispatch`).

Seis jobs:

```
build-and-test ──► docker-images
               ├──► dependency-check
               ├──► zap-baseline
               └──► staging-deploy-e2e
                         │
                         ▼
                   quality-gate  (falla si alguno ≠ success)
```

| Job | Qué muestra al profesor |
|---|---|
| `build-and-test` | `./gradlew build`, unit, integration, api, contract, JaCoCo, Sonar |
| `docker-images` | Build de imágenes API + frontend |
| `dependency-check` | SCA (OWASP Dependency-Check) |
| `zap-baseline` | Stack Compose + smoke seguridad + ZAP |
| `staging-deploy-e2e` | **Despliegue de staging** + smoke + Playwright |
| `quality-gate` | Gate final que ata todo |

**Mostrar en vivo:** pestaña Actions de GitHub → último run de *DevSecOps Pipeline* → expandir cada job. Equivalente local: [`infra/jenkins/Jenkinsfile`](../../../infra/jenkins/Jenkinsfile).

Detalle: [P43–P46](#f-cicd) · [`PIPELINE.md`](../ci/PIPELINE.md)

---

### P122. ¿Cómo se despliega?

**Con Docker Compose**, no con un PaaS (todavía).

| Ambiente | Cómo |
|---|---|
| **Local / desarrollo** | `docker compose --env-file .env up -d --build` ([`docker-compose.yml`](../../../docker-compose.yml)) |
| **Staging** | `docker compose -f docker-compose.staging.yml --env-file .env.staging up -d --build` |
| **Producción (plantilla)** | `docker-compose.prod.yml` + `.env.production` (puertos solo en `127.0.0.1`, Swagger off) |
| **CI** | El job `staging-deploy-e2e` levanta staging en el runner, prueba y hace `down -v` |

Flujo de un deploy:

1. Se construyen las imágenes (`Dockerfile` multi-stage API; frontend Vite → nginx).
2. Compose arranca Postgres, Keycloak (importa realm), API, frontend y (en local) observabilidad.
3. Flyway aplica migraciones al arrancar la API.
4. Healthchecks / `wait-for-stack.sh` esperan a que Keycloak y la API respondan.
5. Smoke (`post-deploy-smoke.sh`) y, en CI, E2E Playwright.

**Además hay plantillas cloud** (Render + Vercel): [`CLOUD.md`](../ci/CLOUD.md), `render.yaml`, `deploy-staging.yml` / `deploy-prod.yml`. Compose local/CI sigue siendo el camino del PDF; el PaaS es el pedido verbal. Ver [P135](#p135-vercel-y-render--hay-que-desplegar-en-una-plataforma).

---

### P123. ¿En qué punto se despliegan los environments?

| Momento | Ambiente | Quién |
|---|---|---|
| Desarrollo diario | `local` / perfil Spring `docker` o `local` | Developer en su máquina |
| Cada push/PR a `develop` | **Staging efímero (CI)** | Job `staging-deploy-e2e` (Compose en el runner) |
| Push a `develop` (cloud) | **Staging persistente** | `deploy-staging.yml` → Render + Vercel |
| Push a `main` (cloud) | **Production** | `deploy-prod.yml` → Render + Vercel |
| Compose prod local | Plantilla | `docker-compose.prod.yml` (demo local) |

El punto exacto en el pipeline:

```
build-and-test (OK)
       │
       ▼
staging-deploy-e2e   ← AQUÍ se despliega el environment de staging
  1. cp .env.staging.example .env.staging
  2. docker compose -f docker-compose.staging.yml up -d --build
  3. wait-for-stack.sh
  4. post-deploy-smoke.sh
  5. Playwright E2E
  6. down -v
```

Los tres environments están definidos (puertos, `.env.*`, perfiles Spring). Staging se **ejerce de verdad** en CI; prod es plantilla lista, no un deploy cloud automático.

**Mostrar:** [`docker-compose.staging.yml`](../../../docker-compose.staging.yml) + job `staging-deploy-e2e` en `devsecops.yml`.

---

### P124. ¿Cómo implementamos la paginación y qué usamos?

**Offset / page** con el mecanismo estándar de **Spring Data** (`Pageable` + `Page`).

1. El controlador recibe `?page=0&size=20&sort=...` vía `@PageableDefault(size = 20) Pageable pageable`.
2. El servicio llama al repositorio (`@Query` nativa en productos; `JpaSpecificationExecutor` + `Specification` en movimientos de stock).
3. Se envuelve en **`PageResponse<T>`** (record propio) para no acoplar el JSON al formato interno de Spring.

```java
// ProductController
public PageResponse<ProductResponse> list(..., @PageableDefault(size = 20) Pageable pageable)

// PageResponse.from(page) → content, page, size, totalElements, totalPages, ...
```

Frontend: `Products.tsx` pide `size=10` y muestra totales.

**No usamos cursor/keyset.** Offset basta a esta escala y permite “página 3 de 12”.

**Mostrar:** [`ProductController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/ProductController.java), [`PageResponse.java`](../../../src/main/java/icc354/pucmm/proyectoqa/dto/PageResponse.java), [P17–P18](#p17-se-utiliza-paginación).

---

### P125. ¿En qué proceso se verifica que el token está vivo?

Hay **dos capas**, cliente y servidor:

**1. Frontend (antes de cada llamada y al expirar)**

```ts
// api/client.ts — si el JWT vence en < 30 s, lo renueva
await keycloak.updateToken(30);

// AuthContext — cuando Keycloak avisa que expiró
keycloak.onTokenExpired = () => {
  keycloak.updateToken(30).catch(() => keycloak.logout());
};
```

Si el **refresh token** también murió, se cierra sesión. Al arrancar usa `onLoad: "check-sso"` (cookie de sesión de Keycloak, no re-login obligatorio).

**2. API (en cada petición HTTP)**

El filtro `BearerTokenAuthenticationFilter` (OAuth2 Resource Server):

1. Lee `Authorization: Bearer …`
2. Valida **firma** con JWKS de Keycloak
3. Valida **exp** (si expiró → **401**)
4. Valida **issuer** (`iss`)
5. `extractAuthorities` convierte roles del JWT en authorities

La API **no** pregunta a Keycloak “¿sigue vivo?” (no hay introspección). Confía en la firma + `exp` del JWT. Eso es lo correcto para JWT.

**Mostrar:** [`frontend/src/api/client.ts`](../../../frontend/src/api/client.ts), [`AuthContext.tsx`](../../../frontend/src/auth/AuthContext.tsx), [`DockerSecurityConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/DockerSecurityConfig.java).

---

### P126. ¿En qué punto se maneja la restricción de roles? ¿Es el mismo endpoint para API y frontend?

**Misma API REST** para el navegador, curl, k6 y Swagger. No hay un endpoint distinto “para el frontend”.

La restricción real está **solo en el backend**, en cada método:

```java
@PreAuthorize("hasAuthority('product:view')")   // GET
@PreAuthorize("hasAuthority('product:manage')") // POST/PUT/DELETE
@PreAuthorize("hasAuthority('stock:manage')")   // POST movimientos
```

El frontend **no es seguridad**: oculta botones con `hasResourceRole` / `PERMISSIONS` para UX. Cualquiera puede llamar la API con curl; sin el permiso en el JWT recibe **403**.

```
UI (opcional)     →  hasResourceRole('product:manage')  → muestra/oculta botón
API (obligatorio) →  @PreAuthorize(...)                 → 200 o 403
```

**Demostración:** login como `viewer` → no ve “Crear”; `POST /api/v1/products` con su token → 403. Ver [`permissions.spec.ts`](../../../frontend/e2e/permissions.spec.ts).

---

### P127. ¿Dónde está la gestión de tokens y clientes?

**En Keycloak**, no en la aplicación.

| Qué | Dónde |
|---|---|
| Realm, usuarios, roles de cliente | [`keycloak/inventory-realm.json`](../../../keycloak/inventory-realm.json) (import al arrancar) |
| Cliente confidencial `inventory-api` | secret, password grant, **dueño de los 7 permisos** |
| Cliente público `inventory-frontend` | PKCE, redirect URIs del SPA |
| Emisión / refresh / logout | Keycloak (`/protocol/openid-connect/token`, …) |
| Validación del access token | API: Resource Server + JWKS |
| Obtención en el navegador | [`frontend/src/auth/keycloak.ts`](../../../frontend/src/auth/keycloak.ts) + `AuthContext` |
| Obtención en scripts/tests | password grant contra `inventory-api` (`helpers.js`, smoke, IT) |

Consola admin Keycloak: http://localhost:8081 (`admin`/`admin`) → realm **inventory** → Clients / Users / Roles.

La app **no guarda** usuarios ni emite tokens.

---

### P128. ¿Cómo se le da el privilegio de acuerdo al stock?

Con **dos client roles** del cliente `inventory-api`, asignados al usuario en Keycloak:

| Permiso | Quién lo tiene (demo) | Qué protege |
|---|---|---|
| `stock:view` | `viewer`, `stock-manager`, `admin` | `GET` movimientos / historial |
| `stock:manage` | `stock-manager`, `admin` | `POST` crear movimiento (IN/OUT/ADJUSTMENT) |

```java
// StockController
@PreAuthorize("hasAuthority('stock:view')")    // listar
@PreAuthorize("hasAuthority('stock:manage')")  // crear movimiento
```

`stock-manager` puede mover inventario **sin** `product:manage` ni `report:view`: eso es la granularidad fina.

La **regla de negocio** (stock insuficiente → 400) vive en `StockService.calculateQuantityAfter`, no en Keycloak. Keycloak solo dice *quién puede intentar*; el servicio dice *si la cantidad es válida*.

**Mostrar:** realm (usuario `stock-manager`) + [`StockController.java`](../../../src/main/java/icc354/pucmm/proyectoqa/controller/StockController.java).

---

### P129. Tener la prueba de estrés local

Sí. Script k6 + evidencia versionada.

```bash
docker compose up -d --build postgres keycloak tempo loki alloy api
./scripts/k6-run.sh stress          # o: ./scripts/k6-run.sh all
```

- Script: [`tests/k6/stress-products.js`](../../../tests/k6/stress-products.js)
- Evidencia: [`docs/final/testing/k6/stress-products-summary.txt`](../testing/k6/stress-products-summary.txt)
- Pico: **80 VUs**; umbral **p95 &lt; 2000 ms**, error rate **&lt; 5 %**
- Última corrida documentada: p95 ≈ **22.5 ms**, **0 %** errores, ~17 800 requests

También hay **load** (15 VUs, p95 &lt; 500 ms). Guía: [`docs/final/testing/k6/README.md`](../testing/k6/README.md).

---

### P130. “Hay que hacer despliegue en una plataforma” (Vercel / Render / cloud)

**Estado actual:** el despliegue demostrado es **Docker Compose** (local + staging en GitHub Actions). **No** hay proyecto desplegado en Vercel ni Render.

| Opción | Encaja con |
|---|---|
| **Compose en CI** (lo que tenemos) | Cumple “ambiente desplegado + smoke/E2E post-deploy” del curso |
| **Render** (API + Postgres + Keycloak) + **Vercel** (frontend) | Pedido verbal del profesor; pendiente si lo exige en la defensa |

Si preguntan por cloud: Blueprint Render + Vercel ya están en el repo ([CLOUD.md](../ci/CLOUD.md)); falta conectar cuentas y secrets. Compose CI sigue siendo el smoke automático. Ver [P135](#p135-vercel-y-render--hay-que-desplegar-en-una-plataforma).

---

### P131. Si quiero crear un token para preparar la API (acceso de una empresa hipotética), ¿cómo se gestiona?

**No se “crea un token a mano” en el código.** Se gestiona en Keycloak según el tipo de consumidor:

**A) Integración máquina-a-máquina (empresa / backend de tercero) — lo correcto**

1. En Keycloak → Clients → crear cliente confidencial, p. ej. `empresa-xyz`.
2. Activar **Service accounts** (client credentials).
3. Asignar al service account solo los client roles necesarios (`product:view`, etc.).
4. La empresa obtiene el token:

```bash
curl -s -X POST "http://localhost:8081/realms/inventory/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=empresa-xyz" \
  -d "client_secret=<secreto>"
```

5. Llama la API con `Authorization: Bearer <access_token>`. La API no cambia: sigue validando JWT + `@PreAuthorize`.

**B) Usuario humano de esa empresa**

Crear usuario en el realm, asignar roles de `inventory-api`, login con Authorization Code (SPA) o password grant solo en automatización.

**C) Lo que usamos hoy en demos/scripts** (no para una empresa real)

Password grant del cliente `inventory-api` con usuarios demo (`admin`/`viewer`). Útil para k6 y smoke; **desaconsejado** como modelo de integración externa.

**Mostrar:** Clients en la consola Keycloak + claim `resource_access` en jwt.io.

---

### P132. Prueba de performance: ¿cuántas pruebas, cobertura p95 / p90?

**Dos escenarios k6** (no “cobertura de código”; son umbrales de latencia):

| # | Tipo | Script | Pico VUs | Gate p95 | Gate p90 | Error rate |
|---|---|---|---|---|---|---|
| 1 | **Load** | `load-products.js` | 15 | **&lt; 500 ms** | *no hay gate* | &lt; 1 % |
| 2 | **Stress** | `stress-products.js` | 80 | **&lt; 2000 ms** | *no hay gate* | &lt; 5 % |

Solo se **exige p95** en los thresholds. El **p90 se mide** en el summary de k6 (informativo), pero no falla el test si p90 sube.

Resultados documentados:

| | Load | Stress |
|---|---|---|
| p95 duration | ~15.3 ms | ~22.5 ms |
| p90 duration | ~13.0 ms | ~15.1 ms |
| http_req_failed | 0 % | 0 % |
| requests | ~1 025 | ~17 819 |

Endpoint bajo prueba: `GET /api/v1/products` con JWT.

**Punto a punto (E2E)** no es k6: son **12 tests Playwright** (`frontend/e2e/`). k6 = performance; Playwright = punta a punta UI+API.

---

### P133. ¿Se renderizó una herramienta para el % de coverage? ¿Qué clase está floja? ¿Cuántas pruebas hizo k6?

**Herramientas de cobertura (sí, dos):**

1. **JaCoCo** — reporte HTML: `./gradlew jacocoTestReport` → `build/reports/jacoco/test/html/index.html`
2. **SonarCloud** — dashboard del proyecto (`jeanc24_ProyectoQA`): bugs, smells, duplicación y **% cobertura**

El pipeline sube el artefacto `jacoco-report` y corre `sonar` cuando hay `SONAR_TOKEN`.

**Qué estaba “flojo” (historia útil):**

- Sonar mostraba **~34 %** porque las carpetas `service/` / `dto/` no coincidían con los paquetes `application.service` / `application.dto` → Sonar no mapeaba la cobertura de los servicios (lo más grande del código).
- Además JaCoCo **ignoraba** `integrationTest.exec`.
- Tras el fix: local ~**86 %** líneas; Sonar esperado ~**82 %**. Umbral de gate: **60 %** en clases de `application.service` (excepto `AuditService`, cubierto por IT).

**Clases deliberadamente fuera del umbral unitario:** `AuditService` (necesita Envers/sesión real). DTOs y configs no entran en el mínimo de JaCoCo verification porque no aportan confianza.

**k6:** **2 pruebas** (load + stress). Ver [P132](#p132-prueba-de-performance-cuántas-pruebas-cobertura-p95--p90).

Detalle del bug de cobertura: [P97b](#p97b-por-qué-la-cobertura-en-sonar-estaba-en-34--y-qué-hicieron).

---

### P134. Logs del proyecto: ¿para qué Grafana, Loki, Tempo? ¿Dónde están las traces de Tempo?

| Pieza | Para qué |
|---|---|
| **Prometheus** | Métricas numéricas (req/s, latencia, 401/403/5xx, JVM, HikariCP) |
| **Loki** | Logs centralizados del contenedor `inventory-api` (vía Alloy + Docker socket) |
| **Tempo** | Trazas distribuidas (spans HTTP + JDBC) exportadas por OpenTelemetry |
| **Grafana** | UI que junta las tres señales; dashboards versionados en `infra/grafana/dashboards/` |
| **Alloy** | Agente: recibe OTLP de la API, reparte a Tempo/Loki y scrape/forward |

**Dónde ver traces de Tempo:**

1. Grafana http://localhost:3001 → dashboard **Observabilidad — Métricas, Logs y Trazas** → panel inferior **“Trazas recientes — service.name = inventory-api”** (datasource Tempo).
2. O **Explore** → datasource Tempo → buscar `service.name = inventory-api`.
3. Desde un log en Loki: clic en el **TraceID** (derived field) → salta a Tempo.

Flujo técnico: API → OTLP → Alloy → Tempo. Cada log lleva `[traceId,spanId]` (`logging.pattern.correlation`).

**Mostrar:** [`observability.json`](../../../infra/grafana/dashboards/observability.json), [`datasources.yml`](../../../infra/grafana/provisioning/datasources/datasources.yml).

---

### P135. Vercel y Render — ¿hay que desplegar en una plataforma?

Pedido verbal del profesor. **Sí, y el repo ya trae el andamiaje:**

| Pieza | Dónde |
|---|---|
| Blueprint staging | [`render.yaml`](../../../render.yaml) (branch `develop`) |
| Blueprint prod | [`infra/render/render.prod.yaml`](../../../infra/render/render.prod.yaml) (branch `main`) |
| FE | Vercel (`frontend/`, [`vercel.json`](../../../frontend/vercel.json)) |
| Pipelines | `deploy-staging.yml` / `deploy-prod.yml` |
| Guía | [`CLOUD.md`](../ci/CLOUD.md) |

**Grafana:** uno solo en Compose local. No hay tres Grafanas (dev/staging/prod): el profesor lo indicó y en free tier es inviable.

Respuesta en defensa:

1. Staging cloud ← `develop`; prod cloud ← `main`.
2. API + Postgres + Keycloak en **Render**; SPA en **Vercel**.
3. El DevSecOps sigue probando staging Compose efímero en CI; el cloud es el ambiente persistente.
4. Observabilidad se demuestra en local (un Grafana).

Tras crear las cuentas hay que pegar URLs en Secrets/Variables y completar env `sync: false` en Render (issuer Keycloak = URL pública).

---

### P136. Logs de errores en los dashboards

Sí, en el dashboard unificado:

| Panel | Fuente | Query |
|---|---|---|
| **Logs ERROR** | Loki | `{job="inventory-api"} \|~ "(?i)ERROR"` |
| **Errores 5xx / 401** | Prometheus | métricas HTTP filtradas por status |
| **Security** (otro dashboard) | Prometheus | series 401 / 403 |

También Explore → Loki → mismo filtro, o `{job="inventory-api", level="ERROR"}` si Alloy etiquetó el nivel.

**Nota honesta:** 401/403 de Spring Security a menudo **no** generan línea `ERROR` en log; sí suben en paneles de **métricas**. Para llenar “Logs ERROR” hace falta una excepción de negocio/runtime. Para la demo visual: generar 401/403 (Security) + tráfico 200 (trazas) + 404 autenticado (pasa por la app).

---

### P137. Debemos tener Tempo, Loki y Prometheus: gráficos y traces

**Sí, provisionado.** Tres datasources + dashboard que los muestra juntos:

```
Grafana dashboards/
  observability.json  ← Prometheus (gráficos) + Loki (logs) + Tempo (traces)
  app / infra / security / business / api-ops  ← principalmente Prometheus
```

Servicios en Compose: `prometheus`, `loki`, `tempo`, `alloy`, `grafana` (puerto host **3001**).

Para la defensa: abrir **Observabilidad — Métricas, Logs y Trazas**, generar tráfico con JWT, refrescar 10 s. Ver [P134](#p134-logs-del-proyecto-para-qué-grafana-loki-tempo-dónde-están-las-traces-de-tempo).

---

### P138. Pruebas de stress y punto a punto (k6)

Aclarar el vocabulario del profesor:

| Lo que pide | Qué tenemos | Herramienta |
|---|---|---|
| **Stress** | `stress-products.js` (80 VUs) | **k6** |
| **Load / performance** | `load-products.js` (15 VUs) | **k6** |
| **Punto a punto (E2E)** | 9 specs Playwright (login, permisos, productos, dashboard…) | **Playwright**, no k6 |

k6 golpea un endpoint con muchos VUs; no navega el UI. Playwright sí recorre el flujo completo navegador → Keycloak → API.

Ejecutar stress: [P129](#p129-tener-la-prueba-de-estrés-local). E2E: `cd frontend && npx playwright test`.

---

### P139. Cobertura de stress / performance y mostrar la cobertura

Son **dos “coberturas” distintas**; no mezclarlas:

1. **Cobertura de código (unit/IT):** JaCoCo HTML + SonarCloud (% líneas/ramas). Gate 60 % en servicios. Mostrar `build/reports/jacoco/...` o el proyecto en SonarCloud.
2. **“Cobertura” de performance:** no es % de código. Son **umbrales** p95/error rate cumplidos en load y stress, con evidence en `docs/final/testing/k6/*-summary.txt`.

En la defensa: abrir el TXT de stress (p95, p90, checks 100 %) **y** el HTML/Sonar de JaCoCo. Frase clara: *“k6 no mide cobertura de código; mide si bajo 80 usuarios el p95 sigue bajo el umbral.”*

---

### P140. ¿Cómo sabe la API qué rol tiene cada uno y cómo se manejan los roles?

1. El usuario inicia sesión en Keycloak → el **access token JWT** trae:

```json
"resource_access": {
  "inventory-api": {
    "roles": ["product:view", "stock:view", "stock:manage"]
  }
}
```

2. En cada request, `DockerSecurityConfig.extractAuthorities` lee `realm_access` + `resource_access[inventory-api]` y crea `SimpleGrantedAuthority` por cada rol.
3. `@PreAuthorize("hasAuthority('stock:manage')")` consulta esas authorities en el `SecurityContext`.
4. Si falta el permiso → **403**; si el token es inválido/expirado → **401**.

Los roles **no están hardcodeados por usuario** en Java. Viven en Keycloak (realm JSON / consola). El código solo declara *qué permiso exige cada endpoint*. El frontend usa los **mismos nombres** (`permissions.ts`) solo para ocultar UI.

```
Keycloak (asignación) → JWT (transporte) → extractAuthorities (mapeo) → @PreAuthorize (decisión)
```

**Mostrar en vivo:** jwt.io con un token de `stock-manager` + el método anotado en `StockController`.

---

### P141. ¿Para usar el token es con user/pass authentication?

**Depende del cliente; la API siempre recibe Bearer JWT**, nunca user/pass en el header de negocio.

| Quién | Cómo obtiene el token | ¿User/pass? |
|---|---|---|
| **Frontend (humano)** | Authorization Code + **PKCE** | Sí, pero **en la página de Keycloak**, no en la API |
| **k6 / smoke / IT** | Resource Owner **Password** grant (`inventory-api` + secret) | Sí, solo automatización |
| **Empresa / M2M (ideal)** | **Client credentials** | No: client_id + client_secret |

La API **no** implementa form login. Solo:

```
Authorization: Bearer <access_token>
```

Swagger: botón Authorize → pegar el access_token (scheme `bearerAuth` en [`OpenApiConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java)).

---

### P142. ¿Cómo se mapea Swagger?

**springdoc-openapi** escanea los controladores en runtime (perfiles `!prod`).

| Pieza | Rol |
|---|---|
| Anotaciones `@Tag`, `@Operation`, `@ApiResponse`, `@ParameterObject` | Documentan cada endpoint |
| [`OpenApiConfig.java`](../../../src/main/java/icc354/pucmm/proyectoqa/config/OpenApiConfig.java) | Título, versión, security scheme Bearer JWT |
| UI | http://localhost:8080/swagger-ui.html (dev/staging) |
| Spec JSON | `/v3/api-docs` |
| Prod | `springdoc` deshabilitado → no hay UI ni rutas públicas de docs |

No hay un `swagger.yaml` a mano: el mapa se **genera** del código. Si añades un `@GetMapping` con `@Operation`, aparece solo.

---

### P143. ¿Cómo se manejan los permisos para hacer el despliegue?

Dos lecturas posibles; ambas válidas:

**A) Permisos de la app (roles) — no controlan el deploy**  
Keycloak decide quién usa la API; **no** quién despliega. Un `viewer` no puede “desplegar menos”: el deploy es infra.

**B) Permisos del pipeline / secretos (lo que sí controla el deploy)**

| Secreto / permiso | Dónde | Para qué |
|---|---|---|
| `SONAR_TOKEN` | GitHub Secrets | Análisis Sonar |
| `GITHUB_TOKEN` | automático | checkout, artifacts |
| `NVD_API_KEY` (opcional) | Secret | Dependency-Check más rápido |
| Write en el repo / Environments | GitHub | Quién puede mergear a `develop`/`main` |
| Archivos `.env` / `.env.staging` | No se commitean secretos reales | Compose local/CI |

En CI, staging se despliega con credenciales de **ejemplo** (`.env.staging.example` copiado). En un deploy real a plataforma, las variables irían en el vault del PaaS y el job necesitaría un token de deploy (Render API key, Vercel token) guardado como secret — **aún no configurado** porque el PaaS no está cableado.

**Mostrar:** Settings → Secrets del repo + el job que hace `docker compose ... up` sin pedir roles de Keycloak.
