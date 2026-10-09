package com.rileycleavenger.gnvcraft.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** A Lime-green scooter: deck, two wheels, stem and handlebar. y = 24 is the ground, front is -Z. */
public class ScooterModel extends EntityModel<LivingEntityRenderState> {
	public ScooterModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("deck", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, 0.0F, -6.0F, 6.0F, 1.5F, 14.0F), PartPose.offset(0.0F, 18.0F, 0.0F));
		root.addOrReplaceChild("rear_wheel", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, -2.5F, -2.5F, 2.0F, 5.0F, 5.0F), PartPose.offset(0.0F, 21.5F, 8.5F));
		root.addOrReplaceChild("front_wheel", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, -2.5F, -2.5F, 2.0F, 5.0F, 5.0F), PartPose.offset(0.0F, 21.5F, -8.5F));
		root.addOrReplaceChild("stem", CubeListBuilder.create().texOffs(0, 26).addBox(-0.75F, -17.0F, -0.75F, 1.5F, 17.0F, 1.5F), PartPose.offset(0.0F, 19.0F, -6.5F));
		root.addOrReplaceChild("handlebar", CubeListBuilder.create().texOffs(8, 26).addBox(-5.0F, -1.0F, -0.75F, 10.0F, 1.5F, 1.5F), PartPose.offset(0.0F, 2.0F, -6.5F));
		return LayerDefinition.create(mesh, 32, 32);
	}
}
