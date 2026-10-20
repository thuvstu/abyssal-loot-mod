package com.abyssalloot;

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
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * アイテム41武器種 + 深淵防具4種 + 魂の欠片.
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
                props -> new Item(props.stacksTo(1).durability(1561).rarity(rarity).fireResistant()));
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
    }

    private static DeferredItem<Item> armor(String id, Rarity rarity, EquipmentSlot slot) {
        return armor(id, rarity, slot, "abyssal");
    }

    /** 着用モデルつき防具 (assets/abyssalloot/equipment/<asset>.json を参照)。
     * ENCHANTABLE 15 ≒ 鉄相当: バニラのダメージ軽減・耐久力などのエンチャントが深淵防具にも効く */
    private static DeferredItem<Item> armor(String id, Rarity rarity, EquipmentSlot slot, String asset) {
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, asset));
        return ITEMS.registerItem(id,
                props -> new AbyssArmorItem(props.stacksTo(1).durability(1651).rarity(rarity).fireResistant()
                        .component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                                net.minecraft.world.item.equipment.Equippable.builder(slot)
                                        .setAsset(key).build())
                        .component(net.minecraft.core.component.DataComponents.ENCHANTABLE,
                                new net.minecraft.world.item.enchantment.Enchantable(15)),
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
    public static final DeferredItem<Item> ABYSS_GREATSWORD = weapon("abyss_greatsword", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_TWIN_DAGGER = weapon("abyss_twin_dagger", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_WAR_HORN = weapon("abyss_war_horn", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_WIND_BLADE = weapon("abyss_wind_blade", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_EMBER_FLAIL = weapon("abyss_ember_flail", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_ASTRAL_LANCE = weapon("abyss_astral_lance", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_TIDE_TRIDENT = weapon("abyss_tide_trident", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_MOON_MACE = weapon("abyss_moon_mace", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_DEPTH_GUN = weapon("abyss_depth_gun", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_VENOM_WHIP = weapon("abyss_venom_whip", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_FLAME_GRIMOIRE = weapon("abyss_flame_grimoire", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSS_RIFT_BLADE = weapon("abyss_rift_blade", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSS_METEOR_LANCE = weapon("abyss_meteor_lance", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_ECHO_KATANA = weapon("abyss_echo_katana", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_HOWLING_CLAW = weapon("abyss_howling_claw", Rarity.COMMON);

    public static final DeferredItem<Item> ABYSS_HELM = armor("abyss_helm", Rarity.COMMON, EquipmentSlot.HEAD);
    public static final DeferredItem<Item> ABYSS_CHEST = armor("abyss_chest", Rarity.COMMON, EquipmentSlot.CHEST);
    public static final DeferredItem<Item> ABYSS_LEGS = armor("abyss_legs", Rarity.COMMON, EquipmentSlot.LEGS);
    public static final DeferredItem<Item> ABYSS_BOOTS = armor("abyss_boots", Rarity.COMMON, EquipmentSlot.FEET);

    /** 深淵防具セット (blood/shadow/rock/star/frost/storm/moon/ember × 4部位)。接辞つき生成は GearFactory.makeSetArmor */
    public static final java.util.Map<String, DeferredItem<Item>> SET_PIECES = buildSetPieces();

    private static java.util.Map<String, DeferredItem<Item>> buildSetPieces() {
        var map = new java.util.LinkedHashMap<String, DeferredItem<Item>>();
        for (String set : new String[]{"blood", "shadow", "rock", "star", "frost", "storm", "moon", "ember"}) {
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

        /** 効果種 (heal_30 / gauge_40 など) */
        public String consumableKind() {
            return kind;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty() || held.getItem() != this) return InteractionResult.PASS;
            if (Consumables.useConsumable(sp, kind)) {
                if (!level.isClientSide()) held.shrink(1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
    }

    private static DeferredItem<Item> consumable(String id, String kind, Rarity rarity) {
        return ITEMS.registerItem(id,
                props -> new AbyssConsumable(props.stacksTo(16).rarity(rarity)
                        .component(net.minecraft.core.component.DataComponents.LORE,
                                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                                        Component.literal("§7右クリックで使用 (使い切り)"),
                                        Component.literal("§b" + Consumables.consumableDesc(kind))))),
                        kind));
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
    public static final DeferredItem<Item> ABYSS_BERSERK = consumable("abyss_berserk", "berserk", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_BULWARK = consumable("abyss_bulwark", "bulwark", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_BOMB_H = consumable("abyss_bomb_h", "bomb_blaze", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_FORTUNE = consumable("abyss_fortune", "fortune", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_RATION = consumable("abyss_ration", "ration", Rarity.COMMON);
    // --- 第3弾 消耗品 (24種 再現) ---
    public static final DeferredItem<Item> ABYSS_FLASK = consumable("abyss_flask", "heal_50", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_GUT = consumable("abyss_gut", "gorging", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_GRANDFEAST = consumable("abyss_grandfeast", "grand_feast", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_REGENX = consumable("abyss_regenx", "regen2", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_STRX = consumable("abyss_strx", "str2", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_RESX = consumable("abyss_resx", "res2", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_ABSX = consumable("abyss_absx", "abs2", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_STRIDE = consumable("abyss_stride", "spd3", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_NIGHTWARD = consumable("abyss_nightward", "nv10", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_SALAMANDER = consumable("abyss_salamander", "fire10", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_PEARLDIVE = consumable("abyss_pearldive", "water5", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_VEILNOTE = consumable("abyss_veilnote", "invis30", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_PURITY = consumable("abyss_purity", "purity", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_CHAINBOMB = consumable("abyss_chainbomb", "chain_bomb", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_ARTSCROLL = consumable("abyss_artscroll", "art_scroll", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSS_SKILLRESET = consumable("abyss_skillreset", "skill_reset", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_LLEAP = consumable("abyss_lleap", "leap", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_GOLDMIST = consumable("abyss_goldmist", "luck_rain", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_LEECHCANDY = consumable("abyss_leechcandy", "gum_leech", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_DEPTHBOMB = consumable("abyss_depthbomb", "depth_bomb", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_EAGLE = consumable("abyss_eagle", "eagle", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_OVERCHARGE = consumable("abyss_overcharge", "overcharge", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_PICKVEIN = consumable("abyss_pickvein", "haste2", Rarity.COMMON);
    public static final DeferredItem<Item> ABYSS_GODSPEED = consumable("abyss_godspeed", "godspeed", Rarity.UNCOMMON);
    // --- アクティブ道具 (右クリック発動・使い捨て) ---
    public static final DeferredItem<Item> ABYSS_BREACHER = consumable("abyss_breacher", "breacher", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_NETSNARE = consumable("abyss_netsnare", "netsnare", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_REAPPLE = consumable("abyss_reapple", "reapple", Rarity.EPIC);
    public static final DeferredItem<Item> ABYSS_SHARDFLING = consumable("abyss_shardfling", "shard_fling", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SOUNDBELL = consumable("abyss_soundbell", "bell_collect", Rarity.UNCOMMON);
    public static final DeferredItem<Item> ABYSS_CAREBOX = consumable("abyss_carebox", "care_box", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_WARDRUMS = consumable("abyss_wardrums", "war_drums", Rarity.RARE);
    // --- 職業必殺の巻物 (8種: 右クリックでその型の必殺技を即発動) ---
    public static final DeferredItem<Item> ABYSS_SCROLL_SAMURAI = consumable("abyss_scroll_samurai", "skill_demo::samurai", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SCROLL_MAGE = consumable("abyss_scroll_mage", "skill_demo::mage", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SCROLL_NINJA = consumable("abyss_scroll_ninja", "skill_demo::ninja", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SCROLL_PALADIN = consumable("abyss_scroll_paladin", "skill_demo::paladin", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SCROLL_GUNNER = consumable("abyss_scroll_gunner", "skill_demo::gunner", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SCROLL_BERSERKER = consumable("abyss_scroll_berserker", "skill_demo::berserker", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SCROLL_MONK = consumable("abyss_scroll_monk", "skill_demo::monk", Rarity.RARE);
    public static final DeferredItem<Item> ABYSS_SCROLL_ARCHER = consumable("abyss_scroll_archer", "skill_demo::archer", Rarity.RARE);

    /** 新素材 (第3弾16種・レシピの核として消耗品に繋がる) */
    public static final DeferredItem<Item> ABYSS_TEARDROP = ITEMS.registerSimpleItem("abyss_teardrop",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_EMBER = ITEMS.registerSimpleItem("abyss_ember",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_FROST_PRISM = ITEMS.registerSimpleItem("abyss_frost_prism",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_MOONSTONE = ITEMS.registerSimpleItem("abyss_moonstone",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ABYSS_BONE_CHARM = ITEMS.registerSimpleItem("abyss_bone_charm",
            () -> new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> ABYSS_BLOODROOT = ITEMS.registerSimpleItem("abyss_bloodroot",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_THUNDER_THREAD = ITEMS.registerSimpleItem("abyss_thunder_thread",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ABYSS_VENOM_SAC = ITEMS.registerSimpleItem("abyss_venom_sac",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_ASH_POWDER = ITEMS.registerSimpleItem("abyss_ash_powder",
            () -> new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> ABYSS_VOID_SPLINTER = ITEMS.registerSimpleItem("abyss_void_splinter",
            () -> new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<Item> ABYSS_GOLD_VEIN = ITEMS.registerSimpleItem("abyss_gold_vein",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_IRON_ROSE = ITEMS.registerSimpleItem("abyss_iron_rose",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_NIGHT_PEARL = ITEMS.registerSimpleItem("abyss_night_pearl",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ABYSS_LAVA_PEARL = ITEMS.registerSimpleItem("abyss_lava_pearl",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> ABYSS_MIST_WISP = ITEMS.registerSimpleItem("abyss_mist_wisp",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ABYSS_STAR_SEED = ITEMS.registerSimpleItem("abyss_star_seed",
            () -> new Item.Properties().rarity(Rarity.RARE));

    /** 魂の欠片圧縮 (9個→袋→包→核。欠片のインベントリを整える) */
    public static final DeferredItem<Item> SOUL_SHARD_POUCH = ITEMS.registerSimpleItem("soul_shard_pouch",
            () -> new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SOUL_SHARD_BALE = ITEMS.registerSimpleItem("soul_shard_bale",
            () -> new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> SOUL_SHARD_CORE = ITEMS.registerSimpleItem("soul_shard_core",
            () -> new Item.Properties().rarity(Rarity.EPIC));

    /** 奥義の断片 (武器種ごと41種。断片+古代石+欠片2でその武器を完成できる) */
    public static final java.util.Map<GameData.WeaponStyle, DeferredItem<Item>> ART_SHARDS = buildArtShards();

    private static java.util.Map<GameData.WeaponStyle, DeferredItem<Item>> buildArtShards() {
        var map = new java.util.LinkedHashMap<GameData.WeaponStyle, DeferredItem<Item>>();
        for (GameData.WeaponStyle style : GameData.WeaponStyle.values()) {
            var shardId = "art_shard_" + style.id;
            map.put(style, ITEMS.registerSimpleItem(shardId,
                    () -> new Item.Properties().rarity(style == GameData.WeaponStyle.SWORD ? Rarity.COMMON
                            : Rarity.UNCOMMON)));
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    /** 欠片のランダム1個 (層主・番人の特別報酬用) */
    public static ItemStack randomArtShard(net.minecraft.util.RandomSource rand) {
        var styles = GameData.WeaponStyle.values();
        var style = styles[rand.nextInt(styles.length)];
        return new ItemStack(ART_SHARDS.get(style).get());
    }

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
    /** 深淵の釣竿・鋏 (生活用。耐久高め) */
    public static final DeferredItem<Item> ABYSS_FISHING_ROD = ITEMS.registerItem("abyss_fishing_rod",
            props -> new net.minecraft.world.item.FishingRodItem(
                    props.durability(512).rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<Item> ABYSS_SHEARS = ITEMS.registerItem("abyss_shears",
            props -> new net.minecraft.world.item.ShearsItem(
                    props.durability(1024).rarity(Rarity.UNCOMMON).fireResistant()));
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
                        output.accept(ABYSS_GREATSWORD.get());
                        output.accept(ABYSS_TWIN_DAGGER.get());
                        output.accept(ABYSS_WAR_HORN.get());
                        output.accept(ABYSS_WIND_BLADE.get());
                        output.accept(ABYSS_EMBER_FLAIL.get());
                        output.accept(ABYSS_ASTRAL_LANCE.get());
                        output.accept(ABYSS_TIDE_TRIDENT.get());
                        output.accept(ABYSS_MOON_MACE.get());
                        output.accept(ABYSS_DEPTH_GUN.get());
                        output.accept(ABYSS_VENOM_WHIP.get());
                        output.accept(ABYSS_FLAME_GRIMOIRE.get());
                        output.accept(ABYSS_RIFT_BLADE.get());
                        output.accept(ABYSS_METEOR_LANCE.get());
                        output.accept(ABYSS_ECHO_KATANA.get());
                        output.accept(ABYSS_HOWLING_CLAW.get());
                        output.accept(ABYSS_PICKAXE.get());
                        output.accept(ABYSS_TOOL_AXE.get());
                        output.accept(ABYSS_SHOVEL.get());
                        output.accept(ABYSS_HOE.get());
                        output.accept(ABYSS_FISHING_ROD.get());
                        output.accept(ABYSS_SHEARS.get());
                        output.accept(ABYSS_HELM.get());
                        output.accept(ABYSS_CHEST.get());
                        output.accept(ABYSS_LEGS.get());
                        output.accept(ABYSS_BOOTS.get());
                        for (var piece : SET_PIECES.values()) output.accept(piece.get());
                        output.accept(SOUL_SHARD_POUCH.get());
                        output.accept(SOUL_SHARD_BALE.get());
                        output.accept(SOUL_SHARD_CORE.get());
                        for (var shard : ART_SHARDS.values()) output.accept(shard.get());
                        output.accept(ABYSS_TEARDROP.get());
                        output.accept(ABYSS_EMBER.get());
                        output.accept(ABYSS_FROST_PRISM.get());
                        output.accept(ABYSS_MOONSTONE.get());
                        output.accept(ABYSS_BONE_CHARM.get());
                        output.accept(ABYSS_BLOODROOT.get());
                        output.accept(ABYSS_THUNDER_THREAD.get());
                        output.accept(ABYSS_VENOM_SAC.get());
                        output.accept(ABYSS_ASH_POWDER.get());
                        output.accept(ABYSS_VOID_SPLINTER.get());
                        output.accept(ABYSS_GOLD_VEIN.get());
                        output.accept(ABYSS_IRON_ROSE.get());
                        output.accept(ABYSS_NIGHT_PEARL.get());
                        output.accept(ABYSS_LAVA_PEARL.get());
                        output.accept(ABYSS_MIST_WISP.get());
                        output.accept(ABYSS_STAR_SEED.get());
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
                        output.accept(ABYSS_BERSERK.get());
                        output.accept(ABYSS_BULWARK.get());
                        output.accept(ABYSS_BOMB_H.get());
                        output.accept(ABYSS_FORTUNE.get());
                        output.accept(ABYSS_RATION.get());
                        output.accept(ABYSS_FLASK.get());
                        output.accept(ABYSS_GUT.get());
                        output.accept(ABYSS_GRANDFEAST.get());
                        output.accept(ABYSS_REGENX.get());
                        output.accept(ABYSS_STRX.get());
                        output.accept(ABYSS_RESX.get());
                        output.accept(ABYSS_ABSX.get());
                        output.accept(ABYSS_STRIDE.get());
                        output.accept(ABYSS_NIGHTWARD.get());
                        output.accept(ABYSS_SALAMANDER.get());
                        output.accept(ABYSS_PEARLDIVE.get());
                        output.accept(ABYSS_VEILNOTE.get());
                        output.accept(ABYSS_PURITY.get());
                        output.accept(ABYSS_CHAINBOMB.get());
                        output.accept(ABYSS_ARTSCROLL.get());
                        output.accept(ABYSS_SKILLRESET.get());
                        output.accept(ABYSS_LLEAP.get());
                        output.accept(ABYSS_GOLDMIST.get());
                        output.accept(ABYSS_LEECHCANDY.get());
                        output.accept(ABYSS_DEPTHBOMB.get());
                        output.accept(ABYSS_EAGLE.get());
                        output.accept(ABYSS_OVERCHARGE.get());
                        output.accept(ABYSS_PICKVEIN.get());
                        output.accept(ABYSS_GODSPEED.get());
                        output.accept(ABYSS_SCROLL_SAMURAI.get());
                        output.accept(ABYSS_SCROLL_MAGE.get());
                        output.accept(ABYSS_SCROLL_NINJA.get());
                        output.accept(ABYSS_SCROLL_PALADIN.get());
                        output.accept(ABYSS_SCROLL_GUNNER.get());
                        output.accept(ABYSS_SCROLL_BERSERKER.get());
                        output.accept(ABYSS_SCROLL_MONK.get());
                        output.accept(ABYSS_SCROLL_ARCHER.get());
                        output.accept(ABYSS_BREACHER.get());
                        output.accept(ABYSS_NETSNARE.get());
                        output.accept(ABYSS_REAPPLE.get());
                        output.accept(ABYSS_SHARDFLING.get());
                        output.accept(ABYSS_SOUNDBELL.get());
                        output.accept(ABYSS_CAREBOX.get());
                        output.accept(ABYSS_WARDRUMS.get());
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

    /** 消耗品かどうか (効果種を返す。消耗品でなければ null) */
    public static String consumableKindOf(Item item) {
        return item instanceof AbyssConsumable c ? c.consumableKind() : null;
    }

    /** 消耗品のランダム1個 (ドロップ用) */
    private static final java.util.List<DeferredItem<Item>> DROP_CONSUMABLES = java.util.List.of(
            ABYSS_HEAL_S, ABYSS_HEAL_M, ABYSS_HEAL_L, ABYSS_SOUL_S, ABYSS_SOUL_L,
            ABYSS_PWR, ABYSS_SPD, ABYSS_IRN, ABYSS_RGN, ABYSS_ABS,
            ABYSS_FIR, ABYSS_OWL, ABYSS_FTH, ABYSS_LCK,
            ABYSS_BOMB_F, ABYSS_BOMB_T, ABYSS_BOMB_I,
            ABYSS_TELE_A, ABYSS_TELE_S, ABYSS_CURE,
            ABYSS_MAGNET, ABYSS_REVEAL, ABYSS_FEAST, ABYSS_SMOKE,
            ABYSS_BERSERK, ABYSS_BULWARK, ABYSS_BOMB_H, ABYSS_FORTUNE, ABYSS_RATION,
            ABYSS_FLASK, ABYSS_GUT, ABYSS_GRANDFEAST, ABYSS_LEECHCANDY,
            ABYSS_STRIDE, ABYSS_PICKVEIN, ABYSS_GODSPEED, ABYSS_PURITY);

    public static ItemStack randomConsumable(net.minecraft.util.RandomSource rand) {
        return new ItemStack(DROP_CONSUMABLES.get(rand.nextInt(DROP_CONSUMABLES.size())).get());
    }

        /** 生活ツール判定 (採掘属性の疾走効果用) */
    public static boolean isTool(Item item) {
        return item == ABYSS_PICKAXE.get() || item == ABYSS_TOOL_AXE.get()
                || item == ABYSS_SHOVEL.get() || item == ABYSS_HOE.get();
    }

    /** 手持ちItem -> 武器種の逆引き表 (登録後に1度だけ構築) */
    private static java.util.Map<net.minecraft.world.item.Item, GameData.WeaponStyle> STYLE_INDEX;

    /** 手持ちItem -> 武器種 の逆引き */
    public static GameData.WeaponStyle styleOf(net.minecraft.world.item.Item item) {
        var index = STYLE_INDEX;
        if (index == null) {
            var map = new java.util.HashMap<net.minecraft.world.item.Item, GameData.WeaponStyle>();
            for (GameData.WeaponStyle style : GameData.WeaponStyle.values()) {
                map.put(itemOf(style), style);
            }
            index = map;
            STYLE_INDEX = index;
        }
        return index.get(item);
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
            case GREATSWORD -> ABYSS_GREATSWORD.get();
            case TWIN_DAGGER -> ABYSS_TWIN_DAGGER.get();
            case WAR_HORN -> ABYSS_WAR_HORN.get();
            case WIND_BLADE -> ABYSS_WIND_BLADE.get();
            case EMBER_FLAIL -> ABYSS_EMBER_FLAIL.get();
            case ASTRAL_LANCE -> ABYSS_ASTRAL_LANCE.get();
            case TIDE_TRIDENT -> ABYSS_TIDE_TRIDENT.get();
            case MOON_MACE -> ABYSS_MOON_MACE.get();
            case DEPTH_GUN -> ABYSS_DEPTH_GUN.get();
            case VENOM_WHIP -> ABYSS_VENOM_WHIP.get();
            case FLAME_GRIMOIRE -> ABYSS_FLAME_GRIMOIRE.get();
            case RIFT_BLADE -> ABYSS_RIFT_BLADE.get();
            case METEOR_LANCE -> ABYSS_METEOR_LANCE.get();
            case ECHO_KATANA -> ABYSS_ECHO_KATANA.get();
            case HOWLING_CLAW -> ABYSS_HOWLING_CLAW.get();
        };
    }
}
