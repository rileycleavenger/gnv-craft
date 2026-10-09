package com.rileycleavenger.gnvcraft.world;

import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Decides every block of one chunk column from the map features that overlap the chunk. */
final class ChunkPlan {
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
	private static final BlockState ASPHALT = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
	private static final BlockState ROAD_GRAY = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState SIDEWALK = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState LINE = Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState();
	private static final BlockState PARKING = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	private static final BlockState APRON = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState PITCH = Blocks.CONCRETE_POWDER.pick(DyeColor.GREEN).defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState SAND = Blocks.SAND.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final int x0, z0;
	private final List<GnvMap.Road> roads;
	private final List<GnvMap.Area> areas;
	private final java.util.List<GnvMap.Building> buildings;

	ChunkPlan(GnvMap map, int x0, int z0) {
		this.x0 = x0;
		this.z0 = z0;
		this.roads = map.query(GnvMap.Road.class, x0, z0, x0 + 15, z0 + 15);
		this.areas = map.query(GnvMap.Area.class, x0, z0, x0 + 15, z0 + 15);
		this.buildings = new java.util.ArrayList<>(map.query(GnvMap.Building.class, x0 - 1, z0 - 1, x0 + 16, z0 + 16));
		this.buildings.removeIf(b -> com.rileycleavenger.gnvcraft.world.map.InteriorSpecs.isHidden(b.id));
	}

	/** Writes the column and returns the top Y that was set. */
	int build(int lx, int lz, BiConsumer<Integer, BlockState> out) {
		int x = this.x0 + lx;
		int z = this.z0 + lz;
		int s = GnvChunkGenerator.SURFACE_Y;
		out.accept(-64, BEDROCK);
		for (int y = -63; y < s - 3; y++) {
			out.accept(y, STONE);
		}
		for (int y = s - 3; y < s; y++) {
			out.accept(y, DIRT);
		}
		BlockState top = this.ground(x, z);
		if (top == WATER) {
			out.accept(s, SAND);
			out.accept(s + 1, WATER);
			return s + 1;
		}
		out.accept(s, top);
		GnvMap.Building b = this.buildingAt(x, z);
		if (b != null) {
			return BuildingBlocks.column(b, x, z, s, out);
		}
		return s;
	}

	private BlockState ground(int x, int z) {
		BlockState g = GRASS;
		for (GnvMap.Area a : this.areas) {
			if (x >= a.minX && x <= a.maxX && z >= a.minZ && z <= a.maxZ && GnvMap.inside(a.poly, x, z)) {
				g = switch (a.kind) {
					case "water" -> WATER;
					case "parking" -> PARKING;
					case "pitch" -> PITCH;
					case "aero_runway", "aero_taxiway" -> ASPHALT;
					case "aero_apron", "aero_terminal" -> APRON;
					default -> g;
				};
			}
		}
		for (GnvMap.Road r : this.roads) {
			if (x < r.minX || x > r.maxX || z < r.minZ || z > r.maxZ) {
				continue;
			}
			double half = r.w / 2.0;
			double d2 = GnvMap.distSq(r.pts, x, z);
			if (d2 <= half * half) {
				boolean centre = d2 <= 0.6 && r.w >= 8;
				g = centre && (x + z) % 4 < 2 ? LINE : ASPHALT;
			} else if (d2 <= (half + 2) * (half + 2) && g == GRASS && r.w >= 7) {
				g = SIDEWALK;
			}
		}
		return g;
	}

	GnvMap.Building buildingAt(int x, int z) {
		for (GnvMap.Building b : this.buildings) {
			if (x >= b.minX && x <= b.maxX && z >= b.minZ && z <= b.maxZ && GnvMap.inside(b.poly, x, z)) {
				return b;
			}
		}
		return null;
	}
}
