package dev.villageheart.heart.net;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.BuildingRow;
import dev.villageheart.core.heart.DetailKind;
import dev.villageheart.core.heart.GoalLine;
import dev.villageheart.core.heart.HeartLimits;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.JobChoice;
import dev.villageheart.core.heart.NeedsFlags;
import dev.villageheart.core.heart.Overview;
import dev.villageheart.core.heart.RowStatus;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.Stock;
import dev.villageheart.core.heart.StockLine;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.stock.StorageAlert;
import dev.villageheart.heart.HeartOpenData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

class HeartCodecsTest {
	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		codec.encode(buf, value);
		T back = codec.decode(buf);
		assertEquals(0, buf.readableBytes(), "everything written was read");
		return back;
	}

	private static int encodedSize(HeartSnapshot s) {
		ByteBuf buf = Unpooled.buffer();
		HeartCodecs.SNAPSHOT.encode(buf, s);
		return buf.readableBytes();
	}

	private static UUID id(int n) { return new UUID(7, n); }

	private static HeartSnapshot full() {
		TextLine t = new TextLine("gui.villageheart.heart.alert.x", "%s buildings need attention", List.of("2"));
		Overview o = new Overview("Owner", Tier.VILLAGE, Optional.of(Tier.TOWN),
				List.of(new GoalLine(new TextLine("g", "Goal"), 3, 5, false)), 9, 7, 2, 4, 2, 3, 33, 12, -1,
				List.of(new AlertLine(AlertCode.STORAGE_FULL, Severity.WARNING, t),
						new AlertLine(AlertCode.WORKPLACE_INVALID, Severity.DANGER, new TextLine("k", "f"))), 1);
		List<VillagerRow> v = List.of(
				new VillagerRow(id(1), "Ada", "farmer", "villageheart:farmer", 2, 150, 300, NeedsFlags.of(true, false, true),
						ActivityKind.WORKING, true, false, true, true),
				new VillagerRow(id(2), "Bram", "", "minecraft:none", 0, 0, 0, NeedsFlags.UNKNOWN, ActivityKind.AWAY, false, true,
						false, false));
		List<BuildingRow> b = List.of(new BuildingRow("house", -5, 64, 1_000_000, RowStatus.INVALID,
				List.of(new TextLine("a", "b"), new TextLine("c", "d")), 3, DetailKind.HOMES, 2, 0));
		Stock s = new Stock(List.of(new StockLine("minecraft:bread", 1L << 40, 5), new StockLine("minecraft:oak_log", 64, 0)), 4,
				(1L << 40) + 64, 5L << 40, 2, 1, 4, 30, 108, StorageAlert.FULL);
		return new HeartSnapshot(Rank.STEWARD, o, v, 3, List.of(new JobChoice("farmer", 0, false), new JobChoice("mason", 1, true)),
				b, 2, s);
	}

	@Test void roundTripFullSnapshot() {
		HeartSnapshot s = full();
		assertEquals(s, roundTrip(HeartCodecs.SNAPSHOT, s));
	}

	@Test void roundTripEmptyAndEveryEnum() {
		Overview o = new Overview("", Tier.TOWN, Optional.empty(), List.of(), 0, 0, 0, 0, 0, 0, -1, 0, -1, List.of(), 0);
		HeartSnapshot s = new HeartSnapshot(Rank.VISITOR, o, List.of(), 0, List.of(), List.of(), 0, Stock.EMPTY);
		assertEquals(s, roundTrip(HeartCodecs.SNAPSHOT, s));
		for (Rank r : Rank.values()) {
			HeartSnapshot x = new HeartSnapshot(r, o, List.of(), 0, List.of(), List.of(), 0, Stock.EMPTY);
			assertEquals(x, roundTrip(HeartCodecs.SNAPSHOT, x));
		}
		for (ActivityKind a : ActivityKind.values()) {
			VillagerRow row = new VillagerRow(id(1), "n", "", "x", 0, 0, 0, NeedsFlags.UNKNOWN, a, false, false, false, true);
			assertEquals(row, roundTrip(HeartCodecs.VILLAGER_ROW, row));
		}
		for (RowStatus st : RowStatus.values()) for (DetailKind d : DetailKind.values()) {
			BuildingRow row = new BuildingRow("house", 1, 2, 3, st, List.of(), 0, d, 1, 1);
			assertEquals(row, roundTrip(HeartCodecs.BUILDING_ROW, row));
		}
		for (StorageAlert a : StorageAlert.values()) {
			Stock st = new Stock(List.of(), 0, 0, 0, 0, 0, 0, 0, 0, a);
			assertEquals(st, roundTrip(HeartCodecs.STOCK, st));
		}
	}

	@Test void payloadsRoundTrip() {
		HeartSnapshotPayload snap = new HeartSnapshotPayload(5, full());
		assertEquals(snap, roundTrip(HeartSnapshotPayload.CODEC, snap));
		HeartActionResultPayload res = new HeartActionResultPayload(5, true, "message.villageheart.hire.hired", "Hired as %s.",
				List.of("Farmer"));
		assertEquals(res, roundTrip(HeartActionResultPayload.CODEC, res));
		AssignJobPayload a = new AssignJobPayload(300, id(4), "farmer", Optional.of(new BlockPos(-3, 70, 12)));
		assertEquals(a, roundTrip(AssignJobPayload.CODEC, a));
		AssignJobPayload a2 = new AssignJobPayload(0, id(4), "farmer", Optional.empty());
		assertEquals(a2, roundTrip(AssignJobPayload.CODEC, a2));
		FireWorkerPayload f = new FireWorkerPayload(2, id(9));
		assertEquals(f, roundTrip(FireWorkerPayload.CODEC, f));
		RecheckBuildingPayload r = new RecheckBuildingPayload(2, new BlockPos(1, 2, 3));
		assertEquals(r, roundTrip(RecheckBuildingPayload.CODEC, r));
		HeartOpenData d = new HeartOpenData(new BlockPos(4, 5, 6), full());
		assertEquals(d, roundTrip(HeartOpenData.STREAM_CODEC, d));
	}

	// ---- size ---------------------------------------------------------------------------------------------------------

	private static String s(int n) { return "€".repeat(n); }

	/** Every list at its cap, every string at its cap in 3-byte characters. */
	private static HeartSnapshot worstCase() {
		TextLine t = new TextLine(s(HeartLimits.MAX_KEY), s(HeartLimits.MAX_TEXT),
				List.of(s(HeartLimits.MAX_ARG), s(HeartLimits.MAX_ARG)));
		assertEquals(HeartLimits.MAX_TEXT, t.fallback().length(), "the test really fills the caps");
		List<AlertLine> alerts = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_ALERTS; i++) alerts.add(new AlertLine(AlertCode.NO_STORAGE, Severity.DANGER, t));
		List<GoalLine> goals = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_GOALS; i++) goals.add(new GoalLine(t, Integer.MAX_VALUE, Integer.MAX_VALUE, true));
		Overview o = new Overview(s(HeartLimits.MAX_NAME), Tier.TOWN, Optional.of(Tier.TOWN), goals, Integer.MIN_VALUE,
				Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
				Integer.MIN_VALUE, Integer.MIN_VALUE, alerts, Integer.MIN_VALUE);
		List<VillagerRow> v = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_VILLAGERS; i++) {
			v.add(new VillagerRow(UUID.randomUUID(), s(HeartLimits.MAX_NAME), s(HeartLimits.MAX_ID), s(HeartLimits.MAX_ID),
					Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, NeedsFlags.of(true, true, true), ActivityKind.OTHER,
					true, true, true, true));
		}
		List<JobChoice> jobs = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_JOB_CHOICES; i++) jobs.add(new JobChoice(s(HeartLimits.MAX_ID), Integer.MIN_VALUE, true));
		List<TextLine> reasons = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_REASONS; i++) reasons.add(t);
		List<BuildingRow> b = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_BUILDINGS; i++) {
			b.add(new BuildingRow(s(HeartLimits.MAX_ID), Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, RowStatus.INVALID,
					reasons, Integer.MIN_VALUE, DetailKind.CONTAINERS, Integer.MIN_VALUE, Integer.MIN_VALUE));
		}
		List<StockLine> items = new ArrayList<>();
		for (int i = 0; i < HeartLimits.MAX_STOCK_KINDS; i++) items.add(new StockLine(s(HeartLimits.MAX_ID), Long.MIN_VALUE, Integer.MIN_VALUE));
		Stock st = new Stock(items, Integer.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
				Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, StorageAlert.FULL);
		return new HeartSnapshot(Rank.OWNER, o, v, Integer.MIN_VALUE, jobs, b, Integer.MIN_VALUE, st);
	}

	@Test void worstCaseSize() {
		HeartSnapshot s = worstCase();
		int size = encodedSize(s);
		System.out.println("heart snapshot worst case: " + size + " bytes (" + size / 1024 + " KiB)");
		assertTrue(size < 512 * 1024, "worst case " + size + " bytes must stay under half the 1 MiB vanilla S2C cap");
		assertEquals(s, roundTrip(HeartCodecs.SNAPSHOT, s), "and it still decodes");
	}

	// ---- limits -------------------------------------------------------------------------------------------------------

	@Test void clientStringsAreBounded() {
		ByteBuf buf = Unpooled.buffer();
		// A 33-char job id written by a hostile client, with the same wire layout as the real payload.
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, 1);
		net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buf, id(1));
		net.minecraft.network.codec.ByteBufCodecs.stringUtf8(1000).encode(buf, "j".repeat(HeartLimits.MAX_JOB_ID + 1));
		net.minecraft.network.codec.ByteBufCodecs.BOOL.encode(buf, false);
		assertThrows(DecoderException.class, () -> AssignJobPayload.CODEC.decode(buf));

		ByteBuf ok = Unpooled.buffer();
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(ok, 1);
		net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(ok, id(1));
		net.minecraft.network.codec.ByteBufCodecs.stringUtf8(1000).encode(ok, "j".repeat(HeartLimits.MAX_JOB_ID));
		net.minecraft.network.codec.ByteBufCodecs.BOOL.encode(ok, false);
		assertEquals(HeartLimits.MAX_JOB_ID, AssignJobPayload.CODEC.decode(ok).jobId().length());
	}

	@Test void listsAreBounded() {
		// 101 villager rows: too many to encode and, written by a hostile peer, too many to decode.
		VillagerRow row = new VillagerRow(id(1), "n", "", "x", 0, 0, 0, NeedsFlags.UNKNOWN, ActivityKind.IDLE, false, false, false,
				true);
		List<VillagerRow> many = new ArrayList<>();
		for (int i = 0; i <= HeartLimits.MAX_VILLAGERS; i++) many.add(row);
		Overview o = new Overview("", Tier.TOWN, Optional.empty(), List.of(), 0, 0, 0, 0, 0, 0, -1, 0, -1, List.of(), 0);
		HeartSnapshot tooMany = new HeartSnapshot(Rank.OWNER, o, many, 0, List.of(), List.of(), 0, Stock.EMPTY);
		assertThrows(RuntimeException.class, () -> encodedSize(tooMany));

		ByteBuf hostile = Unpooled.buffer();
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(hostile, Rank.OWNER.ordinal());
		HeartCodecs.OVERVIEW.encode(hostile, o);
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(hostile, 1_000_000); // villager count
		assertThrows(DecoderException.class, () -> HeartCodecs.SNAPSHOT.decode(hostile));
	}

	@Test void badEnumOrdinalIsRejected() {
		ByteBuf buf = Unpooled.buffer();
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, 99);
		assertThrows(DecoderException.class, () -> HeartCodecs.SNAPSHOT.decode(buf));
	}

	@Test void textLineIsCutToTheWireCaps() {
		TextLine t = new TextLine("k".repeat(500), "f".repeat(500), List.of("a".repeat(500), "b", "c", "d"));
		assertEquals(HeartLimits.MAX_KEY, t.key().length());
		assertEquals(HeartLimits.MAX_TEXT, t.fallback().length());
		assertEquals(HeartLimits.MAX_TEXT_ARGS, t.args().size());
		assertEquals(HeartLimits.MAX_ARG, t.args().get(0).length());
		assertEquals(t, roundTrip(HeartCodecs.TEXT_LINE, t));
	}
}
