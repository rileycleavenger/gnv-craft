package com.rileycleavenger.gnvcraft.client.render;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.HeySirModelLayers;
import com.rileycleavenger.gnvcraft.entity.DennisEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

public class DennisRenderer extends HumanoidMobRenderer<DennisEntity, DennisRenderState, DennisModel> {
	private static final Identifier TEXTURE = GnvCraftMod.id("textures/entity/dennis.png");

	public DennisRenderer(EntityRendererProvider.Context context) {
		super(context, new DennisModel(context.bakeLayer(HeySirModelLayers.DENNIS)), 0.5F);
	}

	@Override
	public DennisRenderState createRenderState() {
		return new DennisRenderState();
	}

	@Override
	public Identifier getTextureLocation(DennisRenderState state) {
		return TEXTURE;
	}
}
