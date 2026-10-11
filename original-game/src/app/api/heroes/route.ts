import { NextResponse } from "next/server";
import { and, desc, eq, sql } from "drizzle-orm";
import { db } from "@/db";
import { artifacts, heroes, runs } from "@/db/schema";
import { getDailyRun } from "@/game/progression";
import { CLASSES } from "@/game/core";

export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  const body = await req.json().catch(() => null);
  const name = typeof body?.name === "string" ? Array.from(body.name.trim().normalize("NFC")).slice(0, 16).join("") : "";
  const classId = String(body?.classId ?? "samurai");
  if (!name || !CLASSES.some((c) => c.id === classId)) return NextResponse.json({ error: "名前と職業を確認してください。" }, { status: 400 });
  try {
    const result = await db.transaction(async (tx) => {
      // Lock the name, including when the row doesn't exist yet (input blur and Start may race).
      await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${name}))`);
      const [found] = await tx.select().from(heroes).where(eq(heroes.name, name)).orderBy(heroes.createdAt).limit(1);
      if (found) {
        if (found.classId === classId) return { hero: found, created: false };
        const [updated] = await tx.update(heroes).set({ classId, updatedAt: new Date() }).where(eq(heroes.id, found.id)).returning();
        return { hero: updated, created: false };
      }
      const [hero] = await tx.insert(heroes).values({ name, classId }).returning();
      return { hero, created: true };
    });
    return NextResponse.json(result);
  } catch (e) {
    console.error("hero load failed", e);
    return NextResponse.json({ error: "英雄の記録を読み込めませんでした。" }, { status: 503 });
  }
}

export async function GET() {
  const daily = getDailyRun();
  const board = await db
    .select({
      id: runs.id,
      heroName: runs.heroName,
      classId: runs.classId,
      floor: runs.floor,
      level: runs.level,
      kills: runs.kills,
      score: runs.score,
      victory: runs.victory,
      weaponName: runs.weaponName,
      rarity: runs.rarity,
      build: runs.build,
      createdAt: runs.createdAt,
    })
    .from(runs)
    .orderBy(desc(runs.score))
    .limit(20);

  const dailyBoard = await db
    .select({
      id: runs.id,
      heroName: runs.heroName,
      classId: runs.classId,
      floor: runs.floor,
      level: runs.level,
      kills: runs.kills,
      score: runs.score,
      victory: runs.victory,
      challengeName: runs.challengeName,
    })
    .from(runs)
    .where(and(eq(runs.challengeMode, "daily"), eq(runs.challengeDay, daily.challengeDay ?? "")))
    .orderBy(desc(runs.score))
    .limit(10);

  const champions = await db
    .select({
      name: heroes.name,
      classId: heroes.classId,
      bestFloor: heroes.bestFloor,
      bestScore: heroes.bestScore,
      totalRuns: heroes.totalRuns,
      totalKills: heroes.totalKills,
      victories: heroes.victories,
      souls: heroes.souls,
    })
    .from(heroes)
    .orderBy(desc(heroes.bestScore))
    .limit(12);

  const relics = await db
    .select({
      itemName: artifacts.itemName,
      slot: artifacts.slot,
      rarity: artifacts.rarity,
      power: artifacts.power,
      floor: artifacts.floor,
      heroName: artifacts.heroName,
      affixes: artifacts.affixes,
      createdAt: artifacts.createdAt,
    })
    .from(artifacts)
    .orderBy(desc(artifacts.power))
    .limit(12);

  const totals = await db
    .select({
      runs: sql<number>`count(*)::int`,
      kills: sql<number>`coalesce(sum(kills),0)::int`,
      bestFloor: sql<number>`coalesce(max(floor),0)::int`,
    })
    .from(runs);

  return NextResponse.json({
    board,
    dailyBoard,
    dailyDate: daily.challengeDay,
    champions,
    relics,
    totals: totals[0] ?? { runs: 0, kills: 0, bestFloor: 0 },
  });
}
