package dev.villageheart.core.defence;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The defence cells of a recorded site (docs/plans/M25.md 3.9): cobblestone is {@link DefenceKind#STONE}, a fence gate is
 * {@link DefenceKind#GATE}. Everything else in the blueprint (planks, fences, ladders, lanterns, air) is not a defence cell.
 */
public final class DefenceCells {
	private DefenceCells() {}

	/** A defence cell in the world, with the blueprint state string it was generated from. */
	public record Placed(Pos pos, DefenceKind kind, String expectedState) {}

	public static List<Placed> of(DefenceSite site) {
		Optional<Blueprint> bp = Blueprints.resolve(site.blueprintId(), new BlueprintParams(Math.max(0, site.length())));
		if (bp.isEmpty()) return List.of();
		Blueprint b = bp.get();
		Placement p = new Placement(new Pos(site.ground().x(), site.ground().y(), site.ground().z()), site.rotation());
		List<Placed> out = new ArrayList<>();
		for (int y = 0; y < b.height(); y++) for (int z = 0; z < b.depth(); z++) for (int x = 0; x < b.width(); x++) {
			PaletteEntry e = b.entry(x, y, z);
			DefenceKind kind = kindOf(e);
			if (kind == null) continue;
			out.add(new Placed(p.world(b, x, y, z), kind, e.state()));
		}
		return List.copyOf(out);
	}

	/** Cobblestone, or a fence-gate palette entry. Null for every other cell. */
	public static DefenceKind kindOf(PaletteEntry e) {
		if (e == null || !e.isBlock()) return null;
		if ("minecraft:cobblestone".equals(e.blockId())) return DefenceKind.STONE;
		if (e.material() != null && "#minecraft:fence_gates".equals(e.material().id())) return DefenceKind.GATE;
		if (e.blockId().endsWith("_fence_gate")) return DefenceKind.GATE;
		return null;
	}

	public static int count(List<Placed> cells, DefenceKind kind) {
		int n = 0;
		for (Placed c : cells) if (c.kind() == kind) n++;
		return n;
	}

	/** Packed offset from the site's ground block. The save stores these, not absolute cells. */
	public static long relative(HeartLocation ground, Pos world) {
		return Pos.pack(world.x() - ground.x(), world.y() - ground.y(), world.z() - ground.z());
	}

	public static Pos absolute(HeartLocation ground, long relative) {
		Pos d = Pos.unpack(relative);
		return new Pos(ground.x() + d.x(), ground.y() + d.y(), ground.z() + d.z());
	}
}
