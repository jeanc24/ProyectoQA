import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import TechIcon from "../components/TechIcon";
import {
  LAYER_LABEL,
  TECH_BY_ID,
  TECH_ITEMS,
  isTechId,
  type TechLayer,
} from "../data/techGuide";
import "../styles/tech-guide.css";

const TOC = [
  { id: "que-es", label: "1. Qué es" },
  { id: "arrancar", label: "2. Arrancar" },
  { id: "mapa", label: "3. Mapa mental" },
  { id: "carpetas", label: "4. Carpetas" },
  { id: "login", label: "5. Login y roles" },
  { id: "pantallas", label: "6. Pantallas" },
  { id: "puertos", label: "7. Puertos" },
  { id: "peticion", label: "8. Una petición" },
  { id: "stack", label: "9. Stack" },
  { id: "tests", label: "10. Tests y CI" },
  { id: "faq", label: "11. FAQ" },
];

const LAYER_ORDER: TechLayer[] = [
  "frontend",
  "backend",
  "security",
  "data",
  "infra",
  "testing",
  "ci",
  "observability",
];

const DEMO_USERS = [
  {
    user: "admin",
    pass: "admin",
    can: "Todo: productos, stock, dashboard, auditoría",
  },
  {
    user: "viewer",
    pass: "viewer",
    can: "Solo lectura de productos y stock",
  },
  {
    user: "stock-manager",
    pass: "stock-manager",
    can: "Ver productos + registrar movimientos de stock",
  },
  {
    user: "auditor",
    pass: "auditor",
    can: "Lectura + historial de auditoría (Envers)",
  },
];

const PERMISSIONS = [
  { id: "product:view", desc: "Ver productos y categorías" },
  { id: "product:manage", desc: "Crear, editar y borrar productos" },
  { id: "stock:view", desc: "Ver niveles e historial de stock" },
  { id: "stock:manage", desc: "Entradas, salidas y ajustes" },
  { id: "report:view", desc: "Dashboard y reportes" },
  { id: "audit:view", desc: "Revisiones Envers de productos" },
  { id: "user:manage", desc: "Gestión de usuarios (admin)" },
];

const SERVICES = [
  { name: "Frontend", url: "http://localhost:3000", note: "UI (Docker/nginx). Dev: :5173" },
  { name: "API", url: "http://localhost:8080", note: "REST + Actuator + Swagger" },
  { name: "Keycloak", url: "http://localhost:8081", note: "SSO · realm inventory" },
  { name: "PostgreSQL", url: "localhost:5433", note: "BD inventory / inventory" },
  { name: "Grafana", url: "http://localhost:3001", note: "admin / admin" },
  { name: "Prometheus", url: "http://localhost:9090", note: "Métricas" },
  { name: "Tempo", url: "http://localhost:3200", note: "Trazas" },
  { name: "Loki", url: "http://localhost:3100", note: "Logs" },
  { name: "Alloy", url: "http://localhost:12345", note: "Collector OTLP" },
  { name: "Alertmanager", url: "http://localhost:9093", note: "Alertas" },
  { name: "Jenkins", url: "http://localhost:8082", note: "CI local opcional" },
];

const FOLDERS = [
  { path: "frontend/", desc: "React + Vite. Páginas, auth Keycloak, llamadas API." },
  { path: "src/main/java/…/proyectoqa/", desc: "API Spring Boot: controllers, domain, security." },
  { path: "src/main/resources/db/migration/", desc: "Scripts Flyway del esquema." },
  { path: "keycloak/", desc: "Realm inventory, clients y usuarios demo." },
  { path: "docker-compose.yml", desc: "Orquesta Postgres, Keycloak, API, UI y obs." },
  { path: "frontend/e2e/", desc: "Playwright: login, productos, permisos." },
  { path: "docs/", desc: "Documentación de testing, charters, etc." },
];

const SCREENS = [
  {
    route: "/",
    title: "Landing",
    body: "Página pública. Login abre Keycloak. Desde aquí puedes abrir esta guía.",
  },
  {
    route: "/management → /products",
    title: "Productos",
    body: "Tras SSO caes en management y redirige a productos. CRUD si tienes product:manage.",
  },
  {
    route: "/stock",
    title: "Stock",
    body: "Movimientos e historial. stock:manage para registrar entradas/salidas.",
  },
  {
    route: "/dashboard",
    title: "Dashboard",
    body: "Reportes e indicadores. Requiere report:view.",
  },
  {
    route: "/unauthorized",
    title: "Sin permiso",
    body: "Si tu rol no cubre la ruta, el frontend te manda aquí.",
  },
];

const REQUEST_STEPS = [
  {
    n: "01",
    title: "UI (React)",
    body: "El usuario hace clic. apiFetch prepara la petición a /api/v1/…",
  },
  {
    n: "02",
    title: "Token",
    body: "Antes de llamar, keycloak.updateToken(30) renueva el JWT si hace falta.",
  },
  {
    n: "03",
    title: "API (Spring)",
    body: "Resource Server valida el JWT. @PreAuthorize comprueba el permiso.",
  },
  {
    n: "04",
    title: "Datos",
    body: "JPA habla con PostgreSQL. Si la entidad está @Audited, Envers guarda revisión.",
  },
  {
    n: "05",
    title: "Observabilidad",
    body: "OTLP → Alloy → métricas/trazas/logs. Grafana las muestra; Alertmanager avisa.",
  },
];

const FAQ = [
  {
    q: "¿Por qué me redirige a Keycloak?",
    a: "El login no es un formulario propio: es SSO. Keycloak autentica y devuelve un JWT al frontend.",
  },
  {
    q: "¿Dónde están los “roles”?",
    a: "No hay un rol único “Admin”. Hay permisos del cliente inventory-api (product:view, stock:manage…). El frontend y la API los leen del token.",
  },
  {
    q: "Cambié el frontend y en :3000 no se ve",
    a: "Docker sirve un build estático. Hay que docker compose up --build -d frontend, o usar npm run dev en :5173.",
  },
  {
    q: "¿Cómo pruebo otro usuario?",
    a: "Cierra sesión y entra con viewer/viewer, stock-manager/stock-manager o auditor/auditor.",
  },
  {
    q: "¿Dónde miro la API?",
    a: "Swagger en http://localhost:8080/swagger-ui.html y health en /actuator/health.",
  },
  {
    q: "¿Qué es /management?",
    a: "Entrada post-login. Solo redirige a /products; las pantallas reales son /products, /stock y /dashboard.",
  },
];

export default function TechGuide() {
  const { techId } = useParams();
  const highlight = isTechId(techId) ? techId : null;
  const [openLayers, setOpenLayers] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(LAYER_ORDER.map((l) => [l, true])),
  );
  const [activeSection, setActiveSection] = useState(TOC[0].id);

  const byLayer = useMemo(
    () =>
      LAYER_ORDER.map((layer) => ({
        layer,
        items: TECH_ITEMS.filter((t) => t.layer === layer),
      })).filter((g) => g.items.length > 0),
    [],
  );

  useEffect(() => {
    const nodes = TOC.map((t) => document.getElementById(t.id)).filter(
      Boolean,
    ) as HTMLElement[];

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio);
        if (visible[0]?.target.id) setActiveSection(visible[0].target.id);
      },
      { rootMargin: "-20% 0px -55% 0px", threshold: [0.15, 0.4] },
    );

    nodes.forEach((n) => observer.observe(n));
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!highlight) return;
    const el = document.getElementById(`tech-${highlight}`);
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [highlight]);

  function toggleLayer(layer: TechLayer) {
    setOpenLayers((prev) => ({ ...prev, [layer]: !prev[layer] }));
  }

  function expandAll(open: boolean) {
    setOpenLayers(Object.fromEntries(LAYER_ORDER.map((l) => [l, open])));
  }

  return (
    <div className="guide-root">
      <header className="guide-topnav">
        <Link to="/" className="guide-brand">
          Inventario
        </Link>
        <nav className="guide-topnav-links" aria-label="Secciones">
          {TOC.slice(0, 5).map((item) => (
            <a key={item.id} href={`#${item.id}`}>
              {item.label.replace(/^\d+\.\s*/, "")}
            </a>
          ))}
        </nav>
        <Link to="/" className="guide-topnav-cta">
          Volver al inicio
        </Link>
      </header>

      <section className="guide-hero">
        <h1 className="guide-headline">
          <span>Todo lo que necesitas</span>
          <span>para entender el monorepo</span>
          <span className="guide-headline-muted">sin adivinar.</span>
        </h1>
        <p className="guide-hero-sub">
          Recorrido para quien llega nuevo: arranque, carpetas, login, roles,
          puertos y stack. Baja con scroll o usa el menú lateral.
        </p>
      </section>

      <div className="guide-shell">
        <aside className="guide-toc" aria-label="Contenido">
          <p className="guide-toc-label">Recorrido</p>
          <ol>
            {TOC.map((item) => (
              <li key={item.id}>
                <a
                  href={`#${item.id}`}
                  className={activeSection === item.id ? "is-active" : undefined}
                >
                  {item.label}
                </a>
              </li>
            ))}
          </ol>
          <p className="guide-toc-hint">
            Lee de corrido. El stack completo está abierto por capas más abajo.
          </p>
        </aside>

        <main className="guide-main">
          {/* 1 */}
          <section id="que-es" className="guide-block">
            <p className="guide-kicker">Paso 1</p>
            <h2>Qué es Inventario (ProyectoQA)</h2>
            <p className="guide-lead">
              Sistema de <strong>gestión de inventarios empresarial</strong> para
              la asignatura de Aseguramiento de Calidad (PUCMM). No es solo una
              UI: es un monorepo con API, SSO, base de datos, auditoría, tests y
              observabilidad.
            </p>
            <div className="guide-callout-grid">
              <article>
                <h3>Problema que resuelve</h3>
                <p>
                  Centralizar productos, movimientos de stock y reportes, con
                  usuarios que solo ven/hacen lo que su permiso permite.
                </p>
              </article>
              <article>
                <h3>Piezas principales</h3>
                <p>
                  React (UI) · Keycloak (login) · Spring Boot (API) · PostgreSQL
                  (datos + Envers) · Docker Compose (todo junto).
                </p>
              </article>
              <article>
                <h3>Qué vas a aprender aquí</h3>
                <p>
                  Cómo levantarlo, cómo fluye un login, qué carpeta mirar, qué
                  usuario probar y cómo se conecta el stack.
                </p>
              </article>
            </div>
          </section>

          {/* 2 */}
          <section id="arrancar" className="guide-block">
            <p className="guide-kicker">Paso 2</p>
            <h2>Arrancar en 5 minutos</h2>
            <p className="guide-lead">
              La vía rápida es Docker. Necesitas Docker en ejecución antes de
              cualquier test de integración.
            </p>
            <div className="guide-steps">
              <div className="guide-step-card">
                <span>1</span>
                <div>
                  <strong>Levantar el stack</strong>
                  <pre className="guide-inline-code">
                    <code>docker compose up --build -d</code>
                  </pre>
                </div>
              </div>
              <div className="guide-step-card">
                <span>2</span>
                <div>
                  <strong>Esperar la API</strong>
                  <p>
                    Abre{" "}
                    <a
                      href="http://localhost:8080/actuator/health"
                      target="_blank"
                      rel="noreferrer"
                    >
                      /actuator/health
                    </a>{" "}
                    hasta ver <code>{`{"status":"UP"}`}</code>.
                  </p>
                </div>
              </div>
              <div className="guide-step-card">
                <span>3</span>
                <div>
                  <strong>Abrir la app</strong>
                  <p>
                    <a href="http://localhost:3000" target="_blank" rel="noreferrer">
                      http://localhost:3000
                    </a>{" "}
                    (o <code>npm run dev</code> → :5173 para desarrollo UI).
                  </p>
                </div>
              </div>
              <div className="guide-step-card">
                <span>4</span>
                <div>
                  <strong>Entrar</strong>
                  <p>
                    Log in → Keycloak → usuario <code>admin</code> /{" "}
                    <code>admin</code> → llegas a productos.
                  </p>
                </div>
              </div>
            </div>
            <div className="guide-note">
              <strong>Tip:</strong> si solo cambias frontend y usas Docker,
              reconstruye con{" "}
              <code>docker compose up --build -d frontend</code>. Un{" "}
              <code>restart</code> no recompila la UI.
            </div>
          </section>

          {/* 3 */}
          <section id="mapa" className="guide-block">
            <p className="guide-kicker">Paso 3</p>
            <h2>Mapa mental (sin clicks)</h2>
            <p className="guide-lead">
              Guarda esta imagen mental: el usuario nunca habla solo con la base
              de datos.
            </p>
            <div className="guide-pipeline" aria-label="Arquitectura simplificada">
              {[
                "Browser",
                "React",
                "Keycloak",
                "Spring Boot",
                "PostgreSQL",
                "Obs (Grafana…)",
              ].map((label, i) => (
                <div key={label} className="guide-pipeline-item">
                  {i > 0 && <span className="guide-pipeline-arrow">→</span>}
                  <div className="guide-pipeline-node">{label}</div>
                </div>
              ))}
            </div>
            <ul className="guide-plain-list">
              <li>
                <strong>React</strong> pinta pantallas y manda el JWT en cada
                llamada.
              </li>
              <li>
                <strong>Keycloak</strong> autentica personas y firma tokens con
                permisos.
              </li>
              <li>
                <strong>Spring Boot</strong> aplica reglas de negocio y seguridad.
              </li>
              <li>
                <strong>PostgreSQL + Flyway + Envers</strong> guardan datos e
                historial.
              </li>
              <li>
                <strong>Prometheus / Tempo / Loki / Grafana</strong> dejan ver qué
                pasó en runtime.
              </li>
            </ul>
          </section>

          {/* 4 */}
          <section id="carpetas" className="guide-block">
            <p className="guide-kicker">Paso 4</p>
            <h2>Dónde está cada cosa</h2>
            <p className="guide-lead">
              Si eres nuevo, empieza por estas rutas del monorepo:
            </p>
            <div className="guide-table-wrap">
              <table className="guide-table">
                <thead>
                  <tr>
                    <th>Ruta</th>
                    <th>Para qué sirve</th>
                  </tr>
                </thead>
                <tbody>
                  {FOLDERS.map((f) => (
                    <tr key={f.path}>
                      <td>
                        <code>{f.path}</code>
                      </td>
                      <td>{f.desc}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {/* 5 */}
          <section id="login" className="guide-block">
            <p className="guide-kicker">Paso 5</p>
            <h2>Login, usuarios demo y permisos</h2>
            <p className="guide-lead">
              Flujo: Landing → Log in → Keycloak → vuelve a{" "}
              <code>/management</code> → redirige a <code>/products</code>.
              Logout vuelve a <code>/</code>.
            </p>

            <h3 className="guide-subhead">Usuarios para probar</h3>
            <div className="guide-table-wrap">
              <table className="guide-table">
                <thead>
                  <tr>
                    <th>Usuario</th>
                    <th>Password</th>
                    <th>Qué puede hacer</th>
                  </tr>
                </thead>
                <tbody>
                  {DEMO_USERS.map((u) => (
                    <tr key={u.user}>
                      <td>
                        <code>{u.user}</code>
                      </td>
                      <td>
                        <code>{u.pass}</code>
                      </td>
                      <td>{u.can}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <h3 className="guide-subhead">Permisos del cliente inventory-api</h3>
            <div className="guide-perm-grid">
              {PERMISSIONS.map((p) => (
                <div key={p.id} className="guide-perm-chip">
                  <code>{p.id}</code>
                  <span>{p.desc}</span>
                </div>
              ))}
            </div>
            <div className="guide-note">
              La API usa <code>@PreAuthorize</code>; el frontend oculta botones y
              rutas según <code>hasRole(...)</code> sobre esos mismos permisos.
            </div>
          </section>

          {/* 6 */}
          <section id="pantallas" className="guide-block">
            <p className="guide-kicker">Paso 6</p>
            <h2>Pantallas que importan</h2>
            <div className="guide-screen-grid">
              {SCREENS.map((s) => (
                <article key={s.route} className="guide-screen-card">
                  <code>{s.route}</code>
                  <h3>{s.title}</h3>
                  <p>{s.body}</p>
                </article>
              ))}
            </div>
          </section>

          {/* 7 */}
          <section id="puertos" className="guide-block">
            <p className="guide-kicker">Paso 7</p>
            <h2>Servicios y puertos</h2>
            <p className="guide-lead">
              Todo corre en Docker Compose. Guarda esta tabla cerca.
            </p>
            <div className="guide-table-wrap">
              <table className="guide-table">
                <thead>
                  <tr>
                    <th>Servicio</th>
                    <th>URL / host</th>
                    <th>Nota</th>
                  </tr>
                </thead>
                <tbody>
                  {SERVICES.map((s) => (
                    <tr key={s.name}>
                      <td>{s.name}</td>
                      <td>
                        {s.url.startsWith("http") ? (
                          <a href={s.url} target="_blank" rel="noreferrer">
                            {s.url}
                          </a>
                        ) : (
                          <code>{s.url}</code>
                        )}
                      </td>
                      <td>{s.note}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {/* 8 */}
          <section id="peticion" className="guide-block">
            <p className="guide-kicker">Paso 8</p>
            <h2>Qué pasa en una petición típica</h2>
            <p className="guide-lead">
              Ejemplo: listar productos. Léelo de arriba abajo; no hay que elegir
              nodos.
            </p>
            <div className="guide-request-list">
              {REQUEST_STEPS.map((step) => (
                <article key={step.n} className="guide-request-card">
                  <span className="guide-request-n">{step.n}</span>
                  <div>
                    <h3>{step.title}</h3>
                    <p>{step.body}</p>
                  </div>
                </article>
              ))}
            </div>
            <div className="guide-code guide-code-light">
              <div className="guide-code-bar">
                <span>Idea del cliente HTTP</span>
                <span className="guide-code-lang">ts</span>
              </div>
              <pre>
                <code>{`// frontend/src/api/client.ts (idea)
await keycloak.updateToken(30);
fetch(API + path, {
  headers: { Authorization: "Bearer " + keycloak.token }
});`}</code>
              </pre>
            </div>
          </section>

          {/* 9 */}
          <section id="stack" className="guide-block">
            <p className="guide-kicker">Paso 9</p>
            <h2>Stack completo (todo visible)</h2>
            <p className="guide-lead">
              Agrupado por capa. Expande/colapsa si quieres, pero por defecto
              está abierto para leer de corrido.
            </p>
            <div className="guide-stack-toolbar">
              <button type="button" onClick={() => expandAll(true)}>
                Expandir todo
              </button>
              <button type="button" onClick={() => expandAll(false)}>
                Colapsar todo
              </button>
            </div>

            {byLayer.map(({ layer, items }) => (
              <div key={layer} className="guide-layer-accordion">
                <button
                  type="button"
                  className="guide-layer-toggle"
                  aria-expanded={openLayers[layer]}
                  onClick={() => toggleLayer(layer)}
                >
                  <span>
                    {LAYER_LABEL[layer]}{" "}
                    <em>({items.length})</em>
                  </span>
                  <span aria-hidden>{openLayers[layer] ? "−" : "+"}</span>
                </button>
                {openLayers[layer] && (
                  <div className="guide-tech-list">
                    {items.map((item) => (
                      <article
                        key={item.id}
                        id={`tech-${item.id}`}
                        className={`guide-tech-doc${highlight === item.id ? " is-highlight" : ""}`}
                      >
                        <header className="guide-tech-doc-head">
                          <span
                            className="guide-catalog-icon"
                            style={{
                              background: `${item.color}18`,
                              borderColor: `${item.color}55`,
                            }}
                          >
                            <TechIcon
                              id={item.id}
                              size={22}
                              color={item.color}
                            />
                          </span>
                          <div>
                            <h3>{item.name}</h3>
                            <p>{item.role}</p>
                          </div>
                        </header>
                        <p>{item.summary}</p>
                        <ul>
                          {item.howItFits.map((line) => (
                            <li key={line}>{line}</li>
                          ))}
                        </ul>
                        <p className="guide-tech-links">
                          <strong>Conecta con:</strong>{" "}
                          {item.connectsTo
                            .map((id) => TECH_BY_ID[id].shortName)
                            .join(" · ")}
                        </p>
                        <div className="guide-code guide-code-light">
                          <div className="guide-code-bar">
                            <span>{item.code.title}</span>
                            <span className="guide-code-lang">
                              {item.code.language}
                            </span>
                          </div>
                          <pre>
                            <code>{item.code.snippet}</code>
                          </pre>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </section>

          {/* 10 */}
          <section id="tests" className="guide-block">
            <p className="guide-kicker">Paso 10</p>
            <h2>Tests y CI (visión rápida)</h2>
            <div className="guide-callout-grid">
              <article>
                <h3>Backend</h3>
                <p>
                  JUnit + Mockito; integración con Testcontainers (Postgres /
                  Keycloak). Comando típico: <code>./gradlew test</code>.
                </p>
              </article>
              <article>
                <h3>Frontend E2E</h3>
                <p>
                  Playwright en <code>frontend/e2e</code>: login real, productos,
                  permisos. <code>cd frontend && npm run test:e2e</code>.
                </p>
              </article>
              <article>
                <h3>CI</h3>
                <p>
                  GitHub Actions en el repo; Jenkins opcional en :8082 para
                  demos locales.
                </p>
              </article>
            </div>
          </section>

          {/* 11 */}
          <section id="faq" className="guide-block">
            <p className="guide-kicker">Paso 11</p>
            <h2>Preguntas frecuentes de quien llega nuevo</h2>
            <div className="guide-faq">
              {FAQ.map((item) => (
                <details key={item.q} className="guide-faq-item">
                  <summary>{item.q}</summary>
                  <p>{item.a}</p>
                </details>
              ))}
            </div>
          </section>

          <footer className="guide-footer">
            <p>
              Siguiente paso: arranca Docker, entra como <code>viewer</code> y
              luego como <code>admin</code> para notar la diferencia de
              permisos.
            </p>
            <Link to="/" className="guide-footer-cta">
              Volver al inicio
            </Link>
          </footer>
        </main>
      </div>
    </div>
  );
}
