# Military Relays

The relay industries `IndEvo_ComArray` (Military Relays) and `IndEvo_IntArray` (Interstellar Relay): patrol HQ replacement, relay entities and fleet size sharing across a relay network. Read it before changing fleet size transfer, relay entities or the Relay Hypertransmitter unlock. The Hypertransmitter item itself is in [Installables](../items/INSTALLABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Both ids use `MilitaryRelay`, a subclass of vanilla `MilitaryBase`. On a market without a military industry the relay works as a patrol HQ at double upkeep. Relays build only in systems where the faction has a military base or high command, or anywhere once a Relay Hypertransmitter sits in a functional military base or high command. All functional relays of a faction form networks (one per system, plus one shared network for every system with an Interstellar Relay); each member raises its `COMBAT_FLEET_SIZE_MULT` toward the largest fleet size in its network. The Interstellar Relay also spawns a comm relay entity.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/relay/industry/MilitaryRelay.java` | Industry plugin for both ids: patrol HQ mode (`patrolHqApply`, `patrolHqUnapply`), build rules, relay entity create and remove, `getFleetSizeTransferFraction`, tooltips. Nested `RelayItemRemovalButtonListener` (industry option) and `RelayFactor` (`ColonyOtherFactorsListener`) |
| `indevo/industries/relay/listener/RelayNetworkBrain.java` | Builds networks and applies results: `updateNetworks()` on new day, market open and close, and `forceUpdate()` |
| `indevo/industries/relay/plugins/RelayNetwork.java` | One network: list of `NetworkEntry`, picks the largest and assigns targets |
| `indevo/industries/relay/plugins/NetworkEntry.java` | Per market snapshot: `baseFleetSize` without relay bonus, `targetMult`, `sourceMarketId`; `applyToMarket()` pushes it into the condition |
| `indevo/industries/relay/condition/RelayConditionPlugin.java` | Hidden condition `IndEvo_RelayCondition`; holds the `NetworkEntry` and applies the fleet size mult |
| `indevo/industries/relay/entities/RelayEntityPlugin.java` | Custom entity plugin of the makeshift `IndEvo_int_array`: adds itself to vanilla `CommRelayCondition` of every market in its location |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_ComArray` (`Ids.COMARRAY`), upgrade `IndEvo_IntArray` | `data/campaign/industries.csv`, tags `structure, unraidable, tactical_bombardment, patrol, military` |
| Industry | `IndEvo_IntArray` (`Ids.INTARRAY`), downgrade `IndEvo_ComArray` | `data/campaign/industries.csv` |
| Condition | `IndEvo_RelayCondition` | `data/campaign/market_conditions.csv`, hidden |
| Entity | `IndEvo_int_array` (makeshift, `RelayEntityPlugin`), `IndEvo_pristine_int_array` (vanilla `CommRelayEntityPlugin`) | `data/config/custom_entities.json:1036`, `:1065`; tag `IndEvo_intArray` (`Ids.INTARRAY_ENTITY_TAG`) |
| Setting | `IndEvo_ComArray` | `data/config/LunaSettings.csv`, enables both |
| Memory key | `$IndEvo_RelayUnlocked` (`SpecialItemEffectsRepo.TRANSMITTER_UNLOCK_KEY`) | sector memory, set by the Hypertransmitter effect |
| Strings | category `IndEvo_ComArray` | `data/strings/strings.json:141` |

## Registration and lifecycle

- CSV plugin column for both industries.
- `ModPlugin.setListenersIfNeeded()`: `MilitaryRelay.RelayItemRemovalButtonListener.register()` (`ModPlugin.java:500`), `RelayNetworkBrain.getInstanceOrRegister()` (`:508`). `RelayFactor` is registered in `SpecialItemEffectsRepo.initEffectListeners()`.
- `RelayNetworkBrain` is a transient `CampaignEventListener` (`Global.getSector().addTransientListener`) and a `NewDayListener`. `TimeTracker.onNewDay` finds it through `SectorAPI.getAllListeners()` (`TimeTracker.java:133`), which includes transient listeners (`obf/campaign/CampaignEngine.java:2073`).
- Extra network updates: `finishBuildingOrUpgrading`, `setAICoreId`, and `notifyBeingRemoved` (delayed by `DelayedActionScriptRunWhilePaused(0.01f)` so the industry is gone first).
- Saved state: the `NetworkEntry` inside each `RelayConditionPlugin`, `addedOrRemovedItem` on the industry. Networks are rebuilt from scratch on every update.

Update chain: `TimeTracker -> RelayNetworkBrain.onNewDay -> updateNetworks -> RelayNetwork.calculateAndApplyFleetSizeMults -> NetworkEntry.assignTargets -> NetworkEntry.applyToMarket -> RelayConditionPlugin.unapply/apply`.

## Flow

### Industry modes

`apply()` (`MilitaryRelay.java:90`):

- `IndEvo_IntArray`: `createCommRelayStation` with `IndEvo_int_array`, or `IndEvo_pristine_int_array` when a special item is installed.
- No military on the market (`MiscIE.marketHasMilitary`): upkeep x2 (`ind_patHQ`), `patrolHqApply` copies vanilla patrol HQ behaviour: `super.apply(false)`, light/medium patrols by size and fleet size mult, demand supplies/fuel/ships `size - 1`, crew supply `size`, ground defense mult, `MemFlags.MARKET_PATROL`.
- Military present: only `applyIncomeAndUpkeep(3)`. `BaseIndustry.apply` is not called, so AI core, improve and item effects are not applied through the base class in this mode (AI cores still work, `getFleetSizeTransferFraction` reads `aiCoreId` directly).
- Build cost and time of `IndEvo_ComArray` without military: patrol HQ build time and cost x1.2 (`getBuildTime`, `getBuildCost`).

`isFunctional()` additionally requires `isFunctionAllowed()`: military base or high command of the faction in the system (`MiscIE.systemHasIndustry`), or `$IndEvo_RelayUnlocked`.

### Networks

`RelayNetworkBrain.updateNetworks()` (`:44`), per faction:

1. Markets with a functional `MilitaryRelay`, grouped by star system.
2. Every system that has an `IndEvo_IntArray` joins one shared network; other systems get their own.
3. Each network: largest `baseFleetSize` wins; every entry gets `targetMult = largest / own` and the source market id.

`RelayConditionPlugin.apply` (`:32`): with a functional relay on the market,
`best = base * targetMult`, `target = min(best, base + best * transferFraction)`, then `COMBAT_FLEET_SIZE_MULT.modifyMult(ID, target / base)` if above 1.

`getFleetSizeTransferFraction()` = 0.3 `+` AI core (alpha 0.3, beta 0.2, gamma 0.1) `+` 0.1 if improved (`:336`).

### Relay entities

`createCommRelayStation` checks for an entity tagged `IndEvo_intArray` and the market id in the location before spawning; it orbits the primary entity. `removeCommRelayStation` (from `unapply`) queues `EntityRemovalScript`, which removes the entity when the market no longer has the industry or when `addedOrRemovedItem` forces it (item swap between makeshift and pristine).

### Item removal option

`RelayItemRemovalButtonListener` shows "Remove Hypertransmitter" on a player `IndEvo_ComArray` holding a special item and moves it to storage. Inferred: the item can only end up there by downgrading an `IndEvo_IntArray`, because the item's `plugin params` list `IndEvo_IntArray, militarybase, highcommand` only.

## Contracts

- `NetworkEntry.calculateBaseFleetSize` rebuilds the stat without mods whose `source` is `IndEvo_RelayCondition`, so a relay bonus never feeds back into the next update.
- `MilitaryRelay.unapply` unmodifies `COMBAT_FLEET_SIZE_MULT` under the industry mod id, not the condition id; the condition removes its own mod.
- A market that loses its relay keeps a stale `NetworkEntry` in its condition; `RelayConditionPlugin.apply` ignores it because no functional relay is found.
- Colony factor tooltip: `RelayFactor` names the nearest player military base or high command with a special item.

## Known issues and dormant code

| Item | State |
|---|---|
| `NetworkEntry.calculateBaseFleetSize` (`NetworkEntry.java:54`) | computes `flat * (1 + sum percent) / 100 * mult`; vanilla `StatBonus.computeEffective` is `(base + base * percent / 100 + flat) * mult` (`api/combat/StatBonus.java:259`). Open issue in `Changelog_todo:30`: "Military relay bonus calc is still unreliable", and `:106` "relay increase spiral" |
| `RelayNetworkBrain.getInstanceOrRegister` (`:22`) | checks `ListenerManager.hasListenerOfClass` but registers with `addTransientListener`, so the check never matches; every `forceUpdate()` adds another transient brain and each runs `updateNetworks` per day and per market open |
| `removeCommRelayStation` script check (`MilitaryRelay.java:274`) | compares `getClass().getName()` to `"EntityRemovalScript"`; never true, dead check |
| Relay market outside a star system | `Collectors.groupingBy(MarketAPI::getStarSystem)` throws on a null key (inferred, `RelayNetworkBrain.java:54`) |

## Maintenance

Update this doc when the transfer formula, network grouping, patrol HQ mode or relay entities change. Item effect text belongs in [Installables](../items/INSTALLABLES.md), `NewDayListener` dispatch in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
