package com.rileycleavenger.gnvcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rileycleavenger.gnvcraft.GnvCraftMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Draws the bike in HeySir's own pose space, so it turns, falls over and flashes red with him.
 * Under him while riding; at his right side (model -x) while he walks it.
 */
public class BikeLayer extends RenderLayer<HeySirRenderState, HeySirModel> {
	private static final Identifier TEXTURE = GnvCraftMod.id("textures/entity/heysir_bike.png");
	private static final float WALKING_OFFSET_X = -10.0F / 16.0F;
	private static final float WALKING_OFFSET_Z = 3.5F / 16.0F;

	private final BikeModel model;

	public BikeLayer(RenderLayerParent<HeySirRenderState, HeySirModel> renderer, BikeModel model) {
		super(renderer);
		this.model = model;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, HeySirRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		if (!state.riding) {
			poseStack.translate(WALKING_OFFSET_X, 0.0F, WALKING_OFFSET_Z);
		}
		submitNodeCollector.submitModel(
			this.model,
			state,
			poseStack,
			RenderTypes.entityCutout(TEXTURE),
			lightCoords,
			LivingEntityRenderer.getOverlayCoords(state, 0.0F),
			-1,
			null,
			state.outlineColor,
			null
		);
		poseStack.popPose();
	}
}
