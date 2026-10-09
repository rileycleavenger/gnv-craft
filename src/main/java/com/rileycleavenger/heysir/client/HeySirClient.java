package com.rileycleavenger.heysir.client;

import com.rileycleavenger.heysir.HeySirMod;
import com.rileycleavenger.heysir.client.render.BikeModel;
import com.rileycleavenger.heysir.client.render.HeySirModel;
import com.rileycleavenger.heysir.client.render.HeySirRenderer;
import com.rileycleavenger.heysir.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = HeySirMod.MOD_ID, dist = Dist.CLIENT)
public class HeySirClient {
	public HeySirClient(IEventBus modBus) {
		modBus.addListener(HeySirClient::onRegisterLayers);
		modBus.addListener(HeySirClient::onRegisterRenderers);
	}

	private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(HeySirModelLayers.HEYSIR, HeySirModel::createLayer);
		event.registerLayerDefinition(HeySirModelLayers.BIKE, BikeModel::createLayer);
	}

	private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntities.HEYSIR.get(), HeySirRenderer::new);
	}
}
