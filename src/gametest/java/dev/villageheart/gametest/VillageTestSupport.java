package dev.villageheart.gametest;

import com.mojang.authlib.GameProfile;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.world.VillageClaims;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helpers for GameTests that touch the (global, one-per-world) village claim.
 * Every such test must declare its own test environment so vanilla runs it in its own batch,
 * and must call {@link #reset} first and {@link #done} last.
 */
public final class VillageTestSupport {
	private static final List<ServerPlayer> PLAYERS = new ArrayList<>();

	private VillageTestSupport() {}

	/** Clear the claim, leftover players, leftover emeralds, and restore gamerules. */
	public static void reset(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		removePlayers(server);
		// worker tests (M7): give back every POI ticket the mod holds before the claim (and with it the held set) is forgotten,
		// restore the real building source, and discard villagers earlier worker tests spawned
		dev.villageheart.worker.JobSiteReservations.releaseAll(server);
		dev.villageheart.worker.Workers.setJobSiteSourceForTest(null);
		WorkerTestSupport.discardSpawned(h.getLevel());
		dev.villageheart.worker.lumber.LumberRuntime.resetForTests();
		dev.villageheart.worker.gear.SelfCraftRuntime.resetForTests();
		dev.villageheart.worker.gear.SelfCraftSwitch.reset();
		dev.villageheart.worker.gear.SmithService.CURRENT.set(dev.villageheart.worker.smith.SmithServiceImpl.INSTANCE);
		dev.villageheart.worker.smith.SmithRuntime.resetForTests();
		dev.villageheart.golem.SmithyRepairs.reset();
		dev.villageheart.golem.GolemGate.reset();
		SmithTestSupport.releaseForced(h.getLevel());
		dev.villageheart.build.BuildRuntime.resetForTests();
		dev.villageheart.build.BuildersTableActions.reset();
		dev.villageheart.worker.farm.FarmRuntime.resetForTests();
		dev.villageheart.worker.cook.CookRuntime.resetForTests();
		dev.villageheart.worker.farm.FarmJob.setStallTicksForTest(-1);
		dev.villageheart.worker.farm.FarmJob.setAllCellsTakenForTest(false);
		dev.villageheart.worker.farm.FarmJob.testKeepAll = i -> false;
		dev.villageheart.worker.job.WorkerStatuses.clearAll();
		NeedsTestHooks.disable(); // M11: workers of other tests keep full pace and nobody eats; only the needs_ tests switch it back on
		dev.villageheart.food.FoodStats.reset();
		dev.villageheart.needs.Housing.reset();
		dev.villageheart.worker.LevelUpFeedback.resetForTests();
		dev.villageheart.worker.job.WalkTo.setNudgeListenerForTest(null);
		dev.villageheart.worker.WorkXp.clear();
		dev.villageheart.worker.job.AwayTrips.clearForTests();
		dev.villageheart.worker.mine.MinerJob.clearForTests();
		dev.villageheart.worker.hunt.HunterRuntime.resetForTests();
		dev.villageheart.worker.guard.GuardSystem.resetForTests();
		dev.villageheart.worker.guard.GuardRuntime.resetForTests();
		dev.villageheart.worker.guard.GuardCombat.resetForTests();
		dev.villageheart.worker.guard.GuardAlarm.resetForTests();
		dev.villageheart.worker.guard.GuardGear.resetForTests();
		dev.villageheart.worker.guard.PatrolPoints.invalidate();
		dev.villageheart.defence.GateKeeper.resetForTests(server);
		dev.villageheart.market.Markets.clearRuntime(); // M22
		dev.villageheart.market.Markets.withdrawLimitForTest = null;
		dev.villageheart.economy.VillageDawn.removeTestListeners(); // M12: a failed wage test never closed its dawn listener
		dev.villageheart.economy.WageStats.setTestListener(null);
		VillageClaims.data(server).setPendingTicketReleases(java.util.Set.of()); // stale entries of failed runs in this reused world
		VillageClaims.data(server).clear();
		h.getLevel().getGameRules().set(GameRules.BLOCK_DROPS, true, server);
		purgePoi(h);
		for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
			if (e.getItem().is(Items.EMERALD)) e.discard();
		}
	}

	/**
	 * Bells (and their POIs) from other tests, including failed ones that never cleaned up, can sit within the
	 * 16-block claim range of a neighbouring test structure. Each claim test runs alone in its own batch, so it is
	 * safe to drop every village-bell POI around the test before it starts. Only MEETING POIs are touched: a bell
	 * block is set to air (vanilla then removes its POI itself); a POI whose block is already gone is an orphan and
	 * is removed directly. Calling poi.remove while the block still stands would make vanilla log
	 * "POI data mismatch: never registered" when the block is removed later. Other POI types are left alone.
	 */
	private static void purgePoi(GameTestHelper h) {
		var poi = h.getLevel().getPoiManager();
		BlockPos centre = h.absolutePos(BlockPos.ZERO);
		for (var rec : poi.getInRange(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), centre, 48,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			BlockPos p = rec.getPos().immutable();
			if (h.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.BELL)) {
				h.getLevel().setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			} else {
				poi.remove(p);
			}
		}
	}

	private static void removePlayers(MinecraftServer server) {
		for (ServerPlayer p : PLAYERS) server.getPlayerList().remove(p);
		PLAYERS.clear();
	}

	/** A real ServerPlayer (not the mock helper, which is always creative) with its own name and UUID. */
	public static ServerPlayer player(GameTestHelper h, String name, GameType mode) {
		return player(h, name, mode, (server, level, profile) -> new ServerPlayer(server, level, profile, ClientInformation.createDefault()));
	}

	/** Builds the player object, so a test can pass a subclass (for example one that records chat messages). */
	@FunctionalInterface
	public interface PlayerFactory {
		ServerPlayer create(MinecraftServer server, net.minecraft.server.level.ServerLevel level, GameProfile profile);
	}

	/** Like {@link #player(GameTestHelper, String, GameType)} with a custom ServerPlayer subclass. Tracked and removed by reset. */
	public static ServerPlayer player(GameTestHelper h, String name, GameType mode, PlayerFactory factory) {
		MinecraftServer server = h.getLevel().getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		ServerPlayer p = factory.create(server, h.getLevel(), profile);
		Connection conn = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(conn);
		server.getPlayerList().placeNewPlayer(conn, p, CommonListenerCookie.createInitial(profile, false));
		p.setGameMode(mode);
		PLAYERS.add(p);
		if (p.isCreative() != (mode == GameType.CREATIVE)) {
			throw new IllegalStateException("player isCreative() does not match requested mode " + mode);
		}
		if (mode == GameType.SURVIVAL && p.hasInfiniteMaterials()) {
			throw new IllegalStateException("survival player has infinite materials");
		}
		return p;
	}

	/** Gives the player one Heart and places it at relBelow.above() (the click hits relBelow's top face... see plan). */
	public static void place(GameTestHelper h, ServerPlayer p, BlockPos relBelow) {
		ItemStack stack = new ItemStack(ModBlocks.VILLAGE_HEART_ITEM);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		h.placeAt(p, stack, relBelow, Direction.UP);
	}

	/** Reads the saved village back from disk through a brand-new DimensionDataStorage (what a restart would see). */
	public static dev.villageheart.world.VillageSavedData reload(MinecraftServer server) throws java.io.IOException {
		server.overworld().getDataStorage().saveAndJoin();
		java.nio.file.Path dir = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(
				net.minecraft.world.level.Level.OVERWORLD, server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data");
		try (net.minecraft.world.level.storage.DimensionDataStorage fresh = new net.minecraft.world.level.storage.DimensionDataStorage(dir,
				server.getFixerUpper(), server.registryAccess())) {
			return fresh.get(dev.villageheart.world.VillageSavedData.TYPE);
		}
	}

	/**
	 * Test-relative block of a world position. {@code GameTestHelper.relativePos} is not the inverse of {@code absolutePos}:
	 * it rotates by the test rotation plus 180°, so on an unrotated test a round trip lands on the mirrored cell.
	 * This subtracts the origin. Our tests are never rotated; a rotated structure fails here.
	 */
	public static BlockPos rel(GameTestHelper h, BlockPos abs) {
		BlockPos origin = h.absolutePos(BlockPos.ZERO);
		BlockPos rel = abs.subtract(origin);
		if (!h.absolutePos(rel).equals(abs)) throw new AssertionError("the test is rotated; positions cannot be mapped back: " + abs);
		return rel;
	}

	public static int emeralds(GameTestHelper h, BlockPos relPos, double radius) {
		int n = 0;
		for (ItemEntity e : h.getEntities(EntityType.ITEM, relPos, radius)) {
			if (e.getItem().is(Items.EMERALD)) n += e.getItem().getCount();
		}
		return n;
	}

	public static void done(GameTestHelper h) {
		reset(h);
		h.succeed();
	}
}
