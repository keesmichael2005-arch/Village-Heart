package dev.villageheart.build;

import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.blueprint.SiteRules.SiteProblem;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import dev.villageheart.world.VillageClaims;
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
