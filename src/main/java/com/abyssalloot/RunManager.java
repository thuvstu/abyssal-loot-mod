package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 層進行・ウェーブ・ボス・職業適用.
 * 原作: FINAL_FLOOR=25, 5層ごとボス, TRUE_FLOOR=26, PINNACLE_FLOOR=27.
 * 同ワールドにアリーナを展開する方式 (Dimensionは後続で追加).
 */
public final class RunManager {
    private RunManager() {}

    /** 層主ボス -> ボスバー */
    public static final Map<Monster, ServerBossEvent> BOSS_BARS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** 悪夢化: 無限深淵のこの層以降のエリートが悪夢になる (精鋭の先の段階) */
    public static final int NIGHTMARE_MIN_FLOOR = 30;
    /** 悪夢化の確率 (エリートのうち 50%) */
    public static final double NIGHTMARE_CHANCE = 0.50;

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
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        return GameData.dailyModForDay(day).id();
    }

    /** 現在の日替わり効果 (dailyモードでない時は null) */
    public static GameData.DailyMod dailyMod(ServerPlayer player) {
        if (!isDaily(player)) return null;
        return dailyMod();
    }

    /** 今日の日替わり効果 (モード不問) */
    public static GameData.DailyMod dailyMod() {
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        return GameData.dailyModForDay(day);
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
            player.displayClientMessage(Component.literal("§d◆ 魂ゲージMAX！Rキーで武器奥義！"), true);
            // 恩寵「絶対領域」: ゲージ100%で3秒の無敵バリア
            if (boonLv(player, "absolute_domain") > 0) {
                player.setData(ModAttachments.IFRAME.get(), 3.0f);
                player.displayClientMessage(Component.literal("§b◆ 絶対領域展開！"), false);
            }
        }
        // ゲージの変動をHUDに即時反映 (0.2秒に1回に間引き)
        CombatHandler.sendHudSoon(player);
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
    public static int countMats(ServerPlayer player, Item item) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** 素材をn個消費 (不足ならfalse) */
    public static boolean takeMats(ServerPlayer player, Item item, int n) {
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

    public static Map<String, Integer> boonsOf(ServerPlayer player) {
        var map = new HashMap<String, Integer>();
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

    /** 未選択の祝福選択の残数 (原作 engine.ts pendingBoons) */
    public static int pendingBoonsOf(ServerPlayer player) {
        return Math.max(0, player.getData(ModAttachments.PENDING_BOONS.get()));
    }

    /** 未選択の祝福選択を n 回追加 (レベルアップなど)。盟約モードでは無効 */
    public static void addPendingBoons(ServerPlayer player, int n) {
        if (n <= 0 || isCovenant(player)) return;
        player.setData(ModAttachments.PENDING_BOONS.get(), pendingBoonsOf(player) + n);
    }

    public static void setPendingBoons(ServerPlayer player, int n) {
        player.setData(ModAttachments.PENDING_BOONS.get(), Math.max(0, n));
    }

    public static boolean isRunLocked(ServerPlayer player) {
        return player.getData(ModAttachments.RUN_LOCK.get()) != 0;
    }

    /* ---------------- 遺物・技能・fate ---------------- */

    public static List<String> relicsOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.RELICS.get());
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public static Set<String> relicFlagsOf(ServerPlayer player) {
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

    public static List<String> passivesOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.PASSIVES.get());
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public static Set<String> passiveFlagsOf(ServerPlayer player) {
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
                DelayRunner.after(ticks, () -> {
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
        // 開いていたGUI (商店・鍛造など) を閉じてから出撃する
        player.closeContainer();
        clearRunField(player);
        int floor = 1;
        String mode = player.getData(ModAttachments.MODE.get());
        resetRunState(player, floor);
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
            player.setData(ModAttachments.HEIRLOOM.get(), ItemStack.EMPTY.copy());
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
            player.sendSystemMessage(Component.literal("§f5. レベルアップで祝福3択の画面が開く (選ぶまで保存)。属性1点・5層ごとに層主"));
            player.sendSystemMessage(Component.literal("§7詳しくは /abyss help ／設定は /abyss menu"));
        }
    }

    /**
     * 出撃の初期化: 全ラン依存状態を既定値に戻す (出撃開始時).
     * データ区分: ①ラン依存の提示・イベント (clearLingeringRunState で消える) /
     * ②ラン中の集計・スタック・CT (ここで消える) /
     * ③永続 (レベル・実績・HEIRLOOM・装備 — ここでは触らない).
     */
    private static void resetRunState(ServerPlayer player, int floor) {
        // ① 提示・イベント・層状態 (endRun/retire と共通のクリア処理)
        clearLingeringRunState(player);
        // ② 進行カウンタ・ラン中スタック・CT
        player.setData(ModAttachments.FLOOR.get(), floor);
        player.setData(ModAttachments.KILLS.get(), 0);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 0);
        player.setData(ModAttachments.SOUL.get(), 0f);
        player.setData(ModAttachments.COMBO.get(), 0);
        player.setData(ModAttachments.BOSSES.get(), "");
        player.setData(ModAttachments.RELICS.get(), "");
        player.setData(ModAttachments.PASSIVES.get(), "");
        player.setData(ModAttachments.ART_USES.get(), 0);
        player.setData(ModAttachments.PERFECT.get(), 0);
        player.setData(ModAttachments.EVENT_TOTAL.get(), 0);
        player.setData(ModAttachments.MIRROR_USED.get(), 0);
        player.setData(ModAttachments.HARVEST.get(), 0);
        player.setData(ModAttachments.BEST_COMBO.get(), 0);
        player.setData(ModAttachments.DMG_DEALT.get(), 0);
        player.setData(ModAttachments.SCYTHE.get(), 0);
        player.setData(ModAttachments.GCNT.get(), 0);
        player.setData(ModAttachments.FOLLOW_HIT.get(), 0);
        player.setData(ModAttachments.SUB_CD.get(), 0f);
        player.setData(ModAttachments.SUB_SHIELD.get(), 0f);
        player.setData(ModAttachments.PARRY_TICK.get(), -1000L);
        player.setData(ModAttachments.LIGHT_CD.get(), 0f);
        player.setData(ModAttachments.SKILL_CD.get(), 0f);
        player.setData(ModAttachments.DASH_CD.get(), 0f);
        player.setData(ModAttachments.IFRAME.get(), 0f);
        player.setData(ModAttachments.COUNTER.get(), 0f);
        player.setData(ModAttachments.FORTIFY.get(), 0f);
    }

    /** 原作の魂の内訳をチャットに出す (勝利時の要約). 報酬の欠片とは別の換算値 */
    static void sendSoulSummary(ServerPlayer player, boolean trueCleared) {
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
                FloorEvents.countShards(player),
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
        player.getInventory().add(new ItemStack(ModItems.SOUL_SHARD.get(), shards));
        player.sendSystemMessage(Component.literal("§e魂の欠片 ×" + shards + " (呪印・難易度・幸運の倍率込み)"));
        var styles = GameData.WeaponStyle.values();
        player.getInventory().add(GearFactory.makePrizeWeapon(styles[player.getRandom().nextInt(styles.length)],
                player.getRandom(), StatCalc.luckOf(player), 25));
        sendSoulSummary(player, false);
        Achievements.grantAch(player, "full-clear", "深淵を終わらせた者", 16);
        if (isCovenant(player)) {
            var a = GameData.achievementById("covenant-kept");
            Achievements.grantAch(player, "covenant-kept", a.title(), GameData.shardsForReward(a.reward()));
        }
        // 出撃終了: 条件固定を解除
        endRun(player);
    }

    /**
     * リタイア: 出撃を中断し、アリーナのバリアを除去して残存MOBを消す。
     * 装備・欠片は保持される (原作の帰還に相当)。
     */
    public static void retire(ServerPlayer player) {
        clearRunField(player);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 0);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        clearLingeringRunState(player);
        // 開いていたGUI (商店・鍛造・比較など) を強制的に閉じる
        player.closeContainer();
        player.sendSystemMessage(Component.literal("§7リタイアした (装備・欠片は保持)。再出撃は /abyss start"));
    }

    /** 戦場の掃除: 前回アリーナのバリア除去・残存MOB消去・ボスバー解除 (再出撃・死亡時に使用) */
    public static void clearRunField(ServerPlayer player) {
        ArenaManager.clearArenaBarriers(player);
        if (player.level() instanceof ServerLevel level) {
            BlockPos center = ArenaManager.arenaCenterOf(player);
            AABB box = center != null
                    ? new AABB(center).inflate(64)
                    : player.getBoundingBox().inflate(64);
            for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.isAlive())) {
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

    /** 現在層の異変 (なければ null) */
    public static GameData.Mutation currentMutation(ServerPlayer player) {
        String id = player.getData(ModAttachments.MUTATION.get());
        return id == null || id.isEmpty() ? null : GameData.mutationById(id);
    }

    public static String mutationName(String id) {
        var m = GameData.mutationById(id);
        return m == null ? "静穏" : m.name();
    }

    public static boolean hasMutation(ServerPlayer player, String id) {
        return id.equals(player.getData(ModAttachments.MUTATION.get()));
    }

    /**
     * 出撃の終了処理 (勝利・踏破)。条件固定を解除し、提示中の祝福/遺物/商店などの
     * ラン依存状態を必ず消す (次の出撃に前ランの未選択が引きずられないように)。
     */
    static void endRun(ServerPlayer player) {
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        player.setData(ModAttachments.WAVE_TOTAL.get(), 0);
        clearLingeringRunState(player);
        // bossrush の Lv10 ステータスなど、ラン中の適用を本来レベルに戻す
        var clsE = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        StatCalc.applyClass(player, clsE, player.getData(ModAttachments.LEVEL.get()), true);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9f, 1f);
        ArenaManager.clearArenaBarriers(player);
        player.closeContainer();
    }

    /** ラン中のみ有効な提示・イベント状態を消す (endRun / retire / resetRunState 共通) */
    private static void clearLingeringRunState(ServerPlayer player) {
        player.setData(ModAttachments.OFFER.get(), "");
        player.setData(ModAttachments.PENDING_BOONS.get(), 0);
        player.setData(ModAttachments.RELIC_OFFER.get(), "");
        player.setData(ModAttachments.PASSIVE_OFFER.get(), "");
        player.setData(ModAttachments.SHOP.get(), "");
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        player.setData(ModAttachments.MUTATION.get(), "");
        player.setData(ModAttachments.DUNGEON.get(), "");
        player.setData(ModAttachments.FATE_MIST.get(), 0L);
        player.setData(ModAttachments.FATE_TIME.get(), 0L);
        player.setData(ModAttachments.TRAPS.get(), "");
        player.setData(ModAttachments.DESTRUCT.get(), "");
        // 祝福・祭壇効果はラン依存 (次の出撃に持ち越さない)
        player.setData(ModAttachments.BOONS.get(), "");
        player.setData(ModAttachments.SHRINE_FATE.get(), "");
        player.setData(ModAttachments.SHRINE_UNTIL.get(), 0L);
    }

    /** 次層への進行 (転移門の開放を確認。キー・ボタン用) */
    public static void tryNextFloor(ServerPlayer player) {
        int total = player.getData(ModAttachments.WAVE_TOTAL.get());
        int kills = player.getData(ModAttachments.FLOOR_KILLS.get());
        if (total > 0 && player.getData(ModAttachments.PORTAL_OPEN.get()) == 0) {
            player.sendSystemMessage(Component.literal(
                    "§c転移門が開いていない (残り" + Math.max(0, total - kills) + "体)"));
            return;
        }
        nextFloor(player);
    }

    public static void nextFloor(ServerPlayer player) {
        String mode = player.getData(ModAttachments.MODE.get());
        int cur = player.getData(ModAttachments.FLOOR.get());
        if (cur >= 100) {
            player.sendSystemMessage(Component.literal("§7無限深淵の最深部に到達した (endless の限界)"));
            return;
        }
        int floor = cur + 1;
        // 踏破判定 (原作FINAL_FLOOR=25 / 巡礼は8層主 / 終焉の座は27単層)
        if ("bossrush".equals(mode) && floor > 8) {
            var a = GameData.achievementById("rush-master");
            Achievements.grantAch(player, "rush-master", a.title(), GameData.shardsForReward(a.reward()));
            victory(player);
            return;
        }
        if ("pinnacle".equals(mode) && floor > GameData.PINNACLE_FLOOR) {
            BossHandler.pinnacleVictory(player);
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
                Achievements.grantAch(player, "daily-depth", a.title(), GameData.shardsForReward(a.reward()));
            }
            if (sigilsOf(player).size() >= 8) {
                var a = GameData.achievementById("eight-sigils");
                Achievements.grantAch(player, "eight-sigils", a.title(), GameData.shardsForReward(a.reward()));
            }
        }
        if (floor == 15 && "inferno".equals(player.getData(ModAttachments.DIFF.get()))) {
            var a = GameData.achievementById("inferno-slayer");
            Achievements.grantAch(player, "inferno-slayer", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (floor == 35 && "endless".equals(mode)) {
            var a = GameData.achievementById("endless-diver");
            Achievements.grantAch(player, "endless-diver", a.title(), GameData.shardsForReward(a.reward()));
        }
        if (floor == 50 && "endless".equals(mode)) {
            var a = GameData.achievementById("endless-fifty");
            Achievements.grantAch(player, "endless-fifty", a.title(), GameData.shardsForReward(a.reward()));
        }
        player.setData(ModAttachments.FLOOR.get(), floor);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        // 層移動で開いていたGUI (商店など) は自動的に閉じる (前の層の状態を引きずらない)
        player.closeContainer();
        // 未選択の祝福は層移動しても絶対に消さない (原作 pendingBoons は新出撃まで保存される).
        // ここで OFFER を消すと「選択できないまま次の層に上がる」不具合の原因になったため提示だけやり直す.
        if (pendingBoonsOf(player) > 0
                || !player.getData(ModAttachments.OFFER.get()).isBlank()) {
            player.sendSystemMessage(Component.literal(
                    "§e◆ レベルアップの祝福が未選択だ (" + Math.max(1, pendingBoonsOf(player)) + "件)"));
            BoonHandler.resendBoonOffer(player);
        }
        player.setData(ModAttachments.EVENT_DONE.get(), 0);
        player.setData(ModAttachments.SHOP.get(), "");
        player.setData(ModAttachments.TRAPS.get(), "");
        player.setData(ModAttachments.DESTRUCT.get(), "");
        // 層移動回復 (飢餓の呪印で減衰). 日替わり「乾ききった迷宮」では回復なし
        boolean noHeal = isDaily(player) && dailyMod() != null && dailyMod().noFloorHeal();
        if (!noHeal) heal(player, player.getMaxHealth() * 0.25);
        else player.sendSystemMessage(Component.literal("§e乾ききった迷宮: 層移動の回復なし"));
        ArenaManager.refreshArena(player, floor);
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
        // rollFloor=false は追加の襲来 (swarm 等) なので総数に加算する
        if (rollFloor) {
        // rollFloor=false は追加の襲来 (swarm 等) なので総数に加算する
        if (rollFloor) {
            player.setData(ModAttachments.WAVE_TOTAL.get(), count + (bossFloor ? 1 : 0));
        } else {
            player.setData(ModAttachments.WAVE_TOTAL.get(),
                    player.getData(ModAttachments.WAVE_TOTAL.get()) + count);
        }
        } else {
            player.setData(ModAttachments.WAVE_TOTAL.get(),
                    player.getData(ModAttachments.WAVE_TOTAL.get()) + count);
        }
        // 湧き中心はアリーナ中心 (プレイヤー位置だと場外に湧くため)
        BlockPos arena = ArenaManager.arenaCenterOf(player);
        BlockPos center = arena != null ? arena : player.blockPosition();

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
                // 悪夢化: 無限深淵の深層 (NIGHTMARE_MIN_FLOOR 以降) のエリートは確率で悪夢になる
                if (floor >= NIGHTMARE_MIN_FLOOR
                        && "endless".equals(player.getData(ModAttachments.MODE.get()))
                        && rand.nextDouble() < NIGHTMARE_CHANCE) {
                    Enemies.makeNightmare(mob);
                }
            }
            Enemies.tagByRole(mob, spec);
            mob.setTarget(player);
            level.addFreshEntity(mob);
        }

        if (bossFloor) {
            BossHandler.spawnFloorBoss(player, level, center, floor, scale);
        } else if (ascensionFloor) {
            BossHandler.spawnAscensionBoss(player, level, center, scale);
        } else if (rollFloor && rand.nextDouble() < 0.45) {
            // 層の中盤イベント (原作 RANDOM_EVENTS). ボス層以外で45%
            FloorEvents.triggerRandomEvent(player, level, center, floor);
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

    public static void compass(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!relicFlagsOf(player).contains("compass")) {
            player.sendSystemMessage(Component.literal("§c深羅針盤を持っていない (遺物で取得)"));
            return;
        }
        var pos = player.position();
        var target = level.getEntitiesOfClass(Mob.class,
                new AABB(pos.x - 64, pos.y - 32, pos.z - 64,
                        pos.x + 64, pos.y + 32, pos.z + 64),
                e -> e.isAlive() && (e.getTags().contains(Enemies.WARDEN_TAG)
                        || (e.getCustomName() != null && e.getCustomName().getString().contains("エリート"))
                        || BOSS_BARS.keySet().stream().anyMatch(b -> b.getUUID().equals(e.getUUID())))).stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceTo(pos)))
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

    /** 防具変更検出用 (UUID -> 4部位の署名) */
    private static final Map<UUID, String> ARMOR_SIG = new HashMap<>();

    /** ログアウトで防具署名キャッシュを掃除 (CombatHandler.onPlayerLoggedOut から呼ぶ) */
    static void onLogout(ServerPlayer player) {
        ARMOR_SIG.remove(player.getUUID());
    }

    private static String armorSig(ServerPlayer player) {
        var sb = new StringBuilder();
        for (var slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            var st = player.getItemBySlot(slot);
            if (!sb.isEmpty()) sb.append("|");
            sb.append(BuiltInRegistries.ITEM.getKey(st.getItem()));
            sb.append(st.getOrDefault(DataComponents.CUSTOM_DATA,
                    CustomData.EMPTY).copyTag().toString());
        }
        return sb.toString();
    }

    /** 遺物・区域の毎秒処理 (呪杯の代償・巨人の欲・溶鉄の炉・虚無の心臓) */
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
        // 深獄の呼吸: 試練外でも高度0未満 (バニラの地下) に潜ると魂が漂ってくる
        // (レベルアップに繋がるので、洞窟探索自体が冒険の足場になる)
        if (!isRunLocked(player) && player.getY() < 0) {
            addGauge(player, 1);
        }
        // 生活属性: 採掘=保持ツールで掘削加速 / 跳躍=常時跳躍力 (40tickごとに更新)
        if (t % 40 == 0) {
            double mine = StatCalc.attrEff(player, 8);
            if (mine > 0 && ModItems.isTool(player.getMainHandItem().getItem())) {
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 70,
                        Math.min(2, (int) (mine / 10)), false, false));
            }
            double jump = StatCalc.attrEff(player, 9);
            if (jump > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 70,
                        Math.min(2, (int) (jump / 12)), false, false));
            }
        }
        // 防具スキル (tick系)。兜: 危険感知・水の加護 / 鎧: 聖盾
        var helm = player.getItemBySlot(EquipmentSlot.HEAD);
        var helmSkill = GearData.skillOf(helm);
        if ("h_danger".equals(helmSkill) && t % 40 == 0) {
            for (Mob m : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(10), e -> e.isAlive())) {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            }
        }
        if ("h_breath".equals(helmSkill) && player.isInWater()
                && !player.hasEffect(MobEffects.WATER_BREATHING)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 240, 0, false, false));
        }
        // 兜スキル「炉火の兜」: 炎上を自動的に鎮める
        if ("h_warmth".equals(helmSkill) && player.isOnFire()) {
            player.setRemainingFireTicks(0);
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(),
                    8, 0.4, 0.6, 0.4, 0.05);
        }
        var legs = player.getItemBySlot(EquipmentSlot.LEGS);
        // 脚スキル「羽毛の脚」: 常時緩降下
        if ("l_feather".equals(GearData.skillOf(legs))
                && player.getDeltaMovement().y < -0.1
                && !player.hasEffect(MobEffects.SLOW_FALLING)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
        }
        var feet = player.getItemBySlot(EquipmentSlot.FEET);
        // 靴スキル「跳上げの靴」: 常時跳躍力II
        if ("b_charge".equals(GearData.skillOf(feet))
                && !player.hasEffect(MobEffects.JUMP_BOOST) && t % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 240, 1, false, false));
        }
        // 靴スキル「磁石の靴」: 8m内の落とし物を吸い寄せる (2秒ごと)
        if ("b_magnet".equals(GearData.skillOf(feet)) && t % 40 == 0) {
            Vec3 pos = player.position();
            for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(8), e -> e.isAlive() && !e.hasPickUpDelay())) {
                Vec3 d = pos.subtract(it.position());
                if (d.lengthSqr() > 1) {
                    it.setDeltaMovement(d.normalize().scale(0.35));
                    it.setNoPickUpDelay();
                }
            }
        }
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
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
            int have = FloorEvents.countShards(player);
            if (have >= 2) {
                int half = have / 2;
                if (FloorEvents.takeShards(player, half)) {
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
                var v = new Vec3(
                        player.getX() - m.getX(), 0, player.getZ() - m.getZ()).normalize().scale(0.6);
                m.push(v.x, 0, v.z);
            }
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                    16, 4, 2, 4, 0.1);
        }
    }

    /** ステータスGUI用: showStatus と同じ内容の行リスト */
    public static List<String> statusLines(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        int[] a = StatCalc.attrsOf(player);
        var out = new ArrayList<String>();
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
        out.add(String.format("§7属性 剛%d 韻%d 敏%d 忍%d 幸%d 集%d 活%d 技%d 採%d 跳%d",
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9]));
        out.add("§7適性: " + GameData.ATTR_NAMES[cls.affinityAttr()] + "★ (効果1.5倍) / 節目 10・20・30点");
        out.add(String.format("§7搦み §b射程+%.1f §b横斬り+%.1f §b連撃+%d (接辞・祝福・スキル・武器種)",
                StatCalc.reachOf(player), StatCalc.sweepOf(player), StatCalc.strikesOf(player)));
        out.add("§7祝福 " + boonsOf(player));
        out.add("§7層主 " + player.getData(ModAttachments.BOSSES.get()));
        out.add("§7区域 " + GameData.zoneForFloor(player.getData(ModAttachments.FLOOR.get())).name()
                + " §8| §7異変 " + mutationName(player.getData(ModAttachments.MUTATION.get())));
        var echo = StatCalc.activeBiomeEcho(player);
        out.add("§7地脈の共鳴 " + (echo != null ? "§b" + echo.name() + " §7(" + echo.desc() + ")" : "§8なし"));
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
