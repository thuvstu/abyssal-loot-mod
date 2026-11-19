package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 操作ハブ (クライアント専用)。全GUI・主要操作へのボタン集約。
 * 各ボタンはチャットコマンドを送信するため、サーバー側の検証は各コマンド側で行われる。
 * レイアウト: 見出し → 進行状況カード (層・残存・魂のゲージ) → 2列 (左=進行と選択 / 右=装備と情報)。
 */
public class HubScreen extends Screen {
    /** {表示名, コマンド, 系統色} */
    private record Entry(String label, String command, int accent) {}

    private static final Entry[] LEFT = {
            new Entry("出撃する", "abyss start", UiKit.GOLD),
            new Entry("次の層へ", "abyss next", UiKit.GOLD),
            new Entry("祝福を選ぶ", "abyss boon", UiKit.VIOLET),
            new Entry("層間イベント", "abyss event_screen", UiKit.VIOLET),
            new Entry("遺物を選ぶ", "abyss relic", UiKit.VIOLET),
            new Entry("技能の再提示", "abyss passive", UiKit.BLUE),
    };
    private static final Entry[] RIGHT = {
            new Entry("鍛造", "abyss forge", UiKit.CYAN),
            new Entry("商店", "abyss shop", UiKit.CYAN),
            new Entry("装備比較", "abyss compare", UiKit.CYAN),
            new Entry("保管庫", "abyss vault", UiKit.GOLD),
            new Entry("特殊スキル", "abyss sub", UiKit.GREEN),
            new Entry("ステータス", "abyss statusgui", UiKit.GREEN),
            new Entry("設定メニュー", "abyss menu", UiKit.SUB),
            new Entry("操作説明", "abyss help", UiKit.SUB),
    };

    private static final int PW = 332;
    private static final int BW = 150;
    private static final int ROW = 20;
    /** 進行状況カードの高さ */
    private static final int CARD_H = 40;

    public HubScreen() {
        super(Component.literal("深淵メニュー"));
    }

    private int px0() {
        return width / 2 - PW / 2;
    }

    private int py0() {
        return Math.max(4, height / 2 - panelH() / 2);
    }

    private int panelH() {
        return 16 + 8 + CARD_H + 6 + 12 + RIGHT.length * ROW + 8;
    }

    private int gridY() {
        return py0() + 16 + 8 + CARD_H + 6 + 12;
    }

    @Override
    protected void init() {
        int colX0 = width / 2 - BW - 6;
        int colX1 = width / 2 + 6;
        add(LEFT, colX0);
        add(RIGHT, colX1);
    }

    private void add(Entry[] entries, int x) {
        int y = gridY();
        for (Entry e : entries) {
            addRenderableWidget(UiButton.of(e.label(), x, y, BW, ROW - 3, e.accent(), () -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.sendCommand(e.command());
            }));
            y += ROW;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = px0();
        int y0 = py0();
        int x1 = x0 + PW;
        int y1 = y0 + panelH();
        UiKit.panel(g, x0, y0, x1, y1, UiKit.VIOLET);
        UiKit.titleBar(g, font, x0, x1, y0, title.getString(), UiKit.VIOLET);
        renderStatus(g, x0 + 10, y0 + 24, PW - 20);
        int sy = gridY() - 12;
        UiKit.section(g, font, "進行と選択", width / 2 - BW - 6, sy, UiKit.GOLD);
        UiKit.section(g, font, "装備と情報", width / 2 + 6, sy, UiKit.CYAN);
        super.render(g, mouseX, mouseY, partialTick);
        UiKit.keyHints(g, font, width / 2, Math.min(height - 13, y1 + 5), "G", "開閉", "ESC", "閉じる");
    }

    /** 進行状況カード: 層・モード・残存 (ゲージ)・魂 (ゲージ)・未選択の祝福 */
    private void renderStatus(GuiGraphics g, int x, int y, int w) {
        var st = ClientGui.hubStatus();
        g.fillGradient(x, y, x + w, y + CARD_H, 0xC0221F3E, 0xC0141228);
        g.renderOutline(x, y, w, CARD_H, UiKit.alpha(UiKit.EDGE, 0xFF));
        if (st == null || !st.inRun()) {
            g.drawCenteredString(font, "深淵に潜っていない", x + w / 2, y + 9, UiKit.TEXT);
            g.drawCenteredString(font, "『出撃する』か /abyss quick で潜行を始める", x + w / 2, y + 23, UiKit.MUTED);
            return;
        }
        var zone = GameData.zoneForFloor(Math.max(1, st.floor()));
        g.drawString(font, "第" + st.floor() + "層", x + 8, y + 5, UiKit.TITLE, false);
        int cx = x + 8 + font.width("第" + st.floor() + "層") + 6;
        cx += UiKit.chip(g, font, zone.name(), cx, y + 3, UiKit.CYAN) + 4;
        UiKit.chip(g, font, st.mode(), cx, y + 3, UiKit.VIOLET);
        String kills = "撃破 " + st.kills();
        g.drawString(font, kills, x + w - 8 - font.width(kills), y + 5, UiKit.SUB, false);
        // 残存 (層クリアまで) と 魂 の 2 本のゲージ
        int bw = (w - 32) / 2;
        int by = y + 29;
        if (st.portalOpen() != 0) {
            g.drawString(font, "転移門 OPEN", x + 8, by - 11, UiKit.GREEN, false);
            UiKit.bar(g, x + 8, by, bw, 6, 1f, UiKit.GREEN, false);
        } else {
            int total = Math.max(1, st.waveTotal());
            int left = Math.max(0, st.waveTotal() - st.floorKills());
            g.drawString(font, "残存 " + left + "/" + st.waveTotal(), x + 8, by - 11, 0xFFFFB86B, false);
            UiKit.bar(g, x + 8, by, bw, 6, (float) st.floorKills() / total, 0xFFFFB86B, false);
        }
        int sx = x + 24 + bw;
        float soul = st.soul();
        g.drawString(font, String.format("魂 %.0f/100", soul), sx, by - 11,
                soul >= 100 ? 0xFFE14AFF : UiKit.SUB, false);
        UiKit.bar(g, sx, by, bw, 6, soul / 100f, 0xFFB14BFF, true);
        if (st.pendingBoons() > 0) {
            String p = "祝福未選択 ×" + st.pendingBoons();
            UiKit.chip(g, font, p, sx + bw - font.width(p) - 6, by - 13, 0xFFE14AFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
