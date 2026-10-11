"use client";

import { useMemo, useState } from "react";
import type { Engine } from "@/game/engine";
import type { Item, Materials, MaterialId } from "@/game/core";
import { MATERIALS, RARITY_META, SLOT_META, WEAPON_CLASSES, WEAPON_TRAITS, forgeItems } from "@/game/core";
import { Panel, RarityTag } from "./ui";

type Props = {
  engine: Engine;
  items: Item[];
  equippedIds: Set<string>;
  materials: Materials;
  stashCount: number;
  stashCap: number;
  onClose: () => void;
};

export default function ForgePanel({ engine, items, equippedIds, materials, stashCount, stashCap, onClose }: Props) {
  const [tab, setTab] = useState<"forge" | "stash">("forge");
  const [selA, setSelA] = useState<string | null>(null);
  const [selB, setSelB] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);

  const itemA = items.find((i) => i.id === selA) ?? null;
  const itemB = items.find((i) => i.id === selB) ?? null;

  const preview = useMemo(() => {
    if (!itemA || !itemB || itemA.slot !== itemB.slot || itemA.id === itemB.id) return null;
    try {
      const fakeMats: Materials = {};
      for (const k of Object.keys(MATERIALS) as MaterialId[]) fakeMats[k] = (materials[k] ?? 0) + 9999;
      return forgeItems(Math.random, itemA, itemB, fakeMats, itemA.ilvl);
    } catch { return null; }
  }, [itemA, itemB, materials]);

  const costOk = useMemo(() => {
    if (!preview) return false;
    for (const [k, v] of Object.entries(preview.cost) as [MaterialId, number][]) {
      if ((materials[k] ?? 0) < v) return false;
    }
    return true;
  }, [preview, materials]);

  const doForge = () => {
    if (!selA || !selB) return;
    const ok = engine.doForge(selA, selB);
    if (ok) {
      setFlash("鍛造成功！新たな装備が生まれた");
      setSelA(null);
      setSelB(null);
      setTimeout(() => setFlash(null), 2200);
    }
  };

  const selectItem = (id: string) => {
    if (selA === id) { setSelA(null); return; }
    if (selB === id) { setSelB(null); return; }
    if (!selA) setSelA(id);
    else if (!selB) setSelB(id);
    else { setSelA(id); setSelB(null); }
  };

  const equipItem = (id: string) => {
    engine.equipFromStash(id);
  };
  const sellItem = (id: string) => {
    engine.sellFromStash(id);
    if (selA === id) setSelA(null);
    if (selB === id) setSelB(null);
  };

  return (
    <div className="modal-in w-[min(980px,95vw)]">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="title-glow font-display text-[26px] font-black tracking-[0.16em] text-gold sm:text-[28px]">鍛 鉄 炉 ・ 倉 庫</h2>
          <p className="text-[10px] text-ash">装備2つを素材と共に融合し、最強の逸品を生み出せ ／ 倉庫 {stashCount}/{stashCap}</p>
        </div>
        <div className="flex gap-1.5">
          <button onClick={() => setTab("forge")} className={`btn border px-3 py-1.5 text-[11px] tracking-widest ${tab === "forge" ? "border-gold/60 bg-gold/15 text-gold" : "border-line text-ash hover:text-bone"}`}>鍛造</button>
          <button onClick={() => setTab("stash")} className={`btn border px-3 py-1.5 text-[11px] tracking-widest ${tab === "stash" ? "border-jade/60 bg-jade/15 text-jade" : "border-line text-ash hover:text-bone"}`}>倉庫管理</button>
          <button onClick={onClose} className="btn border border-line px-3 py-1.5 text-[11px] text-ash hover:text-bone">閉じる [F]</button>
        </div>
      </div>

      {/* 素材表示 */}
      <div className="mb-3 flex flex-wrap gap-2">
        {(Object.keys(MATERIALS) as MaterialId[]).map((k) => (
          <div key={k} className="flex items-center gap-1.5 border px-2 py-1 text-[11px]"
            style={{ borderColor: `${MATERIALS[k].color}44`, background: `${MATERIALS[k].color}0a` }}>
            <span style={{ color: MATERIALS[k].color }}>{MATERIALS[k].icon}</span>
            <span className="text-ash">{MATERIALS[k].name}</span>
            <span className="tabular font-bold" style={{ color: MATERIALS[k].color }}>{materials[k] ?? 0}</span>
          </div>
        ))}
      </div>

      {flash && (
        <div className="mb-3 border border-gold/50 bg-gold/10 px-3 py-2 text-center text-[12px] font-bold text-gold animate-pulse">{flash}</div>
      )}

      {tab === "forge" ? (
        <div className="grid gap-3 lg:grid-cols-[1fr_auto_1fr]">
          {/* 装備リスト */}
          <Panel title={`所持装備 (${items.length})`}>
            <div className="max-h-[380px] overflow-y-auto p-2 space-y-1.5">
              {items.map((it) => {
                const selected = it.id === selA || it.id === selB;
                const idx = it.id === selA ? 1 : it.id === selB ? 2 : 0;
                const equipped = equippedIds.has(it.id);
                return (
                  <button key={it.id} onClick={() => selectItem(it.id)}
                    className={`btn w-full border p-2 text-left text-[11px] transition ${selected ? "bg-panel2" : "border-line bg-abyss2/40 hover:border-line"}`}
                    style={selected ? { borderColor: RARITY_META[it.rarity].color, boxShadow: `inset 0 0 20px -12px ${RARITY_META[it.rarity].color}` } : undefined}>
                    <div className="flex items-center justify-between">
                      <div className="flex min-w-0 items-center gap-1.5">
                        {idx > 0 && <span className="shrink-0 border px-1 text-[9px] text-gold">{idx}</span>}
                        <RarityTag rarity={it.rarity} small />
                        <span className="truncate font-bold" style={{ color: RARITY_META[it.rarity].color }}>{it.name}</span>
                        {equipped && <span className="shrink-0 border border-jade/50 px-1 text-[8px] text-jade">装備中</span>}
                      </div>
                      <span className="tabular shrink-0 text-[10px] text-gold">P{it.power}</span>
                    </div>
                    <div className="mt-0.5 flex flex-wrap items-center gap-1 text-[9px] text-ash">
                      <span>{SLOT_META[it.slot].label}</span>
                      {it.weaponClass && <span style={{ color: WEAPON_CLASSES[it.weaponClass].color }}>{WEAPON_CLASSES[it.weaponClass].name}</span>}
                      {it.weaponTrait && (
                        <span style={{ color: WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.color }}>
                          [{WEAPON_TRAITS.find((t) => t.id === it.weaponTrait)?.name}]
                        </span>
                      )}
                      {it.forged ? <span className="text-arcane">鍛造×{it.forged}</span> : null}
                    </div>
                  </button>
                );
              })}
              {items.length === 0 && <p className="text-[11px] text-ash/60">装備がありません</p>}
            </div>
          </Panel>

          {/* 中央: 合成ボタン */}
          <div className="flex flex-col items-center justify-center gap-3 py-4 lg:py-0">
            <div className="text-center text-[10px] text-ash">
              {itemA ? <span style={{ color: RARITY_META[itemA.rarity].color }}>{itemA.name}</span> : "装備Aを選択"}
            </div>
            <div className="text-2xl text-gold">＋</div>
            <div className="text-center text-[10px] text-ash">
              {itemB ? <span style={{ color: RARITY_META[itemB.rarity].color }}>{itemB.name}</span> : "装備Bを選択"}
            </div>
            {itemA && itemB && itemA.slot !== itemB.slot && (
              <p className="max-w-[160px] text-center text-[10px] text-blood">同じ部位同士のみ融合できます</p>
            )}
            <button onClick={doForge} disabled={!preview || !costOk}
              className="btn border-2 border-gold/70 bg-gold/15 px-6 py-3 font-display text-[16px] font-bold tracking-[0.2em] text-gold disabled:border-line disabled:text-ash disabled:opacity-40">
              鍛造する
            </button>
            {preview && (
              <div className="text-[10px] text-ash">
                {Object.entries(preview.cost).map(([k, v]) => {
                  const m = MATERIALS[k as MaterialId];
                  const have = materials[k as MaterialId] ?? 0;
                  const ok = have >= (v as number);
                  return (
                    <div key={k} className={ok ? "" : "text-blood"}>
                      {m.icon} {m.name} {have}/{v as number}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* 右: プレビュー */}
          <Panel title="合成プレビュー">
            <div className="p-3">
              {preview ? (
                <div>
                  <div className="flex items-center gap-2">
                    <RarityTag rarity={preview.item.rarity} />
                    <span className="font-display text-[17px] font-bold" style={{ color: RARITY_META[preview.item.rarity].color }}>{preview.item.name}</span>
                  </div>
                  <div className="mt-1 text-[10px] text-ash">
                    P{preview.item.power} {preview.item.forged ? `鍛造×${preview.item.forged}` : ""}
                    {preview.bonusAffixes > 0 && <span className="ml-1 text-jade">+{preview.bonusAffixes} 新特性</span>}
                  </div>
                  {preview.item.weaponClass && (
                    <div className="mt-1 text-[10px]">
                      <span style={{ color: WEAPON_CLASSES[preview.item.weaponClass].color }}>{WEAPON_CLASSES[preview.item.weaponClass].name}</span>
                      {preview.item.weaponTrait && (
                        <span className="ml-1" style={{ color: WEAPON_TRAITS.find((t) => t.id === preview.item.weaponTrait)?.color }}>
                          [{WEAPON_TRAITS.find((t) => t.id === preview.item.weaponTrait)?.name}]
                        </span>
                      )}
                    </div>
                  )}
                  <div className="mt-2 space-y-0.5 border-t border-line/50 pt-2">
                    {preview.item.affixes.map((af, i) => (
                      <div key={i} className="flex justify-between text-[10px]">
                        <span className="text-ash">{af.label}</span>
                        <span className="text-jade tabular">{af.text}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <p className="text-[11px] text-ash/60">同じ部位の装備を2つ選択してください</p>
              )}
            </div>
          </Panel>
        </div>
      ) : (
        <Panel title="倉庫 — 装備の入れ替え・売却">
          <div className="grid max-h-[420px] gap-1.5 overflow-y-auto p-2 sm:grid-cols-2">
            {items.filter((it) => !equippedIds.has(it.id)).map((it) => (
              <div key={it.id} className="border border-line bg-abyss2/50 p-2 text-[11px]">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex min-w-0 items-center gap-1.5">
                    <RarityTag rarity={it.rarity} small />
                    <span className="truncate font-bold" style={{ color: RARITY_META[it.rarity].color }}>{it.name}</span>
                  </div>
                  <span className="tabular shrink-0 text-[10px] text-gold">P{it.power}</span>
                </div>
                <div className="mt-0.5 text-[9px] text-ash">{SLOT_META[it.slot].label} {it.forged ? `／ 鍛造×${it.forged}` : ""}</div>
                <div className="mt-1.5 flex gap-1.5">
                  <button onClick={() => equipItem(it.id)} className="btn flex-1 border border-jade/50 bg-jade/10 py-1 text-[10px] text-jade">装備</button>
                  <button onClick={() => sellItem(it.id)} className="btn flex-1 border border-gold/50 bg-gold/10 py-1 text-[10px] text-gold">
                    売却 +{Math.round(4 + it.power * 0.6)}G
                  </button>
                </div>
              </div>
            ))}
            {items.filter((it) => !equippedIds.has(it.id)).length === 0 && (
              <p className="col-span-2 text-center text-[11px] text-ash/60 py-6">倉庫は空です — 戦利品を「倉庫へ」収納すると、ここに表示されます</p>
            )}
          </div>
        </Panel>
      )}
    </div>
  );
}
