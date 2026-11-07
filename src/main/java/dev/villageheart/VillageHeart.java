package dev.villageheart;

import dev.villageheart.block.ModBlocks;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageHeart implements ModInitializer {
	public static final String MOD_ID = "villageheart";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		LOG.info("Village Heart loaded");
	}
}
