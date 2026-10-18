package com.abyssalloot;

import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * 原作 src/game/core.ts / trials.ts / attributes.ts / weapon-arts.ts の対応表をMCに落としたもの.
 * 数値は原作ベース値をMC向けにスケール (MCのHP20=ハート10基準ではなく、
 * MOD内では独自HPとして Attributes.MAX_HEALTH を上書きする).
 *
 * 純粋な計算式はすべてこのクラスに集約し、MC非依存のままユニットテストできるようにしている
 * (src/test/java/com/abyssalloot/GameDataTest.java).
 */
public final class GameData {
    private GameData() {}

    public enum HeroClass {
        //                 id         名前    称号        HP   HP/Lv  攻撃 攻速  会心  弾数 移動 回避CT  適性属性 技能名        小技CT倍率
        SAMURAI("samurai", "剣聖", "孤高の一刀", 94, 11, 20, 1.55f, 0.08f, 1, 250, 1.10f, 0, "絶空斬", 0.95f),
        MAGE("mage", "魔導士", "星詠みの術者", 60, 7, 16, 2.0f, 0.10f, 1, 244, 1.25f, 5, "メテオフォール", 1.20f),
        NINJA("ninja", "忍者", "影縫いの刃", 69, 8, 12, 3.1f, 0.20f, 1, 300, 0.62f, 2, "影分身乱舞", 0.80f),
        PALADIN("paladin", "聖騎士", "不滅の盾", 137, 15, 16, 1.25f, 0.05f, 1, 226, 1.50f, 3, "ジャッジメント", 1.10f),
        GUNNER("gunner", "銃魔", "鉛の嵐", 72, 9, 11, 3.4f, 0.14f, 2, 256, 1.00f, 4, "バレットヘル", 1.05f),
        BERSERKER("berserker", "狂戦士", "血路の王", 108, 13, 24, 1.30f, 0.12f, 1, 260, 1.20f, 0, "血爆", 1.00f),
        MONK("monk", "僧兵", "静寂の拳", 101, 12, 15, 2.60f, 0.10f, 1, 270, 0.90f, 1, "禅定結界", 0.90f),
        ARCHER("archer", "弓使い", "月下の狙撃手", 78, 9, 13, 2.40f, 0.16f, 3, 265, 1.00f, 5, "天矢雨", 1.15f);

        public final String id;
        public final String name;
        public final String title;
        public final double baseHp;
        public final double hpPerLevel;
        public final double baseDamage;
        public final float attackSpeed;
        public final float crit;
        public final int projectiles;
        /** 原作 moveSpeed (px/s). MC側は {@link #mcMoveSpeed()} で変換 */
        public final double moveSpeed;
        /** 原作 base.dashCd (秒) */
        public final double dashCd;
        /** 職業適性: この属性の効果が1.5倍になる */
        private final int affinity;
        /** 原作 skillName 対応 */
        private final String skill;
        /** 武器小技のCT補正。近接職ほど短く、魔法・遠距離職ほど長い (威力は据え置き) */
        private final float lightCd;

        HeroClass(String id, String name, String title, double baseHp, double hpPerLevel,
                  double baseDamage, float attackSpeed, float crit, int projectiles,
                  double moveSpeed, double dashCd, int affinity, String skill, float lightCd) {
            this.id = id; this.name = name; this.title = title;
            this.baseHp = baseHp; this.hpPerLevel = hpPerLevel;
            this.baseDamage = baseDamage; this.attackSpeed = attackSpeed;
            this.crit = crit; this.projectiles = projectiles;
            this.moveSpeed = moveSpeed; this.dashCd = dashCd;
            this.affinity = affinity; this.skill = skill; this.lightCd = lightCd;
        }

        private static final HeroClass[] VALUES = values();
        private static final java.util.Map<String, HeroClass> INDEX = new java.util.HashMap<>();
        static {
            for (HeroClass c : VALUES) INDEX.put(c.id, c);
        }

        public static HeroClass byId(String id) {
            HeroClass c = INDEX.get(id);
            return c != null ? c : SAMURAI;
        }

        /** MCの移動速度属性 (既定0.1 ≒ 原作 250px/s) への換算 */
        public double mcMoveSpeed() {
            return moveSpeed / 2500.0;
        }

        /** 職業適性: この属性の効果が1.5倍になる */
        public int affinityAttr() {
            return affinity;
        }

        /** 原作 skillName 対応 */
        public String skillName() {
            return skill;
        }

        /** 武器小技のCT補正。近接職ほど短く、魔法・遠距離職ほど長い (威力は据え置き) */
        public float lightCdMult() {
            return lightCd;
        }
    }

    public enum WeaponStyle {
        SWORD("sword", "剣", "月牙斬", "3発の貫通斬撃波", 0xd6e8ff),
        AXE("axe", "斧", "大地砕き", "周囲を大爆発させ強く吹き飛ばす", 0xff9465),
        SPEAR("spear", "槍", "竜殺し", "狙った方向へ無敵で駆け抜ける", 0x86dcf4),
        STAFF("staff", "杖", "星辰大爆裂", "敵位置に0.6秒後巨大隕石", 0xbca2ff),
        BOW("bow", "弓", "疾風連雨", "13本の矢を自動照準で連射", 0x92ebbd),
        DAGGER("dagger", "短剣", "影襲", "敵背後へ瞬移し単体強襲", 0xe4c8ff),
        HAMMER("hammer", "槌", "崩壊スマッシュ", "単体極大+感電スタン伝播", 0xf3cf75),
        KATANA("katana", "刀", "心無一刀", "周囲を一閃・敵弾(敵)を斬り払う", 0xffb5bf),
        SCYTHE("scythe", "鎌", "冥府の収穫", "周囲を刈り取り低HPに追加打", 0xaff097),
        GAUNTLET("gauntlet", "拳套", "六連震掌", "前方6連衝撃・最後に吹き飛ばし", 0xffd092),
        CHAKRAM("chakram", "輪刃", "輪廻乱舞", "16枚の貫通輪刃を全方位へ", 0x91f2eb),
        TOME("tome", "魔導書", "禁書・虚空葬", "狙った地点に虚空の爆縮・脆弱", 0xde9df7),
        RUNE_BLADE("rune_blade", "ルーン剣", "ルーン連斬", "5連斬撃波・凍結を添える", 0x7ce8ff),
        BLOOD_AXE("blood_axe", "血斧", "血潮爆砕", "爆発と共に生命を吸う", 0xff2a55),
        STORM_SPEAR("storm_spear", "嵐槍", "螺旋突貫", "突進の軌跡に雷撃", 0x9fd8ff),
        VOID_STAFF("void_staff", "虚杖", "虚空落とし", "隕石を3連で落とす", 0x8a5cff),
        MOON_BOW("moon_bow", "月弓", "月雨", "9矢を自動照準・貫通で連射", 0xd6f0ff),
        FANG_DAGGER("fang_dagger", "牙短剣", "牙影連襲", "3回瞬移し背後から噛む", 0xffe14a),
        QUAKE_HAMMER("quake_hammer", "震槌", "地鳴り", "広範囲に激震・鈍足", 0xc9a227),
        FROST_KATANA("frost_katana", "氷刀", "氷月一閃", "一閃と共に周囲を凍結", 0xaee9ff),
        SOUL_SCYTHE("soul_scythe", "魂鎌", "魂収穫", "刈り取り魂ゲージを得る", 0xc9a0ff),
        TITAN_GAUNTLET("titan_gauntlet", "剛拳", "岩砕十連", "10連衝撃・最後に大爆発", 0xffb03c),
        ECLIPSE_CHAKRAM("eclipse_chakram", "蝕輪", "蝕輪廻", "24枚の輪刃を全方位へ", 0x5c8cff),
        CHAOS_TOME("chaos_tome", "混沌書", "混沌特異点", "爆縮と無作為の弱体", 0xff5c8a),
        ROD("rod", "ロッド", "螺旋嵐", "周囲を嵐で引き寄せ刻む", 0x9fd8ff),
        WAND("wand", "ワンド", "魔弾連星", "10連の自動魔弾を放つ", 0xd6b8ff),
        GREATSWORD("greatsword", "大剣", "天地断", "広域を薙ぎ払い衝撃波", 0xe9e3d5),
        TWIN_DAGGER("twin_dagger", "双短剣", "乱舞連牙", "周囲へ8連の牙", 0xffe14a),
        WAR_HORN("war_horn", "戦角", "大雄叫び", "吹き飛ばし+雄叫び強化", 0xffd092),
        WIND_BLADE("wind_blade", "風輪双刃", "千風乱舞", "3波の風輪が拡大して薙ぎ払う", 0x9fe8d8),
        EMBER_FLAIL("ember_flail", "焔鎖鞭", "炎獄旋回", "炎の鎖を左右に薙ぎ最後に爆気", 0xff8a3c),
        ASTRAL_LANCE("astral_lance", "星輝槍", "鋼星乱嵐", "星槍を乱れ打ち極大の一撃で締める", 0xffe9a8),
        TIDE_TRIDENT("tide_trident", "潮王戟", "静潮・大渦", "渦で引き寄せ潮波で押し流す", 0x7cd8ff),
        MOON_MACE("moon_mace", "月鱗鎚", "月砕", "周囲を打ち上げ鈍足にする", 0xd8e8ff),
        DEPTH_GUN("depth_gun", "深銃", "急所乱舞", "6連の急所射撃を打ち込む", 0xff4d6d),
        VENOM_WHIP("venom_whip", "猛毒鞭", "毒蛇百景", "広く薙ぎ払い猛毒を塗る", 0x8ff06e),
        FLAME_GRIMOIRE("flame_grimoire", "焔典", "業火伽藍", "炎の連壁と発火で焼き尽くす", 0xff6a2b),
        RIFT_BLADE("rift_blade", "虚刃", "虚空断層", "2段瞬移で空間ごと断つ", 0x8a5cff),
        METEOR_LANCE("meteor_lance", "隕槍", "天斥落雁", "隕槍を1点に極大落下させる", 0xffd23c),
        ECHO_KATANA("echo_katana", "残刀", "三影刻", "標的の周囲を3点瞬斬する", 0xa8e8ff),
        HOWLING_CLAW("howling_claw", "哮爪", "裂空爪", "急所へ突進し三連爪を刻む", 0xffb03c);

        public final String id;
        public final String weaponName;
        public final String artName;
        public final String artDesc;
        public final int color;

        WeaponStyle(String id, String weaponName, String artName, String artDesc, int color) {
            this.id = id; this.weaponName = weaponName;
            this.artName = artName; this.artDesc = artDesc; this.color = color;
        }

        public static final WeaponStyle[] VALUES = values();
        private static final java.util.Map<String, WeaponStyle> INDEX = new java.util.HashMap<>();
        static {
            for (WeaponStyle w : VALUES) INDEX.put(w.id, w);
        }

        public static WeaponStyle byId(String id) {
            WeaponStyle w = INDEX.get(id);
            return w != null ? w : SWORD;
        }
    }

    /** 原作 trials.ts の8呪印 (reward = 魂加算%) */
    public enum Sigil {
        MIGHT("might", "剛力", "敵HP×1.30", 18),
        FANGS("fangs", "鋭牙", "敵攻撃×1.22", 16),
        GALE("gale", "疾風", "敵移動×1.16", 14),
        FRENZY("frenzy", "乱舞", "エリート率×1.9", 22),
        FAMINE("famine", "飢餓", "回復量×0.58", 20),
        PAIN("pain", "鈍痛", "魂・奥義ゲージ獲得×0.70", 16),
        GAZE("gaze", "注視", "索敵・射程×1.45", 14),
        ABYSS("abyss", "深淵", "敵HP・攻撃・移動・索敵×1.10", 26);

        public final String id;
        public final String name;
        public final String effect;
        public final int reward;

        Sigil(String id, String name, String effect, int reward) {
            this.id = id; this.name = name; this.effect = effect; this.reward = reward;
        }

        private static final Sigil[] VALUES = values();
        private static final java.util.Map<String, Sigil> INDEX = new java.util.HashMap<>();
        static {
            for (Sigil s : VALUES) INDEX.put(s.id, s);
        }

        public static Sigil byId(String id) {
            return INDEX.get(id);
        }
    }

    /**
     * 原作 trialRules() の写し. 呪印の実戦効果はすべてここから引く.
     * 全選択時は enemyHp = 1.3*1.1, eliteRate = 1.9, healing = 0.58, energy = 0.7,
     * detection = 1.45*1.1, soulMultiplier = 2.46.
     */
    public record TrialRules(double enemyHp, double enemyDamage, double enemySpeed,
                             double eliteRate, double healing, double energy,
                             double detection, double soulMultiplier) {}

    public static TrialRules trialRules(Set<String> sigils) {
        boolean abyss = sigils.contains("abyss");
        double abyssMul = abyss ? 1.10 : 1.0;
        int bonus = 0;
        for (Sigil s : Sigil.values()) {
            if (sigils.contains(s.id)) bonus += s.reward;
        }
        return new TrialRules(
                (sigils.contains("might") ? 1.30 : 1.0) * abyssMul,
                (sigils.contains("fangs") ? 1.22 : 1.0) * abyssMul,
                (sigils.contains("gale") ? 1.16 : 1.0) * abyssMul,
                sigils.contains("frenzy") ? 1.9 : 1.0,
                sigils.contains("famine") ? 0.58 : 1.0,
                sigils.contains("pain") ? 0.70 : 1.0,
                (sigils.contains("gaze") ? 1.45 : 1.0) * abyssMul,
                (100 + bonus) / 100.0);
    }

    public static double heroDamage(HeroClass cls, int level) {
        return cls.baseDamage * (1.0 + (level - 1) * 0.17);
    }

    /** 奥義・必殺技のレベル段階 (10レベルごとに1段階、最大3)。段階名つき */
    public static int skillTier(int level) {
        return Math.min(3, Math.max(0, level / 10));
    }

    /** 奥義・必殺技のレベル倍率 (1.0 / 1.3 / 1.6 / 1.9) */
    public static double skillTierMult(int level) {
        return 1.0 + skillTier(level) * 0.3;
    }

    /** 奥義・必殺技の段階名 ("", "・改", "・極", "・神") */
    private static final String[] TIER_NAMES = {"", "・改", "・極", "・神"};

    public static String skillTierName(int level) {
        return TIER_NAMES[skillTier(level)];
    }

    public static double heroMaxHp(HeroClass cls, int level) {
        return cls.baseHp + (level - 1) * cls.hpPerLevel;
    }

    /** 原作の敵基礎索敵距離 (MC FOLLOW_RANGE 既定32 をそのまま基準にする) */
    public static final double BASE_FOLLOW_RANGE = 32.0;

    /** 原作: clamp((0.05 + floor*0.014) * eliteRate, 0, 0.9) */
    public static double eliteChance(int floor, Set<String> sigils) {
        double rate = trialRules(sigils).eliteRate();
        return Math.max(0, Math.min(0.9, (0.05 + floor * 0.014) * rate));
    }

    /**
     * 回避CT: 原作 max(0.28, dashCd * (1 - clamp(dashCdPct,0,65)/100)).
     * dashCdPct = 敏捷1点につき1 (attributes.ts).
     */
    public static double dashCooldown(HeroClass cls, int agility) {
        double pct = Math.max(0, Math.min(65, agility));
        return Math.max(0.28, cls.dashCd * (1 - pct / 100.0));
    }

    /**
     * 奥義ゲージ: 与えた実ダメージで蓄積 (原作 artChargeGain).
     * actualDamage は HP を超えないよう呼び出し側で切り詰める (オーバーキル除外).
     * 奥義自身のダメージ・環境罠・二次反応は呼び出し側で artDamage=true として除外する.
     */
    public static double artChargeGain(double actualDamage, double attack, boolean artDamage) {
        if (artDamage || !Double.isFinite(actualDamage) || actualDamage <= 0) return 0;
        return Math.min(7.0, actualDamage / Math.max(10.0, attack) * 3.0);
    }

    /** 被ダメージでのゲージ蓄積 (原作 suffered / maxHp * 35). suffered は残HPまで */
    public static double damageTakenCharge(double suffered, double maxHp) {
        if (!Double.isFinite(suffered) || suffered <= 0) return 0;
        return suffered / Math.max(1.0, maxHp) * 35.0;
    }

    /**
     * 素材の端数: 原作 materialAmount と同じ確率丸め.
     * 幸運1点につき+4%. rng は [0,1) を返す乱数.
     */
    public static int materialAmount(double base, int fortune, DoubleSupplier rng) {
        double exact = Math.max(0, base) * (1 + Math.max(0, fortune) * 0.04);
        int whole = (int) Math.floor(exact);
        return whole + (rng.getAsDouble() < exact - whole ? 1 : 0);
    }

    /** 運 = 幸運点 * 7 (attributes.ts luckFlat) */
    public static int luckOf(int fortunePoints) {
        return Math.max(0, fortunePoints) * 7;
    }

    /** 原作の敵ドロップ係数 (0.028 + luck*0.0007) に合わせた武器ドロップ加算 */
    public static double luckDropBonus(int luck) {
        return Math.max(0, luck) * 0.0007;
    }

    public static double enemyHpMult(Set<String> sigils) {
        return trialRules(sigils).enemyHp();
    }

    public static double enemyDmgMult(Set<String> sigils) {
        return trialRules(sigils).enemyDamage();
    }

    /** ラン終了時の魂の内訳 (原作 run-rules.ts calculateRunReward の写し). */
    public record RunReward(int baseSouls, int beforeTrials, int runSouls, int trialBonus) {}

    /**
     * 原作 calculateRunReward: 基礎魂 = 層×14 + 撃破×0.6 + レベル×8 + スコア/40 (+勝利900 +真の深淵6000)。
     * 何も進めずに離脱した場合は 0 (無限の魂稼ぎ防止)。
     */
    public static RunReward runReward(int floor, int kills, int level, int score, boolean victory, boolean trueCleared,
                                      double modeRate, double difficultyRate, double trialMult) {
        boolean progressed = kills > 0 || floor > 1 || victory;
        int base = progressed
                ? (int) Math.round(floor * 14.0 + kills * 0.6 + level * 8.0 + score / 40.0
                        + (victory ? 900 : 0) + (trueCleared ? 6000 : 0))
                : 0;
        int before = (int) Math.round(base * modeRate * difficultyRate);
        int run = (int) Math.round(before * trialMult);
        return new RunReward(base, before, run, run - before);
    }

    /**
     * 原作 summarize の基礎スコア.
     * 層×620 + 撃破×14 + レベル×110 + 欠片×1.6 + 与ダメ×0.06 (+勝利12000 +最大コンボ×2 +弾幕の見切り×12)。
     * gold は原作の蓄積ゴールドに対応 (Mod では所持欠片で近似)。
     * 見切り (PERFECT) は原作の graze/bulletsReflected の代替。
     */
    public static int baseScore(int floor, int kills, int level, int shards, int damage,
                                boolean victory, int bestCombo, int perfect, boolean danmaku) {
        double s = floor * 620.0 + kills * 14.0 + level * 110.0 + shards * 1.6 + damage * 0.06
                + (victory ? 12000 : 0) + bestCombo * 2.0 + (danmaku ? perfect * 12.0 : 0);
        return (int) Math.round(s);
    }

    /** 原作 summarize: 基礎スコアに日替わり・モード・難易度の倍率 */
    public static int finalScore(int base, double dailyScoreRate, String mode, String difficulty) {
        return (int) Math.round(base * dailyScoreRate * modeScoreMult(mode)
                * difficultyById(difficulty).scoreMul());
    }

    /** 魂加算 (報酬用). 原作 soulMultiplier = (100 + Σreward)/100 */
    public static double soulMult(Set<String> sigils) {
        return trialRules(sigils).soulMultiplier();
    }

    /**
     * 特殊敵の出現スロット (原作の固定配置に対応). 該当しなければ null (通常敵の抽選へ).
     * 護衛兵 4層〜 / 蘇生師 6層〜 / 召喚門 7層〜. 同じ番号は上の役割を優先.
     */
    public static String specialSlotRole(int floor, int slot) {
        if (floor >= 7 && slot % 9 == 8) return "gate";
        if (floor >= 6 && slot % 7 == 5) return "necromancer";
        if (floor >= 4 && slot % 5 == 3) return "guard";
        return null;
    }

    /** 層による敵HP倍率 (上層ほど急峻に。1 + 層*0.30 + 層^2*0.004) */
    public static double floorHpScale(int floor) {
        return 1 + floor * 0.30 + floor * floor * 0.004;
    }

    /** 層による敵攻撃倍率 (1 + 層*0.12 + 層^2*0.002) */
    public static double floorDmgScale(int floor) {
        return 1 + floor * 0.12 + floor * floor * 0.002;
    }

    /**
     * 原作 core.ts FLOOR_MUTATIONS の写し. 層の異変 (12種).
     * enemyHp/enemyDmg/enemySpeed/spawn/eliteRate は敵側, gold/loot は報酬側の倍率.
     * darkness は視界を塞ぐ (MCでは盲目効果で再現).
     */
    public record Mutation(String id, String name, String desc, double enemyHp, double enemyDmg,
                           double enemySpeed, double spawn, double eliteRate, double gold,
                           double loot, boolean darkness) {}

    public static final java.util.List<Mutation> MUTATIONS = java.util.List.of(
            new Mutation("darkness", "闇の迷宮", "視界が極度に塞がれる。転移門の光だけが頼り。",
                    1.00, 1.05, 1.00, 1.00, 1.25, 1.2, 1.4, true),
            new Mutation("frenzy", "狂乱の檻", "敵の速度と出現数が目に見えて跳ね上がる。",
                    0.92, 1.08, 1.30, 1.38, 1.30, 1.25, 1.2, false),
            new Mutation("gilded", "黄金の流砂", "敵が頑丈になるが、ゴールドが降り注ぐ。",
                    1.24, 1.00, 0.95, 1.05, 1.40, 2.3, 1.2, false),
            new Mutation("bloodfeast", "血の饗宴", "敵の牙が研がれる。",
                    1.10, 1.38, 1.08, 1.12, 1.35, 1.3, 1.35, false),
            new Mutation("fortress", "防衛線", "敵の装甲が異様に固い。",
                    1.58, 1.00, 0.88, 0.78, 1.50, 1.3, 1.25, false),
            new Mutation("mire", "毒瘴の構造", "毒沼が増す。",
                    1.15, 1.12, 1.00, 1.00, 1.20, 1.15, 1.3, false),
            new Mutation("bounty", "豊穣の刻", "報酬が増えるが、それを狙う者も増える。",
                    1.05, 1.14, 1.00, 1.18, 1.65, 1.25, 1.85, false),
            new Mutation("swarm", "群れの層", "敵が数重ねて押し寄せる。",
                    0.85, 0.95, 1.12, 1.65, 0.90, 1.2, 1.1, false),
            new Mutation("champion", "強者の巡礼地", "鍛え上げた強敵に出会いやすい。",
                    1.12, 1.15, 1.04, 0.95, 2.30, 1.3, 1.5, false),
            new Mutation("silence", "静寂の回廊", "沈黙が支配する層。敵は少ないが分配質が強い。",
                    0.90, 1.00, 0.90, 0.80, 1.10, 1.40, 1.20, false),
            new Mutation("maldust", "呪塵の層", "呪われた粉塵が漂う。敵は手強く、戦利品も濃い。",
                    1.00, 1.25, 1.00, 1.05, 1.15, 1.25, 1.50, false),
            new Mutation("vendetta", "因縁の刻", "宿敵たちが殺到する。精鋭率が跳ね上がる。",
                    0.95, 1.10, 1.15, 1.00, 2.20, 1.30, 1.30, false));

    public static final java.util.Map<String, Mutation> MUTATION_INDEX =
            MUTATIONS.stream().collect(java.util.stream.Collectors.toMap(Mutation::id, m -> m));

    public static Mutation mutationById(String id) {
        return MUTATION_INDEX.get(id);
    }

    /**
     * 原作 rollFloorMutation: 真の決戦層は平穏. 通常層は62%、層主の層は14%で異変.
     * r は [0,1) の乱数 (2回使用: 発生判定と種類選択)
     */
    public static Mutation rollMutation(java.util.function.DoubleSupplier r, int floor) {
        if (floor >= 26) return null;
        boolean bossFloor = floor % 5 == 0;
        double chance = bossFloor ? 0.14 : 0.62;
        if (r.getAsDouble() > chance) return null;
        int idx = Math.min(MUTATIONS.size() - 1, (int) (r.getAsDouble() * MUTATIONS.size()));
        return MUTATIONS.get(idx);
    }

    /* ---------------- 原作 core.ts DIFFICULTIES (5段階) ---------------- */

    public record Difficulty(String id, String name, double hpMul, double dmgMul, double soulMul, double scoreMul) {}

    public static final java.util.List<Difficulty> DIFFICULTIES = java.util.List.of(
            new Difficulty("normal", "通常", 1.0, 1.0, 1.0, 1.0),
            new Difficulty("hard", "極難", 1.4, 1.3, 1.5, 1.6),
            new Difficulty("hell", "地獄", 1.9, 1.65, 2.1, 2.4),
            new Difficulty("inferno", "阿鼻叫喚", 2.7, 2.3, 3.2, 3.8),
            new Difficulty("omega", "終焉の契約", 3.8, 3.0, 7.56, 5.0));

    public static final java.util.Map<String, Difficulty> DIFFICULTY_INDEX =
            DIFFICULTIES.stream().collect(java.util.stream.Collectors.toMap(Difficulty::id, d -> d));

    public static Difficulty difficultyById(String id) {
        Difficulty d = DIFFICULTY_INDEX.get(id);
        return d != null ? d : DIFFICULTIES.get(0);
    }

    /* ---------------- 原作 progression.ts RUN_MODES (8種) ---------------- */

    public record RunMode(String id, String name, double soulMul, double scoreMul) {}

    public static final java.util.List<RunMode> RUN_MODES = java.util.List.of(
            new RunMode("standard", "通常潜行", 1.0, 1.0),
            new RunMode("bossrush", "主の巡礼", 2.0, 2.2),
            new RunMode("endless", "無限深淵", 1.6, 1.8),
            new RunMode("covenant", "不幸の盟約", 3.0, 3.4),
            new RunMode("pinnacle", "終焉の座", 4.5, 5.0),
            new RunMode("danmaku", "弾幕の獄", 3.5, 4.0),
            new RunMode("omega", "終焉の契約", 7.56, 5.0),
            new RunMode("daily", "日替わり深淵", 1.0, 1.0));

    public static final java.util.Map<String, RunMode> RUN_MODE_INDEX =
            RUN_MODES.stream().collect(java.util.stream.Collectors.toMap(RunMode::id, m -> m));

    public static double modeSoulMult(String mode) {
        RunMode m = RUN_MODE_INDEX.get(mode);
        return m != null ? m.soulMul() : 1.0;
    }

    /** 原作 summarize のモード倍率 (daily は standard 扱い) */
    public static double modeScoreMult(String mode) {
        String key = "daily".equals(mode) ? "standard" : mode;
        RunMode m = RUN_MODE_INDEX.get(key);
        return m != null ? m.scoreMul() : 1.0;
    }

    /* ---------------- 原作 DAILY_MODIFIERS (7種) ---------------- */

    public record DailyMod(String id, String title, double enemyHp, double enemyDmg,
                           double enemySpeed, double spawn, double elite, double gold,
                           double xp, double soul, double score, boolean noFloorHeal) {}

    public static final java.util.List<DailyMod> DAILY_MODS = java.util.List.of(
            new DailyMod("blood-moon", "血月の狩猟", 1.42, 1.18, 1.08, 1.30, 1.50, 1.15, 1.10, 1.50, 1.55, false),
            new DailyMod("glass-oath", "硝子の誓約", 1.05, 1.18, 1.12, 1.08, 1.20, 1.10, 1.05, 1.35, 1.65, false),
            new DailyMod("endless-hunt", "終わらぬ追跡", 1.12, 1.06, 1.34, 1.55, 1.35, 1.20, 1.15, 1.35, 1.60, false),
            new DailyMod("drought", "乾ききった迷宮", 1.14, 1.12, 1.04, 1.12, 1.20, 1.22, 1.45, 1.45, 1.50, true),
            new DailyMod("treasure-tide", "黄金の潮流", 1.32, 1.06, 1.00, 1.15, 1.70, 2.05, 1.12, 1.40, 1.50, false),
            new DailyMod("void-storm", "虚空嵐", 1.18, 1.22, 1.12, 1.22, 2.10, 1.25, 1.18, 1.55, 1.75, false),
            new DailyMod("starfall", "星墜ちの夜", 1.25, 1.10, 1.05, 1.10, 1.40, 1.25, 1.80, 1.55, 1.65, false));

    public static final java.util.Map<String, DailyMod> DAILY_MOD_INDEX =
            DAILY_MODS.stream().collect(java.util.stream.Collectors.toMap(DailyMod::id, m -> m));

    public static DailyMod dailyModById(String id) {
        return DAILY_MOD_INDEX.get(id);
    }

    /** 原作 getDailyRun と同じく UTC 日で 7種ローテ */
    public static DailyMod dailyModForDay(long epochDay) {
        int i = (int) (Math.floorMod(epochDay, DAILY_MODS.size()));
        return DAILY_MODS.get(i);
    }

    /* ---------------- 原作 WARDENS (6種)・isWardenFloor ---------------- */

    public record Warden(String name, String flavor, double hpMul, double dmgMul) {}

    public static final java.util.List<Warden> WARDENS = java.util.List.of(
            new Warden("石憑の番人・ガルグ", "層を護り続ける朽ちぬ石像。", 5.2, 1.7),
            new Warden("幽玄の番人・ルファ", "迷い込んだ者を静かに刈り取る。", 4.6, 1.9),
            new Warden("窮獄の番人・メズ", "深淵の責め苦が仇成す者。", 5.8, 2.0),
            new Warden("永劫の番人・ノヴォ", "何人かの冒険者の記憶が人の形を模した。", 6.2, 2.2),
            new Warden("戸惑の番人・ツイマ", "あらゆる扉の先にその知識が宿る。", 6.8, 2.4),
            new Warden("灰燼の番人・ヴォスク", "焼け残った囁きが鎧の形をなした。", 7.5, 2.6));

    /** 原作 isWardenFloor: ボス層・真層を除く 3/8 層 */
    public static boolean isWardenFloor(int floor) {
        return floor % 5 != 0 && floor < 26 && floor >= 3 && (floor % 10 == 3 || floor % 10 == 8);
    }

    /* ---------------- 原作 ZONES (6種) ---------------- */

    public record Zone(String id, String name, String desc, int minFloor, int maxFloor,
                       int hazards, double enemySpeed, double projSpeed,
                       boolean emberRain, double voidPull, double slip) {}

    public static final java.util.List<Zone> ZONES = java.util.List.of(
            new Zone("ash", "灰の回廊", "静かで、まだ死んでいない入口。", 1, 5, 0, 1.0, 1.0, false, 0, 0),
            new Zone("ossuary", "骸骨の墓所", "壁が骨で出来ている。何かが這い回る。", 6, 10, 1, 1.05, 1.0, false, 0, 0),
            new Zone("forge", "溶鉄の炉", "天井から火花が降る。熱で視界が歪む。", 11, 15, 3, 1.0, 1.12, true, 0, 0),
            new Zone("glacier", "氷結の深層", "床が氷だ。足が言うことを聞かない。", 16, 20, 2, 1.1, 1.0, false, 0, 1.85),
            new Zone("voidheart", "虚無の心臓", "空間が軋み、全てが内側へ引き込まれる。", 21, 25, 3, 1.14, 1.28, false, 46, 0),
            new Zone("nihil", "真の深淵", "ここには何もない。帰る場所も。", 26, 28, 0, 1.0, 1.0, false, 0, 0),
            new Zone("lower_abyss", "果て無き深底", "終わらない下層。虚無がさらに沈んでいく。", 29, Integer.MAX_VALUE, 3, 1.16, 1.25, false, 56, 0));

    public static Zone zoneForFloor(int floor) {
        for (Zone z : ZONES) {
            if (floor >= z.minFloor() && floor <= z.maxFloor()) return z;
        }
        return ZONES.get(0);
    }

    /* ---------------- 原作 RELICS (26種・ラン中最大2つ) ---------------- */

    public record Relic(String id, String name, String desc, String flag) {}

    public static final java.util.List<Relic> RELICS = java.util.List.of(
            new Relic("soulfang", "魂喰いの爪", "撃破ごとに魂ゲージ +8", "soulfang"),
            new Relic("mirror", "呪われた鏡", "死の瞬間に40%でHP50%蘇生 (1ラン1回)", "mirror"),
            new Relic("coin", "盗賊の金貨", "敵が落とす素材の量が2倍", "coin"),
            new Relic("chalice", "呪杯", "攻撃力 +40%。常にHPが削れ続ける", "chalice"),
            new Relic("twin", "双魂の盃", "10レベルごとに弾数+1・攻撃+10%", "twin"),
            new Relic("storm", "嵐召喚者の冠", "見切り成立のたび落雷が3体を焼く", "storm"),
            new Relic("greed", "巨人の欲", "45秒ごとに所持欠片の半分がHPに変わる", "greed"),
            new Relic("compass", "深羅針盤", "地上の最強装備へ光の道を示す", "compass"),
            new Relic("r_blade", "斬鉄剣", "攻撃力 +20%", null),
            new Relic("r_wind", "疾風の羽", "攻撃速度 +10%", null),
            new Relic("r_plate", "金剛の盾", "ダメージ軽減 +15%", null),
            new Relic("r_heart", "巨人の心臓", "最大HP +20%", null),
            new Relic("r_eye", "千里眼", "会心率 +8%", null),
            new Relic("r_fang", "吸血牙", "生命吸収 +5%", null),
            new Relic("r_orb", "魂の宝珠", "魂ゲージ増加 +30%", null),
            new Relic("r_wave", "震動の鈴", "攻撃範囲 +15%", null),
            new Relic("r_bomb", "爆薬庫", "爆裂確率 +15%", null),
            new Relic("r_boot", "疾風の靴", "移動速度 +12%", null),
            new Relic("r_spring", "生命の泉", "HP自動回復 +2/秒", null),
            new Relic("r_star", "星屑の冠", "運 +25", null),
            new Relic("r_gale", "疾風の触角", "攻撃速度 +15% / 移動速度 +8%", null),
            new Relic("r_moon", "月璋", "会心率 +10% / 会心威力 +22%", null),
            new Relic("r_core", "星の核", "攻撃範囲 +20% / 貫通 +1", null),
            new Relic("r_flame", "焦土の指輪", "爆裂確率 +25% / 攻撃力 +6%", null),
            new Relic("r_tide", "潮の腕輪", "ダメージ軽減 +8% / 最大HP +10%", null),
            new Relic("r_grail", "残響の聖杯", "魂ゲージ増加 +35% / HP回復 +1/秒", null));

    public static final java.util.Map<String, Relic> RELIC_INDEX =
            RELICS.stream().collect(java.util.stream.Collectors.toMap(Relic::id, r -> r));

    public static Relic relicById(String id) {
        return RELIC_INDEX.get(id);
    }

    /** 未所持から n 件引く (原作 rollRelics) */
    public static java.util.List<Relic> rollRelics(java.util.Random r, java.util.List<String> owned, int n) {
        var pool = new java.util.ArrayList<Relic>();
        for (Relic re : RELICS) {
            if (!owned.contains(re.id())) pool.add(re);
        }
        java.util.Collections.shuffle(pool, r);
        return pool.subList(0, Math.min(n, pool.size()));
    }

    public static java.util.Set<String> relicFlags(java.util.Collection<String> owned) {
        var s = new java.util.HashSet<String>();
        for (String id : owned) {
            Relic re = relicById(id);
            if (re != null) s.add(re.flag());
        }
        return s;
    }

    /* ---------------- 原作 PASSIVES (44種) ---------------- */

    public record Passive(String id, String name, String desc, String grade, String flag) {}

    public static final java.util.List<Passive> PASSIVES = java.util.List.of(
            new Passive("iron_oath", "鉄の律儀", "攻撃力 +25% / 最大HP -12%", "minor", null),
            new Passive("clear_mind", "清心", "被ダメ軽減 +14% / 攻撃速度 -10%", "minor", null),
            new Passive("abyss_eye", "深淵の目", "会心 +12% / 運 +40 / 最大HP -25", "minor", null),
            new Passive("glass_cannon", "硝子の大砲", "攻撃力 +55% / 最大HP -35% / 被ダメ +10%", "major", null),
            new Passive("blood_pact", "血の盟約", "生命吸収 +6% / 自然回復を喪失", "minor", null),
            new Passive("slow_burn", "業火の刻", "炎上付与 +35% / 移動速度 -12%", "minor", null),
            new Passive("twin_soul", "双魂", "追加弾数 +1 / 攻撃力 -18%", "major", null),
            new Passive("giant_slayer", "巨人殺し", "エリート・層主へ +45% / 雑魚へ -15%", "major", "giant_slayer"),
            new Passive("swarm_bane", "群れの克星", "同時に3体以上ヒットで +35%", "major", "swarm_bane"),
            new Passive("last_stand", "最後の砦", "HP30%以下で攻撃 +50% / 軽減 +25%", "major", "last_stand"),
            new Passive("momentum", "勢い", "コンボ20以上で攻速 +35% / 切れると被ダメ +15%", "cursed", "momentum"),
            new Passive("greedy", "強欲の契", "ゴールド +60% / 被ダメ +12%", "minor", null),
            new Passive("scholar", "学者の飢え", "経験値 +45% / 最大HP -15%", "minor", null),
            new Passive("swift", "疾影", "移動 +25% / 回避短縮 20% / 攻撃力 -10%", "minor", null),
            new Passive("thorn_lord", "棘の王", "棘 +80 / 最大HP -10%", "minor", null),
            new Passive("no_mercy", "無慈悲", "攻撃力 +20% / ダメージ軽減 -8%", "minor", null),
            new Passive("undying", "不滅の誓い", "一度だけ HP50% で復活 / 最大HP -20%", "cursed", "undying"),
            new Passive("echo_strike", "残響の型", "フィニッシャーが再放される / 攻速 -15%", "cursed", "echo_strike"),
            new Passive("perfection", "完全主義", "見切り後3秒間、攻撃 +90%", "major", "last_stand"),
            new Passive("frostbound", "氷縛の理", "氷結 +40% / 攻撃速度 -12%", "minor", null),
            new Passive("fighting_spirit", "闘志", "攻撃力 +18%", "minor", null),
            new Passive("last_bet", "背水の陣", "攻撃力 +35% / 最大HP -20%", "major", null),
            new Passive("acrobat", "軽業", "攻撃速度 +12% / 移動速度 +8%", "minor", null),
            new Passive("iron_wall", "鉄壁", "ダメージ軽減 +12% / 最大HP +10%", "minor", null),
            new Passive("sniper", "狙撃手", "会心威力 +40% / 会心率 -3%", "minor", null),
            new Passive("runner", "疾駆", "移動速度 +15% / 攻撃速度 +5%", "minor", null),
            new Passive("miser", "貯蓄家", "最大HP +20% / 移動速度 -5%", "minor", null),
            new Passive("executioner", "処刑人", "会心率 +10% / 攻撃力 +8%", "major", null),
            new Passive("ascetic", "禁欲", "魂ゲージ増加 +25% / 攻撃力 -5%", "minor", null),
            new Passive("regenerate", "自動修復", "HP自動回復 +1.5/秒", "minor", null),
            new Passive("lucky", "強運", "運 +30 / 会心率 +3%", "minor", null),
            new Passive("destroyer", "破壊者", "攻撃範囲 +15% / 攻撃力 -8%", "major", null),
            new Passive("bomber", "爆弾魔", "爆裂確率 +15% / 軽減 -5%", "minor", null),
            new Passive("vampire", "吸血鬼", "生命吸収 +4% / 最大HP -8%", "major", null),
            new Passive("freezer", "凍結師", "氷結確率 +20% / 攻撃速度 -5%", "minor", null),
            new Passive("berserk", "狂化", "攻撃力 +45% / 軽減 -15% / 最大HP -15%", "cursed", null),
            new Passive("hellfire", "獄炎", "攻撃力 +30% / 爆裂 +20% / 最大HP -10%", "major", null),
            new Passive("moon_gaze", "月睫", "会心率 +15% / 魂ゲージ +15% / 軽減 -6%", "major", null),
            new Passive("ironhide", "鉄皮", "軽減 +18% / 移動 -8%", "minor", null),
            new Passive("phantom", "幻影", "移動 +25% / 攻撃力 -10%", "minor", null),
            new Passive("gluttony", "暴食", "生命吸収 +8% / 攻撃力 +10% / 最大HP -12%", "major", null),
            new Passive("ascension", "昇華", "魂ゲージ +40% / 軽減 -10%", "major", null),
            new Passive("plague", "疫病", "氷結 +30% / 攻撃力 -12%", "minor", null),
            new Passive("titan_heart", "巨心", "最大HP +30% / 攻撃力 -8%", "minor", null));

    public static final java.util.Map<String, Passive> PASSIVE_INDEX =
            PASSIVES.stream().collect(java.util.stream.Collectors.toMap(Passive::id, p -> p));

    public static Passive passiveById(String id) {
        return PASSIVE_INDEX.get(id);
    }

    /** 主撃破後の3候補 (原作 rollPassives: 既取得除外・呪は1つまで) */
    public static java.util.List<Passive> rollPassives(java.util.Random r, java.util.List<String> owned, int n) {
        long cursed = 0;
        for (String id : owned) {
            Passive p = passiveById(id);
            if (p != null && "cursed".equals(p.grade())) cursed++;
        }
        var pool = new java.util.ArrayList<Passive>();
        for (Passive p : PASSIVES) {
            if (owned.contains(p.id())) continue;
            if ("cursed".equals(p.grade()) && cursed >= 1) continue;
            pool.add(p);
        }
        java.util.Collections.shuffle(pool, r);
        return pool.subList(0, Math.min(n, pool.size()));
    }

    public static java.util.Set<String> passiveFlags(java.util.Collection<String> owned) {
        var s = new java.util.HashSet<String>();
        for (String id : owned) {
            Passive p = passiveById(id);
            if (p != null && p.flag() != null) s.add(p.flag());
        }
        return s;
    }

    /**
     * 技能の数値効果 (原作 passiveMods の MC 対応分)。単一の表に統合し、
     * passiveDamagePct/MaxHpPct/ArmorPct と呪喰らい (呪喰らいは Σ|v| = 欠点無効+正効果2倍) を導出する。
     * id -> [攻撃%, 最大HP%, 被ダメ軽減%]
     */
    private static final java.util.Map<String, double[]> PASSIVE_MODS = java.util.Map.ofEntries(
            java.util.Map.entry("iron_oath", new double[]{25, -12, 0}),
            java.util.Map.entry("clear_mind", new double[]{0, 0, 14}),
            java.util.Map.entry("glass_cannon", new double[]{55, -35, -10}),
            java.util.Map.entry("scholar", new double[]{0, -15, 0}),
            java.util.Map.entry("twin_soul", new double[]{-18, 0, 0}),
            java.util.Map.entry("no_mercy", new double[]{20, 0, -8}),
            java.util.Map.entry("swift", new double[]{-10, 0, 0}),
            java.util.Map.entry("greedy", new double[]{0, 0, -12}),
            java.util.Map.entry("thorn_lord", new double[]{0, -10, 0}),
            java.util.Map.entry("undying", new double[]{0, -20, 0}));

    /** 指定列の合計 (col: 0=攻撃% 1=最大HP% 2=被ダメ軽減%) */
    private static double passiveModSum(java.util.Collection<String> owned, int col) {
        double v = 0;
        for (String id : owned) {
            var m = PASSIVE_MODS.get(id);
            if (m != null) v += m[col];
        }
        return v;
    }

    /** 指定列の絶対値合計 (呪喰らい: 欠点 (負) を無効化し、正効果を2倍にする補正値) */
    private static double passiveModAbsSum(java.util.Collection<String> owned, int col) {
        double v = 0;
        for (String id : owned) {
            var m = PASSIVE_MODS.get(id);
            if (m != null) v += Math.abs(m[col]);
        }
        return v;
    }

    /** 技能の攻撃倍率の加算項 (%) */
    public static double passiveDamagePct(java.util.Collection<String> owned) {
        return passiveModSum(owned, 0);
    }

    public static double passiveMaxHpPct(java.util.Collection<String> owned) {
        return passiveModSum(owned, 1);
    }

    /** 被ダメ軽減の加算項 (%). 正=軽減、負=被ダメ増 */
    public static double passiveArmorPct(java.util.Collection<String> owned) {
        return passiveModSum(owned, 2);
    }

    /**
     * 恩寵「呪喰らい」: 技能の欠点 (負の効果) を無効化し、正の効果を2倍にする.
     * 各ヘルパーの結果に足す補正値を返す (補正後 = base + bonus)。欠点が消え正が2倍になるので Σ|v| に等しい。
     */
    public static double curseEaterBonusDamage(java.util.Collection<String> owned) {
        return passiveModAbsSum(owned, 0);
    }

    public static double curseEaterBonusMaxHp(java.util.Collection<String> owned) {
        return passiveModAbsSum(owned, 1);
    }

    public static double curseEaterBonusArmor(java.util.Collection<String> owned) {
        return passiveModAbsSum(owned, 2);
    }

    /* ---------------- 原作 ACHIEVEMENTS (32種) ---------------- */

    public record Achievement(String id, String title, String desc, int reward, String tier) {}

    public static final java.util.List<Achievement> ACHIEVEMENTS = java.util.List.of(
            new Achievement("gate-breaker", "門を断つ者", "呪われた召喚門を1つ破壊する", 180, "silver"),
            new Achievement("eight-sigils", "八重の試練", "呪印8枚で第10層へ到達する", 800, "mythic"),
            new Achievement("art-master", "武の極致", "1回の冒険で武器奥義を10回使う", 300, "gold"),
            new Achievement("first-blood", "深淵への第一歩", "初めて敵を倒す", 25, "bronze"),
            new Achievement("hundred-kills", "百鬼夜行", "1回の冒険で100体撃破", 100, "silver"),
            new Achievement("thousand-kills", "一騎当千", "1回の冒険で1,000体撃破", 500, "gold"),
            new Achievement("floor-five", "門番を越えて", "第5層の主を撃破", 100, "silver"),
            new Achievement("floor-ten", "深層探査者", "第10層の主を撃破", 250, "gold"),
            new Achievement("floor-twenty", "虚無の手前", "第20層の主を撃破", 600, "mythic"),
            new Achievement("full-clear", "深淵を終わらせた者", "第25層を踏破する", 1500, "mythic"),
            new Achievement("true-clear", "虚無を_zeroに還した者", "原初虚無《ニヒル・ゼロ》を討伐する", 4000, "mythic"),
            new Achievement("heirloom-keep", "血脈の守り人", "装備を継承して次の冒険へ持ち帰る", 400, "gold"),
            new Achievement("mythic-hunter", "神話を掴む手", "神話級装備を発見する", 300, "gold"),
            new Achievement("combo-fifty", "無間連撃", "1回の冒険で50コンボ達成", 250, "gold"),
            new Achievement("perfect-ten", "刹那の見切り", "1回の冒険で10回見切る", 300, "gold"),
            new Achievement("event-five", "祭壇の巡礼者", "層間イベントを5回完了", 220, "silver"),
            new Achievement("daily-depth", "日替わり踏破者", "日替わり深淵で第10層へ到達", 450, "gold"),
            new Achievement("level-twenty", "刻まれた成長", "1回の冒険でLv20到達", 250, "silver"),
            new Achievement("boss-three", "層主狩り", "1回の冒険で3体の層主を撃破", 450, "gold"),
            new Achievement("covenant-kept", "盟約を全うした者", "不幸の盟約で深淵を踏破する", 5000, "mythic"),
            new Achievement("rush-master", "巡礼の果て", "主の巡礼で8体の層主を撃破", 1800, "mythic"),
            new Achievement("endless-diver", "限界の先", "無限深淵で第35層へ到達", 2200, "mythic"),
            new Achievement("pinnacle-god", "終焉を穿つ者", "終焉の座でタルタロス・オメガを討ち果たす", 10000, "mythic"),
            new Achievement("inferno-slayer", "阿鼻叫喚を制す", "難易度【阿鼻叫喚】で第15層へ到達する", 4000, "mythic"),
            new Achievement("combo-hundred", "百連撃", "1回の冒険で100コンボ達成", 1000, "mythic"),
            new Achievement("perfect-twenty", "見切りの達人", "1回の冒険で20回見切る", 450, "gold"),
            new Achievement("harvest-ten", "大豊作", "刈り取りを10回成功させる", 400, "gold"),
            new Achievement("boss-five", "序列の主", "1回の冒険で5体の層主を撃破", 700, "gold"),
            new Achievement("event-ten", "祭壇の常連", "層間イベントを10回完了", 400, "silver"),
            new Achievement("kills-five-thousand", "五千斬", "1回の冒険で5,000体撃破", 2200, "mythic"),
            new Achievement("endless-fifty", "無限の果て", "無限深淵で第50層へ到達", 2600, "mythic"),
            new Achievement("forge-max", "覚醒の銘", "鍛造を最高段階まで鍛え上げる", 500, "gold"));

    public static final java.util.Map<String, Achievement> ACHIEVEMENT_INDEX =
            ACHIEVEMENTS.stream().collect(java.util.stream.Collectors.toMap(Achievement::id, a -> a));

    public static Achievement achievementById(String id) {
        return ACHIEVEMENT_INDEX.get(id);
    }

    /** 原作の魂報酬を欠片に換算 (魂25=欠片1、最低1) */
    public static int shardsForReward(int souls) {
        return Math.max(1, (int) Math.round(souls / 25.0));
    }

    /* ---------------- 層主・ボス (原作 BOSSES 8) ---------------- */

    public static final int FINAL_FLOOR = 25;
    public static final int TRUE_FLOOR = 26;
    public static final int PINNACLE_FLOOR = 27;
    public static final int ASCENSION_FLOOR = 28;

    public record BossDef(int floor, String id, String name, double hp, double dmg, double speed) {}

    /* ボス定義 (原作ベースの数値)。hp は層スケールで除算した上で、バニラ属性上限
     *  (MAX_HEALTH=1024) にクランプされる — 実効値の目安: 5層≈346 / 10層≈477 / 15層≈656 /
     *  20層≈884 / 25層以降はすべて 1024 (差別化は dmg とフェーズ弾幕で行う). */
    public static final java.util.List<BossDef> BOSSES = java.util.List.of(
            new BossDef(5, "b_goblin", "ゴブリン王 グルザ", 900, 20, 82),
            new BossDef(10, "b_wyrm", "屍竜 ヴァイト", 2100, 28, 96),
            new BossDef(15, "b_knight", "深淵騎士アビス", 4200, 36, 112),
            new BossDef(20, "b_eye", "万象の邪眼", 7600, 44, 92),
            new BossDef(25, "b_god", "虚無神 ニヒル", 15000, 56, 108),
            new BossDef(26, "b_true", "原初虚無 《ニヒル・ゼロ》", 34000, 74, 118),
            new BossDef(27, "b_pinnacle", "終焉神 《タルタロス・オメガ》", 380000, 280, 132),
            new BossDef(28, "b_ascension", "超越者", 850000, 380, 165));

    public static final java.util.Map<Integer, BossDef> BOSS_INDEX = new java.util.HashMap<>();
    static {
        for (BossDef b : BOSSES) BOSS_INDEX.put(b.floor(), b);
    }

    public static BossDef bossForFloor(int floor) {
        return BOSS_INDEX.get(floor);
    }

    /* ---------------- 真・最終決戦フェーズ (原作 TRUE_BOSS_PHASES) ---------------- */

    public record TruePhase(double hpAbove, String label, double fireRate, int bullets,
                            double projSpeed, double speed, boolean spiral,
                            boolean radial, boolean charge, int summon) {}

    public static final java.util.List<TruePhase> TRUE_PHASES = java.util.List.of(
            new TruePhase(0.66, "虚無の胎動", 0.9, 9, 300, 1.0, true, false, false, 0),
            new TruePhase(0.33, "深淵の咆哮", 0.62, 14, 340, 1.22, true, true, true, 0),
            new TruePhase(0.0, "終焉・万物の帰無", 0.42, 20, 390, 1.45, true, true, true, 3));

    public static TruePhase truePhaseFor(double hpRatio) {
        for (TruePhase p : TRUE_PHASES) {
            if (hpRatio > p.hpAbove()) return p;
        }
        return TRUE_PHASES.get(TRUE_PHASES.size() - 1);
    }

    /* ---------------- 終焉の座フェーズ (原作 PINNACLE_PHASES) ---------------- */

    public record PinnaclePhase(double hpAbove, String label, double fireRate, int bullets,
                                double projSpeed, double speed, boolean crossBeams,
                                boolean cataclysm, boolean dimensionalSlash, boolean shieldGuards) {}

    public static final java.util.List<PinnaclePhase> PINNACLE_PHASES = java.util.List.of(
            new PinnaclePhase(0.75, "天墜の審判", 0.50, 16, 380, 1.10, true, false, false, false),
            new PinnaclePhase(0.50, "次元断絶", 0.38, 22, 420, 1.25, true, false, true, true),
            new PinnaclePhase(0.25, "終末の咆哮", 0.30, 28, 460, 1.40, true, false, true, false),
            new PinnaclePhase(0.0, "原初崩壊・星辰帰無", 0.22, 36, 520, 1.55, true, true, false, false));

    public static PinnaclePhase pinnaclePhaseFor(double hpRatio) {
        for (PinnaclePhase p : PINNACLE_PHASES) {
            if (hpRatio > p.hpAbove()) return p;
        }
        return PINNACLE_PHASES.get(PINNACLE_PHASES.size() - 1);
    }

    /* ---------------- ランダムイベント (原作 RANDOM_EVENTS 7種) ---------------- */

    public record RandomEvent(String id, String name, String desc, String kind, String fate) {}

    public static final java.util.List<RandomEvent> RANDOM_EVENTS = java.util.List.of(
            new RandomEvent("treasure", "宝の間", "隠し扉が開いた。装備が3点、転がり落ちてくる。", "loot", null),
            new RandomEvent("gamble", "賭博の祭壇", "欠片の半分を賭ける。当たれば倍、外れれば無。", "gamble", null),
            new RandomEvent("elite_ambush", "精鋭の襲撃", "鍛え抜かれた強敵が3体。", "elite", null),
            new RandomEvent("spring", "加護の泉", "全回復し、素材も得る。", "heal", null),
            new RandomEvent("curse_mist", "呪いの霧", "20秒間 被ダメ+30%。だが戦利品は2倍。", "fate", "mist"),
            new RandomEvent("timewarp", "時の歪み", "15秒間 敵が半分の速さ。魂ゲージ2倍。", "fate", "timewarp"),
            new RandomEvent("swarm_call", "群れの呼び声", "大量の敵が押し寄せる。", "swarm", null));

    public static final java.util.Map<String, RandomEvent> RANDOM_EVENT_INDEX =
            RANDOM_EVENTS.stream().collect(java.util.stream.Collectors.toMap(RandomEvent::id, e -> e));

    public static RandomEvent randomEventById(String id) {
        return RANDOM_EVENT_INDEX.get(id);
    }

    /* ---------------- 地脈の共鳴 (バニラバイオームとの融合要素) ----------------
     * 現在いるバニラバイオームが、その土地の性質を深淵の戦闘へ滲ませる。
     * 効果は接辞キー束 (EXTRA_* と同じ感覚) で、StatCalc.extraStat が合算する。
     */
    public record BiomeEcho(String biome, String name, String desc, java.util.Map<String, Double> mods) {}

    public static final java.util.List<BiomeEcho> BIOME_ECHOS = java.util.List.of(
            new BiomeEcho("minecraft:soul_sand_valley", "魂谷の共鳴", "魂ゲージ増加 +15%",
                    java.util.Map.of("energyPct", 15.0)),
            new BiomeEcho("minecraft:crimson_forest", "焔森の共鳴", "攻撃力 +8%",
                    java.util.Map.of("damagePct", 8.0)),
            new BiomeEcho("minecraft:warped_forest", "歪森の共鳴", "攻撃範囲 +12%",
                    java.util.Map.of("areaPct", 12.0)),
            new BiomeEcho("minecraft:basalt_deltas", "玄武の共鳴", "被ダメ軽減 +8% / 攻撃力 +4%",
                    java.util.Map.of("armorPct", 8.0, "damagePct", 4.0)),
            new BiomeEcho("minecraft:deep_dark", "深闇の共鳴", "魂ゲージ +20% / 攻撃力 -5% (踏み鳴らすな)",
                    java.util.Map.of("energyPct", 20.0, "damagePct", -5.0)),
            new BiomeEcho("minecraft:lush_caves", "翠窟の共鳴", "HP回復 +1/秒",
                    java.util.Map.of("regenFlat", 1.0)),
            new BiomeEcho("minecraft:dripstone_caves", "鍾乳の共鳴", "攻撃力 +12%",
                    java.util.Map.of("damagePct", 12.0)),
            new BiomeEcho("minecraft:frozen_peaks", "氷峰の共鳴", "凍結 +15%",
                    java.util.Map.of("freezePct", 15.0)),
            new BiomeEcho("minecraft:nether_wastes", "焔海の共鳴", "攻撃力 +6%",
                    java.util.Map.of("damagePct", 6.0)),
            new BiomeEcho("minecraft:the_end", "虚界の共鳴", "会心威力 +12% / 会心率 +4%",
                    java.util.Map.of("critDmgPct", 12.0, "critPct", 4.0)),
            new BiomeEcho("minecraft:cherry_grove", "花霞の共鳴", "HP回復 +0.5/秒 / 移動速度 +5%",
                    java.util.Map.of("regenFlat", 0.5, "moveSpeedPct", 5.0)),
            new BiomeEcho("minecraft:pale_garden", "白庭の共鳴", "被ダメ軽減 +10% / 攻撃力 -3% (静かすぎて眠い)",
                    java.util.Map.of("armorPct", 10.0, "damagePct", -3.0)),
            new BiomeEcho("minecraft:badlands", "赤土の共鳴", "攻撃射程 +0.6m",
                    java.util.Map.of("reach", 1.0)),
            new BiomeEcho("minecraft:warm_ocean", "潮海の共鳴", "攻撃範囲 +10% / HP回復 +0.5/秒",
                    java.util.Map.of("areaPct", 10.0, "regenFlat", 0.5)));

    public static final java.util.Map<String, BiomeEcho> BIOME_ECHO_INDEX =
            BIOME_ECHOS.stream().collect(java.util.stream.Collectors.toMap(BiomeEcho::biome, e -> e));

    public static BiomeEcho biomeEchoById(String biomeId) {
        return BIOME_ECHO_INDEX.get(biomeId);
    }

    /* ---------------- 原作 RARITY_META (希少度の重み) ---------------- */

    /** 原作 rollRarity の写し. l = 運 + 層×1.6 + ボーナス (鍛冶場65・層主130) */
    public static String rollRarity(java.util.function.DoubleSupplier r, int luck, double bonus, int floor) {
        double l = luck + floor * 1.6 + bonus;
        double wNormal = Math.max(4, 46 - l * 0.55);
        double wMagic = 29 + l * 0.05;
        double wRare = 15 + l * 0.32;
        double wLeg = Math.max(0.4, 7.5 + l * 0.20);
        double wMyth = Math.max(0.05, 2.5 * 0.35 + l * 0.075);
        double total = wNormal + wMagic + wRare + wLeg + wMyth;
        double x = r.getAsDouble() * total;
        if ((x -= wNormal) <= 0) return "normal";
        if ((x -= wMagic) <= 0) return "magic";
        if ((x -= wRare) <= 0) return "rare";
        if ((x -= wLeg) <= 0) return "legendary";
        return "mythic";
    }

    /** 希少度の序列 (0=normal, 1=magic, 2=rare, 3=legendary, 4=mythic)。未知の値は0 */
    public static int rarityRank(String rarity) {
        return switch (rarity) {
            case "magic" -> 1;
            case "rare" -> 2;
            case "legendary" -> 3;
            case "mythic" -> 4;
            default -> 0;
        };
    }

    /** 装備合成の素材種別 (希少度上位側で判定)。レア未満は null (欠片のみで足りる) */
    public record MergeMaterial(String id, String name, int count) {}

    public static MergeMaterial mergeMaterial(String topRarity) {
        return switch (topRarity) {
            case "mythic" -> new MergeMaterial("void", "虚空のエッセンス×2", 2);
            case "legendary" -> new MergeMaterial("chaos", "混沌の宝石×1", 1);
            case "rare" -> new MergeMaterial("ancient", "古代の石×1", 1);
            default -> null;
        };
    }

    /* ---------------- 原作 ELITE_PREFIX (精鋭の冠詞6種) ---------------- */

    public record ElitePrefix(String name, double speed, double hp, double dmg) {}

    public static final java.util.List<ElitePrefix> ELITE_PREFIX = java.util.List.of(
            new ElitePrefix("狂乱の", 1.45, 0.9, 1.1),
            new ElitePrefix("重厚な", 0.72, 2.6, 1.3),
            new ElitePrefix("灼熱の", 1.1, 1.3, 1.35),
            new ElitePrefix("凍てつく", 0.95, 1.5, 1.2),
            new ElitePrefix("雷光の", 1.25, 1.2, 1.45),
            new ElitePrefix("太古の", 0.9, 3.2, 1.6));

    public static ElitePrefix elitePrefix(java.util.function.DoubleSupplier r) {
        int idx = Math.min(ELITE_PREFIX.size() - 1, (int) (r.getAsDouble() * ELITE_PREFIX.size()));
        return ELITE_PREFIX.get(idx);
    }

    /* ---------------- 原作 ELITE_TRAITS (精鋭の特殊行動6種) ---------------- */

    public record EliteTrait(String id, String name, String desc) {}

    public static final java.util.List<EliteTrait> ELITE_TRAITS = java.util.List.of(
            new EliteTrait("regen", "再生", "絶え間なくHPが再生する"),
            new EliteTrait("shield", "魔盾", "通常の倍のHPを盾で受け持つ"),
            new EliteTrait("splitter", "分裂者", "撃破した瞬間に2体の小敵へ分裂"),
            new EliteTrait("burst", "爆砕", "死の間際、周囲を炸裂させる"),
            new EliteTrait("warp", "跳躍者", "間合いを詰めるたびにテレポートする"),
            new EliteTrait("vampire", "吸魂", "攻撃が命中するたびHPを吸収する"));

    public static EliteTrait eliteTraitById(String id) {
        for (EliteTrait t : ELITE_TRAITS) {
            if (t.id().equals(id)) return t;
        }
        return null;
    }

    /** 原作: 精鋭の60%が特性を1つ持つ */
    public static EliteTrait rollEliteTrait(java.util.function.DoubleSupplier r) {
        if (r.getAsDouble() >= 0.6) return null;
        int idx = Math.min(ELITE_TRAITS.size() - 1, (int) (r.getAsDouble() * ELITE_TRAITS.size()));
        return ELITE_TRAITS.get(idx);
    }

    /* ---------------- 悪夢 (NIGHTMARE) の特殊行動6種 (精鋭の先の段階) ---------------- */

    public record NightmareTrait(String id, String name, String desc) {}

    public static final java.util.List<NightmareTrait> NIGHTMARE_TRAITS = java.util.List.of(
            new NightmareTrait("vanish", "幻影", "被弾25%で瞬間移動し短時間攻撃上昇"),
            new NightmareTrait("siphon", "魂蝕", "攻撃命中でプレイヤーの魂ゲージを吸う"),
            new NightmareTrait("clone", "影分身", "撃破時50%で高攻撃の残像を1体残す"),
            new NightmareTrait("siege", "攻城", "遠距離から予兆つき範囲爆発を撃つ"),
            new NightmareTrait("berserk", "狂乱", "HP半分以下で攻撃と速度が大幅上昇 (1回)"),
            new NightmareTrait("wither", "衰弱", "攻撃命中で弱体効果を付与"));

    /** 重複なしで count 個の悪夢特性を引く (Fisher–Yates で混ぜて先頭から) */
    public static java.util.List<NightmareTrait> rollNightmareTraits(java.util.function.DoubleSupplier r, int count) {
        var pool = new java.util.ArrayList<NightmareTrait>(NIGHTMARE_TRAITS);
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = (int) (r.getAsDouble() * (i + 1));
            java.util.Collections.swap(pool, i, j);
        }
        return pool.subList(0, Math.min(count, pool.size()));
    }

    public static NightmareTrait nightmareTraitById(String id) {
        for (NightmareTrait t : NIGHTMARE_TRAITS) {
            if (t.id().equals(id)) return t;
        }
        return null;
    }

    /* ---------------- 原作 CURSED_MODS (呪い装備の弱点6種) ---------------- */

    public record CursedMod(String key, double min, double max) {}

    public static final java.util.List<CursedMod> CURSED_MODS = java.util.List.of(
            new CursedMod("maxHpPct", -18, -9),
            new CursedMod("moveSpeedPct", -12, -6),
            new CursedMod("armorPct", -9, -4),
            new CursedMod("attackSpeedPct", -14, -8),
            new CursedMod("luckFlat", -30, -12),
            new CursedMod("xpPct", -20, -10));

    /** 原作: レア以上で12%が呪い */
    public static boolean rollCursed(java.util.function.DoubleSupplier r, String rarity) {
        if (!rarity.equals("rare") && !rarity.equals("legendary") && !rarity.equals("mythic")) return false;
        return r.getAsDouble() < 0.12;
    }

    public static CursedMod rollCursedMod(java.util.function.DoubleSupplier r) {
        int idx = Math.min(CURSED_MODS.size() - 1, (int) (r.getAsDouble() * CURSED_MODS.size()));
        return CURSED_MODS.get(idx);
    }

    /** 呪い値の抽選 (min〜max の一様乱数、小数1桁) */
    public static double rollCursedValue(java.util.function.DoubleSupplier r, CursedMod mod) {
        return Math.round((mod.min() + r.getAsDouble() * (mod.max() - mod.min())) * 10) / 10.0;
    }

    /* ---------------- 原作 WEAPON_CLASSES (41種の装備ボーナス) ---------------- */

    public record WeaponBonus(double damagePct, double attackSpeedPct, double areaPct, double energyPct,
                              int projectiles, int pierce, double critPct, double critDmgPct,
                              double explodePct, double moveSpeedPct, double maxHpPct) {}

    private static WeaponBonus wb(double dmg, double aspd, double area, double energy,
                                  int proj, int pierce, double crit, double critDmg,
                                  double explode, double move, double maxHp) {
        return new WeaponBonus(dmg, aspd, area, energy, proj, pierce, crit, critDmg, explode, move, maxHp);
    }

    public static final java.util.Map<String, WeaponBonus> WEAPON_BONUS = java.util.Map.ofEntries(
            java.util.Map.entry("sword", wb(0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("axe", wb(18, -12, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("spear", wb(0, 0, 28, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("staff", wb(-8, 0, 0, 35, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("bow", wb(0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0)),
            java.util.Map.entry("dagger", wb(0, 0, 0, 0, 0, 0, 12, 30, 0, 0, 0)),
            java.util.Map.entry("hammer", wb(0, -20, 0, 0, 0, 0, 0, 0, 22, 0, 0)),
            java.util.Map.entry("katana", wb(10, 8, 0, 0, 0, 0, 6, 0, 0, 0, 0)),
            java.util.Map.entry("scythe", wb(14, 0, 18, 0, 0, 0, 0, 0, 0, 0, -6)),
            java.util.Map.entry("gauntlet", wb(-14, 28, 0, 0, 0, 0, 0, 0, 0, 8, 0)),
            java.util.Map.entry("chakram", wb(0, -8, 20, 0, 0, 3, 0, 0, 0, 0, 0)),
            java.util.Map.entry("tome", wb(22, -26, 0, 0, 0, 0, 0, 0, 35, 0, 0)),
            java.util.Map.entry("rune_blade", wb(8, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("blood_axe", wb(24, -14, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("storm_spear", wb(0, 10, 20, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("void_staff", wb(-5, 0, 0, 40, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("moon_bow", wb(0, 0, 0, 0, 1, 2, 0, 0, 0, 0, 0)),
            java.util.Map.entry("fang_dagger", wb(0, 0, 0, 0, 0, 0, 15, 40, 0, 0, 0)),
            java.util.Map.entry("quake_hammer", wb(0, -24, 0, 0, 0, 0, 0, 0, 30, 0, 0)),
            java.util.Map.entry("frost_katana", wb(12, 0, 10, 0, 0, 0, 8, 0, 0, 0, 0)),
            java.util.Map.entry("soul_scythe", wb(16, 0, 0, 25, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("titan_gauntlet", wb(10, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("eclipse_chakram", wb(0, 0, 15, 0, 0, 4, 0, 0, 0, 0, 0)),
            java.util.Map.entry("chaos_tome", wb(18, -20, 0, 0, 0, 0, 0, 0, 40, 0, 0)),
            java.util.Map.entry("rod", wb(0, 0, 15, 20, 1, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("wand", wb(-10, 0, 0, 15, 2, 0, 8, 0, 0, 0, 0)),
            java.util.Map.entry("greatsword", wb(30, -25, 10, 0, 0, 0, 0, 0, 0, 0, 0)),
            java.util.Map.entry("twin_dagger", wb(-5, 20, 0, 0, 0, 0, 10, 0, 0, 0, 0)),
            java.util.Map.entry("war_horn", wb(0, -10, 0, 0, 0, 0, 0, 0, 0, 0, 15)),
            java.util.Map.entry("wind_blade", wb(0, 14, 22, 0, 0, 2, 0, 0, 0, 0, 0)),
            java.util.Map.entry("ember_flail", wb(10, -6, 12, 0, 0, 0, 0, 0, 18, 0, 0)),
            java.util.Map.entry("astral_lance", wb(6, 0, 8, 30, 1, 0, 8, 0, 0, 0, 0)),
            java.util.Map.entry("tide_trident", wb(-6, 0, 25, 0, 0, 1, 0, 0, 0, 6, 6)),
            java.util.Map.entry("moon_mace", wb(6, -15, 15, 0, 0, 0, 0, 0, 10, 0, 10)),
            java.util.Map.entry("depth_gun", wb(4, 6, 0, 0, 1, 0, 10, 30, 0, 0, 0)),
            java.util.Map.entry("venom_whip", wb(0, 6, 10, 0, 0, 0, 0, 0, 0, 4, 0)),
            java.util.Map.entry("flame_grimoire", wb(12, -20, 0, 20, 0, 0, 0, 0, 30, 0, 0)),
            java.util.Map.entry("rift_blade", wb(10, 0, 0, 0, 0, 0, 12, 0, 0, 6, 0)),
            java.util.Map.entry("meteor_lance", wb(20, -18, 8, 0, 0, 1, 0, 0, 12, 0, 0)),
            java.util.Map.entry("echo_katana", wb(8, 12, 0, 0, 0, 0, 10, 0, 0, 0, 0)),
            java.util.Map.entry("howling_claw", wb(-6, 22, 0, 0, 0, 0, 0, 25, 0, 10, 0)));

    public static WeaponBonus weaponBonus(String styleId) {
        return WEAPON_BONUS.getOrDefault(styleId,
                new WeaponBonus(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    /* ---------------- 武器種ごとの「搦み」 (射程・横斬り・連撃の素値) ----------------
     * 原作の武器クラス形状差をMCへ。StatCalc.shapeOf が参照する。
     * reach = 攻撃射程 (バニラ属性 + 奥義の突進/横薙距離) /
     * sweep = 横方向の斬り幅 (半角ラジアンの拡大) / strikes = 一打で同時に斬る隣接敵数。
     * 未登録の武器種はボーナスなし扱い。
     */
    public record ShapeBonus(double reach, double sweep, double strikes) {}

    public static final java.util.Map<String, ShapeBonus> WEAPON_SHAPE = java.util.Map.ofEntries(
            java.util.Map.entry("spear", new ShapeBonus(2, 0, 0)),
            java.util.Map.entry("storm_spear", new ShapeBonus(2, 0, 0)),
            java.util.Map.entry("tide_trident", new ShapeBonus(1, 0, 0)),
            java.util.Map.entry("astral_lance", new ShapeBonus(1, 0, 0)),
            java.util.Map.entry("bow", new ShapeBonus(1, 0, 0)),
            java.util.Map.entry("moon_bow", new ShapeBonus(1, 0, 0)),
            java.util.Map.entry("greatsword", new ShapeBonus(1, 1, 0)),
            java.util.Map.entry("katana", new ShapeBonus(0, 1, 0)),
            java.util.Map.entry("frost_katana", new ShapeBonus(0, 1, 0)),
            java.util.Map.entry("axe", new ShapeBonus(0, 1, 0)),
            java.util.Map.entry("scythe", new ShapeBonus(0, 2, 0)),
            java.util.Map.entry("ember_flail", new ShapeBonus(0, 2, 0)),
            java.util.Map.entry("chakram", new ShapeBonus(0, 1, 0)),
            java.util.Map.entry("wind_blade", new ShapeBonus(0, 1, 0)),
            java.util.Map.entry("war_horn", new ShapeBonus(0, 1, 0)),
            java.util.Map.entry("twin_dagger", new ShapeBonus(0, 0, 1)),
            java.util.Map.entry("gauntlet", new ShapeBonus(0, 0, 1)),
            java.util.Map.entry("titan_gauntlet", new ShapeBonus(0, 0, 1)),
            java.util.Map.entry("moon_mace", new ShapeBonus(0, 1, 1)),
            java.util.Map.entry("depth_gun", new ShapeBonus(1, 0, 1)),
            java.util.Map.entry("venom_whip", new ShapeBonus(0, 2, 0)),
            java.util.Map.entry("flame_grimoire", new ShapeBonus(0, 1, 0)),
            java.util.Map.entry("rift_blade", new ShapeBonus(1, 0, 1)),
            java.util.Map.entry("meteor_lance", new ShapeBonus(1, 0, 0)),
            java.util.Map.entry("echo_katana", new ShapeBonus(0, 1, 1)),
            java.util.Map.entry("howling_claw", new ShapeBonus(0, 0, 2)));

    /* ---------------- 武器の左クリック追撃 (WEAPON_FOLLOWUP) ----------------
     * 武器種ごとの固有連打ボーナス。一定回数の通常攻撃ごとに追加効果が発動する。
     * 発動ロジックは WeaponHitHandler.applyFollowup (FOLLOW_HIT 添付でカウント)。
     * 未登録の武器種はボーナスなし扱い。
     */
    public static final java.util.Map<String, String> WEAPON_FOLLOWUP = java.util.Map.ofEntries(
            java.util.Map.entry("spear", "pierce3"),           // 毎3打目: 前方貫通衝撃波
            java.util.Map.entry("storm_spear", "pierce3"),
            java.util.Map.entry("tide_trident", "pierce3"),
            java.util.Map.entry("meteor_lance", "pierce3"),
            java.util.Map.entry("hammer", "burst4"),           // 毎4打目: 周囲爆発
            java.util.Map.entry("quake_hammer", "burst4"),
            java.util.Map.entry("moon_mace", "burst4"),
            java.util.Map.entry("dagger", "backflip4"),        // 毎4打目: 後方跳ね返し
            java.util.Map.entry("twin_dagger", "backflip4"),
            java.util.Map.entry("fang_dagger", "backflip4"),
            java.util.Map.entry("gauntlet", "soul3"),          // 毎3打目: 魂+3
            java.util.Map.entry("titan_gauntlet", "soul3"),
            java.util.Map.entry("depth_gun", "soul3"),
            java.util.Map.entry("greatsword", "swipe5"),       // 毎5打目: 太い横薙ぎ
            java.util.Map.entry("rune_blade", "swipe5"),
            java.util.Map.entry("war_horn", "thorn3"),         // 毎3打目: 周囲ノックバック
            java.util.Map.entry("staff", "heal6"),             // 毎6打目: 小回復
            java.util.Map.entry("void_staff", "heal6"),
            java.util.Map.entry("scythe", "crit3"),            // 毎3打目: 追加打撃
            java.util.Map.entry("ember_flail", "crit3"),
            java.util.Map.entry("echo_katana", "crit3"),
            java.util.Map.entry("howling_claw", "crit3"));

    /* ---------------- 全武器の固有能力 (命中・撃破・被弾で発動) ----------------
     * kind: CHAIN (確率連鎖) / FROST (確率凍結) / SOUL (命中で魂) /
     *       EXECUTE (瀕死へ倍率) / BURST (確率爆発) / GUARD (被弾時反射) / POISON (確率猛毒)
     */
    public record WeaponAbility(String kind, double chance, double value, String desc) {}

    public static final java.util.Map<String, WeaponAbility> WEAPON_ABILITIES =
            java.util.Map.ofEntries(
                    java.util.Map.entry("sword", new WeaponAbility("SOUL", 0.5, 3, "命中時50%で魂+3")),
                    java.util.Map.entry("axe", new WeaponAbility("BURST", 0.15, 1.0, "命中時15%で爆発")),
                    java.util.Map.entry("spear", new WeaponAbility("EXECUTE", 1.0, 0.4, "瀕死へ+40%")),
                    java.util.Map.entry("staff", new WeaponAbility("SOUL", 0.6, 5, "命中時60%で魂+5")),
                    java.util.Map.entry("bow", new WeaponAbility("CHAIN", 0.2, 0.5, "命中時20%で連鎖")),
                    java.util.Map.entry("dagger", new WeaponAbility("EXECUTE", 1.0, 0.5, "瀕死へ+50%")),
                    java.util.Map.entry("hammer", new WeaponAbility("BURST", 0.25, 1.5, "命中時25%で爆発")),
                    java.util.Map.entry("katana", new WeaponAbility("FROST", 0.3, 0.8, "命中時30%で凍結")),
                    java.util.Map.entry("scythe", new WeaponAbility("SOUL", 0.4, 6, "命中時40%で魂+6")),
                    java.util.Map.entry("gauntlet", new WeaponAbility("BURST", 0.2, 0.8, "命中時20%で爆発")),
                    java.util.Map.entry("chakram", new WeaponAbility("CHAIN", 0.25, 0.4, "命中時25%で連鎖")),
                    java.util.Map.entry("tome", new WeaponAbility("FROST", 0.2, 1.2, "命中時20%で凍結")),
                    java.util.Map.entry("rune_blade", new WeaponAbility("FROST", 0.35, 1.0, "命中時35%で凍結")),
                    java.util.Map.entry("blood_axe", new WeaponAbility("EXECUTE", 1.0, 0.6, "瀕死へ+60%")),
                    java.util.Map.entry("storm_spear", new WeaponAbility("CHAIN", 0.3, 0.7, "命中時30%で連鎖")),
                    java.util.Map.entry("void_staff", new WeaponAbility("SOUL", 0.5, 8, "命中時50%で魂+8")),
                    java.util.Map.entry("moon_bow", new WeaponAbility("CHAIN", 0.3, 0.5, "命中時30%で連鎖")),
                    java.util.Map.entry("fang_dagger", new WeaponAbility("EXECUTE", 1.0, 0.7, "瀕死へ+70%")),
                    java.util.Map.entry("quake_hammer", new WeaponAbility("BURST", 0.3, 2.0, "命中時30%で爆発")),
                    java.util.Map.entry("frost_katana", new WeaponAbility("FROST", 0.5, 1.0, "命中時50%で凍結")),
                    java.util.Map.entry("soul_scythe", new WeaponAbility("SOUL", 1.0, 4, "命中で魂+4")),
                    java.util.Map.entry("titan_gauntlet", new WeaponAbility("BURST", 0.2, 1.2, "命中時20%で爆発")),
                    java.util.Map.entry("eclipse_chakram", new WeaponAbility("CHAIN", 0.35, 0.5, "命中時35%で連鎖")),
                    java.util.Map.entry("chaos_tome", new WeaponAbility("FROST", 0.25, 1.5, "命中時25%で凍結")),
                    java.util.Map.entry("rod", new WeaponAbility("SOUL", 0.5, 6, "命中時50%で魂+6")),
                    java.util.Map.entry("wand", new WeaponAbility("CHAIN", 0.2, 0.4, "命中時20%で連鎖")),
                    java.util.Map.entry("greatsword", new WeaponAbility("BURST", 0.2, 1.8, "命中時20%で爆発")),
                    java.util.Map.entry("twin_dagger", new WeaponAbility("EXECUTE", 1.0, 0.4, "瀕死へ+40%")),
                    java.util.Map.entry("war_horn", new WeaponAbility("GUARD", 0.3, 0.5, "被弾時30%で反射")),
                    java.util.Map.entry("wind_blade", new WeaponAbility("CHAIN", 0.3, 0.5, "命中時30%で連鎖")),
                    java.util.Map.entry("ember_flail", new WeaponAbility("BURST", 0.22, 1.6, "命中時22%で爆発")),
                    java.util.Map.entry("astral_lance", new WeaponAbility("SOUL", 0.5, 5, "命中時50%で魂+5")),
                    java.util.Map.entry("tide_trident", new WeaponAbility("GUARD", 0.3, 0.5, "被弾時30%で反射")),
                    java.util.Map.entry("moon_mace", new WeaponAbility("BURST", 0.2, 1.2, "命中時20%で爆発")),
                    java.util.Map.entry("depth_gun", new WeaponAbility("EXECUTE", 1.0, 0.45, "瀕死へ+45%")),
                    java.util.Map.entry("venom_whip", new WeaponAbility("POISON", 0.3, 1.0, "命中時30%で猛毒")),
                    java.util.Map.entry("flame_grimoire", new WeaponAbility("BURST", 0.25, 1.5, "命中時25%で爆発")),
                    java.util.Map.entry("rift_blade", new WeaponAbility("CHAIN", 0.3, 0.6, "命中時30%で連鎖")),
                    java.util.Map.entry("meteor_lance", new WeaponAbility("BURST", 0.3, 2.2, "命中時30%で爆発")),
                    java.util.Map.entry("echo_katana", new WeaponAbility("SOUL", 0.4, 4, "命中時40%で魂+4")),
                    java.util.Map.entry("howling_claw", new WeaponAbility("EXECUTE", 1.0, 0.6, "瀕死へ+60%")));

    /* ---------------- 武器小技 (右クリック。CT1~3秒で連発できる軽い技) ----------------
     * mult は攻撃力倍率 (複数ヒットは合計値)。cd は基底CT (秒) で、
     * 最終CTは職業補正 {@link HeroClass#lightCdMult()} を掛けて 1.0~3.0秒にクランプする。
     * 近接軽量=低CT低威力、重量・魔法・遠距離=高CT中威力 (職業の持ち味に合わせた配分)。
     */
    public record LightSkill(String name, String desc, float cd, double mult) {}

    public static final java.util.Map<String, LightSkill> WEAPON_LIGHTS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("sword", new LightSkill("疾刃", "前方を素早く斬る", 1.2f, 0.9)),
                    java.util.Map.entry("axe", new LightSkill("裂砕打", "前方を砕き吹き飛ばす", 2.6f, 1.8)),
                    java.util.Map.entry("spear", new LightSkill("穿突", "直線を貫いて突く", 1.8f, 1.3)),
                    java.util.Map.entry("staff", new LightSkill("星弾", "最も近い敵へ小隕石", 2.0f, 1.2)),
                    java.util.Map.entry("bow", new LightSkill("三連矢", "3本の矢を自動照準で放つ", 1.5f, 1.35)),
                    java.util.Map.entry("dagger", new LightSkill("影刺", "最寄り敵へ2連の急所突き", 1.0f, 1.1)),
                    java.util.Map.entry("hammer", new LightSkill("粉砕落とし", "単体に極大の一撃", 2.8f, 2.2)),
                    java.util.Map.entry("katana", new LightSkill("居合", "前方を一閃し敵弾を斬る", 1.3f, 1.1)),
                    java.util.Map.entry("scythe", new LightSkill("小刈り", "周囲を軽く刈る", 1.6f, 0.85)),
                    java.util.Map.entry("gauntlet", new LightSkill("震掌", "前方へ3連の掌打", 1.1f, 1.5)),
                    java.util.Map.entry("chakram", new LightSkill("旋輪", "周囲に輪刃を回す", 1.7f, 0.8)),
                    java.util.Map.entry("tome", new LightSkill("呪言", "敵位置を呪い爆縮する", 2.2f, 1.3)),
                    java.util.Map.entry("rune_blade", new LightSkill("刻印斬", "3方向にルーン斬撃", 1.4f, 1.0)),
                    java.util.Map.entry("blood_axe", new LightSkill("血啜り", "斬りつけ生命を吸う", 2.4f, 1.6)),
                    java.util.Map.entry("storm_spear", new LightSkill("雷槍", "直線を雷で貫く", 1.9f, 1.25)),
                    java.util.Map.entry("void_staff", new LightSkill("虚無弾", "虚無の弾を落とす", 2.0f, 1.1)),
                    java.util.Map.entry("moon_bow", new LightSkill("月影矢", "3本の月矢を連射する", 1.6f, 1.65)),
                    java.util.Map.entry("fang_dagger", new LightSkill("牙突き", "最寄りへ2連の牙", 1.0f, 1.0)),
                    java.util.Map.entry("quake_hammer", new LightSkill("地割れ", "地面を割って鈍足にする", 2.8f, 1.7)),
                    java.util.Map.entry("frost_katana", new LightSkill("氷一閃", "凍てつく一閃", 1.4f, 1.0)),
                    java.util.Map.entry("soul_scythe", new LightSkill("魂削り", "刈って魂を削り取る", 1.7f, 0.85)),
                    java.util.Map.entry("titan_gauntlet", new LightSkill("岩砕拳", "岩をも砕く大拳", 2.2f, 1.8)),
                    java.util.Map.entry("eclipse_chakram", new LightSkill("蝕回し", "蝕の輪を回す", 1.8f, 0.9)),
                    java.util.Map.entry("chaos_tome", new LightSkill("混沌の言葉", "混沌の弾で無作為に弱体", 2.2f, 1.3)),
                    java.util.Map.entry("rod", new LightSkill("嵐の弾", "嵐の魔弾を3連で放つ", 1.8f, 2.1)),
                    java.util.Map.entry("wand", new LightSkill("小魔弾", "1発の魔弾を放つ", 1.2f, 0.8)),
                    java.util.Map.entry("greatsword", new LightSkill("大薙ぎ", "広く薙ぎ払う", 2.4f, 1.7)),
                    java.util.Map.entry("twin_dagger", new LightSkill("双牙乱れ", "前方を3連で突く", 1.0f, 1.35)),
                    java.util.Map.entry("war_horn", new LightSkill("威嚇咆哮", "咆哮で押し退け力を得る", 2.5f, 0.8)),
                    java.util.Map.entry("wind_blade", new LightSkill("風輪斬", "風の輪を放つ", 1.5f, 0.9)),
                    java.util.Map.entry("ember_flail", new LightSkill("焔鞭撃", "炎の鞭で薙ぎ発火", 1.8f, 1.0)),
                    java.util.Map.entry("astral_lance", new LightSkill("星槍一撃", "星の槍を落とす", 1.9f, 1.3)),
                    java.util.Map.entry("tide_trident", new LightSkill("潮突き", "潮と共に突いて押し流す", 1.8f, 1.2)),
                    java.util.Map.entry("moon_mace", new LightSkill("月打ち上げ", "打ち上げて殴る", 2.6f, 1.9)),
                    java.util.Map.entry("depth_gun", new LightSkill("急所射撃", "最も近い敵を狙撃する", 1.4f, 0.85)),
                    java.util.Map.entry("venom_whip", new LightSkill("毒鞭薙ぎ", "毒の鞭で薙ぎ払う", 1.7f, 0.85)),
                    java.util.Map.entry("flame_grimoire", new LightSkill("焔弾", "炎の弾で焼き尽くす", 2.0f, 1.2)),
                    java.util.Map.entry("rift_blade", new LightSkill("虚斬", "空間ごと斬り裂く", 1.5f, 1.1)),
                    java.util.Map.entry("meteor_lance", new LightSkill("隕落撃", "隕石の槍を落とす", 2.8f, 2.0)),
                    java.util.Map.entry("echo_katana", new LightSkill("残影二刀", "残影が追い斬る", 1.4f, 1.2)),
                    java.util.Map.entry("howling_claw", new LightSkill("哮爪撃", "3連の爪で引き裂く", 1.2f, 1.65)));

    /** 武器小技のCT (秒)。職業補正を掛け 1.0~3.0秒にクランプ */
    public static double lightCooldown(HeroClass cls, WeaponStyle style) {
        LightSkill l = WEAPON_LIGHTS.get(style.id);
        if (l == null) return 2.0;
        return Math.max(1.0, Math.min(3.0, l.cd() * cls.lightCdMult()));
    }

    /* ---------------- ダンジョン特性 (層ごとに65%で1つ。異変とは別枠) ---------------- */
    public record DungeonTrait(String id, String name, String desc,
                               double hp, double dmg, double speed, double spawn, double loot, double detection) {}

    public static final java.util.List<DungeonTrait> DUNGEON_TRAITS = java.util.List.of(
            new DungeonTrait("pressure", "重圧", "敵攻撃+25%", 1.0, 1.25, 1.0, 1.0, 1.0, 1.0),
            new DungeonTrait("fortress", "堅牢", "敵HP+30%", 1.3, 1.0, 1.0, 1.0, 1.0, 1.0),
            new DungeonTrait("gale", "疾風", "敵移動+20%", 1.0, 1.0, 1.2, 1.0, 1.0, 1.0),
            new DungeonTrait("swarm", "群生", "出現数+40%", 1.0, 1.0, 1.0, 1.4, 1.0, 1.0),
            new DungeonTrait("bounty", "豊穣", "戦利品+50%", 1.0, 1.0, 1.0, 1.0, 1.5, 1.0),
            new DungeonTrait("deaths", "死地", "敵HP・攻撃+15%", 1.15, 1.15, 1.0, 1.0, 1.0, 1.0),
            new DungeonTrait("mist", "濃霧", "索敵+50%", 1.0, 1.0, 1.0, 1.0, 1.0, 1.5),
            new DungeonTrait("barren", "痩せ地", "出現-30%・戦利品+50%", 1.0, 1.0, 1.0, 0.7, 1.5, 1.0),
            new DungeonTrait("embergrave", "焼け跡", "敵HP+20%・攻撃+20%・戦利品+30%", 1.2, 1.2, 1.0, 1.0, 1.3, 1.05),
            new DungeonTrait("quicksand", "流砂", "敵移動+28%・敵HP-8%", 0.92, 1.0, 1.28, 1.0, 1.0, 1.05),
            new DungeonTrait("nocturne", "夜想曲", "索敵+60%・出現-20%", 1.0, 1.05, 1.0, 0.8, 1.1, 1.6));

    public static DungeonTrait dungeonById(String id) {
        for (DungeonTrait t : DUNGEON_TRAITS) {
            if (t.id().equals(id)) return t;
        }
        return null;
    }

    /* ---------------- 全モブの固有能力 (spec id -> 能力種) ----------------
     * THORNS (被弾時15%反射) / BURST (死亡時爆発) / WARP (被弾時30%転移) /
     * SPLIT (死亡時40%で子スライム) / LEECH (与ダメの10%吸収) / SNARE (25%で鈍足)
     */
    public static final java.util.Map<String, String> MOB_ABILITIES = java.util.Map.ofEntries(
            java.util.Map.entry("iron_guard", "THORNS"),
            java.util.Map.entry("necromancer", "WARP"),
            java.util.Map.entry("cursed_gate", "THORNS"),
            java.util.Map.entry("slime", "SPLIT"),
            java.util.Map.entry("bat", "SNARE"),
            java.util.Map.entry("goblin", "LEECH"),
            java.util.Map.entry("skeleton", "THORNS"),
            java.util.Map.entry("imp", "BURST"),
            java.util.Map.entry("bomber", "BURST"),
            java.util.Map.entry("splitter", "THORNS"),
            java.util.Map.entry("wraith", "WARP"),
            java.util.Map.entry("golem", "THORNS"),
            java.util.Map.entry("phantom", "WARP"),
            java.util.Map.entry("cultist", "LEECH"),
            java.util.Map.entry("eye", "SNARE"),
            java.util.Map.entry("mirrorbat", "SNARE"),
            java.util.Map.entry("charger", "BURST"),
            java.util.Map.entry("dragonkin", "LEECH"));

    /* ---------------- 追加コンテンツの汎用ステータス (恩寵・技能・遺物・防具set) ----------------
     * 追加の恩寵・技能・遺物・防具セット効果はすべて接辞キー (damagePct 等) の束で表し、
     * RunManager.extraStat が合算して各計算式に反映する。振る舞い系フックは不要。
     */
    public static final java.util.Map<String, java.util.Map<String, Double>> EXTRA_BOON_MODS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("b_power", java.util.Map.of("damagePct", 15.0)),
                    java.util.Map.entry("b_swift", java.util.Map.of("attackSpeedPct", 12.0)),
                    java.util.Map.entry("b_guard", java.util.Map.of("armorPct", 10.0)),
                    java.util.Map.entry("b_dash", java.util.Map.of("moveSpeedPct", 10.0)),
                    java.util.Map.entry("b_eye", java.util.Map.of("critPct", 6.0)),
                    java.util.Map.entry("b_fang", java.util.Map.of("critDmgPct", 30.0)),
                    java.util.Map.entry("b_leech", java.util.Map.of("lifestealPct", 3.0)),
                    java.util.Map.entry("b_mend", java.util.Map.of("regenFlat", 1.0)),
                    java.util.Map.entry("b_luck", java.util.Map.of("luckFlat", 20.0)),
                    java.util.Map.entry("b_soul", java.util.Map.of("energyPct", 20.0)),
                    java.util.Map.entry("b_wide", java.util.Map.of("areaPct", 12.0)),
                    java.util.Map.entry("b_blast", java.util.Map.of("explodePct", 10.0)),
                    java.util.Map.entry("b_frost", java.util.Map.of("freezePct", 12.0)),
                    java.util.Map.entry("b_body", java.util.Map.of("maxHpPct", 12.0)),
                    java.util.Map.entry("b_pierce", java.util.Map.of("pierce", 1.0)),
                    java.util.Map.entry("b_chain", java.util.Map.of("chain", 1.0)),
                    java.util.Map.entry("b_volley", java.util.Map.of("projectiles", 1.0)),
                    java.util.Map.entry("b_flat", java.util.Map.of("damageFlat", 8.0)),
                    java.util.Map.entry("b_vital", java.util.Map.of("maxHpFlat", 30.0)),
                    java.util.Map.entry("b_drain", java.util.Map.of("lifestealPct", 5.0, "damagePct", 10.0)),
                    java.util.Map.entry("b_hawk", java.util.Map.of("moveSpeedPct", 15.0, "damagePct", 8.0)),
                    java.util.Map.entry("b_aegis", java.util.Map.of("armorPct", 14.0)),
                    java.util.Map.entry("b_inferno", java.util.Map.of("explodePct", 18.0, "damagePct", 4.0)),
                    java.util.Map.entry("b_squall", java.util.Map.of("attackSpeedPct", 8.0, "areaPct", 10.0)),
                    java.util.Map.entry("b_luna", java.util.Map.of("critPct", 4.0, "critDmgPct", 20.0)),
                    java.util.Map.entry("b_midas", java.util.Map.of("luckFlat", 15.0, "regenFlat", 0.5)),
                    java.util.Map.entry("b_web", java.util.Map.of("freezePct", 15.0, "chain", 1.0)),
                    java.util.Map.entry("b_giant", java.util.Map.of("maxHpPct", 18.0, "maxHpFlat", 25.0)),
                    java.util.Map.entry("b_reach", java.util.Map.of("reach", 1.0)),
                    java.util.Map.entry("b_sweep", java.util.Map.of("sweep", 1.0)),
                    java.util.Map.entry("b_barrage", java.util.Map.of("strikes", 1.0)));

    public static final java.util.Map<String, java.util.Map<String, Double>> EXTRA_PASSIVE_MODS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("fighting_spirit", java.util.Map.of("damagePct", 18.0)),
                    java.util.Map.entry("last_bet", java.util.Map.of("damagePct", 35.0, "maxHpPct", -20.0)),
                    java.util.Map.entry("acrobat", java.util.Map.of("attackSpeedPct", 12.0, "moveSpeedPct", 8.0)),
                    java.util.Map.entry("iron_wall", java.util.Map.of("armorPct", 12.0, "maxHpPct", 10.0)),
                    java.util.Map.entry("sniper", java.util.Map.of("critDmgPct", 40.0, "critPct", -3.0)),
                    java.util.Map.entry("runner", java.util.Map.of("moveSpeedPct", 15.0, "attackSpeedPct", 5.0)),
                    java.util.Map.entry("miser", java.util.Map.of("maxHpPct", 20.0, "moveSpeedPct", -5.0)),
                    java.util.Map.entry("executioner", java.util.Map.of("critPct", 10.0, "damagePct", 8.0)),
                    java.util.Map.entry("ascetic", java.util.Map.of("energyPct", 25.0, "damagePct", -5.0)),
                    java.util.Map.entry("regenerate", java.util.Map.of("regenFlat", 1.5)),
                    java.util.Map.entry("lucky", java.util.Map.of("luckFlat", 30.0, "critPct", 3.0)),
                    java.util.Map.entry("destroyer", java.util.Map.of("areaPct", 15.0, "damagePct", -8.0)),
                    java.util.Map.entry("bomber", java.util.Map.of("explodePct", 15.0, "armorPct", -5.0)),
                    java.util.Map.entry("vampire", java.util.Map.of("lifestealPct", 4.0, "maxHpPct", -8.0)),
                    java.util.Map.entry("freezer", java.util.Map.of("freezePct", 20.0, "attackSpeedPct", -5.0)),
                    java.util.Map.entry("berserk", java.util.Map.of("damagePct", 45.0, "armorPct", -15.0, "maxHpPct", -15.0)),
                    java.util.Map.entry("hellfire", java.util.Map.of("damagePct", 30.0, "explodePct", 20.0, "maxHpPct", -10.0)),
                    java.util.Map.entry("moon_gaze", java.util.Map.of("critPct", 15.0, "energyPct", 15.0, "armorPct", -6.0)),
                    java.util.Map.entry("ironhide", java.util.Map.of("armorPct", 18.0, "moveSpeedPct", -8.0)),
                    java.util.Map.entry("phantom", java.util.Map.of("moveSpeedPct", 25.0, "damagePct", -10.0)),
                    java.util.Map.entry("gluttony", java.util.Map.of("lifestealPct", 8.0, "damagePct", 10.0, "maxHpPct", -12.0)),
                    java.util.Map.entry("ascension", java.util.Map.of("energyPct", 40.0, "armorPct", -10.0)),
                    java.util.Map.entry("plague", java.util.Map.of("freezePct", 30.0, "damagePct", -12.0)),
                    java.util.Map.entry("titan_heart", java.util.Map.of("maxHpPct", 30.0, "damagePct", -8.0)));

    public static final java.util.Map<String, java.util.Map<String, Double>> EXTRA_RELIC_MODS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("r_blade", java.util.Map.of("damagePct", 20.0)),
                    java.util.Map.entry("r_wind", java.util.Map.of("attackSpeedPct", 10.0)),
                    java.util.Map.entry("r_plate", java.util.Map.of("armorPct", 15.0)),
                    java.util.Map.entry("r_heart", java.util.Map.of("maxHpPct", 20.0)),
                    java.util.Map.entry("r_eye", java.util.Map.of("critPct", 8.0)),
                    java.util.Map.entry("r_fang", java.util.Map.of("lifestealPct", 5.0)),
                    java.util.Map.entry("r_orb", java.util.Map.of("energyPct", 30.0)),
                    java.util.Map.entry("r_wave", java.util.Map.of("areaPct", 15.0)),
                    java.util.Map.entry("r_bomb", java.util.Map.of("explodePct", 15.0)),
                    java.util.Map.entry("r_boot", java.util.Map.of("moveSpeedPct", 12.0)),
                    java.util.Map.entry("r_spring", java.util.Map.of("regenFlat", 2.0)),
                    java.util.Map.entry("r_star", java.util.Map.of("luckFlat", 25.0)),
                    java.util.Map.entry("r_gale", java.util.Map.of("attackSpeedPct", 15.0, "moveSpeedPct", 8.0)),
                    java.util.Map.entry("r_moon", java.util.Map.of("critPct", 10.0, "critDmgPct", 22.0)),
                    java.util.Map.entry("r_core", java.util.Map.of("areaPct", 20.0, "pierce", 1.0)),
                    java.util.Map.entry("r_flame", java.util.Map.of("explodePct", 25.0, "damagePct", 6.0)),
                    java.util.Map.entry("r_tide", java.util.Map.of("armorPct", 8.0, "maxHpPct", 10.0)),
                    java.util.Map.entry("r_grail", java.util.Map.of("energyPct", 35.0, "regenFlat", 1.0)));

    /** 深淵防具セット (2部位・4部位ボーナス)。部位は abyss_<set>_helm/chest/legs/boots */
    public record ArmorSet(String id, String name, int trimColor, int accentColor,
                           java.util.Map<String, Double> bonus2, java.util.Map<String, Double> bonus4) {}

    public static final java.util.List<ArmorSet> ARMOR_SETS = java.util.List.of(
            new ArmorSet("blood", "血装", 0xff4d6d, 0xffd0d8,
                    java.util.Map.of("lifestealPct", 3.0),
                    java.util.Map.of("damagePct", 12.0, "lifestealPct", 3.0)),
            new ArmorSet("shadow", "影装", 0x8a7cff, 0xe0d8ff,
                    java.util.Map.of("moveSpeedPct", 10.0),
                    java.util.Map.of("critPct", 8.0, "attackSpeedPct", 10.0)),
            new ArmorSet("rock", "岩装", 0xc9a227, 0xffe9a8,
                    java.util.Map.of("armorPct", 8.0),
                    java.util.Map.of("maxHpPct", 15.0, "armorPct", 8.0)),
            new ArmorSet("star", "星装", 0x7ce8ff, 0xe0f8ff,
                    java.util.Map.of("energyPct", 20.0),
                    java.util.Map.of("energyPct", 20.0, "areaPct", 12.0)),
            new ArmorSet("frost", "氷装", 0xaee9ff, 0xe8f8ff,
                    java.util.Map.of("freezePct", 8.0),
                    java.util.Map.of("freezePct", 8.0, "armorPct", 10.0)),
            new ArmorSet("storm", "嵐装", 0xffe14a, 0xfff2c9,
                    java.util.Map.of("attackSpeedPct", 8.0),
                    java.util.Map.of("attackSpeedPct", 8.0, "chain", 1.0)),
            new ArmorSet("moon", "月装", 0xa8e8ff, 0xeaffff,
                    java.util.Map.of("energyPct", 15.0),
                    java.util.Map.of("energyPct", 15.0, "critDmgPct", 15.0)),
            new ArmorSet("ember", "焔装", 0xff6a2b, 0xffd0a0,
                    java.util.Map.of("explodePct", 8.0),
                    java.util.Map.of("explodePct", 8.0, "damagePct", 8.0)));

    public static ArmorSet armorSetById(String id) {
        for (ArmorSet s : ARMOR_SETS) {
            if (s.id().equals(id)) return s;
        }
        return null;
    }

    /* ---------------- 装備スキル (武器kill系・防具tick系。装備に付随する能力) ---------------- */
    public record GearSkill(String id, String name, String desc) {}

    /** 武器スキル (撃破時)。abyss_skill に刻印 */
    public static final java.util.List<GearSkill> WEAPON_SKILLS = java.util.List.of(
            new GearSkill("w_soul", "魂収穫", "撃破ごとに魂ゲージ+8"),
            new GearSkill("w_feast", "血宴", "撃破時に最大HPの5%回復"),
            new GearSkill("w_chain", "連鎖殺戮", "撃破時に周囲へ攻撃力の80%"),
            new GearSkill("w_break", "崩し", "撃破時に周囲へ弱体5秒"),
            new GearSkill("w_swift", "疾殺", "撃破時に移動速度上昇5秒"),
            new GearSkill("w_burst", "爆裂殺", "撃破時に小爆発"),
            new GearSkill("w_venge", "復讐の銘", "撃破時に攻撃力上昇5秒"),
            new GearSkill("w_tide", "潮の招き", "撃破時に周囲の敵を引き寄せ鈍足"),
            new GearSkill("w_gale", "追風の銘", "撃破時に移動速度3秒+魂+6"),
            new GearSkill("w_ignite", "火種の銘", "撃破時に周囲の敵を発火"),
            new GearSkill("w_greed", "強奪の銘", "撃破時 20%で魂の欠片+1"),
            new GearSkill("w_hymn", "鎮魂の銘", "撃破で守護4秒・小回復"),
            new GearSkill("w_warcry", "戦の銘", "撃破で攻撃・採掘5秒"),
            new GearSkill("w_avalanche", "雪崩の銘", "撃破で周囲3体を引き寄せる"),
            new GearSkill("w_storm", "稲妻の銘", "撃破で最寄りの敵に落雷"),
            new GearSkill("w_rend", "撕裂の銘", "撃破で周囲に凋み5秒"));

    /** 防具スキル (部位ごと)。abyss_skill に刻印 */
    public static final java.util.List<GearSkill> ARMOR_SKILLS = java.util.List.of(
            new GearSkill("h_danger", "危険感知", "兜: 10m内の敵を発光表示"),
            new GearSkill("h_breath", "水の加護", "兜: 水中呼吸が続く"),
            new GearSkill("c_thorns", "荊棘の鎧", "鎧: 受けた近接の20%を反射"),
            new GearSkill("c_aegis", "聖盾", "鎧: 守護効果が絶えない"),
            new GearSkill("l_leap", "落法", "脚: 落下ダメージ半減"),
            new GearSkill("b_dash", "疾駆", "靴: 撃破で回避CTリセット"),
            new GearSkill("c_frost", "霜の鎧", "鎧: 被弾時に周囲を氷結"),
            new GearSkill("c_soul", "魂導の鎧", "鎧: 被弾で魂ゲージ+4"),
            new GearSkill("h_warmth", "炉火の兜", "兜: 炎上を自動的に鎮める"),
            new GearSkill("l_feather", "羽毛の脚", "脚: 常時緩降下"),
            new GearSkill("b_magnet", "磁石の靴", "靴: 8m内の落とし物を吸い寄せる"),
            new GearSkill("b_vigor", "敏捷の靴", "靴: 回避CT -10%"),
            new GearSkill("h_hawk", "鷹眼の兜", "兜: 攻撃射程 +1"),
            new GearSkill("l_sweep", "横薙の脚", "脚: 横斬り (薙ぎ幅) +1"),
            new GearSkill("b_barrage", "連撃の靴", "靴: 一打の連撃 +1"),
            new GearSkill("h_swift", "迅径の兜", "兜: 常時移動速度 +8%"),
            new GearSkill("c_guard", "鉄壁の鎧", "鎧: 被ダメ -8% (追加軽減)"),
            new GearSkill("l_rock", "磐座の脚", "脚: 落下ダメージを8割軽減"),
            new GearSkill("b_charge", "跳上げの靴", "靴: 常時跳躍力II"));

    public static GearSkill gearSkillById(String id) {
        for (GearSkill s : WEAPON_SKILLS) {
            if (s.id().equals(id)) return s;
        }
        for (GearSkill s : ARMOR_SKILLS) {
            if (s.id().equals(id)) return s;
        }
        return null;
    }

    /* ---------------- 属性10種・節目ボーナス ----------------
     * 原作 attributes.ts の5属性 + 追加5属性 (集中・活力・技巧・採掘・跳躍)。
     * 各属性は 10/20/30 点で節目ボーナス (接辞キー束) が付く。
     */
    public static final String[] ATTR_NAMES = {"剛力", "韻律", "敏捷", "忍耐", "幸運", "集中", "活力", "技巧", "採掘", "跳躍"};
    public static final String[] ATTR_SHORT = {"剛", "韻", "敏", "忍", "幸", "集", "活", "技", "採", "跳"};
    public static final String[] ATTR_DESCS = {
            "攻撃力 +3%/点", "攻撃速度 +2%/点", "移動速度 +1.5%・回避短縮/点",
            "最大HP +4%/点", "運 +7・素材 +4%/点",
            "会心率 +0.8%・会心威力 +2%/点", "HP再生 +0.15/秒・軽減 +0.5%/点",
            "攻撃範囲 +1.5%・爆裂 +0.8%/点",
            "掘削加速・鉱石の欠片率UP/点", "跳躍力UP・落下軽減/点"};

    public record Mile(int at, java.util.Map<String, Double> mods) {}

    public static final java.util.List<java.util.List<Mile>> ATTR_MILESTONES = java.util.List.of(
            java.util.List.of(
                    new Mile(10, java.util.Map.of("damagePct", 10.0)),
                    new Mile(20, java.util.Map.of("critDmgPct", 20.0)),
                    new Mile(30, java.util.Map.of("damagePct", 15.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("attackSpeedPct", 8.0)),
                    new Mile(20, java.util.Map.of("areaPct", 10.0)),
                    new Mile(30, java.util.Map.of("attackSpeedPct", 12.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("moveSpeedPct", 8.0)),
                    new Mile(20, java.util.Map.of("energyPct", 15.0)),
                    new Mile(30, java.util.Map.of("moveSpeedPct", 12.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("maxHpPct", 10.0)),
                    new Mile(20, java.util.Map.of("armorPct", 8.0)),
                    new Mile(30, java.util.Map.of("maxHpPct", 15.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("luckFlat", 20.0)),
                    new Mile(20, java.util.Map.of("energyPct", 15.0)),
                    new Mile(30, java.util.Map.of("luckFlat", 30.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("critPct", 5.0)),
                    new Mile(20, java.util.Map.of("critDmgPct", 25.0)),
                    new Mile(30, java.util.Map.of("critPct", 8.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("regenFlat", 1.0)),
                    new Mile(20, java.util.Map.of("armorPct", 6.0)),
                    new Mile(30, java.util.Map.of("maxHpPct", 12.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("areaPct", 8.0)),
                    new Mile(20, java.util.Map.of("explodePct", 10.0)),
                    new Mile(30, java.util.Map.of("areaPct", 12.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("luckFlat", 10.0)),
                    new Mile(20, java.util.Map.of("energyPct", 10.0)),
                    new Mile(30, java.util.Map.of("luckFlat", 15.0))),
            java.util.List.of(
                    new Mile(10, java.util.Map.of("moveSpeedPct", 6.0)),
                    new Mile(20, java.util.Map.of("armorPct", 4.0)),
                    new Mile(30, java.util.Map.of("moveSpeedPct", 8.0))));

    /** 節目の到達数 (0〜3)。表示・効果集計用 */
    public static int milestoneTier(int attrIdx, int points) {
        if (attrIdx < 0 || attrIdx >= ATTR_MILESTONES.size()) return 0;
        int t = 0;
        for (Mile m : ATTR_MILESTONES.get(attrIdx)) {
            if (points >= m.at()) t++;
        }
        return t;
    }

    /* ---------------- 原作の武器名 (接頭・接尾・伝説・神話) ---------------- */

    public static final java.util.List<String> PREFIXES = java.util.List.of(
            "焔の", "氷結の", "雷光の", "血塗れの", "古代の", "呪われし", "聖なる", "虚空の",
            "竜王の", "修羅の", "月影の", "太陽の", "深淵の", "嵐の", "骸の", "星辰の");
    public static final java.util.List<String> SUFFIXES = java.util.List.of(
            "破滅", "暴食", "疾風怒涛", "永劫", "慟哭", "天罰", "輪廻", "絶望",
            "王の栄光", "神殺し", "黄昏", "無明", "竜鱗", "殺意", "祝福", "終焉");
    public static final java.util.List<String> LEGEND_NAMES = java.util.List.of(
            "エクスカリバー", "グラム", "天羽々斬", "ミョルニル", "ガーンディーヴァ", "デュランダル",
            "ヘルメスの杖", "レーヴァテイン", "アスカロン", "ゲイ・ボルグ", "草薙剣", "ヴァジュラ");
    public static final java.util.List<String> MYTHIC_NAMES = java.util.List.of(
            "虚無神の残光", "創世の楔", "運命を断つ者", "無限の歯車", "神々の黄昏", "原初の炎",
            "時を喰らう顎", "万象の終焉", "アビス・コア", "天上の戒律");

    private static String pickName(java.util.function.DoubleSupplier r, java.util.List<String> pool) {
        return pool.get(Math.min(pool.size() - 1, (int) (r.getAsDouble() * pool.size())));
    }

    public static String prefixFor(java.util.function.DoubleSupplier r) { return pickName(r, PREFIXES); }
    public static String suffixFor(java.util.function.DoubleSupplier r) { return pickName(r, SUFFIXES); }
    public static String legendFor(java.util.function.DoubleSupplier r) { return pickName(r, LEGEND_NAMES); }
    public static String mythicFor(java.util.function.DoubleSupplier r) { return pickName(r, MYTHIC_NAMES); }

    /* ---------------- 原作 AFFIXES (武器に付く接辞・抜粋22種) ---------------- */

    public record AffixKey(String key, double min, double max, int tier) {}

    /** 原作 AFFIXES のうち武器に付くもの (slots 制限を反映)。値は min〜max (小数1桁、個数は丸め) */
    public static final java.util.List<AffixKey> WEAPON_AFFIXES = java.util.List.of(
            new AffixKey("damageFlat", 3, 7, 1),
            new AffixKey("damagePct", 4, 9, 1),
            new AffixKey("maxHpFlat", 10, 24, 1),
            new AffixKey("attackSpeedPct", 4, 9, 1),
            new AffixKey("critPct", 1.5, 3.5, 1),
            new AffixKey("moveSpeedPct", 3, 7, 1),
            new AffixKey("armorPct", 2, 5, 1),
            new AffixKey("areaPct", 4, 10, 1),
            new AffixKey("maxHpPct", 4, 9, 2),
            new AffixKey("critDmgPct", 8, 20, 2),
            new AffixKey("lifestealPct", 1, 2.4, 2),
            new AffixKey("regenFlat", 0.4, 1.1, 2),
            new AffixKey("luckFlat", 6, 16, 2),
            new AffixKey("energyPct", 8, 18, 2),
            new AffixKey("pierce", 1, 1, 3),
            new AffixKey("projectiles", 1, 1, 3),
            new AffixKey("chain", 1, 2, 3),
            new AffixKey("explodePct", 4, 10, 3),
            new AffixKey("freezePct", 5, 12, 3),
            new AffixKey("reach", 1, 1, 3),
            new AffixKey("sweep", 1, 1, 3),
            new AffixKey("strikes", 1, 1, 3));

    /** 原作 RARITY_META の接辞数 [min, max] */
    public static int[] affixCountRange(String rarity) {
        return switch (rarity) {
            case "mythic" -> new int[]{5, 6};
            case "legendary" -> new int[]{4, 5};
            case "rare" -> new int[]{3, 4};
            case "magic" -> new int[]{1, 2};
            default -> new int[]{0, 1};
        };
    }

    public record RolledAffix(String key, double value) {}

    /**
     * 接辞を n 件引く (原作 genItem: 重複なし・tierBoost で重み付け・値は小数1桁)。
     * r は [0,1) の乱数 (キー選択と値に各1回ずつ使用)。
     */
    public static java.util.List<RolledAffix> rollAffixes(java.util.function.DoubleSupplier r,
                                                          String rarity, int n) {
        return rollAffixes(r, rarity, n, WEAPON_AFFIXES);
    }

    /** 防具用の接辞プール (貫通・追加弾・連鎖は武器専用のため除外。原作の slots 制限に相当) */
    public static final java.util.List<AffixKey> ARMOR_AFFIXES = WEAPON_AFFIXES.stream()
            .filter(a -> !a.key().equals("pierce") && !a.key().equals("projectiles") && !a.key().equals("chain"))
            .toList();

    public static java.util.List<RolledAffix> rollAffixes(java.util.function.DoubleSupplier r,
                                                          String rarity, int n,
                                                          java.util.List<AffixKey> poolDef) {
        int tierBoost = rarity.equals("mythic") ? 3 : rarity.equals("legendary") ? 2 : 1;
        var pool = new java.util.ArrayList<>(poolDef);
        var out = new java.util.ArrayList<RolledAffix>();
        for (int i = 0; i < n && !pool.isEmpty(); i++) {
            double total = 0;
            for (var a : pool) total += a.tier() <= tierBoost ? 6 - a.tier() : 0.6;
            double x = r.getAsDouble() * total;
            AffixKey sel = pool.get(pool.size() - 1);
            for (var a : pool) {
                x -= a.tier() <= tierBoost ? 6 - a.tier() : 0.6;
                if (x <= 0) {
                    sel = a;
                    break;
                }
            }
            pool.remove(sel);
            double v;
            if (isWholeAffix(sel.key())) {
                v = Math.max(1, Math.round(sel.min() + r.getAsDouble() * (sel.max() - sel.min())));
            } else {
                v = Math.round((sel.min() + r.getAsDouble() * (sel.max() - sel.min())) * 10) / 10.0;
            }
            out.add(new RolledAffix(sel.key(), v));
        }
        return out;
    }

    /** 整数スロットの接辞 (1スタック単位で意味が出るもの) */
    public static boolean isWholeAffix(String key) {
        return key.equals("pierce") || key.equals("projectiles") || key.equals("chain")
                || key.equals("reach") || key.equals("sweep") || key.equals("strikes");
    }
}
