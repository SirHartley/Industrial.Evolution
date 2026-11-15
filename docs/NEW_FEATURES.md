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

Status: plan in the project file `SHUTTLE_TRAFFIC_PLAN.md` (sections 13 to 23 hold Philipp's calls of 2026-11-11, section 24 the staged plan), code review in `SHUTTLE_TRAFFIC_REVIEW.md` section 7. Stages 3 (groundwork, no visible change), 4 (fixes), 4a (look fixes from the stage 4 live test: trails by time, sizes and glow, Remnant blue, plan section 25) 5 (fleet tied craft), 6 (dodging, hits, free steering, escorts, tenders) 7 (dogfights around NPC battles), 8 (faction profiles, economy tells, event flights) 9 (IE industry flights, convoys, meteor swarms, rush hours) and 10 (calm down after the second live test: speed cap, stage 4 avoidance, escorts and tenders removed, plan section 26) are in `dev`; current behaviour and files are in [SHUTTLE_TRAFFIC](core/SHUTTLE_TRAFFIC.md).

- Stages 4a to 6: live test by Philipp pending (plan section 24 lists it); stage 6 leaves the escort layer and how often run over hits open for it.
- Stage 10: live test by Philipp pending (plan section 24, stage 10).

### 1.4 Rogue asteroids (small formations and drizzle)

**Status: shapes approved on the mockup (Philipp, 2026-11-15). Stages 1 to 3 implemented, stage 4 (live test) open. Q12 to Q16 are built on the recommended answers until he says otherwise. Current behaviour and files: [METEOR](exploration/METEOR.md#rogue-asteroids). Checked against `dev` at `779055f`; engine facts against the starsector-knowledge skill, which is now built from 0.98.5a-RC4.**

Deviations found while building: the console command is `indevo_spawnRogue` (the `indevo_` prefix of [CONSOLE](core/CONSOLE.md)); generator sizes below 20 (Teardrop tail, Chevron arms) are raised to 20; `Circle.getAngleForPoint` now uses strict `atan2`, since the game's fast angle lookup is off by up to about 0.06 degrees, a few hundred su on these arcs; `ArcOnOrbitLossMovement` drops its orbit itself when the focus is gone, for the Escort rocks; non aimed rogues start on the system edge; an aim point beyond the grid is clamped 500 su inside it; the mockup's formation yaw (`angle` in `build()`) is not used.

Philipp's briefs:

- Rogues (2026-11-08): "minor 'roid storms or rogue asteroids that sometimes traverse systems without warning or trajectory - just a small mini-swarm that rarely have treasureoids in them".
- Drizzle (2026-11-09, moved here from `TERRAIN_PLAN.md` on 2026-11-15, "one of these should be the meteor drizzle"): "full-system low volume meteor showers that just have small 'roids across the entire system and no treasureoids".

Philipp's calls (2026-11-15):

- Not big swarms: small assemblies of rocks in different shapes, e.g. wide and sparse, localized, long, drop shaped.
- Any of the existing rock types.
- Path anywhere in the system, weighted towards where the player is heading.
- Can show up in inhabited systems.
- Not every rogue has treasure.
- Same damage as the showers.
- Rough mockups first: project file `ie/ROGUE_ASTEROIDS_MOCKUP.html` (live view at game scale, one card per shape, path map).
- After the first look at the mockups: no Stream (a thin line of rocks "looks stupid"), Teardrop is the long shape. Wall and Chevron need a more organic look: redrawn ragged, with uneven spacing, bends and stragglers.
- After the second look: all eight work (Scatter, Cluster, Teardrop, Wall, Chevron, Pair, Escort, Drizzle). Keep them rare enough to not be annoying. Other fleets dodge them. 30% of rogues go for the player's path or vicinity.

**What already exists (confirmed in code)**

- Roll: `MeteorSwarmManager.reportEconomyTick` (`MeteorSwarmManager.java:79`) rolls in the player's current location, 10 ticks per month (`economyIterPerMonth`, vanilla `settings.json:527`). `isValidMeteorLoc` (`:99`) refuses systems with markets (`:112`) and checks the 31 day global and 186 day location timeouts (`:176-177`).
- Size: `MeteorFactory.spawn` passes `data.size` as radius, sprite width and sprite height (`MeteorFactory.java:43`), and a hit is `distance < entity.getRadius()` (`MeteorEntity.java:132`). So a rock hits at twice the distance its sprite covers. Shower rocks are size 20 to about 100, ice rocks double that (`IceSwarmSpawner.java:83`), speed 200 to 500 su/s (`StandardSwarmSpawner.java:69`), which is burn 10 to 25 (`speedPerBurnLevel` 20).
- Hits: targets are `Tags.STATION` entities and every fleet in the location, station fleets included (`MeteorEntity.java:48-49`), only within 1000 su of the viewport (`:107`). A fleet gets one ship damaged and a push that drops its orbit (`MeteorImpact.java:119`, `:193`); anything else gets sound, glow and fragments. `Misc.applyDamage` never takes a hull below 1% (`Misc.java:5961`). Vanilla puts a market's station fleet back in orbit on its next advance (`OrbitalStation.java:221`).
- Warning path: `ArcingMovementModule` and `ExternalOrbitMovement` report every rock's angle to the location's `MeteorSwarmWarningPathRenderer` (`ArcingMovementModule.java:52`, `ExternalOrbitMovement.java:29`). The renderer ends after 10 s without reports and `RadioactiveTerrain` lives as long as the renderer (`RadioactiveTerrain.java:91`). A rock on another path that reports would keep a shower's warning path and terrain alive and light the wrong signs.
- Arcs: every movement module has an arc (`MeteorMovementModuleAPI.getArc`). Ice rocks of radius 50 or more spawn orbiting splinters that fall back to that arc (`IcyRockEntity.java:31`, `:47`); the tag `IndEvo_IceRockNoSplinters` turns that off. `ArcingMovementModule(arc, velocity, startAngle)` starts a rock anywhere on its arc.
- Irradiated rocks raise `$IndEvo_RadEffectLevel` on fleets within 10 radii (`SpicyRockEntity.java:47-48`): radiation sound and screen effect for the player. The CR loss comes from `RadioactiveTerrain`, which only showers spawn.
- Meteor entities have `showIconOnMap` (`custom_entities.json`), so every rock is on the system map.
- Every meteor gets `METEOR_TAG` in `MeteorEntity.init`: missiles, the Catch.Release harpoon and the shuttle traffic closure (`docs/core/SHUTTLE_TRAFFIC.md`, `updateSwarms`) work on rogues with no change. Rogues bring that closure into colonies: a pad within 600 su of a rock stops launching while the rock is near.
- Heading: `CampaignUIAPI.getCurrentCourseTarget()` is the next step of a laid in course, `CampaignFleetAPI.getMoveDestination()` is where the fleet was sent and returns the fleet's own location when unset.

**Plan**

- Roll: a second roll in `reportEconomyTick`, also in systems where showers are not valid, and only when no shower was rolled that tick. New setting `IndEvo_meteor_rogueChancePerEconomyTick`, default 0.05 (one rogue per about 60 days spent in valid systems; with 30% aimed at the player that is about one near the player per 200 days), range 0 to 0.3, 0 turns rogues off. Also gated by `IndEvo_meteor_enabled`.
- Location: `isValidRogueLoc` is `isValidMeteorLoc` without the market rule and the shower timeouts, plus: a star system, no `$IndEvo_warningRenderer` in the location (no rogue during a shower) and an own location timeout `$IndEvo_RogueMeteorTimeout`, 30 days (drizzle: its duration plus 30). A rogue sets no shower timeout.
- Pick: shape by weight: Scatter 20, Cluster 20, Teardrop 20, Wall 10, Chevron 10, Pair 10, Escort 5, Drizzle 10.
- No warning: no intel, no warning path, no sign, no sound, no delay. The rocks are on the system map like shower rocks (Q16).
- Path: one very wide `CircularArc` (radius 150000 to 400000 su) from system edge to system edge (`IndEvo_meteor_gridLimit`), so it is nearly straight and all existing arc code works: movement, splinters, removal at the arc end. It runs through one aim point: with 30% chance a point 0 to 8000 su ahead on the player's course (course target if it is in this location, else `getMoveDestination()`, else the fleet itself) with 1500 su spread, otherwise a random point within 20000 su of the centre. Crossing angle random.
- Timing: the formation starts on its arc out of view (at least 2000 su outside the viewport) and far enough back to reach the aim point about when the fleet does, at most at the system edge. It keeps going to the far edge.
- Formation: `RogueFormation` gives each rock an offset along and across the path and a size. Across becomes the arc radius (`getModifiedRadiusArc`, as the showers do), along becomes the start angle. One speed per formation, 250 to 400 su/s, scaled by each rock's arc radius so the shape holds. Sizes 20 to 100, one rock of up to 200 in Escort. Shapes are outlines filled at random, never rocks on a ruled line; numbers per shape: the generators in the mockup page.
- Types: one of `ASTEROID`, `ICEROID`, `IRRADIOID` per rogue by the shower weights (12, 3, 3), mixed like the showers: half the rocks of an ice rogue are ice rocks at double size, 30% of an irradiated rogue are irradiated rocks, the rest asteroids. Ice rocks below size 100 get `IndEvo_IceRockNoSplinters`, since splinters from size 50 more than double a small formation (15 rocks became 37 in the mockup). Irradiated rogues have no terrain: glow, radiation sound and screen effect, no CR loss. `PLANETOID` is a shower, not a rock type, and is not used.
- Treasure: 20% per formation (Q14), one rock: `TreasuroidEntity` for asteroid and irradiated rogues, `IceTreasureoidEntity` for ice. `TreasuradoidEntity` (radius 400, entourage, loot station) stays with the showers.
- Damage: `MeteorImpact` and `IceroidImpact` unchanged for fleets (decided). A station fleet (`isStationMode`) breaks the rock with glow and fragments but takes no damage and no push (Q12). This goes for shower rocks too: today a hit drops a station fleet's orbit, and only market stations get it back.
- Dodge (decided): NPC fleets steer out of a rock's lane. In `MeteorEntity.advance`, inside the near viewport block and on a 0.2 s interval per rock: for every fleet target with a `ModularFleetAIAPI` that is not the player, not in station mode and not on an orbit, take the point where the rock passes closest to the fleet; if that is within 4 s and closer than rock size + fleet radius + 150 su, call `getNavModule().avoidLocation(loc, thatPoint, range, range + 1, 0.03f)`. The avoid vector then points across the rock's path. A range band of 1 or less gives the full weight of 2 against the fleet's own heading (`NavigationModule.advance`). A fleet parked on an orbit does not move by its nav module and still gets hit. Goes for shower rocks too.
- Drizzle: a timed state of the system, 5 to 15 days, no terrain or tooltip (Q13), not a formation. Rocks of size 20 to 48 only (no splinters), one rock type mix by the same weights, never treasure. A `RogueDrizzleSpawner` location script keeps a target density of rocks around the viewport (1 rock per 500000 su2 of the view plus a 600 su margin, cap 80), spawns them in the margin on lanes that cross the view and removes them once they are 1000 su outside the margin, so cost is flat whatever the system size. Lanes are concentric wide arcs around one far centre, two or three lane sets up to 8 degrees apart for the spread, speed 200 to 500 su/s per rock. With the player out of the system it removes its rocks and waits; it ends by timestamp.
- Warning path fix, needed for all of the above: the renderer takes reports only from rocks on its own arc (same arc centre), so `reportAngle` gets the arc. No silent movement class; splinters and orbiting rocks are covered too. The maelstrom item in `TERRAIN_PLAN.md` waits for this.
- Test hook: console command `IndEvo_SpawnRogue <shape> [asteroid|ice|irradiated] [treasure]` spawns one rogue aimed at the player in the current system, `drizzle` starts a drizzle.

**Stages**

1. Shared fixes in the shower code: warning path reports carry the arc; station fleets take no damage and no push; NPC fleets dodge. Feature doc and changelog.
2. Formations: `RogueFormation`, `RogueAsteroidSpawner`, the roll, the setting, the console command, `RogueAsteroidChecks` (headless: rock counts and footprint per shape and seed, the arc runs through the aim point, start and end on the system edge, the closest approach maths of the dodge).
3. Drizzle: `RogueDrizzleSpawner`, roll weight, console command.
4. Live test by Philipp (console command in an inhabited and an uninhabited system, all three rock types, treasure, drizzle, a shower running next to a rogue).

**Owners and data**

| Area | Owner (new unless noted) | Role |
|---|---|---|
| Roll | `MeteorSwarmManager` (changed) | Rogue roll, `isValidRogueLoc`, shape pick |
| Rocks | `MeteorEntity`, `IcyRockEntity`, `MeteorImpact` (changed) | Dodge, station fleet rule |
| Spawner | `indevo/exploration/meteor/rogue/RogueAsteroidSpawner` | Path, aim point, timing, places one formation, treasure roll |
| Shapes | `indevo/exploration/meteor/rogue/RogueFormation` | Enum of shapes: offsets and sizes per rock |
| Drizzle | `indevo/exploration/meteor/rogue/RogueDrizzleSpawner` | Saved location script: lanes, density around the viewport, end time |
| Warning path | `MeteorSwarmWarningPathRenderer`, `ArcingMovementModule`, `ExternalOrbitMovement` (changed) | Reports carry the arc, the renderer ignores other arcs |
| Settings | `Setting` (changed), `data/config/LunaSettings.csv` | `IndEvo_meteor_rogueChancePerEconomyTick` |
| Console | `indevo/console/SpawnRogue`, `data/console/commands.csv` | Test hook |
| Checks | `indevo/tools/RogueAsteroidChecks` | Headless, never shipped, not part of `ReleaseBuild` |
| Docs | `docs/exploration/METEOR.md` (rogue section, files, ids), `docs/core/SETTINGS.md`, `docs/core/SHUTTLE_TRAFFIC.md` (swarm closure now also in colonies) | |

Save: `RogueDrizzleSpawner` is saved with its location; rocks save their movement module as today. New classes only, nothing saved today changes shape.

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
10. Decided (Philipp, 2026-11-15): all eight shapes on the mockup page are in, Stream is out. Weights are my call: Scatter 20, Cluster 20, Teardrop 20, Wall 10, Chevron 10, Pair 10, Escort 5, Drizzle 10.
11. Decided (Philipp, 2026-11-15): 30% of rogues aim at the player's path or vicinity, 70% anywhere.
12. Rogues in colonies: rocks break on station fleets with glow and fragments but no damage (built this way: a cluster can otherwise take a colony's station down to 1% hull, a few percent per rock), or same damage as on fleets?
13. Drizzle: 5 to 15 days, medium volume, and no terrain or tooltip like the other rogues (built this way), or the system wide terrain with name and end time from the terrain plan?
14. Rogue treasure: 20% per formation, never in drizzle (built this way). Right level?
15. Decided in direction (Philipp, 2026-11-15): rare enough to not be annoying. Built as 5% per economy tick and a 30 day location timeout, with the chance as a setting. Right level?
16. Rogue rocks on the system map like shower rocks (built this way, the only tell), or hidden?

## 3. Suggested order

1. Stage 0 (IE, no visible change): pull the delivery helpers out of `Shipment`; add `SideQuestClaims` and claim IE's special fleets.
2. Stage 1 (IE): Return Freight with spawner, dialog, transit, intel, settings, feature doc, changelog. Live test by Philipp.
3. Stage 2 (CR): claim keys and checks in CR, CR docs and changelog.
4. Stage 3: live test with both mods: no fleet with two quests, no two open offers in one system, the 7 day gap holds.
5. Rogue asteroids (1.4): independent of the side quests, stages in the item. Live test by Philipp.

Behaviour to keep in mind:

- The distress framework port (`DISTRESS_REWORK_PLAN.md`) must claim its callers with `$sirhartley_sideQuest` as well.
- Detachments are player faction fleets and never givers.
- CR skips fleets returning to despawn, IE allows them; the claim decides, not that flag.
