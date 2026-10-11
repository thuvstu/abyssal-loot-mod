"use client";

import { useCallback, useMemo, useRef, useState } from "react";
import { CLASSES, type ClassDef, type Item } from "@/game/core";
import type { RunSummary } from "@/game/engine";
import { standardRun, type RunConfig, type AchievementDef } from "@/game/progression";
import { EMPTY_BOARD, type BoardData, type Hero, buyMeta, discardHeirloom, ensureHero, fetchBoard, openExpedition, saveHeirloom, submitRun, toEngineItem } from "@/game/api";
import TitleScreen from "./TitleScreen";
import GameScreen from "./GameScreen";
import ResultScreen from "./ResultScreen";

type View = "title" | "game" | "result";
export type SaveState = "idle" | "saving" | "saved" | "error";

export default function Abyss({ initial }: { initial: BoardData }) {
  const [board, setBoard] = useState(initial ?? EMPTY_BOARD);
  const [hero, setHero] = useState<Hero | null>(null);
  const [view, setView] = useState<View>("title");
  const [cls, setCls] = useState<ClassDef>(CLASSES[0]);
  const [runConfig, setRunConfig] = useState<RunConfig>(standardRun());
  const [summary, setSummary] = useState<RunSummary | null>(null);
  const [soulsEarned, setSoulsEarned] = useState(0);
  const [runSouls, setRunSouls] = useState(0);
  const [trialBonus, setTrialBonus] = useState(0);
  const [newAchievements, setNewAchievements] = useState<AchievementDef[]>([]);
  const [rank, setRank] = useState<number | null>(null);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [runKey, setRunKey] = useState(0);
  const heroRef = useRef<Hero | null>(null);
  const expeditionRef = useRef<string | null>(null);
  const launchLock = useRef(false);
  const saveLock = useRef(false);
  const purchaseLock = useRef(false);
  heroRef.current = hero;

  const handleStart = useCallback(async (c: ClassDef, name: string, config: RunConfig) => {
    if (launchLock.current || saveLock.current) return;
    launchLock.current = true; setStarting(true); setError(null);
    try {
      const { hero: loaded } = await ensureHero(name, c.id);
      const opened = await openExpedition(loaded.id, c.id, config);
      heroRef.current = loaded; setHero(loaded);
      expeditionRef.current = opened.expeditionId;
      setCls(c); setRunConfig(opened.config);
      setSummary(null); setRank(null); setSoulsEarned(0); setRunSouls(0); setTrialBonus(0);
      setNewAchievements([]); setSaveState("idle"); setSaveError(null);
      setRunKey((key) => key + 1); setView("game");
    } catch (e) {
      setError(e instanceof Error ? e.message : "出撃を登録できませんでした。");
      setView("title");
    } finally { launchLock.current = false; setStarting(false); }
  }, []);

  const handleBuy = useCallback(async (key: string) => {
    const h = heroRef.current;
    if (!h || purchaseLock.current) return;
    purchaseLock.current = true; setError(null);
    try { const res = await buyMeta(h.id, key); heroRef.current = res.hero; setHero(res.hero); }
    catch (e) { setError(e instanceof Error ? e.message : "強化に失敗しました。"); }
    finally { purchaseLock.current = false; }
  }, []);

  const handleRename = useCallback(async (name: string) => {
    if (launchLock.current || heroRef.current?.name === name) return;
    try {
      const res = await ensureHero(name, cls.id);
      // Never replace the active hero with a late input-blur response during launch.
      if (!launchLock.current) { heroRef.current = res.hero; setHero(res.hero); }
    } catch (e) { setError(e instanceof Error ? e.message : "英雄を読み込めませんでした。"); }
  }, [cls.id]);

  const handleEnd = useCallback(async (s: RunSummary) => {
    if (saveLock.current) return;
    setSummary(s); setView("result"); setSaveState("saving"); setSaveError(null);
    const h = heroRef.current, expeditionId = expeditionRef.current;
    if (!h || !expeditionId) { setSaveState("error"); setSaveError("出撃記録が見つかりません。"); return; }
    saveLock.current = true;
    try {
      const result = await submitRun({
        heroId: h.id, expeditionId, classId: cls.id,
        floor: s.floor, level: s.level, kills: s.kills, gold: s.gold, score: s.score, durationSec: s.durationSec,
        victory: s.victory, weaponName: s.weaponName, rarity: s.rarity, build: s.build, artifactsFound: s.artifactsFound,
        challengeMode: s.challengeMode, challengeId: s.challengeId, challengeName: s.challengeName, challengeDay: s.challengeDay,
        difficulty: s.difficulty, relics: s.relics, eventsCompleted: s.eventsCompleted, bossesKilled: s.bossesKilled,
        bestCombo: s.bestCombo, perfectDodges: s.perfectDodges, trueCleared: s.trueCleared, pinnacleCleared: s.pinnacleCleared,
        trialSigils: s.trialSigils, trialCount: s.trialSigils.length, attributes: s.attributes,
        gatesDestroyed: s.gatesDestroyed, weaponArtUses: s.weaponArtUses,
      });
      heroRef.current = result.hero; setHero(result.hero);
      setSoulsEarned(result.soulsEarned); setRunSouls(result.runSouls); setTrialBonus(result.trialBonus);
      setNewAchievements(result.achievements); setSaveState("saved");
      // Leaderboard refresh must not turn a successful settlement into a failure.
      void fetchBoard().then((b) => {
        setBoard(b);
        const entries = s.challengeMode === "daily" ? b.dailyBoard : b.board;
        const i = entries.findIndex((entry) => entry.id === result.run.id);
        setRank(i >= 0 ? i + 1 : null);
      }).catch(() => undefined);
    } catch (e) {
      setSaveState("error"); setSaveError(e instanceof Error ? e.message : "戦績の保存に失敗しました。");
    } finally { saveLock.current = false; }
  }, [cls.id]);

  const handleQuit = useCallback(() => {
    setView("title");
    void fetchBoard().then(setBoard).catch(() => undefined);
  }, []);

  const handleKeepItem = useCallback(async (item: Item) => {
    const h = heroRef.current;
    if (!h || saveState !== "saved") throw new Error("先に戦績の保存を完了してください。");
    const res = await saveHeirloom(h.id, item);
    heroRef.current = res.hero; setHero(res.hero);
  }, [saveState]);

  const handleDiscardHeirloom = useCallback(async () => {
    const h = heroRef.current;
    if (!h) return;
    try { const res = await discardHeirloom(h.id); heroRef.current = res.hero; setHero(res.hero); }
    catch (e) { setError(e instanceof Error ? e.message : "継承装備を更新できませんでした。"); }
  }, []);

  const heirloom = useMemo(() => toEngineItem(hero?.heirloom), [hero?.heirloom]);
  if (view === "game") return <GameScreen key={runKey} cls={cls} hero={hero} heirloom={heirloom} runConfig={runConfig} onEnd={handleEnd} onQuit={handleQuit} />;
  if (view === "result" && summary) return (
    <ResultScreen summary={summary} cls={cls} hero={hero} soulsEarned={soulsEarned} runSouls={runSouls}
      trialBonus={trialBonus} saveState={saveState} saveError={saveError} onRetrySave={() => void handleEnd(summary)}
      newAchievements={newAchievements} rank={rank} onKeepItem={handleKeepItem} keptItemName={hero?.heirloom?.name ?? null}
      onDiscardHeirloom={handleDiscardHeirloom} onRetry={() => { if (hero && saveState === "saved") void handleStart(cls, hero.name, runConfig); }} onTitle={handleQuit} />
  );
  return <TitleScreen board={board} hero={hero} onStart={handleStart} onBuy={handleBuy} onRename={handleRename}
    onClass={setCls} onDiscardHeirloom={handleDiscardHeirloom} starting={starting} error={error} />;
}
