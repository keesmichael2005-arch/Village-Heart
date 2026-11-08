package dev.villageheart.build;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.PlacementMode;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.WallLines;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.blueprint.SiteRules.SiteProblem;
import dev.villageheart.world.VillageClaims;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /villageheart build start <blueprint> <x y z> <rotation>}, {@code advance <id> <n|all>}, {@code cancel <id>} and {@code list}
 * (operators, level 2). {@code start} runs the same site rules as the Blueprint item, as the Owner. {@code advance} is the instant builder of
 * tests and simulations: it runs up to n steps of the plan through the same placement code a builder villager uses.
 */
public final class BuildCommands {
	private BuildCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(build()));
	}

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("villageheart").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("build")
						.then(Commands.literal("start").then(Commands.argument("blueprint", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Blueprints.all().stream().map(Blueprint::id), b))
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
										.executes(ctx -> start(ctx, Rotation4.NONE))
										.then(Commands.argument("rotation", StringArgumentType.word())
												.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
														Arrays.stream(Rotation4.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b))
												.executes(ctx -> start(ctx, rotation(StringArgumentType.getString(ctx, "rotation"))))))))
						.then(Commands.literal("line").then(Commands.argument("blueprint", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
										Blueprints.all().stream().filter(bp -> bp.mode() == PlacementMode.LINE).map(Blueprint::id), b))
								.then(Commands.argument("from", BlockPosArgument.blockPos())
										.then(Commands.argument("to", BlockPosArgument.blockPos()).executes(BuildCommands::line)))))
						.then(Commands.literal("advance").then(Commands.argument("id", UuidArgument.uuid())
								.then(Commands.literal("all").executes(ctx -> advance(ctx, Integer.MAX_VALUE)))
								.then(Commands.argument("steps", IntegerArgumentType.integer(1)).executes(ctx -> advance(ctx, IntegerArgumentType.getInteger(ctx, "steps"))))))
						.then(Commands.literal("cancel").then(Commands.argument("id", UuidArgument.uuid()).executes(BuildCommands::cancel)))
						.then(Commands.literal("list").executes(BuildCommands::list)));
	}

	private static Rotation4 rotation(String s) {
		for (Rotation4 r : Rotation4.values()) if (r.name().toLowerCase(Locale.ROOT).equals(s)) return r;
		return Rotation4.NONE;
	}

	private static int start(CommandContext<CommandSourceStack> ctx, Rotation4 rotation) {
		CommandSourceStack source = ctx.getSource();
		Optional<Blueprint> bp = Blueprints.byId(StringArgumentType.getString(ctx, "blueprint"));
		if (bp.isEmpty()) {
			source.sendFailure(Component.literal("Unknown blueprint"));
			return 0;
		}
		Optional<VillageState> village = VillageClaims.current(source.getServer());
		if (village.isEmpty()) {
			source.sendFailure(Component.literal("No village"));
			return 0;
		}
		if (bp.get().mode() == PlacementMode.LINE) {
			source.sendFailure(Component.literal(bp.get().name() + " is a line blueprint: use /villageheart build line"));
			return 0;
		}
		BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
		var level = source.getLevel();
		SiteProblem problem = SiteValidation.check(level, village.get(), bp.get(),
				new dev.villageheart.core.blueprint.Placement(new dev.villageheart.core.room.Pos(pos.getX(), pos.getY(), pos.getZ()), rotation), Rank.OWNER);
		if (problem != null) {
			source.sendFailure(Component.literal(problem.english()));
			return 0;
		}
		BuildJob job = BuildJobs.start(level, bp.get(), pos, rotation, village.get().owner());
		source.sendSuccess(() -> Component.literal("Build " + job.id() + " started: " + bp.get().name()), true);
		return 1;
	}

	/** {@code build line <blueprint> <from> <to>}: the two clicks of a wall or gate, as the Owner. */
	private static int line(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Optional<Blueprint> unit = Blueprints.byId(StringArgumentType.getString(ctx, "blueprint"));
		if (unit.isEmpty() || unit.get().mode() != PlacementMode.LINE) {
			source.sendFailure(Component.literal("Not a line blueprint"));
			return 0;
		}
		Optional<VillageState> village = VillageClaims.current(source.getServer());
		if (village.isEmpty()) {
			source.sendFailure(Component.literal("No village"));
			return 0;
		}
		BlockPos from = BlockPosArgument.getBlockPos(ctx, "from"), to = BlockPosArgument.getBlockPos(ctx, "to");
		WallLines.Result r = WallLines.of(new Pos(from.getX(), from.getY(), from.getZ()), new Pos(to.getX(), to.getY(), to.getZ()),
				Blueprints.minLength(unit.get()), Balance.MAX_WALL_LENGTH);
		if (!r.ok()) {
			source.sendFailure(Component.literal(r.problem().english()));
			return 0;
		}
		Blueprint bp = Blueprints.resolve(unit.get().id(), new BlueprintParams(r.line().length())).orElseThrow();
		var level = source.getLevel();
		SiteProblem problem = SiteValidation.check(level, village.get(), bp, new Placement(r.line().ground(), r.line().rotation()), Rank.OWNER);
		if (problem != null) {
			source.sendFailure(Component.literal(problem.english()));
			return 0;
		}
		BuildJob job = BuildJobs.start(level, bp, from, r.line().rotation(), new BlueprintParams(r.line().length()), village.get().owner());
		source.sendSuccess(() -> Component.literal("Build " + job.id() + " started: " + bp.name() + " of " + r.line().length()), true);
		return 1;
	}

	private static int advance(CommandContext<CommandSourceStack> ctx, int n) {
		UUID id = UuidArgument.getUuid(ctx, "id");
		BuildJobs.Advanced a = BuildJobs.advance(ctx.getSource().getServer(), id, n);
		ctx.getSource().sendSuccess(() -> Component.literal("Advanced " + a.steps() + " steps" + (a.finished() ? ", finished" : "")
				+ (a.stoppedBecause().isEmpty() ? "" : ", stopped: " + a.stoppedBecause())), false);
		return a.steps();
	}

	private static int cancel(CommandContext<CommandSourceStack> ctx) {
		boolean ok = BuildJobs.cancel(ctx.getSource().getServer(), UuidArgument.getUuid(ctx, "id"));
		if (!ok) ctx.getSource().sendFailure(Component.literal("No such build"));
		else ctx.getSource().sendSuccess(() -> Component.literal("Build cancelled"), true);
		return ok ? 1 : 0;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		var server = ctx.getSource().getServer();
		var jobs = BuildJobs.list(server);
		if (jobs.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No builds"), false);
			return 0;
		}
		for (BuildJob j : jobs) {
			int total = BuildJobs.steps(server, j).size();
			ctx.getSource().sendSuccess(() -> Component.literal(j.id() + " " + j.blueprintId() + " at " + j.ground().x() + " " + j.ground().y() + " "
					+ j.ground().z() + " " + j.rotation().name().toLowerCase(Locale.ROOT) + " step " + j.cursor() + "/" + total + " ("
					+ BuildPlan.percent(BuildJobs.steps(server, j), j.cursor()) + "%)"), false);
		}
		return jobs.size();
	}
}
