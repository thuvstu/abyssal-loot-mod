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
    private static final String[] ATTRS = {"剛", "韻", "敏", "忍", "幸", "集", "活", "技"};

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
        // 属性行の右側に縦に5ボタンを配置 (テキストと被りにくい余白側)
        int y = attrY() - 44;
        for (int i = 0; i < ATTRS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal("+" + ATTRS[i]), b -> {
                var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (conn != null) {
                    conn.send(new ServerboundCustomPayloadPacket(new ModPackets.AttrPacket(idx)));
                }
            }).bounds(width / 2 + 118, y + i * 22, 34, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(width / 2 - 40, height - 30, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        var lines = ClientGui.statusLines();
        int y = Math.max(20, height / 2 - (lines.size() + 1) * 6);
        g.drawString(font, title.getString(), width / 2 - 110, y - 14, 0xFFFFD23C, false);
        for (String s : lines) {
            g.drawString(font, s, width / 2 - 110, y, 0xFFE8E8F0, false);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
