package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;

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

    /** アリーナ半径 (MOB収容の境界も兼ねる) */
    public static final int ARENA_R = 20;

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
                * (1 + wbonusOf(player).energyPct() / 100.0)
                * (1 + affixOf(player, "energyPct") / 100.0);
        // 呪い (xpPct): 成長阻害でゲージ増加が鈍る (呪喰らいで無効化)
        var held = player.getMainHandItem();
        if (curseActive(player, held) && "xpPct".equals(curseOf(held))) {
            rate *= 1 + curseValueOf(held) / 100.0;
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
        int fortune = attrsOf(player)[4];
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

    /* ---------------- 属性・祝福 ---------------- */

    /** 剛力,韻律,敏捷,忍耐,幸運 */
    public static int[] attrsOf(ServerPlayer player) {
        int[] a = new int[5];
        try {
            String[] p = player.getData(ModAttachments.ATTRS.get()).split(",");
            for (int i = 0; i < Math.min(5, p.length); i++) a[i] = Math.max(0, Integer.parseInt(p[i].trim()));
        } catch (Exception ignored) {}
        return a;
    }

    public static void setAttrs(ServerPlayer player, int[] a) {
        player.setData(ModAttachments.ATTRS.get(), a[0] + "," + a[1] + "," + a[2] + "," + a[3] + "," + a[4]);
    }

    public static int attrSpent(ServerPlayer player) {
        int s = 0;
        for (int v : attrsOf(player)) s += v;
        return s;
    }

    /** 未配分の属性点 (Lv-1-配分済み) */
    public static int attrPointsLeft(ServerPlayer player) {
        return Math.max(0, player.getData(ModAttachments.LEVEL.get()) - 1 - attrSpent(player));
    }

    /** 属性1点配分 (成功でメッセージ・職業再適用)。GUI/コマンド共通 */
    public static boolean addAttrPoint(ServerPlayer player, int idx) {
        if (idx < 0 || idx > 4) {
            player.sendSystemMessage(Component.literal("§c剛力/韻律/敏捷/忍耐/幸運で指定"));
            return false;
        }
        int level = player.getData(ModAttachments.LEVEL.get());
        int spent = attrSpent(player);
        if (spent >= level - 1) {
            player.sendSystemMessage(Component.literal(
                    "§c属性ポイント不足 (Lv" + level + "で" + (level - 1) + "点、配分済" + spent + "点)"));
            return false;
        }
        int[] a = attrsOf(player);
        a[idx]++;
        setAttrs(player, a);
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        applyClass(player, cls, level);
        player.sendSystemMessage(Component.literal(
                "§a属性配分: 剛" + a[0] + " 韻" + a[1] + " 敏" + a[2] + " 忍" + a[3] + " 幸" + a[4]
                        + " (残" + (level - 1 - spent - 1) + "点)"));
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

    public static void applyClass(ServerPlayer player, GameData.HeroClass cls, int level) {
        applyClass(player, cls, level, false);
    }

    /** 防具変更時の静かな再計算用 (quiet=true で告知なし) */
    public static void applyClass(ServerPlayer player, GameData.HeroClass cls, int level, boolean quiet) {
        player.setData(ModAttachments.HERO_CLASS.get(), cls.id);
        player.setData(ModAttachments.LEVEL.get(), level);
        int[] a = attrsOf(player);
        var passives = passivesOf(player);
        // 呪い武器の弱点 (主手持ちのみ。呪喰らいで無効化。攻速は attackSpeedPctOf 側で処理)
        var held = player.getMainHandItem();
        double curseHp = 0, curseMove = 0;
        if (curseActive(player, held)) {
            String key = curseOf(held);
            double val = curseValueOf(held);
            if ("maxHpPct".equals(key)) curseHp = val;
            else if ("moveSpeedPct".equals(key)) curseMove = val;
        }
        // 原作: 剛力+3%/点, 韻律+2%/点, 敏捷+1.5%/点, 忍耐+4%/点, 幸運=会心相当
        var wb0 = wbonusOf(player);
        double maxHp = (GameData.heroMaxHp(cls, level) + affixOf(player, "maxHpFlat"))
                * (1 + a[3] * 0.04) * (1 + boonLv(player, "hp") * 0.18)
                * (1 + wb0.maxHpPct() / 100.0) * (1 + affixOf(player, "maxHpPct") / 100.0)
                * (1 + GameData.passiveMaxHpPct(passives) / 100.0)
                * (1 + player.getData(ModAttachments.HARVEST.get()) * 0.01)
                * (1 + curseHp / 100.0);
        if (boonLv(player, "curse_eater") > 0) {
            maxHp *= 1 + GameData.curseEaterBonusMaxHp(passives) / 100.0;
        }
        // 技能「深淵の目」: 最大HP -25 (固定)
        if (passives.contains("abyss_eye")) maxHp = Math.max(1, maxHp - 25);
        // 祭壇「黄金」: 最大HP-20
        if ("gold".equals(activeShrine(player))) maxHp = Math.max(1, maxHp - 20);
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(Math.min(1024, maxHp));
            player.setHealth((float) Math.min(maxHp, attr.getValue()));
        }
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(GameData.heroDamage(cls, level));
        var spd = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) {
            double base = cls.mcMoveSpeed();
            // 疾走の刻印 +12%/Lv (原作 b_speed)。回避短縮は dash 側で適用
            double movePct = boonLv(player, "speed") * 12 + curseMove
                    + wb0.moveSpeedPct() + affixOf(player, "moveSpeedPct");
            if (passives.contains("swift")) movePct += 25;
            if (passives.contains("slow_burn")) movePct -= 12;
            spd.setBaseValue(base * (1 + a[2] * 0.015) * (1 + movePct / 100.0));
        }
        var atkSpd = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpd != null) {
            double asPct = attackSpeedPctOf(player);
            atkSpd.setBaseValue(cls.attackSpeed
                    * (1 + a[1] * 0.02)
                    * (1 + asPct / 100.0));
        }
        if (!quiet) {
            player.displayClientMessage(Component.literal(
                    "§6職業: " + cls.name + "「" + cls.title + "」 必殺技: " + cls.skillName()), false);
        }
    }

    /** 攻速の合計% (迅風・武器クラス・接辞・技能・呪い)。applyClass と共有 */
    public static double attackSpeedPctOf(ServerPlayer player) {
        var held = player.getMainHandItem();
        double curseAspd = 0;
        if (curseActive(player, held) && "attackSpeedPct".equals(curseOf(held))) {
            curseAspd = curseValueOf(held);
        }
        double v = boonLv(player, "as") * 16 + curseAspd
                + wbonusOf(player).attackSpeedPct() + affixOf(player, "attackSpeedPct");
        var passives = passivesOf(player);
        if (passives.contains("clear_mind")) v -= 10;
        if (passives.contains("frostbound")) v -= 12;
        if (passives.contains("echo_strike")) v -= 15;
        return v;
    }

    /** 鎌の収穫スタックを攻速属性へ即時反映 (チャット告知なし) */
    public static void refreshAttackSpeed(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int[] a = attrsOf(player);
        var atkSpd = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpd != null) {
            atkSpd.setBaseValue(cls.attackSpeed
                    * (1 + a[1] * 0.02)
                    * (1 + attackSpeedPctOf(player) / 100.0)
                    * (1 + player.getData(ModAttachments.SCYTHE.get()) * 0.06));
        }
    }

    public static double attackDamageOf(ServerPlayer player) {
        return attackDamageWith(player, player.getMainHandItem());
    }

    /** 指定武器を持った場合の攻撃力 (装備比較GUI用)。affix/curse/forge/traitはheldで評価 */
    public static double attackDamageWith(ServerPlayer player, net.minecraft.world.item.ItemStack held) {
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        double base = atk != null ? atk.getValue() : 10;
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int level = player.getData(ModAttachments.LEVEL.get());
        int[] a = attrsOf(player);
        var passives = passivesOf(player);
        // 破壊の刻印 (b_dmg2): 固定+12/Lv (乗算の前に加算)。防具の攻撃接辞も合算
        base += boonLv(player, "dmg2") * 12 + affixOf(held, "damageFlat") + armorAffix(player, "damageFlat");
        var wb = wbonusOf(held);
        double mult = (1 + a[0] * 0.03) * (1 + boonLv(player, "dmg") * 0.12)
                * (1 + boonLv(player, "dmg1") * 0.18) * (1 + boonLv(player, "dmg2") * 0.08)
                * (1 + wb.damagePct() / 100.0) * (1 + (affixOf(held, "damagePct") + armorAffix(player, "damagePct")) / 100.0)
                * (1 + player.getData(ModAttachments.OATH_DMG.get()) * 0.08)
                * (1 + forgeLv(held) * 0.08)
                * curseMultOf(player, held)
                * (1 + GameData.passiveDamagePct(passives) / 100.0)
                * (1 + player.getData(ModAttachments.HARVEST.get()) * 0.02);
        // 恩寵「呪喰らい」: 技能の欠点無効・正効果2倍
        if (boonLv(player, "curse_eater") > 0) {
            mult *= 1 + GameData.curseEaterBonusDamage(passives) / 100.0;
        }
        // 遺物「呪杯」: +40% / 「双魂の盃」: 10レベルごとに+10%
        if (relicFlagsOf(player).contains("chalice")) mult *= 1.4;
        if (relicFlagsOf(player).contains("twin")) mult *= 1 + (level / 10) * 0.10;
        // 技能「最後の砦」: HP30%以下で+50% / 「勢い」: コンボ20以上で+35%
        double hpRatio = player.getHealth() / Math.max(1, player.getMaxHealth());
        if (passives.contains("last_stand") && hpRatio < 0.3) {
            mult *= 1.5;
        }
        if (passives.contains("momentum") && player.getData(ModAttachments.COMBO.get()) >= 20) {
            mult *= 1.35;
        }
        // 恩寵「最後の笑い」: HP30%以下で+70%/Lv / 「過剰修復」: HP割合×30%/Lv
        if (boonLv(player, "lastlaugh") > 0 && hpRatio < 0.3) {
            mult *= 1 + 0.70 * boonLv(player, "lastlaugh");
        }
        if (boonLv(player, "overflow") > 0) {
            mult *= 1 + 0.30 * hpRatio * boonLv(player, "overflow");
        }
        // 恩寵「完全主義」: 見切り後3秒間+90%/Lv
        if (boonLv(player, "perfect") > 0
                && player.level().getGameTime() - player.getData(ModAttachments.PARRY_TICK.get()) < 60) {
            mult *= 1 + 0.90 * boonLv(player, "perfect");
        }
        // 祭壇「怒り」: 与ダメ+60%
        if ("rage".equals(activeShrine(player))) mult *= 1.6;
        return Math.max(1, GameData.heroDamage(cls, level) * 0.35 + base * 0.15) * mult;
    }

    /** 鍛造レベル (武器CustomData "abyss_forge") */
    public static int forgeLv(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (ModItems.styleOf(stack.getItem()) == null) return 0;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return Math.max(0, Math.min(5, data.copyTag().getInt("abyss_forge").orElse(0)));
    }

    public static void setForgeLv(net.minecraft.world.item.ItemStack stack, int lv) {
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putInt("abyss_forge", Math.max(0, Math.min(5, lv)));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
    }

    /* ---------------- 武器特性・レアリティ (原作WEAPON_TRAITS/RARITY準拠) ---------------- */

    public record TraitDef(String id, String name) {}

    public static final TraitDef[] TRAITS = {
            new TraitDef("sharp", "鋭刃"), new TraitDef("vampiric", "吸血鬼"),
            new TraitDef("explosive", "爆砕"), new TraitDef("terminator", "断末魔"),
            new TraitDef("chain_l", "連鎖雷"), new TraitDef("infernus", "業火"),
            new TraitDef("venomous", "猛毒"), new TraitDef("rend", "裂傷"),
            new TraitDef("frenzy", "狂戦士"), new TraitDef("fortify", "不壊"),
    };

    public static String traitName(String id) {
        for (var t : TRAITS) {
            if (t.id().equals(id)) return t.name();
        }
        return null;
    }

    public static String traitOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String id = data.copyTag().getString("abyss_trait").orElse("");
        return id.isEmpty() ? null : id;
    }

    public static String rarityOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "normal";
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String r = data.copyTag().getString("abyss_rarity").orElse("");
        return r.isEmpty() ? "normal" : r;
    }

    public static double rarityMult(String rarity) {
        return switch (rarity) {
            case "mythic" -> 1.7;
            case "legendary" -> 1.45;
            case "rare" -> 1.25;
            case "magic" -> 1.1;
            default -> 1.0;
        };
    }

    private static String rarityLabel(String rarity) {
        return switch (rarity) {
            case "mythic" -> "§d神話";
            case "legendary" -> "§6レジェンド";
            case "rare" -> "§eレア";
            case "magic" -> "§b魔法";
            default -> "§7通常";
        };
    }

    /* ---------------- 武器クラス・接辞 (原作 WEAPON_CLASSES / AFFIXES) ---------------- */

    /** 主手持ち武器のクラス特性 (なければゼロ) */
    public static GameData.WeaponBonus wbonusOf(ServerPlayer player) {
        return wbonusOf(player.getMainHandItem());
    }

    /** 指定スタックの武器クラス特性 (なければゼロ) */
    public static GameData.WeaponBonus wbonusOf(net.minecraft.world.item.ItemStack stack) {
        var style = ModItems.styleOf(stack.getItem());
        return style == null ? GameData.weaponBonus("") : GameData.weaponBonus(style.id);
    }

    /** 主手持ち武器の接辞値 (なければ 0)。防具4部位の合算も含む */
    public static double affixOf(ServerPlayer player, String key) {
        return affixOf(player.getMainHandItem(), key) + armorAffix(player, key);
    }

    /** 装備中防具4部位の接辞合計 (原作の7スロット合算に相当) */
    public static double armorAffix(ServerPlayer player, String key) {
        double v = 0;
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET}) {
            v += affixMap(player.getItemBySlot(slot)).getOrDefault(key, 0.0);
        }
        return v;
    }

    public static double affixOf(net.minecraft.world.item.ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return 0;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String raw = data.copyTag().getString("abyss_affixes").orElse("");
        if (raw.isEmpty()) return 0;
        for (String e : raw.split(";")) {
            String[] kv = e.split(":");
            if (kv.length == 2 && kv[0].equals(key)) {
                try {
                    return Double.parseDouble(kv[1]);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String rarityColor(String rarity) {
        return switch (rarity) {
            case "mythic" -> "§d";
            case "legendary" -> "§6";
            case "rare" -> "§e";
            case "magic" -> "§b";
            default -> "§7";
        };
    }

    /** 原作 STAT_META.worth (威力計算用)。鍛造費・商店価格の基礎値 */
    private static final java.util.Map<String, Double> WORTH = java.util.Map.ofEntries(
            java.util.Map.entry("damageFlat", 1.0), java.util.Map.entry("damagePct", 3.4),
            java.util.Map.entry("attackSpeedPct", 3.2), java.util.Map.entry("moveSpeedPct", 2.4),
            java.util.Map.entry("critPct", 2.8), java.util.Map.entry("critDmgPct", 1.5),
            java.util.Map.entry("areaPct", 1.8), java.util.Map.entry("projectiles", 26.0),
            java.util.Map.entry("pierce", 14.0), java.util.Map.entry("lifestealPct", 6.0),
            java.util.Map.entry("armorPct", 5.0), java.util.Map.entry("regenFlat", 16.0),
            java.util.Map.entry("luckFlat", 1.1), java.util.Map.entry("energyPct", 1.6),
            java.util.Map.entry("maxHpFlat", 0.1), java.util.Map.entry("maxHpPct", 2.6),
            java.util.Map.entry("chain", 20.0), java.util.Map.entry("explodePct", 3.0),
            java.util.Map.entry("freezePct", 2.6));

    /** 武器の接辞マップ (key→value)。原作 mods に相当 */
    public static java.util.Map<String, Double> affixMap(net.minecraft.world.item.ItemStack stack) {
        var out = new java.util.LinkedHashMap<String, Double>();
        if (stack == null || stack.isEmpty()) return out;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String raw = data.copyTag().getString("abyss_affixes").orElse("");
        if (raw.isEmpty()) return out;
        for (String e : raw.split(";")) {
            String[] kv = e.split(":");
            if (kv.length == 2) {
                try {
                    out.put(kv[0], Double.parseDouble(kv[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    /**
     * 武器の威力 (原作 power 式の MC 版)。
     * Σ|接辞|×worth × 希少度倍率 × (1+鍛造Lv×0.08)。鍛造費・商店価格に使用。
     */
    public static int powerOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (ModItems.styleOf(stack.getItem()) == null && ModItems.slotOf(stack.getItem()) == null) return 0;
        double p = 0;
        for (var e : affixMap(stack).entrySet()) {
            p += Math.abs(e.getValue()) * WORTH.getOrDefault(e.getKey(), 1.0);
        }
        String rarity = rarityOf(stack);
        p *= rarityMult(rarity) * (1 + forgeLv(stack) * 0.08);
        if (traitOf(stack) != null) p *= 1.1;
        if (curseOf(stack) != null) p *= 1.08;
        return Math.max(1, (int) Math.round(p));
    }

    /** ランダム武器生成 (レアリティ抽選+特性付与)。運・層・ボーナスなしの簡易版 */
    public static net.minecraft.world.item.ItemStack makeWeapon(
            GameData.WeaponStyle style, net.minecraft.util.RandomSource rand) {
        return makeWeapon(style, rand, 0, 1, 0);
    }

    /**
     * ランダム武器生成 (原作 genItem の MC 対応版)。
     * 希少度は原作 rollRarity (運・層・ボーナスで上位が出やすい)。レア以上は12%で呪い。
     * 接辞は希少度ごとの件数 (通常0-1/魔法1-2/レア3-4/レジェンド4-5/神話5-6) を抽選し、
     * 命名はレア=接頭+武器名・魔法=接頭+武器名・接尾・レジェンド/神話=専用名 (原作通り)。
     */
    public static net.minecraft.world.item.ItemStack makeWeapon(
            GameData.WeaponStyle style, net.minecraft.util.RandomSource rand,
            int luck, int floor, double bonus) {
        var stack = new net.minecraft.world.item.ItemStack(ModItems.itemOf(style));
        String rarity = GameData.rollRarity(rand::nextDouble, luck, bonus, floor);
        String trait = null;
        if (!rarity.equals("normal") && !rarity.equals("magic")) {
            trait = TRAITS[rand.nextInt(TRAITS.length)].id();
        } else if (rand.nextFloat() < (rarity.equals("magic") ? 0.3f : 0.1f)) {
            trait = TRAITS[rand.nextInt(TRAITS.length)].id();
        }
        // 呪い: レア以上で12%。弱点1つと引き換えに攻撃+24% (原作の特性2底上げに対応)
        String curseKey = null;
        double curseVal = 0;
        if (GameData.rollCursed(rand::nextDouble, rarity)) {
            var mod = GameData.rollCursedMod(rand::nextDouble);
            curseKey = mod.key();
            curseVal = GameData.rollCursedValue(rand::nextDouble, mod);
        }
        // 接辞
        int[] range = GameData.affixCountRange(rarity);
        int nAffix = range[0] + rand.nextInt(range[1] - range[0] + 1);
        var affixes = GameData.rollAffixes(rand::nextDouble, rarity, nAffix);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("abyss_rarity", rarity);
        if (trait != null) tag.putString("abyss_trait", trait);
        if (curseKey != null) {
            tag.putString("abyss_curse", curseKey);
            tag.putDouble("abyss_curse_val", curseVal);
        }
        if (!affixes.isEmpty()) {
            var sb = new StringBuilder();
            for (var af : affixes) {
                if (!sb.isEmpty()) sb.append(";");
                sb.append(af.key()).append(":").append(af.value());
            }
            tag.putString("abyss_affixes", sb.toString());
        }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        // 命名 (原作 genItem)
        String disp = switch (rarity) {
            case "mythic" -> GameData.mythicFor(rand::nextDouble);
            case "legendary" -> GameData.legendFor(rand::nextDouble);
            case "rare" -> GameData.prefixFor(rand::nextDouble) + style.weaponName;
            case "magic" -> GameData.prefixFor(rand::nextDouble) + style.weaponName
                    + "・" + GameData.suffixFor(rand::nextDouble);
            default -> style.weaponName;
        };
        String name = (curseKey != null ? "§5呪縛の" : "") + rarityColor(rarity) + disp;
        if (trait != null) name += "〔" + traitName(trait) + "〕";
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(name));
        return stack;
    }

    /** 深淵防具の部位名 */
    public static String armorBaseName(net.minecraft.world.item.Item item) {
        if (item == ModItems.ABYSS_HELM.get()) return "深淵兜";
        if (item == ModItems.ABYSS_CHEST.get()) return "深淵鎧";
        if (item == ModItems.ABYSS_LEGS.get()) return "深淵脚";
        if (item == ModItems.ABYSS_BOOTS.get()) return "深淵靴";
        return null;
    }

    /**
     * ランダム防具生成 (武器 makeWeapon の防具版)。
     * 希少度・呪い・接辞・命名は武器と同式。特性は付かない (原作通り武器専用)。
     * piece が null なら4部位からランダム。
     */
    public static net.minecraft.world.item.ItemStack makeArmor(
            net.minecraft.world.item.Item piece, net.minecraft.util.RandomSource rand,
            int luck, int floor, double bonus) {
        var pieces = new net.minecraft.world.item.Item[]{
                ModItems.ABYSS_HELM.get(), ModItems.ABYSS_CHEST.get(),
                ModItems.ABYSS_LEGS.get(), ModItems.ABYSS_BOOTS.get()};
        var item = piece != null ? piece : pieces[rand.nextInt(pieces.length)];
        var stack = new net.minecraft.world.item.ItemStack(item);
        String rarity = GameData.rollRarity(rand::nextDouble, luck, bonus, floor);
        String curseKey = null;
        double curseVal = 0;
        if (GameData.rollCursed(rand::nextDouble, rarity)) {
            var mod = GameData.rollCursedMod(rand::nextDouble);
            curseKey = mod.key();
            curseVal = GameData.rollCursedValue(rand::nextDouble, mod);
        }
        int[] range = GameData.affixCountRange(rarity);
        int nAffix = range[0] + rand.nextInt(range[1] - range[0] + 1);
        var affixes = GameData.rollAffixes(rand::nextDouble, rarity, nAffix, GameData.ARMOR_AFFIXES);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("abyss_rarity", rarity);
        if (curseKey != null) {
            tag.putString("abyss_curse", curseKey);
            tag.putDouble("abyss_curse_val", curseVal);
        }
        if (!affixes.isEmpty()) {
            var sb = new StringBuilder();
            for (var af : affixes) {
                if (!sb.isEmpty()) sb.append(";");
                sb.append(af.key()).append(":").append(af.value());
            }
            tag.putString("abyss_affixes", sb.toString());
        }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        String base = armorBaseName(item);
        String disp = switch (rarity) {
            case "mythic" -> GameData.mythicFor(rand::nextDouble);
            case "legendary" -> GameData.legendFor(rand::nextDouble);
            case "rare" -> GameData.prefixFor(rand::nextDouble) + base;
            case "magic" -> GameData.prefixFor(rand::nextDouble) + base
                    + "・" + GameData.suffixFor(rand::nextDouble);
            default -> base;
        };
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal((curseKey != null ? "§5呪縛の" : "") + rarityColor(rarity) + disp));
        return stack;
    }

    /** 武器or防具のランダム装備 (雑魚ドロップ用。武器65%・防具35%) */
    public static net.minecraft.world.item.ItemStack makeEquipment(
            net.minecraft.util.RandomSource rand, int luck, int floor, double bonus) {
        if (rand.nextDouble() < 0.35) {
            return makeArmor(null, rand, luck, floor, bonus);
        }
        var styles = GameData.WeaponStyle.values();
        return makeWeapon(styles[rand.nextInt(styles.length)], rand, luck, floor, bonus);
    }

    /** 接辞キー -> 日本語表示 (原作 STAT_META.label) */
    private static final java.util.Map<String, String> AFFIX_LABEL = java.util.Map.ofEntries(
            java.util.Map.entry("damageFlat", "攻撃力"), java.util.Map.entry("damagePct", "攻撃力"),
            java.util.Map.entry("maxHpFlat", "最大HP"), java.util.Map.entry("maxHpPct", "最大HP"),
            java.util.Map.entry("attackSpeedPct", "攻撃速度"), java.util.Map.entry("critPct", "会心率"),
            java.util.Map.entry("critDmgPct", "会心威力"), java.util.Map.entry("moveSpeedPct", "移動速度"),
            java.util.Map.entry("armorPct", "軽減"), java.util.Map.entry("areaPct", "範囲"),
            java.util.Map.entry("lifestealPct", "吸収"), java.util.Map.entry("regenFlat", "再生"),
            java.util.Map.entry("luckFlat", "運"), java.util.Map.entry("energyPct", "魂"),
            java.util.Map.entry("pierce", "貫通"), java.util.Map.entry("projectiles", "追加弾"),
            java.util.Map.entry("chain", "連鎖"), java.util.Map.entry("explodePct", "爆裂"),
            java.util.Map.entry("freezePct", "凍結"));

    /** 武器・防具のツールチップ追記 (接辞・特性・奥義・使い方) */
    public static void appendGearLore(net.minecraft.world.item.ItemStack stack,
                                      java.util.function.Consumer<Component> out, boolean isWeapon) {
        var style = ModItems.styleOf(stack.getItem());
        if (isWeapon && style != null) {
            out.accept(Component.literal("§d奥義「" + style.artName + "」: " + style.artDesc));
            out.accept(Component.literal("§7Rキーで発動 (魂ゲージ100で可)"));
        } else if (!isWeapon) {
            out.accept(Component.literal("§7右クリックで装備。接辞は武器と合算"));
        }
        String trait = traitOf(stack);
        if (trait != null) {
            out.accept(Component.literal("§6特性〔" + traitName(trait) + "〕"));
        }
        for (var e : affixMap(stack).entrySet()) {
            String label = AFFIX_LABEL.getOrDefault(e.getKey(), e.getKey());
            double v = e.getValue();
            String num = (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            out.accept(Component.literal("§b+" + num + " " + label));
        }
        String curse = curseOf(stack);
        if (curse != null) {
            out.accept(Component.literal("§5呪い: " + AFFIX_LABEL.getOrDefault(curse, curse)
                    + " に弱点 (恩寵「呪喰らい」で無効化)"));
        }
        int forge = forgeLv(stack);
        if (forge > 0) out.accept(Component.literal("§b鍛造+" + forge));
        out.accept(Component.literal("§8威力 " + powerOf(stack)));
        if (isWeapon) {
            out.accept(Component.literal("§8/abyss compare・/abyss merge で厳選・合成"));
        }
    }

    /** 武器の呪い種別 (なければ null)。原作 CURSED_MODS のキー */
    public static String curseOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        String id = data.copyTag().getString("abyss_curse").orElse("");
        return id.isEmpty() ? null : id;
    }

    public static double curseValueOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        return data.copyTag().getDouble("abyss_curse_val").orElse(0.0);
    }

    /**
     * 呪い武器の攻撃倍率。弱点と引き換えに +24%。
     * 恩寵「呪喰らい」所持時は弱点を無効化し効果2倍 (+48%)。
     */
    public static double curseMultOf(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (curseOf(stack) == null) return 1.0;
        return boonLv(player, "curse_eater") > 0 ? 1.48 : 1.24;
    }

    /** 呪いの弱点が有効か (呪喰らいで無効化される) */
    public static boolean curseActive(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        return curseOf(stack) != null && boonLv(player, "curse_eater") == 0;
    }

    /** 会心率 (原作crit + 幸運0.5%/点 + 心眼+5%/Lv + 深淵の目+12%) */
    public static double critOf(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        // 幸運(運+7/点)は素材・ドロップ側で使う. 会心は職業値+心眼の刻印のみ
        double crit = cls.crit + boonLv(player, "crit") * 0.05
                + wbonusOf(player).critPct() / 100.0 + affixOf(player, "critPct") / 100.0;
        if (passivesOf(player).contains("abyss_eye")) crit += 0.12;
        return Math.min(0.95, crit);
    }

    /** 会心威力 (原作2.0倍 + 致命の刻印+45%/Lv) */
    public static double critDamageMult(ServerPlayer player) {
        return 2.0 + boonLv(player, "critdmg") * 0.45
                + wbonusOf(player).critDmgPct() / 100.0 + affixOf(player, "critDmgPct") / 100.0;
    }

    /** 攻撃範囲 (恩寵「拡大の刻印」+22%/Lv。奥義・祝福の半径に適用) */
    public static double areaMult(ServerPlayer player) {
        return (1 + boonLv(player, "area") * 0.22)
                * (1 + wbonusOf(player).areaPct() / 100.0)
                * (1 + affixOf(player, "areaPct") / 100.0);
    }

    /** 運 (幸運+7/点 + 知識の渇望+20/Lv + 呪いluckFlat) */
    public static int luckOf(ServerPlayer player) {
        int luck = GameData.luckOf(attrsOf(player)[4]) + boonLv(player, "xp") * 20
                + (int) affixOf(player, "luckFlat");
        var held = player.getMainHandItem();
        if (curseActive(player, held) && "luckFlat".equals(curseOf(held))) {
            luck += (int) curseValueOf(held);
        }
        // 祭壇「試練」: 運+100
        if ("scourge".equals(activeShrine(player))) luck += 100;
        return luck;
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
        applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()));
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

    /** 奥義・必殺の威力倍率 (武の祝福+12%/Lv) */
    public static double artMultOf(ServerPlayer player) {
        return 1 + boonLv(player, "art") * 0.12;
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
        applyClass(player, cls, level);
        buildArena(player);
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
            player.sendSystemMessage(Component.literal("§f5. レベルアップで祝福3択・属性1点。5層ごとに層主が待つ"));
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
        player.getInventory().add(makeWeapon(styles[player.getRandom().nextInt(styles.length)],
                player.getRandom(), luckOf(player), 25, 0));
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
        clearArenaBarriers(player);
    }

    /**
     * リタイア: 出撃を中断し、アリーナのバリアを除去して残存MOBを消す。
     * 装備・欠片は保持される (原作の帰還に相当)。
     */
    public static void retire(ServerPlayer player) {
        clearArenaBarriers(player);
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
        buildArena(player);
        double hpMult = GameData.enemyHpMult(sigilsOf(player)) * diffHpMult(player);
        double dmgMult = GameData.enemyDmgMult(sigilsOf(player)) * diffDmgMult(player);
        Monster boss = ModEntities.ZERO.get().create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("true boss create failed");
        BlockPos bp = player.blockPosition();
        int zy = safeSpawnY(level, bp.getX(), bp.getZ() - 10, bp.getY() + 1);
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
        int my = safeSpawnY(level, p.getX(), p.getZ(), p.getY());
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
        var stack = makeWeapon(style, player.getRandom());
        var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putString("abyss_rarity", "mythic");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§d神話の" + style.weaponName));
        player.getInventory().add(stack);
        sendSoulSummary(player, true);
        CombatHandler.grantAch(player, "true-clear", "虚無を_zeroに還した者", 32);
        player.sendSystemMessage(Component.literal("§c終焉の座が開いた… /abyss pinnacle で《タルタロス・オメガ》に挑め"));
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        clearArenaBarriers(player);
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
        buildArena(player);
        var def = GameData.bossForFloor(GameData.PINNACLE_FLOOR);
        double hpMult = GameData.enemyHpMult(sigilsOf(player)) * diffHpMult(player);
        double dmgMult = GameData.enemyDmgMult(sigilsOf(player)) * diffDmgMult(player);
        Monster boss = ModEntities.OMEGA.get().create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("pinnacle boss create failed");
        BlockPos bp2 = player.blockPosition();
        int oy = safeSpawnY(level, bp2.getX(), bp2.getZ() - 10, bp2.getY() + 1);
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
            var stack = makeWeapon(style, player.getRandom());
            var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            tag.putString("abyss_rarity", "mythic");
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("§d神話の" + style.weaponName));
            player.getInventory().add(stack);
        }
        var a = GameData.achievementById("pinnacle-god");
        CombatHandler.grantAch(player, "pinnacle-god", a.title(), GameData.shardsForReward(a.reward()));
        player.setData(ModAttachments.FLOOR.get(), 1);
        player.setData(ModAttachments.FLOOR_KILLS.get(), 0);
        player.setData(ModAttachments.PORTAL_OPEN.get(), 0);
        player.setData(ModAttachments.RUN_LOCK.get(), 0);
        clearArenaBarriers(player);
    }

    /** 超越者 (原作 b_ascension・28層。無限深淵で到達する裏決戦) */
    public static void ascensionVictory(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§e★ 超越者を越えた！深淵の果てに名を刻んだ！ ★"));
        var styles = GameData.WeaponStyle.values();
        for (int i = 0; i < 2; i++) {
            var style = styles[player.getRandom().nextInt(styles.length)];
            var stack = makeWeapon(style, player.getRandom());
            var tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            tag.putString("abyss_rarity", "mythic");
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("§d神話の" + style.weaponName));
            player.getInventory().add(stack);
        }
        int shards = materialCount(player, 32);
        if (shards > 0) {
            player.getInventory().add(new net.minecraft.world.item.ItemStack(
                    ModItems.SOUL_SHARD.get(), shards));
        }
        sendSoulSummary(player, true);
        clearArenaBarriers(player);
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

    /** アリーナ半径 (MOB収容の境界も兼ねる) — 上の ARENA_R を参照 */
    private static final int WALL_H = 5;

    /** 区域ごとの床ブロック (見た目で深層帯が分かる) */
    private static net.minecraft.world.level.block.Block floorBlockFor(String zoneId) {
        return switch (zoneId) {
            case "ossuary" -> Blocks.BONE_BLOCK;
            case "forge" -> Blocks.BLACKSTONE;
            case "glacier" -> Blocks.PACKED_ICE;
            case "voidheart" -> Blocks.OBSIDIAN;
            case "nihil" -> Blocks.BEDROCK;
            default -> Blocks.STONE_BRICKS;
        };
    }

    /** 区域ごとの障害物ブロック */
    private static net.minecraft.world.level.block.Block trimBlockFor(String zoneId) {
        return switch (zoneId) {
            case "ossuary" -> Blocks.QUARTZ_BLOCK;
            case "forge" -> Blocks.MAGMA_BLOCK;
            case "glacier" -> Blocks.BLUE_ICE;
            case "voidheart" -> Blocks.CRYING_OBSIDIAN;
            case "nihil" -> Blocks.OBSIDIAN;
            default -> Blocks.COBBLESTONE;
        };
    }

    /**
     * アリーナ構築: 整地 (埋没防止)・区域床・ランダム障害物・5段塀・バリア天井。
     * 前回のアリーナのバリアは除去する。
     */
    private static void buildArena(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos old = arenaCenterOf(player);
        BlockPos center = player.blockPosition();
        int r = ARENA_R;
        if (old != null && !old.equals(center)) clearBarriers(level, old);
        player.setData(ModAttachments.ARENA.get(),
                center.getX() + "," + center.getY() + "," + center.getZ());
        int floor = Math.max(1, player.getData(ModAttachments.FLOOR.get()));
        String zone = GameData.zoneForFloor(floor).id();
        var floorBlock = floorBlockFor(zone);
        var trim = trimBlockFor(zone);
        var rand = player.getRandom();
        // 床
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                level.setBlock(center.offset(x, -1, z), floorBlock.defaultBlockState(), 3);
            }
        }
        // 整地: 場内の地形・樹木を除去 (埋没防止。基盤は残す)
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                for (int y = 0; y <= 9; y++) {
                    BlockPos p = center.offset(x, y, z);
                    var st = level.getBlockState(p);
                    if (!st.isAir() && !st.is(Blocks.BEDROCK)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // ランダム障害物: 柱・岩塊・棘を 5〜7基 (中央4・湧き輪13〜15を避ける)
        int nFeat = 5 + rand.nextInt(3);
        int placed = 0;
        for (int tries = 0; tries < 40 && placed < nFeat; tries++) {
            double ang = rand.nextDouble() * Math.PI * 2;
            double dist = 5 + rand.nextDouble() * 7;
            if (dist > 12.5 && dist < 15.5) continue;
            int fx = center.getX() + (int) Math.round(Math.cos(ang) * dist);
            int fz = center.getZ() + (int) Math.round(Math.sin(ang) * dist);
            int kind = rand.nextInt(3);
            if (kind == 0) {
                int h = 2 + rand.nextInt(3);
                for (int y = 0; y < h; y++) {
                    level.setBlock(new BlockPos(fx, center.getY() + y, fz),
                            (y == h - 1 ? trim : Blocks.COBBLESTONE).defaultBlockState(), 3);
                }
            } else if (kind == 1) {
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        level.setBlock(new BlockPos(fx + dx, center.getY(), fz + dz),
                                Blocks.COBBLESTONE.defaultBlockState(), 3);
                        if (rand.nextBoolean()) {
                            level.setBlock(new BlockPos(fx + dx, center.getY() + 1, fz + dz),
                                    trim.defaultBlockState(), 3);
                        }
                    }
                }
            } else {
                level.setBlock(new BlockPos(fx, center.getY(), fz),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                level.setBlock(new BlockPos(fx, center.getY() + 1, fz),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            }
            placed++;
        }
        // 塀は5段 (ジャンプ・ノックバック越えを防ぐ。飛行等の抜けは収容処理で戻す)
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= (r - 2) * (r - 2)) continue;
                if (x * x + z * z > r * r) continue;
                for (int y = 0; y < WALL_H; y++) {
                    level.setBlock(center.offset(x, y, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                }
            }
        }
        // バリア: 塀上〜天井を不可視ブロックで封鎖 (リタイア・終了まで維持)
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d2 = x * x + z * z;
                if (d2 > r * r) continue;
                if (d2 > (r - 2) * (r - 2)) {
                    for (int y = WALL_H; y <= WALL_H + 5; y++) {
                        level.setBlock(center.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), 3);
                    }
                } else if (d2 <= (r - 2) * (r - 2) && Math.abs(x) + Math.abs(z) > 0) {
                    // 天井 (中央1マスは開けて光を入れる)
                    if ((x * x + z * z) % 7 == 0) continue;
                    level.setBlock(center.offset(x, WALL_H + 5, z), Blocks.BARRIER.defaultBlockState(), 3);
                }
            }
        }
        player.displayClientMessage(Component.literal("§7試練のアリーナを展開した (周囲を整地)"), false);
    }

    /** 前回アリーナのバリアを除去 (再出撃・終了時) */
    public static void clearBarriers(ServerLevel level, BlockPos center) {
        int r = ARENA_R;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d2 = x * x + z * z;
                if (d2 > r * r) continue;
                if (d2 > (r - 2) * (r - 2)) {
                    for (int y = WALL_H; y <= WALL_H + 5; y++) {
                        BlockPos p = center.offset(x, y, z);
                        if (level.getBlockState(p).is(Blocks.BARRIER)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                } else {
                    BlockPos p = center.offset(x, WALL_H + 5, z);
                    if (level.getBlockState(p).is(Blocks.BARRIER)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /** 現在のアリーナのバリアを除去 (リタイア・勝利時) */
    public static void clearArenaBarriers(ServerPlayer player) {
        BlockPos center = arenaCenterOf(player);
        if (center != null && player.level() instanceof ServerLevel level) {
            clearBarriers(level, center);
        }
    }

    /** 安全な湧きY: 上から走査し、固体の上の3マス空気を探す (埋没防止) */
    public static int safeSpawnY(ServerLevel level, int x, int z, int baseY) {
        for (int y = baseY + 12; y >= baseY - 10; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).isAir()
                    && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.above().above()).isAir()
                    && !level.getBlockState(p.below()).isAir()
                    && level.getBlockState(p.below()).getFluidState().isEmpty()) {
                return y;
            }
        }
        return baseY;
    }

    /** 保存されたアリーナ中心 (未出撃なら null) */
    public static BlockPos arenaCenterOf(ServerPlayer player) {
        String raw = player.getData(ModAttachments.ARENA.get());
        if (raw == null || raw.isBlank()) return null;
        String[] p = raw.split(",");
        if (p.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException ex) {
            return null;
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
            int sy = safeSpawnY(level, sx, sz, center.getY() + 1);
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
                int sy = safeSpawnY(level, center.getX(), center.getZ() + 10, center.getY() + 1);
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
        placeHazards(player, level, center, floor);
    }

    /** 層主の出現 (原作 BOSSES の名前・能力で) */
    private static void spawnFloorBoss(ServerPlayer player, ServerLevel level, BlockPos center,
                                       int floor, Enemies.Scale scale) {
        GameData.BossDef def = GameData.bossForFloor(floor);
        String bossName = def != null ? def.name() : "層主 第" + floor + "層";
        Mob boss = (Mob) Enemies.bossMobFor(floor).create(level, EntitySpawnReason.EVENT);
        if (boss == null) throw new IllegalStateException("boss create failed");
        int sy = safeSpawnY(level, center.getX(), center.getZ() - 10, center.getY() + 1);
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
        int sy = safeSpawnY(level, center.getX(), center.getZ() - 12, center.getY() + 1);
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

    /* ---------------- 罠・破壊物 ---------------- */

    /** 5層〜罠 (針・岩) / 3層〜破壊物を配置 */
    private static void placeHazards(ServerPlayer player, ServerLevel level, BlockPos center, int floor) {
        var rand = player.getRandom();
        if (floor >= 5) {
            int n = Math.min(6, 2 + floor / 8 + GameData.zoneForFloor(floor).hazards());
            var sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                double ang = rand.nextDouble() * Math.PI * 2;
                double dist = 6 + rand.nextDouble() * 10;
                int x = center.getX() + (int) (Math.cos(ang) * dist);
                int z = center.getZ() + (int) (Math.sin(ang) * dist);
                int y = center.getY();
                // 原作 traps.ts: spike (針・小範囲) / rock (岩・大範囲)
                String kind = rand.nextFloat() < 0.5f ? "spike" : "rock";
                if (!sb.isEmpty()) sb.append(";");
                sb.append(kind).append(",").append(x).append(",").append(y).append(",").append(z);
                var puff = "spike".equals(kind) ? ParticleTypes.CRIT : ParticleTypes.SMOKE;
                level.sendParticles(puff, x + 0.5, y + 0.2, z + 0.5, 4, 0.3, 0.2, 0.3, 0.01);
            }
            player.setData(ModAttachments.TRAPS.get(), sb.toString());
        }
        if (floor >= 3) {
            String[] kinds = {"red", "blue", "shrine"};
            var sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                double ang = rand.nextDouble() * Math.PI * 2;
                double dist = 8 + rand.nextDouble() * 6;
                BlockPos p = center.offset((int) (Math.cos(ang) * dist), 0, (int) (Math.sin(ang) * dist));
                String kind = kinds[rand.nextInt(kinds.length)];
                var state = switch (kind) {
                    case "red" -> Blocks.RED_STAINED_GLASS.defaultBlockState();
                    case "blue" -> Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                    default -> Blocks.ENCHANTING_TABLE.defaultBlockState();
                };
                level.setBlock(p, state, 3);
                if (!sb.isEmpty()) sb.append(";");
                sb.append(kind).append(",").append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ());
            }
            player.setData(ModAttachments.DESTRUCT.get(), sb.toString());
        }
    }

    /** 罠チェック (10tickごと)。原作 traps.ts: spike=針・小範囲速発 / rock=岩・大範囲1秒予兆 */
    public static void tickTraps(ServerPlayer player) {
        String raw = player.getData(ModAttachments.TRAPS.get());
        if (raw == null || raw.isBlank()) return;
        var level = (ServerLevel) player.level();
        var keep = new java.util.ArrayList<String>();
        boolean changed = false;
        for (String t : raw.split(";")) {
            String[] p = t.split(",");
            // 新形式 kind,x,y,z / 旧形式 x,y,z (岩として扱う)
            String kind = "rock";
            int off = 0;
            if (p.length == 4) {
                kind = p[0];
                off = 1;
            } else if (p.length != 3) {
                continue;
            }
            final String fkind = kind;
            try {
                double x = Double.parseDouble(p[off]) + 0.5;
                double y = Double.parseDouble(p[off + 1]);
                double z = Double.parseDouble(p[off + 2]) + 0.5;
                double d = Math.hypot(player.getX() - x, player.getZ() - z);
                if (d < 2.5 && Math.abs(player.getY() - y) < 3) {
                    var at = new net.minecraft.world.phys.Vec3(x, y + 1, z);
                    if ("spike".equals(fkind)) {
                        // 針罠: 短い予兆 (0.5秒) の後に小範囲の直撃 + 鈍足
                        for (int k = 0; k < 2; k++) {
                            CombatHandler.after(5 * k, () -> level.sendParticles(ParticleTypes.CRIT,
                                    x, y + 0.2, z, 8, 0.8, 0.1, 0.8, 0.02));
                        }
                        CombatHandler.after(10, () -> {
                            if (!player.isAlive()) return;
                            player.hurtServer(level, level.damageSources().cactus(), 6f);
                            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
                        });
                        player.sendSystemMessage(Component.literal("§e針罠！"));
                    } else {
                        // 岩罠: 予兆 (約1秒) を経て範囲爆発. 予兆の間は煙を出す
                        for (int k = 0; k < 4; k++) {
                            CombatHandler.after(5 * k, () -> level.sendParticles(ParticleTypes.SMOKE,
                                    x, y + 0.2, z, 10, 1.0, 0.1, 1.0, 0.02));
                        }
                        CombatHandler.after(20, () -> WeaponArts.trapBlast(level, player, at));
                        player.sendSystemMessage(Component.literal("§c岩罠が起動… 1秒後に爆発！"));
                    }
                    changed = true;
                } else {
                    keep.add(t);
                    if (d < 8) {
                        var puff = "spike".equals(fkind) ? ParticleTypes.CRIT : ParticleTypes.SMOKE;
                        level.sendParticles(puff, x, y + 0.2, z, 1, 0.2, 0.1, 0.2, 0.01);
                    }
                }
            } catch (NumberFormatException ignored) {
                changed = true;
            }
        }
        if (changed) player.setData(ModAttachments.TRAPS.get(), String.join(";", keep));
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
            int price = Math.max(2, (powerOf(makeWeapon(st, rand, luckOf(player), floor, 0)) + 7) / 15);
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
        var drop = makeWeapon(st, player.getRandom(), luckOf(player), floor + 1,
                bossFloor ? 130 : 65);
        player.getInventory().add(drop);
        player.sendSystemMessage(Component.literal(
                "§b宝箱を開けた: " + drop.getHoverName().getString()));
        if ("mythic".equals(rarityOf(drop)) || "legendary".equals(rarityOf(drop))) {
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
        int lv = forgeLv(main);
        if (lv >= 5) {
            player.sendSystemMessage(Component.literal("§cこの武器は鍛造MAX (覚醒)"));
            return;
        }
        int cost = 3 + lv * 2 + (powerOf(main) + powerOf(sub) + 12) / 25;
        if (countShards(player) < cost) {
            player.sendSystemMessage(Component.literal("§c欠片" + cost + "個が足りない (所持" + countShards(player) + ")"));
            return;
        }
        // 原作の素材種別コスト (希少度の高い方で判定)。不足時は欠片を消費しない
        var order = java.util.List.of("normal", "magic", "rare", "legendary", "mythic");
        String top = order.indexOf(rarityOf(sub)) > order.indexOf(rarityOf(main))
                ? rarityOf(sub) : rarityOf(main);
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
        var ma = affixMap(main);
        var sa = affixMap(sub);
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
        String mr = rarityOf(main);
        String or = rarityOf(sub);
        if (order.indexOf(or) > order.indexOf(mr)) tag.putString("abyss_rarity", or);
        String subTrait = traitOf(sub);
        if (traitOf(main) == null && subTrait != null) tag.putString("abyss_trait", subTrait);
        main.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        setForgeLv(main, lv + 1);
        String nowTrait = traitOf(main);
        String hover = main.getHoverName().getString().replaceAll("^[§0-9a-f]+(覚醒|神錬)・", "");
        String lead = "";
        var leadM = java.util.regex.Pattern.compile("^(§[0-9a-f])+").matcher(hover);
        if (leadM.find()) lead = leadM.group();
        String body = hover.substring(lead.length());
        if (nowTrait != null && !body.contains("〔")) body += "〔" + traitName(nowTrait) + "〕";
        String prefix = lv + 1 >= 5 ? "§d覚醒・" : lv + 1 >= 3 ? "§b神錬・" : "";
        String newName = (prefix.isEmpty() ? lead : prefix) + body;
        if (!newName.equals(main.getHoverName().getString())) {
            main.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(newName));
        }
        player.sendSystemMessage(Component.literal(
                "§b合成！ " + main.getHoverName().getString() + " 鍛造+" + (lv + 1)
                        + " 威力" + powerOf(main) + " (欠片-" + cost + ")"));
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
        player.getInventory().add(makeWeapon(st, player.getRandom(), luckOf(player),
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
                    var drop = makeWeapon(styles[player.getRandom().nextInt(styles.length)],
                            player.getRandom(), luckOf(player), floor, 0);
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
            applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()), true);
            player.displayClientMessage(Component.literal("§b防具を変更: ステータス再計算"), true);
        }
        long t = level.getGameTime();
        // 巨躯の刻印・接辞: HP回復 (1秒ごと)
        double regen = boonLv(player, "hp") * 0.6 + affixOf(player, "regenFlat");
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

    /** 装備比較GUI用: 指定武器の性能テキスト。空なら (空) 1行 */
    public static java.util.List<String> compareLines(ServerPlayer player,
                                                      net.minecraft.world.item.ItemStack stack) {
        var style = ModItems.styleOf(stack.getItem());
        boolean isArmor = ModItems.slotOf(stack.getItem()) != null;
        if (style == null && !isArmor) return java.util.List.of("(空)");
        String trait = traitOf(stack);
        String curse = curseOf(stack);
        int affixes = 0;
        for (var ak : GameData.WEAPON_AFFIXES) {
            if (affixOf(stack, ak.key()) != 0) affixes++;
        }
        var out = new java.util.ArrayList<String>();
        out.add(stack.getHoverName().getString());
        if (isArmor) {
            out.add("部位: " + ModItems.slotOf(stack.getItem()) + " 威力: " + powerOf(stack));
        } else {
            out.add(String.format("攻撃 %.1f (奥義「%s」)", attackDamageWith(player, stack), style.artName));
        }
        out.add("希少:" + rarityOf(stack) + " 特性:" + (trait != null ? traitName(trait) : "なし"));
        out.add("鍛造+" + forgeLv(stack) + " 接辞:" + affixes
                + (curse != null ? " 呪い:" + curse + curseValueOf(stack) : ""));
        return out;
    }

    /** ステータスGUI用: showStatus と同じ内容の行リスト */
    public static java.util.List<String> statusLines(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        GameData.WeaponStyle style = ModItems.styleOf(player.getMainHandItem().getItem());
        int[] a = attrsOf(player);
        var out = new java.util.ArrayList<String>();
        out.add(String.format("§6[%s %s Lv%d] §f第%d層[%s/%s]",
                cls.name, cls.title, player.getData(ModAttachments.LEVEL.get()),
                player.getData(ModAttachments.FLOOR.get()),
                player.getData(ModAttachments.MODE.get()), player.getData(ModAttachments.DIFF.get())));
        out.add(String.format("§d魂%.0f/100 §c攻撃%.1f §aHP%.0f/%.0f §e撃破%d §b奥義:%s",
                player.getData(ModAttachments.SOUL.get()),
                attackDamageOf(player),
                player.getHealth(), player.getMaxHealth(),
                player.getData(ModAttachments.KILLS.get()),
                style != null ? style.artName : "武器を持ってね"));
        out.add(String.format("§7属性 剛%d 韻%d 敏%d 忍%d 幸%d",
                a[0], a[1], a[2], a[3], a[4]));
        out.add("§7祝福 " + boonsOf(player));
        out.add("§7層主 " + player.getData(ModAttachments.BOSSES.get()));
        out.add("§7区域 " + GameData.zoneForFloor(player.getData(ModAttachments.FLOOR.get())).name()
                + " §8| §7異変 " + mutationName(player.getData(ModAttachments.MUTATION.get())));
        out.add("§7遺物 " + relicsOf(player));
        out.add("§7技能 " + passivesOf(player));
        out.add("§7実績 " + player.getData(ModAttachments.ACH.get()));
        var held = player.getMainHandItem();
        if (ModItems.styleOf(held.getItem()) != null) {
            out.addAll(compareLines(player, held));
        }
        out.add(String.format("§7スコア %d (撃破%d 与ダメ%d 最大combo%d)",
                calcScore(player, false),
                player.getData(ModAttachments.KILLS.get()),
                player.getData(ModAttachments.DMG_DEALT.get()),
                player.getData(ModAttachments.BEST_COMBO.get())));
        return out;
    }
}
