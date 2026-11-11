package dev.villageheart.core.gear;

import dev.villageheart.core.smith.SmithKind;
import dev.villageheart.core.smith.SmithRecipe;
import dev.villageheart.core.smith.SmithRecipes;
import dev.villageheart.core.smith.SmithSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Which tier a worker makes (M19a) and which tier the smith makes (M19). */
public final class TierChoice {
	private TierChoice() {}

	/** Stone if affordable, else wooden, else empty. Never copper or better, whatever the stockpile holds. Logs count as planks. */
	public static Optional<CraftPlan> selfMade(GearNeed need, Materials m) {
		if (need.isArmour()) return Optional.empty();
		Optional<CraftPlan> stone = plan(ToolRecipes.of(need, CraftTier.STONE), m);
		if (stone.isPresent()) return stone;
		return plan(ToolRecipes.of(need, CraftTier.WOODEN), m);
	}

	/** How many planks (counting a log as 4) the cheapest, wooden, recipe is short of; zero when it is affordable. */
	public static Deficit deficit(GearNeed need, Materials m) {
		ToolRecipe r = ToolRecipes.of(need, CraftTier.WOODEN);
		int batches = m.total(MaterialRole.STICK) >= r.sticks() ? 0 : batchesFor(r.sticks());
		long planksNeeded = r.head() + (long) batches * ToolRecipes.PLANKS_PER_STICK_BATCH;
		long have = m.total(MaterialRole.PLANKS) + m.total(MaterialRole.LOGS) * ToolRecipes.PLANKS_PER_LOG;
		return new Deficit(need, "wood", (int) Math.max(0, planksNeeded - have));
	}

	/**
	 * The smith's choice: the best tier this demand can afford right now, or empty. Logs are ignored (they are fuel, not a smith material).
	 * {@code smeltableUnits} is how many raw ore the fuel (after the cook's reserve) can still smelt. A diamond piece also has to clear the
	 * reserve and the "everyone else is already iron" rule.
	 */
	public static Optional<GearOrder> best(GearDemand demand, Materials materials, int smeltableUnits, VillageGear gear, SmithSettings settings) {
		Materials smith = stripLogs(materials);
		for (CraftTier tier : order(demand)) {
			if (tier == CraftTier.DIAMOND && !diamondAllowed(demand, smith, gear, settings)) continue;
			Optional<GearOrder> order = orderFor(demand, tier, smith, smeltableUnits);
			if (order.isPresent()) return order;
		}
		return Optional.empty();
	}

	/**
	 * The cheapest material this demand is short of, ignoring logs and smelting. A best-tier demand (axe, pickaxe, guard, armour) is priced
	 * at its last resort (wood or leather); a pace-independent tool is priced at stone, the tier it prefers.
	 */
	public static Deficit cheapestShort(GearDemand demand, Materials materials) {
		Materials smith = stripLogs(materials);
		List<CraftTier> tiers = order(demand);
		CraftTier cheap = demand.key().need().isArmour() || bestTier(demand) ? tiers.get(tiers.size() - 1) : tiers.get(0);
		return shortOf(demand.key().need(), cheap, smith);
	}

	/**
	 * Materials for one recipe. The head comes from {@link CraftTier#headRole()}: planks (and, for a self-crafter, logs) or the role's items.
	 * Sticks come from Storage when there are enough, otherwise from a whole stick batch, never a mix.
	 */
	static Optional<CraftPlan> plan(ToolRecipe r, Materials m) {
		boolean plankHead = r.tier().headRole() == MaterialRole.PLANKS;
		List<ItemCount> take = new ArrayList<>();
		List<ItemCount> leftovers = new ArrayList<>();
		long planksNeeded = 0;
		if (plankHead) planksNeeded += r.head();
		else if (r.head() > 0) {
			Optional<List<ItemCount>> head = takeFrom(m, r.tier().headRole(), r.head());
			if (head.isEmpty()) return Optional.empty();
			take.addAll(head.get());
		}
		int batches = 0;
		if (r.sticks() <= 0) {
			// armour, or a tool that somehow needs no handle
		} else if (m.total(MaterialRole.STICK) >= r.sticks()) {
			take.addAll(takeFrom(m, MaterialRole.STICK, r.sticks()).orElseThrow());
		} else {
			batches = batchesFor(r.sticks());
			planksNeeded += (long) batches * ToolRecipes.PLANKS_PER_STICK_BATCH;
			int made = batches * ToolRecipes.STICKS_PER_BATCH;
			if (made > r.sticks()) leftovers.add(new ItemCount(ToolRecipes.STICK, made - r.sticks()));
		}
		int logs = 0;
		if (planksNeeded > 0) {
			long fromPlanks = Math.min(planksNeeded, m.total(MaterialRole.PLANKS));
			if (fromPlanks > 0) take.addAll(takeFrom(m, MaterialRole.PLANKS, fromPlanks).orElseThrow());
			long missing = planksNeeded - fromPlanks;
			if (missing > 0) {
				long logsNeeded = (missing + ToolRecipes.PLANKS_PER_LOG - 1) / ToolRecipes.PLANKS_PER_LOG;
				Optional<List<ItemCount>> logTake = takeFrom(m, MaterialRole.LOGS, logsNeeded);
				if (logTake.isEmpty()) return Optional.empty();
				take.addAll(logTake.get());
				logs = (int) logsNeeded;
				long extra = logsNeeded * ToolRecipes.PLANKS_PER_LOG - missing;
				if (extra > 0) {
					String last = logTake.get().get(logTake.get().size() - 1).id();
					Optional<String> planks = PlankTable.planksOf(last);
					if (planks.isEmpty()) return Optional.empty();
					leftovers.add(new ItemCount(planks.get(), (int) extra));
				}
			}
		}
		return Optional.of(new CraftPlan(r, take, batches, logs, leftovers));
	}

	private static int batchesFor(int sticks) { return (sticks + ToolRecipes.STICKS_PER_BATCH - 1) / ToolRecipes.STICKS_PER_BATCH; }

	/** Takes {@code n} from the items of a role in list order; empty when fewer are available. Zero takes nothing. */
	private static Optional<List<ItemCount>> takeFrom(Materials m, MaterialRole role, long n) {
		List<ItemCount> out = new ArrayList<>();
		long left = n;
		for (StockItem i : m.items()) {
			if (left <= 0) break;
			if (i.role() != role || i.available() <= 0) continue;
			int t = (int) Math.min(left, i.available());
			out.add(new ItemCount(i.id(), t));
			left -= t;
		}
		return left > 0 ? Optional.empty() : Optional.of(out);
	}

	/** Best tier first. Pace-independent tools (hoe, hunter sword) prefer stone, then wood, and never diamond. */
	private static List<CraftTier> order(GearDemand d) {
		if (d.key().need().isArmour()) return List.of(CraftTier.DIAMOND, CraftTier.IRON, CraftTier.COPPER, CraftTier.LEATHER);
		if (bestTier(d)) return List.of(CraftTier.DIAMOND, CraftTier.IRON, CraftTier.COPPER, CraftTier.STONE, CraftTier.WOODEN);
		return List.of(CraftTier.STONE, CraftTier.WOODEN, CraftTier.COPPER, CraftTier.IRON);
	}

	private static boolean bestTier(GearDemand d) {
		return d.paceDependsOnTier() || "guard".equals(d.job()) || d.urgency() == Urgency.U2_GUARD_WEAPON || d.urgency() == Urgency.U3_GUARD_ARMOUR;
	}

	/**
	 * Diamonds left after the cost must be strictly more than the reserve, so 8 diamonds and a cost of 3 with reserve 5 stays iron
	 * (the pinned example; the rule sentence says "at least"). Everyone else's key must already be iron-or-better. Only an axe, a
	 * pickaxe or guard gear may be diamond.
	 */
	private static boolean diamondAllowed(GearDemand d, Materials m, VillageGear gear, SmithSettings settings) {
		GearNeed need = d.key().need();
		boolean kind = need == GearNeed.AXE || need == GearNeed.PICKAXE || "guard".equals(d.job()) || need.isArmour()
				|| d.urgency() == Urgency.U2_GUARD_WEAPON || d.urgency() == Urgency.U3_GUARD_ARMOUR;
		if (!kind) return false;
		for (var e : gear.ironOrBetter().entrySet()) {
			if (e.getKey().equals(d.key())) continue;
			if (!e.getValue()) return false;
		}
		int cost = headCount(need);
		long left = m.total(MaterialRole.DIAMOND) - cost;
		return left > settings.diamondReserve();
	}

	private static int headCount(GearNeed need) {
		if (need.isArmour()) return switch (need) {
			case HEAD -> 5;
			case CHEST -> 8;
			case LEGS -> 7;
			case FEET -> 4;
			default -> 0;
		};
		return need == GearNeed.AXE || need == GearNeed.PICKAXE ? 3 : 2;
	}

	private static Optional<GearOrder> orderFor(GearDemand demand, CraftTier tier, Materials smith, int smeltableUnits) {
		Optional<SmithRecipe> recipe = SmithRecipes.craft(demand.key().need(), tier);
		if (recipe.isEmpty() || recipe.get().shape().isEmpty()) return Optional.empty();
		ToolRecipe shape = recipe.get().shape().get();
		MaterialRole raw = rawOf(tier.headRole());
		int canSmelt = 0;
		Materials boosted = smith;
		if (raw != null && smeltableUnits > 0) {
			canSmelt = (int) Math.min(smeltableUnits, smith.total(raw));
			if (canSmelt > 0) {
				List<StockItem> items = new ArrayList<>(smith.items());
				items.add(new StockItem(ingotId(tier.headRole()), tier.headRole(), canSmelt));
				boosted = new Materials(items);
			}
		}
		Optional<CraftPlan> planned = plan(shape, boosted);
		if (planned.isEmpty() || planned.get().logs() > 0) return Optional.empty();
		CraftPlan made = planned.get();
		int ingots = countId(made.take(), ingotId(tier.headRole()));
		int real = (int) Math.min(ingots, smith.total(tier.headRole()));
		int smelted = ingots - real;
		if (smelted > canSmelt) return Optional.empty();
		List<ItemCount> take = rewriteHead(made.take(), tier, smith, real, smelted);
		if (take == null) return Optional.empty();
		CraftPlan plan = new CraftPlan(shape, take, made.stickBatches(), 0, made.leftovers());
		List<SmithRecipe> pre = new ArrayList<>();
		if (smelted > 0) {
			SmithRecipe smelt = smeltRecipe(tier).orElse(null);
			if (smelt == null) return Optional.empty();
			for (int i = 0; i < smelted; i++) pre.add(smelt);
		}
		if (plan.stickBatches() > 0) {
			SmithRecipe sticks = SmithRecipes.byOutput("minecraft:stick").orElseThrow();
			for (int i = 0; i < plan.stickBatches(); i++) pre.add(sticks);
		}
		return Optional.of(new GearOrder(demand, recipe.get(), plan, pre));
	}

	private static List<ItemCount> rewriteHead(List<ItemCount> take, CraftTier tier, Materials real, int realCount, int smelted) {
		String id = ingotId(tier.headRole());
		if (id == null) return take;
		List<ItemCount> out = new ArrayList<>();
		boolean placed = false;
		for (ItemCount c : take) {
			if (c.id().equals(id)) {
				if (!placed) {
					if (realCount > 0) {
						Optional<List<ItemCount>> got = takeFrom(real, tier.headRole(), realCount);
						if (got.isEmpty()) return null;
						out.addAll(got.get());
					}
					if (smelted > 0) {
						MaterialRole raw = rawOf(tier.headRole());
						Optional<List<ItemCount>> got = takeFrom(real, raw, smelted);
						if (got.isEmpty()) return null;
						out.addAll(got.get());
					}
					placed = true;
				}
			} else {
				out.add(c);
			}
		}
		return out;
	}

	private static int countId(List<ItemCount> take, String id) {
		if (id == null) return 0;
		int n = 0;
		for (ItemCount c : take) if (c.id().equals(id)) n += c.count();
		return n;
	}

	private static Optional<SmithRecipe> smeltRecipe(CraftTier tier) {
		String id = ingotId(tier.headRole());
		if (id == null) return Optional.empty();
		return SmithRecipes.byOutput(id).filter(r -> r.kind() == SmithKind.SMELT);
	}

	private static MaterialRole rawOf(MaterialRole head) {
		return switch (head) {
			case COPPER_INGOT -> MaterialRole.RAW_COPPER;
			case IRON_INGOT -> MaterialRole.RAW_IRON;
			default -> null;
		};
	}

	private static String ingotId(MaterialRole role) {
		return switch (role) {
			case COPPER_INGOT -> "minecraft:copper_ingot";
			case IRON_INGOT -> "minecraft:iron_ingot";
			case DIAMOND -> "minecraft:diamond";
			case LEATHER -> "minecraft:leather";
			default -> null;
		};
	}

	private static Materials stripLogs(Materials m) {
		List<StockItem> kept = new ArrayList<>();
		for (StockItem i : m.items()) if (i.role() != MaterialRole.LOGS) kept.add(i);
		return new Materials(kept);
	}

	private static Deficit shortOf(GearNeed need, CraftTier tier, Materials m) {
		String name = switch (tier) {
			case WOODEN -> "planks";
			case STONE -> "cobblestone";
			case COPPER -> "copper";
			case IRON -> "iron";
			case DIAMOND -> "diamond";
			case LEATHER -> "leather";
		};
		if (tier == CraftTier.WOODEN) {
			int sticks = need == GearNeed.SWORD ? 1 : need.isArmour() ? 0 : 2;
			int batches = m.total(MaterialRole.STICK) >= sticks ? 0 : batchesFor(sticks);
			long needPlanks = headCount(need) + (long) batches * ToolRecipes.PLANKS_PER_STICK_BATCH;
			int short_ = (int) Math.max(0, needPlanks - m.total(MaterialRole.PLANKS));
			return new Deficit(need, name, short_);
		}
		long have = m.total(tier.headRole());
		int short_ = (int) Math.max(0, headCount(need) - have);
		if (tier != CraftTier.LEATHER && !need.isArmour()) {
			int sticks = need == GearNeed.SWORD ? 1 : 2;
			if (m.total(MaterialRole.STICK) < sticks) {
				int batches = batchesFor(sticks);
				int plankShort = (int) Math.max(0, (long) batches * ToolRecipes.PLANKS_PER_STICK_BATCH - m.total(MaterialRole.PLANKS));
				if (short_ == 0 && plankShort > 0) return new Deficit(need, "planks", plankShort);
			}
		}
		return new Deficit(need, name, short_);
	}
}
