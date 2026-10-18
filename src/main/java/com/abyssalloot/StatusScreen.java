package com.abyssalloot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * ステータス画面 (クライアント専用)。表示テキストはサーバーが生成し
 * {@link ModPackets.StatusSync} で送る。属性ボタン付き (未配分点の割振り)。
 */
public class StatusScreen extends Screen {
    private static final String[] ATTRS = {"剛", "韻", "敏", "忍", "幸", "集", "活", "技", "採", "跳"};

    public StatusScreen() {
        super(Component.literal("ステータス"));
    }

    /** 属性行のY座標 (render と共有。属性は3行目) */
    private int attrY() {
        int y0 = Math.max(20, height / 2 - (ClientGui.statusLines().size() + 1) * 6);
        return y0 + 24;
    }

    @Override
    protected void init() {
        // 属性行の右側に2列×5行で配置 (10属性対応)
        int y = attrY() - 44;
        for (int i = 0; i < ATTRS.length; i++) {
            final int idx = i;
            int col = i / 5;
            int row = i % 5;
            addRenderableWidget(Button.builder(Component.literal("+" + ATTRS[i]), b -> {
                var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (conn != null) {
                    conn.send(new ServerboundCustomPayloadPacket(new ModPackets.AttrPacket(idx)));
                }
            }).bounds(width / 2 + 118 + col * 38, y + row * 22, 34, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(width / 2 - 40, height - 30, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var lines = ClientGui.statusLines();
        int x = width / 2 - 110;
        int y = Math.max(30, height / 2 - (lines.size() + 1) * 6);
        // パネル背景 (テキスト块と属性ボタン列を囲む)。super.render の前に描き、
        // 属性ボタンがパネルの下に沈むのを防ぐ
        int px0 = x - 10;
        int px1 = width / 2 + 118 + 76;
        int py0 = y - 22;
        int py1 = Math.max(height - 36, y + lines.size() * 12 + 8);
        g.fill(px0, py0, px1, py1, 0xD010141E);
        g.fill(px0, py0, px1, py0 + 1, 0xFF5A6478);
        g.fill(px0, py1 - 1, px1, py1, 0xFF5A6478);
        g.fill(px0, py0, px0 + 1, py1, 0xFF5A6478);
        g.fill(px1 - 1, py0, px1, py1, 0xFF5A6478);
        g.drawString(font, "◆ " + title.getString(), x, y - 14, 0xFFFFD23C, false);
        super.render(g, mouseX, mouseY, partialTick);
        for (String s : lines) {
            g.drawString(font, s, x, y, 0xFFE8E8F0, false);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
