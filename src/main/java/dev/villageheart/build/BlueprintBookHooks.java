package dev.villageheart.build;

import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;

/** Set by the client. The server leaves it empty. */
public final class BlueprintBookHooks {
	public static Consumer<InteractionHand> openScreen = hand -> {};

	private BlueprintBookHooks() {}
}
