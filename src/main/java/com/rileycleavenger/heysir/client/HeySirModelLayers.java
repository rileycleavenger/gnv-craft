package com.rileycleavenger.heysir.client;

import com.rileycleavenger.heysir.HeySirMod;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class HeySirModelLayers {
	public static final ModelLayerLocation HEYSIR = new ModelLayerLocation(HeySirMod.id("heysir"), "main");
	public static final ModelLayerLocation BIKE = new ModelLayerLocation(HeySirMod.id("heysir_bike"), "main");

	private HeySirModelLayers() {
	}
}
