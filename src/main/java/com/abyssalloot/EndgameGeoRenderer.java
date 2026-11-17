package com.abyssalloot;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

import com.abyssalloot.ModItems.EndgameWeaponItem;

/**
 * エンド武器の手持ち 3D モデル (GeckoLib, クライアント専用)。
 * <p>
 * 資産は tools/art/geo_weapons.py が生成する:
 * <ul>
 *   <li>geckolib/models/item/&lt;id&gt;.geo.json — 骨格とモデル</li>
 *   <li>geckolib/animations/item/&lt;id&gt;.animation.json — 待機アニメ</li>
 *   <li>textures/item/geo/&lt;id&gt;_&lt;v&gt;.png (+ _glowmask) — 色違いテクスチャと発光部</li>
 * </ul>
 * 骨の表示規則は geo_weapons.nocturne_hidden / aibou_hidden と一致させる。
 */
public class EndgameGeoRenderer extends GeoItemRenderer<EndgameWeaponItem> {
    static final int NOCTURNE_NOTES = 5;
    static final int AIBOU_SHARDS = 4;

    private final boolean nocturne;

    public EndgameGeoRenderer(EndgameWeaponItem item) {
        super(new Model(item.kind()));
        this.nocturne = item.kind().equals("abyss_nocturne");
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override
    public void addRenderData(EndgameWeaponItem animatable, @Nullable RenderData renderData, GeoRenderState renderState,
                              float partialTick) {
        int v = renderData == null ? 0 : EndgameWeaponItem.variantOf(renderData.itemStack());
        renderState.addGeckolibData(EndgameWeaponItem.VARIANT, v);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> renderPassInfo, BoneSnapshots snapshots) {
        int v = variant(renderPassInfo.renderState());
        if (nocturne) {
            // カウント +1 個の音符が周回する
            for (int k = 0; k < NOCTURNE_NOTES; k++) {
                boolean hide = k > v;
                snapshots.ifPresent("note" + k, s -> s.skipRender(hide));
            }
        } else {
            boolean wings = v >= 3;
            snapshots.ifPresent("wing_l", s -> s.skipRender(!wings));
            snapshots.ifPresent("wing_r", s -> s.skipRender(!wings));
            int shards = shardsOf(v);
            for (int k = 0; k < AIBOU_SHARDS; k++) {
                boolean hide = k >= shards;
                snapshots.ifPresent("shard" + k, s -> s.skipRender(hide));
            }
        }
    }

    /** 相棒の剣: 段階 4 で欠片 2 つ、段階 5 で 4 つ */
    static int shardsOf(int stage) {
        return stage < 4 ? 0 : stage == 4 ? 2 : AIBOU_SHARDS;
    }

    static int variant(GeoRenderState state) {
        Integer v = state.getGeckolibData(EndgameWeaponItem.VARIANT);
        return v == null ? 0 : v;
    }

    /** モデル・アニメは 1 つ、テクスチャだけ段階ごとに差し替える */
    static final class Model extends GeoModel<EndgameWeaponItem> {
        private final Identifier geo;
        private final String kind;
        private final int variants;
        private final String suffix;

        Model(String kind) {
            this.kind = kind;
            this.geo = Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID, "item/" + kind);
            boolean nocturne = kind.equals("abyss_nocturne");
            this.variants = nocturne ? 5 : 6;
            this.suffix = nocturne ? "_c" : "_s";
        }

        @Override
        public Identifier getModelResource(GeoRenderState renderState) {
            return geo;
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            int v = Math.max(0, Math.min(variants - 1, variant(renderState)));
            return Identifier.fromNamespaceAndPath(AbyssalLootMod.MODID,
                    "textures/item/geo/" + kind + suffix + v + ".png");
        }

        @Override
        public Identifier getAnimationResource(EndgameWeaponItem animatable) {
            return geo;
        }
    }
}
