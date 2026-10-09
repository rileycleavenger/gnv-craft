package com.rileycleavenger.heysir.client;

import com.rileycleavenger.heysir.HeySirMod;
import com.rileycleavenger.heysir.client.render.BikeModel;
import com.rileycleavenger.heysir.client.render.HeySirModel;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class HeySirModelLayers {
	public static final ModelLayerLocation HEYSIR = new ModelLayerLocation(HeySirMod.id("heysir"), "main");
	public static final ModelLayerLocation BIKE = new ModelLayerLocation(HeySirMod.id("heysir_bike"), "main");

	private HeySirModelLayers() {
	}

	public static void initialize() {
		ModelLayerRegistry.registerModelLayer(HEYSIR, HeySirModel::createLayer);
		ModelLayerRegistry.registerModelLayer(BIKE, BikeModel::createLayer);
	}
}
