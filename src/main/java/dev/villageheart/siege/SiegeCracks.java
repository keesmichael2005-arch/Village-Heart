package dev.villageheart.siege;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.WallHp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Re-sends crack stages to a player who can see the wall (docs/plans/M25.md 3.9). */
public final class SiegeCracks {
	private SiegeCracks() {}

	public static void resend(MinecraftServer server, VillageState village) {
		var damage = SiegeDamage.damage(server);
		if (damage.cracked().isEmpty() || server.getPlayerList().getPlayers().isEmpty()) return;
		DefenceIndex index = DefenceIndex.of(village.defences());
		ServerLevel level = dev.villageheart.worker.Workers.levelOf(server, village.heart());
		if (level == null) return;
		for (var e : damage.cracked().entrySet()) {
			DefenceCellAt at = at(index, e.getKey());
			if (at == null) continue;
			if (!nearPlayer(level, at.pos())) continue;
			int stage = WallHp.stage(e.getValue(), WallHp.hp(at.kind()));
			level.destroyBlockProgress(SiegeDamage.crackId(village.heart(), level, at.pos()), at.pos(), stage);
		}
	}

	private static boolean nearPlayer(ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			if (p.level() != level) continue;
			double dx = p.getX() - x, dy = p.getY() - y, dz = p.getZ() - z;
			if (dx * dx + dy * dy + dz * dz <= 32.0 * 32.0) return true;
		}
		return false;
	}

	private static DefenceCellAt at(DefenceIndex index, long packed) {
		var cell = index.get(packed);
		if (cell == null) return null;
		Pos p = cell.pos();
		return new DefenceCellAt(new BlockPos(p.x(), p.y(), p.z()), cell.kind());
	}

	private record DefenceCellAt(BlockPos pos, dev.villageheart.core.defence.DefenceKind kind) {}
}
