package com.rileycleavenger.gnvcraft.world;

import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import java.util.function.BiConsumer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Shell of a building: walls on the footprint edge, a floor every FLOOR_H blocks, window bands, a roof. The interior is filled by the interior generators. */
final class BuildingBlocks {
	static final int FLOOR_H = 4;

	private BuildingBlocks() {
	}

	static BlockState wall(GnvMap.Building b) {
		String k = b.kind == null ? "" : b.kind;
		return switch (k) {
			case "apartments", "dormitory", "residential" -> Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE).defaultBlockState();
			case "university", "college", "school", "public" -> Blocks.BRICKS.defaultBlockState();
			case "bar", "pub", "nightclub" -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
			case "stadium" -> Blocks.CONCRETE.pick(DyeColor.ORANGE).defaultBlockState();
			case "house", "detached" -> Blocks.OAK_PLANKS.defaultBlockState();
			default -> Blocks.SMOOTH_STONE.defaultBlockState();
		};
	}

	/** Whether (x,z) is on the outer ring of the footprint. */
	static boolean isEdge(GnvMap.Building b, int x, int z) {
		return !GnvMap.inside(b.poly, x - 1, z) || !GnvMap.inside(b.poly, x + 1, z)
			|| !GnvMap.inside(b.poly, x, z - 1) || !GnvMap.inside(b.poly, x, z + 1);
	}

	static int column(GnvMap.Building b, int x, int z, int surface, BiConsumer<Integer, BlockState> out) {
		int storeys = b.storeys();
		int height = storeys * FLOOR_H;
		boolean edge = isEdge(b, x, z);
		BlockState wall = wall(b);
		BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
		out.accept(surface, Blocks.POLISHED_ANDESITE.defaultBlockState());
		for (int dy = 1; dy <= height; dy++) {
			int y = surface + dy;
			boolean floorLevel = dy % FLOOR_H == 0;
			if (dy == height) {
				out.accept(y, Blocks.STONE_BRICKS.defaultBlockState());
			} else if (edge) {
				boolean window = dy % FLOOR_H == 2 && (x + z) % 3 != 0;
				out.accept(y, window ? Blocks.GLASS_PANE.defaultBlockState() : wall);
			} else if (floorLevel) {
				out.accept(y, floor);
			}
		}
		return surface + height;
	}
}
