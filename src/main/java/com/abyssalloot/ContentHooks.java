package com.abyssalloot;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 追加コンテンツ (Content) をゲームへ繋ぐ処理: 魔物素材・区域素材のドロップと、mix: 消耗品の効果.
 */
public final class ContentHooks {
    private ContentHooks() {}

    /** 魔物素材の確率: 通常 / 精鋭 (層主・番人は確定) */
    static final float TROPHY_NORMAL = 0.10f;
    static final float TROPHY_ELITE = 0.35f;
    /** 区域素材の確率: 通常 / 精鋭 (層主は 2 個確定、番人は 1 個確定) */
    static final float ZONE_NORMAL = 0.07f;
    static final float ZONE_ELITE = 0.30f;

    private static final Map<String, Holder<MobEffect>> EFFECTS = Map.ofEntries(
            Map.entry("strength", MobEffects.STRENGTH), Map.entry("speed", MobEffects.SPEED),
            Map.entry("resistance", MobEffects.RESISTANCE), Map.entry("regeneration", MobEffects.REGENERATION),
            Map.entry("absorption", MobEffects.ABSORPTION), Map.entry("fire_resistance", MobEffects.FIRE_RESISTANCE),
            Map.entry("haste", MobEffects.HASTE), Map.entry("jump_boost", MobEffects.JUMP_BOOST),
            Map.entry("night_vision", MobEffects.NIGHT_VISION), Map.entry("luck", MobEffects.LUCK),
            Map.entry("slow_falling", MobEffects.SLOW_FALLING), Map.entry("water_breathing", MobEffects.WATER_BREATHING),
            Map.entry("invisibility", MobEffects.INVISIBILITY));

    static {
        // 表示名の表と効果の表がずれていたら起動時に気付く
        if (!EFFECTS.keySet().equals(Content.EFFECT_NAMES.keySet())) {
            throw new IllegalStateException("Content.EFFECT_NAMES と ContentHooks.EFFECTS のキーが一致しない");
        }
    }

    /** mix: 消耗品の効果を適用する */
    public static boolean applyMix(ServerPlayer player, String kind) {
        for (Content.MixPart p : Content.parseMix(kind)) {
            switch (p.type()) {
                case "heal" -> RunManager.heal(player, player.getMaxHealth() * p.value() / 100.0);
                case "gauge" -> RunManager.addGauge(player, p.value());
                default -> player.addEffect(new MobEffectInstance(EFFECTS.get(p.effect()),
                        p.seconds() * 20, p.amplifier()));
            }
        }
        return true;
    }

    /** 倒した敵の種類に対応する魔物素材 (無ければ EMPTY) */
    public static ItemStack trophyOf(Entity dead) {
        String mob = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).getPath();
        String id = Content.TROPHY_BY_MOB.get(mob);
        if (id == null) return ItemStack.EMPTY;
        var item = ModItems.CONTENT_ITEMS.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    /** 区域の素材を 1 個抽選 (区域に素材が無ければ EMPTY) */
    public static ItemStack zoneMaterial(String zoneId, RandomSource rand) {
        List<String> pool = Content.ZONE_DROPS.get(zoneId);
        if (pool == null || pool.isEmpty()) return ItemStack.EMPTY;
        var item = ModItems.byId(pool.get(rand.nextInt(pool.size())));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * 撃破時のドロップ: その敵の魔物素材と、現在の区域の素材.
     *
     * @param tier 0=通常 / 1=精鋭 / 2=番人 / 3=層主
     */
    public static void rollKillDrops(ServerPlayer player, Entity dead, int tier, RandomSource rand) {
        float trophyChance = tier >= 2 ? 1f : tier == 1 ? TROPHY_ELITE : TROPHY_NORMAL;
        if (rand.nextFloat() < trophyChance) {
            ItemStack trophy = trophyOf(dead);
            if (!trophy.isEmpty()) LootHandler.giveOrDrop(player, trophy);
        }
        int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
        String zone = GameData.zoneForFloor(floor).id();
        int count = tier == 3 ? 2 : tier == 2 ? 1
                : rand.nextFloat() < (tier == 1 ? ZONE_ELITE : ZONE_NORMAL) ? 1 : 0;
        for (int i = 0; i < count; i++) {
            ItemStack mat = zoneMaterial(zone, rand);
            if (!mat.isEmpty()) LootHandler.giveOrDrop(player, mat);
        }
    }
}
