package com.rileycleavenger.gnvcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Plain player-skin humanoid (with the hat/jacket overlay layers) for the special NPCs. */
public class NpcModel extends HumanoidModel<HumanoidRenderState> {
	public NpcModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createLayer() {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
	}
}
