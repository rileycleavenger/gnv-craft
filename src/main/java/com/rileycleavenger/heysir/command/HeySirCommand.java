package com.rileycleavenger.heysir.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rileycleavenger.heysir.director.HeySirData;
import com.rileycleavenger.heysir.director.HeySirDirector;
import com.rileycleavenger.heysir.entity.HeySirEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only helpers for testing: /heysir status|summon|reset. */
public final class HeySirCommand {
	private HeySirCommand() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> register(event.getDispatcher()));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("heysir")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("status").executes(context -> status(context.getSource())))
			.then(Commands.literal("summon").executes(context -> summon(context.getSource())))
			.then(Commands.literal("reset").executes(context -> reset(context.getSource()))));
	}

	private static int status(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HeySirData data = HeySirDirector.getData(player);
		long now = HeySirDirector.clockTime(source.getServer());
		double health = Math.scalb(HeySirEntity.BASE_HEALTH, data.deaths());
		StringBuilder message = new StringBuilder()
			.append("HeySir: ").append(data.phase().getSerializedName())
			.append(", deaths ").append(data.deaths())
			.append(", health ").append(String.format("%.0f", health));
		if (data.phase() == HeySirData.Phase.AWAY || data.phase() == HeySirData.Phase.DEAD) {
			long remaining = Math.max(0, data.returnAt() - now);
			message.append(", back in ").append(remaining).append(" ticks (")
				.append(String.format("%.2f", remaining / (double) HeySirDirector.DAY_TICKS)).append(" days)");
		}
		source.sendSuccess(() -> Component.literal(message.toString()), false);
		return 1;
	}

	private static int summon(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HeySirDirector.discardAll(player);
		HeySirDirector.setData(player, HeySirDirector.getData(player).withPhase(HeySirData.Phase.FOLLOWING));
		if (HeySirDirector.spawn(player, HeySirEntity.Mode.FOLLOW, 8, 14, false) == null) {
			source.sendFailure(Component.literal("Couldn't find room for HeySir here; he'll show up shortly."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Hey Sir!"), false);
		return 1;
	}

	private static int reset(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HeySirDirector.discardAll(player);
		HeySirDirector.setData(player, HeySirData.DEFAULT);
		source.sendSuccess(() -> Component.literal("HeySir has forgotten you (for now)."), false);
		return 1;
	}
}
