# Small industries

Industries that are a single class in `indevo/industries/` (Centralization Bureau, Supercomputer, Restoration Docks, Requisitions Centre, Commerce override, invisible planetary shield), the shared storage base class, and two dead classes. Read the index first to find the doc for any IE industry id. It does not cover the submarkets these industries add; see [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Industry index

Every IE row of `data/campaign/industries.csv` (plugin column, paths relative to `indevo/industries/`).

| Id | Line | Class | Doc |
|---|---|---|---|
| `IndEvo_AdManuf`, `IndEvo_AdAssem`, `IndEvo_ComForge` | 2, 5, 8 | `assembler/industry/VariableManufactory`, `VariableAssembler`, `CommodityForge` | [Assembler](ASSEMBLER.md) |
| `IndEvo_ComArray`, `IndEvo_IntArray` | 11, 16 | `relay/industry/MilitaryRelay` | [Relay](RELAY.md) |
| `IndEvo_AdInfra` | 21 | `CentralizationBureau` | this doc |
| `IndEvo_ScrapYard` | 22 | `salvageyards/industry/SalvageYards` | [Salvage Yards](SALVAGE_YARDS.md) |
| `IndEvo_SupCom` | 25 | `Supercomputer` | this doc |
| `IndEvo_embassy` | 28 | `embassy/industry/Embassy` | [Embassy](EMBASSY.md) |
| `IndEvo_senate` | 31 | `senate/industry/Senate` | [Senate](SENATE.md) |
| `IndEvo_dryDock` | 32 | `RestorationDocks` | this doc |
| `IndEvo_Academy` | 35 | `academy/industry/Academy` | [Academy](ACADEMY.md) |
| `IndEvo_ReqCenter` | 38 | `RequisitionCenter` | this doc |
| `IndEvo_EngHub` | 42 | `engineeringhub/industry/EngineeringHub` | [Engineering Hub](ENGINEERING_HUB.md) |
| `IndEvo_PrivatePort` | 45 | `courierport/industry/CourierPortV2` | [Courier Port](COURIER_PORT.md) |
| `IndEvo_pirateHaven`, `IndEvo_pirateHavenSecondary` | 48, 90 | `privateer/industry/PrivateerBase`, `PrivateerBaseSlotBlocker` | [Privateer](PRIVATEER.md) |
| `IndEvo_SwitchableRefining`, `IndEvo_SwitchableMining`, `IndEvo_SwitchablePopulation` | 51-53 | `changeling/industry/...` | [Changeling](CHANGELING.md) |
| `IndEvo_PetStore` | 54 | `petshop/industry/PetShop` | [Pets](PETS.md) |
| `IndEvo_Museum` | 55 | `museum/industry/Museum` | [Museum](MUSEUM.md) |
| `IndEvo_Warehouse`, `#IndEvo_Warphouse` (commented) | 58, 61 | `warehouses/industry/Warehouses` | [Warehouses](WAREHOUSES.md) |
| `IndEvo_Artillery_base`, `_mortar`, `_missile`, `_railgun` | 64, 67, 74, 81 | `artillery/industry/ArtilleryStation` | [Artillery](ARTILLERY.md) |
| `IndEvo_ChamelionIndustry`, `IndEvo_RuinedInfra` | 91, 102 | `ruinfra/industry/ChamelionIndustry`, `RuinedInfrastructure` | [Ruined Infrastructure](RUINED_INFRASTRUCTURE.md) |
| `IndEvo_InvisiblePlanetaryShield` | 92 | `InvisiblePlanetaryShield` | this doc |
| `IndEvo_HullDecon`, `IndEvo_HullForge`, `IndEvo_ResLab`, `IndEvo_RiftGen`, `IndEvo_Ruins` | 95-99 | `derelicts/industry/HullDeconstructor`, `HullForge`, `AncientLaboratory`, `RiftGenerator`, `Ruins` | [Derelicts](DERELICTS.md) |
| `IndEvo_BaseFlavourInd` and nine wonders | 107-134 | `worldwonder/industry/WorldWonder` and subclasses | [World Wonders](WORLD_WONDER.md) |
| `commerce` (replaces the vanilla row) | 141 | `TradeCenter` | this doc |

A mod CSV row with a vanilla id replaces the whole vanilla row (skill `docs/concepts/csv-json-merging.md:24-26`).

## Overview

Each industry here is one class with its CSV row and an enable setting of the same name in `data/config/LunaSettings.csv` (`IndEvo_AdInfra` :89, `IndEvo_SupCom` :91, `IndEvo_dryDock` :94, `IndEvo_ReqCenter` :96). Several add a player submarket through `SubMarketAddOrRemovePlugin` ([Submarkets](../core/SUBMARKETS.md)). Special item effects for the Logistics Core and Simulation Engine are listed in [Installables](../items/INSTALLABLES.md); the industries read `getSpecialItem()` directly.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/CentralizationBureau.java` | `IndEvo_AdInfra`: flat supply/demand bonus to local industries duplicated in the system; nested `BureauFactor` (colony factor tooltip) |
| `indevo/industries/Supercomputer.java` | `IndEvo_SupCom`: consumes AI cores over time for a system wide income mult; nested `SuperComputerFactor` |
| `indevo/industries/RestorationDocks.java` | `IndEvo_dryDock`: monthly D-mod removal from ships in its storage; static `getBaseShipHullSpec`, `getListNonBuiltInDMods` used by other features |
| `indevo/industries/RequisitionCenter.java` | `IndEvo_ReqCenter`: adds the weapon import submarket; tooltip only, stock logic is in `RequisitionsCenterSubmarketPlugin` |
| `indevo/industries/TradeCenter.java` | Plugin of the vanilla `commerce` id: independent open market on player colonies, balance setting variant |
| `indevo/industries/InvisiblePlanetaryShield.java` | `IndEvo_InvisiblePlanetaryShield`: vanilla `PlanetaryShield` with selectable shield visuals; nested `AlternateTextureOptionProvider` |
| `indevo/industries/SharedSubmarketUser.java`, `SharedSubmarketUserAPI.java` | Base class and interface for industries that add and filter the `IndEvo_SharedStore` submarket |
| `indevo/industries/OrbitalStation.java` | Dead, see Known issues |
| `indevo/industries/compatibility/TechTree.java` | Dead, see Known issues |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_AdInfra` (`Ids.ADINFRA`) | `industries.csv:21`, upkeep 6 |
| Industry | `IndEvo_SupCom` (`Ids.SUPCOM`) | `industries.csv:25`, upkeep 4 |
| Industry | `IndEvo_dryDock` (`Ids.REPAIRDOCKS`) | `industries.csv:32`, upkeep 5 |
| Industry | `IndEvo_ReqCenter` (`Ids.REQCENTER`) | `industries.csv:38`, upkeep 6 |
| Industry | `IndEvo_InvisiblePlanetaryShield` (`Ids.PLANETARY_SHIELD_ALT`) | `industries.csv:92`, `do_not_show_in_build_dialog, hide_in_codex` |
| Industry | `commerce` (`Ids.COMMERCE`) | `industries.csv:141` |
| Submarkets | `IndEvo_RepairStorage` (`Ids.REPSTORAGE`), `IndEvo_ReqCenterMarket` (`Ids.REQMARKET`), `IndEvo_SharedStore` (`Ids.SHAREDSTORAGE`) | `data/campaign/submarkets.csv:2`, `:3`, `:8` |
| List | `IndEvo_buerauList` (sic) -> `data/config/indEvo/bureau_industry_whitelist.csv` | `data/config/settings.json:223`, `Ids.BUREAU_LIST` |
| Settings | `IndEvo_alpha_core_runtime` 558, `IndEvo_beta_core_runtime` 279, `IndEvo_gamma_core_runtime` 93 (days) | `LunaSettings.csv:152-154` |
| Settings | `IndEvo_restoFee_FRIGATE` 1000, `_DESTROYER` 3000, `_CRUISER` 7000, `_CAPITAL` 10000 | `LunaSettings.csv:157-160` |
| Setting | `IndEvo_CommerceBalanceChanges` (default true) | `LunaSettings.csv:9` |
| Memory key | `$IndEvo_maxDModMemory` (map `FleetMemberAPI -> Integer`) | sector memory, `RestorationDocks` |
| Market tags | `IndEvo_PS_FULL`, `IndEvo_PS_LOW`, `IndEvo_PS_NONE` | `InvisiblePlanetaryShield` |
| Sprite | `IndEvo` / `shield_texture` | `data/config/settings.json:360` |
| Strings | categories `IndEvo_AdInfra`, `IndEvo_dryDock`, `IndEvo_ReqCenter` | `data/strings/strings.json:126`, `:215`, `:256` |

## Registration and lifecycle

- All via CSV plugin column.
- `ModPlugin.setListenersIfNeeded()`: `InvisiblePlanetaryShield.AlternateTextureOptionProvider.register()` (`ModPlugin.java:501`). `BureauFactor` and `SuperComputerFactor` are registered by `SpecialItemEffectsRepo.initEffectListeners()` ([Installables](../items/INSTALLABLES.md)).
- `ModPlugin.onGameLoad` calls `overrideVanillaCommerce()` when `IndEvo_CommerceBalanceChanges` is on (`ModPlugin.java:171`).
- `NewGameIndustryPlacer` places Restoration Docks (`NewGameIndustryPlacer.java:69`) and the Requisitions Centre at `baetis` (`:133`) on new game; see [Utils](../core/UTILS.md).
- Supercomputer, Restoration Docks and Requisitions Centre add themselves as transient listeners in `apply()` and remove themselves in `unapply()`; `ListenerManager.addListener` dedupes the same instance (skill `obf/campaign/ListenerManager.java:29`).

## Flow

### Centralization Bureau (`IndEvo_AdInfra`)

- `getIndustryExistOnMultipleMap()` (`CentralizationBureau.java:75`): for every whitelisted industry on this market, count same faction markets in the system that have it. With a special item (Logistics Core `IndEvo_log_core`) all faction markets within 12 LY count too. Heavy industry and orbital works count for each other.
- `setCommoditySupply()` (player only): every supply and demand of each matched local industry gets `modifyFlatAlways(min(count, max))`. Max: none 0, gamma 1, beta 2, alpha 3, item `LOG_CORE_MAX_BONUS` 4.
- `isFunctional` needs an AI core or the item. Installing the item moves an installed AI core to storage (`:58`).
- One per system per faction (`isOnlyInstanceInSystemExcludeMarket`). AI markets get a gamma core once (`AImode`).
- Whitelist: vanilla `farming` to `highcommand`, Shadowyards `ms_*`, `loa_void_extraction`.

### Supercomputer (`IndEvo_SupCom`)

- Extends `SharedSubmarketUser`; only AI core commodities are legal in the shared storage (`Supercomputer.java:616`).
- `onNewDay` (`:325`): `getNextCore` moves the installed core into `lastApplied`; after `getCoreUseTimes(core)` days (runtime setting times `runtimeMult`; a modded core runs as long as a gamma core) it takes the next core from the shared storage (same type first, then gamma, beta, alpha). Warning 30 days before, message when empty. AI markets get a fresh gamma core.
- `getCurrentIncrease()`: very cold 0.20, cold 0.15, else 0.05; x1.2 with alpha; +0.1 (`SIMULATOR_BASE_INCREASE`) with the Simulation Engine.
- `modifyAllMarketIncome()` (`:172`), keyed by `getModId()`, which `unapply()` also clears on every market in the system: sums all supercomputers in the system plus the distance scaled bonus of the nearest Simulation Engine supercomputer in another system within 12 LY; one source gives `1 + sum`, several give `1 + sum * 0.88^n`; applied as `getIncomeMult().modifyMult` on every same faction market in the system.
- Last economy tick of the month: beta core gives random credits straight to the player; with `IndEvo_ResLab` on the market a cold (5%) or very cold (10%) planet may dump a random AI core into storage (x1.5 with beta).
- AI cores: gamma upkeep x0.9, beta upkeep x0.8 plus credits, alpha x1.2 income.
- Build: not on hot or very hot planets, stations, gas giants. Pather interest +6.
- `getDistanceIncomeMult` and `getNearestSupCom` are also read by `ResourceCondition` ([Economy](../core/ECONOMY.md)).

### Restoration Docks (`IndEvo_dryDock`)

- `advance` adds `IndEvo_RepairStorage`; demand `IndEvo_parts` `min(eligible ships, 4)`.
- `reportEconomyTick` on the last tick of the month (`RestorationDocks.java:105`, before month end because of the report) -> `removeDMods(aiMode)`.
- `removeDMods`: budget = market size (+3 alpha). Per step: pick a ship (beta: fewest D-mods; else random), remove one random non built in D-mod, add `getSingleDModRepairCost` to the monthly report node (`industries`, or `dryDock` for AI markets). Gamma: one D-mod per ship. A clean ship is restored to its base hull (`getBaseShipHullSpec`).
- Cost per D-mod = `(baseValue / 2) * 1.2^(maxD + 1) / maxD * aiCoreBonusMult` (`:313`), where `maxD` is the highest D-mod count seen for that member (`$IndEvo_maxDModMemory`). Beta mult 0.85, alpha upkeep x0.2.
- AI markets (not hostile, or pirates): the player can use the docks there; a hull size fee scaled by `1 - relation` (clamped to +-0.5) is added.
- Inferred: the player pays through the monthly report; credits are only checked, not subtracted, during the loop.

### Requisitions Centre (`IndEvo_ReqCenter`)

- `advance` adds `IndEvo_ReqCenterMarket`; `unapply` queues its removal. Stock, rep thresholds (0.35, 0.5, 0.7) and AI core effects live in `RequisitionsCenterSubmarketPlugin` ([Submarkets](../core/SUBMARKETS.md)), which calls `publicAddAfterDescriptionSection` for its tooltip.
- With an [Embassy](EMBASSY.md) ambassador the centre sources from the aligned faction only; gamma core ignores the ambassador.

### Commerce override (`commerce`)

- `apply()`: on functional player markets adds an `open_market` of faction `independent` (or re-adds the transient `saved` one), stability -3, income +25%. Improve +25% (+10% with the balance setting). Tri-Tachyon immigration +10.
- Alpha core: income +25%, or tariff x0.75 on player markets with the balance setting.
- Balance setting: one per system per faction; `overrideVanillaCommerce()` (`ModPlugin.java:534`) replaces the Dealmaker Holosuite effect in `ItemEffectsRepo` with +25% income and calls `replaceIndustries(TradeCenter.class, "commerce")`.

### Invisible planetary shield

- `CloudPainter` swaps the vanilla shield for this industry ([World Wonders](WORLD_WONDER.md)). `getId()` returns `planetaryshield`, so `Market.hasIndustry` and `getIndustry`, which match on `Industry.getId()` (skill `obf/campaign/econ/Market.java:200-218`), treat it as the vanilla shield.
- Option "Change Shield Visuals" cycles market tags FULL -> LOW -> NONE; `apply` uses vanilla texture, the IE translucent texture, or no shield visuals.

### SharedSubmarketUser

`addSharedSubmarket()` queues `SubMarketAddOrRemovePlugin` for `IndEvo_SharedStore` on functional player markets; subclasses call it from `advance`. `SharedSubmarketPlugin` asks every `SharedSubmarketUserAPI` industry on the market `isLegalOnSharedSubmarket` and `addTooltipLine`. Users: `Supercomputer`, `SalvageYards`, `EngineeringHub`, `BaseForgeTemplateUser`; `CommodityForge` implements the interface directly.

## Contracts

- `BaseIndustry.getModId()` is `"ind_" + id` (skill `api/impl/campaign/econ/impl/BaseIndustry.java:164`, `:266`). Code that unmodifies by the bare id misses the mod.
- `BaseIndustry.applyAICoreModifiers` calls only the branch for the installed core (skill `BaseIndustry.java:246`); fields set by one branch are not reset by another.
- Bureau mods use `modifyFlatAlways` on other industries' stats; `unapply` must run with the same map or the mod stays (inferred).

## Known issues and dormant code

| Item | State |
|---|---|
| `RestorationDocks.removeDMods` loop (`RestorationDocks.java:179`) | `removalBudget >= 0` repairs one D-mod more than the budget |
| `RestorationDocks.getBaseRepairPrice` (`:346`) | divides by 0.85 for beta, so the tooltip shows a higher price |
| `RestorationDocks.aiCoreBonusMult` | only reset by beta and none branches; alpha or gamma after beta keeps 0.85 |
| `RequisitionCenter` tooltip (`RequisitionCenter.java:82`) | ambassador text shows only with a gamma core, the core that ignores the ambassador; inner `repInt < 35` branch never runs |
| `RequisitionCenter.apply` | `addListener(this)` with no listener interface; no effect |
| `TradeCenter.applyNoAICoreModifiers` (`TradeCenter.java:179`) | unmodifies tariff under `getModId()`, set under `getModId(1)`; the alpha tariff mod stays after core removal |
| `TradeCenter` alpha tooltip | with balance setting the format args are misordered (tariff string in the demand slot) |
| `ModPlugin.replaceIndustries` (`ModPlugin.java:562`) | `ind.getClass().isInstance(targetClass)` is always false; every `commerce` is removed and re-added on every load with the setting on; `Changelog_todo:56` "Double check commerce building reset bug" |
| `CentralizationBureau` `ADMANUF` branch (`:98`) | dead: `IndEvo_AdManuf` is not whitelisted; would NPE on `getSpecialItem().getId()` without an item |
| Bureau | `Changelog_todo:61`: "Cent Bureau should really run off a condition" |
| `OrbitalStation` | `@Deprecated`, no CSV row or reference; skips stations tagged `NO_ORBITAL_STATION` |
| `TechTree` | no reference; Cryo_but_better compat; `researchSaved.get` unboxing NPEs for unknown ids |

## Maintenance

Update this doc and the index when an IE row is added to `industries.csv` or a class here changes. Submarket logic belongs in [Submarkets](../core/SUBMARKETS.md), item effects in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
