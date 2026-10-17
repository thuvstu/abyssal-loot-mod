package com.abyssalloot;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 原作ルールのMC移植が壊れていないことを保証する.
 * docs/TRIALS-AND-WEAPON-ARTS.md の数値と突き合わせること.
 */
public class GameDataTest {

    @Test
    void allSigilsSoulMultIs246() {
        Set<String> all = new HashSet<>();
        for (GameData.Sigil s : GameData.Sigil.values()) all.add(s.id);
        assertEquals(8, all.size());
        // 全選択で 1 + 146/100 = 2.46
        assertEquals(2.46, GameData.soulMult(all), 1e-9);
    }

    @Test
    void noSigilsIsNeutral() {
        Set<String> none = new HashSet<>();
        assertEquals(1.0, GameData.soulMult(none), 1e-9);
        assertEquals(1.0, GameData.enemyHpMult(none), 1e-9);
        assertEquals(1.0, GameData.enemyDmgMult(none), 1e-9);
    }

    @Test
    void sigilEffectsMatchDocs() {
        assertEquals(1.30, GameData.enemyHpMult(Set.of("might")), 1e-9);
        assertEquals(1.22, GameData.enemyDmgMult(Set.of("fangs")), 1e-9);
        // 深淵は全部に×1.10
        assertEquals(1.10, GameData.enemyHpMult(Set.of("abyss")), 1e-9);
        assertEquals(1.10, GameData.enemyDmgMult(Set.of("abyss")), 1e-9);
        assertEquals(1.30 * 1.10, GameData.enemyHpMult(Set.of("might", "abyss")), 1e-9);
    }

    @Test
    void heroGrowthMatchesCoreTs() {
        // samurai: hp 94+11/lv, damage 20*(1+0.17*(lv-1))
        var samurai = GameData.HeroClass.byId("samurai");
        assertEquals(94.0, GameData.heroMaxHp(samurai, 1), 1e-9);
        assertEquals(105.0, GameData.heroMaxHp(samurai, 2), 1e-9);
        assertEquals(20.0, GameData.heroDamage(samurai, 1), 1e-9);
        assertEquals(20.0 * 1.17, GameData.heroDamage(samurai, 2), 1e-9);
    }

    @Test
    void classSkillNamesExist() {
        for (GameData.HeroClass c : GameData.HeroClass.values()) {
            assertNotNull(c.skillName());
            assertFalse(c.skillName().isBlank());
        }
        assertEquals(8, GameData.HeroClass.values().length);
        assertEquals("天矢雨", GameData.HeroClass.byId("archer").skillName());
    }

    @Test
    void skillTierMultIsTiered() {
        assertEquals(1.0, GameData.skillTierMult(1), 1e-9);
        assertEquals(1.0, GameData.skillTierMult(9), 1e-9);
        assertEquals(1.3, GameData.skillTierMult(10), 1e-9);
        assertEquals(1.6, GameData.skillTierMult(20), 1e-9);
        assertEquals(1.9, GameData.skillTierMult(30), 1e-9);
        assertEquals(1.9, GameData.skillTierMult(99), 1e-9);
        assertEquals("", GameData.skillTierName(1));
        assertEquals("・改", GameData.skillTierName(10));
        assertEquals("・極", GameData.skillTierName(20));
        assertEquals("・神", GameData.skillTierName(30));
    }

    @Test
    void allWeaponArtsExist() {
        assertEquals(33, GameData.WeaponStyle.values().length);
        for (GameData.WeaponStyle w : GameData.WeaponStyle.values()) {
            assertNotNull(w.artName);
            assertFalse(w.artDesc.isBlank());
            // 逆引きが壊れていないこと
            assertEquals(w, GameData.WeaponStyle.byId(w.id));
        }
    }

    @Test
    void unknownIdsFallBackSafely() {
        assertEquals(GameData.HeroClass.SAMURAI, GameData.HeroClass.byId("nope"));
        assertEquals(GameData.WeaponStyle.SWORD, GameData.WeaponStyle.byId("nope"));
        assertNull(GameData.Sigil.byId("nope"));
    }

    @Test
    void eightSigilsDefined() {
        assertEquals(8, GameData.Sigil.values().length);
        int total = 0;
        for (GameData.Sigil s : GameData.Sigil.values()) total += s.reward;
        assertEquals(146, total);
    }

    private static Set<String> allSigils() {
        Set<String> all = new HashSet<>();
        for (GameData.Sigil s : GameData.Sigil.values()) all.add(s.id);
        return all;
    }

    @Test
    void trialRulesAllSigilsMatchTrialsTs() {
        // 原作 trials.ts trialRules() の全選択値
        var r = GameData.trialRules(allSigils());
        assertEquals(1.30 * 1.10, r.enemyHp(), 1e-9);
        assertEquals(1.22 * 1.10, r.enemyDamage(), 1e-9);
        assertEquals(1.16 * 1.10, r.enemySpeed(), 1e-9);   // 深淵は移動にも効く
        assertEquals(1.9, r.eliteRate(), 1e-9);
        assertEquals(0.58, r.healing(), 1e-9);
        assertEquals(0.70, r.energy(), 1e-9);
        assertEquals(1.45 * 1.10, r.detection(), 1e-9);    // 注視・深淵は索敵にも効く
        assertEquals(2.46, r.soulMultiplier(), 1e-9);
    }

    @Test
    void neutralTrialRulesAreIdentity() {
        var r = GameData.trialRules(new HashSet<>());
        assertEquals(1.0, r.enemySpeed(), 1e-9);
        assertEquals(1.0, r.eliteRate(), 1e-9);
        assertEquals(1.0, r.healing(), 1e-9);
        assertEquals(1.0, r.energy(), 1e-9);
        assertEquals(1.0, r.detection(), 1e-9);
    }

    @Test
    void eliteChanceFollowsFloorAndFrenzyWithCap() {
        // 原作: clamp((0.05 + floor*0.014) * eliteRate, 0, 0.9)
        assertEquals(0.05, GameData.eliteChance(0, new HashSet<>()), 1e-9);
        assertEquals(0.05 + 10 * 0.014, GameData.eliteChance(10, new HashSet<>()), 1e-9);
        assertEquals((0.05 + 10 * 0.014) * 1.9,
                GameData.eliteChance(10, Set.of("frenzy")), 1e-9);
        assertEquals(0.9, GameData.eliteChance(50, Set.of("frenzy")), 1e-9);
    }

    @Test
    void dashCooldownPerClassAndAgility() {
        var ninja = GameData.HeroClass.NINJA;
        var samurai = GameData.HeroClass.SAMURAI;
        assertEquals(0.62, GameData.dashCooldown(ninja, 0), 1e-6); // float 定数の誤差を許容
        assertEquals(1.10, GameData.dashCooldown(samurai, 0), 1e-6); // float 定数の誤差を許容
        assertEquals(1.10 * 0.9, GameData.dashCooldown(samurai, 10), 1e-6); // float 定数の誤差を許容
        // 敏捷は最大65%まで短縮、下限0.28秒
        assertEquals(0.28, GameData.dashCooldown(ninja, 65), 1e-6); // float 定数の誤差を許容
        assertEquals(0.28, GameData.dashCooldown(ninja, 999), 1e-6); // float 定数の誤差を許容
    }

    @Test
    void moveSpeedUsesClassBaseFromCore() {
        // 原作 moveSpeed 250 (剣聖) を MC既定 0.1 に換算
        assertEquals(0.1, GameData.HeroClass.SAMURAI.mcMoveSpeed(), 1e-9);
        assertTrue(GameData.HeroClass.NINJA.mcMoveSpeed() > GameData.HeroClass.PALADIN.mcMoveSpeed());
    }

    @Test
    void artChargeFollowsDamageWithoutOverkillOrSelfCharge() {
        // 原作 artChargeGain: min(7, 実ダメ / max(10, 攻撃) * 3). 奥義自身は0
        assertEquals(0.0, GameData.artChargeGain(100, 20, true), 1e-9);
        assertEquals(0.0, GameData.artChargeGain(0, 20, false), 1e-9);
        assertEquals(1.5, GameData.artChargeGain(10, 20, false), 1e-9);
        assertEquals(7.0, GameData.artChargeGain(1000, 20, false), 1e-9);
        // 攻撃力が10未満でも分母は10
        assertEquals(3.0, GameData.artChargeGain(10, 1, false), 1e-9);
    }

    @Test
    void damageTakenChargeIsProportionalToMaxHp() {
        // 原作: suffered / maxHp * 35
        assertEquals(17.5, GameData.damageTakenCharge(50, 100), 1e-9);
        assertEquals(0.0, GameData.damageTakenCharge(0, 100), 1e-9);
    }

    @Test
    void materialAmountKeepsFortuneBonusViaStochasticRounding() {
        // 幸運1点=+4%. 端数は確率丸めで期待値が一致する
        assertEquals(2, GameData.materialAmount(1, 25, () -> 0.99)); // exact=2.0
        assertEquals(2, GameData.materialAmount(1, 1, () -> 0.0));   // exact=1.04 -> 0.04の確率で+1 -> 当たり
        assertEquals(1, GameData.materialAmount(1, 1, () -> 0.5));
        assertEquals(0, GameData.materialAmount(0, 25, () -> 0.0));
    }

    @Test
    void fortuneIsLuckAndDropBonus() {
        assertEquals(21, GameData.luckOf(3));
        assertEquals(0.0, GameData.luckDropBonus(0), 1e-9);
        assertEquals(0.07, GameData.luckDropBonus(100), 1e-9);
    }

    @Test
    void specialSlotsFollowFloorGates() {
        // 護衛兵 4層〜 (i%5==3)
        assertNull(GameData.specialSlotRole(3, 3));
        assertEquals("guard", GameData.specialSlotRole(4, 3));
        // 蘇生師 6層〜 (i%7==5)
        assertEquals("necromancer", GameData.specialSlotRole(6, 5));
        assertNull(GameData.specialSlotRole(5, 5));
        // 召喚門 7層〜 (i%9==8). 6層では門は湧かず、8%5==3 なので護衛兵になる
        assertEquals("gate", GameData.specialSlotRole(7, 8));
        assertEquals("guard", GameData.specialSlotRole(6, 8));
        // 門は護衛兵より優先 (17%9==8 かつ 17%5==2 なので門)
        assertEquals("gate", GameData.specialSlotRole(7, 17));
    }

    @Test
    void floorScalingMatchesCoreTs() {
        assertEquals(1.0, GameData.floorHpScale(0), 1e-9);
        assertEquals(1 + 10 * 0.30 + 100 * 0.004, GameData.floorHpScale(10), 1e-9);
        assertEquals(1 + 10 * 0.12 + 100 * 0.002, GameData.floorDmgScale(10), 1e-9);
    }

    @Test
    void twelveFloorMutationsWithUniqueIds() {
        assertEquals(9, GameData.MUTATIONS.size() - 3); // 元の9種が残っている
        var ids = new java.util.HashSet<String>();
        for (var m : GameData.MUTATIONS) ids.add(m.id());
        assertEquals(12, ids.size());
        // 原作の値の抜粋: 血の饗宴 攻撃1.38 / 豊穣の刻 ドロップ1.85
        assertEquals(1.38, GameData.mutationById("bloodfeast").enemyDmg(), 1e-9);
        assertEquals(1.85, GameData.mutationById("bounty").loot(), 1e-9);
    }

    @Test
    void mutationRollFollowsCoreChances() {
        // 通常層 62%, 層主の層 14%, 真の決戦層 0%
        assertNull(GameData.rollMutation(() -> 0.9, 3));            // 0.9 > 0.62
        assertNotNull(GameData.rollMutation(() -> 0.5, 3));         // 0.5 <= 0.62
        assertNull(GameData.rollMutation(() -> 0.2, 5));            // 層主: 0.2 > 0.14
        assertNotNull(GameData.rollMutation(() -> 0.1, 5));         // 層主: 0.1 <= 0.14
        assertNull(GameData.rollMutation(() -> 0.0, 26));           // 真の決戦層は平穏
        // 一様乱数 0.5 は index = floor(0.5*12) = 6 (豊穣の刻)
        assertEquals("bounty", GameData.rollMutation(() -> 0.5, 3).id());
    }

    @Test
    void runRewardMatchesOriginalFormula() {
        // 勝利: 25*14 + 100*0.6 + 10*8 + 0/40 + 900 = 1390
        var r = GameData.runReward(25, 100, 10, 0, true, false, 1.0, 1.0, 1.0);
        assertEquals(1390, r.baseSouls());
        // 極難 x1.5 -> 2085, 呪印 x2.46 -> 5129.1 -> 5129
        var h = GameData.runReward(25, 100, 10, 0, true, false, 1.0, 1.5, 2.46);
        assertEquals(2085, h.beforeTrials());
        assertEquals(5129, h.runSouls());
        assertEquals(5129 - 2085, h.trialBonus());
        // 何も進めずに離脱すると 0
        assertEquals(0, GameData.runReward(1, 0, 1, 0, false, false, 1.0, 1.0, 2.0).runSouls());
        // 真の深淵は +6000
        assertEquals(1390 + 6000, GameData.runReward(25, 100, 10, 0, true, true, 1.0, 1.0, 1.0).baseSouls());
        // スコア項: スコア4000 → +100
        assertEquals(1390 + 100, GameData.runReward(25, 100, 10, 4000, true, false, 1.0, 1.0, 1.0).baseSouls());
    }

    @Test
    void scoreFormulaMatchesSummarize() {
        // 層1・撃破0・Lv1・欠片0・与ダメ0・非勝利・コンボ0 → 620+0+110 = 730
        assertEquals(730, GameData.baseScore(1, 0, 1, 0, 0, false, 0, 0, false));
        // 勝利 +12000、コンボ50 → +100
        assertEquals(730 + 12000 + 100, GameData.baseScore(1, 0, 1, 0, 0, true, 50, 0, false));
        // 弾幕の見切り10 → +120
        assertEquals(730 + 120, GameData.baseScore(1, 0, 1, 0, 0, false, 0, 10, true));
        // 非弾幕では見切りは加算されない
        assertEquals(730, GameData.baseScore(1, 0, 1, 0, 0, false, 0, 10, false));
        // 倍率: 通常×1、極難×1.6、主の巡礼×2.2
        assertEquals(730, GameData.finalScore(730, 1.0, "standard", "normal"));
        assertEquals((int) Math.round(730 * 1.6), GameData.finalScore(730, 1.0, "standard", "hard"));
        assertEquals((int) Math.round(730 * 2.2), GameData.finalScore(730, 1.0, "bossrush", "normal"));
        // daily は standard 扱い
        assertEquals((int) Math.round(730 * 1.55), GameData.finalScore(730, 1.55, "daily", "normal"));
        assertEquals(8, GameData.RUN_MODES.size());
    }

    @Test
    void rarityEliteAndCurseMatchCoreTs() {
        // rollRarity: l=0 → 原作の基礎重み (normal 46 / mythic 0.875)
        assertEquals("normal", GameData.rollRarity(() -> 0.0, 0, 0, 1));
        assertEquals("mythic", GameData.rollRarity(() -> 0.9999, 0, 0, 1));
        // 運が高いと上位が出やすい (l=200: normal max(4, 46-110)=4)
        assertEquals("rare", GameData.rollRarity(() -> 0.5, 200, 0, 1));
        assertEquals(6, GameData.ELITE_PREFIX.size());
        assertEquals("狂乱の", GameData.elitePrefix(() -> 0.0).name());
        assertEquals("太古の", GameData.elitePrefix(() -> 0.9999).name());
        assertEquals(6, GameData.ELITE_TRAITS.size());
        assertNotNull(GameData.eliteTraitById("burst"));
        // 特性は60%で付く
        assertNull(GameData.rollEliteTrait(() -> 0.9));
        assertNotNull(GameData.rollEliteTrait(() -> 0.1));
        assertEquals(6, GameData.CURSED_MODS.size());
        // normal は呪われない、レア以上は12%
        assertFalse(GameData.rollCursed(() -> 0.0, "normal"));
        assertTrue(GameData.rollCursed(() -> 0.05, "rare"));
        assertFalse(GameData.rollCursed(() -> 0.5, "mythic"));
        assertEquals("maxHpPct", GameData.rollCursedMod(() -> 0.0).key());
        assertEquals(-18.0, GameData.rollCursedValue(() -> 0.0, GameData.CURSED_MODS.get(0)), 1e-9);
    }

    @Test
    void fiveDifficultiesMatchCoreTs() {
        assertEquals(5, GameData.DIFFICULTIES.size());
        assertEquals(1.0, GameData.difficultyById("normal").soulMul(), 1e-9);
        assertEquals(1.4, GameData.difficultyById("hard").hpMul(), 1e-9);
        assertEquals(1.9, GameData.difficultyById("hell").hpMul(), 1e-9);
        assertEquals(2.7, GameData.difficultyById("inferno").hpMul(), 1e-9);
        assertEquals(3.2, GameData.difficultyById("inferno").soulMul(), 1e-9);
        assertEquals(7.56, GameData.difficultyById("omega").soulMul(), 1e-9);
        assertEquals("normal", GameData.difficultyById("nope").id());
    }

    @Test
    void eightRunModesWithOriginalSoulRates() {
        assertEquals(8, GameData.RUN_MODES.size());
        assertEquals(1.0, GameData.modeSoulMult("standard"), 1e-9);
        assertEquals(2.0, GameData.modeSoulMult("bossrush"), 1e-9);
        assertEquals(1.6, GameData.modeSoulMult("endless"), 1e-9);
        assertEquals(3.0, GameData.modeSoulMult("covenant"), 1e-9);
        assertEquals(4.5, GameData.modeSoulMult("pinnacle"), 1e-9);
        assertEquals(7.56, GameData.modeSoulMult("omega"), 1e-9);
        assertEquals(1.0, GameData.modeSoulMult("nope"), 1e-9);
    }

    @Test
    void sevenDailyModifiersRotate() {
        assertEquals(7, GameData.DAILY_MODS.size());
        assertEquals(GameData.DAILY_MODS.get(0), GameData.dailyModForDay(0));
        assertEquals(GameData.DAILY_MODS.get(0), GameData.dailyModForDay(7));
        assertEquals("blood-moon", GameData.dailyModForDay(0).id());
        assertNotNull(GameData.dailyModById("drought"));
        assertTrue(GameData.dailyModById("drought").noFloorHeal());
    }

    @Test
    void wardenFloorsAreThreeAndEight() {
        assertTrue(GameData.isWardenFloor(3));
        assertTrue(GameData.isWardenFloor(8));
        assertTrue(GameData.isWardenFloor(13));
        assertTrue(GameData.isWardenFloor(18));
        assertFalse(GameData.isWardenFloor(5));
        assertFalse(GameData.isWardenFloor(10));
        assertFalse(GameData.isWardenFloor(2));
        assertFalse(GameData.isWardenFloor(26));
        assertEquals(6, GameData.WARDENS.size());
        assertEquals("灰燼の番人・ヴォスク", GameData.WARDENS.get(5).name());
    }

    @Test
    void sevenZonesCoverAllFloors() {
        assertEquals(7, GameData.ZONES.size());
        assertEquals("ash", GameData.zoneForFloor(1).id());
        assertEquals("ossuary", GameData.zoneForFloor(6).id());
        assertEquals("forge", GameData.zoneForFloor(11).id());
        assertEquals("glacier", GameData.zoneForFloor(16).id());
        assertEquals("voidheart", GameData.zoneForFloor(21).id());
        assertEquals("nihil", GameData.zoneForFloor(26).id());
        assertEquals("lower_abyss", GameData.zoneForFloor(30).id());
        assertEquals("lower_abyss", GameData.zoneForFloor(100).id());
        assertTrue(GameData.zoneForFloor(11).emberRain());
        assertTrue(GameData.zoneForFloor(21).voidPull() > 0);
        assertTrue(GameData.zoneForFloor(30).voidPull() > 0);
    }

    @Test
    void relicsWithMaxTwo() {
        assertEquals(26, GameData.RELICS.size());
        var roll = GameData.rollRelics(new java.util.Random(42), java.util.List.of(), 3);
        assertEquals(3, roll.size());
        var owned = java.util.List.of("soulfang", "mirror");
        var roll2 = GameData.rollRelics(new java.util.Random(42), owned, 3);
        for (var r : roll2) assertFalse(owned.contains(r.id()));
        assertTrue(GameData.relicFlags(java.util.List.of("soulfang", "coin")).contains("soulfang"));
    }

    @Test
    void passivesWithCursedLimit() {
        assertEquals(44, GameData.PASSIVES.size());
        var roll = GameData.rollPassives(new java.util.Random(7), java.util.List.of(), 3);
        assertEquals(3, roll.size());
        // 呪い所持時は呪いが出ない
        var owned = java.util.List.of("momentum");
        var roll2 = GameData.rollPassives(new java.util.Random(7), owned, 10);
        for (var p : roll2) {
            assertFalse(owned.contains(p.id()));
            assertNotEquals("cursed", p.grade());
        }
        assertEquals(25.0, GameData.passiveDamagePct(java.util.List.of("iron_oath")), 1e-9);
        assertEquals(-20.0, GameData.passiveMaxHpPct(java.util.List.of("undying")), 1e-9);
        assertTrue(GameData.passiveFlags(java.util.List.of("giant_slayer")).contains("giant_slayer"));
    }

    @Test
    void thirtyTwoAchievementsWithShardConversion() {
        assertEquals(32, GameData.ACHIEVEMENTS.size());
        assertNotNull(GameData.achievementById("pinnacle-god"));
        assertEquals(1, GameData.shardsForReward(25));
        assertEquals(60, GameData.shardsForReward(1500));
    }

    @Test
    void eightBossesWithFloors() {
        assertEquals(8, GameData.BOSSES.size());
        assertEquals(25, GameData.FINAL_FLOOR);
        assertEquals(26, GameData.TRUE_FLOOR);
        assertEquals(27, GameData.PINNACLE_FLOOR);
        assertEquals("ゴブリン王 グルザ", GameData.bossForFloor(5).name());
        assertEquals("超越者", GameData.bossForFloor(28).name());
        assertNull(GameData.bossForFloor(7));
    }

    @Test
    void trueAndPinnaclePhasesFollowHpRatio() {
        assertEquals(3, GameData.TRUE_PHASES.size());
        assertEquals("虚無の胎動", GameData.truePhaseFor(0.9).label());
        assertEquals("深淵の咆哮", GameData.truePhaseFor(0.5).label());
        assertEquals("終焉・万物の帰無", GameData.truePhaseFor(0.1).label());
        assertEquals(4, GameData.PINNACLE_PHASES.size());
        assertEquals("天墜の審判", GameData.pinnaclePhaseFor(0.9).label());
        assertEquals("次元断絶", GameData.pinnaclePhaseFor(0.6).label());
        assertEquals("終末の咆哮", GameData.pinnaclePhaseFor(0.4).label());
        assertEquals("原初崩壊・星辰帰無", GameData.pinnaclePhaseFor(0.1).label());
    }

    @Test
    void sevenRandomEventsWithKinds() {
        assertEquals(7, GameData.RANDOM_EVENTS.size());
        assertEquals("loot", GameData.randomEventById("treasure").kind());
        assertEquals("mist", GameData.randomEventById("curse_mist").fate());
        assertEquals("timewarp", GameData.randomEventById("timewarp").fate());
    }

    @Test
    void biomeEchoTableIsSound() {
        assertEquals(8, GameData.BIOME_ECHOS.size());
        var ids = new java.util.HashSet<String>();
        for (var e : GameData.BIOME_ECHOS) {
            assertTrue(ids.add(e.biome()), "重複したバイオーム: " + e.biome());
            assertTrue(e.biome().startsWith("minecraft:"), e.biome());
            assertFalse(e.name().isBlank() || e.desc().isBlank());
            assertFalse(e.mods().isEmpty());
        }
        assertEquals(15.0,
                GameData.biomeEchoById("minecraft:soul_sand_valley").mods().getOrDefault("energyPct", 0.0), 1e-9);
        assertEquals(-5.0,
                GameData.biomeEchoById("minecraft:deep_dark").mods().getOrDefault("damagePct", 0.0), 1e-9);
        assertNull(GameData.biomeEchoById("minecraft:plains"));
    }

    @Test
    void curseEaterNullifiesDrawbacksAndDoublesBonuses() {
        // curseEaterBonus* はベース式への加算分を返す (合計 = ベース + 補正)。
        // 鉄の律儀: 攻撃+25%/最大HP-12% → 合計は攻撃+50%/最大HP±0%
        assertEquals(25.0,
                GameData.curseEaterBonusDamage(java.util.List.of("iron_oath")), 1e-9);
        assertEquals(25.0 + 25.0,
                GameData.passiveDamagePct(java.util.List.of("iron_oath"))
                        + GameData.curseEaterBonusDamage(java.util.List.of("iron_oath")), 1e-9);
        assertEquals(12.0,
                GameData.curseEaterBonusMaxHp(java.util.List.of("iron_oath")), 1e-9);
        assertEquals(-12.0 + 12.0,
                GameData.passiveMaxHpPct(java.util.List.of("iron_oath"))
                        + GameData.curseEaterBonusMaxHp(java.util.List.of("iron_oath")), 1e-9);
        // 硝子の大砲: 攻撃+55%/最大HP-35%/軽減-10% → 合計は攻撃+110%/最大HP±0%/軽減±0%
        assertEquals(55.0,
                GameData.curseEaterBonusDamage(java.util.List.of("glass_cannon")), 1e-9);
        assertEquals(35.0,
                GameData.curseEaterBonusMaxHp(java.util.List.of("glass_cannon")), 1e-9);
        assertEquals(10.0,
                GameData.curseEaterBonusArmor(java.util.List.of("glass_cannon")), 1e-9);
        // 未取得なら補正0
        assertEquals(0.0, GameData.curseEaterBonusDamage(java.util.List.of()), 1e-9);
    }

    @Test
    void ascensionFloorIsAfterPinnacle() {
        assertEquals(28, GameData.ASCENSION_FLOOR);
        assertNotNull(GameData.bossForFloor(GameData.ASCENSION_FLOOR));
        assertEquals(8, GameData.BOSSES.size());
    }

    /** 属性10種・節目・職業適性の定義健全性 */
    @Test
    void eightAttributesWithMilestones() {
        assertEquals(10, GameData.ATTR_NAMES.length);
        assertEquals(10, GameData.ATTR_SHORT.length);
        assertEquals(10, GameData.ATTR_DESCS.length);
        assertEquals(10, GameData.ATTR_MILESTONES.size());
        for (var miles : GameData.ATTR_MILESTONES) {
            assertEquals(3, miles.size());
            assertEquals(10, miles.get(0).at());
            assertEquals(20, miles.get(1).at());
            assertEquals(30, miles.get(2).at());
            for (var m : miles) assertFalse(m.mods().isEmpty());
        }
        assertEquals(0, GameData.milestoneTier(0, 0));
        assertEquals(1, GameData.milestoneTier(0, 10));
        assertEquals(2, GameData.milestoneTier(0, 25));
        assertEquals(3, GameData.milestoneTier(0, 30));
        assertEquals(0, GameData.milestoneTier(-1, 99));
        // 各職業に適性があり範囲内
        for (GameData.HeroClass c : GameData.HeroClass.values()) {
            assertTrue(c.affinityAttr() >= 0 && c.affinityAttr() < 10);
        }
        assertEquals(0, GameData.HeroClass.byId("samurai").affinityAttr());
        assertEquals(5, GameData.HeroClass.byId("mage").affinityAttr());
    }

    @Test
    void extraContentIsSound() {
        assertEquals(31, GameData.EXTRA_BOON_MODS.size());
        assertEquals(24, GameData.EXTRA_PASSIVE_MODS.size());
        assertEquals(18, GameData.EXTRA_RELIC_MODS.size());
        assertEquals(6, GameData.ARMOR_SETS.size());
        var setIds = new java.util.HashSet<String>();
        for (var s : GameData.ARMOR_SETS) {
            assertTrue(setIds.add(s.id()));
            assertFalse(s.bonus2().isEmpty());
            assertFalse(s.bonus4().isEmpty());
        }
        // 追加恩寵・技能・遺物はすべて定義表に存在し、IDが一意
        var ids = new java.util.HashSet<String>();
        for (var b : Boons.BOON_POOL) assertTrue(ids.add("b:" + b.id()));
        for (var p : GameData.PASSIVES) assertTrue(ids.add("p:" + p.id()));
        for (var r : GameData.RELICS) assertTrue(ids.add("r:" + r.id()));
        for (String id : GameData.EXTRA_BOON_MODS.keySet()) assertTrue(ids.contains("b:" + id));
        for (String id : GameData.EXTRA_PASSIVE_MODS.keySet()) assertTrue(ids.contains("p:" + id));
        for (String id : GameData.EXTRA_RELIC_MODS.keySet()) assertTrue(ids.contains("r:" + id));
        // 新武器は奥義名・説明・ボーナスを持つ
        for (String id : new String[]{"rune_blade", "blood_axe", "storm_spear", "void_staff",
                "moon_bow", "fang_dagger", "quake_hammer", "frost_katana", "soul_scythe",
                "titan_gauntlet", "eclipse_chakram", "chaos_tome", "rod", "wand",
                "greatsword", "twin_dagger", "war_horn",
                "wind_blade", "ember_flail", "astral_lance", "tide_trident"}) {
            var w = GameData.WeaponStyle.byId(id);
            assertEquals(id, w.id);
            assertFalse(w.artName.isBlank());
            assertNotNull(GameData.WEAPON_BONUS.get(id));
        }
        // 新職業の必殺技名
        assertEquals("血爆", GameData.HeroClass.byId("berserker").skillName());
        assertEquals("禅定結界", GameData.HeroClass.byId("monk").skillName());
        // 装備スキル6+6
        assertEquals(12, GameData.WEAPON_SKILLS.size());
        assertEquals(15, GameData.ARMOR_SKILLS.size());
        assertNotNull(GameData.gearSkillById("w_soul"));
        assertNotNull(GameData.gearSkillById("b_dash"));
        // 全武器に能力・全通常モブに能力・ダンジョン特性8種
        assertEquals(33, GameData.WEAPON_ABILITIES.size());
        for (GameData.WeaponStyle w : GameData.WeaponStyle.values()) {
            assertNotNull(GameData.WEAPON_ABILITIES.get(w.id), "ability missing: " + w.id);
        }
        assertEquals(11, GameData.DUNGEON_TRAITS.size());
        assertEquals(18, GameData.MOB_ABILITIES.size());
        assertEquals("THORNS", GameData.MOB_ABILITIES.get("golem"));
        assertEquals("SPLIT", GameData.MOB_ABILITIES.get("slime"));
        assertEquals("WARP", GameData.MOB_ABILITIES.get("phantom"));
        // 追加遺物の flag null でも relicFlags が壊れない
        assertTrue(GameData.relicFlags(java.util.List.of("r_blade", "soulfang")).contains("soulfang"));
    }

    @Test
    void boonPoolCoversOriginal39PlusLegacy2() {
        var pool = Boons.BOON_POOL;
        assertEquals(72, pool.length);
        var ids = new java.util.HashSet<String>();
        for (var b : pool) {
            assertFalse(b.desc().isBlank() || b.name().isBlank());
            assertTrue(ids.add(b.id()), "重複した祝福ID: " + b.id());
            assertNotNull(Boons.boonById(b.id()));
        }
        // 原作 core.ts BOONS の全ID (b_ 接頭語を除去したもの) が含まれること
        var original = java.util.List.of(
                "dmg1", "dmg2", "as", "crit", "critdmg", "hp", "armor", "speed", "area",
                "life", "proj", "pierce", "explode", "freeze", "thorns", "energy", "xp",
                "orbit", "aura", "chain", "missile", "nova", "familiar", "execute", "blood",
                "magnet", "quake", "bladespin", "vengeance", "thunderfury", "echo", "greed",
                "warden", "harvest", "overflow", "curse_eater", "lastlaugh", "perfect",
                "absolute_domain");
        assertEquals(39, original.size());
        for (String id : original) {
            assertTrue(ids.contains(id), "原作祝福が未移植: " + id);
        }
    }

    @Test
    void rollBoonsPicksThreeUniqueWithGradeWeights() {
        // 固定乱数 (常に0.0): 重みの先頭から選ばれる。3択・重複なしを確認
        var pick = Boons.rollBoons(() -> 0.0, 3);
        assertEquals(3, pick.size());
        var ids = new java.util.HashSet<String>();
        for (var b : pick) {
            assertTrue(ids.add(b.id()), "祝福3択に重複: " + b.id());
            assertNotNull(Boons.boonById(b.id()));
        }
        // 等級の重みは原作通り
        assertEquals(4.2, Boons.weightOf("common"), 1e-9);
        assertEquals(2.4, Boons.weightOf("rare"), 1e-9);
        assertEquals(1.1, Boons.weightOf("epic"), 1e-9);
    }

    @Test
    void weaponClassBonusMatchesCoreTs() {
        assertEquals(33, GameData.WEAPON_BONUS.size());
        assertEquals(18.0, GameData.weaponBonus("axe").damagePct(), 1e-9);
        assertEquals(-12.0, GameData.weaponBonus("axe").attackSpeedPct(), 1e-9);
        assertEquals(28.0, GameData.weaponBonus("spear").areaPct(), 1e-9);
        assertEquals(35.0, GameData.weaponBonus("staff").energyPct(), 1e-9);
        assertEquals(1, GameData.weaponBonus("bow").projectiles());
        assertEquals(12.0, GameData.weaponBonus("dagger").critPct(), 1e-9);
        assertEquals(30.0, GameData.weaponBonus("dagger").critDmgPct(), 1e-9);
        assertEquals(22.0, GameData.weaponBonus("hammer").explodePct(), 1e-9);
        assertEquals(0.0, GameData.weaponBonus("nope").damagePct(), 1e-9);
    }

    @Test
    void weaponNamesMatchCoreTs() {
        assertEquals(16, GameData.PREFIXES.size());
        assertEquals(16, GameData.SUFFIXES.size());
        assertEquals(12, GameData.LEGEND_NAMES.size());
        assertEquals(10, GameData.MYTHIC_NAMES.size());
        assertEquals("焔の", GameData.prefixFor(() -> 0.0));
        assertEquals("エクスカリバー", GameData.legendFor(() -> 0.0));
        assertEquals("虚無神の残光", GameData.mythicFor(() -> 0.0));
    }

    @Test
    void affixRollMatchesGenItem() {
        assertEquals(22, GameData.WEAPON_AFFIXES.size());
        assertArrayEquals(new int[]{0, 1}, GameData.affixCountRange("normal"));
        assertArrayEquals(new int[]{1, 2}, GameData.affixCountRange("magic"));
        assertArrayEquals(new int[]{3, 4}, GameData.affixCountRange("rare"));
        assertArrayEquals(new int[]{4, 5}, GameData.affixCountRange("legendary"));
        assertArrayEquals(new int[]{5, 6}, GameData.affixCountRange("mythic"));
        // 件数・重複なし・範囲内
        var rolled = GameData.rollAffixes(() -> 0.5, "rare", 3);
        assertEquals(3, rolled.size());
        var keys = new java.util.HashSet<String>();
        for (var af : rolled) {
            assertTrue(keys.add(af.key()), "接辞に重複: " + af.key());
            boolean known = false;
            for (var def : GameData.WEAPON_AFFIXES) {
                if (def.key().equals(af.key())) {
                    known = true;
                    assertTrue(af.value() >= def.min() && af.value() <= def.max(),
                            af.key() + "=" + af.value());
                }
            }
            assertTrue(known, "未知の接辞: " + af.key());
        }
    }
}
