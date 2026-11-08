status: RUNNING         # RUNNING | PAUSED | DONE
budget: "run 2 (user, 2026-11-09 ~07:00 NZ, at 86% weekly): keep going, no stop percentage. Goal: every milestone built AND reviewed (v1.0 gate) before the human's first real playthrough. Weekly resets 16:00 NZ Mon 9 Nov (03:00Z); work stops cleanly if the weekly limit hits first."
start_weekly_usage: "17% weekly at 2026-11-07T07:25Z (user-reported)"
calibration: "run 1: 17% -> 34% weekly for ~$188 => ~$11 per 1% weekly"
run_cost_usd: "run 1 total ~188 (orchestrator session usage incl. all workers, 06:28Z-17:45Z). 5h limit never hit; weekly start 17%. Ask the user for the weekly % to calibrate"
current: "run 2 | M15, M17(+xp), M21 merged | M11 r3 FAIL -> same builder fixing | M12 VERIFY r1 (m12 @69321c9, on m11 base) | backlog-2 r1 FAIL (step-aside nudge misses 2-block jams, nudges busy workers) -> same builder fixing | M19a plan ready (m19a-plan 40a71e0) | M19 plan r2 ready (c69a1ca) | QUEUED: tool-speed after backlog-2 merges, then M19a build (needs M19 plan r3 note: shared core.gear) | plans ready: M12 M13 M14 M18 M19 M22 M23 M25 M26 (wait on M11)"
blocked: []
decisions_by_human:
  - "2026-11-09: v1.0 gate '2 players' = M24's 1 real client + 1 server-side FakePlayer test. ACCEPTED"
  - "2026-11-09: tools: the smith crafts tools of every material the village has (wood, stone, copper, iron, diamond), best tier the resources allow, crafting takes time (M19 plan being revised). Tool work speed follows VANILLA mining speeds (wood 2, stone 4, copper 5, iron 6, diamond 8, netherite 9, gold 12): follow-up 'tool-speed' sets Balance multipliers to speed/2 with stone pace anchored, and restores ROADMAP M15 'iron >= 1.3x stone' (replaces orchestrator D1). Queued after backlog-2 (both touch lumberjack timing)"
  - "2026-11-09 (human intent, orchestrator balance): the smith crafts ONLY on worker demand (missing or worn-out job tool; no spares), never upgrades a working tool (worn-out replacement = best affordable tier), and crafting is slow (L1 ticks/tool: wood 600, stone 1200, copper 1800, iron 2400, diamond 4800, tunable). Player-made tools in a job chest always count, by design"
  - "2026-11-09: Smithy stays Town tier and unlocks copper/iron/diamond tools. Before that, workers self-craft wooden and stone tools slowly (new milestone M19a, deps M15 + jobs only). M19 r2 plan (c69a1ca): demand-only, worn-out replacement may be a lower tier but no worker is downgraded while its tool works (orchestrator accepted)"
open_questions_for_human:
  - "M26 (balance, runs last; not urgent) D-M26-1: may the `full` 30-day sim use one-shot standing orders (e.g. hire a miner when Village unlocks) and still count as no player input? Fallback: a variant that sets the tier at setup. D-M26-3: approve the test audit and tunable bounds file before the first baseline run (orchestrator will review if unanswered)"
  - "Decided by the orchestrator, FYI: the M22 plan adds a village-wide cap of 32 emeralds/day on merchant sales (not in DESIGN), so many stalls plus a cobblestone generator cannot become an unlimited emerald farm. Tunable in M26"
  - "Decided by the orchestrator, FYI: M23 blocks all vanilla villager breeding inside the claim (DESIGN §7: growth only via the dawn trigger), so feeding villagers bread does nothing there. M23 T-L2-2 (no baby in 3 days) watches days 1-2 only to dawn+1500 ticks, since births can only be licensed at dawn; the verifier must confirm no other birth path exists"
  - "M11 starter sim: if a zombie kills a hired worker, food or logs stop for the rest of the 10-day run. Choose: (a) peaceful difficulty for the `starter` scenario, or (b) a scenario re-hire action. Default if unanswered: (b)"
  - "Fixed by the orchestrator, FYI: ROADMAP M11 L2 said 20 bread minus 4 villagers leaves 8; the correct figure is 12 (each eats 2 bread = 10 nutrition)"
plans_ready_not_built: [M9 (needs M7), M10 (needs M9), M11 (needs M7, M9, M10)]
partially_built: [M8 steps 1-3 on m8 (steps 4-9 need M7)]
wakes:                  # run 2, scheduled while usage was available (NZ times)
  - 2026-11-08 09:45, 14:50, 19:55; 2026-11-09 01:00, 06:05 (5 min after each 5h reset)
  - 2026-11-09 11:10 (extra reset wake)
  - 2026-11-09 16:00 run end (weekly reset)