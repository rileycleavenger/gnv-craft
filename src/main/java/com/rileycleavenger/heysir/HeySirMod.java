package com.rileycleavenger.heysir;

import com.mojang.logging.LogUtils;
import com.rileycleavenger.heysir.command.HeySirCommand;
import com.rileycleavenger.heysir.director.HeySirDirector;
import com.rileycleavenger.heysir.registry.ModAttachments;
import com.rileycleavenger.heysir.registry.ModEntities;
import com.rileycleavenger.heysir.registry.ModItems;
import com.rileycleavenger.heysir.registry.ModSounds;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HeySirMod.MOD_ID)
public class HeySirMod {
	public static final String MOD_ID = "heysir";
	public static final Logger LOGGER = LogUtils.getLogger();

	public HeySirMod(IEventBus modBus) {
		ModSounds.register(modBus);
		ModEntities.register(modBus);
		ModItems.register(modBus);
		ModAttachments.register(modBus);
		HeySirDirector.register();
		HeySirCommand.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
