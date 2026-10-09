package com.rileycleavenger.gnvcraft.world;

import com.rileycleavenger.gnvcraft.registry.ModBlocks;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every building is enterable: walls on the footprint edge with window bands, a door gap at street level, a floor every
 * FLOOR_H blocks, 8x8 rooms divided by partitions with doorways, ladder + elevator shafts between floors, and furnishings by type.
 * Everything is a pure function of (building, x, z) so chunks line up. Researched interiors will override this per OSM id.
 */
final class BuildingBlocks {
	static final int FLOOR_H = 4;
	private static final int ROOM = 8;
	private static final Map<Long, Layout> LAYOUTS = new ConcurrentHashMap<>();

	/** Cached per-building feature positions. */
	private record Layout(int doorX, int doorZ, int ladderX, int ladderZ, int elevX, int elevZ) {
	}

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

	private static boolean onPartition(GnvMap.Building b, int x, int z) {
		return Math.floorMod(x - b.minX, ROOM) == 0 || Math.floorMod(z - b.minZ, ROOM) == 0;
	}

	private static boolean isDoorway(GnvMap.Building b, int x, int z) {
		int ax = Math.floorMod(x - b.minX, ROOM);
		int az = Math.floorMod(z - b.minZ, ROOM);
		return (ax == 0 && (az == 3 || az == 4)) || (az == 0 && (ax == 3 || ax == 4));
	}

	private static boolean freeCell(GnvMap.Building b, int x, int z) {
		return GnvMap.inside(b.poly, x, z) && !isEdge(b, x, z) && !onPartition(b, x, z);
	}

	private static Layout layout(GnvMap.Building b) {
		return LAYOUTS.computeIfAbsent(b.id, id -> {
			// Door: nearest edge column to the midpoint of the footprint's first side.
			int[] p0 = b.poly[0];
			int[] p1 = b.poly[1 % b.poly.length];
			int mx = (p0[0] + p1[0]) / 2;
			int mz = (p0[1] + p1[1]) / 2;
			int doorX = mx;
			int doorZ = mz;
			search:
			for (int r = 0; r <= 4; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (GnvMap.inside(b.poly, mx + dx, mz + dz) && isEdge(b, mx + dx, mz + dz)) {
							doorX = mx + dx;
							doorZ = mz + dz;
							break search;
						}
					}
				}
			}
			int ladderX = Integer.MIN_VALUE, ladderZ = 0, elevX = Integer.MIN_VALUE, elevZ = 0;
			for (int z = b.minZ; z <= b.maxZ && elevX == Integer.MIN_VALUE; z++) {
				for (int x = b.minX; x <= b.maxX; x++) {
					if (ladderX == Integer.MIN_VALUE) {
						if (freeCell(b, x, z) && isEdge(b, x - 1, z) && GnvMap.inside(b.poly, x - 1, z)) {
							ladderX = x;
							ladderZ = z;
						}
					} else if (freeCell(b, x, z) && (x != ladderX || z != ladderZ) && Math.abs(x - ladderX) + Math.abs(z - ladderZ) >= 2) {
						elevX = x;
						elevZ = z;
						break;
					}
				}
			}
			return new Layout(doorX, doorZ, ladderX, ladderZ, elevX, elevZ);
		});
	}

	static int column(GnvMap.Building b, int x, int z, int surface, BiConsumer<Integer, BlockState> out) {
		int storeys = b.storeys();
		int height = storeys * FLOOR_H;
		Layout l = layout(b);
		boolean edge = isEdge(b, x, z);
		BlockState wall = wall(b);
		BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
		boolean door = edge && Math.abs(x - l.doorX) <= 1 && Math.abs(z - l.doorZ) <= 1;
		boolean ladder = x == l.ladderX && z == l.ladderZ;
		boolean elevator = x == l.elevX && z == l.elevZ && storeys > 1;
		String kind = b.kind == null ? "" : b.kind;
		boolean bar = kind.equals("bar") || kind.equals("pub") || kind.equals("nightclub");
		int ax = Math.floorMod(x - b.minX, ROOM);
		int az = Math.floorMod(z - b.minZ, ROOM);

		out.accept(surface, elevator ? ModBlocks.ELEVATOR.get().defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
		for (int dy = 1; dy <= height; dy++) {
			int y = surface + dy;
			int fy = dy % FLOOR_H; // 0 = floor slab, 1..3 = room space
			if (dy == height) {
				out.accept(y, Blocks.STONE_BRICKS.defaultBlockState());
			} else if (edge) {
				if (door && dy <= 2) {
					continue;
				}
				boolean window = fy == 2 && (x + z) % 3 != 0 && !(door);
				out.accept(y, window ? Blocks.GLASS_PANE.defaultBlockState() : wall);
			} else if (fy == 0) {
				if (ladder) {
					out.accept(y, ladderState());
				} else if (elevator) {
					out.accept(y, ModBlocks.ELEVATOR.get().defaultBlockState());
				} else {
					out.accept(y, floor);
				}
			} else if (ladder) {
				out.accept(y, ladderState());
			} else if (onPartition(b, x, z) && !isDoorway(b, x, z) && !elevator) {
				out.accept(y, Blocks.DYED_TERRACOTTA.pick(DyeColor.LIGHT_GRAY).defaultBlockState());
			} else if (fy == 3 && ax == 4 && az == 4) {
				out.accept(y, Blocks.SEA_LANTERN.defaultBlockState());
			} else if (fy == 1) {
				BlockState furniture = furniture(bar, kind, ax, az);
				if (furniture != null) {
					out.accept(y, furniture);
				}
			}
		}
		return surface + height;
	}

	private static BlockState ladderState() {
		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
	}

	static BlockState furniture(boolean bar, String kind, int ax, int az) {
		if (bar) {
			if (az == 6 && ax >= 2 && ax <= 6) {
				return ax == 4 ? ModBlocks.BAR_TAP.get().defaultBlockState() : Blocks.BARREL.defaultBlockState();
			}
			if (az == 2 && (ax == 2 || ax == 5)) {
				return Blocks.DARK_OAK_FENCE.defaultBlockState();
			}
			if (ax == 7 && az == 7) {
				return Blocks.JUKEBOX.defaultBlockState();
			}
			return null;
		}
		return switch (kind) {
			case "apartments", "dormitory", "residential", "house", "detached" -> {
				if (ax == 2 && az == 2) {
					yield Blocks.CRAFTING_TABLE.defaultBlockState();
				}
				if (ax == 6 && az == 6) {
					yield Blocks.FURNACE.defaultBlockState();
				}
				if (ax == 6 && az == 2) {
					yield Blocks.BOOKSHELF.defaultBlockState();
				}
				yield null;
			}
			case "restaurant", "fast_food", "cafe" -> az == 6 && ax >= 2 && ax <= 6 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : null;
			case "retail", "supermarket", "commercial", "shop" -> (ax == 3 || ax == 6) && az >= 2 && az <= 5 ? Blocks.BOOKSHELF.defaultBlockState() : null;
			default -> ax == 3 && az == 3 ? Blocks.CRAFTING_TABLE.defaultBlockState() : null;
		};
	}
}
