package dev.villageheart.market;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.villageheart.core.Balance;
import dev.villageheart.core.market.Reserves;
import dev.villageheart.world.VillageClaims;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /villageheart market reserves <foodDays> <items>} and {@code market status} (permission level 2): admin and simulation access (M22). */
public final class MarketCommands {
	private MarketCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(build()));
	}

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("villageheart").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("market")
						.then(Commands.literal("status").executes(MarketCommands::status))
						.then(Commands.literal("reserves")
								.executes(MarketCommands::status)
								.then(Commands.argument("foodDays", IntegerArgumentType.integer(0, Balance.MAX_FOOD_RESERVE_DAYS))
										.then(Commands.argument("items", IntegerArgumentType.integer(0, Balance.MAX_ITEM_RESERVE))
												.executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "foodDays"), IntegerArgumentType.getInteger(ctx, "items")))))));
	}

	private static int set(CommandContext<CommandSourceStack> ctx, int foodDays, int items) {
		CommandSourceStack source = ctx.getSource();
		if (VillageClaims.current(source.getServer()).isEmpty()) {
			source.sendFailure(Component.translatable("message.villageheart.hire.no_village"));
			return 0;
		}
		if (!Reserves.valid(foodDays, items)) {
			source.sendFailure(Component.translatable("message.villageheart.market.bad_reserve"));
			return 0;
		}
		Markets.setReserves(source.getServer(), new Reserves(foodDays, items));
		source.sendSuccess(() -> Component.translatable("message.villageheart.market.reserves", foodDays, items), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		var village = VillageClaims.current(source.getServer());
		if (village.isEmpty()) {
			source.sendFailure(Component.translatable("message.villageheart.hire.no_village"));
			return 0;
		}
		var state = Markets.state(source.getServer());
		long merchants = village.get().workers().stream().filter(w -> w.jobId().equals("merchant")).count();
		int sold = state.ledger().forDay(Markets.today(source.getServer().overworld())).soldToday();
		source.sendSuccess(() -> Component.translatable("message.villageheart.market.status", sold, merchants, state.reserves().foodDays(),
				state.reserves().itemReserve()), false);
		return sold;
	}
}
