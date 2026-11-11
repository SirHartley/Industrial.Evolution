# Startup probe

A probe builds UI panels, widgets and hooks from real objects inside `onApplicationLoad`, asserts on them, renders them, saves screenshots and exits. It needs no save and no clicks and takes about a minute. It cannot do anything that needs a running campaign: use [scripted checks](scripted-checks.md) for that.

This file covers the Windows runner. The Linux runner uses an older layout: [linux-runner.md](linux-runner.md).

## Run

```bash
bash "$SKILL/scripts/smoke.sh" probe "<mod project folder>" "<mod project folder>/native-smoke/MyModNativeSmoke.java"
```

`smoke.sh probe` takes a slot, uses the game folder and JDK that `smoke.sh setup` stored ([setup.md](setup.md)), picks a new output folder (`work_root/OWNER/probe-<time>-slotN`, printed on the first line) and calls `scripts/run-native-smoke.ps1`. The runner's options are environment variables. From Git Bash, put them in front of the command, with paths in `C:/...` form:

```bash
SMOKE_MOD_JAR='C:/Mods/MyMod/out/compile/MyMod.jar' SMOKE_EXTRA_MODS='MagicLib lunalib' SMOKE_RESOLUTION=1280x800 \
  bash "$SKILL/scripts/smoke.sh" probe "C:/Mods/MyMod" "C:/Mods/MyMod/native-smoke/MyModNativeSmoke.java"
```

The long form, for a perf slot (`run mymod.probe --perf -- ...`) or an output folder of your own. Every path goes in Windows form, because `smoke-slot.sh run` passes its command on unchanged. `{slot}` becomes the slot number:

```bash
bash "$SKILL/scripts/live/smoke-slot.sh" run mymod.probe -- powershell -NoProfile -ExecutionPolicy Bypass \
  -File "$(cygpath -w "$SKILL/scripts/run-native-smoke.ps1")" 'D:\Starsector' 'D:\Mods\MyMod' 'D:\Mods\MyMod\native-smoke\MyModNativeSmoke.java' 'D:\smoke-work\mymod-probe-01-slot{slot}'
```

The run passed when the runner exits 0 after `NATIVE_SMOKE COMPLETE`, and you have opened every PNG in the output folder. Exit 124 is a timeout, 125 a crash; `NATIVE_SMOKE FAIL` lines name failed asserts.

The runner's own arguments are the game root (the folder holding `starsector-core`, `mods` and `jre`), the mod's root folder (the one holding `mod_info.json`), the probe source and a fresh output directory (a new one every run). `smoke.sh probe` fills in the game root and the output directory. Called by hand, pass paths without a trailing backslash: Windows PowerShell quotes an argument with spaces as `"...\"`, and the backslash then escapes the closing quote. In PowerShell, `SMOKE_*` variables stay set for the rest of the session; clear them with `Remove-Item Env:SMOKE_*` before a run for another mod.

**Build the mod jar with the project's compile gate.** When the project has one, such as the house-style builder's `java jars/src/<mod>/tools/ReleaseBuild.java compile`, run it first and pass the checked jar it reports as `SMOKE_MOD_JAR`. It compiles exactly the shipped source set (IDE excludes honoured) and runs the project's own checks. Never use a command that replaces the committed release jar, such as the builder's `build`. When the build config holds a game path for another machine, override it for the build (`-P`, an environment variable) or restore the file afterwards (`git checkout -- <file>`); never commit the change. Without a compile gate, the runner compiles `SMOKE_SRC_DIRS` with plain `javac`: no excludes, no Kotlin.

Optional environment:

| Variable | Default | Meaning |
|---|---|---|
| `SMOKE_MOD_JAR` | none | Prebuilt mod jar. It replaces the `mod_info.json` `jars` entry with the same file name, else the first entry. |
| `SMOKE_SRC_DIRS` | `src` | Without `SMOKE_MOD_JAR`: `;`-separated source directories, absolute or relative to the project, compiled with `--release 17` into the first `jars` entry. |
| `SMOKE_PROBE_SOURCES` | none | `;`-separated extra test-only Java files compiled into the companion mod with the probe, behind the gate. |
| `SMOKE_SUPPORT_SOURCES` | none | `;`-separated test-only helpers compiled with `NativeSmokeSupport` on the game's classpath, outside the gate, so they may use reflection and files. `starsector-combat-smoke` passes `CombatSmokeSupport` here. |
| `SMOKE_EXTRA_MODS` | none | Mods in `GAME\mods` to link and enable, each a `mod_info.json` id (highest version wins) or a folder name. Space-separated, or `;`-separated when a name holds spaces. Missing dependencies of the mod under test are found by id without being listed. |
| `SMOKE_CLASSPATH` | none | `;`-separated extra jars for compiling only. Never on the runtime classpath. |
| `SMOKE_PATCHLIB` | `1` | Links and enables PatchLib (id `patchlib`). Set to `0` for mods without patches. |
| `SMOKE_TIMEOUT` | `240` | Seconds before the JVM is killed. |
| `SMOKE_HEAP` | `3g` | Heap size for `-Xms` and `-Xmx`. |
| `SMOKE_RESOLUTION` | `1024x768` | Windowed size. It must fit the UI under test; the Character tab is 1104x734, so use at least 1280x800. |
| `SMOKE_JFR` | `1` | Records `<out>\profile.jfr` with Java Flight Recorder. `0` turns it off. |
| `SMOKE_CURSOR_GUARD` | `1` | Loads `CursorGuard`, so the game never moves the real cursor. `0` turns it off. |
| `SMOKE_JAVAC_DIR` | the JDK `smoke.sh setup` stored, then `PATH`, then `GAME\jdk-17\bin` | Folder with `javac.exe`, `jar.exe` and `jdeps.exe` (JDK 17+). Without `jdeps.exe` the gate preflight is skipped with a warning. |

Requirements and behaviour:
- The real game must have run once, so its preferences exist in `HKCU\Software\JavaSoft\Prefs\com\fs\starfarer`. The runner exports them to a temp XML, serves them from memory through `SmokePrefs` and deletes the XML afterwards. Nothing is written back to the registry. Never print, patch or replace licence data.
- The game's `jre\bin\java.exe` runs with `starsector-core` as the working directory, the vanilla jars in launcher order and the `native\windows` libraries. A real game window opens for the run, so leave the machine alone meanwhile. The runner prints that Java's version and warns when a launch file in the install names another Java; ask the user which one they play on before treating results as theirs.
- A probe takes about a minute of one slot. Its load time and profile are only comparable when no other game ran; take a perf slot (`smoke-slot.sh run OWNER --perf -- powershell ...`, the long form above) when load time is the result.
- A crash shows a modal "Fatal" box that keeps the process alive. The runner watches the game log for `ERROR com.fs.starfarer.combat.CombatMain`, then stops the process it started and exits 125. A timeout exits 124.
- Every `javac` call gets an empty `-sourcepath` and `-implicit:none`, because some mod jars (MagicLib) bundle `.java` files that javac would otherwise compile instead of using the jar's classes.
- Junctions are removed at the end without touching their targets. Never `Remove-Item -Recurse` or `rm -rf` an output directory: after a hard kill it still holds junctions. Delete it with `smoke.sh clean DIR`.
- The runner refuses to start without a slot (`NO_SLOT`). `smoke.sh probe` and `smoke-slot.sh run` provide one; `SMOKE_LOCK=0` turns the check off, only on a machine where no other session starts games.
- Results land in `<out>\launcher.log` (stdout, then stderr), the game's own log in `<out>\logs`, and the PNGs in `<out>`. On failure the runner repeats the `NATIVE_SMOKE FAIL` and gate lines from the log.

## How a run is put together

- **Isolated mods folder.** The game starts with `paths.mods` pointing at `<out>\mods`, with its own saves, logs, screenshots and preferences. Nothing in the game install is enabled, installed or changed.
- **The mod under test loads as itself.** The runner stages it in `<out>\mods\<project folder>`: top-level folders are linked with junctions, top-level files (`mod_info.json`, the `.version` file) are copied, and the jars listed in `mod_info.json` are built fresh. Its own `ModPlugin`, `.version` file and dependencies work as shipped. Folders named `jars out build lib libs docs src gradle native-smoke`, folders starting with `.`, and folders that hold `mod_info.json` jar entries are not linked.
- **Dependencies** are linked from `GAME\mods`: PatchLib by default, the rest through `SMOKE_EXTRA_MODS`, and any `mod_info.json` dependency still missing is found by id. The runner fails before launching when a dependency is not installed.
- **The probe runs in a companion mod** named `~ Native Smoke` (id `native_smoke`), built from the probe sources. The game calls `onApplicationLoad` for the core plugin, then for each mod sorted by `mod_info` `name` with a case-sensitive `String.compareTo` (`ModManager`, `ResourceLoaderState`). `~` sorts after every letter, so the probe starts after every other mod's `onApplicationLoad`; the runner fails when a mod name would sort later.
- **The real cursor stays put.** The game centres the operating system's cursor through LWJGL at battle start, even behind other windows. A test-only agent (`templates/CursorGuard.java`) drops that move; LWJGL's own position is still set. See [The real cursor](live-session.md#the-real-cursor).
- **The security gate applies to the probe.** All mod jars, the probe's included, load through the game's script classloader, which throws `SecurityException` for `java.io` (apart from streams and readers), `java.nio.file.File*` (`Files` included), `java.lang.reflect` (apart from `InvocationTargetException`), `java.util.prefs` and `javax.script`. `NativeSmokeSupport` and `SmokePrefs` sit on the game's own classpath, outside the gate, and do the file access and reflection for the probe. Only the vanilla game jars join them there (Fast Rendering's jars stay off), so the mod runs behind the gate exactly as shipped.

## Write a probe

1. Put the probe in the **default package** in `native-smoke/` at the project root: outside the shipped sources and outside the compile gate's source set. It must never reach the mod jar.
2. Extend `BaseModPlugin` and override `onApplicationLoad()` with `NativeSmokeSupport.run(MyProbe::probe)`. By then resources are loaded, the GL context exists and the mod's own `onApplicationLoad` has run. `run` prints `NATIVE_SMOKE COMPLETE` and exits 0 only if nothing threw and every `expect` held; otherwise it prints `NATIVE_SMOKE FAILED` and, after an exception, saves `failure.png`.
3. **Stay inside the gate.** Never name a blocked class in probe code (see [How a run is put together](#how-a-run-is-put-together)): no `File`, `Files`, `Field`, `Method`, `Constructor` or `Proxy`. Reach files, reflection and screenshots through `NativeSmokeSupport`, whose public methods take and return only allowed types. The runner checks `Probe.jar` with `jdeps` before launching and names any blocked class it finds. When extending `NativeSmokeSupport`, keep blocked types out of public signatures and never catch an exception and carry on: a failure must reach `run` so it cannot turn into a pass.
4. Build state from real objects. For campaign-side UI, `Global.setSector(CampaignEngine.getInstance())` gives a usable sector, player character data and persistent data at startup without loading a save. Seed only what the UI reads.
5. **Find obfuscated classes by shape, not name.** Use the same rule your patch uses: `findClass(anchor, package, predicate)` with `hasMethod` checks on readable member names. Derive types from constructor parameters (`constructorParameters`) and read readable constants with `getStatic` (for example `CHAR_PANEL_WIDTH`) rather than hardcoding sizes. Use `construct` to pick constructors by argument shape.
6. Mount native panels in a root `createCustom(width, height, null)` panel, size them as vanilla does, `advance` about 30 frames, then `render` and `screenshot`.
7. Assert what a player would notice:
   - components exist and are visible (`getOpacity() > 0`);
   - they sit inside their parent and do not overlap native neighbours;
   - their edges align with the native control they sit beside;
   - labels read correctly;
   - the region actually has pixels (`litPixels`).
   Use `check` for prerequisites that later steps need (it stops the probe) and `expect` for independent assertions (it records and carries on, so one game boot reports every failure). Log positions with `NativeSmokeSupport.log`.
8. Drive state changes through the mod's real entry points. Private methods may be called with `invoke` from test code. Re-render and capture each state.
9. **Read every PNG with an image tool.** Exit status and the marker do not establish visual correctness.
10. Read the `NATIVE_SMOKE` lines and the PatchLib `Discovered patch` / `Installed a ... patch site` lines in `launcher.log` and `logs`. Report only the paths you exercised.

## Known pitfalls

- **Game-state gates.** At `onApplicationLoad` the state is not `CAMPAIGN`. Hooks gated on `Global.getCurrentState()` or on a player fleet will no-op, so call the code the hook would call directly, and say so, or use scripted checks. For combat code gated on `CombatState` or on `settings.json` combat plugins, use a real battle ([profiling.md](profiling.md#frame-cost-in-a-real-battle)). A PatchLib `Discovered patch` line proves discovery, not installation or execution. A target class first loaded after startup may not log an `Installed` line.
- **Synthetic input events.** `NativeSmokeSupport.key()` and `mouse()` return proxies that answer every `InputEventAPI` call (unlisted ones with `false`, `0` or `null`). Engine helpers that cast the event to its internal type, such as `PositionAPI.containsEvent(event)`, still throw `ClassCastException` on them, and a plugin's `guard` often hides that, so the test just sees an unconsumed event. Hit-test against the tracked cursor in mod code (it is equivalent and avoids the internal type), and move the real cursor with `Mouse.setCursorPosition` before sending a mouse event.
- **Gate trips.** `SecurityException: File access and reflection are not allowed to scripts` in the log means code behind the gate named a blocked class. In probe code, move the call into `NativeSmokeSupport`. In mod code it is a real bug players would hit.
- **Tooltip inset.** Buttons added through a `TooltipMakerAPI` render 5 units right of the element origin. Compare against native control positions and compensate in layout constants rather than eyeballing.
- **Detached ship-list icons.** `TooltipMakerAPI.addShipList()` results stay parented to the tooltip, and `setLocation()` recomputes the position from that parent. Detach with `UIPanelAPI.removeComponent()` before positioning independently, or render the attached owner.
- **Dialog fold animations** hold content through `setNext()`. Start traversal from a readable accessor such as `getInnerPanel()`, not the outer dialog's current children.
- **Exact-type hooks** need the actual target class, not a subclass fixture.
- **Case in PowerShell.** PowerShell variables ignore case, so `foreach ($mod ...)` overwrites `$Mod`. Use distinct names in runner scripts.
- **Fast Rendering.** If the install uses Fast Rendering, startup results are without it. That is usually what you want for a baseline. To check a rendering bug with it, use scripted checks with `--fast` or a live session.
- **License or launcher hangs.** Use an already licensed install. Read `launcher.log`.
