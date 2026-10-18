package com.abyssalloot;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 深淵の固有MOB (通常18 + 層主8)。見た目 (モデル・テクスチャ) を固有にするため、
 * 振る舞いを受け継ぐバニラ系統の派生サブクラスとして定義する。
 * 能力値 (HP/攻撃/速度) はすべて {@link Enemies#apply} で原作値から絶対設定するため、
 * 属性サプライヤは索敵等の土台のみ (Monster 標準) でよい。
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AbyssalLootMod.MODID);

    // ---- 通常18種 ----
    public static class SlimeMob extends Slime {
        public SlimeMob(EntityType<SlimeMob> type, Level level) { super(type, level); }
    }

    public static class BatMob extends Vex {
        public BatMob(EntityType<BatMob> type, Level level) { super(type, level); }
    }

    public static class GoblinMob extends Spider {
        public GoblinMob(EntityType<GoblinMob> type, Level level) { super(type, level); }
    }

    public static class SkeletonMob extends Zombie {
        public SkeletonMob(EntityType<SkeletonMob> type, Level level) { super(type, level); }
    }

    public static class ImpMob extends Blaze {
        public ImpMob(EntityType<ImpMob> type, Level level) { super(type, level); }
    }

    public static class BomberMob extends Creeper {
        public BomberMob(EntityType<BomberMob> type, Level level) { super(type, level); }
    }

    public static class SplitterMob extends MagmaCube {
        public SplitterMob(EntityType<SplitterMob> type, Level level) { super(type, level); }
    }

    public static class WraithMob extends Phantom {
        public WraithMob(EntityType<WraithMob> type, Level level) { super(type, level); }
    }

    public static class GolemMob extends Ravager {
        public GolemMob(EntityType<GolemMob> type, Level level) { super(type, level); }
    }

    public static class PhantomMob extends EnderMan {
        public PhantomMob(EntityType<PhantomMob> type, Level level) { super(type, level); }
    }

    public static class CultistMob extends Witch {
        public CultistMob(EntityType<CultistMob> type, Level level) { super(type, level); }
    }

    public static class EyeMob extends Vex {
        public EyeMob(EntityType<EyeMob> type, Level level) { super(type, level); }
    }

    public static class MirrorBatMob extends Vex {
        public MirrorBatMob(EntityType<MirrorBatMob> type, Level level) { super(type, level); }
    }

    public static class ChargerMob extends Vindicator {
        public ChargerMob(EntityType<ChargerMob> type, Level level) { super(type, level); }
    }

    public static class DragonkinMob extends Breeze {
        public DragonkinMob(EntityType<DragonkinMob> type, Level level) { super(type, level); }
    }

    public static class GuardMob extends Zombie {
        public GuardMob(EntityType<GuardMob> type, Level level) { super(type, level); }
    }

    public static class NecromancerMob extends Evoker {
        public NecromancerMob(EntityType<NecromancerMob> type, Level level) { super(type, level); }
    }

    public static class GateMob extends Zombie {
        public GateMob(EntityType<GateMob> type, Level level) { super(type, level); }
    }

    // ---- 層主8種 ----
    public static class GoblinKingMob extends Vindicator {
        public GoblinKingMob(EntityType<GoblinKingMob> type, Level level) { super(type, level); }
    }

    public static class WyrmMob extends Blaze {
        public WyrmMob(EntityType<WyrmMob> type, Level level) { super(type, level); }
    }

    public static class KnightMob extends Vindicator {
        public KnightMob(EntityType<KnightMob> type, Level level) { super(type, level); }
    }

    public static class BossEyeMob extends Vex {
        public BossEyeMob(EntityType<BossEyeMob> type, Level level) { super(type, level); }
    }

    public static class NihilMob extends Warden {
        public NihilMob(EntityType<NihilMob> type, Level level) { super(type, level); }
    }

    public static class ZeroMob extends Warden {
        public ZeroMob(EntityType<ZeroMob> type, Level level) { super(type, level); }
    }

    public static class OmegaMob extends Warden {
        public OmegaMob(EntityType<OmegaMob> type, Level level) { super(type, level); }
    }

    public static class AscenderMob extends WitherBoss {
        public AscenderMob(EntityType<AscenderMob> type, Level level) { super(type, level); }
    }

    private static <T extends Entity> Supplier<EntityType<T>> reg(String id,
                                                                              EntityType.EntityFactory<T> factory,
                                                                              MobCategory cat, float w, float h) {
        return TYPES.register(id, () -> EntityType.Builder.of(factory, cat).sized(w, h)
                .build(ResourceKey.create(Registries.ENTITY_TYPE,
                        Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, id))));
    }

    public static final Supplier<EntityType<SlimeMob>> SLIME = reg("abyss_slime", SlimeMob::new, MobCategory.MONSTER, 0.6f, 0.6f);
    public static final Supplier<EntityType<BatMob>> BAT = reg("abyss_bat", BatMob::new, MobCategory.MONSTER, 0.6f, 0.6f);
    public static final Supplier<EntityType<GoblinMob>> GOBLIN = reg("abyss_goblin", GoblinMob::new, MobCategory.MONSTER, 0.7f, 1.3f);
    public static final Supplier<EntityType<SkeletonMob>> SKELETON = reg("abyss_skeleton", SkeletonMob::new, MobCategory.MONSTER, 0.6f, 1.6f);
    public static final Supplier<EntityType<ImpMob>> IMP = reg("abyss_imp", ImpMob::new, MobCategory.MONSTER, 0.6f, 1.1f);
    public static final Supplier<EntityType<BomberMob>> BOMBER = reg("abyss_bomber", BomberMob::new, MobCategory.MONSTER, 0.8f, 1.0f);
    public static final Supplier<EntityType<SplitterMob>> SPLITTER = reg("abyss_splitter", SplitterMob::new, MobCategory.MONSTER, 1.4f, 1.4f);
    public static final Supplier<EntityType<WraithMob>> WRAITH = reg("abyss_wraith", WraithMob::new, MobCategory.MONSTER, 0.9f, 1.3f);
    public static final Supplier<EntityType<GolemMob>> GOLEM = reg("abyss_golem", GolemMob::new, MobCategory.MONSTER, 1.95f, 2.2f);
    public static final Supplier<EntityType<PhantomMob>> PHANTOM = reg("abyss_phantom", PhantomMob::new, MobCategory.MONSTER, 0.8f, 2.2f);
    public static final Supplier<EntityType<CultistMob>> CULTIST = reg("abyss_cultist", CultistMob::new, MobCategory.MONSTER, 0.7f, 1.7f);
    public static final Supplier<EntityType<EyeMob>> EYE = reg("abyss_eye", EyeMob::new, MobCategory.MONSTER, 1.0f, 1.0f);
    public static final Supplier<EntityType<MirrorBatMob>> MIRRORBAT = reg("abyss_mirrorbat", MirrorBatMob::new, MobCategory.MONSTER, 0.6f, 0.6f);
    public static final Supplier<EntityType<ChargerMob>> CHARGER = reg("abyss_charger", ChargerMob::new, MobCategory.MONSTER, 1.0f, 1.6f);
    public static final Supplier<EntityType<DragonkinMob>> DRAGONKIN = reg("abyss_dragonkin", DragonkinMob::new, MobCategory.MONSTER, 1.0f, 1.8f);
    public static final Supplier<EntityType<GuardMob>> GUARD = reg("abyss_guard", GuardMob::new, MobCategory.MONSTER, 0.7f, 2.0f);
    public static final Supplier<EntityType<NecromancerMob>> NECROMANCER = reg("abyss_necromancer", NecromancerMob::new, MobCategory.MONSTER, 0.7f, 1.7f);
    public static final Supplier<EntityType<GateMob>> GATE = reg("abyss_gate", GateMob::new, MobCategory.MONSTER, 1.2f, 2.4f);
    public static final Supplier<EntityType<GoblinKingMob>> GOBLIN_KING = reg("abyss_goblin_king", GoblinKingMob::new, MobCategory.MONSTER, 1.8f, 2.2f);
    public static final Supplier<EntityType<WyrmMob>> WYRM = reg("abyss_wyrm", WyrmMob::new, MobCategory.MONSTER, 2.2f, 1.8f);
    public static final Supplier<EntityType<KnightMob>> KNIGHT = reg("abyss_knight", KnightMob::new, MobCategory.MONSTER, 1.2f, 2.4f);
    public static final Supplier<EntityType<BossEyeMob>> BOSS_EYE = reg("abyss_boss_eye", BossEyeMob::new, MobCategory.MONSTER, 2.2f, 2.2f);
    public static final Supplier<EntityType<NihilMob>> NIHIL = reg("abyss_nihil", NihilMob::new, MobCategory.MONSTER, 1.4f, 2.8f);
    public static final Supplier<EntityType<ZeroMob>> ZERO = reg("abyss_zero", ZeroMob::new, MobCategory.MONSTER, 1.6f, 3.2f);
    public static final Supplier<EntityType<OmegaMob>> OMEGA = reg("abyss_omega", OmegaMob::new, MobCategory.MONSTER, 1.8f, 3.6f);
    public static final Supplier<EntityType<AscenderMob>> ASCENDER = reg("abyss_ascender", AscenderMob::new, MobCategory.MONSTER, 2.0f, 4.0f);

    /** 全種に Monster 標準の属性土台を登録 (実値は Enemies.apply で原作値から絶対設定) */
    public static void attributes(IEventBus modBus) {
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            AttributeSupplier base = Monster.createMonsterAttributes().build();
            event.put(SLIME.get(), base);
            event.put(BAT.get(), base);
            event.put(GOBLIN.get(), base);
            event.put(SKELETON.get(), base);
            event.put(IMP.get(), base);
            event.put(BOMBER.get(), base);
            event.put(SPLITTER.get(), base);
            event.put(WRAITH.get(), base);
            event.put(GOLEM.get(), base);
            event.put(PHANTOM.get(), base);
            event.put(CULTIST.get(), base);
            event.put(EYE.get(), base);
            event.put(MIRRORBAT.get(), base);
            event.put(CHARGER.get(), base);
            event.put(DRAGONKIN.get(), base);
            event.put(GUARD.get(), base);
            event.put(NECROMANCER.get(), base);
            event.put(GATE.get(), base);
            event.put(GOBLIN_KING.get(), base);
            event.put(WYRM.get(), base);
            event.put(KNIGHT.get(), base);
            event.put(BOSS_EYE.get(), base);
            event.put(NIHIL.get(), base);
            event.put(ZERO.get(), base);
            event.put(OMEGA.get(), base);
            event.put(ASCENDER.get(), base);
        });
    }
}
