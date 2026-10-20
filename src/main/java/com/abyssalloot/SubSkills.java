package com.abyssalloot;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特殊スキル (Cキー / ActPacket "sub"): 囮・影分身・引力地雷など汎用の切り札8種。
 * 装備は {@link ModAttachments#SUB_SKILL} に1つだけ保持し、CTは SUB_CD で管理する
 * (切り替えはハブ / /abyss sub の選択画面)。地雷と重力井戸は設置型のため
 * {@link #tickWorld(ServerPlayer)} で進行させる。
 */
public final class SubSkills {
    private SubSkills() {}

    public record SubSkill(String id, String name, String desc, float cd) {}

    public static final List<SubSkill> SUB_SKILLS = List.of(
            new SubSkill("decoy", "囮", "囮を設置し12m内の敵の注意を6秒間引きつける", 15f),
            new SubSkill("clone", "影分身", "3体の分身が前方へ走り範囲を斬る (3連)", 18f),
            new SubSkill("mine", "引力地雷", "地雷を設置。敵が近づくと爆発して引き寄せる", 12f),
            new SubSkill("timecrush", "時締め", "周囲の敵を強く足止めし、8秒後に小爆発", 18f),
            new SubSkill("thornshield", "棘の盾", "6秒間 被ダメ-30%と反射を得る", 20f),
            new SubSkill("blinkswap", "錯影", "最寄りの敵と位置を交換して足止め", 14f),
            new SubSkill("soulfeed", "魂の供物", "HP20%を捧げて魂ゲージ+35", 10f),
            new SubSkill("gravitywell", "重力井戸", "照準地点に4秒間の吸引フィールドを作る", 22f),
            new SubSkill("bloodrush", "血の覚醒", "HP10%を捧げて8秒間 移動・攻撃速度が大幅に上がる", 16f),
            new SubSkill("soulcannon", "魂砲", "魂ゲージ30を消費し、最寄り敵に強力な一撃 (攻撃力×7)", 20f),
            new SubSkill("skyfall", "天墜撃", "前方へ跳躍し、着地地点で周囲を爆砕する", 22f));

    public static SubSkill byId(String id) {
        for (SubSkill s : SUB_SKILLS) {
            if (s.id().equals(id)) return s;
        }
        return SUB_SKILLS.get(0);
    }

    public static String idOf(ServerPlayer player) {
        String id = player.getData(ModAttachments.SUB_SKILL.get());
        return id == null || id.isBlank() ? "decoy" : id;
    }

    /** 現在装備中の特殊スキルを実行 (CT管理は SkillHandler) */
    public static boolean execute(ServerPlayer player, String id) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        return switch (id) {
            case "clone" -> cloneStrike(player, level);
            case "mine" -> placeMine(player, level);
            case "timecrush" -> timeCrush(player, level);
            case "blinkswap" -> blinkSwap(player, level);
            case "soulfeed" -> soulFeed(player, level);
            case "gravitywell" -> gravityWell(player, level);
            case "thornshield" -> thornShield(player);
            case "bloodrush" -> bloodRush(player, level);
            case "soulcannon" -> soulCannon(player, level);
            case "skyfall" -> skyFall(player, level);
            default -> placeDecoy(player, level);
        };
    }

    /** 囮: 武器スタンド (透明・無敵・マーカー) を設置。EnemyAI.tick が敵のターゲットをここへ向ける */
    private static boolean placeDecoy(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        Vec3 at = player.position().add(look.x * 3, 0, look.z * 3);
        var stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.EVENT);
        if (stand == null) return false;
        stand.setPos(at.x, player.getY(), at.z);
        stand.setYRot(player.getYRot());
        stand.setCustomName(Component.literal("§d〔囮〕"));
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.addTag(DECOY_TAG);
        level.addFreshEntity(stand);
        level.sendParticles(ParticleTypes.PORTAL, at.x, player.getY() + 1, at.z, 30, 0.6, 1, 0.6, 0.2);
        level.playSound(null, stand.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.6f, 1.4f);
        DelayRunner.after(6 * 20, () -> {
            if (stand.isAlive()) {
                level.sendParticles(ParticleTypes.SMOKE, stand.getX(), stand.getY() + 1, stand.getZ(),
                        12, 0.4, 0.8, 0.4, 0.05);
                stand.discard();
            }
        });
        return true;
    }

    /** 影分身: 3連の範囲斬撃 (前後に走る演出つき) */
    private static boolean cloneStrike(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            DelayRunner.after(k * 3, () -> {
                if (!player.isAlive()) return;
                Vec3 at = player.position().add(look.scale(2 + k * 2));
                WeaponArts.pulse(level, player, at, 2.6, 0.6, k == 2, ParticleTypes.SWEEP_ATTACK);
                level.sendParticles(new DustParticleOptions(0x9fd8ff, 1.2f),
                        at.x, player.getY() + 1, at.z, 14, 0.8, 0.8, 0.8, 0.1);
            });
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.8f, 1.3f);
        return true;
    }

    /** 引力地雷: 設置した位置に敵が入ると爆発+引き寄せ (tickWorld で進行) */
    private static final Map<UUID, Vec3> MINES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MINE_UNTIL = new ConcurrentHashMap<>();

    private static boolean placeMine(ServerPlayer player, ServerLevel level) {
        MINES.put(player.getUUID(), player.position().add(0, 0.2, 0));
        MINE_UNTIL.put(player.getUUID(), (long) 15 * 20);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(),
                12, 0.4, 0.2, 0.4, 0.05);
        return true;
    }

    /** 時締め: 周囲の敵を強スローで足止めし、8秒後に小爆発 */
    private static boolean timeCrush(ServerPlayer player, ServerLevel level) {
        var at = player.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at.x - 10, at.y - 5, at.z - 10,
                        at.x + 10, at.y + 5, at.z + 10),
                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 1, false, false));
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 1, at.z, 40, 6, 1.5, 6, 0.02);
        DelayRunner.after(8 * 20, () -> {
            if (player.isAlive()) {
                WeaponArts.pulse(level, player, player.position(), 4.0, 1.5, false, ParticleTypes.WITCH);
            }
        });
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }

    /** 棘の盾: 被ダメ軽減+反射 (WeaponHitHandler が SUB_SHIELD を参照)。付与のみ */
    private static boolean thornShield(ServerPlayer player) {
        player.setData(ModAttachments.SUB_SHIELD.get(), 6f);
        player.displayClientMessage(Component.literal(
                "§e棘の盾展開 (6秒) — 被ダメ-30%・反射"), true);
        return true;
    }

    /** 錯影: 最寄り敵と位置交換 */
    private static boolean blinkSwap(ServerPlayer player, ServerLevel level) {
        Vec3 origin = player.position();
        LivingEntity t = nearest(origin, level, 16);
        if (t == null) {
            player.displayClientMessage(Component.literal("§c錯影: 敵がいない"), true);
            return false;
        }
        Vec3 tp = t.position();
        player.teleportTo(tp.x, tp.y, tp.z);
        t.teleportTo(origin.x, origin.y, origin.z);
        t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 4, false, false));
        player.setData(ModAttachments.IFRAME.get(), 0.3f);
        level.sendParticles(ParticleTypes.PORTAL, tp.x, tp.y + 1, tp.z, 20, 0.6, 1, 0.6, 0.2);
        level.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1, origin.z, 20, 0.6, 1, 0.6, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8f, 1.1f);
        return true;
    }

    /** 魂の供物: HP20%を魂ゲージへ */
    private static boolean soulFeed(ServerPlayer player, ServerLevel level) {
        float cost = player.getMaxHealth() * 0.2f;
        if (player.getHealth() <= cost + 1) {
            player.displayClientMessage(Component.literal("§c供物には命が足りない"), true);
            return false;
        }
        player.hurtServer(level, level.damageSources().magic(), cost);
        RunManager.addGauge(player, 35);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(),
                20, 0.5, 1, 0.5, 0.2);
        return true;
    }

    /** 重力井戸: 照準地点に4秒間の吸引フィールド (tickWorld で進行) */
    private static final Map<UUID, Vec3> WELLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WELL_UNTIL = new ConcurrentHashMap<>();

    private static boolean gravityWell(ServerPlayer player, ServerLevel level) {
        Vec3 at = player.position().add(player.getLookAngle().scale(8));
        WELLS.put(player.getUUID(), at);
        WELL_UNTIL.put(player.getUUID(), (long) 4 * 20);
        level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT,
                SoundSource.PLAYERS, 0.8f, 0.5f);
        return true;
    }

    /** 血の覚醒: HP10%を捧げて移動・攻撃速度が大幅に上がる (8秒)。魂の供物の戦闘版 */
    private static boolean bloodRush(ServerPlayer player, ServerLevel level) {
        float cost = player.getMaxHealth() * 0.1f;
        if (player.getHealth() <= cost + 1) {
            player.displayClientMessage(Component.literal("§c覚醒には命が足りない"), true);
            return false;
        }
        player.hurtServer(level, level.damageSources().magic(), cost);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 1, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, 160, 1, false, false));
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                player.getX(), player.getY() + 1, player.getZ(), 16, 0.5, 0.8, 0.5, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 0.5f, 1.6f);
        player.displayClientMessage(Component.literal(
                "§c血の覚醒！ 移動・攻撃速度上昇 (8秒)"), true);
        return true;
    }

    /** 魂砲: 魂ゲージ30を消費し、最寄り敵に強力な一撃 (魂の供物の逆換算) */
    private static boolean soulCannon(ServerPlayer player, ServerLevel level) {
        float soul = player.getData(ModAttachments.SOUL.get());
        if (soul < 30f) {
            player.displayClientMessage(Component.literal(
                    "§c魂ゲージが足りない (30必要)"), true);
            return false;
        }
        LivingEntity t = nearest(player.position(), level, 16);
        if (t == null) {
            player.displayClientMessage(Component.literal("§c魂砲: 敵がいない"), true);
            return false;
        }
        // addGauge は加算専用のため直接減算 (悪夢「魂蝕」と同じ扱い)
        player.setData(ModAttachments.SOUL.get(), Math.max(0, soul - 30f));
        Vec3 from = player.position().add(0, 1.5, 0);
        Vec3 to = t.position().add(0, t.getBbHeight() / 2, 0);
        int steps = Math.max(1, (int) (from.distanceTo(to) * 2));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, (double) i / steps);
            level.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        WeaponArts.artHurt(level, t, level.damageSources().playerAttack(player),
                (float) (7.0 * StatCalc.attackDamageOf(player)));
        level.sendParticles(ParticleTypes.SOUL, to.x, to.y, to.z, 24, 0.8, 0.8, 0.8, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8f, 1.4f);
        return true;
    }

    /** 天墜撃: 前方へ跳躍し、着地地点で周囲を爆砕 (緩落下つきで落下ダメージは出ない) */
    private static boolean skyFall(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle();
        player.push(look.x * 1.3, 0.55, look.z * 1.3);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY(), player.getZ(), 12, 0.4, 0.2, 0.4, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7f, 0.8f);
        DelayRunner.after(10, () -> {
            if (!player.isAlive()) return;
            Vec3 at = player.position();
            WeaponArts.pulse(level, player, at, 4.5, 3.0, true, ParticleTypes.EXPLOSION);
            level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND,
                    SoundSource.PLAYERS, 0.9f, 1.0f);
        });
        return true;
    }

    /** 地雷と重力井戸の進行 (CombatHandler.onServerTick から 0.5秒ごとに呼ぶ) */
    public static void tickWorld(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        tickGravityMine(player, level, now);
        tickGravityWell(player, level, now);
    }

    /** 引力地雷: 敵が3m内に入ったら爆発+引き寄せ。15秒で失効 */
    private static void tickGravityMine(ServerPlayer player, ServerLevel level, long now) {
        Vec3 mine = MINES.get(player.getUUID());
        if (mine == null) return;
        long until = MINE_UNTIL.getOrDefault(player.getUUID(), 0L);
        var victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(mine.x - 3, mine.y - 2, mine.z - 3,
                        mine.x + 3, mine.y + 2, mine.z + 3),
                x -> x.isAlive() && !(x instanceof ServerPlayer));
        if (!victims.isEmpty() || now >= until) {
            WeaponArts.pulse(level, player, mine, 3.0, 4.0, false, ParticleTypes.EXPLOSION);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(mine.x - 8, mine.y - 4, mine.z - 8,
                            mine.x + 8, mine.y + 4, mine.z + 8),
                    x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                Vec3 pull = mine.subtract(e.position()).normalize().scale(1.4);
                e.push(pull.x, 0.2, pull.z);
            }
            MINES.remove(player.getUUID());
            MINE_UNTIL.remove(player.getUUID());
        } else {
            level.sendParticles(ParticleTypes.END_ROD, mine.x, mine.y + 0.2, mine.z,
                    2, 0.3, 0.15, 0.3, 0.01);
        }
    }

    /** 重力井戸: 半径6mの敵を吸引+継続ダメージ。4秒で消滅 */
    private static void tickGravityWell(ServerPlayer player, ServerLevel level, long now) {
        Vec3 well = WELLS.get(player.getUUID());
        if (well == null) return;
        long until = WELL_UNTIL.getOrDefault(player.getUUID(), 0L);
        if (now >= until) {
            WELLS.remove(player.getUUID());
            WELL_UNTIL.remove(player.getUUID());
            WeaponArts.pulse(level, player, well, 4.5, 1.2, false, ParticleTypes.PORTAL);
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(well.x - 6, well.y - 4, well.z - 6,
                        well.x + 6, well.y + 4, well.z + 6),
                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
            Vec3 pull = well.subtract(e.position()).normalize().scale(0.35);
            e.push(pull.x, 0.1, pull.z);
            WeaponArts.artHurt(level, e, level.damageSources().playerAttack(player), 0.5f);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, well.x, well.y + 1, well.z,
                8, 1.5, 1.5, 1.5, 0.1);
    }


    /** 囮の寿命処理は設置時の DelayRunner が担うため廃止。
     * 「敵の注意を引きつける」効果は {@link EnemyAI} の毎tick処理で abyss_decoy タグを検索して実装。 */
    public static final String DECOY_TAG = "abyss_decoy";

    private static LivingEntity nearest(Vec3 origin, ServerLevel level, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin.x - range, origin.y - 5, origin.z - range,
                        origin.x + range, origin.y + 5, origin.z + range),
                x -> x.isAlive() && !(x instanceof ServerPlayer))) {
            double d = e.position().distanceToSqr(origin);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }
}
