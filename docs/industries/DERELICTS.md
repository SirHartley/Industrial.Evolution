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
| `indevo/industries/derelicts/industry/BaseForgeTemplateUser.java` | Abstract base for Lab, Decon, Forge (and RiftGen availability). Build check, `getSpec()` view without the CSV downgrade. The forge template slot is the vanilla item slot (template rows' `plugin params`). Extends `SharedSubmarketUser`. |
| `indevo/industries/derelicts/industry/AncientLaboratory.java` | Template repair and commodity bonus. `RelicSpeedUpTarget` (Accelerate). |
| `indevo/industries/derelicts/industry/HullDeconstructor.java` | Ship to forge template. Owns the `IndEvo_DeconstStorage` submarket. `RelicSpeedUpTarget` (Accelerate). |
| `indevo/industries/derelicts/industry/HullForge.java` | Forge template to ship. Cost, d-mods, built in hullmods. |
| `indevo/industries/derelicts/industry/RiftGenerator.java` | Cooldown, target pick, starts `PlanetMovingScript`. `RelicSpeedUpTarget` (Recharge). |
| `indevo/industries/derelicts/industry/WIP/*.java` (3 classes) | `IndEvo_DistGun`, `IndEvo_DroneCont`, `IndEvo_Lighthouse`. Excluded by `IndEvo.iml:11`, old package `industrialevolution.*`, do not compile. |
| `indevo/industries/derelicts/scripts/PlanetMovingScript.java` | Persistent script. Builds the jump point, moves planet and market, re-applies station. |
| `indevo/industries/derelicts/scripts/WarpEffectAnimationScript.java` | Staged warp animation via LunaLib campaign renderer. |
| `indevo/industries/derelicts/entities/WarpEffectPlugin.java`, `HoleEffectPlugin.java` | Deprecated custom entity plugins, unused. |
| `indevo/industries/derelicts/hullmods/PrintDefectsLow/Med/High.java` | Print d-mods. |
| `indevo/industries/derelicts/hullmods/PrintingHullMod.java` | `IndEvo_auto` indicator; raises S-mod cap by the `IndEvo_compN` tag. |
| `indevo/industries/derelicts/listeners/AncientLabCommoditySwitchOptionProvider.java` | Industry option "change bonus commodity" on the lab. |
| `indevo/industries/derelicts/plugins/AncientLabRelicOptionProvider.java` | Industry option "Relic services..." on a functional player owned lab, also on remote colony screens. Opens the relic panel. |
| `indevo/industries/derelicts/dialogue/AncientLabRelicPanelDelegate.java` | Relic services panel (`BaseCustomDialogDelegate` on the [UI kit](../core/UI.md)): Templates and Ships tabs as list and detail, cost, payment, result line, `?` with the rules and rates. Static `show(market, view)` opens it in its own interaction dialog (nested `Host`). |
| `indevo/industries/derelicts/dialogue/AncientLabRelicView.java` | View state: tab (`ships`), `selected` row key (`FIRST` picks the first row, null shows nothing picked), `message` (result line), scroll offsets. |
| `indevo/industries/derelicts/listeners/PlanetJumpListener.java` | Interface fired after a move. No implementers. |
| `indevo/industries/derelicts/plugins/RiftGenOptionProvider.java` | Industry option that opens the rift rules dialog. |
| `indevo/industries/derelicts/plugins/RemoveIndustryOptionWarningProvider.java` | Confirm dialog before removing any industry tagged `IndEvo_showDeconstructWarning`. |
| `indevo/industries/derelicts/rules/IndEvo_*.java` (4 classes) | Rule commands: `IndEvo_RiftInit`, `IndEvo_GeneratorReadyToFire`, `IndEvo_GeneratorAllowedToFire`, `IndEvo_MoreThanTwoColonies`. |
| `indevo/industries/derelicts/utils/RuinsManager.java` | Upgrade roll, sector placement, core world cleanup. |
| `indevo/industries/derelicts/utils/IndustrySpecView.java` | `IndustrySpecAPI` that delegates to the shared spec but returns its own `getUpgrade`, `getDowngrade` and their plugin instances. `of(cached, spec, upgrade, downgrade)` reuses the cached view while it still matches. Returned by `Ruins.getSpec()` (`Ruins.java:28`), `BaseForgeTemplateUser.getSpec()` (`:52`) and `RiftGenerator.getSpec()` (`:55`). |
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
| Variant tag | `IndEvo_comp1/2/3` | `HullForge.java:422-424` |
| Variant tag | `IndEvo_hasInbuiltLogMod` | `HullDeconstructor.java:55` |
| Memory key | `$IndEvo_ruinsIndustryId` (market), `$IndEvo_ruinsPlaced_<marketId>` | `Ruins.java:17`, `RuinsManager` |
| Variant tag | `no_auto_penalty` (vanilla `Tags.TAG_AUTOMATED_NO_PENALTY`), added by the Ships tab | `AncientLabRelicPanelDelegate.integrate` |
| Memory key | `$IndEvo_LabBonusCommodityID` and `_1/_2/_3`, `$IndEvo_ruinsPickerSeed` (sector) | `AncientLaboratory.java:37` |
| Memory key | `"$" + industryId` sector flag (template tooltips) | `BaseForgeTemplateUser.java:73-76` |
| Memory key | `$IndEvo_original_planet_orbit`, `$IndEvo_closeDialogueOnNextReturn` | `PlanetMovingScript`, rift rules |
| Setting | `IndEvo_Enable_Derelicts` | `data/config/LunaSettings.csv:7` |
| Setting | `IndEvo_derelictDeliverToGathering`, `IndEvo_RG_cooldownTime` (90), `IndEvo_RG_range` (9), `IndEvo_reslab_autoDeliverToClosestDecon` | `data/config/LunaSettings.csv:147-150` |
| Setting | `IndEvo_hullDecon_autoDeliverToClosestForge`, `IndEvo_hullDecon_days_*` (10/20/30/40) | `data/config/LunaSettings.csv:153-157` |
| Setting | `IndEvo_hullForge_autoQueueShipsUntilEmpty`, `IndEvo_hullForge_autoDeliverToClosestLab`, `IndEvo_hullForge_days_*` (5/10/15/20) | `data/config/LunaSettings.csv:158-163` |
| Setting | `IndEvo_ToggleMagicRandomSelector` (settings.json only) | `data/config/settings.json:28`, read at `HullForge.java:278,444` |
| Config | `IndEvo_labList` -> `data/config/indEvo/lab_commodity_whitelist.csv` | `data/config/settings.json:19` |
| Rules | `IndEvo_RiftGenTargetOptions`, `IndEvo_RiftTarget_*`, `IndEvo_FireBaseRGOptionList`, `IndEvo_RiftControlMenuPoint*` | `data/campaign/rules.csv:732-810` |

Forge template item ids (`IndEvo_ForgeTemplate`, `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate`) live in [Installables](../items/INSTALLABLES.md). Settings mechanics: [Settings](../core/SETTINGS.md).

## Registration and lifecycle

- `ModPlugin.onNewGameAfterEconomyLoad`: `RuinsManager.forceCleanCoreRuins()` (`indevo/utils/ModPlugin.java:290`) -> `RuinsManager.placeIndustrialRuins()` (`:305`).
- `ModPlugin.onGameLoad`, transient listeners re-added each load: `DerelictRuinsPlacer` (PlayerColonizationListener, `:437`), `ResolveRuinsToUpgradeListener` (SurveyPlanetListener, `:438`), `AncientLabCommoditySwitchOptionProvider.register()` (`:454`), `AncientLabRelicOptionProvider.register()` (`:455`), `RiftGenOptionProvider.register()` (`:474`), `RemoveIndustryOptionWarningProvider.register()` (`:483`).
- Each industry adds itself as a transient listener in `apply()` and removes itself in `unapply()`. `onNewDay` comes from IE's `NewDayListener`, driven by `indevo/utils/timers/TimeTracker.java:127`. See [Utils](../core/UTILS.md).
- Rule commands: package `indevo.industries.derelicts.rules` listed in `data/config/settings.json:35`. `IndEvo_requiresColonyMenuPoint.java:32` also checks `IndEvo_RiftGen`; see [Rule commands](../dialogue/RULE_COMMANDS.md).
- `PlanetMovingScript` is persistent (`Global.getSector().addScript`, `RiftGenerator.java:384-386`). The animation script is transient.
- Console command `SpawnRuins`: see [Console](../core/CONSOLE.md).

## Flow

### Ruins placement and upgrade pick

`placeIndustrialRuins` (`RuinsManager.java:104-169`) -> for uninhabited remnant systems only: chance 0.1, suppressed 0.3, resurgent 0.5; second planet x0.7; max 2 per system; tag `IndEvo_RuinsAlways` forces it. Removes generated conditions elsewhere. `seedRuinsIfNeeded` adds `ruins_scattered`.

`RuinsCondition.apply` (`RuinsCondition.java:23`) -> on a real non neutral colony: `RuinsManager.setUpgradeSpec` if `$IndEvo_ruinsIndustryId` missing -> add `IndEvo_Ruins` once.

`setUpgradeSpec` (`RuinsManager.java:171-207`): weights Lab 1, Decon 0.7, Forge 0.7, RiftGen 0.35, seeded by the planet salvage seed. RiftGen is excluded with solar array, artillery condition, minefield, Niko satellites, rings, gas giant.

`Ruins.getSpec()` (`Ruins.java:28-32`) returns an `IndustrySpecView` whose upgrade is `$IndEvo_ruinsIndustryId`, so the option menu, `startUpgrading` and the tooltips read this market's target. `Ruins.apply` (`:22`, no `super.apply`) -> `RuinsManager.setUpgradeSpec` if the market has no target yet (ruins added by `DerelictRuinsPlacer` on a planet whose condition came after new game and was surveyed without the survey listener, for example through `SurveyDataSpecial`) -> `triggerUpgradeForAI` (`:67-83`): NPC market size > 4, free slot, more than 2 markets, not already upgrading (`apply` runs on every economy step, and `startUpgrading` resets the progress). `BaseForgeTemplateUser.isAvailableToBuild` (`:40`) only lets the matching industry build, and not while ruins are upgrading. The CSV downgrade to `IndEvo_Ruins` only keeps the four structures out of the build list (skill `obf/campaign/ui/marketinfo/IndustryPickerDialog.java:182`); `BaseForgeTemplateUser.getSpec()` (`:52`) and `RiftGenerator.getSpec()` (`:55`) return a view without it, so the menu never offers "Downgrade to Old Industrial Ruins".

### Forge template loop

- Deconstructor: ship put into `IndEvo_DeconstStorage` (added in `advance`, `HullDeconstructor.java:72`) -> stripped, N days by hull size -> forge template with 2 or 3 charges (+1 beta), quality base 1, alpha/beta 2, gamma 0. Alpha adds a built in logistics hullmod (`addBuiltInLogisticsHullmods`, `:149`). Gamma: upkeep x0.7 while idle. Optional auto delivery to the closest forge.
- Forge: one ship per charge into storage. Cost 20% of base value, x0.7 with beta, booked on the "Hull printing" report line ([BillingHelper](../core/UTILS.md#billinghelper)). D-mods via `ForgeTemplateItemPlugin.addPrintDefectDMods` (template quality + forge quality). Alpha: 2 built in hullmods plus `IndEvo_compN` tag (`compensateInbuilts`, `:426`). Spent template: degraded, or empty with 0.6 chance on beta (`EMPTY_FORGE_TEMPLATE_CHANCE`, `:82`).
- Lab: degraded template -> empty template, 20 days, 10 with gamma (`initRepair`, `AncientLaboratory.java:113`). Commodity bonus +2 on existing supply of a whitelist commodity; beta +3 on one, alpha +1 on three (`getBonusValue :129`, `setAllCommodityBonuses :140`).
- Relic speed up: `HullDeconstructor` and `AncientLaboratory` (both Accelerate) are `RelicSpeedUpTarget`s. The industry option halves the days left (adds to `daysPassed`) for 1 Relic Component per remaining day, once per 60 days per industry. See [Economy](../core/ECONOMY.md#relic-speed-up).
- All three autofeed from the shared storage and deliver to the gathering point when `IndEvo_derelictDeliverToGathering` is on (inferred: `repairInstalledFT :270` pattern repeats in decon and forge).

### Ancient Laboratory relic services

"Relic services..." -> `AncientLabRelicPanelDelegate.show(ui, lab, new AncientLabRelicView())` -> `DialogCreatorUI.showDialog`, so it works on the remote colony screen. Both tabs are instant on payment. Relic Components come from the lab colony's storage first, then fleet cargo (`RelicHelper.take`).

Panel (board 11 and 12, 1004 x 560 or less on small screens): title with `?` (rules and rates per tab), tabs Templates and Ships, the Relic Components stock right. Left: heading, table header, scrolling rows (48 high) and one result line at the bottom (`view.message`, green, set after Refine or Integrate, cleared on a click). Right: a scrolling detail column (`AncientLabRelicView.DETAIL`): heading, hull picture, the effect as before and after values; below it, the lore text (`REFINE_LORE`, `INTEGRATE_LORE`) in grey as the last scrolling item; pinned and never scrolling, the cost row and the green action button. When Relic Components do not cover the cost the button is grey with "Not enough Relic Components." below. An empty list keeps its heading and one grey line; with no row picked the detail says "Pick a Forge Template to refine." or "Pick a ship to integrate.". The panel opens with nothing picked.

- Template rows: template icon, hull sprite, hull name and size, quality level as pips and text, stack size, cost with the relic icon (red when short). Detail: quality level before and after as pips, the print defect the printed hull gets before and after (`getDefectId`, same thresholds as `ForgeTemplateItemPlugin.addPrintDefectDMods`), templates in the stack, charges, where it is stored. Button Refine.
- Ship rows: ship sprite, ship name, hull and size, location (Fleet or storage), the max CR penalty, cost. Detail: max CR and Automated Ships limit as before and after with the skill icon, "Permanent". Button Integrate.

- Templates tab: forge templates in `IndEvo_SharedStore` and local storage with a payload variant and quality level above 0. Refine runs `ForgeTemplateItemPlugin.incrementForgeTemplateQualityLevel(data, -1)` on the payload, so every stack sharing that payload key changes. Cost `RELICS_PER_TEMPLATE_FP` (2) x hull FP x stack size, per level. Templates without a payload (drops, salvage) are always level 0 and never show.
- Ships tab: fleet members and local storage ships where `Misc.isAutomated` and not `Automated.isAutomatedNoPenalty`. Integrate clones the variant, clears the original variant, gives it a new id, adds `no_auto_penalty` and sets it with `member.setVariant(variant, false, true)`. Vanilla then skips the -100% max CR (skill `api/impl/hullmods/Automated.java:20`) and the Automated Ships skill point count (`BaseSkillEffectDescription.java:792`). Cost `RELICS_PER_SHIP_DP` (10) x hull DP (`getSuppliesToRecover`).

### Rift Generator move

`RiftGenOptionProvider.onClick` -> rules `IndEvo_RiftGenTargetOptions` -> `IndEvo_RiftInit <MODE>` -> `RiftGenerator.initRift` (`:185`) -> `getTarget` (`:261`, modes CORE within 6 LY of Askonia, COLONY, FRINGE, RANDOM; range `IndEvo_RG_range` +3 LY beta) -> `movePlanet` (`:381`): adds `PlanetMovingScript`, `RecentUnrest` +1.

Rules: `moveIsLegal` (`:154`, no rings, no boggled mirror or shade orbiters). Cooldown `IndEvo_RG_cooldownTime` days counted in `onNewDay` (`:102`), alpha -30. Recharge: `RiftGenerator` is an instant `RelicSpeedUpTarget`; the industry option ends the cooldown for 3 Relic Components per remaining day, once per 60 days, not while moving. See [Economy](../core/ECONOMY.md#relic-speed-up). Gamma allows a second artificial rift per system (`getArtificialRiftAmount`, `:211`). `advance` adds `IndEvo_CryoRevivalDisabler`.

`PlanetMovingScript.advance` (`:78`): wait 5 s -> phase 0 starts `WarpEffectAnimationScript` -> phase 2 (forced after 10 s, `:89-92`) -> `createJumpPoint` (`:150`) with magnetic field at the old orbit -> `clearSpecialEntitiesWithOrbitTarget` (`:199`) -> `transferOrbitFocus` (`:177`, obf `CircularOrbit*` classes) -> `movePlanet` (`:367`): new planet in target with same id and copied spec, market removed and re-added, hyperspace jump point destinations cycled (`:435`), old planet expired (`:476`) -> `reApplyStation` (`:517`) -> `triggerJumpListeners` (`:136`) -> `setMoving(false)`.

## Contracts

- Relic service help states the minimum template quality level and the permanent CR and skill-limit effects of ship integration.

- `BaseIndustry.spec` is the shared `Global.getSettings().getIndustrySpec(id)` (skill `api/impl/campaign/econ/impl/BaseIndustry.java:160,702`); never write per market values onto it. `Ruins` overrides `getSpec()` with a view instead; every vanilla reader of the upgrade goes through `getSpec()` (the industry option menu, obfuscated `b` in `com.fs.starfarer.campaign.ui.marketinfo` on 0.98a-RC8 Windows, skill `obf/campaign/ui/marketinfo/_c37.java:224,259`; `BaseIndustry.java:575,1075`). Forge template users and `RiftGenerator` do the same for the downgrade.
- `$IndEvo_ruinsIndustryId` is the single source for which structure a ruin becomes. Set once, persists in market memory. Also read by `Supercomputer.java:205`, `ModUpdater.java:96-141`, `LocatorSystemRatingUpdater.java:121`.
- The relic panel only changes the template payload tag and ship variant tags; it saves nothing of its own.
- Forge templates move between industries only as cargo through `IndEvo_SharedStore` / `IndEvo_DeconstStorage` and storage. Item plugins own the template data, charges and quality ([Installables](../items/INSTALLABLES.md#forge-templates)). Each template id lists only the structure that uses it in its params, and the Forge and the Deconstructor hide the slot while working.
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
| `AncientLaboratory.java:263-275` | Null gathering point returns false, retried daily. |
| `HullForge.java:283` | NPE if the target market has no storage submarket. |
| `PlanetMovingScript.java:557` | `removeEntity(null)` when there is no magnetic field. Effect unverified. |
| Changelog_todo | "riftgen anim sounds" (`Changelog_todo:55,82`), "Change derelict industries to superstructures" (`:140`). |

## Maintenance

Update this doc when an industry in `indevo/industries/derelicts` changes its cost, cores, timers or delivery, when the ruins placement or weights change, or when the rift move steps change. Forge template item rules go in [Installables](../items/INSTALLABLES.md), storage submarkets in [Submarkets](../core/SUBMARKETS.md), the artillery condition in [Artillery](ARTILLERY.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
