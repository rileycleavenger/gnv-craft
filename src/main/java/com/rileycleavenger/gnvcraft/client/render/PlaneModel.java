package com.rileycleavenger.gnvcraft.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** Small white high-wing prop plane. Nose is -Z. Pivot is the cabin centre (y = 24 ground). */
public class PlaneModel extends EntityModel<LivingEntityRenderState> {
	private final ModelPart body;
	private final ModelPart prop;

	public PlaneModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.prop = this.body.getChild("prop");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, -14.0F, 12.0F, 12.0F, 28.0F), PartPose.offset(0.0F, 14.0F, 0.0F));
		body.addOrReplaceChild("wing", CubeListBuilder.create().texOffs(0, 40).addBox(-30.0F, -7.5F, -6.0F, 60.0F, 1.5F, 12.0F), PartPose.ZERO);
		body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 54).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 12.0F), PartPose.offset(0.0F, -2.0F, 14.0F));
		body.addOrReplaceChild("stabilizer", CubeListBuilder.create().texOffs(34, 54).addBox(-10.0F, -0.75F, 0.0F, 20.0F, 1.5F, 6.0F), PartPose.offset(0.0F, -2.0F, 22.0F));
		body.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(34, 62).addBox(-0.75F, -9.0F, 0.0F, 1.5F, 9.0F, 6.0F), PartPose.offset(0.0F, -2.0F, 22.0F));
		body.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 70).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 4.0F), PartPose.offset(0.0F, 0.0F, -14.0F));
		body.addOrReplaceChild("prop", CubeListBuilder.create().texOffs(30, 70).addBox(-1.0F, -9.0F, -0.5F, 2.0F, 18.0F, 1.0F), PartPose.offset(0.0F, 0.0F, -19.0F));
		body.addOrReplaceChild("gear_left", CubeListBuilder.create().texOffs(0, 82).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), PartPose.offset(-7.0F, 6.0F, -4.0F));
		body.addOrReplaceChild("gear_right", CubeListBuilder.create().texOffs(0, 82).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), PartPose.offset(7.0F, 6.0F, -4.0F));
		return LayerDefinition.create(mesh, 128, 96);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		this.body.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.prop.zRot = state.ageInTicks * 1.8F;
	}
}
