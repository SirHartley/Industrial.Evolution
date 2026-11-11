#!/usr/bin/env bash
# Builds what the runners need, once per machine and per version of the sources, into the machine folder
# (%LOCALAPPDATA%\starsector-smoke\tools\<sum>): gamectl.exe, the prefs helper (SmokePrefs) and the cursor guard agent.
# Parts that are built already are skipped, so every launch may call this. The skill folder may be read-only: the
# only thing written there, when it can be, is a copy of gamectl.exe next to this script for older project runners.
#   build-tools.sh [gamectl] [prefs] [guard]     default: all three; prints the tools folder on the last line
# gamectl needs the C# compiler that ships with Windows (.NET Framework 4); prefs and guard need a JDK 17+
# (SMOKE_JAVAC_DIR, or the javac_dir setting stored by smoke.sh setup). Exit 1 names the part that failed.
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
smoke_tools || exit 1
TOOLS=$REPLY
TEMPLATES="$HERE/../../templates"
(( $# )) || set -- gamectl prefs guard
if command -v cygpath >/dev/null 2>&1; then
    winpath() { cygpath -w -- "$1"; }
elif command -v wslpath >/dev/null 2>&1 && [[ $TOOLS == /mnt/* ]]; then
    winpath() { wslpath -w -- "$1"; }
else
    winpath() { printf '%s' "$1"; }
fi
failed=0
for part in "$@"; do
    case $part in
        gamectl)
            if [[ -x $TOOLS/gamectl.exe ]]; then
                [[ -e $HERE/gamectl.exe ]] || cp -- "$TOOLS/gamectl.exe" "$HERE/gamectl.exe" 2>/dev/null || true
                continue
            fi
            csc=
            for c in /c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe /mnt/c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe; do
                [[ -x $c ]] && { csc=$c; break; }
            done
            [[ -n $csc ]] || { echo 'TOOLS_FAILED gamectl: csc.exe not found (gamectl is Windows only)' >&2; failed=1; continue; }
            # a temporary name first: another slot may build or start it at the same moment
            tmp="gamectl.$$.exe"
            if (cd -- "$TOOLS" && "$csc" -nologo -optimize -r:System.Drawing.dll "-out:$tmp" "$(winpath "$HERE/gamectl.cs")" >&2); then
                mv -n -- "$TOOLS/$tmp" "$TOOLS/gamectl.exe"; rm -f -- "$TOOLS/$tmp"
                cp -- "$TOOLS/gamectl.exe" "$HERE/gamectl.exe" 2>/dev/null || true
                echo "Built $TOOLS/gamectl.exe" >&2
            else
                rm -f -- "$TOOLS/$tmp"
                echo 'TOOLS_FAILED gamectl: csc.exe reported errors' >&2; failed=1
            fi
            ;;
        prefs)
            [[ -f $TOOLS/prefs-classes/SmokePrefs.class ]] && continue
            smoke_jdk || { echo 'TOOLS_FAILED prefs: no JDK. Run smoke.sh setup --jdk DIR, or set SMOKE_JAVAC_DIR' >&2; failed=1; continue; }
            tmp="$TOOLS/prefs-classes.$$"; rm -rf -- "$tmp"; mkdir -p "$tmp"
            if javac --release 17 -nowarn -d "$(winpath "$tmp")" "$(winpath "$TEMPLATES/SmokePrefs.java")" >&2; then
                mv -T -- "$tmp" "$TOOLS/prefs-classes" 2>/dev/null || rm -rf -- "$tmp"
            else
                rm -rf -- "$tmp"; echo 'TOOLS_FAILED prefs: javac reported errors' >&2; failed=1
            fi
            ;;
        guard)
            [[ -f $TOOLS/CursorGuard.jar ]] && continue
            smoke_jdk && command -v jar >/dev/null 2>&1 || { echo 'TOOLS_FAILED guard: no JDK. Run smoke.sh setup --jdk DIR, or set SMOKE_JAVAC_DIR' >&2; failed=1; continue; }
            tmp="$TOOLS/guard.$$"; rm -rf -- "$tmp"; mkdir -p "$tmp/classes"
            if javac -nowarn -Xlint:-options -source 17 -target 17 --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
                    -d "$(winpath "$tmp/classes")" "$(winpath "$TEMPLATES/CursorGuard.java")" >&2 &&
                printf 'Premain-Class: CursorGuard\n' > "$tmp/manifest.txt" &&
                jar cfm "$(winpath "$tmp/CursorGuard.jar")" "$(winpath "$tmp/manifest.txt")" -C "$(winpath "$tmp/classes")" . >&2; then
                mv -n -- "$tmp/CursorGuard.jar" "$TOOLS/CursorGuard.jar"
            else
                echo 'TOOLS_FAILED guard: javac or jar reported errors' >&2; failed=1
            fi
            rm -rf -- "$tmp"
            ;;
        *) echo "Unknown part $part; parts: gamectl prefs guard" >&2; exit 2 ;;
    esac
done
printf '%s\n' "$TOOLS"
exit $failed
