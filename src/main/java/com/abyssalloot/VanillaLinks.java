package com.abyssalloot;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * バニラとの接続点を集めたクラス.
 *  - 行商人の取引: エメラルド ⇄ 魂の欠片・深淵の消耗品 (行商人が深淵の痕跡を商う)
 *  - グローバルルートモディファイア: バニラの宝箱に魂の欠片を混入 (AbyssShardLootModifier)
 * 追加のバニラ統合 (醸造・錬金・構造物連動など) もここに置く。
 */
public final class VanillaLinks {
    private VanillaLinks() {}

    /** GLM シリアライザの登録 (data/abyssalloot/loot_modifiers/*.json の type から参照される) */
    public static final DeferredRegister<MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>>
            LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, AbyssalLootMod.MODID);

    public static final DeferredHolder<MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>,
            MapCodec<AbyssShardLootModifier>> SHARD_LOOT =
            LOOT_MODIFIER_SERIALIZERS.register("shard_loot", () -> AbyssShardLootModifier.CODEC);

    /**
     * 行商人の品揃えに深淵の痕跡を混ぜる (ゲームバス・リロード時に発火).
     * エメラルド→欠片は高めの価格 (欠片はランの通貨のため、バニラ側からの入手導線は狭く)。
     * レア取引で欠片を深淵の消耗品に両替できる (ラン外でも消耗品が活きる)。
     */
    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        // 普通の取引: エメラルド16 → 魂の欠片1 (2回まで)
        event.getGenericTrades().add(new BasicItemListing(
                16, new net.minecraft.world.item.ItemStack(ModItems.SOUL_SHARD.get()), 2, 4, 1.0f));
        // レアな取引: 欠片2 → 回復薬M (1回)
        event.getRareTrades().add(new BasicItemListing(
                new net.minecraft.world.item.ItemStack(ModItems.SOUL_SHARD.get(), 2),
                new net.minecraft.world.item.ItemStack(ModItems.ABYSS_HEAL_M.get()), 1, 6, 1.0f));
        // レアな取引: 欠片3 → 千里眼の霧薬 (1回)
        event.getRareTrades().add(new BasicItemListing(
                new net.minecraft.world.item.ItemStack(ModItems.SOUL_SHARD.get(), 3),
                new net.minecraft.world.item.ItemStack(ModItems.ABYSS_REVEAL.get()), 1, 6, 1.0f));
    }
}
