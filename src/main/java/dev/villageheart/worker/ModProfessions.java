package dev.villageheart.worker;

import com.google.common.collect.ImmutableSet;
import dev.villageheart.VillageHeart;
import dev.villageheart.core.worker.JobType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * One villager profession per {@link JobType}, {@code villageheart:<id>}. They have no job-site POI (both predicates are always
 * false), so vanilla never assigns or acquires them; they have no trades (no entry in {@code VillagerTrades.TRADES}), so a worker
 * never opens a trade screen. The mod alone assigns them, in {@code Workers.hire}.
 */
public final class ModProfessions {
	private static final Map<JobType, ResourceKey<VillagerProfession>> KEYS = new EnumMap<>(JobType.class);

	private ModProfessions() {}

	public static void register() {
		for (JobType job : JobType.values()) {
			ResourceKey<VillagerProfession> key = key(job);
			Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, key, new VillagerProfession(
					Component.translatable("entity." + VillageHeart.MOD_ID + ".villager." + job.id()),
					PoiType.NONE, PoiType.NONE, ImmutableSet.of(), ImmutableSet.of(), null));
		}
	}

	public static ResourceKey<VillagerProfession> key(JobType job) {
		return KEYS.computeIfAbsent(job, j -> ResourceKey.create(Registries.VILLAGER_PROFESSION,
				Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, j.id())));
	}

	public static Holder<VillagerProfession> holder(JobType job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(key(job));
	}

	/** True for any {@code villageheart:} profession. */
	public static boolean isModProfession(Holder<VillagerProfession> profession) {
		return profession.unwrapKey().map(k -> k.identifier().getNamespace().equals(VillageHeart.MOD_ID)).orElse(false);
	}
}
