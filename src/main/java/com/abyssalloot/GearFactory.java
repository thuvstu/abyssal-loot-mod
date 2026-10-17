package com.abyssalloot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * 武器・防具の生成 (原作 genItem の MC 対応版)。
 * 希少度抽選・呪い・接辞・命名は1箇所に集約し、武器/防具/セット防具で共有する。
 */
public final class GearFactory {
    private GearFactory() {}

    /** 希少度の抽選結果 (レア以上かどうかの判定もここで) */
    private record Roll(String rarity, String curseKey, double curseVal,
                        List<GameData.RolledAffix> affixes) {}

    private static boolean isRareOrAbove(String rarity) {
        return !rarity.equals("normal") && !rarity.equals("magic");
    }

    /** 希少度・呪い・接辞の共通抽選 (原作 genItem)。呪いはレア以上で12% */
    private static Roll rollGear(RandomSource rand, int luck, int floor, double bonus,
                                 List<GameData.AffixKey> affixPool) {
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
        var affixes = GameData.rollAffixes(rand::nextDouble, rarity, nAffix, affixPool);
        return new Roll(rarity, curseKey, curseVal, affixes);
    }

    /** 共通抽選を CustomData タグへ展開 (レアリティ・呪い・接辞) */
    private static CompoundTag baseTag(Roll roll) {
        var tag = new CompoundTag();
        tag.putString("abyss_rarity", roll.rarity());
        if (roll.curseKey() != null) {
            tag.putString("abyss_curse", roll.curseKey());
            tag.putDouble("abyss_curse_val", roll.curseVal());
        }
        if (!roll.affixes().isEmpty()) {
            var sb = new StringBuilder();
            for (var af : roll.affixes()) {
                if (!sb.isEmpty()) sb.append(";");
                sb.append(af.key()).append(":").append(af.value());
            }
            tag.putString("abyss_affixes", sb.toString());
        }
        return tag;
    }

    /** 命名 (原作 genItem): レア=接頭+本体 / 魔法=接頭+本体・接尾 / レジェンド/神話=専用名 */
    private static String displayName(String base, String rarity, RandomSource rand) {
        return switch (rarity) {
            case "mythic" -> GameData.mythicFor(rand::nextDouble);
            case "legendary" -> GameData.legendFor(rand::nextDouble);
            case "rare" -> GameData.prefixFor(rand::nextDouble) + base;
            case "magic" -> GameData.prefixFor(rand::nextDouble) + base
                    + "・" + GameData.suffixFor(rand::nextDouble);
            default -> base;
        };
    }

    /** 武器名の本体表示 (呪縛の接頭・色・特性〔〕付き) */
    private static String weaponTitle(String base, Roll roll, String trait, RandomSource rand) {
        String name = (roll.curseKey() != null ? "§5呪縛の" : "") + GearData.rarityColor(roll.rarity())
                + displayName(base, roll.rarity(), rand);
        if (trait != null) name += "〔" + GearData.traitName(trait) + "〕";
        return name;
    }

    /** 防具名の本体表示 (呪縛の接頭・色付き)。特性は付かない (原作通り武器専用) */
    private static String armorTitle(String base, Roll roll, RandomSource rand) {
        return (roll.curseKey() != null ? "§5呪縛の" : "") + GearData.rarityColor(roll.rarity())
                + displayName(base, roll.rarity(), rand);
    }

    /** ランダム武器生成 (レアリティ抽選+特性付与)。運・層・ボーナスなしの簡易版 */
    public static ItemStack makeWeapon(GameData.WeaponStyle style, RandomSource rand) {
        return makeWeapon(style, rand, 0, 1, 0);
    }

    /**
     * ランダム武器生成。
     * 接辞は希少度ごとの件数 (通常0-1/魔法1-2/レア3-4/レジェンド4-5/神話5-6) を抽選し、
     * 特性はレア以上で必須・それ以下でも低確率。武器スキルはレア以上で25%。
     */
    public static ItemStack makeWeapon(GameData.WeaponStyle style, RandomSource rand,
                                       int luck, int floor, double bonus) {
        return buildWeapon(style, rollGear(rand, luck, floor, bonus, GameData.WEAPON_AFFIXES), rand);
    }

    /** 抽選済みのロールから武器を積み上げる (命名含む)。makeWeapon / makePrizeWeapon の共通本体 */
    private static ItemStack buildWeapon(GameData.WeaponStyle style, Roll roll, RandomSource rand) {
        // 特性: レア以上で必ず、魔法で30%、通常で10%
        String trait = null;
        if (isRareOrAbove(roll.rarity())) {
            trait = GearData.TRAITS[rand.nextInt(GearData.TRAITS.length)].id();
        } else if (rand.nextFloat() < (roll.rarity().equals("magic") ? 0.3f : 0.1f)) {
            trait = GearData.TRAITS[rand.nextInt(GearData.TRAITS.length)].id();
        }
        var tag = baseTag(roll);
        if (trait != null) tag.putString("abyss_trait", trait);
        // 武器スキル: レア以上で25%
        if (isRareOrAbove(roll.rarity()) && rand.nextDouble() < 0.25) {
            var skills = GameData.WEAPON_SKILLS;
            tag.putString("abyss_skill", skills.get(rand.nextInt(skills.size())).id());
        }
        var stack = new ItemStack(ModItems.itemOf(style));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(weaponTitle(style.weaponName, roll, trait, rand)));
        GearData.refreshLore(stack);
        return stack;
    }

    /**
     * 賞品武器 (報酬用)。希少度をレア級に下支えし (抽象ボーナス200で抽選)、
     * 鍛造素材になるスキルを必ず1つ刻む。勝利品・商店・層主系の報酬で使う。
     */
    public static ItemStack makePrizeWeapon(GameData.WeaponStyle style, RandomSource rand,
                                            int luck, int floor) {
        var roll = rollGear(rand, luck, floor, 200, GameData.WEAPON_AFFIXES);
        if (GameData.rarityRank(roll.rarity()) < 2) {
            roll = new Roll("rare", roll.curseKey(), roll.curseVal(), roll.affixes());
        }
        var stack = buildWeapon(style, roll, rand);
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (GearData.skillOf(stack) == null) {
            var skills = GameData.WEAPON_SKILLS;
            tag.putString("abyss_skill", skills.get(rand.nextInt(skills.size())).id());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            GearData.refreshLore(stack);
        }
        return stack;
    }

    /** 深淵防具の部位名 (セット品はスロットから判定) */
    public static String armorBaseName(Item item) {
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
     * ランダム防具生成 (makeWeapon の防具版)。希少度・呪い・接辞・命名は武器と同式。
     * 部位スキルは30%。piece が null なら4部位からランダム。
     */
    public static ItemStack makeArmor(Item piece, RandomSource rand, int luck, int floor, double bonus) {
        var pieces = new Item[]{
                ModItems.ABYSS_HELM.get(), ModItems.ABYSS_CHEST.get(),
                ModItems.ABYSS_LEGS.get(), ModItems.ABYSS_BOOTS.get()};
        var item = piece != null ? piece : pieces[rand.nextInt(pieces.length)];
        var roll = rollGear(rand, luck, floor, bonus, GameData.ARMOR_AFFIXES);
        var tag = baseTag(roll);
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
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(armorTitle(armorBaseName(item), roll, rand)));
        GearData.refreshLore(stack);
        return stack;
    }

    /** 武器or防具のランダム装備 (雑魚ドロップ用。武器65%・防具35%) */
    public static ItemStack makeEquipment(RandomSource rand, int luck, int floor, double bonus) {
        if (rand.nextDouble() < 0.35) {
            return makeArmor(null, rand, luck, floor, bonus);
        }
        var styles = GameData.WeaponStyle.values();
        return makeWeapon(styles[rand.nextInt(styles.length)], rand, luck, floor, bonus);
    }

    /**
     * セット防具生成 (abyss_<set>_<piece>)。abyss_set を刻印し【セット名】で命名。
     * setId が null ならランダムセット、piece が null ならランダム部位。
     */
    public static ItemStack makeSetArmor(String setId, Item piece, RandomSource rand,
                                         int luck, int floor, double bonus) {
        var sets = GameData.ARMOR_SETS;
        GameData.ArmorSet picked = setId != null ? GameData.armorSetById(setId)
                : sets.get(rand.nextInt(sets.size()));
        if (picked == null) picked = sets.get(0);
        final GameData.ArmorSet set = picked;
        var items = ModItems.SET_PIECES.entrySet().stream()
                .filter(e -> e.getKey().startsWith(set.id() + "_")).toList();
        Item item = piece != null ? piece
                : items.get(rand.nextInt(items.size())).getValue().get();
        var stack = makeArmor(item, rand, luck, floor, bonus);
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString("abyss_set", set.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        var roll = new Roll(GearData.rarityOf(stack), null, 0, List.of());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                "§b【" + set.name() + "】" + armorTitle(armorBaseName(item), roll, rand)));
        GearData.refreshLore(stack);
        return stack;
    }
}
