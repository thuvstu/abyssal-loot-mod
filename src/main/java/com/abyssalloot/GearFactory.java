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
 * 武器・防具の生成 (RunManager から分離)。
 */
public final class GearFactory {
    private GearFactory() {}



















    /** ランダム武器生成 (レアリティ抽選+特性付与)。運・層・ボーナスなしの簡易版 */
    public static net.minecraft.world.item.ItemStack makeWeapon(
            GameData.WeaponStyle style, net.minecraft.util.RandomSource rand) {
        return makeWeapon(style, rand, 0, 1, 0);
    }


    /**
     * ランダム武器生成 (原作 genItem の MC 対応版)。
     * 希少度は原作 rollRarity (運・層・ボーナスで上位が出やすい)。レア以上は12%で呪い。
     * 接辞は希少度ごとの件数 (通常0-1/魔法1-2/レア3-4/レジェンド4-5/神話5-6) を抽選し、
     * 命名はレア=接頭+武器名・魔法=接頭+武器名・接尾・レジェンド/神話=専用名 (原作通り)。
     */
    public static net.minecraft.world.item.ItemStack makeWeapon(
            GameData.WeaponStyle style, net.minecraft.util.RandomSource rand,
            int luck, int floor, double bonus) {
        var stack = new net.minecraft.world.item.ItemStack(ModItems.itemOf(style));
        String rarity = GameData.rollRarity(rand::nextDouble, luck, bonus, floor);
        String trait = null;
        if (!rarity.equals("normal") && !rarity.equals("magic")) {
            trait = GearData.TRAITS[rand.nextInt(GearData.TRAITS.length)].id();
        } else if (rand.nextFloat() < (rarity.equals("magic") ? 0.3f : 0.1f)) {
            trait = GearData.TRAITS[rand.nextInt(GearData.TRAITS.length)].id();
        }
        // 呪い: レア以上で12%。弱点1つと引き換えに攻撃+24% (原作の特性2底上げに対応)
        String curseKey = null;
        double curseVal = 0;
        if (GameData.rollCursed(rand::nextDouble, rarity)) {
            var mod = GameData.rollCursedMod(rand::nextDouble);
            curseKey = mod.key();
            curseVal = GameData.rollCursedValue(rand::nextDouble, mod);
        }
        // 接辞
        int[] range = GameData.affixCountRange(rarity);
        int nAffix = range[0] + rand.nextInt(range[1] - range[0] + 1);
        var affixes = GameData.rollAffixes(rand::nextDouble, rarity, nAffix);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("abyss_rarity", rarity);
        if (trait != null) tag.putString("abyss_trait", trait);
        // 武器スキル: レア以上で25%
        if (!rarity.equals("normal") && !rarity.equals("magic") && rand.nextDouble() < 0.25) {
            var skills = GameData.WEAPON_SKILLS;
            tag.putString("abyss_skill", skills.get(rand.nextInt(skills.size())).id());
        }
        if (curseKey != null) {
            tag.putString("abyss_curse", curseKey);
            tag.putDouble("abyss_curse_val", curseVal);
        }
        if (!affixes.isEmpty()) {
            var sb = new StringBuilder();
            for (var af : affixes) {
                if (!sb.isEmpty()) sb.append(";");
                sb.append(af.key()).append(":").append(af.value());
            }
            tag.putString("abyss_affixes", sb.toString());
        }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        // 命名 (原作 genItem)
        String disp = switch (rarity) {
            case "mythic" -> GameData.mythicFor(rand::nextDouble);
            case "legendary" -> GameData.legendFor(rand::nextDouble);
            case "rare" -> GameData.prefixFor(rand::nextDouble) + style.weaponName;
            case "magic" -> GameData.prefixFor(rand::nextDouble) + style.weaponName
                    + "・" + GameData.suffixFor(rand::nextDouble);
            default -> style.weaponName;
        };
        String name = (curseKey != null ? "§5呪縛の" : "") + GearData.rarityColor(rarity) + disp;
        if (trait != null) name += "〔" + GearData.traitName(trait) + "〕";
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(name));
        GearData.refreshLore(stack);
        return stack;
    }


    /** 深淵防具の部位名 (セット品はスロットから判定) */
    public static String armorBaseName(net.minecraft.world.item.Item item) {
        if (item == ModItems.ABYSS_HELM.get()) return "深淵兜";
        if (item == ModItems.ABYSS_CHEST.get()) return "深淵鎧";
        if (item == ModItems.ABYSS_LEGS.get()) return "深淵脚";
        if (item == ModItems.ABYSS_BOOTS.get()) return "深淵靴";
        var slot = ModItems.slotOf(item);
        if (slot == null) return null;
        return switch (slot) {
            case HEAD -> "深淵兜";
            case CHEST -> "深淵鎧";
            case LEGS -> "深淵脚";
            case FEET -> "深淵靴";
            default -> null;
        };
    }


    /**
     * ランダム防具生成 (武器 makeWeapon の防具版)。
     * 希少度・呪い・接辞・命名は武器と同式。特性は付かない (原作通り武器専用)。
     * piece が null なら4部位からランダム。
     */
    public static net.minecraft.world.item.ItemStack makeArmor(
            net.minecraft.world.item.Item piece, net.minecraft.util.RandomSource rand,
            int luck, int floor, double bonus) {
        var pieces = new net.minecraft.world.item.Item[]{
                ModItems.ABYSS_HELM.get(), ModItems.ABYSS_CHEST.get(),
                ModItems.ABYSS_LEGS.get(), ModItems.ABYSS_BOOTS.get()};
        var item = piece != null ? piece : pieces[rand.nextInt(pieces.length)];
        var stack = new net.minecraft.world.item.ItemStack(item);
        String rarity = GameData.rollRarity(rand::nextDouble, luck, bonus, floor);
        String curseKey = null;
        double curseVal = 0;
        if (GameData.rollCursed(rand::nextDouble, rarity)) {
            var mod = GameData.rollCursedMod(rand::nextDouble);
            curseKey = mod.key();
            curseVal = GameData.rollCursedValue(rand::nextDouble, mod);
        }
        int[] range = GameData.affixCountRange(rarity);
        int nAffix = range[0] + rand.nextInt(range[1] - range[0] + 1);
        var affixes = GameData.rollAffixes(rand::nextDouble, rarity, nAffix, GameData.ARMOR_AFFIXES);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("abyss_rarity", rarity);
        // 防具スキル: 部位に応じたものを30%
        if (rand.nextDouble() < 0.30) {
            String prefix = switch (ModItems.slotOf(item)) {
                case HEAD -> "h_";
                case CHEST -> "c_";
                case LEGS -> "l_";
                case FEET -> "b_";
                default -> "";
            };
            var pool = GameData.ARMOR_SKILLS.stream().filter(s -> s.id().startsWith(prefix)).toList();
            if (!pool.isEmpty()) tag.putString("abyss_skill", pool.get(rand.nextInt(pool.size())).id());
        }
        if (curseKey != null) {
            tag.putString("abyss_curse", curseKey);
            tag.putDouble("abyss_curse_val", curseVal);
        }
        if (!affixes.isEmpty()) {
            var sb = new StringBuilder();
            for (var af : affixes) {
                if (!sb.isEmpty()) sb.append(";");
                sb.append(af.key()).append(":").append(af.value());
            }
            tag.putString("abyss_affixes", sb.toString());
        }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        String base = armorBaseName(item);
        String disp = switch (rarity) {
            case "mythic" -> GameData.mythicFor(rand::nextDouble);
            case "legendary" -> GameData.legendFor(rand::nextDouble);
            case "rare" -> GameData.prefixFor(rand::nextDouble) + base;
            case "magic" -> GameData.prefixFor(rand::nextDouble) + base
                    + "・" + GameData.suffixFor(rand::nextDouble);
            default -> base;
        };
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal((curseKey != null ? "§5呪縛の" : "") + GearData.rarityColor(rarity) + disp));
        GearData.refreshLore(stack);
        return stack;
    }


    /** 武器or防具のランダム装備 (雑魚ドロップ用。武器65%・防具35%) */
    public static net.minecraft.world.item.ItemStack makeEquipment(
            net.minecraft.util.RandomSource rand, int luck, int floor, double bonus) {
        if (rand.nextDouble() < 0.35) {
            return makeArmor(null, rand, luck, floor, bonus);
        }
        var styles = GameData.WeaponStyle.values();
        return makeWeapon(styles[rand.nextInt(styles.length)], rand, luck, floor, bonus);
    }


    /**
     * セット防具生成 (abyss_<set>_<piece>)。abyss_set を刻印しセット名で命名。
     * setId が null ならランダムセット、piece が null ならランダム部位。
     */
    public static net.minecraft.world.item.ItemStack makeSetArmor(
            String setId, net.minecraft.world.item.Item piece, net.minecraft.util.RandomSource rand,
            int luck, int floor, double bonus) {
        var sets = GameData.ARMOR_SETS;
        GameData.ArmorSet picked = setId != null ? GameData.armorSetById(setId)
                : sets.get(rand.nextInt(sets.size()));
        if (picked == null) picked = sets.get(0);
        final GameData.ArmorSet set = picked;
        var keys = new java.util.ArrayList<>(ModItems.SET_PIECES.keySet().stream()
                .filter(k -> k.startsWith(set.id() + "_")).toList());
        String key = piece != null ? null : keys.get(rand.nextInt(keys.size()));
        net.minecraft.world.item.Item item = piece != null ? piece
                : ModItems.SET_PIECES.get(key).get();
        var stack = makeArmor(item, rand, luck, floor, bonus);
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putString("abyss_set", set.id());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        String base = armorBaseName(item);
        String rarity = GearData.rarityOf(stack);
        String disp = switch (rarity) {
            case "mythic" -> GameData.mythicFor(rand::nextDouble);
            case "legendary" -> GameData.legendFor(rand::nextDouble);
            case "rare" -> GameData.prefixFor(rand::nextDouble) + base;
            case "magic" -> GameData.prefixFor(rand::nextDouble) + base
                    + "・" + GameData.suffixFor(rand::nextDouble);
            default -> base;
        };
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§b【" + set.name() + "】" + GearData.rarityColor(rarity) + disp));
        GearData.refreshLore(stack);
        return stack;
    }
}
