package dev.villageheart.gametest;

import dev.villageheart.client.build.BlueprintBookScreen;
import dev.villageheart.core.blueprint.BlueprintCatalog;
import dev.villageheart.core.blueprint.BlueprintGroup;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.InteractionHand;

/** The Blueprint Book screen: groups on the left, tiles in the middle, in the Heart's colours. */
final class BlueprintBookClientTests {
	private BlueprintBookClientTests() {}

	static void book(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			context.setScreen(() -> new BlueprintBookScreen(InteractionHand.MAIN_HAND));
			context.waitTicks(10);
			int buildings = context.computeOnClient(mc -> mc.screen instanceof BlueprintBookScreen s ? s.visibleCount() : -1);
			int want = BlueprintCatalog.in(BlueprintGroup.BUILDINGS).size();
			if (buildings != want) throw new AssertionError("the Buildings group shows " + buildings + ", expected " + want);
			context.takeScreenshot("blueprint_book");
			context.computeOnClient(mc -> {
				if (mc.screen instanceof BlueprintBookScreen s) s.show(BlueprintGroup.DEFENCE);
				return true;
			});
			context.waitTicks(5);
			int defence = context.computeOnClient(mc -> mc.screen instanceof BlueprintBookScreen s ? s.visibleCount() : -1);
			if (defence != BlueprintCatalog.in(BlueprintGroup.DEFENCE).size()) {
				throw new AssertionError("Defence shows " + defence);
			}
			context.takeScreenshot("blueprint_book_defence");
			context.setScreen(() -> null);
		}
	}
}
