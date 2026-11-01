# Console commands

IE's Console Commands (`lw_console`) commands: what each does, where it lives and its limits. Read it before adding or changing a command. It does not cover the features the commands touch; see the linked feature docs.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Seven commands in `indevo/console/`, listed in `data/console/commands.csv`. Console Commands reads that CSV and instantiates the class; IE has no Java registration. Console Commands is not in `mod_info.json` dependencies: without it the CSV is ignored and the classes are never loaded (inferred; the classes import `org.lazywizard.console` and only that mod reads the CSV). See [Compatibility](COMPATIBILITY.md).

## Files

| File | Owner / role |
|---|---|
| `data/console/commands.csv` | Command id, class, tags, syntax, help text |
| `indevo/console/AssignSalvageSpecials.java` | `indevo_assignSalvageSpecials`: runs `IndEvo_SalvageSpecialAssigner.init()` again and prints removed and added special totals. Campaign only. See [Salvage](../exploration/SALVAGE.md) |
| `indevo/console/SpawnStation.java` | `indevo_spawnStation <type>`: adds a custom entity at the player position, circular orbit around the system center. Types: `mining` (`Entities.STATION_MINING`), `habitat`/`orbital` (`Entities.ORBITAL_HABITAT`), `research` (`Entities.STATION_RESEARCH`), `arsenal` (`Ids.ARSENAL_ENTITY`), `lab` (`Ids.LAB_ENTITY`), each with aliases. See [Stations](../exploration/STATIONS.md) |
| `indevo/console/ClearDisruption.java` | `indevo_clearDisruption`: `setDisrupted(0)` on every disrupted industry of the current market. Market only |
| `indevo/console/SpawnRuins.java` | `indevo_spawnRuins`: if no planet has `Ids.COND_RUINS`, rolls industrial ruins on ruined planets in remnant themed systems (chance 0.2, 0.5 suppressed, 0.8 resurgent; second planet chance / 3). See [Derelicts](../industries/DERELICTS.md) |
| `indevo/console/PrintRaidTimeout.java` | `indevo_printRaidTimeout`: prints the player and AI raid timeout maps of `RaidTimeout`. See [Utils](UTILS.md#timers), [Privateer](../industries/PRIVATEER.md) |
| `indevo/console/PrintModData.java` | `indevo_printModData`: counts IE custom entities, scripts (sector, location, entity) and listeners by class name containing `indevo` or `splinterfleet`, plus ambassador count (`AmbassadorItemHelper.POST_AMBASSADOR`). Debug aid for script and listener leaks |
| `indevo/console/SetMarketGrowthProgress.java` | `indevo_setMarketGrowth <0..1>`: sets `PopulationComposition` weight between the current and next size thresholds from `CoreImmigrationPluginImpl.getWeightForMarketSizeStatic`. Market only |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Command | `indevo_assignSalvageSpecials`, `indevo_spawnStation`, `indevo_clearDisruption`, `indevo_spawnRuins`, `indevo_printRaidTimeout`, `indevo_printModData`, `indevo_setMarketGrowth` | `data/console/commands.csv` |
| Memory key | `$IndEvo_RaidTimeout`, `$IndEvo_RaidTimeout_AI` (read) | `PrintRaidTimeout` |
| Condition | `Ids.COND_RUINS` | `SpawnRuins` |

## Registration and lifecycle

`commands.csv` row -> Console Commands loads class -> `runCommand(args, context)`. Each command checks `context.isInCampaign()` or `context.isInMarket()` first and returns `CommandResult.WRONG_CONTEXT`, except `PrintModData`, which has no context check.

## Flow

To add a command:

1. Add a class in `indevo/console/` implementing `org.lazywizard.console.BaseCommand`.
2. Guard the context (`CommonStrings.ERROR_CAMPAIGN_ONLY` or `ERROR_MARKET_ONLY`).
3. Add the CSV row: id prefixed `indevo_`, tags `indevo` plus `campaign` where it applies.

## Contracts

- `PrintModData` reads `BaseCampaignEntity.getScripts()` (`indevo/console/PrintModData.java:36`), a non API class from the game jar (`obf/campaign/BaseCampaignEntity.java:98` in the knowledge base). Recheck after a game update.
- `SetMarketGrowthProgress` accepts only `0`, `1` or `d.dd` (`indevo/console/SetMarketGrowthProgress.java:27`).

## Known issues and dormant code

| Item | State |
|---|---|
| `SpawnRuins` | Does not call `RuinsManager` seeding or upgrade spec setup, only adds the condition (inferred from `industries/derelicts/utils/RuinsManager.java:152`) |
| `SpawnStation` (`indevo/console/SpawnStation.java:67`) | Casts the location to `StarSystemAPI`; in hyperspace this is a `ClassCastException` (suspected) |
| `SpawnStation` | Error text lists only mining, habitat, research |
| `PrintRaidTimeout` (`indevo/console/PrintRaidTimeout.java:36`) | NPE if a timed out system id no longer exists |

## Maintenance

Update this doc when a command is added, removed or its syntax changes. Feature behaviour belongs in the feature doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
