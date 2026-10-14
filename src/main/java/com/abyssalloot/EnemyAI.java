package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 特殊敵・固有AIの毎tick挙動 (原作 engine.ts の各 ai に対応).
 *  - guard: 回頭制限 (毎tick)
 *  - summoner/gate: 召喚 (10tickごと)
 *  - shoot: 中距離 (8〜16m) を維持. 発射自体はバニラAI (Blaze/Breeze/Guardian/Pillager) に任せる
 *  - charge: 約6秒ごとに予兆 (鈍足+煙) → 突進 (速度バースト+衝撃波)
 *  - teleport: 約5秒ごとにプレイヤー近傍へ瞬間移動
 *  - orbit: プレイヤーの周囲を周回 (接触ダメージはバニラAI)
 *  - bomber: 接近したら即点火 (Creeper の自然爆発を確実にする)
 *  - split: 死亡時の分裂は CombatHandler → Enemies.handleSplit
 */
public final class EnemyAI {
    private EnemyAI() {}

    private static final int TICK_PERIOD = 10;
    private static final double SEARCH_RANGE = 48;
    private static final Map<UUID, Integer> NEXT_SUMMON = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_CHARGE = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_TELE = new HashMap<>();
    private static final Map<UUID, Double> ORBIT_ANG = new HashMap<>();

    public static void tick(MinecraftServer server, int now) {
        if (now % TICK_PERIOD != 0) return;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level)) continue;
            var found = level.getEntitiesOfClass(Mob.class,
                    p.getBoundingBox().inflate(64),
                    e -> e.isAlive() && !e.getTags().isEmpty());
            for (Mob m : found) {
                if (!seen.add(m.getUUID())) continue;
                var tags = m.getTags();
                // 時の歪み: 敵速度半減 (鈍足で再現)
                if (RunManager.hasFate(p, "timewarp")) {
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, false, false));
                }
                // 精鋭「再生」: 最大HPの2%/0.5秒を回復
                if (tags.contains(Enemies.ET_REGEN) && m.getHealth() < m.getMaxHealth()) {
                    m.heal(m.getMaxHealth() * 0.02f);
                }
                if (tags.contains(Enemies.NECRO_TAG)) {
                    tickNecromancer(level, m, now);
                } else if (tags.contains(Enemies.GATE_TAG)) {
                    tickGate(level, m, now);
                } else if (tags.contains(Enemies.CHARGE_TAG)) {
                    tickCharge(level, m, now);
                } else if (tags.contains(Enemies.TELE_TAG)) {
                    tickTeleport(level, m, now);
                } else if (tags.contains(Enemies.ORBIT_TAG)) {
                    tickOrbit(level, m, now);
                } else if (tags.contains(Enemies.SHOOT_TAG)) {
                    tickShooter(level, m, now);
                } else if (tags.contains(Enemies.BOMBER_TAG)) {
                    tickBomber(level, m, now);
                }
                tickVisuals(level, m, now, tags);
            }
        }
        // 倒れた敵の記録を捨てる
        NEXT_SUMMON.keySet().removeIf(id -> !seen.contains(id));
        NEXT_CHARGE.keySet().removeIf(id -> !seen.contains(id));
        NEXT_TELE.keySet().removeIf(id -> !seen.contains(id));
        ORBIT_ANG.keySet().removeIf(id -> !seen.contains(id));
        // 脱走収容 (壁を越えたMOBをアリーナ内へ戻す)
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() instanceof ServerLevel level) containMobs(p, level);
        }
    }

    /**
     * 脱走収容: アリーナ半径+3 を超えた深淵MOBを内側へ戻す.
     * 壁3段で地上MOBは防げるが、飛行 (VEX/PHANTOM/BLAZE)・登攀 (SPIDER)・
     * テレポート (ENDERMAN)・奥義の吹き飛びは壁を越えるため、0.5秒ごとに引き戻す.
     */
    private static void containMobs(ServerPlayer player, ServerLevel level) {
        BlockPos center = RunManager.arenaCenterOf(player);
        if (center == null) return;
        double cx = center.getX() + 0.5, cy = center.getY(), cz = center.getZ() + 0.5;
        double r = RunManager.ARENA_R;
        // 戦闘中のプレイヤーも場内に留める (ウェーブ進行中のみ)
        double pdx = player.getX() - cx, pdz = player.getZ() - cz;
        double pd = Math.sqrt(pdx * pdx + pdz * pdz);
        int total = player.getData(ModAttachments.WAVE_TOTAL.get());
        boolean active = total > 0 && player.getData(ModAttachments.PORTAL_OPEN.get()) == 0
                && player.getData(ModAttachments.FLOOR_KILLS.get()) < total;
        if (active && pd > r + 5) {
            double pang = Math.atan2(pdz, pdx);
            player.teleportTo(cx + Math.cos(pang) * (r - 3), cy + 1, cz + Math.sin(pang) * (r - 3));
            player.displayClientMessage(Component.literal("§c試練の場から出られない！"), true);
        }
        var strays = level.getEntitiesOfClass(Mob.class,
                new AABB(cx - 64, cy - 32, cz - 64, cx + 64, cy + 32, cz + 64),
                e -> e.isAlive() && (RunManager.BOSS_BARS.containsKey(e) || hasAbyssTag(e)));
        for (Mob m : strays) {
            double dx = m.getX() - cx, dz = m.getZ() - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= r + 3 && m.getY() <= cy + 12 && m.getY() >= cy - 8) continue;
            double ang = d > 1e-3 ? Math.atan2(dz, dx) : 0;
            double nx = cx + Math.cos(ang) * (r - 3);
            double nz = cz + Math.sin(ang) * (r - 3);
            m.teleportTo(nx, cy + 1, nz);
            m.getNavigation().stop();
            level.sendParticles(ParticleTypes.PORTAL, nx, cy + 1, nz, 16, 0.8, 1, 0.8, 0.1);
        }
    }

    private static boolean hasAbyssTag(Mob m) {
        for (String t : m.getTags()) {
            if (t.startsWith("abyss_")) return true;
        }
        return false;
    }

    /**
     * 見た目演出 (原作の発光オーラ・精鋭リング・HPバーに相当)。
     * 層主: 魂+魔女パーティクル / 精鋭 (abyss_et_*): 金の粉 /
     * 被弾した通常敵: 名札に HP を表示 (全快で戻す)。
     */
    private static void tickVisuals(ServerLevel level, Mob m, int now, Set<String> tags) {
        boolean boss = RunManager.BOSS_BARS.containsKey(m);
        boolean elite = false;
        for (String t : tags) {
            if (t.startsWith("abyss_et_")) {
                elite = true;
                break;
            }
        }
        if (boss && now % 10 == 0) {
            level.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(),
                    3, m.getBbWidth() * 0.5, m.getBbHeight() * 0.4, m.getBbWidth() * 0.5, 0.02);
            level.sendParticles(ParticleTypes.WITCH, m.getX(), m.getY() + 0.3, m.getZ(),
                    2, m.getBbWidth() * 0.6, 0.4, m.getBbWidth() * 0.6, 0.05);
        } else if (elite && now % 20 == 0) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xffd700, 1.0f),
                    m.getX(), m.getY() + m.getBbHeight() + 0.4, m.getZ(), 4, 0.5, 0.3, 0.5, 0.02);
        }
        if (!boss && m.getCustomName() != null) {
            String cur = m.getCustomName().getString();
            String base = cur.contains(HP_SUFFIX_HEAD)
                    ? cur.substring(0, cur.indexOf(HP_SUFFIX_HEAD)) : cur;
            if (m.getHealth() < m.getMaxHealth() - 0.5f) {
                m.setCustomName(Component.literal(base + HP_SUFFIX_HEAD
                        + Math.max(0, Math.ceil(m.getHealth())) + "/" + Math.round(m.getMaxHealth()) + "§8]"));
                m.setCustomNameVisible(true);
            } else if (cur.contains(HP_SUFFIX_HEAD)) {
                m.setCustomName(Component.literal(base));
                m.setCustomNameVisible(m.getTags().contains(Enemies.WARDEN_TAG)
                        || elite || tags.contains(Enemies.GATE_TAG) || tags.contains(Enemies.NECRO_TAG)
                        || tags.contains(Enemies.GUARD_TAG));
            }
        }
    }

    private static final String HP_SUFFIX_HEAD = " §8[§c";

    /** 回頭速度 (度/tick). 護衛兵は約80度/秒、突進・番人・層主は約120度/秒 */
    private static final double GUARD_TURN_PER_TICK = 4.0;
    private static final double HEAVY_TURN_PER_TICK = 6.0;

    /**
     * 毎tick: 原作の敵は回頭に時間がかかる。護衛兵・突進・番人・層主は
     * 最寄りのプレイヤーへ一定速度でしか向きを変えない (側面への回り込みが有効)。
     */
    public static void tickGuards(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel level)) continue;
            var heavies = level.getEntitiesOfClass(Mob.class,
                    p.getBoundingBox().inflate(64),
                    e -> e.isAlive() && !e.getTags().contains(Enemies.GATE_TAG)
                            && (e.getTags().contains(Enemies.GUARD_TAG)
                                    || e.getTags().contains(Enemies.CHARGE_TAG)
                                    || e.getTags().contains(Enemies.WARDEN_TAG)
                                    || RunManager.BOSS_BARS.containsKey(e)));
            for (Mob g : heavies) {
                double rate = g.getTags().contains(Enemies.GUARD_TAG)
                        ? GUARD_TURN_PER_TICK : HEAVY_TURN_PER_TICK;
                ServerPlayer t = nearestPlayer(level, g);
                if (t == null) continue;
                // MCの向き規約: 方向ベクトル = (-sin(yaw), cos(yaw))
                double dx = t.getX() - g.getX();
                double dz = t.getZ() - g.getZ();
                if (dx * dx + dz * dz < 1e-6) continue;
                double want = Math.toDegrees(Math.atan2(-dx, dz));
                float cur = g.getYRot();
                double diff = ((want - cur) % 360 + 540) % 360 - 180;
                double step = Math.max(-rate, Math.min(rate, diff));
                float next = (float) (cur + step);
                g.setYRot(next);
                g.setYBodyRot(next);
            }
        }
    }

    /** 最寄りの生存プレイヤー (48m以内) */
    private static ServerPlayer nearestPlayer(ServerLevel level, Mob m) {
        Player p = level.getNearestPlayer(m.getX(), m.getY(), m.getZ(), SEARCH_RANGE, false);
        return p instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    private static int floorOf(ServerPlayer p) {
        return Math.max(1, p.getData(ModAttachments.FLOOR.get()));
    }

    /** 射撃: 中距離を保つ (発射はバニラAI). 近すぎれば離れ、遠すぎれば寄る */
    private static void tickShooter(ServerLevel level, Mob m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) {
            m.getNavigation().stop();
            return;
        }
        double dx = m.getX() - target.getX();
        double dz = m.getZ() - target.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 8) {
            double len = Math.max(d, 1e-3);
            m.getNavigation().moveTo(m.getX() + dx / len * 8, m.getY(), m.getZ() + dz / len * 8, 1.0);
        } else if (d > 16) {
            m.getNavigation().moveTo(target, 1.0);
        } else {
            m.getNavigation().stop();
        }
        m.setTarget(target);
    }

    /** 突進: 約6秒ごとに予兆→突進. 突進時は衝撃波で周囲に打撃 */
    private static void tickCharge(ServerLevel level, Mob m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) {
            m.getNavigation().stop();
            return;
        }
        m.setTarget(target);
        double dx = target.getX() - m.getX();
        double dz = target.getZ() - m.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 4 && d < 20) {
            m.getNavigation().moveTo(target, 1.0);
        }
        if (now < NEXT_CHARGE.getOrDefault(m.getUUID(), 0)) return;
        if (d < 4 || d > 20) {
            NEXT_CHARGE.put(m.getUUID(), now + 20);
            return;
        }
        // 予兆 (鈍足+煙 1秒) → 突進
        m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3, false, false));
        level.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 1, m.getZ(), 16, 0.8, 0.5, 0.8, 0.05);
        final double len = Math.max(d, 1e-3);
        final double vx = dx / len;
        final double vz = dz / len;
        CombatHandler.after(20, () -> {
            if (!m.isAlive() || m.isRemoved()) return;
            m.push(vx * 2.4, 0.15, vz * 2.4);
            level.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + 0.5, m.getZ(), 20, 1, 0.5, 1, 0.1);
            CombatHandler.after(8, () -> {
                if (!m.isAlive() || m.isRemoved()) return;
                var atk = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                double dmg = atk != null ? atk.getValue() * 1.5 : 10;
                WeaponArts.enemyPulse(level, m.position(), 3.5, dmg, ParticleTypes.EXPLOSION);
            });
        });
        NEXT_CHARGE.put(m.getUUID(), now + 120);
    }

    /** 瞬間移動: 約5秒ごとにプレイヤー近傍 (±4m) へ */
    private static void tickTeleport(ServerLevel level, Mob m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) return;
        m.setTarget(target);
        if (now < NEXT_TELE.getOrDefault(m.getUUID(), 0)) return;
        double dx = target.getX() - m.getX();
        double dz = target.getZ() - m.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 6) {
            NEXT_TELE.put(m.getUUID(), now + 40);
            return;
        }
        level.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 24, 0.8, 1, 0.8, 0.2);
        double ang = m.getRandom().nextDouble() * Math.PI * 2;
        double nx = target.getX() + Math.cos(ang) * (3 + m.getRandom().nextDouble() * 2);
        double nz = target.getZ() + Math.sin(ang) * (3 + m.getRandom().nextDouble() * 2);
        m.teleportTo(nx, target.getY(), nz);
        level.sendParticles(ParticleTypes.PORTAL, nx, target.getY() + 1, nz, 24, 0.8, 1, 0.8, 0.2);
        NEXT_TELE.put(m.getUUID(), now + 100);
    }

    /** 周回: プレイヤーの周囲 (半径6m) を回る. 速度は原作通り速め */
    private static void tickOrbit(ServerLevel level, Mob m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) return;
        m.setTarget(target);
        double ang = ORBIT_ANG.getOrDefault(m.getUUID(), m.getRandom().nextDouble() * Math.PI * 2);
        ang += 0.35;
        ORBIT_ANG.put(m.getUUID(), ang);
        double nx = target.getX() + Math.cos(ang) * 6;
        double nz = target.getZ() + Math.sin(ang) * 6;
        m.getNavigation().moveTo(nx, target.getY(), nz, 1.25);
    }

    /** 自爆: 接近したら即点火 (Creeper の自然爆発を確実に) */
    private static void tickBomber(ServerLevel level, Mob m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) return;
        m.setTarget(target);
        double d = Math.sqrt(m.distanceToSqr(target));
        if (m instanceof Creeper creeper) {
            if (d < 4.5) creeper.ignite();
            if (d > 3) m.getNavigation().moveTo(target, 1.3);
        } else if (d < 2.5) {
            var atk = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            double dmg = atk != null ? atk.getValue() * 2 : 20;
            WeaponArts.enemyPulse(level, m.position(), 4, dmg, ParticleTypes.EXPLOSION);
            m.hurtServer(level, level.damageSources().magic(), Float.MAX_VALUE);
        } else {
            m.getNavigation().moveTo(target, 1.3);
        }
    }

    private static void tickNecromancer(ServerLevel level, Mob m, int now) {
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

    private static void tickGate(ServerLevel level, Mob m, int now) {
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
