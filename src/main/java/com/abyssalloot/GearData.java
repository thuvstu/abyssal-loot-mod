package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 装備の CustomData 読み書き・威力・lore (RunManager から分離)。
 */
public final class GearData {
    private GearData() {}


    /** 鍛造レベル (武器CustomData "abyss_forge") */
    public static int forgeLv(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (ModItems.styleOf(stack.getItem()) == null) return 0;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return Math.max(0, Math.min(5, data.copyTag().getInt("abyss_forge").orElse(0)));
    }


    public static void setForgeLv(net.minecraft.world.item.ItemStack stack, int lv) {
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putInt("abyss_forge", Math.max(0, Math.min(5, lv)));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
    }


    /* ---------------- 武器特性・レアリティ (原作WEAPON_TRAITS/RARITY準拠) ---------------- */

    public record TraitDef(String id, String name) {}


    public static final TraitDef[] TRAITS = {
            new TraitDef("sharp", "鋭刃"), new TraitDef("vampiric", "吸血鬼"),
            new TraitDef("explosive", "爆砕"), new TraitDef("terminator", "断末魔"),
            new TraitDef("chain_l", "連鎖雷"), new TraitDef("infernus", "業火"),
            new TraitDef("venomous", "猛毒"), new TraitDef("rend", "裂傷"),
            new TraitDef("frenzy", "狂戦士"), new TraitDef("fortify", "不壊"),
    };


    public static String traitName(String id) {
        for (var t : TRAITS) {
            if (t.id().equals(id)) return t.name();
        }
        return null;
    }


    public static String traitOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String id = data.copyTag().getString("abyss_trait").orElse("");
        return id.isEmpty() ? null : id;
    }


    public static String rarityOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "normal";
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String r = data.copyTag().getString("abyss_rarity").orElse("");
        return r.isEmpty() ? "normal" : r;
    }


    public static double rarityMult(String rarity) {
        return switch (rarity) {
            case "mythic" -> 1.7;
            case "legendary" -> 1.45;
            case "rare" -> 1.25;
            case "magic" -> 1.1;
            default -> 1.0;
        };
    }


    private static String rarityLabel(String rarity) {
        return switch (rarity) {
            case "mythic" -> "§d神話";
            case "legendary" -> "§6レジェンド";
            case "rare" -> "§eレア";
            case "magic" -> "§b魔法";
            default -> "§7通常";
        };
    }


    public static double affixOf(net.minecraft.world.item.ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return 0;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String raw = data.copyTag().getString("abyss_affixes").orElse("");
        if (raw.isEmpty()) return 0;
        for (String e : raw.split(";")) {
            String[] kv = e.split(":");
            if (kv.length == 2 && kv[0].equals(key)) {
                try {
                    return Double.parseDouble(kv[1]);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }


    public static String rarityColor(String rarity) {
        return switch (rarity) {
            case "mythic" -> "§d";
            case "legendary" -> "§6";
            case "rare" -> "§e";
            case "magic" -> "§b";
            default -> "§7";
        };
    }


    /** 原作 STAT_META.worth (威力計算用)。鍛造費・商店価格の基礎値 */
    private static final java.util.Map<String, Double> WORTH = java.util.Map.ofEntries(
            java.util.Map.entry("damageFlat", 1.0), java.util.Map.entry("damagePct", 3.4),
            java.util.Map.entry("attackSpeedPct", 3.2), java.util.Map.entry("moveSpeedPct", 2.4),
            java.util.Map.entry("critPct", 2.8), java.util.Map.entry("critDmgPct", 1.5),
            java.util.Map.entry("areaPct", 1.8), java.util.Map.entry("projectiles", 26.0),
            java.util.Map.entry("pierce", 14.0), java.util.Map.entry("lifestealPct", 6.0),
            java.util.Map.entry("armorPct", 5.0), java.util.Map.entry("regenFlat", 16.0),
            java.util.Map.entry("luckFlat", 1.1), java.util.Map.entry("energyPct", 1.6),
            java.util.Map.entry("maxHpFlat", 0.1), java.util.Map.entry("maxHpPct", 2.6),
            java.util.Map.entry("chain", 20.0), java.util.Map.entry("explodePct", 3.0),
            java.util.Map.entry("freezePct", 2.6));


    /** 武器の接辞マップ (key→value)。原作 mods に相当 */
    public static java.util.Map<String, Double> affixMap(net.minecraft.world.item.ItemStack stack) {
        var out = new java.util.LinkedHashMap<String, Double>();
        if (stack == null || stack.isEmpty()) return out;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String raw = data.copyTag().getString("abyss_affixes").orElse("");
        if (raw.isEmpty()) return out;
        for (String e : raw.split(";")) {
            String[] kv = e.split(":");
            if (kv.length == 2) {
                try {
                    out.put(kv[0], Double.parseDouble(kv[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }


    /**
     * 武器の威力 (原作 power 式の MC 版)。
     * Σ|接辞|×worth × 希少度倍率 × (1+鍛造Lv×0.08)。鍛造費・商店価格に使用。
     */
    public static int powerOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (ModItems.styleOf(stack.getItem()) == null && ModItems.slotOf(stack.getItem()) == null) return 0;
        double p = 0;
        for (var e : affixMap(stack).entrySet()) {
            p += Math.abs(e.getValue()) * WORTH.getOrDefault(e.getKey(), 1.0);
        }
        String rarity = rarityOf(stack);
        p *= rarityMult(rarity) * (1 + forgeLv(stack) * 0.08);
        if (traitOf(stack) != null) p *= 1.1;
        if (curseOf(stack) != null) p *= 1.08;
        return Math.max(1, (int) Math.round(p));
    }


    /** 接辞キー -> 日本語表示 (原作 STAT_META.label) */
    private static final java.util.Map<String, String> AFFIX_LABEL = java.util.Map.ofEntries(
            java.util.Map.entry("damageFlat", "攻撃力"), java.util.Map.entry("damagePct", "攻撃力"),
            java.util.Map.entry("maxHpFlat", "最大HP"), java.util.Map.entry("maxHpPct", "最大HP"),
            java.util.Map.entry("attackSpeedPct", "攻撃速度"), java.util.Map.entry("critPct", "会心率"),
            java.util.Map.entry("critDmgPct", "会心威力"), java.util.Map.entry("moveSpeedPct", "移動速度"),
            java.util.Map.entry("armorPct", "軽減"), java.util.Map.entry("areaPct", "範囲"),
            java.util.Map.entry("lifestealPct", "吸収"), java.util.Map.entry("regenFlat", "再生"),
            java.util.Map.entry("luckFlat", "運"), java.util.Map.entry("energyPct", "魂"),
            java.util.Map.entry("pierce", "貫通"), java.util.Map.entry("projectiles", "追加弾"),
            java.util.Map.entry("chain", "連鎖"), java.util.Map.entry("explodePct", "爆裂"),
            java.util.Map.entry("freezePct", "凍結"));


    /** 武器・防具のツールチップ追記 (接辞・特性・奥義・使い方) */
    public static void appendGearLore(net.minecraft.world.item.ItemStack stack,
                                      java.util.function.Consumer<Component> out, boolean isWeapon) {
        var style = ModItems.styleOf(stack.getItem());
        if (isWeapon && style != null) {
            out.accept(Component.literal("§d奥義「" + style.artName + "」: " + style.artDesc));
            out.accept(Component.literal("§7Rキーで発動 (魂ゲージ100で可)"));
        } else if (!isWeapon) {
            out.accept(Component.literal("§7右クリックで装備。接辞は武器と合算"));
        }
        String trait = traitOf(stack);
        if (trait != null) {
            out.accept(Component.literal("§6特性〔" + traitName(trait) + "〕"));
        }
        for (var e : affixMap(stack).entrySet()) {
            String label = AFFIX_LABEL.getOrDefault(e.getKey(), e.getKey());
            double v = e.getValue();
            String num = (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            out.accept(Component.literal("§b+" + num + " " + label));
        }
        String curse = curseOf(stack);
        if (curse != null) {
            out.accept(Component.literal("§5呪い: " + AFFIX_LABEL.getOrDefault(curse, curse)
                    + " に弱点 (恩寵「呪喰らい」で無効化)"));
        }
        int forge = forgeLv(stack);
        if (forge > 0) out.accept(Component.literal("§b鍛造+" + forge));
        out.accept(Component.literal("§8威力 " + powerOf(stack)));
        if (isWeapon) {
            out.accept(Component.literal("§8/abyss compare・/abyss merge で厳選・合成"));
        }
    }


    /** 武器の呪い種別 (なければ null)。原作 CURSED_MODS のキー */
    public static String curseOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String id = data.copyTag().getString("abyss_curse").orElse("");
        return id.isEmpty() ? null : id;
    }


    public static double curseValueOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return data.copyTag().getDouble("abyss_curse_val").orElse(0.0);
    }


    /**
     * 呪い武器の攻撃倍率。弱点と引き換えに +24%。
     * 恩寵「呪喰らい」所持時は弱点を無効化し効果2倍 (+48%)。
     */
    public static double curseMultOf(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (curseOf(stack) == null) return 1.0;
        return RunManager.boonLv(player, "curse_eater") > 0 ? 1.48 : 1.24;
    }


    /** 呪いの弱点が有効か (呪喰らいで無効化される) */
    public static boolean curseActive(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        return curseOf(stack) != null && RunManager.boonLv(player, "curse_eater") == 0;
    }


    /** 装備比較GUI用: 指定武器の性能テキスト。空なら (空) 1行 */
    public static java.util.List<String> compareLines(ServerPlayer player,
                                                      net.minecraft.world.item.ItemStack stack) {
        var style = ModItems.styleOf(stack.getItem());
        boolean isArmor = ModItems.slotOf(stack.getItem()) != null;
        if (style == null && !isArmor) return java.util.List.of("(空)");
        String trait = traitOf(stack);
        String curse = curseOf(stack);
        int affixes = 0;
        for (var ak : GameData.WEAPON_AFFIXES) {
            if (affixOf(stack, ak.key()) != 0) affixes++;
        }
        var out = new java.util.ArrayList<String>();
        out.add(stack.getHoverName().getString());
        if (isArmor) {
            out.add("部位: " + ModItems.slotOf(stack.getItem()) + " 威力: " + powerOf(stack));
        } else {
            out.add(String.format("攻撃 %.1f (奥義「%s」)", StatCalc.attackDamageWith(player, stack), style.artName));
        }
        out.add("希少:" + rarityOf(stack) + " 特性:" + (trait != null ? traitName(trait) : "なし"));
        out.add("鍛造+" + forgeLv(stack) + " 接辞:" + affixes
                + (curse != null ? " 呪い:" + curse + curseValueOf(stack) : ""));
        return out;
    }
}
