package com.abyssalloot;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
            GearData.appendGearLore(stack, out, true);
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
            GearData.appendGearLore(stack, out, false);
        }
    }

    private static DeferredItem<Item> armor(String id, Rarity rarity, EquipmentSlot slot) {
        return armor(id, rarity, slot, "abyssal");
    }

    /** 着用モデルつき防具 (assets/abyssalloot/equipment/<asset>.json を参照) */
    private static DeferredItem<Item> armor(String id, Rarity rarity, EquipmentSlot slot, String asset) {
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, asset));
        return ITEMS.registerItem(id,
                props -> new AbyssArmorItem(props.stacksTo(1).durability(1651).rarity(rarity).fireResistant()
                        .component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                                net.minecraft.world.item.equipment.Equippable.builder(slot)
                                        .setAsset(key).build()),
                        slot));
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
    public static final DeferredItem<Item> ABYSS_RUNE_BLADE = weapon("abyss_rune_blade", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_BLOOD_AXE = weapon("abyss_blood_axe", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_STORM_SPEAR = weapon("abyss_storm_spear", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_VOID_STAFF = weapon("abyss_void_staff", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_MOON_BOW = weapon("abyss_moon_bow", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_FANG_DAGGER = weapon("abyss_fang_dagger", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_QUAKE_HAMMER = weapon("abyss_quake_hammer", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_FROST_KATANA = weapon("abyss_frost_katana", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSS_SOUL_SCYTHE = weapon("abyss_soul_scythe", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_TITAN_GAUNTLET = weapon("abyss_titan_gauntlet", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_ECLIPSE_CHAKRAM = weapon("abyss_eclipse_chakram", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_CHAOS_TOME = weapon("abyss_chaos_tome", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSS_ROD = weapon("abyss_rod", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_WAND = weapon("abyss_wand", Rarity.COMMON);

    public static final DeferredItem<Item> ABYSS_HELM = armor("abyss_helm", Rarity.COMMON, EquipmentSlot.HEAD);
    public static final DeferredItem<Item> ABYSS_CHEST = armor("abyss_chest", Rarity.COMMON, EquipmentSlot.CHEST);
    public static final DeferredItem<Item> ABYSS_LEGS = armor("abyss_legs", Rarity.COMMON, EquipmentSlot.LEGS);
    public static final DeferredItem<Item> ABYSS_BOOTS = armor("abyss_boots", Rarity.COMMON, EquipmentSlot.FEET);

    /** 深淵防具セット (blood/shadow/rock/star × 4部位)。接辞つき生成は GearFactory.makeSetArmor */
    public static final java.util.Map<String, DeferredItem<Item>> SET_PIECES = buildSetPieces();

    private static java.util.Map<String, DeferredItem<Item>> buildSetPieces() {
        var map = new java.util.LinkedHashMap<String, DeferredItem<Item>>();
        for (String set : new String[]{"blood", "shadow", "rock", "star", "frost", "storm"}) {
            map.put(set + "_helm", armor("abyss_" + set + "_helm", Rarity.COMMON, EquipmentSlot.HEAD, set));
            map.put(set + "_chest", armor("abyss_" + set + "_chest", Rarity.COMMON, EquipmentSlot.CHEST, set));
            map.put(set + "_legs", armor("abyss_" + set + "_legs", Rarity.COMMON, EquipmentSlot.LEGS, set));
            map.put(set + "_boots", armor("abyss_" + set + "_boots", Rarity.COMMON, EquipmentSlot.FEET, set));
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    /** 消耗品 (使用効果は RunManager.useConsumable)。ID -> 効果種 */
    public static class AbyssConsumable extends Item {
        private final String kind;

        public AbyssConsumable(Properties props, String kind) {
            super(props);
            this.kind = kind;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty() || held.getItem() != this) return InteractionResult.PASS;
            if (RunManager.useConsumable(sp, kind)) {
                if (!level.isClientSide()) held.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display,
                                    Consumer<Component> out, TooltipFlag flag) {
            super.appendHoverText(stack, ctx, display, out, flag);
            out.accept(Component.literal("§7右クリックで使用 (使い切り)"));
            out.accept(Component.literal("§b" + RunManager.consumableDesc(kind)));
        }
    }

    private static DeferredItem<Item> consumable(String id, String kind, Rarity rarity) {
        return ITEMS.registerItem(id,
                props -> new AbyssConsumable(props.stacksTo(16).rarity(rarity), kind));
    }

    public static final DeferredItem<Item> ABYSS_HEAL_S = consumable("abyss_heal_s", "heal_30", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_HEAL_M = consumable("abyss_heal_m", "heal_70", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_HEAL_L = consumable("abyss_heal_l", "heal_100", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SOUL_S = consumable("abyss_soul_s", "gauge_40", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_SOUL_L = consumable("abyss_soul_l", "gauge_100", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_PWR = consumable("abyss_pwr", "buff_str", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_SPD = consumable("abyss_spd", "buff_spd", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_IRN = consumable("abyss_irn", "buff_res", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_RGN = consumable("abyss_rgn", "buff_regen", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_ABS = consumable("abyss_abs", "buff_abso", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_FIR = consumable("abyss_fir", "buff_fire", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_OWL = consumable("abyss_owl", "buff_nv", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_FTH = consumable("abyss_fth", "buff_fall", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_LCK = consumable("abyss_lck", "buff_luck", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_BOMB_F = consumable("abyss_bomb_f", "bomb_fire", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_BOMB_T = consumable("abyss_bomb_t", "bomb_bolt", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_BOMB_I = consumable("abyss_bomb_i", "bomb_frost", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_TELE_A = consumable("abyss_tele_a", "tele_arena", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_TELE_S = consumable("abyss_tele_s", "tele_spawn", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_CURE = consumable("abyss_cure", "cure", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_MAGNET = consumable("abyss_magnet", "magnet", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_REVEAL = consumable("abyss_reveal", "reveal", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_FEAST = consumable("abyss_feast", "feast", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_SMOKE = consumable("abyss_smoke", "smoke", Rarity.UNCOMMON);

    /** 生活ツール用素材 (ネザライト級・高速・魂の欠片等で修繕可) */
    public static final net.minecraft.world.item.ToolMaterial ABYSS_TIER =
            new net.minecraft.world.item.ToolMaterial(net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
                    3000, 12.0F, 5.0F, 18,
                    net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID,
                                    "repairs_tools")));

    /** 深淵の生活ツール (試練の合間のマイクラ生活用。火耐性つき) */
    public static final DeferredItem<Item> ABYSS_PICKAXE = ITEMS.registerItem("abyss_pickaxe",
            props -> new Item(props.pickaxe(ABYSS_TIER, 1.0F, -2.6F).rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<Item> ABYSS_TOOL_AXE = ITEMS.registerItem("abyss_tool_axe",
            props -> new net.minecraft.world.item.AxeItem(ABYSS_TIER, 6.0F, -2.8F,
                    props.rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<Item> ABYSS_SHOVEL = ITEMS.registerItem("abyss_shovel",
            props -> new net.minecraft.world.item.ShovelItem(ABYSS_TIER, 1.5F, -2.8F,
                    props.rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<Item> ABYSS_HOE = ITEMS.registerItem("abyss_hoe",
            props -> new net.minecraft.world.item.HoeItem(ABYSS_TIER, -3.0F, -1.0F,
                    props.rarity(Rarity.UNCOMMON).fireResistant()));
    /** 追加素材 (ドロップ・クラフト用) */
    public static final DeferredItem<Item> ABYSS_BLOOD_CRYSTAL = ITEMS.registerSimpleItem("abyss_blood_crystal",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_SHADOW_STONE = ITEMS.registerSimpleItem("abyss_shadow_stone",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_STAR_DUST = ITEMS.registerSimpleItem("abyss_star_dust",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ABYSS_WYRM_SCALE = ITEMS.registerSimpleItem("abyss_wyrm_scale",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ABYSS_ANCIENT_CORE = ITEMS.registerSimpleItem("abyss_ancient_core",
            () -> new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<Item> ABYSS_VOID_PIECE = ITEMS.registerSimpleItem("abyss_void_piece",
            () -> new Item.Properties().rarity(Rarity.EPIC));

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
                        output.accept(ABYSS_RUNE_BLADE.get());
                        output.accept(ABYSS_BLOOD_AXE.get());
                        output.accept(ABYSS_STORM_SPEAR.get());
                        output.accept(ABYSS_VOID_STAFF.get());
                        output.accept(ABYSS_MOON_BOW.get());
                        output.accept(ABYSS_FANG_DAGGER.get());
                        output.accept(ABYSS_QUAKE_HAMMER.get());
                        output.accept(ABYSS_FROST_KATANA.get());
                        output.accept(ABYSS_SOUL_SCYTHE.get());
                        output.accept(ABYSS_TITAN_GAUNTLET.get());
                        output.accept(ABYSS_ECLIPSE_CHAKRAM.get());
                        output.accept(ABYSS_CHAOS_TOME.get());
                        output.accept(ABYSS_ROD.get());
                        output.accept(ABYSS_WAND.get());
                        output.accept(ABYSS_PICKAXE.get());
                        output.accept(ABYSS_TOOL_AXE.get());
                        output.accept(ABYSS_SHOVEL.get());
                        output.accept(ABYSS_HOE.get());
                        output.accept(ABYSS_HELM.get());
                        output.accept(ABYSS_CHEST.get());
                        output.accept(ABYSS_LEGS.get());
                        output.accept(ABYSS_BOOTS.get());
                        for (var piece : SET_PIECES.values()) output.accept(piece.get());
                        output.accept(ABYSS_HEAL_S.get());
                        output.accept(ABYSS_HEAL_M.get());
                        output.accept(ABYSS_HEAL_L.get());
                        output.accept(ABYSS_SOUL_S.get());
                        output.accept(ABYSS_SOUL_L.get());
                        output.accept(ABYSS_PWR.get());
                        output.accept(ABYSS_SPD.get());
                        output.accept(ABYSS_IRN.get());
                        output.accept(ABYSS_RGN.get());
                        output.accept(ABYSS_ABS.get());
                        output.accept(ABYSS_FIR.get());
                        output.accept(ABYSS_OWL.get());
                        output.accept(ABYSS_FTH.get());
                        output.accept(ABYSS_LCK.get());
                        output.accept(ABYSS_BOMB_F.get());
                        output.accept(ABYSS_BOMB_T.get());
                        output.accept(ABYSS_BOMB_I.get());
                        output.accept(ABYSS_TELE_A.get());
                        output.accept(ABYSS_TELE_S.get());
                        output.accept(ABYSS_CURE.get());
                        output.accept(ABYSS_MAGNET.get());
                        output.accept(ABYSS_REVEAL.get());
                        output.accept(ABYSS_FEAST.get());
                        output.accept(ABYSS_SMOKE.get());
                        output.accept(ABYSS_BLOOD_CRYSTAL.get());
                        output.accept(ABYSS_SHADOW_STONE.get());
                        output.accept(ABYSS_STAR_DUST.get());
                        output.accept(ABYSS_WYRM_SCALE.get());
                        output.accept(ABYSS_ANCIENT_CORE.get());
                        output.accept(ABYSS_VOID_PIECE.get());
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

    /** 消耗品のランダム1個 (ドロップ用) */
    public static ItemStack randomConsumable(net.minecraft.util.RandomSource rand) {
        var all = java.util.List.of(
                ABYSS_HEAL_S, ABYSS_HEAL_M, ABYSS_SOUL_S, ABYSS_PWR, ABYSS_SPD, ABYSS_IRN,
                ABYSS_RGN, ABYSS_FIR, ABYSS_OWL, ABYSS_FTH, ABYSS_LCK, ABYSS_BOMB_F,
                ABYSS_TELE_S, ABYSS_CURE, ABYSS_FEAST, ABYSS_SMOKE, ABYSS_HEAL_L, ABYSS_SOUL_L,
                ABYSS_ABS, ABYSS_BOMB_T, ABYSS_BOMB_I, ABYSS_TELE_A, ABYSS_MAGNET, ABYSS_REVEAL);
        return new ItemStack(all.get(rand.nextInt(all.size())).get());
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
        if (item == ABYSS_RUNE_BLADE.get()) return GameData.WeaponStyle.RUNE_BLADE;
        if (item == ABYSS_BLOOD_AXE.get()) return GameData.WeaponStyle.BLOOD_AXE;
        if (item == ABYSS_STORM_SPEAR.get()) return GameData.WeaponStyle.STORM_SPEAR;
        if (item == ABYSS_VOID_STAFF.get()) return GameData.WeaponStyle.VOID_STAFF;
        if (item == ABYSS_MOON_BOW.get()) return GameData.WeaponStyle.MOON_BOW;
        if (item == ABYSS_FANG_DAGGER.get()) return GameData.WeaponStyle.FANG_DAGGER;
        if (item == ABYSS_QUAKE_HAMMER.get()) return GameData.WeaponStyle.QUAKE_HAMMER;
        if (item == ABYSS_FROST_KATANA.get()) return GameData.WeaponStyle.FROST_KATANA;
        if (item == ABYSS_SOUL_SCYTHE.get()) return GameData.WeaponStyle.SOUL_SCYTHE;
        if (item == ABYSS_TITAN_GAUNTLET.get()) return GameData.WeaponStyle.TITAN_GAUNTLET;
        if (item == ABYSS_ECLIPSE_CHAKRAM.get()) return GameData.WeaponStyle.ECLIPSE_CHAKRAM;
        if (item == ABYSS_CHAOS_TOME.get()) return GameData.WeaponStyle.CHAOS_TOME;
        if (item == ABYSS_ROD.get()) return GameData.WeaponStyle.ROD;
        if (item == ABYSS_WAND.get()) return GameData.WeaponStyle.WAND;
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
            case RUNE_BLADE -> ABYSS_RUNE_BLADE.get();
            case BLOOD_AXE -> ABYSS_BLOOD_AXE.get();
            case STORM_SPEAR -> ABYSS_STORM_SPEAR.get();
            case VOID_STAFF -> ABYSS_VOID_STAFF.get();
            case MOON_BOW -> ABYSS_MOON_BOW.get();
            case FANG_DAGGER -> ABYSS_FANG_DAGGER.get();
            case QUAKE_HAMMER -> ABYSS_QUAKE_HAMMER.get();
            case FROST_KATANA -> ABYSS_FROST_KATANA.get();
            case SOUL_SCYTHE -> ABYSS_SOUL_SCYTHE.get();
            case TITAN_GAUNTLET -> ABYSS_TITAN_GAUNTLET.get();
            case ECLIPSE_CHAKRAM -> ABYSS_ECLIPSE_CHAKRAM.get();
            case CHAOS_TOME -> ABYSS_CHAOS_TOME.get();
            case ROD -> ABYSS_ROD.get();
            case WAND -> ABYSS_WAND.get();
        };
    }
}
