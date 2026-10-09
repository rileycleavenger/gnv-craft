package com.rileycleavenger.heysir.registry;

import com.rileycleavenger.heysir.HeySirMod;
import com.rileycleavenger.heysir.entity.HeySirEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> HEYSIR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, HeySirMod.id("heysir"));

	// noSave: HeySir only exists near his player; the director recreates him from player data.
	public static final EntityType<HeySirEntity> HEYSIR = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		HEYSIR_KEY,
		EntityType.Builder.<HeySirEntity>of(HeySirEntity::new, MobCategory.MISC)
			.sized(0.8F, 2.1F)
			.eyeHeight(1.9F)
			.clientTrackingRange(10)
			.noSave()
			.build(HEYSIR_KEY)
	);

	private ModEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(HEYSIR, HeySirEntity.createAttributes());
	}
}
