package com.rileycleavenger.gnvcraft.test;

import com.rileycleavenger.gnvcraft.GnvCraftMod;
import com.rileycleavenger.gnvcraft.director.HeySirData;
import com.rileycleavenger.gnvcraft.director.HeySirDirector;
import com.rileycleavenger.gnvcraft.entity.HeySirEntity;
import com.rileycleavenger.gnvcraft.registry.ModItems;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Server-side checks of HeySir's behavior, driven by a mock player. Run with ./gradlew runGameTestServer.
 * Only part of the dev environment; not included in the mod jar.
 */
@Mod(GnvCraftMod.MOD_ID)
public class HeySirGameTests {
	private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, GnvCraftMod.MOD_ID);

	private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RECIPE =
		TEST_FUNCTIONS.register("recipe_loaded", () -> HeySirGameTests::recipeLoaded);
	private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LIFECYCLE =
		TEST_FUNCTIONS.register("lifecycle", () -> HeySirGameTests::lifecycle);

	private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DRINKING =
		TEST_FUNCTIONS.register("drinking", () -> HeySirGameTests::drinking);
	private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SCOOTER =
		TEST_FUNCTIONS.register("scooter_rental", () -> HeySirGameTests::scooterRental);
	private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MAP_DATA =
		TEST_FUNCTIONS.register("map_data", () -> HeySirGameTests::mapData);
	private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ENTITIES =
		TEST_FUNCTIONS.register("npcs_spawn", () -> HeySirGameTests::npcsSpawn);

	public HeySirGameTests(IEventBus modBus) {
		TEST_FUNCTIONS.register(modBus);
		modBus.addListener(HeySirGameTests::registerTests);
	}

	private static void registerTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(GnvCraftMod.id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
		event.registerTest(GnvCraftMod.id("recipe_loaded"), new FunctionGameTestInstance(RECIPE.getKey(), testData(environment, 40)));
		event.registerTest(GnvCraftMod.id("drinking"), new FunctionGameTestInstance(DRINKING.getKey(), testData(environment, 200)));
		event.registerTest(GnvCraftMod.id("scooter_rental"), new FunctionGameTestInstance(SCOOTER.getKey(), testData(environment, 200)));
		event.registerTest(GnvCraftMod.id("map_data"), new FunctionGameTestInstance(MAP_DATA.getKey(), testData(environment, 40)));
		event.registerTest(GnvCraftMod.id("npcs_spawn"), new FunctionGameTestInstance(ENTITIES.getKey(), testData(environment, 200)));
		event.registerTest(GnvCraftMod.id("lifecycle"), new FunctionGameTestInstance(LIFECYCLE.getKey(), testData(environment, 6000)));
	}

	private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(Holder<TestEnvironmentDefinition<?>> environment, int maxTicks) {
		return new TestData<>(environment, GnvCraftMod.id("empty"), maxTicks, 0, true);
	}

	private static void recipeLoaded(GameTestHelper helper) {
		boolean present = helper.getLevel().getServer().getRecipeManager()
			.byKey(ResourceKey.create(Registries.RECIPE, GnvCraftMod.id("mcdonalds_giftcard"))).isPresent();
		helper.assertTrue(present, "giftcard recipe is loaded");
		helper.succeed();
	}

	private static void drinking(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;
		com.rileycleavenger.gnvcraft.drunk.DrunkSystem.set(player, 0.0F);
		ItemStack beer = new ItemStack(ModItems.BEER.get());
		beer.getItem().finishUsingItem(beer, helper.getLevel(), player);
		helper.assertTrue(com.rileycleavenger.gnvcraft.drunk.DrunkSystem.get(player) == 1.0F, "a beer adds 1");
		ItemStack shot = new ItemStack(ModItems.SHOT.get());
		shot.getItem().finishUsingItem(shot, helper.getLevel(), player);
		helper.assertTrue(com.rileycleavenger.gnvcraft.drunk.DrunkSystem.get(player) == 2.5F, "a shot adds 1.5");
		com.rileycleavenger.gnvcraft.drunk.DrunkSystem.set(player, 50.0F);
		helper.assertTrue(com.rileycleavenger.gnvcraft.drunk.DrunkSystem.get(player) == com.rileycleavenger.gnvcraft.drunk.DrunkSystem.DEATH_LEVEL, "level is capped at the death level");
		helper.succeed();
	}

	private static void scooterRental(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer renter = helper.makeMockServerPlayerInLevel();
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		renter.getAbilities().instabuild = false;
		com.rileycleavenger.gnvcraft.entity.LimeScooterEntity scooter = com.rileycleavenger.gnvcraft.registry.ModEntities.LIME_SCOOTER.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		scooter.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		level.addFreshEntity(scooter);
		long now = HeySirDirector.clockTime(server);
		helper.assertFalse(scooter.isRentedBy(renter, now), "locked at first");
		renter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
		scooter.interact(renter, InteractionHand.MAIN_HAND, scooter.position());
		helper.assertTrue(renter.getMainHandItem().getCount() == 2, "one gold ingot is used");
		helper.assertTrue(scooter.isRentedBy(renter, now), "renter can ride");
		helper.assertFalse(scooter.isRentedBy(other, now), "nobody else can ride");
		helper.assertTrue(scooter.rentedUntil() == now + HeySirDirector.DAY_TICKS, "rented for exactly one in-game day");
		helper.assertFalse(scooter.isRentedBy(renter, now + HeySirDirector.DAY_TICKS), "locks again after a day");
		helper.succeed();
	}

	private static void mapData(GameTestHelper helper) {
		helper.assertTrue(com.rileycleavenger.gnvcraft.world.map.GnvRaster.covers(0, 0), "LiDAR core covers 13th & University");
		helper.assertTrue(com.rileycleavenger.gnvcraft.world.map.GnvRaster.surface(0, 0) != com.rileycleavenger.gnvcraft.world.map.GnvRaster.S_GRASS, "the intersection is paved");
		boolean standard = com.rileycleavenger.gnvcraft.world.map.GnvRaster.buildings().stream().anyMatch(b -> "The Standard at Gainesville".equals(b.name) && b.levels == 10);
		helper.assertTrue(standard, "The Standard is a 10-level building from the LiDAR");
		var house = com.rileycleavenger.gnvcraft.world.map.InteriorSpecs.all().stream().filter(sp -> sp.id.equals("house_201_nw_10th")).findFirst();
		helper.assertTrue(house.isPresent(), "201 NW 10th St spec loads");
		helper.assertTrue(com.rileycleavenger.gnvcraft.world.map.GnvRaster.covers(house.get().minX(), house.get().minZ()), "the house sits on LiDAR terrain");
		helper.succeed();
	}

	private static void npcsSpawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (var type : java.util.List.of(com.rileycleavenger.gnvcraft.registry.ModEntities.DENNIS.get(), com.rileycleavenger.gnvcraft.registry.ModEntities.FAN.get(), com.rileycleavenger.gnvcraft.registry.ModEntities.PLANE.get())) {
			helper.assertTrue(type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND) != null, type + " can be created");
		}
		helper.succeed();
	}

	private static void lifecycle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;
		Vec3 start = ground(level, helper.absoluteVec(new Vec3(1.5, 0, 1.5)));
		teleport(player, level, start);
		HeySirDirector.setData(player, new HeySirData(HeySirData.Phase.FOLLOWING, 0, 0));
		long[] clockAtPayment = new long[1];

		GameTestSequence sequence = helper.startSequence()
			.thenExecute(() -> helper.assertTrue(HeySirDirector.spawn(player, HeySirEntity.Mode.FOLLOW, 8, 14, false) != null, "HeySir spawns"))
			// Following and catching up.
			.thenExecute(() -> teleport(player, level, ground(level, start.add(14, 0, 0))))
			.thenWaitUntil(() -> helper.assertTrue(heySir(player) != null && heySir(player).distanceTo(player) < 5.0F, "HeySir follows"))
			.thenExecute(() -> teleport(player, level, ground(level, start.add(80, 0, 0))))
			.thenWaitUntil(() -> helper.assertTrue(heySir(player) != null && heySir(player).distanceTo(player) < 30.0F, "HeySir catches up"))
			// Nobody rides his bike.
			.thenExecute(() -> {
				HeySirEntity heySir = heySir(player);
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				heySir.interact(player, InteractionHand.MAIN_HAND, heySir.position());
				helper.assertTrue(player.getVehicle() == null, "right-clicking doesn't mount the bike");
				helper.assertFalse(player.startRiding(heySir, false, false), "player can't mount HeySir");
			})
			// Giftcard: he leaves and is back 3 days later.
			.thenExecute(() -> {
				HeySirEntity heySir = heySir(player);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MCDONALDS_GIFTCARD.get(), 2));
				clockAtPayment[0] = HeySirDirector.clockTime(server);
				heySir.interact(player, InteractionHand.MAIN_HAND, heySir.position());
				HeySirData data = HeySirDirector.getData(player);
				helper.assertTrue(player.getMainHandItem().getCount() == 1, "one giftcard consumed");
				helper.assertTrue(data.phase() == HeySirData.Phase.AWAY, "phase is AWAY");
				helper.assertTrue(data.returnAt() == clockAtPayment[0] + HeySirDirector.AWAY_TICKS, "returns in exactly 3 days");
				helper.assertTrue(heySir.getMode() == HeySirEntity.Mode.LEAVE, "HeySir rides away");
			})
			.thenWaitUntil(() -> helper.assertTrue(heySir(player) == null, "HeySir is gone"))
			.thenExecute(() -> command(server, "time add 71000"))
			.thenIdle(60)
			.thenExecute(() -> {
				helper.assertTrue(HeySirDirector.getData(player).phase() == HeySirData.Phase.AWAY, "still away before 3 days");
				helper.assertTrue(heySir(player) == null, "no HeySir before 3 days");
			})
			.thenExecute(() -> command(server, "time add 1000"))
			.thenWaitUntil(() -> helper.assertTrue(heySir(player) != null, "HeySir is back after 3 days"));

		// Each death doubles his health; he's back at the start of the next day.
		for (int deaths = 1; deaths <= 2; deaths++) {
			int expectedDeaths = deaths;
			double expectedHealth = HeySirEntity.BASE_HEALTH * Math.pow(2, deaths);
			sequence = sequence
				.thenExecute(() -> heySir(player).kill(level))
				.thenExecute(() -> {
					HeySirData data = HeySirDirector.getData(player);
					long now = HeySirDirector.clockTime(server);
					helper.assertTrue(data.phase() == HeySirData.Phase.DEAD, "phase is DEAD");
					helper.assertTrue(data.deaths() == expectedDeaths, "death count is " + expectedDeaths);
					helper.assertTrue(data.returnAt() % HeySirDirector.DAY_TICKS == 0 && data.returnAt() > now, "returns at the next day");
				})
				.thenIdle(60)
				.thenExecute(() -> helper.assertTrue(heySir(player) == null, "gone until the next day"))
				.thenExecute(() -> command(server, "time add " + (HeySirDirector.getData(player).returnAt() - HeySirDirector.clockTime(server))))
				.thenWaitUntil(() -> {
					HeySirEntity heySir = heySir(player);
					helper.assertTrue(heySir != null, "HeySir is back the next day");
					helper.assertTrue(heySir.getAttributeValue(Attributes.MAX_HEALTH) == expectedHealth, "max health is " + expectedHealth);
				});
		}

		sequence
			// Past the vanilla 1024 cap, damage is scaled down instead.
			.thenExecute(() -> {
				HeySirDirector.setData(player, new HeySirData(HeySirData.Phase.FOLLOWING, 7, 0));
				HeySirDirector.discardAll(player);
			})
			.thenWaitUntil(() -> helper.assertTrue(heySir(player) != null, "HeySir respawns"))
			.thenExecute(() -> {
				HeySirEntity heySir = heySir(player);
				helper.assertTrue(heySir.getAttributeValue(Attributes.MAX_HEALTH) == 1024.0, "max health caps at 1024");
				float before = heySir.getHealth();
				heySir.hurtServer(level, level.damageSources().generic(), 100.0F);
				// 7 deaths = 2560 effective health, so 100 damage only takes 100 * 1024 / 2560 = 40.
				helper.assertTrue(Math.abs(before - heySir.getHealth() - 40.0F) < 0.5F, "damage is scaled past the cap");
			})
			// He follows you into the Nether.
			.thenExecute(() -> {
				ServerLevel nether = server.getLevel(Level.NETHER);
				BlockPos center = new BlockPos(0, 100, 0);
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, 0, -12), center.offset(12, 5, 12))) {
					nether.setBlockAndUpdate(pos, pos.getY() == 100 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
				teleport(player, nether, new Vec3(0.5, 101, 0.5));
			})
			.thenWaitUntil(() -> {
				HeySirEntity heySir = heySir(player);
				helper.assertTrue(heySir != null && heySir.level().dimension() == Level.NETHER, "HeySir follows into the Nether");
			})
			.thenExecute(() -> {
				HeySirDirector.discardAll(player);
				server.getPlayerList().remove(player);
			})
			.thenSucceed();
	}

	private static @Nullable HeySirEntity heySir(ServerPlayer player) {
		return HeySirDirector.findHeySir(player);
	}

	/** Mock players don't load chunks around themselves the way real players do, so the test does it. */
	private static void loadAround(ServerLevel level, Vec3 pos) {
		int chunkX = Mth.floor(pos.x) >> 4;
		int chunkZ = Mth.floor(pos.z) >> 4;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				level.setChunkForced(chunkX + dx, chunkZ + dz, true);
			}
		}
	}

	private static Vec3 ground(ServerLevel level, Vec3 pos) {
		loadAround(level, pos);
		return new Vec3(pos.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z)), pos.z);
	}

	private static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
		loadAround(level, pos);
		player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), 0.0F, 0.0F, true);
	}

	private static void command(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}
}
