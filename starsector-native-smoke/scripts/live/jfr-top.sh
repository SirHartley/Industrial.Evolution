#!/usr/bin/env bash
# Reads a Java Flight Recorder file from a run: CPU, allocation and GC, with warnings when your code stands out.
#   jfr-top.sh PROFILE.jfr [PACKAGE_PREFIX]                     full report
#   jfr-top.sh --summary PROFILE.jfr [PACKAGE_PREFIX]           one PROFILE line, plus PROFILE WARN lines
#   jfr-top.sh --compare BEFORE.jfr AFTER.jfr [PACKAGE_PREFIX]  what moved between two runs of the same steps
# PACKAGE_PREFIX is the mod's own package, such as mymod. Warnings use PROFILE_WARN_SHARE (default 10, percent of all samples)
# and PROFILE_WARN_PAUSE (default 250, ms). Needs jfr.exe from a JDK: on PATH, in SMOKE_JAVAC_DIR, or the javac_dir setting
# stored by smoke.sh setup. The game's JRE records but has no reader.
set -euo pipefail
MODE=report
case ${1:-} in
    --summary) MODE=summary; shift ;;
    --compare) MODE=compare; shift ;;
esac
(( $# >= 1 )) || { sed -n '3,5p' "$0" >&2; exit 2; }
. "$(cd -- "$(dirname -- "$0")" && pwd)/lib.sh"
smoke_jdk || true
command -v jfr >/dev/null 2>&1 || { echo 'NO_JDK: jfr not found, so there is no profile report. Run smoke.sh setup, or set SMOKE_JAVAC_DIR to a JDK bin folder' >&2; exit 1; }
TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT
WARN_SHARE=${PROFILE_WARN_SHARE:-10}
WARN_PAUSE=${PROFILE_WARN_PAUSE:-250}

# jfr print cuts stacks to 5 frames unless told otherwise, which hides nearly all mod code under vanilla frames
dump() { jfr print --stack-depth 64 --events "$2" "$1" > "$3"; }

# prints: total weight, weight with PKG on the stack, then a table of "weight kind frame" (kind self or own)
digest() {
    awk -v pkg="$2" -v alloc="$3" '
        alloc && /^ *weight = / { v = $3; u = $4; w = v * (u == "kB" ? 1 / 1024 : u == "MB" ? 1 : u == "GB" ? 1024 : 1 / 1048576); next }
        /stackTrace = \[/ { s = 1; top = ""; own = ""; if (!alloc) w = 1; next }
        s && /^ *\]/ {
            s = 0; total += w
            if (top != "") self[top] += w
            if (own != "") { inc += w; first[own] += w }
            next
        }
        s {
            line = $0; gsub(/^ +/, "", line); sub(/ +line:.*/, "", line)
            if (line == "...") next
            if (top == "") top = line
            if (pkg != "" && own == "" && index(line, pkg) == 1) own = line
        }
        END {
            printf "%.0f %.0f\n", total, inc
            for (k in self) printf "%.1f self %s\n", self[k], k
            for (k in first) printf "%.1f own %s\n", first[k], k
        }' "$1"
}

show() {  # digest file, kind, lines, unit
    tail -n +2 "$1" | awk -v k="$2" '$2 == k { w = $1; $1 = ""; $2 = ""; sub(/^ +/, ""); printf "%.1f %s\n", w, $0 }' | sort -rn | head -"$3" |
        awk -v t="$(head -1 "$1" | cut -d' ' -f1)" '{ w = $1; $1 = ""; printf "%6.1f%% %s\n", (t > 0 ? 100 * w / t : 0), $0 }'
}

prepare() {  # jfr file, package, prefix
    # three JVM starts, seconds apiece, so side by side
    local a b c
    dump "$1" jdk.ExecutionSample "$TMP/$3-cpu.txt" & a=$!
    dump "$1" jdk.ObjectAllocationSample "$TMP/$3-alloc.txt" & b=$!
    dump "$1" jdk.GarbageCollection "$TMP/$3-gc.txt" & c=$!
    wait "$a" || return 1
    wait "$b" || return 1
    wait "$c" || return 1
    digest "$TMP/$3-cpu.txt" "$2" 0 > "$TMP/$3-cpu.dig"
    digest "$TMP/$3-alloc.txt" "$2" 1 > "$TMP/$3-alloc.dig"
}

gc() {  # gc dump: prints count, total pause ms, longest pause ms
    awk '
        function ms(v, u) { return v * (u == "s" ? 1000 : u == "us" ? 0.001 : u == "ns" ? 0.000001 : 1) }
        /^ *sumOfPauses = /  { n++; sum += ms($3, $4) }
        /^ *longestPause = / { if (ms($3, $4) > max) max = ms($3, $4) }
        END { printf "%d %.0f %.0f\n", n, sum, max }' "$1"
}

share() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.1f", (a > 0 ? 100 * b / a : 0) }'; }

summary() {  # prefix, package
    read -r cpu_all cpu_own < "$TMP/$1-cpu.dig"
    read -r mem_all mem_own < "$TMP/$1-alloc.dig"
    read -r gc_n gc_sum gc_max < <(gc "$TMP/$1-gc.txt")
    cpu_share=$(share "$cpu_all" "$cpu_own")
    mem_share=$(share "$mem_all" "$mem_own")
    printf 'PROFILE: %s CPU samples, %s MB allocated, %s GCs, longest pause %s ms' "$cpu_all" "$mem_all" "$gc_n" "$gc_max"
    if [[ -n $2 ]]; then printf ', %s %s%% of CPU, %s%% of allocation' "$2" "$cpu_share" "$mem_share"; fi
    printf '\n'
    if [[ -n $2 ]]; then
        awk -v a="$cpu_share" -v w="$WARN_SHARE" 'BEGIN { exit !(a >= w) }' && printf 'PROFILE WARN: %s is %s%% of CPU samples\n' "$2" "$cpu_share"
        awk -v a="$mem_share" -v w="$WARN_SHARE" 'BEGIN { exit !(a >= w) }' && printf 'PROFILE WARN: %s is %s%% of allocation\n' "$2" "$mem_share"
    fi
    (( gc_max >= WARN_PAUSE )) && printf 'PROFILE WARN: longest GC pause %s ms\n' "$gc_max"
    return 0
}

if [[ $MODE == compare ]]; then
    (( $# >= 2 )) || { sed -n '3,5p' "$0" >&2; exit 2; }
    PKG=${3:-}
    prepare "$1" "$PKG" before
    prepare "$2" "$PKG" after
    printf 'Before: '; summary before "$PKG" | head -1
    printf 'After:  '; summary after "$PKG" | head -1
    for kind in cpu alloc; do
        for scope in self own; do
            [[ $scope == own && -z $PKG ]] && continue
            printf '\nBiggest changes, %s, %s (share of all samples, before -> after)\n' "$kind" "$scope"
            awk -v k="$scope" -v bt="$(head -1 "$TMP/before-$kind.dig" | cut -d' ' -f1)" -v at="$(head -1 "$TMP/after-$kind.dig" | cut -d' ' -f1)" '
                FNR == 1 { next }
                $2 != k { next }
                { w = $1; $1 = ""; $2 = ""; sub(/^ +/, ""); if (FILENAME == ARGV[1]) b[$0] = w; else a[$0] = w; seen[$0] = 1 }
                END {
                    for (f in seen) {
                        x = (bt > 0 ? 100 * b[f] / bt : 0); y = (at > 0 ? 100 * a[f] / at : 0); d = y - x
                        printf "%.2f %5.1f%% -> %5.1f%%  %s\n", (d < 0 ? -d : d), x, y, f
                    }
                }' "$TMP/before-$kind.dig" "$TMP/after-$kind.dig" | sort -rn | head -10 | cut -d' ' -f2-
        done
    done
    exit 0
fi

PKG=${2:-}
prepare "$1" "$PKG" run
summary run "$PKG"
[[ $MODE == summary ]] && exit 0
printf '\nTop CPU frames (self)\n'
show "$TMP/run-cpu.dig" self 15
if [[ -n $PKG ]]; then
    printf '\nCPU by first frame in %s (time under it, callees included)\n' "$PKG"
    show "$TMP/run-cpu.dig" own 15
fi
printf '\nTop allocating frames (self)\n'
show "$TMP/run-alloc.dig" self 15
if [[ -n $PKG ]]; then
    printf '\nAllocation by first frame in %s\n' "$PKG"
    show "$TMP/run-alloc.dig" own 15
fi
