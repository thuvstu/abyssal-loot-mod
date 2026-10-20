package com.abyssalloot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

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
        int age;

        Active(UUID owner, UUID target, ArmorStand stand, float total, boolean crit) {
            this.owner = owner;
            this.target = target;
            this.stand = stand;
            this.total = total;
            this.crit = crit;
        }
    }

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
        long now = level.getGameTime();

        // 直近の同一対象への表示があれば合算する (連撃・追撃のまとめ表示)
        Active last = null;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Active a = ACTIVE.get(i);
            if (a.owner.equals(attacker.getUUID()) && a.target.equals(target.getUUID())
                    && a.age < MERGE_WINDOW) {
                last = a;
                break;
            }
        }
        if (last != null) {
            last.total += damage;
            last.crit = last.crit || crit;
            last.age = 0;
            last.stand.setCustomName(label(last.total, last.crit));
            return;
        }

        // 同時表示数が上限に達しているときは表示しない (戦闘の重さを保つ)
        int active = 0;
        for (Active a : ACTIVE) {
            if (a.owner.equals(attacker.getUUID())) active++;
        }
        if (active >= MAX_ACTIVE) return;

        ArmorStand stand = new ArmorStand(level,
                target.getX() + (attacker.getRandom().nextDouble() - 0.5) * 0.7,
                target.getY() + target.getBbHeight() * 0.65,
                target.getZ() + (attacker.getRandom().nextDouble() - 0.5) * 0.7);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setCustomName(label(damage, crit));
        stand.setCustomNameVisible(true);
        level.addFreshEntity(stand);
        ACTIVE.add(new Active(attacker.getUUID(), target.getUUID(), stand, damage, crit));
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
