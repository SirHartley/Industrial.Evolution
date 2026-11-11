#!/usr/bin/env bash
# Shares one machine between smoke runs: up to `slots` games at once, or one perf run alone.
# Every run that starts a game holds a slot from before the launch until its game has exited.
# Rules: references/slots.md.
#   smoke-slot.sh acquire OWNER [--perf|--shared] [--wait S] [--holder PID]
#                                     take a slot and print its number; exit 3 after S seconds, 4 when stopped
#   smoke-slot.sh run OWNER [--perf|--shared] [--wait S] -- CMD [ARG...]
#                                     take a slot, run CMD with SMOKE_SLOT set and {slot} in its arguments replaced,
#                                     free the slot after
#   smoke-slot.sh release SLOT OWNER  free your slot
#   smoke-slot.sh touch SLOT          mark an interactive slot as in use
#   smoke-slot.sh game SLOT WORK_DIR [OWNER]
#                                     before a launch (launch-game.sh does this): record the folder whose game.pid
#                                     belongs to the slot, print the slot's mode; exit 4 when no game may start
#   smoke-slot.sh keep SLOT           hand a runner's slot to the session (runner KEEP=1)
#   smoke-slot.sh others SLOT         count the other held slots
#   smoke-slot.sh pause [REASON]      no new games: queued runs end, running games finish
#   smoke-slot.sh resume              lift the pause
#   smoke-slot.sh cancel OWNER        stop OWNER's queued runs, runners and games now and free its slots
#   smoke-slot.sh config [KEY=VALUE]  show or change the machine's settings
#   smoke-slot.sh get KEY             print one stored setting (empty when unset)
#   smoke-slot.sh status              settings, pause, slots, perf lock, queue, memory
#   smoke-slot.sh reap                remove stale locks (acquire does this itself)
# OWNER names the project and task: letters, digits, '.', '_' and '-', for example mymod.checks
# Settings live in the lock folder, so every session on the machine reads the same ones (built-in default, variable
# that overrides it for one process):
#   slots      games at once (3, SMOKE_SLOTS)
#   default    mode of a run that passes neither --perf nor --shared: shared or perf (shared, SMOKE_DEFAULT_MODE)
#   mem_mb     available memory a new game needs (5120: 3 GB heap plus native memory, SMOKE_SLOT_MEM_MB)
#   idle_min   minutes a slot without a holder process may go without a game or a touch (15, SMOKE_SLOT_IDLE)
#   perf_wait  seconds a perf run waits for the other games to end (2700, SMOKE_PERF_WAIT)
#   legacy     ';'-separated lock folders of an older one-game-at-a-time setup (none, SMOKE_LEGACY_LOCKS). No game
#              starts while someone else holds one; while slots are held this script holds them, so that setup waits
#   game_root, javac_dir, work_root   the game folder, the JDK 17+ bin folder and the folder for work folders.
#              smoke.sh setup finds and stores them, so no session has to pass or guess a path
# SMOKE_LOCK_ROOT moves the lock folder (default %LOCALAPPDATA%\starsector-smoke on Windows, /tmp/starsector-smoke-USER
# elsewhere); every session on the machine must use the same one. SMOKE_DETACHED=1: see "Stopping runs" in the rules.
set -uo pipefail
[[ $0 == */* ]] && HERE=${0%/*} || HERE=.
[[ $HERE == /* ]] || HERE="$PWD/$HERE"
HERE=${HERE//\/.\//\/}; HERE=${HERE%/.}
export MSYS_NO_PATHCONV=1
# each external command costs up to a second in Git Bash on a busy Windows machine, so this script uses builtins
. "$HERE/lib.sh"
smoke_root || exit 2
WIN=$SMOKE_WIN
ROOT=$SMOKE_ROOT_DIR
# C:\x and C:/x become /c/x
posix_path() { smoke_posix_path "$1"; }
[[ -d $ROOT/queue && -d $ROOT/cancel ]] || mkdir -p "$ROOT/queue" "$ROOT/cancel" || exit 2
TICKET_S=30
HELD_MAX_S=$(( 180 * 60 ))
OURS='smoke-slots:'

say() { printf '%s\n' "$*" >&2; }
nap() { sleep "$1" 2>/dev/null || read -rt "$1" < /dev/zero 2>/dev/null || true; }
clock() {
    printf -v NOW '%(%s)T' -1
    printf -v STAMP '%(%F %T)T' -1
    US=${EPOCHREALTIME:-}; US=${US/[.,]/}
    [[ $US =~ ^[0-9]{16}$ ]] || US=${NOW}000000
}
mtime() { MT=$(stat -c %Y -- "$1" 2>/dev/null) || MT=0; }

# KEY=VALUE lines of FILE into PREFIX_KEY variables, after clearing every PREFIX_ variable
readkv() {
    local file=$1 pre=$2 k v
    eval "unset \"\${!${pre}_@}\""
    [[ -r $file ]] || return 1
    while IFS='=' read -r k v || [[ -n $k ]]; do
        [[ $k =~ ^[a-z_]+$ ]] && printf -v "${pre}_$k" '%s' "$v"
    done < "$file"
    return 0
}

write_info() {
    local dir=$1; shift
    printf '%s\n' "$@" > "$dir/info.tmp" && mv -f -- "$dir/info.tmp" "$dir/info"
}

readkv "$ROOT/config" cfg
SLOTS=${SMOKE_SLOTS:-${cfg_slots:-3}}
DEFAULT_MODE=${SMOKE_DEFAULT_MODE:-${cfg_default:-shared}}
MEM_MB=${SMOKE_SLOT_MEM_MB:-${cfg_mem_mb:-5120}}
IDLE_MIN=${SMOKE_SLOT_IDLE:-${cfg_idle_min:-15}}
PERF_WAIT=${SMOKE_PERF_WAIT:-${cfg_perf_wait:-2700}}
LEGACY_RAW=${SMOKE_LEGACY_LOCKS:-${cfg_legacy:-}}
IDLE_S=$(( IDLE_MIN * 60 ))
LEGACY=()
IFS=';' read -r -a _raw <<< "$LEGACY_RAW"
for _p in "${_raw[@]}"; do [[ -n $_p ]] && posix_path "$_p" && LEGACY+=("${REPLY%/}"); done

# Process checks. Windows PIDs, so a session in another shell can check them; one process list per pass.
if (( WIN )); then
    read -r MYWIN < "/proc/$$/winpid"
    snap() { (( SNAP_OK )) || { TASKS=$(tasklist.exe /NH /FO CSV 2>/dev/null); SNAP_OK=1; }; }
    alive_w() { snap; [[ $TASKS == *"\",\"$1\",\""* ]]; }
    image_w() { snap; local l=${TASKS%%\",\"$1\",\"*}; l=${l##*$'\n'}; REPLY=${l#\"}; }
    mem_w() {
        snap; local l=${TASKS#*\",\"$1\",\"}; l=${l%%$'\n'*}; l=${l##*,\"}; l=${l//[^0-9]/}
        REPLY=$(( ${l:-0} / 1024 ))
    }
    winpid_of() { REPLY=; read -r REPLY 2>/dev/null < "/proc/$1/winpid"; }
    kill_tree_w() { taskkill.exe /PID "$1" /T /F >/dev/null 2>&1; }
else
    MYWIN=$$
    snap() { :; }
    alive_w() { kill -0 "$1" 2>/dev/null; }
    image_w() { REPLY=; read -r REPLY 2>/dev/null < "/proc/$1/comm"; }
    mem_w() {
        local k v; REPLY=0
        while read -r k v _; do [[ $k == VmRSS: ]] && REPLY=$(( v / 1024 )); done 2>/dev/null < "/proc/$1/status"
    }
    winpid_of() { REPLY=$1; }
    kill_tree_w() {
        local c
        for c in $(pgrep -P "$1" 2>/dev/null); do kill_tree_w "$c"; done
        kill -KILL "$1" 2>/dev/null
    }
fi
SNAP_OK=0

free_mb() {
    local k v avail= free=
    MEM=
    if [[ -r /proc/meminfo ]]; then
        # Git Bash fills MemFree with the memory Windows counts as available
        while read -r k v _; do
            case $k in MemAvailable:) avail=$v ;; MemFree:) free=$v ;; esac
        done < /proc/meminfo
        v=${avail:-$free}
        [[ -n $v ]] && MEM=$(( v / 1024 ))
    fi
    if [[ -z $MEM ]] && (( WIN )); then
        MEM=$(powershell.exe -NoProfile -Command "try { (Get-CimInstance Win32_PerfFormattedData_PerfOS_Memory -ErrorAction Stop).AvailableMBytes } catch { [int]((Get-CimInstance Win32_OperatingSystem -ErrorAction Stop).FreePhysicalMemory / 1024) }" 2>/dev/null | tr -dc '0-9')
    fi
    [[ -n $MEM ]]
}

# The processes above this one up to the first that is not a shell of this machine (PID 1 in /proc). When one of them
# ends, whoever started the run has stopped it, and the run must not take a slot or start a game any more.
ancestry() {
    local p=$PPID st f rp=') '
    WATCH=() WATCH_W=()
    [[ ${SMOKE_DETACHED:-0} == 1 ]] && return
    while [[ $p =~ ^[0-9]+$ ]] && (( p > 1 && ${#WATCH[@]} < 8 )); do
        WATCH+=("$p")
        winpid_of "$p"; WATCH_W+=("${REPLY:-$p}")
        read -r st 2>/dev/null < "/proc/$p/stat" || break
        st=${st##*"$rp"}
        read -r -a f <<< "$st"
        p=${f[1]:-1}
    done
}
orphaned() {
    local i
    for i in "${!WATCH[@]}"; do
        kill -0 "${WATCH[i]}" 2>/dev/null || { WHY="process ${WATCH_W[i]} that started this run has ended"; return 0; }
    done
    return 1
}

valid_owner() { [[ $1 =~ ^[A-Za-z0-9._-]+$ ]] || { say "OWNER must be letters, digits, '.', '_' or '-': $1"; exit 2; }; }
valid_slot() { [[ $1 =~ ^[0-9]+$ ]] || { say "Not a slot number: $1"; exit 2; }; }

paused() {
    readkv "$ROOT/paused" pz || return 1
    PAUSE_WHY="${pz_reason:-no reason given} (by ${pz_by:-?} at ${pz_since:-?})"
}
# true when OWNER was cancelled after the time T (microseconds)
cancelled() { readkv "$ROOT/cancel/$1" cx && [[ -n ${cx_t:-} ]] && (( cx_t >= $2 )); }

# Sets s_state (free, live or stale) and the slot's fields. A slot is stale only when its game is not running and its
# holder process is gone, or it has been idle past its limit. A running game always keeps its slot.
inspect() {
    local dir="$ROOT/slot-$1" last pid
    readkv "$dir/info" s
    s_beat=0 s_idle=0 s_game_alive=0 s_holder_alive=0 s_game_pid=
    if [[ ! -d $dir ]]; then s_state=free; return; fi
    if [[ -z ${s_id:-} ]]; then
        # mkdir done, info about to be written
        mtime "$dir"; (( NOW - MT < 60 )) && s_state=live || s_state=stale
        return
    fi
    read -r s_beat 2>/dev/null < "$dir/beat"; s_beat=${s_beat//[^0-9]/}; last=${s_beat:-0}
    if [[ -n ${s_game_dir:-} ]]; then
        pid=; read -r pid 2>/dev/null < "$s_game_dir/game.pid"; pid=${pid//[^0-9]/}
        if [[ -n $pid ]] && alive_w "$pid"; then
            s_game_alive=1; s_game_pid=$pid
        else
            mtime "$s_game_dir/logs/starsector.log"; (( MT > last )) && last=$MT
        fi
    fi
    s_idle=$(( NOW - last ))
    [[ -n ${s_holder:-} ]] && alive_w "$s_holder" && s_holder_alive=1
    if (( s_game_alive )); then
        s_state=live
    elif [[ -n ${s_holder:-} ]]; then
        (( s_holder_alive && s_idle < HELD_MAX_S )) && s_state=live || s_state=stale
    else
        (( s_idle < IDLE_S )) && s_state=live || s_state=stale
    fi
}

# Sets p_state (none, live or stale). While draining, the waiting process keeps it; once running, its slot does.
inspect_perf() {
    local dir="$ROOT/perf"
    readkv "$dir/info" p
    if [[ ! -d $dir ]]; then p_state=none; return; fi
    if [[ -z ${p_phase:-} ]]; then
        mtime "$dir"; (( NOW - MT < 60 )) && p_state=live || p_state=stale
        return
    fi
    if [[ $p_phase == draining ]]; then
        alive_w "${p_waiter:-0}" && p_state=live || p_state=stale
    else
        readkv "$ROOT/slot-${p_slot:-0}/info" ps
        [[ -n ${ps_id:-} && $ps_id == "${p_slot_id:-}" ]] && p_state=live || p_state=stale
    fi
}

# a ticket is fresh while its waiter rewrites it every pass
ticket_fresh() {
    readkv "$1" q || return 1
    if [[ -n ${q_t:-} ]]; then (( NOW - q_t <= TICKET_S )); else mtime "$1"; (( NOW - MT <= TICKET_S )); fi
}

mutex() {
    local m="$ROOT/$1.lock"
    mkdir "$m" 2>/dev/null && return 0
    # one that died keeps it at most a minute
    mtime "$m"; (( NOW - MT > 60 )) && rmdir "$m" 2>/dev/null && mkdir "$m" 2>/dev/null
}

count_held() {
    local d
    HELD=0
    for d in "$ROOT"/slot-*; do [[ -d $d ]] && HELD=$(( HELD + 1 )); done
}

# the first legacy lock someone else holds
legacy_foreign() {
    local lock line
    for lock in "${LEGACY[@]}"; do
        [[ -e $lock ]] || continue
        line=; read -r line 2>/dev/null < "$lock/owner.txt"
        [[ $line == "$OURS"* ]] && continue
        LOCK_BY="$lock (${line:-no owner.txt})"
        return 0
    done
    return 1
}
# takes the legacy locks for the slots; fails when someone else has one
legacy_hold() {
    local lock here=$HERE
    for lock in "${LEGACY[@]}"; do
        mkdir "$lock" 2>/dev/null || continue
        (( WIN )) && here=$(cygpath -w -- "$HERE")
        printf '%s games run in smoke slots; bash "%s/smoke-slot.sh" status\n' "$OURS" "$here" > "$lock/owner.txt"
    done
    ! legacy_foreign
}
legacy_sync() {
    (( ${#LEGACY[@]} )) || return 0
    local lock line
    count_held
    if (( HELD )); then legacy_hold; return 0; fi
    for lock in "${LEGACY[@]}"; do
        line=; read -r line 2>/dev/null < "$lock/owner.txt"
        [[ $line == "$OURS"* ]] && rm -f -- "$lock/owner.txt" && rmdir -- "$lock" 2>/dev/null
    done
    return 0
}

reap() {
    clock
    mutex reap || return 0
    local dir n t
    SNAP_OK=0
    for dir in "$ROOT"/slot-*; do
        [[ -d $dir ]] || continue
        n=${dir##*/slot-}
        inspect "$n"
        if [[ $s_state == stale ]]; then
            printf '%s reaped slot %s: owner %s, holder %s, idle %ss, no game\n' "$STAMP" "$n" "${s_owner:-?}" "${s_holder:-none}" "$s_idle" >> "$ROOT/reaped.log"
            rm -rf -- "$dir"
        fi
    done
    inspect_perf
    if [[ $p_state == stale ]]; then
        printf '%s reaped perf lock of %s\n' "$STAMP" "${p_owner:-?}" >> "$ROOT/reaped.log"
        rm -rf -- "$ROOT/perf"
    fi
    for t in "$ROOT"/queue/*; do
        [[ -f $t ]] && ! ticket_fresh "$t" && rm -f -- "$t"
    done
    for t in "$ROOT"/cancel/*; do
        [[ -f $t ]] && readkv "$t" cx && (( NOW - ${cx_t:-0} / 1000000 > 86400 )) && rm -f -- "$t"
    done
    legacy_sync
    rmdir "$ROOT/reap.lock" 2>/dev/null
}

# Takes the first free slot into SLOT. A shared run that finds a perf lock gives the slot back, so a perf run that is
# draining never sees a game start after it began waiting.
take() {
    local mode=$1 holder=$2 holder_pid=$3 owner=$4 n dir
    for (( n = 1; n <= SLOTS; n++ )); do
        dir="$ROOT/slot-$n"
        mkdir "$dir" 2>/dev/null || continue
        if [[ $mode == shared && -d $ROOT/perf ]] || ! legacy_hold; then rmdir "$dir"; return 1; fi
        clock
        printf '%s\n' "$NOW" > "$dir/beat"
        write_info "$dir" "id=$US-$$" "owner=$owner" "mode=$mode" "holder=$holder" "holder_pid=$holder_pid" \
            "since=$STAMP" "since_us=$US" "game_dir="
        SLOT=$n
        return 0
    done
    return 1
}

# Waits for a slot and sets SLOT. Ends with 4 when the machine is paused, OWNER is cancelled or the process that
# started the run has ended, so a run stopped while it queued never starts a game later.
acquire() {
    local owner=$1 mode=$2 wait_s=$3 holder=$4 holder_pid=$5
    local ticket t0 deadline reason last_reason= older older_perf t
    ancestry
    reap
    t0=$US
    ticket="$ROOT/queue/$US.$$"
    deadline=$(( NOW + wait_s ))
    while :; do
        clock
        SNAP_OK=0
        printf 'owner=%s\nmode=%s\npid=%s\nwinpid=%s\nt0=%s\nt=%s\n' "$owner" "$mode" "$$" "$MYWIN" "$t0" "$NOW" > "$ticket"
        WHY=
        if paused; then WHY="machine paused: $PAUSE_WHY"
        elif cancelled "$owner" "$t0"; then WHY="$owner was cancelled"
        else orphaned; fi
        if [[ -n $WHY ]]; then
            rm -f -- "$ticket"
            readkv "$ROOT/perf/info" p && [[ ${p_waiter:-} == "$MYWIN" && ${p_phase:-} == draining ]] && rm -rf -- "$ROOT/perf"
            say "STOPPED: $WHY"
            return 4
        fi
        reason=
        if legacy_foreign; then
            reason="older lock $LOCK_BY is held"
        else
            older=0; older_perf=0
            for t in "$ROOT"/queue/*; do
                [[ -f $t && $t < $ticket ]] || continue
                ticket_fresh "$t" || continue
                older=$(( older + 1 ))
                [[ ${q_mode:-} == perf ]] && older_perf=1
            done
            count_held
            if [[ $mode == perf ]]; then
                inspect_perf
                if (( older > 0 )); then
                    reason="$older run(s) queued ahead"
                else
                    if [[ $p_state == none ]] && mkdir "$ROOT/perf" 2>/dev/null; then
                        write_info "$ROOT/perf" "owner=$owner" "phase=draining" "waiter=$MYWIN" "since=$STAMP"
                        p_waiter=$MYWIN p_owner=$owner
                    fi
                    if [[ ${p_waiter:-} == "$MYWIN" ]]; then
                        if (( HELD == 0 )) && take perf "$holder" "$holder_pid" "$owner"; then
                            readkv "$ROOT/slot-$SLOT/info" ps
                            write_info "$ROOT/perf" "owner=$owner" "phase=running" "slot=$SLOT" "slot_id=$ps_id" "since=$STAMP"
                            rm -f -- "$ticket"
                            return 0
                        fi
                        reason="$HELD game(s) still running"
                    else
                        reason="perf run of ${p_owner:-?} holds the machine"
                    fi
                fi
            else
                if [[ -d $ROOT/perf ]]; then
                    readkv "$ROOT/perf/info" p
                    reason="perf run of ${p_owner:-?} holds the machine"
                elif (( older_perf )); then
                    reason="a perf run is queued ahead"
                elif (( SLOTS - HELD <= older )); then
                    reason="all $SLOTS slots busy, $older run(s) queued ahead"
                elif free_mb && (( MEM < MEM_MB )); then
                    reason="$MEM MB memory available, a game needs $MEM_MB"
                elif take shared "$holder" "$holder_pid" "$owner"; then
                    rm -f -- "$ticket"
                    return 0
                else
                    reason="slots busy"
                fi
            fi
        fi
        [[ $reason != "$last_reason" ]] && say "waiting: $reason" && last_reason=$reason
        if (( NOW >= deadline )); then
            rm -f -- "$ticket"
            readkv "$ROOT/perf/info" p && [[ ${p_waiter:-} == "$MYWIN" ]] && rm -rf -- "$ROOT/perf"
            say "TIMEOUT after ${wait_s}s: $reason"
            status >&2
            return 3
        fi
        nap 5
        reap
    done
}

release() {
    local n=$1 who=$2 dir="$ROOT/slot-$1" i
    readkv "$dir/info" r
    [[ -d $dir ]] || { say "Slot $n is not held"; return 0; }
    if [[ -n $who && ${r_owner:-} != "$who" ]]; then
        say "Slot $n belongs to ${r_owner:-?}, not $who; not releasing it"
        return 1
    fi
    readkv "$ROOT/perf/info" p && [[ ${p_slot:-} == "$n" ]] && rm -rf -- "$ROOT/perf"
    rm -rf -- "$dir"
    say "released slot $n"
    # the last slot gives the legacy locks back
    if (( ${#LEGACY[@]} )); then
        for i in 1 2 3; do clock; mutex reap && { legacy_sync; rmdir "$ROOT/reap.lock" 2>/dev/null; break; }; nap 1; done
    fi
    return 0
}

# TERM first, so a runner stops its game and frees its slot itself; then the whole process tree
end_proc() {
    local pid=$1 win=$2 i
    if [[ -n $pid ]] && winpid_of "$pid" && [[ $REPLY == "$win" ]] && kill -TERM "$pid" 2>/dev/null; then
        for i in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || return 0; nap 1; done
        say "PID $win did not stop after TERM; ending its process tree"
    else
        SNAP_OK=0
        alive_w "$win" || return 0
        image_w "$win"
        [[ ${REPLY,,} == bash* ]] || { say "PID $win is $REPLY, not a run of this script; not stopping it"; return 1; }
    fi
    kill_tree_w "$win"
}

cancel() {
    local owner=$1 dir n stopped=0 left t i
    clock
    printf 't=%s\nsince=%s\n' "$US" "$STAMP" > "$ROOT/cancel/$owner"
    say "cancelled $owner: queued runs end on their next check (5 s)"
    SNAP_OK=0
    for dir in "$ROOT"/slot-*; do
        [[ -d $dir ]] || continue
        n=${dir##*/slot-}
        clock; inspect "$n"
        [[ ${s_owner:-} == "$owner" ]] || continue
        if (( s_holder_alive )); then
            say "slot $n: stopping holder PID $s_holder"
            end_proc "${s_holder_pid:-}" "$s_holder"
            SNAP_OK=0; clock; inspect "$n"
        fi
        if (( s_game_alive )); then
            say "slot $n: stopping game PID $s_game_pid"
            bash "$HERE/game-proc.sh" stop "$s_game_dir" || say "slot $n: game PID $s_game_pid did not stop; stop it by hand"
        fi
        [[ -d $dir ]] && release "$n" "$owner" 2>/dev/null
        stopped=$(( stopped + 1 ))
    done
    for i in $(seq 1 12); do
        clock; left=0
        for t in "$ROOT"/queue/*; do
            [[ -f $t ]] && ticket_fresh "$t" && [[ ${q_owner:-} == "$owner" ]] && left=$(( left + 1 ))
        done
        (( left == 0 )) && break
        nap 5
    done
    say "cancel $owner: $stopped slot(s) freed, ${left:-0} queued run(s) still waiting"
    (( left == 0 ))
}

status() {
    local n t legacy_state lock
    clock
    SNAP_OK=0
    printf 'Lock folder %s: %s slots, default %s, memory gate %s MB, idle %s min, perf wait %ss\n' "$ROOT" "$SLOTS" "$DEFAULT_MODE" "$MEM_MB" "$IDLE_MIN" "$PERF_WAIT"
    for lock in "${LEGACY[@]}"; do
        legacy_state=
        if [[ ! -e $lock ]]; then legacy_state=free
        else read -r legacy_state 2>/dev/null < "$lock/owner.txt"; [[ $legacy_state == "$OURS"* ]] && legacy_state='held for the slots'; fi
        printf 'older lock %s: %s\n' "$lock" "${legacy_state:-held, no owner.txt}"
    done
    paused && printf 'PAUSED: %s\n' "$PAUSE_WHY"
    for (( n = 1; n <= SLOTS; n++ )); do
        inspect "$n"
        if [[ $s_state == free ]]; then printf 'slot %s  free\n' "$n"; continue; fi
        printf 'slot %s  %s  %s  %s' "$n" "$s_state" "${s_owner:-?}" "${s_mode:-?}"
        [[ -n ${s_holder:-} ]] && printf '  holder %s %s' "$s_holder" "$( (( s_holder_alive )) && echo running || echo gone)"
        if (( s_game_alive )); then
            mem_w "$s_game_pid"
            printf '  game PID %s, %s MB' "$s_game_pid" "$REPLY"
        elif [[ -n ${s_game_dir:-} ]]; then
            printf '  no game'
        fi
        printf '  idle %sm' "$(( s_idle / 60 ))"
        [[ -n ${s_game_dir:-} ]] && printf '  %s' "$s_game_dir"
        printf '\n'
    done
    inspect_perf
    [[ $p_state != none ]] && printf 'perf  %s  %s  %s%s\n' "$p_state" "${p_owner:-?}" "${p_phase:-?}" "${p_slot:+, slot $p_slot}"
    for t in "$ROOT"/queue/*; do
        [[ -f $t ]] && ticket_fresh "$t" || continue
        printf 'queued  %s  %s  PID %s\n' "${q_owner:-?}" "${q_mode:-?}" "${q_winpid:-?}"
    done
    for t in "$ROOT"/cancel/*; do
        [[ -f $t ]] && readkv "$t" cx && (( NOW - ${cx_t:-0} / 1000000 < 3600 )) && printf 'cancelled  %s at %s\n' "${t##*/}" "${cx_since:-?}"
    done
    free_mb
    printf 'memory  %s MB available, a new game needs %s MB\n' "${MEM:-?}" "$MEM_MB"
}

config() {
    local kv key val k
    local keys=(slots default mem_mb idle_min perf_wait legacy game_root javac_dir work_root)
    if (( $# == 0 )); then
        for k in "${keys[@]}"; do eval "printf '%s=%s\n' \"\$k\" \"\${cfg_$k:-}\""; done
        printf 'in effect for this process: slots %s, default %s, mem_mb %s, idle_min %s, perf_wait %s, legacy %s\n' \
            "$SLOTS" "$DEFAULT_MODE" "$MEM_MB" "$IDLE_MIN" "$PERF_WAIT" "${LEGACY_RAW:-none}"
        return 0
    fi
    for kv in "$@"; do
        [[ $kv == *=* ]] || { say "Use KEY=VALUE: $kv"; return 2; }
        key=${kv%%=*}; val=${kv#*=}
        case $key in
            slots|mem_mb|idle_min|perf_wait) [[ -z $val || $val =~ ^[1-9][0-9]*$ ]] || { say "$key takes a number"; return 2; } ;;
            default) [[ -z $val || $val == shared || $val == perf ]] || { say 'default is shared or perf'; return 2; } ;;
            legacy|game_root|javac_dir|work_root) ;;
            *) say "Unknown setting $key; settings: ${keys[*]}"; return 2 ;;
        esac
        printf -v "cfg_$key" '%s' "$val"
    done
    for k in "${keys[@]}"; do eval "[[ -n \${cfg_$k:-} ]] && printf '%s=%s\n' \"\$k\" \"\$cfg_$k\""; done > "$ROOT/config.tmp"
    mv -f -- "$ROOT/config.tmp" "$ROOT/config"
    say "saved $ROOT/config"
}

holder_of() { winpid_of "$1"; HOLDER_W=${REPLY:-$1}; }

cmd=${1:-}
shift || true
case $cmd in
    acquire|run)
        (( $# >= 1 )) || { sed -n '2,22p' "$0" >&2; exit 2; }
        owner=$1; shift
        valid_owner "$owner"
        mode=; wait_s=; holder=
        while (( $# )); do
            case $1 in
                --perf) mode=perf; shift ;;
                --shared) mode=shared; shift ;;
                --wait) wait_s=$2; shift 2 ;;
                --holder) holder=$2; shift 2 ;;
                --) shift; break ;;
                *) [[ $cmd == acquire ]] || break; say "Unknown option: $1"; exit 2 ;;
            esac
        done
        mode=${mode:-$DEFAULT_MODE}
        [[ $mode == perf || $mode == shared ]] || { say "default must be shared or perf, not $mode"; exit 2; }
        [[ $mode == perf ]] && wait_s=${wait_s:-$PERF_WAIT}
        wait_s=${wait_s:-3600}
        if [[ $cmd == acquire ]]; then
            HOLDER_W=
            [[ -n $holder ]] && holder_of "$holder"
            acquire "$owner" "$mode" "$wait_s" "$HOLDER_W" "$holder" || exit $?
            printf '%s\n' "$SLOT"
            exit 0
        fi
        (( $# )) || { say 'run needs a command after --'; exit 2; }
        acquire "$owner" "$mode" "$wait_s" "$MYWIN" "$$" || exit $?
        readkv "$ROOT/slot-$SLOT/info" own
        since_us=$own_since_us
        # a runner that handed the slot to the session (KEEP=1) cleared the holder, so the slot stays
        trap 'readkv "$ROOT/slot-$SLOT/info" own; [[ ${own_holder:-} == "$MYWIN" ]] && release "$SLOT" "$owner"' EXIT
        export SMOKE_SLOT=$SLOT SMOKE_OWNER=$owner
        args=()
        for a in "$@"; do args+=("${a//\{slot\}/$SLOT}"); done
        say "slot $SLOT ($mode) for $owner"
        "${args[@]}" &
        child=$!
        stop_child() { winpid_of "$child"; [[ -n $REPLY ]] && kill_tree_w "$REPLY"; kill "$child" 2>/dev/null; wait "$child" 2>/dev/null; }
        trap 'stop_child; exit 143' TERM
        trap 'stop_child; exit 130' INT
        while kill -0 "$child" 2>/dev/null; do
            clock; WHY=
            cancelled "$owner" "$since_us" && WHY="$owner was cancelled"
            [[ -z $WHY ]] && orphaned
            if [[ -n $WHY ]]; then say "STOPPED: $WHY"; stop_child; exit 4; fi
            nap 3
        done
        wait "$child"
        exit $?
        ;;
    release)
        (( $# == 2 )) || { say 'Usage: smoke-slot.sh release SLOT OWNER'; exit 2; }
        valid_slot "$1"
        release "$1" "$2"
        ;;
    touch)
        valid_slot "${1:-}"
        [[ -d $ROOT/slot-$1 ]] || { say "Slot $1 is not held; take one with acquire"; exit 1; }
        clock
        printf '%s\n' "$NOW" > "$ROOT/slot-$1/beat"
        ;;
    game)
        (( $# == 2 || $# == 3 )) || { say 'Usage: smoke-slot.sh game SLOT WORK_DIR [OWNER]'; exit 2; }
        valid_slot "$1"
        dir="$ROOT/slot-$1"
        clock
        readkv "$dir/info" s && [[ -n ${s_id:-} ]] || { say "STOPPED: slot $1 is not held (released, or reaped as stale); take a new one"; exit 1; }
        [[ -n ${3:-} && $3 != "${s_owner:-}" ]] && { say "STOPPED: slot $1 belongs to ${s_owner:-?}, not $3"; exit 4; }
        paused && { say "STOPPED: machine paused: $PAUSE_WHY"; exit 4; }
        cancelled "${s_owner:-}" "${s_since_us:-0}" && { say "STOPPED: ${s_owner:-} was cancelled"; exit 4; }
        legacy_hold || { say "STOPPED: older lock $LOCK_BY is held"; exit 4; }
        work=$(cd -- "$2" && pwd) || exit 2
        write_info "$dir" "id=$s_id" "owner=${s_owner:-}" "mode=${s_mode:-}" "holder=${s_holder:-}" "holder_pid=${s_holder_pid:-}" \
            "since=${s_since:-}" "since_us=${s_since_us:-}" "game_dir=$work"
        printf '%s\n' "$NOW" > "$dir/beat"
        printf '%s\n' "${s_mode:-shared}"
        ;;
    keep)
        valid_slot "${1:-}"
        dir="$ROOT/slot-$1"
        readkv "$dir/info" s && [[ -n ${s_id:-} ]] || exit 1
        clock
        write_info "$dir" "id=$s_id" "owner=${s_owner:-}" "mode=${s_mode:-}" "holder=" "holder_pid=" \
            "since=${s_since:-}" "since_us=${s_since_us:-}" "game_dir=${s_game_dir:-}"
        printf '%s\n' "$NOW" > "$dir/beat"
        ;;
    others)
        valid_slot "${1:-}"
        count=0
        for dir in "$ROOT"/slot-*; do [[ -d $dir && $dir != "$ROOT/slot-$1" ]] && count=$(( count + 1 )); done
        echo "$count"
        ;;
    pause)
        clock
        printf 'reason=%s\nby=%s\nsince=%s\n' "${*:-}" "${SMOKE_OWNER:-${USER:-${USERNAME:-?}}}" "$STAMP" > "$ROOT/paused"
        say "paused: queued runs end within 5 s, running games finish, no new game starts until: smoke-slot.sh resume"
        ;;
    resume) rm -f -- "$ROOT/paused"; say 'resumed' ;;
    cancel)
        (( $# == 1 )) || { say 'Usage: smoke-slot.sh cancel OWNER'; exit 2; }
        valid_owner "$1"
        cancel "$1"
        ;;
    config) config "$@" ;;
    get)
        (( $# == 1 )) && [[ $1 =~ ^[a-z_]+$ ]] || { say 'Usage: smoke-slot.sh get KEY'; exit 2; }
        eval "printf '%s\\n' \"\${cfg_$1:-}\""
        ;;
    status) reap; status ;;
    reap) reap ;;
    *) sed -n '2,22p' "$0" >&2; exit 2 ;;
esac
