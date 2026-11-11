package dev.villageheart.gametest.mixin;

import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.impl.client.gametest.context.ClientGameTestContextImpl;
import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clears vanilla toasts before every client-test screenshot (fabric-client-gametest-api-v1 4.3.5).
 * A "New Recipe(s) Unlocked" toast otherwise sits on the Stockpile and Ranks tabs for the whole shot.
 * The clear runs on the client thread, before {@code takeScreenshot} renders the frame.
 */
@Mixin(ClientGameTestContextImpl.class)
abstract class ScreenshotToastClear {
	private static final Logger VH_LOGGER = LoggerFactory.getLogger("villageheart-gametest");

	static {
		VH_LOGGER.info("SCREENSHOT_TOAST_CLEAR installed");
	}

	@Inject(method = "takeScreenshot", at = @At("HEAD"))
	private void villageheart$clearToasts(TestScreenshotOptions options, CallbackInfoReturnable<Path> cir) {
		ThreadingImpl.runOnClient(() -> Minecraft.getInstance().getToastManager().clear());
	}
}
