#!/usr/bin/env bash
# Layer 3: boot a real headless server on a fixed seed, claim the village, fast-forward N days, write
# reports/sim/<scenario>.json. See docs/TESTING.md ("Layer 3: simulations") and docs/plans/M2.md section 3.7.
#
#   scripts/simulate.sh <scenario> [days] [--assert "<expr>"]... [--reload-at <day>]
#   scripts/simulate.sh --list
#
# Exit codes: 0 PASS (clean shutdown, N day records, no crash report) | 1 FAIL (an assertion was false)
#             2 ERROR (anything else: crash, hang, compile failure, timeout, bad report, lock held) | 64 usage
# env: SIM_TIMEOUT_SECONDS (overall limit), SIM_STALL_SECONDS (default 600, report must change at least this often),
#      SIM_START_SECONDS (default 180, report must appear), SIM_GRADLE_CMD (default "./gradlew --no-daemon runSimulation"),
#      SIM_DIR (default build/run/sim), SIM_REPORT_DIR (default reports/sim), SIM_POLL (poll seconds, default 5)
# Never trusts Gradle's exit code alone: a server crash still ends runSimulation with 0.
set -uo pipefail
cd "$(dirname "$0")/.."

SIM_DIR=${SIM_DIR:-build/run/sim}
REPORT_DIR=${SIM_REPORT_DIR:-reports/sim}
SIM_GRADLE_CMD=${SIM_GRADLE_CMD:-"./gradlew --no-daemon runSimulation"}
SIM_STALL_SECONDS=${SIM_STALL_SECONDS:-600}
SIM_START_SECONDS=${SIM_START_SECONDS:-180}
SIM_POLL=${SIM_POLL:-5}
: "${SIM_DIR:?}" "${REPORT_DIR:?}"

usage() {
  echo "usage: scripts/simulate.sh <scenario> [days] [--assert \"<expr>\"]... [--reload-at <day>]" >&2
  echo "       scripts/simulate.sh --list" >&2
  exit 64
}

if [ "${1:-}" = "--list" ]; then
  python3 - <<'PY'
import json, glob, os
for f in sorted(glob.glob("sim/scenarios/*.json")):
    d = json.load(open(f))
    print("%-18s %s" % (os.path.basename(f)[:-5], d.get("description", "")))
PY
  exit 0
fi

SCENARIO=${1:-}
[ -n "$SCENARIO" ] || usage
shift
case "$SCENARIO" in -*|*[!a-z0-9_]*) echo "bad scenario name: $SCENARIO" >&2; usage ;; esac
SCEN_FILE="sim/scenarios/$SCENARIO.json"
[ -f "$SCEN_FILE" ] || { echo "unknown scenario '$SCENARIO' (try --list)" >&2; exit 64; }

DEFAULT_DAYS=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["default_days"])' "$SCEN_FILE") || exit 64
SEED=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("seed", 1))' "$SCEN_FILE") || exit 64
DAYS=$DEFAULT_DAYS
EXTRA=()
RELOAD_AT=""
if [ $# -gt 0 ] && [[ "$1" != --* ]]; then DAYS=$1; shift; fi
while [ $# -gt 0 ]; do
  case "$1" in
    --assert) [ $# -ge 2 ] || usage; EXTRA+=("$2"); shift 2 ;;
    --reload-at) [ $# -ge 2 ] || usage; RELOAD_AT=$2; shift 2 ;;
    *) echo "unexpected argument: $1" >&2; usage ;;
  esac
done
[[ "$DAYS" =~ ^[0-9]+$ ]] && [ "$DAYS" -le 365 ] || { echo "days must be an integer 0..365, got '$DAYS'" >&2; usage; }
[ "$DAYS" -gt 0 ] || [ ${#EXTRA[@]} -eq 0 ] || { echo "days = 0 together with --assert is refused (nothing would be checked)" >&2; exit 64; }
if [ -n "$RELOAD_AT" ]; then
  [[ "$RELOAD_AT" =~ ^[0-9]+$ ]] && [ "$RELOAD_AT" -ge 1 ] && [ "$RELOAD_AT" -le $((DAYS - 1)) ] \
    || { echo "--reload-at must be in 1..days-1" >&2; usage; }
fi

[ -n "${JAVA_HOME_25:-}" ] || [ -x "$HOME/.jdk25/bin/java" ] || python3 scripts/install-java25.py
export JAVA_HOME="${JAVA_HOME_25:-$HOME/.jdk25}"

mkdir -p "$SIM_DIR" "$REPORT_DIR"
exec 9> "$SIM_DIR/.lock"
flock -n 9 || { echo "another simulation is running (lock $SIM_DIR/.lock)" >&2; exit 2; }

ROOT_DIR=$PWD
REPORT="$REPORT_DIR/$SCENARIO.json"
REPORT_ABS=$(realpath -m "$REPORT")
SCEN_ABS=$(realpath "$SCEN_FILE")
FALLBACK="$SIM_DIR/sim-error.json"
REQUEST="$SIM_DIR/sim-request.json"
find "$REPORT_DIR" -maxdepth 1 -name "$SCENARIO.*" -type f -delete
rm -f "$FALLBACK" "$REQUEST"
rm -rf "${SIM_DIR:?}/world" "${SIM_DIR:?}/crash-reports"
echo "eula=true" > "$SIM_DIR/eula.txt"
cat > "$SIM_DIR/server.properties" <<PROPS
online-mode=false
level-seed=$SEED
difficulty=normal
pause-when-empty-seconds=0
max-tick-time=60000
spawn-protection=0
view-distance=6
simulation-distance=6
sync-chunk-writes=false
enable-rcon=false
motd=villageheart-sim
PROPS

write_request() { # stop_after_day|"" resume(true|false)
  python3 - "$REQUEST" "$SCEN_ABS" "$SCENARIO" "$DAYS" "$REPORT_ABS" "$1" "$2" ${EXTRA[@]+"${EXTRA[@]}"} <<'PY'
import json, sys
path, scen_file, name, days, out, stop, resume = sys.argv[1:8]
req = {"scenario_file": scen_file, "scenario": name, "days": int(days), "out": out,
       "extra_assertions": sys.argv[8:], "stop_after_day": int(stop) if stop else None,
       "resume": resume == "true"}
json.dump(req, open(path, "w"))
PY
}

PGID=""
kill_group() {
  if [ -n "$PGID" ]; then
    kill -TERM -- "-$PGID" 2>/dev/null
    sleep 1
    kill -KILL -- "-$PGID" 2>/dev/null
  fi
  pkill -KILL -f -- "villageheart.sim.request=" 2>/dev/null
  return 0
}
trap kill_group EXIT
trap 'exit 2' INT TERM

mtime() { stat -c %Y "$1" 2>/dev/null || echo ""; }
report_file() { if [ -f "$REPORT" ]; then echo "$REPORT"; elif [ -f "$FALLBACK" ]; then echo "$FALLBACK"; fi; }

# run_phase <log>: sets PHASE_RC (gradle rc) or PHASE_ABORT (reason). Never exits.
run_phase() {
  local log=$1 limit=${SIM_TIMEOUT_SECONDS:-$((240 + 150 * DAYS))}
  PHASE_RC=""; PHASE_ABORT=""
  local base_m; base_m=$(mtime "$REPORT")
  setsid timeout -k 30 "${limit}s" bash -c "$SIM_GRADLE_CMD" < /dev/null > "$log" 2>&1 9>&- &
  local pid=$!
  PGID=$pid
  local start=$SECONDS started=0 last_m="" last_change=$SECONDS m rf
  while kill -0 "$pid" 2>/dev/null; do
    sleep "$SIM_POLL"
    rf=$(report_file)
    m=""; [ -n "$rf" ] && m=$(mtime "$rf")
    if [ "$started" -eq 0 ]; then
      if [ -n "$m" ] && { [ "$rf" != "$REPORT" ] || [ "$m" != "$base_m" ] || [ -z "$base_m" ]; }; then
        started=1; last_m=$m; last_change=$SECONDS
      elif [ $((SECONDS - start)) -ge "$SIM_START_SECONDS" ]; then
        PHASE_ABORT="harness did not start: no report within ${SIM_START_SECONDS}s"; break
      fi
    else
      if [ "$m" != "$last_m" ]; then last_m=$m; last_change=$SECONDS
      elif [ $((SECONDS - last_change)) -ge "$SIM_STALL_SECONDS" ]; then
        PHASE_ABORT="stalled: report unchanged for ${SIM_STALL_SECONDS}s"; break
      fi
    fi
  done
  if [ -n "$PHASE_ABORT" ]; then
    kill_group
    wait "$pid" 2>/dev/null
    return
  fi
  wait "$pid"; PHASE_RC=$?
  kill_group   # leftovers of a finished or timed-out phase must not hold session.lock
  PGID=""
}

# decide <expect_days> <mode: final|phase1> <stop_day>: prints "<code> <reason>"
decide() {
  python3 - "$(report_file)" "$1" "$2" "$3" "$SIM_DIR/crash-reports" "$RELOAD_AT" <<'PY'
import json, os, sys
rf, ndays, mode, stop, crashdir, reload_at = sys.argv[1:7]
ndays = int(ndays)
def out(code, why): print(code, why); sys.exit(0)
if not rf or not os.path.isfile(rf): out(2, "no report written")
try: r = json.load(open(rf))
except Exception as e: out(2, "unparsable report: %s" % e)
crashes = [f for f in os.listdir(crashdir)] if os.path.isdir(crashdir) else []
if crashes: out(2, "server crash report(s): %s" % ", ".join(crashes))
st = r.get("status")
if st == "ERROR": out(2, "harness ERROR: %s" % str(r.get("error"))[:400].replace("\n", " | "))
if not r.get("clean_shutdown"): out(2, "no clean_shutdown (server did not stop cleanly), status %s" % st)
n = len(r.get("days", []))
if mode == "phase1":
    if st != "RUNNING": out(2, "phase 1 status %s, expected RUNNING" % st)
    if n != int(stop): out(2, "phase 1 has %d day records, expected %s" % (n, stop))
    if r.get("stopped_after_day") != int(stop): out(2, "stopped_after_day is %s, expected %s" % (r.get("stopped_after_day"), stop))
    out(0, "phase 1 ok")
if st not in ("PASS", "FAIL"): out(2, "status is %s" % st)
if n != ndays: out(2, "%d day records, expected %d" % (n, ndays))
if reload_at and r.get("reloads") != [int(reload_at)]: out(2, "reloads is %s, expected [%s]" % (r.get("reloads"), reload_at))
out(0 if st == "PASS" else 1, st)
PY
}

finish() { # code reason
  local code=$1 why=$2
  [ -f "$REPORT_DIR/$SCENARIO.summary.md" ] && cat "$REPORT_DIR/$SCENARIO.summary.md"
  if [ "$code" -eq 2 ]; then
    echo "SIMULATION ERROR: $why" >&2
    local l
    for l in "$REPORT_DIR/$SCENARIO.2.log" "$REPORT_DIR/$SCENARIO.log"; do
      if [ -f "$l" ]; then echo "--- last 30 lines of $l" >&2; tail -30 "$l" >&2; break; fi
    done
  elif [ "$code" -eq 1 ]; then echo "SIMULATION FAIL: an assertion was false (see above)" >&2
  else echo "SIMULATION PASS: $REPORT"; fi
  exit "$code"
}

LOG1="$REPORT_DIR/$SCENARIO.log"
if [ -n "$RELOAD_AT" ]; then
  write_request "$RELOAD_AT" false
  run_phase "$LOG1"
  [ -z "$PHASE_ABORT" ] || finish 2 "phase 1: $PHASE_ABORT"
  [ "$PHASE_RC" -eq 0 ] || finish 2 "phase 1: gradle exited with $PHASE_RC"
  read -r code why < <(decide "$DAYS" phase1 "$RELOAD_AT")
  [ "$code" -eq 0 ] || finish 2 "$why"
  write_request "" true
  run_phase "$REPORT_DIR/$SCENARIO.2.log"
else
  write_request "" false
  run_phase "$LOG1"
fi
[ -z "$PHASE_ABORT" ] || finish 2 "$PHASE_ABORT"
[ "$PHASE_RC" -eq 0 ] || finish 2 "gradle exited with $PHASE_RC (124/137 = timeout)"
read -r code why < <(decide "$DAYS" final "")
finish "$code" "$why"
