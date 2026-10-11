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
        //                 id         名前    称号        HP   HP/Lv  攻撃 攻速  会心  弾数 移動 回避CT
        SAMURAI("samurai", "剣聖", "孤高の一刀", 130, 15, 20, 1.55f, 0.08f, 1, 250, 1.10f),
        MAGE("mage", "魔導士", "星詠みの術者", 84, 9, 16, 2.0f, 0.10f, 1, 244, 1.25f),
        NINJA("ninja", "忍者", "影縫いの刃", 96, 11, 12, 3.1f, 0.20f, 1, 300, 0.62f),
        PALADIN("paladin", "聖騎士", "不滅の盾", 190, 21, 16, 1.25f, 0.05f, 1, 226, 1.50f),
        GUNNER("gunner", "銃魔", "鉛の嵐", 100, 12, 11, 3.4f, 0.14f, 2, 256, 1.00f);

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

        HeroClass(String id, String name, String title, double baseHp, double hpPerLevel,
                  double baseDamage, float attackSpeed, float crit, int projectiles,
                  double moveSpeed, double dashCd) {
            this.id = id; this.name = name; this.title = title;
            this.baseHp = baseHp; this.hpPerLevel = hpPerLevel;
            this.baseDamage = baseDamage; this.attackSpeed = attackSpeed;
            this.crit = crit; this.projectiles = projectiles;
            this.moveSpeed = moveSpeed; this.dashCd = dashCd;
        }

        public static HeroClass byId(String id) {
            for (HeroClass c : values()) {
                if (c.id.equals(id)) return c;
            }
            return SAMURAI;
        }

        /** MCの移動速度属性 (既定0.1 ≒ 原作 250px/s) への換算 */
        public double mcMoveSpeed() {
            return moveSpeed / 2500.0;
        }

        /** 原作 skillName 対応 */
        public String skillName() {
            return switch (this) {
                case SAMURAI -> "絶空斬";
                case MAGE -> "メテオフォール";
                case NINJA -> "影分身乱舞";
                case PALADIN -> "ジャッジメント";
                case GUNNER -> "バレットヘル";
            };
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
        TOME("tome", "魔導書", "禁書・虚空葬", "狙った地点に虚空の爆縮・脆弱", 0xde9df7);

        public final String id;
        public final String weaponName;
        public final String artName;
        public final String artDesc;
        public final int color;

        WeaponStyle(String id, String weaponName, String artName, String artDesc, int color) {
            this.id = id; this.weaponName = weaponName;
            this.artName = artName; this.artDesc = artDesc; this.color = color;
        }

        public static WeaponStyle byId(String id) {
            for (WeaponStyle w : values()) {
                if (w.id.equals(id)) return w;
            }
            return SWORD;
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

        public static Sigil byId(String id) {
            for (Sigil s : values()) {
                if (s.id.equals(id)) return s;
            }
            return null;
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
     * 原作 calculateRunReward: 基礎魂 = 層×14 + 撃破×0.6 + レベル×8 (+勝利900 +真の深淵6000)。
     * 何も進めずに離脱した場合は 0 (無限の魂稼ぎ防止)。
     * 原作の「スコア/40」項は Mod にスコアが無いため省略している。
     */
    public static RunReward runReward(int floor, int kills, int level, boolean victory, boolean trueCleared,
                                      double modeRate, double difficultyRate, double trialMult) {
        boolean progressed = kills > 0 || floor > 1 || victory;
        int base = progressed
                ? (int) Math.round(floor * 14.0 + kills * 0.6 + level * 8.0
                        + (victory ? 900 : 0) + (trueCleared ? 6000 : 0))
                : 0;
        int before = (int) Math.round(base * modeRate * difficultyRate);
        int run = (int) Math.round(before * trialMult);
        return new RunReward(base, before, run, run - before);
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

    /** 層による敵HP倍率 (原作 1 + 層*0.22) */
    public static double floorHpScale(int floor) {
        return 1 + floor * 0.22;
    }

    /** 層による敵攻撃倍率 (原作 1 + 層*0.08) */
    public static double floorDmgScale(int floor) {
        return 1 + floor * 0.08;
    }

    /**
     * 原作 core.ts FLOOR_MUTATIONS の写し. 層の異変 (9種).
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
                    1.12, 1.15, 1.04, 0.95, 2.30, 1.3, 1.5, false));

    public static Mutation mutationById(String id) {
        for (Mutation m : MUTATIONS) {
            if (m.id().equals(id)) return m;
        }
        return null;
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
}
