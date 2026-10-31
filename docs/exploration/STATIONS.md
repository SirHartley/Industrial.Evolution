# Derelict stations

World-gen placement of the IE salvageable stations: orbital laboratory, arsenal station and derelict pet center. Read it before changing where or how often these stations spawn. It does not cover the salvage specials assigned to them; see [Salvage](SALVAGE.md). Pet loot belongs to [Pets](../industries/PETS.md), minefields around arsenal stations to [Minefields](MINEFIELDS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

On a new game `DerelictStationPlacer` counts the vanilla salvageable stations (mining, research, orbital habitat), multiplies by `IndEvo_derelictStationAmount`, and splits that budget into lab, arsenal and pet center targets. It then walks unpopulated systems in random order and rolls per system theme. The stations are plain salvageable custom entities: interaction, loot and defenders come from vanilla salvage through their `salvage_entity_gen_data.csv` rows. Arsenal stations in planet orbit also get a minefield.

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/stations/DerelictStationPlacer.java` | All placement logic (`init`, `addLabStations`, `addArsenalStation`, `addDerelictPetCenter`, `addStation`, `addEntity`, `addSalvageEntity`), plus dev helpers `addDebugStation` and `addStationToFirstPlanet` |
| `indevo/console/SpawnStation.java` | Console command that spawns an arsenal or lab station at the player (see [Console](../core/CONSOLE.md)) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Custom entity | `IndEvo_orbitalLaboratory` (`Ids.LAB_ENTITY`) | `data/config/custom_entities.json:1118`; tags `has_interaction_dialog, salvageable, neutrino, salvor_orbital` |
| Custom entity | `IndEvo_arsenalStation` (`Ids.ARSENAL_ENTITY`) | `data/config/custom_entities.json:1094` |
| Custom entity | `IndEvo_abandonedPetCenter` (`Ids.ABANDONED_PETSHOP_ENTITY`) | `data/config/custom_entities.json:1142` |
| Salvage gen rows | same three ids: rating, detection range 2200, `DISCOVERABLE`, XP, `drop_value`, `drop_random` | `data/campaign/procgen/salvage_entity_gen_data.csv:2`, `:9`, `:18` |
| Drop groups | lab: `indEvo_tech`, `indEvo_consumables`, `indEvo_missiles`; arsenal: `indEvo_weapons`, `indEvo_consumables`, `indEvo_missiles_always`, `indEvo_missiles`; pet center: `indEvo_petStuff`, `indEvo_pets_normal`, `indEvo_pets_rare`, `indEvo_consumables` | `data/campaign/procgen/drop_groups.csv` |
| Descriptions | `IndEvo_arsenalStation`, `IndEvo_orbitalLaboratory`, `IndEvo_abandonedPetCenter` (CUSTOM) | `data/strings/descriptions.csv:4-5`, `:26` |
| Setting | `IndEvo_Enable_Derelicts` | `LunaSettings.csv:14`; checked in `ModPlugin.onNewGameAfterEconomyLoad` before calling the placer |
| Setting | `IndEvo_enable_derelict_stations` | `LunaSettings.csv:18`; `DerelictStationPlacer.init:43` |
| Setting | `IndEvo_derelictStationAmount` (0.2) | `LunaSettings.csv:19`; overwrites static `ADDITIONAL_STATION_AMT` (`:44`) |
| Setting | `IndEvo_pets` | `LunaSettings.csv:13`; zeroes the pet center budget (`:68-71`) |
| Memory | `MemFlags.SALVAGE_SEED` per entity | `addSalvageEntity:275-276` |

## Registration and lifecycle

`ModPlugin.onNewGameAfterEconomyLoad()` (`indevo/utils/ModPlugin.java:321-324`): if `IndEvo_Enable_Derelicts`, `new DerelictStationPlacer().init()` then `new IndEvo_SalvageSpecialAssigner().init()`. The order matters: the special assigner runs over all salvageables after the stations exist, so new stations get IE specials (see [Salvage](SALVAGE.md)). Runs once per new game; nothing is stored on the placer.

Order: `onNewGameAfterEconomyLoad -> DerelictStationPlacer.init -> IndEvo_SalvageSpecialAssigner.init -> spawnMineFields`.

## Flow

`init()` (`:42-133`):

1. Budget: count `Tags.SALVAGEABLE` entities of type `station_mining`, `station_research`, `orbital_habitat`; times `ADDITIONAL_STATION_AMT`; lab = round(x * 0.4), arsenal = round(x * 0.45), pet = round(x * 0.5).
2. Per-system chance: `amount * 3 / systemCount` per type.
3. Shuffle all star systems. Skip systems with markets, systems in `getStarSystemsWithMarkets()`, and `theme_core_unpopulated`/`theme_core_populated`.
4. Per theme tag (exact match on vanilla `Tags.THEME_*`):

| Theme | Lab | Arsenal | Pet center |
|---|---|---|---|
| `THEME_MISC` | chance x0.5 | chance x1 | chance x0.5 |
| `THEME_REMNANT` | chance x3 | chance x1 | |
| `THEME_RUINS` | | chance x2 | chance x2 |
| `THEME_DERELICT` | chance x0.5 | | |
| no tag containing "theme" | chance x1 | | |

5. Stop when all budgets reach zero.

Placement:

- Lab: vanilla `BaseThemeGenerator.getLocations` with weights 5 each for small nebula, gas giant orbit, near star, L point, in ring; marks the orbited planet used.
- Arsenal: `pickCommonLocation(random, system, 100, true, null)`. If minefields are enabled and the location is `PLANET_ORBIT`, the condition `IndEvo_mineFieldCondition` is added to the orbited planet's market (or the parent planet if the focus is a moon) and `addMineField()` is called directly (`:143-157`).
- Pet center: `pickCommonLocation(random, system, 100, false, null)`.
- `addStation()` adds the entity through vanilla-style `addSalvageEntity()` (spec lookup via `SalvageEntityGeneratorOld.getSalvageSpec`, radius override, discoverability from the spec type, salvage seed, detected range mod "gen"), then `convertOrbitPointingDown` unless it orbits a star at 5000 or more.

## Contracts

- `random` is `StarSystemGenerator.random`, so placement follows the sector seed.
- The station ids are used elsewhere: `IndEvo_SalvageSpecialAssigner.pickSpecialFor` has per-type special tables; `LocatorSystemRatingUpdater.salvageEntityMap` rates lab 20 and arsenal 10 for the Tech Locator ([Consumables](../items/CONSUMABLES.md)); meteor code references them too ([Meteor](METEOR.md)).
- No rules.csv rows: the stations use the vanilla salvage dialog through the `salvageable` tag and their `salvage_entity_gen_data.csv` row (defender columns empty).

## Known issues and dormant code

| Item | State |
|---|---|
| "skip systems that already have stations" (`:88-97`) | Checks `token.getId()` against entity type ids; ids are generated, so (inferred) this never skips |
| `addArsenalStation` | NPE at `:143` if `pickCommonLocation` returns null (`loc.type`); `addDerelictPetCenter` returns true even when no location was found, consuming budget |
| `addDebugStation`, `addStationToFirstPlanet` | Dev helpers, no caller in the codebase |
| `ADDITIONAL_STATION_AMT` | Static, overwritten from settings on each `init` |
| Changelog_todo | "Add icons to custom explo stations" (`Changelog_todo:54`); "Pet stations: Fix the -1c pet food cost in monthly report" (`:58`); "Pet stations: Add main menu dialogue" (`:66`) |

## Maintenance

Update this doc when placement rules, the station entity or salvage rows, or the settings above change. Specials on these stations go in [Salvage](SALVAGE.md); minefield behaviour in [Minefields](MINEFIELDS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
