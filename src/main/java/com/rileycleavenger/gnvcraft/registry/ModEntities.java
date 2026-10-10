package com.rileycleavenger.gnvcraft.registry;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.entity.ChandlerEntity;
import com.rileycleavenger.gnvcraft.entity.DennisEntity;
import com.rileycleavenger.gnvcraft.entity.DonnieEntity;
import com.rileycleavenger.gnvcraft.entity.FanEntity;
import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import com.rileycleavenger.gnvcraft.entity.LimeScooterEntity;
import com.rileycleavenger.gnvcraft.entity.PlaneEntity;
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

	public static final DeferredHolder<EntityType<?>, EntityType<DennisEntity>> DENNIS = ENTITY_TYPES.registerEntityType(
		"dennis",
		DennisEntity::new,
		MobCategory.MISC,
		builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10)
	);

	public static final DeferredHolder<EntityType<?>, EntityType<FanEntity>> FAN = ENTITY_TYPES.registerEntityType(
		"fan",
		FanEntity::new,
		MobCategory.MISC,
		builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8).noSave()
	);

	public static final DeferredHolder<EntityType<?>, EntityType<LimeScooterEntity>> LIME_SCOOTER = ENTITY_TYPES.registerEntityType(
		"lime_scooter",
		LimeScooterEntity::new,
		MobCategory.MISC,
		builder -> builder.sized(0.6F, 1.1F).clientTrackingRange(8)
	);

	public static final DeferredHolder<EntityType<?>, EntityType<PlaneEntity>> PLANE = ENTITY_TYPES.registerEntityType(
		"plane",
		PlaneEntity::new,
		MobCategory.MISC,
		builder -> builder.sized(3.0F, 1.6F).passengerAttachments(0.7F).clientTrackingRange(16).updateInterval(2)
	);

	public static final DeferredHolder<EntityType<?>, EntityType<ChandlerEntity>> CHANDLER = ENTITY_TYPES.registerEntityType(
		"chandler", ChandlerEntity::new, MobCategory.MISC, builder -> builder.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10)
	);
	public static final DeferredHolder<EntityType<?>, EntityType<DonnieEntity>> DONNIE = ENTITY_TYPES.registerEntityType(
		"donnie", DonnieEntity::new, MobCategory.MISC, builder -> builder.sized(0.75F, 2.05F).eyeHeight(1.85F).clientTrackingRange(10)
	);

	private ModEntities() {
	}

	public static void register(IEventBus modBus) {
		ENTITY_TYPES.register(modBus);
		modBus.addListener(ModEntities::onAttributeCreation);
	}

	private static void onAttributeCreation(EntityAttributeCreationEvent event) {
		event.put(HEYSIR.get(), HeySirEntity.createAttributes().build());
		event.put(DENNIS.get(), DennisEntity.createAttributes().build());
		event.put(CHANDLER.get(), ChandlerEntity.createAttributes().build());
		event.put(DONNIE.get(), DonnieEntity.createAttributes().build());
		event.put(FAN.get(), FanEntity.createAttributes().build());
		event.put(LIME_SCOOTER.get(), LimeScooterEntity.createAttributes().build());
		event.put(PLANE.get(), PlaneEntity.createAttributes().build());
	}
}
