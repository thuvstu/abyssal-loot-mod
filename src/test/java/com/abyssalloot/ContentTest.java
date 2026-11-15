package com.abyssalloot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

/** 追加コンテンツ (abyssalloot_content.json) の定義の整合 */
class ContentTest {

    @Test
    void 追加アイテムは100件() {
        int n = Content.ARMOR_SETS.size() * 4 + Content.TROPHIES.size()
                + Content.ZONE_MATERIALS.size() + Content.CONSUMABLES.size();
        assertEquals(100, n);
    }

    @Test
    void IDは重複しない() {
        var seen = new HashSet<String>();
        for (var t : Content.TROPHIES) assertTrue(seen.add(t.id()), t.id());
        for (var m : Content.ZONE_MATERIALS) assertTrue(seen.add(m.id()), m.id());
        for (var c : Content.CONSUMABLES) assertTrue(seen.add(c.id()), c.id());
    }

    @Test
    void 追加セットは防具セット表に連結される() {
        assertEquals(8 + Content.ARMOR_SETS.size(), GameData.ARMOR_SETS.size());
        for (var s : Content.ARMOR_SETS) {
            var set = GameData.armorSetById(s.id());
            assertNotNull(set, s.id());
            assertFalse(set.bonus2().isEmpty(), s.id() + " の 2 部位効果が空");
            assertFalse(set.bonus4().isEmpty(), s.id() + " の 4 部位効果が空");
        }
    }

    @Test
    void 全区域に素材がある() {
        for (GameData.Zone z : GameData.ZONES) {
            var pool = Content.ZONE_DROPS.get(z.id());
            assertNotNull(pool, "区域 " + z.id() + " に素材が無い");
            assertFalse(pool.isEmpty(), z.id());
        }
        // 素材側の区域 ID の打ち間違いも検出する
        var zoneIds = new HashSet<String>();
        for (GameData.Zone z : GameData.ZONES) zoneIds.add(z.id());
        for (String zone : Content.ZONE_DROPS.keySet()) {
            assertTrue(zoneIds.contains(zone), "存在しない区域: " + zone);
        }
    }

    @Test
    void 消耗品の効果はすべて解析できる() {
        for (var c : Content.CONSUMABLES) {
            assertTrue(Content.isMix(c.kind()), c.id());
            assertFalse(Content.parseMix(c.kind()).isEmpty(), c.id());
            assertFalse(Content.describeMix(c.kind()).isBlank(), c.id());
        }
    }

    @Test
    void mixの解析と説明() {
        var parts = Content.parseMix("mix:heal=20;gauge=100;fx=speed,60,1");
        assertEquals(3, parts.size());
        assertEquals("heal", parts.get(0).type());
        assertEquals(20, parts.get(0).value());
        assertEquals("speed", parts.get(2).effect());
        assertEquals(60, parts.get(2).seconds());
        assertEquals(1, parts.get(2).amplifier());
        assertEquals("HPを20%回復 / 魂ゲージ全満 / 移動速度上昇II 60秒",
                Content.describeMix("mix:heal=20;gauge=100;fx=speed,60,1"));
    }

    @Test
    void 不正なmixは例外() {
        assertThrows(IllegalArgumentException.class, () -> Content.parseMix("heal_30"));
        assertThrows(IllegalArgumentException.class, () -> Content.parseMix("mix:fx=flying,10,0"));
        assertThrows(IllegalArgumentException.class, () -> Content.parseMix("mix:fx=speed,10"));
        assertThrows(IllegalArgumentException.class, () -> Content.parseMix("mix:boom=3"));
    }
}
