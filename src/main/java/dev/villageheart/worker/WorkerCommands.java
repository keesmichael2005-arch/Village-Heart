package dev.villageheart.worker;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.world.VillageClaims;
import java.util.Arrays;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * {@code /villageheart hire <entity> <job> [building x y z]}, {@code fire <entity>} and {@code workers} (permission level 2). Thin: they
 * call the same {@link Workers} API as the Heart screen will in M8; rank checks belong to the screen's packets.
 */
public final class WorkerCommands {
	private WorkerCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(build()));
	}

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("villageheart").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("hire")
						.then(Commands.argument("entity", EntityArgument.entity())
								.then(Commands.argument("job", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(JobType.values()).map(JobType::id), b))
										.executes(ctx -> hire(ctx, Optional.empty()))
										.then(Commands.argument("building", BlockPosArgument.blockPos())
												.executes(ctx -> hire(ctx, Optional.of(BlockPosArgument.getBlockPos(ctx, "building"))))))))
				.then(Commands.literal("fire")
						.then(Commands.argument("entity", EntityArgument.entity()).executes(WorkerCommands::fire)))
				.then(Commands.literal("workers").executes(WorkerCommands::list));
	}

	private static int hire(CommandContext<CommandSourceStack> ctx, Optional<BlockPos> building) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		Entity entity = EntityArgument.getEntity(ctx, "entity");
		String jobId = StringArgumentType.getString(ctx, "job");
		Optional<JobType> job = JobType.byId(jobId);
		if (job.isEmpty()) {
			source.sendFailure(Component.translatable("message.villageheart.hire.unknown_job", jobId));
			return 0;
		}
		if (!(entity.level() instanceof ServerLevel level)) return 0;
		Optional<HeartLocation> preferred = building.map(p -> VillageClaims.locate(source.getLevel(), p));
		HireResult result = Workers.hire(level, entity, job.get(), preferred);
		if (result instanceof HireResult.Refused r) {
			source.sendFailure(Component.translatable(r.reason().langKey()));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("message.villageheart.hire.hired", job.get().displayName()), true);
		return 1;
	}

	private static int fire(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		Entity entity = EntityArgument.getEntity(ctx, "entity");
		if (entity instanceof Villager v && v.level() instanceof ServerLevel level && Workers.fire(level, v)) {
			source.sendSuccess(() -> Component.translatable("message.villageheart.hire.fired"), true);
			return 1;
		}
		source.sendFailure(Component.translatable("message.villageheart.hire.not_a_worker"));
		return 0;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Optional<VillageState> village = VillageClaims.current(source.getServer());
		if (village.isEmpty()) {
			source.sendFailure(Component.translatable("message.villageheart.hire.no_village"));
			return 0;
		}
		var workers = village.get().workers();
		if (workers.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("message.villageheart.hire.workers_none"), false);
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("message.villageheart.hire.workers_header", workers.size()), false);
		for (var w : workers) {
			source.sendSuccess(() -> Component.translatable("message.villageheart.hire.worker_line", w.jobId(), w.villager().toString()), false);
		}
		return workers.size();
	}
}
