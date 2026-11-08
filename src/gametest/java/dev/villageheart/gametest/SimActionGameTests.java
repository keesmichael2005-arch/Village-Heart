package dev.villageheart.gametest;

import static dev.villageheart.gametest.NeedsTestSupport.sweepVillagers;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.core.sim.ScenarioParser;
import dev.villageheart.sim.Host;
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
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
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
			level.getChunk(cx, cz);
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
					+ " (carrot " + level.getBlockState(farm.above()) + ", sapling " + level.getBlockState(sapling.above()) + ")");
		}).thenExecute(() -> {
			level.setChunkForced(cx, cz, false);
			level.setChunkForced(sapling.getX() >> 4, sapling.getZ() >> 4, false);
			level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, true, level.getServer());
			h.succeed();
		});
	}
}
