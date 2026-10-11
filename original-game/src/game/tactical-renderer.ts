type TacticalBody = { shape: string; r: number; angle: number; color: string; accent: string; t: number; summonCd?: number };

/** Called in the enemy-local transform; no gameplay RNG is consumed by drawing. */
export function drawTacticalBody(g: CanvasRenderingContext2D, e: TacticalBody, font: string) {
  const r = e.r;
  g.save();
  if (e.shape === "guard") {
    g.rotate(e.angle);
    g.fillStyle = "#172434"; g.strokeStyle = "#a7c4df"; g.lineWidth = 3;
    g.beginPath(); g.moveTo(-r, -r * .65); g.lineTo(r * .55, -r * .9); g.lineTo(r, 0); g.lineTo(r * .55, r * .9); g.lineTo(-r, r * .65); g.closePath(); g.fill(); g.stroke();
    g.fillStyle = "#d7ebff"; g.fillRect(r * .18, -5, 9, 10);
    // Shield panel and exact 120-degree protection sector.
    g.fillStyle = "#5a83a544"; g.strokeStyle = "#8ed4ff"; g.lineWidth = 5;
    g.beginPath(); g.arc(0, 0, r + 12, -Math.PI / 3, Math.PI / 3); g.stroke();
    g.beginPath(); g.moveTo(0, 0); g.arc(0, 0, r + 14, -Math.PI / 3, Math.PI / 3); g.closePath(); g.fill();
    g.lineWidth = 4; g.beginPath(); g.moveTo(r + 20, 0); g.lineTo(r + 45, 0); g.lineTo(r + 33, -10); g.moveTo(r + 45, 0); g.lineTo(r + 33, 10); g.stroke();
    g.fillStyle = "#ffc580";
    g.beginPath(); g.moveTo(-r - 15, -9); g.lineTo(-r - 4, 0); g.lineTo(-r - 15, 9); g.closePath(); g.fill();
    g.rotate(-e.angle);
    g.font = `bold 16px ${font}`; g.textAlign = "center"; g.lineWidth = 4; g.strokeStyle = "#05070c"; g.fillStyle = "#b8e6ff";
    g.strokeText("盾・正面120°", 0, -r - 38); g.fillText("盾・正面120°", 0, -r - 38);
  } else if (e.shape === "necromancer") {
    g.fillStyle = "#251b3c"; g.strokeStyle = e.color; g.lineWidth = 3;
    g.beginPath(); g.moveTo(0, -r * 1.3); g.lineTo(r, r); g.lineTo(0, r * .6); g.lineTo(-r, r); g.closePath(); g.fill(); g.stroke();
    g.fillStyle = "#efe3da"; g.beginPath(); g.arc(0, -r * .2, r * .5, 0, 7); g.fill();
    g.fillStyle = "#2e153f"; g.fillRect(-7, -8, 4, 5); g.fillRect(3, -8, 4, 5);
    g.strokeStyle = "#b4e6a8"; g.lineWidth = 3; g.beginPath(); g.moveTo(r * .8, r); g.lineTo(r * 1.1, -r * 1.5); g.stroke();
    g.fillStyle = "#93f0ab"; g.beginPath(); g.arc(r * 1.1, -r * 1.5, 6, 0, 7); g.fill();
    g.strokeStyle = "#b7a2e5"; g.setLineDash([5, 8]); g.lineDashOffset = -e.t * 15; g.beginPath(); g.arc(0, 0, r + 13, 0, 7); g.stroke();
  } else {
    g.fillStyle = "#17121e"; g.strokeStyle = "#c76399"; g.lineWidth = 5;
    g.beginPath(); g.moveTo(-r * 1.1, r); g.lineTo(-r, -r * 1.1); g.lineTo(0, -r * 1.45); g.lineTo(r, -r * 1.1); g.lineTo(r * 1.1, r); g.closePath(); g.fill(); g.stroke();
    const aura = g.createRadialGradient(0, 0, 2, 0, 0, r * 1.4);
    aura.addColorStop(0, "#ed9ecc"); aura.addColorStop(.28, "#632950"); aura.addColorStop(1, "#190d25");
    g.fillStyle = aura; g.beginPath(); g.ellipse(0, 0, r * .72, r * 1.04, 0, 0, 7); g.fill();
    g.strokeStyle = "#ef8abd"; g.lineWidth = 2;
    g.save(); g.rotate(e.t * .9);
    for (let i = 0; i < 3; i++) { g.rotate(Math.PI / 3); g.beginPath(); g.ellipse(0, 0, r * .7, r * .32, 0, 0, 7); g.stroke(); }
    g.restore();
    g.textAlign = "center"; g.font = `bold 17px ${font}`; g.strokeStyle = "#100815"; g.lineWidth = 5; g.fillStyle = "#ffc4d8";
    g.strokeText("召喚門 ─ 破壊せよ", 0, -r - 36); g.fillText("召喚門 ─ 破壊せよ", 0, -r - 36);
    g.font = `12px ${font}`; g.fillText(`増援 ${Math.max(0, e.summonCd ?? 0).toFixed(1)}s`, 0, r + 24);
  }
  g.restore();
}
