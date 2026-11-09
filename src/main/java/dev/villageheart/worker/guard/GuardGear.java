package dev.villageheart.worker.guard;

import dev.villageheart.core.Balance;
import dev.villageheart.core.guard.GearItem;
import dev.villageheart.core.guard.GearSlot;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.job.CampContainers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * A guard's gear (M18 3.7): reading the numbers of an item from its own components, listing what a guard may take (the Barracks chest and
 * the stockpile), equipping, handing in. Gear is worn on the villager with a guaranteed drop, so a guard that dies drops it intact.
 */
public final class GuardGear {
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Barracks chest slots promised to a guard for its trip: "chestPos#slot" -> guard (two guards never walk for one chestplate). */
	static final Map<String, UUID> CLAIMS = new HashMap<>();

	private GuardGear() {}

	public static void resetForTests() { CLAIMS.clear(); }

	/** One thing a guard could take: the planner's numbers and where it really is. */
	record Candidate(GearItem item, StackKey key, BlockPos chest, int chestSlot, Container container) {}

	static EquipmentSlot slotOf(GearSlot s) {
		return switch (s) {
			case WEAPON -> EquipmentSlot.MAINHAND;
			case HEAD -> EquipmentSlot.HEAD;
			case CHEST -> EquipmentSlot.CHEST;
			case LEGS -> EquipmentSlot.LEGS;
			case FEET -> EquipmentSlot.FEET;
		};
	}

	static GearSlot gearSlotOf(EquipmentSlot s) {
		return switch (s) {
			case MAINHAND -> GearSlot.WEAPON;
			case HEAD -> GearSlot.HEAD;
			case CHEST -> GearSlot.CHEST;
			case LEGS -> GearSlot.LEGS;
			case FEET -> GearSlot.FEET;
			default -> null;
		};
	}

	/** The gear numbers of a stack, or null when it is no guard gear (swords and head/chest/legs/feet armour only; BODY is for horses). */
	public static GearItem read(ItemStack s, GearItem.Source source, int order) {
		if (s.isEmpty()) return null;
		GearSlot slot = null;
		EquipmentSlot es = null;
		var eq = s.get(DataComponents.EQUIPPABLE);
		if (eq != null) {
			slot = gearSlotOf(eq.slot());
			es = eq.slot();
			if (slot == GearSlot.WEAPON) slot = null;
		}
		if (slot == null && s.is(ItemTags.SWORDS)) {
			slot = GearSlot.WEAPON;
			es = EquipmentSlot.MAINHAND;
		}
		if (slot == null) return null;
		double[] sum = new double[3]; // armor, toughness, attack damage
		s.forEachModifier(es, (attr, mod) -> {
			if (mod.operation() != AttributeModifier.Operation.ADD_VALUE) return;
			if (attr.is(Attributes.ARMOR)) sum[0] += mod.amount();
			else if (attr.is(Attributes.ARMOR_TOUGHNESS)) sum[1] += mod.amount();
			else if (attr.is(Attributes.ATTACK_DAMAGE)) sum[2] += mod.amount();
		});
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		return new GearItem(id, slot, sum[0], sum[1], sum[2], s.getDamageValue(), s.getMaxDamage(), source, order);
	}

	/** What the guard wears now (WORN). */
	static List<GearItem> worn(Villager g) {
		List<GearItem> out = new ArrayList<>();
		for (EquipmentSlot es : SLOTS) {
			GearItem it = read(g.getItemBySlot(es), GearItem.Source.WORN, 0);
			if (it != null && it.slot() == gearSlotOf(es)) out.add(it);
		}
		return out;
	}

	static boolean wearsAnything(Villager g) {
		for (EquipmentSlot es : SLOTS) if (!g.getItemBySlot(es).isEmpty()) return true;
		return false;
	}

	/** The building's chests (empty when unreadable now). */
	static List<CampContainers.Chest> chests(MinecraftServer server, Villager g) {
		WorkerData d = g.getAttached(WorkerAttachments.WORKER);
		if (d == null) return List.of();
		return CampContainers.of(server, d.building()).orElse(List.of());
	}

	/** Everything this guard could take: unclaimed Barracks chest slots, then the stockpile minus what others reserved. Orders are unique indexes. */
	static List<Candidate> available(ServerLevel level, Villager g) {
		MinecraftServer server = level.getServer();
		List<Candidate> out = new ArrayList<>();
		for (var chest : chests(server, g)) {
			Container c = chest.container();
			for (int i = 0; i < c.getContainerSize(); i++) {
				UUID claimed = CLAIMS.get(chest.pos().asLong() + "#" + i);
				if (claimed != null && !claimed.equals(g.getUUID())) continue;
				GearItem it = read(c.getItem(i), GearItem.Source.BARRACKS, out.size());
				if (it != null) out.add(new Candidate(it, null, chest.pos(), i, c));
			}
		}
		var reservations = Stockpiles.reservations(server);
		for (var total : Stockpiles.snapshot(server).totals()) {
			StackKey key = total.key();
			long others = reservations.reserved(key) - reservations.reservedBy(g.getUUID(), key);
			if (total.count() - others <= 0) continue;
			GearItem it = read(key.toStack(1), GearItem.Source.STOCKPILE, out.size());
			if (it != null) out.add(new Candidate(it, key, null, -1, null));
		}
		return out;
	}

	/** Puts the stack on the guard (guaranteed drop). The stack it replaces is returned (may be empty). */
	static ItemStack equip(Villager g, EquipmentSlot slot, ItemStack stack) {
		ItemStack old = g.getItemBySlot(slot).copy();
		g.setItemSlot(slot, stack);
		g.setGuaranteedDrop(slot);
		return old;
	}

	/** Moves a stack into the first free slot of the guard's building chests; true when it fit. */
	static boolean putInBarracks(MinecraftServer server, Villager g, ItemStack stack) {
		for (var chest : chests(server, g)) {
			Container c = chest.container();
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (c.getItem(i).isEmpty()) {
					c.setItem(i, stack.copy());
					c.setChanged();
					stack.setCount(0);
					return true;
				}
			}
		}
		return false;
	}

	/** Moves all equipment and carried gear out of the guard: Barracks chest, else Storage, else the ground. */
	public static void returnAll(ServerLevel level, Villager guard) {
		for (EquipmentSlot es : SLOTS) {
			ItemStack s = guard.getItemBySlot(es).copy();
			if (s.isEmpty()) continue;
			guard.setItemSlot(es, ItemStack.EMPTY);
			handIn(level, guard, s);
		}
		var inv = guard.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (read(s, GearItem.Source.WORN, 0) == null) continue;
			ItemStack copy = s.copy();
			inv.setItem(i, ItemStack.EMPTY);
			handIn(level, guard, copy);
		}
	}

	/** Barracks chest, else Storage, else dropped at the guard's feet. */
	static void handIn(ServerLevel level, Villager guard, ItemStack s) {
		if (putInBarracks(level.getServer(), guard, s)) return;
		Stockpiles.deposit(level, guard.blockPosition(), s);
		if (!s.isEmpty()) guard.spawnAtLocation(level, s);
	}

	static Optional<BlockPos> nothing() { return Optional.empty(); }

	static boolean lowGear(GearItem it) { return it.durabilityFraction() < Balance.GEAR_MIN_DURABILITY; }
}
