package com.abyssalloot;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * 原作 weapon-arts.ts のMC移植.
 * planWeaponArt の幾何学は原作準拠、実行はMCの範囲ダメージ+演出に置換.
 */
public final class WeaponArts {
    private WeaponArts() {}

    /** 奥義・罠・二次反応によるダメージ中は >0. 武器命中処理がこれを見て威力上書き・ゲージ蓄積を止める */
    private static int artHitDepth = 0;

    /** 奥義系の命中か (原作: 奥義自身・環境罠・二次反応はゲージを増やさない) */
    public static boolean inArtHit() {
        return artHitDepth > 0;
    }

    /**
     * 奥義/二次反応の直接ダメージ. 威力は呼び出し側の値をそのまま使い、
     * 武器命中の威力上書き・奥義ゲージ蓄積の対象から外す.
     */
    public static void artHurt(ServerLevel level, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float dmg) {
        artHitDepth++;
        try {
            target.hurtServer(level, source, dmg);
        } finally {
            artHitDepth--;
        }
    }

    static ServerLevel slevel(ServerPlayer player) {
        return (ServerLevel) player.level();
    }

    public static boolean execute(ServerPlayer player, GameData.WeaponStyle style, double attackDamage) {
        ServerLevel level = slevel(player);
        Vec3 origin = player.position();
        Vec3 look = player.getLookAngle();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin.x - 24, origin.y - 8, origin.z - 24, origin.x + 24, origin.y + 8, origin.z + 24),
                e -> e.isAlive() && !(e instanceof ServerPlayer));

        switch (style) {
            case SWORD -> {
                // 月牙斬: 前方3方向へ斬撃波 (貫通イメージで3連ヒット)
                for (int i = -1; i <= 1; i++) {
                    double ang = Math.atan2(look.z, look.x) + i * 0.18;
                    Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    slashCone(level, player, origin, dir, 14, 2.1 * attackDamage, ParticleTypes.SWEEP_ATTACK);
                }
                burst(player, "月牙斬！");
                return true;
            }
            case AXE -> {
                // 大地砕き: 大範囲爆発+強ノックバック
                aoe(level, player, origin, 9, 4.2 * attackDamage, true, ParticleTypes.EXPLOSION);
                burst(player, "大地砕き！");
                return true;
            }
            case SPEAR -> {
                // 竜殺し: 前方へ突進 (無敵付与) + 経路ダメージ
                Vec3 dest = origin.add(look.x * 10, 0, look.z * 10);
                player.teleportTo(dest.x, dest.y, dest.z);
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4, false, false));
                aoe(level, player, dest, 6, 4.8 * attackDamage, true, ParticleTypes.CRIT);
                burst(player, "竜殺し！");
                return true;
            }
            case STAFF -> {
                // 星辰大爆裂: 最寄り敵位置に隕石
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(8));
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1, at.z, 60, 2, 2, 2, 0.2);
                Vec3 target = at;
                CombatHandler.after(12, () ->
                        aoe(slevel(player), player, target, 8, 6.5 * attackDamage, true, ParticleTypes.EXPLOSION_EMITTER));
                burst(player, "星辰大爆裂！");
                return true;
            }
            case BOW -> {
                // 疾風連雨: 近距離13連撃を自動照準で小分けダメージ
                for (int i = 0; i < 13; i++) {
                    LivingEntity t = nearest(origin, targets);
                    if (t == null) break;
                    artHurt(level, t, level.damageSources().playerAttack(player), (float) (0.82 * attackDamage));
                    level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1, t.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
                    targets.remove(t);
                }
                burst(player, "疾風連雨！");
                return true;
            }
            case DAGGER -> {
                // 影襲: 最寄り敵の背後へ瞬移し単体強襲
                LivingEntity t = nearest(origin, targets);
                if (t == null) return false;
                Vec3 behind = t.position().subtract(t.getLookAngle().scale(2.2));
                player.teleportTo(behind.x, behind.y, behind.z);
                artHurt(level, t, level.damageSources().playerAttack(player), (float) (7.0 * attackDamage));
                level.sendParticles(ParticleTypes.PORTAL, behind.x, behind.y + 1, behind.z, 40, 1, 1, 1, 0.4);
                burst(player, "影襲！");
                return true;
            }
            case HAMMER -> {
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(5));
                aoe(level, player, at, 6, 8.0 * attackDamage, false, ParticleTypes.ELECTRIC_SPARK);
                if (t != null) {
                    t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3));
                    t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                }
                burst(player, "崩壊スマッシュ！");
                return true;
            }
            case KATANA -> {
                // 心無一刀: 周囲一閃
                aoe(level, player, origin, 12, 5.6 * attackDamage, false, ParticleTypes.SWEEP_ATTACK);
                burst(player, "心無一刀！");
                return true;
            }
            case SCYTHE -> {
                // 冥府の収穫: 周囲 + 低HP追加
                for (LivingEntity e : List.copyOf(targets)) {
                    if (e.distanceTo(player) > 10) continue;
                    double mult = (e.getHealth() / Math.max(1, e.getMaxHealth()) < 0.3) ? 3.8 * 1.8 : 3.8;
                    artHurt(level, e, level.damageSources().playerAttack(player), (float) (mult * attackDamage / 4.0));
                }
                level.sendParticles(ParticleTypes.SOUL, origin.x, origin.y + 1, origin.z, 50, 4, 2, 4, 0.2);
                burst(player, "冥府の収穫！");
                return true;
            }
            case GAUNTLET -> {
                // 六連震掌: 前方6連
                Vec3 dir = look;
                for (int i = 0; i < 6; i++) {
                    double mult = (i == 5) ? 2.7 : 0.65;
                    slashCone(level, player, origin.add(dir.scale(2 + i)), dir, 5, mult * attackDamage, ParticleTypes.CRIT);
                }
                burst(player, "六連震掌！");
                return true;
            }
            case CHAKRAM -> {
                // 輪廻乱舞: 全方位16枚イメージで周囲2回転ダメージ
                aoe(level, player, origin, 11, 1.35 * attackDamage * 3, false, ParticleTypes.END_ROD);
                burst(player, "輪廻乱舞！");
                return true;
            }
            case TOME -> {
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(8));
                Vec3 target = at;
                CombatHandler.after(15, () -> {
                    ServerLevel lv = slevel(player);
                    aoe(lv, player, target, 9, 5.5 * attackDamage, true, ParticleTypes.WITCH);
                    for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                            new AABB(target.x - 9, target.y - 4, target.z - 9, target.x + 9, target.y + 4, target.z + 9),
                            x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    }
                });
                burst(player, "禁書・虚空葬！");
                return true;
            }
        }
        return false;
    }

    /** 原作 skillName (Q技) のMC移植 */
    public static void executeSkill(ServerPlayer player, GameData.HeroClass cls, double attackDamage) {
        ServerLevel level = slevel(player);
        Vec3 origin = player.position();
        switch (cls) {
            case SAMURAI -> {
                aoe(level, player, origin, 12, 3.0 * attackDamage, true, ParticleTypes.SWEEP_ATTACK);
                announce(player, "絶空斬！");
            }
            case MAGE -> {
                for (int i = 0; i < 5; i++) {
                    Vec3 at = origin.add((player.getRandom().nextDouble() - 0.5) * 16, 0,
                            (player.getRandom().nextDouble() - 0.5) * 16);
                    aoe(level, player, at, 5, 2.2 * attackDamage, false, ParticleTypes.FLAME);
                }
                announce(player, "メテオフォール！");
            }
            case NINJA -> {
                aoe(level, player, origin, 8, 1.4 * attackDamage, false, ParticleTypes.PORTAL);
                aoe(level, player, origin, 8, 1.4 * attackDamage, false, ParticleTypes.CRIT);
                announce(player, "影分身乱舞！");
            }
            case PALADIN -> {
                aoe(level, player, origin, 10, 2.6 * attackDamage, false, ParticleTypes.END_ROD);
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                announce(player, "ジャッジメント！");
            }
            case GUNNER -> {
                for (int k = 0; k < 3; k++) {
                    aoe(level, player, origin, 14, 1.2 * attackDamage, false, ParticleTypes.CRIT);
                }
                announce(player, "バレットヘル！");
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1f, 1f);
    }

    /** 感圧罠の爆発 (敵味方共通・原作traps.ts準拠の簡易版) */
    public static void trapBlast(ServerLevel level, ServerPlayer attacker, Vec3 at) {
        pulse(level, attacker, at, 5, 3.0, true, ParticleTypes.EXPLOSION);
    }

    /** 指定点中心の範囲打撃 (罠・爆砕・回転刃・聖域の共通処理) */
    public static void pulse(ServerLevel level, ServerPlayer attacker, Vec3 at, double radius,
                             double mult, boolean knockback, ParticleOptions particle) {
        double dmg = RunManager.attackDamageOf(attacker) * mult;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at.x - radius, at.y - 5, at.z - radius, at.x + radius, at.y + 5, at.z + radius),
                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
            if (e.position().distanceTo(at) > radius + 1) continue;
            artHurt(level, e, level.damageSources().playerAttack(attacker), (float) dmg);
            if (knockback) {
                Vec3 kb = e.position().subtract(at).normalize().scale(2.2);
                e.push(kb.x, 0.5, kb.z);
            }
        }
        level.sendParticles(particle, at.x, at.y + 1, at.z, 30, radius / 2.4, 1.0, radius / 2.4, 0.12);
    }

    /** 敵側の範囲打撃 (層主・真ボスの弾幕用。被弾はmagic扱い) */
    public static void enemyPulse(ServerLevel level, Vec3 at, double radius, double dmg,
                                 ParticleOptions particle) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at.x - radius, at.y - 5, at.z - radius, at.x + radius, at.y + 5, at.z + radius),
                x -> x.isAlive() && x instanceof ServerPlayer)) {
            if (e.position().distanceTo(at) > radius + 1) continue;
            e.hurtServer(level, level.damageSources().magic(), (float) dmg);
        }
        level.sendParticles(particle, at.x, at.y + 1, at.z, 40, radius / 2.4, 1.4, radius / 2.4, 0.15);
        level.playSound(null, new net.minecraft.core.BlockPos((int) at.x, (int) at.y, (int) at.z),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    private static LivingEntity nearest(Vec3 origin, List<LivingEntity> targets) {
        return targets.stream()
                .filter(e -> e.isAlive())
                .min(Comparator.comparingDouble(e -> e.position().distanceTo(origin)))
                .orElse(null);
    }

    private static void slashCone(ServerLevel level, ServerPlayer attacker, Vec3 origin, Vec3 dir, double range, double dmg, ParticleOptions particle) {
        double baseAng = Math.atan2(dir.z, dir.x);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin.x - range, origin.y - 5, origin.z - range, origin.x + range, origin.y + 5, origin.z + range),
                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
            Vec3 d = e.position().subtract(origin);
            if (d.length() > range + 1) continue;
            double ang = Math.atan2(d.z, d.x);
            double diff = Math.abs(normAng(ang - baseAng));
            if (diff < 0.6) {
                artHurt(level, e, level.damageSources().playerAttack(attacker), (float) dmg);
                Vec3 kb = d.normalize().scale(1.2);
                e.push(kb.x, 0.25, kb.z);
            }
        }
        level.sendParticles(particle, origin.x + dir.x * 3, origin.y + 1.2, origin.z + dir.z * 3, 12, 1.4, 0.6, 1.4, 0.12);
    }

    private static void aoe(ServerLevel level, ServerPlayer attacker, Vec3 at, double radius, double dmg, boolean knockback, ParticleOptions particle) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at.x - radius, at.y - 5, at.z - radius, at.x + radius, at.y + 5, at.z + radius),
                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
            if (e.position().distanceTo(at) > radius + 1) continue;
            artHurt(level, e, level.damageSources().playerAttack(attacker), (float) dmg);
            if (knockback) {
                Vec3 kb = e.position().subtract(at).normalize().scale(2.2);
                e.push(kb.x, 0.5, kb.z);
            }
        }
        level.sendParticles(particle, at.x, at.y + 1, at.z, 40, radius / 2.4, 1.2, radius / 2.4, 0.15);
        level.playSound(null, new net.minecraft.core.BlockPos((int) at.x, (int) at.y, (int) at.z),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    private static double normAng(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    private static void burst(ServerPlayer player, String text) {
        announce(player, text);
        slevel(player).playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.8f);
    }

    private static void announce(ServerPlayer player, String text) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d◆ " + text), true);
    }
}
