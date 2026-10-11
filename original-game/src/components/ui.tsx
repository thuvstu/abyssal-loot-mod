"use client";

import type { CSSProperties, ReactNode } from "react";
import { Item, RARITY_META, Rarity, SLOT_META, STAT_META, Slot, StatKey } from "@/game/core";

/* ------------------------------------------------------------- スロット図 */

const PATHS: Record<string, ReactNode> = {
  sword: (
    <>
      <path d="M12 2 L15 6 L15 15 L12 18 L9 15 L9 6 Z" />
      <path d="M6 17 H18" />
      <path d="M12 18 V22" />
    </>
  ),
  helm: (
    <>
      <path d="M5 12 A7 7 0 0 1 19 12 V18 L15 21 H9 L5 18 Z" />
      <path d="M12 8 V18" />
      <path d="M8 13 H16" />
    </>
  ),
  armor: (
    <>
      <path d="M12 3 L20 6 V12 C20 17 16 20 12 21 C8 20 4 17 4 12 V6 Z" />
      <path d="M12 3 V21" />
    </>
  ),
  glove: (
    <>
      <path d="M7 21 V11 L6 8 V4 H9 V9 H11 V3 H14 V9 H16 V5 H18 V13 L17 21 Z" />
    </>
  ),
  boot: (
    <>
      <path d="M7 3 H12 V12 L19 15 V21 H7 Z" />
      <path d="M7 16 H19" />
    </>
  ),
  ring: (
    <>
      <circle cx="12" cy="15" r="6" />
      <path d="M12 3 L15 7 H9 Z" />
    </>
  ),
  amulet: (
    <>
      <path d="M6 3 L12 10 L18 3" />
      <path d="M12 10 L16 15 L12 21 L8 15 Z" />
    </>
  ),
};

export function SlotIcon({ slot, className = "", style }: { slot: Slot; className?: string; style?: CSSProperties }) {
  return (
    <svg viewBox="0 0 24 24" className={className} style={style} fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" strokeLinecap="round" aria-hidden>
      {PATHS[SLOT_META[slot].icon]}
    </svg>
  );
}

/* ------------------------------------------------------------------ 部品 */

export function Panel({ title, right, children, className = "", tone = "" }: { title?: ReactNode; right?: ReactNode; children: ReactNode; className?: string; tone?: string }) {
  return (
    <section className={`panel corner relative ${tone} ${className}`}>
      {title && (
        <header className="flex items-center justify-between gap-3 border-b border-line/80 px-3 py-2">
          <h3 className="font-display text-[13px] font-bold tracking-[0.18em] text-bone/90 uppercase">{title}</h3>
          {right}
        </header>
      )}
      {children}
    </section>
  );
}

export function Bar({ value, max, color, bg = "rgba(255,255,255,.08)", h = 8, glow = true }: { value: number; max: number; color: string; bg?: string; h?: number; glow?: boolean }) {
  const p = max > 0 ? Math.max(0, Math.min(1, value / max)) : 0;
  return (
    <div className="relative w-full overflow-hidden rounded-[2px]" style={{ height: h, background: bg }}>
      <div
        className="absolute inset-y-0 left-0 transition-[width] duration-150 ease-out"
        style={{ width: `${p * 100}%`, background: color, boxShadow: glow ? `0 0 12px ${color}` : undefined }}
      />
    </div>
  );
}

export function RarityTag({ rarity, small }: { rarity: Rarity; small?: boolean }) {
  const m = RARITY_META[rarity];
  return (
    <span
      className={`inline-block border font-bold tracking-widest ${small ? "px-1.5 py-px text-[9px]" : "px-2 py-0.5 text-[10px]"}`}
      style={{ color: m.color, borderColor: `${m.color}66`, background: m.bg }}
    >
      {m.label}
    </span>
  );
}

export function ItemName({ item, className = "" }: { item: Item; className?: string }) {
  return (
    <span className={className} style={{ color: RARITY_META[item.rarity].color }}>
      {item.name}
    </span>
  );
}

export function ItemCard({ item, compact, highlight }: { item: Item | null; compact?: boolean; highlight?: boolean }) {
  if (!item) {
    return (
      <div className="flex h-full min-h-[74px] items-center justify-center border border-dashed border-line/70 px-2 text-[11px] text-ash/60">
        空
      </div>
    );
  }
  const m = RARITY_META[item.rarity];
  return (
    <div
      className={`relative overflow-hidden border px-2.5 py-2 ${compact ? "" : ""} ${highlight ? "holo" : ""}`}
      style={{ borderColor: `${m.color}55`, background: `linear-gradient(180deg, ${m.bg}, rgba(8,6,14,.92))` }}
    >
      <div className="flex items-start gap-2">
        <SlotIcon slot={item.slot} className="mt-0.5 h-5 w-5 shrink-0" />
        <div className="min-w-0 flex-1">
          <div className="truncate font-display text-[13px] font-bold leading-tight" style={{ color: m.color }}>
            {item.name}
          </div>
          <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[10px] text-ash">
            <span>{SLOT_META[item.slot].label}</span>
            <span className="opacity-50">|</span>
            <span>{m.label}</span>
            <span className="opacity-50">|</span>
            <span>iLv {item.ilvl}</span>
            <span className="opacity-50">|</span>
            <span className="text-gold/80">総合力 {item.power}</span>
            {item.cursed && (
              <>
                <span className="opacity-50">|</span>
                <span className="text-blood">【呪縛】</span>
              </>
            )}
          </div>
        </div>
      </div>
      {!compact && Object.keys(item.mods).length > 0 && (
        <ul className="mt-1.5 space-y-0.5 border-t border-white/5 pt-1.5">
          {Object.entries(item.mods).map(([k, v]) => {
            const key = k as StatKey;
            if (!v) return null;
            return (
              <li key={k} className="text-[11px] leading-tight" style={{ color: v > 0 ? "#8ce8b8" : "#ff8a9a" }}>
                {STAT_META[key].label} <span className="tabular">{STAT_META[key].fmt(v)}</span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export function StatLine({ k, v, dim }: { k: StatKey; v: number; dim?: boolean }) {
  return (
    <div className={`flex items-baseline justify-between gap-2 text-[11px] ${dim ? "text-ash/60" : "text-bone/85"}`}>
      <span>{STAT_META[k].label}</span>
      <span className="tabular font-bold">{STAT_META[k].fmt(v)}</span>
    </div>
  );
}

export function KeyCap({ children }: { children: ReactNode }) {
  return (
    <kbd className="inline-flex min-w-[22px] items-center justify-center border border-line bg-panel2 px-1.5 py-0.5 text-[10px] font-bold text-bone/90 shadow-[0_2px_0_#0a0812]">
      {children}
    </kbd>
  );
}
