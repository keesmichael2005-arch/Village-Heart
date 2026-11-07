package dev.villageheart.core.worker;

import dev.villageheart.core.HeartLocation;

public sealed interface HireDecision {
	record Hired(SiteView site, HeartLocation jobBlock) implements HireDecision {}

	record Refused(HireRefusal reason) implements HireDecision {}
}
