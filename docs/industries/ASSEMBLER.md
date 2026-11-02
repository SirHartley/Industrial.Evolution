# Variable industries (Assembler, Manufactory, Commodity Forge)

Covers the three industries that run on a Variable Production Chip (VPC): Variable Assembler `IndEvo_AdAssem`, Variable Manufactory `IndEvo_AdManuf` and Commodity Forge `IndEvo_ComForge`, plus the monthly delivery message. Read it before changing VPC output, deposit amounts or the Forge burn out. It does not cover the VPC items and their per chip supply and demand table; see [Installables](../items/INSTALLABLES.md). The shared industry storage the Forge feeds from is in [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

All three hold one VPC in their own field instead of the vanilla special item slot. The VPC sets market supply and demand for one or two commodities, and at each month end the industry also deposits a fixed amount of those commodities into local storage or the production gathering point. The Assembler is a structure capped at 3 supply and 4 demand per commodity; it upgrades to the Manufactory, an industry with no cap and higher deposits. The Commodity Forge locks its VPC after 7 days or the first deposit and destroys it after 3 deposits (2 with an alpha core), then auto loads the next VPC from shared industry storage.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/assembler/industry/VPCUserIndustryAPI.java` | Contract the VPC effects call: `setSupply`, `setDemand`, `vpcUnapply`, `hasVPC`, `getDepositList` |
| `indevo/industries/assembler/industry/VariableAssembler.java` | Base for all three: VPC field, apply, output cap, deposit formula, month end delivery, AI cores, NPC random VPC |
| `indevo/industries/assembler/industry/VariableManufactory.java` | Manufactory: size override +1, no output cap, extra heavy machinery demand, keeps VPC across upgrade |
| `indevo/industries/assembler/industry/CommodityForge.java` | Forge: lock timer, burn out after `maxDeposits`, auto feed from `IndEvo_SharedStore`, `SharedSubmarketUserAPI`, `NewDayListener` |
| `indevo/industries/assembler/listeners/DepositMessage.java` | Transient `EconomyTickListener`: one month end message summing deposits of all player VPC industries |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_AdAssem` (structure, upgrade `IndEvo_AdManuf`), `IndEvo_AdManuf` (industry, downgrade `IndEvo_AdAssem`), `IndEvo_ComForge` (structure) | `data/campaign/industries.csv:2,5,8`; `indevo/ids/Ids.java:47-49` |
| Setting | `IndEvo_AdAssem`, `IndEvo_AdManuf`, `IndEvo_ComForge` (build toggles) | `data/config/LunaSettings.csv:91-93` |
| Setting | `IndEvo_VarInd_deliverToProductionPoint` (deliver to gathering point instead of local storage) | `data/config/LunaSettings.csv:120` |
| Config | `IndEvo_AdAssem_amounts`, `IndEvo_AdManuf_amounts`, `IndEvo_ComForge_amounts`: base deposit per commodity and `logIncrease` | `data/config/settings.json:6-43`, read as `getId() + "_amounts"` |
| Sprites | category `IndEvo`: `assembler`, `manufactorium`, `comforge` (active image), `VPCRemovalIcon` | `data/config/settings.json:170-175` |
| Strings | `IndEvo_VarInd`, `IndEvo_AdAssem`, `IndEvo_ComForge` | `data/strings/strings.json:96`, `:109`, `:115` |
| Item tags | `IndEvo_VPC_common` (70), `IndEvo_VPC_uncommon` (25), `IndEvo_VPC_rare` (5): NPC pick weights | `VariableAssembler.java:180-189`, `indevo/ids/ItemIds.java:31-33` |
| Whitelist | All three are valid ruin upgrade targets | `data/config/indEvo/ruins_industry_whitelist.csv:5-7` |

## Registration and lifecycle

- Industry plugins from `industries.csv`. Each `apply()` adds the industry itself as a transient listener (`EconomyTickListener`, Forge also `NewDayListener`) and `unapply()` removes it (`VariableAssembler.java:54,84`, `CommodityForge.java:57,84`).
- `DepositMessage.register()` in `ModPlugin.setListenersIfNeeded()` (`indevo/utils/ModPlugin.java:427`), transient, re-added each load.
- `NewGameIndustryPlacer.placeManufactories()` puts a Manufactory with `IndEvo_vpc_IndEvo_parts` on `eochu_bres` at new game (`indevo/utils/update/NewGameIndustryPlacer.java:44-54`).
- `initWithParams` accepts a VPC id, so econ files can preinstall one (`VariableAssembler.java:430-439`).

Apply chain: `VariableAssembler.apply -> applyIndEvo_VPCEffects -> VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS[id].apply(this) -> setSupply / setDemand -> applyOutputReduction (Assembler only) -> applyDeficits -> BaseIndustry.apply(true)`.

Month end chain: `reportEconomyMonthEnd -> getDepositList -> getDepositAmount -> cargo.addCommodity` (storage or gathering point) and `DepositMessage.reportEconomyMonthEnd -> MessageIntel`.

## Flow

### Supply and demand

`setSupply` / `setDemand` use `size = market size + getVPCMarketSizeOverride()` (Assembler -1, Manufactory +1, Forge +2), +1 more if the result is 3 or less, then add each VPC modifier (`VariableAssembler.java:113-132`). The Assembler then caps every supply at 3 and demand at 4 with a flat mod (`applyOutputReduction`, `:206-220`). Heavy machinery demand: Assembler `size - 3`, Manufactory `size - 1`, Forge `size`. Any demand deficit reduces all supply (`applyDeficits`, `:103-106`).

### Deposits

`getDepositAmount` (`VariableAssembler.java:151-172`):

`round(round((logIncrease * ln(size - 2) + amount) / 10) * 10 * aiCoreMult * timeMult * dualMult * deficitMult)`

- `amount`, `logIncrease`: from `<industryId>_amounts` in `settings.json`.
- `aiCoreMult`: alpha 1.15, beta 1.25, else 1.
- `timeMult`: Assembler and Manufactory 0.7 for the first month after a new VPC (`toggleRampUp`, `:232-241`), Forge `3 / maxDeposits` (1.5 with alpha).
- `dualMult`: 0.8 for two output VPCs. `deficitMult`: `1 - deficit / (supply + deficit)`.

Delivery goes to the gathering point storage if `IndEvo_VarInd_deliverToProductionPoint` is on and that storage exists, else to local storage (`:246-269`).

### Commodity Forge

- `onNewDay`: counts days while a VPC is installed and unlocked; above 7 days `vpcIsLocked` (`CommodityForge.java:177-185`). Locked means `getInstallableItems()` returns nothing, so the player can not remove it (`:211-221`).
- Month end: deposit, lock, `depositCounter++`; at `maxDeposits` the VPC is deleted, a burn out message is posted and `autoFeed` loads the first VPC found in `IndEvo_SharedStore` (`:139-175`, `:113-134`).
- Adds the shared storage submarket through `SubMarketAddOrRemovePlugin` while functional; legal items there are the ten VPC ids (`:381-414`).

### AI cores and NPC markets

| Core | Assembler / Manufactory | Forge |
|---|---|---|
| Gamma | upkeep x0.9 | upkeep x0.9 |
| Beta | deposit x1.25 | upkeep x0.1 while no VPC installed |
| Alpha | deposit x1.15, upkeep x0.8 | 2 deposits instead of 3 (x1.5 each) |

NPC markets without a VPC get one random VPC by tag weight once (`AImode`, `VariableAssembler.java:174-195`).

## Contracts

- The VPC lives in `currentVPC`; `getSpecialItem` / `setSpecialItem` are overridden to read and write it, so vanilla item code sees the VPC as the special item (`VariableAssembler.java:376-393`). Removal refunds it unless the removal is an upgrade (`:395-404`); upgrade and downgrade copy it in `upgradeFinished`.
- `aiCoreMult`, `rampUpExpired`, `lastInstalledSpecialItemId`, Forge `daysPassed`, `depositCounter`, `vpcIsLocked` are saved fields on the industry.
- `ConvertVPCSpecial` reads `DUAL_OUTPUT_REDUCTION_MULT` (`indevo/exploration/salvage/specials/ConvertVPCSpecial.java:23`); see [Salvage](../exploration/SALVAGE.md).
- Shared industry storage and `SubMarketAddOrRemovePlugin`: [Submarkets](../core/SUBMARKETS.md). Setting reads: [Settings](../core/SETTINGS.md).

## Known issues and dormant code

| Item | State |
|---|---|
| Gamma core `aiCoreMult` | Suspected: no `applyGammaCoreModifiers` override, so switching alpha or beta to gamma keeps the old deposit mult (`VariableAssembler.java:515-525`; dispatch in `api/impl/campaign/econ/impl/BaseIndustry.java:246-257` in the knowledge base) |
| Forge lock at month end | The first deposit locks the VPC even if 7 days have not passed (`CommodityForge.java:144`) |
| `VPCUserIndustryAPI` | Only implemented by `VariableAssembler` and its subclasses |
| Changelog_todo | "Rework VPCs to use random tiered output system" |

## Maintenance

Update this doc when the deposit formula, size overrides, caps, AI core effects or Forge timing change. Per VPC commodity tables belong in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
