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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 特殊敵・固有AIの毎tick挙動 (原作 engine.ts の各 ai に対応).
 *  - guard: 回頭制限 (毎tick)
 *  - summoner/gate: 召喚 (判定は10tick周期・召喚間隔は 140/116tick)
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
    /** 悪夢射撃の扇状3発の次回発射 tick */
    private static final Map<UUID, Integer> NEXT_FAN = new HashMap<>();
    /** 悪夢「狂乱」の発動済みタグ (一度だけ発動) */
    private static final String NT_BERSERK_DONE = "abyss_nt_berserk_done";

    public static void tick(MinecraftServer server, int now) {
        if (now % TICK_PERIOD != 0) return;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() instanceof ServerLevel level) {
                tickPlayerEnemies(p, level, now, seen);
            }
        }
        pruneStaleRecords(seen);
        // 脱走収容 (壁を越えたMOBをアリーナ内へ戻す)。重いAABB走査なので 1秒周期 (元は0.5秒) に間引く
        if (now % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level() instanceof ServerLevel level) containMobs(p, level);
            }
        }
    }

    /** 1プレイヤーの周囲にいる深淵MOBを列挙し、個別処理へ回す */
    private static void tickPlayerEnemies(ServerPlayer p, ServerLevel level, int now, Set<UUID> seen) {
        // 囮 (特殊スキル「囮」): 16m内の囮スタンドを拾い、近くの深淵敵の注意を6秒間逸らす
        var decoys = level.getEntitiesOfClass(ArmorStand.class,
                p.getBoundingBox().inflate(24),
                a -> a.isAlive() && a.getTags().contains(SubSkills.DECOY_TAG));
        var found = level.getEntitiesOfClass(Mob.class,
                p.getBoundingBox().inflate(64),
                e -> e.isAlive() && (!e.getTags().isEmpty() || RunManager.BOSS_BARS.containsKey(e)));
        for (Mob m : found) {
            if (!seen.add(m.getUUID())) continue;
            tickMob(level, m, p, decoys, now);
        }
    }

    /** 個々のMOBの毎tick処理 (状態異常・囮・AIディスパッチ・演出) */
    private static void tickMob(ServerLevel level, Mob m, ServerPlayer p,
                                List<ArmorStand> decoys, int now) {
        var tags = m.getTags();
        // 時の歪み: 敵速度半減 (鈍足で再現)
        if (RunManager.hasFate(p, "timewarp")) {
            m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, false, false));
        }
        // 囮の注意引きつけ: 12m内の敵は囮へ向く (層主は囮に引かれない)
        if (!decoys.isEmpty() && !RunManager.BOSS_BARS.containsKey(m)) {
            for (var d : decoys) {
                if (m.position().distanceToSqr(d.position()) < 12 * 12) {
                    m.setTarget(d);
                    break;
                }
            }
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
        tickNightmare(level, m, now, tags);
    }

    /** 倒れた・見失った敵の記録を捨てる */
    private static void pruneStaleRecords(Set<UUID> seen) {
        NEXT_SUMMON.keySet().removeIf(id -> !seen.contains(id));
        NEXT_CHARGE.keySet().removeIf(id -> !seen.contains(id));
        NEXT_TELE.keySet().removeIf(id -> !seen.contains(id));
        ORBIT_ANG.keySet().removeIf(id -> !seen.contains(id));
        SHOOT_DIR.keySet().removeIf(id -> !seen.contains(id));
        NEXT_FAN.keySet().removeIf(id -> !seen.contains(id));
    }

    /**
     * 脱走収容: アリーナ半径+3 を超えた深淵MOBを内側へ戻す.
     * 壁3段で地上MOBは防げるが、飛行 (VEX/PHANTOM/BLAZE)・登攀 (SPIDER)・
     * テレポート (ENDERMAN)・奥義の吹き飛びは壁を越えるため、1秒ごとに引き戻す.
     */
    private static void containMobs(ServerPlayer player, ServerLevel level) {
        BlockPos center = ArenaManager.arenaCenterOf(player);
        if (center == null) return;
        double cx = center.getX() + 0.5, cy = center.getY(), cz = center.getZ() + 0.5;
        double r = ArenaManager.ARENA_R;
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
        return LootHandler.hasAbyssTag(m);
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
            tickBossEnrage(level, m);
        } else if (elite && now % 20 == 0) {
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xffd700, 1.0f),
                    m.getX(), m.getY() + m.getBbHeight() + 0.4, m.getZ(), 4, 0.5, 0.3, 0.5, 0.02);
        }
        // 名札HP表示は深淵MOB限定 (他MOD・バニラ由来の名札付きMOBを加工しない)
        if (!boss && hasAbyssTag(m) && m.getCustomName() != null) {
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

    /** 層主の激高フラグタグ (一度だけ発動) */
    private static final String ENRAGE_TAG = "abyss_enraged";

    /**
     * 層主の激高: HP 35% 未満で一度だけ移動速度+攻撃が跳ね上がる。
     * (真ボス・終彦の座はフェーズ制弾幕があるが激高も持たせる。音・チャット告知つき)
     */
    private static void tickBossEnrage(ServerLevel level, Mob boss) {
        if (boss.getTags().contains(ENRAGE_TAG)) return;
        if (boss.getHealth() / Math.max(1, boss.getMaxHealth()) > 0.35) return;
        boss.addTag(ENRAGE_TAG);
        boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 999999, 1, false, false));
        var atk = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() * 1.25);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                boss.getX(), boss.getY() + boss.getBbHeight() + 0.4, boss.getZ(), 12, 0.6, 0.4, 0.6, 0.05);
        level.playSound(null, boss.blockPosition(),
                net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.HOSTILE,
                1.0f, 1.15f);
        var target = level.getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), 64, false);
        if (target != null) {
            target.displayClientMessage(Component.literal(
                    "§c◆ " + boss.getName().getString() + " が激高した！ 刻まれた体力を最低まで見せてやる！"), false);
        }
    }

    /** 回頭速度 (度/tick). 護衛兵は約80度/秒、突進・番人・層主は約120度/秒 */
    private static final double GUARD_TURN_PER_TICK = 4.0;
    private static final double HEAVY_TURN_PER_TICK = 6.0;

    /**
     * 毎tick: 原作の敵は回頭に時間がかかる。護衛兵・突進・番人・層主は
     * 最寄りのプレイヤーへ一定速度でしか向きを変えない (側面への回り込みが有効)。
     */
    public static void tickGuards(MinecraftServer server, int now) {
        // 2tickごとに2tick分まとめて回頭 (秒間の回頭量は同一)。毎tickの entity scan を半減
        if (now % 2 != 0) return;
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
                double rate = 2 * (g.getTags().contains(Enemies.GUARD_TAG)
                        ? GUARD_TURN_PER_TICK : HEAVY_TURN_PER_TICK);
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

    /** 上層ほどAIの手数が増える (突進・瞬間移動・召喚の間隔短縮、周回加速) */
    private static double tempoOf(ServerPlayer target) {
        return 1 + floorOf(target) * 0.03;
    }

    private static int scaledInterval(ServerPlayer target, int base, int min) {
        return Math.max(min, (int) (base / tempoOf(target)));
    }

    /** 中距離維持 (射撃・蘇生師共通): 近すぎれば離れ、遠すぎれば寄る。それ以外は停止 */
    private static void keepDistance(Mob m, ServerPlayer target, double low, double high,
                                     double retreatSpeed, double approachSpeed) {
        double dx = m.getX() - target.getX();
        double dz = m.getZ() - target.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < low) {
            double len = Math.max(d, 1e-3);
            m.getNavigation().moveTo(m.getX() + dx / len * 8, m.getY(), m.getZ() + dz / len * 8, retreatSpeed);
        } else if (d > high) {
            m.getNavigation().moveTo(target, approachSpeed);
        } else {
            m.getNavigation().stop();
        }
    }

    /** 射撃 AI の横移動方向 (±1。MOBごとに固定, 時々折り返す) */
    private static final Map<UUID, Integer> SHOOT_DIR = new HashMap<>();

    /** 射撃: 中距離を保ちつつ横にジグザグ移動 (発射はバニラAI). 直立不動で撃たれるのを防ぐ */
    private static void tickShooter(ServerLevel level, Mob m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) {
            m.getNavigation().stop();
            return;
        }
        keepDistance(m, target, 8, 16, 1.0, 1.0);
        double dx = m.getX() - target.getX();
        double dz = m.getZ() - target.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= 8 && d <= 16) {
            // 中距離圏: 目標の周囲を ±45° ずつ巻いて横にこぼれる (サイトが振れ続ける)
            int dir = SHOOT_DIR.computeIfAbsent(m.getUUID(), k -> m.getRandom().nextBoolean() ? 1 : -1);
            double sw = Math.toRadians(45) * dir;
            double ca = Math.cos(sw);
            double sa = Math.sin(sw);
            m.getNavigation().moveTo(
                    target.getX() + (dx * ca - dz * sa) / Math.max(1e-3, d) * d,
                    m.getY(),
                    target.getZ() + (dx * sa + dz * ca) / Math.max(1e-3, d) * d,
                    1.0);
            if (m.getRandom().nextFloat() < 0.1f) SHOOT_DIR.put(m.getUUID(), -dir);
        }
        m.setTarget(target);
        // 悪夢の射撃強化: 扇状3発を追加発射 (原作 shoot ai の eprojs 扇射に対応)
        var tags = m.getTags();
        if (hasNightmareTag(tags) && now >= NEXT_FAN.getOrDefault(m.getUUID(), 0)) {
            nightmareFanShot(level, m, target);
            NEXT_FAN.put(m.getUUID(), now + scaledInterval(target, 90, 40));
        }
        // 悪夢「攻城」: 半周期で遅延爆発撃ちを1発
        if (tags.contains(Enemies.NT_SIEGE) && now % 120 == 60) {
            level.sendParticles(ParticleTypes.FLAME,
                    m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
            DelayRunner.after(10, () -> {
                if (!m.isAlive() || m.isRemoved() || !target.isAlive()) return;
                WeaponArts.enemyPulse(level, target.position(), 2.0, 8.0, ParticleTypes.FLAME);
            });
        }
    }

    /** 悪夢化しているか (abyss_nt_* の特性タグの有無。狂乱の発動済みタグは除く) */
    private static boolean hasNightmareTag(Set<String> tags) {
        for (String t : tags) {
            if (t.startsWith("abyss_nt_") && !t.equals(NT_BERSERK_DONE)) return true;
        }
        return false;
    }

    /**
     * 悪夢の扇状3発: 標的方向を中心に ±0.25rad ずつ逸らしたスペクトル矢を放つ。
     * 矢の威力 = 攻撃×0.7 (AbstractArrow は速度×baseDamage でダメージが決まるため速度分を割る)。
     * 拾えない (Pickup.DISALLOWED)。
     */
    private static void nightmareFanShot(ServerLevel level, Mob m, ServerPlayer target) {
        Vec3 from = m.getEyePosition();
        Vec3 to = target.getEyePosition();
        double dx = to.x - from.x, dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1e-3) return;
        double baseAng = Math.atan2(dz, dx);
        var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        double dmg = (atk != null ? atk.getValue() * 0.7 : 8) / 1.6;
        for (int i = -1; i <= 1; i++) {
            double a = baseAng + i * 0.25;
            var arrow = new SpectralArrow(level, m,
                    new ItemStack(net.minecraft.world.item.Items.ARROW), ItemStack.EMPTY);
            arrow.setPos(from.x, from.y - 0.1, from.z);
            arrow.setBaseDamage(dmg);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(Math.cos(a) * dist, to.y - from.y, Math.sin(a) * dist, 1.6f, 0);
            level.addFreshEntity(arrow);
        }
        level.sendParticles(ParticleTypes.CRIT, from.x, from.y, from.z, 4, 0.2, 0.2, 0.2, 0.1);
    }

    /**
     * 悪夢特性の毎tick挙動 (精鋭の先の段階)。
     *  - NT_SIEGE: 3秒ごとに最寄りのプレイヤー位置へ予兆 → 範囲爆発
     *  - NT_BERSERK: HP半分以下で一度だけ攻撃・速度が大幅上昇 (告知はせず効果付与のみ)
     */
    private static void tickNightmare(ServerLevel level, Mob m, int now, Set<String> tags) {
        // NT_SIEGE: 予兆つきの範囲爆発 (味方プレイヤーのみ被ダメ)
        if (tags.contains(Enemies.NT_SIEGE) && now % 60 == 0) {
            Player p = level.getNearestPlayer(m.getX(), m.getY(), m.getZ(), 24, false);
            if (p != null && p.isAlive()) {
                level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 15, 2, 0.5, 2, 0.05);
                DelayRunner.after(20, () -> {
                    if (!m.isAlive() || m.isRemoved() || !p.isAlive()) return;
                    WeaponArts.enemyPulse(level, p.position(), 3.0, 8.0, ParticleTypes.FLAME);
                });
            }
        }
        // NT_BERSERK: HP50% 以下で一度だけ発動
        if (tags.contains(Enemies.NT_BERSERK) && !tags.contains(NT_BERSERK_DONE)
                && m.getHealth() / Math.max(1, m.getMaxHealth()) <= 0.5) {
            m.addTag(NT_BERSERK_DONE);
            m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100 * 20, 1, false, false));
            m.addEffect(new MobEffectInstance(MobEffects.SPEED, 100 * 20, 0, false, false));
        }
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
        DelayRunner.after(20, () -> {
            if (!m.isAlive() || m.isRemoved()) return;
            m.push(vx * 2.4, 0.15, vz * 2.4);
            level.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + 0.5, m.getZ(), 20, 1, 0.5, 1, 0.1);
            DelayRunner.after(8, () -> {
                if (!m.isAlive() || m.isRemoved()) return;
                var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
                double dmg = atk != null ? atk.getValue() * 1.5 : 10;
                WeaponArts.enemyPulse(level, m.position(), 3.5, dmg, ParticleTypes.EXPLOSION);
            });
        });
        NEXT_CHARGE.put(m.getUUID(), now + scaledInterval(target, 120, 40));
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
            NEXT_TELE.put(m.getUUID(), now + scaledInterval(target, 40, 15));
            return;
        }
        level.sendParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 24, 0.8, 1, 0.8, 0.2);
        double ang = m.getRandom().nextDouble() * Math.PI * 2;
        double nx = target.getX() + Math.cos(ang) * (3 + m.getRandom().nextDouble() * 2);
        double nz = target.getZ() + Math.sin(ang) * (3 + m.getRandom().nextDouble() * 2);
        m.teleportTo(nx, target.getY(), nz);
        level.sendParticles(ParticleTypes.PORTAL, nx, target.getY() + 1, nz, 24, 0.8, 1, 0.8, 0.2);
        NEXT_TELE.put(m.getUUID(), now + scaledInterval(target, 100, 35));
    }

    /** 周回: プレイヤーの周囲 (半径6m) を回る. 速度は原作通り速め */
    private static void tickOrbit(ServerLevel level, Mob m, int now) {
        ServerPlayer target = nearestPlayer(level, m);
        if (target == null) return;
        m.setTarget(target);
        double ang = ORBIT_ANG.getOrDefault(m.getUUID(), m.getRandom().nextDouble() * Math.PI * 2);
        ang += 0.35 * tempoOf(target);
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
            var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
            double dmg = atk != null ? atk.getValue() * 2 : 20;
            WeaponArts.enemyPulse(level, m.position(), 4, dmg, ParticleTypes.EXPLOSION);
            m.discard();  // 自爆: 実体を消す (ドロップなし)
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
        keepDistance(m, target, 5, 10, 1.1, 1.0);
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
        NEXT_SUMMON.put(m.getUUID(), now + scaledInterval(target, Enemies.NECRO_SUMMON_TICKS, 40));
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
        NEXT_SUMMON.put(m.getUUID(), now + scaledInterval(target, Enemies.GATE_SUMMON_TICKS, 40));
    }
}
