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

	/** Job blocks whose POI ticket the mod holds (a copy). */
	public java.util.Set<HeartLocation> reservedJobBlocks() { return claim.reservedJobBlocks(); }

	public void setReservedJobBlocks(java.util.Set<HeartLocation> blocks) {
		claim.setReservedJobBlocks(blocks);
		setDirty();
	}

	/** Held job blocks whose ticket still has to be released once their chunk loads. */
	public java.util.Set<HeartLocation> pendingTicketReleases() { return claim.pendingTicketReleases(); }

	public void setPendingTicketReleases(java.util.Set<HeartLocation> blocks) {
		claim.setPendingTicketReleases(blocks);
		setDirty();
	}

	/** Day index of the last dinner, absent for a claim that has not seen one (M11). */
	public java.util.OptionalLong lastDinnerDay() { return claim.lastDinnerDay(); }

	public void setLastDinnerDay(long day) {
		claim.setLastDinnerDay(day);
		setDirty();
	}

	/** Who is paid and who strikes today (M12); absent for a claim that has not seen a wage tick. */
	public Optional<dev.villageheart.core.economy.Payroll> payroll() { return claim.payroll(); }

	public void setPayroll(dev.villageheart.core.economy.Payroll payroll) {
		claim.setPayroll(payroll);
		setDirty();
	}

	/** The market's reserves and today's sales (M22). */
	public dev.villageheart.core.market.MarketState market() { return claim.market(); }

	public void setMarket(dev.villageheart.core.market.MarketState m) {
		claim.setMarket(m);
		setDirty();
	}

	/** Population growth: today's licence, the last birth, the pairs that could not meet (M23). */
	public dev.villageheart.core.growth.GrowthState growth() { return claim.growth(); }

	public void setGrowth(dev.villageheart.core.growth.GrowthState g) {
		claim.setGrowth(g);
		setDirty();
	}

	/** Guide tasks the village has already done. */
	public dev.villageheart.core.guide.GuideProgress guide() { return claim.guide(); }

	public void setGuide(dev.villageheart.core.guide.GuideProgress g) {
		claim.setGuide(g);
		setDirty();
	}

	public int epoch() { return epoch; }

	public void clear() {
		epoch++;
		claim.clear();
		setDirty();
	}
}
