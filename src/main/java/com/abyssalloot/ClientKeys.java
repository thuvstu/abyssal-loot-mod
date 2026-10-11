package com.abyssalloot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * クライアント専用: R=武器奥義 / F=必殺技 / C=回避ダッシュ.
 * サーバー側では決して読み込まれない (Distチェック経由でのみ初期化).
 */
public final class ClientKeys {
    private ClientKeys() {}

    private static KeyMapping artKey;
    private static KeyMapping skillKey;
    private static KeyMapping dashKey;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            var category = new KeyMapping.Category(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            AbyssalLootMod.MODID, "main"));
            event.registerCategory(category);
            artKey = new KeyMapping("key.abyssalloot.art", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category);
            skillKey = new KeyMapping("key.abyssalloot.skill", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, category);
            dashKey = new KeyMapping("key.abyssalloot.dash", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category);
            event.register(artKey);
            event.register(skillKey);
            event.register(dashKey);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn == null) return;
            while (artKey != null && artKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("art")));
            }
            while (skillKey != null && skillKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("skill")));
            }
            while (dashKey != null && dashKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("dash")));
            }
        });
    }
}
