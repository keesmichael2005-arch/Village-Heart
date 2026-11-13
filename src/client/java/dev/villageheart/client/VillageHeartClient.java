package dev.villageheart.client;

import dev.villageheart.client.guide.GuideHud;
import dev.villageheart.client.heart.HeartScreen;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.heart.ModMenus;
import dev.villageheart.core.guide.GuideTask;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.heart.net.GuideToastPayload;
import dev.villageheart.heart.net.HeartActionResultPayload;
import dev.villageheart.heart.net.HeartSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class VillageHeartClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.HEART, HeartScreen::new);
		GuideHud.register();
		dev.villageheart.client.build.BlueprintPreview.register();
		dev.villageheart.client.build.SchematicOverlay.register();
		dev.villageheart.build.BlueprintBookHooks.openScreen = hand ->
				net.minecraft.client.Minecraft.getInstance().setScreen(new dev.villageheart.client.build.BlueprintBookScreen(hand));
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
		ClientPlayNetworking.registerGlobalReceiver(GuideToastPayload.TYPE, (payload, ctx) -> {
			GuideTask task = GuideTask.byId(payload.taskId()).orElse(null);
			if (task == null) return;
			Component title = Component.translatable(HeartTexts.key("guide.task." + task.id()));
			Component body = Component.translatable(HeartTexts.key("guide.checked"));
			var toastId = SystemToast.SystemToastId.PERIODIC_NOTIFICATION;
			ctx.client().getToastManager().addToast(SystemToast.multiline(ctx.client(), toastId, title, body));
			if (ctx.player() != null) ctx.player().playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
		});
		ClientPlayNetworking.registerGlobalReceiver(dev.villageheart.heart.net.GuideTasksPayload.TYPE, (payload, ctx) ->
				GuideHud.setTasks(payload.taskIds()));
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
