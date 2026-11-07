package dev.villageheart.heart;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.heart.HeartAction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the Heart screen actions. Step 3 only has the entry point the C2S receivers call; the gate, the three
 * actions and their replies arrive in step 6 (needs M7's hiring).
 */
public final class HeartActions {
	private HeartActions() {}

	public static void handle(ServerPlayer player, int containerId, HeartAction action) {
		VillageHeart.LOG.debug("Heart action from {} for menu {} not handled yet: {}", player.getName().getString(), containerId, action);
	}
}
