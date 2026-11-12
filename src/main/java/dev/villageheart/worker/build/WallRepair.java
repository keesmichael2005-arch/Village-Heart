package dev.villageheart.worker.build;

import dev.villageheart.build.BuildExecutor;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.StandPoints;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceCells;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.defence.Defences;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.DefenceDamage;
import dev.villageheart.core.siege.RepairPlanner;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.siege.SiegeDamage;
import dev.villageheart.siege.SiegeStats;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.WorkXp;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The builder's repair walk (docs/plans/M25.md 3.15). Repairs come before a new build, only during {@link DayPhase#WORK},
 * and never while the village is under attack. One fixed reservation id, one action per {@link Balance#BUILD_TICKS_PER_BLOCK}
 * at level 1, cobblestone for stone and any fence gate for a gate. A light crack is free. A player's block or an entity is left alone.
 */
public final class WallRepair {
	/** One reservation for every repair fetch. */
	public static final UUID RESERVATION = UUID.fromString("c25e0015-0000-4000-8000-000000000028");

	private static final double ACT_REACH = Balance.BUILD_REACH + 1.4;
	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private WallRepair() {}

	/** True when this builder is repairing or waiting on materials, so a new build waits. */
	public static boolean tick(ServerLevel level, Villager v, long now) {
		if (!Balance.REPAIR_BEFORE_BUILDS) return false;
		WorkerData data = v.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.BUILDER) return false;
		if (Workers.tripRunning(v)) return false;
		MinecraftServer server = level.getServer();
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return false;
		boolean threat = village.threats().underAttack();
		DayPhase phase = DaySchedule.phaseAt(level.getDayTime());
		if (threat || phase != DayPhase.WORK) {
			pause(v);
			return false;
		}
		Job job = JOBS.computeIfAbsent(v.getUUID(), id -> new Job());
		Target target = next(level, v, village, job);
		if (target == null) return false;
		if (target.waiting()) {
			say(job, "Repairs need " + target.missing() + " more " + target.waitItem());
			return true;
		}
		return perform(level, v, data, job, village, target, now);
	}

	public static void stop(Villager v) { pause(v); }

	public static void resetForTests() { JOBS.clear(); }

	public static String line(UUID villager) {
		Job job = JOBS.get(villager);
		return job == null ? "" : job.line;
	}

	public static boolean saw(UUID villager, String text) {
		Job job = JOBS.get(villager);
		return job != null && job.lines.contains(text);
	}

	public static int workTicks(UUID villager) {
		Job job = JOBS.get(villager);
		return job == null ? 0 : job.workTicks;
	}

	public static int walkTicks(UUID villager) {
		Job job = JOBS.get(villager);
		return job == null ? 0 : job.walkTicks;
	}

	private static void pause(Villager v) {
		Job job = JOBS.get(v.getUUID());
		if (job == null) return;
		job.walk.release(v);
		job.stand = null;
		job.target = Long.MIN_VALUE;
		job.clock.reset();
	}

	@Nullable
	private static Target next(ServerLevel level, Villager v, VillageState village, Job job) {
		DefenceDamage damage = SiegeDamage.damage(level.getServer());
		DefenceIndex index = DefenceIndex.of(village.defences());
		List<RepairPlanner.Cell> cells = new ArrayList<>();
		for (DefenceCell cell : index.cells()) {
			DefenceSite site = village.defence(cell.siteId()).orElse(null);
			if (site == null) continue;
			long relative = DefenceCells.relative(site.ground(), cell.pos());
			boolean opened = site.opened().contains(relative) || job.skipped.contains(cell.packed());
			cells.add(new RepairPlanner.Cell(cell.packed(), cell.kind(), damage.damageAt(cell.packed()), damage.isBreached(cell.packed()), opened));
		}
		Map<String, Integer> stock = stock(level, v);
		var plan = RepairPlanner.next(false, DayPhase.WORK, cells, village.heart(), stock);
		if (plan.isPresent()) {
			if (plan.get() instanceof RepairPlanner.Wait w) return Target.wait(w.item(), w.missing());
			RepairPlanner.Repair r = (RepairPlanner.Repair) plan.get();
			return Target.repair(r.packed(), r.cost(), r.materialId());
		}
		return keyTarget(level, v, village, job, damage);
	}

	@Nullable
	private static Target keyTarget(ServerLevel level, Villager v, VillageState village, Job job, DefenceDamage damage) {
		Defences.Key best = null;
		long bestD = Long.MAX_VALUE;
		for (DefenceSite site : village.defences()) {
			for (Defences.Key key : Defences.keys(site)) {
				long packed = key.at().pack();
				if (!damage.isBreached(packed) || job.skipped.contains(packed)) continue;
				long d = dist2(key.at().x(), key.at().y(), key.at().z(), village.heart());
				if (best == null || d < bestD) {
					best = key;
					bestD = d;
				}
			}
		}
		if (best == null) return null;
		String material = best.blockId();
		if (count(level, v, material) < 1) return Target.wait(itemName(material), 1);
		return Target.repair(best.at().pack(), 1, material);
	}

	private static boolean perform(ServerLevel level, Villager v, WorkerData data, Job job, VillageState village, Target target, long now) {
		Located loc = locate(village, target.packed());
		if (loc == null) {
			job.skipped.add(target.packed());
			return true;
		}
		BlockPos pos = new BlockPos(loc.pos.x(), loc.pos.y(), loc.pos.z());
		if (job.target != target.packed()) {
			job.target = target.packed();
			job.clock.reset();
			job.walk.clear();
			job.stand = null;
		}
		if (target.cost() > 0 && !carrying(v, target.materialId())) {
			if (!withdraw(level, v, target.materialId())) {
				say(job, "Repairs need " + target.cost() + " more " + itemName(target.materialId()));
				return true;
			}
		}
		say(job, "Repairing");
		if (distance(v, pos) > ACT_REACH) {
			job.walkTicks++;
			if (job.stand == null || job.failedStands.contains(job.stand)) job.stand = chooseStand(level, village, loc.site, pos, job.failedStands);
			BlockPos goal = job.stand != null ? job.stand : pos;
			if (!job.walk.active() || !job.walk.target().equals(goal)) job.walk.begin(goal, 1.5, now);
			if (job.walk.tick(v, now) == WalkTo.State.UNREACHABLE && job.stand != null) {
				job.failedStands.add(job.stand);
				job.stand = null;
				job.walk.clear();
			}
			return true;
		}
		job.workTicks++;
		if (job.clock.tick(WorkSpeed.untooled(data.level(), data.needs())) <= 0) return true;
		job.clock.reset();
		BlockState state = level.getBlockState(pos);
		String wantId = blockId(loc.entry.state());
		if (entityIn(level, v, pos) || foreign(state, wantId)) {
			job.skipped.add(target.packed());
			job.target = Long.MIN_VALUE;
			return true;
		}
		boolean matches = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(wantId);
		if (!matches) {
			Item item = target.cost() > 0 ? takeOne(v, target.materialId()) : null;
			if (target.cost() > 0 && item == null) {
				say(job, "Repairs need 1 more " + itemName(target.materialId()));
				return true;
			}
			if (item == null) item = net.minecraft.world.item.Items.COBBLESTONE;
			v.swing(InteractionHand.MAIN_HAND);
			BuildExecutor.place(level, loc.bp, loc.placement, new BuildPlan.Step(0, BuildPlan.Kind.PLACE, loc.pos, loc.entry, List.of()), item, v);
		} else if (target.cost() > 0) {
			if (takeOne(v, target.materialId()) == null) {
				say(job, "Repairs need 1 more " + itemName(target.materialId()));
				return true;
			}
			v.swing(InteractionHand.MAIN_HAND);
		} else {
			v.swing(InteractionHand.MAIN_HAND);
		}
		WorkXp.award(v, WorkUnit.BLOCK_PLACED, 1);
		SiegeDamage.repair(level.getServer(), target.packed());
		SiegeStats.repaired(target.cost());
		job.target = Long.MIN_VALUE;
		return true;
	}

	@Nullable
	private static BlockPos chooseStand(ServerLevel level, VillageState village, DefenceSite site, BlockPos cell, Set<BlockPos> failed) {
		DefenceIndex index = DefenceIndex.of(village.defences());
		Pos cellPos = new Pos(cell.getX(), cell.getY(), cell.getZ());
		int walkY = site.ground().y() + 1;
		StandPoints.Standable standable = (x, z) -> {
			for (int y : new int[] {walkY, cell.getY(), site.ground().y()}) {
				BlockPos feet = new BlockPos(x, y, z);
				if (failed.contains(feet)) continue;
				if (canStand(level, village, index, feet)) return OptionalInt.of(y);
			}
			return OptionalInt.empty();
		};
		List<Pos> ring = StandPoints.ring(cellPos, cellPos, standable);
		Pos outside = null;
		Pos inside = null;
		long cellD = dist2(cell.getX(), cell.getY(), cell.getZ(), village.heart());
		for (Pos p : ring) {
			long d = dist2(p.x(), p.y(), p.z(), village.heart());
			if (d >= cellD) {
				if (outside == null || d > dist2(outside.x(), outside.y(), outside.z(), village.heart())) outside = p;
			} else if (inside == null || d < dist2(inside.x(), inside.y(), inside.z(), village.heart())) inside = p;
		}
		Pos pick = outside != null ? outside : inside;
		if (pick == null && !ring.isEmpty()) pick = ring.get(0);
		return pick == null ? null : new BlockPos(pick.x(), pick.y(), pick.z());
	}

	private static boolean canStand(ServerLevel level, VillageState village, DefenceIndex index, BlockPos feet) {
		String dim = level.dimension().identifier().toString();
		if (!ClaimGeometry.inClaim(village.heart(), new HeartLocation(dim, feet.getX(), feet.getY(), feet.getZ()))) return false;
		if (index.get(Pos.pack(feet.getX(), feet.getY(), feet.getZ())) != null) return false;
		BlockPos below = feet.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
		return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
	}

	private static boolean entityIn(ServerLevel level, Villager self, BlockPos pos) {
		AABB box = new AABB(pos).deflate(0.05);
		return !level.getEntities(self, box, e -> e.isAlive() && !(e instanceof ItemEntity)).isEmpty();
	}

	private static boolean foreign(BlockState state, String expectedBlockId) {
		if (state.isAir() || state.canBeReplaced()) return false;
		return !BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(expectedBlockId);
	}

	@Nullable
	private static Located locate(VillageState village, long packed) {
		Pos pos = Pos.unpack(packed);
		for (DefenceSite site : village.defences()) {
			Blueprint bp = Blueprints.resolve(site.blueprintId(), new BlueprintParams(Math.max(0, site.length()))).orElse(null);
			if (bp == null) continue;
			Placement placement = new Placement(new Pos(site.ground().x(), site.ground().y(), site.ground().z()), site.rotation());
			PaletteEntry entry = placement.entryAt(bp, pos);
			if (entry != null && entry.isBlock()) return new Located(site, bp, placement, entry, pos);
		}
		return null;
	}

	private static Map<String, Integer> stock(ServerLevel level, Villager v) {
		MinecraftServer server = level.getServer();
		Map<String, Integer> stock = new HashMap<>();
		stock.put(RepairPlanner.COBBLESTONE, count(level, v, RepairPlanner.COBBLESTONE));
		stock.put(RepairPlanner.FENCE_GATE, count(level, v, RepairPlanner.FENCE_GATE));
		return stock;
	}

	private static int count(ServerLevel level, Villager v, String materialId) {
		MinecraftServer server = level.getServer();
		long stored;
		if (RepairPlanner.FENCE_GATE.equals(materialId)) stored = Stockpiles.count(server, ItemTags.FENCE_GATES);
		else if (materialId.startsWith("#")) stored = Stockpiles.count(server, TagKey.create(Registries.ITEM, Identifier.parse(materialId.substring(1))));
		else stored = Stockpiles.count(server, item(materialId));
		return (int) Math.min(Integer.MAX_VALUE, stored + carried(v, materialId));
	}

	private static int carried(Villager v, String materialId) {
		int n = 0;
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty() && serves(stack, materialId)) n += stack.getCount();
		}
		return n;
	}

	private static boolean carrying(Villager v, String materialId) { return carried(v, materialId) > 0; }

	private static boolean withdraw(ServerLevel level, Villager v, String materialId) {
		ItemStack got = Stockpiles.withdrawFirst(level, v.blockPosition(), key -> serves(key.toStack(1), materialId), 1, RESERVATION);
		if (got.isEmpty()) return false;
		v.getInventory().addItem(got);
		return true;
	}

	@Nullable
	private static Item takeOne(Villager v, String materialId) {
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty() || !serves(stack, materialId)) continue;
			Item item = stack.getItem();
			stack.shrink(1);
			inv.setChanged();
			return item;
		}
		return null;
	}

	private static boolean serves(ItemStack stack, String materialId) {
		if (RepairPlanner.FENCE_GATE.equals(materialId)) return stack.is(ItemTags.FENCE_GATES);
		if (RepairPlanner.COBBLESTONE.equals(materialId)) return stack.is(net.minecraft.world.item.Items.COBBLESTONE);
		if (materialId.startsWith("#")) return stack.is(TagKey.create(Registries.ITEM, Identifier.parse(materialId.substring(1))));
		return stack.is(item(materialId));
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	private static String itemName(String materialId) {
		if (RepairPlanner.FENCE_GATE.equals(materialId)) return "fence gate";
		if (RepairPlanner.COBBLESTONE.equals(materialId)) return "cobblestone";
		String bare = materialId.startsWith("#") ? materialId.substring(1) : materialId;
		int colon = bare.indexOf(':');
		return (colon < 0 ? bare : bare.substring(colon + 1)).replace('_', ' ');
	}

	private static String blockId(String state) {
		int i = state.indexOf('[');
		return i < 0 ? state : state.substring(0, i);
	}

	private static double distance(Villager v, BlockPos pos) {
		double dx = v.getX() - (pos.getX() + 0.5);
		double dy = v.getY() - (pos.getY() + 0.5);
		double dz = v.getZ() - (pos.getZ() + 0.5);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static long dist2(int x, int y, int z, HeartLocation heart) {
		long dx = x - heart.x(), dy = y - heart.y(), dz = z - heart.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private static void say(Job job, String line) {
		job.line = line;
		if (job.lines.isEmpty() || !job.lines.get(job.lines.size() - 1).equals(line)) job.lines.add(line);
	}

	private static final class Job {
		final WalkTo walk = new WalkTo();
		final WorkClock clock = new WorkClock(Balance.BUILD_TICKS_PER_BLOCK);
		final List<String> lines = new ArrayList<>();
		final Set<Long> skipped = new HashSet<>();
		final Set<BlockPos> failedStands = new HashSet<>();
		long target = Long.MIN_VALUE;
		int workTicks;
		int walkTicks;
		String line = "";
		@Nullable BlockPos stand;
	}

	private record Target(long packed, int cost, String materialId, @Nullable String waitItem, int missing) {
		static Target repair(long packed, int cost, String materialId) { return new Target(packed, cost, materialId, null, 0); }

		static Target wait(String item, int missing) { return new Target(0, 0, "", item, missing); }

		boolean waiting() { return waitItem != null; }
	}

	private record Located(DefenceSite site, Blueprint bp, Placement placement, PaletteEntry entry, Pos pos) {}
}
