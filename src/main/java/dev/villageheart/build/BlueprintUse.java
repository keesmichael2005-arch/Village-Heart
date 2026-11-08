package dev.villageheart.build;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.core.blueprint.PlacementMode;
import dev.villageheart.core.blueprint.WallLines;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.blueprint.SiteRules.SiteProblem;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a click with a Blueprint item does, on the server (plan 3.7). Not sneaking: with no pending site, or a click outside the shown box,
 * the clicked ground becomes the new pending site (the rotation turns the blueprint's front towards the player); a click inside the box
 * confirms it. Sneaking: rotate the pending site a quarter turn. Every change re-runs the full site check; the result is stored in the
 * item's {@code blueprint_site} component for the client to draw and said on the action bar.
 */
public final class BlueprintUse {
	private BlueprintUse() {}

	/** The ground block of a click: the clicked block, or the one below when that can be replaced (short grass). */
	public static BlockPos groundOf(ServerLevel level, BlockPos clicked) {
		return level.getBlockState(clicked).canBeReplaced() ? clicked.below() : clicked;
	}

	private static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }

	/** @param clicked the clicked block, null for a click in the air (sneaking) */
	public static InteractionResult click(ServerPlayer player, ServerLevel level, ItemStack stack, @Nullable BlockPos clicked, boolean sneaking) {
		Optional<Blueprint> found = BlueprintItem.blueprintOf(stack);
		if (found.isEmpty()) return InteractionResult.PASS;
		Blueprint bp = found.get();
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) {
			player.displayClientMessage(Component.translatable("build.villageheart.msg.no_village"), true);
			return InteractionResult.SUCCESS;
		}
		Rank rank = village.get().rankOf(player.getUUID());
		BlueprintSite pending = stack.get(ModComponents.BLUEPRINT_SITE);
		if (bp.mode() == PlacementMode.LINE) return clickLine(player, level, village.get(), stack, bp, pending, clicked, sneaking, rank);

		if (sneaking && pending != null) {
			show(player, level, village.get(), stack, bp, pending.ground(), pending.rotationEnum().next(), rank);
			return InteractionResult.SUCCESS;
		}
		if (clicked == null) return InteractionResult.PASS;
		BlockPos ground = groundOf(level, clicked);
		if (pending != null && inShownBox(bp, pending, ground)) {
			confirm(player, level, village.get(), stack, bp, pending, rank);
			return InteractionResult.SUCCESS;
		}
		Dir towardPlayer = Dir.valueOf(player.getDirection().getOpposite().name());
		show(player, level, village.get(), stack, bp, ground, Rotation4.turning(bp.front(), towardPlayer), rank);
		return InteractionResult.SUCCESS;
	}

	// ---- LINE blueprints (M21): start, end, confirm ----

	private static void say(ServerPlayer player, TextLine line) {
		player.displayClientMessage(Component.translatableWithFallback(line.key(), line.fallback(), line.args().toArray()), true);
	}

	/**
	 * Click 1 sets the start; click 2 sets the end (a line problem keeps the start); a click on the shown line confirms, any other click is a
	 * new end; sneaking clears the start (docs/plans/M21.md 3.2 #1).
	 */
	private static InteractionResult clickLine(ServerPlayer player, ServerLevel level, VillageState village, ItemStack stack, Blueprint unit,
			@Nullable BlueprintSite pending, @Nullable BlockPos clicked, boolean sneaking, Rank rank) {
		if (sneaking) {
			if (pending == null) return InteractionResult.PASS;
			stack.remove(ModComponents.BLUEPRINT_SITE);
			say(player, new TextLine("build.villageheart.msg.line_cleared", "Line cleared: click the start"));
			return InteractionResult.SUCCESS;
		}
		if (clicked == null) return InteractionResult.PASS;
		BlockPos ground = groundOf(level, clicked);
		if (pending == null) {
			boolean allowed = dev.villageheart.core.heart.Permissions.allows(rank, dev.villageheart.core.heart.Permission.START_BLUEPRINT);
			TextLine text = allowed ? new TextLine("build.villageheart.msg.line_start", "Click the end: straight along x or z, up to 32 blocks")
					: new TextLine("build.villageheart.site.rank", "Only a Steward or the Owner can start a blueprint");
			stack.set(ModComponents.BLUEPRINT_SITE, new BlueprintSite(ground.immutable(), 0, allowed, text, 0));
			say(player, text);
			return InteractionResult.SUCCESS;
		}
		if (pending.length() > 0) {
			Blueprint line = Blueprints.resolve(unit.id(), new BlueprintParams(pending.length())).orElse(null);
			if (line != null && inShownBox(line, pending, ground)) {
				confirmLine(player, level, village, stack, line, pending, rank);
				return InteractionResult.SUCCESS;
			}
		}
		showLine(player, level, village, stack, unit, pending.ground(), ground, rank);
		return InteractionResult.SUCCESS;
	}

	/** Works out the line start to end, runs the site check and stores the result in the stack (and says it). */
	private static void showLine(ServerPlayer player, ServerLevel level, VillageState village, ItemStack stack, Blueprint unit, BlockPos start,
			BlockPos end, Rank rank) {
		WallLines.Result r = WallLines.of(pos(start), pos(end), Blueprints.minLength(unit), Balance.MAX_WALL_LENGTH);
		if (!r.ok()) {
			TextLine text = r.problem().text();
			stack.set(ModComponents.BLUEPRINT_SITE, new BlueprintSite(start.immutable(), 0, false, text, 0));
			say(player, text);
			return;
		}
		WallLines.Line line = r.line();
		Blueprint bp = Blueprints.resolve(unit.id(), new BlueprintParams(line.length())).orElseThrow();
		SiteProblem problem = SiteValidation.check(level, village, bp, new Placement(line.ground(), line.rotation()), rank);
		TextLine text = problem == null ? new TextLine("build.villageheart.msg.line_ok", "%s: %s blocks, level at y %s. Click the line to confirm, sneak to start over",
				List.of(unit.name(), Integer.toString(line.length()), Integer.toString(start.getY() + 1))) : problem.text();
		stack.set(ModComponents.BLUEPRINT_SITE, new BlueprintSite(start.immutable(), line.rotation().ordinal(), problem == null, text, line.length()));
		say(player, text);
	}

	private static void confirmLine(ServerPlayer player, ServerLevel level, VillageState village, ItemStack stack, Blueprint line,
			BlueprintSite site, Rank rank) {
		Rotation4 rotation = site.rotationEnum();
		SiteProblem problem = SiteValidation.check(level, village, line, new Placement(pos(site.ground()), rotation), rank);
		if (problem != null) {
			TextLine text = problem.text();
			stack.set(ModComponents.BLUEPRINT_SITE, new BlueprintSite(site.ground(), site.rotation(), false, text, site.length()));
			say(player, text);
			return;
		}
		BuildJobs.start(level, line, site.ground(), rotation, new BlueprintParams(site.length()), player.getUUID());
		stack.shrink(1);
		stack.remove(ModComponents.BLUEPRINT_SITE);
		var name = BuildTexts.blueprintName(line);
		player.sendSystemMessage(Component.translatable("build.villageheart.msg.site_set", Component.translatableWithFallback(name.key(), name.fallback()),
				"(" + site.ground().getX() + ", " + site.ground().getY() + ", " + site.ground().getZ() + ")"));
		if (village.workers().stream().noneMatch(w -> w.jobId().equals("builder"))) {
			player.sendSystemMessage(Component.translatable("build.villageheart.msg.no_builder"));
		}
	}

	/** A click confirms when its ground is a column of the shown box with a y from one below the site's ground up to the box's top. */
	public static boolean inShownBox(Blueprint bp, BlueprintSite site, BlockPos ground) {
		Placement pl = new Placement(pos(site.ground()), site.rotationEnum());
		Pos min = pl.min(bp), max = pl.max(bp);
		return ground.getX() >= min.x() && ground.getX() <= max.x() && ground.getZ() >= min.z() && ground.getZ() <= max.z()
				&& ground.getY() >= site.ground().getY() - 1 && ground.getY() <= max.y();
	}

	private static void show(ServerPlayer player, ServerLevel level, VillageState village, ItemStack stack, Blueprint bp, BlockPos ground,
			Rotation4 rotation, Rank rank) {
		SiteProblem problem = SiteValidation.check(level, village, bp, new Placement(pos(ground), rotation), rank);
		TextLine reason = problem == null ? new TextLine("build.villageheart.msg.site_ok", "Site ok") : problem.text();
		stack.set(ModComponents.BLUEPRINT_SITE, new BlueprintSite(ground.immutable(), rotation.ordinal(), problem == null, reason));
		player.displayClientMessage(problem == null ? Component.translatable("build.villageheart.msg.site_ok")
				: Component.translatableWithFallback(problem.key(), problem.fallback(), problem.args().toArray()), true);
	}

	private static void confirm(ServerPlayer player, ServerLevel level, VillageState village, ItemStack stack, Blueprint bp, BlueprintSite site,
			Rank rank) {
		Rotation4 rotation = site.rotationEnum();
		SiteProblem problem = SiteValidation.check(level, village, bp, new Placement(pos(site.ground()), rotation), rank);
		if (problem != null) {
			show(player, level, village, stack, bp, site.ground(), rotation, rank);
			return;
		}
		BuildJobs.start(level, bp, site.ground(), rotation, player.getUUID());
		stack.shrink(1);
		stack.remove(ModComponents.BLUEPRINT_SITE); // a stack that survives (creative) must not keep drawing the confirmed site
		var line = BuildTexts.blueprintName(bp);
		player.sendSystemMessage(Component.translatable("build.villageheart.msg.site_set", Component.translatableWithFallback(line.key(), line.fallback()),
				"(" + site.ground().getX() + ", " + site.ground().getY() + ", " + site.ground().getZ() + ")"));
		if (village.workers().stream().noneMatch(w -> w.jobId().equals("builder"))) {
			player.sendSystemMessage(Component.translatable("build.villageheart.msg.no_builder"));
		}
	}
}
