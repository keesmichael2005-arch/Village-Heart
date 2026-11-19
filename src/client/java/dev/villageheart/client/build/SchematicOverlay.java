package dev.villageheart.client.build;

import com.mojang.blaze3d.platform.InputConstants;
import dev.villageheart.build.net.RequestSchematicsPayload;
import dev.villageheart.build.net.SchematicSyncPayload;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.PlacementMode;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * V toggles outlines of plans that are placed and not finished. The colour is blue, so it stays apart from the green and red of a site
 * you are still choosing.
 */
@Environment(EnvType.CLIENT)
public final class SchematicOverlay {
	public static final int COLOR = 0x4C7FBF;
	private static final int MAX_PARTICLES = 480;

	private static KeyMapping toggle;
	private static boolean shown;
	private static List<SchematicSyncPayload.Site> sites = List.of();
	/** Test hook: particles drawn on the last tick. */
	public static int lastCount;

	private SchematicOverlay() {}

	public static boolean shown() { return shown; }

	public static void accept(SchematicSyncPayload payload) { sites = payload.sites(); }

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("villageheart", "villageheart"));
		toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.villageheart.schematics", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
		ClientPlayNetworking.registerGlobalReceiver(SchematicSyncPayload.TYPE, (payload, ctx) -> accept(payload));
		ClientTickEvents.END_CLIENT_TICK.register(SchematicOverlay::tick);
	}

	private static void tick(Minecraft mc) {
		if (toggle != null) {
			while (toggle.consumeClick()) {
				shown = !shown;
				if (shown && mc.getConnection() != null) ClientPlayNetworking.send(new RequestSchematicsPayload());
			}
		}
		if (mc.level == null || mc.player == null || mc.isPaused()) return;
		if (!shown) {
			lastCount = 0;
			return;
		}
		if (mc.getConnection() != null && mc.level.getGameTime() % 40 == 0) ClientPlayNetworking.send(new RequestSchematicsPayload());
		if (mc.level.getGameTime() % 4 != 0) return;
		int budget = MAX_PARTICLES;
		int drawn = 0;
		DustParticleOptions dust = new DustParticleOptions(COLOR, 1.0F);
		for (SchematicSyncPayload.Site site : sites) {
			if (budget <= 0) break;
			Blueprint bp = Blueprints.resolve(site.blueprintId(), new BlueprintParams(Math.max(site.length(), 1))).orElse(null);
			if (bp == null) continue;
			if (bp.mode() == PlacementMode.LINE && site.length() < 1) continue;
			int rotation = Math.floorMod(site.rotation(), Rotation4.values().length);
			Placement pl = new Placement(new Pos(site.x(), site.y(), site.z()), Rotation4.values()[rotation]);
			Pos min = pl.min(bp), max = pl.max(bp);
			drawn += dustBox(mc, dust, min, max, budget);
			budget = MAX_PARTICLES - drawn;
		}
		lastCount = drawn;
	}

	private static int dustBox(Minecraft mc, DustParticleOptions dust, Pos min, Pos max, int budget) {
		double y0 = min.y() + 0.05, y1 = max.y() + 1.0;
		double x0 = min.x(), x1 = max.x() + 1, z0 = min.z(), z1 = max.z() + 1;
		double step = Math.max(1.0, 2 * ((x1 - x0) + (z1 - z0)) / 24.0);
		int n = 0;
		for (double x = x0; x <= x1 && n < budget; x += step) {
			mc.level.addParticle(dust, x, y0, z0, 0, 0, 0);
			mc.level.addParticle(dust, x, y0, z1, 0, 0, 0);
			n += 2;
		}
		for (double z = z0 + step; z < z1 && n < budget; z += step) {
			mc.level.addParticle(dust, x0, y0, z, 0, 0, 0);
			mc.level.addParticle(dust, x1, y0, z, 0, 0, 0);
			n += 2;
		}
		for (double y = y0 + step; y <= y1 && n < budget; y += step) {
			mc.level.addParticle(dust, x0, y, z0, 0, 0, 0);
			mc.level.addParticle(dust, x1, y, z0, 0, 0, 0);
			mc.level.addParticle(dust, x0, y, z1, 0, 0, 0);
			mc.level.addParticle(dust, x1, y, z1, 0, 0, 0);
			n += 4;
		}
		return n;
	}
}
