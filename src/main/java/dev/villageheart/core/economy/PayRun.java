package dev.villageheart.core.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The pay rule (M12 plan 3.2 #5): in hiring order; a worker whose wage fits what is left is paid, otherwise it strikes and the walk goes on. */
public final class PayRun {
	private PayRun() {}

	public static PayResult run(long day, int treasury, List<Payee> inHiringOrder) {
		if (treasury < 0) throw new IllegalArgumentException("treasury must be >= 0: " + treasury);
		int left = treasury;
		int bill = 0;
		List<UUID> paid = new ArrayList<>();
		List<Payee> unpaid = new ArrayList<>();
		for (Payee p : inHiringOrder) {
			bill = Math.addExact(bill, p.wage());
			if (p.wage() <= left) {
				left -= p.wage();
				paid.add(p.villager());
			} else {
				unpaid.add(p);
			}
		}
		return new PayResult(day, treasury, left, treasury - left, bill, paid, unpaid);
	}
}
