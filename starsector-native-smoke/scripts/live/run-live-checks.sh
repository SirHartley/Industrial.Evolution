#!/usr/bin/env bash
# Scripted live checks in the real game, from Git Bash on Windows. Boots an isolated game, loads a base save, runs the
# groups named on the command line in one game, prints the failures and exits 0 only when everything passed.
#   run-live-checks.sh --project PROJECT_DIR GROUP[,GROUP...]
#   run-live-checks.sh GAME_ROOT WORK_DIR CHECK_MOD_DIR GROUP[,GROUP...] MOD [MOD...]
# --project is the short form (smoke.sh checks calls it): everything comes from PROJECT_DIR/native-smoke/live/project.sh
# (smoke.sh init writes it) and from the machine's settings (smoke.sh setup). The long form names everything:
#   GAME_ROOT      the install folder
#   WORK_DIR       scratch folder for this project, reused between runs (mods, saves, logs, base-saves). {slot} in it
#                  becomes the slot number, so games in different slots never share a folder: .../myproject/slot{slot}
#   CHECK_MOD_DIR  your copy of templates/live-check-mod; it is copied to WORK_DIR/run/check-mod and built there
#   MOD            the mods to enable: a mod folder, or a mod_info.json id found in GAME_ROOT/mods (highest version
#                  wins). Their own dependencies are added. Stage the mod under test first when its jar is not in place.
# GROUP all runs every group in the check mod's Groups.java except battle.
# Each run's results, log, captures and profile are copied to WORK_DIR/runs/<time>-<groups>/.
# The last line is the verdict (references/scripted-checks.md lists them); only COMPLETE means every check passed.
# The run takes a slot from smoke-slot.sh and frees it at the end, unless SMOKE_SLOT holds one the session took.
# It ends with exit 4 and a STOPPED line when the machine is paused, its owner is cancelled or the shell that started it
# has ended (a stopped task), and stops its game on the way out.
# Optional environment:
#   SMOKE_OWNER      owner for the slot lock (default: OWNER.checks from the project file, or the project folder of
#                    WORK_DIR, myproject in .../myproject/slot{slot})
#   PERF             1 takes the machine alone (smoke-slot.sh --perf): for frame cost, load time, days per second and
#                    before/after profiles. Waits until every other game has ended. 0 shares the machine; unset uses
#                    the machine's default (smoke-slot.sh config)
#   SMOKE_DETACHED   1 keeps the run going after the shell that started it has ended; stop it with smoke-slot.sh cancel
#   SMOKE_JAVAC_DIR  JDK 17+ bin folder when javac is not on PATH and smoke.sh setup stored none
#   CHECK_JARS       ';'-separated extra jars the checks compile against. The runner adds the jars of every enabled
#                    mod itself (the mod_info.json entries and every other jar in its jars folder)
#   SMOKE_JAVA_ARGS  extra JVM arguments for the game, space-separated, or one per line
#   FAST             1 uses Fast Rendering when it is installed: boots about 8 s faster, fine for development, use 0
#                    for the final run
#   JFR              0 turns off the Java Flight Recorder profile of the run (WORK_DIR/run/profile.jfr, report in profile.txt)
#   PROFILE_PKG      the mod's package prefix, so the profile reports and warns about your code
#   PROFILE_BASE     an earlier profile.jfr of the same groups, the run then also writes compare.txt against it
#   LIMIT            seconds before the run counts as timed out (default 600)
#   SMOKE_STALE_OK   1 starts the game even when a mod jar is older than its sources (STALE_JAR)
#   NEWGAME          1 runs the groups on a fresh new game instead of the base save (what a new game generates)
#   NEWBASE          1 makes a new base save even when one exists
#   KEEP             1 leaves the game open after the last step, for gamectl clicks and captures. A group can do the
#                    same at any step with c.hold(). The run then ends with HELD or COMPLETE and says how to stop the game
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
smoke_root || exit 2
smoke_version; VERSION=$REPLY
SMOKE_SH="$(cd -- "$HERE/.." && pwd)/smoke.sh"
usage() { sed -n '2,14p' "$0" >&2; exit 2; }

PROJECT=
if [[ ${1:-} == --project ]]; then
    (( $# == 3 )) || usage
    PROJECT=$(cd -- "$2" 2>/dev/null && pwd) || { echo "NO_PROJECT: no such folder: $2" >&2; exit 2; }
    GROUP=$3
    PFILE="$PROJECT/native-smoke/live/project.sh"
    [[ -r $PFILE ]] || { echo "NO_PROJECT: $PFILE is missing. Create it: bash \"$SMOKE_SH\" init \"$PROJECT\"" >&2; exit 2; }
    OWNER=; DEPS=(); CHECK_DIR="$PROJECT/native-smoke/live/check-mod"
    java_args() { :; }
    . "$PFILE"
    [[ $OWNER =~ ^[A-Za-z0-9._-]+$ ]] || { echo "BAD_PROJECT: OWNER in $PFILE must be letters, digits, '.', '_' or '-'" >&2; exit 2; }
    GAME=${GAME_ROOT:-}
    [[ -n $GAME ]] || { smoke_cfg game_root; GAME=$REPLY; }
    [[ -n $GAME ]] || { echo "NO_SETUP: no game folder is stored for this machine. Run once: bash \"$SMOKE_SH\" setup \"<Starsector folder>\"" >&2; exit 2; }
    smoke_posix_path "$GAME"; GAME=$REPLY
    work_root=${SMOKE_WORK_ROOT:-}
    [[ -n $work_root ]] || { smoke_cfg work_root; work_root=${REPLY:-$SMOKE_ROOT_DIR/work}; }
    smoke_posix_path "$work_root"
    LIVE="$REPLY/$OWNER/slot{slot}"
    CHECK=$CHECK_DIR
    : "${SMOKE_OWNER:=$OWNER.checks}"
    : "${PROFILE_PKG:=}"
    set --
else
    (( $# >= 5 )) || usage
    GAME=$1; LIVE=$2; CHECK=$3; GROUP=$4; shift 4
fi
[[ -d $GAME ]] || { echo "NO_GAME: no such folder: $GAME" >&2; exit 2; }
[[ -r $CHECK/mod_info.json ]] || { echo "NO_CHECK_MOD: no mod_info.json in $CHECK (a copy of templates/live-check-mod)" >&2; exit 2; }

# a wrong group name costs a whole game start, so read the names from the check mod first
known=()
groups_file=$(find "$CHECK/src" -name Groups.java 2>/dev/null | head -n 1)
if [[ -n $groups_file ]]; then
    mapfile -t known < <(grep -o -E 'case +"[^"]+" *:' "$groups_file" | sed -E 's/.*"([^"]+)".*/\1/')
fi
if [[ $GROUP == all ]]; then
    GROUP=
    for g in "${known[@]}"; do [[ $g == battle ]] || GROUP+="${GROUP:+,}$g"; done
    [[ -n $GROUP ]] || { echo "UNKNOWN_GROUP: no case \"name\" labels in a Groups.java under $CHECK/src; name the groups" >&2; exit 2; }
elif (( ${#known[@]} )); then
    IFS=, read -r -a asked <<< "$GROUP"
    for g in "${asked[@]}"; do
        [[ " ${known[*]} " == *" $g "* ]] || { echo "UNKNOWN_GROUP: $g. Groups in Groups.java: ${known[*]}" >&2; exit 2; }
    done
fi

# one slot per game; a slot the session took already comes in SMOKE_SLOT and stays the session's
OWN_SLOT=
if [[ ${SMOKE_LOCK:-1} != 0 && -z ${SMOKE_SLOT:-} ]]; then
    if [[ $LIVE == *'{slot}'* ]]; then OWN_SLOT=$(basename -- "$(dirname -- "$LIVE")"); else OWN_SLOT=$(basename -- "$LIVE"); fi
    OWN_SLOT=${SMOKE_OWNER:-$OWN_SLOT}
    perf=()
    [[ ${PERF:-} == 1 ]] && perf=(--perf)
    [[ ${PERF:-} == 0 ]] && perf=(--shared)
    SMOKE_SLOT=$(bash "$HERE/smoke-slot.sh" acquire "$OWN_SLOT" "${perf[@]}" --holder $$) || exit $?
    export SMOKE_SLOT SMOKE_OWNER=$OWN_SLOT
fi
LIVE=${LIVE//\{slot\}/${SMOKE_SLOT:-0}}
WORK="$LIVE/run"
L="$WORK/logs/starsector.log"
# 1 once the game is left open for the session (KEEP=1 or c.hold()): then the links and the slot stay
OPEN=
MAKER="$(dirname -- "$LIVE")/base-saves.making"
MAKING=
STAGING="$(dirname -- "$LIVE")/staging.lock"
HOLD_STAGING=
release_slot() {
    [[ -n $OWN_SLOT ]] || return 0
    if [[ -n $OPEN ]] && bash "$HERE/game-proc.sh" alive "$WORK"; then
        bash "$HERE/smoke-slot.sh" keep "$SMOKE_SLOT"
    else
        bash "$HERE/smoke-slot.sh" release "$SMOKE_SLOT" "$OWN_SLOT" 2>/dev/null
    fi
}
trap release_slot EXIT
# so the EXIT trap stops the game and frees the slot when smoke-slot.sh cancel or the user ends this script
trap 'exit 143' TERM
trap 'exit 130' INT
smoke_jdk || { echo "NO_JDK: javac (JDK 17+) not found. Run: bash \"$SMOKE_SH\" setup, or set SMOKE_JAVAC_DIR to a JDK's bin folder" >&2; exit 1; }
smoke_gamectl || { echo "NO_GAMECTL: gamectl.exe could not be built (Windows only), see the lines above" >&2; exit 1; }
GC=$REPLY
export GAMECTL=$GC
mkdir -p "$WORK/logs"
rm -f "$WORK/hide.log"
echo "starsector-native-smoke $VERSION, groups $GROUP, work folder $LIVE"

# A task stop can end the shell that started this script and leave the script running. It then stops itself at the
# next wait: a run nobody watches must not hold a slot or start games.
starters=() starter_win=()
if [[ ${SMOKE_DETACHED:-0} != 1 ]]; then
    p=$PPID; rp=') '
    while [[ $p =~ ^[0-9]+$ ]] && (( p > 1 && ${#starters[@]} < 8 )); do
        starters+=("$p")
        w=$p; read -r w 2>/dev/null < "/proc/$p/winpid"; starter_win+=("$w")
        st=; read -r st 2>/dev/null < "/proc/$p/stat" || break
        st=${st##*"$rp"}; read -r -a f <<< "$st"; p=${f[1]:-1}
    done
fi
STOPPING=
gone() {
    [[ -z $STOPPING ]] || return 1
    local i
    for i in "${!starters[@]}"; do
        kill -0 "${starters[i]}" 2>/dev/null || { echo "STOPPED: process ${starter_win[i]} that started this run has ended; stopping the run" >&2; return 0; }
    done
    return 1
}
w() { gone && exit 4; ping -n $(( $1 + 1 )) 127.0.0.1 >/dev/null; }
# only the game this runner started: the user may have their own game, an IDE or a Gradle daemon running Java
running() { bash "$HERE/game-proc.sh" alive "$WORK"; }
# the game's crash handler logs this, then shows a modal "Fatal" box that keeps the process alive
crashed() { grep -a -q 'ERROR com.fs.starfarer.combat.CombatMain' "$L" 2>/dev/null; }
stop() { bash "$HERE/game-proc.sh" stop "$WORK"; w 2; }
shot() { "$GC" bshot "$(cygpath -m "$1")" >/dev/null 2>&1; }
# the check mod waits for this file after c.shot and c.key (common files of the game: saves/common/NAME.data)
ack() { mkdir -p "$WORK/saves/common"; : > "$WORK/saves/common/livecheck_ack_$1.data"; }
# mkdir is the lock, the pid inside tells a dead owner from a live one
take_lock() {
    local other
    if ! mkdir "$1" 2>/dev/null; then
        other=$(cat "$1/pid" 2>/dev/null)
        [[ -n $other ]] && kill -0 "$other" 2>/dev/null && return 1
        rm -rf "$1"; mkdir "$1" 2>/dev/null || return 1
    fi
    echo $$ > "$1/pid"
}

# One runner per WORK_DIR: two runners would share one game, log and save folder.
LOCK="$LIVE/runner.lock"
take_lock "$LOCK" || { echo "BUSY: another run-live-checks.sh (PID $(cat "$LOCK/pid" 2>/dev/null)) is using $LIVE. Wait for it to finish, or put {slot} in WORK_DIR" >&2; exit 2; }
if running; then
    echo "BUSY: a game from an earlier run is still open (PID $(cat "$WORK/game.pid")). Stop it: bash \"$HERE/game-proc.sh\" stop \"$WORK\"" >&2
    rm -rf "$LOCK"; exit 2
fi

# FAST=1 uses fast rendering where it is installed; the RUN line of the results says whether it was on
fast_render=0
[[ ${FAST:-0} == 1 ]] && fast_render=auto
launch() {
    # clear first: a wait on the log must never match the previous run
    : > "$L"
    rm -f "$WORK/game.pid"
    # the title screen clicks below are for 1600x900. the line break makes launch-game.sh read one argument per line
    SMOKE_RESOLUTION=1600x900 SMOKE_FAST_RENDER=$fast_render SMOKE_JAVA_ARGS="$1"$'\n' bash "$HERE/launch-game.sh" "$GAME" "$WORK" > "$WORK/launch.log" 2>&1 &
    local job=$! why
    for _ in $(seq 1 30); do [[ -r "$WORK/game.pid" ]] && break; kill -0 "$job" 2>/dev/null || break; w 1; done
    export GAMECTL_PID=$(cat "$WORK/game.pid" 2>/dev/null)
    if [[ -z $GAMECTL_PID ]]; then
        # paused, cancelled or the slot went away before the game started
        why=$(grep -m 1 '^STOPPED' "$WORK/launch.log")
        [[ -n $why ]] && { echo "$why" >&2; exit 4; }
        echo "NO_GAME_PID: the game did not start, read $WORK/launch.log (last lines below)" >&2
        tail -n 5 "$WORK/launch.log" >&2
        exit 1
    fi
    for _ in $(seq 1 60); do
        grep -a -q 'LIVE_CHECK application loaded' "$L" 2>/dev/null && return
        crashed && return
        running || return
        if grep -a -q 'Running vanilla game with no mods' "$L" 2>/dev/null; then
            echo "MODS_NOT_LOADED: the game found none of the mods in $WORK/mods (its log says 'Running vanilla game with no mods'): the junctions there do not resolve for it. Run: bash \"$SMOKE_SH\" doctor" >&2
            exit 1
        fi
        w 2
    done
    # without the check mod nothing would end the run but the time limit
    shot "$WORK/not-loaded.png"
    echo "CHECK_MOD_NOT_LOADED: no 'LIVE_CHECK application loaded' line in 120 s. Open $WORK/not-loaded.png and read $L" >&2
    exit 1
}
# one JVM argument per line: the caller's, the project's, then this run's
jvm_args() {
    local a
    if [[ ${SMOKE_JAVA_ARGS:-} == *$'\n'* ]]; then
        printf '%s\n' "$SMOKE_JAVA_ARGS"
    else
        for a in ${SMOKE_JAVA_ARGS:-}; do printf '%s\n' "$a"; done
    fi
    [[ -n $PROJECT ]] && java_args
    (( $# )) && printf '%s\n' "$@"
}
unlink_all() {
    local entry
    for entry in "$@"; do
        [[ -d $entry ]] && MSYS_NO_PATHCONV=1 cmd.exe /c rmdir "$(cygpath -w "${entry%/}")" >/dev/null 2>&1
    done
}

# At exit: stop our game, and remove the junctions. A recursive delete of this folder would otherwise go through them
# into the real mods.
cleanup() {
    STOPPING=1
    rm -rf "$LOCK"
    [[ -n $MAKING ]] && rm -rf "$MAKER"
    [[ -n $HOLD_STAGING ]] && rm -rf "$STAGING"
    if [[ -z $OPEN ]]; then
        running && stop
        unlink_all "$WORK"/mods/*/ "$WORK"/staged/*/*/
        rm -f "$WORK/mods/enabled_mods.json"
    fi
    release_slot
}
trap cleanup EXIT

# the mod under test, built and staged by the project's own stage_mod, or linked with its built jars copied
stale_dirs=()
if [[ -n $PROJECT ]]; then
    STAGE="$WORK/staged/${PROJECT##*/}"; STAGED=$STAGE; LIVE_SCRIPTS=$HERE
    # one build per project at a time: runs in other slots use the same checkout and build folder
    for _ in $(seq 1 120); do take_lock "$STAGING" && { HOLD_STAGING=1; break; }; w 5; done
    [[ -n $HOLD_STAGING ]] || { echo "BUSY: another run has been staging this project for 10 minutes ($STAGING)" >&2; exit 2; }
    echo "Staging $PROJECT"
    if declare -F stage_mod >/dev/null; then
        stage_mod > "$WORK/stage.log" 2>&1
    else
        stale_dirs+=("$PROJECT")
        bash "$HERE/stage-mod.sh" "$PROJECT" "$STAGE" > "$WORK/stage.log" 2>&1
    fi || { echo "STAGE_FAILED: the mod under test was not built or staged, read $WORK/stage.log (last lines below)" >&2; tail -n 20 "$WORK/stage.log" >&2; exit 1; }
    rm -rf "$STAGING"; HOLD_STAGING=
    [[ -f $STAGED/mod_info.json ]] || { echo "STAGE_FAILED: no mod_info.json in $STAGED; stage_mod must leave the mod in \$STAGE or set STAGED" >&2; exit 1; }
    set -- "${DEPS[@]}" "$STAGED"
fi

mod_info() { smoke_mod_info "$1"; }
mods=() ids=' '
for mod in "$@"; do
    [[ -d $mod ]] || mod=$(bash "$HERE/find-mod.sh" "$GAME" "$mod") || { echo "MISSING_MOD: install it in $GAME/mods, or fix the id" >&2; exit 1; }
    [[ -r $mod/mod_info.json ]] || { echo "MISSING_MOD: no mod_info.json in $mod" >&2; exit 1; }
    mods+=("$mod")
    mod_info "$mod"
    [[ $REPLY =~ \"id\"[[:space:]]*:[[:space:]]*\"([^\"]*)\" ]] && ids+="${BASH_REMATCH[1],,} "
done
# what the mods need themselves, so a short list is enough
i=0
while (( i < ${#mods[@]} )); do
    mod=${mods[i]}; i=$(( i + 1 ))
    mod_info "$mod"
    [[ $REPLY =~ \"dependencies\"[[:space:]]*:[[:space:]]*\[([^]]*)\] ]] || continue
    list=${BASH_REMATCH[1]}
    while [[ $list =~ \"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]]; do
        dep=${BASH_REMATCH[1]}; list=${list#*"${BASH_REMATCH[0]}"}
        [[ $ids == *" ${dep,,} "* ]] && continue
        found=$(bash "$HERE/find-mod.sh" "$GAME" "$dep") || { echo "MISSING_MOD: ${mod##*/} needs $dep, which is not in $GAME/mods" >&2; exit 1; }
        echo "Added dependency $dep: $found"
        mods+=("$found"); ids+="${dep,,} "
    done
done
# a jar older than its sources (a pull without a build) crashes at load after a whole game start; catch it here.
# copies made within 2 minutes of the jar count as current, so a staged copy of a built mod passes
stale=()
jars=()
IFS=';' read -r -a extra <<< "${CHECK_JARS:-}"
for jar in "${extra[@]}"; do [[ -n $jar ]] && jars+=("$(cygpath -u "$jar")"); done
for mod in "${mods[@]}" "${stale_dirs[@]}"; do
    src=
    for d in "$mod/jars/src" "$mod/src"; do [[ -d $d ]] && { src=$d; break; }; done
    mod_info "$mod"
    [[ $REPLY =~ \"jars\"[[:space:]]*:[[:space:]]*\[([^]]*)\] ]] || continue
    list=${BASH_REMATCH[1]}
    while [[ $list =~ \"([^\"]+)\" ]]; do
        jar=$mod/${BASH_REMATCH[1]}; list=${list#*\"*\"}
        if [[ ! -f $jar ]]; then stale+=("$jar is missing"); continue; fi
        # the checks compile against what the game will load
        [[ $mod == "$PROJECT" ]] || jars+=("$jar")
        [[ -n $src ]] || continue
        jt=$(find "$jar" -printf '%T@'); newest=0
        while read -r t; do t=${t%.*}; (( t > newest )) && newest=$t; done < <(find "$src" -name '*.java' -newer "$jar" -printf '%T@\n')
        (( newest > ${jt%.*} + 120 )) && stale+=("$jar is older than the sources in $src")
    done
done
# jars a mod ships next to its own without listing them (PatchLibAPI.jar): for compiling the checks only
for mod in "${mods[@]}"; do
    for jar in "$mod"/jars/*.jar; do
        [[ -f $jar && " ${jars[*]} " != *" $jar "* ]] && jars+=("$jar")
    done
done
if (( ${#stale[@]} )) && [[ ${SMOKE_STALE_OK:-0} != 1 ]]; then
    printf 'STALE_JAR: %s\n' "${stale[@]}" >&2
    echo "Build the jar or stage a built copy of the mod first. SMOKE_STALE_OK=1 starts the game anyway" >&2
    exit 1
fi
jars+=("$GAME/starsector-core/starfarer_obf.jar" "$GAME/starsector-core/fs.common_obf.jar")
# built in a copy: a game in another slot may have the source folder's jar open, and Windows locks it
STAGED_CHECK="$WORK/check-mod"
if [[ $(cd -- "$CHECK" && pwd) != "$STAGED_CHECK" ]]; then
    rm -rf -- "$STAGED_CHECK"
    mkdir -p "$STAGED_CHECK"
    cp -r -- "$CHECK"/. "$STAGED_CHECK"/
fi
# the jar is named by the check mod's own mod_info.json
check_jar=live_checks
mod_info "$STAGED_CHECK"
[[ $REPLY =~ \"jars\"[[:space:]]*:[[:space:]]*\[[[:space:]]*\"jars/([^\"]+)\.jar\" ]] && check_jar=${BASH_REMATCH[1]}
bash "$HERE/build-test-mod.sh" "$GAME" "$STAGED_CHECK" "$check_jar" "${jars[@]}" >/dev/null ||
    { echo "BUILD_FAILED: the check mod did not compile, javac's errors are above. Fix the sources in $CHECK" >&2; exit 1; }
# always rebuilt: after a hard kill or an open game the old set can be stale
bash "$HERE/make-modset.sh" "$WORK" "${mods[@]}" "$STAGED_CHECK" > "$WORK/modset.log" 2>&1 ||
    { echo "MODSET_FAILED: read $WORK/modset.log (last lines below)" >&2; tail -n 5 "$WORK/modset.log" >&2; exit 1; }
enabled=$(sed -E 's/.*\[(.*)\].*/\1/; s/"//g' "$WORK/mods/enabled_mods.json")

# another slot of this project with the same mod list may have a base save already: copying it skips a 90 s new game
copy_base() {
    local other
    for other in "$(dirname -- "$LIVE")"/*/base-saves.mods; do
        [[ -f $other && $other != "$LIVE/base-saves.mods" ]] && cmp -s "$other" "$WORK/mods/enabled_mods.json" || continue
        rm -rf -- "$LIVE/base-saves.tmp"
        # the other slot may replace its save meanwhile, so check its mod list again after the copy
        if cp -r -- "${other%.mods}" "$LIVE/base-saves.tmp" 2>/dev/null && cmp -s "$other" "$WORK/mods/enabled_mods.json"; then
            mv -- "$LIVE/base-saves.tmp" "$LIVE/base-saves"
            cp -- "$other" "$LIVE/base-saves.mods"
            echo "Base save copied from ${other%.mods}"
            return 0
        fi
        rm -rf -- "$LIVE/base-saves.tmp"
    done
    return 1
}
base=reused
if [[ ${NEWGAME:-0} == 1 ]]; then
    base='none (new game)'
else
    if [[ ${NEWBASE:-0} == 1 ]]; then
        rm -rf "$LIVE/base-saves" "$LIVE/base-saves.mods"
    elif [[ -d "$LIVE/base-saves" ]] && ! cmp -s "$WORK/mods/enabled_mods.json" "$LIVE/base-saves.mods"; then
        # The base save belongs to the mod list it was made with. A different list needs a new one.
        echo "Mod list changed since the base save was made; making a new one"
        rm -rf "$LIVE/base-saves" "$LIVE/base-saves.mods"
    fi
    if [[ ! -d "$LIVE/base-saves" ]]; then
        base=copied
        # one maker per project: slots that start together would each pay for the same new game
        for _ in $(seq 1 120); do
            [[ ${NEWBASE:-0} != 1 ]] && copy_base && break
            take_lock "$MAKER" && { MAKING=1; break; }
            w 5
        done
    fi
    # one new game per mod list, about 90 s. every run then starts from this save
    if [[ ! -d "$LIVE/base-saves" ]]; then
        [[ -n $MAKING ]] || { echo "BUSY: another slot has been making the base save for 10 minutes ($MAKER)" >&2; exit 2; }
        base=new
        echo "Making the base save (a new game, about 90 s)"
        rm -rf "$WORK/saves"
        launch "$(jvm_args)"
        made=
        if ! crashed; then
            w 8
            bash "$HERE/new-game-1600x900.sh"
            for _ in $(seq 1 90); do grep -a -q 'Saving stage 8' "$L" 2>/dev/null && { made=1; break; }; crashed && break; running || break; w 2; done
        fi
        if [[ -z $made ]] && crashed; then
            w 2; shot "$WORK/fatal.png"
            echo "FATAL: the game crashed before the base save, open $WORK/fatal.png and read the ERROR lines in $L" >&2
            exit 1
        fi
        if [[ -z $made ]]; then
            shot "$WORK/new-game-failed.png"
            echo "NEW_GAME_FAILED: no save after the new-game clicks. Open $WORK/new-game-failed.png; the coordinates are for 1600x900 and an unmodded title screen" >&2
            exit 1
        fi
        w 2
        stop
        rm -rf -- "$LIVE/base-saves.tmp"
        cp -r "$WORK/saves" "$LIVE/base-saves.tmp" && mv -- "$LIVE/base-saves.tmp" "$LIVE/base-saves"
        cp "$WORK/mods/enabled_mods.json" "$LIVE/base-saves.mods"
    fi
    [[ -n $MAKING ]] && { rm -rf "$MAKER"; MAKING=; }
fi

rm -rf "$WORK/saves"; rm -f "$WORK"/check-*.png "$WORK"/key-* "$WORK"/{stuck,fatal,new-game-failed}.png "$WORK/profile.jfr" "$WORK/threads.txt"
if [[ ${NEWGAME:-0} == 1 ]]; then mkdir -p "$WORK/saves"; else cp -r "$LIVE/base-saves" "$WORK/saves"; fi
run_args=("-Dlive.check=$GROUP")
[[ ${KEEP:-0} == 1 ]] && run_args+=(-Dlive.keep=true)
[[ ${JFR:-1} != 0 ]] && run_args+=("-XX:StartFlightRecording=filename=$(cygpath -m "$WORK/profile.jfr"),settings=profile,dumponexit=true")
launch "$(jvm_args "${run_args[@]}")"

if [[ ${NEWGAME:-0} == 1 ]]; then
    crashed || { w 8; bash "$HERE/new-game-1600x900.sh"; }
else
    # clicks on the title screen before it is ready do nothing, so click until the save has loaded
    for _ in $(seq 1 60); do
        grep -a -q 'LIVE_CHECK game loaded' "$L" && break
        grep -a -q 'Error loading' "$L" && break
        crashed && break
        running || break
        "$GC" bmove 1220 283 >/dev/null 2>&1; w 1; "$GC" bclick 1234 283 >/dev/null 2>&1; w 1
    done
fi

# other games in the run's time make its absolute timings incomparable, so the results say how many ran
others_max=0; tick=0
others() {
    [[ -n ${SMOKE_SLOT:-} ]] || return 0
    local n; n=$(bash "$HERE/smoke-slot.sh" others "$SMOKE_SLOT")
    (( ${n:-0} > others_max )) && others_max=$n
}
others
limit=${LIMIT:-600}; start=$SECONDS; verdict=TIMEOUT
while (( SECONDS - start < limit )); do
    (( tick++ % 10 == 0 )) && others
    # one pass over the log per poll: every grep is a process, and Git Bash pays up to a second for each
    seen=$(grep -a -o -E 'LIVE_CHECK (COMPLETE|hold|shot [a-z0-9-]+|key [A-Za-z0-9]+_[0-9]+)|Error loading|ERROR com\.fs\.starfarer\.combat\.CombatMain' "$L" 2>/dev/null)
    while IFS= read -r line; do
        case $line in
            'LIVE_CHECK shot '*)
                name=${line#LIVE_CHECK shot }
                [[ -f "$WORK/check-$name.png" ]] || { shot "$WORK/check-$name.png"; ack "shot_$name"; }
                ;;
            'LIVE_CHECK key '*)
                key=${line#LIVE_CHECK key }
                [[ -f "$WORK/key-$key" ]] || { "$GC" bkey "${key%_*}" >/dev/null 2>&1; : > "$WORK/key-$key"; ack "key_$key"; }
                ;;
        esac
    done <<< "$seen"
    [[ $seen == *'LIVE_CHECK COMPLETE'* ]] && { verdict=COMPLETE; break; }
    [[ $seen == *'LIVE_CHECK hold'* ]] && { verdict=HELD; break; }
    [[ $seen == *'Error loading'* ]] && { verdict=LOAD_FAILED; break; }
    [[ $seen == *'CombatMain'* ]] && { w 2; shot "$WORK/fatal.png"; verdict=FATAL; break; }
    # the game exits right after COMPLETE, possibly during the grep above
    running || { grep -a -q 'LIVE_CHECK COMPLETE' "$L" && verdict=COMPLETE || verdict=CRASH; break; }
    w 1
done
# a screen says at once what a stuck run is doing, and a thread dump says where the game hangs. the profile is
# written when the JVM exits by itself, so a game that gets stopped is asked for it first
if [[ $verdict == TIMEOUT || $verdict == LOAD_FAILED ]]; then
    shot "$WORK/stuck.png"
    pid=$(cat "$WORK/game.pid" 2>/dev/null)
    if [[ -n $pid ]] && command -v jcmd >/dev/null 2>&1; then
        timeout 20 jcmd "$pid" Thread.print > "$WORK/threads.txt" 2>&1
        [[ ${JFR:-1} != 0 ]] && timeout 30 jcmd "$pid" JFR.dump "filename=$(cygpath -m "$WORK/profile.jfr")" >/dev/null 2>&1
    fi
fi
if [[ $verdict == HELD || ( $verdict == COMPLETE && ${KEEP:-0} == 1 ) ]] && running; then
    OPEN=1
else
    # the profile is written as the JVM exits
    [[ $verdict == COMPLETE && ${JFR:-1} != 0 ]] && for _ in $(seq 1 30); do running || break; w 1; done
    running && stop
fi

# the log4j prefix goes, the lines that only steer the runner go, the rest is the result
grep -a -E 'LIVE_CHECK' "$L" | sed -E 's/^[0-9]+ +\[[^]]*\] [A-Z]+ +[^ ]+ +- //' |
    grep -vE '^LIVE_CHECK (step [0-9]|shot |key |hold|saving|quick load)' > "$WORK/results.body"
ok=$(grep -c '^LIVE_CHECK ok: ' "$WORK/results.body")
bad=$(grep -c '^LIVE_CHECK FAIL' "$WORK/results.body")
# the game counts its checks itself; a different count here means lines were lost or made up
game=$(sed -n 's/^LIVE_CHECK COMPLETE passed=\([0-9]*\) failed=\([0-9]*\).*/\1 \2/p' "$WORK/results.body" | tail -n 1)
mode=$(sed -n 's/^Slot [0-9]*, //p' "$WORK/launch.log" | head -n 1)
{
    echo "RUN starsector-native-smoke $VERSION, groups $GROUP, mods $enabled"
    echo "RUN base save $base, $(grep -m 1 '^Fast Rendering' "$WORK/launch.log"), $(grep -m 1 '^Java: ' "$WORK/launch.log")"
    cat "$WORK/results.body"
    echo "MODE ${mode:-no slot}, other games during the run: $others_max"
} > "$WORK/results.txt"
rm -f "$WORK/results.body"
grep -E '^LIVE_CHECK (FAIL|COMPLETE|group )' "$WORK/results.txt" | cut -c1-170
tail -n 1 "$WORK/results.txt"
# an exception the game swallowed fails the run. vanilla .ship files without a csv row and fast rendering's class probing are noise
errors=$(grep -a -E '\] (ERROR|FATAL) ' "$L" | grep -vE 'Ship hull spec \[|ScriptLoader  - Error while initializing plugin|LIVE_CHECK' | sed -E 's/^[0-9]+ +\[[^]]*\] //' | cut -c1-200 | sort | uniq -c | sort -rn)
if [[ $verdict == COMPLETE ]]; then
    if (( bad > 0 )); then verdict=CHECKS_FAILED
    elif [[ -n $game && $game != "$ok $bad" ]]; then verdict=COUNT_MISMATCH
    elif (( ok == 0 )); then verdict=NO_CHECKS
    elif [[ -n $errors ]]; then verdict=LOG_ERRORS
    fi
fi
[[ -n $errors ]] && { echo '--- log errors'; echo "$errors" | head -20; }
# the next run clears WORK_DIR/run, so keep this one
name=${GROUP//,/+}; name=${name//[^A-Za-z0-9+_.-]/_}
keep="$LIVE/runs/$(date +%Y%m%d-%H%M%S)-${name:0:80}"
mkdir -p "$keep"
summary="$verdict: $ok ok, $bad failed, $SECONDS s, results in $keep"
echo "$summary" >> "$WORK/results.txt"
cp "$L" "$WORK/results.txt" "$keep/" 2>/dev/null
cp "$WORK"/*.png "$WORK/threads.txt" "$WORK/hide.log" "$WORK/launch.log" "$keep/" 2>/dev/null
if [[ -s "$WORK/profile.jfr" ]]; then
    bash "$HERE/jfr-top.sh" "$WORK/profile.jfr" "${PROFILE_PKG:-}" > "$WORK/profile.txt" 2>&1
    if (( others_max > 0 )); then
        echo "PROFILE NOTE: $others_max other game(s) ran alongside. Compare CPU and allocation shares only, not load time, days per second, GC pauses or frame times" >> "$WORK/profile.txt"
    fi
    grep -E '^PROFILE' "$WORK/profile.txt" | cut -c1-170 | grep . || echo "PROFILE: no report, read $WORK/profile.txt"
    if [[ -n ${PROFILE_BASE:-} ]]; then
        bash "$HERE/jfr-top.sh" --compare "$PROFILE_BASE" "$WORK/profile.jfr" "${PROFILE_PKG:-}" > "$WORK/compare.txt" 2>&1
        (( others_max > 0 )) && echo "PROFILE NOTE: this run was not alone; rerun both sides with PERF=1 before reporting a time difference" >> "$WORK/compare.txt"
    fi
    cp "$WORK"/profile.* "$WORK/compare.txt" "$keep/" 2>/dev/null
fi
shots=$(cd -- "$keep" && ls check-*.png 2>/dev/null | tr '\n' ' ')
[[ -n $shots ]] && echo "CAPTURES in $keep: ${shots}- open each one with an image tool, a pass proves nothing about what a player sees"
case $verdict in
    CHECKS_FAILED) echo "NEXT: read the FAIL lines above; every line is in $keep/results.txt" ;;
    COUNT_MISMATCH) echo "NEXT: the game counted ${game% *} ok and ${game#* } failed, the log lines say $ok and $bad. Read the LIVE_CHECK lines in $keep/starsector.log" ;;
    NO_CHECKS) echo "NEXT: the groups ran without a single c.expect. A run without checks proves nothing: add checks, or fix the group" ;;
    LOG_ERRORS) echo "NEXT: every check passed, but the game logged the errors above. Fix them, or report them with the result" ;;
    TIMEOUT) echo "NEXT: open $keep/stuck.png, then threads.txt (where the game hangs). Raise LIMIT only when the last lines of results.txt show the run still moved" ;;
    LOAD_FAILED) echo "NEXT: open $keep/stuck.png. The base save did not load: rerun with NEWBASE=1" ;;
    FATAL) echo "NEXT: the game crashed. Open $keep/fatal.png and read the ERROR lines in $keep/starsector.log" ;;
    CRASH) echo "NEXT: the game exited before the checks ended. Read the end of $keep/starsector.log and $keep/launch.log" ;;
esac
if [[ -n $OPEN ]]; then
    echo "GAME OPEN: PID $(cat "$WORK/game.pid" 2>/dev/null), slot ${SMOKE_SLOT:-none}. Drive it: GAMECTL_PID=$(cat "$WORK/game.pid" 2>/dev/null) \"$GC\" bshot OUT.png"
    echo "GAME OPEN: when done: bash \"$HERE/game-proc.sh\" stop \"$WORK\"${OWN_SLOT:+ && bash \"$HERE/smoke-slot.sh\" release $SMOKE_SLOT $OWN_SLOT}"
fi
echo "$summary"
[[ $verdict == COMPLETE || ( $verdict == HELD && $bad -eq 0 ) ]]
