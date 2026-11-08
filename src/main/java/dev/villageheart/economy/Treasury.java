package dev.villageheart.economy;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.economy.Payroll;
import dev.villageheart.core.economy.Settlement;
import dev.villageheart.core.economy.TreasuryMath;
import dev.villageheart.core.heart.Permission;
import dev.villageheart.core.heart.Permissions;
import dev.villageheart.heart.HeartFacts;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSavedData;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The only writer of the treasury besides the wage run and the Heart's release (M12 plan 3.2 #1, 3.4). Every increase settles the
 * strikers at once (DESIGN section 4: "on strike until the treasury can pay"). Server thread only. Other milestones put income in through
 * {@link #deposit} (M22's merchant).
 *
 * <p>Write order, so emeralds are never lost or made: a deposit writes the treasury first and only then takes the items out of the hand
 * (the inventory path takes them first because the count is unknown before, and gives them back if the write fails).
 */
public final class Treasury {
	private Treasury() {}

	/** @param accepted emeralds that went in (less than offered when the treasury is nearly full) */
	public record DepositResult(int accepted, int settledWorkers, int treasuryAfter) {}

	/** What a right-click with emeralds did. */
	public enum HandResult {
		/** Deposited, or the treasury was full: the click is used up. */
		CONSUMED,
		/** Not allowed (a Visitor), not this village's Heart or no emeralds: the click falls through to the screen. */
		PASS
	}

	/** Puts {@code n} emeralds into the treasury (clamped at the cap) and pays the strikers it can. {@code by} only names the actor in the log. */
	public static DepositResult deposit(MinecraftServer server, int n, @Nullable ServerPlayer by) {
		if (n < 0) throw new IllegalArgumentException("deposit must be >= 0: " + n);
		VillageSavedData data = VillageClaims.data(server);
		Optional<VillageState> village = data.village();
		if (village.isEmpty()) return new DepositResult(0, 0, 0);
		int accepted = TreasuryMath.depositable(village.get().treasury(), n);
		if (accepted == 0) return new DepositResult(0, 0, village.get().treasury());
		int settled = writeAndSettle(server, village.get().treasury() + accepted);
		int after = data.village().orElseThrow().treasury();
		VillageHeart.LOG.info("[treasury] {} deposited {} emeralds, treasury {} -> {}, {} striking workers paid", by == null ? "-" : by.getGameProfile().name(),
				accepted, village.get().treasury(), after, settled);
		return new DepositResult(accepted, settled, after);
	}

	/** Writes a new treasury value (already clamped) and settles the strikers with it; returns how many were paid. Used by deposits and {@code treasury set}. */
	private static int writeAndSettle(MinecraftServer server, int newTreasury) {
		VillageSavedData data = VillageClaims.data(server);
		int left = newTreasury;
		int settled = 0;
		Optional<Payroll> payroll = data.payroll();
		if (payroll.isPresent()) {
			Settlement s = payroll.get().settle(newTreasury);
			left = s.treasuryAfter();
			settled = s.paid().size();
			if (settled > 0) data.setPayroll(s.payroll());
		}
		int write = left;
		data.update(v -> v.withTreasury(write));
		if (settled > 0) Wages.mirrorNeeds(server);
		changed(server);
		return settled;
	}

	/** An admin write ({@code /villageheart treasury set}): the value, clamped to the cap, then the strikers are settled too. */
	public static int set(MinecraftServer server, int value) {
		int clamped = Math.max(0, Math.min(value, dev.villageheart.core.Balance.MAX_TREASURY));
		if (VillageClaims.current(server).isEmpty()) return 0;
		writeAndSettle(server, clamped);
		return VillageClaims.current(server).orElseThrow().treasury();
	}

	private static void changed(MinecraftServer server) {
		HeartFacts.invalidate();
		HeartMenu.markAllDirty(server);
	}

	/**
	 * Right-click on the Heart with emeralds in the main hand: the whole stack goes in (as much as fits). A Visitor gets a message and the click
	 * falls through (the Overview opens).
	 */
	public static HandResult depositFromHand(ServerPlayer player, InteractionHand hand) {
		MinecraftServer server = player.level().getServer();
		ItemStack stack = player.getItemInHand(hand);
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty() || !stack.is(Items.EMERALD)) return HandResult.PASS;
		if (!Permissions.allows(village.get().rankOf(player.getUUID()), Permission.DEPOSIT_TREASURY)) {
			player.displayClientMessage(Component.translatable("message.villageheart.treasury.not_allowed"), true);
			return HandResult.PASS;
		}
		DepositResult r = deposit(server, stack.getCount(), player);
		if (r.accepted() == 0) {
			player.displayClientMessage(Component.translatable("message.villageheart.treasury.full"), true);
			return HandResult.CONSUMED;
		}
		stack.shrink(r.accepted()); // after the write
		player.containerMenu.broadcastChanges();
		depositedMessage(player, r);
		return HandResult.CONSUMED;
	}

	/** "Deposit all": every emerald the player carries (as many as fit). Returns what happened; {@code accepted == 0} when nothing went in. */
	public static DepositResult depositInventory(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return new DepositResult(0, 0, 0);
		int carried = player.getInventory().countItem(Items.EMERALD);
		if (carried == 0) {
			player.displayClientMessage(Component.translatable("message.villageheart.treasury.no_emeralds"), true);
			return new DepositResult(0, 0, village.get().treasury());
		}
		int room = TreasuryMath.depositable(village.get().treasury(), carried);
		if (room == 0) {
			player.displayClientMessage(Component.translatable("message.villageheart.treasury.full"), true);
			return new DepositResult(0, 0, village.get().treasury());
		}
		// never ask vanilla to remove 0: that only counts (the /clear path)
		int removed = player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.EMERALD), room, player.inventoryMenu.getCraftSlots());
		player.containerMenu.broadcastChanges();
		player.inventoryMenu.slotsChanged(player.getInventory());
		DepositResult r;
		try {
			r = deposit(server, removed, player);
		} catch (RuntimeException e) {
			player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, removed));
			throw e;
		}
		if (r.accepted() < removed) player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, removed - r.accepted()));
		depositedMessage(player, r);
		return r;
	}

	/** Takes up to {@code asked} emeralds out of the treasury and gives them to the player (what does not fit drops at their feet). Returns the number taken. */
	public static int withdraw(ServerPlayer player, int asked) {
		MinecraftServer server = player.level().getServer();
		VillageSavedData data = VillageClaims.data(server);
		Optional<VillageState> village = data.village();
		if (village.isEmpty()) return 0;
		int n = TreasuryMath.withdrawable(village.get().treasury(), asked);
		if (n == 0) {
			player.displayClientMessage(Component.translatable("message.villageheart.treasury.empty"), true);
			return 0;
		}
		data.update(v -> v.withdrawTreasury(n)); // the treasury first, the items after
		changed(server);
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, n));
		player.containerMenu.broadcastChanges();
		int after = village.get().treasury() - n;
		VillageHeart.LOG.info("[treasury] {} withdrew {} emeralds, treasury {} -> {}", player.getGameProfile().name(), n, village.get().treasury(), after);
		player.displayClientMessage(Component.translatable("message.villageheart.treasury.withdrawn", n, after), true);
		return n;
	}

	private static void depositedMessage(ServerPlayer player, DepositResult r) {
		player.displayClientMessage(r.settledWorkers() > 0
				? Component.translatable("message.villageheart.treasury.deposited_settled", r.accepted(), r.settledWorkers(), r.treasuryAfter())
				: Component.translatable("message.villageheart.treasury.deposited", r.accepted(), r.treasuryAfter()), true);
	}
}
