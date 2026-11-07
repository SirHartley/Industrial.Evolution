# Submarkets

IE's submarket framework in `indevo/submarkets/`: the submarket rows, the shared plugin implementations, and how industries add and remove their submarkets. Read it before adding a submarket or changing what happens to its cargo on removal. Feature specific behaviour (what a ship in the dock does, what the Requisitions Centre stocks and why) lives in the feature docs linked below.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`; the add and remove flow against `dev` at `9d4f033`. Recheck engine facts after a game update.

## Overview

Each IE submarket is a row in `data/campaign/submarkets.csv` with a plugin class. `IndustrySubmarkets` owns adding and removing them: the owning industry calls `ensure` from `advance()` and `release` from `notifyBeingRemoved`. Removal moves storage cargo to colony storage, calls the plugin's `RemovableSubmarketPluginAPI.notifyBeingRemoved()` and drops the submarket in the same call; no sector scripts are involved.

## Files

| File | Owner / role |
|---|---|
| `indevo/submarkets/IndustrySubmarkets.java` | Owner of add and remove: owner table, `ensure`, `release`, `remove`, `removeOrphans` |
| `indevo/submarkets/RemovableSubmarketPluginAPI.java` | Plugin hook: `notifyBeingRemoved()` runs right before `IndustrySubmarkets.remove` drops the submarket |
| `indevo/submarkets/BaseRemovableStorageSubmarketPlugin.java` | `StoragePlugin` that starts unlocked (`setPlayerPaidToUnlock(true)`); base of the museum and warehouse plugins |
| `indevo/submarkets/impl/RestorationDocksSubmarketPlugin.java` | `IndEvo_RepairStorage`: fleet screen only; accepts ships with removable D-mods that are not `VARIANT_UNRESTORABLE`/`HULL_UNRESTORABLE`; tooltip shows the cost prediction from `RestorationDocks`. Feature: [Small industries](../industries/SMALL_INDUSTRIES.md) |
| `indevo/submarkets/impl/RequisitionsCenterSubmarketPlugin.java` | `IndEvo_ReqCenterMarket`: open weapons market, buy only, 35000 credit unlock on non player markets, stock by faction standing, ambassador and AI core. Feature: [Small industries](../industries/SMALL_INDUSTRIES.md), [Embassy](../industries/EMBASSY.md) |
| `indevo/submarkets/impl/DeconstructorSubmarketPlugin.java` | `IndEvo_DeconstStorage`: fleet screen only; ship whitelist from `Ids.PRINT_LIST`, blacklist prism and Vayra boss ships. Feature: [Derelicts](../industries/DERELICTS.md) |
| `indevo/submarkets/impl/ReverseEngineeringSubmarketPlugin.java` | `IndEvo_EngStorage`: fleet screen only; whitelist `Ids.REVERSE_LIST` plus any hull with a `_bp` tag unless strict; blacklists `threat`/`dweller`, prism, Vayra; tooltip shows research progress. Feature: [Engineering Hub](../industries/ENGINEERING_HUB.md) |
| `indevo/submarkets/impl/SharedSubmarketPlugin.java` | `IndEvo_SharedStore`: cargo screen only, free transfer; an item is legal if any industry on the market implementing `SharedSubmarketUserAPI` accepts it. Users: `indevo/industries/SharedSubmarketUser.java`, [Assembler](../industries/ASSEMBLER.md) `CommodityForge` |

Feature submarkets with their plugin outside this package:

| Id | Plugin | Doc |
|---|---|---|
| `IndEvo_PetSubMarket` | `indevo/industries/petshop/submarket/PetSubMarket.java` | [Pets](../industries/PETS.md) |
| `IndEvo_MuseumStorage` | `indevo/industries/museum/submarket/MuseumSubmarketPlugin.java` (extends `BaseRemovableStorageSubmarketPlugin`) | [Museum](../industries/MUSEUM.md) |
| `IndEvo_WarehouseStorage_1` to `_5` | `indevo/industries/warehouses/submarket/WarehouseSubmarketPlugin.java` (extends `BaseRemovableStorageSubmarketPlugin`) | [Warehouses](../industries/WAREHOUSES.md) |
| `IndEvo_tempStorage` | vanilla `StoragePlugin` | `ModUpdater` swap space, see [Utils](UTILS.md#update) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Submarket | `IndEvo_RepairStorage` (`Ids.REPSTORAGE`), `IndEvo_ReqCenterMarket` (`Ids.REQMARKET`), `IndEvo_DeconstStorage` (`Ids.DECSTORAGE`), `IndEvo_EngStorage` (`Ids.ENGSTORAGE`), `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`), `IndEvo_PetSubMarket` (`Ids.PETMARKET`), `IndEvo_MuseumStorage`, `IndEvo_WarehouseStorage_1..5`, `IndEvo_tempStorage` (`Ids.TEMPSTORE`) | `data/campaign/submarkets.csv` (faction `player` for all) |
| Faction (colour only) | `indStorageColour`, `deconStorageColour`, `engHubStorageColour`, `dryDockButtonColor`, `reqSellStorageColour` | `submarket.setFaction` in each plugin's `init` |
| Whitelist | `IndEvo_printList`, `IndEvo_reverseList`, `IndEvo_custOrderList` (faction blacklist for Requisitions) | `data/config/settings.json:53` to `:56`; see [Settings](SETTINGS.md) |
| Setting | `IndEvo_DeconIgnoreWhitelists`, `IndEvo_DeconAllowNexOmega`, `IndEvo_EngHubIgnoreWhitelists`, `IndEvo_EngHubStrictWhitelist` | `data/config/LunaSettings.csv:151`, `:152`, `:136`, `:135` |
| Memory key | `$IndEvo_maxDModMemory` (read in dock tooltip) | `RestorationDocksSubmarketPlugin.java:135` |
| Hullmod | `IndEvo_auto` (`Ids.PRINTING_INDICATOR`): printed ships are refused | Decon and RE plugins |

## Registration and lifecycle

- CSV `script` column -> engine creates the plugin on `market.addSubmarket(id)`.
- Owner table (`IndustrySubmarkets.java:27-38`):

| Submarket | Owner industry | Player only | Cargo on removal |
|---|---|---|---|
| `IndEvo_RepairStorage` | `IndEvo_dryDock` | no | moved to storage |
| `IndEvo_ReqCenterMarket` | `IndEvo_ReqCenter` | no | dropped |
| `IndEvo_DeconstStorage` | `IndEvo_HullDecon` | no | moved |
| `IndEvo_EngStorage` | `IndEvo_EngHub` | no | moved |
| `IndEvo_PetSubMarket` | `IndEvo_PetStore` | no | dropped |
| `IndEvo_MuseumStorage` | `IndEvo_Museum` | yes | moved |
| `IndEvo_SharedStore` | any `SharedSubmarketUserAPI` industry | yes | moved |
| `IndEvo_WarehouseStorage_1` to `_5` | `IndEvo_Warehouse` | yes | moved |

- Add: owner `advance()` -> `IndustrySubmarkets.ensure(this, id)` (`:46`) -> `market.addSubmarket(id)` while the industry is functional; the engine skips an id already present (`obf/campaign/econ/Market.java:505`), so the per frame call is cheap. Callers: `RestorationDocks.java:75`, `RequisitionCenter.java:42`, `HullDeconstructor.java:75`, `EngineeringHub.java:82`, `SharedSubmarketUser.java:13` (shared storage for every subclass), `CommodityForge.java:78`, `PetShop.java:182` (while `IndEvo_pets` is on, `remove` otherwise), `Museum.java:128` (while player owned).
- Ownership loss: `ensure` for a player only id on a market that is no longer player owned calls `remove` instead. Museum checks ownership itself first and runs its own `notifyBeingRemoved` (parades).
- Remove: owner `notifyBeingRemoved(mode, forUpgrade)` -> `IndustrySubmarkets.release(this)` (`:55`) -> `remove` for every id the industry owns that no other industry on the market owns. The leaving industry is still in the market's list at that point (`Market.java:259-270`), so it is skipped by identity. Callers: `RestorationDocks.java:81`, `RequisitionCenter.java:48`, `SharedSubmarketUser.java:19` (decon, eng hub and shared storage), `CommodityForge.java:84`, `PetShop.java:189`, `Museum.java:351`.
- `remove(market, id)` (`:66`): for cargo keeping ids moves cargo and mothballed ships to `MiscIE.getStorage(market)` and unlocks it; then `RemovableSubmarketPluginAPI.notifyBeingRemoved()`, then `market.removeSubmarket(id)`. Warehouses call it for their player driven removal (`Warehouses.java:159`) and drop their data only when it returns true.
- Load: `ModPlugin.onGameLoad` -> `IndustrySubmarkets.removeOrphans()` (`:76`) removes every registered submarket whose market has no owner industry.

Engine facts behind this (knowledge skill, 0.98a-RC8):

- `Market.removeIndustry` calls `notifyBeingRemoved(mode, forUpgrade)` then `unapply()`; `reapplyIndustries` calls `unapply()` then `apply()` on every industry (`obf/campaign/econ/Market.java:259-279`), and `startBuilding` calls `unapply()` (`api/impl/campaign/econ/impl/BaseIndustry.java:551`). `unapply()` is no removal signal.
- Upgrades and downgrades both go through `market.removeIndustry(getId(), null, true)` then `addIndustry(upgradeId)` (`BaseIndustry.java:559-560`). `release` runs for them too: no IE upgrade or downgrade target owns the same submarket (decon, forge and lab downgrade to `IndEvo_Ruins`, `data/campaign/industries.csv`).
- `Market.advance` runs submarket plugins first, then industries over a copy (`Market.java:433`, `:458`), so adding or removing a submarket from industry `advance` is safe.
- Industry tooltip previews never call `advance` or `notifyBeingRemoved`; the preview copy is dropped with `getIndustries().remove(this)` (`BaseIndustry.java:1291-1293`).
- `DecivTracker.decivilize` removes every industry, then every submarket including storage (`api/impl/campaign/intel/deciv/DecivTracker.java:227`, `:263`), so IE cargo moved to storage on decivilization is lost with it, as vanilla storage is.

## Flow

### Requisitions Centre stock

`updateCargoPrePlayerInteraction` (`RequisitionsCenterSubmarketPlugin.java:37`) -> clears commodities -> when `okToUpdateShipsAndWeapons()`: `pruneWeapons(0)` then

- gamma core or no ambassador: mixed pick from `getActiveFactionList(0.35, player)` (standing >= 0.35, not inactive, not in `IndEvo_custOrderList`), count `5 + size - 1 + factions (+5 military, +5 alpha)`;
- ambassador present: single faction of the ambassador, `7 + size - 1 (+5, +5)`.

Max weapon tier by standing (`getMaxTier`): <0.5 -> 0, <0.7 -> 1, <0.9 -> 2, else 3; beta core +1 tier and drops tier 0. One weapon per autofit category (PD, kinetic, missile, non kinetic, strike) first, rest from the full picker. Alpha core: larger stacks and rerolls weapons with more than 10 copies across all storages. Tariff 40% player owned, 60% otherwise. `writeReplace` prunes weapons before saving.

### Whitelist checks (Decon, RE)

`updateCargoPrePlayerInteraction` rebuilds `allowedShips` and `restrictedShips` from `MiscIE.getCSVSetFromMemory`, `getPrismBossShips()`, `getVayraBossShips()` -> `isIllegalOnSubmarket(member)` returns `restricted || notAllowed || printed` (RE also `getResearchValue <= 0`). Dev mode allows everything. Ignore whitelist settings skip the lists. Decon refuses Nexerelin merc ships (`nex_mercBuff` buff) unless `IndEvo_DeconAllowNexOmega`.

## Contracts

- Every IE submarket an industry adds has a row in the owner table. `ensure` and `remove` throw for an unregistered id; `ensure` throws when the calling industry does not own the id.
- Only `IndustrySubmarkets` removes these submarkets, and only Warehouses add one directly (player driven, `Warehouses.java:151`).
- Never remove a submarket from a submarket plugin's own `advance`: the engine loops over the submarket list without a copy (`Market.java:433`), so that would throw a `ConcurrentModificationException` (inferred).
- Storage target is `MiscIE.getStorage(market)`, which falls back to RAT `rat_settlement_storage` ([Utils](UTILS.md#miscie)). The unlock is set only when that plugin is a `StoragePlugin`. No storage: `remove` logs a warning, keeps the IE submarket with its cargo and returns false.
- A shared storage user upgrading into another user would have its storage moved to colony storage and re added empty. No such upgrade exists.
- Plugins with `isFreeTransfer()` true and `isParticipatesInEconomy()` false behave as storage: no tariff, no market effect.

## Known issues and dormant code

| Item | State |
|---|---|
| `ReverseEngineeringSubmarketPlugin.roleListings` | `@Deprecated`, unused |
| `ReverseEngineeringSubmarketPlugin.java:60` | `getHullSpec(id)` for every faction known ship; throws on a missing hull id (engine fact: spec getters throw on missing ids, knowledge base `docs/concepts/spec-loading.md`) |
| `RestorationDocksSubmarketPlugin.createTooltipAfterDescription` (`:130`) | Casts `market.getIndustry(REPAIRDOCKS)` without a null check |
| `isSetForRemoval` | Saved field on six plugins, set by `notifyBeingRemoved` right before the submarket is dropped, so the UI checks on it never show. Removing the field breaks saves |
| Changelog_todo | "museum storage gets placed before building complete", "museum storage doesn't remove properly", "warehouse storage linking", "implement 5 more shared storage variants", "check resto dock billing for bugs", "Requisition center RAT settlement plugin" |

## Maintenance

Update this doc when a submarket row, plugin or the add/remove contract changes. What a submarket does for its feature belongs in that feature's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
