#!/usr/bin/env bash
# One entry point for the starsector-native-smoke skill. Run it from Git Bash on Windows.
#   smoke.sh setup [GAME_ROOT] [--jdk DIR] [--work DIR]
#                                         check this machine and store its paths; once per machine, safe to repeat
#   smoke.sh doctor                       the same checks, changing nothing
#   smoke.sh init PROJECT_DIR             add native-smoke/live (check mod and project.sh) to a mod project
#   smoke.sh checks PROJECT_DIR GROUPS [--shards N] [--perf|--shared] [--fast] [--keep] [--newgame] [--newbase] [--limit S]
#                                         scripted live checks; GROUPS is a,b,c or all
#   smoke.sh probe PROJECT_DIR PROBE.java startup probe in a slot (Windows)
#   smoke.sh status                       who runs what on this machine
#   smoke.sh stop OWNER                   stop OWNER's runs and games now and free its slots
#   smoke.sh clean DIR [DIR...]           delete work folders without following their links
#   smoke.sh version
# GAME_ROOT is the folder that holds starsector-core, mods and jre. Ask the user for it; never search the disk.
# Every command prints one last line that says what happened. Lines in capitals with a colon (NO_SETUP:, STALE_JAR:,
# TIMEOUT: ...) name the problem and the fix; references/scripted-checks.md lists them.
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
LIVE="$HERE/live"
. "$LIVE/lib.sh"
smoke_root || exit 2
smoke_version; VERSION=$REPLY
usage() { sed -n '2,14p' "$0" >&2; exit 2; }

problems=0
ok() { printf 'ok    %s\n' "$1"; }
note() { printf 'note  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n      fix: %s\n' "$1" "$2"; problems=$(( problems + 1 )); }
# C:/x for storing: bash, PowerShell and Java all read it
mixed() { if command -v cygpath >/dev/null 2>&1; then REPLY=$(cygpath -m -- "$1"); else REPLY=$1; fi; }

# REPLY = bin folder of a JDK 17+ and JDK_VERSION, or fails. $1: a folder the caller named (only that one is tried)
jdk_ok() {
    local c=$1 v
    [[ -x $c/javac || -x $c/javac.exe ]] && [[ -x $c/jar || -x $c/jar.exe ]] || return 1
    v=$("$c/javac" -version 2>&1) || return 1
    [[ $v =~ javac\ ([0-9]+)[0-9._]* ]] && (( BASH_REMATCH[1] >= 17 )) || return 1
    JDK_MAJOR=${BASH_REMATCH[1]}; JDK_VERSION=${BASH_REMATCH[0]}
}
find_jdk() {
    local named=$1 game=$2 c best= best_major=0 best_version= list=()
    if [[ -n $named ]]; then
        smoke_posix_path "$named"; named=${REPLY%/}
        for c in "$named" "$named/bin"; do jdk_ok "$c" && { REPLY=$c; return 0; }; done
        return 1
    fi
    # the user's own choices first, in this order
    [[ -n ${SMOKE_JAVAC_DIR:-} ]] && list+=("$SMOKE_JAVAC_DIR")
    smoke_cfg javac_dir; [[ -n $REPLY ]] && list+=("$REPLY")
    for c in "${list[@]}"; do
        smoke_posix_path "$c"; c=${REPLY%/}
        jdk_ok "$c" && { REPLY=$c; return 0; }
    done
    # then a short fixed list of the usual places. The game runs on Java 17, so the lowest version from 17 up wins
    list=()
    c=$(command -v javac 2>/dev/null) && list+=("${c%/*}")
    [[ -n ${JAVA_HOME:-} ]] && list+=("$JAVA_HOME/bin")
    [[ -n $game ]] && list+=("$game/jdk-17/bin")
    list+=("$HOME"/.jdks/*/bin)
    for c in "/c/Program Files/Java" "/c/Program Files/Eclipse Adoptium" "/c/Program Files/Amazon Corretto" \
        "/c/Program Files/Microsoft" "/c/Program Files/Zulu" "/c/Program Files/BellSoft" /usr/lib/jvm; do
        list+=("$c"/*/bin)
    done
    for c in "${list[@]}"; do
        smoke_posix_path "$c"; c=${REPLY%/}
        jdk_ok "$c" || continue
        if [[ -z $best ]] || (( JDK_MAJOR < best_major )); then best=$c; best_major=$JDK_MAJOR; best_version=$JDK_VERSION; fi
    done
    [[ -n $best ]] || return 1
    JDK_MAJOR=$best_major; JDK_VERSION=$best_version; REPLY=$best
}

# the mod sets are junctions in the work folder. bash resolves them itself, so ask cmd, which sees what the game sees
links_ok() {
    local t="$1/.smoke-link-test-$$" w seen
    mkdir -p "$t/target" && : > "$t/target/seen" || return 1
    w=$(cygpath -w -- "$t")
    MSYS_NO_PATHCONV=1 cmd.exe /c mklink /J "$w\\link" "$w\\target" >/dev/null 2>&1
    seen=$(MSYS_NO_PATHCONV=1 cmd.exe /c dir /b "$w\\link\\" 2>/dev/null)
    MSYS_NO_PATHCONV=1 cmd.exe /c rmdir "$w\\link" >/dev/null 2>&1
    [[ -e $t/link ]] || rm -rf -- "$t"
    [[ $seen == *seen* ]]
}

cmd_setup() {
    local write=$1; shift
    local game= jdk= work= javac_dir= platform=
    while (( $# )); do
        case $1 in
            --jdk) jdk=${2:-}; shift; shift || true ;;
            --work) work=${2:-}; shift; shift || true ;;
            -*) echo "Unknown option $1" >&2; usage ;;
            *) game=$1; shift ;;
        esac
    done
    echo "starsector-native-smoke $VERSION, machine folder $SMOKE_ROOT_DIR"

    if (( SMOKE_WIN )); then
        ok 'Git Bash on Windows'
    else
        note 'not Git Bash on Windows: scripted checks and live sessions need it. On Linux only the startup probe runs (references/linux-runner.md)'
    fi

    [[ -n $game ]] || { smoke_cfg game_root; game=$REPLY; }
    if [[ -z $game ]]; then
        fail 'no game folder given or stored' "run: bash \"$HERE/smoke.sh\" setup \"<folder that holds starsector-core>\". Ask the user for the path; never search the disk for it"
    else
        smoke_posix_path "$game"; game=${REPLY%/}
        [[ ${game##*/} == starsector-core ]] && game=${game%/*}
        if [[ -r $game/starsector-core/starfarer.api.jar ]]; then
            platform=windows
            if [[ -x $game/jre/bin/java.exe || -r $game/jre/bin/java.exe ]]; then
                ok "game folder $game ($("$game/jre/bin/java.exe" -version 2>&1 | head -n 1))"
            else
                fail "the game's own Java is missing: $game/jre/bin/java.exe" 'repair or reinstall the game; the runs use its jre folder'
            fi
        elif [[ -r $game/starfarer.api.jar ]]; then
            platform=linux
            ok "game folder $game (Linux install)"
        else
            fail "$game is not a Starsector folder (no starsector-core in it)" 'pass the folder that holds starsector-core, mods and jre. Ask the user for the path; never search the disk for it'
            game=
        fi
    fi

    if find_jdk "$jdk" "$game"; then
        javac_dir=$REPLY
        ok "JDK $javac_dir ($JDK_VERSION)"
        for tool in jfr jcmd jdeps; do
            [[ -x $javac_dir/$tool || -x $javac_dir/$tool.exe ]] || note "$tool is missing in that JDK: $([[ $tool == jdeps ]] && echo 'the probe runner skips its gate check' || echo 'no profile report or thread dump')"
        done
    elif [[ -n $jdk ]]; then
        fail "$jdk is not the bin folder of a JDK 17 or newer (javac and jar)" 'pass the bin folder itself, for example --jdk "C:\Users\me\.jdks\corretto-17.0.12\bin"'
    else
        fail 'no JDK 17 or newer found (PATH, JAVA_HOME, GAME/jdk-17, ~/.jdks, Program Files)' "install a JDK 17 (the game ships a JRE only), then run: bash \"$HERE/smoke.sh\" setup --jdk \"<its bin folder>\""
    fi

    # the game reads its licence and settings from the registry (Linux: ~/.java); never print them
    if [[ $platform == windows ]]; then
        if MSYS_NO_PATHCONV=1 reg.exe query 'HKCU\Software\JavaSoft\Prefs\com\fs\starfarer' >/dev/null 2>&1; then
            ok 'the game has run on this account (its preferences exist)'
        else
            fail 'the game has never run on this Windows account' 'the user starts the game once through its own launcher and enters the licence key, then run setup again'
        fi
    elif [[ $platform == linux ]]; then
        if [[ -r $HOME/.java/.userPrefs/com/fs/starfarer/prefs.xml ]]; then ok 'the game has run on this account (its preferences exist)'
        else fail 'the game has never run on this account' 'the user starts the game once and enters the licence key, then run setup again'; fi
    fi

    [[ -n $work ]] || { smoke_cfg work_root; work=${REPLY:-$SMOKE_ROOT_DIR/work}; }
    smoke_posix_path "$work"; work=${REPLY%/}
    if [[ -n $game && ( $work == "$game/mods" || $work == "$game/mods"/* || $work == "$game/starsector-core"* ) ]]; then
        fail "the work folder $work is inside the game's mods or core folder" 'pass another one with --work DIR'
    elif (( write )) && ! mkdir -p "$work" 2>/dev/null; then
        fail "cannot create the work folder $work" 'pass a folder this account may write to with --work DIR'
    else
        smoke_real_dir "$work"
        [[ $REPLY != "$work" ]] && note "$work is redirected by the app this runs in (MSIX); the runs use $REPLY"
        work=$REPLY
        if (( SMOKE_WIN )) && [[ -d $work ]] && ! links_ok "$work"; then
            fail "a junction in the work folder $work does not resolve for the game" 'pass a work folder outside %LOCALAPPDATA% with --work DIR'
        else
            ok "work folder $work"
        fi
    fi

    if [[ -n $javac_dir ]]; then
        if (( write )); then
            if [[ $platform == windows ]]; then parts=(gamectl prefs guard); else parts=(prefs guard); fi
            if SMOKE_JAVAC_DIR=$javac_dir bash "$LIVE/build-tools.sh" "${parts[@]}" > /dev/null; then
                smoke_tools; ok "tools built in $REPLY"
            else
                fail 'a tool did not build (TOOLS_FAILED lines above)' 'gamectl needs the .NET Framework 4 compiler that ships with Windows; the other two need the JDK. Try another JDK with --jdk'
            fi
        else
            smoke_tools
            if [[ $platform != windows || -x $REPLY/gamectl.exe ]] && [[ -f $REPLY/prefs-classes/SmokePrefs.class && -f $REPLY/CursorGuard.jar ]]; then
                ok "tools built in $REPLY"
            else
                fail "the tools for this copy of the skill are not built ($REPLY)" "run: bash \"$HERE/smoke.sh\" setup"
            fi
        fi
    fi

    smoke_cfg slots
    local slots=$REPLY cores
    if [[ -z $slots ]]; then
        cores=$(nproc 2>/dev/null || echo 4)
        slots=$(( cores / 2 )); (( slots < 1 )) && slots=1; (( slots > 3 )) && slots=3
        note "$cores CPU threads: $slots game(s) at once. Change it with: bash \"$LIVE/smoke-slot.sh\" config slots=N"
    else
        ok "$slots game(s) at once"
    fi

    if (( problems )); then
        echo "SETUP FAIL: $problems problem(s). Fix the FAIL lines above, then run setup again"
        return 1
    fi
    if (( write )); then
        mixed "$game"; game=$REPLY
        mixed "$javac_dir"; javac_dir=$REPLY
        mixed "$work"; work=$REPLY
        bash "$LIVE/smoke-slot.sh" config "game_root=$game" "javac_dir=$javac_dir" "work_root=$work" "slots=$slots" 2>/dev/null ||
            { echo "SETUP FAIL: could not write $SMOKE_ROOT_DIR/config"; return 1; }
        echo "SETUP OK: stored in $SMOKE_ROOT_DIR/config. Next: bash \"$HERE/smoke.sh\" init \"<mod project folder>\""
    else
        echo "DOCTOR OK: this machine is set up"
    fi
}

cmd_init() {
    (( $# == 1 )) || usage
    local project live info id owner deps= pkg= list dep
    project=$(cd -- "$1" 2>/dev/null && pwd) || { echo "NO_PROJECT: no such folder: $1" >&2; exit 2; }
    [[ -r $project/mod_info.json ]] || { echo "NO_PROJECT: no mod_info.json in $project. Pass the mod's root folder" >&2; exit 2; }
    live="$project/native-smoke/live"
    if [[ -e $live/project.sh ]]; then
        echo "INIT OK: already set up, $live/project.sh exists. Run: bash \"$HERE/smoke.sh\" checks \"$project\" all"
        return 0
    fi
    smoke_mod_info "$project"; info=$REPLY
    [[ $info =~ \"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]] || { echo "NO_PROJECT: no \"id\" in $project/mod_info.json" >&2; exit 2; }
    id=${BASH_REMATCH[1]}
    owner=${id,,}; owner=${owner//[^a-z0-9._-]/-}
    if [[ $info =~ \"modPlugin\"[[:space:]]*:[[:space:]]*\"([A-Za-z0-9_]+)\. ]]; then pkg=${BASH_REMATCH[1]}; fi
    mkdir -p "$live" || exit 1
    # a project that keeps its check mod right in native-smoke/live (an older layout) keeps it there
    local check='"$PROJECT/native-smoke/live/check-mod"'
    if [[ -r $live/mod_info.json && -d $live/src ]]; then
        check='"$PROJECT/native-smoke/live"'
    elif [[ ! -e $live/check-mod ]]; then
        cp -r -- "$HERE/../templates/live-check-mod" "$live/check-mod" || exit 1
    fi
    sed -e "s|@OWNER@|$owner|" -e "s|@DEPS@|$deps|" -e "s|@PKG@|$pkg|" \
        -e "s|^CHECK_DIR=.*|CHECK_DIR=$check|" "$HERE/../templates/project.sh" > "$live/project.sh" || exit 1
    echo "Wrote $live/project.sh (owner $owner, package ${pkg:-not found: set PROFILE_PKG})"
    [[ $check == *check-mod* ]] && echo "Wrote $live/check-mod: put checks in src/livecheck/ExampleChecks.java, add groups to Groups.java"
    compgen -G "$project/jars/src/*/tools/ReleaseBuild.java" >/dev/null &&
        echo "This project has a compile gate (ReleaseBuild.java): set stage_mod in project.sh so the game runs the jar the gate built"
    local first=example groups_file
    if [[ $check != *check-mod* ]]; then
        echo "Kept the check mod in $live. The runner passes the groups as -Dlive.check; an older check mod that reads another property gets it from java_args in project.sh (references/setup.md)"
        groups_file=$(find "$live/src" -name Groups.java 2>/dev/null | head -n 1)
        first=GROUP
        [[ -n $groups_file ]] && first=$(grep -o -E 'case +"[^"]+"' "$groups_file" | head -n 1 | sed -E 's/.*"([^"]+)"/\1/')
    fi
    echo "INIT OK. Next: bash \"$HERE/smoke.sh\" checks \"$project\" ${first:-GROUP}"
}

cmd_checks() {
    (( $# >= 2 )) || usage
    local project=$1 groups=$2 shards=1; shift 2
    while (( $# )); do
        case $1 in
            --shards) shards=${2:-}; shift; shift || true ;;
            --perf) export PERF=1; shift ;;
            --shared) export PERF=0; shift ;;
            --fast) export FAST=1; shift ;;
            --keep) export KEEP=1; shift ;;
            --newgame) export NEWGAME=1; shift ;;
            --newbase) export NEWBASE=1; shift ;;
            --limit) export LIMIT=${2:-}; shift; shift || true ;;
            *) echo "Unknown option $1" >&2; usage ;;
        esac
    done
    [[ $shards =~ ^[1-9]$ ]] || { echo "--shards takes 1 to 9" >&2; exit 2; }
    if (( shards > 1 )); then
        exec bash "$LIVE/run-shards.sh" "$shards" "$project" "$groups"
    fi
    exec bash "$LIVE/run-live-checks.sh" --project "$project" "$groups"
}

cmd_probe() {
    (( $# == 2 )) || usage
    local project probe game owner info out
    (( SMOKE_WIN )) || { echo "NOT_WINDOWS: this runs the Windows probe runner. On Linux use scripts/run-native-smoke.sh (references/linux-runner.md)" >&2; exit 2; }
    project=$(cd -- "$1" 2>/dev/null && pwd) || { echo "NO_PROJECT: no such folder: $1" >&2; exit 2; }
    [[ -r $project/mod_info.json ]] || { echo "NO_PROJECT: no mod_info.json in $project. Pass the mod's root folder" >&2; exit 2; }
    [[ -r $2 ]] || { echo "NO_PROBE: no such file: $2" >&2; exit 2; }
    probe=$(cd -- "$(dirname -- "$2")" && pwd)/${2##*[/\\]}
    game=${GAME_ROOT:-}
    [[ -n $game ]] || { smoke_cfg game_root; game=$REPLY; }
    [[ -n $game ]] || { echo "NO_SETUP: no game folder is stored for this machine. Run once: bash \"$HERE/smoke.sh\" setup \"<Starsector folder>\"" >&2; exit 2; }
    smoke_jdk || { echo "NO_JDK: javac (JDK 17+) not found. Run: bash \"$HERE/smoke.sh\" setup" >&2; exit 2; }
    smoke_mod_info "$project"; info=$REPLY
    owner=${project##*/}
    [[ $info =~ \"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]] && owner=${BASH_REMATCH[1]}
    owner=${owner,,}; owner=${owner//[^a-z0-9._-]/-}
    # the project's own owner name when it has one, so status shows one name per project
    if [[ -r $project/native-smoke/live/project.sh ]]; then
        REPLY=$(OWNER=; PROJECT=$project; . "$project/native-smoke/live/project.sh" >/dev/null 2>&1; printf '%s' "$OWNER")
        [[ $REPLY =~ ^[A-Za-z0-9._-]+$ ]] && owner=$REPLY
    fi
    smoke_cfg work_root; out=${SMOKE_WORK_ROOT:-${REPLY:-$SMOKE_ROOT_DIR/work}}
    smoke_posix_path "$out"
    # a new folder every run, per slot: the runner refuses a used one
    out="$REPLY/$owner/probe-$(date +%Y%m%d-%H%M%S)-slot{slot}"
    mkdir -p "$(dirname -- "$out")"
    echo "starsector-native-smoke $VERSION, probe ${probe##*/}, output ${out%-slot*}-slotN"
    # smoke-slot.sh turns Git Bash's path conversion off for its command, so every path goes in Windows form
    exec bash "$LIVE/smoke-slot.sh" run "${SMOKE_OWNER:-$owner.probe}" -- powershell.exe -NoProfile -ExecutionPolicy Bypass \
        -File "$(cygpath -w -- "$HERE/run-native-smoke.ps1")" "$(cygpath -w -- "$game")" "$(cygpath -w -- "$project")" \
        "$(cygpath -w -- "$probe")" "$(cygpath -w -- "$out")"
}

cmd=${1:-}
shift || true
case $cmd in
    setup) cmd_setup 1 "$@" ;;
    doctor) cmd_setup 0 ;;
    init) cmd_init "$@" ;;
    checks) cmd_checks "$@" ;;
    probe) cmd_probe "$@" ;;
    status) exec bash "$LIVE/smoke-slot.sh" status ;;
    stop) (( $# == 1 )) || usage; exec bash "$LIVE/smoke-slot.sh" cancel "$1" ;;
    clean) (( $# )) || usage; exec bash "$LIVE/clean-work.sh" "$@" ;;
    version) echo "starsector-native-smoke $VERSION" ;;
    *) usage ;;
esac
