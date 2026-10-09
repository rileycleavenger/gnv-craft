package com.rileycleavenger.gnvcraft.drunk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A bar drink: finishing it adds {@code amount} to the drinker's drunk level. */
public class DrinkItem extends Item {
	private final float amount;

	public DrinkItem(Properties properties, float amount) {
		super(properties);
		this.amount = amount;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			DrunkSystem.add(player, this.amount);
		}
		return super.finishUsingItem(stack, level, entity);
	}
}
