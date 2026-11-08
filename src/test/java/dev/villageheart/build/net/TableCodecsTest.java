package dev.villageheart.build.net;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.blueprint.TableSnapshot;
import dev.villageheart.core.blueprint.TableSnapshot.TableBlueprint;
import dev.villageheart.core.blueprint.TableSnapshot.TableBuild;
import dev.villageheart.core.blueprint.TableSnapshot.TableMaterial;
import dev.villageheart.core.heart.BuildRow;
import dev.villageheart.core.heart.HeartLimits;
import dev.villageheart.core.heart.TextLine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

class TableCodecsTest {
	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		codec.encode(buf, value);
		T back = codec.decode(buf);
		assertEquals(0, buf.readableBytes(), "the whole message was read");
		return back;
	}

	private static String s(int n) { return "€".repeat(n); }

	private static TableSnapshot sample() {
		TextLine t = new TextLine("build.villageheart.needs", "Needs %s more %s", List.of("12", "planks"));
		BuildRow row = new BuildRow("small_house", 10, 64, -3, BuildStatus.NEEDS_MATERIALS, 86, 172, t, List.of(t), "Ada");
		return new TableSnapshot(Rank.STEWARD, Tier.VILLAGE,
				List.of(new TableBlueprint("small_house", false, 7, 5, 8, List.of(new TableMaterial("planks", 119, 40), new TableMaterial("door", 1, 0))),
						new TableBlueprint("farm_plot", true, 11, 3, 11, List.of())),
				List.of(new TableBuild(new UUID(1, 2), row)));
	}

	@Test
	void roundTrips() {
		TableSnapshot snap = sample();
		assertEquals(snap, roundTrip(TableCodecs.SNAPSHOT, snap));
		BuildersTableSnapshotPayload p = new BuildersTableSnapshotPayload(7, snap);
		assertEquals(p, roundTrip(BuildersTableSnapshotPayload.CODEC, p));
		TakeBlueprintPayload take = new TakeBlueprintPayload(3, "small_house");
		assertEquals(take, roundTrip(TakeBlueprintPayload.CODEC, take));
		CancelBuildPayload cancel = new CancelBuildPayload(3, new UUID(5, 6));
		assertEquals(cancel, roundTrip(CancelBuildPayload.CODEC, cancel));
	}

	@Test
	void worstCaseIsSmall() {
		TextLine t = new TextLine("build.villageheart." + s(HeartLimits.MAX_KEY - 19), s(HeartLimits.MAX_TEXT), List.of(s(HeartLimits.MAX_BUILD_ARG), s(HeartLimits.MAX_BUILD_ARG)));
		BuildRow row = new BuildRow(s(HeartLimits.MAX_ID), Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, BuildStatus.BLOCKED,
				Integer.MIN_VALUE, Integer.MIN_VALUE, t, List.of(t, t), s(HeartLimits.MAX_NAME));
		List<TableMaterial> mats = new ArrayList<>();
		for (int i = 0; i < TableSnapshot.MAX_MATERIALS; i++) mats.add(new TableMaterial(s(HeartLimits.MAX_NAME), Integer.MIN_VALUE, Long.MIN_VALUE));
		List<TableBlueprint> bps = new ArrayList<>();
		for (int i = 0; i < TableSnapshot.MAX_BLUEPRINTS; i++) bps.add(new TableBlueprint(s(HeartLimits.MAX_ID), true, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, mats));
		List<TableBuild> builds = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_BUILDS; i++) builds.add(new TableBuild(UUID.randomUUID(), row));
		TableSnapshot worst = new TableSnapshot(Rank.OWNER, Tier.TOWN, bps, builds);
		ByteBuf buf = Unpooled.buffer();
		TableCodecs.SNAPSHOT.encode(buf, worst);
		System.out.println("builders table snapshot worst case: " + buf.readableBytes() + " bytes");
		assertTrue(buf.readableBytes() < 64 * 1024, "worst case " + buf.readableBytes());
		assertEquals(worst, TableCodecs.SNAPSHOT.decode(buf));
	}

	@Test
	void listsAreBounded() {
		List<TableBlueprint> many = new ArrayList<>();
		for (int i = 0; i <= TableSnapshot.MAX_BLUEPRINTS; i++) many.add(new TableBlueprint("x", false, 1, 1, 1, List.of()));
		TableSnapshot tooMany = new TableSnapshot(Rank.OWNER, Tier.TOWN, many, List.of());
		assertThrows(RuntimeException.class, () -> TableCodecs.SNAPSHOT.encode(Unpooled.buffer(), tooMany));
		ByteBuf hostile = Unpooled.buffer();
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(hostile, Rank.OWNER.ordinal());
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(hostile, Tier.TOWN.ordinal());
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(hostile, 1_000_000);
		assertThrows(DecoderException.class, () -> TableCodecs.SNAPSHOT.decode(hostile));
		ByteBuf bad = Unpooled.buffer();
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(bad, 99);
		assertThrows(DecoderException.class, () -> TableCodecs.SNAPSHOT.decode(bad));
		ByteBuf longId = Unpooled.buffer();
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(longId, 1);
		net.minecraft.network.codec.ByteBufCodecs.stringUtf8(1000).encode(longId, "j".repeat(HeartLimits.MAX_ID + 1));
		assertThrows(DecoderException.class, () -> TakeBlueprintPayload.CODEC.decode(longId));
	}
}
