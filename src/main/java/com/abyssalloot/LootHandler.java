package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 戦利品まわり: 深淵MOBのドロップ抑制・素材/欠片の配布・破壊物の破壊効果・武器スキル (撃破時発動).
 * CombatHandler から分離 (giveOrDrop は Achievements 等の他クラスからも使う).
 */
public final class LootHandler {
    private LootHandler() {}

    /** 深淵タグ持ちか (ドロップ抑止・放浪判定の共用) */
    static boolean hasAbyssTag(net.minecraft.world.entity.Entity e) {
        for (String t : e.getTags()) {
            if (t.startsWith("abyss_")) return true;
        }
        return false;
    }

    /**
     * 深淵MOBのバニラドロップを消す (骨・矢・火薬などのゴミ拾い防止).
     * Mod の報酬 (武器・欠片) は直接配布のため影響なし。経験値オーブは残る.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMobDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof ServerPlayer) return;
        boolean ours = RunManager.BOSS_BARS.containsKey(e) || hasAbyssTag(e);
        if (ours) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    /** インベントリに入らなければ足元に落とす (配布の共通処理) */
    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            var level = (ServerLevel) player.level();
            var e = new net.minecraft.world.entity.item.ItemEntity(
                    level, player.getX(), player.getY() + 1, player.getZ(), stack);
            level.addFreshEntity(e);
        }
    }

    /** 素材の抽選 (古代50/混沌30/虚空20。原作 MATERIALS の重みに対応) */
    static ItemStack randomMaterial(net.minecraft.util.RandomSource rand) {
        float r = rand.nextFloat();
        if (r < 0.12f) return new ItemStack(ModItems.VOID_ESSENCE.get());
        if (r < 0.30f) return new ItemStack(ModItems.CHAOS_GEM.get());
        if (r < 0.48f) return new ItemStack(ModItems.ANCIENT_STONE.get());
        var extra = new ItemStack[]{
                new ItemStack(ModItems.ABYSS_BLOOD_CRYSTAL.get()),
                new ItemStack(ModItems.ABYSS_SHADOW_STONE.get()),
                new ItemStack(ModItems.ABYSS_STAR_DUST.get()),
                new ItemStack(ModItems.ABYSS_WYRM_SCALE.get()),
                new ItemStack(ModItems.ABYSS_ANCIENT_CORE.get()),
                new ItemStack(ModItems.ABYSS_VOID_PIECE.get())};
        return extra[rand.nextInt(extra.length)];
    }

    /** 精鋭の追加ドロップ: 新素材1〜2個 (18%) */
    static void eliteMaterialDrop(ServerPlayer player, net.minecraft.util.RandomSource rand) {
        if (rand.nextFloat() < 0.18f) {
            giveOrDrop(player, randomMaterial(rand));
            if (rand.nextFloat() < 0.4f) giveOrDrop(player, randomMaterial(rand));
        }
    }

    /** 破壊物 (赤水晶・青瓶・運命の祭壇) を壊した時の効果 */
    static void triggerDestructible(ServerPlayer player, ServerLevel level, String kind,
                                    net.minecraft.core.BlockPos pos) {
        var at = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        switch (kind) {
            case "red" -> {
                // 赤水晶: 原作通り 攻撃×4.2 の爆発 (220px ≒ 半径4)。自分にも当たる
                WeaponArts.pulse(level, player, at, 4, 4.2, true,
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION);
                player.sendSystemMessage(Component.literal("§c赤水晶が爆発した！"));
            }
            case "blue" -> {
                // 青瓶: 原作通り 周囲の敵を凍結 (250px ≒ 半径5)。回復はしない
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        at.x, at.y + 1, at.z, 30, 2, 1.5, 2, 0.1);
                for (var e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(at.x - 5, at.y - 4, at.z - 5,
                                at.x + 5, at.y + 4, at.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4));
                    e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 1));
                }
                player.sendSystemMessage(Component.literal("§b青の霊薬！ 周囲の敵が凍結した"));
            }
            default -> {
                // 運命の祭壇: 原作の5 fate から1つ (rage/gold/haste/armor/scourge)
                String[] fates = {"rage", "gold", "haste", "armor", "scourge"};
                String fate = fates[player.getRandom().nextInt(fates.length)];
                int dur = fate.equals("scourge") ? 400 : fate.equals("gold") || fate.equals("haste") ? 600 : 500;
                RunManager.setShrine(player, fate, dur);
                String label = switch (fate) {
                    case "rage" -> "§c怒りの加護: 与ダメ+60%・被ダメ+20% (25秒)";
                    case "gold" -> "§6黄金の祝福: 素材+150%・最大HP-20 (30秒)";
                    case "haste" -> "§a風の加護: 攻速・移動が上昇 (30秒)";
                    case "armor" -> "§7大地の加護: 被ダメ-50%・回避不可 (25秒)";
                    default -> "§5深淵の試練: 毎秒2損耗・運+100 (20秒)";
                };
                player.sendSystemMessage(Component.literal("§e【運命の祭壇】" + label));
            }
        }
    }

    /** 武器スキル (撃破時発動)。主手持ちの abyss_skill を読む */
    static void triggerWeaponSkill(ServerPlayer player, ServerLevel level, LivingEntity dead) {
        String skill = GearData.skillOf(player.getMainHandItem());
        if (skill == null) return;
        Vec3 pos = dead.position();
        switch (skill) {
            case "w_soul" -> RunManager.addGauge(player, 8);
            case "w_feast" -> RunManager.heal(player, player.getMaxHealth() * 0.05);
            case "w_chain" -> WeaponArts.pulse(level, player, pos, 4, 0.8, false,
                    net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            case "w_break" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 5, pos.y - 4, pos.z - 5,
                                pos.x + 5, pos.y + 4, pos.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                }
            }
            case "w_swift" -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0));
            case "w_burst" -> WeaponArts.pulse(level, player, pos, 3, 1.5, true,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION);
            case "w_venge" -> player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
            case "w_tide" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 6, pos.y - 4, pos.z - 6,
                                pos.x + 6, pos.y + 4, pos.z + 6),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    Vec3 pull = pos.subtract(e.position()).normalize().scale(1.6);
                    e.push(pull.x, 0.2, pull.z);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        pos.x, pos.y + 1, pos.z, 20, 2, 1, 2, 0.1);
            }
            case "w_gale" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1));
                RunManager.addGauge(player, 6);
            }
            case "w_ignite" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 5, pos.y - 4, pos.z - 5,
                                pos.x + 5, pos.y + 4, pos.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
                }
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        pos.x, pos.y + 1, pos.z, 20, 1.5, 1, 1.5, 0.1);
            }
            case "w_greed" -> {
                if (player.getRandom().nextFloat() < 0.20f) {
                    giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get()));
                    player.displayClientMessage(Component.literal("§6強奪！ 魂の欠片を奪った"), true);
                }
            }
            case "w_hymn" -> {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 0));
                RunManager.heal(player, player.getMaxHealth() * 0.02);
            }
            case "w_warcry" -> {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 100, 0));
            }
            case "w_avalanche" -> {
                var cands = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 6, pos.y - 4, pos.z - 6,
                                pos.x + 6, pos.y + 4, pos.z + 6),
                        x -> x.isAlive() && !(x instanceof ServerPlayer));
                cands.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)));
                int n = 0;
                for (LivingEntity e : cands) {
                    if (n++ >= 3) break;
                    Vec3 pull = pos.subtract(e.position()).normalize().scale(2.0);
                    e.push(pull.x, 0.2, pull.z);
                }
            }
            case "w_storm" -> {
                var target = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 12, pos.y - 6, pos.z - 12,
                                pos.x + 12, pos.y + 6, pos.z + 12),
                        x -> x.isAlive() && !(x instanceof ServerPlayer)).stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                        .orElse(null);
                if (target != null) {
                    WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                            (float) (StatCalc.attackDamageOf(player) * 1.2));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            target.getX(), target.getY() + 1, target.getZ(), 16, 0.4, 0.8, 0.4, 0.1);
                }
            }
            case "w_rend" -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 5, pos.y - 4, pos.z - 5,
                                pos.x + 5, pos.y + 4, pos.z + 5),
                        x -> x.isAlive() && !(x instanceof ServerPlayer))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                }
            }
            default -> {
            }
        }
    }
}
