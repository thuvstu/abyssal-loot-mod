import type { Mods, Difficulty } from "./core";
import { DIFFICULTIES } from "./core";
import type { SigilId } from "./trials";

export type RunMode = "standard" | "daily" | "bossrush" | "endless" | "covenant" | "pinnacle" | "danmaku" | "omega";

export const RUN_MODES: Record<
  Exclude<RunMode, "daily">,
  { name: string; desc: string; color: string; tag: string; scoreMul: number; soulMul: number }
> = {
  standard: {
    name: "通常潜行", desc: "全25層を下り、虚無神ニヒルを討つ。練習と長期記録に最適。",
    color: "#2dd4a7", tag: "標準", scoreMul: 1, soulMul: 1,
  },
  bossrush: {
    name: "主の巡礼", desc: "Lv10から始まる層主8連戦。初期属性9点でビルドを作り、連続決戦へ。",
    color: "#ff3b5c", tag: "高難度", scoreMul: 2.2, soulMul: 2.0,
  },
  endless: {
    name: "無限深淵", desc: "25層で終わらない。敵は層と共に無限に強くなる。どこまで沈める？",
    color: "#8b5cf6", tag: " endless", scoreMul: 1.8, soulMul: 1.6,
  },
  covenant: {
    name: "不幸の盟約", desc: "祝福が一切得られない。装備とパッシブと腕だけが頼り。報酬は破格。",
    color: "#e34bff", tag: "禁呪", scoreMul: 3.4, soulMul: 3.0,
  },
  pinnacle: {
    name: "終焉の座", desc: "全装備総合力5000超・全強化済みを想定した極限決戦。ミスは致命傷。見切り・反撃・重撃・奥義の全てで神を穿て。",
    color: "#ff2a55", tag: "極限決戦", scoreMul: 5.0, soulMul: 4.5,
  },
  danmaku: {
    name: "弾幕の獄", desc: "弾幕シューティング×ガチアクション。斬撃・見切り・ダッシュで敵弾を弾き・反射し、弾丸の嵐を踊り抜けろ。被弾は軽いが弾の密度は異常。",
    color: "#ff3b8d", tag: "弾幕", scoreMul: 4.0, soulMul: 3.5,
  },
  omega: {
    name: "終焉の契約", desc: "全8試練呪印を強制付与。阿鼻叫喚の倍率×2.46。スコア×5.0、魂×7.56。",
    color: "#ff00ff", tag: "終焉", scoreMul: 5.0, soulMul: 7.56,
  },
};

export type DailyModifier = {
  id: string;
  title: string;
  tagline: string;
  description: string;
  color: string;
  enemyHp: number;
  enemyDamage: number;
  enemySpeed: number;
  spawnRate: number;
  eliteRate: number;
  goldRate: number;
  xpRate: number;
  scoreRate: number;
  soulRate: number;
  noFloorHeal?: boolean;
  playerMods?: Mods;
};

export type RunConfig = {
  mode: RunMode;
  challengeId: string;
  challengeName: string;
  challengeDay: string | null;
  seed: number;
  modifier: DailyModifier | null;
  /** 難易度（通常／極難／地獄） */
  difficulty: Difficulty;
  trialSigils: SigilId[];
};

export const DAILY_MODIFIERS: DailyModifier[] = [
  {
    id: "blood-moon", title: "血月の狩猟", tagline: "倒すか、喰われるか。",
    description: "敵の体力と攻撃力が増す。敵の群れも濃くなるが、持ち帰る魂は大幅増。",
    color: "#ff4d6d", enemyHp: 1.42, enemyDamage: 1.18, enemySpeed: 1.08,
    spawnRate: 1.3, eliteRate: 1.5, goldRate: 1.15, xpRate: 1.1, scoreRate: 1.55, soulRate: 1.5,
  },
  {
    id: "glass-oath", title: "硝子の誓約", tagline: "一撃必殺、紙一重。",
    description: "最大HP -35%、攻撃力 +42%。回避とコンボを極め、先に敵を倒せ。",
    color: "#8be9ff", enemyHp: 1.05, enemyDamage: 1.18, enemySpeed: 1.12,
    spawnRate: 1.08, eliteRate: 1.2, goldRate: 1.1, xpRate: 1.05, scoreRate: 1.65, soulRate: 1.35,
    playerMods: { maxHpPct: -35, damagePct: 42, critPct: 5 },
  },
  {
    id: "endless-hunt", title: "終わらぬ追跡", tagline: "背後に安全地帯はない。",
    description: "敵の速度と出現数が上昇。移動と見切りで群れを翻弄せよ。",
    color: "#ff9a3c", enemyHp: 1.12, enemyDamage: 1.06, enemySpeed: 1.34,
    spawnRate: 1.55, eliteRate: 1.35, goldRate: 1.2, xpRate: 1.15, scoreRate: 1.6, soulRate: 1.35,
  },
  {
    id: "drought", title: "乾ききった迷宮", tagline: "癒しは自ら奪い取れ。",
    description: "層移動時の回復なし。自然回復も封印されるが、敵の魂と経験値が豊富。",
    color: "#e7c58d", enemyHp: 1.14, enemyDamage: 1.12, enemySpeed: 1.04,
    spawnRate: 1.12, eliteRate: 1.2, goldRate: 1.22, xpRate: 1.45, scoreRate: 1.5, soulRate: 1.45,
    noFloorHeal: true, playerMods: { regenFlat: -100 },
  },
  {
    id: "treasure-tide", title: "黄金の潮流", tagline: "欲望の分だけ、深く沈め。",
    description: "敵が頑丈になり、装備の質とゴールドが上昇。戦利品を掴み取れ。",
    color: "#f5c542", enemyHp: 1.32, enemyDamage: 1.06, enemySpeed: 1,
    spawnRate: 1.15, eliteRate: 1.7, goldRate: 2.05, xpRate: 1.12, scoreRate: 1.5, soulRate: 1.4,
    playerMods: { luckFlat: 35 },
  },
  {
    id: "void-storm", title: "虚空嵐", tagline: "空間そのものが牙をむく。",
    description: "敵弾が速く、エリートが増える。弾きを決めれば一気に魂を奪える。",
    color: "#c58bff", enemyHp: 1.18, enemyDamage: 1.22, enemySpeed: 1.12,
    spawnRate: 1.22, eliteRate: 2.1, goldRate: 1.25, xpRate: 1.18, scoreRate: 1.75, soulRate: 1.55,
    playerMods: { dashCdPct: 8, energyPct: 15 },
  },
  {
    id: "starfall", title: "星墜ちの夜", tagline: "知識と幸運、敵もまた強大。",
    description: "経験値と運が大きく上昇。層の主も強化される、育成型の高難度。",
    color: "#7c8cff", enemyHp: 1.25, enemyDamage: 1.1, enemySpeed: 1.05,
    spawnRate: 1.1, eliteRate: 1.4, goldRate: 1.25, xpRate: 1.8, scoreRate: 1.65, soulRate: 1.55,
    playerMods: { luckFlat: 24, xpPct: 20 },
  },
];

function hashSeed(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function utcDay(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}

export function getDailyRun(now = new Date(), difficulty: Difficulty = "normal"): RunConfig {
  const date = utcDay(now);
  const midnight = Date.parse(`${date}T00:00:00.000Z`);
  const index = Math.floor(midnight / 86_400_000) % DAILY_MODIFIERS.length;
  const modifier = DAILY_MODIFIERS[(index + DAILY_MODIFIERS.length) % DAILY_MODIFIERS.length];
  return {
    mode: "daily",
    challengeId: modifier.id,
    challengeName: modifier.title,
    challengeDay: date,
    seed: hashSeed(`${date}:${modifier.id}:abyssal-loot-v1`),
    modifier,
    trialSigils: [],
    difficulty,
  };
}

export function standardRun(difficulty: Difficulty = "normal"): RunConfig {
  return { mode: "standard", challengeId: "standard", challengeName: "通常潜行", challengeDay: null, seed: 0, modifier: null, difficulty, trialSigils: [] };
}

/** モードIDから RunConfig を作る（日替わり以外は毎回ランダムシード） */
export function buildRun(mode: Exclude<RunMode, "daily">, difficulty: Difficulty = "normal"): RunConfig {
  const def = RUN_MODES[mode];
  return {
    mode,
    challengeId: mode,
    challengeName: def.name,
    challengeDay: null,
    seed: (Math.random() * 1e9) | 0,
    modifier: null,
    trialSigils: [],
    difficulty,
  };
}

export type AchievementDef = {
  id: string;
  title: string;
  description: string;
  reward: number;
  icon: string;
  tier: "bronze" | "silver" | "gold" | "mythic";
};

export const ACHIEVEMENTS: AchievementDef[] = [
  { id: "gate-breaker", title: "門を断つ者", description: "呪われた召喚門を1つ破壊する", reward: 180, icon: "門", tier: "silver" },
  { id: "eight-sigils", title: "八重の試練", description: "呪印8枚で第10層へ到達する", reward: 800, icon: "Ⅷ", tier: "mythic" },
  { id: "art-master", title: "武の極致", description: "1回の冒険で武器奥義を10回使う", reward: 300, icon: "極", tier: "gold" },
  { id: "first-blood", title: "深淵への第一歩", description: "初めて敵を倒す", reward: 25, icon: "⚔", tier: "bronze" },
  { id: "hundred-kills", title: "百鬼夜行", description: "1回の冒険で100体撃破", reward: 100, icon: "☠", tier: "silver" },
  { id: "thousand-kills", title: "一騎当千", description: "1回の冒険で1,000体撃破", reward: 500, icon: "✦", tier: "gold" },
  { id: "floor-five", title: "門番を越えて", description: "第5層の主を撃破", reward: 100, icon: "♜", tier: "silver" },
  { id: "floor-ten", title: "深層探査者", description: "第10層の主を撃破", reward: 250, icon: "◈", tier: "gold" },
  { id: "floor-twenty", title: "虚無の手前", description: "第20層の主を撃破", reward: 600, icon: "☄", tier: "mythic" },
  { id: "full-clear", title: "深淵を終わらせた者", description: "第25層を踏破する", reward: 1500, icon: "♛", tier: "mythic" },
  { id: "true-clear", title: "虚無を_zeroに還した者", description: "原初虚無《ニヒル・ゼロ》を討伐する", reward: 4000, icon: "☯", tier: "mythic" },
  { id: "heirloom-keep", title: "血脈の守り人", description: "装備を継承して次の冒険へ持ち帰る", reward: 400, icon: "❖", tier: "gold" },
  { id: "mythic-hunter", title: "神話を掴む手", description: "神話級装備を発見する", reward: 300, icon: "✧", tier: "gold" },
  { id: "combo-fifty", title: "無間連撃", description: "1回の冒険で50コンボ達成", reward: 250, icon: "〽", tier: "gold" },
  { id: "perfect-ten", title: "刹那の見切り", description: "1回の冒険で10回見切る", reward: 300, icon: "⏱", tier: "gold" },
  { id: "event-five", title: "祭壇の巡礼者", description: "層間イベントを5回完了", reward: 220, icon: "♨", tier: "silver" },
  { id: "daily-depth", title: "日替わり踏破者", description: "日替わり深淵で第10層へ到達", reward: 450, icon: "☼", tier: "gold" },
  { id: "level-twenty", title: "刻まれた成長", description: "1回の冒険でLv20到達", reward: 250, icon: "↑", tier: "silver" },
  { id: "boss-three", title: "層主狩り", description: "1回の冒険で3体の層主を撃破", reward: 450, icon: "♞", tier: "gold" },
  { id: "covenant-kept", title: "盟約を全うした者", description: "不幸の盟約で深淵を踏破する", reward: 5000, icon: "☩", tier: "mythic" },
  { id: "rush-master", title: "巡礼の果て", description: "主の巡礼で8体の層主を撃破", reward: 1800, icon: "⛨", tier: "mythic" },
  { id: "endless-diver", title: "限界の先", description: "無限深淵で第35層へ到達", reward: 2200, icon: "∇", tier: "mythic" },
  { id: "pinnacle-god", title: "終焉を穿つ者", description: "終焉の座でタルタロス・オメガを討ち果たす", reward: 10000, icon: "Ω", tier: "mythic" },
  { id: "inferno-slayer", title: "阿鼻叫喚を制す", description: "難易度【阿鼻叫喚】で第15層へ到達する", reward: 4000, icon: "♨", tier: "mythic" },
];

export type UnlockedAchievement = { key: string; unlockedAt: string | Date };
