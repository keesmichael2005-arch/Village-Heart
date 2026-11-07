package dev.villageheart.world;

import com.mojang.serialization.Codec;
import dev.villageheart.core.ClaimRules;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorldClaim;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** The one village claim of the world, stored in the overworld's data folder as villageheart_village.dat. */
public class VillageSavedData extends SavedData {
	public static final Codec<VillageSavedData> CODEC =
			VillageCodecs.WORLD_CLAIM.xmap(VillageSavedData::new, VillageSavedData::claimView);
	// DataFixTypes is null on purpose: Fabric API's DimensionDataStorageMixin skips the fixer for null.
	public static final SavedDataType<VillageSavedData> TYPE =
			new SavedDataType<>("villageheart_village", VillageSavedData::new, CODEC, null);

	private final WorldClaim claim;
	/** Not saved: bumped on every claim, release and clear, so transient caches keyed to a claim never outlive it. */
	private int epoch;

	public VillageSavedData() {
		this(new WorldClaim());
	}

	private VillageSavedData(WorldClaim claim) {
		this.claim = claim;
	}

	/** For the codec only. Callers must not mutate it. */
	private WorldClaim claimView() {
		return claim;
	}

	public Optional<VillageState> village() {
		return claim.village();
	}

	public ClaimRules.Result evaluate(double distanceToBell) {
		return claim.evaluate(distanceToBell);
	}

	public ClaimRules.Result claim(HeartLocation at, UUID owner, String ownerName, double distanceToBell, long gameTime) {
		ClaimRules.Result r = claim.claim(at, owner, ownerName, distanceToBell, gameTime);
		if (r == ClaimRules.Result.CLAIMED) {
			epoch++;
			setDirty();
		}
		return r;
	}

	public Optional<VillageState> releaseAt(HeartLocation at) {
		Optional<VillageState> r = claim.releaseAt(at);
		if (r.isPresent()) {
			epoch++;
			setDirty();
		}
		return r;
	}

	public WorldClaim.BreakDecision mayBreakHeart(HeartLocation at, UUID player) {
		return claim.mayBreakHeart(at, player);
	}

	public void update(UnaryOperator<VillageState> f) {
		claim.update(f);
		setDirty();
	}

	public int epoch() { return epoch; }

	public void clear() {
		epoch++;
		claim.clear();
		setDirty();
	}
}
