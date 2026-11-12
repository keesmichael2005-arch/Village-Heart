# M26 results (balance pass)

Builder: Sonnet cloud session, branch `m26`. Seed 1, normal difficulty. Villager AI is not seeded, so every number is one run, not a distribution.
Raw reports: the builder's scratchpad (`m26-builder/`), not committed.

## Baseline (before any tuning), code at `95e8557` (m25-full + dev + the `full` scenario and its harness additions)

The `starter`, `defended` and `defended_walled` runs shared the machine with a second short smoke run (timing numbers there are informational).

| scenario | days | result | key numbers | deaths |
|---|---|---|---|---|
| starter | 10 | PASS | village_ms mean 0.06 | 0 |
| defended | 10 | FAIL | deaths.mobs 4 (bar <= 1), kills 23 (>= 15 ok), guard.duty_samples day 2 = 49 (< 100) | d2 guard zombified, d2 guard (zombie villager), d2 farmer, d2 unhired |
| defended_walled | 10 | FAIL | deaths.mobs 4, kills 12 (< 15), duty_samples day 5 = 96 | d2, d5 x2, d8: all guards |
| siege | 4 | FAIL | siege.villager_deaths 3 (bar <= 2) | 3 unhired, night of day 2 |
| raid | 6 | FAIL | villager_deaths 4 (bar <= 3.25), at_end 9 (bar 9.75); victory, 0 losses | 3 by vindicators (unhired), 1 guard by a ravager |
