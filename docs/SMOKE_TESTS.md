# Smoke tests

How IE is run inside the real game to see or exercise a change: setup on the user's machine, the `native-smoke/` wrapper and probes, and how results are read and reported. Read it before writing a probe or starting a run. It does not govern the compile gate ([CLAUDE.md](../CLAUDE.md#required-compile-gate)), the release jar ([RELEASE.md](RELEASE.md)) or how the runner works inside (the `starsector-native-smoke` skill owns that).

A smoke test supplements the compile gate and is not a merge requirement. When one ran, the PR records it.

## When to use

| Need | Mode |
|---|---|
| See a UI change: panels, tooltips, buttons, intel layout built from real objects | Startup probe |
| Show that a PatchLib patch ([ARCHITECTURE.md](ARCHITECTURE.md#patches)) is discovered and installed | Startup probe, then the PatchLib lines in the log |
| Anything that needs a running campaign: loaded or old saves, intel, dialogs, markets, days passing, console commands | Live session |

Both modes come from the `starsector-native-smoke` skill. Runs happen on the user's Windows machine only; cloud sessions have no game. A startup run opens a game window for up to a minute, so tell the user before starting one.

## Setup

- **Game:** the modding install `E:\Games\Starsector 0.98\Starsector 098a-RC8 - Modding` (it holds `starsector-core`, `mods` and `jre`). It must have run once, so its licence is in the registry. Runs never change it: mods are linked into an isolated folder per run.
- **Dependencies:** every id in `mod_info.json` `dependencies` (LazyLib, MagicLib, LunaLib, GraphicsLib, PatchLib) installed in that `mods` folder. Folder names do not matter and folders may be symbolic links: the wrapper finds each one by its `mod_info.json` id.
- **JDK 17** with `javac`, `jar` and `jdeps`, on `PATH` or in `SMOKE_JAVAC_DIR`. The compile gate refuses other versions.
- **Skill folder:** the wrapper needs the `starsector-native-smoke` skill folder (`-Skill` or `SMOKE_SKILL`). Claude sees it when the skill loads; for a manual run, unpack the skill anywhere.

## Run

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File native-smoke\run.ps1 -Skill <skill folder>
```

`native-smoke/run.ps1`:
1. runs the compile gate (`ReleaseBuild.java compile`) and takes the checked jar it reports;
2. finds each dependency folder in the game's `mods` folder by id and fails when one is missing or several folders share an id;
3. calls the skill's `scripts/run-native-smoke.ps1` with that jar, those folders and a new output folder `out\native-smoke\<timestamp>` (gitignored), and exits with its code.

| Option | Default | Meaning |
|---|---|---|
| `-Probe` | `IndEvoNativeSmoke.java` | Probe file, in `native-smoke/` or a path. |
| `-Game` | `SMOKE_GAME`, else the modding install above | Game root, without a trailing backslash. |
| `-Skill` | `SMOKE_SKILL` | `starsector-native-smoke` skill folder. |
| `-Out` | `out\native-smoke\<timestamp>` | Output folder; must not exist. |
| `-ExtraMods` | found by id | `;`-separated dependency folder names, when several folders share an id. |

`SMOKE_RESOLUTION` defaults to `1280x800`. Other `SMOKE_*` variables pass through to the runner (see the skill).

## Probes

- Probes live in `native-smoke/` in the default package, never under `jars/src`. The builder compiles only `jars/src` and packaging is an allowlist ([RELEASE.md](RELEASE.md#what-ships)), so they never ship.
- `native-smoke/IndEvoNativeSmoke.java` is the boot check: IE is enabled, `ModPlugin.onApplicationLoad` ran before the probe (the `IndEvo_simulator` effect from `SpecialItemEffectsRepo` is in `ItemEffectsRepo.ITEM_EFFECTS`), and a tooltip with the Simulation Engine icon renders (`boot.png`). Run it first after a setup change.
- A feature probe is `native-smoke/<Feature>NativeSmoke.java`. Follow the skill's "Write a probe" rules: probes run behind the game's security classloader, so files and reflection go through `NativeSmokeSupport`.
- At probe time `ModPlugin.onApplicationLoad` has run (`PlayerSettings.install`, GraphicsLib setup, item effects). `onGameLoad` has not: no listeners or scripts from `setListenersIfNeeded`/`setScriptsIfNeeded`, no academy market, and `Global.getCurrentState()` is not `CAMPAIGN`. A probe that needs those calls the code directly and says so, or the check becomes a live session.

## Results

- The output folder holds `launcher.log` (the `NATIVE_SMOKE` lines), the game log in `logs/` and the PNGs. Open every PNG; the exit code alone proves nothing about what a player sees.
- PatchLib's `Discovered patch` and `Installed a ... patch site` lines show which IE patches loaded. Discovery is not installation, and a target first loaded after startup may not log an install.
- Delete output folders after inspection. Never `Remove-Item -Recurse` one that still holds junctions (only a killed run leaves them).
- The PR records the probe, the command, the result, the PNG names and what was not exercised.

## Live sessions

Use the skill's live mode (`references/live-game.md`) against the modding install. The mod set links the repository folder as it is, so IE loads `jars/IndEvo.jar` (the `mod_info.json` entry, gitignored): build the IDE artifact or copy the checked jar from `out/release-build-*` there first. Console commands for a session go in the skill's test mod, never in IE.
