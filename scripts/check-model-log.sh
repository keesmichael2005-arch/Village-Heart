#!/usr/bin/env bash
# Fails when a client log has a model/texture warning about one of our assets.
#   scripts/check-model-log.sh <log>     exit 1: a villageheart model problem, 2: log missing or empty, 0: clean
set -uo pipefail
log=${1:-}
[ -n "$log" ] && [ -s "$log" ] || { echo "check-model-log: log missing or empty: ${log:-<none>}" >&2; exit 2; }
if grep -E '(Missing (block model|model for variant|textures in model|texture references in model)|Unable to bake( item)? model).*villageheart' "$log"; then
  echo "check-model-log: villageheart model problems found (lines above)" >&2
  exit 1
fi
exit 0
