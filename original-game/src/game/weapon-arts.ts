import type { WeaponClassId } from "./core";

export const WEAPON_ARTS: Record<WeaponClassId, { name: string; description: string; color: string; glyph: string }> = {
  sword: { name: "月牙斬", description: "3発の貫通斬撃波。敵の列をまとめて斬り抜く。", color: "#d6e8ff", glyph: "月" },
  axe: { name: "大地砕き", description: "周囲を大爆発させ、敵を強く吹き飛ばす。", color: "#ff9465", glyph: "砕" },
  spear: { name: "竜殺し", description: "狙った方向へ無敵で駆け抜け、経路上の全員を強襲。", color: "#86dcf4", glyph: "竜" },
  staff: { name: "星辰大爆裂", description: "最寄りの敵の位置に、0.6秒後に巨大隕石が落ちる。", color: "#bca2ff", glyph: "星" },
  bow: { name: "疾風連雨", description: "13本の矢を連射。発射ごとに最寄りの敵へ自動照準。", color: "#92ebbd", glyph: "雨" },
  dagger: { name: "影襲", description: "最寄りの敵の背後へ瞬移し、単体へ強烈な一撃。", color: "#e4c8ff", glyph: "影" },
  hammer: { name: "崩壊スマッシュ", description: "単体へ極大ダメージ。衝撃が感電とスタンを伝播。", color: "#f3cf75", glyph: "崩" },
  katana: { name: "心無一刀", description: "周囲すべてを一文字に断つ。敵弾も斬り払う。", color: "#ffb5bf", glyph: "無" },
  scythe: { name: "冥府の収穫", description: "周囲を刈り取り、低HPの敵には追加ダメージ。", color: "#aff097", glyph: "冥" },
  gauntlet: { name: "六連震掌", description: "前方に6連続の衝撃を打ち込み、最後の一撃で吹き飛ばす。", color: "#ffd092", glyph: "掌" },
  chakram: { name: "輪廻乱舞", description: "16枚の貫通輪刃を全方位へ射出する。", color: "#91f2eb", glyph: "輪" },
  tome: { name: "禁書・虚空葬", description: "狙った地点に虚空の爆縮。周囲へ脆弱を刻む。", color: "#de9df7", glyph: "禁" },
};

export type ArtTarget = { id: number; x: number; y: number; angle: number; hp: number; r: number };
export type ArtCommand = {
  delay: number;
  kind: "wave" | "burst" | "dash" | "teleport" | "arrow" | "hammer" | "cut" | "fist";
  x: number; y: number; angle: number;
  multiplier: number;
  radius: number;
  targetId?: number;
  index?: number;
};

/** A pure plan; its timing and geometry are tested independently of rendering. */
export function planWeaponArt(style: WeaponClassId, x: number, y: number, angle: number, targets: ArtTarget[]): ArtCommand[] {
  const nearest = [...targets].sort((a, b) => Math.hypot(a.x - x, a.y - y) - Math.hypot(b.x - x, b.y - y))[0];
  const at = { x: nearest?.x ?? x + Math.cos(angle) * 220, y: nearest?.y ?? y + Math.sin(angle) * 220 };
  const cmd = (kind: ArtCommand["kind"], multiplier: number, radius: number, delay = 0): ArtCommand => ({ kind, x, y, angle, multiplier, radius, delay });
  switch (style) {
    case "sword": return [-0.18, 0, 0.18].map((a) => ({ ...cmd("wave", 2.1, 14), angle: angle + a }));
    case "axe": return [cmd("burst", 4.2, 270)];
    case "spear": return [cmd("dash", 4.8, 62)];
    case "staff": return [{ ...cmd("burst", 6.5, 260, 0.6), ...at }];
    case "bow": return Array.from({ length: 13 }, (_, index) => ({ ...cmd("arrow", 0.82, 9, index * 0.075), index }));
    case "dagger": {
      const behind = nearest ? { x: nearest.x - Math.cos(nearest.angle) * (nearest.r + 42), y: nearest.y - Math.sin(nearest.angle) * (nearest.r + 42) } : { x: x + Math.cos(angle) * 180, y: y + Math.sin(angle) * 180 };
      return [{ ...cmd("teleport", 0, 0), ...behind }, { ...cmd("hammer", 7, 105, 0.02), ...at, targetId: nearest?.id }];
    }
    case "hammer": return [{ ...cmd("hammer", 8, 190), ...at, targetId: nearest?.id }];
    case "katana": return [cmd("cut", 5.6, 370)];
    case "scythe": return [cmd("cut", 3.8, 290)];
    case "gauntlet": return Array.from({ length: 6 }, (_, index) => ({ ...cmd("fist", index === 5 ? 2.7 : 0.65, index === 5 ? 170 : 115, index * 0.1), index }));
    case "chakram": return Array.from({ length: 16 }, (_, i) => ({ ...cmd("wave", 1.35, 12), angle: angle + i * Math.PI / 8 }));
    case "tome": return [{ ...cmd("burst", 5.5, 290, 0.75), ...at }];
  }
}

export const normalizeAngle = (v: number): number => Math.atan2(Math.sin(v), Math.cos(v));
export const turnToward = (from: number, to: number, maxTurn: number): number => from + Math.max(-maxTurn, Math.min(maxTurn, normalizeAngle(to - from)));

/** incomingAngle is the travel direction of the hit. The shield faces the attacker. */
export function guardMultiplier(facing: number, incomingAngle: number): number {
  const towardSource = incomingAngle + Math.PI;
  return Math.abs(normalizeAngle(towardSource - facing)) <= Math.PI / 3 ? 0.3 : 1.35;
}

/** No charge from an art's own damage or from overkill. */
export function artChargeGain(actualDamage: number, attack: number, artDamage = false): number {
  if (artDamage || !Number.isFinite(actualDamage) || actualDamage <= 0) return 0;
  return Math.min(7, actualDamage / Math.max(10, attack) * 3);
}
