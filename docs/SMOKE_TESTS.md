# Smoke tests

How IE is run inside the real game to see or exercise a change: setup on the user's machine, the `native-smoke/` wrapper and probes, and how results are read and reported. Read it before writing a probe or starting a run. It does not govern the compile gate ([CLAUDE.md](../CLAUDE.md#required-compile-gate)), the release jar ([RELEASE.md](RELEASE.md)) or how the runner works inside (the `starsector-native-smoke` skill owns that).

A smoke test supplements the compile gate and is not a merge requirement. When one ran, the PR records it.

## When to use

| Need | Mode |
|---|---|
| See a UI change: panels, tooltips, buttons, intel layout built from real objects | Startup probe |
| Show that a PatchLib patch ([ARCHITECTURE.md](ARCHITECTURE.md#patches)) is discovered and installed | Startup probe, then the PatchLib lines in the log |
| Verify campaign behaviour or find bugs in bulk: colonies, industries, edicts, swarms, days passing, save and reload | Live checks |
| See a screen in a running campaign, or drive one by hand: dialogs, intel, menus | Live session |

Both modes come from the `starsector-native-smoke` skill. Runs happen on the user's Windows machine only; cloud sessions have no game. A startup run opens a game window for up to a minute, so tell the user before starting one.

## Setup

- **Game:** the modding install `E:\Games\Starsector 0.98\Starsector 098a-RC8 - Modding` (it holds `starsector-core`, `mods` and `jre`). It must have run once, so its licence is in the registry. Runs never change it: mods are linked into an isolated folder per run.
- **Dependencies:** every id in `mod_info.json` `dependencies` (LazyLib, MagicLib, LunaLib, GraphicsLib, PatchLib) installed in that `mods` folder. Folder names do not matter and folders may be symbolic links: the wrapper finds each one by its `mod_info.json` id.
- **JDK 17** with `javac`, `jar` and `jdeps`, on `PATH` or in `SMOKE_JAVAC_DIR`. The compile gate refuses other versions. Git Bash for `native-smoke/live/run.sh`.
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
- Delete output folders after inspection. Never `Remove-Item -Recurse` one that still holds junctions (only a killed run leaves them). The same holds for `out/live`: `run.sh` removes its junctions on exit, `KEEP=1` and a killed shell leave them.
- The PR records the probe, the command, the result, the PNG names and what was not exercised.

## Live checks

`native-smoke/live/` is a test-only mod (`iecheck`) plus `run.sh`: a real campaign driven by script, no clicks. It builds the checked jar, stages it in `out/live/stage`, makes one new game the first time (`out/live/base-saves`), loads that save and runs the groups in one game. The game window stays behind other windows. `gamectl` only drives this run's game (`GAMECTL_PID` from `game.pid`), so other slots may run games meanwhile. The game always runs at 1600x900, where the save load and new game clicks are measured. A missing check mod or IE link in `out/live/run/mods` rebuilds the mod set.

```bash
export SMOKE_SKILL='<skill folder>' SMOKE_JAVAC_DIR='<jdk 17 bin>'
bash native-smoke/live/run.sh commerce,pets,ruins,colony,industry,senate,misc,path,parade,paradetime,content,consume,shipyard,rest,patches,relic,vpc,nextuse,newgame,arcmath,meteor
```

The full set is about 340 checks in 12 minutes and exits 0 only when all pass. Any ERROR line outside the known noise fails the run.

| Option | Meaning |
|---|---|
| `FAST=1` | Fast Rendering, boots 8 s faster. Use it while working, final run without |
| `JFR=0` | Skip the profile. On by default: `out/live/run/profile.jfr`, report in `profile.txt`, `PROFILE` lines in the run output, read with the skill's `jfr-top.sh` |
| `PROFILE_BASE=<file>` | An older `profile.jfr` of the same groups, also writes `compare.txt` |
| `LIMIT=<s>` | Seconds before the run counts as timed out (600) |
| `NOBUILD=1` | Reuse `out/live/IndEvo.jar` |
| `KEEP=1` | Leave the game open for `gamectl`. Groups `ui0` and `ui94` build the Micromanagement and Fast Build state and wait |
| `NEWGAME=1` | Start a new game instead of the base save, `SWITCHES=off` turns the content switches off first |
| `STAGE=<folder>` | Load that mod folder, an unpacked release, instead of the checkout |
| `SMOKE_SLOT=<n>` | Use a slot the session took from the skill's `smoke-slot.sh`. Without it the run takes one itself (owner `ie.live`, shared mode) and frees it at the end; `KEEP=1` keeps it for the open game. `SMOKE_LOCK=0` runs without a slot, only on a machine where nothing else starts games. `LOCKWAIT=<s>` is how long it waits for a slot (1800) |

| Group | Covers |
|---|---|
| `commerce` | One per system, alpha core tariff, Improve, Holosuite, setting off, open market, reload |
| `meteor` | Rogue planet despawn, reload mid flight, intel timing, dust clouds, radiation, type roll. Run it last: it leaves delayed swarms behind |
| `shuttle` | Shuttle traffic cost at Sindria: ms per frame at traffic 0 and 80, read with the profile; a note with the craft per route kind and a check that local flights (pickets, cruises, mining) still launch at 80. Not in the full set |
| `traffic` | Shuttle traffic at setting 80 in Aztlan, Arcadia, Askonia and Corvus: route demand, max and active per kind and per market, craft per budget group, craft near the player and within radius + 800 su of each market, world speed per kind, parked `ORBIT_PASSIVE` fleets and their speed. Notes, one check per system, and a check that Chicomoztoc has at least twice the craft of Citadel Arcadia around it. Not in the full set |
| `trafficperf` | Shuttle traffic worst case in Askonia, run with `--perf`: ms per frame at traffic 0 and 80, then a fresh scene per setting (0, 80, 120) with six parked Diktat fleets at Sindria, two 16 carrier NPC battles beside the player and escape pods near their cap. Read the profile filtered to `indevo.ambience.shuttletraffic`. Not in the full set |
| `path` | Swarm path indicator on real swarms, sampled per frame |
| `arcmath` | Real `ArcingMovementModule` over 4000 random arcs |
| `pets` | Stored pets eat from their storage, starve, die |
| `ruins` | Ruin upgrades, ruined infrastructure, Fast Build timing |
| `colony` | Governments, Supercomputer, relay comparison, ambassadors |
| `embassy` | Hire, carry, install, dismiss through the real dialog, sold, spaced, lost, Warehouse, detachments, Courier Port, monthly gain and penalties, cores, hostile, delay, recall, relocation, sabotage, setting off |
| `industry` | Toggles, Academy, Commodity Forge, transmitters, Museum, Animal Center, Restoration Docks |
| `senate` | Edicts, stable locations, Privateer slots, Cloud Painter, Salvage Yards raids |
| `misc` | Settings saved live, artillery settings, Privateer targets, console commands, Prospector notice |
| `parade` | Parade tags with a full system, launch blockers, a lost target, a normal return |
| `paradetime` | Parade length, bonus through reloads, the trip home, two museums on one colony |
| `content` | Forge World, hand built ships, Ancient Laboratory, ruins, gathering point, pet ability, Courier Port contract, Rift Generator |
| `consume` | Consumable tooltips, Tech Locator ratings, interceptor, scoop, missile hotkey by real key press |
| `shipyard` | Automated Shipyard dialog: relics from storage, lost components, failure line |
| `rest` | Combat timers, Courier Port cost, ship factory, station placement, survey story, mine spread, `indevo_spawnRuins`, pet fees, unknown pet box, Requisitions tooltip, Rift Generator target, credit stash, ship parts, detachment wrecks and auto load |
| `patches` | Every IE patch off and back on, the vanilla Commerce rule returns while off |
| `vpc` | VPC rolls per tier, drop params, loot resolver, hist3t tag, salvage drops from `indEvo_tech_addition`, invalid tier data, Assembler and Manufactory supply and demand with an ultra VPC, reload |
| `relic` | Relic payment order, construction speed up and cooldown, Edict relic removal unrest, Lattice job, recorded hullmods, Project Reforge unlock, two step Academy training, Tech Mining boost through reload and month end |
| `nextuse` | Museum, minefield and meteor settings on next use |
| `newgame` | IE content on the base save. With `NEWGAME=1 SWITCHES=off` a new game without any |
| `boot` | Just the load. With `STAGE=<folder>` the release layout |
| `uiacademy` | Academy panel views ([Panel views](#panel-views-and-the-layout-audit)): officers, scrolled list, Neural Interface and alpha core slots, many skills, trainee, paused, Accelerate used and short, blocked Train and Retrieve, NPC Academy without credits, administrators, governor, admin trainee, worst case, empty. Not in the full set |
| `uidetach` | Detachment panel views: setup, blocked, Take, 30 ships, worst case, deployed, met in space, attack order, dormant and returning, empty. Not in the full set |
| `uicourier` | Courier Port panel views: contract list, by load, stockpiles, invalid contract, Remove confirm, beta and alpha core, new contract steps, 11 storages and the expanded `+N` tiles, 60 contracts, worst case, empty. Not in the full set |
| `uisalvage` | Salvage Yards panel views: empty assembly, D-mod and S-mod tabs without and with matching ships, blocked S-mod target, a confirmed order, sliders, 12 scrapped ships, over budget, no credits, story point confirm, Accelerate armed, 40 lines and 5 orders in progress, Wreck Registry empty, few and filled, worst case. Not in the full set |
| `uiedict` | Senate Edict panel views: preview with met requirements, an unmet Edict, Labour Restrictions on a colony with 12 industries, active before and after the minimum term, bribe short of Relic Components, one colony view, worst case, a Senate with no colony in range. Not in the full set |
| `uilab` | Ancient Laboratory relic panel views: both tabs empty, six Forge Templates, a level 1 template, short of Relic Components, nothing picked, automated ships, result line, 40 ships, worst case. Not in the full set |
| `uihub` | Engineering Hub panel views: hullmods empty, 8 and 60 recovered hullmods, short of Relic Components, reverse engineering idle, in work with progress, relic payment, a hull below 100%, 200 cruiser blueprints, 40 dropped off ships, blocked Finish and Accelerate, worst case. Not in the full set |
| `uistyle` | One dialog with kit headings, the panel strips (heading note, Edict, Research card, Detachment, title), every button kind and the green and amber fills at 100, 75 and 60 % beside widgets built the way vanilla intel builds them, sizes logged as `STYLE` notes. Not in the full set |
| `uiresearch` | Research Tracts side panel at the Galatia Academy (`UiViews.side`, captured as the whole screen): cards, Info open, Project Reforge without a Corrupted Nanoforge, item rows, scrolled with a Stock click, empty. Not in the full set |
| `holdacademy` ... `holdresearch`, `holdvanilla` | Build one panel's state, open it (`holdvanilla`: a player colony with vanilla industries and 10 officers, no panel), log the click targets and leave the game open (`HELD`) for `gamectl`. Not in the full set |

Day skips run at 60x. Fleets can overshoot the 50 unit arrival check at that speed and never land, so set `Time.mult = 3f` around a fleet arriving somewhere.

A new check goes in the group of its feature, or in a new `<Feature>Checks.java` listed in `Groups.java`. Run it on the broken build first, so it is known to fail. Repeat anything random in one step: the rogue planet bug passed a single spawn and failed 348 of 4000 arcs. How steps, day skips and reloads work is in the skill's `references/live-game.md`.

## Live sessions

Use the skill's live mode (`references/live-game.md`) against the modding install. The mod set links the repository folder as it is, so IE loads `jars/IndEvo.jar` (the `mod_info.json` entry, gitignored): build the IDE artifact or copy the checked jar from `out/release-build-*` there first. Console commands for a session go in the skill's test mod, never in IE.

## Panel views and the layout audit

The `ui<panel>` groups open each view of a kit panel through its static `show` with a view state (`UiViews.view`): open, wait 120 frames (the dialog fades in for about a second, earlier captures show its noise texture), optionally act on the drawn panel (a click through the `Runnable` the kit stores as button data, `UiAudit.click`), audit, capture `check-<panel>-<view>.png`, close through the dialog's `dismissCustomDialog(1)`. The note `rect <view> x y w h` is the dialog rectangle in screen pixels from the top left, for cropping.

`UiAudit` walks the component tree from the custom dialog under the current interaction dialog (dialogs keep their content in `getInnerPanel`, which `getChildrenCopy` does not list) and reports, with x and y from the content panel's top left:

- two labels whose drawn text rectangles intersect (width from `computeTextWidth`, line height from the label, position from its alignment),
- one line text wider than its box or wrapping, wrapped text taller than its box,
- text, buttons, custom panels and leaf components outside the panel, or outside their scroll area horizontally (vertical overflow inside a scroll area is scrolling),
- sibling buttons that partly intersect (a button fully inside another one, like a stepper on a row, is allowed),
- a labelled button that its scroll area cuts while every scroll area above it is at offset 0 (what the player sees first),
- text that ends in `...` (a `Ui.fit` cut; button labels such as "Cargo..." are button text, not labels). Over long test names registered with `UiAudit.acceptLong` (longer than the game or the player can produce) are logged as `accepted: long name`, not as findings.

Hidden components (opacity 0) and anything fully scrolled out of view are skipped; tooltips are not in the tree. A view passes only with zero findings, and every capture is still opened and compared with its board: the audit does not see text over icons or wrong wording.
