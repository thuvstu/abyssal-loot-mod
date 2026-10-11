import { NextResponse } from "next/server";
import { eq } from "drizzle-orm";
import { db } from "@/db";
import { achievementUnlocks, heroes } from "@/db/schema";
import { ACHIEVEMENTS } from "@/game/progression";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  const heroId = new URL(req.url).searchParams.get("heroId") ?? "";
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(heroId)) {
    return NextResponse.json({ achievements: ACHIEVEMENTS.map((a) => ({ ...a, unlockedAt: null })) });
  }

  const [hero] = await db.select({ id: heroes.id }).from(heroes).where(eq(heroes.id, heroId)).limit(1);
  if (!hero) return NextResponse.json({ achievements: ACHIEVEMENTS.map((a) => ({ ...a, unlockedAt: null })) });

  const unlocked = await db
    .select({ key: achievementUnlocks.key, unlockedAt: achievementUnlocks.unlockedAt })
    .from(achievementUnlocks)
    .where(eq(achievementUnlocks.heroId, heroId));
  const map = new Map(unlocked.map((x) => [x.key, x.unlockedAt]));
  return NextResponse.json({
    achievements: ACHIEVEMENTS.map((a) => ({ ...a, unlockedAt: map.get(a.id) ?? null })),
  });
}
