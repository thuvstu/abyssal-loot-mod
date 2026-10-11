"use client";

import { TRIAL_SIGILS, trialRules, type SigilId } from "@/game/trials";

export default function TrialSelector({ value, onChange }: { value: SigilId[]; onChange: (ids: SigilId[]) => void }) {
  const rules = trialRules(value);
  return (
    <section className="trial-console" aria-labelledby="trial-title">
      <header className="trial-header">
        <div>
          <span className="trial-eyebrow">PACT OF THE ABYSS</span>
          <h3 id="trial-title">試練の呪印</h3>
          <p>自ら選ぶ苦難。その数だけ、魂は重くなる。</p>
        </div>
        <div className="trial-reward" aria-live="polite"><span>{rules.count} / 8 枚</span><strong>魂 ×{rules.soulMultiplier.toFixed(2)}</strong><small>呪印による報酬倍率</small></div>
      </header>
      <div className="trial-sigils">
        {TRIAL_SIGILS.map((s) => {
          const selected = value.includes(s.id);
          return (
            <button type="button" key={s.id} aria-pressed={selected} className={`sigil-card ${selected ? "is-bound" : ""}`} style={{ "--sigil-color": s.color } as React.CSSProperties}
              onClick={() => onChange(selected ? value.filter((id) => id !== s.id) : [...value, s.id])}>
              <span className="sigil-top"><span className="sigil-number">{s.rune}</span><span className="sigil-check" aria-hidden>{selected ? "✓" : "+"}</span></span>
              <span className="sigil-symbol" aria-hidden><svg viewBox="0 0 52 52" fill="none"><path d="M26 3 45 15v22L26 49 7 37V15Z"/><path d="M26 10v32M12 18l28 16M40 18 12 34"/><circle cx="26" cy="26" r="10"/></svg></span>
              <strong>{s.name}</strong><span className="sigil-description">{s.effect}</span><span className="sigil-bounty">魂 +{s.reward}%</span>
            </button>
          );
        })}
      </div>
      <footer className="trial-footer">
        <span>8枚すべてで <b>魂 ×2.46</b>。選択は出撃時にサーバーへ記録。</span>
        <div><button type="button" onClick={() => onChange([])} disabled={!value.length}>解除</button><button type="button" onClick={() => onChange(TRIAL_SIGILS.map((x) => x.id))} disabled={value.length === 8}>全てを背負う ↗</button></div>
      </footer>
    </section>
  );
}
