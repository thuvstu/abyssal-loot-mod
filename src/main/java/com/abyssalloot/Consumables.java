package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** 消耗品の説明と使用効果 (軍糧丸・回復薬・巻物など)。RunManager から分離.
 * 使用は ModItems.ConsumableItem (右クリック) から呼ばれる。
 */
public final class Consumables {
    private Consumables() {}

    public static String consumableDesc(String kind) {
        return switch (kind) {
            case "heal_30" -> "HPを30%回復";
            case "heal_70" -> "HPを70%回復";
            case "heal_100" -> "HPを全回復";
            case "gauge_40" -> "魂ゲージ+40";
            case "gauge_100" -> "魂ゲージ全満";
            case "buff_str" -> "攻撃力上昇60秒";
            case "buff_spd" -> "移動速度上昇60秒";
            case "buff_res" -> "ダメージ耐性60秒";
            case "buff_regen" -> "再生60秒";
            case "buff_abso" -> "守護60秒";
            case "buff_fire" -> "火炎耐性120秒";
            case "buff_nv" -> "暗視120秒";
            case "buff_fall" -> "落下耐性60秒";
            case "buff_luck" -> "幸運60秒";
            case "bomb_fire" -> "前方に火炎爆発";
            case "bomb_bolt" -> "前方に雷爆発・鈍足";
            case "bomb_frost" -> "前方に氷爆発・凍結";
            case "tele_arena" -> "アリーナ中心へ転移";
            case "tele_spawn" -> "初期スポーンへ転移";
            case "cure" -> "状態異常を解除";
            case "magnet" -> "周囲の品を吸着";
            case "reveal" -> "戦況を看破する";
            case "feast" -> "満腹・小回復";
            case "smoke" -> "煙に紛れて隠形15秒";
            case "berserk" -> "攻撃力・採掘速度が30秒跳ね上がる";
            case "bulwark" -> "耐性・守護が30秒纏う";
            case "bomb_blaze" -> "前方に業火の爆発";
            case "fortune" -> "幸運の加護5分";
            case "ration" -> "満腹+8・小回復";
            case "heal_50" -> "HPを50%回復";
            case "gorging" -> "満腹全快・再生10秒";
            case "grand_feast" -> "満腹全快・HP40%・再生20秒";
            case "regen2" -> "再生II 45秒";
            case "str2" -> "攻撃力II 45秒";
            case "res2" -> "耐性II 45秒";
            case "abs2" -> "守護II 45秒";
            case "spd3" -> "移動III 30秒";
            case "nv10" -> "暗視10分";
            case "fire10" -> "防炎10分";
            case "water5" -> "水中呼吸5分";
            case "invis30" -> "隠形30秒・移動II";
            case "purity" -> "悪しざまを一括解除";
            case "chain_bomb" -> "前方に3連の爆発";
            case "art_scroll" -> "魂ゲージが満タンに";
            case "skill_reset" -> "必殺CT リセット";
            case "leap" -> "跳躍II・緩降下60秒";
            case "luck_rain" -> "砂金の加護2分";
            case "gum_leech" -> "即回復30";
            case "depth_bomb" -> "原地に大爆発";
            case "eagle" -> "周囲10m看破15秒";
            case "overcharge" -> "魂ゲージ+60";
            case "haste2" -> "採掘II 45秒";
            case "godspeed" -> "移動III・採掘II 20秒";
            case "skill_demo" -> "対応する型の必殺技を即発動";
            case "breacher" -> "軽量爆薬を前方に爆発させる";
            case "netsnare" -> "索縛の網を撒き周囲を鈍足+弱体化";
            case "reapple" -> "全回復・守護II 60秒";
            case "shard_fling" -> "欠片3個を弾いて周囲に大打撃";
            case "bell_collect" -> "拾い込み: 12m内の落し物を直接回収";
            case "care_box" -> "応急救急箱: HP60%回復・満腹";
            case "war_drums" -> "戦鼓の杯: 攻撃II・採掘II 20秒";
            default -> kind;
        };
    }

    /** 消耗品の使用。効果を適用したら true (1個消費) */
    public static boolean useConsumable(ServerPlayer player, String kind) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return false;
        Vec3 origin = player.position();
        switch (kind) {
            case "heal_30" -> RunManager.heal(player, player.getMaxHealth() * 0.3);
            case "heal_70" -> RunManager.heal(player, player.getMaxHealth() * 0.7);
            case "heal_100" -> RunManager.heal(player, player.getMaxHealth());
            case "gauge_40" -> RunManager.addGauge(player, 40);
            case "gauge_100" -> RunManager.addGauge(player, 100);
            case "buff_str" -> player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1200, 0));
            case "buff_spd" -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 0));
            case "buff_res" -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 0));
            case "buff_regen" -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 0));
            case "buff_abso" -> player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1));
            case "buff_fire" -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0));
            case "buff_nv" -> player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0));
            case "buff_fall" -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1200, 0));
            case "buff_luck" -> player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 0));
            case "bomb_fire" -> WeaponArts.pulse(level, player,
                    origin.add(player.getLookAngle().scale(8)), 4, 3.0, true, ParticleTypes.EXPLOSION);
            case "bomb_bolt" -> {
                Vec3 at = origin.add(player.getLookAngle().scale(8));
                WeaponArts.pulse(level, player, at, 5, 4.0, true, ParticleTypes.ELECTRIC_SPARK);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(at.x - 5, at.y - 4, at.z - 5, at.x + 5, at.y + 4, at.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                }
            }
            case "bomb_frost" -> {
                Vec3 at = origin.add(player.getLookAngle().scale(8));
                WeaponArts.pulse(level, player, at, 4, 3.0, false, ParticleTypes.SNOWFLAKE);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(at.x - 4, at.y - 4, at.z - 4, at.x + 4, at.y + 4, at.z + 4),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
                }
            }
            case "tele_arena" -> {
                BlockPos c = ArenaManager.arenaCenterOf(player);
                if (c == null) {
                    player.sendSystemMessage(Component.literal("§cアリーナが無い (出撃中のみ)"));
                    return false;
                }
                player.teleportTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
                level.sendParticles(ParticleTypes.PORTAL, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5,
                        30, 1, 1, 1, 0.3);
            }
            case "tele_spawn" -> {
                var rd = level.getLevelData().getRespawnData();
                if (rd == null) rd = LevelData.RespawnData.DEFAULT;
                GlobalPos gp = rd.globalPos();
                ServerLevel dest = player.level() instanceof ServerLevel sl
                        ? sl.getServer().getLevel(gp.dimension()) : level;
                if (dest == null) dest = level;
                BlockPos s = gp.pos();
                int sy = ArenaManager.safeSpawnY(dest, s.getX(), s.getZ(), s.getY() + 1);
                player.teleportTo(dest, s.getX() + 0.5, sy, s.getZ() + 0.5,
                        Set.<Relative>of(), rd.yaw(), rd.pitch(), true);
            }
            case "cure" -> {
                var bad = java.util.List.of(MobEffects.WITHER, MobEffects.POISON,
                        MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.HUNGER,
                        MobEffects.BLINDNESS, MobEffects.UNLUCK, MobEffects.MINING_FATIGUE,
                        MobEffects.DARKNESS, MobEffects.BAD_OMEN);
                for (var eff : java.util.List.copyOf(player.getActiveEffects())) {
                    if (bad.contains(eff.getEffect())) player.removeEffect(eff.getEffect());
                }
                player.displayClientMessage(Component.literal("§a浄化した"), true);
            }
            case "magnet" -> {
                for (var it : level.getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(12), e -> e.isAlive())) {
                    Vec3 d = origin.subtract(it.position()).normalize().scale(1.2);
                    it.setDeltaMovement(d.x, Math.max(0.3, d.y + 0.4), d.z);
                    it.setNoPickUpDelay();
                }
            }
            case "reveal" -> {
                int total = player.getData(ModAttachments.WAVE_TOTAL.get());
                int kills = player.getData(ModAttachments.FLOOR_KILLS.get());
                player.sendSystemMessage(Component.literal("§e看破: 残り " + Math.max(0, total - kills) + "体"));
                for (var e : RunManager.BOSS_BARS.entrySet()) {
                    if (e.getKey().level() != level || e.getKey().distanceTo(player) > 64) continue;
                    if (e.getKey().isAlive()) {
                        player.sendSystemMessage(Component.literal("§c層主 HP "
                                + Math.max(0, Math.round(e.getKey().getHealth())) + "/"
                                + Math.round(e.getKey().getMaxHealth())));
                    }
                }
            }
            case "feast" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 0));
                RunManager.heal(player, 20);
            }
            case "smoke" -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 0));
                level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + 1, origin.z,
                        40, 1.5, 1.5, 1.5, 0.1);
            }
            case "berserk" -> {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 1));
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 600, 1));
                level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y + 1, origin.z,
                        16, 0.8, 1, 0.8, 0.1);
            }
            case "bulwark" -> {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 1));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
                level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y + 1, origin.z,
                        16, 0.8, 1, 0.8, 0.1);
            }
            case "bomb_blaze" -> {
                Vec3 at = origin.add(player.getLookAngle().scale(8));
                WeaponArts.pulse(level, player, at, 5, 3.2, true, ParticleTypes.FLAME);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(at.x - 5, at.y - 4, at.z - 5, at.x + 5, at.y + 4, at.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 160));
                }
            }
            case "fortune" -> {
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 6000, 0));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, origin.x, origin.y + 1, origin.z,
                        16, 0.8, 1, 0.8, 0.1);
            }
            case "ration" -> {
                // 満腹度回復 (バニラの食料システムとの融合。試練中の食繋ぎに)
                player.getFoodData().eat(8, 0.6F);
                RunManager.heal(player, 10);
            }
            case "heal_50" -> RunManager.heal(player, player.getMaxHealth() * 0.5);
            case "gorging" -> {
                player.getFoodData().eat(20, 1.0F);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            }
            case "grand_feast" -> {
                player.getFoodData().eat(20, 1.2F);
                RunManager.heal(player, player.getMaxHealth() * 0.4);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 0));
            }
            case "regen2" -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            case "str2" -> player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 900, 1));
            case "res2" -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 900, 1));
            case "abs2" -> player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 900, 2));
            case "spd3" -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 2));
            case "nv10" -> player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 12000, 0));
            case "fire10" -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 12000, 0));
            case "water5" -> player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 6000, 0));
            case "invis30" -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 1));
            }
            case "purity" -> {
                java.util.List.of(MobEffects.WITHER, MobEffects.POISON, MobEffects.SLOWNESS,
                        MobEffects.WEAKNESS, MobEffects.MINING_FATIGUE, MobEffects.DARKNESS,
                        MobEffects.HUNGER, MobEffects.BLINDNESS, MobEffects.UNLUCK,
                        MobEffects.NAUSEA, MobEffects.LEVITATION).forEach(player::removeEffect);
            }
            case "chain_bomb" -> {
                Vec3 look = player.getLookAngle();
                for (int i = 0; i < 3; i++) {
                    Vec3 at = origin.add(look.scale(4 + i * 3));
                    final Vec3 target = at;
                    final boolean last = (i == 2);
                    DelayRunner.after(4 + i * 4, () -> {
                        if (!player.isAlive()) return;
                        WeaponArts.pulse((ServerLevel) player.level(), player, target, 3.5, 2.2,
                                last, ParticleTypes.EXPLOSION);
                    });
                }
            }
            case "art_scroll" -> {
                // addGauge 経由にして MAX到達時の効果も発動させる
                RunManager.addGauge(player, 100);
            }
            case "skill_reset" -> {
                player.setData(ModAttachments.SKILL_CD.get(), 0f);
                player.displayClientMessage(Component.literal("§d必殺技の呼吸が整った — CT リセット！"), true);
            }
            case "leap" -> {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 1200, 1));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1200, 0));
            }
            case "luck_rain" -> {
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 2400, 0));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, origin.x, origin.y + 2, origin.z,
                        40, 2, 2, 2, 0.2);
            }
            case "gum_leech" -> RunManager.heal(player, 30);
            case "depth_bomb" -> WeaponArts.pulse(level, player, origin, 6, 3.5, true,
                    ParticleTypes.EXPLOSION_EMITTER);
            case "eagle" -> {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(10), e -> e.isAlive())) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
                }
                player.displayClientMessage(Component.literal("§b鷹の眼が開いた — 周囲10mを見破る (15秒)"), true);
            }
            case "overcharge" -> RunManager.addGauge(player, 60);
            case "haste2" -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, 900, 1));
            case "godspeed" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 2));
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 400, 1));
            }
            case "breacher" -> {
                Vec3 at = origin.add(player.getLookAngle().scale(5));
                WeaponArts.pulse(level, player, at, 3, 1.5, true, ParticleTypes.EXPLOSION);
            }
            case "netsnare" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(8),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_COBWEB, origin.x, origin.y + 1, origin.z,
                        40, 3, 1, 3, 0.1);
                player.displayClientMessage(Component.literal("§7索縛の網が絡みついた"), true);
            }
            case "reapple" -> {
                RunManager.heal(player, player.getMaxHealth());
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1));
            }
            case "shard_fling" -> {
                if (FloorEvents.countShards(player) < 3) {
                    player.displayClientMessage(Component.literal("§c欠片3個が無いと魂を弾けない (所持" + FloorEvents.countShards(player) + ")"), true);
                    return false;
                }
                FloorEvents.takeShards(player, 3);
                WeaponArts.pulse(level, player, origin.add(player.getLookAngle().scale(4)), 4, 4.0, true,
                        ParticleTypes.SOUL);
                level.sendParticles(ParticleTypes.SOUL, origin.x, origin.y + 1, origin.z, 30, 2, 1, 2, 0.2);
            }
            case "bell_collect" -> {
                int got = 0;
                for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(12), e -> e.isAlive())) {
                    LootHandler.giveOrDrop(player, it.getItem().copy());
                    it.discard();
                    got++;
                }
                if (got == 0) {
                    player.displayClientMessage(Component.literal("§7鐘が響くが回収するものは無い"), true);
                } else {
                    level.sendParticles(ParticleTypes.NOTE, origin.x, origin.y + 2, origin.z, 16, 1, 1, 1, 1.0);
                    player.displayClientMessage(Component.literal("§e鐘の音に呼ばれ " + got + " 種が飛んできた！"), true);
                }
            }
            case "care_box" -> {
                RunManager.heal(player, player.getMaxHealth() * 0.6);
                player.getFoodData().eat(10, 0.5F);
            }
            case "war_drums" -> {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 1));
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 400, 1));
                RunManager.heal(player, player.getMaxHealth() * 0.1);
            }
            default -> {
                if (kind.startsWith("skill_demo::")) {
                    // 職業の必殺巻物: その型の必殺技をCTなしで1度だけ発動
                    var castCls = GameData.HeroClass.byId(kind.substring("skill_demo::".length()));
                    WeaponArts.executeSkill(player, castCls,
                            StatCalc.attackDamageOf(player) * StatCalc.artMultOf(player));
                    player.sendSystemMessage(Component.literal(
                            "§d「" + castCls.skillName() + "」の型をなぞった！"));
                    return true;
                }
                return false;
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    /** 出撃中か (呪印・モード・難易度の固定・原作 /api/expeditions の条件固定に相当) */
}
