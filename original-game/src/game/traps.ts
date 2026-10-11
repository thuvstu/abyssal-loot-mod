import { clamp, rnd, rndInt, type RNG } from "./core";

export type Trap = { id: number; x: number; y: number; kind: "spike" | "rock"; state: "armed" | "warning" | "spent"; timer: number; radius: number; warning: number };
export type TrapActor = { id: number; x: number; y: number; r: number; alive: boolean };
export type TrapBlast = { x: number; y: number; radius: number; kind: Trap["kind"] };

export function generateTraps(rng: RNG, floor: number, width: number, height: number, blocked: { x: number; y: number; r: number }[]): Trap[] {
  if (floor < 5 || floor === 26) return [];
  const out: Trap[] = [];
  const count = Math.min(8, 3 + Math.floor(floor / 5));
  for (let attempt = 0; attempt < 100 && out.length < count; attempt++) {
    const x = rnd(rng, 160, width - 160), y = rnd(rng, 160, height - 160);
    if (Math.hypot(x - width / 2, y - height / 2) < 280) continue;
    if ([...blocked, ...out.map((t) => ({ ...t, r: 70 }))].some((p) => Math.hypot(p.x - x, p.y - y) < p.r + 75)) continue;
    const kind = rndInt(rng, 0, 1) === 0 ? "spike" : "rock";
    out.push({ id: out.length, x, y, kind, state: "armed", timer: 0, radius: kind === "spike" ? 92 : 140, warning: kind === "spike" ? 0.36 : 0.72 });
  }
  return out;
}

/** One-use, shared hazards. A player and an enemy on the same plate cannot trigger two explosions. */
export function tickTraps(traps: Trap[], dt: number, actors: TrapActor[]): TrapBlast[] {
  const blasts: TrapBlast[] = [];
  for (const t of traps) {
    if (t.state === "spent") continue;
    if (t.state === "armed") {
      if (actors.some((a) => a.alive && Math.hypot(a.x - t.x, a.y - t.y) <= 27 + a.r)) {
        t.state = "warning"; t.timer = t.warning;
      }
      continue;
    }
    t.timer -= Math.max(0, dt);
    if (t.timer <= 0) {
      t.state = "spent";
      blasts.push({ x: t.x, y: t.y, radius: t.radius, kind: t.kind });
    }
  }
  return blasts;
}

export function drawTraps(g: CanvasRenderingContext2D, traps: Trap[], time: number, px: number, py: number, font: string) {
  for (const t of traps) {
    g.save(); g.translate(t.x, t.y);
    const color = t.kind === "spike" ? "#f4ad67" : "#a9bddb";
    const warning = t.state === "warning";
    g.globalAlpha = t.state === "spent" ? 0.3 : 0.9;
    g.fillStyle = "#17151e"; g.strokeStyle = warning ? "#ff5369" : color; g.lineWidth = 2;
    g.beginPath(); g.moveTo(0, -32); g.lineTo(36, 0); g.lineTo(0, 26); g.lineTo(-36, 0); g.closePath(); g.fill(); g.stroke();
    if (t.kind === "spike") {
      for (const x of [-12, 0, 12]) { g.beginPath(); g.moveTo(x - 4, 4); g.lineTo(x, -12); g.lineTo(x + 4, 4); g.stroke(); }
    } else {
      g.beginPath(); g.moveTo(-13, -5); g.lineTo(-8, -17); g.lineTo(10, -14); g.lineTo(15, 0); g.lineTo(3, 9); g.lineTo(-13, -5); g.stroke();
    }
    if (warning) {
      const p = 1 - clamp(t.timer / t.warning, 0, 1);
      g.globalAlpha = 0.17 + p * 0.25; g.fillStyle = "#ff5369";
      g.beginPath(); g.arc(0, 0, t.radius, 0, Math.PI * 2); g.fill();
      g.globalAlpha = 0.9; g.setLineDash([9, 5]); g.lineDashOffset = -time * 35;
      g.beginPath(); g.arc(0, 0, t.radius, 0, Math.PI * 2); g.stroke();
      g.setLineDash([]); g.lineWidth = 4;
      g.beginPath(); g.arc(0, 0, 42, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * p); g.stroke();
    }
    if (Math.hypot(t.x - px, t.y - py) < 310 || warning) {
      g.globalAlpha = t.state === "spent" ? 0.4 : 1; g.font = `13px ${font}`; g.textAlign = "center";
      g.strokeStyle = "#06050b"; g.lineWidth = 4; g.fillStyle = warning ? "#ffe1ce" : color;
      const label = t.state === "spent" ? "作動済" : `${t.kind === "spike" ? "針罠" : "岩罠"}${warning ? " ─ 回避!" : ""}`;
      g.strokeText(label, 0, -44); g.fillText(label, 0, -44);
    }
    g.restore();
  }
}
