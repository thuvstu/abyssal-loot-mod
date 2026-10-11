"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent, ReactNode } from "react";
import { Engine, FloorEvent, RunSummary, Snapshot } from "@/game/engine";
import type { RunConfig } from "@/game/progression";
import type { Passive, Relic } from "@/game/core";
import { PASSIVE_GRADE } from "@/game/core";
import {
  ABILITIES, AbilityId, Boon, BOON_GRADE, ClassDef, Item, MATERIALS, MaterialId, RARITY_META,
  SLOTS, SLOT_META, STAT_META, StatKey, WEAPON_CLASSES, WEAPON_TRAITS, diffItems, nf,
} from "@/game/core";
import type { Hero } from "@/game/api";
import { isMuted, playSfx, setMuted } from "@/game/audio";
import { Bar, ItemCard, KeyCap, Panel, RarityTag, SlotIcon } from "./ui";
import ForgePanel from "./ForgePanel";
import AttributePanel from "./AttributePanel";
import WeaponArtControl from "./WeaponArtControl";
import type { AttributeId } from "@/game/attributes";

type Props = {
  cls: ClassDef;
  hero: Hero | null;
  runConfig: RunConfig;
  /** 前回踏破時に持ち帰った継承装備 */
  heirloom?: Item | null;
  onEnd: (s: RunSummary) => void;
  onQuit: () => void;
};

type LogLine = { id: number; text: string; color: string };

const fmtTime = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;

export default function GameScreen({ cls, hero, runConfig, heirloom, onEnd, onQuit }: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const engineRef = useRef<Engine | null>(null);
  const cb = useRef({ onEnd, onQuit });
  cb.current = { onEnd, onQuit };

  const [snap, setSnap] = useState<Snapshot | null>(null);
  const [logs, setLogs] = useState<LogLine[]>([]);
  const [boons, setBoons] = useState<Boon[] | null>(null);
  const [loot, setLoot] = useState<{ item: Item; cur: Item | null } | null>(null);
  const [floorEvent, setFloorEvent] = useState<FloorEvent | null>(null);
  const [passiveChoices, setPassiveChoices] = useState<Passive[] | null>(null);
  const [relicChoices, setRelicChoices] = useState<Relic[] | null>(null);
  const [lootReviewOpen, setLootReviewOpen] = useState(false);
  const [drawer, setDrawer] = useState<null | "gear" | "stats" | "forge">(null);
  const [isTouch, setIsTouch] = useState(false);
  const [hint, setHint] = useState(true);
  const [muted, setMutedState] = useState(false);
  const logId = useRef(1);

  useEffect(() => {
    setMutedState(isMuted());
    setIsTouch(window.matchMedia?.("(pointer: coarse)").matches ?? false);
    const t = setTimeout(() => setHint(false), 7000);
    return () => clearTimeout(t);
  }, []);

  const addLog = useCallback((text: string, color = "#cfd8e6") => {
    setLogs((l) => [...l.slice(-9), { id: logId.current++, text, color }]);
  }, []);

  useEffect(() => {
    const cv = canvasRef.current;
    if (!cv) return;
    const eng = new Engine(cv, {
      onSnapshot: (s) => setSnap(s),
      onLevelUp: (b) => setBoons(b),
      onLoot: (it, cur) => setLoot({ item: it, cur }),
      onFloorEvent: (event) => setFloorEvent(event),
      onPassives: (list) => setPassiveChoices(list),
      onRelics: (list) => setRelicChoices(list),
      onLootReview: () => setLootReviewOpen(true),
      onEnd: (s) => cb.current.onEnd(s),
      onLog: (t, c) => addLog(t, c),
    });
    engineRef.current = eng;

    const ro = new ResizeObserver(() => eng.resize());
    if (wrapRef.current) ro.observe(wrapRef.current);

    const fam = getComputedStyle(document.documentElement).getPropertyValue("--f-pixel").trim();
    if (fam) eng.uiFont = fam;

    eng.start(cls, hero?.meta ?? null, runConfig, heirloom ?? null);
    return () => {
      ro.disconnect();
      eng.destroy();
      engineRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const choose = useCallback((b: Boon) => {
    engineRef.current?.chooseBoon(b);
    setBoons(null);
  }, []);
  const doEquip = useCallback(() => { engineRef.current?.equipPending(); setLoot(null); }, []);
  const doSmelt = useCallback(() => { engineRef.current?.discardPending(); setLoot(null); }, []);
  const doDrop = useCallback(() => { engineRef.current?.stashPending(); setLoot(null); }, []);
  const chooseRelic = useCallback((rel: Relic) => {
    engineRef.current?.chooseRelic(rel);
    setRelicChoices(null);
  }, []);
  const choosePassive = useCallback((p: Passive) => {
    engineRef.current?.choosePassive(p);
    setPassiveChoices(null);
  }, []);
  const chooseEvent = useCallback((i: number) => {
    engineRef.current?.chooseFloorEvent(i);
    setFloorEvent(null);
  }, []);
  const doPause = useCallback(() => { engineRef.current?.togglePause(); }, []);
  const useArt = useCallback(() => { engineRef.current?.useWeaponArt(); }, []);
  const allocate = useCallback((id: AttributeId, amount: number) => { engineRef.current?.allocateAttribute(id, amount); }, []);
  const openForge = useCallback(() => { if (engineRef.current?.openForge()) setDrawer("forge"); }, []);
  const closeForge = useCallback(() => { engineRef.current?.closeForge(); setDrawer(null); }, []);
  const quit = useCallback(() => {
    const e = engineRef.current;
    if (!e) return cb.current.onQuit();
    e.abandon();
  }, []);
  const doDash = useCallback((e: ReactPointerEvent) => { e.preventDefault(); e.stopPropagation(); engineRef.current?.dash(); }, []);
  const doSkill = useCallback((e: ReactPointerEvent) => { e.preventDefault(); e.stopPropagation(); engineRef.current?.useSkill(); }, []);
  const doBomb = useCallback((e: ReactPointerEvent) => { e.preventDefault(); e.stopPropagation(); engineRef.current?.useBomb(); }, []);
  const toggleAuto = useCallback(() => engineRef.current?.toggleAutoAim(), []);
  const descendTrue = useCallback(() => { engineRef.current?.descendToTrueAbyss(); }, []);
  const returnWithLoot = useCallback(() => { engineRef.current?.finishRunNow(); }, []);

  // モーダル用ショートカット
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const k = e.key.toLowerCase();
      if (e.repeat) return;
      if (drawer === "forge" && (k === "f" || k === "escape")) { e.preventDefault(); closeForge(); return; }
      if (boons && engineRef.current?.status === "levelup") {
        const i = ["1", "2", "3"].indexOf(k);
        if (i >= 0 && boons[i]) { e.preventDefault(); choose(boons[i]); }
        return;
      }
      if (loot && engineRef.current?.status === "loot") {
        if (k === "e" || k === "enter" || k === "1") { e.preventDefault(); doEquip(); }
        else if (k === "x" || k === "2") { e.preventDefault(); doSmelt(); }
        else if (k === "g" || k === "3") { e.preventDefault(); doDrop(); }
      }
      if (floorEvent && engineRef.current?.status === "event" && ["1", "2", "3"].includes(k)) {
        e.preventDefault();
        chooseEvent(Number(k) - 1);
      }
      if (relicChoices && engineRef.current?.status === "relic") {
        const i = ["1", "2", "3"].indexOf(k);
        if (i >= 0 && relicChoices[i]) { e.preventDefault(); chooseRelic(relicChoices[i]); }
        return;
      }
      if (passiveChoices && engineRef.current?.status === "passive") {
        const i = ["1", "2", "3"].indexOf(k);
        if (i >= 0 && passiveChoices[i]) { e.preventDefault(); choosePassive(passiveChoices[i]); }
        return;
      }
      if ((snap?.status ?? "") === "shop" && k === "escape") {
        e.preventDefault();
        engineRef.current?.closeShop();
      }
      if (k === "f" && !boons && !loot && !floorEvent && (snap?.status ?? "playing") === "playing") {
        openForge();
      }
      // 真・最終決戦への選択
      if ((snap?.status ?? "") === "trueabyss") {
        if (k === "enter" || k === "1") { e.preventDefault(); descendTrue(); }
        else if (k === "escape" || k === "2") { e.preventDefault(); returnWithLoot(); }
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [boons, loot, floorEvent, passiveChoices, relicChoices, drawer, snap?.status, choose, choosePassive, chooseRelic, chooseEvent, doEquip, doSmelt, doDrop, descendTrue, returnWithLoot, openForge, closeForge]);

  const d = snap?.derived;
  const status = snap?.status ?? "playing";
  const paused = status === "paused";
  const finalDuel = snap?.floor === 26 && runConfig.mode !== "endless" && runConfig.mode !== "bossrush";
  const hpPct = snap ? snap.hp / Math.max(1, snap.maxHp) : 1;

  const statRows = useMemo(() => {
    if (!d) return [];
    const rows: { k: StatKey; v: number }[] = [
      { k: "damageFlat", v: d.damage },
      { k: "attackSpeedPct", v: (d.attackSpeed - 1) * 100 },
      { k: "critPct", v: d.crit * 100 },
      { k: "critDmgPct", v: (d.critDmg - 1) * 100 },
      { k: "areaPct", v: (d.area - 1) * 100 },
      { k: "moveSpeedPct", v: d.moveSpeed },
      { k: "armorPct", v: d.armor * 100 },
      { k: "lifestealPct", v: d.lifesteal * 100 },
      { k: "projectiles", v: d.projectiles },
      { k: "pierce", v: d.pierce },
      { k: "chain", v: d.chain },
      { k: "explodePct", v: d.explode * 100 },
      { k: "freezePct", v: d.freeze * 100 },
      { k: "thornsFlat", v: d.thorns },
      { k: "regenFlat", v: d.regen },
      { k: "luckFlat", v: d.luck },
      { k: "xpPct", v: (d.xpMult - 1) * 100 },
      { k: "goldPct", v: (d.goldMult - 1) * 100 },
    ];
    return rows.filter((r) => Math.abs(r.v) > 0.04);
  }, [d]);

  const abilityChips = useMemo(() => {
    if (!snap) return [];
    return Object.entries(snap.abilities)
      .filter(([, v]) => v > 0)
      .map(([k, v]) => ({ id: k as AbilityId, lv: v, meta: ABILITIES[k as AbilityId] }))
      .filter((a) => a.meta);
  }, [snap]);

  return (
    <div className="game-shell relative flex h-[100dvh] w-full overflow-hidden bg-abyss">
      {/* ------------------------------------------------- 左：装備 */}
      <aside className="hidden w-[228px] shrink-0 flex-col gap-2 overflow-y-auto border-r border-line bg-abyss2/85 p-2 lg:flex">
        <GearPanel snap={snap} cls={cls} />
        <AbilityPanel chips={abilityChips} boons={snap?.boons ?? []} />
      </aside>

      {/* ------------------------------------------------- 中央 */}
      <main ref={wrapRef} className="relative min-w-0 flex-1">
        <canvas ref={canvasRef} className="no-touch absolute inset-0 block h-full w-full cursor-none" />

        {/* 上部HUD */}
        <div className="pointer-events-none absolute inset-x-0 top-0 z-10 p-2 sm:p-3">
          <div className="game-top-row flex items-start gap-3">
            {/* HP / XP */}
            <div className="game-vitals min-w-0 max-w-[330px] flex-1 sm:min-w-[210px]">
              <div className="mb-1 flex items-end gap-2">
                <span className="font-display text-[26px] leading-none font-black text-gold sm:text-[30px]">{snap?.level ?? 1}</span>
                <span className="pb-1 text-[9px] tracking-[0.28em] text-ash">LEVEL</span>
                <span className="ml-auto pb-1 tabular text-[11px] font-bold" style={{ color: hpPct < 0.3 ? "#ff5c6e" : "#e9e3d5" }}>
                  {nf(snap?.hp ?? 0)} / {nf(snap?.maxHp ?? 0)}
                </span>
              </div>
              <div className="border border-black/60 bg-black/45 p-1 backdrop-blur-[2px]">
                <Bar value={snap?.hp ?? 0} max={snap?.maxHp ?? 1} color={hpPct < 0.3 ? "#ff2d4a" : "#e0243c"} h={13} />
                <div className="mt-1">
                  <Bar value={snap?.xp ?? 0} max={snap?.xpNeed ?? 1} color="#5cc8ff" h={5} />
                </div>
              </div>
              <div className="mt-1 flex items-center gap-2 text-[10px] text-ash">
                <span className="tabular hidden sm:inline">
                  XP {snap?.xp ?? 0}/{snap?.xpNeed ?? 1}
                </span>
                <span className="hidden opacity-40 sm:inline">|</span>
                <span className="tabular">DPS <b className="text-ember">{nf(snap?.dps ?? 0)}</b></span>
                <span className="tabular sm:hidden">
                  <span className="opacity-40">|</span> <b className="text-bone">{snap?.floor ?? 1}F</b>
                  <span className="ml-1 text-blood">残{snap?.left ?? 0}</span>
                </span>
                <span className="tabular sm:hidden">
                  <span className="opacity-40">|</span> <b className="text-gold">{nf(snap?.gold ?? 0)}G</b>
                </span>
              </div>
              {/* 素材バー */}
              {snap?.materials && Object.values(snap.materials).some((v) => v > 0) && (
                <div className="mt-0.5 flex flex-wrap gap-1.5 text-[9px]">
                  {(Object.keys(MATERIALS) as MaterialId[]).filter((k) => (snap.materials[k] ?? 0) > 0).map((k) => (
                    <span key={k} className="flex items-center gap-0.5 border px-1 py-0.5"
                      style={{ borderColor: `${MATERIALS[k].color}33`, background: `${MATERIALS[k].color}08` }}>
                      <span style={{ color: MATERIALS[k].color }}>{MATERIALS[k].icon}</span>
                      <span className="tabular font-bold" style={{ color: MATERIALS[k].color }}>{snap.materials[k]}</span>
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* 層 */}
            <div className="hidden shrink-0 border border-line bg-black/50 px-3 py-1.5 text-center backdrop-blur-[2px] sm:block">
              <div className="text-[9px] tracking-[0.3em]" style={{ color: snap?.zoneColor ?? "#8d86a3" }}>
                {snap?.zoneName ?? "ABYSS FLOOR"}
              </div>
              <div className="font-display text-[26px] leading-none font-black text-bone">
                {snap?.floor ?? 1}
                <span className="text-[13px] text-ember">F</span>
              </div>
              <div className="mt-0.5 text-[10px] text-ash">
                残り <b className="tabular text-blood">{snap?.left ?? 0}</b> ／ {fmtTime(snap?.time ?? 0)}
              </div>
              {snap?.mutationName && (
                <div className="mt-1 -mx-1 border-t px-2 pt-1 text-[9px] leading-tight"
                  style={{ borderColor: `${snap.mutationColor ?? "#fff"}55`, background: `${snap.mutationColor ?? "#fff"}12`, color: snap.mutationColor ?? undefined }}>
                  異変 • {snap.mutationName}
                </div>
              )}
              {snap?.fateLabel && (
                <div className="mt-1 -mx-1 border-t border-gold/40 bg-gold/10 px-2 pt-1 text-[9px] leading-tight text-gold">
                  運命 • {snap.fateLabel} <span className="tabular opacity-70">{Math.ceil(snap.fateT)}s</span>
                </div>
              )}
              {snap && snap.curseT > 0 && (
                <div className="mt-1 -mx-1 border-t border-pink-500/50 bg-pink-500/15 px-2 pt-1 text-[9px] leading-tight text-pink-400">
                  邪眼の呪い • 被ダメ+25% <span className="tabular opacity-70">{Math.ceil(snap.curseT)}s</span>
                </div>
              )}
              {snap && snap.bulwark > 0 && (
                <div className="mt-1 -mx-1 border-t border-amber-300/50 bg-amber-300/10 px-2 pt-1 text-[9px] leading-tight text-amber-200">
                  神聖の盾 ×{snap.bulwark}
                </div>
              )}
              {snap && snap.difficultyName !== "通常" && (
                <div className="mt-1 -mx-1 border-t px-2 pt-1 text-[9px] leading-tight" style={{ borderColor: `${snap.difficultyColor}55`, color: snap.difficultyColor }}>
                  難易度 • {snap.difficultyName}
                </div>
              )}
            </div>

            {/* 右情報 */}
            <div className="game-menu ml-auto flex shrink-0 flex-col items-end gap-1">
              <div className="hidden gap-1.5 sm:flex">
                <Chip label="GOLD" value={(snap?.gold ?? 0).toLocaleString()} color="#f5c542" />
                <Chip label="KILL" value={String(snap?.kills ?? 0)} color="#ff6a7d" />
                <Chip label="波" value={`${snap?.wave ?? 0}/${snap?.waveTotal ?? 0}`} color="#8b5cf6" />
              </div>
              <div className="game-menu-actions pointer-events-auto flex flex-wrap justify-end gap-1.5">
                <button
                  onClick={toggleAuto}
                  className="btn border px-2.5 py-1 text-[10px] tracking-widest"
                  style={{
                    borderColor: snap?.autoAim ? "#2dd4a7" : "#2c2444",
                    color: snap?.autoAim ? "#2dd4a7" : "#8d86a3",
                    background: snap?.autoAim ? "rgba(45,212,167,.14)" : "rgba(0,0,0,.55)",
                  }}
                >
                  AUTO {snap?.autoAim ? "ON" : "OFF"}
                </button>
                <button onClick={doPause} className="btn border border-line bg-black/55 px-2.5 py-1 text-[10px] tracking-widest text-ash hover:text-bone">
                  {paused ? "再開" : "停止"}
                </button>
                {!finalDuel && (
                  <button onClick={quit} className="btn border border-blood/50 bg-black/55 px-2.5 py-1 text-[10px] tracking-widest text-blood/80 hover:text-blood">
                    撤退
                  </button>
                )}
                {finalDuel && (
                  <span className="border border-white/30 bg-black/55 px-2.5 py-1 text-[10px] tracking-widest text-white/70">
                    決戦中・退路なし
                  </span>
                )}
                <button onClick={() => setDrawer(drawer === "gear" ? null : "gear")} className="btn border border-line bg-black/55 px-2.5 py-1 text-[10px] tracking-widest text-ash hover:text-bone lg:hidden">
                  装備
                </button>
                <button onClick={() => setDrawer(drawer === "stats" ? null : "stats")} className="btn border border-line bg-black/55 px-2.5 py-1 text-[10px] tracking-widest text-ash hover:text-bone xl:hidden">
                  能力
                </button>
                <button onClick={() => drawer === "forge" ? closeForge() : openForge()} className="btn border border-gold/40 bg-black/55 px-2.5 py-1 text-[10px] tracking-widest text-gold/80 hover:text-gold">
                  鍛造 [F]
                </button>
              </div>
            </div>
          </div>

          <div className="game-trial-strip">
            <span>試練 <b>{snap?.trialSigils.length ?? 0}</b> 枚 <i>·</i> 魂 <strong>×{(snap?.trialSoulMultiplier ?? 1).toFixed(2)}</strong></span>
            {runConfig.mode === "danmaku" && (
              <span className="text-cyan-200">GRAZE <b>{snap?.grazeCount ?? 0}</b> <i>·</i> 弾反射 <b>{snap?.bulletsReflected ?? 0}</b> <i>·</i> ボム <b>{snap?.bombStock ?? 0}</b></span>
            )}
            <span className="hidden sm:inline">召喚門破壊 {snap?.gatesDestroyed ?? 0} <i>·</i> 奥義 {snap?.weaponArtUses ?? 0} 回</span>
            {snap?.gamepadConnected && (
              <span className="text-jade">● GAMEPAD接続中</span>
            )}
            {(snap?.attributePoints ?? 0) > 0 && (
              <button type="button" onClick={doPause} className="unspent-points pointer-events-auto" aria-label="一時停止して属性ポイントを配分">
                属性 +{snap?.attributePoints} <small>[Esc]</small>
              </button>
            )}
          </div>

          {runConfig.mode === "danmaku" && snap && snap.deathbombT > 0 && (
            <div className="pointer-events-none absolute inset-x-0 top-1/3 z-30 flex justify-center px-4">
              <div className="animate-pulse border-2 border-white bg-blood/80 px-5 py-3 text-center font-display text-[20px] font-bold text-white shadow-[0_0_55px_#ffffff]">
                今すぐ [X] デスボム！
              </div>
            </div>
          )}

          {/* 弾幕モードのスペルカード表示とグレイズ情報 */}
          {runConfig.mode === "danmaku" && snap && (
            <div className="mx-auto mt-1.5 flex max-w-[640px] flex-wrap items-center justify-center gap-2 border border-pink-500/40 bg-black/60 px-3 py-1.5 text-[10px] tracking-wider backdrop-blur-[2px]">
              <span className="font-display font-bold text-pink-300">{snap.spellName ?? "弾幕準備中"}</span>
              <span className="text-ash">GRAZE <b className="tabular text-cyan-200">{snap.grazeCount}</b></span>
              <span className="text-ash">COMBO <b className="tabular text-cyan-200">{snap.grazeCombo}</b></span>
              <span className="text-ash">BOMB <b className="tabular text-cyan-200">{snap.bombStock}</b> <span className="text-cyan-200/70">({Math.floor(snap.bombCharge)}%)</span></span>
              {snap.focus && <span className="font-bold text-white">● FOCUS</span>}
            </div>
          )}

          {runConfig.mode === "danmaku" && snap?.spellName && snap.spellAnnounceT > 0 && (
            <div className="pointer-events-none absolute inset-x-0 top-32 z-20 flex justify-center px-4">
              <div className="border-y border-pink-300/80 bg-black/80 px-6 py-3 text-center shadow-[0_0_40px_#ff3b8d77]">
                <div className="text-[9px] tracking-[0.45em] text-pink-200/70">SPELL CARD</div>
                <div className="font-display text-[20px] font-bold tracking-[0.15em] text-pink-200 sm:text-[26px]">{snap.spellName}</div>
              </div>
            </div>
          )}

          {/* ボスゲージ */}
          {snap?.bossName && (
            <div className="mx-auto mt-2 w-full max-w-[640px]">
              <div className="mb-1 flex items-baseline justify-between gap-2">
                <span className="font-display text-[13px] font-bold tracking-[0.18em] text-blood">{snap.bossName}</span>
                <span className="tabular text-[10px] text-ash">
                  {nf(snap.bossHp)} / {nf(snap.bossMax)}
                </span>
              </div>
              <div className="border border-blood/40 bg-black/60 p-1">
                <Bar value={snap.bossHp} max={snap.bossMax} color="linear-gradient(90deg,#ff2d4a,#ff8a2b)" h={12} />
              </div>
              {(snap.truePhaseIdx ?? 0) > 0 && (
                <div className="mt-1 flex items-center justify-center gap-2 text-[10px] tracking-[0.2em]">
                  <span className="text-ash">第{Math.min(3, snap.truePhaseIdx + 1)}段階</span>
                  <span className="font-display font-bold" style={{ color: snap.truePhaseLabel === "討伐済" ? "#2dd4a7" : "#ffffff" }}>
                    {snap.truePhaseLabel}
                  </span>
                  <span className="flex gap-0.5">
                    {[0, 1, 2].map((i) => (
                      <span key={i} className="h-1.5 w-5" style={{ background: i <= snap.truePhaseIdx ? "#e34bff" : "rgba(255,255,255,.12)" }} />
                    ))}
                  </span>
                </div>
              )}
            </div>
          )}

          {snap?.portalOpen && (() => {
            const rushAt = 14;
            const left = Math.max(0, rushAt - snap.portalIdle);
            const urgent = left < 6 && snap.floor < 26 && snap.floor % 5 !== 0;
            return (
              <div className="mt-2 text-center">
                <span
                  className={`inline-block border bg-black/60 px-3 py-1 font-display text-[12px] tracking-[0.3em] pulse-ring ${urgent ? "border-blood/70 text-blood" : "border-jade/60 text-jade"}`}
                >
                  {urgent ? `追っ手が迫る — ${Math.ceil(left)}s` : "転移門が開いた — 中央へ向かえ"}
                </span>
                <div className="mt-1 text-[9px] tracking-widest text-ash/70">
                  置き去りの装備は自動売却（半値以下）／ 経験値オーブは消滅
                </div>
              </div>
            );
          })()}
        </div>

        {/* コンボ */}
        {(snap?.combo ?? 0) > 3 && (
          <div className="pointer-events-none absolute top-[22%] right-3 z-10 text-right sm:top-1/4 sm:right-6">
            <div
              className="font-display leading-none font-black title-glow tabular"
              style={{
                fontSize: Math.min(64, 34 + (snap?.combo ?? 0) * 0.4),
                color: (snap?.combo ?? 0) >= 40 ? "#e34bff" : (snap?.combo ?? 0) >= 20 ? "#ff8a2b" : "#f5c542",
              }}
            >
              {snap?.combo}
            </div>
            <div className="text-[10px] tracking-[0.4em] text-gold/80">
              {(snap?.combo ?? 0) >= 40 ? "S RANK" : (snap?.combo ?? 0) >= 20 ? "A RANK" : "COMBO"} ・ +{Math.min(40, Math.round((snap?.combo ?? 0) * 1))}%
            </div>
          </div>
        )}

        {/* ログ */}
        <div className="pointer-events-none absolute bottom-24 left-2 z-10 hidden max-w-[340px] space-y-0.5 sm:block">
          {logs.slice(-6).map((l, i, arr) => (
            <div key={l.id} className="slide-in text-[11px] leading-tight" style={{ color: l.color, opacity: 0.35 + (i / arr.length) * 0.65 }}>
              ▸ {l.text}
            </div>
          ))}
        </div>

        {/* 下部スキルバー（PC） */}
        {!isTouch && (
          <div className="pointer-events-none absolute inset-x-0 bottom-0 z-10 flex justify-center p-2 sm:p-3">
            <div className="flex items-end gap-2">
              <SkillSlot
                label={snap?.counterReady ? "反撃!" : snap && snap.charge > 0 ? (snap.charge >= 0.98 ? "重撃・極" : "溜め中") : cls.attack === "melee" ? "抜刀" : "射撃"}
                keyHint={cls.attack === "melee" ? "LMB 長押し=溜め" : "LMB"}
                color={snap?.counterReady ? "#7cffd0" : snap && snap.charge >= 0.98 ? "#ffffff" : cls.color}
                ready
                ratio={snap && snap.charge > 0 ? snap.charge : snap && snap.atkMax > 0 ? 1 - Math.min(1, snap.atkCd / snap.atkMax) : 1}
              />
              <SkillSlot
                label="回避"
                keyHint="SHIFT"
                color="#2dd4a7"
                ratio={snap && snap.dashMax > 0 ? 1 - snap.dashCd / snap.dashMax : 1}
                ready={!!snap && snap.dashCd <= 0}
              />
              <SkillSlot
                label={cls.skillName}
                keyHint="Q"
                color="#f5c542"
                ratio={snap ? snap.energy / 100 : 0}
                ready={!!snap && snap.energy >= 100}
                big
              />
              <WeaponArtControl snapshot={snap} onUse={useArt} />
              {runConfig.mode === "danmaku" && (
                <button
                  type="button"
                  onClick={() => engineRef.current?.useBomb()}
                  disabled={!snap?.bombStock || status !== "playing"}
                  title="X：ボム。敵弾を全消去し、範囲攻撃＋無敵2.2秒"
                  className="pointer-events-auto relative flex h-[56px] min-w-[72px] flex-col items-center justify-center border-2 border-cyan-300/60 bg-cyan-300/10 px-2 text-cyan-100 disabled:border-line disabled:text-ash/50"
                >
                  <span className="font-display text-[12px] font-bold">ボム ×{snap?.bombStock ?? 0}</span>
                  <span className="text-[9px]">[X] {Math.floor(snap?.bombCharge ?? 0)}%</span>
                </button>
              )}
            </div>
          </div>
        )}

        {/* タッチ用ボタン */}
        {isTouch && (
          <div className="touch-combat-controls no-touch absolute right-3 bottom-4 z-10 flex flex-col items-end gap-3 sm:right-5 sm:bottom-6">
            {runConfig.mode === "danmaku" && (
              <TouchButton label={`ボム ${snap?.bombStock ?? 0}`} sub={`${Math.floor(snap?.bombCharge ?? 0)}%`} color="#bfeaff" size={68} ratio={(snap?.bombCharge ?? 0) / 100} ready={!!snap?.bombStock && status === "playing"} onPress={doBomb} />
            )}
            <WeaponArtControl snapshot={snap} onUse={useArt} touch />
            <TouchButton
              label={cls.skillName}
              sub="必殺"
              color="#f5c542"
              size={82}
              ratio={snap ? snap.energy / 100 : 0}
              ready={!!snap && snap.energy >= 100}
              onPress={doSkill}
            />
            <TouchButton
              label="回避"
              sub="DASH"
              color="#2dd4a7"
              size={70}
              ratio={snap && snap.dashMax > 0 ? 1 - snap.dashCd / snap.dashMax : 1}
              ready={!!snap && snap.dashCd <= 0}
              onPress={doDash}
            />
          </div>
        )}

        {/* タッチFOCUS: 左手で低速移動を保持。離した瞬間に通常速度へ戻る */}
        {isTouch && runConfig.mode === "danmaku" && (
          <button
            type="button"
            aria-label="押している間、低速移動して当たり判定を表示"
            onPointerDown={(e) => { e.preventDefault(); e.stopPropagation(); e.currentTarget.setPointerCapture(e.pointerId); engineRef.current?.setFocus(true); }}
            onPointerUp={(e) => { e.preventDefault(); engineRef.current?.setFocus(false); }}
            onPointerCancel={() => engineRef.current?.setFocus(false)}
            onLostPointerCapture={() => engineRef.current?.setFocus(false)}
            className={`no-touch absolute bottom-4 left-3 z-20 flex h-16 w-16 flex-col items-center justify-center rounded-full border-2 bg-black/70 text-[11px] font-bold select-none sm:bottom-6 sm:left-5 ${snap?.focus ? "border-white text-white shadow-[0_0_24px_#bfeaff]" : "border-cyan-300/60 text-cyan-200"}`}
            style={{ touchAction: "none" }}
          >
            <span>FOCUS</span><small className="text-[8px] opacity-70">低速</small>
          </button>
        )}

        {/* 操作ヒント */}
        {hint && status === "playing" && (
          <div className="pointer-events-none absolute inset-x-0 bottom-28 z-10 flex justify-center px-3 sm:bottom-24">
            <div className="modal-in border border-line bg-black/70 px-4 py-2 text-center text-[11px] leading-relaxed text-bone/90 backdrop-blur-[2px]">
              {isTouch ? (
                <>
                  <span className="text-jade">左半分</span>をドラッグで移動 ／ <span className="text-gold">右半分</span>をドラッグで照準＆攻撃
                  <br />
                  {runConfig.mode === "danmaku" ? <><span className="text-cyan-200">FOCUS</span>を押して低速＋当たり判定表示 ／ 弾をかすめてボム補充 ／ ボムで弾消し</> : <>回避直後の一撃は反撃 ／ 敵弾に重ねて「見切り」発動</>}
                </>
              ) : (
                <>
                  WASD 移動 ／ マウス照準 ／ 左クリック連打=3段コンボ、<span className="text-bone">長押し=溜め重撃</span>
                  <br />
                  {runConfig.mode === "danmaku" ? <><span className="text-cyan-200">Ctrl / Z</span> 低速＆当たり判定 ／ グレイズでボム補充 ／ <span className="text-cyan-200">X</span> ボムで弾消し ／ Shiftダッシュで反射</> : <><span className="text-jade">Shift</span> 回避→反撃／敵弾を見切り／<span className="text-gold">Q</span> 職業技／<span className="text-gold">E</span> 武器奥義</>}
                </>
              )}
            </div>
          </div>
        )}

        {/* ------------------------------------------------ オーバーレイ */}
        {paused && (
          <Overlay>
            <div className="pause-build modal-in panel corner w-[min(650px,94vw)] p-4 text-center sm:p-6">
              <h2 className="font-display text-[30px] font-black tracking-[0.2em] text-bone">一時停止</h2>
              <p className="mt-1 text-[11px] text-ash">戦闘・罠・奥義の時間も完全停止</p>
              {snap && <AttributePanel attributes={snap.attributes} points={snap.attributePoints} onAllocate={allocate} />}
              <div className="mt-5 grid gap-2">
                <button onClick={doPause} className="btn border-2 border-jade/70 bg-jade/15 py-3 font-display text-[16px] font-bold tracking-[0.2em] text-jade">
                  戦闘を再開
                </button>
                {!finalDuel ? (
                  <button onClick={quit} className="btn border border-blood/50 py-2.5 text-[12px] tracking-[0.2em] text-blood/80">
                    撤退する（記録は保存される）
                  </button>
                ) : (
                  <p className="border border-white/20 py-2.5 text-center text-[12px] tracking-[0.2em] text-white/70">
                    真・最終決戦中 — 退路はない
                  </p>
                )}
              </div>
            </div>
          </Overlay>
        )}

        {floorEvent && status === "event" && (
          <Overlay>
            <div className="modal-in w-[min(800px,95vw)]">
              <div className="mb-4 text-center">
                <div className="text-[10px] tracking-[0.42em] text-ember">FLOOR {floorEvent.floor} INTERMISSION</div>
                <h2 className="title-glow mt-1 font-display text-[30px] font-black tracking-[0.16em] text-gold sm:text-[38px]">
                  {floorEvent.title}
                </h2>
                <p className="mt-1 text-[11px] text-ash">{floorEvent.subtitle}</p>
                <p className="mt-2 text-[10px] tracking-widest text-ash/70">ここで選んだ恩恵は、この冒険の最後まで続く</p>
              </div>
              <div className="grid gap-2 sm:grid-cols-3 sm:gap-3">
                {floorEvent.options.map((o, i) => (
                  <button
                    key={o.id}
                    onClick={() => chooseEvent(i)}
                    className="btn corner group border-2 p-3 text-left sm:p-4"
                    style={{ borderColor: `${o.color}88`, background: `linear-gradient(160deg, ${o.color}1f, rgba(8,6,14,.96))` }}
                  >
                    <div className="mb-3 flex items-center justify-between">
                      <span className="font-display text-[10px] tracking-[0.22em]" style={{ color: o.color }}>選択肢 {i + 1}</span>
                      <KeyCap>{i + 1}</KeyCap>
                    </div>
                    <div className="font-display text-[18px] font-bold sm:text-[20px]" style={{ color: o.color }}>{o.title}</div>
                    <p className="mt-2 text-[11px] leading-relaxed text-bone/75">{o.desc}</p>
                    <div className="mt-4 border-t border-white/10 pt-2 text-[9px] tracking-widest text-ash group-hover:text-bone">運命を選ぶ →</div>
                  </button>
                ))}
              </div>
            </div>
          </Overlay>
        )}

        {/* 略奪商人 */}
        {(snap?.status ?? "") === "shop" && (
          <Overlay>
            <div className="modal-in w-[min(900px,95vw)]">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h2 className="title-glow font-display text-[26px] font-black tracking-[0.16em] text-gold">略 奪 商 人</h2>
                  <p className="text-[10px] text-ash">「買いなよ、これは安い。次に会える保証はないけどな」</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className="tabular border border-gold/50 bg-gold/10 px-3 py-1 text-[12px] font-bold text-gold">{snap?.gold.toLocaleString()} G</span>
                  <button onClick={() => engineRef.current?.closeShop()} className="btn border border-line px-3 py-1 text-[11px] text-ash hover:text-bone">店を出る [Esc]</button>
                </div>
              </div>
              <div className="grid gap-2 sm:grid-cols-3">
                {snap?.shop.map((entry, i) => (
                  <ShopCard key={entry.item.id} entry={entry} index={i} gold={snap.gold} onBuy={(idx) => engineRef.current?.buyShopItem(idx)} />
                ))}
              </div>
              {snap?.shop.length === 0 && <p className="py-3 text-center text-[11px] text-ash/60">売り尽くした — リロールするか店を出よう</p>}

              {/* Reroll & Recycling Assistant */}
              <div className="mt-4 border-t border-line/60 pt-3 flex flex-wrap items-center justify-between gap-3 bg-black/30 p-2.5">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-[10px] text-ash tracking-widest uppercase">素材リサイクル（クリックで1個売却してゴールド獲得）:</span>
                  {(Object.keys(MATERIALS) as MaterialId[]).map((k) => {
                    const count = snap?.materials[k] ?? 0;
                    const prices: Record<MaterialId, number> = {
                      soul_shard: 10,
                      ancient_stone: 25,
                      chaos_gem: 80,
                      void_essence: 180,
                      dragon_heart: 500,
                    };
                    const reward = Math.round(prices[k] * (d?.goldMult ?? 1));
                    return (
                      <button
                        key={k}
                        disabled={count <= 0}
                        onClick={() => engineRef.current?.sellMaterial(k)}
                        className={`btn flex items-center gap-1 border px-2 py-0.5 text-[10px] transition ${count > 0 ? "border-gold/30 hover:bg-gold/10 text-gold" : "border-line text-ash/40"}`}
                        title={`${MATERIALS[k].name}を1個売却して +${reward}G`}
                      >
                        <span style={{ color: MATERIALS[k].color }}>{MATERIALS[k].icon}</span>
                        <span className="tabular">{count}</span>
                        <span className="text-ash/60">({reward}G)</span>
                      </button>
                    );
                  })}
                </div>
                <button
                  disabled={!!(snap && snap.gold < 25)}
                  onClick={() => engineRef.current?.rerollShop()}
                  className="btn border border-gold/70 bg-gold/15 px-3 py-1.5 text-[11px] font-bold text-gold disabled:opacity-40"
                >
                  品揃えを更新する (−25G)
                </button>
              </div>
            </div>
          </Overlay>
        )}

        {/* 真・最終決戦への選択 */}
        {(snap?.status ?? "") === "trueabyss" && (
          <Overlay>
            <div className="modal-in w-[min(760px,95vw)] panel corner p-6 text-center">
              <div className="text-[10px] tracking-[0.5em] text-ash">THE TRUE ABYSS</div>
              <h2 className="title-glow mt-2 font-display text-[30px] leading-tight font-black tracking-[0.12em] text-white sm:text-[42px]">
                虚無神は、消えていない
              </h2>
              <p className="mx-auto mt-3 max-w-[520px] text-[12px] leading-relaxed text-ash">
                ニヒルの残骸の下から、さらに古い<span className="text-bone">原初虚無《ニヒル・ゼロ》</span>の鼓動が聞こえる。
                <br />
                ここで帰還すれば<span className="text-jade">装備を1つ持ち帰れる</span>。
                だが更に下へ降りれば、それは叶わなくなるかもしれない。
              </p>
              <div className="mt-5 grid gap-2 sm:grid-cols-2">
                <button onClick={descendTrue} className="btn corner border-2 border-white/80 bg-white/10 py-4 font-display text-[16px] font-black tracking-[0.2em] text-white shadow-[0_0_50px_-14px_#fff]">
                  真・第26層へ降りる
                  <span className="mt-1 block text-[10px] font-normal tracking-widest text-ash">全回復して挑める／撤退不可 [Enter]</span>
                </button>
                <button onClick={returnWithLoot} className="btn corner border-2 border-jade/70 bg-jade/12 py-4 font-display text-[16px] font-black tracking-[0.2em] text-jade">
                  装備を持って帰還
                  <span className="mt-1 block text-[10px] font-normal tracking-widest text-ash">踏破として記録／1点を継承 [Esc]</span>
                </button>
              </div>
              <p className="mt-4 text-[10px] tracking-widest text-ember">
                《ニヒル・ゼロ》は3段階の形態を持つ — 討伐すれば真の踏破と莫大な魂が報われる
              </p>
            </div>
          </Overlay>
        )}

        {/* 実行遺物選択 */}
        {relicChoices && status === "relic" && (
          <Overlay>
            <div className="modal-in w-[min(960px,96vw)]">
              <div className="mb-3 text-center">
                <div className="text-[10px] tracking-[0.5em] text-cyan-300/90">RELIC OF THE ABYSS</div>
                <h2 className="title-glow font-display text-[30px] leading-tight font-black tracking-[0.14em] text-cyan-300">深淵の遺物</h2>
                <p className="mt-1 text-[11px] text-ash">
                  深淵に眠る神器を一つ選びなさい。ゲームの法則そのものを歪める力です。
                </p>
              </div>
              <div className="grid gap-2 sm:grid-cols-3 sm:gap-3">
                {relicChoices.map((rel, i) => (
                  <button
                    key={rel.id}
                    onClick={() => chooseRelic(rel)}
                    className="btn corner group relative overflow-hidden border-2 p-3 text-left sm:p-4"
                    style={{ borderColor: `${rel.color}88`, background: `linear-gradient(180deg, ${rel.color}1c, rgba(8,6,14,.96))` }}
                  >
                    <div className="mb-2 flex items-center justify-between">
                      <span className="font-display text-[30px]" style={{ color: rel.color }}>{rel.icon}</span>
                      <KeyCap>{i + 1}</KeyCap>
                    </div>
                    <div className="font-display text-[19px] leading-tight font-bold" style={{ color: rel.color }}>{rel.name}</div>
                    <p className="mt-2 text-[11px] leading-relaxed text-bone/80">{rel.desc}</p>
                    <div className="mt-3 border-t border-white/10 pt-2 text-[9px] tracking-widest text-ash">
                      遺物 {i + 1} ／ 最大2つまで併持
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </Overlay>
        )}

        {/* パッシブスキル選択 */}
        {passiveChoices && status === "passive" && (
          <Overlay>
            <div className="modal-in w-[min(960px,96vw)]">
              <div className="mb-3 text-center">
                <div className="text-[10px] tracking-[0.5em] text-arcane/90">PASSIVE MASTERY</div>
                <h2 className="title-glow font-display text-[30px] leading-tight font-black tracking-[0.14em] text-arcane">型を刻め</h2>
                <p className="mt-1 text-[11px] text-ash">
                  層の主を倒した者だけが選べる、代償付きの恒久強化。すべての恩恵には必ず裏がある。
                </p>
              </div>
              <div className="grid gap-2 sm:grid-cols-3 sm:gap-3">
                {passiveChoices.map((p, i) => {
                  const g = PASSIVE_GRADE[p.grade];
                  return (
                    <button
                      key={p.id}
                      onClick={() => choosePassive(p)}
                      className="btn corner group relative overflow-hidden border-2 p-3 text-left sm:p-4"
                      style={{ borderColor: `${p.color}88`, background: `linear-gradient(180deg, ${p.color}1c, rgba(8,6,14,.96))` }}
                    >
                      <div className="mb-2 flex items-center justify-between">
                        <span className="border px-1.5 py-px text-[9px] tracking-[0.2em]" style={{ color: g.color, borderColor: `${g.color}66` }}>
                          {g.label}級パッシブ
                        </span>
                        <KeyCap>{i + 1}</KeyCap>
                      </div>
                      <div className="font-display text-[19px] leading-tight font-bold" style={{ color: p.color }}>{p.name}</div>
                      <p className="mt-2 text-[11px] leading-relaxed text-bone/80">{p.desc}</p>
                      <div className="mt-3 border-t border-white/10 pt-2 text-[9px] tracking-widest text-ash">
                        恒久効果 ／ 重複不可
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          </Overlay>
        )}

        {boons && status === "levelup" && (
          <Overlay>
            <div className="modal-in w-[min(940px,95vw)]">
              <div className="mb-3 text-center">
                <div className="text-[10px] tracking-[0.5em] text-gold/80">LEVEL {snap?.level} REACHED</div>
                <h2 className="title-glow font-display text-[34px] leading-tight font-black tracking-[0.14em] text-gold">祝福を選べ</h2>
                <p className="text-[11px] text-ash">3つのうち1つを選ぶ。この冒険のビルドを育てる</p>
              </div>
              <div className="grid gap-2 sm:grid-cols-3 sm:gap-3">
                {boons.map((b, i) => {
                  const g = BOON_GRADE[b.grade];
                  return (
                    <button
                      key={b.id}
                      onClick={() => choose(b)}
                      className="btn corner group relative overflow-hidden border-2 p-3 text-left sm:p-4"
                      style={{ borderColor: `${g.color}88`, background: `linear-gradient(180deg, ${g.color}1f, rgba(8,6,14,.95))` }}
                    >
                      <div className="mb-2 flex items-center justify-between">
                        <span className="border px-1.5 py-px text-[9px] tracking-[0.2em]" style={{ color: g.color, borderColor: `${g.color}66` }}>
                          {g.label}
                        </span>
                        <KeyCap>{i + 1}</KeyCap>
                      </div>
                      <div className="font-display text-[20px] leading-tight font-bold" style={{ color: g.color }}>
                        {b.name}
                      </div>
                      <p className="mt-2 text-[11px] leading-relaxed text-bone/80">{b.desc}</p>
                      {b.ability && (
                        <div className="mt-2 border-t border-white/10 pt-1.5 text-[10px] text-ash">
                          能力 {ABILITIES[b.ability].name} Lv{Math.min(ABILITIES[b.ability].max, (snap?.abilities[b.ability] ?? 0) + 1)}
                        </div>
                      )}
                      <span className="pointer-events-none absolute inset-0 opacity-0 transition group-hover:opacity-100" style={{ boxShadow: `inset 0 0 60px -20px ${g.color}` }} />
                    </button>
                  );
                })}
              </div>
            </div>
          </Overlay>
        )}

        {loot && status === "loot" && (
          <Overlay>
            <LootModal item={loot.item} cur={loot.cur} onEquip={doEquip} onSmelt={doSmelt} onDrop={doDrop} goldMult={d?.goldMult ?? 1} stashCount={snap?.stashCount ?? 0} stashCap={snap?.stashCap ?? 0} />
          </Overlay>
        )}
      </main>

      {/* ------------------------------------------------- 右：能力 */}
      <aside className="hidden w-[246px] shrink-0 flex-col gap-2 overflow-y-auto border-l border-line bg-abyss2/85 p-2 xl:flex">
        <StatsPanel rows={statRows} d={d} />
        <Panel title="探索ログ">
          <div className="max-h-[220px] space-y-1 overflow-y-auto p-2">
            {logs.length === 0 && <p className="text-[11px] text-ash/50">…</p>}
            {logs.map((l) => (
              <p key={l.id} className="text-[11px] leading-tight" style={{ color: l.color }}>
                ▸ {l.text}
              </p>
            ))}
          </div>
        </Panel>
      </aside>

      {/* モバイルドロワー */}
      {drawer && drawer !== "forge" && (
        <div className="absolute inset-0 z-30 flex xl:hidden" onClick={() => setDrawer(null)}>
          <div className="flex-1 bg-black/70" />
          <div className="w-[280px] overflow-y-auto border-l border-line bg-abyss2 p-2" onClick={(e) => e.stopPropagation()}>
            {drawer === "gear" ? <GearPanel snap={snap} cls={cls} /> : <StatsPanel rows={statRows} d={d} />}
          </div>
        </div>
      )}

      {/* 鍛造パネル */}
      {drawer === "forge" && engineRef.current && (
        <Overlay>
          <ForgePanel
            engine={engineRef.current}
            items={[...engineRef.current.getEquippedItems(), ...engineRef.current.getStashItems()]}
            equippedIds={new Set(engineRef.current.getEquippedItems().map((i) => i.id))}
            materials={snap?.materials ?? {}}
            stashCount={snap?.stashCount ?? 0}
            stashCap={snap?.stashCap ?? 0}
            onClose={closeForge}
          />
        </Overlay>
      )}

      {/* 倉庫確認（階層移動後に自動回収した装備の一括処理） */}
      {lootReviewOpen && engineRef.current && (
        <LootReviewPanel
          engine={engineRef.current}
          items={engineRef.current.getStashItems()}
          equippedIds={new Set(engineRef.current.getEquippedItems().map((i) => i.id))}
          onEquip={(id) => engineRef.current?.equipFromStash(id)}
          onSalvage={(id) => engineRef.current?.salvageFromStash(id)}
          onSalvageAll={() => engineRef.current?.salvageAllFromStash()}
          onDismiss={() => { engineRef.current?.dismissLootReview(); setLootReviewOpen(false); }}
        />
      )}
    </div>
  );
}

/* ---------------------------------------------------------------- 倉庫確認 */

function LootReviewPanel({ engine, items, equippedIds, onEquip, onSalvage, onSalvageAll, onDismiss }: {
  engine: Engine;
  items: Item[];
  equippedIds: Set<string>;
  onEquip: (id: string) => void;
  onSalvage: (id: string) => void;
  onSalvageAll: () => void;
  onDismiss: () => void;
}) {
  const [tick, setTick] = useState(0);
  const refresh = () => setTick((t) => t + 1);
  void engine;
  const unequipped = items.filter((it) => !equippedIds.has(it.id));
  const totalValue = unequipped.reduce((sum, it) => sum + (4 + it.power * 0.6), 0);
  const totalSouls = unequipped.reduce((sum, it) => sum + Math.max(1, Math.round(it.power * 0.05)), 0);

  return (
    <Overlay>
      <div className="modal-in w-[min(1020px,96vw)]">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 className="title-glow font-display text-[24px] font-black tracking-[0.16em] text-jade sm:text-[28px]">収 蔵 庫 確 認</h2>
            <p className="text-[10px] text-ash">階層移動時に自動回収した装備。より強いものがあれば装備し、不要なら解体してゴールド＋魂を得よう</p>
          </div>
          <div className="flex gap-1.5">
            <button
              onClick={() => { onSalvageAll(); refresh(); }}
              disabled={unequipped.length === 0}
              className="btn border border-gold/60 bg-gold/12 px-3 py-1.5 text-[11px] font-bold text-gold disabled:opacity-40"
            >
              全て一括解体 (+{Math.round(totalValue).toLocaleString()}G / +{totalSouls}◈)
            </button>
            <button onClick={onDismiss} className="btn border border-line px-3 py-1.5 text-[11px] text-ash hover:text-bone">閉じる [Esc]</button>
          </div>
        </div>

        <div className="grid max-h-[62vh] gap-1.5 overflow-y-auto p-1 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((it) => {
            const m = RARITY_META[it.rarity];
            const equipped = equippedIds.has(it.id);
            const gold = Math.round(4 + it.power * 0.6);
            const souls = Math.max(1, Math.round(it.power * 0.05));
            return (
              <div key={it.id} className="corner border p-2" style={{ borderColor: `${m.color}55`, background: `linear-gradient(160deg, ${m.bg}, rgba(8,6,14,.92))` }}>
                <div className="flex items-start gap-2">
                  <SlotIcon slot={it.slot} className="mt-0.5 h-5 w-5 shrink-0" style={{ color: m.color }} />
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-display text-[12px] font-bold" style={{ color: m.color }}>{it.name}</div>
                    <div className="flex flex-wrap items-center gap-x-1.5 text-[9px] text-ash">
                      <span>{SLOT_META[it.slot].label}</span>
                      <span className="text-gold/80">P{it.power}</span>
                      {it.forged ? <span className="text-arcane">鍛造×{it.forged}</span> : null}
                    </div>
                  </div>
                </div>
                {equipped ? (
                  <div className="mt-2 border border-jade/50 bg-jade/10 py-1 text-center text-[10px] font-bold text-jade">装備中</div>
                ) : (
                  <div className="mt-2 grid grid-cols-2 gap-1.5">
                    <button onClick={() => { onEquip(it.id); refresh(); }} className="btn border border-jade/50 bg-jade/10 py-1 text-[10px] text-jade">装備</button>
                    <button onClick={() => { onSalvage(it.id); refresh(); }} className="btn border border-gold/50 bg-gold/10 py-1 text-[10px] text-gold">
                      解体 +{gold}G
                    </button>
                  </div>
                )}
              </div>
            );
          })}
          {items.length === 0 && (
            <p className="col-span-full py-8 text-center text-[11px] text-ash/60">倉庫は空です</p>
          )}
        </div>
      </div>
    </Overlay>
  );
}

/* ------------------------------------------------------------------ 部品 */

function ShopCard({ entry, index, gold, onBuy }: { entry: { item: Item; price: number }; index: number; gold: number; onBuy: (i: number) => void }) {
  const it = entry.item;
  const m = RARITY_META[it.rarity];
  const afford = gold >= entry.price;
  return (
    <button
      onClick={() => onBuy(index)}
      disabled={!afford}
      className="btn corner group border-2 p-3 text-left disabled:opacity-45"
      style={{ borderColor: afford ? `${m.color}88` : "#2c2444", background: `linear-gradient(160deg, ${m.bg}, rgba(8,6,14,.95))` }}
    >
      <div className="mb-1 flex items-center justify-between">
        <RarityTag rarity={it.rarity} small />
        <span className="tabular text-[10px] text-gold">P{it.power}</span>
      </div>
      <div className="font-display text-[14px] font-bold leading-tight" style={{ color: m.color }}>{it.name}</div>
      <div className="mt-0.5 flex flex-wrap items-center gap-1 text-[9px] text-ash">
        <span>{SLOT_META[it.slot].label}</span>
        {it.weaponClass && <span style={{ color: WEAPON_CLASSES[it.weaponClass].color }}>{WEAPON_CLASSES[it.weaponClass].name}</span>}
        {it.weaponTrait && (
          <span style={{ color: WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.color }}>
            [{WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.name}]
          </span>
        )}
        {it.cursed && <span className="text-blood">【呪】</span>}
      </div>
      <div className="mt-1.5 space-y-0.5 border-t border-white/5 pt-1.5">
        {it.affixes.slice(0, 4).map((a, j) => (
          <div key={j} className="flex justify-between text-[10px]">
            <span className="text-ash truncate">{a.label}</span>
            <span className={a.value < 0 ? "text-blood tabular" : "text-jade tabular"}>{a.text}</span>
          </div>
        ))}
      </div>
      <div className={`mt-2 border px-2 py-1 text-center text-[11px] font-bold tracking-widest ${afford ? "border-gold/60 bg-gold/12 text-gold" : "border-line text-ash/50"}`}>
        {afford ? "購入する" : "金が足りない"} — {entry.price.toLocaleString()}G
      </div>
    </button>
  );
}

function Overlay({ children }: { children: ReactNode }) {
  return (
    <div className="absolute inset-0 z-20 flex items-start justify-center overflow-y-auto bg-black/78 p-3 backdrop-blur-[3px] sm:items-center">
      <div className="my-auto w-full flex justify-center">{children}</div>
    </div>
  );
}

function Chip({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="border border-line bg-black/50 px-2 py-1 text-right backdrop-blur-[2px]">
      <div className="text-[8px] tracking-[0.24em] text-ash">{label}</div>
      <div className="tabular text-[13px] leading-tight font-bold" style={{ color }}>
        {value}
      </div>
    </div>
  );
}

function SkillSlot({ label, keyHint, color, ratio, ready, big }: { label: string; keyHint: string; color: string; ratio: number; ready?: boolean; big?: boolean }) {
  const size = big ? "h-[68px] w-[68px]" : "h-[56px] w-[56px]";
  return (
    <div className="relative">
      <div
        className={`${size} relative flex flex-col items-center justify-center overflow-hidden border-2 bg-black/65 backdrop-blur-[2px]`}
        style={{ borderColor: ready ? color : "#2c2444", boxShadow: ready ? `0 0 24px -6px ${color}` : undefined }}
      >
        <div className="absolute inset-x-0 bottom-0 bg-white/10" style={{ height: `${Math.max(0, Math.min(1, ratio)) * 100}%`, background: `${color}44` }} />
        <span className="relative font-display text-[10px] leading-tight font-bold" style={{ color: ready ? color : "#8d86a3" }}>
          {label}
        </span>
        <span className="relative mt-0.5 text-[8px] tracking-widest text-ash">{keyHint}</span>
      </div>
    </div>
  );
}

function TouchButton({ label, sub, color, size, ratio, ready, onPress }: { label: string; sub: string; color: string; size: number; ratio: number; ready: boolean; onPress: (e: ReactPointerEvent) => void }) {
  const r = Math.max(0, Math.min(1, ratio));
  const circ = 2 * Math.PI * 46;
  return (
    <button
      onPointerDown={onPress}
      onContextMenu={(e) => e.preventDefault()}
      className={`no-touch relative flex items-center justify-center rounded-full border-2 bg-black/60 backdrop-blur-[2px] active:scale-95 ${ready ? "pulse-ring" : ""}`}
      style={{ width: size, height: size, borderColor: ready ? color : "#2c2444", boxShadow: ready ? `0 0 28px -4px ${color}` : undefined, touchAction: "none" }}
      aria-label={label}
    >
      <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full -rotate-90">
        <circle cx="50" cy="50" r="46" fill="none" stroke="rgba(255,255,255,.08)" strokeWidth="6" />
        <circle cx="50" cy="50" r="46" fill="none" stroke={color} strokeWidth="6" strokeDasharray={circ} strokeDashoffset={circ * (1 - r)} strokeLinecap="round" style={{ transition: "stroke-dashoffset .12s linear", opacity: ready ? 1 : 0.6 }} />
      </svg>
      <span className="relative flex flex-col items-center leading-none">
        <span className="font-display text-[11px] font-bold" style={{ color: ready ? color : "#8d86a3" }}>{label}</span>
        <span className="mt-0.5 text-[8px] tracking-widest text-ash">{sub}</span>
      </span>
    </button>
  );
}

function GearPanel({ snap, cls }: { snap: Snapshot | null; cls: ClassDef }) {
  return (
    <Panel
      title="装備"
      right={
        <span className="text-[10px] text-ash">
          合計 <b className="tabular text-gold">{Object.values(snap?.equipment ?? {}).reduce((a, b) => a + (b?.power ?? 0), 0)}</b>
        </span>
      }
    >
      <div className="space-y-1.5 p-2">
        {SLOTS.map((s) => {
          const it = snap?.equipment[s] ?? null;
          return (
            <div key={s} className="flex items-stretch gap-1.5">
              <div className="flex w-8 shrink-0 flex-col items-center justify-center border border-line bg-abyss/70 text-ash">
                <SlotIcon slot={s} className="h-4 w-4" style={{ color: it ? RARITY_META[it.rarity].color : undefined }} />
              </div>
              <div className="min-w-0 flex-1">
                {it ? (
                  <div
                    className="border px-2 py-1"
                    style={{ borderColor: `${RARITY_META[it.rarity].color}55`, background: RARITY_META[it.rarity].bg }}
                  >
                    <div className="truncate font-display text-[12px] font-bold leading-tight" style={{ color: RARITY_META[it.rarity].color }}>
                      {it.name}
                    </div>
                    <div className="flex items-center gap-1.5 text-[9px] text-ash">
                      <span>{SLOT_META[s].label}</span>
                      <span className="tabular text-gold/80">P{it.power}</span>
                      {it.weaponClass && (
                        <span style={{ color: WEAPON_CLASSES[it.weaponClass].color }}>{WEAPON_CLASSES[it.weaponClass].name}</span>
                      )}
                      {it.weaponTrait && (
                        <span style={{ color: WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.color }}>
                          [{WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.name}]
                        </span>
                      )}
                      {it.forged ? <span className="text-arcane">鍛造×{it.forged}</span> : null}
                      <span className="truncate">{it.affixes.slice(0, 2).map((a) => a.text).join(" ")}</span>
                    </div>
                  </div>
                ) : (
                  <div className="flex h-full items-center border border-dashed border-line/60 px-2 text-[10px] text-ash/45">
                    {SLOT_META[s].label} — 空
                  </div>
                )}
              </div>
            </div>
          );
        })}
        <div className="mt-2 border-t border-line/60 pt-2 text-[10px] leading-relaxed text-ash">
          <span style={{ color: cls.color }}>{cls.name}</span> ／ {cls.attack === "melee" ? "近接型" : "遠距離型"}
          {snap?.weaponClassName && <span className="ml-1">・{snap.weaponClassName}</span>}
          <br />
          運 <b className="tabular text-gold">{Math.round(snap?.derived.luck ?? 0)}</b> ／ 回収範囲{" "}
          <b className="tabular text-bone">{Math.round(snap?.derived.pickup ?? 0)}</b>
        </div>
        {snap && snap.relics.length > 0 && (
          <div className="mt-2 border-t border-line/60 pt-2">
            <div className="mb-1 text-[9px] tracking-[0.28em] text-cyan-300/90">深淵の遺物</div>
            <div className="flex flex-wrap gap-1">
              {snap.relics.map((rel, i) => (
                <span key={i} className="flex items-center gap-1 border px-1.5 py-0.5 text-[9px]" style={{ borderColor: `${rel.color}55`, color: rel.color, background: `${rel.color}12` }}>
                  <span>{rel.icon}</span>{rel.name}
                </span>
              ))}
            </div>
          </div>
        )}
        {snap && snap.passives.length > 0 && (
          <div className="mt-2 border-t border-line/60 pt-2">
            <div className="mb-1 text-[9px] tracking-[0.28em] text-arcane/90">刻まれた型 ({snap.passives.length})</div>
            <div className="flex flex-wrap gap-1">
              {snap.passives.map((name, i) => (
                <span key={i} className="border border-arcane/40 bg-arcane/10 px-1.5 py-0.5 text-[9px] text-arcane">{name}</span>
              ))}
            </div>
          </div>
        )}
        {snap?.autoAim && (
          <div className="mt-2 border border-blood/40 bg-blood/8 p-1.5 text-[9px] leading-snug text-blood/90">
            オートエイム中 — 与ダメ -30% / 会心なし / 攻速 -20% / 射程縮小
          </div>
        )}
      </div>
    </Panel>
  );
}

function AbilityPanel({ chips, boons }: { chips: { id: AbilityId; lv: number; meta: (typeof ABILITIES)[AbilityId] }[]; boons: string[] }) {
  return (
    <Panel title="獲得した力" right={<span className="tabular text-[10px] text-ash">{boons.length}</span>}>
      <div className="space-y-1 p-2">
        {chips.length === 0 && <p className="text-[10px] text-ash/50">まだ能力を得ていない</p>}
        {chips.map((c) => (
          <div key={c.id} className="flex items-center gap-2 border border-line/70 bg-abyss/60 px-2 py-1">
            <span className="h-2 w-2 shrink-0" style={{ background: c.meta.color, boxShadow: `0 0 8px ${c.meta.color}` }} />
            <span className="flex-1 truncate text-[11px] text-bone">{c.meta.name}</span>
            <span className="flex gap-0.5">
              {Array.from({ length: c.meta.max }, (_, i) => (
                <span key={i} className="h-2.5 w-1" style={{ background: i < c.lv ? c.meta.color : "rgba(255,255,255,.1)" }} />
              ))}
            </span>
          </div>
        ))}
        {boons.length > 0 && (
          <div className="flex flex-wrap gap-1 border-t border-line/50 pt-1.5">
            {boons.slice(-14).map((b, i) => (
              <span key={i} className="border border-line/70 px-1 py-px text-[9px] text-ash">
                {b}
              </span>
            ))}
          </div>
        )}
      </div>
    </Panel>
  );
}

function StatsPanel({ rows, d }: { rows: { k: StatKey; v: number }[]; d?: Snapshot["derived"] }) {
  return (
    <Panel title="戦闘ステータス">
      <div className="grid grid-cols-2 gap-x-3 gap-y-0.5 p-2">
        <div className="col-span-2 mb-1 grid grid-cols-2 gap-2 border-b border-line/60 pb-1.5">
          <div>
            <div className="text-[9px] tracking-widest text-ash">攻撃力</div>
            <div className="tabular font-display text-[19px] leading-none font-bold text-ember">{nf(d?.damage ?? 0)}</div>
          </div>
          <div>
            <div className="text-[9px] tracking-widest text-ash">攻撃速度</div>
            <div className="tabular font-display text-[19px] leading-none font-bold text-jade">{(d?.attackSpeed ?? 0).toFixed(2)}/s</div>
          </div>
        </div>
        {rows.slice(0, 14).map((r) => (
          <div key={r.k} className="flex items-baseline justify-between gap-1 border-b border-line/25 py-0.5 text-[10px]">
            <span className="truncate text-ash">{STAT_META[r.k].label}</span>
            <span className="tabular shrink-0 font-bold text-bone/90">
              {r.k === "attackSpeedPct" ? `${(d?.attackSpeed ?? 0).toFixed(2)}` : r.k === "moveSpeedPct" ? String(Math.round(r.v)) : STAT_META[r.k].fmt(r.v)}
            </span>
          </div>
        ))}
      </div>
    </Panel>
  );
}

function LootModal({ item, cur, onEquip, onSmelt, onDrop, goldMult, stashCount, stashCap }: { item: Item; cur: Item | null; onEquip: () => void; onSmelt: () => void; onDrop: () => void; goldMult: number; stashCount: number; stashCap: number }) {
  const m = RARITY_META[item.rarity];
  const delta = diffItems(item, cur);
  const better = item.power - (cur?.power ?? 0);
  const smeltGold = Math.round((4 + item.power * 0.6) * goldMult);
  return (
    <div className="modal-in w-[min(760px,95vw)]">
      <div className="mb-2 text-center">
        <div className="text-[10px] tracking-[0.45em] text-ash">ITEM DROP</div>
        <h2 className="font-display text-[24px] leading-tight font-black tracking-[0.12em]" style={{ color: m.color }}>
          {item.name}
        </h2>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <div className="mb-1 flex items-center gap-2">
            <RarityTag rarity={item.rarity} />
            <span className="text-[10px] tracking-[0.25em] text-jade">新規獲得</span>
          </div>
          <ItemCard item={item} highlight={item.rarity === "mythic" || item.rarity === "legendary"} />
        </div>
        <div>
          <div className="mb-1 flex items-center gap-2">
            {cur ? <RarityTag rarity={cur.rarity} /> : <span className="border border-line px-2 py-0.5 text-[10px] text-ash">なし</span>}
            <span className="text-[10px] tracking-[0.25em] text-ash">現在の装備</span>
          </div>
          <ItemCard item={cur} />
        </div>
      </div>

      <div className="mt-3 border border-line bg-panel p-3">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-[10px] tracking-[0.3em] text-ash">総合力比較</span>
          <span className="tabular text-[13px] font-bold">
            <span className="text-gold">{item.power}</span>
            <span className="mx-1 text-ash/50">vs</span>
            <span className="text-ash">{cur?.power ?? 0}</span>
            <span className="ml-2" style={{ color: better > 0 ? "#2dd4a7" : better < 0 ? "#ff5c6e" : "#8d86a3" }}>
              {better > 0 ? "▲" : better < 0 ? "▼" : "■"} {Math.abs(better)}
            </span>
          </span>
        </div>
        {delta.length === 0 ? (
          <p className="text-[11px] text-ash">ステータスに変化なし</p>
        ) : (
          <div className="grid gap-x-4 gap-y-0.5 sm:grid-cols-2">
            {delta.slice(0, 12).map((r) => (
              <div key={r.key} className="flex items-baseline justify-between border-b border-line/30 py-0.5 text-[11px]">
                <span className="text-ash">{r.label}</span>
                <span className="tabular font-bold" style={{ color: r.delta > 0 ? "#2dd4a7" : "#ff5c6e" }}>
                  {r.delta > 0 ? "+" : "−"}
                  {r.text.replace("−", "").replace("-", "")}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-[2fr_1fr_1fr]">
        <button
          onClick={onEquip}
          className="btn border-2 py-3 font-display text-[15px] font-bold tracking-[0.18em]"
          style={{ borderColor: better >= 0 ? "#2dd4a7" : "#f5c542", color: better >= 0 ? "#2dd4a7" : "#f5c542", background: better >= 0 ? "rgba(45,212,167,.12)" : "rgba(245,197,66,.1)" }}
        >
          装備する <span className="ml-1 text-[10px] opacity-70">[E]</span>
        </button>
        <button onClick={onSmelt} className="btn border border-gold/50 bg-gold/10 py-3 text-[12px] tracking-widest text-gold">
          分解 +{smeltGold}G <span className="ml-1 text-[9px] opacity-70">[X]</span>
        </button>
        <button onClick={onDrop} className="btn border border-line py-3 text-[12px] tracking-widest text-ash" disabled={stashCount >= stashCap}>
          倉庫へ {stashCount}/{stashCap} <span className="ml-1 text-[9px] opacity-70">[G]</span>
        </button>
      </div>
      {stashCount >= stashCap && <p className="mt-1.5 text-center text-[10px] text-ember">倉庫が満杯です — 装備すると旧装備は自動売却されます</p>}
    </div>
  );
}


