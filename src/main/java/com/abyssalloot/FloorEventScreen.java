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
 * レイアウト: パネル背景 + カード群 + 転移門OPEN時は「次の層へ進む」ボタン。
 */
public class FloorEventScreen extends Screen {
    private final String mode;
    private String title;
    private List<String> ids;
    private List<String> names;
    private List<String> descs;
    private String footer;
    private boolean portalOpen;
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
        this.portalOpen = portalOpen;
        this.chosen = false;
    }

    private int cardX() {
        return width / 2 - 190;
    }

    private int cardW() {
        // 小さい画面ではみ出さないよう画面幅に合わせて縮める
        return Math.min(380, width - 16);
    }

    private int cardY() {
        return Math.max(74, height / 2 - 30 - ids.size() * 17);
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
        int cx = width / 2;
        int cy = cardY();
        int ch = ids.size() * 34 + (footer != null && !footer.isEmpty() ? 18 : 4)
                + (portalOpen ? 30 : 0);
        // パネル (カード群を囲む)
        int px0 = cardX() - 10;
        int px1 = cardX() + cardW() + 10;
        int py0 = cy - 36;
        int py1 = cy + ch + 8;
        UiKit.panel(g, px0, py0, px1, py1, frameColor());
        UiKit.titleBar(g, font, px0, px1, py0, title, frameColor());
        String kind = switch (mode) {
            case "relic" -> "遺物";
            case "passive" -> "技能";
            default -> "層間イベント";
        };
        UiKit.chip(g, font, kind, px0 + 8, py0 + 20, frameColor());
        super.render(g, mouseX, mouseY, partialTick);
        int y = cy;
        for (int i = 0; i < ids.size(); i++) {
            drawChoice(g, i, cardX(), y, cardW(), mouseX, mouseY);
            y += 34;
        }
        if (footer != null && !footer.isEmpty()) {
            g.drawCenteredString(font, footer, cx, y + 4, 0xFF9FB0C9);
            y += 14;
        }
        if (portalOpen) {
            boolean hover = mouseX >= cx - 90 && mouseX < cx + 90
                    && mouseY >= y + 8 && mouseY < y + 28;
            UiKit.card(g, cx - 90, y + 8, 180, 20, UiKit.GREEN, hover);
            g.drawCenteredString(font, "▶ 次の層へ進む", cx + 2, y + 14, hover ? UiKit.TITLE : UiKit.GREEN);
        }
        UiKit.keyHints(g, font, cx, py1 + 6, "1〜" + Math.max(1, ids.size()), "選ぶ", "ESC", "後で選ぶ");
    }

    private void drawChoice(GuiGraphics g, int i, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 30;
        int frame = frameColor();
        UiKit.card(g, x, y, w, 30, frame, hover);
        g.drawString(font, String.valueOf(i + 1), x + 9, y + 11, frame, true);
        g.drawString(font, i < names.size() ? names.get(i) : ids.get(i), x + 20, y + 5,
                hover ? UiKit.TITLE : frame, false);
        g.drawString(font, i < descs.size() ? descs.get(i) : "", x + 20, y + 17, 0xFFC9CFDE, false);
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
