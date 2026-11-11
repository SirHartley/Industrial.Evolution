#!/usr/bin/env bash
# Deletes a smoke work or output folder without following its links into the real mods and the checkout.
#   clean-work.sh DIR [DIR...]
# Use this, never rm -rf or Remove-Item -Recurse: after a hard kill the folder still holds junctions to GAME/mods and
# to the mod project, and a recursive delete goes through them. Refuses a folder that holds a running test game, a
# folder that is not a smoke folder, the game and a mod.
set -uo pipefail
(( $# )) || { sed -n '2,6p' "$0" >&2; exit 2; }
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
status=0
for arg in "$@"; do
    dir=$(cd -- "$arg" 2>/dev/null && pwd) || { echo "Gone already: $arg"; continue; }
    if [[ -e $dir/mod_info.json || -e $dir/starsector-core || -e $dir/starfarer.api.jar || -e $dir/.git ]]; then
        echo "REFUSED: $dir is a mod, a game or a checkout, not a smoke folder" >&2; status=1; continue
    fi
    # what the runners leave behind: run/ and runs/ (scripted checks), mods/ and logs/ (a live session), launcher.log (a probe)
    looks=
    for mark in run runs base-saves mods logs launcher.log slot1 slot2 slot3; do [[ -e $dir/$mark ]] && looks=1; done
    for sub in "$dir"/slot*/run "$dir"/*/slot*/run; do [[ -e $sub ]] && looks=1; done
    [[ -n $looks ]] || { echo "REFUSED: $dir does not look like a smoke folder (no run, runs, mods, logs or slot folders)" >&2; status=1; continue; }
    busy=
    while IFS= read -r pidfile; do
        bash "$HERE/game-proc.sh" alive "$(dirname -- "$pidfile")" && busy=$pidfile
    done < <(find "$dir" -maxdepth 4 -name game.pid 2>/dev/null)
    [[ -z $busy ]] || { echo "REFUSED: a test game is running ($busy). Stop it first: bash \"$HERE/game-proc.sh\" stop \"$(dirname -- "$busy")\"" >&2; status=1; continue; }
    if command -v cmd.exe >/dev/null 2>&1 && command -v cygpath >/dev/null 2>&1; then
        # rmdir /s removes a junction as a link and never enters it
        MSYS_NO_PATHCONV=1 cmd.exe /c rmdir /S /Q "$(cygpath -w -- "$dir")" >/dev/null 2>&1
    else
        # symlinks here, and rm never follows those
        rm -rf -- "$dir"
    fi
    if [[ -e $dir ]]; then echo "FAILED: $dir is still there (a file in it is open?)" >&2; status=1; else echo "Removed $dir"; fi
done
exit $status
