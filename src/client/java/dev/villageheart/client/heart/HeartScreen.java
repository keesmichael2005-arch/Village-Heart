package dev.villageheart.client.heart;

import dev.villageheart.client.art.ModTextures;
import dev.villageheart.core.Rank;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.Balance;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.BuildRow;
import dev.villageheart.core.heart.BuildingRow;
import dev.villageheart.core.heart.DetailKind;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.heart.JobChoice;
import dev.villageheart.core.heart.MemberRow;
import dev.villageheart.core.heart.Overview;
import dev.villageheart.core.heart.Permission;
import dev.villageheart.core.heart.Permissions;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.Stock;
import dev.villageheart.core.heart.StockLine;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.stock.StorageAlert;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.heart.net.AssignJobPayload;
import dev.villageheart.heart.net.FireWorkerPayload;
import dev.villageheart.heart.net.HeartActionResultPayload;
import dev.villageheart.heart.net.RecheckBuildingPayload;
import dev.villageheart.heart.net.SetRankPayload;
import dev.villageheart.heart.net.TreasuryActionPayload;
import dev.villageheart.heart.net.TransferOwnershipPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Heart screen (DESIGN section 10): five tabs (the Ranks tab only for the Owner), all drawn here (no vanilla list widgets), sized for the smallest supported
 * window, 427 x 240 GUI pixels (854 x 480 at GUI scale 2). The data comes from {@link HeartMenu#snapshot()}; every write is a payload
 * the server checks again. Visitors get the Overview only: the other tabs are not even in their snapshot.
 */
@Environment(EnvType.CLIENT)
public class HeartScreen extends Screen implements MenuAccess<HeartMenu> {
	/** Set by tests: record every drawn rectangle so {@link #layoutProblems()} can check them. */
	public static boolean recordLayout;

	static final int TAB_H = 24;
	static final int FOOTER_H = 33;
	static final int ROW_H = 22;
	static final int MEMBER_ROW_H = 16;
	static final int RANKS_HEADER_H = 14;
	static final int BUILDS_HEADER_H = 14;
	public static final int TAB_RANKS = 4;
	static final int STOCK_ROW_H = 18;
	static final int MARGIN = 8;
	static final int SCROLLBAR_W = 4;

	static final int WHITE = 0xFFFFFFFF;
	static final int GREY = 0xFFAAAAAA;
	static final int DARK_GREY = 0xFF808080;
	static final int GREEN = 0xFF55FF55;
	/** The XP bar of a worker whose next level waits for a bigger village (M15). */
	static final int LEVEL_CAPPED_GOLD = 0xFFE0B040;
	static final int RED = 0xFFFF5555;
	static final int YELLOW = 0xFFFFD45A;
	static final int GOLD = 0xFFFFAA00;

	private static final int AREA_HEADER = 0, AREA_BODY = 1, AREA_FOOTER = 2;
	private static final String[] TAB_KEYS = {"tab.overview", "tab.villagers", "tab.buildings", "tab.stockpile", "tab.ranks"};

	private final HeartMenu menu;
	private int tab;
	private final int[] scroll = new int[5];
	private UUID selectedVillager;
	private BlockPos selectedBuilding;
	private UUID selectedMember;
	private int pendingTransferTicks;
	private int jobIndex;
	private boolean pendingConfirm;
	private boolean actionsDirty = true;
	private int seenVersion = -1;
	private final List<AbstractWidget> actionWidgets = new ArrayList<>();
	private Button doneButton;
	private Component status;
	private int statusColor;
	private int statusTicks;
	private final Map<String, ItemStack> stacks = new HashMap<>();

	// test hooks
	private record Rect(int area, int group, int x, int y, int w, int h) {}

	private List<Rect> frame = new ArrayList<>();
	private List<Rect> lastFrame = List.of();
	private int clipped;
	private int lastClipped;
	/** The last text that was cut off (for test messages). */
	private String lastClippedText = "";

	public String lastClippedText() { return lastClippedText; }
	private int groupCounter;
	private int[] clip = {0, 0, 0, 0};
	private int scrollbarRight;
	private int lastScrollbarRight;
	private int rowAnchorX, rowAnchorY; // set while drawing a row (see villagerRowCentre)

	public HeartScreen(HeartMenu menu, Inventory inventory, Component title) {
		super(title);
		this.menu = menu;
	}

	@Override
	public HeartMenu getMenu() { return menu; }

	// ---- Screen plumbing ----------------------------------------------------------------------

	/** Like vanilla container screens: the world keeps running (else a singleplayer server would pause and never refresh). */
	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public boolean isInGameUi() { return true; }

	@Override
	public void onClose() {
		if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
		else super.onClose();
	}

	@Override
	protected void init() {
		doneButton = Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width - MARGIN - 100, height - FOOTER_H + 6, 100, 20).build();
		addRenderableWidget(doneButton);
		actionWidgets.clear();
		actionsDirty = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (statusTicks > 0) statusTicks--;
		if (pendingTransferTicks > 0 && --pendingTransferTicks == 0) {
			pendingConfirm = false;
			actionsDirty = true;
		}
		if (menu.version() != seenVersion) {
			seenVersion = menu.version();
			onSnapshot();
		}
		if (actionsDirty) rebuildActions();
		if (tab == 0) refreshTreasuryButtons();
		if (tab == 3) refreshReserveButtons();
	}

	private HeartSnapshot snap() { return menu.snapshot(); }

	private boolean allows(Permission p) { return Permissions.allows(snap().viewerRank(), p); }

	private boolean tabAllowed(int i) {
		if (i == 0) return true;
		return i == TAB_RANKS ? allows(Permission.MANAGE_RANKS) : allows(Permission.VIEW_DETAILS);
	}

	/** The one place a tab is chosen: every route (click, key, rank change) ends here, so a locked tab can never be shown. */
	public void selectTab(int i) {
		int next = i >= 0 && i < TAB_KEYS.length && tabAllowed(i) ? i : 0;
		if (next != tab) {
			tab = next;
			pendingConfirm = false;
			actionsDirty = true;
		}
	}

	private int tab() {
		if (!tabAllowed(tab)) selectTab(0);
		return tab;
	}

	private void onSnapshot() {
		selectTab(tab); // a rank change may lock the current tab
		HeartSnapshot s = snap();
		if (selectedVillager != null && s.villagers().stream().noneMatch(v -> v.id().equals(selectedVillager))) selectedVillager = null;
		if (selectedBuilding != null && s.buildings().stream().noneMatch(b -> pos(b).equals(selectedBuilding))) selectedBuilding = null;
		if (selectedMember != null && s.members().stream().noneMatch(m -> m.id().equals(selectedMember))) {
			selectedMember = null;
			pendingConfirm = false;
		}
		actionsDirty = true;
	}

	public void showResult(HeartActionResultPayload p) {
		Object[] args = p.args().toArray();
		status = Component.translatableWithFallback(p.key(), p.fallback(), args);
		statusColor = p.ok() ? GREEN : RED;
		statusTicks = 100;
	}

	private static BlockPos pos(BuildingRow b) { return new BlockPos(b.x(), b.y(), b.z()); }

	// ---- text helpers -------------------------------------------------------------------------

	private static Component t(String suffix, Object... args) { return Component.translatable(HeartTexts.key(suffix), args); }

	private void note(int area, int group, int x, int y, int w, int h) {
		if (!recordLayout) return;
		int x2 = Math.min(x + w, clip[2] > 0 ? clip[2] : Integer.MAX_VALUE);
		int y2 = Math.min(y + h, clip[3] > 0 ? clip[3] : Integer.MAX_VALUE);
		int x1 = Math.max(x, clip[0]), y1 = Math.max(y, clip[1]);
		if (x2 > x1 && y2 > y1) frame.add(new Rect(area, group, x1, y1, x2 - x1, y2 - y1));
	}

	/** Draws {@code c} cut with an ellipsis to {@code maxW}; returns the drawn width. */
	private int text(GuiGraphics g, Component c, int x, int y, int maxW, int color, int area, int group) {
		String s = c.getString();
		if (font.width(s) > maxW) {
			s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
			clipped++;
			lastClippedText = c.getString() + " in " + maxW;
		}
		g.drawString(font, s, x, y, color);
		int w = font.width(s);
		note(area, group, x, y, w, 9);
		return w;
	}

	private void textRight(GuiGraphics g, Component c, int right, int y, int maxW, int color, int area, int group) {
		String s = c.getString();
		if (font.width(s) > maxW) {
			s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
			clipped++;
			lastClippedText = c.getString() + " in " + maxW;
		}
		int w = font.width(s);
		g.drawString(font, s, right - w, y, color);
		note(area, group, right - w, y, w, 9);
	}

	private static Component text(TextLine l) {
		return Component.translatableWithFallback(l.key(), l.fallback(), l.args().toArray());
	}

	private static int severityColor(Severity s) { return s == Severity.DANGER ? RED : YELLOW; }

	private ItemStack stack(String id) {
		return stacks.computeIfAbsent(id, k -> {
			try {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(k)));
			} catch (RuntimeException e) {
				return ItemStack.EMPTY;
			}
		});
	}

	// ---- layout -------------------------------------------------------------------------------

	private int bodyTop() { return TAB_H; }

	private int bodyBottom() { return height - FOOTER_H; }

	private int tabW() { return Math.min(110, (width - 2 * MARGIN) / TAB_KEYS.length); }

	private int tabX0() { return (width - tabW() * TAB_KEYS.length) / 2; }

	/** GUI coordinates of the middle of tab {@code i}'s button (for tests). */
	public int[] tabButtonCentre(int i) { return new int[] {tabX0() + i * tabW() + tabW() / 2, TAB_H / 2}; }

	public int currentTab() { return tab; }

	public int clippedThisFrame() { return lastClipped; }

	private boolean actionBarShown() {
		if (tab == 1) return villagerActionBar();
		if (tab == 2) return allows(Permission.RECHECK_BUILDING) && selectedBuilding != null;
		if (tab == TAB_RANKS) return selectedMemberRow() != null && selectedMemberRow().rank() != Rank.OWNER;
		if (tab == 3) return true; // the reserve bar (M22)
		return false;
	}

	private boolean villagerActionBar() {
		VillagerRow r = selectedRow();
		return r != null && (r.isWorker() ? allows(Permission.FIRE) : allows(Permission.HIRE));
	}

	/** The scrolling list's rectangle {x, y, w, h} on the current tab. */
	private int[] listRect() {
		int top = bodyTop() + 4;
		int bottom = bodyBottom() - 4 - (actionBarShown() ? 24 : 0);
		if (tab == 3) top = bodyTop() + 4 + 40;
		if (tab == TAB_RANKS) top = bodyTop() + 4 + RANKS_HEADER_H;
		return new int[] {MARGIN, top, width - 2 * MARGIN, bottom - top};
	}

	private int maxScroll(int contentH) { return Math.max(0, contentH - listRect()[3]); }

	private int contentHeight() {
		HeartSnapshot s = snap();
		return switch (tab) {
			case 1 -> (s.villagers().size() + (s.moreVillagers() > 0 ? 1 : 0)) * ROW_H;
			case 2 -> (s.buildings().size() + (s.moreBuildings() > 0 ? 1 : 0)) * ROW_H + (s.builds().isEmpty() ? 0 : BUILDS_HEADER_H + s.builds().size() * ROW_H);
			case 3 -> ((s.stock().items().size() + 1) / 2 + (s.stock().moreKinds() > 0 ? 1 : 0)) * STOCK_ROW_H;
			case TAB_RANKS -> (s.members().size() + (s.moreMembers() > 0 ? 1 : 0)) * MEMBER_ROW_H;
			default -> 0;
		};
	}

	/** Maximum scroll offset (pixels) of the current tab's list. */
	public int maxScrollAmount() { return maxScroll(contentHeight()); }

	public int scrollAmount() { return scroll[tab]; }

	private void clampScroll() { scroll[tab] = Math.max(0, Math.min(scroll[tab], maxScroll(contentHeight()))); }

	private VillagerRow selectedRow() {
		if (selectedVillager == null) return null;
		for (VillagerRow v : snap().villagers()) if (v.id().equals(selectedVillager)) return v;
		return null;
	}

	private MemberRow selectedMemberRow() {
		if (selectedMember == null) return null;
		for (MemberRow m : snap().members()) if (m.id().equals(selectedMember)) return m;
		return null;
	}

	/** GUI coordinates of the middle of the row of player {@code id} in the Ranks tab, scrolling it into view; null when not listed. */
	public int[] memberRowCentre(UUID id) {
		List<MemberRow> rows = snap().members();
		for (int i = 0; i < rows.size(); i++) {
			if (!rows.get(i).id().equals(id)) continue;
			int[] r = listRect();
			int y = i * MEMBER_ROW_H;
			if (y < scroll[TAB_RANKS]) scroll[TAB_RANKS] = y;
			else if (y + MEMBER_ROW_H > scroll[TAB_RANKS] + r[3]) scroll[TAB_RANKS] = y + MEMBER_ROW_H - r[3];
			scroll[TAB_RANKS] = Math.max(0, Math.min(scroll[TAB_RANKS], maxScroll(rows.size() * MEMBER_ROW_H)));
			return new int[] {r[0] + 60, r[1] + y - scroll[TAB_RANKS] + MEMBER_ROW_H / 2};
		}
		return null;
	}

	/** GUI coordinates of the middle of the last button of the action bar (the Transfer button in the Ranks tab); null when none. */
	public int[] lastActionCentre() {
		if (actionWidgets.isEmpty()) return null;
		AbstractWidget w = actionWidgets.get(actionWidgets.size() - 1);
		return new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
	}

	/** GUI coordinates of the middle of the first button of the action bar (the guard's Shift button in the Villagers tab); null when none. */
	public int[] firstActionCentre() {
		if (actionWidgets.isEmpty()) return null;
		AbstractWidget w = actionWidgets.get(0);
		return new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
	}

	/** The labels of the buttons of the action bar, left to right (for tests: the Overview's treasury bar). */
	public List<String> actionLabels() {
		List<String> out = new ArrayList<>();
		for (AbstractWidget w : actionWidgets) out.add(w.getMessage().getString());
		return out;
	}

	/** True while the Transfer button asks for its second click. */
	public boolean transferPending() { return pendingConfirm && tab == TAB_RANKS; }

	/** GUI coordinates of the middle of the row of villager {@code id}, scrolling it into view first; null when not listed. */
	public int[] villagerRowCentre(UUID id) {
		List<VillagerRow> rows = snap().villagers();
		for (int i = 0; i < rows.size(); i++) {
			if (!rows.get(i).id().equals(id)) continue;
			int[] r = listRect();
			int y = i * ROW_H;
			if (y < scroll[1]) scroll[1] = y;
			else if (y + ROW_H > scroll[1] + r[3]) scroll[1] = y + ROW_H - r[3];
			scroll[1] = Math.max(0, Math.min(scroll[1], maxScroll(rows.size() * ROW_H)));
			return new int[] {r[0] + 60, r[1] + y - scroll[1] + ROW_H / 2};
		}
		return null;
	}

	/** Index of the first villager row that is at least partly visible (for scroll tests). */
	public int firstVisibleVillagerRow() { return scroll[1] / ROW_H; }

	public List<String> layoutProblems() {
		List<String> out = new ArrayList<>();
		Map<Integer, List<Rect>> groups = new HashMap<>();
		for (Rect r : lastFrame) {
			int top, bottom;
			switch (r.area()) {
				case AREA_HEADER -> { top = 0; bottom = TAB_H; }
				case AREA_FOOTER -> { top = height - FOOTER_H; bottom = height; }
				default -> { top = TAB_H; bottom = height - FOOTER_H; }
			}
			if (r.x() < 0 || r.x() + r.w() > width || r.y() < top || r.y() + r.h() > bottom) {
				out.add("outside its area: " + r);
			}
			if (r.group() >= 0) groups.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r);
		}
		for (List<Rect> g : groups.values()) {
			for (int i = 0; i < g.size(); i++) for (int j = i + 1; j < g.size(); j++) {
				Rect a = g.get(i), b = g.get(j);
				if (a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h()) {
					out.add("overlap: " + a + " and " + b);
				}
			}
		}
		if (lastScrollbarRight > width - 2) out.add("scrollbar at " + lastScrollbarRight + " is outside the window");
		return out;
	}

	// ---- actions bar --------------------------------------------------------------------------

	private void rebuildActions() {
		actionsDirty = false;
		for (AbstractWidget w : actionWidgets) removeWidget(w);
		actionWidgets.clear();
		int y = bodyBottom() - 24;
		if (tab == 0) {
			treasuryBar(y);
		} else if (tab == 1) {
			VillagerRow r = selectedRow();
			if (r == null) return;
			if (r.isWorker()) {
				if (r.shift().isGuard() && allows(Permission.SET_GUARD_SHIFT)) {
					UUID sid = r.id();
					int next = r.shift().nextChoice();
					addAction(Button.builder(Component.translatable(HeartTexts.key("shift.button"), Component.translatable(HeartTexts.key("shift.choice." + (r.shift().pinned() ? (r.shift() == dev.villageheart.core.heart.GuardShiftView.DAY_PINNED ? 1 : 2) : 0)))),
							b -> ClientPlayNetworking.send(new dev.villageheart.heart.net.SetGuardShiftPayload(menu.containerId, sid, next))).bounds(MARGIN + 76, y, 110, 20).build());
				}
				if (allows(Permission.FIRE)) {
					UUID id = r.id();
					addAction(Button.builder(Component.translatable(HeartTexts.key("fire")),
							b -> ClientPlayNetworking.send(new FireWorkerPayload(menu.containerId, id))).bounds(MARGIN, y, 70, 20).build());
				}
			} else if (allows(Permission.HIRE) && !r.nitwit()) {
				List<JobChoice> choices = snap().jobChoices();
				if (choices.isEmpty()) return;
				jobIndex = Math.floorMod(jobIndex, choices.size());
				JobChoice c = choices.get(jobIndex);
				Component jobName = jobName(c.jobId());
				addAction(Button.builder(t("villagers.job", jobName, c.freeSlots()), b -> {
					jobIndex++;
					pendingConfirm = false;
					actionsDirty = true;
				}).bounds(MARGIN, y, 170, 20).build());
				boolean usable = c.freeSlots() > 0 && !c.tierLocked();
				Component label = pendingConfirm ? t("confirm_trades") : Component.translatable(HeartTexts.key("assign"));
				Button assign = Button.builder(label, b -> {
					if (r.hasTraded() && !pendingConfirm) {
						pendingConfirm = true;
						actionsDirty = true;
						return;
					}
					pendingConfirm = false;
					ClientPlayNetworking.send(new AssignJobPayload(menu.containerId, r.id(), c.jobId(), Optional.empty()));
					actionsDirty = true;
				}).bounds(MARGIN + 176, y, pendingConfirm ? 150 : 70, 20).build();
				assign.active = usable;
				if (!usable) assign.setTooltip(Tooltip.create(t(c.tierLocked() ? "assign.tier_locked" : "assign.no_slot")));
				addAction(assign);
			}
		} else if (tab == TAB_RANKS && allows(Permission.MANAGE_RANKS)) {
			MemberRow m = selectedMemberRow();
			if (m == null || m.rank() == Rank.OWNER) return;
			UUID id = m.id();
			Rank current = m.rank();
			int x = MARGIN;
			x = rankButton(x, y, "ranks.make_steward", Rank.STEWARD, current, id);
			x = rankButton(x, y, "ranks.make_resident", Rank.RESIDENT, current, id);
			x = rankButton(x, y, "ranks.make_visitor", Rank.VISITOR, current, id);
			boolean member = current == Rank.STEWARD || current == Rank.RESIDENT;
			Component label = pendingConfirm ? t("ranks.transfer_confirm", m.name()) : t("ranks.transfer");
			Button transfer = Button.builder(label, b -> {
				if (!pendingConfirm) {
					pendingConfirm = true;
					pendingTransferTicks = 100;
					actionsDirty = true;
					return;
				}
				pendingConfirm = false;
				pendingTransferTicks = 0;
				ClientPlayNetworking.send(new TransferOwnershipPayload(menu.containerId, id, current.ordinal()));
				actionsDirty = true;
			}).bounds(x + 4, y, pendingConfirm ? width - MARGIN - (x + 4) : 110, 20).build();
			transfer.active = member;
			if (!member) transfer.setTooltip(Tooltip.create(t("ranks.transfer_needs_member")));
			addAction(transfer);
		} else if (tab == 3) {
			reserveBar(y);
		} else if (tab == 2 && allows(Permission.RECHECK_BUILDING) && selectedBuilding != null) {
			BlockPos p = selectedBuilding;
			addAction(Button.builder(t("check_now"), b -> ClientPlayNetworking.send(new RecheckBuildingPayload(menu.containerId, p)))
					.bounds(MARGIN, y, 100, 20).build());
		}
	}

	// ---- the reserve bar of the Stockpile tab (M22) ----------------------------------------------------

	private Button foodLess, foodMore, itemsLess, itemsMore;
	/** x of the four labels and values of the bar, set by {@link #reserveBar}. */
	private int barFoodLabelX, barFoodValueX, barItemsLabelX, barItemsValueX;

	private static final int BAR_BTN_W = 20, BAR_ITEM_BTN_W = 28, BAR_VALUE_W = 46;

	private void sendReserves(int foodDays, int itemReserve) {
		ClientPlayNetworking.send(new dev.villageheart.heart.net.SetReservesPayload(menu.containerId, foodDays, itemReserve));
	}

	/** "Keep: food [-] 3 days [+]   other items [-] 64 each [+]": buttons for those who may set the reserves, the values for everyone. */
	private void reserveBar(int y) {
		foodLess = foodMore = itemsLess = itemsMore = null;
		int x = MARGIN;
		barFoodLabelX = x;
		x += font.width(t("reserve.label_food").getString()) + 6;
		int bx = x;
		boolean edit = allows(Permission.SET_RESERVES);
		if (edit) {
			foodLess = Button.builder(t("reserve.food_less"), b -> sendReserves(snap().stock().reserves().foodDays() - Balance.FOOD_RESERVE_STEP, snap().stock().reserves().itemReserve()))
					.bounds(bx, y, BAR_BTN_W, 20).build();
			addAction(foodLess);
		}
		barFoodValueX = bx + (edit ? BAR_BTN_W + 4 : 0);
		if (edit) {
			foodMore = Button.builder(t("reserve.food_more"), b -> sendReserves(snap().stock().reserves().foodDays() + Balance.FOOD_RESERVE_STEP, snap().stock().reserves().itemReserve()))
					.bounds(barFoodValueX + BAR_VALUE_W + 4, y, BAR_BTN_W, 20).build();
			addAction(foodMore);
		}
		x = barFoodValueX + BAR_VALUE_W + (edit ? BAR_BTN_W + 8 : 0) + 10;
		barItemsLabelX = x;
		x += font.width(t("reserve.label_items").getString()) + 6;
		if (edit) {
			itemsLess = Button.builder(t("reserve.items_less"), b -> sendReserves(snap().stock().reserves().foodDays(), snap().stock().reserves().itemReserve() - Balance.ITEM_RESERVE_STEP))
					.bounds(x, y, BAR_ITEM_BTN_W, 20).build();
			addAction(itemsLess);
		}
		barItemsValueX = x + (edit ? BAR_ITEM_BTN_W + 4 : 0);
		if (edit) {
			itemsMore = Button.builder(t("reserve.items_more"), b -> sendReserves(snap().stock().reserves().foodDays(), snap().stock().reserves().itemReserve() + Balance.ITEM_RESERVE_STEP))
					.bounds(barItemsValueX + BAR_VALUE_W + 4, y, BAR_ITEM_BTN_W, 20).build();
			addAction(itemsMore);
		}
		refreshReserveButtons();
	}

	/** The buttons stop at the ends of the range (checked against the latest snapshot, every tick). */
	private void refreshReserveButtons() {
		var r = snap().stock().reserves();
		if (foodLess != null) foodLess.active = r.foodDays() - Balance.FOOD_RESERVE_STEP >= 0;
		if (foodMore != null) foodMore.active = r.foodDays() + Balance.FOOD_RESERVE_STEP <= Balance.MAX_FOOD_RESERVE_DAYS;
		if (itemsLess != null) itemsLess.active = r.itemReserve() - Balance.ITEM_RESERVE_STEP >= 0;
		if (itemsMore != null) itemsMore.active = r.itemReserve() + Balance.ITEM_RESERVE_STEP <= Balance.MAX_ITEM_RESERVE;
	}

	private Button depositButton, withdrawOneButton, withdrawStackButton;

	/** The treasury bar of the Overview (M12): Deposit all for Residents and above, Withdraw 1 and 64 for the Owner. */
	private void treasuryBar(int y) {
		depositButton = withdrawOneButton = withdrawStackButton = null;
		int x = MARGIN;
		if (allows(Permission.DEPOSIT_TREASURY)) {
			depositButton = Button.builder(t("deposit_all"), b -> ClientPlayNetworking.send(new TreasuryActionPayload(menu.containerId, false, 0)))
					.bounds(x, y, 100, 20).tooltip(Tooltip.create(t("deposit_all.tip"))).build();
			addAction(depositButton);
			x += 106;
		}
		if (allows(Permission.WITHDRAW_TREASURY)) {
			withdrawOneButton = Button.builder(t("withdraw_one"), b -> ClientPlayNetworking.send(new TreasuryActionPayload(menu.containerId, true, 1)))
					.bounds(x, y, 80, 20).build();
			addAction(withdrawOneButton);
			withdrawStackButton = Button.builder(t("withdraw_stack"), b -> ClientPlayNetworking.send(new TreasuryActionPayload(menu.containerId, true, Balance.MAX_WITHDRAW)))
					.bounds(x + 84, y, 86, 20).build();
			addAction(withdrawStackButton);
		}
		refreshTreasuryButtons();
	}

	/** Deposit needs an emerald in the client's inventory; Withdraw needs something in the treasury. Run every tick (the inventory changes). */
	private void refreshTreasuryButtons() {
		if (depositButton != null) {
			depositButton.active = minecraft != null && minecraft.player != null && minecraft.player.getInventory().countItem(Items.EMERALD) > 0;
		}
		boolean stocked = snap().overview().treasury() > 0;
		if (withdrawOneButton != null) withdrawOneButton.active = stocked;
		if (withdrawStackButton != null) withdrawStackButton.active = stocked;
	}

	/** One of the three "make them ..." buttons; returns the x where the next one starts. */
	private int rankButton(int x, int y, String key, Rank target, Rank current, UUID id) {
		Button b = Button.builder(t(key), btn -> {
			pendingConfirm = false;
			ClientPlayNetworking.send(new SetRankPayload(menu.containerId, id, target.ordinal()));
			actionsDirty = true;
		}).bounds(x, y, 66, 20).build();
		b.active = current != target;
		addAction(b);
		return x + 70;
	}

	private void addAction(AbstractWidget w) {
		actionWidgets.add(w);
		addRenderableWidget(w);
	}

	private static Component jobName(String jobId) { return Component.translatable("entity.villageheart.villager." + jobId); }

	// ---- input --------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
		if (super.mouseClicked(ev, doubleClick)) return true;
		if (ev.button() != 0) return false;
		double mx = ev.x(), my = ev.y();
		if (my >= 0 && my < TAB_H) {
			for (int i = 0; i < TAB_KEYS.length; i++) {
				int x0 = tabX0() + i * tabW();
				if (mx >= x0 && mx < x0 + tabW()) {
					selectTab(i);
					return true;
				}
			}
			return false;
		}
		int[] r = listRect();
		if (tab() == TAB_RANKS) {
			if (mx >= r[0] && mx < r[0] + r[2] - SCROLLBAR_W - 4 && my >= r[1] && my < r[1] + r[3]) {
				int idx = (int) ((my - r[1] + scroll[tab]) / MEMBER_ROW_H);
				if (idx < snap().members().size()) {
					UUID id = snap().members().get(idx).id();
					if (!id.equals(selectedMember)) {
						pendingConfirm = false;
						pendingTransferTicks = 0;
					}
					selectedMember = id;
					actionsDirty = true;
					return true;
				}
			}
			return false;
		}
		if (tab() == 1 || tab() == 2) {
			if (mx >= r[0] && mx < r[0] + r[2] - SCROLLBAR_W - 4 && my >= r[1] && my < r[1] + r[3]) {
				int idx = (int) ((my - r[1] + scroll[tab]) / ROW_H);
				if (tab == 1 && idx < snap().villagers().size()) {
					UUID id = snap().villagers().get(idx).id();
					if (!id.equals(selectedVillager)) pendingConfirm = false;
					selectedVillager = id;
					actionsDirty = true;
					return true;
				}
				if (tab == 2 && idx < snap().buildings().size()) {
					selectedBuilding = pos(snap().buildings().get(idx));
					actionsDirty = true;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (tab() == 0) return super.mouseScrolled(mx, my, sx, sy);
		scroll[tab] -= (int) Math.round(sy * (tab == 3 ? STOCK_ROW_H : tab == TAB_RANKS ? MEMBER_ROW_H : ROW_H));
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent ev) {
		if (minecraft != null && minecraft.options.keyInventory.matches(ev)) {
			onClose();
			return true;
		}
		if (ev.hasControlDown() && ev.input() >= '1' && ev.input() <= '5') {
			selectTab(ev.input() - '1');
			return true;
		}
		return super.keyPressed(ev);
	}

	// ---- rendering ----------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		super.renderBackground(g, mx, my, pt);
		lastFrame = frame;
		frame = new ArrayList<>();
		lastClipped = clipped;
		clipped = 0;
		groupCounter = 0;
		lastScrollbarRight = scrollbarRight;
		scrollbarRight = 0;
		tab();
		clampScroll();

		g.fill(0, 0, width, TAB_H, 0xFF202020);
		g.fill(0, TAB_H, width, bodyBottom(), 0x99000000);
		g.fill(0, bodyBottom(), width, height, 0xFF202020);
		g.fill(0, bodyBottom(), width, bodyBottom() + 1, 0xFF000000);
		drawTabs(g, mx, my);
		clip = new int[] {0, bodyTop(), width, bodyBottom()};
		switch (tab) {
			case 0 -> drawOverview(g, mx, my);
			case 1 -> drawVillagers(g, mx, my);
			case 2 -> drawBuildings(g, mx, my);
			case TAB_RANKS -> drawRanks(g, mx, my);
			default -> drawStockpile(g, mx, my);
		}
		clip = new int[] {0, 0, 0, 0};
		if (statusTicks > 0 && status != null) {
			text(g, status, MARGIN, height - FOOTER_H + 12, width - 3 * MARGIN - 100, statusColor, AREA_FOOTER, -1);
		}
	}

	private void drawTabs(GuiGraphics g, int mx, int my) {
		int tabIcons = groupCounter++;
		for (int i = 0; i < TAB_KEYS.length; i++) {
			int x0 = tabX0() + i * tabW();
			boolean allowed = tabAllowed(i);
			boolean selected = i == tab;
			int bg = selected ? 0xFF4A4A4A : (allowed ? 0xFF303030 : 0xFF1A1A1A);
			g.fill(x0, 2, x0 + tabW() - 1, TAB_H, bg);
			if (selected) g.fill(x0, TAB_H - 2, x0 + tabW() - 1, TAB_H, 0xFFFFFFFF);
			ModTextures.blit(g, ModTextures.TABS[i], x0 + 3, 8, 8);
			note(AREA_HEADER, tabIcons, x0 + 3, 8, 8, 8);
			Component label = Component.translatable(HeartTexts.key(TAB_KEYS[i]));
			int color = !allowed ? DARK_GREY : selected ? WHITE : GREY;
			int w = Math.min(font.width(label.getString()), tabW() - 6);
			text(g, label, x0 + (tabW() - 1 - w) / 2, 10, tabW() - 6, color, AREA_HEADER, -1);
			if (!allowed && mx >= x0 && mx < x0 + tabW() && my >= 0 && my < TAB_H) {
				g.setTooltipForNextFrame(font, Component.translatable(HeartTexts.key(i == TAB_RANKS ? "tab.ranks_locked" : "tab.locked")), mx, my);
			}
		}
	}

	private void drawOverview(GuiGraphics g, int mx, int my) {
		Overview o = snap().overview();
		int colW = (width - 3 * MARGIN) / 2;
		int lx = MARGIN, rx = MARGIN * 2 + colW;
		int y = bodyTop() + 8;
		int grp = groupCounter++;
		text(g, t("overview.title", o.ownerName()), lx, y, colW, GOLD, AREA_BODY, grp);
		ModTextures.blit(g, ModTextures.tier(o.tier()), lx, y + 12, 8);
		note(AREA_BODY, grp, lx, y + 12, 8, 8);
		text(g, t("overview.tier", o.tier().displayName()), lx + 10, y + 12, colW - 10, WHITE, AREA_BODY, grp);
		boolean ready = !o.goals().isEmpty() && o.goals().stream().allMatch(dev.villageheart.core.heart.GoalLine::met);
		text(g, o.nextTier().isPresent() ? t(ready ? "overview.next_tier_ready" : "overview.next_tier", o.nextTier().get().displayName()) : t("overview.top_tier"),
				lx, y + 24, colW, ready ? GREEN : GREY, AREA_BODY, grp);
		int gy = y + 36;
		int rows = 0;
		int grpBox = groupCounter++;
		for (var goal : o.goals()) {
			if (rows++ >= dev.villageheart.core.heart.HeartLimits.MAX_GOAL_ROWS) break;
			// an 8 x 8 check (met) or an open box, then the label. The text stays at lx + 10, so the icon does not take any of its width.
			int bx = lx, by = gy;
			ModTextures.blit(g, goal.met() ? ModTextures.GOAL_MET : ModTextures.GOAL_OPEN, bx, by, 8);
			note(AREA_BODY, grpBox, bx, by, 8, 8);
			text(g, text(goal.label()), lx + 10, gy, colW - 50, goal.met() ? GREEN : WHITE, AREA_BODY, grp);
			gy += 10;
		}

		int grp2 = groupCounter++;
		Component pop = t("overview.population", o.population(), o.beds());
		text(g, pop, rx, y, colW, WHITE, AREA_BODY, grp2);
		if (mx >= rx && mx < rx + colW && my >= y && my < y + 9) {
			g.setTooltipForNextFrame(font, t("overview.population_tip"), mx, my);
		}
		text(g, t("overview.workers", o.workers(), o.workerSlots()), rx, y + 12, colW, WHITE, AREA_BODY, grp2);
		text(g, o.foodDaysTenths() < 0 ? t("overview.food_none") : t("overview.food", days(o.foodDaysTenths())), rx, y + 24, colW,
				WHITE, AREA_BODY, grp2);
		text(g, t("overview.treasury", o.treasury()), rx, y + 36, colW, WHITE, AREA_BODY, grp2);
		if (o.wageBill() < 0) text(g, t("overview.wages_none"), rx, y + 48, colW, GREY, AREA_BODY, grp2);
		else text(g, t("overview.wages", o.wageBill()), rx, y + 48, colW, o.treasury() < o.wageBill() ? RED : GREY, AREA_BODY, grp2);

		int ay = Math.max(y + 66, gy + 4);
		int grp3 = groupCounter++;
		// the treasury bar (M12) takes the bottom 24 px of the body: the alert list gives way to it
		boolean bar = !actionWidgets.isEmpty();
		int fit = Math.max(1, (bodyBottom() - (bar ? 24 : 0) - 2 - ay) / 12);
		if (o.alerts().isEmpty()) {
			text(g, t("overview.no_alerts"), lx, ay, width - 2 * MARGIN, GREY, AREA_BODY, grp3);
		} else {
			int lines = o.alerts().size() + (o.moreAlerts() > 0 ? 1 : 0);
			int shown = lines <= fit ? o.alerts().size() : Math.max(0, fit - 1);
			for (int i = 0; i < shown; i++) {
				AlertLine a = o.alerts().get(i);
				ModTextures.blit(g, a.severity() == dev.villageheart.core.heart.Severity.DANGER ? ModTextures.ALERT_DANGER : ModTextures.ALERT_WARNING, lx, ay, 8);
				note(AREA_BODY, grp3, lx, ay, 8, 8);
				text(g, text(a.text()), lx + 10, ay, width - 2 * MARGIN - 10, severityColor(a.severity()), AREA_BODY, grp3);
				ay += 12;
			}
			int hidden = o.moreAlerts() + (o.alerts().size() - shown);
			if (hidden > 0) text(g, t("overview.more_alerts", hidden), lx, ay, width - 2 * MARGIN, GREY, AREA_BODY, grp3);
		}
		for (AbstractWidget w : actionWidgets) note(AREA_BODY, grp3, w.getX(), w.getY(), w.getWidth(), w.getHeight());
	}

	static String days(int tenths) { return (tenths / 10) + "." + (tenths % 10); }

	private boolean lockedBody(GuiGraphics g) {
		if (tabAllowed(tab)) return false;
		text(g, t("locked.body"), MARGIN, bodyTop() + 8, width - 2 * MARGIN, GREY, AREA_BODY, -1);
		return true;
	}

	private void scrollbar(GuiGraphics g, int[] r, int contentH) {
		if (contentH <= r[3]) return;
		int x = r[0] + r[2] - SCROLLBAR_W - 2;
		g.fill(x, r[1], x + SCROLLBAR_W, r[1] + r[3], 0xFF101010);
		int thumbH = Math.max(12, r[3] * r[3] / contentH);
		int range = r[3] - thumbH;
		int ms = Math.max(1, contentH - r[3]);
		int ty = r[1] + range * scroll[tab] / ms;
		g.fill(x, ty, x + SCROLLBAR_W, ty + thumbH, 0xFFA0A0A0);
		scrollbarRight = Math.max(scrollbarRight, x + SCROLLBAR_W);
	}

	private void drawRanks(GuiGraphics g, int mx, int my) {
		if (lockedBody(g)) return;
		HeartSnapshot s = snap();
		int total = s.members().size() + s.moreMembers();
		text(g, t("ranks.header", total), MARGIN, bodyTop() + 6, width - 2 * MARGIN, GREY, AREA_BODY, groupCounter++);
		int[] r = listRect();
		clip = new int[] {r[0], r[1], r[0] + r[2], r[1] + r[3]};
		g.enableScissor(r[0], r[1], r[0] + r[2], r[1] + r[3]);
		int rowW = r[2] - SCROLLBAR_W - 4;
		int y = r[1] - scroll[TAB_RANKS];
		for (MemberRow m : s.members()) {
			if (y + MEMBER_ROW_H > r[1] && y < r[1] + r[3]) drawMemberRow(g, m, r[0], y, rowW, mx, my);
			y += MEMBER_ROW_H;
		}
		if (s.moreMembers() > 0 && y + MEMBER_ROW_H > r[1] && y < r[1] + r[3]) {
			text(g, t("ranks.more", s.moreMembers()), r[0] + 4, y + 4, rowW - 8, GREY, AREA_BODY, groupCounter++);
		}
		g.disableScissor();
		clip = new int[] {0, bodyTop(), width, bodyBottom()};
		scrollbar(g, r, s.members().size() * MEMBER_ROW_H);
	}

	private void drawMemberRow(GuiGraphics g, MemberRow m, int x, int y, int w, int mx, int my) {
		int grp = groupCounter++;
		boolean selected = m.id().equals(selectedMember);
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + MEMBER_ROW_H;
		if (selected) g.fill(x, y, x + w, y + MEMBER_ROW_H - 1, 0x66FFFFFF);
		else if (hover) g.fill(x, y, x + w, y + MEMBER_ROW_H - 1, 0x33FFFFFF);
		ModTextures.blit(g, ModTextures.rank(m.rank()), x + 3, y + 4, 8);
		g.fill(x + 8, y + 9, x + 11, y + 12, m.online() ? GREEN : DARK_GREY);
		note(AREA_BODY, grp, x + 3, y + 4, 8, 8);
		int color = m.rank() == Rank.OWNER ? GOLD : m.online() ? WHITE : GREY;
		text(g, Component.literal(m.name()), x + 14, y + 4, 180, color, AREA_BODY, grp);
		Component rank = Component.translatable(HeartTexts.key("rank." + m.rank().name().toLowerCase(Locale.ROOT)));
		int rankColor = m.rank() == Rank.OWNER ? GOLD : m.rank() == Rank.VISITOR ? DARK_GREY : WHITE;
		text(g, rank, x + 210, y + 4, 90, rankColor, AREA_BODY, grp);
		Component state = t(m.online() ? "ranks.online" : "ranks.offline");
		textRight(g, state, x + w - 4, y + 4, 70, GREY, AREA_BODY, grp);
	}

	private void drawVillagers(GuiGraphics g, int mx, int my) {
		if (lockedBody(g)) return;
		HeartSnapshot s = snap();
		int[] r = listRect();
		clip = new int[] {r[0], r[1], r[0] + r[2], r[1] + r[3]};
		g.enableScissor(r[0], r[1], r[0] + r[2], r[1] + r[3]);
		if (s.villagers().isEmpty()) {
			text(g, t("villagers.none"), r[0] + 4, r[1] + 6, r[2] - 8, GREY, AREA_BODY, -1);
		}
		int rowW = r[2] - SCROLLBAR_W - 4;
		int y = r[1] - scroll[1];
		for (VillagerRow v : s.villagers()) {
			if (y + ROW_H > r[1] && y < r[1] + r[3]) drawVillagerRow(g, v, r[0], y, rowW, mx, my);
			y += ROW_H;
		}
		if (s.moreVillagers() > 0 && y + ROW_H > r[1] && y < r[1] + r[3]) {
			text(g, t("villagers.more", s.moreVillagers()), r[0] + 4, y + 7, rowW - 8, GREY, AREA_BODY, groupCounter++);
		}
		g.disableScissor();
		clip = new int[] {0, bodyTop(), width, bodyBottom()};
		scrollbar(g, r, s.villagers().size() * ROW_H);
		if (selectedRow() != null && selectedRow().nitwit() && allows(Permission.HIRE)) {
			text(g, t("villagers.nitwit_note"), MARGIN, bodyBottom() - 20, width - 2 * MARGIN, GREY, AREA_BODY, groupCounter++);
		}
	}

	private void drawVillagerRow(GuiGraphics g, VillagerRow v, int x, int y, int w, int mx, int my) {
		int grp = groupCounter++;
		boolean selected = v.id().equals(selectedVillager);
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
		if (selected) g.fill(x, y, x + w, y + ROW_H - 1, 0x66FFFFFF);
		else if (hover) g.fill(x, y, x + w, y + ROW_H - 1, 0x33FFFFFF);
		int ty = y + 7;
		int nameX = x + 4;
		int nameW = 56;
		if (v.isWorker() && !v.jobId().isEmpty()) {
			ModTextures.blit(g, ModTextures.job(v.jobId()), x + 2, y + 7, 8);
			note(AREA_BODY, grp, x + 2, y + 7, 8, 8);
			nameX = x + 12;
			nameW = 48;
		}
		text(g, Component.literal(v.name()), nameX, ty, nameW, v.nitwit() ? DARK_GREY : WHITE, AREA_BODY, grp);
		Component job;
		if (v.isWorker()) job = t("villagers.level", jobName(v.jobId()), v.level());
		else if (v.nitwit()) job = t("villagers.nitwit");
		else if (v.vanillaProfession().isEmpty() || v.vanillaProfession().equals("minecraft:none")) job = t("villagers.unemployed");
		else job = Component.translatable("entity.minecraft.villager." + Identifier.parse(v.vanillaProfession()).getPath());
		text(g, job, x + 62, ty, 88, v.isWorker() ? WHITE : GREY, AREA_BODY, grp);
		if (v.isWorker()) {
			int bx = x + 152, by = y + 8;
			g.fill(bx, by, bx + 40, by + 5, 0xFF404040);
			if (v.levelCapped()) {
				// the XP for the next level is banked, the village tier holds the level back (M15): a full gold bar
				g.fill(bx, by, bx + 40, by + 5, LEVEL_CAPPED_GOLD);
				if (mx >= bx && mx < bx + 40 && my >= by - 2 && my < by + 7) {
					String need = dev.villageheart.core.job.Leveling.tierForNext(new dev.villageheart.core.job.Progress(Math.min(3, v.level()), v.xp()))
							.map(dev.villageheart.core.Tier::displayName).orElse("");
					g.setTooltipForNextFrame(font, t("villagers.level_capped", need, v.level() + 1), mx, my);
				}
			} else if (v.xpForNext() > 0) {
				int fill = Math.max(0, Math.min(40, 40 * v.xp() / v.xpForNext()));
				if (fill > 0) g.fill(bx, by, bx + fill, by + 5, 0xFF55CC55);
			} else {
				g.fill(bx, by, bx + 40, by + 5, 0xFF55CC55);
			}
			note(AREA_BODY, grp, bx, by, 40, 5);
			if (v.needs().known()) {
				drawNeed(g, ModTextures.NEED_FOOD, v.needs().fed(), x + 198, y + 3, grp);
				drawNeed(g, ModTextures.NEED_BED, v.needs().housed(), x + 216, y + 3, grp);
				drawNeed(g, ModTextures.NEED_PAY, v.needs().paid(), x + 234, y + 3, grp);
			}
		}
		Component act = v.workplaceInvalid() && v.isWorker() ? Component.translatable(HeartTexts.key("activity.workplace_invalid"))
				: v.shift().isGuard() ? Component.translatable(HeartTexts.key("shift." + v.shift().langId())).append(" - ").append(Component.translatable(HeartTexts.activityKey(v.activity(), v.jobId())))
				: Component.translatable(HeartTexts.activityKey(v.activity(), v.jobId()));
		boolean warn = v.workplaceInvalid() && v.isWorker() || dev.villageheart.core.heart.ActivityWarnings.isWarning(v.activity());
		int actColor = warn ? RED : GREY;
		text(g, act, x + 258, ty, w - 258 - 4, actColor, AREA_BODY, grp);
	}

	private void drawNeed(GuiGraphics g, Identifier icon, boolean met, int x, int y, int grp) {
		ModTextures.blit(g, icon, x, y, 16);
		if (!met) {
			g.fill(x, y, x + 16, y + 16, 0xAA000000);
			for (int i = 0; i < 16; i++) g.fill(x + i, y + 15 - i, x + i + 2, y + 16 - i, 0xFFFF3333);
		}
		note(AREA_BODY, grp, x, y, 16, 16);
	}

	private void drawBuildings(GuiGraphics g, int mx, int my) {
		if (lockedBody(g)) return;
		HeartSnapshot s = snap();
		int[] r = listRect();
		clip = new int[] {r[0], r[1], r[0] + r[2], r[1] + r[3]};
		g.enableScissor(r[0], r[1], r[0] + r[2], r[1] + r[3]);
		if (s.buildings().isEmpty()) text(g, t("buildings.none"), r[0] + 4, r[1] + 6, r[2] - 8, GREY, AREA_BODY, -1);
		int rowW = r[2] - SCROLLBAR_W - 4;
		int y = r[1] - scroll[2];
		Component hoverTip = null;
		for (BuildingRow b : s.buildings()) {
			if (y + ROW_H > r[1] && y < r[1] + r[3]) {
				drawBuildingRow(g, b, r[0], y, rowW, mx, my);
				if (mx >= r[0] && mx < r[0] + rowW && my >= y && my < y + ROW_H && my >= r[1] && my < r[1] + r[3] && b.reasons().size() > 1) {
					var m = Component.empty();
					boolean first = true;
					for (TextLine l : b.reasons()) {
						if (!first) m.append("\n");
						m.append(text(l));
						first = false;
					}
					hoverTip = m;
				}
			}
			y += ROW_H;
		}
		if (s.moreBuildings() > 0) {
			if (y + ROW_H > r[1] && y < r[1] + r[3]) text(g, t("buildings.more", s.moreBuildings()), r[0] + 4, y + 7, rowW - 8, GREY, AREA_BODY, groupCounter++);
			y += ROW_H;
		}
		if (!s.builds().isEmpty()) {
			if (y + BUILDS_HEADER_H > r[1] && y < r[1] + r[3]) text(g, t("buildings.builds"), r[0] + 4, y + 3, rowW - 8, GREY, AREA_BODY, groupCounter++);
			y += BUILDS_HEADER_H;
			for (BuildRow b : s.builds()) {
				if (y + ROW_H > r[1] && y < r[1] + r[3]) drawBuildRow(g, b, r[0], y, rowW);
				y += ROW_H;
			}
		}
		g.disableScissor();
		clip = new int[] {0, bodyTop(), width, bodyBottom()};
		scrollbar(g, r, contentHeight());
		if (hoverTip != null) g.setTooltipForNextFrame(font, hoverTip, mx, my);
	}

	/** A build of the Buildings tab: blueprint, where, its status line (what it needs, what is in the way) and the builder; two lines like a building. */
	private void drawBuildRow(GuiGraphics g, BuildRow b, int x, int y, int w) {
		int grp = groupCounter++;
		Component name = Component.translatableWithFallback("build.villageheart.blueprint." + b.blueprintId(), b.blueprintId());
		int nameW = text(g, name, x + 4, y + 2, 110, WHITE, AREA_BODY, grp);
		text(g, t("buildings.coords", b.x(), b.y(), b.z()), x + 4 + nameW + 6, y + 2, 120, GREY, AREA_BODY, grp);
		int color = switch (b.state()) {
			case WAITING_FOR_BUILDER -> GREY;
			case CLEARING, BUILDING, CHECKING -> GREEN;
			default -> YELLOW;
		};
		Identifier stateIcon = switch (b.state()) {
			case WAITING_FOR_BUILDER, NOT_LOADED -> ModTextures.STATUS_WAITING;
			case CLEARING, BUILDING, CHECKING -> ModTextures.STATUS_PROGRESS;
			default -> ModTextures.STATUS_BLOCKED;
		};
		ModTextures.blit(g, stateIcon, x + 240, y + 1, 8);
		note(AREA_BODY, grp, x + 240, y + 1, 8, 8);
		text(g, text(b.status()), x + 250, y + 2, w - 254, color, AREA_BODY, grp);
		if (!b.missing().isEmpty()) {
			Component first = text(b.missing().get(0));
			Component line = b.missing().size() > 1 ? Component.empty().append(first).append(" ").append(t("reasons.more", b.missing().size() - 1)) : first;
			text(g, line, x + 4, y + 12, w - 8, YELLOW, AREA_BODY, grp);
		} else if (!b.builderName().isEmpty()) {
			text(g, t("builds.builder", b.builderName()), x + 4, y + 12, w - 8, DARK_GREY, AREA_BODY, grp);
		}
	}

	private void drawBuildingRow(GuiGraphics g, BuildingRow b, int x, int y, int w, int mx, int my) {
		int grp = groupCounter++;
		boolean selected = pos(b).equals(selectedBuilding);
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW_H;
		if (selected) g.fill(x, y, x + w, y + ROW_H - 1, 0x66FFFFFF);
		else if (hover) g.fill(x, y, x + w, y + ROW_H - 1, 0x33FFFFFF);
		Component name = BuildingType.byId(b.typeId()).isPresent()
				? Component.translatable(HeartTexts.key("building." + b.typeId())) : Component.literal(b.typeId());
		if (BuildingType.byId(b.typeId()).isPresent()) {
			ModTextures.blit(g, ModTextures.building(b.typeId()), x + 2, y + 2, 8);
			note(AREA_BODY, grp, x + 2, y + 2, 8, 8);
		}
		int nameW = text(g, name, x + 12, y + 2, 100, WHITE, AREA_BODY, grp);
		text(g, t("buildings.coords", b.x(), b.y(), b.z()), x + 12 + nameW + 6, y + 2, 120, GREY, AREA_BODY, grp);
		Component status = Component.translatable(HeartTexts.key("status." + b.status().name().toLowerCase(Locale.ROOT)));
		int color = switch (b.status()) {
			case VALID -> GREEN;
			case INVALID -> RED;
			default -> GREY;
		};
		Identifier statusIcon = switch (b.status()) {
			case VALID -> ModTextures.STATUS_VALID;
			case INVALID -> ModTextures.STATUS_INVALID;
			case UNCHECKED, DEFERRED -> ModTextures.STATUS_UNCHECKED;
		};
		ModTextures.blit(g, statusIcon, x + 240, y + 1, 8);
		note(AREA_BODY, grp, x + 240, y + 1, 8, 8);
		text(g, status, x + 250, y + 2, 80, color, AREA_BODY, grp);
		Component detail = switch (b.detail()) {
			case WORKER_SLOTS -> t("detail.slots", b.workersHere(), b.detailCount());
			case HOMES -> t("detail.homes", b.detailCount());
			case CONTAINERS -> t("detail.containers", b.detailCount());
			case NONE -> Component.empty();
		};
		textRight(g, detail, x + w - 4, y + 2, 70, WHITE, AREA_BODY, grp);
		if (!b.reasons().isEmpty()) {
			Component first = text(b.reasons().get(0));
			int more = b.reasons().size() - 1 + b.moreReasons();
			Component line = more > 0 ? Component.empty().append(first).append(" ").append(t("reasons.more", more)) : first;
			text(g, line, x + 4, y + 12, w - 8, DARK_GREY, AREA_BODY, grp);
		}
	}

	private void drawStockpile(GuiGraphics g, int mx, int my) {
		if (lockedBody(g)) return;
		HeartSnapshot s = snap();
		Stock st = s.stock();
		int y = bodyTop() + 6;
		int grp = groupCounter++;
		int w = width - 2 * MARGIN;
		text(g, t("stock.line1", st.totalItems(), st.storages(), st.freeSlots(), st.slots()), MARGIN, y, w, WHITE, AREA_BODY, grp);
		int ft = s.overview().foodDaysTenths();
		text(g, ft < 0 ? t("stock.line2_none", st.nutrition()) : t("stock.line2", st.nutrition(), days(ft)), MARGIN, y + 12, w,
				WHITE, AREA_BODY, grp);
		boolean alerted = false;
		if (st.alert() != StorageAlert.OK) {
			for (AlertLine a : s.overview().alerts()) {
				if (a.code() == dev.villageheart.core.heart.AlertCode.NO_STORAGE || a.code() == dev.villageheart.core.heart.AlertCode.STORAGE_FULL) {
					text(g, text(a.text()), MARGIN, y + 24, w, severityColor(a.severity()), AREA_BODY, grp);
					alerted = true;
				}
			}
		}
		if (!alerted) {
			// the market line takes the place of the (usually empty) storage alert
			text(g, st.merchants() > 0 ? t("stock.market_sold", st.soldToday()) : t("stock.market_none"), MARGIN, y + 24, w, st.merchants() > 0 ? GREEN : GREY, AREA_BODY, grp);
		}
		int[] r = listRect();
		clip = new int[] {r[0], r[1], r[0] + r[2], r[1] + r[3]};
		g.enableScissor(r[0], r[1], r[0] + r[2], r[1] + r[3]);
		if (st.items().isEmpty()) text(g, t("stock.none"), r[0] + 4, r[1] + 4, r[2] - 8, GREY, AREA_BODY, -1);
		int cellW = (r[2] - SCROLLBAR_W - 4) / 2;
		int ry = r[1] - scroll[3];
		List<StockLine> items = st.items();
		for (int i = 0; i < items.size(); i += 2) {
			if (ry + STOCK_ROW_H > r[1] && ry < r[1] + r[3]) {
				int rg = groupCounter++;
				drawStockCell(g, items.get(i), r[0], ry, cellW, rg, mx, my);
				if (i + 1 < items.size()) drawStockCell(g, items.get(i + 1), r[0] + cellW, ry, cellW, rg, mx, my);
			}
			ry += STOCK_ROW_H;
		}
		if (st.moreKinds() > 0 && ry + STOCK_ROW_H > r[1] && ry < r[1] + r[3]) {
			text(g, t("stock.more", st.moreKinds()), r[0] + 4, ry + 5, r[2] - 8, GREY, AREA_BODY, groupCounter++);
		}
		g.disableScissor();
		clip = new int[] {0, bodyTop(), width, bodyBottom()};
		scrollbar(g, r, contentHeight());
		drawReserveBar(g, mx, my, st);
	}

	/** Labels and values of the reserve bar; the buttons themselves are widgets ({@link #reserveBar}). */
	private void drawReserveBar(GuiGraphics g, int mx, int my, Stock st) {
		int y = bodyBottom() - 24;
		int grp = groupCounter++;
		int ty = y + 6;
		var r = st.reserves();
		boolean edit = allows(Permission.SET_RESERVES);
		if (!edit) {
			// no buttons were laid out: compute the same positions for the read-only values
			int x = MARGIN;
			barFoodLabelX = x;
			barFoodValueX = x + font.width(t("reserve.label_food").getString()) + 6;
			barItemsLabelX = barFoodValueX + BAR_VALUE_W + 10;
			barItemsValueX = barItemsLabelX + font.width(t("reserve.label_items").getString()) + 6;
		}
		text(g, t("reserve.label_food"), barFoodLabelX, ty, 100, GREY, AREA_BODY, grp);
		text(g, t("reserve.days", r.foodDays()), barFoodValueX, ty, BAR_VALUE_W, WHITE, AREA_BODY, grp);
		text(g, t("reserve.label_items"), barItemsLabelX, ty, 100, GREY, AREA_BODY, grp);
		text(g, t("reserve.each", r.itemReserve()), barItemsValueX, ty, BAR_VALUE_W, WHITE, AREA_BODY, grp);
	}

	private void drawStockCell(GuiGraphics g, StockLine line, int x, int y, int w, int grp, int mx, int my) {
		ItemStack stack = stack(line.itemId());
		if (!stack.isEmpty()) g.renderItem(stack, x + 2, y + 1);
		note(AREA_BODY, grp, x + 2, y + 1, 16, 16);
		Component count = t("stock.count", line.count());
		int countW = font.width(count.getString());
		Component name = stack.isEmpty() ? Component.literal(line.itemId()) : stack.getHoverName();
		text(g, name, x + 22, y + 5, w - 22 - countW - 10, WHITE, AREA_BODY, grp);
		textRight(g, count, x + w - 6, y + 5, countW + 2, line.forSale() > 0 ? 0xFF55FF55 : GREY, AREA_BODY, grp);
		int[] lr = listRect();
		if (line.forSale() > 0 && mx >= x && mx < x + w && my >= y && my < y + STOCK_ROW_H && my >= lr[1] && my < lr[1] + lr[3]) {
			g.setTooltipForNextFrame(font, t("stock.for_sale", line.forSale()), mx, my);
		}
	}
}
