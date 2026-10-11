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
}
