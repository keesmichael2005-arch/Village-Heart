package dev.villageheart.gametest;

import static dev.villageheart.gametest.GuardTestSupport.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.siege.SiegeSize;
import dev.villageheart.core.siege.Threat;
import dev.villageheart.core.siege.ThreatRules;
import dev.villageheart.core.siege.ThreatState;
import dev.villageheart.raid.ThreatScheduler;
import dev.villageheart.siege.LureSlots;
import dev.villageheart.siege.SiegeGamerules;
import dev.villageheart.siege.VanillaSiege;
import dev.villageheart.worker.guard.GuardActivities;
import dev.villageheart.worker.guard.GuardAlarm;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * Zombie sieges (docs/plans/M25.md step 17). One environment each. The ring and the guards follow the yard rules.
 */
public class ZombieSiegeGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	/** T-L2-34. Two guards, a closed ring: three zombies at 18000, the day guard wakes, and 23000 sends them back to bed. */
	@GameTest(environment = "villageheart-gametest:siege_spawns", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void siegeSpawnsOutsideAndCallsGuards(GameTestHelper h) {
		Villager[] day = new Villager[1];
		Villager[] night = new Villager[1];
		BlockPos[] heart = new BlockPos[1];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> storage(h));
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			heart[0] = heartRel(h);
			ring(h, heart[0], 12);
			pad(h, heart[0], 44);
			day[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			night[0] = hireGuard(h, spawnSpot().east(), Optional.of(Shift.NIGHT));
			arm(h);
			setTime(h, 17990);
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the day guard is asleep before the spawn", () -> {
			h.assertTrue(day[0].isSleeping() && day[0].getBrain().isActive(Activity.REST), "day " + day[0].getBrain().getActiveActivities());
			h.assertTrue(night[0].getBrain().isActive(GuardActivities.DUTY), "night " + night[0].getBrain().getActiveActivities());
			h.assertTrue(state(h).threats().siege().isEmpty(), "a siege record before 18000 would wake the guard");
			h.assertTrue(state(h).threats().pendingSiegeDay() != ThreatState.NO_DAY, "no roll yet " + debug(h));
		});
		seq = seq.thenExecute(() -> setTime(h, 18000));
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "three siege zombies", () -> {
			h.assertValueEqual(3, tagged(h).size(), debug(h));
		});
		seq = seq.thenExecute(() -> {
			BlockPos abs = h.absolutePos(heart[0]);
			for (Zombie zombie : tagged(h)) {
				h.assertTrue(zombie.isPersistenceRequired(), "persistent");
				h.assertTrue(zombie.getTags().contains(LureSlots.SIEGE_TAG), "tagged");
				double dist = Math.sqrt(zombie.blockPosition().distSqr(abs));
				h.assertTrue(dist >= 20, "closer than 20: " + dist);
				h.assertTrue(outside(zombie.blockPosition(), abs, 12), "inside the ring " + zombie.blockPosition());
			}
			var facts = dev.villageheart.heart.HeartFacts.collect(h.getLevel().getServer());
			var alerts = HeartSnapshotBuilder.build(facts.orElseThrow(), Rank.OWNER).overview().alerts();
			h.assertTrue(alerts.stream().anyMatch(a -> a.code() == AlertCode.SIEGE && a.text().english().contains("Zombie siege")),
					"siege alert " + alerts);
			h.assertValueEqual(3, SiegeSize.zombies(1, 2), "two guards face 3");
			Villager victim = h.spawn(EntityType.VILLAGER, new BlockPos(4, 0, 4));
			victim.kill(h.getLevel());
			var siege = state(h).threats().siege().orElseThrow();
			h.assertValueEqual(1, siege.villagerDeaths(), "villager deaths");
			h.assertTrue(state(h).deaths().recent(1).get(0).duringSiege(), "the death is marked during the siege");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the day guard woke", () -> {
			h.assertTrue(!day[0].isSleeping() && day[0].getBrain().isActive(GuardActivities.DUTY), "day " + day[0].getBrain().getActiveActivities());
		});
		seq = seq.thenExecute(() -> {
			GuardAlarm.resetForTests();
			h.assertTrue(state(h).threats().underAttack(), "the siege is still the flag that holds duty");
		}).thenIdle(20).thenExecute(() -> {
			h.assertTrue(day[0].getBrain().isActive(GuardActivities.DUTY), "day still on duty after the alarm reset");
			h.assertTrue(night[0].getBrain().isActive(GuardActivities.DUTY), "night still on duty after the alarm reset");
			BuildingTestSupport.owner().teleportTo(h.absolutePos(new BlockPos(3, 1, 200)).getX() + 0.5, h.absolutePos(heart[0]).getY(),
					h.absolutePos(new BlockPos(3, 1, 200)).getZ() + 0.5);
			setTime(h, 23000);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the siege ended and the day guard is back in bed", () -> {
			h.assertTrue(state(h).threats().siege().isEmpty(), "still running");
			h.assertTrue(state(h).threats().lastSiege().isPresent(), "no summary");
			h.assertValueEqual(3, state(h).threats().lastSiege().orElseThrow().spawned(), "summary");
			h.assertTrue(day[0].getBrain().isActive(Activity.REST), "day " + day[0].getBrain().getActiveActivities());
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/** T-L2-34b. Two guards at T = 8 still face 3 zombies. A Hamlet rolls nothing, and vanilla stays out of the claim. */
	@GameTest(environment = "villageheart-gametest:siege_early", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void earlySiegeIsThreeZombies(GameTestHelper h) {
		BlockPos[] heart = new BlockPos[1];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			heart[0] = heartRel(h);
			pad(h, heart[0], 44);
			hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			hireGuard(h, spawnSpot().east(), Optional.of(Shift.NIGHT));
			arm(h);
			Threat.forceForTest(8);
			// A chunk forced on the spawn tick is not entity-ticking yet, so the ring is forced while it is still day.
			setTime(h, 1000);
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
		});
		seq = seq.thenIdle(10).thenExecute(() -> setTime(h, 18000));
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "T = 8 with two guards is still 3, not 20", () -> {
			h.assertValueEqual(3, tagged(h).size(), "zombies");
			h.assertValueEqual(3, SiegeSize.zombies(8, 2), "size rule");
		});
		seq = seq.thenExecute(() -> {
			for (Zombie zombie : tagged(h)) zombie.discard();
			h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			VillageClaims.data(h.getLevel().getServer()).update(v -> v.withTier(Tier.HAMLET).withThreats(v.threats()
					.withSiege(Optional.empty()).withPendingSiegeDay(ThreatState.NO_DAY)));
			ThreatScheduler.clearForTests();
			ThreatRules.siegeGraceForTest(0, 0);
			ThreatRules.forceSiege();
			setTime(h, 18000 + 24000);
		});
		seq = seq.thenIdle(10).thenExecute(() -> {
			h.assertTrue(tagged(h).isEmpty(), "a Hamlet spawned " + tagged(h).size());
			h.assertTrue(state(h).threats().siege().isEmpty(), "a Hamlet has a siege record");
			VanillaSiege.clear();
			VanillaSiege.forceTonight();
			h.getLevel().getGameRules().set(GameRules.SPAWN_MOBS, true, h.getLevel().getServer());
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, true, h.getLevel().getServer());
			setTime(h, 17999);
		});
		seq = seq.thenIdle(400).thenExecute(() -> {
			h.assertTrue(VanillaSiege.suppressedCount() > 0, "the claim never redirected isVillage");
			h.assertTrue(zombiesInClaim(h).isEmpty(), "vanilla zombies in the claim: " + zombiesInClaim(h).size());
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/**
	 * T-L2-35. Vanilla's siege does not spawn inside the claim, including for a player standing just outside it.
	 * A village built 200 blocks away still does.
	 */
	@GameTest(environment = "villageheart-gametest:siege_vanilla", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void vanillaSiegeSuppressedInClaim(GameTestHelper h) {
		BlockPos[] heart = new BlockPos[1];
		BlockPos[] control = new BlockPos[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			heart[0] = heartRel(h);
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			BlockPos stand = h.absolutePos(heart[0]);
			BuildingTestSupport.owner().teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
			light(h, heart[0], 16);
			allowMonsters(h);
			h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, true, h.getLevel().getServer());
			h.getLevel().getGameRules().set(SiegeGamerules.SIEGES, false, h.getLevel().getServer());
			claimBell(h, h.absolutePos(BuildingTestSupport.BELL));
			VanillaSiege.forceTonight();
			setTime(h, 17999);
		});
		seq = seq.thenIdle(400).thenExecute(() -> {
			ServerLevel level = h.getLevel();
			h.assertTrue(VanillaSiege.suppressedCount() > 0, "a player in the claim did not skip the vanilla siege day "
					+ level.getDayTime() + " bright " + level.isBrightOutside() + " suppressed " + VanillaSiege.suppressedCount());
			h.assertTrue(zombiesInClaim(h).isEmpty(), "zombies in the claim");
			ServerPlayer owner = BuildingTestSupport.owner();
			BlockPos outside = h.absolutePos(heart[0].offset(85, 0, 0));
			owner.teleportTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
			platform(h, outside, 6);
			h.getLevel().setBlock(outside.offset(2, 0, 0), Blocks.BELL.defaultBlockState(), 3);
		});
		seq = seq.thenIdle(5).thenExecute(() -> {
			claimBell(h, h.absolutePos(heart[0].offset(87, 0, 0)));
			VanillaSiege.forceTonight();
			setTime(h, 24000 + 17999);
		});
		seq = seq.thenIdle(400).thenExecute(() -> {
			h.assertTrue(zombiesInClaim(h).isEmpty(), "a player just outside pulled zombies into the claim");
			// A night with no daylight leaves the vanilla siege already set up, so the control night would not look again.
			setTime(h, 48000 + 6000);
			h.getLevel().updateSkyBrightness();
		});
		seq = seq.thenIdle(5).thenExecute(() -> control[0] = surfaceVillage(h, h.absolutePos(heart[0].offset(200, 0, 0))));
		seq = seq.thenIdle(10).thenExecute(() -> {
			claimBell(h, control[0]);
			VanillaSiege.forceTonight();
			setTime(h, 48000 + 17999);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "the control village spawns", () -> {
			long near = h.getLevel().getEntities(EntityType.ZOMBIE, new AABB(control[0]).inflate(48), z -> z.isAlive()).size();
			BlockPos player = BuildingTestSupport.owner().blockPosition();
			BlockPos out = control[0].offset(32, 0, 0);
			int spawnY = h.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, out.getX(), out.getZ());
			BlockPos spot = new BlockPos(out.getX(), spawnY, out.getZ());
			h.assertTrue(near > 0, "no zombie near the control bell at " + control[0].toShortString()
					+ " day " + h.getLevel().getDayTime() + " bright " + h.getLevel().isBrightOutside()
					+ " village " + h.getLevel().isVillage(control[0]) + " player " + player.toShortString()
					+ " playerVillage " + h.getLevel().isVillage(player)
					+ " spot " + spot.toShortString() + " " + h.getLevel().getBlockState(spot.below()).getBlock().getName().getString()
					+ "/" + h.getLevel().getBlockState(spot).getBlock().getName().getString()
					+ " sky " + h.getLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY, spot)
					+ " block " + h.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, spot)
					+ " spotVillage " + h.getLevel().isVillage(spot)
					+ " monsters " + h.getLevel().isSpawningMonsters());
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	private static void arm(GameTestHelper h) {
		ThreatScheduler.clearForTests();
		Threat.clearForce();
		Threat.forceForTest(1);
		ThreatRules.siegeGraceForTest(0, 0);
		ThreatRules.forceSiege();
		allowMonsters(h);
		h.getLevel().getGameRules().set(SiegeGamerules.SIEGES, true, h.getLevel().getServer());
		BlockPos heart = heartRel(h);
		light(h, heart, 30);
		BlockPos at = h.absolutePos(heart);
		BuildingTestSupport.owner().teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	private static void storage(GameTestHelper h) {
		BuildingTestSupport.room(h, STORE, 5, 4);
		BlockPos a = BuildingTestSupport.inside(STORE, 0, 0, 3);
		BlockPos b = BuildingTestSupport.inside(STORE, 4, 0, 3);
		h.setBlock(a, Blocks.CHEST);
		h.setBlock(b, Blocks.CHEST);
		BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.STORAGE, storeMarker(), net.minecraft.core.Direction.NORTH);
	}

	/** A closed square of wall whose nearest face is {@code radius} blocks from the Heart. */
	private static void ring(GameTestHelper h, BlockPos heart, int radius) {
		var chest = StockpileTestSupport.container(h, BuildingTestSupport.inside(STORE, 0, 0, 3));
		for (int i = 0; i < 8; i++) chest.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
		int x0 = heart.getX() - radius, x1 = heart.getX() + radius;
		int z0 = heart.getZ() - radius, z1 = heart.getZ() + radius;
		int y = -1;
		// The west and south faces sit outside the 32-block yard. Lay stone and clear the barrier there first.
		prepare(h, x0, z0, x1, z0);
		prepare(h, x0, z1, x1, z1);
		prepare(h, x0, z0 + 1, x0, z1 - 1);
		prepare(h, x1, z0 + 1, x1, z1 - 1);
		DefenceTestSupport.startLine(h, "wall_segment", new BlockPos(x0, y, z0), new BlockPos(x1, y, z0));
		DefenceTestSupport.startLine(h, "wall_segment", new BlockPos(x0, y, z1), new BlockPos(x1, y, z1));
		DefenceTestSupport.startLine(h, "wall_segment", new BlockPos(x0, y, z0 + 1), new BlockPos(x0, y, z1 - 1));
		DefenceTestSupport.startLine(h, "wall_segment", new BlockPos(x1, y, z0 + 1), new BlockPos(x1, y, z1 - 1));
		DefenceTestSupport.advanceAll(h);
		h.assertTrue(state(h).defences().size() >= 4, "the ring is recorded");
	}

	private static void prepare(GameTestHelper h, int x0, int z0, int x1, int z1) {
		int xa = Math.min(x0, x1), xb = Math.max(x0, x1);
		int za = Math.min(z0, z1), zb = Math.max(z0, z1);
		for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
			h.setBlock(new BlockPos(x, -1, z), Blocks.STONE.defaultBlockState());
			for (int y = 0; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
		}
	}

	/** Stone and air on the circle the spawner searches, plus torches so a natural zombie cannot appear there. */
	private static void pad(GameTestHelper h, BlockPos heartRel, int distance) {
		ServerLevel level = h.getLevel();
		BlockPos heart = h.absolutePos(heartRel);
		int floorY = h.absolutePos(BlockPos.ZERO).getY() - 1;
		int inner = distance - 3, outer = distance + 3;
		for (int dx = -outer; dx <= outer; dx++) for (int dz = -outer; dz <= outer; dz++) {
			int r2 = dx * dx + dz * dz;
			if (r2 < inner * inner || r2 > outer * outer) continue;
			int x = heart.getX() + dx, z = heart.getZ() + dz;
			level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(new BlockPos(x, floorY + 1, z), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(new BlockPos(x, floorY + 2, z), Blocks.AIR.defaultBlockState(), 3);
		}
		for (int cx = (heart.getX() - outer) >> 4; cx <= (heart.getX() + outer) >> 4; cx++) {
			for (int cz = (heart.getZ() - outer) >> 4; cz <= (heart.getZ() + outer) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
	}

	private static void light(GameTestHelper h, BlockPos heart, int radius) {
		ServerLevel level = h.getLevel();
		BlockPos origin = h.absolutePos(BlockPos.ZERO);
		for (int x = -radius; x <= radius; x += 4) for (int z = -radius; z <= radius; z += 4) {
			BlockPos at = origin.offset(heart.getX() + x, 0, heart.getZ() + z);
			if (level.getBlockState(at).isAir()) level.setBlock(at, Blocks.TORCH.defaultBlockState(), 3);
		}
	}

	private static void platform(GameTestHelper h, BlockPos origin, int radius) {
		ServerLevel level = h.getLevel();
		for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
			level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(origin.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(origin.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
		}
	}

	/** Rules first, then difficulty: {@code setDifficulty} is what copies them into the spawner's monster flag. */
	private static void allowMonsters(GameTestHelper h) {
		var server = h.getLevel().getServer();
		h.getLevel().getGameRules().set(GameRules.SPAWN_MOBS, true, server);
		h.getLevel().getGameRules().set(GameRules.SPAWN_MONSTERS, true, server);
		server.setDifficulty(Difficulty.NORMAL, true);
	}

	/**
	 * Vanilla looks for a spawn on the world surface about 32 blocks from the player, and only inside a village section.
	 * The yard sits below that surface, so the control village is built up there.
	 */
	private static BlockPos surfaceVillage(GameTestHelper h, BlockPos near) {
		ServerLevel level = h.getLevel();
		int cx = near.getX() >> 4, cz = near.getZ() >> 4;
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
			level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, near.getX(), near.getZ());
		BlockPos feet = new BlockPos(near.getX(), y, near.getZ());
		platform(h, feet, 48);
		// Vanilla only treats a spot within one section of a bell as a village, and it aims 32 blocks out.
		var poi = level.getPoiManager();
		for (int dx = -48; dx <= 48; dx += 16) for (int dz = -48; dz <= 48; dz += 16) {
			BlockPos bell = feet.offset(dx, 0, dz);
			level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
			poi.take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE), (type, pos) -> pos.equals(bell), bell, 1);
		}
		for (int i = 0; i < 4; i++) level.setBlock(feet.offset(2 + i, 0, 2), Blocks.RED_BED.defaultBlockState(), 3);
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (villager != null) {
			villager.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 2.5, 0, 0);
			level.addFreshEntity(villager);
		}
		BuildingTestSupport.owner().teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
		return feet;
	}

	private static void claimBell(GameTestHelper h, BlockPos bell) {
		for (int i = 0; i < 20 && !h.getLevel().isVillage(bell); i++) {
			h.getLevel().getPoiManager().take(type -> type.is(net.minecraft.tags.PoiTypeTags.VILLAGE),
					(type, pos) -> pos.equals(bell), bell, 1);
		}
		h.assertTrue(h.getLevel().isVillage(bell), "not a village at " + bell.toShortString());
	}

	private static BlockPos heartRel(GameTestHelper h) {
		HeartLocation loc = state(h).heart();
		return VillageTestSupport.rel(h, new BlockPos(loc.x(), loc.y(), loc.z()));
	}

	private static dev.villageheart.core.VillageState state(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).orElseThrow();
	}

	private static List<Zombie> tagged(GameTestHelper h) {
		HeartLocation heart = state(h).heart();
		AABB box = new AABB(heart.x(), heart.y(), heart.z(), heart.x() + 1, heart.y() + 1, heart.z() + 1).inflate(Balance.CLAIM_RADIUS + 32);
		List<Zombie> out = new ArrayList<>();
		for (Zombie zombie : h.getLevel().getEntities(EntityType.ZOMBIE, box, z -> z.isAlive() && z.getTags().contains(LureSlots.SIEGE_TAG))) out.add(zombie);
		return out;
	}

	private static List<Zombie> zombiesInClaim(GameTestHelper h) {
		HeartLocation heart = state(h).heart();
		AABB box = new AABB(heart.x(), heart.y(), heart.z(), heart.x() + 1, heart.y() + 1, heart.z() + 1).inflate(Balance.CLAIM_RADIUS);
		List<Zombie> out = new ArrayList<>();
		for (Zombie zombie : h.getLevel().getEntities(EntityType.ZOMBIE, box, z -> z.isAlive())) {
			if (ClaimGeometry.inClaim(heart, VillageClaims.locate(h.getLevel(), zombie.blockPosition()))) out.add(zombie);
		}
		return out;
	}

	private static String debug(GameTestHelper h) {
		var level = h.getLevel();
		var threats = state(h).threats();
		BlockPos heart = h.absolutePos(heartRel(h));
		ServerPlayer owner = BuildingTestSupport.owner();
		double dist = owner == null ? -1 : Math.sqrt(owner.blockPosition().distSqr(heart));
		return "day " + level.getDayTime() + " game " + level.getGameTime() + " tier " + state(h).tier()
				+ " pending " + threats.pendingSiegeDay() + " siege " + threats.siege().isPresent()
				+ " diff " + level.getDifficulty() + " mobs " + level.getGameRules().get(GameRules.SPAWN_MOBS)
				+ " sieges " + level.getGameRules().get(SiegeGamerules.SIEGES) + " owner " + dist
				+ " forced " + ThreatRules.siegeForced();
	}

	private static boolean outside(BlockPos zombie, BlockPos heart, int radius) {
		return Math.abs(zombie.getX() - heart.getX()) > radius || Math.abs(zombie.getZ() - heart.getZ()) > radius;
	}
}
