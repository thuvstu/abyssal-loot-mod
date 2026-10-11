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
        // samurai: hp 130+15/lv, damage 20*(1+0.17*(lv-1))
        var samurai = GameData.HeroClass.byId("samurai");
        assertEquals(130.0, GameData.heroMaxHp(samurai, 1), 1e-9);
        assertEquals(145.0, GameData.heroMaxHp(samurai, 2), 1e-9);
        assertEquals(20.0, GameData.heroDamage(samurai, 1), 1e-9);
        assertEquals(20.0 * 1.17, GameData.heroDamage(samurai, 2), 1e-9);
    }

    @Test
    void classSkillNamesExist() {
        for (GameData.HeroClass c : GameData.HeroClass.values()) {
            assertNotNull(c.skillName());
            assertFalse(c.skillName().isBlank());
        }
        assertEquals(5, GameData.HeroClass.values().length);
    }

    @Test
    void twelveWeaponArtsExist() {
        assertEquals(12, GameData.WeaponStyle.values().length);
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
        assertEquals(1 + 10 * 0.22, GameData.floorHpScale(10), 1e-9);
        assertEquals(1 + 10 * 0.08, GameData.floorDmgScale(10), 1e-9);
    }

    @Test
    void nineFloorMutationsWithUniqueIds() {
        assertEquals(9, GameData.MUTATIONS.size());
        var ids = new java.util.HashSet<String>();
        for (var m : GameData.MUTATIONS) ids.add(m.id());
        assertEquals(9, ids.size());
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
        // 一様乱数 0.5 は index = floor(0.5*9) = 4 (防衛線)
        assertEquals("fortress", GameData.rollMutation(() -> 0.5, 3).id());
    }
}
