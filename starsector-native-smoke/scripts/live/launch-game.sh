#!/usr/bin/env bash
# Starts Starsector directly (no launcher window) against an isolated mods/saves/logs folder.
# Works from Git Bash on Windows, from WSL against a Windows install, and on Linux.
# Runs in the foreground until the game exits; start it in the background.
# While the game runs, its PID is in WORK_DIR/game.pid. Stop it with game-proc.sh stop WORK_DIR, never by process name.
# Needs a slot from smoke-slot.sh in SMOKE_SLOT (run-live-checks.sh takes one itself).
#
# Usage: launch-game.sh GAME_ROOT WORK_DIR
#   GAME_ROOT  the Starsector install (the folder holding starsector-core on Windows,
#              or starfarer.api.jar on Linux)
#   WORK_DIR   folder holding mods/ (with enabled_mods.json), saves/, screenshots/, logs/
#
# Optional environment:
#   SMOKE_RESOLUTION   windowed size (default 1600x900)
#   SMOKE_HEAP         heap size for -Xms/-Xmx (default 3g)
#   SMOKE_FAST_RENDER  auto (default): use Fast Rendering (fr.jar) when it is installed; 1: require it; 0: never
#   SMOKE_HIDE         Windows only. 1 (default): keep the game window behind other windows while it starts
#   SMOKE_HIDE_SECONDS how long to keep pushing the window back (default 300: a new game on a busy PC takes longer than 2 minutes)
#   SMOKE_JAVA_ARGS    extra JVM arguments, space-separated; one per line when the value holds a line break, so an
#                      argument may hold a path with spaces
#   SMOKE_PREFS        Windows only. 1 (default): serve the registry prefs from memory, so the run never writes them; needs a JDK
#   SMOKE_CURSOR_GUARD Windows only. 1 (default): the game never moves the real cursor (templates/CursorGuard.java); needs a JDK
#   SMOKE_SLOT         the slot this game runs in (smoke-slot.sh acquire). SMOKE_LOCK=0 runs without one, only on a machine
#                      where no other session starts games
#   SMOKE_OWNER        the slot's owner; the launch stops when the slot belongs to someone else (reaped and taken again)
#   SMOKE_PRIORITY     Windows only. Process priority: BelowNormal (default) in a shared slot, Normal in a perf slot
# The prefs helper, the cursor guard and gamectl.exe are built once per machine into the machine folder
# (build-tools.sh); the JDK for that comes from SMOKE_JAVAC_DIR or the javac_dir setting (smoke.sh setup).
# Each launch's focus handling is logged to WORK_DIR/hide.log.
set -euo pipefail

if (( $# != 2 )); then
    printf 'Usage: bash %s GAME_ROOT WORK_DIR\n' "$0" >&2
    exit 2
fi
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
GAME=$(cd -- "$1" && pwd)
WORK=$(mkdir -p -- "$2" && cd -- "$2" && pwd)
RES=${SMOKE_RESOLUTION:-1600x900}
HEAP=${SMOKE_HEAP:-3g}
FAST=${SMOKE_FAST_RENDER:-auto}

# Work out the platform and how to turn a path into one the game's JVM understands.
if [[ -d "$GAME/starsector-core" ]]; then
    PLATFORM=windows
    CORE="$GAME/starsector-core"
    JAVA="$GAME/jre/bin/java.exe"
    NATIVE='native\windows'
    SEP=';'
    if command -v cygpath >/dev/null; then
        winpath() { cygpath -w -- "$1"; }
    elif command -v wslpath >/dev/null; then
        winpath() { wslpath -w -- "$1"; }
    else
        printf 'Need cygpath (Git Bash) or wslpath (WSL) to start the Windows game.\n' >&2
        exit 1
    fi
    jvmpath() { winpath "$1"; }
elif [[ -r "$GAME/starfarer.api.jar" ]]; then
    PLATFORM=linux
    CORE="$GAME"
    JAVA="$GAME/jre_linux/bin/java"
    NATIVE="$GAME/native/linux"
    SEP=':'
    jvmpath() { printf '%s' "$1"; }
    [[ -n ${DISPLAY:-} || -n ${WAYLAND_DISPLAY:-} ]] || { printf 'No display. Set DISPLAY (X11 or WSLg).\n' >&2; exit 1; }
else
    printf 'Not a Starsector install (no starsector-core and no starfarer.api.jar): %s\n' "$GAME" >&2
    exit 1
fi
[[ -x "$JAVA" || -r "$JAVA" ]] || { printf 'Missing game Java: %s\n' "$JAVA" >&2; exit 1; }
# The user may play on another Java through a custom launcher (a newer JDK, Miko). Results can differ there,
# so say which Java this run uses, and name any other Java the install's launch files point at.
printf 'Java: %s (%s)\n' "$("$JAVA" -version 2>&1 | head -n 1)" "$JAVA"
others=$(cat "$GAME"/*.bat "$GAME"/*.cmd "$GAME/vmparams" 2>/dev/null |
    grep -a -o -i -E '[^ "@=]*bin[\\/]javaw?(\.exe)?' | grep -v -i -E '(^|[\\/])jre(_linux)?[\\/]bin' | sort -u | tr '\n' ' ' || true)
[[ -n $others ]] && printf 'WARNING: launch files in %s use another Java: %s- ask which one the user plays on\n' "$GAME" "$others" >&2
[[ -r "$WORK/mods/enabled_mods.json" ]] || { printf 'Missing %s/mods/enabled_mods.json\n' "$WORK" >&2; exit 1; }
mkdir -p "$WORK/saves" "$WORK/screenshots" "$WORK/logs"

# every game holds a slot, so sessions sharing the machine know it runs and stale locks can be told apart
MODE=shared
if [[ ${SMOKE_LOCK:-1} != 0 ]]; then
    [[ -n ${SMOKE_SLOT:-} ]] || { printf 'No slot. Take one: SMOKE_SLOT=$(bash %s/smoke-slot.sh acquire OWNER)\n' "$HERE" >&2; exit 1; }
    # refuses while the machine is paused, after a cancel, or when the slot now belongs to someone else
    MODE=$(bash "$HERE/smoke-slot.sh" game "$SMOKE_SLOT" "$WORK" "${SMOKE_OWNER:-}") || exit $?
    printf 'Slot %s, %s\n' "$SMOKE_SLOT" "$MODE"
fi

# The game's own jars, in the order the vanilla launcher uses (0.98a).
JARS=(janino.jar commons-compiler.jar commons-compiler-jdk.jar starfarer.api.jar starfarer_obf.jar
    jogg-0.0.7.jar jorbis-0.0.15.jar json.jar lwjgl.jar jinput.jar log4j-1.2.9.jar lwjgl_util.jar
    fs.sound_obf.jar fs.common_obf.jar xstream-1.4.10.jar txw2-3.0.2.jar jaxb-api-2.4.0-b180830.0359.jar
    webp-imageio-0.1.6.jar)
for jar in "${JARS[@]}"; do
    [[ -r "$CORE/$jar" ]] || { printf 'Missing game jar: %s/%s\n' "$CORE" "$jar" >&2; exit 1; }
done

# Fast Rendering (https://github.com/Halke1986/starsector-render): fr.jar goes first on the classpath.
# 0.8.0 and later load it through a Java agent (fr.agent.jar); older versions replaced the system class loader.
FR_ARGS=()
if [[ $FAST != 0 && -r "$CORE/fr.jar" ]]; then
    JARS=(fr.jar "${JARS[@]}")
    if [[ -r "$CORE/fr.agent.jar" ]]; then
        FR_ARGS=(-javaagent:fr.agent.jar -Dcom.genir.renderer.watchdog=0)
    else
        FR_ARGS=(-Djava.system.class.loader=com.genir.renderer.loaders.AppClassLoader)
    fi
    # Fast Rendering's own launcher also loads PatchLib's agent when it is installed.
    [[ -r "$CORE/PatchLibAgent.jar" ]] && FR_ARGS=(-javaagent:PatchLibAgent.jar "${FR_ARGS[@]}")
    printf 'Fast Rendering: on (%s)\n' "${FR_ARGS[*]}"
elif [[ $FAST == 1 ]]; then
    printf 'SMOKE_FAST_RENDER=1 but %s/fr.jar is missing\n' "$CORE" >&2
    exit 1
else
    printf 'Fast Rendering: off\n'
fi
CP=$(IFS="$SEP"; printf '%s' "${JARS[*]}")

# Java prefs are the registry on Windows. A plain run writes its save path to the "continue" key there, which repoints
# the Continue button of every other install. Serve a copy from memory instead and point Continue at this run's newest save.
PREFS_ARGS=()
PREFS_DIR="$WORK/prefs"
# javac and jar each start a JVM, seconds apiece on a busy PC, so the helpers are built once per machine, not on every launch
TOOLS=
if [[ $PLATFORM == windows ]]; then
    parts=()
    [[ ${SMOKE_PREFS:-1} == 1 ]] && parts+=(prefs)
    [[ ${SMOKE_CURSOR_GUARD:-1} == 1 ]] && parts+=(guard)
    [[ ${SMOKE_HIDE:-1} == 1 ]] && parts+=(gamectl)
    if (( ${#parts[@]} )); then
        TOOLS=$(bash "$HERE/build-tools.sh" "${parts[@]}" | tail -n 1) || true
    fi
fi
if [[ $PLATFORM == windows && ${SMOKE_PREFS:-1} == 1 ]]; then
    PREFS_CLS="$TOOLS/prefs-classes"
    if [[ -n $TOOLS && -f $PREFS_CLS/SmokePrefs.class ]]; then
        mkdir -p "$PREFS_DIR"
        "$JAVA" -cp "$(winpath "$PREFS_CLS")" SmokePrefs export "$(winpath "$PREFS_DIR/prefs.xml")"
        save=$(ls -dt "$WORK"/saves/save_* 2>/dev/null | head -n 1 || true)
        if [[ -n $save ]]; then
            CONTINUE="$(winpath "$save")" perl -pi -e 's/(<entry key="continue" value=")[^"]*/$1$ENV{CONTINUE}/' "$PREFS_DIR/prefs.xml"
        else
            perl -pi -e '$_ = "" if /<entry key="continue"/' "$PREFS_DIR/prefs.xml"
        fi
        rm -f "$PREFS_DIR/prefs.xml.bak"
        PREFS_ARGS=(-Djava.util.prefs.PreferencesFactory=SmokePrefs -Dsmoke.prefs.xml="$(winpath "$PREFS_DIR/prefs.xml")")
        CP="$(winpath "$PREFS_CLS")$SEP$CP"
    else
        printf 'No JDK: the run writes the continue key in the registry. Run smoke.sh setup, or set SMOKE_JAVAC_DIR.\n' >&2
    fi
fi

# the game centres the real cursor through LWJGL at battle start, even behind other windows; the agent drops that move
GUARD_ARGS=()
GUARD_JAR="$TOOLS/CursorGuard.jar"
if [[ $PLATFORM == windows && ${SMOKE_CURSOR_GUARD:-1} == 1 ]]; then
    if [[ -n $TOOLS && -f $GUARD_JAR ]]; then
        GUARD_ARGS=(-javaagent:"$(winpath "$GUARD_JAR")" --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED)
    else
        printf 'No JDK: the game can move the real cursor. Run smoke.sh setup, or set SMOKE_JAVAC_DIR.\n' >&2
    fi
fi

# one argument per line when the value holds a line break, so a path with spaces stays in one argument
extra=()
if [[ ${SMOKE_JAVA_ARGS:-} == *$'\n'* ]]; then
    while IFS= read -r arg; do [[ -n $arg ]] && extra+=("$arg"); done <<< "$SMOKE_JAVA_ARGS"
else
    read -r -a extra <<< "${SMOKE_JAVA_ARGS:-}"
fi
cd -- "$CORE"
"$JAVA" -noverify -Xms"$HEAP" -Xmx"$HEAP" -Xss4m \
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
    --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.lang.ref=ALL-UNNAMED \
    --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.desktop/java.awt.font=ALL-UNNAMED \
    --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED \
    --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
    "${FR_ARGS[@]}" "${PREFS_ARGS[@]}" "${GUARD_ARGS[@]}" "${extra[@]}" \
    -Dfile.encoding=UTF-8 -Djava.util.Arrays.useLegacyMergeSort=true -Djava.library.path="$NATIVE" \
    -Dcom.fs.starfarer.settings.paths.saves="$(jvmpath "$WORK/saves")" \
    -Dcom.fs.starfarer.settings.paths.screenshots="$(jvmpath "$WORK/screenshots")" \
    -Dcom.fs.starfarer.settings.paths.mods="$(jvmpath "$WORK/mods")" \
    -Dcom.fs.starfarer.settings.paths.logs="$(jvmpath "$WORK/logs")" \
    $([[ $PLATFORM == linux ]] && printf '%s' '-Dcom.fs.starfarer.settings.linux=true') \
    -DlaunchDirect=true -DstartRes="$RES" -DstartFS=false -DstartSound=false \
    -classpath "$CP" com.fs.starfarer.StarfarerLauncher &
job=$!

# Record the game's own PID. game-proc.sh stops only this process, never another Java the user runs.
pid=
if [[ $PLATFORM == linux ]]; then
    pid=$job
else
    # $! is a shell process here (Git Bash runs java.exe as its child), so find the java.exe whose command line
    # names this run's folder
    filter="\$_.CommandLine -like '*$(winpath "$WORK")\\*'"
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        pid=$(powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $filter } | Select-Object -First 1 -ExpandProperty ProcessId" 2>/dev/null | tr -dc '0-9')
        [[ -n $pid ]] && break
        sleep 1
    done
fi
if [[ -n $pid ]]; then
    printf '%s\n' "$pid" > "$WORK/game.pid"
    printf 'Game PID %s, written to %s/game.pid\n' "$pid" "$WORK"
else
    printf 'WARNING: game PID not found; game-proc.sh cannot stop this run\n' >&2
fi
if [[ $PLATFORM == windows && ${SMOKE_HIDE:-1} == 1 && -n $TOOLS && -x "$TOOLS/gamectl.exe" && -n $pid ]]; then
    GAMECTL_PID=$pid "$TOOLS/gamectl.exe" hide "${SMOKE_HIDE_SECONDS:-300}" >> "$WORK/hide.log" 2>&1 &
fi
if [[ $PLATFORM == windows && -n $pid ]]; then
    # a lower priority keeps the PC usable while several games run; a perf run keeps the normal one
    prio=${SMOKE_PRIORITY:-$([[ $MODE == perf ]] && echo Normal || echo BelowNormal)}
    [[ $prio != Normal ]] && powershell.exe -NoProfile -Command "(Get-Process -Id $pid).PriorityClass = '$prio'" >/dev/null 2>&1
    if [[ $MODE == perf ]]; then
        others=$(powershell.exe -NoProfile -Command "@(Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -like '*StarfarerLauncher*' -and \$_.ProcessId -ne $pid }).Count" 2>/dev/null | tr -dc '0-9' || true)
        (( ${others:-0} > 0 )) && printf 'PERF WARNING: %s other Starsector game(s) running outside the slots (the user'"'"'s own?); times are not clean\n' "$others" >&2
    fi
fi
wait "$job" || status=$?
rm -f -- "$WORK/game.pid"
# the copied prefs hold the licence
rm -rf -- "$PREFS_DIR"
exit "${status:-0}"
