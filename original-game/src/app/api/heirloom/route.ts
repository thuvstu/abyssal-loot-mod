import { NextResponse } from "next/server";
import { eq, sql } from "drizzle-orm";
import { db } from "@/db";
import { heroes } from "@/db/schema";

export const dynamic = "force-dynamic";

type IncomingItem = {
  slot?: unknown;
  rarity?: unknown;
  base?: unknown;
  name?: unknown;
  ilvl?: unknown;
  power?: unknown;
  weaponClass?: unknown;
  weaponTrait?: unknown;
  forged?: unknown;
  mods?: unknown;
  affixes?: unknown;
};

const VALID_SLOTS = ["weapon", "helm", "armor", "gloves", "boots", "ring", "amulet"] as const;
const VALID_RARITIES = ["normal", "magic", "rare", "legendary", "mythic"] as const;

export async function POST(req: Request) {
  const b = await req.json().catch(() => null);
  const heroId = String(b?.heroId ?? "");
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(heroId)) {
    return NextResponse.json({ error: "heroId が不正です" }, { status: 400 });
  }

  const it = b?.item as IncomingItem | null;
  const slot = VALID_SLOTS.includes(it?.slot as (typeof VALID_SLOTS)[number]) ? (it?.slot as string) : null;
  const rarity = VALID_RARITIES.includes(it?.rarity as (typeof VALID_RARITIES)[number]) ? (it?.rarity as string) : null;
  if (!it || !slot || !rarity || typeof it.name !== "string" || !it.name.trim()) {
    return NextResponse.json({ error: "装備データが不正です" }, { status: 400 });
  }

  const [hero] = await db.select().from(heroes).where(eq(heroes.id, heroId)).limit(1);
  if (!hero) return NextResponse.json({ error: "英雄が見つかりません" }, { status: 404 });

  // 踏破（または真討伐）済みでなければ継承できない
  if (hero.victories < 1 && hero.trueClears < 1) {
    return NextResponse.json({ error: "深淵を踏破してから装備を継承できます" }, { status: 403 });
  }

  const num = (v: unknown, min: number, max: number, dflt = 0) => {
    const n = Number(v);
    return Number.isFinite(n) ? Math.max(min, Math.min(max, Math.round(n * 10) / 10)) : dflt;
  };

  const stored = {
    id: `heir_${Date.now().toString(36)}`,
    slot,
    rarity,
    base: typeof it.base === "string" ? it.base.slice(0, 48) : "",
    name: it.name.trim().slice(0, 64),
    ilvl: num(it.ilvl, 1, 99, 1),
    power: num(it.power, 0, 10_000_000),
    weaponClass: typeof it.weaponClass === "string" ? it.weaponClass : null,
    weaponTrait: typeof it.weaponTrait === "string" ? it.weaponTrait : null,
    forged: it.forged == null ? null : num(it.forged, 0, 99),
    mods: it.mods && typeof it.mods === "object" && !Array.isArray(it.mods)
      ? Object.fromEntries(
          Object.entries(it.mods as Record<string, unknown>)
            .filter(([, v]) => Number.isFinite(Number(v)))
            .map(([k, v]) => [String(k).slice(0, 32), num(v, -100000, 100000)])
            .slice(0, 40),
        )
      : {},
    affixes: Array.isArray(it.affixes)
      ? it.affixes.slice(0, 12).map((a) => ({
          key: String((a as Record<string, unknown>)?.key ?? "").slice(0, 32),
          label: String((a as Record<string, unknown>)?.label ?? "").slice(0, 32),
          text: String((a as Record<string, unknown>)?.text ?? "").slice(0, 32),
          value: num((a as Record<string, unknown>)?.value, -100000, 100000),
          tier: num((a as Record<string, unknown>)?.tier, 1, 5, 1),
        }))
      : [],
  };

  const [updated] = await db
    .update(heroes)
    .set({ heirloom: stored, updatedAt: new Date() })
    .where(eq(heroes.id, heroId))
    .returning();

  return NextResponse.json({ hero: updated, heirloom: stored });
}

/** 継承装備を手放す */
export async function DELETE(req: Request) {
  const heroId = new URL(req.url).searchParams.get("heroId") ?? "";
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(heroId)) {
    return NextResponse.json({ error: "heroId が不正です" }, { status: 400 });
  }
  const [hero] = await db.select({ id: heroes.id }).from(heroes).where(eq(heroes.id, heroId)).limit(1);
  if (!hero) return NextResponse.json({ error: "英雄が見つかりません" }, { status: 404 });

  const [updated] = await db
    .update(heroes)
    .set({ heirloom: null, updatedAt: new Date() })
    .where(eq(heroes.id, heroId))
    .returning();
  void sql;
  return NextResponse.json({ hero: updated });
}
