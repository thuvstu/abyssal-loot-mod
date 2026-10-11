"use client";

import { useState } from "react";
import { ClassDef, MATERIALS, MaterialId, RARITY_META, SLOT_META, WEAPON_CLASSES, WEAPON_TRAITS } from "@/game/core";
import type { Item } from "@/game/core";
import type { RunSummary } from "@/game/engine";
import type { Hero } from "@/game/api";
import type { AchievementDef } from "@/game/progression";
import { ItemCard, Panel, RarityTag, SlotIcon } from "./ui";
import { TRIAL_SIGILS, trialRules } from "@/game/trials";
import { ATTRIBUTES, availablePoints } from "@/game/attributes";
import type { SaveState } from "./Abyss";

type Props = {
  summary: RunSummary;
  cls: ClassDef;
  hero: Hero | null;
  soulsEarned: number;
  runSouls: number;
  trialBonus: number;
  saveState: SaveState;
  saveError: string | null;
  onRetrySave: () => void;
  newAchievements: AchievementDef[];
  rank: number | null;
  /** 踏破時に1つだけ持ち帰る装備 */
  onKeepItem?: (item: Item) => void | Promise<void>;
  /** 現在継承済みの装備名 */
  keptItemName?: string | null;
  /** 継承装備を手放す */
  onDiscardHeirloom?: () => void;
  onRetry: () => void;
  onTitle: () => void;
};

const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;

export default function ResultScreen({ summary, cls, hero, soulsEarned, runSouls, trialBonus, saveState, saveError, onRetrySave, newAchievements, rank, onKeepItem, onDiscardHeirloom, onRetry, onTitle }: Props) {
  const [kept, setKept] = useState<string | null>(null);
  const [keepBusy, setKeepBusy] = useState(false);
  const [keepError, setKeepError] = useState<string | null>(null);
  const trial = trialRules(summary.trialSigils);
  const s = summary;
  const tone = s.trueCleared ? "#ffffff" : s.victory ? "#2dd4a7" : s.abandoned ? "#8d86a3" : "#e0243c";
  const headline = s.trueCleared ? "真 ・ 踏 破" : s.victory ? "深 淵 踏 破" : s.abandoned ? "戦 線 離 脱" : "討 ち 死 に";
  const sub = s.trueCleared
    ? "原初虚無《ニヒル・ゼロ》は静寂に還った。深淵は、本当に終わった。"
    : s.victory
      ? "虚無神ニヒルは消滅した。世界は一時的に息を吹き返す。"
      : s.abandoned
        ? "あなたは逃げ延びた。魂だけは持ち帰れた。"
        : "あなたの肉体は崩れ落ちた。だが魂は祭壇へと還る。";

  const cells: [string, string, string][] = [
    ["到達層", `${s.floor}F`, "#ff8a2b"],
    ["最終Lv", `${s.level}`, "#f5c542"],
    ["撃破数", s.kills.toLocaleString(), "#ff6a7d"],
    ["召喚門破壊", `${s.gatesDestroyed}`, "#e79abb"],
    ["武器奥義", `${s.weaponArtUses}回`, "#94dbe7"],
    ["層の主討伐", `${s.bossesKilled}`, "#ff5c6e"],
    ["最大コンボ", `${s.bestCombo}`, "#e34bff"],
    ["見切り回数", `${s.perfectDodges}`, "#2dd4a7"],
    ...(s.challengeMode === "danmaku" ? [
      ["グレイズ", s.grazeCount.toLocaleString(), "#bfeaff"],
      ["弾反射", s.bulletsReflected.toLocaleString(), "#ff8ac2"],
    ] as [string, string, string][] : []),
    ["平均DPS", s.dps.toLocaleString(), "#2dd4a7"],
    ["回収ゴールド", s.gold.toLocaleString(), "#f5c542"],
    ["生存時間", mmss(s.durationSec), "#e9e3d5"],
    ["SCORE", s.score.toLocaleString(), "#e9e3d5"],
  ];

  return (
    <div className="bg-abyss noise relative min-h-screen overflow-y-auto">
      <div className="pointer-events-none absolute inset-0" style={{ background: `radial-gradient(ellipse 70% 50% at 50% 0%, ${tone}22, transparent 70%)` }} />
      <div className="relative mx-auto w-full max-w-[1080px] px-4 py-8 sm:px-6">
        <div className="mb-6 text-center">
          <div className="text-[10px] tracking-[0.5em] text-ash">RUN COMPLETE ／ {cls.name}・{hero?.name ?? "名もなき者"}</div>
          <h1 className="title-glow mt-1 font-display text-[42px] leading-none font-black tracking-[0.14em] sm:text-[62px]" style={{ color: tone }}>
            {headline}
          </h1>
          <p className="mt-2 text-[12px] text-ash">{sub}</p>
        </div>

        <div role="status" aria-live="polite" className="mb-4 border border-line bg-panel px-4 py-3 text-center text-[12px]">
          {saveState === "saving" && <span className="text-ash">戦績を保存しています…</span>}
          {saveState === "saved" && <span className="text-jade">記録・報酬を保存しました。呪印の条件はサーバーで照合済み。</span>}
          {saveState === "error" && <><p className="text-blood">{saveError ?? "保存に失敗しました。"}</p><button onClick={onRetrySave} className="btn mt-2 border border-gold/60 px-4 py-2 text-gold">記録を再送する</button><p className="mt-2 text-[10px] text-ash">同じ冒険を再送しても報酬は重複しません。</p></>}
        </div>

        <div className="mb-4 flex flex-wrap items-center justify-center gap-3 border border-gold/40 bg-gold/5 px-4 py-3">
          <span className="text-[10px] tracking-[0.3em] text-gold/80">獲得した魂</span>
          <span className="tabular font-display text-[34px] leading-none font-black text-gold title-glow">◈ {soulsEarned.toLocaleString()}</span>
          {soulsEarned !== runSouls && (
            <span className="text-[10px] text-ash">
              （探索 {runSouls.toLocaleString()} + 実績 {(soulsEarned - runSouls).toLocaleString()}）
            </span>
          )}
          <span className="text-[11px] text-ash">
            所持 <b className="tabular text-gold">{(hero?.souls ?? 0).toLocaleString()}</b>
          </span>
          {rank !== null && rank > 0 && (
            <span className="border border-arcane/50 bg-arcane/10 px-2 py-1 text-[11px] text-arcane">
              世界ランク <b className="tabular">#{rank}</b>
            </span>
          )}
          {s.challengeMode === "daily" && (
            <span className="border border-jade/50 bg-jade/10 px-2 py-1 text-[11px] text-jade">
              本日の深淵: {s.challengeName}
            </span>
          )}
        </div>

        <Panel title={`試練の記録 / ${trial.count}枚 · 魂 ×${trial.soulMultiplier.toFixed(2)}`} className="mb-4">
          <div className="space-y-3 p-3">
            <div className="trial-result-row">
              {TRIAL_SIGILS.filter((x) => trial.ids.includes(x.id)).map((x) => <span key={x.id}>{x.name}</span>)}
              {trial.count === 0 && <span>呪印なし</span>}
            </div>
            {saveState === "saved" && <p className="text-[11px] text-ash">探索報酬 <b className="text-bone">{Math.max(0, runSouls - trialBonus).toLocaleString()}</b> ＋ 呪印加算 <b className="text-gold">{trialBonus.toLocaleString()}</b> 魂</p>}
            <div className="border-t border-line/60 pt-2 text-[10px] text-ash">
              <span className="mb-2 block tracking-widest">最終属性配分 ／ 未配分 {availablePoints(s.level, s.attributes)}</span>
              <div className="flex flex-wrap gap-x-4 gap-y-2">{ATTRIBUTES.map((a) => <span key={a.id}>{a.name} <b style={{ color: a.color }}>{s.attributes[a.id]}</b></span>)}</div>
            </div>
          </div>
        </Panel>

        {newAchievements.length > 0 && (
          <div className="mb-4 space-y-1.5">
            {newAchievements.map((a) => (
              <div key={a.id} className="modal-in flex items-center gap-3 border border-gold/50 bg-gold/10 px-3 py-2">
                <span className="text-[20px]">{a.icon}</span>
                <div className="min-w-0 flex-1">
                  <div className="font-display text-[13px] font-bold text-gold">実績解除 — {a.title}</div>
                  <div className="truncate text-[10px] text-ash">{a.description}</div>
                </div>
                <span className="shrink-0 tabular text-[12px] font-bold text-gold">+{a.reward}◈</span>
              </div>
            ))}
          </div>
        )}

        <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {cells.map(([l, v, c]) => (
            <div key={l} className="panel corner px-3 py-2.5">
              <div className="text-[9px] tracking-[0.28em] text-ash">{l}</div>
              <div className="tabular font-display text-[24px] leading-tight font-bold" style={{ color: c }}>
                {v}
              </div>
            </div>
          ))}
        </div>

        {/* 継承装備の選択 — 踏破 / 真討伐時にのみ1点持ち帰れる */}
        {s.canKeepItem && s.finalEquipment.length > 0 && (
          <Panel
            title="継承の宝物庫 / HEIRLOOM"
            tone="panel-gold"
            right={
              <span className="text-[10px] text-ash">
                {kept ? "継承確定" : "1点のみ持ち帰れる"}
              </span>
            }
          >
            <div className="p-3">
              <p className="mb-2.5 text-[11px] leading-relaxed text-ash">
                {keepError && <span className="mb-2 block text-blood" role="alert">{keepError}</span>}
                深淵を踏破した者だけが、<span className="text-gold">1つの装備</span>を現実へ持ち帰ることを許される。
                次の出撃では、それが最初から装備された状態で始まる。
              </p>
              {kept ? (
                <div className="flex items-center gap-3 border border-gold/50 bg-gold/10 p-3">
                  <SlotIcon slot={s.finalEquipment.find((i) => i.name === kept)?.slot ?? "weapon"} className="h-6 w-6 shrink-0" style={{ color: "#f5c542" }} />
                  <div className="min-w-0 flex-1">
                    <div className="text-[10px] tracking-[0.25em] text-gold">次代へ継承する装備</div>
                    <div className="truncate font-display text-[16px] font-bold text-gold">{kept}</div>
                  </div>
                  <span className="shrink-0 border border-jade/60 px-2 py-1 text-[10px] text-jade">決定</span>
                  <button
                    onClick={() => { setKept(null); onDiscardHeirloom?.(); }}
                    className="btn shrink-0 border border-line px-2 py-1 text-[10px] text-ash hover:text-bone"
                  >
                    選び直す
                  </button>
                </div>
              ) : (
                <div className="grid max-h-[300px] gap-1.5 overflow-y-auto sm:grid-cols-2">
                  {s.finalEquipment.map((it) => {
                    const m = RARITY_META[it.rarity];
                    return (
                      <button
                        key={it.id}
                        disabled={saveState !== "saved" || keepBusy}
                        onClick={async () => {
                          if (!onKeepItem || keepBusy) return;
                          setKeepBusy(true); setKeepError(null);
                          try { await onKeepItem(it); setKept(it.name); }
                          catch (e) { setKeepError(e instanceof Error ? e.message : "保存できませんでした。"); }
                          finally { setKeepBusy(false); }
                        }}
                        className="btn corner border p-2 text-left transition"
                        style={{ borderColor: `${m.color}55`, background: `linear-gradient(160deg, ${m.bg}, rgba(8,6,14,.92))` }}
                      >
                        <div className="flex items-start gap-2">
                          <SlotIcon slot={it.slot} className="mt-0.5 h-5 w-5 shrink-0" style={{ color: m.color }} />
                          <div className="min-w-0 flex-1">
                            <div className="truncate font-display text-[12px] font-bold" style={{ color: m.color }}>{it.name}</div>
                            <div className="flex flex-wrap items-center gap-x-1.5 text-[9px] text-ash">
                              <span>{SLOT_META[it.slot].label}</span>
                              <span className="text-gold/80">P{it.power}</span>
                              {it.weaponClass && <span style={{ color: WEAPON_CLASSES[it.weaponClass].color }}>{WEAPON_CLASSES[it.weaponClass].name}</span>}
                              {it.weaponTrait && (
                                <span style={{ color: WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.color }}>
                                  [{WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.name}]
                                </span>
                              )}
                              {it.forged ? <span className="text-arcane">鍛造×{it.forged}</span> : null}
                            </div>
                          </div>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </Panel>
        )}

        <div className="mt-5 grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
          <div className="space-y-4">
            <Panel title={`最終ビルド / ${s.build.length} 祝福`}>
              <div className="flex flex-wrap gap-1.5 p-3">
                {s.build.length === 0 && <p className="text-[11px] text-ash/60">祝福を得る前に倒れた…</p>}
                {s.build.map((b, i) => (
                  <span key={i} className="border border-line bg-abyss2 px-2 py-1 text-[11px] text-bone/85">
                    <span className="mr-1 text-ash/50 tabular">{String(i + 1).padStart(2, "0")}</span>
                    {b}
                  </span>
                ))}
              </div>
            </Panel>

            {Object.values(s.materials).some((v) => (v ?? 0) > 0) && (
              <Panel title="収集した鍛造素材">
                <div className="flex flex-wrap gap-2 p-3">
                  {(Object.keys(MATERIALS) as MaterialId[]).filter((k) => (s.materials[k] ?? 0) > 0).map((k) => (
                    <span key={k} className="flex items-center gap-1.5 border px-2 py-1 text-[11px]"
                      style={{ borderColor: `${MATERIALS[k].color}44`, background: `${MATERIALS[k].color}0a` }}>
                      <span style={{ color: MATERIALS[k].color }}>{MATERIALS[k].icon}</span>
                      <span className="text-ash">{MATERIALS[k].name}</span>
                      <span className="tabular font-bold" style={{ color: MATERIALS[k].color }}>{s.materials[k]}</span>
                    </span>
                  ))}
                </div>
                <p className="px-3 pb-3 text-[10px] text-ash/70">素材は冒険ごとにリセットされます。次の潜行で鍛造に活用しよう。</p>
              </Panel>
            )}

            {s.artifactsFound.length > 0 && (
              <Panel title={`今回の発見 / 神話級 ${s.artifactsFound.length}点`}>
                <div className="grid gap-2 p-3 sm:grid-cols-2">
                  {s.artifactsFound.slice(0, 8).map((a, i) => {
                    const m = RARITY_META[(a.rarity as keyof typeof RARITY_META) ?? "legendary"];
                    return (
                      <div key={i} className="corner border p-2" style={{ borderColor: `${m.color}55`, background: m.bg }}>
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate font-display text-[13px] font-bold" style={{ color: m.color }}>
                            {a.name}
                          </span>
                          <span className="tabular shrink-0 text-[10px] text-gold">P{a.power}</span>
                        </div>
                        <div className="mt-0.5 text-[10px] text-ash">
                          {m.label} ／ {a.floor}F 発見
                        </div>
                        <div className="mt-1 flex flex-wrap gap-1">
                          {a.affixes.slice(0, 4).map((x, j) => (
                            <span key={j} className="border border-white/10 px-1 text-[9px] text-jade/90">
                              {x}
                            </span>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Panel>
            )}
          </div>

          <div className="space-y-4">
            <Panel title="最高の戦利品">
              <div className="p-3">
                {s.bestItem ? <ItemCard item={s.bestItem} highlight /> : <p className="text-[11px] text-ash/60">装備なし</p>}
                {s.weaponName && (
                  <div className="mt-2 flex items-center gap-2 border-t border-line/60 pt-2 text-[11px] text-ash">
                    <span>最終武器</span>
                    <RarityTag rarity={(s.rarity as keyof typeof RARITY_META) ?? "normal"} small />
                    <span className="truncate" style={{ color: RARITY_META[(s.rarity as keyof typeof RARITY_META) ?? "normal"].color }}>
                      {s.weaponName}
                    </span>
                  </div>
                )}
              </div>
            </Panel>

            <Panel title="英雄の記録">
              <div className="space-y-1 p-3 text-[11px]">
                {[
                  ["最深到達層", `${hero?.bestFloor ?? 0}F`],
                  ["最高スコア", (hero?.bestScore ?? 0).toLocaleString()],
                  ["累計撃破", (hero?.totalKills ?? 0).toLocaleString()],
                  ["出撃回数", String(hero?.totalRuns ?? 0)],
                  ["踏破回数", String(hero?.victories ?? 0)],
                ].map(([l, v]) => (
                  <div key={l} className="flex justify-between border-b border-line/40 py-1">
                    <span className="text-ash">{l}</span>
                    <span className="tabular font-bold text-bone">{v}</span>
                  </div>
                ))}
              </div>
            </Panel>
          </div>
        </div>

        <div className="mt-6 grid gap-2 sm:grid-cols-2">
          <button
            onClick={onRetry}
            disabled={saveState !== "saved" || keepBusy}
            className="btn corner border-2 border-blood bg-gradient-to-b from-blood/80 to-blood/35 py-4 font-display text-[19px] font-black tracking-[0.24em] text-white shadow-[0_0_46px_-14px_#e0243c]"
          >
            再 度 出 撃
          </button>
          <button onClick={onTitle} className="btn corner border border-line bg-panel2 py-4 font-display text-[16px] font-bold tracking-[0.24em] text-ash hover:text-bone">
            祭壇へ戻る（魂を使う）
          </button>
        </div>
        <p className="mt-3 text-center text-[10px] tracking-widest text-ash/60">
          {saveState === "saved" ? "戦績は保存済みです。祭壇で魂を使い、次のビルドへ。" : "戦績の保存完了を確認してから再出撃してください。"}
        </p>
      </div>
    </div>
  );
}
