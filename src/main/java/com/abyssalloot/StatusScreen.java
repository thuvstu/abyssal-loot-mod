package com.abyssalloot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * ステータス画面 (クライアント専用)。表示テキストはサーバーが生成し
 * {@link ModPackets.StatusSync} で送る。右側に属性の割り振りボタン (2列×5行)。
 */
public class StatusScreen extends Screen {
    private static final String[] ATTRS = {"剛", "韻", "敏", "忍", "幸", "集", "活", "技", "採", "跳"};
    private static final int TEXT_W = 236;
    private static final int SIDE_W = 84;

    public StatusScreen() {
        super(Component.literal("ステータス"));
    }

    private int lineCount() {
        return Math.max(10, ClientGui.statusLines().size());
    }

    private int panelW() {
        return TEXT_W + SIDE_W + 28;
    }

    private int panelH() {
        return 16 + 10 + lineCount() * 11 + 34;
    }

    private int x0() {
        return width / 2 - panelW() / 2;
    }

    private int y0() {
        return Math.max(4, height / 2 - panelH() / 2);
    }

    @Override
    protected void init() {
        int sx = x0() + 14 + TEXT_W + 10;
        int sy = y0() + 16 + 24;
        for (int i = 0; i < ATTRS.length; i++) {
            final int idx = i;
            int col = i / 5;
            int row = i % 5;
            addRenderableWidget(UiButton.of("+" + ATTRS[i], sx + col * 40, sy + row * 22, 36, 18, UiKit.GREEN, () -> {
                var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (conn != null) {
                    conn.send(new ServerboundCustomPayloadPacket(new ModPackets.AttrPacket(idx)));
                }
            }));
        }
        addRenderableWidget(UiButton.of("閉じる", width / 2 - 40, y0() + panelH() - 24, 80, 16,
                UiKit.SUB, this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = x0();
        int y0 = y0();
        int x1 = x0 + panelW();
        int y1 = y0 + panelH();
        UiKit.panel(g, x0, y0, x1, y1, UiKit.GREEN);
        UiKit.titleBar(g, font, x0, x1, y0, title.getString(), UiKit.GREEN);
        // 左: サーバー生成の行 (偶数行に薄い縞)
        int tx = x0 + 14;
        int ty = y0 + 16 + 10;
        var lines = ClientGui.statusLines();
        for (int i = 0; i < lines.size(); i++) {
            if (i % 2 == 0) g.fill(tx - 4, ty - 2, tx + TEXT_W, ty + 9, 0x18FFFFFF);
            g.drawString(font, lines.get(i), tx, ty, UiKit.TEXT, false);
            ty += 11;
        }
        // 右: 属性の割り振り
        int sx = x0 + 14 + TEXT_W + 6;
        g.fill(sx - 1, y0 + 22, sx, y1 - 30, UiKit.alpha(UiKit.EDGE, 0xFF));
        UiKit.section(g, font, "属性を割り振る", sx + 4, y0 + 16 + 10, UiKit.GREEN);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
