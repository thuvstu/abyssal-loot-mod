package com.abyssalloot;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

/**
 * 深淵防具の着用 3D モデル (GeckoLib)。セット (equipment asset) ごとに 1 つのモデルを全部位で共有し、
 * GeckoLib が部位ごとの骨 (armorHead・armorBody …) だけを描く。
 * 資産: geckolib/models/armor/&lt;set&gt;.geo.json / geckolib/animations/armor/&lt;set&gt;.animation.json /
 * textures/armor/&lt;set&gt;.png (+ _glowmask.png は自動発光)。生成は tools/art/geo_armor.py。
 */
public class AbyssArmorRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends GeoArmorRenderer<ModItems.AbyssArmorItem, R> {
    public AbyssArmorRenderer(String asset) {
        super(new DefaultedGeoModel<>(Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, asset)) {
            @Override
            protected String subtype() {
                return "armor";
            }
        });
        withRenderLayer(AutoGlowingGeoLayer::new);
    }
}
