package com.rileycleavenger.gnvcraft;

import com.mojang.logging.LogUtils;
import com.rileycleavenger.gnvcraft.command.HeySirCommand;
import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import com.rileycleavenger.gnvcraft.registry.ModItems;
import com.rileycleavenger.gnvcraft.registry.ModSounds;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(GnvCraftMod.MOD_ID)
public class GnvCraftMod {
	public static final String MOD_ID = "gnvcraft";
	public static final Logger LOGGER = LogUtils.getLogger();

	public GnvCraftMod(IEventBus modBus) {
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
