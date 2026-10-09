package com.rileycleavenger.heysir.director;

import com.rileycleavenger.heysir.registry.ModEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Finds a safe spot for HeySir (and his bike) to appear near a player. */
public final class SpawnFinder {
	private static final int ATTEMPTS = 24;
	private static final int VERTICAL_SEARCH = 8;

	private SpawnFinder() {
	}

	/**
	 * @param surface pick the top block of each column (first meeting) instead of searching near the
	 *                player's height; non-surface searches prefer spots behind the player so he rides into view.
	 */
	public static Optional<Vec3> find(ServerLevel level, Player player, int minDistance, int maxDistance, boolean surface, RandomSource random) {
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			float yaw = surface
				? random.nextFloat() * 360.0F
				: player.getYRot() + 180.0F + (random.nextFloat() - 0.5F) * 140.0F;
			double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
			double x = Math.floor(player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance) + 0.5;
			double z = Math.floor(player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance) + 0.5;
			if (!level.isLoaded(BlockPos.containing(x, player.getY(), z))) {
				continue;
			}
			if (surface) {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
				if (canStandAt(level, x, y, z)) {
					return Optional.of(new Vec3(x, y, z));
				}
			} else {
				int baseY = Mth.floor(player.getY());
				for (int offset = 0; offset <= VERTICAL_SEARCH * 2; offset++) {
					int y = baseY + ((offset & 1) == 0 ? offset / 2 : -(offset / 2 + 1));
					if (canStandAt(level, x, y, z)) {
						return Optional.of(new Vec3(x, y, z));
					}
				}
			}
		}
		if (!surface && minDistance > 4) {
			return find(level, player, 2, 4, false, random);
		}
		return Optional.empty();
	}

	private static boolean canStandAt(ServerLevel level, double x, int y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		BlockPos floor = pos.below();
		return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
			&& level.getFluidState(pos).isEmpty()
			&& level.noCollision(ModEntities.HEYSIR.get().getSpawnAABB(x, y, z));
	}
}
