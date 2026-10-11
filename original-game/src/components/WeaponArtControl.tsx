"use client";

import type { Snapshot } from "@/game/engine";

export default function WeaponArtControl({ snapshot, onUse, touch = false }: { snapshot: Snapshot | null; onUse: () => void; touch?: boolean }) {
  const gauge = Math.max(0, Math.min(100, snapshot?.weaponArtGauge ?? 0));
  const ready = gauge >= 100;
  const color = snapshot?.weaponArtColor ?? "#d3bd8e";
  return (
    <button type="button" className={`weapon-art-control ${touch ? "touch-art" : ""} ${ready ? "art-ready" : ""}`} disabled={!ready || snapshot?.status !== "playing"}
      style={{ "--art-color": color } as React.CSSProperties}
      onPointerDown={(e) => { e.preventDefault(); e.stopPropagation(); onUse(); }}
      onClick={(e) => { if (e.detail === 0) onUse(); }} title={snapshot?.weaponArtDescription} aria-label={`${snapshot?.weaponArtName ?? "武器奥義"} ${Math.floor(gauge)}%`}>
      <svg viewBox="0 0 64 64" aria-hidden><circle cx="32" cy="32" r="29" className="art-track"/><circle cx="32" cy="32" r="29" className="art-progress" strokeDasharray={Math.PI * 58} strokeDashoffset={Math.PI * 58 * (1 - gauge / 100)}/></svg>
      <span className="art-glyph">{snapshot?.weaponArtGlyph ?? "奥"}</span>
      <span className="art-label">{snapshot?.weaponArtName ?? "武器奥義"}</span>
      <span className="art-key">{ready ? (touch ? "奥義 発動" : "[E] 発動") : `${Math.floor(gauge)}%`}</span>
    </button>
  );
}
