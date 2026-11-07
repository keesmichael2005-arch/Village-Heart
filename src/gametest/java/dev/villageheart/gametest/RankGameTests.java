package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.*;
import static dev.villageheart.gametest.HeartTestSupport.*;
import static dev.villageheart.gametest.RankTestSupport.*;

import dev.villageheart.block.MarkerStatus;
import dev.villageheart.building.BuildingSystem;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.Rejection;
import dev.villageheart.gametest.RankTestSupport.Console;
import dev.villageheart.gametest.HeartTestSupport.OverlayPlayer;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.heart.HeartActions;
import dev.villageheart.heart.net.HeartActionResultPayload;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Layer 2: ranks and the Visitor container rule (M24). Own environment per test, the {@code yard} structure, {@code skyAccess}
 * (rooms are scanned), {@code seed}/{@code begin} first and {@code done} last. Breaks go through {@code player.gameMode.destroyBlock}
 * and opens through {@code useItemOn}, so Fabric's event hooks run (never {@code level.destroyBlock}, which skips the event).
 */
public class RankGameTests {
	private static MinecraftServer server(GameTestHelper h) { return h.getLevel().getServer(); }

	private static final BlockPos CONTROL_CHEST = new BlockPos(8, 1, 16);

	private static boolean opens(GameTestHelper h, ServerPlayer p, BlockPos rel) {
		InteractionResult r = use(h, p, rel);
		boolean open = p.containerMenu instanceof ChestMenu;
		p.closeContainer();
		h.assertTrue(p.containerMenu == p.inventoryMenu, "closed again");
		return open && r.consumesAction();
	}

	// ---- T-L2-1 (roadmap) ---------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:ranks_open", structure = YARD, skyAccess = true, maxTicks = 700)
	public void visitorCannotOpenStorageChestResidentCan(GameTestHelper h) {
		OverlayPlayer[] p = new OverlayPlayer[4]; // V, R, S, O is the claim's owner
		BlockPos furnace = inside(STORAGE, 1, 0, 3), ender = inside(STORAGE, 2, 0, 3), farmChest = inside(FARMHOUSE, 4, 0, 3);
		BlockPos stoneSpot = chest1().above();
		seed(h).thenExecute(() -> {
			h.setBlock(CONTROL_CHEST, Blocks.CHEST);
			h.setBlock(furnace, Blocks.FURNACE);
			h.setBlock(ender, Blocks.ENDER_CHEST);
			h.assertTrue(Buildings.guardAreas(server(h)).stream().allMatch(a -> a.enclosed().isPresent()),
					"both rooms have an enclosed scan (the guard uses scans, not the fallback box)");
			p[0] = ranked(h, "vis", Rank.VISITOR);
			p[1] = ranked(h, "res", Rank.RESIDENT);
			p[2] = ranked(h, "stew", Rank.STEWARD);
			ServerPlayer owner = owner();
			h.assertFalse(opens(h, p[0], chest1()), "visitor cannot open the Storage chest");
			h.assertTrue(hasOverlay(p[0], "message.villageheart.protect.container"), "the refusal says why: " + p[0].overlays);
			h.assertTrue(opens(h, p[1], chest1()), "resident opens");
			h.assertTrue(opens(h, p[2], chest1()), "steward opens");
			h.assertTrue(opens(h, owner, chest1()), "owner opens");
			h.assertTrue(opens(h, p[0], CONTROL_CHEST), "a chest outside every building is not guarded");
			h.assertFalse(opens(h, p[0], farmChest), "building chests, not only Storage");

			p[0].setGameMode(GameType.SPECTATOR);
			use(h, p[0], chest1());
			h.assertTrue(p[0].containerMenu == p[0].inventoryMenu, "a spectator visitor is refused as well");
			p[0].setGameMode(GameType.SURVIVAL);

			use(h, p[0], furnace);
			h.assertTrue(p[0].containerMenu == p[0].inventoryMenu, "furnaces are containers too");
			use(h, p[0], ender);
			h.assertTrue(p[0].containerMenu != p[0].inventoryMenu, "the ender chest is personal and stays usable");
			p[0].closeContainer();

			p[0].setShiftKeyDown(true);
			use(h, p[0], chest1(), new ItemStack(Items.STONE));
			p[0].setShiftKeyDown(false);
			h.assertTrue(h.getBlockState(stoneSpot).is(Blocks.STONE), "sneak-placing against the chest still places the block");
			h.assertTrue(p[0].containerMenu == p[0].inventoryMenu, "...and the chest stayed closed");
			h.setBlock(stoneSpot, Blocks.AIR);

			VillageClaims.setRank(server(h), p[0].getUUID(), Rank.RESIDENT);
			h.assertTrue(opens(h, p[0], chest1()), "the rank is re-read at once: promoted visitor opens in the same tick");
			VillageClaims.setRank(server(h), p[0].getUUID(), Rank.VISITOR);
			h.assertFalse(opens(h, p[0], chest1()), "and demoted again, refused at once");
			done(h);
		});
	}

	// ---- T-L2-2 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:ranks_break", structure = YARD, skyAccess = true, maxTicks = 700)
	public void visitorCannotBreakProtectedContainerOrMarker(GameTestHelper h) {
		BlockPos storageMarker = markerPos(STORAGE, 5);
		BlockPos wall = STORAGE.offset(0, 2, 2);
		seed(h).thenExecute(() -> {
			OverlayPlayer v = ranked(h, "vis", Rank.VISITOR);
			OverlayPlayer r = ranked(h, "res", Rank.RESIDENT);
			boolean broke = v.gameMode.destroyBlock(h.absolutePos(chest2()));
			h.assertFalse(broke, "visitor cannot break the Storage chest");
			h.assertTrue(h.getBlockState(chest2()).is(Blocks.CHEST), "the chest is still there");
			h.assertTrue(hasOverlay(v, "message.villageheart.protect.break"), "the hook ran and said so: " + v.overlays);
			h.assertFalse(v.gameMode.destroyBlock(h.absolutePos(storageMarker)), "visitor cannot break a registered marker");
			h.assertTrue(record(h, storageMarker).isPresent(), "the building is still registered");
			h.assertTrue(r.gameMode.destroyBlock(h.absolutePos(chest2())), "a resident can break the chest");
			h.assertTrue(h.getBlockState(chest2()).isAir(), "...and it is gone");
			h.assertTrue(v.gameMode.destroyBlock(h.absolutePos(wall)), "a wall block is out of scope by design (docs/DESIGN.md)");
			done(h);
		});
	}

	// ---- T-L2-3 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:ranks_scan", structure = YARD, skyAccess = true, maxTicks = 900)
	public void guardUsesLastEnclosedScanAndFailsClosed(GameTestHelper h) {
		BlockPos storageMarker = markerPos(STORAGE, 5);
		BlockPos wall = STORAGE.offset(6, 2, 2);
		BlockPos far = new BlockPos(11, 1, 28);
		BlockPos near = new BlockPos(11, 1, 18);
		OverlayPlayer[] v = new OverlayPlayer[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			v[0] = ranked(h, "vis", Rank.VISITOR);
			h.setBlock(far, Blocks.CHEST);
			h.setBlock(near, Blocks.CHEST);
			h.setBlock(wall, Blocks.AIR);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "the leaking Storage reads red", () ->
				h.assertTrue(status(h, storageMarker) != MarkerStatus.VALID, "storage not valid"));
		seq = seq.thenExecute(() -> {
			h.assertFalse(opens(h, v[0], chest1()), "the leaking scan does not open the guard: the last enclosed scan is used");
			try {
				VillageState saved = VillageTestSupport.reload(server(h)).village().orElseThrow();
				h.assertTrue(saved.building(loc(h, storageMarker)).isPresent(), "the Storage record survives in the saved data");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			BuildingSystem.resetRuntimeForTests();
			h.assertFalse(opens(h, v[0], chest1()), "after a restart the fallback box around the saved marker protects the chest");
			h.assertFalse(opens(h, v[0], near), "...and a chest within 16 blocks of it");
			h.assertTrue(opens(h, v[0], far), "...but not one far from every marker");
			h.setBlock(wall, Blocks.OAK_PLANKS);
		});
		seq = awaitWithin(h, seq, bound(2, 0) + 20, "the Storage is green again and both rooms have an enclosed scan", () -> {
			h.assertTrue(status(h, storageMarker) == MarkerStatus.VALID, "storage valid");
			h.assertTrue(Buildings.guardAreas(server(h)).stream().allMatch(a -> a.enclosed().isPresent()), "both rescanned");
		});
		seq.thenExecute(() -> {
			h.assertFalse(opens(h, v[0], chest1()), "with a fresh enclosed scan the chest is protected");
			h.assertTrue(opens(h, v[0], near), "a chest 10 blocks outside the room is free: the box is no longer used");
			done(h);
		});
	}

	// ---- T-L2-7 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:ranks_cmd", structure = YARD, skyAccess = true, maxTicks = 300)
	public void opCommandsHandAnAbandonedVillage(GameTestHelper h) {
		begin(h).thenExecute(() -> {
			MinecraftServer server = server(h);
			ServerPlayer a = owner();
			ServerPlayer b = ranked(h, "bee", Rank.VISITOR);
			ServerPlayer c = ranked(h, "cee", Rank.VISITOR);
			ServerPlayer d = ranked(h, "dee", Rank.VISITOR);
			Console out = new Console();
			h.assertTrue(VillageClaims.current(server).orElseThrow().knownPlayer(b.getUUID()).isPresent(), "the join recorded a visitor");

			out.run(h, "villageheart owner Nobody");
			h.assertValueEqual("message.villageheart.rank.cmd.unknown", out.lastKey(), "never seen");
			h.assertValueEqual(a.getUUID(), VillageClaims.current(server).orElseThrow().owner(), "owner unchanged");

			out.runAs(b, "villageheart owner bee");
			h.assertValueEqual(a.getUUID(), VillageClaims.current(server).orElseThrow().owner(), "a non-op cannot take the village");

			out.run(h, "villageheart rank owner visitor");
			h.assertValueEqual(Rejection.OWNER_RANK_FIXED.langKey(), out.lastKey(), "the owner's rank is fixed");

			out.run(h, "villageheart rank cee resident");
			h.assertValueEqual(Rank.RESIDENT, VillageClaims.current(server).orElseThrow().rankOf(c.getUUID()), "rank works for an online player");

			leave(h, a);
			out.run(h, "villageheart owner bee");
			VillageState v = VillageClaims.current(server).orElseThrow();
			h.assertValueEqual(b.getUUID(), v.owner(), "owner is bee, while the old owner is offline");
			h.assertValueEqual(Rank.STEWARD, v.rankOf(a.getUUID()), "the old owner is a Steward");
			h.assertTrue(b.gameMode.destroyBlock(heartPos(h)),
					"the new owner can break the Heart");
			h.assertTrue(VillageClaims.current(server).isEmpty(), "...and the claim is released");
			done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:ranks_cap", structure = YARD, skyAccess = true, maxTicks = 300)
	public void rankCommandHonoursTheCapWithoutPartialChange(GameTestHelper h) {
		begin(h).thenExecute(() -> {
			MinecraftServer server = server(h);
			ServerPlayer d = ranked(h, "dee", Rank.VISITOR);
			VillageClaims.data(server).update(v -> {
				VillageState s = v;
				for (int i = 1; i <= dev.villageheart.core.Balance.MAX_KNOWN_PLAYERS - 1; i++) {
					s = s.withRank(new java.util.UUID(0, i), "m" + i, Rank.RESIDENT, i);
				}
				return s;
			});
			VillageState before = VillageClaims.current(server).orElseThrow();
			Console out = new Console();
			out.run(h, "villageheart rank dee resident");
			h.assertValueEqual(Rejection.TOO_MANY_MEMBERS.langKey(), out.lastKey(), "at the cap");
			h.assertValueEqual(before.ranks(), VillageClaims.current(server).orElseThrow().ranks(), "no partial change");
			h.assertValueEqual(Rank.VISITOR, VillageClaims.current(server).orElseThrow().rankOf(d.getUUID()), "still a visitor");
			done(h);
		});
	}

	// ---- T-L2-4 (roadmap) ---------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:ranks_hire", structure = YARD, skyAccess = true, maxTicks = 700)
	public void stewardCanHire(GameTestHelper h) {
		Villager[] v = new Villager[1];
		BuildingTestSupport.RecordingPlayer[] vis = new BuildingTestSupport.RecordingPlayer[1];
		List<Sent>[] sent = new List[1];
		seed(h).thenExecute(() -> {
			sent[0] = recorder();
			v[0] = villager(h);
			vis[0] = chatty(h, "vis", Rank.VISITOR);
			open(h, owner());
			open(h, vis[0]);
			assign(vis[0], v[0], "farmer", Optional.empty());
			HeartActionResultPayload r = lastResult(sent[0], vis[0]);
			h.assertTrue(!r.ok() && r.key().equals(Rejection.NO_PERMISSION.langKey()), "a Visitor cannot hire: " + r);
			h.assertFalse(isWorker(h, v[0]), "not a worker");

			setRank(owner(), vis[0].getUUID(), Rank.STEWARD.ordinal());
			HeartActionResultPayload set = lastResult(sent[0], owner());
			h.assertTrue(set.ok() && set.key().equals("message.villageheart.rank.set"), "the Owner made V a Steward: " + set);
			h.assertTrue(hasChat(vis[0], "message.villageheart.rank.you_are"), "V was told: " + vis[0].messages);

			assign(vis[0], v[0], "farmer", Optional.empty());
			HeartActionResultPayload hired = lastResult(sent[0], vis[0]);
			h.assertTrue(hired.ok() && hired.key().equals("message.villageheart.hire.hired"), "the new Steward hires: " + hired);
			h.assertTrue(isWorker(h, v[0]), "the villager is a worker");
			done(h);
		});
	}

	// ---- T-L2-5 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:ranks_validate", structure = YARD, skyAccess = true, maxTicks = 700)
	public void setRankAndTransferValidation(GameTestHelper h) {
		BlockPos table = new BlockPos(3, 1, 20);
		seed(h).thenExecute(() -> {
			List<Sent> sent = recorder();
			MinecraftServer server = server(h);
			ServerPlayer o = owner();
			ServerPlayer s = ranked(h, "stew", Rank.STEWARD);
			ServerPlayer r = ranked(h, "res", Rank.RESIDENT);
			ServerPlayer q = ranked(h, "quin", Rank.RESIDENT);
			ServerPlayer vis = ranked(h, "vis", Rank.VISITOR);
			h.setBlock(table, Blocks.CRAFTING_TABLE);
			open(h, o);
			open(h, s);

			setRank(s, vis.getUUID(), 2);
			h.assertValueEqual(Rejection.NO_PERMISSION.langKey(), lastResult(sent, s).key(), "a Steward cannot rank");
			h.assertValueEqual(Rank.VISITOR, rankOf(server, vis), "unchanged");
			transfer(s, s.getUUID(), 1);
			h.assertValueEqual(Rejection.NO_PERMISSION.langKey(), lastResult(sent, s).key(), "a Steward cannot transfer");

			HeartActions.reset();
			setRank(o, o.getUUID(), 1);
			h.assertValueEqual(Rejection.OWNER_RANK_FIXED.langKey(), lastResult(sent, o).key(), "the Owner's rank is fixed");
			setRank(o, vis.getUUID(), 99);
			h.assertValueEqual(Rejection.BAD_RANK.langKey(), lastResult(sent, o).key(), "a crafted rank");
			setRank(o, java.util.UUID.randomUUID(), 2);
			h.assertValueEqual(Rejection.UNKNOWN_PLAYER.langKey(), lastResult(sent, o).key(), "a stranger who is not here");

			use(h, r, chest1());
			h.assertTrue(r.containerMenu instanceof ChestMenu, "R has the Storage chest open");
			use(h, q, table);
			h.assertTrue(q.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu, "Q has a crafting table open");
			HeartActions.reset();
			setRank(o, r.getUUID(), 3);
			setRank(o, q.getUUID(), 3);
			h.assertValueEqual(Rank.VISITOR, rankOf(server, r), "R is a Visitor now");
			h.assertTrue(r.containerMenu == r.inventoryMenu, "R's chest menu was closed by the demotion");
			h.assertTrue(q.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu, "Q's crafting table stays open");
			h.assertFalse(opens(h, r, chest1()), "R cannot open the chest again");

			HeartActions.reset();
			transfer(o, s.getUUID(), Rank.RESIDENT.ordinal());
			h.assertValueEqual(Rejection.TARGET_CHANGED.langKey(), lastResult(sent, o).key(), "S is a Steward, not a Resident");
			transfer(o, vis.getUUID(), Rank.VISITOR.ordinal());
			h.assertValueEqual(Rejection.NOT_A_MEMBER.langKey(), lastResult(sent, o).key(), "a Visitor cannot receive the village");
			h.assertValueEqual(o.getUUID(), VillageClaims.current(server).orElseThrow().owner(), "still the owner");
			transfer(o, s.getUUID(), Rank.STEWARD.ordinal());
			h.assertValueEqual("message.villageheart.rank.transferred", lastResult(sent, o).key(), "transferred");
			VillageState v = VillageClaims.current(server).orElseThrow();
			h.assertValueEqual(s.getUUID(), v.owner(), "S owns the village");
			h.assertValueEqual("stew", v.ownerName(), "ownerName follows");
			h.assertValueEqual(Rank.STEWARD, v.rankOf(o.getUUID()), "the old owner is a Steward");
			h.assertFalse(o.gameMode.destroyBlock(heartPos(h)), "the old owner can no longer break the Heart");
			h.assertTrue(VillageClaims.current(server).isPresent(), "still claimed");

			try {
				VillageState saved = VillageTestSupport.reload(server).village().orElseThrow();
				h.assertValueEqual(v.owner(), saved.owner(), "saved owner");
				h.assertValueEqual(v.ranks(), saved.ranks(), "saved ranks");
				h.assertValueEqual(v.known(), saved.known(), "saved names");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			done(h);
		});
	}

	private static Rank rankOf(MinecraftServer server, ServerPlayer p) {
		return VillageClaims.current(server).orElseThrow().rankOf(p.getUUID());
	}

	// ---- T-L2-8 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:ranks_names", structure = YARD, skyAccess = true, maxTicks = 300)
	public void namesFollowRenames(GameTestHelper h) {
		begin(h).thenExecute(() -> {
			MinecraftServer server = server(h);
			ServerPlayer r = ranked(h, "Rae", Rank.RESIDENT);
			h.assertValueEqual("Rae", VillageClaims.current(server).orElseThrow().name(r.getUUID()), "ranking recorded the name");
			leave(h, r);
			ServerPlayer r2 = joinAs(h, r.getUUID(), "Rae2");
			h.assertValueEqual("Rae2", VillageClaims.current(server).orElseThrow().name(r.getUUID()), "the JOIN listener recorded the rename");
			h.assertValueEqual(Rank.RESIDENT, VillageClaims.current(server).orElseThrow().rankOf(r.getUUID()), "...and the rank stayed");

			ServerPlayer o = owner();
			leave(h, o);
			ServerPlayer o2 = joinAs(h, o.getUUID(), "Olga2");
			h.assertValueEqual("Olga2", VillageClaims.current(server).orElseThrow().ownerName(), "the owner's rename updates ownerName");
			h.assertTrue(snapshot(h, Rank.OWNER).overview().ownerName().equals("Olga2"), "...and the Overview shows it");

			ServerPlayer v = joinAs(h, java.util.UUID.randomUUID(), "Vera");
			h.assertTrue(VillageClaims.current(server).orElseThrow().knownPlayer(v.getUUID()).isPresent(), "a visitor joining is in the name book");
			leave(h, r2);
			leave(h, o2);
			leave(h, v);
			done(h);
		});
	}
}
