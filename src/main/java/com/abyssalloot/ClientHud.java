package com.abyssalloot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * クライアント専用: 魂ゲージ・層・レベルのHUD.
 * 数値はサーバーからの HudSync で更新される (未受信時は表示しない).
 */
public final class ClientHud {
    private ClientHud() {}

    private static volatile float soul = -1;
    private static volatile int floor;
    private static volatile int level;
    private static volatile int kills;
    private static volatile int combo;
    private static volatile int attrPoints;
    private static volatile int portalOpen;

    public static void update(float s, int f, int lv, int k, int c, int ap, int po) {
        soul = s;
        floor = f;
        level = lv;
        kills = k;
        combo = c;
        attrPoints = ap;
        portalOpen = po;
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "hud"),
                ClientHud::render));
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (soul < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int bw = 120;
        int x0 = w / 2 - bw / 2;
        int y0 = h - 48;
        // 背景
        g.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + 9, 0x80000000);
        // 魂ゲージ (100で発光色に)
        int fill = (int) (bw * Math.min(1, soul / 100f));
        int color = soul >= 100 ? 0xFFE14AFF : 0xFFB14BFF;
        g.fill(x0, y0, x0 + fill, y0 + 8, color);
        var font = mc.font;
        g.drawString(font, String.format("魂%.0f/100 第%d層 Lv%d", soul, floor, level), x0, y0 - 11, 0xFFFFFFFF);
        if (combo >= 10) {
            g.drawString(font, combo + "連撃！", x0 + bw + 6, y0 - 2, 0xFFFFD23C);
        }
        g.drawString(font, "撃破" + kills, x0, y0 + 12, 0xFF9FB0C9);
        // 未配分の属性点と転移門の開放を見逃さないよう表示
        if (attrPoints > 0) {
            g.drawString(font, "属性+" + attrPoints + " (G→ステータス)", x0 + bw + 6, y0 + 12, 0xFF7CFFB2);
        }
        if (portalOpen != 0) {
            g.drawString(font, "◆転移門OPEN (G→次の層へ)", x0, y0 - 22, 0xFF7CFFB2);
        }
    }
}
