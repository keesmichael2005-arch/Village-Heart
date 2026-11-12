package dev.villageheart.core.smith;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.gear.GearCraft;
import java.util.Objects;

/**
 * A smith's saved craft: M19a's {@link GearCraft} plus the chest it is going to. {@code refundPending} means the committed
 * items still have to go back (a bad save, or the refund is waiting for a loaded chunk).
 */
public record SmithCraft(GearCraft craft, HeartLocation destination, boolean refundPending) {
	public SmithCraft {
		Objects.requireNonNull(craft, "craft");
		Objects.requireNonNull(destination, "destination");
	}

	public SmithCraft(GearCraft craft, HeartLocation destination) { this(craft, destination, false); }

	public SmithCraft withProgress(int p) { return new SmithCraft(craft.withProgress(p), destination, refundPending); }

	public SmithCraft withRefundPending(boolean p) { return new SmithCraft(craft, destination, p); }
}
