package dev.villageheart.core.room;

/** Group content keys (decided by the glue adapter). Block ids are the other keys, e.g. "minecraft:chest". */
public final class RoomContents {
	private RoomContents() {}

	/** Any bed, counted on its HEAD half only. */
	public static final String BED = "#bed";
	/** Any door, counted on its LOWER half only. */
	public static final String DOOR = "#door";
	/** Any ChestBlock (chest, trapped chest, copper chests); not the ender chest. */
	public static final String CHEST = "#chest";
	/** Light emission above zero at scan time. */
	public static final String LIGHT = "#light";
}
