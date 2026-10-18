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
 * 小技/必殺のCTは残存時のみ表示 (CT中はサーバーが0.1秒ごとに再送する).
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
    private static volatile int pendingBoons;
    private static volatile float lightCd;
    private static volatile float skillCd;
    private static volatile boolean relicPending;
    private static volatile boolean passivePending;

    public static void update(float s, int f, int lv, int k, int c, int ap, int po, int pb,
                              float lcd, float scd, int rp, int pp) {
        soul = s;
        floor = f;
        level = lv;
        kills = k;
        combo = c;
        attrPoints = ap;
        portalOpen = po;
        pendingBoons = pb;
        lightCd = lcd;
        skillCd = scd;
        relicPending = rp != 0;
        passivePending = pp != 0;
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
        // 左上に寄せる (ホットバーと被らないように)
        int x0 = 6;
        int y0 = 6;
        int bw = 120;
        // 背景
        g.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + 9, 0x80000000);
        // 魂ゲージ (100で発光色に)
        int fill = (int) (bw * Math.min(1, soul / 100f));
        int color = soul >= 100 ? 0xFFE14AFF : 0xFFB14BFF;
        g.fill(x0, y0, x0 + fill, y0 + 8, color);
        var font = mc.font;
        g.drawString(font, String.format("魂%.0f/100 第%d層 Lv%d", soul, floor, level), x0, y0 + 12, 0xFFFFFFFF);
        int y = y0 + 24;
        if (combo >= 10) {
            g.drawString(font, combo + "連撃！", x0, y, 0xFFFFD23C);
            y += 12;
        }
        g.drawString(font, "撃破" + kills, x0, y, 0xFF9FB0C9);
        y += 12;
        // 小技・必殺のCT (残存時のみ)
        if (lightCd > 0) {
            g.drawString(font, String.format("小技 %.1fs (右クリック)", lightCd), x0, y, 0xFF7CC8FF);
            y += 12;
        }
        if (skillCd > 0) {
            g.drawString(font, String.format("必殺 %.1fs (V)", skillCd), x0, y, 0xFFFFD23C);
            y += 12;
        }
        // 未配分の属性点と転移門の開放を見逃さないよう表示
        if (attrPoints > 0) {
            g.drawString(font, "属性+" + attrPoints + " (G→ステータス)", x0, y, 0xFF7CFFB2);
            y += 12;
        }
        // 未選択の祝福 (点滅して注意を促す)
        if (pendingBoons > 0 && (System.currentTimeMillis() / 400) % 2 == 0) {
            g.drawString(font, "◆祝福が未選択! 残" + pendingBoons + "件 (G→祝福を選ぶ)", x0, y, 0xFFE14AFF);
            y += 12;
        }
        // 未選択の遺物・技能 (点滅して注意を促す)
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        if (relicPending && blink) {
            g.drawString(font, "◆遺物が未選択! (G→遺物を選ぶ)", x0, y, 0xFFB44BFF);
            y += 12;
        }
        if (passivePending && blink) {
            g.drawString(font, "◆技能が未選択! (G→技能の再提示)", x0, y, 0xFF4BA8FF);
            y += 12;
        }
        if (portalOpen != 0) {
            g.drawString(font, "◆転移門OPEN (G→次の層へ)", x0, y, 0xFF7CFFB2);
        }
    }
}
