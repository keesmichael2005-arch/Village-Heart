package dev.villageheart.core.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Who has been paid for the current day and who is on strike (M12 plan 3.2 #6). Saved with the claim. {@code unpaid} is in hiring order and
 * stores each striker's wage as charged at dawn. Owed wages are never carried over: a new dawn replaces both collections.
 */
public record Payroll(long lastWageDay, Set<UUID> paidToday, List<Payee> unpaid) {
	public Payroll {
		Objects.requireNonNull(paidToday, "paidToday");
		Objects.requireNonNull(unpaid, "unpaid");
		paidToday = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(paidToday));
		unpaid = List.copyOf(unpaid);
		Set<UUID> seen = new LinkedHashSet<>();
		for (Payee p : unpaid) {
			if (!seen.add(p.villager())) throw new IllegalArgumentException("a villager is unpaid twice: " + p.villager());
			if (paidToday.contains(p.villager())) throw new IllegalArgumentException("a villager is both paid and unpaid: " + p.villager());
		}
	}

	public static Payroll initial(long lastWageDay) { return new Payroll(lastWageDay, Set.of(), List.of()); }

	/** After a wage run: both collections are replaced by that run. */
	public Payroll afterDawn(PayResult r) { return new Payroll(r.day(), new LinkedHashSet<>(r.paid()), r.unpaid()); }

	/** Wages are off (tests): the day passes, nobody is paid or charged. */
	public Payroll consumeDawn(long day) { return initial(day); }

	public boolean isUnpaid(UUID villager) {
		for (Payee p : unpaid) if (p.villager().equals(villager)) return true;
		return false;
	}

	/** The number of strikers that are still workers. */
	public int strikers(Collection<UUID> currentWorkers) {
		int n = 0;
		for (Payee p : unpaid) if (currentWorkers.contains(p.villager())) n++;
		return n;
	}

	/** Pays the strikers, at their stored wages, in hiring order, skipping on past one that does not fit. Called after every treasury increase. */
	public Settlement settle(int treasury) {
		if (treasury < 0) throw new IllegalArgumentException("treasury must be >= 0: " + treasury);
		int left = treasury;
		List<UUID> paid = new ArrayList<>();
		List<Payee> still = new ArrayList<>();
		Set<UUID> today = new LinkedHashSet<>(paidToday);
		for (Payee p : unpaid) {
			if (p.wage() <= left) {
				left -= p.wage();
				today.add(p.villager());
				paid.add(p.villager());
			} else {
				still.add(p);
			}
		}
		return new Settlement(new Payroll(lastWageDay, today, still), left, paid);
	}

	/**
	 * A hire is charged at once (closes the fire-at-dusk exploit): a villager already paid today costs nothing, a striker stays a striker,
	 * a new one is charged if it fits and else joins the strikers.
	 */
	public Settlement hire(int treasury, Payee newWorker) {
		if (paidToday.contains(newWorker.villager()) || isUnpaid(newWorker.villager())) return new Settlement(this, treasury, List.of());
		if (newWorker.wage() <= treasury) {
			Set<UUID> today = new LinkedHashSet<>(paidToday);
			today.add(newWorker.villager());
			return new Settlement(new Payroll(lastWageDay, today, unpaid), treasury - newWorker.wage(), List.of(newWorker.villager()));
		}
		List<Payee> next = new ArrayList<>(unpaid);
		next.add(newWorker);
		return new Settlement(new Payroll(lastWageDay, paidToday, next), treasury, List.of());
	}
}
