#!/usr/bin/env bash
# Prints the folder under GAME_ROOT/mods whose mod_info.json has this id. Folder names carry versions
# ("LazyLib-3.0.0", "! PatchLib !-1.0.3") and several versions can sit side by side; the highest version wins.
#   find-mod.sh GAME_ROOT MOD_ID
set -uo pipefail
(( $# == 2 )) || { sed -n '2,4p' "$0" >&2; exit 2; }
# One awk pass over every mod_info.json. The mod's own id is the first "id" in the file (dependencies come later).
# The version is a string or {"major":..,"minor":..,"patch":..}.
awk -v want="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]')" '
function val(s, key,   m) {
    if (match(s, "\"" key "\"[ \t]*:[ \t]*\"?[0-9A-Za-z._ -]*")) {
        m = substr(s, RSTART, RLENGTH); sub(/^[^:]*:[ \t]*"?/, "", m); return m
    }
    return ""
}
FNR == 1 { id[FILENAME] = ""; ver[FILENAME] = ""; maj = min = pat = "" }
{
    sub(/#.*/, "")
    if (id[FILENAME] == "" && (v = val($0, "id")) != "") id[FILENAME] = tolower(v)
    if (ver[FILENAME] == "" && $0 ~ /"version"[ \t]*:[ \t]*"/) ver[FILENAME] = val($0, "version")
    if (maj == "") maj = val($0, "major"); if (min == "") min = val($0, "minor"); if (pat == "") pat = val($0, "patch")
    if (ver[FILENAME] == "" && maj != "" && min != "") ver[FILENAME] = maj "." min "." pat
}
END { for (f in id) if (id[f] == want) { d = f; sub(/\/mod_info\.json$/, "", d); print ver[f] "\t" d } }
' "$1"/mods/*/mod_info.json 2>/dev/null | sort -V -k1,1 | tail -n 1 | cut -f2 | grep . ||
    { printf 'No mod with id %s in %s/mods\n' "$2" "$1" >&2; exit 1; }
