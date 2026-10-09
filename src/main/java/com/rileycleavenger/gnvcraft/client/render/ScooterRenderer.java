package com.rileycleavenger.gnvcraft.client.render;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.HeySirModelLayers;
import com.rileycleavenger.gnvcraft.entity.LimeScooterEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ScooterRenderer extends MobRenderer<LimeScooterEntity, LivingEntityRenderState, ScooterModel> {
	private static final Identifier TEXTURE = GnvCraftMod.id("textures/entity/lime_scooter.png");

	public ScooterRenderer(EntityRendererProvider.Context context) {
		super(context, new ScooterModel(context.bakeLayer(HeySirModelLayers.SCOOTER)), 0.35F);
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
