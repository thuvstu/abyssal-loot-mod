import { NextResponse } from "next/server";
import { eq } from "drizzle-orm";
import { db } from "@/db";
import { heroes } from "@/db/schema";
import { META_UPGRADES } from "@/game/core";
import { validUuid } from "@/game/run-rules";

export const dynamic = "force-dynamic";
export async function POST(req: Request) {
  const b = await req.json().catch(() => null);
  const id = String(b?.heroId ?? ""), key = String(b?.key ?? "");
  const def = META_UPGRADES.find((m) => m.key === key);
  if (!validUuid(id) || !def) return NextResponse.json({ error: "英雄・強化項目が不正です。" }, { status: 400 });
  try {
    const result = await db.transaction(async (tx) => {
      const [hero] = await tx.select().from(heroes).where(eq(heroes.id, id)).for("update");
      if (!hero) return { error: "英雄が見つかりません。", status: 404 };
      const current = Math.max(0, Math.floor(Number(hero.meta?.[key] ?? 0)));
      if (current >= def.max) return { error: "既に最大レベルです。", status: 400 };
      const cost = Math.round(def.baseCost * Math.pow(def.growth, current));
      if (hero.souls < cost) return { error: "魂が足りません。", status: 400 };
      const [updated] = await tx.update(heroes).set({ souls: hero.souls - cost, meta: { ...hero.meta, [key]: current + 1 }, updatedAt: new Date() }).where(eq(heroes.id, id)).returning();
      return { hero: updated, cost };
    });
    if ("error" in result) return NextResponse.json({ error: result.error }, { status: result.status });
    return NextResponse.json(result);
  } catch (e) {
    console.error("meta upgrade failed", e);
    return NextResponse.json({ error: "強化を保存できませんでした。" }, { status: 503 });
  }
}
