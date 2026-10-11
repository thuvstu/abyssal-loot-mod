/** Shared between the simulation, preparation screen and server settlement. */
export const TRIAL_SIGILS = [
  { id: "might", name: "剛力", rune: "I", effect: "敵の最大HP +30%", reward: 18, color: "#df706a" },
  { id: "fangs", name: "鋭牙", rune: "II", effect: "敵の攻撃力 +22%", reward: 16, color: "#ff9472" },
  { id: "gale", name: "疾風", rune: "III", effect: "敵の移動速度 +16%", reward: 14, color: "#b7d2d5" },
  { id: "frenzy", name: "乱舞", rune: "IV", effect: "エリート出現率 ×1.9", reward: 22, color: "#be9bec" },
  { id: "famine", name: "飢餓", rune: "V", effect: "HP回復量 −42%", reward: 20, color: "#d3ad77" },
  { id: "pain", name: "鈍痛", rune: "VI", effect: "魂・武器奥義のゲージ獲得 −30%", reward: 16, color: "#8faad8" },
  { id: "gaze", name: "注視", rune: "VII", effect: "敵の索敵・射撃開始距離 +45%", reward: 14, color: "#91cba1" },
  { id: "abyss", name: "深淵", rune: "VIII", effect: "敵のHP・攻撃・移動・索敵 +10%", reward: 26, color: "#eee3ca" },
] as const;
export type SigilId = (typeof TRIAL_SIGILS)[number]["id"];

/** Reject unknown IDs, duplicates and excessive counts; never accept a multiplier from the client. */
export function validateSigils(value: unknown): SigilId[] {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > TRIAL_SIGILS.length) {
    throw new Error("呪印は0〜8枚の配列で指定してください。");
  }
  const known = new Set<string>(TRIAL_SIGILS.map((s) => s.id));
  const unique = new Set<string>();
  for (const id of value) {
    if (typeof id !== "string" || !known.has(id)) throw new Error("不明な呪印が指定されています。");
    if (unique.has(id)) throw new Error("同じ呪印を重ねて指定することはできません。");
    unique.add(id);
  }
  return TRIAL_SIGILS.filter((s) => unique.has(s.id)).map((s) => s.id);
}

export function trialRules(value: unknown = []) {
  const ids = validateSigils(value);
  const has = (id: SigilId) => ids.includes(id);
  const abyss = has("abyss") ? 1.1 : 1;
  // Integer percentages avoid floating-point display errors: all eight sum to 146%.
  const bonus = TRIAL_SIGILS.reduce((sum, s) => sum + (has(s.id) ? s.reward : 0), 0);
  return {
    ids,
    count: ids.length,
    soulMultiplier: (100 + bonus) / 100,
    enemyHp: (has("might") ? 1.3 : 1) * abyss,
    enemyDamage: (has("fangs") ? 1.22 : 1) * abyss,
    enemySpeed: (has("gale") ? 1.16 : 1) * abyss,
    eliteRate: has("frenzy") ? 1.9 : 1,
    healing: has("famine") ? 0.58 : 1,
    energy: has("pain") ? 0.7 : 1,
    detection: (has("gaze") ? 1.45 : 1) * abyss,
  };
}
export type TrialRules = ReturnType<typeof trialRules>;
