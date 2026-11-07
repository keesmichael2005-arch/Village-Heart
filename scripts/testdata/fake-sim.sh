#!/usr/bin/env bash
# Stand-in for "./gradlew runSimulation" in scripts/test-simulate.sh. Picks its behaviour from $FAKE_CASE.
set -u
SIM_DIR=${SIM_DIR:-build/run/sim}
# The long-lived processes carry the real server's marker argument (villageheart.sim.request=...) and the run's SIM_DIR in
# their argv, so a machine-wide pkill -f on that marker, or a leftover check scoped to the temp dir, can see them.
python3 - "$SIM_DIR" "${FAKE_CASE:-pass}" "villageheart.sim.request=$SIM_DIR/sim-request.json" <<'PY'
import json, os, sys, time
sim_dir, case = sys.argv[1:3]
req = json.load(open(os.path.join(sim_dir, "sim-request.json")))
open(os.path.join(sim_dir, "fake-calls.log"), "a").write(json.dumps(req) + "\n")
out = req["out"]
os.makedirs(os.path.dirname(out), exist_ok=True)
n = req["days"]
def report(status="PASS", days=n, clean=True, stopped=None, reloads=()):
    r = {"schema": 1, "scenario": req["scenario"], "status": status, "error": "boom" if status == "ERROR" else None,
         "clean_shutdown": clean, "heartbeat": 1, "actions_done": 1, "stopped_after_day": stopped, "seed": 1,
         "difficulty": "normal", "days_requested": n, "minecraft": "1.21.11", "g0": 41, "reloads": list(reloads),
         "baseline": None, "days": [{"day": d + 1} for d in range(days)], "assertions": []}
    json.dump(r, open(out, "w"))
if req.get("stop_after_day"):
    report("RUNNING", req["stop_after_day"], True, req["stop_after_day"]); sys.exit(0)
if req.get("resume"):
    report("PASS", n, True, None, [int(os.environ.get("FAKE_RELOAD", "2"))]); sys.exit(0)
if case == "pass": report()
elif case == "fail": report("FAIL")
elif case == "error": report("ERROR")
elif case == "running": report("RUNNING")
elif case == "noclean": report(clean=False)
elif case == "fourdays": report(days=n - 1)
elif case == "crashreport":
    report(); os.makedirs(os.path.join(sim_dir, "crash-reports"), exist_ok=True)
    open(os.path.join(sim_dir, "crash-reports", "crash-x.txt"), "w").write("crash")
elif case == "noreport": pass
elif case == "rc1": report(); sys.exit(1)
elif case == "sleep":
    report("RUNNING", 0, False)
    os.system("python3 -c 'import time; time.sleep(100)' '%s/grandchild' & wait" % sim_dir); time.sleep(100)
elif case == "escape":
    # like Gradle's daemon + server: a child in its own session and process group, reachable only through the run token
    report("RUNNING", 0, False)
    os.system("setsid python3 -c 'import time; time.sleep(100)' '%s/escapee' villageheart.sim.request=x & wait" % sim_dir); time.sleep(100)
elif case == "slow_start": time.sleep(100)
else: sys.exit(99)
PY
