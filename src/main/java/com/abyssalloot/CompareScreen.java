package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
        addRenderableWidget(Button.builder(Component.literal("比較する"), b -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ComparePacket()));
            }
        }).bounds(leftPos + 53, topPos + 57, 70, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF26262E);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF5C6E8B);
        g.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF5C6E8B);
        g.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF111116);
        g.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF111116);
        slotFrame(g, 30, 35);
        slotFrame(g, 130, 35);
    }

    private void slotFrame(GuiGraphics g, int x, int y) {
        int x0 = leftPos + x - 1, y0 = topPos + y - 1;
        g.fill(x0, y0, x0 + 18, y0 + 18, 0xFF111116);
        g.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, 0xFF33333E);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFFFF, false);
        g.drawString(font, "A", 30, 24, 0xFF8BE9FF, false);
        g.drawString(font, "B", 130, 24, 0xFFE8C86A, false);
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
