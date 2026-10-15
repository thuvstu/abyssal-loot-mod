package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 層進行・ウェーブ・ボス・職業適用.
 * 原作: FINAL_FLOOR=25, 5層ごとボス, TRUE_FLOOR=26, PINNACLE_FLOOR=27.
 * 同ワールドにアリーナを展開する方式 (Dimensionは後続で追加).
 */
public final class RunManager {
    private RunManager() {}

    /** 層主ボス -> ボスバー */
    public static final java.util.Map<net.minecraft.world.entity.monster.Monster, ServerBossEvent> BOSS_BARS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /** アリーナ半径・塀高は {@link ArenaManager} に移動 */

    /** 原作 DIFFICULTIES のHP倍率 (normal/hard/hell/inferno/omega) */
    public static double diffHpMult(ServerPlayer player) {
        return GameData.difficultyById(player.getData(ModAttachments.DIFF.get())).hpMul();
    }

    /** 原作 DIFFICULTIES の攻撃倍率 */
    public static double diffDmgMult(ServerPlayer player) {
        return GameData.difficultyById(player.getData(ModAttachments.DIFF.get())).dmgMul();
    }

    /** 原作 DIFFICULTIES の魂倍率 */
    public static double diffSoulMult(ServerPlayer player) {
        return GameData.difficultyById(player.getData(ModAttachments.DIFF.get())).soulMul();
    }

    public static boolean isBossrush(ServerPlayer player) {
        return "bossrush".equals(player.getData(ModAttachments.MODE.get()));
    }

    public static boolean isCovenant(ServerPlayer player) {
        return "covenant".equals(player.getData(ModAttachments.MODE.get()));
    }

    public static boolean isDaily(ServerPlayer player) {
        return "daily".equals(player.getData(ModAttachments.MODE.get()));
    }

    /** 日替わりプリセット (原作 getDailyRun と同じくUTC日で7種ローテ) */
    public static String dailyPreset() {
        long day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        return GameData.dailyModForDay(day).id();
    }

    /** 現在の日替わり効果 (dailyモードでない時は null) */
    public static GameData.DailyMod dailyMod(ServerPlayer player) {
        if (!isDaily(player)) return null;
        return dailyMod();
    }

    /** 今日の日替わり効果 (モード不問) */
    public static GameData.DailyMod dailyMod() {
        long day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        return GameData.dailyModForDay(day);
    }

    public static double dailyHpMult() {
        return dailyMod().enemyHp();
    }

    public static double dailyDmgMult() {
        return dailyMod().enemyDmg();
    }

    public static Set<String> sigilsOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.SIGILS.get());
        if (raw == null || raw.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.split(",")));
    }

    public static void setSigils(ServerPlayer player, Set<String> sigils) {
        player.setData(ModAttachments.SIGILS.get(), String.join(",", sigils));
    }

    /* ---------------- 回復・ゲージ・報酬 (呪印の効果を一箇所で適用) ---------------- */

    /**
     * 戦闘回復・回復アイテム・休息・層移動の回復. 飢餓の呪印で回復量×0.58 (原作 healPlayer).
     * 初期HP・蘇生HPは対象外なので、この関数を通さない.
     */
    public static void heal(ServerPlayer player, double amount) {
        if (amount <= 0 || !player.isAlive()) return;
        double rate = GameData.trialRules(sigilsOf(player)).healing();
        player.heal((float) (amount * rate));
    }

    /**
     * 奥義ゲージの獲得 (原作 gainEnergy / weaponArtGauge). 鈍痛の呪印で×0.70.
     * 魂の報酬倍率(呪印・難易度など)は関係しない.
     */
    public static void addGauge(ServerPlayer player, double raw) {
        if (raw <= 0 || !player.isAlive()) return;
        double rate = GameData.trialRules(sigilsOf(player)).energy()
                * (1 + boonLv(player, "energy") * 0.35)
                * (1 + StatCalc.wbonusOf(player).energyPct() / 100.0)
                * (1 + (StatCalc.affixOf(player, "energyPct") + StatCalc.extraStat(player, "energyPct")) / 100.0);
        // 呪い (xpPct): 成長阻害でゲージ増加が鈍る (呪喰らいで無効化)
        var held = player.getMainHandItem();
        if (GearData.curseActive(player, held) && "xpPct".equals(GearData.curseOf(held))) {
            rate *= 1 + GearData.curseValueOf(held) / 100.0;
        }
        // fate「時の歪み」: 魂ゲージ2倍 (原作の経験値2倍に対応)
        if (hasFate(player, "timewarp")) rate *= 2;
        // 弾幕の獄: ゲージ増加1.5倍 (被弾軽減と引き換えの乱戦モード)
        if ("danmaku".equals(player.getData(ModAttachments.MODE.get()))) rate *= 1.5;
        float cur = player.getData(ModAttachments.SOUL.get());
        float next = (float) Math.min(100.0, cur + raw * rate);
        player.setData(ModAttachments.SOUL.get(), next);
        if (cur < 100f && next >= 100f) {
            player.displayClientMessage(Component.literal("§d◆ 魂ゲージMAX！右クリックで武器奥義！"), true);
            // 恩寵「絶対領域」: ゲージ100%で3秒の無敵バリア
            if (boonLv(player, "absolute_domain") > 0) {
                player.setData(ModAttachments.IFRAME.get(), 3.0f);
                player.displayClientMessage(Component.literal("§b◆ 絶対領域展開！"), false);
            }
        }
    }

    /** 魂の報酬倍率 (原作 calculateRunReward: モード×難易度×呪印×日替わり×異変) */
    public static double rewardMult(ServerPlayer player) {
        double m = GameData.soulMult(sigilsOf(player)) * diffSoulMult(player)
                * GameData.modeSoulMult(player.getData(ModAttachments.MODE.get()));
        if (isDaily(player)) {
            var dm = dailyMod();
            if (dm != null) m *= dm.soul();
        }
        var mut = currentMutation(player);
        if (mut != null) m *= mut.gold();
        return m;
    }

    /** 素材個数: 報酬倍率と幸運(+4%/点)を掛け、端数は確率丸め (1個でも無駄にならない) */
    public static int materialCount(ServerPlayer player, double base) {
        int fortune = (int) StatCalc.attrEff(player, 4);
        var rand = player.getRandom();
        double amount = base * rewardMult(player);
        // 遺物「盗賊の金貨」: 素材2倍 / 技能「強欲の契」: +60% / 恩寵「強欲の刻印」: +55%/Lv
        if (relicFlagsOf(player).contains("coin")) amount *= 2;
        if (passivesOf(player).contains("greedy")) amount *= 1.6;
        if (boonLv(player, "greed") > 0) amount *= 1 + 0.55 * boonLv(player, "greed");
        // fate「呪いの霧」: 戦利品2倍
        if (hasFate(player, "mist")) amount *= 2;
        // 祭壇「黄金」: 素材+150%
        if ("gold".equals(activeShrine(player))) amount *= 2.5;
        return GameData.materialAmount(amount, fortune, rand::nextDouble);
    }

    /** 素材アイテムの所持数 (古代石・混沌・虚空) */
    public static int countMats(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** 素材をn個消費 (不足ならfalse) */
    public static boolean takeMats(ServerPlayer player, net.minecraft.world.item.Item item, int n) {
        if (countMats(player, item) < n) return false;
        var inv = player.getInventory();
        int left = n;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            var s = inv.getItem(i);
            if (s.is(item)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return true;
    }










    public static java.util.Map<String, Integer> boonsOf(ServerPlayer player) {
        var map = new java.util.HashMap<String, Integer>();
        String raw = player.getData(ModAttachments.BOONS.get());
        if (raw == null || raw.isBlank()) return map;
        for (String e : raw.split(",")) {
            String[] kv = e.split(":");
            if (kv.length == 2) {
                try {
                    map.put(kv[0], map.getOrDefault(kv[0], 0) + Math.max(0, Integer.parseInt(kv[1])));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public static void addBoon(ServerPlayer player, String id) {
        var map = boonsOf(player);
        map.put(id, map.getOrDefault(id, 0) + 1);
        var sb = new StringBuilder();
        for (var e : map.entrySet()) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        player.setData(ModAttachments.BOONS.get(), sb.toString());
    }

    public static int boonLv(ServerPlayer player, String id) {
        return boonsOf(player).getOrDefault(id, 0);
    }







    /** 消耗品の効果説明 (lore 用) */
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
            default -> kind;
        };
    }

    /** 消耗品の使用。効果を適用したら true (1個消費) */
    public static boolean useConsumable(ServerPlayer player, String kind) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return false;
        Vec3 origin = player.position();
        switch (kind) {
            case "heal_30" -> heal(player, player.getMaxHealth() * 0.3);
            case "heal_70" -> heal(player, player.getMaxHealth() * 0.7);
            case "heal_100" -> heal(player, player.getMaxHealth());
            case "gauge_40" -> addGauge(player, 40);
            case "gauge_100" -> addGauge(player, 100);
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
                if (rd == null) rd = net.minecraft.world.level.storage.LevelData.RespawnData.DEFAULT;
                net.minecraft.core.GlobalPos gp = rd.globalPos();
                ServerLevel dest = player.level() instanceof ServerLevel sl
                        ? sl.getServer().getLevel(gp.dimension()) : level;
                if (dest == null) dest = level;
                BlockPos s = gp.pos();
                int sy = ArenaManager.safeSpawnY(dest, s.getX(), s.getZ(), s.getY() + 1);
                player.teleportTo(dest, s.getX() + 0.5, sy, s.getZ() + 0.5,
                        java.util.Set.<net.minecraft.world.entity.Relative>of(), rd.yaw(), rd.pitch(), true);
            }
            case "cure" -> {
                player.removeAllEffects();
                player.displayClientMessage(Component.literal("§a浄化した"), true);
            }
            case "magnet" -> {
                for (var it : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
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
                for (var e : BOSS_BARS.entrySet()) {
                    if (e.getKey().isAlive()) {
                        player.sendSystemMessage(Component.literal("§c層主 HP "
                                + Math.max(0, Math.round(e.getKey().getHealth())) + "/"
                                + Math.round(e.getKey().getMaxHealth())));
                    }
                }
            }
            case "feast" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 0));
                heal(player, 20);
            }
            case "smoke" -> {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 0));
                level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + 1, origin.z,
                        40, 1.5, 1.5, 1.5, 0.1);
            }
            default -> {
                return false;
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }





    /** 出撃中か (呪印・モード・難易度の固定・原作 /api/expeditions の条件固定に相当) */
    public static boolean isRunLocked(ServerPlayer player) {
        return player.getData(ModAttachments.RUN_LOCK.get()) != 0;
    }

    /* ---------------- 遺物・技能・fate ---------------- */

    public static java.util.List<String> relicsOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.RELICS.get());
        if (raw == null || raw.isBlank()) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public static java.util.Set<String> relicFlagsOf(ServerPlayer player) {
        return GameData.relicFlags(relicsOf(player));
    }

    /** 遺物の取得 (原作: ラン中最大2つ) */
    public static boolean addRelic(ServerPlayer player, String id) {
        var owned = relicsOf(player);
        if (owned.contains(id) || owned.size() >= 2) return false;
        if (GameData.relicById(id) == null) return false;
        owned.add(id);
        player.setData(ModAttachments.RELICS.get(), String.join(",", owned));
        return true;
    }

    public static java.util.List<String> passivesOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.PASSIVES.get());
        if (raw == null || raw.isBlank()) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public static java.util.Set<String> passiveFlagsOf(ServerPlayer player) {
        return GameData.passiveFlags(passivesOf(player));
    }

    public static boolean addPassive(ServerPlayer player, String id) {
        var owned = passivesOf(player);
        if (owned.contains(id)) return false;
        if (GameData.passiveById(id) == null) return false;
        owned.add(id);
        player.setData(ModAttachments.PASSIVES.get(), String.join(",", owned));
        // ステータス系は即時反映
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        StatCalc.applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()));
        return true;
    }

    /** fate効果の有無 (呪いの霧 mist / 時の歪み timewarp) */
    public static boolean hasFate(ServerPlayer player, String fate) {
        long now = player.level().getGameTime();
        return switch (fate) {
            case "mist" -> now < player.getData(ModAttachments.FATE_MIST.get());
            case "timewarp" -> now < player.getData(ModAttachments.FATE_TIME.get());
            default -> false;
        };
    }

    public static void setFate(ServerPlayer player, String fate, int ticks) {
        long until = player.level().getGameTime() + ticks;
        if ("mist".equals(fate)) player.setData(ModAttachments.FATE_MIST.get(), until);
        else if ("timewarp".equals(fate)) player.setData(ModAttachments.FATE_TIME.get(), until);
    }

    /**
     * 運命の祭壇の効果 (原作 shrine_fate の5種)。
     * rage=与ダメ+60%・被ダメ+20% / gold=素材+150%・最大HP-20 / haste=攻速+40%・移動+30% /
     * armor=被ダメ-50%・回避不可 / scourge=毎秒2損耗・運+100。
     */
    public static void setShrine(ServerPlayer player, String fate, int ticks) {
        player.setData(ModAttachments.SHRINE_FATE.get(), fate);
        player.setData(ModAttachments.SHRINE_UNTIL.get(), player.level().getGameTime() + ticks);
        if ("haste".equals(fate)) {
            // 移動+30% ≒ SPEED II。攻速+40%は属性に直接載せ、終了時に戻す
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, false, false));
            var atkSpd = player.getAttribute(Attributes.ATTACK_SPEED);
            if (atkSpd != null) {
                atkSpd.setBaseValue(atkSpd.getBaseValue() * 1.4);
                CombatHandler.after(ticks, () -> {
                    var a2 = player.getAttribute(Attributes.ATTACK_SPEED);
                    if (player.isAlive() && a2 != null) a2.setBaseValue(a2.getBaseValue() / 1.4);
                });
            }
        }
        if ("armor".equals(fate)) {
            // 回避不可: CTを満たす
            player.setData(ModAttachments.DASH_CD.get(),
                    Math.max(player.getData(ModAttachments.DASH_CD.get()), ticks / 20f));
        }
    }

    /** 有効な祭壇効果ID (なければ null) */
    public static String activeShrine(ServerPlayer player) {
        String f = player.getData(ModAttachments.SHRINE_FATE.get());
        if (f == null || f.isBlank()) return null;
        return player.level().getGameTime() < player.getData(ModAttachments.SHRINE_UNTIL.get()) ? f : null;
    }


    /** 出撃: 現在地の近くに試練アリーナを整地し第1ウェーブを出す */
    public static void startRun(ServerPlayer player) {
        int floor = 1;
        String mode = player.getData(ModAttachments.MODE.get());
        player.setData(ModAttachments.FLOOR.get(), floor);
        player.setData(ModAttachments.KILLS.get(), 0);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.SOUL.get(), 0f);
        player.setData(ModAttachments.COMBO.get(), 0);
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.BOSSES.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        player.setData(ModAttachments.SHOP.get(), "");
        player.setData(ModAttachments.TRAPS.get(), "");
        player.setData(ModAttachments.DESTRUCT.get(), "");
        player.setData(ModAttachments.RELICS.get(), "");
        player.setData(ModAttachments.PASSIVES.get(), "");
        player.setData(ModAttachments.RELIC_OFFER.get(), "");
        player.setData(ModAttachments.PASSIVE_OFFER.get(), "");
        player.setData(ModAttachments.FATE_MIST.get(), 0L);
        player.setData(ModAttachments.FATE_TIME.get(), 0L);
        player.setData(ModAttachments.ART_USES.get(), 0);
        player.setData(ModAttachments.PERFECT.get(), 0);
        player.setData(ModAttachments.EVENT_TOTAL.get(), 0);
        player.setData(ModAttachments.MIRROR_USED.get(), 0);
        player.setData(ModAttachments.HARVEST.get(), 0);
        player.setData(ModAttachments.BEST_COMBO.get(), 0);
        player.setData(ModAttachments.DMG_DEALT.get(), 0);
        player.setData(ModAttachments.SCYTHE.get(), 0);
        player.setData(ModAttachments.GCNT.get(), 0);
        player.setData(ModAttachments.PARRY_TICK.get(), -1000L);
        // 終焉の契約モード: 全8呪印を強制付与 (原作 omega)
        if ("omega".equals(mode)) {
            var all = new HashSet<String>();
            for (GameData.Sigil s : GameData.Sigil.values()) all.add(s.id);
            setSigils(player, all);
            player.sendSystemMessage(Component.literal("§d終焉の契約: 全8呪印を強制付与"));
        }
        // 出撃中は呪印・モード・難易度を固定 (原作 /api/expeditions の条件固定に対応)
        player.setData(ModAttachments.RUN_LOCK.get(), 1);
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int level = "bossrush".equals(mode) ? 10 : player.getData(ModAttachments.LEVEL.get());
        // 継承装備があれば受け取る
        var heirloom = player.getData(ModAttachments.HEIRLOOM.get());
        if (heirloom != null && !heirloom.isEmpty()) {
            player.getInventory().add(heirloom.copy());
            player.sendSystemMessage(Component.literal("§6血脈の装備を受け継いだ: " + heirloom.getHoverName().getString()));
            player.setData(ModAttachments.HEIRLOOM.get(), net.minecraft.world.item.ItemStack.EMPTY.copy());
        }
        StatCalc.applyClass(player, cls, level);
        ArenaManager.buildArena(player);
        spawnWave(player, floor);
        double soul = rewardMult(player);
        player.displayClientMessage(Component.literal(
                "§5深淵潜行開始 — 第1層 [" + mode + "/" + player.getData(ModAttachments.DIFF.get())
                        + "] 魂倍率×" + String.format("%.2f", soul)), false);
        // 初出撃チュートリアル (2回目以降は出さない。/abyss help で再読可)
        if (player.getData(ModAttachments.TUTOR.get()) == 0) {
            player.setData(ModAttachments.TUTOR.get(), 1);
            player.sendSystemMessage(Component.literal("§6━━━ 初潜行ガイド ━━━"));
            player.sendSystemMessage(Component.literal("§f1. 左クリックで攻撃。敵を倒して魂ゲージを溜めよう"));
            player.sendSystemMessage(Component.literal("§f2. ゲージMAXで §bR §f(武器奥義) が撃てる。§dV §fは職業の必殺技"));
            player.sendSystemMessage(Component.literal("§f3. §aX §fは回避ダッシュ。敵の攻撃に合わせると「見切り」で魂+14"));
            player.sendSystemMessage(Component.literal("§f4. 敵を全滅させると選択肢が出る → 転移門へ → §b/abyss next §fで次層"));
            player.sendSystemMessage(Component.literal("§f5. レベルアップで祝福3択・属性1点 (8属性・適性★1.5倍・節目有)。5層ごとに層主が待つ"));
            player.sendSystemMessage(Component.literal("§7詳しくは /abyss help ／設定は /abyss menu"));
        }
    }

    /** 踏破勝利: 報酬 + 継承の案内 */
    /** 原作の魂の内訳をチャットに出す (勝利時の要約). 報酬の欠片とは別の換算値 */
    private static void sendSoulSummary(ServerPlayer player, boolean trueCleared) {
        var sigils = sigilsOf(player);
        int score = calcScore(player, true);
        var r = GameData.runReward(player.getData(ModAttachments.FLOOR.get()),
                player.getData(ModAttachments.KILLS.get()),
                player.getData(ModAttachments.LEVEL.get()), score, true, trueCleared,
                1.0, diffSoulMult(player), GameData.soulMult(sigils));
        player.sendSystemMessage(Component.literal(String.format(
                "§d原作換算の魂: 基礎%d (スコア%d) → 難度・呪印込み%d (呪印ボーナス+%d)",
                r.baseSouls(), score, r.beforeTrials(), r.trialBonus())));
    }

    /** 原作 summarize の写し (FLOOR・日替わり・モード・難易度の倍率込み) */
    public static int calcScore(ServerPlayer player, boolean victory) {
        String mode = player.getData(ModAttachments.MODE.get());
        double dailyRate = 1.0;
        if (isDaily(player)) {
            var dm = dailyMod();
            if (dm != null) dailyRate = dm.score();
        }
        int base = GameData.baseScore(
                player.getData(ModAttachments.FLOOR.get()),
                player.getData(ModAttachments.KILLS.get()),
                player.getData(ModAttachments.LEVEL.get()),
                countShards(player),
                player.getData(ModAttachments.DMG_DEALT.get()),
                victory,
                player.getData(ModAttachments.BEST_COMBO.get()),
                player.getData(ModAttachments.PERFECT.get()),
                "danmaku".equals(mode));
        return GameData.finalScore(base, dailyRate, mode, player.getData(ModAttachments.DIFF.get()));
    }

    public static void victory(ServerPlayer player) {
        if (isDaily(player)) {
            player.sendSystemMessage(Component.literal("§6★ 日替わり深淵【" + dailyPreset() + "】踏破！ ★"));
        } else {
            player.sendSystemMessage(Component.literal("§6★ 深淵踏破！虚無神を打ち果たした！ ★"));
        }
        player.sendSystemMessage(Component.literal("§e報酬: 魂の欠片×16 / Abyss武器 / 手持ち武器1点を /abyss keep で継承可能"));
        player.sendSystemMessage(Component.literal("§5真の深淵が開いた… /abyss true で《ニヒル・ゼロ》に挑め"));
        int shards = materialCount(player, 16);
        player.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.SOUL_SHARD.get(), shards));
        player.sendSystemMessage(Component.literal("§e魂の欠片 ×" + shards + " (呪印・難易度・幸運の倍率込み)"));
        var styles = GameData.WeaponStyle.values();
        player.getInventory().add(GearFactory.makeWeapon(styles[player.getRandom().nextInt(styles.length)],
                player.getRandom(), StatCalc.luckOf(player), 25, 0));
        sendSoulSummary(player, false);
        CombatHandler.grantAch(player, "full-clear", "深淵を終わらせた者", 16);
        if (isCovenant(player)) {
            var a = GameData.achievementById("covenant-kept");
            CombatHandler.grantAch(player, "covenant-kept", a.title(), GameData.shardsForReward(a.reward()));
        }
        // 出撃終了: 条件固定を解除
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        ArenaManager.clearArenaBarriers(player);
    }

    /**
     * リタイア: 出撃を中断し、アリーナのバリアを除去して残存MOBを消す。
     * 装備・欠片は保持される (原作の帰還に相当)。
     */
    public static void retire(ServerPlayer player) {
        ArenaManager.clearArenaBarriers(player);
        if (player.level() instanceof ServerLevel level) {
            for (Mob m : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(64), e -> e.isAlive())) {
                boolean abyss = RunManager.BOSS_BARS.containsKey(m);
                if (!abyss) {
                    for (String t : m.getTags()) {
                        if (t.startsWith("abyss_")) {
                            abyss = true;
                            break;
                        }
                    }
                }
                if (abyss) m.discard();
            }
            for (var e : BOSS_BARS.entrySet()) {
                e.getValue().removePlayer(player);
            }
        }
        player.setData(ModAttachments.WAVE_TOTAL.get(), 0);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        player.sendSystemMessage(Component.literal("§7リタイアした (装備・欠片は保持)。再出撃は /abyss start"));
    }

    public static boolean isTrueFloor(ServerPlayer player) {
        return player.getData(ModAttachments.FLOOR.get()) == 26;
    }

    /**
     * 層の異変を抽選 (原作 rollFloorMutation). 異変が出なかった層では必ず解除する.
     * (旧版は解除しなかったため、前の層の異変が次の層まで残っていた)
     */
    public static String rollMutation(ServerPlayer player, int floor) {
        var m = GameData.rollMutation(player.getRandom()::nextDouble, floor);
        String id = m == null ? "" : m.id();
        player.setData(ModAttachments.MUTATION.get(), id);
        if (m != null) {
            player.sendSystemMessage(Component.literal("§d《異変 — " + m.name() + "》" + m.desc()));
        }
        return id;
    }

    /** 現在層の異変 (なければ null) */
    /** 層のダンジョン特性を抽選 (65%で1つ)。異変とは別枠 */
    public static String rollDungeon(ServerPlayer player) {
        String id = "";
        if (player.getRandom().nextDouble() < 0.65) {
            var pool = GameData.DUNGEON_TRAITS;
            var t = pool.get(player.getRandom().nextInt(pool.size()));
            id = t.id();
            player.sendSystemMessage(Component.literal("§5《地相 — " + t.name() + "》" + t.desc()));
        }
        player.setData(ModAttachments.DUNGEON.get(), id);
        return id;
    }

    /** 現在層のダンジョン特性 (なければ null) */
    public static GameData.DungeonTrait currentDungeon(ServerPlayer player) {
        String id = player.getData(ModAttachments.DUNGEON.get());
        return id == null || id.isEmpty() ? null : GameData.dungeonById(id);
    }

    public static GameData.Mutation currentMutation(ServerPlayer player) {
        String id = player.getData(ModAttachments.MUTATION.get());
        return id == null || id.isEmpty() ? null : GameData.mutationById(id);
    }

    public static String mutationName(String id) {
        var m = GameData.mutationById(id);
        return m == null ? "静穏" : m.name();
    }

    public static String mutationDesc(String id) {
        var m = GameData.mutationById(id);
        return m == null ? "" : m.desc();
    }

    public static boolean hasMutation(ServerPlayer player, String id) {
        return id.equals(player.getData(ModAttachments.MUTATION.get()));
    }

    /** 真・最終決戦 (原作TRUE_FLOOR=26) */
    public static void startTrue(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        player.setData(ModAttachments.FLOOR.get(), 26);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 1);
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        ArenaManager.buildArena(player);
        double hpMult = GameData.enemyHpMult(sigilsOf(player)) * diffHpMult(player);
        double dmgMult = GameData.enemyDmgMult(sigilsOf(player)) * diffDmgMult(player);
        Monster boss = ModEntities.ZERO.get().create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("true boss create failed");
        BlockPos bp = player.blockPosition();
        int zy = ArenaManager.safeSpawnY(level, bp.getX(), bp.getZ() - 10, bp.getY() + 1);
        boss.setPos(bp.getX() + 0.5, zy, bp.getZ() - 10 + 0.5);
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(4000 * hpMult);
            boss.setHealth(boss.getMaxHealth());
        }
        var atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(30 * dmgMult);
        var spd0 = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd0 != null) spd0.setBaseValue(Enemies.SPEED_PARITY * 118 / 100.0);
        boss.setCustomName(Component.literal("§f原初虚無 《ニヒル・ゼロ》"));
        boss.setCustomNameVisible(true);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        var bar = new ServerBossEvent(Component.literal("§f原初虚無 《ニヒル・ゼロ》"),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        bar.setProgress(1f);
        BOSS_BARS.put(boss, bar);
        player.sendSystemMessage(Component.literal("§f◆ 真・最終決戦 — 3フェーズの弾幕を耐え抜け！"));
    }

    /** 真ボスのフェーズ弾幕 (原作 TRUE_BOSS_PHASES の3段階。5秒ごとに残HP割合で変化) */
    public static void tickTrueBoss(ServerLevel level, Monster boss) {
        String name = boss.getCustomName() != null ? boss.getCustomName().getString() : "";
        if (!name.contains("ニヒル")) return;
        double ratio = boss.getHealth() / Math.max(1, boss.getMaxHealth());
        var ph = GameData.truePhaseFor(ratio);
        var at = boss.position();
        // フェーズ名をボスバーに反映
        for (var e : BOSS_BARS.entrySet()) {
            if (e.getKey().getUUID().equals(boss.getUUID())) {
                e.getValue().setName(Component.literal("§f原初虚無 《ニヒル・ゼロ》 — " + ph.label()));
            }
        }
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
        for (var e : BOSS_BARS.entrySet()) {
            if (e.getKey().getUUID().equals(boss.getUUID())) {
                e.getValue().setName(Component.literal("§c終焉神 《タルタロス・オメガ》 — " + ph.label()));
            }
        }
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
                new net.minecraft.world.phys.AABB(boss.blockPosition()).inflate(24),
                m -> !BOSS_BARS.containsKey(m)).size();
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
        // 神話武器確定
        var styles = GameData.WeaponStyle.values();
        var style = styles[player.getRandom().nextInt(styles.length)];
        var stack = GearFactory.makeWeapon(style, player.getRandom());
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putString("abyss_rarity", "mythic");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§d神話の" + style.weaponName));
                    player.getInventory().add(stack);
        player.getInventory().add(GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 26, 130));
        sendSoulSummary(player, true);
        CombatHandler.grantAch(player, "true-clear", "虚無を_zeroに還した者", 32);
        player.sendSystemMessage(Component.literal("§c終焉の座が開いた… /abyss pinnacle で《タルタロス・オメガ》に挑め"));
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        ArenaManager.clearArenaBarriers(player);
    }

    /** 終焉の座 (原作PINNACLE_FLOOR=27。単層決戦) */
    public static void startPinnacle(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        player.setData(ModAttachments.FLOOR.get(), GameData.PINNACLE_FLOOR);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 1);
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        ArenaManager.buildArena(player);
        var def = GameData.bossForFloor(GameData.PINNACLE_FLOOR);
        double hpMult = GameData.enemyHpMult(sigilsOf(player)) * diffHpMult(player);
        double dmgMult = GameData.enemyDmgMult(sigilsOf(player)) * diffDmgMult(player);
        Monster boss = ModEntities.OMEGA.get().create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("pinnacle boss create failed");
        BlockPos bp2 = player.blockPosition();
        int oy = ArenaManager.safeSpawnY(level, bp2.getX(), bp2.getZ() - 10, bp2.getY() + 1);
        boss.setPos(bp2.getX() + 0.5, oy, bp2.getZ() - 10 + 0.5);
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(1000000, def.hp() * hpMult / 20));
            boss.setHealth(boss.getMaxHealth());
        }
        var atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(def.dmg() * dmgMult / 4);
        var spdP = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spdP != null) spdP.setBaseValue(Enemies.SPEED_PARITY * def.speed() / 100.0);
        boss.setCustomName(Component.literal("§c" + def.name()));
        boss.setCustomNameVisible(true);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        var bar = new ServerBossEvent(Component.literal("§c" + def.name()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        bar.addPlayer(player);
        bar.setProgress(1f);
        BOSS_BARS.put(boss, bar);
        player.sendSystemMessage(Component.literal("§c◆ 終焉の座 — 4フェーズの審判を耐え抜け！ (MC向けにHP・攻撃を圧縮)"));
    }

    /** 終焉の座の撃破 */
    public static void pinnacleVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6★ 終焉を穿った！タルタロス・オメガ討伐！ ★"));
        var styles = GameData.WeaponStyle.values();
        for (int i = 0; i < 3; i++) {
            var style = styles[player.getRandom().nextInt(styles.length)];
            var stack = GearFactory.makeWeapon(style, player.getRandom());
            var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            tag.putString("abyss_rarity", "mythic");
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("§d神話の" + style.weaponName));
            player.getInventory().add(stack);
        }
        var setDrop = GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 27, 130);
        var setTag = setDrop.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        setTag.putString("abyss_rarity", "mythic");
        setDrop.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(setTag));
        player.getInventory().add(setDrop);
        var a = GameData.achievementById("pinnacle-god");
        CombatHandler.grantAch(player, "pinnacle-god", a.title(), GameData.shardsForReward(a.reward()));
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        ArenaManager.clearArenaBarriers(player);
    }

    /** 超越者 (原作 b_ascension・28層。無限深淵で到達する裏決戦) */
    public static void ascensionVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§e★ 超越者を越えた！深淵の果てに名を刻んだ！ ★"));
        var styles = GameData.WeaponStyle.values();
        for (int i = 0; i < 2; i++) {
            var style = styles[player.getRandom().nextInt(styles.length)];
            var stack = GearFactory.makeWeapon(style, player.getRandom());
            var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            tag.putString("abyss_rarity", "mythic");
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("§d神話の" + style.weaponName));
            player.getInventory().add(stack);
        }
        player.getInventory().add(GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), 28, 130));
        int shards = materialCount(player, 32);
        if (shards > 0) {
            player.getInventory().add(new net.minecraft.world.item.ItemStack(
                    ModItems.SOUL_SHARD.get(), shards));
        }
        sendSoulSummary(player, true);
        ArenaManager.clearArenaBarriers(player);
    }

    public static void nextFloor(ServerPlayer player) {
        String mode = player.getData(ModAttachments.MODE.get());
        int cur = player.getData(ModAttachments.FLOOR.get());
        if (cur >= 100) {
            player.sendSystemMessage(Component.literal("§7ここが最深層だ (真ボスを討て)"));
            return;
        }
        int floor = cur + 1;
        // 踏破判定 (原作FINAL_FLOOR=25 / 巡礼は8層主 / 終焉の座は27単層)
        if ("bossrush".equals(mode) && floor > 8) {
            var a = GameData.achievementById("rush-master");
            CombatHandler.grantAch(player, "rush-master", a.title(), GameData.shardsForReward(a.reward()));
            victory(player);
            return;
        }
        if ("pinnacle".equals(mode) && floor > GameData.PINNACLE_FLOOR) {
            pinnacleVictory(player);
            return;
        }
        if (!"endless".equals(mode) && !"bossrush".equals(mode) && !"pinnacle".equals(mode)
                && floor > GameData.FINAL_FLOOR) {
            victory(player);
            return;
        }
        // 層到達の実績
        if (floor == 10) {
            if (isDaily(player)) {
                var a = GameData.achievementById("daily-depth");
                CombatHandler.grantAch(player, "daily-depth", a.title(), GameData.shardsForReward(a.reward()));
            }
            if (sigilsOf(player).size() >= 8) {
                var a = GameData.achievementById("eight-sigils");
                CombatHandler.grantAch(player, "eight-sigils", a.title(), GameData.shardsForReward(a.reward()));
            }
        }
        if (floor == 15 && "inferno".equals(player.getData(ModAttachments.DIFF.get()))) {
            var a = GameData.achievementById("inferno-slayer");
            CombatHandler.grantAch(player, "inferno-slayer", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (floor == 35 && "endless".equals(mode)) {
            var a = GameData.achievementById("endless-diver");
            CombatHandler.grantAch(player, "endless-diver", a.title(), GameData.shardsForReward(a.reward()));
        }
        player.setData(ModAttachments.FLOOR.get(), floor);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        player.setData(ModAttachments.SHOP.get(), "");
        player.setData(ModAttachments.TRAPS.get(), "");
        player.setData(ModAttachments.DESTRUCT.get(), "");
        // 層移動回復 (飢餓の呪印で減衰). 日替わり「乾ききった迷宮」では回復なし
        boolean noHeal = isDaily(player) && dailyMod() != null && dailyMod().noFloorHeal();
        if (!noHeal) heal(player, player.getMaxHealth() * 0.25);
        else player.sendSystemMessage(Component.literal("§e乾ききった迷宮: 層移動の回復なし"));
        player.teleportTo(player.getX(), player.getY(), player.getZ());
        spawnWave(player, floor);
        var zone = GameData.zoneForFloor(floor);
        if (floor % 5 == 0) {
            player.displayClientMessage(Component.literal("§4◆ 第" + floor + "層 — 層主出現！"), false);
        } else {
            player.displayClientMessage(Component.literal(
                    "§5— 第" + floor + "層 [" + zone.name() + "] —"), false);
        }
    }







    /** ウェーブ湧き: 原作式 waveTotal = 9 + floor*2.4 をMC向けに圧縮. 敵の抽選・能力値は Enemies に委譲 */
    public static void spawnWave(ServerPlayer player, int floor) {
        spawnWave(player, floor, true);
    }

    /** rollFloor=true: 層の開始 (異変を抽選). false: 追加の襲来 (同じ層の異変を維持) */
    public static void spawnWave(ServerPlayer player, int floor, boolean rollFloor) {
        ServerLevel level = (ServerLevel) player.level();
        if (rollFloor) rollMutation(player, floor);
        if (rollFloor) rollDungeon(player);
        var sigils = sigilsOf(player);
        var scale = Enemies.scaleFor(player, floor);
        boolean bossFloor = floor % 5 == 0 || isBossrush(player);
        // 無限深淵の 28 層は超越者 (原作 ASCENSION_FLOOR) の決戦層
        boolean ascensionFloor = "endless".equals(player.getData(ModAttachments.MODE.get()))
                && floor == GameData.ASCENSION_FLOOR;
        double eliteChance = GameData.eliteChance(floor, sigils);
        var rand = player.getRandom();

        var mut = currentMutation(player);
        double spawnMul = mut != null ? mut.spawn() : 1.0;
        var dun = currentDungeon(player);
        if (dun != null) spawnMul *= dun.spawn();
        if (isDaily(player)) {
            var dm = dailyMod();
            if (dm != null) {
                spawnMul *= dm.spawn();
                eliteChance = Math.min(0.9, eliteChance * dm.elite());
            }
        }
        int count = bossFloor ? 4 + floor / 2 : Math.min(24, 5 + (int) (floor * 1.6));
        count = Math.min(40, (int) Math.round(count * spawnMul));
        // 弾幕の獄: 敵の群れが濃くなる (被弾軽減・ゲージ増加と引き換え)
        if ("danmaku".equals(player.getData(ModAttachments.MODE.get()))) {
            count = Math.min(48, (int) Math.round(count * 1.5));
        }
        if (mut != null && mut.eliteRate() != 1.0) {
            eliteChance = Math.min(0.9, eliteChance * mut.eliteRate());
        }
        if (rollFloor && mut != null && mut.darkness()) {
            // 闇の迷宮: 視界を塞ぐ (盲目は長めに付与し、層の間は持続させる)
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 60 * 5, 0, false, false));
        }
        // 区域の告知 (5層ごとに変わる深層帯)
        if (rollFloor) {
            var zone = GameData.zoneForFloor(floor);
            player.sendSystemMessage(Component.literal("§7《区域 — " + zone.name() + "》" + zone.desc()));
            if (zone.emberRain()) {
                player.sendSystemMessage(Component.literal("§c溶鉄の炉: 火の粉が降り注ぐ！"));
            }
        }
        player.setData(ModAttachments.WAVE_TOTAL.get(), count + (bossFloor ? 1 : 0));
        BlockPos center = player.blockPosition();

        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2 * i) / count;
            int sx = center.getX() + (int) (Math.cos(ang) * 14);
            int sz = center.getZ() + (int) (Math.sin(ang) * 14);
            int sy = ArenaManager.safeSpawnY(level, sx, sz, center.getY() + 1);
            var spec = Enemies.pickSpec(floor, i, rand);
            Mob mob = Enemies.create(level, spec);
            mob.setPos(sx + 0.5, sy, sz + 0.5);
            // 門は耐久が高い (原作の門は壊すまで増援し続ける)
            double extra = spec.role() == Enemies.Role.GATE ? Enemies.GATE_HP_MULT : 1.0;
            Enemies.apply(mob, spec, scale, extra);
            if (spec.role() == Enemies.Role.NORMAL && rand.nextDouble() < eliteChance) {
                Enemies.makeElite(mob);
            }
            Enemies.tagByRole(mob, spec);
            mob.setTarget(player);
            level.addFreshEntity(mob);
        }

        if (bossFloor) {
            spawnFloorBoss(player, level, center, floor, scale);
        } else if (ascensionFloor) {
            spawnAscensionBoss(player, level, center, scale);
        } else if (rollFloor && rand.nextDouble() < 0.45) {
            // 層の中盤イベント (原作 RANDOM_EVENTS). ボス層以外で45%
            triggerRandomEvent(player, level, center, floor);
        }
        // 番人 (原作 WARDENS: ボス層でない 3/8 層のミニボス)
        if (rollFloor && !bossFloor && GameData.isWardenFloor(floor)) {
            var w = GameData.WARDENS.get(rand.nextInt(GameData.WARDENS.size()));
            Mob warden = (Mob) Enemies.wardenMob().create(level, EntitySpawnReason.EVENT);
            if (warden != null) {
                int sy = ArenaManager.safeSpawnY(level, center.getX(), center.getZ() + 10, center.getY() + 1);
                warden.setPos(center.getX() + 0.5, sy, center.getZ() + 10.5);
                Enemies.apply(warden, Enemies.byId("golem"), scale, 1.0);
                Enemies.makeWarden(warden, w);
                warden.setTarget(player);
                level.addFreshEntity(warden);
                player.setData(ModAttachments.WAVE_TOTAL.get(),
                        player.getData(ModAttachments.WAVE_TOTAL.get()) + 1);
                player.sendSystemMessage(Component.literal(
                        "§c◆ 番人出現 — " + w.name() + "「" + w.flavor() + "」"));
            }
        }
        ArenaManager.placeHazards(player, level, center, floor);
    }

    /** 層主の出現 (原作 BOSSES の名前・能力で) */
    private static void spawnFloorBoss(ServerPlayer player, ServerLevel level, BlockPos center,
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
            BOSS_BARS.put(monster, bar);
        }
    }

    /** 超越者 (原作 b_ascension・28層。無限深淵で到達する裏決戦) */
    private static void spawnAscensionBoss(ServerPlayer player, ServerLevel level, BlockPos center,
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
            BOSS_BARS.put(monster, bar);
        }
        player.sendSystemMessage(Component.literal("§e◆ 超越者 — 真の深淵と終焉の座を制した者だけが立てる相手！"));
    }



    /* ---------------- 層間イベント ---------------- */

    /** 魂の欠片の所持数 */
    public static int countShards(ServerPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) n += s.getCount();
        }
        return n;
    }

    /** 魂の欠片をn個消費 (不足ならfalse) */
    public static boolean takeShards(ServerPlayer player, int n) {
        if (countShards(player) < n) return false;
        var inv = player.getInventory();
        int left = n;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            var s = inv.getItem(i);
            if (s.is(ModItems.SOUL_SHARD.get())) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return true;
    }

    /** 層クリア時に選択肢を提示 (原作の rest/forge/oath/merchant) */
    public static void offerEvent(ServerPlayer player) {
        if (player.getData(ModAttachments.EVENT_DONE.get()) != 0) return;
        // 商店の品揃えを生成。価格は原作 merchant (power比例) の欠片版: 見本の威力から算出
        var styles = GameData.WeaponStyle.values();
        var rand = player.getRandom();
        int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
        var shop = new java.util.ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            var st = styles[rand.nextInt(styles.length)];
            int price = Math.max(2, (GearData.powerOf(GearFactory.makeWeapon(st, rand, StatCalc.luckOf(player), floor, 0)) + 7) / 15);
            shop.add(st.id + ":" + price);
        }
        player.setData(ModAttachments.SHOP.get(), String.join(",", shop));
        var msg = Component.literal("§e◆ 層間イベント (1つ選択): ");
        msg.append(btn("§a[休息 HP38%+魂25]", "/abyss event rest"));
        msg.append(btn("§5[血の誓約 祝福]", "/abyss event oath"));
        msg.append(btn("§6[商店 欠片で購入]", "/abyss event shop"));
        msg.append(btn("§b[遺された宝箱]", "/abyss event forge"));
        player.sendSystemMessage(msg);
        player.sendSystemMessage(Component.literal(
                "§7所持欠片: " + countShards(player) + "個 §8| §7武器合成は /abyss merge (両手にAbyss武器)"));
    }

    private static Component btn(String label, String cmd) {
        return Component.literal(label + " ").withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))
                .withUnderlined(true));
    }

    public static void doEvent(ServerPlayer player, String kind) {
        if (player.getData(ModAttachments.EVENT_DONE.get()) != 0) {
            player.sendSystemMessage(Component.literal("§cこの層のイベントは使用済み"));
            return;
        }
        switch (kind) {
            case "rest" -> {
                // 原作: 最大HPの38%回復 + 魂ゲージ25
                completeEvent(player);
                heal(player, player.getMaxHealth() * 0.38);
                addGauge(player, 25);
                player.sendSystemMessage(Component.literal("§a休息した。HP38%回復・魂+25。"));
            }
            case "oath" -> {
                // 原作: 最大HPの18%を捧げ、祝福を1つ選ぶ (盟約モードでは宝箱に差し替わる)
                if (isCovenant(player)) {
                    doForgeCrate(player);
                    break;
                }
                completeEvent(player);
                float cost = player.getMaxHealth() * 0.18f;
                if (player.getHealth() > cost + 1) {
                    player.hurtServer((ServerLevel) player.level(),
                            player.level().damageSources().magic(), cost);
                }
                player.sendSystemMessage(Component.literal("§5血の誓約… 最大HP18%を捧げた。祝福を選べ"));
                CombatHandler.offerBoons(player);
            }
            case "shop" -> {
                String raw = player.getData(ModAttachments.SHOP.get());
                if (raw == null || raw.isBlank()) {
                    player.sendSystemMessage(Component.literal("§c商品なし"));
                    return;
                }
                // 商店GUIを直接開く (購入はGUIのボタン→doBuy。チャット購入も可)
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, pl) -> new ShopMenu(id, inv),
                        Component.literal("商店")));
                player.sendSystemMessage(Component.literal(
                        "§6商店を開いた (所持欠片" + countShards(player) + "／チャット購入は /abyss buy 1〜3)"));
            }
            case "forge" -> {
                // 原作「遺された宝箱」: 第(層+1)層相当の装備 (層主の層はボーナス130)
                doForgeCrate(player);
            }
            default -> player.sendSystemMessage(Component.literal("§crest/oath/shop/forge で指定"));
        }
    }

    /** 原作の遺された宝箱/禁呪の宝箱: 層相当の装備を1点得る (鍛冶場ボーナス付き) */
    public static void doForgeCrate(ServerPlayer player) {
        completeEvent(player);
        int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
        boolean bossFloor = floor % 5 == 0;
        var styles = GameData.WeaponStyle.values();
        var st = styles[player.getRandom().nextInt(styles.length)];
        net.minecraft.world.item.ItemStack drop;
        if (player.getRandom().nextDouble() < 0.4) {
            drop = GearFactory.makeSetArmor(null, null, player.getRandom(), StatCalc.luckOf(player), floor + 1,
                    bossFloor ? 130 : 65);
        } else {
            drop = GearFactory.makeWeapon(st, player.getRandom(), StatCalc.luckOf(player), floor + 1,
                    bossFloor ? 130 : 65);
        }
        player.getInventory().add(drop);
        player.sendSystemMessage(Component.literal(
                "§b宝箱を開けた: " + drop.getHoverName().getString()));
        if ("mythic".equals(GearData.rarityOf(drop)) || "legendary".equals(GearData.rarityOf(drop))) {
            player.sendSystemMessage(Component.literal("§d★ 高希少度の装備だ！ (/abyss keep で継承可能)"));
        }
    }

    /**
     * 武器合成マージ (原作 forgeItems の MC 対応版)。
     * 2点の同武器種を合成し、鍛造+1 (最大5)。費用は欠片 3+鍛造Lv×2+威力/25
     * (+原作の素材種別: レア以上=古代石×1 / レジェンド以上=混沌×1 / 神話絡み=虚空×2)。
     * 接辞は両者の最善値を ×1.05 で統合 (原作のモジュラー合成)、特性は本体優先・無ければ継承。
     * Lv3以上は「神錬・」、Lv5は「覚醒・」の銘が付く (原作の forgedCount 命名に対応)。
     * 鍛造画面のスロットからも呼べるよう、任意の2スタックで動作する。
     */
    public static void doMerge(ServerPlayer player) {
        doMergeStacks(player, player.getMainHandItem(), player.getOffhandItem());
    }

    public static void doMergeStacks(ServerPlayer player,
                                     net.minecraft.world.item.ItemStack main,
                                     net.minecraft.world.item.ItemStack sub) {
        boolean bothWeapons = ModItems.styleOf(main.getItem()) != null
                && ModItems.styleOf(sub.getItem()) != null;
        boolean bothArmor = ModItems.slotOf(main.getItem()) != null
                && main.getItem() == sub.getItem();
        if (!bothWeapons && !bothArmor) {
            player.sendSystemMessage(Component.literal("§c合成にはAbyss武器2点か同種の深淵防具2点が必要 (両手持ち or 鍛造画面)"));
            return;
        }
        int lv = GearData.forgeLv(main);
        if (lv >= 5) {
            player.sendSystemMessage(Component.literal("§cこの武器は鍛造MAX (覚醒)"));
            return;
        }
        int cost = 3 + lv * 2 + (GearData.powerOf(main) + GearData.powerOf(sub) + 12) / 25;
        if (countShards(player) < cost) {
            player.sendSystemMessage(Component.literal("§c欠片" + cost + "個が足りない (所持" + countShards(player) + ")"));
            return;
        }
        // 原作の素材種別コスト (希少度の高い方で判定)。不足時は欠片を消費しない
        var order = java.util.List.of("normal", "magic", "rare", "legendary", "mythic");
        String top = order.indexOf(GearData.rarityOf(sub)) > order.indexOf(GearData.rarityOf(main))
                ? GearData.rarityOf(sub) : GearData.rarityOf(main);
        net.minecraft.world.item.Item matItem = null;
        int matCount = 0;
        String matName = "";
        if (top.equals("mythic")) {
            matItem = ModItems.VOID_ESSENCE.get();
            matCount = 2;
            matName = "虚空のエッセンス×2";
        } else if (top.equals("legendary")) {
            matItem = ModItems.CHAOS_GEM.get();
            matCount = 1;
            matName = "混沌の宝石×1";
        } else if (top.equals("rare")) {
            matItem = ModItems.ANCIENT_STONE.get();
            matCount = 1;
            matName = "古代の石×1";
        }
        if (matItem != null && countMats(player, matItem) < matCount) {
            player.sendSystemMessage(Component.literal("§c" + matName + "が足りない (精鋭・番人・層主が落とす)"));
            return;
        }
        takeShards(player, cost);
        if (matItem != null) takeMats(player, matItem, matCount);
        sub.shrink(1);
        // 原作のモジュラー合成: 両方の接辞から最善値を採用し ×1.05 (上位8件まで)
        var merged = new java.util.LinkedHashMap<String, Double>();
        var ma = GearData.affixMap(main);
        var sa = GearData.affixMap(sub);
        var keys = new java.util.LinkedHashSet<String>();
        keys.addAll(ma.keySet());
        keys.addAll(sa.keySet());
        for (String k : keys) {
            double best = Math.max(ma.getOrDefault(k, 0.0), sa.getOrDefault(k, 0.0));
            if (best > 0) merged.put(k, Math.round(best * 1.05 * 10) / 10.0);
        }
        var ordered = new java.util.ArrayList<>(merged.entrySet());
        ordered.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        var tag = main.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (!ordered.isEmpty()) {
            var sb = new StringBuilder();
            int n = 0;
            for (var e : ordered) {
                if (n >= 8) break;
                if (!sb.isEmpty()) sb.append(";");
                sb.append(e.getKey()).append(":").append(e.getValue());
                n++;
            }
            tag.putString("abyss_affixes", sb.toString());
        }
        // 原作の高ランク採用: 素材側の希少度が高ければ引き継ぐ。特性も本体優先・無ければ継承
        String mr = GearData.rarityOf(main);
        String or = GearData.rarityOf(sub);
        if (order.indexOf(or) > order.indexOf(mr)) tag.putString("abyss_rarity", or);
        String subTrait = GearData.traitOf(sub);
        if (GearData.traitOf(main) == null && subTrait != null) tag.putString("abyss_trait", subTrait);
        main.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        GearData.setForgeLv(main, lv + 1);
        String nowTrait = GearData.traitOf(main);
        String hover = main.getHoverName().getString().replaceAll("^[§0-9a-f]+(覚醒|神錬)・", "");
        String lead = "";
        var leadM = java.util.regex.Pattern.compile("^(§[0-9a-f])+").matcher(hover);
        if (leadM.find()) lead = leadM.group();
        String body = hover.substring(lead.length());
        if (nowTrait != null && !body.contains("〔")) body += "〔" + GearData.traitName(nowTrait) + "〕";
        String prefix = lv + 1 >= 5 ? "§d覚醒・" : lv + 1 >= 3 ? "§b神錬・" : "";
        String newName = (prefix.isEmpty() ? lead : prefix) + body;
        if (!newName.equals(main.getHoverName().getString())) {
            main.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(newName));
        }
        player.sendSystemMessage(Component.literal(
                "§b合成！ " + main.getHoverName().getString() + " 鍛造+" + (lv + 1)
                        + " 威力" + GearData.powerOf(main) + " (欠片-" + cost + ")"));
    }

    /** 層間イベントの完了記録 (実績「祭壇の巡礼者」は5回で) */
    public static void completeEvent(ServerPlayer player) {
        player.setData(ModAttachments.EVENT_DONE.get(), 1);
        int total = player.getData(ModAttachments.EVENT_TOTAL.get()) + 1;
        player.setData(ModAttachments.EVENT_TOTAL.get(), total);
        if (total >= 5) {
            var a = GameData.achievementById("event-five");
            CombatHandler.grantAch(player, "event-five", a.title(), GameData.shardsForReward(a.reward()));
        }
    }

    public static void doBuy(ServerPlayer player, int index) {
        String raw = player.getData(ModAttachments.SHOP.get());
        if (raw == null || raw.isBlank()) {
            player.sendSystemMessage(Component.literal("§c商店は開いていない (/abyss event shop)"));
            return;
        }
        String[] items = raw.split(",");
        if (index < 1 || index > items.length) {
            player.sendSystemMessage(Component.literal("§c1〜" + items.length + "で指定"));
            return;
        }
        String[] kv = items[index - 1].split(":");
        int price;
        try {
            price = Integer.parseInt(kv[1]);
        } catch (Exception ex) {
            return;
        }
        var st = GameData.WeaponStyle.byId(kv[0]);
        if (!takeShards(player, price)) {
            player.sendSystemMessage(Component.literal("§c欠片" + price + "個が足りない (所持" + countShards(player) + ")"));
            return;
        }
        player.getInventory().add(GearFactory.makeWeapon(st, player.getRandom(), StatCalc.luckOf(player),
                Math.max(1, player.getData(ModAttachments.FLOOR.get())), 0));
        completeEvent(player);
        player.sendSystemMessage(Component.literal("§6購入: " + st.weaponName + " (奥義「" + st.artName + "」)"));
    }

    /* ---------------- ランダムイベント (原作 RANDOM_EVENTS 7種) ---------------- */

    /** 層の中盤イベントを1件引いて発動する */
    public static void triggerRandomEvent(ServerPlayer player, ServerLevel level, BlockPos center, int floor) {
        var ev = GameData.RANDOM_EVENTS.get(player.getRandom().nextInt(GameData.RANDOM_EVENTS.size()));
        player.sendSystemMessage(Component.literal("§e◆ " + ev.name() + " — " + ev.desc()));
        var scale = Enemies.scaleFor(player, floor);
        switch (ev.kind()) {
            case "loot" -> {
                // 宝の間: 装備3点 + 遺物の3択 (最大2つまで)
                var styles = GameData.WeaponStyle.values();
                for (int i = 0; i < 3; i++) {
                    var drop = GearFactory.makeWeapon(styles[player.getRandom().nextInt(styles.length)],
                            player.getRandom(), StatCalc.luckOf(player), floor, 0);
                    var e = new net.minecraft.world.entity.item.ItemEntity(
                            level, center.getX() + player.getRandom().nextInt(7) - 3,
                            center.getY() + 1, center.getZ() + player.getRandom().nextInt(7) - 3, drop);
                    level.addFreshEntity(e);
                }
                offerRelics(player);
            }
            case "gamble" -> {
                // 賭博の祭壇: 欠片の半分を賭ける
                int have = countShards(player);
                if (have < 2) {
                    player.sendSystemMessage(Component.literal("§7欠片が足りず祭壇は沈黙した (2個〜で賭けられる)"));
                    break;
                }
                int bet = have / 2;
                if (player.getRandom().nextBoolean()) {
                    player.getInventory().add(new net.minecraft.world.item.ItemStack(
                            ModItems.SOUL_SHARD.get(), bet));
                    player.sendSystemMessage(Component.literal("§6当たり！ 欠片+" + bet));
                } else {
                    takeShards(player, bet);
                    player.sendSystemMessage(Component.literal("§c外れ… 欠片-" + bet));
                }
            }
            case "elite" -> {
                // 精鋭の襲撃: 精鋭3体 (通常敵のみ)
                for (int i = 0; i < 3; i++) {
                    var spec = Enemies.pickSpec(floor, -1, player.getRandom());
                    if (spec.role() != Enemies.Role.NORMAL) spec = Enemies.byId("golem");
                    Mob mob = Enemies.create(level, spec);
                    BlockPos pos = center.offset(player.getRandom().nextInt(17) - 8, 1,
                            player.getRandom().nextInt(17) - 8);
                    mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    Enemies.apply(mob, spec, scale, 1.0);
                    Enemies.makeElite(mob);
                    Enemies.tagByRole(mob, spec);
                    mob.setTarget(player);
                    level.addFreshEntity(mob);
                }
                player.setData(ModAttachments.WAVE_TOTAL.get(),
                        player.getData(ModAttachments.WAVE_TOTAL.get()) + 3);
            }
            case "heal" -> {
                // 加護の泉: 全回復 + 素材
                heal(player, player.getMaxHealth());
                int shards = materialCount(player, 3.0);
                if (shards > 0) {
                    player.getInventory().add(new net.minecraft.world.item.ItemStack(
                            ModItems.SOUL_SHARD.get(), shards));
                }
                player.sendSystemMessage(Component.literal("§a全回復！ 欠片+" + shards));
            }
            case "fate" -> {
                if ("mist".equals(ev.fate())) {
                    setFate(player, "mist", 400);
                    player.sendSystemMessage(Component.literal("§5呪いの霧 (20秒): 被ダメ+30%、戦利品2倍"));
                } else {
                    setFate(player, "timewarp", 300);
                    var mobs = level.getEntitiesOfClass(Mob.class,
                            player.getBoundingBox().inflate(48),
                            e -> e.isAlive());
                    for (var m : mobs) {
                        m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 300, 2, false, false));
                    }
                    player.sendSystemMessage(Component.literal("§b時の歪み (15秒): 敵半速、魂ゲージ2倍"));
                }
            }
            default -> {
                // 群れの呼び声: 追加ウェーブ
                spawnWave(player, floor, false);
                player.sendSystemMessage(Component.literal("§e群れが押し寄せる！ 追加ウェーブ"));
            }
        }
    }

    /* ---------------- 遺物・技能の提示 ---------------- */

    private static Component btn2(String label, String cmd) {
        return Component.literal(label + " ").withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))
                .withUnderlined(true));
    }

    /** 遺物の3択を提示 (宝の間・層主撃破時) */
    public static void offerRelics(ServerPlayer player) {
        var owned = relicsOf(player);
        if (owned.size() >= 2) return;
        var roll = GameData.rollRelics(new java.util.Random(player.getRandom().nextLong()), owned, 3);
        if (roll.isEmpty()) return;
        var ids = new java.util.ArrayList<String>();
        var msg = Component.literal("§d遺物を選べ (最大2つまで): ");
        for (var r : roll) {
            ids.add(r.id());
            msg.append(btn2("§6[" + r.name() + " " + r.desc() + "] ", "/abyss relic " + r.id()));
        }
        player.setData(ModAttachments.RELIC_OFFER.get(), String.join(",", ids));
        player.sendSystemMessage(msg);
    }

    public static void doRelic(ServerPlayer player, String id) {
        String offer = player.getData(ModAttachments.RELIC_OFFER.get());
        if (offer == null || !java.util.List.of(offer.split(",")).contains(id)) {
            player.sendSystemMessage(Component.literal("§c今はその遺物を選べない"));
            return;
        }
        if (addRelic(player, id)) {
            player.setData(ModAttachments.RELIC_OFFER.get(), "");
            var r = GameData.relicById(id);
            player.sendSystemMessage(Component.literal("§d遺物を得た: " + r.name() + " (" + r.desc() + ")"));
        } else {
            player.sendSystemMessage(Component.literal("§c遺物は2つまで (取得済み・上限)"));
        }
    }

    /** 技能の3択を提示 (層主撃破時) */
    public static void offerPassives(ServerPlayer player) {
        var owned = passivesOf(player);
        var roll = GameData.rollPassives(new java.util.Random(player.getRandom().nextLong()), owned, 3);
        if (roll.isEmpty()) return;
        var ids = new java.util.ArrayList<String>();
        var msg = Component.literal("§b技能を選べ (代償付き): ");
        for (var p : roll) {
            ids.add(p.id());
            msg.append(btn2("§6[" + p.name() + " " + p.desc() + "] ", "/abyss passive " + p.id()));
        }
        player.setData(ModAttachments.PASSIVE_OFFER.get(), String.join(",", ids));
        player.sendSystemMessage(msg);
    }

    public static void doPassive(ServerPlayer player, String id) {
        String offer = player.getData(ModAttachments.PASSIVE_OFFER.get());
        if (offer == null || !java.util.List.of(offer.split(",")).contains(id)) {
            player.sendSystemMessage(Component.literal("§c今はその技能を選べない"));
            return;
        }
        if (addPassive(player, id)) {
            player.setData(ModAttachments.PASSIVE_OFFER.get(), "");
            var p = GameData.passiveById(id);
            player.sendSystemMessage(Component.literal("§b技能を得た: " + p.name() + " (" + p.desc() + ")"));
        }
    }

    /** 深羅針盤: 最寄りの強敵 (精鋭・番人・層主) を発光させて指し示す */
    public static void compass(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!relicFlagsOf(player).contains("compass")) {
            player.sendSystemMessage(Component.literal("§c深羅針盤を持っていない (遺物で取得)"));
            return;
        }
        var pos = player.position();
        var target = level.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(pos.x - 64, pos.y - 32, pos.z - 64,
                        pos.x + 64, pos.y + 32, pos.z + 64),
                e -> e.isAlive() && (e.getTags().contains(Enemies.WARDEN_TAG)
                        || (e.getCustomName() != null && e.getCustomName().getString().contains("エリート"))
                        || BOSS_BARS.keySet().stream().anyMatch(b -> b.getUUID().equals(e.getUUID())))).stream()
                .min(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
                .orElse(null);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§7近くに強敵はいない"));
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        player.sendSystemMessage(Component.literal(String.format(
                "§b羅針盤が指し示す: %s (%.0fm %s)",
                target.getName().getString(), Math.sqrt(dx * dx + dz * dz), dirName(dx, dz))));
    }

    private static String dirName(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        if (ang < 0) ang += 360;
        String[] dirs = {"南", "南西", "西", "北西", "北", "北東", "東", "南東", "南"};
        return dirs[(int) Math.round(ang / 45)];
    }

    /** 遺物・区域の毎秒処理 (呪杯の代償・巨人の欲・溶鉄の炉・虚無の心臓) */
    /** 防具変更検出用 (UUID -> 4部位の署名) */
    private static final java.util.Map<java.util.UUID, String> ARMOR_SIG = new java.util.HashMap<>();

    private static String armorSig(ServerPlayer player) {
        var sb = new StringBuilder();
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET}) {
            var st = player.getItemBySlot(slot);
            if (!sb.isEmpty()) sb.append("|");
            sb.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()));
            sb.append(st.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().toString());
        }
        return sb.toString();
    }

    public static void tickRunEffects(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return;
        // 防具の着脱でステータス再計算 (初回は記録のみ)
        String sig = armorSig(player);
        String prev = ARMOR_SIG.get(player.getUUID());
        if (prev == null) {
            ARMOR_SIG.put(player.getUUID(), sig);
        } else if (!prev.equals(sig)) {
            ARMOR_SIG.put(player.getUUID(), sig);
            GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
            StatCalc.applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()), true);
            player.displayClientMessage(Component.literal("§b防具を変更: ステータス再計算"), true);
        }
        long t = level.getGameTime();
        // 防具スキル (tick系)。兜: 危険感知・水の加護 / 鎧: 聖盾
        var helm = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        var helmSkill = GearData.skillOf(helm);
        if ("h_danger".equals(helmSkill) && t % 40 == 0) {
            for (Mob m : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(10), e -> e.isAlive())) {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            }
        }
        if ("h_breath".equals(helmSkill) && player.isInWater()
                && !player.hasEffect(MobEffects.WATER_BREATHING)) {            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 240, 0, false, false));
        }
        var chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if ("c_aegis".equals(GearData.skillOf(chest))
                && !player.hasEffect(MobEffects.ABSORPTION) && t % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 1, false, false));
        }
        // 巨躯の刻印・接辞: HP回復 (1秒ごと)
        double regen = boonLv(player, "hp") * 0.6 + StatCalc.affixOf(player, "regenFlat")
                + StatCalc.extraStat(player, "regenFlat") + StatCalc.attrEff(player, 6) * 0.15;
        if (regen > 0 && t % 20 == 0) {
            heal(player, regen);
        }
        // 祭壇「試練」: 毎秒2損耗
        if ("scourge".equals(activeShrine(player)) && t % 20 == 0) {
            if (player.getHealth() > 3) {
                player.hurtServer(level, player.level().damageSources().magic(), 2f);
            }
        }
        // 氷結の深層: 滑走 (移動方向に慣性が付く)
        var zoneSlip = GameData.zoneForFloor(Math.max(1, player.getData(ModAttachments.FLOOR.get())));
        if (zoneSlip.slip() > 0 && t % 10 == 0 && player.onGround()) {
            var mv = player.getDeltaMovement();
            if (mv.horizontalDistanceSqr() > 0.01) {
                player.push(mv.x * 0.12, 0, mv.z * 0.12);
            }
        }
        // 呪杯: 常にHPが削れ続ける (2秒ごとに最大HP1%)
        if (relicFlagsOf(player).contains("chalice") && t % 40 == 0) {
            float dmg = (float) (player.getMaxHealth() * 0.01);
            if (player.getHealth() > dmg + 1) {
                player.hurtServer(level, player.level().damageSources().magic(), dmg);
            }
        }
        // 巨人の欲: 45秒ごとに欠片の半分がHPに変わる (欠片1=HP4)
        if (relicFlagsOf(player).contains("greed") && t % 900 == 0) {
            int have = countShards(player);
            if (have >= 2) {
                int half = have / 2;
                if (takeShards(player, half)) {
                    heal(player, half * 4);
                    player.sendSystemMessage(Component.literal("§6巨人の欲: 欠片-" + half + " → HP回復"));
                }
            }
        }
        // 溶鉄の炉: 火の粉 (10秒ごとに小ダメージ+発火演出)
        var zone = GameData.zoneForFloor(Math.max(1, player.getData(ModAttachments.FLOOR.get())));
        if (zone.emberRain() && t % 200 == 0) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 2, player.getZ(),
                    12, 3, 1, 3, 0.05);
            if (player.getRandom().nextFloat() < 0.5) {
                player.setRemainingFireTicks(20);
            }
        }
        // 虚無の心臓: 中心への引き寄せ (10秒ごとに軽く引く)
        if (zone.voidPull() > 0 && t % 200 == 0) {
            // アリーナ中心は出撃地とずれるため、近傍の敵を寄せる演出に留める
            var mobs = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24),
                    e -> e.isAlive());
            for (var m : mobs) {
                var v = new net.minecraft.world.phys.Vec3(
                        player.getX() - m.getX(), 0, player.getZ() - m.getZ()).normalize().scale(0.6);
                m.push(v.x, 0, v.z);
            }
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                    16, 4, 2, 4, 0.1);
        }
    }


    /** ステータスGUI用: showStatus と同じ内容の行リスト */
    public static java.util.List<String> statusLines(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        int[] a = StatCalc.attrsOf(player);
        var out = new java.util.ArrayList<String>();
        out.add(String.format("§6[%s %s Lv%d] §f第%d層[%s/%s]",
                cls.name, cls.title, player.getData(ModAttachments.LEVEL.get()),
                player.getData(ModAttachments.FLOOR.get()),
                player.getData(ModAttachments.MODE.get()), player.getData(ModAttachments.DIFF.get())));
        out.add(String.format("§d魂%.0f/100 §c攻撃%.1f §aHP%.0f/%.0f §e撃破%d §b奥義:%s",
                player.getData(ModAttachments.SOUL.get()),
                StatCalc.attackDamageOf(player),
                player.getHealth(), player.getMaxHealth(),
                player.getData(ModAttachments.KILLS.get()),
                style != null ? style.artName : "武器を持ってね"));
        out.add(String.format("§7属性 剛%d 韻%d 敏%d 忍%d 幸%d 集%d 活%d 技%d",
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7]));
        out.add("§7適性: " + GameData.ATTR_NAMES[cls.affinityAttr()] + "★ (効果1.5倍) / 節目 10・20・30点");
        out.add("§7祝福 " + boonsOf(player));
        out.add("§7層主 " + player.getData(ModAttachments.BOSSES.get()));
        out.add("§7区域 " + GameData.zoneForFloor(player.getData(ModAttachments.FLOOR.get())).name()
                + " §8| §7異変 " + mutationName(player.getData(ModAttachments.MUTATION.get())));
        out.add("§7遺物 " + relicsOf(player));
        out.add("§7技能 " + passivesOf(player));
        out.add("§7実績 " + player.getData(ModAttachments.ACH.get()));
        var held = player.getMainHandItem();
        if (ModItems.styleOf(held.getItem()) != null) {
            out.addAll(GearData.compareLines(player, held));
        }
        out.add(String.format("§7スコア %d (撃破%d 与ダメ%d 最大combo%d)",
                calcScore(player, false),
                player.getData(ModAttachments.KILLS.get()),
                player.getData(ModAttachments.DMG_DEALT.get()),
                player.getData(ModAttachments.BEST_COMBO.get())));
        return out;
    }
}
