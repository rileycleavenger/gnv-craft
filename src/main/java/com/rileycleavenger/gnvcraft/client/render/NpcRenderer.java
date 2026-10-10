package com.rileycleavenger.gnvcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.HeySirModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PathfinderMob;

/** Renders a special NPC with its own skin (assets/gnvcraft/textures/entity/<name>.png) at a given scale. */
public class NpcRenderer<T extends PathfinderMob> extends HumanoidMobRenderer<T, HumanoidRenderState, NpcModel> {
	private final Identifier texture;
	private final float size;

	public NpcRenderer(EntityRendererProvider.Context context, String skin, float size) {
		super(context, new NpcModel(context.bakeLayer(HeySirModelLayers.NPC)), 0.5F * size);
		this.texture = GnvCraftMod.id("textures/entity/" + skin + ".png");
		this.size = size;
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	protected void scale(HumanoidRenderState state, PoseStack poseStack) {
		poseStack.scale(this.size, this.size, this.size);
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return this.texture;
	}
}
