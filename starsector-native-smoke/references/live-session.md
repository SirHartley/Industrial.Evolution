# Live session

A live session starts the real game against an isolated mods, saves and logs folder, then drives it with clicks, key presses and a console, and proves each step with window captures. Use it for what only clicks can reach: a save from an older mod version, the title screen, real screens, a console command. It is slow and manual. Whatever test code can drive belongs in [scripted checks](scripted-checks.md), and a game that scripted checks left open (`c.hold()`, `--keep`) is driven the same way as below.

Before you start: `smoke.sh setup` has run ([setup.md](setup.md)), and you have read [slots.md](slots.md), because here you take and free the slot by hand.

Status: the Windows path was used end to end, with and without extra content mods, to reproduce and fix seven MagicLib bugs on 0.98a-RC8, about thirty game launches in all. Linux and WSL paths are written but not yet run.

## Files

| Path | Purpose |
|---|---|
| `scripts/live/launch-game.sh` | Starts the game directly (no launcher window) against `WORK_DIR/{mods,saves,screenshots,logs}`. Detects Windows or Linux installs, uses Fast Rendering when it is installed, and on Windows keeps the window behind yours. Writes the game's PID to `WORK_DIR/game.pid` and prints the Java it uses. |
| `scripts/live/game-proc.sh` | `alive`, `stop` or `pid` for the game in `WORK_DIR/game.pid` only. See [Process safety](slots.md#process-safety). |
| `scripts/live/smoke-slot.sh` | Shares the machine between sessions: a slot per game (three at once by default), a perf lock for a run that must be alone, a queue, removal of stale locks and `status`. See [Slots and locks](slots.md#slots-and-locks). |
| `scripts/live/find-mod.sh` | Prints the folder in `GAME/mods` for a `mod_info.json` id, highest version first. Folder names carry versions, so look mods up by id. |
| `scripts/live/make-modset.sh` | Fills `WORK_DIR/mods` with links to the mod folders you name and writes `enabled_mods.json` from their ids. Junctions on Windows and WSL-to-Windows, symlinks on Linux. |
| `scripts/live/wait-log.sh` | Waits for the game log to match a pattern or go quiet. Exits 1 when the game is gone and 3 when it crashed. |
| `scripts/live/gamectl.sh`, `gamectl.cs` | Windows window control: background clicks, keys, typing and window capture. `gamectl.sh path` prints the built `gamectl.exe`; `gamectl.sh COMMAND ...` runs one command. Built on first use into the machine folder (`build-tools.sh`). |
| `scripts/live/stage-mod.sh` | Stages the mod under test in a folder of its own: links to its folders, copies of its files and jars, so the game never locks a jar in the checkout. |
| `scripts/live/clean-work.sh` | Deletes a work folder without following its links (`smoke.sh clean`). |
| `scripts/live/build-test-mod.sh` | Compiles a test mod's `src/` against the game API and any mod jars it calls. |
| `scripts/live/new-game-1600x900.sh` | Example: starts a new game from the title screen with background input only. |
| `templates/live-test-mod/` | Test-only helper mod: stops the game idling in the background, makes F10 open the console, and shows how to add a console command that prints the state you need to prove. |
| `templates/CursorGuard.java` | Test-only Java agent that `launch-game.sh` and the startup runner load, so the game never moves the real cursor. See [The real cursor](#the-real-cursor). |

## Isolation

- Never point the game at the user's real `mods` folder, `enabled_mods.json` or `saves`. `launch-game.sh` passes `-Dcom.fs.starfarer.settings.paths.{saves,screenshots,mods,logs}` so everything lands in `WORK_DIR`.
- `make-modset.sh` only links mods; it never copies, edits or deletes them. When it rebuilds the folder it removes links only.
- The license is read from the user's own install (Windows registry, or `~/.java/.userPrefs` on Linux). Never print, copy into a shared place, or edit license data.
- On Windows the run gets a memory copy of the registry prefs (`SmokePrefs`), as the startup runner does. Without it the game writes each save path to the `continue` key, which repoints Continue in every install on the machine, and a later run in another folder loads the wrong save. The copy's `continue` points at the newest save in `WORK_DIR/saves`, so the Continue button loads this run's save. The copy is deleted when the game exits. It needs the JDK that `smoke.sh setup` stored.
- The runners use the game's own `jre`. When `launch-game.sh` warns that a launch file in the install names another Java (a newer JDK, a custom launcher), ask the user which one they play on before reporting results as theirs. PatchLib, for one, breaks on Java 25 and later without Fast Rendering.
- Keep the mod list small. Each extra mod adds load time. With LazyLib, Console Commands, one library and a test mod, 0.98a-RC8 reached the title screen in about 15 seconds of game-log time, and about 9 with Fast Rendering.

## Start a session

```bash
SKILL='<this skill folder>'
SLOT="$SKILL/scripts/live/smoke-slot.sh"
GAME=$(bash "$SLOT" get game_root)          # stored by smoke.sh setup; empty means: run setup first
export SMOKE_OWNER=myproject.session
export SMOKE_SLOT=$(bash "$SLOT" acquire "$SMOKE_OWNER")
WORK="$(bash "$SLOT" get work_root)/myproject/session-slot$SMOKE_SLOT"
mkdir -p "$WORK"

LAZYLIB=$(bash "$SKILL/scripts/live/find-mod.sh" "$GAME" lw_lazylib)
CONSOLE=$(bash "$SKILL/scripts/live/find-mod.sh" "$GAME" lw_console)
cp -r "$SKILL/templates/live-test-mod" "$WORK/live_test_helper"
bash "$SKILL/scripts/live/build-test-mod.sh" "$GAME" "$WORK/live_test_helper" live_test_helper "$CONSOLE/jars/lw_Console.jar"
bash "$SKILL/scripts/live/stage-mod.sh" /path/to/ModUnderTest "$WORK/staged/ModUnderTest"
bash "$SKILL/scripts/live/make-modset.sh" "$WORK" "$LAZYLIB" "$CONSOLE" "$WORK/staged/ModUnderTest" "$WORK/live_test_helper"
bash "$SKILL/scripts/live/launch-game.sh" "$GAME" "$WORK" > "$WORK/launch.log" 2>&1   # run in the background
bash "$SKILL/scripts/live/wait-log.sh" "$WORK/logs/starsector.log" "" 200
export GAMECTL_PID=$(cat "$WORK/game.pid")
G=$(bash "$SKILL/scripts/live/gamectl.sh" path)
# ... drive and capture ...
bash "$SKILL/scripts/live/game-proc.sh" stop "$WORK"
bash "$SLOT" release "$SMOKE_SLOT" "$SMOKE_OWNER"
```

`make-modset.sh` takes mod folders, so list every dependency yourself here (the check runner adds them, this path does not). Build the mod under test before staging it; `stage-mod.sh` takes a freshly built jar as a third argument. `launch-game.sh` options are listed at the top of the script. The useful ones are `SMOKE_RESOLUTION` (default `1600x900`), `SMOKE_HEAP` (default `3g`) and `SMOKE_FAST_RENDER` (`auto`, `1` or `0`).

To test a library update against an old save, run the old version, make the save, back up the save folder, then swap the link to the new build and load the backup. Restore the backup before every run so each run starts from the same state.

Saves and versions:

- Keep each backup under its original folder name (for example `backups/old-save/save_tester_123/`) and copy that folder back. The title screen's Continue button follows the last-saved folder name and is greyed out when that folder is missing, so load with Load Game instead.
- The game will not continue a save made with a higher mod version number than the one now enabled. A save made on a "1.5.8" build cannot be continued on a "1.5.8rc2" build.
- To test what a release will do (for example a check that compares against the version a save was made with), copy the build and change only the `version` in its `mod_info.json`.
- To compare before and after a fix, run the same steps on the unfixed and the fixed build from the same restored save, and keep both sets of captures.

## Fast Rendering

[Fast Rendering](https://github.com/Halke1986/starsector-render) speeds up loading and drawing. `launch-game.sh` turns it on when `fr.jar` is in the game's core folder (`starsector-core` on Windows):

- 0.8.0 and later: `fr.jar` first on the classpath, plus `-javaagent:fr.agent.jar -Dcom.genir.renderer.watchdog=0`. This is what its own `fr.bat` and `fr.vmparams` do.
- Before 0.8.0: `-Djava.system.class.loader=com.genir.renderer.loaders.AppClassLoader`. Newer `fr.jar` files do not contain that class, and the JVM then fails at startup with `ClassNotFoundException: com.genir.renderer.loaders.AppClassLoader`.
- If `PatchLibAgent.jar` is in the core folder it is added as an agent too, as `fr.bat` does.

Fast Rendering rewrites the game's rendering, texture and script loading. If a result could depend on drawing or loading (missing sprites, wrong colours, a plugin that fails to initialize), rerun with `SMOKE_FAST_RENDER=0` before reporting it.

Once in about thirty Windows launches with Fast Rendering and background input, the game hung right after loading a save: the window kept its last frame, the process used no CPU, and the log stopped at `Loading stage 39 - last`. A thread dump (`jstack <pid>` from any JDK 17) showed the game's main thread blocked in `org.lwjgl.input.Mouse.getX`, waiting for a lock held by a Fast Rendering thread that was stuck inside `WindowsDisplay.nUpdate` (the window message loop). Rerunning the same steps worked. If a run stalls, check the CPU time of the java process; if it does not move, take a thread dump, then restart. Do not report a hang like this as a bug in the mod under test without reproducing it with `SMOKE_FAST_RENDER=0`. The game's version text gains `FR` and a version number (for example `0.98a-RC8 FR8.0`), so captures show which mode was used. Fast Rendering documents Windows only; on Linux it is untested here.

## Driving the game on Windows

`gamectl.exe` posts window messages straight to the game window. Nothing happens to the real mouse or keyboard, and the game never comes to the front. The user can keep working.

```bash
G=$(bash "$SKILL/scripts/live/gamectl.sh" path)      # with GAMECTL_PID set to the game's PID
"$G" bmove 1230 283; "$G" bclick 1235 283     # move first, then click
"$G" bkey F10; "$G" btype mystatus; "$G" bkey ENTER
"$G" bshot "$WORK/step-03.png"                 # then open the PNG and look at it
```

What makes this work, and the limits found so far:

- **The game idles when it is not in front.** With the campaign paused and the window inactive, the main loop stops, so background input and captures do nothing. The test mod's `data/config/settings.json` sets `"idleWhileWindowNotVisible": false`; mod settings merge into the game's.
- **Keep the window behind, not minimized.** `bshot` uses `PrintWindow` with full-content capture, which works while other windows cover the game but returns nothing useful for a minimized window. `launch-game.sh` runs `gamectl hide`, which pushes the window to the back whenever it comes forward during startup and gives focus back to the window that had it.
- **Move before you click.** The first click on a button sometimes only updates hover state. A `bmove` a few pixels away, then `bclick`, works reliably.
- **Typing.** The game builds characters from key-down messages and the real keyboard's Shift state. Only lowercase letters, digits, space, `-`, `.` and `/` can be typed, and if the person at the PC holds Shift while you type, some letters come out as capitals. Name test console commands with lowercase letters and digits, and have them read `-` as `_` in IDs (variant and paintjob IDs are full of underscores). Avoid arguments that need capitals: for example, build an empty hull from a hull ID instead of typing a variant ID like `sunder_CS`. Console Commands ignores case in command names. `btype` waits about 70 ms per key press; at 30 fps, faster typing dropped letters.
- **The first key after the console opens can be lost**, especially the first time in a session. Send `bkey BACK` (harmless on an empty line) before typing.
- **No modifier keys.** Ctrl, Shift and Alt cannot be held with posted messages. Console Commands opens with Ctrl+Backspace by default, so the test mod sets F10 instead (see `ConsoleKey.java`). The legacy console reads `consoleKeystroke` from `saves/common/config/lw_console_settings.json.data` as `keyCode|ctrl|alt|shift`; the default (new) console ignores that file.
- **Coordinates** are client pixels, origin top left, the same as the `bshot` PNG. Measure them on a capture. They depend on resolution and on mods that change UI, so the `new-game-1600x900.sh` coordinates are for 1600x900 only.
- **Confirmation dialogs.** Saving from the Esc menu opens a "Save current game" dialog; the save only happens after its Save button. Check the log for `Saving stage 8` rather than trusting the click. A new game also writes its first save right after generation, which is easy to mistake for your own save.
- **Clicking too early.** "Log quiet" can come a moment before the title menu accepts clicks. Wait a few more seconds before the first click, and capture the screen to confirm the click did something.
- **Dialogs from console commands.** `showInteractionDialog` called from inside a console command is dropped. Add a transient `EveryFrameScript` that opens the dialog on its first unpaused frame, then close the console and unpause. Number keys pick dialog options (`bkey 2`).
- **Spawned fleets wander off.** A fleet created for a test has no orders and leaves. Give it `FleetAssignment.HOLD` near the player.
- **Moving the fleet.** A `bclick` on empty campaign space sets a travel destination while the game is unpaused.
- **`gamectl hide` keeps running** for its whole timer after a launch. That is why the tools are built per source version into the machine folder and never rebuilt in place.
- **F12.** The game's own screenshot key did not save a file when sent as a background key press. Use `bshot`.
- **Foreground commands** (`click`, `key`, `shot`, ...) exist for cases the background path cannot reach. They check that the game is in front before every input and stop otherwise, so a click can never land in another window. They move the real cursor. Avoid them while someone is using the PC, and never use them while another slot is held.

## The real cursor

The game moves the operating system's cursor itself: `CombatState.init` centres it when a battle opens, and taking command of another ship does the same (the combat UI, and vanilla `NeuralLinkScript`). Both call `Mouse.setCursorPosition`, and LWJGL 2's `WindowsDisplay.setCursorPosition` then calls `SetCursorPos` without checking whether the window is in front (LWJGL 2.9.3 sources). So a test game behind the user's windows pulled the cursor into its own window at every battle start, and more often with several games.

`launch-game.sh` and the startup runner load `templates/CursorGuard.java` as a Java agent. It replaces that one native call in `WindowsDisplay.setCursorPosition(II)V` with a `pop2`; LWJGL still records the position, so the game sees the same coordinates, and probes that place LWJGL's cursor with `Mouse.setCursorPosition` keep working. Grabbed mode is left alone, because LWJGL grabs and centres only a focused window. The launch log shows `CURSOR_GUARD on: ...`, or `CURSOR_GUARD off: ...` with the reason (the agent then changes nothing and the game starts normally). It needs a JDK (`SMOKE_JAVAC_DIR`) and the export `--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED`, which the runners pass. `SMOKE_CURSOR_GUARD=0` turns it off. Foreground `gamectl` commands still move the cursor, on purpose.

## Driving the game on Linux or WSL

`gamectl` is Windows-only. Two possible routes, neither run yet (untested):

- **Against a Windows install from WSL:** `launch-game.sh` uses `wslpath` and starts `java.exe`; `make-modset.sh` makes junctions with `cmd.exe`. `gamectl.exe` is a Windows program and can be called from WSL as is.
- **Linux install (native, or under WSLg):** X11 tools such as `xdotool` (`xdotool search --name Starsector`, `mousemove --window`, `click`, `key`) and `import -window <id>` (ImageMagick) for captures. `xdotool key --window` sends synthetic events that some programs ignore, so check that LWJGL accepts them before relying on it. The idle setting above still applies.

## Waiting

- **If a run looks stuck, capture the window first** (`gamectl bshot` with an absolute path) and open the image. A screen says at once what a log hides: a load error dialog, a popup, the title screen. `run-live-checks.sh` does this itself on a timeout or a failed load and prints the path.
- Clear the log (`: > "$WORK/logs/starsector.log"`) before an action you want to wait on, then call `wait-log.sh LOG PATTERN SECONDS 0` with a pattern when you know one (`"Saving stage 8"`), or with `""` to wait for the log to stop growing. The fourth argument is the line to search from: `0` after clearing the log, or the `at line N` the last wait printed. Without it only lines written after the call count, so a line the game wrote between your action and the call is missed.
- After a new game, wait for `"Saving stage 8"`: the game saves once generation finishes. That is much faster than waiting for quiet, since the new campaign starts unpaused and keeps logging.
- "Quiet" works for loading screens. It does not work while the campaign runs unpaused, because the game keeps logging; there it waits out the whole timeout. Pause first (`bkey SPACE`) or wait on a pattern.
- In-game time: one day takes about 10 real seconds at normal speed.

## Proving a result

- Add console commands to the test mod that print exactly the state the claim depends on (a field, a count, credits before and after). A capture of the console output is the evidence. Keep the test mod's classes out of the mod under test.
- Capture before and after every step that matters, and open every PNG. A step that "ran" is not a step that worked: the save dialog above looked done and was not.
- Read the game log for exceptions after each run. Mods such as Console Commands log harmless errors of their own (for example `Failed to reduce modpath` when the mods folder is outside the game folder); say which ones you ignored.
- To check a fix for side effects, run one full session (new game, play a while, save, reload, play) on the unfixed and the fixed build with the same mods, keep each whole log, and compare the sets of error and warning lines with numbers and object addresses stripped. Lines that appear only with the fix need explaining.
- To check something drawn too often or not at all, measure it: capture the same scene on both builds and compare the mean brightness of the region it covers. A campaign trail drawn twice measured exactly twice as bright as the fixed one.
- Some state can't be seen on screen (a tag on a ship, a field in a save). Print it with a console command, and for saves, search the save's `campaign.xml` (with `compressSaveGameData` off, the default).
- Mod code, the test mod's included, cannot use `java.io` file classes or `java.lang.reflect` (the game's script class loader blocks them). Checks that need reflection belong in a startup probe, where `NativeSmokeSupport` does it from the game's classpath ([startup-probe.md](startup-probe.md)).

## Ending a session

1. Stop the game: `bash "$SKILL/scripts/live/game-proc.sh" stop "$WORK"`.
2. Free the slot: `bash "$SKILL/scripts/live/smoke-slot.sh" release "$SMOKE_SLOT" "$SMOKE_OWNER"`.
3. Check `bash "$SKILL/scripts/smoke.sh" status`: no slot of yours is left.
4. Keep the captures you report; delete the rest with `bash "$SKILL/scripts/smoke.sh" clean "$WORK"`.
