import type { CSSProperties } from "react";
import { Link } from "react-router-dom";
import TechIcon from "./TechIcon";
import type { TechItem } from "../data/techGuide";

type Props = {
  tech: TechItem;
  compact?: boolean;
  light?: boolean;
};

export default function TechChip({ tech, compact, light }: Props) {
  return (
    <Link
      to={`/guide/${tech.id}`}
      className={`tech-chip${compact ? " tech-chip-compact" : ""}${light ? " tech-chip-light" : ""}`}
      style={{ "--chip-color": tech.color } as CSSProperties}
      title={tech.name}
    >
      <span className="tech-chip-icon" aria-hidden>
        <TechIcon id={tech.id} size={compact ? 14 : 16} color={tech.color} />
      </span>
      <span className="tech-chip-label">{tech.shortName}</span>
    </Link>
  );
}
