import "dotenv/config";
import { test, expect, devices } from "@playwright/test";
import { eq, inArray } from "drizzle-orm";
import { db, pool } from "../../src/db";
import { heroes, runs } from "../../src/db/schema";
import { TRIAL_SIGILS } from "../../src/game/trials";
import { emptyAttributes } from "../../src/game/attributes";

const created = new Set<string>();
const stamp = Date.now().toString(36);
const all = TRIAL_SIGILS.map((s) => s.id);

test.afterAll(async () => {
  if (created.size) await db.delete(heroes).where(inArray(heroes.id, [...created]));
  await pool.end();
});

test('server registers sigils, rejects tampering, settles one time and protects other heroes', async ({ request }) => {
  const name = `QA${stamp}A`.slice(0, 16);
  const [a, b] = await Promise.all([
    request.post('/api/heroes', { data: { name, classId: 'samurai' } }),
    request.post('/api/heroes', { data: { name, classId: 'samurai' } }),
  ]);
  expect(a.ok()).toBeTruthy(); expect(b.ok()).toBeTruthy();
  const hero = (await a.json()).hero; created.add(hero.id);
  expect((await b.json()).hero.id).toBe(hero.id);
  const other = (await (await request.post('/api/heroes', { data: { name: `QA${stamp}B`.slice(0, 16), classId: 'mage' } })).json()).hero;
  created.add(other.id);
  const registered = await request.post('/api/expeditions', { data: { heroId: hero.id, classId: 'samurai', mode: 'standard', difficulty: 'normal', trialSigils: all } });
  expect(registered.ok()).toBeTruthy();
  const exp = await registered.json();
  expect(exp.trialCount).toBe(8); expect(exp.trialSoulMultiplier).toBe(2.46);
  for (const invalid of [['might', 'might'], ['unknown'], Array(9).fill('might')]) {
    const rejected = await request.post('/api/expeditions', { data: { heroId: hero.id, classId: 'samurai', mode: 'standard', difficulty: 'normal', trialSigils: invalid } });
    expect(rejected.status()).toBe(400);
  }
  const data = { heroId: hero.id, expeditionId: exp.expeditionId, classId: 'samurai', challengeMode: 'standard', challengeId: 'standard', difficulty: 'normal',
    floor: 10, level: 12, kills: 100, gold: 100, score: 12000, durationSec: 240, victory: false, trueCleared: false,
    trialSigils: all, trialCount: 8, attributes: { ...emptyAttributes(), strength: 3, fortune: 2 }, gatesDestroyed: 1, weaponArtUses: 10, bossesKilled: 1,
    eventsCompleted: 3, bestCombo: 50, perfectDodges: 5, build: [], artifactsFound: [] };
  expect((await request.post('/api/runs', { data: { ...data, trialCount: 999 } })).status()).toBe(400);
  expect((await request.post('/api/runs', { data: { ...data, trialSigils: [], trialCount: 0 } })).status()).toBe(409);
  expect((await request.post('/api/runs', { data: { ...data, heroId: other.id } })).status()).toBe(403);
  expect((await request.post('/api/runs', { data: { ...data, attributes: { strength: 99 } } })).status()).toBe(400);
  const [r1, r2] = await Promise.all([request.post('/api/runs', { data }), request.post('/api/runs', { data })]);
  expect(r1.status()).toBe(200); expect(r2.status()).toBe(200);
  const first = await r1.json(), second = await r2.json();
  expect(first.run.id).toBe(second.run.id);
  expect(first.trialCount).toBe(8); expect(first.trialSoulMultiplier).toBe(2.46);
  const baseSouls = Math.round(10 * 14 + 100 * .6 + 12 * 8 + 12000 / 40);
  expect(first.runSouls).toBe(Math.round(baseSouls * 2.46));
  expect(first.achievements.some((x: { id: string }) => x.id === 'gate-breaker')).toBeTruthy();
  expect(first.achievements.some((x: { id: string }) => x.id === 'eight-sigils')).toBeTruthy();
  expect(first.achievements.some((x: { id: string }) => x.id === 'art-master')).toBeTruthy();
  const [heroAfter] = await db.select().from(heroes).where(eq(heroes.id, hero.id));
  expect(heroAfter.totalRuns).toBe(1); expect(heroAfter.souls).toBe(first.soulsEarned);
  const stored = await db.select().from(runs).where(eq(runs.expeditionId, exp.expeditionId));
  expect(stored).toHaveLength(1); expect(stored[0].trialSigils).toEqual(all); expect(stored[0].attributes.strength).toBe(3);
  const upgrade = await request.post('/api/meta', { data: { heroId: hero.id, key: 'dmg' } });
  expect(upgrade.status()).toBe(200);
  const [otherAfter] = await db.select().from(heroes).where(eq(heroes.id, other.id));
  expect(otherAfter.souls).toBe(0); expect(otherAfter.meta).toEqual({});
});

test('desktop pact, attributes, workshop and persisted run operate without browser errors', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '試練の呪印' })).toBeVisible();
  await page.locator('input[placeholder="例：アッシュ"]').fill(`QA${stamp}D`.slice(0, 16));
  await page.getByRole('button', { name: /主の巡礼/ }).click();
  await page.getByRole('button', { name: '全てを背負う ↗' }).click();
  await expect(page.locator('.sigil-card[aria-pressed="true"]')).toHaveCount(8);
  await expect(page.locator('.trial-reward strong')).toHaveText('魂 ×2.46');
  await page.locator('.trial-console').screenshot({ path: '.artifacts/trials-desktop.png' });
  const response = page.waitForResponse((r) => r.url().endsWith('/api/expeditions') && r.request().method() === 'POST');
  await page.getByRole('button', { name: '迷 宮 へ 出 撃' }).click();
  const opened = await response; expect(opened.status()).toBe(200);
  const cfg = await opened.json();
  const launch = await db.select().from(heroes).where(eq(heroes.name, `QA${stamp}D`.slice(0, 16)));
  for (const h of launch) created.add(h.id);
  expect(cfg.config.trialSigils).toHaveLength(8);
  await expect(page.locator('canvas')).toBeVisible();
  await expect(page.locator('.game-trial-strip')).toContainText('×2.46');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('heading', { name: '属性を刻む' })).toBeVisible();
  await expect(page.locator('.attribute-remaining b')).toHaveText('9');
  await page.getByRole('button', { name: '剛力に1ポイント配分' }).click();
  await expect(page.locator('.attribute-remaining b')).toHaveText('8');
  await expect(page.locator('.attribute-row').first()).toContainText('1 pt');
  await page.locator('.pause-build').screenshot({ path: '.artifacts/attributes-desktop.png' });
  await page.keyboard.press('Escape');
  await expect(page.locator('.pause-build')).toBeHidden();
  await expect(page.locator('.weapon-art-control')).toBeVisible();
  await page.keyboard.press('f');
  await expect(page.getByRole('heading', { name: /鍛/ }).last()).toBeVisible();
  await page.keyboard.press('f');
  const settled = page.waitForResponse((r) => r.url().endsWith('/api/runs') && r.request().method() === 'POST');
  await page.getByRole('button', { name: '撤退', exact: true }).click();
  const saved = await settled; expect(saved.status()).toBe(200);
  const savedData = await saved.json();
  expect(savedData.run.attributes.strength).toBe(1);
  expect(savedData.run.trialSigils).toHaveLength(8);
  await expect(page.getByText('記録・報酬を保存しました。呪印の条件はサーバーで照合済み。')).toBeVisible();
  expect(errors).toEqual([]);
});

test('mobile portrait and landscape expose touch art and allocation with no horizontal overflow', async ({ browser }) => {
  const context = await browser.newContext({ ...devices['iPhone 13'], browserName: undefined } as Parameters<typeof browser.newContext>[0]);
  const page = await context.newPage();
  const errors: string[] = []; page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('http://127.0.0.1:3000/');
  await page.locator('input[placeholder="例：アッシュ"]').fill(`QA${stamp}M`.slice(0, 16));
  await page.getByRole('button', { name: /主の巡礼/ }).tap();
  await page.locator('.sigil-card').filter({ hasText: '注視' }).tap();
  await expect(page.locator('.sigil-card[aria-pressed="true"]')).toHaveCount(1);
  // Chromium's oversized locator screenshot can reset touch emulation; capture only after interaction checks.
  expect(await page.evaluate(() => navigator.maxTouchPoints > 0)).toBeTruthy();
  const response = page.waitForResponse((r) => r.url().endsWith('/api/expeditions'));
  await page.getByRole('button', { name: '迷 宮 へ 出 撃' }).tap();
  expect((await response).status()).toBe(200);
  const launch = await db.select().from(heroes).where(eq(heroes.name, `QA${stamp}M`.slice(0, 16)));
  for (const h of launch) created.add(h.id);
  await expect(page.locator('.touch-art')).toBeVisible();
  await expect(page.locator('.unspent-points')).toBeVisible();
  await page.getByRole('button', { name: '停止', exact: true }).tap();
  await page.getByRole('button', { name: '幸運に5ポイント配分' }).tap();
  await expect(page.locator('.attribute-remaining b')).toHaveText('4');
  await page.screenshot({ path: '.artifacts/attributes-mobile.png' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy();
  await page.setViewportSize({ width: 844, height: 390 });
  await expect(page.getByRole('heading', { name: '属性を刻む' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy();
  expect(errors).toEqual([]);
  await context.close();
});
