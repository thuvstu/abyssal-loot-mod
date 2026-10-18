package com.abyssalloot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 鍛造画面のコンテナ。スロット0=本体武器、1=素材武器 + プレイヤーインベントリ。
 * 合成は画面のボタン→パケット→ {@link FloorEvents#doMergeStacks} で行い、
 * 結果はスロット0の実スタックに直接反映される (自動同期)。
 */
public class ForgeMenu extends AbstractContainerMenu {
    public static final int SLOT_WEAPON = 0;
    public static final int SLOT_MATERIAL = 1;

    private final Container inputs = new SimpleContainer(2);

    public ForgeMenu(int id, Inventory inv) {
        super(ModMenus.FORGE.get(), id);
        initSlots(inv);
    }

    /** 追加データ付きの開設用 (IMenuTypeExtension.create が要求する形) */
    public ForgeMenu(int id, Inventory inv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(id, inv);
    }

    private void initSlots(Inventory inv) {
        addSlot(new Slot(inputs, SLOT_WEAPON, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ModItems.styleOf(stack.getItem()) != null
                        || ModItems.slotOf(stack.getItem()) != null;
            }
        });
        addSlot(new Slot(inputs, SLOT_MATERIAL, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ModItems.styleOf(stack.getItem()) != null
                        || ModItems.slotOf(stack.getItem()) != null;
            }
        });
        // プレイヤーインベントリ (3行) + ホットバー
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
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
            } else if (ModItems.styleOf(in.getItem()) != null || ModItems.slotOf(in.getItem()) != null) {
                if (!moveItemStackTo(in, SLOT_WEAPON, SLOT_MATERIAL + 1, false)) {
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
