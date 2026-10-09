package com.rileycleavenger.heysir;

import com.rileycleavenger.heysir.command.HeySirCommand;
import com.rileycleavenger.heysir.director.HeySirDirector;
import com.rileycleavenger.heysir.registry.ModAttachments;
import com.rileycleavenger.heysir.registry.ModEntities;
import com.rileycleavenger.heysir.registry.ModItems;
import com.rileycleavenger.heysir.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeySirMod implements ModInitializer {
	public static final String MOD_ID = "heysir";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.initialize();
		ModEntities.initialize();
		ModItems.initialize();
		ModAttachments.initialize();
		HeySirDirector.initialize();
		HeySirCommand.initialize();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
