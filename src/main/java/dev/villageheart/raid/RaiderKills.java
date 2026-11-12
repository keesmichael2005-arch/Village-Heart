package dev.villageheart.raid;

import dev.villageheart.core.raid.RaidTracker;
import dev.villageheart.world.VillageClaims;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/** A raider of the tracked raid who actually dies counts. A totem fires the allow and then the mob lives, so the note expires. */
public final class RaiderKills {
	private static final Map<UUID, Note> NOTES = new HashMap<>();

	private record Note(int raidId, int tick, boolean extra) {}

	private RaiderKills() {}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			note(entity);
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> confirm(entity));
	}

	/** The mod's added raiders (step 16) pass true. A vanilla raider is false. */
	public static void markExtra(UUID id) {
		Note n = NOTES.get(id);
		if (n != null) NOTES.put(id, new Note(n.raidId, n.tick, true));
	}

	public static void clearForTests() { NOTES.clear(); }

	private static void note(LivingEntity entity) {
		if (!(entity instanceof Raider raider) || !(entity.level() instanceof ServerLevel level)) return;
		sweep(level.getServer());
		Raid raid = raider.getCurrentRaid();
		if (raid == null) return;
		var id = level.getRaids().getId(raid);
		if (id.isEmpty()) return;
		var state = VillageClaims.current(level.getServer());
		if (state.isEmpty() || state.get().threats().raid().isEmpty()) return;
		if (state.get().threats().raid().get().vanillaId() != id.getAsInt()) return;
		boolean extra = raider.getTags().contains(ThreatScheduler.EXTRA_TAG);
		NOTES.put(raider.getUUID(), new Note(id.getAsInt(), level.getServer().getTickCount(), extra));
	}

	private static void confirm(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) return;
		sweep(level.getServer());
		Note n = NOTES.remove(entity.getUUID());
		if (n == null) return;
		MinecraftServer server = level.getServer();
		var state = VillageClaims.current(server);
		if (state.isEmpty() || state.get().threats().raid().isEmpty()) return;
		if (state.get().threats().raid().get().vanillaId() != n.raidId) return;
		boolean extra = n.extra;
		VillageClaims.data(server).update(s -> {
			var next = RaidTracker.onRaiderKilled(s.threats().raid(), extra);
			return next.isEmpty() ? s : s.withRaid(next);
		});
	}

	private static void sweep(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Note> it = NOTES.values().iterator();
		while (it.hasNext()) if (now - it.next().tick > 2) it.remove();
	}
}
