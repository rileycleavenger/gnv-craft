package com.rileycleavenger.gnvcraft;

import com.mojang.logging.LogUtils;
import com.rileycleavenger.gnvcraft.command.GnvCommand;
import com.rileycleavenger.gnvcraft.command.HeySirCommand;
import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.drunk.DrunkSystem;
import com.rileycleavenger.gnvcraft.gameday.GameDayDirector;
import com.rileycleavenger.gnvcraft.npc.PlaneDirector;
import com.rileycleavenger.gnvcraft.npc.ScooterPlacer;
import com.rileycleavenger.gnvcraft.npc.SpecialNpcDirector;
import com.rileycleavenger.gnvcraft.registry.ModAttachments;
import com.rileycleavenger.gnvcraft.registry.ModBlocks;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import com.rileycleavenger.gnvcraft.registry.ModItems;
import com.rileycleavenger.gnvcraft.registry.ModSounds;
import com.rileycleavenger.gnvcraft.registry.ModWorldgen;
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
		ModBlocks.register(modBus);
		ModAttachments.register(modBus);
		ModWorldgen.register(modBus);
		HeySirDirector.register();
		SpecialNpcDirector.register();
		DrunkSystem.register();
		ScooterPlacer.register();
		PlaneDirector.register();
		GameDayDirector.register();
		HeySirCommand.register();
		GnvCommand.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
