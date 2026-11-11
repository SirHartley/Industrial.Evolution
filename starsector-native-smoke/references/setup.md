# Setup

The skill holds no path of any machine or project. `smoke.sh setup` stores what a machine needs, `smoke.sh init` stores what a project needs. Every other command reads those two places, so no session has to pass or guess a path.

| What | Where | Written by |
|---|---|---|
| Machine settings, slots and locks, built tools, default work folder | The machine folder: `%LOCALAPPDATA%\starsector-smoke` (`/tmp/starsector-smoke-USER` outside Windows) | `smoke.sh setup`, the runners |
| Project settings and the check mod | `PROJECT/native-smoke/live/` | `smoke.sh init`, then you |
| The skill | Wherever it is installed. Read-only. | Nobody |

## The machine: `smoke.sh setup`

```bash
bash "$SKILL/scripts/smoke.sh" setup "<game folder>" [--jdk "<JDK bin folder>"] [--work "<folder>"]
bash "$SKILL/scripts/smoke.sh" doctor      # the same checks, changes nothing
```

Run it from Git Bash. The game folder is the one that holds `starsector-core`, `mods` and `jre`; ask the user for it. A later `setup` without arguments checks the stored values again.

| Check | `FAIL` means | Fix |
|---|---|---|
| Game folder | No path given or stored, or the folder has no `starsector-core`, or `jre\bin\java.exe` is missing. | Ask the user for the folder and pass it. Never search the disk. |
| JDK 17 or newer | No `javac` and `jar` found. The game ships a JRE only. | Install a JDK 17 and pass `--jdk "<its bin folder>"`. |
| The game has run | The game's preferences are missing for this Windows account, so there is no licence. | The user starts the game once through its own launcher and enters the key. Never print or copy the preferences. |
| Work folder | It cannot be created, it lies inside the game's `mods` or core folder, or a junction in it does not resolve for the game. | Pass `--work DIR` with a folder this account may write to, outside `%LOCALAPPDATA%`. |
| Tools | `gamectl.exe`, the prefs helper or the cursor guard did not build. | `gamectl` needs the C# compiler that ships with Windows (.NET Framework 4), the other two the JDK. Try another JDK. |

Where the JDK comes from, in this order: `--jdk`, `SMOKE_JAVAC_DIR`, the stored `javac_dir`, then the lowest version from 17 up among `PATH`, `JAVA_HOME`, `GAME\jdk-17\bin`, `~/.jdks`, the usual `Program Files` vendor folders and `/usr/lib/jvm`. The game runs on Java 17, so the lowest fitting JDK wins. `note` lines name missing extras: without `jfr` and `jcmd` there is no profile report or thread dump, without `jdeps` the probe runner skips its gate check.

What setup stores in `config` of the machine folder (read one with `bash "$SKILL/scripts/live/smoke-slot.sh" get KEY`, change one with `... config KEY=VALUE`):

| Key | Value |
|---|---|
| `game_root` | The game folder. |
| `javac_dir` | The JDK's `bin` folder. |
| `work_root` | Where work folders go: `work_root/OWNER/slotN`. Default `work` in the machine folder. The Claude desktop app is an MSIX package that redirects `%LOCALAPPDATA%` to `%LOCALAPPDATA%\Packages\<app>\LocalCache\Local`; the junctions of a mod set only resolve through the real path, so setup stores that one (a `note` line says so). |
| `slots` | Games at once. Setup picks half the CPU threads, at least 1 and at most 3, unless it is set already. |
| `default`, `mem_mb`, `idle_min`, `perf_wait`, `legacy` | Slot settings: [slots.md](slots.md#slots-and-locks). |

One process can override a stored value: `GAME_ROOT`, `SMOKE_JAVAC_DIR`, `SMOKE_WORK_ROOT`. `SMOKE_LOCK_ROOT` moves the machine folder; every session on the machine must then use the same one.

The tools are built into `tools/<sum>` of the machine folder, where the sum is a checksum of their sources. A new version of the skill builds its own set next to the old one at the first run; nothing has to be rebuilt by hand, and a game that still runs keeps its files.

## A project: `smoke.sh init`

```bash
bash "$SKILL/scripts/smoke.sh" init "<mod project folder>"     # the folder that holds mod_info.json
```

It writes `native-smoke/live/project.sh` and copies `templates/live-check-mod` to `native-smoke/live/check-mod`. A project that keeps an older check mod right in `native-smoke/live` keeps it there. A second `init` changes nothing. Commit both: the checks are the project's regression suite.

`project.sh` is a bash file the runner sources. Plain assignments and the two functions, nothing else:

| Setting | Meaning |
|---|---|
| `OWNER` | The name shown in `smoke.sh status` and used for the work folder. Letters, digits, `.`, `_`, `-`. From the mod id. Runs of the project are owned by `OWNER.checks` and `OWNER.probe`. |
| `DEPS=(id ...)` | Ids of extra mods to enable, such as `lw_console`. Leave out what `mod_info.json` names as a dependency: the runner adds dependencies, and theirs, by id from `GAME/mods` and stops with `MISSING_MOD` when one is not installed. |
| `PROFILE_PKG` | The mod's own Java package prefix, so the profile covers and warns about its code. `init` takes the first part of the `modPlugin` class name; correct it when the package is deeper (`com.me.mymod`). |
| `CHECK_DIR` | The check mod folder. |
| `stage_mod()` | Optional. How the mod under test is built and staged. See below. |
| `java_args()` | Optional. Extra JVM arguments for the game, printed one per line, so an argument may hold spaces. |

### Building and staging the mod under test

The game never loads the mod from the checkout. Each run stages it in its own work folder: links to the project's top-level folders, copies of its top-level files and copies of its jars. So a run never locks a jar in the checkout, and games in other slots never share a jar.

- **Without `stage_mod`** the runner calls `scripts/live/stage-mod.sh PROJECT STAGE`, which copies the jars `mod_info.json` names as they are. A jar that is missing or more than 2 minutes older than the newest `.java` file stops the run with `STALE_JAR`: build the mod first.
- **With a compile gate, set `stage_mod`**, so the game runs exactly what the gate built and checked. `init` says so when it finds a `ReleaseBuild.java`. The function runs in the runner's shell with `PROJECT`, `STAGE` (the folder to fill) and `LIVE_SCRIPTS` (the skill's `scripts/live`) set. It must leave the mod in `$STAGE` (or set `STAGED` to the folder that holds it) and return non-zero when the build fails; its output goes to `stage.log`, and a failure ends the run with `STAGE_FAILED` and the last lines of that file.

```bash
stage_mod() {
    (cd "$PROJECT" && java jars/src/mymod/tools/ReleaseBuild.java compile) || return 1
    bash "$LIVE_SCRIPTS/stage-mod.sh" "$PROJECT" "$STAGE" "$PROJECT/out/compile/MyMod.jar"
}
```

A jar passed to `stage-mod.sh` replaces the `mod_info.json` `jars` entry with the same file name, else the first entry. Never use a build command that replaces the committed release jar. When the build config holds a game path of another machine, override it for the build (`-P`, an environment variable) or restore the file afterwards; never commit the change. One build runs per project at a time: runs in other slots wait for it.

Not linked into the stage: folders named `jars out build lib libs docs src gradle native-smoke`, folders starting with `.`, and folders that hold `mod_info.json` jar entries.

## Work folders

`work_root/OWNER/slotN/` for scripted checks, one per slot, reused between runs:

| Path | Content |
|---|---|
| `run/` | The current run: `mods/` (links and `enabled_mods.json`), `saves/`, `logs/starsector.log`, `staged/`, `check-mod/`, `results.txt`, `launch.log`, `stage.log`, `modset.log`, captures, `profile.jfr`, `profile.txt`. Cleared at the next run. |
| `runs/<time>-<groups>/` | Each run's kept results: log, `results.txt`, captures, `threads.txt`, profile. |
| `base-saves/`, `base-saves.mods` | The base save and the mod list it was made with. |

Probe outputs are `work_root/OWNER/probe-<time>-slotN/`. Remove what you no longer need with `smoke.sh clean DIR`, never by hand: [slots.md](slots.md#process-safety).

## Sharing and updating the skill

- The skill works as installed for another user on another machine: that user runs `setup` once.
- Never edit files in the skill folder to fit a machine or a project. A fix that every user needs goes into the skill itself; everything else belongs in `project.sh` or the machine settings.
- Older project runners that call `scripts/live/gamectl.exe` directly still work where the skill folder is writable: `build-tools.sh` leaves a copy there. New code asks for the path: `bash "$SKILL/scripts/live/gamectl.sh" path`.
- `smoke.sh version` and the first line of every run print the skill's version; a `results.txt` starts with it.
