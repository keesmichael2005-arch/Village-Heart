#!/usr/bin/env bash
# Self-test for check-gametest-log.sh.
cd "$(dirname "$0")/.."
d=$(mktemp -d); trap 'rm -rf "$d"' EXIT
echo "[x] All 0 required tests passed :)" > "$d/zero.log"
echo "[x] All 57 required tests passed :)" > "$d/ok.log"
echo "nothing" > "$d/none.log"
scripts/check-gametest-log.sh "$d/zero.log" 2>/dev/null && { echo "FAIL: 0 tests accepted"; exit 1; }
scripts/check-gametest-log.sh "$d/none.log" 2>/dev/null && { echo "FAIL: missing line accepted"; exit 1; }
scripts/check-gametest-log.sh "$d/ok.log" >/dev/null || { echo "FAIL: 57 tests rejected"; exit 1; }
echo "check-gametest-log self-test ok"
