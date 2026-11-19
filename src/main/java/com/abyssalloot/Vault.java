package com.abyssalloot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 深淵の保管庫: 持ち物があふれる問題への対策。
 * <ul>
 *   <li>区分 (武器・防具・素材・消耗品・その他) ごとに 4 ページ × 54 マス。プレイヤーに保存される (死亡・ログアウトで消えない)</li>
 *   <li>「一括収納」で手持ち (ホットバー・装備中・魂の欠片以外) の mod アイテムを区分ごとに振り分け、同じ物はまとめる</li>
 *   <li>戦利品の受け取りで手持ちが満杯なら、地面に落とさず保管庫へ送る ({@link LootHandler#giveOrDrop})</li>
 *   <li>「整列」で今のページを希少度・名前順に並べ、同じ物をまとめる</li>
 * </ul>
 */
public final class Vault {
    private Vault() {}

    public static final String[] CATEGORIES = {"weapon", "armor", "material", "consumable", "other"};
    public static final String[] CATEGORY_NAMES = {"武器", "防具", "素材", "消耗品", "その他"};
    public static final int PAGES = 4;
    public static final int PAGE_SIZE = 54;
    public static final int CAPACITY = PAGES * PAGE_SIZE;

    /** アイテムの区分 (CATEGORIES の添字) */
    public static int categoryOf(ItemStack stack) {
        var item = stack.getItem();
        if (ModItems.styleOf(item) != null || ModItems.isTool(item)) return 0;
        if (ModItems.slotOf(item) != null) return 1;
        if (ModItems.consumableKindOf(item) != null) return 3;
        if (AbyssalLootMod.MODID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) return 2;
        return 4;
    }

    private static boolean isShard(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().startsWith("soul_shard");
    }

    static List<ItemStack> list(ServerPlayer player, int cat) {
        Map<String, List<ItemStack>> all = player.getData(ModAttachments.VAULT.get());
        var l = all.get(CATEGORIES[cat]);
        var out = new ArrayList<ItemStack>(CAPACITY);
        if (l != null) for (ItemStack s : l) out.add(s.copy());
        while (out.size() < CAPACITY) out.add(ItemStack.EMPTY);
        return out;
    }

    static void put(ServerPlayer player, int cat, List<ItemStack> items) {
        var all = new HashMap<>(player.getData(ModAttachments.VAULT.get()));
        int last = items.size();
        while (last > 0 && items.get(last - 1).isEmpty()) last--;
        all.put(CATEGORIES[cat], List.copyOf(items.subList(0, last).stream().map(ItemStack::copy).toList()));
        player.setData(ModAttachments.VAULT.get(), all);
    }

    /** 保管庫へ入れる (同じ物にまとめてから空きへ)。入りきらなかった残りを返す */
    public static ItemStack store(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int cat = categoryOf(stack);
        var items = list(player, cat);
        ItemStack rest = stack.copy();
        for (int i = 0; i < items.size() && !rest.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, rest) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n);
                rest.shrink(n);
            }
        }
        for (int i = 0; i < items.size() && !rest.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                int n = Math.min(rest.getCount(), rest.getMaxStackSize());
                items.set(i, rest.copyWithCount(n));
                rest.shrink(n);
            }
        }
        put(player, cat, items);
        return rest;
    }

    /** 一括収納: ホットバー (0-8) と装備中・魂の欠片を除く mod アイテムを保管庫へ。consumables=true で消耗品も */
    public static int stashAll(ServerPlayer player, boolean consumables) {
        var inv = player.getInventory();
        int moved = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || isShard(s)) continue;
            int cat = categoryOf(s);
            if (cat == 4 || (cat == 3 && !consumables)) continue;
            ItemStack rest = store(player, s);
            moved += s.getCount() - rest.getCount();
            inv.setItem(i, rest);
        }
        return moved;
    }

    /** 希少度 → 名前 の順に並べ、同じ物をまとめる */
    static void sortList(List<ItemStack> items) {
        var merged = new ArrayList<ItemStack>();
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            ItemStack rest = s.copy();
            for (ItemStack m : merged) {
                if (rest.isEmpty()) break;
                if (ItemStack.isSameItemSameComponents(m, rest) && m.getCount() < m.getMaxStackSize()) {
                    int n = Math.min(rest.getCount(), m.getMaxStackSize() - m.getCount());
                    m.grow(n);
                    rest.shrink(n);
                }
            }
            if (!rest.isEmpty()) merged.add(rest);
        }
        merged.sort(Comparator.<ItemStack>comparingInt(s -> -GameData.rarityRank(GearData.rarityOf(s)))
                .thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparing(s -> s.getHoverName().getString()));
        for (int i = 0; i < items.size(); i++) items.set(i, i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
    }

    public static void open(ServerPlayer player, int cat) {
        player.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Menu(id, inv, cat), Component.literal("深淵の保管庫")));
    }

    /* ================================================================ メニュー */

    public static class Menu extends AbstractContainerMenu {
        private final SimpleContainer view = new SimpleContainer(PAGE_SIZE);
        /** [0]=区分 [1]=ページ (クライアントへ同期) */
        final ContainerData data;
        private final Player owner;

        public Menu(int id, Inventory inv, int cat) {
            super(ModMenus.VAULT.get(), id);
            this.owner = inv.player;
            this.data = new SimpleContainerData(2);
            data.set(0, cat);
            addDataSlots(data);
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 9; c++) addSlot(new Slot(view, c + r * 9, 8 + c * 18, 30 + r * 18));
            }
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 150 + r * 18));
            }
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 208));
            load();
        }

        public Menu(int id, Inventory inv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
            this(id, inv, 0);
        }

        private void load() {
            if (!(owner instanceof ServerPlayer sp)) return;
            var items = list(sp, data.get(0));
            int base = data.get(1) * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE; i++) view.setItem(i, items.get(base + i));
        }

        private void save() {
            if (!(owner instanceof ServerPlayer sp)) return;
            int cat = data.get(0);
            var items = list(sp, cat);
            int base = data.get(1) * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE; i++) items.set(base + i, view.getItem(i).copy());
            put(sp, cat, items);
        }

        /** ボタン (VaultPacket): 0=区分を選ぶ / 1=ページ送り (arg=±1) / 2=一括収納 / 3=整列 / 4=消耗品も一括収納 */
        public void act(ServerPlayer sp, int action, int arg) {
            save();
            switch (action) {
                case 0 -> {
                    data.set(0, Math.floorMod(arg, CATEGORIES.length));
                    data.set(1, 0);
                }
                case 1 -> data.set(1, Math.floorMod(data.get(1) + arg, PAGES));
                case 2, 4 -> {
                    int n = stashAll(sp, action == 4);
                    sp.displayClientMessage(Component.literal(n > 0 ? "§a" + n + " 個を保管庫へ振り分けた" : "§7収納する物がない"), true);
                }
                case 3 -> {
                    int cat = data.get(0);
                    var items = list(sp, cat);
                    sortList(items);
                    put(sp, cat, items);
                }
                default -> {}
            }
            load();
            broadcastChanges();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack before = stack.copy();
            if (index < PAGE_SIZE) {
                if (!moveItemStackTo(stack, PAGE_SIZE, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, PAGE_SIZE, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            return before;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            save();
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isAlive();
        }

        public int category() {
            return data.get(0);
        }

        public int page() {
            return data.get(1);
        }
    }
}
