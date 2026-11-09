package dev.villageheart.gametest;

import dev.villageheart.block.MarkerStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the guard GameTests (M18). Rules (docs/TESTING.md, "Guard tests (M18+)"): own environment per test
 * ({@code guards_<name>}), the {@code yard} structure with sky access, {@code BuildingTestSupport.begin} + {@code WorkerTestSupport.begin}
 * first, {@code VillageTestSupport.done} + {@code WorkerTestSupport.end} last, tier Village. Natural mob spawning stays off (the
 * environments' game rule), so no mob joins a test that did not spawn it.
 */
public final class GuardTestSupport {
	private GuardTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:guards_" + name; }

	public static final BlockPos BARRACKS = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;

	public static BlockPos barracksMarker() { return BuildingTestSupport.markerPos(BARRACKS, 5); }

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 5); }

	/** The interior cell where a guard is spawned: the row between the grindstones (north) and the beds (south). */
	public static BlockPos spawnSpot() { return BuildingTestSupport.inside(BARRACKS, 3, 0, 1); }

	public static BlockPos grindstone(int i) { return BuildingTestSupport.inside(BARRACKS, i, 0, 0); }

	public static BlockPos bedFoot(int i) { return BuildingTestSupport.inside(BARRACKS, i, 0, 2); }

	public static BlockPos bedHead(int i) { return bedFoot(i).south(); }

	public static BlockPos chest() { return BuildingTestSupport.inside(BARRACKS, 4, 0, 0); }

	/** A stone floor under the whole footprint, so nothing outside the rooms is natural grass. */
	public static void floor(GameTestHelper h) {
		for (int x = 0; x <= 31; x++) for (int z = 0; z <= 31; z++) h.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
	}

	/** Torches every 4 blocks outside the rooms, so a dark yard does not spawn anything. */
	public static void lightYard(GameTestHelper h) {
		for (int x = 0; x <= 31; x += 4) for (int z = 0; z <= 31; z += 4) {
			boolean inRooms = x >= 7 && x <= 27 && z >= 2 && z <= 10;
			if (!inRooms) h.setBlock(new BlockPos(x, 0, z), Blocks.TORCH);
		}
	}

	/** A valid Barracks at {@link #BARRACKS}: {@code guards} grindstones and beds (up to 4), a chest, a torch. The marker is placed by {@link #placeBarracks}. */
	public static void buildBarracks(GameTestHelper h, int guards) {
		BuildingTestSupport.room(h, BARRACKS, 5, 4);
		for (int i = 0; i < guards; i++) {
			h.setBlock(grindstone(i), Blocks.GRINDSTONE);
			WorkerTestSupport.bed(h, bedFoot(i));
		}
		h.setBlock(chest(), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(BARRACKS, 4, 0, 1), Blocks.TORCH);
	}

	public static void placeBarracks(GameTestHelper h) {
		BuildingTestSupport.placeMarker(h, BuildingType.BARRACKS, barracksMarker(), Direction.NORTH);
	}

	public static void assertBarracksValid(GameTestHelper h) {
		BuildingTestSupport.assertStatus(h, barracksMarker(), MarkerStatus.VALID);
	}

	/** Claim, floor, tier Village, a Barracks with {@code guards} beds; the sequence ends when the Barracks is valid. */
	public static GameTestSequence scene(GameTestHelper h, int guards) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			floor(h);
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.VILLAGE);
			buildBarracks(h, guards);
			placeBarracks(h);
		}).thenWaitUntil(() -> assertBarracksValid(h));
	}

	public static Villager hireGuard(GameTestHelper h, BlockPos spawnRel, Optional<Shift> pin) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnRel);
		HireResult r = Workers.hire(h.getLevel(), v, JobType.GUARD, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the guard was hired: " + r);
		pin(h, v, pin);
		return v;
	}

	public static void pin(GameTestHelper h, Villager v, Optional<Shift> pin) {
		VillageClaims.data(h.getLevel().getServer()).update(s -> s.withGuardPin(v.getUUID(), pin));
	}

	public static void setTime(GameTestHelper h, long dayTime) { h.getLevel().setDayTime(dayTime); }

	public static ItemStack stack(net.minecraft.world.item.Item item) { return new ItemStack(item); }

	// ---- recorders and mobs -------------------------------------------------------------------------

	/** (victim, attacker) of every damage with a source entity since {@link #clearRecords}. */
	private static final java.util.List<java.util.UUID[]> DAMAGE = new java.util.ArrayList<>();
	/** Every villager that died since {@link #clearRecords}. */
	private static final java.util.List<java.util.UUID> VILLAGER_DEATHS = new java.util.ArrayList<>();
	private static boolean installed;

	/** Registers the test-wide recorders once (Fabric events cannot be removed); {@link #clearRecords} empties them per test. */
	public static void installRecorders() {
		if (!installed) {
			installed = true;
			net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((e, src, base, taken, blocked) -> {
				if (src.getEntity() != null) DAMAGE.add(new java.util.UUID[] {e.getUUID(), src.getEntity().getUUID()});
			});
			net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
				if (e instanceof Villager) VILLAGER_DEATHS.add(e.getUUID());
			});
		}
		clearRecords();
	}

	public static void clearRecords() {
		DAMAGE.clear();
		VILLAGER_DEATHS.clear();
	}

	/** Has {@code attacker} damaged {@code victim} since the records were cleared? */
	public static boolean damaged(net.minecraft.world.entity.Entity victim, net.minecraft.world.entity.Entity attacker) {
		return DAMAGE.stream().anyMatch(d -> d[0].equals(victim.getUUID()) && d[1].equals(attacker.getUUID()));
	}

	public static java.util.List<java.util.UUID> villagerDeaths() { return java.util.List.copyOf(VILLAGER_DEATHS); }

	/** An adult zombie that does not burn in daylight (iron helmet) and stays loaded. */
	public static net.minecraft.world.entity.monster.zombie.Zombie zombie(GameTestHelper h, BlockPos rel) {
		var z = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, rel);
		z.setBaby(false);
		z.setPersistenceRequired();
		z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
		h.assertTrue(!z.isBaby(), "the zombie is an adult");
		return z;
	}

	public static void arm(Villager g, net.minecraft.world.item.Item sword) {
		g.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(sword));
		g.setGuaranteedDrop(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
	}

	/** Puts a stack in a slot of the Barracks chest. */
	public static void putInChest(GameTestHelper h, int slot, ItemStack stack) { LumberTestSupport.putInChest(h, chest(), slot, stack); }
}
