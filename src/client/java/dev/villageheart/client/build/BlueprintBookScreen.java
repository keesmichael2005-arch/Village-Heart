package dev.villageheart.client.build;

import dev.villageheart.client.art.ModTextures;
import dev.villageheart.build.net.SelectBookBlueprintPayload;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintCatalog;
import dev.villageheart.core.blueprint.BlueprintGroup;
import dev.villageheart.core.blueprint.BookTexts;
import dev.villageheart.core.blueprint.MaterialList;
import dev.villageheart.core.blueprint.PlacementMode;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/**
 * The Blueprint Book: groups on the left, plan tiles in the middle, the hovered plan on the right. A click arms that plan and closes the book.
 * Drawn in the same bars and colours as the Heart and the Builder's Table.
 */
@Environment(EnvType.CLIENT)
public class BlueprintBookScreen extends Screen {
	static final int MARGIN = 8;
	static final int TOP = 28;
	static final int FOOTER_H = 33;
	static final int GROUP_W = 92;
	static final int DETAIL_W = 148;
	static final int TILE_H = 22;
	static final int TILE_GAP = 4;
	static final int WHITE = 0xFFFFFFFF, GREY = 0xFFAAAAAA, DARK_GREY = 0xFF808080;

	private final InteractionHand hand;
	private BlueprintGroup group = BlueprintGroup.BUILDINGS;
	private int scroll;
	private int hover = -1;

	public BlueprintBookScreen(InteractionHand hand) {
		super(Component.translatable(BookTexts.key("title")));
		this.hand = hand;
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public boolean isInGameUi() { return true; }

	public BlueprintGroup group() { return group; }

	public void show(BlueprintGroup group) {
		this.group = group;
		scroll = 0;
		hover = -1;
	}

	public int visibleCount() { return plans().size(); }

	/** GUI coordinates of the middle of category row {@code i}. */
	public int[] categoryCentre(int i) {
		return new int[] {MARGIN + GROUP_W / 2, TOP + 14 + i * 18 + 8};
	}

	private static Component t(String suffix, Object... args) {
		return Component.translatableWithFallback(BookTexts.key(suffix), BookTexts.english(suffix), args);
	}

	private List<Blueprint> plans() { return BlueprintCatalog.in(group); }

	private int gridX() { return MARGIN + GROUP_W + 8; }

	private int gridRight() { return width - MARGIN - DETAIL_W - 8; }

	private int gridBottom() { return height - FOOTER_H - 4; }

	private int columns() {
		int span = Math.max(70, gridRight() - gridX());
		return Math.max(1, span / 90);
	}

	private int tileW() {
		int cols = columns();
		int span = Math.max(70, gridRight() - gridX());
		return Math.max(70, (span - (cols - 1) * TILE_GAP) / cols);
	}

	private int maxScroll() {
		int rows = (plans().size() + columns() - 1) / columns();
		int content = rows * (TILE_H + TILE_GAP);
		return Math.max(0, content - (gridBottom() - TOP));
	}

	private int tileIndex(double mx, double my) {
		if (mx < gridX() || mx >= gridRight() || my < TOP || my >= gridBottom()) return -1;
		int colW = tileW() + TILE_GAP;
		int rowH = TILE_H + TILE_GAP;
		int col = (int) ((mx - gridX()) / colW);
		int row = (int) ((my - TOP + scroll) / rowH);
		if (col < 0 || col >= columns()) return -1;
		int localX = (int) (mx - gridX()) - col * colW;
		int localY = (int) (my - TOP + scroll) - row * rowH;
		if (localX >= tileW() || localY >= TILE_H) return -1;
		int i = row * columns() + col;
		return i >= 0 && i < plans().size() ? i : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
		if (super.mouseClicked(ev, doubleClick)) return true;
		double mx = ev.x(), my = ev.y();
		BlueprintGroup[] groups = BlueprintGroup.values();
		for (int i = 0; i < groups.length; i++) {
			int y = TOP + 14 + i * 18;
			if (mx >= MARGIN && mx < MARGIN + GROUP_W && my >= y && my < y + 16) {
				show(groups[i]);
				return true;
			}
		}
		int i = tileIndex(mx, my);
		if (i >= 0) {
			ClientPlayNetworking.send(new SelectBookBlueprintPayload(hand.ordinal(), plans().get(i).id()));
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (mx >= gridX() && mx < gridRight() && my >= TOP && my < gridBottom()) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(sy * (TILE_H + TILE_GAP))));
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

	private void scrollbar(GuiGraphics g, int count, int cols) {
		int rows = (count + cols - 1) / cols;
		int content = rows * (TILE_H + TILE_GAP);
		int view = gridBottom() - TOP;
		if (content <= view) return;
		int x = gridRight() + 2;
		g.fill(x, TOP, x + 4, gridBottom(), 0xFF101010);
		int thumbH = Math.max(12, view * view / content);
		int ty = TOP + (view - thumbH) * scroll / Math.max(1, content - view);
		g.fill(x, ty, x + 4, ty + thumbH, 0xFFA0A0A0);
	}

	private int line(GuiGraphics g, String s, int x, int y, int maxW, int color) {
		if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
		g.drawString(font, s, x, y, color);
		return font.width(s);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		super.renderBackground(g, mx, my, pt);
		g.fill(0, 0, width, 22, 0xFF202020);
		g.fill(0, 22, width, height - FOOTER_H, 0x99000000);
		g.fill(0, height - FOOTER_H, width, height, 0xFF202020);
		g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, 0xFF000000);
		ModTextures.blit(g, ModTextures.TABLE_BLUEPRINT, MARGIN, 3, 16);
		line(g, t("title").getString(), MARGIN + 18, 7, width - 2 * MARGIN - 18, WHITE);

		BlueprintGroup[] groups = BlueprintGroup.values();
		for (int i = 0; i < groups.length; i++) {
			BlueprintGroup gr = groups[i];
			int y = TOP + 14 + i * 18;
			boolean on = gr == group;
			boolean over = mx >= MARGIN && mx < MARGIN + GROUP_W && my >= y && my < y + 16;
			if (on) g.fill(MARGIN, y, MARGIN + GROUP_W, y + 16, 0x66FFFFFF);
			else if (over) g.fill(MARGIN, y, MARGIN + GROUP_W, y + 16, 0x33FFFFFF);
			String label = t(gr.id()).getString();
			String count = Integer.toString(BlueprintCatalog.in(gr).size());
			line(g, label, MARGIN + 4, y + 4, GROUP_W - 22, on ? WHITE : GREY);
			g.drawString(font, count, MARGIN + GROUP_W - 4 - font.width(count), y + 4, DARK_GREY);
		}

		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		hover = tileIndex(mx, my);
		List<Blueprint> list = plans();
		int cols = columns();
		int tw = tileW();
		g.enableScissor(gridX(), TOP, gridRight(), gridBottom());
		for (int i = 0; i < list.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int x = gridX() + col * (tw + TILE_GAP);
			int y = TOP + row * (TILE_H + TILE_GAP) - scroll;
			if (y + TILE_H < TOP || y > gridBottom()) continue;
			boolean over = i == hover;
			g.fill(x, y, x + tw, y + TILE_H, over ? 0x66FFFFFF : 0x33FFFFFF);
			ModTextures.blit(g, ModTextures.TABLE_BLUEPRINT, x + 3, y + 7, 8);
			String name = Component.translatableWithFallback("build.villageheart.blueprint." + list.get(i).id(), list.get(i).name()).getString();
			line(g, name, x + 14, y + 7, tw - 18, WHITE);
		}
		g.disableScissor();
		scrollbar(g, list.size(), cols);

		int rx = width - MARGIN - DETAIL_W;
		Blueprint shown = hover >= 0 && hover < list.size() ? list.get(hover) : null;
		if (shown != null) {
			String name = Component.translatableWithFallback("build.villageheart.blueprint." + shown.id(), shown.name()).getString();
			line(g, name, rx, TOP, DETAIL_W, WHITE);
			String meta = shown.mode() == PlacementMode.LINE ? t("line").getString()
					: t("size", shown.width(), shown.depth(), shown.height()).getString();
			line(g, meta, rx, TOP + 12, DETAIL_W, GREY);
			line(g, t("tier", shown.tier().displayName()).getString(), rx, TOP + 24, DETAIL_W, DARK_GREY);
			int y = TOP + 40;
			for (MaterialList.Line material : shown.materials().lines()) {
				if (y > gridBottom() - 10) break;
				line(g, material.material().words(material.count()), rx, y, DETAIL_W, GREY);
				y += 10;
			}
		}
		line(g, t("pick").getString(), MARGIN, height - FOOTER_H + 8, width - 2 * MARGIN, GREY);
		line(g, t("schematics").getString(), MARGIN, height - FOOTER_H + 18, width - 2 * MARGIN, DARK_GREY);
	}
}
