# Salvage Yards

The Salvage Yards industry (`IndEvo_ScrapYard`): salvage points from nearby battles and raids, hull output on top of heavy industry, the Salvage Yards panel (ship assembly contract, Wreck Registry orders, D-mod and S-mod transfer) and the Salvage Drone Replicator item with its Wreck Registry. Read it before changing salvage point sources, output, or the panel. Ship parts and relic component drops are in [Installables](../items/INSTALLABLES.md) (`ShipComponentLootManager`); the shared AI core/weapon storage submarket is in [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The yards collect salvage points (SP, called salvage units in the UI): fleet points lost in battles by fleets in the system, a share of raid fleet points against the system, 3 per day while any market in the system has pirate activity, and weapons fed in from the shared storage. Every `outputSalvagePointValue` SP adds one unit of hull output. Output goes into the heavy industry's `ships` supply, or into the yards' own supply with a ship quality penalty if there is none. The yards also supply metals and ship parts. The colony interaction menu opens the Salvage Yards panel on its Ship assembly, D-mods or S-mods tab.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/salvageyards/industry/SalvageYards.java` | Industry plugin. SP state and sources (`reportBattleOccurred`, `onNewDay`, `autoFeed`), monthly decay (`reportEconomyMonthEnd`), supply and demand (`applySupDemProfiles`), `getHullCapacity` for the contract dialog, AI cores |
| `indevo/industries/salvageyards/plugins/SalvageYardsOptionProvider.java` | Industry option "Visit the Salvage Yards..." opens a `RuleBasedInteractionDialogPluginImpl` firing `IndEvo_FireBaseSYOptionList` |
| `indevo/industries/salvageyards/rules/IndEvo_ShowSalvageYardsPanel.java` | Rule command `IndEvo_ShowSalvageYardsPanel <assembly\|registry\|dmods\|smods>`: swaps in `SalvageYardsDialogPlugin` on the given tab |
| `indevo/industries/salvageyards/plugins/SalvageYardsDialogPlugin.java` | `InteractionDialogPlugin` that hosts the panel, reopens it after vanilla pickers and fires `IndEvo_YardsBaseMenu` on Return; static `show(market, view)` for code and tests |
| `indevo/industries/salvageyards/plugins/SalvageYardsPanel.java` | Custom dialog delegate: title, tabs, counters, help, tab dispatch |
| `indevo/industries/salvageyards/plugins/SalvageYardsView.java` | View state: tab, selected registry hull, transfer origin, target and mods, the order, confirm and scroll state |
| `indevo/industries/salvageyards/plugins/YardsOrder.java` | The order being built: blueprint hulls, registry hulls, weapons, wings (requested amounts), scrapped ships, parts, relics |
| `indevo/industries/salvageyards/plugins/YardsContract.java` | Prices, budget, limits and confirm of the order; nested `Line` (one order line, capped to the registry) |
| `indevo/industries/salvageyards/plugins/AssemblyTab.java` | Ship assembly tab: order lines, pickers, scrapped hulls, running orders with Accelerate, contract column |
| `indevo/industries/salvageyards/plugins/RegistryTab.java` | Wreck Registry tab: registry hulls, weapons and wings, fit of the selected hull |
| `indevo/industries/salvageyards/plugins/TransferTab.java` | D-mods and S-mods tabs: origin and target strips, mod selection, slots, costs, Transfer |
| `indevo/industries/salvageyards/plugins/ModTransfer.java` | Transfer rules shared by both transfer tabs: ship pool, checks, reasons, costs, `moveDMods`, `moveSMods`, `cycleToCustomVariant` |
| `indevo/industries/salvageyards/plugins/YardsSlider.java` | Value slider with step buttons for parts and relics |
| `indevo/industries/salvageyards/memory/YardsProductionData.java` | Saved in the intel: production list, parts and relics spent, base cost, `registryOrder`, D-mod forecast |
| `indevo/industries/salvageyards/intel/YardsCustomProductionIntel.java` | Production order intel and delivery (registry hulls as fitted, registry weapons and wings); `NewDayListener`, `RelicSpeedUpTarget` (Accelerate Construction, `SPEED_UP_LABEL`) |
| `indevo/industries/salvageyards/listeners/SalvageDronesBattleListener.java` | Transient `BaseCampaignEventListener`: on every battle, calls `reportBattleInDroneRange` on each yards with the item whose system is the battle's or within range |
| `indevo/industries/salvageyards/memory/WreckRegistry.java` | Saved per yards: hulls (last `MAX_HULLS_PER_ID` fits per hull id), weapon and wing counts; nested `WreckFit` (one lost ship's fit) and `Order` (what a contract takes) |
| `indevo/items/installable/SalvageDronesEffect.java` | Item description; no apply, the yards read `special` |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_ScrapYard` (`Ids.SCRAPYARD`) | `data/campaign/industries.csv`, tags `heavyindustry, industry, industrial` |
| Commodities | `IndEvo_parts`, `IndEvo_rare_parts` (`ItemIds.PARTS`, `RARE_PARTS`) | `data/campaign/commodities.csv` |
| Submarket | `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`) | added by `SharedSubmarketUser`; see [Small industries](SMALL_INDUSTRIES.md) |
| Rules | `IndEvo_SalvageYardsBaseMenu`, `IndEvo_SalvageYardsColonyOptionMenu`, `IndEvo_IntitiateYardsMenu`, `IndEvo_FireSYBaseMenuFromIndustryOption`, `IndEvo_SYCustomProductionPanel(_init)`, `IndEvo_SYMoveDMods(_init)`, `IndEvo_SYMoveSMods(_init)`, `IndEvo_SYLeave`; triggers `IndEvo_YardsBaseMenu`, `IndEvo_FireBaseSYOptionList` | `data/campaign/rules.csv` |
| Item | `IndEvo_drones` (`ItemIds.SALVAGE_DRONES`), Salvage Drone Replicator | `data/campaign/special_items.csv:55`, params `IndEvo_ScrapYard`; drops in `indEvo_tech_addition` and `indEvo_tech_event` like the other IE colony items |
| Memory key | `$IndEvo_closeDialogueOnNextReturn` | interaction target memory, set by the option provider |
| Memory key | `$IndEvo_HasSelectedSP_SY` | market memory: story point double capacity bought but not yet used |
| Setting | `IndEvo_ScrapYard` | enables the industry; `isFunctional()` also checks it |
| Setting | `IndEvo_SYBaseDMods`, `IndEvo_SYPartValueMult`, `IndEvo_SYHullDeliveryTime`, `IndEvo_RarePartsAmountPerFP`, `IndEvo_SYDModMoveBaseCostMult`, `IndEvo_shipPickerRowCount` | read at use time in the panel classes; a contract keeps the delivery time it was accepted with (`YardsCustomProductionIntel.deliveryTime`, saved) |

## Registration and lifecycle

- CSV plugin column. `ModPlugin.setListenersIfNeeded()` registers `SalvageYardsOptionProvider` and `SalvageDronesBattleListener` (`ModPlugin.java:478-479`).
- `apply()` (functional only) registers itself as transient listener (`EconomyTickListener`, `NewDayListener`) and adds itself as `FleetEventListener` to every fleet currently in the system, skipping fleets that carry a `LuddicPathCellsIntel` listener to avoid a vanilla `ConcurrentModificationException` (`SalvageYards.java:195`).
- `advance` keeps the shared submarket present; `unapply` removes listener, submarket and production quality mod.
- Dialog commands are found through `ruleCommandPackages` (see [Rule commands](../dialogue/RULE_COMMANDS.md)).
- `YardsCustomProductionIntel.init()` registers the intel as a persistent listener; it deregisters 60 days after delivery or failure.
- Saved fields: `availableSalvagePoints`, `countedIntel` (raid intel already paid out, pruned daily once the intel manager drops it), `baseOutput`, `currentWeaponBonusSP`, `outputSalvagePointValue`, `outputDecayMult`, `wreckRegistry` (null until `getWreckRegistry()` first runs, so yards without the item save none). The intel saves `YardsProductionData` (`indevo.industries.salvageyards.memory`), whose `registryOrder` is null for orders without registry entries.

## Flow

### Salvage points

| Source | Amount | Owner |
|---|---|---|
| Battle involving a listened fleet | `Misc.getSnapshotFPLost(fleet)` | `reportBattleOccurred` |
| `RaidIntel` on the system, succeeded | 30% of `getRaidFPAdjusted()` | `onNewDay` |
| `RaidIntel` failed in action stage, player absent for the action stage | 70% | `onNewDay` |
| `GenericRaidFGI` on the system | 30% success, 70% failure of `getTotalFPSpawned()` | `onNewDay`, which reads `getIntel(RaidIntel.class)` plus `getIntel(GenericRaidFGI.class)`: the class lookup only returns subclasses |
| Pirate activity condition in the system | 3 per day | `onNewDay` |
| Battle in another system or in hyperspace within 6 LY, Salvage Drone Replicator installed | 50% of `Misc.getSnapshotFPLost` over both snapshot sides | `reportBattleInDroneRange` |
| Weapons in `IndEvo_SharedStore` | `cargo space * 2` per unit, capped at `size * 50` per month | `autoFeed` |

`IntelArchive()` marks raids that had already ended when the yards came online. Month end resets the weapon cap, rolls `baseOutput` and multiplies SP by `outputDecayMult` (0.5).

### Output

`applySupDemProfiles()` (`:148`): `base` = 3 + bonus (player), `size` + bonus (player, beta core), `max(3, baseOutput)` + bonus (AI); bonus = `SP / outputSalvagePointValue`, capped at `size + 4`.

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

### Salvage Drone Replicator

`IndEvo_drones`, installed through the vanilla item slot (`BaseIndustry.getInstallableItems`, params `IndEvo_ScrapYard`). `hasSalvageDrones()` is true while the yards are functional and the item is installed.

- `SalvageDronesBattleListener.reportBattleOccurred(primaryWinner, battle)` checks every market with the yards: same containing location, or `Misc.getDistanceLY` from the winner's hyperspace location to the market's of at most `SALVAGE_DRONES_RANGE_LY` (6). Hyperspace battles count by distance.
- `reportBattleInDroneRange`: records the battle in the Wreck Registry, then battles in another location add `SALVAGE_DRONES_SP_SHARE` (0.5) of the FP lost on both snapshot sides. Battles in the yards' own system get no SP there, the per fleet listener already pays them.
- The industry tooltip shows the registry totals while the item is installed.

#### Wreck Registry

`WreckRegistry.recordLosses(battle)` walks `Misc.getSnapshotMembersLost` of every fleet on both snapshot sides:

- Weapons in non built in slots and non built in wings, unless tagged `Tags.NO_DROP` / `Tags.WING_NO_DROP`. Counts per id, capped at `MAX_WEAPONS_PER_ID` (20) and `MAX_WINGS_PER_ID` (10).
- Hulls: skipped for fleets with `MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY`, stations, fighter wings and `Misc.isUnboardable` hulls. The restored hull (`RestorationDocks.getBaseShipHullSpec(variant, false)`) must pass the Engineering Hub whitelist (`ReverseEngineeringSubmarketPlugin.isWhitelisted`, see [Engineering Hub](ENGINEERING_HUB.md)). Each hull keeps its `WreckFit`, the oldest drops past `MAX_HULLS_PER_ID` (5).
- `WreckFit` keeps slot weapons, wings, hullmods (not hidden, D-mod, `Tags.HULLMOD_NO_DROP` or perma mods), vents and caps. `createMember(stats)` builds a custom `REFIT` variant of the restored hull, clamps vents and caps to the player's max and drops caps, vents, wings, weapons, then hullmods until `computeOPCost` fits the player's ordnance points. `getValue()` is the base value of its weapons and wings.

### Salvage Yards panel

`IndEvo_ShowSalvageYardsPanel` replaces the dialog plugin with `SalvageYardsDialogPlugin`, which hides the text and visual panels and shows `SalvageYardsPanel` as a custom dialog (board 1024 x 760, `UiFrame.width/height`). Tabs: Ship assembly, Wreck Registry (only while `hasSalvageDrones()`), D-mods, S-mods. The header shows salvage units, Ship Components and Relic Components (fleet plus local storage) and credits, right to left, each left out when it would reach the tabs. Every change rebuilds the panel through `UiRoot`; the order and selections live in `SalvageYardsView` until the panel closes. Return (the confirm button, no cancel) fires `IndEvo_YardsBaseMenu` a frame later; with no original plugin (`show`) it dismisses the dialog. Escape is blocked through `EscapeInterceptListener` while the panel is up.

Vanilla pickers (`showCustomProductionPicker`, `showFleetMemberPickerDialog`) need the panel closed: the dialog keeps one sub dialog and ignores a second `showCustomDialog` (`obf/ui/newui/_c192.java:1009,1062`), and the custom dialog calls its delegate before it clears that slot (`obf/ui/newui/super.java:115-127`). `openPicker` dismisses the panel, opens the picker on the next `advance` and then calls `showCustomDialog` every frame until the picker is gone, since the production picker has no cancel callback (`SalvageYardsDialogPlugin.java:114`).

### Ship assembly contract

The order holds blueprint hulls from `showCustomProductionPicker` (known ships), registry weapons and wings (also offered in the picker), registry hulls (from the Wreck Registry tab) and scrapped ships (`showFleetMemberPickerDialog` on fleet and storage ships). Picks are added to the order; the x on a line's sprite removes the line. `YardsContract` prices it each rebuild:

- Price mult `costMult` = 1.8 - relation, clamped to 0.9..1.5. Line cost = `createSampleItem(...).getBaseCost() * qty * costMult`, rounded per line like `FactionProduction.getTotalCurrentCost`.
- Capacity = `3 + scrap value + getHullCapacity() * productionCapacityPerSWUnit * (2 with story point)`. Scrapped ships count 30% of base value, less per D-mod. The story point is spent on the button (bonus XP `IndEvo_SYDoubleCapBonusXP`) and kept in `$IndEvo_HasSelectedSP_SY` until a contract is confirmed.
- Ship Components reduce the cost by up to 50%; Relic Components reduce D-mods (`SY_BASE_DMODS` minus the filled share of `FP * RARE_PARTS_AMOUNT_PER_FP`). Both sliders count fleet cargo plus local storage.
- Confirm needs items, value within capacity, credits and enough salvage units. It charges credits, takes relics and parts (fleet cargo first, then storage), removes scrapped ships, takes registry entries and salvage units, and posts `YardsCustomProductionIntel`, which delivers to local storage after `IndEvo_SYHullDeliveryTime` days with the forecast D-mods (`DModManager.addDMods`) and unlocks storage. It fails if the market decivilizes or loses the yards.

Orders in progress lists the market's waiting intel with day progress and Accelerate (two clicks). Accelerate is off with the reason under it on cooldown, with too few days left or with too few relics; the tooltip is `RelicSpeedUpDialog.addTooltip`. Confirm halves the days left for 1 Relic Component per remaining day from the yards market's storage, then fleet cargo, once per 60 days per order (`relicSpeedUpTimestamp`). The intel keeps its own `Accelerate Construction` button. See [Economy](../core/ECONOMY.md#relic-speed-up).

### Wreck Registry orders

The Wreck Registry tab (yards with the Salvage Drone Replicator) lists registry hulls without a blueprint, sorted by name, then registry weapons and wings as cells. Hull rows add or remove one with the stepper; a weapon or wing cell adds one on click and its x removes one. `YardsContract.canAdd` allows one more while the registry count, the salvage units and the budget left cover it. The detail column shows the fit the next hull would take (weapons, wings, hullmods) and its cost.

- Registry hulls cost `SALVAGE_DRONES_SP_PER_FP` (10) salvage units per FP; weapons and wings cost none. The order takes the oldest fits (`getOldestFits`) and adds `getValue() * costMult` credits (`fitCost`, included in `getCost()`). Registry hulls get D-mods, rare parts and parts like any yards hull.
- Order amounts are requests: a line shows `min(requested, registry count)` and "Limited to the Wreck Registry: N" when the registry has fewer. Weapons and wings picked in the production picker are not capped there.
- `toData()` builds `YardsProductionData.registryOrder`. Confirm runs `WreckRegistry.take(order)` and `spendSalvagePoints`. Delivery builds one fitted member per taken fit, then bare hulls, then adds the weapons and wings; D-mods apply to all hulls.

### D-mod transfer

D-mods tab (`TransferTab`, `ModTransfer.Kind.DMOD`). The ship pool is the player fleet plus the market storage's mothballed ships (`ModTransfer.getMembers`, never inits the storage fleet). From lists ships with D-mods that are not built into the hull; To lists ships of the same hull size and manufacturer. Ships that can not take part are dimmed with the reason (`getOriginReason`, `getTargetReason`). A target must be restorable and lack at least one selected D-mod. With no valid pair the tab shows only `getUnavailableReason` (no movable D-mods, no matching hull, every matching hull already has them). Cost per D-mod uses the origin hull's base value, `IndEvo_SYDModMoveBaseCostMult` and the relation mult (1.5 - relation, clamped to 0.8..1.5). Origin restores to the base hull when clean (`RestorationDocks.getBaseShipHullSpec`), target becomes a D-hull.

### S-mod transfer

S-mods tab (`TransferTab`, `ModTransfer.Kind.SMOD`), same ship pool and layout as the D-mod transfer:

- Origin: a ship with S-mods (`Misc.getCurrSpecialModsList`: no built ins, D-mods or hidden mods), without `Tags.HULLMOD_NO_BUILD_IN`.
- Target: same hull size and manufacturer (`ModTransfer.isSameSizeAndTechtype`), below its S-mod cap (`Misc.getCurrSpecialMods` against `Misc.getMaxPermanentMods(member, playerStats)`), with the S-mod already installed as a normal hullmod (in `getHullMods`, not a perma mod, not built into the hull). The campaign has no `ShipAPI` for `HullModEffect.isApplicableToShip`, so the vanilla refit's install check stands in for it.
- The selection stops at the target's free slots; other rows show why they can not be picked. The slot boxes show built in, incoming and free slots.
- `getUnavailableReason` names the first missing step: no S-mods, no matching hull, no free slot, hullmod not installed on a matching hull.
- Cost per S-mod: `RELICS_PER_DP` (5) x target `getUnmodifiedDeploymentPointsCost()` Relic Components, plus credits from the D-mod transfer formula (`ModTransfer.getCreditCostPerMod`) with n = origin S-mod count. Relics come from local storage first, then fleet cargo; credits through `BillingHelper.chargeNow`.
- Apply: both variants are cloned to custom `REFIT` variants; origin `removePermaMod` and `removeMod`, target `addPermaMod(id, true)`. No bonus XP.

## Contracts

- Registry entries leave the registry only at contract confirm. `WreckRegistry.take` and `SalvageYards.spendSalvagePoints` throw when counts or SP are short; the dialog checks both first.
- `Order.fits` holds the oldest fits per hull id, and `take` removes from the front of the same list (`WreckRegistry.java:223`).
- Rules must not fire from inside the custom dialog's dismiss; `SalvageYardsDialogPlugin` sets `returnPending` and fires `IndEvo_YardsBaseMenu` on the next `advance`.
- The transfer tabs name the selection blockers and target requirements on the rows and under the Transfer button. The S-mod lore describes compatible hulls, matching the size and manufacturer checks.

- The industry only counts battles of fleets present when `apply()` last ran; new arrivals are added on the next reapply.
- `isFunctional()` returns false while the setting is off, which also stops a saved yards from producing. The card goes dark; the tooltip shows "Salvage Yards are turned off in the mod settings." (`addRightAfterDescriptionSection`, `SalvageYards.java:423`, `NORMAL` mode, not while building) and vanilla's "Currently under construction" line below it, since vanilla prints that for any non functional industry that is not disrupted.
- `custom_production_mod` handling (`modifyPlayerCustomProduction`) is never called; only the unmodify runs. Output reaches custom production through heavy industry `ships` supply instead (inferred from the "now adds onto heavy industry directly" comment, `:76`).

## Known issues and dormant code

| Item | State |
|---|---|
| AI core fields not reset (`applyAlphaCoreModifiers` etc., `:502`) | `BaseIndustry.applyAICoreModifiers` calls only one branch (`api/impl/campaign/econ/impl/BaseIndustry.java:246`); switching alpha to beta keeps alpha decay, beta to gamma keeps 90 SP per unit |
| Story point bonus (`YardsContract.getCapacity`, `:186`) | multiplies only the hull capacity term (operator precedence) |
| D-mod transfer cost | formula `(1.2^n + 1) / n` differs from `RestorationDocks` `1.2^(n+1) / n` |
| `modifyPlayerCustomProduction` | dead code |

## Maintenance

Update this doc when SP sources, output math, AI core effects or the panel change. Parts drops belong in [Installables](../items/INSTALLABLES.md), the shared submarket in [Submarkets](../core/SUBMARKETS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
