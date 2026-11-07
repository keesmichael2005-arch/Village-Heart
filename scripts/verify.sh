#!/usr/bin/env bash
# One command that runs every automated check and writes reports/verify-summary.md.
# Agents: run this before claiming anything works. Exit code 0 == all green.
#   scripts/verify.sh            # layers 1, 2, 4 (+ the stubbed L3 script self-test)
#   scripts/verify.sh --fast     # layers 1, 2 only (no game client)
set -uo pipefail
cd "$(dirname "$0")/.."
[ -n "${JAVA_HOME_25:-}" ] || [ -x "$HOME/.jdk25/bin/java" ] || python3 scripts/install-java25.py
export JAVA_HOME="${JAVA_HOME_25:-$HOME/.jdk25}"
mkdir -p reports
SUMMARY=reports/verify-summary.md
echo "# Verify run $(date -u +%FT%TZ) @ $(git rev-parse --short HEAD 2>/dev/null)" > "$SUMMARY"
STATUS=0

step() { # name, logfile, command...
  local name=$1 log=$2; shift 2
  echo "== $name"
  if "$@" > "reports/$log" 2>&1; then echo "- ✅ $name" >> "$SUMMARY"
  else echo "- ❌ $name (see reports/$log)" >> "$SUMMARY"; STATUS=1; fi
}

step "Layer 1: build + unit tests" build.log ./gradlew --no-daemon build
step "Layer 2: in-world GameTests (headless server)" gametest.log ./gradlew --no-daemon runGameTest
grep -E "required tests (passed|failed)|failed at" reports/gametest.log | sed 's/^/    /' >> "$SUMMARY"

step "Layer 3 script self-test (stubbed server)" simulate-selftest.log \
  bash -c 'bash -n scripts/simulate.sh && scripts/test-simulate.sh'

step "Model-log gate self-test" model-log-selftest.log scripts/test-check-model-log.sh

if [ "${1:-}" != "--fast" ]; then
  step "Layer 4: client GameTests + screenshots (virtual display)" clienttest.log \
    xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew --no-daemon runClientGameTest
  # Our gametest mixin fixes a lost-wakeup race in Fabric's NetworkSynchronizer (docs/TESTING.md). Fail if it was not
  # installed, and report how often it saved the run (each rescue would have been a 10 s stall and likely a crash).
  step "Layer 4: NetworkSynchronizer lost-wakeup fix installed" lost-wakeup-fix.log \
    grep -m1 "LOST_WAKEUP_FIX installed" reports/clienttest.log
  echo "    lost wakeups rescued in layer 4: $(grep -c "LOST_WAKEUP_RESCUED" reports/clienttest.log)" >> "$SUMMARY"
  step "Client log has no villageheart model warnings" model-log-gate.log scripts/check-model-log.sh reports/clienttest.log
  rm -rf reports/screenshots && mkdir -p reports/screenshots
  cp build/run/clientGameTest/screenshots/*.png reports/screenshots/ 2>/dev/null
  echo "- Screenshots: $(ls reports/screenshots | tr '\n' ' ')" >> "$SUMMARY"
fi

# Wall-clock perf limits: alone, after every other Gradle step, so nothing competes for the CPU.
step "Perf limits (perfTest, runs alone)" perftest.log ./gradlew --no-daemon --no-parallel perfTest
grep -h "perf " reports/perftest.log | sed 's/^/    /' >> "$SUMMARY"

cat "$SUMMARY"
exit $STATUS
