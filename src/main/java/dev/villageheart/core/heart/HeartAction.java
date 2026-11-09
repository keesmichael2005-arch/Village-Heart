package dev.villageheart.core.heart;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A change a player asks for through the Heart screen. M18 adds SetGuardShift. */
public sealed interface HeartAction permits HeartAction.Assign, HeartAction.Fire, HeartAction.Recheck, HeartAction.SetRank,
		HeartAction.Transfer, HeartAction.Deposit, HeartAction.Withdraw, HeartAction.SetReserves {
	Permission permission();

	/** Checked by the gate after the village checks and before the permission: a value no honest client sends. */
	default Optional<Rejection> malformed() { return Optional.empty(); }

	record Assign(UUID villager, String jobId, Optional<HeartLocation> building) implements HeartAction {
		public Assign {
			Objects.requireNonNull(villager, "villager");
			Objects.requireNonNull(jobId, "jobId");
			Objects.requireNonNull(building, "building");
		}

		@Override public Permission permission() { return Permission.HIRE; }
	}

	record Fire(UUID villager) implements HeartAction {
		public Fire { Objects.requireNonNull(villager, "villager"); }

		@Override public Permission permission() { return Permission.FIRE; }
	}

	record Recheck(HeartLocation marker) implements HeartAction {
		public Recheck { Objects.requireNonNull(marker, "marker"); }

		@Override public Permission permission() { return Permission.RECHECK_BUILDING; }
	}

	/** Put every emerald the player carries into the treasury (M12). */
	record Deposit() implements HeartAction {
		@Override public Permission permission() { return Permission.DEPOSIT_TREASURY; }
	}

	/** Take {@code amount} emeralds out of the treasury (M12). Any int decodes; only 1..{@link Balance#MAX_WITHDRAW} pass the gate. */
	record Withdraw(int amount) implements HeartAction {
		@Override public Permission permission() { return Permission.WITHDRAW_TREASURY; }

		@Override public Optional<Rejection> malformed() {
			return amount >= 1 && amount <= Balance.MAX_WITHDRAW ? Optional.empty() : Optional.of(Rejection.BAD_AMOUNT);
		}
	}

	/** Give {@code player} a rank. {@code rankId} is a {@link Rank} ordinal as sent by the client: any int decodes, only 1..3 pass. */
	record SetRank(UUID player, int rankId) implements HeartAction {
		public SetRank { Objects.requireNonNull(player, "player"); }

		@Override public Permission permission() { return Permission.MANAGE_RANKS; }

		@Override public Optional<Rejection> malformed() {
			boolean ok = rankId >= Rank.STEWARD.ordinal() && rankId <= Rank.VISITOR.ordinal();
			return ok ? Optional.empty() : Optional.of(Rejection.BAD_RANK);
		}

		/** Only valid after {@link #malformed()} passed. */
		public Rank rank() { return Rank.values()[rankId]; }
	}

	/** Give the village to {@code player}; {@code expectedRank} is the rank the screen showed for them (a Rank ordinal). */
	record Transfer(UUID player, int expectedRank) implements HeartAction {
		public Transfer { Objects.requireNonNull(player, "player"); }

		@Override public Permission permission() { return Permission.TRANSFER_OWNERSHIP; }

		@Override public Optional<Rejection> malformed() {
			boolean ok = expectedRank >= 0 && expectedRank < Rank.values().length;
			return ok ? Optional.empty() : Optional.of(Rejection.BAD_RANK);
		}

		/** Only valid after {@link #malformed()} passed. */
		public Rank expected() { return Rank.values()[expectedRank]; }
	}

	/** Set what the market keeps (M22): days of food and the stock of every other item. Any ints decode; only values in range pass the gate. */
	record SetReserves(int foodDays, int itemReserve) implements HeartAction {
		@Override public Permission permission() { return Permission.SET_RESERVES; }

		@Override public Optional<Rejection> malformed() {
			return dev.villageheart.core.market.Reserves.valid(foodDays, itemReserve) ? Optional.empty() : Optional.of(Rejection.BAD_RESERVE);
		}

		/** Only valid after {@link #malformed()} passed. */
		public dev.villageheart.core.market.Reserves reserves() { return new dev.villageheart.core.market.Reserves(foodDays, itemReserve); }
	}
}
