package dev.villageheart.client.build;

import dev.villageheart.build.BlueprintSite;
import dev.villageheart.build.ModComponents;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.PlacementMode;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.room.Pos;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;

/**
 * The footprint of a Blueprint item (plan 3.7): while the player holds one that shows a site, every 4 ticks a dust particle on every block
 * along the outline of the box at its bottom layer and on the 4 vertical corner edges (at most 120): green when the server said the site is
 * fine, red otherwise. The geometry is core's {@link Placement}, the same box the server checks.
 */
@Environment(EnvType.CLIENT)
public final class BlueprintPreview {
	private BlueprintPreview() {}

	public static final int OK_COLOR = 0x3FBF3F, BAD_COLOR = 0xD03030;
	private static final int MAX_PARTICLES = 120;
	private static final double STEP = 0.5;

	/** Test hooks: what the last tick drew. */
	public static int lastCount;
	public static int lastColor;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BlueprintPreview::tick);
	}

	/** The site shown by the item in a hand, or null. */
	public static BlueprintSite siteOf(ItemStack stack) {
		return stack.get(ModComponents.BLUEPRINT_SITE);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 4 != 0) return;
		ItemStack held = mc.player.getMainHandItem();
		if (siteOf(held) == null || held.get(ModComponents.BLUEPRINT) == null) held = mc.player.getOffhandItem();
		BlueprintSite site = siteOf(held);
		String id = held.get(ModComponents.BLUEPRINT);
		if (site == null || id == null) {
			lastCount = 0;
			return;
		}
		Blueprint unit = Blueprints.byId(id).orElse(null);
		if (unit == null) return;
		if (unit.mode() == PlacementMode.LINE && site.length() == 0) {
			startColumn(mc, site);
			return;
		}
		Blueprint bp = Blueprints.resolve(id, new BlueprintParams(site.length())).orElse(null);
		if (bp == null) return;
		Placement pl = new Placement(new Pos(site.ground().getX(), site.ground().getY(), site.ground().getZ()), site.rotationEnum());
		Pos min = pl.min(bp), max = pl.max(bp);
		DustParticleOptions dust = new DustParticleOptions(site.ok() ? OK_COLOR : BAD_COLOR, 1.0F);
		lastColor = site.ok() ? OK_COLOR : BAD_COLOR;
		int n = 0;
		double y0 = min.y() + 0.05, y1 = max.y() + 1.0;
		double x0 = min.x(), x1 = max.x() + 1, z0 = min.z(), z1 = max.z() + 1;
		// a long wall: fewer particles per block, so the whole outline fits under the cap
		double step = Math.max(STEP, 2 * ((x1 - x0) + (z1 - z0)) / 90.0);
		// the outline at the bottom layer: a particle every half block
		for (double x = x0; x <= x1 && n < MAX_PARTICLES; x += step) {
			mc.level.addParticle(dust, x, y0, z0, 0, 0, 0);
			mc.level.addParticle(dust, x, y0, z1, 0, 0, 0);
			n += 2;
		}
		for (double z = z0 + step; z < z1 && n < MAX_PARTICLES; z += step) {
			mc.level.addParticle(dust, x0, y0, z, 0, 0, 0);
			mc.level.addParticle(dust, x1, y0, z, 0, 0, 0);
			n += 2;
		}
		// the four vertical edges
		for (double y = y0 + STEP; y <= y1 && n < MAX_PARTICLES; y += STEP) {
			mc.level.addParticle(dust, x0, y, z0, 0, 0, 0);
			mc.level.addParticle(dust, x1, y, z0, 0, 0, 0);
			mc.level.addParticle(dust, x0, y, z1, 0, 0, 0);
			mc.level.addParticle(dust, x1, y, z1, 0, 0, 0);
			n += 4;
		}
		lastCount = n;
	}

	/** Only the start of a line is set: a three-high column of dust on that block (at most 24 particles). */
	private static void startColumn(Minecraft mc, BlueprintSite site) {
		DustParticleOptions dust = new DustParticleOptions(site.ok() ? OK_COLOR : BAD_COLOR, 1.0F);
		lastColor = site.ok() ? OK_COLOR : BAD_COLOR;
		int n = 0;
		double x0 = site.ground().getX(), z0 = site.ground().getZ(), y0 = site.ground().getY() + 1.05;
		for (double y = y0; y <= y0 + 3 - 0.05; y += 0.5) {
			for (double[] c : new double[][] {{x0, z0}, {x0 + 1, z0}, {x0, z0 + 1}, {x0 + 1, z0 + 1}}) {
				mc.level.addParticle(dust, c[0], y, c[1], 0, 0, 0);
				n++;
			}
		}
		lastCount = n;
	}
}
