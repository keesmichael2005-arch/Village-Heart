package dev.villageheart.world;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.Balance;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidOutcome;
import dev.villageheart.core.raid.RaidPlan;
import dev.villageheart.core.raid.RaidRecord;
import dev.villageheart.core.raid.RaidSummary;
import dev.villageheart.core.siege.DefenceDamage;
import dev.villageheart.core.siege.PendingRaid;
import dev.villageheart.core.siege.SiegeRecord;
import dev.villageheart.core.siege.ThreatState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The {@code threats} save object (docs/plans/M25.md 3.4). A bad value drops that value and keeps the village, with one warning
 * counted in {@link #loadWarnings}.
 */
public final class ThreatCodecs {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("villageheart");

	/** Warnings from the last loads. Tests reset it; a malformed damage array adds one. */
	public static int loadWarnings;

	private ThreatCodecs() {}

	public static void warn(String message) {
		loadWarnings++;
		LOG.warn("Village save: {}", message);
	}

	/**
	 * A field that reads as {@code fallback} when it is missing or unreadable. An unreadable value warns once and is dropped.
	 * {@link com.mojang.serialization.MapCodec#orElse} does not run its consumer on this DFU version, so the warning is here.
	 */
	static <A> com.mojang.serialization.MapCodec<A> lenient(Codec<A> codec, String name, A fallback) {
		return new com.mojang.serialization.MapCodec<>() {
			@Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString(name)); }

			@Override public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
				T value = input.get(name);
				if (value == null) return DataResult.success(fallback);
				DataResult<A> parsed = codec.parse(ops, value);
				if (parsed.error().isPresent()) {
					warn(name + ": " + parsed.error().get().message());
					return DataResult.success(parsed.result().orElse(fallback));
				}
				return DataResult.success(parsed.result().orElse(fallback));
			}

			@Override public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				if (input == null || input.equals(fallback)) return prefix;
				return prefix.add(name, input, codec);
			}
		};
	}

	/** Like {@link #lenient}, for a value that is simply absent when it is missing or unreadable. */
	static <A> com.mojang.serialization.MapCodec<Optional<A>> lenientOptional(Codec<A> codec, String name) {
		return new com.mojang.serialization.MapCodec<>() {
			@Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString(name)); }

			@Override public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
				T value = input.get(name);
				if (value == null) return DataResult.success(Optional.empty());
				DataResult<A> parsed = codec.parse(ops, value);
				if (parsed.error().isPresent()) {
					warn(name + ": " + parsed.error().get().message());
					return DataResult.success(Optional.empty());
				}
				return DataResult.success(Optional.of(parsed.result().orElseThrow()));
			}

			@Override public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				return input.isEmpty() ? prefix : prefix.add(name, input.get(), codec);
			}
		};
	}

	private static <E extends Enum<E>> Codec<E> enumId(Class<E> type, java.util.function.Function<E, String> id,
			java.util.function.Function<String, Optional<E>> byId) {
		return Codec.STRING.comapFlatMap(
				s -> byId.apply(s).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown " + type.getSimpleName() + " " + s)),
				id);
	}

	private static final Codec<RaidOrigin> ORIGIN = enumId(RaidOrigin.class, RaidOrigin::id, RaidOrigin::byId);
	private static final Codec<RaidOutcome> OUTCOME = enumId(RaidOutcome.class, RaidOutcome::id, RaidOutcome::byId);

	private record RawPlan(int omen, int extraVindicators, int extraRavagers, int waveCap, boolean early) {}

	/** A plan that will not construct (omen 9, a negative count) is an error, so the raid around it is dropped. */
	private static final Codec<RaidPlan> PLAN = RecordCodecBuilder.<RawPlan>create(i -> i.group(
			Codec.INT.optionalFieldOf("omen", 1).forGetter(RawPlan::omen),
			Codec.INT.optionalFieldOf("extra_vindicators", 0).forGetter(RawPlan::extraVindicators),
			Codec.INT.optionalFieldOf("extra_ravagers", 0).forGetter(RawPlan::extraRavagers),
			Codec.INT.optionalFieldOf("wave_cap", 0).forGetter(RawPlan::waveCap),
			Codec.BOOL.optionalFieldOf("early", false).forGetter(RawPlan::early)
	).apply(i, RawPlan::new)).flatXmap(r -> {
		try {
			return DataResult.success(new RaidPlan(r.omen(), r.extraVindicators(), r.extraRavagers(), r.waveCap(), r.early()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(e::getMessage);
		}
	}, p -> DataResult.success(new RawPlan(p.omen(), p.extraVindicators(), p.extraRavagers(), p.waveCap(), p.early())));

	private record RaidA(int vanillaId, RaidOrigin origin, RaidPlan plan, long startedAt, int startDay, int villagersAtStart,
			int guardsAtStart, int guardsQualifiedAtStart, int guardsWoundedAtStart, int guardsUnloadedAtStart) {}

	private record RaidB(int wavesTotal, int wavesSpawned, int raidersAlive, boolean betweenWaves, int villagerDeaths, int raidersKilled,
			int extrasForWave, int extraRaidersKilled, long allGuardsOnDutyAt) {}

	private static final com.mojang.serialization.MapCodec<RaidA> RAID_A = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.fieldOf("vanilla_id").forGetter(RaidA::vanillaId),
			ORIGIN.fieldOf("origin").forGetter(RaidA::origin),
			PLAN.optionalFieldOf("plan", RaidPlan.NONE).forGetter(RaidA::plan),
			Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(RaidA::startedAt),
			Codec.INT.optionalFieldOf("start_day", 1).forGetter(RaidA::startDay),
			Codec.INT.optionalFieldOf("villagers_at_start", 0).forGetter(RaidA::villagersAtStart),
			Codec.INT.optionalFieldOf("guards_at_start", 0).forGetter(RaidA::guardsAtStart),
			Codec.INT.optionalFieldOf("guards_qualified_at_start", 0).forGetter(RaidA::guardsQualifiedAtStart),
			Codec.INT.optionalFieldOf("guards_wounded_at_start", 0).forGetter(RaidA::guardsWoundedAtStart),
			Codec.INT.optionalFieldOf("guards_unloaded_at_start", 0).forGetter(RaidA::guardsUnloadedAtStart)
	).apply(i, RaidA::new));

	private static final com.mojang.serialization.MapCodec<RaidB> RAID_B = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("waves_total", 0).forGetter(RaidB::wavesTotal),
			Codec.INT.optionalFieldOf("waves_spawned", 0).forGetter(RaidB::wavesSpawned),
			Codec.INT.optionalFieldOf("raiders_alive", 0).forGetter(RaidB::raidersAlive),
			Codec.BOOL.optionalFieldOf("between_waves", false).forGetter(RaidB::betweenWaves),
			Codec.INT.optionalFieldOf("villager_deaths", 0).forGetter(RaidB::villagerDeaths),
			Codec.INT.optionalFieldOf("raiders_killed", 0).forGetter(RaidB::raidersKilled),
			Codec.INT.optionalFieldOf("extras_for_wave", 0).forGetter(RaidB::extrasForWave),
			Codec.INT.optionalFieldOf("extra_raiders_killed", 0).forGetter(RaidB::extraRaidersKilled),
			Codec.LONG.optionalFieldOf("all_guards_on_duty_at", -1L).forGetter(RaidB::allGuardsOnDutyAt)
	).apply(i, RaidB::new));

	public static final Codec<RaidRecord> RAID = Codec.mapPair(RAID_A, RAID_B).codec().flatXmap(
			p -> {
				try {
					return DataResult.success(new RaidRecord(p.getFirst().vanillaId(), p.getFirst().origin(), p.getFirst().plan(), p.getFirst().startedAt(), p.getFirst().startDay(),
							p.getFirst().villagersAtStart(), p.getFirst().guardsAtStart(), p.getFirst().guardsQualifiedAtStart(), p.getFirst().guardsWoundedAtStart(),
							p.getFirst().guardsUnloadedAtStart(), p.getSecond().wavesTotal(), p.getSecond().wavesSpawned(), p.getSecond().raidersAlive(),
							p.getSecond().betweenWaves(), p.getSecond().villagerDeaths(), p.getSecond().raidersKilled(), p.getSecond().extrasForWave(),
							p.getSecond().extraRaidersKilled(), p.getSecond().allGuardsOnDutyAt()));
				} catch (IllegalArgumentException e) {
					return DataResult.error(e::getMessage);
				}
			},
			r -> DataResult.success(Pair.of(new RaidA(r.vanillaId(), r.origin(), r.plan(), r.startedAt(), r.startDay(), r.villagersAtStart(), r.guardsAtStart(),
					r.guardsQualifiedAtStart(), r.guardsWoundedAtStart(), r.guardsUnloadedAtStart()),
					new RaidB(r.wavesTotal(), r.wavesSpawned(), r.raidersAlive(), r.betweenWaves(), r.villagerDeaths(), r.raidersKilled(),
							r.extrasForWave(), r.extraRaidersKilled(), r.allGuardsOnDutyAt()))));

	public static final Codec<RaidSummary> SUMMARY = RecordCodecBuilder.<RaidSummary>create(i -> i.group(
			Codec.INT.optionalFieldOf("start_day", 1).forGetter(RaidSummary::startDay),
			Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(RaidSummary::startedAt),
			Codec.LONG.optionalFieldOf("ended_at", 0L).forGetter(RaidSummary::endedAt),
			OUTCOME.fieldOf("outcome").forGetter(RaidSummary::outcome),
			ORIGIN.fieldOf("origin").forGetter(RaidSummary::origin),
			Codec.INT.optionalFieldOf("waves_spawned", 0).forGetter(RaidSummary::wavesSpawned),
			Codec.INT.optionalFieldOf("waves_total", 0).forGetter(RaidSummary::wavesTotal),
			Codec.INT.optionalFieldOf("villagers_at_start", 0).forGetter(RaidSummary::villagersAtStart),
			Codec.INT.optionalFieldOf("villagers_at_end", 0).forGetter(RaidSummary::villagersAtEnd),
			Codec.INT.optionalFieldOf("villager_deaths", 0).forGetter(RaidSummary::villagerDeaths),
			Codec.INT.optionalFieldOf("raiders_killed", 0).forGetter(RaidSummary::raidersKilled),
			Codec.INT.optionalFieldOf("reward", 0).forGetter(RaidSummary::reward)
	).apply(i, RaidSummary::new)).flatXmap(s -> {
		try {
			return DataResult.success(s);
		} catch (IllegalArgumentException e) {
			return DataResult.error(e::getMessage);
		}
	}, DataResult::success);

	private static final Codec<PendingRaid> PENDING = RecordCodecBuilder.<PendingRaid>create(i -> i.group(
			Codec.INT.fieldOf("rolled_day").forGetter(PendingRaid::rolledDay),
			Codec.INT.fieldOf("start_tick").forGetter(PendingRaid::startTick)
	).apply(i, PendingRaid::new)).flatXmap(p -> p.startTick() < 0 || p.startTick() > 24000
			? DataResult.error(() -> "pending raid start_tick " + p.startTick() + " is out of range")
			: DataResult.success(p), DataResult::success);

	public static final Codec<SiegeRecord> SIEGE = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("start_day", 0).forGetter(SiegeRecord::startDay),
			VillageCodecs.lenientList(VillageCodecs.UUID_STRING).optionalFieldOf("zombies", List.of()).forGetter(SiegeRecord::zombieIds),
			Codec.INT.optionalFieldOf("spawned", 0).forGetter(SiegeRecord::spawned),
			Codec.INT.optionalFieldOf("killed", 0).forGetter(SiegeRecord::killed),
			Codec.INT.optionalFieldOf("villager_deaths", 0).forGetter(SiegeRecord::villagerDeaths)
	).apply(i, SiegeRecord::new));

	private record RawRolls(int raid, int siege) {}

	private static final Codec<RawRolls> ROLLS = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("raid", ThreatState.NO_DAY).forGetter(RawRolls::raid),
			Codec.INT.optionalFieldOf("siege", ThreatState.NO_DAY).forGetter(RawRolls::siege)
	).apply(i, RawRolls::new));

	private record RawDamage(List<Long> cracked, List<Long> breached) {}

	private static final Codec<DefenceDamage> DAMAGE = RecordCodecBuilder.<RawDamage>create(i -> i.group(
			Codec.LONG.listOf().optionalFieldOf("cracked", List.of()).forGetter(RawDamage::cracked),
			Codec.LONG.listOf().optionalFieldOf("breached", List.of()).forGetter(RawDamage::breached)
	).apply(i, RawDamage::new)).flatXmap(raw -> {
		if ((raw.cracked().size() & 1) == 1) return DataResult.error(() -> "odd-length damage array (" + raw.cracked().size() + ")");
		Map<Long, Integer> cracked = new LinkedHashMap<>();
		for (int n = 0; n < raw.cracked().size(); n += 2) {
			long dmg = raw.cracked().get(n + 1);
			if (dmg < 1 || dmg > Balance.HP_STONE - 1) return DataResult.error(() -> "out-of-range damage " + dmg);
			cracked.putIfAbsent(raw.cracked().get(n), (int) dmg);
		}
		DefenceDamage.Capped capped = DefenceDamage.cap(cracked, raw.breached());
		if (capped.trimmed() > 0) warn("damage has " + (capped.damage().size() + capped.trimmed()) + " entries; keeping " + Balance.MAX_DAMAGED_CELLS + " (breaches first)");
		return DataResult.success(capped.damage());
	}, d -> DataResult.success(new RawDamage(d.flatCracked(), sorted(d.breached()))));

	private static List<Long> sorted(List<Long> in) {
		List<Long> out = new ArrayList<>(in);
		out.sort(Long::compare);
		return out;
	}

	private record RawThreat(Optional<RaidRecord> raid, Optional<RaidSummary> lastRaid, Optional<PendingRaid> pending, Optional<SiegeRecord> siege,
			Optional<SiegeRecord> lastSiege, int lastRaidEndDay, int lastSiegeDay, int lastRewardDay, RawRolls rolls, DefenceDamage damage) {}

	public static final Codec<ThreatState> THREATS = RecordCodecBuilder.<RawThreat>create(i -> i.group(
			lenientOptional(RAID, "raid").forGetter(RawThreat::raid),
			lenientOptional(SUMMARY, "last_raid").forGetter(RawThreat::lastRaid),
			lenientOptional(PENDING, "pending").forGetter(RawThreat::pending),
			lenientOptional(SIEGE, "siege").forGetter(RawThreat::siege),
			lenientOptional(SIEGE, "last_siege").forGetter(RawThreat::lastSiege),
			Codec.INT.optionalFieldOf("last_raid_end_day", ThreatState.NO_DAY).forGetter(RawThreat::lastRaidEndDay),
			Codec.INT.optionalFieldOf("last_siege_day", ThreatState.NO_DAY).forGetter(RawThreat::lastSiegeDay),
			Codec.INT.optionalFieldOf("last_reward_day", ThreatState.NO_DAY).forGetter(RawThreat::lastRewardDay),
			lenient(ROLLS, "last_rolled_day", new RawRolls(ThreatState.NO_DAY, ThreatState.NO_DAY)).forGetter(RawThreat::rolls),
			lenient(DAMAGE, "damage", DefenceDamage.EMPTY).forGetter(RawThreat::damage)
	).apply(i, RawThreat::new)).xmap(
			r -> new ThreatState(r.raid(), r.lastRaid(), r.pending(), r.siege(), r.lastSiege(), r.lastRaidEndDay(), r.lastSiegeDay(), r.lastRewardDay(),
					r.rolls().raid(), r.rolls().siege(), r.damage()),
			t -> new RawThreat(t.raid(), t.lastRaid(), t.pending(), t.siege(), t.lastSiege(), t.lastRaidEndDay(), t.lastSiegeDay(), t.lastRewardDay(),
					new RawRolls(t.lastRaidRollDay(), t.lastSiegeRollDay()), t.damage()));

	/** Sorted ground-relative positions, for the defence-site codec. */
	public static List<Long> sortedSet(java.util.Set<Long> in) {
		List<Long> out = new ArrayList<>(in);
		out.sort(Long::compare);
		return out;
	}

	static java.util.Set<Long> setOf(List<Long> in) {
		return java.util.Set.copyOf(new java.util.LinkedHashSet<>(in));
	}
}
