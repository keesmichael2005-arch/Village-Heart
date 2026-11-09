package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.TreeTask;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tier.TierProgress;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M14: the tier and its progress in the village state and its save. */
class VillageStateTierTest {
	static final String DIM = "minecraft:overworld";
	static final HeartLocation HEART = new HeartLocation(DIM, 0, 64, 0);
	static final HeartLocation CAMP = new HeartLocation(DIM, 20, 64, 0);
	static final UUID OWNER = new UUID(0, 1);
	static final TierProgress P = new TierProgress(7, 41, 33);

	static VillageState base() { return VillageState.founded(HEART, OWNER, "o", 0); }

	@Test void foundedIsHamletWithInitialProgress() {
		assertEquals(Tier.HAMLET, base().tier());
		assertEquals(TierProgress.INITIAL, base().tierProgress());
	}

	@Test void tierUpWalksAndThrowsAtTown() {
		VillageState v = base().tierUp();
		assertEquals(Tier.VILLAGE, v.tier());
		assertEquals(Tier.TOWN, v.tierUp().tier());
		assertThrows(IllegalStateException.class, () -> v.tierUp().tierUp());
	}

	@Test void oldConstructorsGiveInitialProgress() {
		VillageState v = new VillageState(HEART, OWNER, "o", 0, Map.of(), List.of(), 0, Tier.HAMLET, List.of(), List.of());
		assertEquals(TierProgress.INITIAL, v.tierProgress());
	}

	private static Object arg(Class<?> t) {
		if (t == UUID.class) return new UUID(9, 9);
		if (t == String.class) return "x";
		if (t == long.class) return 5L;
		if (t == int.class) return 1;
		if (t == Rank.class) return Rank.RESIDENT;
		if (t == Tier.class) return Tier.VILLAGE;
		if (t == HeartLocation.class) return new HeartLocation(DIM, 99, 64, 99);
		if (t == TierProgress.class) return TierProgress.INITIAL;
		if (t == BuildingRecord.class) return new BuildingRecord("storage", new HeartLocation(DIM, 5, 64, 5));
		if (t == WorkerRecord.class) return new WorkerRecord(new UUID(3, 3), "farmer", new HeartLocation(DIM, 5, 64, 5), new HeartLocation(DIM, 6, 64, 5));
		if (t == TreeTask.class) return new TreeTask(CAMP, TreeTask.Stage.FELLING, List.of(new HeartLocation(DIM, 21, 64, 1)), "minecraft:oak_sapling", 1L,
				new Pos(19, 64, -1), new Pos(23, 72, 3));
		if (t == BuildJob.class) return new BuildJob(new UUID(5, 1), "wall_segment", BlueprintParams.NONE, new HeartLocation(DIM, 10, 64, 3), Rotation4.CLOCKWISE_90,
				100L, OWNER, 0, Map.of(), null);
		if (t == DefenceSite.class) return new DefenceSite(new UUID(1, 1), "lamp_post", new HeartLocation(DIM, 5, 64, 6), Rotation4.CLOCKWISE_90, 77L);
		if (t == SelfCraft.class) return new SelfCraft(new UUID(4, 4), new GearCraft("minecraft:stone_pickaxe",
				new OrderKey(new HeartLocation(DIM, 5, 64, 5), GearNeed.AXE, 0), 0, List.of()), false);
		if (t == java.util.Optional.class) return java.util.Optional.of(dev.villageheart.core.guard.Shift.DAY); // withGuardPin (M18)
		if (t == Map.class) return Map.of(new UUID(3, 3), new dev.villageheart.core.guard.GuardShift(dev.villageheart.core.guard.Shift.DAY, true)); // withGuardShifts (M18)
		throw new AssertionError("add a fixture for " + t.getName() + " to VillageStateTierTest.arg");
	}

	/** Every method that rebuilds the state must carry the progress: a new with* that forgets it fails here. */
	@Test void everyWithMethodKeepsTheProgress() throws Exception {
		VillageState seed = base().withTierProgress(P).withTreasury(5);
		List<String> checked = new ArrayList<>();
		for (Method m : VillageState.class.getDeclaredMethods()) {
			if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers()) || m.getReturnType() != VillageState.class) continue;
			if (m.getName().equals("withTierProgress")) continue;
			Object[] args = new Object[m.getParameterCount()];
			for (int i = 0; i < args.length; i++) args[i] = arg(m.getParameterTypes()[i]);
			VillageState out;
			try {
				out = (VillageState) m.invoke(seed, args);
			} catch (InvocationTargetException e) {
				if (m.getName().equals("transferOwnership")) continue; // a stranger cannot take over; covered by the members tests
				throw e;
			}
			assertEquals(P, out.tierProgress(), m.getName());
			checked.add(m.getName());
		}
		assertTrue(checked.containsAll(List.of("withTier", "tierUp", "withTreeTask", "withBuild", "withDefence", "withSelfCraft", "withTreasury", "withWorker")), checked.toString());
		// the add methods really changed something (so the sweep did not just get "this" back)
		assertNotEquals(seed, seed.withTreeTask((TreeTask) arg(TreeTask.class)));
		assertNotEquals(seed, seed.withBuild((BuildJob) arg(BuildJob.class)));
		assertNotEquals(seed, seed.withDefence((DefenceSite) arg(DefenceSite.class)));
		assertNotEquals(seed, seed.withSelfCraft((SelfCraft) arg(SelfCraft.class)));
	}

	@Test void withoutMethodsKeepTheProgressWhenTheyChangeSomething() {
		VillageState v = base().withTierProgress(P).withTreeTask((TreeTask) arg(TreeTask.class)).withBuild((BuildJob) arg(BuildJob.class))
				.withDefence((DefenceSite) arg(DefenceSite.class)).withSelfCraft((SelfCraft) arg(SelfCraft.class));
		assertEquals(P, v.withoutTreeTask(CAMP).tierProgress());
		assertEquals(P, v.withoutBuild(new UUID(5, 1)).tierProgress());
		assertEquals(P, v.withoutDefence(new UUID(1, 1)).tierProgress());
		assertEquals(P, v.withoutSelfCraft(new UUID(4, 4)).tierProgress());
		assertEquals(0, v.withoutBuild(new UUID(5, 1)).builds().size());
	}

	// ---- the save ----

	static final String BASE = VillageCodecsTest.BASE;

	static VillageState parse(String extra) {
		return VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + BASE + extra + "}")).getOrThrow();
	}

	@Test void roundTripAndPinnedKeys() {
		VillageState v = base().withTierProgress(P);
		JsonElement e = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow();
		assertEquals(v, VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, e).getOrThrow());
		var tp = e.getAsJsonObject().getAsJsonObject("tier_progress");
		assertEquals(java.util.Set.of("fed_streak", "last_dinner_day", "best_dawn_treasury"), tp.keySet());
		assertEquals(7, tp.get("fed_streak").getAsInt());
		assertEquals(41, tp.get("last_dinner_day").getAsLong());
		assertEquals(33, tp.get("best_dawn_treasury").getAsInt());
	}

	@Test void noDayIsNotWritten() {
		VillageState v = base().withTierProgress(new TierProgress(2, TierProgress.NO_DAY, 0));
		var tp = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject().getAsJsonObject("tier_progress");
		assertFalse(tp.has("last_dinner_day"));
		assertEquals(v.tierProgress(), parse(",\"tier_progress\":{\"fed_streak\":2}").tierProgress());
	}

	@Test void initialProgressOmitsTheKeyOrReadsBack() {
		assertEquals(TierProgress.INITIAL, parse("").tierProgress());
		VillageState v = base();
		assertEquals(v, VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow()).getOrThrow());
	}

	@Test void badProgressNeverDropsTheVillage() {
		assertEquals(0, parse(",\"tier_progress\":{\"fed_streak\":-3}").tierProgress().fedStreak());
		assertEquals(0, parse(",\"tier_progress\":{\"fed_streak\":\"x\"}").tierProgress().fedStreak());
		assertEquals(0, parse(",\"tier_progress\":{\"best_dawn_treasury\":-1}").tierProgress().bestDawnTreasury());
		assertEquals(TierProgress.INITIAL, parse(",\"tier_progress\":{}").tierProgress());
		assertEquals(TierProgress.INITIAL, parse(",\"tier_progress\":5").tierProgress());
		assertEquals(4, parse(",\"tier_progress\":{\"fed_streak\":4,\"last_dinner_day\":\"bad\"}").tierProgress().fedStreak());
	}

	/** The village codec is a flat record of at most 16 fields (RecordCodecBuilder.group); this pins the top-level key set so a split must keep it. */
	@Test void topLevelKeysStayFlat() {
		VillageState v = base().withTierProgress(P).withTreasury(5).withTier(Tier.VILLAGE);
		var keys = VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, v).getOrThrow().getAsJsonObject().keySet();
		assertTrue(keys.containsAll(java.util.Set.of("heart", "owner", "owner_name", "claimed_at", "treasury", "tier", "tier_progress")), keys.toString());
	}
}
