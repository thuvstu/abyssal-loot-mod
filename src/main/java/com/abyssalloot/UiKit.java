package com.abyssalloot;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * MOD の GUI 共通の描画部品 (クライアント専用)。
 * 全画面・HUD を同じ「深淵」調 (紺紫のパネル・色付きの角飾り・金の見出し) で揃えるため、
 * 画面側は座標と色だけを渡し、塗り方はここに集約する。
 */
public final class UiKit {
    private UiKit() {}

    // ---- 配色 (ARGB)
    public static final int PANEL_TOP = 0xF41C1934;
    public static final int PANEL_BOT = 0xF40D0C1B;
    public static final int EDGE = 0xFF3D3763;
    public static final int EDGE_DARK = 0xFF07060E;
    public static final int GOLD = 0xFFFFD23C;
    public static final int TITLE = 0xFFFFE6A0;
    public static final int TEXT = 0xFFE8E6F4;
    public static final int SUB = 0xFFA8A4C8;
    public static final int MUTED = 0xFF6E6A8A;
    public static final int VIOLET = 0xFFB48CFF;
    public static final int CYAN = 0xFF7CE8FF;
    public static final int GREEN = 0xFF7CFFB2;
    public static final int RED = 0xFFFF5C6E;
    public static final int BLUE = 0xFF4BA8FF;

    public static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0x00FFFFFF);
    }

    /** 明るさを k 倍 (アルファは維持) */
    public static int scale(int color, float k) {
        int r = Math.min(255, (int) (((color >> 16) & 255) * k));
        int g = Math.min(255, (int) (((color >> 8) & 255) * k));
        int b = Math.min(255, (int) ((color & 255) * k));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /**
     * パネル: 影 + 暗い外枠 + 縦グラデーション + 枠 + 上辺の照り + 四隅の角飾り (accent 色)。
     */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        g.fill(x0 + 3, y0 + 3, x1 + 3, y1 + 3, 0x50000000);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, EDGE_DARK);
        g.fillGradient(x0, y0, x1, y1, PANEL_TOP, PANEL_BOT);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, EDGE);
        g.hLine(x0 + 1, x1 - 2, y0 + 1, 0x24FFFFFF);
        corners(g, x0, y0, x1, y1, accent);
    }

    /** 四隅の L 字飾りと角の光点 */
    public static void corners(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        int len = 7;
        int a = alpha(accent, 0xE0);
        g.hLine(x0, x0 + len, y0, a);
        g.vLine(x0, y0, y0 + len, a);
        g.hLine(x1 - 1 - len, x1 - 1, y0, a);
        g.vLine(x1 - 1, y0, y0 + len, a);
        g.hLine(x0, x0 + len, y1 - 1, a);
        g.vLine(x0, y1 - 1 - len, y1 - 1, a);
        g.hLine(x1 - 1 - len, x1 - 1, y1 - 1, a);
        g.vLine(x1 - 1, y1 - 1 - len, y1 - 1, a);
        int hi = scale(accent, 1.3f) | 0xFF000000;
        g.fill(x0 - 1, y0 - 1, x0 + 1, y0 + 1, hi);
        g.fill(x1 - 1, y0 - 1, x1 + 1, y0 + 1, hi);
        g.fill(x0 - 1, y1 - 1, x0 + 1, y1 + 1, hi);
        g.fill(x1 - 1, y1 - 1, x1 + 1, y1 + 1, hi);
    }

    /** 見出し帯 (accent 色が左右へ溶ける帯 + 下線 + ◆見出し◆)。帯の高さ 16 */
    public static void titleBar(GuiGraphics g, Font font, int x0, int x1, int y, String text, int accent) {
        int mid = (x0 + x1) / 2;
        int half = (x1 - x0) / 2 - 1;
        // 中央が濃く、左右へ薄れる帯 (左右対称の横グラデーションを 4 段で近似)
        for (int i = 0; i < 4; i++) {
            int w = half * (4 - i) / 4;
            g.fill(mid - w, y, mid + w, y + 16, alpha(accent, 0x16));
        }
        divider(g, x0 + 1, x1 - 1, y + 16, accent);
        g.drawCenteredString(font, "◆ " + text + " ◆", mid, y + 4, TITLE);
    }

    /** 区切り線: 中央が明るく端へ向かって消える */
    public static void divider(GuiGraphics g, int x0, int x1, int y, int color) {
        int mid = (x0 + x1) / 2;
        int half = (x1 - x0) / 2;
        int[] alphas = {0xD0, 0x90, 0x50, 0x20};
        for (int i = 0; i < alphas.length; i++) {
            int a = half * i / alphas.length;
            int b = half * (i + 1) / alphas.length;
            g.hLine(mid - b, mid - a, y, alpha(color, alphas[i]));
            g.hLine(mid + a, mid + b, y, alpha(color, alphas[i]));
        }
    }

    /** 小見出し (色の四角 + 文字) */
    public static void section(GuiGraphics g, Font font, String text, int x, int y, int accent) {
        g.fill(x, y + 1, x + 3, y + 8, accent);
        g.drawString(font, text, x + 6, y, SUB, false);
    }

    /** 選択カード (左の色帯・ホバーで明るく枠が点灯) */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int frame, boolean hover) {
        g.fillGradient(x, y, x + w, y + h, hover ? 0xF4322D52 : 0xF4221F3A, hover ? 0xF4221E3C : 0xF4151329);
        g.renderOutline(x, y, w, h, hover ? frame : alpha(frame, 0x80));
        g.fill(x + 1, y + 1, x + 4, y + h - 1, frame);
        if (hover) {
            g.hLine(x + 4, x + w - 2, y + 1, 0x30FFFFFF);
            g.fill(x + w - 8, y + h / 2 - 2, x + w - 4, y + h / 2 + 2, frame); // 選択の目印
        }
    }

    /** 小さなラベル (枠つき)。描いた幅を返す */
    public static int chip(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int w = font.width(text) + 6;
        g.fill(x, y, x + w, y + 11, alpha(color, 0x38));
        g.renderOutline(x, y, w, 11, alpha(color, 0xB0));
        g.drawString(font, text, x + 3, y + 2, color, false);
        return w;
    }

    /** キー操作の案内 ([キー] 説明) を中央揃えで1行に並べる */
    public static void keyHints(GuiGraphics g, Font font, int cx, int y, String... pairs) {
        int total = 0;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            total += font.width(pairs[i]) + 6 + 3 + font.width(pairs[i + 1]) + 10;
        }
        int x = cx - total / 2;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            x += chip(g, font, pairs[i], x, y, SUB) + 3;
            g.drawString(font, pairs[i + 1], x, y + 2, MUTED, false);
            x += font.width(pairs[i + 1]) + 10;
        }
    }

    /**
     * ゲージ: 背景・枠・塗り (上が明るい縦グラデーション)・上端の照り・先端の光・25%刻みの刻線.
     */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float pct, int color, boolean segments) {
        pct = Math.max(0f, Math.min(1f, pct));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE_DARK);
        g.fill(x, y, x + w, y + h, 0xC0181530);
        int fill = Math.round(w * pct);
        if (fill > 0) {
            g.fillGradient(x, y, x + fill, y + h, scale(color, 1.15f) | 0xFF000000, scale(color, 0.7f) | 0xFF000000);
            g.hLine(x, x + fill - 1, y, 0x50FFFFFF);
            if (fill < w) g.vLine(x + fill - 1, y - 1, y + h, 0xC0FFFFFF);
        }
        if (segments) {
            for (int i = 1; i <= 3; i++) {
                int sx = x + w * i / 4;
                g.vLine(sx, y - 1, y + h, 0x80000000);
            }
        }
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, alpha(EDGE, 0xFF));
    }

    /** アイテムスロットの窪み (18x18。x,y はスロットの左上 = アイテムの描画位置) */
    public static void slot(GuiGraphics g, int x, int y, int accent) {
        g.fill(x - 1, y - 1, x + 17, y + 17, EDGE_DARK);
        g.fillGradient(x, y, x + 16, y + 16, 0xFF15132A, 0xFF231F3E);
        g.hLine(x, x + 15, y, 0xFF0B0A16);           // 上辺の影 (窪み)
        g.vLine(x, y, y + 15, 0xFF0B0A16);
        g.hLine(x - 1, x + 16, y + 16, alpha(accent, 0x60));  // 下辺の照り返し
        g.vLine(x + 16, y - 1, y + 16, alpha(accent, 0x60));
    }

    /** コンテナ画面: 枠 (パネル) と全スロットの窪みを描く */
    public static void containerPanel(GuiGraphics g, AbstractContainerMenu menu, int left, int top,
                                      int w, int h, int accent) {
        panel(g, left, top, left + w, top + h, accent);
        for (Slot s : menu.slots) {
            slot(g, left + s.x, top + s.y, accent);
        }
    }
}
