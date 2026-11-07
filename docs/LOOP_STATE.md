status: RUNNING         # RUNNING | PAUSED | DONE
budget: "run 2: stop new workers at run-2 cost ~$400 (session cost_usd ~588) or 07:00 NZ Mon 9 Nov (2026-11-08T18:00Z), whichever first"
start_weekly_usage: "17% weekly at 2026-11-07T07:25Z (user-reported)"
calibration: "run 1: 17% -> 34% weekly for ~$188 => ~$11 per 1% weekly"
run_cost_usd: "run 1 total ~188 (orchestrator session usage incl. all workers, 06:28Z-17:45Z). 5h limit never hit; weekly start 17%. Ask the user for the weekly % to calibrate"
current: "run 2 | M24 PASS r1 merged | M9 r1 FAIL -> builder fixing (m9) | next: M10, M20 after M9"
blocked: []
open_questions_for_human:
  - "M11 starter sim: if a zombie kills a hired worker, food or logs stop for the rest of the 10-day run. Choose: (a) peaceful difficulty for the `starter` scenario, or (b) a scenario re-hire action. Default if unanswered: (b)"
  - "Fixed by the orchestrator, FYI: ROADMAP M11 L2 said 20 bread minus 4 villagers leaves 8; the correct figure is 12 (each eats 2 bread = 10 nutrition)"
plans_ready_not_built: [M9 (needs M7), M10 (needs M9), M11 (needs M7, M9, M10)]
partially_built: [M8 steps 1-3 on m8 (steps 4-9 need M7)]
wakes:                  # run 2, scheduled while usage was available (NZ times)
  - 2026-11-08 09:45, 14:50, 19:55; 2026-11-09 01:00, 06:05 (5 min after each 5h reset)
  - 2026-11-09 07:00 stop