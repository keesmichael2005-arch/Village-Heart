package dev.villageheart.worker;

import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.VillagerNames;
import dev.villageheart.core.job.Leveling;
import dev.villageheart.core.job.XpChange;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * What a level-up looks and sounds like (M15): happy-villager particles, the celebrate sound, a chat line to every online member of rank
 * Resident or above (Visitors are not told about the village's staff), and a notice when a worker has the XP for its next level but the
 * village tier holds it back. At most {@link #MAX_LINES_PER_TICK} lines per server tick; the rest of the level-ups of that tick become one
 * "and N more workers levelled up" line at the end of the tick, so a tier-up with many workers does not flood the chat.
 */
public final class LevelUpFeedback implements WorkerLevels.LevelListener {
	public static final LevelUpFeedback INSTANCE = new LevelUpFeedback();
	public static final int MAX_LINES_PER_TICK = 3;

	public enum Kind { LEVEL_UP, CAPPED }

	/** What the test seam records (particles and sound cannot be observed server-side in a GameTest). */
	public record FeedbackEvent(UUID villager, int from, int to, Kind kind) {}

	private static Consumer<FeedbackEvent> sink;
	private static int linesThisTick;
	private static int overflow;
	private static long tick = Long.MIN_VALUE;

	private LevelUpFeedback() {}

	public static void register() {
		WorkerLevels.register(INSTANCE);
		ServerTickEvents.END_SERVER_TICK.register(LevelUpFeedback::flush);
	}

	/** Test seam: every event is passed here before the effects run. Pass null to remove. */
	public static void setSinkForTest(Consumer<FeedbackEvent> s) { sink = s; }

	/** Test hook: forgets the rate limiter's state. */
	public static void resetForTests() {
		sink = null;
		linesThisTick = 0;
		overflow = 0;
		tick = Long.MIN_VALUE;
	}

	@Override
	public void onLevelChanged(ServerLevel level, Villager villager, XpChange change) {
		if (sink != null) sink.accept(new FeedbackEvent(villager.getUUID(), change.before().level(), change.after().level(), Kind.LEVEL_UP));
		MinecraftServer server = level.getServer();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
		if (!claim(server)) {
			overflow++;
			return;
		}
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		WorkerData data = villager.getAttached(WorkerAttachments.WORKER);
		Component job = Component.literal(data == null ? "" : data.job().displayName());
		tell(server, Component.translatable("message.villageheart.worker_level_up", nameOf(villager), job, change.after().level()));
	}

	@Override
	public void onCapped(ServerLevel level, Villager villager, XpChange change) {
		if (sink != null) sink.accept(new FeedbackEvent(villager.getUUID(), change.after().level(), change.after().level() + 1, Kind.CAPPED));
		MinecraftServer server = level.getServer();
		Optional<dev.villageheart.core.Tier> needed = Leveling.tierForNext(change.after());
		if (needed.isEmpty() || !claim(server)) return;
		tell(server, Component.translatable("message.villageheart.worker_level_capped", nameOf(villager), change.after().level() + 1,
				needed.get().displayName()));
	}

	/** True while this server tick still has a chat line to spend. */
	private static boolean claim(MinecraftServer server) {
		long now = server.getTickCount();
		if (now != tick) {
			flush(server);
			tick = now;
			linesThisTick = 0;
		}
		if (linesThisTick >= MAX_LINES_PER_TICK) return false;
		linesThisTick++;
		return true;
	}

	/** End of tick: one summary line for the level-ups that did not get their own. */
	private static void flush(MinecraftServer server) {
		if (overflow > 0) {
			int n = overflow;
			overflow = 0;
			tell(server, Component.translatable("message.villageheart.workers_level_up_more", n));
		}
		linesThisTick = 0;
		tick = Long.MIN_VALUE;
	}

	private static String nameOf(Villager v) {
		return v.hasCustomName() ? v.getCustomName().getString() : VillagerNames.of(v.getUUID());
	}

	private static void tell(MinecraftServer server, Component line) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (village.get().rankOf(p.getUUID()).atLeast(Rank.RESIDENT)) p.sendSystemMessage(line);
		}
	}
}
