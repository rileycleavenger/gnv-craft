package com.rileycleavenger.gnvcraft.world.map;

import com.google.gson.Gson;
import com.rileycleavenger.gnvcraft.GnvCraftMod;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/**
 * The LiDAR-baked part of Gainesville (tools/lidar/bake_core.py): per block column the real ground height, surface,
 * building and roof height, and tree canopy. Regions are 256x256 and loaded lazily.
 */
public final class GnvRaster {
	public static final int REGION = 256;

	public static final int S_GRASS = 0, S_ASPHALT = 1, S_CONCRETE = 2, S_PARKING = 3, S_WATER = 4, S_SAND = 5, S_LANE_WHITE = 6,
		S_CENTER_YELLOW = 7, S_CROSSWALK = 8, S_TURF = 9, S_BRICK = 10, S_PATH_DIRT = 11, S_FLOOR = 12, S_EDGE_WHITE = 13,
		S_TRACK = 14, S_GRAVEL = 15;

	public static final class Building {
		public int i;
		public @Nullable String name;
		public @Nullable String kind;
		public int levels;
		public int baseY;
		public int floorH;
		public int topY;
		public int minX, minZ, maxX, maxZ;
		public @Nullable String addr;
		public @Nullable Door door;
		public @Nullable Facade facade;
		public int @Nullable [] stair;
		public int @Nullable [] elevator;
	}

	/** Facade read off street photos: base material for the lowest floors, upper material, optional vertical bands. */
	public static final class Facade {
		public String base;
		public int baseFloors;
		public String upper;
		public @Nullable String band;
		public int bandEvery;
		public @Nullable String plinth;
		public @Nullable String rail;
	}

	public static final class Door {
		public int x;
		public int z;
		public String facing = "south";
	}

	private static final class Table {
		int x0, z0, size, region;
		List<Building> buildings;
		List<String> roofPalette = List.of();
	}

	static final class Region {
		final short[] ground = new short[REGION * REGION];
		final byte[] surface = new byte[REGION * REGION];
		final char[] bldg = new char[REGION * REGION];
		final short[] roof = new short[REGION * REGION];
		final short[] leafTop = new short[REGION * REGION];
		final short[] leafBot = new short[REGION * REGION];
		final byte[] flags = new byte[REGION * REGION];
		final byte[] color = new byte[REGION * REGION];
	}

	private static final Region MISSING = new Region();
	private static final Map<Long, Region> REGIONS = new ConcurrentHashMap<>();
	private static @Nullable Table table;
	private static int minX, minZ, maxX, maxZ;

	private GnvRaster() {
	}

	private static synchronized Table table() {
		if (table == null) {
			try (InputStream in = GnvRaster.class.getResourceAsStream("/data/gnvcraft/gnv_map/raster/buildings.json")) {
				if (in == null) {
					table = new Table();
					table.buildings = List.of();
					table.size = 0;
				} else {
					table = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Table.class);
					GnvCraftMod.LOGGER.info("Loaded LiDAR core: {} buildings, {}x{} blocks", table.buildings.size(), table.size, table.size);
				}
			} catch (Exception e) {
				throw new IllegalStateException("Couldn't read the LiDAR building table", e);
			}
			minX = table.x0;
			minZ = table.z0;
			maxX = table.x0 + table.size - 1;
			maxZ = table.z0 + table.size - 1;
		}
		return table;
	}

	/** Whether the block column is inside the LiDAR core. */
	public static boolean covers(int x, int z) {
		Table t = table();
		return t.size > 0 && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	/** Whether the whole 16x16 chunk is inside the LiDAR core. */
	public static boolean coversChunk(int x0, int z0) {
		return covers(x0, z0) && covers(x0 + 15, z0 + 15);
	}

	private static Region region(int x, int z) {
		int rx = Math.floorDiv(x, REGION);
		int rz = Math.floorDiv(z, REGION);
		long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
		return REGIONS.computeIfAbsent(key, k -> load(rx, rz));
	}

	private static Region load(int rx, int rz) {
		String path = "/data/gnvcraft/gnv_map/raster/r." + rx + "." + rz + ".bin.gz";
		try (InputStream raw = GnvRaster.class.getResourceAsStream(path)) {
			if (raw == null) {
				return MISSING;
			}
			byte[] bytes = new DataInputStream(new GZIPInputStream(raw)).readAllBytes();
			ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			Region r = new Region();
			int n = REGION * REGION;
			for (int i = 0; i < n; i++) r.ground[i] = buf.getShort();
			buf.get(r.surface);
			for (int i = 0; i < n; i++) r.bldg[i] = buf.getChar();
			for (int i = 0; i < n; i++) r.roof[i] = buf.getShort();
			for (int i = 0; i < n; i++) r.leafTop[i] = buf.getShort();
			for (int i = 0; i < n; i++) r.leafBot[i] = buf.getShort();
			buf.get(r.flags);
			buf.get(r.color);
			return r;
		} catch (Exception e) {
			GnvCraftMod.LOGGER.error("Couldn't read LiDAR region {}", path, e);
			return MISSING;
		}
	}

	private static int idx(int x, int z) {
		return Math.floorMod(z, REGION) * REGION + Math.floorMod(x, REGION);
	}

	public static int ground(int x, int z) {
		return region(x, z).ground[idx(x, z)];
	}

	public static int surface(int x, int z) {
		return region(x, z).surface[idx(x, z)] & 0xFF;
	}

	/** Building index + 1, 0 = none. */
	public static int building(int x, int z) {
		return region(x, z).bldg[idx(x, z)];
	}

	public static int roof(int x, int z) {
		return region(x, z).roof[idx(x, z)];
	}

	public static int leafTop(int x, int z) {
		return region(x, z).leafTop[idx(x, z)];
	}

	public static int leafBot(int x, int z) {
		return region(x, z).leafBot[idx(x, z)];
	}

	public static boolean trunk(int x, int z) {
		return (region(x, z).flags[idx(x, z)] & 1) != 0;
	}

	/** Pool water seen in the aerial photo (on a roof or the ground). */
	public static boolean pool(int x, int z) {
		return (region(x, z).flags[idx(x, z)] & 2) != 0;
	}

	/** Roof block id matching the aerial photo's colour here, or null. */
	public static @Nullable String roofColor(int x, int z) {
		int c = region(x, z).color[idx(x, z)] & 0xFF;
		List<String> pal = table().roofPalette;
		return c >= 1 && c <= pal.size() ? pal.get(c - 1) : null;
	}

	public static @Nullable Building info(int index) {
		List<Building> list = table().buildings;
		return index >= 1 && index <= list.size() ? list.get(index - 1) : null;
	}

	public static List<Building> buildings() {
		return table().buildings;
	}

	/** Y a player or NPC should stand at for this column (top ground block + 1), anywhere in the world. */
	public static int standY(int x, int z) {
		if (covers(x, z)) {
			int b = building(x, z);
			return ground(x, z) + 1 + (b > 0 ? 0 : 0);
		}
		return com.rileycleavenger.gnvcraft.world.GnvChunkGenerator.SURFACE_Y + 1;
	}
}
