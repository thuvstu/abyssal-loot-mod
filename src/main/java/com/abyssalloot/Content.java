package com.abyssalloot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 追加コンテンツの定義 ({@code abyssalloot_content.json}) を読む.
 * <p>
 * 同じ JSON を tools/art/content.py も読み、アイコン・レシピ・翻訳を生成する.
 * アイテムを増やすときは JSON に 1 件足すだけでよい (登録は ModItems、効果・ドロップは ContentHooks).
 * <p>
 * このクラスは Minecraft のクラスに依存しない (GameData と同じく単体テストから読める).
 */
public final class Content {
    private Content() {}

    /** 防具セット (GameData.ARMOR_SETS に連結される) */
    public record SetDef(String id, String name, int body, int trim,
                         Map<String, Double> bonus2, Map<String, Double> bonus4) {}

    /** 魔物素材: mob = エンティティ ID (abyss_slime など) */
    public record TrophyDef(String id, String mob, String name, String rarity) {}

    /** 区域素材: zone = GameData.Zone の id */
    public record ZoneMaterialDef(String id, String zone, String name, String rarity) {}

    /** 消耗品: kind = 効果の記述 (mix:heal=20;fx=speed,60,1 など) */
    public record ConsumableDef(String id, String name, String kind, String rarity) {}

    public static final List<SetDef> ARMOR_SETS;
    public static final List<TrophyDef> TROPHIES;
    public static final List<ZoneMaterialDef> ZONE_MATERIALS;
    public static final List<ConsumableDef> CONSUMABLES;
    /** 区域 id -> その区域で落ちる素材のアイテム ID (新素材 + これまで未ドロップだった既存素材) */
    public static final Map<String, List<String>> ZONE_DROPS;
    /** エンティティ ID -> 魔物素材のアイテム ID */
    public static final Map<String, String> TROPHY_BY_MOB;

    static {
        JsonObject root = load();
        var sets = new ArrayList<SetDef>();
        for (var e : array(root, "armor_sets")) {
            JsonObject o = e.getAsJsonObject();
            sets.add(new SetDef(str(o, "id"), str(o, "name"), color(o, "body"), color(o, "trim"),
                    doubles(o.getAsJsonObject("bonus2")), doubles(o.getAsJsonObject("bonus4"))));
        }
        var trophies = new ArrayList<TrophyDef>();
        var byMob = new LinkedHashMap<String, String>();
        for (var e : array(root, "trophies")) {
            JsonObject o = e.getAsJsonObject();
            var t = new TrophyDef(str(o, "id"), str(o, "mob"), str(o, "name"), str(o, "rarity"));
            trophies.add(t);
            byMob.put(t.mob(), t.id());
        }
        var mats = new ArrayList<ZoneMaterialDef>();
        var drops = new LinkedHashMap<String, List<String>>();
        for (var e : array(root, "zone_materials")) {
            JsonObject o = e.getAsJsonObject();
            var m = new ZoneMaterialDef(str(o, "id"), str(o, "zone"), str(o, "name"), str(o, "rarity"));
            mats.add(m);
            drops.computeIfAbsent(m.zone(), k -> new ArrayList<>()).add(m.id());
        }
        JsonObject existing = root.getAsJsonObject("zone_existing_drops");
        if (existing != null) {
            for (var en : existing.entrySet()) {
                var list = drops.computeIfAbsent(en.getKey(), k -> new ArrayList<>());
                for (var id : en.getValue().getAsJsonArray()) list.add(id.getAsString());
            }
        }
        var cons = new ArrayList<ConsumableDef>();
        for (var e : array(root, "consumables")) {
            JsonObject o = e.getAsJsonObject();
            cons.add(new ConsumableDef(str(o, "id"), str(o, "name"), str(o, "kind"), str(o, "rarity")));
        }
        ARMOR_SETS = List.copyOf(sets);
        TROPHIES = List.copyOf(trophies);
        ZONE_MATERIALS = List.copyOf(mats);
        CONSUMABLES = List.copyOf(cons);
        var frozen = new LinkedHashMap<String, List<String>>();
        drops.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        ZONE_DROPS = java.util.Collections.unmodifiableMap(frozen);
        TROPHY_BY_MOB = java.util.Collections.unmodifiableMap(byMob);
    }

    private static JsonObject load() {
        try (var in = Content.class.getResourceAsStream("/abyssalloot_content.json")) {
            if (in == null) throw new IllegalStateException("abyssalloot_content.json が見つからない");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("abyssalloot_content.json を読めない", e);
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.get(key).getAsString();
    }

    private static int color(JsonObject o, String key) {
        return Integer.parseInt(str(o, key), 16);
    }

    private static Map<String, Double> doubles(JsonObject o) {
        var map = new LinkedHashMap<String, Double>();
        if (o != null) o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsDouble()));
        return java.util.Collections.unmodifiableMap(map);
    }

    // ------------------------------------------------------------------ 消耗品の効果記述 (mix:)

    /** mix の 1 要素。type = heal / gauge / fx */
    public record MixPart(String type, String effect, int value, int seconds, int amplifier) {}

    public static boolean isMix(String kind) {
        return kind != null && kind.startsWith("mix:");
    }

    /**
     * "mix:heal=20;gauge=10;fx=speed,60,1" を解析する.
     * heal / gauge は %、fx は 効果名,秒,強度 (0 始まり). 書式が不正なら例外 (起動時に気付けるようにする).
     */
    public static List<MixPart> parseMix(String kind) {
        if (!isMix(kind)) throw new IllegalArgumentException("mix: で始まらない: " + kind);
        var parts = new ArrayList<MixPart>();
        for (String raw : kind.substring(4).split(";")) {
            String[] kv = raw.trim().split("=", 2);
            if (kv.length != 2) throw new IllegalArgumentException("mix の要素が不正: " + raw);
            switch (kv[0]) {
                case "heal", "gauge" -> parts.add(new MixPart(kv[0], "", Integer.parseInt(kv[1]), 0, 0));
                case "fx" -> {
                    String[] a = kv[1].split(",");
                    if (a.length != 3) throw new IllegalArgumentException("fx は 効果名,秒,強度: " + raw);
                    if (!EFFECT_NAMES.containsKey(a[0])) {
                        throw new IllegalArgumentException("未対応の効果: " + a[0]);
                    }
                    parts.add(new MixPart("fx", a[0], 0, Integer.parseInt(a[1]), Integer.parseInt(a[2])));
                }
                default -> throw new IllegalArgumentException("mix の種類が不正: " + kv[0]);
            }
        }
        if (parts.isEmpty()) throw new IllegalArgumentException("mix が空: " + kind);
        return parts;
    }

    /** 効果名 -> 表示名 (ContentHooks の効果対応表と同じキー) */
    public static final Map<String, String> EFFECT_NAMES = Map.ofEntries(
            Map.entry("strength", "攻撃力上昇"), Map.entry("speed", "移動速度上昇"),
            Map.entry("resistance", "ダメージ耐性"), Map.entry("regeneration", "再生"),
            Map.entry("absorption", "守護"), Map.entry("fire_resistance", "火炎耐性"),
            Map.entry("haste", "採掘・攻撃速度上昇"), Map.entry("jump_boost", "跳躍力上昇"),
            Map.entry("night_vision", "暗視"), Map.entry("luck", "幸運"),
            Map.entry("slow_falling", "落下低速"), Map.entry("water_breathing", "水中呼吸"),
            Map.entry("invisibility", "透明化"));

    private static final String[] ROMAN = {"", "II", "III", "IV", "V"};

    /** ツールチップ用の説明 ("HPを20%回復 / 跳躍力上昇II 60秒") */
    public static String describeMix(String kind) {
        var out = new ArrayList<String>();
        for (MixPart p : parseMix(kind)) {
            switch (p.type()) {
                case "heal" -> out.add(p.value() >= 100 ? "HPを全回復" : "HPを" + p.value() + "%回復");
                case "gauge" -> out.add(p.value() >= 100 ? "魂ゲージ全満" : "魂ゲージ+" + p.value());
                default -> out.add(EFFECT_NAMES.get(p.effect())
                        + ROMAN[Math.min(ROMAN.length - 1, Math.max(0, p.amplifier()))] + " " + p.seconds() + "秒");
            }
        }
        return String.join(" / ", out);
    }
}
