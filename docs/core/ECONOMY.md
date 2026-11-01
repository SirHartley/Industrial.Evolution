# Economy

IE's global economy hooks in `indevo/economy/`: the hidden resource condition on every market (ship parts supply and demand, corruption, Supercomputer income) and the relic based construction speed up button. Read it before changing ship parts, corruption or construction time. It does not cover the industries that produce the inputs; see [Salvage Yards](../industries/SALVAGE_YARDS.md) and [Small industries](../industries/SMALL_INDUSTRIES.md) (Supercomputer).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Every market in the economy gets the hidden condition `IndEvo_ressCond` (`ResourceCondition`). Its `apply` adds Starship Components (`IndEvo_parts`) supply and demand to heavy industries, applies a corruption income multiplier above a credit cutoff, and applies the distance based income bonus of the nearest Supercomputer. Separately, an industry option "Speed up construction..." halves the remaining build time of any building or upgrading industry for Relic Components (`IndEvo_rare_parts`), once per 60 days per colony.

## Files

| File | Owner / role |
|---|---|
| `indevo/economy/conditions/ResourceCondition.java` | Condition plugin: `applyParts()`, `applyCorruption()`, `applySupComIncome()`; static `applyRessourceCond(market)` adds the condition to markets in the economy |
| `indevo/economy/listeners/ResourceConditionApplicator.java` | `PlayerColonizationListener` and `EconomyTickListener`: adds the condition on colonization and on every economy tick to all markets |
| `indevo/economy/listeners/RelicBuildTimeReductionButtonAdder.java` | `BaseSimpleBaseIndustryOptionProvider`: the speed up option, dialog and payment |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Condition | `IndEvo_ressCond` (`Ids.COND_RESSOURCES`), tags `hidden, technical` | `data/campaign/market_conditions.csv:28` |
| Commodity | `IndEvo_parts` (`ItemIds.PARTS`), `IndEvo_rare_parts` (`ItemIds.RARE_PARTS`) | `data/campaign/commodities.csv:2`, `:3` |
| Setting | `IndEvo_ScrapYard` (parts on/off), `IndEvo_Corruption`, `IndEvo_CorruptionCutoff` (default 40000) | `data/config/LunaSettings.csv:90`, `:6`, `:7` |
| Memory key | `$IndEvo_reduceBuildTimeCooldown` (market memory, expires after `COOLDOWN_DAYS` 60) | `RelicBuildTimeReductionButtonAdder` |
| Modifier ids | condition id (`getModId()`) and `<id>_corruption` on `market.getIncomeMult()`; condition id on `Stats.PRODUCTION_QUALITY_MOD` | `ResourceCondition` |

## Registration and lifecycle

- `ModPlugin.onGameLoad` -> `ResourceConditionApplicator.applyResourceConditionToAllMarkets()` (`indevo/utils/ModPlugin.java:163`) and `ResourceConditionApplicator.register()` (`:452`, transient listener).
- Economy tick -> `applyResourceConditionToAllMarkets()` -> `ResourceCondition.applyRessourceCond(m)` adds the condition if `m.isInEconomy()` and missing. The condition itself persists in the market.
- `RelicBuildTimeReductionButtonAdder.register()` (`indevo/utils/ModPlugin.java:506`) adds a transient listener.

## Flow

### Ship parts

`ResourceCondition.apply` -> if `IndEvo_ScrapYard` -> `applyParts()`. Per industry:

| Industry | Parts supply | Parts demand |
|---|---|---|
| `instanceof HeavyIndustry` (vanilla `heavyindustry` and `orbitalworks`, both use that plugin in vanilla `data/campaign/industries.csv`) | `min(size - 3, 3)` | `size` |
| Shadowyards `MS_modularFac` / `MS_fabUpgrader`, only if `shadow_ships` enabled | `MODULARFACTORIES` size-3, `PARALLEL_PRODUCTION` size-2 | `MILITARY_LINES` size-4, `SHIPYARDS` size-2 |
| Other industry tagged `heavyindustry` that supplies ships and no parts | `min(machinery - 2, 4)` | ships supply + 2 |

- Supply: Corrupted Nanoforge -1, Pristine -3; metals or rare metals deficit cuts parts supply (`applyPartsSupply`).
- Demand: Corrupted -1, Pristine -2. A parts shortfall gives a production quality penalty `deficit / demand * maxPenalty` (-0.5, -0.4 corrupted, -0.3 pristine) and cuts ship output by at most 2 (`applyPartsDemands`, `ResourceCondition.java:176`).

### Corruption

If `IndEvo_Corruption` and net income `> IndEvo_CorruptionCutoff`: with `x = income / cutoff`, the income above the cutoff is scaled by `x^-0.6` and applied as an income multiplier "Corruption" (`ResourceCondition.java:42`). A flat modifier is not used because the market income cannot take one (code comment, `:56`).

### Supercomputer income

If no market of the same faction in the location has `IndEvo_SupCom`: income mult `1 + Supercomputer.getDistanceIncomeMult(locInHyper)` labelled with the nearest Supercomputer market (`ResourceCondition.java:61`). Distance math is owned by `Supercomputer`; see [Small industries](../industries/SMALL_INDUSTRIES.md).

### Construction speed up

`isSuitable` (building, or upgrading with an upgrade spec) -> option enabled unless `$IndEvo_reduceBuildTimeCooldown` -> `onClick` reads private `buildTime` via `StarReflectionUtils.get(ind, "buildTime", float.class, true)` -> cost `round(daysLeft * RELICS_PER_DAY)` (1 relic per remaining day) taken from local storage first, then player cargo -> `BaseIndustry.setBuildProgress((buildTime - left) + left / 2)` -> sets cooldown.

## Contracts

- The condition applies to every economy market including NPC ones, so parts, corruption and Supercomputer bonus affect AI factions too (`ResourceConditionApplicator.java:13`).
- `unapply` resets `IndEvo_parts` supply and demand to 0 on every industry of the market (`ResourceCondition.java:136`); any other source of parts on these industries must reapply after this condition. Inferred from the code: condition unapply order relative to industries is engine controlled.
- Reflection on `buildTime` (`RelicBuildTimeReductionButtonAdder.java:60`) depends on the `BaseIndustry` field name; `Edict_FastBuild` writes the same field through `ReflectionUtils`. See [Utils](UTILS.md#helper), [Senate](../industries/SENATE.md).
- Shadowyards classes are imported at compile time (`ResourceCondition.java:14` to `16`); guarded by `isModEnabled("shadow_ships")`. See [Compatibility](COMPATIBILITY.md).

## Known issues and dormant code

| Item | State |
|---|---|
| `applyParts` (`ResourceCondition.java:86`) | Supply `size - 3` is negative or zero below size 4; zero returns early, negative is passed to `ind.supply` (suspected) |
| `unapply` (`ResourceCondition.java:137`) | Casts every industry to `BaseIndustry`; a modded industry that is not one throws `ClassCastException` (suspected) |
| `RelicBuildTimeReductionButtonAdder.isSuitable` | No player owned or local market check; logs every call in dev mode (`:40`, `:46`) |
| `RelicBuildTimeReductionButtonAdder` imports | Unused `org.codehaus.janino.Mod` and `RUINED_INFRA_UPGRADE_ID_KEY` |
| Changelog_todo | "check if BP creation works with relics" (open task, related to relic use) |

## Maintenance

Update this doc when parts, corruption, Supercomputer income or the speed up cost changes. Industry behaviour belongs in the industry docs; settings mechanics in [Settings](SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
