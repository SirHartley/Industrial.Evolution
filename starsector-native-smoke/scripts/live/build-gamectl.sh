#!/usr/bin/env bash
# Builds gamectl.exe (Windows only) into the machine's tools folder and prints its path. Kept for older notes:
# the runners build it themselves, and gamectl.sh runs it.
set -euo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
printf '%s/gamectl.exe\n' "$(bash "$HERE/build-tools.sh" gamectl | tail -n 1)"
