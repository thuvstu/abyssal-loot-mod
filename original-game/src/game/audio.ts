// ===== 軽量プロシージャル効果音エンジン（アセット不要・WebAudio生成） =====

type SfxName =
  | "hit" | "crit" | "kill" | "levelup" | "pickup" | "equip" | "forge"
  | "boss" | "death" | "skill" | "dash" | "parry" | "ui" | "portal" | "achievement" | "error";

let ctx: AudioContext | null = null;
let muted = false;

export function initAudio() {
  if (typeof window === "undefined") return;
  try {
    muted = localStorage.getItem("abyss_muted") === "1";
  } catch { /* noop */ }
}

export function isMuted() {
  return muted;
}

export function setMuted(v: boolean) {
  muted = v;
  try { localStorage.setItem("abyss_muted", v ? "1" : "0"); } catch { /* noop */ }
}

function getCtx(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (!ctx) {
    const Ctor = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    ctx = new Ctor();
  }
  if (ctx.state === "suspended") void ctx.resume();
  return ctx;
}

function tone(
  c: AudioContext,
  freq: number,
  dur: number,
  type: OscillatorType,
  gain: number,
  delay = 0,
  freqEnd?: number,
) {
  const osc = c.createOscillator();
  const g = c.createGain();
  osc.type = type;
  const t0 = c.currentTime + delay;
  osc.frequency.setValueAtTime(freq, t0);
  if (freqEnd !== undefined) osc.frequency.exponentialRampToValueAtTime(Math.max(1, freqEnd), t0 + dur);
  g.gain.setValueAtTime(gain, t0);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  osc.connect(g);
  g.connect(c.destination);
  osc.start(t0);
  osc.stop(t0 + dur + 0.02);
}

function noise(c: AudioContext, dur: number, gain: number, delay = 0, filterFreq = 2000) {
  const bufferSize = Math.floor(c.sampleRate * dur);
  const buffer = c.createBuffer(1, bufferSize, c.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < bufferSize; i++) data[i] = Math.random() * 2 - 1;
  const src = c.createBufferSource();
  src.buffer = buffer;
  const filter = c.createBiquadFilter();
  filter.type = "lowpass";
  filter.frequency.value = filterFreq;
  const g = c.createGain();
  const t0 = c.currentTime + delay;
  g.gain.setValueAtTime(gain, t0);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  src.connect(filter);
  filter.connect(g);
  g.connect(c.destination);
  src.start(t0);
  src.stop(t0 + dur + 0.02);
}

// スロットル: 同時多発で音割れしないよう最小間隔を設ける
const lastPlay: Partial<Record<SfxName, number>> = {};
const THROTTLE: Partial<Record<SfxName, number>> = { hit: 0.03, pickup: 0.05, ui: 0.05 };

export function playSfx(name: SfxName, opts?: { pitch?: number }) {
  if (muted) return;
  const c = getCtx();
  if (!c) return;
  const now = c.currentTime;
  const th = THROTTLE[name];
  if (th && lastPlay[name] !== undefined && now - (lastPlay[name] as number) < th) return;
  lastPlay[name] = now;
  const p = opts?.pitch ?? 1;

  switch (name) {
    case "hit":
      tone(c, 180 * p, 0.06, "square", 0.05, 0, 90 * p);
      break;
    case "crit":
      tone(c, 420 * p, 0.09, "sawtooth", 0.07, 0, 180 * p);
      noise(c, 0.05, 0.03, 0, 3500);
      break;
    case "kill":
      tone(c, 260 * p, 0.12, "triangle", 0.06, 0, 90);
      break;
    case "levelup":
      [0, 0.09, 0.18].forEach((d, i) => tone(c, 440 * Math.pow(1.26, i), 0.18, "triangle", 0.08, d));
      break;
    case "pickup":
      tone(c, 740, 0.07, "sine", 0.05, 0, 980);
      break;
    case "equip":
      tone(c, 320, 0.09, "square", 0.06, 0, 520);
      tone(c, 220, 0.09, "square", 0.04, 0.03);
      break;
    case "forge":
      noise(c, 0.3, 0.06, 0, 1200);
      [0, 0.08, 0.16, 0.3].forEach((d, i) => tone(c, 180 + i * 90, 0.2, "sawtooth", 0.06, d, 60 + i * 60));
      break;
    case "boss":
      tone(c, 70, 0.5, "sawtooth", 0.09, 0, 40);
      noise(c, 0.4, 0.05, 0, 400);
      break;
    case "death":
      tone(c, 220, 0.6, "sawtooth", 0.08, 0, 40);
      noise(c, 0.5, 0.05, 0.05, 500);
      break;
    case "skill":
      tone(c, 160, 0.3, "sawtooth", 0.08, 0, 640);
      noise(c, 0.25, 0.04, 0, 2200);
      break;
    case "dash":
      tone(c, 500, 0.08, "sine", 0.04, 0, 180);
      break;
    case "parry":
      tone(c, 900, 0.05, "square", 0.06, 0, 1600);
      noise(c, 0.04, 0.03, 0, 4000);
      break;
    case "portal":
      tone(c, 300, 0.4, "sine", 0.05, 0, 760);
      break;
    case "achievement":
      [0, 0.1, 0.2, 0.32].forEach((d, i) => tone(c, 523.25 * Math.pow(1.19, i), 0.22, "triangle", 0.07, d));
      break;
    case "error":
      tone(c, 140, 0.15, "square", 0.06, 0, 90);
      break;
    case "ui":
    default:
      tone(c, 600, 0.04, "sine", 0.035, 0, 760);
      break;
  }
}
