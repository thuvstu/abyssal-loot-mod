package com.abyssalloot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアントキー -> サーバーの操作パケット (art/skill/dash).
 */
public final class ModPackets {
    private ModPackets() {}

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
    }

    /** サーバー→クライアント: HUD表示用ステータス (1秒ごと) */
    public record HudSync(float soul, int floor, int level, int kills, int combo)
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
                },
                buf -> new HudSync(buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        /** クライアント側で受信 (ClientHudへ反映) */
        public static void handle(HudSync payload, IPayloadContext ctx) {
            ctx.enqueueWork(() -> ClientHud.update(
                    payload.soul(), payload.floor(), payload.level(), payload.kills(), payload.combo()));
        }
    }

    private static void handle(ActPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                switch (payload.kind()) {
                    case "art" -> CombatHandler.tryArt(sp);
                    case "skill" -> CombatHandler.trySkill(sp);
                    case "dash" -> CombatHandler.dash(sp);
                }
            }
        });
    }
}
