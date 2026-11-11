#!/usr/bin/env bash
# Runs gamectl.exe (window control, Windows only) from the machine's tools folder, building it on first use.
#   gamectl.sh path             print the exe's path, for many calls in a row: G=$(bash gamectl.sh path); "$G" bshot out.png
#   gamectl.sh COMMAND [ARG...] run one command: bshot, bmove, bclick, brclick, bkey, btype, hide, ... (list: top of gamectl.cs)
# Set GAMECTL_PID to the test game's PID (WORK_DIR/game.pid) first, so only that window is used.
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
(( $# )) || { sed -n '2,5p' "$0" >&2; exit 2; }
smoke_gamectl || exit 1
if [[ $1 == path ]]; then printf '%s\n' "$REPLY"; exit 0; fi
exec "$REPLY" "$@"
