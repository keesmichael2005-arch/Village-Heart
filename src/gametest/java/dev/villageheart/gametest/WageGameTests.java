package dev.villageheart.gametest;

import static dev.villageheart.gametest.LumberTestSupport.axe;
import static dev.villageheart.gametest.LumberTestSupport.chest;
import static dev.villageheart.gametest.LumberTestSupport.growOak;
import static dev.villageheart.gametest.LumberTestSupport.logsOf;
import static dev.villageheart.gametest.LumberTestSupport.putInChest;
import static dev.villageheart.gametest.LumberTestSupport.standing;
import static dev.villageheart.gametest.WageTestSupport.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.heart.HeartAction;
import dev.villageheart.core.heart.Rejection;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.economy.VillageDawn;
import dev.villageheart.heart.HeartActions;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Layer 2: the treasury and the wages (M12). Rules in {@link WageTestSupport}. Hires happen with wages still off (a hire is charged once
 * wages are on), then {@code wagesOn()}; a dawn is crossed with {@code toDawn}. Measured numbers are logged as {@code [m12] ...}.
 */
public class WageGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static MinecraftServer server(GameTestHelper h) { return h.getLevel().getServer(); }

	private static int carried(ServerPlayer p) { return p.getInventory().countItem(Items.EMERALD); }

	private static List<UUID> ids(Villager... vs) { return java.util.Arrays.stream(vs).map(Villager::getUUID).toList(); }

	// ---- T-L2-1 (roadmap) -----------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_three", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void threeWorkersTwoEmeraldsOneStrikes(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[3];
		PayResult[] run = new PayResult[1];
		BlockPos[] spots = {new BlockPos(3, 0, 14), new BlockPos(3, 0, 22), new BlockPos(3, 0, 29), new BlockPos(14, 0, 24)};
		BlockPos[] bases = new BlockPos[4];
		@SuppressWarnings("unchecked") Set<BlockPos>[] logs = new Set[4];
		double[] min = {1e9, 1e9, 1e9};
		boolean[] sampling = new boolean[1];
		h.onEachTick(() -> {
			if (!sampling[0]) return;
			for (int i : new int[] {0, 2}) {
				if (v[i] == null) continue;
				for (int t = 0; t < 4; t++) if (bases[t] != null) min[i] = Math.min(min[i], distAbs(v[i], bases[t]));
			}
		});
		GameTestSequence seq = scene(h, 2, 1).thenExecute(() -> {
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			putInChest(h, chest(), 1, axe(ToolTier.STONE));
			for (int i = 0; i < 3; i++) {
				bases[i] = growOak(h, spots[i], 10L + i);
				logs[i] = logsOf(h, bases[i]);
			}
			v[0] = hireLumberjack(h, 0); // A
			v[1] = hireFarmer(h);         // B
			v[2] = hireLumberjack(h, 3);  // C
			wagesOn();
			deposit(h, 2); // nobody is unpaid yet, so nothing settles
			h.assertValueEqual(2, treasury(h), "treasury before the dawn");
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			PayResult r = run[0];
			h.assertTrue(r != null, "a wage run happened");
			h.assertValueEqual(ids(v[0], v[1]), r.paid(), "A and B are paid, in hiring order");
			h.assertValueEqual(ids(v[2]), r.unpaidIds(), "C is on strike");
			h.assertValueEqual(0, treasury(h), "treasury after the dawn");
			h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[2]), "C's status");
			h.assertFalse(paid(v[2]), "C's needs.paid is false within the dawn tick");
			h.assertTrue(paid(v[0]) && paid(v[1]), "A and B stay paid");
			h.assertTrue(status(h, v[0]) != WorkerStatus.ON_STRIKE, "A is not on strike");
			setPhase(h, 900); // same day index: no second run
			sampling[0] = true;
		});
		// 2500 ticks of the working day, checking C's status every 100
		for (int k = 0; k < 25; k++) {
			seq = seq.thenIdle(100).thenExecute(() -> h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[2]), "C stays on strike"));
		}
		seq = seq.thenExecute(() -> {
			int felled = 0;
			for (int i = 0; i < 3; i++) felled += logs[i].size() - standing(h, logs[i]);
			log("threeWorkersTwoEmeraldsOneStrikes: A closest to a tree " + min[0] + ", C closest " + min[2] + ", logs felled by the day's first 2500 ticks " + felled);
			h.assertTrue(felled >= 1, "the control: the paid lumberjack A felled at least one log (" + felled + ")");
			h.assertTrue(min[0] < 4.5, "A walked up to a tree (closest " + min[0] + ")");
			h.assertTrue(min[2] > 5.0, "C never went near a tree while on strike (closest " + min[2] + ")");
			// capability: a deposit ends the strike and C then goes to work on a fresh tree
			bases[3] = growOak(h, spots[3], 20L);
			logs[3] = logsOf(h, bases[3]);
			min[2] = 1e9;
			deposit(h, 1);
			h.assertTrue(payroll(h).unpaid().isEmpty(), "the deposit paid C");
			h.assertTrue(paid(v[2]), "C's needs.paid is true in the deposit tick");
			h.assertValueEqual(0, treasury(h), "the emerald went to C's wage");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2400, "C reaching a tree once paid",
				() -> h.assertTrue(min[2] < 4.5, "C walked up to a tree (closest " + min[2] + ")"));
		seq.thenExecute(() -> {
			h.assertTrue(status(h, v[2]) != WorkerStatus.ON_STRIKE, "C is no longer on strike");
			done(h);
		});
	}

	// ---- T-L2-2 ---------------------------------------------------------------------------------

	private static InteractionResult click(GameTestHelper h, ServerPlayer p, ItemStack stack) {
		p.closeContainer();
		HeartTestSupport.standNearHeart(h, p);
		return BuildingTestSupport.rightClickAbs(h.getLevel(), p, HeartTestSupport.heartPos(h), Direction.UP, stack);
	}

	@GameTest(environment = "villageheart-gametest:wages_click", structure = YARD, skyAccess = true, maxTicks = 900)
	public void depositByRightClick(GameTestHelper h) {
		ServerPlayer[] p = new ServerPlayer[3]; // resident, steward, visitor
		GameTestSequence seq = HeartTestSupport.seed(h).thenExecute(() -> {
			p[0] = HeartTestSupport.player(h, "res", Rank.RESIDENT);
			p[1] = HeartTestSupport.player(h, "stew", Rank.STEWARD);
			p[2] = VillageTestSupport.player(h, "vis", GameType.SURVIVAL, HeartTestSupport.OverlayPlayer::new);
			HeartTestSupport.standNearHeart(h, p[2]);
			ServerPlayer o = BuildingTestSupport.owner();
			HeartTestSupport.standNearHeart(h, o);

			InteractionResult r = click(h, p[0], new ItemStack(Items.EMERALD, 10));
			h.assertTrue(r.consumesAction(), "the Resident's click is used");
			h.assertValueEqual(10, treasury(h), "Resident +10");
			h.assertTrue(p[0].getMainHandItem().isEmpty(), "the Resident's hand is empty");
			h.assertTrue(p[0].containerMenu == p[0].inventoryMenu, "no screen opened for a deposit");

			click(h, p[1], new ItemStack(Items.EMERALD, 64));
			h.assertValueEqual(74, treasury(h), "Steward +64");

			o.setGameMode(GameType.CREATIVE);
			click(h, o, new ItemStack(Items.EMERALD, 5));
			h.assertValueEqual(79, treasury(h), "creative Owner +5");
			h.assertTrue(o.getMainHandItem().isEmpty(), "what goes in leaves the hand, creative too");
			o.setGameMode(GameType.SURVIVAL);

			ItemStack visitorStack = new ItemStack(Items.EMERALD, 5);
			InteractionResult vr = click(h, p[2], visitorStack);
			h.assertValueEqual(79, treasury(h), "a Visitor deposits nothing");
			h.assertValueEqual(5, p[2].getMainHandItem().getCount(), "the Visitor keeps its emeralds");
			h.assertTrue(((HeartTestSupport.OverlayPlayer) p[2]).overlays.stream().anyMatch(c -> c.getString().contains("Only Residents and above")
					|| c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("message.villageheart.treasury.not_allowed")),
					"the Visitor is told why");
			h.assertTrue(p[2].containerMenu instanceof HeartMenu, "the Visitor's Overview screen opened (" + vr + ")");

			o.setShiftKeyDown(true);
			click(h, o, new ItemStack(Items.EMERALD, 3));
			o.setShiftKeyDown(false);
			h.assertValueEqual(79, treasury(h), "sneaking skips the block");
			h.assertValueEqual(3, o.getMainHandItem().getCount(), "the sneaking Owner keeps 3");

			click(h, o, new ItemStack(Items.STICK));
			h.assertTrue(o.containerMenu instanceof HeartMenu, "a stick opens the screen as before");

			o.closeContainer();
			o.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 4));
			click(h, o, ItemStack.EMPTY);
			h.assertValueEqual(79, treasury(h), "emeralds in the off hand are not deposited");
			h.assertTrue(o.containerMenu instanceof HeartMenu, "an empty main hand opens the screen");
			h.assertValueEqual(4, o.getOffhandItem().getCount(), "the off hand is untouched");
			o.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

			// a Heart that does not hold the village
			BlockPos other = new BlockPos(20, 1, 25);
			h.setBlock(other, dev.villageheart.block.ModBlocks.VILLAGE_HEART);
			p[0].closeContainer();
			p[0].setPos(h.absolutePos(other).getX() + 0.5, h.absolutePos(other).getY(), h.absolutePos(other).getZ() + 2.5);
			BuildingTestSupport.rightClickAbs(h.getLevel(), p[0], h.absolutePos(other), Direction.UP, new ItemStack(Items.EMERALD, 4));
			h.assertValueEqual(79, treasury(h), "an unclaimed Heart takes nothing");
			h.assertValueEqual(4, p[0].getMainHandItem().getCount(), "the emeralds stay");
			h.setBlock(other, net.minecraft.world.level.block.Blocks.AIR);

			// the cap
			VillageClaims.data(server(h)).update(s -> s.withTreasury(Balance.MAX_TREASURY - 3));
			click(h, p[1], new ItemStack(Items.EMERALD, 10));
			h.assertValueEqual(Balance.MAX_TREASURY, treasury(h), "the treasury fills up to the cap");
			h.assertValueEqual(7, p[1].getMainHandItem().getCount(), "7 stay in the hand");
			click(h, p[1], new ItemStack(Items.EMERALD, 7));
			h.assertValueEqual(Balance.MAX_TREASURY, treasury(h), "a full treasury takes nothing");
			h.assertValueEqual(7, p[1].getMainHandItem().getCount(), "all stay");
			done(h);
		});
	}

	// ---- T-L2-3 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_screen", structure = YARD, skyAccess = true, maxTicks = 900)
	public void treasuryScreenActions(GameTestHelper h) {
		ServerPlayer[] p = new ServerPlayer[2]; // resident, steward
		List<HeartTestSupport.Sent>[] sent = new List[1];
		GameTestSequence seq = HeartTestSupport.seed(h).thenExecute(() -> {
			sent[0] = HeartTestSupport.recorder();
			p[0] = HeartTestSupport.player(h, "res", Rank.RESIDENT);
			p[1] = HeartTestSupport.player(h, "stew", Rank.STEWARD);
			ServerPlayer o = BuildingTestSupport.owner();
			HeartTestSupport.open(h, p[0]);
			HeartTestSupport.open(h, p[1]);
			HeartTestSupport.open(h, o);
			p[0].getInventory().add(new ItemStack(Items.EMERALD, 64));
			p[0].getInventory().add(new ItemStack(Items.EMERALD, 6));
			h.assertValueEqual(70, carried(p[0]), "the Resident carries 70");
			act(p[0], new HeartAction.Deposit());
			h.assertValueEqual(70, treasury(h), "deposit all");
			h.assertValueEqual(0, carried(p[0]), "the emeralds left the inventory");
			h.assertTrue(HeartTestSupport.lastResult(sent[0], p[0]).ok(), "deposit reply ok");
			h.assertValueEqual("message.villageheart.treasury.deposited", HeartTestSupport.lastResult(sent[0], p[0]).key(), "deposit reply key");

			act(p[1], new HeartAction.Withdraw(10));
			h.assertValueEqual(Rejection.NO_PERMISSION.langKey(), HeartTestSupport.lastResult(sent[0], p[1]).key(), "a Steward cannot withdraw");
			act(p[0], new HeartAction.Withdraw(1));
			h.assertValueEqual(Rejection.NO_PERMISSION.langKey(), HeartTestSupport.lastResult(sent[0], p[0]).key(), "a Resident cannot withdraw");
			h.assertValueEqual(70, treasury(h), "nothing left the treasury");

			act(o, new HeartAction.Withdraw(10));
			h.assertValueEqual(60, treasury(h), "the Owner withdraws 10");
			h.assertValueEqual(10, carried(o), "and carries them");
			act(o, new HeartAction.Withdraw(64));
			h.assertValueEqual(0, treasury(h), "withdraw 64 with 60 takes 60");
			h.assertValueEqual(70, carried(o), "70 emeralds in total");
			act(o, new HeartAction.Withdraw(1));
			h.assertValueEqual("message.villageheart.treasury.empty", HeartTestSupport.lastResult(sent[0], o).key(), "an empty treasury says so");
			act(o, new HeartAction.Withdraw(65));
			h.assertValueEqual(Rejection.BAD_AMOUNT.langKey(), HeartTestSupport.lastResult(sent[0], o).key(), "65 is a bad amount");
			act(o, new HeartAction.Withdraw(0));
			h.assertValueEqual(Rejection.BAD_AMOUNT.langKey(), HeartTestSupport.lastResult(sent[0], o).key(), "0 is a bad amount");

			// a full inventory: what does not fit drops at the player
			act(p[0], new HeartAction.Deposit()); // nothing carried
			h.assertValueEqual("message.villageheart.treasury.no_emeralds", HeartTestSupport.lastResult(sent[0], p[0]).key(), "nothing to deposit");
			VillageClaims.data(server(h)).update(s -> s.withTreasury(20));
			for (int i = 0; i < p[0].getInventory().getContainerSize(); i++) p[0].getInventory().setItem(i, new ItemStack(Items.STONE, 64));
			p[0].setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 64));
			ServerPlayer rich = BuildingTestSupport.owner();
			for (int i = 0; i < rich.getInventory().getContainerSize(); i++) rich.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
			rich.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 64));
			act(rich, new HeartAction.Withdraw(5));
			h.assertValueEqual(15, treasury(h), "the full-inventory withdraw still takes from the treasury");
			h.assertValueEqual(5, VillageTestSupport.emeralds(h, HeartTestSupport.heartPos(h).subtract(h.absolutePos(BlockPos.ZERO)), 8) , "5 emeralds dropped at the Owner's feet");
			for (int i = 0; i < rich.getInventory().getContainerSize(); i++) rich.getInventory().setItem(i, ItemStack.EMPTY);
			rich.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			for (int i = 0; i < p[0].getInventory().getContainerSize(); i++) p[0].getInventory().setItem(i, ItemStack.EMPTY);
			p[0].setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

			// the cap: a deposit into a full treasury (the max == 0 trap of clearOrCountMatchingItems)
			VillageClaims.data(server(h)).update(s -> s.withTreasury(Balance.MAX_TREASURY));
			p[0].getInventory().add(new ItemStack(Items.EMERALD, 3));
			act(p[0], new HeartAction.Deposit());
			h.assertValueEqual(3, carried(p[0]), "the Resident keeps its 3 emeralds: the treasury is full");
			h.assertValueEqual(Balance.MAX_TREASURY, treasury(h), "full stays full");
		});
		seq = seq.thenIdle(25).thenExecute(() -> {
			var last = HeartTestSupport.snapshotsTo(sent[0], BuildingTestSupport.owner());
			h.assertTrue(!last.isEmpty(), "the Owner's open menu got snapshots");
			h.assertValueEqual(Balance.MAX_TREASURY, last.get(last.size() - 1).snapshot().overview().treasury(), "within 21 ticks the open menu shows the new treasury");
			HeartTestSupport.done(h);
		});
	}

	private static void act(ServerPlayer p, HeartAction a) {
		HeartActions.RATE.forget(p.getUUID());
		HeartActions.handle(p, p.containerMenu.containerId, a);
	}

	// ---- T-L2-4 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_ends", structure = YARD, skyAccess = true, maxTicks = 7000)
	public void depositEndsStrike(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[2];
		PayResult[] run = new PayResult[1];
		ServerPlayer[] res = new ServerPlayer[1];
		BlockPos[] base = new BlockPos[1];
		@SuppressWarnings("unchecked") Set<BlockPos>[] logs = new Set[1];
		int[] before = new int[1];
		GameTestSequence seq = scene(h, 2, 0).thenExecute(() -> {
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			putInChest(h, chest(), 1, axe(ToolTier.STONE));
			base[0] = growOak(h, new BlockPos(3, 0, 16), 30L);
			logs[0] = logsOf(h, base[0]);
			v[0] = hireLumberjack(h, 0);
			res[0] = HeartTestSupport.player(h, "res", Rank.RESIDENT);
			wagesOn();
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[0]), "C is on strike after the dawn");
			setPhase(h, 2000);
			before[0] = standing(h, logs[0]);
			click(h, res[0], new ItemStack(Items.EMERALD, 3));
			h.assertTrue(payroll(h).unpaid().isEmpty(), "unpaid is empty in the deposit tick");
			h.assertValueEqual(2, treasury(h), "3 in, the wage of 1 out");
			h.assertTrue(paid(v[0]), "needs.paid is true in the deposit tick");
			h.assertTrue(status(h, v[0]) != WorkerStatus.ON_STRIKE, "no longer on strike");
			click(h, res[0], new ItemStack(Items.EMERALD, 1));
			h.assertValueEqual(3, treasury(h), "a second deposit pays nobody again");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, LumberTestSupport.fellBound(1, 30, ToolTier.STONE), "a log falling once paid",
				() -> h.assertTrue(standing(h, logs[0]) < before[0], "a log fell"));
		seq = seq.thenExecute(() -> {
			// treasury 0, then a second lumberjack: charged at once, strikes at once; `set` settles
			VillageClaims.data(server(h)).update(s -> s.withTreasury(0));
			v[1] = hireLumberjack(h, 3);
			h.assertTrue(payroll(h).isUnpaid(v[1].getUUID()), "a hire with an empty treasury strikes at once");
			h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[1]), "status of the new hire");
			h.assertFalse(paid(v[1]), "needs.paid of the new hire is false from its first tick");
			dev.villageheart.economy.Treasury.set(server(h), 1);
			h.assertTrue(payroll(h).unpaid().isEmpty(), "treasury set 1 settled the new hire");
			h.assertValueEqual(0, treasury(h), "its wage was paid out of the new value");
			done(h);
		});
	}

	// ---- T-L2-5 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_oncepay", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void onePayPerDayNoCatchUp(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		PayResult[] run = new PayResult[1];
		int[] dawns = new int[1];
		VillageDawn.Registration[] reg = new VillageDawn.Registration[1];
		GameTestSequence seq = scene(h, 2, 0).thenExecute(() -> {
			reg[0] = VillageDawn.register("test:count", (srv, village, day, wages) -> dawns[0]++);
			hireLumberjack(h, 0);
			hireLumberjack(h, 3);
			wagesOn();
			deposit(h, 10);
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(8, treasury(h), "the first dawn costs 2");
			h.assertValueEqual(1, dawns[0], "one dawn event");
		});
		seq = seq.thenIdle(400).thenExecute(() -> {
			h.assertValueEqual(8, treasury(h), "400 ticks later still 8");
			// three days at once: one run
			h.getLevel().setDayTime(h.getLevel().getDayTime() + 3 * 24000L);
		});
		seq = seq.thenIdle(5).thenExecute(() -> {
			h.assertValueEqual(6, treasury(h), "a jump over three days pays once");
			h.assertValueEqual(dayIndex(h), payroll(h).lastWageDay(), "lastWageDay is the new index");
			h.assertValueEqual(2, dawns[0], "two dawn events so far");
		});
		seq = seq.thenIdle(100).thenExecute(() -> {
			h.assertValueEqual(6, treasury(h), "no second run for the same day");
			h.getLevel().setDayTime(1000); // back to day index 0
		});
		seq = seq.thenIdle(5).thenExecute(() -> {
			h.assertValueEqual(4, treasury(h), "going back to day 0 pays once");
			h.assertValueEqual(0L, payroll(h).lastWageDay(), "lastWageDay moved back to 0");
			wagesOff();
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(4, treasury(h), "a dawn with wages off costs nothing");
			h.assertValueEqual(1L, payroll(h).lastWageDay(), "but the day is consumed");
			h.assertValueEqual(4, dawns[0], "the dawn event still fires with wages off (PayResult.none)");
			wagesOn();
		});
		seq = seq.thenIdle(60).thenExecute(() -> {
			h.assertValueEqual(4, treasury(h), "switching wages on does not fire the stale run");
			reg[0].close();
			done(h);
		});
	}

	// ---- T-L2-6 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_frozen", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void frozenWhileHeartUnloaded(GameTestHelper h) {
		VillageTestSupport.reset(h);
		ServerLevel level = h.getLevel();
		BlockPos near = h.absolutePos(BlockPos.ZERO);
		BlockPos heart = new BlockPos(near.getX() + 1, near.getY() + 1, near.getZ() + 100_000);
		BlockPos bell = heart.east(2);
		int[] dawns = new int[1];
		long[] dayBefore = new long[1];
		VillageDawn.Registration[] reg = new VillageDawn.Registration[1];
		ServerPlayer[] owner = new ServerPlayer[1];
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			owner[0] = VillageTestSupport.player(h, "far_owner", GameType.SURVIVAL);
			level.getChunk(heart.getX() >> 4, heart.getZ() >> 4);
			level.setChunkForced(heart.getX() >> 4, heart.getZ() >> 4, true);
			level.setBlock(heart.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(bell.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(bell, net.minecraft.world.level.block.Blocks.BELL.defaultBlockState(), 3);
			level.setBlock(heart, dev.villageheart.block.ModBlocks.VILLAGE_HEART.defaultBlockState(), 3);
		});
		seq = seq.thenIdle(5).thenExecute(() -> {
			dev.villageheart.core.ClaimRules.Result r = VillageClaims.claim(level, heart, owner[0]);
			h.assertValueEqual(dev.villageheart.core.ClaimRules.Result.CLAIMED, r, "the far village was claimed");
			reg[0] = VillageDawn.register("test:count", (srv, village, day, wages) -> dawns[0]++);
			wagesOn();
		});
		seq = seq.thenIdle(5).thenExecute(() -> {
			h.assertTrue(VillageClaims.data(server(h)).payroll().isPresent(), "the payroll was initialised on the first tick");
			dayBefore[0] = payroll(h).lastWageDay();
			level.setChunkForced(heart.getX() >> 4, heart.getZ() >> 4, false);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the far Heart chunk unloading", () -> h.assertFalse(level.isLoaded(heart), "the Heart chunk is unloaded"));
		seq = seq.thenExecute(() -> level.setDayTime(level.getDayTime() + 2 * 24000L));
		seq = seq.thenIdle(60).thenExecute(() -> {
			h.assertFalse(level.isLoaded(heart), "still unloaded");
			h.assertValueEqual(dayBefore[0], payroll(h).lastWageDay(), "no wage run while the village is frozen");
			h.assertValueEqual(0, dawns[0], "no dawn event while frozen");
			level.getChunk(heart.getX() >> 4, heart.getZ() >> 4);
			level.setChunkForced(heart.getX() >> 4, heart.getZ() >> 4, true);
		});
		seq = seq.thenIdle(10).thenExecute(() -> {
			h.assertTrue(level.isLoaded(heart), "loaded again");
			h.assertValueEqual(dayIndex(h), payroll(h).lastWageDay(), "one run on load: the clock is at today");
			h.assertValueEqual(1, dawns[0], "exactly one dawn event on load, not one per missed day");
		});
		seq = seq.thenIdle(60).thenExecute(() -> {
			h.assertValueEqual(1, dawns[0], "and no second");
			reg[0].close();
			level.setChunkForced(heart.getX() >> 4, heart.getZ() >> 4, false);
			level.setBlock(heart, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(bell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-7 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_reload", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void payrollSurvivesReload(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[2];
		PayResult[] run = new PayResult[1];
		int[] runs = new int[1];
		GameTestSequence seq = scene(h, 2, 0).thenExecute(() -> {
			v[0] = hireLumberjack(h, 0);
			v[1] = hireLumberjack(h, 3);
			Workers.setLevel(server(h), v[1], 3);
			wagesOn();
			deposit(h, 1);
			VillageDawn.register("test:runs", (srv, village, day, wages) -> runs[0]++);
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1, runs[0], "one run so far");
			h.assertValueEqual(ids(v[1]), run[0] == null ? List.<UUID>of() : run[0].unpaidIds(), "the level 3 worker (wage 2) strikes: 2 > 1 left");
			try {
				var back = VillageTestSupport.reload(server(h));
				h.assertValueEqual(payroll(h), back.payroll().orElseThrow(), "the payroll reads back equal");
				h.assertValueEqual(village(h).workers(), back.village().orElseThrow().workers(), "the workers (and their levels) read back equal");
				h.assertValueEqual(village(h).treasury(), back.village().orElseThrow().treasury(), "the treasury reads back equal");
				h.assertValueEqual(3, back.village().orElseThrow().workers().get(1).level(), "the level mirror was saved");
			} catch (java.io.IOException e) {
				throw h.assertionException("reload failed: " + e);
			}
		});
		seq = seq.thenIdle(40).thenExecute(() -> {
			h.assertValueEqual(1, runs[0], "no second run for the same day");
			h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[1]), "the striker is still on strike");
			VillageDawn.removeTestListeners();
			done(h);
		});
	}

	// ---- T-L2-8 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_hire", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void hireIsChargedNoFreeDays(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[2];
		PayResult[] run = new PayResult[1];
		GameTestSequence seq = scene(h, 2, 0).thenExecute(() -> {
			wagesOn();
			deposit(h, 1);
			v[0] = hireLumberjack(h, 0); // W
			h.assertValueEqual(0, treasury(h), "the first hire is charged");
			h.assertTrue(payroll(h).paidToday().contains(v[0].getUUID()), "W is paid for today");
			v[1] = hireLumberjack(h, 3); // X
			h.assertTrue(payroll(h).isUnpaid(v[1].getUUID()), "X strikes at once");
			h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[1]), "X's status");
			Workers.fire(h.getLevel(), v[0]);
			HireResultCheck.rehire(h, v[0], JobType.LUMBERJACK);
			h.assertValueEqual(0, treasury(h), "W re-hired the same day costs nothing");
			h.assertFalse(payroll(h).isUnpaid(v[0].getUUID()), "W is not on strike");
			Workers.fire(h.getLevel(), v[1]);
			HireResultCheck.rehire(h, v[1], JobType.LUMBERJACK);
			h.assertTrue(payroll(h).isUnpaid(v[1].getUUID()), "X re-hired is still on strike");
			h.assertValueEqual(1, payroll(h).unpaid().size(), "and listed once");
			// the exploit: fire both before the dawn, re-hire both after it
			Workers.fire(h.getLevel(), v[0]);
			Workers.fire(h.getLevel(), v[1]);
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			h.assertTrue(run[0] != null && run[0].paid().isEmpty() && run[0].unpaid().isEmpty(), "the dawn found nobody on the books");
			HireResultCheck.rehire(h, v[1], JobType.LUMBERJACK); // X first this time
			HireResultCheck.rehire(h, v[0], JobType.LUMBERJACK);
			h.assertTrue(payroll(h).isUnpaid(v[0].getUUID()) && payroll(h).isUnpaid(v[1].getUUID()), "no free day: both strike with an empty treasury");
			h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[0]), "W's status");
			deposit(h, 2);
			h.assertTrue(payroll(h).unpaid().isEmpty(), "a deposit of 2 settles both");
			h.assertValueEqual(0, treasury(h), "and is spent");
			deposit(h, 1);
		});
		seq = toDawn(h, seq, run);
		seq.thenExecute(() -> {
			h.assertValueEqual(ids(v[1]), run[0].paid(), "the first in the new hiring order (X) is paid");
			h.assertValueEqual(ids(v[0]), run[0].unpaidIds(), "the other strikes");
			h.assertValueEqual(0, treasury(h), "treasury spent");
			done(h);
		});
	}

	/** Hires a villager that was fired a moment ago. */
	private static final class HireResultCheck {
		static void rehire(GameTestHelper h, Villager v, JobType job) {
			var r = Workers.hire(h.getLevel(), v, job, Optional.empty());
			h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "re-hired: " + r);
		}
	}

	// ---- T-L2-9 ---------------------------------------------------------------------------------

	private static void setAttachedLevel(Villager v, int level) {
		WorkerData d = v.getAttached(WorkerAttachments.WORKER);
		v.setAttached(WorkerAttachments.WORKER, d.withProgress(level, d.xp()));
	}

	@GameTest(environment = "villageheart-gametest:wages_levels", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void wagesByLevel(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[3];
		PayResult[] run = new PayResult[1];
		GameTestSequence seq = scene(h, 2, 1).thenExecute(() -> {
			v[0] = hireLumberjack(h, 0);
			v[1] = hireFarmer(h);
			v[2] = hireLumberjack(h, 3);
			// the attachment only: the registry mirror has to follow by itself
			setAttachedLevel(v[0], 1);
			setAttachedLevel(v[1], 2);
			setAttachedLevel(v[2], 3);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 25, "the level mirror", () -> {
			List<Integer> levels = village(h).workers().stream().map(w -> w.level()).toList();
			h.assertValueEqual(List.of(1, 2, 3), levels, "WorkerRecord.level follows the attachments");
		});
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(4, dev.villageheart.heart.HeartFacts.collect(server(h)).orElseThrow().wageBill(), "wage bill 1 + 1 + 2");
			v[2].discard(); // the level 3 worker is not loaded any more: the record has to answer for it
			wagesOn();
			deposit(h, 4);
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(3, run[0].paid().size(), "all three are paid, the unloaded one at its recorded level 3");
			h.assertValueEqual(0, treasury(h), "4 spent");
			deposit(h, 3);
		});
		seq = toDawn(h, seq, run);
		seq.thenExecute(() -> {
			h.assertValueEqual(1, run[0].unpaid().size(), "one strikes with 3 for a bill of 4");
			h.assertValueEqual(v[2].getUUID(), run[0].unpaid().get(0).villager(), "the level 3 worker, hired last");
			h.assertValueEqual(2, run[0].unpaid().get(0).wage(), "at its stored wage");
			h.assertValueEqual(1, treasury(h), "1 left");
			done(h);
		});
	}

	// ---- T-L2-10 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_release", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void releaseClearsPayroll(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[1];
		PayResult[] run = new PayResult[1];
		BlockPos heart = new BlockPos(3, 1, 1);
		int[] drops = new int[1];
		GameTestSequence seq = scene(h, 1, 0).thenExecute(() -> {
			v[0] = hireLumberjack(h, 0);
			Workers.setLevel(server(h), v[0], 3);
			wagesOn();
			deposit(h, 1);
		});
		seq = toDawn(h, seq, run);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(ids(v[0]), run[0].unpaidIds(), "the level 3 worker (wage 2) strikes with 1 emerald");
			h.assertValueEqual(1, treasury(h), "the treasury is untouched");
			drops[0] = VillageTestSupport.emeralds(h, heart, 4);
			boolean ok = BuildingTestSupport.owner().gameMode.destroyBlock(h.absolutePos(heart));
			h.assertTrue(ok, "the Owner broke the Heart");
			h.assertTrue(VillageClaims.current(server(h)).isEmpty(), "the claim is released");
			h.assertValueEqual(1, VillageTestSupport.emeralds(h, heart, 4) - drops[0], "the treasury dropped");
			h.assertTrue(VillageClaims.data(server(h)).payroll().isEmpty(), "the payroll went with the claim");
			VillageTestSupport.place(h, BuildingTestSupport.owner(), heart.below());
		});
		seq = seq.thenIdle(5).thenExecute(() -> {
			h.assertTrue(VillageClaims.current(server(h)).isPresent(), "claimed again");
			h.assertTrue(payroll(h).unpaid().isEmpty(), "no strikers in the new village");
			h.assertValueEqual(dayIndex(h), payroll(h).lastWageDay(), "a fresh clock: today");
			h.assertFalse(dev.villageheart.economy.Wages.onStrike(server(h), v[0].getUUID()), "the old worker is not on strike");
			done(h);
		});
	}

	// ---- T-L2-11 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_claim", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void claimDoesNotCharge(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		h.getLevel().setDayTime(dayIndexOf(h) * 24000L + 5000);
		PayResult[] run = new PayResult[1];
		long[] day = new long[1];
		GameTestSequence seq = scene(h, 1, 0).thenExecute(() -> {
			day[0] = dayIndex(h);
			deposit(h, 5);
			hireLumberjack(h, 0);
			wagesOn();
		});
		seq = seq.thenIdle(40).thenExecute(() -> {
			h.assertValueEqual(day[0], payroll(h).lastWageDay(), "the payroll starts at today");
			h.assertValueEqual(5, treasury(h), "claiming at day time 5000 charged nothing");
		});
		seq = toDawn(h, seq, run);
		seq.thenExecute(() -> {
			h.assertValueEqual(1, run[0].paid().size(), "the first run is at the next dawn");
			h.assertValueEqual(4, treasury(h), "and costs 1");
			done(h);
		});
	}

	private static long dayIndexOf(GameTestHelper h) { return dev.villageheart.core.time.DailyClock.dayIndex(h.getLevel().getDayTime()); }

	// ---- M24 T-L2-6: only the Owner can withdraw ---------------------------------------------------

	@GameTest(environment = "villageheart-gametest:wages_owner", structure = YARD, skyAccess = true, maxTicks = 900)
	public void onlyOwnerCanWithdraw(GameTestHelper h) {
		ServerPlayer[] p = new ServerPlayer[3]; // steward, resident, visitor
		GameTestSequence seq = HeartTestSupport.seed(h).thenExecute(() -> {
			p[0] = HeartTestSupport.player(h, "stew", Rank.STEWARD);
			p[1] = HeartTestSupport.player(h, "res", Rank.RESIDENT);
			p[2] = HeartTestSupport.player(h, "vis", Rank.VISITOR);
			deposit(h, 10);
			ServerPlayer o = BuildingTestSupport.owner();
			for (ServerPlayer x : new ServerPlayer[] {o, p[0], p[1], p[2]}) HeartTestSupport.open(h, x);
			for (ServerPlayer x : p) {
				HeartTestSupport.send(x, dev.villageheart.heart.net.TreasuryActionPayload.CODEC,
						new dev.villageheart.heart.net.TreasuryActionPayload(x.containerMenu.containerId, true, 1));
				h.assertValueEqual(10, treasury(h), x.getName().getString() + " withdraws nothing");
				h.assertValueEqual(0, carried(x), x.getName().getString() + " carries no emerald");
			}
			HeartTestSupport.send(o, dev.villageheart.heart.net.TreasuryActionPayload.CODEC,
					new dev.villageheart.heart.net.TreasuryActionPayload(o.containerMenu.containerId, true, 1));
			h.assertValueEqual(9, treasury(h), "the Owner withdraws 1");
			h.assertValueEqual(1, carried(o), "and carries it");
			dev.villageheart.rank.Members.transfer(server(h), "test", o.getUUID(), p[0].getUUID(), Optional.empty(), false);
		});
		seq = seq.thenIdle(25).thenExecute(() -> {
			ServerPlayer oldOwner = BuildingTestSupport.owner();
			HeartTestSupport.open(h, oldOwner);
			HeartTestSupport.open(h, p[0]);
			HeartTestSupport.send(oldOwner, dev.villageheart.heart.net.TreasuryActionPayload.CODEC,
					new dev.villageheart.heart.net.TreasuryActionPayload(oldOwner.containerMenu.containerId, true, 1));
			h.assertValueEqual(9, treasury(h), "the old Owner can no longer withdraw");
			HeartTestSupport.send(p[0], dev.villageheart.heart.net.TreasuryActionPayload.CODEC,
					new dev.villageheart.heart.net.TreasuryActionPayload(p[0].containerMenu.containerId, true, 1));
			h.assertValueEqual(8, treasury(h), "the new Owner can");
			h.assertValueEqual(1, carried(p[0]), "and carries it");
			HeartTestSupport.done(h);
		});
	}

	// ---- M15 T-L2-4: a worker that lost its attachment comes back at its level ----------------------

	@GameTest(environment = "villageheart-gametest:wages_relevel", structure = YARD, skyAccess = true, maxTicks = 900)
	public void workerComesBackAtItsLevel(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1, 0).thenExecute(() -> {
			v[0] = hireLumberjack(h, 0);
			Workers.setLevel(server(h), v[0], 2);
			h.assertValueEqual(2, village(h).workers().get(0).level(), "setLevel wrote the registry mirror at once");
			v[0].removeAttached(WorkerAttachments.WORKER); // the attachment is lost
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 45, "the attachment coming back", () -> h.assertTrue(WorkerAttachments.isWorker(v[0]), "reinstalled"));
		seq.thenExecute(() -> {
			h.assertValueEqual(2, WorkerAttachments.get(v[0]).orElseThrow().level(), "it is back at level 2, not 1");
			done(h);
		});
	}
}
