# Derelict industries

Industrial ruins and the four structures they upgrade into: Ancient Laboratory, Hull Deconstructor, Hull Forge, Rift Generator (planet mover). Read before touching ruins placement, forge template flow between the three template users, or the rift planet move. It does not cover the forge template items themselves; see [Installables](../items/INSTALLABLES.md). It does not cover the shared and deconstructor storage submarkets; see [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

`IndEvo_RuinsCondition` spawns on uninhabited planets in remnant systems. Colonizing one adds the `IndEvo_Ruins` industry, which upgrades into exactly one of four structures, picked per planet by a seeded weighted roll. Lab, Deconstructor and Forge form a loop around forge templates: Deconstructor turns a ship into a template, Forge prints ships from a template until it degrades, Lab repairs degraded templates and adds a commodity bonus. Rift Generator moves the whole planet with its market to another system through an animated warp and leaves a jump point behind.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/derelicts/conditions/RuinsCondition.java` | Condition on the planet. Adds `IndEvo_Ruins` on colonization, removes itself when the ruins industry is gone. |
| `indevo/industries/derelicts/conditions/CryoRevivalDisabler.java` | Hidden condition added by Rift Generator. Daily: disrupts `cryorevival` if no cryosleeper in range. |
| `indevo/industries/derelicts/conditions/BeaconCondition.java` | Dead. No csv row, nothing references it. |
| `indevo/industries/derelicts/industry/Ruins.java` | Base industry. `getSpec()` returns a per instance spec view whose upgrade is the market's target, AI auto upgrade, cleans the condition on removal. |
| `indevo/industries/derelicts/industry/BaseForgeTemplateUser.java` | Abstract base for Lab, Decon, Forge (and RiftGen availability). Build check, forge template slot, `getSpec()` view without the CSV downgrade. Extends `SharedSubmarketUser`. |
| `indevo/industries/derelicts/industry/AncientLaboratory.java` | Template repair and commodity bonus. |
| `indevo/industries/derelicts/industry/HullDeconstructor.java` | Ship to forge template. Owns the `IndEvo_DeconstStorage` submarket. |
| `indevo/industries/derelicts/industry/HullForge.java` | Forge template to ship. Cost, d-mods, built in hullmods. |
| `indevo/industries/derelicts/industry/RiftGenerator.java` | Cooldown, target pick, starts `PlanetMovingScript`. |
| `indevo/industries/derelicts/industry/WIP/*.java` (3 classes) | `IndEvo_DistGun`, `IndEvo_DroneCont`, `IndEvo_Lighthouse`. Excluded by `IndEvo.iml:11`, old package `industrialevolution.*`, do not compile. |
| `indevo/industries/derelicts/scripts/PlanetMovingScript.java` | Persistent script. Builds the jump point, moves planet and market, re-applies station. |
| `indevo/industries/derelicts/scripts/WarpEffectAnimationScript.java` | Staged warp animation via LunaLib campaign renderer. |
| `indevo/industries/derelicts/entities/WarpEffectPlugin.java`, `HoleEffectPlugin.java` | Deprecated custom entity plugins, unused. |
| `indevo/industries/derelicts/hullmods/PrintDefectsLow/Med/High.java` | Print d-mods. |
| `indevo/industries/derelicts/hullmods/PrintingHullMod.java` | `IndEvo_auto` indicator; raises S-mod cap by the `IndEvo_compN` tag. |
| `indevo/industries/derelicts/listeners/AncientLabCommoditySwitchOptionProvider.java` | Industry option "change bonus commodity" on the lab. |
| `indevo/industries/derelicts/listeners/PlanetJumpListener.java` | Interface fired after a move. No implementers. |
| `indevo/industries/derelicts/plugins/RiftGenOptionProvider.java` | Industry option that opens the rift rules dialog. |
| `indevo/industries/derelicts/plugins/RemoveIndustryOptionWarningProvider.java` | Confirm dialog before removing any industry tagged `IndEvo_showDeconstructWarning`. |
| `indevo/industries/derelicts/rules/IndEvo_*.java` (4 classes) | Rule commands: `IndEvo_RiftInit`, `IndEvo_GeneratorReadyToFire`, `IndEvo_GeneratorAllowedToFire`, `IndEvo_MoreThanTwoColonies`. |
| `indevo/industries/derelicts/utils/RuinsManager.java` | Upgrade roll, sector placement, core world cleanup. |
| `indevo/industries/derelicts/utils/RuinsCountMap.java` | Deprecated, unused. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Ruins`, `IndEvo_ResLab`, `IndEvo_HullDecon`, `IndEvo_HullForge`, `IndEvo_RiftGen` | `data/campaign/industries.csv:93-97`. All downgrade to `IndEvo_Ruins`, tags `do_not_show_in_build_dialog`, `IndEvo_showDeconstructWarning`. |
| Condition | `IndEvo_RuinsCondition` | `data/campaign/market_conditions.csv:20`, `data/campaign/procgen/condition_gen_data.csv:2` (group `industry`, freq 5) |
| Condition | `IndEvo_CryoRevivalDisabler` (hidden) | `data/campaign/market_conditions.csv:27` |
| Hullmod | `IndEvo_print_low`, `IndEvo_print_med`, `IndEvo_print_high` (dmod) | `data/hullmods/hull_mods.csv:2,5,8` |
| Hullmod | `IndEvo_auto` (`Ids.PRINTING_INDICATOR`) | `data/hullmods/hull_mods.csv:11` |
| Entity | `IndEvo_WarpEffect`, `IndEvo_WormHoleEffect` | `data/config/custom_entities.json:1166-1185` |
| Jump point | id `IndEvo_planet_replacer`, desc `IndEvo_JumpRift` | `PlanetMovingScript.java:50` |
| Variant tag | `IndEvo_comp1/2/3` | `HullForge.java:431-433` |
| Variant tag | `IndEvo_hasInbuiltLogMod` | `HullDeconstructor.java:56` |
| Memory key | `$IndEvo_ruinsIndustryId` (market), `$IndEvo_ruinsPlaced_<marketId>` | `Ruins.java:29`, `RuinsManager` |
| Memory key | `$IndEvo_LabBonusCommodityID` and `_1/_2/_3`, `$IndEvo_ruinsPickerSeed` (sector) | `AncientLaboratory.java:38` |
| Memory key | `"$" + industryId` sector flag (template tooltips) | `BaseForgeTemplateUser.java:81-84` |
| Memory key | `$IndEvo_original_planet_orbit`, `$IndEvo_closeDialogueOnNextReturn` | `PlanetMovingScript`, rift rules |
| Setting | `IndEvo_Enable_Derelicts` | `data/config/LunaSettings.csv:14` |
| Setting | `IndEvo_derelictDeliverToGathering`, `IndEvo_RG_cooldownTime` (90), `IndEvo_RG_range` (9), `IndEvo_reslab_autoDeliverToClosestDecon` | `data/config/LunaSettings.csv:133-136` |
| Setting | `IndEvo_hullDecon_autoDeliverToClosestForge`, `IndEvo_hullDecon_days_*` (10/20/30/40) | `data/config/LunaSettings.csv:139-143` |
| Setting | `IndEvo_hullForge_autoQueueShipsUntilEmpty`, `IndEvo_hullForge_autoDeliverToClosestLab`, `IndEvo_hullForge_days_*` (5/10/15/20) | `data/config/LunaSettings.csv:144-149` |
| Setting | `IndEvo_ToggleMagicRandomSelector` (settings.json only) | `data/config/settings.json:234`, read at `HullForge.java:287,463` |
| Config | `IndEvo_labList` -> `data/config/indEvo/lab_commodity_whitelist.csv` | `data/config/settings.json:226` |
| Rules | `IndEvo_RiftGenTargetOptions`, `IndEvo_RiftTarget_*`, `IndEvo_FireBaseRGOptionList`, `IndEvo_RiftControlMenuPoint*` | `data/campaign/rules.csv:845-923` |
| Strings | `IndEvo_HullDecon`, `IndEvo_HullForge` AI core text | `data/strings/strings.json:270,279` |

Forge template item ids (`IndEvo_ForgeTemplate`, `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate`) live in [Installables](../items/INSTALLABLES.md). Settings mechanics: [Settings](../core/SETTINGS.md).

## Registration and lifecycle

- `ModPlugin.onNewGameAfterEconomyLoad`: `RuinsManager.forceCleanCoreRuins()` (`indevo/utils/ModPlugin.java:303`) -> `RuinsManager.placeIndustrialRuins()` (`:332`).
- `ModPlugin.onGameLoad`, transient listeners re-added each load: `DerelictRuinsPlacer` (PlayerColonizationListener, `:462`), `ResolveRuinsToUpgradeListener` (SurveyPlanetListener, `:463`), `AncientLabCommoditySwitchOptionProvider.register()` (`:478`), `RiftGenOptionProvider.register()` (`:497`), `RemoveIndustryOptionWarningProvider.register()` (`:506`).
- Each industry adds itself as a transient listener in `apply()` and removes itself in `unapply()`. `onNewDay` comes from IE's `NewDayListener`, driven by `indevo/utils/timers/TimeTracker.java:130`. See [Utils](../core/UTILS.md).
- Rule commands: package `indevo.industries.derelicts.rules` listed in `data/config/settings.json:240`. `IndEvo_requiresColonyMenuPoint.java:32` also checks `IndEvo_RiftGen`; see [Rule commands](../dialogue/RULE_COMMANDS.md).
- `PlanetMovingScript` is persistent (`Global.getSector().addScript`, `RiftGenerator.java:384-386`). The animation script is transient.
- Console command `SpawnRuins`: see [Console](../core/CONSOLE.md).

## Flow

### Ruins placement and upgrade pick

`placeIndustrialRuins` (`RuinsManager.java:104-169`) -> for uninhabited remnant systems only: chance 0.1, suppressed 0.3, resurgent 0.5; second planet x0.7; max 2 per system; tag `IndEvo_RuinsAlways` forces it. Removes generated conditions elsewhere. `seedRuinsIfNeeded` adds `ruins_scattered`.

`RuinsCondition.apply` (`RuinsCondition.java:23`) -> on a real non neutral colony: `RuinsManager.setUpgradeSpec` if `$IndEvo_ruinsIndustryId` missing -> add `IndEvo_Ruins` once.

`setUpgradeSpec` (`RuinsManager.java:171-207`): weights Lab 1, Decon 0.7, Forge 0.7, RiftGen 0.35, seeded by the planet salvage seed. RiftGen is excluded with solar array, artillery condition, minefield, Niko satellites, rings, gas giant.

`Ruins.getSpec()` (`Ruins.java:40-49`) returns an `IndustrySpecView` ([Utils](../core/UTILS.md)) whose upgrade is `$IndEvo_ruinsIndustryId`, so the option menu, `startUpgrading` and the tooltips read this market's target. `Ruins.apply` (`:35`, no `super.apply`) -> `triggerUpgradeForAI` (`:84-99`): NPC market size > 4, free slot, more than 2 markets. `BaseForgeTemplateUser.isAvailableToBuild` (`:40`) only lets the matching industry build, and not while ruins are upgrading. The CSV downgrade to `IndEvo_Ruins` only keeps the four structures out of the build list (skill `obf/campaign/ui/marketinfo/IndustryPickerDialog.java:182`); `BaseForgeTemplateUser.getSpec()` (`:52`) and `RiftGenerator.getSpec()` (`:55`) return a view without it, so the menu never offers "Downgrade to Old Industrial Ruins".

### Forge template loop

- Deconstructor: ship put into `IndEvo_DeconstStorage` (added in `advance`, `HullDeconstructor.java:73`) -> stripped, N days by hull size -> forge template with 2 or 3 charges (+1 beta), quality base 1, alpha/beta 2, gamma 0. Alpha adds a built in logistics hullmod (`addBuiltInLogisticsHullmods`, `:160`). Gamma: upkeep x0.7 while idle. Optional auto delivery to the closest forge.
- Forge: one ship per charge into storage. Cost 20% of base value, x0.7 with beta, booked on the monthly report industry node (`chargeCosts`, `HullForge.java:449`). D-mods via `ForgeTemplateItemPlugin.addPrintDefectDMods` (template quality + forge quality). Alpha: 2 built in hullmods plus `IndEvo_compN` tag (`compensateInbuilts`, `:435`). Spent template: degraded, or empty with 0.6 chance on beta (`EMPTY_FORGE_TEMPLATE_CHANCE`, `:83`).
- Lab: degraded template -> empty template, 20 days, 10 with gamma (`initRepair`, `AncientLaboratory.java:121`). Commodity bonus +2 on existing supply of a whitelist commodity; beta +3 on one, alpha +1 on three (`getBonusValue :137`, `setAllCommodityBonuses :148`).
- All three autofeed from the shared storage and deliver to the gathering point when `IndEvo_derelictDeliverToGathering` is on (inferred: `repairInstalledFT :270` pattern repeats in decon and forge).

### Rift Generator move

`RiftGenOptionProvider.onClick` -> rules `IndEvo_RiftGenTargetOptions` -> `IndEvo_RiftInit <MODE>` -> `RiftGenerator.initRift` (`:185`) -> `getTarget` (`:261`, modes CORE within 6 LY of Askonia, COLONY, FRINGE, RANDOM; range `IndEvo_RG_range` +3 LY beta) -> `movePlanet` (`:381`): adds `PlanetMovingScript`, `RecentUnrest` +1.

Rules: `moveIsLegal` (`:154`, no rings, no boggled mirror or shade orbiters). Cooldown `IndEvo_RG_cooldownTime` days counted in `onNewDay` (`:102`), alpha -30. Gamma allows a second artificial rift per system (`getArtificialRiftAmount`, `:211`). `advance` adds `IndEvo_CryoRevivalDisabler`.

`PlanetMovingScript.advance` (`:78`): wait 5 s -> phase 0 starts `WarpEffectAnimationScript` -> phase 2 (forced after 10 s, `:89-92`) -> `createJumpPoint` (`:150`) with magnetic field at the old orbit -> `clearSpecialEntitiesWithOrbitTarget` (`:199`) -> `transferOrbitFocus` (`:177`, obf `CircularOrbit*` classes) -> `movePlanet` (`:367`): new planet in target with same id and copied spec, market removed and re-added, hyperspace jump point destinations cycled (`:435`), old planet expired (`:476`) -> `reApplyStation` (`:517`) -> `triggerJumpListeners` (`:136`) -> `setMoving(false)`.

## Contracts

- `BaseIndustry.spec` is the shared `Global.getSettings().getIndustrySpec(id)` (skill `api/impl/campaign/econ/impl/BaseIndustry.java:160,702`); never write per market values onto it. `Ruins` overrides `getSpec()` with a view instead; every vanilla reader of the upgrade goes through `getSpec()` (option menu `_c37.java:224,259`, `BaseIndustry.java:575,1075`). Forge template users and `RiftGenerator` do the same for the downgrade.
- `$IndEvo_ruinsIndustryId` is the single source for which structure a ruin becomes. Set once, persists in market memory. Also read by `Supercomputer.java:213`, `ModUpdater.java:96-141`, `LocatorSystemRatingUpdater.java:121`.
- Forge templates move between industries only as cargo through `IndEvo_SharedStore` / `IndEvo_DeconstStorage` and storage. Item plugins (`ForgeTemplateItemPlugin.java:132,248,306`, `EmptyForgeTemplateItemPlugin.java:54-61`) own charges, quality and `IndEvo_auto`.
- Rift move replaces the planet entity, keeping the id. Anything that caches `PlanetAPI` refs to a moved planet holds a dead entity (inferred). `PlanetJumpListener` is the hook for that and has no users.
- Transient listeners in `onGameLoad` have no class dedupe; `ListenerManager.addListener` dedupes only the same instance (`obf/campaign/ListenerManager.java:29`). Fine since transient lists are empty at load.
- `getTransientScripts()` and `getScripts()` are separate lists (`obf/campaign/CampaignEngine.java:1061-1130`). `PlanetMovingScript` is a persistent sector script, so `WarpEffectAnimationScript.movePlanet` and `WarpEffectPlugin.movePlanet` search `getScripts()`.

## Known issues and dormant code

| Item | State |
|---|---|
| `industry/WIP/` (3 classes, `IndEvo_Lighthouse` = `Ids.BEACON`) | Excluded by `IndEvo.iml:11`, no csv rows. |
| `BeaconCondition` | Dead. Static `STABILITY_PENALTY` shared across instances. |
| `RuinsCountMap`, `WarpEffectPlugin`, `HoleEffectPlugin` | Deprecated, unused. |
| `PlanetJumpListener` | No implementers. |
| `AncientLaboratory.java:284-296` | Null gathering point returns false, retried daily. |
| `HullForge.java:292` | NPE if the target market has no storage submarket. |
| `PlanetMovingScript.java:557` | `removeEntity(null)` when there is no magnetic field. Effect unverified. |
| Changelog_todo | "riftgen anim sounds" (`Changelog_todo:55,82`), "Change derelict industries to superstructures" (`:140`). |

## Maintenance

Update this doc when an industry in `indevo/industries/derelicts` changes its cost, cores, timers or delivery, when the ruins placement or weights change, or when the rift move steps change. Forge template item rules go in [Installables](../items/INSTALLABLES.md), storage submarkets in [Submarkets](../core/SUBMARKETS.md), the artillery condition in [Artillery](ARTILLERY.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
