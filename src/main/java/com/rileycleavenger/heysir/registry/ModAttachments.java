package com.rileycleavenger.heysir.registry;

import com.rileycleavenger.heysir.HeySirMod;
import com.rileycleavenger.heysir.director.HeySirData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	public static final AttachmentType<HeySirData> HEYSIR_DATA = AttachmentRegistry.create(
		HeySirMod.id("heysir_data"),
		builder -> builder
			.initializer(() -> HeySirData.DEFAULT)
			.persistent(HeySirData.CODEC)
			.copyOnDeath()
	);

	private ModAttachments() {
	}

	public static void initialize() {
	}
}
