# Economy

IE's global economy hooks in `indevo/economy/`: the hidden resource condition on every market (ship parts supply and demand, Supercomputer income), the relic speed up for construction and IE timers, Express Delivery on the vanilla custom production contract, relic payment for the refit build in, and the relic boost for vanilla Tech Mining. Read it before changing ship parts, Supercomputer income, construction time, a relic sink or the build in patch. It does not cover the industries that produce the inputs; see [Salvage Yards](../industries/SALVAGE_YARDS.md) and [Small industries](../industries/SMALL_INDUSTRIES.md) (Supercomputer).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Every market in the economy gets the hidden condition `IndEvo_ressCond` (`ResourceCondition`). Its `apply` adds Starship Components (`IndEvo_parts`) supply and demand to heavy industries and applies the distance based income bonus of the nearest Supercomputer. Separately, an industry option "Speed up construction..." halves the remaining build time of any building or upgrading industry for Relic Components (`IndEvo_rare_parts`), once per 60 days per colony. The same relic speed up halves IE timers (Engineering Hub, Hull Deconstructor, Ancient Laboratory, Academy training, Salvage Yards orders) or ends the Rift Generator cooldown, once per 60 days per timer. Express Delivery halves the delivery time of a vanilla custom production order for Relic Components. In the refit build in mode a "Use Relics" toggle lets new S-mods be paid with Relic Components instead of story points. The industry option "Boost Extraction" on vanilla Tech Mining doubles the next monthly find for Relic Components.

## Files

| File | Owner / role |
|---|---|
| `indevo/economy/conditions/ResourceCondition.java` | Condition plugin: `applyParts()`, `applySupComIncome()`; static `applyRessourceCond(market)` adds the condition to markets in the economy |
| `indevo/economy/listeners/ResourceConditionApplicator.java` | `PlayerColonizationListener` and `EconomyTickListener`: adds the condition on colonization and on every economy tick to all markets |
| `indevo/economy/listeners/RelicBuildTimeReductionButtonAdder.java` | `BaseSimpleBaseIndustryOptionProvider`: the construction speed up option; nested `ConstructionTarget` adapts a building `BaseIndustry` |
| `indevo/economy/RelicSpeedUpTarget.java` | Interface for one timer: days left and total, `speedUp(days)`, label, lore; defaults for cost, days after, cooldown and the constants |
| `indevo/economy/listeners/RelicSpeedUpOptionProvider.java` | `BaseSimpleBaseIndustryOptionProvider`: the speed up option on any player owned industry that implements `RelicSpeedUpTarget` and `canSpeedUp()` |
| `indevo/economy/plugins/RelicConfirmDialog.java` | Shared relic confirm dialog (`CustomDialogDelegate`), laid out like the vanilla story point dialog: requirement line, description, relic counter left of the buttons; static `addRequirement`, `addCounter` |
| `indevo/economy/plugins/RelicSpeedUpDialog.java` | `RelicConfirmDialog` for every relic speed up, plus static `addBody`, `addTooltip`, `isAvailable`, `apply` |
| `indevo/dialogue/rules/IndEvo_CPCExpress.java` | Rule command for Express Delivery: `valid`, `options`, `apply` |
| `data/campaign/rules.csv` block `# Custom production express delivery` | Express Delivery offer and the two accept paths |
| `indevo/economy/patches/RelicBuildInPatch.java` | PatchLib patch on `ModPickerDialogV3`: adds the toggle, redirects the story point check, message and payment; nested `Toggle` holds the state and pays |
| `indevo/economy/listeners/TechMiningRelicOptionProvider.java` | `BaseSimpleBaseIndustryOptionProvider` and `EconomyTickListener`: Boost Extraction option, dialog, industry tooltip line, removal at month end |
| `indevo/economy/patches/RelicBuildInCounterPatch.java` | PatchLib patch on `ModPickerDialogV3$PermaModInfoWidget`: counter and bonus experience readout while the toggle is on |
| `indevo/economy/patches/RelicBuildInListPatch.java` | PatchLib patch on `ModPickerDialogV3$PermaModInfoWidget`: replaces the effect list with the relic list while the toggle is on |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Condition | `IndEvo_ressCond` (`Ids.COND_RESSOURCES`), tags `hidden, technical` | `data/campaign/market_conditions.csv:28` |
| Commodity | `IndEvo_parts` (`ItemIds.PARTS`), `IndEvo_rare_parts` (`ItemIds.RARE_PARTS`) | `data/campaign/commodities.csv:2`, `:3` |
| Setting | `IndEvo_ScrapYard` (parts on/off) | `data/config/LunaSettings.csv:97` |
| Memory key | `$IndEvo_reduceBuildTimeCooldown` (market memory, expires after `COOLDOWN_DAYS` 60) | `RelicBuildTimeReductionButtonAdder` |
| Option ids | `IndEvo_cpcExpress` (`IndEvo_CPCExpress.OPTION_EXPRESS`), `IndEvo_cpcStandard` | rules, `IndEvo_CPCExpress` |
| Rules | `IndEvo_cpcExpressOffer` (trigger `CPCBlueprintsPicked`, score 100), `IndEvo_cpcStandard`, `IndEvo_cpcStandardBar`, `IndEvo_cpcExpress`, `IndEvo_cpcExpressBar` | `data/campaign/rules.csv:856` |
| Memory key | `$IndEvo_relicSpeedUp_<getSpeedUpId()>` (market memory, expires after `COOLDOWN_DAYS` 60): industry id, `<academy id>_officer`, `<academy id>_admin` | `RelicSpeedUpTarget` |
| Memory key | `$IndEvo_relicTechMining` (market memory, `true` while a Tech Mining boost is pending) | `TechMiningRelicOptionProvider.MEM_BOOST` |
| Modifier id | `IndEvo_relicTechMining`, mult `FIND_MULT` 2 on `Stats.TECH_MINING_MULT` | `TechMiningRelicOptionProvider.MOD_ID` |
| Modifier ids | condition id (`getModId()`) on `market.getIncomeMult()`; condition id on `Stats.PRODUCTION_QUALITY_MOD` | `ResourceCondition` |

## Registration and lifecycle

- `ModPlugin.onGameLoad` -> `ResourceConditionApplicator.applyResourceConditionToAllMarkets()` (`indevo/utils/ModPlugin.java:165`) and `ResourceConditionApplicator.register()` (`:434`, transient listener).
- Economy tick -> `applyResourceConditionToAllMarkets()` -> `ResourceCondition.applyRessourceCond(m)` adds the condition if `m.isInEconomy()` and missing. The condition itself persists in the market.
- `RelicBuildTimeReductionButtonAdder.register()` and `RelicSpeedUpOptionProvider.register()` (`indevo/utils/ModPlugin.java:492`, `:496`) add transient listeners.
- `TechMiningRelicOptionProvider.register()` (`indevo/utils/ModPlugin.java:497`) adds a transient listener; one instance is both the option provider and the `EconomyTickListener`.
- `RelicBuildInPatch` and `RelicBuildInCounterPatch` need no registration; PatchLib installs them when the refit build in dialog first loads. The toggle state is transient, nothing is saved.

## Flow

### Ship parts

`ResourceCondition.apply` -> if `IndEvo_ScrapYard` -> `applyParts()`. Per industry:

| Industry | Parts supply | Parts demand |
|---|---|---|
| `instanceof HeavyIndustry` (vanilla `heavyindustry` and `orbitalworks`, both use that plugin in vanilla `data/campaign/industries.csv`) | `min(size - 3, 3)` | `size` |
| Shadowyards `MS_modularFac` / `MS_fabUpgrader`, only if `shadow_ships` enabled | `MODULARFACTORIES` size-3, `PARALLEL_PRODUCTION` size-2 | `MILITARY_LINES` size-4, `SHIPYARDS` size-2 |
| Other industry tagged `heavyindustry` that supplies ships and no parts | `min(machinery - 2, 4)` | ships supply + 2 |

- Supply: Corrupted Nanoforge -1, Pristine -3; metals or rare metals deficit cuts parts supply (`applyPartsSupply`).
- Demand: Corrupted -1, Pristine -2. A parts shortfall gives a production quality penalty `deficit / demand * maxPenalty` (-0.5, -0.4 corrupted, -0.3 pristine) and cuts ship output by at most 2 (`applyPartsDemands`, `ResourceCondition.java:152`).

### Supercomputer income

If no market of the same faction in the location has `IndEvo_SupCom`: income mult `1 + Supercomputer.getDistanceIncomeMult(locInHyper)` labelled with the nearest Supercomputer market (`ResourceCondition.java:39`). Distance math is owned by `Supercomputer`; see [Small industries](../industries/SMALL_INDUSTRIES.md).

### Relic speed up

Shared by construction and the IE timers. Constants on `RelicSpeedUpTarget`: `RELICS_PER_DAY` 1, `RELICS_PER_DAY_INSTANT` 3, `COOLDOWN_DAYS` 60.

| Timer | Label | Target | Entry point |
|---|---|---|---|
| Construction or upgrade | Speed up construction | `RelicBuildTimeReductionButtonAdder.ConstructionTarget` | Industry option |
| Engineering Hub reverse engineering | Accelerate Research | `EngineeringHub` | Industry option |
| Hull Deconstructor | Accelerate | `HullDeconstructor` | Industry option |
| Ancient Laboratory template repair | Accelerate | `AncientLaboratory` | Industry option |
| Rift Generator cooldown | Recharge | `RiftGenerator`, `isSpeedUpInstant()` | Industry option |
| Academy officer and admin training | Accelerate | `Academy.TrainingSpeedUp` (`getTrainingSpeedUp(admin)`) | One click button in the trainee detail of the Academy panel (`RelicSpeedUpDialog.apply`, no dialog), see [Academy](../industries/ACADEMY.md) |
| Salvage Yards order delivery | Accelerate Construction | `YardsCustomProductionIntel` | Intel button with a confirm prompt and an option in the ship assembly dialog, see [Salvage Yards](../industries/SALVAGE_YARDS.md) |

- Halving: days after `left - left / 2`, cost `left * RELICS_PER_DAY`. Instant (Rift Generator): days after 0, cost `left * RELICS_PER_DAY_INSTANT`.
- `canSpeedUp()` needs more than 1 day left; the industry option only shows then. `RelicSpeedUpDialog.isAvailable` adds the cooldown check, the option or button is disabled while it runs.
- `RelicSpeedUpDialog.apply` checks availability and relics again, takes the cost through `RelicHelper.take(getMarket(), cost)` (colony storage, then fleet cargo, [Utils](UTILS.md#helper)), calls `speedUp(left - after)` and starts the cooldown.
- Cooldowns: market memory per timer by default (`getSpeedUpCooldownLeft`, `startSpeedUpCooldown`). Construction keeps `$IndEvo_reduceBuildTimeCooldown` (per colony, "on this colony"). Salvage Yards orders keep a clock timestamp on the intel (`relicSpeedUpTimestamp`, "on this order"). Default scope text "here".
- Dialog: `RelicConfirmDialog` sizes itself to its text (`getHeight`) and draws the counter "Relic Components: N" below its own panel, left of the dialog buttons. Without enough relics the only button is Return; Confirm checks the relics again before `apply()`. Subclasses pay in `apply()`.
- Text: the dialog writes the requirement line, the effect with time remaining before and after, and the relic counter. `addBody` writes the same for the intel confirm prompt. `addTooltip` writes effect, cost line, the cooldown rule (`getSpeedUpScope()`), the cooldown left or "Too few days left" and the gray lore (`getSpeedUpLore()`, Philipp's text, RELIC_PLAN section 6; construction has none) for the option or button hover.
- Construction: `ConstructionTarget` reads private `buildTime` via `StarReflectionUtils.get(ind, "buildTime", float.class, true)`; days left `round(buildTime - buildProgress)`; `speedUp` adds the days to `BaseIndustry.setBuildProgress`. `isSuitable`: a `BaseIndustry` that is building, or upgrading with an upgrade spec.

### Custom production Express Delivery

Vanilla `CustomProductionContract` fires `CPCBlueprintsPicked` after the production picker; vanilla `cpcBPPicked` / `cpcBPPickedBar` accept the order at once. `IndEvo_cpcExpressOffer` wins with `score:100` when `IndEvo_CPCExpress valid $cpc_ref` resolves the contract, shows the contact's line and two options:

- `IndEvo_CPCExpress options` relabels Express Delivery with the cost (`round(PROD_DAYS * RELICS_PER_DAY)`, 60 at vanilla `PROD_DAYS`), disables it when fleet cargo has too few Relic Components and adds a tooltip with the new delivery time and the cost line.
- Standard Delivery (`IndEvo_cpcStandard`, `...Bar` on `$cpc_barEvent`) runs the vanilla row's script unchanged.
- Express Delivery (`IndEvo_cpcExpress`, `...Bar`) runs the same script with `IndEvo_CPCExpress apply $cpc_ref` right after the accept: takes the relics from fleet cargo and sets `getData(Stage.WAITING).elapsed = PROD_DAYS / 2`. The stage connection (`connectWithDaysElapsed`) and the intel both read that value, so delivery and the shown days follow. No save data of its own.

### Relic build in

Vanilla flow is translated from obfuscated `com.fs.starfarer.coreui.refit.ModPickerDialogV3` (0.98a-RC8): `tableRowSelected` refuses a new S-mod when `getStoryPoints() - pending <= 0`, `actionPerformed` on Confirm calls `spendStoryPoints(count, true, null, true, bonusXpFraction, text)`, `PermaModInfoWidget.advanceImpl` draws the counter from `getStoryPoints()`.

`createUI` in build in mode (`isPermMode()`, campaign only, the counter widget exists) -> `RelicBuildInPatch.afterCreateUI` shrinks the hullmod effect list (the widget's `ScrollPanelAPI` child) by one button row, adds the "Use Relics" area checkbox in that row at the full widget width (its 5 px first element x offset reset, so it lines up with Confirm and Cancel), moves vanilla's Confirm and Cancel (the `ButtonAPI` children whose text starts with "Confirm" / "Cancel", the label carries the shortcut) below it and the "Once confirmed" warning line (the `TooltipMakerAPI` child) below them, and makes this `Toggle` the active one. While relics are on the counter title "Story points remaining" reads "Relic components remaining" (the counter button's custom data `getLabel()`; `getValue()` is the number). Cost per S-mod `max(1, round(5 x unmodified DP))` of the refit ship (`RELICS_PER_DP`). Relics are counted in fleet cargo plus, when the refit was opened from an interaction at a market with storage access, that storage.

Toggle on:

- `tableRowSelected`: `getStoryPoints()` returns `available / cost`, so vanilla allows that many new S-mods. "No story points available" becomes "Not enough Relic Components".
- Counter widget: `getStoryPoints()` returns the same number (it greys the counter out at 0). The counter value's `setText` gets the relics left after the pending S-mods (`available - pending x cost`), the bonus experience readout (the only other `setText` ending in `%`) shows `0%`.
- Effect list: vanilla rebuilds it in every `advanceImpl` with `addSModSection` for pending S-mods that have an S-mod effect. `RelicBuildInListPatch` removes vanilla's tooltip from the scroll panel's content container each frame and shows its own list instead, rebuilt only when the pending S-mods change: one row per pending S-mod, `addSModSection` (or a heading with the hullmod name when it has no S-mod effect) on the left and, in the top right in line with the heading (same 10 px top pad), the Relic Components icon with "x N" centered below it. Hovering the icon shows "Use N relics instead of 1 SP to build in this hullmod" (Philipp's text). The ship comes from `getRefitPanel().getShipDisplay().getShip()`.
- Confirm: `spendStoryPoints` is not called (no story point, no bonus experience, no playthrough log text). `Toggle.pay(count)` takes `count x cost` through `RelicHelper.take` (storage first, then fleet cargo), and shows "Lost N Relic Components".

The checkbox is disabled while new S-mods are selected (`getAddedPermaMods` plus `getAddedSModdedBuiltIns`) and, while off, when relics do not cover one S-mod. Its hover tooltip shows effect, cost line and the lore line in gray. Toggle off: every redirect calls down, vanilla behaviour.

### Tech Mining boost

Relic sink on vanilla `TechMining` (API impl). The monthly find is `generateCargoForGatheringPoint` (`TechMining.java:198`): its size is ruin size (`getTechMiningRuinSizeModifier`, 0.2 to 1) times `Stats.TECH_MINING_MULT` times the decay value `$core_techMiningMult` (x `techMiningDecay` per month). `CoreScript.reportEconomyMonthEnd` -> `doCustomProduction` calls it on every industry of player markets (`CoreScript.java:1108`, `:1006`).

`isSuitable` (`techmining`, functional, player owned) -> option enabled unless `$IndEvo_relicTechMining` -> `RelicConfirmDialog` shows the requirement line, ruins, remaining yield (`$core_techMiningMult` as a percentage) and the relic counter, cost `max(5, round(20 x ruin size x find level))` -> `RelicHelper.take` (colony storage, then fleet cargo) -> `modifyMult(IndEvo_relicTechMining, 2)` on `Stats.TECH_MINING_MULT` and sets the memory key. The decay value is not touched, so the ruins do not wear out faster. The option hover shows effect, cost line and the lore text in gray (`LORE`, RELIC_PLAN section 6); the industry tooltip shows "Boost Extraction: next month's finds doubled" while pending.

`reportEconomyMonthEnd` removes the modifier and key from every market that has them, unless the market is player owned and its Tech Mining exists but is not functional (disrupted or upgrading): then the boost waits for the next month end. The listener runs after the find: `ReachEconomyStepper.doEndOfMonthStuff` calls `SectorAPI.reportEconomyMonthEnd` (sector listeners, `CoreScript` among them) before `ListenerUtil.reportEconomyMonthEnd` (`EconomyTickListener`s). Inferred from obfuscated code (`com.fs.starfarer.campaign.econ.reach.ReachEconomyStepper`); recheck after a game update.

### Relic sinks

One off Relic Component (`IndEvo_rare_parts`) payments. Each feature owns its cost constant and flow.

| Sink | Cost | Paid from | Owner |
|---|---|---|---|
| Construction speed up | 1 per remaining day | colony storage, then fleet cargo | [Relic speed up](#relic-speed-up) |
| IE timer speed up (Accelerate, Salvage Yards Accelerate Construction) | 1 per remaining day, halves it | colony storage, then fleet cargo | [Relic speed up](#relic-speed-up) |
| Rift Generator recharge (Recharge) | 3 per remaining day, ends the cooldown | colony storage, then fleet cargo | [Relic speed up](#relic-speed-up) |
| Early Edict removal | 1 per remaining day | colony storage, then fleet cargo | [Senate](../industries/SENATE.md#runtime-and-penalties) |
| Express Delivery | 1 per delivery day (60 at vanilla) | fleet cargo | [Custom production Express Delivery](#custom-production-express-delivery) |
| Story point derelict recovery | 5 per DP of each recovered ship | fleet cargo | [Salvage](../exploration/SALVAGE.md#relic-recovery) |
| Salvage Yards S-mod transfer | 5 x target DP per S-mod, plus credits | local storage, then fleet cargo | [Salvage Yards](../industries/SALVAGE_YARDS.md#s-mod-transfer) |
| Project Reforge (Galatia research) | 500, plus 1 Corrupted Nanoforge on Redeem | fleet cargo (relics as points) | [Research](../dialogue/RESEARCH.md#projects) |
| Forge Template refining | 2 per hull FP per quality level | colony storage, then fleet cargo | [Derelicts](../industries/DERELICTS.md#ancient-laboratory-relic-services) |
| Automated ship integration | 10 per hull DP | colony storage, then fleet cargo | [Derelicts](../industries/DERELICTS.md#ancient-laboratory-relic-services) |
| Hullmod extraction | `max(25, round(baseValue / 100))` per hullmod | colony storage, then fleet cargo | [Engineering Hub](../industries/ENGINEERING_HUB.md#hullmod-extraction) |
| Relic Lattice Work (Orbital Works) | 20 per hull DP, +1 S-mod slot after 93 days | colony storage, then fleet cargo | [Orbital Works slot](../industries/ORBITAL_WORKS_SLOT.md) |
| Refit build in | 5 per DP of the ship, per S-mod | market storage, then fleet cargo | [Relic build in](#relic-build-in) |
| Tech Mining Boost Extraction | `max(5, round(20 x ruin size x find level))` | colony storage, then fleet cargo | [Tech Mining boost](#tech-mining-boost) |

## Contracts

- The condition applies to every economy market including NPC ones, so parts and the Supercomputer bonus affect AI factions too (`ResourceConditionApplicator.java:13`).
- `unapply` resets `IndEvo_parts` supply and demand to 0 on every `BaseIndustry` of the market (`ResourceCondition.java:112`); any other source of parts on these industries must reapply after this condition. Inferred from the code: condition unapply order relative to industries is engine controlled.
- Reflection on `buildTime` (`RelicBuildTimeReductionButtonAdder.java:114`) depends on the `BaseIndustry` field name; `Edict_FastBuild` writes the same field through `ReflectionUtils`. See [Utils](UTILS.md#helper), [Senate](../industries/SENATE.md).
- Express Delivery has to run after the accept: `BaseHubMission.setCurrentStage` resets the new stage's `elapsed` to 0 (vanilla `BaseHubMission.java:1219`).
- The IE accept rows copy vanilla `cpcBPPicked` / `cpcBPPickedBar` (`$option = contact_accept 0`, `FireBest DialogOptionSelected`, `Call $cpc_ref repSuccess`, `Call $cpc_ref addContacts`). A game update that changes those rows needs the copy checked.
- Another mod with a higher scored `CPCBlueprintsPicked` row wins over IE; the Express option is then missing and the order goes through as vanilla.
- Shadowyards classes are imported at compile time (`ResourceCondition.java:14` to `16`); guarded by `isModEnabled("shadow_ships")`. See [Compatibility](COMPATIBILITY.md).
- `RelicBuildInPatch` and `RelicBuildInCounterPatch` target a readable class and its named inner class in an obfuscated package, every target there is inferred; recheck after a game update. Only `isPermMode`, `getAddedPermaMods` and `getAddedSModdedBuiltIns` are called directly (stable descriptors); `getRefitPanel`, `getMember` and `getChildrenCopy` go through PatchLib reflection. The toggle layout assumes vanilla's widget layout (`ModPickerDialogV3.java:1114-1125` in the knowledge skill).
- Only one build in dialog is open at a time: the counter widget can not reach the dialog's `PatchData`, so the active `Toggle` is a static weak reference checked by identity against the dialog or widget.
- `Toggle.pay` throws when relics do not cover the confirmed S-mods; the checkbox lock makes that unreachable.

## Known issues and dormant code

| Item | State |
|---|---|
| `applyParts` (`ResourceCondition.java:64`) | Supply `size - 3` is negative or zero below size 4; zero returns early, negative is passed to `ind.supply` (suspected) |
| `RelicBuildTimeReductionButtonAdder.isSuitable` | No player owned check (`:24`) |
| Changelog_todo | "check if BP creation works with relics" (open task, related to relic use) |
| Tech Mining boost without a gathering point | `doCustomProduction` returns early when the player has no production gathering point (`CoreScript.java:849`); the boost is still removed at month end |
| Relic build in | vanilla still writes an `SModRecord` with 1 story point spent per S-mod; the counter label still reads "Story points remaining" while the toggle is on (the tooltip explains it) |

## Maintenance

Update this doc when parts, Supercomputer income, a relic sink or its cost, or the build in patch changes. Industry behaviour belongs in the industry docs; settings mechanics in [Settings](SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
