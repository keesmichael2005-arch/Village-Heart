#!/usr/bin/env bash
# Self-test of scripts/check-model-log.sh against sample logs.
set -uo pipefail
cd "$(dirname "$0")/.."
FAILED=0
expect() { # name, want, log
  scripts/check-model-log.sh "$3" > /dev/null 2>&1
  local got=$?
  if [ "$got" -eq "$2" ]; then echo "ok   $1 (exit $got)"; else echo "FAIL $1: expected $2, got $got"; FAILED=1; fi
}
expect bad_log   1 scripts/testdata/model-log-bad.log
expect clean_log 0 scripts/testdata/model-log-clean.log
expect missing   2 scripts/testdata/does-not-exist.log
: > "${TMPDIR:-/tmp}/empty-model-log.$$"
expect empty     2 "${TMPDIR:-/tmp}/empty-model-log.$$"
rm -f "${TMPDIR:-/tmp}/empty-model-log.$$"
exit $FAILED
