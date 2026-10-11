import type { Item, Rarity, STAT_META } from "./core";
import type { AchievementDef, RunConfig, RunMode } from "./progression";
import type { SigilId } from "./trials";
import type { Attributes } from "./attributes";

/** 継承装備の直列化形（DBに保存される） */
export type StoredItem = {
  id: string;
  slot: string;
  rarity: string;
  base: string;
  name: string;
  ilvl: number;
  power: number;
  weaponClass?: string | null;
  weaponTrait?: string | null;
  forged?: number | null;
  mods: Record<string, number>;
  affixes: { key: string; label: string; text: string; value: number; tier: number }[];
};

export type Hero = {
  id: string;
  name: string;
  classId: string;
  souls: number;
  meta: Record<string, number>;
  bestFloor: number;
  bestScore: number;
  deepestKills: number;
  totalRuns: number;
  totalKills: number;
  victories: number;
  heirloom: StoredItem | null;
  trueClears: number;
};

export type BoardEntry = {
  id: string;
  heroName: string;
  classId: string;
  floor: number;
  level: number;
  kills: number;
  score: number;
  victory: boolean;
  weaponName: string | null;
  rarity: string | null;
  build: string[];
  createdAt: string | Date;
};

export type Champion = {
  name: string;
  classId: string;
  bestFloor: number;
  bestScore: number;
  totalRuns: number;
  totalKills: number;
  victories: number;
  souls: number;
};

export type DailyBoardEntry = {
  id: string;
  heroName: string;
  classId: string;
  floor: number;
  level: number;
  kills: number;
  score: number;
  victory: boolean;
  challengeName: string;
};

export type AchievementView = AchievementDef & { unlockedAt: string | Date | null };

export type Relic = {
  itemName: string;
  slot: string;
  rarity: string;
  power: number;
  floor: number;
  heroName: string;
  affixes: string[];
  createdAt: string | Date;
};

export type BoardData = {
  board: BoardEntry[];
  dailyBoard: DailyBoardEntry[];
  dailyDate: string;
  champions: Champion[];
  relics: Relic[];
  totals: { runs: number; kills: number; bestFloor: number };
};

export const EMPTY_BOARD: BoardData = { board: [], dailyBoard: [], dailyDate: "", champions: [], relics: [], totals: { runs: 0, kills: 0, bestFloor: 0 } };

async function j<T>(r: Response): Promise<T> {
  if (!r.ok) {
    const e = await r.json().catch(() => ({}));
    throw new Error((e as { error?: string }).error ?? `HTTP ${r.status}`);
  }
  return (await r.json()) as T;
}

export const fetchBoard = () => fetch("/api/heroes", { cache: "no-store" }).then((r) => j<BoardData>(r));

export const fetchProgression = (heroId: string) =>
  fetch(`/api/progression?heroId=${encodeURIComponent(heroId)}`, { cache: "no-store" })
    .then((r) => j<{ achievements: AchievementView[] }>(r));

export const ensureHero = (name: string, classId: string) =>
  fetch("/api/heroes", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ name, classId }),
  }).then((r) => j<{ hero: Hero; created: boolean }>(r));

export const buyMeta = (heroId: string, key: string) =>
  fetch("/api/meta", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ heroId, key }),
  }).then((r) => j<{ hero: Hero; cost: number }>(r));

/** 踏破時に装備を1つ持ち帰り、次回出撃時に継承させる */
export const saveHeirloom = (heroId: string, item: Item) =>
  fetch("/api/heirloom", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ heroId, item }),
  }).then((r) => j<{ hero: Hero; heirloom: StoredItem }>(r));

export const discardHeirloom = (heroId: string) =>
  fetch(`/api/heirloom?heroId=${encodeURIComponent(heroId)}`, { method: "DELETE" })
    .then((r) => j<{ hero: Hero }>(r));

/** DBに保存された継承装備を、エンジンで扱える Item へ復元する */
export function toEngineItem(s: StoredItem | null | undefined): Item | null {
  if (!s) return null;
  const mods: Partial<Record<keyof typeof STAT_META, number>> = {};
  for (const [k, v] of Object.entries(s.mods ?? {})) {
    if (Number.isFinite(v)) mods[k as keyof typeof STAT_META] = v;
  }
  return {
    id: `it_heir_${Math.floor(Math.random() * 1e9).toString(36)}`,
    slot: s.slot as Item["slot"],
    rarity: s.rarity as Item["rarity"],
    base: s.base,
    name: s.name,
    ilvl: s.ilvl,
    mods,
    affixes: (s.affixes ?? []).map((a) => ({
      key: a.key as keyof typeof STAT_META,
      label: a.label,
      text: a.text,
      value: a.value,
      tier: a.tier,
    })),
    power: s.power,
    weaponClass: (s.weaponClass ?? undefined) as Item["weaponClass"],
    weaponTrait: (s.weaponTrait ?? undefined) as Item["weaponTrait"],
    forged: s.forged ?? 0,
  };
}

export const openExpedition = (heroId: string, classId: string, config: RunConfig) =>
  fetch("/api/expeditions", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ heroId, classId, mode: config.mode, difficulty: config.difficulty, trialSigils: config.trialSigils }),
  }).then((r) => j<{ expeditionId: string; config: RunConfig; trialCount: number; trialSoulMultiplier: number }>(r));

export type RunPayload = {
  heroId: string;
  expeditionId: string;
  trialSigils: SigilId[];
  trialCount: number;
  attributes: Attributes;
  gatesDestroyed: number;
  weaponArtUses: number;
  classId: string;
  floor: number;
  level: number;
  kills: number;
  gold: number;
  score: number;
  durationSec: number;
  victory: boolean;
  weaponName: string | null;
  rarity: Rarity | string | null;
  build: string[];
  artifactsFound: { name: string; slot: string; rarity: string; power: number; floor: number; affixes: string[] }[];
  challengeMode: RunMode;
  challengeId: string;
  challengeName: string;
  challengeDay: string | null;
  difficulty: string;
  relics: string[];
  eventsCompleted: number;
  bossesKilled: number;
  bestCombo: number;
  perfectDodges: number;
  trueCleared: boolean;
  pinnacleCleared: boolean;
};

export const submitRun = (p: RunPayload) =>
  fetch("/api/runs", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(p),
  }).then((r) => j<{ run: { id: string }; hero: Hero; soulsEarned: number; runSouls: number; achievementSouls: number; trialBonus: number; trialCount: number; trialSoulMultiplier: number; achievements: AchievementDef[]; replayed: boolean }>(r));

export const rarityOf = (v: string | null | undefined): Rarity =>
  v === "magic" || v === "rare" || v === "legendary" || v === "mythic" || v === "normal" ? v : "normal";
