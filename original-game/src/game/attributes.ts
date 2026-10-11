import type { Mods } from "./core";

export const ATTRIBUTES = [
  { id: "strength", name: "剛力", icon: "刃", color: "#f49b89", description: "攻撃力 +3% / 点" },
  { id: "rhythm", name: "韻律", icon: "律", color: "#b6a2e6", description: "攻撃速度 +2% / 点" },
  { id: "agility", name: "敏捷", icon: "風", color: "#80d7bb", description: "移動速度 +1.5%・回避短縮 +1% / 点" },
  { id: "endurance", name: "忍耐", icon: "盾", color: "#93bde4", description: "最大HP +4% / 点" },
  { id: "fortune", name: "幸運", icon: "星", color: "#e7c56f", description: "運 +7・素材獲得量 +4% / 点" },
] as const;
export type AttributeId = (typeof ATTRIBUTES)[number]["id"];
export type Attributes = Record<AttributeId, number>;
export const emptyAttributes = (): Attributes => ({ strength: 0, rhythm: 0, agility: 0, endurance: 0, fortune: 0 });
export const spentPoints = (a: Attributes): number => ATTRIBUTES.reduce((sum, x) => sum + a[x.id], 0);
export const availablePoints = (level: number, a: Attributes): number => Math.max(0, Math.floor(level) - 1 - spentPoints(a));

export function attributeMods(a: Attributes): Mods {
  return { damagePct: a.strength * 3, attackSpeedPct: a.rhythm * 2, moveSpeedPct: a.agility * 1.5,
    dashCdPct: a.agility, maxHpPct: a.endurance * 4, luckFlat: a.fortune * 7 };
}

export function allocatePoint(a: Attributes, id: AttributeId, level: number, amount = 1): Attributes | null {
  if (!ATTRIBUTES.some((x) => x.id === id) || !Number.isSafeInteger(amount) || amount <= 0 || availablePoints(level, a) < amount) return null;
  return { ...a, [id]: a[id] + amount };
}

export function validateAttributes(value: unknown, level: number): Attributes {
  if (value === undefined) return emptyAttributes();
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("属性配分が不正です。");
  const raw = value as Record<string, unknown>;
  const result = emptyAttributes();
  if (Object.keys(raw).some((k) => !ATTRIBUTES.some((x) => x.id === k))) throw new Error("不明な属性が指定されています。");
  for (const { id } of ATTRIBUTES) {
    const n = raw[id] ?? 0;
    if (typeof n !== "number" || !Number.isSafeInteger(n) || n < 0 || n > 998) throw new Error("属性ポイントは0以上の整数で指定してください。");
    result[id] = n;
  }
  if (spentPoints(result) > Math.max(0, level - 1)) throw new Error("獲得した属性ポイントを超えています。");
  return result;
}

/** Fractional material bonuses use seeded stochastic rounding, preserving +4% even for one shard. */
export function materialAmount(base: number, fortune: number, rng: () => number): number {
  const exact = Math.max(0, base) * (1 + Math.max(0, fortune) * 0.04);
  const whole = Math.floor(exact);
  return whole + (rng() < exact - whole ? 1 : 0);
}
