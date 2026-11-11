# starsector-native-smoke 2.0.0 on Windows

Machine: DESKTOP-EDL72JL, Windows 10 Pro 19045, Git Bash, 16 threads. Game: `E:\Games\Starsector 0.98\Starsector 098a-RC8 - Modding` (0.98a-RC8, jre 17.0.10). JDK: corretto 17.0.12. Skill: worktree `C:\Modding\smoke-skill-test`, branch `smoke-skill-test`, bumped to 2.0.1 with the first fix. P = `C:\Modding\smoketest-p` (id `smoketest`, no jars). Runs went through the Bash tool of the Claude desktop app (Code tab).

## Results

| Test | Result | What came out | Fix |
|---|---|---|---|
| T1 | PASS | `starsector-native-smoke 2.0.0`, after the bump `2.0.1` | |
| T2 | PASS | `SETUP OK`, 6 s. Config kept `legacy`, added `game_root`, `javac_dir`, `work_root`, `slots=3`. Tools folder holds `gamectl.exe`, `prefs-classes/SmokePrefs.class`, `CursorGuard.jar`. No licence data printed. After fix 1, `work_root` is the real package path and a `note` line says so | `8a14bf8a` |
| T2b | PASS | `DOCTOR OK`. `setup C:/Modding`: FAIL line with `fix:`, last line `SETUP FAIL: 1 problem(s)`, exit 1, config unchanged. Empty `SMOKE_LOCK_ROOT`: `NO_SETUP: ...`, exit 2 | |
| T2c | PASS | parser error count 0 | |
| T3 | PASS | `INIT OK`, wrote `project.sh` and `check-mod/`. Second init: `INIT OK: already set up`, no file changed | |
| T4 | PASS after fix | First run: the game booted with no mods (`Running vanilla game with no mods`), made a vanilla base save and waited for the time limit; stopped by hand. Cause in Fixes 1. After the fix: `COMPLETE: 3 ok, 0 failed, 141 s`, exit 0, results.txt starts with two RUN lines (`base save new`), PROFILE line printed, results in `runs/` | `8a14bf8a`, `1a991743` |
| T5 | PASS | `COMPLETE ... 63 s` (141 s before), `RUN base save reused`, `status` all slots free, no reparse points left under `run/` (`dir /AL /S`) | |
| T6 | PASS | `check-campaign.png` shows the paused campaign, CAPTURES line printed, no ack failure. Step 2 started 2.0 s after `LIVE_CHECK shot`, step 3 1.9 s after `LIVE_CHECK key ESC_1`. The ESC did not open the game menu | |
| T7 | PASS | `CHECKS_FAILED: 1 ok, 2 failed`, exit 1. `FAIL: fails / x (got 1, want 2)` and `FAIL: fails / step 2 threw java.lang.IllegalStateException: boom`; game counted `passed=1 failed=2` | |
| T8 | PASS | Empty group: `NO_CHECKS`, exit 1. `nosuch`: `UNKNOWN_GROUP: nosuch. Groups in Groups.java: example acks fails ...`, exit 2, no game, slots free | |
| T9 | PASS | `HELD: 1 ok`, exit 0, two `GAME OPEN:` lines, slot shown live. `gamectl bshot` with the printed PID saved a campaign frame. The printed stop command stopped the game and freed the slot. The run's junctions stay in `run/mods` until the next run or `clean` | |
| T10 | PASS | `--keep`: `COMPLETE: 3 ok`, `GAME OPEN:` lines, game open; stopped as printed, slot free. No PROFILE line while the game stays open | |
| T11 | PASS | `--limit 90`: `TIMEOUT`, exit 1. stuck.png shows the campaign speeding up time, threads.txt holds 51 threads (main in `SensorGhostManager.spawnGhost`), profile.jfr 3.8 MB | |
| T12 | PASS | `--newgame`: `COMPLETE: 3 ok, 102 s`, `RUN base save none (new game)` | |
| T13 | PASS | `--shards 2` example,colony: two games in slot 1 and 2, slot 2 `base save copied`, last line `COMPLETE: 7 ok, 0 failed, 80 s, 2 games` | |
| T14 | PASS after fix | Before: the stopped run printed nothing and exited 143 (docs say STOPPED and 4). After: last line `STOPPED: this run got a stop signal (smoke.sh stop smoketest.checks, ...)`, exit 4, game gone, slot free, no junctions | `abad49ce` |
| T15 | PASS | With `DEPS=(lw_lazylib)` and a held game stopped as printed, five junctions left (LazyLib among them). `clean <work_root>/smoketest`: `Removed ...`; LazyLib still holds its 13 files, P intact. `clean` on the game folder and on P: `REFUSED`, exit 1 | |
| T16 | PASS | `--perf`: `MODE perf, other games during the run: 0`, COMPLETE | |
| T17 | PASS | Ran as shard 2 of T13: `listener got the planet`, `market is player owned`, `spaceport exists`, `size (got 4, want 4)` | |

## Fixes

1. `8a14bf8a` store real work folder path under msix redirection. The Claude desktop app is an MSIX package: `%LOCALAPPDATA%\starsector-smoke` written from inside it lands in `%LOCALAPPDATA%\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Local\starsector-smoke`. Bash resolves junctions itself and sees through, but a junction whose target is the redirected path does not resolve for Java or cmd (`File Not Found`), so the game found no mods. `lib.sh` gets `smoke_real_dir` (finds the package path with a marker file), setup stores the real `work_root` and tests a junction in it through cmd (FAIL with `--work` fix when it does not resolve). Docs: `0fdff704`.
2. `1a991743` stop when the game loads no mods. The runner went on after 120 s without `LIVE_CHECK application loaded`, made a base save without the mods and waited out the 600 s limit. Now `MODS_NOT_LOADED` (log says no mods) or `CHECK_MOD_NOT_LOADED` (with `not-loaded.png`), both in the stops table.
3. `abad49ce` print stopped and exit 4 on stop signal. `smoke.sh stop` sends TERM to the runner, whose trap exited 143 silently.

## Notes

- The old machine config held only `legacy=C:/Modding/.smoke-lock` (no `slots`, no `default`). Backup in `C:\Modding\smoke-skill-test-config.bak`. Both the config and the old work folder live in the redirected package folder, so only processes inside the Claude desktop app see them (see open items).
- The installed skill on this machine was already replaced by the same 2.0.0 (identical apart from line endings), so there was no older installed copy left to check against the new config keys.
- The IE repo has `core.autocrlf=true`, so the first checkout of the worktree had CRLF scripts. setup still ran with them (tools folder sum differs). The worktree was checked out again with LF to match the installed skill; every later test ran on LF files.
- `build-tools.sh` leaves an untracked `scripts/live/gamectl.exe` in the skill copy (by design, for older project runners). Not committed.
- `launch.log` holds the game's whole stdout (about 5 MB per run, the same lines as `starsector.log`).

## Open items

- The lock folder (`%LOCALAPPDATA%\starsector-smoke`) is redirected the same way: a Claude Code CLI in a normal terminal sees another, empty lock folder and would not see the desktop app's slots. Not changed; every session on this PC runs in the desktop app.

## Not tested
