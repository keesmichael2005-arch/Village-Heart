package dev.villageheart.building;

import dev.villageheart.block.MarkerBlock;
import dev.villageheart.core.Balance;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingRules;
import dev.villageheart.core.building.BuildingRules.CheckContext;
import dev.villageheart.core.building.BuildingRules.SiteFacts;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.Links;
import dev.villageheart.core.building.Reason;
import dev.villageheart.core.building.RoomFacts;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.building.VerdictPolicy;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomProblem;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.room.SharedRooms;
import dev.villageheart.room.RoomScans;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Checks one registered building against the world and applies the verdict (DESIGN section 3.2). */
final class BuildingValidator {
	private BuildingValidator() {}

	/**
	 * Returns the verdict, or null when nothing was checked (not registered, chunk not loaded, or the marker is gone and was
	 * unregistered). A deferred verdict changes nothing.
	 */
	@Nullable
	static Verdict validate(MinecraftServer server, BuildingRuntime rt, HeartLocation loc) {
		Optional<VillageState> villageOpt = VillageClaims.current(server);
		if (villageOpt.isEmpty()) return null;
		VillageState village = villageOpt.get();
		BuildingRecord record = village.building(loc).orElse(null);
		if (record == null) return null;
		ServerLevel level = BuildingSystem.levelOf(server, loc);
		if (level == null) return null;
		BlockPos pos = new BlockPos(loc.x(), loc.y(), loc.z());
		if (!BuildingSystem.isLoaded(level, pos)) return null; // never load a chunk; the last verdict stays
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof MarkerBlock marker) || !marker.type().id().equals(record.typeId())) {
			BuildingSystem.unregister(rt, loc, record); // orphan: removed without a neighbour update
			return null;
		}
		BuildingType type = marker.type();
		Reason claimArea = dev.villageheart.core.building.ClaimArea.check(village.heart(), loc, type);
		Pos here = new Pos(loc.x(), loc.y(), loc.z());

		Verdict verdict;
		if (type.kind() == BuildingKind.ROOM) {
			RoomScan raw = RoomScans.scan(level, pos, state.getValue(MarkerBlock.FACING));
			rt.rawScans.put(loc, raw);
			if (raw.enclosed()) rt.lastEnclosed.put(loc, raw);
			RoomScan shared = applyShared(rt, loc);
			RoomProblem notLoaded = shared.problems().stream().filter(p -> p.kind() == RoomProblem.Kind.NOT_LOADED).findFirst().orElse(null);
			if (notLoaded != null) {
				verdict = Verdict.deferred(Reason.notLoaded(notLoaded.message()));
			} else {
				int linked = type == BuildingType.MINE || type == BuildingType.HUNTING_LODGE ? validLinkedSites(village, record, type) : 0;
				verdict = BuildingRules.checkRoom(type, RoomFacts.of(shared), new CheckContext(village.tier(), claimArea, null, linked));
			}
		} else {
			verdict = siteVerdict(level, village, rt, record, type, here, claimArea);
		}
		rt.verdicts.put(loc, verdict);
		if (verdict.deferred()) return verdict;
		apply(level, pos, state, rt, record, verdict);
		return verdict;
	}

	private static Verdict siteVerdict(ServerLevel level, VillageState village, BuildingRuntime rt, BuildingRecord record,
			BuildingType type, Pos here, @Nullable Reason claimArea) {
		SiteSurveys.Survey survey = SiteSurveys.survey(level, type, here, homeRooms(village, rt));
		if (survey.notLoaded() != null) {
			return Verdict.deferred(Reason.notLoaded(new RoomProblem(RoomProblem.Kind.NOT_LOADED, survey.notLoaded()).message()));
		}
		BuildingType parent = type.parent().orElseThrow();
		int range = type == BuildingType.MINESHAFT ? Balance.MINESHAFT_LINK_RANGE : Integer.MAX_VALUE;
		List<Links.Candidate> candidates = new ArrayList<>();
		for (BuildingRecord b : village.buildings()) {
			if (b.typeId().equals(parent.id())) candidates.add(new Links.Candidate(new Pos(b.marker().x(), b.marker().y(), b.marker().z()), b.marker().dimension()));
		}
		Pos link = Links.nearest(here, record.marker().dimension(), candidates, range);
		return BuildingRules.checkSite(type, survey.facts(), new CheckContext(village.tier(), claimArea, link, 0));
	}

	/** The cached raw scans of registered Houses and Farmhouses (the Hunting Ground may not sit inside them). */
	private static List<RoomScan> homeRooms(VillageState village, BuildingRuntime rt) {
		List<RoomScan> out = new ArrayList<>();
		for (BuildingRecord b : village.buildings()) {
			if (b.typeId().equals(BuildingType.HOUSE.id()) || b.typeId().equals(BuildingType.FARMHOUSE.id())) {
				RoomScan s = rt.rawScans.get(b.marker());
				if (s != null) out.add(s);
			}
		}
		return out;
	}

	private static int validLinkedSites(VillageState village, BuildingRecord parent, BuildingType parentType) {
		BuildingType site = BuildingRequirements.of(parentType).requiredSite();
		int n = 0;
		for (BuildingRecord b : village.buildings()) {
			if (b.typeId().equals(site.id()) && b.status() == BuildingStatus.VALID && parent.marker().equals(b.link())) n++;
		}
		return n;
	}

	/**
	 * The shared-room rule across the registry: apply it to a copy of the cached raw scans (only enclosed ones matter), and
	 * prioritise the room partners only when this building's set of partners changed.
	 */
	private static RoomScan applyShared(BuildingRuntime rt, HeartLocation loc) {
		Map<HeartLocation, RoomScan> copy = new LinkedHashMap<>();
		for (var e : rt.rawScans.entrySet()) if (e.getValue().marker() != null) copy.put(e.getKey(), e.getValue());
		RoomScan mine = rt.rawScans.get(loc);
		Set<HeartLocation> partners = new HashSet<>();
		if (mine.enclosed()) {
			for (var e : copy.entrySet()) {
				if (!e.getKey().equals(loc) && e.getValue().enclosed() && e.getValue().roomKey() == mine.roomKey()) partners.add(e.getKey());
			}
		}
		Set<HeartLocation> before = rt.sharedWith.getOrDefault(loc, Set.of());
		if (!partners.equals(before)) {
			rt.sharedWith.put(loc, partners);
			for (HeartLocation p : partners) rt.schedule.prioritise(p);
			for (HeartLocation p : before) rt.schedule.prioritise(p);
		}
		return SharedRooms.apply(copy).get(loc);
	}

	private static void apply(ServerLevel level, BlockPos pos, BlockState state, BuildingRuntime rt, BuildingRecord record, Verdict verdict) {
		BuildingSystem.setStatus(level, pos, state, verdict.status());
		VerdictPolicy.Outcome out = VerdictPolicy.apply(record, verdict);
		if (!out.changed()) return;
		MinecraftServer server = level.getServer();
		VillageClaims.data(server).update(s -> s.building(record.marker()).isPresent() ? s.withBuilding(out.record()) : s);
		// a site that changed status or link: its old and new parent want to know
		if (record.type().map(t -> t.kind() == BuildingKind.OUTDOOR_SITE).orElse(false)) {
			if (record.link() != null) rt.schedule.prioritise(record.link());
			if (out.record().link() != null) rt.schedule.prioritise(out.record().link());
		}
	}
}
