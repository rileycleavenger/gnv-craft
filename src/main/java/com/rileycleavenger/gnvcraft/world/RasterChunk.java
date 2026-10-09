package com.rileycleavenger.gnvcraft.world;

import com.rileycleavenger.gnvcraft.registry.ModBlocks;
import com.rileycleavenger.gnvcraft.world.map.GnvRaster;
import com.rileycleavenger.gnvcraft.world.map.InteriorSpecs;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a chunk inside the LiDAR core: measured terrain, OSM surfaces and lane markings, LiDAR buildings (real footprints and
 * roof heights, with floors, rooms, doors, stairs and elevators inside), and trees grown from the measured canopy.
 */
final class RasterChunk {
	interface Sink {
		void set(int x, int y, int z, BlockState state);
	}

	private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
	private static final int ROOM = 8;

	private RasterChunk() {
	}

	static BlockState b(String id) {
		return STATES.computeIfAbsent(id, s -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
			} catch (Exception e) {
				return Blocks.STONE.defaultBlockState();
			}
		});
	}

	private static BlockState surfaceBlock(int code) {
		return switch (code) {
			case GnvRaster.S_ASPHALT, GnvRaster.S_PARKING -> b("minecraft:gray_concrete");
			case GnvRaster.S_CONCRETE -> b("minecraft:light_gray_concrete");
			case GnvRaster.S_SAND -> b("minecraft:sand");
			case GnvRaster.S_LANE_WHITE, GnvRaster.S_CROSSWALK, GnvRaster.S_EDGE_WHITE -> b("minecraft:white_concrete");
			case GnvRaster.S_CENTER_YELLOW -> b("minecraft:yellow_concrete");
			case GnvRaster.S_BRICK -> b("minecraft:bricks");
			case GnvRaster.S_PATH_DIRT -> b("minecraft:dirt_path");
			case GnvRaster.S_FLOOR -> b("minecraft:polished_andesite");
			case GnvRaster.S_TRACK -> b("minecraft:red_terracotta");
			case GnvRaster.S_GRAVEL -> b("minecraft:gravel");
			case GnvRaster.S_ORANGE -> b("minecraft:orange_concrete");
			case GnvRaster.S_BLUE -> b("minecraft:blue_concrete");
			case GnvRaster.S_TURF, GnvRaster.S_GOAL_POST, GnvRaster.S_GOAL_BAR, GnvRaster.S_GOAL_UPRIGHT -> b("minecraft:moss_block");
			default -> b("minecraft:grass_block");
		};
	}

	private static boolean paved(int code) {
		return code != GnvRaster.S_GRASS && code != GnvRaster.S_TURF && code != GnvRaster.S_SAND && code != GnvRaster.S_PATH_DIRT;
	}

	/** Facade block for an edge cell: the photo-derived facade if the building has one, else a plausible material for its type. */
	private static BlockState facade(GnvRaster.Building info, int x, int y, int z, int floor) {
		GnvRaster.Facade f = info.facade;
		if (f == null) {
			// UF's collegiate buildings: red-orange brick with tan stone courses at each floor line (seen in street photos)
			String k = info.kind == null ? "" : info.kind;
			boolean campus = k.equals("university") || k.equals("college") || k.equals("library");
			if (campus && Math.floorMod(y - info.baseY, Math.max(3, info.floorH)) == 0) {
				return b("minecraft:smooth_sandstone");
			}
			return wall(info);
		}
		if (f.trim != null && Math.floorMod(y - info.baseY, Math.max(3, info.floorH)) == 0) {
			return b(f.trim);
		}
		if (f.plinth != null && y == info.baseY + 1) {
			return b(f.plinth);
		}
		if (f.band != null && f.bandEvery > 0 && Math.floorMod(x + z, f.bandEvery) == 0) {
			return b(f.band);
		}
		return b(floor < f.baseFloors ? f.base : f.upper);
	}

	private static final String[] HOUSE_COLORS = {"minecraft:white_concrete", "minecraft:smooth_quartz", "minecraft:end_stone", "minecraft:light_gray_concrete",
		"minecraft:smooth_sandstone", "minecraft:light_blue_terracotta", "minecraft:white_terracotta", "minecraft:birch_planks"};
	private static final String[] COMMERCIAL_COLORS = {"minecraft:smooth_sandstone", "minecraft:white_concrete", "minecraft:light_gray_concrete",
		"minecraft:bricks", "minecraft:sandstone", "minecraft:stone_bricks"};

	private static BlockState wall(GnvRaster.Building info) {
		String k = info.kind == null ? "" : info.kind;
		int pick = (int) ((info.i * 2654435761L) >>> 8) & 0xFFFF;   // stable per-building variety
		return switch (k) {
			case "apartments", "dormitory", "residential" -> b(pick % 3 == 0 ? "minecraft:bricks" : pick % 3 == 1 ? "minecraft:white_concrete" : "minecraft:smooth_sandstone");
			case "university", "college", "school", "public", "library" -> b("minecraft:bricks");
			case "bar", "pub", "nightclub" -> b("minecraft:dark_oak_planks");
			case "house", "detached", "semidetached_house", "yes" -> b(info.levels <= 2 && (info.topY - info.baseY) <= 9
				? HOUSE_COLORS[pick % HOUSE_COLORS.length] : COMMERCIAL_COLORS[pick % COMMERCIAL_COLORS.length]);
			case "church", "place_of_worship" -> b("minecraft:stone_bricks");
			case "parking", "garage", "garages" -> b("minecraft:smooth_stone");
			case "hotel" -> b("minecraft:quartz_block");
			default -> b(COMMERCIAL_COLORS[pick % COMMERCIAL_COLORS.length]);
		};
	}

	private static BlockState roofBlock(GnvRaster.Building info) {
		String k = info.kind == null ? "" : info.kind;
		return switch (k) {
			case "house", "detached", "semidetached_house", "residential" -> b("minecraft:dark_gray_concrete");
			case "university", "college", "school", "library", "church" -> b("minecraft:terracotta");
			default -> b("minecraft:light_gray_concrete");
		};
	}

	private static boolean insideSpec(int x, int z) {
		for (InteriorSpecs.Spec s : InteriorSpecs.all()) {
			if (!s.overlay && x >= s.minX() && x <= s.maxX() && z >= s.minZ() && z <= s.maxZ()) {
				return true;
			}
		}
		return false;
	}

	static void build(int x0, int z0, Sink out) {
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				column(x0 + lx, z0 + lz, out);
			}
		}
	}

	private static void column(int x, int z, Sink out) {
		int g = GnvRaster.ground(x, z);
		int s = GnvRaster.surface(x, z);
		out.set(x, -64, z, b("minecraft:bedrock"));
		for (int y = -63; y < g - 3; y++) {
			out.set(x, y, z, b("minecraft:stone"));
		}
		for (int y = Math.max(-63, g - 3); y < g; y++) {
			out.set(x, y, z, b("minecraft:dirt"));
		}
		int bi = GnvRaster.building(x, z);
		boolean spec = insideSpec(x, z);
		if (s == GnvRaster.S_WATER || (GnvRaster.pool(x, z) && bi == 0 && !spec)) {
			out.set(x, g - 2, z, b("minecraft:sand"));
			out.set(x, g - 1, z, b("minecraft:water"));
			out.set(x, g, z, b("minecraft:water"));
			return;
		}
		if (bi > 0 && !spec) {
			GnvRaster.Building info = GnvRaster.info(bi);
			if (info != null && "stadium".equals(info.kind) && (info.fieldX != 0 || info.fieldZ != 0)
				&& !(GnvRaster.roof(x, z) - info.baseY > 36 && x < info.fieldX - 40)) {
				stadium(info, bi, x, z, g, out);
				return;
			}
			if (info != null) {
				building(info, bi, x, z, g, out);
				return;
			}
		}
		out.set(x, g, z, surfaceBlock(spec && (s == GnvRaster.S_FLOOR || bi > 0) ? GnvRaster.S_GRASS : s));
		goalpost(s, x, g, z, out);
		// grass tufts and flowers on open lawns, never on paving
		if (!paved(s) && s == GnvRaster.S_GRASS && GnvRaster.leafTop(x, z) == 0) {
			int h = Math.floorMod(x * 73428767 ^ z * 912931, 97);
			if (h < 6) {
				out.set(x, g + 1, z, b("minecraft:short_grass"));
			}
		}
		if (!spec) {
			trees(x, z, g, s, out);
		}
	}

	private static void trees(int x, int z, int g, int s, Sink out) {
		int top = GnvRaster.leafTop(x, z);
		if (top <= g) {
			return;
		}
		int bot = Math.max(g + 2, GnvRaster.leafBot(x, z));
		// light poles, antennas and wires are unclassified in this LiDAR too: only real crowns (surrounded by canopy) become trees
		int support = canopySupport(x, z);
		// cranes, light towers and wires also come back as "canopy": nothing in Gainesville's tree canopy is over ~32 m
		if (support < 3 || top - g > 32) {
			return;
		}
		if (GnvRaster.trunk(x, z) && support >= 7 && (!paved(s) || s == GnvRaster.S_CONCRETE)) {
			for (int y = g + 1; y < top; y++) {
				out.set(x, y, z, b("minecraft:oak_log"));
			}
		}
		for (int y = bot; y <= top; y++) {
			if (GnvRaster.trunk(x, z) && y != top) {
				continue;
			}
			// the canopy's outer surface gets ragged gaps so crowns don't read as hedge walls
			if (outerLeaf(x, y, z) && Math.floorMod((x * 734287 + y * 912931 + z * 438289) >>> 3, 100) < 38) {
				continue;
			}
			out.set(x, y, z, b("minecraft:oak_leaves[persistent=true]"));
		}
		// Spanish moss under live oak canopies
		if (Math.floorMod(x * 31 + z * 17, 9) == 0 && bot - 1 > g + 2) {
			out.set(x, bot - 1, z, b("minecraft:pale_hanging_moss[tip=true]"));
		}
	}

	/**
	 * Apartment floors: a double-loaded corridor every 18 blocks across the building, units 7 blocks wide on both sides with a
	 * door from the corridor, a kitchen by the door, and a bedroom behind an inner wall. The ground floor is an open lobby.
	 */
	private static void apartmentCell(int floor, int fy, int cm, int mm, boolean alongX, int x, int y, int z, Sink out) {
		if (floor == 0) {
			if (fy == 1 && cm == 12 && mm >= 2 && mm <= 5) {
				out.set(x, y, z, b("minecraft:smooth_quartz"));          // lobby / leasing desk
			} else if (fy == 1 && (cm == 4 || cm == 15) && mm == 3) {
				out.set(x, y, z, b("minecraft:quartz_stairs[facing=north,half=bottom]"));
			}
			return;
		}
		boolean corridor = cm == 8 || cm == 9;
		boolean corridorWall = cm == 7 || cm == 10;
		if (corridor) {
			return;
		}
		if (corridorWall) {
			if (mm == 3 && fy <= 2) {
				String facing = alongX ? (cm == 7 ? "north" : "south") : (cm == 7 ? "west" : "east");
				out.set(x, y, z, b("minecraft:spruce_door[facing=" + facing + ",half=" + (fy == 1 ? "lower" : "upper") + ",hinge=left,open=false]"));
			} else {
				out.set(x, y, z, b("minecraft:white_concrete"));
			}
			return;
		}
		if (mm == 0) {
			out.set(x, y, z, b("minecraft:white_concrete"));                 // wall between units
			return;
		}
		boolean inner = cm == 3 || cm == 14;                                    // living | bedroom wall
		if (inner) {
			out.set(x, y, z, mm == 5 && fy <= 2 ? b("minecraft:air") : b("minecraft:white_concrete"));
			return;
		}
		if (fy != 1) {
			return;
		}
		boolean bedroomSide = cm <= 2 || cm >= 15;
		if (bedroomSide) {
			if ((cm == 1 || cm == 16) && (mm == 2 || mm == 3)) {
				String facing = alongX ? (cm == 1 ? "north" : "south") : (cm == 1 ? "west" : "east");
				out.set(x, y, z, b("minecraft:light_blue_bed[part=" + (mm == 2 ? "foot" : "head") + ",facing=" + (alongX ? "east" : "south") + "]"));
			} else if ((cm == 1 || cm == 16) && mm == 6) {
				out.set(x, y, z, b("minecraft:barrel[facing=up]"));
			}
		} else if ((cm == 6 || cm == 11) && mm >= 4) {
			out.set(x, y, z, mm == 5 ? b("minecraft:furnace") : b("minecraft:smooth_quartz"));  // kitchen by the door
		} else if ((cm == 4 || cm == 13) && mm >= 1 && mm <= 3) {
			out.set(x, y, z, b("minecraft:gray_wool"));                    // couch
		}
	}

	private static int canopySupport(int x, int z) {
		int n = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && GnvRaster.covers(x + dx, z + dz) && GnvRaster.leafTop(x + dx, z + dz) > 0) {
					n++;
				}
			}
		}
		return n;
	}

	private static boolean hasLeaf(int x, int y, int z) {
		if (!GnvRaster.covers(x, z)) {
			return false;
		}
		int top = GnvRaster.leafTop(x, z);
		return top > 0 && y <= top && y >= GnvRaster.leafBot(x, z);
	}

	private static boolean outerLeaf(int x, int y, int z) {
		return !hasLeaf(x + 1, y, z) || !hasLeaf(x - 1, y, z) || !hasLeaf(x, y, z + 1) || !hasLeaf(x, y, z - 1) || !hasLeaf(x, y + 1, z);
	}

	/** Goalposts on a football field: single post, crossbar 10 ft up, uprights to 30 ft (in blocks: 3 and 9). */
	private static void goalpost(int s, int x, int g, int z, Sink out) {
		BlockState yellow = b("minecraft:yellow_concrete");
		switch (s) {
			case GnvRaster.S_GOAL_POST -> {
				for (int y = g + 1; y <= g + 3; y++) {
					out.set(x, y, z, yellow);
				}
			}
			case GnvRaster.S_GOAL_BAR -> out.set(x, g + 3, z, yellow);
			case GnvRaster.S_GOAL_UPRIGHT -> {
				for (int y = g + 3; y <= g + 9; y++) {
					out.set(x, y, z, yellow);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * A stadium column: the measured LiDAR seating surface, coloured from the aerial photo, solid concrete under it with a
	 * concourse at ground level, and the photographed brick/concrete exterior on the outer edge.
	 */
	private static void stadium(GnvRaster.Building info, int bi, int x, int z, int g, Sink out) {
		int base = info.baseY;
		int top = GnvRaster.roof(x, z);
		boolean edge = !sameBuilding(bi, x - 1, z) || !sameBuilding(bi, x + 1, z) || !sameBuilding(bi, x, z - 1) || !sameBuilding(bi, x, z + 1);
		boolean nearEdge = !edge && (!sameBuilding(bi, x - 2, z) || !sameBuilding(bi, x + 2, z) || !sameBuilding(bi, x, z - 2) || !sameBuilding(bi, x, z + 2));
		String photo = GnvRaster.roofColor(x, z);
		BlockState seat = b(photo != null ? photo : "minecraft:light_gray_concrete");
		out.set(x, base, z, b("minecraft:smooth_stone"));
		boolean low = top - base <= 2;                                  // the padded field wall
		for (int y = base + 1; y < top; y++) {
			int fy = y - base;
			if (edge && !low) {
				boolean opening = fy <= 4 && Math.floorMod(x + z, 6) < 3 || fy > 4 && fy % 5 >= 2 && fy % 5 <= 3 && Math.floorMod(x + z, 4) != 0;
				out.set(x, y, z, opening ? b("minecraft:air") : b(fy <= 15 ? "minecraft:bricks" : "minecraft:light_gray_concrete"));
			} else if (!low && !nearEdge && fy <= 4 && top - base > 10) {
				if (fy == 4 && Math.floorMod(x * 7 + z * 13, 11) == 0) {
					out.set(x, y, z, b("minecraft:sea_lantern"));          // concourse lights
				}
			} else {
				out.set(x, y, z, b("minecraft:light_gray_concrete"));
			}
		}
		out.set(x, top, z, low ? b("minecraft:orange_concrete") : seat);
	}

	private static boolean sameBuilding(int bi, int x, int z) {
		return GnvRaster.covers(x, z) && GnvRaster.building(x, z) == bi;
	}

	private static void building(GnvRaster.Building info, int bi, int x, int z, int g, Sink out) {
		int base = info.baseY;
		int roofY = GnvRaster.roof(x, z);
		int floorH = Math.max(3, info.floorH);
		int levels = Math.max(1, info.levels);
		boolean edge = !sameBuilding(bi, x - 1, z) || !sameBuilding(bi, x + 1, z) || !sameBuilding(bi, x, z - 1) || !sameBuilding(bi, x, z + 1);
		// lowest neighbouring roof inside this building: fill down to it so stepped and pitched roofs are closed
		int lowNeighbour = roofY;
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (sameBuilding(bi, x + d[0], z + d[1])) {
				lowNeighbour = Math.min(lowNeighbour, GnvRaster.roof(x + d[0], z + d[1]));
			}
		}
		int interiorTop = Math.min(roofY - 1, base + levels * floorH);
		BlockState wall = wall(info);
		boolean isDoor = info.door != null && info.door.x == x && info.door.z == z;
		boolean stair = info.stair != null && info.stair[0] == x && info.stair[1] == z;
		boolean elevator = info.elevator != null && info.elevator[0] == x && info.elevator[1] == z && levels > 1;
		String kind = info.kind == null ? "" : info.kind;
		boolean bar = kind.equals("bar") || kind.equals("pub") || kind.equals("nightclub");
		boolean open = bar || kind.equals("parking") || kind.equals("stadium") || kind.equals("church") || kind.equals("garage");
		int ax = Math.floorMod(x - info.minX, ROOM);
		int az = Math.floorMod(z - info.minZ, ROOM);
		boolean partition = !open && (ax == 0 || az == 0);
		boolean doorway = (ax == 0 && (az == 3 || az == 4)) || (az == 0 && (ax == 3 || ax == 4));
		boolean apartments = (kind.equals("apartments") || kind.equals("dormitory") || kind.equals("residential")) && levels >= 3;
		boolean alongX = (info.maxX - info.minX) >= (info.maxZ - info.minZ);
		int main = alongX ? x - info.minX : z - info.minZ;
		int cm = Math.floorMod(alongX ? z - info.minZ : x - info.minX, 18);
		int mm = Math.floorMod(main, 7);

		for (int y = g; y < base; y++) {
			out.set(x, y, z, wall);
		}
		out.set(x, base, z, elevator ? ModBlocks.ELEVATOR.get().defaultBlockState() : b("minecraft:polished_andesite"));
		for (int y = base + 1; y <= roofY; y++) {
			int fy = y - base;
			boolean slab = fy % floorH == 0 && y <= interiorTop + 1;
			if (y == roofY || (y > lowNeighbour && y > interiorTop)) {
				String photo = GnvRaster.roofColor(x, z);
				if (y == roofY && !edge && GnvRaster.pool(x, z)) {
					out.set(x, y, z, b("minecraft:water"));
				} else {
					out.set(x, y, z, photo != null && y == roofY ? b(photo) : roofBlock(info));
				}
			} else if (edge) {
				if (isDoor && fy <= 2) {
					out.set(x, y, z, b("minecraft:oak_door[facing=" + info.door.facing + ",half=" + (fy == 1 ? "lower" : "upper") + ",hinge=left,open=false]"));
					continue;
				}
				boolean window = y <= interiorTop && fy % floorH >= 1 && fy % floorH <= Math.min(2, floorH - 2) && Math.floorMod(x + z, 4) != 0
					&& !(kind.equals("parking") || kind.equals("garage"));
				// recessed balconies: every 12th bay from the third floor up, railing at the facade and open above it
				if (info.facade != null && info.facade.rail != null && fy / floorH >= 2 && y <= interiorTop
					&& Math.floorMod(x + z, 12) >= 4 && Math.floorMod(x + z, 12) <= 6 && fy % floorH != 0) {
					out.set(x, y, z, fy % floorH == 1 ? b(info.facade.rail) : b("minecraft:air"));
					continue;
				}
				out.set(x, y, z, window ? b("minecraft:glass") : facade(info, x, y, z, fy / floorH));
			} else if (y > interiorTop) {
				// attic / space under a pitched roof
			} else if (slab) {
				if (stair) {
					out.set(x, y, z, b("minecraft:scaffolding[distance=0,bottom=false]"));
				} else if (elevator) {
					out.set(x, y, z, ModBlocks.ELEVATOR.get().defaultBlockState());
				} else if (apartments ? (cm == 8 && mm == 3 || cm == 4 && mm == 4 || cm == 15 && mm == 4) : (ax == 4 && az == 4)) {
					out.set(x, y, z, b("minecraft:sea_lantern"));
				} else {
					out.set(x, y, z, b("minecraft:smooth_stone"));
				}
			} else if (stair) {
				out.set(x, y, z, b("minecraft:scaffolding[distance=0,bottom=false]"));
			} else if (elevator) {
				// shaft: open space above each elevator pad
			} else if (apartments) {
				apartmentCell(fy / floorH, fy % floorH, cm, mm, alongX, x, y, z, out);
			} else if (partition && !doorway) {
				out.set(x, y, z, b("minecraft:white_concrete"));
			} else if (fy % floorH == 1) {
				BlockState f = BuildingBlocks.furniture(bar, kind, ax, az);
				if (f != null) {
					out.set(x, y, z, f);
				}
			}
		}
	}
}
