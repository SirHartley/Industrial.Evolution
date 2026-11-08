# Installable items

IE special items that go into industries (VPCs, forge templates, ambassadors, blueprints, the four colony items), their item and installable plugins, the effects IE adds to the vanilla item effect repo, and the loot listeners that drop items and ship parts. Read it before adding an item, changing an item effect or touching drops. It does not cover consumable items (`IndEvo_consumable_*`); see [Consumables](CONSUMABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

There are two install paths. Items in vanilla style (VPCs, Logistics Core, Neuroconditioning Compounds, Relay Hypertransmitter, Simulation Engine, forge templates, ambassadors) use `GenericSpecialItemPlugin` or a subclass with industry ids in `plugin params`, and their `InstallableItemEffect` lives in `SpecialItemEffectsRepo`, merged into vanilla `ItemEffectsRepo.ITEM_EFFECTS`. `BaseIndustry` then owns the slot, the effect calls, the tooltip and the refund; an industry can swap the vanilla slot for a `NamedItemSlotPlugin` to name the item in the menu. Blueprints keep their own install slot, an IE `BaseInstallableIndustryItemPlugin` subclass that the Engineering Hub returns from `getInstallableItems()`, and only have a description. VPCs are one item whose tier and commodities are rolled into the item data. Item payloads that do not fit a data string (a deconstructed variant) live in `ItemPayloadMemory`, keyed from the item data. Loot listeners add IE items to salvage and add ship parts and relic components to fleets and wrecks.

## Files

| File | Owner / role |
|---|---|
| `indevo/items/installable/SpecialItemEffectsRepo.java` | `ITEM_EFFECTS` map of the effect classes below, merged into vanilla repo; `initEffectListeners()` registers colony factor listeners; shared constants |
| `indevo/items/installable/LogCoreEffect.java`, `SimulatorEffect.java`, `TransmitterEffect.java`, `NeuralCompoundsEffect.java`, `WormholeAnchorEffect.java` | `BaseInstallableItemEffect` per vanilla style item |
| `indevo/items/installable/BlueprintInstallableItemPlugin.java` | Blueprint slot for `EngineeringHub`: `BLUEPRINT_IDS` (`IndEvo_relicSpecialItem`, `ship_bp`, `tiandong_retrofit_bp`, `roider_retrofit_bp`), `isBlueprint`, `addBlueprintDescription`; `getIndustry()` for the relic dialog patch |
| `indevo/items/installable/NamedItemSlotPlugin.java` | `GenericInstallableItemPlugin` with the item name in the menu texts and an own menu tooltip line; used by forge template users, the Embassy and the variable industries |
| `indevo/items/installable/ForgeTemplateEffect.java` | `BaseInstallableItemEffect` for the three forge template ids; description only, no apply |
| `indevo/items/installable/AmbassadorEffect.java` | `BaseInstallableItemEffect` for `IndEvo_amb`; description only, no apply |
| `indevo/items/installable/VPCEffect.java` | `BaseInstallableItemEffect` for `IndEvo_vpc`: no apply (the industries read `special`), `unapply` calls `VPCUserIndustryAPI.vpcUnapply`, description from the item data |
| `indevo/items/VPCItemPlugin.java` | `GenericSpecialItemPlugin` for `IndEvo_vpc`: `Tier`, the item data format and its helpers (`createItem`, `roll`, `getOutputs`, `getSupply`, `getDemand`, `getDepositMult`), name, price, procedural icon, drop resolve |
| `indevo/items/memory/VPCCommodityRepo.java` | VPC commodity and package tables from `vpc_commodities.csv` and `vpc_packages.csv`, session cached |
| `indevo/items/ForgeTemplateItemPlugin.java` | `GenericSpecialItemPlugin` for `IndEvo_ForgeTemplate`: name, price, drop resolve (`pickShip`), the item data format and its helpers (`createForgeTemplate`, `getHullId`, `getCharges`, `getVariant`, `useCharge`), quality, print defects |
| `indevo/items/EmptyForgeTemplateItemPlugin.java` | `GenericSpecialItemPlugin` for `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate`; tooltip hint depends on sector memory `$IndEvo_ResLab`, `$IndEvo_HullDecon` |
| `indevo/items/AmbassadorItemPlugin.java` | Cargo plugin for `IndEvo_amb`: name and tooltip from the person the item data names (`AmbassadorItemHelper`) |
| `indevo/items/memory/ItemPayloadMemory.java` | Item payloads in sector memory `$IndEvo_itemPayloads`: `add`, `put`, `get`, `contains`, `remove` by key |
| `indevo/items/listeners/SpecialItemDropsListener.java` | `ShowLootListener`: adds IE drop groups to salvage loot |
| `indevo/items/listeners/ShipComponentLootManager.java` | `PartsLootAdder` (fleet spawn cargo) and `PartsCargoInterceptor` (`ShowLootListener`): ship parts and relic components |

## Data and ids

| Id | Plugin | Params / use | Line in `data/campaign/special_items.csv` |
|---|---|---|---|
| `IndEvo_vpc` | `VPCItemPlugin` | `colony_item`; `IndEvo_AdAssem, IndEvo_AdManuf, IndEvo_ComForge`; data `tier\|commodityId,commodityId`; [VPCs](#vpcs), [Assembler](../industries/ASSEMBLER.md) | 2 |
| `IndEvo_amb` | `AmbassadorItemPlugin` | params `IndEvo_embassy`; data is the person id; `no_drop`; [Embassy](../industries/EMBASSY.md) | 4 |
| `IndEvo_ForgeTemplate` | `ForgeTemplateItemPlugin` | params `IndEvo_HullForge`; data `hullId|charges` or `hullId|charges|payloadKey`; [Derelicts](../industries/DERELICTS.md) | 6 |
| `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate` | `EmptyForgeTemplateItemPlugin` | params `IndEvo_ResLab`, `IndEvo_HullDecon`; tag `IndEvo_FT` | 7, 8 |
| `IndEvo_PetBox` | `PetItemPlugin` | [Pets](../industries/PETS.md) | 11 |
| `IndEvo_log_core` | `GenericSpecialItemPlugin` | `colony_item`; `IndEvo_AdInfra`; [Small industries](../industries/SMALL_INDUSTRIES.md) | 14 |
| `IndEvo_neurals` | `GenericSpecialItemPlugin` | `colony_item`; `IndEvo_senate`; [Senate](../industries/SENATE.md) | 18 |
| `IndEvo_transmitter` | `GenericSpecialItemPlugin` | `colony_item`; `IndEvo_IntArray, militarybase, highcommand`; [Relay](../industries/RELAY.md) | 19 |
| `IndEvo_simulator` | `GenericSpecialItemPlugin` | `colony_item`, `planet_search`; `IndEvo_SupCom`; [Small industries](../industries/SMALL_INDUSTRIES.md) | 20 |
| `IndEvo_alpha_core`, `IndEvo_beta_core`, `IndEvo_gamma_core` | `GenericSpecialItemPlugin` | `no_drop`, display only: `ItemIds.convertAICoreToSpecial`, shown by `Supercomputer.getVisibleInstalledItems` | 41-43 |
| `IndEvo_relicSpecialItem` | `GenericSpecialItemPlugin` | relic mode for the Engineering Hub blueprint slot, never in cargo, listed by `RelicInstallDialogPatch` ([Engineering Hub](../industries/ENGINEERING_HUB.md#relic-components-in-the-install-dialog)); params `IndEvo_Warehouse, IndEvo_Warphouse` | 44 |
| `IndEvo_debug` | `GenericSpecialItemPlugin` | Debug Fish, `no_drop` | 46 |
| `#wormhole_anchor` | `WormholeAnchorItemPlugin` | commented out; [Warehouses](../industries/WAREHOUSES.md) | 49 |

| Kind | Id / key | Where |
|---|---|---|
| Drop groups | `indEvo_tech_addition`, `indEvo_mining_addition` | `data/campaign/procgen/drop_groups.csv:2-23` |
| Config | VPC commodity and package tables | `data/config/indEvo/vpc_commodities.csv`, `vpc_packages.csv` |
| Session memory | `$IndEvo_vpcCommodities`, `$IndEvo_vpcPackages` (`VPCCommodityRepo`) | `SessionTransientMemory` |
| Sprite | category `IndEvo`: `VPCRareChip` (rare and ultra rare chip frame) | `data/config/settings.json:136` |
| Memory key | `$IndEvo_FT_dropResolveMap` (`ForgeTemplateItemPlugin.DROP_MAP_KEY`) | sector memory, hull id -> drop weight divisor |
| Memory key | `$IndEvo_itemPayloads` (`ItemPayloadMemory.PAYLOAD_MAP_KEY`) | sector memory, payload key -> payload (forge template `ShipVariantAPI`) |
| Memory key | `$IndEvo_RelayUnlocked` (`TRANSMITTER_UNLOCK_KEY`) | sector memory |
| Memory key prefix | `$IndEvo_qualityLevel_` (`TEMPLATE_QUALITY_LEVEL`) | forge template quality, see [Derelicts](../industries/DERELICTS.md) |
| Settings | `IndEvo_PartsDropInCampaign`, `IndEvo_relicComponentHardbattleFPAdvantage`, `IndEvo_relicComponentFPDestroyedFract` | `data/config/LunaSettings.csv`, read in `ShipComponentLootManager` |

## Registration and lifecycle

- `ModPlugin.onApplicationLoad`: `SpecialItemEffectsRepo.addItemEffectsToVanillaRepo()` (`ModPlugin.java:129`), `ItemEffectsRepo.ITEM_EFFECTS.putAll(ITEM_EFFECTS)`. `BaseIndustry` looks up the effect of an installed special item by id in that map (skill `api/impl/campaign/econ/impl/BaseIndustry.java:214`).
- `ModPlugin.setListenersIfNeeded()`: `SpecialItemEffectsRepo.initEffectListeners()` (`:426`) adds `SuperComputerFactor`, `Senate.SenateFactor`, `MilitaryRelay.RelayFactor`, `CentralizationBureau.BureauFactor` (deduped by class); `PartsCargoInterceptor.register()` (`:431`), `PartsLootAdder.register()` (`:433`, `addTransientListener`), `SpecialItemDropsListener.register()` (`:443`, deduped by class). All transient.
- `ModPlugin.loadTransientMemory()` warms the VPC tables and loads their icons (`ModPlugin.java:412`).
- Installable plugins are created on demand by the owning industry's `getInstallableItems()` (skill `api/campaign/econ/Industry.java:149`), for example `VariableAssembler.java:327`, `EngineeringHub.java:487`, `Embassy.java:237`, `BaseForgeTemplateUser.java:51`.
- Item cargo plugins come from the `plugin` column.

## Flow

### Vanilla repo effects (`SpecialItemEffectsRepo`)

| Item | `apply` | Requirement / text |
|---|---|---|
| `IndEvo_log_core` | none; `CentralizationBureau` reads `getSpecialItem()` | range 12 LY, max bonus 4 |
| `IndEvo_simulator` | none; `Supercomputer` reads it | cold or extreme cold; +0.1 income, range 12 LY |
| `IndEvo_transmitter` | outside a `MilitaryRelay`: set `$IndEvo_RelayUnlocked`; `unapply` unsets it unless another functional non relay industry holds a transmitter (`TransmitterEffect.java:35`) | in a relay: pristine relay entity |
| `IndEvo_neurals` | none; Senate edicts read `NEURAL_COMPOUNDS_UNREST_RED` 0.5 | see [Senate](../industries/SENATE.md) |
| `IndEvo_ForgeTemplate`, `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate` | none (`ForgeTemplateEffect`); the industries read `getSpecialItem()` | hull, charges, material cost |
| `IndEvo_vpc` | none (`VPCEffect`); the variable industries read `special` in their own `apply`, before `BaseIndustry` runs the effect | tier and commodities; see [VPCs](#vpcs) |
| `IndEvo_amb` | none (`AmbassadorEffect`); `Embassy` reads `getSpecialItem()` | ambassador name and faction; see [Embassy](../industries/EMBASSY.md) |
| `wormhole_anchor` | none | "linked storages" text; vanilla has no effect for this id, IE adds one |

### Blueprint install slot

- `isInstallableItem` checks the stack id against `BLUEPRINT_IDS`. `get/setCurrentlyInstalledItemData` read and write the industry's `getSpecialItem` / `setSpecialItem`.
-  `IndEvo_relicSpecialItem` is added to the pick list by a PatchLib patch on the vanilla dialog, not through cargo; see [Engineering Hub](../industries/ENGINEERING_HUB.md#relic-components-in-the-install-dialog).

### VPCs

One item id, `IndEvo_vpc`, with data `tier|commodityId,commodityId` (`rare|supplies,fuel`). Create one only through `VPCItemPlugin.createItem` or `roll` (`VPCItemPlugin.java:166-180`).

| Tier | Outputs | Commodity pool | Price | NPC weight | Pather interest |
|---|---|---|---|---|---|
| `COMMON` | 1 | common | 30000 | 70 | 1 |
| `UNCOMMON` | 1 | uncommon | 50000 | 25 | 2 |
| `RARE` | 2 | all | 100000 | 5 | 4 |
| `ULTRA` (ultra rare) | 3 | all | 200000 | 1 | 6 |

- Commodity table `vpc_commodities.csv`: `id`, `pool` (`common`, `uncommon`, `rare`), `supply` modifier, `demand` as `commodity:modifier` list, one deposit column per industry id, optional `name` (`Exo-Armor Suits`), `icon` (whole chip sprite) and `desc` (flavor). Rows merge by `id` across mods; a row with an unknown commodity or bad values is skipped with a warning, a demand entry on an unknown commodity is dropped with a warning (`VPCCommodityRepo.java:59-93`, `parseDemand`). Rare pool commodities (fuel) only roll on rare and ultra rare chips (`Tier.canRoll`, `VPCItemPlugin.java:53-59`); a roll never repeats a commodity.
- Package table `vpc_packages.csv`: a commodity set with its own name, chip icon and flavor (`conflict`: marines and hand weapons, `exploration`: fuel and supplies), matched by set equality (`VPCCommodityRepo.getPackage`).
- Supply: one modifier per output; with 2 or 3 outputs each is at most `-outputs` (`getSupply`, `:236-244`). Demand: the highest modifier per input commodity over all outputs (`getDemand`, `:246-253`). Deposit mult 1, 0.8, 0.65 for 1, 2, 3 outputs (`getDepositMult`, `:255-258`). The industries apply them; see [Assembler](../industries/ASSEMBLER.md).
- Name: `VPC <commodity>` for one output, else `VPC <package name>`, else `VPC Package` for two and `VPC Suite` for three outputs (`getDisplayName`, `:260-270`). The commodities show on the icon and in the description. Price is the tier price.
- Icon: the engine draws `VPC_empty.png`, then `render` (`:95-132`) draws the commodity or package chip icon if there is one; otherwise the `VPCRareChip` frame for rare and ultra rare, a screen area and 1 to 3 commodity icons. Industry menus and installed item lists use `getIconName` (`:278-281`): the chip icon, else the item icon.
- Drops: rows `item_IndEvo_vpc:{tier:<tier>}`, resolved by `resolveDropParamsToSpecificItemData` (`:159-164`); no `tier` means common. Groups `indEvo_tech_addition`, `indEvo_mining_addition`, `indEvo_tech` (research projects) and `indEvo_tech_event` (rare and ultra rare only, `ItemChoiceSpecial`) at `drop_groups.csv:9-12`, `:16-19`, `:25-28`, `:80-81`.
- NPC markets: `rollForNPC` picks the tier by `npcWeight` ([Assembler](../industries/ASSEMBLER.md#ai-cores-and-npc-markets)).
- Codex: the codex stack has null data; `VPCEffect` shows a generic line for it (`VPCEffect.java:45-48`).

### Forge templates

One id, `IndEvo_ForgeTemplate`, with data `hullId|charges` (drops, salvage) or `hullId|charges|payloadKey` (Hull Deconstructor). Charges are clamped to 1 to `MAX_CHARGES` 5. The payload key points to the deconstructed `ShipVariantAPI` in `ItemPayloadMemory`; the variant keeps the quality tag `$IndEvo_qualityLevel_N` and is cloned for each print. The Ancient Laboratory relic services lower that level by one per payment ([Derelicts](../industries/DERELICTS.md#ancient-laboratory-relic-services)). `useCharge` returns the template with one charge less, or `IndEvo_DegForgeTemplate` with the payload removed when the last charge is used. A payload key without a payload is an illegal state: the tooltip names it and `createNakedFleetMemberFromForgeTemplate` throws.

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
- Every `colony_item` row needs an effect in `ITEM_EFFECTS` that works with a null industry: the codex calls `getConditionsRelatedToRequirements(null)` and `getRequirements(null)` (skill `api/impl/codex/CodexDataV2.java:2546-2556`) and links the item to the industries in its params (`:2589-2596`).
- Ambassador items are plain `SpecialItemData` with the person id as data; read them only through `AmbassadorItemHelper`. Users: [Embassy](../industries/EMBASSY.md).
- Forge templates are plain `SpecialItemData`; everything is in the data string, so a copy as `new SpecialItemData(id, data)` keeps the template. Read it only through the `ForgeTemplateItemPlugin` helpers. Users: [Derelicts](../industries/DERELICTS.md) (`HullForge`, `HullDeconstructor`, `AncientLaboratory`), [Salvage](../exploration/SALVAGE.md) (`PrintShipSpecial`, `ItemChoiceSpecial`).
- VPC content lives only in the data string; read it only through the `VPCItemPlugin` helpers (`isVPC`, `isValid`, `getTier`, `getOutputs`, `getSupply`, `getDemand`, `getDepositMult`). A copy as `new SpecialItemData(id, data)` keeps the chip. Users: [Assembler](../industries/ASSEMBLER.md), [Salvage](../exploration/SALVAGE.md) (`ConvertVPCSpecial`, `ItemChoiceSpecial`), `NewGameIndustryPlacer`.
- A VPC with null data, an unknown tier or no commodity that is in the table is invalid (`isValid`, `VPCItemPlugin.java:190-198`): tooltips name it (`VPCEffect.java:50-53`) and `getOutputs` throws `IllegalStateException` (`:224`), so an installed invalid VPC throws on apply and at month end. A commodity id that is no longer in the table (its mod removed) is left out of the outputs with one warning per id (`:230`).
- `ForgeTemplateItemPlugin.getValidVariantIdForHullId` is also used by the [Salvage Yards](../industries/SALVAGE_YARDS.md) production intel.

## Known issues and dormant code

| Item | State |
|---|---|
| `wormhole_anchor` | effect active, item row commented; see [Warehouses](../industries/WAREHOUSES.md) |
| VPCs and the Historian | The VPC row has no `hist3t` tag: vanilla `SpecialItemOffer.addItemToCargo` adds the item with null data, which would be an invalid VPC. The Historian no longer points to VPCs |
| VPC icons in industry menus | Chips without their own icon (rare and ultra rare mixes other than the packages) show the plain item icon there; the commodity icons only draw on cargo stacks |

## Maintenance

Update this doc when a non consumable row in `special_items.csv`, an effect map, a drop group, a loot rule, a VPC tier or the VPC tables change. Industry behaviour behind an item belongs in that industry's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
