package com.rileycleavenger.gnvcraft.world.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hand-authored buildings and interiors (data/gnvcraft/gnv_interiors/*.json, written by tools/interiors/build_interiors.py).
 * A spec is a palette plus a list of box fills relative to an anchor: [x0,y0,z0,x1,y1,z1,paletteIndex]; y = 0 is the ground surface block.
 * Later fills overwrite earlier ones. Specs can hide the OSM building they replace.
 */
public final class InteriorSpecs {
	public static final class Spec {
		public String id;
		public String name;
		public String address;
		public String note;
		public int[] anchor;
		public int[] size;
		public long[] hide_osm = new long[0];
		/** Overlay specs (street furniture, plazas) are stamped on top of the generated city instead of replacing it. */
		public boolean overlay;
		public String[] palette;
		public int[][] ops;
		transient BlockState[] states;

		/** Y of the spec's ground layer: the measured ground at its centre inside the LiDAR core, else the flat world. */
		public int groundY() {
			int cx = this.anchor[0] + this.size[0] / 2;
			int cz = this.anchor[1] + this.size[2] / 2;
			return GnvRaster.covers(cx, cz) ? GnvRaster.ground(cx, cz) : GnvChunkGenerator.SURFACE_Y;
		}

		public int minX() {
			return this.anchor[0];
		}

		public int minZ() {
			return this.anchor[1];
		}

		public int maxX() {
			return this.anchor[0] + this.size[0] - 1;
		}

		public int maxZ() {
			return this.anchor[1] + this.size[2] - 1;
		}
	}

	private static List<Spec> specs;
	private static Set<Long> hidden;

	private InteriorSpecs() {
	}

	public static synchronized List<Spec> all() {
		if (specs == null) {
			specs = new ArrayList<>();
			hidden = new HashSet<>();
			Gson gson = new Gson();
			try (InputStream idx = InteriorSpecs.class.getResourceAsStream("/data/gnvcraft/gnv_interiors/index.json")) {
				if (idx != null) {
					JsonObject index = gson.fromJson(new InputStreamReader(idx, StandardCharsets.UTF_8), JsonObject.class);
					for (var e : index.getAsJsonArray("specs")) {
						try (InputStream in = InteriorSpecs.class.getResourceAsStream("/data/gnvcraft/gnv_interiors/" + e.getAsString() + ".json")) {
							Spec spec = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Spec.class);
							spec.states = new BlockState[spec.palette.length];
							for (int i = 0; i < spec.palette.length; i++) {
								spec.states[i] = parse(spec.palette[i]);
							}
							specs.add(spec);
							for (long id : spec.hide_osm) {
								hidden.add(id);
							}
						}
					}
				}
			} catch (Exception ex) {
				GnvCraftMod.LOGGER.error("Couldn't load interior specs", ex);
			}
			GnvCraftMod.LOGGER.info("Loaded {} authored interiors", specs.size());
		}
		return specs;
	}

	private static BlockState parse(String s) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
		} catch (Exception ex) {
			GnvCraftMod.LOGGER.warn("Bad block '{}' in interior spec", s);
			return Blocks.AIR.defaultBlockState();
		}
	}

	public static boolean isHidden(long osmId) {
		all();
		return hidden.contains(osmId);
	}

	public static BlockState state(Spec spec, int index) {
		return spec.states[index];
	}

	/** Calls {@code out(x, y, z, state)} for every authored block that falls in the chunk starting at (x0, z0). */
	public interface Sink {
		void set(int x, int y, int z, BlockState state);
	}

	public static void stamp(int x0, int z0, Sink out) {
		for (Spec spec : all()) {
			int baseY = spec.groundY();
			if (spec.maxX() < x0 || spec.minX() > x0 + 15 || spec.maxZ() < z0 || spec.minZ() > z0 + 15) {
				continue;
			}
			for (int[] op : spec.ops) {
				int ax = spec.anchor[0];
				int az = spec.anchor[1];
				int fx0 = Math.max(ax + op[0], x0);
				int fx1 = Math.min(ax + op[3], x0 + 15);
				int fz0 = Math.max(az + op[2], z0);
				int fz1 = Math.min(az + op[5], z0 + 15);
				if (fx0 > fx1 || fz0 > fz1) {
					continue;
				}
				BlockState state = spec.states[op[6]];
				for (int x = fx0; x <= fx1; x++) {
					for (int z = fz0; z <= fz1; z++) {
						for (int y = op[1]; y <= op[4]; y++) {
							out.set(x, baseY + y, z, state);
						}
					}
				}
			}
		}
	}
}
