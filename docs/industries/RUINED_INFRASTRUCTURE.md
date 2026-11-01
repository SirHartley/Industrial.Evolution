# Ruined Infrastructure

The Ruined Infrastructure structure (`IndEvo_RuinedInfra`) and its hidden upgrade proxy `IndEvo_ChamelionIndustry`: a ruin on a planet that restores into one fixed industry at reduced cost and time. Read it before changing ruin placement, the target pick or the restore flow. It does not cover the Old Industrial Ruins (`IndEvo_Ruins`) that become derelict industries; see [Derelicts](DERELICTS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

At new game, uncolonized planets with tech mining ruins may get the `IndEvo_RuinedInfra` condition. When a faction colonizes the planet, the condition picks a target industry from `ruins_industry_whitelist.csv` (seeded by the planet) and adds the `IndEvo_RuinedInfra` industry. Upgrading that industry shows the target industry through `ChamelionIndustry`, costs and takes `IndEvo_DerInfraBuildRed` times the normal amount, and on completion swaps in the real target industry. Decivilizing a size 4+ colony with whitelisted industries leaves new ruined infrastructure for the next colonist.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/ruinfra/conditions/DerelictInfrastructureCondition.java` | Hidden condition. Picks and stores the target (`setUpgradeSpec`), adds the industry once (`addRuinfraIfNeeded`), removes itself when the industry is gone; static helpers `resetRuinfraState`, `marketPrimedForCondition` |
| `indevo/industries/ruinfra/industry/RuinedInfrastructure.java` | `IndEvo_RuinedInfra` industry: unbuildable, no AI cores, custom `startUpgrading` and `finishBuildingOrUpgrading`, AI faction auto upgrade, own tooltip ("restore" wording) |
| `indevo/industries/ruinfra/industry/ChamelionIndustry.java` | `IndEvo_ChamelionIndustry`: upgrade proxy whose `init` swaps its id to the stored target, so the upgrade dialog shows the target's name, image and stats; cost and time times `BUILD_PARAM_RED`; `isAvailableToBuild` and `getUnavailableReason` come from a fresh target instance |
| `indevo/industries/ruinfra/listener/RuinfraOnDecivListener.java` | `ColonyDecivListener`: primes a decivilizing market with a target from its own whitelisted industries |
| `indevo/industries/ruinfra/utils/DerelictInfrastructurePlacer.java` | New game placement of the condition |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_RuinedInfra` (`Ids.RUINFRA`), upgrade `IndEvo_ChamelionIndustry` | `data/campaign/industries.csv`, `do_not_show_in_build_dialog, codex_unlockable`, cost 0 |
| Industry | `IndEvo_ChamelionIndustry` (`Ids.CHAMELION`), downgrade `IndEvo_RuinedInfra` | `data/campaign/industries.csv`, hidden |
| Condition | `IndEvo_RuinedInfra` (`Ids.COND_INFRA`, same string as the industry id) | `data/campaign/market_conditions.csv:30`, hidden |
| Whitelist | `data/config/indEvo/ruins_industry_whitelist.csv` via `settings.json` key `IndEvo_ruindList` (`Ids.RUIND_LIST`) | IE industries, vanilla `farming` through `waystation`, `alcohol_brewery` |
| Setting | `IndEvo_DerInfraBuildRed` | cost and time mult, read once into `ChamelionIndustry.BUILD_PARAM_RED` |
| Setting | `IndEvo_Enable_Derelicts` | must be on for new game placement |
| Memory key | `$IndEvo_RuinedInfraIndId` (`RUINED_INFRA_UPGRADE_ID_KEY`) | market memory: target industry id |
| Memory key | `$IndEvo_infraPlaced_<marketId>` | market memory: industry already placed |
| Tag | `IndEvo_NoDerelictInfra` (`NO_INFRA_TAG`) | planet tag that blocks placement |

## Registration and lifecycle

- `ModPlugin.onNewGameAfterEconomyLoad()` calls `DerelictInfrastructurePlacer.placeRuinedInfrastructure()` (`ModPlugin.java:317`).
- `ModPlugin.setListenersIfNeeded()` calls `RuinfraOnDecivListener.register()` (`:482`), transient, deduped by class.
- `ModPlugin.loadTransientMemory()` preloads the whitelist (`:443`).

Chain: `placeRuinedInfrastructure -> condition on planet -> colonized -> DerelictInfrastructureCondition.apply -> setUpgradeSpec + addIndustry(IndEvo_RuinedInfra) -> player upgrades (ChamelionIndustry shown) -> RuinedInfrastructure.finishBuildingOrUpgrading -> addIndustry(target)`.

## Flow

### Placement

`placeRuinedInfrastructure` (`DerelictInfrastructurePlacer.java:20`) skips systems with markets. Per planet with `TechMining.getTechMiningRuinSizeModifier > 0`: chance `= modifier * 0.5`, x1.5 in `theme_ruins` / `theme_derelict` systems, divided by `1 + 0.3 * placed in system`; roll seeded with `Misc.getSalvageSeed(planet)`.

### Target and industry

`DerelictInfrastructureCondition.apply` runs only for real markets (not planet condition markets, not `neutral`) that have not placed yet. `setUpgradeSpec(market)` picks from the whitelist with the planet salvage seed and repicks while `getNewPluginInstance(market).isAvailableToBuild()` is false; fallback `IndEvo_AdAssem`. The target is only written if the key is unset. Then the industry is added and `EconomyAPI.tripleStep()` runs.

`advance` removes the condition once the market has no `IndEvo_RuinedInfra` industry and the placed flag is set.

### Restore

- `RuinedInfrastructure.startUpgrading` sets `upgradeId` to the CSV upgrade (`IndEvo_ChamelionIndustry`) and `buildTime = target build time * BUILD_PARAM_RED` (`RuinedInfrastructure.java:60`).
- `finishBuildingOrUpgrading` removes `IndEvo_ChamelionIndustry` and itself, adds the stored target and calls its `finishBuildingOrUpgrading` (`:72`).
- `notifyBeingRemoved` removes the condition.
- AI markets (`triggerUpgradeForAI`, `RuinedInfrastructure.java:100`, from `apply` on every economy step): not already restoring, a stored target, size above 4, faction owns more than 2 markets, the `IndEvo_ChamelionIndustry` instance (the target) is buildable and, for an industry target, a slot is free.

### Decivilization

`reportColonyAboutToBeDecivilized`: size 4+, planet, at least one whitelisted industry; resets state and stores a random whitelisted industry of that market. `reportColonyDecivilized` adds the condition if the market is primed.

## Contracts

- `ChamelionIndustry.getSpec()` returns the spec for its current `id`; after `finishBuildingOrUpgrading` the id goes back to `IndEvo_ChamelionIndustry`.
- `RuinedInfrastructure.apply()` does not call `BaseIndustry.apply`, so it has no upkeep, supply or item effects.
- The target is never re-rolled. A restore blocked by the target's own build rules (one per system, free slots, its setting) stays blocked until they pass, and the upgrade tooltip shows the target's `getUnavailableReason` (skill `api/impl/campaign/econ/impl/BaseIndustry.java:1106-1113`). At full industry slots vanilla adds "Maximum number of industries reached" (`:1046-1051`).
- The condition and industry share the string `IndEvo_RuinedInfra`; `RuinfraOnDecivListener` adds the condition with `Ids.RUINFRA`, which is the same value.
- `BUILD_PARAM_RED` is a static read at class load; a changed LunaLib value needs a restart (inferred from static init).

## Known issues and dormant code

| Item | State |
|---|---|
| Restore cost | open issue `Changelog_todo:8`: "Advertised price for upgrading the ruins to heavy industry was 250K ... zero credits were actually deducted" |
| `ChamelionIndustry.readResolve` (`ChamelionIndustry.java:34`) | condition inverted: sets `spec` from the memory key only when the key is missing, so it reads `getIndustrySpec(null)` |
| `setUpgradeSpec` repick (`DerelictInfrastructureCondition.java:55`) | first pick is seeded, repicks use the picker's own random; not deterministic per planet |
| `ChamelionIndustry.isDone` | written, never read |
| `RuinedInfrastructure.finishBuildingOrUpgrading` player message | commented out |

## Maintenance

Update this doc when placement odds, the whitelist key, the restore swap or the decivilization hook change. Derelict ruins belong in [Derelicts](DERELICTS.md), whitelist loading in [Settings](../core/SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
