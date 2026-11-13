package dev.villageheart.guide;

import dev.villageheart.core.Balance;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.MaterialList;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.economy.Payroll;
import dev.villageheart.core.economy.WageRules;
import dev.villageheart.core.guide.GuideBook;
import dev.villageheart.core.guide.GuideProgress;
import dev.villageheart.core.guide.GuideSignals;
import dev.villageheart.core.guide.GuideTask;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.heart.net.GuideToastPayload;
import dev.villageheart.heart.net.HeartNetwork;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.Workers;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;

/** Latches guide tasks from the live village, about once a second, and tells online players when the next task changes. */
public final class GuideSystem implements VillageSystem {
	public static final GuideSystem INSTANCE = new GuideSystem();

	private HeartLocation heart;
	private long claimedAt = Long.MIN_VALUE;
	private final Set<UUID> seenBuilds = new HashSet<>();
	private GuideSignals latest = GuideSignals.none();

	private GuideSystem() {}

	public static GuideSignals latest() { return INSTANCE.latest; }

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if ((server.getTickCount() % 20) != 0) return;
		var data = VillageClaims.data(server);
		GuideProgress before = data.guide();
		GuideSignals signals = read(server, village, before);
		latest = signals;
		GuideProgress after = GuideBook.advance(before, signals);
		if (after.equals(before)) return;
		data.setGuide(after);
		HeartMenu.markAllDirty(server);
		String next = GuideBook.current(after);
		if (!next.equals(GuideBook.current(before))) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) HeartNetwork.send(player, new GuideToastPayload(next));
		}
	}

	private GuideSignals read(MinecraftServer server, VillageState village, GuideProgress progress) {
		if (village.claimedAtGameTime() != claimedAt || heart == null || !village.heart().equals(heart)) {
			claimedAt = village.claimedAtGameTime();
			heart = village.heart();
			seenBuilds.clear();
			for (BuildJob job : village.builds()) seenBuilds.add(job.id());
		}
		Set<String> valid = new HashSet<>();
		boolean marker = false;
		for (BuildingRecord building : village.buildings()) {
			marker = true;
			if (building.status() == BuildingStatus.VALID) valid.add(building.typeId());
		}
		Set<String> jobs = new HashSet<>();
		List<Integer> levels = new ArrayList<>();
		for (WorkerRecord worker : village.workers()) {
			jobs.add(worker.jobId());
			levels.add(worker.level());
		}
		Set<String> defences = new HashSet<>();
		for (DefenceSite site : village.defences()) defences.add(site.blueprintId());
		Set<UUID> now = new HashSet<>();
		boolean cursorDone = false;
		for (BuildJob job : village.builds()) {
			now.add(job.id());
			Optional<Blueprint> plan = Blueprints.resolve(job.blueprintId(), job.params());
			if (plan.isPresent() && job.cursor() >= plan.get().placeCount()) cursorDone = true;
		}
		boolean buildGone = false;
		for (UUID id : seenBuilds) if (!now.contains(id)) buildGone = true;
		seenBuilds.clear();
		seenBuilds.addAll(now);
		boolean heartOpen = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.containerMenu instanceof HeartMenu) heartOpen = true;
		}
		Optional<Payroll> payroll = VillageClaims.data(server).payroll();
		boolean wagesPaid = payroll.isPresent() && !jobs.isEmpty() && payroll.get().unpaid().isEmpty() && !payroll.get().paidToday().isEmpty();
		int bill = WageRules.bill(levels);
		int shortfall = Math.max(0, bill - village.treasury());
		boolean smallHouse = false;
		for (BuildJob job : village.builds()) if ("small_house".equals(job.blueprintId())) smallHouse = true;
		boolean stocked = progress.has(GuideTask.HOUSE_MATERIALS.id()) || smallHouse
				|| (village.tier().ordinal() >= Tier.VILLAGE.ordinal() && stockCovers(server));
		boolean field = progress.has(GuideTask.FIELD.id()) || fieldPlanted(server, village);
		boolean golem = progress.has(GuideTask.GOLEM.id()) || !village.golemBuilds().isEmpty() || golemNearby(server, village);
		return new GuideSignals(heartOpen || progress.has(GuideTask.OPEN_HEART.id()), marker, valid, jobs,
				village.treasury() > 0 || progress.has(GuideTask.EMERALDS.id()), field,
				village.tierProgress().fedStreak() >= 1, wagesPaid, stocked,
				!village.builds().isEmpty() || !defences.isEmpty() || buildGone || cursorDone,
				!defences.isEmpty() || buildGone || cursorDone, golem, defences, village.tier(), shortfall);
	}

	private static long counted(MinecraftServer server, boolean tag, String bare) {
		if (tag) return Stockpiles.count(server, TagKey.create(Registries.ITEM, Identifier.parse(bare)));
		return Stockpiles.count(server, BuiltInRegistries.ITEM.getValue(Identifier.parse(bare)));
	}

	private static boolean stockCovers(MinecraftServer server) {
		for (MaterialList.Line line : Blueprints.SMALL_HOUSE.materials().lines()) {
			if (counted(server, line.material().isTag(), line.material().bareId()) < line.count()) return false;
		}
		return true;
	}

	private static boolean fieldPlanted(MinecraftServer server, VillageState village) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return false;
		for (BuildingRecord building : village.buildings()) {
			if (!"farmhouse".equals(building.typeId()) || building.status() != BuildingStatus.VALID) continue;
			BlockPos origin = new BlockPos(building.marker().x(), building.marker().y(), building.marker().z());
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) {
				if (dx * dx + dz * dz > 24 * 24) continue;
				for (int dy = -8; dy <= 8; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (!level.hasChunkAt(cursor)) continue;
					if (level.getBlockState(cursor).getBlock() instanceof CropBlock) return true;
				}
			}
		}
		return false;
	}

	private static boolean golemNearby(MinecraftServer server, VillageState village) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return false;
		HeartLocation at = village.heart();
		int r = Balance.CLAIM_RADIUS;
		AABB box = new AABB(at.x() - r, at.y() - r, at.z() - r, at.x() + r + 1, at.y() + r + 1, at.z() + r + 1);
		return !level.getEntities(EntityType.IRON_GOLEM, box, e -> e.isAlive()).isEmpty();
	}
}
