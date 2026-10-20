package com.abyssalloot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 命中ダメージの浮遊表示.
 * 表示はアーマースタンドのネームプレートで表現する (パケット不要・他のプレイヤーにも見える).
 * <ul>
 *   <li>0.2秒以内の連続ヒットは合算して1つの数字にまとめる (連撃でも読み切れない)</li>
 *   <li>会心=金色/太字・大技=淡紫/太字・通常=白</li>
 *   <li>1人あたりの同時表示数と寿命を制限して負荷を抑える</li>
 * </ul>
 */
public final class DamageNumbers {
    private DamageNumbers() {}

    /** 表示の寿命 (tick) */
    private static final int LIFETIME = 24;
    /** 1人あたりの同時表示上限 */
    private static final int MAX_ACTIVE = 10;
    /** 合算する時間幅 (tick) */
    private static final int MERGE_WINDOW = 4;
    /** 1tickあたりの上昇量 */
    private static final double RISE = 0.05;

    /** 浮遊表示1件 (アーマースタンド + 集計中のダメージ) */
    private static final class Active {
        final UUID owner;
        final UUID target;
        final ArmorStand stand;
        float total;
        boolean crit;
        boolean heal;
        int age;

        Active(UUID owner, UUID target, ArmorStand stand, float total, boolean crit, boolean heal) {
            this.owner = owner;
            this.target = target;
            this.stand = stand;
            this.total = total;
            this.crit = crit;
            this.heal = heal;
        }
    }

    /** 回復表示の合算キー (同じ対象キーとして扱う) */
    private static final UUID HEAL_KEY = new UUID(0L, 0L);

    private static final List<Active> ACTIVE = new ArrayList<>();

    /**
     * 与ダメージを表示する.
     *
     * @param attacker 攻撃したプレイヤー (表示数の上限管理用)
     * @param target   被弾した敵 (表示位置)
     * @param damage   実際に与えたダメージ
     * @param crit     会心か
     */
    public static void show(ServerPlayer attacker, LivingEntity target, float damage, boolean crit) {
        if (damage <= 0) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;
        // 攻撃者の反対側 (敵の影側) に表示する。攻撃の邪魔をしないように
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double ox = len > 1e-4 ? dx / len * 0.55 : 0;
        double oz = len > 1e-4 ? dz / len * 0.55 : 0;
        spawn(attacker, target.getUUID(),
                target.getX() + ox + (attacker.getRandom().nextDouble() - 0.5) * 0.4,
                target.getY() + target.getBbHeight() * 0.65,
                target.getZ() + oz + (attacker.getRandom().nextDouble() - 0.5) * 0.4,
                label(damage, crit), damage, crit);
    }

    /** 回復量を表示する (吸血・寿命回復) */
    public static void showHeal(ServerPlayer player, float amount) {
        if (amount <= 0.5f) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // 頭の上に表示する (自分の攻撃を邪魔しないため)
        spawn(player, HEAL_KEY,
                player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.6,
                player.getY() + player.getBbHeight() + 0.35,
                player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.6,
                Component.literal("+" + Math.round(amount)).withStyle(st -> st
                        .withColor(ChatFormatting.GREEN).withBold(true)),
                amount, false);
    }

    /** 浮遊数字を1つ立ち上げる (同一 대상・近接したものは合算) */
    private static void spawn(ServerPlayer attacker, UUID mergeKey, double x, double y, double z,
                              Component text, float amount, boolean crit) {
        ServerLevel level = (ServerLevel) attacker.level();

        // 直近の同一対象への表示があれば合算する (連撃・追撃のまとめ表示)
        Active last = null;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Active a = ACTIVE.get(i);
            if (a.owner.equals(attacker.getUUID()) && a.target.equals(mergeKey)
                    && a.age < MERGE_WINDOW) {
                last = a;
                break;
            }
        }
        if (last != null) {
            last.total += amount;
            last.crit = last.crit || crit;
            last.age = 0;
            last.stand.setCustomName(last.heal
                    ? Component.literal("+" + Math.round(last.total)).withStyle(st -> st
                            .withColor(ChatFormatting.GREEN).withBold(true))
                    : label(last.total, last.crit));
            return;
        }

        // 同時表示数が上限に達しているときは表示しない (戦闘の重さを保つ)
        int active = 0;
        for (Active a : ACTIVE) {
            if (a.owner.equals(attacker.getUUID())) active++;
        }
        if (active >= MAX_ACTIVE) return;

        ArmorStand stand = new ArmorStand(level, x, y, z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.addTag(TAG);
        level.addFreshEntity(stand);
        ACTIVE.add(new Active(attacker.getUUID(), mergeKey, stand, amount, crit, mergeKey == HEAL_KEY));
    }

    /** 浮遊数字アーマースタンドの識別タグ (誤って掴거나開かれたりしないようにする) */
    public static final String TAG = "abyss_damage_number";

    /** 数字表示の誤操作を防ぐ (アーマースタンドのGUIを開かせない) */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() != null && event.getTarget().getTags().contains(TAG)) {
            event.setCanceled(true);
        }
    }

    /** 毎tick: 浮遊表示の上昇と寿命管理 */
    public static void tick() {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Active a = ACTIVE.get(i);
            a.stand.setPos(a.stand.getX(), a.stand.getY() + RISE, a.stand.getZ());
            if (++a.age > LIFETIME) {
                a.stand.discard();
                ACTIVE.remove(i);
            }
        }
    }

    /** 数字の見た目 (会心=金・大技=淡紫・通常=白) */
    private static Component label(float damage, boolean crit) {
        int v = Math.max(0, Math.round(damage));
        String s = String.valueOf(v);
        if (crit) {
            return Component.literal(s).withStyle(st -> st.withColor(ChatFormatting.GOLD).withBold(true));
        }
        if (v >= 60) {
            return Component.literal(s).withStyle(st -> st.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true));
        }
        return Component.literal(s).withStyle(st -> st.withColor(ChatFormatting.WHITE));
    }
}
