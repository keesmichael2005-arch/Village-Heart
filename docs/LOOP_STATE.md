status: RUNNING         # RUNNING | PAUSED | DONE
budget: "stop at 2026-11-08 07:00 NZDT (2026-11-07T18:00Z): no new worker after this"
start_weekly_usage: "17% weekly at 2026-11-07T07:25Z (user-reported)"
calibration: unknown    # weekly-% per $ of run cost; user reports % after run 1
run_cost_usd: "~156 at 15:50Z (orchestrator session usage incl. all workers); 5h window never hit"
current: "M6 PASS r2 merged | M7 BUILD (m7), step 9 now unblocked"
blocked: []
open_questions_for_human:
  - "M11 starter sim: if a zombie kills a hired worker, food or logs stop for the rest of the 10-day run. Choose: (a) peaceful difficulty for the `starter` scenario, or (b) a scenario re-hire action. Default if unanswered: (b)"
  - "Fixed by the orchestrator, FYI: ROADMAP M11 L2 said 20 bread minus 4 villagers leaves 8; the correct figure is 12 (each eats 2 bread = 10 nutrition)"
plans_ready_not_built: [M9 (needs M7), M10 (needs M9), M11 (needs M7, M9, M10)]
partially_built: [M8 steps 1-3 on m8 (steps 4-9 need M7)]
wakes:                  # send_later wake-ups scheduled while usage was available
  - 2026-11-07T10:45Z   # reset 10:40Z + 5 min   (trig_014Ka2PfmCo3kBeuYqyYWdVb)
  - 2026-11-07T15:50Z   # +5h05m                (trig_013VvNRdGJ1WATqVyapKvKkg)
  - 2026-11-07T18:00Z   # 07:00 NZ stop          (trig_01WHe7XCFT2LKWxz1FZy1oYA)
