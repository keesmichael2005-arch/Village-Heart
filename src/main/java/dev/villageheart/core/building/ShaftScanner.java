package dev.villageheart.core.building;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.BlockView;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomContents;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Surveys a Mineshaft (DESIGN section 3.3): a breadth-first fill from the marker cell over OPEN cells, going down.
 * The marker hangs on a post at the entrance; its own cell counts as open. Pure, over a {@link BlockView}.
 */
public final class ShaftScanner {
	private ShaftScanner() {}

	/**
	 * @param depth     marker y minus the lowest visited y
	 * @param deepestY  the lowest visited y
	 * @param notLoaded the first unloaded position the fill touched (the survey is then incomplete), else null
	 */
	public record ShaftSurvey(int depth, int deepestY, int rails, int lights, @Nullable Pos notLoaded) {}

	public static ShaftSurvey scan(BlockView v, Pos marker) {
		Set<Long> visited = new HashSet<>();
		Set<Long> litSolids = new HashSet<>();
		ArrayDeque<Pos> queue = new ArrayDeque<>();
		visited.add(marker.pack());
		queue.add(marker);
		int minY = marker.y(), rails = 0, lights = 0;
		boolean capped = false;
		while (!queue.isEmpty() && !capped) {
			Pos p = queue.poll();
			minY = Math.min(minY, p.y());
			if (!p.equals(marker)) {
				Cell here = v.cell(p);
				if (here.contents().contains("#rail")) rails++;
				if (here.contents().contains(RoomContents.LIGHT)) lights++;
			}
			for (Dir d : Dir.values()) {
				Pos n = p.offset(d);
				if (n.y() > marker.y()) continue;
				int dx = Math.abs(n.x() - marker.x()), dz = Math.abs(n.z() - marker.z());
				if (Math.max(dx, dz) > Balance.SHAFT_RADIUS) continue;
				if (n.y() >= marker.y() - 1 && Math.max(dx, dz) > 1) continue; // entrance rule: no surface spread
				if (visited.contains(n.pack())) continue;
				Cell c = v.cell(n);
				if (c.kind() == CellKind.UNLOADED) return new ShaftSurvey(marker.y() - minY, minY, rails, lights, n);
				if (c.kind() == CellKind.SOLID) {
					if (c.contents().contains(RoomContents.LIGHT) && litSolids.add(n.pack())) lights++;
					continue;
				}
				if (visited.size() >= Balance.SHAFT_MAX_AIR) {
					capped = true;
					break;
				}
				visited.add(n.pack());
				queue.add(n);
			}
		}
		// cells queued but never processed still count towards the depth reached
		for (Pos q : queue) minY = Math.min(minY, q.y());
		return new ShaftSurvey(marker.y() - minY, minY, rails, lights, null);
	}
}
