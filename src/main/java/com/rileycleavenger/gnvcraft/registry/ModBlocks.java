package com.rileycleavenger.gnvcraft.registry;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.block.BarTapBlock;
import com.rileycleavenger.gnvcraft.block.ElevatorBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GnvCraftMod.MOD_ID);
	private static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(GnvCraftMod.MOD_ID);

	public static final DeferredBlock<ElevatorBlock> ELEVATOR = BLOCKS.registerBlock(
		"elevator", ElevatorBlock::new, p -> p.strength(3.0F, 6.0F).requiresCorrectToolForDrops().lightLevel(s -> 6)
	);
	public static final DeferredBlock<BarTapBlock> BAR_TAP = BLOCKS.registerBlock(
		"bar_tap", BarTapBlock::new, p -> p.strength(2.0F).lightLevel(s -> 4)
	);
	public static final DeferredItem<BlockItem> ELEVATOR_ITEM = BLOCK_ITEMS.registerSimpleBlockItem(ELEVATOR);
	public static final DeferredItem<BlockItem> BAR_TAP_ITEM = BLOCK_ITEMS.registerSimpleBlockItem(BAR_TAP);

	private ModBlocks() {
	}

	public static void register(IEventBus modBus) {
		BLOCKS.register(modBus);
		BLOCK_ITEMS.register(modBus);
		modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
			if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
				event.accept(ELEVATOR_ITEM);
				event.accept(BAR_TAP_ITEM);
			}
		});
	}
}
