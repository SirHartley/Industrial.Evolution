# WIP

The unfinished package under `indevo/WIP/`: `converters` (Logistics Core colony takeover faction). Read it before resuming the feature or deleting its leftovers. It does not cover the other WIP folders excluded in `IndEvo.iml` (industries, exploration, items); those belong to their feature docs.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

`converters` is a design for a hostile AI faction (`IndEvo_converters`) that takes over a colony when a Logistics Core (`IndEvo_log_core`) runs in it, then spreads. Its code does not compile against the current source tree and is excluded from the build. It stays because `Changelog_todo` lists the paperclip factory easter egg.

The mobile colony prototype (colony ship entity, hidden system factory, stub dialog, design notes in `concept`) was never reachable and is deleted; its code is in git at `7b9ddadd` under `jars/src/indevo/WIP/mobilecolony/`.

## Files

Excluded: `IndEvo.iml` `excludeFolder` `jars/src/indevo/WIP/converters`.

| File | Owner / role |
|---|---|
| `indevo/WIP/converters/conditions/IndEvo_LogCoreCond.java` | Market condition: while a Logistics Core is installed, rolls each month for takeover with chance `0.07 * monthsWithActiveCore` (instant for a market named `davemode`); takeover moves the market to `IndEvo_converters`, stores a `ColonyRestorationPackage` (faction, comm board, admin, free port, player owned) in `$IndEvo_ColonyRestObj`, installs a gamma AI admin, zeroes all supply and demand, starts `IndEvo_ConverterWarManager` (`$IndEvo_ActiveConverterWar`). `resetColonyToOriginalState` reverses it |
| `indevo/WIP/converters/industry/IndEvo_PaperClipFactory.java` | Empty industry; AI core descriptions are the Restoration Docks text, inlined; comments describe paperclip output growing with the core, takeover fleets and saturation bombing as the cure |
| `indevo/WIP/converters/listeners/IndEvo_ConverterWarManager.java` | Empty `onNewDay()`; long comment with the spread design (outposts, hubs, invasions, Transmat reward) |
| `indevo/WIP/converters/listeners/ConverterRepRestetter.java` | Monthly reset of every faction's relation to `IndEvo_converters` to -1 |
| `indevo/WIP/converters/scripts/IndEvo_TakeoverScript.java` | Vanilla market transfer: entities, station fleets, submarkets, reapply |
| `indevo/WIP/converters/scripts/IndEvo_TakeoverScriptWithNex.java` | Same with Nexerelin: `MarketTransferIntel`, `DiplomacyManager.notifyMarketCaptured`, `SectorManager.factionEliminated`, `NexUtilsMarket.reportMarketTransferred` |
| `indevo/WIP/converters/scripts/PlayerFactionSetupNag.java` | Copy of Nexerelin's nag that opens the player faction dialog |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Faction | `IndEvo_converters` (`Ids.CONVERTERS_FACTION_ID`) | `data/world/factions/IndEvo_converters.faction`, `factions.csv:8` |
| Nexerelin faction config | `IndEvo_converters` | `data/config/exerelinFactionConfig/mod_factions.csv`, `IndEvo_converters.json` |
| Rules | `greetingConverter` and the converter comm block | `data/campaign/rules.csv:421` to `:461` |
| Special item | `IndEvo_log_core` | `data/campaign/special_items.csv:20`; drop groups `data/campaign/procgen/drop_groups.csv:2`, `:75` |
| Sound | `IndEvo_converter_hostile` | `data/config/sounds.json:14` |
| Memory key | `$IndEvo_LogCoreCondKey`, `$IndEvo_ActiveConverterWar`, `$IndEvo_ColonyRestObj` | `IndEvo_LogCoreCond` (excluded code) |

## Registration and lifecycle

- Nothing registered. `ModPlugin.onNewGameAfterEconomyLoad` has `//ConverterRepRestetter.resetConverterRep();` commented out (`indevo/utils/ModPlugin.java:303`). No `market_conditions.csv` row for the log core condition and no `industries.csv` row for the paperclip factory (grep of `data/campaign`).

## Flow

Live today: the Logistics Core only boosts the Centralization Bureau (`indevo/items/installable/LogCoreEffect.java`); nothing calls `IndEvo_LogCoreCond`. See [Installables](../items/INSTALLABLES.md).

Intended converters flow (from comments and code): core installed -> condition counts months -> takeover roll -> `transferMarketToConverters` -> war manager spreads -> player recaptures -> `resetColonyToOriginalState`.

## Contracts

- converters cannot be built as is: package declarations are `indevo.converters.*` while files live in `indevo/WIP/converters/`, and imports name classes that no longer exist (`indevo.IndEvo_StringHelper`, `indevo.ids.IndEvo_ids`, `indevo.ids.IndEvo_Items`, `indevo.plugins.timers.IndEvo_newDayListener`, `indevo.IndEvo_IndustryHelper`). Current owners: `StringHelper`, `Ids`, `ItemIds`, `NewDayListener` ([Utils](UTILS.md)); `IndEvo_IndustryHelper.addOrRemoveSubmarket` has no current equivalent.
- `IndEvo_TakeoverScriptWithNex` and `PlayerFactionSetupNag` import Nexerelin unguarded; any revival needs the `isModEnabled("nexerelin")` split that `IndEvo_LogCoreCond` already has.
- The converter faction, rules block, item and Nexerelin config ship in every build even though the code is excluded.

## Known issues and dormant code

| Item | State |
|---|---|
| `indevo/WIP/converters/` | Excluded by `IndEvo.iml`, does not compile |
| `IndEvo_LogCoreCond.reportEconomyTick` (`:277`) | Increments months on every tick in dev mode, on top of the month end increment |
| `IndEvo_LogCoreCond.apply` (`:48`) | Adds itself as a listener on every apply without dedupe |

## Maintenance

Update this doc when the package is resumed, moved out of `WIP`, or deleted, and when its data rows change. When a feature leaves WIP it gets its own feature doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
