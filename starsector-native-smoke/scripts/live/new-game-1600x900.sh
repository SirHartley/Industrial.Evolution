#!/usr/bin/env bash
# Windows example: from the title screen, starts a new small-sector game named "tester"
# (mercenary start, easy, no tutorial) with background input only.
# Coordinates are client pixels for a 1600x900 window on 0.98a-RC8 with no mods that change these screens.
# Every click comes after a move a few pixels away: without it the first click can only update hover state,
# and the key presses that follow then land on the wrong screen.
# Set GAMECTL_PID (WORK_DIR/game.pid) so input goes only to the test game.
set -euo pipefail
G=${GAMECTL:-$(bash "$(cd -- "$(dirname -- "$0")" && pwd)/gamectl.sh" path)}
wait_s() { sleep "$1" 2>/dev/null || read -t "$1" < /dev/zero 2>/dev/null || true; }
click() { "$G" bmove $(( $1 - 8 )) $(( $2 + 4 )); wait_s 0.5; "$G" bclick "$1" "$2"; wait_s "${3:-1}"; }
click 1222 396 3          # New Game
click 1022 241            # name field
"$G" btype tester
click 928 367             # turn help popups off
click 967 451             # small sector
click 388 620 3           # Continue
"$G" bkey 4; wait_s 2     # mercenary start
"$G" bkey 2; wait_s 2     # easy
"$G" bkey 2; wait_s 6     # skip tutorial
"$G" bkey G               # start game; the sector then generates
