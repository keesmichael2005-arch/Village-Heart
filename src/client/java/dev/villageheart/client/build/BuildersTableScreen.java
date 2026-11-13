package dev.villageheart.client.build;

import dev.villageheart.client.art.ModTextures;
import dev.villageheart.build.BuildersTableMenu;
import dev.villageheart.build.net.CancelBuildPayload;
import dev.villageheart.build.net.TakeBlueprintPayload;
import dev.villageheart.core.Balance;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.blueprint.PlacementMode;
import dev.villageheart.core.blueprint.TableSnapshot;
import dev.villageheart.core.blueprint.TableSnapshot.TableBlueprint;
import dev.villageheart.core.blueprint.TableSnapshot.TableBuild;
import dev.villageheart.core.blueprint.TableSnapshot.TableMaterial;
import dev.villageheart.core.blueprint.TableTexts;
import dev.villageheart.core.heart.Permission;
import dev.villageheart.core.heart.Permissions;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.heart.net.HeartActionResultPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Builder's Table screen (plan 3.7), sized for the smallest supported window (427 x 240 GUI pixels): the blueprints on the left, the
 * selected one on the right with its material list (what Storage could still give / what it costs) and the Take button, the builds in progress
 * below with a Cancel button each (Steward and above). Every write is a payload the server checks again.
 */
@Environment(EnvType.CLIENT)
public class BuildersTableScreen extends Screen implements MenuAccess<BuildersTableMenu> {
	static final int MARGIN = 8;
	static final int TOP = 26;
	static final int LIST_W = 150;
	static final int ROW_H = 16;
	static final int BUILD_H = 13;
	static final int FOOTER_H = 33;
	/** Rows of the blueprint list that fit above the builds section. */
	static final int LIST_ROWS = 7;
	/** Material lines that fit above the Take button. */
	static final int MATERIAL_ROWS = 6;
	static final int WHITE = 0xFFFFFFFF, GREY = 0xFFAAAAAA, DARK_GREY = 0xFF808080, GREEN = 0xFF55FF55, RED = 0xFFFF5555, YELLOW = 0xFFFFD45A;

	private final BuildersTableMenu menu;
	private int selected;
	private int scroll;
	private int listScroll;
	private int materialScroll;
	private int seenVersion = -1;
	private Button take;
	private Button done;
	private Component status;
	private int statusColor;
	private int statusTicks;
	private int clipped, lastClipped;

	public BuildersTableScreen(BuildersTableMenu menu, Inventory inventory, Component title) {
		super(title);
		this.menu = menu;
	}

	@Override
	public BuildersTableMenu getMenu() { return menu; }

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public boolean isInGameUi() { return true; }

	@Override
	public void onClose() {
		if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
		else super.onClose();
	}

	private static Component t(String suffix, Object... args) {
		return Component.translatableWithFallback(TableTexts.key(suffix), TableTexts.english(suffix), args);
	}

	private static Component text(TextLine l) { return Component.translatableWithFallback(l.key(), l.fallback(), l.args().toArray()); }

	private TableSnapshot snap() { return menu.snapshot(); }

	private boolean mayStart() { return Permissions.allows(snap().viewerRank(), Permission.START_BLUEPRINT); }

	private TableBlueprint selectedBlueprint() {
		List<TableBlueprint> l = snap().blueprints();
		return selected >= 0 && selected < l.size() ? l.get(selected) : null;
	}

	/** Why Take is off, or null when it is on. */
	private Component takeProblem() {
		TableBlueprint b = selectedBlueprint();
		if (b == null) return t("select");
		if (!mayStart()) return t("no_permission");
		if (b.locked()) return t("locked", Blueprints.byId(b.id()).map(bp -> bp.tier().displayName()).orElse(dev.villageheart.core.Tier.VILLAGE.displayName()));
		if (snap().builds().size() >= Balance.MAX_ACTIVE_BUILDS) return t("too_many");
		return null;
	}

	@Override
	protected void init() {
		done = Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width - MARGIN - 100, height - FOOTER_H + 6, 100, 20).build();
		addRenderableWidget(done);
		take = Button.builder(t("take"), b -> {
			TableBlueprint bp = selectedBlueprint();
			if (bp != null && takeProblem() == null) ClientPlayNetworking.send(new TakeBlueprintPayload(menu.containerId, bp.id()));
		}).bounds(rightX(), TOP + 100, 130, 20).build();
		addRenderableWidget(take);
		refreshTake();
	}

	private int rightX() { return MARGIN + LIST_W + 8; }

	private void refreshTake() {
		if (take == null) return;
		Component problem = takeProblem();
		take.active = problem == null;
		take.setTooltip(problem == null ? null : Tooltip.create(problem));
	}

	@Override
	public void tick() {
		super.tick();
		if (statusTicks > 0) statusTicks--;
		if (menu.version() != seenVersion) {
			seenVersion = menu.version();
			selected = Math.min(selected, Math.max(0, snap().blueprints().size() - 1));
			scroll = Math.max(0, Math.min(scroll, maxScroll()));
		}
		refreshTake();
	}

	public void showResult(HeartActionResultPayload p) {
		status = Component.translatableWithFallback(p.key(), p.fallback(), p.args().toArray());
		statusColor = p.ok() ? GREEN : RED;
		statusTicks = 100;
	}

	public int clippedThisFrame() { return lastClipped; }

	/** The last reply of the server, as shown in the footer (for tests); empty when there is none. */
	public String statusText() { return status == null ? "" : status.getString(); }

	// ---- geometry --------------------------------------------------------------------------------

	private int buildsTop() { return TOP + 130; }

	private int buildsBottom() { return height - FOOTER_H - 4; }

	private int maxScroll() { return Math.max(0, snap().builds().size() * BUILD_H - (buildsBottom() - buildsTop() - 12)); }

	private int listTop() { return TOP + 12; }

	private int listBottom() { return listTop() + LIST_ROWS * ROW_H; }

	/** GUI coordinates of the middle of the blueprint row {@code i} (for tests). */
	public int[] blueprintRowCentre(int i) { return new int[] {MARGIN + LIST_W / 2, listTop() + (i - listScroll) * ROW_H + ROW_H / 2}; }

	/** GUI coordinates of the middle of the Take button (for tests). */
	public int[] takeButtonCentre() { return new int[] {take.getX() + take.getWidth() / 2, take.getY() + take.getHeight() / 2}; }

	public int selectedIndex() { return selected; }

	public Button takeButton() { return take; }

	// ---- input -----------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
		if (super.mouseClicked(ev, doubleClick)) return true;
		double mx = ev.x(), my = ev.y();
		List<TableBlueprint> bps = snap().blueprints();
		for (int i = 0; i < bps.size(); i++) {
			int y = listTop() + (i - listScroll) * ROW_H;
			if (y < listTop() || y + ROW_H > listBottom()) continue;
			if (mx >= MARGIN && mx < MARGIN + LIST_W && my >= y && my < y + ROW_H - 1) {
				if (selected != i) materialScroll = 0;
				selected = i;
				refreshTake();
				return true;
			}
		}
		if (mayStart()) {
			int i = 0;
			for (TableBuild b : snap().builds()) {
				int y = buildsTop() + 12 + i * BUILD_H - scroll;
				int x = width - MARGIN - 52;
				if (y >= buildsTop() + 10 && y + BUILD_H <= buildsBottom() + 2 && mx >= x && mx < x + 52 && my >= y && my < y + BUILD_H - 1) {
					ClientPlayNetworking.send(new CancelBuildPayload(menu.containerId, b.id()));
					return true;
				}
				i++;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (mx >= MARGIN && mx < MARGIN + LIST_W && my >= listTop() && my < listBottom()) {
			int max = Math.max(0, snap().blueprints().size() - LIST_ROWS);
			listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.round(sy)));
			return true;
		}
		TableBlueprint sel = selectedBlueprint();
		if (sel != null && mx >= rightX() && my >= TOP + 40 && my < TOP + 100) {
			int max = Math.max(0, sel.materials().size() - MATERIAL_ROWS);
			materialScroll = Math.max(0, Math.min(max, materialScroll - (int) Math.round(sy)));
			return true;
		}
		if (my >= buildsTop()) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(sy * BUILD_H)));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent ev) {
		if (minecraft != null && minecraft.options.keyInventory.matches(ev)) {
			onClose();
			return true;
		}
		return super.keyPressed(ev);
	}

	// ---- rendering -------------------------------------------------------------------------------

	private int line(GuiGraphics g, String s, int x, int y, int maxW, int color) {
		if (font.width(s) > maxW) {
			s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
			clipped++;
		}
		g.drawString(font, s, x, y, color);
		return font.width(s);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		super.renderBackground(g, mx, my, pt);
		lastClipped = clipped;
		clipped = 0;
		TableSnapshot s = snap();
		g.fill(0, 0, width, 22, 0xFF202020);
		g.fill(0, 22, width, height - FOOTER_H, 0x99000000);
		g.fill(0, height - FOOTER_H, width, height, 0xFF202020);
		g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0xFF000000);
		ModTextures.blit(g, ModTextures.TABLE_BLUEPRINT, MARGIN, 3, 16);
		line(g, t("title").getString(), MARGIN + 18, 7, width - 2 * MARGIN - 18, WHITE);

		// the blueprints
		line(g, t("blueprints").getString(), MARGIN, TOP, LIST_W, GREY);
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, s.blueprints().size() - LIST_ROWS)));
		g.enableScissor(MARGIN, listTop(), MARGIN + LIST_W, listBottom());
		for (int i = 0; i < s.blueprints().size(); i++) {
			TableBlueprint b = s.blueprints().get(i);
			int y = listTop() + (i - listScroll) * ROW_H;
			if (y + ROW_H <= listTop() || y >= listBottom()) continue;
			boolean hover = mx >= MARGIN && mx < MARGIN + LIST_W && my >= y && my < y + ROW_H - 1;
			if (i == selected) g.fill(MARGIN, y, MARGIN + LIST_W, y + ROW_H - 1, 0x66FFFFFF);
			else if (hover) g.fill(MARGIN, y, MARGIN + LIST_W, y + ROW_H - 1, 0x33FFFFFF);
			String name = Component.translatableWithFallback("build.villageheart.blueprint." + b.id(), b.id()).getString();
			Blueprint bp = Blueprints.byId(b.id()).orElse(null);
			String info = b.locked() ? (bp == null ? "" : bp.tier().displayName()) : bp != null && bp.mode() == PlacementMode.LINE ? t("line_tag").getString()
					: b.width() + "x" + b.depth() + "x" + b.height();
			int iw = font.width(info);
			ModTextures.blit(g, b.locked() ? ModTextures.TABLE_LOCKED : ModTextures.TABLE_BLUEPRINT, MARGIN + 2, y + 4, 8);
			line(g, name, MARGIN + 12, y + 4, LIST_W - 20 - iw, b.locked() ? DARK_GREY : WHITE);
			g.drawString(font, info, MARGIN + LIST_W - 4 - iw, y + 4, b.locked() ? RED : GREY);
		}
		g.disableScissor();

		// the selected blueprint
		TableBlueprint sel = selectedBlueprint();
		int rx = rightX(), rw = width - rx - MARGIN;
		if (sel != null) {
			String name = Component.translatableWithFallback("build.villageheart.blueprint." + sel.id(), sel.id()).getString();
			line(g, name, rx, TOP, rw, WHITE);
			Blueprint selBp = Blueprints.byId(sel.id()).orElse(null);
			boolean isLine = selBp != null && selBp.mode() == PlacementMode.LINE;
			if (sel.locked() && selBp != null) {
				line(g, t("locked", selBp.tier().displayName()).getString(), rx, TOP + 12, rw, RED);
			} else {
				line(g, isLine ? t("line_hint").getString() : t("size", sel.width(), sel.depth(), sel.height()).getString(), rx, TOP + 12, rw, GREY);
			}
			ModTextures.blit(g, ModTextures.TABLE_MATERIALS, rx, TOP + 26, 8);
			line(g, t(!isLine ? "materials" : sel.id().equals("gate") ? "materials_gate" : "materials_wall").getString(), rx + 10, TOP + 28, rw - 10, GREY);
			materialScroll = Math.max(0, Math.min(materialScroll, Math.max(0, sel.materials().size() - MATERIAL_ROWS)));
			g.enableScissor(rx, TOP + 40, rx + rw, TOP + 100);
			int y = TOP + 40 - materialScroll * 10;
			for (TableMaterial m : sel.materials()) {
				int row = y;
				y += 10;
				if (row + 10 <= TOP + 40 || row >= TOP + 100) continue;
				boolean enough = m.have() >= m.needed();
				line(g, m.name(), rx + 4, row, rw - 70, WHITE);
				String have = m.have() + " / " + m.needed();
				g.drawString(font, have, rx + rw - font.width(have), row, enough ? GREEN : RED);
			}
			g.disableScissor();
			if (sel.id().equals("gate")) line(g, t("gate_note").getString(), rx, TOP + 88, rw, DARK_GREY);
		}
		take.setPosition(rx, TOP + 100);

		// the builds
		int by = buildsTop();
		line(g, t("builds").getString(), MARGIN, by, 100, GREY);
		g.enableScissor(0, by + 10, width, buildsBottom() + 2);
		if (s.builds().isEmpty()) line(g, t("builds_none").getString(), MARGIN + 4, by + 14, width - 2 * MARGIN, DARK_GREY);
		int i = 0;
		for (TableBuild b : s.builds()) {
			int y = by + 12 + i * BUILD_H - scroll;
			i++;
			if (y + BUILD_H < by + 10 || y > buildsBottom() + 2) continue;
			String bpName = Component.translatableWithFallback("build.villageheart.blueprint." + b.row().blueprintId(), b.row().blueprintId()).getString();
			int w = line(g, bpName + " (" + b.row().x() + ", " + b.row().y() + ", " + b.row().z() + ")", MARGIN + 4, y + 2, 170, WHITE);
			Component statusLine = b.row().missing().isEmpty() || b.row().state() != BuildStatus.NEEDS_MATERIALS ? text(b.row().status()) : text(b.row().missing().get(0));
			int color = switch (b.row().state()) {
				case NEEDS_MATERIALS, BLOCKED, IN_THE_WAY, UNREACHABLE, NOT_LOADED -> YELLOW;
				default -> GREY;
			};
			line(g, statusLine.getString(), MARGIN + 4 + 176, y + 2, width - 2 * MARGIN - 176 - 60, color);
			if (mayStart()) {
				int x = width - MARGIN - 52;
				boolean hover = mx >= x && mx < x + 52 && my >= y && my < y + BUILD_H - 1;
				g.fill(x, y, x + 52, y + BUILD_H - 1, hover ? 0xFF7A3030 : 0xFF5A2020);
				String c = t("cancel").getString();
				g.drawString(font, c, x + (52 - font.width(c)) / 2, y + 2, WHITE);
			}
		}
		g.disableScissor();
		if (statusTicks > 0 && status != null) line(g, status.getString(), MARGIN, height - FOOTER_H + 12, width - 3 * MARGIN - 100, statusColor);
	}
}
