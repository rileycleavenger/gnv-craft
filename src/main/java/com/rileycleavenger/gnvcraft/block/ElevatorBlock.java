package com.rileycleavenger.gnvcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Elevator call button/pad: stacked in a shaft, one per floor. Click to ride up; sneak-click to ride down. */
public class ElevatorBlock extends Block {
	private static final int MAX_RIDE = 96;

	public ElevatorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		int dir = player.isShiftKeyDown() ? -1 : 1;
		for (int i = 1; i <= MAX_RIDE; i++) {
			BlockPos target = pos.offset(0, dir * i, 0);
			if (level.isOutsideBuildHeight(target)) {
				break;
			}
			if (level.getBlockState(target).is(this)) {
				if (!level.isClientSide()) {
					player.teleportTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
					level.playSound(null, target, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 1.2F);
					player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(dir > 0 ? "Going up" : "Going down"));
				}
				return InteractionResult.SUCCESS;
			}
		}
		if (!level.isClientSide()) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(dir > 0 ? "Top floor" : "Ground floor"));
		}
		return InteractionResult.SUCCESS;
	}
}
