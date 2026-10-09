package com.rileycleavenger.gnvcraft.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * HeySir's red bike, seat removed. Coordinates are model pixels with the bottom bracket at z = 0;
 * y = 24 is the ground. Texture regions (64x64 units) must match tools/gen_textures.py.
 */
public class BikeModel extends EntityModel<HeySirRenderState> {
	private static final int RED_U = 0, RED_V = 0;
	private static final int SILVER_U = 16, SILVER_V = 0;
	private static final int BLACK_U = 40, BLACK_V = 0;
	private static final int WHEEL_U = 0, WHEEL_V = 24;
	private static final int CHAINRING_U = 24, CHAINRING_V = 24;

	private static final float WHEEL_RADIUS = 6.0F;
	private static final float AXLE_Y = 24.0F - WHEEL_RADIUS;
	private static final float REAR_AXLE_Z = 8.0F;
	private static final float FRONT_AXLE_Z = -13.0F;
	private static final float BB_Y = 16.5F;
	private static final float CRANK_LENGTH = 2.5F;

	private final ModelPart frontWheel;
	private final ModelPart rearWheel;
	private final ModelPart crank;

	public BikeModel(ModelPart root) {
		super(root);
		this.frontWheel = root.getChild("front_wheel");
		this.rearWheel = root.getChild("rear_wheel");
		this.crank = root.getChild("crank");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		float seatTopZ = 2.5F, seatTopY = 8.5F;
		float headTopZ = -10.0F, headTopY = 7.5F;
		float headBottomZ = -10.6F, headBottomY = 10.5F;
		float barZ = -12.0F, barY = 4.5F;

		// Frame.
		tube(root, "seat_tube", 0.0F, 0.0F, BB_Y, seatTopZ, seatTopY, 1.2F, RED_U, RED_V);
		tube(root, "top_tube", 0.0F, seatTopZ, seatTopY + 0.7F, headTopZ, headTopY + 0.7F, 1.2F, RED_U, RED_V);
		tube(root, "down_tube", 0.0F, 0.0F, BB_Y, headBottomZ, headBottomY, 1.4F, RED_U, RED_V);
		tube(root, "head_tube", 0.0F, headTopZ, headTopY, headBottomZ, headBottomY, 1.6F, RED_U, RED_V);
		for (int side = -1; side <= 1; side += 2) {
			String suffix = side < 0 ? "_right" : "_left";
			tube(root, "chain_stay" + suffix, side, 0.0F, BB_Y, REAR_AXLE_Z, AXLE_Y, 0.8F, RED_U, RED_V);
			tube(root, "seat_stay" + suffix, side, seatTopZ, seatTopY + 0.5F, REAR_AXLE_Z, AXLE_Y, 0.8F, RED_U, RED_V);
			tube(root, "fork" + suffix, side, headBottomZ, headBottomY, FRONT_AXLE_Z, AXLE_Y, 0.8F, RED_U, RED_V);
		}

		// Bare seat post sticking out of the frame where the seat used to be.
		tube(root, "seat_post", 0.0F, seatTopZ, seatTopY, seatTopZ + 0.4F, seatTopY - 2.0F, 0.9F, SILVER_U, SILVER_V);

		// Stem, handlebars and grips.
		tube(root, "stem", 0.0F, headTopZ, headTopY, barZ, barY, 1.0F, SILVER_U, SILVER_V);
		root.addOrReplaceChild(
			"handlebar",
			CubeListBuilder.create()
				.texOffs(SILVER_U, SILVER_V).addBox(-5.0F, -0.45F, -0.45F, 10.0F, 0.9F, 0.9F)
				.texOffs(BLACK_U, BLACK_V).addBox(-5.4F, -0.65F, -0.65F, 2.0F, 1.3F, 1.3F)
				.texOffs(BLACK_U, BLACK_V).addBox(3.4F, -0.65F, -0.65F, 2.0F, 1.3F, 1.3F),
			PartPose.offset(0.0F, barY, barZ)
		);

		// Chainring on the bike's right side (-x in model space) and the crank with pedals.
		root.addOrReplaceChild(
			"chainring",
			CubeListBuilder.create().texOffs(CHAINRING_U, CHAINRING_V).addBox(0.0F, -2.5F, -2.5F, 0.0F, 5.0F, 5.0F),
			PartPose.offset(-1.3F, BB_Y, 0.0F)
		);
		root.addOrReplaceChild(
			"crank",
			CubeListBuilder.create()
				.texOffs(SILVER_U, SILVER_V).addBox(1.4F, 0.0F, -0.4F, 0.8F, CRANK_LENGTH, 0.8F)
				.texOffs(SILVER_U, SILVER_V).addBox(-2.2F, -CRANK_LENGTH, -0.4F, 0.8F, CRANK_LENGTH, 0.8F)
				.texOffs(BLACK_U, BLACK_V).addBox(2.2F, CRANK_LENGTH - 0.4F, -0.9F, 2.0F, 0.8F, 1.8F)
				.texOffs(BLACK_U, BLACK_V).addBox(-4.2F, -CRANK_LENGTH - 0.4F, -0.9F, 2.0F, 0.8F, 1.8F),
			PartPose.offset(0.0F, BB_Y, 0.0F)
		);

		// Wheels are flat panels with a round, transparent-edged texture so they look circular.
		CubeListBuilder wheel = CubeListBuilder.create()
			.texOffs(WHEEL_U, WHEEL_V)
			.addBox(0.0F, -WHEEL_RADIUS, -WHEEL_RADIUS, 0.0F, WHEEL_RADIUS * 2, WHEEL_RADIUS * 2);
		root.addOrReplaceChild("front_wheel", wheel, PartPose.offset(0.0F, AXLE_Y, FRONT_AXLE_Z));
		root.addOrReplaceChild("rear_wheel", wheel, PartPose.offset(0.0F, AXLE_Y, REAR_AXLE_Z));

		return LayerDefinition.create(mesh, 64, 64);
	}

	/** Adds a square tube of the given thickness running from (z1, y1) to (z2, y2) at height x. */
	private static void tube(PartDefinition root, String name, float x, float z1, float y1, float z2, float y2, float thickness, int u, int v) {
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = (float) Math.sqrt(dy * dy + dz * dz);
		float half = thickness / 2.0F;
		root.addOrReplaceChild(
			name,
			CubeListBuilder.create().texOffs(u, v).addBox(-half, 0.0F, -half, thickness, length, thickness),
			PartPose.offsetAndRotation(x, y1, z1, (float) Math.atan2(dz, dy), 0.0F, 0.0F)
		);
	}

	@Override
	public void setupAnim(HeySirRenderState state) {
		super.setupAnim(state);
		this.frontWheel.xRot = state.wheelSpin;
		this.rearWheel.xRot = state.wheelSpin;
		this.crank.xRot = state.crankAngle;
	}
}
