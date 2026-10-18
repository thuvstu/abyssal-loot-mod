package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

/**
 * ボス・決戦まわり (RunManager から分離): 層主の生成・真ボス/終焉の座/超越者の
 * 開戦・フェーズ弾幕・勝利処理を担う。BOSS_BARS の実体は RunManager が保持する。
 */
public final class BossHandler {
    private BossHandler() {}

    public static void startTrue(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RunManager.clearRunField(player);
        player.setData(ModAttachments.FLOOR.get(), GameData.TRUE_FLOOR);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 1);
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        ArenaManager.buildArena(player);
        double hpMult = GameData.enemyHpMult(RunManager.sigilsOf(player)) * RunManager.diffHpMult(player);
        double dmgMult = GameData.enemyDmgMult(RunManager.sigilsOf(player)) * RunManager.diffDmgMult(player);
        var bar = spawnBoss(ModEntities.ZERO.get(), level, player,
                Component.literal("§f原初虚無 《ニヒル・ゼロ》"),
                4000 * hpMult, 30 * dmgMult, Enemies.SPEED_PARITY * 118 / 100.0,
                BossEvent.BossBarColor.WHITE);
        if (bar == null) throw new IllegalStateException("true boss create failed");
        player.sendSystemMessage(Component.literal("§f◆ 真・最終決戦 — 3フェーズの弾幕を耐え抜け！"));
    }

    /**
     * ボスの湧き共通: プレイヤーの手前10m・安全な高さに出現させ、
     * 能力値 (原作ベース×倍率)、カスタム名、ボスバーを整える。戻り値はボスバー (失敗でnull).
     */
    private static ServerBossEvent spawnBoss(EntityType<? extends Monster> type, ServerLevel level,
                                             ServerPlayer player, Component name,
                                             double hp, double dmg, double speed,
                                             BossEvent.BossBarColor color) {
        Monster boss = (Monster) type.create(level, EntitySpawnReason.EVENT);
        if (boss == null) return null;
        BlockPos bp = player.blockPosition();
        int sy = ArenaManager.safeSpawnY(level, bp.getX(), bp.getZ() - 10, bp.getY() + 1);
        boss.setPos(bp.getX() + 0.5, sy, bp.getZ() - 10 + 0.5);
        setMobStats(boss, hp, dmg, speed);
        boss.setCustomName(name);
        boss.setCustomNameVisible(true);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        var bar = new ServerBossEvent(name, color, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        bar.setProgress(1f);
        RunManager.BOSS_BARS.put(boss, bar);
        return bar;
    }

    /** MOBのHP・攻撃・移動速度を絶対設定する (属性上限1e6で圧縮するう。呼び出し側はHP満タンの心持で) */
    private static void setMobStats(Mob mob, double hp, double dmg, double speed) {
        var maxHp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(Math.min(1000000, hp));
            mob.setHealth(mob.getMaxHealth());
        }
        var atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(dmg);
        var spd = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(speed);
    }

    /** ボスバーのフェーズ名差し替え (真ボス・終焉の座共通) */
    private static void refreshBarLabel(Monster boss, String label) {
        for (var e : RunManager.BOSS_BARS.entrySet()) {
            if (e.getKey().getUUID().equals(boss.getUUID())) {
                e.getValue().setName(Component.literal(label));
            }
        }
    }

    private static ItemStack withRarity(ItemStack stack, String rarity, String name) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString("abyss_rarity", rarity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        GearData.refreshLore(stack);
        return stack;
    }

    /** 神話確定の武器 (真ボス・終焉の座・超越者の報酬)。ランダム武器種に神話ランクと神話の銘、スキルも確定 */
    private static ItemStack mythicWeapon(ServerPlayer player) {
        var styles = GameData.WeaponStyle.values();
        var style = styles[player.getRandom().nextInt(styles.length)];
        var stack = GearFactory.makePrizeWeapon(style, player.getRandom(), StatCalc.luckOf(player), 26);
        return withRarity(stack, "mythic", "§d神話の" + style.weaponName);
    }

    /** 真ボスのフェーズ弾幕 (原作 TRUE_BOSS_PHASES の3段階。5秒ごとに残HP割合で変化) */
    public static void tickTrueBoss(ServerLevel level, Monster boss) {
        String name = boss.getCustomName() != null ? boss.getCustomName().getString() : "";
        if (!name.contains("ニヒル")) return;
        double ratio = boss.getHealth() / Math.max(1, boss.getMaxHealth());
        var ph = GameData.truePhaseFor(ratio);
        var at = boss.position();
        // フェーズ名をボスバーに反映
        refreshBarLabel(boss, "§f原初虚無 《ニヒル・ゼロ》 — " + ph.label());
        WeaponArts.enemyPulse(level, at, 4 + ph.bullets() * 0.4, 6 + ph.bullets() * 0.7,
                ratio > 0.66 ? ParticleTypes.PORTAL : ratio > 0.33 ? ParticleTypes.FLAME : ParticleTypes.SOUL);
        if (ph.summon() > 0) {
            for (int i = 0; i < Math.min(2, ph.summon()); i++) {
                summonMinion(level, boss, i == 0 ? ModEntities.SKELETON.get() : ModEntities.GOBLIN.get());
            }
        } else if (ratio <= 0.66) {
            summonMinion(level, boss, ModEntities.SKELETON.get());
        }
    }

    /** 終焉の座のフェーズ弾幕 (原作 PINNACLE_PHASES の4段階) */
    public static void tickPinnacleBoss(ServerLevel level, Monster boss) {
        String name = boss.getCustomName() != null ? boss.getCustomName().getString() : "";
        if (!name.contains("タルタロス")) return;
        double ratio = boss.getHealth() / Math.max(1, boss.getMaxHealth());
        var ph = GameData.pinnaclePhaseFor(ratio);
        var at = boss.position();
        refreshBarLabel(boss, "§c終焉神 《タルタロス・オメガ》 — " + ph.label());
        WeaponArts.enemyPulse(level, at, 5 + ph.bullets() * 0.35, 10 + ph.bullets() * 0.8,
                ParticleTypes.SOUL);
        if (ph.shieldGuards() || ph.cataclysm()) {
            summonMinion(level, boss, ModEntities.IMP.get());
        }
        if (ph.cataclysm()) {
            summonMinion(level, boss, ModEntities.SKELETON.get());
        }
    }

    private static void summonMinion(ServerLevel level, Monster boss, EntityType<? extends Monster> type) {
        long count = level.getEntitiesOfClass(Monster.class,
                new AABB(boss.blockPosition()).inflate(24),
                m -> !RunManager.BOSS_BARS.containsKey(m)).size();
        if (count >= 6) return;
        Monster m = (Monster) type.create(level, EntitySpawnReason.EVENT);
        if (m == null) return;
        var p = boss.blockPosition().offset(
                boss.getRandom().nextInt(9) - 4, 1, boss.getRandom().nextInt(9) - 4);
        int my = ArenaManager.safeSpawnY(level, p.getX(), p.getZ(), p.getY());
        m.setPos(p.getX() + 0.5, my, p.getZ() + 0.5);
        var target = level.getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), 48, false);
        if (target instanceof ServerPlayer sp) m.setTarget(sp);
        else if (target != null) m.setTarget(target);
        level.addFreshEntity(m);
    }

    /** 真ボス撃破 */
    public static void trueVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§f★ 虚無を_zeroに還した！ ★"));
        player.getInventory().add(mythicWeapon(player));
        player.getInventory().add(GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 26, 130));
        RunManager.sendSoulSummary(player, true);
        Achievements.grantAch(player, "true-clear", "虚無を_zeroに還した者", 32);
        player.sendSystemMessage(Component.literal("§c終焉の座が開いた… /abyss pinnacle で《タルタロス・オメガ》に挑め"));
        RunManager.endRun(player);
    }

    /** 終焉の座 (原作PINNACLE_FLOOR=27。単層決戦) */
    public static void startPinnacle(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RunManager.clearRunField(player);
        player.setData(ModAttachments.FLOOR.get(), GameData.PINNACLE_FLOOR);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 1);
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        ArenaManager.buildArena(player);
        var def = GameData.bossForFloor(GameData.PINNACLE_FLOOR);
        double hpMult = GameData.enemyHpMult(RunManager.sigilsOf(player)) * RunManager.diffHpMult(player);
        double dmgMult = GameData.enemyDmgMult(RunManager.sigilsOf(player)) * RunManager.diffDmgMult(player);
        var bar = spawnBoss(ModEntities.OMEGA.get(), level, player,
                Component.literal("§c" + def.name()),
                def.hp() * hpMult / 20, def.dmg() * dmgMult / 4,
                Enemies.SPEED_PARITY * def.speed() / 100.0,
                BossEvent.BossBarColor.RED);
        if (bar == null) throw new IllegalStateException("pinnacle boss create failed");
        player.sendSystemMessage(Component.literal("§c◆ 終焉の座 — 4フェーズの審判を耐え抜け！ (MC向けにHP・攻撃を圧縮)"));
    }

    /** 終焉の座の撃破 */
    public static void pinnacleVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6★ 終焉を穿った！タルタロス・オメガ討伐！ ★"));
        for (int i = 0; i < 3; i++) {
            player.getInventory().add(mythicWeapon(player));
        }
        var setDrop = GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 27, 130);
        withRarity(setDrop, "mythic", setDrop.getHoverName().getString());
        player.getInventory().add(setDrop);
        var a = GameData.achievementById("pinnacle-god");
        Achievements.grantAch(player, "pinnacle-god", a.title(), GameData.shardsForReward(a.reward()));
        RunManager.endRun(player);
    }

    /** 超越者 (原作 b_ascension・28層。無限深淵で到達する裏決戦) */
    public static void ascensionVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§e★ 超越者を越えた！深淵の果てに名を刻んだ！ ★"));
        for (int i = 0; i < 2; i++) {
            player.getInventory().add(mythicWeapon(player));
        }
        player.getInventory().add(GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 28, 130));
        int shards = RunManager.materialCount(player, 32);
        if (shards > 0) {
            player.getInventory().add(new ItemStack(
                    ModItems.SOUL_SHARD.get(), shards));
        }
        RunManager.sendSoulSummary(player, true);
        ArenaManager.clearArenaBarriers(player);
    }

    static void spawnFloorBoss(ServerPlayer player, ServerLevel level, BlockPos center,
                                       int floor, Enemies.Scale scale) {
        GameData.BossDef def = GameData.bossForFloor(floor);
        String bossName = def != null ? def.name() : "層主 第" + floor + "層";
        Mob boss = (Mob) Enemies.bossMobFor(floor).create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("boss create failed");
        int sy = ArenaManager.safeSpawnY(level, center.getX(), center.getZ() - 10, center.getY() + 1);
        boss.setPos(center.getX() + 0.5, sy, center.getZ() - 10 + 0.5);
        double hpBase = def != null ? def.hp() : 60 + floor * 60;
        double dmgBase = def != null ? def.dmg() : 8 + floor * 1.6;
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(1000000, hpBase * scale.hp() / Math.max(1, GameData.floorHpScale(floor))));
            boss.setHealth(boss.getMaxHealth());
        }
        var atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(dmgBase * scale.dmg() / Math.max(1, GameData.floorDmgScale(floor)));
        double spdBase = def != null ? def.speed() : 90;
        var spd = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(Enemies.SPEED_PARITY * spdBase / 100.0 * scale.speed());
        boss.setCustomName(Component.literal("§4" + bossName));
        boss.setCustomNameVisible(true);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        if (boss instanceof Monster monster) {
            var bar = new ServerBossEvent(
                    Component.literal("§4" + bossName),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            bar.addPlayer(player);
            bar.setProgress(1f);
            RunManager.BOSS_BARS.put(monster, bar);
        }
    }

    /** 超越者 (原作 b_ascension・28層。無限深淵で到達する裏決戦) */
    static void spawnAscensionBoss(ServerPlayer player, ServerLevel level, BlockPos center,
                                           Enemies.Scale scale) {
        var def = GameData.bossForFloor(GameData.ASCENSION_FLOOR);
        Mob boss = ModEntities.ASCENDER.get().create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("ascension boss create failed");
        int sy = ArenaManager.safeSpawnY(level, center.getX(), center.getZ() - 12, center.getY() + 1);
        boss.setPos(center.getX() + 0.5, sy, center.getZ() - 12 + 0.5);
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            // MC の属性上限に収まるよう圧縮 (原作85万/属性倍率)
            hp.setBaseValue(Math.min(1000000, 40000 * scale.hp() / Math.max(1, GameData.floorHpScale(28))));
            boss.setHealth(boss.getMaxHealth());
        }
        var atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(def.dmg() * scale.dmg() / Math.max(1, GameData.floorDmgScale(28)) / 6);
        }
        var spd = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(Enemies.SPEED_PARITY * def.speed() / 100.0 * scale.speed());
        boss.setCustomName(Component.literal("§e" + def.name()));
        boss.setCustomNameVisible(true);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        player.setData(ModAttachments.WAVE_TOTAL.get(),
                player.getData(ModAttachments.WAVE_TOTAL.get()) + 1);
        var bar = new ServerBossEvent(Component.literal("§e" + def.name()),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        bar.setProgress(1f);
        if (boss instanceof Monster monster) {
            RunManager.BOSS_BARS.put(monster, bar);
        }
        player.sendSystemMessage(Component.literal("§e◆ 超越者 — 真の深淵と終焉の座を制した者だけが立てる相手！"));
    }
}
