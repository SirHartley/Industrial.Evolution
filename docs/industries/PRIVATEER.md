# Privateer Base

The Privateer Base industry (`IndEvo_pirateHaven`): monthly raids on hostile systems, decaying raid output, loot delivery and the Lawless Subpopulation condition. Read it before changing raid targeting, raid output or the slot patch. It does not cover the Helldivers government variant beyond its hooks; see [Changeling](CHANGELING.md). Raid cooldowns live in `RaidTimeout`; see [Utils](../core/UTILS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The base takes two industry slots: a PatchLib patch counts it twice in `Misc.getNumIndustries`. Once a month it picks a hostile faction, rates that faction's systems and launches a vanilla `RaidIntel` raid from its market. On success the base supplies the commodities the raided markets produced (90%, 45% on a failed action stage), decaying by 1 unit per month, and a player owned base drops weapons, fighters and blueprints into storage. While functional it adds the `IndEvo_pirate_subpop` condition to its market.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/privateer/industry/PrivateerBase.java` | Industry plugin. Raid scheduling (`reportEconomyTick`), target choice (`getRaidTarget`, `getBestTargetSystem`), raid assembly (`startRaid`), result handling (`notifyRaidEnded`, `RaidIntel.RaidDelegate`), output memory and decay (`supplyMemory`, `supplyDiminishment`), loot (`spoilsOfWar`, `getLoot`), AI core effects |
| `indevo/industries/privateer/patches/PrivateerIndustrySlotPatch.java` | `@After` on `Misc.getNumIndustries(MarketAPI)`: +1 per `IndEvo_pirateHaven` on the market or in its construction queue |
| `indevo/industries/privateer/intel/PrivateerBaseRaidIntel.java` | `RaidIntel` subclass; `createFleet` builds the raider fleet with `FleetFactoryV3`, sets war fleet and raider flags, names it |
| `indevo/industries/privateer/conditions/PirateSubpopulationCondition.java` | `IndEvo_pirate_subpop` condition: stability and immigration effect, Nexerelin morality check |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_pirateHaven` (`Ids.PIRATEHAVEN`) | `data/campaign/industries.csv`, tags `industry, tactical_bombardment, military` |
| Condition | `IndEvo_pirate_subpop` (`Ids.COND_PIRATES`) | `data/campaign/market_conditions.csv`, `decivRemove` TRUE |
| Setting | `IndEvo_pirateHaven` | `data/config/LunaSettings.csv`, enables the industry |
| Setting | `IndEvo_RaidForUnknownOnly` | blueprint loot skips blueprints the player knows; `PrivateerBase.java:315` |
| Setting | `IndEvo_PrivateerDeliverToGatheringPoint` | loot goes to the production gathering point storage; `PrivateerBase.java:303` |
| Memory key | `$IndEvo_RaidTimeout`, `$IndEvo_RaidTimeout_AI` | sector memory, owned by `RaidTimeout` |

Settings are listed in [Settings](../core/SETTINGS.md).

## Registration and lifecycle

- CSV plugin column; nothing in `ModPlugin`. `PrivateerIndustrySlotPatch` is a `@Patch` class, installed by PatchLib ([Patches](../ARCHITECTURE.md#patches)).
- `apply()` adds itself as transient `EconomyTickListener` (`PrivateerBase.java:73`); `unapply()` removes it.
- `ListenerManager.addListener` dedupes the same instance (`obf/campaign/ListenerManager.java:29`, `ObjectRepository.add` checks `contains`), so the repeated call in `apply()` is safe.
- Persistent state is in fields: `currentIntel`, `raidTimeoutMonths`, `supplyMemory`, `decayStopTimer`, `aiCoreFPBonus`. `apply()` rebuilds `supply` from `supplyMemory` (`:75-81`).

Run order: `reportEconomyTick` (every tick: add condition) `-> last tick of month -> supplyDiminishment -> raidTimeoutMonths-- -> getRaidTarget -> startRaid -> RaidIntel stages -> notifyRaidEnded -> setRaidIndustryOutput / spoilsOfWar / RaidTimeout.addRaidedSystem`.

## Flow

### Target choice

`getActiveHostileFactions()` collects factions with a visible in economy market hostile to the base faction. `getRaidTarget()` tries every hostile faction once in random order and calls `getBestTargetSystem(faction)`, which scores every system of that faction not in `RaidTimeout`:

- `+ getBaseRaidFP() * 1.5 - FP of hostile fleets in or near the system`
- station `-20` (else `+10`), patrol HQ `-size` (else `+5`), military base `-3 * size`, high command `-4 * size`
- `+ size`, `+20` for heavy industry / orbital works (also Shadowyards `ms_modularFac`, `ms_massIndustry`), `+ total output / 2`

Highest score wins. No target on a player base sends a "make some more enemies" message.

### Raid

`startRaid` (`:568`) builds `PrivateerBaseRaidIntel` with `AssembleStage` (nearest jump point in the home system, spawn FP = `getBaseRaidFP()`), `TravelStage`, vanilla `PirateRaidActionStage`, `ReturnStage`. Abort FP is 50% of spawn FP on every stage. The target system gets a 3 month `RaidTimeout` at start.

`getBaseRaidFP() = size * 50 * fleetSizeMult * random(0.75..1.25) * aiCoreFPBonus^2 * (1 + alcohol bonus)` (`:172-178`). `fleetSizeMult` is the market fleet size, `getMod(COMBAT_FLEET_SIZE_MULT).computeEffective(0)`, divided by the colony size flat `FleetFactoryV3.getNumShipsMultForMarketSize(size)` that `size * 50` already stands for, so flat bonuses (skills, items, Lockdown) and mults (doctrine, shortage, stability, military, relay) count the way they do for vanilla fleets. The core bonus is applied twice on purpose (code comment, `:173`). Alcohol bonus: 3% per available unit of any commodity whose id contains `alcoholism` (Alcoholism mod).

### Result

`notifyRaidEnded` (`:190`):

| Status | Effect |
|---|---|
| Success | `setRaidIndustryOutput(system, true)`; `RaidTimeout` 8 months (6 with beta core); player base: `spoilsOfWar` |
| Failure in or after action stage | output at `FAIL_OUTPUT_FRACT` 0.45; `raidTimeoutMonths += 1`; 1 month `RaidTimeout` |
| Failure before action stage | no output; same timeouts |

`setRaidIndustryOutput` takes, per commodity, the highest supply of any single industry on each hostile market in the system (`getMarketProductionAmounts`), skips `ships`, and raises the base's own supply to `round(value * fract)` if that is higher than the current remembered value. With a gamma core the commodity also skips the next monthly decay (`decayStopTimer`).

### Loot

`spoilsOfWar` treats markets with heavy industry, orbital works, Shadowyards yards, military base or high command as ship users. `getLoot(market, withBP)`:

- chance of any drop 0.7 (0.9 alpha), extra drops 0.3 (0.4 alpha) halved
- ship users roll blueprints from the target faction's known ships, weapons and fighters; skips `no_bp_drop` and unboardable hulls; Tiandong and Roider hulls become retrofit blueprints (`EngineeringHub.isTiandong`, `isRoider`)
- weapons and fighters weighted by rarity; non ship users only drop tier 0 to 1, non large weapons and tier 0 fighters

Cargo goes to local storage, or to the gathering point when the setting is on or storage is missing.

### Lawless Subpopulation

`PirateSubpopulationCondition.apply`: stability `-2`, `+2` with the base on the market, `+2` if a pirate, Luddic Path or (Nexerelin) `EVIL` morality faction is at worst neutral to the market faction. Immigration adds the `poor` faction and a growth modifier from `getImmigrationBonus()`.

### Helldivers variant

If the population industry is `SwitchablePopulation` running `HelldiversSubIndustry`, names change ("Democratic Liberation Base", "Democratic Liberation Force") and raid results call `HelldiversSubIndustry.reportPrivateerBaseRaidFinished` (`PrivateerBase.java:210-222`). See [Changeling](CHANGELING.md).

### AI cores

| Core | Effect | Where |
|---|---|---|
| Alpha | `aiCoreFPBonus` 1.2 (applied twice), better loot chances | `applyAICoreToIncomeAndUpkeep`, `getLoot` |
| Beta | `aiCoreFPBonus` 1.1 (applied twice), success timeout 6 months | `notifyRaidEnded` |
| Gamma | delays decay of newly raised outputs by one month | `setRaidIndustryOutput` |

## Contracts

- Two slots: vanilla `Misc.getNumIndustries` (skill `api/util/Misc.java:5248`) counts the base once, built, under construction or queued; the patch adds one more. Every vanilla limit check uses it: build menu, queue start (`BaseIndustry.java:614`), over-limit stability penalty (`PopulationAndInfrastructure.java:454`), the colony industry counter. Code that counts `isIndustry()` itself sees one: the colony industry name list and the NPC admin skill pick (`CoreLifecyclePluginImpl.java:1723`).
- Build check: setting on, at least two free industry slots, and fewer than two `IndEvo_pirateHaven` of the faction in the system (`isAvailableToBuild`, `:96`). `getUnavailableReason` uses the same two counts.
- Build tooltip: `BaseIndustry.createTooltip` adds the instance to the market while it builds (skill `api/impl/campaign/econ/impl/BaseIndustry.java:966`), and `Market.clone` is shallow, so the shared industry list holds it there. `getFreeIndustrySlots` and `getAmountInSystem` subtract it again (two slots, one base) when `market.getIndustries()` contains the instance; without that the tooltip shows the unavailable reason while the build button is enabled.
- `updateSupplyAndDemandModifiers` clears `supplyBonus`, so the output ignores market supply bonuses (`:60`).
- `RaidIntel` comes from `api/impl/campaign/intel/raid/RaidIntel.java`; the base is its `RaidDelegate`. `currentIntel` is saved; only one raid per base runs at a time.
- The condition is added but never removed by IE; it stays after the base is gone (only `decivRemove` clears it). Inferred: intended as a lasting penalty.
- Dev mode (`Global.getSettings().isDevMode()`) starts a raid and runs `debugOutputs()` on every economy tick (`:139`).

## Known issues and dormant code

| Item | State |
|---|---|
| Dev mode branch `startRaid(getRaidTarget(), ...)` (`:140`) | NPE in `MiscIE.getMarketsInLocation(null)` when no target exists; dev mode only |
| `PirateSubpopulationCondition` imports `exerelin.*` | only resolved when `nexerelin` is enabled; compile time dependency on Nexerelin |

## Maintenance

Update this doc when raid scoring, output fractions, timeouts, loot rules or the slot patch change. Raid timeout storage belongs in [Utils](../core/UTILS.md), Helldivers in [Changeling](CHANGELING.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
