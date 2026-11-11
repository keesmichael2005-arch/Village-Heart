package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.room.Pos;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Helpers for the builder GameTests (M20). Rules (docs/TESTING.md, "Builder GameTests"): own environment per test
 * ({@code villageheart-gametest:build_<name>}), the {@code yard} structure with {@code skyAccess}, {@link #layout} first and
 * {@code VillageTestSupport.done} last. The yard is cleaned and grassed first (it is not cleared by the framework).
 */
public final class BuildTestSupport {
	private BuildTestSupport() {}

	public static final String YARD = BuildingTestSupport.YARD;

	public static String env(String name) { return "villageheart-gametest:build_" + name; }

	/** Storage room (5x4 interior) at the west, Builder's Hut at the east, both with their door and marker on the north wall (z = 3). */
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos HUT = WorkerTestSupport.ORIGIN_B;

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 5); }

	public static BlockPos hutMarker() { return BuildingTestSupport.markerPos(HUT, 5); }

	public static BlockPos chest1() { return BuildingTestSupport.inside(STORE, 0, 0, 3); }

	public static BlockPos chest2() { return BuildingTestSupport.inside(STORE, 4, 0, 3); }

	public static BlockPos hutChest() { return BuildingTestSupport.inside(HUT, 4, 0, 3); }

	public static BlockPos table() { return BuildingTestSupport.inside(HUT, 0, 0, 0); }

	/** The clicked ground block of the standard site (a 12 x 12 grass area lies around it; the House box and its ring fit in any rotation). */
	public static final BlockPos GROUND = new BlockPos(15, -1, 20);

	/** The south-facing player position of the item flow tests (yaw 180 = looking north). */
	public static final BlockPos PLAYER_SPOT = new BlockPos(15, 0, 27);

	// ---- the yard --------------------------------------------------------------------------------

	/** Air above, dirt and grass below, no leftover items: the yard is not cleared by the framework. */
	public static void cleanYard(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
			for (int y = 0; y < 27; y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (!h.getBlockState(p).isAir()) h.setBlock(p, Blocks.AIR);
			}
			BlockPos g = new BlockPos(x, -1, z);
			if (!h.getBlockState(g).is(Blocks.GRASS_BLOCK)) h.setBlock(g, Blocks.GRASS_BLOCK);
			BlockPos d = new BlockPos(x, -2, z);
			if (!h.getBlockState(d).is(Blocks.DIRT)) h.setBlock(d, Blocks.DIRT);
		}
		BlockPos a = h.absolutePos(BlockPos.ZERO);
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(a.getX() - 4, a.getY() - 4, a.getZ() - 4, a.getX() + 36, a.getY() + 30, a.getZ() + 36))) {
			e.discard();
		}
	}

	/** Posts with a torch on top at the corners of the site area, outside the stand ring of any placement. */
	public static void lightPosts(GameTestHelper h) {
		for (int[] c : new int[][] {{7, 13}, {23, 13}, {7, 28}, {23, 28}}) {
			h.setBlock(new BlockPos(c[0], 0, c[1]), Blocks.COBBLESTONE);
			h.setBlock(new BlockPos(c[0], 1, c[1]), Blocks.COBBLESTONE);
			h.setBlock(new BlockPos(c[0], 2, c[1]), Blocks.TORCH);
		}
	}

	// ---- the layout ------------------------------------------------------------------------------

	/** The small Storage (3x3 interior) of the GRID layout, in the east strip. */
	public static final BlockPos GRID_STORE = new BlockPos(27, 0, 4);

	/** The four ground blocks of the GRID layout: boxes of a House or a Farm Plot in any rotation fit, with their stand rings, inside the yard. */
	public static final BlockPos[] GRID = {new BlockPos(7, -1, 12), new BlockPos(20, -1, 12), new BlockPos(7, -1, 25), new BlockPos(20, -1, 25)};

	/** What a layout builds besides the claim and the tier. */
	public enum With {
		/** Storage and Builder's Hut, torch posts around the site area. */
		STANDARD,
		/** Storage only, torch posts. */
		STORAGE_NO_HUT,
		/** A small Storage in the east strip and nothing else: four sites fit in a 2 x 2 grid ({@link #GRID}). */
		GRID
	}

	private static BlockPos activeChest1 = chest1(), activeChest2 = chest2();

	/**
	 * Cleans the yard, claims, sets the tier to VILLAGE and builds a valid Storage (two chests) and, for STANDARD, a valid Builder's Hut
	 * (Builder's Table, a chest, a torch). The sequence ends when they are valid.
	 */
	public static GameTestSequence layout(GameTestHelper h, With with) {
		dev.villageheart.worker.build.BuilderJob.listener = null;
		cleanYard(h);
		boolean grid = with == With.GRID;
		BlockPos store = grid ? GRID_STORE : STORE;
		int w = grid ? 3 : 5, d = grid ? 3 : 4;
		BlockPos marker = BuildingTestSupport.markerPos(store, w);
		activeChest1 = BuildingTestSupport.inside(store, 0, 0, d - 1);
		activeChest2 = BuildingTestSupport.inside(store, w - 1, 0, d - 1);
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			BuildingTestSupport.room(h, store, w, d);
			h.setBlock(activeChest1, Blocks.CHEST);
			h.setBlock(activeChest2, Blocks.CHEST);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, marker, Direction.NORTH);
			if (with == With.STANDARD) {
				BuildingTestSupport.room(h, HUT, 5, 4);
				h.setBlock(table(), JobBlocks.BLOCKS.get(JobBlockType.BUILDERS_TABLE));
				h.setBlock(hutChest(), Blocks.CHEST);
				h.setBlock(BuildingTestSupport.inside(HUT, 2, 0, 3), Blocks.TORCH);
				BuildingTestSupport.placeMarker(h, BuildingType.BUILDERS_HUT, hutMarker(), Direction.NORTH);
			}
			if (!grid) lightPosts(h);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID);
			if (with == With.STANDARD) BuildingTestSupport.assertStatus(h, hutMarker(), MarkerStatus.VALID);
		});
	}

	// ---- stock -----------------------------------------------------------------------------------

	/** Adds the stacks to the two Storage chests (first free slots, stack limits respected). */
	public static void stock(GameTestHelper h, ItemStack... stacks) {
		Container[] cs = {StockpileTestSupport.container(h, activeChest1), StockpileTestSupport.container(h, activeChest2)};
		for (ItemStack in : stacks) {
			int left = in.getCount();
			for (Container c : cs) {
				for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
					ItemStack slot = c.getItem(i);
					int max = in.getMaxStackSize();
					if (slot.isEmpty()) {
						int n = Math.min(max, left);
						c.setItem(i, in.copyWithCount(n));
						left -= n;
					} else if (ItemStack.isSameItemSameComponents(slot, in) && slot.getCount() < max) {
						int n = Math.min(max - slot.getCount(), left);
						slot.grow(n);
						left -= n;
					}
				}
				c.setChanged();
			}
			if (left > 0) throw new AssertionError("the Storage chests are full");
		}
	}

	/** The materials of {@code copies} Small Houses. */
	public static ItemStack[] houseMaterials(int copies) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(Items.COBBLESTONE, 49 * copies));
		out.add(new ItemStack(Items.OAK_PLANKS, 119 * copies));
		out.add(new ItemStack(Items.OAK_DOOR, copies));
		for (int i = 0; i < copies; i++) out.add(new ItemStack(Items.WHITE_BED));
		out.add(new ItemStack(Items.TORCH, copies));
		out.add(new ItemStack(MarkerBlocks.ITEMS.get(BuildingType.HOUSE), copies));
		return out.toArray(new ItemStack[0]);
	}

	public static ItemStack[] shedMaterials(int copies) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(Items.COBBLESTONE, 49 * copies));
		out.add(new ItemStack(Items.OAK_PLANKS, 119 * copies));
		out.add(new ItemStack(Items.OAK_DOOR, copies));
		out.add(new ItemStack(Items.CHEST, 2 * copies));
		out.add(new ItemStack(Items.TORCH, copies));
		out.add(new ItemStack(MarkerBlocks.ITEMS.get(BuildingType.STORAGE), copies));
		return out.toArray(new ItemStack[0]);
	}

	public static ItemStack[] farmMaterials(int copies) {
		List<ItemStack> out = new ArrayList<>();
		out.add(new ItemStack(Items.DIRT, 80 * copies));
		for (int i = 0; i < copies; i++) out.add(new ItemStack(Items.WATER_BUCKET));
		out.add(new ItemStack(Items.OAK_FENCE, 39 * copies));
		out.add(new ItemStack(Items.OAK_FENCE_GATE, copies));
		return out.toArray(new ItemStack[0]);
	}

	// ---- builds ----------------------------------------------------------------------------------

	public static MinecraftServer server(GameTestHelper h) { return h.getLevel().getServer(); }

	public static List<BuildJob> builds(GameTestHelper h) { return BuildJobs.list(server(h)); }

	/** Runs a command as the server (permission: owner-level). */
	public static void command(GameTestHelper h, String cmd) {
		var source = server(h).createCommandSourceStack().withLevel(h.getLevel());
		server(h).getCommands().performPrefixedCommand(source, cmd);
	}

	/** {@code /villageheart build start <id> <abs pos> <rotation>} at a relative ground block. */
	public static void startBuild(GameTestHelper h, Blueprint bp, BlockPos groundRel, Rotation4 r) {
		BlockPos a = h.absolutePos(groundRel);
		command(h, "villageheart build start " + bp.id() + " " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + r.name().toLowerCase(java.util.Locale.ROOT));
	}

	public static Placement placement(GameTestHelper h, BlockPos groundRel, Rotation4 r) {
		BlockPos a = h.absolutePos(groundRel);
		return new Placement(new Pos(a.getX(), a.getY(), a.getZ()), r);
	}

	/** Test-relative block of a world {@link Pos}. See {@link VillageTestSupport#rel}. */
	public static BlockPos rel(GameTestHelper h, Pos abs) {
		return VillageTestSupport.rel(h, new BlockPos(abs.x(), abs.y(), abs.z()));
	}

	/** The absolute position of a test-relative one, as a core {@link Pos}. */
	public static Pos abs(GameTestHelper h, BlockPos rel) {
		BlockPos a = h.absolutePos(rel);
		return new Pos(a.getX(), a.getY(), a.getZ());
	}

	/** Relative position of a blueprint cell. */
	public static BlockPos cell(GameTestHelper h, Blueprint bp, Placement p, int lx, int ly, int lz) { return rel(h, p.world(bp, lx, ly, lz)); }

	public static UUID onlyBuild(GameTestHelper h) {
		List<BuildJob> b = builds(h);
		h.assertTrue(b.size() == 1, "exactly one build, got " + b.size());
		return b.get(0).id();
	}

	/** Position of the blueprint's marker, relative. */
	public static BlockPos markerCell(GameTestHelper h, Blueprint bp, Placement p) {
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			if (bp.isMarker(bp.entry(x, y, z))) return cell(h, bp, p, x, y, z);
		}
		throw new AssertionError("no marker in " + bp.id());
	}

	public static MarkerStatus markerStatus(GameTestHelper h, BlockPos rel) {
		var s = h.getBlockState(rel);
		return s.getBlock() instanceof MarkerBlock ? s.getValue(MarkerBlock.STATUS) : null;
	}

	public static long totalOf(GameTestHelper h, Item item) {
		return dev.villageheart.stock.Stockpiles.count(server(h), item);
	}

	public static boolean claimed(GameTestHelper h) { return VillageClaims.current(server(h)).isPresent(); }

	// ---- players ---------------------------------------------------------------------------------

	/** A recording player of the given rank standing at {@link #PLAYER_SPOT}, looking north (yaw 180) at the standard site. */
	public static BuildingTestSupport.RecordingPlayer playerWithRank(GameTestHelper h, String name, dev.villageheart.core.Rank rank) {
		// survival: a creative player keeps the stack that a use consumed (vanilla restores its count)
		var p = (BuildingTestSupport.RecordingPlayer) VillageTestSupport.player(h, name, net.minecraft.world.level.GameType.SURVIVAL,
				BuildingTestSupport.RecordingPlayer::new);
		VillageClaims.setRank(server(h), p.getUUID(), rank);
		var at = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(PLAYER_SPOT));
		p.setPos(at.x, at.y, at.z);
		MarkerTestSupport.look(p, Direction.NORTH);
		return p;
	}

	/** A hash of every block of the yard (and two layers below): "nothing changed" is a hash that stays equal. */
	public static int worldHash(GameTestHelper h) {
		int hash = 1;
		for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = -2; y < 12; y++) {
			hash = 31 * hash + Block.getId(h.getBlockState(new BlockPos(x, y, z)));
		}
		return hash;
	}

	/** A hash of every block of a blueprint's box at a placement (the builder opens doors elsewhere, so the whole yard is too much). */
	public static int boxHash(GameTestHelper h, Blueprint bp, Placement p) {
		int hash = 1;
		Pos a = p.min(bp), b = p.max(bp);
		for (int x = a.x(); x <= b.x(); x++) for (int y = a.y(); y <= b.y(); y++) for (int z = a.z(); z <= b.z(); z++) {
			hash = 31 * hash + Block.getId(h.getLevel().getBlockState(new BlockPos(x, y, z)));
		}
		return hash;
	}

	/** The last chat message's translation key, after asserting there is one (BACKLOG M5-r1: never get(0) of an empty list). */
	public static String lastKey(GameTestHelper h, BuildingTestSupport.RecordingPlayer p) {
		h.assertTrue(!p.messages.isEmpty(), "the player got a chat message");
		return BuildingTestSupport.translatable(p.messages.get(p.messages.size() - 1)).getKey();
	}

	public static String lastOverlayKey(GameTestHelper h, BuildingTestSupport.RecordingPlayer p) {
		h.assertTrue(!p.overlays.isEmpty(), "the player got an action bar message");
		return BuildingTestSupport.translatable(p.overlays.get(p.overlays.size() - 1)).getKey();
	}

	// ---- builders --------------------------------------------------------------------------------

	/** Counts of what builders did, from {@code BuilderJob.listener}: every clear and place, and whether the builder stood outside the box. */
	public static final class ActionLog implements dev.villageheart.worker.build.BuilderJob.ActionListener {
		public int places, clears, insideBox;
		public String firstInside = "";

		@Override
		public void acted(net.minecraft.world.entity.npc.villager.Villager builder, dev.villageheart.core.blueprint.BuildPlan.Kind kind, Pos cell,
				boolean outsideBox) {
			if (kind == dev.villageheart.core.blueprint.BuildPlan.Kind.CLEAR) clears++;
			else places++;
			if (!outsideBox) {
				insideBox++;
				if (firstInside.isEmpty()) firstInside = kind + " at " + cell + " from " + builder.position();
			}
		}
	}

	public static ActionLog watchActions() {
		ActionLog log = new ActionLog();
		dev.villageheart.worker.build.BuilderJob.listener = log;
		return log;
	}

	/** A villager spawned in the Hut and hired as the builder (the sequence must already hold a valid Hut). */
	public static net.minecraft.world.entity.npc.villager.Villager hireBuilder(GameTestHelper h) {
		var v = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(HUT, 1, 0, 1));
		var r = dev.villageheart.worker.Workers.hire(h.getLevel(), v, dev.villageheart.core.worker.JobType.BUILDER, java.util.Optional.empty());
		h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "the builder was hired: " + r);
		return v;
	}

	/**
	 * A Steward standing south of the site, looking north, takes a Blueprint and confirms the site on the standard ground with two clicks:
	 * the first shows it (the door turns towards the player), the second confirms. Asserts the pinned rotation.
	 */
	public static void confirmSite(GameTestHelper h, Blueprint bp, Rotation4 expected) {
		var steward = playerWithRank(h, "steward", dev.villageheart.core.Rank.STEWARD);
		ItemStack stack = dev.villageheart.build.BlueprintItem.of(bp);
		BuildingTestSupport.rightClick(h, steward, GROUND, Direction.UP, stack);
		var site = stack.get(dev.villageheart.build.ModComponents.BLUEPRINT_SITE);
		h.assertTrue(site != null && site.ok(), "the shown site is fine: " + (site == null ? "none" : site.reason().english()));
		h.assertTrue(site.rotationEnum() == expected, "the first click picks the rotation that turns the door to the player: " + site.rotationEnum());
		BuildingTestSupport.rightClick(h, steward, GROUND, Direction.UP, stack);
		h.assertTrue(builds(h).size() == 1 && builds(h).get(0).rotation() == expected, "the second click confirmed the site");
	}

	/** The cursor of the only build, or -1 when there is none. */
	public static int cursor(GameTestHelper h) {
		List<BuildJob> b = builds(h);
		return b.isEmpty() ? -1 : b.get(0).cursor();
	}

	public static void assertHouseGreen(GameTestHelper h, Blueprint bp, Placement p) {
		BlockPos marker = markerCell(h, bp, p);
		h.assertTrue(markerStatus(h, marker) == MarkerStatus.VALID, "the House marker is green: " + markerStatus(h, marker) + " "
				+ BuildingTestSupport.reasons(h, marker).stream().map(r -> r.message()).toList());
	}
}
