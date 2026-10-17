package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * クライアント専用: GUI 画面の登録と、サーバーから届いた表示データの保持
 * (サーバー側からは参照しないこと)。
 */
public final class ClientGui {
    private ClientGui() {}

    private static List<String> compareLeft = List.of("Aに武器を置いて「比較する」");
    private static List<String> compareRight = List.of("Bに武器を置いて「比較する」");
    private static List<String> statusLines = List.of();
    /** 祝福の提示中の3択 (BoonSelectScreen の再描画用) */
    private static List<String> boonChoices = List.of();
    private static int boonRemaining;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(ModMenus.FORGE.get(), ForgeScreen::new);
            event.register(ModMenus.SHOP.get(), ShopScreen::new);
            event.register(ModMenus.COMPARE.get(), CompareScreen::new);
        });
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> {
            event.registerLayerDefinition(AbyssModels.SLIME, AbyssModels.SlimeModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.SPLITTER, AbyssModels.SplitterModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.BAT, AbyssModels.BatModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GOBLIN, AbyssModels.GoblinModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.SKELETON, AbyssModels.SkeletonModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.IMP, AbyssModels.ImpModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.BOMBER, AbyssModels.BomberModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.WRAITH, AbyssModels.WraithModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GOLEM, AbyssModels.GolemModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.PHANTOM, AbyssModels.WraithModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.CULTIST, AbyssModels.ImpModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.EYE, AbyssModels.EyeModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.CHARGER, AbyssModels.ChargerModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.DRAGONKIN, AbyssModels.DragonkinModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GUARD, AbyssModels.GuardModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.NECROMANCER, AbyssModels.NecromancerModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GATE, AbyssModels.GateModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.KING, AbyssModels.KingModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.DRAGON, AbyssModels.DragonModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.KNIGHT, AbyssModels.KnightModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GOD, AbyssModels.GodModel::createBodyLayer);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            bind(event, ModEntities.SLIME, AbyssModels.SLIME, AbyssModels.SlimeModel::new, "slime", 0.3f, 0.96f);
            bind(event, ModEntities.SPLITTER, AbyssModels.SPLITTER, AbyssModels.SplitterModel::new, "splitter", 0.7f, 2.24f);
            bind(event, ModEntities.BAT, AbyssModels.BAT, AbyssModels.BatModel::new, "bat", 0.3f, 0.96f);
            bind(event, ModEntities.MIRRORBAT, AbyssModels.BAT, AbyssModels.BatModel::new, "mirrorbat", 0.3f, 0.96f);
            bind(event, ModEntities.GOBLIN, AbyssModels.GOBLIN, AbyssModels.GoblinModel::new, "goblin", 0.4f, 0.9f);
            bind(event, ModEntities.SKELETON, AbyssModels.SKELETON, AbyssModels.SkeletonModel::new, "skeleton", 0.4f, 1.11f);
            bind(event, ModEntities.IMP, AbyssModels.IMP, AbyssModels.ImpModel::new, "imp", 0.35f, 0.88f);
            bind(event, ModEntities.BOMBER, AbyssModels.BOMBER, AbyssModels.BomberModel::new, "bomber", 0.4f, 1.6f);
            bind(event, ModEntities.WRAITH, AbyssModels.WRAITH, AbyssModels.WraithModel::new, "wraith", 0.4f, 1.04f);
            bind(event, ModEntities.GOLEM, AbyssModels.GOLEM, AbyssModels.GolemModel::new, "golem", 0.9f, 1.07f);
            bind(event, ModEntities.PHANTOM, AbyssModels.PHANTOM, AbyssModels.WraithModel::new, "phantom", 0.5f, 1.76f);
            bind(event, ModEntities.CULTIST, AbyssModels.CULTIST, AbyssModels.ImpModel::new, "cultist", 0.4f, 1.36f);
            bind(event, ModEntities.EYE, AbyssModels.EYE, AbyssModels.EyeModel::new, "eye", 0.4f, 1.14f);
            bind(event, ModEntities.BOSS_EYE, AbyssModels.EYE, AbyssModels.EyeModel::new, "boss_eye", 1.0f, 2.51f);
            bind(event, ModEntities.CHARGER, AbyssModels.CHARGER, AbyssModels.ChargerModel::new, "charger", 0.6f, 1.07f);
            bind(event, ModEntities.GOBLIN_KING, AbyssModels.KING, AbyssModels.KingModel::new, "goblin_king", 0.9f, 1.16f);
            bind(event, ModEntities.DRAGONKIN, AbyssModels.DRAGONKIN, AbyssModels.DragonkinModel::new, "dragonkin", 0.6f, 1.2f);
            bind(event, ModEntities.WYRM, AbyssModels.DRAGON, AbyssModels.DragonModel::new, "wyrm", 1.0f, 1.2f);
            bind(event, ModEntities.GUARD, AbyssModels.GUARD, AbyssModels.GuardModel::new, "guard", 0.5f, 1.23f);
            bind(event, ModEntities.KNIGHT, AbyssModels.KNIGHT, AbyssModels.KnightModel::new, "knight", 0.8f, 1.2f);
            bind(event, ModEntities.NECROMANCER, AbyssModels.NECROMANCER, AbyssModels.NecromancerModel::new, "necromancer", 0.45f, 1.05f);
            bind(event, ModEntities.GATE, AbyssModels.GATE, AbyssModels.GateModel::new, "gate", 0.8f, 1.37f);
            bind(event, ModEntities.NIHIL, AbyssModels.GOD, AbyssModels.GodModel::new, "nihil", 0.9f, 1.47f);
            bind(event, ModEntities.ZERO, AbyssModels.GOD, AbyssModels.GodModel::new, "zero", 1.0f, 1.68f);
            bind(event, ModEntities.OMEGA, AbyssModels.GOD, AbyssModels.GodModel::new, "omega", 1.1f, 1.9f);
            bind(event, ModEntities.ASCENDER, AbyssModels.GOD, AbyssModels.GodModel::new, "ascender", 1.2f, 2.1f);
        });
    }

    private static <T extends Mob> void bind(EntityRenderersEvent.RegisterRenderers event,
                                             Supplier<EntityType<T>> type,
                                             net.minecraft.client.model.geom.ModelLayerLocation layer,
                                             Function<ModelPart, EntityModel<LivingEntityRenderState>> factory,
                                             String tex, float shadow, float modelScale) {
        event.registerEntityRenderer(type.get(),
                ctx -> new AbyssRenderer<>(ctx, factory.apply(ctx.bakeLayer(layer)), shadow,
                        Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "textures/entity/" + tex + ".png"),
                        modelScale));
    }

    public static List<String> compareLeft() {
        return compareLeft;
    }

    public static List<String> compareRight() {
        return compareRight;
    }

    public static void showCompare(List<String> left, List<String> right) {
        compareLeft = List.copyOf(left);
        compareRight = List.copyOf(right);
    }

    public static void showStatus(List<String> lines) {
        statusLines = List.copyOf(lines);
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        // 開き済みなら内容だけ更新 (属性ボタン連打に対応)
        if (!(mc.screen instanceof StatusScreen)) {
            mc.setScreen(new StatusScreen());
        }
    }

    public static List<String> statusLines() {
        return statusLines;
    }

    public static void openHub() {
        if (Minecraft.getInstance() != null) {
            Minecraft.getInstance().setScreen(new HubScreen());
        }
    }

    /**
     * 祝福の3択を受け取り、選択画面を開く。既に選択画面が開いている場合は内容だけ差し替える
     * (連続レベルアップで次の提示に平滑に移る)。他のGUIが開いていれば上書きする。
     */
    public static void showBoonChoices(List<String> choices, int remaining) {
        boonChoices = choices == null ? List.of() : List.copyOf(choices);
        boonRemaining = remaining;
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.screen instanceof BoonSelectScreen bs) {
            bs.refresh(boonChoices, boonRemaining);
        } else {
            mc.setScreen(new BoonSelectScreen(boonChoices, boonRemaining));
        }
    }
}
