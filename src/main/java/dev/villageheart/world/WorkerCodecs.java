package dev.villageheart.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkerData;

/** Codecs for the worker attachment. DFU only, so they unit-test with JsonOps. Field names are part of the save format. */
public final class WorkerCodecs {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("villageheart");

	private WorkerCodecs() {}

	/** Unvalidated shapes, so out-of-range values become DataResult errors instead of exceptions. */
	private record RawNeeds(boolean fed, int hungryDays, boolean housed, boolean paid) {}

	private static final Codec<RawNeeds> RAW_NEEDS = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("fed", Needs.DEFAULT.fed()).forGetter(RawNeeds::fed),
			Codec.INT.optionalFieldOf("hungry_days", Needs.DEFAULT.hungryDays()).forGetter(RawNeeds::hungryDays),
			Codec.BOOL.optionalFieldOf("housed", Needs.DEFAULT.housed()).forGetter(RawNeeds::housed),
			Codec.BOOL.optionalFieldOf("paid", Needs.DEFAULT.paid()).forGetter(RawNeeds::paid)
	).apply(i, RawNeeds::new));

	public static final Codec<Needs> NEEDS = RAW_NEEDS.flatXmap(r -> {
		if (r.hungryDays() < 0) return DataResult.error(() -> "Invalid needs: hungryDays must be >= 0: " + r.hungryDays());
		// Lenient: a save whose fed flag contradicts hungry_days is repaired (hungry_days wins). An error here would make Fabric drop the
		// whole worker attachment and silently fire the worker.
		if (r.fed() != (r.hungryDays() == 0)) {
			LOG.warn("Worker needs: fed={} contradicts hungry_days={}; trusting hungry_days", r.fed(), r.hungryDays());
		}
		return DataResult.success(Needs.of(r.hungryDays(), r.housed(), r.paid()));
	}, n -> DataResult.success(new RawNeeds(n.fed(), n.hungryDays(), n.housed(), n.paid())));

	private record Raw(String job, HeartLocation building, HeartLocation jobBlock, int level, int xp, Needs needs) {}

	private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("job").forGetter(Raw::job),
			VillageCodecs.HEART_LOCATION.fieldOf("building").forGetter(Raw::building),
			VillageCodecs.HEART_LOCATION.fieldOf("job_block").forGetter(Raw::jobBlock),
			Codec.INT.optionalFieldOf("level", 1).forGetter(Raw::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(Raw::xp),
			NEEDS.optionalFieldOf("needs", Needs.DEFAULT).forGetter(Raw::needs)
	).apply(i, Raw::new));

	public static final Codec<WorkerData> WORKER_DATA = RAW.flatXmap(r -> {
		try {
			return DataResult.success(new WorkerData(r.job(), r.building(), r.jobBlock(), r.level(), r.xp(), r.needs()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid worker: " + e.getMessage());
		}
	}, w -> DataResult.success(new Raw(w.jobId(), w.building(), w.jobBlock(), w.level(), w.xp(), w.needs())));
}
