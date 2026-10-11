import { and, desc, eq, sql } from "drizzle-orm";
import { db } from "@/db";
import { artifacts, heroes, runs } from "@/db/schema";
import { EMPTY_BOARD, type BoardData } from "@/game/api";
import { getDailyRun } from "@/game/progression";
import Abyss from "@/components/Abyss";

export const dynamic = "force-dynamic";

async function loadBoard(): Promise<BoardData> {
  const daily = getDailyRun();
  const [board, dailyBoard, champions, relics, totals] = await Promise.all([
    db
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
      .limit(20),
    db
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
      .limit(10),
    db
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
      .limit(12),
    db
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
      .limit(12),
    db
      .select({
        runs: sql<number>`count(*)::int`,
        kills: sql<number>`coalesce(sum(kills),0)::int`,
        bestFloor: sql<number>`coalesce(max(floor),0)::int`,
      })
      .from(runs),
  ]);

  return { board, dailyBoard, dailyDate: daily.challengeDay ?? "", champions, relics, totals: totals[0] ?? { runs: 0, kills: 0, bestFloor: 0 } };
}

export default async function Home() {
  let initial: BoardData = EMPTY_BOARD;
  try {
    initial = await loadBoard();
  } catch {
    initial = EMPTY_BOARD;
  }
  return <Abyss initial={initial} />;
}
