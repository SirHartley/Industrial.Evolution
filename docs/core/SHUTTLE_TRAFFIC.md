# Shuttle traffic

Visual only small craft in the player's current star system: cargo lanes between markets and to orbital stations, joyrides, patrol pickets, training flights, mining and salvage runs, pirate cargo hops, scavenging hunts, smugglers, diplomats, maintenance runs, traffic that scatters from hostile fleets, and escape pods from fleets destroyed in battle. Read it before touching `indevo/ambience/shuttletraffic/`, `data/config/indEvo/shuttle_traffic.csv` or `IndEvo_shuttleTraffic`. It does not cover real fleets or trade routes; nothing here changes the economy.

Checked against Starsector 0.98a-RC8, LunaLib 2.0.1 (`lib/LunaLib.jar`) and IE `ba20e5b`. Recheck engine facts after a game update.

## Overview

One transient LunaLib campaign renderer owns everything: anchors, routes, craft, trails, pods. Nothing is an entity, nothing is clickable, nothing is saved. Only the player's current location is simulated; hyperspace has no traffic.

- Anchors are places craft launch from or land at: market planets and stations (`MARKET`), Decivilized Subpopulation planets (`DECIV`), Remnant battlestations (`REMNANT`) and one off destinations (`TARGET`: hunt planets, escape pod planets).
- Routes say what flies where and how often. Most keep a number of flights in the air on a jittered timer; rare routes roll a daily chance.
- Craft follow a chain of path segments, land with a descend look, swerve around slow fleets, and scatter to other ports when hostile fleets come close.

## Files

| File | Owner / role |
|---|---|
| `indevo/ambience/shuttletraffic/ShuttleTrafficRenderer.java` | `LunaCampaignRenderingPlugin`: registration, location change, daily rebuild and rare rolls, fleet scan, anchor threats, fleeing and screening, spawning, warm start, escape pod queue, render |
| `ShuttleNetwork.java` | Anchors and routes for one location, density, closed ports, mining and salvage targets, refuge lookup, star and planet avoidance (`getBend`), hunt planets |
| `ShuttleFlights.java` | Builds the craft and path of each flight kind, formations, flee, away, hold and screen paths, escape pods |
| `ShuttleAnchor.java` | One launch or landing place: type (`PLANET`, `STATION`), role, pad depth, landing length, pad busy time, threat state |
| `ShuttleRoute.java` | One route: kind, anchors, optional target, bend, in flight limit, timer or daily chance |
| `ShuttleTarget.java` | A point relative to an entity, for mining belts, debris fields, wrecks and objectives |
| `ShuttlePath.java` | Segment chain with arc length tables: `BezierSegment`, `CircleSegment` (circle or rotated ellipse); waypoints on an anchor pad, fixed, relative to an entity, or at the start or end of another segment |
| `Shuttle.java` | One craft: progress, easing, jitter, wobble, fleet avoidance, facing, landing and docking look, docking queue, formation following, stays, fleeing, stranded hold, picket screening, trail, rendering |
| `ShuttleFleetScan.java` | Fleets near the player and threat fleets in the location, rebuilt every 0.25 s |
| `ShuttleHull.java` | Sprite, size, speed, flags and engine colours for one hull |
| `ShuttleHullPool.java` | Base pool from the CSV, faction pools, crew rule, rolls per flight kind, sprites, engine colours, escape pod sprites |
| `EscapePodListener.java` | Transient `BaseCampaignEventListener`: queues fleets destroyed in battle |
| `data/config/indEvo/shuttle_traffic.csv` | Base civilian craft: `id, hullId, weight, small` |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Setting | `IndEvo_shuttleTraffic` (Int, 60, 0 to 120): most craft in flight in the current system, 0 turns traffic and escape pods off | `data/config/LunaSettings.csv:13`, `indevo/config/Setting.java:10`, read every frame in `ShuttleTrafficRenderer.advance` (`:83`) |
| CSV | `data/config/indEvo/shuttle_traffic.csv`, read with `getMergedSpreadsheetDataForMod("id", path, "IndEvo")`; rows starting with `#` are skipped | `ShuttleHullPool.getBasePool` |
| Sprites | `campaignEntities` / `fusion_lamp_glow` (engine glow); `misc` / `cargoPods` cut into 32 px cells for escape pods; hull sprites from `ShipHullSpecAPI.getSpriteName()` | `ShuttleTrafficRenderer`, `ShuttleHullPool.createPod` (`:98`) |
| Config | `data/config/engine_styles.json` (merged): `engineCampaignColor` or `engineColor` for the glow, `contrailCampaignColor` or `contrailColor` at alpha 75 for the trail, `type` `SMOKE` for a normal blend trail | `ShuttleHullPool.getEngineLook` (`:265`) |
| Industry ids | `Industries.PATROLHQ`, `MILITARYBASE`, `HIGHCOMMAND`, `MINING`, `Ids.SWITCHABLE_MINING`, `Ids.ACADEMY`, `Ids.EMBASSY`, `Ids.SCRAPYARD` | `ShuttleNetwork.addMarkets` (`:170`) |

Base pool: `shuttlepod` 10 (small), `kite` 4 (small), `hermes` 2, `mudskipper` 2, `shepherd` 1, `ox` 1. Another mod adds rows by shipping the same path with its own ids.

## Registration and lifecycle

- `ModPlugin.onGameLoad() -> setListenersIfNeeded() -> ShuttleTrafficRenderer.register()` (`indevo/utils/ModPlugin.java:493`). Guarded with `LunaCampaignRenderer.hasRendererOfClass`; resets the hull pool caches, adds a transient renderer and a transient `EscapePodListener` (`Global.getSector().addTransientListener`) (`ShuttleTrafficRenderer.java:59`).
- LunaLib's `luna_campaign_renderer` entity renders the plugin in every layer and only advances it while that entity is in the player's location.

`advance -> [location changed] network.rebuild -> fleetScan.scan -> updateThreats -> warmStart | [1 day] network.rebuild -> rollRareFlights -> [0.25 s] fleetScan.scan -> updateThreats -> react -> spawn -> launchQueuedPods -> Shuttle.advance`

## Flow

### Anchors and routes

`ShuttleNetwork.rebuild` (`:66`) on location change and once per day.

- A market counts if its primary entity is in the location, `isInEconomy()`, not `isHidden()`, `hasSpaceport()` and not `isDiscoverable()` (`:409`). A primary entity tagged `Tags.STATION` is a station anchor, otherwise a planet anchor.
- Density (`:421`): Megaport x1.5, Commerce x1.25, stability `clamp(0.5 + stability / 20, 0.5, 1)`, x0.5 while `MemFlags.RECENTLY_RAIDED` or `RECENTLY_BOMBARDED`. A disrupted Spaceport or Megaport closes the market anchor and its stations (`:415`).
- Anchors and routes not seen in a rebuild are dropped. Routes keep their key `kind|anchor id|destination id`, bend, timer and counts across rebuilds.

| Kind | Route | In flight | Cap share |
|---|---|---|---|
| `SUPPLY` | Planet market to each connected `Tags.STATION` entity | `max(1, round((1 + size / 3) * density))` | 1.0 |
| `PIRATE_CARGO` | Pirate owned market to each `DECIV` anchor | 1 | 1.0 |
| `SMUGGLER` | Market pair with hostile factions, neither pirate | 1, 1% per day | 1.0 |
| `DIPLOMAT` | Market with an Embassy to another faction's market, picked per flight | 1, 5% per day | 1.0 |
| `HUNT` | `DECIV` anchor to another non star planet (ruins weight 3, else 1), stays down 2 to 5 days, flies home | 1, 3% per day | 1.0 |
| `MAINTENANCE` | Nearest same faction market to each comm relay, nav buoy or sensor array; one lap at 100 su | 1, 2% per day | 1.0 |
| `MARKET` | Market pair, factions not hostile, neither pirate | `max(2, round(chord / 500 * mean density * (sizeA + sizeB) / 10))`: about one craft per 500 su of lane for two size 5 markets | 0.9 |
| `MINING` | Market with Mining to the nearest asteroid belt, ring or asteroid field; lingers 1 to 3 days | `max(1, round((1 + size / 4) * density))` | 0.8 |
| `SALVAGE` | Market with Salvage Yards to the nearest debris field or wreck; lingers 2 to 4 days | 1 | 0.8 |
| `PICKET` | Market with Patrol HQ (1) or Military Base / High Command (2); one or two laps of an ellipse at `radius + 150..300` | wings | 0.8 |
| `JOYRIDE` | Every market (2 for pirates) and Remnant battlestation (2) | 1 or 2 | 0.7 |
| `TRAINING` | Market with an Academy; a loose group of 2 or 3 flies an orbit | 1 group | 0.7 |

- Cap share: a route only launches while the craft in flight are below `setting * share`, so joyrides and training drop first, then mining, salvage and pickets, then market lanes. Formations count each craft.
- Timer routes (`ShuttleTrafficRenderer.spawn`, `:215`): lanes `(distance / 27 + 6) / max * (0.6..1.4)` s, others `Kind.pause * (0.6..1.4)` s. Rare routes roll their chance once per day (`rollRareFlights`, `:209`) and launch when the roll is pending.
- Warm start fills every timer route to its limit at spread progress, trails and formation history pre-filled.
- Nothing launches from or to a threatened anchor, or from an anchor threatened in the last day (`ShuttleFlights.canFly`, `:464`).

### Hulls

- Crew rule (`ShuttleHullPool.isCrewed`, `:211`): `getMinCrew() > 0`, no built in `HullMods.AUTOMATED`, no `Tags.AUTOMATED`; wings tagged `Tags.AUTOMATED_FIGHTER` are skipped. Remnant joyrides skip the rule.
- Faction pool (`:166`), cached per faction id and crew rule: hulls of `getKnownFighters()` (wing size clamped 1 to 3) and the frigates of `getKnownShips()` without `STATION` or `MODULE` hints or `Tags.RESTRICTED`. Civilian hints (`CIVILIAN`, `FREIGHTER`, `TANKER`, `LINER`, `TRANSPORT`) mark a hull civilian; fighters and Kite skins are small. Weights: CSV weight, else civilian 3, fighter 2, other 1.
- Rolls: supply hops 80% base small craft, else faction civilian small craft; market lanes, smugglers and maintenance 60% base, else faction civilian; pirate cargo hops the pirate faction's small craft; diplomats a faction `LINER` or Hermes / Kite; hunts Shuttle Pod, Kite, Mudskipper; joyrides 20% base, 80% faction; pickets faction fighters, fallback Talon; training faction fighters or Kite / Shuttle Pod; mining faction `FREIGHTER` or Shepherd / Mudskipper / Ox; salvage Ox / Mudskipper / Shepherd.
- Engine colours: `getEngineSlots()` on the game's hull spec class and `getStyleId()` on the first slot, by name through `ReflectionUtils.invoke`; a `CUSTOM` or unknown style uses the slot colour. If reflection fails, manufacturer `Low Tech` / `High Tech` / anything else maps to `LOW_TECH` / `HIGH_TECH` / `MIDLINE`, with one warning.
- Unknown or uncrewed CSV hulls are dropped with a warning; a hull without a loadable sprite is skipped.

### Paths

- Market lanes (`trafficLanePath`, `ShuttleFlights.java:83`): one curve per market pair, bent the same way in both directions. Pads sit 8 +-1.5 degrees right of the live line between the anchors and jitter is +-3 su, so each direction keeps to its own side and a pair shows two steady streams about 60 su apart mid lane.
- Other lanes: one cubic Bezier between pads (`0.6 * radius` deep on planets, `0.15 * radius` on stations), evaluated live, so orbiting anchors carry the lane. Pads sit at a fixed offset from the live direction to the other anchor (+-40 degrees, scaled down to +-12 on lanes under 800 su), so the lane turns with orbiting anchors instead of stretching; handles `min(radius + 80, chord * 0.45)` along the outward radial. Smugglers launch and land on the far sides and bend twice as wide.
- Bend: control points shift along the chord normal by `bendFrac * chord`, `bendFrac` in +-0.125 per route; outside market lanes the two directions arc on opposite sides. `ShuttleNetwork.getBend` (`:112`) pushes the bend clear of any star (corona or event horizon outer edge, else `1.5 * radius`) or planet (`1.3 * radius + 30`).
- Joyrides (`ShuttleFlights.joyridePath`): Orbit, one full lap at `radius + 100..220` su around the anchor; Arc, planets only (60%), a low hop at `0.25 * radius + 40..80` su over 60 to 140 degrees to another spot on the same planet. Pads sit 60 degrees behind the entry and past the exit, so the craft climbs out along the entry radial and spirals back in; handles `0.5 * R` at the pads, `0.4 * R` at the circle.
- Linger (`lingerPath`, `:356`): Beziers between points on circles of `wander * (0.8..1.2)` that turn 80 degrees per step, handles `0.5 * wander` (longer ones overshoot into cusps), drifting 40 su per step along a belt band; the count follows the linger days at cruise speed. Two spare points at each end let the loop start at the point heading most away from home and end at the one heading most toward it, so the transits need no u turn. Wander is 80 su for belts and fields, 60 to 120 su for debris fields, 70 su for wrecks.
- Jitter up to +-10 su (market lanes +-3) scales with `sin(pi * t)`. Paths that join a craft's current position to a pad or circle use handles up to 150 su. Arc length tables of 16 samples. A craft keeps its progress as a segment and an arc length fraction inside it (`ShuttlePath.Cursor`); the current segment's table is rebuilt every frame and the rest every 1 s, so a rebuild never moves the craft. Progress is distance based, so fast forward is safe.
- Frames: a craft stores its trail and formation history relative to a virtual frame (`Shuttle.moveFrame`, `:699`) that moves each frame by how far the current segment's start and end points moved, blended by `t * t * (3 - 2t)` on a Bezier (how its control points move) and the start on a circle. The trail then rides exactly like the path under the craft and stays in line with the sprite even between two bodies that orbit apart. A segment that starts where the last one ended carries on from that end. Every waypoint also names the entity it moves with (`Waypoint.getFrame`); paths that start at a craft's position (redirect, flee, away, screen, holds) start relative to the current segment's dominant entity.

### Formations

Fighters on joyrides and pickets fly as a wing of `wingSize` (at most 3); training groups are 2 or 3. The leader records its position every 4 su (16 points); followers sit on that history `8 su * rank` back and `6 su * rank` out, alternating sides, and copy the leader's scale, alpha and layer. Training followers add +-4 su noise; one in four groups has a straggler that drops back up to 30 su while the leader turns and catches up on straights. Formations skip the docking queue and break up when they flee. When the leader starts to dodge a fleet, every follower takes over the leader's path at its own slot, at +-10% speed, spreads out to four times its slot offset and dodges on its own.

### Fleet avoidance

`ShuttleFleetScan.scan` (`:25`) every 0.25 s: obstacles are fleets that are not `isStationMode()`, slower than 40 su/s, within 3000 su of the player, and either the player fleet or 5 or more ships. `Shuttle.getAvoidanceTarget` (`:543`) pushes the craft along the path normal to clear `fleet radius + 40`, to the side it is already on, easing at 20 su/s, fading out over the first and last 15% of a path. Fleeing craft also avoid hostile threats at any speed.

### Danger

- Threats (`ShuttleFleetScan`, `:34`): the player fleet, and fleets with at least 30 fleet points; a threat counts against a faction it is hostile to.
- Anchors (`updateThreats`, `:248`): threatened with a hostile threat within `radius + 600` su, overrun within `radius`. `lastThreatDay` is kept; Remnant anchors are never threatened.
- Craft (`react`, `:263`): a craft flees when a hostile threat is within 400 su or its destination is threatened. A fleeing craft flees again only when its refuge becomes threatened.
- Refuge (`ShuttleNetwork.findRefuge`, `:90`): nearest usable, unthreatened, non hostile anchor, other factions x1.5 distance; pirates only use pirate markets and `DECIV` anchors, everyone else neither; Remnant anchors never.
- Disarray (`Shuttle.flee`, `:233`): heading kick of up to +-70 degrees, one in five flies a 60 to 90 su panic circle first, speed x1.4, turn rate x2, a 10 su swerve fading over 3 s. Formations break up and each craft flees alone. A refugee lands, waits hidden and flies home once its home has been safe for 1 day.
- No refuge: fly 800 to 1500 su away from the threat, then hold a 150 su circle at 60% speed until no hostile threat has been within 800 su for 1 day, then fly home.
- Pickets never flee from nearby threats: while their market is threatened they fly 120 su circles at 60% speed centred `radius + 250` su toward the threat, and land once it leaves. If the market is overrun they scatter like the rest. Remnant drones never flee.
- A closed port (disrupted, or the anchor dropped) diverts inbound craft calmly to a refuge without disarray.

### Escape pods

- `EscapePodListener.reportFleetDespawned` (`:17`) on `FleetDespawnReason.DESTROYED_BY_BATTLE`, not the player fleet, in the player's location. `CampaignFleet.despawn` reports while the fleet is still in place (knowledge skill `obf/campaign/fleet/CampaignFleet.java:1599`).
- Pods (`getPodCount`, `:354`): from `getFleetData().getSnapshot()`, crewed non automated members only, 1 per frigate or destroyer, 2 per cruiser, 3 per capital, at most 16; an empty snapshot gives 3.
- Planet (`findPodPlanet`, `:373`): nearest non star planet whose surface is within 2000 su; a non hostile market planet in range wins over a closer one.
- Queued until `getCampaignUI().isShowingDialog()` is false and the game is unpaused, dropped if the player left the location. At most 32 pods in flight.
- Each pod (`ShuttleFlights.launchPods`, `:146`): random 32 px cell of the cargo pod sheet at 13 su, appears within 1.5 s, shoots out at 80 to 120 su/s easing to 35 su/s over 2 s, tumbles, curves to a random point on the planet disc and lands with the planet look. Short thin `LOW_TECH` contrail, no engine glow. Pods ignore fleets and threats.

### Look

- Hull sprites at 0.13 (fighters) and 0.075 (frigates) of sprite size, below vanilla's campaign 0.15 and 0.11. Speed 30 su/s fighters, 24 su/s frigates, +-15% per craft, easing to 35% over launch and landing. Joyrides x1.1, pickets x0.8, hunts, mining and salvage x0.8.
- Everything draws in `TERRAIN_7`: above planets, rings, asteroids and stations, below fleets. Planet launch and landing over `1.2 * radius` (at most 40% of the path): scale to 0.5, 30% darker, and the craft fades only while over the surface, from the limb to the pad. Station docking over 60 su: scale to 0.7, alpha to 0. A craft pulled out of a landing (fleeing, holding) fades back over 1 s.
- Facing follows the path tangent plus the rate of the sideways offset, turning at most 90 degrees per second, never the world step, so orbiting anchors do not swing the sprite or trail.
- Trail: 16 point ring buffer, a point every 8 su, untextured `GL_QUAD_STRIP` 3 su wide, fading to the tail, engine style colour and blend. Smugglers: 30% trail, no glow. Hunts: 50% trail, heavier glow flicker.
- Engine glow at the rear, flickering, 1.5x for 2 s around launch and landing. No blinking lights.
- Traffic fades out between view mult 2 and 3 (max campaign zoom is 3). Off viewport craft (300 su margin) are skipped.

### Docking queue

A station pad is busy while a craft is in its last 100 su and 1 s after. A craft that reaches that point while the pad is busy flies a 120 su circle around the station, tangent to its heading, and checks again after each lap; when free it claims the pad and flies a short Bezier in. Formations and pods skip the queue.

## Contracts

- Transient only: no entity, listener field, memory key or script lands in the save. Removing IE never breaks a save through this feature.
- Advancing stops while `Global.getSector().isPaused()` (`ShuttleTrafficRenderer.java:84`); the feature keeps its own day count from `convertToDays`, so stays and safe times only pass while the player is in a system with traffic.
- `ShuttleHullPool` caches are static and reset on every `register()`.
- `getHullSpec` throws on unknown ids, so every CSV and `getKnownShips()` id is checked against `getAllShipHullSpecs()` first.
- The engine style reflection names (`getEngineSlots`, `getStyleId`) are unobfuscated public methods of the game's hull spec and `EngineSlot` classes (knowledge skill `obf/loading/specs/_c147.java`, renamed from `g.java`, and `EngineSlot.java:47`); never use an obfuscated name here.
- Remnant battlestations are fleets in station mode with `MemFlags.MEMORY_KEY_FLEET_TYPE` `FleetTypes.BATTLESTATION` and faction `Factions.REMNANTS`.

## Known issues and dormant code

| Item | State |
|---|---|
| In game look | Tests (2026-11-08) fixed: rubberbanding, nervous trails, blinking lights, loop joyrides, craft vanishing under planets, sprite size, trails out of line with the craft between moving bodies. Untested in game: market lanes, blended trail frame, look of orbits and arcs, formation break up |
| Performance | Live smoke `shuttle` group (2026-11-08, before market lanes): 55 craft at Sindria, all of `shuttletraffic` 0.4% of CPU and allocation |

## Maintenance

Update this doc when a flight kind, the anchor rules, the hull pool, the CSV columns or the setting changes. Setting mechanics belong in [Settings](SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
