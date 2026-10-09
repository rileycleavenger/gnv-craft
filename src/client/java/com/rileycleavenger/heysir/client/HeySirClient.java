package com.rileycleavenger.heysir.client;

import com.rileycleavenger.heysir.client.render.HeySirRenderer;
import com.rileycleavenger.heysir.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class HeySirClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HeySirModelLayers.initialize();
		EntityRenderers.register(ModEntities.HEYSIR, HeySirRenderer::new);
	}
}
