package dev.villageheart.heart.net;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.BuildRow;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.BuildingRow;
import dev.villageheart.core.heart.DeathRow;
import dev.villageheart.core.heart.DetailKind;
import dev.villageheart.core.heart.GoalLine;
import dev.villageheart.core.heart.HeartLimits;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.JobChoice;
import dev.villageheart.core.heart.MemberRow;
import dev.villageheart.core.heart.NeedsFlags;
import dev.villageheart.core.heart.Overview;
import dev.villageheart.core.heart.RowStatus;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.Stock;
import dev.villageheart.core.heart.StockLine;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.stock.StorageAlert;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wire codecs of the Heart screen snapshot. Hand written (the records have more fields than {@code StreamCodec.composite}
 * takes). Every string and list is bounded, so a hostile or broken peer cannot make us allocate without limit: decoding
 * something over a bound throws {@link DecoderException}. Items travel as id strings, so no registry access is needed.
 */
public final class HeartCodecs {
	private HeartCodecs() {}

	private static <T> StreamCodec<ByteBuf, T> codec(Function<ByteBuf, T> decode, BiConsumer<ByteBuf, T> encode) {
		return new StreamCodec<>() {
			@Override public T decode(ByteBuf buf) { return decode.apply(buf); }

			@Override public void encode(ByteBuf buf, T value) { encode.accept(buf, value); }
		};
	}

	private static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(Class<E> type) {
		E[] values = type.getEnumConstants();
		return codec(buf -> {
			int i = ByteBufCodecs.VAR_INT.decode(buf);
			if (i < 0 || i >= values.length) throw new DecoderException("bad " + type.getSimpleName() + " ordinal " + i);
			return values[i];
		}, (buf, e) -> ByteBufCodecs.VAR_INT.encode(buf, e.ordinal()));
	}

	private static final StreamCodec<ByteBuf, Integer> INT = ByteBufCodecs.VAR_INT;
	private static final StreamCodec<ByteBuf, Long> LONG = ByteBufCodecs.VAR_LONG;
	private static final StreamCodec<ByteBuf, Boolean> BOOL = ByteBufCodecs.BOOL;
	public static final StreamCodec<ByteBuf, String> KEY = ByteBufCodecs.stringUtf8(HeartLimits.MAX_KEY);
	public static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(HeartLimits.MAX_ID);
	public static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(HeartLimits.MAX_NAME);
	public static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(HeartLimits.MAX_TEXT);
	public static final StreamCodec<ByteBuf, String> ARG = ByteBufCodecs.stringUtf8(HeartLimits.MAX_ARG);
	/** The job id a client sends. */
	public static final StreamCodec<ByteBuf, String> JOB_ID = ByteBufCodecs.stringUtf8(HeartLimits.MAX_JOB_ID);
	public static final StreamCodec<ByteBuf, List<String>> ARGS = ARG.apply(ByteBufCodecs.list(HeartLimits.MAX_TEXT_ARGS));

	private static final StreamCodec<ByteBuf, Rank> RANK = enumCodec(Rank.class);
	private static final StreamCodec<ByteBuf, Tier> TIER = enumCodec(Tier.class);
	private static final StreamCodec<ByteBuf, Severity> SEVERITY = enumCodec(Severity.class);
	private static final StreamCodec<ByteBuf, AlertCode> ALERT_CODE = enumCodec(AlertCode.class);
	private static final StreamCodec<ByteBuf, ActivityKind> ACTIVITY = enumCodec(ActivityKind.class);
	private static final StreamCodec<ByteBuf, dev.villageheart.core.heart.GuardShiftView> SHIFT = enumCodec(dev.villageheart.core.heart.GuardShiftView.class);
	private static final StreamCodec<ByteBuf, RowStatus> ROW_STATUS = enumCodec(RowStatus.class);
	private static final StreamCodec<ByteBuf, DetailKind> DETAIL = enumCodec(DetailKind.class);
	private static final StreamCodec<ByteBuf, StorageAlert> STORAGE_ALERT = enumCodec(StorageAlert.class);

	public static final StreamCodec<ByteBuf, TextLine> TEXT_LINE = codec(
			buf -> new TextLine(KEY.decode(buf), TEXT.decode(buf), ARGS.decode(buf)),
			(buf, t) -> {
				KEY.encode(buf, t.key());
				TEXT.encode(buf, t.fallback());
				ARGS.encode(buf, t.args());
			});

	private static final StreamCodec<ByteBuf, List<String>> BUILD_ARGS = ByteBufCodecs.stringUtf8(HeartLimits.MAX_BUILD_ARG)
			.apply(ByteBufCodecs.list(HeartLimits.MAX_TEXT_ARGS));

	/** A text of the build system: args may be a position, so up to {@link HeartLimits#MAX_BUILD_ARG} characters (the key must say so). */
	public static final StreamCodec<ByteBuf, TextLine> BUILD_TEXT_LINE = codec(
			buf -> new TextLine(KEY.decode(buf), TEXT.decode(buf), BUILD_ARGS.decode(buf)),
			(buf, t) -> {
				KEY.encode(buf, t.key());
				TEXT.encode(buf, t.fallback());
				BUILD_ARGS.encode(buf, t.args());
			});

	private static final StreamCodec<ByteBuf, AlertLine> ALERT_LINE = codec(
			buf -> new AlertLine(ALERT_CODE.decode(buf), SEVERITY.decode(buf), TEXT_LINE.decode(buf)),
			(buf, a) -> {
				ALERT_CODE.encode(buf, a.code());
				SEVERITY.encode(buf, a.severity());
				TEXT_LINE.encode(buf, a.text());
			});

	private static final StreamCodec<ByteBuf, GoalLine> GOAL_LINE = codec(
			buf -> new GoalLine(TEXT_LINE.decode(buf), INT.decode(buf), INT.decode(buf), BOOL.decode(buf)),
			(buf, g) -> {
				TEXT_LINE.encode(buf, g.label());
				INT.encode(buf, g.have());
				INT.encode(buf, g.need());
				BOOL.encode(buf, g.met());
			});

	private static final StreamCodec<ByteBuf, List<GoalLine>> GOALS = GOAL_LINE.apply(ByteBufCodecs.list(HeartLimits.MAX_GOALS));
	private static final StreamCodec<ByteBuf, List<AlertLine>> ALERTS = ALERT_LINE.apply(ByteBufCodecs.list(HeartLimits.MAX_ALERTS));
	private static final StreamCodec<ByteBuf, Optional<Tier>> NEXT_TIER = ByteBufCodecs.optional(TIER);
	private static final StreamCodec<ByteBuf, DeathRow> DEATH_ROW = codec(
			buf -> new DeathRow(NAME.decode(buf), NAME.decode(buf), ByteBufCodecs.stringUtf8(HeartLimits.MAX_ID).decode(buf),
					INT.decode(buf), BOOL.decode(buf)),
			(buf, d) -> {
				NAME.encode(buf, d.name());
				NAME.encode(buf, d.jobId());
				ByteBufCodecs.stringUtf8(HeartLimits.MAX_ID).encode(buf, d.cause());
				INT.encode(buf, d.day());
				BOOL.encode(buf, d.duringRaid());
			});
	private static final StreamCodec<ByteBuf, List<DeathRow>> DEATHS = DEATH_ROW.apply(ByteBufCodecs.list(HeartLimits.MAX_DEATH_ROWS));

	public static final StreamCodec<ByteBuf, Overview> OVERVIEW = codec(
			buf -> new Overview(NAME.decode(buf), TIER.decode(buf), NEXT_TIER.decode(buf), GOALS.decode(buf), INT.decode(buf),
					INT.decode(buf), INT.decode(buf), INT.decode(buf), INT.decode(buf), INT.decode(buf), INT.decode(buf),
					INT.decode(buf), INT.decode(buf), ALERTS.decode(buf), INT.decode(buf), DEATHS.decode(buf), LONG.decode(buf)),
			(buf, o) -> {
				NAME.encode(buf, o.ownerName());
				TIER.encode(buf, o.tier());
				NEXT_TIER.encode(buf, o.nextTier());
				GOALS.encode(buf, o.goals());
				INT.encode(buf, o.population());
				INT.encode(buf, o.adults());
				INT.encode(buf, o.babies());
				INT.encode(buf, o.beds());
				INT.encode(buf, o.workers());
				INT.encode(buf, o.workerSlots());
				INT.encode(buf, o.foodDaysTenths());
				INT.encode(buf, o.treasury());
				INT.encode(buf, o.wageBill());
				ALERTS.encode(buf, o.alerts());
				INT.encode(buf, o.moreAlerts());
				DEATHS.encode(buf, o.recentDeaths());
				LONG.encode(buf, o.deathsLogged());
			});

	private static final StreamCodec<ByteBuf, NeedsFlags> NEEDS = codec(
			buf -> new NeedsFlags(BOOL.decode(buf), BOOL.decode(buf), BOOL.decode(buf), BOOL.decode(buf)),
			(buf, n) -> {
				BOOL.encode(buf, n.known());
				BOOL.encode(buf, n.fed());
				BOOL.encode(buf, n.housed());
				BOOL.encode(buf, n.paid());
			});

	public static final StreamCodec<ByteBuf, VillagerRow> VILLAGER_ROW = codec(
			buf -> new VillagerRow(UUIDUtil.STREAM_CODEC.decode(buf), NAME.decode(buf), ID.decode(buf), ID.decode(buf),
					INT.decode(buf), INT.decode(buf), INT.decode(buf), NEEDS.decode(buf), ACTIVITY.decode(buf), BOOL.decode(buf),
					BOOL.decode(buf), BOOL.decode(buf), BOOL.decode(buf), BOOL.decode(buf), SHIFT.decode(buf)),
			(buf, v) -> {
				UUIDUtil.STREAM_CODEC.encode(buf, v.id());
				NAME.encode(buf, v.name());
				ID.encode(buf, v.jobId());
				ID.encode(buf, v.vanillaProfession());
				INT.encode(buf, v.level());
				INT.encode(buf, v.xp());
				INT.encode(buf, v.xpForNext());
				NEEDS.encode(buf, v.needs());
				ACTIVITY.encode(buf, v.activity());
				BOOL.encode(buf, v.workplaceInvalid());
				BOOL.encode(buf, v.nitwit());
				BOOL.encode(buf, v.hasTraded());
				BOOL.encode(buf, v.loaded());
				BOOL.encode(buf, v.levelCapped());
				SHIFT.encode(buf, v.shift());
			});

	private static final StreamCodec<ByteBuf, JobChoice> JOB_CHOICE = codec(
			buf -> new JobChoice(ID.decode(buf), INT.decode(buf), BOOL.decode(buf)),
			(buf, j) -> {
				ID.encode(buf, j.jobId());
				INT.encode(buf, j.freeSlots());
				BOOL.encode(buf, j.tierLocked());
			});

	private static final StreamCodec<ByteBuf, List<TextLine>> REASONS = TEXT_LINE.apply(ByteBufCodecs.list(HeartLimits.MAX_REASONS));

	public static final StreamCodec<ByteBuf, BuildingRow> BUILDING_ROW = codec(
			buf -> new BuildingRow(ID.decode(buf), INT.decode(buf), INT.decode(buf), INT.decode(buf), ROW_STATUS.decode(buf),
					REASONS.decode(buf), INT.decode(buf), DETAIL.decode(buf), INT.decode(buf), INT.decode(buf)),
			(buf, b) -> {
				ID.encode(buf, b.typeId());
				INT.encode(buf, b.x());
				INT.encode(buf, b.y());
				INT.encode(buf, b.z());
				ROW_STATUS.encode(buf, b.status());
				REASONS.encode(buf, b.reasons());
				INT.encode(buf, b.moreReasons());
				DETAIL.encode(buf, b.detail());
				INT.encode(buf, b.detailCount());
				INT.encode(buf, b.workersHere());
			});

	private static final StreamCodec<ByteBuf, BuildStatus> BUILD_STATUS = enumCodec(BuildStatus.class);

	public static final StreamCodec<ByteBuf, BuildRow> BUILD_ROW = codec(
			buf -> new BuildRow(ID.decode(buf), INT.decode(buf), INT.decode(buf), INT.decode(buf), BUILD_STATUS.decode(buf), INT.decode(buf),
					INT.decode(buf), BUILD_TEXT_LINE.decode(buf), BUILD_TEXT_LINE.apply(ByteBufCodecs.list(HeartLimits.MAX_BUILD_MISSING)).decode(buf), NAME.decode(buf)),
			(buf, b) -> {
				ID.encode(buf, b.blueprintId());
				INT.encode(buf, b.x());
				INT.encode(buf, b.y());
				INT.encode(buf, b.z());
				BUILD_STATUS.encode(buf, b.state());
				INT.encode(buf, b.placed());
				INT.encode(buf, b.total());
				BUILD_TEXT_LINE.encode(buf, b.status());
				BUILD_TEXT_LINE.apply(ByteBufCodecs.list(HeartLimits.MAX_BUILD_MISSING)).encode(buf, b.missing());
				NAME.encode(buf, b.builderName());
			});

	private static final StreamCodec<ByteBuf, List<BuildRow>> BUILDS = BUILD_ROW.apply(ByteBufCodecs.list(HeartLimits.MAX_BUILDS));

	private static final StreamCodec<ByteBuf, StockLine> STOCK_LINE = codec(
			buf -> new StockLine(ID.decode(buf), LONG.decode(buf), INT.decode(buf), LONG.decode(buf)),
			(buf, s) -> {
				ID.encode(buf, s.itemId());
				LONG.encode(buf, s.count());
				INT.encode(buf, s.nutritionEach());
				LONG.encode(buf, s.forSale());
			});

	private static final StreamCodec<ByteBuf, List<StockLine>> STOCK_LINES = STOCK_LINE.apply(ByteBufCodecs.list(HeartLimits.MAX_STOCK_KINDS));

	public static final StreamCodec<ByteBuf, Stock> STOCK = codec(
			buf -> new Stock(STOCK_LINES.decode(buf), INT.decode(buf), LONG.decode(buf), LONG.decode(buf), INT.decode(buf),
					INT.decode(buf), INT.decode(buf), INT.decode(buf), INT.decode(buf), STORAGE_ALERT.decode(buf),
					decodeReserves(buf), INT.decode(buf), INT.decode(buf)),
			(buf, s) -> {
				STOCK_LINES.encode(buf, s.items());
				INT.encode(buf, s.moreKinds());
				LONG.encode(buf, s.totalItems());
				LONG.encode(buf, s.nutrition());
				INT.encode(buf, s.storages());
				INT.encode(buf, s.unknownStorages());
				INT.encode(buf, s.containers());
				INT.encode(buf, s.freeSlots());
				INT.encode(buf, s.slots());
				STORAGE_ALERT.encode(buf, s.alert());
				INT.encode(buf, s.reserves().foodDays());
				INT.encode(buf, s.reserves().itemReserve());
				INT.encode(buf, s.soldToday());
				INT.encode(buf, s.merchants());
			});

	/** The two reserve numbers, clamped into range: a hostile server cannot make the client throw. */
	private static dev.villageheart.core.market.Reserves decodeReserves(ByteBuf buf) {
		int food = INT.decode(buf);
		int items = INT.decode(buf);
		return dev.villageheart.core.market.Reserves.clamped(food, items);
	}

	private static final StreamCodec<ByteBuf, List<VillagerRow>> VILLAGERS = VILLAGER_ROW.apply(ByteBufCodecs.list(HeartLimits.MAX_VILLAGERS));
	private static final StreamCodec<ByteBuf, List<JobChoice>> JOB_CHOICES = JOB_CHOICE.apply(ByteBufCodecs.list(HeartLimits.MAX_JOB_CHOICES));
	private static final StreamCodec<ByteBuf, List<BuildingRow>> BUILDINGS = BUILDING_ROW.apply(ByteBufCodecs.list(HeartLimits.MAX_BUILDINGS));

	public static final StreamCodec<ByteBuf, MemberRow> MEMBER_ROW = codec(
			buf -> new MemberRow(UUIDUtil.STREAM_CODEC.decode(buf), NAME.decode(buf), RANK.decode(buf), BOOL.decode(buf)),
			(buf, m) -> {
				UUIDUtil.STREAM_CODEC.encode(buf, m.id());
				NAME.encode(buf, m.name());
				RANK.encode(buf, m.rank());
				BOOL.encode(buf, m.online());
			});

	private static final StreamCodec<ByteBuf, List<MemberRow>> MEMBERS = MEMBER_ROW.apply(ByteBufCodecs.list(HeartLimits.MAX_MEMBERS));

	public static final StreamCodec<ByteBuf, HeartSnapshot> SNAPSHOT = codec(
			buf -> new HeartSnapshot(RANK.decode(buf), OVERVIEW.decode(buf), VILLAGERS.decode(buf), INT.decode(buf),
					JOB_CHOICES.decode(buf), BUILDINGS.decode(buf), INT.decode(buf), STOCK.decode(buf), MEMBERS.decode(buf),
					INT.decode(buf), BUILDS.decode(buf)),
			(buf, s) -> {
				RANK.encode(buf, s.viewerRank());
				OVERVIEW.encode(buf, s.overview());
				VILLAGERS.encode(buf, s.villagers());
				INT.encode(buf, s.moreVillagers());
				JOB_CHOICES.encode(buf, s.jobChoices());
				BUILDINGS.encode(buf, s.buildings());
				INT.encode(buf, s.moreBuildings());
				STOCK.encode(buf, s.stock());
				MEMBERS.encode(buf, s.members());
				INT.encode(buf, s.moreMembers());
				BUILDS.encode(buf, s.builds());
			});
}
