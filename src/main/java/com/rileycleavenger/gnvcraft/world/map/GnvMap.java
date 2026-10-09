package com.rileycleavenger.gnvcraft.world.map;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.rileycleavenger.gnvcraft.GnvCraftMod;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/** Gainesville baked from OpenStreetMap (see tools/osm/fetch_gnv.py). Coordinates are blocks: origin = 13th St & University Ave, +X east, +Z south. */
public final class GnvMap {
	private static final int CELL = 32;
	private static @Nullable GnvMap instance;

	public static final class Road {
		public long id;
		@SerializedName("class") public String cls;
		public int w;
		public @Nullable String name;
		public int[][] pts;
		public int minX, minZ, maxX, maxZ;
	}

	public static final class Area {
		public long id;
		public String kind;
		public int[][] poly;
		public @Nullable String name;
		public int minX, minZ, maxX, maxZ;
	}

	public static final class Building {
		public long id;
		public @Nullable String name;
		public @Nullable String kind;
		public int levels;
		public int[][] poly;
		public int minX, minZ, maxX, maxZ;

		/** Storeys: OSM levels when tagged, otherwise a guess from the building type. */
		public int storeys() {
			if (this.levels > 0) {
				return Math.min(this.levels, 20);
			}
			String k = this.kind == null ? "yes" : this.kind;
			return switch (k) {
				case "apartments", "dormitory", "residential" -> 4;
				case "university", "college", "school", "office", "commercial", "hospital", "hotel" -> 3;
				case "stadium" -> 5;
				case "house", "detached", "garage", "garages", "shed", "bar", "pub", "restaurant", "fast_food", "cafe" -> 1;
				default -> 1;
			};
		}
	}

	public static final class Landmark {
		public long id;
		public String name;
		public @Nullable String kind;
		public int[] p;
	}

	private static final class Raw {
		List<Road> roads = new ArrayList<>();
		List<Area> areas = new ArrayList<>();
		List<Building> buildings = new ArrayList<>();
		List<Landmark> landmarks = new ArrayList<>();
	}

	public final List<Road> roads;
	public final List<Area> areas;
	public final List<Building> buildings;
	public final List<Landmark> landmarks;
	private final Map<Long, List<Object>> grid = new HashMap<>();
	private final Map<Long, Building> buildingById = new HashMap<>();

	private GnvMap(Raw raw) {
		this.roads = raw.roads;
		this.areas = raw.areas;
		this.buildings = raw.buildings;
		this.landmarks = raw.landmarks;
		for (Road r : this.roads) {
			bounds(r.pts, r.w / 2 + 3, b -> { r.minX = b[0]; r.minZ = b[1]; r.maxX = b[2]; r.maxZ = b[3]; });
			this.index(r, r.minX, r.minZ, r.maxX, r.maxZ);
		}
		for (Area a : this.areas) {
			bounds(a.poly, 1, b -> { a.minX = b[0]; a.minZ = b[1]; a.maxX = b[2]; a.maxZ = b[3]; });
			this.index(a, a.minX, a.minZ, a.maxX, a.maxZ);
		}
		for (Building b : this.buildings) {
			bounds(b.poly, 1, bb -> { b.minX = bb[0]; b.minZ = bb[1]; b.maxX = bb[2]; b.maxZ = bb[3]; });
			this.index(b, b.minX, b.minZ, b.maxX, b.maxZ);
			this.buildingById.put(b.id, b);
		}
	}

	private static void bounds(int[][] pts, int pad, java.util.function.Consumer<int[]> out) {
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] p : pts) {
			minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
			minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]);
		}
		out.accept(new int[] {minX - pad, minZ - pad, maxX + pad, maxZ + pad});
	}

	private void index(Object o, int minX, int minZ, int maxX, int maxZ) {
		for (int cx = Math.floorDiv(minX, CELL); cx <= Math.floorDiv(maxX, CELL); cx++) {
			for (int cz = Math.floorDiv(minZ, CELL); cz <= Math.floorDiv(maxZ, CELL); cz++) {
				this.grid.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(o);
			}
		}
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	/** Features whose bounds may touch the given block rectangle. */
	public <T> List<T> query(Class<T> type, int minX, int minZ, int maxX, int maxZ) {
		List<T> out = new ArrayList<>();
		java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (int cx = Math.floorDiv(minX, CELL); cx <= Math.floorDiv(maxX, CELL); cx++) {
			for (int cz = Math.floorDiv(minZ, CELL); cz <= Math.floorDiv(maxZ, CELL); cz++) {
				List<Object> cell = this.grid.get(key(cx, cz));
				if (cell != null) {
					for (Object o : cell) {
						if (type.isInstance(o) && seen.add(o)) {
							out.add(type.cast(o));
						}
					}
				}
			}
		}
		return out;
	}

	public @Nullable Building building(long id) {
		return this.buildingById.get(id);
	}

	public @Nullable Landmark landmark(String nameContains) {
		String n = nameContains.toLowerCase();
		for (Landmark l : this.landmarks) {
			if (l.name != null && l.name.toLowerCase().contains(n)) {
				return l;
			}
		}
		return null;
	}

	public @Nullable Landmark landmarkKind(String kind) {
		for (Landmark l : this.landmarks) {
			if (kind.equals(l.kind)) {
				return l;
			}
		}
		return null;
	}

	/** The apron landmark closest to the given block position. */
	public @Nullable Landmark nearestKind(String kind, int x, int z) {
		Landmark best = null;
		long bestD = Long.MAX_VALUE;
		for (Landmark l : this.landmarks) {
			if (kind.equals(l.kind)) {
				long d = (long) (l.p[0] - x) * (l.p[0] - x) + (long) (l.p[1] - z) * (l.p[1] - z);
				if (d < bestD) {
					bestD = d;
					best = l;
				}
			}
		}
		return best;
	}

	public static synchronized GnvMap get() {
		if (instance == null) {
			try (InputStream in = GnvMap.class.getResourceAsStream("/data/gnvcraft/gnv_map/map.json.gz")) {
				if (in == null) {
					throw new IllegalStateException("gnv_map/map.json.gz missing; run tools/osm/fetch_gnv.py");
				}
				Raw raw = new Gson().fromJson(new InputStreamReader(new GZIPInputStream(in)), Raw.class);
				instance = new GnvMap(raw);
				GnvCraftMod.LOGGER.info("Loaded Gainesville map: {} roads, {} areas, {} buildings, {} landmarks",
					raw.roads.size(), raw.areas.size(), raw.buildings.size(), raw.landmarks.size());
			} catch (java.io.IOException e) {
				throw new IllegalStateException("Couldn't read the Gainesville map", e);
			}
		}
		return instance;
	}

	/** Even-odd point in polygon. */
	public static boolean inside(int[][] poly, int x, int z) {
		boolean in = false;
		for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			if ((poly[i][1] > z) != (poly[j][1] > z)
				&& x < (long) (poly[j][0] - poly[i][0]) * (z - poly[i][1]) / (double) (poly[j][1] - poly[i][1]) + poly[i][0]) {
				in = !in;
			}
		}
		return in;
	}

	/** Squared distance from a point to a polyline. */
	public static double distSq(int[][] pts, int x, int z) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 1 < pts.length; i++) {
			double ax = pts[i][0], az = pts[i][1], bx = pts[i + 1][0], bz = pts[i + 1][1];
			double dx = bx - ax, dz = bz - az;
			double len = dx * dx + dz * dz;
			double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len));
			double px = ax + t * dx - x, pz = az + t * dz - z;
			best = Math.min(best, px * px + pz * pz);
		}
		return best;
	}
}
