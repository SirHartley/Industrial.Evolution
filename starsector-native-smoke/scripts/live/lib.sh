#!/usr/bin/env bash
# Sourced by the scripts of this skill: the machine folder, the machine's settings and the built tools.
#   . "$HERE/lib.sh"
#   smoke_root        SMOKE_ROOT_DIR = the machine folder (locks, config, tools, default work folder), SMOKE_WIN = 1 in Git Bash
#   smoke_cfg KEY     REPLY = a setting from SMOKE_ROOT_DIR/config, empty when unset (smoke.sh setup writes them)
#   smoke_jdk         puts SMOKE_JAVAC_DIR, or the stored javac_dir, on PATH; fails when there is no javac
#   smoke_tools       REPLY = folder for the tools built from this copy of the skill (gamectl.exe, prefs helper, cursor guard)
#   smoke_gamectl     REPLY = gamectl.exe, built on first use
#   smoke_version     REPLY = the skill's version
#   smoke_posix_path  REPLY = C:\x or C:/x as /c/x
#   smoke_mod_info    REPLY = DIR/mod_info.json without its '#' comment lines (loose JSON, read with patterns)
# Every function is safe under set -eu.
SMOKE_LIB=${BASH_SOURCE[0]%/*}
[[ $SMOKE_LIB == /* ]] || SMOKE_LIB="$PWD/$SMOKE_LIB"
SMOKE_LIB=${SMOKE_LIB//\/.\//\/}; SMOKE_LIB=${SMOKE_LIB%/.}

smoke_posix_path() {
    local p=${1//\\//}
    if (( ${SMOKE_WIN:-0} )) && [[ $p =~ ^([A-Za-z]):(/.*)?$ ]]; then
        if [[ -d /${BASH_REMATCH[1],,} ]]; then p="/${BASH_REMATCH[1],,}${BASH_REMATCH[2]}"; else p=$(cygpath -u -- "$p"); fi
    fi
    REPLY=$p
}

smoke_root() {
    [[ -n ${SMOKE_ROOT_DIR:-} ]] && return 0
    SMOKE_WIN=0
    # each external command costs up to a second in Git Bash on a busy Windows machine, so builtins where possible
    if command -v tasklist.exe >/dev/null 2>&1 && [[ -r /proc/$$/winpid ]]; then SMOKE_WIN=1; fi
    if [[ -n ${SMOKE_LOCK_ROOT:-} ]]; then
        smoke_posix_path "$SMOKE_LOCK_ROOT"
    elif (( SMOKE_WIN )) && [[ -n ${LOCALAPPDATA:-} ]]; then
        smoke_posix_path "$LOCALAPPDATA/starsector-smoke"
    else
        REPLY="/tmp/starsector-smoke-${USER:-$(id -un)}"
    fi
    [[ -n $REPLY ]] || { echo 'No machine folder' >&2; return 2; }
    SMOKE_ROOT_DIR=$REPLY
    [[ -d $SMOKE_ROOT_DIR ]] || mkdir -p "$SMOKE_ROOT_DIR" || return 2
    return 0
}

smoke_cfg() {
    local k v
    REPLY=
    smoke_root || return 0
    [[ -r $SMOKE_ROOT_DIR/config ]] || return 0
    while IFS='=' read -r k v || [[ -n $k ]]; do
        if [[ $k == "$1" ]]; then REPLY=${v%$'\r'}; fi
    done < "$SMOKE_ROOT_DIR/config"
    return 0
}

smoke_jdk() {
    smoke_root || return 1
    if [[ -z ${SMOKE_JAVAC_DIR:-} ]]; then
        smoke_cfg javac_dir
        if [[ -n $REPLY ]]; then export SMOKE_JAVAC_DIR=$REPLY; fi
    fi
    if [[ -n ${SMOKE_JAVAC_DIR:-} ]]; then
        smoke_posix_path "$SMOKE_JAVAC_DIR"
        case ":$PATH:" in *":$REPLY:"*) ;; *) export PATH="$REPLY:$PATH" ;; esac
    fi
    command -v javac >/dev/null 2>&1
}

# one folder per version of the tool sources, so two copies of the skill on one machine never fight over a locked exe
smoke_tools() {
    local sum
    smoke_root || return 1
    sum=$(cat "$SMOKE_LIB/gamectl.cs" "$SMOKE_LIB/../../templates/SmokePrefs.java" "$SMOKE_LIB/../../templates/CursorGuard.java" | cksum) || return 1
    REPLY="$SMOKE_ROOT_DIR/tools/${sum%% *}"
    [[ -d $REPLY ]] || mkdir -p "$REPLY" || return 1
    return 0
}

smoke_gamectl() {
    local exe
    smoke_tools || return 1
    exe="$REPLY/gamectl.exe"
    if [[ ! -x $exe ]]; then
        bash "$SMOKE_LIB/build-tools.sh" gamectl >&2 || return 1
    fi
    REPLY=$exe
    return 0
}

smoke_mod_info() {
    local line
    REPLY=
    [[ -r $1/mod_info.json ]] || return 1
    while IFS= read -r line || [[ -n $line ]]; do
        if [[ ! $line =~ ^[[:space:]]*# ]]; then REPLY+=$line$'\n'; fi
    done < "$1/mod_info.json"
    return 0
}

smoke_version() {
    REPLY=unknown
    if [[ -r $SMOKE_LIB/../../VERSION ]]; then read -r REPLY < "$SMOKE_LIB/../../VERSION" || true; fi
    return 0
}
