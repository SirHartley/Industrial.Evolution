# Installable items

IE special items that go into industries (VPCs, forge templates, ambassadors, blueprints, the four colony items), their item and installable plugins, the effects IE adds to the vanilla item effect repo, and the loot listeners that drop items and ship parts. Read it before adding an item, changing an item effect or touching drops. It does not cover consumable items (`IndEvo_consumable_*`); see [Consumables](CONSUMABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

There are two install paths. Items in vanilla style (Logistics Core, Neuroconditioning Compounds, Relay Hypertransmitter, Simulation Engine) use `GenericSpecialItemPlugin` with industry ids in `plugin params`, and their `InstallableItemEffect` lives in `SpecialItemEffectsRepo`, merged into vanilla `ItemEffectsRepo.ITEM_EFFECTS`. Items with their own install slot (VPCs, forge templates, ambassadors, blueprints) use an IE `BaseInstallableIndustryItemPlugin` subclass that the owning industry returns from `getInstallableItems()`; these keep their own effect maps. Loot listeners add IE items to salvage and add ship parts and relic components to fleets and wrecks.

## Files

| File | Owner / role |
|---|---|
| `indevo/items/installable/SpecialItemEffectsRepo.java` | `ITEM_EFFECTS` map merged into vanilla repo; `initEffectListeners()` registers colony factor listeners; shared constants |
| `indevo/items/installable/VPCInstallableItemPlugin.java` | VPC slot for `VariableAssembler` and `CommodityForge`; `IndEvo_VPC_EFFECTS` maps each VPC id to `setSupply` / `setDemand` on `VPCUserIndustryAPI` |
| `indevo/items/installable/BlueprintInstallableItemPlugin.java` | Blueprint slot for `EngineeringHub`: `IndEvo_relicSpecialItem`, `ship_bp`, `tiandong_retrofit_bp`, `roider_retrofit_bp` |
| `indevo/items/installable/ForgeTemplateInstallableItemPlugin.java` | Forge template slot for `BaseForgeTemplateUser`, `HullForge`, `HullDeconstructor`, `AncientLaboratory`; `FORGETEMPLATE_EFFECTS` calls `setForgeShip(hull of data)` |
| `indevo/items/installable/AmbassadorInstallableItemPlugin.java` | Ambassador slot for `Embassy`; stores via `Embassy.get/setAmbassadorItemData`; strings category `IndEvo_AmbassadorItem` |
| `indevo/items/VPCItemPlugin.java` | Cargo plugin for `IndEvo_vpc_*`: icon render, tooltip from `IndEvo_VPC_EFFECTS` |
| `indevo/items/ForgeTemplateItemPlugin.java` | Cargo plugin for `IndEvo_ForgeTemplate_1..4`: name, price, drop resolve (`pickShip`), charge and quality helpers, print defects |
| `indevo/items/EmptyForgeTemplateItemPlugin.java` | Cargo plugin for `IndEvo_ForgeTemplate_5`, `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate`; tooltip hint depends on sector memory `$IndEvo_ResLab`, `$IndEvo_HullDecon` |
| `indevo/items/AmbassadorItemPlugin.java` | Cargo plugin for `IndEvo_amb`: name and tooltip from the `PersonAPI` in `AmbassadorItemData` |
| `indevo/items/specialitemdata/AmbassadorItemData.java`, `ForgeTemplateData.java` | `SpecialItemData` subclasses carrying a `PersonAPI` or `ShipVariantAPI`; override `equals` / `hashCode` |
| `indevo/items/listeners/SpecialItemDropsListener.java` | `ShowLootListener`: adds IE drop groups to salvage loot |
| `indevo/items/listeners/ShipComponentLootManager.java` | `PartsLootAdder` (fleet spawn cargo) and `PartsCargoInterceptor` (`ShowLootListener`): ship parts and relic components |

## Data and ids

| Id | Plugin | Params / use | Line in `data/campaign/special_items.csv` |
|---|---|---|---|
| `IndEvo_vpc_supplies`, `_marines`, `_heavy_machinery`, `_domestic_goods`, `_drugs`, `_hand_weapons`, `_IndEvo_parts`, `_luxury_goods`, `_marines_hand_weapons`, `_supply_fuel` | `VPCItemPlugin` | tags `IndEvo_VPC_common` / `_uncommon` / `_rare`; [Assembler](../industries/ASSEMBLER.md) | 2-20 |
| `IndEvo_amb` | `AmbassadorItemPlugin` | `no_drop`; [Embassy](../industries/EMBASSY.md) | 23 |
| `IndEvo_ForgeTemplate_1..4` | `ForgeTemplateItemPlugin` | charges = last digit; [Derelicts](../industries/DERELICTS.md) | 25-28 |
| `IndEvo_ForgeTemplate_5` | `EmptyForgeTemplateItemPlugin` | see Known issues | 29 |
| `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate` | `EmptyForgeTemplateItemPlugin` | tag `IndEvo_FT` | 30, 31 |
| `IndEvo_PetBox` | `PetItemPlugin` | [Pets](../industries/PETS.md) | 34 |
| `IndEvo_log_core` | `GenericSpecialItemPlugin` | `IndEvo_AdInfra`; [Small industries](../industries/SMALL_INDUSTRIES.md) | 37 |
| `IndEvo_neurals` | `GenericSpecialItemPlugin` | `IndEvo_senate`; [Senate](../industries/SENATE.md) | 41 |
| `IndEvo_transmitter` | `GenericSpecialItemPlugin` | `IndEvo_IntArray, militarybase, highcommand`; [Relay](../industries/RELAY.md) | 42 |
| `IndEvo_simulator` | `GenericSpecialItemPlugin` | `IndEvo_SupCom`; [Small industries](../industries/SMALL_INDUSTRIES.md) | 43 |
| `IndEvo_alpha_core`, `IndEvo_beta_core`, `IndEvo_gamma_core` | `GenericSpecialItemPlugin` | `no_drop`, display only: `ItemIds.convertAICoreToSpecial`, shown by `Supercomputer.getVisibleInstalledItems` | 64-66 |
| `IndEvo_relicSpecialItem` | `GenericSpecialItemPlugin` | dummy for the Engineering Hub blueprint slot; params `IndEvo_Warehouse, IndEvo_Warphouse` | 67 |
| `IndEvo_debug` | `GenericSpecialItemPlugin` | Debug Fish, `no_drop` | 69 |
| `#wormhole_anchor` | `WormholeAnchorItemPlugin` | commented out; [Warehouses](../industries/WAREHOUSES.md) | 72 |

| Kind | Id / key | Where |
|---|---|---|
| Drop groups | `indEvo_tech_addition`, `indEvo_mining_addition` | `data/campaign/procgen/drop_groups.csv:2-19` |
| Memory key | `$IndEvo_FT_dropResolveMap` (`ForgeTemplateItemPlugin.DROP_MAP_KEY`) | sector memory, hull id -> drop weight divisor |
| Memory key | `$IndEvo_RelayUnlocked` (`TRANSMITTER_UNLOCK_KEY`) | sector memory |
| Memory key prefix | `$IndEvo_qualityLevel_` (`TEMPLATE_QUALITY_LEVEL`) | forge template quality, see [Derelicts](../industries/DERELICTS.md) |
| Settings | `IndEvo_PartsDropInCampaign`, `IndEvo_relicComponentHardbattleFPAdvantage`, `IndEvo_relicComponentFPDestroyedFract` | `data/config/LunaSettings.csv`, read in `ShipComponentLootManager` |

## Registration and lifecycle

- `ModPlugin.onApplicationLoad`: `SpecialItemEffectsRepo.addItemEffectsToVanillaRepo()` (`ModPlugin.java:127`), `ItemEffectsRepo.ITEM_EFFECTS.putAll(ITEM_EFFECTS)`. `BaseIndustry` looks up the effect of an installed special item by id in that map (skill `api/impl/campaign/econ/impl/BaseIndustry.java:214`).
- `ModPlugin.setListenersIfNeeded()`: `SpecialItemEffectsRepo.initEffectListeners()` (`:453`) adds `SuperComputerFactor`, `Senate.SenateFactor`, `MilitaryRelay.RelayFactor`, `CentralizationBureau.BureauFactor` (deduped by class); `PartsCargoInterceptor.register()` (`:458`), `PartsLootAdder.register()` (`:460`, `addTransientListener`), `SpecialItemDropsListener.register()` (`:469`, deduped by class). All transient.
- Installable plugins are created on demand by the owning industry's `getInstallableItems()` (skill `api/campaign/econ/Industry.java:149`), for example `VariableAssembler.java:425`, `EngineeringHub.java:540`, `Embassy.java:282`, `HullForge.java:486`.
- Item cargo plugins come from the `plugin` column.

## Flow

### Vanilla repo effects (`SpecialItemEffectsRepo`)

| Item | `apply` | Requirement / text |
|---|---|---|
| `IndEvo_log_core` | none; `CentralizationBureau` reads `getSpecialItem()` | range 12 LY, max bonus 4 |
| `IndEvo_simulator` | none; `Supercomputer` reads it | cold or extreme cold; +0.1 income, range 12 LY |
| `IndEvo_transmitter` | outside a `MilitaryRelay`: set `$IndEvo_RelayUnlocked`; `unapply` unsets it unless another functional non relay industry holds a transmitter (`SpecialItemEffectsRepo.java:165`) | in a relay: pristine relay entity |
| `IndEvo_neurals` | none; Senate edicts read `NEURAL_COMPOUNDS_UNREST_RED` 0.5 | see [Senate](../industries/SENATE.md) |
| `wormhole_anchor` | none | "linked storages" text; vanilla has no effect for this id, IE adds one |

### Own install slots

- `isInstallableItem` checks the stack id against the plugin's effect map. `get/setCurrentlyInstalledItemData` read and write the industry (`getCurrentVPC`, `getSpecialItem`, `getAmbassadorItemData`).
- Forge templates: `apply` calls `BaseForgeTemplateUser.setForgeShip(Global.getSettings().getHullSpec(data))`; empty and degraded templates set nothing.
- Blueprints: `getMenuItemTitle` adds a dummy `IndEvo_relicSpecialItem` to cargo so it shows in the pick list; the hub's `apply` removes it again (code comment "dirtiest hack ever").

### Forge template drops

`ForgeTemplateItemPlugin.resolveDropParamsToSpecificItemData` -> `pickShip(tags, random)` (`:177`): hull base value above 200000, in the print whitelist (`Ids.PRINT_LIST`) minus `ziggurat`, not a Vayra or Prism boss, not a D-hull. Weight `1 / dropMod`; each pick adds 4 to that hull's entry in `$IndEvo_FT_dropResolveMap`. Price = base + `hull value * 0.1 * mult * (1 + 0.2 * charges)`.

### Salvage drops (`SpecialItemDropsListener`)

On loot: collects the target's drop data (entity, then salvage spec). For each `rare_tech` / `rare_tech_low` value group adds an `indEvo_tech_addition` group (`rare_tech_low` value x0.5); for random groups chances x4 for `rare_tech`. `techmining_first_find` adds `indEvo_mining_addition`. Rolls with `SalvageEntity.generateSalvage` seeded from `MemFlags.SALVAGE_SEED`. Logic derived from Extra Systems Reloaded (code comment).

### Ship parts and relic components (`ShipComponentLootManager`)

| Source | Adds | Rule |
|---|---|---|
| `PartsLootAdder.reportFleetSpawned` | `IndEvo_rare_parts` to fleet cargo | factions `omega` 0.5, `remnant` 0.25, `derelict` 0.1, IE derelict faction 0.2, `rat_abyssals`, `zea_*` 0.3, `threat` 0.05, times FP plus random; skips detachments |
| same | `IndEvo_parts` | `IndEvo_PartsDropInCampaign` and `IndEvo_ScrapYard` on: `0.5 * FP` plus random |
| `PartsCargoInterceptor`, wreck | `IndEvo_parts` | same settings; `0.3 * FP` plus up to `0.2 * FP`, seeded by the wreck id |
| `PartsCargoInterceptor`, fleet (not pirates, not Luddic Path) | `IndEvo_rare_parts` | hard battle: `0.3 * FP destroyed`; automated ships of unlisted factions: `0.2 * destroyed FP` plus random |
| `PartsCargoInterceptor`, derelict probe / survey ship / mothership / cryosleeper | `IndEvo_rare_parts` | 1-10, 20-200, 150-400, 250-500, salvage seeded |

## Contracts

- `ITEM_EFFECTS` is merged at application load; it is static and survives save loads.
- `ForgeTemplateData` and `AmbassadorItemData` must keep `equals` / `hashCode` so cargo stacks and installs match on id, data and payload. Users: [Embassy](../industries/EMBASSY.md) (`AmbassadorItemHelper`, `AmbassadorPersonManager` and others), [Derelicts](../industries/DERELICTS.md) (`HullForge`, `HullDeconstructor`), [Salvage](../exploration/SALVAGE.md) (`PrintShipSpecial`).
- `ForgeTemplateItemPlugin.getValidVariantIdForHullId` is also used by the [Salvage Yards](../industries/SALVAGE_YARDS.md) production intel.
- Forge template charges are the last char of the item id; `getIncrementedFTId` clamps to 5 and falls back to `IndEvo_DegForgeTemplate`.

## Known issues and dormant code

| Item | State |
|---|---|
| `IndEvo_ForgeTemplate_5` row | uses `EmptyForgeTemplateItemPlugin`, so a 5 charge template shows no hull name or price (suspected CSV error) |
| `incrementForgeTemplateData` (`ForgeTemplateItemPlugin.java:383`) | non `ForgeTemplateData` branch passes `-1` instead of `num` |
| `ForgeTemplateItemPlugin.applyPrintDefects` | `@Deprecated` |
| Commented effects | `INTERFACES`, `NANITES`, `SALVAGE_DRONES`, `ANALYSER`, experimental log core (`SpecialItemEffectsRepo.java:98-139`, `:230-304`) |
| `wormhole_anchor` | effect active, item row commented; see [Warehouses](../industries/WAREHOUSES.md) |

## Maintenance

Update this doc when a non consumable row in `special_items.csv`, an effect map, a drop group or a loot rule changes. Industry behaviour behind an item belongs in that industry's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
