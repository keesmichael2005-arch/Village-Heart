package dev.villageheart.client.build;

import dev.villageheart.build.ModComponents;
import dev.villageheart.build.net.MirrorBlueprintPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.ItemStack;

/** Swap-hands mirrors a held blueprint instead of moving it to the other hand. Consumed before vanilla sees the click. */
@Environment(EnvType.CLIENT)
public final class BlueprintMirrorKey {
	private BlueprintMirrorKey() {}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.screen != null) return;
			ItemStack main = mc.player.getMainHandItem();
			ItemStack off = mc.player.getOffhandItem();
			boolean held = main.get(ModComponents.BLUEPRINT) != null || off.get(ModComponents.BLUEPRINT) != null;
			if (!held) return;
			while (mc.options.keySwapOffhand.consumeClick()) ClientPlayNetworking.send(new MirrorBlueprintPayload());
		});
	}
}
