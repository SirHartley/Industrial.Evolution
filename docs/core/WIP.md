# WIP

The two unfinished packages under `indevo/WIP/`: `converters` (Logistics Core colony takeover faction) and `mobilecolony` (colony ship). Read it before resuming either feature or deleting its leftovers. It does not cover the other WIP folders excluded in `IndEvo.iml` (industries, exploration, items); those belong to their feature docs.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

`converters` is a design for a hostile AI faction (`IndEvo_converters`) that takes over a colony when a Logistics Core (`IndEvo_log_core`) runs in it, then spreads. Its code does not compile against the current source tree and is excluded from the build. `mobilecolony` is the start of a player colony that lives on a moving entity; it compiles, its campaign plugin is registered every load, but nothing creates a mobile colony, so it never runs in a game.

## Files

### converters (excluded: `IndEvo.iml` `excludeFolder` `jars/src/indevo/WIP/converters`)

| File | Owner / role |
|---|---|
| `indevo/WIP/converters/conditions/IndEvo_LogCoreCond.java` | Market condition: while a Logistics Core is installed, rolls each month for takeover with chance `0.07 * monthsWithActiveCore` (instant for a market named `davemode`); takeover moves the market to `IndEvo_converters`, stores a `ColonyRestorationPackage` (faction, comm board, admin, free port, player owned) in `$IndEvo_ColonyRestObj`, installs a gamma AI admin, zeroes all supply and demand, starts `IndEvo_ConverterWarManager` (`$IndEvo_ActiveConverterWar`). `resetColonyToOriginalState` reverses it |
| `indevo/WIP/converters/industry/IndEvo_PaperClipFactory.java` | Empty industry; comments describe paperclip output growing with the core, takeover fleets and saturation bombing as the cure |
| `indevo/WIP/converters/listeners/IndEvo_ConverterWarManager.java` | Empty `onNewDay()`; long comment with the spread design (outposts, hubs, invasions, Transmat reward) |
| `indevo/WIP/converters/listeners/ConverterRepRestetter.java` | Monthly reset of every faction's relation to `IndEvo_converters` to -1 |
| `indevo/WIP/converters/scripts/IndEvo_TakeoverScript.java` | Vanilla market transfer: entities, station fleets, submarkets, reapply |
| `indevo/WIP/converters/scripts/IndEvo_TakeoverScriptWithNex.java` | Same with Nexerelin: `MarketTransferIntel`, `DiplomacyManager.notifyMarketCaptured`, `SectorManager.factionEliminated`, `NexUtilsMarket.reportMarketTransferred` |
| `indevo/WIP/converters/scripts/PlayerFactionSetupNag.java` | Copy of Nexerelin's nag that opens the player faction dialog |

### mobilecolony (not excluded, compiled)

| File | Owner / role |
|---|---|
| `indevo/WIP/mobilecolony/concept` | Design notes: mobile and deployed modes, movement orders, upgrades, colony management, marine exploration with events |
| `indevo/WIP/mobilecolony/utility/MobileColonyFactory.java` | `create()`: hidden star system (`IndEvo_MobileColonySystem_<n>`, cut off from hyperspace), hidden size 3 player faction market (`Andvaranaut`, not player owned), and an empty player fleet with `ColonyFleetAssignmentAI`; joins fleet and market. Does not spawn the fleet |
| `indevo/WIP/mobilecolony/memory/ColonyMemory.java` | `getPersistentData()` map `$IndEvo_MobileSystemMemory`: number -> `ColonyMemoryEntry` |
| `indevo/WIP/mobilecolony/memory/ColonyMemoryEntry.java` | Market, system, fleet; `joinFleetAndMarket()` sets `MemFlags.STATION_MARKET` on the fleet |
| `indevo/WIP/mobilecolony/scripts/ColonyFleetAssignmentAI.java` | `BaseAssignmentAI`: orbit the player fleet |
| `indevo/WIP/mobilecolony/entities/MobileColonyEntityPlugin.java` | Plugin of custom entity `IndEvo_MobileColony` ("Brimir-Class Colony Ship"): follows the player fleet, six engine trails through `MagicCampaignTrailPlugin` |
| `indevo/WIP/mobilecolony/plugins/MobileColonyCampaignPlugin.java` | `BaseCampaignPlugin`: entities with memory `$IndEvo_MobileColony` get `MobileColonyInteractionDialoguePlugin` |
| `indevo/WIP/mobilecolony/dialogue/MobileColonyInteractionDialoguePlugin.java` | Stub dialog with only "Leave"; restore and sacrifice options commented out (copied from another dialog) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Faction | `IndEvo_converters` (`Ids.CONVERTERS_FACTION_ID`) | `data/world/factions/IndEvo_converters.faction`, `factions.csv:8` |
| Nexerelin faction config | `IndEvo_converters` | `data/config/exerelinFactionConfig/mod_factions.csv`, `IndEvo_converters.json` |
| Rules | `greetingConverter` and the converter comm block | `data/campaign/rules.csv:421` to `:461` |
| Special item | `IndEvo_log_core` | `data/campaign/special_items.csv:37`; drop groups `data/campaign/procgen/drop_groups.csv:2`, `:72` |
| Sound | `IndEvo_converter_hostile` | `data/config/sounds.json:14` |
| Custom entity | `IndEvo_MobileColony` (tag `IndEvo_mobileColony`) | `data/config/custom_entities.json:2` |
| Memory key | `$IndEvo_MobileColony`, `$IndEvo_MobileColonyNum` (fleet) | `MobileColonyFactory` |
| Persistent data | `$IndEvo_MobileSystemMemory` | `ColonyMemory` |
| Memory key | `$IndEvo_LogCoreCondKey`, `$IndEvo_ActiveConverterWar`, `$IndEvo_ColonyRestObj` | `IndEvo_LogCoreCond` (excluded code) |

## Registration and lifecycle

- converters: nothing registered. `ModPlugin.onNewGameAfterEconomyLoad` has `//ConverterRepRestetter.resetConverterRep();` commented out (`indevo/utils/ModPlugin.java:307`). No `market_conditions.csv` row for the log core condition and no `industries.csv` row for the paperclip factory (grep of `data/campaign`).
- mobilecolony: `ModPlugin.setScriptsIfNeeded` -> `registerPlugin(new MobileColonyCampaignPlugin())` every load (`indevo/utils/ModPlugin.java:495`). `BaseCampaignPlugin.isTransient()` returns true (vanilla `BaseCampaignPlugin`), so it does not pile up in saves. `MobileColonyFactory.create()` has no caller.

## Flow

Live today: the Logistics Core only boosts the Centralization Bureau (`indevo/items/installable/SpecialItemEffectsRepo.java:79`); a commented out "experimental log core effect" below it (`:97`) calls `IndEvo_LogCoreCond`. See [Installables](../items/INSTALLABLES.md).

Intended converters flow (from comments and code): core installed -> condition counts months -> takeover roll -> `transferMarketToConverters` -> war manager spreads -> player recaptures -> `resetColonyToOriginalState`.

Intended mobile colony flow (from `concept`): `create()` -> spawn fleet and entity -> toggle mobile and deployed -> management and exploration UI. Only the factory, follow movement, trails and a stub dialog exist.

## Contracts

- converters cannot be built as is: package declarations are `indevo.converters.*` while files live in `indevo/WIP/converters/`, and imports name classes that no longer exist (`indevo.IndEvo_StringHelper`, `indevo.ids.IndEvo_ids`, `indevo.ids.IndEvo_Items`, `indevo.plugins.timers.IndEvo_newDayListener`, `indevo.IndEvo_IndustryHelper`). Current owners: `StringHelper`, `Ids`, `ItemIds`, `NewDayListener`, `MiscIE.addOrRemoveSubmarket` ([Utils](UTILS.md)).
- `IndEvo_TakeoverScriptWithNex` and `PlayerFactionSetupNag` import Nexerelin unguarded; any revival needs the `isModEnabled("nexerelin")` split that `IndEvo_LogCoreCond` already has.
- The converter faction, rules block, item and Nexerelin config ship in every build even though the code is excluded.

## Known issues and dormant code

| Item | State |
|---|---|
| `indevo/WIP/converters/` | Excluded by `IndEvo.iml`, does not compile |
| `indevo/WIP/mobilecolony/` | Compiled and partly registered, never reachable (no `create()` caller) |
| `ColonyMemory.getNext()` (`memory/ColonyMemory.java:32`) | Returns the largest used number, not the next free one, so a second colony overwrites the first (suspected bug) |
| `IndEvo_LogCoreCond.reportEconomyTick` (`:277`) | Increments months on every tick in dev mode, on top of the month end increment |
| `IndEvo_LogCoreCond.apply` (`:48`) | Adds itself as a listener on every apply without dedupe |
| `MobileColonyFactory.createMobileMarket` (`:79`) | Ignores the `id` parameter, uses `Misc.genUID()` |

## Maintenance

Update this doc when either package is resumed, moved out of `WIP`, or deleted, and when its data rows change. When a feature leaves WIP it gets its own feature doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
