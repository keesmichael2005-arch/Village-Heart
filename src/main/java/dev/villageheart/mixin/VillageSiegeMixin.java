package dev.villageheart.mixin;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.siege.VanillaSiege;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.VillageSiege;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla sieges skip a player inside the claim, and a spawn candidate inside the claim is not a village
 * (docs/plans/M25.md 3.12). A player just outside cannot pull zombies through the walls. The suppression stays
 * when {@code villageheart:sieges} is off. A village outside the claim still sieges.
 */
@Mixin(VillageSiege.class)
public class VillageSiegeMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int forceTonight(RandomSource random, int bound) {
		if (bound == 10 && VanillaSiege.tonight()) return 0;
		return random.nextInt(bound);
	}

	@Redirect(method = {"tryToSetupSiege", "findRandomSpawnPos"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isVillage(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean outsideClaim(ServerLevel level, BlockPos pos) {
		if (claimed(level, pos)) {
			VanillaSiege.noteSuppressed();
			return false;
		}
		return level.isVillage(pos);
	}

	private static boolean claimed(ServerLevel level, BlockPos pos) {
		var state = VillageClaims.current(level.getServer());
		if (state.isEmpty()) return false;
		HeartLocation heart = state.get().heart();
		return ClaimGeometry.inClaim(heart, VillageClaims.locate(level, pos));
	}
}
