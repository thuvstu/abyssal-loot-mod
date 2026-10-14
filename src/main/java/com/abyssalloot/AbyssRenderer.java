package com.abyssalloot;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * 深淵MOBの汎用レンダラ (クライアント専用)。モデルとテクスチャを受け取るだけ。
 */
public class AbyssRenderer<T extends Mob> extends MobRenderer<T, LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {
    private final Identifier texture;

    public AbyssRenderer(EntityRendererProvider.Context ctx, EntityModel<LivingEntityRenderState> model,
                         float shadow, Identifier texture) {
        super(ctx, model, shadow);
        this.texture = texture;
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return texture;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }
}
