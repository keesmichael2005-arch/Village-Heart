package dev.villageheart.client.pocket;

import dev.villageheart.core.job.PocketRules;
import dev.villageheart.worker.pocket.PocketMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The hired villager's pocket, drawn in the villager trading frame. The trades list holds what they are carrying.
 * The right side is who they are. Nothing of theirs can be moved.
 */
public class PocketScreen extends AbstractContainerScreen<PocketMenu> {
	private static final Identifier TEXTURE = Identifier.parse("minecraft:textures/gui/container/villager.png");
	private static final Identifier XP_BG = Identifier.withDefaultNamespace("container/villager/experience_bar_background");
	private static final Identifier XP_FILL = Identifier.withDefaultNamespace("container/villager/experience_bar_current");
	private static final int TEXTURE_W = 512;
	private static final int TEXTURE_H = 256;
	private static final int TEXT = 0xFF404040;
	private static final int OFF = 0xFF8B8B8B;

	public PocketScreen(PocketMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 276;
		this.imageHeight = 166;
		this.inventoryLabelX = 107;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos, y = this.topPos;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, TEXTURE_W, TEXTURE_H);
		// The trading slots and arrow are part of the frame. This screen does not trade, so cover them.
		g.fill(x + 108, y + 24, x + 268, y + 74, 0xFFC6C6C6);
		for (int i = 0; i < PocketRules.SLOTS; i++) well(g, PocketMenu.pocketX(i), PocketMenu.pocketY(i));
		PocketMenu.View v = this.menu.view();
		if (v.guard()) {
			for (int i = 0; i < v.gear().size(); i++) well(g, PocketMenu.gearX(i), PocketMenu.GEAR_Y);
		}
		int barX = x + 136, barY = y + 16;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BG, barX, barY, 102, 5);
		int fill = v.xpForNext() <= 0 ? 102 : Math.max(0, Math.min(102, 102 * v.xp() / v.xpForNext()));
		if (fill > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_FILL, 102, 5, 0, 0, barX, barY, fill, 5);
	}

	private void well(GuiGraphics g, int sx, int sy) {
		int x = this.leftPos + sx - 1, y = this.topPos + sy - 1;
		g.fill(x, y, x + 18, y + 18, 0xFF373737);
		g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF555555);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		PocketMenu.View v = this.menu.view();
		center(g, v.jobName(), 53, 6);
		center(g, v.name(), 49 + this.imageWidth / 2, 6);
		g.drawString(this.font, v.jobName() + " L" + v.level(), 136, 26, TEXT, false);
		g.drawString(this.font, v.activity(), 136, 36, TEXT, false);
		int nx = 136;
		nx = word(g, "Fed", v.fed(), nx, 46);
		nx = word(g, "Housed", v.housed(), nx + 6, 46);
		nx = word(g, "Paid", v.paid(), nx + 6, 46);
		if (v.guard()) g.drawString(this.font, "Worn", nx + 8, 46, TEXT, false);
		g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
	}

	private void center(GuiGraphics g, String text, int mid, int y) {
		g.drawString(this.font, text, mid - this.font.width(text) / 2, y, TEXT, false);
	}

	private int word(GuiGraphics g, String text, boolean on, int x, int y) {
		g.drawString(this.font, text, x, y, on ? TEXT : OFF, false);
		return x + this.font.width(text);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
	}
}
