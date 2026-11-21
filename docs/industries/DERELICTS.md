# Derelict industries

Industrial ruins and the four structures they upgrade into: Ancient Laboratory, Hull Deconstructor, Hull Forge, Rift Generator (planet mover). Read before touching ruins placement, forge template flow between the three template users, or the rift planet move. It does not cover the forge template items themselves; see [Installables](../items/INSTALLABLES.md). It does not cover the shared and deconstructor storage submarkets; see [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

`IndEvo_RuinsCondition` spawns on uninhabited planets in remnant systems. Colonizing one adds the `IndEvo_Ruins` industry, which upgrades into exactly one of four structures, picked per planet by a seeded weighted roll. Lab, Deconstructor and Forge form a loop around forge templates: Deconstructor turns a ship into a template, Forge prints ships from a template until it degrades, Lab repairs degraded templates and adds a commodity bonus. Rift Generator moves the planet, its market and everything in its orbit to another system through an animated warp and leaves a jump point behind. The free modes jump to a random system of a target area shown on the sector map; Precise targeting picks one system for Relic Components.

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
| `indevo/industries/derelicts/industry/RiftGenerator.java` | Cooldown, AI cores, block reason, `fire(mode, random)` and `fire(system)`, starts `RiftJumpScript`. `RelicSpeedUpTarget` (Recharge). |
| `indevo/industries/derelicts/RiftTargetMode.java` | Free modes `COLONY`, `CORE`, `FRINGE`, `RANDOM`, their reach multipliers (0.9, 0.8, 1, 1.3) and rules option ids. |
| `indevo/industries/derelicts/RiftTargets.java` | Eligible systems, the target area of a mode, Precise targets, the core centre. Built per call from the generator's range. |
| `indevo/industries/derelicts/RiftTargetArea.java` | One mode's area: aim point, anchor (closest system to the aim), systems, `pick(random)`, description. |
| `indevo/industries/derelicts/RiftRelocator.java` | The jump: tear jump point, the same planet object moved with its orbiters, period sync for mirrors and shades, free orbit in the target, `getMovers`, `closeTear`. |
| `indevo/industries/derelicts/RiftBlockers.java` | Reasons a planet can not jump (first match): no planet, AOTD megastructure, `$IndEvo_riftBlocked`, a blocking entity in orbit. |
| `indevo/industries/derelicts/industry/WIP/*.java` (3 classes) | `IndEvo_DistGun`, `IndEvo_DroneCont`, `IndEvo_Lighthouse`. Excluded by `IndEvo.iml:11`, old package `industrialevolution.*`, do not compile. |
| `indevo/industries/derelicts/scripts/RiftJumpScript.java` | Persistent script: 5 s after firing plays the warp, then `RiftRelocator.relocate`, campaign message, `PlanetJumpListener`, `RiftGenerator.onJumpDone`. |
| `indevo/industries/derelicts/renderers/RiftWarpRenderer.java` | Transient LunaLib renderer: whirl, planet shrink, flare, warp sounds. Draws only in its own location. |
| `indevo/industries/derelicts/dialogue/RiftTargetMap.java` | Area line for the mode tooltips and the mode page, vanilla sector map (`createSectorMap`) for the mode tooltips. |
| `indevo/industries/derelicts/dialogue/RiftPrecisePicker.java` | Vanilla map picker for Precise targeting. Stores the pick in local memory and fires `IndEvo_RiftPrecisePicked`. |
| `indevo/industries/derelicts/dialogue/RiftPreciseConfirmDialog.java` | `RelicConfirmDialog` for Precise: takes the relics, fires, then fires `IndEvo_RiftPreciseFired`. |
| `indevo/industries/derelicts/hullmods/PrintDefectsLow/Med/High.java` | Print d-mods. |
| `indevo/industries/derelicts/hullmods/PrintingHullMod.java` | `IndEvo_auto` indicator; raises S-mod cap by the `IndEvo_compN` tag. |
| `indevo/industries/derelicts/listeners/AncientLabCommoditySwitchOptionProvider.java` | Industry option "change bonus commodity" on the lab. |
| `indevo/industries/derelicts/plugins/AncientLabRelicOptionProvider.java` | Industry option "Relic services..." on a functional player owned lab, also on remote colony screens. Opens the relic panel. |
| `indevo/industries/derelicts/dialogue/AncientLabRelicPanelDelegate.java` | Relic services panel (`BaseCustomDialogDelegate` on the [UI kit](../core/UI.md)): Templates and Ships tabs as list and detail, cost, payment, result line, `?` with the rules and rates. Static `show(market, view)` opens it in its own interaction dialog (nested `Host`). |
| `indevo/industries/derelicts/dialogue/AncientLabRelicView.java` | View state: tab (`ships`), `selected` row key (`FIRST` picks the first row, null shows nothing picked), `message` (result line), scroll offsets. |
| `indevo/industries/derelicts/listeners/PlanetJumpListener.java` | Interface fired by `RiftJumpScript` after a jump. No implementers. |
| `indevo/industries/derelicts/listeners/RiftTearManager.java` | Persistent `NewDayListener`: open tears, daily name countdown, closes a tear when its days run out. |
| `indevo/industries/derelicts/plugins/RiftGenOptionProvider.java` | Industry option that opens the rift rules dialog, only while `getBlockReason()` is null. |
| `indevo/industries/derelicts/plugins/RemoveIndustryOptionWarningProvider.java` | Confirm dialog before removing any industry tagged `IndEvo_showDeconstructWarning`. |
| `indevo/industries/derelicts/rules/IndEvo_RiftGen.java` | Rule command, verbs `gate <optionId>`, `setupTargetMenu`, `showArea <MODE>`, `fire <MODE>`, `pickTarget`, `showPick`, `confirmPrecise`. |
| `indevo/industries/derelicts/utils/RuinsManager.java` | Upgrade roll, sector placement, core world cleanup. |
| `indevo/industries/derelicts/utils/IndustrySpecView.java` | `IndustrySpecAPI` that delegates to the shared spec but returns its own `getUpgrade`, `getDowngrade` and their plugin instances. `of(cached, spec, upgrade, downgrade)` reuses the cached view while it still matches. Returned by `Ruins.getSpec()` (`Ruins.java:28`), `BaseForgeTemplateUser.getSpec()` (`:52`) and `RiftGenerator.getSpec()` (`:45`). |
| `indevo/industries/derelicts/utils/RuinsCountMap.java` | Deprecated, unused. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Ruins`, `IndEvo_ResLab`, `IndEvo_HullDecon`, `IndEvo_HullForge`, `IndEvo_RiftGen` | `data/campaign/industries.csv:93-97`. All downgrade to `IndEvo_Ruins`, tags `do_not_show_in_build_dialog`, `IndEvo_showDeconstructWarning`. |
| Condition | `IndEvo_RuinsCondition` | `data/campaign/market_conditions.csv:20`, `data/campaign/procgen/condition_gen_data.csv:2` (group `industry`, freq 5) |
| Condition | `IndEvo_CryoRevivalDisabler` (hidden) | `data/campaign/market_conditions.csv:27` |
| Hullmod | `IndEvo_print_low`, `IndEvo_print_med`, `IndEvo_print_high` (dmod) | `data/hullmods/hull_mods.csv:2,5,8` |
| Hullmod | `IndEvo_auto` (`Ids.PRINTING_INDICATOR`) | `data/hullmods/hull_mods.csv:11` |
| Jump point | id `IndEvo_planet_replacer_<uid>` (rifts from before 4.2.a: `IndEvo_planet_replacer`), tag `IndEvo_artificialRift`, desc `IndEvo_JumpRiftTemporary` (permanent tears from before 4.2.a: `IndEvo_JumpRift`); the jump point dialog prints only text3 (skill `api/impl/campaign/JumpPointInteractionDialogPluginImpl.java:234-236`), so both rows carry the text in text1 and text3 | `RiftRelocator.java:26-29`, `data/strings/descriptions.csv:3-4` |
| Variant tag | `IndEvo_comp1/2/3` | `HullForge.java:422-424` |
| Variant tag | `IndEvo_hasInbuiltLogMod` | `HullDeconstructor.java:55` |
| Memory key | `$IndEvo_ruinsIndustryId` (market), `$IndEvo_ruinsPlaced_<marketId>` | `Ruins.java:17`, `RuinsManager` |
| Variant tag | `no_auto_penalty` (vanilla `Tags.TAG_AUTOMATED_NO_PENALTY`), added by the Ships tab | `AncientLabRelicPanelDelegate.integrate` |
| Memory key | `$IndEvo_LabBonusCommodityID` and `_1/_2/_3`, `$IndEvo_ruinsPickerSeed` (sector) | `AncientLaboratory.java:37` |
| Memory key | `"$" + industryId` sector flag (template tooltips) | `BaseForgeTemplateUser.java:73-76` |
| Memory key | `$IndEvo_original_planet_orbit` (planet, orbit radius before the first jump), `$IndEvo_closeDialogueOnNextReturn` | `RiftRelocator.getOriginalOrbit`, rift rules |
| Memory key | `$IndEvo_riftBlocked` (any entity, true blocks the jump; for other mods), `$IndEvo_riftPreciseTarget` (local, picked system id) | `RiftBlockers.java:12`, `RiftPrecisePicker` |
| Tag | `IndEvo_noRiftTarget` on a star system: never a target | `RiftTargets.java:21` |
| Setting | `IndEvo_Enable_Derelicts` | `data/config/LunaSettings.csv:7` |
| Setting | `IndEvo_derelictDeliverToGathering`, `IndEvo_RG_cooldownTime` (90), `IndEvo_RG_range` (9), `IndEvo_RG_preciseCost` (100), `IndEvo_reslab_autoDeliverToClosestDecon` | `data/config/LunaSettings.csv:156-160` |
| Setting | `IndEvo_hullDecon_autoDeliverToClosestForge`, `IndEvo_hullDecon_days_*` (10/20/30/40) | `data/config/LunaSettings.csv:153-157` |
| Setting | `IndEvo_hullForge_autoQueueShipsUntilEmpty`, `IndEvo_hullForge_autoDeliverToClosestLab`, `IndEvo_hullForge_days_*` (5/10/15/20) | `data/config/LunaSettings.csv:158-163` |
| Setting | `IndEvo_ToggleMagicRandomSelector` (settings.json only) | `data/config/settings.json:28`, read at `HullForge.java:278,444` |
| Config | `IndEvo_labList` -> `data/config/indEvo/lab_commodity_whitelist.csv` | `data/config/settings.json:19` |
| Config | `IndEvo_riftBlockedSystemsList` -> `data/config/indEvo/rift_blocked_systems.csv` (system ids, never a target), `IndEvo_riftBlockingEntitiesList` -> `data/config/indEvo/rift_blocking_entities.csv` (custom entity types that block in orbit: `bifrost_gate`, `aotd_pluto_station`) | `data/config/settings.json:25-26` |
| Rules | `IndEvo_RiftGenTargetOptions`, `IndEvo_RiftTarget_*`, `IndEvo_RiftPrecisePicked`, `IndEvo_RiftPreciseFired`, `IndEvo_FireBaseRGOptionList`, `IndEvo_RiftControlMenuPoint*` | `data/campaign/rules.csv:756-817` |

Forge template item ids (`IndEvo_ForgeTemplate`, `IndEvo_DegForgeTemplate`, `IndEvo_EmptForgeTemplate`) live in [Installables](../items/INSTALLABLES.md). Settings mechanics: [Settings](../core/SETTINGS.md).

## Registration and lifecycle

- `ModPlugin.onNewGameAfterEconomyLoad`: `RuinsManager.forceCleanCoreRuins()` (`indevo/utils/ModPlugin.java:290`) -> `RuinsManager.placeIndustrialRuins()` (`:305`).
- `ModPlugin.onGameLoad`, transient listeners re-added each load: `DerelictRuinsPlacer` (PlayerColonizationListener, `:451`), `ResolveRuinsToUpgradeListener` (SurveyPlanetListener and ColonyInteractionListener, `:452`), `AncientLabCommoditySwitchOptionProvider.register()` (`:470`), `AncientLabRelicOptionProvider.register()` (`:471`), `RiftGenOptionProvider.register()` (`:491`), `RemoveIndustryOptionWarningProvider.register()` (`:503`). `loadTransientMemory` (`:418`) preloads the two rift lists.
- Each industry adds itself as a transient listener in `apply()` and removes itself in `unapply()`. `onNewDay` comes from IE's `NewDayListener`, driven by `indevo/utils/timers/TimeTracker.java:127`. See [Utils](../core/UTILS.md).
- Rule commands: package `indevo.industries.derelicts.rules` listed in `data/config/settings.json:35`. `IndEvo_requiresColonyMenuPoint.java:32` also checks `IndEvo_RiftGen`; see [Rule commands](../dialogue/RULE_COMMANDS.md).
- `RiftTearManager.getInstanceOrRegister()` (`RiftTearManager.java:32`): persistent listener, added on the first jump by `RiftRelocator.relocate`.
- `RiftJumpScript` is persistent (`Global.getSector().addScript` in `RiftGenerator.fire`, `:137`). `RiftWarpRenderer` is a transient LunaLib renderer (`addTransientRenderer`); after a load during the warp the script starts it again.
- Console command `SpawnRuins`: see [Console](../core/CONSOLE.md).

## Flow

### Ruins placement and upgrade pick

`placeIndustrialRuins` (`RuinsManager.java:104-169`) -> for uninhabited remnant systems only: chance 0.1, suppressed 0.3, resurgent 0.5; second planet x0.7; max 2 per system; tag `IndEvo_RuinsAlways` forces it. Removes generated conditions elsewhere. `seedRuinsIfNeeded` adds `ruins_scattered`.

`ResolveRuinsToUpgradeListener` -> `RuinsManager.setUpgradeSpec` if `$IndEvo_ruinsIndustryId` is missing, on survey and when the player opens any planet dialog (`reportPlayerOpenedMarket`, fired for uncolonized planets too, skill `obf/campaign/CampaignState.java:2272`). The dialog roll covers survey data items and other `Misc.setFullySurveyed` paths, which fire no survey listener; the condition tooltip shows the scan line only with a target and a full survey (`RuinsCondition.java:102`).

`RuinsCondition.apply` (`RuinsCondition.java:23`) -> on a real non neutral colony: `RuinsManager.setUpgradeSpec` if `$IndEvo_ruinsIndustryId` missing -> add `IndEvo_Ruins` once.

`setUpgradeSpec` (`RuinsManager.java:197-233`): weights Lab 1, Decon 0.7, Forge 0.7, RiftGen 0.35, seeded by the planet salvage seed. RiftGen is excluded with solar array, artillery condition, minefield, Niko satellites, rings, gas giant. Every target another planet in the same system already holds in `$IndEvo_ruinsIndustryId` is removed from the picker (`:217-222`), so two ruins in one system never share a target, whichever rolls first. An empty picker falls back to Lab (`:228`); new game seeds at most two ruins per system, so it never empties there. A Rift Generator jump does not re-check: a planet moved into a system can share its target with ruins there.

`Ruins.getSpec()` (`Ruins.java:28-32`) returns an `IndustrySpecView` whose upgrade is `$IndEvo_ruinsIndustryId`, so the option menu, `startUpgrading` and the tooltips read this market's target. `Ruins.apply` (`:22`, no `super.apply`) -> `RuinsManager.setUpgradeSpec` if the market has no target yet (ruins added by `DerelictRuinsPlacer` on a planet whose condition came after new game and was surveyed without the survey listener, for example through `SurveyDataSpecial`) -> `triggerUpgradeForAI` (`:67-83`): NPC market size > 4, free slot, more than 2 markets, not already upgrading (`apply` runs on every economy step, and `startUpgrading` resets the progress). `BaseForgeTemplateUser.isAvailableToBuild` (`:40`) only lets the matching industry build, and not while ruins are upgrading. The CSV downgrade to `IndEvo_Ruins` only keeps the four structures out of the build list (skill `obf/campaign/ui/marketinfo/IndustryPickerDialog.java:182`); `BaseForgeTemplateUser.getSpec()` (`:52`) and `RiftGenerator.getSpec()` (`:45`) return a view without it, so the menu never offers "Downgrade to Old Industrial Ruins".

### Forge template loop

- Deconstructor: ship put into `IndEvo_DeconstStorage` (added in `advance`, `HullDeconstructor.java:72`) -> stripped, N days by hull size -> forge template with 2 or 3 charges (+1 beta), quality base 1, alpha/beta 2, gamma 0. Alpha adds a built in logistics hullmod (`addBuiltInLogisticsHullmods`, `:149`). Gamma: upkeep x0.7 while idle. Optional auto delivery to the closest forge.
- Forge: one ship per charge into storage. Cost 20% of base value, x0.7 with beta, booked on the "Hull printing" report line ([BillingHelper](../core/UTILS.md#billinghelper)). D-mods via `ForgeTemplateItemPlugin.addPrintDefectDMods` (template quality + forge quality). Alpha: 2 built in hullmods plus `IndEvo_compN` tag (`compensateInbuilts`, `:426`). Spent template: degraded, or empty with 0.6 chance on beta (`EMPTY_FORGE_TEMPLATE_CHANCE`, `:82`).
- Lab: degraded template -> empty template, 20 days, 10 with gamma (`initRepair`, `AncientLaboratory.java:113`). Commodity bonus +2 on existing supply of a whitelist commodity; beta +3 on one, alpha +1 on three (`getBonusValue :129`, `setAllCommodityBonuses :140`).
- Relic speed up: `HullDeconstructor` and `AncientLaboratory` (both Accelerate) are `RelicSpeedUpTarget`s. The industry option halves the days left (adds to `daysPassed`) for 1 Relic Component per remaining day, once per 60 days per industry. See [Economy](../core/ECONOMY.md#relic-speed-up).
- All three autofeed from the shared storage and deliver to the gathering point when `IndEvo_derelictDeliverToGathering` is on (inferred: `repairInstalledFT :270` pattern repeats in decon and forge).

### Ancient Laboratory relic services

"Relic services..." -> `AncientLabRelicPanelDelegate.show(ui, lab, new AncientLabRelicView())` -> `DialogCreatorUI.showDialog`, so it works on the remote colony screen. Both tabs are instant on payment. Relic Components come from the lab colony's storage first, then fleet cargo (`RelicHelper.take`).

Panel (board 11 and 12, 1004 x 560 or less on small screens): title with `?` (rules and rates per tab), tabs Templates and Ships, the Relic Components stock right. Left: heading, table header, scrolling rows (48 high) and one result line at the bottom (`view.message`, green, set after Refine or Integrate, cleared on a click). Right: a scrolling detail column (`AncientLabRelicView.DETAIL`): heading, hull picture, the effect as before and after values; pinned and never scrolling, the cost row and the action button (`UiButtons.relic`: green, relic icon and cost) with the lore text (`REFINE_LORE`, `INTEGRATE_LORE`) as tooltip. When Relic Components do not cover the cost the button is grey with "Not enough Relic Components." below. An empty list keeps its heading and one grey line; with no row picked the detail says "Pick a Forge Template to refine." or "Pick a ship to integrate.". The panel opens with nothing picked.

- Template rows: template icon, hull sprite, hull name and size, quality level as pips and text, stack size, cost with the relic icon (red when short). Detail: quality level before and after as pips, the print defect the printed hull gets before and after (`getDefectId`, same thresholds as `ForgeTemplateItemPlugin.addPrintDefectDMods`), templates in the stack, charges, where it is stored. Button Refine.
- Ship rows: ship sprite, ship name, hull and size, location (Fleet or storage), the max CR penalty, cost. Detail: max CR and Automated Ships limit as before and after with the skill icon, "Permanent". Button Integrate.

- Templates tab: forge templates in `IndEvo_SharedStore` and local storage with a payload variant and quality level above 0. Refine runs `ForgeTemplateItemPlugin.incrementForgeTemplateQualityLevel(data, -1)` on the payload, so every stack sharing that payload key changes. Cost `RELICS_PER_TEMPLATE_DP` (2) x hull DP x stack size, per level. Templates without a payload (drops, salvage) are always level 0 and never show.
- Ships tab: fleet members and local storage ships where `Misc.isAutomated` and not `Automated.isAutomatedNoPenalty`. Integrate clones the variant, clears the original variant, gives it a new id, adds `no_auto_penalty` and sets it with `member.setVariant(variant, false, true)`. Vanilla then skips the -100% max CR (skill `api/impl/hullmods/Automated.java:20`) and the Automated Ships skill point count (`BaseSkillEffectDescription.java:792`). Cost `RELICS_PER_SHIP_DP` (10) x hull DP (`getSuppliesToRecover`).

### Rift Generator move

Menu: colony menu "Rift Generator Control" (`IndEvo_RiftControlMenuPoint`, `IndEvo_RiftControlMenuPoint2`) or the industry option (`RiftGenOptionProvider`). `IndEvo_RiftGen gate` greys the menu option with the first reason of `RiftGenerator.getBlockReason` (`:103`): jumping, cooldown with days left, `RiftBlockers.getReason` (`RiftBlockers.java:20`), no eligible system in the widest reach. Cooldown `IndEvo_RG_cooldownTime` days counted in `onNewDay` (`:77`), alpha -30, ready at 0 days left (always in dev mode). Recharge: `RiftGenerator` is an instant `RelicSpeedUpTarget`; the industry option ends the cooldown for 3 Relic Components per remaining day, once per 60 days, not while jumping. See [Economy](../core/ECONOMY.md#relic-speed-up). `advance` adds `IndEvo_CryoRevivalDisabler`.

Target menu (`IndEvo_RiftGenTargetOptions`): the four free modes, Precise (`5:IndEvo_RiftTarget_PRECISE_pick`) and Return. `IndEvo_RiftTarget_setup` comes last, so its `setupTargetMenu` (`IndEvo_RiftGen.java:87`) runs after every option exists: COLONY needs another colony system of the faction, a mode without an area is grey with its reason, every other mode gets an option tooltip with the area line ("Target area: around the X." or "anywhere within N light years.") and the sector map: the area systems at full alpha with a highlight marker each, the planet's marker in its faction colour, an arrow from the planet's system to the mean position of the area (none for RANDOM). Precise is green with range, cost line and lore as tooltip; grey without targets or with too few relics.

Areas (`RiftTargets.getArea`, `:32`): candidates are the eligible systems within `IndEvo_RG_range` (+3 beta) times the mode's reach multiplier. RANDOM takes all of them. CORE aims at the mean position of `theme_core_populated` systems (`getCoreCenter`, `:85`), FRINGE straight away from it at full reach, COLONY at the closest other system with a colony of the market's faction (Nexerelin outposts skipped, `:127`); an aim beyond reach is pulled back to reach. The area is the candidates sorted by distance to the aim: at least 3, plus every one within 3 LY; the closest is the anchor named in the text. The jump picks one at random.

Eligible (`isEligible`, `:73`): not the own system, no `theme_hidden`, `system_cut_off_from_hyper`, `system_abyssal` or `IndEvo_noRiftTarget` tag, not deep space or in the abyss, has a hyperspace anchor, has a star or is a nebula, not in `rift_blocked_systems.csv`. An open or old tear in a system does not block it.

Mode page: the panel text, then `showArea` adds the area line (no map) to the text panel. Initiate: `fire <MODE>` -> `RiftGenerator.fire(mode, random)` (`:122`).

Precise: `pickTarget` -> `dialog.showCampaignEntityPicker` with the hyperspace anchors of the eligible systems within base range (no multiplier), info line with distance and cost -> the pick goes into `$IndEvo_riftPreciseTarget` -> `IndEvo_RiftPrecisePicked` page (lock line, Initiate in green from `showPick`, Choose another system, Return) -> Initiate -> `RiftPreciseConfirmDialog` with `IndEvo_RG_preciseCost` (relics from colony storage, then fleet cargo) -> Confirm takes the relics, `fire(system)`, `IndEvo_RiftPreciseFired` (fire text, Leave). Cancel keeps the page. Cost 0 skips the popup.

`RiftGenerator.fire(system)` (`:128`) throws when blocked or the system is not eligible, else sets `jumping`, resets the cooldown, adds `RecentUnrest` +1 and `RiftJumpScript` with the tear lifetime (`getTearDays`, `:154`: the recharge time, at least 1 day, gamma x2).

`RiftJumpScript.advance` (`RiftJumpScript.java:40`): waits 5 s. With the planet in the player's location it starts `RiftWarpRenderer` and scales the planet radius with it (whirl grows 3 s, holds 1 s, planet and whirl shrink 0.5 s, flare 1 s); it jumps when the warp collapses (4.6 s) or 10 s later at most. Elsewhere it jumps at once. `jump` (`:63`) restores the radius, skips the jump if the market left the economy, then `RiftRelocator.relocate`.

`RiftRelocator.relocate` (`RiftRelocator.java:45`): collects orbiters transitively (`collect`, `:162`; orbit focus, ring band focus, ring terrain related entity). Planets, jump points, entities of another market and the player fleet stay (`staysBehind`, `:176`); stations, the station fleet, rings, magnetic fields, mirrors, shades, radio chatter, orbital junk and custom entities move. Creates the tear jump point with a magnetic field on the planet's orbit (`createRift`, `:189`), points hyperspace jump points that led to the planet at the tear (`redirectHyperspaceJumpPoints`, `:226`), puts the stayers in orbit around the tear (the player fleet just loses its orbit). Then the market leaves the economy, the planet and movers go `removeEntity` / `addEntity` into the target, the planet gets its new orbit around the target centre, direct orbiters that shared the planet's old period get the new one with their orbit type kept (`syncPeriod`, `:249`; vanilla mirrors and shades point down at the planet's period, `MiscellaneousThemeGenerator.java:471`), the market comes back (`addMarket(market, false)`: no second junk or chatter), the tear gets the planet as destination and opens in `RiftTearManager`. Orbit (`findFreeOrbit`, `:277`): starts at the orbit before the first jump (`$IndEvo_original_planet_orbit`) or star radius x 3 + 700, whichever is larger, then one sweep outwards past the bands of the centre's direct orbiters (their own orbiters, ring bands and ring terrain included), keeping the planet's own extent + 200 free. The period scales with the radius, else radius / 20.

Tear lifetime (`RiftTearManager`): `open` stores the tear, the redirected hyperspace jump points and the days left. `onNewDay` (`:54`) counts one day down, like the recharge, and renames the tear and its hyperspace jump points "Artificial Hyperspace Tear (closes in N days)"; at 0 days left `RiftRelocator.closeTear` (`RiftRelocator.java:89`) removes the tear from the hyperspace jump points (a jump point left without destinations is removed), removes the tear's magnetic field, puts every other orbiter of the tear in a circular orbit around the system centre at its current distance and angle, removes the tear, and the manager posts "The Artificial Hyperspace Tear in the X system has closed." Tears from before 4.2.a are not in the manager and stay open.

## Contracts

- Relic service help states the minimum template quality level and the permanent CR and skill-limit effects of ship integration.

- `BaseIndustry.spec` is the shared `Global.getSettings().getIndustrySpec(id)` (skill `api/impl/campaign/econ/impl/BaseIndustry.java:160,702`); never write per market values onto it. `Ruins` overrides `getSpec()` with a view instead; every vanilla reader of the upgrade goes through `getSpec()` (the industry option menu, obfuscated `b` in `com.fs.starfarer.campaign.ui.marketinfo` on 0.98a-RC8 Windows, skill `obf/campaign/ui/marketinfo/_c37.java:224,259`; `BaseIndustry.java:575,1075`). Forge template users and `RiftGenerator` do the same for the downgrade.
- `$IndEvo_ruinsIndustryId` is the single source for which structure a ruin becomes. Set once, persists in market memory. Also read by `Supercomputer.java:205`, `ModUpdater.java:96-141`, `LocatorSystemRatingUpdater.java:121`.
- The relic panel only changes the template payload tag and ship variant tags; it saves nothing of its own.
- Forge templates move between industries only as cargo through `IndEvo_SharedStore` / `IndEvo_DeconstStorage` and storage. Item plugins own the template data, charges and quality ([Installables](../items/INSTALLABLES.md#forge-templates)). Each template id lists only the structure that uses it in its params, and the Forge and the Deconstructor hide the slot while working.
- A rift jump keeps the same `PlanetAPI`, market and orbiter objects: memory, tags, conditions and industries stay. `BaseLocation.addObject` sets the new containing location, `BaseLocation.advance` resets the light source each frame and `Economy.advance` rebuilds the market location cache each frame (skill `obf/campaign/BaseLocation.java:263,458-482`, `obf/campaign/econ/reach/ReachEconomy.java:105`). Code that caches the planet's `StarSystemAPI` keeps the old one; AOTD megastructures and the Bifrost gate do, so `RiftBlockers` refuses those planets. `PlanetJumpListener` fires after each jump and has no users.
- `RiftGenerator.fire` throws on a blocked generator or an ineligible system: the rules grey every option that would get there. The free mode area is computed for the tooltip and again on fire from the same state, so the jump lands in the area the map showed.
- Block reasons come from `getBlockReason` only; the colony menu tooltip, the industry tooltip and the industry option all read it.
- Transient listeners in `onGameLoad` have no class dedupe; `ListenerManager.addListener` dedupes only the same instance (`obf/campaign/ListenerManager.java:29`). Fine since transient lists are empty at load.
- `RiftWarpRenderer` is transient and holds no saved state; `RiftJumpScript` keeps the original planet radius, so a save during the shrink restores it on the jump.

## Known issues and dormant code

| Item | State |
|---|---|
| `industry/WIP/` (3 classes, `IndEvo_Lighthouse` = `Ids.BEACON`) | Excluded by `IndEvo.iml:11`, no csv rows. |
| `BeaconCondition` | Dead. Static `STABILITY_PENALTY` shared across instances. |
| `RuinsCountMap` | Deprecated, unused. |
| `PlanetJumpListener` | No implementers. |
| `AncientLaboratory.java:263-275` | Null gathering point returns false, retried daily. |
| `HullForge.java:283` | NPE if the target market has no storage submarket. |
| Gas giant jump | The planet gets no gravity well in the new system (inferred, autogen would duplicate every jump point there). |
| Several open tears | A gamma core (tear x2) or a relic Recharge lets one Generator fire again while its last tear is open. |
| Changelog_todo | "Change derelict industries to superstructures" (`:140`). The rift warp sounds from "riftgen anim sounds" (`:55,82`) now play. |

## Maintenance

Update this doc when an industry in `indevo/industries/derelicts` changes its cost, cores, timers or delivery, when the ruins placement or weights change, or when the rift targeting, blocks or jump steps change. Forge template item rules go in [Installables](../items/INSTALLABLES.md), storage submarkets in [Submarkets](../core/SUBMARKETS.md), the artillery condition in [Artillery](ARTILLERY.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
