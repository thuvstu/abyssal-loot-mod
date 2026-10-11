import { DIFFICULTIES } from "./core";
import { RUN_MODES, type RunConfig } from "./progression";
import { trialRules } from "./trials";

export type RewardStats = { floor: number; kills: number; level: number; score: number; victory: boolean; trueCleared: boolean };
export function calculateRunReward(stats: RewardStats, config: RunConfig) {
  const progressed = stats.kills > 0 || stats.floor > 1 || stats.victory;
  // Opening and immediately abandoning a run must not be a source of infinite pact rewards.
  const baseSouls = progressed ? Math.round(stats.floor * 14 + stats.kills * 0.6 + stats.level * 8 + stats.score / 40 + (stats.victory ? 900 : 0) + (stats.trueCleared ? 6000 : 0)) : 0;
  const modeRate = config.mode === "daily" ? config.modifier?.soulRate ?? 1 : RUN_MODES[config.mode].soulMul;
  const difficultyRate = DIFFICULTIES[config.difficulty].soulMul;
  const trial = trialRules(config.trialSigils);
  const beforeTrials = Math.round(baseSouls * modeRate * difficultyRate);
  const runSouls = Math.round(beforeTrials * trial.soulMultiplier);
  return { baseSouls, beforeTrials, runSouls, trialBonus: runSouls - beforeTrials, trialCount: trial.count, trialSoulMultiplier: trial.soulMultiplier };
}
export const validUuid = (v: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
export const safeInt = (v: unknown, min: number, max: number) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, Math.round(n))) : min;
};
