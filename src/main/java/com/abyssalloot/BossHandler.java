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
import net.minecraft.world.phys.Vec3;

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
                BossEvent.BossBarColor.WHITE, "abyss_true_boss");
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
                                             BossEvent.BossBarColor color, String tag) {
        Monster boss = (Monster) type.create(level, EntitySpawnReason.EVENT);
        if (boss == null) return null;
        BlockPos arena = ArenaManager.arenaCenterOf(player);
        BlockPos bp = arena != null ? arena : player.blockPosition();
        int sy = ArenaManager.safeSpawnY(level, bp.getX(), bp.getZ() - 10, bp.getY() + 1);
        boss.setPos(bp.getX() + 0.5, sy, bp.getZ() - 10 + 0.5);
        setMobStats(boss, hp, dmg, speed);
        if (tag != null) boss.addTag(tag);
        Enemies.markWaveMob(player, boss);
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

    /** MOBのHP・攻撃・移動速度を絶対設定する。呼び出し側はHP満タンの心持で。
     *  バニラ属性の上限 (MAX_HEALTH=1024 / ATTACK_DAMAGE=2048) を超える値は getValue() で
     *  クランプされるため、実効値は min(上限, 設定値)。25層以降のボスは実質 HP1024 で、
     *  難易度の差別化は攻撃力とフェーズ弾幕 (tickTrueBoss/tickPinnacleBoss) で担保している. */
    private static void setMobStats(Mob mob, double hp, double dmg, double speed) {
        var maxHp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(Math.min(1024, hp));
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
        if (!boss.getTags().contains("abyss_true_boss")) return;
        double ratio = boss.getHealth() / Math.max(1, boss.getMaxHealth());
        var ph = GameData.truePhaseFor(ratio);
        var at = boss.position();
        // フェーズ名をボスバーに反映
        refreshBarLabel(boss, "§f原初虚無 《ニヒル・ゼロ》 — " + ph.label());
        WeaponArts.enemyPulse(level, at, 4 + ph.bullets() * 0.4, 6 + ph.bullets() * 0.7,
                ratio > 0.66 ? ParticleTypes.PORTAL : ratio > 0.33 ? ParticleTypes.FLAME : ParticleTypes.SOUL);
        // フェーズごとの追加弾幕 (螺旋・リング・十字の組み合わせ)
        if (ratio > 0.66) {
            enemyRing(level, at, 4.0, 7.0, 6.0);
        } else if (ratio > 0.33) {
            enemySpiral(level, at, 2, 8, 6.0);
        } else {
            enemyCross(level, at, 8.0);
        }
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
        // フェーズごとの追加弾幕 (深層ほど弾幕が混ざり合う)
        if (ratio > 0.75) {
            enemySpiral(level, at, 2, 8, 10.0);
        } else if (ratio > 0.50) {
            enemyCross(level, at, 12.0);
        } else if (ratio > 0.25) {
            enemyRing(level, at, 4.0, 7.0, 10.0);
            enemyCross(level, at, 12.0);
        } else {
            enemySpiral(level, at, 3, 10, 12.0);
        }
        if (ph.shieldGuards() || ph.cataclysm()) {
            summonMinion(level, boss, ModEntities.IMP.get());
        }
        if (ph.cataclysm()) {
            summonMinion(level, boss, ModEntities.SKELETON.get());
        }
    }

    /* ---------------- 決戦の追加弾幕 (enemyPulse の組み合わせ) ---------------- */

    /** 螺旋状に爆発を放つ (waves 回 × bullets 方向、各 pulse は円周上の点に小爆発) */
    private static void enemySpiral(ServerLevel level, Vec3 at, int waves, int bullets, double dmg) {
        for (int wave = 0; wave < waves; wave++) {
            final int w = wave;
            DelayRunner.after(wave * 4, () -> {
                for (int i = 0; i < bullets; i++) {
                    double ang = w * 0.7 + i * (Math.PI * 2 / bullets);
                    Vec3 pos = at.add(Math.cos(ang) * 2.5, 0, Math.sin(ang) * 2.5);
                    WeaponArts.enemyPulse(level, pos, 1.2, dmg, ParticleTypes.SOUL);
                }
            });
        }
    }

    /** 同心円リング (2重): 各リング 8 点に爆発 */
    private static void enemyRing(ServerLevel level, Vec3 at, double r1, double r2, double dmg) {
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI * 2 * i / 8;
            WeaponArts.enemyPulse(level, at.add(Math.cos(ang) * r1, 0, Math.sin(ang) * r1),
                    1.5, dmg, ParticleTypes.FLAME);
            WeaponArts.enemyPulse(level, at.add(Math.cos(ang) * r2, 0, Math.sin(ang) * r2),
                    1.5, dmg, ParticleTypes.FLAME);
        }
    }

    /** 十字4点に遅延爆発 */
    private static void enemyCross(ServerLevel level, Vec3 at, double dmg) {
        for (int i = 0; i < 4; i++) {
            double ang = Math.PI / 2 * i;
            Vec3 pos = at.add(Math.cos(ang) * 3, 0, Math.sin(ang) * 3);
            DelayRunner.after(12, () -> WeaponArts.enemyPulse(level, pos, 2.2, dmg, ParticleTypes.EXPLOSION));
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
        // 召喚扱い (ウェーブ数に数えない) + 深淵タグ (retire 時の掃除対象に)
        m.addTag(Enemies.SUMMON_TAG);
        m.addTag("abyss_boss_minion");
        level.addFreshEntity(m);
    }

    /** 真ボス撃破 */
    public static void trueVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§f★ 虚無を_zeroに還した！ ★"));
        LootHandler.giveOrDrop(player, mythicWeapon(player));
        LootHandler.giveOrDrop(player, GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 26, 130));
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
                BossEvent.BossBarColor.RED, null);
        if (bar == null) throw new IllegalStateException("pinnacle boss create failed");
        player.sendSystemMessage(Component.literal("§c◆ 終焉の座 — 4フェーズの審判を耐え抜け！ (MC向けにHP・攻撃を圧縮)"));
    }

    /** 終焉の座の撃破 */
    public static void pinnacleVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6★ 終焉を穿った！タルタロス・オメガ討伐！ ★"));
        for (int i = 0; i < 3; i++) {
            LootHandler.giveOrDrop(player, mythicWeapon(player));
        }
        var setDrop = GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 27, 130);
        withRarity(setDrop, "mythic", setDrop.getHoverName().getString());
        LootHandler.giveOrDrop(player, setDrop);
        Achievements.grant(player, "pinnacle-god");
        RunManager.endRun(player);
    }

    /** 超越者 (原作 b_ascension・28層。無限深淵で到達する裏決戦) */
    public static void ascensionVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§e★ 超越者を越えた！深淵の果てに名を刻んだ！ ★"));
        for (int i = 0; i < 2; i++) {
            LootHandler.giveOrDrop(player, mythicWeapon(player));
        }
        LootHandler.giveOrDrop(player, GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 28, 130));
        int shards = RunManager.materialCount(player, 32);
        if (shards > 0) {
            LootHandler.giveOrDrop(player, new ItemStack(
                    ModItems.SOUL_SHARD.get(), shards));
        }
        RunManager.sendSoulSummary(player, true);
        ArenaManager.clearArenaBarriers(player);
    }

    /** ボスバーの生成・登録 (spawnFloorBoss / spawnAscensionBoss 共通) */
    private static void registerBossBar(ServerPlayer player, Mob boss, Component name,
                                        BossEvent.BossBarColor color) {
        if (!(boss instanceof Monster monster)) return;
        var bar = new ServerBossEvent(name, color, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        bar.setProgress(1f);
        RunManager.BOSS_BARS.put(monster, bar);
    }

    /** ボスの能力を「層スケールで原作値を割って」適用する共通処理 (25層以降は属性上限に収まる) */
    private static void applyScaledStats(Mob boss, double hpBase, double dmgBase, double speedBase,
                                         Enemies.Scale scale, int floor) {
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(1024, hpBase * scale.hp() / Math.max(1, GameData.floorHpScale(floor))));
            boss.setHealth(boss.getMaxHealth());
        }
        var atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(dmgBase * scale.dmg() / Math.max(1, GameData.floorDmgScale(floor)));
        }
        var spd = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(Enemies.SPEED_PARITY * speedBase / 100.0 * scale.speed());
    }

    static void spawnFloorBoss(ServerPlayer player, ServerLevel level, BlockPos center,
                                       int floor, Enemies.Scale scale) {
        GameData.BossDef def = GameData.bossForFloor(floor);
        String bossName = def != null ? def.name() : "層主 第" + floor + "層";
        Mob boss = (Mob) Enemies.bossMobFor(floor).create(level, EntitySpawnReason.EVENT);
        if (boss == null) {
            // 生成できなかったボスをノルマに残さない
            RunManager.addWaveTotal(player, -1);
            AbyssalLootMod.LOGGER.warn("層主の生成に失敗した (floor={})", floor);
            return;
        }
        int sy = ArenaManager.safeSpawnY(level, center.getX(), center.getZ() - 10, center.getY() + 1);
        boss.setPos(center.getX() + 0.5, sy, center.getZ() - 10 + 0.5);
        double hpBase = def != null ? def.hp() : 60 + floor * 60;
        double dmgBase = def != null ? def.dmg() : 8 + floor * 1.6;
        double spdBase = def != null ? def.speed() : 90;
        applyScaledStats(boss, hpBase, dmgBase, spdBase, scale, floor);
        boss.setCustomName(Component.literal("§4" + bossName));
        boss.setCustomNameVisible(true);
        Enemies.markWaveMob(player, boss);
        boss.setTarget(player);
        if (!level.addFreshEntity(boss)) {
            // 生成失敗時はノルマを1減らして「倒せないボス」を残さない
            RunManager.addWaveTotal(player, -1);
            return;
        }
        registerBossBar(player, boss, Component.literal("§4" + bossName), BossEvent.BossBarColor.RED);
    }

    /** 超越者 (原作 b_ascension・28層。無限深淵で到達する裏決戦) */
    static void spawnAscensionBoss(ServerPlayer player, ServerLevel level, BlockPos center,
                                           Enemies.Scale scale) {
        var def = GameData.bossForFloor(GameData.ASCENSION_FLOOR);
        Mob boss = ModEntities.ASCENDER.get().create(level, EntitySpawnReason.EVENT);
        if (boss == null) {
            AbyssalLootMod.LOGGER.warn("超越者の生成に失敗した");
            return;
        }
        int sy = ArenaManager.safeSpawnY(level, center.getX(), center.getZ() - 12, center.getY() + 1);
        boss.setPos(center.getX() + 0.5, sy, center.getZ() - 12 + 0.5);
        // MC の属性上限に収まるよう圧縮 (原作85万/属性倍率)
        applyScaledStats(boss, 40000, def.dmg() / 6, def.speed(), scale, 28);
        boss.setCustomName(Component.literal("§e" + def.name()));
        boss.setCustomNameVisible(true);
        Enemies.markWaveMob(player, boss);
        boss.setTarget(player);
        if (!level.addFreshEntity(boss)) return;
        RunManager.addWaveTotal(player, 1);
        registerBossBar(player, boss, Component.literal("§e" + def.name()), BossEvent.BossBarColor.PURPLE);
        player.sendSystemMessage(Component.literal("§e◆ 超越者 — 真の深淵と終焉の座を制した者だけが立てる相手！"));
    }
}
