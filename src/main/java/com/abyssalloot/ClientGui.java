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
            event.registerLayerDefinition(AbyssModels.PHANTOM, AbyssModels.PhantomModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.CULTIST, AbyssModels.CultistModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.EYE, AbyssModels.EyeModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.CHARGER, AbyssModels.ChargerModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.DRAGONKIN, AbyssModels.DragonkinModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GUARD, AbyssModels.GuardModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.NECROMANCER, AbyssModels.NecromancerModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GATE, AbyssModels.GateModel::createBodyLayer);
            event.registerLayerDefinition(AbyssModels.GOD, AbyssModels.GodModel::createBodyLayer);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            bind(event, ModEntities.SLIME, AbyssModels.SLIME, AbyssModels.SlimeModel::new, "slime", 0.4f);
            bind(event, ModEntities.SPLITTER, AbyssModels.SPLITTER, AbyssModels.SplitterModel::new, "splitter", 0.9f);
            bind(event, ModEntities.BAT, AbyssModels.BAT, AbyssModels.BatModel::new, "bat", 0.3f);
            bind(event, ModEntities.MIRRORBAT, AbyssModels.BAT, AbyssModels.BatModel::new, "mirrorbat", 0.3f);
            bind(event, ModEntities.GOBLIN, AbyssModels.GOBLIN, AbyssModels.GoblinModel::new, "goblin", 0.5f);
            bind(event, ModEntities.SKELETON, AbyssModels.SKELETON, AbyssModels.SkeletonModel::new, "skeleton", 0.5f);
            bind(event, ModEntities.IMP, AbyssModels.IMP, AbyssModels.ImpModel::new, "imp", 0.5f);
            bind(event, ModEntities.BOMBER, AbyssModels.BOMBER, AbyssModels.BomberModel::new, "bomber", 0.5f);
            bind(event, ModEntities.WRAITH, AbyssModels.WRAITH, AbyssModels.WraithModel::new, "wraith", 0.4f);
            bind(event, ModEntities.GOLEM, AbyssModels.GOLEM, AbyssModels.GolemModel::new, "golem", 0.9f);
            bind(event, ModEntities.PHANTOM, AbyssModels.PHANTOM, AbyssModels.PhantomModel::new, "phantom", 0.5f);
            bind(event, ModEntities.CULTIST, AbyssModels.CULTIST, AbyssModels.CultistModel::new, "cultist", 0.5f);
            bind(event, ModEntities.EYE, AbyssModels.EYE, AbyssModels.EyeModel::new, "eye", 0.4f);
            bind(event, ModEntities.BOSS_EYE, AbyssModels.EYE, AbyssModels.EyeModel::new, "boss_eye", 0.6f);
            bind(event, ModEntities.CHARGER, AbyssModels.CHARGER, AbyssModels.ChargerModel::new, "charger", 0.6f);
            bind(event, ModEntities.GOBLIN_KING, AbyssModels.CHARGER, AbyssModels.ChargerModel::new, "goblin_king", 0.7f);
            bind(event, ModEntities.DRAGONKIN, AbyssModels.DRAGONKIN, AbyssModels.DragonkinModel::new, "dragonkin", 0.6f);
            bind(event, ModEntities.WYRM, AbyssModels.DRAGONKIN, AbyssModels.DragonkinModel::new, "wyrm", 0.8f);
            bind(event, ModEntities.GUARD, AbyssModels.GUARD, AbyssModels.GuardModel::new, "guard", 0.5f);
            bind(event, ModEntities.KNIGHT, AbyssModels.GUARD, AbyssModels.GuardModel::new, "knight", 0.6f);
            bind(event, ModEntities.NECROMANCER, AbyssModels.NECROMANCER, AbyssModels.NecromancerModel::new, "necromancer", 0.5f);
            bind(event, ModEntities.GATE, AbyssModels.GATE, AbyssModels.GateModel::new, "gate", 0.8f);
            bind(event, ModEntities.NIHIL, AbyssModels.GOD, AbyssModels.GodModel::new, "nihil", 0.7f);
            bind(event, ModEntities.ZERO, AbyssModels.GOD, AbyssModels.GodModel::new, "zero", 0.7f);
            bind(event, ModEntities.OMEGA, AbyssModels.GOD, AbyssModels.GodModel::new, "omega", 0.9f);
            bind(event, ModEntities.ASCENDER, AbyssModels.GOD, AbyssModels.GodModel::new, "ascender", 0.9f);
        });
    }

    private static <T extends Mob> void bind(EntityRenderersEvent.RegisterRenderers event,
                                             Supplier<EntityType<T>> type,
                                             net.minecraft.client.model.geom.ModelLayerLocation layer,
                                             Function<ModelPart, EntityModel<LivingEntityRenderState>> factory,
                                             String tex, float shadow) {
        event.registerEntityRenderer(type.get(),
                ctx -> new AbyssRenderer<>(ctx, factory.apply(ctx.bakeLayer(layer)), shadow,
                        Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "textures/entity/" + tex + ".png")));
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
}
