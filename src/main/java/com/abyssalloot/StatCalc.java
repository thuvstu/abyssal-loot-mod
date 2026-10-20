package com.abyssalloot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ステータス計算式・属性・汎用ボーナス集計 (RunManager から分離)。
 */
public final class StatCalc {
    private StatCalc() {}

    /* ---------------- 属性・祝福 ---------------- */

    /** 剛力,韻律,敏捷,忍耐,幸運,集中,活力,技巧,採掘,跳躍 (旧セーブは後ろを0埋め) */
    public static int[] attrsOf(ServerPlayer player) {
        int[] a = new int[10];
        try {
            String[] p = player.getData(ModAttachments.ATTRS.get()).split(",");
            for (int i = 0; i < Math.min(10, p.length); i++) a[i] = Math.max(0, Integer.parseInt(p[i].trim()));
        } catch (Exception ignored) {}
        return a;
    }

    public static void setAttrs(ServerPlayer player, int[] a) {
        var sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb.append(",");
            sb.append(i < a.length ? Math.max(0, a[i]) : 0);
        }
        player.setData(ModAttachments.ATTRS.get(), sb.toString());
        invalidateCaches(player);
    }

    /**
     * 属性の実効値 (職業適性は1.5倍)。計算式はすべてこちらを使う。
     * 節目の到達判定は投下点 (素の値) で行う。
     */
    public static double attrEff(ServerPlayer player, int idx) {
        int[] a = attrsOf(player);
        if (idx < 0 || idx >= a.length) return 0;
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        return a[idx] * (GameData.ATTR_NAMES.length > idx && cls.affinityAttr() == idx ? 1.5 : 1.0);
    }

    /** 節目ボーナスの合算 (接辞キー)。extraStat に含まれる */
    public static double attrMilestone(ServerPlayer player, String key) {
        int[] a = attrsOf(player);
        double v = 0;
        for (int i = 0; i < Math.min(a.length, GameData.ATTR_MILESTONES.size()); i++) {
            for (GameData.Mile m : GameData.ATTR_MILESTONES.get(i)) {
                if (a[i] >= m.at()) v += m.mods().getOrDefault(key, 0.0);
            }
        }
        return v;
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

    /** 属性1点配分 (成功でメッセージ・職業再適用・節目告知)。GUI/コマンド共通 */
    public static boolean addAttrPoint(ServerPlayer player, int idx) {
        if (idx < 0 || idx > 9) {
            player.sendSystemMessage(Component.literal("§c剛力/韻律/敏捷/忍耐/幸運/集中/活力/技巧/採掘/跳躍で指定"));
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
        int before = a[idx];
        a[idx]++;
        setAttrs(player, a);
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        applyClass(player, cls, level);
        var msg = new StringBuilder("§a属性配分:");
        for (int i = 0; i < 10; i++) {
            msg.append(" ").append(GameData.ATTR_SHORT[i]).append(a[i]);
            if (cls.affinityAttr() == i) msg.append("★");
        }
        msg.append(" (残").append(level - 1 - spent - 1).append("点)");
        player.sendSystemMessage(Component.literal(msg.toString()));
        if (GameData.milestoneTier(idx, a[idx]) > GameData.milestoneTier(idx, before)) {
            int next = nextMile(idx, a[idx]);
            player.sendSystemMessage(Component.literal("§d◆ 節目到達！ " + GameData.ATTR_NAMES[idx]
                    + a[idx] + "点ボーナス解放" + (a[idx] >= 30 ? " (最大)" : " (次は" + next + "点)")));
        }
        return true;
    }

    private static int nextMile(int idx, int points) {
        for (GameData.Mile m : GameData.ATTR_MILESTONES.get(idx)) {
            if (points < m.at()) return m.at();
        }
        return 30;
    }

    /** 振り直し (欠片8個)。配分を全戻しし、職業を再適用する */
    public static void respec(ServerPlayer player) {
        if (attrSpent(player) == 0) {
            player.sendSystemMessage(Component.literal("§c振り直す点が無い"));
            return;
        }
        if (!FloorEvents.takeShards(player, 8)) {
            player.sendSystemMessage(Component.literal("§c欠片8個が足りない (所持" + FloorEvents.countShards(player) + ")"));
            return;
        }
        setAttrs(player, new int[10]);
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        applyClass(player, cls, player.getData(ModAttachments.LEVEL.get()));
        player.sendSystemMessage(Component.literal("§b属性を振り直した (欠片-8)。再配分は /abyss attr かステータス画面で"));
    }

    public static void applyClass(ServerPlayer player, GameData.HeroClass cls, int level) {
        applyClass(player, cls, level, false);
    }

    /** 防具変更時の静かな再計算用 (quiet=true で告知なし) */
    public static void applyClass(ServerPlayer player, GameData.HeroClass cls, int level, boolean quiet) {
        // クラス・レベル・装備の適用はステータスの導出元が変わるため、キャッシュを先に無効化する
        invalidateCaches(player);
        player.setData(ModAttachments.HERO_CLASS.get(), cls.id);
        player.setData(ModAttachments.LEVEL.get(), level);
        var passives = RunManager.passivesOf(player);
        var curse = cursePenaltyOf(player);
        var wb = wbonusOf(player);

        applyMaxHealth(player, cls, level, passives, curse.hpPct(), wb.maxHpPct());
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(GameData.heroDamage(cls, level));
        applyMovementSpeed(player, cls, passives, curse.movePct(), wb.moveSpeedPct());
        applyAttackSpeed(player, cls);
        applyReach(player);
        if (!quiet) {
            player.displayClientMessage(Component.literal(
                    "§6職業: " + cls.name + "「" + cls.title + "」 必殺技: " + cls.skillName()), false);
        }
    }

    /** 呪い武器の弱点 (主手持ちのみ。呪喰らいで無効化。攻速は attackSpeedPctOf 側で処理) */
    private static CursePenalty cursePenaltyOf(ServerPlayer player) {
        var held = player.getMainHandItem();
        double curseHp = 0, curseMove = 0;
        if (GearData.curseActive(player, held)) {
            String key = GearData.curseOf(held);
            double val = GearData.curseValueOf(held);
            if ("maxHpPct".equals(key)) curseHp = val;
            else if ("moveSpeedPct".equals(key)) curseMove = val;
        }
        return new CursePenalty(curseHp, curseMove);
    }

    private record CursePenalty(double hpPct, double movePct) {}

    /** 最大HP (原作: 剛力+3%/点, 忍耐+4%/点, 幸運=会心相当 などの補正を含む) */
    private static void applyMaxHealth(ServerPlayer player, GameData.HeroClass cls, int level,
                                       List<String> passives, double curseHp, double wbMaxHpPct) {
        double maxHp = (GameData.heroMaxHp(cls, level) + affixOf(player, "maxHpFlat") + extraStat(player, "maxHpFlat"))
                * (1 + attrEff(player, 3) * 0.04) * (1 + RunManager.boonLv(player, "hp") * 0.18)
                * (1 + wbMaxHpPct / 100.0) * (1 + affixPlusExtra(player, "maxHpPct") / 100.0)
                * (1 + GameData.passiveMaxHpPct(passives) / 100.0)
                * (1 + player.getData(ModAttachments.HARVEST.get()) * 0.01)
                * (1 + curseHp / 100.0);
        if (RunManager.boonLv(player, "curse_eater") > 0) {
            maxHp *= 1 + GameData.curseEaterBonusMaxHp(passives) / 100.0;
        }
        // 技能「深淵の目」: 最大HP -25 (固定)
        if (passives.contains("abyss_eye")) maxHp = Math.max(1, maxHp - 25);
        // 祭壇「黄金」: 最大HP-20
        if ("gold".equals(RunManager.activeShrine(player))) maxHp = Math.max(1, maxHp - 20);
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            // 現在HPの割合を維持 (装備の着脱で全快・激減しないように)
            double ratio = player.getHealth() / Math.max(1.0, player.getMaxHealth());
            // 1024 = バニラ属性の上限。長期 endless で Lv84+ (剣聖) に達するとクランプされる
            attr.setBaseValue(Math.min(1024, maxHp));
            player.setHealth((float) Math.min(attr.getValue(), Math.max(1.0, attr.getValue() * ratio)));
        }
    }

    /** 移動速度 (疾走の刻印・靴・兜スキル・技能・呪い) */
    private static void applyMovementSpeed(ServerPlayer player, GameData.HeroClass cls,
                                           List<String> passives, double curseMove, double wbMovePct) {
        var spd = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd == null) return;
        double base = cls.mcMoveSpeed();
        // 疾走の刻印 +12%/Lv (原作 b_speed)。回避短縮は dash 側で適用
        double movePct = RunManager.boonLv(player, "speed") * 12 + curseMove
                + wbMovePct + affixOf(player, "moveSpeedPct") + extraStat(player, "moveSpeedPct");
        // 防具スロット能力: 靴=疾走 (常時移動+3%)
        if (ModItems.slotOf(player.getItemBySlot(EquipmentSlot.FEET).getItem()) != null) {
            movePct += 3;
        }
        // 防具スキル「迅径の兜」: 常時移動 +8%
        if ("h_swift".equals(GearData.skillOf(player.getItemBySlot(EquipmentSlot.HEAD)))) {
            movePct += 8;
        }
        if (passives.contains("swift")) movePct += 25;
        if (passives.contains("slow_burn")) movePct -= 12;
        spd.setBaseValue(base * (1 + attrEff(player, 2) * 0.015) * (1 + movePct / 100.0));
    }

    /** 攻撃速度 (韻律・装備補正・収穫スタック) */
    private static void applyAttackSpeed(ServerPlayer player, GameData.HeroClass cls) {
        var atkSpd = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpd == null) return;
        double asPct = attackSpeedPctOf(player);
        // 鎌の収穫スタック (+6%/枚) も含める (refreshAttackSpeed と同一式)
        atkSpd.setBaseValue(cls.attackSpeed
                * (1 + attrEff(player, 1) * 0.02)
                * (1 + asPct / 100.0)
                * (1 + player.getData(ModAttachments.SCYTHE.get()) * 0.06));
    }

    /** 攻撃射程 (バニラの ENTITY_INTERACTION_RANGE。素値3m + 射程0.6m/点, 上限8m) */
    private static void applyReach(ServerPlayer player) {
        var reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            reach.setBaseValue(Math.min(8.0, 3.0 + reachOf(player) * 0.6));
        }
    }


    /** 攻速の合計% (迅風・武器クラス・接辞・技能・呪い)。applyClass と共有 */
    public static double attackSpeedPctOf(ServerPlayer player) {
        var held = player.getMainHandItem();
        double curseAspd = 0;
        if (GearData.curseActive(player, held) && "attackSpeedPct".equals(GearData.curseOf(held))) {
            curseAspd = GearData.curseValueOf(held);
        }
        double v = RunManager.boonLv(player, "as") * 16 + curseAspd
                + wbonusOf(player).attackSpeedPct() + affixPlusExtra(player, "attackSpeedPct");
        var passives = RunManager.passivesOf(player);
        if (passives.contains("clear_mind")) v -= 10;
        if (passives.contains("frostbound")) v -= 12;
        if (passives.contains("echo_strike")) v -= 15;
        return v;
    }

    /** 鎌の収穫スタックを攻速属性へ即時反映 (チャット告知なし) */
    public static void refreshAttackSpeed(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        var atkSpd = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpd != null) {
            atkSpd.setBaseValue(cls.attackSpeed
                    * (1 + attrEff(player, 1) * 0.02)
                    * (1 + attackSpeedPctOf(player) / 100.0)
                    * (1 + player.getData(ModAttachments.SCYTHE.get()) * 0.06));
        }
    }

    public static double attackDamageOf(ServerPlayer player) {
        return attackDamageWith(player, player.getMainHandItem()) * ArenaManager.arenaPowerMult(player);
    }

    /** 指定武器を持った場合の攻撃力 (装備比較GUI用)。affix/curse/forge/traitはheldで評価 */
    public static double attackDamageWith(ServerPlayer player, ItemStack held) {
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        double base = atk != null ? atk.getValue() : 10;
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        int level = player.getData(ModAttachments.LEVEL.get());
        var passives = RunManager.passivesOf(player);
        // 破壊の刻印 (b_dmg2): 固定+12/Lv (乗算の前に加算)。防具・追加効果の攻撃接辞も合算
        base += RunManager.boonLv(player, "dmg2") * 12 + GearData.affixOf(held, "damageFlat") + armorAffix(player, "damageFlat")
                + extraStat(player, "damageFlat");
        var wb = wbonusOf(held);
        double mult = damageMultiplier(player, held, level, passives, wb);
        // [基礎値] 原作ダメージをMC向けに1/2へスケール
        // (heroDamage*0.35 + 属性値*0.15 = heroDamage*0.5相当。属性値にはバフも含まれるが己係数 0.15 で寄与は小さい)
        return Math.max(1, GameData.heroDamage(cls, level) * 0.35 + base * 0.15) * mult;
    }

    /** 与ダメ倍率 (属性・恩寵・接辞・鍛造・呪い・遺物・技能・祭壇) */
    private static double damageMultiplier(ServerPlayer player, ItemStack held, int level,
                                           List<String> passives, GameData.WeaponBonus wb) {
        double mult = (1 + attrEff(player, 0) * 0.03) * (1 + RunManager.boonLv(player, "dmg") * 0.12)
                * (1 + RunManager.boonLv(player, "dmg1") * 0.18) * (1 + RunManager.boonLv(player, "dmg2") * 0.08)
                * (1 + wb.damagePct() / 100.0) * (1 + (GearData.affixOf(held, "damagePct") + armorAffix(player, "damagePct") + extraStat(player, "damagePct")) / 100.0)
                * (1 + player.getData(ModAttachments.OATH_DMG.get()) * 0.08)
                * (1 + GearData.forgeLv(held) * 0.08)
                * GearData.curseMultOf(player, held)
                * (1 + GameData.passiveDamagePct(passives) / 100.0)
                * (1 + player.getData(ModAttachments.HARVEST.get()) * 0.02);
        // 恩寵「呪喰らい」: 技能の欠点無効・正効果2倍
        if (RunManager.boonLv(player, "curse_eater") > 0) {
            mult *= 1 + GameData.curseEaterBonusDamage(passives) / 100.0;
        }
        // 遺物「呪杯」: +40% / 「双魂の盃」: 10レベルごとに+10%
        if (RunManager.relicFlagsOf(player).contains("chalice")) mult *= 1.4;
        if (RunManager.relicFlagsOf(player).contains("twin")) mult *= 1 + (level / 10) * 0.10;
        // 技能「最後の砦」: HP30%以下で+50% / 「勢い」: コンボ20以上で+35%
        double hpRatio = player.getHealth() / Math.max(1, player.getMaxHealth());
        if (passives.contains("last_stand") && hpRatio < 0.3) {
            mult *= 1.5;
        }
        if (passives.contains("momentum") && player.getData(ModAttachments.COMBO.get()) >= 20) {
            mult *= 1.35;
        }
        // 恩寵「最後の笑い」: HP30%以下で+70%/Lv / 「過剰修復」: HP割合×30%/Lv
        if (RunManager.boonLv(player, "lastlaugh") > 0 && hpRatio < 0.3) {
            mult *= 1 + 0.70 * RunManager.boonLv(player, "lastlaugh");
        }
        if (RunManager.boonLv(player, "overflow") > 0) {
            mult *= 1 + 0.30 * hpRatio * RunManager.boonLv(player, "overflow");
        }
        // 恩寵「完全主義」: 見切り後3秒間+90%/Lv
        if (RunManager.boonLv(player, "perfect") > 0
                && player.level().getGameTime() - player.getData(ModAttachments.PARRY_TICK.get()) < 60) {
            mult *= 1 + 0.90 * RunManager.boonLv(player, "perfect");
        }
        // 祭壇「怒り」: 与ダメ+60%
        if ("rage".equals(RunManager.activeShrine(player))) mult *= 1.6;
        return mult;
    }


    /* ---------------- 武器クラス・接辞 (原作 WEAPON_CLASSES / AFFIXES) ---------------- */

    /** 主手持ち武器のクラス特性 (なければゼロ) */
    public static GameData.WeaponBonus wbonusOf(ServerPlayer player) {
        return wbonusOf(player.getMainHandItem());
    }

    /** 指定スタックの武器クラス特性 (なければゼロ) */
    public static GameData.WeaponBonus wbonusOf(ItemStack stack) {
        var style = ModItems.styleOf(stack.getItem());
        return style == null ? GameData.weaponBonus("") : GameData.weaponBonus(style.id);
    }

    /** 主手持ち武器の接辞値 (なければ 0)。防具4部位の合算も含む */
    public static double affixOf(ServerPlayer player, String key) {
        return GearData.affixOf(player.getMainHandItem(), key) + armorAffix(player, key);
    }

    /** 接辞 (武器+防具) と追加コンテンツ合算の合計。10箇所以上で同じ加算パターンの統一用 */
    public static double affixPlusExtra(ServerPlayer player, String key) {
        return affixOf(player, key) + extraStat(player, key);
    }

    /** 装備中防具4部位の接辞合計 (原作の7スロット合算に相当) */
    public static double armorAffix(ServerPlayer player, String key) {
        double v = 0;
        for (var slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            v += GearData.affixMap(player.getItemBySlot(slot)).getOrDefault(key, 0.0);
        }
        return v;
    }

    /**
     * 追加コンテンツの汎用ステータス合算 (追加恩寵・追加技能・追加遺物・防具セット)。
     * すべて接辞キーで表し、各計算式に加算する。
     *
     * 呼び出し頻度が非常に高い (1ヒットで複数回) ため、同一tick内の結果をプレイヤー単位で
     * キャッシュする。添付データ・装備の変更は {@link #invalidateCaches(ServerPlayer)} で無効化する
     * (未呼び出しでも寿命は1tickなので最大1tickの遅れで自己修復する)。
     */
    public static double extraStat(ServerPlayer player, String key) {
        long t = player.level().getGameTime();
        var uuid = player.getUUID();
        var c = EXTRA_CACHE.get(uuid);
        if (c == null || c.tick() != t) {
            if (EXTRA_CACHE.size() > 256) EXTRA_CACHE.clear();
            c = new ExtraCache(t, new HashMap<>());
            EXTRA_CACHE.put(uuid, c);
        }
        Double v = c.vals().get(key);
        if (v == null) {
            v = computeExtraStat(player, key);
            c.vals().put(key, v);
        }
        return v;
    }

    private record ExtraCache(long tick, HashMap<String, Double> vals) {}

    private static final Map<UUID, ExtraCache> EXTRA_CACHE = new HashMap<>();

    /** ステータスの導出元 (祝福・技能・遺物・属性・装備) が変わったときに呼ぶ (StatCalc/RunManager の書き込み系から) */
    public static void invalidateCaches(ServerPlayer player) {
        EXTRA_CACHE.remove(player.getUUID());
        ECHO_CACHE.remove(player.getUUID());
    }

    private static double computeExtraStat(ServerPlayer player, String key) {
        double v = 0;
        for (var e : RunManager.boonsOf(player).entrySet()) {
            var m = GameData.EXTRA_BOON_MODS.get(e.getKey());
            if (m != null) v += m.getOrDefault(key, 0.0) * e.getValue();
        }
        for (String id : RunManager.passivesOf(player)) {
            var m = GameData.EXTRA_PASSIVE_MODS.get(id);
            if (m != null) v += m.getOrDefault(key, 0.0);
        }
        for (String id : RunManager.relicsOf(player)) {
            var m = GameData.EXTRA_RELIC_MODS.get(id);
            if (m != null) v += m.getOrDefault(key, 0.0);
        }
        v += armorSetBonus(player, key);
        v += attrMilestone(player, key);
        // 地脈の共鳴: 現在地のバニラバイオームが戦闘に滲む
        var echo = activeBiomeEcho(player);
        if (echo != null) v += echo.mods().getOrDefault(key, 0.0);
        return v;
    }

    /** 現在地のバイオーム共鳴 (該当ナシ = null)。1秒間キャッシュ (毎打点のバイオーム参照を省く) */
    public static GameData.BiomeEcho activeBiomeEcho(ServerPlayer player) {
        long t = player.level().getGameTime();
        var cached = ECHO_CACHE.get(player.getUUID());
        if (cached != null && cached.tick() == t) return cached.echo();
        GameData.BiomeEcho echo = null;
        try {
            var id = player.level().registryAccess()
                    .lookupOrThrow(Registries.BIOME)
                    .getKey(player.level().getBiome(player.blockPosition()).value());
            echo = GameData.biomeEchoById(id.getNamespace() + ":" + id.getPath());
        } catch (Exception ignored) {
            echo = null;
        }
        if (ECHO_CACHE.size() > 256) ECHO_CACHE.clear();
        ECHO_CACHE.put(player.getUUID(), new EchoCache(t, echo));
        return echo;
    }

    private record EchoCache(long tick, GameData.BiomeEcho echo) {}

    private static final Map<UUID, EchoCache> ECHO_CACHE = new HashMap<>();

    /** 装備中防具セットのボーナス (2部位・4部位)。setId -> 部位数で判定 */
    public static double armorSetBonus(ServerPlayer player, String key) {
        var count = new HashMap<String, Integer>();
        for (var slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            var st = player.getItemBySlot(slot);
            if (st.isEmpty()) continue;
            var data = st.getOrDefault(DataComponents.CUSTOM_DATA,
                    CustomData.EMPTY);
            String set = data.copyTag().getString("abyss_set").orElse("");
            if (!set.isEmpty()) count.merge(set, 1, Integer::sum);
        }
        double v = 0;
        for (var e : count.entrySet()) {
            var s = GameData.armorSetById(e.getKey());
            if (s == null) continue;
            if (e.getValue() >= 2) v += s.bonus2().getOrDefault(key, 0.0);
            if (e.getValue() >= 4) v += s.bonus4().getOrDefault(key, 0.0);
        }
        return v;
    }

    /** 会心率 (原作crit + 心眼の刻印+5%/Lv + 深淵の目+12%)。幸運はドロップ側で使うため不含 */
    public static double critOf(ServerPlayer player) {
        GameData.HeroClass cls = GameData.HeroClass.byId(player.getData(ModAttachments.HERO_CLASS.get()));
        // 幸運(運+7/点)は素材・ドロップ側で使う. 会心は職業値+心眼の刻印のみ
        double crit = cls.crit + RunManager.boonLv(player, "crit") * 0.05
                + wbonusOf(player).critPct() / 100.0 + affixPlusExtra(player, "critPct") / 100.0
                + attrEff(player, 5) * 0.008;
        if (RunManager.passivesOf(player).contains("abyss_eye")) crit += 0.12;
        return Math.min(0.95, crit);
    }

    /** 会心威力 (原作2.0倍 + 致命の刻印+45%/Lv) */
    public static double critDamageMult(ServerPlayer player) {
        return 2.0 + RunManager.boonLv(player, "critdmg") * 0.45
                + wbonusOf(player).critDmgPct() / 100.0 + affixPlusExtra(player, "critDmgPct") / 100.0
                + attrEff(player, 5) * 0.02;
    }

    /** 攻撃範囲 (恩寵「拡大の刻印」+22%/Lv。奥義・祝福の半径に適用) */
    public static double areaMult(ServerPlayer player) {
        return (1 + RunManager.boonLv(player, "area") * 0.22)
                * (1 + wbonusOf(player).areaPct() / 100.0)
                * (1 + affixPlusExtra(player, "areaPct") / 100.0)
                * (1 + attrEff(player, 7) * 0.015);
    }
    /** 運 (幸運+7/点 + 知識の渇望+20/Lv + 呪いluckFlat) */
    public static int luckOf(ServerPlayer player) {
        int luck = GameData.luckOf((int) attrEff(player, 4)) + RunManager.boonLv(player, "xp") * 20
                + (int) affixPlusExtra(player, "luckFlat");
        var held = player.getMainHandItem();
        if (GearData.curseActive(player, held) && "luckFlat".equals(GearData.curseOf(held))) {
            luck += (int) GearData.curseValueOf(held);
        }
        // 祭壇「試練」: 運+100
        if ("scourge".equals(RunManager.activeShrine(player))) luck += 100;
        return luck;
    }

    /** 奥義・必殺の威力倍率 (武の祝福+12%/Lv) */
    public static double artMultOf(ServerPlayer player) {
        return 1 + RunManager.boonLv(player, "art") * 0.12;
    }

    /* ---------------- 武器の搦み (射程・横斬り・連撃) ----------------
     * 出所は4系統がそれぞれ独立して積算される:
     *   1) 接辞 (reach/sweep/strikes — 武器・防具両方)
     *   2) 武器種の素値 (GameData.WEAPON_SHAPE)
     *   3) 祝福 (b_reach / b_sweep / b_barrage)
     *   4) 防具スキル (h_hawk / l_sweep / b_barrage)
     */

    /** 主手持ち武器種の形状ボーナス (なければゼロ) */
    public static GameData.ShapeBonus shapeOf(ServerPlayer player) {
        var style = ModItems.styleOf(player.getMainHandItem().getItem());
        return style == null ? null : GameData.WEAPON_SHAPE.get(style.id);
    }

    /** 攻撃射程の加算値 (+1 あたり属性+0.6m・奥義範囲+8%) */
    public static double reachOf(ServerPlayer player) {
        double v = extraStat(player, "reach");
        var shape = shapeOf(player);
        if (shape != null) v += shape.reach();
        if ("h_hawk".equals(GearData.skillOf(player.getItemBySlot(EquipmentSlot.HEAD)))) v += 1;
        return v;
    }

    /** 横斬り (薙ぎ幅) の加算値 (+1 あたり横斬りの半角+12%) */
    public static double sweepOf(ServerPlayer player) {
        double v = extraStat(player, "sweep");
        var shape = shapeOf(player);
        if (shape != null) v += shape.sweep();
        if ("l_sweep".equals(GearData.skillOf(player.getItemBySlot(EquipmentSlot.LEGS)))) v += 1;
        return v;
    }

    /** 一打の連撃 (同時に斬る隣接敵の追加数, 上限3) */
    public static int strikesOf(ServerPlayer player) {
        int v = (int) extraStat(player, "strikes");
        var shape = shapeOf(player);
        if (shape != null) v += (int) shape.strikes();
        if ("b_barrage".equals(GearData.skillOf(player.getItemBySlot(EquipmentSlot.FEET)))) v += 1;
        return Math.max(0, Math.min(3, v));
    }

    /** 奥義横薙・斬撃系の倍率 (射程) */
    public static double reachMult(ServerPlayer player) {
        return 1 + Math.min(8, reachOf(player)) * 0.08;
    }

    /** 斬撃円錐の半角 (ラジアン。基礎0.6 + 横斬り12%/点, 上限1.5) */
    public static double sweepArc(ServerPlayer player) {
        return Math.min(1.5, 0.6 * (1 + 0.12 * sweepOf(player)));
    }
}
