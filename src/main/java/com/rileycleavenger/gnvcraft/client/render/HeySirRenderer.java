package com.rileycleavenger.gnvcraft.client.render;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.HeySirModelLayers;
import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class HeySirRenderer extends HumanoidMobRenderer<HeySirEntity, HeySirRenderState, HeySirModel> {
	private static final Identifier TEXTURE = GnvCraftMod.id("textures/entity/heysir.png");

	public HeySirRenderer(EntityRendererProvider.Context context) {
		super(context, new HeySirModel(context.bakeLayer(HeySirModelLayers.HEYSIR)), 0.6F);
		this.addLayer(new BikeLayer(this, new BikeModel(context.bakeLayer(HeySirModelLayers.BIKE))));
	}

	@Override
	public HeySirRenderState createRenderState() {
		return new HeySirRenderState();
	}

	@Override
	public void extractRenderState(HeySirEntity entity, HeySirRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.riding = entity.isRiding();
		state.wheelSpin = Mth.lerp(partialTicks, entity.prevWheelSpin, entity.wheelSpin);
		state.crankAngle = Mth.lerp(partialTicks, entity.prevCrankAngle, entity.crankAngle);
	}

	@Override
	public Identifier getTextureLocation(HeySirRenderState state) {
		return TEXTURE;
	}
}
