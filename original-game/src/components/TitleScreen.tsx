"use client";

import { useEffect, useMemo, useState } from "react";
import { CLASSES, ClassDef, META_UPGRADES, RARITY_META, Rarity, classById, metaCost } from "@/game/core";
import type { AchievementView, BoardData, Hero } from "@/game/api";
import { fetchProgression } from "@/game/api";
import { getDailyRun, standardRun, buildRun, RUN_MODES } from "@/game/progression";
import type { RunConfig, RunMode } from "@/game/progression";
import { DIFFICULTIES } from "@/game/core";
import type { Difficulty } from "@/game/core";
import { isMuted, setMuted } from "@/game/audio";
import { Bar, ItemCard, KeyCap, Panel, SlotIcon } from "./ui";
import { toEngineItem } from "@/game/api";
import type { Slot } from "@/game/core";
import TrialSelector from "./TrialSelector";
import { validateSigils, type SigilId } from "@/game/trials";

type Props = {
  board: BoardData;
  hero: Hero | null;
  onStart: (cls: ClassDef, name: string, config: RunConfig) => void;
  onBuy: (key: string) => void;
  onRename: (name: string) => void;
  onClass: (c: ClassDef) => void;
  onDiscardHeirloom: () => void;
  starting: boolean;
  error: string | null;
};

const cls = (id: string) => classById(id);

export default function TitleScreen({ board, hero, onStart, onBuy, onRename, onClass, onDiscardHeirloom, starting, error }: Props) {
  const [sel, setSel] = useState<ClassDef>(CLASSES[0]);
  const [name, setName] = useState(hero?.name ?? "");
  useEffect(() => {
    // Server and first client render use identical markup; restore browser preferences after hydration.
    try {
      const savedClass = localStorage.getItem("abyss_last_class");
      const savedName = localStorage.getItem("abyss_last_name");
      if (savedClass) { const restored = classById(savedClass); setSel(restored); onClass(restored); }
      if (savedName) setName((n) => n || savedName);
    } catch { /* Browser storage may be disabled. */ }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const [tab, setTab] = useState<"rank" | "daily" | "relic" | "achieve" | "guide">("rank");
  const [mode, setMode] = useState<RunMode>("standard");
  const [difficulty, setDifficulty] = useState<Difficulty>("normal");
  const [trialSigils, setTrialSigils] = useState<SigilId[]>([]);
  useEffect(() => {
    try { const saved = localStorage.getItem("abyss_trial_sigils"); if (saved) setTrialSigils(validateSigils(JSON.parse(saved))); } catch { /* Invalid stored settings are ignored. */ }
  }, []);
  const chooseSigils = (ids: SigilId[]) => {
    setTrialSigils(ids);
    try { localStorage.setItem("abyss_trial_sigils", JSON.stringify(ids)); } catch { /* Storage may be disabled. */ }
  };
  const [muted, setMutedState] = useState(false);
  const [achievements, setAchievements] = useState<AchievementView[] | null>(null);
  const dailyRun = useMemo(() => getDailyRun(), []);
  const runConfig: RunConfig = { ...(mode === "daily" ? { ...dailyRun, difficulty } : buildRun(mode, difficulty)), trialSigils };
  const heirloomItem = useMemo(() => toEngineItem(hero?.heirloom), [hero?.heirloom]);

  useEffect(() => { setMutedState(isMuted()); }, []);
  useEffect(() => {
    if (!hero) return;
    fetchProgression(hero.id).then((r) => setAchievements(r.achievements)).catch(() => undefined);
  }, [hero]);

  const toggleMute = () => {
    const next = !muted;
    setMuted(next);
    setMutedState(next);
  };

  const embers = useMemo(
    () =>
      Array.from({ length: 22 }, (_, i) => ({
        left: `${(i * 37) % 100}%`,
        size: 2 + ((i * 13) % 5),
        dur: 7 + ((i * 29) % 11),
        delay: -((i * 17) % 12),
        color: i % 4 === 0 ? "#f5c542" : i % 3 === 0 ? "#2dd4a7" : "#e0243c",
      })),
    [],
  );

  const souls = hero?.souls ?? 0;
  const meta = hero?.meta ?? {};

  return (
    <div className="bg-abyss noise relative min-h-screen overflow-x-hidden">
      <div className="grid-lines pointer-events-none absolute inset-0 opacity-60" />
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        {embers.map((e, i) => (
          <span
            key={i}
            className="ember"
            style={{
              left: e.left,
              width: e.size,
              height: e.size,
              background: e.color,
              boxShadow: `0 0 ${e.size * 4}px ${e.color}`,
              animationDuration: `${e.dur}s`,
              animationDelay: `${e.delay}s`,
            }}
          />
        ))}
      </div>

      <div className="relative mx-auto w-full max-w-[1420px] px-4 py-7 sm:px-6 lg:py-10">
        <button
          onClick={toggleMute}
          aria-label={muted ? "サウンドをオンにする" : "サウンドをオフにする"}
          className="btn absolute top-4 right-4 z-10 border border-line bg-black/50 px-2.5 py-1.5 text-[10px] tracking-widest text-ash hover:text-bone sm:top-6 sm:right-6"
        >
          {muted ? "♪ OFF" : "♪ ON"}
        </button>

        {/* ---------------------------------------------------------- 頭部 */}
        <header className="mb-7 text-center">
          <div className="mb-2 flex items-center justify-center gap-3 text-[10px] tracking-[0.55em] text-ember/80">
            <span className="h-px w-10 bg-gradient-to-r from-transparent to-ember/70" />
            HACK &amp; SLASH × ROGUELITE
            <span className="h-px w-10 bg-gradient-to-l from-transparent to-ember/70" />
          </div>
          <h1 className="title-glow flicker font-display text-[40px] leading-[0.95] font-black tracking-[0.06em] text-bone sm:text-[62px] lg:text-[78px]">
            <span className="text-blood">深</span>淵の略奪者
          </h1>
          <p className="mt-1 font-display text-[13px] tracking-[0.42em] text-arcane/90 sm:text-[16px]">A B Y S S A L &nbsp; L O O T</p>
          <p className="mx-auto mt-4 max-w-2xl text-[12px] leading-relaxed text-ash sm:text-[13px]">
            無限に生成される装備、祝福、そして迷宮。
            <span className="text-bone">第25層</span>に眠る<span className="text-blood">虚無神ニヒル</span>を討ち果たすまで、死ねば全てを失う。
            持ち帰れるのは<span className="text-gold">魂</span>だけだ。
          </p>
        </header>

        {/* -------------------------------------------------------- 実績帯 */}
        <div className="mb-6 grid grid-cols-2 gap-px overflow-hidden border border-line bg-line sm:grid-cols-4">
          {[
            { l: "総出撃", v: board.totals.runs.toLocaleString() },
            { l: "総撃破数", v: board.totals.kills.toLocaleString() },
            { l: "世界最深層", v: `${board.totals.bestFloor}F` },
            { l: "あなたの魂", v: souls.toLocaleString() },
          ].map((s) => (
            <div key={s.l} className="bg-abyss2 px-3 py-2.5 text-center">
              <div className="text-[10px] tracking-[0.25em] text-ash">{s.l}</div>
              <div className="tabular font-display text-[20px] font-bold text-gold sm:text-[24px]">{s.v}</div>
            </div>
          ))}
        </div>

        <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_400px]">
          {/* --------------------------------------------------- 左：出撃 */}
          <div className="space-y-5">
            <Panel title="英雄登録 / HERO">
              <div className="p-3">
                <label className="mb-1.5 block text-[10px] tracking-[0.3em] text-ash">名前</label>
                <div className="flex flex-wrap gap-2">
                  <input
                    value={name}
                    maxLength={16}
                    onChange={(e) => setName(e.target.value)}
                    onBlur={() => name.trim() && onRename(name.trim())}
                    placeholder="例：アッシュ"
                    className="min-w-[180px] flex-1 border border-line bg-abyss px-3 py-2.5 font-display text-[15px] tracking-wider text-bone outline-none placeholder:text-ash/40 focus:border-ember/70 focus:shadow-[0_0_0_1px_rgba(255,138,43,.35)]"
                  />
                  {hero && (
                    <div className="flex items-center gap-3 border border-line bg-panel2 px-3 text-[11px] text-ash">
                      <span>
                        最深 <b className="text-bone">{hero.bestFloor}F</b>
                      </span>
                      <span>
                        最高スコア <b className="text-gold">{hero.bestScore.toLocaleString()}</b>
                      </span>
                      <span>
                        出撃 <b className="text-bone">{hero.totalRuns}</b>
                      </span>
                      {hero.victories > 0 && <span className="text-jade">踏破 {hero.victories}</span>}
                    </div>
                  )}
                </div>

                <div className="mt-4 mb-2 flex items-baseline justify-between">
                  <span className="text-[10px] tracking-[0.3em] text-ash">職業選択</span>
                  <span className="text-[10px] text-ash/70">{CLASSES.length} CLASS</span>
                </div>

                <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                  {CLASSES.map((c) => {
                    const on = sel.id === c.id;
                    return (
                      <button
                        key={c.id}
                        onClick={() => {
                          setSel(c);
                          onClass(c);
                        }}
                        className={`btn corner group relative overflow-hidden border p-3 text-left ${on ? "bg-panel2" : "border-line bg-abyss2/60 hover:border-line"}`}
                        style={on ? { borderColor: c.color, boxShadow: `0 0 0 1px ${c.color}55, 0 0 34px -14px ${c.color}` } : undefined}
                      >
                        <div className="flex items-center gap-2">
                          <span
                            className="flex h-9 w-9 shrink-0 items-center justify-center border"
                            style={{ borderColor: `${c.color}66`, background: `${c.color}18`, color: c.color }}
                          >
                            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                              {c.attack === "melee" ? (
                                <>
                                  <path d="M4 20 L14 10" />
                                  <path d="M12 4 L20 4 L20 12 L14 18 L6 18 Z" />
                                </>
                              ) : (
                                <>
                                  <circle cx="12" cy="12" r="3" />
                                  <path d="M12 2 V6 M12 18 V22 M2 12 H6 M18 12 H22" />
                                </>
                              )}
                            </svg>
                          </span>
                          <div className="min-w-0">
                            <div className="font-display text-[17px] leading-none font-bold" style={{ color: on ? c.color : "#e9e3d5" }}>
                              {c.name}
                            </div>
                            <div className="mt-1 text-[10px] tracking-wider text-ash">{c.title}</div>
                          </div>
                        </div>
                        <p className="mt-2 line-clamp-2 text-[11px] leading-snug text-ash">{c.desc}</p>
                        <div className="mt-2.5 space-y-1">
                          {[
                            { l: "耐久", v: c.base.hp / 200, col: "#ff4d5e" },
                            { l: "火力", v: c.base.damage * c.base.attackSpeed / 70, col: "#f5c542" },
                            { l: "俊敏", v: c.base.moveSpeed / 320, col: "#2dd4a7" },
                          ].map((b) => (
                            <div key={b.l} className="flex items-center gap-2">
                              <span className="w-7 shrink-0 text-[9px] text-ash">{b.l}</span>
                              <Bar value={Math.min(1, b.v)} max={1} color={b.col} h={4} glow={false} />
                            </div>
                          ))}
                        </div>
                        <div className="mt-2 border-t border-white/5 pt-1.5 text-[10px] text-ash">
                          <span style={{ color: c.color }}>必殺</span> {c.skillName}
                        </div>
                      </button>
                    );
                  })}
                </div>

                <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                  <button
                    onClick={() => setMode("standard")}
                    className={`btn corner border p-3 text-left transition ${mode === "standard" ? "border-jade/70 bg-jade/10" : "border-line bg-abyss2/50 hover:border-line"}`}
                  >
                    <div className="flex items-center justify-between">
                      <span className={`font-display text-[15px] font-bold ${mode === "standard" ? "text-jade" : "text-bone"}`}>{RUN_MODES.standard.name}</span>
                      <span className="text-[9px] tracking-widest text-ash">{RUN_MODES.standard.tag}</span>
                    </div>
                    <p className="mt-1 text-[10px] leading-relaxed text-ash">{RUN_MODES.standard.desc}</p>
                  </button>

                  {(["bossrush", "endless", "covenant", "danmaku", "pinnacle"] as const).map((m) => {
                    const def = RUN_MODES[m];
                    const on = mode === m;
                    return (
                      <button
                        key={m}
                        onClick={() => setMode(m)}
                        className={`btn corner border p-3 text-left transition ${on ? "bg-panel2" : "border-line bg-abyss2/50 hover:border-line"}`}
                        style={on ? { borderColor: def.color, boxShadow: `0 0 0 1px ${def.color}55, inset 0 0 30px -18px ${def.color}` } : undefined}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-display text-[15px] font-bold" style={{ color: on ? def.color : "#e9e3d5" }}>{def.name}</span>
                          <span className="shrink-0 border px-1.5 py-0.5 text-[9px]" style={{ color: def.color, borderColor: `${def.color}66` }}>{def.tag}</span>
                        </div>
                        <p className="mt-1 text-[10px] leading-relaxed text-ash">{def.desc}</p>
                        <div className="mt-1.5 flex gap-2 text-[9px]">
                          <span className="text-gold">SCORE ×{def.scoreMul}</span>
                          <span className="text-arcane">魂 ×{def.soulMul}</span>
                        </div>
                      </button>
                    );
                  })}

                  <button
                    onClick={() => setMode("daily")}
                    className={`btn corner border p-3 text-left transition ${mode === "daily" ? "bg-panel2" : "border-line bg-abyss2/50 hover:border-line"}`}
                    style={mode === "daily" ? { borderColor: dailyRun.modifier?.color ?? "#f5c542", boxShadow: `inset 0 0 28px -18px ${dailyRun.modifier?.color ?? "#f5c542"}` } : undefined}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-display text-[15px] font-bold" style={{ color: dailyRun.modifier?.color ?? "#f5c542" }}>今日の深淵</span>
                      <span className="shrink-0 border border-gold/40 px-1.5 py-0.5 text-[9px] text-gold">+{Math.round(((dailyRun.modifier?.soulRate ?? 1) - 1) * 100)}% 魂</span>
                    </div>
                    <div className="mt-1 flex flex-wrap items-baseline gap-x-2">
                      <span className="font-display text-[12px] font-bold" style={{ color: dailyRun.modifier?.color }}>{dailyRun.modifier?.title}</span>
                      <span className="text-[9px] text-ash">{dailyRun.challengeDay} UTC</span>
                    </div>
                    <p className="mt-1 text-[10px] leading-relaxed text-ash">{dailyRun.modifier?.description}</p>
                  </button>
                </div>

                {mode === "covenant" && (
                  <div className="mt-2 border border-arcane/40 bg-arcane/8 px-3 py-2 text-[10px] leading-relaxed text-arcane">
                    不幸の盟約では祝福（レベルアップボーナス）とパッシブが一切得られません。
                    装備・鍛造・パッシブ無しの素のステータスと腕だけで25層を下りる、最上位の挑戦です。
                  </div>
                )}
                {mode === "endless" && (
                  <div className="mt-2 border border-arcane/30 bg-arcane/5 px-3 py-2 text-[10px] leading-relaxed text-ash">
                    無限深淵では25層以降も層が続き、敵は層に比例して際限なく強くなります。踏破条件は「力尽きること」。
                  </div>
                )}
                {mode === "danmaku" && (
                  <div className="mt-2 border border-pink-500/50 bg-pink-500/10 px-3 py-2 text-[10px] leading-relaxed text-pink-200">
                    弾幕の獄：Ctrl / Z で低速移動＆当たり判定を表示。弾をかするとグレイズでボムゲージが溜まり、Xでボムを放って敵弾を一掃。斬撃とダッシュで弾を反射して攻めろ。
                  </div>
                )}
                {mode === "pinnacle" && (
                  <div className="mt-2 border border-blood/50 bg-blood/10 px-3 py-2 text-[10px] leading-relaxed text-blood">
                    終焉の座：神話装備7部位（総合力5000超）と全強化で、タルタロス・オメガに直接挑む極限決戦。
                  </div>
                )}

                {mode === "daily" && (
                  <div className="mt-2 flex flex-wrap items-center justify-between gap-2 border border-gold/20 bg-gold/5 px-3 py-2 text-[10px]">
                    <span className="text-ash">全員同じシード・同じ変異。結果は日替わりランキングに記録。</span>
                    <span className="tabular font-bold text-gold">SCORE ×{dailyRun.modifier?.scoreRate.toFixed(2)}</span>
                  </div>
                )}

                {error && <p className="mt-3 border border-blood/50 bg-blood/10 px-3 py-2 text-[11px] text-blood">{error}</p>}

                <TrialSelector value={trialSigils} onChange={chooseSigils} />

                {/* 難易度選択 */}
                <div className="mt-4">
                  <div className="mb-1.5 text-[10px] tracking-[0.3em] text-ash">難易度</div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    {(Object.keys(DIFFICULTIES) as Difficulty[]).map((d) => {
                      const def = DIFFICULTIES[d];
                      const on = difficulty === d;
                      return (
                        <button
                          key={d}
                          onClick={() => setDifficulty(d)}
                          className={`btn corner border p-2 text-left transition ${on ? "bg-panel2" : "border-line bg-abyss2/50 hover:border-line"}`}
                          style={on ? { borderColor: def.color, boxShadow: `0 0 0 1px ${def.color}55, inset 0 0 26px -16px ${def.color}` } : undefined}
                        >
                          <div className="flex items-center justify-between">
                            <span className="font-display text-[14px] font-bold" style={{ color: on ? def.color : "#e9e3d5" }}>{def.name}</span>
                            <span className="text-[9px] text-ash">SCORE×{def.scoreMul}</span>
                          </div>
                          <p className="mt-1 text-[9px] leading-snug text-ash">{def.desc}</p>
                        </button>
                      );
                    })}
                  </div>
                </div>

                <button
                  disabled={starting || !name.trim()}
                  onClick={() => {
                    try {
                      localStorage.setItem("abyss_last_name", name.trim());
                      localStorage.setItem("abyss_last_class", sel.id);
                    } catch { /* noop */ }
                    onStart(sel, name.trim(), runConfig);
                  }}
                  className="btn corner mt-4 w-full border-2 border-blood bg-gradient-to-b from-blood/85 to-blood/40 py-4 font-display text-[22px] font-black tracking-[0.3em] text-white shadow-[0_0_50px_-12px_#e0243c] disabled:border-line disabled:from-panel2 disabled:to-panel2 disabled:text-ash"
                >
                  {starting ? "接続中…" : "迷 宮 へ 出 撃"}
                </button>
                <p className="mt-2 text-center text-[10px] tracking-widest text-ash/70">
                  選択中：<span style={{ color: sel.color }}>{sel.name}</span> ／ 死は永遠の損失、魂のみ継承される
                </p>
              </div>
            </Panel>

            {/* ----------------------------------------------- タブ領域 */}
            <Panel
              title={
                <span className="flex gap-1">
                  {([
                    ["rank", "深淵ランキング"],
                    ["daily", "今日の深淵"],
                    ["relic", "神話装備殿"],
                    ["achieve", "実績"],
                    ["guide", "操作と指南"],
                  ] as const).map(([k, l]) => (
                    <button
                      key={k}
                      onClick={() => setTab(k)}
                      className={`px-2.5 py-1 text-[11px] tracking-[0.2em] transition ${
                        tab === k ? "bg-blood/25 text-bone" : "text-ash hover:text-bone"
                      }`}
                    >
                      {l}
                    </button>
                  ))}
                </span>
              }
            >
              <div className="max-h-[340px] overflow-y-auto p-3">
                {tab === "rank" && (
                  <div className="space-y-3">
                    {board.board.length === 0 && <Empty text="まだ誰も深淵に到達していない。最初の略奪者になれ。" />}
                    {board.board.length > 0 && (
                      <table className="w-full border-collapse text-[11px]">
                        <thead>
                          <tr className="text-left text-[9px] tracking-[0.2em] text-ash/70">
                            <th className="pb-1.5 font-normal">#</th>
                            <th className="pb-1.5 font-normal">英雄</th>
                            <th className="pb-1.5 font-normal">層</th>
                            <th className="pb-1.5 font-normal">Lv</th>
                            <th className="pb-1.5 font-normal">撃破</th>
                            <th className="pb-1.5 text-right font-normal">SCORE</th>
                          </tr>
                        </thead>
                        <tbody>
                          {board.board.map((r, i) => {
                            const cc = cls(r.classId);
                            return (
                              <tr key={r.id} className="border-t border-line/50">
                                <td className={`py-1.5 tabular ${i < 3 ? "font-display text-[14px] font-bold text-gold" : "text-ash"}`}>{i + 1}</td>
                                <td className="py-1.5">
                                  <div className="flex items-center gap-1.5">
                                    <span className="h-2 w-2 shrink-0" style={{ background: cc.color }} />
                                    <span className="truncate text-bone">{r.heroName}</span>
                                    <span className="text-ash/60">{cc.name}</span>
                                    {r.victory && <span className="border border-jade/60 px-1 text-[9px] text-jade">踏破</span>}
                                  </div>
                                  {r.weaponName && (
                                    <div className="truncate text-[10px]" style={{ color: RARITY_META[(r.rarity ?? "normal") as Rarity].color }}>
                                      {r.weaponName}
                                    </div>
                                  )}
                                </td>
                                <td className="py-1.5 tabular text-ember">{r.floor}F</td>
                                <td className="py-1.5 tabular text-bone">{r.level}</td>
                                <td className="py-1.5 tabular text-ash">{r.kills}</td>
                                <td className="py-1.5 text-right tabular font-bold text-gold">{r.score.toLocaleString()}</td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    )}
                    {board.champions.length > 0 && (
                      <div className="border-t border-line/60 pt-3">
                        <div className="mb-2 text-[10px] tracking-[0.3em] text-ash">歴戦の英雄</div>
                        <div className="grid gap-1.5 sm:grid-cols-2">
                          {board.champions.slice(0, 6).map((c) => (
                            <div key={c.name + c.classId} className="flex items-center justify-between border border-line/60 bg-abyss2/60 px-2 py-1.5 text-[11px]">
                              <span className="flex items-center gap-1.5 truncate">
                                <span className="h-2 w-2" style={{ background: cls(c.classId).color }} />
                                <span className="truncate text-bone">{c.name}</span>
                              </span>
                              <span className="tabular shrink-0 text-ash">
                                {c.bestFloor}F / <span className="text-gold">{c.bestScore.toLocaleString()}</span>
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {tab === "relic" && (
                  <div className="space-y-2">
                    {board.relics.length === 0 && <Empty text="神話級の発見はまだ記録されていない。伝説を刻め。" />}
                    <div className="grid gap-2 sm:grid-cols-2">
                      {board.relics.map((rl, i) => {
                        const m = RARITY_META[(rl.rarity ?? "legendary") as Rarity];
                        return (
                          <div
                            key={i}
                            className="corner relative overflow-hidden border p-2.5"
                            style={{ borderColor: `${m.color}55`, background: `linear-gradient(160deg, ${m.bg}, rgba(8,6,14,.9))` }}
                          >
                            <div className="flex items-start gap-2">
                              <SlotIcon slot={(rl.slot as Slot) ?? "weapon"} className="mt-0.5 h-5 w-5 shrink-0" style={{ color: m.color }} />
                              <div className="min-w-0">
                                <div className="truncate font-display text-[13px] font-bold" style={{ color: m.color }}>
                                  {rl.itemName}
                                </div>
                                <div className="text-[10px] text-ash">
                                  {m.label} ／ 総合力 <b className="text-gold">{rl.power}</b> ／ {rl.floor}F ／ {rl.heroName}
                                </div>
                              </div>
                            </div>
                            <ul className="mt-1.5 space-y-0.5 border-t border-white/5 pt-1.5">
                              {rl.affixes.slice(0, 4).map((a, j) => (
                                <li key={j} className="text-[10px] text-jade/90">
                                  {a}
                                </li>
                              ))}
                            </ul>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {tab === "daily" && (
                  <div className="space-y-3">
                    <div className="border p-2.5" style={{ borderColor: `${dailyRun.modifier?.color}55`, background: `${dailyRun.modifier?.color}0c` }}>
                      <div className="flex items-center justify-between">
                        <span className="font-display text-[15px] font-bold" style={{ color: dailyRun.modifier?.color }}>{dailyRun.modifier?.title}</span>
                        <span className="tabular text-[10px] text-ash">{dailyRun.challengeDay} UTC</span>
                      </div>
                      <p className="mt-1 text-[11px] leading-relaxed text-ash">{dailyRun.modifier?.description}</p>
                    </div>
                    {board.dailyBoard.length === 0 && <Empty text="本日の深淵にはまだ誰も挑んでいない。先陣を切れ。" />}
                    {board.dailyBoard.length > 0 && (
                      <table className="w-full border-collapse text-[11px]">
                        <thead>
                          <tr className="text-left text-[9px] tracking-[0.2em] text-ash/70">
                            <th className="pb-1.5 font-normal">#</th>
                            <th className="pb-1.5 font-normal">英雄</th>
                            <th className="pb-1.5 font-normal">層</th>
                            <th className="pb-1.5 font-normal">撃破</th>
                            <th className="pb-1.5 text-right font-normal">SCORE</th>
                          </tr>
                        </thead>
                        <tbody>
                          {board.dailyBoard.map((r, i) => {
                            const cc = cls(r.classId);
                            return (
                              <tr key={r.id} className="border-t border-line/50">
                                <td className={`py-1.5 tabular ${i < 3 ? "font-display text-[14px] font-bold text-gold" : "text-ash"}`}>{i + 1}</td>
                                <td className="py-1.5">
                                  <div className="flex items-center gap-1.5">
                                    <span className="h-2 w-2 shrink-0" style={{ background: cc.color }} />
                                    <span className="truncate text-bone">{r.heroName}</span>
                                    <span className="text-ash/60">{cc.name}</span>
                                    {r.victory && <span className="border border-jade/60 px-1 text-[9px] text-jade">踏破</span>}
                                  </div>
                                </td>
                                <td className="py-1.5 tabular text-ember">{r.floor}F</td>
                                <td className="py-1.5 tabular text-ash">{r.kills}</td>
                                <td className="py-1.5 text-right tabular font-bold text-gold">{r.score.toLocaleString()}</td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    )}
                  </div>
                )}

                {tab === "achieve" && (
                  <div className="space-y-1.5">
                    {!hero && <Empty text="出撃すると実績の進捗が記録されます" />}
                    {hero && !achievements && <Empty text="読み込み中…" />}
                    {hero && achievements && achievements.map((a) => {
                      const unlocked = !!a.unlockedAt;
                      const tierColor = a.tier === "mythic" ? "#e34bff" : a.tier === "gold" ? "#f5c542" : a.tier === "silver" ? "#bfc8d6" : "#c08552";
                      return (
                        <div key={a.id} className={`flex items-center gap-2.5 border p-2 ${unlocked ? "" : "opacity-50"}`}
                          style={{ borderColor: unlocked ? `${tierColor}55` : "#2c2444", background: unlocked ? `${tierColor}0c` : "transparent" }}>
                          <span className="flex h-8 w-8 shrink-0 items-center justify-center border text-[16px]" style={{ borderColor: `${tierColor}66`, color: tierColor }}>
                            {a.icon}
                          </span>
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <span className="truncate font-display text-[13px] font-bold" style={{ color: unlocked ? tierColor : "#e9e3d5" }}>{a.title}</span>
                              <span className="shrink-0 tabular text-[10px] text-gold">+{a.reward}◈</span>
                            </div>
                            <div className="truncate text-[10px] text-ash">{a.description}</div>
                          </div>
                          {unlocked && <span className="shrink-0 text-[9px] text-jade">達成済</span>}
                        </div>
                      );
                    })}
                  </div>
                )}

                {tab === "guide" && <Guide />}
              </div>
            </Panel>
          </div>

          {/* --------------------------------------------- 右：魂の祭壇 */}
          <div className="space-y-5">
            {/* 継承装備 */}
            <Panel
              title="継承装備 / HEIRLOOM"
              tone="panel-gold"
              right={
                hero?.trueClears ? (
                  <span className="border border-white/40 px-1.5 py-0.5 text-[9px] text-white">真討伐 {hero.trueClears}回</span>
                ) : undefined
              }
            >
              <div className="p-3">
                {heirloomItem ? (
                  <>
                    <p className="mb-2 text-[10px] leading-relaxed text-ash">
                      前回の踏破で持ち帰った装備。<span className="text-gold">次の出撃から最初に装備</span>されている。
                    </p>
                    <ItemCard item={heirloomItem} highlight />
                    <button
                      onClick={onDiscardHeirloom}
                      className="btn mt-2 w-full border border-line py-2 text-[11px] tracking-widest text-ash hover:text-blood"
                    >
                      この継承を手放す
                    </button>
                  </>
                ) : (
                  <p className="text-[11px] leading-relaxed text-ash/70">
                    <span className="text-bone">第25層を踏破</span>すると、装備を1つだけ持ち帰れるようになる。
                    <br />
                    真の決戦《ニヒル・ゼロ》を制すれば、更なる褒賞が待つ。
                  </p>
                )}
              </div>
            </Panel>

            <Panel title="魂の祭壇 / ALTAR" tone="panel-gold" right={<span className="tabular text-[13px] font-bold text-gold">◈ {souls.toLocaleString()}</span>}>
              <div className="space-y-2 p-3">
                <p className="mb-2 text-[11px] leading-relaxed text-ash">
                  死しても<span className="text-gold">魂</span>は残る。永久強化に注ぎ込めば、次の冒険はより深くまで届く。
                </p>
                {META_UPGRADES.map((u) => {
                  const lv = Number(meta?.[u.key] ?? 0);
                  const maxed = lv >= u.max;
                  const cost = metaCost(u, lv);
                  const afford = souls >= cost;
                  return (
                    <div key={u.key} className={`border p-2 transition ${maxed ? "border-jade/40 bg-jade/5" : "border-line bg-abyss2/50"}`}>
                      <div className="flex items-center justify-between gap-2">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-display text-[13px] font-bold text-bone">{u.label}</span>
                            <span className="tabular text-[10px] text-ash">
                              Lv {lv}/{u.max}
                            </span>
                          </div>
                          <div className="truncate text-[10px] text-ash">{u.desc}</div>
                        </div>
                        <button
                          disabled={maxed || !afford || !hero}
                          onClick={() => onBuy(u.key)}
                          className={`btn shrink-0 border px-2.5 py-1.5 text-[11px] font-bold ${
                            maxed
                              ? "border-jade/50 text-jade"
                              : afford && hero
                                ? "border-gold/60 bg-gold/15 text-gold"
                                : "border-line text-ash/60"
                          }`}
                        >
                          {maxed ? "最大" : `◈ ${cost}`}
                        </button>
                      </div>
                      <div className="mt-1.5 flex gap-0.5">
                        {Array.from({ length: u.max }, (_, i) => (
                          <span key={i} className="h-1 flex-1" style={{ background: i < lv ? "#f5c542" : "rgba(255,255,255,.09)" }} />
                        ))}
                      </div>
                      {lv > 0 && <div className="mt-1 text-[10px] text-jade">{u.display(lv)}</div>}
                    </div>
                  );
                })}
                {!hero && <p className="mt-2 text-center text-[10px] text-ash/70">出撃すると英雄が登録され、魂を注げるようになります</p>}
              </div>
            </Panel>

            <Panel title="選択中の職業">
              <div className="p-3">
                <div className="font-display text-[22px] font-black" style={{ color: sel.color }}>
                  {sel.name}
                </div>
                <div className="mb-2 text-[11px] tracking-wider text-ash">{sel.title}</div>
                <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-[11px]">
                  {[
                    ["生命", `${sel.base.hp} (+${sel.base.hpPerLevel}/Lv)`],
                    ["攻撃力", `${sel.base.damage}`],
                    ["攻撃速度", `${sel.base.attackSpeed.toFixed(2)}/秒`],
                    ["移動速度", `${sel.base.moveSpeed}`],
                    ["会心率", `${Math.round(sel.base.crit * 100)}%`],
                    ["会心威力", `${Math.round(sel.base.critDmg * 100)}%`],
                    ["射程形式", sel.attack === "melee" ? "近接 抜刀" : "遠距離 射撃"],
                    ["回避再使用", `${sel.base.dashCd.toFixed(2)}秒`],
                  ].map(([k, v]) => (
                    <div key={k} className="flex justify-between border-b border-line/40 py-1">
                      <dt className="text-ash">{k}</dt>
                      <dd className="tabular text-bone">{v}</dd>
                    </div>
                  ))}
                </dl>
                <div className="mt-2.5 border border-line bg-abyss2/70 p-2">
                  <div className="text-[10px] tracking-[0.25em] text-ember">必殺技 / {sel.skillName}</div>
                  <p className="mt-1 text-[11px] leading-relaxed text-ash">{sel.skillDesc}</p>
                  <p className="mt-1 text-[10px] text-ash/70">魂ゲージを100まで溜めて発動（Q / 右クリック）</p>
                </div>
              </div>
            </Panel>
          </div>
        </div>

        <footer className="mt-8 border-t border-line/60 pt-4 text-center text-[10px] tracking-[0.2em] text-ash/60">
          ABYSSAL LOOT — 全25層 ／ ランダム生成装備 ／ 永久死 ／ 魂による継承
        </footer>
      </div>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return <p className="border border-dashed border-line/70 px-3 py-6 text-center text-[11px] text-ash/60">{text}</p>;
}

const GUIDE_ROWS = [
  { k: "弾幕・低速", keys: ["Ctrl", "Z"], note: "弾幕の獄では押している間、38%速度で精密移動。実際の当たり判定が光る" },
  { k: "グレイズ", keys: [], note: "弾をかすめるとスコア＆ボムゲージ。FOCUS中は補充が早い。1発につき1回のみ" },
  { k: "ボム", keys: ["X"], note: "敵弾を一掃・範囲攻撃・2.2秒の無敵。グレイズ100%で1回補充" },
  { k: "武器固有奥義", keys: ["E"], note: "実ダメージで専用ゲージ蓄積。満タンで発動。スマホは『奥義』ボタン" },
  { k: "属性配分", keys: ["Esc"], note: "レベルごとに1点。剛力・韻律・敏捷・忍耐・幸運へ配分" },
  { k: "移動", keys: ["W", "A", "S", "D"], note: "スマホ：画面左半分をドラッグ。ゲームパッドの左スティック／十字キーにも対応" },
  { k: "照準・攻撃", keys: ["J", "Z"], note: "マウス照準＋左クリック連打で3段コンボ／スマホ：右半分ドラッジ。ZはJと同じ攻撃" },
  { k: "ゲームパッド", keys: [], note: "左スティック移動／右スティック照準／X・RT攻撃／B・A回避／LB・RB必殺／Yボム／L3低速" },
  { k: "溜め重撃", keys: [], note: "近接職は攻撃を押し続けると溜め。離すと1.4〜2.6倍、満タンで衝撃波＋スタン" },
  { k: "反撃", keys: [], note: "回避直後0.55秒（見切り後1.1秒）の最初の一撃は1.5倍＋魂ゲージ回復" },
  { k: "オートエイム", keys: [], note: "AUTO ボタンで切替（スマホは初期ON）最寄りの敵を自動攻撃" },
  { k: "回避（無敵）", keys: ["Shift", "Space"], note: "突進中は敵を斬り裂く。敵弾に重ねると「見切り」" },
  { k: "必殺技", keys: ["Q"], note: "右クリック／画面ボタン。魂ゲージ100%で発動" },
  { k: "装備を拾う", keys: ["G"], note: "接触で自動。Eは武器奥義" },
  { k: "一時停止", keys: ["Esc", "P"], note: "" },
];

function Guide() {
  return (
    <div className="space-y-4">
      <div className="space-y-1.5">
        {GUIDE_ROWS.map((r) => (
          <div key={r.k} className="flex items-center justify-between gap-3 border-b border-line/40 py-1.5 text-[11px]">
            <span className="shrink-0 text-ash">{r.k}</span>
            <span className="flex flex-wrap items-center justify-end gap-1 text-bone">
              {r.keys.map((kk) => (
                <KeyCap key={kk}>{kk}</KeyCap>
              ))}
              <span className="text-[10px] text-ash/80">{r.note}</span>
            </span>
          </div>
        ))}
      </div>
      <div className="space-y-2 text-[11px] leading-relaxed text-ash">
        <p>
          <b className="text-jade">アクション：</b>近接職は<span className="text-bone">3段コンボ</span>（3撃目は大振りで吹き飛ばし）。斬撃で敵弾を
          <span className="text-bone">弾く</span>と魂ゲージ回復。回避中に敵の攻撃を躱すと<span className="text-jade">見切り</span>が発動し、
          時間が減速・回避が即再使用可能に。<span className="text-gold">コンボ数</span>に応じて与ダメージ最大+40%。
        </p>
        <p>
          <b className="text-bone">目的：</b>各層の敵を全滅させると<span className="text-jade">転移門</span>が開く。触れて次の層へ。
          5層ごとに<span className="text-blood">層の主</span>が待ち受け、HP30%以下で<span className="text-blood">猛攻形態</span>に移行する。
        </p>
        <p>
          <b className="text-ember">緊張感：</b>転移門を開けたまま14秒以上居座ると<span className="text-blood">追っ手</span>が湧く（最大3波）。
          拾い残した装備は層移動時に<span className="text-ash">半値以下で自動売却</span>、経験値オーブは<span className="text-ash">消滅</span>する。
          残り3体以下になると敵は<span className="text-blood">狂暴化</span>、8層以降の射撃敵は<span className="text-bone">偏差射撃</span>で移動先を狙ってくる。
        </p>
        <p>
          <b className="text-bone">ハクスラ：</b>敵は装備を落とす。拾うと現在装備との<span className="text-gold">総合力</span>と各ステータスの差分が表示される。
          装備するか、<span className="text-jade">倉庫</span>（最大28枠）へ収納するか選べる。武器には<span className="text-gold">武器種</span>（剣・斧・槍・杖・弓・短剣・槌・刀）と
          <span className="text-gold">特性</span>（吸血鬼・爆砕・業火・猛毒など）がランダムで付与される。
        </p>
        <p>
          <b className="text-ember">鍛造：</b>敵や層の主は<span className="text-bone">魂の欠片・古代の石・混沌の宝石・虚空のエッセンス・竜の心臓</span>を落とす。
          装備中または倉庫内の<span className="text-gold">同部位の装備を2つ</span>選び、素材を消費して融合すれば、両方の長所を併せ持つ上位装備が生まれる。
          鍛造を重ねるほど強力になる（[F]キーで鍛造炉を開く）。
        </p>
        <p>
          <b className="text-arcane">状態異常：</b>武器特性により敵へ<span className="text-bone">毒・炎上・出血</span>などを付与できる。
          継続ダメージで着実に削れる。
        </p>
        <p>
          <b className="text-bone">ローグ：</b>レベルアップごとに <span className="text-gold">3つの祝福</span>から1つを選ぶ。
          回転刃・連鎖雷撃・追尾魔弾などの能力は重ねがけで強化される。3層ごと・層の主の後には<span className="text-jade">イベント</span>（休息/宝箱/血の誓約）が発生。
        </p>
        <p>
          <b className="text-gold">変化する迷宮：</b>層ごとに<span className="text-ember">異変</span>が染まる — 闇の迷宮・狂乱の檻・黄金の流砂・毒瘴の構造…。
          一部の層には<span className="text-gold">層の番人</span>（ミニボス）や<span className="text-blood">伏兵</span>が潜み、
          討てば高品質な報いが得られる。素材と珍装備を扱う<span className="text-ember">略奪商人</span>が現れることも。
        </p>
        <p>
          <b className="text-bone">試練と戦術：</b>出撃前に8枚の呪印を任意選択。全て背負うと魂×2.46。回復・奥義ゲージまで制限されるため、ビルドに合う試練を選ぶこと。
          護衛兵の正面120°は与ダメージ30%、側面・背後は135%。蘇生師と召喚門を先に壊し、5層以降の針罠・岩罠へ敵を誘い込もう。罠は敵味方共通、点滅が起爆の合図。
        </p>
        <p>
          <b className="text-bone">永久死：</b>倒されると装備と祝福は消える。ただし層数・撃破数に応じた
          <span className="text-gold">魂</span>を持ち帰り、祭壇で永久強化できる。<span className="text-ember">実績</span>を解除すると追加の魂も得られる。
        </p>
        <p>
          <b className="text-arcane">5つのモード：</b>
          <span className="text-bone">通常潜行</span>／
          <span className="text-blood">主の巡礼</span>（層主8連戦・スコア×2.2）／
          <span className="text-arcane">無限深淵</span>（25層で終わらない・際限なく強化）／
          <span className="text-arcane">不幸の盟約</span>（祝福・パッシブ禁止・スコア×3.4）／
          <span className="text-gold">今日の深淵</span>
        </p>
        <p>
          <b className="text-ember">6つのダンジョン帯：</b>
          灰の回廊 → 骸骨の墓所 → <span className="text-ember">溶鉄の炉</span>（火の粉が降り注ぐ）→
          <span className="text-bone">氷結の深層</span>（床が滑り、慣性が残る）→
          <span className="text-arcane">虚無の心臓</span>（空間が引き寄せ、敵弾が加速）→ 真の深淵
        </p>
        <p>
          <b className="text-arcane">パッシブスキル：</b>層の主を倒すたびに3候補から1つ選び、<span className="text-bone">型</span>として刻む。
          巨人殺し・群れの克星・最後の砦・勢い・不滅の誓い・残響の型など挙動まで変わるものも。
          <span className="text-arcane">呪級</span>は強力だが致命的な欠点を抱える。すべて重複不可。
        </p>
        <p>
          <b className="text-gold">12の武器スタイル：</b>剣・斧・槍・杖・弓・短剣・槌・刀に加え、
          <span className="text-jade">鎌</span>（撃破で攻速が積み上がる収穫スタック）・
          <span className="text-ember">拳套</span>（超攻速の連打型）・
          <span className="text-bone">輪刃</span>（貫通3・跳ね返り）・
          <span className="text-arcane">魔導書</span>（爆縮型）
        </p>
        <p>
          <b className="text-jade">新アビリティ：</b>渦刃・復讐・天雷・残響・強欲・障壁・刈り取り・過剰修復。
          祝福は<span className="text-gold">40種以上</span>、パッシブは<span className="text-arcane">20種</span>。掛け合わせでビルドは無限に広がる。
        </p>
        <p>
          <b className="text-blood">ランダムイベント：</b>層の中盤で突然発生。
          宝の間／賭博の祭壇（所持金の半分を賭ける）／精鋭の襲撃／加護の泉／呪いの霧／時の歪み／群れの呼び声
        </p>
        <p>
          <b className="text-blood">オートエイムは弱い：</b>与ダメージ -30%・クリティカル無効・攻撃速度 -20%・射程縮小。
          楽はできるが、勝てるビルドには足りない。手動照準と溜め重撃・反撃を使い込んだ者だけが深層に届く。
        </p>
      </div>
    </div>
  );
}
