package com.abyssalloot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 祝福選択画面 (クライアント専用)。レベルアップ・血の誓約でサーバーから {@link ModPackets.BoonSync} が届くと開く。
 * 3択 (等級色つきボックス) を数字キー 1/2/3 かクリックで選ぶ。選択は {@link ModPackets.BoonPacket} で送り、
 * サーバー側で3択からの選択かを再検証する。閉じても未選択の祝福は PENDING_BOONS に保存され再提示される。
 */
public class BoonSelectScreen extends Screen {
    private List<String> choices;
    private int remaining;

    public BoonSelectScreen(List<String> choices, int remaining) {
        super(Component.literal("祝福を選択"));
        this.choices = choices == null ? List.of() : List.copyOf(choices);
        this.remaining = remaining;
    }

    /** 連続提示 (残りの選択分) で同じ画面の内容を差し替える */
    public void refresh(List<String> next, int nextRemaining) {
        this.choices = next == null ? List.of() : List.copyOf(next);
        this.remaining = nextRemaining;
    }

    private int choiceX() {
        return width / 2 - 180;
    }

    private int choiceY() {
        return Math.max(70, height / 2 - 56);
    }

    /** 決定 (クリックと数字キーの共通)。選べない場合はサーバー側で拒否される */
    private void choose(int i) {
        if (i < 0 || i >= choices.size()) return;
        var conn = minecraft != null ? minecraft.getConnection() : null;
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new ModPackets.BoonPacket(choices.get(i))));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawCenteredString(font, "◆ レベルアップ — 祝福を選べ ◆", width / 2, height / 2 - 108, 0xFFFFD23C);
        g.drawCenteredString(font, "未選択の残り " + Math.max(1, remaining) + "件 / 閉じても失われない",
                width / 2, height / 2 - 92, 0xFF9FB0C9);
        int y = choiceY();
        for (int i = 0; i < choices.size(); i++) {
            drawChoice(g, i, choiceX(), y, 360, mouseX, mouseY);
            y += 28;
        }
        g.drawCenteredString(font, "数字キー 1〜3 でも選択 / ESC で後に選ぶ (G→祝福を選ぶ)", width / 2, y + 12, 0xFF6E7686);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 等級色の枠 + 名前/効果のカード */
    private void drawChoice(GuiGraphics g, int i, int x, int y, int w, int mouseX, int mouseY) {
        var def = Boons.boonById(choices.get(i));
        if (def == null) return;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 24;
        g.fill(x, y, x + w, y + 24, hover ? 0xFF3A3A46 : 0xFF26262E);
        int frame = switch (def.grade()) {
            case "epic" -> 0xFFB44BFF;
            case "rare" -> 0xFF4BA8FF;
            default -> 0xFF6EF08A;
        };
        g.fill(x, y, x + w, y + 1, frame);
        g.fill(x, y + 23, x + w, y + 24, frame);
        g.fill(x, y, x + 1, y + 24, frame);
        g.fill(x + w - 1, y, x + w, y + 24, frame);
        g.drawString(font, (i + 1) + ". " + def.name(), x + 12, y + 3, frame, false);
        g.drawString(font, def.desc(), x + 12, y + 14, 0xFFC9CFDE, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_1 -> { choose(0); return true; }
            case GLFW.GLFW_KEY_2 -> { choose(1); return true; }
            case GLFW.GLFW_KEY_3 -> { choose(2); return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && !choices.isEmpty()) {
            double mouseX = event.x();
            double mouseY = event.y();
            int y = choiceY();
            for (int i = 0; i < choices.size(); i++) {
                if (mouseX >= choiceX() && mouseX < choiceX() + 360 && mouseY >= y && mouseY < y + 24) {
                    choose(i);
                    return true;
                }
                y += 28;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
