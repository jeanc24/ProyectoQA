export type TechId =
  | "spring-boot"
  | "react"
  | "typescript"
  | "vite"
  | "postgresql"
  | "flyway"
  | "envers"
  | "keycloak"
  | "docker"
  | "junit"
  | "testcontainers"
  | "playwright"
  | "github-actions"
  | "jenkins"
  | "prometheus"
  | "grafana"
  | "opentelemetry"
  | "tempo"
  | "loki"
  | "alloy"
  | "alertmanager";

export type TechLayer =
  | "frontend"
  | "backend"
  | "data"
  | "security"
  | "infra"
  | "testing"
  | "ci"
  | "observability";

export type TechItem = {
  id: TechId;
  name: string;
  shortName: string;
  color: string;
  layer: TechLayer;
  role: string;
  summary: string;
  connectsTo: TechId[];
  code: { title: string; language: string; snippet: string };
  howItFits: string[];
};

export const TECH_ITEMS: TechItem[] = [
  {
    id: "react",
    name: "React 19",
    shortName: "React",
    color: "#61DAFB",
    layer: "frontend",
    role: "Interfaz web",
    summary:
      "UI de la consola: landing, productos, stock y dashboard. Enruta con React Router y habla con la API usando el JWT de Keycloak.",
    connectsTo: ["typescript", "vite", "keycloak", "spring-boot"],
    code: {
      title: "frontend/src/auth/AuthContext.tsx",
      language: "tsx",
      snippet: `const login = useCallback(() => {
  keycloak.login({
    redirectUri: window.location.origin + "/management",
  });
}, []);`,
    },
    howItFits: [
      "Páginas protegidas con ProtectedRoute + permisos",
      "Tras login redirige a /management → /products",
      "Cada llamada API lleva el Bearer token",
    ],
  },
  {
    id: "typescript",
    name: "TypeScript",
    shortName: "TypeScript",
    color: "#3178C6",
    layer: "frontend",
    role: "Tipado del frontend",
    summary:
      "Contratos tipados para productos, stock, reportes y auth. Reduce errores en el cliente antes de llegar a la API.",
    connectsTo: ["react", "vite"],
    code: {
      title: "frontend/src/api/products.ts",
      language: "ts",
      snippet: `export function listProducts(params: ProductQuery) {
  return apiFetch<PageResponse<ProductResponse>>(
    \`/api/v1/products?\${toQuery(params)}\`,
  );
}`,
    },
    howItFits: [
      "Tipos alineados con DTOs del backend",
      "Vite + tsc en el build de producción",
    ],
  },
  {
    id: "vite",
    name: "Vite",
    shortName: "Vite",
    color: "#646CFF",
    layer: "frontend",
    role: "Build y dev server",
    summary:
      "Herramienta de desarrollo y empaquetado del frontend. En Docker el build estático se sirve con nginx.",
    connectsTo: ["react", "typescript", "docker"],
    code: {
      title: "frontend/package.json",
      language: "json",
      snippet: `"scripts": {
  "dev": "vite",
  "build": "tsc -b && vite build",
  "preview": "vite preview"
}`,
    },
    howItFits: [
      "Dev en :5173, producción en contenedor :3000",
      "Variables VITE_* para Keycloak y API",
    ],
  },
  {
    id: "spring-boot",
    name: "Spring Boot",
    shortName: "Spring Boot",
    color: "#6DB33F",
    layer: "backend",
    role: "API REST",
    summary:
      "Backend Java: endpoints /api/v1/*, seguridad OAuth2 Resource Server, Actuator y métricas. Es el núcleo del dominio inventario.",
    connectsTo: [
      "keycloak",
      "postgresql",
      "envers",
      "flyway",
      "react",
      "prometheus",
      "opentelemetry",
      "junit",
    ],
    code: {
      title: "ProductController.java",
      language: "java",
      snippet: `@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
  @GetMapping
  public Page<ProductResponse> list(/* filtros */) { … }
}`,
    },
    howItFits: [
      "Valida JWT emitido por Keycloak",
      "Permisos granulares (product:view, stock:manage…)",
      "Expone Actuator/health y métricas Micrometer",
    ],
  },
  {
    id: "postgresql",
    name: "PostgreSQL",
    shortName: "PostgreSQL",
    color: "#4169E1",
    layer: "data",
    role: "Base de datos",
    summary:
      "Almacena productos, categorías, movimientos de stock y tablas de auditoría Envers. Puerto host 5433.",
    connectsTo: ["spring-boot", "flyway", "envers", "docker"],
    code: {
      title: "application / Docker",
      language: "yaml",
      snippet: `DB: inventory @ localhost:5433
Usuario/clave: inventory / inventory
Driver JDBC → Spring Data JPA`,
    },
    howItFits: [
      "Esquema versionado con Flyway",
      "Entidades JPA + revisiones *_aud",
    ],
  },
  {
    id: "flyway",
    name: "Flyway",
    shortName: "Flyway",
    color: "#CC0200",
    layer: "data",
    role: "Migraciones SQL",
    summary:
      "Aplica scripts de migración al arrancar la API. Mantiene el esquema reproducible entre entornos.",
    connectsTo: ["postgresql", "spring-boot"],
    code: {
      title: "src/main/resources/db/migration/",
      language: "sql",
      snippet: `-- V1__…sql
CREATE TABLE product ( … );
-- Vn aplica en orden en cada deploy`,
    },
    howItFits: [
      "Sin migraciones a mano en prod",
      "Misma BD local, Docker e integración",
    ],
  },
  {
    id: "envers",
    name: "Hibernate Envers",
    shortName: "Envers",
    color: "#59666C",
    layer: "data",
    role: "Auditoría de entidades",
    summary:
      "Versiona cambios en entidades anotadas con @Audited. La API de auditoría expone el historial de productos.",
    connectsTo: ["spring-boot", "postgresql"],
    code: {
      title: "Product.java",
      language: "java",
      snippet: `@Entity
@Audited
public class Product {
  // cada UPDATE genera revisión en tablas *_aud
}`,
    },
    howItFits: [
      "Endpoint /api/v1/audit/products/{id}",
      "Permiso audit:view (p. ej. rol auditor)",
    ],
  },
  {
    id: "keycloak",
    name: "Keycloak",
    shortName: "Keycloak",
    color: "#4D4D4D",
    layer: "security",
    role: "Identity Provider",
    summary:
      "SSO OAuth2/OIDC. Emite JWT con roles del cliente inventory-api. El frontend inicia login; la API valida el token.",
    connectsTo: ["react", "spring-boot", "docker"],
    code: {
      title: "AuthContext + realm inventory",
      language: "ts",
      snippet: `keycloak.init({
  onLoad: "check-sso",
  pkceMethod: "S256",
});
// Roles: product:view, stock:manage, report:view…`,
    },
    howItFits: [
      "Realm inventory, client inventory-frontend",
      "Usuarios demo: admin, viewer, stock-manager, auditor",
      "Puerto :8081",
    ],
  },
  {
    id: "docker",
    name: "Docker Compose",
    shortName: "Docker",
    color: "#2496ED",
    layer: "infra",
    role: "Orquestación local",
    summary:
      "Levanta API, frontend, Postgres, Keycloak y el stack de observabilidad con un solo compose up.",
    connectsTo: [
      "spring-boot",
      "react",
      "postgresql",
      "keycloak",
      "prometheus",
      "grafana",
      "jenkins",
    ],
    code: {
      title: "docker-compose.yml",
      language: "bash",
      snippet: `docker compose up --build -d
# frontend :3000  api :8080  keycloak :8081`,
    },
    howItFits: [
      "Misma topología para demos y QA",
      "Rebuild del frontend necesario tras cambios UI",
    ],
  },
  {
    id: "junit",
    name: "JUnit 5",
    shortName: "JUnit",
    color: "#25A162",
    layer: "testing",
    role: "Tests unitarios / API",
    summary:
      "Suite de pruebas del backend con Mockito y escenarios de controlador/seguridad.",
    connectsTo: ["spring-boot", "testcontainers"],
    code: {
      title: "ProductControllerTest.java",
      language: "java",
      snippet: `@WebMvcTest(ProductController.class)
void list_returnsOk() throws Exception {
  mockMvc.perform(get("/api/v1/products"))
    .andExpect(status().isOk());
}`,
    },
    howItFits: [
      "CI ejecuta ./gradlew test",
      "Complementa Testcontainers e integración Keycloak",
    ],
  },
  {
    id: "testcontainers",
    name: "Testcontainers",
    shortName: "Testcontainers",
    color: "#291A3B",
    layer: "testing",
    role: "Tests de integración",
    summary:
      "Levanta contenedores reales (Postgres/Keycloak) durante tests de integración.",
    connectsTo: ["junit", "postgresql", "keycloak", "docker"],
    code: {
      title: "Integration tests",
      language: "java",
      snippet: `// Contenedores efímeros en el test
// Misma imagen Docker que en compose`,
    },
    howItFits: [
      "Valida seguridad y persistencia de punta a punta",
      "Requiere Docker en ejecución",
    ],
  },
  {
    id: "playwright",
    name: "Playwright",
    shortName: "Playwright",
    color: "#2EAD33",
    layer: "testing",
    role: "E2E del frontend",
    summary:
      "Pruebas de navegador: login Keycloak, productos, permisos por rol.",
    connectsTo: ["react", "keycloak", "github-actions"],
    code: {
      title: "frontend/e2e/helpers/auth.ts",
      language: "ts",
      snippet: `await page.goto("/login");
await page.getByTestId("login-button").click();
// Keycloak → /management → /products`,
    },
    howItFits: [
      "npm run test:e2e en frontend/",
      "Cubre flujo real de SSO",
    ],
  },
  {
    id: "github-actions",
    name: "GitHub Actions",
    shortName: "Actions",
    color: "#2088FF",
    layer: "ci",
    role: "CI en la nube",
    summary:
      "Pipeline en push/PR: build, tests, quality gate (Sonar) según workflows del repo.",
    connectsTo: ["junit", "playwright", "spring-boot"],
    code: {
      title: ".github/workflows/ci.yml",
      language: "yaml",
      snippet: `on: [push, pull_request]
jobs:
  build-and-test: …`,
    },
    howItFits: [
      "Badge de CI en el README",
      "Complementa Jenkins local opcional",
    ],
  },
  {
    id: "jenkins",
    name: "Jenkins",
    shortName: "Jenkins",
    color: "#D24939",
    layer: "ci",
    role: "CI local (opcional)",
    summary:
      "Instancia Jenkins en Docker (:8082) para pipelines locales del monorepo.",
    connectsTo: ["docker", "junit"],
    code: {
      title: "docker-compose (servicio jenkins)",
      language: "text",
      snippet: `http://localhost:8082
Pipeline CI local opcional`,
    },
    howItFits: [
      "Útil en demos de CI sin depender solo de GitHub",
    ],
  },
  {
    id: "prometheus",
    name: "Prometheus",
    shortName: "Prometheus",
    color: "#E6522C",
    layer: "observability",
    role: "Métricas",
    summary:
      "Scrapea métricas de la API (Micrometer/Actuator). Grafana las visualiza.",
    connectsTo: ["spring-boot", "grafana", "alertmanager", "docker"],
    code: {
      title: "Actuator + scrape",
      language: "text",
      snippet: `API /actuator/prometheus
Prometheus :9090 scrapea el target`,
    },
    howItFits: [
      "Salud y latencia de endpoints",
      "Alertas vía Alertmanager",
    ],
  },
  {
    id: "grafana",
    name: "Grafana",
    shortName: "Grafana",
    color: "#F46800",
    layer: "observability",
    role: "Dashboards",
    summary:
      "Visualiza métricas (Prometheus), logs (Loki) y trazas (Tempo). Login demo admin/admin en :3001.",
    connectsTo: ["prometheus", "loki", "tempo", "docker"],
    code: {
      title: "Grafana datasources",
      language: "text",
      snippet: `Prometheus → métricas
Loki → logs
Tempo → trazas`,
    },
    howItFits: [
      "Punto único de observabilidad del stack",
    ],
  },
  {
    id: "opentelemetry",
    name: "OpenTelemetry",
    shortName: "OpenTelemetry",
    color: "#425CC7",
    layer: "observability",
    role: "Instrumentación",
    summary:
      "La API emite telemetría OTLP (trazas/métricas/logs) hacia el collector Alloy.",
    connectsTo: ["spring-boot", "alloy"],
    code: {
      title: "OTLP export",
      language: "text",
      snippet: `App → OTLP (4317/4318) → Alloy
Alloy enruta a Tempo / Loki / …`,
    },
    howItFits: [
      "Correlación request → traza → log",
    ],
  },
  {
    id: "alloy",
    name: "Grafana Alloy",
    shortName: "Alloy",
    color: "#F15A29",
    layer: "observability",
    role: "Collector OTLP",
    summary:
      "Recibe telemetría y la reparte a Tempo, Loki y el resto del pipeline de obs.",
    connectsTo: ["opentelemetry", "tempo", "loki", "docker"],
    code: {
      title: "Alloy UI",
      language: "text",
      snippet: `Collector :12345
OTLP gRPC/HTTP 4317 / 4318`,
    },
    howItFits: [
      "Desacopla la app de los backends de storage",
    ],
  },
  {
    id: "tempo",
    name: "Tempo",
    shortName: "Tempo",
    color: "#FCB414",
    layer: "observability",
    role: "Almacén de trazas",
    summary:
      "Guarda distributed traces. Se consulta desde Grafana.",
    connectsTo: ["alloy", "grafana"],
    code: {
      title: "Tempo",
      language: "text",
      snippet: `http://localhost:3200
Explore traces en Grafana`,
    },
    howItFits: [
      "Sigue un request JWT a través de la API",
    ],
  },
  {
    id: "loki",
    name: "Loki",
    shortName: "Loki",
    color: "#FBB41A",
    layer: "observability",
    role: "Almacén de logs",
    summary:
      "Agrega logs del stack. Grafana hace LogQL sobre Loki.",
    connectsTo: ["alloy", "grafana"],
    code: {
      title: "Loki",
      language: "text",
      snippet: `http://localhost:3100
Logs correlacionables con Tempo`,
    },
    howItFits: [
      "Debug de auth, stock y errores de API",
    ],
  },
  {
    id: "alertmanager",
    name: "Alertmanager",
    shortName: "Alertmanager",
    color: "#E6522C",
    layer: "observability",
    role: "Alertas",
    summary:
      "Recibe reglas de Prometheus y gestiona notificaciones operacionales.",
    connectsTo: ["prometheus", "docker"],
    code: {
      title: "Alertmanager",
      language: "text",
      snippet: `http://localhost:9093
Reglas OBS / health del sistema`,
    },
    howItFits: [
      "Cierra el loop métrica → alerta",
    ],
  },
];

export const TECH_BY_ID: Record<TechId, TechItem> = Object.fromEntries(
  TECH_ITEMS.map((t) => [t.id, t]),
) as Record<TechId, TechItem>;

export function isTechId(value: string | undefined): value is TechId {
  return !!value && value in TECH_BY_ID;
}

/** Edges for the interactive graph (from → to), derived from connectsTo */
export function buildEdges(): { from: TechId; to: TechId }[] {
  const seen = new Set<string>();
  const edges: { from: TechId; to: TechId }[] = [];
  for (const tech of TECH_ITEMS) {
    for (const to of tech.connectsTo) {
      const key = [tech.id, to].sort().join("::");
      if (seen.has(key)) continue;
      seen.add(key);
      edges.push({ from: tech.id, to });
    }
  }
  return edges;
}

export const LAYER_LABEL: Record<TechLayer, string> = {
  frontend: "Frontend",
  backend: "Backend",
  data: "Datos",
  security: "Seguridad",
  infra: "Infra",
  testing: "Testing",
  ci: "CI",
  observability: "Observabilidad",
};
