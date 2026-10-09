package com.rileycleavenger.gnvcraft.client;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.render.BikeModel;
import com.rileycleavenger.gnvcraft.client.render.HeySirModel;
import com.rileycleavenger.gnvcraft.client.render.HeySirRenderer;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = GnvCraftMod.MOD_ID, dist = Dist.CLIENT)
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
