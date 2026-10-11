package com.abyssalloot;

/**
 * 原作 src/game/core.ts の対応表をMCに落としたもの.
 * 数値は原作ベース値をMC向けにスケール (MCのHP20=ハート10基準ではなく、
 * MOD内では独自HPとして Attributes.MAX_HEALTH を上書きする).
 */
public final class GameData {
    private GameData() {}

    public enum HeroClass {
        SAMURAI("samurai", "剣聖", "孤高の一刀", 130, 15, 20, 1.55f, 0.08f, 1),
        MAGE("mage", "魔導士", "星詠みの術者", 84, 9, 16, 2.0f, 0.10f, 1),
        NINJA("ninja", "忍者", "影縫いの刃", 96, 11, 12, 3.1f, 0.20f, 1),
        PALADIN("paladin", "聖騎士", "不滅の盾", 190, 21, 16, 1.25f, 0.05f, 1),
        GUNNER("gunner", "銃魔", "鉛の嵐", 100, 12, 11, 3.4f, 0.14f, 2);

        public final String id;
        public final String name;
        public final String title;
        public final double baseHp;
        public final double hpPerLevel;
        public final double baseDamage;
        public final float attackSpeed;
        public final float crit;
        public final int projectiles;

        HeroClass(String id, String name, String title, double baseHp, double hpPerLevel,
                  double baseDamage, float attackSpeed, float crit, int projectiles) {
            this.id = id; this.name = name; this.title = title;
            this.baseHp = baseHp; this.hpPerLevel = hpPerLevel;
            this.baseDamage = baseDamage; this.attackSpeed = attackSpeed;
            this.crit = crit; this.projectiles = projectiles;
        }

        public static HeroClass byId(String id) {
            for (HeroClass c : values()) {
                if (c.id.equals(id)) return c;
            }
            return SAMURAI;
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

    /** 原作 trials.ts の8呪印 */
    public enum Sigil {
        MIGHT("might", "剛力", "敵HP×1.30", 18),
        FANGS("fangs", "鋭牙", "敵攻撃×1.22", 16),
        GALE("gale", "疾風", "敵移動×1.16", 14),
        FRENZY("frenzy", "乱舞", "エリート率×1.9", 22),
        FAMINE("famine", "飢餓", "回復量×0.58", 20),
        PAIN("pain", "鈍痛", "魂・奥義獲得×0.70", 16),
        GAZE("gaze", "注視", "索敵・射程×1.45", 14),
        ABYSS("abyss", "深淵", "敵HP・攻撃・移動×1.10", 26);

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

    public static double heroDamage(HeroClass cls, int level) {
        return cls.baseDamage * (1.0 + (level - 1) * 0.17);
    }

    public static double heroMaxHp(HeroClass cls, int level) {
        return cls.baseHp + (level - 1) * cls.hpPerLevel;
    }

    public static double enemyHpMult(java.util.Set<String> sigils) {
        double m = 1.0;
        if (sigils.contains("might")) m *= 1.30;
        if (sigils.contains("abyss")) m *= 1.10;
        return m;
    }

    public static double enemyDmgMult(java.util.Set<String> sigils) {
        double m = 1.0;
        if (sigils.contains("fangs")) m *= 1.22;
        if (sigils.contains("abyss")) m *= 1.10;
        return m;
    }

    public static double soulMult(java.util.Set<String> sigils) {
        int bonus = 0;
        for (Sigil s : Sigil.values()) {
            if (sigils.contains(s.id)) bonus += s.reward;
        }
        return (100 + bonus) / 100.0;
    }
}
