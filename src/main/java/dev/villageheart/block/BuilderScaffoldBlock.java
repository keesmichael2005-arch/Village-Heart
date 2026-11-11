package dev.villageheart.block;

import net.minecraft.world.level.block.Block;

/** A builder's own scaffold. It is not an item, and it has no loot table, so breaking it drops nothing. */
public class BuilderScaffoldBlock extends Block {
	public BuilderScaffoldBlock(Properties properties) { super(properties); }
}
