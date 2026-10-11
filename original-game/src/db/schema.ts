import {
  boolean,
  integer,
  jsonb,
  pgTable,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from "drizzle-orm/pg-core";
import type { RunConfig } from "../game/progression";
import type { Attributes } from "../game/attributes";

/** 永遠に蓄積される英雄（メタ進行） */
export const heroes = pgTable("heroes", {
  id: uuid("id").primaryKey().defaultRandom(),
  name: text("name").notNull(),
  classId: text("class_id").notNull(),
  souls: integer("souls").notNull().default(0),
  meta: jsonb("meta").$type<Record<string, number>>().notNull().default({}),
  bestFloor: integer("best_floor").notNull().default(0),
  bestScore: integer("best_score").notNull().default(0),
  deepestKills: integer("deepest_kills").notNull().default(0),
  totalRuns: integer("total_runs").notNull().default(0),
  totalKills: integer("total_kills").notNull().default(0),
  victories: integer("victories").notNull().default(0),
  /** 踏破時に持ち帰った継承装備（1つまで・次回出撃時に初期装備として継承） */
  heirloom: jsonb("heirloom").$type<StoredItem | null>(),
  /** 真・最終決戦（《ニヒル・ゼロ》）を討伐した回数 */
  trueClears: integer("true_clears").notNull().default(0),
  /** 終焉の座（最難関決戦・タルタロス・オメガ）を討伐した回数 */
  pinnacleClears: integer("pinnacle_clears").notNull().default(0),
  createdAt: timestamp("created_at").notNull().defaultNow(),
  updatedAt: timestamp("updated_at").notNull().defaultNow(),
});

/** 継承装備としてDBに保存する装備の直列化形 */
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

/** 出撃前に確定した条件。報酬計算はこの記録を正とする。 */
export const expeditions = pgTable("expeditions", {
  id: uuid("id").primaryKey().defaultRandom(),
  heroId: uuid("hero_id").notNull().references(() => heroes.id, { onDelete: "cascade" }),
  classId: text("class_id").notNull(),
  config: jsonb("config").$type<RunConfig>().notNull(),
  settled: boolean("settled").notNull().default(false),
  createdAt: timestamp("created_at").notNull().defaultNow(),
});

/** 1回の冒険の記録 */
export const runs = pgTable("runs", {
  id: uuid("id").primaryKey().defaultRandom(),
  heroId: uuid("hero_id")
    .notNull()
    .references(() => heroes.id, { onDelete: "cascade" }),
  heroName: text("hero_name").notNull(),
  classId: text("class_id").notNull(),
  expeditionId: uuid("expedition_id").references(() => expeditions.id, { onDelete: "cascade" }),
  trialSigils: jsonb("trial_sigils").$type<string[]>().notNull().default([]),
  difficulty: text("difficulty").notNull().default("normal"),
  attributes: jsonb("attributes").$type<Attributes>().notNull().default({ strength: 0, rhythm: 0, agility: 0, endurance: 0, fortune: 0 }),
  gatesDestroyed: integer("gates_destroyed").notNull().default(0),
  weaponArtUses: integer("weapon_art_uses").notNull().default(0),
  reward: jsonb("reward").$type<{ runSouls: number; achievementSouls: number; trialBonus: number; achievementIds: string[] }>(),
  floor: integer("floor").notNull(),
  level: integer("level").notNull(),
  kills: integer("kills").notNull(),
  gold: integer("gold").notNull().default(0),
  score: integer("score").notNull(),
  durationSec: integer("duration_sec").notNull().default(0),
  victory: boolean("victory").notNull().default(false),
  challengeMode: text("challenge_mode").notNull().default("standard"),
  challengeId: text("challenge_id").notNull().default("standard"),
  challengeName: text("challenge_name").notNull().default("通常潜行"),
  challengeDay: text("challenge_day"),
  eventsCompleted: integer("events_completed").notNull().default(0),
  bossesKilled: integer("bosses_killed").notNull().default(0),
  bestCombo: integer("best_combo").notNull().default(0),
  perfectDodges: integer("perfect_dodges").notNull().default(0),
  weaponName: text("weapon_name"),
  rarity: text("rarity"),
  build: jsonb("build").$type<string[]>().notNull().default([]),
  createdAt: timestamp("created_at").notNull().defaultNow(),
}, (table) => [uniqueIndex("runs_expedition_unique").on(table.expeditionId)]);

/** 神話級装備の殿堂 */
export const artifacts = pgTable("artifacts", {
  id: uuid("id").primaryKey().defaultRandom(),
  heroName: text("hero_name").notNull(),
  itemName: text("item_name").notNull(),
  slot: text("slot").notNull(),
  rarity: text("rarity").notNull(),
  power: integer("power").notNull(),
  floor: integer("floor").notNull(),
  affixes: jsonb("affixes").$type<string[]>().notNull().default([]),
  createdAt: timestamp("created_at").notNull().defaultNow(),
});

/** 英雄ごとの恒久実績（重複解除なし） */
export const achievementUnlocks = pgTable(
  "achievement_unlocks",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    heroId: uuid("hero_id")
      .notNull()
      .references(() => heroes.id, { onDelete: "cascade" }),
    key: text("key").notNull(),
    unlockedAt: timestamp("unlocked_at").notNull().defaultNow(),
  },
  (table) => [uniqueIndex("achievement_hero_key_unique").on(table.heroId, table.key)],
);

export type HeroRow = typeof heroes.$inferSelect;
export type RunRow = typeof runs.$inferSelect;
export type ArtifactRow = typeof artifacts.$inferSelect;
export type AchievementUnlockRow = typeof achievementUnlocks.$inferSelect;
