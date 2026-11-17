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
    /** 選択送信済みフラグ (連打でパケットが二重送信されるのを防ぐ) */
    private boolean chosen;

    public BoonSelectScreen(List<String> choices, int remaining) {
        super(Component.literal("祝福を選択"));
        this.choices = choices == null ? List.of() : List.copyOf(choices);
        this.remaining = remaining;
    }

    /** 連続提示 (残りの選択分) で同じ画面の内容を差し替える */
    public void refresh(List<String> next, int nextRemaining) {
        this.choices = next == null ? List.of() : List.copyOf(next);
        this.remaining = nextRemaining;
        this.chosen = false;
    }

    private int choiceX() {
        return width / 2 - 180;
    }

    private int choiceY() {
        return Math.max(70, height / 2 - 56);
    }

    /** 選択カードの幅 (小さい画面ではみ出さないよう画面幅に合わせて縮める) */
    private int choiceW() {
        return Math.min(360, width - 16);
    }

    /** 決定 (クリックと数字キーの共通)。選べない場合はサーバー側で拒否される */
    private void choose(int i) {
        if (chosen || i < 0 || i >= choices.size()) return;
        var conn = minecraft != null ? minecraft.getConnection() : null;
        if (conn != null) {
            chosen = true;
            conn.send(new ServerboundCustomPayloadPacket(new ModPackets.BoonPacket(choices.get(i))));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = choiceX() - 12;
        int x1 = choiceX() + choiceW() + 12;
        int y0 = choiceY() - 44;
        int y1 = choiceY() + choices.size() * 28 + 8;
        UiKit.panel(g, x0, y0, x1, y1, UiKit.GOLD);
        UiKit.titleBar(g, font, x0, x1, y0, "レベルアップ — 祝福を選べ", UiKit.GOLD);
        g.drawCenteredString(font, "未選択の残り " + Math.max(1, remaining) + " 件 (閉じても失われない)",
                width / 2, y0 + 24, UiKit.SUB);
        int y = choiceY();
        for (int i = 0; i < choices.size(); i++) {
            drawChoice(g, i, choiceX(), y, choiceW(), mouseX, mouseY);
            y += 28;
        }
        UiKit.keyHints(g, font, width / 2, y1 + 6, "1〜" + Math.max(1, choices.size()), "選ぶ", "ESC", "後で (G→祝福を選ぶ)");
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 等級色の帯つきカード: 番号・名前・等級ラベル / 効果 */
    private void drawChoice(GuiGraphics g, int i, int x, int y, int w, int mouseX, int mouseY) {
        var def = Boons.boonById(choices.get(i));
        if (def == null) return;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 24;
        int frame = switch (def.grade()) {
            case "epic" -> 0xFFB44BFF;
            case "rare" -> 0xFF4BA8FF;
            default -> 0xFF6EF08A;
        };
        String grade = switch (def.grade()) {
            case "epic" -> "叙事";
            case "rare" -> "希少";
            default -> "通常";
        };
        UiKit.card(g, x, y, w, 24, frame, hover);
        g.drawString(font, String.valueOf(i + 1), x + 9, y + 8, UiKit.alpha(frame, 0xFF), true);
        g.drawString(font, def.name(), x + 20, y + 3, hover ? UiKit.TITLE : frame, false);
        UiKit.chip(g, font, grade, x + w - font.width(grade) - 18, y + 2, frame);
        g.drawString(font, def.desc(), x + 20, y + 14, 0xFFC9CFDE, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 数字キーで選択 (選択肢の数に応じて 1〜N まで有効。FloorEventScreen と同じ方式)
        int n = event.key() - GLFW.GLFW_KEY_1;
        if (n >= 0 && n < choices.size()) {
            choose(n);
            return true;
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
                if (mouseX >= choiceX() && mouseX < choiceX() + choiceW() && mouseY >= y && mouseY < y + 24) {
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
