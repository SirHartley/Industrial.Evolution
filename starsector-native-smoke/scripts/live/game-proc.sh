#!/usr/bin/env bash
# Checks or stops the one game a launch-game.sh run started, by the PID it wrote to WORK_DIR/game.pid.
# Never matches processes by name: the user may have their own game, an IDE or a Gradle daemon running Java.
#   game-proc.sh pid   WORK_DIR   print the PID (exit 1 when there is no pid file)
#   game-proc.sh alive WORK_DIR   exit 0 while that process runs
#   game-proc.sh stop  WORK_DIR   stop it, after checking its command line still names WORK_DIR
set -uo pipefail
(( $# == 2 )) || { sed -n '2,6p' "$0" >&2; exit 2; }
cmd=$1
WORK=$(cd -- "$2" 2>/dev/null && pwd) || { printf 'No such folder: %s\n' "$2" >&2; exit 2; }
# the file goes away when the game exits, possibly between two lines here
pid=$(tr -dc '0-9' 2>/dev/null < "$WORK/game.pid")
[[ -n $pid ]] || exit 1

if command -v tasklist.exe >/dev/null || command -v tasklist >/dev/null; then
    alive() { tasklist.exe /FI "PID eq $pid" /NH 2>/dev/null | grep -q " $pid "; }
    # the game's command line holds WORK_DIR's path, so a reused PID never matches
    owned() {
        local win cmdline
        win=$(cygpath -w -- "$WORK" 2>/dev/null || wslpath -w -- "$WORK")
        cmdline=$(powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Process -Filter 'ProcessId=$pid').CommandLine" 2>/dev/null)
        [[ ${cmdline,,} == *"${win,,}\\"* ]]
    }
    kill_it() { taskkill.exe /PID "$pid" /T /F >/dev/null 2>&1; }
else
    alive() { kill -0 "$pid" 2>/dev/null; }
    owned() { tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -qF "$WORK/"; }
    kill_it() { kill "$pid" 2>/dev/null; pause 3; kill -9 "$pid" 2>/dev/null; }
fi
pause() { sleep "$1" 2>/dev/null || read -rt "$1" < /dev/zero 2>/dev/null || true; }
# Git Bash turns /FI and /PID into paths without this
export MSYS_NO_PATHCONV=1

case $cmd in
    pid) printf '%s\n' "$pid" ;;
    alive) alive ;;
    stop)
        alive || { rm -f "$WORK/game.pid"; exit 0; }
        owned || { printf 'PID %s does not belong to %s; not stopping it\n' "$pid" "$WORK" >&2; exit 1; }
        kill_it
        for _ in 1 2 3 4 5 6 7 8 9 10; do alive || break; pause 1; done
        alive && { printf 'PID %s is still running\n' "$pid" >&2; exit 1; }
        rm -f "$WORK/game.pid"
        ;;
    *) sed -n '2,6p' "$0" >&2; exit 2 ;;
esac
