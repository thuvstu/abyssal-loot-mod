package com.abyssalloot;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Abyssal Loot — 原作ブラウザゲームの NeoForge 再現MOD.
 * v0.1.0: コマンド+右クリック操作で遊べるMVP.
 *  - 8職業 / 41武器種 / 武器奥義 / 8呪印 / 層進行 / 魂ゲージ / 回避見切り
 */
@Mod(AbyssalLootMod.MODID)
public class AbyssalLootMod {
    public static final String MODID = "abyssalloot";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AbyssalLootMod(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.TYPES.register(modEventBus);
        ModEntities.attributes(modEventBus);
        VanillaLinks.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(ModPackets::register);

        NeoForge.EVENT_BUS.register(ModCommands.class);
        NeoForge.EVENT_BUS.register(CombatHandler.class);
        // 撃破処理 (KillHandler) は CombatHandler から分離された際に登録が漏れていた。
        // 未登録だと討伐数・戦利品・層クリア判定が一切動かないため必ず登録する。
        NeoForge.EVENT_BUS.register(KillHandler.class);
        NeoForge.EVENT_BUS.register(LootHandler.class);
        NeoForge.EVENT_BUS.register(WeaponHitHandler.class);
        NeoForge.EVENT_BUS.register(SkillHandler.class);
        NeoForge.EVENT_BUS.register(VanillaLinks.class);
        // 転移門の右クリック進行とログアウト時のチャージ掃除
        NeoForge.EVENT_BUS.register(TransferGate.class);
        // ダメージ表示のアーマースタンドを誤操作しないようにする
        NeoForge.EVENT_BUS.register(DamageNumbers.class);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ClientKeys.init(modEventBus);
            ClientHud.init(modEventBus);
            ClientGui.init(modEventBus);
        }
    }
}
