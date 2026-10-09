package com.rileycleavenger.gnvcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

/** Player-skin humanoid that never stops dancing: arm pumps, hip sway and a bouncing step. */
public class DennisModel extends HumanoidModel<DennisRenderState> {
	public DennisModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createLayer() {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
	}

	@Override
	public void setupAnim(DennisRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks * 0.45F;
		float beat = Mth.sin(t);
		float bounce = Math.abs(Mth.sin(t)) * 1.5F;
		this.rightArm.xRot = -2.6F + beat * 0.5F;
		this.leftArm.xRot = -2.6F - beat * 0.5F;
		this.rightArm.zRot = 0.4F + Mth.cos(t * 2.0F) * 0.25F;
		this.leftArm.zRot = -0.4F - Mth.cos(t * 2.0F) * 0.25F;
		this.rightLeg.xRot = Mth.sin(t) * 0.7F;
		this.leftLeg.xRot = -Mth.sin(t) * 0.7F;
		this.rightLeg.zRot = 0.1F;
		this.leftLeg.zRot = -0.1F;
		this.body.zRot = Mth.sin(t) * 0.12F;
		this.head.zRot = -Mth.sin(t) * 0.08F;
		this.head.y -= bounce;
		this.body.y -= bounce;
		this.rightArm.y -= bounce;
		this.leftArm.y -= bounce;
	}
}
