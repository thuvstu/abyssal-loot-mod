package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
        addRenderableWidget(Button.builder(Component.literal("合成する"), b -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                conn.send(new ServerboundCustomPayloadPacket(new ModPackets.ForgePacket()));
            }
        }).bounds(leftPos + 53, topPos + 57, 70, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // 背景パネル (バニラの作業台に寄せた暗色)
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2A2A34);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF555566);
        g.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF555566);
        g.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF111116);
        g.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF111116);
        // スロット枠
        slotFrame(g, 44, 35);
        slotFrame(g, 116, 35);
        // 合成方向の矢印
        g.drawString(font, "→", leftPos + 84, topPos + 39, 0xFFE8C86A, false);
    }

    private void slotFrame(GuiGraphics g, int x, int y) {
        int x0 = leftPos + x - 1, y0 = topPos + y - 1;
        g.fill(x0, y0, x0 + 18, y0 + 18, 0xFF111116);
        g.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, 0xFF3A3A44);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFAAAAAA, false);
        if (minecraft == null || minecraft.player == null) return;
        g.drawString(font, "本体", 44, 24, 0xFF8BE9FF, false);
        g.drawString(font, "素材", 116, 24, 0xFFE8C86A, false);

        ItemStack main = menu.getSlot(ForgeMenu.SLOT_WEAPON).getItem();
        ItemStack sub = menu.getSlot(ForgeMenu.SLOT_MATERIAL).getItem();
        if (main.isEmpty() || sub.isEmpty()) {
            g.drawString(font, "2スロットにAbyss武器を置いてね", 8, 60, 0xFF888899, false);
            return;
        }
        int lv = GearData.forgeLv(main);
        if (lv >= 5) {
            g.drawString(font, "鍛造MAX (覚醒)", 8, 60, 0xFFFFD23C, false);
            return;
        }
        int cost = 3 + lv * 2;
        // 所持数 (クライアント側の表示用。確定はサーバー側)
        var inv = minecraft.player.getInventory();
        int shards = countIn(inv, ModItems.SOUL_SHARD.get().getDefaultInstance().getItem());
        g.drawString(font, "欠片×" + cost + " (所持" + shards + ")", 8, 60, shards >= cost ? 0xFF7CFFB2 : 0xFFFF5C6E, false);
        var mat = GameData.mergeMaterial(topRarity(GearData.rarityOf(main), GearData.rarityOf(sub)));
        if (mat != null) {
            net.minecraft.world.item.Item matItem = switch (mat.id()) {
                case "void" -> ModItems.VOID_ESSENCE.get();
                case "chaos" -> ModItems.CHAOS_GEM.get();
                default -> ModItems.ANCIENT_STONE.get();
            };
            int have = countIn(inv, matItem);
            g.drawString(font, mat.name() + " (所持" + have + ")", 8, 70,
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
