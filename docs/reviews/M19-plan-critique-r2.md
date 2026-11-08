# M19 plan critique, round 2 (r2 revision)

Critic: a fresh session that read only `docs/plans/M19.md` (r2) and the `## M19: Blacksmith / Smithy` section of `docs/ROADMAP.md`.
It read no other docs and no code. The Minecraft jar was not in `~/.gradle` in this container, so no API was re-checked. Every
**[verified]** claim is taken as given, and "unknown" below means "the plan does not say".

Scope: the r2 parts (3.2 #2, #4, #6, #7, #8, 3.3, 3.6, T-L1-1/2/2b/5/7, T-L2-5..5h, 5.5, risks 7-10, 7b). r1 parts are mentioned only
where r2 changes how they interact.

Summary: **11 MAJOR, 17 MINOR.** The biggest problems:
- Cancelling is too eager. A camp that briefly goes unloaded or invalid wipes up to 4800 ticks of committed progress.
- A demand is keyed to one worker, but the tool it produces goes into a camp chest that every worker shares.
- Delivery can loop forever when the camp chest is full.
- An orphaned saved craft can block a demand forever.
- As 3.3 shapes the API, the diamond rule can never pass.
- The roadmap test T-L2-5b can go red when the smith behaves correctly.
- Nothing tests the copper tier.

---

## MAJOR

### 1. Cancelling is too eager: a camp that is unloaded, unscanned or briefly invalid destroys committed progress
3.2 #4 "Cancel" cancels an order when the demand is "gone". 3.4 `DemandCensus` says a camp it cannot read (`CampTools.Missing.UNKNOWN`)
"yields **no** demand", and 3.2 #4 says "nothing for a job whose building is invalid". On the cancel check, "no demand because I cannot
see the camp" and "no demand because the worker is satisfied" look the same. So:
- If the Mine's chunk unloads while the Smithy stays loaded (the player walks off in that direction), a 2400-tick iron craft is cancelled
  at its next 100-tick check, and the progress is lost.
- A building whose validity flickers during a rescan (a door left open, a block being placed, a scan in progress) also cancels the craft
  and loses its progress.
- When the camp comes back, the demand returns and a new craft starts from 0. With slow crafts this can repeat every day, so a worker
  could in practice never get its tool.

This is also the "demand flip-flops" case the task asked about. Wear cannot flip back by itself, but census *visibility* and building
*validity* do flip.

**Fix:** make the census three-valued per (worker, need): `PRESENT`, `ABSENT`, `UNKNOWN`.
- Cancel only on `ABSENT`, and only for definite reasons: the worker is no longer hired in that job, a usable tool of the kind is in the
  camp, or the building is unregistered.
- `UNKNOWN`, and "registered but invalid", **pause** the craft (as 3.2 #9 already does for a golem build at an invalid Smithy). They do
  not cancel it.
- Optionally add hysteresis: cancel only after `ABSENT` on 2 checks in a row.
- `GearDemandPlanner.cancelled` takes the reason. Add an L1 case ("UNKNOWN → not cancelled") and an L2 test: the camp's chunk is
  force-unloaded mid-craft, and the craft survives with its progress.

### 2. A demand is keyed by worker UUID, but the tool goes to a shared camp chest, and M9 hands tools out best first
`OrderKey(worker, need)`, while the delivered tool lands in the camp chest. `ToolAllocation.assign` then gives the best tool to the first
worker in hiring order, not to the worker it was made for. Example with two smiths (or one smith and a player tool):
- Lumberjacks W1 and W2 both MISSING. Smith A crafts for W1 and smith B for W2. A finishes first.
- The next census assigns the new axe to W1, so W1's demand is gone. The fix in this example is harmless.

But in the mixed-tier case:
- W1 has a fresh stone axe, W2 a worn stone axe. Smith A crafts iron for W2.
- On delivery W1 takes the iron axe and W2 takes W1's fresh stone axe, so the demand is gone. Also fine.

The bad case:
- W1 MISSING (order on smith A, 2000/2400 progress), W2 MISSING (order on smith B, 100/2400).
- The player drops one axe into the chest. The census gives it to **W1**, so A's nearly finished craft is cancelled and B's barely
  started one carries on.

That wastes the most work possible, and it repeats any time the tie-break is not aligned with progress. The same mapping problem makes
"`current` = the tier of the worn tool it holds" undefined when a camp has several worn tools: the plan runs `assign` only over the
*not-worn* tools.

**Fix:** treat demand as a **count per (camp building, need)**:
- `needed = hired workers − not-worn tools` (with the job's wear rule). The order key becomes `(building, need, slotIndex)`.
- When `needed` drops below the number of open orders for that camp, cancel the open order with the **least committed progress**.
- Derive `current` per slot from a full `assign` over all tools (worn ones included), which matches what M9's lease really hands out.
- Pin this in L1 with a two-workers-one-camp table: {fresh iron, worn stone} → one WORN with current STONE; {worn iron, worn stone} → two
  WORN; one player tool with two open orders → the low-progress order is cancelled.
- Add an L2 test with two lumberjacks sharing one camp.

### 3. Unverified assumption: are a worker's tools in the camp chest, or in its hand/inventory?
The census reads only "the tools of the right kind in its camp containers". The plan has no §3.1 row saying where a tool sits while a
worker uses it. If M9/M16/M17 move the leased tool into the villager's hand or inventory (for example a miner who takes its pickaxe
underground, or a hunter on a trip), then every *working* worker looks like MISSING. The smith would then make tools nobody needs, which
breaks the roadmap's first L1 criterion in the real game while every L1 test stays green (they feed demands in by hand).

**Fix:** add assumption **A11**: "`CampTools.lease` leaves the stack in the camp container (only a lease record moves). If not, the
census also counts tools in hired workers' hands and inventories." Check it at step 7. T-L2-5b already catches this for the miner (it
would craft) only if the miner really goes out and works. Make that explicit, and add the same check for a hunter who is out on a trip.

### 4. Delivery to a full or unreachable job chest loops forever, and worn tools fill the camp chests
3.2 #8 says a full or unreachable chest sends the tool to Storage and "the demand stays open". On the next pass, row 7 sees "a usable
item of that kind is in Storage", so it runs `FetchAndDeliver`. That walks to the same full chest, fails again, and puts the tool back in
Storage. The loop has no backoff, so a smith can burn a whole day walking.

r2 also makes full chests *likely*: the smith "never takes a tool away" (asserted in T-L2-5c). Each upgrade through wear leaves the old,
lower-tier worn tool in the chest. Because `assign` gives the best tool first, that worn tool is never used and never breaks. One worn
tool piles up per replacement.

**Fix:**
- (a) `FetchAndDeliver` and `Deliver` check for a free slot *before* fetching. After a failure the demand is marked
  `DELIVERY_BLOCKED` for `SMITH_DELIVERY_BACKOFF` (say 1200 ticks), with a hint such as "Lumber Camp chest is full".
- (b) On delivery the smith takes the camp's worn tools of that kind that no worker leases, and puts them into the Smithy chest. They then
  get repaired there by row 9 (r1 machinery) and become Storage stock for a later `FetchAndDeliver`. That needs a change to T-L2-5c's
  "worn axe still in the chest" assertion. It is an owner call, so flag it.
- (c) Add an L2 test: the camp chest is full, the tool goes to Storage once, there is no second walk within the backoff, and the hint is
  shown.

### 5. An orphaned saved craft can block its demand forever (smith fired, furnace unstaffed, Smithy invalid)
Crafts are saved per blast furnace and continue "by this or the next smith of that blast furnace", and `SmithOrders` is rebuilt from the
saved crafts. So:
- If the smith is fired and nobody is hired at that furnace, the craft stays saved forever.
- Its `OrderKey` stays open, so **no other smith** (another furnace or another Smithy) may serve that worker. The miner then waits with
  no pickaxe, indefinitely.
- The same happens when the Smithy is registered but invalid (row 1 idles, the craft stays paused, the key stays open).

**Fix:** a saved craft with no hired, loaded smith at its furnace at the start of WORK (or for `SMITH_ORPHAN_TICKS`, say 2000 work ticks)
is either adopted by any hired smith of the same Smithy (re-key the map entry to that smith's furnace), or cancelled with a refund so the
key is freed. Add a T-L1-5 or L2 case: fire the smith mid-craft, hire a smith at the second furnace, and check it continues or refunds.

### 6. As 3.3 shapes the API, the diamond rule (b) never passes
`VillageGear(int tierWorkersWithoutIron …)` is one village-wide number, and `TierChoice.best(d, m, g, s)` cannot remove the demanding
worker from it. The demanding worker has no usable tool, or a worn one below iron, so it is itself "without iron". The count is therefore
≥ 1 whenever a stone-or-lower or tool-less worker asks. Diamonds are then reachable only for a worker whose worn tool is already iron, and
the second half of T-L1-2 (i) ("20 diamonds and every other worker iron+ → `diamond_pickaxe`" for a MISSING miner) can never pass.

There are also two undefined counts:
- Do unloaded guards ("count as fully equipped") count as iron or better?
- Does an iron item currently being crafted or carried for someone count as theirs?

**Fix:** have `VillageGear` carry per-(worker/slot) flags (`Map<OrderKey, Boolean ironOrBetter>`), so `TierChoice` checks "all keys ≠
d.key". Define an unloaded guard as iron or better. Count an open iron-or-better order as already having it, otherwise two demands block
each other. Add T-L1-2 cases for exactly these.

### 7. The roadmap test T-L2-5b can go red when the smith is right (worn line reached within the day)
The miner works all day with a stone pickaxe (131). Wear is 10 per haul, so the worn line is max(14, 20) = 20 remaining, reached after
**12 hauls**. The test asserts "damage = 10 × hauls, still above the worn line" and "no CRAFT all day". If an M16 miner does 12 or more
hauls in a work day (the plan never says how many; A9 even rescales haul units), the pickaxe *correctly* becomes worn and the smith
*correctly* makes an iron pickaxe. The test is then red for the right behaviour, or the builder "fixes" it by weakening the wear rule.

**Fix:** keep the roadmap wording but make the assertion exact:
- At every CRAFT commit and delivery, the miner's tool must have remaining ≥ the worn line, else red. Any craft while the tool is still
  unworn is a proactive upgrade.
- Separately, either keep the hauls below 12 (log the count and assert it ≤ 11 so the setup proves itself), or give the miner a Mine
  with a fixed small ore budget.

### 8. Nothing tests the copper tier (tools or armour), although it sits in the middle of the tier ladder the owner named
T-L1-2 covers wooden (b), stone (c), iron (d), diamond (i) and leather (o), but no case picks copper. No L2 test uses copper either (T-L2-4
only smelts it). A planner that skipped copper (iron → stone), or ranked it below stone, would pass every test. The same goes for "raw
copper counts as copper via a SMELT prerequisite".

**Fix:** add T-L1-2 cases:
- 3 copper ingots + cobble + sticks → `copper_axe` (not stone).
- 3 raw copper + fuel → `copper_axe` with SMELT ×3.
- A worn copper axe with only cobble → no order (never downgrade).
- A guard chest with 8 copper ingots and leather → `copper_chestplate`.

Also extend T-L2-10 so the copper recipes match vanilla.

### 9. The guard-gear census counts pieces the guards cannot reach (the Smithy chest), which can hide a demand forever
3.2 #4 counts usable pieces "in the Smithy chest". The Smithy chest is the player's own repair drop-off (3.2 #5: "including the player's
own tools, that is what the Smithy chest is for"). A diamond chestplate the player left there, or keeps there, counts as guard 1's chest
piece, so guard 1 never gets a chestplate. Unless M18 guards also draw from the Smithy chest, which the plan does not claim, they only take
from the stockpile and the Barracks.

The same double counting hits a sword in Storage: it covers a guard's WEAPON slot *and* is the "usable item in Storage" for a hunter's U1
`FetchAndDeliver`. Once the hunter takes it, the guard demand reappears a census later. That is a delay rather than a deadlock, but it is
non-deterministic.

**Fix:**
- Count only where guards draw gear from: worn, Barracks, the stockpile, plus items a smith is carrying back to the Barracks after repair.
- In one census pass, give each physical stack to exactly one demand (tool workers first, then guards). Use the same allocation for
  `FetchAndDeliver`.

### 10. Committed-craft cancel versus "repairable now" makes guard demands flip-flop with iron stock
A guard piece below the usable line "that is **repairable now** counts as usable". Whether it is repairable now depends on whether its
material is available at that moment. So a guard's demand appears and disappears as ingots come and go:
- No iron → MISSING. The smith smelts raw iron as a SMELT prerequisite and commits a chestplate.
- Ingots from a miner's haul or a smelt batch arrive. The broken chestplate becomes "repairable now", the demand disappears, and the craft
  is cancelled with a refund (up to 2400 lost ticks).
- The repair then uses the ingots, which may again make something else unaffordable.

**Fix:** freeze the classification for an order's lifetime. Once a slot's MISSING order is committed, that demand is kept until the
craft ends, unless the guard is fired or a *usable* (not merely repairable) piece appears. Or count a repairable piece as usable only when
its repair is already queued or reserved. Add an L1 case.

### 11. T-L2-5h cannot pass as written, and its arithmetic is wrong
- The setup writes a saved `iron_pickaxe` craft but never creates the demand: no Mine, no hired miner, no empty Mine chest. At the first
  100-tick cancel check the demand is "gone", so the craft is cancelled and refunded, and the test is red for a setup reason. Or a
  builder adds a "keep saved crafts without a demand" shortcut to make it pass, which breaks T-L2-5d's rule.
- The `committed` list is not given, but the second part asserts "exactly 3 iron + 2 sticks" refunded.
- At progress 1200 and day time 10 400, it reaches 1800 by 11 000, so the next day it finishes ≈ **600** work ticks after 1000, not "≈
  1200 later".
- There is no `maxTicks`. The night gap alone is 14 000 ticks.
- `reloadEntity` on the smith does not exercise the saved-data path or the "`SmithOrders` rebuilt from saved crafts" path (both are
  transient or world-level, not entity-level).

**Fix:**
- Build the full T-L2-5 scene (miner, empty Mine chest), with committed `[iron_ingot 3, stick 2]`.
- Assert progress frozen at 1800 ± 20 from 11 000 for 200 sampled ticks, then `setDayTime(next 1000 − 20)` instead of waiting the night
  out.
- Expect finish ≈ 600 work ticks after 1000.
- Also clear `SmithRuntime` and `SmithOrders` (the restart path), then assert the same worker gets **no second order** while the saved
  craft runs.
- Give it an explicit `maxTicks`.

---

## MINOR

### 12. `current` tier is a `CraftTier`, so a worn gold or netherite tool has no tier to compare
`GearDemand.current: Optional<CraftTier>`, but player tools of gold and netherite "satisfy a demand", and a worn one creates a WORN demand
whose `current` cannot be expressed. Never-downgrade is then undefined:
- Worn netherite: "≥ current" would mean "never, until it breaks".
- Worn gold: gold ranks between copper and iron in `ToolTier`, so is stone a downgrade?

There are now three rankings (`ToolTier` ordinal, `CraftTier` order with LEATHER between WOODEN and STONE, `GearInfo.rank`).

**Fix:** keep `current` as `GearInfo.rank`, or `ToolTier`, and define the mapping:
- netherite → the WORN demand may take DIAMOND (if the diamond rule allows) or IRON.
- gold → treated as COPPER for the never-downgrade comparison.

Pin both in T-L1-2.

### 13. Never-downgrade guarantees idle time when the old tier is unaffordable, although M9's best-first lease would make an early lower-tier delivery harmless
With a worn iron axe and only cobble, the plan waits until the axe breaks and then crafts stone. The worker idles for the whole stone
craft (1200 ticks, or 2400 for an iron replacement of a worn diamond blocked by the diamond rule). Because `ToolAllocation` gives the best
tool first, a stone axe delivered *now* would not be used until the worn iron one breaks, so the working tool is not actually downgraded.
This is a roadmap wording question ("never a lower one while it still works"), so **flag it for the owner**:
- Either allow a lower-tier order for a WORN demand when nothing ≥ current is affordable, since the worker keeps using the better tool
  until it breaks.
- Or keep the rule and say in DESIGN that it costs one craft of idle time.

### 14. The worn margin is in wear units, not in time, so "the worker loses little time" is unproven
The floor is `2 × wearPerUnit` (2 logs for a lumberjack) and the 10 % term (13 logs on stone). Against a 1200-2400-tick craft, a
lumberjack will probably finish its last 13 logs long before the replacement exists. The plan never states logs per tick or hauls per
day.

**Fix:** set the worn line from the expected craft time of the likely replacement tier: `remaining < wearPerUnit × ceil((craftTicks(tier)
+ deliveryAllowance) / ticksPerWorkUnit(job))`, clamped to at least the 10 % term. Or measure it in L2 and record it in §8. T-L1-2b pins
the formula.

### 15. "Best affordable" for the farmer's hoe spends iron for no gain
The hoe is a BONUS tool, "×1.25 any tier". Best-affordable makes an iron hoe (2 ingots, 2400 ticks) when a stone one (600 or 1200 ticks)
gives the same bonus. The iron could have gone to a U1 tool or a golem. Hunters may be the same, depending on what tier does for them.

**Fix (owner call):** for needs where the tier does not change the job's pace, choose the cheapest tier whose durability is ≥ stone's.
Or cap hoes at stone, or at copper. Pin it in T-L1-2.

### 16. Row 12 "no open demand of U1-U4" is ambiguous and can block golems for good
- "Open demand" could mean a demand with no order yet, or any demand at all. A demand another smith is already crafting for should not
  block this smith's golem.
- A demand that is open but cannot be ordered also blocks every golem until something changes. Example: a worn diamond pickaxe, where the
  diamond rule fails and never-downgrade forbids iron, even with 36+ ingots in stock.

**Fix:** "no *orderable* demand of U1-U4 without an open order", where orderable means `TierChoice.best` is non-empty. Add T-L1-5 cases.

### 17. SMELT prerequisites conflict with the smelt time gate and `SmithKeep`
- Row 7 `StartCraft` is not time-gated, but its SMELT step is a smelt batch, and 3.2 #6 gates smelt batches by `SMITH_DONE_BY`. Which
  rule wins?
- The prerequisite's ingots must stay in the smith's inventory for the craft. 3.2 #3 says smelt outputs go to `depositNow` →
  `DepositTrip`, and `SmithKeep` keeps the "outputs of the current job", so the plan must define the *order* as the current job, with
  prerequisite outputs as order inputs.
- At GO_HOME (keep 0) the fetched but uncommitted inputs and the prerequisite ingots go back to Storage, and the next day the tier is
  chosen again.

**Fix:**
- Define `SmithJob.Order(prereqs…, craft)` as one job for `SmithKeep`.
- Gate the *start* of a `StartCraft` (fetch included) by `WORK_END − (expected fetch + smelt + walk)`, so an order is not fetched at
  10 990 only to be deposited again at 11 000.
- Add a T-L1-5 case.

### 18. Cancelling before commit is not "nothing happened"
3.2 #4 says "before commit nothing happened (reservations released)". By then the smith may already carry the fetched materials, or may
have run STICKS (planks are now sticks) or SMELT (raw ore and fuel are now ingots).

**Fix:** a pre-commit cancel deposits whatever the smith carries for the order (the sticks or ingots made, not the original planks or
ore; the fuel is spent) and releases the reservations. Also add an L2 case for the *pending* cancel the roadmap names ("cancels a pending
order"). T-L2-5d only cancels after commit.

### 19. Overnight delivery and the restart path for a carried tool are not specified
3.2 #6 says a finished tool "on its way to a job chest … it delivers first" at GO_HOME, but `SmithWork` runs only in WORK. Who walks it
at night? The plan should also say whether it walks to a far camp after dark. After a restart, `SmithOrders` is transient, so
`carriedFor` is empty, row 5 is false, and the tool goes to Storage and comes back through `FetchAndDeliver`. That works, but it is
undocumented and costs two walks.

**Fix:** keep the finished tool overnight and deliver it at the next WORK. Rebuild `carriedFor` after a restart by matching carried tool
ids against live demands. Say both in 3.2 #8.

### 20. A craft whose worker changes camp is delivered to the old camp
The key is `(worker, need)` and `destination` is fixed at order time. A lumberjack fired from Camp A and hired at Camp B keeps the same
key. The craft is not cancelled and the tool goes to Camp A. This is solved by #2's per-building key; otherwise compare `destination` in
`cancelled`.

### 21. `FetchAndDeliver` from Storage is underspecified
- `SmithFacts.storageTools: Set<String>` has ids only, so a *worn* Storage tool would be delivered and then fail to cover the demand,
  bringing the demand straight back. Apply the job's worn rule and pass `(id, remaining, max)`.
- Two smiths or two demands can pick the same Storage axe. Reserve the stack under the smith's UUID at decision time.
- Storage may hold the player's enchanted or named tools that they only meant to store. Either it is intended ("the player's tools
  satisfy"), in which case say so in DESIGN, or skip stacks with components, as the smith already does for inputs.
- State the never-downgrade rule for Storage tools too. Delivering a lower-tier Storage tool for a WORN demand is harmless, see #13.
- If camp chests are also stockpile containers (the plan does not say; M6 stockpile membership is unknown here), "Storage" tools would
  include other camps' tools. Add an assumption row.

### 22. Shovels and spears: the recipe table and `GearNeed` contradict each other
3.2 #2 says spears and shovels are "in the table (pinned)", but the table lists 5 kinds with shovel and **no spear**. `GearNeed` has no
SHOVEL or SPEAR, yet T-L1-1 asserts "every CRAFT has a need and a tier", which fails for shovel rows.

**Fix:** drop shovels and spears from `SmithRecipes` (v1 never makes them; T-L2-10 can still compare the five recipes the smith does
make). Or add `GearNeed.SHOVEL`/`SPEAR` marked never-demanded and relax the T-L1-1 wording.

### 23. Repairs cannot handle half of what the smith now makes
The repair materials are iron, gold, copper and leather. Diamond tools and armour (now crafted) and wooden or stone tools (now crafted,
repairable in vanilla with planks or stone-tool materials) are never repairable. So a worn diamond guard chestplate is never "repairable
now" and gets *replaced* (8 diamonds) instead of repaired (1-3 diamonds).

**Fix:** add `diamond` under the diamond reserve rule (and optionally `#planks` and `#stone_tool_materials` for the Smithy chest). Or
state in DESIGN that diamond gear is replace-only, and extend T-L1-3 and T-L2-10. Also list the copper repair tag, which 3.2 #5 omits.

### 24. The roadmap says guard gear "about to break" counts, but the plan has no WORN class for guards
The owner's criterion covers "a hired worker **or guard** … with one about to break". Guards get MISSING demands only (U2 and U3), and a
piece below 10 % counts as usable if it is repairable, otherwise it is MISSING.

**Fix:** state explicitly that for guards "about to break" means below `GEAR_MIN_DURABILITY` and repair is preferred. Or add a guard WORN
class (fraction < 0.25 → order only if not repairable). Map it in 5.5.

### 25. Materials taken by others between tier choice and commit
"Afford" subtracts reservations, but unreserved withdrawals (the player, M-builder construction, a cook burning logs that could have
become planks) can drain the stock during the fetch walk. The plan does not say what happens when the fetch comes back short: choose the
tier again (possibly lower), or drop the order.

**Fix:** a short fetch releases everything and the planner runs again at once. Never commit a partial recipe. Add a T-L1-5 or L2 case.
Also say whether the smith's *own* reservations (for example a running golem fetch, or a pre-empted smelt batch's kept outputs) count as
"carried" for a new order. They should not, or the same ingots get counted twice.

### 26. Two smiths in one Smithy share a single saved golem build
`golemBuilds` is keyed by the Smithy marker, and row 8 sends *every* smith of that Smithy to `BuildGolem`. Two smiths would tick one
progress value: double speed at best, lost writes at worst.

**Fix:** give the build an owner (the first smith's UUID, transient, re-claimed at the start of WORK) and let only that smith tick it.

### 27. Saved-progress validation versus tunable times
`SMITH_TOOL_TICKS` is tunable. If M26 lowers iron from 2400 to 1800, a saved craft at 2000 is "out of range" and gets refunded instead of
finished. The r1 golem rule goes further and *drops* a build at progress > `GOLEM_BUILD_TICKS`, losing 36 ingots. That is inconsistent
with r2's refund rule for crafts.

**Fix:** clamp progress to `[0, ticks]` (the craft or build then finishes on the next tick), and refund only for an unknown recipe or a
negative value. Apply the same rule to `golem_builds` and update T-L1-7.

### 28. Ambiguous or under-specified test cases and stale references
- T-L1-2 (m), "wood/stone fallback **or** empty": pin one outcome, given the exact stock.
- T-L1-2 (i), hunter or farmer with 20 diamonds → "iron": the stock must include iron ingots. State it.
- T-L2-5c: parts 2 and 3 do not say that the iron axe from part 1 is removed. Without that there is no demand, so part 2 can never make
  a stone axe. State the reset between parts.
- T-L2-5f: no starting day time is given (it needs about 5500 WORK ticks). Set it to 1000.
- Two signatures for `GearDemandPlanner.next`: 3.2 #4 has no `classes` parameter, 3.3 has one.
- §7's r1 table still points at r1 row and case labels ("row 7", "T-L1-2 (j)/(k)/(l)/(m)/(n)") that r2 renumbered. Update them, or say in
  7b that §7 refers to r1 numbering.
- No test covers several smiths at once, two workers sharing a camp, a full camp chest, or a worker fired mid-delivery. Add one L2 each,
  or L1 where the logic is pure: #2, #4, #5, #20.
- Runtime: T-L2-5b (10 600) and T-L2-5h (about 15 000 as written) add a lot to the GameTest suite. Use `setDayTime` jumps where the
  assertion allows.

---

## Roadmap criterion → test check (would the test go red if the feature were broken?)

| Roadmap criterion | Mapped test | Red when broken? |
|---|---|---|
| L1: no order without a demand | T-L1-2 (a) | Yes for the planner. Not for the census (#3), so add the A11 check |
| L1: no upgrade of a working tool | T-L1-2 (f) | Yes |
| L1: worn-out tool → best affordable tier, never lower while it works | T-L1-2 (g), (h) | Partly: (g) offers only iron, so "best of several" is shown only for MISSING (c, d). There is no copper case (#8). Add lower tiers to (g)'s stock |
| L1: player tool cancels a pending order | T-L1-2 (j) | Weak: it only checks `cancelled()` on an input list it is handed. The real behaviour is in the census, covered at L2 by T-L2-5d (after commit only, #18) |
| L1: only planks → wooden tools | T-L1-2 (b) | Yes |
| L1: diamonds only under the reserve rule | T-L1-2 (i) | Cannot pass as the API is shaped (#6) |
| L2: miner without pickaxe → stone pickaxe in the Mine chest in about the stone time | T-L2-5 | Yes (tier, delivery, lower bound 1180) |
| L2: working stone pickaxe and plenty of iron → nothing all day | T-L2-5b | Can go red when the smith is right (#7) |
| Owner bullet: guards' gear on demand, every tier | T-L2-5f (iron only) | Iron yes. Copper, leather and diamond are L1 only, and copper is missing (#8) |
| Owner bullet: slow crafts | T-L1-1, T-L2-5/5e/5f lower bounds | Yes |
| Owner bullet: no spare stock | T-L2-5b, T-L1-2 (a) | Yes |

---
_Generated by [Claude Code](https://claude.ai/code)_
