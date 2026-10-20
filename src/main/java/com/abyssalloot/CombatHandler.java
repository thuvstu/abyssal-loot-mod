package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

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
    private static final Map<UUID, Long> LAST_HUD_TICK =
            new HashMap<>();

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

    /** ログアウトでHUD間引き・撃破時刻・防具署名のキャッシュを掃除 (UUID再利用の誤爆防止) */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_HUD_TICK.remove(player.getUUID());
            KillHandler.onLogout(player);
            RunManager.onLogout(player);
        }
    }

    /* ---------------- 破壊物 ---------------- */

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        // 生活ツール能力: 対応ブロックで欠片・素材ボーナス
        triggerToolAbility(player, level, event.getPos());
        String raw = player.getData(ModAttachments.DESTRUCT.get());
        if (raw == null || raw.isBlank()) return;
        var pos = event.getPos();
        var keep = new ArrayList<String>();
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
                                           BlockPos pos) {
        var held = player.getMainHandItem().getItem();
        String path = BuiltInRegistries.BLOCK
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
                LootHandler.giveOrDrop(player, new ItemStack(Items.WHEAT_SEEDS));
            }
        } else if (held == ModItems.ABYSS_SHEARS.get()) {
            // 蜘蛛の巣から50%で糸+2、葉から10%で棒
            if (path.contains("cobweb") && rand.nextDouble() < 0.5) {
                LootHandler.giveOrDrop(player, new ItemStack(Items.STRING, 2));
            } else if (path.contains("leaves") && rand.nextDouble() < 0.10) {
                LootHandler.giveOrDrop(player, new ItemStack(Items.STICK));
            }
        }
    }

    /** 釣果2倍 (深淵の釣竿・30%で各ドロップ複製) */
    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getMainHandItem().getItem() != ModItems.ABYSS_FISHING_ROD.get()
                && player.getItemBySlot(EquipmentSlot.OFFHAND).getItem()
                        != ModItems.ABYSS_FISHING_ROD.get()) return;
        if (player.getRandom().nextFloat() < 0.30f) {
            for (ItemStack s : List.copyOf(event.getDrops())) {
                event.getDrops().add(s.copy());
            }
            player.displayClientMessage(Component.literal("§b大漁！"), true);
        }
    }

    /** HUDの即時更新 (未選択カウントを出すため) */
    static void sendHud(ServerPlayer player) {
        LAST_HUD_TICK.put(player.getUUID(), (long) tickCounter);
        PacketDistributor.sendToPlayer(player,
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
                        player.getData(ModAttachments.PASSIVE_OFFER.get()).isBlank() ? 0 : 1,
                        player.getData(ModAttachments.SUB_SKILL.get()),
                        player.getData(ModAttachments.SUB_CD.get())));    }

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

    /** サーバtickカウンタ (各種周期処理・撃破時刻の基準) */
    private static int tickCounter = 0;

    /** 無敵・ダッシュCT・反撃窓の減衰 + ボスバー更新 + 罠 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        tickCounter++;
        // 遅延タスク (絶対tick到達で発火・除去)
        DelayRunner.tick();
        // ダメージ数値の浮遊表示 (上昇・寿命管理)
        DamageNumbers.tick();
        EnemyAI.tickGuards(server, tickCounter);
        EnemyAI.tick(server, tickCounter);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickCooldowns(player);
            if (tickCounter % 10 == 0) ArenaManager.tickTraps(player);
            if (tickCounter % 10 == 0) SubSkills.tickWorld(player);
            if (tickCounter % 20 == 0) {
                tickAbilityBoons(player);
                RunManager.tickRunEffects(player);
            }
            // コンボ減衰 (5秒撃破なしでリセット。KillHandler が撃破時刻を管理)
            KillHandler.tickComboDecay(player, tickCounter);
            // CT中は0.2秒ごとにHUDを再送して残り秒を表示する (0.1秒ごとの送信はパケット過多のため間引き)
            boolean anyCd = player.getData(ModAttachments.LIGHT_CD.get()) > 0
                    || player.getData(ModAttachments.SKILL_CD.get()) > 0
                    || player.getData(ModAttachments.SUB_CD.get()) > 0;
            if (anyCd ? tickCounter % 4 == 0 : tickCounter % 20 == 0) {
                sendHud(player);
            }
        }
        tickBossBars(server);
    }

    /** 無敵・CT・反撃窓の減衰 (1tick=0.05秒) */
    private static void tickCooldowns(ServerPlayer player) {
        decay(player, ModAttachments.IFRAME);
        decay(player, ModAttachments.DASH_CD);
        decay(player, ModAttachments.COUNTER);
        decay(player, ModAttachments.FORTIFY);
        decay(player, ModAttachments.SKILL_CD);
        decay(player, ModAttachments.LIGHT_CD);
        decay(player, ModAttachments.SUB_CD);
        decay(player, ModAttachments.SUB_SHIELD);
    }

    /** 0.05秒ぶん減衰させ、0未満にはしない */
    private static void decay(ServerPlayer player, Supplier<AttachmentType<Float>> type) {
        float value = player.getData(type.get());
        if (value > 0) player.setData(type.get(), Math.max(0, value - 0.05f));
    }

    /** ボスバー進捗 + 真ボス/終焉の座の弾幕 + 倒れたボスの掃除 */
    private static void tickBossBars(MinecraftServer server) {
        var dead = new ArrayList<Monster>();
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
        tickAutoBolt(player);
        tickAuraBoons(player, level, pos);
        tickMissiles(player, level, pos);
        tickFamiliar(player, level, pos);
        tickNova(player, level, pos);
    }

    /** 魔術武器 (ロッド・ワンド) の自動魔弾 (1.5秒ごと)。弾数ボーナスで多重化 */
    private static void tickAutoBolt(ServerPlayer player) {
        if (tickCounter % 30 != 0) return;
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

    /** 回転刃・聖域の常時オーラ */
    private static void tickAuraBoons(ServerPlayer player, ServerLevel level, Vec3 pos) {
        if (RunManager.boonLv(player, "orbit") > 0) {
            WeaponArts.pulse(level, player, pos, 3.5, 0.8, false, ParticleTypes.SWEEP_ATTACK);
        }
        if (RunManager.boonLv(player, "aura") > 0) {
            WeaponArts.pulse(level, player, pos, 5, 0.5, false, ParticleTypes.FLAME);
        }
    }

    /** 分裂の刻印・弓の追加弾: 発射弾数 +1/Lv */
    private static void tickMissiles(ServerPlayer player, ServerLevel level, Vec3 pos) {
        if (RunManager.boonLv(player, "missile") <= 0) return;
        int shots = 1 + RunManager.boonLv(player, "proj")
                + StatCalc.wbonusOf(player).projectiles()
                + (int) (StatCalc.affixOf(player, "projectiles") + StatCalc.extraStat(player, "projectiles"));
        for (int i = 0; i < shots; i++) {
            var target = nearestEnemy(level, pos, 16);
            if (target == null) continue;
            WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                    (float) (StatCalc.attackDamageOf(player) * 0.7));
            level.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + 1, target.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
        }
    }

    /** 影の眷属: 3秒ごとに使役が援護 */
    private static void tickFamiliar(ServerPlayer player, ServerLevel level, Vec3 pos) {
        if (RunManager.boonLv(player, "familiar") <= 0 || tickCounter % 60 != 0) return;
        var target = nearestEnemy(level, pos, 12);
        if (target == null) return;
        WeaponArts.artHurt(level, target, level.damageSources().playerAttack(player),
                (float) (StatCalc.attackDamageOf(player) * 0.5));
        level.sendParticles(ParticleTypes.SOUL,
                target.getX(), target.getY() + 1, target.getZ(), 10, 0.4, 0.6, 0.4, 0.1);
    }

    /** 氷霜新星: 6秒ごとに凍結波 */
    private static void tickNova(ServerPlayer player, ServerLevel level, Vec3 pos) {
        if (RunManager.boonLv(player, "nova") <= 0 || tickCounter % 120 != 0) return;
        WeaponArts.pulse(level, player, pos, 6, 0.4, false, ParticleTypes.SNOWFLAKE);
        for (var e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos.x - 6, pos.y - 4, pos.z - 6, pos.x + 6, pos.y + 4, pos.z + 6),
                e -> e.isAlive() && !(e instanceof ServerPlayer))) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
        }
    }

    /** 指定位置周辺でもっとも近い敵 (プレイヤー以外) */
    private static LivingEntity nearestEnemy(ServerLevel level, Vec3 pos, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos.x - radius, pos.y - 6, pos.z - radius,
                        pos.x + radius, pos.y + 6, pos.z + radius),
                e -> e.isAlive() && !(e instanceof ServerPlayer)).stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                .orElse(null);
    }

    /** ステータス表示 (RunManager.statusLines の単一出力源。GUI と内容を共有する) */
    public static void showStatus(ServerPlayer player) {
        for (String line : RunManager.statusLines(player)) {
            player.sendSystemMessage(Component.literal(line));
        }
    }
}
