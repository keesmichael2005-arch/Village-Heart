package dev.villageheart.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.worker.JobType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M26-R: the merchant never strikes, every other job may. */
class StrikeRulesTest {
	@Test
	void onlyTheMerchantIsExempt() {
		assertFalse(StrikeRules.mayStrike(JobType.MERCHANT));
		for (JobType j : JobType.values()) if (j != JobType.MERCHANT) assertTrue(StrikeRules.mayStrike(j), j.id());
	}

	/** The spiral the rule breaks: with an empty treasury everyone is unpaid; the merchant's first sales then pay the wages in hiring order, itself first. */
	@Test
	void anUnpaidMerchantsFirstSalesSettleTheWagesInHiringOrder() {
		UUID merchant = UUID.randomUUID(), farmer = UUID.randomUUID(), miner = UUID.randomUUID();
		PayResult r = PayRun.run(1, 0, List.of(new Payee(merchant, 1), new Payee(farmer, 1), new Payee(miner, 2)));
		assertEquals(3, r.unpaid().size(), "treasury 0: all three unpaid, the merchant too");
		Payroll p = Payroll.initial(0).afterDawn(r);
		Settlement first = p.settle(1); // the merchant's first sale
		assertEquals(List.of(merchant), first.paid(), "the merchant is paid first");
		Settlement second = first.payroll().settle(1); // its second sale
		assertEquals(List.of(farmer), second.paid());
		Settlement third = second.payroll().settle(2);
		assertEquals(List.of(miner), third.paid());
		assertTrue(third.payroll().unpaid().isEmpty(), "three sales' worth of emeralds clear the payroll");
	}
}
