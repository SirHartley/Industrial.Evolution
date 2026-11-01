# Submarkets

IE's submarket framework in `indevo/submarkets/`: the submarket rows, the shared plugin implementations, and how industries add and remove their submarkets. Read it before adding a submarket or changing what happens to its cargo on removal. Feature specific behaviour (what a ship in the dock does, what the Requisitions Centre stocks and why) lives in the feature docs linked below.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Each IE submarket is a row in `data/campaign/submarkets.csv` with a plugin class. Industries add the submarket while functional and remove it when gone. Two removal contracts exist: the older `DynamicSubmarket` (removal through `SubMarketAddOrRemovePlugin`, cargo moved to vanilla storage for most ids) and `RemovablePlayerSubmarketPluginAPI` (`notifyBeingRemoved()` moves cargo to storage, used by Museum and Warehouses).

## Files

| File | Owner / role |
|---|---|
| `indevo/submarkets/DynamicSubmarket.java` | Older removal contract: `prepareForRemoval()` sets the plugin's `isSetForRemoval` |
| `indevo/submarkets/RemovablePlayerSubmarketPluginAPI.java` | Current removal contract: `notifyBeingRemoved()` |
| `indevo/submarkets/BaseRemovableStorageSubmarketPlugin.java` | `StoragePlugin` that starts unlocked (`setPlayerPaidToUnlock(true)`); `notifyBeingRemoved()` -> `moveCargoToStorage()` (cargo and mothballed ships to `MiscIE.getStorageCargo(market)`) |
| `indevo/submarkets/script/SubMarketAddOrRemovePlugin.java` | One shot sector script: add `submarketID`, or remove it if its industry is gone (transferring cargo for some ids) |
| `indevo/submarkets/impl/RestorationDocksSubmarketPlugin.java` | `IndEvo_RepairStorage`: fleet screen only; accepts ships with removable D-mods that are not `VARIANT_UNRESTORABLE`/`HULL_UNRESTORABLE`; tooltip shows the cost prediction from `RestorationDocks`. Feature: [Small industries](../industries/SMALL_INDUSTRIES.md) |
| `indevo/submarkets/impl/RequisitionsCenterSubmarketPlugin.java` | `IndEvo_ReqCenterMarket`: open weapons market, buy only, 35000 credit unlock on non player markets, stock by faction standing, ambassador and AI core. Feature: [Small industries](../industries/SMALL_INDUSTRIES.md), [Embassy](../industries/EMBASSY.md) |
| `indevo/submarkets/impl/DeconstructorSubmarketPlugin.java` | `IndEvo_DeconstStorage`: fleet screen only; ship whitelist from `Ids.PRINT_LIST`, blacklist prism and Vayra boss ships. Feature: [Derelicts](../industries/DERELICTS.md) |
| `indevo/submarkets/impl/ReverseEngineeringSubmarketPlugin.java` | `IndEvo_EngStorage`: fleet screen only; whitelist `Ids.REVERSE_LIST` plus any hull with a `_bp` tag unless strict; blacklists `threat`/`dweller`, prism, Vayra; tooltip shows research progress. Feature: [Engineering Hub](../industries/ENGINEERING_HUB.md) |
| `indevo/submarkets/impl/SharedSubmarketPlugin.java` | `IndEvo_SharedStore`: cargo screen only, free transfer; an item is legal if any industry on the market implementing `SharedSubmarketUserAPI` accepts it. Users: `indevo/industries/SharedSubmarketUser.java`, [Assembler](../industries/ASSEMBLER.md) `CommodityForge` |

Feature submarkets with their plugin outside this package:

| Id | Plugin | Doc |
|---|---|---|
| `IndEvo_PetSubMarket` | `indevo/industries/petshop/submarket/PetSubMarket.java` (`DynamicSubmarket`) | [Pets](../industries/PETS.md) |
| `IndEvo_MuseumStorage` | `indevo/industries/museum/submarket/MuseumSubmarketPlugin.java` (extends `BaseRemovableStorageSubmarketPlugin`) | [Museum](../industries/MUSEUM.md) |
| `IndEvo_WarehouseStorage_1` to `_5` | `indevo/industries/warehouses/submarket/WarehouseSubmarketPlugin.java` (extends `BaseRemovableStorageSubmarketPlugin`) | [Warehouses](../industries/WAREHOUSES.md) |
| `IndEvo_tempStorage` | vanilla `StoragePlugin` | `ModUpdater` swap space, see [Utils](UTILS.md#update) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Submarket | `IndEvo_RepairStorage` (`Ids.REPSTORAGE`), `IndEvo_ReqCenterMarket` (`Ids.REQMARKET`), `IndEvo_DeconstStorage` (`Ids.DECSTORAGE`), `IndEvo_EngStorage` (`Ids.ENGSTORAGE`), `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`), `IndEvo_PetSubMarket` (`Ids.PETMARKET`), `IndEvo_MuseumStorage`, `IndEvo_WarehouseStorage_1..5`, `IndEvo_tempStorage` (`Ids.TEMPSTORE`) | `data/campaign/submarkets.csv` (faction `player` for all) |
| Faction (colour only) | `indStorageColour`, `deconStorageColour`, `engHubStorageColour`, `dryDockButtonColor`, `reqSellStorageColour` | `submarket.setFaction` in each plugin's `init` |
| Whitelist | `IndEvo_printList`, `IndEvo_reverseList`, `IndEvo_custOrderList` (faction blacklist for Requisitions) | `data/config/settings.json:53` to `:56`; see [Settings](SETTINGS.md) |
| Setting | `IndEvo_DeconIgnoreWhitelists`, `IndEvo_DeconAllowNexOmega`, `IndEvo_EngHubIgnoreWhitelists`, `IndEvo_EngHubStrictWhitelist` | `data/config/LunaSettings.csv:134`, `:135`, `:108`, `:107` |
| Memory key | `$IndEvo_maxDModMemory` (read in dock tooltip) | `RestorationDocksSubmarketPlugin.java:135` |
| Hullmod | `IndEvo_auto` (`Ids.PRINTING_INDICATOR`): printed ships are refused | Decon and RE plugins |

## Registration and lifecycle

- CSV `script` column -> engine creates the plugin on `market.addSubmarket(id)`.
- Add: industry `apply()` -> `Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, id, false))` -> next frame `market.addSubmarket(id)`. Callers: `RestorationDocks.java:75`, `RequisitionCenter.java:42`, `HullDeconstructor.java:76`, `EngineeringHub.java:84`, `SharedSubmarketUser.java:11`, `CommodityForge.java:407`, `PetShop.java:180`.
- Remove: industry `unapply()` / removal -> same script with `true` -> `removeSubMarket()` switch on the id -> if the owning industry is gone: optional `moveContentsToStorage`, `((DynamicSubmarket) plugin).prepareForRemoval()`, `market.removeSubmarket(id)`.
- Museum and Warehouses add directly (`Museum.java:126`) and remove through `RemovablePlayerSubmarketPluginAPI.notifyBeingRemoved()` then `removeSubmarket` (`Warehouses.java:159`, `:166`).

| Removed id | Owner check | Cargo |
|---|---|---|
| `IndEvo_PetSubMarket` | no `IndEvo_PetStore`, or `IndEvo_pets` off | dropped |
| `IndEvo_RepairStorage` | no `IndEvo_dryDock` | moved to storage |
| `IndEvo_ReqCenterMarket` | no `IndEvo_ReqCenter` | dropped |
| `IndEvo_DeconstStorage` | no `IndEvo_HullDecon` | moved |
| `IndEvo_EngStorage` | no `IndEvo_EngHub` | moved |
| `IndEvo_SharedStore` | no `SharedSubmarketUserAPI` industry left | moved |

## Flow

### Requisitions Centre stock

`updateCargoPrePlayerInteraction` (`RequisitionsCenterSubmarketPlugin.java:37`) -> clears commodities -> when `okToUpdateShipsAndWeapons()`: `pruneWeapons(0)` then

- gamma core or no ambassador: mixed pick from `getActiveFactionList(0.35, player)` (standing >= 0.35, not inactive, not in `IndEvo_custOrderList`), count `5 + size - 1 + factions (+5 military, +5 alpha)`;
- ambassador present: single faction of the ambassador, `7 + size - 1 (+5, +5)`.

Max weapon tier by standing (`getMaxTier`): <0.5 -> 0, <0.7 -> 1, <0.9 -> 2, else 3; beta core +1 tier and drops tier 0. One weapon per autofit category (PD, kinetic, missile, non kinetic, strike) first, rest from the full picker. Alpha core: larger stacks and rerolls weapons with more than 10 copies across all storages. Tariff 40% player owned, 60% otherwise. `writeReplace` prunes weapons before saving.

### Whitelist checks (Decon, RE)

`updateCargoPrePlayerInteraction` rebuilds `allowedShips` and `restrictedShips` from `MiscIE.getCSVSetFromMemory`, `getPrismBossShips()`, `getVayraBossShips()` -> `isIllegalOnSubmarket(member)` returns `restricted || notAllowed || printed` (RE also `getResearchValue <= 0`). Dev mode allows everything. Ignore whitelist settings skip the lists. Decon refuses Nexerelin merc ships (`nex_mercBuff` buff) unless `IndEvo_DeconAllowNexOmega`.

## Contracts

- `SubMarketAddOrRemovePlugin.removeSubmarket` casts the plugin to `DynamicSubmarket` (`script/SubMarketAddOrRemovePlugin.java:103`); a submarket removed through it must implement that interface.
- The removal switch falls through on purpose or by accident: a call for one id also removes any later id whose industry is gone (`script/SubMarketAddOrRemovePlugin.java:53` to `97`). Inferred; no comment states intent.
- Storage target is `MiscIE.getStorageCargo(market)`, which falls back to RAT `rat_settlement_storage` ([Utils](UTILS.md#miscie)). `moveContentsToStorage` itself requires vanilla `storage` (`:111`).
- Plugins with `isFreeTransfer()` true and `isParticipatesInEconomy()` false behave as storage: no tariff, no market effect.

## Known issues and dormant code

| Item | State |
|---|---|
| `ReverseEngineeringSubmarketPlugin.roleListings` | `@Deprecated`, unused |
| `ReverseEngineeringSubmarketPlugin.java:60` | `getHullSpec(id)` for every faction known ship; throws on a missing hull id (engine fact: spec getters throw on missing ids, knowledge base `docs/concepts/spec-loading.md`) |
| `RestorationDocksSubmarketPlugin.createTooltipAfterDescription` (`:130`) | Casts `market.getIndustry(REPAIRDOCKS)` without a null check |
| `DynamicSubmarket` | Marked "old implementation" in its javadoc; still used by six plugins |
| Changelog_todo | "museum storage gets placed before building complete", "museum storage doesn't remove properly", "warehouse storage linking", "implement 5 more shared storage variants", "check resto dock billing for bugs", "Requisition center RAT settlement plugin" |

## Maintenance

Update this doc when a submarket row, plugin or the add/remove contract changes. What a submarket does for its feature belongs in that feature's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
