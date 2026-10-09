package com.rileycleavenger.gnvcraft.test;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.director.HeySirData;
import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.drunk.DrunkSystem;
import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import com.rileycleavenger.gnvcraft.entity.LimeScooterEntity;
import com.rileycleavenger.gnvcraft.gameday.GameDayDirector;
import com.rileycleavenger.gnvcraft.npc.PlaneDirector;
import com.rileycleavenger.gnvcraft.registry.ModEntities;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import com.rileycleavenger.gnvcraft.world.map.GnvRaster;
import com.rileycleavenger.gnvcraft.world.map.InteriorSpecs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * With -Dgnvcraft.screenshotTest=true (./gradlew runScreenshotClient), creates a Gainesville world, visits a list of scenes
 * (Dennis, The Standard, The Hub, a bar, game day, drunk vision, the plane, ...), saves a screenshot of each to
 * run/screenshots/screenshots/, and quits.
 */
@Mod(value = GnvCraftMod.MOD_ID, dist = Dist.CLIENT)
public class HeySirScreenshots {
	private record Shot(String name, int settleTicks, boolean hud, Consumer<ServerPlayer> setup) {
	}

	private final List<Shot> shots = new ArrayList<>();
	private int index;
	private int ticksInWorld;
	private int nextAt = 80;
	private boolean setupDone;
	private boolean worldRequested;

	public HeySirScreenshots() {
		if (Boolean.getBoolean("gnvcraft.screenshotTest")) {
			NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> this.tick(Minecraft.getInstance()));
		}
	}

	private static int y(int x, int z) {
		return GnvRaster.standY(x, z);
	}

	private static GnvRaster.@Nullable Building named(String part) {
		for (GnvRaster.Building b : GnvRaster.buildings()) {
			if (b.name != null && b.name.toLowerCase().contains(part.toLowerCase())) {
				return b;
			}
		}
		return null;
	}

	/** A corridor cell on the given floor of an apartment building, found the same way the generator lays corridors out. */
	private static double @Nullable [] corridor(GnvRaster.Building b, int floor) {
		boolean alongX = (b.maxX - b.minX) >= (b.maxZ - b.minZ);
		for (int k = 0; k < 6; k++) {
			for (int m = 4; m < 40; m++) {
				int x = alongX ? b.minX + m : b.minX + 8 + 18 * k;
				int z = alongX ? b.minZ + 8 + 18 * k : b.minZ + m;
				if (GnvRaster.building(x, z) == b.i && GnvRaster.building(alongX ? x + 12 : x, alongX ? z : z + 12) == b.i) {
					double fy = b.baseY + floor * b.floorH + 1.05;
					return new double[] {x + 0.5, fy, z + 0.5, alongX ? x + 12.5 : x + 0.5, fy + 0.6, alongX ? z + 0.5 : z + 12.5};
				}
			}
		}
		return null;
	}

	/** Nearest open street/sidewalk cell (no building, no canopy) to the given point, for a clear camera. */
	private static int[] openSpot(int x, int z) {
		for (int r = 0; r < 40; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !GnvRaster.covers(x + dx, z + dz)) {
						continue;
					}
					int s = GnvRaster.surface(x + dx, z + dz);
					if ((s == GnvRaster.S_ASPHALT || s == GnvRaster.S_CONCRETE || s == GnvRaster.S_LANE_WHITE || s == GnvRaster.S_CENTER_YELLOW)
						&& GnvRaster.building(x + dx, z + dz) == 0 && GnvRaster.leafTop(x + dx, z + dz) == 0) {
						return new int[] {x + dx, z + dz};
					}
				}
			}
		}
		return new int[] {x, z};
	}

	private static void noHeySir(ServerPlayer p) {
		HeySirDirector.discardAll(p);
		HeySirDirector.setData(p, new HeySirData(HeySirData.Phase.AWAY, 0, Long.MAX_VALUE));
	}

	private void buildShots() {
		this.shots.add(new Shot("aerial-13th-and-university", 160, false, p -> placeLooking(p, 70, y(0, 0) + 75, 120, -40, y(0, 0), -50)));
		int[] corner = openSpot(16, 16);
		this.shots.add(new Shot("street-13th-and-university", 100, false, p -> placeLooking(p, corner[0] + 0.5, y(corner[0], corner[1]) + 0.1, corner[1] + 0.5, -30, y(0, 0) + 6, -40)));
		var home = com.rileycleavenger.gnvcraft.npc.SpecialNpcDirector.dennisHome();
		this.shots.add(new Shot("dennis", 120, false, p -> placeLooking(p, home.getX() + 5.5, home.getY() + 0.3, home.getZ() + 4.5, home.getX() + 0.5, home.getY() + 1.2, home.getZ() + 0.5)));
		this.shots.add(new Shot("heysir-street", 100, false, p -> {
			placeLooking(p, 30, y(30, 40), 40, 30, y(30, 30) + 1, 30);
			HeySirDirector.setData(p, new HeySirData(HeySirData.Phase.FOLLOWING, 0, 0));
			HeySirEntity h = HeySirDirector.spawn(p, HeySirEntity.Mode.FOLLOW, 5, 7, false);
			if (h != null) {
				h.setNoAi(true);
			}
		}));
		for (String name : new String[] {"The Standard at", "Hub Gainesville"}) {
			GnvRaster.Building b = named(name);
			if (b == null) {
				continue;
			}
			String id = name.startsWith("The Standard") ? "the-standard" : "the-hub";
			double cx = (b.minX + b.maxX) / 2.0;
			double cz = (b.minZ + b.maxZ) / 2.0;
			// cameras on the open street: 13th St for the Standard, NW 3rd Ave for the Hub
			int[] cam = name.startsWith("The Standard") ? openSpot(22, -40) : openSpot((int) cx - 20, -200);
			double ex = name.startsWith("The Standard") ? 45 : cam[0] + 0.5;
			double ez = name.startsWith("The Standard") ? 25 : cam[1] + 0.5;
			double ey = name.startsWith("The Standard") ? b.baseY + 22 : y(cam[0], cam[1]) + 1.5;
			this.shots.add(new Shot(id + "-exterior", 140, false, p -> {
				noHeySir(p);
				placeLooking(p, ex, ey, ez, cx, (b.baseY + b.topY) / 2.0, cz);
			}));
			this.shots.add(new Shot(id + "-roof", 80, false, p -> placeLooking(p, b.minX + 3, b.topY + 6, b.minZ + 3, cx, b.topY - 4, cz)));
			double[] c = corridor(b, 3);
			if (c != null) {
				this.shots.add(new Shot(id + "-corridor", 60, false, p -> placeLooking(p, c[0], c[1], c[2], c[3], c[4], c[5])));
			}
		}
		// views matching the reference photos
		this.shots.add(new Shot("hub-corner", 140, false, p -> { noHeySir(p); placeLooking(p, -14, y(-14, -196) + 3, -196, 70, y(70, -235) + 12, -240); }));
		this.shots.add(new Shot("hub-from-13th", 120, false, p -> placeLooking(p, -45, y(-45, -200) + 26, -205, 40, y(40, -240) + 12, -245)));
		this.shots.add(new Shot("hub-pool-deck", 100, false, p -> placeLooking(p, 75, y(75, -180) + 52, -182, 75, y(75, -215) + 26, -224)));
		this.shots.add(new Shot("hub-and-publix", 120, false, p -> placeLooking(p, 110, y(110, -120) + 40, -110, 60, y(60, -200) + 8, -210)));
		this.shots.add(new Shot("chick-fil-a-corner", 120, false, p -> placeLooking(p, 9, y(9, 9) + 0.2, 9, -14, y(-14, -14) + 4, -16)));
		GnvRaster.Building swamp = named("Ben Hill Griffin");
		if (swamp != null) {
			double fx = swamp.fieldX;
			double fz = swamp.fieldZ;
			this.shots.add(new Shot("stadium-aerial", 220, false, p -> placeLooking(p, fx - 150, swamp.baseY + 110, fz + 170, fx + 10, swamp.baseY + 10, fz - 10)));
			this.shots.add(new Shot("stadium-from-press-box", 120, false, p -> placeLooking(p, fx - 75, swamp.baseY + 42, fz, fx + 20, swamp.baseY, fz)));
			this.shots.add(new Shot("stadium-field", 100, false, p -> placeLooking(p, fx, swamp.baseY + 2, fz + 40, fx, swamp.baseY + 8, fz - 60)));
		}
		// landmarks across the V1 area, each from the nearest open street
		String[][] landmarks = {{"stadium", "Ben Hill Griffin"}, {"century-tower", "Century Tower"}, {"library-west", "Library West"},
			{"reitz-union", "Reitz Union"}, {"courthouse", "Alachua County Courthouse"}, {"shands", "Shands Hospital"},
			{"airport-terminal", "Gainesville Regional Airport"}};
		for (String[] lm : landmarks) {
			GnvRaster.Building b = named(lm[1]);
			if (b == null) {
				continue;
			}
			double cx = (b.minX + b.maxX) / 2.0;
			double cz = (b.minZ + b.maxZ) / 2.0;
			int span = Math.max(b.maxX - b.minX, b.maxZ - b.minZ);
			// elevated three-quarter view from the south-east, above the tree canopy
			double camX = cx + span * 0.55 + 25;
			double camZ = cz + span * 0.55 + 25;
			double camY = b.topY + 12 + span * 0.15;
			this.shots.add(new Shot(lm[0], 160, false, p -> {
				noHeySir(p);
				placeLooking(p, camX, camY, camZ, cx, (b.baseY * 2 + b.topY) / 3.0, cz);
			}));
		}
		for (InteriorSpecs.Spec spec : InteriorSpecs.all()) {
			int gy = spec.groundY() + 1;
			double cz = spec.anchor[1] + 7.5;
			this.shots.add(new Shot("house-201-nw-10th-exterior", 120, false, p -> placeLooking(p, spec.anchor[0] - 6, gy + 1.5, cz + 6, spec.anchor[0] + 12, gy + 2, cz)));
			this.shots.add(new Shot("house-201-nw-10th-sunroom", 60, false, p -> placeLooking(p, spec.anchor[0] + 7 + 1.5, gy + 0.05, cz - 5, spec.anchor[0] + 7 + 6, gy + 1.2, cz - 1)));
			this.shots.add(new Shot("house-201-nw-10th-living", 60, false, p -> placeLooking(p, spec.anchor[0] + 7 + 8.5, gy + 0.05, cz - 0.5, spec.anchor[0] + 7 + 1, gy + 1.4, cz - 1)));
		}
		GnvRaster.Building bar = null;
		for (GnvRaster.Building b : GnvRaster.buildings()) {
			if (("bar".equals(b.kind) || "pub".equals(b.kind) || "nightclub".equals(b.kind)) && b.door != null) {
				bar = b;
				break;
			}
		}
		if (bar != null) {
			GnvRaster.Building barFinal = bar;
			this.shots.add(new Shot("bar-interior", 100, true, p -> {
				placeLooking(p, barFinal.minX + 2.5, barFinal.baseY + 1.05, barFinal.minZ + 2.5, barFinal.maxX, barFinal.baseY + 1.5, barFinal.maxZ);
				DrunkSystem.set(p, 0.0F);
			}));
		}
		this.shots.add(new Shot("drunk-level-4", 60, true, p -> {
			placeLooking(p, corner[0] + 0.5, y(corner[0], corner[1]) + 0.1, corner[1] + 0.5, -30, y(0, 0) + 6, -40);
			DrunkSystem.set(p, 4.0F);
		}));
		this.shots.add(new Shot("drunk-level-8", 60, true, p -> DrunkSystem.set(p, 7.9F)));
		this.shots.add(new Shot("sober", 10, false, p -> DrunkSystem.set(p, 0.0F)));
		this.shots.add(new Shot("scooter", 100, false, p -> {
			int[] spot = openSpot(home.getX() - 6, home.getZ() - 2);
			int sx = spot[0];
			int sz = spot[1];
			placeLooking(p, sx + 4.5, y(sx, sz) + 0.6, sz + 3.5, sx + 0.5, y(sx, sz) + 0.3, sz + 0.5);
			LimeScooterEntity sc = ModEntities.LIME_SCOOTER.get().create(p.level(), EntitySpawnReason.COMMAND);
			sc.snapTo(sx + 0.5, y(sx, sz), sz + 0.5, 200.0F, 0.0F);
			p.level().addFreshEntity(sc);
		}));
		this.shots.add(new Shot("game-day", 260, false, p -> {
			GameDayDirector.force(true);
			int[] st = openSpot(40, 6);
			placeLooking(p, st[0] + 0.5, y(st[0], st[1]) + 1.5, st[1] + 0.5, -40, y(-40, 0) + 1, -2);
		}));
		var park = PlaneDirector.parking();
		this.shots.add(new Shot("airport-plane", 220, false, p -> {
			GameDayDirector.force(false);
			placeLooking(p, park.getX() + 28, park.getY() + 18, park.getZ() + 20, park.getX(), park.getY() + 1, park.getZ());
		}));
	}

	private void tick(Minecraft mc) {
		if (mc.player == null || mc.getSingleplayerServer() == null) {
			if (!this.worldRequested && mc.gui.screen() instanceof TitleScreen) {
				this.worldRequested = true;
				this.createWorld(mc);
			}
			return;
		}
		this.ticksInWorld++;
		if (this.ticksInWorld == 40) {
			this.buildShots();
			this.nextAt = 60;
			mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().setInvulnerable(true));
		}
		if (this.shots.isEmpty() || this.ticksInWorld < this.nextAt) {
			return;
		}
		if (this.index >= this.shots.size()) {
			GnvCraftMod.LOGGER.info("[screenshots] done: {} scenes", this.shots.size());
			mc.stop();
			return;
		}
		Shot shot = this.shots.get(this.index);
		if (!this.setupDone) {
			if (mc.gui.hud.isHidden() == shot.hud()) {
				mc.gui.hud.toggle();
			}
			this.onServer(mc, shot.setup());
			this.setupDone = true;
			this.nextAt = this.ticksInWorld + shot.settleTicks();
		} else {
			this.screenshot(mc, shot.name() + ".png");
			this.index++;
			this.setupDone = false;
			this.nextAt = this.ticksInWorld + 5;
		}
	}

	private static void placeLooking(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x;
		double dz = tz - z;
		float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(ty - (y + 1.62), Math.hypot(dx, dz))));
		p.setNoGravity(true);
		p.getAbilities().flying = true;
		p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, true);
	}

	private void createWorld(Minecraft mc) {
		try {
			String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "GNV Screenshots", "");
			LevelSettings settings = new LevelSettings(
				"GNV Screenshots", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT
			);
			ResourceKey<WorldPreset> gainesville = ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(GnvCraftMod.MOD_ID, "gainesville"));
			mc.createWorldOpenFlows().createFreshLevel(
				folder,
				settings,
				new WorldOptions(1L, false, false),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(gainesville).value().createWorldDimensions(),
				new TitleScreen()
			);
		} catch (IOException e) {
			GnvCraftMod.LOGGER.error("Couldn't create the screenshot world", e);
		}
	}

	private void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
		mc.getSingleplayerServer().execute(() -> action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));
	}

	private void screenshot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> GnvCraftMod.LOGGER.info("[screenshots] {}", message.getString()));
	}
}
