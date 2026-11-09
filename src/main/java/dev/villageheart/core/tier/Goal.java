package dev.villageheart.core.tier;

public record Goal(GoalId id, int have, int need) {
	public boolean met() { return have >= need; }
}
