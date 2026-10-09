package com.rileycleavenger.gnvcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

/**
 * HeySir himself: a standard player-skin humanoid. While riding he stands on the pedals (the seat
 * is gone) leaning toward the handlebars; otherwise he walks the bike with his right hand on it.
 */
public class HeySirModel extends HumanoidModel<HeySirRenderState> {
	/** How far he's raised to stand on the pedals, in model pixels. */
	private static final float RIDING_LIFT = 6.0F;
	private static final float RIDING_LEAN = 0.55F;
	private static final float RIDING_ARM_PITCH = -0.8F;
	private static final float WALKING_BIKE_ARM_PITCH = -1.25F;

	public HeySirModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createLayer() {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
	}

	@Override
	public void setupAnim(HeySirRenderState state) {
		super.setupAnim(state);
		if (state.riding) {
			this.poseRiding(state);
		} else {
			this.rightArm.xRot = WALKING_BIKE_ARM_PITCH + this.rightArm.xRot * 0.1F;
			this.rightArm.yRot = 0.0F;
			this.rightArm.zRot = 0.0F;
		}
	}

	private void poseRiding(HeySirRenderState state) {
		float hipY = 12.0F - RIDING_LIFT;
		float sin = Mth.sin(RIDING_LEAN);
		float cos = Mth.cos(RIDING_LEAN);

		// Lean the upper body forward around the hips.
		float neckY = hipY - 12.0F * cos;
		float neckZ = -12.0F * sin;
		this.head.y = neckY;
		this.head.z = neckZ;
		this.body.y = neckY;
		this.body.z = neckZ;
		this.body.xRot = RIDING_LEAN;

		float shoulderY = hipY - 10.0F * cos;
		float shoulderZ = -10.0F * sin;
		for (ModelPart arm : new ModelPart[] {this.rightArm, this.leftArm}) {
			arm.y = shoulderY;
			arm.z = shoulderZ;
			arm.xRot = RIDING_ARM_PITCH;
			arm.yRot = 0.0F;
		}
		this.rightArm.zRot = 0.05F;
		this.leftArm.zRot = -0.05F;

		// Feet follow the pedals: BikeModel's crank is 2.5 px long.
		this.poseLeg(this.rightLeg, hipY, state.crankAngle, 0.06F);
		this.poseLeg(this.leftLeg, hipY, state.crankAngle + Mth.PI, -0.06F);
	}

	private void poseLeg(ModelPart leg, float hipY, float crank, float splay) {
		leg.y = hipY - 2.0F + 2.5F * Mth.cos(crank);
		leg.z = 0.0F;
		leg.xRot = (float) Math.asin(2.5F * Mth.sin(crank) / 12.0F);
		leg.yRot = 0.0F;
		leg.zRot = splay;
	}
}
