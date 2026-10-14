package com.abyssalloot;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * 深淵MOBの固有モデル (クライアント専用)。全17種。
 * 1.21.11 の Entity State 方式に対応 (EntityModel&lt;LivingEntityRenderState&gt;)。
 * 箱モデルのみで構成し、テクスチャは原作色のグラデーション
 * (UV位置合わせ不要のため全パート原点参照)。
 * モデル空間は 16px = 1ブロック、足元 (地面) が y=24。
 */
public final class AbyssModels {
    private AbyssModels() {}

    public static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, name), "main");
    }

    public static final ModelLayerLocation SLIME = layer("slime");
    public static final ModelLayerLocation SPLITTER = layer("splitter");
    public static final ModelLayerLocation BAT = layer("bat");
    public static final ModelLayerLocation GOBLIN = layer("goblin");
    public static final ModelLayerLocation SKELETON = layer("skeleton");
    public static final ModelLayerLocation IMP = layer("imp");
    public static final ModelLayerLocation BOMBER = layer("bomber");
    public static final ModelLayerLocation WRAITH = layer("wraith");
    public static final ModelLayerLocation GOLEM = layer("golem");
    public static final ModelLayerLocation PHANTOM = layer("phantom");
    public static final ModelLayerLocation CULTIST = layer("cultist");
    public static final ModelLayerLocation EYE = layer("eye");
    public static final ModelLayerLocation CHARGER = layer("charger");
    public static final ModelLayerLocation DRAGONKIN = layer("dragonkin");
    public static final ModelLayerLocation GUARD = layer("guard");
    public static final ModelLayerLocation NECROMANCER = layer("necromancer");
    public static final ModelLayerLocation GATE = layer("gate");
    public static final ModelLayerLocation GOD = layer("god");

    private static void box(PartDefinition root, String name,
                            float x, float y, float z, float dx, float dy, float dz,
                            float ox, float oy, float oz) {
        root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, dx, dy, dz, new CubeDeformation(0.0f)),
                PartPose.offset(ox, oy, oz));
    }

    private abstract static class Base extends EntityModel<LivingEntityRenderState> {
        protected Base(ModelPart root) {
            super(root, RenderTypes::entityCutoutNoCull);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
        }
    }

    private abstract static class Winged extends Base {
        protected final ModelPart wingL;
        protected final ModelPart wingR;

        protected Winged(ModelPart root) {
            super(root);
            this.wingL = root.getChild("wingL");
            this.wingR = root.getChild("wingR");
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float flap = Mth.sin(state.ageInTicks * 0.6f) * 0.45f;
            wingL.zRot = 0.35f + flap;
            wingR.zRot = -0.35f - flap;
        }
    }

    private abstract static class Walker extends Base {
        protected final ModelPart legL;
        protected final ModelPart legR;
        protected final ModelPart armL;
        protected final ModelPart armR;

        protected Walker(ModelPart root) {
            super(root);
            this.legL = root.getChild("legL");
            this.legR = root.getChild("legR");
            this.armL = root.getChild("armL");
            this.armR = root.getChild("armR");
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.4f * state.walkAnimationSpeed;
            legL.xRot = swing;
            legR.xRot = -swing;
            armL.xRot = -swing * 0.8f;
            armR.xRot = swing * 0.8f;
        }
    }

    // ---- スライム系 ----

    public static class SlimeModel extends Base {
        public SlimeModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "body", -5, 16, -5, 10, 8, 10, 0, 0, 0);
            box(root, "inner", -3, 18, -3, 6, 6, 6, 0, 0, 0);
            box(root, "eyeL", -3, 18, -5.2f, 2, 3, 1, 0, 0, 0);
            box(root, "eyeR", 1, 18, -5.2f, 2, 3, 1, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    public static class SplitterModel extends Base {
        public SplitterModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "body", -10, 10, -10, 20, 14, 20, 0, 0, 0);
            box(root, "inner", -6, 13, -6, 12, 9, 12, 0, 0, 0);
            box(root, "eyeL", -5, 14, -10.2f, 3, 4, 1, 0, 0, 0);
            box(root, "eyeR", 2, 14, -10.2f, 3, 4, 1, 0, 0, 0);
            box(root, "bud", 6, 6, 6, 5, 5, 5, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- コウモリ ----

    public static class BatModel extends Winged {
        public BatModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "body", -2, 17, -3, 4, 5, 6, 0, 0, 0);
            box(root, "head", -2.5f, 14, -4, 5, 4, 4, 0, 0, 0);
            box(root, "earL", -2.5f, 12, -3, 1.5f, 2, 1, 0, 0, 0);
            box(root, "earR", 1, 12, -3, 1.5f, 2, 1, 0, 0, 0);
            box(root, "wingL", 0, -0.5f, -2.5f, 9, 1, 5, 2, 17, 0);
            box(root, "wingR", -9, -0.5f, -2.5f, 9, 1, 5, -2, 17, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- ゴブリン (小型人型) ----

    public static class GoblinModel extends Walker {
        public GoblinModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -3, 17, -1.5f, 3, 7, 3, 0, 0, 0);
            box(root, "legR", 0, 17, -1.5f, 3, 7, 3, 0, 0, 0);
            box(root, "torso", -3.5f, 10, -2, 7, 7, 4, 0, 0, 0);
            box(root, "armL", -6.5f, 10, -1.5f, 3, 7, 3, 0, 0, 0);
            box(root, "armR", 3.5f, 10, -1.5f, 3, 7, 3, 0, 0, 0);
            box(root, "head", -3.5f, 4, -3.5f, 7, 6, 7, 0, 0, 0);
            box(root, "earL", -7.5f, 6, -1, 4, 1.5f, 2, 0, 0, 0);
            box(root, "earR", 3.5f, 6, -1, 4, 1.5f, 2, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 骸骨 (細身人型) ----

    public static class SkeletonModel extends Walker {
        public SkeletonModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -2.5f, 15, -1, 2, 9, 2, 0, 0, 0);
            box(root, "legR", 0.5f, 15, -1, 2, 9, 2, 0, 0, 0);
            box(root, "torso", -3, 7, -1.5f, 6, 8, 3, 0, 0, 0);
            box(root, "armL", -5, 7, -1, 2, 9, 2, 0, 0, 0);
            box(root, "armR", 3, 7, -1, 2, 9, 2, 0, 0, 0);
            box(root, "head", -3, 1, -3, 6, 6, 6, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- インプ (小悪魔) ----

    public static class ImpModel extends Winged {
        public ImpModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -2.5f, 20, -1, 2, 4, 2, 0, 0, 0);
            box(root, "legR", 0.5f, 20, -1, 2, 4, 2, 0, 0, 0);
            box(root, "body", -2.5f, 13, -2, 5, 7, 4, 0, 0, 0);
            box(root, "head", -3, 7, -3, 6, 6, 6, 0, 0, 0);
            box(root, "hornL", -3.5f, 4, -2, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "hornR", 2, 4, -2, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "wingL", 0, -0.5f, -1.5f, 6, 1, 3, 2, 14, 1);
            box(root, "wingR", -6, -0.5f, -1.5f, 6, 1, 3, -2, 14, 1);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 自爆ガエル (丸胴) ----

    public static class BomberModel extends Base {
        public BomberModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "body", -4.5f, 15, -4.5f, 9, 9, 9, 0, 0, 0);
            box(root, "eyeL", -3, 16, -4.7f, 2, 2, 1, 0, 0, 0);
            box(root, "eyeR", 1, 16, -4.7f, 2, 2, 1, 0, 0, 0);
            box(root, "fuse", -0.5f, 11, -0.5f, 1, 4, 1, 0, 0, 0);
            box(root, "footL", -4, 22, -3, 3, 2, 5, 0, 0, 0);
            box(root, "footR", 1, 22, -3, 3, 2, 5, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- レイス (浮遊マント) ----

    public static class WraithModel extends Base {
        private final ModelPart torso;

        public WraithModel(ModelPart root) {
            super(root);
            this.torso = root.getChild("torso");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "head", -3, 8, -3, 6, 6, 6, 0, 0, 0);
            box(root, "shoulders", -4.5f, 14, -2.5f, 9, 3, 5, 0, 0, 0);
            box(root, "torso", -3.5f, 17, -2, 7, 4, 4, 0, 0, 0);
            box(root, "tail", -2.5f, 21, -1.5f, 5, 3, 3, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            torso.xRot = Mth.sin(state.ageInTicks * 0.15f) * 0.08f;
        }
    }

    // ---- ゴーレム (巨岩) ----

    public static class GolemModel extends Base {
        public GolemModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -10, 10, -4, 8, 14, 8, 0, 0, 0);
            box(root, "legR", 2, 10, -4, 8, 14, 8, 0, 0, 0);
            box(root, "torso", -8, -2, -5, 16, 12, 10, 0, 0, 0);
            box(root, "armL", -14, 0, -3, 6, 14, 6, 0, 0, 0);
            box(root, "armR", 8, 0, -3, 6, 14, 6, 0, 0, 0);
            box(root, "head", -4, -9, -4, 8, 7, 8, 0, 0, 0);
            box(root, "brow", -4, -7, -4.5f, 8, 2, 1, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- ファントム (長身) ----

    public static class PhantomModel extends Base {
        public PhantomModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -4, 8, -2, 4, 16, 4, 0, 0, 0);
            box(root, "legR", 0, 8, -2, 4, 16, 4, 0, 0, 0);
            box(root, "torso", -4, -2, -2.5f, 8, 10, 5, 0, 0, 0);
            box(root, "armL", -8, -1, -2, 4, 15, 4, 0, 0, 0);
            box(root, "armR", 4, -1, -2, 4, 15, 4, 0, 0, 0);
            box(root, "head", -3.5f, -9, -3.5f, 7, 7, 7, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 教徒 (ローブ) ----

    public static class CultistModel extends Base {
        public CultistModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "robe", -4.5f, 8, -3.5f, 9, 16, 7, 0, 0, 0);
            box(root, "shoulders", -5, 5, -3.5f, 10, 3, 7, 0, 0, 0);
            box(root, "hood", -3.5f, -2, -3.5f, 7, 7, 7, 0, 0, 0);
            box(root, "peak", -1.5f, -5, -1.5f, 3, 3, 3, 0, 0, 0);
            box(root, "armL", -8, 8, -1.5f, 3, 8, 3, 0, 0, 0);
            box(root, "armR", 5, 8, -1.5f, 3, 8, 3, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 邪眼 (浮遊眼球) ----

    public static class EyeModel extends Base {
        private final ModelPart ball;

        public EyeModel(ModelPart root) {
            super(root);
            this.ball = root.getChild("ball");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "ball", -6, 12, -6, 12, 12, 12, 0, 0, 0);
            box(root, "iris", -2.5f, 14, -6.5f, 5, 5, 1, 0, 0, 0);
            box(root, "spikeT", -1, 8, -1, 2, 4, 2, 0, 0, 0);
            box(root, "spikeB", -1, 24, -1, 2, 2, 2, 0, 0, 0);
            box(root, "spikeL", -8, 16, -1, 2, 2, 2, 0, 0, 0);
            box(root, "spikeR", 6, 16, -1, 2, 2, 2, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            ball.zRot = Mth.sin(state.ageInTicks * 0.12f) * 0.12f;
        }
    }

    // ---- オーク (突進) ----

    public static class ChargerModel extends Base {
        public ChargerModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -5.5f, 15, -2.5f, 5, 9, 5, 0, 0, 0);
            box(root, "legR", 0.5f, 15, -2.5f, 5, 9, 5, 0, 0, 0);
            box(root, "torso", -5, 6, -3.5f, 10, 9, 7, 0, 0, 0);
            box(root, "armL", -9, 6, -2, 4, 9, 4, 0, 0, 0);
            box(root, "armR", 5, 6, -2, 4, 9, 4, 0, 0, 0);
            box(root, "head", -3.5f, 0, -3.5f, 7, 6, 7, 0, 0, 0);
            box(root, "tuskL", -3, 4, -4, 1, 2, 1, 0, 0, 0);
            box(root, "tuskR", 2, 4, -4, 1, 2, 1, 0, 0, 0);
            box(root, "padL", -9.5f, 4, -2.5f, 5, 2, 5, 0, 0, 0);
            box(root, "padR", 4.5f, 4, -2.5f, 5, 2, 5, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 竜人 (翼) ----

    public static class DragonkinModel extends Winged {
        public DragonkinModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -3.5f, 15, -1.5f, 3, 9, 3, 0, 0, 0);
            box(root, "legR", 0.5f, 15, -1.5f, 3, 9, 3, 0, 0, 0);
            box(root, "torso", -3.5f, 7, -2, 7, 8, 4, 0, 0, 0);
            box(root, "armL", -6.5f, 7, -1.5f, 3, 8, 3, 0, 0, 0);
            box(root, "armR", 3.5f, 7, -1.5f, 3, 8, 3, 0, 0, 0);
            box(root, "head", -3, 1, -3, 6, 6, 6, 0, 0, 0);
            box(root, "hornL", -3.5f, -2, -2, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "hornR", 2, -2, -2, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "wingL", 0, -1, -2.5f, 10, 1, 5, 3, 6, 1);
            box(root, "wingR", -10, -1, -2.5f, 10, 1, 5, -3, 6, 1);
            box(root, "tail", -1, 20, 1, 2, 2, 8, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 護衛兵 (盾持ち) ----

    public static class GuardModel extends Base {
        public GuardModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", -4, 15, -2, 4, 9, 4, 0, 0, 0);
            box(root, "legR", 0, 15, -2, 4, 9, 4, 0, 0, 0);
            box(root, "torso", -4, 6, -2, 8, 9, 4, 0, 0, 0);
            box(root, "armL", -8, 6, -2, 4, 9, 4, 0, 0, 0);
            box(root, "armR", 4, 6, -2, 4, 9, 4, 0, 0, 0);
            box(root, "head", -3.5f, -1, -3.5f, 7, 7, 7, 0, 0, 0);
            box(root, "helm", -4, -2, -4, 8, 4, 8, 0, 0, 0);
            box(root, "crest", -0.5f, -5, -3, 1, 3, 6, 0, 0, 0);
            box(root, "shield", -9.5f, 7, -4, 1, 11, 8, 0, 0, 0);
            box(root, "padR", 3.5f, 4, -2.5f, 5, 2, 5, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 蘇生師 (骨ローブ) ----

    public static class NecromancerModel extends Base {
        public NecromancerModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "robe", -4, 11, -3, 8, 13, 6, 0, 0, 0);
            box(root, "shoulders", -4.5f, 8, -3, 9, 3, 6, 0, 0, 0);
            box(root, "skull", -3, 2, -3, 6, 6, 6, 0, 0, 0);
            box(root, "hood", -4, -1, -4, 8, 5, 8, 0, 0, 0);
            box(root, "armL", -7, 10, -1.5f, 3, 8, 3, 0, 0, 0);
            box(root, "armR", 4, 10, -1.5f, 3, 8, 3, 0, 0, 0);
            box(root, "rib1", -3, 13, -3.2f, 6, 1, 1, 0, 0, 0);
            box(root, "rib2", -3, 16, -3.2f, 6, 1, 1, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 召喚門 (静止) ----

    public static class GateModel extends Base {
        public GateModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "pillarL", -9.5f, 4, -2.5f, 5, 20, 5, 0, 0, 0);
            box(root, "pillarR", 4.5f, 4, -2.5f, 5, 20, 5, 0, 0, 0);
            box(root, "lintel", -9.5f, -1, -2.5f, 19, 5, 5, 0, 0, 0);
            box(root, "frame", -5, 7, -0.5f, 10, 14, 1, 0, 0, 0);
            box(root, "skullT", -2, -4, -2, 4, 3, 4, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ---- 神 (層主・真・頂点・超越) ----

    public static class GodModel extends Base {
        protected final ModelPart wingL;
        protected final ModelPart wingR;

        public GodModel(ModelPart root) {
            super(root);
            this.wingL = root.getChild("wingL");
            this.wingR = root.getChild("wingR");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "robe", -4.5f, 9, -3.5f, 9, 15, 7, 0, 0, 0);
            box(root, "shoulders", -5, 5, -3.5f, 10, 4, 7, 0, 0, 0);
            box(root, "head", -3.5f, -2, -3.5f, 7, 7, 7, 0, 0, 0);
            box(root, "halo", -5.5f, -6, -5.5f, 11, 1, 11, 0, 0, 0);
            box(root, "armL", -9, 4, -2, 4, 10, 4, 0, 0, 0);
            box(root, "armR", 5, 4, -2, 4, 10, 4, 0, 0, 0);
            box(root, "wingL", 0, -1, -3, 12, 1, 6, 4, 2, 2);
            box(root, "wingR", -12, -1, -3, 12, 1, 6, -4, 2, 2);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float flap = Mth.sin(state.ageInTicks * 0.25f) * 0.3f;
            wingL.zRot = 0.3f + flap;
            wingR.zRot = -0.3f - flap;
        }
    }
}
