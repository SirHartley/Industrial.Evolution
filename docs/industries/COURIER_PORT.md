# Courier Port

Covers the Courier Port industry (`IndEvo_PrivatePort`) and the shipping contract system: contract editor dialog, daily scheduler, courier fleets, cost formula and shipping intel. Read it before changing contracts, shipping costs or courier fleet behaviour. It does not cover the side panel framework the editor draws on; see [Side panel](../dialogue/SIDE_PANEL.md). Which submarkets exist is owned by [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The Courier Port is a structure with no supply or demand. While the player owns at least one functional port, saved contracts move cargo and ships between whitelisted submarkets of any two non-hostile markets on a cadence (once, 14, 31, 93 or 186 days). On each due day the scheduler pulls the goods from the origin submarket, spawns a courier fleet that flies to the destination, and deposits the goods on arrival or when a failsafe timer runs out. Cost is charged as upkeep on the nearest port's monthly report. The AI core installed in the most recently applied port is global: alpha cuts total cost, beta cuts the distance multiplier, gamma lets comm relays open the shipping menu.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/courierport/industry/CourierPortV2.java` | Industry plugin: build toggle, upcoming shipment tooltip, writes the global AI core key |
| `indevo/industries/courierport/ShippingContract.java` | Contract data: from/to market and submarket ids, `Scope`, target cargo, ship variant list, cadence, `elapsedDays`, `isActive`; market lookup incl. RAT settlement and Gacha station |
| `indevo/industries/courierport/ShippingContractMemory.java` | Contract list in sector memory; add, replace, remove |
| `indevo/industries/courierport/ShippingCostCalculator.java` | Cost formula, AI core reductions, AI core memory key |
| `indevo/industries/courierport/ShippingCargoManager.java` | Picks the goods for a contract from the origin submarket (copy or remove) |
| `indevo/industries/courierport/ShippingTargetHelper.java` | Valid origin and target markets and submarkets |
| `indevo/industries/courierport/ShippingTooltipHelper.java` | Text helpers (cadence, amounts) |
| `indevo/industries/courierport/listeners/ShippingManager.java` | Persistent `NewDayListener`: daily contract tick, charging |
| `indevo/industries/courierport/listeners/Shipment.java` | One running delivery: `NewDayListener` + `EveryFrameScript`; failsafe, reroute, transfer |
| `indevo/industries/courierport/listeners/SubmarketCargoPicker.java`, `SubmarketShipPicker.java` | Vanilla cargo and fleet member pickers for specific scopes |
| `indevo/industries/courierport/fleet/CourierFleetCreator.java` | Builds the courier fleet with `FleetFactoryV3` |
| `indevo/industries/courierport/fleet/CourierFleetAssignmentAI.java` | Load, travel, unload, despawn assignments |
| `indevo/industries/courierport/intel/ShippingIntel.java` | Intel per shipment, ends 7 days after delivery |
| `indevo/industries/courierport/dialogue/CourierPortDialoguePlugin.java` | Rule command and `InteractionDialogPlugin`: New Contract / Manage Contracts / Return |
| `indevo/industries/courierport/dialogue/ContractSidePanelCreator.java` | Contract editor side panel; edit backup for cancel |
| `indevo/industries/courierport/dialogue/ContractListSidePanelCreator.java` | Contract list: Edit, Remove, Ship now, Activate |
| `indevo/industries/courierport/dialogue/CourierPortIsDisplayInRelay.java` | Rule condition: gamma core installed |
| `indevo/industries/courierport/plugin/CourierPortOptionProvider.java` | Industry option "Visit the Courier Port..." |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_PrivatePort` (`Ids.PORT`) | `data/campaign/industries.csv:45`, `indevo/ids/Ids.java:69` |
| Faction | `IndEvo_couriers` (courier fleets) | `data/world/factions/IndEvo_couriers.faction` |
| Setting | `IndEvo_PrivatePort` (build toggle and new game placement) | `data/config/LunaSettings.csv:105` |
| Setting | `IndEvo_courier_costPerSpace` (5), `IndEvo_courier_costPerShip` (170), `IndEvo_courier_baseFee` (1000), `IndEvo_courier_costPerLyMult` (1.0) | `data/config/LunaSettings.csv:191-194`, read at use time in `ShippingCostCalculator` |
| Config CSV | `IndEvo_shipingList`: submarket whitelist | `data/config/settings.json:55`, `data/config/indEvo/shipping_submarkets_whitelist.csv` |
| Memory key | `$IndEvo_ShippingContainerStorage` (sector, contract list) | `ShippingContractMemory.java:11` |
| Memory key | `$IndEvo_CurrentShippingCore` (sector, last applied core id or `none`) | `ShippingCostCalculator.java:21` |
| Memory key | `$IndEvo_ShippingContractMemory` (sector, editor backup, 0 day expiry) | `ContractSidePanelCreator.java:42` |
| Memory key | `$IndEvo_tempDialogueInstance` (sector, current dialog plugin) | `CourierPortDialoguePlugin.java:47` |
| Rules | `IndEvo_OpenShippingPortFromIndustryOption`, `IndEvo_ShippingMenuPoint`, `IndEvo_ShippingMenuPoint2`, `IndEvo_IntitiateShippingMenu`, `IndEvo_CommrelayShippingPoint`, `IndEvo_COB_shipping` | `data/campaign/rules.csv:934-948` |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded()`: `ShippingManager.getInstanceOrRegister()` (persistent listener, one instance) and `CourierPortOptionProvider.register()` (transient) (`indevo/utils/ModPlugin.java:437,472`).
- `NewGameIndustryPlacer.placeCourierPorts()` puts one port per faction per star system at new game when the setting is on (`indevo/utils/update/NewGameIndustryPlacer.java:92-118`).
- `NewDayListener` calls come from `TimeTracker`, which collects listeners from the listener manager and sector listeners; see [Utils](../core/UTILS.md).
- Entry points into the dialog: market menu and colony option point rules, the industry option (fires trigger `IndEvo_FireBaseSPOptionList` with `$IndEvo_closeDialogueOnNextReturn`), and comm relays when `CourierPortIsDisplayInRelay` is true. All end in `CourierPortDialoguePlugin.execute`, which swaps itself in as the dialog plugin and restores the original on Return.

Run chain: `TimeTracker -> ShippingManager.onNewDay -> performShippingActions -> Shipment.create (ShippingCargoManager remove + CourierFleetCreator.createFleet) -> Shipment.init -> spawnFleet + CourierFleetAssignmentAI + ShippingIntel + chargePlayer -> (assignment end or failsafe) Shipment.finalizeAndRemove -> transfer`.

## Flow

### Scheduler

`ShippingManager.onNewDay()` (`ShippingManager.java:40-72`) does nothing unless a player market has a functional port. Per contract: invalid -> skip, and if active mark inactive and message; active -> `elapsedDays++`; `elapsedDays > recurrentDays` -> reset and ship. One time (`recurrentDays == 0`) active contracts are removed after shipping. "Ship now" sets `elapsedDays = 999`, so inactive contracts still ship the next day ("on demand") (`ContractListSidePanelCreator.java:101`). A shipment is only created if a dry run finds goods.

### Goods selection

`ShippingCargoManager.getTargetCargoFromOrigin` (`:15-117`): scope decides cargo and/or ships. Specific cargo takes up to the saved quantity per stack; specific ships match by variant id, then by hull or base hull. Items illegal in the destination submarket are skipped. When removing, it reports a `PlayerMarketTransaction` to the origin submarket so local resources stockpile cost applies (`:75-78`).

### Cost

`getTotalContractCost = shipCost + cargoCost + baseFee` (`ShippingCostCalculator.java:50-52`).
- Cargo: cargo space per stack (personnel and fuel count 0.75 per unit) x `costPerSpace`.
- Ships: hull deployment points (`getSuppliesToRecover()`) x `costPerShip` (`ShippingCostCalculator.java:97`).
- Both x distance mult `round((1 + ly * 1000^(1 + ly/250) / 10000) * 10) / 10`, x0.8 with beta core, x `costPerLyMult` (`:41-52`). Alpha core x0.9 on both parts.
- `chargePlayer` adds the cost to the upkeep of the `IndEvo_PrivatePort` node of the closest port market in the monthly report; without a port it subtracts credits directly (`ShippingManager.java:93-105`).

### Fleet and delivery

- `CourierFleetCreator.createFleet` sizes freighter, combat, tanker and liner points from the cargo, uses the origin faction's doctrine, then sets faction `IndEvo_couriers`, ignore and ignored flags and no auto despawn (`:24-99`).
- Same market: delivers at once, no fleet (`Shipment.java:55-62`).
- Failsafe: `7 + ceil(travel days * 3)` days; on expiry the goods are delivered anyway (`Shipment.java:75-87,212-221`).
- Every frame `Shipment.advance` reroutes if the destination market died: target storage of destination, else origin submarket, else origin storage, else the first player market (`:234-240`, `:190-208`).
- `transfer` puts items illegal on the target submarket into that market's storage instead, and moves stored marine XP with `PlayerFleetPersonnelTracker.transferPersonnel` (`:116-151`).

### Editor

`ContractSidePanelCreator.showPanel` redraws the whole panel after every button. Editing works on the live contract object; the first `showPanel` stores a copy as backup, Cancel puts the copy back and Confirm saves through `ShippingContractMemory.addOrReplaceContract`, which refuses invalid contracts (`ContractSidePanelCreator.java:958,988`, `ShippingContractMemory.java:26-48`). Origin markets: non-hostile markets with storage and a whitelisted submarket, the RAT settlement and restored Gacha stations as targets ([Gacha](../exploration/GACHA.md), [Compatibility](../core/COMPATIBILITY.md)).

## Contracts

- Contracts, shipments and the intel are saved: the list lives in sector memory, `ShippingManager` and each `Shipment` are persistent listeners, `Shipment` is also a sector script.
- The AI core effect is global and set by whichever port last ran `applyAICoreModifiers()` (`CourierPortV2.java:126-145`). Removing a core updates it on the next apply.
- Cost settings are read at use time, so contract screens and new shipments show a LunaLib change at once. A shipment fixes its cost when it is dispatched (`Shipment.java:52`).
- `ShippingContract` and `ShippingTargetHelper` import `assortment_of_things` classes directly; calls are guarded by `isModEnabled("assortment_of_things")` and the JVM resolves the class lazily (inferred). See [Compatibility](../core/COMPATIBILITY.md).
- The Embassy ambassador tracker reads `Shipment.cargo` and `Shipment.done` of every `Shipment` listener to find ambassadors in transit ([Embassy](EMBASSY.md#hire-and-transport)). Keep both fields and the listener registration.
- `ShippingContract.equals` compares ids but `hashCode` is not overridden; it is used as a `HashMap` key in `CourierPortV2.getShipmentWithDayList` (identity hash, works because the same objects are used).

## Known issues and dormant code

| Item | State |
|---|---|
| `CourierFleetCreator.createFleet` returns null | Suspected npe in `Shipment.init` (`spawnFleet`, `setFailsafeTimer`) when the factory returns an empty fleet (`CourierFleetCreator.java:85`) |
| Same market shipment | Calls `fleet.despawn` on a fleet that was never spawned (`Shipment.java:96`); intel added after finalize |
| Inactive one time contracts | Never removed by the scheduler, only by the player |
| `ShippingIntel.removeAll` | Console helper; comment names an old package path |
| Changelog_todo | "Allow courier to ship to superstructures for restoration", "Courier Port Option for settlements" |

## Maintenance

Update this doc when the cost formula, scopes, scheduler rules, entry rules or the submarket whitelist change. Side panel mechanics belong in [Side panel](../dialogue/SIDE_PANEL.md); submarket definitions in [Submarkets](../core/SUBMARKETS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
