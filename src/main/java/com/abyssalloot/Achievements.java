package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 実績付与 (重複なし・欠片報酬)。撃破・層進行・鍛造など各所から呼ばれる。
 * CombatHandler から分離。
 */
public final class Achievements {
    private Achievements() {}

    /** 実績付与。レベルアップ音で確実な存在感を出す。バニラ進捗 (data/abyssalloot/advancement/abyss/) も連動して付与される */
    public static void grantAch(ServerPlayer player, String id, String title, int shards) {
        String raw = player.getData(ModAttachments.ACH.get());
        var have = new java.util.HashSet<String>();
        if (raw != null && !raw.isBlank()) have.addAll(java.util.List.of(raw.split(",")));
        if (have.contains(id)) return;
        have.add(id);
        player.setData(ModAttachments.ACH.get(), String.join(",", have));
        player.sendSystemMessage(Component.literal("§e★ 実績解除: " + title + " (欠片+" + shards + ")"));
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS,
                0.7f, 1.5f);
        if (shards > 0) {
            LootHandler.giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get(), shards));
        }
        awardVanillaAdvancement(player, id);
    }

    /**
     * 対応するバニラ進捗をプログラム的に付与する (criteria=minecraft:impossible は自然発火しない).
     * 進捗画面・バニラ式トーストに表示され、実績がバニラ UI の一部として見えるようになる。
     * ルート (abyss/root) も同時に付与して系譜を開く。進捗が無い環境では何もしない (安全側)。
     */
    private static void awardVanillaAdvancement(ServerPlayer player, String id) {
        var server = player.level().getServer();
        if (server == null) return;
        try {
            var manager = server.getAdvancements();
            var root = manager.get(net.minecraft.resources.Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "abyss/root"));
            if (root != null) player.getAdvancements().award(root, "granted");
            var holder = manager.get(net.minecraft.resources.Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "abyss/" + id));
            if (holder != null) player.getAdvancements().award(holder, "granted");
        } catch (Exception ignored) {
        }
    }

    /** IDのみで付与する1行ヘルパー (タイトル・欠片は定義から自動解決)。未定義IDは無視 */
    public static void grant(ServerPlayer player, String id) {
        var a = GameData.achievementById(id);
        if (a != null) grantAch(player, id, a.title(), GameData.shardsForReward(a.reward()));
    }
}
