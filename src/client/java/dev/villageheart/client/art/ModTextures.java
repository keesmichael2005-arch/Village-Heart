package dev.villageheart.client.art;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * GUI sprites drawn by the Heart screen and the Builder's Table. The file path is the string literal so a test can
 * check the PNG exists; the sprite id is that path under {@code textures/gui/sprites/} without the suffix.
 */
@Environment(EnvType.CLIENT)
public final class ModTextures {
	private ModTextures() {}

	public static final Identifier TAB_OVERVIEW = sprite("textures/gui/sprites/heart/tab_overview.png");
	public static final Identifier TAB_VILLAGERS = sprite("textures/gui/sprites/heart/tab_villagers.png");
	public static final Identifier TAB_BUILDINGS = sprite("textures/gui/sprites/heart/tab_buildings.png");
	public static final Identifier TAB_STOCKPILE = sprite("textures/gui/sprites/heart/tab_stockpile.png");
	public static final Identifier TAB_RANKS = sprite("textures/gui/sprites/heart/tab_ranks.png");
	public static final Identifier[] TABS = {TAB_OVERVIEW, TAB_VILLAGERS, TAB_BUILDINGS, TAB_STOCKPILE, TAB_RANKS, TAB_OVERVIEW};

	public static final Identifier TIER_HAMLET = sprite("textures/gui/sprites/heart/tier_hamlet.png");
	public static final Identifier TIER_VILLAGE = sprite("textures/gui/sprites/heart/tier_village.png");
	public static final Identifier TIER_TOWN = sprite("textures/gui/sprites/heart/tier_town.png");

	public static final Identifier RANK_OWNER = sprite("textures/gui/sprites/heart/rank_owner.png");
	public static final Identifier RANK_STEWARD = sprite("textures/gui/sprites/heart/rank_steward.png");
	public static final Identifier RANK_RESIDENT = sprite("textures/gui/sprites/heart/rank_resident.png");
	public static final Identifier RANK_VISITOR = sprite("textures/gui/sprites/heart/rank_visitor.png");

	public static final Identifier GOAL_MET = sprite("textures/gui/sprites/heart/goal_met.png");
	public static final Identifier GOAL_OPEN = sprite("textures/gui/sprites/heart/goal_open.png");
	public static final Identifier ALERT_DANGER = sprite("textures/gui/sprites/heart/alert_danger.png");
	public static final Identifier ALERT_WARNING = sprite("textures/gui/sprites/heart/alert_warning.png");

	public static final Identifier STATUS_VALID = sprite("textures/gui/sprites/heart/status_valid.png");
	public static final Identifier STATUS_INVALID = sprite("textures/gui/sprites/heart/status_invalid.png");
	public static final Identifier STATUS_UNCHECKED = sprite("textures/gui/sprites/heart/status_unchecked.png");
	public static final Identifier STATUS_PROGRESS = sprite("textures/gui/sprites/heart/status_progress.png");
	public static final Identifier STATUS_WAITING = sprite("textures/gui/sprites/heart/status_waiting.png");
	public static final Identifier STATUS_BLOCKED = sprite("textures/gui/sprites/heart/status_blocked.png");

	public static final Identifier NEED_FOOD = sprite("textures/gui/sprites/heart/need_food.png");
	public static final Identifier NEED_BED = sprite("textures/gui/sprites/heart/need_bed.png");
	public static final Identifier NEED_PAY = sprite("textures/gui/sprites/heart/need_pay.png");

	public static final Identifier TABLE_BLUEPRINT = sprite("textures/gui/sprites/table/blueprint.png");
	public static final Identifier TABLE_LOCKED = sprite("textures/gui/sprites/table/locked.png");
	public static final Identifier TABLE_MATERIALS = sprite("textures/gui/sprites/table/materials.png");

	private static Identifier sprite(String file) {
		String id = file.substring("textures/gui/sprites/".length(), file.length() - ".png".length());
		return Identifier.fromNamespaceAndPath("villageheart", id);
	}

	public static Identifier job(String jobId) {
		return sprite("textures/gui/sprites/heart/job_" + jobId + ".png");
	}

	public static Identifier building(String typeId) {
		return sprite("textures/gui/sprites/heart/building_" + typeId + ".png");
	}

	public static Identifier rank(dev.villageheart.core.Rank rank) {
		return switch (rank) {
			case OWNER -> RANK_OWNER;
			case STEWARD -> RANK_STEWARD;
			case RESIDENT -> RANK_RESIDENT;
			case VISITOR -> RANK_VISITOR;
		};
	}

	public static Identifier tier(dev.villageheart.core.Tier tier) {
		return switch (tier) {
			case HAMLET -> TIER_HAMLET;
			case VILLAGE -> TIER_VILLAGE;
			case TOWN -> TIER_TOWN;
		};
	}

	public static void blit(GuiGraphics g, Identifier sprite, int x, int y, int size) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size);
	}
}
