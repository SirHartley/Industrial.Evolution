# Scripted checks

The fastest way to verify behaviour in a running campaign: no clicks, no console typing. The check mod in `PROJECT/native-smoke/live/check-mod` runs a list of steps on the real sector and logs `LIVE_CHECK ok:` and `LIVE_CHECK FAIL:` lines. `smoke.sh checks` builds and stages the mod under test, boots an isolated game, loads a base save, runs the groups, stops the game and prints a verdict.

```bash
bash "$SKILL/scripts/smoke.sh" checks "<mod project folder>" example        # one group
bash "$SKILL/scripts/smoke.sh" checks "<mod project folder>" colony,raids   # several groups, one game
bash "$SKILL/scripts/smoke.sh" checks "<mod project folder>" all            # every group except battle
```

Before the first run: `smoke.sh setup` once per machine and `smoke.sh init PROJECT` once per project ([setup.md](setup.md)).

| Option | Effect |
|---|---|
| `--shards N` | Deals the groups out to N games that run at once: [Several games](#several-games). |
| `--perf` | The run waits until no other game runs and keeps the machine to itself. For every run whose timings are the result. |
| `--shared` | Shares the machine even when the machine's default is perf. |
| `--fast` | Uses Fast Rendering when it is installed: boots about 8 s faster. For development; do the final run without it. |
| `--keep` | Leaves the game open after the last step: [A game left open](#a-game-left-open). |
| `--newgame` | Runs the groups on a fresh new game instead of the base save, to check what a new game generates. About 90 s more. |
| `--newbase` | Makes a new base save even when one exists. |
| `--limit S` | Seconds until the run counts as timed out. Default 600. |

Variables for rarer needs: `JFR=0` (no profile), `PROFILE_BASE=old.jfr` (compare against an earlier profile), `CHECK_JARS` (extra jars for compiling the checks, `;`-separated), `SMOKE_JAVA_ARGS` (extra JVM arguments), `SMOKE_STALE_OK=1`, `SMOKE_DETACHED=1` ([slots.md](slots.md#slots-and-locks), rule 11).

## What a run does

1. **Checks the group names** against the `case "name":` labels in the check mod's `Groups.java`. A wrong name stops with `UNKNOWN_GROUP` and the list of names, before any game starts.
2. **Takes a slot** and waits in the queue when the machine is full ([slots.md](slots.md)). The work folder is `work_root/OWNER/slotN`.
3. **Builds and stages the mod under test** with the project's `stage_mod`, or links it with its built jars ([setup.md](setup.md#building-and-staging-the-mod-under-test)).
4. **Resolves the mods**: the staged mod, `DEPS`, and every dependency their `mod_info.json` files name, found by id in `GAME/mods` (highest version wins). Each added one prints `Added dependency ...`.
5. **Stops on a stale jar.** A mod folder with sources whose jar is missing or more than 2 minutes older than its newest `.java` file ends the run with `STALE_JAR`. Such a jar crashes at load after a whole game start.
6. **Compiles the check mod** in a copy inside the work folder, against the game and the jars of every enabled mod.
7. **Makes or reuses the base save.** One per mod list. The first run of a mod list starts a new game (about 3 minutes on a busy PC) and saves it; later runs load it. A slot without one copies it from another slot of the project, and only one run makes it while the others wait.
8. **Runs the groups** in one game, answers `c.shot` and `c.key`, and watches the log for the end, a crash or a failed load.
9. **Writes the results**, copies them to `runs/<time>-<groups>/`, reads the profile and prints the verdict.

Dev mode is turned off for the run (a modding install often has it on, and it changes behaviour). Turn it on in a step that needs it.

## Reading the output

The run prints, in this order: the version line, staging and dependency lines, then every `LIVE_CHECK FAIL`, `LIVE_CHECK group` and `LIVE_CHECK COMPLETE` line, the `MODE` line, log errors, `PROFILE` lines, a `CAPTURES` line, a `NEXT:` line and the verdict as the last line:

```text
LIVE_CHECK group example: 3 ok, 0 failed, 41 s
LIVE_CHECK COMPLETE passed=3 failed=0
MODE shared, other games during the run: 0
PROFILE: 3501 CPU samples, 10087 MB allocated, 22 GCs, longest pause 146 ms, mymod 1.3% of CPU, 1.1% of allocation
CAPTURES in .../runs/20261111-201500-example: check-menu.png - open each one with an image tool, a pass proves nothing about what a player sees
COMPLETE: 3 ok, 0 failed, 96 s, results in .../runs/20261111-201500-example
```

- The verdict table is in `SKILL.md`. Only `COMPLETE` means everything passed. The exit code is 0 for `COMPLETE` and for `HELD` without a failed check, 1 for every other verdict, 2 for a stop before the game starts, 3 when the wait for a slot timed out and 4 for `STOPPED`.
- Check ids carry their group: `LIVE_CHECK FAIL: colony / mining still there after a month`.
- The game counts its checks itself (`passed=N failed=M`). When the lines in the log give other numbers, the verdict is `COUNT_MISMATCH`.
- An ERROR or FATAL line in the game log outside the known noise fails the run with `LOG_ERRORS`, so a swallowed exception cannot pass.
- `results.txt` in the kept folder holds every line: two `RUN` lines (skill version, groups, mods, base save, Fast Rendering, Java), the check lines, the `MODE` line and the verdict. Next to it: `starsector.log`, `launch.log`, the captures, `threads.txt` after a timeout, `profile.jfr` and `profile.txt`.
- **Read every `FAIL` line.** A step that throws counts as a failure and the run goes on; a failed step can leave later steps without their state, so fix the first failure first.

## Stops before the game starts

Each prints one line that starts with the name. Do what the line says; do not work around it.

| Line | Cause | Fix |
|---|---|---|
| `NO_SETUP` | No game folder is stored for this machine. | `smoke.sh setup "<game folder>"`; ask the user for the folder. |
| `NO_PROJECT` | The folder does not exist, or has no `mod_info.json` or no `native-smoke/live/project.sh`. | Pass the mod's root folder; `smoke.sh init PROJECT`. |
| `BAD_PROJECT` | `OWNER` in `project.sh` has other characters than letters, digits, `.`, `_`, `-`. | Fix `project.sh`. |
| `NO_GAME` | The stored game folder is gone. | `smoke.sh setup "<game folder>"`. |
| `NO_CHECK_MOD` | `CHECK_DIR` has no `mod_info.json`. | `smoke.sh init`, or fix `CHECK_DIR` in `project.sh`. |
| `UNKNOWN_GROUP` | A group is not a case label in `Groups.java`. | Use a listed name, or add the group. |
| `NO_JDK`, `NO_GAMECTL` | No JDK 17+, or `gamectl.exe` did not build. | `smoke.sh setup`. |
| `BUSY` | Another run uses this work folder, a game from an earlier run is still open, or another run has been staging or making the base save for 10 minutes. | Wait, or run the command the line prints. Check `smoke.sh status`. |
| `STAGE_FAILED` | `stage_mod` or the default staging failed: usually the mod did not compile. | Read the lines it prints from `stage.log`. |
| `MISSING_MOD` | A dependency or a `DEPS` id is not in `GAME/mods`. | Tell the user which mod to install; never install or enable mods in the user's game yourself. |
| `STALE_JAR` | A jar is missing or older than its sources. | Build the mod, or set `stage_mod`. |
| `BUILD_FAILED` | The check mod did not compile. | Fix the sources; javac's errors are above the line. |
| `MODSET_FAILED` | The links for the mod set could not be made. | Read `modset.log`. |
| `NO_GAME_PID` | The game did not start. | Read the lines it prints from `launch.log`. |
| `NEW_GAME_FAILED` | The new-game clicks for the base save did not end in a save. | Open `new-game-failed.png`. The clicks fit 1600x900 and an unchanged title screen. |
| `FATAL` | The game crashed while the base save was made. | Open `fatal.png`, read the ERROR lines in the log. |
| `STOPPED` | The machine is paused, the owner was cancelled, or the shell that started the run has ended. | Read the line and `smoke.sh status`. Do not restart in a loop. |

## Writing checks

The check mod is a small mod of its own (`livecheck` package, never shipped):

| File | Role |
|---|---|
| `Groups.java` | Maps group names to step lists. One `case "name":` label per group; the runner reads these labels. |
| `ExampleChecks.java` | A group: `steps()` returns the list. Copy it for each group. |
| `Ctx.java` | What a step gets as `c`: checks, waits, captures, stored values. |
| `Colony.java`, `Time.java`, `Ui.java` | Colonies in free systems, the day skip, reading tooltips and dialogs. |
| `Runner.java`, `CheckPlugin.java`, `Step.java` | The driver. Leave them alone. |

A step runs once, on the campaign thread, paused. `c.days`, `c.until`, `c.frames`, `c.reload`, `c.shot` and `c.key` hold the next step back until they are done, so put what must be checked afterwards into the next step.

```java
steps.add(c -> {
    MarketAPI market = Colony.make(Colony.planets(1).get(0), 5);
    c.put("market", market.getId());
    market.addIndustry(Industries.MINING);
    c.expect(market.hasIndustry(Industries.MINING), "colony has mining");
    c.days(35f);
});
steps.add(c -> c.expect(c.market("market").hasIndustry(Industries.MINING), "mining still there after a month"));
```

| Call | Use |
|---|---|
| `c.expect(condition, id)` | One check. The id is a sentence that is true when the check passes. |
| `c.eq(actual, expected, id)`, `c.near(actual, expected, tolerance, id)` | A check whose failed line shows both values. Prefer them over `expect(a.equals(b), ...)`. |
| `c.note(text)` | A line in the results that is not a check. |
| `c.days(n)` | Lets n game days pass. About 1.7 days per second. |
| `c.until(condition, maxDays, id)` | Runs time until the condition holds; fails with the id after `maxDays`. A fourth argument is a hook that runs every frame. |
| `c.frames(n)` | Waits n frames, for UI that needs a moment. |
| `c.reload()` | Saves and loads in the same process (`cmdSave`, then `quickLoad`), about 5 s. The next step runs in the loaded game. |
| `c.put(key, value)`, `c.get(key)`, `c.market(key)` | Values that survive a reload (persistent data). Statics survive `quickLoad` too, not a restart. |
| `c.shot("name")` | The runner saves `check-name.png`. Lowercase letters, digits and `-`; one name once per run. |
| `c.key("ESC")` | A real key press through the runner, for input that only goes through the game's input loop, such as ability hotkeys. Key names are gamectl's: `ESC`, `ENTER`, `SPACE`, `TAB`, `F1` to `F12`, letters, digits. |
| `c.keepDialog(true)` | The driver closes any open dialog before each step. This keeps one open for the steps that work in it; turn it off again afterwards. |
| `c.hold()` | Ends the steps here and leaves the game open: [A game left open](#a-game-left-open). |

After `c.shot` and `c.key` the next step waits until the runner has answered (it writes an ack file the game sees). When no answer comes within 15 s, the run gets a `FAIL` line that says so; the capture or key press did not happen.

| Need | Use | Cost |
|---|---|---|
| Colonies | `Colony.make(planet, size)` founds one the way the player does, with the colonized listener call. `Colony.planets(n)` gives planets in a system no other group uses. | instant |
| Text a player reads | `Ui.tooltip()`, fill it with the real tooltip code, `Ui.text(tooltip)`. Works on dialog text panels too. Tooltip fixes need no screenshot. | instant |
| A dialog to work in | `c.keepDialog(true)`, then `showInteractionDialog(new Ui.Bare(), entity)`, then `c.frames(30)`. `dialog.setInteractionTarget(entity)` reuses one dialog for many entities. | |
| Is a dialog option enabled | `Ui.button(dialog.getOptionPanel(), "label").isEnabled()`; the option panel has no getter. | |
| Real code that needs a dialog | Rule commands run fine with `Global.getFactory().createMemory()` memory maps and `Misc.tokenize` params. | |
| Private members | `MethodHandles.privateLookupIn` works behind the game's security gate, reflection classes do not. | |
| Finished raids and similar | Subclass the real intel class in the check mod and override only the state getters, so the production lookups still run. | |
| What a new game generates | `--newgame`. Set switches in the check plugin's `onApplicationLoad`; the check mod's name sorts it after the libraries. | about 90 s |
| UI that only exists on screen | A group that builds the state and calls `c.hold()`, then `gamectl` clicks and captures. | |
| A real battle, frame cost | `templates/live-check-combat/`: [profiling.md](profiling.md#frame-cost-in-a-real-battle). | |

Add a group: write `MyChecks.java` like `ExampleChecks.java`, add `case "mygroup": all.addAll(MyChecks.steps()); break;` to `Groups.java`, run `smoke.sh checks PROJECT mygroup`.

## A game left open

For UI that only exists on screen. Two ways:

- `c.hold()` in a step: the steps end there, the verdict is `HELD` and the game stays open in the state the group built.
- `--keep`: the game stays open after the last step with the verdict it earned.

The run then prints `GAME OPEN:` lines with the game's PID, the `gamectl` path and the command that stops the game and frees the slot. The slot stays yours while the game runs. Drive the game with background `gamectl` commands and capture every step ([live-session.md](live-session.md#driving-the-game-on-windows)), then run the stop command the line gave. Never leave an open game behind: it holds a slot other sessions wait for.

At 1600x900 with the colony remote: `bclick 842 885` opens Command, a row opens the colony, a click on an industry opens its menu.

## Several games

```bash
bash "$SKILL/scripts/smoke.sh" checks "<mod project folder>" a,b,c,d,e,f --shards 3
```

The groups are dealt out in order (a and d to game 1, b and e to game 2, c and f to game 3), each game in its own slot and work folder. N is capped at the machine's `slots`. The first game makes the base save when there is none; the others wait and copy it. Each game's result lines are printed under `--- shard I`, its whole output is kept as `runner.log` in that run's results folder, and the last line adds them up: `COMPLETE: X ok, Y failed, T s, N games`, or `SHARDS_FAILED` with the games that did not end with `COMPLETE`.

- Use it when the groups take more than about three minutes together; below that the extra boots cost more than they save.
- Never for timings: the games share the CPU. `--perf` and `--keep` are refused with shards.
- Groups must not depend on each other's state or order.
- Stop all of them with `smoke.sh stop OWNER.checks`.

## Speed

- **Many groups in one game.** `a,b,c` needs one boot: 85 checks over 4 groups took 101 s. `Colony.planets` never reuses a system, so groups stay apart. Put a group that leaves delayed work behind last.
- **Never restart for a reload.** `c.reload()` is about 10 times faster than a boot.
- **Day skip** caps at about 1.7 days per second. `Time.mult` above 60 gains nothing: the limit is the CPU cost of vanilla `Market.advance` and stat updates.
- **`--fast`** reaches the title in 19 s instead of 26 s and runs checks the same.
- **Reuse the work folder.** It holds the base save; a new `work_root` or owner pays for a new game again.

## Accuracy

- **Reproduce first.** Run the check on the broken build and see it fail before fixing, so the check is known to detect the bug. A check that has never failed is unproven.
- **A check that passes on nothing proves nothing.** Add the opposite case: the expected effect with the setting on as well as off, a stock count above zero before asserting zero. Timers that never ran and shops never opened gave passes that way.
- **Assert the setup, not only the result.** A value of 0 can mean "bug gone" or "the setup did nothing".
- **Repeat anything random in the same process.** A real spawn check passed on its first run while 8.7% of arcs failed. Running the real movement class over 4000 random arcs in one step found the bug in a second and proved the fix with 0 of 4000.
- **Use the real class in a tight loop** when the input space is large: most plugin and module classes run fine on a location token with `advance(1f / 60f)`.
- **Measure across the whole run, not at the end.** Sample per frame with the four-argument `c.until` and count bad frames. The end state hid every symptom of a flashing indicator.
- **Record when an event happened from the frame hook.** Reading it after a long wait measures the wait.
- **Test planets differ.** Conditions on a random planet can silently zero an output. Feed the value the check needs instead of relying on the planet.
- **An ability on the fleet needs `Global.getSector().getCharacterData().addAbility(id)` too**, or it is gone a frame later.
- **Fleets can miss their target at 60x.** Above about 10x a fleet can overshoot the 50 unit arrival check, so `GO_TO_LOCATION_AND_DESPAWN` never lands. Set `Time.mult = 3f` around a step that waits for a fleet to arrive, and suspect the harness before calling such a failure a bug.
- **A dialog you did not open stops yours.** `showInteractionDialog` returns false and `showCoreUITab` does nothing while the Cargo tab or any other dialog is up, such as the accident report of a fleet out of supplies. `getCurrentInteractionDialog()` stays null then, `isShowingDialog()` says true. A check that only fails after other groups ran is usually state like this. The driver tops up supplies and closes dialogs before each step for that reason.

## Watching a run

- Run `smoke.sh checks` in the foreground, or as the tool's background command. Never with a trailing `&` in a shell that then exits.
- A background run needs no polling of its log: wait for it to end and read its last lines. When it seems stuck, `smoke.sh status` shows whether it waits for a slot; a capture (`gamectl bshot`, [live-session.md](live-session.md#driving-the-game-on-windows)) shows the screen.
- To stop it: `smoke.sh stop OWNER.checks`, then stop the task, then `smoke.sh status`.
- Never edit a script of the skill, `project.sh` or the check mod's sources while a run uses them. Bash reads a script as it goes.

## Without a project file

`run-live-checks.sh` also takes everything as arguments, for a check mod that belongs to no project:

```bash
bash "$SKILL/scripts/live/run-live-checks.sh" GAME_ROOT 'WORK_DIR/slot{slot}' CHECK_MOD_DIR GROUPS MOD [MOD...]
```

Each `MOD` is a folder or a `mod_info.json` id; dependencies are added as above, and the mod under test must be staged by the caller (`scripts/live/stage-mod.sh`). The options are the variables at the top of the script (`PERF`, `FAST`, `KEEP`, `NEWGAME`, `NEWBASE`, `LIMIT`). Prefer a project file.
