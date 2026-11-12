package dev.villageheart.gametest;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.growth.License;
import dev.villageheart.food.Dinner;
import dev.villageheart.growth.Growth;
import dev.villageheart.growth.GrowthStats;
import dev.villageheart.needs.Housing;
import dev.villageheart.needs.NeedsOverride;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the growth GameTests (M23). Rules (docs/TESTING.md, "Growth GameTests"): own {@code growth_<name>} environment (its own batch: the
 * day clock is shared), the {@code yard} structure with {@code skyAccess}, {@link #scene} first (claim, Storage with bread, up to two Houses;
 * dinner and wages stay off, growth is on), {@link WageTestSupport#toDawn} to cross a dawn, {@link #end} last. Unemployed adults are spawned
 * with no job site. Babies are counted twice: by the mod's birth hook ({@link GrowthStats#births}) and by an {@code ENTITY_LOAD} listener
 * that sees every baby villager appear in the world (it catches a vanilla birth the hook would not).
 */
public final class GrowthTestSupport {
	private GrowthTestSupport() {}

	public static final BlockPos HOUSE1 = NeedsTestSupport.HOUSE;
	public static final BlockPos HOUSE2 = new BlockPos(18, 0, 14);

	private static final AtomicInteger LOADED_BABIES = new AtomicInteger();
	private static boolean listening;

	public static String env(String name) { return "villageheart-gametest:growth_" + name; }

	public static void log(String s) { System.out.println("[m23] " + s); }

	/** Interior foot position of bed {@code i} (0..3) of the House at {@code origin}; the head is one block south. */
	public static BlockPos bedFoot(BlockPos origin, int i) {
		return BuildingTestSupport.inside(origin, i % 2 == 0 ? 0 : 4, 0, i < 2 ? 0 : 2);
	}

	public static BlockPos bedHead(BlockPos origin, int i) { return bedFoot(origin, i).south(); }

	public static BlockPos marker(BlockPos origin) { return BuildingTestSupport.markerPos(origin, 5); }

	public static void buildHouse(GameTestHelper h, BlockPos origin, int beds) {
		BuildingTestSupport.room(h, origin, 5, 4);
		for (int i = 0; i < beds; i++) WorkerTestSupport.bed(h, bedFoot(origin, i));
		h.setBlock(BuildingTestSupport.inside(origin, 2, 0, 3), Blocks.TORCH);
		BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, marker(origin), Direction.NORTH);
	}

	/**
	 * Claim, a valid Storage holding {@code bread} bread (waits until the stockpile sees it), a House with {@code beds1} beds and, when
	 * {@code beds2 > 0}, a second one. Growth is on, dinner and wages are off. Call {@link WorkerTestSupport#begin} NOT before: this does.
	 */
	public static GameTestSequence scene(GameTestHelper h, int beds1, int beds2, int bread) {
		WorkerTestSupport.begin(h);
		registerListener();
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			NeedsTestSupport.sweepVillagers(h);
			WorkerTestSupport.light(h, 0, 0, 31, 31); // before any room: a torch never replaces a bed
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			FarmTestSupport.buildStorage(h);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, FarmTestSupport.storeMarker(), Direction.NORTH);
			if (beds1 > 0) buildHouse(h, HOUSE1, beds1);
			if (beds2 > 0) buildHouse(h, HOUSE2, beds2);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, FarmTestSupport.storeMarker(), dev.villageheart.block.MarkerStatus.VALID);
			if (beds1 > 0) {
				BuildingTestSupport.assertStatus(h, marker(HOUSE1), dev.villageheart.block.MarkerStatus.VALID);
				for (int i = 0; i < beds1; i++) h.assertTrue(WorkerTestSupport.poiPresent(h, bedHead(HOUSE1, i)), "the POI of bed " + i + " of House 1 exists");
			}
			if (beds2 > 0) {
				BuildingTestSupport.assertStatus(h, marker(HOUSE2), dev.villageheart.block.MarkerStatus.VALID);
				for (int i = 0; i < beds2; i++) h.assertTrue(WorkerTestSupport.poiPresent(h, bedHead(HOUSE2, i)), "the POI of bed " + i + " of House 2 exists");
			}
		}).thenExecute(() -> {
			if (bread > 0) {
				if (bread > 64) NeedsTestSupport.put(h, new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, bread - 64));
				else NeedsTestSupport.putBread(h, bread);
			}
			NeedsOverride.growthEnabled = true;
			GrowthStats.reset();
			LOADED_BABIES.set(0);
		}).thenWaitUntil(() -> h.assertTrue(Dinner.nutrition(h.getLevel().getServer()) >= 5L * bread, "the stockpile sees the bread: " + Dinner.nutrition(h.getLevel().getServer())));
	}

	/** Counts every baby villager that appears in the world (a birth by any path). */
	private static synchronized void registerListener() {
		if (listening) return;
		listening = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && v.isBaby() && v.getAge() <= -23000) LOADED_BABIES.incrementAndGet();
		});
	}

	public static String describe(GameTestHelper h) {
		return "licence " + licence(h).map(l -> l.state() + l.lapse().map(r -> "/" + r).orElse("") + " seeds " + l.seeds() + " aborts " + l.aborts()
				+ " approach " + l.approachTicks()).orElse("none")
				+ ", dawn " + GrowthStats.lastDawn() + ", " + dev.villageheart.growth.BreedTrace.summary() + ", day time " + h.getLevel().getDayTime();
	}

	public static int loadedBabies() { return LOADED_BABIES.get(); }

	// ---- villagers -----------------------------------------------------------------------------------

	/** An unemployed adult inside the House at {@code origin} (interior column {@code x}, row {@code z}). */
	public static Villager adult(GameTestHelper h, BlockPos origin, int x, int z) {
		Villager v = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(origin, x, 0, z));
		v.setAge(0);
		return v;
	}

	/** Gives {@code v} the bed whose head is at {@code headRel} (ticket and HOME). */
	public static void home(GameTestHelper h, Villager v, BlockPos headRel) { NeedsTestSupport.claimBed(h, v, headRel); }

	/** True when the HOME POI at the ABSOLUTE position exists and has a free ticket (h.relativePos is not the inverse of h.absolutePos here). */
	public static boolean spaceAbs(GameTestHelper h, BlockPos abs) {
		return h.getLevel().getPoiManager().getInSquare(t -> true, abs, 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
				.anyMatch(r -> r.getPos().equals(abs));
	}

	/** Gives {@code v} the bed whose head is at the ABSOLUTE position (ticket and HOME). */
	public static void claimBedAbs(GameTestHelper h, Villager v, BlockPos headAbs) {
		var poi = h.getLevel().getPoiManager();
		var got = poi.take(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), (t, p) -> p.equals(headAbs), headAbs, 1);
		h.assertTrue(got.isPresent(), "the bed at " + headAbs.toShortString() + " had a free ticket");
		v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
				net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), headAbs));
	}

	/** Makes the housing index current (the needs system does it every 100 ticks). */
	public static void housingNow(GameTestHelper h) { Housing.rebuild(h.getLevel().getServer()); }

	public static boolean housed(GameTestHelper h, Villager v) { return Housing.housed(h.getLevel().getServer(), v, Optional.empty()); }

	public static void breadInPockets(Villager v, int n) { v.getInventory().addItem(new ItemStack(Items.BREAD, n)); }

	// ---- state -------------------------------------------------------------------------------------

	public static VillageState village(GameTestHelper h) { return VillageClaims.current(h.getLevel().getServer()).orElseThrow(); }

	public static Optional<License> licence(GameTestHelper h) { return Growth.state(h.getLevel().getServer()).licence(); }

	public static List<Villager> babies(GameTestHelper h) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : h.getEntities(EntityType.VILLAGER)) if (v.isBaby()) out.add(v);
		return out;
	}

	/** Licenses the pair for today as a dawn would (a House bed reserved), for tests that need a courtship without waiting for the dawn. */
	public static License license(GameTestHelper h, Villager a, Villager b) {
		long day = WageTestSupport.dayIndex(h);
		return Growth.issueLicence(h.getLevel().getServer(), village(h), day, a, b).orElseThrow(() -> h.assertionException("no free House bed to reserve"));
	}

	/** Runs {@code check} every {@code step} ticks for {@code total} ticks. */
	public static GameTestSequence watch(GameTestSequence seq, int total, int step, Runnable check) {
		for (int t = 0; t < total; t += step) seq = seq.thenExecuteAfter(step, check);
		return seq;
	}

	/** Puts the villager at an exact position given relative to the test (block corner coordinates), feet on the floor. */
	public static void placeAt(GameTestHelper h, Villager v, double x, double y, double z) {
		net.minecraft.world.phys.Vec3 p = h.absoluteVec(new net.minecraft.world.phys.Vec3(x, y, z));
		v.snapTo(p.x, p.y, p.z, 0.0F, 0.0F);
	}

	/**
	 * Jumps to the next dawn. Before the jump every villager in the test area loses its {@code CANT_REACH_WALK_TARGET_SINCE} stamp.
	 * The jump moves the day time to the evening (REST starts) without moving the game time. Vanilla's REST behaviour
	 * {@code SetWalkTargetFromBlockMemory(HOME, ..., 1200)} releases the bed and erases HOME when the stamp is older than 1200 ticks, and a stroll
	 * of day 1 that failed to reach its target leaves such a stamp: in normal play thousands of ticks of other walks between morning and REST clear it,
	 * the jump skips them. Without this, about 1 run in 25 lost a second-pair adult's HOME across the jump and the next dawn was
	 * {@code TOO_FEW_READY} (review M23-r2 item 1; the same fix as in {@code noFreeBedNoBabyInThreeDays}).
	 */
	public static GameTestSequence toDawn(GameTestHelper h, GameTestSequence seq) {
		return WageTestSupport.toDawn(h, seq.thenExecute(() -> {
			for (Villager v : h.getEntities(EntityType.VILLAGER)) {
				v.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
			}
		}), new PayResult[1]);
	}

	public static void end(GameTestHelper h) {
		for (Villager b : babies(h)) b.discard();
		VillageTestSupport.done(h);
		WorkerTestSupport.end(h);
	}
}
