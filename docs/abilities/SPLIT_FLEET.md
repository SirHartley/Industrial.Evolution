# Split fleet (detachments)

Covers the Manage Detachment ability: splitting the player fleet into up to three AI detachments, their behaviour modes, assignment AIs, ability AIs, combat losses and recovery. Read it before touching anything in `indevo/abilities/splitfleet/`. It does not cover the side panel framework the control panel is drawn with; see [Side panel](../dialogue/SIDE_PANEL.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The player ability `splinter_fleet` opens a control panel with three loadout slots. Each loadout stores ship variants, cargo and a behaviour. "Spawn" moves the matching ships, officers and cargo into a new player faction fleet ("Detachment N") that runs one assignment AI script per behaviour (stay, follow, hide, escort, intercept, transport). Detachments return and merge on recall, go dormant when out of supplies (or fuel in hyperspace), and leave recoverable wrecks with cryo-stored officers when they lose ships in battle. The live AI generation is V2 (`BaseSplinterFleetAssignmentAIV2`); V3 is an empty stub.

## Files

| File | Owner / role |
|---|---|
| `indevo/abilities/splitfleet/SplinterAbilityPlugin.java` | Ability plugin (`abilities.csv`). `activateImpl` opens `AbilityPanelDialoguePlugin` (`:13`). Not usable during hyperspace transition (`:18`). |
| `indevo/abilities/splitfleet/SplinterFleetCampignPlugin.java` | Transient `BaseCampaignPlugin` (`:33`). Picks `DetachmentDialoguePlugin` with `HIGHEST` for detachment targets not in `COMBAT` (`:38`); picks detachment ability AIs with `MOD_SET` (`:49`). `clear()` (`:80`) unsets both stores, nothing calls it. |
| `indevo/abilities/splitfleet/FleetUtils.java` | Spawn (`createAndSpawnFleet`, `:86`), merge (`mergeFleetWithPlayerFleet`, `:51`), cargo transfer, fake fleets for previews, assignment AI lookup and swap (`:260`, `:270`). Holds `DETACHMENT_IDENTIFIER_KEY`. |
| `indevo/abilities/splitfleet/OrbitFocus.java` | Creates invisible `SplinterFleet_OrbitFocus` entities and picks orbits for holding, hiding, dormancy and wreck fields. Also used by consumables and smoke clouds. |
| `fleetManagement/Behaviour.java` | `FleetBehaviour` enum, index map for the UI, colours, tooltips; base behaviour and override memory; `changeBehaviourAndUpdateAI` maps behaviour to AI class (`:196`). |
| `fleetManagement/DetachmentMemory.java` | Slot `1..3` -> live `CampaignFleetAPI` map in sector memory. |
| `fleetManagement/LoadoutMemory.java` | Slot `1..3` -> `Loadout` (variants, behaviour, target cargo, transport market) in sector memory. |
| `fleetManagement/CombatAndDerelictionScript.java` | Per detachment `EveryFrameScript` + `FleetEventListener`. Despawn messages and slot cleanup (`:80`); after a lost battle spawns wrecks with `OfficerAndShipRecoverySpecial` and `RecoverableShipIntel` (`:97`). `advance` only logs in dev mode (`:63`). |
| `fleetManagement/FleetDereliction.java` | Older copy of `addDerelict`/`leaveCargoInPods`. Dead. |
| `fleetAssignmentAIs/SplinterFleetAssignmentAIV2.java` | Interface for the assignment AI script (`setDone`, `setFlags`). |
| `fleetAssignmentAIs/BaseSplinterFleetAssignmentAIV2.java` | Live AI base, extends vanilla `BaseAssignmentAI`, also `CampaignEventListener` and `GateTransitListener`. Combat, dormancy, override modes, jump following, return and merge. |
| `fleetAssignmentAIs/StayAssignmentAI.java`, `FollowAssignmentAI.java`, `HideAssignmentAI.java`, `EscortAssignmentAI.java`, `InterceptAssignmentAI.java`, `TransportAssignmentAI.java` | Live behaviour AIs (one per selectable mode). |
| `fleetAssignmentAIs/DeliverAssignmentAI.java` | DELIVER mode AI. Reachable in code but not selectable (index map entry commented out, `Behaviour.java:53`). Dormant. |
| `fleetAssignmentAIs/PatrolAssignmentAI.java` | Empty patrol AI, never constructed (`Behaviour.java:211`). Dead. |
| `fleetAssignmentAIs/ForceSingleJumpScript.java` | `@Deprecated`, unreferenced. Dead. |
| `fleetAssignmentAIs/JumpInfo.java` | Queued jump (from token, `JumpDestination`). `isRegularJump()` means `from` is a `JumpPointAPI`. |
| `fleetAssignmentAIs/V3/BaseDetachmentFleetAssignmentAIV3.java` | Comment-only design stub (follow a token that tracks the player). Dead. |
| `abilityAIs/DetachmentSustainedBurnAbilityAI.java`, `DetachmentEburnAbilityPlugin.java`, `DetachmentGoDarkAbilityPlugin.java`, `DetachmentTransponderAbilityAI.java` | Ability AIs for detachments: mirror the player's sustained burn, go dark and transponder state; e-burn is a copy of vanilla `EmergencyBurnAbilityAI`. |
| `dialogue/AbilityPanelDialoguePlugin.java` | Control panel dialog. Stores itself in sector memory under `$SplinterFleet_DialoguePlugin` (expire 0) so panel button callbacks can find it (`:43`). On close applies loadout behaviour changes (`:73`). |
| `dialogue/SplinterFleetSidePanelCreator.java` | Builds the three slot rows (Members, Cargo, `<` behaviour `>`, Set Target or Lay Course, Spawn/Recall/Cancel) on `VisualCustomPanel`. Owns spawn preconditions (`:376`). |
| `dialogue/TwoFleetFleetMemberPicker.java` | Ship picker for a loadout; hides `SHIP_CAN_NOT_SCUTTLE` ships (`:27`), clears loadout cargo on pick (`:45`). |
| `dialogue/TwoFleetCargoPicker.java` | Cargo picker between player fleet and a loadout preview or a live detachment; auto loads one month of supplies, fuel and crew on first open (`:166`). |
| `dialogue/FleetStatus.java` | Capacity, upkeep, range and operating time numbers from a fake fleet. |
| `dialogue/DetachmentDialoguePlugin.java` | Dialog when the player interacts with a detachment: move cargo, change behaviour, merge, leave. |
| `dialogue/SplinterFleetDialoguePluginAPI.java` | Shared interface for the two dialog plugins (`showBaseOptions`, `getDialog`). |
| `dialogue/DialogueInterceptListener.java` | Swaps a `FleetInteractionDialogPluginImpl` on a detachment for `DetachmentDialoguePlugin`. Never registered. Dead. |
| `intel/RecoverableShipIntel.java` | `FleetLogIntel` for a wreck field; removes itself when its wreck list is empty. |
| `intel/DerelictFleetLocationIntel.java` | "Dormant Detachment" intel. Never constructed. Dead. |
| `salvageSpecials/OfficerAndShipRecoverySpecial.java` | Salvage special on detachment wrecks: thaw the officer, then recover the ship with condition based hits and d-mods. |
| `listeners/DetachmentAbilityAdder.java` | `NewDayListener` that grants the ability once the tutorial is over, then removes itself (`:26`). |
| `locationFollower/PlayerFleetFollower.java`, `LocationFollowListenerAPI.java` | Token that tracks the player fleet across locations. `register()` is commented out (`ModPlugin.java:531`); only `getToken()` is used by the dormant DELIVER AI. Dormant. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Ability | `splinter_fleet` (`Ids.ABILITY_DETACHMENT`) | `data/campaign/abilities.csv:2`, `indevo/ids/Ids.java:141` |
| Custom entity | `SplinterFleet_OrbitFocus` (hidden), `development_SplinterFleet_OrbitFocus` (visible debug twin) | `data/config/custom_entities.json:420`, `:431` |
| Entity tag | `SplinterFleet_OF` | `OrbitFocus.ORBIT_FOCUS_TAG` |
| Fleet tag | `SplinterFleet_DetachmentIsJumping` | `BaseSplinterFleetAssignmentAIV2.CURRENTLY_JUMPING_FLAG` |
| Memory, fleet | `$splinterFleetIdentifier` (is detachment), `$SplinterFleet_Use_ability` (ability the AI wants on) | `FleetUtils.java:30`, `:32` |
| Memory, fleet | `$SplinterFleetBehaviourMode`, `$SplinterFleetBehaviourModeOverride` | `Behaviour.java:23`, `:24` |
| Memory, sector | `$SplinterFleetDetachmentStoreMemoryReference` (slot -> fleet), `$SplinterFleetLoadoutStoreMemoryReference` (slot -> loadout) | `DetachmentMemory.java:12`, `LoadoutMemory.java:13` |
| Memory, sector | `$SplinterFleet_DialoguePlugin` (panel instance, expire 0), `$IndEvo_followToken` (dormant follower) | `AbilityPanelDialoguePlugin.java:19`, `PlayerFleetFollower.java:14` |
| Memory, entity | `$IndEvo_derOrbitList` (wreck list on a focus), `$IndEvo_derTokenVarKey` (variant on a wreck) | `CombatAndDerelictionScript.java:37`, `:38` |
| Unused keys | `$SplinterFleet_IsReturning`, `$SplinterFleet_DetachmentLocation_` | `Behaviour.java:25`, `DetachmentMemory.java:13` |
| Rules | none; dialogs are Java plugins | |

## Registration and lifecycle

- `ModPlugin.setListenersIfNeeded` -> `DetachmentAbilityAdder.register()` (`ModPlugin.java:463`); persistent `NewDayListener`, deduped by `hasListenerOfClass`.
- `ModPlugin.setScriptsIfNeeded` -> `registerPlugin(new SplinterFleetCampignPlugin())` on every load (`ModPlugin.java:518`); transient, so it is not saved.
- `abilities.csv` plugin column -> `SplinterAbilityPlugin`.
- Per detachment, attached at spawn and saved with the fleet: one `SplinterFleetAssignmentAIV2` script and one `CombatAndDerelictionScript` (`FleetUtils.java:167`, `:168`). The AI adds itself as a sector `CampaignEventListener` on first `advance` (`regsterIfNeeded`, `BaseSplinterFleetAssignmentAIV2.java:487`) and removes itself when done (`:127`).

Load order: `onGameLoad -> DetachmentAbilityAdder.register -> registerPlugin(SplinterFleetCampignPlugin) -> new day -> ability granted`.

Spawn order: `Spawn button -> FleetUtils.createAndSpawnFleet -> spawnFleet near player -> DetachmentMemory.addDetachment -> Behaviour.changeBehaviourAndUpdateAI -> FleetUtils.setOrReplaceAssignmentAI -> CombatAndDerelictionScript`. The slot must be registered before the AI is set, because `TransportAssignmentAI` reads the loadout by slot number (`FleetUtils.java:163`).

## Flow

### Control panel

`SplinterAbilityPlugin.activateImpl -> AbilityPanelDialoguePlugin.init -> SplinterFleetSidePanelCreator.showPanel`. Every button rebuilds the whole panel through `refreshCustomPanel`. Behaviour arrows edit `Loadout.behaviour` only; for a live detachment the change is applied on "Return" via `Behaviour.updateActiveDetachmentBehaviour` (`Behaviour.java:241`).

Spawn needs (`SplinterFleetSidePanelCreator.java:376`): ships picked and at least one present, fewer ships than the player fleet, non-empty loadout cargo, supplies >= repair cost + one month, not dormant, and for TRANSPORT a target market and fuel > distance x fuel per LY. Ships are matched by `getHullVariantId()` (first unused member per variant, `FleetUtils.java:101`), so duplicate hulls with the same variant id are interchangeable. Target markets come from `ShippingTargetHelper.getValidOriginPlanets()` (see [Courier port](../industries/COURIER_PORT.md)).

### Spawn and merge

`createAndSpawnFleet` (`FleetUtils.java:86`) builds the fleet with `createEmptyFleet(player, "Detachment N", false)` so it keeps fuel and supply use, marks each variant `REFIT` with no original variant and `TAG_NO_AUTOFIT` (stops variant revert and autofit purge on load, `:112`), strips non-AI officers for TRANSPORT (`:123`), moves officers out of the player fleet data, adds all AI-default abilities, transfers cargo, and sets memory flags: no ship recovery, non-hostile, never avoid player, skip transponder status.

`mergeFleetWithPlayerFleet` (`FleetUtils.java:51`) moves members and officers back (AI cores, the player and default persons are not re-added as officers), removes `TAG_NO_AUTOFIT`, adds cargo, reports despawn and removes the entity, then clears the slot.

### Behaviours and AI

| Index | Behaviour | AI class | Assignment | Flags (`setFlags`) |
|---|---|---|---|---|
| 1 | STAY | `StayAssignmentAI` | `HOLD` on an orbit focus at spawn spot | visible only to player, ignores fleets |
| 2 | FOLLOW | `FollowAssignmentAI` | `ORBIT_PASSIVE` player, follows jumps | same |
| 3 | HIDE | `HideAssignmentAI` | `GO_TO_LOCATION` hide focus, then orbit copy and AI removed | ignored by and ignores fleets, go dark |
| 4 | ESCORT | `EscortAssignmentAI` | `ORBIT_PASSIVE` player, `INTERCEPT` hostiles with FP < 1.6 x own, fleeing ones, ones targeting the player, or the player's target | visible only to player, ignores fleets |
| 5 | INTERCEPT | `InterceptAssignmentAI` | as escort, only fleets targeting the player or the player's target | same |
| 6 | TRANSPORT | `TransportAssignmentAI` | go to market, orbit 3 days, `GO_TO_LOCATION_AND_DESPAWN`; script puts cargo and ships into market storage | ignored by and ignores fleets |
| (7) | DELIVER | `DeliverAssignmentAI` | deliver cargo, keep fuel and supplies for the trip back, return to player | not selectable |

Override modes live in `$SplinterFleetBehaviourModeOverride` and are acted on by `BaseSplinterFleetAssignmentAIV2.doOverrideActions` (`:265`), which runs on the edge where the override appears or clears:

| Override | Set by | Effect |
|---|---|---|
| `RETURN_TO_PLAYER_AND_MERGE` | Recall button, merge option | `GO_TO_LOCATION` player, merge on arrival (`:292`); follows queued jumps |
| `DORMANT` | supplies < 1, or in hyperspace with fuel < 1 (`:244`) | neutral faction, AI removed ("Derelict"), transponder off, mission important, orbit at spot |
| `COMBAT` | `fleet.getBattle() != null` (`:214`) | AI removed ("In Combat"); cleared 3 s after battle ends (`BATTLE_RELEASE_TIME`) |
| `HEAD_TO_PLAYER_ON_TARGET` | player targets the detachment (`:368`) | move to player and slow down for 3 s near them |
| `CARGO_DETACHMENT_CHEAT_MODE` | DELIVER fleet out of fuel after delivering | AI kept, AI mode off |

Clearing any override calls `giveInitialAssignments()` again, which resets AI, faction, transponder and flags and calls `pickNext()`. Dormancy clears when supplies >= 1 (and fuel >= 1 in hyperspace, `:232`). The "Leave" option of the detachment dialog adds 1 supply when empty and clears the override (`DetachmentDialoguePlugin.java:132`).

### Following across locations

Follow, escort and intercept queue the player's jumps in `jumpInfoMap` from `reportFleetJumped`. `checkForJumpActions` (`:184`) runs the lowest queued jump with `SectorAPI.doHyperspaceTransition`. `goToPlayerInOtherSystemActions` (`:395`) handles a detachment left behind: exits through `Misc.getDistressJumpPoint` or a hyperspace token, enters through a hyperspace jump point, or brute forces a jump to the largest planet or a temporary focus.

### Ability AIs

`SplinterFleetCampignPlugin.pickAbilityAI` returns detachment AIs for sustained burn, emergency burn, go dark and transponder when `$splinterFleetIdentifier` is set. The assignment AI requests go dark through `$SplinterFleet_Use_ability` (`useAbility`, `BaseSplinterFleetAssignmentAIV2.java:387`); go dark and transponder otherwise copy the player state when close.

### Combat losses and recovery

`CombatAndDerelictionScript.reportBattleOccurred` (`:97`) creates an orbit focus at the fleet, moves overload cargo into the wrecks of lost members, spawns `Entities.WRECK` salvage entities with `OfficerAndShipRecoverySpecialData` (condition AVERAGE) and posts `RecoverableShipIntel`. The special thaws the officer first (`initOfficer`, `OfficerAndShipRecoverySpecial.java:82`), then offers recovery of a cloned variant with fresh hull id; skipping scuttles for weapons, wings and supplies.

## Contracts

- `SectorAPI.reportFleetDespawned` only reaches global `CampaignEventListener`s (`CampaignEngine.reportFleetDespawned`, `obf/campaign/CampaignEngine.java:2149`). The comment at `FleetUtils.java:80` saying it handles slot removal is wrong; `mergeFleetWithPlayerFleet` removes the slot itself (`:83`). `CampaignFleetAPI.despawn` does call fleet `FleetEventListener`s (`obf/campaign/fleet/CampaignFleet.java:1599`), which is how transport and battle losses clear the slot.
- `GateTransitListener` is dispatched only to listeners in the `ListenerManager` (`ListenerUtil.reportFleetTransitingGate`, `api/campaign/listeners/ListenerUtil.java:268`). The AI is only added through `SectorAPI.addListener`, so the gate overrides never fire (the TODO at `BaseSplinterFleetAssignmentAIV2.java:147` asks exactly this). Gate jumps are still seen via `reportFleetJumped`.
- `BaseAssignmentAI.advance` calls `pickNext()` whenever the fleet has no assignment (`api/impl/campaign/procgen/themes/BaseAssignmentAI.java:66`). Behaviour AIs return early from `pickNext` when the fleet AI is null, which is how dormancy, combat and hiding stop reassignment.
- Save data: both slot maps, `Loadout` objects (with `ShipVariantAPI` refs and a cargo), the AI script, its `jumpInfoMap` and the `CombatAndDerelictionScript` persist. `AbilityPanelDialoguePlugin` and `VisualCustomPanel` state do not.
- Slot numbers `1, 2, 3` are hardcoded in `DetachmentMemory`, `LoadoutMemory`, `Behaviour.updateActiveDetachmentBehaviour` and the panel. Slot `0` from `getNumForFleet` means "not a live detachment".
- Every orbit focus is a new custom entity and none are removed (inferred from no `removeEntity` call on them outside `PlayerFleetFollower`).
- Cross feature: ship component loot skips detachments via `FleetUtils.isDetachmentFleet` (`indevo/items/listeners/ShipComponentLootManager.java:57`); `OrbitFocus` is reused by `BeaconAbilityPlugin`, `ScoopAbilityPlugin`, `SmokeCloudEntityPlugin` (see [Consumables](../items/CONSUMABLES.md)) and `MineBeltTerrainPlugin` spawns `SplinterFleet_OrbitFocus` entities (see [Minefields](../exploration/MINEFIELDS.md)). Market storage access uses `MiscIE.getStorageCargo` (see [Utils](../core/UTILS.md)).

## Known issues and dormant code

| Item | State |
|---|---|
| V3 AI (`V3/BaseDetachmentFleetAssignmentAIV3`) | comment stub, dead |
| `DeliverAssignmentAI`, `PlayerFleetFollower` | dormant: DELIVER not in `behaviourIndexMap`, follower never registered; Changelog_todo "DEV Added - Behaviour: Deliver", "DEV Deliver fleets still refuse to leave system" |
| `PatrolAssignmentAI`, `ForceSingleJumpScript`, `FleetDereliction`, `DialogueInterceptListener`, `DerelictFleetLocationIntel`, `SplinterFleetCampignPlugin.clear()` | dead, unreferenced |
| Gate transit handling | never called, see Contracts; `reportFleetTransitingGate` also checks the player fleet's memory instead of `this.fleet` (`BaseSplinterFleetAssignmentAIV2.java:153`) |
| Overload drop loop | `while (overloadedByAmount > 0 \|\| safetyCounter > stacks.size())`: the safety check is inverted and never stops the loop; `picker.isEmpty()` is the only exit (`CombatAndDerelictionScript.java:143`) |
| Supply transfer | `Math.max(toFleet.totalCargoCap, fromFleet.currentSupplies / 2f)` should likely be `min`, so a detachment can be sent more supplies than it holds (`TwoFleetCargoPicker.java:175`) |
| Escort and intercept AI | `fleet.getCurrentAssignment().getAssignment()` without a null check, NPE when the fleet has no assignment (`EscortAssignmentAI.java:55`, `InterceptAssignmentAI.java:55`) |
| `OrbitFocus` entities | Created on every behaviour change and never removed (inferred leak) |
| General | Changelog_todo: "Detachments are still bugged as fuck" |
| Marine XP | Changelog_todo: "Marine xp get wiped with detachments"; the transaction based fix is disabled with `if (false)` (`TwoFleetCargoPicker.java:75`) |
| Crew over capacity | Changelog_todo: "Detachments eat crew that is in excess of capacity" |
| Other system | Changelog_todo: "Detachments refuse to move when in other system (??)" |
| Hiding at the sun | Changelog_todo: "? Detachments like to hide in system center (sun)"; `getAlternateHidingPosition` and the centred orbit fallback in `getClosestValidOrbit` orbit the system centre |

## Maintenance

Update this doc when a behaviour, override, memory key or slot count changes, or when the V3 AI replaces V2. Side panel framework facts belong in [Side panel](../dialogue/SIDE_PANEL.md); storage and courier target rules in [Courier port](../industries/COURIER_PORT.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
