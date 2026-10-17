package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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

    /** 遅延実行 (CombatHandler.after の安全版)。発動者が既に倒れていたら何もしない */
    private static void later(ServerPlayer player, int ticks, java.util.function.Consumer<ServerLevel> action) {
        CombatHandler.after(ticks, () -> {
            if (!player.isAlive()) return;
            action.accept(slevel(player));
        });
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
                burst(player, "月牙斬！", style);
                return true;
            }
            case AXE -> {
                // 大地砕き: 大範囲爆発+強ノックバック (原作 burst r270px ≒ 半径5)
                aoe(level, player, origin, 5, 4.2 * attackDamage, true, ParticleTypes.EXPLOSION);
                burst(player, "大地砕き！", style);
                return true;
            }
            case SPEAR -> {
                // 竜殺し: 前方へ突進 (無敵付与) + 経路3点の全員を強襲 (原作 dash)
                Vec3 dest = origin.add(look.x * 6, 0, look.z * 6);
                player.teleportTo(dest.x, dest.y, dest.z);
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4, false, false));
                java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
                for (double d = 2; d <= 6; d += 2) {
                    Vec3 at = origin.add(look.x * d, 0, look.z * d);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(at.x - 2.5, at.y - 4, at.z - 2.5, at.x + 2.5, at.y + 4, at.z + 2.5),
                            x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                        if (hit.add(e)) {
                            artHurt(level, e, level.damageSources().playerAttack(player),
                                    (float) (4.8 * attackDamage));
                            Vec3 kb = e.position().subtract(at).normalize().scale(1.6);
                            e.push(kb.x, 0.3, kb.z);
                        }
                    }
                    level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1, at.z, 12, 1, 0.6, 1, 0.1);
                }
                burst(player, "竜殺し！", style);
                return true;
            }
            case STAFF -> {
                // 星辰大爆裂: 最寄り敵位置に隕石
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(8));
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1, at.z, 60, 2, 2, 2, 0.2);
                Vec3 target = at;
                later(player, 12, lv ->
                        aoe(lv, player, target, 5, 6.5 * attackDamage, true, ParticleTypes.EXPLOSION_EMITTER));
                burst(player, "星辰大爆裂！", style);
                return true;
            }
            case BOW -> {
                // 疾風連雨: 13連矢を自動照準で連射 (発射ごとに最寄りを再取得。原作 0.075秒刻み)
                for (int i = 0; i < 13; i++) {
                    int delay = i * 3 / 2;
                    later(player, delay, lv -> {
                        LivingEntity t = nearest(player.position(), lv.getEntitiesOfClass(LivingEntity.class,
                                new AABB(player.getX() - 24, player.getY() - 8, player.getZ() - 24,
                                        player.getX() + 24, player.getY() + 8, player.getZ() + 24),
                                e -> e.isAlive() && !(e instanceof ServerPlayer)));
                        if (t == null) return;
                        artHurt(lv, t, lv.damageSources().playerAttack(player),
                                (float) (0.82 * attackDamage));
                        lv.sendParticles(ParticleTypes.CRIT,
                                t.getX(), t.getY() + 1, t.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
                    });
                }
                burst(player, "疾風連雨！", style);
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
                burst(player, "影襲！", style);
                return true;
            }
            case HAMMER -> {
                // 崩壊スマッシュ: 単体へ極大打撃。感電とスタンを周囲へ伝播 (原作通り)
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(5));
                aoe(level, player, at, 3.5, 8.0 * attackDamage, false, ParticleTypes.ELECTRIC_SPARK);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(at.x - 3.5, at.y - 4, at.z - 3.5, at.x + 3.5, at.y + 4, at.z + 3.5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3));
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            e.getX(), e.getY() + 1, e.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
                }
                if (t != null) t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
                burst(player, "崩壊スマッシュ！", style);
                return true;
            }
            case KATANA -> {
                // 心無一刀: 周囲一閃 (原作 cut r370px ≒ 半径7)。敵弾も斬り払う
                aoe(level, player, origin, 7, 5.6 * attackDamage, false, ParticleTypes.SWEEP_ATTACK);
                int cleared = sweepProjectiles(level, origin, 7);
                if (cleared > 0) {
                    level.sendParticles(ParticleTypes.END_ROD,
                            origin.x, origin.y + 1.5, origin.z, cleared * 4, 3, 1.5, 3, 0.15);
                }
                burst(player, "心無一刀！", style);
                return true;
            }
            case SCYTHE -> {
                // 冥府の収穫: 周囲を刈り取り (原作 cut r290px ≒ 半径5.5)。低HPに追加打
                for (LivingEntity e : List.copyOf(targets)) {
                    if (e.distanceTo(player) > 5.5) continue;
                    double mult = (e.getHealth() / Math.max(1, e.getMaxHealth()) < 0.3) ? 3.8 * 1.8 : 3.8;
                    artHurt(level, e, level.damageSources().playerAttack(player), (float) (mult * attackDamage));
                }
                level.sendParticles(ParticleTypes.SOUL, origin.x, origin.y + 1, origin.z, 50, 4, 2, 4, 0.2);
                burst(player, "冥府の収穫！", style);
                return true;
            }
            case GAUNTLET -> {
                // 六連震掌: 前方6連 (原作 fist r115/170px ≒ 2/3、0.1秒刻み)。最終撃は吹き飛ばし
                Vec3 dir = look;
                for (int i = 0; i < 6; i++) {
                    double mult = (i == 5) ? 2.7 : 0.65;
                    Vec3 at = origin.add(dir.scale(2 + i));
                    boolean last = (i == 5);
                    later(player, i * 2, lv -> {
                        double range = (last ? 3.0 : 3.5) * StatCalc.areaMult(player);
                        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                                new AABB(at.x - range, at.y - 5, at.z - range,
                                        at.x + range, at.y + 5, at.z + range),
                                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                            Vec3 d = e.position().subtract(at);
                            if (d.length() > range + 1) continue;
                            double ang = Math.atan2(d.z, d.x) - Math.atan2(dir.z, dir.x);
                            while (ang > Math.PI) ang -= Math.PI * 2;
                            while (ang < -Math.PI) ang += Math.PI * 2;
                            if (Math.abs(ang) > 0.7) continue;
                            artHurt(lv, e, lv.damageSources().playerAttack(player),
                                    (float) (mult * attackDamage));
                            Vec3 kb = d.normalize().scale(last ? 3.0 : 1.0);
                            e.push(kb.x, last ? 0.6 : 0.2, kb.z);
                        }
                        lv.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1.2, at.z, 10, 1, 0.5, 1, 0.1);
                    });
                }
                burst(player, "六連震掌！", style);
                return true;
            }
            case CHAKRAM -> {
                // 輪廻乱舞: 16枚の輪刃を2周に分けて放つ (原作 16 wave × 1.35)
                aoe(level, player, origin, 7, 1.35 * attackDamage * 2, false, ParticleTypes.END_ROD);
                later(player, 10, lv ->
                        aoe(lv, player, origin, 7, 1.35 * attackDamage * 2, false, ParticleTypes.END_ROD));
                burst(player, "輪廻乱舞！", style);
                return true;
            }
            case RUNE_BLADE -> {
                // ルーン連斬: 5方向へ斬撃波+氷結付与
                for (int i = -2; i <= 2; i++) {
                    double ang = Math.atan2(look.z, look.x) + i * 0.15;
                    Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    slashCone(level, player, origin, dir, 12, 1.5 * attackDamage, ParticleTypes.SWEEP_ATTACK);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin.x - 12, origin.y - 5, origin.z - 12,
                                origin.x + 12, origin.y + 5, origin.z + 12),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                }
                burst(player, "ルーン連斬！", style);
                return true;
            }
            case BLOOD_AXE -> {
                // 血潮爆砕: 大爆発+与ダメの一部を吸収
                final double[] drained = {0};
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin.x - 5, origin.y - 5, origin.z - 5,
                                origin.x + 5, origin.y + 5, origin.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    float before = e.getHealth();
                    artHurt(level, e, level.damageSources().playerAttack(player),
                            (float) (3.6 * attackDamage));
                    drained[0] += Math.max(0, before - e.getHealth());
                    Vec3 kb = e.position().subtract(origin).normalize().scale(2.4);
                    e.push(kb.x, 0.5, kb.z);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 1, origin.z,
                        40, 2.5, 1.2, 2.5, 0.12);
                if (drained[0] > 0) {
                    player.heal((float) Math.min(player.getMaxHealth() - player.getHealth(), drained[0] * 0.25));
                    level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(),
                            8, 0.5, 0.5, 0.5, 0.1);
                }
                burst(player, "血潮爆砕！", style);
                return true;
            }
            case STORM_SPEAR -> {
                // 螺旋突貫: 突進+軌跡に雷撃
                Vec3 dest = origin.add(look.x * 6, 0, look.z * 6);
                player.teleportTo(dest.x, dest.y, dest.z);
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4, false, false));
                java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
                for (double d = 2; d <= 6; d += 2) {
                    Vec3 at = origin.add(look.x * d, 0, look.z * d);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(at.x - 2.5, at.y - 4, at.z - 2.5, at.x + 2.5, at.y + 4, at.z + 2.5),
                            x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                        if (hit.add(e)) {
                            artHurt(level, e, level.damageSources().playerAttack(player),
                                    (float) (3.2 * attackDamage));
                            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                    e.getX(), e.getY() + 1, e.getZ(), 10, 0.5, 0.8, 0.5, 0.15);
                        }
                    }
                }
                burst(player, "螺旋突貫！", style);
                return true;
            }
            case VOID_STAFF -> {
                // 虚空落とし: 最寄り敵位置に隕石3連 (0.4秒刻み)
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(8));
                Vec3 target = at;
                for (int i = 0; i < 3; i++) {
                    later(player, 8 + i * 8, lv ->
                            aoe(lv, player, target, 4, 2.8 * attackDamage, true, ParticleTypes.PORTAL));
                }
                level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 3, at.z, 30, 1.5, 2, 1.5, 0.15);
                burst(player, "虚空落とし！", style);
                return true;
            }
            case MOON_BOW -> {
                // 月雨: 9矢を自動照準・貫通で連射
                for (int i = 0; i < 9; i++) {
                    later(player, i * 2, lv -> {
                        LivingEntity tgt = nearest(player.position(), lv.getEntitiesOfClass(LivingEntity.class,
                                new AABB(player.getX() - 24, player.getY() - 8, player.getZ() - 24,
                                        player.getX() + 24, player.getY() + 8, player.getZ() + 24),
                                e -> e.isAlive() && !(e instanceof ServerPlayer)));
                        if (tgt == null) return;
                        artHurt(lv, tgt, lv.damageSources().playerAttack(player),
                                (float) (1.1 * attackDamage));
                        lv.sendParticles(ParticleTypes.END_ROD,
                                tgt.getX(), tgt.getY() + 1, tgt.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
                    });
                }
                burst(player, "月雨！", style);
                return true;
            }
            case FANG_DAGGER -> {
                // 牙影連襲: 3回瞬移し背後から噛む
                LivingEntity t = nearest(origin, targets);
                if (t == null) return false;
                for (int i = 0; i < 3; i++) {
                    LivingEntity tgt = t;
                    later(player, i * 4, lv -> {
                        if (!tgt.isAlive()) return;
                        Vec3 behind = tgt.position().subtract(tgt.getLookAngle().scale(2.0));
                        player.teleportTo(behind.x, behind.y, behind.z);
                        artHurt(lv, tgt, lv.damageSources().playerAttack(player),
                                (float) (2.6 * attackDamage));
                        lv.sendParticles(ParticleTypes.CRIT, behind.x, behind.y + 1, behind.z,
                                12, 0.6, 0.6, 0.6, 0.15);
                    });
                }
                burst(player, "牙影連襲！", style);
                return true;
            }
            case QUAKE_HAMMER -> {
                // 地鳴り: 広範囲激震+鈍足
                aoe(level, player, origin, 8, 3.4 * attackDamage, true, ParticleTypes.EXPLOSION);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin.x - 8, origin.y - 4, origin.z - 8,
                                origin.x + 8, origin.y + 4, origin.z + 8),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                }
                burst(player, "地鳴り！", style);
                return true;
            }
            case FROST_KATANA -> {
                // 氷月一閃: 周囲一閃+全体凍結・敵弾消去
                aoe(level, player, origin, 6, 4.2 * attackDamage, false, ParticleTypes.SNOWFLAKE);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin.x - 6, origin.y - 4, origin.z - 6,
                                origin.x + 6, origin.y + 4, origin.z + 6),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4));
                    e.setTicksFrozen(Math.min(200, e.getTicksFrozen() + 120));
                }
                sweepProjectiles(level, origin, 6);
                burst(player, "氷月一閃！", style);
                return true;
            }
            case SOUL_SCYTHE -> {
                // 魂収穫: 刈り取り+撃破ごとに魂ゲージ
                for (LivingEntity e : List.copyOf(targets)) {
                    if (e.distanceTo(player) > 6) continue;
                    float before = e.getHealth();
                    artHurt(level, e, level.damageSources().playerAttack(player),
                            (float) (3.2 * attackDamage));
                    if (!e.isAlive()) {
                        RunManager.addGauge(player, 12);
                    } else if (before - e.getHealth() > 0) {
                        RunManager.addGauge(player, 3);
                    }
                }
                level.sendParticles(ParticleTypes.SOUL, origin.x, origin.y + 1, origin.z, 50, 4, 2, 4, 0.2);
                burst(player, "魂収穫！", style);
                return true;
            }
            case TITAN_GAUNTLET -> {
                // 岩砕十連: 前方10連・最後に大爆発
                Vec3 dir = look;
                for (int i = 0; i < 10; i++) {
                    boolean last = (i == 9);
                    Vec3 at = origin.add(dir.scale(2 + i * 0.8));
                    later(player, i * 2, lv ->
                            aoe(lv, player, at, last ? 4 : 2.5, (last ? 3.5 : 0.55) * attackDamage,
                                    last, ParticleTypes.CRIT));
                }
                burst(player, "岩砕十連！", style);
                return true;
            }
            case ECLIPSE_CHAKRAM -> {
                // 蝕輪廻: 24枚の輪刃を3周に分けて放つ
                for (int w = 0; w < 3; w++) {
                    later(player, w * 8, lv ->
                            aoe(lv, player, origin, 8, 1.2 * attackDamage * 2, false, ParticleTypes.PORTAL));
                }
                burst(player, "蝕輪廻！", style);
                return true;
            }
            case CHAOS_TOME -> {
                // 混沌特異点: 爆縮+無作為の弱体 (鈍足/弱化/発光のいずれか)
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(8));
                Vec3 target = at;
                later(player, 12, lv -> {
                    aoe(lv, player, target, 6, 5.0 * attackDamage, true, ParticleTypes.PORTAL);
                    var effs = new MobEffectInstance[]{
                            new MobEffectInstance(MobEffects.SLOWNESS, 120, 2),
                            new MobEffectInstance(MobEffects.WEAKNESS, 120, 1),
                            new MobEffectInstance(MobEffects.GLOWING, 200, 0),
                    };
                    for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                            new AABB(target.x - 6, target.y - 4, target.z - 6,
                                    target.x + 6, target.y + 4, target.z + 6),
                            x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                        e.addEffect(effs[player.getRandom().nextInt(effs.length)]);
                    }
                });
                level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 30, 2, 1.5, 2, 0.15);
                burst(player, "混沌特異点！", style);
                return true;
            }
            case TOME -> {
                LivingEntity t = nearest(origin, targets);
                Vec3 at = t != null ? t.position() : origin.add(look.scale(8));
                Vec3 target = at;
                later(player, 15, lv -> {
                    aoe(lv, player, target, 5, 5.5 * attackDamage, true, ParticleTypes.WITCH);
                    for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                            new AABB(target.x - 5, target.y - 4, target.z - 5,
                                    target.x + 5, target.y + 4, target.z + 5),
                            x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    }
                });
                burst(player, "禁書・虚空葬！", style);
                return true;
            }
            case ROD -> {
                // 螺旋嵐: 周囲を嵐で引き寄せ刻む
                for (LivingEntity e : List.copyOf(targets)) {
                    Vec3 d = origin.subtract(e.position());
                    double dist = d.length();
                    if (dist > 9 || dist < 0.5) continue;
                    Vec3 pull = d.normalize().scale(1.8);
                    e.push(pull.x, 0.2, pull.z);
                    artHurt(level, e, level.damageSources().playerAttack(player),
                            (float) (2.2 * attackDamage));
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, origin.x, origin.y + 1, origin.z,
                        40, 4, 2, 4, 0.2);
                level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y + 0.5, origin.z,
                        25, 3.5, 1, 3.5, 0.15);
                burst(player, "螺旋嵐！", style);
                return true;
            }
            case WAND -> {
                // 魔弾連星: 10連の自動魔弾
                for (int i = 0; i < 10; i++) {
                    later(player, i * 2, lv -> CombatHandler.castWandBolt(player, 18, 1.1, 1));
                }
                burst(player, "魔弾連星！", style);
                return true;
            }
            case GREATSWORD -> {
                // 天地断: 広域を薙ぎ払い衝撃波
                Vec3 dir = look;
                slashCone(level, player, origin, dir, 9, 4.5 * attackDamage, ParticleTypes.SWEEP_ATTACK);
                later(player, 6, lv ->
                        aoe(lv, player, origin.add(dir.scale(6)), 4, 2.5 * attackDamage, true,
                                ParticleTypes.EXPLOSION));
                burst(player, "天地断！", style);
                return true;
            }
            case TWIN_DAGGER -> {
                // 乱舞連牙: 周囲へ8連の牙
                for (int i = 0; i < 8; i++) {
                    double ang = Math.atan2(look.z, look.x) + i * Math.PI / 4;
                    Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    later(player, i, lv ->
                            slashCone(lv, player, origin, dir, 6, 1.2 * attackDamage, ParticleTypes.CRIT));
                }
                burst(player, "乱舞連牙！", style);
                return true;
            }
            case WAR_HORN -> {
                // 大雄叫び: 吹き飛ばし+雄叫び強化
                aoe(level, player, origin, 8, 2.0 * attackDamage, true, ParticleTypes.SONIC_BOOM);
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin.x - 8, origin.y - 4, origin.z - 8,
                                origin.x + 8, origin.y + 4, origin.z + 8),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                }
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS, 1f, 0.5f);
                burst(player, "大雄叫び！", style);
                return true;
            }
            case WIND_BLADE -> {
                // 千風乱舞: 3波の風輪が拡大する同心円 (半径4→9)
                for (int w = 0; w < 3; w++) {
                    final int wave = w;
                    later(player, w * 5, lv ->
                            aoe(lv, player, origin, 4 + wave * 2.5, (1.4 + wave * 0.35) * attackDamage, wave == 2,
                                    ParticleTypes.SWEEP_ATTACK));
                }
                later(player, 16, lv -> aoe(lv, player, origin, 6, 2.2 * attackDamage, true, ParticleTypes.CLOUD));
                burst(player, "千風乱舞！", style);
                return true;
            }
            case EMBER_FLAIL -> {
                // 炎獄旋回: 前方を左右7連の炎弧で薙ぎ、最後に爆気
                for (int i = 0; i < 7; i++) {
                    double ang = Math.atan2(look.z, look.x) + (i - 3) * 0.42;
                    Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    final int k = i;
                    later(player, k * 2, lv ->
                            slashCone(lv, player, origin, dir, 6.5, 1.35 * attackDamage, ParticleTypes.FLAME));
                }
                later(player, 15, lv -> aoe(lv, player, origin.add(look.scale(4)), 4,
                        3.0 * attackDamage, true, ParticleTypes.EXPLOSION));
                burst(player, "炎獄旋回！", style);
                return true;
            }
            case ASTRAL_LANCE -> {
                // 鋼星乱嵐: 近傍に7本の星槍を乱れ打ち、極大の一撃で締める
                for (int i = 0; i < 7; i++) {
                    Vec3 at = origin.add((player.getRandom().nextDouble() - 0.5) * 14, 0,
                            (player.getRandom().nextDouble() - 0.5) * 14);
                    final Vec3 target = at;
                    later(player, 3 + i * 3, lv ->
                            aoe(lv, player, target, 2.2, 1.7 * attackDamage, true, ParticleTypes.END_ROD));
                }
                later(player, 24, lv -> aoe(lv, player, origin, 5.5, 4.0 * attackDamage, true,
                        ParticleTypes.WITCH));
                burst(player, "鋼星乱嵐！", style);
                return true;
            }
            case TIDE_TRIDENT -> {
                // 静潮・大渦: 周囲を引き寄せ、3重の潮波で押し流す+鈍足
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin.x - 12, origin.y - 5, origin.z - 12,
                                origin.x + 12, origin.y + 5, origin.z + 12),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    Vec3 pull = origin.subtract(e.position()).normalize().scale(2.4);
                    e.push(pull.x, 0.25, pull.z);
                }
                for (int w = 0; w < 3; w++) {
                    final int wave = w;
                    later(player, 4 + w * 5, lv -> {
                        double r = 5 + wave * 2;
                        aoe(lv, player, origin, r, (1.5 + wave * 0.45) * attackDamage, true, ParticleTypes.SPLASH);
                        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                                new AABB(origin.x - r, origin.y - 5, origin.z - r,
                                        origin.x + r, origin.y + 5, origin.z + r),
                                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
                        }
                    });
                }
                burst(player, "静潮・大渦！", style);
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
            case BERSERKER -> {
                // 血爆: 自身の現在HP20%を捧げて大爆発
                float cost = player.getHealth() * 0.2f;
                if (player.getHealth() > cost + 1) {
                    player.hurtServer(level, level.damageSources().magic(), cost);
                }
                aoe(level, player, origin, 8, 5.0 * attackDamage, true, ParticleTypes.EXPLOSION);
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
                announce(player, "血爆！");
            }
            case MONK -> {
                // 禅定結界: 結界内の敵を鎮め自身を癒す
                aoe(level, player, origin, 9, 1.2 * attackDamage, false, ParticleTypes.END_ROD);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(origin.x - 9, origin.y - 4, origin.z - 9,
                                origin.x + 9, origin.y + 4, origin.z + 9),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                }
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0));
                announce(player, "禅定結界！");
            }
            case ARCHER -> {
                // 天矢雨: 5重の矢の輪が広がる
                for (int k = 0; k < 5; k++) {
                    aoe(level, player, origin, 6 + k * 2, (1.5 - k * 0.1) * attackDamage, false, ParticleTypes.CRIT);
                }
                announce(player, "天矢雨！");
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
        radius *= StatCalc.areaMult(attacker);
        double dmg = StatCalc.attackDamageOf(attacker) * mult;
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
        level.playSound(null, new BlockPos((int) at.x, (int) at.y, (int) at.z),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    private static LivingEntity nearest(Vec3 origin, List<LivingEntity> targets) {
        return targets.stream()
                .filter(e -> e.isAlive())
                .min(Comparator.comparingDouble(e -> e.position().distanceTo(origin)))
                .orElse(null);
    }

    private static void slashCone(ServerLevel level, ServerPlayer attacker, Vec3 origin, Vec3 dir, double range, double dmg, ParticleOptions particle) {
        range *= StatCalc.areaMult(attacker);
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
        radius *= StatCalc.areaMult(attacker);
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
        level.playSound(null, new BlockPos((int) at.x, (int) at.y, (int) at.z),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    private static double normAng(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /** 周囲の (味方弾以外の) 射投影を斬り払う。消した数を返す (斬り払い・一閃系共通) */
    private static int sweepProjectiles(ServerLevel level, Vec3 at, double radius) {
        int cleared = 0;
        for (var p : level.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class,
                new AABB(at.x - radius, at.y - 4, at.z - radius,
                        at.x + radius, at.y + 4, at.z + radius),
                x -> !x.isRemoved())) {
            if (p.getOwner() instanceof ServerPlayer) continue;
            p.discard();
            cleared++;
        }
        return cleared;
    }

    /** 奥義発動の告知+効果音+武器種色の粉パーティクル */
    private static void burst(ServerPlayer player, String text, GameData.WeaponStyle style) {
        announce(player, text);
        slevel(player).playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.8f);
        if (style != null) {
            slevel(player).sendParticles(new DustParticleOptions(style.color, 1.2f),
                    player.getX(), player.getY() + 1.2, player.getZ(), 24, 1.2, 1.0, 1.2, 0.08);
        }
    }

    private static void announce(ServerPlayer player, String text) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d◆ " + text), true);
    }
}
