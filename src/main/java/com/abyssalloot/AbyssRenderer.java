package com.abyssalloot;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * 深淵MOBの汎用レンダラ (クライアント専用)。モデル・テクスチャ・拡大率を受け取るだけ。
 */
public class AbyssRenderer<T extends Mob> extends MobRenderer<T, LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {
    private final Identifier texture;
    private final float modelScale;

    public AbyssRenderer(EntityRendererProvider.Context ctx, EntityModel<LivingEntityRenderState> model,
                         float shadow, Identifier texture, float modelScale) {
        super(ctx, model, shadow);
        this.texture = texture;
        this.modelScale = modelScale;
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return texture;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack pose) {
        super.scale(state, pose);
        pose.scale(modelScale, modelScale, modelScale);
    }
}
