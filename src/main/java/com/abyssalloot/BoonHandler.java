package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 祝福3択の提示・決定。GUI (BoonSelectScreen) とチャットボタンの両方から選べる.
 * 原作 engine.ts drainRewards 相当: 選ぶまで提示が消えない (逐次開示).
 * CombatHandler から分離。
 */
public final class BoonHandler {
    private BoonHandler() {}

    /**
     * 祝福3択の提示。原作 engine.ts drainRewards 相当:
     * 未選択の祝福 (PENDING_BOONS) がある間、1回ずつ選択画面 (GUI+チャット) で提示し、
     * 選ぶまで消えない (層移動などで消される前に PENDING_BOONS が保存される)。
     */
    public static void offerBoons(ServerPlayer player) {
        if (RunManager.isCovenant(player)) return;
        var rand = player.getRandom();
        var pick = Boons.rollBoons(rand::nextDouble, 3);
        var ids = new java.util.ArrayList<String>();
        var msg = Component.literal("§e◆ 祝福を選べ (画面のボタンかクリック): ");
        for (var b : pick) {
            ids.add(b.id());
            var btn = Component.literal("§6[" + b.name() + " " + b.desc() + "] ")
                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/abyss boon " + b.id()))
                            .withUnderlined(true));
            msg.append(btn);
        }
        player.setData(ModAttachments.OFFER.get(), String.join(",", ids));
        player.sendSystemMessage(msg);
        presentBoonGui(player);
    }

    /** 未選択分から祝福3択を提示する (既に提示中なら何もしない)。盟約モードでは無視 */
    static void drainPendingBoons(ServerPlayer player) {
        if (RunManager.isCovenant(player)) return;
        if (RunManager.pendingBoonsOf(player) <= 0
                || !player.getData(ModAttachments.OFFER.get()).isBlank()) return;
        offerBoons(player);
    }

    /** 選択GUI + フラッシュHUDをクライアントへ送る */
    private static void presentBoonGui(ServerPlayer player) {
        String raw = player.getData(ModAttachments.OFFER.get());
        if (raw == null || raw.isBlank()) return;
        var ids = new java.util.ArrayList<String>(java.util.List.of(raw.split(",")));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ModPackets.BoonSync(ids, RunManager.pendingBoonsOf(player)));
        CombatHandler.sendHud(player);
    }

    /** 未選択の祝福を再提示 (上書きしない。GUIを開く・チャットにもボタンを出す) */
    public static void resendBoonOffer(ServerPlayer player) {
        String raw = player.getData(ModAttachments.OFFER.get());
        if (raw != null && !raw.isBlank()) {
            var msg = Component.literal("§e祝福を選べ (再提示): ");
            for (String id : raw.split(",")) {
                var b = Boons.boonById(id);
                if (b == null) continue;
                var btn = Component.literal("§6[" + b.name() + " " + b.desc() + "] ")
                        .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/abyss boon " + b.id()))
                                .withUnderlined(true));
                msg.append(btn);
            }
            player.sendSystemMessage(msg);
            presentBoonGui(player);
            return;
        }
        // 提示分が無い場合は未選択残数から再ロール (startRun・startTrue で OFFER を消去済みでも復元できる)
        if (RunManager.pendingBoonsOf(player) > 0 && !RunManager.isCovenant(player)) {
            offerBoons(player);
            return;
        }
        player.sendSystemMessage(Component.literal("§7選択待ちの祝福は無い"));
    }

    /**
     * 祝福の決定 (GUI・コマンド共通)。提示中の3択からの選択のみ有効。
     * 決定で未選択カウントを消費し、まだ残っていれば次の3択を提示する (原作 drainRewards の逐次開示)。
     */
    public static boolean chooseBoon(ServerPlayer player, String id) {
        var def = Boons.boonById(id);
        if (def == null) return false;
        String offer = player.getData(ModAttachments.OFFER.get());
        if (offer == null || !java.util.List.of(offer.split(",")).contains(id)) {
            player.sendSystemMessage(Component.literal("§c今はその祝福を選べない (レベルアップ時の3択から選んでね)"));
            return false;
        }
        RunManager.addBoon(player, id);
        player.setData(ModAttachments.OFFER.get(), "");
        int left = Math.max(0, RunManager.pendingBoonsOf(player) - 1);
        RunManager.setPendingBoons(player, left);
        GameData.HeroClass cls = GameData.HeroClass.byId(
                player.getData(ModAttachments.HERO_CLASS.get()));
        StatCalc.applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()));
        player.sendSystemMessage(Component.literal(
                "§e祝福を得た: " + def.name() + " (" + def.desc() + ")"
                        + (left > 0 ? " — 残り" + left + "件の選択待ち" : "")));
        CombatHandler.sendHud(player);
        if (left > 0) drainPendingBoons(player);
        return true;
    }
}
