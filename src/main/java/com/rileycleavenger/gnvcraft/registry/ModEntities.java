package com.rileycleavenger.gnvcraft.registry;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
	private static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(GnvCraftMod.MOD_ID);

	// noSave: HeySir only exists near his player; the director recreates him from player data.
	public static final DeferredHolder<EntityType<?>, EntityType<HeySirEntity>> HEYSIR = ENTITY_TYPES.registerEntityType(
		"heysir",
		HeySirEntity::new,
		MobCategory.MISC,
		builder -> builder
			.sized(0.8F, 2.1F)
			.eyeHeight(1.9F)
			.clientTrackingRange(10)
			.noSave()
	);

	private ModEntities() {
	}

	public static void register(IEventBus modBus) {
		ENTITY_TYPES.register(modBus);
		modBus.addListener(ModEntities::onAttributeCreation);
	}

	private static void onAttributeCreation(EntityAttributeCreationEvent event) {
		event.put(HEYSIR.get(), HeySirEntity.createAttributes().build());
	}
}
