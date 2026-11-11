# starsector-native-smoke 2.0.0 on Windows

Machine: DESKTOP-EDL72JL, Windows 10 Pro 19045, Git Bash, 16 threads. Game: `E:\Games\Starsector 0.98\Starsector 098a-RC8 - Modding` (0.98a-RC8, jre 17.0.10). JDK: corretto 17.0.12. Skill: worktree `C:\Modding\smoke-skill-test`, branch `smoke-skill-test`. P = `C:\Modding\smoketest-p`.

## Results

| Test | Result | What came out | Fix |
|---|---|---|---|
| T1 | PASS | `starsector-native-smoke 2.0.0` | |
| T2 | PASS | `SETUP OK`, 6 s. Config kept `legacy`, added `game_root`, `javac_dir`, `work_root`, `slots=3`. Tools folder holds `gamectl.exe`, `prefs-classes/SmokePrefs.class`, `CursorGuard.jar`. No licence data printed | |
| T2b | PASS | `DOCTOR OK`. `setup C:/Modding`: FAIL line with `fix:`, last line `SETUP FAIL: 1 problem(s)`, exit 1, config unchanged. Empty `SMOKE_LOCK_ROOT`: `NO_SETUP: ...`, exit 2 | |
| T2c | PASS | parser error count 0 | |
| T3 | PASS | `INIT OK`, wrote `project.sh` and `check-mod/`. Second init: `INIT OK: already set up`, no file changed | |

## Fixes

## Notes

- The old machine config held only `legacy=C:/Modding/.smoke-lock` (no `slots`, no `default`). Backup in `C:\Modding\smoke-skill-test-config.bak`.
- The installed skill on this machine was already replaced by the same 2.0.0 (identical apart from line endings), so there was no older installed copy left to check against the new config keys.
- The IE repo has `core.autocrlf=true`, so the first checkout of the worktree had CRLF scripts. setup still ran with them (tools folder sum differs). The worktree was checked out again with LF to match the installed skill; every test below ran on LF files.
- `build-tools.sh` leaves an untracked `scripts/live/gamectl.exe` in the skill copy (by design, for older project runners). Not committed.

## Not tested
