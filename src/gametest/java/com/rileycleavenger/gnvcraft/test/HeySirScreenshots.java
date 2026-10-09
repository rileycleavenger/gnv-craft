package com.rileycleavenger.gnvcraft.test;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.director.HeySirData;
import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import com.rileycleavenger.gnvcraft.registry.ModItems;
import java.io.IOException;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * With -Dgnvcraft.screenshotTest=true (./gradlew runScreenshotClient), creates a flat world, poses HeySir
 * in front of the camera, saves screenshots to run/screenshots/screenshots/, and quits.
 */
@Mod(value = GnvCraftMod.MOD_ID, dist = Dist.CLIENT)
public class HeySirScreenshots {
	private int ticksInWorld;
	private boolean worldRequested;

	public HeySirScreenshots() {
		if (Boolean.getBoolean("gnvcraft.screenshotTest")) {
			NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> this.tick(Minecraft.getInstance()));
		}
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
		switch (this.ticksInWorld) {
			case 60 -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				this.onServer(mc, player -> {
					player.teleportTo(player.level(), 0.5, player.getY(), 0.5, Set.of(), 0.0F, 15.0F, true);
					HeySirDirector.setData(player, new HeySirData(HeySirData.Phase.FOLLOWING, 0, 0));
					HeySirEntity heySir = HeySirDirector.spawn(player, HeySirEntity.Mode.FOLLOW, 4, 6, false);
					if (heySir != null) {
						heySir.setNoAi(true);
						heySir.teleportTo(0.5, player.getY(), 5.5);
						face(heySir, 90.0F);
						heySir.setRiding(true);
					}
				});
			}
			case 100 -> this.screenshot(mc, "heysir-riding-side.png");
			case 105 -> this.onServer(mc, player -> heySir(player).setRiding(false));
			case 125 -> this.screenshot(mc, "heysir-walking-bike-side.png");
			case 130 -> this.onServer(mc, player -> {
				HeySirEntity heySir = heySir(player);
				heySir.setRiding(true);
				face(heySir, 135.0F);
			});
			case 150 -> this.screenshot(mc, "heysir-riding-three-quarter.png");
			case 155 -> {
				mc.gui.hud.toggle();
				this.onServer(mc, player -> {
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MCDONALDS_GIFTCARD.get(), 3));
					player.getInventory().setItem(1, new ItemStack(ModItems.HEYSIR_SPAWN_EGG.get()));
					face(heySir(player), 90.0F);
				});
			}
			case 180 -> this.screenshot(mc, "heysir-nametag-and-items.png");
			case 200 -> {
				GnvCraftMod.LOGGER.info("[heysir-screenshots] done");
				mc.stop();
			}
			default -> {
			}
		}
	}

	private void createWorld(Minecraft mc) {
		try {
			String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "HeySir Screenshots", "");
			LevelSettings settings = new LevelSettings(
				"HeySir Screenshots", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT
			);
			mc.createWorldOpenFlows().createFreshLevel(
				folder,
				settings,
				new WorldOptions(1L, false, false),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				new TitleScreen()
			);
		} catch (IOException e) {
			GnvCraftMod.LOGGER.error("Couldn't create the screenshot world", e);
		}
	}

	private void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
		mc.getSingleplayerServer().execute(() -> action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));
	}

	private void screenshot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> GnvCraftMod.LOGGER.info("[heysir-screenshots] {}", message.getString()));
	}

	private static @Nullable HeySirEntity heySir(ServerPlayer player) {
		return HeySirDirector.findHeySir(player);
	}

	private static void face(@Nullable HeySirEntity heySir, float yaw) {
		if (heySir != null) {
			heySir.setYRot(yaw);
			heySir.setYHeadRot(yaw);
			heySir.setYBodyRot(yaw);
		}
	}
}
