package com.abyssalloot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 武器・防具ガチャ (本編とは別のお楽しみ要素)。
 * <ul>
 *   <li>魂の欠片で引く: 1 回 {@link #COST_ONE} / 10 連 {@link #COST_TEN} (1 回分おまけ)。クリエイティブは無料</li>
 *   <li>排出率: 通常 40 / 魔法 30 / レア 20 / レジェンド 8 / 神話 2 (%)</li>
 *   <li>10 連はレア以上が 1 つ確定。神話が {@link #PITY} 回出なければ次は神話確定 (天井。回数はプレイヤーに保存)</li>
 *   <li>武器 60% (ドロップ対象の全武器種) / 防具 40% (うちセット防具 60%)</li>
 *   <li>手持ちが満杯なら保管庫へ送る</li>
 * </ul>
 */
public final class Gacha {
    private Gacha() {}

    public static final int COST_ONE = 30;
    public static final int COST_TEN = 270;
    public static final int PITY = 60;
    /** 通常・魔法・レア・レジェンド・神話 の重み (合計 100) */
    public static final int[] RATES = {40, 30, 20, 8, 2};
    public static final String[] RARITIES = {"normal", "magic", "rare", "legendary", "mythic"};

    static int countShards(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) n += s.getCount();
        }
        return n;
    }

    private static void takeShards(ServerPlayer p, int n) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) {
                int k = Math.min(n, s.getCount());
                s.shrink(k);
                n -= k;
            }
        }
    }

    static String rollRarity(RandomSource r) {
        int x = r.nextInt(100);
        for (int i = 0; i < RATES.length; i++) {
            if ((x -= RATES[i]) < 0) return RARITIES[i];
        }
        return "normal";
    }

    static ItemStack makeItem(RandomSource r, String rarity) {
        if (r.nextInt(100) < 60) {
            var styles = GameData.WeaponStyle.DROPPABLE;
            return GearFactory.makeWeaponOfRarity(styles[r.nextInt(styles.length)], rarity, r);
        }
        Item[] pieces = {ModItems.ABYSS_HELM.get(), ModItems.ABYSS_CHEST.get(), ModItems.ABYSS_LEGS.get(),
                ModItems.ABYSS_BOOTS.get()};
        Item piece = pieces[r.nextInt(pieces.length)];
        String set = null;
        if (r.nextInt(100) < 60) {
            var sets = GameData.ARMOR_SETS;
            set = sets.get(r.nextInt(sets.size())).id();
        }
        return GearFactory.makeArmorOfRarity(set, piece, rarity, r);
    }

    /** n 回引く (1 か 10)。支払えなければ何もしない。結果をクライアントへ送る */
    public static void pull(ServerPlayer p, int n) {
        pull(p, n, p.isCreative());
    }

    /** free = 欠片を払わない (クリエイティブ) */
    public static void pull(ServerPlayer p, int n, boolean free) {
        if (n != 1 && n != 10) return;
        int cost = n == 10 ? COST_TEN : COST_ONE;
        if (!free && countShards(p) < cost) {
            p.displayClientMessage(Component.literal("§c魂の欠片が足りない (必要 " + cost + " / 所持 " + countShards(p) + ")"), true);
            return;
        }
        if (!free) takeShards(p, cost);
        RandomSource r = p.getRandom();
        int since = p.getData(ModAttachments.GACHA_PITY.get());
        var results = new ArrayList<ItemStack>();
        var ranks = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            String rar = rollRarity(r);
            since++;
            if (since >= PITY) rar = "mythic";
            if (rar.equals("mythic")) since = 0;
            ranks.add(rar);
        }
        if (n == 10 && ranks.stream().allMatch(x -> GameData.rarityRank(x) < 2)) {
            ranks.set(9, "rare");  // 10 連はレア以上が 1 つ確定
        }
        for (String rar : ranks) {
            ItemStack it = makeItem(r, rar);
            results.add(it.copy());
            LootHandler.giveOrDrop(p, it);
        }
        p.setData(ModAttachments.GACHA_PITY.get(), since);
        p.setData(ModAttachments.GACHA_TOTAL.get(), p.getData(ModAttachments.GACHA_TOTAL.get()) + n);
        int best = ranks.stream().mapToInt(GameData::rarityRank).max().orElse(0);
        if (best >= 3) {
            p.level().playSound(null, p.blockPosition(), best == 4 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                    : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, best == 4 ? 1.0f : 1.3f);
        }
        ModPackets.send(p, new ModPackets.GachaResult(results, PITY - since, countShards(p), free));
    }

    /** 画面を開く (現在の欠片・天井までの回数を添える) */
    public static void open(ServerPlayer p) {
        ModPackets.send(p, new ModPackets.GachaResult(List.of(), PITY - p.getData(ModAttachments.GACHA_PITY.get()),
                countShards(p), p.isCreative()));
    }
}
