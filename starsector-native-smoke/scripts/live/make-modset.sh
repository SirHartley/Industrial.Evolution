#!/usr/bin/env bash
# Builds WORK_DIR/mods as links to existing mod folders and writes enabled_mods.json from their mod_info.json ids.
# The user's own mods folder and enabled_mods.json are never touched.
#   make-modset.sh WORK_DIR MOD_FOLDER [MOD_FOLDER...]
# Each MOD_FOLDER is a full path to a mod (the folder holding mod_info.json).
# On Windows (Git Bash) and on WSL against a Windows install, links are NTFS junctions so the Windows JVM sees them.
set -euo pipefail
(( $# >= 2 )) || { printf 'Usage: %s WORK_DIR MOD_FOLDER [MOD_FOLDER...]\n' "$0" >&2; exit 2; }
WORK=$(mkdir -p -- "$1" && cd -- "$1" && pwd)
shift
MODS="$WORK/mods"
mkdir -p "$MODS"

if command -v cygpath >/dev/null; then
    winpath() { cygpath -w -- "$1"; }
elif command -v wslpath >/dev/null && [[ $WORK == /mnt/* ]]; then
    winpath() { wslpath -w -- "$1"; }
else
    winpath() { return 1; }
fi

# Remove only links (and the old enabled_mods.json); never delete a real folder.
for entry in "$MODS"/* "$MODS"/.[!.]*; do
    [[ -e $entry || -L $entry ]] || continue
    if [[ -L $entry ]]; then
        rm -f -- "$entry"
    elif winpath "$entry" >/dev/null 2>&1 && [[ -d $entry ]]; then
        # A junction looks like a plain folder to some shells; rmdir removes the link only.
        MSYS_NO_PATHCONV=1 cmd.exe /c rmdir "$(winpath "$entry")" >/dev/null 2>&1 || {
            printf 'Not a link, leaving it alone: %s\n' "$entry" >&2
        }
    elif [[ $(basename -- "$entry") == enabled_mods.json ]]; then
        rm -f -- "$entry"
    else
        printf 'Not a link, leaving it alone: %s\n' "$entry" >&2
    fi
done

ids=()
for mod in "$@"; do
    [[ -r "$mod/mod_info.json" ]] || { printf 'No mod_info.json in %s\n' "$mod" >&2; exit 1; }
    name=$(basename -- "$mod")
    if winpath "$MODS" >/dev/null 2>&1; then
        MSYS_NO_PATHCONV=1 cmd.exe /c mklink /J "$(winpath "$MODS/$name")" "$(winpath "$mod")" >/dev/null ||
            { printf 'mklink failed for %s\n' "$mod" >&2; exit 1; }
    else
        ln -s -- "$mod" "$MODS/$name"
    fi
    # mod_info.json is loose JSON (comments, trailing commas), so read the id with a pattern.
    id=$(grep -v '^[[:space:]]*#' "$mod/mod_info.json" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -n 1 | sed 's/.*"\([^"]*\)"$/\1/')
    ids+=("$id")
    printf '%s -> %s (%s)\n' "$name" "$mod" "$id"
done
printf '{"enabledMods":[%s]}\n' "$(printf '"%s",' "${ids[@]}" | sed 's/,$//')" > "$MODS/enabled_mods.json"
cat "$MODS/enabled_mods.json"
