package com.rileycleavenger.gnvcraft.client.render;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.HeySirModelLayers;
import com.rileycleavenger.gnvcraft.entity.FanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

public class FanRenderer extends HumanoidMobRenderer<FanEntity, FanRenderState, FanModel> {
	private static final Identifier[] TEXTURES = new Identifier[6];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = GnvCraftMod.id("textures/entity/fan_" + (i + 1) + ".png");
		}
	}

	public FanRenderer(EntityRendererProvider.Context context) {
		super(context, new FanModel(context.bakeLayer(HeySirModelLayers.FAN)), 0.5F);
	}

	@Override
	public FanRenderState createRenderState() {
		return new FanRenderState();
	}

	@Override
	public void extractRenderState(FanEntity entity, FanRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.variant = entity.variant();
	}

	@Override
	public Identifier getTextureLocation(FanRenderState state) {
		return TEXTURES[state.variant];
	}
}
