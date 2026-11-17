package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Inventory;

/**
 * 装備比較GUI (クライアント専用)。A/Bスロットの武器をサーバー計算で比較し、
 * 結果テキストを2列表示する。ドラッグ配置はバニラ操作のまま使える。
 */
public class CompareScreen extends AbstractContainerScreen<CompareMenu> {
    public CompareScreen(CompareMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        // 所持品の下に比較結果を表示するため縦長にする (スロット配置はメニュー側のまま)
        this.imageHeight = 232;
        this.inventoryLabelY = 100000;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(UiButton.of("比較する", leftPos + 53, topPos + 58, 70, 16, UiKit.CYAN, () -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ComparePacket()));
            }
        }));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        UiKit.containerPanel(g, menu, leftPos, topPos, imageWidth, imageHeight, UiKit.CYAN);
        g.renderOutline(leftPos + 28, topPos + 33, 20, 20, UiKit.alpha(UiKit.CYAN, 0xC0));
        g.renderOutline(leftPos + 128, topPos + 33, 20, 20, UiKit.alpha(UiKit.GOLD, 0xC0));
        g.drawCenteredString(font, "VS", leftPos + 88, topPos + 39, UiKit.SUB);
        // 比較結果の欄 (A / B の 2 列)
        int ry = topPos + 160;
        UiKit.divider(g, leftPos + 6, leftPos + imageWidth - 6, ry - 2, UiKit.CYAN);
        g.fill(leftPos + 4, ry, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0x40000000);
        g.vLine(leftPos + 88, ry + 2, topPos + imageHeight - 6, UiKit.alpha(UiKit.EDGE, 0xFF));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "◆ " + title.getString(), titleLabelX, titleLabelY, UiKit.TITLE, false);
        g.drawString(font, "A", 36, 23, UiKit.CYAN, false);
        g.drawString(font, "B", 136, 23, UiKit.GOLD, false);
        var left = ClientGui.compareLeft();
        var right = ClientGui.compareRight();
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            int y = 164 + i * 10;
            if (y > imageHeight - 12) break;
            if (i < left.size()) g.drawString(font, left.get(i), 6, y, 0xFF8BE9FF, false);
            if (i < right.size()) g.drawString(font, right.get(i), 92, y, 0xFFE8C86A, false);
        }
    }
}
