package com.abyssalloot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 魂ゲージ・奥義/必殺/小技/回避の発動・見切り・独自ダメージ式.
 * 操作:
 *  - 武器で右クリック: 武器小技 (CT1~3秒で連発)
 *  - スニーク+右クリック: 必殺技 (魂不要, CT25秒)
 *  - Rキー / /abyss art: 武器奥義 (魂ゲージ100で発動)
 *  - Xキー / /abyss dash: 回避ダッシュ (0.5秒無敵, 見切り延長あり)
 */
public final class CombatHandler {
    private CombatHandler() {}

    /** 現在のサーバtick (KillHandler が撃破時刻を記録するのに使う) */
    static int tickCounter() {
        return tickCounter;
    }

    /** 最終HUD送信tick (間引き送信用) */
    private static final java.util.Map<java.util.UUID, Long> LAST_HUD_TICK =
            new java.util.HashMap<>();

    /** 装備変更で即時ステータス再計算 (防具の着脱・持替でHP等が追従する) */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isAlive()) return;
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        StatCalc.applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()), true);
    }

    /** 出撃中の死亡: 死なずにリタイア処理 (装備・欠片は保持)。出撃外では通常死亡 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getData(ModAttachments.RUN_LOCK.get()) == 0
                && player.getData(ModAttachments.WAVE_TOTAL.get()) == 0) return;
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth() * 0.5f);
        player.removeAllEffects();
        RunManager.retire(player);
        player.sendSystemMessage(Component.literal("§c敗北…深淵の加護で生還した (装備・欠片は保持)"));
    }

    /** 撃破で魂ゲージ蓄積 (原作 artChargeGain の簡易版: 固定+敵最大HP比) */

    /* ---------------- 破壊物 ---------------- */

    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // 生活ツール能力: 対応ブロックで欠片・素材ボーナス
        triggerToolAbility(player, level, event.getPos());
        String raw = player.getData(ModAttachments.DESTRUCT.get());
        if (raw == null || raw.isBlank()) return;
        var pos = event.getPos();
        var keep = new java.util.ArrayList<String>();
        boolean hit = false;
        for (String d : raw.split(";")) {
            String[] p = d.split(",");
            if (p.length != 4) continue;
            try {
                int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
                if (!hit && x == pos.getX() && y == pos.getY() && z == pos.getZ()) {
                    hit = true;
                    LootHandler.triggerDestructible(player, level, p[0], pos);
                } else {
                    keep.add(d);
                }
            } catch (NumberFormatException ignored) {}
        }
        if (hit) player.setData(ModAttachments.DESTRUCT.get(), String.join(";", keep));
    }

    /** 生活ツール能力。手持ちの深淵ツールに応じた採集ボーナス */
    private static void triggerToolAbility(ServerPlayer player, ServerLevel level,
                                           net.minecraft.core.BlockPos pos) {
        var held = player.getMainHandItem().getItem();
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock()).getPath();
        var rand = player.getRandom();
        Runnable bonus = () -> {
            LootHandler.giveOrDrop(player, new ItemStack(ModItems.SOUL_SHARD.get()));
            player.displayClientMessage(Component.literal("§b深淵の恵み: 魂の欠片+1"), true);
        };
        if (held == ModItems.ABYSS_PICKAXE.get()) {
            // 鉱石から12%で欠片 (採掘属性で+0.5%/点)
            if (path.contains("ore") && rand.nextDouble() < 0.12 + StatCalc.attrEff(player, 8) * 0.005) bonus.run();
        } else if (held == ModItems.ABYSS_TOOL_AXE.get()) {
            // 原木・幹から10%で欠片 (採掘属性で+0.5%/点)
            if ((path.contains("log") || path.contains("stem"))
                    && rand.nextDouble() < 0.10 + StatCalc.attrEff(player, 8) * 0.005) {
                bonus.run();
            }
        } else if (held == ModItems.ABYSS_SHOVEL.get()) {
            // 土・砂・砂利から8%で欠片 (採掘属性で+0.5%/点)
            if ((path.contains("dirt") || path.contains("sand") || path.contains("gravel"))
                    && rand.nextDouble() < 0.08 + StatCalc.attrEff(player, 8) * 0.005) {
                bonus.run();
            }
        } else if (held == ModItems.ABYSS_HOE.get()) {
            // 作物から25%で種+1
            if ((path.contains("wheat") || path.contains("carrot") || path.contains("potato")
                    || path.contains("beetroot") || path.contains("melon") || path.contains("pumpkin"))
                    && rand.nextDouble() < 0.25) {
                LootHandler.giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS));
            }
        } else if (held == ModItems.ABYSS_SHEARS.get()) {
            // 蜘蛛の巣から50%で糸+2、葉から10%で棒
            if (path.contains("cobweb") && rand.nextDouble() < 0.5) {
                LootHandler.giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.STRING, 2));
            } else if (path.contains("leaves") && rand.nextDouble() < 0.10) {
                LootHandler.giveOrDrop(player, new ItemStack(net.minecraft.world.item.Items.STICK));
            }
        }
    }

    /** 釣果2倍 (深淵の釣竿・30%で各ドロップ複製) */
    @SubscribeEvent
    public static void onFished(net.neoforged.neoforge.event.entity.player.ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getMainHandItem().getItem() != ModItems.ABYSS_FISHING_ROD.get()
                && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).getItem()
                        != ModItems.ABYSS_FISHING_ROD.get()) return;
        if (player.getRandom().nextFloat() < 0.30f) {
            for (ItemStack s : java.util.List.copyOf(event.getDrops())) {
                event.getDrops().add(s.copy());
            }
            player.displayClientMessage(Component.literal("§b大漁！"), true);
        }
    }


    /** HUDの即時更新 (未選択カウントを出すため) */
    static void sendHud(ServerPlayer player) {
        LAST_HUD_TICK.put(player.getUUID(), (long) tickCounter);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ModPackets.HudSync(
                        player.getData(ModAttachments.SOUL.get()),
                        player.getData(ModAttachments.FLOOR.get()),
                        player.getData(ModAttachments.LEVEL.get()),
                        player.getData(ModAttachments.KILLS.get()),
                        player.getData(ModAttachments.COMBO.get()),
                        StatCalc.attrPointsLeft(player),
                        player.getData(ModAttachments.PORTAL_OPEN.get()),
                        RunManager.pendingBoonsOf(player),
                        player.getData(ModAttachments.LIGHT_CD.get()),
                        player.getData(ModAttachments.SKILL_CD.get()),
                        player.getData(ModAttachments.RELIC_OFFER.get()).isBlank() ? 0 : 1,
                        player.getData(ModAttachments.PASSIVE_OFFER.get()).isBlank() ? 0 : 1));
    }

    /**
     * HUDの間引き送信 (撃破・魂ゲージ変動など頻繁な変化用).
     * 0.2秒以内の連発は間引くことで、撃破数・ゲージ・転移門の開放がほぼ即時に
     * 反映されつつパケット量を抑える (定期送信0.1〜1秒でも確実に上書きされる).
     */
    static void sendHudSoon(ServerPlayer player) {
        Long last = LAST_HUD_TICK.get(player.getUUID());
        if (last != null && tickCounter - last < 4) return;
        sendHud(player);
    }


    /** 無敵・ダッシュCT・反撃窓の減衰 + ボスバー更新 + 罠 */
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        tickCounter++;
        // 遅延タスク (絶対tick到達で発火・除去)
        DelayRunner.tick();
        EnemyAI.tickGuards(server, tickCounter);
        EnemyAI.tick(server, tickCounter);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            float iframe = player.getData(ModAttachments.IFRAME.get());
            if (iframe > 0) player.setData(ModAttachments.IFRAME.get(), Math.max(0, iframe - 0.05f));
            float dashCd = player.getData(ModAttachments.DASH_CD.get());
            if (dashCd > 0) player.setData(ModAttachments.DASH_CD.get(), Math.max(0, dashCd - 0.05f));
            float counter = player.getData(ModAttachments.COUNTER.get());
            if (counter > 0) player.setData(ModAttachments.COUNTER.get(), Math.max(0, counter - 0.05f));
            float fortify = player.getData(ModAttachments.FORTIFY.get());
            if (fortify > 0) player.setData(ModAttachments.FORTIFY.get(), Math.max(0, fortify - 0.05f));
            float skillCd = player.getData(ModAttachments.SKILL_CD.get());
            if (skillCd > 0) player.setData(ModAttachments.SKILL_CD.get(), Math.max(0, skillCd - 0.05f));
            float lightCd = player.getData(ModAttachments.LIGHT_CD.get());
            if (lightCd > 0) player.setData(ModAttachments.LIGHT_CD.get(), Math.max(0, lightCd - 0.05f));
            if (tickCounter % 10 == 0) ArenaManager.tickTraps(player);
            if (tickCounter % 20 == 0) tickAbilityBoons(player);
            if (tickCounter % 20 == 0) RunManager.tickRunEffects(player);
            // コンボ減衰 (5秒撃破なしでリセット。KillHandler が撃破時刻を管理)
            KillHandler.tickComboDecay(player, tickCounter);
            // CT中は0.1秒ごとにHUDを再送して残り秒を滑らかに表示する
            boolean anyCd = player.getData(ModAttachments.LIGHT_CD.get()) > 0
                    || player.getData(ModAttachments.SKILL_CD.get()) > 0;
            if (anyCd ? tickCounter % 2 == 0 : tickCounter % 20 == 0) {
                sendHud(player);
            }
        }
        // ボスバー進捗 + 真ボス弾幕
        var dead = new java.util.ArrayList<net.minecraft.world.entity.monster.Monster>();
        synchronized (RunManager.BOSS_BARS) {
            for (var e : RunManager.BOSS_BARS.entrySet()) {
                var boss = e.getKey();
                if (!boss.isAlive() || boss.isRemoved()) {
                    dead.add(boss);
                    continue;
                }
                e.getValue().setProgress((float) (boss.getHealth() / Math.max(1, boss.getMaxHealth())));
                if (tickCounter % 100 == 0 && boss.level() instanceof ServerLevel sl) {
                    BossHandler.tickTrueBoss(sl, boss);
                    BossHandler.tickPinnacleBoss(sl, boss);
                }
            }
        }
        for (var boss : dead) {
            var bar = RunManager.BOSS_BARS.remove(boss);
            if (bar != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.removePlayer(p);
            }
        }
    }

    /** 祝福能力の自動発動 (回転刃/聖域/魔弾・1秒ごと) */
    private static void tickAbilityBoons(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!player.isAlive()) return;
        var pos = player.position();
        // 魔術武器 (ロッド・ワンド) の自動魔弾 (1.5秒ごと)。銃魔は弾数ボーナスで多重化
        if (tickCounter % 30 == 0) {
            var style = ModItems.styleOf(player.getMainHandItem().getItem());
            if (style == GameData.WeaponStyle.ROD) {
                int targets = 1 + StatCalc.wbonusOf(player).projectiles()
                        + (int) (StatCalc.affixOf(player, "projectiles")
                                + StatCalc.extraStat(player, "projectiles"));
                SkillHandler.castWandBolt(player, 12, 0.7, targets);
            } else if (style == GameData.WeaponStyle.WAND) {
                SkillHandler.castWandBolt(player, 16, 0.9, 1);
            }
        }
        if (RunManager.boonLv(player, "orbit") > 0) {
            WeaponArts.pulse(level, player, pos, 3.5, 0.8, false,
                    net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK);
        }
        if (RunManager.boonLv(player, "aura") > 0) {
            WeaponArts.pulse(level, player, pos, 5, 0.5, false,
                    net.minecraft.core.particles.ParticleTypes.FLAME);
        }
        if (RunManager.boonLv(player, "missile") > 0) {
            // 分裂の刻印・弓の追加弾: 発射弾数 +1/Lv
            int shots = 1 + RunManager.boonLv(player, "proj")
                    + StatCalc.wbonusOf(player).projectiles()
                    + (int) (StatCalc.affixOf(player, "projectiles") + StatCalc.extraStat(player, "projectiles"));
            for (int i = 0; i < shots; i++) {
                var target = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(pos.x - 16, pos.y - 6, pos.z - 16,
                                pos.x + 16, pos.y + 6, pos.z + 16),
                        e -> e.isAlive() && !(e instanceof ServerPlayer)).stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                        .orElse(null);
                if (target != null) {
                    WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                            (float) (StatCalc.attackDamageOf(player) * 0.7));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                            target.getX(), target.getY() + 1, target.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
                    if (shots > 1) target.teleportTo(target.getX(), target.getY(), target.getZ());
                }
            }
        }
        // 影の眷属: 3秒ごとに使役が援護 (2秒周期tickの3回に1回≒6秒では遅いので毎tick判定で別カウンタ)
        if (RunManager.boonLv(player, "familiar") > 0 && tickCounter % 60 == 0) {
            var target = level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(pos.x - 12, pos.y - 6, pos.z - 12,
                            pos.x + 12, pos.y + 6, pos.z + 12),
                    e -> e.isAlive() && !(e instanceof ServerPlayer)).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                    .orElse(null);
            if (target != null) {
                WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                        (float) (StatCalc.attackDamageOf(player) * 0.5));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                        target.getX(), target.getY() + 1, target.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
            }
        }
        // 氷霜新星: 6秒ごとに凍結波
        if (RunManager.boonLv(player, "nova") > 0 && tickCounter % 120 == 0) {
            WeaponArts.pulse(level, player, pos, 6, 0.4, false,
                    net.minecraft.core.particles.ParticleTypes.SNOWFLAKE);
            for (var e : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(pos.x - 6, pos.y - 4, pos.z - 6,
                            pos.x + 6, pos.y + 4, pos.z + 6),
                    e -> e.isAlive() && !(e instanceof ServerPlayer))) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
            }
        }
    }

    public static void showStatus(ServerPlayer player) {
        GameData.HeroClass cls =
                GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        int[] a = StatCalc.attrsOf(player);
        player.sendSystemMessage(Component.literal(String.format(
                "§6[%s %s Lv%d] §f第%d層[%s/%s] §d魂%.0f/100 §c攻撃%.1f §aHP%.0f/%.0f §e撃破%d §b奥義:%s",
                cls.name, cls.title, player.getData(ModAttachments.LEVEL.get()),
                player.getData(ModAttachments.FLOOR.get()),
                player.getData(ModAttachments.MODE.get()), player.getData(ModAttachments.DIFF.get()),
                player.getData(ModAttachments.SOUL.get()),
                StatCalc.attackDamageOf(player),
                player.getHealth(), player.getMaxHealth(),
                player.getData(ModAttachments.KILLS.get()),
                style != null ? style.artName : "武器を持ってね")));
        player.sendSystemMessage(Component.literal(String.format(
                "§7属性 剛%d 韻%d 敏%d 忍%d 幸%d 集%d 活%d 技%d 採%d 跳%d §8| §7祝福%s §8| §7層主 %s",
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9],
                RunManager.boonsOf(player).toString(),
                player.getData(ModAttachments.BOSSES.get()))));
        player.sendSystemMessage(Component.literal(String.format(
                "§7区域 %s §8| §7遺物 %s §8| §7技能 %s §8| §7実績 %s",
                GameData.zoneForFloor(player.getData(ModAttachments.FLOOR.get())).name(),
                RunManager.relicsOf(player).toString(),
                RunManager.passivesOf(player).toString(),
                player.getData(ModAttachments.ACH.get()))));
        var held = player.getMainHandItem();
        if (ModItems.styleOf(held.getItem()) != null) {
            String trait = GearData.traitOf(held);
            String curse = GearData.curseOf(held);
            int affixes = 0;
            for (var ak : GameData.WEAPON_AFFIXES) {
                if (GearData.affixOf(held, ak.key()) != 0) affixes++;
            }
            player.sendSystemMessage(Component.literal(String.format(
                    "§7武器 %s (希少:%s 特性:%s%s 接辞:%d 鍛造+%d)",
                    held.getHoverName().getString(),
                    GearData.rarityOf(held),
                    trait != null ? GearData.traitName(trait) : "なし",
                    curse != null ? " 呪い:" + curse + GearData.curseValueOf(held) : "",
                    affixes,
                    GearData.forgeLv(held))));
        }
        player.sendSystemMessage(Component.literal(String.format(
                "§7スコア %d (撃破%d 与ダメ%d 最大 combo%d)",
                RunManager.calcScore(player, false),
                player.getData(ModAttachments.KILLS.get()),
                player.getData(ModAttachments.DMG_DEALT.get()),
                player.getData(ModAttachments.BEST_COMBO.get()))));
    }
}
