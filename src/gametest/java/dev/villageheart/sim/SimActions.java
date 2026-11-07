package dev.villageheart.sim;

import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.sim.ScenarioParser;
import dev.villageheart.world.VillageClaims;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/** The scenario action types. The keys must equal {@link ScenarioParser#ACTION_TYPES} (checked in a GameTest). */
final class SimActions {
	private SimActions() {}

	/** Set by the crash action; thrown from an unguarded tick listener so the server really crashes. */
	static volatile boolean crashRequested;

	static final Map<String, SimAction> ALL = Map.of(
			"claim", SimActions::claim,
			"command", SimActions::command,
			"fail", (ctx, a) -> { throw new IllegalStateException("scenario fail action"); },
			"crash", (ctx, a) -> crashRequested = true,
			"hang", SimActions::hang);

	static void crashTick(MinecraftServer server) {
		if (crashRequested) throw new IllegalStateException("scenario crash action: deliberate server crash");
	}

	private static void claim(SimulationRun ctx, dev.villageheart.core.sim.ScenarioAction a) {
		var level = ctx.level();
		BlockPos bell = ctx.bell();
		BlockPos ground;
		if (a.fields().get("offset") instanceof List<?> off && off.size() == 3) {
			ground = bell.offset(((Number) off.get(0)).intValue(), ((Number) off.get(1)).intValue(), ((Number) off.get(2)).intValue()).below();
		} else {
			ground = SimWorld.heartSpot(level, bell).orElseThrow(() -> new IllegalStateException("no Heart spot near bell " + bell.toShortString()));
		}
		if (ctx.player() == null) throw new IllegalStateException("claim needs the simulated owner");
		var player = ctx.player().player;
		ItemStack heart = new ItemStack(ModBlocks.VILLAGE_HEART_ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, heart);
		InteractionResult r = player.gameMode.useItemOn(player, level, heart, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
		var claim = VillageClaims.current(ctx.server());
		if (claim.isEmpty()) {
			throw new IllegalStateException("claim failed: useItemOn returned " + r + ", rules say "
					+ VillageClaims.evaluate(level, ground.above()) + " at " + ground.above().toShortString());
		}
		ctx.onClaimed(ground.above());
	}

	private static void command(SimulationRun ctx, dev.villageheart.core.sim.ScenarioAction a) {
		String cmd = (String) a.fields().get("command");
		BlockPos at = ctx.heart() != null ? ctx.heart() : ctx.bell();
		boolean[] fired = {false};
		boolean[] ok = {false};
		CommandSourceStack src = ctx.server().createCommandSourceStack()
				.withLevel(ctx.level())
				.withPosition(Vec3.atBottomCenterOf(at))
				.withCallback((success, result) -> { fired[0] = true; ok[0] = success; });
		ctx.server().getCommands().performPrefixedCommand(src, cmd);
		if (!fired[0]) throw new IllegalStateException("command gave no result (parse failure?): " + cmd + " (see log)");
		if (!ok[0]) throw new IllegalStateException("command failed: " + cmd + " (see log)");
	}

	private static void hang(SimulationRun ctx, dev.villageheart.core.sim.ScenarioAction a) throws InterruptedException {
		while (true) Thread.sleep(1000);
	}
}
