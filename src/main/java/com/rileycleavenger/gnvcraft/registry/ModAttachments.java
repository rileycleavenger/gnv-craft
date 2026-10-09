package com.rileycleavenger.gnvcraft.registry;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.director.HeySirData;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GnvCraftMod.MOD_ID);

	public static final Supplier<AttachmentType<HeySirData>> HEYSIR_DATA = ATTACHMENT_TYPES.register(
		"heysir_data",
		() -> AttachmentType.builder(() -> HeySirData.DEFAULT)
			.serialize(HeySirData.MAP_CODEC)
			.copyOnDeath()
			.build()
	);

	/** Overworld attachment: first in-game day Dennis may respawn after being killed. */
	public static final Supplier<AttachmentType<Long>> DENNIS_RESPAWN_DAY = ATTACHMENT_TYPES.register(
		"dennis_respawn_day",
		() -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("day")).build()
	);

	/** Drunk level 0..9, synced to its own client for the screen effects. Not kept across death. */
	public static final Supplier<AttachmentType<Float>> DRUNK = ATTACHMENT_TYPES.register(
		"drunk",
		() -> AttachmentType.builder(() -> 0.0F).serialize(Codec.FLOAT.fieldOf("level")).sync(ByteBufCodecs.FLOAT).build()
	);

	/** Overworld attachment: ids of scooter spots that already got their scooter. */
	public static final Supplier<AttachmentType<java.util.List<Integer>>> SCOOTERS_PLACED = ATTACHMENT_TYPES.register(
		"scooters_placed",
		() -> AttachmentType.<java.util.List<Integer>>builder(() -> new java.util.ArrayList<>()).serialize(Codec.INT.listOf().fieldOf("ids")).build()
	);

	/** Overworld attachment: first in-game day the airport plane may (re)spawn. */
	public static final Supplier<AttachmentType<Long>> PLANE_RESPAWN_DAY = ATTACHMENT_TYPES.register(
		"plane_respawn_day",
		() -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("day")).build()
	);

	private ModAttachments() {
	}

	public static void register(IEventBus modBus) {
		ATTACHMENT_TYPES.register(modBus);
	}
}
