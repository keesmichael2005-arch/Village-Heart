package dev.villageheart.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkerData;

/** Codecs for the worker attachment. DFU only, so they unit-test with JsonOps. Field names are part of the save format. */
public final class WorkerCodecs {
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
		try {
			return DataResult.success(new Needs(r.fed(), r.hungryDays(), r.housed(), r.paid()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid needs: " + e.getMessage());
		}
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
