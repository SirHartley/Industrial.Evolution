#!/usr/bin/env bash
# scripted live checks: run.sh GROUP[,GROUP...] from git bash, groups in src/iecheck/Groups.java
# needs SMOKE_SKILL (starsector-native-smoke folder with gamectl.exe built) and SMOKE_JAVAC_DIR (jdk 17 bin)
# LIMIT seconds before the run is stopped, FAST=1 fast rendering, NOBUILD=1 reuses the jar, KEEP=1 leaves the game open
# JFR=0 skips the profile, PROFILE_BASE=<older profile.jfr> also writes compare.txt
# SMOKE_LOCK lock folder shared by all runs on this machine, LOCKWAIT seconds to wait for it (default 1800)
# NEWGAME=1 starts a new game instead of the base save, SWITCHES=off turns the content switches off first
# STAGE=<folder> loads that mod folder (an unpacked release) instead of the checkout
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
REPO=$(cd -- "$HERE/../.." && pwd)
GAME=${SMOKE_GAME:-"/e/Games/Starsector 0.98/Starsector 098a-RC8 - Modding"}
SKILL=$(cygpath -u "${SMOKE_SKILL:?set SMOKE_SKILL}")
LIVE="$REPO/out/live"
GC="$SKILL/scripts/live/gamectl.exe"
WORK="$LIVE/run"
L="$WORK/logs/starsector.log"
LOCK=${SMOKE_LOCK:-/c/Modding/.smoke-lock}
GROUP=${1:?group}
export PATH="$(cygpath -u "${SMOKE_JAVAC_DIR:?set SMOKE_JAVAC_DIR}"):$PATH"

w() { ping -n $(( $1 + 1 )) 127.0.0.1 >/dev/null; }
# the command line names this run's folders, another checkout or the user's own game stays alone
own() { powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -and \$_.CommandLine.Contains('$(cygpath -w "$WORK/mods")') } | ForEach-Object { \$_.ProcessId }" 2>/dev/null | tr -d '\r'; }
stop() { for pid in $(own); do powershell -NoProfile -Command "Stop-Process -Id $pid -Force -Confirm:\$false" >/dev/null 2>&1; done; w 2; }
running() { [[ -n $(own) ]]; }
anygame() { powershell -NoProfile -Command "if (Get-Process java -ErrorAction SilentlyContinue | Where-Object { \$_.Path -like '*Starsector*' }) { exit 0 } else { exit 1 }" >/dev/null 2>&1; }
launch() {
    : > "$L"
    SMOKE_FAST_RENDER=${FAST:-0} SMOKE_JAVA_ARGS="$1" bash "$SKILL/scripts/live/launch-game.sh" "$GAME" "$WORK" > "$WORK/launch.log" 2>&1 &
    for i in $(seq 1 40); do grep -q 'LIVE_CHECK application loaded' "$L" 2>/dev/null && return; w 2; done
}

# junctions must not outlive a run: a recursive delete of this folder would go through them into the real mods
unlink() {
    [[ -n ${KEEP:-} ]] && return
    for entry in "$WORK"/mods/*/ "$LIVE"/stage/Industrial.Evolution/{IndEvo,data,graphics}; do
        [[ -d $entry ]] && MSYS_NO_PATHCONV=1 cmd.exe /c rmdir "$(cygpath -w "${entry%/}")" >/dev/null 2>&1
    done
    rm -f "$WORK/mods/enabled_mods.json"
}

# one game per machine: gamectl finds the first game window, so two runs would click each other's game
take_lock() {
    for _ in $(seq 1 $(( ${LOCKWAIT:-1800} / 5 ))); do
        mkdir "$LOCK" 2>/dev/null && { echo "$(basename "$REPO") $GROUP" > "$LOCK/owner.txt"; HELD=1; return; }
        [[ -z ${said:-} ]] && { said=1; echo "Waiting for $(cat "$LOCK/owner.txt" 2>/dev/null)"; }
        w 5
    done
    echo "$LOCK still held by $(cat "$LOCK/owner.txt" 2>/dev/null)"; exit 1
}
leave() {
    unlink
    [[ -n ${HELD:-} ]] && { rm -f "$LOCK/owner.txt"; rmdir "$LOCK"; }
}
trap leave EXIT


mkdir -p "$LIVE/stage/Industrial.Evolution/jars" "$WORK/logs"
if [[ -z ${NOBUILD:-} || ! -f "$LIVE/IndEvo.jar" ]]; then
    cd "$REPO" || exit 1
    out=$(java jars/src/indevo/tools/ReleaseBuild.java compile 2>&1) || { echo "$out" | tail -20; exit 1; }
    jar=$(echo "$out" | sed -n 's/^Checked jar: \(.*\) (release jar left as is)\r\?$/\1/p' | tail -1)
    cp "$jar" "$LIVE/IndEvo.jar"
    rm -rf "$(dirname "$(cygpath -u "$jar")")"
fi
stage="$LIVE/stage/Industrial.Evolution"
cp "$LIVE/IndEvo.jar" "$stage/jars/IndEvo.jar"
cp "$REPO/mod_info.json" "$REPO/IndEvo.version" "$stage/"
for d in IndEvo data graphics; do
    [[ -e "$stage/$d" ]] || MSYS_NO_PATHCONV=1 cmd.exe /c mklink /J "$(cygpath -w "$stage/$d")" "$(cygpath -w "$REPO/$d")" >/dev/null
done

mod="$LIVE/ie_live_checks"
rm -rf "$mod"; mkdir -p "$mod"; cp -r "$HERE/src" "$HERE/data" "$HERE/mod_info.json" "$mod/"
bash "$SKILL/scripts/live/build-test-mod.sh" "$GAME" "$mod" ie_live_checks "$LIVE/IndEvo.jar" \
    "$GAME/mods/LunaLib/jars/LunaLib.jar" "$GAME/mods/LunaLib/jars/LunaLib-Kotlin.jar" "$GAME/starsector-core/starfarer_obf.jar" \
    "$GAME/starsector-core/fs.common_obf.jar" "$GAME/mods/LazyLib/jars/LazyLib.jar" "$GAME/mods/MagicLib/jars/MagicLib.jar" \
    "$GAME/mods/Console Commands/jars/lw_Console.jar" "$GAME/mods/PatchLib/jars/PatchLibAPI.jar" >/dev/null || exit 1
[[ -f "$WORK/mods/enabled_mods.json" ]] || bash "$SKILL/scripts/live/make-modset.sh" "$WORK" "$GAME/mods/LazyLib" "$GAME/mods/MagicLib" \
    "$GAME/mods/LunaLib" "$GAME/mods/GraphicsLib" "$GAME/mods/PatchLib" "$GAME/mods/Console Commands" "${STAGE:-$stage}" "$mod" >/dev/null

take_lock
anygame && { echo "A game is running. Close it or wait for its run to end."; exit 1; }

# one new game per checkout: every run starts from this save
if [[ ! -d "$LIVE/base-saves" ]]; then
    launch ""
    w 8
    SMOKE_RESOLUTION=1600x900 bash "$SKILL/scripts/live/new-game-1600x900.sh"
    for i in $(seq 1 90); do grep -q 'Saving stage 8' "$L" 2>/dev/null && break; w 2; done
    w 2
    stop
    cp -r "$WORK/saves" "$LIVE/base-saves"
fi

rm -rf "$WORK/saves"; rm -f "$WORK"/check-*.png "$WORK"/key-*
if [[ -n ${NEWGAME:-} ]]; then mkdir -p "$WORK/saves"; else cp -r "$LIVE/base-saves" "$WORK/saves"; fi
extra=""
[[ ${JFR:-1} != 0 ]] && extra=" -XX:StartFlightRecording=filename=$(cygpath -m "$WORK/profile.jfr"),settings=profile,dumponexit=true"
rm -f "$WORK/profile.jfr"
[[ -n ${SWITCHES:-} ]] && extra=" -Die.switches=$SWITCHES$extra"
launch "-Die.check=$GROUP$extra"
if [[ -n ${NEWGAME:-} ]]; then
    w 8
    SMOKE_RESOLUTION=1600x900 bash "$SKILL/scripts/live/new-game-1600x900.sh"
else
    for i in $(seq 1 60); do
        grep -q 'LIVE_CHECK game loaded' "$L" && break
        grep -q 'Error loading' "$L" && break
        "$GC" bmove 1220 283 >/dev/null 2>&1; w 1; "$GC" bclick 1234 283 >/dev/null 2>&1; w 1
    done
fi

limit=${LIMIT:-600}; start=$SECONDS; verdict=TIMEOUT
while (( SECONDS - start < limit )); do
    grep -q 'LIVE_CHECK COMPLETE' "$L" && { verdict=COMPLETE; break; }
    grep -q 'Error loading' "$L" && { verdict=LOAD_FAILED; break; }
    for s in $(grep -o 'LIVE_CHECK shot [a-z0-9-]*' "$L" | cut -d' ' -f3); do
        [[ -f "$WORK/check-$s.png" ]] || "$GC" bshot "$WORK/check-$s.png" >/dev/null 2>&1
    done
    for k in $(grep -o 'LIVE_CHECK key [A-Za-z0-9_]*' "$L" | cut -d' ' -f3); do
        [[ -f "$WORK/key-$k" ]] || { "$GC" bkey "${k%_*}" >/dev/null 2>&1; : > "$WORK/key-$k"; }
    done
    running || { verdict=CRASH; break; }
    w 1
done
# the game exits on its own when the checks end, sometimes before the last poll
grep -q 'LIVE_CHECK COMPLETE' "$L" && verdict=COMPLETE
# a screen says at once what a stuck run is doing
[[ $verdict != COMPLETE && $verdict != CRASH ]] && { "$GC" bshot "$(cygpath -m "$WORK/stuck.png")" >/dev/null 2>&1; echo "$verdict: open $WORK/stuck.png"; }
[[ -n ${KEEP:-} ]] && exit 0
# let the jvm write the recording on exit
[[ $verdict == COMPLETE && ${JFR:-1} != 0 ]] && for i in $(seq 1 30); do running || break; w 1; done
stop

grep -E 'LIVE_CHECK' "$L" | sed -E 's/^[0-9]+ +\[[^]]*\] [A-Z]+ +[a-zA-Z.]+ +- //' | grep -vE '^LIVE_CHECK (step [0-9]|shot |key |saving|quick load)' > "$WORK/results.txt"
grep -E 'FAIL|COMPLETE' "$WORK/results.txt" | cut -c1-170
# vanilla .ship files without a csv row, fast rendering probing plugin classes
errors=$(grep -E '\] (ERROR|FATAL) ' "$L" | grep -vE 'Ship hull spec \[|ScriptLoader  - Error while initializing plugin|LIVE_CHECK' | sed -E 's/^[0-9]+ +\[[^]]*\] //' | cut -c1-200 | sort | uniq -c | sort -rn)
[[ -n $errors ]] && { echo "--- log errors"; echo "$errors" | head -20; verdict=LOG_ERRORS; }
if [[ -s "$WORK/profile.jfr" && -f "$SKILL/scripts/live/jfr-top.sh" ]]; then
    bash "$SKILL/scripts/live/jfr-top.sh" "$WORK/profile.jfr" indevo > "$WORK/profile.txt" 2>&1
    grep -E '^PROFILE' "$WORK/profile.txt" | cut -c1-170
    if [[ -n ${PROFILE_BASE:-} ]]; then bash "$SKILL/scripts/live/jfr-top.sh" --compare "$PROFILE_BASE" "$WORK/profile.jfr" indevo > "$WORK/compare.txt" 2>&1; fi
fi
echo "$verdict: $(grep -c ' ok: ' "$WORK/results.txt") ok, $(grep -c 'FAIL' "$WORK/results.txt") failed, $SECONDS s, results in $WORK/results.txt"
[[ $verdict == COMPLETE ]] && ! grep -q FAIL "$WORK/results.txt"
