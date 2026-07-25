import type { TechId } from "../data/techGuide";

type Props = {
  id: TechId;
  size?: number;
  color?: string;
};

/** Iconos SVG simples por tecnología (sin dependencia externa). */
export default function TechIcon({ id, size = 16, color = "currentColor" }: Props) {
  const common = {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    "aria-hidden": true as const,
  };

  switch (id) {
    case "react":
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="2.2" fill={color} />
          <ellipse cx="12" cy="12" rx="10" ry="4" stroke={color} strokeWidth="1.4" />
          <ellipse
            cx="12"
            cy="12"
            rx="10"
            ry="4"
            stroke={color}
            strokeWidth="1.4"
            transform="rotate(60 12 12)"
          />
          <ellipse
            cx="12"
            cy="12"
            rx="10"
            ry="4"
            stroke={color}
            strokeWidth="1.4"
            transform="rotate(120 12 12)"
          />
        </svg>
      );
    case "typescript":
      return (
        <svg {...common} fill={color}>
          <rect x="2" y="2" width="20" height="20" rx="3" />
          <path
            d="M7.5 10.2h9v1.8h-3.4V18H11V12H7.5v-1.8z"
            fill="#0a0a0a"
          />
        </svg>
      );
    case "vite":
      return (
        <svg {...common} fill={color}>
          <path d="M12 2L3 6.5l1.8 12L12 22l7.2-3.5L21 6.5 12 2z" opacity="0.9" />
          <path d="M12 5.5L8 16h2.2l.7-2h2.2l.7 2H16L12 5.5zm0 4.2l.8 2.8h-1.6L12 9.7z" fill="#0a0a0a" />
        </svg>
      );
    case "spring-boot":
      return (
        <svg {...common} fill={color}>
          <path d="M18.5 5.5c-2.8-2.4-7.8-2-10.6.8-2.5 2.5-2.7 6.3-.8 9.1l-2.6 2.6 1.1 1.1 2.6-2.6c2.8 1.9 6.6 1.7 9.1-.8 2.8-2.8 3.2-7.8.8-10.2l-2.2 2.2c1.2 1.4 1 3.6-.4 5-1.4 1.4-3.6 1.6-5 .4-1.5-1.3-1.7-3.5-.4-5 1.2-1.2 3-1.5 4.5-.7l3.9-3.9z" />
        </svg>
      );
    case "postgresql":
      return (
        <svg {...common} fill={color}>
          <path d="M12 2c-3.5 0-6 2.2-6 6.2 0 2.6 1.2 4.4 2.4 5.8.7.8 1.1 1.5 1.1 2.3V18h1.8v-1.7c0-1.3.5-2.3 1.3-3.2C13.8 11.7 15 10.2 15 8.2 15 4.2 14 2 12 2zm0 2c.8 0 1.5.8 1.5 2.5S12.7 9 12 9s-1.5-.8-1.5-2.5S11.2 4 12 4zM9.2 19.2h5.6V21H9.2v-1.8z" />
        </svg>
      );
    case "flyway":
      return (
        <svg {...common} fill={color}>
          <path d="M4 18h16v2H4v-2zm2-4h3v3H6v-3zm5-4h3v7h-3V10zm5-4h3v11h-3V6zM4 4l8-2 8 2v2H4V4z" />
        </svg>
      );
    case "envers":
      return (
        <svg {...common} fill={color}>
          <path d="M12 2L4 6v6c0 5 3.4 9.4 8 10 4.6-.6 8-5 8-10V6l-8-4zm0 2.2l6 3v4.9c0 3.7-2.4 7-6 7.7-3.6-.7-6-4-6-7.7V7.2l6-3z" />
          <path d="M11 8h2v5h-2V8zm0 6h2v2h-2v-2z" />
        </svg>
      );
    case "keycloak":
      return (
        <svg {...common} fill={color}>
          <path d="M12 2l8 4v6c0 5-3.5 9.3-8 10-4.5-.7-8-5-8-10V6l8-4zm0 3L7 7.5V12c0 3.2 2.1 6 5 6.7 2.9-.7 5-3.5 5-6.7V7.5L12 5z" />
          <circle cx="12" cy="11" r="2" />
        </svg>
      );
    case "docker":
      return (
        <svg {...common} fill={color}>
          <path d="M4 14h2v2H4v-2zm3 0h2v2H7v-2zm3 0h2v2h-2v-2zm3 0h2v2h-2v-2zM7 11h2v2H7v-2zm3 0h2v2h-2v-2zm3 0h2v2h-2v-2zm3 0h2v2h-2v-2zM10 8h2v2h-2V8zm3 0h2v2h-2V8z" />
          <path d="M3 17h15.5c2 0 3.5-1 4.2-2.8.2-.5-.1-1-.6-1H20c.1-2.2-1-4-3.2-4.6-.6-2.2-2.5-3.6-4.8-3.6-2.2 0-4.1 1.4-4.8 3.4H6.5C4.6 8.4 3 10 3 12.2V17z" opacity="0.35" />
        </svg>
      );
    case "junit":
      return (
        <svg {...common} fill={color}>
          <path d="M7 3h10v2H7V3zm1 4h8l-1 12H9L8 7zm2.2 2l.5 8h2.6l.5-8h-3.6z" />
        </svg>
      );
    case "testcontainers":
      return (
        <svg {...common} fill={color}>
          <rect x="3" y="5" width="18" height="14" rx="2" stroke={color} strokeWidth="1.6" fill="none" />
          <path d="M7 9h4v4H7V9zm6 0h4v2h-4V9zm0 4h4v2h-4v-2z" fill={color} />
        </svg>
      );
    case "playwright":
      return (
        <svg {...common} fill={color}>
          <circle cx="8" cy="12" r="3" />
          <circle cx="16" cy="12" r="3" />
          <path d="M5 7c2-2 12-2 14 0M5 17c2 2 12 2 14 0" stroke={color} strokeWidth="1.5" fill="none" />
        </svg>
      );
    case "github-actions":
      return (
        <svg {...common} fill={color}>
          <path d="M12 2a10 10 0 00-3.2 19.5c.5.1.7-.2.7-.5v-1.7c-2.8.6-3.4-1.2-3.4-1.2-.4-1.1-1-1.4-1-1.4-.9-.6.1-.6.1-.6 1 .1 1.5 1 1.5 1 .9 1.5 2.3 1.1 2.9.8.1-.7.3-1.1.6-1.3-2.2-.3-4.6-1.1-4.6-5 0-1.1.4-2 1-2.7-.1-.3-.4-1.3.1-2.7 0 0 .8-.3 2.8 1a9.7 9.7 0 015 0c2-1.3 2.8-1 2.8-1 .5 1.4.2 2.4.1 2.7.7.7 1.1 1.6 1.1 2.7 0 3.9-2.4 4.7-4.6 5 .4.3.7.9.7 1.8v2.6c0 .3.2.6.7.5A10 10 0 0012 2z" />
        </svg>
      );
    case "jenkins":
      return (
        <svg {...common} fill={color}>
          <circle cx="12" cy="8" r="4" />
          <path d="M8 13c0 2 1.5 5 4 7 2.5-2 4-5 4-7H8z" />
        </svg>
      );
    case "prometheus":
      return (
        <svg {...common} fill={color}>
          <circle cx="12" cy="12" r="9" stroke={color} strokeWidth="1.5" fill="none" />
          <path d="M12 6v6l4 2" stroke={color} strokeWidth="1.6" strokeLinecap="round" />
        </svg>
      );
    case "grafana":
      return (
        <svg {...common} fill={color}>
          <path d="M12 3a9 9 0 100 18 9 9 0 000-18zm1 4v5.2l3.5 2-1 1.7L11 13V7h2z" />
        </svg>
      );
    case "opentelemetry":
      return (
        <svg {...common} fill={color}>
          <circle cx="6" cy="12" r="2.5" />
          <circle cx="12" cy="7" r="2.5" />
          <circle cx="12" cy="17" r="2.5" />
          <circle cx="18" cy="12" r="2.5" />
          <path d="M8.2 10.5l2-2M8.2 13.5l2 2M15.8 10.5l-2-2M15.8 13.5l-2 2" stroke={color} strokeWidth="1.4" />
        </svg>
      );
    case "tempo":
      return (
        <svg {...common} fill={color}>
          <path d="M4 6h16v2H4V6zm2 4h3v10H6V10zm5 0h3v10h-3V10zm5 0h3v10h-3V10z" />
        </svg>
      );
    case "loki":
      return (
        <svg {...common} fill={color}>
          <path d="M5 5h14v3H5V5zm0 5h10v3H5v-3zm0 5h14v3H5v-3z" />
        </svg>
      );
    case "alloy":
      return (
        <svg {...common} fill={color}>
          <path d="M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3zm0 2.3L7 8v8l5 2.8 5-2.8V8l-5-2.7z" />
          <circle cx="12" cy="12" r="2.2" />
        </svg>
      );
    case "alertmanager":
      return (
        <svg {...common} fill={color}>
          <path d="M12 3l9 16H3L12 3zm0 4.5L7.2 17h9.6L12 7.5z" />
          <path d="M11 11h2v3h-2v-3zm0 4h2v2h-2v-2z" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="8" stroke={color} strokeWidth="1.5" />
        </svg>
      );
  }
}
