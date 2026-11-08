# Misc

`indevo/other/`: standalone snippets, templates for other modders and unfinished experiments. Read it before reusing or deleting one of these classes. Nothing in this package is registered by IE; it does not cover the WIP packages, see [WIP](WIP.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The package holds code written for other mods or as copy and paste examples (pet hullmod template, PAGSM fleet respawn, unique ship enforcement, Legio "nuclear option"), small rule commands and abandoned ideas. No class here is referenced from `ModPlugin`, any CSV, `rules.csv` or other IE code, except a javadoc pointer from `PetHullmod` (grep of `jars/src` and `data` for each class name).

## Files

| File | Owner / role |
|---|---|
| `indevo/other/YourMod_PetName_PetHullmod.java` | Template for third party pet hullmods: self builds in, reads `PetStatusManager.getPet(variant)` and `Pet.getEffectFract()`, example effect -5% CR loss scaled by the fraction. Referenced in the javadoc of `indevo/industries/petshop/hullmods/PetHullmod.java:14`. See [Pets](../industries/PETS.md) |
| `indevo/other/HasItemAmountAnywhere.java` | Rule command `HasItemAmountAnywhere itemId itemType amount`: true if player cargo or any vanilla storage holds the amount |
| `indevo/other/RemoveItemAmountAnywhere.java` | Rule command: removes the amount from player cargo or the first storage that holds all of it |
| `indevo/other/PlayerHyperspaceTripTracker.java` | `NewDayListener` that records long trips toward the sector edge (`MIN_TRIP_DAYS_FOR_RECORD` 7, start beyond 20% of the way to the edge) into `$IndEvo_tripData`; uses `MagicVariables.getSectorWidthLY()` |
| `indevo/other/RemnantStationLootAdder.java` | `ShowLootListener` stub: adds `ship_bp` `"YOUR SHIP ID"` to loot of remnant fleets with a station |
| `indevo/other/SpawnDerelictFleetMembersOnDeathListener.java` | `FleetEventListener` that spawns wrecks of lost members when a fleet is destroyed in battle |
| `indevo/other/SupercapitalShipRemoveAndReplacer.java` | `@Deprecated`. Transient listener: keeps one hull tagged `supercapital` per NPC fleet, replaces the rest with combat FP via `FleetFactoryV3.addCombatFleetPoints` |
| `indevo/other/UniqueShipRemoveAndReplacer.java` | `@Deprecated`. Persistent listener per hull id: one chosen fleet keeps the hull, every other spawn gets it replaced. Keys `$IndEvo_ContainingFleet_<id>`, `$IndEvo_UniqueShipListenerInstance_<id>` |
| `indevo/other/UniqueFleetRespawnTracker.java` | `@Deprecated`, written for PAGSM. Respawns a named fleet from its source market until the player kills the flagship, then switches to `paramsAfterDefeat`; sets a memory flag on defeat. Key `$pagsm_grand_fleet_on_toilet_duty` |
| `indevo/other/PIRATE_Event_FleetManager.java` | `DisposableFleetManager` spawning up to 3 pirate fleets near systems tagged `CROWN_CONSTELLATION_SYSTEM` while player memory `$crown_ceasefire` is set |
| `indevo/other/POC_SkillCleaner.java` | `@Deprecated` runcode helper: removes `pc_` skills and `ScarPersonalityChanger` hullmods from the player fleet (`runcode indevo.other.POC_SkillCleaner.run()`) |
| `indevo/other/WhichIndustryModIndustryOptionProvider.java` | `@Deprecated`. Industry tooltip line showing the mod name from a `Whichmod_<name>` spec tag |
| `indevo/other/legioNuclearOption/NuclearOptionManager.java` | "Nuclear option" (Legio): `init()` registers the checker and combat listener; weapon list `NUCLEAR_WEAPONS` (`tachyonlance`); faction response list (pirates learn `tachyonlance`) |
| `indevo/other/legioNuclearOption/NuclearWeaponChecker.java` | `RefitTabListener`: on refit close, shows `WarningDialogue` the first time a listed weapon is fitted; list kept in `getPersistentData()` under `$tahlan_foundWeapons` |
| `indevo/other/legioNuclearOption/NuclearWeaponCombatListener.java` | Player `FleetEventListener`: after a battle with a listed weapon fitted, shows `WarBreakoutDialogue`, sets `$tahlan_isNuclear`, adds weapons to responding factions |
| `indevo/other/legioNuclearOption/WarningDialogue.java`, `WarBreakoutDialogue.java` | One shot scripts showing a message dialog with the fitted weapon names |
| `indevo/other/rescueEvent/todo` | Text note only (cryopod rescue idea); no code |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Memory key | `$IndEvo_tripData` (sector) | `PlayerHyperspaceTripTracker` |
| Memory key | `$IndEvo_ContainingFleet_<hull>`, `$IndEvo_UniqueShipListenerInstance_<hull>` | `UniqueShipRemoveAndReplacer` |
| Persistent data | `$tahlan_foundWeapons`; memory `$tahlan_isNuclear` | `legioNuclearOption` |
| Memory key | `$crown_ceasefire` (player memory), tag `CROWN_CONSTELLATION_SYSTEM` | `PIRATE_Event_FleetManager` |

## Registration and lifecycle

None. Each class documents its own intended hook in comments (for example `SupercapitalShipRemoveAndReplacer.init()` from `onGameLoad`, `SpawnDerelictFleetMembersOnDeathListener.attachToPlayerFleetIfNotPresent()` from `onGameLoad`). The two rule commands are not reachable from `rules.csv`: `indevo.other` is not in `ruleCommandPackages` (`data/config/settings.json:30`). See [Rule commands](../dialogue/RULE_COMMANDS.md).

## Flow

Not run in IE. Read the class before copying it; the issues below apply to any copy.

## Contracts

- `legioNuclearOption` depends on `indevo/dialogue/research/scripts/RefitUIOpenChecker` (`NuclearOptionManager.java:8`). Deleting that class breaks this package.
- `PlayerHyperspaceTripTracker` needs MagicLib (`MagicVariables`).

## Known issues and dormant code

| Item | State |
|---|---|
| Whole package | Unregistered (dead in IE) |
| `YourMod_PetName_PetHullmod.java:31`, `:48` | Checks `isModEnabled("indEvo")`; IE's id is `IndEvo` (`mod_info.json`). `isModEnabled` is case sensitive ([Compatibility](COMPATIBILITY.md)), so the check never matches |
| `SpawnDerelictFleetMembersOnDeathListener.java:23` | Dedupe checks `hasScriptOfClass` but adds an event listener, so every call adds another listener |
| `NuclearWeaponCombatListener.java:20` | Dedupe checks `instanceof NuclearWeaponChecker` instead of itself, so `register()` always adds |
| `UniqueFleetRespawnTracker.getTargetMarket` (`:150`) | Never updates `maxRel`, so the last non hostile market wins |
| `UniqueShipRemoveAndReplacer.isChosenFleet` (`:218`) | NPE when no chosen fleet is set |
| `PIRATE_Event_FleetManager.getCrowConstellation` (`:69`) | Returns null while `currSpawnLoc` is null, and `DisposableFleetManager` only sets `currSpawnLoc` from `pickCurrentSpawnLocation()` (`api/impl/campaign/fleets/DisposableFleetManager.java:130` in the knowledge base), so no spawn location is ever picked |
| `RemnantStationLootAdder` | Placeholder hull id `"YOUR SHIP ID"` |

## Maintenance

Update this doc when a class here is registered, moved to a feature package or deleted. A class that gets registered belongs in its feature doc instead. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
