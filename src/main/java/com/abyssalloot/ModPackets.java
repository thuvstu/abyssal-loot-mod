package com.abyssalloot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアントキー -> サーバーの操作パケット (art/skill/dash/forge/boon)。
 */
public final class ModPackets {
    private ModPackets() {}

    /** 鍛造画面の合成ボタン (中身なし。開いているメニューのスロットで合成) */
    public record ForgePacket() implements CustomPacketPayload {
        public static final Type<ForgePacket> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "forge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ForgePacket> CODEC = StreamCodec.of(
                (buf, p) -> {},
                buf -> new ForgePacket());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActPacket(String kind) implements CustomPacketPayload {
        public static final Type<ActPacket> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActPacket> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.kind(), 16),
                buf -> new ActPacket(buf.readUtf(16)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("1");
        reg.playToServer(ActPacket.TYPE, ActPacket.CODEC, ModPackets::handle);
        reg.playToClient(HudSync.TYPE, HudSync.CODEC, HudSync::handle);
        reg.playToServer(ForgePacket.TYPE, ForgePacket.CODEC, ModPackets::handleForge);
        reg.playToServer(ShopPacket.TYPE, ShopPacket.CODEC, ModPackets::handleShop);
        reg.playToServer(ComparePacket.TYPE, ComparePacket.CODEC, ModPackets::handleCompare);
        reg.playToClient(CompareSync.TYPE, CompareSync.CODEC, CompareSync::handle);
        reg.playToClient(StatusSync.TYPE, StatusSync.CODEC, StatusSync::handle);
        reg.playToServer(AttrPacket.TYPE, AttrPacket.CODEC, ModPackets::handleAttr);
        reg.playToClient(HubSync.TYPE, HubSync.CODEC, HubSync::handle);
        reg.playToClient(BoonSync.TYPE, BoonSync.CODEC, BoonSync::handle);
        reg.playToServer(BoonPacket.TYPE, BoonPacket.CODEC, ModPackets::handleBoon);
    }

    /** 商店GUIの購入ボタン (1-3) */
    public record ShopPacket(int index) implements CustomPacketPayload {
        public static final Type<ShopPacket> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "shop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopPacket> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.index()),
                buf -> new ShopPacket(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 装備比較GUIの「比較する」ボタン (中身なし。開いているメニューのスロットで計算) */
    public record ComparePacket() implements CustomPacketPayload {
        public static final Type<ComparePacket> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "compare"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ComparePacket> CODEC = StreamCodec.of(
                (buf, p) -> {},
                buf -> new ComparePacket());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeLines(RegistryFriendlyByteBuf buf, java.util.List<String> lines) {
        buf.writeVarInt(lines.size());
        for (String s : lines) buf.writeUtf(s, 512);
    }

    private static java.util.List<String> readLines(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        var out = new java.util.ArrayList<String>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(512));
        return out;
    }

    /** サーバー→クライアント: 装備比較の結果テキスト (A側/B側) */
    public record CompareSync(java.util.List<String> left, java.util.List<String> right)
            implements CustomPacketPayload {
        public static final Type<CompareSync> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "compare_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CompareSync> CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeLines(buf, p.left());
                    writeLines(buf, p.right());
                },
                buf -> new CompareSync(readLines(buf), readLines(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(CompareSync payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientGui.showCompare(payload.left(), payload.right()));
        }
    }

    /** サーバー→クライアント: ステータス画面の表示テキスト */
    public record StatusSync(java.util.List<String> lines) implements CustomPacketPayload {
        public static final Type<StatusSync> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatusSync> CODEC = StreamCodec.of(
                (buf, p) -> writeLines(buf, p.lines()),
                buf -> new StatusSync(readLines(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(StatusSync payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientGui.showStatus(payload.lines()));
        }
    }

    /** ステータスGUIの属性ボタン (0-4) */
    public record AttrPacket(int index) implements CustomPacketPayload {
        public static final Type<AttrPacket> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "attr"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AttrPacket> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.index()),
                buf -> new AttrPacket(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 祝福選択GUIの決定ボタン (祝福ID) */
    public record BoonPacket(String id) implements CustomPacketPayload {
        public static final Type<BoonPacket> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "boon_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoonPacket> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id(), 64),
                buf -> new BoonPacket(buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** サーバー→クライアント: 祝福の3択表示 (選択GUIを開く)。remaining = 未選択の残数 */
    public record BoonSync(java.util.List<String> choices, int remaining) implements CustomPacketPayload {
        public static final Type<BoonSync> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "boon_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoonSync> CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeLines(buf, p.choices());
                    buf.writeVarInt(p.remaining());
                },
                buf -> new BoonSync(readLines(buf), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(BoonSync payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientGui.showBoonChoices(payload.choices(), payload.remaining()));
        }
    }

    /** サーバー→クライアント: 操作ハブを開く (中身なし) */
    public record HubSync() implements CustomPacketPayload {
        public static final Type<HubSync> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "hub"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HubSync> CODEC = StreamCodec.of(
                (buf, p) -> {},
                buf -> new HubSync());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(HubSync payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientGui.openHub());
        }
    }

    /** サーバー→クライアント: HUD表示用ステータス (1秒ごと。CT中は0.1秒ごと) */
    public record HudSync(float soul, int floor, int level, int kills, int combo,
                          int attrPoints, int portalOpen, int pendingBoons,
                          float lightCd, float skillCd)
            implements CustomPacketPayload {
        public static final Type<HudSync> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HudSync> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeFloat(p.soul());
                    buf.writeVarInt(p.floor());
                    buf.writeVarInt(p.level());
                    buf.writeVarInt(p.kills());
                    buf.writeVarInt(p.combo());
                    buf.writeVarInt(p.attrPoints());
                    buf.writeVarInt(p.portalOpen());
                    buf.writeVarInt(p.pendingBoons());
                    buf.writeFloat(p.lightCd());
                    buf.writeFloat(p.skillCd());
                },
                buf -> new HudSync(buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readFloat(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        /** クライアント側で受信 (ClientHudへ反映) */
        public static void handle(HudSync payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientHud.update(
                    payload.soul(), payload.floor(), payload.level(), payload.kills(), payload.combo(),
                    payload.attrPoints(), payload.portalOpen(), payload.pendingBoons(),
                    payload.lightCd(), payload.skillCd()));
        }
    }

    private static void handle(ActPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                switch (payload.kind()) {
                    case "art" -> SkillHandler.tryArt(sp);
                    case "skill" -> SkillHandler.trySkill(sp);
                    case "dash" -> SkillHandler.dash(sp);
                    case "hub" -> net.neoforged.neoforge.network.PacketDistributor
                            .sendToPlayer(sp, new HubSync());
                    case "next" -> RunManager.tryNextFloor(sp);
                }
            }
        });
    }

    private static void handleAttr(AttrPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                if (StatCalc.addAttrPoint(sp, payload.index())) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                            new StatusSync(RunManager.statusLines(sp)));
                }
            }
        });
    }

    /** 祝福選択GUIの決定 (サーバー側で3択からの選択かを再検証) */
    private static void handleBoon(BoonPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                BoonHandler.chooseBoon(sp, payload.id());
            }
        });
    }

    private static void handleForge(ForgePacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp
                    && sp.containerMenu instanceof ForgeMenu fm) {
                RunManager.doMergeStacks(sp,
                        fm.getSlot(ForgeMenu.SLOT_WEAPON).getItem(),
                        fm.getSlot(ForgeMenu.SLOT_MATERIAL).getItem());
            }
        });
    }

    private static void handleShop(ShopPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp
                    && sp.containerMenu instanceof ShopMenu sm) {
                boolean before = sp.getData(ModAttachments.EVENT_DONE.get()) != 0;
                RunManager.doBuy(sp, payload.index());
                // 購入成功 (イベント消費) したら売約済み表示に更新
                if (!before && sp.getData(ModAttachments.EVENT_DONE.get()) != 0) {
                    sm.markSold(payload.index());
                }
            }
        });
    }

    private static void handleCompare(ComparePacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp
                    && sp.containerMenu instanceof CompareMenu cm) {
                var left = GearData.compareLines(sp, cm.getSlot(CompareMenu.SLOT_A).getItem());
                var right = GearData.compareLines(sp, cm.getSlot(CompareMenu.SLOT_B).getItem());
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new CompareSync(left, right));
            }
        });
    }
}
