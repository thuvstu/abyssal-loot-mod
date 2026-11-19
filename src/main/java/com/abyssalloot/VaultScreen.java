package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Inventory;

/**
 * 深淵の保管庫の画面 (クライアント専用)。上に区分のタブ、右にページ送り・一括収納・整列。
 * 操作はすべて {@link ModPackets.VaultPacket} でサーバーへ送り、サーバー側で保管庫を書き換える。
 */
public class VaultScreen extends AbstractContainerScreen<Vault.Menu> {
    private static final int[] TAB_COLORS = {UiKit.GOLD, UiKit.CYAN, UiKit.GREEN, UiKit.VIOLET, UiKit.SUB};

    public VaultScreen(Vault.Menu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 232;
        this.inventoryLabelY = 140;
    }

    private static void send(int action, int arg) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new ModPackets.VaultPacket(action, arg)));
    }

    @Override
    protected void init() {
        super.init();
        int tw = (imageWidth - 12) / Vault.CATEGORIES.length;
        for (int i = 0; i < Vault.CATEGORIES.length; i++) {
            final int cat = i;
            addRenderableWidget(UiButton.of(Vault.CATEGORY_NAMES[i], leftPos + 6 + i * tw, topPos + 14, tw - 2, 13,
                    TAB_COLORS[i], () -> send(0, cat)));
        }
        int bx = leftPos + imageWidth + 4;
        addRenderableWidget(UiButton.of("◀ 前", bx, topPos + 30, 70, 16, UiKit.SUB, () -> send(1, -1)));
        addRenderableWidget(UiButton.of("次 ▶", bx, topPos + 50, 70, 16, UiKit.SUB, () -> send(1, 1)));
        addRenderableWidget(UiButton.of("一括収納", bx, topPos + 78, 70, 16, UiKit.GOLD, () -> send(2, 0)));
        addRenderableWidget(UiButton.of("+消耗品も", bx, topPos + 98, 70, 16, UiKit.VIOLET, () -> send(4, 0)));
        addRenderableWidget(UiButton.of("整列", bx, topPos + 126, 70, 16, UiKit.CYAN, () -> send(3, 0)));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int accent = TAB_COLORS[Math.floorMod(menu.category(), TAB_COLORS.length)];
        UiKit.containerPanel(g, menu, leftPos, topPos, imageWidth, imageHeight, accent);
        UiKit.divider(g, leftPos + 6, leftPos + imageWidth - 6, topPos + inventoryLabelY - 4, accent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int cat = Math.floorMod(menu.category(), Vault.CATEGORY_NAMES.length);
        g.drawString(font, "◆ " + title.getString() + " — " + Vault.CATEGORY_NAMES[cat], titleLabelX, 4, UiKit.TITLE, false);
        String page = (menu.page() + 1) + " / " + Vault.PAGES;
        UiKit.chip(g, font, page, imageWidth - font.width(page) - 14, 2, TAB_COLORS[cat]);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiKit.SUB, false);
    }
}
