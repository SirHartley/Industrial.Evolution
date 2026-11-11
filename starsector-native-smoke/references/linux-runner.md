# Linux startup runner

`scripts/run-native-smoke.sh` is the older startup-probe runner for a Linux install (native or under WSLg). `starsector-combat-smoke` builds on it. It uses the **merged layout**: the project's classes and the probe go into one test mod whose plugin is the probe, so the mod's own `ModPlugin` never runs, and the probe, the project classes and `NativeSmokeSupport` all sit on the game's classpath, outside the security gate. Probes for this runner may use reflection directly. Everything in [startup-probe.md](startup-probe.md) under "Write a probe" applies, except the gate rule and the mod's own plugin running first. This runner takes no slot by itself: on a machine other sessions use, wrap it in `scripts/live/smoke-slot.sh run OWNER -- bash ...` ([slots.md](slots.md)).

Tested with Starsector 0.98a-RC8, PatchLib 1.0.3 and the game's Linux Java 17 runtime under WSLg (`DISPLAY=:0`).

## Run

Supply a known **Linux** game root. Never search the user's disk for one. A Windows install's jars alone are not enough: you also need Linux native libraries, a working display and existing Linux license preferences (`~/.java/.userPrefs/com/fs/starfarer/prefs.xml`).

```bash
SKILL='<this skill folder>'
SMOKE_RESOLUTION=1280x800 \
SMOKE_CLASSPATH="$HOME/starsector/mods/MagicLib-1.5.6/jars/MagicLib.jar" \
bash "$SKILL/scripts/run-native-smoke.sh" \
  ~/starsector  /path/to/ModProject  /path/to/ModProject/native-smoke/MyModNativeSmoke.java  /tmp/mymod-smoke-01
```

The arguments are the game root, the project root, the probe source and a fresh output directory (use a new one every run).

Optional environment:

| Variable | Default | Meaning |
|---|---|---|
| `SMOKE_SRC_DIRS` | `src` | Colon-separated source directories, absolute or relative to the project. All are compiled with `--release 17`. |
| `SMOKE_RESOURCE_DIRS` | whichever of `data graphics sounds` exist | Linked into the test mod so the mod's CSVs, JSON, rules and sprites load. |
| `SMOKE_MOD_ID` | project `mod_info.json` id | Test mod id. Keep the real id so `isModEnabled` and per-mod `loadCSV` behave as shipped. |
| `SMOKE_EXTRA_MODS` | none | Mods in `GAME/mods` to link and enable, for real runtime dependencies: `mod_info.json` ids (highest version wins) or folder names, such as `MagicLib lw_lazylib`. |
| `SMOKE_CLASSPATH` | none | Extra jars for compile and runtime, for optional APIs referenced by the sources. |
| `SMOKE_PROBE_SOURCES` | none | Colon-separated extra test-only Java files compiled next to the probe, such as a shared helper. |
| `SMOKE_PATCHLIB` | `1` | Links and enables PatchLib (id `patchlib`). Set to `0` for mods without patches. |
| `SMOKE_TIMEOUT` | `240` | Seconds before the JVM is killed. |
| `SMOKE_RESOLUTION` | `1024x768` | Windowed size. It must fit the UI under test; the Character tab is 1104x734, so use at least 1280x800. |

The runner requires `javac` (17+), `jar` and GNU `timeout`. It checks every prerequisite before copying preferences or launching. It never enables mods in the user's installation, loads a save, installs the tested mod or touches a running game. The copied preferences are deleted on exit; never publish them.

## Pitfalls specific to this runner

- **The mod's own plugin does not run.** The probe replaces it. Call the setup the mod's `onApplicationLoad` does from the probe when the code under test needs it.
- **Libraries that read mod-root files.** The runner links only `data`, `graphics` and `sounds` into the test mod. Enabling LunaLib through `SMOKE_EXTRA_MODS` crashed startup because its version checker loads the tested mod's `.version` file (`Error loading [kol.version] resource`). Put such a library on `SMOKE_CLASSPATH` only, unless the probe needs its plugin to run.
- **Mod jars that bundle sources.** Some libraries (MagicLib, for one) ship `.java` files inside their jar. This runner does not guard against it; when javac compiles those instead of using the classes, add `-sourcepath` pointing at an empty folder plus `-implicit:none` to its `javac` calls.
- **Gate escapes.** The mod's classes run outside the security gate here, so a blocked-class bug in mod code passes this runner and fails for players.
- **Combat setup** (see the Fleet Formations example):
  - Set the sector, give the engine a real `BattleCreationContext` and the return state `"Campaign State"`, and create a real shuttle via `createEmptyVariant("shuttlepod_xfer", ...)`.
  - `spawnFleetMember()` queues ships through `CombatEngine.addObject()`. Flush with `executeAdds()` before asserting.
  - This setup suits static combat UI only. To advance the engine (ships fire, projectiles fly, damage lands), use the `starsector-combat-smoke` skill: the `int` `CombatFleetManager` constructor and the paused engine above fail there.
