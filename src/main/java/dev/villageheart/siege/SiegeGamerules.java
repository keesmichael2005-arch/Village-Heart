package dev.villageheart.siege;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** The three M25 switches (docs/plans/M25.md 3.2 #2). All default to on. */
public final class SiegeGamerules {
	public static final GameRule<Boolean> SIEGE_DAMAGE = rule("siege_damage");
	public static final GameRule<Boolean> RANDOM_RAIDS = rule("random_raids");
	public static final GameRule<Boolean> SIEGES = rule("sieges");

	private SiegeGamerules() {}

	/** Touch the fields so registration happens before the registry freezes. */
	public static void register() {
		SIEGE_DAMAGE.getClass();
	}

	private static GameRule<Boolean> rule(String path) {
		return GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MOBS)
				.buildAndRegister(Identifier.fromNamespaceAndPath("villageheart", path));
	}
}
