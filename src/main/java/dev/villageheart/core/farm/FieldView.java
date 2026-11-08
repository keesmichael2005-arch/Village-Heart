package dev.villageheart.core.farm;

/** Read access to the world's crop cells; {@code y} is the crop cell (farmland y + 1). A read costs up to two block reads. */
public interface FieldView {
	FieldCell cell(int x, int y, int z);
}
