status: RUNNING         # RUNNING | PAUSED | DONE
budget: "run 2 (user, 2026-11-08 20:20 NZ): NO cost cap. Use as much usage as possible until the weekly reset at 16:00 NZ Mon 9 Nov (2026-11-09T03:00Z). Up to 3 build tracks."
start_weekly_usage: "17% weekly at 2026-11-07T07:25Z (user-reported)"
calibration: "run 1: 17% -> 34% weekly for ~$188 => ~$11 per 1% weekly"
run_cost_usd: "run 1 total ~188 (orchestrator session usage incl. all workers, 06:28Z-17:45Z). 5h limit never hit; weekly start 17%. Ask the user for the weekly % to calibrate"
current: "run 2 | M15 merged | M11 finisher (m11) -> verify r3 | M17 VERIFY r1 (m17) | M21 BUILD (m21) | M22 planning | plans ready: M12 M13 M14 M18 M19 M23 (wait on M11; M23 also M12+M14)"
blocked: []
open_questions_for_human:
  - "Decided by the orchestrator, FYI: M23 blocks all vanilla villager breeding inside the claim (DESIGN §7: growth only via the dawn trigger), so feeding villagers bread does nothing there. M23 T-L2-2 (no baby in 3 days) watches days 1-2 only to dawn+1500 ticks, since births can only be licensed at dawn; the verifier must confirm no other birth path exists"
  - "M11 starter sim: if a zombie kills a hired worker, food or logs stop for the rest of the 10-day run. Choose: (a) peaceful difficulty for the `starter` scenario, or (b) a scenario re-hire action. Default if unanswered: (b)"
  - "Decided by the orchestrator, FYI: ROADMAP M15 side-by-side changed to iron ≥1.1× stone and ≥1.3× wood (iron ≥1.3× stone is mathematically unreachable with DESIGN tool multipliers). Alternative if preferred: raise the iron multiplier to ~1.9"
  - "Fixed by the orchestrator, FYI: ROADMAP M11 L2 said 20 bread minus 4 villagers leaves 8; the correct figure is 12 (each eats 2 bread = 10 nutrition)"
plans_ready_not_built: [M9 (needs M7), M10 (needs M9), M11 (needs M7, M9, M10)]
partially_built: [M8 steps 1-3 on m8 (steps 4-9 need M7)]
wakes:                  # run 2, scheduled while usage was available (NZ times)
  - 2026-11-08 09:45, 14:50, 19:55; 2026-11-09 01:00, 06:05 (5 min after each 5h reset)
  - 2026-11-09 11:10 (extra reset wake)
  - 2026-11-09 16:00 run end (weekly reset)