package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.sim.SimArea;
import dev.villageheart.core.sim.SimArea.Chunk;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;

/**
 * The L3 stand-in for a player's simulation distance around each hired worker ({@link SimArea}): a ticket that loads and entity-ticks the
 * worker's chunk and {@link SimArea#FOLLOW_TICKING_CHUNKS} around it, moved every {@link SimArea#FOLLOW_INTERVAL_TICKS} ticks. Not saved with the
 * world (a resumed run starts with none and follows the workers again once they load).
 */
public final class SimTickets {
	/** Loading + simulation + keeps the dimension active, no timeout, not persisted (vanilla FORCED is all of these plus persist). */
	public static final TicketType WORKER = new TicketType(TicketType.NO_TIMEOUT,
			TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
	private static boolean registered;

	/** Called from the gametest mod's initializer, before the registries freeze. */
	static void register() {
		if (registered) return;
		Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("villageheart-gametest", "sim_worker"), WORKER);
		registered = true;
	}

	private final ServerLevel level;
	private final SimArea.Follower<UUID> follower = new SimArea.Follower<>();

	public SimTickets(ServerLevel level) { this.level = level; }

	/** Moves the tickets to the given chunks (one entry per followed entity); returns how many tickets changed. */
	public int follow(Map<UUID, Chunk> now) {
		SimArea.Changes c = follower.update(now);
		for (Chunk r : c.remove()) level.getChunkSource().removeTicketWithRadius(WORKER, new ChunkPos(r.x(), r.z()), SimArea.FOLLOW_TICKET_RADIUS);
		for (Chunk a : c.add()) level.getChunkSource().addTicketWithRadius(WORKER, new ChunkPos(a.x(), a.z()), SimArea.FOLLOW_TICKET_RADIUS);
		if (!c.isEmpty()) VillageHeart.LOG.debug("[sim] worker tickets -{} +{}", c.remove(), c.add());
		return c.remove().size() + c.add().size();
	}

	/** Removes every ticket this follower placed. */
	public void releaseAll() {
		for (Chunk h : follower.held()) level.getChunkSource().removeTicketWithRadius(WORKER, new ChunkPos(h.x(), h.z()), SimArea.FOLLOW_TICKET_RADIUS);
		follower.clear();
	}

	public static Chunk chunkOf(net.minecraft.core.BlockPos p) { return Chunk.ofBlock(p.getX(), p.getZ()); }
}
