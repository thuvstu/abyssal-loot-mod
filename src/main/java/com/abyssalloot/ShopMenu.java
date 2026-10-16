package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 商店GUIのコンテナ。スロット0-2=商品プレビュー (持出・投入不可) + プレイヤー所持品。
 * 購入は画面のボタン→パケット→ {@link RunManager#doBuy} で行う。
 * 価格はプレビュー品の CustomData ("abyss_price") に保持し、クライアント表示に使う。
 */
public class ShopMenu extends AbstractContainerMenu {
    public static final int SHOP_SIZE = 3;

    private final Container previews = new SimpleContainer(SHOP_SIZE);

    public ShopMenu(int id, Inventory inv) {
        super(ModMenus.SHOP.get(), id);
        if (inv.player instanceof ServerPlayer sp) {
            fillPreviews(sp);
        }
        for (int i = 0; i < SHOP_SIZE; i++) {
            final int slot = i;
            addSlot(new Slot(previews, slot, 44 + slot * 36, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    /** 追加データ付きの開設用 (IMenuTypeExtension.create が要求する形) */
    public ShopMenu(int id, Inventory inv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(id, inv);
    }

    /** SHOP添付 "style:price,..." からプレビュー品を生成 */
    private void fillPreviews(ServerPlayer sp) {
        String raw = sp.getData(ModAttachments.SHOP.get());
        if (raw == null || raw.isBlank()) return;
        String[] items = raw.split(",");
        for (int i = 0; i < Math.min(SHOP_SIZE, items.length); i++) {
            String[] kv = items[i].split(":");
            if (kv.length != 2) continue;
            var st = GameData.WeaponStyle.byId(kv[0]);
            int price;
            try {
                price = Integer.parseInt(kv[1]);
            } catch (NumberFormatException ex) {
                continue;
            }
            var stack = new ItemStack(ModItems.itemOf(st));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("§e" + st.weaponName + " §7— 欠片×" + price + " (奥義「" + st.artName + "」)"));
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("abyss_price", price);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            GearData.refreshLore(stack);
            previews.setItem(i, stack);
        }
    }

    /** プレビュー品の価格 (なければ -1)。クライアント表示用 */
    public static int priceOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return data.copyTag().getInt("abyss_price").orElse(-1);
    }

    /** 購入済み表示に差し替え (再購入の誤解防止。即時同期される) */
    public void markSold(int index) {
        if (index < 1 || index > SHOP_SIZE) return;
        var sold = new ItemStack(net.minecraft.world.item.Items.BARRIER);
        sold.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§7売約済み"));
        previews.setItem(index - 1, sold);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // プレビュー品の持出し・投入は不可
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
