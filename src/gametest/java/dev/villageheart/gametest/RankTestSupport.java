package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.rightClickAbs;

import dev.villageheart.core.Rank;
import dev.villageheart.gametest.HeartTestSupport.OverlayPlayer;
import dev.villageheart.rank.ContainerProtection;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Helpers of the rank and container-protection GameTests (M24). */
public final class RankTestSupport {
	private RankTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:ranks_" + name; }

	/** A survival player that records its action bar, standing next to the Heart, with the given rank (Visitor: none). */
	public static OverlayPlayer ranked(GameTestHelper h, String name, Rank rank) {
		OverlayPlayer p = (OverlayPlayer) VillageTestSupport.player(h, name, GameType.SURVIVAL, OverlayPlayer::new);
		if (rank != Rank.VISITOR && rank != Rank.OWNER) VillageClaims.setRank(h.getLevel().getServer(), p.getUUID(), rank);
		HeartTestSupport.standNearHeart(h, p);
		return p;
	}

	/** Right-click the top face of the block at {@code rel} with an empty hand (the real {@code useItemOn} path). */
	public static InteractionResult use(GameTestHelper h, ServerPlayer p, BlockPos rel) {
		return use(h, p, rel, ItemStack.EMPTY);
	}

	public static InteractionResult use(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack stack) {
		ContainerProtection.resetForTests();
		return rightClickAbs(h.getLevel(), p, h.absolutePos(rel), Direction.UP, stack);
	}

	public static boolean hasOverlay(OverlayPlayer p, String key) {
		return p.overlays.stream().anyMatch(c -> isKey(c, key));
	}

	private static boolean isKey(Component c, String key) {
		return c.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
	}
}
