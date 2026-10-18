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
 * 汎用選択画面 (クライアント専用): 層間イベント (4択)・遺物 (3択)・技能 (3択)。
 * サーバーから {@link ModPackets.FloorEventSync} が届くと開く。数字キー 1〜4 かクリックで選び、
 * {@link ModPackets.FloorEventPacket} で送る (サーバー側で提示中かを再検証)。
 * ESC で後回し (選択は保存され、再提示やチャットボタン / 操作ハブから選び直せる)。
 */
public class FloorEventScreen extends Screen {
    private final String mode;
    private String title;
    private List<String> ids;
    private List<String> names;
    private List<String> descs;
    private String footer;
    private final boolean portalOpen;
    /** 選択送信済みフラグ (連打でパケットが二重送信されるのを防ぐ) */
    private boolean chosen;

    public FloorEventScreen(String mode, String title, List<String> ids, List<String> names,
                            List<String> descs, String footer, boolean portalOpen) {
        super(Component.literal("選択"));
        this.mode = mode;
        this.title = title;
        this.ids = ids == null ? List.of() : List.copyOf(ids);
        this.names = names == null ? List.of() : List.copyOf(names);
        this.descs = descs == null ? List.of() : List.copyOf(descs);
        this.footer = footer == null ? "" : footer;
        this.portalOpen = portalOpen;
    }

    /** 選択画面の種別 (event / relic / passive)。ClientGui の差し替え判定用 */
    public String mode() {
        return mode;
    }

    /** 連続提示で同じ画面の内容を差し替える (同じ mode のときだけ) */
    public void refresh(String mode, String title, List<String> ids, List<String> names,
                        List<String> descs, String footer, boolean portalOpen) {
        if (!this.mode.equals(mode)) return;
        this.title = title;
        this.ids = ids == null ? List.of() : List.copyOf(ids);
        this.names = names == null ? List.of() : List.copyOf(names);
        this.descs = descs == null ? List.of() : List.copyOf(descs);
        this.footer = footer == null ? "" : footer;
        this.chosen = false;
    }

    private int cardX() {
        return width / 2 - 190;
    }

    private int cardW() {
        return 380;
    }

    private int cardY() {
        return Math.max(64, height / 2 - 40 - ids.size() * 14);
    }

    /** 枠の色 (mode 別) */
    private int frameColor() {
        return switch (mode) {
            case "relic" -> 0xFFB44BFF;
            case "passive" -> 0xFF4BA8FF;
            default -> 0xFFFFD23C;
        };
    }

    /** 決定 (クリックと数字キーの共通)。選べない場合はサーバー側で拒否される */
    private void choose(int i) {
        if (chosen || i < 0 || i >= ids.size()) return;
        var conn = minecraft != null ? minecraft.getConnection() : null;
        if (conn != null) {
            chosen = true;
            conn.send(new ServerboundCustomPayloadPacket(new ModPackets.FloorEventPacket(mode, ids.get(i))));
        }
        onClose();
    }

    /** 「次の層へ進む」(転移門が開いているときだけ出る) */
    private void goNext() {
        var conn = minecraft != null ? minecraft.getConnection() : null;
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ActPacket("next")));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawCenteredString(font, title, width / 2, cardY() - 26, 0xFFFFD23C);
        int y = cardY();
        for (int i = 0; i < ids.size(); i++) {
            drawChoice(g, i, cardX(), y, cardW(), mouseX, mouseY);
            y += 34;
        }
        if (footer != null && !footer.isEmpty()) {
            g.drawCenteredString(font, footer, width / 2, y + 4, 0xFF9FB0C9);
            y += 14;
        }
        if (portalOpen) {
            boolean hover = mouseX >= width / 2 - 90 && mouseX < width / 2 + 90
                    && mouseY >= y + 8 && mouseY < y + 28;
            g.fill(width / 2 - 90, y + 8, width / 2 + 90, y + 28, hover ? 0xFF2F5B33 : 0xFF26382A);
            g.fill(width / 2 - 90, y + 8, width / 2 + 90, y + 9, 0xFF7CFFB2);
            g.fill(width / 2 - 90, y + 27, width / 2 + 90, y + 28, 0xFF7CFFB2);
            g.drawCenteredString(font, "▶ 次の層へ進む", width / 2, y + 13, 0xFF7CFFB2);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawChoice(GuiGraphics g, int i, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 30;
        g.fill(x, y, x + w, y + 30, hover ? 0xFF3A3A46 : 0xFF26262E);
        int frame = frameColor();
        g.fill(x, y, x + w, y + 1, frame);
        g.fill(x, y + 29, x + w, y + 30, frame);
        g.fill(x, y, x + 1, y + 30, frame);
        g.fill(x + w - 1, y, x + w, y + 30, frame);
        g.drawString(font, (i + 1) + ". " + (i < names.size() ? names.get(i) : ids.get(i)),
                x + 12, y + 4, frame, false);
        g.drawString(font, i < descs.size() ? descs.get(i) : "", x + 12, y + 17, 0xFFC9CFDE, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int n = event.key() - GLFW.GLFW_KEY_1;
        if (n >= 0 && n < ids.size()) {
            choose(n);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int y = cardY();
            for (int i = 0; i < ids.size(); i++) {
                if (mouseX >= cardX() && mouseX < cardX() + cardW()
                        && mouseY >= y && mouseY < y + 30) {
                    choose(i);
                    return true;
                }
                y += 34;
            }
            if (portalOpen && mouseY >= y + 8 && mouseY < y + 28
                    && mouseX >= width / 2 - 90 && mouseX < width / 2 + 90) {
                goNext();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
