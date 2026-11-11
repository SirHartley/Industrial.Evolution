#!/usr/bin/env bash
# Isolated native Starsector smoke run: real loader, textures, fonts, widgets and OpenGL context.
# Usage: run-native-smoke.sh GAME_ROOT PROJECT_ROOT PROBE_JAVA NEW_OUTPUT_DIR
# Optional environment:
#   SMOKE_SRC_DIRS       colon-separated source dirs, absolute or under PROJECT_ROOT (default: src)
#   SMOKE_PROBE_SOURCES  colon-separated extra test-only Java files compiled next to the probe
#   SMOKE_SUPPORT_SOURCES the same here, since this layout has no security gate; the Windows runner keeps them apart
#   SMOKE_RESOURCE_DIRS  space-separated resource dirs under PROJECT_ROOT linked into the test mod
#                        (default: those of data graphics sounds that exist)
#   SMOKE_MOD_ID         id of the isolated test mod (default: PROJECT_ROOT/mod_info.json id, else native_ui_smoke)
#   SMOKE_EXTRA_MODS     space-separated mods under GAME_ROOT/mods to link and enable: mod_info.json ids (highest
#                        version wins) or folder names (e.g. lw_lazylib MagicLib)
#   SMOKE_CLASSPATH      colon-separated extra jars for compiling and running (e.g. optional library APIs)
#   SMOKE_PATCHLIB       1 (default) links and enables PatchLib (id patchlib); 0 skips it
#   SMOKE_TIMEOUT        seconds before the isolated JVM is killed (default: 240)
#   SMOKE_RESOLUTION     windowed resolution (default: 1024x768)
set -euo pipefail
umask 077

if (( $# != 4 )); then
    printf 'Usage: bash %s GAME_ROOT PROJECT_ROOT PROBE_JAVA NEW_OUTPUT_DIR\n' "$0" >&2
    exit 2
fi
SKILL=$(dirname -- "$(dirname -- "$(realpath -- "$0")")")
GAME=$(realpath -- "$1")
PROJECT=$(realpath -- "$2")
PROBE=$(realpath -- "$3")
OUT=$(realpath -m -- "$4")
SUPPORT="$SKILL/templates/NativeSmokeSupport.java"
USE_PATCHLIB=${SMOKE_PATCHLIB:-1}
# a folder name that exists, else a mod_info.json id
find_mod() {
    if [[ -r "$GAME/mods/$1/mod_info.json" ]]; then printf '%s' "$GAME/mods/$1"
    else bash "$SKILL/scripts/live/find-mod.sh" "$GAME" "$1"; fi
}
PATCHLIB=
if [[ $USE_PATCHLIB == 1 ]]; then PATCHLIB=$(find_mod patchlib) || exit 1; fi
PREFS="$HOME/.java/.userPrefs/com/fs/starfarer/prefs.xml"
PLUGIN=$(basename -- "${PROBE%.java}")
SRC_DIRS=${SMOKE_SRC_DIRS:-src}
TIMEOUT=${SMOKE_TIMEOUT:-240}
RESOLUTION=${SMOKE_RESOLUTION:-1024x768}

mod_id() {
    grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' "$1" | head -n 1 | sed 's/.*"\([^"]*\)"$/\1/'
}

for command in javac jar timeout; do
    command -v "$command" >/dev/null || { printf 'Missing command: %s\n' "$command" >&2; exit 1; }
done
required=("$GAME/jre_linux/bin/java" "$GAME/starfarer.api.jar" "$GAME/native/linux/liblwjgl64.so"
    "$PROBE" "$SUPPORT" "$PREFS")
[[ $USE_PATCHLIB == 1 ]] && required+=("$PATCHLIB/jars/PatchLibAPI.jar")
for file in "${required[@]}"; do
    [[ -r "$file" ]] || { printf 'Missing prerequisite: %s\n' "$file" >&2; exit 1; }
done
for dir in "$GAME/data" "$GAME/graphics" "$GAME/sounds"; do
    [[ -d "$dir" ]] || { printf 'Missing directory: %s\n' "$dir" >&2; exit 1; }
done
IFS=: read -r -a src_dirs <<< "$SRC_DIRS"
for i in "${!src_dirs[@]}"; do
    [[ ${src_dirs[i]} == /* ]] || src_dirs[i]="$PROJECT/${src_dirs[i]}"
    [[ -d "${src_dirs[i]}" ]] || { printf 'Missing source directory: %s\n' "${src_dirs[i]}" >&2; exit 1; }
done
probe_sources=()
[[ -n ${SMOKE_PROBE_SOURCES:-} ]] && IFS=: read -r -a probe_sources <<< "$SMOKE_PROBE_SOURCES"
if [[ -n ${SMOKE_SUPPORT_SOURCES:-} ]]; then
    IFS=: read -r -a support_sources <<< "$SMOKE_SUPPORT_SOURCES"
    probe_sources+=("${support_sources[@]}")
fi
for file in "${probe_sources[@]}"; do
    [[ -r "$file" ]] || { printf 'Missing probe source: %s\n' "$file" >&2; exit 1; }
done
read -r -a extra_names <<< "${SMOKE_EXTRA_MODS:-}"
extra_mods=()
for mod in "${extra_names[@]}"; do
    folder=$(find_mod "$mod") || exit 1
    extra_mods+=("$folder")
done
if [[ -e "$OUT" || -L "$OUT" ]]; then
    printf 'Output directory already exists; choose a fresh path: %s\n' "$OUT" >&2
    exit 1
fi
if [[ -z ${DISPLAY:-} ]]; then
    printf 'DISPLAY is unset. Provide a working X11 or WSLg display.\n' >&2
    exit 1
fi
if [[ -z ${XAUTHORITY:-} && -r "$HOME/.Xauthority" ]]; then
    export XAUTHORITY="$HOME/.Xauthority"
fi

MOD_ID=${SMOKE_MOD_ID:-}
if [[ -z $MOD_ID && -r "$PROJECT/mod_info.json" ]]; then
    MOD_ID=$(mod_id "$PROJECT/mod_info.json")
fi
MOD_ID=${MOD_ID:-native_ui_smoke}

mkdir -p "$OUT/classes/project" "$OUT/classes/probe" "$OUT/runtime" "$OUT/logs" \
    "$OUT/saves" "$OUT/screenshots" "$OUT/home/.java/.userPrefs/com/fs/starfarer" \
    "$OUT/tmp" "$OUT/mods/native-smoke/jars"
trap 'rm -rf -- "$OUT/home"' EXIT
cp -- "$PREFS" "$OUT/home/.java/.userPrefs/com/fs/starfarer/prefs.xml"
for dir in data graphics sounds; do
    ln -s -- "$GAME/$dir" "$OUT/runtime/$dir"
done

enabled=()
CP="$GAME/*"
if [[ $USE_PATCHLIB == 1 ]]; then
    ln -s -- "$PATCHLIB" "$OUT/mods/PatchLib"
    enabled+=("$(mod_id "$PATCHLIB/mod_info.json")")
    CP="$CP:$PATCHLIB/jars/PatchLibAPI.jar"
fi
for mod in "${extra_mods[@]}"; do
    ln -s -- "$mod" "$OUT/mods/$(basename -- "$mod")"
    enabled+=("$(mod_id "$mod/mod_info.json")")
done
[[ -n ${SMOKE_CLASSPATH:-} ]] && CP="$CP:$SMOKE_CLASSPATH"
enabled+=("$MOD_ID")

if [[ -n ${SMOKE_RESOURCE_DIRS+x} ]]; then
    read -r -a resources <<< "$SMOKE_RESOURCE_DIRS"
else
    resources=()
    for dir in data graphics sounds; do [[ -d "$PROJECT/$dir" ]] && resources+=("$dir"); done
fi
for dir in "${resources[@]}"; do
    [[ -d "$PROJECT/$dir" ]] || { printf 'Missing resource directory: %s\n' "$PROJECT/$dir" >&2; exit 1; }
    ln -s -- "$PROJECT/$dir" "$OUT/mods/native-smoke/$dir"
done

sources=()
for dir in "${src_dirs[@]}"; do
    while IFS= read -r -d '' file; do sources+=("$file"); done \
        < <(find "$dir" -type f -name '*.java' -print0)
done
if (( ${#sources[@]} == 0 )); then
    printf 'No Java sources found under %s (%s)\n' "$PROJECT" "$SRC_DIRS" >&2
    exit 1
fi
javac --release 17 -proc:none -encoding UTF-8 -nowarn -cp "$CP" \
    -d "$OUT/classes/project" "${sources[@]}"
javac --release 17 -proc:none -encoding UTF-8 -cp "$OUT/classes/project:$CP" \
    -d "$OUT/classes/probe" "$SUPPORT" "${probe_sources[@]}" "$PROBE"
jar --create --file "$OUT/mods/native-smoke/jars/Project.jar" -C "$OUT/classes/project" .
printf '{"enabledMods":[%s]}\n' "$(printf '"%s",' "${enabled[@]}" | sed 's/,$//')" \
    > "$OUT/mods/enabled_mods.json"
printf '{"id":"%s","name":"ZZ Native UI Smoke","author":"Local verification","version":"1","description":"Isolated native UI verification.","gameVersion":"0.98a-RC8","utility":true,"jars":["jars/Project.jar"],"modPlugin":"%s"}\n' \
    "$MOD_ID" "$PLUGIN" > "$OUT/mods/native-smoke/mod_info.json"

printf 'Running isolated native UI smoke. Output: %s\n' "$OUT"
(
    cd -- "$OUT/runtime"
    export HOME="$OUT/home"
    timeout "$TIMEOUT" "$GAME/jre_linux/bin/java" -Xms512m -Xmx2048m -Xss4m -noverify \
        --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
        --add-opens=java.base/java.nio=ALL-UNNAMED \
        --add-opens=java.base/java.util=ALL-UNNAMED \
        --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
        --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED \
        --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
        --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
        --add-opens=java.base/java.lang.ref=ALL-UNNAMED \
        --add-opens=java.base/java.text=ALL-UNNAMED \
        --add-opens=java.desktop/java.awt.font=ALL-UNNAMED \
        --add-opens=java.desktop/java.awt=ALL-UNNAMED \
        --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED \
        --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
        --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
        -Dfile.encoding=UTF-8 -Djava.util.Arrays.useLegacyMergeSort=true \
        -Djava.library.path="$GAME/native/linux" -Djava.io.tmpdir="$OUT/tmp" \
        -Duser.home="$OUT/home" -Djava.util.prefs.userRoot="$OUT/home" \
        -Dcom.fs.starfarer.settings.paths.saves="$OUT/saves" \
        -Dcom.fs.starfarer.settings.paths.screenshots="$OUT/screenshots" \
        -Dcom.fs.starfarer.settings.paths.mods="$OUT/mods" \
        -Dcom.fs.starfarer.settings.paths.logs="$OUT/logs" \
        -Dcom.fs.starfarer.settings.linux=true \
        -DlaunchDirect=true -DstartRes="$RESOLUTION" -DstartFS=false -DstartSound=false \
        -Dnative.smoke.output="$OUT" \
        -cp "$OUT/classes/probe:$OUT/classes/project:$CP" \
        com.fs.starfarer.StarfarerLauncher
) > "$OUT/launcher.log" 2>&1 || {
    status=$?
    printf 'Native smoke failed with exit %d. Read %s/launcher.log\n' "$status" "$OUT" >&2
    exit "$status"
}
if ! grep -Fxq 'NATIVE_SMOKE COMPLETE' "$OUT/launcher.log"; then
    printf 'No completion marker. Read %s/launcher.log\n' "$OUT" >&2
    exit 1
fi
if ! compgen -G "$OUT/*.png" >/dev/null; then
    printf 'Probe produced no framebuffer PNGs in %s\n' "$OUT" >&2
    exit 1
fi
printf 'PASS: native smoke completed. Inspect PNGs and NATIVE_SMOKE results in %s\n' "$OUT"
