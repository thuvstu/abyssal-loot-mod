/**
 * ABYSSAL LOOT — コアデータ定義
 * 装備生成 / アフィックス / 敵 / 祝福 / メタ強化
 */

export type RNG = () => number;

export function mulberry32(seed: number): RNG {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export const rnd = (r: RNG, a = 0, b = 1) => a + (b - a) * r();
export const rndInt = (r: RNG, a: number, b: number) => { const low = Math.ceil(Math.min(a, b)); const high = Math.floor(Math.max(a, b)); return low + Math.floor(r() * (high - low + 1)); };
export const pick = <T,>(r: RNG, arr: readonly T[]): T => arr[Math.floor(r() * arr.length) % arr.length];
export const clamp = (v: number, a: number, b: number) => (v < a ? a : v > b ? b : v);

export function weighted<T>(r: RNG, items: readonly T[], w: (t: T) => number): T {
  let total = 0;
  for (const it of items) total += Math.max(0, w(it));
  if (total <= 0) return items[0];
  let x = r() * total;
  for (const it of items) {
    x -= Math.max(0, w(it));
    if (x <= 0) return it;
  }
  return items[items.length - 1];
}

/* ------------------------------------------------------------------ 数値 */

export const nf = (v: number) =>
  Math.abs(v) >= 1000 ? (v / 1000).toFixed(v >= 10000 ? 0 : 1) + "k" : String(Math.round(v));

/* --------------------------------------------------------------- ステータス */

export type StatKey =
  | "maxHpFlat" | "maxHpPct" | "damageFlat" | "damagePct" | "attackSpeedPct"
  | "moveSpeedPct" | "critPct" | "critDmgPct" | "areaPct" | "projectiles"
  | "pierce" | "lifestealPct" | "armorPct" | "regenFlat" | "xpPct" | "luckFlat"
  | "dashCdPct" | "chain" | "explodePct" | "freezePct" | "pickupFlat"
  | "thornsFlat" | "energyPct" | "goldPct";

export type Mods = Partial<Record<StatKey, number>>;

/* ------------------------------------------------------------------ 素材 */

export type MaterialId = "soul_shard" | "ancient_stone" | "chaos_gem" | "void_essence" | "dragon_heart";

export const MATERIALS: Record<MaterialId, { name: string; desc: string; color: string; icon: string; weight: number }> = {
  soul_shard:    { name: "魂の欠片",     desc: "あらゆる鍛造の基盤素材",         color: "#5cc8ff", icon: "◈", weight: 5 },
  ancient_stone: { name: "古代の石",     desc: "遠い時代の記憶を宿す石",         color: "#f5c542", icon: "◆", weight: 3 },
  chaos_gem:     { name: "混沌の宝石",   desc: "予測不能な力を解放する",         color: "#e34bff", icon: "✧", weight: 1.5 },
  void_essence:  { name: "虚空のエッセンス", desc: "深淵から汲み上げた純粋な力",  color: "#7c8cff", icon: "◉", weight: 0.8 },
  dragon_heart:  { name: "竜の心臓",     desc: "最も希少な鍛造素材",             color: "#ff4d6d", icon: "♛", weight: 0.2 },
};

export type Materials = Partial<Record<MaterialId, number>>;

/* -------------------------------------------------------------- ステータス効果 */

export type StatusEffectKind = "poison" | "burn" | "freeze" | "bleed" | "shock" | "slow" | "vulnerable" | "stun";

export const STATUS_EFFECTS: Record<StatusEffectKind, { name: string; color: string; icon: string; duration: number; tickDmg?: number; slow?: number; armor?: number }> = {
  poison:    { name: "毒",     color: "#4bd07a", icon: "☣", duration: 3, tickDmg: 3 },
  burn:      { name: "炎上",   color: "#ff9a3c", icon: "🔥", duration: 2.5, tickDmg: 5 },
  freeze:    { name: "凍結",   color: "#8be9ff", icon: "❄", duration: 2, slow: 0.85 },
  bleed:     { name: "出血",   color: "#ff4d6d", icon: "🩸", duration: 4, tickDmg: 2 },
  shock:     { name: "感電",   color: "#ffe14a", icon: "⚡", duration: 1.5, slow: 0.4 },
  slow:      { name: "鈍足",   color: "#8d86a3", icon: "🐌", duration: 3, slow: 0.5 },
  vulnerable:{ name: "脆弱",   color: "#ff6a7d", icon: "⊕", duration: 4, armor: -0.25 },
  stun:      { name: "スタン", color: "#ffd23c", icon: "💫", duration: 0.8 },
};

export type ActiveStatus = { kind: StatusEffectKind; remaining: number; stacks: number; };

/* ------------------------------------------------------------------ 武器特性 */

export type WeaponTrait = {
  id: string;
  name: string;
  desc: string;
  color: string;
  effect: "crit_bonus" | "lifesteal" | "aoe_on_kill" | "execute" | "chain" | "burn" | "poison" | "bleed" | "frenzy" | "fortify";
  value: number;
};

export const WEAPON_TRAITS: WeaponTrait[] = [
  { id: "sharp",      name: "鋭刃",     desc: "与ダメージ +8%",                              color: "#cfd8e6", effect: "crit_bonus",   value: 8 },
  { id: "vampiric",   name: "吸血鬼",   desc: "撃破時にHP 3%回復",                           color: "#ff4d6d", effect: "lifesteal",    value: 3 },
  { id: "explosive",  name: "爆砕",     desc: "撃破時に周囲に爆発",                           color: "#ff9a3c", effect: "aoe_on_kill",  value: 80 },
  { id: "terminator", name: "断末魔",   desc: "HP30%以下の敵に+50%ダメージ",                 color: "#ff5c6e", effect: "execute",      value: 50 },
  { id: "chain_l",    name: "連鎖雷",   desc: "命中時に2回の雷撃",                            color: "#7ce8ff", effect: "chain",        value: 2 },
  { id: "infernus",   name: "業火",     desc: "命中時に15%で炎上付与",                        color: "#ff9a3c", effect: "burn",         value: 15 },
  { id: "venomous",   name: "猛毒",     desc: "命中時に12%で毒付与",                          color: "#4bd07a", effect: "poison",       value: 12 },
  { id: "rend",       name: "裂傷",     desc: "命中時に10%で出血付与",                        color: "#ff4d6d", effect: "bleed",        value: 10 },
  { id: "frenzy",     name: "狂戦士",   desc: "コンボ30以上で攻速+40%",                       color: "#e34bff", effect: "frenzy",       value: 30 },
  { id: "fortify",    name: "不壊",     desc: "攻撃命中で3%のダメージ軽減を10秒付与",        color: "#ffc94a", effect: "fortify",      value: 3 },
];

/* -------------------------------------------------------------- 武器種別 */

export type WeaponClassId =
  | "sword" | "axe" | "spear" | "staff" | "bow" | "dagger" | "hammer" | "katana"
  | "scythe" | "gauntlet" | "chakram" | "tome";

export const WEAPON_CLASSES: Record<WeaponClassId, { name: string; desc: string; color: string; bonus: Mods }> = {
  sword:  { name: "剣",   desc: "バランス型。攻速+10%",               color: "#e9e3d5", bonus: { attackSpeedPct: 10 } },
  axe:    { name: "斧",   desc: "高火力。攻撃力+18%, 攻速-12%",      color: "#ff8a2b", bonus: { damagePct: 18, attackSpeedPct: -12 } },
  spear:  { name: "槍",   desc: "広範囲。攻撃範囲+28%",               color: "#5cc8ff", bonus: { areaPct: 28 } },
  staff:  { name: "杖",   desc: "魔力型。魂ゲージ+35%, 攻撃力-8%",   color: "#c58bff", bonus: { energyPct: 35, damagePct: -8 } },
  bow:    { name: "弓",   desc: "遠距離型。追加弾+1, 貫通+1",         color: "#7cffb2", bonus: { projectiles: 1, pierce: 1 } },
  dagger: { name: "短剣", desc: "会心型。会心率+12%, 会心威力+30%",   color: "#ffd23c", bonus: { critPct: 12, critDmgPct: 30 } },
  hammer: { name: "槌",   desc: "重撃型。爆裂+22%, 攻速-20%",        color: "#ff6a7d", bonus: { explodePct: 22, attackSpeedPct: -20 } },
  katana: { name: "刀",   desc: "一刀型。攻撃力+10%, 攻速+8%, 会心+6%", color: "#ff4d5e", bonus: { damagePct: 10, attackSpeedPct: 8, critPct: 6 } },
  // --- 追加の武器スタイル（挙動まで変わる） ---
  scythe:  { name: "鎌",   desc: "収穫型。撃破で収穫スタック→攻速+6%/枚(最大10)", color: "#a0e87c", bonus: { damagePct: 14, areaPct: 18, maxHpPct: -6 } },
  gauntlet:{ name: "拳套", desc: "連打型。攻速+28%, 攻撃力-14%。3連撃で衝撃波",   color: "#ffb84a", bonus: { attackSpeedPct: 28, damagePct: -14, moveSpeedPct: 8 } },
  chakram: { name: "輪刃", desc: "跳ね返り型。貫通+3, 攻撃範囲+20%, 攻速-8%",     color: "#7ce8ff", bonus: { pierce: 3, areaPct: 20, attackSpeedPct: -8 } },
  tome:    { name: "魔導書", desc: "爆縮型。爆裂+35%, 攻撃力+22%, 攻速-26%",      color: "#e34bff", bonus: { explodePct: 35, damagePct: 22, attackSpeedPct: -26 } },
};

export function randomWeaponClass(r: RNG): WeaponClassId {
  const keys = Object.keys(WEAPON_CLASSES) as WeaponClassId[];
  return pick(r, keys);
}

export const STAT_META: Record<StatKey, { label: string; fmt: (v: number) => string; worth: number }> = {
  maxHpFlat:     { label: "最大HP",       fmt: (v) => `+${Math.round(v)}`,        worth: 0.10 },
  maxHpPct:      { label: "最大HP",       fmt: (v) => `+${Math.round(v)}%`,       worth: 2.6 },
  damageFlat:    { label: "攻撃力",       fmt: (v) => `+${Math.round(v)}`,        worth: 1.0 },
  damagePct:     { label: "攻撃力",       fmt: (v) => `+${Math.round(v)}%`,       worth: 3.4 },
  attackSpeedPct:{ label: "攻撃速度",     fmt: (v) => `+${Math.round(v)}%`,       worth: 3.2 },
  moveSpeedPct:  { label: "移動速度",     fmt: (v) => `+${Math.round(v)}%`,       worth: 2.4 },
  critPct:       { label: "クリティカル率", fmt: (v) => `+${v.toFixed(1)}%`,      worth: 2.8 },
  critDmgPct:    { label: "クリティカル威力", fmt: (v) => `+${Math.round(v)}%`,   worth: 1.5 },
  areaPct:       { label: "攻撃範囲",     fmt: (v) => `+${Math.round(v)}%`,       worth: 1.8 },
  projectiles:   { label: "追加弾数",     fmt: (v) => `+${Math.round(v)}`,        worth: 26 },
  pierce:        { label: "貫通",         fmt: (v) => `+${Math.round(v)}`,        worth: 14 },
  lifestealPct:  { label: "生命吸収",     fmt: (v) => `+${v.toFixed(1)}%`,        worth: 6.0 },
  armorPct:      { label: "ダメージ軽減", fmt: (v) => `+${Math.round(v)}%`,       worth: 5.0 },
  regenFlat:     { label: "HP自動回復",   fmt: (v) => `+${v.toFixed(1)}/秒`,      worth: 16 },
  xpPct:         { label: "経験値",       fmt: (v) => `+${Math.round(v)}%`,       worth: 1.4 },
  luckFlat:      { label: "運",           fmt: (v) => `+${Math.round(v)}`,        worth: 1.1 },
  dashCdPct:     { label: "回避短縮",     fmt: (v) => `-${Math.round(v)}%`,       worth: 2.2 },
  chain:         { label: "連鎖雷撃",     fmt: (v) => `+${Math.round(v)}`,        worth: 20 },
  explodePct:    { label: "爆裂確率",     fmt: (v) => `+${Math.round(v)}%`,       worth: 3.0 },
  freezePct:     { label: "氷結確率",     fmt: (v) => `+${Math.round(v)}%`,       worth: 2.6 },
  pickupFlat:    { label: "回収範囲",     fmt: (v) => `+${Math.round(v)}`,        worth: 0.35 },
  thornsFlat:    { label: "棘ダメージ",   fmt: (v) => `+${Math.round(v)}`,        worth: 1.2 },
  energyPct:     { label: "魂ゲージ増加", fmt: (v) => `+${Math.round(v)}%`,       worth: 1.6 },
  goldPct:       { label: "ゴールド",     fmt: (v) => `+${Math.round(v)}%`,       worth: 1.2 },
};

export const STAT_ORDER: StatKey[] = [
  "damageFlat", "damagePct", "maxHpFlat", "maxHpPct", "attackSpeedPct", "critPct",
  "critDmgPct", "moveSpeedPct", "areaPct", "projectiles", "pierce", "lifestealPct",
  "armorPct", "regenFlat", "chain", "explodePct", "freezePct", "thornsFlat",
  "dashCdPct", "energyPct", "xpPct", "goldPct", "luckFlat", "pickupFlat",
];

export function sumMods(list: Mods[]): Mods {
  const out: Mods = {};
  for (const m of list) {
    for (const k of Object.keys(m) as StatKey[]) {
      out[k] = (out[k] ?? 0) + (m[k] ?? 0);
    }
  }
  return out;
}

export type Derived = {
  maxHp: number; damage: number; attackSpeed: number; moveSpeed: number;
  crit: number; critDmg: number; area: number; projectiles: number; pierce: number;
  lifesteal: number; armor: number; regen: number; xpMult: number; luck: number;
  dashCd: number; chain: number; explode: number; freeze: number; pickup: number;
  thorns: number; energyMult: number; goldMult: number;
};

export function computeDerived(cls: ClassDef, level: number, m: Mods): Derived {
  const g = (k: StatKey) => m[k] ?? 0;
  const hpBase = cls.base.hp + (level - 1) * cls.base.hpPerLevel;
  return {
    maxHp: Math.max(20, (hpBase + g("maxHpFlat")) * (1 + g("maxHpPct") / 100)),
    damage: Math.max(1, (cls.base.damage * (1 + (level - 1) * 0.17) + g("damageFlat")) * (1 + g("damagePct") / 100)),
    attackSpeed: clamp(cls.base.attackSpeed * (1 + g("attackSpeedPct") / 100), 0.25, 9),
    moveSpeed: clamp(cls.base.moveSpeed * (1 + g("moveSpeedPct") / 100), 90, 620),
    crit: clamp(cls.base.crit + g("critPct") / 100, 0, 0.95),
    critDmg: cls.base.critDmg + g("critDmgPct") / 100,
    area: clamp(1 + g("areaPct") / 100, 0.4, 4),
    projectiles: cls.base.projectiles + Math.floor(g("projectiles")),
    pierce: Math.floor(g("pierce")),
    lifesteal: clamp(g("lifestealPct") / 100, 0, 0.7),
    armor: clamp(g("armorPct") / 100, 0, 0.8),
    regen: Math.max(0, g("regenFlat")),
    xpMult: 1 + g("xpPct") / 100,
    luck: g("luckFlat"),
    dashCd: Math.max(0.28, cls.base.dashCd * (1 - clamp(g("dashCdPct"), 0, 65) / 100)),
    chain: Math.floor(g("chain")),
    explode: clamp(g("explodePct") / 100, 0, 0.85),
    freeze: clamp(g("freezePct") / 100, 0, 0.85),
    pickup: 62 + g("pickupFlat"),
    thorns: g("thornsFlat"),
    energyMult: 1 + g("energyPct") / 100,
    goldMult: 1 + g("goldPct") / 100,
  };
}

/* ------------------------------------------------------------------ 職業 */

export type AttackKind = "melee" | "ranged";

export type ClassDef = {
  id: string;
  name: string;
  title: string;
  desc: string;
  color: string;
  accent: string;
  attack: AttackKind;
  skillName: string;
  skillDesc: string;
  base: {
    hp: number; hpPerLevel: number; damage: number; attackSpeed: number;
    moveSpeed: number; crit: number; critDmg: number; projectiles: number;
    dashCd: number; reach: number; arc: number; projSpeed: number;
  };
};

export const CLASSES: ClassDef[] = [
  {
    id: "samurai", name: "剣聖", title: "孤高の一刀", color: "#ff4d5e", accent: "#ffd0d6",
    desc: "広範囲の抜刀斬撃。接近戦で真価を発揮する攻防一体の型。",
    attack: "melee", skillName: "絶空斬", skillDesc: "全方位へ巨大な斬撃を放ち、敵を吹き飛ばす",
    base: { hp: 130, hpPerLevel: 15, damage: 20, attackSpeed: 1.55, moveSpeed: 250, crit: 0.08, critDmg: 1.9, projectiles: 1, dashCd: 1.1, reach: 104, arc: 2.1, projSpeed: 0 },
  },
  {
    id: "mage", name: "魔導士", title: "星詠みの術者", color: "#7c8cff", accent: "#d9dcff",
    desc: "追尾する魔力弾を連射。脆いが距離を取れば無類の火力。",
    attack: "ranged", skillName: "メテオフォール", skillDesc: "広域に隕石を降り注がせ壊滅させる",
    base: { hp: 84, hpPerLevel: 9, damage: 16, attackSpeed: 2.0, moveSpeed: 244, crit: 0.1, critDmg: 2.1, projectiles: 1, dashCd: 1.25, reach: 0, arc: 0, projSpeed: 620 },
  },
  {
    id: "ninja", name: "忍者", title: "影縫いの刃", color: "#3ce8a5", accent: "#c9fff0",
    desc: "超高速の連撃と回避。会心で敵を連鎖的に屠る。",
    attack: "melee", skillName: "影分身乱舞", skillDesc: "分身が周囲の敵を斬り刻む",
    base: { hp: 96, hpPerLevel: 11, damage: 12, attackSpeed: 3.1, moveSpeed: 300, crit: 0.2, critDmg: 2.4, projectiles: 1, dashCd: 0.62, reach: 78, arc: 1.5, projSpeed: 0 },
  },
  {
    id: "paladin", name: "聖騎士", title: "不滅の盾", color: "#ffc94a", accent: "#fff3cf",
    desc: "圧倒的な耐久と聖なる衝撃波。死ににくい万能型。",
    attack: "melee", skillName: "ジャッジメント", skillDesc: "聖光の柱が周囲を焼き浄化する",
    base: { hp: 190, hpPerLevel: 21, damage: 16, attackSpeed: 1.25, moveSpeed: 226, crit: 0.05, critDmg: 1.75, projectiles: 1, dashCd: 1.5, reach: 92, arc: 2.5, projSpeed: 0 },
  },
  {
    id: "gunner", name: "銃魔", title: "鉛の嵐", color: "#ff9a3c", accent: "#ffe4c7",
    desc: "貫通する魔弾をばら撒く。手数と範囲の暴力。",
    attack: "ranged", skillName: "バレットヘル", skillDesc: "全方位へ弾幕を展開する",
    base: { hp: 100, hpPerLevel: 12, damage: 11, attackSpeed: 3.4, moveSpeed: 256, crit: 0.14, critDmg: 2.0, projectiles: 2, dashCd: 1.0, reach: 0, arc: 0, projSpeed: 780 },
  },
];

export const classById = (id: string) => CLASSES.find((c) => c.id === id) ?? CLASSES[0];

/* ------------------------------------------------------------------ 装備 */

export type Slot = "weapon" | "helm" | "armor" | "gloves" | "boots" | "ring" | "amulet";

export const SLOTS: Slot[] = ["weapon", "helm", "armor", "gloves", "boots", "ring", "amulet"];

export const SLOT_META: Record<Slot, { label: string; icon: string }> = {
  weapon: { label: "武器", icon: "sword" },
  helm: { label: "兜", icon: "helm" },
  armor: { label: "鎧", icon: "armor" },
  gloves: { label: "手甲", icon: "glove" },
  boots: { label: "靴", icon: "boot" },
  ring: { label: "指輪", icon: "ring" },
  amulet: { label: "護符", icon: "amulet" },
};

export type Rarity = "normal" | "magic" | "rare" | "legendary" | "mythic";

export const RARITIES: Rarity[] = ["normal", "magic", "rare", "legendary", "mythic"];

export const RARITY_META: Record<
  Rarity,
  { label: string; color: string; bg: string; affixes: [number, number]; power: number; weight: number }
> = {
  normal:    { label: "通常",   color: "#a8b0c0", bg: "rgba(168,176,192,0.12)", affixes: [0, 1], power: 1.0, weight: 46 },
  magic:     { label: "魔法",   color: "#4aa8ff", bg: "rgba(74,168,255,0.14)",  affixes: [1, 2], power: 1.5, weight: 29 },
  rare:      { label: "レア",   color: "#ffd23c", bg: "rgba(255,210,60,0.14)",  affixes: [3, 4], power: 2.2, weight: 15 },
  legendary: { label: "レジェンド", color: "#ff8a2b", bg: "rgba(255,138,43,0.16)", affixes: [4, 5], power: 3.2, weight: 7.5 },
  mythic:    { label: "神話",   color: "#e34bff", bg: "rgba(227,75,255,0.18)",  affixes: [5, 6], power: 4.6, weight: 2.5 },
};

type BaseDef = { name: string; mods: Mods; weight: number };

const BASES: Record<Slot, BaseDef[]> = {
  weapon: [
    { name: "ショートソード", mods: { damageFlat: 6, attackSpeedPct: 6 }, weight: 10 },
    { name: "ロングブレイド", mods: { damageFlat: 10, areaPct: 6 }, weight: 10 },
    { name: "バトルアクス", mods: { damageFlat: 16, attackSpeedPct: -10 }, weight: 8 },
    { name: "ツインダガー", mods: { damageFlat: 5, attackSpeedPct: 20, critPct: 3 }, weight: 8 },
    { name: "グレートハンマー", mods: { damageFlat: 22, attackSpeedPct: -20, areaPct: 14 }, weight: 6 },
    { name: "アークスタッフ", mods: { damageFlat: 9, projectiles: 1, attackSpeedPct: -6 }, weight: 5 },
    { name: "クロスボウ", mods: { damageFlat: 8, pierce: 1, attackSpeedPct: 10 }, weight: 6 },
    { name: "呪骨の杖", mods: { damageFlat: 11, lifestealPct: 2, critDmgPct: 14 }, weight: 4 },
  ],
  helm: [
    { name: "レザーフード", mods: { maxHpFlat: 16, armorPct: 2 }, weight: 10 },
    { name: "アイアンヘルム", mods: { maxHpFlat: 30, armorPct: 5 }, weight: 8 },
    { name: "魔導サークレット", mods: { maxHpFlat: 12, critPct: 3, damagePct: 4 }, weight: 6 },
    { name: "竜骨の兜", mods: { maxHpFlat: 40, armorPct: 7, thornsFlat: 6 }, weight: 3 },
  ],
  armor: [
    { name: "旅人の外套", mods: { maxHpFlat: 22, moveSpeedPct: 3 }, weight: 10 },
    { name: "チェインメイル", mods: { maxHpFlat: 40, armorPct: 6 }, weight: 9 },
    { name: "プレートアーマー", mods: { maxHpFlat: 68, armorPct: 11, moveSpeedPct: -6 }, weight: 6 },
    { name: "影装束", mods: { maxHpFlat: 20, moveSpeedPct: 10, dashCdPct: 8 }, weight: 5 },
    { name: "聖別の法衣", mods: { maxHpFlat: 44, regenFlat: 0.7, armorPct: 5 }, weight: 4 },
  ],
  gloves: [
    { name: "革の手袋", mods: { attackSpeedPct: 7 }, weight: 10 },
    { name: "ガントレット", mods: { attackSpeedPct: 4, damageFlat: 6, armorPct: 3 }, weight: 8 },
    { name: "狙撃手の手袋", mods: { attackSpeedPct: 10, critPct: 4 }, weight: 6 },
    { name: "血誓の腕輪", mods: { attackSpeedPct: 8, lifestealPct: 2.2 }, weight: 4 },
  ],
  boots: [
    { name: "軽装ブーツ", mods: { moveSpeedPct: 7 }, weight: 10 },
    { name: "重装グリーブ", mods: { moveSpeedPct: -3, armorPct: 5, maxHpFlat: 20 }, weight: 8 },
    { name: "疾風の靴", mods: { moveSpeedPct: 13, dashCdPct: 8 }, weight: 6 },
    { name: "浮遊のサンダル", mods: { moveSpeedPct: 9, xpPct: 8, pickupFlat: 30 }, weight: 4 },
  ],
  ring: [
    { name: "銅の指輪", mods: { damagePct: 5 }, weight: 10 },
    { name: "紅玉の指輪", mods: { damageFlat: 8, critDmgPct: 12 }, weight: 8 },
    { name: "蒼玉の指輪", mods: { maxHpFlat: 26, regenFlat: 0.5 }, weight: 8 },
    { name: "雷光の指輪", mods: { chain: 1, attackSpeedPct: 5 }, weight: 4 },
    { name: "貪欲の指輪", mods: { goldPct: 18, luckFlat: 10 }, weight: 6 },
  ],
  amulet: [
    { name: "鉄の護符", mods: { armorPct: 4, maxHpFlat: 18 }, weight: 10 },
    { name: "狼牙の護符", mods: { damagePct: 9, critPct: 2 }, weight: 8 },
    { name: "精霊のペンダント", mods: { areaPct: 10, energyPct: 12 }, weight: 6 },
    { name: "死神の首飾り", mods: { lifestealPct: 3, explodePct: 5 }, weight: 4 },
  ],
};

type AffixDef = { key: StatKey; min: number; max: number; scale: number; tier: 1 | 2 | 3; slots?: Slot[] };

const PREFIXES: string[] = [
  "焔の", "氷結の", "雷光の", "血塗れの", "古代の", "呪われし", "聖なる", "虚空の",
  "竜王の", "修羅の", "月影の", "太陽の", "深淵の", "嵐の", "骸の", "星辰の",
];
const SUFFIXES: string[] = [
  "破滅", "暴食", "疾風怒涛", "永劫", "慟哭", "天罰", "輪廻", "絶望",
  "王の栄光", "神殺し", "黄昏", "無明", "竜鱗", "殺意", "祝福", "終焉",
];
const LEGEND_NAMES: string[] = [
  "エクスカリバー", "グラム", "天羽々斬", "ミョルニル", "ガーンディーヴァ", "デュランダル",
  "ヘルメスの杖", "レーヴァテイン", "アスカロン", "ゲイ・ボルグ", "草薙剣", "ヴァジュラ",
];
const MYTHIC_NAMES: string[] = [
  "虚無神の残光", "創世の楔", "運命を断つ者", "無限の歯車", "神々の黄昏", "原初の炎",
  "時を喰らう顎", "万象の終焉", "アビス・コア", "天上の戒律",
];

const AFFIXES: AffixDef[] = [
  { key: "damageFlat", min: 3, max: 7, scale: 0.30, tier: 1 },
  { key: "damagePct", min: 4, max: 9, scale: 0.16, tier: 1 },
  { key: "maxHpFlat", min: 10, max: 24, scale: 0.34, tier: 1 },
  { key: "attackSpeedPct", min: 4, max: 9, scale: 0.12, tier: 1 },
  { key: "critPct", min: 1.5, max: 3.5, scale: 0.10, tier: 1 },
  { key: "moveSpeedPct", min: 3, max: 7, scale: 0.06, tier: 1 },
  { key: "armorPct", min: 2, max: 5, scale: 0.10, tier: 1 },
  { key: "areaPct", min: 4, max: 10, scale: 0.10, tier: 1, slots: ["weapon", "gloves", "amulet"] },
  { key: "maxHpPct", min: 4, max: 9, scale: 0.12, tier: 2 },
  { key: "critDmgPct", min: 8, max: 20, scale: 0.22, tier: 2 },
  { key: "lifestealPct", min: 1, max: 2.4, scale: 0.08, tier: 2 },
  { key: "regenFlat", min: 0.4, max: 1.1, scale: 0.10, tier: 2 },
  { key: "xpPct", min: 6, max: 14, scale: 0.10, tier: 2 },
  { key: "goldPct", min: 8, max: 20, scale: 0.14, tier: 2 },
  { key: "luckFlat", min: 6, max: 16, scale: 0.20, tier: 2 },
  { key: "dashCdPct", min: 5, max: 12, scale: 0.08, tier: 2, slots: ["boots", "ring", "amulet"] },
  { key: "pickupFlat", min: 20, max: 50, scale: 0.60, tier: 1 },
  { key: "energyPct", min: 8, max: 18, scale: 0.14, tier: 2 },
  { key: "thornsFlat", min: 5, max: 14, scale: 0.30, tier: 2, slots: ["armor", "helm", "ring"] },
  { key: "pierce", min: 1, max: 1, scale: 0, tier: 3, slots: ["weapon", "gloves", "ring"] },
  { key: "projectiles", min: 1, max: 1, scale: 0, tier: 3, slots: ["weapon", "amulet"] },
  { key: "chain", min: 1, max: 2, scale: 0.05, tier: 3, slots: ["weapon", "ring", "amulet"] },
  { key: "explodePct", min: 4, max: 10, scale: 0.10, tier: 3 },
  { key: "freezePct", min: 5, max: 12, scale: 0.10, tier: 3 },
];

export type Affix = { key: StatKey; label: string; text: string; value: number; tier: number };

export type Item = {
  id: string;
  slot: Slot;
  rarity: Rarity;
  base: string;
  name: string;
  ilvl: number;
  mods: Mods;
  affixes: Affix[];
  power: number;
  weaponClass?: WeaponClassId;
  weaponTrait?: string;
  forged?: number;
  /** 呪われた装備 — 強力だが致命的な弱点を1つ背負う */
  cursed?: boolean;
};

let itemIdSeq = 1;

export function rollRarity(r: RNG, ilvl: number, luck: number, bonus = 0): Rarity {
  const l = luck + ilvl * 1.6 + bonus;
  return weighted<Rarity>(r, RARITIES, (x) => {
    const base = RARITY_META[x].weight;
    if (x === "normal") return Math.max(4, base - l * 0.55);
    if (x === "magic") return base + l * 0.05;
    if (x === "rare") return base + l * 0.32;
    if (x === "legendary") return Math.max(0.4, base + l * 0.20);
    return Math.max(0.05, base * 0.35 + l * 0.075);
  });
}

export function genItem(r: RNG, ilvl: number, slot?: Slot, luck = 0, rarityBonus = 0): Item {
  const s: Slot = slot ?? pick(r, SLOTS);
  const rarity = rollRarity(r, ilvl, luck, rarityBonus);
  const meta = RARITY_META[rarity];
  const baseDef = weighted(r, BASES[s], (b) => b.weight);

  const mods: Mods = { ...baseDef.mods };
  const affixes: Affix[] = [];
  const count = rndInt(r, meta.affixes[0], meta.affixes[1]);
  const pool: AffixDef[] = [];
  for (const a of AFFIXES) if (!a.slots || a.slots.includes(s)) pool.push(a);

  const used = new Set<StatKey>();
  for (let i = 0; i < count; i++) {
    const avail = pool.filter((a) => !used.has(a.key));
    if (!avail.length) break;
    const tierBoost = rarity === "mythic" ? 3 : rarity === "legendary" ? 2 : 1;
    const def = weighted(r, avail, (a) => (a.tier <= tierBoost ? 6 - a.tier : 0.6));
    used.add(def.key);
    let value = rnd(r, def.min, def.max) * (1 + (ilvl - 1) * def.scale);
    if (def.key === "projectiles" || def.key === "pierce" || def.key === "chain") {
      value = Math.max(1, Math.round(value));
    } else {
      value = Math.round(value * 10) / 10;
    }
    const sm = STAT_META[def.key];
    affixes.push({ key: def.key, label: sm.label, text: sm.fmt(value), value, tier: def.tier });
    mods[def.key] = (mods[def.key] ?? 0) + value;
  }

  let name: string;
  if (rarity === "mythic") name = pick(r, MYTHIC_NAMES);
  else if (rarity === "legendary") name = pick(r, LEGEND_NAMES);
  else if (rarity === "rare") name = `${pick(r, PREFIXES)}${baseDef.name}`;
  else if (rarity === "magic") name = `${pick(r, PREFIXES)}${baseDef.name}・${pick(r, SUFFIXES)}`;
  else name = baseDef.name;

  // 呪い: レア以上で12% — 他を強化する代償に、明確な弱点を1つ背負う
  let cursed = false;
  if ((rarity === "rare" || rarity === "legendary" || rarity === "mythic") && r() < 0.12) {
    cursed = true;
    const cmod = pick(r, CURSED_MODS);
    const cval = Math.round(rnd(r, cmod.min, cmod.max) * 10) / 10;
    mods[cmod.key] = (mods[cmod.key] ?? 0) + cval;
    affixes.push({ key: cmod.key, label: `呪・${STAT_META[cmod.key].label}`, text: STAT_META[cmod.key].fmt(cval), value: cval, tier: 1 });
    // 代償に既存の特性を2つ底上げ
    const posKeys = (Object.keys(mods) as StatKey[]).filter((k) => (mods[k] ?? 0) > 0 && k !== cmod.key);
    for (let i = 0; i < 2 && posKeys.length > 0; i++) {
      const k = pick(r, posKeys);
      mods[k] = Math.max((mods[k] ?? 0) * 1.24, (mods[k] ?? 0) + 1);
    }
    name = `呪縛の${name}`;
  }

  let power = 0;
  for (const k of Object.keys(mods) as StatKey[]) power += Math.abs(mods[k] ?? 0) * STAT_META[k].worth;
  power = Math.round(power * meta.power * (1 + ilvl * 0.035) * (cursed ? 1.08 : 1));

  const item: Item = { id: `it_${itemIdSeq++}_${Math.floor(r() * 1e6)}`, slot: s, rarity, base: baseDef.name, name, ilvl, mods, affixes, power, forged: 0, cursed };
  // 武器には種別と特性を付与
  if (s === "weapon") {
    item.weaponClass = randomWeaponClass(r);
    const wc = WEAPON_CLASSES[item.weaponClass];
    for (const [k, v] of Object.entries(wc.bonus) as [StatKey, number][]) {
      item.mods[k] = (item.mods[k] ?? 0) + v;
    }
    // レア以上なら特性も付与
    if (rarity !== "normal") {
      const trait = pick(r, WEAPON_TRAITS);
      item.weaponTrait = trait.id;
      if (trait.effect === "crit_bonus") item.mods.damagePct = (item.mods.damagePct ?? 0) + trait.value;
      else if (trait.effect === "lifesteal") item.mods.lifestealPct = (item.mods.lifestealPct ?? 0) + trait.value * 0.4;
      else if (trait.effect === "execute") {} // engine 側で処理
      else if (trait.effect === "chain") item.mods.chain = (item.mods.chain ?? 0) + trait.value;
      else if (trait.effect === "burn") {} // engine 側で確率付与
      else if (trait.effect === "poison") {}
      else if (trait.effect === "bleed") {}
      else if (trait.effect === "frenzy") {}
      else if (trait.effect === "fortify") {}
      else if (trait.effect === "aoe_on_kill") {}
      // power 再計算
      let p2 = 0;
      for (const kk of Object.keys(item.mods) as StatKey[]) p2 += Math.abs(item.mods[kk] ?? 0) * STAT_META[kk].worth;
      item.power = Math.round(p2 * meta.power * (1 + ilvl * 0.035));
    }
  }
  return item;
}

/* -------------------------------------------------------------- 鍛造マージ */

export type ForgeResult = {
  item: Item;
  cost: Materials;
  bonusAffixes: number;
};

export function canForge(a: Item, b: Item): boolean {
  return a.slot === b.slot && a.id !== b.id;
}

export function forgeItems(r: RNG, a: Item, b: Item, mats: Materials, ilvl: number): ForgeResult | null {
  if (!canForge(a, b)) return null;
  const cost: Materials = { soul_shard: 3 + a.power + b.power };
  if (a.rarity === "mythic" || b.rarity === "mythic") cost.void_essence = 2;
  else if (a.rarity === "legendary" || b.rarity === "legendary") cost.chaos_gem = 1;
  else if (a.rarity === "rare" || b.rarity === "rare") cost.ancient_stone = 1;
  // 素材チェック
  for (const [k, v] of Object.entries(cost) as [MaterialId, number][]) {
    if ((mats[k] ?? 0) < v) return null;
  }
  // ベースはパワーリッチな方
  const [main, sub] = a.power >= b.power ? [a, b] : [b, a];
  // 希少度は最高ランクを採用
  const rarityOrder = RARITIES.indexOf(a.rarity) >= RARITIES.indexOf(b.rarity) ? a.rarity : b.rarity;
  // モジュラー合成: 両方のmodから最善値を採用
  const merged: Mods = {};
  const allKeys = new Set<StatKey>([...(Object.keys(main.mods) as StatKey[]), ...(Object.keys(sub.mods) as StatKey[])]);
  for (const k of allKeys) {
    const va = main.mods[k] ?? 0;
    const vb = sub.mods[k] ?? 0;
    // 正のmodなら最大値、負のmod（防御短縮等）なら絶対値が小さい方
    if (k === "dashCdPct") merged[k] = Math.max(va, vb);
    else merged[k] = Math.max(va, vb) * 1.05; // 5%ボーナス
  }
  // アフィックス合成: 重複を除いて上位を残す
  const affixMap = new Map<StatKey, Affix>();
  for (const af of [...main.affixes, ...sub.affixes]) {
    const existing = affixMap.get(af.key);
    if (!existing || af.value > existing.value) affixMap.set(af.key, { ...af, value: af.value * 1.08, text: STAT_META[af.key].fmt(af.value * 1.08) });
  }
  const mergedAffixes = [...affixMap.values()].sort((x, y) => y.value - x.value).slice(0, 8);
  const bonusAffixes = mergedAffixes.length > main.affixes.length ? mergedAffixes.length - main.affixes.length : 0;
  // 名前はprefix + 本体
  const prefix = rarityOrder === "mythic" ? pick(r, MYTHIC_NAMES) : `${pick(r, PREFIXES)}`;
  const forgedCount = ((main.forged ?? 0) + (sub.forged ?? 0) + 1);
  const name = forgedCount >= 5 ? `覚醒・${main.base}` : forgedCount >= 3 ? `神錬・${main.base}` : `${prefix}${main.base}`;
  let power = 0;
  for (const k of Object.keys(merged) as StatKey[]) power += Math.abs(merged[k] ?? 0) * STAT_META[k].worth;
  power = Math.round(power * RARITY_META[rarityOrder].power * (1 + ilvl * 0.035) * (1 + forgedCount * 0.12));
  const item: Item = {
    id: `it_${itemIdSeq++}_${Math.floor(r() * 1e6)}`, slot: main.slot, rarity: rarityOrder, base: main.base, name, ilvl,
    mods: merged, affixes: mergedAffixes, power,
    weaponClass: main.weaponClass ?? sub.weaponClass,
    weaponTrait: main.weaponTrait ?? sub.weaponTrait,
    forged: forgedCount,
    cursed: main.cursed ?? sub.cursed,
  };
  return { item, cost, bonusAffixes };
}

export const itemMods = (it: Item | null | undefined): Mods => (it ? it.mods : {});

export function diffItems(next: Item | null, cur: Item | null) {
  const a = sumMods([next ? next.mods : {}]);
  const b = sumMods([cur ? cur.mods : {}]);
  const keys = new Set<StatKey>([...(Object.keys(a) as StatKey[]), ...(Object.keys(b) as StatKey[])]);
  const rows: { key: StatKey; label: string; delta: number; text: string }[] = [];
  for (const k of STAT_ORDER) {
    if (!keys.has(k)) continue;
    const d = (a[k] ?? 0) - (b[k] ?? 0);
    if (Math.abs(d) < 0.05) continue;
    const good = k === "dashCdPct" ? d > 0 : d > 0;
    rows.push({ key: k, label: STAT_META[k].label, delta: d, text: STAT_META[k].fmt(Math.abs(d)) });
    void good;
  }
  return rows;
}

/* ------------------------------------------------------------------ 祝福 */

export const ABILITIES = {
  orbit: { name: "回転刃", desc: "周囲を刃が周回し敵を斬り裂く", max: 5, color: "#e8f0ff" },
  aura: { name: "聖なる領域", desc: "常時、周囲の敵に神聖ダメージ", max: 5, color: "#ffd76a" },
  chain: { name: "連鎖雷撃", desc: "攻撃命中時に雷が飛び移る", max: 5, color: "#7ce8ff" },
  missile: { name: "追尾魔弾", desc: "自動で最も近い敵に魔弾を発射", max: 5, color: "#c58bff" },
  nova: { name: "氷霜新星", desc: "定期的に氷の衝撃波で敵を凍結", max: 5, color: "#8be9ff" },
  familiar: { name: "使い魔", desc: "影の眷属が共に戦う", max: 3, color: "#7cffb2" },
  execute: { name: "処刑", desc: "HPの低い敵へのダメージが激増", max: 3, color: "#ff6a6a" },
  blood: { name: "血の契約", desc: "撃破ごとにHPが回復する", max: 3, color: "#ff4d6d" },
  magnet: { name: "貪欲なる磁場", desc: "ドロップを強力に引き寄せる", max: 3, color: "#ffe066" },
  quake: { name: "大地の怒り", desc: "回避時に地面が爆発する", max: 3, color: "#ff9a3c" },
  // --- 追加アビリティ ---
  bladespin:  { name: "渦刃",     desc: "攻撃のたびに周囲へ刃の渦が飛ぶ",       max: 4, color: "#d6e8ff" },
  vengeance:  { name: "復讐",     desc: "被弾した直後、周囲に爆発を放つ",       max: 4, color: "#ff3b5c" },
  thunderfury:{ name: "天雷",     desc: "命中のたび、確率で落雷が落ちる",       max: 4, color: "#ffe14a" },
  echo:       { name: "残響",     desc: "3段フィニッシャーが確率で再放される",   max: 3, color: "#c58bff" },
  greed:      { name: "強欲",     desc: "ゴールドが増えるが被ダメージも増す",    max: 3, color: "#ffb03c" },
  warden:     { name: "障壁",     desc: "立ち止まっているほどダメージ軽減",      max: 3, color: "#8fa3bf" },
  harvest:    { name: "刈り取り", desc: "エリート・ボス撃破で全ステータス上昇",  max: 3, color: "#7cffb2" },
  overflow:   { name: "過剰修復", desc: "満タンのHPを攻撃力に変換する",          max: 3, color: "#5cff9d" },
  absolute_domain: { name: "絶対領域", desc: "魂ゲージ100%で発動。3秒間の無敵バリアを展開する", max: 1, color: "#ff00ff" },
} as const;

export type AbilityId = keyof typeof ABILITIES;

export type Boon = {
  id: string;
  name: string;
  desc: string;
  grade: "common" | "rare" | "epic";
  mods?: Mods;
  ability?: AbilityId;
};

export const BOONS: Boon[] = [
  { id: "b_dmg1", name: "剛力の刻印", desc: "攻撃力 +18%", grade: "common", mods: { damagePct: 18 } },
  { id: "b_dmg2", name: "破壊の刻印", desc: "攻撃力 +12 / 攻撃力 +8%", grade: "common", mods: { damageFlat: 12, damagePct: 8 } },
  { id: "b_as", name: "迅風の刻印", desc: "攻撃速度 +16%", grade: "common", mods: { attackSpeedPct: 16 } },
  { id: "b_crit", name: "心眼の刻印", desc: "クリティカル率 +7%", grade: "common", mods: { critPct: 7 } },
  { id: "b_critdmg", name: "致命の刻印", desc: "クリティカル威力 +45%", grade: "rare", mods: { critDmgPct: 45 } },
  { id: "b_hp", name: "巨躯の刻印", desc: "最大HP +18% / HP回復 +0.6/秒", grade: "common", mods: { maxHpPct: 18, regenFlat: 0.6 } },
  { id: "b_armor", name: "鉄壁の刻印", desc: "ダメージ軽減 +8%", grade: "common", mods: { armorPct: 8 } },
  { id: "b_speed", name: "疾走の刻印", desc: "移動速度 +12% / 回避短縮 12%", grade: "common", mods: { moveSpeedPct: 12, dashCdPct: 12 } },
  { id: "b_area", name: "拡大の刻印", desc: "攻撃範囲 +22%", grade: "common", mods: { areaPct: 22 } },
  { id: "b_life", name: "吸血の刻印", desc: "生命吸収 +4%", grade: "rare", mods: { lifestealPct: 4 } },
  { id: "b_proj", name: "分裂の刻印", desc: "追加弾数 +1", grade: "epic", mods: { projectiles: 1 } },
  { id: "b_pierce", name: "貫通の刻印", desc: "貫通 +2", grade: "rare", mods: { pierce: 2 } },
  { id: "b_explode", name: "爆砕の刻印", desc: "爆裂確率 +18%", grade: "rare", mods: { explodePct: 18 } },
  { id: "b_freeze", name: "凍牙の刻印", desc: "氷結確率 +20%", grade: "rare", mods: { freezePct: 20 } },
  { id: "b_thorns", name: "棘甲の刻印", desc: "棘ダメージ +25", grade: "common", mods: { thornsFlat: 25 } },
  { id: "b_energy", name: "魂の高揚", desc: "魂ゲージ増加 +35%", grade: "common", mods: { energyPct: 35 } },
  { id: "b_xp", name: "知識の渇望", desc: "経験値 +25% / 運 +20", grade: "common", mods: { xpPct: 25, luckFlat: 20 } },
  { id: "b_orbit", name: "回転刃", desc: "周囲を刃が周回する", grade: "rare", ability: "orbit" },
  { id: "b_aura", name: "聖なる領域", desc: "常時、周囲の敵を焼く", grade: "rare", ability: "aura" },
  { id: "b_chain", name: "連鎖雷撃", desc: "命中時に雷が連鎖する", grade: "epic", ability: "chain" },
  { id: "b_missile", name: "追尾魔弾", desc: "自動で魔弾を発射する", grade: "rare", ability: "missile" },
  { id: "b_nova", name: "氷霜新星", desc: "定期 Nova で敵を凍結", grade: "epic", ability: "nova" },
  { id: "b_familiar", name: "影の眷属", desc: "使い魔が共に戦う", grade: "epic", ability: "familiar" },
  { id: "b_execute", name: "処刑人", desc: "瀕死の敵へ +90% ダメージ", grade: "rare", ability: "execute" },
  { id: "b_blood", name: "血の契約", desc: "撃破時にHP回復", grade: "common", ability: "blood" },
  { id: "b_magnet", name: "貪欲なる磁場", desc: "回収範囲 大幅拡大", grade: "common", ability: "magnet" },
  { id: "b_quake", name: "大地の怒り", desc: "回避着地時に爆発", grade: "rare", ability: "quake" },
  // --- 追加祝福 ---
  { id: "b_bladespin", name: "渦刃", desc: "攻撃のたびに刃の渦が飛ぶ", grade: "rare", ability: "bladespin" },
  { id: "b_vengeance", name: "復讐の刻印", desc: "被弾時に周囲へ爆発", grade: "rare", ability: "vengeance" },
  { id: "b_thunderfury", name: "天雷の刻印", desc: "命中時に確率で落雷", grade: "epic", ability: "thunderfury" },
  { id: "b_echo", name: "残響の刻印", desc: "フィニッシャーが再放される", grade: "epic", ability: "echo" },
  { id: "b_greed", name: "強欲の刻印", desc: "ゴールド+55% / 被ダメ+10%", grade: "common", ability: "greed" },
  { id: "b_warden", name: "障壁の刻印", desc: "立ち止まると軽減が上がる", grade: "common", ability: "warden" },
  { id: "b_harvest", name: "刈り取り", desc: "強敵撃破で全ステータス強化", grade: "epic", ability: "harvest" },
  { id: "b_overflow", name: "過剰修復", desc: "余ったHPを攻撃力へ変換", grade: "epic", ability: "overflow" },
  { id: "b_curse_eater", name: "呪喰らい", desc: "呪装備の欠点を無効化し効果を2倍", grade: "epic", mods: {} },
  { id: "b_lastlaugh", name: "最後の笑い", desc: "HP30%以下で攻撃+70%", grade: "rare", mods: {} },
  { id: "b_perfect", name: "完全主義", desc: "見切り後3秒間、攻撃+90%", grade: "epic", mods: {} },
  { id: "b_absolute_domain", name: "絶対領域", desc: "魂ゲージ100%で発動。3秒間の無敵バリアを展開する", grade: "epic", ability: "absolute_domain" },
];

export const BOON_GRADE = {
  common: { label: "一般", color: "#9fb0c9" },
  rare: { label: "稀有", color: "#4aa8ff" },
  epic: { label: "神聖", color: "#ff8a2b" },
} as const;

export function rollBoons(r: RNG, have: Record<string, number>, n = 3): Boon[] {
  const pool = BOONS.filter((b) => {
    if (!b.ability) return true;
    return (have[b.ability] ?? 0) < ABILITIES[b.ability].max;
  });
  const out: Boon[] = [];
  const used = new Set<string>();
  while (out.length < Math.min(n, pool.length)) {
    const b = weighted(r, pool, (x) =>
      used.has(x.id) ? 0 : x.grade === "epic" ? 1.1 : x.grade === "rare" ? 2.4 : 4.2,
    );
    if (used.has(b.id)) break;
    used.add(b.id);
    out.push(b);
  }
  return out;
}

/* -------------------------------------------------------------------- 敵 */

export type EnemyShape = "slime" | "bat" | "skeleton" | "imp" | "golem" | "wraith" | "dragon" | "knight" | "eye" | "god" | "guard" | "necromancer" | "gate";
export type EnemyAI = "chase" | "shoot" | "charge" | "bomber" | "orbit" | "teleport" | "split" | "guard" | "summoner" | "gate";

export type EnemyDef = {
  id: string; name: string; hp: number; dmg: number; speed: number; r: number;
  xp: number; ai: EnemyAI; shape: EnemyShape; color: string; accent: string;
  minFloor: number; elite?: boolean; boss?: boolean; trueBoss?: boolean; pinnacleBoss?: boolean; ascensionBoss?: boolean;
  fireRate?: number; projSpeed?: number;
};

export const ENEMIES: EnemyDef[] = [
  { id: "iron_guard", name: "鉄壁の護衛兵", hp: 76, dmg: 15, speed: 77, r: 25, xp: 14, ai: "guard", shape: "guard", color: "#7d9eb9", accent: "#d7ebff", minFloor: 4 },
  { id: "necromancer", name: "骸骨の蘇生師", hp: 68, dmg: 10, speed: 95, r: 21, xp: 18, ai: "summoner", shape: "necromancer", color: "#b28ce0", accent: "#e9d2ff", minFloor: 6 },
  { id: "cursed_gate", name: "呪われた召喚門", hp: 180, dmg: 0, speed: 0, r: 34, xp: 32, ai: "gate", shape: "gate", color: "#bd517d", accent: "#ffc4d8", minFloor: 7 },
  { id: "slime", name: "腐敗スライム", hp: 22, dmg: 7, speed: 74, r: 17, xp: 3, ai: "chase", shape: "slime", color: "#4bd07a", accent: "#c8ffe0", minFloor: 1 },
  { id: "bat", name: "血吸いコウモリ", hp: 14, dmg: 6, speed: 148, r: 13, xp: 3, ai: "orbit", shape: "bat", color: "#9d6bff", accent: "#e6d6ff", minFloor: 1 },
  { id: "goblin", name: "洞窟ゴブリン", hp: 30, dmg: 9, speed: 96, r: 16, xp: 4, ai: "chase", shape: "skeleton", color: "#7ec850", accent: "#f2ffd6", minFloor: 1 },
  { id: "skeleton", name: "骸骨兵", hp: 42, dmg: 12, speed: 88, r: 17, xp: 6, ai: "chase", shape: "skeleton", color: "#dfe6ee", accent: "#8b95a5", minFloor: 2 },
  { id: "imp", name: "火焔インプ", hp: 26, dmg: 11, speed: 92, r: 15, xp: 6, ai: "shoot", shape: "imp", color: "#ff7a3c", accent: "#ffd9a8", minFloor: 3, fireRate: 1.7, projSpeed: 260 },
  { id: "bomber", name: "自爆ガエル", hp: 34, dmg: 26, speed: 132, r: 18, xp: 7, ai: "bomber", shape: "slime", color: "#ffb03c", accent: "#fff0c9", minFloor: 4 },
  { id: "wraith", name: "怨念レイス", hp: 52, dmg: 15, speed: 118, r: 18, xp: 10, ai: "chase", shape: "wraith", color: "#6ad7ff", accent: "#e6fbff", minFloor: 6 },
  { id: "golem", name: "岩塊ゴーレム", hp: 140, dmg: 22, speed: 58, r: 27, xp: 16, ai: "chase", shape: "golem", color: "#a58a6b", accent: "#ffd9a0", minFloor: 7 },
  { id: "eye", name: "浮遊邪眼", hp: 60, dmg: 14, speed: 82, r: 19, xp: 12, ai: "shoot", shape: "eye", color: "#ff5c9d", accent: "#ffd6e8", minFloor: 9, fireRate: 1.35, projSpeed: 320 },
  { id: "charger", name: "猪突オーク", hp: 96, dmg: 20, speed: 108, r: 22, xp: 14, ai: "charge", shape: "golem", color: "#c96a4a", accent: "#ffe0cf", minFloor: 11 },
  { id: "dragonkin", name: "竜人", hp: 130, dmg: 24, speed: 112, r: 23, xp: 20, ai: "shoot", shape: "dragon", color: "#e0455f", accent: "#ffd0d8", minFloor: 14, fireRate: 1.5, projSpeed: 380 },
  // --- 考察を要求する新種 ---
  { id: "splitter", name: "分裂スライム母", hp: 80, dmg: 11, speed: 66, r: 25, xp: 11, ai: "split", shape: "slime", color: "#7dd6a0", accent: "#d6ffe8", minFloor: 5 },
  { id: "phantom", name: "幻影ファントム", hp: 66, dmg: 17, speed: 96, r: 17, xp: 17, ai: "teleport", shape: "wraith", color: "#a070ff", accent: "#e8dcff", minFloor: 7 },
  { id: "cultist", name: "深淵教徒", hp: 45, dmg: 13, speed: 84, r: 16, xp: 12, ai: "shoot", shape: "imp", color: "#ff8ac2", accent: "#ffd6ec", minFloor: 8, fireRate: 2.0, projSpeed: 420 },
  { id: "mirrorbat", name: "分身コウモリ", hp: 20, dmg: 5, speed: 170, r: 12, xp: 4, ai: "orbit", shape: "bat", color: "#5cd8ff", accent: "#e0f8ff", minFloor: 10 },
];

/** 通常踏破層（虚無神ニヒル） */
export const FINAL_FLOOR = 25;

/** 第25層のニヒルを撃破した後にのみ挑める、真の最終決戦。 */
export const TRUE_FLOOR = 26;

/** 終焉の座（最難関決戦・タルタロス・オメガ）— 全装備総合力5000超え・全強化済み想定。 */
export const PINNACLE_FLOOR = 27;

export const BOSSES: { floor: number; def: EnemyDef }[] = [
  { floor: 5, def: { id: "b_goblin", name: "ゴブリン王 グルザ", hp: 900, dmg: 20, speed: 82, r: 44, xp: 120, ai: "chase", shape: "golem", color: "#8fd44f", accent: "#ffe98a", minFloor: 5, boss: true } },
  { floor: 10, def: { id: "b_wyrm", name: "屍竜 ヴァイト", hp: 2100, dmg: 28, speed: 96, r: 52, xp: 260, ai: "shoot", shape: "dragon", color: "#7c5cff", accent: "#d9c9ff", minFloor: 10, boss: true, fireRate: 1.0, projSpeed: 340 } },
  { floor: 15, def: { id: "b_knight", name: "深淵騎士アビス", hp: 4200, dmg: 36, speed: 112, r: 54, xp: 480, ai: "charge", shape: "knight", color: "#ff3b5c", accent: "#ffc9d2", minFloor: 15, boss: true } },
  { floor: 20, def: { id: "b_eye", name: "万象の邪眼", hp: 7600, dmg: 44, speed: 92, r: 58, xp: 780, ai: "shoot", shape: "eye", color: "#ff5cc8", accent: "#ffd6f2", minFloor: 20, boss: true, fireRate: 0.65, projSpeed: 400 } },
  { floor: 25, def: { id: "b_god", name: "虚無神 ニヒル", hp: 15000, dmg: 56, speed: 108, r: 66, xp: 2000, ai: "chase", shape: "god", color: "#b14bff", accent: "#f0d6ff", minFloor: 25, boss: true } },
  // 真・最終決戦 — ニヒル撃破後にのみ挑める。3フェーズ制。
  { floor: TRUE_FLOOR, def: { id: "b_true", name: "原初虚無 《ニヒル・ゼロ》", hp: 34000, dmg: 74, speed: 118, r: 78, xp: 6000, ai: "chase", shape: "god", color: "#ffffff", accent: "#e34bff", minFloor: TRUE_FLOOR, boss: true, trueBoss: true } },
  // 終焉の座（最難関決戦・タルタロス・オメガ）— 総合力5000+想定、4フェーズ制。
  { floor: PINNACLE_FLOOR, def: { id: "b_pinnacle", name: "終焉神 《タルタロス・オメガ》", hp: 380000, dmg: 280, speed: 132, r: 88, xp: 25000, ai: "chase", shape: "god", color: "#ff2a55", accent: "#ffd700", minFloor: PINNACLE_FLOOR, boss: true, trueBoss: true, pinnacleBoss: true } },
  // 昇華の刻 — 真の深淵と終焉の座を制した者のみが挑める超越者
  { floor: PINNACLE_FLOOR + 1, def: { id: "b_ascension", name: "超越者", hp: 850000, dmg: 380, speed: 165, r: 96, xp: 50000, ai: "chase", shape: "god", color: "#ffd700", accent: "#ffffff", minFloor: PINNACLE_FLOOR + 1, boss: true, trueBoss: true, ascensionBoss: true } },
];


/* ------------------------------------------------------- 真・最終決戦 */


export type TruePhase = {
  /** このフェーズに移行する残HP割合の下限 */
  hpAbove: number;
  label: string;
  color: string;
  /** 弾幕の発射間隔（秒） */
  fireRate: number;
  /** 1回の弾幕あたりの弾数 */
  bullets: number;
  /** 弾速 */
  projSpeed: number;
  /** 螺旋弾幕を使う */
  spiral?: boolean;
  /** 全方位弾幕を使う */
  radial?: boolean;
  /** 突進を使う */
  charge?: boolean;
  /** 取り巻きを召喚する */
  summon?: number;
  /** 移動速度倍率 */
  speed: number;
};

export const TRUE_BOSS_PHASES: TruePhase[] = [
  { hpAbove: 0.66, label: "虚無の胎動", color: "#b14bff", fireRate: 0.9, bullets: 9, projSpeed: 300, speed: 1, spiral: true },
  { hpAbove: 0.33, label: "深淵の咆哮", color: "#ff3b5c", fireRate: 0.62, bullets: 14, projSpeed: 340, speed: 1.22, spiral: true, radial: true, charge: true },
  { hpAbove: 0,    label: "終焉・万物の帰無", color: "#ffffff", fireRate: 0.42, bullets: 20, projSpeed: 390, speed: 1.45, spiral: true, radial: true, charge: true, summon: 3 },
];

export function truePhaseFor(hpRatio: number): TruePhase {
  for (const p of TRUE_BOSS_PHASES) {
    if (hpRatio > p.hpAbove) return p;
  }
  return TRUE_BOSS_PHASES[TRUE_BOSS_PHASES.length - 1];
}

/* ------------------------------------------------------- 終焉の座（最難関決戦） */

export type PinnaclePhase = {
  hpAbove: number;
  label: string;
  color: string;
  fireRate: number;
  bullets: number;
  projSpeed: number;
  speed: number;
  crossBeams?: boolean;
  cataclysm?: boolean;
  dimensionalSlash?: boolean;
  shieldGuards?: boolean;
};

export const PINNACLE_PHASES: PinnaclePhase[] = [
  { hpAbove: 0.75, label: "天墜の審判", color: "#ffd700", fireRate: 0.50, bullets: 16, projSpeed: 380, speed: 1.1, crossBeams: true },
  { hpAbove: 0.50, label: "次元断絶", color: "#ff8a2b", fireRate: 0.38, bullets: 22, projSpeed: 420, speed: 1.25, crossBeams: true, dimensionalSlash: true, shieldGuards: true },
  { hpAbove: 0.25, label: "終末の咆哮", color: "#ff2a55", fireRate: 0.30, bullets: 28, projSpeed: 460, speed: 1.4, crossBeams: true, dimensionalSlash: true },
  { hpAbove: 0,    label: "原初崩壊・星辰帰無", color: "#ffffff", fireRate: 0.22, bullets: 36, projSpeed: 520, speed: 1.55, crossBeams: true, cataclysm: true },
];

export function pinnaclePhaseFor(hpRatio: number): PinnaclePhase {
  for (const p of PINNACLE_PHASES) {
    if (hpRatio > p.hpAbove) return p;
  }
  return PINNACLE_PHASES[PINNACLE_PHASES.length - 1];
}

/** 終焉の座専用：総合力5000超の神話級創世神具一式（7スロット）を生成 */
export function generatePinnacleLoadout(r: RNG, cls: ClassDef, heirloom?: Item | null): Record<Slot, Item> {
  const wClass: WeaponClassId = cls.attack === "melee"
    ? (cls.id === "samurai" ? "katana" : cls.id === "ninja" ? "dagger" : "hammer")
    : (cls.id === "mage" ? "staff" : "bow");

  const weapon: Item = {
    id: "pinnacle_weapon", slot: "weapon", rarity: "mythic",
    base: "神剣ラグナロク", name: `創世神話・天墜の${cls.name === "魔導士" ? "アークスタッフ" : cls.name === "銃魔" ? "クロスボウ" : cls.name === "忍者" ? "ツインダガー" : cls.name === "聖騎士" ? "グレートハンマー" : "神剣"}`,
    ilvl: 40, power: 980,
    weaponClass: wClass, weaponTrait: "vampiric", forged: 3,
    mods: { damageFlat: 220, damagePct: 140, attackSpeedPct: 50, critPct: 26, critDmgPct: 100, pierce: 2, chain: 2 },
    affixes: [
      { key: "damagePct", label: "攻撃力", text: "+140%", value: 140, tier: 3 },
      { key: "damageFlat", label: "攻撃力", text: "+220", value: 220, tier: 3 },
      { key: "critPct", label: "クリティカル率", text: "+26.0%", value: 26, tier: 3 },
      { key: "critDmgPct", label: "クリティカル威力", text: "+100%", value: 100, tier: 3 },
      { key: "attackSpeedPct", label: "攻撃速度", text: "+50%", value: 50, tier: 3 },
    ],
  };

  const helm: Item = {
    id: "pinnacle_helm", slot: "helm", rarity: "mythic",
    base: "竜骨の兜", name: "原初の神冠・竜骨の光背",
    ilvl: 40, power: 740, forged: 3,
    mods: { maxHpFlat: 480, maxHpPct: 60, armorPct: 22, thornsFlat: 90, luckFlat: 40 },
    affixes: [
      { key: "maxHpFlat", label: "最大HP", text: "+480", value: 480, tier: 3 },
      { key: "maxHpPct", label: "最大HP", text: "+60%", value: 60, tier: 3 },
      { key: "armorPct", label: "ダメージ軽減", text: "+22%", value: 22, tier: 3 },
      { key: "thornsFlat", label: "棘ダメージ", text: "+90", value: 90, tier: 3 },
    ],
  };

  const armor: Item = {
    id: "pinnacle_armor", slot: "armor", rarity: "mythic",
    base: "プレートアーマー", name: "天蓋の神鎧・不滅の白銀",
    ilvl: 40, power: 880, forged: 3,
    mods: { maxHpFlat: 720, maxHpPct: 85, armorPct: 36, regenFlat: 18, moveSpeedPct: 15 },
    affixes: [
      { key: "maxHpFlat", label: "最大HP", text: "+720", value: 720, tier: 3 },
      { key: "maxHpPct", label: "最大HP", text: "+85%", value: 85, tier: 3 },
      { key: "armorPct", label: "ダメージ軽減", text: "+36%", value: 36, tier: 3 },
      { key: "regenFlat", label: "HP自動回復", text: "+18.0/秒", value: 18, tier: 3 },
    ],
  };

  const gloves: Item = {
    id: "pinnacle_gloves", slot: "gloves", rarity: "mythic",
    base: "血誓の腕輪", name: "万象を砕く者・神滅の篭手",
    ilvl: 40, power: 720, forged: 3,
    mods: { attackSpeedPct: 45, damagePct: 55, critPct: 18, lifestealPct: 6, areaPct: 25 },
    affixes: [
      { key: "attackSpeedPct", label: "攻撃速度", text: "+45%", value: 45, tier: 3 },
      { key: "damagePct", label: "攻撃力", text: "+55%", value: 55, tier: 3 },
      { key: "critPct", label: "クリティカル率", text: "+18.0%", value: 18, tier: 3 },
      { key: "lifestealPct", label: "生命吸収", text: "+6.0%", value: 6, tier: 3 },
    ],
  };

  const boots: Item = {
    id: "pinnacle_boots", slot: "boots", rarity: "mythic",
    base: "疾風の靴", name: "星辰の歩廊・天翔ける翼",
    ilvl: 40, power: 700, forged: 3,
    mods: { moveSpeedPct: 40, dashCdPct: 35, maxHpFlat: 340, pickupFlat: 90, xpPct: 30 },
    affixes: [
      { key: "moveSpeedPct", label: "移動速度", text: "+40%", value: 40, tier: 3 },
      { key: "dashCdPct", label: "回避短縮", text: "-35%", value: 35, tier: 3 },
      { key: "maxHpFlat", label: "最大HP", text: "+340", value: 340, tier: 3 },
      { key: "pickupFlat", label: "回収範囲", text: "+90", value: 90, tier: 3 },
    ],
  };

  const ring: Item = {
    id: "pinnacle_ring", slot: "ring", rarity: "mythic",
    base: "雷光の指輪", name: "無限連鎖・天変の輪環",
    ilvl: 40, power: 780, forged: 3,
    mods: { damagePct: 70, critDmgPct: 65, chain: 3, energyPct: 55, goldPct: 50 },
    affixes: [
      { key: "damagePct", label: "攻撃力", text: "+70%", value: 70, tier: 3 },
      { key: "critDmgPct", label: "クリティカル威力", text: "+65%", value: 65, tier: 3 },
      { key: "chain", label: "連鎖雷撃", text: "+3", value: 3, tier: 3 },
      { key: "energyPct", label: "魂ゲージ増加", text: "+55%", value: 55, tier: 3 },
    ],
  };

  const amulet: Item = {
    id: "pinnacle_amulet", slot: "amulet", rarity: "mythic",
    base: "死神の首飾り", name: "虚空の心臓・万象の護符",
    ilvl: 40, power: 820, forged: 3,
    mods: { damagePct: 80, maxHpPct: 55, areaPct: 45, luckFlat: 90, explodePct: 25 },
    affixes: [
      { key: "damagePct", label: "攻撃力", text: "+80%", value: 80, tier: 3 },
      { key: "maxHpPct", label: "最大HP", text: "+55%", value: 55, tier: 3 },
      { key: "areaPct", label: "攻撃範囲", text: "+45%", value: 45, tier: 3 },
      { key: "luckFlat", label: "運", text: "+90", value: 90, tier: 3 },
    ],
  };

  const loadout: Record<Slot, Item> = { weapon, helm, armor, gloves, boots, ring, amulet };

  if (heirloom) {
    const elevated: Item = {
      ...heirloom,
      power: Math.max(heirloom.power, 960),
      ilvl: Math.max(heirloom.ilvl, 40),
      rarity: "mythic",
    };
    loadout[heirloom.slot] = elevated;
  }

  return loadout;
}

/* ------------------------------------------------------- フロア異変 */

/** 各層で発生しうる異変 — 同じ層でも毎回違う脅威と恩恵が混ざる */
export type FloorMutation = {
  id: string;
  name: string;
  color: string;
  desc: string;
  enemyHp: number;
  enemyDmg: number;
  enemySpeed: number;
  spawn: number;
  eliteRate: number;
  gold: number;
  loot: number;
  hazards: number;
  darkness: boolean;
};

export const FLOOR_MUTATIONS: FloorMutation[] = [
  {
    id: "darkness", name: "闇の迷宮", color: "#5c5c9e", desc: "視界が極度に塞がれる。転移門の光だけが頼り。",
    enemyHp: 1, enemyDmg: 1.05, enemySpeed: 1, spawn: 1, eliteRate: 1.25, gold: 1.2, loot: 1.4, hazards: 0, darkness: true,
  },
  {
    id: "frenzy", name: "狂乱の檻", color: "#ff5c5c", desc: "敵の速度と出现数が目に見えて跳ね上がる。",
    enemyHp: 0.92, enemyDmg: 1.08, enemySpeed: 1.3, spawn: 1.38, eliteRate: 1.3, gold: 1.25, loot: 1.2, hazards: 0, darkness: false,
  },
  {
    id: "gilded", name: "黄金の流砂", color: "#f5c542", desc: "敵が頑丈になるが、ゴールドが降り注ぐ。金もちの楽園。",
    enemyHp: 1.24, enemyDmg: 1, enemySpeed: 0.95, spawn: 1.05, eliteRate: 1.4, gold: 2.3, loot: 1.2, hazards: 1, darkness: false,
  },
  {
    id: "bloodfeast", name: "血の饗宴", color: "#e0243c", desc: "敵の牙が研がれる。生命力を投資するか、火力で制すか。",
    enemyHp: 1.1, enemyDmg: 1.38, enemySpeed: 1.08, spawn: 1.12, eliteRate: 1.35, gold: 1.3, loot: 1.35, hazards: 1, darkness: false,
  },
  {
    id: "fortress", name: "防衛線", color: "#8fa3bf", desc: "敵の装甲が異様に固い。会心と特性で崩せ。",
    enemyHp: 1.58, enemyDmg: 1, enemySpeed: 0.88, spawn: 0.78, eliteRate: 1.5, gold: 1.3, loot: 1.25, hazards: 0, darkness: false,
  },
  {
    id: "mire", name: "毒瘴の構造", color: "#4bd07a", desc: "毒沼が増す。立ちゃ入るだけで削り合いになる。",
    enemyHp: 1.15, enemyDmg: 1.12, enemySpeed: 1, spawn: 1, eliteRate: 1.2, gold: 1.15, loot: 1.3, hazards: 5, darkness: false,
  },
  {
    id: "bounty", name: "豊穣の刻", color: "#7cffb2", desc: "報酬が増えるが、それを狙う者も増える。",
    enemyHp: 1.05, enemyDmg: 1.14, enemySpeed: 1, spawn: 1.18, eliteRate: 1.65, gold: 1.25, loot: 1.85, hazards: 0, darkness: false,
  },
  {
    id: "swarm", name: "群れの層", color: "#ffe14a", desc: "敵が数重ねて押し寄せる。範囲ビルドの品最好。",
    enemyHp: 0.85, enemyDmg: 0.95, enemySpeed: 1.12, spawn: 1.65, eliteRate: 0.9, gold: 1.2, loot: 1.1, hazards: 0, darkness: false,
  },
  {
    id: "champion", name: "強者の巡礼地", color: "#e34bff", desc: "鍛え上げた強敌に出会いやすい。経験値は最高。",
    enemyHp: 1.12, enemyDmg: 1.15, enemySpeed: 1.04, spawn: 0.95, eliteRate: 2.3, gold: 1.3, loot: 1.5, hazards: 0, darkness: false,
  },
];

/** 通常層は約6割、ボス層は1割で異変が起こる。真・決戦層は常に平穏 */
export function rollFloorMutation(r: RNG, floor: number): FloorMutation | null {
  if (floor >= TRUE_FLOOR) return null;
  const bossFloor = floor % 5 === 0;
  const chance = bossFloor ? 0.14 : 0.62;
  if (r() > chance) return null;
  return pick(r, FLOOR_MUTATIONS);
}

/* ------------------------------------------------------- 層の番人 */

/** ボス層でない層にのみ出るミニボス */
export type WardenDef = { name: string; color: string; hpMul: number; dmgMul: number; flavor: string };

export const WARDENS: WardenDef[] = [
  { name: "石憑の番人・ガルグ", color: "#a58a6b", hpMul: 5.2, dmgMul: 1.7, flavor: "層を護り続ける朽ちぬ石像。" },
  { name: "幽玄の番人・ルファ", color: "#6ad7ff", hpMul: 4.6, dmgMul: 1.9, flavor: "迷い込んだ者を静かに刈り取る。" },
  { name: "窮獄の番人・メズ", color: "#ff6a5c", hpMul: 5.8, dmgMul: 2.0, flavor: "深淵の責め苦が仇成す者。" },
  { name: "永劫の番人・ノヴォ", color: "#c58bff", hpMul: 6.2, dmgMul: 2.2, flavor: "何人かの冒険者の記憶が人の形を模した。" },
  { name: "戸惑の番人・ツイマ", color: "#ffe14a", hpMul: 6.8, dmgMul: 2.4, flavor: "あらゆる扉の先にその知識が宿る。" },
];

/** 番人が出る層（ボス層と真層を除く） */
export function isWardenFloor(floor: number): boolean {
  return floor % 5 !== 0 && floor < TRUE_FLOOR && floor >= 3 && (floor % 10 === 3 || floor % 10 === 8);
}

/* ------------------------------------------------------- 呪われた装備 */

export const CURSED_MODS: { key: StatKey; min: number; max: number }[] = [
  { key: "maxHpPct", min: -18, max: -9 },
  { key: "moveSpeedPct", min: -12, max: -6 },
  { key: "armorPct", min: -9, max: -4 },
  { key: "attackSpeedPct", min: -14, max: -8 },
  { key: "xpPct", min: -20, max: -10 },
  { key: "luckFlat", min: -30, max: -12 },
];
/* --------------------------------------------------------- 継承装備 */

/** 持ち帰った装備に新しいIDを与え、次の冒険でそのまま使える形にする */

export function renewItemId(it: Item): Item {
  return { ...it, id: `it_heirloom_${Math.floor(Math.random() * 1e9).toString(36)}` };


}

export const ELITE_PREFIX = [
  { name: "狂乱の", mods: { speed: 1.45, hp: 0.9, dmg: 1.1 }, color: "#ff5c5c" },
  { name: "重厚な", mods: { speed: 0.72, hp: 2.6, dmg: 1.3 }, color: "#8fa3bf" },
  { name: "灼熱の", mods: { speed: 1.1, hp: 1.3, dmg: 1.35 }, color: "#ff9a3c" },
  { name: "凍てつく", mods: { speed: 0.95, hp: 1.5, dmg: 1.2 }, color: "#5cd8ff" },
  { name: "雷光の", mods: { speed: 1.25, hp: 1.2, dmg: 1.45 }, color: "#ffe14a" },
  { name: "太古の", mods: { speed: 0.9, hp: 3.2, dmg: 1.6 }, color: "#c58bff" },
];

export function xpForLevel(level: number) {
  return Math.round(6 + level * 5 + Math.pow(level, 1.62) * 2.4);
}

/* ------------------------------------------------------------ メタ強化 */

export type MetaUpgrade = {
  key: string;
  label: string;
  desc: string;
  max: number;
  baseCost: number;
  growth: number;
  per: (lv: number) => Mods | { startLevel: number } | { startRarity: number };
  display: (lv: number) => string;
};

export const META_UPGRADES: MetaUpgrade[] = [
  { key: "hp", label: "命の器", desc: "永続的に最大HPが増加", max: 12, baseCost: 60, growth: 1.42, per: (lv) => ({ maxHpPct: lv * 4 }), display: (lv) => `最大HP +${lv * 4}%` },
  { key: "dmg", label: "破壊の紋章", desc: "永続的に攻撃力が増加", max: 12, baseCost: 70, growth: 1.45, per: (lv) => ({ damagePct: lv * 4 }), display: (lv) => `攻撃力 +${lv * 4}%` },
  { key: "armor", label: "鋼の肉体", desc: "被ダメージを恒久的に軽減", max: 8, baseCost: 110, growth: 1.5, per: (lv) => ({ armorPct: lv * 2 }), display: (lv) => `軽減 +${lv * 2}%` },
  { key: "speed", label: "疾風の脚", desc: "移動速度と回避が向上", max: 8, baseCost: 90, growth: 1.45, per: (lv) => ({ moveSpeedPct: lv * 2.5, dashCdPct: lv * 2 }), display: (lv) => `速度 +${(lv * 2.5).toFixed(1)}%` },
  { key: "crit", label: "会心の眼", desc: "クリティカル率の上昇", max: 8, baseCost: 120, growth: 1.5, per: (lv) => ({ critPct: lv * 1.2 }), display: (lv) => `会心 +${(lv * 1.2).toFixed(1)}%` },
  { key: "memory", label: "戦士の記憶", desc: "高い初期レベルで開始する", max: 6, baseCost: 180, growth: 1.75, per: (lv) => ({ startLevel: lv }), display: (lv) => `初期Lv ${1 + lv}` },
  { key: "eye", label: "探索者の眼", desc: "高品質な装備が落ちやすい", max: 10, baseCost: 100, growth: 1.48, per: (lv) => ({ luckFlat: lv * 8 }), display: (lv) => `運 +${lv * 8}` },
  { key: "heirloom", label: "継承の武具", desc: "初期装備の希少度が上昇", max: 4, baseCost: 260, growth: 2.0, per: (lv) => ({ startRarity: lv }), display: (lv) => `初期装備 +${lv}段階` },
  { key: "soul", label: "魂の共鳴", desc: "獲得経験値が恒久的に増加", max: 8, baseCost: 85, growth: 1.44, per: (lv) => ({ xpPct: lv * 5 }), display: (lv) => `経験値 +${lv * 5}%` },
  { key: "vigor", label: "不屈の生命", desc: "HP自動回復を得る", max: 8, baseCost: 130, growth: 1.5, per: (lv) => ({ regenFlat: lv * 0.35 }), display: (lv) => `回復 +${(lv * 0.35).toFixed(2)}/秒` },
];

export function metaMods(meta: Record<string, number> | null | undefined): { mods: Mods; startLevel: number; startRarity: number } {
  const list: Mods[] = [];
  let startLevel = 0;
  let startRarity = 0;
  for (const u of META_UPGRADES) {
    const lv = Number(meta?.[u.key] ?? 0);
    if (lv <= 0) continue;
    const res = u.per(lv) as Mods & { startLevel?: number; startRarity?: number };
    if (res.startLevel) startLevel += res.startLevel;
    if (res.startRarity) startRarity += res.startRarity;
    const copy: Mods = { ...res };
    delete (copy as Record<string, unknown>).startLevel;
    delete (copy as Record<string, unknown>).startRarity;
    list.push(copy);
  }
  return { mods: sumMods(list), startLevel, startRarity };
}

export function metaCost(u: MetaUpgrade, lv: number) {
  return Math.round(u.baseCost * Math.pow(u.growth, lv));
}

export function soulsForRun(floor: number, kills: number, level: number, score: number, victory: boolean) {
  return Math.round(floor * 14 + kills * 0.6 + level * 8 + score / 40 + (victory ? 900 : 0));
}

/* ------------------------------------------------------- パッシブスキル */

/** 層の主を倒すたびに1つ選ぶ、代償付きの恒久パッシブ。ビルドの骨格になる */
export type Passive = {
  id: string;
  name: string;
  desc: string;
  grade: "minor" | "major" | "cursed";
  mods?: Mods;
  /** エンジン挙動フラグ（実装済みのもののみ） */
  flag?: "giant_slayer" | "swarm_bane" | "last_stand" | "momentum" | "undying" | "echo_strike";
  color: string;
};

export const PASSIVES: Passive[] = [
  { id: "iron_oath",  name: "鉄の律儀",   desc: "攻撃力 +25% ／ 最大HP -12%",            grade: "minor", mods: { damagePct: 25, maxHpPct: -12 }, color: "#ff8a2b" },
  { id: "clear_mind", name: "清心",       desc: "被ダメ軽減 +14% ／ 攻撃速度 -10%",       grade: "minor", mods: { armorPct: 14, attackSpeedPct: -10 }, color: "#8fa3bf" },
  { id: "abyss_eye",  name: "深淵の目",   desc: "会心 +12% ／ 運 +40 ／ 最大HP -25",      grade: "minor", mods: { critPct: 12, luckFlat: 40, maxHpFlat: -25 }, color: "#5cc8ff" },
  { id: "glass_cannon",name: "硝子の大砲", desc: "攻撃力 +55% ／ 最大HP -35% ／ 被ダメ +10%", grade: "major", mods: { damagePct: 55, maxHpPct: -35, armorPct: -10 }, color: "#e0243c" },
  { id: "blood_pact", name: "血の盟約",   desc: "生命吸収 +6% ／ 自然回復を喪失",         grade: "minor", mods: { lifestealPct: 6, regenFlat: -100 }, color: "#ff4d6d" },
  { id: "slow_burn",  name: "業火の刻",   desc: "炎上付与 +35% ／ 移動速度 -12%",        grade: "minor", mods: { explodePct: 35, moveSpeedPct: -12 }, color: "#ff9a3c" },
  { id: "twin_soul",  name: "双魂",       desc: "追加弾数 +1 ／ 攻撃力 -18%",            grade: "major", mods: { projectiles: 1, damagePct: -18 }, color: "#c58bff" },
  { id: "giant_slayer",name: "巨人殺し",   desc: "エリート・層主への +45% ／ 雑魚への -15%", grade: "major", flag: "giant_slayer", color: "#ffd23c" },
  { id: "swarm_bane", name: "群れの克星", desc: "同時に3体以上ヒットで +35% ダメージ",    grade: "major", flag: "swarm_bane", color: "#ffe14a" },
  { id: "last_stand", name: "最後の砦",   desc: "HP30%以下で攻撃 +50% ／ 軽減 +25%",      grade: "major", flag: "last_stand", color: "#ff5c6e" },
  { id: "momentum",   name: "勢い",       desc: "コンボ20以上で攻速 +35% ／ 切れると被ダメ +15%", grade: "cursed", flag: "momentum", color: "#e34bff" },
  { id: "greedy",     name: "強欲の契",   desc: "ゴールド +60% ／ 被ダメ +12%",          grade: "minor", mods: { goldPct: 60, armorPct: -12 }, color: "#f5c542" },
  { id: "scholar",    name: "学者の飢え", desc: "経験値 +45% ／ 最大HP -15%",            grade: "minor", mods: { xpPct: 45, maxHpPct: -15 }, color: "#7c8cff" },
  { id: "swift",      name: "疾影",       desc: "移動 +25% ／ 回避短縮 20% ／ 攻撃力 -10%", grade: "minor", mods: { moveSpeedPct: 25, dashCdPct: 20, damagePct: -10 }, color: "#2dd4a7" },
  { id: "thorn_lord", name: "棘の王",     desc: "棘 +80 ／ 最大HP -10%",                 grade: "minor", mods: { thornsFlat: 80, maxHpPct: -10 }, color: "#a0e87c" },
  { id: "no_mercy",   name: "無慈悲",     desc: "攻撃力 +20% ／ ダメージ軽減 -8%",        grade: "minor", mods: { damagePct: 20, armorPct: -8 }, color: "#ff6a7d" },
  { id: "undying",    name: "不滅の誓い", desc: "一度だけ HP50% で復活 ／ 最大HP -20%",   grade: "cursed", flag: "undying", mods: { maxHpPct: -20 }, color: "#ffffff" },
  { id: "echo_strike",name: "残響の型",   desc: "フィニッシャーが再放される ／ 攻速 -15%", grade: "cursed", flag: "echo_strike", mods: { attackSpeedPct: -15 }, color: "#e34bff" },
  { id: "perfection", name: "完全主義",   desc: "見切り後3秒間、攻撃 +90%",              grade: "major", flag: "last_stand", color: "#7cffd0" },
  { id: "frostbound", name: "氷縛の理",   desc: "氷結 +40% ／ 攻撃速度 -12%",            grade: "minor", mods: { freezePct: 40, attackSpeedPct: -12 }, color: "#8be9ff" },
];

export const PASSIVE_GRADE = {
  minor:  { label: "小", color: "#9fb0c9" },
  major:  { label: "大", color: "#4aa8ff" },
  cursed: { label: "呪", color: "#e34bff" },
} as const;

/** 主撃破後のパッシブ3候補を引く。既取得は除外、呪は1つまで */
export function rollPassives(r: RNG, owned: string[], n = 3): Passive[] {
  const cursedCount = owned.filter((id) => PASSIVES.find((p) => p.id === id)?.grade === "cursed").length;
  const pool = PASSIVES.filter((p) => !owned.includes(p.id) && (p.grade !== "cursed" || cursedCount < 1));
  const out: Passive[] = [];
  const used = new Set<string>();
  while (out.length < Math.min(n, pool.length)) {
    const p = weighted(r, pool, (x) => {
      if (used.has(x.id)) return 0;
      return x.grade === "cursed" ? 1.4 : x.grade === "major" ? 2.6 : 4.0;
    });
    if (used.has(p.id)) break;
    used.add(p.id);
    out.push(p);
  }
  return out;
}

export function passiveMods(owned: string[]): Mods {
  const list: Mods[] = [];
  for (const id of owned) {
    const p = PASSIVES.find((x) => x.id === id);
    if (p?.mods) list.push(p.mods);
  }
  return sumMods(list);
}

export function passiveFlags(owned: string[]): Set<string> {
  const s = new Set<string>();
  for (const id of owned) {
    const p = PASSIVES.find((x) => x.id === id);
    if (p?.flag) s.add(p.flag);
  }
  return s;
}

/* ------------------------------------------------------------- ダンジョン帯 */

/** 5層ごとに変わる深層帯。色・危険物・環境ギミックがまるごと替わる */
export type Zone = {
  id: string;
  name: string;
  desc: string;
  color: string;
  hue: number;
  minFloor: number;
  maxFloor: number;
  /** 危険地帯の追加数 */
  hazards: number;
  /** 慣性（大きいほど滑る。氷結の深層） */
  slip?: number;
  /** 敵弾速度倍率 */
  projSpeed?: number;
  /** 火の粉が降り注ぐ */
  emberRain?: boolean;
  /** プレイヤーを引き寄せる重力 */
  voidPull?: number;
  /** 敵の基礎速度倍率 */
  enemySpeed?: number;
};

export const ZONES: Zone[] = [
  { id: "ash",      name: "灰の回廊",     desc: "静かで、まだ死んでいない入口。",           color: "#8fa3bf", hue: 226, minFloor: 1,  maxFloor: 5,  hazards: 0 },
  { id: "ossuary",  name: "骸骨の墓所",   desc: "壁が骨で出来ている。何かが這い回る。",       color: "#dfe6ee", hue: 32,  minFloor: 6,  maxFloor: 10, hazards: 1, enemySpeed: 1.05 },
  { id: "forge",    name: "溶鉄の炉",     desc: "天井から火花が降る。熱で視界が歪む。",       color: "#ff9a3c", hue: 22,  minFloor: 11, maxFloor: 15, hazards: 3, emberRain: true, projSpeed: 1.12 },
  { id: "glacier",  name: "氷結の深層",   desc: "床が氷だ。足が言うことを聞かない。",         color: "#8be9ff", hue: 190, minFloor: 16, maxFloor: 20, hazards: 2, slip: 1.85, enemySpeed: 1.1 },
  { id: "voidheart",name: "虚無の心臓",   desc: "空間が軋み、全てが内側へ引き込まれる。",     color: "#e34bff", hue: 276, minFloor: 21, maxFloor: 25, hazards: 3, projSpeed: 1.28, voidPull: 46, enemySpeed: 1.14 },
  { id: "nihil",    name: "真の深淵",     desc: "ここには何もない。帰る場所も。",            color: "#ffffff", hue: 292, minFloor: 26, maxFloor: 26, hazards: 0 },
];

export function zoneForFloor(floor: number): Zone {
  return ZONES.find((z) => floor >= z.minFloor && floor <= z.maxFloor) ?? ZONES[0];
}

/* ----------------------------------------------------------- ランダムイベント */

/** 層の中盤で突然起こる、一過性の出来事 */
export type RandomEventDef = {
  id: string;
  name: string;
  desc: string;
  color: string;
  /** fate システムに載せる一時効果（任意） */
  fate?: "mist" | "timewarp";
  kind: "loot" | "gamble" | "elite" | "heal" | "fate" | "swarm";
};

export const RANDOM_EVENTS: RandomEventDef[] = [
  { id: "treasure",  name: "宝の間",       desc: "隠し扉が開いた。装備が3点、転がり落ちてくる。", color: "#f5c542", kind: "loot" },
  { id: "gamble",    name: "賭博の祭壇",   desc: "ゴールドの半分を賭ける。当たれば倍、外れれば無。", color: "#ffb03c", kind: "gamble" },
  { id: "elite_ambush", name: "精鋭の襲撃", desc: "冷や汗が止まらない。鍛え抜かれた強敵が3体。",  color: "#ff5c6e", kind: "elite" },
  { id: "spring",    name: "加護の泉",     desc: "冷たい水が傷を癒す。全回復し、素材も得る。",    color: "#2dd4a7", kind: "heal" },
  { id: "curse_mist",name: "呪いの霧",     desc: "20秒間 被ダメ+30%。だが戦利品は2倍。",       color: "#e34bff", kind: "fate", fate: "mist" },
  { id: "timewarp",  name: "時の歪み",     desc: "15秒間 敵が半分の速さ。経験値も2倍。",        color: "#7ce8ff", kind: "fate", fate: "timewarp" },
  { id: "swarm_call",name: "群れの呼び声", desc: "地鳴りと共に大量の敵が押し寄せる。経験値は旨い。", color: "#ffe14a", kind: "swarm" },
];

export function rollRandomEvent(r: RNG): RandomEventDef {
  return pick(r, RANDOM_EVENTS);
}

/* ------------------------------------------------------------- 実行遺物 */

/** 深淵に散る遺物。ラン中に最大2つまで拾える、ゲームを根本から変える神器 */
export type Relic = {
  id: string;
  name: string;
  desc: string;
  color: string;
  icon: string;
  /** エンジンへの挙動フラグ */
  flag: "soulfang" | "mirror" | "coin" | "chalice" | "twin" | "storm" | "greed" | "compass";
};

export const RELICS: Relic[] = [
  { id: "soulfang", name: "魂喰いの爪", desc: "撃破ごとに魂ゲージ +8。必殺技が雪だるま式に早くなる", color: "#c58bff", icon: "☄", flag: "soulfang" },
  { id: "mirror",   name: "呪われた鏡", desc: "死の瞬間に40%の確率でHP50%で蘇る（1ラン1回）",         color: "#5cc8ff", icon: "◈", flag: "mirror" },
  { id: "coin",     name: "盗賊の金貨", desc: "敵が落とす素材の量が2倍になる。鍛造地獄が始まる",     color: "#f5c542", icon: "◉", flag: "coin" },
  { id: "chalice",  name: "呪杯",       desc: "攻撃力 +40%。その代償として常にHPが削れ続ける",       color: "#e0243c", icon: "♆", flag: "chalice" },
  { id: "twin",     name: "双魂の盃",   desc: "10レベル獲得ごとに追加弾数+1・攻撃力+10%が刻まれる", color: "#7cffb2", icon: "∞", flag: "twin" },
  { id: "storm",    name: "嵐召喚者の冠", desc: "見切り成立のたび、空から落雷が3体の敵を焼く",        color: "#ffe14a", icon: "⚡", flag: "storm" },
  { id: "greed",    name: "巨人の欲",   desc: "45秒ごとに所持ゴールドの半分がHPに変わる",           color: "#ff9a3c", icon: "△", flag: "greed" },
  { id: "compass",  name: "深羅針盤",   desc: "地上の最強の装備へ光の道を示し続ける",               color: "#8be9ff", icon: "✦", flag: "compass" },
];

export function rollRelics(r: RNG, owned: string[], n = 3): Relic[] {
  const pool = RELICS.filter((x) => !owned.includes(x.id));
  const out: Relic[] = [];
  const used = new Set<string>();
  while (out.length < Math.min(n, pool.length)) {
    const p = pick(r, pool);
    if (used.has(p.id)) break;
    used.add(p.id);
    out.push(p);
  }
  return out;
}

export function relicFlags(owned: string[]): Set<string> {
  const s = new Set<string>();
  for (const id of owned) {
    const r = RELICS.find((x) => x.id === id);
    if (r) s.add(r.flag);
  }
  return s;
}

/* ------------------------------------------------------------- 難易度 */

export type Difficulty = "normal" | "hard" | "hell" | "inferno" | "omega";

export const DIFFICULTIES: Record<Difficulty, {
  name: string; color: string; desc: string;
  hpMul: number; dmgMul: number; scoreMul: number; soulMul: number;
}> = {
  normal:  { name: "通常",   color: "#2dd4a7", desc: "定石。25層と真の深淵を制覇せよ。",           hpMul: 1,    dmgMul: 1,    scoreMul: 1,   soulMul: 1 },
  hard:    { name: "極難",   color: "#ff9a3c", desc: "敵の強化と攻撃性上昇。スコア・魂×1.5相当。", hpMul: 1.35, dmgMul: 1.25, scoreMul: 1.6, soulMul: 1.5 },
  hell:    { name: "地獄",   color: "#e0243c", desc: "深淵が牙を剥く。生き延びること自体が勲章。", hpMul: 1.8,  dmgMul: 1.55, scoreMul: 2.4, soulMul: 2.1 },
  inferno: { name: "阿鼻叫喚", color: "#ff2a85", desc: "最難度。一切の油断が死を呼ぶ極限の深淵。スコア×3.8、魂×3.2。", hpMul: 2.5,  dmgMul: 2.1,  scoreMul: 3.8, soulMul: 3.2 },
  omega:   { name: "終焉の契約", color: "#ff00ff", desc: "全8試練呪印を強制付与。阿鼻叫喚の倍率×2.46。スコア×5.0、魂×7.56。", hpMul: 3.5,  dmgMul: 2.8,  scoreMul: 5.0, soulMul: 7.56 },
};

/* -------------------------------------------------------- エリート特殊行動 */

export type EliteTrait = {
  id: string;
  name: string;
  color: string;
  desc: string;
};

export const ELITE_TRAITS: EliteTrait[] = [
  { id: "regen",    name: "再生",   color: "#5cff9d", desc: "絶え間なくHPが再生する" },
  { id: "shield",   name: "魔盾",   color: "#5cc8ff", desc: "通常の倍のHPを盾で受け持つ" },
  { id: "splitter", name: "分裂者", color: "#a0e87c", desc: "撃破した瞬間に2体の小敵へ分裂" },
  { id: "burst",    name: "爆砕",   color: "#ff9a3c", desc: "死の間際、周囲を炸裂させる" },
  { id: "warp",     name: "跳躍者", color: "#c58bff", desc: "間合いを詰めるたびにテレポートする" },
  { id: "vampire",  name: "吸魂",   color: "#e34bff", desc: "攻撃が命中するたびHPを吸収する" },
];
