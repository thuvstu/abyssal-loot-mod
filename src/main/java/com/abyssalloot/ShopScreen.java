package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Inventory;

/**
 * 商店GUI (クライアント専用)。商品3点の購入ボタン付き。
 * 在庫・価格はサーバーと同期したプレビュー品から読む。購入可否はサーバー側で再検証。
 */
public class ShopScreen extends AbstractContainerScreen<ShopMenu> {
    public ShopScreen(ShopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < ShopMenu.SHOP_SIZE; i++) {
            final int idx = i + 1;
            addRenderableWidget(Button.builder(Component.literal("購入" + idx), b -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) {
                    conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ShopPacket(idx)));
                }
            }).bounds(leftPos + 32 + i * 36, topPos + 57, 32, 20).build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2A2A30);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF665544);
        g.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF665544);
        g.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF111116);
        g.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF111116);
        for (int i = 0; i < ShopMenu.SHOP_SIZE; i++) {
            int x0 = leftPos + 44 + i * 36 - 1, y0 = topPos + 35 - 1;
            g.fill(x0, y0, x0 + 18, y0 + 18, 0xFF111116);
            g.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, 0xFF3A3428);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFAAAAAA, false);
        if (minecraft == null || minecraft.player == null) return;
        int shards = 0;
        var pinv = minecraft.player.getInventory();
        for (int i = 0; i < pinv.getContainerSize(); i++) {
            var s = pinv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) shards += s.getCount();
        }
        g.drawString(font, "所持欠片: " + shards, 8, 60, 0xFFE8C86A, false);
        g.drawString(font, "購入品の特性はランダム", 8, 70, 0xFF888899, false);
    }
}
