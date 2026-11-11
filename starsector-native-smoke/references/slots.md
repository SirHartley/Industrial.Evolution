# Slots, locks and process safety

How sessions share one machine. `smoke.sh checks` and `smoke.sh probe` follow these rules by themselves, so for them the four lines under "Sharing the machine" in `SKILL.md` are enough. Read this file before a live session (you take the slot by hand there), when a run waits or stops and you need to know why, or when the user asks who holds the machine.

## Slots and locks

The rules for every session that starts a game on a machine other sessions use:

1. **Every game holds a slot.** A run that starts a game process (startup probe, combat smoke, live session, scripted checks) holds a slot from before the launch until its game has exited, one slot per game. `smoke.sh checks` and `smoke.sh probe` take and free one themselves. `launch-game.sh` and the Windows probe runner refuse to start without `SMOKE_SLOT`; by hand, take one with `acquire` or wrap the command in `smoke-slot.sh run`. `SMOKE_LOCK=0` turns this off, only on a machine where no other session starts games.
2. **Locks only through `smoke-slot.sh`.** Never create, edit or delete lock files by hand, and never take a lock for another session. That includes an older lock folder (such as `.smoke-lock`) a project memory may still name: the script holds it for the slots (rule 12), and a session that takes it by hand blocks its own run.
3. **Owner names** are `project.task` (`mymod.checks`, `mymod.session`), so `status` tells everyone who holds what.
4. **Release your own slot, and only yours.** `release SLOT OWNER` refuses a slot of another owner. Free it as soon as its game has exited; do not hold a slot while editing code. The runners release it on every exit they control.
5. **The script removes stale locks, by rule.** Every `acquire`, `status` and `reap` removes stale locks and logs them to `reaped.log` in the lock folder. A slot is stale only when its game is not running and either its holder process has ended, or it went idle (no game, no `touch`, no log write) for `idle_min` minutes (15) without a holder or 3 hours with one. That removal needs nobody's approval. A lock the script reports as live is never removed, whatever it looks like.
6. **A running game keeps its slot.** Never stop another owner's game. When `status` shows a game whose holder is gone and that nobody drives, tell the user; the user decides.
7. **Interactive slots.** `acquire` without `--holder` gives a slot that lasts while its game runs, plus 15 idle minutes between games. Run `touch SLOT` during a longer pause. Export `SMOKE_OWNER` with `SMOKE_SLOT`: when the slot was removed or changed hands, `launch-game.sh` says so and starts nothing; take a new one.
8. **Modes.** A run asks for `--perf` (runner `PERF=1`) or `--shared` (`PERF=0`); one that asks for neither gets the machine's `default` setting. Perf runs are only for runs whose numbers are the result: frame cost, load time, days per second, before and after profiles, regression hunts. Never use one to skip the queue for a functional run. A perf request stops new games from starting, waits for running ones to end (up to `perf_wait`, 45 minutes), then runs alone; its lock ends with its slot. With `default=perf`, pass `--shared` (`PERF=0`) for every run that records no timing, or the machine runs one game at a time. `launch-game.sh` warns when a Starsector game outside the slots runs during a perf run, which may be the user's own; report that with the numbers.
9. **Waiting is first come, first served.** `acquire` queues, prints why it waits whenever the reason changes and gives up after `--wait` seconds (default one hour) with exit 3 and the status table. Report the blocking line to the user; do not remove anything.
10. **Pause and cancel.** `pause REASON` stops the machine gracefully: queued runs end with exit 4 and a `STOPPED` line, running games finish, and no game starts (`launch-game.sh` refuses, a runner ends at its next launch) until `resume`. Pause and resume only when the user asks. `cancel OWNER` stops one owner's work now: its queued runs end, each holder gets TERM (a runner then stops its game and frees its slot), whatever is still running after a minute is killed with its process tree, the owner's games are stopped through `game-proc.sh` and its slots freed. A run of that owner started after the cancel is not affected. Cancel your own owner, or another one only when the user asks.
11. **Stopping runs.** A task stop can end the shell that started a run and leave the run's scripts going; one such leftover waited for the lock and started a game after the machine had been paused. So the slot scripts watch the shells that started them: when one ends, a queued `acquire` stops with exit 4, `run` stops its command, and `run-live-checks.sh` stops its game and frees its slot at its next wait. A runner killed outright cannot clean up, and its game keeps the slot. To stop a run: `cancel OWNER`, then stop the task, then `status`. Start runs in the foreground or as the tool's background command, never with a trailing `&` in a shell that then exits; a run that must outlive its shell needs `SMOKE_DETACHED=1` and ends only through `cancel`.
12. **Machine settings** live in the machine folder (`config`), so every session reads the same ones; set them there, never in one session's environment. `smoke.sh setup` stores the game folder, the JDK and the work folder there as well ([setup.md](setup.md)). `legacy` names lock folders of an older one-game-at-a-time setup: no game starts while someone else holds one, and while slots are held the script holds them (`owner.txt` starts with `smoke-slots:`), so sessions on the old rule wait. Remove the setting once every session uses slots.

| Command | Use |
|---|---|
| `smoke-slot.sh acquire OWNER [--perf\|--shared] [--wait S] [--holder PID]` | Take a slot and print its number. `--holder` is the PID (`$$`) of the script that keeps the slot; without it the slot is interactive (rule 7). Exit 3: timed out. Exit 4: stopped (rules 10 and 11). |
| `smoke-slot.sh run OWNER [--perf\|--shared] [--wait S] -- CMD ...` | Take a slot, run one command with `SMOKE_SLOT` and `SMOKE_OWNER` set and `{slot}` in its arguments replaced, free the slot afterwards. For the startup probe and other one-shot runs. |
| `smoke-slot.sh release SLOT OWNER` | Free your slot. |
| `smoke-slot.sh touch SLOT` | Keep an interactive slot from going idle. |
| `smoke-slot.sh pause [REASON]`, `resume` | Rule 10. |
| `smoke-slot.sh cancel OWNER` | Rule 10. Exit 1 when queued runs of the owner still wait after a minute. |
| `smoke-slot.sh config [KEY=VALUE ...]` | Show or change the machine settings: `slots` (3), `default` (`shared`), `mem_mb` (5120), `idle_min` (15), `perf_wait` (2700 s), `legacy` (none), and the paths `game_root`, `javac_dir`, `work_root` that `smoke.sh setup` stores. An empty value restores the default. |
| `smoke-slot.sh get KEY` | Print one stored setting, empty when unset. |
| `smoke-slot.sh status` | Settings, older locks, a pause, slots with owner, mode, holder, game PID and memory, the perf lock, the queue, cancels of the last hour and available memory. Answers "who holds the game lock" without reading any files. |
| `smoke-slot.sh game`, `keep`, `others`, `reap` | Used by `launch-game.sh` and `run-live-checks.sh`. |

`smoke.sh status` is `smoke-slot.sh status`, and `smoke.sh stop OWNER` is `smoke-slot.sh cancel OWNER`.

The machine folder is `SMOKE_LOCK_ROOT` (default `%LOCALAPPDATA%\starsector-smoke`), shared by every session of the same user. Set it to a folder every session may write to. Layout, for reading only: `config`, `paused`, `cancel/OWNER`, `slot-N/info` (owner, mode, holder PID, start time, game folder), `slot-N/beat`, `perf/info`, `queue/`, `reaped.log`, and besides the locks `tools/` (the built helpers) and `work/` (the default work folder).

Git Bash on a busy Windows machine needs up to a second for every external command, so the script reads its files with shell builtins and lists processes once per pass: expect a few seconds per call, and 5 to 10 s per pass while it waits.

## Several games at once

Up to `slots` games (a machine setting, default 3) run at once on one machine, from one session or several. Each holds a slot ([Slots and locks](#slots-and-locks)). What keeps them apart:

| Shared thing | How the games stay apart |
|---|---|
| Work folder | One per slot. `smoke.sh checks` uses `work_root/OWNER/slotN`; in a live session build `WORK` from `$SMOKE_SLOT`. Two games in one folder share a log, saves and `game.pid`. |
| Files the game writes | Only under the four `paths.*` folders `launch-game.sh` points into `WORK_DIR`: saves (with `saves/common`, the script cache and mission scores), screenshots, logs and mods (`enabled_mods.json`). Checked in 0.98a-RC8: `StarfarerSettings` reads them from the `com.fs.starfarer.settings.paths.*` properties, `ScriptStore` caches under the saves path and `ModManager` writes under the mods path. The install stays read-only. |
| Registry | `SmokePrefs` gives each game a copy in memory; no game writes the `continue` key. |
| Network ports | None. The game, `gamectl` and the profiler open no ports; `jcmd` attaches by PID. |
| Input and captures | `gamectl` posts messages to the window of `GAMECTL_PID` and refuses to guess while several games are open. Always set it from the slot's `game.pid`. |
| Focus | Each launch runs `gamectl hide`, which pushes back any window of that game (a crash box too) and hands focus back to the user's window, never to another test game. Each change is logged with a time in `WORK_DIR/run/hide.log` (kept with the run's results); read it when a game took the front. Foreground `gamectl` commands (`click`, `key`, `shot`, ...) use the real mouse and keyboard: never use them while another slot is held. |
| The real cursor | `CursorGuard`: [The real cursor](live-session.md#the-real-cursor). |
| Mod jars | Windows locks a jar while a game has it loaded. The check runner stages the mod under test and builds the check mod inside each slot's work folder, one build per project at a time. In a live session, stage the mod per slot with `stage-mod.sh` and never rebuild a jar another slot's game may have open. Linked dependencies are only read and are safe to share. |
| Base saves | Per slot folder. A slot without one copies the base save of another slot of the same project when their mod lists match (`base-saves.mods`), which skips the new game. Only one run of a project makes a base save at a time; the others wait and copy it. |
| `gamectl.exe` and the other tools | Built once per version of their sources into `tools/<sum>` of the machine folder, so a new skill version never replaces a file a running game or `gamectl hide` has open. |

Budget per game. Memory: the heap (`SMOKE_HEAP`, 3 GB, reserved at start by `-Xms`) plus native memory for LWJGL, textures on their way to the GPU, the JIT and threads, about 1.5 to 2 GB (an estimate; `smoke-slot.sh status` prints each game's working set, so set `mem_mb` from what the machine shows). A slot is granted only while `mem_mb` (default 5120) is available. CPU: the game loop is mostly one thread plus GC and JIT bursts, so three games want about six free cores to run at full speed. GPU: every game renders all the time because the test mods turn idling off, and video memory is not checked. When textures fail to load or frame rates collapse with three games, lower `slots`.

Games in shared slots run at BelowNormal priority so the PC stays usable; perf slots keep Normal (`SMOKE_PRIORITY` overrides).

What the numbers of a shared run mean. Check results, CPU and allocation shares, allocation totals and call counts are valid with other games running. Wall-clock numbers are not: load time, days per second, GC pause times, frame times and any before and after timing. The runner adds `MODE shared, other games during the run: N` to `results.txt`, and a `PROFILE NOTE` when N is above 0. Report those numbers only with that note, and take every timing claim (regression, speedup, frame cost, load time) from a perf run.

## Process safety

The user may have their own game open, and IDEs and Gradle daemons run `java.exe` too. So every script works on the one game it started, by PID:

- `launch-game.sh` writes the game's PID to `WORK_DIR/game.pid` and removes it when the game exits.
- Stop a game with `bash scripts/live/game-proc.sh stop WORK_DIR`. It checks that the process's command line still names `WORK_DIR` before it kills anything. Never stop Java processes by name or path.
- Set `GAMECTL_PID=$(cat WORK_DIR/game.pid)` before `gamectl` commands. Without it, `gamectl` refuses to act when more than one Starsector window is open.
- Delete a work or output folder only with `smoke.sh clean DIR` (`scripts/live/clean-work.sh`). After a hard kill the folder still holds junctions into `GAME/mods` and the checkout, and `rm -rf` or `Remove-Item -Recurse` deletes through them. The script removes links as links, and refuses a folder that holds a running test game, a mod, a game or a checkout.
- Stopping a background shell (`TaskStop`) can leave the scripts it started running, and their game. Stop a run that holds or waits for a slot with `smoke.sh stop OWNER` first ([Stopping runs](#slots-and-locks), rule 11), and a game you launched by hand with `game-proc.sh stop`.
- Run one live session or runner per work folder at a time. `run-live-checks.sh` refuses to start (`BUSY`) while another one uses the folder or while an earlier run's game is still open. Start runners once, without a trailing `&` inside a background command, so no second copy runs untracked.
- Never edit a script of the skill while a run uses it or waits for a slot. Bash reads the file as it goes, so the run continues at the wrong byte.
- Several games may run at once, each in its own slot and `WORK_DIR`: [Several games at once](#several-games-at-once).
