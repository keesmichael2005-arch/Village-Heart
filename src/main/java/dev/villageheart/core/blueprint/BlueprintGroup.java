package dev.villageheart.core.blueprint;

/** The three groups in the Blueprint Book. A plan belongs to exactly one. */
public enum BlueprintGroup {
	BUILDINGS("buildings", "Buildings"),
	PRODUCTION("production", "Production"),
	DEFENCE("defence", "Defence");

	private final String id;
	private final String label;

	BlueprintGroup(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public String id() { return id; }
	public String label() { return label; }
}
