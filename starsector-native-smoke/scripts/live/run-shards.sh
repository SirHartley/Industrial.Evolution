#!/usr/bin/env bash
# Runs a project's groups in N games at once, each in its own slot and work folder, and adds the verdicts up.
#   run-shards.sh N PROJECT_DIR GROUP[,GROUP...]
# The groups are dealt out in order: group 1 to game 1, group 2 to game 2 and so on. Each game is one
# run-live-checks.sh --project run, so every option of that script applies (FAST, LIMIT, JFR, ...), and each prints to
# WORK/shard-I.log. The first run makes the base save, the others wait for it and copy it.
# Not for timings: the games share the machine, so PERF=1 is refused. Stop all of them with smoke-slot.sh cancel OWNER.
# Exit 0 only when every game ends with COMPLETE.
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
smoke_root || exit 2
(( $# == 3 )) && [[ $1 =~ ^[1-9]$ ]] || { sed -n '2,3p' "$0" >&2; exit 2; }
N=$1; PROJECT=$2; GROUP=$3
[[ ${PERF:-0} != 1 ]] || { echo "PERF=1 runs one game alone; shards share the machine. Drop one of the two" >&2; exit 2; }
[[ -z ${SMOKE_SLOT:-} ]] || { echo "SMOKE_SLOT is set: shards take their own slots. Unset it, or run without shards" >&2; exit 2; }
[[ ${KEEP:-0} != 1 ]] || { echo "KEEP=1 leaves one game open; run that group without shards" >&2; exit 2; }
smoke_cfg slots; slots=${SMOKE_SLOTS:-${REPLY:-3}}
(( N <= slots )) || { echo "This machine runs $slots games at once (smoke-slot.sh config slots); using $slots shards"; N=$slots; }

if [[ $GROUP == all ]]; then
    file=$(find "$PROJECT/native-smoke/live" -name Groups.java 2>/dev/null | head -n 1)
    GROUP=$(grep -o -E 'case +"[^"]+" *:' "${file:-/dev/null}" 2>/dev/null | sed -E 's/.*"([^"]+)".*/\1/' | grep -vx battle | tr '\n' ',')
    GROUP=${GROUP%,}
    [[ -n $GROUP ]] || { echo "UNKNOWN_GROUP: no case \"name\" labels in a Groups.java under $PROJECT/native-smoke/live; name the groups" >&2; exit 2; }
fi
IFS=, read -r -a groups <<< "$GROUP"
(( N <= ${#groups[@]} )) || N=${#groups[@]}
parts=()
for i in "${!groups[@]}"; do
    k=$(( i % N ))
    parts[k]="${parts[k]:+${parts[k]},}${groups[i]}"
done

OUT=$(mktemp -d)
pids=()
stop_all() { for p in "${pids[@]}"; do kill "$p" 2>/dev/null; done; wait 2>/dev/null; }
trap 'stop_all; rm -rf -- "$OUT"; exit 143' TERM
trap 'stop_all; rm -rf -- "$OUT"; exit 130' INT
for k in "${!parts[@]}"; do
    echo "shard $(( k + 1 )) of $N: ${parts[k]}"
    bash "$HERE/run-live-checks.sh" --project "$PROJECT" "${parts[k]}" > "$OUT/shard-$(( k + 1 )).log" 2>&1 &
    pids+=($!)
done
ok=0; bad=0; failed=0
for k in "${!parts[@]}"; do
    wait "${pids[k]}"; code=$?
    log="$OUT/shard-$(( k + 1 )).log"
    last=$(grep -E '^[A-Z_]+: [0-9]+ ok, [0-9]+ failed' "$log" | tail -n 1)
    echo "--- shard $(( k + 1 )) (${parts[k]}), exit $code"
    # what a single run prints, without the progress lines
    grep -E '^(LIVE_CHECK (FAIL|group )|MODE |PROFILE|--- log errors|NEXT:|CAPTURES|GAME OPEN|STOPPED|[A-Z_]+: )' "$log" | cut -c1-200
    if [[ $last =~ ^([A-Z_]+):\ ([0-9]+)\ ok,\ ([0-9]+)\ failed ]]; then
        ok=$(( ok + BASH_REMATCH[2] )); bad=$(( bad + BASH_REMATCH[3] ))
        [[ ${BASH_REMATCH[1]} == COMPLETE && $code == 0 ]] || failed=$(( failed + 1 ))
        dir=${last##*results in }
        [[ -d $dir ]] && cp -- "$log" "$dir/runner.log" 2>/dev/null
    else
        # no verdict line: the run ended before the game ran (setup, build, slot)
        failed=$(( failed + 1 ))
        tail -n 8 "$log"
    fi
done
rm -rf -- "$OUT"
if (( failed == 0 )); then
    echo "COMPLETE: $ok ok, $bad failed, $SECONDS s, $N games"
else
    echo "SHARDS_FAILED: $ok ok, $bad failed, $SECONDS s, $failed of $N games did not end with COMPLETE (their lines are above)"
fi
(( failed == 0 ))
