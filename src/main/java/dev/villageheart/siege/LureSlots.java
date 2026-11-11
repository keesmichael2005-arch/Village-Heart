package dev.villageheart.siege;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.siege.Lure;
import dev.villageheart.core.siege.LureBudget;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.AABB;

/**
 * Which loaded zombies may walk to the village this tick (docs/plans/M25.md 3.11). The pure rules are {@link Lure} and
 * {@link LureBudget}. A slot is not stored: the next admit drops a mob that unloaded, left the radius, or took a target.
 */
public final class LureSlots {
	/** Set on the zombies a siege spawns, so they take a slot ahead of a nearer natural zombie. */
	public static final String SIEGE_TAG = "villageheart.siege";

	private static int tick = Integer.MIN_VALUE;
	private static boolean on;
	private static Set<java.util.UUID> admitted = Set.of();

	private LureSlots() {}

	public static void resetForTests() {
		tick = Integer.MIN_VALUE;
		on = false;
		admitted = Set.of();
	}

	public static boolean on(MinecraftServer server) {
		refresh(server);
		return on;
	}

	public static boolean holds(MinecraftServer server, Mob mob) {
		refresh(server);
		return admitted.contains(mob.getUUID());
	}

	/** Nearest census villager when the mob is still near the claim, otherwise the Heart. */
	public static BlockPos point(MinecraftServer server, Mob mob) {
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return null;
		HeartLocation heart = village.heart();
		BlockPos heartPos = new BlockPos(heart.x(), heart.y(), heart.z());
		if (distance(mob, heart) > Balance.CLAIM_RADIUS + 16) return heartPos;
		Villager nearest = null;
		double best = Double.MAX_VALUE;
		for (Villager villager : VillageCensus.of(server, village)) {
			double d = mob.distanceToSqr(villager);
			if (d < best) {
				best = d;
				nearest = villager;
			}
		}
		return nearest == null ? heartPos : nearest.blockPosition();
	}

	private static void refresh(MinecraftServer server) {
		int now = server.getTickCount();
		if (now == tick) return;
		tick = now;
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) {
			on = false;
			admitted = Set.of();
			return;
		}
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) {
			on = false;
			admitted = Set.of();
			return;
		}
		int day = (int) Math.floorMod(level.getDayTime(), 24000L);
		on = Lure.active(day, seenWalls(village), level.getDifficulty() == Difficulty.PEACEFUL, village.threats().siege().isPresent());
		if (!on) {
			admitted = Set.of();
			return;
		}
		HeartLocation heart = village.heart();
		int r = Balance.LURE_RADIUS;
		AABB box = new AABB(heart.x() - r, heart.y() - r, heart.z() - r, heart.x() + r + 1, heart.y() + r + 1, heart.z() + r + 1);
		List<LureBudget.Mob> mobs = new ArrayList<>();
		for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, box, LureSlots::counts)) consider(mobs, zombie, heart, r);
		for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, box, c -> c.isAlive() && !c.isRemoved())) consider(mobs, creeper, heart, r);
		admitted = new HashSet<>(LureBudget.admit(mobs));
		SiegeStats.lure(admitted.size());
	}

	private static void consider(List<LureBudget.Mob> mobs, Mob mob, HeartLocation heart, int radius) {
		if (mob.getTarget() != null) return;
		double dist = distance(mob, heart);
		if (dist > radius) return;
		mobs.add(new LureBudget.Mob(mob.getUUID(), mob.getTags().contains(SIEGE_TAG), dist, true));
	}

	private static boolean counts(Zombie zombie) {
		return zombie.isAlive() && !zombie.isRemoved() && !(zombie instanceof Drowned) && !(zombie instanceof ZombifiedPiglin);
	}

	private static int seenWalls(VillageState village) {
		int n = 0;
		for (DefenceSite site : village.defences()) {
			String id = site.blueprintId();
			if (!id.equals("wall_segment") && !id.equals("gate")) continue;
			n += site.seen().size();
		}
		return n;
	}

	private static double distance(Mob mob, HeartLocation heart) {
		double dx = mob.getX() - (heart.x() + 0.5);
		double dy = mob.getY() - heart.y();
		double dz = mob.getZ() - (heart.z() + 0.5);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
