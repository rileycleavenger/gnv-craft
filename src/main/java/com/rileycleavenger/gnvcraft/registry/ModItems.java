package com.rileycleavenger.gnvcraft.registry;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GnvCraftMod.MOD_ID);

	public static final DeferredItem<Item> MCDONALDS_GIFTCARD = ITEMS.registerItem(
		"mcdonalds_giftcard", Item::new, properties -> properties.rarity(Rarity.UNCOMMON)
	);
	// Entity types register before items, so HEYSIR is available here.
	public static final DeferredItem<SpawnEggItem> HEYSIR_SPAWN_EGG = ITEMS.registerItem(
		"heysir_spawn_egg", SpawnEggItem::new, properties -> properties.spawnEgg(ModEntities.HEYSIR.get())
	);

	private ModItems() {
	}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
		modBus.addListener(ModItems::onBuildCreativeTabs);
	}

	private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
			event.accept(MCDONALDS_GIFTCARD);
		} else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
			event.accept(HEYSIR_SPAWN_EGG);
		}
	}
}
