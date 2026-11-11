package dev.villageheart.gametest;

import static dev.villageheart.gametest.NeedsTestSupport.sweepVillagers;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.core.sim.ScenarioParser;
import dev.villageheart.core.sim.SimArea;
import dev.villageheart.sim.Host;
import dev.villageheart.sim.SimTickets;
import dev.villageheart.sim.StarterActions;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.stock.StorageContainers;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.world.VillageClaims;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Layer 2: the M11 scenario actions, run by the same code the simulation uses but in the yard (so a broken prefab is found by verify.sh and
 * not ten minutes into a simulation). The site is the yard's ground level.
 */
public class SimActionGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	/** The simulation's runner, played by a GameTest. */
	private static Host host(GameTestHelper h) {
		BlockPos site = h.absolutePos(new BlockPos(0, -1, 0));
		BlockPos[] cur = {site};
		return new Host() {
			public ServerLevel level() { return h.getLevel(); }
			public MinecraftServer server() { return h.getLevel().getServer(); }
			public BlockPos heart() { return h.absolutePos(BuildingTestSupport.BELL); }
			public BlockPos site() { return cur[0]; }
			public void setSite(BlockPos s) { cur[0] = s; }
		};
	}

	private static List<ScenarioAction> starter() {
		try {
			Path p = Path.of("sim/scenarios/starter.json");
			if (!Files.exists(p)) p = Path.of("../../../sim/scenarios/starter.json"); // the GameTest server runs in build/run/gameTest
			return ScenarioParser.parse("starter", Files.readString(p)).actions();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ScenarioAction shifted(ScenarioAction a, int dx, int dz) {
		Map<String, Object> f = new LinkedHashMap<>(a.fields());
		for (String k : new String[] {"at", "area"}) {
			if (f.get(k) instanceof List<?> l) {
				List<Object> n = new ArrayList<>(l);
				n.set(0, ((Number) l.get(0)).longValue() + dx);
				n.set(1, ((Number) l.get(1)).longValue() + dz);
				f.put(k, n);
			}
		}
		return new ScenarioAction(a.type(), a.day(), a.tick(), f);
	}

	private static ScenarioAction find(List<ScenarioAction> all, String type, String building) {
		return all.stream().filter(a -> a.type().equals(type) && (building == null || building.equals(a.fields().get("building")))).findFirst().orElseThrow();
	}

	private static int valid(GameTestHelper h, BuildingType t) { return Buildings.valid(h.getLevel().getServer(), t).size(); }

	// ---- T-L2-12 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_prefab_rooms", structure = YARD, skyAccess = true, maxTicks = 700)
	public void starterPrefabsValidRooms(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<ScenarioAction> all = starter();
		BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			Host host = host(h);
			for (ScenarioAction a : all) if (a.type().equals("prefab") && List.of("house", "storage", "kitchen").contains(a.fields().get("building"))) StarterActions.prefab(host, a);
		}).thenWaitUntil(() -> {
			h.assertValueEqual(2, valid(h, BuildingType.HOUSE), "valid Houses");
			h.assertValueEqual(1, valid(h, BuildingType.STORAGE), "valid Storage");
			h.assertValueEqual(1, valid(h, BuildingType.KITCHEN), "valid Kitchen");
		}).thenExecute(() -> {
			h.assertValueEqual(40L, Stockpiles.count(h.getLevel().getServer(), Items.BREAD), "bread in the Storage");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:needs_prefab_farm", structure = YARD, skyAccess = true, maxTicks = 700)
	public void starterPrefabsValidFarmAndCamp(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<ScenarioAction> all = starter();
		BlockPos[] chop = new BlockPos[1];
		BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			LumberTestSupport.sweepStrayTrees(h, 48);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			Host host = host(h);
			// the scenario's second row, moved into the yard: 10 blocks north, the grove 14 blocks west
			StarterActions.prefab(host, shifted(find(all, "prefab", "farmhouse"), 0, -10));
			StarterActions.field(host, shifted(find(all, "field", null), 0, -10));
			StarterActions.prefab(host, shifted(find(all, "prefab", "lumber_camp"), 0, -10));
			StarterActions.grove(host, shifted(find(all, "grove", null), -14, -10));
		}).thenWaitUntil(() -> {
			h.assertValueEqual(1, valid(h, BuildingType.FARMHOUSE), "valid Farmhouse");
			h.assertValueEqual(1, valid(h, BuildingType.LUMBER_CAMP), "valid Lumber Camp");
		}).thenExecute(() -> {
			int carrots = 0, bases = 0;
			List<BlockPos> logBases = new ArrayList<>();
			boolean axe = false;
			for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
				for (int y = -1; y <= 4; y++) {
					BlockPos rel = new BlockPos(x, y, z);
					BlockPos abs = h.absolutePos(rel);
					BlockState s = h.getLevel().getBlockState(abs);
					if (s.is(Blocks.CARROTS) && s.getValue(CropBlock.AGE) == 7 && h.getLevel().getBlockState(abs.below()).is(Blocks.FARMLAND)) carrots++;
					if (s.is(BlockTags.OVERWORLD_NATURAL_LOGS) && !h.getLevel().getBlockState(abs.below()).is(BlockTags.OVERWORLD_NATURAL_LOGS)) logBases.add(abs);
					if (s.is(Blocks.CHEST)) {
						Container c = StorageContainers.at(h.getLevel(), abs);
						if (c != null) for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.DIAMOND_AXE)) axe = true;
					}
				}
			}
			h.assertValueEqual(80, carrots, "mature carrots on moist farmland");
			h.assertValueEqual(8, logBases.size(), "oak trees in the grove");
			h.assertTrue(axe, "the camp chest holds the diamond axe");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	// ---- T-L2-13 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_hire_action", structure = YARD, skyAccess = true, maxTicks = 700)
	public void hireActionPicksNearestAdult(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<ScenarioAction> all = starter();
		ScenarioAction hire = find(all, "hire", null);
		Villager[] near = new Villager[1];
		Villager[] far = new Villager[1];
		Villager[] baby = new Villager[1];
		Villager[] nitwit = new Villager[1];
		BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			StarterActions.prefab(host(h), shifted(find(all, "prefab", "farmhouse"), 4, 0));
			// hire is for the job "farmer" in "farmhouse" (the first hire action of the file)
		}).thenWaitUntil(() -> h.assertValueEqual(1, valid(h, BuildingType.FARMHOUSE), "valid Farmhouse"))
				.thenExecute(() -> {
					baby[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(4, 0, 12));
					baby[0].setAge(-24000);
					nitwit[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(5, 0, 12));
					nitwit[0].setVillagerData(nitwit[0].getVillagerData().withProfession(h.getLevel().registryAccess(),
							net.minecraft.world.entity.npc.villager.VillagerProfession.NITWIT));
					near[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(8, 0, 20));
					far[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(28, 0, 28));
					StarterActions.hire(host(h), hire);
					h.assertTrue(WorkerAttachments.isWorker(near[0]), "the nearer adult was hired");
					h.assertFalse(WorkerAttachments.isWorker(far[0]), "the farther adult was not");
					h.assertFalse(WorkerAttachments.isWorker(baby[0]) || WorkerAttachments.isWorker(nitwit[0]), "neither the baby nor the nitwit");
					BlockPos front = h.absolutePos(new BlockPos(8, 0, 13));
					h.assertTrue(near[0].distanceToSqr(front.getX() + 0.5, front.getY(), front.getZ() + 0.5) < 1.0, "the hire was teleported in front of the door");
					boolean threw = false;
					try {
						StarterActions.hire(host(h), hire);
					} catch (IllegalStateException e) {
						threw = e.getMessage().contains("hire refused");
					}
					h.assertTrue(threw, "the only slot is taken: the action throws with the refusal");
					ScenarioAction camp = all.stream().filter(a -> a.type().equals("hire") && "lumber_camp".equals(a.fields().get("building"))).findFirst().orElseThrow();
					boolean threw2 = false;
					try {
						StarterActions.hire(host(h), camp);
					} catch (IllegalStateException e) {
						threw2 = e.getMessage().contains("no valid lumber_camp");
					}
					h.assertTrue(threw2, "no valid Lumber Camp: the action throws naming it");
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}

	// ---- T-L2-15 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_random_ticks", structure = YARD, skyAccess = true, maxTicks = 2200)
	public void forcedChunkGrowsWithoutPlayer(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos far = h.absolutePos(new BlockPos(0, 0, 100_000));
		int cx = far.getX() >> 4, cz = far.getZ() >> 4;
		int groundY = h.absolutePos(BlockPos.ZERO).getY() - 1;
		BlockPos farm = new BlockPos((far.getX() >> 4 << 4) + 2, groundY, (far.getZ() >> 4 << 4) + 8); // the sapling 6 blocks east stays in the chunk
		BlockPos sapling = farm.east(6);
		GameTestSequence seq = h.startSequence();
		seq.thenExecute(() -> {
			h.assertValueEqual(300, (int) level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED), "random_tick_speed (the needs_random_ticks environment)");
			// noon, and time frozen: saplings only grow in daylight
			level.setDayTime(6000);
			level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, level.getServer());
			// Generate the forced chunk and its ring synchronously first. A forced chunk is promoted to entity-ticking only once the
			// chunks around it are generated, and that runs on the chunk system's worker threads: a GameTest server ticks as fast as it can,
			// so under load the promotion took hundreds of ticks (454 in a full run, more at load 10: "timed out at tick 2202 of 2200").
			// Blocking here moves that wait out of the tick budget; what is left is the promotion itself (a handful of ticks).
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				level.getChunkSource().getChunk(cx + dx, cz + dz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
			}
			level.setChunkForced(cx, cz, true);
			level.setChunkForced(sapling.getX() >> 4, sapling.getZ() >> 4, true);
		}).thenWaitUntil(() -> {
			// a forced chunk is promoted to entity-ticking a little later (the simulation harness waits for it too)
			h.assertTrue(level.isPositionEntityTicking(farm) && level.isPositionEntityTicking(sapling), "the forced chunks are entity-ticking");
		}).thenExecute(() -> {
			for (int x = -1; x <= 8; x++) for (int z = -2; z <= 2; z++) {
				for (int y = 0; y <= 6; y++) level.setBlock(farm.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				level.setBlock(farm.offset(x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
				level.setBlock(farm.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			}
			level.setBlock(farm.east(1), Blocks.WATER.defaultBlockState(), 2);
			level.setBlock(farm, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 2);
			level.setBlock(farm.above(), Blocks.CARROTS.defaultBlockState(), 2);
			level.setBlock(sapling.above(), Blocks.OAK_SAPLING.defaultBlockState(), 2);
			h.assertTrue(level.getPlayers(p -> true).stream().noneMatch(p -> p.distanceToSqr(far.getX(), far.getY(), far.getZ()) < 100_000.0 * 100_000.0 / 2),
					"no player anywhere near the forced chunk");
		}).thenWaitUntil(() -> {
			boolean carrotGrew = level.getBlockState(farm.above()).is(Blocks.CARROTS) && level.getBlockState(farm.above()).getValue(CropBlock.AGE) > 0;
			boolean saplingGrew = level.getBlockState(sapling.above()).is(BlockTags.OVERWORLD_NATURAL_LOGS)
					|| (level.getBlockState(sapling.above()).is(Blocks.OAK_SAPLING) && level.getBlockState(sapling.above()).getValue(net.minecraft.world.level.block.SaplingBlock.STAGE) == 1);
			h.assertTrue(carrotGrew && saplingGrew, "carrot grew " + carrotGrew + ", sapling grew " + saplingGrew
					+ " (carrot " + level.getBlockState(farm.above()) + ", sapling " + level.getBlockState(sapling.above()) + ")"
					+ " light carrot " + level.getRawBrightness(farm.above(), 0) + " sapling " + level.getRawBrightness(sapling.above(), 0));
		}).thenExecute(() -> {
			level.setChunkForced(cx, cz, false);
			level.setChunkForced(sapling.getX() >> 4, sapling.getZ() >> 4, false);
			level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, true, level.getServer());
			h.succeed();
		});
	}

	// ---- review of M11 round 2: the site ramp ---------------------------------------------------

	/** The ground of a column as a walker sees it: from the top down, through air, leaves and logs, to the first solid block. */
	private static int groundOf(ServerLevel level, int x, int z, int top, int bottom) {
		for (int y = top; y >= bottom; y--) {
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && s.blocksMotion()) return y;
		}
		return bottom;
	}

	/**
	 * Hilly, forested land (steps of up to 6 between neighbours, a cliff, trees, and canopies with air pockets and no trunk under them) around a
	 * flat patch: after {@code ramp} every two columns that share an edge differ by at most 1 up to the ring's minimum width, no grass is left in
	 * mid-air, and the patch is untouched.
	 */
	@GameTest(environment = "villageheart-gametest:sim_ramp", structure = YARD, skyAccess = true, maxTicks = 400)
	public void siteRampHasOneBlockSteps(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos far = h.absolutePos(new BlockPos(0, 0, 200_000));
		int w = 12, d = 8, floor = h.absolutePos(BlockPos.ZERO).getY() + 3;
		BlockPos origin = new BlockPos((far.getX() >> 4 << 4) + 8, floor, (far.getZ() >> 4 << 4) + 8);
		int reach = 9 + 12 + 1; // the ring, the extension to meet the land (up to 12 more) and one more
		int x0 = origin.getX() - reach, x1 = origin.getX() + w + reach, z0 = origin.getZ() - reach, z1 = origin.getZ() + d + reach;
		int bottom = floor - 20, top = floor + 24;
		h.startSequence().thenExecute(() -> {
			for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) level.setChunkForced(cx, cz, true);
			BlockState air = Blocks.AIR.defaultBlockState();
			for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
				boolean patch = x >= origin.getX() && x < origin.getX() + w && z >= origin.getZ() && z < origin.getZ() + d;
				int rough = Math.floorMod(x * 7 + z * 13 + x * z, 7) - 3 + (x < origin.getX() - 4 ? -4 : 0); // a cliff on the west side
				int g = patch ? floor : floor + rough;
				for (int y = bottom; y <= top; y++) {
					level.setBlock(new BlockPos(x, y, z), y < g ? Blocks.DIRT.defaultBlockState() : y == g ? Blocks.GRASS_BLOCK.defaultBlockState() : air, 2);
				}
			}
			// trees on the ring: a trunk with a canopy, and canopies hanging in the air with no trunk (what a cut tree leaves)
			for (int t = 0; t < 14; t++) {
				int tx = origin.getX() - 11 + (t * 5) % (w + 22), tz = origin.getZ() - 10 + (t * 7) % (d + 20);
				if (tx >= origin.getX() - 1 && tx <= origin.getX() + w && tz >= origin.getZ() - 1 && tz <= origin.getZ() + d) continue;
				int gy = groundOf(level, tx, tz, top, bottom);
				boolean trunk = t % 2 == 0;
				for (int dy = 1; dy <= 6; dy++) {
					if (trunk && dy <= 5) level.setBlock(new BlockPos(tx, gy + dy, tz), Blocks.OAK_LOG.defaultBlockState(), 2);
					if (dy >= 4) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
						BlockPos q = new BlockPos(tx + dx, gy + dy, tz + dz);
						if (Math.abs(dx) + Math.abs(dz) < 4 && level.getBlockState(q).isAir()) {
							level.setBlock(q, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
						}
					}
				}
			}
			StarterActions.ramp(level, origin, w, d);
			int minRing = 9 + 3, worst = 0, floating = 0;
			String where = "";
			for (int x = origin.getX() - minRing; x < origin.getX() + w + minRing; x++) for (int z = origin.getZ() - minRing; z < origin.getZ() + d + minRing; z++) {
				int g = groundOf(level, x, z, top, bottom);
				if (level.getBlockState(new BlockPos(x, g, z)).is(Blocks.GRASS_BLOCK) && level.getBlockState(new BlockPos(x, g - 1, z)).isAir()) floating++;
				for (int[] n : new int[][] {{1, 0}, {0, 1}}) {
					int step = Math.abs(g - groundOf(level, x + n[0], z + n[1], top, bottom));
					if (step > worst) { worst = step; where = (x - origin.getX()) + "," + (z - origin.getZ()); }
				}
				boolean patch = x >= origin.getX() && x < origin.getX() + w && z >= origin.getZ() && z < origin.getZ() + d;
				if (patch) h.assertTrue(g == floor, "the patch is untouched at " + x + "," + z + " (" + g + " instead of " + floor + ")");
			}
			for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) level.setChunkForced(cx, cz, false);
			h.assertTrue(worst <= 1, "the largest step between neighbouring columns is " + worst + ", at patch offset " + where);
			h.assertValueEqual(0, floating, "grass blocks with air below them");
			h.succeed();
		});
	}

	// ---- M13 T-L2-11 -----------------------------------------------------------------------------

	/** The wheat field and the Kitchen of {@code starter_cook}: 80 mature wheat on farmland, the Kitchen valid with 32 coal beside the bread. */
	@GameTest(environment = "villageheart-gametest:cook_prefabs", structure = YARD, skyAccess = true, maxTicks = 700)
	public void starterCookPrefabs(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<ScenarioAction> all;
		try {
			Path p = Path.of("sim/scenarios/starter_cook.json");
			if (!Files.exists(p)) p = Path.of("../../../sim/scenarios/starter_cook.json");
			all = ScenarioParser.parse("starter_cook", Files.readString(p)).actions();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		List<ScenarioAction> scenario = all;
		BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			Host host = host(h);
			for (ScenarioAction a : scenario) {
				if (a.type().equals("prefab") && List.of("storage", "kitchen").contains(a.fields().get("building"))) StarterActions.prefab(host, a);
				if (a.type().equals("field") && "wheat".equals(a.fields().get("crop"))) StarterActions.field(host, a);
			}
		}).thenWaitUntil(() -> {
			h.assertValueEqual(1, valid(h, BuildingType.STORAGE), "valid Storage");
			h.assertValueEqual(1, valid(h, BuildingType.KITCHEN), "valid Kitchen");
		}).thenExecute(() -> {
			int wheat = 0;
			for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
				BlockPos abs = h.absolutePos(new BlockPos(x, 0, z));
				BlockState s = h.getLevel().getBlockState(abs);
				if (s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == 7 && h.getLevel().getBlockState(abs.below()).is(Blocks.FARMLAND)) wheat++;
			}
			h.assertValueEqual(80, wheat, "mature wheat on farmland");
			h.assertValueEqual(32L, Stockpiles.count(h.getLevel().getServer(), Items.COAL), "coal in the Storage");
			h.assertValueEqual(40L, Stockpiles.count(h.getLevel().getServer(), Items.BREAD), "bread in the Storage");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	// ---- sim-area: follow tickets (a player's simulation distance around each hired worker) ----------------------------------------------

	/**
	 * The L3 follow ticket really makes a far, otherwise unloaded chunk entity-tick: an entity there ticks, the chunks
	 * {@link SimArea#FOLLOW_TICKING_CHUNKS} away tick too and the next ring does not; when the ticket moves the old spot stops ticking and the
	 * new one starts; after {@code releaseAll} nothing is left.
	 */
	@GameTest(environment = "villageheart-gametest:sim_follow_tickets", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void followTicketKeepsFarChunkTicking(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos near = h.absolutePos(BlockPos.ZERO);
		// mid-chunk, far from every other test (the wage test uses z + 100 000)
		BlockPos far = new BlockPos(((near.getX() + 60_000) & ~15) + 8, 100, (near.getZ() & ~15) + 8);
		BlockPos edge = far.east(16 * SimArea.FOLLOW_TICKING_CHUNKS);
		BlockPos outside = far.east(16 * (SimArea.FOLLOW_TICKING_CHUNKS + 1));
		BlockPos moved = far.east(16 * 10);
		UUID worker = UUID.randomUUID();
		SimTickets tickets = new SimTickets(level);
		ArmorStand[] stand = new ArmorStand[1];
		int[] ticksAt = new int[1];
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			h.assertFalse(level.isPositionEntityTicking(far), "the far chunk does not tick before the ticket");
			generate(level, far);
			h.assertValueEqual(1, tickets.follow(Map.of(worker, SimTickets.chunkOf(far))), "one ticket placed");
			h.assertValueEqual(0, tickets.follow(Map.of(worker, SimTickets.chunkOf(far.west(3)))), "same chunk: no change");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the followed chunk entity-ticking", () -> {
			h.assertTrue(level.isPositionEntityTicking(far), "the followed chunk ticks entities");
			h.assertTrue(level.isPositionEntityTicking(edge), "and so does the chunk " + SimArea.FOLLOW_TICKING_CHUNKS + " away");
		});
		seq = seq.thenExecute(() -> {
			h.assertFalse(level.isPositionEntityTicking(outside), "the next ring does not tick entities");
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ());
			stand[0] = new ArmorStand(level, far.getX() + 0.5, y, far.getZ() + 0.5);
			h.assertTrue(level.addFreshEntity(stand[0]), "an entity can be added in the followed chunk");
			ticksAt[0] = stand[0].tickCount;
		});
		seq = seq.thenIdle(20).thenExecute(() -> {
			h.assertTrue(stand[0].tickCount >= ticksAt[0] + 15, "the entity in the followed chunk ticks (" + (stand[0].tickCount - ticksAt[0]) + " of 20 ticks)");
			stand[0].discard();
			generate(level, moved);
			h.assertValueEqual(2, tickets.follow(Map.of(worker, SimTickets.chunkOf(moved))), "the ticket moves: one removed, one added");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the ticket following the worker", () -> {
			h.assertTrue(level.isPositionEntityTicking(moved), "the new chunk ticks");
			h.assertFalse(level.isPositionEntityTicking(far), "the old chunk no longer ticks");
		});
		seq = seq.thenExecute(tickets::releaseAll);
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the released chunk", () -> h.assertFalse(level.isPositionEntityTicking(moved), "released: nothing ticks"));
		seq.thenExecute(h::succeed);
	}

	/**
	 * Generates the ticket's whole loading square now, on the server thread. The GameTest server does not wait between ticks, so an
	 * asynchronous generation of ~100 fresh chunks 60 000 blocks out lasts longer than any tick budget (seen: 2000 ticks in one second).
	 */
	private static void generate(ServerLevel level, BlockPos centre) {
		int r = SimArea.FOLLOW_TICKET_RADIUS;
		for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) level.getChunk((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz);
	}

	// ---- M26 prep: the wall ring on uneven ground ------------------------------------------------

	/**
	 * {@code wall_ring} where the natural ground outside the west wall is one block above the site (as on seed 1): the wall there is raised so its
	 * top is {@code WALL_CLEARANCE} above that ground, the wall on flat ground stays two high, and a zombie on the higher ground cannot plan a path
	 * to a cell inside. Before the fix the two-high wall was a one-block step from outside: {@code defended_walled} logged scripted zombies inside
	 * the ring with its only gate closed. The ground in front of the gate is one up as well: it is dug down, so the closed gate stops a zombie.
	 */
	@GameTest(environment = "villageheart-gametest:sim_wall_ring_uneven", structure = YARD, skyAccess = true, maxTicks = 400)
	public void wallRingRisesOverHigherGround(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		net.minecraft.world.entity.monster.zombie.Zombie[] z = new net.minecraft.world.entity.monster.zombie.Zombie[2];
		BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			for (int x = 0; x <= 3; x++) for (int zz = 2; zz <= 21; zz++) h.setBlock(new BlockPos(x, 0, zz), Blocks.GRASS_BLOCK); // ground one up, west
			for (int x = 11; x <= 17; x++) for (int zz = 20; zz <= 25; zz++) h.setBlock(new BlockPos(x, 0, zz), Blocks.GRASS_BLOCK); // and in front of the gate
			BlockPos site = h.absolutePos(new BlockPos(4, -1, 4));
			Host host = new Host() {
				public ServerLevel level() { return h.getLevel(); }
				public MinecraftServer server() { return h.getLevel().getServer(); }
				public BlockPos heart() { return h.absolutePos(BuildingTestSupport.BELL); }
				public BlockPos site() { return site; }
				public void setSite(BlockPos s) { }
			};
			dev.villageheart.sim.GuardActions.wallRing(host, new ScenarioAction("wall_ring", 0, 0, Map.of("size", List.of(20L, 16L), "gate", 10L)));
			z[0] = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 12));
			z[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.IRON_HELMET));
			z[0].setPersistenceRequired(); // with its AI: a no-AI mob in mid-air plans from the wrong start node
			z[1] = h.spawn(EntityType.ZOMBIE, new BlockPos(14, 1, 24)); // on the higher ground, two cells out from the gate (x 4 + 10)
			z[1].setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.IRON_HELMET));
			z[1].setPersistenceRequired();
		}).thenIdle(5).thenExecute(() -> {
			h.assertTrue(z[0].getBlockX() < h.absolutePos(new BlockPos(4, 0, 0)).getX(), "the zombie is still outside: " + h.relativePos(z[0].blockPosition()));
			for (int zz = 5; zz <= 18; zz++) {
				for (int y = 0; y <= 2; y++) {
					h.assertTrue(h.getBlockState(new BlockPos(4, y, zz)).is(Blocks.COBBLESTONE), "west wall (higher ground outside) solid at y " + y + ", z " + zz);
				}
				h.assertTrue(h.getBlockState(new BlockPos(23, 0, zz)).is(Blocks.COBBLESTONE) && h.getBlockState(new BlockPos(23, 1, zz)).is(Blocks.COBBLESTONE)
						&& !h.getBlockState(new BlockPos(23, 2, zz)).is(Blocks.COBBLESTONE), "east wall (flat ground) two high at z " + zz);
			}
			net.minecraft.world.level.pathfinder.Path path = z[0].getNavigation().createPath(h.absolutePos(new BlockPos(5, 0, 12)), 0);
			h.assertTrue(path == null || !path.canReach(), "a zombie on the higher ground cannot plan a path over the wall: " + path);
			for (int x = 13; x <= 15; x++) {
				for (int zz = 20; zz <= 21; zz++) h.assertTrue(h.getBlockState(new BlockPos(x, 0, zz)).isAir(), "the gate approach is dug down at " + x + ", " + zz);
			}
			h.assertTrue(h.getBlockState(new BlockPos(14, 0, 19)).getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock, "the gate");
			net.minecraft.world.level.pathfinder.Path viaGate = z[1].getNavigation().createPath(h.absolutePos(new BlockPos(14, 0, 16)), 0);
			h.assertTrue(viaGate == null || !viaGate.canReach(), "a zombie in front of the gate cannot plan a path over it: " + viaGate);
			z[0].discard();
			z[1].discard();
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}
}
