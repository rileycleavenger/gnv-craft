package com.rileycleavenger.gnvcraft.registry;

import com.mojang.serialization.MapCodec;
import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
	private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, GnvCraftMod.MOD_ID);

	static {
		CHUNK_GENERATORS.register("gainesville", () -> GnvChunkGenerator.CODEC);
	}

	private ModWorldgen() {
	}

	public static void register(IEventBus modBus) {
		CHUNK_GENERATORS.register(modBus);
	}
}
