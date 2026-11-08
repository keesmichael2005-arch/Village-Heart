package dev.villageheart.building;

import dev.villageheart.core.Balance;
import dev.villageheart.core.building.AreaCounter;
import dev.villageheart.core.building.AreaCounter.AreaCount;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingRules.SiteFacts;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.ShaftScanner;
import dev.villageheart.core.building.ShaftScanner.ShaftSurvey;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.room.LevelBlockView;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Glue for the outdoor site surveys: the pure scanners run over a {@link LevelBlockView}; armor stands are entities. */
final class SiteSurveys {
	private SiteSurveys() {}

	/** @param notLoaded the survey reached an unloaded area (no verdict this time) */
	record Survey(SiteFacts facts, @Nullable Pos notLoaded, @Nullable ShaftScanner.ShaftDepth depth) {
		Survey(SiteFacts facts, @Nullable Pos notLoaded) { this(facts, notLoaded, null); }
	}

	static Survey survey(ServerLevel level, BuildingType type, Pos here, List<RoomScan> homeRooms) {
		LevelBlockView view = new LevelBlockView(level);
		switch (type) {
			case MINESHAFT -> {
				ShaftSurvey s = ShaftScanner.scan(view, here);
				return new Survey(new SiteFacts(s.depth(), s.rails(), s.lights(), 0, 0, 0, false), s.notLoaded(), s.shaftDepth());
			}
			case HUNTING_GROUND -> {
				AreaCount c = AreaCounter.count(view, here, Balance.HUNTING_GROUND_RADIUS, BuildingRequirements.NATURAL);
				boolean inside = homeRooms.stream().anyMatch(r -> r.occupies(here));
				return new Survey(new SiteFacts(0, 0, 0, c.n(), 0, 0, inside), c.notLoaded());
			}
			case TRAINING_YARD -> {
				AreaCount c = AreaCounter.count(view, here, Balance.TRAINING_YARD_RADIUS, BuildingRequirements.TARGET);
				return new Survey(new SiteFacts(0, 0, 0, 0, c.n(), armorStands(level, here), false), c.notLoaded());
			}
			default -> throw new IllegalArgumentException(type + " is not an outdoor site");
		}
	}

	/** Armor stands within the yard radius of the marker block centre. */
	static int armorStands(ServerLevel level, Pos here) {
		BlockPos pos = new BlockPos(here.x(), here.y(), here.z());
		Vec3 centre = Vec3.atCenterOf(pos);
		double r2 = (double) Balance.TRAINING_YARD_RADIUS * Balance.TRAINING_YARD_RADIUS;
		return level.getEntitiesOfClass(ArmorStand.class, new AABB(pos).inflate(Balance.TRAINING_YARD_RADIUS),
				e -> e.distanceToSqr(centre) <= r2).size();
	}
}
