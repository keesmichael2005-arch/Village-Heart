package dev.villageheart.client.pocket;

import dev.villageheart.core.job.PocketRules;
import dev.villageheart.worker.pocket.PocketMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The read-only card for one hired villager. Nothing can be moved. */
public class PocketScreen extends AbstractContainerScreen<PocketMenu> {
	public PocketScreen(PocketMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 8 + PocketRules.SLOTS * 18 + 8;
		this.imageHeight = menu.view().guard() ? 136 : 110;
		this.inventoryLabelY = this.imageHeight + 20;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos, y = this.topPos;
		g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
		g.fill(x + 3, y + 3, x + this.imageWidth - 3, y + this.imageHeight - 3, 0xFF8B8B8B);
		PocketMenu.View v = this.menu.view();
		g.drawString(this.font, v.name(), x + 8, y + 8, 0xFFFFFF, false);
		g.drawString(this.font, v.jobName() + " L" + v.level(), x + 8, y + 20, 0xFFFFFF, false);
		int bx = x + 8, by = y + 32;
		g.fill(bx, by, bx + 40, by + 5, 0xFF404040);
		int fill = v.xpForNext() <= 0 ? 40 : Math.max(0, Math.min(40, 40 * v.xp() / v.xpForNext()));
		if (fill > 0) g.fill(bx, by, bx + fill, by + 5, 0xFF55CC55);
		g.drawString(this.font, v.activity(), x + 8, y + 42, 0xFFFFFF, false);
		int nx = x + 8;
		nx = word(g, "Fed", v.fed(), nx, y + 56);
		nx = word(g, "Housed", v.housed(), nx + 8, y + 56);
		word(g, "Paid", v.paid(), nx + 8, y + 56);
		if (v.guard()) g.drawString(this.font, "Worn", x + 8, y + 96, 0xFFFFFF, false);
	}

	private int word(GuiGraphics g, String text, boolean on, int x, int y) {
		g.drawString(this.font, text, x, y, on ? 0xFFFFFF : 0xFF666666, false);
		return x + this.font.width(text);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
	}
}
