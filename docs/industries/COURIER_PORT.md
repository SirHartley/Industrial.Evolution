# Courier Port

Covers the Courier Port industry (`IndEvo_PrivatePort`) and the shipping contract system: contract panel, daily scheduler, courier fleets, cost formula and shipping intel. Read it before changing contracts, shipping costs or courier fleet behaviour. It does not cover the UI kit the panel is built with; see [UI](../core/UI.md). Which submarkets exist is owned by [Submarkets](../core/SUBMARKETS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The Courier Port is a structure with no supply or demand. While the player owns at least one functional port, saved contracts move cargo and ships between whitelisted submarkets of any two non-hostile markets on a cadence (once, 14, 31, 93 or 186 days). On each due day the scheduler pulls the goods from the origin submarket, spawns a courier fleet that flies to the destination, and deposits the goods on arrival or when a failsafe timer runs out. Cost is booked on the "Shipping contracts" report line of the nearest port. The AI core installed in the most recently applied port is global: alpha cuts total cost, beta cuts the distance multiplier, gamma lets comm relays open the shipping menu.

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
| `indevo/industries/courierport/listeners/SubmarketCargoPicker.java`, `SubmarketShipPicker.java` | Vanilla cargo and fleet member pickers for selected goods; run a callback when closed |
| `indevo/industries/courierport/listeners/ShippingPlanetPicker.java` | Vanilla entity picker for the origin or destination planet |
| `indevo/industries/courierport/fleet/CourierFleetCreator.java` | Builds the courier fleet with `FleetFactoryV3` |
| `indevo/industries/courierport/fleet/CourierFleetAssignmentAI.java` | Load, travel, unload, despawn assignments |
| `indevo/industries/courierport/intel/ShippingIntel.java` | Intel per shipment, ends 7 days after delivery |
| `indevo/industries/courierport/dialogue/CourierPortDialoguePlugin.java` | Rule command and `InteractionDialogPlugin` that hosts the panel; picker round trip, ESC block, return to rules; static `show` |
| `indevo/industries/courierport/dialogue/CourierView.java` | Panel view state: selected contract, draft, step flags, scroll offsets; factories for entry points |
| `indevo/industries/courierport/dialogue/CourierPanelDelegate.java` | Custom dialog: header, AI core slot, contract list, New contract; rebuilds on every change |
| `indevo/industries/courierport/dialogue/CourierEditor.java` | Editor column: name, Active, route cards, storage tiles, goods, schedule, cost, Remove / Cancel / Save |
| `indevo/industries/courierport/dialogue/CourierPortIsDisplayInRelay.java` | Rule condition: gamma core installed |
| `indevo/industries/courierport/plugin/CourierPortOptionProvider.java` | Industry option "Visit the Courier Port..." |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_PrivatePort` (`Ids.PORT`) | `data/campaign/industries.csv:45`, `indevo/ids/Ids.java:73` |
| Faction | `IndEvo_couriers` (courier fleets) | `data/world/factions/IndEvo_couriers.faction` |
| Setting | `IndEvo_PrivatePort` (build toggle and new game placement) | `data/config/LunaSettings.csv:105` |
| Setting | `IndEvo_courier_costPerSpace` (5), `IndEvo_courier_costPerShip` (170), `IndEvo_courier_baseFee` (1000), `IndEvo_courier_costPerLyMult` (1.0) | `data/config/LunaSettings.csv:191-194`, read at use time in `ShippingCostCalculator` |
| Config CSV | `IndEvo_shipingList`: submarket whitelist | `data/config/settings.json:14`, `data/config/indEvo/shipping_submarkets_whitelist.csv` |
| Memory key | `$IndEvo_ShippingContainerStorage` (sector, contract list) | `ShippingContractMemory.java:11` |
| Memory key | `$IndEvo_CurrentShippingCore` (sector, last applied core id or `none`) | `ShippingCostCalculator.java:21` |
| Rules | `IndEvo_OpenShippingPortFromIndustryOption`, `IndEvo_ShippingMenuPoint`, `IndEvo_ShippingMenuPoint2`, `IndEvo_IntitiateShippingMenu`, `IndEvo_CommrelayShippingPoint`, `IndEvo_COB_shipping` | `data/campaign/rules.csv:816-830` |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded()`: `ShippingManager.getInstanceOrRegister()` (persistent listener, one instance) and `CourierPortOptionProvider.register()` (transient) (`indevo/utils/ModPlugin.java:438,472`).
- `NewGameIndustryPlacer.placeCourierPorts()` puts one port per faction per star system at new game when the setting is on (`indevo/utils/update/NewGameIndustryPlacer.java:100-126`).
- `NewDayListener` calls come from `TimeTracker`, which collects listeners from the listener manager and sector listeners; see [Utils](../core/UTILS.md).
- Entry points into the dialog: market menu and colony option point rules, the industry option (fires trigger `IndEvo_FireBaseSPOptionList` with `$IndEvo_closeDialogueOnNextReturn`), and comm relays when `CourierPortIsDisplayInRelay` is true. All end in `CourierPortDialoguePlugin.execute`, which swaps itself in as the dialog plugin and restores the original on Return. Code and tests open the panel with `CourierPortDialoguePlugin.show(entity, view)` (`CourierPortDialoguePlugin.java:47`), with a view from `CourierView.contract(id)` or `CourierView.newContract(fromMarketId, fromSubmarketId, toMarketId, toSubmarketId, scope)` (`CourierView.java:27,34`); null ids leave that step open, a null scope leaves the goods open. Return then dismisses the dialog.

Run chain: `TimeTracker -> ShippingManager.onNewDay -> performShippingActions -> Shipment.create (ShippingCargoManager remove + CourierFleetCreator.createFleet) -> Shipment.init -> spawnFleet + CourierFleetAssignmentAI + ShippingIntel + chargePlayer -> (assignment end or failsafe) Shipment.finalizeAndRemove -> transfer`.

## Flow

### Scheduler

`ShippingManager.onNewDay()` (`ShippingManager.java:39-71`) does nothing unless a player market has a functional port. Per contract: invalid -> skip, and if active mark inactive and message; active -> `elapsedDays++`; `elapsedDays > recurrentDays` -> reset and ship. One time (`recurrentDays == 0`) active contracts are removed after shipping. "Ship now" sets `elapsedDays = 999`, so inactive contracts still ship the next day ("on demand") (`CourierEditor.java:487-489`). A shipment is only created if a dry run finds goods.

### Goods selection

`ShippingCargoManager.getTargetCargoFromOrigin` (`:15-117`): scope decides cargo and/or ships. Specific cargo takes up to the saved quantity per stack; specific ships match by variant id, then by hull or base hull. Items illegal in the destination submarket are skipped. When removing, it reports a `PlayerMarketTransaction` to the origin submarket so local resources stockpile cost applies (`:75-78`).

### Cost

`getTotalContractCost = shipCost + cargoCost + baseFee` (`ShippingCostCalculator.java:50-52`).
- Cargo: cargo space per stack (personnel and fuel count 0.75 per unit) x `costPerSpace`.
- Ships: hull deployment points (`getSuppliesToRecover()`) x `costPerShip` (`ShippingCostCalculator.java:97`).
- Both x distance mult `round((1 + ly * 1000^(1 + ly/250) / 10000) * 10) / 10`, x0.8 with beta core, x `costPerLyMult` (`:41-52`). Alpha core x0.9 on both parts.
- `chargePlayer` books the cost on the "Shipping contracts" line of the closest port (`getClosestPort`, the origin faction's ports first, then any player port) through [BillingHelper](../core/UTILS.md#billinghelper). `onNewDay` only ships while the player owns a functional port, so a missing port throws.

### Fleet and delivery

- `CourierFleetCreator.createFleet` sizes freighter, combat, tanker and liner points from the cargo, uses the origin faction's doctrine, then sets faction `IndEvo_couriers`, ignore and ignored flags and no auto despawn (`:24-99`).
- Same market: delivers at once, no fleet (`Shipment.java:55-62`).
- Failsafe: `7 + ceil(travel days * 3)` days; on expiry the goods are delivered anyway (`Shipment.java:75-87,212-221`).
- Every frame `Shipment.advance` reroutes if the destination market died: target storage of destination, else origin submarket, else origin storage, else the first player market (`:234-240`, `:190-208`).
- `transfer` puts items illegal on the target submarket into that market's storage instead, and moves stored marine XP with `PlayerFleetPersonnelTracker.transferPersonnel` (`:116-151`).

### Panel

One custom dialog (`CourierPanelDelegate`, 1104 x 709 via `UiFrame`) with the contract list on the left and the editor on the right. Every change rebuilds the panel from `CourierView`; scroll offsets survive the rebuild.

- The editor works on `view.draft`, a copy of the stored contract (`CourierPanelDelegate.java:141-142`). Save writes it through `ShippingContractMemory.addOrReplaceContract`, which refuses invalid contracts and replaces by id in place (`ShippingContractMemory.java:26-42`). Cancel, Return and picking another row drop the draft. Active and Ship now write the stored contract at once (`CourierEditor.java:87-90,487-489`).
- Steps: route (From card, storage tiles, then To), goods (scope or selected cargo and ships), then schedule and cost once the goods are chosen and the draft is valid. Save shows only then. A stored invalid contract shows the reason and Remove only.
- Route card: planet, market name (insignia 21 beside Change...; insignia 15 when it does not fit, wrapped to two lines in place of the size line when still too long), size and system line.
- Origin storages: non-hostile markets with storage and a whitelisted submarket, plus Resource Stockpiles on player colonies; the RAT settlement and restored Gacha stations as targets ([Gacha](../exploration/GACHA.md), [Compatibility](../core/COMPATIBILITY.md)). More tiles than fit one row collapse into a `+N` tile that expands the rows.
- Cost lines use `ShippingCostCalculator.getShipCostForVariants`, `getCargoSpace` and `getDeploymentPoints` on the draft's cargo and variant list; by load scopes show rates, the total is known on the day.

### Picker round trip

The planet, cargo and ship pickers are vanilla sub dialogs and only open while the interaction dialog has no other sub dialog (`obf/ui/newui/_c192.java:989-1068`). The custom dialog clears that state only after its dismiss returns (`obf/ui/newui/super.java:115-127`, `obf/ui/_c199.java:137-150`, `_c192.java:345-367`).

- `openPicker` unsets `BLOCK_ESC`, closes the panel with `dismissCustomDialog(1)` while `picking` is set (so `customDialogCancel` does not leave), then opens the picker (`CourierPortDialoguePlugin.java:96-100`, `CourierPanelDelegate.java:83-98`).
- Pickers call their listener before their own dismiss (`obf/ui/newui/CampaignEntityPickerDialog.java:268-295`), so the callback only sets `reopenPending` and `advance` shows the panel again a frame later (`CourierPortDialoguePlugin.java:102-104,135-148`).
- A cancelled picker keeps the previous selection. Return to rules runs from `advance` too: rules must not fire from inside the custom dialog's dismiss.

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
| Planet icons | List rows and route cards draw `showPlanetInfo` planets at 14 and 56 px; cost with 60 contracts unmeasured |
| `ShippingIntel.removeAll` | Console helper; comment names an old package path |
| Changelog_todo | "Allow courier to ship to superstructures for restoration", "Courier Port Option for settlements" |

## Maintenance

Update this doc when the cost formula, scopes, scheduler rules, entry rules or the submarket whitelist change. UI kit helpers belong in [UI](../core/UI.md); submarket definitions in [Submarkets](../core/SUBMARKETS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
