#!/usr/bin/env bash
# Stages a mod project for a test game: STAGE_DIR gets links to the project's top-level folders, copies of its
# top-level files and copies of its jars, so the game never locks or reads a jar in the checkout.
#   stage-mod.sh PROJECT_DIR STAGE_DIR [JAR...]
# JAR: a freshly built jar (from the project's compile gate). It replaces the mod_info.json "jars" entry with the same
# file name, else the first entry. Without JAR the project's own jars are copied as they are.
# Not linked: jars out build lib libs docs src gradle native-smoke, folders starting with '.', folders holding jars.
# Links are junctions on Windows (Git Bash, or WSL against a Windows install) and symlinks elsewhere.
set -euo pipefail
(( $# >= 2 )) || { sed -n '2,8p' "$0" >&2; exit 2; }
PROJECT=$(cd -- "$1" && pwd)
STAGE=$(mkdir -p -- "$2" && cd -- "$2" && pwd)
shift 2
[[ -r $PROJECT/mod_info.json ]] || { printf 'No mod_info.json in %s\n' "$PROJECT" >&2; exit 1; }
[[ $STAGE != "$PROJECT" && $STAGE != "$PROJECT"/* || $STAGE == "$PROJECT"/out/* || $STAGE == "$PROJECT"/build/* ]] ||
    { printf 'STAGE_DIR must be outside the project, or under its out or build folder: %s\n' "$STAGE" >&2; exit 1; }

if command -v cygpath >/dev/null; then
    winpath() { cygpath -w -- "$1"; }
elif command -v wslpath >/dev/null && [[ $STAGE == /mnt/* ]]; then
    winpath() { wslpath -w -- "$1"; }
else
    winpath() { return 1; }
fi

# remove the old stage: links as links, never through them
for entry in "$STAGE"/* "$STAGE"/.[!.]*; do
    [[ -e $entry || -L $entry ]] || continue
    if [[ -L $entry || -f $entry ]]; then
        rm -f -- "$entry"
    elif winpath "$entry" >/dev/null 2>&1; then
        # a junction looks like a plain folder to some shells; rmdir removes a link or an empty folder only
        MSYS_NO_PATHCONV=1 cmd.exe /c rmdir "$(winpath "$entry")" >/dev/null 2>&1 || true
    fi
done

# the jars the game loads, from mod_info.json (loose JSON, so read with a pattern)
. "$(cd -- "$(dirname -- "$0")" && pwd)/lib.sh"
smoke_mod_info "$PROJECT"; info=$REPLY
entries=()
if [[ $info =~ \"jars\"[[:space:]]*:[[:space:]]*\[([^]]*)\] ]]; then
    list=${BASH_REMATCH[1]}
    while [[ $list =~ \"([^\"]+)\" ]]; do entries+=("${BASH_REMATCH[1]}"); list=${list#*\"*\"}; done
fi
jar_dirs=' '
for e in "${entries[@]}"; do [[ $e == */* ]] && jar_dirs+="${e%%/*} "; done

for entry in "$PROJECT"/*; do
    name=${entry##*/}
    if [[ -d $entry ]]; then
        case $name in jars|out|build|lib|libs|docs|src|gradle|native-smoke) continue ;; esac
        [[ $jar_dirs == *" $name "* ]] && continue
        if winpath "$STAGE" >/dev/null 2>&1; then
            MSYS_NO_PATHCONV=1 cmd.exe /c mklink /J "$(winpath "$STAGE/$name")" "$(winpath "$entry")" >/dev/null ||
                { printf 'mklink failed for %s\n' "$entry" >&2; exit 1; }
        else
            ln -s -- "$entry" "$STAGE/$name"
        fi
    elif [[ -f $entry ]]; then
        cp -- "$entry" "$STAGE/$name"
    fi
done

sources=()
for e in "${entries[@]}"; do sources+=("$PROJECT/$e"); done
for jar in "$@"; do
    (( ${#entries[@]} )) || break
    hit=0
    for i in "${!entries[@]}"; do [[ ${entries[i]##*/} == "${jar##*[/\\]}" ]] && hit=$i; done
    sources[hit]=$jar
done
for i in "${!entries[@]}"; do
    e=${entries[i]}; src=${sources[i]}
    [[ -f $src ]] || { printf 'Missing jar for %s: %s (build the mod first)\n' "$e" "$src" >&2; exit 1; }
    mkdir -p -- "$(dirname -- "$STAGE/$e")"
    cp -- "$src" "$STAGE/$e"
    printf '%s <- %s\n' "$e" "$src"
done
printf 'Staged %s in %s\n' "$PROJECT" "$STAGE"
