import { NextResponse } from "next/server";
import { desc, eq, sql } from "drizzle-orm";
import { db } from "@/db";
import { achievementUnlocks, artifacts, expeditions, heroes, runs } from "@/db/schema";
import { ACHIEVEMENTS } from "@/game/progression";
import { validateSigils, trialRules } from "@/game/trials";
import { validateAttributes } from "@/game/attributes";
import { calculateRunReward, safeInt, validUuid } from "@/game/run-rules";
import { SLOTS, RARITIES } from "@/game/core";

export const dynamic = "force-dynamic";
class InputError extends Error { constructor(message: string, public status = 400) { super(message); } }

export async function POST(req: Request) {
  const b = await req.json().catch(() => null);
  const heroId = String(b?.heroId ?? ""), expeditionId = String(b?.expeditionId ?? "");
  if (!validUuid(heroId) || !validUuid(expeditionId)) return NextResponse.json({ error: "出撃記録が不正です。タイトルから出撃してください。" }, { status: 400 });
  let sigils;
  try { sigils = validateSigils(b.trialSigils); }
  catch (e) { return NextResponse.json({ error: (e as Error).message }, { status: 400 }); }
  const trial = trialRules(sigils);
  if (b.trialCount !== undefined && b.trialCount !== trial.count) return NextResponse.json({ error: "呪印の枚数が一致しません。" }, { status: 400 });

  try {
    const result = await db.transaction(async (tx) => {
      const [expedition] = await tx.select().from(expeditions).where(eq(expeditions.id, expeditionId)).for("update");
      if (!expedition || expedition.heroId !== heroId) throw new InputError("この英雄の出撃記録ではありません。", 403);
      const config = expedition.config;
      const registered = validateSigils(config.trialSigils);
      if (JSON.stringify(registered) !== JSON.stringify(sigils)) throw new InputError("出撃後に呪印を変更することはできません。", 409);
      if (b.challengeMode !== config.mode || b.difficulty !== config.difficulty || b.classId !== expedition.classId) {
        throw new InputError("出撃条件と結果の条件が一致しません。", 409);
      }
      const [hero] = await tx.select().from(heroes).where(eq(heroes.id, heroId)).for("update");
      if (!hero) throw new InputError("英雄が見つかりません。", 404);
      const [previous] = await tx.select().from(runs).where(eq(runs.expeditionId, expeditionId)).limit(1);
      const achievementMap = new Map(ACHIEVEMENTS.map((a) => [a.id, a]));
      if (previous?.reward) {
        const r = previous.reward;
        return { run: previous, hero, soulsEarned: r.runSouls + r.achievementSouls, ...r, trialCount: trial.count, trialSoulMultiplier: trial.soulMultiplier,
          achievements: r.achievementIds.map((id) => achievementMap.get(id)).filter(Boolean), replayed: true };
      }
      if (expedition.settled) throw new InputError("この冒険は既に記録済みです。", 409);
      const floor = safeInt(b.floor, 1, config.mode === "endless" ? 999 : config.mode === "bossrush" ? 8 : config.mode === "pinnacle" ? 27 : 26);
      const level = safeInt(b.level, 1, 999), kills = safeInt(b.kills, 0, 100000);
      const gold = safeInt(b.gold, 0, 10000000), score = safeInt(b.score, 0, 100000000);
      const durationSec = safeInt(b.durationSec, 0, 86400);
      const victory = b.victory === true && config.mode !== "endless" && (config.mode === "bossrush" ? floor === 8 : config.mode === "pinnacle" ? floor === 27 : floor >= 25);
      const trueCleared = b.trueCleared === true && victory && floor === 26;
      const pinnacleCleared = b.pinnacleCleared === true && config.mode === "pinnacle" && victory && floor === 27;
      const bossesKilled = safeInt(b.bossesKilled, 0, Math.min(kills, 999));
      const eventsCompleted = safeInt(b.eventsCompleted, 0, floor * 3);
      const bestCombo = safeInt(b.bestCombo, 0, 100000), perfectDodges = safeInt(b.perfectDodges, 0, 100000);
      const gatesDestroyed = safeInt(b.gatesDestroyed, 0, Math.min(kills, 1000));
      const weaponArtUses = safeInt(b.weaponArtUses, 0, 10000);
      let attributes;
      try { attributes = validateAttributes(b.attributes, level); }
      catch (e) { throw new InputError((e as Error).message); }
      const build = Array.isArray(b.build) ? b.build.slice(0, 60).map((x: unknown) => String(x).slice(0, 64)) : [];
      const found = Array.isArray(b.artifactsFound) ? b.artifactsFound.filter((a: unknown) => a && typeof a === "object").slice(0, 12).map((a: Record<string, unknown>) => ({
        itemName: String(a.name ?? "戦利品").slice(0, 64), slot: SLOTS.includes(a.slot as typeof SLOTS[number]) ? String(a.slot) : "weapon",
        rarity: RARITIES.includes(a.rarity as typeof RARITIES[number]) ? String(a.rarity) : "rare",
        power: safeInt(a.power, 0, 1000000), floor: safeInt(a.floor, 1, floor),
        affixes: Array.isArray(a.affixes) ? a.affixes.slice(0, 8).map((x: unknown) => String(x).slice(0, 64)) : [],
      })) : [];

      const eligible = new Set<string>();
      if (kills >= 1) eligible.add("first-blood");
      if (kills >= 100) eligible.add("hundred-kills");
      if (kills >= 1000) eligible.add("thousand-kills");
      if (bossesKilled >= 1) eligible.add("floor-five");
      if (bossesKilled >= 2) eligible.add("floor-ten");
      if (bossesKilled >= 4 && floor >= 20) eligible.add("floor-twenty");
      if (victory && floor >= 25) eligible.add("full-clear");
      if (trueCleared) eligible.add("true-clear");
      if (pinnacleCleared) eligible.add("pinnacle-god");
      if (config.difficulty === "inferno" && floor >= 15) eligible.add("inferno-slayer");
      if (found.some((a: { rarity: string }) => a.rarity === "mythic")) eligible.add("mythic-hunter");
      if (bestCombo >= 50) eligible.add("combo-fifty");
      if (perfectDodges >= 10) eligible.add("perfect-ten");
      if (eventsCompleted >= 5) eligible.add("event-five");
      if (config.mode === "daily" && floor >= 10) eligible.add("daily-depth");
      if (config.mode === "covenant" && victory) eligible.add("covenant-kept");
      if (config.mode === "bossrush" && bossesKilled >= 8) eligible.add("rush-master");
      if (config.mode === "endless" && floor >= 35) eligible.add("endless-diver");
      if (level >= 20) eligible.add("level-twenty");
      if (bossesKilled >= 3) eligible.add("boss-three");
      if (gatesDestroyed >= 1) eligible.add("gate-breaker");
      if (trial.count === 8 && floor >= 10) eligible.add("eight-sigils");
      if (weaponArtUses >= 10) eligible.add("art-master");
      const newUnlocks = eligible.size ? await tx.insert(achievementUnlocks).values([...eligible].map((key) => ({ heroId, key }))).onConflictDoNothing().returning({ key: achievementUnlocks.key }) : [];
      const achievementIds = newUnlocks.map((a) => a.key);
      const achievementSouls = achievementIds.reduce((sum, id) => sum + (achievementMap.get(id)?.reward ?? 0), 0);
      const reward = calculateRunReward({ floor, level, kills, score, victory, trueCleared }, config);
      if (pinnacleCleared) reward.runSouls += 10000;
      const storedReward = { runSouls: reward.runSouls, achievementSouls, trialBonus: reward.trialBonus, achievementIds };
      const soulsEarned = reward.runSouls + achievementSouls;
      const [run] = await tx.insert(runs).values({
        heroId, expeditionId, heroName: hero.name, classId: expedition.classId, floor, level, kills, gold, score, durationSec, victory,
        challengeMode: config.mode, challengeId: config.challengeId, challengeName: config.challengeName, challengeDay: config.challengeDay,
        trialSigils: registered, difficulty: config.difficulty, attributes, gatesDestroyed, weaponArtUses, reward: storedReward,
        eventsCompleted, bossesKilled, bestCombo, perfectDodges,
        weaponName: b.weaponName ? String(b.weaponName).slice(0, 64) : null,
        rarity: RARITIES.includes(b.rarity) ? b.rarity : null, build,
      }).returning();
      for (const a of found) if ((a.rarity === "legendary" || a.rarity === "mythic") && a.power > 0) await tx.insert(artifacts).values({ heroName: hero.name, ...a });
      const [updated] = await tx.update(heroes).set({
        souls: sql`least(2000000000, ${heroes.souls} + ${soulsEarned})`, bestFloor: sql`greatest(${heroes.bestFloor}, ${floor})`,
        bestScore: sql`greatest(${heroes.bestScore}, ${score})`, deepestKills: sql`greatest(${heroes.deepestKills}, ${kills})`,
        totalRuns: sql`${heroes.totalRuns} + 1`, totalKills: sql`${heroes.totalKills} + ${kills}`,
        victories: sql`${heroes.victories} + ${victory ? 1 : 0}`, trueClears: sql`${heroes.trueClears} + ${trueCleared ? 1 : 0}`,
        pinnacleClears: sql`${heroes.pinnacleClears} + ${pinnacleCleared ? 1 : 0}`, updatedAt: new Date(),
      }).where(eq(heroes.id, heroId)).returning();
      await tx.update(expeditions).set({ settled: true }).where(eq(expeditions.id, expeditionId));
      return { run, hero: updated, soulsEarned, ...storedReward, ...reward, achievements: achievementIds.map((id) => achievementMap.get(id)).filter(Boolean), replayed: false };
    });
    return NextResponse.json(result);
  } catch (e) {
    if (e instanceof InputError) return NextResponse.json({ error: e.message }, { status: e.status });
    console.error("run settlement failed", e);
    return NextResponse.json({ error: "戦績を保存できませんでした。再送しても報酬は重複しません。" }, { status: 503 });
  }
}
