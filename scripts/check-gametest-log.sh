#!/usr/bin/env bash
# Fails when a runGameTest log shows that zero tests ran. Vanilla/Fabric report "All 0 required tests passed :)" and exit 0 when
# -Dfabric-api.gametest.filter matches nothing (a non-exact filter does that: only the exact test id matches).
# usage: check-gametest-log.sh reports/gametest.log
log=${1:?usage: check-gametest-log.sh <gametest.log>}
line=$(grep -E "All [0-9]+ required tests passed" "$log" | tail -1)
if [ -z "$line" ]; then
  # A failing run prints "N required tests failed"; the runGameTest step already fails for that.
  grep -qE "required tests failed" "$log" && exit 0
  echo "check-gametest-log: no 'All N required tests passed' line in $log" >&2
  exit 1
fi
n=$(echo "$line" | sed -E 's/.*All ([0-9]+) required tests passed.*/\1/')
if [ "$n" -lt 1 ]; then
  echo "check-gametest-log: 0 GameTests ran. A -Dfabric-api.gametest.filter must be the EXACT test id" \
    "(villageheart-gametest:<class_snake>_<method_snake>); a partial filter matches nothing and silently passes." >&2
  exit 1
fi
echo "check-gametest-log: $n GameTests ran"
