# Industrial.Evolution new features

Tracking doc for new features, kept like the improvement targets doc: numbered items, each with a status line, the plan, the decisions and the open questions. Plan only, no code. Baseline: IE test branch `dev` at `b302f98`, Catch.Release (CR) `master` at `ee308ef` (2026-11-06).

- Engine facts: starsector-knowledge skill, Starsector 0.98a-RC8. Rules and dialogue: starsector-rules-writing skill.
- Code paths are relative to `jars/src/` of the mod named; `data/...` paths are from the repo root.
- Project files referenced here: `IMPROVEMENT_TARGETS.md` (IE reworks), `DISTRESS_REWORK_PLAN.md` (distress framework port from CR).
- Nothing here was tested in game. "Confirmed" means traced in code, "inferred" means a reasoned guess.
- Standing rules: never patch other mods; CR is Philipp's own, so CR changes are fine once he says so. No class from one mod is referenced by the other. IE stays Java only. Saves may break; items only note it.

## 1. Planned features

### 1.1 Fleet side quests: lockout with Catch.Release

**Status: route decided, memory contract in both mods (Philipp, 2026-11-07). Not implemented.**

Goal: IE and CR both hand side quests to NPC fleets. With both installed, they must never put two quests on one fleet, and should not stack offers in one system.

What CR does today (confirmed in code):

- `FleetQuestSpawner` (transient `EveryFrameScript`): every 3 to 7 days, 7% x frequency setting, at most one open offer or accepted fleet job (`MAX_ACTIVE`), 45 day sector cooldown `$catchrelease_fleetQuestCooldown` (`FleetQuestSpawner.java:46-50`, `:168`, `:193`). It adopts an existing fleet in the player's star system: scavengers, small trade convoys, and dedicated givers (Hegemony and Diktat convoys, Tri-Tachyon prospectors, expeditions, pirate base patrols).
- Giver filter `getGiverFailure` (`:414-456`): skips CR quest fleets, the Fisherman chain captain, any fleet or commander with `MemFlags.ENTITY_MISSION_IMPORTANT`, fleets returning to despawn, hostile and player faction fleets, stations.
- An offer writes `$catchrelease_fleetQuest` and text keys to fleet memory (`FleetQuest.writeDialogueMemory`, `FleetQuest.java:680`) and draws CR's own marker. It does not set vanilla importance. Rules enter through `catchrelease_fleetQuestHail` (`BeginFleetEncounter`) and `catchrelease_fleetQuestPitch` (`OpenCommLink`, score 1200). Acceptance swaps the fleet for a mission owned copy (`FleetQuest.supplant`, `:239`); `release` (`:1285`) clears the keys.
- CR's own fleets: distress callers are vanilla important (`DistressCallManager.java:334`). The chain captain (`$catchrelease_t2Captain`, independent `scavengerSmall` in an empty system) has no vanilla importance. Expedition and prospecting fleets carry `$catchrelease_scienceExpedition` and `$catchrelease_prospectingFleet`.

Where IE and CR collide (both installed):

1. Same fleet. IE cannot see a CR offer (no vanilla importance), and the chain captain is exactly the fleet IE's first quest wants (independent scavenger outside the core). With two quest flags on one fleet both mods' `OpenCommLink` rows match, the higher score wins and the other quest hangs (rules-language.md, `FireBest`).
2. Same time. Each mod has its own timer and cap, so two offers can sit in one system, or come back to back.
3. IE's own fleets are safe from CR today: the roider tutorial fleet is important (`TutorialEncounterScript.java:84`), poor spacer and bounty hunter fleets are hostile, parade fleets are player faction. Courier fleets (`IndEvo_couriers`) only pass because no CR quest type matches their faction and flags.

Route: a lockout contract in memory, the same strings in both mods, plain values only (String, Boolean, Long). Same pattern as the Skillshot lock (`IMPROVEMENT_TARGETS.md` 1.8) and the distress lock (`DISTRESS_REWORK_PLAN.md` 3.4).

| Key | Memory | Value | Written by | Read by |
|---|---|---|---|---|
| `$sirhartley_sideQuest` | fleet | owner mod id: `IndEvo` or `catchrelease` | A mod that puts an offer, a running side quest or a scripted role on the fleet, incl. its own special fleets | Every adopter: skip a fleet that has it, any value |
| `$sirhartley_sideQuestOffer` | fleet | `true` while an offer is open | Offer start; unset on accept, decline, expiry, release | Spawner: no new offer in a location where any fleet has it |
| `$sirhartley_sideQuestLast` | sector | `Long`, `clock.getTimestamp()` of the last offer from either mod | Offer start | Spawner: no roll while `getElapsedDaysSince(ts) < 7` |

- Both mods also treat `ENTITY_MISSION_IMPORTANT` on the fleet or its commander as taken (CR already does, `FleetQuestSpawner.java:447-452`).
- A mod unsets `$sirhartley_sideQuest` only when the value is its own id.
- No memory expiry on these keys: fleet memory expiry only ticks while the fleet's location advances (starsector-knowledge `docs/concepts/rules-engine.md`). Keys are unset explicitly and the gap uses a timestamp, as in the distress lock.
- Stale claims: a mod removed mid save leaves claims on fleets; they go when the fleet despawns. Accepted.
- Rule ids, memory keys and settings stay per mod (`IndEvo_`, `catchrelease_`); only the three keys above are shared. Each mod keeps its own chance, cooldown and cap. The contract stops collisions; it does not share turns.

IE side, owner `indevo/exploration/sidequests/SideQuestClaims.java` (static): `isFree(fleet)`, `claim(fleet)`, `openOffer(fleet)`, `closeOffer(fleet)`, `release(fleet)`, `canOpenOfferIn(location)`, `stampOffer()`. IE also claims its special fleets: courier fleets (`CourierFleetCreator`), museum parade fleets, roider tutorial, poor spacer, bounty hunter. Because older CR versions stay in the wild, `isFree` also skips CR's current keys as plain strings: `$catchrelease_fleetQuest`, `$catchrelease_t2Captain`, `$catchrelease_scienceExpedition`, `$catchrelease_prospectingFleet`, `$distressFramework`.

CR side (proposal, CR is not touched until Philipp says so):

- `FleetQuest.writeDialogueMemory` (`:680`) sets the claim and the offer key; `mark` (accepted) unsets the offer key; `release` (`:1285`) unsets both when the claim is CR's.
- `FleetQuestSpawner.getGiverFailure` (`:414`) rejects claimed fleets; `canOffer` (`:168`) checks the location offer key and the shared gap; `markOffered` (`:193`) stamps `$sirhartley_sideQuestLast`.
- Claim CR's special fleets: chain captain, Fisherman, expedition and prospecting fleets, distress callers.
- CR `docs/ARCHITECTURE.md` gets the contract rows.

Alternative not taken: one shared manager in a small library mod both depend on. One scheduler and fair turns between mods, but a new mod to release and a hard dependency for both. `DISTRESS_REWORK_PLAN.md` 3.4 said to revisit that once a third framework is shared; this would be the fourth (settings model, Skillshot, distress, side quests).

### 1.2 Side quest: Return Freight (working name)

**Status: proposed, waiting on Philipp (Q3 to Q9).**

Philipp's brief: "fleet heading back to core, offers to take some of your cargo/fleet members with them for a nominal fee and drop them into cargo at planet they are heading to (rolled, random, influenced by fleet type)".

Framing: a cheap, one off, risky alternative to the Courier Port. No port, no contract, but the goods ride with a real NPC fleet that can die on the way.

**Offer**

- Roll: one IE `NewDayListener` (`TimeTracker`), `SideQuestSpawner`, picks among IE side quest types by weight; Return Freight is the first. Daily, only while the player is in a star system without `Tags.THEME_CORE`. Chance 2% x `SIDE_QUEST_FREQUENCY` per day, own cooldown 30 days after an offer, plus the 1.1 gap and location rule. At most 3 freights in transit.
- Giver: a fleet in the player's location that passes `SideQuestClaims.isFree`, is alive, not hostile, not player faction, not a station, has a commander, is not in battle or a jump. Unlike CR, fleets returning to despawn are allowed: they are the ones heading home.
- Offer lasts while the giver stays in the location, at most 5 days. Marker: vanilla `Misc.makeImportant(fleet, "IndEvo_returnFreight")`, kept during transit; CR already skips important fleets.
- Destination is rolled once at offer time and named in the pitch. Candidates: non-hostile markets with a storage submarket in `theme_core` systems; any market in the economy if none qualify (inferred fallback for random sectors).

| Fleet type | Carries | Destination weights | Fee mult |
|---|---|---|---|
| `trade`, `smallTrader`, `tradeLiner` | Cargo and ships; refuses goods illegal at the destination | Route market x10, its faction's markets x3 (by size), others x1 | 0.4 |
| `tradeSmuggler` | Cargo incl. illegal goods, no ships | Free ports x5, route market x5, others x1 | 0.5 |
| `scavengerSmall/Medium/Large` | Cargo and ships | Source market (`Misc.getSourceMarket`) x10, free ports and independent markets x3, others x1 | 0.3 |
| `shrinePilgrims` | Legal cargo only | Luddic Church markets x5, source market x5 | 0.2 |
| Anything else | No offer | | |

Route market for trade fleets: `RouteManager.getRoute(EconomyFleetRouteManager.SOURCE_ID, fleet)`, custom data `EconomyFleetAssignmentAI.EconomyRouteData.to` (or `from` once the route has turned back; how to tell which leg is inferred, check the segment index in code).

**Capacity and fee**

- Cargo: the giver's free cargo, fuel and crew space.
- Ships: up to `max(1, giver members / 3)`, none larger than the giver's largest hull, never the flagship or the last ship. Officers stay in the player fleet. Ships ride mothballed in the freight's cargo.
- Never the ambassador item (`ItemIds.AMBASSADOR`): the Embassy tracker only finds ambassadors in Courier `Shipment`s (`AmbassadorItemTrackerPlugin.java:110`).
- Fee: Courier price for the picked goods without the base fee (`ShippingCostCalculator.getCostForCargoSpace`, `getCostForShipSpace`, `getLYMult(from, to)`), x the fleet type mult. Paid in the dialog, never refunded.

**Dialog**

- Rule `IndEvo_returnFreightOpen`: `OpenCommLink`, `$entity.IndEvo_returnFreight score:1200`, `!$entity.isHostile`, calls command `IndEvo_ReturnFreight`.
- Flow: pitch (destination, space, ship slots) -> Cargo (vanilla cargo picker, `InteractionDialogAPI.showCargoPickerDialog`) -> Ships (`showFleetMemberPickerDialog`) -> Confirm with fee, or Decline. Over capacity and illegal picks are refused with a line saying why.
- All text is a brief. Philipp writes it or asks for drafts; rows go through the rules skill checks.

**Transit**

- On confirm: goods move from the player fleet into a saved `CargoAPI` on the freight object, not the giver's cargo, so loot rules never hand them out.
- The giver is swapped for an IE owned copy the way CR's `FleetQuest.supplant` does it (copy members, captains, CR, cargo, commander, name, source market; report the original despawn; fade the original). IE writes its own copy in an IE package. Needed because route fleets despawn with `PLAYER_FAR_AWAY` once the player leaves (`RouteManager.java:609-624`) and their route would not go to the rolled market.
- Carrier: non-hostile, do not get sidetracked, claim kept. Assignments: go to the destination, orbit 1 to 2 days "unloading", then vanilla return to source. Not `FLEET_IGNORED_BY_OTHER_FLEETS` (courier fleets are): it can be attacked, that is the risk.
- Delivered: when the unload assignment ends, into the destination's storage (Courier `Shipment.transfer`, `Shipment.java:116`). Storage takes illegal goods.
- Lost: carrier despawned with `DESTROYED_BY_BATTLE` (`FleetEventListener.reportFleetDespawnedToListener`). Goods are gone, the intel says so. Any other despawn before arrival delivers.
- Failsafe: deliver after `7 + ceil(travel days x 3)` days, as `Shipment.setFailsafeTimer` (`:75`).
- Dead destination: Courier alternate target rules (`Shipment.getAlternateTargetSubmarket`, `:190`).
- Intel per freight: carrier, destination, ETA, contents; ends 7 days after delivery or loss.

**Owners and data**

| Area | Owner (new unless noted) | Role |
|---|---|---|
| Claims | `indevo/exploration/sidequests/SideQuestClaims` | 1.1 contract |
| Spawner | `indevo/exploration/sidequests/SideQuestSpawner` | `NewDayListener`, registered through `getInstanceOrRegister()` |
| Freight | `indevo/exploration/sidequests/freight/ReturnFreight` | Saved data, `FleetEventListener`, `NewDayListener` failsafe |
| Fleet | `.../freight/fleet/ReturnFreightAssignmentAI` | Travel, unload, return |
| Intel | `.../freight/intel/ReturnFreightIntel` | Status and map |
| Dialog | `.../freight/rules/IndEvo_ReturnFreight` | Rule command, pickers, fee |
| Delivery | `courierport` (changed) | Static transfer, alternate target and failsafe helpers pulled out of `Shipment`, used by both |
| Settings | `Setting` (changed) | `SIDE_QUESTS` toggle, `SIDE_QUEST_FREQUENCY` (0 to 5, default 1) |
| Docs | `docs/exploration/SIDE_QUESTS.md` (new feature doc), ARCHITECTURE.md, COMPATIBILITY.md (CR contract), SETTINGS.md | |

Save: only new saved classes; nothing saved today changes shape. The CR side adds memory keys only.

**Engine facts this depends on (0.98a-RC8, confirmed)**

- `Misc.makeImportant` sets `MemFlags.ENTITY_MISSION_IMPORTANT` with a reason, `makeUnimportant` clears that reason (`util/Misc.java:4051-4086`).
- `Misc.isFleetReturningToDespawn` reads the flag `giveStandardReturnToSourceAssignments` sets (`Misc.java:3842-3850`).
- `FleetFactoryV3` writes `MEMORY_KEY_FLEET_TYPE` and `MEMORY_KEY_SOURCE_MARKET` (`FleetFactoryV3.java:1319-1322`).
- `EconomyRouteData.from/to` (`EconomyFleetAssignmentAI.java:46-51`), `EconomyFleetRouteManager.SOURCE_ID` is `"econ"` (`:55`), `RouteManager.getRoute(source, fleet)` (`RouteManager.java:566`).
- NPC market storage opens after a one time 5000 credit payment (`StoragePlugin.java:94-101`).
- Pickers: `InteractionDialogAPI.showFleetMemberPickerDialog` (`:56`), `showCargoPickerDialog` (`:83`, `:107`).
- `FleetDespawnReason.DESTROYED_BY_BATTLE` (`CampaignEventListener.java:34`); `Tags.THEME_CORE` is `"theme_core"` (`Tags.java:142`).

### 1.3 Shuttle traffic: open stages

Status: plan in the project file `SHUTTLE_TRAFFIC_PLAN.md` (sections 13 to 23 hold Philipp's calls of 2026-11-11, section 24 the staged plan), code review in `SHUTTLE_TRAFFIC_REVIEW.md` section 7. Stages 3 (groundwork, no visible change), 4 (fixes), 4a (look fixes from the stage 4 live test: trails by time, sizes and glow, Remnant blue, plan section 25) 5 (fleet tied craft), 6 (dodging, hits, free steering, escorts, tenders) 7 (dogfights around NPC battles), 8 (faction profiles, economy tells, event flights) and 9 (IE industry flights, convoys, meteor swarms, rush hours) are in `dev`; current behaviour and files are in [SHUTTLE_TRAFFIC](core/SHUTTLE_TRAFFIC.md).

- Stages 4a to 6: live test by Philipp pending (plan section 24 lists it); stage 6 leaves the escort layer and how often run over hits open for it.
- Stages 7 to 9: live test by Philipp pending, like 4a to 6.

## 2. Open questions

1. Decided (Philipp, 2026-11-07): memory contract in both mods, not a library mod.
2. Decided with Q1: CR gets the claim keys and checks in stage 2. CR is still only touched when that stage is started.
3. Risk: goods lost with the carrier (recommended, it is what makes the fee nominal) or always delivered like the Courier Port?
4. Destination storage still locked: deliver and warn in the pitch (recommended), roll only unlocked destinations, or unlock on delivery (cheap way around the 5000 credit unlock)?
5. "Core": `theme_core` systems only (recommended), or any market, so fringe colonies can be targets too?
6. Ships: allowed for trade and scavenger fleets with the size and count caps above, or cargo only?
7. Fee: Courier price without base fee x 0.2 to 0.5 by fleet type. Right level?
8. Hyperspace: offers in systems only for now (recommended, same as CR), or hyperspace too?
9. Name: "Return Freight" is a placeholder.

## 3. Suggested order

1. Stage 0 (IE, no visible change): pull the delivery helpers out of `Shipment`; add `SideQuestClaims` and claim IE's special fleets.
2. Stage 1 (IE): Return Freight with spawner, dialog, transit, intel, settings, feature doc, changelog. Live test by Philipp.
3. Stage 2 (CR): claim keys and checks in CR, CR docs and changelog.
4. Stage 3: live test with both mods: no fleet with two quests, no two open offers in one system, the 7 day gap holds.

Behaviour to keep in mind:

- The distress framework port (`DISTRESS_REWORK_PLAN.md`) must claim its callers with `$sirhartley_sideQuest` as well.
- Detachments are player faction fleets and never givers.
- CR skips fleets returning to despawn, IE allows them; the claim decides, not that flag.
