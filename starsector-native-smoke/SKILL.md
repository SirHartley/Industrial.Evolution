---
name: "starsector-native-smoke"
description: "Smoke-test a Starsector mod in the real game runtime, isolated from the user's mods and saves, with check lines and screenshots as evidence. Three modes: scripted live checks (a real campaign driven by test code, pass or fail lines, a profile), a startup probe (native UI, textures, fonts, OpenGL, asserts, then exits) and a live session (clicks, console and captures in the background without taking the user's mouse). One setup command per machine, one command per run. Use when a change or bug must be seen or exercised in game rather than in headless tests."
---

# Starsector native smoke test

Version 2.0.0 (`bash scripts/smoke.sh version`), for Starsector 0.98a-RC8.

Run a mod's code inside the real game, isolated from the user's own mods and saves, and prove the result with check lines and captured frames. A proxy that draws rectangles proves nothing about native rendering, and a passing exit code proves nothing about what a player sees.

Read `starsector-knowledge` before writing game calls. Read the `patchlib` skill before touching hook integration. Its latest version is the `patchlib.skill` download on the [PatchLib wiki](https://github.com/Lukas22041/PatchLib/wiki).

## Rules

They hold in every mode.

1. **One command per job: `scripts/smoke.sh`.** Do not write your own runner, start `java` yourself or copy the scripts into the project. When a command stops, its last line names the problem and the fix: do what it says.
2. **Ask the user for the game folder.** Take it from a path the user gave or from the stored setup. Never search the disk for a game install.
3. **Only `COMPLETE` on the last line means everything passed.** Every other last line is a failure or needs a look ([Verdicts](#verdicts)). Never report a pass from an exit code, a partial log or a summary you built yourself.
4. **Open every PNG a run names** with an image tool before you report. A passed check says nothing about what a player sees.
5. **Never stop a Java process by name or path.** The user may have their own game, an IDE or a Gradle daemon running. Stop your runs with `smoke.sh stop OWNER`.
6. **Never delete a work folder by hand** (`rm -rf`, `Remove-Item -Recurse`). After a hard kill it holds links into the real mods folder and the checkout. Use `smoke.sh clean DIR`.
7. **Never create, edit or delete lock files, and never stop another owner's game.** `smoke.sh status` shows who runs what. When something blocks you, tell the user what the status table shows.
8. **Never print, copy or edit licence data** (the game's registry preferences).
9. **The skill folder is read-only.** Nothing machine-specific or project-specific goes into it. Machine settings live in the machine folder (`smoke.sh setup`), project settings in `PROJECT/native-smoke/live/`.
10. **Several games at once only through `--shards N`.** Never start runs with a trailing `&`.
11. **Say what ran and what did not.** A report names the groups, the verdict line, the mod list and the mode, and everything that was not exercised.

## Set up the machine

Once per machine and Windows account, from Git Bash (the Bash tool on Windows is Git Bash). It is safe to repeat.

```bash
SKILL='<this skill folder>'
bash "$SKILL/scripts/smoke.sh" setup "<game folder>"     # the folder that holds starsector-core, mods and jre
```

It checks the game folder, finds a JDK 17 or newer, checks that the game has run once on this account, builds the three helper tools and stores the paths for every later run and every other session on the machine. It ends with `SETUP OK` or with `FAIL` lines that each carry a `fix:` line. `smoke.sh doctor` runs the same checks without changing anything. When a run says `NO_SETUP`, run setup. Details and every failure: [references/setup.md](references/setup.md).

## Pick a mode

| Mode | Use it for | Command | Read |
|---|---|---|---|
| **Scripted checks** | Verifying fixes and finding bugs in bulk: a real campaign driven by test code, with colonies, day skips, save and reload, pass or fail lines and a profile. No clicks. 200 checks in about 8 minutes. Also real battles and frame cost. Reach for this first once a colony, campaign or battle is involved. | `smoke.sh checks PROJECT GROUPS` | [references/scripted-checks.md](references/scripted-checks.md) |
| **Startup probe** | UI panels, widgets and hooks that can be built from real objects at `onApplicationLoad`, with asserts and screenshots. About a minute per run. | `smoke.sh probe PROJECT PROBE.java` | [references/startup-probe.md](references/startup-probe.md) |
| **Live session** | What only clicks can reach: loading an old save, the title screen, real screens and dialogs, console commands. Slow and manual, so use it last. | `scripts/live/` by hand | [references/live-session.md](references/live-session.md), [references/slots.md](references/slots.md) |

Read the mode's reference before the first run of that mode. Every mode starts a real game window that stays behind the user's windows; the user can keep working.

## Scripted checks in four steps

```bash
bash "$SKILL/scripts/smoke.sh" init "<mod project folder>"            # once per project
# write checks in PROJECT/native-smoke/live/check-mod/src/livecheck/, one group per case label in Groups.java
bash "$SKILL/scripts/smoke.sh" checks "<mod project folder>" example   # groups: a,b,c or all
# read the last line, the FAIL lines above it, the PROFILE lines, and open every PNG the CAPTURES line names
```

`init` writes `native-smoke/live/project.sh` (owner name, extra mods, how to build and stage the mod) and a starting check mod. The first run of a mod list makes a base save from a new game (about 3 minutes); later runs load it. Options: `--shards N` (N games at once), `--perf` (alone on the machine, for timings), `--fast` (Fast Rendering, for development), `--keep` (leave the game open at the end), `--newgame`, `--newbase`, `--limit S`.

## Verdicts

The last line of a `checks` run is `VERDICT: N ok, M failed, T s, results in FOLDER`. A `NEXT:` line above it says what to read.

| Verdict | Meaning | Do |
|---|---|---|
| `COMPLETE` | Every check passed, the game's own count matches, no error in the log. | Read the `PROFILE` lines, open the captures, report. |
| `CHECKS_FAILED` | At least one `LIVE_CHECK FAIL` line. A step that throws counts as a failure. | Read every FAIL line; fix the first one first. |
| `COUNT_MISMATCH` | The game counted other numbers than the log lines show. | Read the `LIVE_CHECK` lines in the kept log; never report this run as passed. |
| `NO_CHECKS` | The groups ran without a single check. | A run without checks proves nothing: add checks or fix the group. |
| `LOG_ERRORS` | Every check passed, but the game logged ERROR lines. | Fix them, or report them with the result. |
| `HELD` | A group called `c.hold()`: the game is open for you. Passed when no check failed so far. | Follow the `GAME OPEN:` lines, then stop the game as they say. |
| `TIMEOUT` | The steps did not end within the limit. | Open `stuck.png`, then `threads.txt`. Raise `--limit` only when the run still moved. |
| `LOAD_FAILED` | The base save did not load. | Open `stuck.png`; rerun with `--newbase`. |
| `FATAL` | The game crashed with its Fatal box. | Open `fatal.png`, read the ERROR lines in the kept log. |
| `CRASH` | The game process ended early. | Read the end of the kept log and `launch.log`. |
| `SHARDS_FAILED` | With `--shards`: at least one game did not end with `COMPLETE`. | Read that game's lines above. |

A run that stops before the game starts prints one line in capitals with a colon (`NO_SETUP:`, `NO_PROJECT:`, `UNKNOWN_GROUP:`, `STALE_JAR:`, `BUILD_FAILED:`, `MISSING_MOD:`, `BUSY:`, `STOPPED:` and others). The line says what to do; [references/scripted-checks.md](references/scripted-checks.md#stops-before-the-game-starts) lists them all.

## Sharing the machine

Up to `slots` games run at once (setup picks 1 to 3 from the CPU), each in a slot; a `--perf` run waits until it is alone. `smoke.sh checks` and `smoke.sh probe` take and free their slot themselves, and wait in a queue when the machine is full.

- `smoke.sh status` shows the slots, the queue and a pause. Run it before you report that something hangs.
- `smoke.sh stop OWNER` stops your runs and games (the owner is in `project.sh` and in `status`). Run it before you stop a background task, or the game keeps running.
- A run that ends with `STOPPED` was paused by the user, cancelled or lost the shell that started it. Do not restart it in a loop: read the line.
- Only a live session takes slots by hand. Read [references/slots.md](references/slots.md) first.

## Profile by default

Scripted runs and startup probes record a Java Flight Recorder profile (`profile.jfr`) unless turned off. Read the `PROFILE` lines of every run and report any `PROFILE WARN`, even when every check passed. Take every timing claim (load time, days per second, frame cost, before and after) from `--perf` runs only: a run that shared the machine says so in its `MODE` line. Steps and traps: [references/profiling.md](references/profiling.md).

## Platforms

| | Scripted checks, live session | Startup probe |
|---|---|---|
| Windows, Git Bash | Supported. The 1.x runner was used end to end on 0.98a-RC8; the 2.0.0 scripts were checked against a mock game on Linux and have not run on Windows yet. | `run-native-smoke.ps1` through `smoke.sh probe`. The runner was tested end to end on 0.98a-RC8; the slot check added in 2.0.0 has not run yet. |
| Linux, WSLg with a Linux install | Launch, mod set and log wait are written; background input is untested. | `scripts/run-native-smoke.sh`, the older merged layout: [references/linux-runner.md](references/linux-runner.md). Tested on 0.98a-RC8. |
| WSL against a Windows install | Written for it, untested. | Not supported. |
| Fast Rendering installed | Off unless `--fast`; a live session uses it when `fr.jar` is present. | Never loaded. |

## Files

| Path | Purpose |
|---|---|
| `scripts/smoke.sh` | The entry point: `setup`, `doctor`, `init`, `checks`, `probe`, `status`, `stop`, `clean`, `version`. |
| `scripts/live/run-live-checks.sh`, `run-shards.sh` | The check runner behind `smoke.sh checks`, and N of them at once. |
| `scripts/run-native-smoke.ps1`, `scripts/run-native-smoke.sh` | Startup probe runners for Windows and Linux. |
| `scripts/live/smoke-slot.sh` | Slots, queue, pause, cancel and the machine settings. |
| `scripts/live/launch-game.sh`, `game-proc.sh`, `gamectl.sh`, `wait-log.sh`, `make-modset.sh`, `find-mod.sh`, `stage-mod.sh`, `build-test-mod.sh` | The parts a live session uses by hand. |
| `scripts/live/clean-work.sh`, `build-tools.sh`, `lib.sh`, `jfr-top.sh` | Safe delete, tool build, shared functions, profile report. |
| `templates/project.sh`, `templates/live-check-mod/` | What `init` copies into a project: the project file and the check mod (`Ctx`, `Runner`, `Colony`, `Ui`, `Time`, `Groups`, `ExampleChecks`). |
| `templates/live-check-combat/` | Optional additions to the check mod: a real battle and its frame cost. |
| `templates/live-test-mod/` | Helper mod for a live session: no idling in the background, F10 opens the console. |
| `templates/NativeSmokeSupport.java`, `SmokePrefs.java`, `CursorGuard.java` | Test-only helpers on the game's classpath: probe support, registry prefs served from memory, and the agent that keeps the game from moving the real cursor. |
| `examples/` | Two startup probes. |
| `references/` | `setup.md`, `scripted-checks.md`, `startup-probe.md`, `live-session.md`, `slots.md`, `profiling.md`, `linux-runner.md`. `live-game.md` only maps its old sections to these files. |

## Finish

- Keep the evidence PNGs and show the changed UI to the user.
- Keep the check mod in the project (`native-smoke/live/`) so the checks stay as a regression suite.
- Remove work folders you no longer need with `smoke.sh clean DIR`.
- Never ship a probe, the check mod, test-only reflection, `System.exit()` or synthetic state in the mod.
- A native smoke supplements the project's normal tests and compile gate; it does not replace them.
