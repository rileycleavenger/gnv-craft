package com.rileycleavenger.gnvcraft.client;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.client.render.BikeModel;
import com.rileycleavenger.gnvcraft.client.render.DennisModel;
import com.rileycleavenger.gnvcraft.client.render.DennisRenderer;
import com.rileycleavenger.gnvcraft.client.render.FanModel;
import com.rileycleavenger.gnvcraft.client.render.FanRenderer;
import com.rileycleavenger.gnvcraft.client.render.HeySirModel;
import com.rileycleavenger.gnvcraft.client.render.PlaneModel;
import com.rileycleavenger.gnvcraft.client.render.PlaneRenderer;
import com.rileycleavenger.gnvcraft.client.render.ScooterModel;
import com.rileycleavenger.gnvcraft.client.render.ScooterRenderer;
import com.rileycleavenger.gnvcraft.client.render.HeySirRenderer;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = GnvCraftMod.MOD_ID, dist = Dist.CLIENT)
public class HeySirClient {
	public HeySirClient(IEventBus modBus) {
		DrunkEffects.register(modBus);
		modBus.addListener(HeySirClient::onRegisterLayers);
		modBus.addListener(HeySirClient::onRegisterRenderers);
	}

	private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(HeySirModelLayers.HEYSIR, HeySirModel::createLayer);
		event.registerLayerDefinition(HeySirModelLayers.BIKE, BikeModel::createLayer);
		event.registerLayerDefinition(HeySirModelLayers.DENNIS, DennisModel::createLayer);
		event.registerLayerDefinition(HeySirModelLayers.FAN, FanModel::createLayer);
		event.registerLayerDefinition(HeySirModelLayers.SCOOTER, ScooterModel::createLayer);
		event.registerLayerDefinition(HeySirModelLayers.PLANE, PlaneModel::createLayer);
	}

	private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntities.HEYSIR.get(), HeySirRenderer::new);
		event.registerEntityRenderer(ModEntities.DENNIS.get(), DennisRenderer::new);
		event.registerEntityRenderer(ModEntities.FAN.get(), FanRenderer::new);
		event.registerEntityRenderer(ModEntities.LIME_SCOOTER.get(), ScooterRenderer::new);
		event.registerEntityRenderer(ModEntities.PLANE.get(), PlaneRenderer::new);
	}
}
