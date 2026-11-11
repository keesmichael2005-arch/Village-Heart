package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.YARD;

import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceCells;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.defence.Defences;
import dev.villageheart.core.siege.SiegeHits;
import dev.villageheart.gametest.BuildTestSupport.With;
import dev.villageheart.siege.SiegeDamage;
import dev.villageheart.siege.SiegeGamerules;
import dev.villageheart.world.VillageClaims;
import io.netty.channel.ChannelFutureListener;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Layer 2 siege tests (docs/plans/M25.md 5.2). One environment per test, the yard, a layout first, {@code VillageTestSupport.done} last.
 * Walls are built with the real pipeline.
 */
public class SiegeGameTests {
	private static BlockPos rel(int x, int y, int z) { return new BlockPos(x, y, z); }

	/** T-L2-13. A wall of 10 and a gate of 5 are recorded, and the index counts their defence cells. */
	@GameTest(environment = "villageheart-gametest:siege_wall_is_recorded", structure = YARD, skyAccess = true, maxTicks = 600)
	public void wallIsRecorded(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_FENCE_GATE, 3));
			DefenceTestSupport.startLine(h, "wall_segment", rel(2, -1, 18), rel(11, -1, 18));
			DefenceTestSupport.startLine(h, "gate", rel(14, -1, 18), rel(18, -1, 18));
			h.assertTrue(BuildTestSupport.builds(h).size() == 2, "wall and gate started");
			DefenceTestSupport.advanceAll(h);
			var village = VillageClaims.current(h.getLevel().getServer()).orElseThrow();
			DefenceSite wall = null, gate = null;
			for (DefenceSite s : village.defences()) {
				if (s.blueprintId().equals("wall_segment")) wall = s;
				if (s.blueprintId().equals("gate")) gate = s;
			}
			h.assertTrue(wall != null && wall.length() == 10, "wall length 10: " + village.defences());
			h.assertTrue(gate != null && gate.length() == 5, "gate length 5: " + village.defences());
			h.assertTrue(Defences.patrolPoints(village.defences(), p -> true).isEmpty(), "a wall and a gate have no patrol point");
			DefenceIndex index = DefenceIndex.of(village.defences());
			int stone = DefenceCells.count(index.cells().stream().map(c -> new DefenceCells.Placed(c.pos(), c.kind(), c.expectedState())).toList(), DefenceKind.STONE);
			int gates = DefenceCells.count(index.cells().stream().map(c -> new DefenceCells.Placed(c.pos(), c.kind(), c.expectedState())).toList(), DefenceKind.GATE);
			h.assertTrue(index.size() == 42 && stone == 39 && gates == 3, "index " + index.size() + " stone " + stone + " gate " + gates);
			var rules = h.getLevel().getGameRules();
			h.assertTrue(rules.get(SiegeGamerules.SIEGE_DAMAGE) && rules.get(SiegeGamerules.RANDOM_RAIDS) && rules.get(SiegeGamerules.SIEGES),
					"the three siege gamerules default on");
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-14. Eleven zombie hits crack a recorded cell to stage 9. The twelfth opens it, with no drop, and the breach is saved at once. */
	@GameTest(environment = "villageheart-gametest:siege_damage_hits", structure = YARD, skyAccess = true, maxTicks = 600)
	public void siegeDamageHitsCracksAndBreaks(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 64));
			DefenceTestSupport.startLine(h, "wall_segment", rel(2, -1, 18), rel(11, -1, 18));
			DefenceTestSupport.advanceAll(h);
			var server = h.getLevel().getServer();
			var village = VillageClaims.current(server).orElseThrow();
			DefenceCell cell = DefenceIndex.of(village.defences()).cells().stream().filter(c -> c.kind() == DefenceKind.STONE).findFirst().orElseThrow();
			BlockPos pos = new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z());
			BlockPos relPos = VillageTestSupport.rel(h, pos);
			Capture capture = new Capture();
			ServerPlayer player = VillageTestSupport.player(h, "siege", GameType.SURVIVAL, (s, level, profile) ->
					new ServerPlayer(s, level, profile, net.minecraft.server.level.ClientInformation.createDefault()), capture);
			player.setPos(pos.getX() + 5.5, pos.getY(), pos.getZ() + 0.5);
			for (int i = 0; i < 11; i++) SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE);
			h.assertTrue(h.getBlockState(relPos).is(net.minecraft.world.level.block.Blocks.COBBLESTONE), "eleven hits leave the block: " + h.getBlockState(relPos));
			h.assertTrue(SiegeDamage.damage(server).damageAt(cell.packed()) == 11, "damage 11, got " + SiegeDamage.damage(server).damageAt(cell.packed()));
			h.assertTrue(capture.packets.stream().anyMatch(p -> p.getPos().equals(pos) && p.getProgress() == 9), "stage 9 was sent: " + capture.packets.size());
			h.assertTrue(capture.packets.stream().anyMatch(p -> p.getId() < 0), "crack ids are negative");
			SiegeDamage.flush(server);
			try {
				var saved = VillageTestSupport.reload(server);
				h.assertTrue(saved.village().orElseThrow().threats().damage().damageAt(cell.packed()) == 11, "flushed damage reloads");
				h.assertTrue(!saved.village().orElseThrow().threats().damage().isBreached(cell.packed()), "a crack is not a breach");
				SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE);
				h.assertTrue(h.getBlockState(relPos).isAir(), "the twelfth hit breaks it");
				for (ItemEntity drop : h.getEntities(EntityType.ITEM, relPos, 4)) h.assertTrue(!drop.getItem().is(Items.COBBLESTONE), "no cobblestone drop");
				h.assertTrue(SiegeDamage.damage(server).isBreached(cell.packed()), "breached");
				h.assertTrue(capture.packets.stream().anyMatch(p -> p.getPos().equals(pos) && p.getProgress() == -1), "stage -1 was sent");
				var breached = VillageTestSupport.reload(server);
				h.assertTrue(breached.village().orElseThrow().threats().damage().isBreached(cell.packed()), "a breach is in the save without waiting for the flush");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			var before = SiegeDamage.damage(server);
			SiegeDamage.hit(server, h.getLevel(), pos, SiegeHits.Attacker.ZOMBIE);
			h.assertTrue(SiegeDamage.damage(server).equals(before), "a hit on a breached cell does nothing");
			VillageTestSupport.done(h);
		});
	}

	private static final class Capture extends Connection {
		final List<ClientboundBlockDestructionPacket> packets = new ArrayList<>();

		Capture() { super(PacketFlow.SERVERBOUND); }

		@Override
		public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
			if (packet instanceof ClientboundBlockDestructionPacket destruction) packets.add(destruction);
			super.send(packet, listener, flush);
		}
	}

}
