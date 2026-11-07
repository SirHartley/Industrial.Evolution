# Economy

IE's global economy hooks in `indevo/economy/`: the hidden resource condition on every market (ship parts supply and demand, Supercomputer income), the relic based construction speed up button, and Express Delivery on the vanilla custom production contract. Read it before changing ship parts, Supercomputer income, construction time or a relic sink. It does not cover the industries that produce the inputs; see [Salvage Yards](../industries/SALVAGE_YARDS.md) and [Small industries](../industries/SMALL_INDUSTRIES.md) (Supercomputer).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Every market in the economy gets the hidden condition `IndEvo_ressCond` (`ResourceCondition`). Its `apply` adds Starship Components (`IndEvo_parts`) supply and demand to heavy industries and applies the distance based income bonus of the nearest Supercomputer. Separately, an industry option "Speed up construction..." halves the remaining build time of any building or upgrading industry for Relic Components (`IndEvo_rare_parts`), once per 60 days per colony. Express Delivery halves the delivery time of a vanilla custom production order for Relic Components.

## Files

| File | Owner / role |
|---|---|
| `indevo/economy/conditions/ResourceCondition.java` | Condition plugin: `applyParts()`, `applySupComIncome()`; static `applyRessourceCond(market)` adds the condition to markets in the economy |
| `indevo/economy/listeners/ResourceConditionApplicator.java` | `PlayerColonizationListener` and `EconomyTickListener`: adds the condition on colonization and on every economy tick to all markets |
| `indevo/economy/listeners/RelicBuildTimeReductionButtonAdder.java` | `BaseSimpleBaseIndustryOptionProvider`: the speed up option, dialog and payment |
| `indevo/dialogue/rules/IndEvo_CPCExpress.java` | Rule command for Express Delivery: `valid`, `options`, `apply` |
| `data/campaign/rules.csv` block `# Custom production express delivery` | Express Delivery offer and the two accept paths |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Condition | `IndEvo_ressCond` (`Ids.COND_RESSOURCES`), tags `hidden, technical` | `data/campaign/market_conditions.csv:28` |
| Commodity | `IndEvo_parts` (`ItemIds.PARTS`), `IndEvo_rare_parts` (`ItemIds.RARE_PARTS`) | `data/campaign/commodities.csv:2`, `:3` |
| Setting | `IndEvo_ScrapYard` (parts on/off) | `data/config/LunaSettings.csv:97` |
| Memory key | `$IndEvo_reduceBuildTimeCooldown` (market memory, expires after `COOLDOWN_DAYS` 60) | `RelicBuildTimeReductionButtonAdder` |
| Option ids | `IndEvo_cpcExpress` (`IndEvo_CPCExpress.OPTION_EXPRESS`), `IndEvo_cpcStandard` | rules, `IndEvo_CPCExpress` |
| Rules | `IndEvo_cpcExpressOffer` (trigger `CPCBlueprintsPicked`, score 100), `IndEvo_cpcStandard`, `IndEvo_cpcStandardBar`, `IndEvo_cpcExpress`, `IndEvo_cpcExpressBar` | `data/campaign/rules.csv:860` |
| Modifier ids | condition id (`getModId()`) on `market.getIncomeMult()`; condition id on `Stats.PRODUCTION_QUALITY_MOD` | `ResourceCondition` |

## Registration and lifecycle

- `ModPlugin.onGameLoad` -> `ResourceConditionApplicator.applyResourceConditionToAllMarkets()` (`indevo/utils/ModPlugin.java:159`) and `ResourceConditionApplicator.register()` (`:428`, transient listener).
- Economy tick -> `applyResourceConditionToAllMarkets()` -> `ResourceCondition.applyRessourceCond(m)` adds the condition if `m.isInEconomy()` and missing. The condition itself persists in the market.
- `RelicBuildTimeReductionButtonAdder.register()` (`indevo/utils/ModPlugin.java:487`) adds a transient listener.

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

### Construction speed up

`isSuitable` (building, or upgrading with an upgrade spec) -> option enabled unless `$IndEvo_reduceBuildTimeCooldown` -> `onClick` reads private `buildTime` via `StarReflectionUtils.get(ind, "buildTime", float.class, true)` -> cost `round(daysLeft * RELICS_PER_DAY)` (1 relic per remaining day) taken from local storage first, then player cargo -> `BaseIndustry.setBuildProgress((buildTime - left) + left / 2)` -> sets cooldown.

### Custom production Express Delivery

Vanilla `CustomProductionContract` fires `CPCBlueprintsPicked` after the production picker; vanilla `cpcBPPicked` / `cpcBPPickedBar` accept the order at once. `IndEvo_cpcExpressOffer` wins with `score:100` when `IndEvo_CPCExpress valid $cpc_ref` resolves the contract, shows the contact's line and two options:

- `IndEvo_CPCExpress options` relabels Express Delivery with the cost (`round(PROD_DAYS * RELICS_PER_DAY)`, 60 at vanilla `PROD_DAYS`), disables it when fleet cargo has too few Relic Components and adds a tooltip with the new delivery time and the cost line.
- Standard Delivery (`IndEvo_cpcStandard`, `...Bar` on `$cpc_barEvent`) runs the vanilla row's script unchanged.
- Express Delivery (`IndEvo_cpcExpress`, `...Bar`) runs the same script with `IndEvo_CPCExpress apply $cpc_ref` right after the accept: takes the relics from fleet cargo and sets `getData(Stage.WAITING).elapsed = PROD_DAYS / 2`. The stage connection (`connectWithDaysElapsed`) and the intel both read that value, so delivery and the shown days follow. No save data of its own.

### Relic sinks

One off Relic Component (`IndEvo_rare_parts`) payments. Each feature owns its cost constant and flow.

| Sink | Cost | Paid from | Owner |
|---|---|---|---|
| Construction speed up | 1 per remaining day | colony storage, then fleet cargo | [Construction speed up](#construction-speed-up) |
| Early Edict removal | 1 per remaining day | colony storage, then fleet cargo | [Senate](../industries/SENATE.md#runtime-and-penalties) |
| Express Delivery | 1 per delivery day (60 at vanilla) | fleet cargo | [Custom production Express Delivery](#custom-production-express-delivery) |
| Story point derelict recovery | 5 per DP of each recovered ship | fleet cargo | [Salvage](../exploration/SALVAGE.md#relic-recovery) |
| Salvage Yards S-mod transfer | 5 x target DP per S-mod, plus credits | local storage, then fleet cargo | [Salvage Yards](../industries/SALVAGE_YARDS.md#s-mod-transfer) |
| Project Reforge (Galatia research) | 500, plus 1 Corrupted Nanoforge on Redeem | fleet cargo (relics as points) | [Research](../dialogue/RESEARCH.md#projects) |
| Forge Template refining | 2 per hull FP per quality level | colony storage, then fleet cargo | [Derelicts](../industries/DERELICTS.md#ancient-laboratory-relic-services) |
| Automated ship integration | 10 per hull DP | colony storage, then fleet cargo | [Derelicts](../industries/DERELICTS.md#ancient-laboratory-relic-services) |

## Contracts

- The condition applies to every economy market including NPC ones, so parts and the Supercomputer bonus affect AI factions too (`ResourceConditionApplicator.java:13`).
- `unapply` resets `IndEvo_parts` supply and demand to 0 on every `BaseIndustry` of the market (`ResourceCondition.java:112`); any other source of parts on these industries must reapply after this condition. Inferred from the code: condition unapply order relative to industries is engine controlled.
- Reflection on `buildTime` (`RelicBuildTimeReductionButtonAdder.java:60`) depends on the `BaseIndustry` field name; `Edict_FastBuild` writes the same field through `ReflectionUtils`. See [Utils](UTILS.md#helper), [Senate](../industries/SENATE.md).
- Express Delivery has to run after the accept: `BaseHubMission.setCurrentStage` resets the new stage's `elapsed` to 0 (vanilla `BaseHubMission.java:1219`).
- The IE accept rows copy vanilla `cpcBPPicked` / `cpcBPPickedBar` (`$option = contact_accept 0`, `FireBest DialogOptionSelected`, `Call $cpc_ref repSuccess`, `Call $cpc_ref addContacts`). A game update that changes those rows needs the copy checked.
- Another mod with a higher scored `CPCBlueprintsPicked` row wins over IE; the Express option is then missing and the order goes through as vanilla.
- Shadowyards classes are imported at compile time (`ResourceCondition.java:14` to `16`); guarded by `isModEnabled("shadow_ships")`. See [Compatibility](COMPATIBILITY.md).

## Known issues and dormant code

| Item | State |
|---|---|
| `applyParts` (`ResourceCondition.java:64`) | Supply `size - 3` is negative or zero below size 4; zero returns early, negative is passed to `ind.supply` (suspected) |
| `RelicBuildTimeReductionButtonAdder.isSuitable` | No player owned or local market check; logs every call in dev mode (`:40`, `:46`) |
| `RelicBuildTimeReductionButtonAdder` imports | Unused `org.codehaus.janino.Mod` and `RUINED_INFRA_UPGRADE_ID_KEY` |
| Changelog_todo | "check if BP creation works with relics" (open task, related to relic use) |

## Maintenance

Update this doc when parts, Supercomputer income, a relic sink or its cost changes. Industry behaviour belongs in the industry docs; settings mechanics in [Settings](SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
