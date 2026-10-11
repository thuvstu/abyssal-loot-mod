const test = require('node:test');
const assert = require('node:assert/strict');
const root = process.env.ABYSS_TEST_BUILD || '/tmp/abyss-test-build';
const core = require(`${root}/core.js`);
const trials = require(`${root}/trials.js`);
const attrs = require(`${root}/attributes.js`);
const arts = require(`${root}/weapon-arts.js`);
const traps = require(`${root}/traps.js`);
const rules = require(`${root}/run-rules.js`);
const progression = require(`${root}/progression.js`);

const noop = () => {};
const gradient = { addColorStop: noop };
const ctx = new Proxy({ createLinearGradient: () => gradient, createRadialGradient: () => gradient, measureText: (t) => ({ width: t.length * 8 }) }, { get: (o, p) => p in o ? o[p] : noop, set: (o, p, v) => { o[p] = v; return true; } });
const canvas = () => ({ width: 980, height: 640, style: {}, getContext: () => ctx, getBoundingClientRect: () => ({ left: 0, top: 0, width: 980, height: 640 }), addEventListener: noop, removeEventListener: noop, setPointerCapture: noop });
global.window = { devicePixelRatio: 1, matchMedia: () => ({ matches: false }), addEventListener: noop, removeEventListener: noop };
global.document = { createElement: () => canvas(), visibilityState: 'visible', addEventListener: noop, removeEventListener: noop };
global.requestAnimationFrame = () => 1;
global.cancelAnimationFrame = noop;
global.localStorage = { getItem: () => 'true', setItem: noop };
const { Engine } = require(`${root}/engine.js`);

function game(sigils = [], classId = 'samurai') {
  let snapshot, summary;
  const prompts = { boons: [], passives: [], relics: [], loot: [] };
  const engine = new Engine(canvas(), { onSnapshot: (s) => snapshot = s, onLevelUp: (b) => prompts.boons.push(b), onLoot: (i) => prompts.loot.push(i),
    onFloorEvent: noop, onPassives: (p) => prompts.passives.push(p), onRelics: (r) => prompts.relics.push(r), onLootReview: noop, onEnd: (s) => summary = s, onLog: noop });
  engine.start(core.classById(classId), null, { ...progression.standardRun(), trialSigils: sigils, seed: 78731 });
  engine.obstacles = []; engine.hazards = []; engine.destructibles = []; engine.traps = [];
  engine.enemies = []; engine.toSpawn = 1; engine.spawnT = 999;
  engine.pushSnap();
  return { engine, prompts, snapshot: () => snapshot, summary: () => summary };
}
function enemy(e, id = 'slime', x = e.px + 90, y = e.py) {
  const target = e.spawnEnemy(false, x, y, id);
  assert.ok(target);
  target.spawnT = 0; target.hp = 10000; target.maxHp = 10000; target.shieldHp = -1; target.eliteTrait = null;
  return target;
}
function giveWeapon(e, style) {
  const weapon = core.genItem(core.mulberry32(98), 5, 'weapon');
  weapon.weaponClass = style; weapon.weaponTrait = undefined; weapon.mods = {};
  e.equipment.weapon = weapon; e.recompute(true);
}

test('eight unique sigils give exactly x2.46, with additive soul rewards', () => {
  const ids = trials.TRIAL_SIGILS.map((x) => x.id);
  const all = trials.trialRules(ids);
  assert.equal(all.count, 8); assert.equal(all.soulMultiplier, 2.46);
  assert.ok(Math.abs(all.enemyHp - 1.43) < 1e-9);
  assert.ok(Math.abs(all.enemyDamage - 1.342) < 1e-9);
  assert.equal(all.healing, .58); assert.equal(all.energy, .7);
  assert.ok(Math.abs(all.detection - 1.595) < 1e-9);
  assert.equal(trials.trialRules([]).soulMultiplier, 1);
});

test('invalid, duplicated and oversized sigils are rejected', () => {
  for (const bad of [null, 'might', ['made_up'], ['might', 'might'], Array(9).fill('might'), [2], ['__proto__']]) assert.throws(() => trials.validateSigils(bad));
  assert.deepEqual(trials.validateSigils(['abyss', 'might']), ['might', 'abyss']);
});

test('soul multiplier is derived from registered config, not submitted numbers', () => {
  const stats = { floor: 10, level: 12, kills: 100, score: 12000, victory: false, trueCleared: false };
  const plain = rules.calculateRunReward(stats, progression.standardRun());
  const burdened = rules.calculateRunReward(stats, { ...progression.standardRun(), trialSigils: trials.TRIAL_SIGILS.map((s) => s.id) });
  assert.equal(burdened.runSouls, Math.round(plain.runSouls * 2.46));
  assert.equal(burdened.trialBonus, burdened.runSouls - plain.runSouls);
});

test('attribute budget allows each point once and rejects overspending', () => {
  const start = attrs.emptyAttributes();
  assert.equal(attrs.availablePoints(4, start), 3);
  const a = attrs.allocatePoint(start, 'strength', 4, 3);
  assert.ok(a); assert.equal(a.strength, 3); assert.equal(attrs.attributeMods(a).damagePct, 9);
  assert.equal(attrs.allocatePoint(a, 'rhythm', 4), null);
  assert.equal(attrs.allocatePoint(start, 'strength', 4, -1), null);
  assert.throws(() => attrs.validateAttributes({ strength: 99 }, 4));
  assert.throws(() => attrs.validateAttributes({ fortune: -1 }, 4));
  assert.throws(() => attrs.validateAttributes({ fake: 2 }, 4));
});

test('fortune material rounding honours fractional +4% bonus', () => {
  const rng = core.mulberry32(62812);
  let total = 0;
  for (let i = 0; i < 10000; i++) total += attrs.materialAmount(1, 1, rng);
  assert.ok(total > 10280 && total < 10520, `unexpected total ${total}`);
});

test('shield blocks the frontal 120 degrees, sides/back deal 135%', () => {
  assert.equal(arts.guardMultiplier(0, Math.PI), .3);
  assert.equal(arts.guardMultiplier(0, 0), 1.35);
  assert.equal(arts.guardMultiplier(0, Math.PI / 2), 1.35);
  const { engine: e } = game();
  const guard = enemy(e, 'iron_guard'); guard.angle = 0;
  e.hitEnemy(guard, 100, false, '#fff', -1, 0);
  assert.equal(guard.maxHp - guard.hp, 30);
  e.combo = 0; guard.hp = guard.maxHp;
  e.hitEnemy(guard, 100, false, '#fff', 1, 0);
  assert.equal(guard.maxHp - guard.hp, 135);
  e.destroy();
});

test('art geometry is style-specific and rain emits thirteen shots', () => {
  const t = [{ id: 1, x: 200, y: 100, r: 20, angle: 0, hp: 500 }];
  assert.equal(arts.planWeaponArt('sword', 0, 0, 0, t).length, 3);
  const rain = arts.planWeaponArt('bow', 0, 0, 0, t);
  assert.equal(rain.length, 13); assert.ok(rain[12].delay > rain[0].delay);
  assert.equal(arts.planWeaponArt('staff', 0, 0, 0, t)[0].delay, .6);
  const dagger = arts.planWeaponArt('dagger', 0, 0, 0, t);
  assert.ok(dagger[0].x < t[0].x); assert.equal(dagger[1].targetId, 1);
  for (const style of Object.keys(arts.WEAPON_ARTS)) assert.ok(arts.planWeaponArt(style, 100, 100, 0, []).length > 0);
});

test('mastery charge cannot refill from its own damage or overkill', () => {
  assert.equal(arts.artChargeGain(9999, 10, true), 0);
  const { engine: e } = game();
  giveWeapon(e, 'axe');
  const t = enemy(e); t.hp = 1;
  e.hitEnemy(t, 999999, false, '#fff', 1, 0);
  assert.ok(e.weaponArtGauge < 1, `overkill generated ${e.weaponArtGauge}`);
  const next = enemy(e);
  e.weaponArtGauge = 100;
  assert.equal(e.useWeaponArt(), true);
  assert.equal(e.weaponArtGauge, 0);
  assert.equal(e.useWeaponArt(), false);
  assert.ok(next.hp < next.maxHp);
  e.destroy();
});

test('all twelve weapon arts execute safely with their own gauge', () => {
  for (const style of Object.keys(arts.WEAPON_ARTS)) {
    const { engine: e } = game();
    giveWeapon(e, style); const target = enemy(e);
    e.iframe = 10; // Isolate art damage from the separate incoming-damage gauge path.
    e.weaponArtGauge = 100;
    assert.equal(e.useWeaponArt(), true, style);
    for (let i = 0; i < 145; i++) e.update(1 / 120);
    assert.equal(e.weaponArtUses, 1, style);
    assert.equal(e.weaponArtGauge, 0, `${style} self charged`);
    assert.ok(target.hp < target.maxHp, `${style} dealt no damage`);
    e.render(1 / 60); e.destroy();
  }
});

test('famine reduces actual healing, pain reduces both resources', () => {
  const { engine: e } = game(['famine', 'pain']);
  e.derived.maxHp = 1000; e.hp = 500;
  e.healPlayer(100); assert.equal(e.hp, 558);
  e.energy = 0; e.gainEnergy(100); assert.equal(e.energy, 70);
  e.weaponArtGauge = 0;
  e.recordDamage(e.derived.damage, false);
  assert.ok(Math.abs(e.weaponArtGauge - 2.1) < 1e-8);
  e.destroy();
});

test('allocating attributes is pause-only and updates live derived stats', () => {
  const { engine: e, snapshot } = game();
  e.level = 4; e.recompute();
  const before = e.derived.damage;
  assert.equal(e.allocateAttribute('strength', 1), false);
  e.togglePause();
  assert.equal(e.allocateAttribute('strength', 3), true);
  assert.ok(e.derived.damage > before);
  assert.equal(snapshot().attributePoints, 0);
  assert.equal(e.allocateAttribute('fortune', 1), false);
  const clock = e.time; e.update(1); assert.equal(e.time, clock);
  e.destroy();
});

test('multi-level XP pickup keeps every level reward and attribute point', () => {
  const { engine: e, prompts, snapshot } = game();
  e.gainXp(350);
  const gained = e.level - 1;
  assert.ok(gained >= 4);
  for (let i = 0; i < gained; i++) {
    e.drainRewards(); assert.equal(e.status, 'levelup');
    const choices = prompts.boons.at(-1);
    e.chooseBoon(choices[0]);
  }
  e.drainRewards(); e.pushSnap();
  assert.equal(prompts.boons.length, gained);
  assert.equal(snapshot().attributePoints, gained);
  e.destroy();
});

test('spike/rock traps have a warning and explode once for both sides', () => {
  for (const kind of ['spike', 'rock']) {
    const trap = { id: 1, x: 100, y: 100, kind, state: 'armed', timer: 0, radius: 100, warning: .4 };
    const actors = [{ id: 0, x: 100, y: 100, r: 20, alive: true }, { id: 1, x: 100, y: 100, r: 20, alive: true }];
    assert.equal(traps.tickTraps([trap], .1, actors).length, 0);
    assert.equal(trap.state, 'warning');
    assert.equal(traps.tickTraps([trap], .5, actors).length, 1);
    assert.equal(traps.tickTraps([trap], .5, actors).length, 0);
  }
  const { engine: e } = game();
  e.hp = e.derived.maxHp; e.iframe = 0;
  const foe = enemy(e, 'cursed_gate', e.px + 24, e.py); foe.summonCd = 999;
  const hp = e.hp;
  e.traps = [{ id: 1, x: e.px, y: e.py, kind: 'spike', state: 'warning', timer: .01, radius: 100, warning: .36 }];
  e.update(.02);
  assert.ok(e.hp < hp); assert.ok(foe.hp < foe.maxHp);
  e.destroy();
});

test('summoning gate remains stationary, bounded and stops on death', () => {
  const { engine: e } = game();
  const gate = enemy(e, 'cursed_gate', e.px + 290, e.py); gate.summonCd = 0;
  const x = gate.x, y = gate.y;
  for (let i = 0; i < 8; i++) { gate.summonCd = 0; e.update(.01); }
  const children = e.enemies.filter((x) => x.summonParent === gate.id);
  assert.ok(children.length > 0 && children.length <= 6);
  assert.ok(children.every((x) => x.defId === 'skeleton' && x.spawnT > 0));
  assert.equal(gate.x, x); assert.equal(gate.y, y);
  const count = children.length;
  e.hitEnemy(gate, 100000, false, '#fff', 1, 0);
  assert.equal(e.gatesDestroyed, 1);
  e.killEnemy(gate); assert.equal(e.gatesDestroyed, 1);
  e.update(.01);
  assert.equal(e.enemies.filter((x) => x.summonParent === gate.id).length, count);
  e.destroy();
});

test('shielded hits do not leak HP damage or mastery charge', () => {
  const { engine: e } = game(); const target = enemy(e);
  target.shieldHp = 1000;
  e.hitEnemy(target, 30, false, '#fff', 1, 0);
  assert.equal(target.hp, target.maxHp); assert.equal(e.weaponArtGauge, 0);
  e.destroy();
});

test('dash has an actual cooldown, E does not pick up loot, forge pauses', () => {
  const { engine: e } = game();
  e.dash(); assert.ok(e.dashCd > 0);
  e.dashT = 0; const cd = e.dashCd; e.dash(); assert.equal(e.dashCd, cd);
  e.weaponArtGauge = 0;
  e.gitems = [{ x: e.px, y: e.py, t: 0, item: core.genItem(core.mulberry32(1), 1) }];
  e.onKey({ key: 'e', repeat: false, target: null, preventDefault: noop }, true);
  assert.equal(e.status, 'playing'); assert.equal(e.pendingLoot, null);
  assert.equal(e.openForge(), true);
  const time = e.time; e.update(1); assert.equal(e.time, time);
  e.closeForge(); assert.equal(e.status, 'playing');
  e.destroy();
});

test('uncollected equipment auto-stashes once and orbs are collected on transition', () => {
  const { engine: e } = game();
  const item = core.genItem(core.mulberry32(5), 3);
  e.gitems = [{ x: 100, y: 100, t: 1, item }];
  e.orbs = [{ x: 100, y: 100, vx: 0, vy: 0, kind: 'xp', value: 800, t: 1 }];
  const level = e.level;
  e.nextFloor();
  // Equipment is stashed (not sold) and the review prompt is queued.
  assert.equal(e.stash.length, 1);
  assert.equal(e.stash[0].id, item.id);
  assert.equal(e.pendingLootReview, true);
  // XP orbs are collected automatically, granting at least one level.
  assert.ok(e.level > level, `level should rise from ${level}`);
  assert.equal(e.orbs.length, 0);
  // Calling settle again must not duplicate the stash.
  e.settleFloorDrops();
  assert.equal(e.stash.length, 1);
  e.destroy();
});

test('bulk salvage pays gold and souls exactly once per stashed item', () => {
  const { engine: e } = game();
  const a = core.genItem(core.mulberry32(7), 3);
  const b = core.genItem(core.mulberry32(9), 4);
  e.stash = [a, b];
  const beforeGold = e.gold;
  const n = e.salvageAllFromStash();
  assert.equal(n, 2);
  assert.equal(e.stash.length, 0);
  const expectedGold = Math.round((4 + a.power * .6) * e.derived.goldMult) + Math.round((4 + b.power * .6) * e.derived.goldMult);
  assert.equal(e.gold - beforeGold, expectedGold);
  const again = e.salvageAllFromStash();
  assert.equal(again, 0);
  e.destroy();
});

test('RNG draw count does not change gameplay results at a different render FPS', () => {
  const a = game(), b = game();
  for (let i = 0; i < 5; i++) a.engine.render(1 / 60);
  assert.equal(a.engine.rng(), b.engine.rng());
  a.engine.destroy(); b.engine.destroy();
});


test('incoming damage deliberately charges weapon art as a separate path', () => {
  const { engine: e } = game();
  e.weaponArtGauge = 0; e.damagePlayer(10, e.px + 60, e.py);
  assert.ok(e.weaponArtGauge > 0 && e.weaponArtGauge < 10);
  e.destroy();
});


test('instant retreat has no soul reward, even with eight sigils', () => {
  const result = rules.calculateRunReward({ floor: 1, level: 10, kills: 0, score: 5000, victory: false, trueCleared: false }, { ...progression.standardRun(), trialSigils: trials.TRIAL_SIGILS.map((s) => s.id) });
  assert.equal(result.runSouls, 0); assert.equal(result.trialBonus, 0);
});

test('bossrush really schedules a boss in each of eight rooms, endless does not end at 26', () => {
  const { engine: e } = game();
  e.runConfig = progression.buildRun('bossrush');
  for (let floor = 1; floor <= 8; floor++) {
    e.floor = floor; e.enemies = [];
    assert.equal(e.isBossFloor(), true);
    const boss = e.spawnEnemy(true, 1200, 800);
    assert.ok(boss?.boss);
  }
  e.runConfig = progression.buildRun('endless'); e.floor = 26; e.enemies = [];
  assert.equal(e.isTrueFloor(), false); assert.equal(e.isBossFloor(), false);
  e.nextFloor(); assert.equal(e.floor, 27); assert.equal(e.status, 'playing');
  e.destroy();
});

test('a queued relic and passive never overwrite one another', () => {
  const { engine: e, prompts } = game();
  e.offerPassives(); e.offerRelic();
  e.drainRewards(); assert.equal(e.status, 'passive');
  const time = e.time; e.update(.5); assert.equal(e.time, time);
  e.choosePassive(prompts.passives[0][0]);
  e.drainRewards(); assert.equal(e.status, 'relic');
  e.chooseRelic(prompts.relics[0][0]);
  assert.equal(e.relics.length, 1); assert.equal(e.passives.length, 1);
  e.destroy();
});

test('E activates a full-gauge art exactly once per keydown, not on key repeat', () => {
  const { engine: e } = game(); giveWeapon(e, 'katana'); enemy(e);
  e.weaponArtGauge = 100;
  e.onKey({ key: 'e', repeat: false, target: null, preventDefault: noop }, true);
  assert.equal(e.weaponArtUses, 1);
  e.weaponArtGauge = 100; e.masteryCd = 0;
  e.onKey({ key: 'e', repeat: true, target: null, preventDefault: noop }, true);
  assert.equal(e.weaponArtUses, 1); assert.equal(e.weaponArtGauge, 100);
  e.destroy();
});

test('dash cancels charging stance immediately into invincible evasion without input lock', () => {
  const { engine: e } = game();
  e.charging = true;
  e.chargeT = 0.8;
  e.dash();
  assert.equal(e.charging, false);
  assert.equal(e.chargeT, 0);
  assert.ok(e.dashT > 0);
  assert.ok(e.iframe > 0);
  e.destroy();
});

test('attack buffer queues an attack during cooldown and triggers instantly upon cooldown finish', () => {
  const { engine: e } = game();
  e.atkCd = 0.05;
  e.attackBuffer = 0.2;
  const initialCombo = e.combo;
  // Advance by 0.06s (clearing atkCd)
  e.update(0.06);
  // attackBuffer is consumed and attack fires
  assert.equal(e.attackBuffer, 0);
  e.destroy();
});
