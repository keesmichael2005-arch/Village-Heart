#!/usr/bin/env bash
# Self-test of scripts/check-mp-log.sh against sample logs.
set -uo pipefail
cd "$(dirname "$0")/.."
FAILED=0
expect() { # name, want, log
  scripts/check-mp-log.sh "$3" > /dev/null 2>&1
  local got=$?
  if [ "$got" -eq "$2" ]; then echo "ok   $1 (exit $got)"; else echo "FAIL $1: expected $2, got $got"; FAILED=1; fi
}
expect clean_with_benign_noise 0 scripts/testdata/mp-log-clean.log
expect server_error            1 scripts/testdata/mp-log-server-error.log
expect villageheart_error      1 scripts/testdata/mp-log-mod-error.log
expect disconnect              1 scripts/testdata/mp-log-disconnect.log
expect timed_out               1 scripts/testdata/mp-log-timeout.log
expect error_after_the_end     0 scripts/testdata/mp-log-error-after-end.log
expect no_markers              2 scripts/testdata/model-log-clean.log
expect no_end_marker           2 scripts/testdata/mp-log-no-end.log
expect missing                 2 scripts/testdata/does-not-exist.log
: > "${TMPDIR:-/tmp}/empty-mp-log.$$"
expect empty                   2 "${TMPDIR:-/tmp}/empty-mp-log.$$"
rm -f "${TMPDIR:-/tmp}/empty-mp-log.$$"
exit $FAILED
