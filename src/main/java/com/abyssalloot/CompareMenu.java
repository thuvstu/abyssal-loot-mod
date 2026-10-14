package com.abyssalloot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 装備比較GUIのコンテナ。スロットA/B (Abyss武器のみ) + プレイヤー所持品。
 * 比較計算は画面のボタン→パケット→サーバーで行い、結果テキストを返送する。
 */
public class CompareMenu extends AbstractContainerMenu {
    public static final int SLOT_A = 0;
    public static final int SLOT_B = 1;

    private final Container inputs = new SimpleContainer(2);

    public CompareMenu(int id, Inventory inv) {
        super(ModMenus.COMPARE.get(), id);
        addSlot(new Slot(inputs, SLOT_A, 30, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ModItems.styleOf(stack.getItem()) != null;
            }
        });
        addSlot(new Slot(inputs, SLOT_B, 130, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ModItems.styleOf(stack.getItem()) != null;
            }
        });
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
    public CompareMenu(int id, Inventory inv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(id, inv);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack in = slot.getItem();
            copy = in.copy();
            if (index < 2) {
                if (!moveItemStackTo(in, 2, slots.size(), true)) return ItemStack.EMPTY;
            } else if (ModItems.styleOf(in.getItem()) != null) {
                if (!moveItemStackTo(in, SLOT_A, SLOT_B + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
            if (in.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, inputs);
    }
}
