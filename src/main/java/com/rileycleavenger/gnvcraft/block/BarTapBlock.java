package com.rileycleavenger.gnvcraft.block;

import com.rileycleavenger.gnvcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Bar tap: click for a beer (sneak-click for a shot). */
public class BarTapBlock extends Block {
	public BarTapBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			ItemStack drink = new ItemStack(player.isShiftKeyDown() ? ModItems.SHOT.get() : ModItems.BEER.get());
			if (!player.getInventory().add(drink)) {
				player.drop(drink, false);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
