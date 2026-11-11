# Profiling and frame cost

## Profiling

Every scripted run records a Java Flight Recorder profile unless `JFR=0`. The game's JRE ships the recorder. The run ends with `PROFILE` lines and writes `profile.jfr` and the full report `profile.txt` into its results folder. `PROFILE_PKG` in the project's `project.sh` names the mod's package, so the report and warnings cover your code:

```text
PROFILE: 3501 CPU samples, 10087 MB allocated, 22 GCs, longest pause 146 ms, mymod 1.3% of CPU, 1.1% of allocation
PROFILE WARN: mymod is 14.0% of CPU samples
```

When to read it:

- **Always read the `PROFILE` lines** of a run, the same way as the check results. Report a `PROFILE WARN` to the user even when every check passed.
- **Open `profile.txt`** when a warning fires, when a check or a day skip got slower, when the user reports stutter, slow days or a long load, or when a change touches per frame code, listeners or loops over all markets.
- **Compare before and after** for any performance change or suspected regression, with both runs in perf mode (`--perf`). Run the same groups on both builds and pass the older file as `PROFILE_BASE`, or run `jfr-top.sh --compare BEFORE.jfr AFTER.jfr my.package`. Scripted steps make two recordings comparable, a hand run does not.
- **Pick the scenario on purpose.** The default sector colony mix can hide a cost: build the state that stresses the code (many colonies, a swarm, a long fleet list) in a group of its own and let it run some game days, then read that recording alone. Load time shows up in the startup probe's `profile.jfr`.

Reading it: "self" is time in that frame, "first frame in PACKAGE" is everything under your code, callees included, so a vanilla method called from your loop counts against you there. Shares are of sampled Java time only: native OpenGL and driver time is not sampled, and a run of several minutes gives only about 500 to 1500 samples. For absolute numbers, time the method itself ([Frame cost in a real battle](#frame-cost-in-a-real-battle)). Loading and vanilla ticking are the baseline: 1 to 2% for a large mod is normal. Fixing a frame under 1% is rarely worth it. The default warning is 10%, set by `PROFILE_WARN_SHARE`, and a GC pause of 250 ms, set by `PROFILE_WARN_PAUSE`.

Traps:

- `jfr print` cuts stacks to 5 frames by default, which hides nearly all mod code under vanilla frames. `jfr-top.sh` passes `--stack-depth 64`. Use the same when reading a recording by hand.
- The recording is written when the JVM exits normally, so it exists after `LIVE_CHECK COMPLETE` and after a startup probe. After `TIMEOUT` and `LOAD_FAILED` the runner asks the game for it (`jcmd JFR.dump`) before it stops the game, and saves a thread dump as `threads.txt`. For a game kept open, use `jcmd <pid> JFR.dump filename=...` from a JDK.
- `jfr` and `jcmd` come from a JDK, not the game's JRE. The scripts use the JDK `smoke.sh setup` stored; when the run prints `PROFILE: no report`, read `profile.txt` for the reason.
- A check run spends its time where the script sends it, so the numbers compare runs of the same steps and do not predict a player's frame rate. For one method, a PatchLib `@Before` and `@After` pair in the check mod times it directly.

## Frame cost in a real battle

The startup combat harness (`starsector-combat-smoke`) is not a real battle: there is no `CombatState`, no combat plugins from `settings.json` and no campaign fleets, so code gated on those returns early without a sign. For combat code in a real battle, and for its cost per frame, copy the contents of `templates/live-check-combat/` over the project's check mod (`native-smoke/live/check-mod`):

- `Battle.java` (a campaign step) fills both fleets with `FleetFactoryV3`, turns off the deployment dialog and calls `getCampaignUI().startBattle(...)`. Add `case "battle": all.addAll(Battle.steps()); break;` to `Groups`. The group `all` leaves `battle` out; name it to run it.
- `BattleDriver.java` is a combat plugin registered in the template's `settings.json`, active only for the `battle` group. It turns the deployment dialog off again in `init` (setting it only before `startBattle` left the dialog open over the battle), deploys every reserve, raises CR, warms up, checks that no dialog is open, then switches the code under test on and off in alternating windows and logs `PERF` lines. The campaign `Runner` does not tick in combat, so the driver ends the run itself: it logs `LIVE_CHECK COMPLETE` with the counts and exits.
- `Timers.java` holds the PatchLib patches: one to time and switch off the code under test (edit its target), and one that times the wait inside `Display.update` and `Display.sync`. Add `patchlib` to `DEPS` in `project.sh`; the runner then compiles the check mod against PatchLib's jars. For PatchLib names and behaviour, read the `patchlib` skill.

Run frame cost in perf mode (`--perf`): other games share the CPU and GPU and move every frame time. Before a frame-time run, **ask the user to turn off frame caps**: vsync, the in-game cap, and driver caps (NVIDIA or AMD control panel), including the separate limit for background windows, which always applies because the game stays behind the user's window. These are the user's settings, they take one click, and an agent cannot change them. A driver cap rounds every frame up to the cap: a 125-ship battle measured exactly 33.33 ms per frame with the code under test on and off. `Display.setVSyncEnabled(false)` in game does not override a driver cap.

Measuring:

- **Work time.** The driver reports wall time per frame and work time, which is wall time minus the time spent waiting inside `Display.update`/`sync`. Work time holds whatever cap is set. Compare work times and report both. A wall median pinned at 1000/N ms, or far above work time, means a cap is still on.
- **Fast alternation in one battle.** Separate battles differ too much. With 600-frame windows, the battle's own swing between windows (about 1 ms) was as large as the effect. Use 60-frame windows for code that switches off at once, and 300 frames with 150 to settle for code that leaves work behind (trails, particles), over about 4800 frames. Compare medians as well as means.
- **Direct timers next to the difference.** Always report the per-call timer of the code under test next to the on/off difference. Time the instances the game runs (`@Before` stores `System.nanoTime()` with `setLocal`, `@After` adds the difference, gated on `isMostDerivedCall()`), not a second instance built in test code.
- **Name exact classes in patches.** `packageName = "data.scripts.plugins"` also matched vanilla's loose `TestCombatPlugin`. Use `typeName`, or log the matched classes.
- **Other load on the PC.** Wall-clock timers count time the OS gives other programs, and GC pauses. On/off windows in one run cancel most of it; separate runs (before and after a fix) do not. Run them as A B A B, compare medians and spread, and add counts that load cannot change (calls, allocations, iterations) where you can.
- **Thread CPU time** (`ThreadMXBean.getCurrentThreadCpuTime`) leaves out other programs, but on Windows it moves in 15.6 ms steps. Use only its average over thousands of frames, never a per-frame value or a median.
- **Assert the state the measurement needs.** A check once "passed" its timers while measuring nothing, because the screen it timed was not open. A battle started from a script opens without the deployment dialog; to time that dialog, call `CombatState.showDeployDialog()` (what the G key calls) and check `isShowingDeploymentDialog()` first. The driver checks that the `Display` timer ran.
