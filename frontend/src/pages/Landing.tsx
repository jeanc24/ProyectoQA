import { useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import TechIcon from "../components/TechIcon";
import { TECH_ITEMS } from "../data/techGuide";
import "../styles/landing.css";

export default function Landing() {
  const { isAuthenticated, login } = useAuth();
  const navigate = useNavigate();

  const mid = Math.ceil(TECH_ITEMS.length / 2);
  const rowA = TECH_ITEMS.slice(0, mid);
  const rowB = TECH_ITEMS.slice(mid);

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/management", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  return (
    <div className="landing-root">
      <header className="landing-nav">
        <a className="landing-logo" href="/">
          Inventario
        </a>

        <div className="landing-nav-actions">
          <Link to="/guide" className="landing-nav-link">
            Guía técnica
          </Link>
          <button
            type="button"
            className="landing-nav-link"
            data-testid="login-button"
            onClick={login}
          >
            Log in
          </button>
        </div>
      </header>

      <main className="landing-hero" id="producto">
        <h1 className="landing-headline">
          <span>Listo para controlar</span>
          <span>tu inventario</span>
          <span className="landing-headline-muted">con claridad?</span>
        </h1>
        <p className="landing-sub">
          Entra con Keycloak, gestiona productos y stock según tu rol, y usa la
          guía técnica cuando quieras entender cómo está armado el monorepo.
        </p>
        <button
          type="button"
          className="landing-btn landing-btn-solid landing-btn-lg"
          onClick={login}
        >
          Iniciar sesión
        </button>
      </main>

      <section className="landing-trust" aria-label="Tecnologías del stack">
        <div className="landing-marquee">
          <div className="landing-marquee-row">
            <div className="landing-marquee-track">
              {[...rowA, ...rowA].map((tech, i) => (
                <span key={`${tech.id}-a-${i}`} className="landing-tech-item">
                  <TechIcon id={tech.id} size={18} color={tech.color} />
                  <span>{tech.shortName}</span>
                </span>
              ))}
            </div>
          </div>
          <div className="landing-marquee-row">
            <div className="landing-marquee-track landing-marquee-track-rev">
              {[...rowB, ...rowB].map((tech, i) => (
                <span key={`${tech.id}-b-${i}`} className="landing-tech-item">
                  <TechIcon id={tech.id} size={18} color={tech.color} />
                  <span>{tech.shortName}</span>
                </span>
              ))}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
