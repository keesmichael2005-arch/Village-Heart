package dev.villageheart.worker.pocket;

import dev.villageheart.core.heart.JobLevels;
import dev.villageheart.core.job.PocketRules;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.heart.ModMenus;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.guard.GuardGear;
import dev.villageheart.worker.job.WorkerStatusText;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A read-only look at one hired villager: eight pocket slots, and a guard's worn sword and armour. */
public class PocketMenu extends AbstractContainerMenu {
	public static final StreamCodec<RegistryFriendlyByteBuf, PocketMenu.View> VIEW = StreamCodec.of(View::write, View::read);

	private final View view;

	public PocketMenu(int id, Inventory inventory, View view) {
		super(ModMenus.POCKET, id);
		this.view = view;
		SimpleContainer pocket = new SimpleContainer(PocketRules.SLOTS);
		for (int i = 0; i < PocketRules.SLOTS; i++) pocket.setItem(i, i < view.pocket().size() ? view.pocket().get(i).copy() : ItemStack.EMPTY);
		for (int i = 0; i < PocketRules.SLOTS; i++) {
			int x = 8 + i * 18;
			addSlot(new Locked(pocket, i, x, 78));
		}
		if (view.guard()) {
			SimpleContainer gear = new SimpleContainer(view.gear().size());
			for (int i = 0; i < view.gear().size(); i++) gear.setItem(i, view.gear().get(i).copy());
			int start = 8 + (PocketRules.SLOTS * 18 - view.gear().size() * 18) / 2;
			for (int i = 0; i < view.gear().size(); i++) addSlot(new Locked(gear, i, start + i * 18, 108));
		}
	}

	public View view() { return view; }

	@Override
	public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

	@Override
	public boolean stillValid(Player player) { return true; }

	@Override
	public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType type, Player player) {}

	/** What the card shows. Built on the server and sent as the menu's opening data. */
	public record View(String name, String jobId, String jobName, int level, int xp, int xpForNext, String activity,
			boolean fed, boolean housed, boolean paid, boolean guard, List<ItemStack> pocket, List<ItemStack> gear) {
		public View {
			pocket = List.copyOf(pocket);
			gear = List.copyOf(gear);
		}

		static void write(RegistryFriendlyByteBuf buf, View v) {
			ByteBufCodecs.STRING_UTF8.encode(buf, v.name);
			ByteBufCodecs.STRING_UTF8.encode(buf, v.jobId);
			ByteBufCodecs.STRING_UTF8.encode(buf, v.jobName);
			ByteBufCodecs.VAR_INT.encode(buf, v.level);
			ByteBufCodecs.VAR_INT.encode(buf, v.xp);
			ByteBufCodecs.VAR_INT.encode(buf, v.xpForNext);
			ByteBufCodecs.STRING_UTF8.encode(buf, v.activity);
			ByteBufCodecs.BOOL.encode(buf, v.fed);
			ByteBufCodecs.BOOL.encode(buf, v.housed);
			ByteBufCodecs.BOOL.encode(buf, v.paid);
			ByteBufCodecs.BOOL.encode(buf, v.guard);
			ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.pocket);
			ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.gear);
		}

		static View read(RegistryFriendlyByteBuf buf) {
			return new View(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.BOOL.decode(buf),
					ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
					ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
		}

		public static View of(Level level, Villager v) {
			WorkerData data = v.getAttached(WorkerAttachments.WORKER);
			JobType job = data == null ? JobType.FARMER : data.job();
			String activity = "Working";
			if (level instanceof net.minecraft.server.level.ServerLevel server && data != null) {
				var village = VillageClaims.current(server.getServer());
				var rec = village.flatMap(s -> s.worker(v.getUUID()));
				if (rec.isPresent()) activity = WorkerStatusText.of(Workers.status(server.getServer(), rec.get()), job).getString();
			}
			List<ItemStack> pocket = new ArrayList<>();
			var inv = v.getInventory();
			for (int i = 0; i < PocketRules.SLOTS; i++) pocket.add(i < inv.getContainerSize() ? inv.getItem(i).copy() : ItemStack.EMPTY);
			List<ItemStack> gear = new ArrayList<>();
			boolean guard = job == JobType.GUARD;
			if (guard) {
				for (EquipmentSlot slot : GuardGear.SLOTS) gear.add(v.getItemBySlot(slot).copy());
			}
			int lvl = data == null ? 1 : data.level();
			int xp = data == null ? 0 : data.xp();
			return new View(v.getName().getString(), job.id(), job.displayName(), lvl, xp, JobLevels.xpForNext(lvl), activity,
					data == null || data.needs().fed(), data == null || data.needs().housed(), data == null || data.needs().paid(),
					guard, pocket, gear);
		}
	}

	private static final class Locked extends Slot {
		Locked(SimpleContainer container, int slot, int x, int y) { super(container, slot, x, y); }

		@Override
		public boolean mayPickup(Player player) { return false; }

		@Override
		public boolean mayPlace(ItemStack stack) { return false; }
	}
}
