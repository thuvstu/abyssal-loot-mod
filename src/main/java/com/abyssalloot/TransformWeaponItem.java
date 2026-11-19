package com.abyssalloot;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 変形する武器 (追加武器 6 種)。手持ちは GeckoLib の 3D モデルで待機アニメをループし、
 * 奥義を撃った瞬間に {@link #playTransform} でサーバーから「変形」アニメを再生させる。
 * 資産: geckolib/models/item/&lt;id&gt;.geo.json / geckolib/animations/item/&lt;id&gt;.animation.json /
 * textures/item/geo/&lt;id&gt;_0.png (+ _glowmask)。生成は tools/art/geo_transform.py。
 */
public class TransformWeaponItem extends Item implements GeoItem {
    private final String id;
    private final RawAnimation idle;
    private final RawAnimation transform;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TransformWeaponItem(Properties props, String id) {
        super(props);
        this.id = id;
        this.idle = RawAnimation.begin().thenLoop("animation." + id + ".idle");
        this.transform = RawAnimation.begin().thenPlay("animation." + id + ".transform");
        GeoItem.registerSyncedAnimatable(this);
    }

    /** 手に持っているこの武器の変形アニメを、周りのプレイヤーにも見えるよう再生する */
    public static void playTransform(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof TransformWeaponItem item && player.level() instanceof ServerLevel level) {
            item.triggerAnim(player, GeoItem.getOrAssignId(stack, level), "main", "transform");
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        GeoItem.getOrAssignId(stack, level);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TransformWeaponItem>("main", 2, test -> test.setAndContinue(idle))
                .triggerableAnim("transform", transform));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new GeoItemRenderer<>(new DefaultedItemGeoModel<TransformWeaponItem>(
                            Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, id))
                            .withAltTexture(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "geo/" + id + "_0")))
                            .withRenderLayer(AutoGlowingGeoLayer::new);
                }
                return renderer;
            }
        });
    }
}
