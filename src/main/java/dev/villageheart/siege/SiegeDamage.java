package dev.villageheart.siege;

import dev.villageheart.build.SiteSupport;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceCells;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.defence.Defences;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.CreeperBlast;
import dev.villageheart.core.siege.DefenceDamage;
import dev.villageheart.core.siege.SiegeHits;
import dev.villageheart.core.siege.WallHp;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The live wall-damage store (docs/plans/M25.md 3.9). A hit updates this, not a fresh {@code VillageState}.
 * Cracks are copied into the claim every {@link dev.villageheart.core.Balance#DAMAGE_FLUSH_TICKS} and on {@link #flush}.
 * A breach is written through at once.
 */
public final class SiegeDamage {
	private static final WeakHashMap<MinecraftServer, Live> LIVE = new WeakHashMap<>();

	private SiegeDamage() {}

	public enum Result { APPLIED, BREACHED, IGNORED, REJECTED }

	/** Hits that were not on a counting defence cell. A gamerule that is off is not a rejected hit. */
	public static int rejected(MinecraftServer server) {
		Live live = LIVE.get(server);
		return live == null ? 0 : live.rejected;
	}

	public static boolean isBreached(MinecraftServer server, long packed) {
		return damage(server).isBreached(packed);
	}

	public static DefenceDamage damage(MinecraftServer server) {
		Live live = LIVE.get(server);
		if (live != null) return live.damage;
		return VillageClaims.current(server).map(v -> v.threats().damage()).orElse(DefenceDamage.EMPTY);
	}

	public static void resetForTests() { LIVE.clear(); }

	/** Copies the live cracks and breaches into the claim so a save sees them. */
	public static void flush(MinecraftServer server) {
		Live live = LIVE.get(server);
		if (live == null || VillageClaims.current(server).isEmpty()) return;
		VillageClaims.data(server).update(v -> v.withThreats(v.threats().withDamage(live.damage)));
	}

	public static Result hit(MinecraftServer server, ServerLevel level, BlockPos pos, SiegeHits.Attacker attacker) {
		if (!level.getGameRules().get(SiegeGamerules.SIEGE_DAMAGE)) return Result.IGNORED;
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return Result.REJECTED;
		Live live = live(server, village);
		long packed = Pos.pack(pos.getX(), pos.getY(), pos.getZ());
		DefenceCell cell = live.index.get(packed);
		if (cell == null) {
			live.rejected++;
			return Result.REJECTED;
		}
		DefenceSite site = village.defence(cell.siteId()).orElse(null);
		if (site == null) return Result.REJECTED;
		long relative = DefenceCells.relative(site.ground(), cell.pos());
		if (site.opened().contains(relative)) return Result.IGNORED;
		BlockState state = level.getBlockState(pos);
		if (!blockId(state).equals(blockIdOf(cell.expectedState()))) {
			live.rejected++;
			return Result.REJECTED;
		}
		if (live.damage.isBreached(packed)) return Result.IGNORED;
		int hp = WallHp.hp(cell.kind());
		boolean breakable = breakable(level, live, pos);
		DefenceDamage next = live.damage.struck(packed, SiegeHits.damage(attacker, cell.kind()), hp, breakable);
		live.damage = next;
		if (next.isBreached(packed)) {
			breakCell(level, village.heart(), pos, state, crackId(village.heart(), level, pos));
			noteMissingKeys(level, live, site);
			flush(server);
			return Result.BREACHED;
		}
		int stage = WallHp.stage(next.damageAt(packed), hp);
		level.destroyBlockProgress(crackId(village.heart(), level, pos), pos, stage);
		return Result.APPLIED;
	}

	/**
	 * A breaching creeper's hole. Only defence cells change. {@code above} is true when the second core cell is the one above
	 * the target; otherwise it is the one below.
	 */
	public static void blast(MinecraftServer server, ServerLevel level, BlockPos target, boolean above, boolean powered) {
		if (!level.getGameRules().get(SiegeGamerules.SIEGE_DAMAGE)) return;
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return;
		Live live = live(server, village);
		BlockPos other = above ? target.above() : target.below();
		int radius = powered ? dev.villageheart.core.Balance.CREEPER_RADIUS_POWERED : dev.villageheart.core.Balance.CREEPER_RADIUS;
		List<BlockPos> cells = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) for (int dz = -radius; dz <= radius; dz++) {
			cells.add(target.offset(dx, dy, dz));
		}
		for (BlockPos pos : cells) {
			long packed = Pos.pack(pos.getX(), pos.getY(), pos.getZ());
			if (live.index.get(packed) == null) continue;
			boolean core = pos.equals(target) || pos.equals(other);
			double distance = Math.sqrt(pos.distSqr(target));
			int amount = CreeperBlast.damage(distance, core, powered);
			if (amount <= 0) continue;
			applyAmount(server, level, village, live, pos, amount);
		}
	}

	/** A player broke this block. An opened cell is not a breach and is not repaired until the material is back. */
	public static void onPlayerBreak(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return;
		Live live = live(server, village);
		long packed = Pos.pack(pos.getX(), pos.getY(), pos.getZ());
		DefenceCell cell = live.index.get(packed);
		if (cell == null) return;
		DefenceSite site = village.defence(cell.siteId()).orElse(null);
		if (site == null) return;
		long relative = DefenceCells.relative(site.ground(), cell.pos());
		Set<Long> opened = new LinkedHashSet<>(site.opened());
		opened.add(relative);
		live.damage = live.damage.without(packed);
		level.destroyBlockProgress(crackId(village.heart(), level, pos), pos, -1);
		DefenceSite updated = site.withOpened(opened);
		VillageClaims.data(server).update(v -> v.withThreats(v.threats().withDamage(live.damage)).replaceDefence(updated));
	}

	/** One tenth of the sites, or every site when {@code bucket} is negative. Loaded chunks only. */
	public static void scan(MinecraftServer server, VillageState village, int bucket) {
		if (village.defences().isEmpty()) return;
		Live live = live(server, village);
		List<DefenceSite> sites = village.defences();
		boolean changed = false;
		DefenceDamage damage = live.damage;
		List<DefenceSite> nextSites = new ArrayList<>();
		for (int i = 0; i < sites.size(); i++) {
			DefenceSite site = sites.get(i);
			if (bucket >= 0 && Math.floorMod(i, 10) != bucket) {
				nextSites.add(site);
				continue;
			}
			ServerLevel level = dev.villageheart.worker.Workers.levelOf(server, site.ground());
			if (level == null) {
				nextSites.add(site);
				continue;
			}
			Set<Long> seen = new LinkedHashSet<>(site.seen());
			Set<Long> opened = new LinkedHashSet<>(site.opened());
			boolean siteChanged = false;
			for (DefenceCells.Placed placed : DefenceCells.of(site)) {
				BlockPos pos = new BlockPos(placed.pos().x(), placed.pos().y(), placed.pos().z());
				if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
				long relative = DefenceCells.relative(site.ground(), placed.pos());
				long packed = placed.pos().pack();
				BlockState state = level.getBlockState(pos);
				boolean holding = blockId(state).equals(blockIdOf(placed.expectedState()));
				boolean missing = state.isAir() || state.canBeReplaced();
				if (holding && seen.add(relative)) siteChanged = true;
				if (opened.contains(relative)) {
					if (holding) {
						opened.remove(relative);
						siteChanged = true;
					}
					continue;
				}
				boolean was = damage.isBreached(packed);
				boolean now = dev.villageheart.core.siege.DefenceScan.breached(seen.contains(relative), false,
						missing ? dev.villageheart.core.siege.DefenceScan.Occupancy.MISSING
								: holding ? dev.villageheart.core.siege.DefenceScan.Occupancy.HOLDING
								: dev.villageheart.core.siege.DefenceScan.Occupancy.OTHER);
				if (now && !was) {
					damage = damage.withBreach(packed);
					changed = true;
				} else if (!now && was) {
					damage = damage.without(packed);
					changed = true;
				}
			}
			nextSites.add(siteChanged ? site.withSeen(seen).withOpened(opened) : site);
			if (siteChanged) changed = true;
		}
		if (!changed) return;
		live.damage = damage;
		final DefenceDamage written = damage;
		VillageClaims.data(server).update(v -> {
			VillageState next = v.withThreats(v.threats().withDamage(written));
			for (DefenceSite s : nextSites) next = next.replaceDefence(s);
			return next;
		});
	}

	private static void applyAmount(MinecraftServer server, ServerLevel level, VillageState village, Live live, BlockPos pos, int amount) {
		long packed = Pos.pack(pos.getX(), pos.getY(), pos.getZ());
		DefenceCell cell = live.index.get(packed);
		if (cell == null || live.damage.isBreached(packed)) return;
		DefenceSite site = village.defence(cell.siteId()).orElse(null);
		if (site == null || site.opened().contains(DefenceCells.relative(site.ground(), cell.pos()))) return;
		BlockState state = level.getBlockState(pos);
		if (!blockId(state).equals(blockIdOf(cell.expectedState()))) return;
		int hp = WallHp.hp(cell.kind());
		DefenceDamage next = live.damage.struck(packed, amount, hp, breakable(level, live, pos));
		live.damage = next;
		if (next.isBreached(packed)) {
			breakCell(level, village.heart(), pos, state, crackId(village.heart(), level, pos));
			noteMissingKeys(level, live, site);
			flush(server);
		} else {
			level.destroyBlockProgress(crackId(village.heart(), level, pos), pos, WallHp.stage(next.damageAt(packed), hp));
		}
	}

	private static void noteMissingKeys(ServerLevel level, Live live, DefenceSite site) {
		for (Defences.Key key : Defences.keys(site)) {
			BlockPos kp = new BlockPos(key.at().x(), key.at().y(), key.at().z());
			if (blockId(level.getBlockState(kp)).equals(key.blockId())) continue;
			live.damage = live.damage.withBreach(Pos.pack(kp.getX(), kp.getY(), kp.getZ()));
		}
	}

	private static boolean breakable(ServerLevel level, Live live, BlockPos pos) {
		if (level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) return false;
		Set<Long> touched = new HashSet<>(live.index.cells().size());
		for (DefenceCell c : live.index.cells()) touched.add(c.packed());
		return SiteSupport.dependent(level, new Pos(pos.getX(), pos.getY(), pos.getZ()), touched).isEmpty();
	}

	private static void breakCell(ServerLevel level, HeartLocation heart, BlockPos pos, BlockState state, int crack) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level.levelEvent(2001, pos, Block.getId(state));
		level.destroyBlockProgress(crack, pos, -1);
	}

	static int crackId(HeartLocation heart, ServerLevel level, BlockPos pos) {
		long id = WallHp.crackId(pos.getX() - heart.x(), pos.getZ() - heart.z(), pos.getY(), level.getMinY());
		return id < Integer.MIN_VALUE || id > Integer.MAX_VALUE ? (int) (id & 0x7fffffffL) * -1 - 1 : (int) id;
	}

	private static Live live(MinecraftServer server, VillageState village) {
		Live live = LIVE.computeIfAbsent(server, s -> new Live());
		int epoch = VillageClaims.data(server).epoch();
		if (live.epoch != epoch) {
			live.epoch = epoch;
			live.damage = village.threats().damage();
			live.defences = null;
		}
		if (live.defences != village.defences()) {
			live.defences = village.defences();
			live.index = DefenceIndex.of(village.defences());
		}
		return live;
	}

	private static String blockId(BlockState state) { return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(); }

	private static String blockIdOf(String state) {
		int i = state.indexOf('[');
		return i < 0 ? state : state.substring(0, i);
	}

	private static final class Live {
		DefenceDamage damage = DefenceDamage.EMPTY;
		DefenceIndex index = DefenceIndex.of(List.of());
		List<DefenceSite> defences;
		int epoch = Integer.MIN_VALUE;
		int rejected;
	}
}
