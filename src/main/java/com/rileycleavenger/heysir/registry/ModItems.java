package com.rileycleavenger.heysir.registry;

import com.rileycleavenger.heysir.HeySirMod;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

public final class ModItems {
	public static final Item MCDONALDS_GIFTCARD = register("mcdonalds_giftcard", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item HEYSIR_SPAWN_EGG = register("heysir_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.HEYSIR));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HeySirMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(MCDONALDS_GIFTCARD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(HEYSIR_SPAWN_EGG));
	}
}
