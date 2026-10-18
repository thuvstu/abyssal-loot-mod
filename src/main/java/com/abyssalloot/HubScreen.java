package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 操作ハブ (クライアント専用)。全GUI・主要操作へのボタン集約。
 * 各ボタンはチャットコマンドを送信するため、サーバー側の検証は各コマンド側で行われる。
 */
public class HubScreen extends Screen {
    private static final String[][] BUTTONS = {
            {"出撃する", "abyss start"},
            {"次の層へ", "abyss next"},
            {"祝福を選ぶ", "abyss boon"},
            {"層間イベントを選ぶ", "abyss event_screen"},
            {"技能の再提示", "abyss passive"},
            {"鍛造", "abyss forge"},
            {"商店", "abyss shop"},
            {"装備比較", "abyss compare"},
            {"ステータス", "abyss statusgui"},
            {"設定メニュー", "abyss menu"},
            {"操作説明", "abyss help"},
    };

    public HubScreen() {
        super(Component.literal("深淵メニュー"));
    }

    @Override
    protected void init() {
        int cx = width / 2 - 100;
        int y = height / 2 - 80;
        for (String[] b : BUTTONS) {
            addRenderableWidget(Button.builder(Component.literal(b[0]), btn -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.sendCommand(b[1]);
            }).bounds(cx, y, 200, 20).build());
            y += 24;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title.getString(), width / 2, height / 2 - 96, 0xFFFFD23C);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
