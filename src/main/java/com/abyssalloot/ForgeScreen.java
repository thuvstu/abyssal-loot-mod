package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 鍛造画面 (クライアント専用。サーバー側からは参照しないこと)。
 * テクスチャ不要の自前描画 + 合成ボタン。費用・素材の所持数はクライアント側の所持品から表示し、
 * 合成の可否はサーバー側で再検証される。
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    public ForgeScreen(ForgeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(UiButton.of("合成する", leftPos + 53, topPos + 58, 70, 16, UiKit.GOLD, () -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ForgePacket()));
            }
        }));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        UiKit.containerPanel(g, menu, leftPos, topPos, imageWidth, imageHeight, UiKit.GOLD);
        // 合成スロットを色枠で強調し、間に合成方向の矢印
        g.renderOutline(leftPos + 42, topPos + 33, 20, 20, UiKit.alpha(UiKit.CYAN, 0xC0));
        g.renderOutline(leftPos + 114, topPos + 33, 20, 20, UiKit.alpha(UiKit.GOLD, 0xC0));
        g.drawCenteredString(font, ">>", leftPos + 88, topPos + 39, UiKit.GOLD);
        UiKit.divider(g, leftPos + 6, leftPos + imageWidth - 6, topPos + inventoryLabelY - 4, UiKit.GOLD);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "◆ " + title.getString(), titleLabelX, titleLabelY, UiKit.TITLE, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiKit.SUB, false);
        if (minecraft == null || minecraft.player == null) return;
        g.drawString(font, "本体", 44, 23, UiKit.CYAN, false);
        g.drawString(font, "素材", 116, 23, UiKit.GOLD, false);

        ItemStack main = menu.getSlot(ForgeMenu.SLOT_WEAPON).getItem();
        ItemStack sub = menu.getSlot(ForgeMenu.SLOT_MATERIAL).getItem();
        if (main.isEmpty() || sub.isEmpty()) {
            g.drawString(font, "2スロットにAbyss武器を置いてね", 8, 82, 0xFF888899, false);
            return;
        }
        int lv = GearData.forgeLv(main);
        if (lv >= 5) {
            g.drawString(font, "鍛造MAX (覚醒)", 8, 82, 0xFFFFD23C, false);
            return;
        }
        // サーバー側 FloorEvents.doMergeStacks の実費式に合わせる (威力分の加算を含む)
        int cost = 3 + lv * 2 + (GearData.powerOf(main) + GearData.powerOf(sub) + 12) / 25;
        // 所持数 (クライアント側の表示用。確定はサーバー側)
        var inv = minecraft.player.getInventory();
        int shards = countIn(inv, ModItems.SOUL_SHARD.get().getDefaultInstance().getItem());
        g.drawString(font, "欠片×" + cost + " (所持" + shards + ")", 8, 82, shards >= cost ? 0xFF7CFFB2 : 0xFFFF5C6E, false);
        var mat = GameData.mergeMaterial(topRarity(GearData.rarityOf(main), GearData.rarityOf(sub)));
        if (mat != null) {
            net.minecraft.world.item.Item matItem = switch (mat.id()) {
                case "void" -> ModItems.VOID_ESSENCE.get();
                case "chaos" -> ModItems.CHAOS_GEM.get();
                default -> ModItems.ANCIENT_STONE.get();
            };
            int have = countIn(inv, matItem);
            g.drawString(font, mat.name() + " (所持" + have + ")", 8, 92,
                    have >= mat.count() ? 0xFF7CFFB2 : 0xFFFF5C6E, false);
        }
    }

    private static String topRarity(String a, String b) {
        return GameData.rarityRank(b) > GameData.rarityRank(a) ? b : a;
    }

    private static int countIn(Inventory inv, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }
}
