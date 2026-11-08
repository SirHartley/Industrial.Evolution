# Salvage Yards

The Salvage Yards industry (`IndEvo_ScrapYard`): salvage points from nearby battles and raids, hull output on top of heavy industry, the custom production contract, the D-mod and S-mod transfer dialogs and the Salvage Drone Replicator item with its Wreck Registry. Read it before changing salvage point sources, output, or either dialog. Ship parts and relic component drops are in [Installables](../items/INSTALLABLES.md) (`ShipComponentLootManager`); the shared AI core/weapon storage submarket is in [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The yards collect salvage points (SP, called salvage units in the UI): fleet points lost in battles by fleets in the system, a share of raid fleet points against the system, 3 per day while any market in the system has pirate activity, and weapons fed in from the shared storage. Every `outputSalvagePointValue` SP adds one unit of hull output. Output goes into the heavy industry's `ships` supply, or into the yards' own supply with a ship quality penalty if there is none. The yards also supply metals and ship parts. The colony interaction menu opens a ship assembly contract and the D-mod and S-mod transfer services.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/salvageyards/industry/SalvageYards.java` | Industry plugin. SP state and sources (`reportBattleOccurred`, `onNewDay`, `autoFeed`), monthly decay (`reportEconomyMonthEnd`), supply and demand (`applySupDemProfiles`), `getHullCapacity` for the contract dialog, AI cores |
| `indevo/industries/salvageyards/plugins/SalvageYardsOptionProvider.java` | Industry option "Visit the Salvage Yards..." opens a `RuleBasedInteractionDialogPluginImpl` firing `IndEvo_FireBaseSYOptionList` |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYCustomProductionDiag.java` | Rule command and `InteractionDialogPlugin` for the ship assembly contract; nested `YardsProductionData` (order, parts spent, cost, D-mod forecast, `registryOrder`) |
| `indevo/industries/salvageyards/intel/YardsCustomProductionIntel.java` | Production order intel and delivery (registry hulls as fitted, registry weapons and wings); `NewDayListener`, `RelicSpeedUpTarget` (Accelerate Construction, `SPEED_UP_LABEL`) |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYMoveDModDiag.java` | Rule command and dialog plugin for moving D-mods between hulls; static `getUnavailableReason`, `getMembers` (ship pool) |
| `indevo/industries/salvageyards/rules/IndEvo_InitSYMoveSModDiag.java` | Rule command and dialog plugin for moving S-mods between hulls; static `getUnavailableReason` |
| `indevo/industries/salvageyards/listeners/SalvageDronesBattleListener.java` | Transient `BaseCampaignEventListener`: on every battle, calls `reportBattleInDroneRange` on each yards with the item whose system is the battle's or within range |
| `indevo/industries/salvageyards/memory/WreckRegistry.java` | Saved per yards: hulls (last `MAX_HULLS_PER_ID` fits per hull id), weapon and wing counts; nested `WreckFit` (one lost ship's fit) and `Order` (what a contract takes) |
| `indevo/items/installable/SalvageDronesEffect.java` | Item description; no apply, the yards read `special` |
| `indevo/industries/salvageyards/plugins/RegistryProductionDelegate.java` | Picker delegate interface of a yards with the item: registry hulls, quantity cap, cap message. The patches below act only on pickers with this delegate |
| `indevo/industries/salvageyards/patches/RegistryProductionTabPatch.java` | Registry hulls tab in the custom production picker (`CustomProductionPanel`) |
| `indevo/industries/salvageyards/patches/RegistryProductionLimitPatch.java` | Quantity cap on `FactionProduction.addItem` |
| `indevo/industries/salvageyards/patches/RegistryProductionRowPatch.java` | Orderable amount in front of the registry hull names |
| `indevo/industries/salvageyards/patches/RegistryProductionTooltipPatch.java` | Registry hulls tab tooltips say the hull comes fitted |
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
- Saved fields: `availableSalvagePoints`, `countedIntel` (raid intel already paid out, pruned daily once the intel manager drops it), `baseOutput`, `currentWeaponBonusSP`, `outputSalvagePointValue`, `outputDecayMult`, `wreckRegistry` (null until `getWreckRegistry()` first runs, so yards without the item save none). The intel saves `YardsProductionData`, whose `registryOrder` is null for orders without registry entries.

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

### Ship assembly contract

`IndEvo_InitSYCustomProductionDiag` replaces the dialog plugin, then: pick hulls with `showCustomProductionPicker` (known ships only, `costMult` = 1.8 - relation, clamped to 0.9..1.5; the D-mod transfer uses 1.5 - relation clamped to 0.8..1.5), scrap hulls for extra budget (30% of base value, less per D-mod), story point option doubles capacity. Capacity = `3 + trade in value + getHullCapacity() * productionCapacityPerSWUnit * (2 with story point)`. Rare parts reduce D-mods (`SY_BASE_DMODS` minus the filled share of `FP * RARE_PARTS_AMOUNT_PER_FP`); parts reduce cost by up to 50%. Confirm removes credits, parts and traded hulls and posts `YardsCustomProductionIntel`, which delivers to local storage after `IndEvo_SYHullDeliveryTime` days with the forecast D-mods (`DModManager.addDMods`) and unlocks storage. It fails if the market decivilizes or loses the yards.

Wreck Registry (yards with the Salvage Drone Replicator):

- The picker gets a `YardsRegistryPickerDelegate` (`RegistryProductionDelegate`). Its Ship hulls tab lists known hulls only; registry hulls without a blueprint (`getRegistryHulls`) get their own Registry hulls tab, second in the row ([Registry hulls tab](#registry-hulls-tab)). Weapons and Fighters list the registry weapons and wings.
- `getMaxQuantity(production, type, id)`: known hulls have no cap; registry hulls are capped at `min(registry count, salvage units left / unit cost)`, where the salvage units left are the yards' units minus the other registry hulls in the order; weapons and wings at their registry count. `RegistryProductionLimitPatch` applies it on every add, `notifyProductionSelected` cuts anything still over it (`production.removeItem`) and lists the cut items in negative highlight.
- `updateRegistryOrder()` builds `YardsProductionData.registryOrder`: every hull without a blueprint comes from the registry, always fitted. Registry hulls cost `SALVAGE_DRONES_SP_PER_FP` (10) salvage units per FP; weapons and wings cost none. The order takes the oldest fits (`getOldestFits`) and adds `getValue() * costMult` credits (`fitCost`, included in `getCost()`). Registry hulls get D-mods, rare parts and parts like any yards hull.
- Confirm needs enough SP (`getAvailableSalvagePoints`), then `WreckRegistry.take(order)` and `spendSalvagePoints`. Delivery builds one fitted member per taken fit, then bare hulls, then adds the weapons and wings; D-mods apply to all hulls.

#### Registry hulls tab

The vanilla custom production picker (`CustomProductionPanel`, readable class in obf `com.fs.starfarer.campaign.command`) has no API to add a tab. Three PatchLib patches act only when the panel's delegate is a `RegistryProductionDelegate`; all targets are inferred:

- `RegistryProductionTabPatch` on `CustomProductionPanel`: in `createIfNeeded` a redirect on the 5 argument static button factory creates a second tab button after the Fighters one (same factory, label `RegistryProductionDelegate.TAB_LABEL`), a constructor redirect builds a second `ShipBlueprintBrowser` and fills its `getKnownHulls()` with the registry hulls before `createUI`. `@After createIfNeeded` finds the vanilla tabs by their browser type (the button text carries the shortcut key), adds both to the panel, puts the tab right of Ship hulls and moves Weapons and Fighters right, adds the pair to the panel's tab map and runs `createUI`. The tab is disabled when the registry has no hull; it opens first when Ship hulls is disabled. `@After actionPerformed` switches to the tab (faders and highlights of every map entry), vanilla's own switch already hides it. `@After restoreTableUIState` checks every tag of the extra browser and updates it, as vanilla does for its three.
- `RegistryProductionLimitPatch` on `FactionProduction` (`DoNotObfuscate`): `@Before addItem(type, id, quantity, max)`, the overload every browser row and plan table click uses. Over the cap it shows `getLimitMessage` ("No more in the Wreck Registry" or "The yards do not have enough salvage units"), sets the max argument, or skips the add when nothing is left.
- `RegistryProductionTooltipPatch` on the anonymous `TooltipMakerAPI` classes in `com.fs.starfarer.campaign.command`: redirects `addPara(String, float)` in `createImpl` and swaps the two vanilla "without weapons" lines (cost column header, row tooltip) when the tooltip's outer browser is a registry browser (`RegistryProductionTabPatch.isRegistryBrowser`, weak set filled at construction).
- `RegistryProductionRowPatch` on the ship row (obf class found by `getHullId` and `getTypeString`): in `createRow`, registry hull names get the amount still orderable as `Nx ` (`getMaxQuantity` minus the hull's count in the current order). A hull id is on one tab only, so the id picks the row. The next `create(String, Color)` label call in `createRow` is the name label, kept weak in the row's PatchData; `@After advanceImpl` recomputes the amount when the order's total cost changes and sets the label text.

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

- Registry entries leave the registry only at contract confirm. `WreckRegistry.take` and `SalvageYards.spendSalvagePoints` throw when counts or SP are short; the dialog checks both first.
- `Order.fits` holds the oldest fits per hull id, and `take` removes from the front of the same list (`WreckRegistry.java:223`).
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
