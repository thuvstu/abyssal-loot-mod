package com.abyssalloot;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.List;
import java.util.function.Consumer;

/**
 * アイテム12武器種 + 深淵防具4種 + 魂の欠片.
 * Tier APIの差異を避けるため素のItemとして登録し、
 * ダメージ計算は CombatHandler 側の独自式で行う (原作式の再現のため).
 * 防具は右クリックで対応スロットに装備し、接辞は武器と合算される。
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AbyssalLootMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AbyssalLootMod.MODID);

    private static DeferredItem<Item> weapon(String id, Rarity rarity) {
        return ITEMS.registerItem(id,
                props -> new AbyssWeaponItem(props.stacksTo(1).durability(1561).rarity(rarity).fireResistant()));
    }

    /** 武器 (lore 表示つき) */
    public static class AbyssWeaponItem extends Item {
        public AbyssWeaponItem(Properties props) {
            super(props);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display,
                                    Consumer<Component> out, TooltipFlag flag) {
            super.appendHoverText(stack, ctx, display, out, flag);
            RunManager.appendGearLore(stack, out, true);
        }
    }

    /** 防具 (lore 表示つき。右クリック装備は EQUIPPABLE コンポーネントが処理) */
    public static class AbyssArmorItem extends Item {
        private final EquipmentSlot slot;

        public AbyssArmorItem(Properties props, EquipmentSlot slot) {
            super(props);
            this.slot = slot;
        }

        public EquipmentSlot abyssSlot() {
            return slot;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display,
                                    Consumer<Component> out, TooltipFlag flag) {
            super.appendHoverText(stack, ctx, display, out, flag);
            RunManager.appendGearLore(stack, out, false);
        }
    }

    private static DeferredItem<Item> armor(String id, Rarity rarity, EquipmentSlot slot) {
        return ITEMS.registerItem(id,
                props -> new AbyssArmorItem(props.stacksTo(1).durability(1651).rarity(rarity).fireResistant()
                        .equippable(slot), slot));
    }

    public static final DeferredItem<Item> ABYSS_SWORD = weapon("abyss_sword", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_AXE = weapon("abyss_axe", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_SPEAR = weapon("abyss_spear", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_STAFF = weapon("abyss_staff", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_BOW = weapon("abyss_bow", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_DAGGER = weapon("abyss_dagger", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_HAMMER = weapon("abyss_hammer", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_KATANA = weapon("abyss_katana", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSS_SCYTHE = weapon("abyss_scythe", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_GAUNTLET = weapon("abyss_gauntlet", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_CHAKRAM = weapon("abyss_chakram", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_TOME = weapon("abyss_tome", Rarity.EPIC);

    public static final DeferredItem<Item> ABYSS_HELM = armor("abyss_helm", Rarity.COMMON, EquipmentSlot.HEAD);
    public static final DeferredItem<Item> ABYSS_CHEST = armor("abyss_chest", Rarity.COMMON, EquipmentSlot.CHEST);
    public static final DeferredItem<Item> ABYSS_LEGS = armor("abyss_legs", Rarity.COMMON, EquipmentSlot.LEGS);
    public static final DeferredItem<Item> ABYSS_BOOTS = armor("abyss_boots", Rarity.COMMON, EquipmentSlot.FEET);

    public static final DeferredItem<Item> SOUL_SHARD = ITEMS.registerSimpleItem("soul_shard",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));

    /** 素材 (原作 MATERIALS のうち鍛造に使う3種)。ドロップ・鍛造コスト用 */
    public static final DeferredItem<Item> ANCIENT_STONE = ITEMS.registerSimpleItem("ancient_stone",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> CHAOS_GEM = ITEMS.registerSimpleItem("chaos_gem",
            () -> new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<Item> VOID_ESSENCE = ITEMS.registerSimpleItem("void_essence",
            () -> new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ABYSS_TAB =
            CREATIVE_TABS.register("abyss_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("Abyssal Loot"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ABYSS_KATANA.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ABYSS_SWORD.get());
                        output.accept(ABYSS_AXE.get());
                        output.accept(ABYSS_SPEAR.get());
                        output.accept(ABYSS_STAFF.get());
                        output.accept(ABYSS_BOW.get());
                        output.accept(ABYSS_DAGGER.get());
                        output.accept(ABYSS_HAMMER.get());
                        output.accept(ABYSS_KATANA.get());
                        output.accept(ABYSS_SCYTHE.get());
                        output.accept(ABYSS_GAUNTLET.get());
                        output.accept(ABYSS_CHAKRAM.get());
                        output.accept(ABYSS_TOME.get());
                        output.accept(ABYSS_HELM.get());
                        output.accept(ABYSS_CHEST.get());
                        output.accept(ABYSS_LEGS.get());
                        output.accept(ABYSS_BOOTS.get());
                        output.accept(SOUL_SHARD.get());
                        output.accept(ANCIENT_STONE.get());
                        output.accept(CHAOS_GEM.get());
                        output.accept(VOID_ESSENCE.get());
                    }).build());

    /** 防具Item -> 装備スロット (武器・その他は null) */
    public static EquipmentSlot slotOf(Item item) {
        if (item instanceof AbyssArmorItem armor) return armor.abyssSlot();
        return null;
    }

    /** 手持ちItem -> 武器種 の逆引き */
    public static GameData.WeaponStyle styleOf(Item item) {
        if (item == ABYSS_SWORD.get()) return GameData.WeaponStyle.SWORD;
        if (item == ABYSS_AXE.get()) return GameData.WeaponStyle.AXE;
        if (item == ABYSS_SPEAR.get()) return GameData.WeaponStyle.SPEAR;
        if (item == ABYSS_STAFF.get()) return GameData.WeaponStyle.STAFF;
        if (item == ABYSS_BOW.get()) return GameData.WeaponStyle.BOW;
        if (item == ABYSS_DAGGER.get()) return GameData.WeaponStyle.DAGGER;
        if (item == ABYSS_HAMMER.get()) return GameData.WeaponStyle.HAMMER;
        if (item == ABYSS_KATANA.get()) return GameData.WeaponStyle.KATANA;
        if (item == ABYSS_SCYTHE.get()) return GameData.WeaponStyle.SCYTHE;
        if (item == ABYSS_GAUNTLET.get()) return GameData.WeaponStyle.GAUNTLET;
        if (item == ABYSS_CHAKRAM.get()) return GameData.WeaponStyle.CHAKRAM;
        if (item == ABYSS_TOME.get()) return GameData.WeaponStyle.TOME;
        return null;
    }

    public static Item itemOf(GameData.WeaponStyle style) {
        return switch (style) {
            case SWORD -> ABYSS_SWORD.get();
            case AXE -> ABYSS_AXE.get();
            case SPEAR -> ABYSS_SPEAR.get();
            case STAFF -> ABYSS_STAFF.get();
            case BOW -> ABYSS_BOW.get();
            case DAGGER -> ABYSS_DAGGER.get();
            case HAMMER -> ABYSS_HAMMER.get();
            case KATANA -> ABYSS_KATANA.get();
            case SCYTHE -> ABYSS_SCYTHE.get();
            case GAUNTLET -> ABYSS_GAUNTLET.get();
            case CHAKRAM -> ABYSS_CHAKRAM.get();
            case TOME -> ABYSS_TOME.get();
        };
    }
}
