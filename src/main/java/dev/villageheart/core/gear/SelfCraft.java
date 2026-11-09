package dev.villageheart.core.gear;

import java.util.UUID;

/** A worker's saved self-craft (at most one per worker). {@code refundPending}: the materials still have to go back (bad data, or the refund waits for a chunk). */
public record SelfCraft(UUID worker, GearCraft craft, boolean refundPending) {
	public SelfCraft withCraft(GearCraft c) { return new SelfCraft(worker, c, refundPending); }

	public SelfCraft withRefundPending(boolean p) { return new SelfCraft(worker, craft, p); }
}
