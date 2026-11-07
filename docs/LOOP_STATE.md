status: RUNNING         # RUNNING | PAUSED | DONE
budget: "stop at 2026-11-08 07:00 NZDT (2026-11-07T18:00Z): no new worker after this"
start_weekly_usage: "~20% (user estimate at start of run 1)"
calibration: unknown    # weekly-% per $ of run cost; user reports % after run 1
run_cost_usd: 0
current: M1 / PLAN
blocked: []
wakes:                  # send_later wake-ups scheduled while usage was available
  - 2026-11-07T10:45Z   # reset 10:40Z + 5 min   (trig_014Ka2PfmCo3kBeuYqyYWdVb)
  - 2026-11-07T15:50Z   # +5h05m                (trig_013VvNRdGJ1WATqVyapKvKkg)
  - 2026-11-07T18:00Z   # 07:00 NZ stop          (trig_01WHe7XCFT2LKWxz1FZy1oYA)
