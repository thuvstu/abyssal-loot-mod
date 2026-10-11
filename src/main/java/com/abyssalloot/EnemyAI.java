package com.abyssalloot;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 特殊敵の毎tick挙動 (原作 engine.ts の summoner / gate の AI).
 *  - 骸骨の蘇生師: 中距離 (5〜10m) を維持し、朽ちた兵を召喚. 生存する子は最大4体.
 *  - 呪われた召喚門: 移動・攻撃をせず、朽ちた兵を増援. 生存する子は最大6体.
 * 両者とも画面内の敵が SCREEN_CAP を超えたら召喚しない.
 * 処理は10tickごと. 召喚の次回時刻は UUID ごとに保持する.
 */
public final class EnemyAI {
    private EnemyAI() {}

    private static final int TICK_PERIOD = 10;
    private static final double SEARCH_RANGE = 48;
    private static final Map<UUID, Integer> NEXT_SUMMON = new HashMap<>();

    public static void tick(MinecraftServer server, int now) {
        if (now % TICK_PERIOD != 0) return;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level)) continue;
            var found = level.getEntitiesOfClass(Monster.class,
                    p.getBoundingBox().inflate(64),
                    e -> e.isAlive() && (e.getTags().contains(Enemies.NECRO_TAG)
                            || e.getTags().contains(Enemies.GATE_TAG)));
            for (Monster m : found) {
                if (!seen.add(m.getUUID())) continue;
                if (m.getTags().contains(Enemies.NECRO_TAG)) {
                    tickNecromancer(level, m, now);
                } else {
                    tickGate(level, m, now);
                }
            }
        }
        // 倒れた敵の記録を捨てる
        NEXT_SUMMON.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 最寄りの生存プレイヤー (48m以内) */
    private static ServerPlayer nearestPlayer(ServerLevel level, Monster m) {
        Player p = level.getNearestPlayer(m.getX(), m.getY(), m.getZ(), SEARCH_RANGE, false);
        return p instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    private static int floorOf(ServerPlayer p) {
        return Math.max(1, p.getData(ModAttachments.FLOOR.get()));
    }

    private static void tickNecromancer(ServerLevel level, Monster m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) {
            m.getNavigation().stop();
            return;
        }
        // 中距離を維持: 近すぎれば離れ、遠すぎれば寄る
        double dx = m.getX() - target.getX();
        double dz = m.getZ() - target.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 5) {
            double len = Math.max(d, 1e-3);
            m.getNavigation().moveTo(m.getX() + dx / len * 8, m.getY(), m.getZ() + dz / len * 8, 1.1);
        } else if (d > 10) {
            m.getNavigation().moveTo(target, 1.0);
        } else {
            m.getNavigation().stop();
        }
        m.setTarget(target);

        if (now < NEXT_SUMMON.getOrDefault(m.getUUID(), 0)) return;
        int alive = Enemies.childrenOf(level, m);
        if (alive >= Enemies.NECRO_CHILD_CAP || Enemies.aliveNear(level, m.getX(), m.getY(), m.getZ()) >= Enemies.SCREEN_CAP) {
            NEXT_SUMMON.put(m.getUUID(), now + 20); // 空きが出たらすぐ再判定
            return;
        }
        var scale = Enemies.scaleFor(target, floorOf(target));
        int n = Math.min(2, Enemies.NECRO_CHILD_CAP - alive);
        for (int i = 0; i < n; i++) {
            Enemies.spawnChild(level, m, scale, target, m.getRandom().nextDouble() * Math.PI * 2, 3);
        }
        level.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 20, 1, 0.5, 1, 0.1);
        target.displayClientMessage(Component.literal("§5蘇生師が朽ちた兵を呼んだ"), true);
        NEXT_SUMMON.put(m.getUUID(), now + Enemies.NECRO_SUMMON_TICKS);
    }

    private static void tickGate(ServerLevel level, Monster m, int now) {
        if (now < NEXT_SUMMON.getOrDefault(m.getUUID(), 0)) return;
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) {
            NEXT_SUMMON.put(m.getUUID(), now + 40);
            return;
        }
        int alive = Enemies.childrenOf(level, m);
        if (alive >= Enemies.GATE_CHILD_CAP || Enemies.aliveNear(level, m.getX(), m.getY(), m.getZ()) >= Enemies.SCREEN_CAP) {
            NEXT_SUMMON.put(m.getUUID(), now + 20);
            return;
        }
        var scale = Enemies.scaleFor(target, floorOf(target));
        int n = Math.min(2, Enemies.GATE_CHILD_CAP - alive);
        for (int i = 0; i < n; i++) {
            Enemies.spawnChild(level, m, scale, target, m.getRandom().nextDouble() * Math.PI * 2, 3);
        }
        level.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 30, 1, 1, 1, 0.2);
        NEXT_SUMMON.put(m.getUUID(), now + Enemies.GATE_SUMMON_TICKS);
    }
}
