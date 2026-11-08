package dev.villageheart.gametest.mixin;

import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to Fabric's in-flight packet counter, for {@link DisconnectInFlightLeakFix}. */
@Mixin(value = NetworkSynchronizer.class, remap = false)
public interface NetworkSynchronizerAccessor {
	@Accessor("inFlightPackets")
	AtomicInteger villageheart$inFlightPackets();
}
