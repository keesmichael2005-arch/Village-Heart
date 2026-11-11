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
		dev.villageheart.build.ModComponents.register();
		ModBlocks.register();
		dev.villageheart.item.ModItems.register();
		dev.villageheart.block.MarkerBlocks.register();
		dev.villageheart.block.JobBlocks.register();
		dev.villageheart.item.ModCreativeTab.register();
		dev.villageheart.worker.ModProfessions.register();
		dev.villageheart.worker.WorkerAttachments.register();
		dev.villageheart.food.DietAttachments.register();
		dev.villageheart.worker.WorkerBrain.register();
		dev.villageheart.worker.guard.Guards.register();
		dev.villageheart.worker.WorkerCommands.register();
		dev.villageheart.market.MarketCommands.register();
		dev.villageheart.room.RoomCells.register();
		dev.villageheart.heart.ModMenus.register();
		dev.villageheart.heart.net.HeartNetwork.register();
		dev.villageheart.build.net.BuildNetwork.register();
		dev.villageheart.rank.ContainerProtection.register();
		dev.villageheart.rank.Members.register();
		dev.villageheart.rank.RankCommands.register();
		dev.villageheart.build.BuildCommands.register();
		dev.villageheart.world.VillageTicker.register();
		dev.villageheart.world.VillageSystems.register("villageheart:buildings", dev.villageheart.building.BuildingSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:job_reservations", dev.villageheart.worker.JobSiteReservations.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:workers", dev.villageheart.worker.WorkerSystem.INSTANCE);
		dev.villageheart.death.DeathLogSystem.register();
		dev.villageheart.worker.WorkerSystem.register();
		dev.villageheart.world.VillageSystems.register("villageheart:lumber", dev.villageheart.worker.lumber.LumberSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:stockpile", dev.villageheart.stock.StockpileSystem.INSTANCE);
		dev.villageheart.worker.job.AwayTrips.register();
		dev.villageheart.world.VillageSystems.register("villageheart:miner", dev.villageheart.worker.mine.MinerSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:hunter", dev.villageheart.worker.hunt.HunterSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:self_craft", dev.villageheart.worker.gear.SelfCraftSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:builds", dev.villageheart.build.BuildSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:dinner", dev.villageheart.food.DinnerSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:needs", dev.villageheart.needs.NeedsSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:wages", dev.villageheart.economy.WageSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:kitchen", dev.villageheart.worker.cook.KitchenSystem.INSTANCE);
		dev.villageheart.tier.Tiers.register();
		dev.villageheart.siege.SiegeSystem.register();
		dev.villageheart.world.VillageSystems.register("villageheart:siege", dev.villageheart.siege.SiegeSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:defences", dev.villageheart.defence.DefenceSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:guards", dev.villageheart.worker.guard.GuardSystem.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:gates", dev.villageheart.defence.GateKeeper.INSTANCE);
		dev.villageheart.siege.SiegeGamerules.register();
		dev.villageheart.siege.SiegeGoals.register();
		dev.villageheart.world.VillageSystems.register("villageheart:raids", dev.villageheart.raid.VillageRaids.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:threats", dev.villageheart.raid.ThreatScheduler.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:unhired_shelter", dev.villageheart.worker.shelter.UnhiredShelter.INSTANCE);
		dev.villageheart.world.VillageSystems.register("villageheart:stuck_raiders", dev.villageheart.raid.StuckRaiderSystem.INSTANCE);
		dev.villageheart.raid.VillageThreats.install();
		dev.villageheart.raid.RaiderKills.register();
		LOG.info("Village Heart loaded");
	}
}
