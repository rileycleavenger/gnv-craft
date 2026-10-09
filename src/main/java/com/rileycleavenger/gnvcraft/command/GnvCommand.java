package com.rileycleavenger.gnvcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rileycleavenger.gnvcraft.drunk.DrunkSystem;
import com.rileycleavenger.gnvcraft.gameday.GameDayDirector;
import com.rileycleavenger.gnvcraft.world.GnvChunkGenerator;
import com.rileycleavenger.gnvcraft.world.map.GnvMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator helpers: /gnv tp <place>, /gnv gameday start|stop|auto|status, /gnv drunk <level>. */
public final class GnvCommand {
	private GnvCommand() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> register(event.getDispatcher()));
	}

	private static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("gnv")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("tp").then(Commands.argument("place", StringArgumentType.greedyString())
				.executes(c -> tp(c.getSource(), StringArgumentType.getString(c, "place")))))
			.then(Commands.literal("gameday")
				.then(Commands.literal("start").executes(c -> gameday(c.getSource(), "start")))
				.then(Commands.literal("stop").executes(c -> gameday(c.getSource(), "stop")))
				.then(Commands.literal("auto").executes(c -> gameday(c.getSource(), "auto")))
				.then(Commands.literal("status").executes(c -> gameday(c.getSource(), "status"))))
			.then(Commands.literal("drunk").then(Commands.argument("level", FloatArgumentType.floatArg(0.0F, 9.0F))
				.executes(c -> drunk(c.getSource(), FloatArgumentType.getFloat(c, "level"))))));
	}

	private static int tp(CommandSourceStack source, String place) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int x = 0;
		int z = 0;
		String key = place.trim().toLowerCase();
		if (!key.equals("origin") && !key.equals("13th")) {
			GnvMap.Landmark l = GnvMap.get().landmark(key);
			if (l == null) {
				source.sendFailure(Component.literal("No landmark matching '" + place + "'. Try: origin, chick-fil-a, a bar name, airport."));
				return 0;
			}
			x = l.p[0];
			z = l.p[1];
		}
		player.teleportTo(x + 0.5, GnvChunkGenerator.SURFACE_Y + 1, z + 0.5);
		source.sendSuccess(() -> Component.literal("Teleported to " + place), false);
		return 1;
	}

	private static int gameday(CommandSourceStack source, String mode) {
		switch (mode) {
			case "start" -> GameDayDirector.force(true);
			case "stop" -> GameDayDirector.force(false);
			case "auto" -> GameDayDirector.auto();
			default -> {
			}
		}
		boolean on = GameDayDirector.isGameDay(source.getServer());
		source.sendSuccess(() -> Component.literal("Game day: " + (on ? "ON" : "off")), false);
		return 1;
	}

	private static int drunk(CommandSourceStack source, float level) throws CommandSyntaxException {
		DrunkSystem.set(source.getPlayerOrException(), level);
		source.sendSuccess(() -> Component.literal("Drunk level " + level), false);
		return 1;
	}
}
