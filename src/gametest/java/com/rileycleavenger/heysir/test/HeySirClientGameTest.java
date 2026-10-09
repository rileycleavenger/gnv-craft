package com.rileycleavenger.heysir.test;

import com.rileycleavenger.heysir.HeySirMod;
import com.rileycleavenger.heysir.director.HeySirData;
import com.rileycleavenger.heysir.director.HeySirDirector;
import com.rileycleavenger.heysir.entity.HeySirEntity;
import com.rileycleavenger.heysir.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * End-to-end check of HeySir in a real client: run with ./gradlew runClientGameTest.
 * Screenshots land in build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class HeySirClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");

			checkRecipeAndItems(server);
			checkRendering(context, server);
			checkFollowing(context, server);
			checkBikeCannotBeUsed(server);
			checkGiftcard(context, server);
			checkDeathsDoubleHealth(context, server);
			checkHealthBeyondVanillaCap(context, server);
			checkDimensionChange(context, server);
		}

		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getConnection().waitForChunksRender();
			TestServerContext server = reopened.getServer();
			log("relog: waiting for HeySir to ride back in");
			waitForHeySir(server, 400);
			assertPhase(server, HeySirData.Phase.FOLLOWING);
		}
		log("all HeySir checks passed");
	}

	private static void checkRecipeAndItems(TestServerContext server) {
		boolean hasRecipe = server.computeOnServer(s -> s.getRecipeManager()
			.byKey(ResourceKey.create(Registries.RECIPE, HeySirMod.id("mcdonalds_giftcard"))).isPresent());
		check(hasRecipe, "giftcard recipe is loaded");
	}

	private static void checkRendering(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		server.runCommand("execute as @a run heysir summon");
		waitForHeySir(server, 100);
		assertPhase(server, HeySirData.Phase.FOLLOWING);

		// Freeze him side-on in front of the camera for screenshots.
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.teleportTo(player.level(), 0.5, player.getY(), 0.5, java.util.Set.of(), 0.0F, 15.0F, true);
			HeySirEntity heySir = heySir(s);
			heySir.setNoAi(true);
			heySir.teleportTo(0.5, player.getY(), 5.5);
			face(heySir, 90.0F);
			heySir.setRiding(true);
		});
		context.waitTicks(30);
		context.takeScreenshot("heysir-riding-side");

		server.runOnServer(s -> heySir(s).setRiding(false));
		context.waitTicks(10);
		context.takeScreenshot("heysir-walking-bike-side");

		server.runOnServer(s -> face(heySir(s), 180.0F));
		context.waitTicks(10);
		context.takeScreenshot("heysir-walking-bike-front");

		server.runOnServer(s -> {
			HeySirEntity heySir = heySir(s);
			heySir.setRiding(true);
			face(heySir, 135.0F);
		});
		context.waitTicks(10);
		context.takeScreenshot("heysir-riding-three-quarter");

		context.runOnClient(mc -> mc.gui.hud.toggle());
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MCDONALDS_GIFTCARD, 3));
			player.getInventory().setItem(1, new ItemStack(ModItems.HEYSIR_SPAWN_EGG));
			face(heySir(s), 90.0F);
		});
		context.waitTicks(20);
		context.takeScreenshot("heysir-nametag-and-items");

		server.runOnServer(s -> heySir(s).setNoAi(false));
	}

	private static void checkFollowing(ClientGameTestContext context, TestServerContext server) {
		log("follow: walking away from HeySir");
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.teleportTo(player.level(), 14.5, player.getY(), 0.5, java.util.Set.of(), -90.0F, 15.0F, true);
		});
		server.waitFor(s -> heySir(s) != null && heySir(s).distanceTo(player(s)) < 5.0F, 400);

		log("follow: outrunning HeySir");
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.teleportTo(player.level(), 80.5, player.getY(), 0.5, java.util.Set.of(), -90.0F, 15.0F, true);
		});
		server.waitFor(s -> {
			HeySirEntity heySir = HeySirDirector.findHeySir(player(s));
			return heySir != null && heySir.distanceTo(player(s)) < 30.0F;
		}, 400);
	}

	private static void checkBikeCannotBeUsed(TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			HeySirEntity heySir = heySir(s);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			heySir.interact(player, InteractionHand.MAIN_HAND, heySir.position());
			check(player.getVehicle() == null, "right-clicking HeySir doesn't put the player on the bike");
			check(!player.startRiding(heySir, false, false), "player can't mount HeySir's bike");
		});
	}

	private static void checkGiftcard(ClientGameTestContext context, TestServerContext server) {
		log("giftcard: paying HeySir");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			HeySirEntity heySir = heySir(s);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MCDONALDS_GIFTCARD, 2));
			long now = HeySirDirector.clockTime(s);
			heySir.interact(player, InteractionHand.MAIN_HAND, heySir.position());
			check(player.getMainHandItem().getCount() == 1, "one giftcard was consumed");
			HeySirData data = HeySirDirector.getData(player);
			check(data.phase() == HeySirData.Phase.AWAY, "phase is AWAY after the giftcard");
			check(data.returnAt() == now + HeySirDirector.AWAY_TICKS, "returns in exactly 3 days");
			check(heySir.getMode() == HeySirEntity.Mode.LEAVE, "HeySir is riding away");
		});
		server.waitFor(s -> HeySirDirector.findHeySir(player(s)) == null, 700);
		log("giftcard: he left");

		server.runCommand("time add 71000");
		context.waitTicks(60);
		assertPhase(server, HeySirData.Phase.AWAY);
		check(server.computeOnServer(s -> HeySirDirector.findHeySir(player(s)) == null), "still away before 3 days pass");

		server.runCommand("time add 1000");
		waitForHeySir(server, 200);
		assertPhase(server, HeySirData.Phase.FOLLOWING);
		log("giftcard: back after 3 days");
		server.runCommand("gamemode creative @a");
	}

	private static void checkDeathsDoubleHealth(ClientGameTestContext context, TestServerContext server) {
		for (int expectedDeaths = 1; expectedDeaths <= 2; expectedDeaths++) {
			int deaths = expectedDeaths;
			server.runCommand("kill @e[type=heysir:heysir]");
			context.waitTicks(5);
			server.runOnServer(s -> {
				HeySirData data = HeySirDirector.getData(player(s));
				long now = HeySirDirector.clockTime(s);
				check(data.phase() == HeySirData.Phase.DEAD, "phase is DEAD after kill " + deaths);
				check(data.deaths() == deaths, "death count is " + deaths);
				check(data.returnAt() % HeySirDirector.DAY_TICKS == 0 && data.returnAt() > now && data.returnAt() - now <= HeySirDirector.DAY_TICKS,
					"returns at the start of the next day");
			});
			context.waitTicks(60);
			check(server.computeOnServer(s -> HeySirDirector.findHeySir(player(s)) == null), "gone until the next day");

			long untilNextDay = server.computeOnServer(s -> HeySirDirector.getData(player(s)).returnAt() - HeySirDirector.clockTime(s));
			server.runCommand("time add " + untilNextDay);
			waitForHeySir(server, 200);
			double expectedHealth = HeySirEntity.BASE_HEALTH * Math.pow(2, deaths);
			double maxHealth = server.computeOnServer(s -> heySir(s).getAttributeValue(Attributes.MAX_HEALTH));
			check(maxHealth == expectedHealth, "max health after " + deaths + " deaths is " + expectedHealth + " (got " + maxHealth + ")");
			log("deaths: back with " + maxHealth + " health");
		}
	}

	private static void checkHealthBeyondVanillaCap(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			HeySirDirector.setData(player, new HeySirData(HeySirData.Phase.FOLLOWING, 7, 0));
			HeySirDirector.discardAll(player);
		});
		waitForHeySir(server, 200);
		server.runOnServer(s -> {
			HeySirEntity heySir = heySir(s);
			check(heySir.getAttributeValue(Attributes.MAX_HEALTH) == 1024.0, "max health caps at 1024");
			float before = heySir.getHealth();
			heySir.hurtServer(player(s).level(), player(s).damageSources().generic(), 100.0F);
			float lost = before - heySir.getHealth();
			// 7 deaths = 2560 effective health, so 100 damage should only take 100 * 1024 / 2560 = 40.
			check(Math.abs(lost - 40.0F) < 0.5F, "damage is scaled past the cap (lost " + lost + ")");
		});
	}

	private static void checkDimensionChange(ClientGameTestContext context, TestServerContext server) {
		log("dimension: following into the Nether");
		server.runCommand("execute in minecraft:the_nether run tp @a 0.5 101 0.5");
		server.waitFor(s -> player(s).level().dimension() == Level.NETHER, 200);
		context.waitTicks(40);
		server.runCommand("execute in minecraft:the_nether run fill -12 100 -12 12 100 12 minecraft:obsidian");
		server.runCommand("execute in minecraft:the_nether run fill -12 101 -12 12 105 12 minecraft:air");
		server.runCommand("execute in minecraft:the_nether run tp @a 0.5 101 0.5");
		waitForHeySir(server, 400);
		check(server.computeOnServer(s -> heySir(s).level().dimension() == Level.NETHER), "HeySir followed into the Nether");
		context.waitTicks(20);
		server.runCommand("execute in minecraft:overworld run tp @a 0.5 -60 0.5");
		server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
		waitForHeySir(server, 400);
	}

	private static void face(HeySirEntity heySir, float yaw) {
		heySir.setYRot(yaw);
		heySir.setYHeadRot(yaw);
		heySir.setYBodyRot(yaw);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static @Nullable HeySirEntity heySir(MinecraftServer server) {
		return HeySirDirector.findHeySir(player(server));
	}

	private static void waitForHeySir(TestServerContext server, int timeout) {
		server.waitFor(s -> heySir(s) != null, timeout);
	}

	private static void assertPhase(TestServerContext server, HeySirData.Phase phase) {
		HeySirData.Phase actual = server.computeOnServer(s -> HeySirDirector.getData(player(s)).phase());
		check(actual == phase, "phase is " + phase + " (got " + actual + ")");
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
		log("ok: " + what);
	}

	private static void log(String message) {
		HeySirMod.LOGGER.info("[heysir-test] {}", message);
	}
}
