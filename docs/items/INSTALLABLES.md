# Installable items

IE special items that go into industries (VPCs, forge templates, ambassadors, blueprints, the four colony items), their item and installable plugins, the effects IE adds to the vanilla item effect repo, and the loot listeners that drop items and ship parts. Read it before adding an item, changing an item effect or touching drops. It does not cover consumable items (`IndEvo_consumable_*`); see [Consumables](CONSUMABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

There are two install paths. Items in vanilla style (Logistics Core, Neuroconditioning Compounds, Relay Hypertransmitter, Simulation Engine, forge templates) use `GenericSpecialItemPlugin` or a subclass with industry ids in `plugin params`, and their `InstallableItemEffect` lives in `SpecialItemEffectsRepo`, merged into vanilla `ItemEffectsRepo.ITEM_EFFECTS`. `BaseIndustry` then owns the slot, the effect calls, the tooltip and the refund. Items with their own install slot (VPCs, ambassadors, blueprints) use an IE `BaseInstallableIndustryItemPlugin` subclass that the owning industry returns from `getInstallableItems()`; these keep their own effect maps. Item payloads that do not fit a data string (a deconstructed variant) live in `ItemPayloadMemory`, keyed from the item data. Loot listeners add IE items to salvage and add ship parts and relic components to fleets and wrecks.

## Files

| File | Owner / role |
|---|---|
| `indevo/items/installable/SpecialItemEffectsRepo.java` | `ITEM_EFFECTS` map merged into vanilla repo; `initEffectListeners()` registers colony factor listeners; shared constants |
| `indevo/items/installable/VPCInstallableItemPlugin.java` | VPC slot for `VariableAssembler` and `CommodityForge`; `IndEvo_VPC_EFFECTS` maps each VPC id to `setSupply` / `setDemand` on `VPCUserIndustryAPI` |
| `indevo/items/installable/BlueprintInstallableItemPlugin.java` | Blueprint slot for `EngineeringHub`: `IndEvo_relicSpecialItem`, `ship_bp`, `tiandong_retrofit_bp`, `roider_retrofit_bp`; `getIndustry()` for the relic dialog patch |
| `indevo/items/installable/NamedItemSlotPlugin.java` | `GenericInstallableItemPlugin` with the item name in the menu texts and an own menu tooltip line; used by forge template users |
| `indevo/items/installable/ForgeTemplateEffect.java` | `BaseInstallableItemEffect` for the three forge template ids; description only, no apply |
| `indevo/items/installable/AmbassadorInstallableItemPlugin.java` | Ambassador slot for `Embassy`; stores via `Embassy.get/setAmbassadorItemData`; strings category `IndEvo_AmbassadorItem` |
| `indevo/items/VPCItemPlugin.java` | Cargo plugin for `IndEvo_vpc_*`: icon render, tooltip from `IndEvo_VPC_EFFECTS` |
| `indevo/items/ForgeTemplateItemPlugin.java` | `GenericSpecialItemPlugin` for `IndEvo_ForgeTemplate`: name, price, drop resolve (`pickShip`), the item data format and its helpers (`createForgeTemplate`, `getHullId`, `getCharges`, `getVariant`, `useCharge`), quality, print defects |
| `indevo/items/EmptyForgeTemplateItemPlugin.java` | `GenericSpecialItemPlugin` for `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate`; tooltip hint depends on sector memory `$IndEvo_ResLab`, `$IndEvo_HullDecon` |
| `indevo/items/AmbassadorItemPlugin.java` | Cargo plugin for `IndEvo_amb`: name and tooltip from the `PersonAPI` in `AmbassadorItemData` |
| `indevo/items/specialitemdata/AmbassadorItemData.java` | `SpecialItemData` subclass carrying a `PersonAPI`; overrides `equals` / `hashCode` |
| `indevo/items/memory/ItemPayloadMemory.java` | Item payloads in sector memory `$IndEvo_itemPayloads`: `add`, `put`, `get`, `contains`, `remove` by key |
| `indevo/items/listeners/SpecialItemDropsListener.java` | `ShowLootListener`: adds IE drop groups to salvage loot |
| `indevo/items/listeners/ShipComponentLootManager.java` | `PartsLootAdder` (fleet spawn cargo) and `PartsCargoInterceptor` (`ShowLootListener`): ship parts and relic components |

## Data and ids

| Id | Plugin | Params / use | Line in `data/campaign/special_items.csv` |
|---|---|---|---|
| `IndEvo_vpc_supplies`, `_marines`, `_heavy_machinery`, `_domestic_goods`, `_drugs`, `_hand_weapons`, `_IndEvo_parts`, `_luxury_goods`, `_marines_hand_weapons`, `_supply_fuel` | `VPCItemPlugin` | tags `IndEvo_VPC_common` / `_uncommon` / `_rare`; [Assembler](../industries/ASSEMBLER.md) | 2-20 |
| `IndEvo_amb` | `AmbassadorItemPlugin` | `no_drop`; [Embassy](../industries/EMBASSY.md) | 23 |
| `IndEvo_ForgeTemplate` | `ForgeTemplateItemPlugin` | params `IndEvo_HullForge`; data `hullId|charges` or `hullId|charges|payloadKey`; [Derelicts](../industries/DERELICTS.md) | 25 |
| `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate` | `EmptyForgeTemplateItemPlugin` | params `IndEvo_ResLab`, `IndEvo_HullDecon`; tag `IndEvo_FT` | 26, 27 |
| `IndEvo_PetBox` | `PetItemPlugin` | [Pets](../industries/PETS.md) | 30 |
| `IndEvo_log_core` | `GenericSpecialItemPlugin` | `IndEvo_AdInfra`; [Small industries](../industries/SMALL_INDUSTRIES.md) | 33 |
| `IndEvo_neurals` | `GenericSpecialItemPlugin` | `IndEvo_senate`; [Senate](../industries/SENATE.md) | 37 |
| `IndEvo_transmitter` | `GenericSpecialItemPlugin` | `IndEvo_IntArray, militarybase, highcommand`; [Relay](../industries/RELAY.md) | 38 |
| `IndEvo_simulator` | `GenericSpecialItemPlugin` | `IndEvo_SupCom`; [Small industries](../industries/SMALL_INDUSTRIES.md) | 39 |
| `IndEvo_alpha_core`, `IndEvo_beta_core`, `IndEvo_gamma_core` | `GenericSpecialItemPlugin` | `no_drop`, display only: `ItemIds.convertAICoreToSpecial`, shown by `Supercomputer.getVisibleInstalledItems` | 60-62 |
| `IndEvo_relicSpecialItem` | `GenericSpecialItemPlugin` | relic mode for the Engineering Hub blueprint slot, never in cargo, listed by `RelicInstallDialogPatch` ([Engineering Hub](../industries/ENGINEERING_HUB.md#relic-components-in-the-install-dialog)); params `IndEvo_Warehouse, IndEvo_Warphouse` | 63 |
| `IndEvo_debug` | `GenericSpecialItemPlugin` | Debug Fish, `no_drop` | 65 |
| `#wormhole_anchor` | `WormholeAnchorItemPlugin` | commented out; [Warehouses](../industries/WAREHOUSES.md) | 68 |

| Kind | Id / key | Where |
|---|---|---|
| Drop groups | `indEvo_tech_addition`, `indEvo_mining_addition` | `data/campaign/procgen/drop_groups.csv:2-19` |
| Memory key | `$IndEvo_FT_dropResolveMap` (`ForgeTemplateItemPlugin.DROP_MAP_KEY`) | sector memory, hull id -> drop weight divisor |
| Memory key | `$IndEvo_itemPayloads` (`ItemPayloadMemory.PAYLOAD_MAP_KEY`) | sector memory, payload key -> payload (forge template `ShipVariantAPI`) |
| Memory key | `$IndEvo_RelayUnlocked` (`TRANSMITTER_UNLOCK_KEY`) | sector memory |
| Memory key prefix | `$IndEvo_qualityLevel_` (`TEMPLATE_QUALITY_LEVEL`) | forge template quality, see [Derelicts](../industries/DERELICTS.md) |
| Settings | `IndEvo_PartsDropInCampaign`, `IndEvo_relicComponentHardbattleFPAdvantage`, `IndEvo_relicComponentFPDestroyedFract` | `data/config/LunaSettings.csv`, read in `ShipComponentLootManager` |

## Registration and lifecycle

- `ModPlugin.onApplicationLoad`: `SpecialItemEffectsRepo.addItemEffectsToVanillaRepo()` (`ModPlugin.java:129`), `ItemEffectsRepo.ITEM_EFFECTS.putAll(ITEM_EFFECTS)`. `BaseIndustry` looks up the effect of an installed special item by id in that map (skill `api/impl/campaign/econ/impl/BaseIndustry.java:214`).
- `ModPlugin.setListenersIfNeeded()`: `SpecialItemEffectsRepo.initEffectListeners()` (`:426`) adds `SuperComputerFactor`, `Senate.SenateFactor`, `MilitaryRelay.RelayFactor`, `CentralizationBureau.BureauFactor` (deduped by class); `PartsCargoInterceptor.register()` (`:431`), `PartsLootAdder.register()` (`:433`, `addTransientListener`), `SpecialItemDropsListener.register()` (`:443`, deduped by class). All transient.
- Installable plugins are created on demand by the owning industry's `getInstallableItems()` (skill `api/campaign/econ/Industry.java:149`), for example `VariableAssembler.java:425`, `EngineeringHub.java:532`, `Embassy.java:279`, `BaseForgeTemplateUser.java:51`.
- Item cargo plugins come from the `plugin` column.

## Flow

### Vanilla repo effects (`SpecialItemEffectsRepo`)

| Item | `apply` | Requirement / text |
|---|---|---|
| `IndEvo_log_core` | none; `CentralizationBureau` reads `getSpecialItem()` | range 12 LY, max bonus 4 |
| `IndEvo_simulator` | none; `Supercomputer` reads it | cold or extreme cold; +0.1 income, range 12 LY |
| `IndEvo_transmitter` | outside a `MilitaryRelay`: set `$IndEvo_RelayUnlocked`; `unapply` unsets it unless another functional non relay industry holds a transmitter (`SpecialItemEffectsRepo.java:165`) | in a relay: pristine relay entity |
| `IndEvo_neurals` | none; Senate edicts read `NEURAL_COMPOUNDS_UNREST_RED` 0.5 | see [Senate](../industries/SENATE.md) |
| `IndEvo_ForgeTemplate`, `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate` | none (`ForgeTemplateEffect`); the industries read `getSpecialItem()` | hull, charges, material cost |
| `wormhole_anchor` | none | "linked storages" text; vanilla has no effect for this id, IE adds one |

### Own install slots

- `isInstallableItem` checks the stack id against the plugin's effect map. `get/setCurrentlyInstalledItemData` read and write the industry (`getCurrentVPC`, `getSpecialItem`, `getAmbassadorItemData`).
- Blueprints: `IndEvo_relicSpecialItem` is added to the pick list by a PatchLib patch on the vanilla dialog, not through cargo; see [Engineering Hub](../industries/ENGINEERING_HUB.md#relic-components-in-the-install-dialog).

### Forge templates

One id, `IndEvo_ForgeTemplate`, with data `hullId|charges` (drops, salvage) or `hullId|charges|payloadKey` (Hull Deconstructor). Charges are clamped to 1 to `MAX_CHARGES` 5. The payload key points to the deconstructed `ShipVariantAPI` in `ItemPayloadMemory`; the variant keeps the quality tag `$IndEvo_qualityLevel_N` and is cloned for each print. `useCharge` returns the template with one charge less, or `IndEvo_DegForgeTemplate` with the payload removed when the last charge is used. A payload key without a payload is an illegal state: the tooltip names it and `createNakedFleetMemberFromForgeTemplate` throws.

The item params send each template to the structure that uses it: charged to the Hull Forge, degraded to the Ancient Laboratory, empty to the Hull Deconstructor. `BaseForgeTemplateUser.getInstallableItems` swaps the vanilla slot for a `NamedItemSlotPlugin` with forge template texts; the Hull Forge and the Deconstructor hide it while working.

### Forge template drops

`ForgeTemplateItemPlugin.resolveDropParamsToSpecificItemData` -> `pickShip(tags, random)`; the drop row's `charges` param sets the charges (default 1): hull base value above 200000, in the print whitelist (`Ids.PRINT_LIST`) minus `ziggurat`, not a Vayra or Prism boss, not a D-hull. Weight `1 / dropMod`; each pick adds 4 to that hull's entry in `$IndEvo_FT_dropResolveMap`. Price = base + `hull value * 0.1 * mult * (1 + 0.2 * charges)`.

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
- `AmbassadorItemData` must keep `equals` / `hashCode` so cargo stacks and installs match on id, data and payload. Users: [Embassy](../industries/EMBASSY.md) (`AmbassadorItemHelper`, `AmbassadorPersonManager` and others).
- Forge templates are plain `SpecialItemData`; everything is in the data string, so a copy as `new SpecialItemData(id, data)` keeps the template. Read it only through the `ForgeTemplateItemPlugin` helpers. Users: [Derelicts](../industries/DERELICTS.md) (`HullForge`, `HullDeconstructor`, `AncientLaboratory`), [Salvage](../exploration/SALVAGE.md) (`PrintShipSpecial`, `ItemChoiceSpecial`).
- `ForgeTemplateItemPlugin.getValidVariantIdForHullId` is also used by the [Salvage Yards](../industries/SALVAGE_YARDS.md) production intel.

## Known issues and dormant code

| Item | State |
|---|---|
| Commented effects | `INTERFACES`, `NANITES`, `SALVAGE_DRONES`, `ANALYSER`, experimental log core (`SpecialItemEffectsRepo.java:98-139`, `:230-304`) |
| `wormhole_anchor` | effect active, item row commented; see [Warehouses](../industries/WAREHOUSES.md) |

## Maintenance

Update this doc when a non consumable row in `special_items.csv`, an effect map, a drop group or a loot rule changes. Industry behaviour behind an item belongs in that industry's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
