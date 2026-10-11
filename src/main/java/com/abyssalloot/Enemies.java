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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;
import java.util.Set;

/**
 * 敵の定義表 (データ) と出現・能力値の計算 (ロジック) を一箇所にまとめる.
 * 原作 engine.ts の ENEMIES / spawnEnemy / 特殊敵 (護衛兵・蘇生師・召喚門) に対応.
 *
 * 通常の敵は出現層と重みで抽選する. 特殊な敵は決まったスロットに湧く:
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

    /** 敵の定義1件. weight=0 の役割は専用スロットでのみ湧く */
    public record Spec(String id, EntityType<? extends Monster> type, String name,
                       int minFloor, int weight, Role role) {}

    public static final List<Spec> ROSTER = List.of(
            new Spec("zombie", EntityType.ZOMBIE, "朽ちた兵", 1, 10, Role.NORMAL),
            new Spec("skeleton", EntityType.SKELETON, "骸骨弓兵", 2, 8, Role.NORMAL),
            new Spec("cave_spider", EntityType.CAVE_SPIDER, "洞窟蜘蛛", 3, 8, Role.NORMAL),
            new Spec("creeper", EntityType.CREEPER, "自爆ガエル", 4, 6, Role.NORMAL),
            new Spec("witch", EntityType.WITCH, "怨念レイス", 6, 6, Role.NORMAL),
            new Spec("husk", EntityType.HUSK, "岩塊の番兵", 7, 5, Role.NORMAL),
            new Spec("blaze", EntityType.BLAZE, "炎の亡霊", 9, 5, Role.NORMAL),
            new Spec("guard", EntityType.ZOMBIE, "鉄壁の護衛兵", 4, 0, Role.GUARD),
            new Spec("necromancer", EntityType.SKELETON, "骸骨の蘇生師", 6, 0, Role.NECROMANCER),
            new Spec("gate", EntityType.ZOMBIE, "呪われた召喚門", 7, 0, Role.GATE));

    /** 1体あたりの能力値倍率 (層・呪印・難易度・モード・異変の合成) */
    public record Scale(double hp, double dmg, double speed, double detection) {}

    /** 層と現在の状態から能力値倍率を計算する */
    public static Scale scaleFor(ServerPlayer player, int floor) {
        Set<String> sigils = RunManager.sigilsOf(player);
        double hp = GameData.enemyHpMult(sigils) * RunManager.diffHpMult(player) * GameData.floorHpScale(floor);
        double dmg = GameData.enemyDmgMult(sigils) * RunManager.diffDmgMult(player) * GameData.floorDmgScale(floor);
        if (RunManager.isDaily(player)) {
            hp *= RunManager.dailyHpMult();
            dmg *= RunManager.dailyDmgMult();
        }
        var trial = GameData.trialRules(sigils);
        double speed = trial.enemySpeed();
        var mut = RunManager.currentMutation(player);
        if (mut != null) {
            hp *= mut.enemyHp();
            dmg *= mut.enemyDmg();
            speed *= mut.enemySpeed();
        }
        return new Scale(hp, dmg, speed, trial.detection());
    }

    /** 能力値を敵に適用する (HP上限は従来どおり1024で打ち切り) */
    public static void apply(Monster m, Scale s, double hpExtra) {
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(1024, hp.getBaseValue() * s.hp() * hpExtra));
            m.setHealth(m.getMaxHealth());
        }
        var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() * s.dmg());
        var spd = m.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(spd.getBaseValue() * s.speed());
        var fol = m.getAttribute(Attributes.FOLLOW_RANGE);
        if (fol != null) fol.setBaseValue(GameData.BASE_FOLLOW_RANGE * s.detection());
    }

    public static Spec byRole(Role role) {
        for (Spec s : ROSTER) {
            if (s.role() == role) return s;
        }
        throw new IllegalStateException("no spec for " + role);
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
        int total = 0;
        for (Spec s : ROSTER) {
            if (s.role() == Role.NORMAL && s.minFloor() <= floor) total += s.weight();
        }
        int r = rand.nextInt(Math.max(1, total));
        for (Spec s : ROSTER) {
            if (s.role() != Role.NORMAL || s.minFloor() > floor) continue;
            r -= s.weight();
            if (r < 0) return s;
        }
        return byRole(Role.NORMAL);
    }

    /** 定義から敵を作る. 失敗時は朽ちた兵 */
    public static Monster create(ServerLevel level, Spec spec) {
        Monster m = spec.type().create(level, EntitySpawnReason.EVENT);
        if (m == null) {
            m = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
            if (m == null) throw new IllegalStateException("mob create failed");
        }
        m.setCustomName(Component.literal("§7" + spec.name()));
        m.setCustomNameVisible(spec.role() != Role.NORMAL);
        return m;
    }

    /** 役割ごとのタグ・挙動 */
    public static void tagByRole(Monster m, Spec spec) {
        switch (spec.role()) {
            case GUARD -> m.addTag(GUARD_TAG);
            case NECROMANCER -> m.addTag(NECRO_TAG);
            case GATE -> {
                m.addTag(GATE_TAG);
                m.setNoAi(true); // 門は移動も攻撃もしない. 増援のみ (EnemyAI)
            }
            default -> { }
        }
    }

    /** 蘇生師・門の子を1体湧かせる */
    public static void spawnChild(ServerLevel level, Monster owner, Scale scale, ServerPlayer ref,
                                  double ang, double radius) {
        Spec zombie = ROSTER.get(0);
        Monster child = create(level, zombie);
        BlockPos p = owner.blockPosition().offset(
                (int) Math.round(Math.cos(ang) * radius), 1, (int) Math.round(Math.sin(ang) * radius));
        child.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        apply(child, scale, 1.0);
        child.addTag(SUMMON_TAG);
        child.addTag(OWNER_PREFIX + owner.getUUID());
        child.setTarget(ref);
        level.addFreshEntity(child);
    }

    /** 持ち主の生存中の子の数 */
    public static int childrenOf(ServerLevel level, Monster owner) {
        String tag = OWNER_PREFIX + owner.getUUID();
        return level.getEntitiesOfClass(Monster.class,
                owner.getBoundingBox().inflate(48),
                e -> e.isAlive() && e.getTags().contains(tag)).size();
    }

    /** 画面内の敵数 (上限判定用) */
    public static int aliveNear(ServerLevel level, double x, double y, double z) {
        return level.getEntitiesOfClass(Monster.class,
                new net.minecraft.world.phys.AABB(x - 64, y - 32, z - 64, x + 64, y + 32, z + 64),
                e -> e.isAlive()).size();
    }

    /** 精鋭化 (発光+体力2倍) */
    public static void makeElite(Monster m) {
        var h2 = m.getAttribute(Attributes.MAX_HEALTH);
        if (h2 != null) {
            h2.setBaseValue(Math.min(2048, h2.getBaseValue() * 2));
            m.setHealth(m.getMaxHealth());
        }
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 999999, 0, false, false));
        m.setCustomName(Component.literal("§6エリート " + m.getName().getString()));
    }
}
