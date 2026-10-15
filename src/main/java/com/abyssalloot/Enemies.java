package com.abyssalloot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;
import java.util.Set;

/**
 * 敵の定義表 (データ) と出現・能力値の計算 (ロジック) を一箇所にまとめる.
 * 原作 engine.ts の ENEMIES / BOSSES / WARDENS / spawnEnemy に対応.
 *
 * 通常の敵は出現層で解禁し、重みは原作式 (1 + max(0, 6-|層-minFloor|)*0.5) で抽選する.
 * 特殊な敵は決まったスロットに湧く:
 *   護衛兵 4層〜 (i%5==3) / 蘇生師 6層〜 (i%7==5) / 召喚門 7層〜 (i%9==8)
 */
public final class Enemies {
    private Enemies() {}

    public static final String GUARD_TAG = "abyss_guard";
    public static final String NECRO_TAG = "abyss_necro";
    public static final String GATE_TAG = "abyss_gate";
    /** 蘇生師・召喚門が呼んだ子. 撃破では層のウェーブ数に数えない */
    public static final String SUMMON_TAG = "abyss_summon";
    /** 子の持ち主 (召喚者のUUID) を示すタグ接頭辞 */
    public static final String OWNER_PREFIX = "abyss_owner:";
    /** 固有AIのタグ (原作 ai に対応) */
    public static final String SHOOT_TAG = "abyss_shoot";
    public static final String CHARGE_TAG = "abyss_charge";
    public static final String SPLIT_TAG = "abyss_split";
    public static final String TELE_TAG = "abyss_tele";
    public static final String ORBIT_TAG = "abyss_orbit";
    public static final String BOMBER_TAG = "abyss_bomber";
    /** 番人 (原作 WARDENS のミニボス) */
    public static final String WARDEN_TAG = "abyss_warden";

    /** 画面内の敵の上限 (原作 42〜48 相当) */
    public static final int SCREEN_CAP = 48;
    public static final int NECRO_CHILD_CAP = 4;
    public static final int GATE_CHILD_CAP = 6;
    /** 蘇生師の召喚間隔 / 門の増援間隔 (tick) */
    public static final int NECRO_SUMMON_TICKS = 140;
    public static final int GATE_SUMMON_TICKS = 116;
    /** 門のHPは通常敵の何倍か (原作の門は耐久が高い) */
    public static final double GATE_HP_MULT = 6.0;

    public enum Role { NORMAL, GUARD, NECROMANCER, GATE }

    /** 敵の定義1件. hp/dmg/speed は原作値 (速度はゾンビ基準の相対値として適用) */
    public record Spec(String id, EntityType<? extends Mob> type, String name,
                       int minFloor, Role role, String ai, double fireRate,
                       double hp, double dmg, double speed) {
    }

    /** 原作 ENEMIES 18種 (順序・能力値は原作通り)。見た目は固有モデル (ModEntities) */
    public static final List<Spec> ROSTER = List.of(
            new Spec("iron_guard", ModEntities.GUARD.get(), "鉄壁の護衛兵", 4, Role.GUARD, "guard", 0, 76, 15, 77),
            new Spec("necromancer", ModEntities.NECROMANCER.get(), "骸骨の蘇生師", 6, Role.NECROMANCER, "summoner", 0, 68, 10, 95),
            new Spec("cursed_gate", ModEntities.GATE.get(), "呪われた召喚門", 7, Role.GATE, "gate", 0, 180, 0, 0),
            new Spec("slime", ModEntities.SLIME.get(), "腐敗スライム", 1, Role.NORMAL, "chase", 0, 22, 7, 74),
            new Spec("bat", ModEntities.BAT.get(), "血吸いコウモリ", 1, Role.NORMAL, "orbit", 0, 14, 6, 148),
            new Spec("goblin", ModEntities.GOBLIN.get(), "洞窟ゴブリン", 1, Role.NORMAL, "chase", 0, 30, 9, 96),
            new Spec("skeleton", ModEntities.SKELETON.get(), "骸骨兵", 2, Role.NORMAL, "chase", 0, 42, 12, 88),
            new Spec("imp", ModEntities.IMP.get(), "火焔インプ", 3, Role.NORMAL, "shoot", 1.7, 26, 11, 92),
            new Spec("bomber", ModEntities.BOMBER.get(), "自爆ガエル", 4, Role.NORMAL, "bomber", 0, 34, 26, 132),
            new Spec("splitter", ModEntities.SPLITTER.get(), "分裂スライム母", 5, Role.NORMAL, "split", 0, 80, 11, 66),
            new Spec("wraith", ModEntities.WRAITH.get(), "怨念レイス", 6, Role.NORMAL, "chase", 0, 52, 15, 118),
            new Spec("golem", ModEntities.GOLEM.get(), "岩塊ゴーレム", 7, Role.NORMAL, "chase", 0, 140, 22, 58),
            new Spec("phantom", ModEntities.PHANTOM.get(), "幻影ファントム", 7, Role.NORMAL, "teleport", 0, 66, 17, 96),
            new Spec("cultist", ModEntities.CULTIST.get(), "深淵教徒", 8, Role.NORMAL, "shoot", 2.0, 45, 13, 84),
            new Spec("eye", ModEntities.EYE.get(), "浮遊邪眼", 9, Role.NORMAL, "shoot", 1.35, 60, 14, 82),
            new Spec("mirrorbat", ModEntities.MIRRORBAT.get(), "分身コウモリ", 10, Role.NORMAL, "orbit", 0, 20, 5, 170),
            new Spec("charger", ModEntities.CHARGER.get(), "猪突オーク", 11, Role.NORMAL, "charge", 0, 96, 20, 108),
            new Spec("dragonkin", ModEntities.DRAGONKIN.get(), "竜人", 14, Role.NORMAL, "shoot", 1.5, 130, 24, 112));

    /** 1体あたりの能力値倍率 (層・呪印・難易度・モード・異変の合成) */
    public record Scale(double hp, double dmg, double speed, double detection) {}

    /** 層と現在の状態から能力値倍率を計算する */
    public static Scale scaleFor(ServerPlayer player, int floor) {
        Set<String> sigils = RunManager.sigilsOf(player);
        double hp = GameData.enemyHpMult(sigils) * RunManager.diffHpMult(player) * GameData.floorHpScale(floor);
        double dmg = GameData.enemyDmgMult(sigils) * RunManager.diffDmgMult(player) * GameData.floorDmgScale(floor);
        GameData.Zone zone = GameData.zoneForFloor(floor);
        double speed = GameData.trialRules(sigils).enemySpeed() * zone.enemySpeed();
        if (RunManager.isDaily(player)) {
            var dm = RunManager.dailyMod(player);
            if (dm != null) {
                hp *= dm.enemyHp();
                dmg *= dm.enemyDmg();
                speed *= dm.enemySpeed();
            }
        }
        var trial = GameData.trialRules(sigils);
        var mut = RunManager.currentMutation(player);
        if (mut != null) {
            hp *= mut.enemyHp();
            dmg *= mut.enemyDmg();
            speed *= mut.enemySpeed();
        }
        var dun = RunManager.currentDungeon(player);
        double detection = trial.detection();
        if (dun != null) {
            hp *= dun.hp();
            dmg *= dun.dmg();
            speed *= dun.speed();
            detection *= dun.detection();
        }
        return new Scale(hp, dmg, speed, detection);
    }

    /**
     * 能力値を敵に適用する (原作値をそのまま使う)。
     * HP = 原作HP × 層・呪印等の倍率 (上限2048)。攻撃 = 原作攻撃 × 倍率。
     * 速度はゾンビ基準 (0.23) に原作の相対速度を掛ける (MC調整。原作比のままでは遅すぎるため)。
     */
    public static final double SPEED_PARITY = 0.23;

    public static void apply(Mob m, Spec spec, Scale s, double hpExtra) {
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(2048, spec.hp() * s.hp() * hpExtra));
            m.setHealth(m.getMaxHealth());
        }
        var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(spec.dmg() * s.dmg());
        var spd = m.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(SPEED_PARITY * spec.speed() / 100.0 * s.speed());
        var fol = m.getAttribute(Attributes.FOLLOW_RANGE);
        if (fol != null) fol.setBaseValue(GameData.BASE_FOLLOW_RANGE * s.detection());
    }

    public static Spec byId(String id) {
        for (Spec s : ROSTER) {
            if (s.id().equals(id)) return s;
        }
        return ROSTER.get(3);
    }

    public static Spec byRole(Role role) {
        for (Spec s : ROSTER) {
            if (s.role() == role) return s;
        }
        throw new IllegalStateException("no spec for " + role);
    }

    /** EntityType から通常 Spec を逆引き (ボス等は null) */
    public static Spec specOf(Mob m) {
        if (m == null) return null;
        for (Spec s : ROSTER) {
            if (s.type() == m.getType()) return s;
        }
        return null;
    }

    /** モブの能力種 (なければ null)。表は GameData.MOB_ABILITIES */
    public static String mobAbility(Mob m) {
        Spec s = specOf(m);
        return s == null ? null : GameData.MOB_ABILITIES.get(s.id());
    }

    /** 原作の重み: 1 + max(0, 6-|層-minFloor|)*0.5 */
    public static double weightFor(Spec s, int floor) {
        return 1 + Math.max(0, 6 - Math.abs(floor - s.minFloor())) * 0.5;
    }

    /** 出現抽選. 特殊スロットを優先し、残りは層で解禁された通常敵から重み抽選 */
    public static Spec pickSpec(int floor, int slot, RandomSource rand) {
        String special = GameData.specialSlotRole(floor, slot);
        if (special != null) {
            return switch (special) {
                case "gate" -> byRole(Role.GATE);
                case "necromancer" -> byRole(Role.NECROMANCER);
                default -> byRole(Role.GUARD);
            };
        }
        double total = 0;
        for (Spec s : ROSTER) {
            if (s.role() == Role.NORMAL && s.minFloor() <= floor) total += weightFor(s, floor);
        }
        double r = rand.nextDouble() * Math.max(1e-6, total);
        for (Spec s : ROSTER) {
            if (s.role() != Role.NORMAL || s.minFloor() > floor) continue;
            r -= weightFor(s, floor);
            if (r <= 0) return s;
        }
        return byId("slime");
    }

    /** 定義から敵を作る. 失敗時は腐敗スライム */
    public static Mob create(ServerLevel level, Spec spec) {
        Mob m = spec.type().create(level, EntitySpawnReason.EVENT);
        if (m == null) {
            m = ModEntities.SLIME.get().create(level, EntitySpawnReason.EVENT);
            if (m == null) throw new IllegalStateException("mob create failed");
        }
        m.setCustomName(Component.literal("§7" + spec.name()));
        m.setCustomNameVisible(spec.role() != Role.NORMAL);
        return m;
    }

    /** 役割・AIごとのタグ付け */
    public static void tagByRole(Mob m, Spec spec) {
        switch (spec.role()) {
            case GUARD -> m.addTag(GUARD_TAG);
            case NECROMANCER -> m.addTag(NECRO_TAG);
            case GATE -> {
                m.addTag(GATE_TAG);
                m.setNoAi(true); // 門は移動も攻撃もしない. 増援のみ (EnemyAI)
            }
            default -> { }
        }
        switch (spec.ai()) {
            case "shoot" -> m.addTag(SHOOT_TAG);
            case "charge" -> m.addTag(CHARGE_TAG);
            case "split" -> m.addTag(SPLIT_TAG);
            case "teleport" -> m.addTag(TELE_TAG);
            case "orbit" -> m.addTag(ORBIT_TAG);
            case "bomber" -> m.addTag(BOMBER_TAG);
            default -> { }
        }
    }

    /** 層主の見た目 (原作 BOSSES の層ごと・固有モデル). 能力値は呼び出し側で BossDef から設定 */
    public static EntityType<? extends Monster> bossMobFor(int floor) {
        return switch (floor) {
            case 5 -> ModEntities.GOBLIN_KING.get();
            case 10 -> ModEntities.WYRM.get();
            case 15 -> ModEntities.KNIGHT.get();
            case 20 -> ModEntities.BOSS_EYE.get();
            case 26 -> ModEntities.ZERO.get();
            case 27 -> ModEntities.OMEGA.get();
            case 28 -> ModEntities.ASCENDER.get();
            default -> ModEntities.NIHIL.get();
        };
    }

    /** 番人の見た目 (岩塊ゴーレム系) */
    public static EntityType<? extends Monster> wardenMob() {
        return ModEntities.GOLEM.get();
    }

    /** 蘇生師・門の子を1体湧かせる */
    public static void spawnChild(ServerLevel level, Mob owner, Scale scale, ServerPlayer ref,
                                  double ang, double radius) {
        Spec slime = byId("slime");
        Mob child = create(level, slime);
        BlockPos p = owner.blockPosition().offset(
                (int) Math.round(Math.cos(ang) * radius), 1, (int) Math.round(Math.sin(ang) * radius));
        child.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        apply(child, slime, scale, 1.0);
        child.addTag(SUMMON_TAG);
        child.addTag(OWNER_PREFIX + owner.getUUID());
        child.setTarget(ref);
        level.addFreshEntity(child);
    }

    /** 分裂スライム母の死亡時: 小さな子を2体残す (原作 split) */
    public static void handleSplit(ServerLevel level, Mob dead, ServerPlayer ref) {
        Scale scale = scaleFor(ref, Math.max(1, ref.getData(ModAttachments.FLOOR.get())));
        for (int i = 0; i < 2; i++) {
            Spec slime = byId("slime");
            Mob child = create(level, slime);
            var p = dead.blockPosition().offset(dead.getRandom().nextInt(3) - 1, 0,
                    dead.getRandom().nextInt(3) - 1);
            child.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            apply(child, slime, scale, 0.4);
            child.addTag(SPLIT_TAG); // バニラドロップ抑止の目印 (撃破数は通常通り計上)
            child.setTarget(ref);
            level.addFreshEntity(child);
        }
    }

    /** 持ち主の生存中の子の数 */
    public static int childrenOf(ServerLevel level, Mob owner) {
        String tag = OWNER_PREFIX + owner.getUUID();
        return level.getEntitiesOfClass(Mob.class,
                owner.getBoundingBox().inflate(48),
                e -> e.isAlive() && e.getTags().contains(tag)).size();
    }

    /** 画面内の敵数 (上限判定用). Mob のみ数えるためプレイヤーは含まれない */
    public static int aliveNear(ServerLevel level, double x, double y, double z) {
        return level.getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(x - 64, y - 32, z - 64, x + 64, y + 32, z + 64),
                e -> e.isAlive()).size();
    }

    /** 精鋭の特性タグ (原作 ELITE_TRAITS に対応) */
    public static final String ET_REGEN = "abyss_et_regen";
    public static final String ET_SHIELD = "abyss_et_shield";
    public static final String ET_SPLITTER = "abyss_et_splitter";
    public static final String ET_BURST = "abyss_et_burst";
    public static final String ET_WARP = "abyss_et_warp";
    public static final String ET_VAMPIRE = "abyss_et_vampire";

    /** 精鋭化 (原作: 冠詞の能力変化 + 体力2倍 + 60%で特性1つ) */
    public static void makeElite(Mob m) {
        var rand = m.getRandom();
        var pre = GameData.elitePrefix(rand::nextDouble);
        var h2 = m.getAttribute(Attributes.MAX_HEALTH);
        if (h2 != null) {
            h2.setBaseValue(Math.min(2048, h2.getBaseValue() * 2 * pre.hp()));
            m.setHealth(m.getMaxHealth());
        }
        var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() * pre.dmg());
        var spd = m.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(spd.getBaseValue() * pre.speed());
        var trait = GameData.rollEliteTrait(rand::nextDouble);
        String traitSuffix = "";
        if (trait != null) {
            traitSuffix = "〔" + trait.name() + "〕";
            switch (trait.id()) {
                case "regen" -> m.addTag(ET_REGEN);
                case "shield" -> {
                    m.addTag(ET_SHIELD);
                    var armor = m.getAttribute(Attributes.ARMOR);
                    if (armor != null) armor.setBaseValue(armor.getBaseValue() + 10);
                }
                case "splitter" -> m.addTag(SPLIT_TAG); // 撃破時に分裂 (既存フックを再利用)
                case "burst" -> m.addTag(ET_BURST);
                case "warp" -> m.addTag(TELE_TAG); // 間合いの詰め直し (既存テレポートを再利用)
                case "vampire" -> m.addTag(ET_VAMPIRE);
                default -> { }
            }
        }
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
        m.setCustomName(Component.literal("§6" + pre.name() + "エリート " + m.getName().getString() + traitSuffix));
    }

    /** 番人化 (原作 WARDENS の倍率・発光・名札) */
    public static void makeWarden(Mob m, GameData.Warden w) {
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(4096, hp.getBaseValue() * w.hpMul()));
            m.setHealth(m.getMaxHealth());
        }
        var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() * w.dmgMul());
        m.addTag(WARDEN_TAG);
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
        m.setCustomName(Component.literal("§c番人 " + w.name()));
        m.setCustomNameVisible(true);
    }
}
