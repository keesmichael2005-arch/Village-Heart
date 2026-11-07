#!/usr/bin/env bash
# Fails when the 2-player dedicated-server session (MultiplayerClientTests, M24) left an error or a disconnect in the client log.
#   scripts/check-mp-log.sh <log>   exit 0: clean, 1: a problem between the markers, 2: log missing/empty or the markers are missing
#
# The session is the stretch between the lines "MP_SESSION begin" and "MP_SESSION end" (both logged by the test). Inside it a line is a
# problem when
#   - it is an ERROR from the "Server thread" or from a villageheart logger, or
#   - it says "Disconnected", "Connection lost", "Timed out" or "Failed to decode" (any thread).
# Not matched on purpose (client-only noise in a sandbox without network or sound): Yggdrasil/authlib errors from their own threads
# ("Yggdrasil Key Fetcher", "Download-n"), the GL/X11 cursor errors and OpenAL/ALSA messages of the Render thread, "Ignoring chat
# session ... missing Services public key". Add a benign pattern here, with a comment, when a new one shows up; never loosen the rules above.
set -uo pipefail
log=${1:-}
[ -n "$log" ] && [ -s "$log" ] || { echo "check-mp-log: log missing or empty: ${log:-<none>}" >&2; exit 2; }
grep -q 'MP_SESSION begin' "$log" && grep -q 'MP_SESSION end' "$log" || { echo "check-mp-log: MP_SESSION begin/end markers missing: the session did not run to its end" >&2; exit 2; }
bad=$(awk '
  /MP_SESSION begin/ { inside = 1; next }
  /MP_SESSION end/   { inside = 0 }
  inside && (/\[Server thread\/ERROR\]/ || /\[[^]]*\/ERROR\] \(villageheart/ || /Disconnected|Connection lost|Timed out|Failed to decode/) { print NR ": " $0 }
' "$log")
if [ -n "$bad" ]; then
  echo "$bad"
  echo "check-mp-log: errors or disconnects during the 2-player session (lines above)" >&2
  exit 1
fi
exit 0
