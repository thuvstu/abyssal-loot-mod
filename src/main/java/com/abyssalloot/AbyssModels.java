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
 * 深淵MOBの固有モデル (クライアント専用)。原作 13 shape に準拠した全16型。
 * 1.21.11 の Entity State 方式 (EntityModel&lt;LivingEntityRenderState&gt;)。
 * モデル空間は 16px = 1ブロック、足元 (地面) が y=24、正面は -z。
 *
 * テクスチャ (64x64) の標準ゾーン (tools/generate_textures.py と対応):
 * BODY (0,0,48,32) 本体グラデ / BELLY (48,0,64,32) 内側・明部 /
 * DARK (0,32,16,48) 目・口の暗部 / WHITE (16,32,32,48) 白目・牙・光 /
 * ACC (32,32,64,48) 発光・模様 / FREE (0,48,64,64) 種別模様 (翼膜・冠・鱗など)
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
    public static final ModelLayerLocation KING = layer("king");
    public static final ModelLayerLocation DRAGON = layer("dragon");
    public static final ModelLayerLocation KNIGHT = layer("knight");
    public static final ModelLayerLocation GOD = layer("god");

    // ---- UVゾーン ----
    // BODY
    private static final int BU = 0;
    private static final int BV = 0;
    // BELLY
    private static final int LU = 48;
    private static final int LV = 0;
    // DARK
    private static final int DU = 0;
    private static final int DV = 32;
    // WHITE
    private static final int WU = 16;
    private static final int WV = 32;
    // ACC
    private static final int AU = 32;
    private static final int AV = 32;
    // FREE
    private static final int FU = 0;
    private static final int FV = 48;

    private static PartDefinition box(PartDefinition parent, String name, int u, int v,
                                      float x, float y, float z, float dx, float dy, float dz,
                                      float ox, float oy, float oz) {
        return parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, dx, dy, dz, new CubeDeformation(0.0f)),
                PartPose.offset(ox, oy, oz));
    }

    private static PartDefinition boxRot(PartDefinition parent, String name, int u, int v,
                                         float x, float y, float z, float dx, float dy, float dz,
                                         float ox, float oy, float oz, float rx, float ry, float rz) {
        return parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, dx, dy, dz, new CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(ox, oy, oz, rx, ry, rz));
    }

    private abstract static class Base extends EntityModel<LivingEntityRenderState> {
        protected Base(ModelPart root) {
            super(root, RenderTypes::entityCutoutNoCull);
        }

        protected Base(ModelPart root, boolean translucent) {
            super(root, translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutoutNoCull);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
        }
    }

    // ================= スライム系 (原作 shape: slime / ドーム+目) =================

    /** 腐敗スライム。ドーム2段+目+口+光沢。潰れアニメ。基準高 10px。 */
    public static class SlimeModel extends Base {
        protected final ModelPart body;

        public SlimeModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = box(root, "body", BU, BV, -5, 17, -5, 10, 7, 10, 0, 0, 0);
            box(body, "top", BU + 12, BV, -4, 14, -4, 8, 3, 8, 0, 0, 0);
            box(body, "belly", LU, LV, -3, 19.5f, -5.3f, 6, 3.5f, 0.5f, 0, 0, 0);
            box(body, "eyeL", DU, DV, -3.4f, 17.5f, -5.4f, 2, 2.5f, 0.6f, 0, 0, 0);
            box(body, "eyeR", DU + 3, DV, 1.4f, 17.5f, -5.4f, 2, 2.5f, 0.6f, 0, 0, 0);
            box(body, "mouth", DU + 6, DV + 4, -1, 21, -5.4f, 2, 1, 0.5f, 0, 0, 0);
            box(body, "shine", WU, WV, -4, 14.5f, -4.2f, 2, 2, 0.4f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float sq = Mth.sin(state.ageInTicks * 0.35f) * 0.09f;
            body.yScale = 1.0f + sq;
            body.xScale = 1.0f - sq * 0.7f;
            body.zScale = 1.0f - sq * 0.7f;
        }
    }

    /** 分裂スライム母。大型ドーム+側方に芽。基準高 10px (scale で拡大)。 */
    public static class SplitterModel extends Base {
        protected final ModelPart body;

        public SplitterModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = box(root, "body", BU, BV, -6, 16, -6, 12, 8, 12, 0, 0, 0);
            box(body, "top", BU + 14, BV, -4.5f, 13, -4.5f, 9, 3, 9, 0, 0, 0);
            box(body, "belly", LU, LV, -3.5f, 18.5f, -6.3f, 7, 4, 0.5f, 0, 0, 0);
            box(body, "eyeL", DU, DV, -4, 16.5f, -6.4f, 2.5f, 3, 0.6f, 0, 0, 0);
            box(body, "eyeR", DU + 3, DV, 1.5f, 16.5f, -6.4f, 2.5f, 3, 0.6f, 0, 0, 0);
            box(body, "bud", BU + 28, BV + 10, 5, 15, 3, 5, 6, 5, 0, 0, 0);
            box(body, "budEye", DU + 6, DV, 6, 17, -2.2f, 1.5f, 2, 0.5f, 0, 0, 0);
            box(body, "spot1", FU + 20, FV, -5, 20, 2, 3, 3, 0.5f, 0, 0, 0);
            box(body, "spot2", FU + 26, FV + 4, 0, 21, 5.8f, 3, 2.5f, 0.5f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float sq = Mth.sin(state.ageInTicks * 0.3f) * 0.08f;
            body.yScale = 1.0f + sq;
            body.xScale = 1.0f - sq * 0.7f;
            body.zScale = 1.0f - sq * 0.7f;
        }
    }

    /** 自爆ガエル。丸胴+導火線+火花+怒り目。基準高 10px。 */
    public static class BomberModel extends Base {
        private final ModelPart body;
        private final ModelPart spark;

        public BomberModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
            this.spark = root.getChild("spark");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = box(root, "body", BU, BV, -4.5f, 15, -4.5f, 9, 9, 9, 0, 0, 0);
            box(body, "top", BU + 10, BV, -3, 12.5f, -3, 6, 2.5f, 6, 0, 0, 0);
            box(body, "belly", LU, LV, -3, 18, -4.8f, 6, 4, 0.5f, 0, 0, 0);
            boxRot(body, "eyeL", DU, DV, -3.2f, 16, -4.9f, 2.2f, 2, 0.6f, 0, 0, 0, 0, 0, 0.35f);
            boxRot(body, "eyeR", DU + 3, DV, 1, 16, -4.9f, 2.2f, 2, 0.6f, 0, 0, 0, 0, 0, -0.35f);
            box(body, "fangL", WU, WV, -2, 20.5f, -4.9f, 1, 1.5f, 0.5f, 0, 0, 0);
            box(body, "fangR", WU + 2, WV, 1, 20.5f, -4.9f, 1, 1.5f, 0.5f, 0, 0, 0);
            box(body, "footL", BU + 22, BV + 12, -4, 22, -3, 3, 2, 5, 0, 0, 0);
            box(body, "footR", BU + 22, BV + 12, 1, 22, -3, 3, 2, 5, 0, 0, 0);
            box(root, "fuse", DU + 8, DV, -0.5f, 9.5f, -0.5f, 1, 3, 1, 0, 0, 0);
            box(root, "spark", AU, AV, -1, 7.5f, -1, 2, 2, 2, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            body.xRot = Mth.sin(state.ageInTicks * 0.25f) * 0.05f;
            float fl = 1.0f + Mth.sin(state.ageInTicks * 1.7f) * 0.18f;
            spark.xScale = fl;
            spark.yScale = fl;
            spark.zScale = fl;
        }
    }

    // ================= コウモリ (原作 shape: bat / 大翼+耳) =================

    /** 血吸いコウモリ・分身コウモリ。楕円胴+大翼+耳+牙。基準高 10px。 */
    public static class BatModel extends Base {
        protected final ModelPart wingL;
        protected final ModelPart wingR;
        protected final ModelPart body;

        public BatModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
            this.wingL = root.getChild("wingL");
            this.wingR = root.getChild("wingR");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = box(root, "body", BU, BV, -2, 16.5f, -3.5f, 4, 6, 7, 0, 0, 0);
            box(body, "head", BU + 12, BV, -2.5f, 13, -4.5f, 5, 4, 4, 0, 0, 0);
            box(body, "earL", BU + 22, BV, -2.5f, 11, -4, 1.5f, 2, 1, 0, 0, 0);
            box(body, "earR", BU + 25, BV, 1, 11, -4, 1.5f, 2, 1, 0, 0, 0);
            box(body, "eyeL", AU, AV, -1.8f, 14, -4.7f, 1.2f, 1.5f, 0.4f, 0, 0, 0);
            box(body, "eyeR", AU + 2, AV, 0.6f, 14, -4.7f, 1.2f, 1.5f, 0.4f, 0, 0, 0);
            box(body, "fangL", WU, WV, -1.2f, 16.5f, -4.7f, 0.7f, 1.2f, 0.4f, 0, 0, 0);
            box(body, "fangR", WU + 1, WV, 0.5f, 16.5f, -4.7f, 0.7f, 1.2f, 0.4f, 0, 0, 0);
            box(body, "belly", LU, LV, -1.5f, 18, -3.7f, 3, 3.5f, 0.4f, 0, 0, 0);
            box(root, "wingL", FU, FV, 0, -0.5f, -3.5f, 11, 1, 7, 1.5f, 16, 0);
            box(root, "wingR", FU + 12, FV, -11, -0.5f, -3.5f, 11, 1, 7, -1.5f, 16, 0);
            box(root, "clawL", DU + 8, DV, 10.5f, -0.5f, -3.5f, 1, 1, 1, 1.5f, 16, 0);
            box(root, "clawR", DU + 8, DV, -11.5f, -0.5f, -3.5f, 1, 1, 1, -1.5f, 16, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float flap = Mth.sin(state.ageInTicks * 0.9f) * 0.55f;
            wingL.zRot = 0.25f + flap;
            wingR.zRot = -0.25f - flap;
            body.y = Mth.sin(state.ageInTicks * 0.5f) * 0.8f;
        }
    }

    // ================= 人型 (原作 shape: skeleton / 頭蓋+武器) =================

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

    /** 洞窟ゴブリン。頭蓋+耳+短剣。基準高 23px。 */
    public static class GoblinModel extends Walker {
        public GoblinModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 14, -1.5f, 0, -1.5f, 3, 7, 3, -1.5f, 17, 0);
            box(root, "legR", BU + 4, BV + 14, -1.5f, 0, -1.5f, 3, 7, 3, 1.5f, 17, 0);
            box(root, "torso", BU + 8, BV + 8, -3.5f, 10, -2, 7, 7, 4, 0, 0, 0);
            box(root, "belt", FU + 40, FV, -3.5f, 15, -2, 7, 2, 4, 0, 0, 0);
            box(root, "armL", BU + 20, BV + 8, -1.5f, 0, -1.5f, 3, 7, 3, -5, 10, 0);
            PartDefinition armR = box(root, "armR", BU + 24, BV + 8, -1.5f, 0, -1.5f, 3, 7, 3, 5, 10, 0);
            box(armR, "dagger", AU, AV + 8, -0.5f, 5, -0.5f, 1, 5, 1, 0, 0, 0);
            box(root, "head", BU + 28, BV, -3.5f, 4, -3.5f, 7, 6, 7, 0, 0, 0);
            box(root, "jaw", LU, LV + 8, -3, 8.5f, -3.2f, 6, 1.5f, 6.4f, 0, 0, 0);
            box(root, "eyeL", DU, DV, -2.8f, 6, -3.7f, 1.8f, 2.2f, 0.4f, 0, 0, 0);
            box(root, "eyeR", DU + 3, DV, 1, 6, -3.7f, 1.8f, 2.2f, 0.4f, 0, 0, 0);
            boxRot(root, "earL", BU + 36, BV, -4, 0, -1, 4, 1.5f, 2, -3.5f, 6, 0, 0, 0, 0.25f);
            boxRot(root, "earR", BU + 36, BV + 3, 0, 0, -1, 4, 1.5f, 2, 3.5f, 6, 0, 0, 0, -0.25f);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    /** 骸骨兵。細身+肋骨+剣。基準高 23px。 */
    public static class SkeletonModel extends Walker {
        public SkeletonModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 16, -1, 0, -1, 2, 9, 2, -1.5f, 15, 0);
            box(root, "legR", BU + 4, BV + 16, -1, 0, -1, 2, 9, 2, 1.5f, 15, 0);
            box(root, "torso", BU + 8, BV + 10, -3, 7, -1.5f, 6, 8, 3, 0, 0, 0);
            box(root, "rib1", WU, WV + 6, -3.2f, 9, -1.7f, 6.4f, 1.2f, 3.4f, 0, 0, 0);
            box(root, "rib2", WU, WV + 9, -3.2f, 12, -1.7f, 6.4f, 1.2f, 3.4f, 0, 0, 0);
            box(root, "armL", BU + 20, BV + 10, -1, 0, -1, 2, 9, 2, -4, 7, 0);
            PartDefinition armR = box(root, "armR", BU + 24, BV + 10, -1, 0, -1, 2, 9, 2, 4, 7, 0);
            box(armR, "sword", AU, AV + 4, -0.5f, 6, -0.5f, 1, 9, 1, 0, 0, 0);
            box(armR, "guard", AU + 2, AV + 4, -1.5f, 5.5f, -0.5f, 3, 1, 1, 0, 0, 0);
            box(root, "head", BU + 28, BV, -3, 1, -3, 6, 6, 6, 0, 0, 0);
            box(root, "crack", FU + 44, FV + 2, -1, 2, -3.2f, 2, 3, 0.4f, 0, 0, 0);
            box(root, "eyeL", DU, DV, -2.2f, 3, -3.2f, 1.6f, 2, 0.4f, 0, 0, 0);
            box(root, "eyeR", DU + 3, DV, 0.6f, 3, -3.2f, 1.6f, 2, 0.4f, 0, 0, 0);
            box(root, "teeth", WU + 4, WV, -2, 6, -3.2f, 4, 1, 0.4f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    // ================= インプ (原作 shape: imp / 円胴+角) =================

    /** 火焔インプ・深淵教徒。円胴+角+尻尾。跳ねアニメ。基準高 20px。 */
    public static class ImpModel extends Base {
        private final ModelPart body;
        private final ModelPart tail;

        public ImpModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
            this.tail = this.body.getChild("tail");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = box(root, "body", BU, BV, -3.5f, 11, -3.5f, 7, 9, 7, 0, 0, 0);
            box(body, "belly", LU, LV, -2.5f, 14, -3.7f, 5, 5, 0.4f, 0, 0, 0);
            boxRot(body, "hornL", AU, AV, -3, -3, -1, 1.5f, 3.5f, 1.5f, 0, 0, 0, 0, 0, 0.4f);
            boxRot(body, "hornR", AU + 4, AV, 1.5f, -3, -1, 1.5f, 3.5f, 1.5f, 0, 0, 0, 0, 0, -0.4f);
            box(body, "eyeL", AU + 8, AV, -2.4f, 13, -3.7f, 1.8f, 2.2f, 0.4f, 0, 0, 0);
            box(body, "eyeR", AU + 11, AV, 0.6f, 13, -3.7f, 1.8f, 2.2f, 0.4f, 0, 0, 0);
            box(body, "fangL", WU, WV, -1.8f, 17, -3.7f, 0.8f, 1.2f, 0.4f, 0, 0, 0);
            box(body, "fangR", WU + 1, WV, 1, 17, -3.7f, 0.8f, 1.2f, 0.4f, 0, 0, 0);
            box(body, "armL", BU + 16, BV + 8, -5, 3, -1, 1.5f, 5, 1.5f, 0, 0, 0);
            box(body, "armR", BU + 19, BV + 8, 3.5f, 3, -1, 1.5f, 5, 1.5f, 0, 0, 0);
            box(body, "legL", BU + 22, BV + 12, -2.5f, 7, -1, 2, 3, 2, 0, 0, 0);
            box(body, "legR", BU + 25, BV + 12, 0.5f, 7, -1, 2, 3, 2, 0, 0, 0);
            PartDefinition tail = box(body, "tail", BU + 28, BV + 12, -0.75f, 5, 2, 1.5f, 5, 1.5f, 0, 0, 0);
            box(tail, "spade", AU + 14, AV, -1.5f, 9, -0.75f, 3, 3, 3, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            body.y = -Math.abs(Mth.sin(state.ageInTicks * 0.4f)) * 1.6f;
            body.xRot = Mth.sin(state.ageInTicks * 0.4f) * 0.06f;
            tail.yRot = Mth.sin(state.ageInTicks * 0.55f) * 0.4f;
        }
    }

    // ================= ゴーレム系 (原作 shape: golem / 岩塊+目) =================

    /** 岩塊ゴーレム。岩積み+眉+腕振り。基準高 33px。 */
    public static class GolemModel extends Base {
        private final ModelPart torso;
        private final ModelPart armL;
        private final ModelPart armR;

        public GolemModel(ModelPart root) {
            super(root);
            this.torso = root.getChild("torso");
            this.armL = root.getChild("armL");
            this.armR = root.getChild("armR");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 16, -10, 10, -4, 8, 14, 8, 0, 0, 0);
            box(root, "legR", BU + 10, BV + 16, 2, 10, -4, 8, 14, 8, 0, 0, 0);
            PartDefinition torso = boxRot(root, "torso", BU + 20, BV, -8, -2, -5, 16, 12, 10, 0, 0, 0, 0, 0, 0.05f);
            box(torso, "plateL", FU, FV, -8.5f, 0, -4, 1, 8, 8, 0, 0, 0);
            box(torso, "plateR", FU + 10, FV, 7.5f, 0, -4, 1, 8, 8, 0, 0, 0);
            box(torso, "core", AU, AV, -2, 3, -5.3f, 4, 4, 0.5f, 0, 0, 0);
            box(torso, "crack", FU + 20, FV + 4, 2, -1, 2, 4, 5, 0.5f, 0, 0, 0);
            box(root, "armL", BU + 38, BV, -6, 0, -3, 6, 14, 6, -11, 0, 0);
            box(root, "armR", BU + 38, BV + 15, -6, 0, -3, 6, 14, 6, 11, 0, 0);
            box(root, "fistL", FU + 30, FV, -7, 12, -4, 8, 4, 8, -11, 0, 0);
            box(root, "fistR", FU + 30, FV + 12, -7, 12, -4, 8, 4, 8, 11, 0, 0);
            boxRot(root, "head", BU, BV, -4, -9, -4, 8, 7, 8, 0, 0, 0, -0.08f, 0, 0);
            box(root, "brow", BU + 10, BV, -4.5f, -7.5f, -4.5f, 9, 2.5f, 1, 0, 0, 0);
            box(root, "eyeL", AU + 6, AV, -3, -5.5f, -4.6f, 2, 1.5f, 0.4f, 0, 0, 0);
            box(root, "eyeR", AU + 9, AV, 1, -5.5f, -4.6f, 2, 1.5f, 0.4f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float swing = Mth.cos(state.walkAnimationPos * 0.5f) * 0.9f * state.walkAnimationSpeed;
            armL.xRot = swing;
            armR.xRot = -swing;
            torso.zRot = 0.05f + Mth.cos(state.walkAnimationPos * 0.5f) * 0.03f * state.walkAnimationSpeed;
        }
    }

    /** 猪突オーク。 bulky +鼻+牙+肩当て。基準高 24px。 */
    public static class ChargerModel extends Base {
        private final ModelPart legL;
        private final ModelPart legR;
        private final ModelPart armL;
        private final ModelPart armR;

        public ChargerModel(ModelPart root) {
            super(root);
            this.legL = root.getChild("legL");
            this.legR = root.getChild("legR");
            this.armL = root.getChild("armL");
            this.armR = root.getChild("armR");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 16, -5.5f, 0, -2.5f, 5, 9, 5, -3, 15, 0);
            box(root, "legR", BU + 6, BV + 16, 0.5f, 0, -2.5f, 5, 9, 5, 3, 15, 0);
            box(root, "torso", BU + 12, BV + 6, -5, 6, -3.5f, 10, 9, 7, 0, 0, 0);
            box(root, "belly", LU, LV, -4, 9, -3.7f, 8, 5, 0.4f, 0, 0, 0);
            box(root, "armL", BU + 24, BV + 6, -4, 0, -2, 4, 9, 4, -7, 6, 0);
            box(root, "armR", BU + 30, BV + 6, 0, 0, -2, 4, 9, 4, 7, 6, 0);
            box(root, "padL", FU, FV, -9.5f, 4, -2.5f, 5, 2.5f, 5, 0, 0, 0);
            box(root, "padR", FU + 6, FV, 4.5f, 4, -2.5f, 5, 2.5f, 5, 0, 0, 0);
            box(root, "head", BU + 36, BV, -3.5f, 0, -3.5f, 7, 6, 7, 0, 0, 0);
            box(root, "snout", BU + 36, BV + 8, -2.5f, 3, -5, 5, 3, 2, 0, 0, 0);
            box(root, "tuskL", WU, WV, -2.5f, 4, -5.2f, 1, 2, 0.6f, 0, 0, 0);
            box(root, "tuskR", WU + 2, WV, 1.5f, 4, -5.2f, 1, 2, 0.6f, 0, 0, 0);
            box(root, "eyeL", DU, DV, -2.5f, 1.5f, -3.7f, 1.6f, 1.6f, 0.4f, 0, 0, 0);
            box(root, "eyeR", DU + 3, DV, 0.9f, 1.5f, -3.7f, 1.6f, 1.6f, 0.4f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.2f * state.walkAnimationSpeed;
            legL.xRot = swing;
            legR.xRot = -swing;
            armL.xRot = -swing * 0.7f;
            armR.xRot = swing * 0.7f;
        }
    }

    /** ゴブリン王グルザ。巨体+王冠+マント。基準高 30px。 */
    public static class KingModel extends Base {
        private final ModelPart legL;
        private final ModelPart legR;
        private final ModelPart armL;
        private final ModelPart armR;
        private final ModelPart cape;

        public KingModel(ModelPart root) {
            super(root);
            this.legL = root.getChild("legL");
            this.legR = root.getChild("legR");
            this.armL = root.getChild("armL");
            this.armR = root.getChild("armR");
            this.cape = root.getChild("cape");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 18, -7, 0, -3, 6, 10, 6, -3.5f, 14, 0);
            box(root, "legR", BU + 8, BV + 18, 1, 0, -3, 6, 10, 6, 3.5f, 14, 0);
            box(root, "torso", BU + 16, BV + 4, -6, 4, -4, 12, 10, 8, 0, 0, 0);
            box(root, "belly", LU, LV, -5, 7, -4.2f, 10, 6, 0.4f, 0, 0, 0);
            box(root, "armL", BU + 30, BV + 4, -5, 0, -2.5f, 5, 11, 5, -8.5f, 4, 0);
            box(root, "armR", BU + 38, BV + 4, 0, 0, -2.5f, 5, 11, 5, 8.5f, 4, 0);
            box(root, "padL", FU, FV, -9.5f, 2, -3, 6, 3, 6, -8.5f, 4, 0);
            box(root, "padR", FU + 8, FV, 3.5f, 2, -3, 6, 3, 6, 8.5f, 4, 0);
            box(root, "head", BU, BV, -4, -3, -4, 8, 7, 8, 0, 0, 0);
            box(root, "snout", BU + 10, BV, -3, 0, -6, 6, 3.5f, 2, 0, 0, 0);
            box(root, "tuskL", WU, WV, -3, 1.5f, -6.2f, 1.2f, 2.5f, 0.6f, 0, 0, 0);
            box(root, "tuskR", WU + 2, WV, 1.8f, 1.5f, -6.2f, 1.2f, 2.5f, 0.6f, 0, 0, 0);
            box(root, "eyeL", DU, DV, -3, -1.5f, -4.2f, 2, 2, 0.4f, 0, 0, 0);
            box(root, "eyeR", DU + 3, DV, 1, -1.5f, -4.2f, 2, 2, 0.4f, 0, 0, 0);
            box(root, "crown", FU + 16, FV, -4.5f, -6.5f, -4.5f, 9, 3.5f, 9, 0, 0, 0);
            box(root, "gem", AU, AV, -1.5f, -5.5f, -4.7f, 3, 2, 0.5f, 0, 0, 0);
            box(root, "cape", FU + 30, FV, -6, 4, 3.5f, 12, 16, 1.5f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float swing = Mth.cos(state.walkAnimationPos * 0.6f) * 1.1f * state.walkAnimationSpeed;
            legL.xRot = swing;
            legR.xRot = -swing;
            armL.xRot = -swing * 0.7f;
            armR.xRot = swing * 0.7f;
            cape.xRot = 0.12f + Mth.sin(state.ageInTicks * 0.3f) * 0.04f;
        }
    }

    // ================= レイス (原作 shape: wraith / 外套+目・半透明) =================

    /** 怨念レイス・幻影ファントム。外套+中空目。半透明。基準高 20px。 */
    public static class WraithModel extends Base {
        private final ModelPart cloak;
        private final ModelPart tail;

        public WraithModel(ModelPart root) {
            super(root, true);
            this.cloak = root.getChild("cloak");
            this.tail = this.cloak.getChild("tail");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition cloak = box(root, "cloak", BU, BV, -3.5f, 12, -2.5f, 7, 9, 5, 0, 0, 0);
            box(cloak, "head", BU + 14, BV, -3, 6, -3, 6, 6, 6, 0, 0, 0);
            box(cloak, "hood", BU + 22, BV, -3.5f, 5, -3.5f, 7, 3, 7, 0, 0, 0);
            box(cloak, "eyeL", DU, DV, -2.2f, 8, -3.2f, 1.8f, 2.4f, 0.5f, 0, 0, 0);
            box(cloak, "eyeR", DU + 3, DV, 0.4f, 8, -3.2f, 1.8f, 2.4f, 0.5f, 0, 0, 0);
            box(cloak, "maw", DU + 6, DV + 4, -1, 11, -3.2f, 2, 1.2f, 0.5f, 0, 0, 0);
            boxRot(cloak, "armL", BU + 30, BV, -5.5f, 2, -1, 2, 7, 2, 0, 0, 0, 0, 0, 0.3f);
            boxRot(cloak, "armR", BU + 34, BV, 3.5f, 2, -1, 2, 7, 2, 0, 0, 0, 0, 0, -0.3f);
            box(cloak, "clawL", WU, WV, -6, 8, -1, 1.5f, 2, 1.5f, 0, 0, 0);
            box(cloak, "clawR", WU + 2, WV, 4.5f, 8, -1, 1.5f, 2, 1.5f, 0, 0, 0);
            box(cloak, "tail", BU + 38, BV + 6, -2.5f, 9, -1.5f, 5, 6, 3, 0, 0, 0);
            box(cloak, "wisp1", AU, AV, -4.5f, 16, -1, 1.5f, 5, 1.5f, 0, 0, 0);
            box(cloak, "wisp2", AU + 3, AV, 3, 17, -1, 1.5f, 4, 1.5f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            cloak.y = Mth.sin(state.ageInTicks * 0.35f) * 1.2f;
            cloak.xRot = Mth.sin(state.ageInTicks * 0.22f) * 0.06f;
            tail.xRot = Mth.sin(state.ageInTicks * 0.5f) * 0.12f;
        }
    }

    // ================= 竜 (原作 shape: dragon / 翼+吻+尾) =================

    /** 屍竜ヴァイト。四足+翼+吻+尾3節+背棘。基準高 24px。 */
    public static class DragonModel extends Base {
        private final ModelPart wingL;
        private final ModelPart wingR;
        private final ModelPart tail1;
        private final ModelPart tail2;
        private final ModelPart jaw;

        public DragonModel(ModelPart root) {
            super(root);
            this.wingL = root.getChild("wingL");
            this.wingR = root.getChild("wingR");
            this.tail1 = root.getChild("tail1");
            this.tail2 = root.getChild("tail2");
            this.jaw = root.getChild("jaw");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "body", BU, BV, -4, 12, -8, 8, 8, 16, 0, 0, 0);
            box(root, "belly", LU, LV, -3, 16, -7.5f, 6, 3, 14, 0, 0, 0);
            box(root, "neck", BU + 10, BV, -2.5f, 9, -13, 5, 5, 6, 0, 0, 0);
            box(root, "head", BU + 18, BV, -3, 5.5f, -18, 6, 5, 5, 0, 0, 0);
            box(root, "snout", BU + 26, BV, -2, 7, -21, 4, 2.5f, 3, 0, 0, 0);
            box(root, "jaw", BU + 26, BV + 4, -2, 9.5f, -21, 4, 1.2f, 3, 0, 0, 0);
            box(root, "hornL", WU, WV, -3.5f, 3, -15, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "hornR", WU + 2, WV, 2, 3, -15, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "eyeL", AU, AV, -3.2f, 6.5f, -17.5f, 1.4f, 1.6f, 1.6f, 0, 0, 0);
            box(root, "eyeR", AU + 4, AV, 1.8f, 6.5f, -17.5f, 1.4f, 1.6f, 1.6f, 0, 0, 0);
            box(root, "spike1", FU, FV, -1, 10.5f, -6, 2, 2, 2, 0, 0, 0);
            box(root, "spike2", FU + 3, FV, -1, 10.5f, 0, 2, 2, 2, 0, 0, 0);
            box(root, "spike3", FU + 6, FV, -1, 10.5f, 6, 2, 2, 2, 0, 0, 0);
            box(root, "tail1", BU + 32, BV + 8, -2, 13, 8, 4, 4, 6, 0, 0, 0);
            box(root, "tail2", BU + 38, BV + 8, -1.5f, 14, 14, 3, 3, 6, 0, 0, 0);
            box(root, "fin", FU + 10, FV, -0.5f, 11, 18, 1, 6, 4, 0, 0, 0);
            box(root, "legFL", BU, BV + 20, -6, 18, -6, 3, 6, 3, 0, 0, 0);
            box(root, "legFR", BU + 5, BV + 20, 3, 18, -6, 3, 6, 3, 0, 0, 0);
            box(root, "legBL", BU, BV + 24, -6, 18, 3, 3, 6, 3, 0, 0, 0);
            box(root, "legBR", BU + 5, BV + 24, 3, 18, 3, 3, 6, 3, 0, 0, 0);
            box(root, "wingL", FU + 16, FV, 0, -1, -4.5f, 15, 1, 9, 3, 11, -2);
            box(root, "wingR", FU + 32, FV, -15, -1, -4.5f, 15, 1, 9, -3, 11, -2);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float flap = Mth.sin(state.ageInTicks * 0.55f) * 0.5f;
            wingL.zRot = 0.2f + flap;
            wingR.zRot = -0.2f - flap;
            tail1.yRot = Mth.sin(state.ageInTicks * 0.4f) * 0.18f;
            tail2.yRot = Mth.sin(state.ageInTicks * 0.4f + 1.0f) * 0.22f;
            jaw.xRot = 0.15f + (Mth.sin(state.ageInTicks * 0.2f) * 0.5f + 0.5f) * 0.35f;
        }
    }

    /** 竜人。人型+翼+角+尾+鉤爪。基準高 24px。 */
    public static class DragonkinModel extends Base {
        private final ModelPart wingL;
        private final ModelPart wingR;
        private final ModelPart tail;
        private final ModelPart legL;
        private final ModelPart legR;

        public DragonkinModel(ModelPart root) {
            super(root);
            this.wingL = root.getChild("wingL");
            this.wingR = root.getChild("wingR");
            this.tail = root.getChild("tail");
            this.legL = root.getChild("legL");
            this.legR = root.getChild("legR");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 16, -3.5f, 0, -1.5f, 3, 9, 3, -2, 15, 0);
            box(root, "legR", BU + 4, BV + 16, 0.5f, 0, -1.5f, 3, 9, 3, 2, 15, 0);
            box(root, "clawFL", WU, WV, -3.5f, 7.5f, -3.5f, 3, 1.5f, 2, -2, 15, 0);
            box(root, "clawFR", WU + 4, WV, 0.5f, 7.5f, -3.5f, 3, 1.5f, 2, 2, 15, 0);
            box(root, "torso", BU + 8, BV + 8, -3.5f, 7, -2, 7, 8, 4, 0, 0, 0);
            box(root, "belly", LU, LV, -2.5f, 9, -2.2f, 5, 5, 0.4f, 0, 0, 0);
            box(root, "armL", BU + 18, BV + 8, -6.5f, 7, -1.5f, 3, 8, 3, 0, 0, 0);
            box(root, "armR", BU + 23, BV + 8, 3.5f, 7, -1.5f, 3, 8, 3, 0, 0, 0);
            box(root, "clawL", WU, WV + 3, -6.5f, 13, -1.5f, 3, 2, 3, 0, 0, 0);
            box(root, "clawR", WU + 4, WV + 3, 3.5f, 13, -1.5f, 3, 2, 3, 0, 0, 0);
            box(root, "head", BU + 28, BV, -3, 1, -3, 6, 6, 6, 0, 0, 0);
            box(root, "snout", BU + 28, BV + 8, -2, 3, -5, 4, 2.5f, 2, 0, 0, 0);
            box(root, "hornL", AU, AV, -3.5f, -2, -2, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "hornR", AU + 3, AV, 2, -2, -2, 1.5f, 3, 1.5f, 0, 0, 0);
            box(root, "eyeL", AU + 6, AV, -2.2f, 2.5f, -3.2f, 1.4f, 1.8f, 0.4f, 0, 0, 0);
            box(root, "eyeR", AU + 9, AV, 0.8f, 2.5f, -3.2f, 1.4f, 1.8f, 0.4f, 0, 0, 0);
            box(root, "wingL", FU, FV, 0, -1, -2.5f, 10, 1, 5, 3, 6, 1);
            box(root, "wingR", FU + 12, FV, -10, -1, -2.5f, 10, 1, 5, -3, 6, 1);
            box(root, "tail", BU + 36, BV + 12, -1, 20, 1, 2, 2, 8, 0, 0, 0);
            box(root, "tailTip", FU + 24, FV, -1.5f, 19.5f, 8, 3, 3, 3, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float flap = Mth.sin(state.ageInTicks * 0.6f) * 0.45f;
            wingL.zRot = 0.35f + flap;
            wingR.zRot = -0.35f - flap;
            float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.2f * state.walkAnimationSpeed;
            legL.xRot = swing;
            legR.xRot = -swing;
            tail.yRot = Mth.sin(state.ageInTicks * 0.45f) * 0.25f;
        }
    }

    // ================= 騎士 (原作 shape: knight / 暗円+大剣) =================

    /** 深淵騎士アビス。暗鎧+大剣+目。基準高 32px。 */
    public static class KnightModel extends Base {
        private final ModelPart legL;
        private final ModelPart legR;
        private final ModelPart armR;
        private final ModelPart blade;

        public KnightModel(ModelPart root) {
            super(root);
            this.legL = root.getChild("legL");
            this.legR = root.getChild("legR");
            this.armR = root.getChild("armR");
            this.blade = this.armR.getChild("blade");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 20, -4, 0, -2, 4, 9, 4, -2, 15, 0);
            box(root, "legR", BU + 6, BV + 20, 0, 0, -2, 4, 9, 4, 2, 15, 0);
            box(root, "torso", BU + 12, BV + 8, -4.5f, 6, -2.5f, 9, 9, 5, 0, 0, 0);
            box(root, "trim", AU, AV, -4.7f, 7, -2.7f, 9.4f, 1.5f, 5.4f, 0, 0, 0);
            box(root, "core", AU + 12, AV, -1.5f, 10, -2.7f, 3, 3, 0.4f, 0, 0, 0);
            box(root, "paulL", BU + 24, BV + 8, -9, 5, -3, 5, 3, 6, 0, 0, 0);
            box(root, "paulR", BU + 24, BV + 14, 4, 5, -3, 5, 3, 6, 0, 0, 0);
            box(root, "armL", BU + 32, BV + 8, -8, 0, -2, 4, 9, 4, -6.5f, 6, 0);
            PartDefinition armR = box(root, "armR", BU + 38, BV + 8, 0, 0, -2, 4, 9, 4, 6.5f, 6, 0);
            box(armR, "blade", BU + 40, BV, -0.75f, 6, -0.75f, 1.5f, 17, 1.5f, 0, 0, 0);
            box(armR, "edge", BU + 44, BV, -0.75f, 6, -1.1f, 1.5f, 17, 0.4f, 0, 0, 0);
            box(armR, "guard", AU + 20, AV, -2.5f, 5, -1, 5, 1.5f, 2, 0, 0, 0);
            box(root, "helm", BU, BV, -4, -2, -4, 8, 8, 8, 0, 0, 0);
            box(root, "crest", FU, FV, -0.75f, -6, -2, 1.5f, 4, 5, 0, 0, 0);
            box(root, "slit", DU, DV, -3, 1, -4.2f, 6, 1.5f, 0.4f, 0, 0, 0);
            box(root, "eyeL", AU + 24, AV, -2.5f, 1, -4.3f, 1.6f, 1.5f, 0.3f, 0, 0, 0);
            box(root, "eyeR", AU + 27, AV, 0.9f, 1, -4.3f, 1.6f, 1.5f, 0.3f, 0, 0, 0);
            box(root, "cape", FU + 8, FV, -4.5f, 6, 2.5f, 9, 15, 1.5f, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.0f * state.walkAnimationSpeed;
            legL.xRot = swing;
            legR.xRot = -swing;
            armR.xRot = swing * 0.5f + Mth.sin(state.ageInTicks * 0.3f) * 0.05f;
            blade.zRot = Mth.sin(state.ageInTicks * 0.23f) * 0.04f;
        }
    }

    // ================= 邪眼 (原作 shape: eye / 眼球+瞳孔+棘) =================

    /** 浮遊邪眼・万象の邪眼。眼球+白目+瞳孔+回転棘輪。基準高 14px。 */
    public static class EyeModel extends Base {
        private final ModelPart ball;
        private final ModelPart ring;

        public EyeModel(ModelPart root) {
            super(root);
            this.ball = root.getChild("ball");
            this.ring = root.getChild("ring");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition ball = box(root, "ball", BU, BV, -6, 12, -6, 12, 12, 12, 0, 0, 0);
            box(ball, "veinT", FU + 30, FV, -5, 12.5f, -1, 10, 1, 2, 0, 0, 0);
            box(ball, "veinB", FU + 30, FV + 4, -5, 22.5f, -1, 10, 1, 2, 0, 0, 0);
            box(ball, "sclera", WU, WV, -3.5f, 14.5f, -6.4f, 7, 7, 1, 0, 0, 0);
            box(ball, "pupil", DU, DV, -1.75f, 16.25f, -6.7f, 3.5f, 3.5f, 0.6f, 0, 0, 0);
            box(ball, "glint", WU + 8, WV, -2.5f, 15, -6.9f, 1.5f, 1.5f, 0.4f, 0, 0, 0);
            PartDefinition ring = root.addOrReplaceChild("ring", CubeListBuilder.create(),
                    PartPose.offset(0, 18, 0));
            box(ring, "spikeT", AU, AV, -1, -11, -1, 2, 5, 2, 0, 0, 0);
            box(ring, "spikeB", AU + 3, AV, -1, 6, -1, 2, 5, 2, 0, 0, 0);
            box(ring, "spikeL", AU + 6, AV, -11, -1, -1, 5, 2, 2, 0, 0, 0);
            box(ring, "spikeR", AU + 12, AV, 6, -1, -1, 5, 2, 2, 0, 0, 0);
            box(ring, "spikeF", AU + 18, AV, -1, -1, -11, 2, 2, 5, 0, 0, 0);
            box(ring, "spikeK", AU + 21, AV, -1, -1, 6, 2, 2, 5, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            ball.y = Mth.sin(state.ageInTicks * 0.4f) * 0.9f;
            ring.yRot = state.ageInTicks * 0.05f;
            ring.xRot = Mth.sin(state.ageInTicks * 0.13f) * 0.2f;
        }
    }

    // ================= 護衛兵 (原作 shape: guard / 盾体+槍) =================

    /** 鉄壁の護衛兵。楯状胴+盾+槍+背棘。基準高 26px。 */
    public static class GuardModel extends Base {
        private final ModelPart legL;
        private final ModelPart legR;

        public GuardModel(ModelPart root) {
            super(root);
            this.legL = root.getChild("legL");
            this.legR = root.getChild("legR");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "legL", BU, BV + 20, -4, 0, -2, 4, 9, 4, -2, 15, 0);
            box(root, "legR", BU + 6, BV + 20, 0, 0, -2, 4, 9, 4, 2, 15, 0);
            box(root, "greaveL", FU, FV, -4.2f, 5, -2.2f, 4.4f, 4, 4.4f, -2, 15, 0);
            box(root, "greaveR", FU + 6, FV, -0.2f, 5, -2.2f, 4.4f, 4, 4.4f, 2, 15, 0);
            boxRot(root, "torso", BU + 12, BV + 6, -4.5f, 5, -2.5f, 9, 10, 5, 0, 0, 0, 0.06f, 0, 0);
            box(root, "chest", LU, LV, -3.5f, 7, -2.7f, 7, 5, 0.4f, 0, 0, 0);
            box(root, "helm", BU + 24, BV + 6, -3.5f, -2, -3.5f, 7, 7, 7, 0, 0, 0);
            box(root, "visor", DU, DV, -2.5f, 1, -3.7f, 5, 1.5f, 0.4f, 0, 0, 0);
            box(root, "crest", AU, AV, -0.75f, -5.5f, -2.5f, 1.5f, 3.5f, 5, 0, 0, 0);
            box(root, "spike", WU, WV, -1, 2, 2.5f, 2, 4, 2, 0, 0, 0);
            PartDefinition armL = box(root, "armL", BU + 32, BV + 6, -8, 0, -2, 4, 9, 4, -6.5f, 6, 0);
            box(armL, "shield", FU + 14, FV, -2.5f, -1, -5, 2, 12, 9, 0, 0, 0);
            box(armL, "boss", AU + 6, AV, -3, 3, -1.5f, 1, 4, 4, 0, 0, 0);
            PartDefinition armR = box(root, "armR", BU + 38, BV + 6, 0, 0, -2, 4, 9, 4, 6.5f, 6, 0);
            box(armR, "spear", BU + 44, BV, -0.5f, -12, -0.5f, 1, 22, 1, 0, 0, 0);
            box(armR, "tip", AU + 12, AV, -1, -16, -1, 2, 4, 2, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float swing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.1f * state.walkAnimationSpeed;
            legL.xRot = swing;
            legR.xRot = -swing;
        }
    }

    // ================= 蘇生師 (原作 shape: necromancer / 三角外套+杖) =================

    /** 骸骨の蘇生師。三角外套+髑髏+杖+召喚輪。基準高 26px。 */
    public static class NecromancerModel extends Base {
        private final ModelPart staff;
        private final ModelPart ring;

        public NecromancerModel(ModelPart root) {
            super(root);
            this.staff = root.getChild("staff");
            this.ring = root.getChild("ring");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "robe", BU, BV + 10, -4.5f, 13, -3.5f, 9, 11, 7, 0, 0, 0);
            box(root, "mid", BU + 18, BV + 8, -3.5f, 7, -3, 7, 6, 6, 0, 0, 0);
            box(root, "trim", FU, FV, -4.7f, 18, -3.7f, 9.4f, 1.5f, 7.4f, 0, 0, 0);
            box(root, "rib1", WU, WV + 6, -3, 14, -3.7f, 6, 1, 0.4f, 0, 0, 0);
            box(root, "rib2", WU, WV + 8, -3, 17, -3.7f, 6, 1, 0.4f, 0, 0, 0);
            box(root, "skull", LU, LV, -2.5f, 2.5f, -2.5f, 5, 5, 5, 0, 0, 0);
            box(root, "hood", BU + 28, BV + 4, -3.5f, 0, -3.5f, 7, 4, 7, 0, 0, 0);
            box(root, "eyeL", DU, DV, -1.8f, 4, -2.7f, 1.4f, 1.8f, 0.4f, 0, 0, 0);
            box(root, "eyeR", DU + 3, DV, 0.4f, 4, -2.7f, 1.4f, 1.8f, 0.4f, 0, 0, 0);
            box(root, "sleeveL", BU + 36, BV + 8, -7, 9, -1.5f, 3, 7, 3, 0, 0, 0);
            box(root, "sleeveR", BU + 40, BV + 8, 4, 9, -1.5f, 3, 7, 3, 0, 0, 0);
            PartDefinition staff = box(root, "staff", BU + 44, BV, 5.5f, -4, -0.5f, 1, 20, 1, 0, 0, 0);
            box(staff, "orb", AU, AV, 3.5f, -8, -2.5f, 5, 5, 5, 0, 0, 0);
            box(staff, "clawT", AU + 6, AV, 4.5f, -10, -1.5f, 3, 2, 3, 0, 0, 0);
            PartDefinition ring = root.addOrReplaceChild("ring", CubeListBuilder.create(),
                    PartPose.offset(0, 23, 0));
            box(ring, "circle", FU + 20, FV, -7, 0, -7, 14, 1, 14, 0, 0, 0);
            box(ring, "rune1", FU + 12, FV + 4, -5, 0, -5.5f, 3, 1, 1, 0, 0, 0);
            box(ring, "rune2", FU + 17, FV + 4, 2, 0, 4.5f, 3, 1, 1, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            staff.y = Mth.sin(state.ageInTicks * 0.4f) * 0.8f;
            ring.yRot = state.ageInTicks * 0.03f;
        }
    }

    // ================= 召喚門 (原作 shape: gate / 五角アーチ+気配) =================

    /** 呪われた召喚門。柱+楣+内幕+髑髏+火。基準高 28px。 */
    public static class GateModel extends Base {
        private final ModelPart veil;
        private final ModelPart flameL;
        private final ModelPart flameR;

        public GateModel(ModelPart root) {
            super(root);
            this.veil = root.getChild("veil");
            this.flameL = root.getChild("flameL");
            this.flameR = root.getChild("flameR");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            box(root, "pillarL", BU, BV, -9.5f, 4, -2.5f, 5, 20, 5, 0, 0, 0);
            box(root, "pillarR", BU + 8, BV, 4.5f, 4, -2.5f, 5, 20, 5, 0, 0, 0);
            box(root, "runeL", FU + 30, FV, -9.7f, 8, -2.7f, 0.4f, 10, 5.4f, 0, 0, 0);
            box(root, "runeR", FU + 36, FV, 9.3f, 8, -2.7f, 0.4f, 10, 5.4f, 0, 0, 0);
            box(root, "lintel", BU + 16, BV, -9.5f, -1, -2.5f, 19, 5, 5, 0, 0, 0);
            box(root, "skullT", LU, LV, -2, -4.5f, -2, 4, 3.5f, 4, 0, 0, 0);
            box(root, "jawT", LU, LV + 6, -1.5f, -1.5f, -2.2f, 3, 1.2f, 4.4f, 0, 0, 0);
            box(root, "frame", BU + 38, BV + 8, -5, 7, -0.5f, 10, 14, 1, 0, 0, 0);
            box(root, "veil", AU, AV, -4.5f, 7.5f, -0.4f, 9, 13, 0.6f, 0, 0, 0);
            box(root, "flameL", AU + 12, AV, -8.5f, 0, -1.5f, 3, 4, 3, 0, 0, 0);
            box(root, "flameR", AU + 17, AV, 5.5f, 0, -1.5f, 3, 4, 3, 0, 0, 0);
            box(root, "bowlL", DU, DV + 8, -9, 3.5f, -2, 4, 1, 4, 0, 0, 0);
            box(root, "bowlR", DU, DV + 10, 5, 3.5f, -2, 4, 1, 4, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float pulse = 1.0f + Mth.sin(state.ageInTicks * 0.45f) * 0.03f;
            veil.xScale = pulse;
            veil.zScale = pulse;
            float fl = 1.0f + Mth.sin(state.ageInTicks * 1.3f) * 0.12f;
            flameL.yScale = fl;
            flameR.yScale = 2.0f - fl;
        }
    }

    // ================= 神 (原作 shape: god / 回転環+核) =================

    /** 虚無神ニヒル・ゼロ・オメガ・超越者。法衣+光輪+翼+逆回転二環。基準高 30px。 */
    public static class GodModel extends Base {
        private final ModelPart body;
        private final ModelPart wingL;
        private final ModelPart wingR;
        private final ModelPart ring1;
        private final ModelPart ring2;

        public GodModel(ModelPart root) {
            super(root);
            this.body = root.getChild("body");
            this.wingL = this.body.getChild("wingL");
            this.wingR = this.body.getChild("wingR");
            this.ring1 = root.getChild("ring1");
            this.ring2 = root.getChild("ring2");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = box(root, "body", BU, BV, -4.5f, 9, -3.5f, 9, 15, 7, 0, 0, 0);
            box(body, "chest", LU, LV, -3.5f, 11, -3.7f, 7, 8, 0.4f, 0, 0, 0);
            box(body, "core", AU, AV, -1.5f, 14, -3.9f, 3, 4, 0.5f, 0, 0, 0);
            box(body, "shoulders", BU + 12, BV, -5, 5, -3.5f, 10, 4, 7, 0, 0, 0);
            box(body, "head", BU + 24, BV, -3.5f, -2, -3.5f, 7, 7, 7, 0, 0, 0);
            box(body, "visage", DU, DV, -2.5f, 0, -3.7f, 5, 3, 0.4f, 0, 0, 0);
            box(body, "halo", WU, WV, -5.5f, -6.5f, -5.5f, 11, 1, 11, 0, 0, 0);
            box(body, "haloIn", AU + 6, AV, -3.5f, -6.5f, -3.5f, 7, 1, 7, 0, 0, 0);
            box(body, "armL", BU + 32, BV, -9, 4, -2, 4, 10, 4, 0, 0, 0);
            box(body, "armR", BU + 38, BV, 5, 4, -2, 4, 10, 4, 0, 0, 0);
            box(body, "clawL", WU, WV + 3, -9, 12, -2, 4, 2, 4, 0, 0, 0);
            box(body, "clawR", WU + 5, WV + 3, 5, 12, -2, 4, 2, 4, 0, 0, 0);
            box(body, "wingL", FU, FV, 0, -1, -3, 12, 1, 6, 4, 2, 2);
            box(body, "wingR", FU + 14, FV, -12, -1, -3, 12, 1, 6, -4, 2, 2);
            box(body, "hem", FU + 28, FV, -5, 20, -4, 10, 4, 8, 0, 0, 0);
            PartDefinition ring1 = root.addOrReplaceChild("ring1", CubeListBuilder.create(),
                    PartPose.offsetAndRotation(0, 15, 0, 0.35f, 0, 0.15f));
            box(ring1, "arc1a", AU + 12, AV + 6, -8, -0.75f, -1.5f, 16, 1.5f, 3, 0, 0, 0);
            box(ring1, "arc1b", AU + 12, AV + 10, -1.5f, -0.75f, -8, 3, 1.5f, 16, 0, 0, 0);
            PartDefinition ring2 = root.addOrReplaceChild("ring2", CubeListBuilder.create(),
                    PartPose.offsetAndRotation(0, 15, 0, -0.3f, 0, -0.2f));
            box(ring2, "arc2a", AU + 12, AV + 14, -7, -0.5f, -1.25f, 14, 1, 2.5f, 0, 0, 0);
            box(ring2, "arc2b", AU + 12, AV + 18, -1.25f, -0.5f, -7, 2.5f, 1, 14, 0, 0, 0);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            body.y = Mth.sin(state.ageInTicks * 0.3f) * 1.0f;
            float flap = Mth.sin(state.ageInTicks * 0.35f) * 0.3f;
            wingL.zRot = 0.3f + flap;
            wingR.zRot = -0.3f - flap;
            ring1.yRot = state.ageInTicks * 0.04f;
            ring2.yRot = -state.ageInTicks * 0.055f;
        }
    }
}
