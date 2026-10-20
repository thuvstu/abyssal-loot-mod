package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 操作ハブ (クライアント専用)。全GUI・主要操作へのボタン集約。
 * 各ボタンはチャットコマンドを送信するため、サーバー側の検証は各コマンド側で行われる。
 * レイアウトは 2列グリッド + パネル背景 (左列=進行と選択 / 右列=装備と情報)。
 */
public class HubScreen extends Screen {
    private static final String[][] BUTTONS = {
            {"出撃する", "abyss start"},
            {"次の層へ", "abyss next"},
            {"祝福を選ぶ", "abyss boon"},
            {"層間イベントを選ぶ", "abyss event_screen"},
            {"遺物を選ぶ", "abyss relic"},
            {"技能の再提示", "abyss passive"},
            {"鍛造", "abyss forge"},
            {"商店", "abyss shop"},
            {"特殊スキル", "abyss sub"},
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
        // 2列グリッド (左: 進行と選択 / 右: 装備と情報)
        int bw = 150;
        int colX0 = width / 2 - bw - 6;
        int colX1 = width / 2 + 6;
        int y = height / 2 - 44;
        for (int i = 0; i < BUTTONS.length; i++) {
            String[] b = BUTTONS[i];
            int x = i % 2 == 0 ? colX0 : colX1;
            int by = y + i / 2 * 24;
            addRenderableWidget(Button.builder(Component.literal(b[0]), btn -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.sendCommand(b[1]);
            }).bounds(x, by, bw, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // パネル背景 (ボタングリッドを囲む。6行×2列)。super.render の前に描き、
        // ボタンがパネルの下に沈むのを防ぐ
        int px0 = width / 2 - 162;
        int px1 = width / 2 + 162;
        int py0 = height / 2 - 92;
        int py1 = height / 2 + 112;
        g.fill(px0, py0, px1, py1, 0xD010141E);
        g.fill(px0, py0, px1, py0 + 1, 0xFF5A6478);
        g.fill(px0, py1 - 1, px1, py1, 0xFF5A6478);
        g.fill(px0, py0, px0 + 1, py1, 0xFF5A6478);
        g.fill(px1 - 1, py0, px1, py1, 0xFF5A6478);
        g.drawCenteredString(font, title.getString(), width / 2, py0 + 6, 0xFFFFD23C);
        renderStatus(g, py0);
        g.drawCenteredString(font, "ESC で閉じる", width / 2, py1 + 6, 0xFF6E7686);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 進行状況の1行サマリー (実行中は層・残存・魂、未実行は出撃の案内) */
    private void renderStatus(GuiGraphics g, int py0) {
        var st = ClientGui.hubStatus();
        if (st == null) return;
        String line1;
        String line2;
        if (st.inRun()) {
            String where = "第" + st.floor() + "層 [" + st.mode() + "]";
            if (st.portalOpen() != 0) {
                line1 = where + "  転移門OPEN (門をくぐって次層へ)";
                line2 = "撃破 " + st.kills() + "  魂 " + Math.round(st.soul()) + "/100"
                        + (st.pendingBoons() > 0 ? "  未選択祝福 " + st.pendingBoons() : "");
            } else {
                int left = Math.max(0, st.waveTotal() - st.floorKills());
                line1 = where + "  残存 " + left + " 体";
                line2 = "撃破 " + st.kills() + "  魂 " + Math.round(st.soul()) + "/100"
                        + (st.pendingBoons() > 0 ? "  未選択祝福 " + st.pendingBoons() : "");
            }
        } else {
            line1 = "深淵に潜っていない (出撃すると潜行が始まります)";
            line2 = "『出撃する』か /abyss quick で即出撃できます";
        }
        g.drawCenteredString(font, line1, width / 2, py0 + 20, 0xFFB9C4D6);
        g.drawCenteredString(font, line2, width / 2, py0 + 31, 0xFF8C96A8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
