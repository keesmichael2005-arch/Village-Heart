package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which chunks the L3 simulation keeps ticking, standing in for a real player's presence (docs/TESTING.md, "Layer 3", "Ticking area").
 * <ul>
 *   <li>A <b>static</b> square around the Heart's chunk that covers the whole claim plus one chunk ({@link #staticRadiusChunks}), force-loaded.</li>
 *   <li>A <b>follow</b> ticket around every hired worker ({@link Follower}): like a player's simulation distance, the worker's own chunk and
 *       {@link #FOLLOW_TICKING_CHUNKS} chunks around it always tick entities, so a worker that strolls or flees past the static square keeps
 *       its brain and can walk home. How far it went is a metric ({@code work.workers.max_heart_distance}), not a freeze.</li>
 * </ul>
 * Pure: chunk coordinates only, the Minecraft glue is {@code dev.villageheart.sim.SimTickets}.
 */
public final class SimArea {
	private SimArea() {}

	/** Chunks (Chebyshev) around a followed worker that tick entities. 2 = 32+ blocks: more than a panicking villager runs between two updates. */
	public static final int FOLLOW_TICKING_CHUNKS = 2;
	/** Ticket radius for {@code addTicketWithRadius}: level 33 - radius, entity-ticking at level <= 31, so 2 more than the ticking radius. */
	public static final int FOLLOW_TICKET_RADIUS = FOLLOW_TICKING_CHUNKS + 2;
	/** The follow tickets move this often (ticks). */
	public static final int FOLLOW_INTERVAL_TICKS = 20;

	public record Chunk(int x, int z) {
		public static Chunk ofBlock(int blockX, int blockZ) { return new Chunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16)); }
	}

	/** Chebyshev radius in chunks of a square around the Heart's chunk that holds every block within {@code claimRadiusBlocks}, plus one chunk. */
	public static int staticRadiusChunks(int claimRadiusBlocks) {
		if (claimRadiusBlocks < 0) throw new IllegalArgumentException("claim radius " + claimRadiusBlocks);
		// the Heart can stand anywhere in its chunk: the far side of the claim is at most 15 + r blocks from the chunk's first block
		return Math.floorDiv(15 + claimRadiusBlocks, 16) + 1;
	}

	/** The (2r+1)^2 chunks around {@code centre}, row by row. */
	public static Set<Chunk> square(Chunk centre, int radius) {
		Set<Chunk> out = new LinkedHashSet<>();
		for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) out.add(new Chunk(centre.x() + dx, centre.z() + dz));
		return out;
	}

	/** Ticket changes to apply: remove first, then add (a chunk may be in both when two workers swap chunks; the counts below keep that right). */
	public record Changes(List<Chunk> remove, List<Chunk> add) {
		public boolean isEmpty() { return remove.isEmpty() && add.isEmpty(); }
	}

	/**
	 * One ticket per distinct chunk that holds at least one followed entity. Tickets of the same type and level at the same chunk are not
	 * counted by the game, so two workers in one chunk share one ticket here and it goes only when the last of them leaves.
	 */
	public static final class Follower<K> {
		private final Map<K, Chunk> at = new HashMap<>();
		private final Map<Chunk, Integer> refs = new HashMap<>();

		/** @param now the chunk of every entity to follow now; an entity that is missing (fired, dead, unloaded) stops being followed. */
		public Changes update(Map<K, Chunk> now) {
			List<Chunk> remove = new ArrayList<>(), add = new ArrayList<>();
			for (var it = at.entrySet().iterator(); it.hasNext(); ) {
				var e = it.next();
				Chunk next = now.get(e.getKey());
				if (Objects.equals(next, e.getValue())) continue;
				if (release(e.getValue())) remove.add(e.getValue());
				if (next == null) it.remove();
				else {
					e.setValue(next);
					if (hold(next)) add.add(next);
				}
			}
			for (var e : now.entrySet()) {
				if (at.containsKey(e.getKey())) continue;
				at.put(e.getKey(), e.getValue());
				if (hold(e.getValue())) add.add(e.getValue());
			}
			// a chunk released by one entity and taken by another in the same update needs no change at all
			List<Chunk> both = new ArrayList<>(remove);
			both.retainAll(add);
			remove.removeAll(both);
			add.removeAll(both);
			return new Changes(remove, add);
		}

		/** Every chunk with a ticket now. */
		public Set<Chunk> held() { return Set.copyOf(refs.keySet()); }

		/** Drops everything (the caller removes the {@link #held()} tickets). */
		public void clear() {
			at.clear();
			refs.clear();
		}

		private boolean hold(Chunk c) { return refs.merge(c, 1, Integer::sum) == 1; }

		private boolean release(Chunk c) {
			int n = refs.merge(c, -1, Integer::sum);
			if (n > 0) return false;
			refs.remove(c);
			return true;
		}
	}
}
