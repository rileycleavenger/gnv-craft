package com.rileycleavenger.gnvcraft.client.render;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.HeySirModelLayers;
import com.rileycleavenger.gnvcraft.entity.PlaneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class PlaneRenderer extends MobRenderer<PlaneEntity, LivingEntityRenderState, PlaneModel> {
	private static final Identifier TEXTURE = GnvCraftMod.id("textures/entity/plane.png");

	public PlaneRenderer(EntityRendererProvider.Context context) {
		super(context, new PlaneModel(context.bakeLayer(HeySirModelLayers.PLANE)), 1.2F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
