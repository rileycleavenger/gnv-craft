package com.rileycleavenger.gnvcraft.registry;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.director.HeySirData;
import java.util.function.Supplier;
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

	private ModAttachments() {
	}

	public static void register(IEventBus modBus) {
		ATTACHMENT_TYPES.register(modBus);
	}
}
