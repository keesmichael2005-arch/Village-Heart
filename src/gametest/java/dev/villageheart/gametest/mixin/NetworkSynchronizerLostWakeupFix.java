package dev.villageheart.gametest.mixin;

import com.google.common.collect.ConcurrentHashMultiset;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

/**
 * Fixes a lost-wakeup race in Fabric's client-gametest {@code NetworkSynchronizer} (fabric-client-gametest-api-v1 4.3.5,
 * shipped with fabric-api 0.141.6+1.21.11, the newest for 1.21.11; later Fabric versions removed the class).
 *
 * <p>{@code waitForPacketHandlers} tests {@code inFlightPackets > 0 && mainThreadPacketHandlers.isEmpty()} without holding
 * {@code morePacketsLock}, then locks and calls {@code morePacketsCondition.await(10, SECONDS)} without testing again. The
 * Netty thread decrements the counter and signals outside that window. If the last in-flight packet is handled between
 * the test and the {@code await}, the signal is lost and the thread parks for the full 10 s; the synchronizer then marks
 * itself invalid ({@code AssertionError: Network synchronizer in invalid state}) and the server crashes. It bites at a
 * world start: the client's first login packet races the server's first tick, and the server thread is the only one that
 * could make progress, so nothing else wakes it. CPU load widens the window.
 *
 * <p>This keeps Fabric's semantics (wake on a signal; still fail after 10 s if a packet really never arrives) but tests the
 * predicate again under the lock before parking and parks in short slices, so a missed signal costs at most one slice.
 * Every rescue is logged with the marker {@code LOST_WAKEUP_RESCUED}.
 */
@Mixin(value = NetworkSynchronizer.class, remap = false)
abstract class NetworkSynchronizerLostWakeupFix {
	@Unique
	private static final Logger VH_LOGGER = LoggerFactory.getLogger("villageheart-gametest");
	@Unique
	private static final long VH_SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(20);

	static {
		// Merged into NetworkSynchronizer's static initializer: proves in the client log that the fix is installed.
		VH_LOGGER.info("LOST_WAKEUP_FIX installed in NetworkSynchronizer (re-check before await, 20 ms slices, 10 s limit kept)");
	}

	@Shadow
	@Final
	private AtomicInteger inFlightPackets;

	@Shadow
	@Final
	private ConcurrentHashMultiset<?> mainThreadPacketHandlers;

	@WrapOperation(method = "waitForPacketHandlers",
			at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/Condition;await(JLjava/util/concurrent/TimeUnit;)Z"))
	private boolean villageheart$awaitWithoutLostWakeup(Condition condition, long time, TimeUnit unit, Operation<Boolean> original) {
		long deadline = System.nanoTime() + unit.toNanos(time);
		int slices = 0;
		while (true) {
			// The caller's inner-loop predicate: true means a packet is still in flight and nothing is queued.
			if (!(inFlightPackets.get() > 0 && mainThreadPacketHandlers.isEmpty())) {
				VH_LOGGER.warn("LOST_WAKEUP_RESCUED: NetworkSynchronizer on '{}': the last in-flight packet was handled {} the "
						+ "await, so its signal was missed; the unpatched code would park here for up to 10 s and could fail the run",
						Thread.currentThread().getName(), slices == 0 ? "just before" : "during a timed-out slice of");
				return true;
			}
			long left = deadline - System.nanoTime();
			if (left <= 0) return false; // a packet really never arrived in 10 s: keep Fabric's failure
			if (original.call(condition, Math.min(left, VH_SLICE_NANOS), TimeUnit.NANOSECONDS)) return true; // signalled
			slices++;
		}
	}
}
