package dev.villageheart.rank;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.villageheart.core.KnownPlayer;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.Rejection;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /villageheart owner <player>}, {@code rank <player> <steward|resident|visitor>} and {@code members} (operators, level 2).
 * The target is a name resolved against the online players and the village's known players (never a profile lookup, which could
 * block or record a stranger). Everything goes through {@link Members}, so the rules are the Heart screen's.
 */
public final class RankCommands {
	private RankCommands() {}

	/** The actor of a command is no village member: used as the "actor" of a transfer so nobody is skipped in the notices. */
	private static final UUID CONSOLE = new UUID(0, 0);

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(build()));
	}

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("villageheart").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("owner").then(Commands.argument("player", StringArgumentType.word())
						.suggests(RankCommands::suggestPlayers).executes(RankCommands::owner)))
				.then(Commands.literal("rank").then(Commands.argument("player", StringArgumentType.word())
						.suggests(RankCommands::suggestPlayers)
						.then(Commands.argument("rank", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("steward", "resident", "visitor"), b))
								.executes(RankCommands::rank))))
				.then(Commands.literal("members").executes(RankCommands::members));
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
			CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
		MinecraftServer server = ctx.getSource().getServer();
		List<String> names = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) names.add(p.getGameProfile().name());
		VillageClaims.current(server).ifPresent(v -> {
			names.add(v.ownerName());
			for (KnownPlayer k : v.known()) if (!k.name().isEmpty()) names.add(k.name());
		});
		return SharedSuggestionProvider.suggest(names, b);
	}

	/** Online players first (case-insensitive), then the owner, then the most recently seen known player of that name. */
	static Optional<UUID> resolve(MinecraftServer server, VillageState state, String name) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getGameProfile().name().equalsIgnoreCase(name)) return Optional.of(p.getUUID());
		}
		if (state.ownerName().equalsIgnoreCase(name)) return Optional.of(state.owner());
		return state.known().stream().filter(k -> k.name().equalsIgnoreCase(name))
				.max(Comparator.comparingLong(KnownPlayer::lastSeen)).map(KnownPlayer::id);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendFailure(message);
		return 0;
	}

	private static int owner(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return fail(ctx, Component.translatable("message.villageheart.rank.cmd.no_village"));
		String name = StringArgumentType.getString(ctx, "player");
		Optional<UUID> target = resolve(server, village.get(), name);
		if (target.isEmpty()) return fail(ctx, Component.translatable("message.villageheart.rank.cmd.unknown", name));
		String actor = ctx.getSource().getTextName();
		Members.Outcome o = Members.transfer(server, actor, CONSOLE, target.get(), Optional.empty(), true);
		if (o.rejected().isPresent()) return fail(ctx, Component.translatable(o.rejected().get().langKey()));
		String oldOwner = village.get().ownerName();
		ctx.getSource().sendSuccess(() -> Component.translatable("message.villageheart.rank.cmd.owner", o.targetName(), oldOwner), true);
		return 1;
	}

	private static int rank(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return fail(ctx, Component.translatable("message.villageheart.rank.cmd.no_village"));
		String name = StringArgumentType.getString(ctx, "player");
		String rankName = StringArgumentType.getString(ctx, "rank").toLowerCase(Locale.ROOT);
		Rank rank = switch (rankName) {
			case "steward" -> Rank.STEWARD;
			case "resident" -> Rank.RESIDENT;
			case "visitor" -> Rank.VISITOR;
			default -> null;
		};
		if (rank == null) return fail(ctx, Component.translatable(Rejection.BAD_RANK.langKey()));
		Optional<UUID> target = resolve(server, village.get(), name);
		if (target.isEmpty()) return fail(ctx, Component.translatable("message.villageheart.rank.cmd.unknown", name));
		Members.Outcome o = Members.setRank(server, ctx.getSource().getTextName(), target.get(), rank);
		if (o.rejected().isPresent()) return fail(ctx, Component.translatable(o.rejected().get().langKey()));
		String key = o.changed() ? "message.villageheart.rank.set" : "message.villageheart.rank.unchanged";
		ctx.getSource().sendSuccess(() -> Component.translatable(key, o.targetName(), Members.rankLabel(o.newRank())), true);
		return 1;
	}

	private static int members(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return fail(ctx, Component.translatable("message.villageheart.rank.cmd.no_village"));
		VillageState v = village.get();
		ctx.getSource().sendSuccess(() -> Component.translatable("message.villageheart.rank.cmd.roster_header"), false);
		List<UUID> ids = new ArrayList<>();
		ids.add(v.owner());
		v.known().stream().sorted(Comparator.comparing((KnownPlayer k) -> v.rankOf(k.id()).ordinal())
				.thenComparing(KnownPlayer::name, String.CASE_INSENSITIVE_ORDER)).forEach(k -> ids.add(k.id()));
		for (UUID id : ids) {
			String name = Members.displayName(server, v, id);
			String rank = Members.rankLabel(v.rankOf(id));
			String state = Members.online(server, id) != null ? "online" : "offline";
			ctx.getSource().sendSuccess(() -> Component.translatable("message.villageheart.rank.cmd.roster_line", name, rank, state), false);
		}
		return ids.size();
	}
}
