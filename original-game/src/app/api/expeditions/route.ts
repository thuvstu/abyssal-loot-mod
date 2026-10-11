import { NextResponse } from "next/server";
import { randomInt } from "node:crypto";
import { eq } from "drizzle-orm";
import { db } from "@/db";
import { expeditions, heroes } from "@/db/schema";
import { CLASSES, DIFFICULTIES, type Difficulty } from "@/game/core";
import { buildRun, getDailyRun, RUN_MODES, type RunMode } from "@/game/progression";
import { trialRules, validateSigils } from "@/game/trials";
import { validUuid } from "@/game/run-rules";

export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  const b = await req.json().catch(() => null);
  if (!b || !validUuid(String(b.heroId ?? ""))) return NextResponse.json({ error: "英雄IDが不正です。" }, { status: 400 });
  let trialSigils;
  try { trialSigils = validateSigils(b.trialSigils); }
  catch (e) { return NextResponse.json({ error: (e as Error).message }, { status: 400 }); }
  const mode = String(b.mode ?? "standard");
  const difficulty = String(b.difficulty ?? "normal");
  if (!(mode === "daily" || Object.hasOwn(RUN_MODES, mode)) || !Object.hasOwn(DIFFICULTIES, difficulty) || !CLASSES.some((c) => c.id === b.classId)) {
    return NextResponse.json({ error: "モード・難易度・職業を確認してください。" }, { status: 400 });
  }
  try {
    const [hero] = await db.select({ id: heroes.id }).from(heroes).where(eq(heroes.id, b.heroId)).limit(1);
    if (!hero) return NextResponse.json({ error: "英雄が見つかりません。" }, { status: 404 });
    const config = mode === "daily" ? getDailyRun(new Date(), difficulty as Difficulty) : buildRun(mode as Exclude<RunMode, "daily">, difficulty as Difficulty);
    config.trialSigils = trialSigils;
    if (mode !== "daily") config.seed = randomInt(1, 2_147_483_647);
    const [record] = await db.insert(expeditions).values({ heroId: hero.id, classId: b.classId, config }).returning({ id: expeditions.id });
    const trials = trialRules(trialSigils);
    return NextResponse.json({ expeditionId: record.id, config, trialCount: trials.count, trialSoulMultiplier: trials.soulMultiplier });
  } catch (e) {
    console.error("expedition start failed", e);
    return NextResponse.json({ error: "出撃条件を保存できませんでした。再試行してください。" }, { status: 503 });
  }
}
