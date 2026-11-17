package com.abyssalloot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * エンドコンテンツ武器 (夜想曲ノクターン・相棒の剣) の共通基盤.
 * <ul>
 *   <li>HPシールド: HPの上に積む耐久。被ダメはまずシールドが吸収し、時間切れ・耐久0・ログアウトで消える。付与は加算</li>
 *   <li>不死: 致死ダメージを無効化して全回復 (トーテム演出。1回で消費)</li>
 *   <li>オート反撃: 被弾量 ×0.1×Lv を攻撃者のHPから直接削る</li>
 *   <li>最上位クラフト時の神話化 (接辞・特性・銘の付与)</li>
 * </ul>
 * 状態はすべて揮発 (ログアウトで破棄)。持続はサーバーtickで管理する。
 */
public final class Endgame {
    private Endgame() {}

    /** プレイヤーごとの一時バフ (tick はゲーム時刻での終了時刻) */
    private static final class Buffs {
        double shield;
        long shieldUntil;
        long undyingUntil;
        int counterLv;
        long counterUntil;
    }

    private static final Map<UUID, Buffs> BUFFS = new HashMap<>();

    private static Buffs buffs(ServerPlayer player) {
        return BUFFS.computeIfAbsent(player.getUUID(), k -> new Buffs());
    }

    private static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    /* ---------------- HPシールド ---------------- */

    /** シールドを加算付与する (持続は長い方に延長) */
    public static void addShield(ServerPlayer player, double amount, int ticks) {
        if (amount <= 0) return;
        Buffs b = buffs(player);
        long until = now(player) + ticks;
        if (b.shieldUntil < now(player)) b.shield = 0;
        b.shield += amount;
        b.shieldUntil = Math.max(b.shieldUntil, until);
    }

    /** 現在のシールド量 (切れていれば0) */
    public static double shieldOf(ServerPlayer player) {
        Buffs b = BUFFS.get(player.getUUID());
        if (b == null || b.shieldUntil < now(player)) return 0;
        return b.shield;
    }

    /* ---------------- 不死・オート反撃 ---------------- */

    public static void grantUndying(ServerPlayer player, int ticks) {
        Buffs b = buffs(player);
        b.undyingUntil = Math.max(b.undyingUntil, now(player) + ticks);
    }

    public static boolean hasUndying(ServerPlayer player) {
        Buffs b = BUFFS.get(player.getUUID());
        return b != null && b.undyingUntil >= now(player);
    }

    public static void grantCounter(ServerPlayer player, int lv, int ticks) {
        if (lv <= 0) return;
        Buffs b = buffs(player);
        b.counterLv = Math.max(b.counterLv, lv);
        b.counterUntil = Math.max(b.counterUntil, now(player) + ticks);
    }

    /* ---------------- 共通の攻撃処理 ---------------- */

    /**
     * 標的のHPを直接減らす (ダメージではなくHP操作)。
     * 0以下になる場合は撃破の扱い (報酬・層進行) を通すため、貫通ダメージでとどめを刺す.
     */
    public static void drainHealth(ServerPlayer player, LivingEntity target, double amount) {
        if (!CombatUtil.isValidTarget(target) || amount <= 0) return;
        ServerLevel level = (ServerLevel) player.level();
        float left = (float) (target.getHealth() - amount);
        if (left <= 0) {
            target.invulnerableTime = 0;
            WeaponArts.artHurt(level, target,
                    level.damageSources().indirectMagic(player, player), target.getHealth() + 1);
        } else {
            target.setHealth(left);
            DamageNumbers.show(player, target, (float) amount, false);
        }
        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                6, 0.3, 0.3, 0.3, 0.05);
    }

    /**
     * スキル攻撃: 通常ダメージと貫通 (防具無視) ダメージを 1:1 で与える.
     * 無敵時間で2撃目が削られないよう、各撃の前に無敵時間を外す.
     */
    public static void splitHit(ServerPlayer player, LivingEntity target, double dmg) {
        if (!CombatUtil.isValidTarget(target) || dmg <= 0) return;
        ServerLevel level = (ServerLevel) player.level();
        target.invulnerableTime = 0;
        WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player), (float) (dmg / 2));
        if (!target.isAlive()) return;
        target.invulnerableTime = 0;
        WeaponArts.artHurt(level, target, level.damageSources().indirectMagic(player, player), (float) (dmg / 2));
    }

    /** 貫通 (防具無視) のみの追撃。ノックバックなし */
    public static void pierceHit(ServerPlayer player, LivingEntity target, double dmg) {
        if (!CombatUtil.isValidTarget(target) || dmg <= 0) return;
        ServerLevel level = (ServerLevel) player.level();
        target.invulnerableTime = 0;
        WeaponArts.artHurt(level, target, level.damageSources().indirectMagic(player, player), (float) dmg);
    }

    /* ---------------- イベント ---------------- */

    /** 被弾: シールド吸収 → オート反撃 → ノクターン入力中の被弾記録 (最後に評価) */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDamaged(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float dmg = event.getNewDamage();
        if (dmg <= 0) return;
        Nocturne.recordTaken(player, dmg);
        Buffs b = BUFFS.get(player.getUUID());
        if (b == null) return;
        long t = now(player);
        if (b.counterLv > 0 && b.counterUntil >= t
                && event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != player) {
            drainHealth(player, attacker, dmg * 0.1 * b.counterLv);
        }
        if (b.shield > 0 && b.shieldUntil >= t) {
            double absorbed = Math.min(b.shield, dmg);
            b.shield -= absorbed;
            event.setNewDamage((float) (dmg - absorbed));
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1, player.getZ(), 4, 0.4, 0.5, 0.4, 0.02);
            if (b.shield <= 0) {
                player.displayClientMessage(Component.literal("§3HPシールドが砕けた"), true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                        SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
    }

    /** 不死: 致死ダメージを無効化して全回復 (1回で消費) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!hasUndying(player)) return;
        event.setCanceled(true);
        BUFFS.get(player.getUUID()).undyingUntil = 0;
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.level().broadcastEntityEvent(player, (byte) 35); // トーテムの演出
        player.sendSystemMessage(Component.literal(
                "§b[ノクターン] §6天命が終曲を拒んだ。§7あなたは再び立ち上がる。"));
    }

    /** 期限切れの掃除と、未刻印のエンドコンテンツ武器の神話化 (1秒ごと) */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        // シフトクリックの一括クラフトでは ItemCraftedEvent に空のスタックが渡ることがあるため、
        // 所持品に未刻印 (通常レア) のエンドコンテンツ武器があればここで刻む
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
                var style = ModItems.styleOf(s.getItem());
                if (style != null && style.isEndgame() && GearData.rarityOf(s).equals("normal")) {
                    stampMythic(s, style, p);
                }
            }
        }
        BUFFS.entrySet().removeIf(e -> {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) return true;
            Buffs b = e.getValue();
            long t = now(p);
            if (b.shieldUntil < t) b.shield = 0;
            if (b.counterUntil < t) b.counterLv = 0;
            return b.shield <= 0 && b.undyingUntil < t && b.counterLv == 0;
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BUFFS.remove(event.getEntity().getUUID());
        Nocturne.onLogout(event.getEntity().getUUID());
    }

    /** 最上位クラフト: 完成品を神話級にし、固有の銘と初期データを刻む */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack out = event.getCrafting();
        var style = ModItems.styleOf(out.getItem());
        if (style == null || !style.isEndgame()) return;
        if (!GearData.rarityOf(out).equals("normal")) return;
        stampMythic(out, style, player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1f, 1f);
        player.sendSystemMessage(Component.literal("§d§l✦ 最上位クラフト成功 ✦ §r")
                .append(out.getHoverName()));
    }

    /** 神話級の接辞・特性・装備スキルを抽選して刻む (クラフト・コマンド配布共通) */
    public static void stampMythic(ItemStack out, GameData.WeaponStyle style, ServerPlayer player) {
        var prize = GearFactory.makePrizeWeapon(style, player.getRandom(), StatCalc.luckOf(player), 27);
        var tag = prize.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString("abyss_rarity", "mythic");
        tag.remove("abyss_curse"); // エンドコンテンツは呪いなし
        tag.remove("abyss_curse_val");
        var old = out.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (String k : old.keySet()) {
            if (k.startsWith("noct_") || k.startsWith("aibou_")) tag.put(k, old.get(k));
        }
        out.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (style == GameData.WeaponStyle.NOCTURNE) {
            Nocturne.applyName(out);
        } else {
            Aibou.applyName(out);
        }
        GearData.refreshLore(out);
    }

    /* ---------------- アイテムデータの小道具 ---------------- */

    static CompoundTag tagOf(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    static int intOf(ItemStack stack, String key, int def) {
        return tagOf(stack).getIntOr(key, def);
    }

    /** CUSTOM_DATA の整数を書き換え、見た目切替 (CUSTOM_MODEL_DATA) も更新する */
    static void putInts(ItemStack stack, Map<String, Integer> values, int modelIndex) {
        CompoundTag tag = tagOf(stack);
        values.forEach(tag::putInt);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of((float) modelIndex), List.of(), List.of(), List.of()));
    }

    /** 防具・耐性を無視しない通常の被ダメ (スロット 000/444 の自傷用) */
    static DamageSource selfHarm(ServerPlayer player) {
        return player.level().damageSources().generic();
    }
}
