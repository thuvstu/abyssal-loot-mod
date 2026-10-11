"use client";

import { ATTRIBUTES, type Attributes, type AttributeId } from "@/game/attributes";

const benefit = (id: AttributeId, n: number) => {
  if (id === "strength") return `攻撃力 +${n * 3}%`;
  if (id === "rhythm") return `攻撃速度 +${n * 2}%`;
  if (id === "agility") return `移動 +${(n * 1.5).toFixed(1)}% · 回避短縮 +${n}%`;
  if (id === "endurance") return `最大HP +${n * 4}%`;
  return `運 +${n * 7} · 素材 +${n * 4}%`;
};

export default function AttributePanel({ attributes, points, onAllocate }: { attributes: Attributes; points: number; onAllocate: (id: AttributeId, amount: number) => void }) {
  return (
    <section className="attribute-console" aria-labelledby="attribute-title">
      <header><div><span className="trial-eyebrow">SHAPE YOUR BUILD</span><h3 id="attribute-title">属性を刻む</h3></div><span className="attribute-remaining" aria-live="polite"><b>{points}</b> 未配分</span></header>
      <p className="attribute-hint">レベルごとに1点。この冒険だけの配分。決定後は戻せません。</p>
      <div className="attribute-rows">
        {ATTRIBUTES.map((a) => (
          <div className="attribute-row" key={a.id}>
            <span className="attribute-icon" style={{ color: a.color, borderColor: `${a.color}55` }}>{a.icon}</span>
            <div className="attribute-copy"><strong>{a.name} <small>{attributes[a.id]} pt</small></strong><span>{a.description}</span><em style={{ color: a.color }}>{benefit(a.id, attributes[a.id])}</em></div>
            <div className="attribute-actions">
              <button type="button" disabled={points < 1} onClick={() => onAllocate(a.id, 1)} aria-label={`${a.name}に1ポイント配分`}>+1</button>
              <button type="button" disabled={points < 5} onClick={() => onAllocate(a.id, 5)} aria-label={`${a.name}に5ポイント配分`}>+5</button>
            </div>
          </div>
        ))}
      </div>
      <p className="attribute-hint">幸運の素材ボーナスは端数も抽選に反映。忍耐は最大HPを増やします（回復効果ではありません）。</p>
    </section>
  );
}
