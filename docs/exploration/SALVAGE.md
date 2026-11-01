# Salvage specials

IE salvage specials (the extra event that shows before salvaging an entity) and the world-gen script that assigns them. Read it before adding a special, changing special weights or debugging a salvage dialog crash. It does not cover where IE stations spawn; see [Stations](STATIONS.md). The "impoverished spacer" comm dialog that follows the credit stash belongs to [Rule commands](../dialogue/RULE_COMMANDS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

On a new game `IndEvo_SalvageSpecialAssigner` walks every salvageable entity and every planet with ruins, and adds or replaces a salvage special from a weighted table chosen by entity type. Each IE special is a vanilla `BaseSalvageSpecial` plus a `SalvageSpecialData` holder that is stored on the entity with `Misc.setSalvageSpecial`. The vanilla salvage dialog creates the plugin from the data when the player salvages.

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/salvage/utils/IndEvo_SalvageSpecialAssigner.java` | World-gen pass (`init`, `addOrReplaceSpecial`, `assignSpecial`), per-type weight tables (`pickSpecialFor`), and one `SpecialCreator` per IE special |
| `indevo/exploration/salvage/specials/ChooseBlueprintSpecial.java` | Pick one ship blueprint from 2 to 4 rare hulls of one size |
| `indevo/exploration/salvage/specials/ConvertVPCSpecial.java` | Feed a VPC to a broken commodity forge, get its commodity output times quality |
| `indevo/exploration/salvage/specials/CreditStashSpecial.java` | Free credits; with chance `credits / 100000` spawns a hostile "Impoverished" fleet that intercepts the player. Also holds `makeFleetInterceptPlayer` |
| `indevo/exploration/salvage/specials/DModRepairSpecial.java` | Remove d-mods from chosen ships; optional Corrupted (+1) or Pristine (+3) Nanoforge, not consumed |
| `indevo/exploration/salvage/specials/DroneSurveyDataSpecial.java` | Survey data 1 to 5 rolled `level` times (weight 1/i), or metals at level 0 |
| `indevo/exploration/salvage/specials/ExtraFuelSpecial.java` | Free fuel up to free tank space, or (30%) a tank explosion that damages one ship's CR and hull |
| `indevo/exploration/salvage/specials/ItemChoiceSpecial.java` | Booby-trapped room with one rare special item; taking it may blow up the entity and kill crew |
| `indevo/exploration/salvage/specials/JuicyRumorsSpecial.java` | Send intel to faction A (+rep) or its worst enemy B (+1.5x rep to B, -0.5x to A) |
| `indevo/exploration/salvage/specials/PrintShipSpecial.java` | Print a ship from a Forge Template in cargo; adds print d-mods and one built-in hullmod, uses one template charge |
| `indevo/exploration/salvage/specials/ShipRouletteSpecial.java` | Sacrifice ships; receive the largest pre-rolled hull whose DP is below the sacrificed total |
| `indevo/exploration/salvage/scripts/ExplosionScript.java` | Location script that spawns a vanilla `Entities.EXPLOSION` on the entity after 0.6 s (used by `ItemChoiceSpecial`) |
| `indevo/exploration/salvage/specials/WIP/IndEvo_SquatterDanger.java` | Excluded by `IndEvo.iml`; old package and imports, does not compile. Copy of `ItemChoiceSpecial` with squatter notes |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Setting | `IndEvo_Enable_Derelicts` (runs the assigner) | `LunaSettings.csv:14`; `ModPlugin.java:324` |
| Setting | `IndEvo_SpecialApplicationChance` (0.9) | `LunaSettings.csv:17`, `settings.json:50`; `init:51` |
| Setting | `IndEvo_shipPickerRowCount` (8) | `LunaSettings.csv:10`; ship pickers in DModRepair, PrintShip, ShipRoulette |
| Drop groups | `rare_tech` (vanilla), `indEvo_tech_event` | `data/campaign/procgen/drop_groups.csv:72-77`; item pool for `ItemChoiceSpecial` |
| settings.json | `IndEvo_ComForge_amounts` (per commodity base amounts, `logIncrease`) | `data/config/settings.json:198`; `ConvertVPCSpecial.getDepositAmount` |
| Item ids | `IndEvo_ForgeTemplate*`, `IndEvo_DegForgeTemplate`, VPC items, `IndEvo_debug` fallback | `indevo/ids/ItemIds.java`; see [Installables](../items/INSTALLABLES.md) |
| Memory (fleet) | `$IndEvo_poor_spacer`, `$IndEvo_stolen_credit_amt` | `CreditStashSpecial:118-119`; read by rules `IndEvo_ImpoverishedSpacerGreeting` and following (`data/campaign/rules.csv`, rule cmds `indevo/dialogue/rules/IndEvo_AddSpacerFineMenuPoint.java`, `IndEvo_DeductSpacerCreditsAndSetNeutral.java`) |
| Rep impact ids | `indEvo_creditSpecial`, `csSpecial` | `CreditStashSpecial:120`, `:155` |
| CR event id | `indEvo_explosion` | `ExtraFuelSpecial:193` |

## Registration and lifecycle

- `ModPlugin.onNewGameAfterEconomyLoad()` calls `new IndEvo_SalvageSpecialAssigner().init()` after `DerelictStationPlacer.init()` (`indevo/utils/ModPlugin.java:322-325`). New game only; existing saves keep whatever specials they rolled.
- Special data objects are saved on the entity (vanilla salvage special memory). They are `final`-field POJOs; renaming or moving a data class breaks old saves (inferred from XStream class names).
- Plugins are created per dialog by `SalvageSpecialData.createSpecialPlugin()`; they hold no state across dialogs except what they write back into the data object (`JuicyRumorsSpecial`, `ItemChoiceSpecial` repair their data on open).

Order: `onNewGameAfterEconomyLoad -> DerelictStationPlacer.init -> IndEvo_SalvageSpecialAssigner.init -> pickSpecialFor -> SpecialCreator.createSpecial -> Misc.setSalvageSpecial`; later `salvage dialog -> SalvageSpecialData.createSpecialPlugin -> init -> optionSelected`.

## Flow

### Assignment

`init()` (`:48-67`) skips locations tagged `THEME_HIDDEN`, `THEME_SPECIAL` or `SYSTEM_ABYSSAL`, then calls `addOrReplaceSpecial` for each `Tags.SALVAGEABLE` entity and for each planet with `TechMining.getTechMiningRuinSizeModifier > 0`.

`addOrReplaceSpecial()` (`:76-88`):

- No special yet, roll `< IndEvo_SpecialApplicationChance`, and (not a cache, or a 10% roll for caches): assign.
- Otherwise, if the existing special is not `ShipRecoverySpecialData`, 40% (`EXISTING_SPECIAL_REMOVAL_CHANCE`): assign (replaces). This branch also catches entities without a special that failed the first roll.

`pickSpecialFor()` (`:104-316`) builds one picker; an entity can match several blocks. Weights (creator args in brackets):

| Entity | Specials (weight) |
|---|---|
| Planet with ruins | nothing 25, credit stash (5k to 30k) 10, fuel 10, rumors 7, drone survey 10 (derelict theme only), d-mod repair 5, roulette 5, blueprint (cruiser max) 5, VPC 5, print ship 1 (remnant theme and ruin size > 0.2) |
| Derelict ship / wreck | nothing 120, drone survey 5, rumors 10, credit stash 5 (orbiting only) |
| Debris field | nothing 30, credit stash 10, rumors 7 |
| `station_mining_remnant` | nothing 30, item choice 5, VPC 5, credit stash 10, d-mod 10, fuel 10, rumors 5 |
| `station_research_remnant` | nothing 25, item choice 5, blueprint (capital) 15, VPC 10, print 7, roulette 15 |
| `orbital_habitat_remnant` | nothing 30, item choice 5, blueprint (destroyer) 10, credit stash 5, fuel 15, d-mod 10, rumors 10 |
| `IndEvo_orbitalLaboratory` | item choice 5, blueprint (capital) 10, print 20, roulette 10 |
| `IndEvo_arsenalStation` | nothing 20, item choice 5, blueprint (cruiser) 10, VPC 10, d-mod 10, fuel 5, vanilla sleeper pods (marines 11, organs 4, officer 1), roulette 15, vanilla ship recovery (large 2, medium 2) |
| `IndEvo_abandonedPetCenter` | nothing 30, item choice 2, credit stash 2, rumors 10, vanilla ship recovery (civilian) 8, vanilla transmitter trap 5 |
| Weapons, supply and equipment caches (all sizes) | nothing 30 plus vanilla creators (sleeper pods, ship recovery, cargo manifest, transmitter trap); IE extras: fuel 20 (supply cache), rumors 10 and credit stash 2 (equipment caches) |

Factions: recoverable ships from vanilla `getNearbyFactions` (remnant systems: Tri-Tachyon, Hegemony, independent); trap fleets pirates, remnants in suppressed or resurgent remnant systems.

### Specials

All specials call `setDone/setShowAgain/setEndWithContinue` per vanilla `BaseSalvageSpecial`. "Not now" sets `showAgain(true)` so the special returns on the next visit, except in `ItemChoiceSpecial` where declining destroys the item.

- `ChooseBlueprintSpecial`: hull ids rolled at creation from `MiscIE.getAllRareShipHulls()` of the picked size (`ChooseBlueprintSpecialCreator` weights frigate 0.4, destroyer 0.3, cruiser 0.3, capital 0.15, each times 0.8, and 0.2 for the maximum size). Adds `SpecialItemData(SHIP_BP, hullId)`.
- `ConvertVPCSpecial`: amount `round(round((logIncrease * ln 7 + base) / 10) * 10 * dualReduction * quality)` per output commodity (`:140-157`), dual output reduction from `VariableAssembler.DUAL_OUTPUT_REDUCTION_MULT` ([Assembler](../industries/ASSEMBLER.md)).
- `CreditStashSpecial`: fleet from `FleetParamsV3` with combat points `rand(min,max) * 5`, faction pirates or independent (coin flip at creation), spawned 2500 to 5000 units away, transponder off, named "Impoverished Pirate/Mercenary", flagged pirate, made aggressive and hostile for 30 days with an `INTERCEPT` assignment (`:105-163`).
- `DModRepairSpecial`: removes `dModRemovalAmt` (+1 corrupted, +3 pristine forge) random non-built-in d-mods across chosen ships using `RestorationDocks.getListNonBuiltInDMods` and restores the base hull spec when none remain ([Small industries](../industries/SMALL_INDUSTRIES.md)).
- `ExtraFuelSpecial`: explosion picks a member weighted against frigates and low-CR ships, applies CR loss (capped to keep emergency burn usable) and armor-based hull damage, hull floor 1%.
- `ItemChoiceSpecial`: on take, if `random < explodeChance`: crew loss `min(ceil(crew * 0.3 * r * explodeChance), 500)`, `ExplosionScript` on the location, and `setShouldAbortSalvageAndRemoveEntity(true)` on exit. Forge templates get a random hull via `ForgeTemplateItemPlugin.pickShip`. Invalid item ids fall back to `IndEvo_debug`.
- `JuicyRumorsSpecial`: rep through `AmbassadorPersonManager.adjustRelationship` ([Embassy](../industries/EMBASSY.md)); inactive factions from `AmbassadorPersonManager.getListOfIncativeFactions()` are excluded and re-rolled on open.
- `PrintShipSpecial`: quality = template quality + special `qualityLevel`; `ForgeTemplateItemPlugin.addPrintDefectDMods`, `HullForge.addBuiltInHullmods(1, ...)`, then the template stack is replaced by `incrementForgeTemplateData(data, -1)` ([Derelicts](../industries/DERELICTS.md)).
- `ShipRouletteSpecial`: one learnable hull per size rolled at creation with its DP; the picker leaves out the flagship and `SHIP_CAN_NOT_SCUTTLE` ships; sacrificed ships are stripped to cargo and removed; the result gets `random(qualityLevel)` d-mods and `prepareShipForRecovery` with weapon retain `1 / qualityLevel`.

## Contracts

- Special data is created at world gen and stored in the save; its constructors run hull and item lookups at that time (`ChooseBlueprintSpecialData`, `ShipRouletteSpecialData`, `JuicyRumorsSpecialData`). A mod list change after world gen can leave stale hull or faction ids; only `ItemChoiceSpecial` and `JuicyRumorsSpecial` repair their data on open.
- `CreditStashSpecial` flags the spawned fleet with `$IndEvo_poor_spacer`, which the rules greeting matches at score 10000.
- Shared helpers: `MiscIE.getAllRareShipHulls`, `getAllLearnableShipHulls`, `getVPCItemSet`, `stripShipNoCargo`, `stripShipToCargoAndReturnVariant`, `addOrIncrement` ([Utils](../core/UTILS.md)).

## Known issues and dormant code

| Item | State |
|---|---|
| `IndEvo_SquatterDanger` | Excluded by `IndEvo.iml` (`specials/WIP`), wrong package, missing `itemID` field; dead |
| `ItemChoiceSpecialCreator` repick loop | `while (item == null || item.isEmpty() || safeguard > 10)` (`IndEvo_SalvageSpecialAssigner.java:350`): the safeguard never ends the loop; an empty picker hangs world gen |
| `DModRepairSpecial` repair loop | On `removeOneDMod` failure it does `i--; continue` without removing the member from the picker (`:178-185`); a member counted by `DModManager` but with no entry in `getListNonBuiltInDMods` loops forever (inferred) |
| `pickSpecialFor` derelict branch | Casts `getCustomPlugin()` to `DerelictShipEntityPlugin` when type is `wreck` (`:152-153`); `variant` is computed and unused |
| `initNothing()` without return | `DModRepairSpecial:65`, `ItemChoiceSpecial:54`, `JuicyRumorsSpecial:53`, `ShipRouletteSpecial:101` call `initNothing()` and keep building the dialog |
| `ShipRouletteSpecial` | `qualityLevel` 0 is possible (creators with min 0) and gives `retain = 1/0` = Infinity (`:276`), same as level 1 |
| `ChooseBlueprintSpecialData` | `pickAndRemove()` NPE if fewer rare hulls of that size than `amt` (`:53-55`) |
| `DerelictStationPlacer.addDebugStation` | Dev-only caller of `PrintShipSpecialCreator` |
| Changelog_todo | Idea: "Adjust salvage event 'Ship Roulette'" with colony output and timed delivery (`Changelog_todo:155-159`) |

## Maintenance

Update this doc when a special, a creator, a weight table, the drop groups or the settings above change. Station placement belongs in [Stations](STATIONS.md); the spacer fine rules in [Rule commands](../dialogue/RULE_COMMANDS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
