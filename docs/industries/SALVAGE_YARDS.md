# Salvage Yards

The Salvage Yards industry (`IndEvo_ScrapYard`): salvage points from nearby battles and raids, hull output on top of heavy industry, the custom production contract, the D-mod and S-mod transfer dialogs and the Salvage Drone Replicator item. Read it before changing salvage point sources, output, or either dialog. Ship parts and relic component drops are in [Installables](../items/INSTALLABLES.md) (`ShipComponentLootManager`); the shared AI core/weapon storage submarket is in [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The yards collect salvage points (SP, called salvage units in the UI): fleet points lost in battles by fleets in the system, a share of raid fleet points against the system, 3 per day while any market in the system has pirate activity, and weapons fed in from the shared storage. Every `outputSalvagePointValue` SP adds one unit of hull output. Output goes into the heavy industry's `ships` supply, or into the yards' own supply with a ship quality penalty if there is none. The yards also supply metals and ship parts. The colony interaction menu opens a ship assembly contract and the D-mod and S-mod transfer services.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/salvageyards/industry/SalvageYards.java` | Industry plugin. SP state and sources (`reportBattleOccurred`, `onNewDay`, `autoFeed`), monthly decay (`reportEconomyMonthEnd`), supply and demand (`applySupDemProfiles`), `getHullCapacity` for the contract dialog, AI cores |
| `indevo/industries/salvageyards/plugins/SalvageYardsOptionProvider.java` | Industry option "Visit the Salvage Yards..." opens a `RuleBasedInteractionDialogPluginImpl` firing `IndEvo_FireBaseSYOptionList` |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYCustomProductionDiag.java` | Rule command and `InteractionDialogPlugin` for the ship assembly contract; nested `YardsProductionData` (order, parts spent, cost, D-mod forecast) |
| `indevo/industries/salvageyards/intel/YardsCustomProductionIntel.java` | Production order intel and delivery; `NewDayListener`, `RelicSpeedUpTarget` (Accelerate Construction, `SPEED_UP_LABEL`) |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYMoveDModDiag.java` | Rule command and dialog plugin for moving D-mods between hulls; static `getUnavailableReason`, `getMembers` (ship pool) |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYMoveSModDiag.java` | Rule command and dialog plugin for moving S-mods between hulls; static `getUnavailableReason` |
| `indevo/industries/salvageyards/listeners/SalvageDronesBattleListener.java` | Transient `BaseCampaignEventListener`: on every battle, calls `reportBattleInDroneRange` on each yards with the item whose system is the battle's or within range |
| `indevo/items/installable/SalvageDronesEffect.java` | Item description; no apply, the yards read `special` |
| `indevo/industries/salvageyards/rules/IndEvo_SYTransferOption.java` | Rule script `IndEvo_SYTransferOption <option> dmod\|smod`: sets the transfer option tooltip, disables it and appends the reason in negative highlight when no pair is valid |

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
| Setting | `IndEvo_SYBaseDMods`, `IndEvo_SYPartValueMult`, `IndEvo_SYHullDeliveryTime`, `IndEvo_RarePartsAmountPerFP`, `IndEvo_SYDModMoveBaseCostMult`, `IndEvo_shipPickerRowCount` | read at use time in the dialog classes; a contract keeps the delivery time it was accepted with (`YardsCustomProductionIntel.deliveryTime`, saved) |

## Registration and lifecycle

- CSV plugin column. `ModPlugin.setListenersIfNeeded()` registers `SalvageYardsOptionProvider` and `SalvageDronesBattleListener` (`ModPlugin.java:478-479`).
- `apply()` (functional only) registers itself as transient listener (`EconomyTickListener`, `NewDayListener`) and adds itself as `FleetEventListener` to every fleet currently in the system, skipping fleets that carry a `LuddicPathCellsIntel` listener to avoid a vanilla `ConcurrentModificationException` (`SalvageYards.java:195`).
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
- `reportBattleInDroneRange`: battles in another location add `SALVAGE_DRONES_SP_SHARE` (0.5) of the FP lost on both snapshot sides. Battles in the yards' own system are skipped there, the per fleet listener already pays them.

### Ship assembly contract

`IndEvo_InitSYCustomProductionDiag` replaces the dialog plugin, then: pick hulls with `showCustomProductionPicker` (known ships only, `costMult` = 1.8 - relation, clamped to 0.9..1.5; the D-mod transfer uses 1.5 - relation clamped to 0.8..1.5), scrap hulls for extra budget (30% of base value, less per D-mod), story point option doubles capacity. Capacity = `3 + trade in value + getHullCapacity() * productionCapacityPerSWUnit * (2 with story point)`. Rare parts reduce D-mods (`SY_BASE_DMODS` minus the filled share of `FP * RARE_PARTS_AMOUNT_PER_FP`); parts reduce cost by up to 50%. Confirm removes credits, parts and traded hulls and posts `YardsCustomProductionIntel`, which delivers to local storage after `IndEvo_SYHullDeliveryTime` days with the forecast D-mods (`DModManager.addDMods`) and unlocks storage. It fails if the market decivilizes or loses the yards.

Accelerate Construction: while the order waits, the intel shows an `Accelerate Construction` button (`BUTTON_EXPRESS`), and the ship assembly dialog main menu shows one option per waiting order of this market (`ACCELERATE_PREFIX` + index, label with the days left) that opens `RelicSpeedUpDialog` and refreshes the menu on close. With no waiting order the option is disabled and its tooltip says so in negative highlight. Its confirm prompt is the shared `RelicSpeedUpDialog.addBody`; confirm halves the days left by adding them to `elapsed`, for 1 Relic Component per remaining day from the yards market's storage, then fleet cargo. Once per 60 days per order (`relicSpeedUpTimestamp`); the button is disabled on cooldown, with 1 day or less left or when relics are short, its tooltip shows effect, cost and lore. See [Economy](../core/ECONOMY.md#relic-speed-up).

### D-mod transfer

`IndEvo_InitSYMoveDModDiag`: pick an origin hull with D-mods that are not built into its hull, a target of the same hull size and manufacturer that is restorable and lacks at least one of them, then pick D-mods that the target lacks. The ship pool is the player fleet plus the market storage's mothballed ships (`getMembers`, never inits the storage fleet). The menu option and the pickers share these checks; `getUnavailableReason` returns null when a pair exists, else the first missing step (no movable D-mods, no matching hull, every matching hull already has them). Cost per D-mod uses the origin hull's base value, `IndEvo_SYDModMoveBaseCostMult` and the relation mult. Origin restores to the base hull when clean (`RestorationDocks.getBaseShipHullSpec`), target becomes a D-hull.

### S-mod transfer

`IndEvo_InitSYMoveSModDiag`, same steps and ship pool (player fleet and local storage) as the D-mod transfer:

- Origin: a ship with S-mods (`Misc.getCurrSpecialModsList`: no built ins, D-mods or hidden mods), without `Tags.HULLMOD_NO_BUILD_IN`.
- Target: same hull size and manufacturer (`IndEvo_InitSYMoveDModDiag.isSameSizeAndTechtype`), below its S-mod cap (`Misc.getCurrSpecialMods` against `Misc.getMaxPermanentMods(member, playerStats)`), with the S-mod already installed as a normal hullmod (in `getHullMods`, not a perma mod, not built into the hull). The campaign has no `ShipAPI` for `HullModEffect.isApplicableToShip`, so the vanilla refit's install check stands in for it.
- The S-mod list on a full target disables unselected entries once the selection fills the free slots.
- `getUnavailableReason` names the first missing step for the menu tooltip: no S-mods, no matching hull, no free slot, hullmod not installed on a matching hull.
- Cost per S-mod: `RELICS_PER_DP` (5) x target `getUnmodifiedDeploymentPointsCost()` Relic Components, plus credits from the D-mod transfer formula (`IndEvo_InitSYMoveDModDiag.getCreditCostPerMod`) with n = origin S-mod count. Relics come from local storage first, then fleet cargo; credits through `BillingHelper.chargeNow`.
- Apply: both variants are cloned to custom `REFIT` variants; origin `removePermaMod` and `removeMod`, target `addPermaMod(id, true)`. No bonus XP.
- The Confirm option tooltip shows effect, cost line and the lore text (`LORE`, gray).

## Contracts

- The S-mod transfer confirmation states the transfer direction; the fee line identifies the credit charge.

- S-mod transfer tooltips state selection blockers and destination requirements. The lore describes compatible hulls, matching the size and manufacturer checks.

- The industry only counts battles of fleets present when `apply()` last ran; new arrivals are added on the next reapply.
- `isFunctional()` returns false while the setting is off, which also stops a saved yards from producing. The card goes dark; the tooltip shows "Salvage Yards are turned off in the mod settings." (`addRightAfterDescriptionSection`, `SalvageYards.java:423`, `NORMAL` mode, not while building) and vanilla's "Currently under construction" line below it, since vanilla prints that for any non functional industry that is not disrupted.
- `custom_production_mod` handling (`modifyPlayerCustomProduction`) is never called; only the unmodify runs. Output reaches custom production through heavy industry `ships` supply instead (inferred from the "now adds onto heavy industry directly" comment, `:76`).

## Known issues and dormant code

| Item | State |
|---|---|
| AI core fields not reset (`applyAlphaCoreModifiers` etc., `:502`) | `BaseIndustry.applyAICoreModifiers` calls only one branch (`api/impl/campaign/econ/impl/BaseIndustry.java:246`); switching alpha to beta keeps alpha decay, beta to gamma keeps 90 SP per unit |
| Story point bonus (`IndEvo_InitSYCustomProductionDiag.getCapacity`, `:355`) | multiplies only the hull capacity term (operator precedence) |
| D-mod transfer cost | formula `(1.2^n + 1) / n` differs from `RestorationDocks` `1.2^(n+1) / n` |
| `modifyPlayerCustomProduction` | dead code |

## Maintenance

Update this doc when SP sources, output math, AI core effects or any dialog change. Parts drops belong in [Installables](../items/INSTALLABLES.md), the shared submarket in [Submarkets](../core/SUBMARKETS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
