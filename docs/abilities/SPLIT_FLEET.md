# Split fleet (detachments)

Covers the Manage Detachment ability: splitting the player fleet into up to two AI detachments, their behaviour modes and overrides, the detachment AI, ability AIs, combat losses and recovery. Read it before touching anything in `indevo/abilities/splitfleet/`. It does not cover the side panel framework the control panel is drawn with; see [Side panel](../dialogue/SIDE_PANEL.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The player ability `splinter_fleet` opens a control panel with two loadout slots (`DetachmentMemory.SLOTS`). Each loadout stores ship variants, cargo, a behaviour and a direction: Send (the picked ships and cargo leave) or Take (the player keeps the picked ships and cargo, everything else leaves). "Spawn" moves the detachment's ships, officers and cargo into a new player faction fleet ("Detachment N") that runs one `DetachmentAI` script. The script gives vanilla assignments for the base behaviour (stay, follow, hide, escort, intercept, transport) or for the highest active override (combat, dormant, head to player, stranded, return); the vanilla fleet AI does all travel, across systems included. Detachments return and merge on recall, go dormant when out of supplies, stop as stranded when out of fuel outside hyperspace, and leave recoverable wrecks with cryo-stored officers when they lose ships in battle.

## Files

| File | Owner / role |
|---|---|
| `indevo/abilities/splitfleet/SplinterAbilityPlugin.java` | Ability plugin (`abilities.csv`). `activateImpl` opens `AbilityPanelDialoguePlugin` (`:13`). Not usable during hyperspace transition (`:18`). |
| `indevo/abilities/splitfleet/SplinterFleetCampignPlugin.java` | Transient `BaseCampaignPlugin`. Picks `DetachmentDialoguePlugin` with `HIGHEST` for detachment targets not in `COMBAT` (`pickInteractionDialogPlugin`); picks detachment ability AIs with `MOD_SET` (`pickAbilityAI`). |
| `indevo/abilities/splitfleet/FleetUtils.java` | Spawn (`createAndSpawnFleet`, `:144`), merge (`mergeFleetWithPlayerFleet`, `:63`, contents in `moveContentsToPlayerFleet`, `:79`), Transport drop (`storeInMarket`, `:106`; `getClosestStorageMarket`, `:123`), loadout ship matching (`getFleetMembersForLoadout`, `:306`), Send/Take split (`staysWithPlayer` `:269`, `getDetachmentMembers` `:275`, `getTakenMembers` `:287`, `getDetachmentCargo` `:298`), cargo transfer, fake fleets for previews, AI lookup (`getAssignmentAI`, `:364`). Holds `DETACHMENT_IDENTIFIER_KEY` and the side quest claim (`SIDE_QUEST_CLAIM_KEY`, `:43`). |
| `indevo/abilities/splitfleet/OrbitFocus.java` | Creates invisible `SplinterFleet_OrbitFocus` entities and picks orbits for holding, hiding, dormancy and wreck fields. Also used by consumables and smoke clouds. |
| `fleetManagement/Behaviour.java` | `FleetBehaviour` enum (base modes, then overrides), `OVERRIDE_PRIORITY` (`:43`), index map for the UI, colours, tooltips; base behaviour and override list memory; `canSwitchLiveTo` rules out Transport for a live detachment (`:196`); `changeBehaviourAndUpdateAI` stores the base mode and tells the AI (`:200`). |
| `fleetManagement/DetachmentMemory.java` | Slot -> live `CampaignFleetAPI` map in sector memory. Owns the slot list `SLOTS` (`:13`). |
| `fleetManagement/DetachmentMarines.java` | Marine XP across split, cargo edit, merge and Transport storage drop: the detachment's share sits in the vanilla `PlayerFleetPersonnelTracker` under the detachment fleet entity, moved with `transferPersonnel(..., null)` (proportional XP). |
| `fleetManagement/LoadoutMemory.java` | Slot -> `Loadout` (variants, behaviour, target cargo, transport market, `takeMode`) in sector memory. In Take mode the variants and cargo are what the player keeps. |
| `fleetManagement/CombatAndDerelictionScript.java` | Per detachment `EveryFrameScript` + `FleetEventListener`. Despawn handling (`reportFleetDespawnedToListener`, `:86`): battle loss message, Transport storage drop, any other despawn returns ships and cargo to the player fleet, then clears the slot; after a battle with losses moves cargo, fuel and personnel over the remaining capacity into a wreck per lost ship with `OfficerAndShipRecoverySpecial`, and posts `RecoverableShipIntel` (`reportBattleOccurred`). `advance` only logs in dev mode. |
| `fleetAssignmentAIs/DetachmentAI.java` | The one AI script per detachment (`EveryFrameScript`). Sets and clears the automatic overrides each frame, applies the effective behaviour on change (`apply`, `:260`; `applyBase`, `:295`), runs escort and intercept targeting and the Transport market check (`act`, `:375`). Nested `MergeScript`, `HideArrivalScript`, `GoSlowScript`. |
| `abilityAIs/DetachmentSustainedBurnAbilityAI.java`, `DetachmentEburnAbilityPlugin.java`, `DetachmentGoDarkAbilityPlugin.java`, `DetachmentTransponderAbilityAI.java` | Ability AIs for detachments: mirror the player's sustained burn, go dark and transponder state; e-burn is a copy of vanilla `EmergencyBurnAbilityAI`. |
| `dialogue/AbilityPanelDialoguePlugin.java` | Control panel dialog. `refreshCustomPanel` builds a new `SplinterFleetSidePanelCreator` with itself; on close syncs loadout and live behaviour (`:61`). |
| `dialogue/SplinterFleetSidePanelCreator.java` | Builds the panel on its own visual panel (`showPanel`, `:55`): a Command row with one column per slot (status line, one button per mode, Recall or Cancel recall, Lay Course, Set Target; `addCommandColumn`, `:82`), then one block per slot (Send, Take, Members, Cargo, Spawn, ship icons, notes; `addSlotSection`, `:142`). Buttons carry a `Runnable` id run by `RunnableButtonPanelPlugin` on press. Owns spawn preconditions (`:165`). |
| `dialogue/TwoFleetFleetMemberPicker.java` | Ship picker for a loadout; hides the flagship and `SHIP_CAN_NOT_SCUTTLE` ships (`FleetUtils.staysWithPlayer`, `:24`), clears loadout cargo on pick (`:44`). Take mode accepts an empty pick. |
| `dialogue/TwoFleetCargoPicker.java` | Cargo picker between player fleet and a loadout preview or a live detachment; auto loads one month of supplies (within free cargo space), fuel and crew on first open. Refuses a pick that puts the detachment over cargo, fuel or personnel capacity and puts everything back (`pickedCargo`, `:93`). In Take mode the picked side is the player's ("You take") and the check runs on the rest ("Left behind"). |
| `dialogue/FleetStatus.java` | Capacity, upkeep, range and operating time numbers from a fake fleet; status tooltip with the override state (`getStateName`, `:110`). |
| `dialogue/DetachmentDialoguePlugin.java` | Dialog when the player interacts with a detachment: move cargo, change behaviour (Transport only while it is the current mode), merge, leave. |
| `dialogue/SplinterFleetDialoguePluginAPI.java` | Shared interface for the two dialog plugins (`showBaseOptions`, `getDialog`). |
| `intel/DetachmentIntel.java` | One intel per live detachment, created at spawn: location (map target is the fleet), mode, state, status lines, ships, Recall or Cancel recall button. `end(fleet, reason)` on merge and despawn shows the reason and ends after 3 days. |
| `intel/RecoverableShipIntel.java` | `FleetLogIntel` for a wreck field; removes itself when its wreck list is empty. |
| `salvageSpecials/OfficerAndShipRecoverySpecial.java` | Salvage special on detachment wrecks: thaw the officer, then recover the ship with condition based hits and d-mods. |
| `listeners/DetachmentAbilityAdder.java` | `NewDayListener` that grants the ability once the tutorial is over, then removes itself (`:26`). |
| `listeners/DetachmentPayroll.java` | `EconomyTickListener` that bills detachment crew, marines and officers into the monthly report. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Ability | `splinter_fleet` (`Ids.ABILITY_DETACHMENT`) | `data/campaign/abilities.csv:2`, `indevo/ids/Ids.java:141` |
| Custom entity | `SplinterFleet_OrbitFocus` (hidden), `development_SplinterFleet_OrbitFocus` (visible debug twin) | `data/config/custom_entities.json:420`, `:431` |
| Entity tag | `SplinterFleet_OF` | `OrbitFocus.ORBIT_FOCUS_TAG` |
| Memory, fleet | `$splinterFleetIdentifier` (is detachment), `$SplinterFleet_Use_ability` (ability the AI wants on) | `FleetUtils.java:38`, `:40` |
| Memory, fleet | `$SplinterFleetBehaviourMode` (base mode), `$IndEvo_detachmentOverrides` (active overrides, `ArrayList<FleetBehaviour>`) | `Behaviour.java:21`, `:22` |
| Memory, fleet | `$IndEvo_detachmentEnded` (despawn already handled, also set on merge) | `CombatAndDerelictionScript.java:44` |
| Memory, fleet | `$sirhartley_sideQuest` = `IndEvo`: shared side quest claim with Catch.Release, set at spawn and kept while dormant; their quests skip a fleet that has the key. Never rename. | `FleetUtils.java:43` |
| Memory, sector | `$SplinterFleetDetachmentStoreMemoryReference` (slot -> fleet), `$SplinterFleetLoadoutStoreMemoryReference` (slot -> loadout) | `DetachmentMemory.java:12`, `LoadoutMemory.java:13` |
| Memory, entity | `$IndEvo_derOrbitList` (wreck list on a focus), `$IndEvo_derTokenVarKey` (variant on a wreck) | `CombatAndDerelictionScript.java:42`, `:43` |
| Rules | none; dialogs are Java plugins | |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded` -> `DetachmentAbilityAdder.register()` (`ModPlugin.java:438`); persistent `NewDayListener`, deduped by `hasListenerOfClass`.
- `ModPlugin.setListenersIfNeeded` -> `DetachmentPayroll.register()` (`ModPlugin.java:439`); persistent `EconomyTickListener`, deduped by `hasListenerOfClass`.
- `ModPlugin.setScriptsIfNeeded` -> `registerPlugin(new SplinterFleetCampignPlugin())` on every load (`ModPlugin.java:493`); transient, so it is not saved.
- `abilities.csv` plugin column -> `SplinterAbilityPlugin`.
- Per detachment, attached at spawn and saved with the fleet: one `DetachmentAI` script (added by `Behaviour.changeBehaviourAndUpdateAI` when missing, `Behaviour.java:209`) and one `CombatAndDerelictionScript`, which adds itself as the fleet's `FleetEventListener`. Neither registers a sector listener. `FleetUtils.createAndSpawnFleet` adds one `DetachmentIntel` to the intel manager (`FleetUtils.java:222`).

Load order: `onGameLoad -> DetachmentAbilityAdder.register -> registerPlugin(SplinterFleetCampignPlugin) -> new day -> ability granted`.

Spawn order: `Spawn button -> FleetUtils.createAndSpawnFleet -> spawnFleet near player -> DetachmentMemory.addDetachment -> Behaviour.changeBehaviourAndUpdateAI -> new DetachmentAI -> CombatAndDerelictionScript`. The slot must be registered before the AI is set, because `DetachmentAI` reads the Transport market from the loadout by slot number.

## Flow

### Control panel

`SplinterAbilityPlugin.activateImpl -> AbilityPanelDialoguePlugin.init -> refreshCustomPanel -> new SplinterFleetSidePanelCreator(plugin).showPanel()`. The creator replaces the visual panel each time; no panel state outlives the dialog. Every button handler ends with `refreshCustomPanel`. Without fuel in the loadout the slot notes that the detachment can not leave the location. Mode buttons edit `Loadout.behaviour` for a slot without a detachment. For a live detachment they call `Behaviour.changeBehaviourAndUpdateAI` at once; Transport is disabled there (`Behaviour.canSwitchLiveTo`), all modes are disabled while dormant. Send/Take is only open before spawn; switching clears the slot's ships and cargo, since they flip meaning. Closing the panel or the detachment dialog still runs `Behaviour.updateActiveDetachmentBehaviour` (`Behaviour.java:217`) to sync loadout and fleet.

Spawn needs (`SplinterFleetSidePanelCreator.java:165`): at least one ship for the detachment, fewer ships than the player fleet, picked loadout cargo, non-empty detachment cargo within the detachment ships' cargo, fuel and personnel capacity, supplies >= repair cost + one month, not dormant, and for TRANSPORT a target market and fuel > distance x fuel per LY. Ships are matched by `getHullVariantId()` (first unused member per variant, `FleetUtils.getFleetMembersForLoadout`, `FleetUtils.java:306`), so duplicate hulls with the same variant id are interchangeable. The flagship and `SHIP_CAN_NOT_SCUTTLE` ships never match (`staysWithPlayer`). Send: the detachment gets the matched ships and the loadout cargo. Take: the player keeps the matched ships, the flagship and unscuttleable ships plus the loadout cargo; the detachment gets every other ship and a copy of the player cargo minus the loadout cargo (`getDetachmentCargo`, clamped by `CargoAPI.removeAll`). Spawn, the panel status and the loadout cargo picker use the same match, so previews only count ships present in the player fleet. Fake members copy the mothball state, CR and hull fraction (`createFakeFleetMember`, `FleetUtils.java:346`), so repair costs match the real ships; vanilla zeroes cargo, fuel, crew and supply upkeep for mothballed members. Live detachments are measured the same way, from fake copies of their members. Target markets come from `ShippingTargetHelper.getValidOriginPlanets()` (see [Courier port](../industries/COURIER_PORT.md)).

### Spawn and merge

`createAndSpawnFleet` (`FleetUtils.java:144`) builds the fleet with `createEmptyFleet(player, "Detachment N", false)` so it keeps fuel and supply use, marks each variant `REFIT` with no original variant and `TAG_NO_AUTOFIT` (stops variant revert and autofit purge on load, `:169`), strips non-AI officers for TRANSPORT (`:173`), moves officers out of the player fleet data, adds all AI-default abilities, transfers cargo, and sets memory flags: no ship recovery, non-hostile, never avoid player, skip transponder status.

`mergeFleetWithPlayerFleet` (`FleetUtils.java:63`) moves members and officers back (AI cores, the player and default persons are not re-added as officers), removes `TAG_NO_AUTOFIT`, adds cargo, reports despawn and removes the entity, then clears the slot.

### Behaviours and AI

`DetachmentAI.advance` (`DetachmentAI.java:148`) first updates the automatic overrides, then reads the effective behaviour (`Behaviour.getFleetBehaviour(fleet, false)`: the first of `OVERRIDE_PRIORITY` in the override list, else the base mode). When it differs from the applied one it calls `apply`, which resets the fleet (orbit, AI, assignments, AI mode off, transponder on, player faction, flags) and gives new vanilla assignments. `refresh()` forces a reapply on the next frame; the AI also refreshes when the fleet AI runs without an assignment.

| Index | Base mode | Assignment | Flags (`setFlags`) |
|---|---|---|---|
| 1 | STAY | `HOLD` on an orbit focus at the spot where the mode was set | visible only to player, ignores fleets |
| 2 | FOLLOW | `ORBIT_PASSIVE` player | same |
| 3 | HIDE | `GO_TO_LOCATION` hide focus, then orbit copy and AI removed (`HideArrivalScript`); heads back when moved more than 100 su | ignored by and ignores fleets, go dark |
| 4 | ESCORT | `ORBIT_PASSIVE` player, `INTERCEPT` for 3 days the player's hostile target, hostiles targeting the player, fleeing ones, or ones with FP < 1.6 x own (`engage`, `:406`); scans every 0.05 to 0.1 days, since no callback reports a fleet coming into view or targeting the player | visible only to player, ignores fleets |
| 5 | INTERCEPT | as escort, only the player's target and fleets targeting the player | same |
| 6 | TRANSPORT | `GO_TO_LOCATION` market, `ORBIT_PASSIVE` 3 days, `GO_TO_LOCATION_AND_DESPAWN`; the despawn listener stores ships and cargo. Picks the closest storage market when the target is gone (daily check). Not selectable for a live detachment. | ignored by and ignores fleets |

Overrides live in `$IndEvo_detachmentOverrides`; several can be active, the first in `OVERRIDE_PRIORITY` wins:

| Override (priority order) | Set and cleared by | Effect |
|---|---|---|
| `COMBAT` | `fleet.getBattle() != null`; cleared 3 s after the battle (`updateCombat`, `:168`) | assignments cleared, AI removed ("In combat") |
| `DORMANT` | supplies < 1, cleared when supplies >= 1 (`updateDormancy`, `:177`) | neutral faction, AI mode on (no supply use, accidents or crew needs), transponder off, mission important, AI removed ("Derelict"), orbit at the spot |
| `HEAD_TO_PLAYER_ON_TARGET` | the player targets the detachment in the same location; STAY only within 1000 su, Transport only while travelling (`updateHeadToPlayer`, `:190`) | `GO_TO_LOCATION` player, slow down for 3 s near them |
| `STRANDED` | fuel <= 0 outside hyperspace while the travel target is in another location; cleared once that changes (`updateStranded`, `:219`) | AI removed ("Out of fuel"), stops |
| `RETURN_TO_PLAYER_AND_MERGE` | Recall button, merge option, Transport without a market (`Behaviour.setReturning`) | `GO_TO_LOCATION` player, merge on arrival (`MergeScript`) |

The detachment dialog's "Leave" clears the player's interaction target and refreshes the AI (`DetachmentDialoguePlugin.java:125`). Dormancy ends only when supplies are moved in.

### Ability AIs

`SplinterFleetCampignPlugin.pickAbilityAI` returns detachment AIs for sustained burn, emergency burn, go dark and transponder when `$splinterFleetIdentifier` is set. The detachment AI requests go dark through `$SplinterFleet_Use_ability` (`useAbility`, `DetachmentAI.java:503`); go dark and transponder otherwise copy the player state when close.

### Payroll

Vanilla `CoreScript.reportEconomyTick` bills only the player fleet's cargo crew and marines and its `OfficerDataAPI` list. Detachment officers stay captains of their ships but leave the player fleet's officer list, so nothing billed them. `DetachmentPayroll.reportEconomyTick` adds each live detachment's crew and marines to the vanilla `CREW` and `MARINES` nodes under `FLEET` and each officer captain (not default, AI core or player) as a child of `OFFICERS` named "<officer> (Detachment N)", at the vanilla rates (`crewSalary`, `marineSalary`, `Misc.getOfficerSalary`) divided by `economyIterPerMonth`. Dormant detachments and the tutorial are not billed.

### Combat losses and recovery

`CombatAndDerelictionScript.reportBattleOccurred` creates an orbit focus at the fleet, moves the cargo overload (each stack gives its share), fuel over fuel capacity and crew, then marines, over personnel capacity into the wrecks of lost members, filling them in order with the last wreck taking the rest, spawns `Entities.WRECK` salvage entities with `OfficerAndShipRecoverySpecialData` (condition AVERAGE) and posts `RecoverableShipIntel`. The special thaws the officer first (`initOfficer`, `OfficerAndShipRecoverySpecial.java:82`), then offers recovery of a cloned variant with fresh hull id; skipping scuttles for weapons, wings and supplies.

## Contracts

- Vanilla `StrategicModule.dumpExcessCargoIfNeeded` (`obf/campaign/ai/StrategicModule.java:813`, every 0.5 to 1.5 days while the fleet AI runs) deletes fuel, personnel and cargo over the fleet's capacity. Capacity is the sum of member fuel, cargo and max crew (`FleetData.updateCargoCapacities`). The cargo pickers and spawn refuse anything over it (`FleetStatus.isOverCapacity`), and battle losses move the excess into wrecks before the AI runs again.
- Supply numbers: `FleetLogisticsAPI.getShipMaintenanceSupplyCost` is per day and adds CR recovery for recovering ships (`obf/campaign/fleet/LogisticsModule.java:215`). `FleetStatus.requiredSuppliesPerMonth` is the sum of the active members' `getSuppliesPerMonth()`, `suppliesToRecover` is `getTotalRepairAndRecoverySupplyCost()`, and operating time is (supplies - repairs) / monthly upkeep. Spawn and auto-load ask for repairs plus one month.
- Marine XP: `DetachmentMarines.move` takes the cargo counts from before the marines move, then the caller moves the cargo and calls `PlayerFleetPersonnelTracker.update()`. The live cargo picker rebuilds the player's count from the delta, because the picker has already moved the player's marines when `pickedCargo` runs. The tracker drops a holder whose entity is gone (`doCleanup`, `api/impl/PlayerFleetPersonnelTracker.java:610`); merge and Transport clear it at once.
- The Embassy ambassador tracker reads the cargo of every fleet in `DetachmentMemory.getFleetMap()` to find ambassadors carried by a detachment ([Embassy](../industries/EMBASSY.md#hire-and-transport)).
- `SectorAPI.reportFleetDespawned` only reaches global `CampaignEventListener`s (`CampaignEngine.reportFleetDespawned`, `obf/campaign/CampaignEngine.java:2149`); `removeEntity` calls no listener. `mergeFleetWithPlayerFleet` removes the slot itself. `CampaignFleetAPI.despawn` calls fleet `FleetEventListener`s (`obf/campaign/fleet/CampaignFleet.java:1599`): `GO_TO_LOCATION_AND_DESPAWN` reports `REACHED_DESTINATION` with the target entity, battles report `DESTROYED_BY_BATTLE`, and a fleet left without members reports `NO_MEMBERS` at the end of its own `advance` (`:848`). A merge from `MergeScript` runs inside that `advance`, so the merge sets `$IndEvo_detachmentEnded` first or the listener would report the empty fleet as lost.
- Cross-system travel is vanilla: `StrategicModule` plans jumps to an assignment target in another location, and player faction fleets know where the player jumped. With AI mode off it drops the plan at 0 fuel (`updateJumpPlanTo`); `NoFuelDriftScript` only covers hyperspace. `STRANDED` covers the rest.
- Lobotomizing is `setAI(null)` plus `setDoNotAdvanceAI(true)` (`DetachmentAI.lobotomize`); `reset()` recreates the AI with `Global.getFactory().createFleetAI`. Supply use (`LogisticsModule`) runs without a fleet AI too; only AI mode stops it, which is why dormancy turns AI mode on.
- Save data: both slot maps, `Loadout` objects (with `ShipVariantAPI` refs, a cargo and `takeMode`, which loads as `false` (Send) from older saves), the `DetachmentAI` (foci, Transport market id, applied mode), the override list and the `CombatAndDerelictionScript` persist. Each `DetachmentIntel` persists with a reference to its fleet until it is removed. The control panel and its button `Runnable`s are never saved. Saves with live detachments from before the AI rewrite reference removed AI classes: recall detachments before updating.
- Slots are `DetachmentMemory.SLOTS` (`1, 2`), used by both memory maps, `Behaviour.updateActiveDetachmentBehaviour` and the panel. Older saves keep a slot `3` entry in both maps; the panel never shows it, and `getNumForFleet` walks the stored map so a merge or despawn still clears it. Slot `0` from `getNumForFleet` means "not a live detachment".
- Every orbit focus is a new custom entity tagged `SplinterFleet_OF`; the dormancy focus is removed once its orbit is copied. `ModPlugin.onGameLoad -> OrbitFocus.removeUnusedOrbitFoci()` removes tagged foci that no entity orbits and no fleet assignment, interaction target, detachment AI (`addUsedEntities`: stay and hide focus) or `RecoverableShipIntel` points at. A focus a new script holds in a field must be added to `addUsedEntities`, or it is removed on the next load. Mine disabled areas use the same entity type without the tag and remove themselves.
- Cross feature: ship component loot skips detachments via `FleetUtils.isDetachmentFleet` (`indevo/items/listeners/ShipComponentLootManager.java:55`); `OrbitFocus` is reused by `BeaconAbilityPlugin`, `ScoopAbilityPlugin`, `SmokeCloudEntityPlugin` (see [Consumables](../items/CONSUMABLES.md)) and `MineBeltTerrainPlugin` spawns `SplinterFleet_OrbitFocus` entities (see [Minefields](../exploration/MINEFIELDS.md)). Market storage access uses `MiscIE.getStorageCargo` (see [Utils](../core/UTILS.md)).

## Known issues and dormant code

| Item | State |
|---|---|
| General | Changelog_todo: "Detachments are still bugged as fuck" |
| Other system | Changelog_todo: "Detachments refuse to move when in other system (??)"; travel is vanilla now, needs an in-game check |
| Officer limit | officers in a detachment leave the player fleet's officer list, so the player can hire more officers than the limit while detachments are out; merge puts them all back over the limit |
| Hiding at the sun | Changelog_todo: "? Detachments like to hide in system center (sun)"; `getAlternateHidingPosition` and the centred orbit fallback in `getClosestValidOrbit` orbit the system centre |

## Maintenance

Update this doc when a behaviour, override, memory key or slot count changes. Side panel framework facts belong in [Side panel](../dialogue/SIDE_PANEL.md); storage and courier target rules in [Courier port](../industries/COURIER_PORT.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
