# Variable industries (Assembler, Manufactory, Commodity Forge)

Covers the three industries that run on a Variable Production Chip (VPC): Variable Assembler `IndEvo_AdAssem`, Variable Manufactory `IndEvo_AdManuf` and Commodity Forge `IndEvo_ComForge`, plus the monthly delivery message. Read it before changing VPC output, deposit amounts or the Forge burn out. It does not cover the VPC item, its tiers and the commodity table; see [Installables](../items/INSTALLABLES.md#vpcs). The shared industry storage the Forge feeds from is in [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

All three hold one VPC in the vanilla special item slot. The VPC sets market supply and demand for one to three commodities, and at each month end the industry also deposits a fixed amount of those commodities into local storage or the production gathering point. The Assembler is a structure capped at 3 supply and 4 demand per commodity; it upgrades to the Manufactory, an industry with no cap and higher deposits. The Commodity Forge locks its VPC after 7 days or the first deposit and destroys it after 3 deposits (2 with an alpha core), then auto loads the next VPC from shared industry storage.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/assembler/industry/VPCUserIndustryAPI.java` | Contract for `VPCEffect` and `DepositMessage`: `vpcUnapply`, `hasVPC`, `getDepositList` |
| `indevo/industries/assembler/industry/VariableAssembler.java` | Base for all three: VPC supply and demand, output cap, deposit formula, month end delivery, AI cores, NPC random VPC, VPC slot |
| `indevo/industries/assembler/industry/VariableManufactory.java` | Manufactory: size override +1, no output cap, extra heavy machinery demand |
| `indevo/industries/assembler/industry/CommodityForge.java` | Forge: lock timer, burn out after `maxDeposits`, auto feed from `IndEvo_SharedStore`, `SharedSubmarketUserAPI`, `NewDayListener` |
| `indevo/industries/assembler/listeners/DepositMessage.java` | Transient `EconomyTickListener`: one month end message summing deposits of all player VPC industries |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_AdAssem` (structure, upgrade `IndEvo_AdManuf`), `IndEvo_AdManuf` (industry, downgrade `IndEvo_AdAssem`), `IndEvo_ComForge` (structure) | `data/campaign/industries.csv:2,5,8`; `indevo/ids/Ids.java:51-53` |
| Setting | `IndEvo_AdAssem`, `IndEvo_AdManuf`, `IndEvo_ComForge` (build toggles) | `data/config/LunaSettings.csv:86-88` |
| Setting | `IndEvo_VarInd_deliverToProductionPoint` (deliver to gathering point instead of local storage) | `data/config/LunaSettings.csv:115` |
| Config | Base deposit per commodity: columns `IndEvo_AdAssem`, `IndEvo_AdManuf`, `IndEvo_ComForge` of the VPC commodity table | `data/config/indEvo/vpc_commodities.csv`, read through `VPCCommodityRepo.Commodity.getDeposit(getId())` |
| Constant | `LOG_INCREASE`: Assembler 55, Manufactory 150, Forge 250 | `VariableAssembler.java:36`, `VariableManufactory.java:9`, `CommodityForge.java:35` |
| Sprites | category `IndEvo`: `assembler`, `manufactorium`, `comforge` (active image), `VPCRemovalIcon` | `data/config/settings.json:130-135` |
| Strings | `IndEvo_VarInd`, `IndEvo_AdAssem`, `IndEvo_ComForge` | `data/strings/strings.json:47`, `:55`, `:61` |
| Whitelist | All three are valid ruin upgrade targets | `data/config/indEvo/ruins_industry_whitelist.csv:5-7` |

## Registration and lifecycle

- Industry plugins from `industries.csv`. Each `apply()` adds the industry itself as a transient listener (`EconomyTickListener`, Forge also `NewDayListener`) and `unapply()` removes it (`VariableAssembler.java:48,79`, `CommodityForge.java:55,90`).
- `DepositMessage.register()` in `ModPlugin.setListenersIfNeeded()` (`indevo/utils/ModPlugin.java:435`), transient, re-added each load.
- `NewGameIndustryPlacer.placeManufactories()` puts a Manufactory with an uncommon ship parts VPC on `eochu_bres` at new game (`indevo/utils/update/NewGameIndustryPlacer.java:45-62`).
- `initWithParams`: vanilla preinstalls `IndEvo_vpc` when an econ file lists it; a VPC without data then gets a random NPC roll, which can swap in the rare or ultra rare id (`VariableAssembler.java:323-328`).

Apply chain: `VariableAssembler.apply -> AImode (NPC) -> applyVPCEffects -> VPCItemPlugin.getSupply / getDemand -> setSupply / setDemand -> applyOutputReduction (Assembler only) -> applyDeficits -> BaseIndustry.apply(true)`. `BaseIndustry.apply` then calls `VPCEffect.apply`, which does nothing: the effect runs after supply and demand are read, so the industries read `special` themselves.

Month end chain: `reportEconomyMonthEnd -> getDepositList -> getDepositAmount -> cargo.addCommodity` (storage or gathering point) and `DepositMessage.reportEconomyMonthEnd -> MessageIntel`.

## Flow

### Supply and demand

`setSupply` / `setDemand` use `size = market size + getVPCMarketSizeOverride()` (Assembler -1, Manufactory +1, Forge +2), +1 more if the result is 3 or less, then add each VPC modifier (`VariableAssembler.java:101-118`). The modifiers come from the VPC commodity table: one supply modifier per output (with 2 or 3 outputs at most `-outputs`) and the highest demand modifier per input commodity ([Installables](../items/INSTALLABLES.md#vpcs)). The Assembler then caps every supply at 3 and demand at 4 with a flat mod (`applyOutputReduction`, `:175-189`). Heavy machinery demand: Assembler `size - 3`, Manufactory `size - 1`, Forge `size`. Any demand deficit reduces all supply (`applyDeficits`, `:91-94`).

### Deposits

`getDepositAmount` (`VariableAssembler.java:136-146`), static form shared with `ConvertVPCSpecial`:

`round(round((logIncrease * ln(size - 2) + amount) / 10) * 10 * aiCoreMult * timeMult * outputMult * deficitMult)`

- `amount`: the industry's column in `vpc_commodities.csv`. `logIncrease`: `getLogIncrease()`, the class `LOG_INCREASE`.
- `aiCoreMult`: alpha 1.15, beta 1.25, else 1.
- `timeMult`: `getTimeMult()`. Assembler and Manufactory 0.7 for the first month after a new VPC (`toggleRampUp`, `:202-207`), Forge `3 / maxDeposits` (1.5 with alpha).
- `outputMult`: `VPCItemPlugin.getDepositMult`, 1, 0.8 or 0.65 for 1, 2 or 3 outputs. `deficitMult`: `1 - deficit / (supply + deficit)`.

Delivery goes to the gathering point storage if `IndEvo_VarInd_deliverToProductionPoint` is on and that storage exists, else to local storage (`:212-235`). The tooltip lists monthly deliveries separately from market supply, with an amount for each commodity (`addOutputLine`, `:271-279`).

### Commodity Forge

- `onNewDay`: counts days while a VPC is installed and unlocked; above 7 days `vpcIsLocked` (`CommodityForge.java:175-182`). Locked means `getInstallableItems()` returns nothing, so the player can not remove it (`:195-197`).
- Month end: deposit, lock, `depositCounter++`; at `maxDeposits` the VPC is deleted, a burn out message is posted and `autoFeed` loads the first valid VPC found in `IndEvo_SharedStore` (`:136-165`, `:110-133`).
- Keeps the shared storage submarket through `IndustrySubmarkets.ensure` in `advance()` and `release` in `notifyBeingRemoved()` (`:77`, `:83`); any VPC stack is legal there (`CommodityForge.java:337-339`).

### AI cores and NPC markets

| Core | Assembler / Manufactory | Forge |
|---|---|---|
| Gamma | upkeep x0.9 | upkeep x0.9 |
| Beta | deposit x1.25 | upkeep x0.1 while no VPC installed |
| Alpha | deposit x1.15, upkeep x0.8 | 2 deposits instead of 3 (x1.5 each) |

NPC markets without a VPC get one random VPC once, tier by `Tier.npcWeight` 70/25/5/1 (`AImode`, `VariableAssembler.java:156-164`; `VPCItemPlugin.Tier.pickForNPC`). Pather interest adds `Tier.patherInterest` (1, 2, 4, 6) on top of the vanilla item tags (`getPatherInterest`, `:295-298`).

## Contracts

- The VPC is the vanilla `special` field. `BaseIndustry` refunds it on removal unless the removal is an upgrade, copies it on upgrade and downgrade, and calls `VPCEffect.unapply` (which calls `vpcUnapply`) when it is replaced or the industry unapplies.
- The slot is a `NamedItemSlotPlugin` labelled "VPC" (`VariableAssembler.java:315-320`). Slot, install dialog and the vanilla installed items tooltip show the spec icon, the chip of the item id.
- `hasVPC()` is true for any of the three VPC ids (`VariableAssembler.java:331-333`, `VPCItemPlugin.isVPC`). An installed VPC with unreadable data throws `IllegalStateException` on apply; tooltips show it as invalid first.
- `aiCoreMult`, `rampUpExpired`, `lastInstalledVPC` (a `SpecialItemData`), Forge `daysPassed`, `depositCounter`, `vpcIsLocked` are saved fields on the industry. The ramp up and Forge lock timers restart when the installed `SpecialItemData` changes.
- `ConvertVPCSpecial` uses the static `getDepositAmount` and `CommodityForge.LOG_INCREASE`; see [Salvage](../exploration/SALVAGE.md).
- Shared industry storage and `IndustrySubmarkets`: [Submarkets](../core/SUBMARKETS.md). Setting reads: [Settings](../core/SETTINGS.md).

## Known issues and dormant code

| Item | State |
|---|---|
| Forge lock at month end | The first deposit locks the VPC even if 7 days have not passed (`CommodityForge.java:141`) |
| `VPCUserIndustryAPI` | Only implemented by `VariableAssembler` and its subclasses |

## Maintenance

Update this doc when the deposit formula, size overrides, caps, AI core effects or Forge timing change. The VPC tiers and the commodity table belong in [Installables](../items/INSTALLABLES.md#vpcs). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
