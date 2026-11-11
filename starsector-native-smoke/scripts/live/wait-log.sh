#!/usr/bin/env bash
# Waits until the game log stops growing for a few seconds (a load finished), or a pattern appears.
#   wait-log.sh LOG_FILE [pattern] [maxSeconds] [fromLine]
# LOG_FILE is WORK_DIR/logs/starsector.log of a launch-game.sh run; the game is the PID in WORK_DIR/game.pid.
# Prints "matched: ..." with the log's line count ("at line N"), "log quiet after Ns" or "TIMEOUT" (exit 2). Prints
# "GAME EXITED" (exit 1) when the game is gone, and "GAME CRASHED" (exit 3) when the game logged a fatal error: its
# modal "Fatal" box keeps the process alive.
# Only lines after fromLine count for the pattern. The default is the line count when this script starts, so a line
# the game wrote between your action and this call is missed: pass the "at line N" of your last wait, or 0 after
# clearing the log (": > LOG_FILE") before the action.
L=$1
pattern=${2:-}
max=${3:-300}
WORK=$(dirname -- "$(dirname -- "$L")")
PROC="$(cd -- "$(dirname -- "$0")" && pwd)/game-proc.sh"
start=${4:-$(wc -l < "$L" 2>/dev/null || echo 0)}
quiet=0
last=$start
# real seconds: on a busy machine one pass of this loop takes far longer than the second it sleeps
t0=$SECONDS
while (( SECONDS - t0 < max )); do
    sleep 1 2>/dev/null || read -t 1 < /dev/zero 2>/dev/null
    # the pid file appears a moment after launch
    if [[ -r "$WORK/game.pid" ]] && ! bash "$PROC" alive "$WORK"; then
        echo "GAME EXITED"
        exit 1
    fi
    if grep -a -q 'ERROR com.fs.starfarer.combat.CombatMain' "$L" 2>/dev/null; then
        echo "GAME CRASHED: capture the window, then run game-proc.sh stop $WORK"
        exit 3
    fi
    now=$(wc -l < "$L" 2>/dev/null || echo 0)
    if [[ -n $pattern ]] && tail -n +"$((start + 1))" "$L" | grep -a -qE "$pattern"; then
        echo "matched: $pattern at line $now"
        exit 0
    fi
    # An empty log means the game has not started writing yet; that is not "quiet".
    if [[ $now -gt 0 && $now -eq $last ]]; then quiet=$((quiet + 1)); else quiet=0; fi
    last=$now
    if [[ -z $pattern && $quiet -ge 4 && $(( SECONDS - t0 )) -ge 5 ]]; then
        echo "log quiet after $(( SECONDS - t0 ))s at line $now"
        exit 0
    fi
done
echo "TIMEOUT"
exit 2
