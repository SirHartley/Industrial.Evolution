#!/usr/bin/env bash
# Compiles a test mod's src/ into jars/<name>.jar against the game's API and any mod jars it uses.
#   build-test-mod.sh GAME_ROOT TEST_MOD_DIR JAR_NAME [EXTRA_JAR...]
# EXTRA_JAR: jars the test code calls, such as Console Commands' jars/lw_Console.jar, LazyLib's jars,
# or the mod under test's jar. Needs a JDK 17+ javac and jar (the game ships a JRE only): on PATH, in SMOKE_JAVAC_DIR,
# or the javac_dir setting stored by smoke.sh setup.
# Close the game first: Windows keeps a running mod's jar locked.
set -euo pipefail
(( $# >= 3 )) || { printf 'Usage: %s GAME_ROOT TEST_MOD_DIR JAR_NAME [EXTRA_JAR...]\n' "$0" >&2; exit 2; }
GAME=$(cd -- "$1" && pwd)
MOD=$(cd -- "$2" && pwd)
NAME=$3
shift 3
if [[ -d "$GAME/starsector-core" ]]; then CORE="$GAME/starsector-core"; else CORE="$GAME"; fi

. "$(cd -- "$(dirname -- "$0")" && pwd)/lib.sh"
smoke_jdk || true
# A Windows javac (Git Bash, or WSL calling javac.exe) wants Windows paths and ';'.
JAVAC=$(command -v javac || command -v javac.exe || true)
[[ -n $JAVAC ]] || { printf 'NO_JDK: javac (JDK 17+) not found. Run smoke.sh setup, or set SMOKE_JAVAC_DIR to a JDK bin folder\n' >&2; exit 1; }
if [[ $JAVAC == *.exe || $(uname -s) == MINGW* || $(uname -s) == MSYS* ]]; then
    SEP=';'
    if command -v cygpath >/dev/null; then p() { cygpath -m -- "$1"; }; else p() { wslpath -m -- "$1"; }; fi
else
    SEP=':'
    p() { printf '%s' "$1"; }
fi

cp_items=()
for jar in starfarer.api.jar lwjgl.jar lwjgl_util.jar json.jar log4j-1.2.9.jar; do
    [[ -r "$CORE/$jar" ]] || { printf 'Missing %s/%s\n' "$CORE" "$jar" >&2; exit 1; }
    cp_items+=("$(p "$CORE/$jar")")
done
for jar in "$@"; do
    [[ -r $jar ]] || { printf 'Missing extra jar: %s\n' "$jar" >&2; exit 1; }
    cp_items+=("$(p "$jar")")
done
CP=$(IFS="$SEP"; printf '%s' "${cp_items[*]}")

OUT=$(mktemp -d)
EMPTY=$(mktemp -d)
trap 'rm -rf -- "$OUT" "$EMPTY"' EXIT
mapfile -t sources < <(find "$MOD/src" -name '*.java')
(( ${#sources[@]} > 0 )) || { printf 'No Java sources in %s/src\n' "$MOD" >&2; exit 1; }
win_sources=()
for s in "${sources[@]}"; do win_sources+=("$(p "$s")"); done
# Mod jars such as MagicLib bundle their own .java files. -sourcepath on an empty folder plus
# -implicit:none stops javac from compiling those instead of using the jar's classes.
"$JAVAC" --release 17 -nowarn -encoding UTF-8 -sourcepath "$(p "$EMPTY")" -implicit:none \
    -cp "$CP" -d "$(p "$OUT")" "${win_sources[@]}"
mkdir -p "$MOD/jars"
jar cf "$(p "$MOD/jars/$NAME.jar")" -C "$(p "$OUT")" .
printf 'Built %s/jars/%s.jar\n' "$MOD" "$NAME"
