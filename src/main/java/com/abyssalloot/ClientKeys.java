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
 * クライアント専用: R=武器奥義 / V=必殺技 / X=回避ダッシュ / G=操作ハブ / N=次の層へ / C=特殊スキル.
 * 右クリック=武器小技 (サーバー側イベントで発動、キー不要)。
 * E(インベントリ)・Q(ドロップ)・F(持ち替え)・C と被らない配置。
 * サーバー側では決して読み込まれない (Distチェック経由でのみ初期化).
 */
public final class ClientKeys {
    private ClientKeys() {}

    private static KeyMapping artKey;
    private static KeyMapping skillKey;
    private static KeyMapping dashKey;
    private static KeyMapping hubKey;
    private static KeyMapping nextKey;
    private static KeyMapping subKey;
    private static KeyMapping quickKey;
    private static KeyMapping vaultKey;

    public static void init(IEventBus modBus) {
        registerKeyMappings(modBus);
        registerKeyTickHandler();
        registerNocturneInput();
    }

    /* ---------------- 夜想曲ノクターン: 数列入力モード ---------------- */

    /** サーバーから通知される入力モード (NocturneSync) */
    private static volatile boolean nocturneInput = false;

    public static void setNocturneInput(boolean on) {
        nocturneInput = on;
    }

    private static void sendAct(String kind) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket(kind)));
    }

    private static boolean holdingNocturne() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getMainHandItem().is(ModItems.ABYSS_NOCTURNE.get());
    }

    /**
     * 入力モード中は数字キーを数列入力として送り、ホットバー切替・ドロップ・持ち替え・
     * インベントリ・左クリックを止める (数字キーがホットバー切替に吸われて入力できない問題の対策)。
     * ClientTickEvent.Pre は Minecraft#handleKeybinds より前に走るため、ここで押下を消費すれば切替は起きない。
     */
    private static void registerNocturneInput() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> {
            if (!nocturneInput) return;
            var mc = Minecraft.getInstance();
            if (mc.player == null) {
                nocturneInput = false;
                return;
            }
            for (KeyMapping k : mc.options.keyHotbarSlots) {
                while (k.consumeClick()) { /* 数字は InputEvent.Key 側で送る */ }
            }
            while (mc.options.keyDrop.consumeClick()) { }
            while (mc.options.keySwapOffhand.consumeClick()) { }
            while (mc.options.keyInventory.consumeClick()) { }
            while (mc.options.keyAttack.consumeClick()) { }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.Key event) -> {
            if (!nocturneInput || event.getAction() != GLFW.GLFW_PRESS) return;
            if (Minecraft.getInstance().screen != null) return;
            int key = event.getKey();
            int digit = -1;
            if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) digit = key - GLFW.GLFW_KEY_0;
            else if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) digit = key - GLFW.GLFW_KEY_KP_0;
            if (digit >= 0) sendAct("noct_d" + digit);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) -> {
            if (nocturneInput) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) -> {
            if (nocturneInput && event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening event) -> {
            if (nocturneInput && event.getNewScreen()
                    instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                event.setCanceled(true);
            }
        });
        // 空振り: 5m以内の最寄りの敵を狙う律動の追撃 (サーバーでCT判定)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) -> {
            if (!nocturneInput && holdingNocturne()) sendAct("noct_swing");
        });
    }

    /** キーマッピングの登録 (R/V/X/G/N/C) */
    private static void registerKeyMappings(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            var category = new KeyMapping.Category(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            AbyssalLootMod.MODID, "main"));
            event.registerCategory(category);
            artKey = new KeyMapping("key.abyssalloot.art", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category);
            skillKey = new KeyMapping("key.abyssalloot.skill", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category);
            dashKey = new KeyMapping("key.abyssalloot.dash", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, category);
            hubKey = new KeyMapping("key.abyssalloot.hub", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category);
            nextKey = new KeyMapping("key.abyssalloot.next", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category);
            subKey = new KeyMapping("key.abyssalloot.sub", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category);
            quickKey = new KeyMapping("key.abyssalloot.quick", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, category);  // F は持ち手の入れ替えと重なるので Z
            vaultKey = new KeyMapping("key.abyssalloot.vault", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category);
            event.register(artKey);
            event.register(skillKey);
            event.register(dashKey);
            event.register(hubKey);
            event.register(nextKey);
            event.register(subKey);
            event.register(quickKey);
            event.register(vaultKey);
        });
    }

    /** キー入力→サーバー送信 (1tickに1回だけ送る。キーリピート対策) */
    private static void registerKeyTickHandler() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn == null) return;
            // キーリピート (長押し) による ActPacket の連送を防ぐため 1tick に 1 回だけ送る
            if (artKey != null && artKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("art")));
            }
            if (skillKey != null && skillKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("skill")));
            }
            if (dashKey != null && dashKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("dash")));
            }
            if (hubKey != null && hubKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("hub")));
            }
            if (nextKey != null && nextKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("next")));
            }
            if (subKey != null && subKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("sub")));
            }
            if (quickKey != null && quickKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("quick")));
            }
            if (vaultKey != null && vaultKey.consumeClick()) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("vault")));
            }
        });
    }
}
