#!/usr/bin/env bash
# Self-test of scripts/simulate.sh's exit-code contract against a stubbed server (scripts/testdata/fake-sim.sh).
set -uo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "${T:?}"' EXIT
export SIM_DIR="$T/sim" SIM_REPORT_DIR="$T/reports" SIM_GRADLE_CMD="scripts/testdata/fake-sim.sh" SIM_POLL=0.2
FAILED=0

expect() { # name, expected_code, env-assignments... -- args...
  local name=$1 want=$2; shift 2
  local envs=()
  while [ "$1" != "--" ]; do envs+=("$1"); shift; done
  shift
  env ${envs[@]+"${envs[@]}"} scripts/simulate.sh "$@" > "$T/out.txt" 2>&1
  local got=$?
  if [ "$got" -eq "$want" ]; then echo "ok   $name (exit $got)"
  else echo "FAIL $name: expected exit $want, got $got"; sed 's/^/     /' "$T/out.txt" | tail -8; FAILED=1; fi
}

expect pass            0 FAKE_CASE=pass        -- bare 5
expect fail            1 FAKE_CASE=fail        -- bare 5
expect error           2 FAKE_CASE=error       -- bare 5
expect running         2 FAKE_CASE=running     -- bare 5
expect no_clean        2 FAKE_CASE=noclean     -- bare 5
expect four_of_five    2 FAKE_CASE=fourdays    -- bare 5
expect crash_report    2 FAKE_CASE=crashreport -- bare 5
expect no_report       2 FAKE_CASE=noreport SIM_START_SECONDS=2 -- bare 5
expect gradle_rc1      2 FAKE_CASE=rc1         -- bare 5
expect timeout         2 FAKE_CASE=sleep SIM_TIMEOUT_SECONDS=5 -- bare 5
if pgrep -f "$T/sim" > /dev/null || pgrep -fx "sleep 100" > /dev/null; then echo "FAIL leftover fake-sim processes"; pgrep -af "$T/sim"; FAILED=1; else echo "ok   no leftover processes"; fi
expect stall           2 FAKE_CASE=sleep SIM_STALL_SECONDS=2 -- bare 5
expect slow_start      2 FAKE_CASE=slow_start SIM_START_SECONDS=2 -- bare 5
expect usage_unknown  64 -- nonesuch
expect usage_days     64 -- bare abc
expect usage_assert0  64 -- bare 0 --assert "population >= 1"
expect usage_reload   64 -- bare 5 --reload-at 5
expect usage_none     64 --
expect smoke_days0     0 FAKE_CASE=pass -- bare 0

rm -f "$SIM_DIR/fake-calls.log"
expect reload          0 FAKE_CASE=pass -- bare 4 --reload-at 2
calls=$(wc -l < "$SIM_DIR/fake-calls.log" 2>/dev/null || echo 0)
second_resume=$(python3 -c 'import json,sys; l=[json.loads(x) for x in open(sys.argv[1])]; print(len(l)==2 and l[0]["stop_after_day"]==2 and l[0]["resume"] is False and l[1]["resume"] is True and l[1]["stop_after_day"] is None)' "$SIM_DIR/fake-calls.log" 2>/dev/null)
if [ "$calls" -eq 2 ] && [ "$second_resume" = "True" ]; then echo "ok   reload makes two phases (second has resume: true)"
else echo "FAIL reload: calls=$calls second_resume=$second_resume"; FAILED=1; fi

# extra assertions with quotes survive into the request
rm -f "$SIM_DIR/fake-calls.log"
expect assert_escaping 0 FAKE_CASE=pass -- bare 1 --assert 'population >= "1" on day 1'
python3 -c 'import json,sys; r=json.loads(open(sys.argv[1]).readline()); sys.exit(0 if r["extra_assertions"]==["population >= \"1\" on day 1"] else 1)' "$SIM_DIR/fake-calls.log" \
  && echo "ok   --assert text reaches the request intact" || { echo "FAIL --assert text mangled"; FAILED=1; }

# the lock refuses a second simulation
mkdir -p "$SIM_DIR"
locked=$( exec 8> "$SIM_DIR/.lock"; flock -n 8; FAKE_CASE=pass scripts/simulate.sh bare 5 > /dev/null 2>&1; echo $? )
if [ "$locked" -eq 2 ]; then echo "ok   lock held (exit 2)"; else echo "FAIL lock held: expected 2, got $locked"; FAILED=1; fi
exit $FAILED
