package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
            addRenderableWidget(UiButton.of("購入", leftPos + 36 + i * 36, topPos + 57, 32, 16, UiKit.GOLD, () -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) {
                    conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ShopPacket(idx)));
                }
            }));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        UiKit.containerPanel(g, menu, leftPos, topPos, imageWidth, imageHeight, UiKit.GOLD);
        // 商品棚: 3 品を金の枠で囲む
        for (int i = 0; i < ShopMenu.SHOP_SIZE; i++) {
            g.renderOutline(leftPos + 42 + i * 36, topPos + 33, 20, 20, UiKit.alpha(UiKit.GOLD, 0xB0));
        }
        UiKit.divider(g, leftPos + 6, leftPos + imageWidth - 6, topPos + inventoryLabelY - 4, UiKit.GOLD);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "◆ " + title.getString(), titleLabelX, titleLabelY, UiKit.TITLE, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiKit.SUB, false);
        if (minecraft == null || minecraft.player == null) return;
        int shards = 0;
        var pinv = minecraft.player.getInventory();
        for (int i = 0; i < pinv.getContainerSize(); i++) {
            var s = pinv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) shards += s.getCount();
        }
        String own = "所持欠片 " + shards;
        UiKit.chip(g, font, own, imageWidth - font.width(own) - 14, 4, UiKit.GOLD);
        g.drawString(font, "購入品の特性はランダム", 8, 18, UiKit.MUTED, false);
    }
}
