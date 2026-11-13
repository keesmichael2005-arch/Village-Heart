package dev.villageheart.client;

import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.heart.ModMenus;
import dev.villageheart.heart.net.HeartActionResultPayload;
import dev.villageheart.heart.net.HeartSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class VillageHeartClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.HEART, HeartScreen::new);
		MenuScreens.register(ModMenus.POCKET, dev.villageheart.client.pocket.PocketScreen::new);
		dev.villageheart.client.build.BlueprintPreview.register();
		MenuScreens.register(ModMenus.BUILDERS_TABLE, dev.villageheart.client.build.BuildersTableScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(dev.villageheart.build.net.BuildersTableSnapshotPayload.TYPE, (payload, ctx) -> {
			if (ctx.player().containerMenu instanceof dev.villageheart.build.BuildersTableMenu m && m.containerId == payload.containerId()) {
				m.accept(payload.snapshot());
			}
		});

		// Anything for a screen that is no longer open (a late packet, another menu) is dropped.
		ClientPlayNetworking.registerGlobalReceiver(HeartSnapshotPayload.TYPE, (payload, ctx) -> {
			if (ctx.player().containerMenu instanceof HeartMenu m && m.containerId == payload.containerId()) {
				m.accept(payload.snapshot());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(HeartActionResultPayload.TYPE, (payload, ctx) -> {
			if (ctx.player().containerMenu instanceof HeartMenu m && m.containerId == payload.containerId()
					&& ctx.client().screen instanceof HeartScreen screen) {
				screen.showResult(payload);
			}
			if (ctx.player().containerMenu instanceof dev.villageheart.build.BuildersTableMenu m && m.containerId == payload.containerId()
					&& ctx.client().screen instanceof dev.villageheart.client.build.BuildersTableScreen screen) {
				screen.showResult(payload);
			}
		});
	}
}
