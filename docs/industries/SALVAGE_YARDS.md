# Salvage Yards

The Salvage Yards industry (`IndEvo_ScrapYard`): salvage points from nearby battles and raids, hull output on top of heavy industry, the custom production contract and the D-mod transfer dialog. Read it before changing salvage point sources, output, or either dialog. Ship parts and relic component drops are in [Installables](../items/INSTALLABLES.md) (`ShipComponentLootManager`); the shared AI core/weapon storage submarket is in [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The yards collect salvage points (SP, called salvage units in the UI): fleet points lost in battles by fleets in the system, a share of raid fleet points against the system, 3 per day while any market in the system has pirate activity, and weapons fed in from the shared storage. Every `outputSalvagePointValue` SP adds one unit of hull output. Output goes into the heavy industry's `ships` supply, or into the yards' own supply with a ship quality penalty if there is none. The yards also supply metals and ship parts. The colony interaction menu opens a ship assembly contract and a D-mod transfer service.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/salvageyards/industry/SalvageYards.java` | Industry plugin. SP state and sources (`reportBattleOccurred`, `onNewDay`, `autoFeed`), monthly decay (`reportEconomyMonthEnd`), supply and demand (`applySupDemProfiles`), `getHullCapacity` for the contract dialog, AI cores |
| `indevo/industries/salvageyards/plugins/SalvageYardsOptionProvider.java` | Industry option "Visit the Salvage Yards..." opens a `RuleBasedInteractionDialogPluginImpl` firing `IndEvo_FireBaseSYOptionList` |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYCustomProductionDiag.java` | Rule command and `InteractionDialogPlugin` for the ship assembly contract; nested `YardsProductionData` (order, parts spent, cost, D-mod forecast) |
| `indevo/industries/salvageyards/intel/YardsCustomProductionIntel.java` | Production order intel and delivery; `NewDayListener` |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYMoveDModDiag.java` | Rule command and dialog plugin for moving D-mods between hulls |
| `indevo/industries/salvageyards/rules/IndEvo_HasValidShipPair.java` | Rule condition: player fleet or local storage has two hulls of one base hull with different D-mod sets |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_ScrapYard` (`Ids.SCRAPYARD`) | `data/campaign/industries.csv`, tags `heavyindustry, industry, industrial` |
| Commodities | `IndEvo_parts`, `IndEvo_rare_parts` (`ItemIds.PARTS`, `RARE_PARTS`) | `data/campaign/commodities.csv` |
| Submarket | `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`) | added by `SharedSubmarketUser`; see [Small industries](SMALL_INDUSTRIES.md) |
| Rules | `IndEvo_SalvageYardsBaseMenu`, `IndEvo_SalvageYardsColonyOptionMenu`, `IndEvo_IntitiateYardsMenu`, `IndEvo_FireSYBaseMenuFromIndustryOption`, `IndEvo_SYCustomProductionPanel(_init)`, `IndEvo_SYMoveDMods(_init, _disabled)`, `IndEvo_SYLeave`; triggers `IndEvo_YardsBaseMenu`, `IndEvo_FireBaseSYOptionList` | `data/campaign/rules.csv` |
| Memory key | `$IndEvo_closeDialogueOnNextReturn` | interaction target memory, set by the option provider |
| Memory key | `$IndEvo_HasSelectedSP_SY` | market memory: story point double capacity bought but not yet used |
| Setting | `IndEvo_ScrapYard` | enables the industry; `isFunctional()` also checks it |
| Setting | `IndEvo_SYBaseDMods`, `IndEvo_SYPartValueMult`, `IndEvo_SYHullDeliveryTime`, `IndEvo_RarePartsAmountPerFP`, `IndEvo_SYDModMoveBaseCostMult`, `IndEvo_shipPickerRowCount` | read as static finals in the dialog classes |
| Strings | category `IndEvo_ScrapYard` | `data/strings/strings.json:159` |

## Registration and lifecycle

- CSV plugin column. `ModPlugin.setListenersIfNeeded()` registers `SalvageYardsOptionProvider` (`ModPlugin.java:476`).
- `apply()` (functional only) registers itself as transient listener (`EconomyTickListener`, `NewDayListener`) and adds itself as `FleetEventListener` to every fleet currently in the system, skipping fleets that carry a `LuddicPathCellsIntel` listener to avoid a vanilla `ConcurrentModificationException` (`SalvageYards.java:202`).
- `advance` keeps the shared submarket present; `unapply` removes listener, submarket and production quality mod.
- Dialog commands are found through `ruleCommandPackages` (see [Rule commands](../dialogue/RULE_COMMANDS.md)).
- `YardsCustomProductionIntel.init()` registers the intel as a persistent listener; it deregisters 60 days after delivery or failure.
- Saved fields: `availableSalvagePoints`, `countedIntel` (raid intel already paid out, pruned daily once the intel manager drops it), `baseOutput`, `currentWeaponBonusSP`, `outputSalvagePointValue`, `outputDecayMult`.

## Flow

### Salvage points

| Source | Amount | Owner |
|---|---|---|
| Battle involving a listened fleet | `Misc.getSnapshotFPLost(fleet)` | `reportBattleOccurred` |
| `RaidIntel` on the system, succeeded | 30% of `getRaidFPAdjusted()` | `onNewDay` |
| `RaidIntel` failed in action stage, player absent for the action stage | 70% | `onNewDay` |
| `GenericRaidFGI` on the system | 30% success, 70% failure of `getTotalFPSpawned()` | `onNewDay`, which reads `getIntel(RaidIntel.class)` plus `getIntel(GenericRaidFGI.class)`: the class lookup only returns subclasses |
| Pirate activity condition in the system | 3 per day | `onNewDay` |
| Weapons in `IndEvo_SharedStore` | `cargo space * 2` per unit, capped at `size * 50` per month | `autoFeed` |

`IntelArchive()` marks raids that had already ended when the yards came online. Month end resets the weapon cap, rolls `baseOutput` and multiplies SP by `outputDecayMult` (0.5).

### Output

`applySupDemProfiles()` (`:155`): `base` = 3 + bonus (player), `size` + bonus (player, beta core), `max(3, baseOutput)` + bonus (AI); bonus = `SP / outputSalvagePointValue`, capped at `size + 4`.

- demand heavy machinery `base - 2`, ships `base`; supply metals and `IndEvo_parts` `base - 1`
- with heavy industry (`HeavyIndustry` subclass, or Shadowyards `MS_modularFac` / `MS_fabUpgrader` when `shadow_ships` is enabled): `hi.getSupply(ships).modifyFlat(base - 2)`
- without: own `ships` supply `base - 2` and `production_quality_mod` `-0.2`
- deficit of heavy machinery or ships reduces ships, metals, parts output (player deficit capped at 3)

### AI cores

| Core | Effect |
|---|---|
| Alpha | 40 SP per output unit, decay keeps 60% |
| Beta | 90 SP per output unit, player base output starts from colony size instead of 3 |
| Gamma | decay keeps 70% |

### Ship assembly contract

`IndEvo_InitSYCustomProductionDiag` replaces the dialog plugin, then: pick hulls with `showCustomProductionPicker` (known ships only, `costMult` = 1.8 - relation, clamped to 0.9..1.5; the D-mod transfer uses 1.5 - relation clamped to 0.8..1.5), scrap hulls for extra budget (30% of base value, less per D-mod), story point option doubles capacity. Capacity = `3 + trade in value + getHullCapacity() * productionCapacityPerSWUnit * (2 with story point)`. Rare parts reduce D-mods (`SY_BASE_DMODS` minus the filled share of `FP * RARE_PARTS_AMOUNT_PER_FP`); parts reduce cost by up to 50%. Confirm removes credits, parts and traded hulls and posts `YardsCustomProductionIntel`, which delivers to local storage after `IndEvo_SYHullDeliveryTime` days with the forecast D-mods (`DModManager.addDMods`) and unlocks storage. It fails if the market decivilizes or loses the yards.

### D-mod transfer

`IndEvo_InitSYMoveDModDiag`: pick an origin hull with D-mods, a target of the same hull size and manufacturer that is restorable and has a different D-mod set, then pick D-mods that the target lacks. Cost per D-mod uses the origin hull's base value, `IndEvo_SYDModMoveBaseCostMult` and the relation mult. Origin restores to the base hull when clean (`RestorationDocks.getBaseShipHullSpec`), target becomes a D-hull.

## Contracts

- The industry only counts battles of fleets present when `apply()` last ran; new arrivals are added on the next reapply.
- `isFunctional()` returns false while the setting is off, which also stops a saved yards from producing.
- `custom_production_mod` handling (`modifyPlayerCustomProduction`) is never called; only the unmodify runs. Output reaches custom production through heavy industry `ships` supply instead (inferred from the "now adds onto heavy industry directly" comment, `:80`).

## Known issues and dormant code

| Item | State |
|---|---|
| AI core fields not reset (`applyAlphaCoreModifiers` etc., `:506`) | `BaseIndustry.applyAICoreModifiers` calls only one branch (`api/impl/campaign/econ/impl/BaseIndustry.java:246`); switching alpha to beta keeps alpha decay, beta to gamma keeps 90 SP per unit |
| Story point bonus (`IndEvo_InitSYCustomProductionDiag.getCapacity`, `:357`) | multiplies only the hull capacity term (operator precedence) |
| D-mod transfer confirm | no credit check before `subtract`; cost formula `(1.2^n + 1) / n` differs from `RestorationDocks` `1.2^(n+1) / n` |
| `modifyPlayerCustomProduction` | dead code |
| Open idea | `Changelog_todo:45`, `:69`: move S-mods with the yards |

## Maintenance

Update this doc when SP sources, output math, AI core effects or either dialog change. Parts drops belong in [Installables](../items/INSTALLABLES.md), the shared submarket in [Submarkets](../core/SUBMARKETS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
