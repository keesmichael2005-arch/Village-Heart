package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.*;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.Rank;
import dev.villageheart.heart.HeartFacts;
import com.mojang.authlib.GameProfile;
import dev.villageheart.heart.net.AssignJobPayload;
import dev.villageheart.heart.net.FireWorkerPayload;
import dev.villageheart.heart.net.HeartActionResultPayload;
import dev.villageheart.heart.net.RecheckBuildingPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import dev.villageheart.heart.HeartActions;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.heart.net.HeartNetwork;
import dev.villageheart.heart.net.HeartSnapshotPayload;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared seed and helpers of the Heart screen GameTests (M8). */
public final class HeartTestSupport {
	private HeartTestSupport() {}

	public static final BlockPos STORAGE = new BlockPos(8, 0, 4);
	public static final BlockPos FARMHOUSE = new BlockPos(18, 0, 4);

	public static String env(String name) { return "villageheart-gametest:heart_" + name; }

	public static BlockPos chest1() { return inside(STORAGE, 0, 0, 3); }

	public static BlockPos chest2() { return inside(STORAGE, 4, 0, 3); }

	/**
	 * The common seed (plan section 5): a claim, a Storage with 2 chests (64 oak logs, 20 bread), a Farmhouse with 1 composter,
	 * both valid. The returned sequence has run {@code begin}; call this first in the test. Villagers are spawned by the test.
	 */
	public static GameTestSequence seed(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			BlockPos sm = room(h, STORAGE, 5, 4);
			h.setBlock(chest1(), net.minecraft.world.level.block.Blocks.CHEST);
			h.setBlock(chest2(), net.minecraft.world.level.block.Blocks.CHEST);
			StockpileTestSupport.fill(h, chest1(), new ItemStack(Items.OAK_LOG, 64));
			StockpileTestSupport.fill(h, chest2(), new ItemStack(Items.BREAD, 20));
			BlockPos fm = WorkerTestSupport.farmhouse(h, FARMHOUSE, 1);
			placeMarker(h, BuildingType.STORAGE, sm, Direction.NORTH);
			placeMarker(h, BuildingType.FARMHOUSE, fm, Direction.NORTH);
		});
		return awaitWithin(h, seq, bound(2, 0), "both buildings valid", () -> {
			h.assertTrue(status(h, markerPos(STORAGE, 5)) == dev.villageheart.block.MarkerStatus.VALID, "storage valid");
			h.assertTrue(status(h, markerPos(FARMHOUSE, 5)) == dev.villageheart.block.MarkerStatus.VALID, "farmhouse valid");
		});
	}

	public static Villager villager(GameTestHelper h) {
		return WorkerTestSupport.spawnVillager(h, inside(FARMHOUSE, 2, 0, 1));
	}

	/** The snapshot for {@code rank}, built from fresh facts (not the cache). */
	public static HeartSnapshot snapshot(GameTestHelper h, Rank rank) {
		return HeartSnapshotBuilder.build(HeartFacts.collect(h.getLevel().getServer()).orElseThrow(), rank);
	}

	public static void done(GameTestHelper h) {
		HeartNetwork.setSenderForTest(null);
		HeartActions.reset();
		VillageTestSupport.done(h);
		WorkerTestSupport.end(h);
	}

	// ---- players, opening, recording ------------------------------------------------------------

	/** A survival player standing next to the Heart with the given rank (Visitor needs none). */
	public static ServerPlayer player(GameTestHelper h, String name, Rank rank) {
		ServerPlayer p = VillageTestSupport.player(h, name, GameType.SURVIVAL);
		if (rank != Rank.VISITOR && rank != Rank.OWNER) VillageClaims.setRank(h.getLevel().getServer(), p.getUUID(), rank);
		standNearHeart(h, p);
		return p;
	}

	public static BlockPos heartPos(GameTestHelper h) {
		var loc = VillageClaims.current(h.getLevel().getServer()).orElseThrow().heart();
		return new BlockPos(loc.x(), loc.y(), loc.z());
	}

	public static void standNearHeart(GameTestHelper h, ServerPlayer p) {
		BlockPos hp = heartPos(h);
		p.setPos(hp.getX() + 0.5, hp.getY(), hp.getZ() + 2.5);
	}

	/** Right-click the Heart (the real {@code useWithoutItem} path) and check that the Heart menu is now open. */
	public static HeartMenu open(GameTestHelper h, ServerPlayer p) {
		standNearHeart(h, p);
		rightClickAbs(h.getLevel(), p, heartPos(h), Direction.UP, ItemStack.EMPTY);
		h.assertTrue(p.containerMenu instanceof HeartMenu, p.getName().getString() + " has the Heart menu open");
		return (HeartMenu) p.containerMenu;
	}

	public record Sent(java.util.UUID player, CustomPacketPayload payload) {}

	/** Installs a recording sender (restored by {@link #done}). Nothing reaches the players' (fake) connections. */
	public static List<Sent> recorder() {
		List<Sent> sent = new ArrayList<>();
		HeartNetwork.setSenderForTest((p, payload) -> sent.add(new Sent(p.getUUID(), payload)));
		return sent;
	}

	public static List<HeartSnapshotPayload> snapshotsTo(List<Sent> sent, ServerPlayer p) {
		return sent.stream().filter(s -> s.player().equals(p.getUUID()) && s.payload() instanceof HeartSnapshotPayload)
				.map(s -> (HeartSnapshotPayload) s.payload()).toList();
	}

	public static List<HeartActionResultPayload> resultsTo(List<Sent> sent, ServerPlayer p) {
		return sent.stream().filter(s -> s.player().equals(p.getUUID()) && s.payload() instanceof HeartActionResultPayload)
				.map(s -> (HeartActionResultPayload) s.payload()).toList();
	}

	public static HeartActionResultPayload lastResult(List<Sent> sent, ServerPlayer p) {
		List<HeartActionResultPayload> r = resultsTo(sent, p);
		if (r.isEmpty()) throw new AssertionError("no action result was sent to " + p.getName().getString());
		return r.get(r.size() - 1);
	}

	/**
	 * Sends {@code payload} the way a client does: encoded with its registered codec, decoded again, then handed to the player's
	 * connection, which Fabric dispatches to our global receiver (synchronously on the server thread).
	 */
	public static <T extends CustomPacketPayload> void send(ServerPlayer p, StreamCodec<ByteBuf, T> codec, T payload) {
		ByteBuf buf = Unpooled.buffer();
		codec.encode(buf, payload);
		T decoded = codec.decode(buf);
		p.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(decoded));
	}

	public static void assign(ServerPlayer p, Villager v, String job, Optional<BlockPos> building) {
		send(p, AssignJobPayload.CODEC, new AssignJobPayload(p.containerMenu.containerId, v.getUUID(), job, building));
	}

	public static void fire(ServerPlayer p, java.util.UUID villager) {
		send(p, FireWorkerPayload.CODEC, new FireWorkerPayload(p.containerMenu.containerId, villager));
	}

	public static void recheck(ServerPlayer p, BlockPos abs) {
		send(p, RecheckBuildingPayload.CODEC, new RecheckBuildingPayload(p.containerMenu.containerId, abs));
	}

	/** True when the villager is a worker (attachment) or has a registry record. */
	public static boolean isWorker(GameTestHelper h, Villager v) {
		return dev.villageheart.worker.WorkerAttachments.isWorker(v)
				|| VillageClaims.current(h.getLevel().getServer()).flatMap(s -> s.worker(v.getUUID())).isPresent();
	}

	/** A player that records the action-bar messages sent to it. */
	public static final class OverlayPlayer extends ServerPlayer {
		public final List<Component> overlays = new ArrayList<>();

		OverlayPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
			super(server, level, profile, ClientInformation.createDefault());
		}

		@Override
		public void sendSystemMessage(Component message, boolean overlay) {
			if (overlay) overlays.add(message);
			super.sendSystemMessage(message, overlay);
		}
	}
}
