import {
  ABILITIES, AbilityId, ActiveStatus, BOONS, Boon, BOSSES, ClassDef, Derived, ELITE_PREFIX, ENEMIES,
  FINAL_FLOOR, FLOOR_MUTATIONS, FloorMutation, Item, MATERIALS, MaterialId, Materials, Mods, RARITY_META,
  STATUS_EFFECTS, SLOTS, Slot, TRUE_BOSS_PHASES, TRUE_FLOOR, WARDENS, WEAPON_CLASSES, WEAPON_TRAITS,
  classById, clamp, computeDerived, forgeItems, genItem, isWardenFloor, metaMods, mulberry32, nf,
  pick, renewItemId, rnd, rndInt, rollBoons, rollFloorMutation, sumMods, truePhaseFor, weighted,
  xpForLevel, PINNACLE_FLOOR, PINNACLE_PHASES, PinnaclePhase, pinnaclePhaseFor, generatePinnacleLoadout,
  META_UPGRADES,
} from "./core";
import { RUN_MODES, RunConfig, RunMode, standardRun } from "./progression";
import {
  ZONES, PASSIVES, PASSIVE_GRADE, Passive, RANDOM_EVENTS, rollRandomEvent, passiveMods, passiveFlags, rollPassives,
  zoneForFloor, RELICS, Relic, rollRelics, relicFlags, DIFFICULTIES, ELITE_TRAITS,
} from "./core";
import { initAudio, playSfx } from "./audio";
import { trialRules, validateSigils, type SigilId } from "./trials";
import { emptyAttributes, attributeMods, availablePoints, allocatePoint, materialAmount, type Attributes, type AttributeId } from "./attributes";
import { WEAPON_ARTS, planWeaponArt, artChargeGain, guardMultiplier, turnToward, type ArtCommand } from "./weapon-arts";
import { generateTraps, tickTraps, drawTraps, type Trap } from "./traps";
import { drawTacticalBody } from "./tactical-renderer";

export type GameStatus = "idle" | "playing" | "levelup" | "loot" | "event" | "trueabyss" | "shop" | "passive" | "relic" | "forge" | "paused" | "dead" | "win";

export type Snapshot = {
  status: GameStatus;
  hp: number; maxHp: number; level: number; xp: number; xpNeed: number;
  floor: number; kills: number; gold: number; time: number; combo: number;
  energy: number; dashCd: number; dashMax: number; atkCd: number; atkMax: number;
  wave: number; waveTotal: number; left: number;
  bossName: string; bossHp: number; bossMax: number;
  derived: Derived; abilities: Record<string, number>;
  portalOpen: boolean; dps: number;
  equipment: Record<Slot, Item | null>;
  boons: string[];
  autoAim: boolean; isTouch: boolean; killStreak: number;
  gamepadConnected: boolean;
  materials: Materials;
  weaponTraitName: string | null;
  weaponTraitDesc: string | null;
  weaponTraitColor: string | null;
  weaponClassName: string | null;
  stashCount: number;
  stashCap: number;
  /** 真・最終決戦中の段階（0=未挑戦） */
  truePhaseIdx: number;
  truePhaseLabel: string | null;
  /** 今回継承した装備名 */
  heirloomSource: string | null;
  /** この層の帯（ゾーン） */
  zoneName: string | null;
  zoneColor: string | null;
  zoneDesc: string | null;
  /** この層の異変 */
  mutationName: string | null;
  /** 獲得済みパッシブ */
  passives: string[];
  mutationColor: string | null;
  mutationDesc: string | null;
  /** 開店中の出品 */
  shop: { item: Item; price: number }[];
  /** 溜め攻撃の進行度 0〜1（0=未溜め） */
  charge: number;
  /** 反撃窓が開いている */
  counterReady: boolean;
  /** 運命の加護（名前と残秒） */
  fateLabel: string | null;
  fateT: number;
  /** 転移門を開けてからの経過秒（追っ手の目安） */
  portalIdle: number;
  /** 邪眼の呪い残秒 */
  curseT: number;
  /** 神聖の盾の残チャージ */
  bulwark: number;
  /** 獲得済み遺物（名前・色・アイコン） */
  relics: { name: string; color: string; icon: string }[];
  /** 難易度表示 */
  difficultyName: string;
  difficultyColor: string;
  pinnaclePhaseIdx: number;
  pinnaclePhaseLabel: string | null;
  cataclysmCountdown: number | null;
  cataclysmStaggerLeft: number | null;
  totalGearPower: number;
  trialSigils: SigilId[]; trialSoulMultiplier: number;
  attributes: Attributes; attributePoints: number;
  weaponArtName: string; weaponArtDescription: string; weaponArtColor: string; weaponArtGlyph: string; weaponArtGauge: number;
  gatesDestroyed: number; weaponArtUses: number; absoluteDomainCd: number;
  stash: Item[];
  focus: boolean;
  grazeCount: number;
  grazeCombo: number;
  bombStock: number;
  bombCharge: number;
  deathbombT: number;
  spellName: string | null;
  spellAnnounceT: number;
  bulletsReflected: number;
};

export type FloorEvent = {
  floor: number;
  title: string;
  subtitle: string;
  options: { id: "rest" | "forge" | "oath" | "merchant"; title: string; desc: string; color: string }[];
};

export type RunSummary = {
  floor: number; level: number; kills: number; gold: number; score: number;
  durationSec: number; victory: boolean; abandoned: boolean; weaponName: string | null; rarity: string | null;
  build: string[]; artifactsFound: { name: string; slot: string; rarity: string; power: number; floor: number; affixes: string[] }[];
  dps: number; bestItem: Item | null;
  challengeMode: RunMode; challengeId: string; challengeName: string; challengeDay: string | null;
  eventsCompleted: number; bossesKilled: number; bestCombo: number; perfectDodges: number;
  finalWeaponClass: string | null; finalWeaponTrait: string | null;
  materials: Materials;
  /** 真・最終決戦（《ニヒル・ゼロ》）を討伐した */
  trueCleared: boolean;
  /** 終焉の座（最難関決戦・タルタロス・オメガ）を討伐した */
  pinnacleCleared: boolean;
  /** 装備を1つ持ち帰れる（踏破 or 真討伐） */
  canKeepItem: boolean;
  /** 持ち帰り選択の対象となる全装備（装備中＋倉庫） */
  finalEquipment: Item[];
  /** 使用した難易度 */
  difficulty: string;
  /** 獲得した遺物ID */
  relics: string[];
  trialSigils: SigilId[];
  attributes: Attributes;
  gatesDestroyed: number;
  weaponArtUses: number;
  grazeCount: number;
  bulletsReflected: number;
};

type Handlers = {
  onSnapshot: (s: Snapshot) => void;
  onLevelUp: (b: Boon[]) => void;
  onLoot: (it: Item, cur: Item | null) => void;
  onFloorEvent: (event: FloorEvent) => void;
  onPassives: (list: Passive[]) => void;
  onRelics: (list: Relic[]) => void;
  onLootReview: (stash: Item[]) => void;
  onEnd: (s: RunSummary) => void;
  onLog: (t: string, c?: string) => void;
};

type Enemy = {
  id: number; name: string; x: number; y: number; vx: number; vy: number; r: number;
  hp: number; maxHp: number; dmg: number; speed: number; xp: number; ai: string;
  shape: string; color: string; accent: string; boss: boolean; elite: boolean; trueBoss: boolean;
  hitCd: number; slow: number; flash: number; t: number; st: number; stT: number;
  angle: number; fire: number; spawnT: number; kx: number; ky: number; orbCd: number;
  scale: number; dead: boolean; def_fire?: number; def_ps?: number;
  status: ActiveStatus[];
  /** 真ボス専用: 突進方向と制御タイマー */
  chargeA?: number; spiralA?: number;
  /** 層の番人（ミニボス） */
  champ: boolean;
  /** 初期敵定義ID（分裂等の挙動判定用） */
  defId: string;
  /** 分裂後の小スライム（これ以上分裂しない） */
  mini: boolean;
  /** ボスの死に際猛攻を既に発動した */
  desperate?: boolean;
  /** エリートの特殊行動 */
  eliteTrait?: string | null;
  /** 魔盾の吸収HP（-1でなし） */
  shieldHp?: number;
  /** 個別ギミックの汎用タイマー */
  mechT?: number;
  summoned?: boolean; summonParent?: number; summonCd?: number;
  pinnacleBoss?: boolean;
  cataclysmT?: number;
  cataclysmStagger?: number;
};

type HitOptions = { art?: boolean; secondary?: boolean; environmental?: boolean; bypassGuard?: boolean; knockback?: number };

type Proj = {
  x: number; y: number; vx: number; vy: number; r: number; dmg: number; life: number;
  pierce: number; hit: Set<number>; color: string; friendly: boolean; homing: number;
  kind: string; size: number; crit: boolean; curse?: boolean; art?: boolean;
  /** 一度だけグレイズする。再び近づいても同じ弾では稼げない */
  grazed?: boolean;
};

type Part = { x: number; y: number; vx: number; vy: number; life: number; max: number; r: number; color: string; kind: string; drag: number };
type Orb = { x: number; y: number; vx: number; vy: number; kind: string; value: number; t: number; dead?: boolean };
type GItem = { x: number; y: number; item: Item; t: number; dead?: boolean };
type FText = { x: number; y: number; vy: number; life: number; text: string; color: string; size: number };
type Slash = { x: number; y: number; a: number; reach: number; arc: number; life: number; max: number; color: string };
type Ring = { x: number; y: number; r: number; max: number; life: number; color: string; width: number };
type Beam = { x1: number; y1: number; x2: number; y2: number; life: number; color: string };
type Obstacle = { x: number; y: number; r: number };
type Hazard = { x: number; y: number; r: number };
type Timer = { t: number; fn: () => void };

type Destructible = {
  id: number;
  x: number;
  y: number;
  r: number;
  kind: "red_crystal" | "blue_flask" | "shrine_fate";
  hp: number;
  maxHp: number;
  dead: boolean;
  t: number;
};

const ROOM_W = 1840;
const ROOM_H = 1180;

export class Engine {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private h: Handlers;
  private raf = 0;
  private last = 0;
  private acc = 0;
  private snapT = 0;
  private roomCanvas: HTMLCanvasElement | null = null;

  rng = mulberry32((Math.random() * 1e9) | 0);
  fxRng = mulberry32(812821);
  trial = trialRules();
  attributes: Attributes = emptyAttributes();
  weaponArtGauge = 0;
  weaponArtUses = 0;
  absoluteDomainCd = 0;
  gatesDestroyed = 0;
  traps: Trap[] = [];
  masteryCd = 0;
  artDamageDepth = 0;
  reactionDepth = 0;
  private pendingBoons = 0;
  private pendingPassives = 0;
  private pendingRelics = 0;
  private lootReturn: "playing" | "shop" = "playing";
  private forgeReturn: "playing" | "paused" = "playing";
  pendingLootReview = false;
  pendingLootReturn: GameStatus = "playing";
  private strikeDash: { remaining: number; angle: number; damage: number; hit: Set<number> } | null = null;
  private artTelegraphs: { x: number; y: number; radius: number; remaining: number; max: number; color: string }[] = [];
  status: GameStatus = "idle";
  cls: ClassDef;
  mods: Mods = {};
  derived: Derived;
  abilities: Record<string, number> = {};
  boonIds: string[] = [];

  equipment: Record<Slot, Item | null> = { weapon: null, helm: null, armor: null, gloves: null, boots: null, ring: null, amulet: null };
  pendingLoot: Item | null = null;
  boonChoices: Boon[] = [];

  floor = 1; level = 1; xp = 0; hp = 100; kills = 0; gold = 0; time = 0;
  energy = 0; readonly energyMax = 100; dashT = 0; dashCd = 0; iframe = 0;
  atkCd = 0; auraCd = 0; orbitCd = 0; missileCd = 0; novaCd = 6; orbitA = 0;
  combo = 0; comboT = 0; dmgWindow: { at: number; amount: number }[] = []; hitstop = 0;
  startRarityBonus = 0; totalDamage = 0; bestCombo = 0;
  bossesKilled = 0; eventsCompleted = 0; perfectDodges = 0;
  runConfig: RunConfig = standardRun();
  floorEvent: FloorEvent | null = null;
  floorEventResolved = false;
  materials: Materials = {};
  forgeResult: Item | null = null;
  stash: Item[] = [];
  readonly stashCap = 28;
  /** 25層ニヒル撃破後に真の深淵への招待を提示したか */
  trueAbyssOffered = false;
  /** 《ニヒル・ゼロ》を討伐した */
  trueCleared = false;
  /** 《タルタロス・オメガ》を討伐した */
  pinnacleCleared = false;
  /** 今回の冒険で継承した装備名 */
  heirloomSource: string | null = null;
  /** 真ボスの現在フェーズ（描画・AI共有） */
  truePhaseIdx = 0;
  /** 終焉ボスの現在フェーズ（描画・AI共有） */
  pinnaclePhaseIdx = 0;
  /** 弾幕の獄：敵弾を弾き／反射した弾数・反射弾数 */
  bulletsShattered = 0;
  bulletsReflected = 0;
  /** 弾幕ディレクターの回転角・タイマー（弾幕の獄専用） */
  danmakuSpiralA = 0;
  danmakuT = 0;
  spellCycleT = 9;
  focusHeld = false;
  grazeCount = 0;
  grazeCombo = 0;
  grazeComboT = 0;
  bombStock = 1;
  bombCharge = 0;
  deathbombT = 0;
  spellIndex = -1;
  spellAnnounceT = 0;
  /** この層の異変 */
  mutation: FloorMutation | null = null;
  /** この層の番人をまだ出していない */
  wardenPending = false;
  /** この層で急襲を起こしていない */
  ambushed = false;
  /** 商店の出品（null なら開いていない） */
  shop: { item: Item; price: number }[] = [];

  px = ROOM_W / 2; py = ROOM_H / 2; pvx = 0; pvy = 0; pAngle = 0; pFlash = 0;
  enemies: Enemy[] = []; projs: Proj[] = []; eprojs: Proj[] = []; parts: Part[] = [];
  orbs: Orb[] = []; gitems: GItem[] = []; texts: FText[] = []; slashes: Slash[] = [];
  rings: Ring[] = []; beams: Beam[] = []; timers: Timer[] = [];
  obstacles: Obstacle[] = []; hazards: Hazard[] = [];
  destructibles: Destructible[] = [];

  // 運命の祭壇（Fate Buff）
  activeFate: "rage" | "gold" | "haste" | "armor" | "scourge" | "mist" | "timewarp" | null = null;
  activeFateT = 0;
  activeFateLabel = "";

  // 職ごとのシナジー＆コンボフィニッシャー状態
  comboPassiveT = 0;
  comboPassiveActive = false;

  // 溜め攻撃: 攻撃ボタン長押しで溜め、離すと重撃
  chargeT = 0;
  charging = false;
  chargeReleased = false;
  /** 攻撃ボタンが前フレームで押されていたか（離した瞬間の検出用） */
  prevWantAttack = false;

  // 反撃窓: 回避直後 0.6 秒以内の最初の攻撃は「反撃」として威力上昇
  counterWindowT = 0;

  // 転移門の急かし: 開いてから一定秒数で敵が追加湧きする
  portalIdleT = 0;
  portalRushWaves = 0;

  // 残り僅かの敵が狂暴化（単体掃討を単調にしない）
  enrageAnnounced = false;

  // パッシブスキル（層の主を倒すたびに選択）
  passives: string[] = [];
  passiveFlags: Set<string> = new Set();
  passiveChoices: Passive[] | null = null;

  // この層の帯（ゾーン）
  zone = ZONES[0];

  // ランダムイベントの予約
  eventPending = false;
  eventFired = false;

  // 満タン攻撃変換（overflow）用の直前HP
  prevHp = 0;

  // 不滅の誓いの残回数
  undyingLeft = 0;

  // 収穫スタック（鎌）
  harvestStacks = 0;

  // 障壁（立ち止まり判定）
  stillT = 0;

  // 見切り直後の攻撃強化タイマー（完全主義）
  perfectT = 0;

  // オートエイムの発射間隔（弱体化：手動より遅い）
  autoAimNext = 0;
  // 先行入力バッファ: クールダウン中に押された入力を覚え、可能になった瞬間に発動する
  attackBuffer = 0;
  dashBuffer = 0;
  // ゲームパッド
  gamepadMoveX = 0;
  gamepadMoveY = 0;
  gamepadAimActive = false;
  gamepadAttack = false;
  gamepadDash = false;
  gamepadSkill = false;
  gamepadBomb = false;
  gamepadFocus = false;
  gamepadConnected = false;
  gamepadPrevAttack = false;
  gamepadPrevDash = false;
  gamepadPrevSkill = false;
  gamepadPrevBomb = false;

  // 直前の攻撃で同時に何体を巻き込んだか（群れの克星 用）
  lastHitBatch = 0;

  // ---- 実行遺物（リリック）
  relics: string[] = [];
  relicFlags: Set<string> = new Set();
  relicChoices: Relic[] | null = null;
  relicOfferedFloors = new Set<number>();
  pendingRelicOffer = false;
  mirrorUsed = false;
  twinLvCounter = 0;
  greedT = 0;
  chaliceT = 0;

  // ---- ボスギミック
  shockwaves: { x: number; y: number; r: number; max: number; life: number; dmg: number; color: string; hitPlayer: boolean }[] = [];
  homingBeams: { x: number; y: number; angle: number; spin: number; len: number; width: number; life: number; dmg: number; color: string; dmgT: number }[] = [];
  curseT = 0;
  bulwark = 0;
  camX = 0; camY = 0; shake = 0; zoom = 1; w = 960; hgt = 600; dpr = 1;
  wave = 0; waveTotal = 0; toSpawn = 0; spawnT = 0; portal: { x: number; y: number; t: number } | null = null;
  boss: Enemy | null = null;
  keys = new Set<string>();
  mouse = { x: 0, y: 0, down: false, wx: 0, wy: 0 };
  touch = {
    move: false, mx: 0, my: 0, dx: 0, dy: 0, movePid: -1,
    aim: false, aox: 0, aoy: 0, ax: 0, ay: 0, aimPid: -1, aimLen: 0,
  };
  isTouch = false;
  autoAim = false;
  timeScale = 1; slowT = 0;
  attackIdx = 0; lastAttackT = -9; dashHit = new Set<number>(); dodgeDone = false; dashDirX = 1; dashDirY = 0;
  killStreak = 0; lastKillT = -9;
  private eid = 1;
  artifactsFound: RunSummary["artifactsFound"] = [];
  flashFx = 0;
  uiFont = "ui-monospace, monospace";

  constructor(canvas: HTMLCanvasElement, handlers: Handlers) {
    this.canvas = canvas;
    const c = canvas.getContext("2d");
    if (!c) throw new Error("canvas 2d unavailable");
    this.ctx = c;
    this.h = handlers;
    this.cls = classById("samurai");
    this.derived = computeDerived(this.cls, 1, {});
    initAudio();
    this.resize();
    this.bind();
    this.raf = requestAnimationFrame(this.loop);
  }

  /* ------------------------------------------------------------ lifecycle */

  private onKey = (e: KeyboardEvent, down: boolean) => {
    const tag = (e.target as HTMLElement | null)?.tagName;
    if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
    const k = e.key.toLowerCase();
    if (["arrowup", "arrowdown", "arrowleft", "arrowright", " ", "control", "z"].includes(k)) e.preventDefault();
    if (down) {
      if (this.status === "playing") this.keys.add(k);
      if (e.repeat) return;
      if (k === "escape" || k === "p") this.togglePause();
      if (k === "q") this.useSkill();
      if (k === "e") { e.preventDefault(); this.useWeaponArt(); }
      if (k === "v") { e.preventDefault(); this.useAbsoluteDomain(); }
      if (k === "x") { e.preventDefault(); this.useBomb(); }
      if (k === "g") this.tryPickupNearest();
    } else this.keys.delete(k);
  };
  private kd = (e: KeyboardEvent) => this.onKey(e, true);
  private ku = (e: KeyboardEvent) => this.onKey(e, false);
  private mm = (e: PointerEvent) => {
    const r = this.canvas.getBoundingClientRect();
    if (e.pointerType === "touch") { this.handleTouchMove(e); return; }
    this.mouse.x = e.clientX - r.left;
    this.mouse.y = e.clientY - r.top;
  };
  private md = (e: PointerEvent) => {
    if (e.pointerType === "touch") {
      this.isTouch = true;
      this.canvas.setPointerCapture?.(e.pointerId);
      const r = this.canvas.getBoundingClientRect();
      const lx = e.clientX - r.left, ly = e.clientY - r.top;
      const t = this.touch;
      if (lx < r.width * 0.46 && t.movePid < 0) {
        t.move = true; t.movePid = e.pointerId; t.mx = lx; t.my = ly; t.dx = 0; t.dy = 0;
      } else if (t.aimPid < 0) {
        t.aim = true; t.aimPid = e.pointerId; t.aox = lx; t.aoy = ly; t.ax = lx; t.ay = ly; t.aimLen = 0;
      }
      return;
    }
    if (e.button === 2) { this.useSkill(); return; }
    this.mouse.down = true;
  };
  private mu = (e: PointerEvent) => {
    if (e.pointerType === "touch") {
      const t = this.touch;
      if (e.pointerId === t.movePid) { t.move = false; t.movePid = -1; t.dx = 0; t.dy = 0; }
      if (e.pointerId === t.aimPid) { t.aim = false; t.aimPid = -1; t.aimLen = 0; }
      return;
    }
    if (e.button !== 2) this.mouse.down = false;
  };
  private handleTouchMove(e: PointerEvent) {
    const r = this.canvas.getBoundingClientRect();
    const lx = e.clientX - r.left;
    const ly = e.clientY - r.top;
    const t = this.touch;
    if (e.pointerId === t.movePid) {
      const R = 58;
      let dx = lx - t.mx, dy = ly - t.my;
      const l = Math.hypot(dx, dy);
      if (l > R) { t.mx = lx - (dx / l) * R; t.my = ly - (dy / l) * R; dx = (dx / l) * R; dy = (dy / l) * R; }
      t.dx = clamp(dx / R, -1, 1);
      t.dy = clamp(dy / R, -1, 1);
    } else if (e.pointerId === t.aimPid) {
      t.ax = lx; t.ay = ly;
      t.aimLen = Math.hypot(lx - t.aox, ly - t.aoy);
    }
  };

  /** 画面ボタン / キー共用の回避（あらゆる硬直をキャンセルして最優先で発動） */
  dash() {
    if (this.status !== "playing" || this.dashCd > 0) return;
    // 硬直キャンセル: 溜め・攻撃後隙・突進を即座に破棄して無敵回避へ
    this.charging = false;
    this.chargeT = 0;
    this.strikeDash = null;
    this.dashBuffer = 0;

    const d = this.derived;
    let ix = 0, iy = 0;
    if (this.keys.has("a") || this.keys.has("arrowleft")) ix -= 1;
    if (this.keys.has("d") || this.keys.has("arrowright")) ix += 1;
    if (this.keys.has("w") || this.keys.has("arrowup")) iy -= 1;
    if (this.keys.has("s") || this.keys.has("arrowdown")) iy += 1;
    if (this.touch.move) { ix += this.touch.dx; iy += this.touch.dy; }
    if (this.gamepadMoveX || this.gamepadMoveY) { ix += this.gamepadMoveX; iy += this.gamepadMoveY; }
    let a: number;
    if (Math.hypot(ix, iy) > 0.1) a = Math.atan2(iy, ix);
    else a = this.pAngle;
    this.dashDirX = Math.cos(a); this.dashDirY = Math.sin(a);
    // ---- 職業固有ダッシュ技 ----
    this.dashT = 0.16;
    this.dashCd = d.dashCd * (this.cls.id === "ninja" ? 0.68 : 1);
    this.iframe = 0.32;
    playSfx("dash");
    this.dashHit = new Set(); this.dodgeDone = false;
    // 回避後 0.55 秒は反撃窓（見切り成功時は perfectDodge 側で延長）
    this.counterWindowT = 0.55;

    // 斬り込み先（突進方向）を起点に技を発動
    const ox = this.px, oy = this.py;
    const dx = this.dashDirX, dy = this.dashDirY;
    if (this.cls.id === "samurai") {
      // 抜刀 — 突進先で横一線の大斬り（突進先に着地する）
      this.timers.push({
        t: 0.15, fn: () => {
          const a = Math.atan2(dy, dx) + Math.PI / 2;
          this.slashes.push({ x: this.px, y: this.py, a, reach: 150 * d.area, arc: Math.PI, life: 0.24, max: 0.24, color: "#ffd0d6" });
          for (const e of [...this.enemies]) {
            if (e.spawnT > 0) continue;
            const ex = e.x - this.px, ey = e.y - this.py;
            if (Math.hypot(ex, ey) > 150 * d.area + e.r) continue;
            let da = Math.atan2(ey, ex) - a;
            while (da > Math.PI) da -= Math.PI * 2;
            while (da < -Math.PI) da += Math.PI * 2;
            if (Math.abs(da) > Math.PI / 2 + 0.2) continue;
            const roll = this.rollDmg();
            this.hitEnemy(e, roll.value * 1.25, roll.crit, "#ff4d5e", ex, ey, true);
          }
          this.text(this.px, this.py - 54, "抜刀!", "#ff4d5e", 20);
        },
      });
    } else if (this.cls.id === "mage") {
      // 転移爆 — 起点に魔力爆発
      this.timers.push({
        t: 0.14, fn: () => {
          this.explodeAt(ox, oy, 140 * d.area, d.damage * 0.85, "#7c8cff");
          this.text(ox, oy - 40, "転移爆", "#7c8cff", 18);
        },
      });
    } else if (this.cls.id === "ninja") {
      // 影爆 — 起点に影が残り、0.5秒後に炸裂
      this.rings.push({ x: ox, y: oy, r: 6, max: 90, life: 0.4, color: "#3ce8a5", width: 3 });
      this.timers.push({
        t: 0.5, fn: () => {
          this.explodeAt(ox, oy, 150 * d.area, d.damage * 0.95, "#3ce8a5");
        },
      });
      this.text(this.px, this.py - 50, "影爆", "#3ce8a5", 18);
    } else if (this.cls.id === "paladin") {
      // 神聖の盾 — 直近2回の被弾を無効化する盾を獲得
      this.bulwark = Math.min(2, this.bulwark + 2);
      this.text(this.px, this.py - 52, "神聖の盾", "#ffd76a", 20);
      this.rings.push({ x: this.px, y: this.py, r: 8, max: 70, life: 0.5, color: "#ffd76a", width: 5 });
    } else {
      // 銃魔：グレネード — 突進経路に2発を配置
      for (let i = 1; i <= 2; i++) {
        const gx = clamp(ox + dx * 90 * i, 70, ROOM_W - 70);
        const gy = clamp(oy + dy * 90 * i, 70, ROOM_H - 70);
        this.timers.push({
          t: 0.25 + i * 0.2, fn: () => {
            this.explodeAt(gx, gy, 150 * d.area, d.damage * 1.0, "#ff9a3c");
          },
        });
      }
      this.text(this.px, this.py - 50, "グレネード", "#ff9a3c", 18);
    }

    // ボスの斬り込み（st===2）を突っ切れば「パーフェクトカット」
    for (const e of [...this.enemies]) {
      if (e.boss && e.ai === "charge" && e.st === 2 && Math.hypot(e.x - this.px, e.y - this.py) < 190 + e.r) {
        const dmg = d.damage * 8;
        e.hp -= dmg; e.flash = 1; e.st = 0; e.stT = 2.2; e.vx = 0; e.vy = 0;
        this.slowT = 0.7; this.shake = 26; this.hitstop = 0.14;
        this.text(e.x, e.y - e.r - 30, "パーフェクトカット!", "#ffffff", 28);
        this.log("【パーフェクトカット】突っ切った！《深淵騎士》が失神した", "#ffffff");
        this.beams.push({ x1: this.px, y1: this.py, x2: e.x, y2: e.y, life: 0.4, color: "#ffffff" });
        for (let i = 0; i < 40; i++) this.spark(e.x, e.y, "#ffffff", 380);
        playSfx("crit", { pitch: 0.6 });
        if (e.hp <= 0) this.killEnemy(e);
      }
    }
    this.pvx = this.dashDirX * 1180; this.pvy = this.dashDirY * 1180;
    for (let i = 0; i < 14; i++) this.spark(this.px, this.py, this.cls.color, 130);
    if ((this.abilities.quake ?? 0) > 0) {
      const lv = this.abilities.quake;
      this.timers.push({ t: 0.18, fn: () => this.explodeAt(this.px, this.py, 130 + lv * 22, d.damage * (1.1 + lv * 0.35), "#ff9a3c") });
    }
  }

  toggleAutoAim() { this.autoAim = !this.autoAim; this.pushSnap(); }

  private weaponStyle(): keyof typeof WEAPON_ARTS {
    const style = this.equipment.weapon?.weaponClass;
    if (style && Object.hasOwn(WEAPON_ARTS, style)) return style;
    return this.cls.id === "mage" ? "staff" : this.cls.id === "gunner" ? "bow" : this.cls.id === "ninja" ? "dagger" : this.cls.id === "samurai" ? "katana" : "hammer";
  }

  /** Weapon art is deliberately independent of the class Q skill. */
  useWeaponArt(): boolean {
    if (this.status !== "playing" || this.hp <= 0 || this.weaponArtGauge < 100 || this.masteryCd > 0) return false;
    const style = this.weaponStyle();
    const def = WEAPON_ARTS[style];
    const targets = this.enemies.filter((e) => !e.dead && e.hp > 0 && e.spawnT <= 0);
    const plan = planWeaponArt(style, this.px, this.py, this.pAngle, targets);
    this.weaponArtGauge = 0;
    this.weaponArtUses++;
    this.masteryCd = 0.7;
    this.charging = false; this.chargeT = 0;
    this.shake = Math.min(18, this.shake + 9);
    this.text(this.px, this.py - 74, def.name, def.color, 27);
    this.log(`武器奥義 — ${def.name}`, def.color);
    playSfx("crit", { pitch: 0.72 });
    const damage = this.derived.damage;
    const area = Math.min(1.8, this.derived.area);
    for (const command of plan) {
      if (command.delay > 0) {
        if (command.kind === "burst") this.artTelegraphs.push({ x: command.x, y: command.y, radius: command.radius * area, remaining: command.delay, max: command.delay, color: def.color });
        this.timers.push({ t: command.delay, fn: () => this.executeArt(command, style, damage, area) });
      } else this.executeArt(command, style, damage, area);
    }
    this.pushSnap();
    return true;
  }

  useAbsoluteDomain(): boolean {
    if (this.status !== "playing" || this.hp <= 0 || this.weaponArtGauge < 100 || this.absoluteDomainCd > 0 || (this.abilities.absolute_domain ?? 0) < 1) return false;
    this.weaponArtGauge = 0;
    this.absoluteDomainCd = 30;
    this.iframe = 3;
    this.charging = false; this.chargeT = 0;
    this.shake = Math.min(18, this.shake + 9);
    this.text(this.px, this.py - 74, "絶対領域", "#ff00ff", 27);
    this.log(`絶対領域発動 — 3秒間の無敵バリア`, "#ff00ff");
    playSfx("crit", { pitch: 0.72 });
    this.pushSnap();
    return true;
  }

  private executeArt(command: ArtCommand, style: keyof typeof WEAPON_ARTS, damage: number, area: number) {
    if (this.status === "dead" || this.status === "win" || this.hp <= 0) return;
    const color = WEAPON_ARTS[style].color;
    const c = command;
    if (c.kind === "wave" || c.kind === "arrow") {
      const target = c.kind === "arrow" ? this.nearest(this.px, this.py, 1200, new Set()) : null;
      const x = c.kind === "arrow" ? this.px : c.x, y = c.kind === "arrow" ? this.py : c.y;
      const a = target ? Math.atan2(target.y - y, target.x - x) : c.angle;
      this.projs.push({ x: x + Math.cos(a) * 30, y: y + Math.sin(a) * 30, vx: Math.cos(a) * 900, vy: Math.sin(a) * 900,
        r: c.radius * area, dmg: damage * c.multiplier, life: 1.65, pierce: c.kind === "wave" ? 12 : 2,
        hit: new Set(), color, friendly: true, homing: c.kind === "arrow" ? 4.5 : 0, kind: "art_wave", size: 1.8, crit: false, art: true });
      return;
    }
    if (c.kind === "teleport") {
      for (let i = 0; i < 14; i++) this.spark(this.px, this.py, color, 200);
      this.px = clamp(c.x, 80, ROOM_W - 80); this.py = clamp(c.y, 80, ROOM_H - 80);
      this.pvx = 0; this.pvy = 0;
      this.resolveCircle(24);
      this.iframe = Math.max(this.iframe, 0.55);
      for (let i = 0; i < 14; i++) this.spark(this.px, this.py, color, 200);
      return;
    }
    if (c.kind === "dash") {
      this.strikeDash = { remaining: 0.3, angle: c.angle, damage: damage * c.multiplier, hit: new Set() };
      this.iframe = Math.max(this.iframe, 0.48);
      this.dashT = 0;
      this.dashCd = Math.max(this.dashCd, this.derived.dashCd);
      return;
    }
    let x = c.x, y = c.y;
    if (c.kind === "fist") { x = this.px + Math.cos(c.angle) * 80; y = this.py + Math.sin(c.angle) * 80; }
    const radius = c.radius * area;
    this.rings.push({ x, y, r: 8, max: radius, life: 0.5, color, width: 12 });
    for (let i = 0; i < 24; i++) this.spark(x, y, color, 340);
    if (c.kind === "cut") {
      this.slashes.push({ x, y, a: c.angle, reach: radius, arc: Math.PI * 2, life: 0.3, max: 0.3, color });
      this.beams.push({ x1: x - radius, y1: y, x2: x + radius, y2: y, life: 0.4, color });
      this.eprojs = this.eprojs.filter((p) => Math.hypot(p.x - x, p.y - y) > radius);
      this.iframe = Math.max(this.iframe, 0.3);
    }
    if (style === "staff" || style === "tome") this.beams.push({ x1: x - 80, y1: y - 450, x2: x, y2: y, life: 0.3, color });
    const victims = [...this.enemies].filter((e) => !e.dead && e.spawnT <= 0 && Math.hypot(e.x - x, e.y - y) <= radius + e.r);
    let primary = victims.find((e) => e.id === c.targetId);
    if (!primary && c.kind === "hammer") primary = victims.sort((a, b) => Math.hypot(a.x - x, a.y - y) - Math.hypot(b.x - x, b.y - y))[0];
    for (const e of victims) {
      let multiplier = c.kind === "hammer" && primary !== e ? 2.1 : c.multiplier;
      if (style === "scythe" && e.hp / e.maxHp < 0.3) multiplier *= 1.8;
      const dx = e.x - this.px, dy = e.y - this.py;
      this.hitEnemy(e, damage * multiplier, false, color, dx, dy, true, { art: true, secondary: true, bypassGuard: c.kind === "burst" || c.kind === "cut", knockback: style === "axe" ? 1150 : 330 });
      if (e.dead) continue;
      if (style === "hammer") { this.applyStatus(e, "shock"); this.applyStatus(e, "stun"); this.beams.push({ x1: x, y1: y, x2: e.x, y2: e.y, life: 0.3, color }); }
      if (style === "tome") this.applyStatus(e, "vulnerable");
      if (style === "axe" || (style === "gauntlet" && c.index === 5)) this.applyStatus(e, "stun");
    }
  }

  private tickArtDash(dt: number): boolean {
    const art = this.strikeDash;
    if (!art) return false;
    const beforeX = this.px, beforeY = this.py;
    this.pvx = Math.cos(art.angle) * 1350; this.pvy = Math.sin(art.angle) * 1350;
    this.px += this.pvx * dt; this.py += this.pvy * dt;
    this.resolveCircle(24);
    const vx = this.px - beforeX, vy = this.py - beforeY, length2 = vx * vx + vy * vy || 1;
    for (const e of [...this.enemies]) {
      if (e.dead || e.spawnT > 0 || art.hit.has(e.id)) continue;
      const t = clamp(((e.x - beforeX) * vx + (e.y - beforeY) * vy) / length2, 0, 1);
      if (Math.hypot(e.x - (beforeX + vx * t), e.y - (beforeY + vy * t)) <= e.r + 46) {
        art.hit.add(e.id);
        this.hitEnemy(e, art.damage, false, WEAPON_ARTS.spear.color, Math.cos(art.angle), Math.sin(art.angle), true, { art: true, secondary: true, bypassGuard: true, knockback: 850 });
      }
    }
    this.parts.push({ x: this.px, y: this.py, vx: 0, vy: 0, life: 0.35, max: 0.35, r: 24, color: WEAPON_ARTS.spear.color, kind: "ghost", drag: 0 });
    art.remaining -= dt;
    if (art.remaining <= 0) { this.strikeDash = null; this.pvx *= 0.2; this.pvy *= 0.2; }
    return true;
  }

  private perfectDodge() {
    if (this.dodgeDone) return;
    this.dodgeDone = true;
    this.perfectDodges++;
    playSfx("parry");
    this.slowT = 0.5;
    this.dashCd = 0;
    this.counterWindowT = 1.1; // 見切り後は反撃窓が長い
    this.gainEnergy(14 * this.derived.energyMult);
    this.flashFx = Math.min(1, this.flashFx + 0.35);
    this.text(this.px, this.py - 56, "見切り!", "#2dd4a7", 26);
    this.rings.push({ x: this.px, y: this.py, r: 10, max: 150, life: 0.4, color: "#2dd4a7", width: 6 });
    for (let i = 0; i < 18; i++) this.spark(this.px, this.py, "#7cffd0", 240);
    // 見切り成功で周囲に衝撃
    for (const e of [...this.enemies]) {
      if (e.spawnT > 0) continue;
      const dx = e.x - this.px, dy = e.y - this.py;
      if (Math.hypot(dx, dy) < 120 + e.r) { e.slow = 1.2; e.kx += dx * 3; e.ky += dy * 3; }
    }

    // ---- 弾幕の獄：見切りで周囲の敵弾を弾き・反射
    if (this.isDanmakuMode()) {
      let reflected = 0;
      for (const p of this.eprojs) {
        if (p.life > 0 && Math.hypot(p.x - this.px, p.y - this.py) < 150 + p.r) {
          p.life = 0; this.bulletsShattered++;
          for (let k = 0; k < 5; k++) this.spark(p.x, p.y, "#bfe9ff", 180);
          const t = this.nearest(p.x, p.y, 900, new Set());
          const a = t ? Math.atan2(t.y - p.y, t.x - p.x) : this.pAngle;
          this.projs.push({ x: p.x, y: p.y, vx: Math.cos(a) * 540, vy: Math.sin(a) * 540, r: 7, dmg: this.derived.damage * 0.55, life: 1.1, pierce: 1, hit: new Set(), color: "#bfe9ff", friendly: true, homing: 3, kind: "reflected", size: 0.9, crit: false });
          this.bulletsReflected++; reflected++;
        }
      }
      if (reflected > 0) {
        this.gainEnergy(reflected * 2 * this.derived.energyMult);
        this.text(this.px, this.py - 74, `反射 ${reflected}弾!`, "#bfe9ff", 20);
      }
    }

    // ---- クラス固有の見切りカウンターシナジー
    if (this.cls.id === "ninja") {
      this.text(this.px, this.py - 76, "影分身 • 見切り連撃！", "#3ce8a5", 24);
      for (let i = 0; i < 3; i++) {
        this.timers.push({
          t: i * 0.12,
          fn: () => {
            this.slashes.push({ x: this.px, y: this.py, a: rnd(this.rng, 0, 7), reach: 180, arc: Math.PI * 2, life: 0.18, max: 0.18, color: "#3ce8a5" });
            this.rings.push({ x: this.px, y: this.py, r: 10, max: 180, life: 0.3, color: "#3ce8a5", width: 5 });
            for (const e of [...this.enemies]) {
              if (e.spawnT > 0 || e.dead) continue;
              if (Math.hypot(e.x - this.px, e.y - this.py) < 180 + e.r) {
                this.hitEnemy(e, this.derived.damage * 1.35, true, "#3ce8a5", e.x - this.px, e.y - this.py, true);
              }
            }
          },
        });
      }
    } else if (this.cls.id === "paladin") {
      this.text(this.px, this.py - 76, "神聖の裁き！", "#ffd76a", 24);
      const targets = this.enemies.filter((e) => e.spawnT <= 0 && !e.dead && Math.hypot(e.x - this.px, e.y - this.py) < 360).slice(0, 5);
      for (const e of targets) {
        this.beams.push({ x1: this.px, y1: this.py, x2: e.x, y2: e.y, life: 0.28, color: "#ffd76a" });
        this.hitEnemy(e, this.derived.damage * 2.8, false, "#ffd76a", e.x - this.px, e.y - this.py);
        for (let k = 0; k < 6; k++) this.spark(e.x, e.y, "#ffd76a", 160);
      }
    }
  }
  private ctx2 = (e: MouseEvent) => e.preventDefault();
  private rs = () => this.resize();
  private blur = () => {
    this.keys.clear();
    this.mouse.down = false;
    this.touch.move = false;
    this.touch.aim = false;
    this.touch.dx = 0;
    this.touch.dy = 0;
    this.touch.movePid = -1; this.touch.aimPid = -1; this.touch.aimLen = 0;
    this.prevWantAttack = false; this.charging = false; this.chargeT = 0;
    this.focusHeld = false;
  };

  private bind() {
    window.addEventListener("keydown", this.kd);
    window.addEventListener("keyup", this.ku);
    window.addEventListener("resize", this.rs);
    window.addEventListener("blur", this.blur);
    this.canvas.addEventListener("pointermove", this.mm);
    this.canvas.addEventListener("pointerdown", this.md);
    window.addEventListener("pointerup", this.mu);
    window.addEventListener("pointercancel", this.mu);
    this.canvas.addEventListener("contextmenu", this.ctx2);
    this.isTouch = typeof window !== "undefined" && window.matchMedia?.("(pointer: coarse)").matches;
    this.autoAim = this.isTouch;
  }

  destroy() {
    cancelAnimationFrame(this.raf);
    window.removeEventListener("keydown", this.kd);
    window.removeEventListener("keyup", this.ku);
    window.removeEventListener("resize", this.rs);
    window.removeEventListener("blur", this.blur);
    this.canvas.removeEventListener("pointermove", this.mm);
    this.canvas.removeEventListener("pointerdown", this.md);
    window.removeEventListener("pointerup", this.mu);
    window.removeEventListener("pointercancel", this.mu);
    this.canvas.removeEventListener("contextmenu", this.ctx2);
  }

  resize() {
    const rect = this.canvas.getBoundingClientRect();
    this.dpr = Math.min(2, window.devicePixelRatio || 1);
    this.w = Math.max(320, rect.width);
    this.hgt = Math.max(240, rect.height);
    this.canvas.width = Math.round(this.w * this.dpr);
    this.canvas.height = Math.round(this.hgt * this.dpr);
    this.zoom = clamp(Math.min(this.hgt / 640, this.w / 980), 0.56, 1.35);
  }

  /* --------------------------------------------------------------- setup */

  start(cls: ClassDef, meta: Record<string, number> | null, config?: RunConfig, heirloom?: Item | null) {
    const selected = config ?? standardRun();
    this.runConfig = { ...selected, difficulty: selected.difficulty ?? "normal", trialSigils: validateSigils(selected.trialSigils) };
    this.trial = trialRules(this.runConfig.trialSigils);
    this.attributes = emptyAttributes();
    this.weaponArtGauge = 0; this.weaponArtUses = 0; this.gatesDestroyed = 0;
    this.pendingBoons = 0; this.pendingPassives = 0; this.pendingRelics = 0;
    this.strikeDash = null; this.masteryCd = 0; this.artDamageDepth = 0; this.reactionDepth = 0;
    this.dashT = 0; this.atkCd = 0; this.iframe = 0; this.acc = 0;
    this.trueAbyssOffered = false;
    this.trueCleared = false;
    this.truePhaseIdx = 0;
    this.focusHeld = false; this.grazeCount = 0; this.grazeCombo = 0; this.grazeComboT = 0;
    this.bombStock = 1; this.bombCharge = 0; this.deathbombT = 0; this.danmakuT = 0; this.danmakuSpiralA = 0;
    this.bulletsShattered = 0; this.bulletsReflected = 0; this.spellIndex = -1; this.spellAnnounceT = 0; this.spellCycleT = 9;
    const seed = this.runConfig.seed || ((Math.random() * 1e9) | 0);
    this.rng = mulberry32(seed);
    this.fxRng = mulberry32(seed ^ 0x712ccbb);
    this.cls = cls;
    const isPinnacle = this.runConfig.mode === "pinnacle";
    const maxedMeta: Record<string, number> = {};
    if (isPinnacle) {
      for (const u of META_UPGRADES) maxedMeta[u.key] = u.max;
    }
    const m = isPinnacle ? metaMods(maxedMeta) : metaMods(meta);
    this.mods = sumMods([m.mods, this.runConfig.modifier?.playerMods ?? {}]);
    this.startRarityBonus = m.startRarity;
    this.level = isPinnacle ? 40 : (this.runConfig.mode === "bossrush" ? 10 : 1) + m.startLevel;
    this.xp = 0;
    this.abilities = {};
    this.boonIds = [];
    this.artifactsFound = [];
    this.equipment = { weapon: null, helm: null, armor: null, gloves: null, boots: null, ring: null, amulet: null };
    this.bossesKilled = 0; this.eventsCompleted = 0; this.perfectDodges = 0;
    this.totalDamage = 0; this.bestCombo = 0; this.killStreak = 0; this.lastKillT = -9;
    this.attackIdx = 0; this.lastAttackT = -9; this.floorEvent = null; this.floorEventResolved = false;
    this.slowT = 0; this.timeScale = 1;
    this.stash = []; this.materials = {};
    this.destructibles = [];
    this.activeFate = null; this.activeFateT = 0; this.activeFateLabel = "";
    this.comboPassiveT = 0; this.comboPassiveActive = false;
    this.chargeT = 0; this.charging = false; this.chargeReleased = false; this.prevWantAttack = false;
    this.counterWindowT = 0; this.portalIdleT = 0; this.portalRushWaves = 0; this.enrageAnnounced = false;
    this.passives = []; this.passiveFlags = new Set(); this.passiveChoices = null;
    this.eventPending = false; this.eventFired = false;
    this.undyingLeft = 0; this.harvestStacks = 0; this.stillT = 0; this.perfectT = 0;
    this.zone = isPinnacle ? { id: "pinnacle", name: "終焉の座", desc: "総合力5000超の神具を纏い、終焉神を穿て。", color: "#ff2a55", hue: 352, minFloor: PINNACLE_FLOOR, maxFloor: PINNACLE_FLOOR, hazards: 0 } : ZONES[0];
    this.relics = isPinnacle ? ["soulfang", "storm"] : [];
    this.relicFlags = relicFlags(this.relics);
    this.relicChoices = null; this.relicOfferedFloors = new Set();
    this.mirrorUsed = false; this.twinLvCounter = 0; this.greedT = 0; this.chaliceT = 0; this.pendingRelicOffer = false;
    this.shockwaves = []; this.homingBeams = []; this.curseT = 0; this.bulwark = 0;

    if (isPinnacle) {
      this.equipment = generatePinnacleLoadout(this.rng, this.cls, heirloom);
      this.heirloomSource = heirloom ? heirloom.name : "創世神具・天墜の一式 (P5400+)";
    } else {
      // 継承装備（前回踏破時に持ち帰った1点）を先に装備
      if (heirloom) {
        const h = renewItemId(heirloom);
        this.equipment[h.slot] = h;
        this.heirloomSource = h.name;
      } else {
        this.heirloomSource = null;
      }
      // 初期装備
      if (!this.equipment.weapon) {
        this.equipment.weapon = genItem(this.rng, this.runConfig.mode === "bossrush" ? 8 : 1, "weapon", m.mods.luckFlat ?? 0, 40 + m.startRarity * 60);
      }
      if (m.startRarity > 0 && !this.equipment.armor) {
        this.equipment.armor = genItem(this.rng, 1, "armor", 0, m.startRarity * 40);
      }
    }
    this.recompute(true);
    this.hp = this.derived.maxHp;
    this.floor = isPinnacle ? PINNACLE_FLOOR : 1;
    this.kills = 0; this.gold = isPinnacle ? 500 : 0; this.time = 0; this.combo = 0;
    this.energy = isPinnacle ? 100 : 0; this.dashCd = 0; this.dmgWindow = [];
    this.buildFloor();
    this.status = "playing";
    this.last = performance.now();
    if (!this.raf) this.loop(this.last);
    this.pushSnap();
    this.log(isPinnacle ? `${cls.name} — 終焉の座 決戦開始！` : `${cls.name} ${cls.title} — 迷宮 第1層`, isPinnacle ? "#ff2a55" : "#ffd76a");
  }

  recompute(healToMax = false) {
    const items: Mods[] = [this.mods, attributeMods(this.attributes)];
    for (const s of SLOTS) if (this.equipment[s]) items.push(this.equipment[s]!.mods);

    // 運命の祭壇／ランダムイベントの一時的な効果
    const fateMods: Mods = {};
    if (this.activeFate === "rage") fateMods.damagePct = 60;
    else if (this.activeFate === "haste") { fateMods.attackSpeedPct = 40; fateMods.moveSpeedPct = 30; }
    else if (this.activeFate === "armor") fateMods.armorPct = 50;
    else if (this.activeFate === "scourge") fateMods.luckFlat = 100;
    else if (this.activeFate === "mist") { fateMods.armorPct = -30; fateMods.goldPct = 100; fateMods.luckFlat = 60; }
    else if (this.activeFate === "timewarp") { fateMods.xpPct = 100; }

    // パッシブスキル
    items.push(passiveMods(this.passives));

    // 鎌の収穫スタック
    if ((this.equipment.weapon?.weaponClass ?? "") === "scythe" && this.harvestStacks > 0) {
      fateMods.attackSpeedPct = (fateMods.attackSpeedPct ?? 0) + Math.min(10, this.harvestStacks) * 6;
    }
    // 刈り取り（エリート撃破の永続強化）
    if ((this.abilities.harvest ?? 0) > 0) {
      const lv = this.abilities.harvest;
      fateMods.damagePct = (fateMods.damagePct ?? 0) + lv * 4;
      fateMods.maxHpPct = (fateMods.maxHpPct ?? 0) + lv * 3;
    }
    // 過剰修復（満タン分を攻撃へ）
    if ((this.abilities.overflow ?? 0) > 0) {
      const over = clamp(this.hp / Math.max(1, this.derived.maxHp) - 0.85, 0, 0.15);
      fateMods.damagePct = (fateMods.damagePct ?? 0) + over * 400 * this.abilities.overflow;
    }
    // 勢い（コンボ依存）
    if (this.passiveFlags.has("momentum")) {
      if (this.combo >= 20) fateMods.attackSpeedPct = (fateMods.attackSpeedPct ?? 0) + 35;
    }
    // 最後の砦
    if (this.passiveFlags.has("last_stand")) {
      const low = this.hp / Math.max(1, this.derived.maxHp);
      if (low < 0.3) { fateMods.damagePct = (fateMods.damagePct ?? 0) + 50; fateMods.armorPct = (fateMods.armorPct ?? 0) + 25; }
    }
    // 見切り直後の完全主義
    if (this.perfectT > 0) fateMods.damagePct = (fateMods.damagePct ?? 0) + 90;

    this.derived = computeDerived(this.cls, this.level, sumMods([...items, fateMods]));
    if (healToMax) this.hp = this.derived.maxHp;
    else this.hp = Math.min(this.hp, this.derived.maxHp);
  }

  private healPlayer(amount: number): number {
    if (this.hp <= 0) return 0;
    const before = this.hp;
    this.hp = Math.min(this.derived.maxHp, this.hp + Math.max(0, amount) * this.trial.healing);
    return this.hp - before;
  }

  private gainEnergy(amount: number) {
    this.energy = clamp(this.energy + Math.max(0, amount) * this.trial.energy, 0, this.energyMax);
  }

  allocateAttribute(id: AttributeId, amount = 1): boolean {
    if (this.status !== "paused") return false;
    const next = allocatePoint(this.attributes, id, this.level, amount);
    if (!next) return false;
    this.attributes = next;
    this.recompute();
    playSfx("ui");
    this.pushSnap();
    return true;
  }

  openForge(): boolean {
    if (this.status !== "playing" && this.status !== "paused") return false;
    this.forgeReturn = this.status;
    this.blur(); this.status = "forge"; this.pushSnap();
    return true;
  }

  closeForge() {
    if (this.status !== "forge") return;
    this.status = this.forgeReturn; this.last = performance.now(); this.acc = 0; this.pushSnap();
  }

  private buildFloor() {
    const r = this.rng;
    this.enemies = []; this.projs = []; this.eprojs = []; this.parts = []; this.orbs = [];
    this.gitems = []; this.texts = []; this.slashes = []; this.rings = []; this.beams = [];
    this.timers = []; this.obstacles = []; this.hazards = []; this.portal = null; this.boss = null;
    this.shockwaves = []; this.homingBeams = []; this.curseT = 0;
    this.floorEvent = null; this.floorEventResolved = false;
    this.eventFired = false; this.eventPending = false;
    this.strikeDash = null; this.artTelegraphs = [];
    this.px = ROOM_W / 2; this.py = ROOM_H / 2; this.pvx = 0; this.pvy = 0;
    this.camX = this.px; this.camY = this.py;

    // 真・最終決戦の間は遮蔽物も毒沼もない、純粋な決闘場にする
    const isTrue = this.isTrueFloor();
    // この層の帯（ゾーン）を決める
    this.zone = zoneForFloor(this.floor);
    // フロア異変（真・決戦層には発生しない）
    this.mutation = rollFloorMutation(r, this.floor);
    this.wardenPending = isWardenFloor(this.floor);
    this.ambushed = false;
    this.shop = [];
    this.portalIdleT = 0; this.portalRushWaves = 0; this.enrageAnnounced = false;
    this.chargeT = 0; this.charging = false; this.counterWindowT = 0;
    this.spellIndex = -1; this.spellCycleT = 9; this.spellAnnounceT = 0; this.danmakuT = 0; this.grazeCombo = 0; this.grazeComboT = 0;
    if (this.mutation) {
      this.log(`《異変 — ${this.mutation.name}》 ${this.mutation.desc}`, this.mutation.color);
    }
    if (this.floor === this.zone.minFloor) {
      this.log(`《${this.zone.name}》 ${this.zone.desc}`, this.zone.color);
    }
    if (this.wardenPending) {
      this.rings.push({ x: ROOM_W / 2, y: ROOM_H / 2, r: 10, max: 300, life: 1, color: "#f5c542", width: 8 });
      this.log("不気味な気配… この層には《番人》が潜んでいる", "#f5c542");
    }
    const nObs = isTrue ? 0 : rndInt(r, 5, 9);
    for (let i = 0; i < nObs; i++) {
      const x = rnd(r, 150, ROOM_W - 150), y = rnd(r, 150, ROOM_H - 150);
      if (Math.hypot(x - ROOM_W / 2, y - ROOM_H / 2) < 200) continue;
      this.obstacles.push({ x, y, r: rnd(r, 26, 46) });
    }
    if (!isTrue && this.floor >= 3) {
      const nH = rndInt(r, 1, Math.min(4, 1 + Math.floor(this.floor / 4))) + (this.mutation?.hazards ?? 0) + this.zone.hazards;
      for (let i = 0; i < nH; i++) {
        const x = rnd(r, 200, ROOM_W - 200), y = rnd(r, 200, ROOM_H - 200);
        if (Math.hypot(x - ROOM_W / 2, y - ROOM_H / 2) < 220) continue;
        this.hazards.push({ x, y, r: rnd(r, 52, 92) });
      }
    }

    this.destructibles = [];
    if (!isTrue) {
      const nD = rndInt(r, 5, 10);
      for (let i = 0; i < nD; i++) {
        const x = rnd(r, 180, ROOM_W - 180);
        const y = rnd(r, 180, ROOM_H - 180);
        if (Math.hypot(x - ROOM_W / 2, y - ROOM_H / 2) < 200) continue;
        if (this.obstacles.some((o) => Math.hypot(x - o.x, y - o.y) < o.r + 34)) continue;
        if (this.destructibles.some((d) => Math.hypot(x - d.x, y - d.y) < d.r + 34)) continue;

        const kRoll = r();
        const kind = kRoll < 0.42 ? "red_crystal" : kRoll < 0.82 ? "blue_flask" : "shrine_fate";
        const hp = kind === "shrine_fate" ? 1 : 16;
        this.destructibles.push({
          id: this.eid++,
          x,
          y,
          r: kind === "shrine_fate" ? 22 : 15,
          kind,
          hp,
          maxHp: hp,
          dead: false,
          t: rnd(r, 0, 100),
        });
      }
    }
    this.traps = this.isTrueFloor() ? [] : generateTraps(this.rng, this.floor, ROOM_W, ROOM_H, [...this.obstacles, ...this.destructibles, ...this.hazards]);
    this.wave = 0;
    const spawnRate = (this.runConfig.modifier?.spawnRate ?? 1) * (this.mutation?.spawn ?? 1);
    // 主の巡礼: 雑魚はわずかに出るだけ。層主との一騎打ちに集中させる
    const rush = this.runConfig.mode === "bossrush";
    this.waveTotal = this.isBossFloor()
      ? 1
      : rush
        ? Math.min(6, 2 + Math.floor(this.floor / 6))
        : Math.min(52, Math.ceil(Math.round(9 + this.floor * 2.4) * spawnRate));
    this.toSpawn = this.waveTotal;
    this.spawnT = 0.6;
    this.paintRoom();
  }

  isBossFloor() { return this.runConfig.mode === "bossrush" || this.runConfig.mode === "pinnacle" || this.floor % 5 === 0 || this.isTrueFloor() || this.isPinnacleFloor(); }

  isTrueFloor() { return this.floor === TRUE_FLOOR && this.runConfig.mode !== "endless" && this.runConfig.mode !== "bossrush" && this.runConfig.mode !== "pinnacle"; }

  isPinnacleFloor() { return this.floor === PINNACLE_FLOOR || this.runConfig.mode === "pinnacle"; }

  isDanmakuMode() { return this.runConfig.mode === "danmaku"; }

  /** スマホ用FOCUSボタン。PCではCtrl/Zを押している間だけ低速 */
  setFocus(held: boolean) {
    this.focusHeld = held && this.isDanmakuMode();
    this.pushSnap();
  }

  /** 東方風のボム: 画面内の弾を消去し、範囲攻撃・無敵を得る */
  useBomb() {
    if (!this.isDanmakuMode() || this.status !== "playing" || this.bombStock <= 0) return false;
    if (this.deathbombT > 0) {
      this.hp = Math.max(this.hp, this.derived.maxHp * 0.2);
      this.deathbombT = 0;
      this.text(this.px, this.py - 95, "デスボム!", "#ffffff", 30);
      this.log("【デスボム】最後の一瞬で死を打ち消した！", "#ffffff");
    }
    this.bombStock--;
    this.iframe = Math.max(this.iframe, 2.2);
    this.slowT = Math.max(this.slowT, 0.7);
    this.shake = 19; this.flashFx = 1;
    let cleared = 0;
    for (const p of this.eprojs) if (p.life > 0) { p.life = 0; cleared++; }
    this.bulletsShattered += cleared;
    this.homingBeams = [];
    this.shockwaves = [];
    this.rings.push({ x: this.px, y: this.py, r: 12, max: 850, life: 0.85, color: "#bfeaff", width: 22 });
    for (let i = 0; i < 48; i++) this.spark(this.px, this.py, "#bfeaff", 420);
    for (const e of [...this.enemies]) {
      if (e.dead || e.spawnT > 0 || Math.hypot(e.x - this.px, e.y - this.py) > 850) continue;
      this.hitEnemy(e, this.derived.damage * 3.2, false, "#bfeaff", e.x - this.px, e.y - this.py, false, { secondary: true });
    }
    this.text(this.px, this.py - 70, `ボム! 弾消し ${cleared}`, "#ffffff", 27);
    this.log(`【ボム】敵弾 ${cleared} 発を一掃！ 無敵2.2秒`, "#bfeaff");
    playSfx("skill");
    this.pushSnap();
    return true;
  }

  /** 弾幕の獄：周囲の敵弾を弾き、敵の武器に反射する */
  private shatterBullets(x: number, y: number, radius: number, reflect: boolean) {
    let shattered = 0, reflected = 0;
    for (const p of this.eprojs) {
      if (p.life <= 0) continue;
      if (Math.hypot(p.x - x, p.y - y) > radius + p.r) continue;
      p.life = 0;
      shattered++; this.bulletsShattered++;
      for (let k = 0; k < 5; k++) this.spark(p.x, p.y, "#bfe9ff", 180);
      if (reflect) {
        const t = this.nearest(p.x, p.y, 900, new Set());
        const a = t ? Math.atan2(t.y - p.y, t.x - p.x) : this.pAngle;
        this.projs.push({ x: p.x, y: p.y, vx: Math.cos(a) * 540, vy: Math.sin(a) * 540, r: 7, dmg: this.derived.damage * 0.55, life: 1.1, pierce: 1, hit: new Set(), color: "#bfe9ff", friendly: true, homing: 3, kind: "reflected", size: 0.9, crit: false });
        this.bulletsReflected++; reflected++;
      }
    }
    return { shattered, reflected };
  }

  /** 弾幕の獄：敵弾を「反射しても敵に届く」ため、接触ダメージを軽めに */
  private danmakuBulletDmg(base: number) { return this.isDanmakuMode() ? base * 0.5 : base; }

  /** 弾幕の獄：1発の弾幕パターンを発射する（スパイラル／リング／ファン／風車） */
  private fireDanmakuFrom(x: number, y: number, color: string, dmg: number) {
    if (this.eprojs.length >= 300) return;
    const pattern = Math.max(0, this.spellIndex);
    const speed = 200 + Math.min(120, this.floor * 8);
    const aimA = Math.atan2(this.py - y, this.px - x);
    const dmgScaled = this.danmakuBulletDmg(dmg);
    if (pattern === 0) {
      this.danmakuSpiralA += 0.52;
      for (let arm = 0; arm < 3; arm++) {
        const a = this.danmakuSpiralA + arm * (Math.PI * 2 / 3);
        this.eprojs.push({ x, y, vx: Math.cos(a) * speed, vy: Math.sin(a) * speed, r: 8, dmg: dmgScaled, life: 4.5, pierce: 0, hit: new Set(), color, friendly: false, homing: 0, kind: "bolt", size: 1, crit: false });
      }
    } else if (pattern === 1) {
      const n = 12 + Math.min(8, this.floor);
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2 + this.danmakuSpiralA * 0.3;
        this.eprojs.push({ x, y, vx: Math.cos(a) * speed * 0.8, vy: Math.sin(a) * speed * 0.8, r: 8, dmg: dmgScaled, life: 4.5, pierce: 0, hit: new Set(), color, friendly: false, homing: 0, kind: "bolt", size: 1, crit: false });
      }
    } else if (pattern === 2) {
      const n = 7 + Math.min(6, this.floor);
      for (let i = 0; i < n; i++) {
        const a = aimA + (i - (n - 1) / 2) * 0.16;
        this.eprojs.push({ x, y, vx: Math.cos(a) * speed * 1.15, vy: Math.sin(a) * speed * 1.15, r: 8, dmg: dmgScaled, life: 4.5, pierce: 0, hit: new Set(), color, friendly: false, homing: 0, kind: "bolt", size: 1, crit: false });
      }
    } else {
      this.danmakuSpiralA += 0.42;
      for (let arm = 0; arm < 2; arm++) {
        const a = this.danmakuSpiralA + arm * Math.PI;
        for (let seg = 0; seg < 4; seg++) {
          const b = a + seg * 0.28;
          this.eprojs.push({ x, y, vx: Math.cos(b) * speed, vy: Math.sin(b) * speed, r: 8, dmg: dmgScaled, life: 4.5, pierce: 0, hit: new Set(), color, friendly: false, homing: 0, kind: "bolt", size: 1, crit: false });
        }
      }
    }
  }

  /** 弾幕の獄：弾幕ディレクター。最も近い敵が弾幕を放ち続ける */
  tickDanmaku(dt: number) {
    if (!this.isDanmakuMode() || this.status !== "playing") return;
    const spellNames = ["月符・三重螺旋", "星符・輪廻の花", "夜符・狙撃と虚空", "幻符・回転風車"];
    this.spellCycleT -= dt;
    if (this.spellIndex < 0 || this.spellCycleT <= 0) {
      this.spellIndex = (this.spellIndex + 1) % spellNames.length;
      this.spellCycleT = 9;
      this.spellAnnounceT = 1;
      this.danmakuT = 0.35;
      this.log(`【スペルカード】${spellNames[this.spellIndex]} — 軌道を見極めろ`, "#ff8ac2");
      this.rings.push({ x: this.px, y: this.py, r: 20, max: 180, life: 0.65, color: "#ff8ac2", width: 4 });
      playSfx("ui");
    }
    if (this.spellAnnounceT > 0) { this.spellAnnounceT -= dt; return; }
    this.danmakuT -= dt;
    if (this.danmakuT > 0 || this.eprojs.length >= 300) return;
    const emitters = this.enemies.filter((e) => e.spawnT <= 0 && !e.dead && Math.hypot(e.x - this.px, e.y - this.py) < 760);
    if (emitters.length === 0) { this.danmakuT = 0.3; return; }
    emitters.sort((a, b) => Math.hypot(a.x - this.px, a.y - this.py) - Math.hypot(b.x - this.px, b.y - this.py));
    const count = 1 + (this.floor >= 10 ? 1 : 0) + (this.floor >= 20 ? 1 : 0);
    for (let i = 0; i < Math.min(count, emitters.length); i++) {
      const e = emitters[i];
      this.fireDanmakuFrom(e.x, e.y, e.boss ? "#ff2f6d" : e.color, e.dmg * 0.5);
    }
    this.danmakuT = Math.max(0.34, 0.62 - this.floor * 0.012);
  }

  private paintRoom() {
    const c = document.createElement("canvas");
    c.width = ROOM_W; c.height = ROOM_H;
    const g = c.getContext("2d");
    if (!g) return;
    const r = this.fxRng;
    const hue = this.isPinnacleFloor() ? 352 : this.isTrueFloor() ? 292 : this.isBossFloor() ? 348 : (this.floor % 5 === 1 ? 226 : this.floor % 5 === 2 ? 268 : this.floor % 5 === 3 ? 190 : 32);
    g.fillStyle = `hsl(${hue} 26% 6%)`;
    g.fillRect(0, 0, ROOM_W, ROOM_H);
    const T = 92;
    for (let y = 0; y < ROOM_H; y += T) {
      for (let x = 0; x < ROOM_W; x += T) {
        const l = 8 + rnd(r, -2.4, 3.4);
        g.fillStyle = `hsl(${hue + rnd(r, -8, 8)} 24% ${l}%)`;
        g.fillRect(x + 1, y + 1, T - 2, T - 2);
        if (r() < 0.16) {
          g.strokeStyle = `hsla(${hue} 30% ${l + 6}% / .5)`;
          g.lineWidth = 2;
          g.beginPath();
          g.moveTo(x + rnd(r, 6, T - 6), y);
          g.lineTo(x + rnd(r, 6, T - 6), y + T);
          g.stroke();
        }
        if (r() < 0.1) {
          g.fillStyle = `hsla(${hue + 40} 60% 30% / .12)`;
          g.beginPath();
          g.ellipse(x + rnd(r, 10, T - 10), y + rnd(r, 10, T - 10), rnd(r, 8, 26), rnd(r, 6, 20), 0, 0, 7);
          g.fill();
        }
      }
    }
    // 魔法陣
    g.save();
    g.translate(ROOM_W / 2, ROOM_H / 2);
    g.strokeStyle = `hsla(${hue} 90% 62% / .10)`;
    g.lineWidth = 3;
    for (const rad of [190, 232, 300]) { g.beginPath(); g.arc(0, 0, rad, 0, 7); g.stroke(); }
    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * Math.PI * 2;
      g.beginPath(); g.moveTo(Math.cos(a) * 190, Math.sin(a) * 190); g.lineTo(Math.cos(a) * 300, Math.sin(a) * 300); g.stroke();
    }
    g.restore();
    // 壁
    const wall = 46;
    g.fillStyle = `hsl(${hue} 22% 4%)`;
    g.fillRect(0, 0, ROOM_W, wall); g.fillRect(0, ROOM_H - wall, ROOM_W, wall);
    g.fillRect(0, 0, wall, ROOM_H); g.fillRect(ROOM_W - wall, 0, wall, ROOM_H);
    g.strokeStyle = `hsla(${hue} 95% 60% / .5)`; g.lineWidth = 4;
    g.strokeRect(wall, wall, ROOM_W - wall * 2, ROOM_H - wall * 2);
    g.strokeStyle = `hsla(${hue} 95% 70% / .12)`; g.lineWidth = 16;
    g.strokeRect(wall, wall, ROOM_W - wall * 2, ROOM_H - wall * 2);
    this.roomCanvas = c;
  }

  /* ---------------------------------------------------------------- loop */

  togglePause() {
    if (this.status === "playing") { this.blur(); this.status = "paused"; this.acc = 0; this.pushSnap(); }
    else if (this.status === "paused") { this.status = "playing"; this.last = performance.now(); this.acc = 0; this.pushSnap(); }
  }

  /** Presentation happens only at a fixed-step boundary, never in the middle of an AoE/death loop. */
  private drainRewards() {
    if (this.status !== "playing" || this.hp <= 0) return;
    if (this.pendingBoons > 0) {
      this.pendingBoons--;
      if (this.runConfig.mode === "covenant") return;
      this.boonChoices = rollBoons(this.rng, this.abilities, 3);
      this.status = "levelup"; this.blur();
      this.h.onLevelUp(this.boonChoices); this.pushSnap();
    } else if (this.pendingPassives > 0) {
      this.pendingPassives--;
      this.passiveChoices = rollPassives(this.rng, this.passives, 3);
      if (this.passiveChoices.length) {
        this.status = "passive"; this.blur();
        this.h.onPassives(this.passiveChoices); this.pushSnap();
      }
    } else if (this.pendingRelics > 0) {
      this.pendingRelics--;
      if (this.relics.length >= 2) return;
      this.relicChoices = rollRelics(this.rng, this.relics, 3);
      if (this.relicChoices.length) {
        this.status = "relic"; this.blur();
        this.h.onRelics(this.relicChoices); this.pushSnap();
      }
    }
  }

  private loop = (now: number) => {
    this.raf = requestAnimationFrame(this.loop);
    let dt = (now - this.last) / 1000;
    this.last = now;
    if (dt > 0.05) dt = 0.05;
    if (this.status === "playing") {
      if (this.slowT > 0) this.slowT -= dt;
      const target = this.slowT > 0 ? 0.28 : 1;
      this.timeScale += (target - this.timeScale) * Math.min(1, dt * (target < 1 ? 30 : 7));
      this.acc += dt;
      const step = 1 / 120;
      let guard = 0;
      while (this.status === "playing" && this.acc >= step && guard++ < 8) {
        // ヒットストップ分離: プレイヤーは100%等速で入力・移動・回避可能、敵側のみスロー化
        this.update(step * this.timeScale, this.hitstop > 0);
        this.acc -= step;
      }
      if (this.hitstop > 0) this.hitstop -= dt;
    }
    if (this.status !== "playing") this.acc = 0;
    this.render(dt);
    this.snapT += dt;
    if (this.snapT > 0.09) { this.snapT = 0; this.pushSnap(); }
  };

  /** ゲームパッド入力を読み取り、キーボード/マウスと同じ内部状態に正規化する */
  private pollGamepad() {
    const nav = typeof navigator !== "undefined" ? navigator : undefined;
    if (!nav?.getGamepads) return;
    let gp: Gamepad | null = null;
    for (const p of nav.getGamepads()) if (p && p.connected) { gp = p; break; }
    if (!gp) {
      this.gamepadMoveX = 0; this.gamepadMoveY = 0;
      this.gamepadAttack = false; this.gamepadDash = false;
      this.gamepadSkill = false; this.gamepadBomb = false; this.gamepadFocus = false;
      this.gamepadConnected = false;
      return;
    }
    this.gamepadConnected = true;
    // 左スティック移動（放射状デッドゾーン付き）
    const lx = gp.axes[0] ?? 0, ly = gp.axes[1] ?? 0;
    const ll = Math.hypot(lx, ly);
    this.gamepadMoveX = ll > 0.18 ? lx : 0;
    this.gamepadMoveY = ll > 0.18 ? ly : 0;
    // 右スティック照準
    const rx = gp.axes[2] ?? 0, ry = gp.axes[3] ?? 0;
    const rl = Math.hypot(rx, ry);
    if (rl > 0.35) {
      this.pAngle = Math.atan2(ry, rx);
      this.gamepadAimActive = true;
    } else this.gamepadAimActive = false;
    // 十字キー（DPad）移動にも対応
    if (gp.buttons[12]?.pressed) this.gamepadMoveY -= 1;
    if (gp.buttons[13]?.pressed) this.gamepadMoveY += 1;
    if (gp.buttons[14]?.pressed) this.gamepadMoveX -= 1;
    if (gp.buttons[15]?.pressed) this.gamepadMoveX += 1;
    // ボタン（立ち上がりエッジ検出）
    const b = (i: number) => !!gp!.buttons[i]?.pressed;
    this.gamepadAttack = b(2) || b(7) || (gp.buttons[7]?.value ?? 0) > 0.4; // X / RT
    this.gamepadDash = b(1) || b(0); // B / A
    this.gamepadSkill = b(4) || b(6) || b(5); // LB / RB
    this.gamepadBomb = b(3) || b(9); // Y / Start
    this.gamepadFocus = b(10) || b(8); // L3 / Select
    // ダッシュ・必殺・ボムは立ち上がりで一度だけ発火
    if (this.gamepadDash && !this.gamepadPrevDash) this.dashBuffer = 0.2;
    if (this.gamepadSkill && !this.gamepadPrevSkill) this.useSkill();
    if (this.gamepadBomb && !this.gamepadPrevBomb) this.useBomb();
    if (this.gamepadFocus) this.setFocus(true);
    this.gamepadPrevAttack = this.gamepadAttack;
    this.gamepadPrevDash = this.gamepadDash;
    this.gamepadPrevSkill = this.gamepadSkill;
    this.gamepadPrevBomb = this.gamepadBomb;
  }

  private update(dt: number, isHitstop: boolean = false) {
    if (this.status !== "playing") return;
    this.drainRewards();
    if (this.status !== "playing") return;
    const r = this.rng;
    const d = this.derived;
    this.time += dt;
    // 敵・弾幕・ハザード用のデルタタイム: ヒットストップ時は敵側のみスロー化し、プレイヤーの入力・移動・回避は100%等速でノーディレイ処理
    const enemyDt = isHitstop ? dt * 0.12 : dt;
    if (this.deathbombT > 0) {
      this.deathbombT -= dt;
      if (this.deathbombT <= 0) { this.deathbombT = 0; this.hp = 0; this.finish(false); return; }
    }
    if (this.comboT > 0) { this.comboT -= dt; if (this.comboT <= 0) this.combo = 0; }
    if (this.flashFx > 0) this.flashFx -= dt * 3;
    if (this.pFlash > 0) this.pFlash -= dt * 5;
    if (this.iframe > 0) this.iframe -= dt;
    if (this.dashCd > 0) this.dashCd -= dt;
    if (this.atkCd > 0) this.atkCd -= dt;
    this.masteryCd = Math.max(0, this.masteryCd - dt);
    if (this.absoluteDomainCd > 0) this.absoluteDomainCd = Math.max(0, this.absoluteDomainCd - dt);
    for (const t of this.artTelegraphs) t.remaining -= dt;
    this.artTelegraphs = this.artTelegraphs.filter((t) => t.remaining > 0);

    // ---- 運命の祭壇の代償/加護タイマー
    if (this.activeFateT > 0) {
      this.activeFateT -= dt;
      if (this.activeFate === "scourge") {
        // 2 HP/sec scourge damage (bypasses iframe but doesn't stagger)
        this.hp = Math.max(1, this.hp - 2 * dt);
      }
      if (this.activeFateT <= 0) {
        this.log(`運命の効果が切れた: 【${this.activeFateLabel}】`, "#8d86a3");
        this.activeFate = null;
        this.activeFateLabel = "";
        this.recompute(); // 再計算
      }
    }

    // ---- 遺物: 巨人の欲（45秒でゴールド半額→HP）
    if (this.relicFlags.has("greed")) {
      this.greedT += dt;
      if (this.greedT >= 45) {
        this.greedT = 0;
        if (this.gold >= 20) {
          const convert = Math.floor(this.gold / 2);
          this.gold -= convert;
          const heal = convert * 0.6;
          this.healPlayer(heal);
          this.text(this.px, this.py - 48, `巨人の欲 +${Math.round(heal)}HP`, "#ff9a3c", 16);
          this.log(`【巨人の欲】${convert}Gを血肉に転換 (+${Math.round(heal)} HP)`, "#ff9a3c");
        }
      }
    }
    // ---- 遺物: 呪杯（常にHPを削る代償）
    if (this.relicFlags.has("chalice") && this.hp > 1) {
      this.hp = Math.max(1, this.hp - 1.1 * dt);
      this.chaliceT += dt;
      if (this.chaliceT > 1) { this.chaliceT = 0; }
    }
    // ---- 邪眼の呪いのカウントダウン
    if (this.curseT > 0) this.curseT -= dt;

    // ---- クラスコンボシナジーのタイマー
    if (this.comboPassiveT > 0) {
      this.comboPassiveT -= dt;
      if (this.comboPassiveT <= 0) {
        this.comboPassiveActive = false;
      }
    }

    // ---- 弾幕の獄：弾幕ディレクターとグレイズ継続
    this.tickDanmaku(dt);
    if (this.grazeComboT > 0) {
      this.grazeComboT -= dt;
      if (this.grazeComboT <= 0) this.grazeCombo = 0;
    }

    // ---- ゲームパッド（アクションゲームの標準入力に対応）
    this.pollGamepad();

    // ---- input
    let ix = 0, iy = 0;
    if (this.keys.has("a") || this.keys.has("arrowleft")) ix -= 1;
    if (this.keys.has("d") || this.keys.has("arrowright")) ix += 1;
    if (this.keys.has("w") || this.keys.has("arrowup")) iy -= 1;
    if (this.keys.has("s") || this.keys.has("arrowdown")) iy += 1;
    if (this.touch.move) {
      // 放射状デッドゾーン: スティック中心の微妙な揺れで誤動作しない
      const tl = Math.hypot(this.touch.dx, this.touch.dy);
      if (tl > 0.22) {
        const scaled = Math.min(1, (tl - 0.22) / 0.78);
        ix += (this.touch.dx / tl) * scaled;
        iy += (this.touch.dy / tl) * scaled;
      }
    }
    if (this.gamepadMoveX || this.gamepadMoveY) { ix += this.gamepadMoveX; iy += this.gamepadMoveY; }
    const il = Math.hypot(ix, iy);
    if (il > 1) { ix /= il; iy /= il; }

    // aim
    let manualAim = this.gamepadAimActive;
    let autoFire = false;
    if (this.gamepadAimActive) {
      // ゲームパッド照準は最優先
    } else if (this.touch.aim) {
      if (this.touch.aimLen > 14) {
        this.pAngle = Math.atan2(this.touch.ay - this.touch.aoy, this.touch.ax - this.touch.aox);
        manualAim = true;
      } else if (!this.autoAim && il > 0.1) {
        this.pAngle = Math.atan2(iy, ix);
      }
    } else if (!this.autoAim && !this.isTouch) {
      const ax = (this.mouse.x - this.w / 2) / this.zoom;
      const ay = (this.mouse.y - this.hgt / 2) / this.zoom;
      this.mouse.wx = this.camX + ax; this.mouse.wy = this.camY + ay;
      const dx = this.mouse.wx - this.px, dy = this.mouse.wy - this.py;
      if (Math.hypot(dx, dy) > 6) { this.pAngle = Math.atan2(dy, dx); manualAim = true; }
    }
    if (!manualAim && this.autoAim) {
      // オートエイムは「お助け」ではなく明確に弱い。
      // ・索敵半径が狭い ／ 最も近い敵にしか撃てない ／ 反応に遅延がある
      // ・与ダメージ -30%、クリティカル無効、攻速 -20%
      const t = this.nearest(this.px, this.py, 430, new Set());
      if (t) {
        const dist = Math.hypot(t.x - this.px, t.y - this.py);
        const range = this.cls.attack === "melee"
          ? this.cls.base.reach * d.area * 0.85 + t.r + 10
          : 520;
        autoFire = dist < range && this.time > this.autoAimNext;
        if (autoFire) this.autoAimNext = this.time + 0.14;
        // 狙いは少し酔う（誤差を含む）
        this.pAngle = Math.atan2(t.y - this.py, t.x - this.px) + Math.sin(this.time * 3.1) * 0.09;
      } else if (il > 0.1) this.pAngle = Math.atan2(iy, ix);
    } else if (!manualAim && !this.autoAim && il > 0.1 && this.isTouch) {
      this.pAngle = Math.atan2(iy, ix);
    }

    // dash（先行入力バッファ付き: クールダウン直前の入力を覚える）
    if (this.keys.has("shift") || this.keys.has(" ")) {
      if (this.dashCd <= 0 && this.dashT <= 0) this.dash();
      else this.dashBuffer = 0.2;
    }
    if (this.dashBuffer > 0) {
      this.dashBuffer -= dt;
      if (this.dashBuffer <= 0) this.dashBuffer = 0;
      else if (this.dashCd <= 0 && this.dashT <= 0) { this.dash(); this.dashBuffer = 0; }
    }
    if (this.dashT > 0) {
      this.dashT -= dt;
      this.parts.push({ x: this.px, y: this.py, vx: 0, vy: 0, life: 0.3, max: 0.3, r: 15, color: this.cls.color, kind: "ghost", drag: 0 });
      // ダッシュ斬り：すり抜けた敵にダメージ
      const dashDmg = d.damage * (this.cls.attack === "melee" ? 1.1 : 0.6);
      for (const e of [...this.enemies]) {
        if (e.spawnT > 0 || this.dashHit.has(e.id)) continue;
        const dx = e.x - this.px, dy = e.y - this.py;
        if (Math.hypot(dx, dy) < e.r + 30) {
          this.dashHit.add(e.id);
          this.hitEnemy(e, dashDmg, false, this.cls.accent, this.dashDirX, this.dashDirY, true);
          // 接触直前なら見切り成立
          if (e.hitCd <= 0) this.perfectDodge();
        }
      }
      for (const p of this.eprojs) {
        if (p.life > 0 && Math.hypot(p.x - this.px, p.y - this.py) < p.r + 26) {
          p.life = 0;
          this.bulletsShattered++;
          this.spark(p.x, p.y, "#7cffd0", 120);
          if (this.isDanmakuMode()) {
            // 弾幕の獄：ダッシュが弾を反射して敵の武器に変える
            const t = this.nearest(p.x, p.y, 900, new Set());
            const a = t ? Math.atan2(t.y - p.y, t.x - p.x) : this.pAngle;
            this.projs.push({ x: p.x, y: p.y, vx: Math.cos(a) * 520, vy: Math.sin(a) * 520, r: 7, dmg: this.derived.damage * 0.55, life: 1.1, pierce: 1, hit: new Set(), color: "#bfe9ff", friendly: true, homing: 3, kind: "reflected", size: 0.9, crit: false });
            this.bulletsReflected++;
          } else {
            this.perfectDodge();
          }
        }
      }
    } else {
      // Ctrl / Z またはタッチFOCUSで低速移動。溜め中も85%速度で歩行可能（足止め硬直の完全撤廃）
      const focused = this.isDanmakuMode() && (this.focusHeld || this.keys.has("control") || this.keys.has("z") || this.gamepadFocus);
      const sp = d.moveSpeed * (focused ? 0.38 : this.charging ? 0.85 : 1);
      // 氷結の深層では床が滑る。FOCUS中だけ慣性を抑えられる。
      const grip = focused ? 20 : this.zone.slip ? 16 / this.zone.slip : 18;
      this.pvx += (ix * sp - this.pvx) * Math.min(1, dt * grip);
      this.pvy += (iy * sp - this.pvy) * Math.min(1, dt * grip);
      // 立ち止まり検知（障壁 用）
      if (il > 0.12) this.stillT = 0; else this.stillT += dt;
    }
    if (!this.tickArtDash(dt)) { this.px += this.pvx * dt; this.py += this.pvy * dt; }

    // attack / 溜め攻撃（先行入力バッファ0.25秒・入力硬直なし）
    const wantAttack = this.mouse.down || this.keys.has("j") || this.keys.has("k") || this.keys.has("z") || this.gamepadAttack || (this.touch.aim && this.touch.aimLen > 14) || autoFire;
    const manualHold = this.mouse.down || this.keys.has("j") || this.keys.has("k") || this.keys.has("z") || this.gamepadAttack || (this.touch.aim && this.touch.aimLen > 14);
    if (this.counterWindowT > 0) this.counterWindowT -= dt;

    // 先行入力: クールダウン中やダッシュ終わり際に押された入力を0.25秒間ストック
    if (manualHold && !this.prevWantAttack) this.attackBuffer = 0.25;
    if (this.attackBuffer > 0) {
      this.attackBuffer -= dt;
      if (this.attackBuffer < 0) this.attackBuffer = 0;
    }

    if (this.cls.attack === "melee" && manualHold && !autoFire) {
      // 連打は通常攻撃、長押しは溜め（0.32秒でキビキビと溜め移行）
      if (this.atkCd <= 0 && !this.charging && (this.dashT <= 0.05)) {
        const fresh = !this.prevWantAttack || this.attackBuffer > 0;
        if (fresh) {
          this.attack();
          this.chargeT = 0;
          this.attackBuffer = 0;
        } else {
          this.chargeT += dt;
          if (this.chargeT > 0.32) {
            this.charging = true;
            this.chargeT = 0.32;
          }
        }
      } else if (this.charging) {
        this.chargeT = Math.min(1.2, this.chargeT + dt);
        // 溜め中は気を吐くエフェクト
        if (Math.random() < dt * 24) this.spark(this.px + rnd(r, -20, 20), this.py + rnd(r, -20, 20), this.cls.accent, 60);
        if (this.chargeT >= 1.2 && Math.random() < dt * 6) {
          this.text(this.px, this.py - 50, "MAX", this.cls.color, 14);
        }
      }
    } else if (this.charging && !manualHold) {
      // 離した → 重撃解放
      this.releaseCharge();
    } else {
      if (!manualHold) { this.chargeT = 0; this.charging = false; }
      // 先行入力バッファがあればクールダウン終了、またはダッシュ終わり際に最速発動
      if (this.atkCd <= 0 && this.dashT <= 0.05) {
        if (this.attackBuffer > 0 || wantAttack) {
          this.attack();
          this.attackBuffer = 0;
        }
      }
    }
    this.prevWantAttack = manualHold;

    // collide w/ world
    this.resolveCircle(28);

    // hazards
    for (const hz of this.hazards) {
      if (Math.hypot(this.px - hz.x, this.py - hz.y) < hz.r + 14 && this.iframe <= 0) {
        this.damagePlayer(9 * (1 + this.floor * 0.12) * dt * 4, hz.x, hz.y);
      }
    }

    // ---- ゾーン環境ギミック
    if (this.zone.emberRain && this.iframe <= 0) {
      // 溶鉄の炉: 天井から火花が降る。動き続けることで当たりにくい
      if (r() < dt * 0.9) {
        const ex = clamp(this.px + rnd(r, -190, 190), 80, ROOM_W - 80);
        const ey = clamp(this.py + rnd(r, -190, 190), 80, ROOM_H - 80);
        this.timers.push({
          t: 0.55, fn: () => {
            this.rings.push({ x: ex, y: ey, r: 8, max: 62, life: 0.32, color: "#ff9a3c", width: 5 });
            for (let i = 0; i < 6; i++) this.spark(ex, ey, "#ffb03c", 150);
            if (this.status === "playing" && Math.hypot(this.px - ex, this.py - ey) < 66) this.damagePlayer(this.derived.maxHp * 0.07, ex, ey);
          },
        });
      }
    }
    if (this.zone.voidPull) {
      // 虚無の心臓: 空間が内側へ引き込む。敵弾も加速する
      const a = Math.atan2(ROOM_H / 2 - this.py, ROOM_W / 2 - this.px);
      this.pvx += Math.cos(a) * this.zone.voidPull * dt * 6;
      this.pvy += Math.sin(a) * this.zone.voidPull * dt * 6;
    }

    // ---- ランダムイベント（層の中盤で一度だけ）
    if (!this.eventFired && !this.isBossFloor() && this.floor < TRUE_FLOOR && this.toSpawn > 0
      && this.toSpawn <= Math.floor(this.waveTotal * 0.55)) {
      this.eventFired = true;
      if (r() < 0.5) this.triggerRandomEvent();
    }

    // ---- 層の番人（層の半分を進めた頃、静かに目覚める）
    if (this.wardenPending && this.toSpawn <= Math.floor(this.waveTotal * 0.52)) {
      this.wardenPending = false;
      this.spawnWarden();
    }

    // ---- 急襲（層の中盤、静けさを破って湧き出る伏兵）
    if (!this.ambushed && !this.isBossFloor() && this.toSpawn > 0 && this.toSpawn <= Math.floor(this.waveTotal * 0.5) && r() < dt * 0.06) {
      this.ambushed = true;
      const n = rndInt(r, 6, 12);
      for (let i = 0; i < n; i++) this.spawnEnemy(false);
      this.log("！伏兵だ — 壁の向こうから崩れ込んでくる", "#ff5c6e");
      this.text(this.px, this.py - 80, "伏兵!", "#ff5c6e", 26);
      this.shake = 8;
    }

    // ---- spawn
    if (this.toSpawn > 0) {
      this.spawnT -= enemyDt;
      if (this.spawnT <= 0) {
        const alive = this.enemies.length;
        const cap = this.isBossFloor() ? 28 : 24;
        if (alive < cap) {
          const batch = this.isBossFloor() && this.wave === 0 ? 1 : Math.min(this.toSpawn, rndInt(r, 1, Math.min(5, this.toSpawn)));
          for (let i = 0; i < batch; i++) this.spawnEnemy(this.isBossFloor() && this.wave === 0 && i === 0);
          this.toSpawn -= batch;
          this.wave++;
          // 敵が少ないほど次の波が早く来る
          this.spawnT = rnd(r, 0.6, 1.3) * (alive < 4 ? 0.5 : 1);
        } else this.spawnT = 0.3;
      }
    }

    // ---- enemies (ヒットストップ中もプレイヤーは等速、敵のみスロー)
    for (const e of [...this.enemies]) {
      if (this.status !== "playing") break;
      if (e.dead || e.hp <= 0) continue;
      if (e.spawnT > 0) { e.spawnT -= enemyDt; continue; }
      e.t += enemyDt;
      if (e.flash > 0) e.flash -= dt * 6;
      if (e.hitCd > 0) e.hitCd -= dt;
      if (e.orbCd > 0) e.orbCd -= enemyDt;
      // ステータス効果tick
      for (let si = e.status.length - 1; si >= 0; si--) {
        const s = e.status[si];
        s.remaining -= dt;
        const def = STATUS_EFFECTS[s.kind];
        if (def.tickDmg) {
          const tick = def.tickDmg * s.stacks * (1 + this.level * 0.18) * dt;
          this.recordDamage(Math.min(e.hp, tick), true);
          e.hp -= tick;
          if (this.fxRng() < dt * 2) this.text(e.x, e.y - e.r, def.icon, def.color, 12);
        }
        if (def.slow) e.slow = Math.max(e.slow, def.slow * s.stacks * 0.5);
        if (s.remaining <= 0) e.status.splice(si, 1);
      }
      if (e.hp <= 0 && !e.dead) { this.killEnemy(e); continue; }
      if (e.status.some((st) => st.kind === "stun" && st.remaining > 0)) {
        e.vx *= 0.85; e.vy *= 0.85; e.kx *= 0.85; e.ky *= 0.85;
        continue;
      }
      const slowF = (e.slow > 0 ? 0.45 : 1) * (this.activeFate === "timewarp" ? 0.5 : 1);
      if (e.slow > 0) e.slow -= dt;
      const dx = this.px - e.x, dy = this.py - e.y;
      const dist = Math.hypot(dx, dy) || 1;
      const nx = dx / dist, ny = dy / dist;
      const towardPlayer = Math.atan2(dy, dx);
      e.angle = e.ai === "guard" ? turnToward(e.angle, towardPlayer, dt * (e.st === 1 ? 0.45 : 1.25)) : towardPlayer;
      // エリート特殊行動 tick
      if (e.eliteTrait === "regen" && e.hp < e.maxHp) {
        e.hp = Math.min(e.maxHp, e.hp + e.maxHp * 0.03 * dt);
        if (Math.random() < dt * 3) this.spark(e.x + rnd(r, -e.r, e.r), e.y + rnd(r, -e.r, e.r), "#5cff9d", 60);
      }
      if (e.eliteTrait === "warp" && dist < 165 && e.orbCd <= 0) {
        e.orbCd = rnd(r, 3.6, 5);
        for (let i = 0; i < 7; i++) this.spark(e.x, e.y, "#c58bff", 160);
        const wa = rnd(r, 0, Math.PI * 2);
        e.x = clamp(this.px + Math.cos(wa) * rnd(r, 230, 340), 80, ROOM_W - 80);
        e.y = clamp(this.py + Math.sin(wa) * rnd(r, 230, 340), 80, ROOM_H - 80);
        for (let i = 0; i < 7; i++) this.spark(e.x, e.y, "#e6dcff", 180);
      }
      let mvx = 0, mvy = 0;
      let spdMul = 1;
      e.stT -= dt;

      // 戦術敵：向きを固定して隙を作る護衛、後衛の蘇生師、定点の門。
      if (e.ai === "guard") {
        if (e.st === 0) {
          mvx = nx * 0.8; mvy = ny * 0.8;
          e.fire -= dt;
          if (dist < e.r + 110 && e.fire <= 0) {
            e.st = 1; e.stT = 0.65;
            this.slashes.push({ x: e.x, y: e.y, a: e.angle, reach: 130, arc: Math.PI / 2, life: 0.65, max: 0.65, color: "#ff945e" });
          }
        } else if (e.stT <= 0) {
          if (dist < e.r + 116 && Math.cos(towardPlayer - e.angle) > 0.4) this.damagePlayer(e.dmg * 1.15, e.x, e.y);
          e.st = 0; e.fire = 1.7;
        }
      } else if (e.ai === "summoner" || e.ai === "gate") {
        if (e.ai === "summoner") {
          if (dist > 340) { mvx = nx; mvy = ny; }
          else if (dist < 255) { mvx = -nx; mvy = -ny; }
          else { mvx = -ny * 0.45; mvy = nx * 0.45; }
        } else { e.vx = 0; e.vy = 0; e.kx = 0; e.ky = 0; }
        e.summonCd = (e.summonCd ?? 3) - dt;
        if (e.summonCd <= 0 && dist < 800 * this.trial.detection) {
          e.summonCd = e.ai === "gate" ? 5.8 : 7;
          const aliveChildren = this.enemies.filter((child) => !child.dead && child.summonParent === e.id).length;
          if (aliveChildren < (e.ai === "gate" ? 6 : 4) && this.enemies.filter((foe) => !foe.dead).length < 42) {
            const count = Math.min(e.ai === "gate" ? 2 : 1, 6 - aliveChildren);
            for (let i = 0; i < count; i++) {
              const a = rnd(r, 0, Math.PI * 2);
              this.spawnEnemy(false, clamp(e.x + Math.cos(a) * 75, 90, ROOM_W - 90), clamp(e.y + Math.sin(a) * 75, 90, ROOM_H - 90), "skeleton", e);
            }
            this.rings.push({ x: e.x, y: e.y, r: 10, max: 115, life: 0.65, color: e.accent, width: 6 });
            this.text(e.x, e.y - e.r - 36, e.ai === "gate" ? "召喚" : "蘇生", e.accent, 19);
          }
        }
      } else if (e.pinnacleBoss) {
        // ---- 終焉神 《タルタロス・オメガ》: 総合力5000+想定の最難関4段階AI ----
        const ratio = clamp(e.hp / e.maxHp, 0, 1);
        const ph = pinnaclePhaseFor(ratio);
        const pIdx = PINNACLE_PHASES.indexOf(ph);
        spdMul = ph.speed;

        // フェーズ遷移演出
        if (pIdx !== this.pinnaclePhaseIdx) {
          this.pinnaclePhaseIdx = pIdx;
          this.shake = 34; this.flashFx = 1; this.slowT = 0.9;
          this.rings.push({ x: e.x, y: e.y, r: 20, max: 1200, life: 1.2, color: ph.color, width: 22 });
          for (let i = 0; i < 90; i++) this.spark(e.x, e.y, ph.color, 520);
          this.text(this.px, this.py - 96, ph.label, ph.color, 32);
          this.log(`【終焉神 タルタロス・オメガ】第${pIdx + 1}段階 — ${ph.label}`, ph.color);
          this.eprojs = [];
          this.shockwaves = [];
          playSfx("boss");
          if (ph.shieldGuards) {
            for (let i = 0; i < 2; i++) {
              const a = e.angle + (i === 0 ? 1 : -1) * 1.2;
              this.spawnEnemy(false, clamp(e.x + Math.cos(a) * 110, 80, ROOM_W - 80), clamp(e.y + Math.sin(a) * 110, 80, ROOM_H - 80), "iron_guard", e);
            }
            this.text(e.x, e.y - e.r - 30, "鉄壁の親衛隊！", "#7d9eb9", 22);
          }
          if (ph.cataclysm) {
            e.cataclysmT = 5.0;
            e.cataclysmStagger = 32000;
            this.log("⚠ 終焉神が星辰の帰無を詠唱中！ 5秒以内に強撃/奥義で詠唱を中断させろ！", "#ffffff");
            this.text(e.x, e.y - e.r - 40, "崩壊カウントダウン！", "#ffffff", 26);
          }
          e.st = 0; e.stT = 1.0;
        }

        e.spiralA = (e.spiralA ?? 0) + dt * 3.4;

        // 第4段階：崩壊カウントダウン詠唱
        if (e.cataclysmT !== undefined && e.cataclysmT > 0) {
          e.cataclysmT -= dt;
          e.vx *= 0.85; e.vy *= 0.85;
          if (Math.random() < dt * 26) this.spark(e.x + rnd(r, -e.r, e.r), e.y + rnd(r, -e.r, e.r), "#ffffff", 280);
          if (e.cataclysmT <= 0) {
            e.cataclysmT = 6.0;
            e.cataclysmStagger = 32000;
            this.flashFx = 1; this.shake = 38;
            this.rings.push({ x: e.x, y: e.y, r: 10, max: 1400, life: 1, color: "#ffffff", width: 28 });
            this.log("天地無帰が炸裂した！ 凄まじい衝撃波が駆け抜ける", "#ff2a55");
            this.damagePlayer(1800, e.x, e.y);
          }
        } else {
          if (e.st === 0) {
            const want = 280;
            if (dist > want + 50) { mvx = nx; mvy = ny; }
            else if (dist < want - 80) { mvx = -nx; mvy = -ny; }
            else { mvx = -ny * 0.85; mvy = nx * 0.85; }
          }
        }

        // 次元の刃（テレポート強襲）
        if (ph.dimensionalSlash && e.st === 0 && e.stT <= 0 && dist > 180) {
          e.st = 1; e.stT = 0.5;
          e.chargeA = e.angle;
          for (let i = 0; i < 12; i++) this.spark(e.x, e.y, "#ff2a55", 260);
          this.rings.push({ x: e.x, y: e.y, r: 10, max: 180, life: 0.4, color: "#ff2a55", width: 8 });
        }
        if (e.st === 1 && e.stT <= 0) {
          const behindA = this.pAngle + Math.PI;
          e.x = clamp(this.px + Math.cos(behindA) * 110, 90, ROOM_W - 90);
          e.y = clamp(this.py + Math.sin(behindA) * 110, 90, ROOM_H - 90);
          e.st = 2; e.stT = 0.4;
          this.slashes.push({ x: e.x, y: e.y, a: Math.atan2(this.py - e.y, this.px - e.x), reach: 240, arc: Math.PI * 1.5, life: 0.35, max: 0.35, color: "#ff2a55" });
          for (let i = 0; i < 18; i++) this.spark(e.x, e.y, "#ff2a55", 340);
          playSfx("crit", { pitch: 0.65 });
        }
        if (e.st === 2 && e.stT <= 0) {
          if (Math.hypot(this.px - e.x, this.py - e.y) < 230 && this.iframe <= 0) {
            this.damagePlayer(e.dmg * 1.25, e.x, e.y);
          }
          e.st = 0; e.stT = 3.2;
        }

        // 追跡クロスビーム
        e.mechT = (e.mechT ?? 0) - dt;
        if (ph.crossBeams && e.mechT <= 0) {
          e.mechT = 5.2;
          for (let i = 0; i < 4; i++) {
            const ba = (e.spiralA ?? 0) + (i * Math.PI) / 2;
            this.homingBeams.push({ x: e.x, y: e.y, angle: ba, spin: 0.48, len: 820, width: 28, life: 5.0, dmg: e.dmg * 1.15, color: ph.color, dmgT: 0 });
          }
          this.rings.push({ x: e.x, y: e.y, r: 20, max: 300, life: 0.6, color: ph.color, width: 8 });
        }

        // 弾幕
        e.fire -= dt;
        if (e.fire <= 0 && e.st === 0 && (!e.cataclysmT || e.cataclysmT <= 0)) {
          e.fire = ph.fireRate;
          const ps = ph.projSpeed;
          const n = ph.bullets;
          for (let i = 0; i < n; i++) {
            const a = (i / n) * Math.PI * 2 + (e.spiralA ?? 0);
            this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * ps, vy: Math.sin(a) * ps, r: 13, dmg: e.dmg * 0.58, life: 4.2, pierce: 0, hit: new Set(), color: ph.color, friendly: false, homing: 0, kind: "bolt", size: 1.45, crit: false });
          }
          const tFly = dist / (ps * 1.2);
          const tx = this.px + this.pvx * tFly * 0.4;
          const ty = this.py + this.pvy * tFly * 0.4;
          const leadA = Math.atan2(ty - e.y, tx - e.x);
          for (let k = -1; k <= 1; k++) {
            this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(leadA + k * 0.15) * ps * 1.2, vy: Math.sin(leadA + k * 0.15) * ps * 1.2, r: 15, dmg: e.dmg * 0.75, life: 3.8, pierce: 0, hit: new Set(), color: "#ffd700", friendly: false, homing: 0, kind: "bolt", size: 1.6, crit: false });
          }
        }
      } else if (e.trueBoss) {
        const ratio = clamp(e.hp / e.maxHp, 0, 1);
        const ph = truePhaseFor(ratio);
        const pIdx = TRUE_BOSS_PHASES.indexOf(ph);
        spdMul = ph.speed;

        // フェーズ遷移演出
        if (pIdx !== this.truePhaseIdx) {
          this.truePhaseIdx = pIdx;
          this.shake = 28; this.flashFx = 1; this.slowT = 0.75;
          this.rings.push({ x: e.x, y: e.y, r: 20, max: 980, life: 1, color: ph.color, width: 18 });
          for (let i = 0; i < 70; i++) this.spark(e.x, e.y, ph.color, 460);
          this.text(this.px, this.py - 92, ph.label, ph.color, 30);
          this.log(`《ニヒル・ゼロ》第${pIdx + 1}段階 — ${ph.label}`, ph.color);
          this.eprojs = [];
          e.st = 0; e.stT = 1.3;
        }

        e.spiralA = (e.spiralA ?? 0) + dt * 2.6;

        // 移動: 中距離を維持しつつ周回
        if (e.st === 0) {
          const want = 310;
          if (dist > want + 60) { mvx = nx; mvy = ny; }
          else if (dist < want - 90) { mvx = -nx; mvy = -ny; }
          else { mvx = -ny * 0.62; mvy = nx * 0.62; }
        }

        // 突進
        if (ph.charge && e.st === 0 && e.stT <= 0 && dist > 220) {
          e.st = 1; e.stT = 0.6; e.chargeA = e.angle;
          this.rings.push({ x: e.x, y: e.y, r: 10, max: 200, life: 0.5, color: ph.color, width: 8 });
        }
        if (e.st === 1 && e.stT <= 0) {
          e.st = 2; e.stT = 0.5;
          const ca = e.chargeA ?? e.angle;
          e.vx = Math.cos(ca) * 1620; e.vy = Math.sin(ca) * 1620;
          for (let i = 0; i < 18; i++) this.spark(e.x, e.y, ph.color, 320);
        }
        if (e.st === 2 && e.stT <= 0) { e.st = 0; e.stT = 1.7; }

        // 弾幕: 螺旋 + 全方位
        e.fire -= dt;
        if (e.fire <= 0 && e.st === 0) {
          e.fire = ph.fireRate;
          const ps = ph.projSpeed;
          if (ph.spiral) {
            const arms = Math.max(2, Math.ceil(ph.bullets / 2));
            for (let i = 0; i < arms; i++) {
              const a = (e.spiralA ?? 0) + (i * Math.PI * 2) / arms;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * ps, vy: Math.sin(a) * ps, r: 12, dmg: e.dmg * 0.62, life: 4.2, pierce: 0, hit: new Set(), color: ph.color, friendly: false, homing: 0, kind: "bolt", size: 1.4, crit: false });
            }
          }
          if (ph.radial) {
            const n = ph.bullets;
            for (let i = 0; i < n; i++) {
              const a = (i / n) * Math.PI * 2 + (e.spiralA ?? 0) * 0.32;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * ps * 0.82, vy: Math.sin(a) * ps * 0.82, r: 11, dmg: e.dmg * 0.55, life: 4.6, pierce: 0, hit: new Set(), color: e.accent, friendly: false, homing: 0, kind: "bolt", size: 1.25, crit: false });
            }
            this.rings.push({ x: e.x, y: e.y, r: 24, max: 240, life: 0.4, color: e.accent, width: 5 });
          }
        }

        // 従者召喚
        if (ph.summon && e.orbCd <= 0 && this.enemies.length < 18) {
          e.orbCd = 11;
          for (let i = 0; i < ph.summon; i++) this.spawnEnemy(false, e.x + rnd(r, -140, 140), e.y + rnd(r, -140, 140));
          this.text(e.x, e.y - e.r - 26, "虚無の従者", "#e34bff", 20);
        }
      } else if (e.ai === "shoot") {
        const want = e.boss ? 330 : 290;
        if (dist > want + 40) { mvx = nx; mvy = ny; } else if (dist < want - 60) { mvx = -nx; mvy = -ny; }
        else { mvx = -ny * 0.7; mvy = nx * 0.7; }
        const isInferno = this.runConfig.difficulty === "inferno";
        e.fire -= dt * (isInferno ? 1.3 : 1);
        if (e.fire <= 0 && dist < (e.boss ? 1100 : 580 * this.trial.detection)) {
          e.fire = (e.def_fire ?? 1.5) / ((1 + this.floor * 0.012) * (isInferno ? 1.25 : 1));
          const ps = (e.def_ps ?? 300) * (isInferno ? 1.25 : 1);
          const n = e.boss ? 5 : 1;
          // 8層以降は偏差射撃: プレイヤーの移動先を予測して撃つ（単純な横歩きでは避けられない）
          let aimA = e.angle;
          if (this.floor >= 8 && !e.boss) {
            const lead = clamp((this.floor - 8) * 0.06, 0, 0.55);
            const tFly = dist / ps;
            const tx = this.px + this.pvx * tFly * lead;
            const ty = this.py + this.pvy * tFly * lead;
            aimA = Math.atan2(ty - e.y, tx - e.x);
          }
          for (let i = 0; i < n; i++) {
            const a = aimA + (i - (n - 1) / 2) * 0.22;
            this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * ps, vy: Math.sin(a) * ps, r: e.boss ? 13 : 8, dmg: e.dmg * 0.7, life: 3.4, pierce: 0, hit: new Set(), color: e.accent, friendly: false, homing: 0, kind: "bolt", size: e.boss ? 1.5 : 1, crit: false });
          }
        }
      } else if (e.ai === "teleport") {
        // 幻影 — 攻撃射程に入るたびテレポートで間合いを揺さぶる
        mvx = nx; mvy = ny;
        if (dist > 130 && e.orbCd <= 0) {
          for (let i = 0; i < 8; i++) this.spark(e.x, e.y, "#a070ff", 200);
          const a = rnd(r, 0, Math.PI * 2);
          const d2 = rnd(r, 150, 260);
          const nx2 = clamp(this.px + Math.cos(a) * d2, 90, ROOM_W - 90);
          const ny2 = clamp(this.py + Math.sin(a) * d2, 90, ROOM_H - 90);
          e.x = nx2; e.y = ny2;
          for (let i = 0; i < 8; i++) this.spark(e.x, e.y, "#e8dcff", 240);
          e.orbCd = rnd(r, 2.6, 3.6);
          e.flash = 0.4;
        }
      } else if (e.ai === "split") {
        // 分裂母 — 撃破した者は小っちゃく湧き返るのを知る
        mvx = nx * 0.85; mvy = ny * 0.85;
      } else if (e.ai === "orbit") {
        const want = 150;
        const tang = dist > want ? 1 : -0.6;
        mvx = nx * tang * 0.6 - ny * 0.9; mvy = ny * tang * 0.6 + nx * 0.9;
      } else if (e.ai === "charge") {
        if (e.st === 0) { mvx = nx * 0.5; mvy = ny * 0.5; if (dist < 420 && e.stT <= 0) { e.st = 1; e.stT = 0.55; } }
        else if (e.st === 1) { mvx = 0; mvy = 0; if (e.stT <= 0) { e.st = 2; e.stT = 0.5; e.vx = nx * e.speed * 4.2; e.vy = ny * e.speed * 4.2; } }
        else { mvx = 0; mvy = 0; if (e.stT <= 0) { e.st = 0; e.stT = rnd(r, 1.2, 2.4); } }
      } else if (e.ai === "bomber") {
        mvx = nx; mvy = ny;
        if (dist < e.r + 34) { this.explodeAt(e.x, e.y, 118, e.dmg * 1.5, "#ffb03c", false); this.killEnemy(e); }
      } else {
        mvx = nx; mvy = ny;
      }

      if (e.boss && !e.trueBoss) {
        // 死に際の猛攻: HP30%以下で弾幕間隔が短縮、速度上昇、弾が二重輪になる
        const desperate = e.hp < e.maxHp * 0.3;
        if (desperate && !e.desperate) {
          e.desperate = true;
          e.speed *= 1.3;
          this.log(`${e.name} が死に物狂いで吼えた — 弾幕が苛烈になる`, "#ff5c6e");
          this.text(e.x, e.y - e.r - 30, "猛攻!", "#ff5c6e", 26);
          this.rings.push({ x: e.x, y: e.y, r: 20, max: 400, life: 0.8, color: "#ff5c6e", width: 12 });
          this.shake = 16;
          playSfx("boss");
        }
        const rate = desperate ? 0.7 : 0.4;
        const aimA = Math.atan2(this.py - e.y, this.px - e.x);
        e.mechT = (e.mechT ?? 0) - dt;

        if (e.defId === "b_goblin") {
          // ゴブリン王：全方位弾幕 ＋ 2.8秒ごとに地を踏む衝撃波
          e.fire -= dt * rate;
          if (e.fire <= 0) {
            e.fire = 3.2;
            const n = (14 + Math.min(14, this.floor)) * (desperate ? 2 : 1);
            for (let i = 0; i < n; i++) {
              const a = (i / n) * Math.PI * 2 + e.t;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * 240, vy: Math.sin(a) * 240, r: 11, dmg: e.dmg * 0.6, life: 4, pierce: 0, hit: new Set(), color: e.accent, friendly: false, homing: 0, kind: "bolt", size: 1.3, crit: false });
            }
          }
          if (e.mechT <= 0) {
            e.mechT = 2.8;
            this.shockwaves.push({ x: e.x, y: e.y, r: e.r, max: 420, life: 1, dmg: e.dmg * 0.9, color: "#8fd44f", hitPlayer: false });
          }
        } else if (e.defId === "b_wyrm") {
          // 屍竜：プレイヤーを狙った扇状の吐息（5本一斉）＋ 周期の地揺れ
          e.fire -= dt * rate;
          if (e.fire <= 0) {
            e.fire = 2.6;
            const n = desperate ? 15 : 11;
            for (let i = 0; i < n; i++) {
              const a = aimA + (i - (n - 1) / 2) * 0.17;
              const sp = 300 + Math.floor(i % 3) * 40;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, r: 13, dmg: e.dmg * 0.55, life: 3.6, pierce: 0, hit: new Set(), color: e.accent, friendly: false, homing: 0, kind: "bolt", size: 1.4, crit: false });
            }
            this.text(e.x, e.y - e.r - 24, "吐息", e.accent, 18);
          }
          if (e.mechT <= 0) {
            e.mechT = 4.2;
            this.shockwaves.push({ x: e.x, y: e.y, r: e.r, max: 460, life: 1, dmg: e.dmg, color: e.color, hitPlayer: false });
          }
        } else if (e.defId === "b_knight") {
          // 深淵騎士：十字弾幕（プレイヤーを照準に十字を向ける）＋ 斬り込みはパーフェクトカット可能
          e.fire -= dt * rate;
          if (e.fire <= 0) {
            e.fire = 2.9;
            const n = desperate ? 16 : 12;
            for (let i = 0; i < n; i++) {
              // 縦横2方向の十字
              const a = aimA + (i % 2 === 0 ? 0 : Math.PI / 2) + (Math.floor(i / 2) - n / 4) * 0.28;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * 260, vy: Math.sin(a) * 260, r: 11, dmg: e.dmg * 0.5, life: 3.8, pierce: 0, hit: new Set(), color: e.accent, friendly: false, homing: 0, kind: "bolt", size: 1.3, crit: false });
            }
          }
          if (e.mechT <= 0) {
            e.mechT = 3.6;
            this.shockwaves.push({ x: e.x, y: e.y, r: e.r, max: 400, life: 1, dmg: e.dmg * 0.85, color: e.color, hitPlayer: false });
          }
        } else if (e.defId === "b_eye") {
          // 万象の邪眼：追跡ビーム（2本・ゆっくり振り向き）＋ 周期の呪いの新星
          if (e.mechT <= 0) {
            e.mechT = 6.5;
            this.homingBeams.push({ x: e.x, y: e.y, angle: e.angle, spin: 0.5, len: 640, width: 26, life: 5, dmg: e.dmg * 1.2, color: "#ff5cc8", dmgT: 0 });
            this.homingBeams.push({ x: e.x, y: e.y, angle: e.angle + Math.PI, spin: -0.5, len: 640, width: 26, life: 5, dmg: e.dmg * 1.2, color: "#ff5cc8", dmgT: 0 });
            // 呪いの新星 — 命中すると被ダメ+25%の呪い
            const n = 12;
            for (let i = 0; i < n; i++) {
              const a = (i / n) * Math.PI * 2;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * 200, vy: Math.sin(a) * 200, r: 14, dmg: e.dmg * 0.4, life: 3.4, pierce: 0, hit: new Set(), color: "#ff5cc8", friendly: false, homing: 0, kind: "bolt", size: 1.4, crit: false, curse: true });
            }
          }
          e.fire -= dt * rate;
          if (e.fire <= 0) {
            e.fire = 2.2;
            for (let i = 0; i < 7; i++) {
              const a = aimA + (i - 3) * 0.2;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * 320, vy: Math.sin(a) * 320, r: 11, dmg: e.dmg * 0.5, life: 3.2, pierce: 0, hit: new Set(), color: e.accent, friendly: false, homing: 0, kind: "bolt", size: 1.2, crit: false });
            }
          }
        } else {
          // 残りのボス（虚無神ニヒル等）：既存の全方位弾幕 ＋ 追跡ビーム
          e.fire -= dt * rate;
          if (e.fire <= 0) {
            e.fire = 3.2;
            const n = (14 + Math.min(14, this.floor)) * (desperate ? 2 : 1);
            for (let i = 0; i < n; i++) {
              const a = (i / n) * Math.PI * 2 + e.t;
              this.eprojs.push({ x: e.x, y: e.y, vx: Math.cos(a) * 240, vy: Math.sin(a) * 240, r: 11, dmg: e.dmg * 0.6, life: 4, pierce: 0, hit: new Set(), color: e.accent, friendly: false, homing: 0, kind: "bolt", size: 1.3, crit: false });
            }
            this.rings.push({ x: e.x, y: e.y, r: 20, max: 260, life: 0.6, color: e.accent, width: 6 });
          }
          if (e.mechT <= 0) {
            e.mechT = 7;
            this.homingBeams.push({ x: e.x, y: e.y, angle: e.angle, spin: 0.4, len: 600, width: 22, life: 5, dmg: e.dmg, color: e.color, dmgT: 0 });
          }
        }
        if (e.hp < e.maxHp * 0.5 && e.orbCd <= 0 && this.enemies.length < 22) {
          e.orbCd = 9;
          for (let i = 0; i < 3; i++) this.spawnEnemy(false, e.x + rnd(r, -80, 80), e.y + rnd(r, -80, 80));
          this.text(e.x, e.y - e.r - 20, "召喚!", "#ff8ac2", 20);
        }
      }

      if (!e.boss && dist > 650 * this.trial.detection && e.hp >= e.maxHp) spdMul *= 0.55;
      if (e.st !== 2) {
        e.vx += (mvx * e.speed * spdMul * slowF - e.vx) * Math.min(1, dt * 7);
        e.vy += (mvy * e.speed * spdMul * slowF - e.vy) * Math.min(1, dt * 7);
      } else {
        e.vx *= 0.985; e.vy *= 0.985;
      }
      e.x += (e.vx + e.kx) * dt; e.y += (e.vy + e.ky) * dt;
      e.kx *= Math.pow(0.0016, dt); e.ky *= Math.pow(0.0016, dt);
      this.clampEnemy(e);

      // separation
      for (const o of this.enemies) {
        if (o === e || o.spawnT > 0 || o.dead || e.ai === "gate") continue;
        const ddx = e.x - o.x, ddy = e.y - o.y;
        const dd = Math.hypot(ddx, ddy);
        const min = e.r + o.r;
        if (dd > 0.01 && dd < min) {
          const push = ((min - dd) / min) * 130 * dt;
          e.x += (ddx / dd) * push; e.y += (ddy / dd) * push;
        }
      }

      // contact damage
      if (e.ai !== "gate" && e.ai !== "guard" && !e.dead && Math.hypot(this.px - e.x, this.py - e.y) < e.r + 22 && e.hitCd <= 0 && this.iframe <= 0) {
        e.hitCd = 0.62;
        this.damagePlayer(e.dmg, e.x, e.y);
        if (d.thorns > 0) this.hitEnemy(e, d.thorns, false, "#c8d6ff");
        // 吸魂（エリート特殊行動）：命中した分だけHPを吸収
        if (e.eliteTrait === "vampire" && e.hp < e.maxHp) {
          e.hp = Math.min(e.maxHp, e.hp + e.dmg * 0.5);
          this.spark(e.x, e.y, "#e34bff", 90);
        }
      }
    }
    this.enemies = this.enemies.filter((e) => !e.dead && e.hp > 0);
    if (this.status !== "playing") return;
    const blasts = tickTraps(this.traps, dt, [
      { id: 0, x: this.px, y: this.py, r: 20, alive: this.hp > 0 },
      ...this.enemies.map((e) => ({ id: e.id, x: e.x, y: e.y, r: e.r, alive: !e.dead && e.spawnT <= 0 })),
    ]);
    for (const blast of blasts) {
      const color = blast.kind === "spike" ? "#ffb06e" : "#bfd5f3";
      this.rings.push({ x: blast.x, y: blast.y, r: 8, max: blast.radius, life: 0.45, color, width: 12 });
      for (let i = 0; i < 18; i++) this.spark(blast.x, blast.y, color, 320);
      this.shake = Math.min(16, this.shake + 7);
      for (const e of [...this.enemies]) {
        if (e.dead || e.spawnT > 0) continue;
        if (Math.hypot(e.x - blast.x, e.y - blast.y) <= blast.radius + e.r) {
          this.hitEnemy(e, this.derived.damage * 2 + e.maxHp * (e.boss ? 0.015 : 0.2), false, color, e.x - blast.x, e.y - blast.y, true, { environmental: true, secondary: true, bypassGuard: true, knockback: 950 });
          if (!e.dead) this.applyStatus(e, "stun");
        }
      }
      if (Math.hypot(this.px - blast.x, this.py - blast.y) <= blast.radius + 20) this.damagePlayer(15 + this.floor * 2.2, blast.x, blast.y);
    }
    if (this.status !== "playing") return;

    // ---- projectiles（ゾーンで敵弾速度が変わる）
    const zProj = this.zone.projSpeed ?? 1;
    for (const p of this.projs) {
      p.life -= dt;
      if (p.homing > 0) {
        const t = this.nearest(p.x, p.y, 460, p.hit);
        if (t) {
          const a = Math.atan2(t.y - p.y, t.x - p.x);
          const cur = Math.atan2(p.vy, p.vx);
          let da = a - cur;
          while (da > Math.PI) da -= Math.PI * 2;
          while (da < -Math.PI) da += Math.PI * 2;
          const na = cur + clamp(da, -p.homing * dt, p.homing * dt);
          const sp = Math.hypot(p.vx, p.vy);
          p.vx = Math.cos(na) * sp; p.vy = Math.sin(na) * sp;
        }
      }
      p.x += p.vx * dt; p.y += p.vy * dt;
      if (p.x < 40 || p.x > ROOM_W - 40 || p.y < 40 || p.y > ROOM_H - 40) p.life = 0;
      for (const o of this.obstacles) if (Math.hypot(p.x - o.x, p.y - o.y) < o.r) { p.life = 0; this.spark(p.x, p.y, p.color, 40); }
      if (p.life <= 0) continue;
      for (const d of this.destructibles) {
        if (d.dead) continue;
        if (Math.hypot(p.x - d.x, p.y - d.y) < d.r + p.r) {
          d.hp -= p.dmg;
          p.life = 0;
          this.spark(p.x, p.y, p.color, 120);
          if (d.hp <= 0) { d.dead = true; this.destroyDestructible(d); }
          break;
        }
      }
      if (p.life <= 0) continue;
      for (const e of [...this.enemies]) {
        if (e.spawnT > 0 || e.dead || e.hp <= 0 || p.hit.has(e.id)) continue;
        if (Math.hypot(p.x - e.x, p.y - e.y) < e.r + p.r) {
          p.hit.add(e.id);
          this.hitEnemy(e, p.dmg, p.crit, p.color, p.vx, p.vy, false, { art: p.art, secondary: p.art });
          if (p.pierce <= 0) { p.life = 0; break; }
          p.pierce--;
        }
      }
    }
    this.projs = this.projs.filter((p) => p.life > 0);

    for (const p of this.eprojs) {
      p.life -= dt; p.x += p.vx * zProj * dt; p.y += p.vy * zProj * dt;
      if (p.x < 40 || p.x > ROOM_W - 40 || p.y < 40 || p.y > ROOM_H - 40) p.life = 0;
      if (p.life <= 0) continue;
      const focused = this.isDanmakuMode() && (this.focusHeld || this.keys.has("control") || this.keys.has("z"));
      const hitR = this.isDanmakuMode() ? focused ? 5 : 10 : 18;
      const dist = Math.hypot(p.x - this.px, p.y - this.py);
      // グレイズ：接触しない範囲でかすめた弾を、一発につき一度だけカウント
      if (this.isDanmakuMode() && !p.grazed && this.iframe <= 0 && dist >= p.r + hitR && dist < p.r + hitR + 30) {
        p.grazed = true;
        this.grazeCount++; this.grazeCombo++; this.grazeComboT = 2.5;
        this.bombCharge += focused ? 3.5 : 2.5;
        if (this.bombCharge >= 100) {
          this.bombCharge -= 100;
          this.bombStock = Math.min(3, this.bombStock + 1);
          this.text(this.px, this.py - 52, "ボム補充!", "#bfeaff", 21);
          playSfx("levelup");
        } else if (this.grazeCombo % 10 === 0) {
          this.text(this.px, this.py - 48, `${this.grazeCombo} GRAZE`, "#bfeaff", 17);
        }
        this.energy = Math.min(this.energyMax, this.energy + 0.5);
        this.spark(p.x, p.y, "#ffffff", 90);
      }
      if (dist < p.r + hitR) {
        p.life = 0;
        if (this.iframe <= 0) {
          this.damagePlayer(this.danmakuBulletDmg(p.dmg), p.x, p.y);
          if (p.curse && this.curseT <= 0) {
            this.curseT = 8;
            this.text(this.px, this.py - 60, "呪われた — 被ダメ+25%", "#ff5cc8", 17);
            this.log("邪眼の呪いがあなたの魂に刻まれた（8秒・被ダメ+25%）", "#ff5cc8");
            for (let i = 0; i < 16; i++) this.spark(this.px, this.py, "#ff5cc8", 200);
          } else if (p.curse) {
            this.curseT = 8;
          }
        }
      }
    }
    this.eprojs = this.eprojs.filter((p) => p.life > 0);

    // ---- ボス衝撃波（拡張リングに接触するとダメージ）
    for (const sw of this.shockwaves) {
      sw.life -= dt;
      sw.r += (sw.max - sw.r) * Math.min(1, dt * 3.2);
      if (!sw.hitPlayer && this.iframe <= 0 && this.status === "playing") {
        const dd = Math.abs(Math.hypot(this.px - sw.x, this.py - sw.y) - sw.r);
        if (dd < 24) {
          sw.hitPlayer = true;
          this.damagePlayer(sw.dmg, sw.x, sw.y);
        }
      }
    }
    this.shockwaves = this.shockwaves.filter((sw) => sw.life > 0);

    // ---- 追跡ビーム（ゆっくり振り向き、貫通して刺す）
    for (const b of this.homingBeams) {
      b.life -= dt;
      b.dmgT -= dt;
      b.angle += b.spin * dt;
      // プレイヤーへ緩やかに追跡
      const ta = Math.atan2(this.py - b.y, this.px - b.x);
      let da = ta - b.angle;
      while (da > Math.PI) da -= Math.PI * 2;
      while (da < -Math.PI) da += Math.PI * 2;
      b.angle += clamp(da, -0.5 * dt, 0.5 * dt);
      // 線分とプレイヤーの距離で命中判定
      const ex = b.x + Math.cos(b.angle) * b.len;
      const ey = b.y + Math.sin(b.angle) * b.len;
      const dpx = ex - b.x, dpy = ey - b.y;
      const denom = dpx * dpx + dpy * dpy || 1;
      const t = clamp(((this.px - b.x) * dpx + (this.py - b.y) * dpy) / denom, 0, 1);
      const cx = b.x + dpx * t, cy = b.y + dpy * t;
      if (Math.hypot(this.px - cx, this.py - cy) < b.width + 14 && b.dmgT <= 0 && this.status === "playing") {
        b.dmgT = 0.5;
        if (this.iframe <= 0) this.damagePlayer(b.dmg, b.x, b.y);
      }
    }
    this.homingBeams = this.homingBeams.filter((b) => b.life > 0);

    // ---- 邪眼の呪いの新星（命中で呪い付与）
    // （呪いの新星は b_eye の弾として射出、命中時に curseT を設定）

    if (this.status !== "playing") return;
    // ---- abilities
    this.tickAbilities(dt);
    if (this.status !== "playing") return;

    // ---- timers
    for (const t of this.timers) t.t -= dt;
    const due = this.timers.filter((t) => t.t <= 0);
    this.timers = this.timers.filter((t) => t.t > 0);
    for (let i = 0; i < due.length; i++) {
      if (this.status !== "playing") { this.timers.push(...due.slice(i)); break; }
      due[i].fn();
    }
    if (this.status !== "playing") return;

    // ---- slashes
    for (const s of this.slashes) s.life -= dt;
    this.slashes = this.slashes.filter((s) => s.life > 0);
    for (const rg of this.rings) { rg.life -= dt; rg.r += (rg.max - rg.r) * Math.min(1, dt * 9); }
    this.rings = this.rings.filter((x) => x.life > 0);
    for (const b of this.beams) b.life -= dt;
    this.beams = this.beams.filter((b) => b.life > 0);

    // ---- particles
    for (const p of this.parts) {
      p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt;
      p.vx *= Math.pow(p.drag, dt * 60); p.vy *= Math.pow(p.drag, dt * 60);
    }
    if (this.parts.length > 780) this.parts.splice(0, this.parts.length - 780);
    this.parts = this.parts.filter((p) => p.life > 0);

    for (const t of this.texts) { t.life -= dt; t.y += t.vy * dt; t.vy *= 0.94; }
    this.texts = this.texts.filter((t) => t.life > 0);

    // ---- orbs & items
    for (const o of this.orbs) {
      o.t += dt;
      const dd = Math.hypot(this.px - o.x, this.py - o.y);
      const pull = o.kind === "heart" ? d.pickup * 0.7 : d.pickup * ((this.abilities.magnet ?? 0) > 0 ? 2.3 : 1);
      if (dd < pull) {
        const s = clamp((1 - dd / pull) * 900 + 90, 90, 900);
        o.vx += ((this.px - o.x) / (dd || 1)) * s * dt * 6;
        o.vy += ((this.py - o.y) / (dd || 1)) * s * dt * 6;
      }
      o.vx *= 0.94; o.vy *= 0.94;
      o.x += o.vx * dt; o.y += o.vy * dt;
      if (dd < 26) {
        o.dead = true;
        if (o.kind === "xp") this.gainXp(o.value);
        else if (o.kind === "gold") { this.gold += o.value; this.gainEnergy(2); }
        else if (o.kind === "heart") { this.healPlayer(o.value); this.text(this.px, this.py - 34, `+${Math.round(o.value)}`, "#5cff9d", 18); }
        else if (o.kind === "soul") { this.gainEnergy(o.value); }
      }
    }
    this.orbs = this.orbs.filter((o) => !o.dead);
    this.drainRewards();
    if (this.status !== "playing") return;

    for (const gi of this.gitems) {
      gi.t += dt;
      const dd = Math.hypot(this.px - gi.x, this.py - gi.y) || 1;
      if (gi.t > -0.6 && dd < 130) { gi.x += ((this.px - gi.x) / dd) * 60 * dt * 4; gi.y += ((this.py - gi.y) / dd) * 60 * dt * 4; }
      if (gi.t > 0 && dd < 34) { gi.dead = true; this.openLoot(gi.item); break; }
    }
    this.gitems = this.gitems.filter((g) => !g.dead);
    if (this.status !== "playing") return;

    // ---- 残党狂暴化: 残り敵が少なくなると攻撃性と速度が上がる（終盤のだらだら掃討を潰す）
    {
      const alive = this.enemies.filter((e) => e.spawnT <= 0 && !e.boss && !e.champ).length;
      if (this.toSpawn <= 0 && alive > 0 && alive <= 3 && !this.enrageAnnounced && !this.isBossFloor()) {
        this.enrageAnnounced = true;
        for (const e of [...this.enemies]) {
          if (e.boss || e.champ || e.spawnT > 0) continue;
          e.speed *= 1.35;
          e.dmg *= 1.25;
          e.hp = Math.min(e.maxHp, e.hp + e.maxHp * 0.15);
          this.rings.push({ x: e.x, y: e.y, r: 6, max: e.r * 4, life: 0.5, color: "#ff5c5c", width: 4 });
        }
        this.log("残党が狂暴化した — 速く、強く、執拗に", "#ff5c5c");
        this.text(this.px, this.py - 70, "狂暴化!", "#ff5c5c", 22);
      }
    }

    // ---- portal
    if (this.portal) {
      this.portal.t += dt;
      this.portalIdleT += dt;
      // 転移門を開けたまま居座ると、深淵が追っ手を送り込む（最大3波・徐々に強化）
      const rushAt = 14 + this.portalRushWaves * 10;
      if (!this.isBossFloor() && this.floor < TRUE_FLOOR && this.portalIdleT > rushAt && this.portalRushWaves < 3) {
        this.portalRushWaves++;
        const n = 4 + this.portalRushWaves * 3;
        for (let i = 0; i < n; i++) this.spawnEnemy(false);
        for (const e of [...this.enemies]) { if (e.spawnT > 0) { e.speed *= 1.15 + this.portalRushWaves * 0.1; } }
        this.log(`転移門が軋む… 追っ手 第${this.portalRushWaves}波（${n}体）`, "#ff8a2b");
        this.text(this.px, this.py - 80, "追っ手!", "#ff8a2b", 24);
        this.shake = 10;
        playSfx("boss");
      }
      if (Math.hypot(this.px - this.portal.x, this.py - this.portal.y) < 46) { playSfx("portal"); this.nextFloor(); return; }
    } else if (this.toSpawn <= 0 && this.enemies.length === 0) {
      if (!this.floorEventResolved && (this.floor % 3 === 0 || this.isBossFloor())) {
        this.openFloorEvent();
      } else if (!this.portal) {
        // 25層のニヒルを撃破した → 真の深淵への選択を提示
        if (this.floor === FINAL_FLOOR && !this.trueAbyssOffered && this.runConfig.mode !== "endless") {
          this.offerTrueAbyss();
          return;
        }
        if (this.floor >= TRUE_FLOOR && this.runConfig.mode !== "endless") { this.finish(true); return; }
        this.portal = { x: ROOM_W / 2, y: ROOM_H / 2, t: 0 };
        this.rings.push({ x: ROOM_W / 2, y: ROOM_H / 2, r: 10, max: 420, life: 1, color: "#7ce8ff", width: 8 });
        this.log("転移門が開いた — 次の層へ", "#7ce8ff");
      }
    }

    // camera
    const lookAhead = this.isTouch || this.autoAim;
    const offx = lookAhead ? clamp(this.pvx * 0.14, -70, 70) : clamp((this.mouse.x - this.w / 2) * 0.12, -70, 70);
    const offy = lookAhead ? clamp(this.pvy * 0.14, -70, 70) : clamp((this.mouse.y - this.hgt / 2) * 0.12, -70, 70);
    const tx = this.px + offx, ty = this.py + offy;
    this.camX += (tx - this.camX) * Math.min(1, dt * 7);
    this.camY += (ty - this.camY) * Math.min(1, dt * 7);
    const hw = this.w / 2 / this.zoom, hh = this.hgt / 2 / this.zoom;
    this.camX = hw * 2 > ROOM_W ? ROOM_W / 2 : clamp(this.camX, hw, ROOM_W - hw);
    this.camY = hh * 2 > ROOM_H ? ROOM_H / 2 : clamp(this.camY, hh, ROOM_H - hh);
    if (this.shake > 0) this.shake = Math.max(0, this.shake - dt * 34);

    // dps window
    this.dmgWindow = this.dmgWindow.filter((x) => x.at > this.time - 3);
    // regen
    if (d.regen > 0) this.healPlayer(d.regen * dt);
  }

  private clampEnemy(e: Enemy) {
    if (e.ai === "gate") return;
    const m = 48 + e.r;
    if (e.x < m) { e.x = m; e.vx *= -0.3; }
    if (e.x > ROOM_W - m) { e.x = ROOM_W - m; e.vx *= -0.3; }
    if (e.y < m) { e.y = m; e.vy *= -0.3; }
    if (e.y > ROOM_H - m) { e.y = ROOM_H - m; e.vy *= -0.3; }
    for (const o of this.obstacles) {
      const dx = e.x - o.x, dy = e.y - o.y, dd = Math.hypot(dx, dy);
      const min = o.r + e.r;
      if (dd < min && dd > 0.01) { e.x = o.x + (dx / dd) * min; e.y = o.y + (dy / dd) * min; }
    }
    for (const d of this.destructibles) {
      if (d.dead) continue;
      const dx = e.x - d.x, dy = e.y - d.y, dd = Math.hypot(dx, dy);
      const min = d.r + e.r;
      if (dd < min && dd > 0.01) { e.x = d.x + (dx / dd) * min; e.y = d.y + (dy / dd) * min; }
    }
  }

  private resolveCircle(pr: number) {
    const m = 48 + pr;
    this.px = clamp(this.px, m, ROOM_W - m);
    this.py = clamp(this.py, m, ROOM_H - m);
    for (const o of this.obstacles) {
      const dx = this.px - o.x, dy = this.py - o.y, dd = Math.hypot(dx, dy);
      const min = o.r + pr;
      if (dd < min && dd > 0.01) { this.px = o.x + (dx / dd) * min; this.py = o.y + (dy / dd) * min; }
    }
    for (const d of this.destructibles) {
      if (d.dead) continue;
      const dx = this.px - d.x, dy = this.py - d.y, dd = Math.hypot(dx, dy);
      const min = d.r + pr;
      if (dd < min && dd > 0.01) { this.px = d.x + (dx / dd) * min; this.py = d.y + (dy / dd) * min; }
    }
  }

  /* -------------------------------------------------------------- combat */

  /** 溜め攻撃の解放 — 溜め時間に応じて 1.4〜2.6 倍、満タンで衝撃波とスタン */
  private releaseCharge() {
    const d = this.derived;
    const ratio = clamp((this.chargeT - 0.32) / (1.2 - 0.32), 0, 1);
    const mul = 1.4 + ratio * 1.2;
    const full = ratio >= 0.98;
    this.charging = false;
    this.chargeT = 0;
    this.atkCd = (1 / d.attackSpeed) * (full ? 1.6 : 1.2);
    const reach = this.cls.base.reach * d.area * (1.15 + ratio * 0.35);
    const arc = this.cls.base.arc * (1.1 + ratio * 0.5);
    this.slashes.push({ x: this.px, y: this.py, a: this.pAngle, reach, arc, life: 0.3, max: 0.3, color: full ? "#ffffff" : this.cls.accent });
    this.shake = Math.min(18, this.shake + 6 + ratio * 8);
    this.hitstop = full ? 0.1 : 0.06;
    playSfx(full ? "crit" : "hit", { pitch: full ? 0.7 : 0.85 });
    this.text(this.px, this.py - 56, full ? "重撃・極" : "重撃", full ? "#ffffff" : this.cls.color, full ? 26 : 20);
    if (full) this.rings.push({ x: this.px, y: this.py, r: 10, max: reach + 40, life: 0.45, color: this.cls.color, width: 10 });

    let hits = 0;
    for (const e of [...this.enemies]) {
      if (e.spawnT > 0) continue;
      const dx = e.x - this.px, dy = e.y - this.py;
      const dist = Math.hypot(dx, dy);
      if (dist > reach + e.r) continue;
      let da = Math.atan2(dy, dx) - this.pAngle;
      while (da > Math.PI) da -= Math.PI * 2;
      while (da < -Math.PI) da += Math.PI * 2;
      if (Math.abs(da) > Math.abs(arc) / 2 + 0.3) continue;
      const roll = this.rollDmg();
      this.hitEnemy(e, roll.value * mul, roll.crit || full, this.cls.color, dx * 3, dy * 3, true);
      if (full) this.applyStatus(e, "stun");
      hits++;
    }
    // 満タン溜めは破壊オブジェクトも強制起爆
    for (const ob of this.destructibles) {
      if (ob.dead) continue;
      if (Math.hypot(ob.x - this.px, ob.y - this.py) < reach + ob.r) {
        ob.dead = true; this.destroyDestructible(ob);
      }
    }
    if (hits >= 3) this.gainEnergy(6 * d.energyMult);
  }

  private attack(isEcho = false) {
    const d = this.derived;
    const r = this.rng;
    this.atkCd = 1 / (d.attackSpeed * (this.comboPassiveActive ? 1.4 : 1) * (this.autoAim ? 0.8 : 1));
    // 反撃: 回避直後の最初の一撃は威力 1.5 倍 ＋ 必殺ゲージ回復
    const isCounter = this.counterWindowT > 0;
    if (isCounter) {
      this.counterWindowT = 0;
      this.text(this.px, this.py - 48, "反撃!", "#7cffd0", 20);
      this.gainEnergy(8 * d.energyMult);
      this.rings.push({ x: this.px, y: this.py, r: 8, max: 90, life: 0.25, color: "#7cffd0", width: 4 });
    }
    const counterMul = isCounter ? 1.5 : 1;
    if (this.cls.attack === "melee") {
      // 3段コンボ：間が空くとリセット、3段目は大振りのフィニッシュ
      if (this.time - this.lastAttackT > 1.1) this.attackIdx = 0;
      this.lastAttackT = this.time;
      const idx = this.attackIdx;
      this.attackIdx = (this.attackIdx + 1) % 3;
      const finisher = idx === 2;
      const reach = this.cls.base.reach * d.area * (finisher ? 1.3 : 1);
      const arc = this.cls.base.arc * (finisher ? 1.25 : 1);
      if (finisher) this.atkCd *= 1.3;
      // 1段目と2段目で振る向きを反転
      const mirror = idx === 1 ? -1 : 1;
      this.slashes.push({ x: this.px, y: this.py, a: this.pAngle, reach, arc: arc * mirror, life: finisher ? 0.26 : 0.18, max: finisher ? 0.26 : 0.18, color: finisher ? "#ffffff" : this.cls.accent });
      this.shake = Math.min(finisher ? 13 : 9, this.shake + (finisher ? 4.5 : 2.2));
      // 弾き：斬撃範囲内の敵弾を消し飛ばす
      let parried = 0;
      for (const p of this.eprojs) {
        if (p.life <= 0) continue;
        const dx = p.x - this.px, dy = p.y - this.py;
        if (Math.hypot(dx, dy) > reach + 20) continue;
        let da = Math.atan2(dy, dx) - this.pAngle;
        while (da > Math.PI) da -= Math.PI * 2;
        while (da < -Math.PI) da += Math.PI * 2;
        if (Math.abs(da) > Math.abs(arc) / 2 + 0.3) continue;
        p.life = 0; parried++;
        for (let k = 0; k < 6; k++) this.spark(p.x, p.y, "#bfe9ff", 180);
      }
      if (parried > 0) {
        this.gainEnergy(3 * parried * d.energyMult);
        this.text(this.px, this.py - 48, "弾き!", "#bfe9ff", 18);
      }
      for (const d of this.destructibles) {
        if (d.dead) continue;
        const dx = d.x - this.px, dy = d.y - this.py;
        const dist = Math.hypot(dx, dy);
        if (dist > reach + d.r) continue;
        let da = Math.atan2(dy, dx) - this.pAngle;
        while (da > Math.PI) da -= Math.PI * 2;
        while (da < -Math.PI) da += Math.PI * 2;
        if (Math.abs(da) > Math.abs(arc) / 2 + 0.25) continue;
        const roll = this.rollDmg();
        d.hp -= roll.value * (finisher ? 1.7 : 1);
        if (d.hp <= 0) { d.dead = true; this.destroyDestructible(d); }
      }
      let hits = 0;
      for (const e of [...this.enemies]) {
        if (e.spawnT > 0) continue;
        const dx = e.x - this.px, dy = e.y - this.py;
        const dist = Math.hypot(dx, dy);
        if (dist > reach + e.r) continue;
        let da = Math.atan2(dy, dx) - this.pAngle;
        while (da > Math.PI) da -= Math.PI * 2;
        while (da < -Math.PI) da += Math.PI * 2;
        if (Math.abs(da) > Math.abs(arc) / 2 + 0.25) continue;
        const roll = this.rollDmg();
        const mul = (finisher ? 1.7 : 1) * counterMul;
        this.hitEnemy(e, roll.value * mul, roll.crit || isCounter, this.cls.color, dx * (finisher ? 2.2 : 1), dy * (finisher ? 2.2 : 1), true);
        hits++;
      }
      this.lastHitBatch = hits;
      if (hits > 2 || (finisher && hits > 0)) this.hitstop = finisher ? 0.07 : 0.05;
      // パッシブ: 残響の型（フィニッシャーが再放される）
      if (!isEcho && finisher && this.passiveFlags.has("echo_strike") && this.rng() < 0.45) {
        this.timers.push({ t: 0.16, fn: () => { if (this.status === "playing") { this.attackIdx = 2; this.attack(true); } } });
      }
      // アビリティ: 残響
      if (!isEcho && finisher && (this.abilities.echo ?? 0) > 0 && this.rng() < 0.2 * this.abilities.echo) {
        this.timers.push({ t: 0.2, fn: () => { if (this.status === "playing") { this.attackIdx = 2; this.attack(true); } } });
      }
    } else {
      const n = Math.max(1, d.projectiles) + (this.comboPassiveActive ? 2 : 0);
      const spread = 0.075 * (n - 1);
      for (let i = 0; i < n; i++) {
        const a = this.pAngle + (i - (n - 1) / 2) * (n > 1 ? spread * 2.1 : 0) + rnd(r, -0.025, 0.025);
        const sp = this.cls.base.projSpeed * (1 + (d.area - 1) * 0.2);
        const roll = this.rollDmg();
        this.projs.push({
          x: this.px + Math.cos(a) * 22, y: this.py + Math.sin(a) * 22,
          vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, r: 8 * d.area * (isCounter ? 1.4 : 1), dmg: roll.value * counterMul,
          life: 1.5, pierce: d.pierce, hit: new Set(), color: this.cls.color, friendly: true,
          homing: this.cls.id === "mage" ? 2.4 : 0, kind: this.cls.id === "gunner" ? "bullet" : "bolt", size: 1, crit: roll.crit,
        });
      }
      this.shake = Math.min(6, this.shake + 1.1);
      for (let i = 0; i < 4; i++) this.spark(this.px + Math.cos(this.pAngle) * 24, this.py + Math.sin(this.pAngle) * 24, this.cls.accent, 90);
    }
    this.gainEnergy(1.1 * d.energyMult);
  }

  private rollDmg(): { value: number; crit: boolean } {
    const d = this.derived;
    const base = d.damage * rnd(this.rng, 0.88, 1.12);
    const crit = !this.autoAim && this.rng() < d.crit;
    return { value: Math.round(base * (crit ? d.critDmg : 1) * 10) / 10, crit };
  }

  private destroyDestructible(d: Destructible) {
    d.dead = true;
    const r = this.rng;
    if (d.kind === "red_crystal") {
      playSfx("crit", { pitch: 0.62 });
      this.explodeAt(d.x, d.y, 220, this.derived.damage * 4.2, "#ff4d6d", true);
      this.log("爆発クリスタルが起爆した！", "#ff4d6d");
    } else if (d.kind === "blue_flask") {
      playSfx("parry", { pitch: 0.8 });
      this.rings.push({ x: d.x, y: d.y, r: 10, max: 240, life: 0.6, color: "#8be9ff", width: 8 });
      for (let i = 0; i < 30; i++) this.spark(d.x, d.y, "#8be9ff", 220);
      this.log("氷結のフラスコが砕け散った！", "#8be9ff");
      // Freeze all nearby enemies
      for (const e of [...this.enemies]) {
        if (e.spawnT > 0 || e.dead) continue;
        const dist = Math.hypot(e.x - d.x, e.y - d.y);
        if (dist < 250) {
          this.applyStatus(e, "freeze");
          e.slow = 4.5;
        }
      }
    } else if (d.kind === "shrine_fate") {
      playSfx("levelup");
      this.rings.push({ x: d.x, y: d.y, r: 10, max: 180, life: 0.5, color: "#f5c542", width: 6 });
      for (let i = 0; i < 20; i++) this.spark(d.x, d.y, "#f5c542", 180);
      
      const fates = [
        { id: "rage", label: "怒りの加護", color: "#ff5c5c", desc: "与ダメ+60% ＆ 被ダメ+20%（25秒）" },
        { id: "gold", label: "黄金の祝福", color: "#f5c542", desc: "討伐ゴールド+150% ＆ 最大HP-20（30秒）" },
        { id: "haste", label: "風の加護", color: "#2dd4a7", desc: "攻撃速度+40% ＆ 移動速度+30%（30秒）" },
        { id: "armor", label: "大地の加護", color: "#8fa3bf", desc: "被ダメ軽減+50% ＆ ダッシュ使用不可（25秒）" },
        { id: "scourge", label: "深淵の試練", color: "#e34bff", desc: "常時毎秒2ダメージ ＆ 幸運+100（20秒）" },
      ] as const;

      const rolled = pick(r, fates);
      this.activeFate = rolled.id;
      this.activeFateT = rolled.id === "scourge" ? 20 : rolled.id === "gold" ? 30 : 25;
      this.activeFateLabel = rolled.label;
      this.recompute();
      this.log(`【運命の祭壇】が砕けた！ ${rolled.label} を得た: ${rolled.desc}`, rolled.color);
      this.text(this.px, this.py - 60, rolled.label, rolled.color, 24);
    }
  }

  private recordDamage(amount: number, fromArt = false) {
    if (!Number.isFinite(amount) || amount <= 0) return;
    this.totalDamage += amount;
    this.dmgWindow.push({ at: this.time, amount });
    this.weaponArtGauge = clamp(this.weaponArtGauge + artChargeGain(amount, this.derived.damage, fromArt) * this.trial.energy, 0, 100);
  }

  private hitEnemy(e: Enemy, dmg: number, crit: boolean, color: string, kx = 0, ky = 0, melee = false, options: HitOptions = {}) {
    if (e.dead || e.hp <= 0 || !Number.isFinite(dmg) || dmg <= 0) return;
    const d = this.derived;
    const incoming = Math.hypot(kx, ky) > 0.001 ? Math.atan2(ky, kx) : Math.atan2(e.y - this.py, e.x - this.px);
    let final = dmg;
    if (this.autoAim && !options.secondary && !options.environmental) final *= 0.7;
    if (this.autoAim) crit = false;
    if (!options.bypassGuard && !options.environmental) {
      if (e.defId === "iron_guard") {
        const mult = guardMultiplier(e.angle, incoming);
        final *= mult;
        if (e.orbCd <= 0) {
          this.text(e.x, e.y - e.r - 28, mult < 1 ? "正面防御 70%" : "背後!", mult < 1 ? "#a7c4df" : "#ffd986", 19);
          e.orbCd = 0.22;
        }
      } else if (e.defId === "b_knight" && e.st !== 2 && guardMultiplier(e.angle, incoming) < 1) final *= 0.6;
    }
    if (!options.environmental) {
      if ((this.abilities.execute ?? 0) > 0 && e.hp / e.maxHp < 0.3) final *= 1 + 0.32 * this.abilities.execute;
      const trait = WEAPON_TRAITS.find((t) => t.id === this.equipment.weapon?.weaponTrait);
      if (trait?.effect === "execute" && e.hp / e.maxHp < 0.3) final *= 1 + trait.value / 100;
      if (this.passiveFlags.has("giant_slayer")) final *= e.elite || e.boss || e.champ ? 1.45 : 0.85;
      if (this.passiveFlags.has("swarm_bane") && this.lastHitBatch >= 3) final *= 1.35;
      final *= 1 + Math.min(0.4, this.combo * 0.01);
    }
    if (e.status.some((x) => x.kind === "vulnerable")) final *= 1.25;
    if (e.status.some((x) => x.kind === "shock")) final *= 1.15;
    final = Math.max(1, Math.round(final));
    let shielded = 0;
    if ((e.shieldHp ?? 0) > 0 && !options.environmental) {
      shielded = Math.min(e.shieldHp!, final); e.shieldHp! -= shielded; final -= shielded;
    }
    const actual = Math.min(e.hp, final);
    // 終焉神の星辰帰無（崩壊カウントダウン）に対するスタッガー蓄積
    if (e.pinnacleBoss && e.cataclysmT !== undefined && e.cataclysmT > 0) {
      const staggerMul = options.art ? 3.5 : melee ? 2.2 : 1.4;
      e.cataclysmStagger = (e.cataclysmStagger ?? 32000) - actual * staggerMul;
      if (e.cataclysmStagger <= 0) {
        e.cataclysmT = 0;
        e.cataclysmStagger = 0;
        this.applyStatus(e, "stun");
        this.applyStatus(e, "vulnerable");
        e.slow = 3.5;
        this.flashFx = 0.9;
        this.shake = 26;
        this.text(e.x, e.y - e.r - 40, "STAGGER!! 詠唱中断", "#ffd700", 28);
        this.log("【STAGGER!!】神の詠唱を打ち破った！ タルタロス・オメガが大きく怯む！", "#ffd700");
        this.rings.push({ x: e.x, y: e.y, r: 20, max: 600, life: 0.9, color: "#ffd700", width: 16 });
        playSfx("crit", { pitch: 1.5 });
      }
    }
    e.hp -= final; e.flash = 1;
    const push = options.knockback ?? (melee ? 260 : 130);
    if (e.ai !== "gate") {
      e.kx += Math.cos(incoming) * push * (e.boss ? 0.15 : 1);
      e.ky += Math.sin(incoming) * push * (e.boss ? 0.15 : 1);
    }
    if (actual > 0 && !options.environmental) {
      this.combo++; this.comboT = 2.4;
      this.bestCombo = Math.max(this.bestCombo, this.combo);
    }
    this.recordDamage(actual, !!(options.art || options.secondary || options.environmental));
    if (!options.environmental) this.gainEnergy(actual * 0.055 * d.energyMult);
    this.text(e.x + rnd(this.fxRng, -12, 12), e.y - e.r - 6, shielded > 0 && final === 0 ? "盾" : crit ? `${nf(actual)}!` : nf(actual), shielded > 0 && final === 0 ? "#8bdcff" : crit ? "#ffd23c" : color, crit ? 25 : 17);
    for (let i = 0; i < (crit ? 9 : 4); i++) this.spark(e.x, e.y, color, crit ? 200 : 140);
    playSfx(crit ? "crit" : "hit", { pitch: e.boss ? 0.65 : 1 });
    if (crit) { this.shake = Math.min(12, this.shake + 3); this.hitstop = 0.035; }
    if (d.lifesteal > 0 && actual > 0 && !options.environmental) this.healPlayer(actual * d.lifesteal);
    // Death is recorded before any secondary reaction so rewards cannot be duplicated.
    if (e.hp <= 0) this.killEnemy(e);
    if (actual <= 0 || options.secondary || options.environmental || options.art || this.reactionDepth > 0) return;
    this.reactionDepth++;
    try {
      if (d.chain > 0 || (this.abilities.chain ?? 0) > 0) this.chainLightning(e, Math.min(10, d.chain + (this.abilities.chain ?? 0)), actual * 0.42);
      if (d.explode > 0 && this.rng() < d.explode) this.explodeAt(e.x, e.y, 96 * d.area, actual * 0.6, "#ff9a3c");
      if (!e.dead) {
        if (d.freeze > 0 && this.rng() < d.freeze) this.applyStatus(e, "freeze");
        const trait = WEAPON_TRAITS.find((t) => t.id === this.equipment.weapon?.weaponTrait);
        if (trait && this.rng() * 100 < trait.value) {
          if (trait.effect === "burn" || trait.effect === "poison" || trait.effect === "bleed") this.applyStatus(e, trait.effect);
        }
      }
      if ((this.abilities.thunderfury ?? 0) > 0 && this.rng() < 0.09 * this.abilities.thunderfury) {
        this.beams.push({ x1: e.x, y1: e.y - 620, x2: e.x, y2: e.y, life: 0.26, color: "#ffe14a" });
        this.explodeAt(e.x, e.y, 90 + this.abilities.thunderfury * 22, actual * (0.8 + this.abilities.thunderfury * 0.3), "#ffe14a");
      }
      if (this.cls.id === "samurai" && crit && this.combo >= 35) {
        this.beams.push({ x1: this.px, y1: this.py, x2: e.x + Math.cos(incoming) * 450, y2: e.y + Math.sin(incoming) * 450, life: 0.3, color: "#ffd0d6" });
        this.explodeAt(e.x, e.y, 150, actual * 1.6, "#ffd0d6");
      } else if (this.cls.id === "mage" && !e.dead && e.status.some((x) => x.kind === "burn") && e.status.some((x) => x.kind === "freeze")) {
        e.status = e.status.filter((x) => x.kind !== "burn" && x.kind !== "freeze");
        this.explodeAt(e.x, e.y, 160, actual * 2.2, "#c58bff");
        this.text(e.x, e.y - e.r - 20, "元素崩壊", "#c58bff", 22);
      } else if (this.cls.id === "gunner" && this.combo >= 50) {
        if (!this.comboPassiveActive) this.text(this.px, this.py - 60, "OVERDRIVE", "#ff9a3c", 26);
        this.comboPassiveActive = true; this.comboPassiveT = 2.4;
      }
    } finally { this.reactionDepth--; }
  }

  private applyStatus(e: Enemy, kind: ActiveStatus["kind"]) {
    if (e.dead || e.hp <= 0 || e.ai === "gate") return;
    const def = STATUS_EFFECTS[kind];
    const existing = e.status.find((s) => s.kind === kind);
    if (existing) {
      existing.remaining = def.duration * (e.boss && kind === "stun" ? 0.3 : 1);
      existing.stacks = Math.min(5, existing.stacks + 1);
    } else {
      e.status.push({ kind, remaining: def.duration * (e.boss && kind === "stun" ? 0.3 : 1), stacks: 1 });
    }
    this.spark(e.x, e.y, def.color, 80);
  }

  private chainLightning(from: Enemy, jumps: number, dmg: number) {
    const hitSet = new Set<number>([from.id]);
    let cur = from;
    let px = cur.x, py = cur.y;
    for (let i = 0; i < jumps; i++) {
      let best: Enemy | null = null, bd = 250;
      for (const e of [...this.enemies]) {
        if (hitSet.has(e.id) || e.spawnT > 0 || e.dead || e.hp <= 0) continue;
        const dd = Math.hypot(e.x - px, e.y - py);
        if (dd < bd) { bd = dd; best = e; }
      }
      if (!best) break;
      this.beams.push({ x1: px, y1: py, x2: best.x, y2: best.y, life: 0.22, color: "#9be9ff" });
      hitSet.add(best.id);
      px = best.x; py = best.y;
      this.hitEnemy(best, dmg, false, "#9be9ff", best.x - cur.x, best.y - cur.y, false, { secondary: true });
      cur = best;
    }
    void cur;
  }

  private explodeAt(x: number, y: number, radius: number, dmg: number, color: string, friendly = true) {
    this.rings.push({ x, y, r: 8, max: radius, life: 0.42, color, width: 10 });
    for (let i = 0; i < 22; i++) this.spark(x, y, color, 300);
    this.shake = Math.min(14, this.shake + 5);
    if (friendly) {
      for (const e of [...this.enemies]) {
        if (e.spawnT > 0) continue;
        const dx = e.x - x, dy = e.y - y, dd = Math.hypot(dx, dy);
        if (dd < radius + e.r) this.hitEnemy(e, dmg * (1 - dd / (radius + e.r) * 0.5), false, color, dx, dy, false, { secondary: true, bypassGuard: true });
      }
    } else {
      if (Math.hypot(this.px - x, this.py - y) < radius + 20 && this.iframe <= 0) this.damagePlayer(dmg, x, y);
    }
  }

  private killEnemy(e: Enemy) {
    if (e.dead) return;
    e.dead = true;
    this.kills++;
    if (e.defId === "cursed_gate" && !e.summoned) {
      this.gatesDestroyed++;
      this.log("召喚門を破壊 ─ この門からの増援は停止した", "#ffc4d8");
      this.text(e.x, e.y - e.r - 34, "召喚停止", "#ffc4d8", 24);
    }
    if (this.equipment.weapon?.weaponClass === "scythe" && this.harvestStacks < 10) { this.harvestStacks++; this.recompute(); }
    if ((this.abilities.harvest ?? 0) > 0 && (e.elite || e.champ || e.boss)) {
      this.mods.damagePct = (this.mods.damagePct ?? 0) + this.abilities.harvest * 1.5;
      this.recompute();
    }
    playSfx(e.boss ? "boss" : "kill");
    if (e.boss) this.bossesKilled++;
    // エリート特殊行動：死の瞬間
    if (e.eliteTrait === "splitter" && !e.mini) {
      for (let i = 0; i < 2; i++) {
        const a = rnd(this.rng, 0, Math.PI * 2);
        const mini = this.spawnEnemy(false, clamp(e.x + Math.cos(a) * 30, 80, ROOM_W - 80), clamp(e.y + Math.sin(a) * 30, 80, ROOM_H - 80), "slime", e);
        if (mini) { mini.mini = true; mini.r *= 0.5; mini.hp *= 0.4; mini.maxHp *= 0.4; mini.name = "分裂体"; }
      }
      this.text(e.x, e.y - e.r - 16, "分裂!", "#a0e87c", 18);
    }
    if (e.eliteTrait === "burst") {
      this.explodeAt(e.x, e.y, 150, e.dmg * 1.1, "#ff9a3c", false);
    }
    // 遺物: 魂喰いの爪 — 撃破ごとに魂ゲージ+8
    if (this.relicFlags.has("soulfang")) {
      this.gainEnergy(8);
      if (this.energy >= this.energyMax) this.text(this.px, this.py - 46, "魂満杯", "#c58bff", 15);
    }
    const r = this.rng;
    for (let i = 0; i < (e.boss ? 60 : 14); i++) this.spark(e.x, e.y, e.color, e.boss ? 420 : 200);
    this.parts.push({ x: e.x, y: e.y, vx: 0, vy: 0, life: 0.5, max: 0.5, r: e.r, color: e.color, kind: "burst", drag: 0.9 });
    if (e.boss) { this.shake = 26; this.hitstop = 0.24; this.flashFx = 1; this.slowT = 1.2; }
    else this.shake = Math.min(10, this.shake + 1.6);
    if (e.elite) { this.hitstop = Math.max(this.hitstop, 0.08); this.rings.push({ x: e.x, y: e.y, r: 10, max: 160, life: 0.5, color: e.color, width: 8 }); }

    // 連続撃破
    if (this.time - this.lastKillT < 1.3) this.killStreak++;
    else this.killStreak = 1;
    this.lastKillT = this.time;
    if (this.killStreak === 5 || this.killStreak === 10 || this.killStreak === 20 || (this.killStreak > 20 && this.killStreak % 15 === 0)) {
      const label = this.killStreak >= 20 ? "殲滅!!" : this.killStreak >= 10 ? "大虐殺!" : "連続撃破!";
      this.text(this.px, this.py - 78, `${this.killStreak} ${label}`, "#ff8a2b", 30);
      this.flashFx = Math.min(1, this.flashFx + 0.3);
      this.gainEnergy(6 * this.derived.energyMult);
      this.rings.push({ x: this.px, y: this.py, r: 20, max: 220, life: 0.5, color: "#ff8a2b", width: 6 });
    }

    const xpVal = Math.round(e.xp * (1 + this.floor * 0.16) * (e.champ ? 1.6 : 1));
    const chunks = e.boss ? 26 : e.champ ? 22 : Math.min(6, 1 + Math.floor(xpVal / 6));
    for (let i = 0; i < chunks; i++) {
      const a = rnd(r, 0, 7);
      this.orbs.push({ x: e.x, y: e.y, vx: Math.cos(a) * rnd(r, 40, 190), vy: Math.sin(a) * rnd(r, 40, 190), kind: "xp", value: Math.max(1, Math.round(xpVal / chunks)), t: 0 });
    }
    const goldChance = e.boss ? 1 : e.elite ? 0.85 : 0.3;
    if (r() < goldChance) {
      const g = Math.round((3 + this.floor * 1.7 + (e.boss ? 120 : 0)) * this.derived.goldMult * (this.runConfig.modifier?.goldRate ?? 1) * (this.mutation?.gold ?? 1) * rnd(r, 0.7, 1.4));
      for (let i = 0; i < (e.boss ? 10 : 1); i++) {
        const a = rnd(r, 0, 7);
        this.orbs.push({ x: e.x, y: e.y, vx: Math.cos(a) * rnd(r, 40, 170), vy: Math.sin(a) * rnd(r, 40, 170), kind: "gold", value: Math.round(g / (e.boss ? 10 : 1)), t: 0 });
      }
    }
    if (r() < (e.boss ? 1 : 0.035) * 1) this.orbs.push({ x: e.x, y: e.y, vx: 0, vy: 0, kind: "heart", value: Math.round(this.derived.maxHp * (e.boss ? 0.5 : 0.2)), t: 0 });
    if (r() < 0.16) this.orbs.push({ x: e.x, y: e.y, vx: 0, vy: 0, kind: "soul", value: 9, t: 0 });

    // 装備ドロップ — 番人は必ず高品質の1点を残す
    let dropChance = e.boss ? 1 : e.champ ? 1 : e.elite ? 0.34 : (0.028 + this.derived.luck * 0.0007) * (this.mutation?.loot ?? 1);
    if (r() < dropChance) {
      const bonus = e.champ ? 110 : e.boss ? 130 : e.elite ? 45 : 0;
      if (e.champ) this.log(`${e.name} は確かな品を残した`, "#f5c542");
      const mutLoot = this.mutation?.loot ?? 1;
    const it = genItem(r, this.floor + rndInt(r, 0, 2) + (mutLoot > 1.3 ? 1 : 0), undefined, this.derived.luck, bonus);
      this.gitems.push({ x: e.x + rnd(r, -14, 14), y: e.y + rnd(r, -14, 14), item: it, t: 0 });
      if (it.rarity === "legendary" || it.rarity === "mythic") {
        this.log(`${RARITY_META[it.rarity].label}装備発見 — ${it.name}`, RARITY_META[it.rarity].color);
        this.artifactsFound.push({ name: it.name, slot: it.slot, rarity: it.rarity, power: it.power, floor: this.floor, affixes: it.affixes.map((a) => `${a.label} ${a.text}`) });
      }
    }
    // 素材ドロップ（遺物: 盗賊の金貨で量2倍）
    const coinMul = this.relicFlags.has("coin") ? 2 : 1;
    const matDrops: { id: MaterialId; chance: number; amount: [number, number]; color: string }[] = [
      { id: "soul_shard", chance: e.boss ? 1 : e.champ ? 0.9 : e.elite ? 0.5 : 0.12, amount: e.boss || e.champ ? [4, 10] : [1, 3], color: "#5cc8ff" },
      { id: "ancient_stone", chance: e.boss ? 1 : e.champ ? 0.8 : e.elite ? 0.3 : 0.04, amount: e.boss || e.champ ? [2, 5] : [1, 2], color: "#f5c542" },
      { id: "chaos_gem", chance: e.boss ? 0.8 : e.champ ? 0.5 : e.elite ? 0.15 : 0.015, amount: e.boss || e.champ ? [2, 4] : [1, 1], color: "#e34bff" },
      { id: "void_essence", chance: e.boss ? 0.6 : e.champ ? 0.4 : e.elite ? 0.08 : 0.005, amount: e.boss || e.champ ? [1, 3] : [1, 1], color: "#7c8cff" },
      { id: "dragon_heart", chance: e.boss ? 0.35 : e.champ ? 0.25 : 0, amount: [1, 1], color: "#ff4d6d" },
    ];
    for (const md of matDrops) {
      if (r() < md.chance) {
        const amt = materialAmount(rndInt(r, md.amount[0], md.amount[1]) * coinMul, this.attributes.fortune, r);
        this.materials[md.id] = (this.materials[md.id] ?? 0) + amt;
        this.text(e.x + rnd(r, -20, 20), e.y - e.r - 18, `+${amt} ${md.id === "soul_shard" ? "魂片" : md.id === "ancient_stone" ? "古石" : md.id === "chaos_gem" ? "混沌" : md.id === "void_essence" ? "虚空" : "竜心"}`, md.color, 15);
      }
    }

    // 武器特性・撃破時効果
    const wep = this.equipment.weapon;
    if (wep?.weaponTrait) {
      const trait = WEAPON_TRAITS.find((t) => t.id === wep.weaponTrait);
      if (trait) {
        if (trait.effect === "aoe_on_kill") {
          this.explodeAt(e.x, e.y, trait.value, this.derived.damage * 0.5, wep.weaponClass ? "#ff9a3c" : "#ff9a3c");
        }
        if (trait.effect === "lifesteal") {
          const heal = this.derived.maxHp * (trait.value / 100);
          this.healPlayer(heal);
          this.text(this.px, this.py - 34, `+${Math.round(heal)}`, "#5cff9d", 16);
        }
      }
    }

    if ((this.abilities.blood ?? 0) > 0) {
      const heal = this.derived.maxHp * 0.008 * this.abilities.blood;
      this.healPlayer(heal);
    }
    // 分裂母は棺から数に化ける
    if (e.ai === "split" && !e.mini) {
      const mhp = e.maxHp * 0.22;
      for (let i = 0; i < 2; i++) {
        const a = rnd(r, 0, Math.PI * 2);
        const mini: Enemy = {
          ...e, id: this.eid++, name: "分裂体", mini: true, champ: false, elite: false,
          x: clamp(e.x + Math.cos(a) * 26, 70, ROOM_W - 70), y: clamp(e.y + Math.sin(a) * 26, 70, ROOM_H - 70),
          r: e.r * 0.52, hp: mhp, maxHp: mhp, dmg: e.dmg * 0.6, xp: Math.max(1, Math.round(e.xp * 0.2)),
          spawnT: 0.3, t: rnd(r, 0, 3), hitCd: 0.3, kx: Math.cos(a) * 300, ky: Math.sin(a) * 300,
          status: [], dead: false, vx: 0, vy: 0,
        };
        this.enemies.push(mini);
        for (let k = 0; k < 5; k++) this.spark(mini.x, mini.y, "#7dd6a0", 160);
      }
      this.text(e.x, e.y - e.r - 16, "分裂!", "#7dd6a0", 20);
    }
    if (e.boss) {
      this.boss = null;
      this.text(e.x, e.y - 40, "撃破!", "#ffd23c", 34);
      if (!e.trueBoss) {
        this.offerPassives();
        if ((this.floor === 5 || this.floor === 20) && !this.relicOfferedFloors.has(this.floor)) {
          this.relicOfferedFloors.add(this.floor); this.offerRelic();
        }
      }
    }
    if (e.champ && this.rng() < 0.35) this.offerPassives();
    if (e.pinnacleBoss) {
      this.pinnacleCleared = true;
      this.eprojs = []; this.shockwaves = []; this.homingBeams = [];
      this.log("終焉神《タルタロス・オメガ》討伐 ─ 全ての試練を越えた", "#ffffff");
      this.finish(true);
    } else if (e.trueBoss) {
      this.trueCleared = this.isTrueFloor();
      this.eprojs = []; this.shockwaves = []; this.homingBeams = [];
      this.log("原初虚無《ニヒル・ゼロ》討伐 ─ 真の踏破達成", "#ffffff");
      this.finish(true);
    }
  }

  private damagePlayer(raw: number, sx: number, sy: number) {
    if (!Number.isFinite(raw) || raw <= 0 || this.iframe > 0 || this.status !== "playing" || this.hp <= 0) return;
    // 神聖の盾（パ拉丁ダッシュ）：被弾を無効化
    if (this.bulwark > 0) {
      this.bulwark--;
      this.iframe = 0.5;
      this.text(this.px, this.py - 52, "盾で防いだ", "#ffd76a", 18);
      this.rings.push({ x: this.px, y: this.py, r: 10, max: 80, life: 0.3, color: "#ffd76a", width: 6 });
      for (let i = 0; i < 12; i++) this.spark(this.px, this.py, "#ffd76a", 220);
      playSfx("parry", { pitch: 1.3 });
      return;
    }
    const d = this.derived;
    let fateMultiplier = 1;
    if (this.activeFate === "rage") fateMultiplier = 1.2;
    if (this.activeFate === "mist") fateMultiplier *= 1.3;
    // 邪眼の呪い：被ダメージ +25%
    if (this.curseT > 0) fateMultiplier *= 1.25;
    // アビリティ: 障壁 — 1.2秒以上立ち止まっていると軽減
    let wardenCut = 0;
    if ((this.abilities.warden ?? 0) > 0 && this.stillT > 1.2) {
      wardenCut = Math.min(0.35, 0.12 * this.abilities.warden);
    }
    // パッシブ: 強欲（ゴールド換算で被ダメ増）は recompute 側で armorPct 減として反映済み
    const dmg = Math.max(1, Math.round(raw * (1 - d.armor) * (1 - wardenCut) * fateMultiplier));
    const suffered = Math.min(this.hp, dmg);
    this.hp -= dmg;
    this.weaponArtGauge = clamp(this.weaponArtGauge + suffered / Math.max(1, d.maxHp) * 35 * this.trial.energy, 0, 100);
    this.pFlash = 1;
    this.iframe = 0.42;
    this.shake = Math.min(16, this.shake + 6);
    this.flashFx = Math.min(1, this.flashFx + 0.6);
    const a = Math.atan2(this.py - sy, this.px - sx);
    this.pvx += Math.cos(a) * 260; this.pvy += Math.sin(a) * 260;
    this.text(this.px, this.py - 40, `-${nf(dmg)}`, "#ff5c6e", 22);
    for (let i = 0; i < 12; i++) this.spark(this.px, this.py, "#ff3b52", 200);
    this.combo = 0;

    // アビリティ: 復讐 — 被弾した直後に周囲へ爆発
    if ((this.abilities.vengeance ?? 0) > 0) {
      const lv = this.abilities.vengeance;
      this.explodeAt(this.px, this.py, 130 + lv * 26, this.derived.damage * (0.9 + lv * 0.45), "#ff3b5c");
    }
    // パッシブ: 勢い が切れた
    if (this.passiveFlags.has("momentum")) { this.recompute(); }

    if (this.hp <= 0) {
      // 弾幕の獄：ボムを残している場合だけ致命被弾後0.18秒のデスボム猶予
      if (this.isDanmakuMode() && this.bombStock > 0 && this.deathbombT <= 0) {
        this.hp = 1;
        this.deathbombT = 0.18;
        this.iframe = Math.max(this.iframe, 0.18);
        this.text(this.px, this.py - 78, "X! デスボム!", "#ffffff", 25);
        this.log("致命弾！ 今すぐXでデスボム — 猶予0.18秒", "#ffffff");
        return;
      }
      // 遺物: 呪われた鏡 — 40%でHP50%復活（1回のみ）
      if (this.relicFlags.has("mirror") && !this.mirrorUsed && this.rng() < 0.4) {
        this.mirrorUsed = true;
        this.hp = this.derived.maxHp * 0.5;
        this.iframe = 2.4;
        this.slowT = 1.0;
        this.flashFx = 1;
        this.shake = 28;
        this.rings.push({ x: this.px, y: this.py, r: 10, max: 640, life: 1, color: "#5cc8ff", width: 18 });
        for (let i = 0; i < 56; i++) this.spark(this.px, this.py, "#5cc8ff", 400);
        this.eprojs = [];
        this.shockwaves = [];
        this.log("【呪われた鏡】死の影が割れた — 鏡の中の自分があなたの身代わりになった", "#5cc8ff");
        this.text(this.px, this.py - 80, "鏡の加護", "#5cc8ff", 32);
        return;
      }
      // パッシブ: 不滅の誓い
      if (this.undyingLeft > 0) {
        this.undyingLeft = 0;
        this.hp = this.derived.maxHp * 0.5;
        this.iframe = 2.2;
        this.slowT = 1.0;
        this.flashFx = 1;
        this.shake = 30;
        this.rings.push({ x: this.px, y: this.py, r: 10, max: 700, life: 1, color: "#ffffff", width: 20 });
        for (let i = 0; i < 60; i++) this.spark(this.px, this.py, "#ffffff", 420);
        this.eprojs = [];
        this.log("【不滅の誓い】発動 — 死の淵から這い上がった", "#ffffff");
        this.text(this.px, this.py - 80, "不滅", "#ffffff", 34);
        // 周囲の敵を吹き飛ばす
        for (const e of [...this.enemies]) {
          if (e.spawnT > 0) continue;
          const dx = e.x - this.px, dy = e.y - this.py;
          if (Math.hypot(dx, dy) < 420) { e.kx += (dx / 1) * 6; e.ky += (dy / 1) * 6; e.slow = 2; }
        }
        return;
      }
      this.hp = 0; this.finish(false);
    }
  }

  /* ----------------------------------------------------------- abilities */

  private tickAbilities(dt: number) {
    const d = this.derived;
    const r = this.rng;
    const lv = (k: AbilityId) => this.abilities[k] ?? 0;

    if (lv("orbit") > 0) {
      const n = 2 + lv("orbit");
      this.orbitA += dt * 2.6;
      const rad = (92 + lv("orbit") * 13) * d.area;
      this.orbitCd -= dt;
      if (this.orbitCd <= 0) {
        this.orbitCd = 0.34;
        for (const e of [...this.enemies]) {
          if (e.spawnT > 0) continue;
          for (let i = 0; i < n; i++) {
            const a = this.orbitA + (i / n) * Math.PI * 2;
            const bx = this.px + Math.cos(a) * rad, by = this.py + Math.sin(a) * rad;
            if (Math.hypot(e.x - bx, e.y - by) < e.r + 22) {
              this.hitEnemy(e, d.damage * (0.55 + lv("orbit") * 0.13), false, "#e8f0ff", e.x - this.px, e.y - this.py);
              break;
            }
          }
        }
      }
    }
    if (lv("aura") > 0) {
      this.auraCd -= dt;
      const rad = (108 + lv("aura") * 22) * d.area;
      if (this.auraCd <= 0) {
        this.auraCd = 0.42;
        this.rings.push({ x: this.px, y: this.py, r: rad * 0.5, max: rad, life: 0.35, color: "#ffd76a", width: 3 });
        for (const e of [...this.enemies]) {
          if (e.spawnT > 0) continue;
          if (Math.hypot(e.x - this.px, e.y - this.py) < rad + e.r) {
            this.hitEnemy(e, d.damage * (0.3 + lv("aura") * 0.11), false, "#ffd76a", e.x - this.px, e.y - this.py);
          }
        }
      }
    }
    if (lv("missile") > 0) {
      this.missileCd -= dt;
      if (this.missileCd <= 0) {
        this.missileCd = Math.max(0.24, 1.15 - lv("missile") * 0.14);
        for (let i = 0; i < lv("missile"); i++) {
          const t = this.nearest(this.px, this.py, 700, new Set());
          const a = t ? Math.atan2(t.y - this.py, t.x - this.px) : this.pAngle + rnd(r, -1, 1);
          this.projs.push({ x: this.px, y: this.py, vx: Math.cos(a) * 340, vy: Math.sin(a) * 340, r: 9, dmg: d.damage * 0.72, life: 2.4, pierce: 0, hit: new Set(), color: "#c58bff", friendly: true, homing: 6, kind: "missile", size: 1, crit: false });
        }
      }
    }
    if (lv("nova") > 0) {
      this.novaCd -= dt;
      if (this.novaCd <= 0) {
        this.novaCd = Math.max(2.2, 6.5 - lv("nova") * 0.8);
        const rad = (180 + lv("nova") * 34) * d.area;
        this.rings.push({ x: this.px, y: this.py, r: 12, max: rad, life: 0.6, color: "#8be9ff", width: 12 });
        for (const e of [...this.enemies]) {
          if (e.spawnT > 0) continue;
          if (Math.hypot(e.x - this.px, e.y - this.py) < rad + e.r) {
            e.slow = 2.2;
            this.hitEnemy(e, d.damage * (0.8 + lv("nova") * 0.2), false, "#8be9ff", e.x - this.px, e.y - this.py);
          }
        }
        this.shake = Math.min(10, this.shake + 4);
      }
    }
    // アビリティ: 渦刃 — 攻撃のたびに刃の渦
    if (lv("bladespin") > 0 && this.atkCd > 0) {
      const key = "bspin_cd" as const;
      const rec = this as unknown as Record<string, number>;
      rec[key] = (rec[key] ?? 0) - dt;
      if ((rec[key] ?? 0) <= 0) {
        rec[key] = 0.34;
        const a = rnd(r, 0, 7);
        this.projs.push({
          x: this.px, y: this.py, vx: Math.cos(a) * 300, vy: Math.sin(a) * 300,
          r: 11 * d.area, dmg: d.damage * (0.3 + lv("bladespin") * 0.14), life: 1.1,
          pierce: 2 + lv("bladespin"), hit: new Set(), color: "#d6e8ff", friendly: true,
          homing: 0, kind: "bolt", size: 0.9, crit: false,
        });
      }
    }
    // アビリティ: 障壁 — 立ち止まると軽減（リアルタイム軽減なのでここで処理）
    if (lv("warden") > 0 && this.stillT > 0.6 && this.status === "playing") {
      this.rings.push({ x: this.px, y: this.py, r: 30, max: 60, life: 0.18, color: "#8fa3bf", width: 2 });
    }
    if (lv("familiar") > 0) {
      const n = lv("familiar");
      for (let i = 0; i < n; i++) {
        const a = this.time * 1.6 + (i / n) * Math.PI * 2;
        const fx = this.px + Math.cos(a) * 128, fy = this.py + Math.sin(a) * 128;
        if (i === 0 || true) {
          const key = `fam_${i}`;
          const prev = (this as unknown as Record<string, number>)[key] ?? 0;
          if (this.time - prev > 0.85) {
            (this as unknown as Record<string, number>)[key] = this.time;
            const t = this.nearest(fx, fy, 620, new Set());
            const ta = t ? Math.atan2(t.y - fy, t.x - fx) : a;
            this.projs.push({ x: fx, y: fy, vx: Math.cos(ta) * 520, vy: Math.sin(ta) * 520, r: 7, dmg: d.damage * 0.5, life: 1.6, pierce: 0, hit: new Set(), color: "#7cffb2", friendly: true, homing: 1.6, kind: "bolt", size: 0.85, crit: false });
          }
        }
      }
    }
  }

  private nearest(x: number, y: number, maxD: number, exclude: Set<number>): Enemy | null {
    let best: Enemy | null = null, bd = maxD;
    for (const e of [...this.enemies]) {
      if (e.spawnT > 0 || e.dead || e.hp <= 0 || exclude.has(e.id)) continue;
      const dd = Math.hypot(e.x - x, e.y - y);
      if (dd < bd) { bd = dd; best = e; }
    }
    return best;
  }

  /* --------------------------------------------------------------- skill */

  useSkill() {
    if (this.status !== "playing" || this.energy < this.energyMax) return;
    this.energy = 0;
    playSfx("skill");
    const d = this.derived;
    const r = this.rng;
    this.flashFx = 0.9;
    this.shake = 22;
    this.hitstop = 0.1;
    const id = this.cls.id;
    this.text(this.px, this.py - 60, this.cls.skillName, this.cls.color, 28);
    if (id === "samurai") {
      const reach = 300 * d.area;
      this.slashes.push({ x: this.px, y: this.py, a: this.pAngle, reach, arc: Math.PI * 2, life: 0.42, max: 0.42, color: "#ffd0d6" });
      this.rings.push({ x: this.px, y: this.py, r: 20, max: reach, life: 0.5, color: "#ff4d5e", width: 14 });
      for (const e of [...this.enemies]) {
        if (e.spawnT > 0) continue;
        const dx = e.x - this.px, dy = e.y - this.py;
        if (Math.hypot(dx, dy) < reach + e.r) this.hitEnemy(e, d.damage * 3.4, true, "#ff4d5e", dx, dy, true);
      }
    } else if (id === "mage") {
      for (let i = 0; i < 16; i++) {
        const t = i < 8 ? this.enemies.filter((e) => e.spawnT <= 0)[i % Math.max(1, this.enemies.length)] : null;
        const tx = t ? t.x : rnd(r, 200, ROOM_W - 200);
        const ty = t ? t.y : rnd(r, 200, ROOM_H - 200);
        this.timers.push({
          t: i * 0.09, fn: () => {
            this.rings.push({ x: tx, y: ty, r: 10, max: 150 * d.area, life: 0.45, color: "#ff9a3c", width: 10 });
            for (const e of [...this.enemies]) {
              if (e.spawnT > 0) continue;
              const dx = e.x - tx, dy = e.y - ty;
              if (Math.hypot(dx, dy) < 150 * d.area + e.r) this.hitEnemy(e, d.damage * 2.2, false, "#ff9a3c", dx, dy);
            }
            for (let k = 0; k < 18; k++) this.spark(tx, ty, "#ff9a3c", 300);
            this.shake = Math.min(16, this.shake + 3);
          },
        });
      }
    } else if (id === "ninja") {
      for (let i = 0; i < 28; i++) {
        const a = (i / 28) * Math.PI * 2;
        this.timers.push({
          t: i * 0.035, fn: () => {
            this.slashes.push({ x: this.px, y: this.py, a, reach: 210 * d.area, arc: 0.9, life: 0.2, max: 0.2, color: "#3ce8a5" });
            for (const e of [...this.enemies]) {
              if (e.spawnT > 0) continue;
              const dx = e.x - this.px, dy = e.y - this.py;
              const dd = Math.hypot(dx, dy);
              if (dd > 210 * d.area + e.r) continue;
              let da = Math.atan2(dy, dx) - a;
              while (da > Math.PI) da -= Math.PI * 2;
              while (da < -Math.PI) da += Math.PI * 2;
              if (Math.abs(da) < 0.7) this.hitEnemy(e, d.damage * 1.15, true, "#3ce8a5", dx, dy, true);
            }
          },
        });
      }
      this.iframe = Math.max(this.iframe, 1.4);
    } else if (id === "paladin") {
      const rad = 340 * d.area;
      this.rings.push({ x: this.px, y: this.py, r: 20, max: rad, life: 0.85, color: "#ffd76a", width: 26 });
      for (const e of [...this.enemies]) {
        if (e.spawnT > 0) continue;
        const dx = e.x - this.px, dy = e.y - this.py;
        if (Math.hypot(dx, dy) < rad + e.r) { this.hitEnemy(e, d.damage * 3.1, false, "#ffd76a", dx, dy); e.slow = 2; }
      }
      this.eprojs = [];
      const heal = d.maxHp * 0.22;
      this.healPlayer(heal);
      this.text(this.px, this.py - 70, `+${nf(heal)}`, "#5cff9d", 26);
      this.iframe = Math.max(this.iframe, 1.1);
    } else {
      const n = 34;
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2 + rnd(r, -0.05, 0.05);
        this.projs.push({ x: this.px, y: this.py, vx: Math.cos(a) * 760, vy: Math.sin(a) * 760, r: 9, dmg: d.damage * 1.5, life: 1.4, pierce: 3, hit: new Set(), color: "#ff9a3c", friendly: true, homing: 0, kind: "bullet", size: 1.1, crit: false });
      }
      this.rings.push({ x: this.px, y: this.py, r: 10, max: 260, life: 0.5, color: "#ff9a3c", width: 8 });
    }
  }

  tryPickupNearest() {
    if (this.status !== "playing") return;
    let best: GItem | null = null, bd = 90;
    for (const g of this.gitems) {
      const dd = Math.hypot(g.x - this.px, g.y - this.py);
      if (dd < bd) { bd = dd; best = g; }
    }
    if (best) { this.openLoot(best.item); best.dead = true; this.gitems = this.gitems.filter((g) => !g.dead); }
  }

  /* --------------------------------------------------------------- spawn */

  /** 層の番人 — 単体で討伐値段のあるミニボス。高品質装備と素材を必ず落とす */
  private spawnWarden() {
    const r = this.rng;
    const def = weighted(r, ENEMIES.filter((e2) => e2.minFloor <= this.floor && e2.ai !== "gate" && e2.ai !== "summoner"), (e2) => 1 + Math.max(0, 6 - Math.abs(this.floor - e2.minFloor)) * 0.5);
    const wdef = pick(r, WARDENS);
    const name = `${wdef.name}`;
    // プレイヤーから遠い辺の位置へ
    let x = rnd(r, 120, ROOM_W - 120), y = rnd(r, 120, ROOM_H - 120);
    for (let tries = 0; tries < 30; tries++) {
      const tx = rnd(r, 100, ROOM_W - 100), ty = rnd(r, 100, ROOM_H - 100);
      if (Math.hypot(tx - this.px, ty - this.py) > 380) { x = tx; y = ty; break; }
    }
    const hpMul = wdef.hpMul * (1 + (this.floor - 1) * 0.4) * (this.runConfig.modifier?.enemyHp ?? 1) * (this.mutation?.enemyHp ?? 1);
    const dmgMul = wdef.dmgMul * (1 + (this.floor - 1) * 0.24) * (this.runConfig.modifier?.enemyDamage ?? 1) * (this.mutation?.enemyDmg ?? 1);
    const e: Enemy = {
      id: this.eid++, name, x, y, vx: 0, vy: 0, r: def.r * 1.55,
      hp: def.hp * hpMul * this.trial.enemyHp, maxHp: def.hp * hpMul * this.trial.enemyHp,
      dmg: def.dmg * dmgMul * this.trial.enemyDamage, speed: def.speed * 1.06 * this.trial.enemySpeed,
      xp: Math.round(def.xp * 5), ai: def.ai, shape: def.shape,
      color: wdef.color, accent: def.accent, boss: false, elite: true, trueBoss: false,
      hitCd: 0, slow: 0, flash: 0, t: rnd(r, 0, 5), st: 0, stT: 1.2,
      angle: 0, fire: 1.4, spawnT: 0.7, kx: 0, ky: 0, orbCd: 0, scale: 1, dead: false,
      def_fire: def.fireRate, def_ps: def.projSpeed, status: [], champ: true, defId: def.id, mini: false,
    };
    this.enemies.push(e);
    this.boss = null; // 番人は上部ボスゲージを使わない（画面外指針では elite 表示）
    this.rings.push({ x, y, r: 10, max: e.r * 5, life: 0.8, color: wdef.color, width: 8 });
    for (let i = 0; i < 30; i++) this.spark(x, y, wdef.color, 260);
    this.log(`《層の番人》${name} — ${wdef.flavor}`, wdef.color);
    this.slowT = 0.4;
    playSfx("boss");
  }

  private spawnEnemy(isBoss: boolean, atX?: number, atY?: number, forcedId?: string, parent?: Enemy): Enemy | undefined {
    if (!isBoss && this.enemies.filter((e) => !e.dead).length >= 48) return undefined;
    const r = this.rng;
    let x = atX ?? 0, y = atY ?? 0;
    if (atX === undefined) {
      for (let tries = 0; tries < 40; tries++) {
        const edge = rndInt(r, 0, 3);
        if (edge === 0) { x = rnd(r, 90, ROOM_W - 90); y = 90; }
        else if (edge === 1) { x = rnd(r, 90, ROOM_W - 90); y = ROOM_H - 90; }
        else if (edge === 2) { x = 90; y = rnd(r, 90, ROOM_H - 90); }
        else { x = ROOM_W - 90; y = rnd(r, 90, ROOM_H - 90); }
        if (Math.hypot(x - this.px, y - this.py) > 300) break;
      }
    }

    if (isBoss) {
      const rushOrder = [0, 1, 2, 3, 4, 2, 3, 5];
      const b = this.runConfig.mode === "bossrush"
        ? BOSSES[rushOrder[Math.min(7, this.floor - 1)]]
        : this.runConfig.mode === "endless" && this.floor > 25
          ? BOSSES[(Math.floor(this.floor / 5) - 1) % 5]
          : BOSSES.find((x2) => x2.floor === this.floor) ?? BOSSES[4];
      const def = b.def;
      const hpMul = (1 + Math.max(0, this.floor - def.minFloor) * 0.06) * (this.runConfig.mode === "bossrush" && this.floor > 5 ? 1.45 : 1) * 1.3 * (this.runConfig.modifier?.enemyHp ?? 1) * (this.mutation?.enemyHp ?? 1) * this.trial.enemyHp * DIFFICULTIES[this.runConfig.difficulty].hpMul;
      const e: Enemy = {
        id: this.eid++, name: def.name, x, y, vx: 0, vy: 0, r: def.r,
        hp: def.hp * hpMul, maxHp: def.hp * hpMul, dmg: def.dmg * 1.75 * (this.runConfig.modifier?.enemyDamage ?? 1) * (this.mutation?.enemyDmg ?? 1) * this.trial.enemyDamage * DIFFICULTIES[this.runConfig.difficulty].dmgMul, speed: def.speed * (this.runConfig.modifier?.enemySpeed ?? 1) * this.trial.enemySpeed,
        xp: def.xp, ai: def.ai, shape: def.shape, color: def.color, accent: def.accent,
        boss: true, elite: false, trueBoss: def.trueBoss === true, pinnacleBoss: def.pinnacleBoss === true, hitCd: 0, slow: 0, flash: 0, t: 0, st: 0, stT: 2,
        angle: 0, fire: 2.6, spawnT: 0.9, kx: 0, ky: 0, orbCd: 8, scale: 1, dead: false,
        def_fire: def.fireRate, def_ps: def.projSpeed, status: [], champ: false, defId: def.id, mini: false,
      };
      this.enemies.push(e);
      this.boss = e;
      this.rings.push({ x, y, r: 10, max: 240, life: 0.9, color: def.accent, width: 10 });
      this.log(`⚠ ${def.name} が現れた`, "#ff5c6e");
      this.shake = 18;
      // 取り巻き
      for (let i = 0; i < (this.runConfig.mode === "bossrush" ? 0 : Math.min(6, 2 + Math.floor(this.floor / 5))); i++) this.spawnEnemy(false);
      return e;
    }

    if (!forcedId && !parent && this.wave === 0 && this.enemies.length === 0 && this.runConfig.mode !== "bossrush") {
      forcedId = this.floor === 4 ? "iron_guard" : this.floor === 6 ? "necromancer" : this.floor === 7 ? "cursed_gate" : undefined;
    }
    const avail = ENEMIES.filter((e2) => e2.minFloor <= this.floor && (e2.ai !== "gate" || this.enemies.filter((e) => !e.dead && e.ai === "gate").length < 2));
    const def = (forcedId ? ENEMIES.find((e) => e.id === forcedId) : null) ?? weighted(r, avail, (e2) => 1 + Math.max(0, 6 - Math.abs(this.floor - e2.minFloor)) * 0.5);
    const mut = this.mutation;
    const elite = !parent && def.ai !== "gate" && r() < clamp((0.05 + this.floor * 0.014) * (this.runConfig.modifier?.eliteRate ?? 1) * (mut?.eliteRate ?? 1) * this.trial.eliteRate, 0, 0.9);
    const pre = elite ? pick(r, ELITE_PREFIX) : null;
    // エリートに特殊行動を割り当てる（6割）
    const eliteTrait = elite && r() < 0.6 ? pick(r, ELITE_TRAITS) : null;
    // 深層ほど曲線が立ち上がる（10層以降は二次項が効き始める）
    const deep = Math.max(0, this.floor - 10);
    const diff = DIFFICULTIES[this.runConfig.difficulty];
    const curveHp = 1 + (this.floor - 1) * 0.42 + deep * deep * 0.02;
    const curveDmg = 1 + (this.floor - 1) * 0.24 + deep * deep * 0.012;
    // エリートは層が深いほど接頭辞の倍率が強まる
    const eliteGrow = pre ? 1 + Math.min(0.6, this.floor * 0.02) : 1;
    const hpScale = curveHp * (pre ? pre.mods.hp * eliteGrow : 1) * (this.runConfig.modifier?.enemyHp ?? 1) * (mut?.enemyHp ?? 1) * diff.hpMul;
    const dmgScale = curveDmg * (pre ? pre.mods.dmg * eliteGrow : 1) * (this.runConfig.modifier?.enemyDamage ?? 1) * (mut?.enemyDmg ?? 1) * diff.dmgMul;
    // 速度も深層でじわじわ上がる（上限 +30%）＋ 難易度
    const speedScale = (this.runConfig.modifier?.enemySpeed ?? 1) * (mut?.enemySpeed ?? 1) * (1 + Math.min(0.3, deep * 0.02)) * (1 + (diff.hpMul - 1) * 0.4);
    const baseHp = def.hp * hpScale * this.trial.enemyHp * (parent ? 0.45 : 1);
    // 魔盾：通常のHPに上乗せする吸収層
    const shieldHp = eliteTrait?.id === "shield" ? baseHp * 1.0 : -1;
    const e: Enemy = {
      id: this.eid++, name: eliteTrait ? `${pre ? pre.name : ""}${def.name}【${eliteTrait.name}】` : (pre ? `${pre.name}${def.name}` : def.name),
      x, y, vx: 0, vy: 0,
      r: def.r * (pre ? 1.32 : 1), hp: baseHp, maxHp: baseHp,
      dmg: def.dmg * dmgScale * this.trial.enemyDamage * (parent ? 0.6 : 1), speed: def.speed * (pre ? pre.mods.speed : 1) * speedScale * this.trial.enemySpeed,
      xp: Math.round(def.xp * (pre ? 3.4 : 1) * (eliteTrait ? 1.3 : 1)), ai: def.ai, shape: def.shape,
      color: eliteTrait ? eliteTrait.color : (pre ? pre.color : def.color), accent: def.accent, boss: false, elite, trueBoss: false,
      hitCd: 0, slow: 0, flash: 0, t: rnd(r, 0, 5), st: 0, stT: rnd(r, 0.6, 2),
      angle: 0, fire: rnd(r, 0.6, 2), spawnT: 0.42, kx: 0, ky: 0, orbCd: 0, scale: 1, dead: false,
      def_fire: def.fireRate, def_ps: def.projSpeed, status: [], champ: false, defId: def.id, mini: false,
      eliteTrait: eliteTrait?.id ?? null, shieldHp,
      summoned: !!parent, summonParent: parent?.id, summonCd: def.ai === "gate" ? 3.8 : 5.2,
    };
    if (parent) { e.name = "朽ちた兵"; e.xp = Math.max(1, Math.round(e.xp * 0.25)); e.spawnT = 0.9; e.mini = true; }
    this.enemies.push(e);
    this.rings.push({ x, y, r: 4, max: e.r * 3, life: 0.42, color: e.color, width: 3 });
    if (eliteTrait) this.text(e.x, e.y - e.r - 18, `【${eliteTrait.name}】`, eliteTrait.color, 16);
    return e;
  }

  private openFloorEvent() {
    this.floorEventResolved = true;
    const stories = [
      { title: "灰燼の祭壇", subtitle: "崩れた石碑に、まだ祈りの火が残っている。" },
      { title: "彷徨う鍛冶場", subtitle: "名もなき鍛冶師が、最後の炉を守っていた。" },
      { title: "運命の分岐点", subtitle: "三つの道。その選択が次の戦いを変える。" },
      { title: "魂の回廊", subtitle: "深淵の残響が、あなたの願いを問う。" },
    ];
    const story = pick(this.rng, stories);
    const options: FloorEvent["options"] = [
      { id: "rest", title: "焚火で休む", desc: `最大HPの38%回復 + 魂ゲージ25`, color: "#2dd4a7" },
      { id: "forge", title: "遺された宝箱", desc: `第${this.floor}層相当の装備を鍛冶場から得る`, color: "#f5c542" },
      { id: "oath", title: "血の誓約", desc: "最大HPの18%を捧げ、祝福を1つ選ぶ", color: "#e34bff" },
    ];
    if (this.runConfig.mode === "covenant") options[2] = { id: "forge", title: "禁呪の宝箱", desc: "祝福なき契約。代わりに装備を得る。", color: "#c58bff" };
    // 所持金があれば略奪商人が現れる（選択肢は3つのうち1つと差し替わる）
    if (this.gold >= 120) {
      options[Math.floor(rnd(this.rng, 0, 1.999))] = {
        id: "merchant", title: "略奪商人の風格", desc: "逃げ延びた商人の影 — ゴールドで装備を買える", color: "#ffb03c",
      };
    }
    this.floorEvent = { floor: this.floor, title: story.title, subtitle: story.subtitle, options };
    this.status = "event";
    this.h.onFloorEvent(this.floorEvent);
    this.log(`${this.floor}層イベント — ${story.title}`, "#f5c542");
    this.pushSnap();
  }

  chooseFloorEvent(index: number) {
    if (this.status !== "event" || !this.floorEvent) return;
    const selected = this.floorEvent.options[index];
    if (!selected) return;
    this.eventsCompleted++;
    this.floorEvent = null;
    this.status = "playing";
    if (selected.id === "rest") {
      const heal = this.derived.maxHp * 0.38;
      this.healPlayer(heal);
      this.gainEnergy(25);
      this.text(this.px, this.py - 48, `休息 +${Math.round(heal)} HP`, "#2dd4a7", 22);
      this.log(`祭壇の火で ${Math.round(heal)} HP を回復`, "#2dd4a7");
    } else if (selected.id === "forge") {
      const it = genItem(this.rng, this.floor + 1, undefined, this.derived.luck, this.isBossFloor() ? 130 : 65);
      this.log(`鍛冶場で装備を発見 — ${it.name}`, RARITY_META[it.rarity].color);
      if (it.rarity === "legendary" || it.rarity === "mythic") {
        this.artifactsFound.push({ name: it.name, slot: it.slot, rarity: it.rarity, power: it.power, floor: this.floor, affixes: it.affixes.map((a) => `${a.label} ${a.text}`) });
      }
      this.openLoot(it);
      return;
    } else if (selected.id === "merchant") {
      this.openShop();
      return;
    } else {
      this.hp = Math.max(1, this.hp - this.derived.maxHp * 0.18);
      this.boonChoices = rollBoons(this.rng, this.abilities, 3);
      this.status = "levelup";
      this.text(this.px, this.py - 50, "血の誓約", "#e34bff", 24);
      this.h.onLevelUp(this.boonChoices);
      this.log("血を捧げ、祝福を求める", "#e34bff");
      this.pushSnap();
      return;
    }
    this.last = performance.now();
    this.pushSnap();
  }

  /* ------------------------------------------- ランダムイベント */

  /** 層の中盤で突然起こる一過性の出来事。毎回違う展開を作る心臓部 */
  private triggerRandomEvent() {
    const r = this.rng;
    const ev = rollRandomEvent(r);
    this.rings.push({ x: this.px, y: this.py, r: 10, max: 300, life: 0.7, color: ev.color, width: 10 });
    this.text(this.px, this.py - 86, ev.name, ev.color, 26);
    this.shake = 8;
    playSfx("levelup");
    this.log(`《${ev.name}》 ${ev.desc}`, ev.color);

    switch (ev.kind) {
      case "loot": {
        // 宝の間: 3点ドロップ
        for (let i = 0; i < 3; i++) {
          const it = genItem(r, this.floor + 1, undefined, this.derived.luck, 40 + i * 30);
          const a = (i / 3) * Math.PI * 2;
          this.gitems.push({ x: clamp(this.px + Math.cos(a) * 110, 70, ROOM_W - 70), y: clamp(this.py + Math.sin(a) * 110, 70, ROOM_H - 70), item: it, t: 0 });
        }
        break;
      }
      case "gamble": {
        // 賭博の祭壇: 所持金の半分を賭ける
        const stake = Math.floor(this.gold / 2);
        if (stake <= 0) { this.log("賭ける金がない… 祭壇は不快感を示した", "#8d86a3"); break; }
        if (r() < 0.5) { this.gold += stake; this.log(`大当たり！ +${stake.toLocaleString()}G`, "#f5c542"); this.text(this.px, this.py - 60, "大当たり!", "#f5c542", 24); }
        else { this.gold -= stake; this.log(`はずれ。 −${stake.toLocaleString()}G`, "#ff5c6e"); this.text(this.px, this.py - 60, "はずれ", "#ff5c6e", 24); }
        break;
      }
      case "elite": {
        // 精鋭の襲撃: エリート3体
        for (let i = 0; i < 3; i++) this.spawnEnemy(false);
        for (const e of this.enemies) if (e.elite && e.spawnT <= 0) { e.hp *= 1.4; e.maxHp *= 1.4; }
        this.log("鍛え抜かれた気配が3つ…", "#ff5c6e");
        break;
      }
      case "heal": {
        // 飢餓の呪印中は回復量が抑えられる。
        this.healPlayer(this.derived.maxHp);
        this.energy = this.energyMax;
        this.materials.soul_shard = (this.materials.soul_shard ?? 0) + 5;
        this.materials.ancient_stone = (this.materials.ancient_stone ?? 0) + 2;
        this.text(this.px, this.py - 60, "全回復", "#2dd4a7", 24);
        break;
      }
      case "fate": {
        if (ev.fate) {
          this.activeFate = ev.fate;
          this.activeFateT = ev.fate === "mist" ? 20 : 15;
          this.activeFateLabel = ev.name;
          this.recompute();
        }
        break;
      }
      case "swarm": {
        // 群れの呼び声: 大量だが弱い
        const n = rndInt(r, 10, 18);
        for (let i = 0; i < n; i++) this.spawnEnemy(false);
        for (const e of this.enemies) if (e.spawnT > 0) { e.hp *= 0.6; e.maxHp *= 0.6; e.xp = Math.max(1, Math.round(e.xp * 1.7)); }
        this.log(`群れが押し寄せてくる（${n}体）— 範囲攻撃が映える`, "#ffe14a");
        break;
      }
    }
    this.pushSnap();
  }

  /* --------------------------------------------- パッシブスキル */

  /** 層の主撃破後、パッシブ3候補を提示 */
  private offerPassives() {
    if (this.runConfig.mode === "covenant" || this.status === "dead" || this.status === "win") return;
    this.pendingPassives++;
  }

  /** 深淵の遺物を提示（5層・20層の主撃破時、及び真の深淵へ降りた時） */
  private offerRelic() {
    if (this.relics.length + this.pendingRelics >= 2 || this.runConfig.mode === "covenant" || this.status === "dead" || this.status === "win") return;
    this.pendingRelics++;
  }

  chooseRelic(rel: Relic) {
    if (this.status !== "relic" || !this.relicChoices?.some((x) => x.id === rel.id) || this.relics.includes(rel.id)) return;
    this.relics.push(rel.id);
    this.relicFlags = relicFlags(this.relics);
    this.relicChoices = null;
    this.status = "playing";
    this.last = performance.now();
    this.recompute();
    this.text(this.px, this.py - 54, `遺物獲得 — ${rel.name}`, rel.color, 22);
    this.log(`【${rel.name}】${rel.desc}`, rel.color);
    for (let i = 0; i < 40; i++) this.spark(this.px, this.py, rel.color, 300);
    this.rings.push({ x: this.px, y: this.py, r: 10, max: 320, life: 0.8, color: rel.color, width: 10 });
    this.pushSnap();
  }

  choosePassive(p: Passive) {
    if (this.status !== "passive" || !this.passiveChoices?.some((x) => x.id === p.id) || this.passives.includes(p.id)) return;
    this.passives.push(p.id);
    this.passiveFlags = passiveFlags(this.passives);
    if (p.flag === "undying") this.undyingLeft = 1;
    const before = this.derived.maxHp;
    this.recompute();
    if (this.derived.maxHp > before) this.hp += this.derived.maxHp - before;
    this.passiveChoices = null;
    this.status = "playing";
    this.last = performance.now();
    this.text(this.px, this.py - 52, p.name, p.color, 22);
    this.log(`パッシブ獲得 — ${p.name}（${p.desc}）`, p.color);
    // パッシブ選択の後に実行遺物を提示
    if (this.pendingRelicOffer) {
      this.pendingRelicOffer = false;
      this.relicOfferedFloors.add(this.floor);
      this.offerRelic();
    }
    this.pushSnap();
  }

  getOwnedPassives(): Passive[] {
    return this.passives.map((id) => PASSIVES.find((p) => p.id === id)).filter(Boolean) as Passive[];
  }

  /* -------------------------------------------------------- 略奪商人 */

  /** 商店開店 — 品質は層に応じて上昇し、他にはない出会いが待つ */
  private openShop() {
    const r = this.rng;
    this.shop = Array.from({ length: 3 }, (_, i) => {
      const it = genItem(r, this.floor + 1, undefined, this.derived.luck, [24, 55, 105][i]);
      const price = Math.max(25, Math.round(it.power * rnd(r, 0.78, 1.05)));
      return { item: it, price };
    });
    this.status = "shop";
    this.log("略奪商人：「買いなよ。ここから下は死ぬ地獄だぜ」", "#ffb03c");
    this.pushSnap();
  }

  buyShopItem(i: number): boolean {
    const entry = this.shop[i];
    if (!entry || this.status !== "shop") return false;
    if (this.gold < entry.price) { playSfx("error"); this.log("ゴールドが足りない", "#ff5c6e"); this.pushSnap(); return false; }
    this.gold -= entry.price;
    this.shop.splice(i, 1);
    playSfx("equip");
    this.log(`購入 — ${entry.item.name} (−${entry.price.toLocaleString()}G)`, "#ffb03c");
    if (entry.item.rarity === "legendary" || entry.item.rarity === "mythic") {
      this.artifactsFound.push({ name: entry.item.name, slot: entry.item.slot, rarity: entry.item.rarity, power: entry.item.power, floor: this.floor, affixes: entry.item.affixes.map((a) => `${a.label} ${a.text}`) });
    }
    this.openLoot(entry.item);
    return true;
  }

  closeShop() {
    if (this.status !== "shop") return;
    this.shop = [];
    this.status = "playing";
    this.last = performance.now();
    this.pushSnap();
  }

  /** 商店の品揃えをリロール（費用: 25G） */
  rerollShop(): boolean {
    if (this.status !== "shop") return false;
    const cost = 25;
    if (this.gold < cost) { playSfx("error"); this.log("ゴールドが足りない", "#ff5c6e"); return false; }
    this.gold -= cost;
    const r = this.rng;
    this.shop = Array.from({ length: 3 }, (_, i) => {
      const it = genItem(r, this.floor + 1, undefined, this.derived.luck, [24, 55, 105][i]);
      const price = Math.max(25, Math.round(it.power * rnd(r, 0.78, 1.05)));
      return { item: it, price };
    });
    playSfx("ui");
    this.log("商店の品揃えを更新した (−25G)", "#ffb03c");
    this.pushSnap();
    return true;
  }

  /** 素材を売却してゴールドを獲得（お助けトレード機能） */
  sellMaterial(matId: MaterialId): boolean {
    if (this.status !== "shop") return false;
    const count = this.materials[matId] ?? 0;
    if (count <= 0) { playSfx("error"); return false; }
    this.materials[matId] = count - 1;
    const prices: Record<MaterialId, number> = {
      soul_shard: 10,
      ancient_stone: 25,
      chaos_gem: 80,
      void_essence: 180,
      dragon_heart: 500,
    };
    const reward = Math.round(prices[matId] * this.derived.goldMult);
    this.gold += reward;
    playSfx("pickup");
    this.log(`${MATERIALS[matId].name}を1個売却した (+${reward}G)`, "#ffb03c");
    this.pushSnap();
    return true;
  }

  /* ---------------------------------------------------- 真・最終決戦 */

  /** 25層のニヒル撃破後、《ニヒル・ゼロ》が待つ真の深淵へ挑むか否かを選ばせる */
  private offerTrueAbyss() {
    this.trueAbyssOffered = true;
    this.status = "trueabyss";
    this.slowT = 0.8;
    this.flashFx = Math.min(1, this.flashFx + 0.6);
    this.shake = 20;
    playSfx("boss");
    this.log("虚無神ニヒルは消え去った… だが深淵の更に下から、何かが目覚めた。", "#ffffff");
    this.pushSnap();
  }

  /** 真の深淵（第26層）へ降りる — ここから先、撤退はできない */
  descendToTrueAbyss() {
    if (this.status !== "trueabyss") return;
    this.settleFloorDrops();
    this.floor = TRUE_FLOOR;
    this.status = "playing";
    this.truePhaseIdx = 0;
    // 真の決戦に挑む者への祝福 — 全回復し、必殺ゲージを満タンにする
    this.hp = this.derived.maxHp;
    this.energy = this.energyMax;
    this.buildFloor();
    this.last = performance.now();
    this.log("▽ 真・第26層 ▽ 原初虚無《ニヒル・ゼロ》の領域 — ここが本当に最後だ", "#ffffff");
    // 真の決戦前に遺物を1つ選べる（2つ目）
    this.timers.push({ t: 0.5, fn: () => this.offerRelic() });
    this.pushSnap();
  }

  /** 踏破としてここで帰還する（装備継承の選択へ） */
  finishRunNow() {
    if (this.status !== "trueabyss") return;
    this.finish(true);
  }

  private settleFloorDrops() {
    // 階層移動時に残った全装備を倉庫へ自動回収（プレイヤーは拾いに行く必要がない）
    const leftover = this.gitems.filter((g) => !g.dead);
    let stashed = 0;
    if (leftover.length > 0) {
      const overflow: Item[] = [];
      for (const g of leftover) {
        if (this.stash.length < this.stashCap) {
          this.stash.push(g.item);
          stashed++;
          this.log(`${g.item.name} を倉庫へ自動回収 (${this.stash.length}/${this.stashCap})`, RARITY_META[g.item.rarity].color);
        } else overflow.push(g.item);
      }
      // 倉庫が満杯で収まらなかった分だけ解体（55%の価値で）
      if (overflow.length > 0) {
        let total = 0;
        for (const it of overflow) {
          total += Math.round((4 + it.power * 0.6) * this.derived.goldMult * 0.55);
        }
        this.gold += total;
        this.log(`倉庫が満杯のため ${overflow.length} 点を解体 (+${total.toLocaleString()}G)`, "#8d86a3");
      }
    }
    // XP / ゴールドオーブも自動回収（取りに行く手間をなくす）
    const lostXp = this.orbs.filter((o) => !o.dead && o.kind === "xp").reduce((a, o) => a + o.value, 0);
    if (lostXp > 0) { this.gainXp(lostXp); this.text(this.px, this.py - 44, `EXP +${lostXp}`, "#5cc8ff", 18); }
    const lostGold = this.orbs.filter((o) => !o.dead && o.kind === "gold").reduce((a, o) => a + o.value, 0);
    if (lostGold > 0) { this.gold += lostGold; this.text(this.px, this.py - 60, `+${lostGold}G`, "#f5c542", 18); }

    this.gitems = [];
    this.orbs = [];
    // 階層移動後に倉庫確認画面を出す（より強い装備を選ぶ or 一括解体）
    this.pendingLootReview = stashed > 0;
    if (this.pendingLootReview) {
      this.pendingLootReturn = "playing";
      this.status = "loot";
      this.h.onLootReview([...this.stash]);
      this.pushSnap();
    }
  }

  /** 倉庫確認画面を閉じて次の層へ */
  dismissLootReview() {
    if (!this.pendingLootReview) return;
    this.pendingLootReview = false;
    this.status = this.pendingLootReturn;
    this.last = performance.now();
    this.pushSnap();
  }

  /** 倉庫の全装備を一括解体（装備中は対象外） */
  reviewSellAll(): number {
    const equipped = new Set(Object.values(this.equipment).filter(Boolean).map((i) => i!.id));
    const n = this.sellAllFromStash([...equipped]);
    if (this.pendingLootReview && this.stash.length === 0) this.dismissLootReview();
    return n;
  }

  private nextFloor() {
    const endless = this.runConfig.mode === "endless";
    if (!endless && this.floor >= TRUE_FLOOR) { this.finish(true); return; }

    this.settleFloorDrops();

    this.floor++;
    const heal = this.derived.maxHp * 0.22;
    if (!this.runConfig.modifier?.noFloorHeal) this.healPlayer(heal);
    this.buildFloor();
    this.log(`第${this.floor}層${this.isBossFloor() ? " — 主の気配…" : ""}`, this.isBossFloor() ? "#ff5c6e" : "#7ce8ff");
    if (this.floor % 5 === 0) this.energy = this.energyMax;
  }

  /* ---------------------------------------------------------------- fx */

  private spark(x: number, y: number, color: string, speed: number) {
    const a = rnd(this.fxRng, 0, 7);
    const s = rnd(this.fxRng, 0.3, 1) * speed;
    this.parts.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, life: rnd(this.fxRng, 0.22, 0.6), max: 0.6, r: rnd(this.fxRng, 1.6, 4.2), color, kind: "spark", drag: 0.9 });
  }

  private text(x: number, y: number, t: string, color: string, size: number) {
    if (this.texts.length > 90) this.texts.shift();
    this.texts.push({ x, y, vy: -58, life: 0.78, text: t, color, size });
  }

  log(t: string, c = "#cfd8e6") { this.h.onLog(t, c); }

  /* ------------------------------------------------------- progression */

  private gainXp(v: number) {
    if (this.status === "dead" || this.status === "win" || this.hp <= 0) return;
    this.xp += Math.round(Math.max(0, v) * this.derived.xpMult * (this.runConfig.modifier?.xpRate ?? 1));
    let gained = 0;
    while (this.xp >= xpForLevel(this.level) && this.level < 999) {
      this.xp -= xpForLevel(this.level); this.level++; gained++;
      if (this.relicFlags.has("twin")) {
        this.twinLvCounter++;
        if (this.twinLvCounter >= 10) {
          this.twinLvCounter = 0;
          this.mods.projectiles = (this.mods.projectiles ?? 0) + 1;
          this.mods.damagePct = (this.mods.damagePct ?? 0) + 10;
          this.log("双魂の盃 ─ 追加弾+1・攻撃力+10%", "#7cffb2");
        }
      }
    }
    if (!gained) return;
    this.recompute();
    this.healPlayer(this.derived.maxHp * 0.16 * gained);
    if (this.runConfig.mode !== "covenant") this.pendingBoons += gained;
    playSfx("levelup");
    this.log(`Lv${this.level} ─ 属性ポイント +${gained}（Escで配分）`, "#dfc78f");
    this.rings.push({ x: this.px, y: this.py, r: 10, max: 240, life: 0.65, color: "#ffd23c", width: 8 });
  }

  chooseBoon(b: Boon) {
    if (this.status !== "levelup" || !this.boonChoices.some((x) => x.id === b.id)) return;
    this.boonChoices = [];
    if (b.mods) this.mods = sumMods([this.mods, b.mods]);
    if (b.ability) this.abilities[b.ability] = (this.abilities[b.ability] ?? 0) + 1;
    this.boonIds.push(b.name);
    const before = this.derived.maxHp;
    this.recompute();
    if (this.derived.maxHp > before) this.hp += this.derived.maxHp - before;
    this.status = "playing";
    this.last = performance.now();
    this.text(this.px, this.py - 50, b.name, "#ffd23c", 22);
    this.log(`祝福獲得 — ${b.name}`, "#ffd23c");
    this.pushSnap();
  }

  private openLoot(it: Item) {
    if (this.status !== "playing" && this.status !== "shop") return;
    this.lootReturn = this.status;
    this.blur();
    this.pendingLoot = it;
    this.status = "loot";
    playSfx("pickup");
    this.h.onLoot(it, this.equipment[it.slot]);
    this.pushSnap();
  }

  equipPending() {
    if (this.status !== "loot" || !this.pendingLoot) return;
    const it = this.pendingLoot;
    if (!it) return;
    const old = this.equipment[it.slot];
    this.equipment[it.slot] = it;
    const before = this.derived.maxHp;
    this.recompute();
    if (this.derived.maxHp > before) this.hp += this.derived.maxHp - before;
    playSfx("equip");
    if (old) {
      if (this.stash.length < this.stashCap) {
        this.stash.push(old);
        this.log(`${old.name} を倉庫へ収納した`, "#8d86a3");
      } else {
        let sell = Math.round(old.power * 0.5 + old.ilvl * 2);
        sell = Math.round(sell * this.derived.goldMult);
        this.gold += sell;
        this.log(`倉庫が満杯のため ${old.name} を売却 (+${sell}G)`, "#f5c542");
      }
    }
    this.pendingLoot = null;
    this.status = this.lootReturn;
    this.last = performance.now();
    this.pushSnap();
  }

  discardPending() {
    if (this.status !== "loot" || !this.pendingLoot) return;
    const it = this.pendingLoot;
    if (it) {
      this.gold += Math.round((4 + it.power * 0.6) * this.derived.goldMult);
      for (let i = 0; i < 8; i++) this.spark(this.px, this.py, "#ffd23c", 160);
      playSfx("pickup", { pitch: 0.7 });
    }
    this.pendingLoot = null;
    this.status = this.lootReturn;
    this.last = performance.now();
    this.pushSnap();
  }

  /** 戦利品を倉庫へ収納（満杯なら売却にフォールバック） */
  stashPending() {
    if (this.status !== "loot" || !this.pendingLoot) return;
    const it = this.pendingLoot;
    if (it) {
      if (this.stash.length < this.stashCap) {
        this.stash.push(it);
        this.log(`${it.name} を倉庫へ収納した (${this.stash.length}/${this.stashCap})`, RARITY_META[it.rarity].color);
        playSfx("equip", { pitch: 0.85 });
      } else {
        const sell = Math.round((4 + it.power * 0.6) * this.derived.goldMult);
        this.gold += sell;
        this.log(`倉庫が満杯のため ${it.name} を売却 (+${sell}G)`, "#f5c542");
        playSfx("pickup", { pitch: 0.7 });
      }
    }
    this.pendingLoot = null;
    this.status = this.lootReturn;
    this.last = performance.now();
    this.pushSnap();
  }

  /** 倉庫のアイテムを装備（現装備は倉庫に戻る） */
  equipFromStash(id: string): boolean {
    const idx = this.stash.findIndex((i) => i.id === id);
    if (idx < 0) return false;
    const it = this.stash[idx];
    const old = this.equipment[it.slot];
    this.stash.splice(idx, 1);
    this.equipment[it.slot] = it;
    if (old) this.stash.push(old);
    const before = this.derived.maxHp;
    this.recompute();
    if (this.derived.maxHp > before) this.hp += this.derived.maxHp - before;
    playSfx("equip");
    this.pushSnap();
    return true;
  }

  /** 倉庫のアイテムを一括解体（売却） */
  sellAllFromStash(exceptIds: string[] = []): number {
    if (this.stash.length === 0) return 0;
    const skip = new Set(exceptIds);
    const keep: Item[] = [];
    let total = 0;
    let count = 0;
    for (const it of this.stash) {
      if (skip.has(it.id)) { keep.push(it); continue; }
      total += Math.round((4 + it.power * 0.6) * this.derived.goldMult);
      count++;
    }
    this.stash = keep;
    if (count > 0) {
      this.gold += total;
      this.log(`${count} 点を一括解体 (+${total.toLocaleString()}G)`, "#f5c542");
      playSfx("pickup", { pitch: 0.7 });
      this.pushSnap();
    }
    return count;
  }

  /** 解体時の価値: 1点あたりゴールドと魂の獲得量 */
  sellValue(it: Item): { gold: number; souls: number } {
    return { gold: Math.round((4 + it.power * 0.6) * this.derived.goldMult), souls: Math.max(1, Math.round(it.power * 0.05)) };
  }

  /** 倉庫のアイテムを解体してゴールド＋少量の魂を得る */
  salvageFromStash(id: string): boolean {
    const idx = this.stash.findIndex((i) => i.id === id);
    if (idx < 0) return false;
    const it = this.stash[idx];
    this.stash.splice(idx, 1);
    const { gold, souls } = this.sellValue(it);
    this.gold += gold;
    this.addSouls(souls);
    this.log(`${it.name} を解体 (+${gold}G / +${souls}◈)`, "#f5c542");
    playSfx("pickup", { pitch: 0.7 });
    this.pushSnap();
    return true;
  }

  /** 倉庫のアイテムを一括解体してゴールド＋少量の魂を得る（装備中は除外） */
  salvageAllFromStash(): number {
    const equipped = new Set(Object.values(this.equipment).filter(Boolean).map((i) => i!.id));
    if (this.stash.length === 0) return 0;
    const keep: Item[] = [];
    let gold = 0, souls = 0, count = 0;
    for (const it of this.stash) {
      if (equipped.has(it.id)) { keep.push(it); continue; }
      const v = this.sellValue(it);
      gold += v.gold; souls += v.souls; count++;
    }
    this.stash = keep;
    if (count > 0) {
      this.gold += gold;
      this.addSouls(souls);
      this.log(`${count} 点を一括解体 (+${gold.toLocaleString()}G / +${souls}◈)`, "#f5c542");
      playSfx("pickup", { pitch: 0.7 });
      this.pushSnap();
    }
    return count;
  }

  private runSouls = 0;
  addSouls(n: number) { this.runSouls += Math.max(0, n); }
  pendingRunSouls() { return this.runSouls; }

  /** 倉庫のアイテムを売却 */
  sellFromStash(id: string): boolean {
    const idx = this.stash.findIndex((i) => i.id === id);
    if (idx < 0) return false;
    const it = this.stash[idx];
    this.stash.splice(idx, 1);
    const sell = Math.round((4 + it.power * 0.6) * this.derived.goldMult);
    this.gold += sell;
    this.log(`${it.name} を売却 (+${sell}G)`, "#f5c542");
    playSfx("pickup", { pitch: 0.7 });
    this.pushSnap();
    return true;
  }

  /** 鍛造: 装備中・倉庫を問わず同部位の2アイテムを素材で融合。結果は倉庫へ（空きスロットなら即装備） */
  doForge(aId: string, bId: string): boolean {
    if (this.status !== "forge") return false;
    const findItem = (id: string): { item: Item; from: "equip" | "stash" } | null => {
      for (const s of SLOTS) {
        if (this.equipment[s]?.id === id) return { item: this.equipment[s] as Item, from: "equip" };
      }
      const st = this.stash.find((i) => i.id === id);
      return st ? { item: st, from: "stash" } : null;
    };
    const fa = findItem(aId);
    const fb = findItem(bId);
    if (!fa || !fb || fa.item.id === fb.item.id) return false;
    const result = forgeItems(this.rng, fa.item, fb.item, this.materials, this.floor);
    if (!result) return false;
    // 素材消費
    for (const [k, v] of Object.entries(result.cost) as [MaterialId, number][]) {
      this.materials[k] = Math.max(0, (this.materials[k] ?? 0) - v);
    }
    // 元アイテムを除去
    for (const f of [fa, fb]) {
      if (f.from === "equip") this.equipment[f.item.slot] = null;
      else this.stash = this.stash.filter((i) => i.id !== f.item.id);
    }
    // 結果配置: その部位が空なら即装備、そうでなければ倉庫へ
    if (!this.equipment[result.item.slot]) {
      this.equipment[result.item.slot] = result.item;
    } else {
      this.stash.push(result.item);
    }
    const before = this.derived.maxHp;
    this.recompute();
    if (this.derived.maxHp > before) this.hp += this.derived.maxHp - before;
    const color = RARITY_META[result.item.rarity].color;
    playSfx("forge");
    this.text(this.px, this.py - 50, `鍛造完了!`, color, 24);
    this.log(`鍛造: ${result.item.name} (${RARITY_META[result.item.rarity].label}) — ${result.bonusAffixes > 0 ? `+${result.bonusAffixes} 新特性` : "強化"}`, color);
    this.forgeResult = result.item;
    this.pushSnap();
    return true;
  }

  getEquippedItems(): Item[] {
    return Object.values(this.equipment).filter(Boolean) as Item[];
  }

  getStashItems(): Item[] {
    return [...this.stash];
  }

  summarize(victory: boolean, abandoned = false): RunSummary {
    const baseScore =
      this.floor * 620 + this.kills * 14 + this.level * 110 + this.gold * 1.6 +
      this.totalDamage * 0.06 + (victory ? 12000 : 0) + this.bestCombo * 2 +
      (this.isDanmakuMode() ? this.grazeCount * 8 + this.bulletsReflected * 4 : 0);
    const modeMul = RUN_MODES[(this.runConfig.mode === "daily" ? "standard" : this.runConfig.mode) as keyof typeof RUN_MODES]?.scoreMul ?? 1;
    const diffMul = DIFFICULTIES[this.runConfig.difficulty].scoreMul;
    const score = Math.round(baseScore * (this.runConfig.modifier?.scoreRate ?? 1) * modeMul * diffMul);
    const eq = Object.values(this.equipment).filter(Boolean) as Item[];
    const best = eq.length ? eq.slice().sort((a, b) => b.power - a.power)[0] : null;
    return {
      floor: this.floor, level: this.level, kills: this.kills, gold: this.gold, score,
      durationSec: Math.round(this.time), victory, abandoned,
      weaponName: this.equipment.weapon?.name ?? null,
      rarity: this.equipment.weapon?.rarity ?? null,
      build: this.boonIds.slice(-24),
      artifactsFound: this.artifactsFound,
      dps: Math.round(this.totalDamage / Math.max(1, this.time)),
      bestItem: best,
      challengeMode: this.runConfig.mode,
      challengeId: this.runConfig.challengeId,
      challengeName: this.runConfig.challengeName,
      challengeDay: this.runConfig.challengeDay,
      difficulty: this.runConfig.difficulty,
      relics: [...this.relics],
      trialSigils: [...this.trial.ids], attributes: { ...this.attributes },
      gatesDestroyed: this.gatesDestroyed, weaponArtUses: this.weaponArtUses,
      grazeCount: this.grazeCount, bulletsReflected: this.bulletsReflected,
      eventsCompleted: this.eventsCompleted,
      bossesKilled: this.bossesKilled,
      bestCombo: this.bestCombo,
      perfectDodges: this.perfectDodges,
      finalWeaponClass: this.equipment.weapon?.weaponClass ?? null,
      finalWeaponTrait: this.equipment.weapon?.weaponTrait ?? null,
      materials: { ...this.materials },
      trueCleared: this.trueCleared,
      pinnacleCleared: this.pinnacleCleared,
      canKeepItem: this.pinnacleCleared || this.trueCleared || (victory && !abandoned),
      finalEquipment: [...(Object.values(this.equipment).filter(Boolean) as Item[]), ...this.stash]
        .sort((a, b) => b.power - a.power)
        .slice(0, 24),
    };
  }

  abandon() {
    if (this.status === "dead" || this.status === "win") return;
    // 真・最終決戦の間は退路が存在しない
    if (this.isTrueFloor()) {
      this.log("退路はない — 《ニヒル・ゼロ》との決着をつめるしかない", "#ff5c6e");
      this.text(this.px, this.py - 60, "退路なし", "#ff5c6e", 22);
      this.pushSnap();
      return;
    }
    this.status = "dead";
    this.h.onEnd(this.summarize(false, true));
    this.pushSnap();
  }

  private finish(victory: boolean) {
    if (this.status === "dead" || this.status === "win") return;
    this.status = victory ? "win" : "dead";
    this.shake = victory ? 10 : 24;
    this.flashFx = 1;
    playSfx(victory ? "levelup" : "death");
    this.h.onEnd(this.summarize(victory, false));
    this.pushSnap();
  }

  private pushSnap() {
    const dps = this.dmgWindow.reduce((a, b) => a + b.amount, 0) / 3;
    this.h.onSnapshot({
      status: this.status, hp: Math.max(0, this.hp), maxHp: this.derived.maxHp ?? 1,
      level: this.level, xp: this.xp, xpNeed: xpForLevel(this.level),
      floor: this.floor, kills: this.kills, gold: this.gold, time: this.time, combo: this.combo,
      energy: this.energy, dashCd: Math.max(0, this.dashCd), dashMax: this.derived.dashCd ?? 1,
      atkCd: Math.max(0, this.atkCd), atkMax: 1 / Math.max(0.05, this.derived.attackSpeed ?? 1),
      wave: this.wave, waveTotal: this.waveTotal, left: this.enemies.length + this.toSpawn,
      bossName: this.boss?.name ?? "", bossHp: this.boss ? Math.max(0, this.boss.hp) : 0,
      bossMax: this.boss?.maxHp ?? 1, derived: this.derived, abilities: { ...this.abilities },
      portalOpen: !!this.portal, dps: Math.round(dps),
      equipment: { ...this.equipment }, boons: [...this.boonIds],
      autoAim: this.autoAim, isTouch: this.isTouch,
      gamepadConnected: this.gamepadConnected,
      killStreak: this.time - this.lastKillT < 1.3 ? this.killStreak : 0,
      curseT: Math.max(0, this.curseT),
      bulwark: this.bulwark,
      relics: this.relics.map((id) => {
        const rel = RELICS.find((x) => x.id === id);
        return rel ? { name: rel.name, color: rel.color, icon: rel.icon } : null;
      }).filter(Boolean) as { name: string; color: string; icon: string }[],
      difficultyName: DIFFICULTIES[this.runConfig.difficulty].name,
      difficultyColor: DIFFICULTIES[this.runConfig.difficulty].color,
      trialSigils: [...this.trial.ids], trialSoulMultiplier: this.trial.soulMultiplier,
      attributes: { ...this.attributes }, attributePoints: availablePoints(this.level, this.attributes),
      weaponArtName: WEAPON_ARTS[this.weaponStyle()].name,
      weaponArtDescription: WEAPON_ARTS[this.weaponStyle()].description,
      weaponArtColor: WEAPON_ARTS[this.weaponStyle()].color,
      weaponArtGlyph: WEAPON_ARTS[this.weaponStyle()].glyph,
      weaponArtGauge: this.weaponArtGauge,
      weaponArtUses: this.weaponArtUses, absoluteDomainCd: this.absoluteDomainCd, gatesDestroyed: this.gatesDestroyed,
      stash: [...this.stash],
      materials: { ...this.materials },
      weaponTraitName: this.equipment.weapon?.weaponTrait ? (WEAPON_TRAITS.find((t) => t.id === this.equipment.weapon?.weaponTrait)?.name ?? null) : null,
      weaponTraitDesc: this.equipment.weapon?.weaponTrait ? (WEAPON_TRAITS.find((t) => t.id === this.equipment.weapon?.weaponTrait)?.desc ?? null) : null,
      weaponTraitColor: this.equipment.weapon?.weaponTrait ? (WEAPON_TRAITS.find((t) => t.id === this.equipment.weapon?.weaponTrait)?.color ?? null) : null,
      weaponClassName: this.equipment.weapon?.weaponClass ? (WEAPON_CLASSES[this.equipment.weapon.weaponClass]?.name ?? null) : null,
      stashCount: this.stash.length, stashCap: this.stashCap,
      truePhaseIdx: this.truePhaseIdx,
      truePhaseLabel: this.trueCleared ? "討伐済" : this.truePhaseIdx > 0 ? TRUE_BOSS_PHASES[this.truePhaseIdx]?.label ?? null : null,
      pinnaclePhaseIdx: this.pinnaclePhaseIdx,
      pinnaclePhaseLabel: this.pinnacleCleared ? "終焉神 討伐" : (this.boss?.pinnacleBoss ? PINNACLE_PHASES[this.pinnaclePhaseIdx]?.label ?? null : null),
      cataclysmCountdown: (this.boss?.cataclysmT !== undefined && this.boss.cataclysmT > 0) ? this.boss.cataclysmT : null,
      cataclysmStaggerLeft: (this.boss?.cataclysmStagger !== undefined && this.boss.cataclysmStagger > 0) ? this.boss.cataclysmStagger : null,
      totalGearPower: Object.values(this.equipment).reduce((sum, item) => sum + (item?.power ?? 0), 0),
      heirloomSource: this.heirloomSource,
      zoneName: this.zone?.name ?? null,
      zoneColor: this.zone?.color ?? null,
      zoneDesc: this.zone?.desc ?? null,
      passives: [...this.passives],
      mutationName: this.mutation?.name ?? null,
      mutationColor: this.mutation?.color ?? null,
      mutationDesc: this.mutation?.desc ?? null,
      shop: this.shop,
      charge: this.charging ? clamp((this.chargeT - 0.32) / (1.2 - 0.32), 0, 1) : 0,
      counterReady: this.counterWindowT > 0,
      fateLabel: this.activeFate ? this.activeFateLabel : null,
      fateT: Math.max(0, this.activeFateT),
      portalIdle: this.portal ? this.portalIdleT : 0,
      focus: this.isDanmakuMode() && this.focusHeld,
      grazeCount: this.grazeCount,
      grazeCombo: this.grazeCombo,
      bombStock: this.bombStock,
      bombCharge: this.bombCharge,
      deathbombT: this.deathbombT,
      spellName: this.isDanmakuMode() && this.spellIndex >= 0 ? ["月符・三重螺旋", "星符・輪廻の花", "夜符・狙撃と虚空", "幻符・回転風車"][this.spellIndex] : null,
      spellAnnounceT: this.spellAnnounceT,
      bulletsReflected: this.bulletsReflected,
    });
  }

  /* -------------------------------------------------------------- render */

  private render(dt: number) {
    const g = this.ctx;
    g.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    g.clearRect(0, 0, this.w, this.hgt);
    g.fillStyle = "#05040a";
    g.fillRect(0, 0, this.w, this.hgt);
    if (!this.roomCanvas) return;

    const sh = this.shake;
    const ox = sh > 0 ? rnd(this.fxRng, -sh, sh) : 0;
    const oy = sh > 0 ? rnd(this.fxRng, -sh, sh) : 0;
    g.save();
    g.translate(this.w / 2 + ox, this.hgt / 2 + oy);
    g.scale(this.zoom, this.zoom);
    g.translate(-this.camX, -this.camY);

    g.drawImage(this.roomCanvas, 0, 0);

    // hazards
    for (const hz of this.hazards) {
      const t = this.time * 2;
      g.save();
      const grd = g.createRadialGradient(hz.x, hz.y, 4, hz.x, hz.y, hz.r);
      grd.addColorStop(0, `rgba(120,255,90,${0.22 + Math.sin(t) * 0.05})`);
      grd.addColorStop(1, "rgba(40,120,30,0)");
      g.fillStyle = grd;
      g.beginPath(); g.arc(hz.x, hz.y, hz.r, 0, 7); g.fill();
      g.strokeStyle = "rgba(150,255,120,.35)"; g.lineWidth = 2;
      g.beginPath(); g.arc(hz.x, hz.y, hz.r * (0.86 + Math.sin(t) * 0.05), 0, 7); g.stroke();
      g.restore();
    }

    // portal
    if (this.portal) {
      const p = this.portal;
      g.save();
      g.translate(p.x, p.y);
      const grd = g.createRadialGradient(0, 0, 4, 0, 0, 78);
      grd.addColorStop(0, "rgba(180,240,255,.95)");
      grd.addColorStop(0.4, "rgba(90,190,255,.4)");
      grd.addColorStop(1, "rgba(40,90,200,0)");
      g.fillStyle = grd; g.beginPath(); g.arc(0, 0, 78, 0, 7); g.fill();
      g.rotate(p.t * 1.4);
      g.strokeStyle = "#bfeaff"; g.lineWidth = 4;
      for (let i = 0; i < 3; i++) {
        g.beginPath(); g.ellipse(0, 0, 40 - i * 9, 22 - i * 5, (i * Math.PI) / 3, 0, 7); g.stroke();
      }
      g.restore();
      // 追っ手の到来が近づくと赤く脈動する警告輪
      if (!this.isBossFloor() && this.floor < TRUE_FLOOR && this.portalRushWaves < 3) {
        const rushAt = 14 + this.portalRushWaves * 10;
        const left = rushAt - this.portalIdleT;
        if (left < 6) {
          const urg = clamp(1 - left / 6, 0, 1);
          g.save();
          g.globalAlpha = 0.25 + urg * 0.45 * (0.5 + Math.sin(this.time * (6 + urg * 10)) * 0.5);
          g.strokeStyle = "#ff5c5c"; g.lineWidth = 6 + urg * 6;
          g.beginPath(); g.arc(p.x, p.y, 96 + urg * 24, 0, 7); g.stroke();
          g.restore();
        }
      }
    }

    drawTraps(g, this.traps, this.time, this.px, this.py, this.uiFont);
    for (const marker of this.artTelegraphs) {
      g.save();
      g.fillStyle = `${marker.color}22`; g.strokeStyle = marker.color; g.lineWidth = 3;
      g.beginPath(); g.arc(marker.x, marker.y, marker.radius, 0, Math.PI * 2); g.fill(); g.stroke();
      const progress = 1 - clamp(marker.remaining / marker.max, 0, 1);
      g.setLineDash([12, 7]); g.lineDashOffset = -this.time * 30;
      g.beginPath(); g.arc(marker.x, marker.y, marker.radius * progress, 0, Math.PI * 2); g.stroke();
      g.setLineDash([]); g.fillStyle = "#ffffff"; g.textAlign = "center"; g.font = `15px ${this.uiFont}`;
      g.fillText(`着弾 ${Math.max(0, marker.remaining).toFixed(1)}s`, marker.x, marker.y);
      g.restore();
    }

    // ground items
    for (const gi of this.gitems) {
      const c = RARITY_META[gi.item.rarity].color;
      const bob = Math.sin(gi.t * 3) * 4;
      g.save();
      g.globalCompositeOperation = "lighter";
      const grd = g.createLinearGradient(gi.x, gi.y - 200, gi.x, gi.y + 12);
      grd.addColorStop(0, "rgba(0,0,0,0)");
      grd.addColorStop(1, c + "cc");
      g.fillStyle = grd;
      g.fillRect(gi.x - 7, gi.y - 200, 14, 212);
      g.restore();
      g.save();
      g.translate(gi.x, gi.y + bob);
      g.fillStyle = c;
      g.strokeStyle = "#0a0812"; g.lineWidth = 3;
      g.beginPath();
      for (let i = 0; i < 6; i++) {
        const a = (i / 6) * Math.PI * 2 + gi.t;
        const rr = 13;
        i ? g.lineTo(Math.cos(a) * rr, Math.sin(a) * rr) : g.moveTo(Math.cos(a) * rr, Math.sin(a) * rr);
      }
      g.closePath(); g.fill(); g.stroke();
      g.restore();
    }

    // orbs
    for (const o of this.orbs) {
      const c = o.kind === "xp" ? "#5cc8ff" : o.kind === "gold" ? "#ffd23c" : o.kind === "heart" ? "#ff4d6d" : "#c58bff";
      g.save();
      g.globalCompositeOperation = "lighter";
      const rr = o.kind === "xp" ? 6 : o.kind === "gold" ? 7 : 11;
      const grd = g.createRadialGradient(o.x, o.y, 0, o.x, o.y, rr * 3);
      grd.addColorStop(0, c); grd.addColorStop(1, "rgba(0,0,0,0)");
      g.fillStyle = grd; g.globalAlpha = 0.5;
      g.beginPath(); g.arc(o.x, o.y, rr * 3, 0, 7); g.fill();
      g.globalAlpha = 1; g.fillStyle = c;
      g.beginPath(); g.arc(o.x, o.y, rr, 0, 7); g.fill();
      g.restore();
    }

    // obstacles
    for (const o of this.obstacles) {
      g.save();
      g.fillStyle = "rgba(0,0,0,.5)";
      g.beginPath(); g.ellipse(o.x + 5, o.y + 9, o.r, o.r * 0.62, 0, 0, 7); g.fill();
      const grd = g.createLinearGradient(o.x - o.r, o.y - o.r, o.x + o.r, o.y + o.r);
      grd.addColorStop(0, "#3a3550"); grd.addColorStop(1, "#171425");
      g.fillStyle = grd;
      g.beginPath(); g.arc(o.x, o.y, o.r, 0, 7); g.fill();
      g.strokeStyle = "rgba(160,150,220,.25)"; g.lineWidth = 3; g.stroke();
      g.restore();
    }

    // destructibles
    for (const d of this.destructibles) {
      if (d.dead) continue;
      g.save();
      g.shadowBlur = 10;
      
      // Shadow
      g.fillStyle = "rgba(0,0,0,.45)";
      g.beginPath(); g.ellipse(d.x + 2, d.y + 5, d.r, d.r * 0.55, 0, 0, 7); g.fill();

      if (d.kind === "red_crystal") {
        g.shadowColor = "#ff4d6d";
        const grd = g.createRadialGradient(d.x, d.y, 2, d.x, d.y, d.r);
        grd.addColorStop(0, "#ff7c8c");
        grd.addColorStop(1, "#ff1e42");
        g.fillStyle = grd;
        // Draw a crystalline diamond cluster
        g.beginPath();
        g.moveTo(d.x, d.y - d.r);
        g.lineTo(d.x + d.r * 0.8, d.y - d.r * 0.2);
        g.lineTo(d.x + d.r * 0.5, d.y + d.r * 0.8);
        g.lineTo(d.x - d.r * 0.5, d.y + d.r * 0.8);
        g.lineTo(d.x - d.r * 0.8, d.y - d.r * 0.2);
        g.closePath(); g.fill();
        g.strokeStyle = "#ffffff"; g.lineWidth = 1.5; g.stroke();
      } else if (d.kind === "blue_flask") {
        g.shadowColor = "#8be9ff";
        g.fillStyle = "#1e3c5a";
        g.strokeStyle = "#8be9ff"; g.lineWidth = 2;
        // Bottle shape
        g.beginPath();
        g.moveTo(d.x - d.r * 0.4, d.y - d.r);
        g.lineTo(d.x + d.r * 0.4, d.y - d.r);
        g.lineTo(d.x + d.r * 0.4, d.y - d.r * 0.6);
        g.lineTo(d.x + d.r, d.y - d.r * 0.2);
        g.lineTo(d.x + d.r, d.y + d.r * 0.8);
        g.lineTo(d.x - d.r, d.y + d.r * 0.8);
        g.lineTo(d.x - d.r, d.y - d.r * 0.2);
        g.lineTo(d.x - d.r * 0.4, d.y - d.r * 0.6);
        g.closePath(); g.fill(); g.stroke();
        // Liquid inside
        g.fillStyle = "rgba(139, 233, 255, 0.42)";
        g.beginPath();
        g.moveTo(d.x - d.r * 0.8, d.y + d.r * 0.1);
        g.lineTo(d.x + d.r * 0.8, d.y + d.r * 0.1);
        g.lineTo(d.x + d.r * 0.8, d.y + d.r * 0.7);
        g.lineTo(d.x - d.r * 0.8, d.y + d.r * 0.7);
        g.closePath(); g.fill();
      } else if (d.kind === "shrine_fate") {
        g.shadowColor = "#f5c542";
        // Ornate pedestal
        g.fillStyle = "#2c2444";
        g.strokeStyle = "#f5c542"; g.lineWidth = 2;
        g.beginPath();
        g.moveTo(d.x - d.r * 0.8, d.y + d.r);
        g.lineTo(d.x + d.r * 0.8, d.y + d.r);
        g.lineTo(d.x + d.r * 0.4, d.y - d.r * 0.4);
        g.lineTo(d.x - d.r * 0.4, d.y - d.r * 0.4);
        g.closePath(); g.fill(); g.stroke();
        // Floating golden star/rune
        const bob = Math.sin(this.time * 3 + d.t) * 5 - 8;
        g.fillStyle = "#ffd23c";
        g.beginPath();
        g.arc(d.x, d.y - d.r + bob, 5, 0, 7);
        g.fill();
        g.strokeStyle = "#ffffff"; g.lineWidth = 1;
        g.beginPath();
        g.moveTo(d.x, d.y - d.r - 8 + bob);
        g.lineTo(d.x + 8, d.y - d.r + bob);
        g.lineTo(d.x, d.y - d.r + 8 + bob);
        g.lineTo(d.x - 8, d.y - d.r + bob);
        g.closePath(); g.stroke();
      }

      // Small health bar if damaged
      if (d.hp < d.maxHp && d.maxHp > 1) {
        const w = d.r * 1.8;
        g.fillStyle = "rgba(0,0,0,.7)";
        g.fillRect(d.x - w / 2, d.y - d.r - 8, w, 4);
        g.fillStyle = "#ff4d6d";
        g.fillRect(d.x - w / 2, d.y - d.r - 8, w * clamp(d.hp / d.maxHp, 0, 1), 4);
      }
      g.restore();
    }

    // particles (behind)
    g.save();
    g.globalCompositeOperation = "lighter";
    for (const p of this.parts) {
      const a = clamp(p.life / p.max, 0, 1);
      if (p.kind === "ghost") {
        g.globalAlpha = a * 0.35; g.fillStyle = p.color;
        g.beginPath(); g.arc(p.x, p.y, p.r, 0, 7); g.fill();
      } else if (p.kind === "burst") {
        g.globalAlpha = a * 0.5; g.fillStyle = p.color;
        g.beginPath(); g.arc(p.x, p.y, p.r * (2 - a), 0, 7); g.fill();
      } else {
        g.globalAlpha = a; g.fillStyle = p.color;
        g.beginPath(); g.arc(p.x, p.y, p.r * a, 0, 7); g.fill();
      }
    }
    g.globalAlpha = 1;
    g.restore();

    // 予兆（突進・ボス弾幕）
    for (const e of [...this.enemies]) {
      if (e.spawnT > 0) continue;
      if (e.ai === "charge" && e.st === 1) {
        const p = 1 - Math.max(0, e.stT) / 0.55;
        g.save();
        g.translate(e.x, e.y); g.rotate(e.angle);
        g.globalAlpha = 0.18 + p * 0.3;
        g.fillStyle = "#ff3b5c";
        g.beginPath(); g.moveTo(0, -e.r); g.lineTo(460, -e.r * 0.8); g.lineTo(460, e.r * 0.8); g.lineTo(0, e.r); g.closePath(); g.fill();
        g.globalAlpha = 0.8; g.strokeStyle = "#ff8a9a"; g.lineWidth = 2;
        g.beginPath(); g.moveTo(0, 0); g.lineTo(460 * p, 0); g.stroke();
        g.restore();
      }
      if (e.boss && e.fire < 0.55 && e.fire > 0) {
        const p = 1 - e.fire / 0.55;
        g.save();
        g.globalAlpha = 0.25 + Math.sin(this.time * 18) * 0.12;
        g.strokeStyle = "#ff3b5c"; g.lineWidth = 6;
        g.setLineDash([18, 12]);
        g.beginPath(); g.arc(e.x, e.y, 90 + p * 170, 0, 7); g.stroke();
        g.restore();
      }
      if (e.ai === "bomber") {
        const dd = Math.hypot(e.x - this.px, e.y - this.py);
        if (dd < 220) {
          g.save();
          g.globalAlpha = 0.14 + Math.sin(this.time * 20) * 0.08;
          g.fillStyle = "#ffb03c";
          g.beginPath(); g.arc(e.x, e.y, 118, 0, 7); g.fill();
          g.restore();
        }
      }
    }

    // enemies
    for (const e of this.enemies) this.drawEnemy(g, e);

    // projectiles
    g.save();
    g.globalCompositeOperation = "lighter";
    for (const p of this.projs) {
      const grd = g.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 4);
      grd.addColorStop(0, "#ffffff"); grd.addColorStop(0.35, p.color); grd.addColorStop(1, "rgba(0,0,0,0)");
      g.fillStyle = grd;
      g.beginPath(); g.arc(p.x, p.y, p.r * 4, 0, 7); g.fill();
      g.fillStyle = "#fff";
      g.beginPath(); g.arc(p.x, p.y, p.r * 0.55, 0, 7); g.fill();
    }
    for (const p of this.eprojs) {
      const grd = g.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 3.4);
      grd.addColorStop(0, "#fff"); grd.addColorStop(0.4, p.color); grd.addColorStop(1, "rgba(0,0,0,0)");
      g.fillStyle = grd;
      g.beginPath(); g.arc(p.x, p.y, p.r * 3.4, 0, 7); g.fill();
    }
    g.restore();

    // beams
    g.save();
    g.globalCompositeOperation = "lighter";
    for (const b of this.beams) {
      g.strokeStyle = b.color; g.lineWidth = 3 + b.life * 14; g.globalAlpha = clamp(b.life * 4, 0, 1);
      g.beginPath();
      const seg = 6;
      for (let i = 0; i <= seg; i++) {
        const t2 = i / seg;
        const x = b.x1 + (b.x2 - b.x1) * t2 + (i > 0 && i < seg ? rnd(this.fxRng, -14, 14) : 0);
        const y = b.y1 + (b.y2 - b.y1) * t2 + (i > 0 && i < seg ? rnd(this.fxRng, -14, 14) : 0);
        i ? g.lineTo(x, y) : g.moveTo(x, y);
      }
      g.stroke();
    }
    g.restore();

    // ボス衝撃波（拡張リング）
    g.save();
    g.globalCompositeOperation = "lighter";
    for (const sw of this.shockwaves) {
      g.globalAlpha = clamp(sw.life, 0, 1) * 0.85;
      g.strokeStyle = sw.color; g.lineWidth = 9;
      g.shadowColor = sw.color; g.shadowBlur = 18;
      g.beginPath(); g.arc(sw.x, sw.y, sw.r, 0, 7); g.stroke();
      g.lineWidth = 3; g.globalAlpha *= 0.6;
      g.beginPath(); g.arc(sw.x, sw.y, sw.r * 0.9, 0, 7); g.stroke();
    }
    g.restore();

    // 追跡ビーム
    g.save();
    g.globalCompositeOperation = "lighter";
    for (const b of this.homingBeams) {
      const ex = b.x + Math.cos(b.angle) * b.len;
      const ey = b.y + Math.sin(b.angle) * b.len;
      const a = clamp(b.life, 0, 1);
      // 外側グロー
      g.globalAlpha = a * 0.4; g.strokeStyle = b.color; g.lineWidth = b.width * 2.4; g.lineCap = "round";
      g.beginPath(); g.moveTo(b.x, b.y); g.lineTo(ex, ey); g.stroke();
      // コア
      g.globalAlpha = a * 0.9; g.lineWidth = b.width * 0.7;
      g.beginPath(); g.moveTo(b.x, b.y); g.lineTo(ex, ey); g.stroke();
      g.globalAlpha = a; g.lineWidth = b.width * 0.22; g.strokeStyle = "#ffffff";
      g.beginPath(); g.moveTo(b.x, b.y); g.lineTo(ex, ey); g.stroke();
    }
    g.restore();

    // 深羅針盤（遺物）：地上の最強装備へ光の道
    if (this.relicFlags.has("compass") && this.gitems.length > 0) {
      let best: GItem | null = null;
      for (const gi of this.gitems) if (!best || gi.item.power > best.item.power) best = gi;
      if (best) {
        g.save();
        g.globalCompositeOperation = "lighter";
        g.globalAlpha = 0.5 + Math.sin(this.time * 4) * 0.2;
        g.strokeStyle = "#8be9ff"; g.lineWidth = 2.5;
        g.setLineDash([10, 10]); g.lineDashOffset = -this.time * 60;
        g.beginPath(); g.moveTo(this.px, this.py); g.lineTo(best.x, best.y); g.stroke();
        g.setLineDash([]);
        g.globalAlpha = 0.9;
        g.font = `16px ${this.uiFont}`; g.textAlign = "center";
        g.fillStyle = "#8be9ff";
        g.fillText("◆", best.x, best.y - 26 - Math.sin(this.time * 5) * 4);
        g.restore();
      }
    }

    // rings
    g.save();
    g.globalCompositeOperation = "lighter";
    for (const r2 of this.rings) {
      g.globalAlpha = clamp(r2.life * 2.2, 0, 1) * 0.8;
      g.strokeStyle = r2.color; g.lineWidth = r2.width;
      g.beginPath(); g.arc(r2.x, r2.y, r2.r, 0, 7); g.stroke();
    }
    g.restore();

    // slashes
    g.save();
    g.globalCompositeOperation = "lighter";
    for (const s of this.slashes) {
      const t = 1 - s.life / s.max;
      g.globalAlpha = clamp(1 - t, 0, 1);
      const grd = g.createRadialGradient(s.x, s.y, s.reach * 0.35, s.x, s.y, s.reach);
      grd.addColorStop(0, "rgba(255,255,255,0)");
      grd.addColorStop(0.72, s.color);
      grd.addColorStop(1, "rgba(255,255,255,0)");
      g.fillStyle = grd;
      g.beginPath();
      g.moveTo(s.x, s.y);
      const ccw = s.arc < 0;
      const arcAbs = Math.abs(s.arc);
      const a0 = s.a - s.arc / 2 + t * s.arc * 0.25;
      g.arc(s.x, s.y, s.reach, a0, a0 + s.arc * (arcAbs > 6 ? 1 : 0.9), ccw);
      g.closePath(); g.fill();
      g.strokeStyle = "#ffffff"; g.lineWidth = 3; g.globalAlpha *= 0.7;
      g.beginPath(); g.arc(s.x, s.y, s.reach * 0.96, a0, a0 + s.arc * 0.9, ccw); g.stroke();
    }
    g.restore();

    // player
    this.drawPlayer(g);
    // 東方風FOCUS: 小さな実当たり判定と低速モードを可視化
    if (this.isDanmakuMode() && (this.focusHeld || this.keys.has("control") || this.keys.has("z") || this.gamepadFocus)) {
      g.save();
      g.globalCompositeOperation = "lighter";
      g.shadowBlur = 16; g.shadowColor = "#ffffff";
      g.strokeStyle = "#bfeaff"; g.lineWidth = 2;
      g.beginPath(); g.arc(this.px, this.py, 5, 0, Math.PI * 2); g.stroke();
      g.fillStyle = "#ffffff";
      g.beginPath(); g.arc(this.px, this.py, 2.5, 0, Math.PI * 2); g.fill();
      g.globalAlpha = 0.5 + Math.sin(this.time * 8) * 0.2;
      g.strokeStyle = "#7ce8ff"; g.lineWidth = 1.5;
      g.beginPath(); g.arc(this.px, this.py, 14, 0, Math.PI * 2); g.stroke();
      g.restore();
    }

    // 溜め攻撃リング（足元に進行度を描く）
    if (this.charging) {
      const ratio = clamp((this.chargeT - 0.32) / (1.2 - 0.32), 0, 1);
      const full = ratio >= 0.98;
      g.save();
      g.translate(this.px, this.py);
      g.rotate(-Math.PI / 2);
      g.lineWidth = 5;
      g.strokeStyle = "rgba(255,255,255,.12)";
      g.beginPath(); g.arc(0, 0, 34, 0, Math.PI * 2); g.stroke();
      g.strokeStyle = full ? "#ffffff" : this.cls.color;
      g.shadowColor = full ? "#ffffff" : this.cls.color;
      g.shadowBlur = full ? 18 : 8;
      g.beginPath(); g.arc(0, 0, 34, 0, Math.PI * 2 * ratio); g.stroke();
      if (full) {
        g.globalAlpha = 0.45 + Math.sin(this.time * 20) * 0.25;
        g.beginPath(); g.arc(0, 0, 42 + Math.sin(this.time * 12) * 3, 0, Math.PI * 2); g.stroke();
      }
      g.restore();
    }
    // 反撃窓: 翡翠の輪が脈動
    if (this.counterWindowT > 0 && this.status === "playing") {
      g.save();
      g.globalAlpha = clamp(this.counterWindowT * 1.6, 0, 0.8);
      g.strokeStyle = "#7cffd0"; g.lineWidth = 2.5;
      g.setLineDash([6, 6]);
      g.lineDashOffset = -this.time * 60;
      g.beginPath(); g.arc(this.px, this.py, 30, 0, Math.PI * 2); g.stroke();
      g.restore();
    }

    // floating text
    g.save();
    g.textAlign = "center";
    for (const t of this.texts) {
      g.globalAlpha = clamp(t.life * 1.6, 0, 1);
      g.font = `${t.size}px ${this.uiFont}`;
      g.lineWidth = 4; g.strokeStyle = "rgba(0,0,0,.75)";
      g.strokeText(t.text, t.x, t.y);
      g.fillStyle = t.color;
      g.fillText(t.text, t.x, t.y);
    }
    g.restore();

    g.restore();

    // ---- screen space overlays
    if (this.flashFx > 0) {
      g.fillStyle = `rgba(255,240,220,${this.flashFx * 0.28})`;
      g.fillRect(0, 0, this.w, this.hgt);
    }
    if (this.pFlash > 0) {
      g.fillStyle = `rgba(220,20,50,${this.pFlash * 0.2})`;
      g.fillRect(0, 0, this.w, this.hgt);
    }
    const vg = g.createRadialGradient(this.w / 2, this.hgt / 2, Math.min(this.w, this.hgt) * 0.32, this.w / 2, this.hgt / 2, Math.max(this.w, this.hgt) * 0.78);
    vg.addColorStop(0, "rgba(0,0,0,0)");
    vg.addColorStop(1, "rgba(0,0,0,.82)");
    g.fillStyle = vg; g.fillRect(0, 0, this.w, this.hgt);
    // 異変・闇の迷宮 — 視界の外を墨で埋める
    if (this.mutation?.darkness) {
      const sx = (this.px - this.camX) * this.zoom + this.w / 2;
      const sy = (this.py - this.camY) * this.zoom + this.hgt / 2;
      const flick = 1 + Math.sin(this.time * 1.6) * 0.04;
      const grad = g.createRadialGradient(sx, sy, 105 * this.zoom * flick, sx, sy, 420 * this.zoom * flick);
      grad.addColorStop(0, "rgba(0,0,0,0)");
      grad.addColorStop(0.55, "rgba(3,1,10,0.5)");
      grad.addColorStop(1, "rgba(0,0,0,0.965)");
      g.fillStyle = grad;
      g.fillRect(0, 0, this.w, this.hgt);
    }
    const hpRatio = this.hp / (this.derived.maxHp || 1);
    if (hpRatio < 0.32 && this.status === "playing") {
      const pulse = 0.18 + Math.sin(this.time * 7) * 0.09;
      const rg = g.createRadialGradient(this.w / 2, this.hgt / 2, Math.min(this.w, this.hgt) * 0.25, this.w / 2, this.hgt / 2, Math.max(this.w, this.hgt) * 0.7);
      rg.addColorStop(0, "rgba(0,0,0,0)");
      rg.addColorStop(1, `rgba(200,10,40,${pulse})`);
      g.fillStyle = rg; g.fillRect(0, 0, this.w, this.hgt);
    }
    // 邪眼の呪い（被ダメ+25%）のヴィネット
    if (this.curseT > 0) {
      const pulse = 0.2 + Math.sin(this.time * 6) * 0.1;
      const rg = g.createRadialGradient(this.w / 2, this.hgt / 2, Math.min(this.w, this.hgt) * 0.3, this.w / 2, this.hgt / 2, Math.max(this.w, this.hgt) * 0.75);
      rg.addColorStop(0, "rgba(0,0,0,0)");
      rg.addColorStop(1, `rgba(255,92,200,${pulse})`);
      g.fillStyle = rg; g.fillRect(0, 0, this.w, this.hgt);
    }
    // スローモーション演出
    if (this.timeScale < 0.9) {
      const a = (1 - this.timeScale) * 0.5;
      g.save();
      g.strokeStyle = `rgba(45,212,167,${a})`; g.lineWidth = 6;
      g.strokeRect(3, 3, this.w - 6, this.hgt - 6);
      g.restore();
    }

    // 画面外のボス / エリート指示
    if (this.status === "playing") {
      const hw = this.w / 2, hh = this.hgt / 2;
      for (const e of [...this.enemies]) {
        if (!(e.boss || e.elite) || e.spawnT > 0) continue;
        const sx = (e.x - this.camX) * this.zoom + hw;
        const sy = (e.y - this.camY) * this.zoom + hh;
        if (sx > 30 && sx < this.w - 30 && sy > 30 && sy < this.hgt - 30) continue;
        const a = Math.atan2(sy - hh, sx - hw);
        const m = 34;
        const ix = clamp(sx, m, this.w - m), iy = clamp(sy, m, this.hgt - m);
        g.save();
        g.translate(ix, iy); g.rotate(a);
        g.fillStyle = e.color; g.globalAlpha = 0.85;
        g.beginPath(); g.moveTo(14, 0); g.lineTo(-8, -9); g.lineTo(-4, 0); g.lineTo(-8, 9); g.closePath(); g.fill();
        g.restore();
        if (e.boss) {
          g.save();
          g.font = `11px ${this.uiFont}`; g.textAlign = "center"; g.fillStyle = e.accent;
          g.fillText("BOSS", ix, iy + 24);
          g.restore();
        }
      }
    }

    // 仮想スティック
    const t = this.touch;
    if (t.move) {
      g.save();
      g.globalAlpha = 0.35;
      g.strokeStyle = "#ffffff"; g.lineWidth = 2;
      g.beginPath(); g.arc(t.mx, t.my, 58, 0, 7); g.stroke();
      g.globalAlpha = 0.6; g.fillStyle = this.cls.color;
      g.beginPath(); g.arc(t.mx + t.dx * 58, t.my + t.dy * 58, 24, 0, 7); g.fill();
      g.restore();
    }
    if (t.aim) {
      g.save();
      g.globalAlpha = 0.35;
      g.strokeStyle = "#ffd23c"; g.lineWidth = 2;
      g.beginPath(); g.arc(t.aox, t.aoy, 58, 0, 7); g.stroke();
      const l = Math.min(58, t.aimLen);
      const a = Math.atan2(t.ay - t.aoy, t.ax - t.aox);
      g.globalAlpha = 0.65; g.fillStyle = "#ffd23c";
      g.beginPath(); g.arc(t.aox + Math.cos(a) * l, t.aoy + Math.sin(a) * l, 22, 0, 7); g.fill();
      g.restore();
    }

    // crosshair（マウス操作時のみ）
    if (this.status === "playing" && !this.isTouch && !this.autoAim) {
      g.save();
      g.strokeStyle = "rgba(255,255,255,.55)"; g.lineWidth = 1.6;
      g.beginPath(); g.arc(this.mouse.x, this.mouse.y, 11, 0, 7); g.stroke();
      g.beginPath();
      g.moveTo(this.mouse.x - 17, this.mouse.y); g.lineTo(this.mouse.x - 5, this.mouse.y);
      g.moveTo(this.mouse.x + 5, this.mouse.y); g.lineTo(this.mouse.x + 17, this.mouse.y);
      g.moveTo(this.mouse.x, this.mouse.y - 17); g.lineTo(this.mouse.x, this.mouse.y - 5);
      g.moveTo(this.mouse.x, this.mouse.y + 5); g.lineTo(this.mouse.x, this.mouse.y + 17);
      g.stroke();
      g.restore();
    }
    void dt;
  }

  private drawPlayer(g: CanvasRenderingContext2D) {
    const d = this.derived;
    const blink = this.iframe > 0 && Math.floor(this.time * 22) % 2 === 0;
    g.save();
    g.translate(this.px, this.py);
    // shadow
    g.fillStyle = "rgba(0,0,0,.55)";
    g.beginPath(); g.ellipse(2, 18, 20, 9, 0, 0, 7); g.fill();
    // aura
    g.save();
    g.globalCompositeOperation = "lighter";
    const grd = g.createRadialGradient(0, 0, 4, 0, 0, 66);
    grd.addColorStop(0, this.cls.color + "88");
    grd.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = grd; g.beginPath(); g.arc(0, 0, 66, 0, 7); g.fill();
    g.restore();

    if ((this.abilities.aura ?? 0) > 0) {
      const rad = (108 + this.abilities.aura * 22) * d.area;
      g.save();
      g.globalAlpha = 0.14; g.fillStyle = "#ffd76a";
      g.beginPath(); g.arc(0, 0, rad, 0, 7); g.fill();
      g.globalAlpha = 0.4; g.strokeStyle = "#ffd76a"; g.lineWidth = 2;
      g.beginPath(); g.arc(0, 0, rad, 0, 7); g.stroke();
      g.restore();
    }

    g.rotate(this.pAngle);
    if (blink) g.globalAlpha = 0.45;
    // body
    g.fillStyle = "#12101c";
    g.strokeStyle = this.cls.color; g.lineWidth = 3.5;
    g.beginPath(); g.arc(0, 0, 19, 0, 7); g.fill(); g.stroke();
    g.fillStyle = this.cls.accent;
    g.beginPath(); g.arc(6, 0, 7, 0, 7); g.fill();
    // weapon
    if (this.cls.attack === "melee") {
      g.fillStyle = "#e6ecff";
      g.beginPath();
      g.moveTo(16, -3); g.lineTo(44, -1.6); g.lineTo(48, 0); g.lineTo(44, 1.6); g.lineTo(16, 3);
      g.closePath(); g.fill();
      g.fillStyle = this.cls.color;
      g.fillRect(10, -4.5, 8, 9);
    } else {
      g.fillStyle = "#2a2438";
      g.fillRect(12, -5, 26, 10);
      g.fillStyle = this.cls.color;
      g.fillRect(32, -3, 8, 6);
    }
    g.restore();

    // orbit blades
    if ((this.abilities.orbit ?? 0) > 0) {
      const n = 2 + this.abilities.orbit;
      const rad = (92 + this.abilities.orbit * 13) * d.area;
      g.save();
      g.globalCompositeOperation = "lighter";
      for (let i = 0; i < n; i++) {
        const a = this.orbitA + (i / n) * Math.PI * 2;
        const bx = this.px + Math.cos(a) * rad, by = this.py + Math.sin(a) * rad;
        g.save(); g.translate(bx, by); g.rotate(a + Math.PI / 2);
        g.fillStyle = "#e8f0ff";
        g.beginPath(); g.moveTo(0, -16); g.lineTo(6, 0); g.lineTo(0, 16); g.lineTo(-6, 0); g.closePath(); g.fill();
        g.globalAlpha = 0.4;
        g.fillStyle = "#9fd8ff";
        g.beginPath(); g.arc(0, 0, 16, 0, 7); g.fill();
        g.restore();
      }
      g.restore();
    }
    // familiars
    if ((this.abilities.familiar ?? 0) > 0) {
      const n = this.abilities.familiar;
      for (let i = 0; i < n; i++) {
        const a = this.time * 1.6 + (i / n) * Math.PI * 2;
        const fx = this.px + Math.cos(a) * 128, fy = this.py + Math.sin(a) * 128;
        g.save(); g.translate(fx, fy);
        g.fillStyle = "#0d1a14"; g.strokeStyle = "#7cffb2"; g.lineWidth = 2;
        g.beginPath(); g.arc(0, 0, 11, 0, 7); g.fill(); g.stroke();
        g.fillStyle = "#7cffb2";
        g.beginPath(); g.arc(3, -2, 2.6, 0, 7); g.arc(-3, -2, 2.6, 0, 7); g.fill();
        g.restore();
      }
    }
    // dash cd ring
    if (this.dashCd > 0) {
      g.save();
      g.strokeStyle = "rgba(255,255,255,.35)"; g.lineWidth = 3;
      g.beginPath();
      g.arc(this.px, this.py, 30, -Math.PI / 2, -Math.PI / 2 + (1 - this.dashCd / (d.dashCd || 1)) * Math.PI * 2);
      g.stroke();
      g.restore();
    }
  }

  private drawEnemy(g: CanvasRenderingContext2D, e: Enemy) {
    const t = e.t;
    g.save();
    g.translate(e.x, e.y);
    g.fillStyle = "rgba(0,0,0,.5)";
    g.beginPath(); g.ellipse(2, e.r * 0.72, e.r * 0.92, e.r * 0.4, 0, 0, 7); g.fill();
    if (e.spawnT > 0) {
      g.globalAlpha = 1 - e.spawnT / 0.9;
      g.scale(1 + e.spawnT, 1 + e.spawnT);
    }
    const flash = e.flash > 0;
    const body = flash ? "#ffffff" : e.color;
    const acc = flash ? "#ffffff" : e.accent;
    g.save();
    g.globalCompositeOperation = "lighter";
    g.globalAlpha = e.boss ? 0.4 : e.elite ? 0.3 : 0.16;
    const grd = g.createRadialGradient(0, 0, 2, 0, 0, e.r * 2.6);
    grd.addColorStop(0, e.color); grd.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = grd; g.beginPath(); g.arc(0, 0, e.r * 2.6, 0, 7); g.fill();
    g.restore();

    const slow = e.slow > 0;
    switch (e.shape) {
      case "guard": case "necromancer": case "gate": {
        drawTacticalBody(g, e, this.uiFont);
        break;
      }
      case "slime": {
        const sq = 1 + Math.sin(t * 6) * 0.1;
        g.scale(1 / sq, sq);
        g.fillStyle = body;
        g.beginPath(); g.arc(0, 0, e.r, Math.PI, 0); g.lineTo(e.r, e.r * 0.5); g.lineTo(-e.r, e.r * 0.5); g.closePath(); g.fill();
        g.fillStyle = "#0b0a12";
        g.beginPath(); g.arc(-e.r * 0.32, -e.r * 0.1, e.r * 0.16, 0, 7); g.arc(e.r * 0.32, -e.r * 0.1, e.r * 0.16, 0, 7); g.fill();
        break;
      }
      case "bat": {
        const f = Math.sin(t * 16) * 0.6;
        g.fillStyle = body;
        g.beginPath(); g.ellipse(0, 0, e.r * 0.62, e.r * 0.78, 0, 0, 7); g.fill();
        g.beginPath();
        g.moveTo(-e.r * 0.4, 0); g.quadraticCurveTo(-e.r * 1.9, -e.r * (0.9 + f), -e.r * 2.1, e.r * 0.3);
        g.quadraticCurveTo(-e.r * 1.1, e.r * 0.2, -e.r * 0.4, e.r * 0.5); g.closePath(); g.fill();
        g.beginPath();
        g.moveTo(e.r * 0.4, 0); g.quadraticCurveTo(e.r * 1.9, -e.r * (0.9 + f), e.r * 2.1, e.r * 0.3);
        g.quadraticCurveTo(e.r * 1.1, e.r * 0.2, e.r * 0.4, e.r * 0.5); g.closePath(); g.fill();
        g.fillStyle = acc;
        g.beginPath(); g.arc(-e.r * 0.22, -e.r * 0.15, 2.6, 0, 7); g.arc(e.r * 0.22, -e.r * 0.15, 2.6, 0, 7); g.fill();
        break;
      }
      case "skeleton": {
        g.rotate(e.angle + Math.PI / 2);
        g.fillStyle = body;
        g.beginPath(); g.arc(0, -e.r * 0.25, e.r * 0.62, 0, 7); g.fill();
        g.fillRect(-e.r * 0.5, 0, e.r, e.r * 0.85);
        g.fillStyle = "#0b0a12";
        g.beginPath(); g.arc(-e.r * 0.24, -e.r * 0.3, e.r * 0.15, 0, 7); g.arc(e.r * 0.24, -e.r * 0.3, e.r * 0.15, 0, 7); g.fill();
        g.strokeStyle = acc; g.lineWidth = 3;
        g.beginPath(); g.moveTo(e.r * 0.5, e.r * 0.1); g.lineTo(e.r * 1.35, -e.r * 0.35); g.stroke();
        break;
      }
      case "imp": {
        g.fillStyle = body;
        g.beginPath(); g.arc(0, 0, e.r * 0.85, 0, 7); g.fill();
        g.beginPath(); g.moveTo(-e.r * 0.6, -e.r * 0.5); g.lineTo(-e.r * 0.95, -e.r * 1.3); g.lineTo(-e.r * 0.15, -e.r * 0.75); g.closePath(); g.fill();
        g.beginPath(); g.moveTo(e.r * 0.6, -e.r * 0.5); g.lineTo(e.r * 0.95, -e.r * 1.3); g.lineTo(e.r * 0.15, -e.r * 0.75); g.closePath(); g.fill();
        g.fillStyle = acc;
        g.beginPath(); g.arc(-e.r * 0.28, -e.r * 0.05, 3, 0, 7); g.arc(e.r * 0.28, -e.r * 0.05, 3, 0, 7); g.fill();
        break;
      }
      case "golem": {
        g.rotate(e.angle);
        g.fillStyle = body;
        g.beginPath();
        g.moveTo(-e.r, -e.r * 0.7); g.lineTo(e.r * 0.8, -e.r); g.lineTo(e.r, e.r * 0.7); g.lineTo(-e.r * 0.7, e.r); g.closePath(); g.fill();
        g.strokeStyle = acc; g.lineWidth = 3; g.stroke();
        g.fillStyle = acc;
        g.beginPath(); g.arc(e.r * 0.3, -e.r * 0.15, 4, 0, 7); g.arc(e.r * 0.3, e.r * 0.25, 4, 0, 7); g.fill();
        break;
      }
      case "wraith": {
        const fl = Math.sin(t * 4) * 4;
        g.fillStyle = body;
        g.globalAlpha = 0.85;
        g.beginPath();
        g.moveTo(0, -e.r);
        g.quadraticCurveTo(e.r, -e.r * 0.2, e.r * 0.6, e.r + fl);
        g.quadraticCurveTo(0, e.r * 0.6, -e.r * 0.6, e.r + fl);
        g.quadraticCurveTo(-e.r, -e.r * 0.2, 0, -e.r);
        g.fill();
        g.globalAlpha = 1;
        g.fillStyle = "#05040a";
        g.beginPath(); g.arc(-e.r * 0.26, -e.r * 0.25, 3.6, 0, 7); g.arc(e.r * 0.26, -e.r * 0.25, 3.6, 0, 7); g.fill();
        break;
      }
      case "eye": {
        g.fillStyle = body;
        g.beginPath(); g.arc(0, 0, e.r, 0, 7); g.fill();
        g.strokeStyle = acc; g.lineWidth = 4; g.stroke();
        const ex = Math.cos(e.angle) * e.r * 0.3, ey = Math.sin(e.angle) * e.r * 0.3;
        g.fillStyle = "#fff";
        g.beginPath(); g.arc(ex, ey, e.r * 0.48, 0, 7); g.fill();
        g.fillStyle = "#12060c";
        g.beginPath(); g.arc(ex + Math.cos(e.angle) * 5, ey + Math.sin(e.angle) * 5, e.r * 0.24, 0, 7); g.fill();
        for (let i = 0; i < 6; i++) {
          const a = t * 1.2 + (i / 6) * Math.PI * 2;
          g.strokeStyle = acc; g.lineWidth = 2;
          g.beginPath(); g.moveTo(Math.cos(a) * e.r, Math.sin(a) * e.r); g.lineTo(Math.cos(a) * e.r * 1.5, Math.sin(a) * e.r * 1.5); g.stroke();
        }
        break;
      }
      case "dragon": {
        g.rotate(e.angle);
        const f = Math.sin(t * 9) * 0.35;
        g.fillStyle = body;
        g.beginPath();
        g.moveTo(-e.r * 0.5, 0); g.quadraticCurveTo(-e.r * 1.7, -e.r * (1 + f), -e.r * 0.4, -e.r * 0.5); g.closePath(); g.fill();
        g.beginPath();
        g.moveTo(-e.r * 0.5, 0); g.quadraticCurveTo(-e.r * 1.7, e.r * (1 + f), -e.r * 0.4, e.r * 0.5); g.closePath(); g.fill();
        g.beginPath(); g.ellipse(0, 0, e.r * 1.1, e.r * 0.62, 0, 0, 7); g.fill();
        g.beginPath(); g.moveTo(e.r * 0.9, -e.r * 0.4); g.lineTo(e.r * 1.7, 0); g.lineTo(e.r * 0.9, e.r * 0.4); g.closePath(); g.fill();
        g.fillStyle = acc;
        g.beginPath(); g.arc(e.r * 1.15, -e.r * 0.18, 3.6, 0, 7); g.arc(e.r * 1.15, e.r * 0.18, 3.6, 0, 7); g.fill();
        break;
      }
      case "knight": {
        g.rotate(e.angle);
        g.fillStyle = "#1b1226";
        g.beginPath(); g.arc(0, 0, e.r, 0, 7); g.fill();
        g.strokeStyle = body; g.lineWidth = 6; g.stroke();
        g.fillStyle = body;
        g.beginPath(); g.moveTo(e.r * 0.5, -6); g.lineTo(e.r * 2.1, -3); g.lineTo(e.r * 2.1, 3); g.lineTo(e.r * 0.5, 6); g.closePath(); g.fill();
        g.fillStyle = acc;
        g.beginPath(); g.arc(e.r * 0.25, -e.r * 0.28, 6, 0, 7); g.arc(e.r * 0.25, e.r * 0.28, 6, 0, 7); g.fill();
        break;
      }
      case "god": {
        g.rotate(t * 0.5);
        for (let i = 0; i < 3; i++) {
          g.save();
          g.rotate((i / 3) * Math.PI * 2 + t * (0.4 + i * 0.3));
          g.strokeStyle = i % 2 ? acc : body; g.lineWidth = 5; g.globalAlpha = 0.85;
          g.beginPath(); g.ellipse(0, 0, e.r * (1.4 + i * 0.3), e.r * (0.5 + i * 0.2), 0, 0, 7); g.stroke();
          g.restore();
        }
        g.globalAlpha = 1;
        const grd = g.createRadialGradient(0, 0, 4, 0, 0, e.r);
        grd.addColorStop(0, "#ffffff"); grd.addColorStop(0.4, body); grd.addColorStop(1, "#1a0630");
        g.fillStyle = grd;
        g.beginPath(); g.arc(0, 0, e.r, 0, 7); g.fill();
        g.fillStyle = "#0a0410";
        g.beginPath(); g.arc(0, 0, e.r * 0.42, 0, 7); g.fill();
        g.fillStyle = acc;
        g.beginPath(); g.arc(Math.cos(t * 2) * 6, Math.sin(t * 2) * 6, e.r * 0.16, 0, 7); g.fill();
        break;
      }
      default: {
        g.fillStyle = body;
        g.beginPath(); g.arc(0, 0, e.r, 0, 7); g.fill();
      }
    }
    if (slow) {
      g.strokeStyle = "rgba(140,230,255,.8)"; g.lineWidth = 3;
      g.beginPath(); g.arc(0, 0, e.r + 5, 0, 7); g.stroke();
    }
    g.restore();

    // hp bar
    if (!e.boss && (e.hp < e.maxHp || e.elite) && e.spawnT <= 0) {
      const w = e.r * 2.2;
      g.fillStyle = "rgba(0,0,0,.7)";
      g.fillRect(e.x - w / 2, e.y - e.r - 15, w, 5);
      g.fillStyle = e.elite ? "#ffd23c" : "#ff4d5e";
      g.fillRect(e.x - w / 2, e.y - e.r - 15, w * clamp(e.hp / e.maxHp, 0, 1), 5);
    }
    if (e.elite && !e.boss) {
      g.save();
      g.globalCompositeOperation = "lighter";
      g.strokeStyle = e.color; g.globalAlpha = 0.4 + Math.sin(t * 5) * 0.2; g.lineWidth = 2;
      g.beginPath(); g.arc(e.x, e.y, e.r + 9, 0, 7); g.stroke();
      g.restore();
    }
  }

  abilityLevels() {
    return Object.entries(this.abilities)
      .filter(([, v]) => v > 0)
      .map(([k, v]) => ({ id: k as AbilityId, name: ABILITIES[k as AbilityId]?.name ?? k, lv: v }));
  }
}

export type { Item, Slot, Boon, Derived };
export { ABILITIES, BOONS, SLOTS, RARITY_META };

