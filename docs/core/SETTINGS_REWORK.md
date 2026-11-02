# Player settings rework

**Status: approved plan, in progress on PR #22.** Philipp took every recommendation on 2026-11-01 ([Decisions](#decisions)) and approved the Quick Settings split. No open questions. This document records how IE moves from the `Settings` string reader to the `Setting` enum and `PlayerSettings` cache that Catch.Release uses, plus the open questions and the work each step needs. Read it before changing the settings mechanism.

It does not govern setting values, balance or help text wording. [SETTINGS.md](SETTINGS.md) stays the authority on the current mechanism until Stage 0 lands; feature docs own what each setting does; help text goes through the house-style `player-text.md`. Move implemented technical detail to SETTINGS.md and [ARCHITECTURE.md](../ARCHITECTURE.md) and tick the plan here.

Checked against Starsector 0.98a-RC8, LunaLib 2.0.1 (`lib/LunaLib.jar`, byte-identical to the jar in Catch.Release `lib/Lazylib_lunalib.zip`), IE `c51d05e` (PR #18 head) and Catch.Release `03923f43`. LunaLib facts are from that jar, decompiled with the knowledge skill's Vineflower.

## Framing

- Scope: how settings are declared, read, validated and applied while a game runs. Not in scope: what a setting does, default values, rescaling raw values to percent.
- Save compatibility is not required (Philipp, 2026-11-01). Settings are not in the save anyway: LunaLib keeps them in the common file `LunaSettings/IndEvo.json` (`LunaSettingsLoader.loadSettings`). Keep every `fieldID`, so player choices survive the rework.
- Each stage achieves one thing:
  - Stage 0: one cached owner for every read, no visible change.
  - Stage 1: typed call sites, correct data (bounds, broken toggle) and content blocks.
  - Stage 2: every setting applies without a restart; each says when it applies.
  - Stage 3: headless checks, complete help text, Quick Settings and the new tab layout.

## Catch.Release model

| Owner | Role | Source |
|---|---|---|
| `config/Setting` | Enum, one constant per LunaLib field: `id`, `fallback`, `min`, `max`, radio `choices`. `sanitize(Object)` returns the fallback for a missing, wrong type or non-finite value, clamps numbers to the bounds and accepts only listed radio choices. Typed reads `value()`, `mult()` (value / 100), `enabled()`. | `Setting.java:135-182` |
| `config/PlayerSettings` | `install()` adds the LunaLib listener once (`hasSettingsListenerOfClass`) and reloads; `ModPlugin` calls it from `onApplicationLoad` and `onGameLoad`. `reload()` forces LunaLib's lazy load, reads the raw `CommonDataJSONObject` for its own mod id, resolves (sanitize, then presets), writes changed values back and saves, rebuilds a status Text row, syncs the open settings UI and applies campaign side effects (bar event weights, quest markers). Runtime callers read an `EnumMap`; derived values have one helper each (`effects()`, `soundVolume(id)`). | `PlayerSettings.java:44` (install), `:70` (reload), `:95` (side effects), `:127` (resolve); `ModPlugin.java:71`, `:97` |
| `config/LunaSettingsBridge` | After a preset, pushes the resolved values into LunaLib's retained pending list and mounted controls, for its own mod id only. Without it, a later Save restores the preset or the old slider value. | `LunaSettingsBridge.java:16-51`, CR `docs/UI.md#lunalib-settings` |
| `tools/PlayerSettingsChecks` | Headless: every enum constant has a CSV row with the same default, bounds and choices; help text present; `%%` survives LunaLib's `addPara` formatting; mod icon is square; presets apply once; retained UI values sync; Starsector's checked `JSONException` is on the classpath. | `PlayerSettingsChecks.java:69` |
| `data/config/LunaSettings.csv` | Nine tabs. `fieldDescription` owns the help text and says when a change applies ("Applies to your next catch."). | |

## IE today

Mechanism detail: [SETTINGS.md](SETTINGS.md). Findings that drive this plan:

1. **No owner object.** 152 string constants in `indevo/utils/helper/Settings.java`; 235 `Settings.getX` calls in 82 files. Each call goes to LunaLib, which looks the key up in its JSON object. LunaLib returns null for a missing key or mod (`LunaSettings.getBoolean` and siblings catch and return null), and `Settings` unboxes it, so a wrong id is a `NullPointerException` at the read site.
2. **Restart needed.** 67 static fields in 18 classes copy a setting at class load (64 `static final`, plus `MortarProjectileEntityPlugin.EFFECT_SIZE` and two in `ShipComponentLootManager`). The General tab says so in a Text row (`IndEvo_text_application_note`: "Restarting your game client after changing settings is advised"). Affected: `MeteorSwarmManager` (16 fields), `MuseumConstants` (14, used in 40 places), artillery (`CampaignAttackScript`, `WatchtowerEntityPlugin`, `IndEvo_ArtilleryDefenderGen`, five projectile classes), `ShippingCostCalculator`, `MineBeltTerrainPlugin`, both salvage yard dialogs, `ShipComponentLootManager`, `ChamelionIndustry`, `CrucibleSpawner`, `GachaStationSpawner`.
3. **Nothing reacts to Save.** IE has no `LunaSettingsListener`. The pet ability is added or removed only in `onGameLoad` (`ModPlugin.java:181-201`).
4. **Bounds.** 21 `Int`/`Double` rows have no `minValue` or `maxValue`. LunaLib then uses 0 and 100 (`LunaSettingsLoader.loadDefault`), and its text field clamps a typed value to the max (`LunaUITextField`, Int, Float and Double branches). `IndEvo_courier_baseFee` defaults to 1000 with that implied max of 100: once a player edits it, 1000 is out of reach. `IndEvo_courier_costPerLyMult` writes its bounds as `0.1f` and `2f` (parsed by `Float.parseFloat`, which accepts the suffix). No min/max pair is checked against its partner.
5. **Help text.** 27 setting rows have an empty `fieldDescription`: 11 artillery rows, `IndEvo_Minefield_PhaseShipImpactMult`, `IndEvo_RG_cooldownTime`, the eight hull deconstructor and forge day counts, six academy rows. Several others are informal ("ect", "I think").
6. **Dead toggle.** `IndEvo_IntArray` has no reader. `MilitaryRelay` serves both relay industries and reads only `IndEvo_ComArray` (`MilitaryRelay.java:306`, `:312`), so the Interstellar Relay toggle does nothing and the Military Relay toggle also blocks the upgrade. Not in the suspected bug list yet.
7. **Dormant setting.** `IndEvo_CrucibleNum` is shown, but nothing calls `CrucibleSpawner.spawn()` ([CRUCIBLE.md](../exploration/CRUCIBLE.md)).
8. **Dead fallback.** `Settings` falls back to `Global.getSettings()` when LunaLib is off, but LunaLib is a hard dependency. `settings.json` holds 102 of those fallback keys, is missing 50 ids and disagrees on one (`IndEvo_Artillery_defense_FP` against `IndEvo_Artillery_defense_FP2`) ([SETTINGS.md](SETTINGS.md#contracts)).
9. **Tabs.** Industries holds 98 of 178 rows.

## Target design

### Shared mechanic: `Setting`

- Package `indevo/config/`, a new area as in Catch.Release.
- One constant per LunaLib field with the constant names `Settings` uses today (`EMBASSY`, `METEOR_MAX_DENSITY`) and the same `fieldID`s, so the call site swap is mechanical: `Settings.getBoolean(Settings.EMBASSY)` becomes `Setting.EMBASSY.enabled()`.
- Fields: `id`, `type` (`BOOLEAN`, `INT`, `DOUBLE`), `fallback`, `min`, `max`, `apply` (`LIVE`, `NEW_GAME`), `block`. `type`, `apply` and `block` are new against Catch.Release: IE has 41 `Int` rows, several world generation settings and content blocks. IE has no radio rows, so no `choices`.
- `sanitize(Object)` as Catch.Release; an `INT` value is also rounded to a whole number.
- Reads: `enabled()`, `value()` (float), `intValue()`. No `mult()`: IE values are raw (0.02 hit chance, 1000 credits).
- `block` is an optional parent `BOOLEAN` setting. `enabled()` returns false when the block is off, whatever the setting's own value, so turning a block back on restores the player's individual choices. One level only.

### Quick Settings and content blocks

Quick Settings is the first tab. It holds the disclaimers, one switch per content block and the few values most players change. A row lives in one tab only: rows move there with their `fieldID`, so existing player values carry over. The General tab merges into it. Split approved by Philipp, 2026-11-01:

| Block | Switch | Covers | Applies |
|---|---|---|---|
| Colony industries | New `IndEvo_block_industries` | Parent of the 19 industry toggles | Build menu at once; built industries stay; NPC placement on new games |
| Derelict tech | `IndEvo_Enable_Derelicts` | Ruins and derelict industries, ruined infrastructure, derelict stations and their salvage specials (`IndEvo_enable_derelict_stations` becomes a child) | New games |
| Artillery | `IndEvo_Enable_Artillery` | Artillery stations and watchtowers | Building, station setup and firing at once; placement on new games. Today placed stations keep firing (`CampaignAttackScript` does not read it); subtask 2.5 adds the gate |
| Minefields | `IndEvo_Enable_minefields` | Minefields around the core worlds and derelict stations | Hits at once; placement on new games |
| Meteor swarms | `IndEvo_meteor_enabled` | Swarm spawns | At once |
| Pets | `IndEvo_pets` | Pet shop, pet ability, abandoned pet shops | At once; world placement on new games |
| Consumable items | New `IndEvo_block_consumables` | Parent of market stock, combat drops and the research project | At once; items already owned stay usable |

Values in Quick Settings: `IndEvo_CommerceBalanceChanges`, `IndEvo_meteor_baseChancePerEconomyTick`, `IndEvo_Artillery_spawnWeight`, `IndEvo_derelictStationAmount`, `IndEvo_AutomatedShipyardNum`, `IndEvo_shipPickerRowCount`.

Content IE has no switch for stays as it is: split fleet and detachments, skills, research projects other than pets and consumables, survey stories, beacons, governments, installable items. Adding a switch for one of these is new feature work, not part of this rework.

Tab layout after the split (nine tabs, as Catch.Release):

| Tab | Rows |
|---|---|
| Quick Settings | Disclaimers, block switches, values above |
| Exploration & Items | Station and special settings, ship and relic component drops, consumable sub toggles |
| Meteor Swarms | Remaining `IndEvo_meteor_*` |
| Minefields | Remaining `IndEvo_Minefield_*` |
| Artillery | Remaining `IndEvo_Artillery_*`, watchtower reset |
| Industries | The 19 industry toggles, Governance, World Wonders |
| Production | Variable industries, Supercomputer, Salvage Yards, Engineering Hub, Restoration Docks |
| Derelict Industries | Derelict infrastructure, laboratory, deconstructor, forge, rift generator |
| Colony Services | Academy, Museum, Courier Port, Privateer Base |

### Shared mechanic: `PlayerSettings`

- `install()` first in `ModPlugin.onApplicationLoad` and `onGameLoad`, as Catch.Release.
- `reload()`: force LunaLib's load with any `LunaSettings.getX` call, read `LunaSettingsLoader.getSettings().get("IndEvo")`, sanitize every field, apply the pair rule, write changed values back and save, then run `applyCampaignSettings()`.
- Pair rule (new): when a max is below its min, the max is raised to the min. Pairs: artillery delay between shots, cooldown, range; meteor intensity, distance from sun, angle; museum additional credits.
- Runtime callers read the cache through `Setting`. An `EnumMap` lookup is cheap enough for per frame reads.
- `applyCampaignSettings()` runs from `onGameLoad` and from the listener. From the listener it runs only when `Global.getCurrentState() == GameState.CAMPAIGN`: the LunaLib menu also opens on the title screen, where `Global.getSector()` is the previous game's sector (knowledge skill `docs/concepts/lifecycle.md`). Catch.Release gates on `Global.getSector() == null`, which never trips there; the same gap exists in that mod. First content: the pet ability add or remove, moved out of `ModPlugin.onGameLoad`.
- LunaLib catches listener exceptions and logs them at debug level (`LunaSettings.reportSettingsChanged`), so `settingsChanged` catches and logs with `log.error` and the stack itself.
- LunaLib does not call listeners for a Save in the new game settings panel (`LunaSettingsUIModsPanel`, `if (!this.newGame)`), so the `onGameLoad` reload is required, as in Catch.Release. LunaLib still stores that Save in `LunaSettingsLoader.getSettings()`, and economy load and world generation read settings before `onGameLoad(true)`, so `onNewGame`, the first new game hook, reloads as well.
- No `LunaSettingsBridge`: presets are not planned (decision 2), and sanitize and the pair rule give the same result when LunaLib writes a retained pending value again.

### Apply timing

- After Stage 2 no setting needs a restart, and the global Text row goes.
- Help text states the new game part in the short style the CSV already uses ("Needs a new game!", "Only works on new game."), normalised to one line: "Needs a new game."
- `NEW_GAME` settings only act during world generation; their help text ends with that line: `IndEvo_enable_derelict_stations`, `IndEvo_derelictStationAmount`, `IndEvo_SpecialApplicationChance`, `IndEvo_AutomatedShipyardNum`, `IndEvo_Artillery_spawnWeight`, `IndEvo_Artillery_starsPerStationInConstellation`.
- Mixed toggles act both in world generation and live: `IndEvo_Enable_Artillery`, `IndEvo_Enable_minefields`, `IndEvo_Enable_Derelicts`, `IndEvo_pets`, and the industry toggles `NewGameIndustryPlacer` reads. They stay `LIVE`; help text names the part that needs a new game ("Placement needs a new game.").
- Values copied into saved objects keep their old value until the object is rebuilt: `CampaignAttackScript` builds its fire interval from the delay settings in its constructor (`CampaignAttackScript.java:50`); each courier shipment fixes its cost at dispatch (`Shipment.java:52`). Museum: nothing saved holds a setting. `Museum.maxParades` is a saved field but `unapplyAICoreModifiers` resets it on every `apply` (`BaseIndustry.apply` calls `applyAICoreModifiers`), and parade size settings are read when a parade spawns (`ParadeFleetProfile.java:93`, `:121`, `:127`); a parade already flying keeps its ships. House rule: new terms use the values at creation, accepted terms stay fixed. Stage 2 decides each case and records it in the feature doc.

## Dependencies

- LunaLib 2.0.1, already a hard dependency. Uses the public `LunaSettings` API plus `LunaSettingsLoader.getSettings()` from LunaLib's backend package, as Catch.Release.
- Not needed: PatchLib, reflection, any new data file.

## Decisions

Philipp, 2026-11-01: every recommendation taken, plus a Quick Settings tab.

1. **Package** `indevo/config/`, added to the CLAUDE.md area list with Stage 0.
2. **No presets.** No `LunaSettingsBridge`; one-shot actions can come later as their own change.
3. **Industries split by area**, see the [tab layout](#quick-settings-and-content-blocks).
4. **Bounds** for the 21 rows without them:

   | Settings | Range |
   |---|---|
   | `IndEvo_SpecialApplicationChance`, `IndEvo_relicComponentFPDestroyedFract`, the four `IndEvo_Minefield_HitChance_*`, `IndEvo_dailyOfficerSpawnChance`, `IndEvo_dailyAdminSpawnChance`, `IndEvo_DerInfraBuildRed` | 0 to 1 |
   | `IndEvo_courier_baseFee` | 0 to 10000 |
   | `IndEvo_derelictStationAmount` | 0 to 2 |
   | `IndEvo_relicComponentHardbattleFPAdvantage` | 1 to 5 |
   | `IndEvo_Minefield_NoHitUntilSum` | 0 to 5 |
   | `IndEvo_Minefield_CivilianShipImpactMult`, `IndEvo_Minefield_PhaseShipImpactMult`, `IndEvo_Artillery_railgun_shotFuzzMult` | 0 to 2 |
   | `IndEvo_Watchtower_faction_reset` | 1 to 365 |
   | `IndEvo_Artillery_railgun_projectilesImpactTime` | 0.5 to 60 |
   | `IndEvo_GovernmentMaxSize` | 1 to 10 |
   | `IndEvo_SYPartValueMult`, `IndEvo_SYDModMoveBaseCostMult` | 0.1 to 5 |

5. **`IndEvo_IntArray`** gates the Interstellar Relay: `MilitaryRelay` reads the toggle of its own id.
6. **`IndEvo_CrucibleNum`** row removed while crucibles are dormant; `CrucibleSpawner` uses a plain constant until they return.
7. **`settings.json`**: all 102 fallback keys removed.
8. **Quick Settings** with content block switches; split approved as in [the section](#quick-settings-and-content-blocks).
9. **Relic components are not a block**: they are integral to research projects and salvage yards. `IndEvo_PartsDropInCampaign` (ship component drops; relic components drop either way) stays a normal setting.
10. **No corruption settings**: corruption leaves IE for its own mod, in a separate change.
11. **No back-fill**: switching a block on in a running game places no world content; help text says which part needs a new game.
12. **Artillery off stops firing** (Philipp, 2026-11-01): placed stations stay, but do not fire while the block is off.

## Open questions

None.

## Implementation plan

Four stages, done in order. Stage 0 changes no visible behaviour. Help text goes through the house-style `player-text.md`. All stages land on PR #22, based on the test branch `claude/project-thread-2vk6rz`.

### Architecture

| Area | Owner (new or changed) | Role |
|---|---|---|
| Declarations | New `indevo/config/Setting` | Ids, type, default, bounds, apply timing, sanitize, typed reads |
| Cache and lifecycle | New `indevo/config/PlayerSettings` | Install, reload, listener, pair rule, write back, campaign side effects |
| Old reader | `indevo/utils/helper/Settings` | Delegates to the cache in Stage 0, deleted in Stage 1 |
| Startup | `indevo/utils/ModPlugin` | `PlayerSettings.install()` first in both hooks; pet ability moves to `applyCampaignSettings()` |
| Checks | New `indevo/tools/SettingsChecks` | Headless CSV, enum and sanitize checks |

### Data files this changes

| File | Change |
|---|---|
| `data/config/LunaSettings.csv` | Explicit bounds (decision 4), help text, apply notes, no global restart note, two new block rows, Quick Settings and nine tabs, crucible row removed |
| `data/config/settings.json` | 102 `IndEvo_*` fallback keys removed; amounts, list paths, sprites and `IndEvo_ToggleMagicRandomSelector` stay |

### Stages

Each stage depends on the stages before it. Within a stage, do the subtasks in order, one commit each. Tick a subtask in its own commit. Every stage finishes with the build gate ([CLAUDE.md](../../CLAUDE.md#building)), the checks it names, its Changelog entries and its SETTINGS.md and ARCHITECTURE.md routes.

**Stage 0: one owner.** No visible change.
- [x] 0.1 Drop the fallback: `Settings` reads LunaLib only; remove the 102 keys and the stale `IndEvo_Artillery_defense_FP` from `settings.json` (decision 7). Done when: no LunaLib id is left in `settings.json`.
- [x] 0.2 Add `indevo/config/Setting` (150 constants from the CSV as it is; rows without bounds get infinite bounds until 1.2; `block` comes with 1.5, `apply` with 2.4) and `PlayerSettings` (install, reload, listener, cache, write back, error logging). `ModPlugin` calls `install()` first in `onApplicationLoad` and `onGameLoad` and `reload()` in `onNewGame`; `config` joins the CLAUDE.md area list. `Settings.getBoolean`, `getFloat` and `getInt` read the cache by id.
  - Done when: values match the old reads; a Save in the campaign settings menu updates the cache (dev log line).

**Stage 1: typed reads and correct data.**
- [x] 1.1 Replace the 231 `Settings.getX(Settings.NAME)` calls with `Setting.NAME.enabled()`, `value()` or `intValue()`; delete `indevo/utils/helper/Settings.java`. Two reads used the wrong type: `SY_PART_VALUE_MULT` (Double, default 1.2) went through `getInt` and truncated to 1, now `value()` (`IndEvo_InitSYCustomProductionDiag.java:73`); `ARTILLERY_STARS_PER_STATION` (Double) is a count and keeps `intValue()` (`ArtilleryStationPlacer.java:85`). Done when: `grep -rn 'Settings\.get' jars/src` finds only `Global.getSettings()` calls.
- [x] 1.2 Explicit bounds for the 21 rows (decision 4), plain numbers for `IndEvo_courier_costPerLyMult`; `Setting` mirrors them.
  - Done when: the courier base fee slider reaches 1000.
- [x] 1.3 Pair rule in `PlayerSettings`.
- [x] 1.4 `IndEvo_IntArray` gates the Interstellar Relay (decision 5).
- [x] 1.5 Content blocks: `Setting.block`, rows `IndEvo_block_industries` and `IndEvo_block_consumables`, children per the block table, `IndEvo_enable_derelict_stations` under `IndEvo_Enable_Derelicts`.
  - Done when: with Colony industries off none of the 19 toggled industries shows in the build menu; back on, each industry follows its own toggle again. The new rows sit in the General tab until 3.3 makes it Quick Settings.
- [x] 1.6 Remove the `IndEvo_CrucibleNum` row and setting (decision 6).

**Stage 2: live settings.**
- [ ] 2.1 Replace the 67 class load copies with use time reads, one commit per feature (meteors, museum, artillery, courier port, minefields, salvage yards, loot, small cases). `MuseumConstants` keeps its fixed constants only.
  - Done when: changing a meteor or artillery value in a running campaign applies without a restart.
- [ ] 2.2 Saved objects: decide each case (fire intervals, contracts, parades), apply it and record it in the feature doc.
- [ ] 2.3 `applyCampaignSettings()` with the `GameState.CAMPAIGN` gate: pet ability add or remove on Save.
- [ ] 2.4 `apply` metadata for the `NEW_GAME` list, apply notes in help text, remove `IndEvo_text_application_note`.
- [ ] 2.5 `CampaignAttackScript.advance` returns at once while `IndEvo_Enable_Artillery` is off (decision 12); the script stays attached, so firing resumes when it is switched back on.
  - Done when: with Artillery off in a running campaign, no placed station fires; back on, they fire again.

**Stage 3: checks and text.**
- [ ] 3.1 `indevo/tools/SettingsChecks`, run with the build gate, outside the release jar:
  - CSV against enum: unique ids, a row for every constant and a constant for every non Header, non Text row, type, default, explicit bounds for every number, default inside its bounds.
  - Text: help text present; `fieldName`, `fieldDescription` and Text rows survive `String.format` (as Catch.Release); `NEW_GAME` help text contains "needs a new game".
  - Logic: sanitize (missing, wrong type, NaN, infinity, clamp, Int rounding), the pair rule, Starsector's checked `JSONException`.
  - Blocks: every block is a `BOOLEAN` in Quick Settings and has no block itself.
  - Source: no `LunaSettings.get` outside `indevo/config`.
- [ ] 3.2 Help text for the 27 empty rows and a pass over the informal ones, in the CSV's existing short style. Keep the limits the old `settings.json` comments stated: missile projectiles per shot must be even, railgun projectiles need a higher fuzz mult, Supercomputer runtimes should be multiples of 31 (tooltips), Salvage Yards off also stops ship component drops, Embassy off also stops ambassador scripts.
- [ ] 3.3 Quick Settings and the nine tab layout, rows moved with their ids.

Steps that change what the player sees end with a live test by Philipp: change a setting in a running campaign, Save, and check it applies; start a new game with changed new game settings.

**Behaviour to keep in mind.**
- Counts in this document are from `c51d05e`, before the corruption removal; that change drops `IndEvo_Corruption` and `IndEvo_CorruptionCutoff`.
- Mod order: IE's `onApplicationLoad` runs before LunaLib's (hooks run in mod `name` order, `lifecycle.md`), so `reload()` must force LunaLib's load; LunaLib then skips its own (`getHasLoaded`).
- LunaLib's `onDevModeF8Reload` reloads settings without calling listeners, and runs after IE's hook. In dev mode, Save again or reload the game after F8.
- LunaLib writes defaults for missing keys (`saveDefaultsToFile`). A renamed `fieldID` resets that setting for every player.
- Minefields off: hits stop at once (`MineBeltTerrainPlugin.applyEffect`). On the next save `beforeGameSave` removes untouched mines and `restoreRemovedEntities` does not put them back, so they leave the running game too; mines with memory stay. Back on: vanilla calls `regenerateAsteroids()` on every `AsteroidSource` at each game load (`CoreLifecyclePluginImpl.onGameLoad`, `regenAsteroids`), which recreates `params.numMines` mines. Removed mines return at the next load, not at once. Calling `createMines` from the listener instead would double the mines that were never removed.
