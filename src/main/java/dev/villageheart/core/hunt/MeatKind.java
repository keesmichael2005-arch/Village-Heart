package dev.villageheart.core.hunt;

/** The four raw meats a hunter brings, in the pinned table order. */
public enum MeatKind {
	BEEF("minecraft:beef"), PORKCHOP("minecraft:porkchop"), MUTTON("minecraft:mutton"), CHICKEN("minecraft:chicken");

	private final String itemId;

	MeatKind(String itemId) { this.itemId = itemId; }

	public String itemId() { return itemId; }
}
