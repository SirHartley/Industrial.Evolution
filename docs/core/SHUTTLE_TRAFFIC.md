# Shuttle traffic

Visual only small craft in the player's current star system: cargo lanes between markets and to orbital stations, local hops to nearby moons, planets and stations, pleasure cruises, patrol pickets, training flights, mining and salvage runs, pirate cargo hops, scavenging hunts, smugglers, diplomats, maintenance runs, craft that serve fleets, fighters dogfighting around NPC battles, a flying style per faction, tells of free ports, pirate activity, Pather cells and hidden bases, event flights (captured objectives, settlers, recovery tugs), traffic that eases around slow fleets and scatters from hostile ones, hits from blasts, burns and pulses, and escape pods from fleets destroyed in battle or decivilized colonies. Read it before touching `indevo/ambience/shuttletraffic/`, `data/config/indEvo/shuttle_traffic.csv`, `shuttle_factions.csv` or `IndEvo_shuttleTraffic`. It does not cover real fleets or trade routes; nothing here changes the economy.

Checked against Starsector 0.98a-RC8, LunaLib 2.0.1 (`lib/LunaLib.jar`) and IE `dev` after stages 3 to 8 of the shuttle traffic plan. Recheck engine facts after a game update.

## Overview

One transient LunaLib campaign renderer drives one `ShuttleTraffic` context that owns everything: anchors, routes, craft, trails, pods. Nothing is an entity, nothing is clickable, nothing is saved. Only the player's current location is simulated; hyperspace has no traffic.

- Anchors are places craft launch from or land at: market planets and stations (`MARKET`), Decivilized Subpopulation planets (`DECIV`), Remnant battlestations (`REMNANT`) and one off destinations (`TARGET`: hunt planets, escape pod planets).
- Routes say what flies where and how often. Most keep a number of flights in the air on a jittered timer; rare routes roll a daily chance.
- Craft follow a chain of path segments, land with a descend look, ease around slow fleets, and scatter to other ports when hostile fleets come close; dogfighters steer by heading and throttle. Every craft is slow, turns wide and changes speed gently: no craft moves faster than 75 su/s (burn 3.75) and path speed changes by at most 12 su/s per second up and 20 down.

## Files

Package `indevo/ambience/shuttletraffic/`:

| File | Owner / role |
|---|---|
| `renderers/ShuttleTrafficRenderer.java` | `LunaCampaignRenderingPlugin`: registration, pause, zoom fade, viewport cull, render order (trails of a list in one GL state block, then sprites) |
| `ShuttleTraffic.java` | Context for one location: network, random, fleet scan, flights, spawner, reactions, craft and pod lists, fleet routes (own map, outside the daily rebuild), own day count; location change, daily rebuild, 0.25 s scan |
| `ShuttleSpawner.java` | Warm start for a new location and for local routes of a market that came into range, rare route rolls, timer launches under the cap share |
| `ShuttleReactions.java` | Anchor threats, fleeing, screening, closed port diverts |
| `ShuttleHits.java` | Explosion blasts and interdiction pulses (0.25 s scan), run over by fleets at burn 14 or faster (every frame); wreck pods |
| `ShuttleNetwork.java` | Anchors and routes for one location, density, liveliness and rush hours, which anchors are near the player, closed ports, meteor swarm closures, mining and salvage targets, shrines, refuge lookup, star and planet avoidance (`getBend`), hunt planets |
| `flights/ShuttleFlights.java` | One `Flight` per `ShuttleRoute.Kind` in an `EnumMap`; throws when a kind has none |
| `flights/LaneFlights.java`, `LocalFlights.java`, `PodFlights.java` | Lane flights (lanes, convoys, couriers, pilgrims, dark craft, diplomats, hunts); local flights (cruises, pickets, training, parades, tows, mining and salvage lingers, surface hops, maintenance) and formations; escape pod queue and launch |
| `flights/FleetFlights.java` | Fleet tied flights: service shuttles for fleets in orbit at a market, player trade shuttles, scavenger survey shuttles; routes per fleet, launches, ending a route |
| `flights/EventFlights.java` | One off flights: a maintenance run to a captured objective, settlers for a new colony, recovery tugs after a battle |
| `dogfights/ShuttleDogfights.java` | Watched NPC battles (at most 2): start rule, fleets and wings per side, round losses, battle end, `DogfightHost` for one battle |
| `dogfights/ShuttleDogfight.java` | One battle's fighters: launches, groups, targeting every 0.25 s, steering every frame, weapons, kills, replacements, the end; no game API, so the checks run it |
| `dogfights/Dogfighter.java`, `DogfightGroup.java`, `DogfightFleet.java`, `WingType.java`, `DogfightHost.java` | One fighter (heading, throttle, states, look); a lone fighter, pair or vic; a battle fleet copied every frame; a wing's hull, role and weapons; hit particles and refuge lookup |
| `dogfights/ShuttleProjectiles.java` | 256 slot ring buffer of guns, lasers and torpedoes per battle, swept hit tests, one `GL_LINES` batch |
| `flights/ShuttlePaths.java`, `BaseFlights.java` | Every path builder (lanes, service hops, redirect, flee, away, hold, screen, cruise, linger, loop); shared flight helpers (`canFly`, `create`, `follower`, `leaderTraits`) |
| `ShuttleAnchor.java` | One launch or landing place: type (`PLANET`, `STATION`, `FLEET`), role, pad depth, landing length, pad busy time, liveliness, near flag, measured velocity, threat state, meteor swarm state, density |
| `ShuttleRoute.java` | One route: kind, anchors, optional target or fleet, bend, in flight limit, timer or daily chance |
| `ShuttleTarget.java` | A point relative to an entity, for mining belts, debris fields, wrecks and objectives |
| `movement/ShuttlePath.java` | Segment chain with arc length tables: `BezierSegment`, `CircleSegment` (circle or rotated ellipse); waypoints on an anchor pad, on a fleet (`FleetWaypoint`), fixed, relative to an entity, or at the start or end of another segment |
| `movement/PathMover.java` | Moves a craft along its path: cursor, easing, slewed and capped path speed (`nextPathSpeed`), jitter, avoidance of slow fleets, near miss, virtual frame |
| `movement/FollowMover.java` | Moves a formation follower on its leader's history, blending in a craft that rejoins |
| `Shuttle.java` | One craft: state, traits, route, ends, stays, landing and docking look, docking queue, fleeing, stranded hold, picket screening, formation reform, disabled drift and wreck, rendering |
| `ShuttleState.java` | Craft states (`CRUISE`, `HOLDING`, `FLEEING`, `FLEEING_HOLDING`, `ESCAPING`, `STRANDED`, `SCREENING`, `DOWN`, `DISABLED`), pace, turn rate and the legal transitions; an illegal one throws |
| `ShuttleLook.java` | Immutable look per flight: glow, trail alpha, width and seconds, glow flicker, pod tumble |
| `ShuttleDepartures.java` | Departure cap: at most 3 craft leave one pad within 10 s, across all routes and trade bursts |
| `ShuttleBudget.java` | Cap split into lanes, local and rare groups: local reserve, scaled lane counts, room per group; fleet tied craft have their own cap of `setting / 4` on top |
| `SpeedClass.java` | Speed classes (`CIVIL`, `SERVICE`, `MILITARY`), the mean speed per class for launch intervals, `canReach` |
| `ShuttleTrail.java` | Trail (by time) and formation history (by distance) ring buffers relative to the virtual frame, trail rendering |
| `ShuttleFleetScan.java` | Pooled entries for fleets near the player, threat fleets and `ORBIT_PASSIVE` fleets in the location, with assignment, target and fleet type, and the distinct battles of the location, rebuilt every 0.25 s |
| `ShuttleHull.java` | Hull look (sprite, engine colours, cached per hull id), size, speed, flags, wing size |
| `ShuttleProfile.java`, `ShuttleFactionRepo.java` | One faction's flying style; the profiles from `shuttle_factions.csv`, read once per session into `SessionTransientMemory` (`$IndEvo_shuttleFactions`), the `pirate` flag |
| `ShuttleHullPool.java` | Base pool from the CSV, faction pools, crew rule, rolls per flight kind, fitted wing hulls, escape pod hull |
| `listeners/ShuttleColonyListener.java` | Transient `ColonyPlayerHostileActListener`, `ObjectiveEventListener`, `PlayerColonizationListener`, `ColonyDecivListener`: queues raid dropships, evacuation pods, objective maintenance runs, settlers and decivilization pods |
| `listeners/ShuttleCampaignListener.java` | Transient `BaseCampaignEventListener`: queues fleets destroyed in battle (pods) and player market transactions (trade shuttles); NPC battle rounds and ends for dogfights; recovery tugs after any battle in view |
| `indevo/tools/ShuttleTrafficChecks.java` | Headless checks, never shipped: constant speed, refresh and segment moves, star pass clearance, cruise circuits, surface hops, budget split and its local reserve, the departure cap per pad, liveliness, state transitions, trail length by time, service hops to a fleet in orbit and the turn back when it leaves, the speed cap and slew, battle round rate, a 12 against 12 dogfight, rush hours, meteor swarm distance |
| `data/config/indEvo/shuttle_traffic.csv` | Base civilian craft: `id, hullId, weight, small` |
| `data/config/indEvo/shuttle_factions.csv` | Faction profiles, see [Faction profiles](#faction-profiles) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Setting | `IndEvo_shuttleTraffic` (Int, 80, 0 to 120): most craft in flight in the current system; fleet tied craft fly on top, up to `setting / 4`; 0 turns traffic, fleet craft and escape pods off | `data/config/LunaSettings.csv:13`, `indevo/config/Setting.java:10`, read every frame in `ShuttleTrafficRenderer.advance` (`renderers/ShuttleTrafficRenderer.java:53`) |
| CSV | `data/config/indEvo/shuttle_traffic.csv`, read with `getMergedSpreadsheetDataForMod("id", path, "IndEvo")`; rows starting with `#` are skipped | `ShuttleHullPool.getBasePool` |
| CSV | `data/config/indEvo/shuttle_factions.csv`, read the same way; another mod adds a row for its own faction id | `ShuttleFactionRepo.load` |
| Sprites | `campaignEntities` / `fusion_lamp_glow` (engine glow); hull sprites from `ShipHullSpecAPI.getSpriteName()`, the vanilla `mining_drone` hull for escape pods | `ShuttleTrafficRenderer`, `ShuttleHullPool.getPod` (`:114`) |
| Config | `data/config/engine_styles.json` (merged): `engineCampaignColor` or `engineColor` for the glow, `contrailCampaignColor` or `contrailColor` at alpha 75 for the trail, `type` `SMOKE` for a normal blend trail | `ShuttleHullPool.getEngineLook` |
| Industry ids | `Industries.PATROLHQ`, `MILITARYBASE`, `HIGHCOMMAND`, `MINING`, `Ids.SWITCHABLE_MINING`, `Ids.ACADEMY`, `Ids.EMBASSY`, `Ids.SCRAPYARD`, `Ids.PIRATEHAVEN`, `Ids.REPAIRDOCKS`, `Ids.PORT` (Courier Port), `Ids.CHURCH` (Cathedral) | `ShuttleNetwork.addMarkets` (`:231`), `addMarketLanes`, `addPilgrims` (`:340`) |
| Condition and tags | `Ids.COND_PARADE` (Museum parade), `Tags.LUDDIC_SHRINE` (on markets and on entities, vanilla `CoreLifecyclePluginImpl.tagLuddicShrines`), `Ids.TAG_WATCHTOWER`, `Ids.TAG_ARTILLERY_STATION`, `MeteorSwarmManager.METEOR_TAG` (on every IE meteor, `MeteorEntity:71`) | `ShuttleNetwork` |

Base pool: `shuttlepod` 10 (small), `kite` 4 (small), `hermes` 2, `mudskipper` 2, `shepherd` 1, `ox` 1. Another mod adds rows by shipping the same path with its own ids.

## Registration and lifecycle

- `ModPlugin.onGameLoad() -> setListenersIfNeeded() -> ShuttleTrafficRenderer.register()` (`indevo/utils/ModPlugin.java:493`). Guarded with `LunaCampaignRenderer.hasRendererOfClass`; resets the hull pool caches, adds a transient renderer, a transient `ShuttleCampaignListener` (`Global.getSector().addTransientListener`) and a transient `ShuttleColonyListener` (`getListenerManager().addListener(x, true)`) (`renderers/ShuttleTrafficRenderer.java:30`).
- LunaLib's `luna_campaign_renderer` entity renders the plugin in every layer and only advances it while that entity is in the player's location.

`ShuttleTrafficRenderer.advance -> ShuttleTraffic.advance -> [location changed] network.rebuild -> network.updateNear -> fleetScan.scan -> reactions.updateThreats -> spawner.warmStart | [1 day] ShuttleHullPool.dropStaleFactionPools -> network.rebuild -> spawner.rollRareFlights -> [0.25 s] fleetScan.scan -> network.updateMotion -> network.updateNear -> [anchor came into range] spawner.warmStart(arrived) -> reactions.updateThreats -> reactions.react -> FleetFlights.update -> EventFlights.update -> ShuttleHits.scan -> ShuttleDogfights.update -> ShuttleHits.advance (run over) -> spawner.spawn -> PodFlights.launchQueued -> FleetFlights.launchQueued -> Shuttle.advance -> FollowMover or PathMover -> ShuttleDogfights.advance`

- Why poll: no callback covers industry changes on NPC markets, port disruption, new debris fields or wrecks, fleet positions (the player's included), spawned explosion entities (IE alone creates them at 15 sites) or an interdiction pulse, so the network rebuilds once a day and the fleet and hit scans run every 0.25 s. Both only run for the player's current location.
- A daily rebuild also drops a faction pool whose known ship or fighter count changed (`Faction.getKnownShips` returns the live set, knowledge skill `obf/campaign/Faction.java:431`).

## Flow

### Anchors and routes

`ShuttleNetwork.rebuild` (`:116`) on location change and once per day, with the sector clock's day for rush hours.

- A market counts if its primary entity is in the location, `isInEconomy()`, `hasSpaceport()`, not `isDiscoverable()`, and not `isHidden()` unless its faction profile is `pirate` (`hasTraffic`). Vanilla raider and Luddic Path bases stay hidden for life and start discoverable; `DiscoverEntityPluginImpl.discoverEntity` sets discoverable to null once the player finds them (knowledge skill `api/impl/campaign/DiscoverEntityPluginImpl.java:39`), so a found base gets traffic and an unfound one has none. A primary entity tagged `Tags.STATION` is a station anchor, otherwise a planet anchor.
- Density (`:617`): Megaport x1.5, Commerce x1.25, stability `clamp(0.5 + stability / 20, 0.5, 1)`, x0.5 while `MemFlags.RECENTLY_RAIDED` or `RECENTLY_BOMBARDED`. A disrupted Spaceport or Megaport closes the market anchor and its stations (`:611`).
- Liveliness (`getLiveliness`): how busy a market feels, `1.6 ^ (size - 5) * density`, x0.6 for a market of size 5 or less with Military Base or High Command and neither Megaport nor Commerce (a larger one is a world with a garrison), x0.8 for a station market. A size 5 market at density 1 is 1, Chicomoztoc (size 8, Megaport) about 6, Citadel Arcadia (size 4 military station) about 0.3. `ShuttleAnchor.liveliness` holds it times the rush hour; connected stations copy their market's. It drives every market demand below.
- Rush hours (`getRushHour`): density and liveliness times `1 + 0.3 sin(2 pi (day / period + phase))`, period 6 to 10 days and phase from a hash of the market id. Lanes, supply hops and mining follow it; cruises and pickets use liveliness without it; it changes with the daily rebuild.
- Near (`updateNear`, every scan and after each rebuild): an anchor is `near` while the player is within 4000 su of its surface, and stops being near past 4600 su; an anchor on screen at a zoom that still shows traffic is always inside that. Only near anchors launch local flights ([budget](#anchors-and-routes)). The player has no callback for moving, so it runs with the 0.25 s scan over the network's anchors, and the budget updates only when an anchor changed. When an anchor comes into range in a scan, `ShuttleSpawner.warmStart(arrived, player)` fills its local routes at spread progress, so the market is busy when the player gets there; a craft that would be placed within 3000 su of the player is left out and launches from its pad on the timer.
- Motion (`updateMotion`, every scan): `ShuttleAnchor.velocity` is the anchor's world velocity from its last two scan positions. Planets and stations move on orbits (a moon of a gas giant close to 40 su/s) and no API gives that speed; the fleet tied speed gate reads it.
- Meteor swarms (`updateSwarms`, `:86`, every scan): an anchor with an IE meteor within `600 su + radius` is `swarmed`, which makes it unusable (`ShuttleAnchor.isUsable`), so nothing launches there and craft bound for it divert like for a closed port. `BaseFlights.canFly` also refuses a flight whose straight line from start to end passes within 600 su of a meteor. Meteor swarms move and have no callback, so the scan reads `getEntitiesWithTag(METEOR_TAG)`; with no meteors in the system it costs one tag lookup.
- Anchors and routes not seen in a rebuild are dropped. Routes keep their key `kind|anchor id|destination id`, bend, timer and counts across rebuilds.

| Kind | Route | In flight (demand) | Group |
|---|---|---|---|
| `SUPPLY` | Planet market to each connected `Tags.STATION` entity | `clamp(round(liveliness), 1, 6)` | local |
| `HOP` | Local hops: every market to its 2 nearest bodies within 4000 su surface to surface (non star, non gas giant planets and moons, `Tags.STATION` entities; not its own connected stations), either way. A neighbouring market with traffic and a faction not hostile is the target as its own market anchor; an entity without a market becomes a `TARGET` anchor; decivilized worlds and markets without traffic (hidden bases) never. Built after every market anchor exists. Bodies orbit, so the targets can change with a daily rebuild. Craft fly their market's faction | `clamp(round(2.5 * liveliness), 1, 16)`, split between the bodies; the share to a neighbouring market times `clamp(its liveliness, 0.25, 1)` | local |
| `SURFACE` | Surface hops: a planet market to itself, between two pads 40 to 110 degrees apart, up over the limb and back down (`ShuttlePaths.surfacePath`, `LocalFlights.launchSurface`). The bustle of a hub with no neighbour in hop range (Chicomoztoc, Sindria). Not for station markets | `min(10, round(1.5 * liveliness))`, none at 0 | local |
| `PIRATE_CARGO` | Market of a `pirate` profile to each `DECIV` anchor | 1 | lanes |
| `SMUGGLER` | Market pair with hostile factions, neither pirate | 1, 1% per day, 3% with a Free Port at either end | rare |
| `DARK` | Market with Pather Cells or a Privateer Base to a random other planet; a hidden pirate base to the nearest `DECIV` anchor, else the nearest market, either way. Small craft, smuggler look | 1, 2% (cells), 3% (Privateer Base, the higher one wins) or 1% (hidden base) per day | rare |
| `DIPLOMAT` | Market with an Embassy to another faction's market, picked per flight | 1, 5% per day | rare |
| `HUNT` | `DECIV` anchor to another non star planet (IE ruins `IndEvo_RuinsCondition` weight 5, vanilla ruins 3, else 1), stays down 2 to 5 days, flies home | 1, 3% per day | rare |
| `MAINTENANCE` | Nearest same faction market to each comm relay, nav buoy, sensor array, IE watchtower or IE artillery station; owner is the entity's `getFaction()` (artillery stations get it from `ArtilleryStationEntityPlugin`, watchtowers from `WatchtowerFactionResetListener`); one lap at 100 su | 1, 2% per day | rare |
| `MARKET` | Market pair, factions not hostile, neither pirate. Convoys: see below | `max(1, round(chord / 500 * sqrt(livelinessA * livelinessB) * mean lanes))`: about one craft per 500 su of lane for two size 5 markets, and a lane is as busy as its quiet end allows; x2/3 per end with Pirate Activity | lanes |
| `COURIER` | Market lane with a Courier Port at either end: a Kite or Hermes at military speed, either way | 1 | lanes |
| `PILGRIM` | Every other market, not pirate and not hostile, to a shrine: a market with a Cathedral or `Tags.LUDDIC_SHRINE` on the market or its entity, or a tagged entity without a market of its own (Beholder Station) as a `TARGET` anchor. One way, a liner or cargo hull at 70% speed | 1 | lanes |
| `MINING` | Market with Mining to the nearest asteroid belt, ring or asteroid field; lingers 1 to 3 days | `clamp(round(1 + 1.5 * liveliness), 1, 6)` | local |
| `SALVAGE` | Market with Salvage Yards to the nearest debris field or wreck; lingers 2 to 4 days | `clamp(round(liveliness), 1, 3)` | local |
| `PICKET` | Market with Patrol HQ (1) or Military Base / High Command (2), times `pickets` and `clamp(liveliness, 0.34, 1.5)`, at least 1, +1 with Pirate Activity; one or two laps of an ellipse at `radius + 150..300` | wings | local |
| `CRUISE` | Every market and Remnant battlestation with a sight in range; none when the demand rounds to 0 | `round(cruises * min(3, liveliness))` (a tour, race or patrol counts once) | local |
| `TRAINING` | Market with an Academy and a sight; a loose group of 2 or 3 flies the cruise circuit | 1 group | local |
| `PARADE` | Market with the Museum parade condition: a wing of 3 faction fighters laps its own planet at `radius + 120` in tight formation | 1 wing | local |
| `TOW` | Planet market with Restoration Docks to its first connected station: two Ox or Mudskipper tugs side by side at 60% speed pull an unlit frigate of the faction (`ShuttleLook.TOWED`: no glow, no trail), either way; needs room for 3 | 1 group | local |

- Budget (`ShuttleBudget`, `ShuttleBudget.update` after every rebuild, when the setting changes and when an anchor's `near` changes): the network writes each route's demand, the budget sets `route.max`. Local flights launch only from `near` anchors: a local route of an anchor that is not near gets max 0, so nothing new launches there and craft already out finish their flight. Local flights reserve `min(local demand of the whole system, cap * 0.4)` craft, local demand counting `Kind.craftPerFlight` per flight (pickets and cruises 2, training 3). The reserve is held from lanes even with no market near: a lane flight takes minutes, and lanes that filled the whole cap left a hub empty when the player arrived. Lanes get the rest: when lane demand is higher, every lane's demand is scaled by `budget / demand`, at least 1. When the near local demand is over the reserve, every near local route is scaled the same way, at least 1. A group may launch into free room the other reserve does not hold; rare flights fit wherever there is room. Formations and races count each craft; pods are not counted. Fleet tied craft (`Group.FLEET`, routes in `ShuttleTraffic.getFleetRoutes`) have their own cap, `setting / 4` (20 at the default 80), on top of the setting, 0 when the setting is 0.
- Convoys (`LaneFlights.launchMarket`, `:66`): on a market lane with both markets at size 5 or more, every `3..6 days / mean convoys` (profile column; 0 means never) the launch is a convoy in place of one craft: 3 cargo hulls (`ShuttleDepartures.CAP`) in line astern 16 su apart, following the leader on the traffic lane, no escort. The first convoy waits a random share of one gap after the route is made, so the warm start never places one. A convoy needs room for 3 craft, else the launch is a single craft and the convoy stays due. It counts as one flight of the route.
- Warm start fills routes in rounds, one flight per route per round, so the first routes in the map do not take the whole cap.
- Departure cap (`ShuttleDepartures`): at most 3 craft leave one pad (anchor entity) within 10 s. A due timer or rare route gets `min(budget room, departure room of both ends)`; with no departure room it retries in 2 s. Flights of several craft (convoys, tours, wings, tows) never go over that room. Warm start places craft mid flight and does not count. Raid dropships, evacuation pods and escape pods are events and do not count.
- Timer routes (`ShuttleSpawner.spawn`, `:63`): lanes `(distance / mean speed + 6) / max * (0.6..1.4)` s, mean speed from the kind's `SpeedClass` (27 su/s civil), others `Kind.pause * (0.6..1.4)` s. Rare routes roll their chance once per day (`rollRareFlights`, `:56`) and launch when the roll is pending.
- Warm start fills every timer route to its limit at spread progress, trails (by age) and formation history pre-filled.
- Nothing launches from or to a threatened anchor, or from an anchor threatened in the last day (`BaseFlights.canFly`, `:40`).

### Faction profiles

`ShuttleFactionRepo.get(faction)`: the faction's row of `shuttle_factions.csv`, else the `default` row; empty cells take the default row, and a null faction (the checks) gets `ShuttleProfile.DEFAULT` without reading. Applied when a craft is created (`BaseFlights.create`, `Shuttle`), so a craft flies its own faction's style; lanes use the flying faction.

| Column | Effect |
|---|---|
| `pirate` | Pirate cargo hops to `DECIV` anchors, pirate refuges, no market lanes, hidden base rules |
| `dark` | Every craft of the faction flies the smuggler look (30% trail, no glow) |
| `laneDiscipline` | 1: market lane pads 8 degrees right of the line, two streams. 0: pads anywhere in +-40 degrees, no pass offset. Mixed in between |
| `jitter` | Mult on the lateral jitter |
| `speedSpread` | Per craft speed spread, +- this share |
| `weave` | Amplitude in su of a slow S curve along the path, one wave per 400 su |
| `formation` | Mult on formation slot noise; above 1 adds `(formation - 1) x 3` su |
| `queue` | FALSE: craft cut into a busy station pad instead of a holding lap |
| `lanes`, `cruises`, `pickets` | Mults on market lane counts (mean of both ends), cruise flights, picket wings |
| `convoys` | Divides the gap between convoys; 0 turns them off |
| `speed` | Mult on civil speed |
| `flicker` | Mult on engine glow flicker, at most full |

Rows: `default` (today's behaviour), `pirates` (pirate, loose lanes, jitter 3, spread 0.35, weave 8, formation 2, no queue, cruises 2, flicker 2), `hegemony` (jitter 0.5, formation 0.5, pickets 1.5), `tritachyon` (lanes 0.8, cruises 1.5, speed 1.2), `luddic_church` (cruises 0, speed 0.85), `luddic_path` (pirate, dark, loose), `sindrian_diktat` (formation 0.5, pickets 2). Faction ids checked against vanilla `data/world/factions`.

### Events

`EventFlights.update` after every scan, while no dialog shows; callbacks are queued.

- Objective changed hands (`ObjectiveEventListener`): the new owner's nearest market in the system flies one maintenance lap at once (rare group).
- Colonized (`PlayerColonizationListener`): for 5 days of the feature's own day count, one settler craft every 3 to 6 s to the new colony: from the player fleet as a service hop while it is within 2000 su, else from the nearest non hostile market. Fleet group.
- Decivilized (`ColonyDecivListener.reportColonyDecivilized`): 10 to 16 evacuation pods.
- Recovery (`reportBattleFinished`, any battle in the system with a fleet the player sees): `Battle.finish` clears both sides before the call, so the point is the radius weighted centre of `getSnapshotBothSides()`. The nearest market not hostile to the primary winner that reaches it within a day at military speed (`SpeedClass.canReach`, about 750 su) sends 1 or 2 Ox, Mudskipper or Shepherd to linger there for a day and fly home. Fleet group.

### Hulls

- Crew rule (`ShuttleHullPool.isCrewed`, `:235`): `getMinCrew() > 0`, no built in `HullMods.AUTOMATED`, no `Tags.AUTOMATED`; wings tagged `Tags.AUTOMATED_FIGHTER` are skipped. Remnant drone patrols skip the rule.
- Faction pool (`getFactionPool`, `:185`), cached per faction id and crew rule, dropped on the daily rebuild when the faction's known ship or fighter count changed: hulls of `getKnownFighters()` (wing size clamped 1 to 3) and the frigates of `getKnownShips()` without `STATION` or `MODULE` hints or `Tags.RESTRICTED`. Civilian hints (`CIVILIAN`, `FREIGHTER`, `TANKER`, `LINER`, `TRANSPORT`) mark a hull civilian; fighters and Kite skins are small. Weights: CSV weight, else civilian 3, fighter 2, other 1.
- Rolls: supply hops 80% base small craft, else faction civilian small craft; market lanes, local hops, smugglers and maintenance 60% base, else faction civilian; pirate cargo hops the pirate faction's small craft; diplomats a faction `LINER` or Hermes / Kite; hunts Shuttle Pod, Kite, Mudskipper; cruises a faction civilian `LINER` if the faction has one, else Kite / Shuttle Pod; pirate races the pirate faction's small craft; Remnant patrols any Remnant hull, never a base pool hull; pickets faction fighters, fallback Talon; training faction fighters or Kite / Shuttle Pod; mining faction `FREIGHTER` or Shepherd / Mudskipper / Ox; salvage Ox / Mudskipper / Shepherd.
- Engine colours (`ShuttleHullPool.getEngineLook`): `getEngineSlots()` on the game's hull spec class gives slot specs, not `EngineSlot`s; their one no arg method returning `EngineSlotAPI` (found by return type through `StarReflectionUtils.getMethodsMatching`) builds the `EngineSlot`, and `getStyleId()` on it names the style. A `CUSTOM` style resolves to its custom style id; an unknown style uses the slot colour. If reflection fails, manufacturer `Low Tech` / `High Tech` or `Remnant` / anything else maps to `LOW_TECH` / `HIGH_TECH` / `MIDLINE`, with one warning.
- Remnant craft are always blue: every entry of the `Factions.REMNANTS` pool (`ShuttleHullPool.forFaction`) gets its own look in the `HIGH_TECH` engine style, whatever the hull's own style, so another faction flying the same hull keeps its colours. Remnant rolls always use the faction pool and never fall back to the base pool; with no Remnant hull there is no flight.
- Unknown or uncrewed CSV hulls are dropped with a warning; a hull without a loadable sprite is skipped.

### Paths

- Market lanes (`ShuttlePaths.trafficLanePath`, `flights/ShuttlePaths.java:55`): one curve per market pair, bent the same way in both directions. Pads sit 8 +-1.5 degrees right of the live line between the anchors and jitter is +-3 su, so each direction keeps to its own side and a pair shows two steady streams about 60 su apart mid lane.
- Other lanes: one cubic Bezier between pads (`0.6 * radius` deep on planets, `0.15 * radius` on stations), evaluated live, so orbiting anchors carry the lane. Pads sit at a fixed offset from the live direction to the other anchor (+-40 degrees, scaled down to +-12 on lanes under 800 su), so the lane turns with orbiting anchors instead of stretching; handles `min(radius + 80, chord * 0.45)` along the outward radial. Smugglers launch and land on the far sides and bend twice as wide.
- Bend: control points shift along the chord normal by `bendFrac * chord`, `bendFrac` in +-`ShuttleRoute.LANE_BEND` (0.25) per route, also for the flight home after a stay; outside market lanes the two directions arc on opposite sides. `ShuttleNetwork.getBend` (`:141`) pushes the bend clear of any star (corona or event horizon outer edge, else `1.5 * radius`) or planet (`1.3 * radius + 30`). The bend is fixed at launch, so it only clears bodies for the geometry at launch.
- Star pass (`ShuttlePaths.lane`): lanes are evaluated live and a long flight lasts 10 to 30 days, so orbiting anchors can sweep a fixed lane through the star. When a star lies between the anchors (`ShuttleNetwork.getStarAcross`, `:115`: projection inside 5% to 95% of the chord, within `radius + chord / 2` of it), the lane runs through a `StarPassWaypoint` (`movement/ShuttlePath.java:108`): the point on the bent lane abreast of the star, pushed out to `1.1 * radius` on the side it started on, recomputed every evaluation, fading out once the star is no longer between the anchors. Market lane directions pass 30 su apart there. Join paths (redirect, flee, away, screen) do not avoid stars.
- Sights (`ShuttleNetwork.findSight`): another planet, moon or station within 3000 su of the anchor and at least 400 su surface to surface from it, never a star. Weight 3 for a gas giant or a planet with a `Terrain.RING` around it, else 1, times `1.25 - distance / 3000`. Kept on the anchor (`ShuttleAnchor.sight`) while it stays valid; no sight, no cruise or training route.
- Cruises (`ShuttlePaths.cruisePath`): the lane Bezier out (bend and star pass as for lanes), one `CircleSegment` lap at `1.5 * radius + 60` su around the sight at x0.9 speed (`Segment.withSpeedMult`), the lane Bezier home bent the other way, so the circuit is an oval. The lap starts at the sight's side toward home +-30 degrees and turns the way the bent lane arrives. One `Cruise` record holds the rolled circuit, so a race flies the same circuit twice.
- Cruise craft (`LocalFlights.launchCruise`): one craft, or a tour of 2 or 3 (35%) in line astern 14 su apart. Pirates race: two small craft on the same circuit, each with its own jitter and speed roll, the second without a route slot. Remnant battlestations fly it as a drone patrol of 2.
- Linger (`lingerPath`, `:178`): Beziers between points on circles of `wander * (0.8..1.2)` that turn 80 degrees per step, handles `0.5 * wander` (longer ones overshoot into cusps), drifting 40 su per step along a belt band; the count follows the linger days at cruise speed. Two spare points at each end let the loop start at the point heading most away from home and end at the one heading most toward it, so the transits need no u turn. Wander is 80 su for belts and fields, 60 to 120 su for debris fields, 70 su for wrecks.
- Jitter up to +-10 su (market lanes +-3) scales with `sin(pi * t)`. Paths that join a craft's current position to a pad or circle use handles up to 150 su. Arc length tables of 16 samples. A craft keeps its progress as a segment and an arc length fraction inside it (`ShuttlePath.Cursor`); the current segment's table is rebuilt every frame and the rest every 1 s, so a rebuild never moves the craft. Progress is distance based, so fast forward is safe.
- Frames: a craft stores its trail and formation history relative to a virtual frame (`PathMover.moveFrame`, `movement/PathMover.java:225`) that moves each frame by how far the current segment's start and end points moved, blended by `t * t * (3 - 2t)` on a Bezier (how its control points move) and the start on a circle. The trail then rides exactly like the path under the craft and stays in line with the sprite even between two bodies that orbit apart. A segment that starts where the last one ended carries on from that end. Every waypoint also names the entity it moves with (`Waypoint.getFrame`); paths that start at a craft's position (redirect, flee, away, screen, holds) start relative to the current segment's dominant entity.

### Formations

Every fighter group flies a V: pickets as a wing of `wingSize` (at most 3), training groups of 2 or 3, Museum parades of 3, Remnant patrols of 2. Line astern is only for cruise tours (2 or 3 liners) and convoy freighters. The leader records its position every 4 su (16 points); followers sit on that history `10 su * rank` back and `8 su * rank` out, alternating sides, and copy the leader's scale, alpha and layer. Training followers add +-4 su noise; one in four groups has a straggler that drops back up to 30 su while the leader turns and catches up on straights. Formations skip the docking queue. Followers sit on the leader's own track, so a wing goes around a slow fleet as one and keeps its V. A formation breaks up only on a near miss, a hit or a flee: every follower takes over the leader's path at its own slot, at +-10% speed, and spreads out to four times its slot offset. After 3 s in which neither it nor the leader is avoiding a fleet, a follower still on the leader's path and in `CRUISE` like it flies back into its slot, closing the gap over 1 s or at 20 su/s, whichever is slower (`Shuttle.tryReform`); it gives up after 30 s.

### Speed and avoiding fleets

- Speed cap and slew (`PathMover.nextPathSpeed`, `SpeedClass.WORLD_CAP`, `SLEW_UP`, `SLEW_DOWN`): the path speed moves toward the wanted speed (cruise x state pace x ease) at 12 su/s per second up and 20 down, then is cut so path speed plus last frame's carry (world motion beyond the path speed: moving ends, sideways moves) stays at 75 su/s. Fleeing (x1.4), pod ejection boosts and frame carry all stay inside it. A new path keeps the current speed; the warm start and the first frame snap to the wanted speed.
- `ShuttleFleetScan.scan` every 0.25 s also keeps every fleet with `FleetAssignment.ORBIT_PASSIVE`, with its assignment, target and `MemFlags.MEMORY_KEY_FLEET_TYPE`, for fleet tied flights. Obstacles are fleets that are not `isStationMode()`, within 3000 su of the player, and either the player fleet or 5 or more ships. Fleeing craft also avoid hostile threats. A craft tied to a fleet never avoids that fleet.
- Avoidance (`PathMover.getAvoidanceTarget`, as in stage 4): only fleets slower than 40 su/s, only when the craft's path point lies within `fleet radius + 40` along the path. The craft eases sideways at 20 su/s to the side it is already on until it clears that circle, at most 1.5 times the radius; the largest offset of all fleets wins. No braking, no surge. The offset fades over the launch and landing part of a path.
- A craft does not get out of the way of a faster fleet; it flies on. Near miss: a fleet at 100 su/s or more within its own radius of the craft gives a 10 su wobble over 2 s and breaks the formation. A fleet at burn 14 or more can run it over (Hits).

### Hits

`ShuttleHits`, visual only: no reputation, no loot, no effect on any fleet. A destroyed craft frees its route slot and the route relaunches on its timer.

- Explosions: the 0.25 s scan walks `getCustomEntities()` for plugins that are an `ExplosionEntityPlugin` (vanilla `Entities.EXPLOSION`, IE `VariableExplosionEntityPlugin` and `MineExplosionEntityPlugin`). Blast radius `(getRenderRange() - entity radius) / 3`, since `ExplosionEntityPlugin.getRenderRange` returns the entity radius plus three blast radii and `params` is protected (knowledge skill `api/impl/campaign/ExplosionEntityPlugin.java:267`); 100 su if that gives nothing. A blast hits during its first 1 s after the scan first saw it.
- Run over: every frame, scan fleets at burn 14 or faster (280 su/s); a craft within half the fleet radius rolls once per pass, 25% at 280 su/s up to 75% at 400 su/s: explode on a failed roll, near miss otherwise. The roll resets once the craft is out of the fleet radius. Craft tied to the fleet (trade, service, survey, raid and settler craft) are spared. Only fleets in the scan count (the player, fleets of 5 or more ships near the player, threats).
- Interdiction pulse: `InterdictionPulseAbility.applyEffect` sets `MemFlags.GLOBAL_INTERDICTION_PULSE_JUST_USED_IN_CURRENT_LOCATION` on the sector and `JUST_DID_INTERDICTION_PULSE` on the fleet for 0.1 day (knowledge skill `api/impl/campaign/abilities/InterdictionPulseAbility.java:228`). The scan reads the sector flag, then each flagged fleet once; craft within `InterdictionPulseAbility.getRange(fleet)` explode one in four, the rest are `DISABLED` for 3 to 6 s. Craft tied to the pulsing fleet are spared.
- `DISABLED`: no glow, no new trail, the craft drifts on its last world velocity losing 30% per second and turns 10 to 25 degrees per second, then restarts on a path from where it is to its destination, else home, else it is removed.
- Explode: 3 hit particles (`LocationAPI.addHitParticle`, 15 to 30 su, white and the engine colour), the sprite drifts and fades over 0.5 s. A crewed craft (not a pod, not Remnant) drops one escape pod half the time toward the nearest planet in pod range (`PodFlights.launchFromWreck`).

### Dogfights

`ShuttleDogfights`, visual only: nothing in a battle changes, the fighters follow it. Own cap outside the setting: at most 12 fighters per side, 2 battles at once; none when the setting is 0.

- Which battles: the 0.25 s scan collects the distinct `CampaignFleetAPI.getBattle()` of the location. A battle starts dogfights when it is not done, the player is not in it and at least one of its fleets has `getVisibilityLevelToPlayerFleet()` other than `NONE`.
- Round rule: `Battle.advance` runs an autoresolve round every day times `max / (2 x min)` fleet points of the two sides (`getFleetPoints()`), clamped 1 to 5, x0.25 with a station (knowledge skill `obf/campaign/fleet/Battle.java:343`). A battle starts only at 1 round a day or slower (sides within 2:1, within 8:1 with a station); a battle that fails at first sight is never picked again. Re-checked every scan: past the limit, or out of sight, no more groups launch.
- Wings: each fleet flies the wings its members carry (`ShuttleDogfights.getFittedWings`, `ShuttleHullPool.getWingHull`, Remnant wings blue). A side that carries none flies up to 4 of each fleet's faction's known fighters; a faction that knows none launches nothing. No crew rule. Role from `FighterWingSpecAPI.getRole()`: `BOMBER` strikes, `SUPPORT` holds within `fleet radius + 150` of its fleet, the rest escort. Weapons from the wing variant's fitted slots: energy fires lasers, ballistic guns, missile torpedoes; an unarmed wing fires guns.
- Groups: per side `clamp(ships / 3, 1, 4)`, launched over 3 s from a random fleet of the side; a lone fighter (40%), pair (35%) or vic (25%). A side without bombers sends every third group to strike. A lost group is replaced 4 to 8 s later. A fighter still launching (1.5 s, fading in over 0.6 s) is not picked as a target.
- Flight: no path; each fighter turns toward a wanted heading at 120 degrees per second and eases at 40 su/s per second to a throttle of 60% to 120% of `2.2 x` its hull's cruise speed, never above 75 su/s. States every 0.25 s: pursue (nearest enemy in the front half, else nearest; an enemy already chased by other groups counts as farther), evade (an enemy within 200 su behind with its nose on me: weave), extend (after an overshoot or 3 s of evading, 1 to 2 s straight), strafe (out to `fleet radius + 250` su near its own side, run at a point within 0.6 of the fleet radius, break past it at 100 su, come around from a new angle), support, form (wingmen keep formation while the leader strafes or launches, then take the leader's target or whoever is on its tail). Escorts take enemy strike craft within `fleet radius + 400` of an own fleet first. Leash: past `battle radius + 300` su from `BattleAPI.computeCenterOfMass()` a fighter turns back.
- Weapons (`ShuttleProjectiles`): guns, rows of 3 to 5 thin tracers (6 su, 1 px) 0.15 s apart at 500 su/s for 0.6 s, +-0.5 degrees along the nose when the target is within 20 degrees and 300 su, so in a turning fight the stream trails behind it. Lasers, 2 or 3 bolts (6 su) at 900 su/s for 0.4 s in the wing's glow colour, aimed at the lead point when the target is within 8 degrees and 250 su. Strafing runs fire from 300 su to 100 su; torpedoes, one per run from 250 su at 200 su/s for 2 s. 1.5 to 3 s between bursts. Hits: fighters at half the sprite length + 2 su, fleets at 0.7 of the fleet radius, one hit particle (10 su on fighters, 14 on fleets, 30 for torpedoes). A fighter dies after 2 or 3 hits: 3 hit particles of 20 to 35 su, then the sprite drifts and fades over 0.5 s.
- Rounds: `reportBattleOccurred` after each autoresolve round (`Battle.doAutoresolveRound`, obf `:448`); `takeSnapshots` runs at the start of each round, so `Misc.getSnapshotMembersLost` gives that round's losses. Each lost ship: 3 hit particles of 25 to 45 su on its fleet, and 1 escape pod for a crewed ship of a fleet that survives (destroyed fleets fire theirs through `reportFleetDespawned`). The side that lost more fleet points loses one group within 0.5 s.
- End: `reportBattleFinished` gives the winner, otherwise a battle that is done, gone, or joined by the player ends with the side that has fleets and more fleet points. Fighters whose fleet is still in the system fly back and fade into it, the winners after a 2.5 s loop. The rest fly to `ShuttleNetwork.findRefuge` within 1500 su and fade into it, or explode within 1.5 s. Shots in flight finish.
- Cost: targeting is O(n^2) per 0.25 s; shots test every enemy fighter and fleet per frame. About 10 us per frame for a full 12 against 12 battle in the checks.

### Danger

- Threats (`ShuttleFleetScan.Entry.isHostileThreat`, `:34`): the player fleet, and fleets with at least 30 fleet points; a threat counts against a faction it is hostile to.
- Anchors (`ShuttleReactions.updateThreats`, `:20`): threatened with a hostile threat within `radius + 600` su, overrun within `radius`. `lastThreatDay` is kept; Remnant anchors are never threatened.
- Craft (`react`, `:36`): a craft flees when a hostile threat is within 400 su or its destination is threatened. A fleeing craft flees again only when its refuge becomes threatened.
- Refuge (`ShuttleNetwork.findRefuge`, `:92`): nearest usable, unthreatened, non hostile anchor, other factions x1.5 distance; `pirate` profiles only use pirate markets and `DECIV` anchors, everyone else neither; Remnant and `TARGET` anchors (hidden bases) never.
- Disarray (`Shuttle.flee`, `:165`): heading kick of up to +-70 degrees, one in five flies a 60 to 90 su panic circle first, speed x1.4, turn rate x2, a 10 su swerve fading over 3 s. Formations break up and each craft flees alone. A refugee lands, waits hidden and flies home once its home has been safe for 1 day. While it waits it holds no cap slot (`Shuttle.isWaitingAtRefuge`); after 3 days it gives up and is removed, which frees its route slot.
- No refuge: fly 800 to 1500 su away from the threat, then hold a 150 su circle at 60% speed until no hostile threat has been within 800 su for 1 day, then fly home.
- Pickets never flee from nearby threats: while their market is threatened they fly 120 su circles at 60% speed centred `radius + 250` su toward the threat, and land once it leaves. If the market is overrun they scatter like the rest. Remnant drones never flee.
- A closed port (disrupted, or the anchor dropped) diverts inbound craft calmly to a refuge without disarray.

### Fleet service shuttles

`FleetFlights.update` runs after every fleet scan (0.25 s). No listener covers it: `reportFleetReachedEntity` fires on arrival only and misses fleets spawned in orbit, fleets already orbiting when the player enters, and the end of the orbit.

Fleet tied flights (service, trade, survey, dropships, settlers from the player fleet) only fly to a fleet slower than 40 su/s against the pad it is at (`SpeedClass.FLEET_TIED_MAX_SPEED`, `FleetFlights.isFast(fleet, pad)`: fleet velocity minus `ShuttleAnchor.velocity`, world speed when there is no pad anchor). A fleet in orbit moves with its planet, so world speed alone turned service craft off at fast moons. When the fleet gets faster, craft bound for it turn back to where they came from, or the nearest refuge, else they are removed (`FleetFlights.turnBackFromFastFleets`); craft from it carry on. The world cap cuts the craft's path speed where the fleet's motion would carry it past 75 su/s.

- A fleet gets a `FLEET_SERVICE` route (key `FLEET_SERVICE|<fleet id>` in `ShuttleTraffic.getFleetRoutes`) while all hold: assignment `FleetAssignment.ORBIT_PASSIVE` (vanilla: trade and smuggling fleets loading or unloading, patrols preparing or standing down, route fleets at their source), not the player, not in station mode, visible to the player (`getVisibilityLevelToPlayerFleet()` not `NONE`), the assignment target is a market anchor or the fleet is within 400 su of one, the anchor is usable and not hostile to the fleet, the fleet is slower than 40 su/s against that anchor, and it is within 1500 su of the anchor's surface.
- Craft in the air: trade and every other type `clamp(2 + ships / 3, 3, 8)` times the anchor density; `FleetTypes.TRADE_SMUGGLER` 2 or 3 with the smuggler look and the far side pad; `PATROL_SMALL`, `_MEDIUM`, `_LARGE` 2 or 3 at civil speed. Launch interval as for lanes, flight time / count.
- Each flight is one way, 50/50 pad to fleet or fleet to pad. Pad to fleet: 70% base small craft, else the pad faction's civilian small craft. Fleet to pad: the fleet faction's small craft.
- Service speed (x1.8 of civil, 54 su/s fighters), easing to 15% at the ends, 1 s trail, steadier glow (`ShuttleLook.SERVICE`). Path (`ShuttlePaths.servicePath`): one Bezier from a pad turned toward the fleet (+-30 degrees) to a `FleetWaypoint`, the fleet's location plus a fixed random offset within half its radius; handles `min(80, distance * 0.35)`. Fleet end at the fleet, planet or station look at the pad, no docking queue. They never avoid their own fleet, avoid others and flee like other traffic.
- Route end (the fleet's assignment changes, it moves past 1500 su, despawns or is no longer visible): no more launches; craft flying to the fleet turn back to the pad on a path that starts where they are (a fleet frame is never used for the turn); craft from the fleet carry on. If the fleet is gone or its sensor fader is at 0, all its craft are removed.

### Player trade shuttles

- `ShuttleCampaignListener.reportPlayerMarketTransaction` sums cargo space, fuel and personnel bought and sold per market while the dialog is open (`FleetFlights.queueTrade`). The submarket is the one the trade screen traded with (`getSubmarketTradedWith()`, knowledge skill `obf/campaign/ui/class.java:693`).
- When no dialog shows (`FleetFlights.launchQueued`): if the market's primary entity is a traffic anchor and the player is within 600 su of it, `clamp(moved / 200, 2, 3)` craft launch over 3 s, pad to fleet bursts also within the pad's departure room (`Shuttle.holdFor`), pad to fleet when more was bought, else fleet to pad. Service speed and look; black market trades (`Submarkets.SUBMARKET_BLACK`) or a transponder off give dark craft with the smuggler look.
- A `TRADE` route per market pad holds the burst (the spawner never launches it); when the player moves more than 600 su from the pad or reaches 40 su/s, craft flying to the fleet turn back. Trades and raids queued while the player fleet is at 40 su/s or more are dropped.

### Scavenger survey shuttles

- A scavenger fleet (`FleetTypes.SCAVENGER_SMALL`, `_MEDIUM`, `_LARGE`) that is "investigating" has `FleetAssignment.PATROL_SYSTEM` with a target entity (`ScavengerFleetAssignmentAI:63`). While it is visible, within 600 su of the target's surface and slower than 40 su/s, it gets a `SURVEY` route (`FleetFlights.updateSurvey`) that launches 1 to 3 shuttles once, 3 s apart, and keeps the route while the fleet investigates so it never sends more.
- Shuttle Pod, Kite or Mudskipper, service speed, hunt look. To a planet: out of the fleet to a pad, stay 1 to 2 days, then a lane back into the fleet. To anything else: out of the fleet, two laps 60 su outside the target at 60% speed, back into the fleet (`ShuttlePaths.surveyLapPath`).
- When the fleet stops investigating, its shuttles fly back on their own; if it despawns, leaves or is out of sight, they are removed.

### Raids and bombardments

- `ShuttleColonyListener`: `reportRaidForValuablesFinishedBeforeCargoShown` and `reportRaidToDisruptFinished` queue 4 to 8 dropships (`FleetFlights.queueRaid`); `reportSaturationBombardmentFinished` queues 12 to 20 evacuation pods, `reportTacticalBombardmentFinished` 4 to 6 (`PodFlights.queueEvacuation`, planets only). Both launch once no dialog shows.
- Dropships: the player faction's fighters (fallback Talon), military speed, from the market's pad to the player fleet, 3 s apart, fleet tied (fleet cap), never flee.
- Evacuation pods: the escape pod hull at 35 su/s, no tumble, on lanes from the planet to a random other usable market anchor, 0.25 s apart, in the pod list (cap 32). No pods without another port.

### Escape pods

- `ShuttleCampaignListener.reportFleetDespawned` on `FleetDespawnReason.DESTROYED_BY_BATTLE`, not the player fleet, in the player's location. `CampaignFleet.despawn` reports while the fleet is still in place (knowledge skill `obf/campaign/fleet/CampaignFleet.java:1599`).
- Also: 1 pod per crewed ship a fleet loses in a watched dogfight round while the fleet survives ([Dogfights](#dogfights)), and 10 to 16 evacuation pods from a decivilized colony ([Events](#events)).
- Bursts (`PodFlights.getBursts`): from `getFleetData().getSnapshot()`, crewed non automated members only, one burst per ship: 2 pods per frigate or destroyer, 3 per cruiser or capital, at most 16 pods per fleet; an empty snapshot gives one burst of 3.
- Away heading (`getAwayHeading`): `FleetEncounterContext` passes the `BattleAPI` as the despawn param (knowledge skill `api/impl/campaign/FleetEncounterContext.java:827`); pods fan away from the centre of `battle.getOtherSideFor(fleet)`, else from the nearest hostile threat in the scan within 3000 su, else in random directions.
- Planet (`findPodPlanet`): nearest non star planet whose surface is within 2000 su; a non hostile market planet in range wins over a closer one.
- Queued until `getCampaignUI().isShowingDialog()` is false and the game is unpaused, dropped if the player left the location. At most 32 pods in flight.
- Each burst (`PodFlights.launch`): starts within 1.5 s of the event, its pods 0.3 s apart on one shared curve (one heading inside an 80 degree fan, one pad offset and handle), each with +-6 su jitter, so the burst reads as a string. The vanilla `mining_drone` sprite at 3 su long with its own engine colours (no pods if a mod removed the hull). Pods shoot out at 80 to 120 su/s easing to 35 su/s over 2 s, tumbling at 14 to 84 degrees per second; the tumble decays over those 2 s and the sprite angle blends into the heading. No trail and no glow while tumbling; then a small glow and a short thin contrail. Pods curve to a random point on the planet disc and land with the planet look. Pods ignore fleets and threats.

### Look

- Hull sprites at 0.18 (fighters) and 0.075 (frigates) of sprite size (`ShuttleHull.drawScale`), and the longer side never below 5 su, aspect kept (`ShuttleHull.MIN_SIZE`). Sizes are for looks, not to scale: fighters come out about as large as frigates, the Shuttle Pod hull at 4.1 x 5 su. Escape pods keep their fixed 3 su. Speed classes (`SpeedClass`, set per flight): civil 30 su/s fighters and 24 su/s frigates (lanes, supply hops, cruises, mining, salvage, hunts, pirate hops, diplomats, smugglers, maintenance); service x1.8 of civil (fleet service, trade, survey); military 60 su/s for every hull (pickets, training, parades, couriers, pirate races, Remnant patrols, dropships). +-15% per craft (the profile's `speedSpread`), civil speed times the profile's `speed`, easing to 35% over launch and landing. Hunts, mining and salvage x0.8. Burn 4 is 80 su/s and no craft moves faster than 75 su/s, so traffic never keeps up with a fleet that is going somewhere.
- Reach rule (`SpeedClass.canReach`): a flight to a target that expires only launches when `distance / speed` fits in the days left. No flight uses it yet.
- Everything draws in `TERRAIN_7`: above planets, rings, asteroids and stations, below fleets. Planet launch and landing over `1.2 * radius` (at most 40% of the path): scale to 0.5, 30% darker, and the craft fades only while over the surface, from the limb to the pad. Station docking over 60 su: scale to 0.7, alpha to 0. A craft pulled out of a landing (fleeing, holding) fades back over 1 s.
- Facing follows the path tangent plus the rate of the sideways offset, turning at most 90 degrees per second, never the world step, so orbiting anchors do not swing the sprite or trail.
- Trail by time (`ShuttleTrail`): where the rear was during the last `ShuttleLook.trailSeconds` (craft 1.5 s, pods 0.8 s), so its length is `speed * trailSeconds`: about 36 su for a civil frigate, 112 su for military craft, 13 su while easing off a pad. Points carry an age, one every `trailSeconds / 15` s from a time accumulator (16 points plus one past the cut); points older than `trailSeconds` drop and the tail is cut at exactly `trailSeconds` between the last two points. Ages only advance in `advance`, so a paused trail holds still. Untextured `GL_QUAD_STRIP`, alpha and width fade by age, the oldest point always fades to nothing. Head width `min(3 su, 0.5 * drawn width)`, so it thins as the craft descends; alpha times throttle, `clamp(speed / class cruise speed, 0.4, 1)`. Engine style colour and blend. The warm start fills the trail by age. Smugglers: 30% trail, no glow. Hunts: 50% trail, heavier glow flicker.
- Fleet end (`Shuttle.End.FLEET`, an anchor of type `FLEET` around the fleet): over the last or first 30 su the craft shrinks to 0.7 and fades, like station docking. Craft tied to a fleet fade with its `getSensorFaderBrightness()`. Easing at the ends goes down to 35% of cruise speed, 15% for the service class (`SpeedClass.minEase`).
- Engine glow at the rear, `0.7 * drawn width`, flickering (rolled in `advance`, so it holds still while paused), 1.3x for 2 s around launch and landing, none while disabled or wrecked. No floor: a glow is never wider than its craft, pods included. No blinking lights.
- Traffic fades out between view mult 2 and 3 (max campaign zoom is 3). Off viewport craft (300 su margin) are skipped.

### Docking queue

A station pad is busy while a craft is in its last 100 su and 1 s after. A craft that reaches that point while the pad is busy flies a 120 su circle around the station, tangent to its heading, and checks again after each lap; when free it claims the pad and flies a short Bezier in. Formations and pods skip the queue.

## Contracts

- Transient only: no entity, listener field, memory key or script lands in the save. Removing IE never breaks a save through this feature. Hit particles are saved with the location (`BaseLocation.hitParticles`), vanilla classes only, gone in under a second.
- `ShuttleDogfight` and its fighter, group and projectile classes use no game API; everything they need from the campaign goes through `DogfightHost` and `DogfightFleet`.
- Craft state changes go through `ShuttleState` (`hold`, `redirect`, `flee`, `screen`, `land`, `strand`, `calm`, `disable`, `restart`); a change not in its table throws `IllegalStateException`. Reactions skip disabled and wrecked craft.
- `ShuttleFlights` throws at construction when a `ShuttleRoute.Kind` has no flight.
- `ShuttleFleetScan.Entry` objects are pooled and reused every scan; never keep one past the frame. `clear` drops the fleet references.
- `ShuttleTrail.render` must run between `ShuttleTrail.beginRender` and `endRender`.
- Advancing stops while `Global.getSector().isPaused()` (`renderers/ShuttleTrafficRenderer.java:51`); the feature keeps its own day count from `convertToDays`, so stays and safe times only pass while the player is in a system with traffic.
- `ShuttleHullPool` caches are static and reset on every `register()`.
- `getHullSpec` throws on unknown ids, so every CSV and `getKnownShips()` id is checked against `getAllShipHullSpecs()` first.
- The engine style reflection names (`getEngineSlots`, `getStyleId`) are unobfuscated public methods of the game's hull spec and `EngineSlot` classes (knowledge skill `obf/loading/specs/_c147.java`, renamed from `g.java`, and `EngineSlot.java:47`). The slot spec class in between is obfuscated (`obf/loading/specs/_c145.java`) and reached only by return type; never use an obfuscated name here. Checked against `lib/starfarer_obf.jar`: one public no arg method on the slot spec returns `EngineSlot`.
- Remnant battlestations are fleets in station mode with `MemFlags.MEMORY_KEY_FLEET_TYPE` `FleetTypes.BATTLESTATION` and faction `Factions.REMNANTS`.

## Known issues and dormant code

| Item | State |
|---|---|
| In game look | Tests (2026-11-08) fixed: rubberbanding, nervous trails, blinking lights, loop joyrides (now cruises), craft vanishing under planets, sprite size, trails out of line with the craft between moving bodies, lanes through the star. Untested in game: star pass, market lanes, blended trail frame, cruises, pod bursts, formation break up, trails by time, sizes and glow, engine colours from slots (before this every hull used manufacturer colours, the slot lookup always failed), formation reform, hits |
| Calm down (stage 10) | Untested in game: speed cap and slew, stage 4 avoidance back in place of dodging, V for every fighter group, stalled trails fading, dogfighter turn rate and speed, thinner gun rows. Not found by reading: which craft flew in lines at Diktat and Hegemony markets; the likely source, escort wings in single file, is removed |
| Layer | Everything draws in `TERRAIN_7`, below fleets, dogfighters included; whether they need a layer above fleets is open for the live test |
| Profiles and events | Untested in game: faction profiles, free port, pirate activity, Pather cells, hidden bases, objective runs, settlers, decivilization pods, recovery tugs |
| More flights | Untested in game: couriers, pilgrims, Museum parades, Restoration Docks tows, Privateer Base dark craft, watchtower and artillery maintenance, convoys, meteor swarm closures, rush hours. IE meteor swarms only roll in uninhabited systems, so the closure mostly matters for hidden bases, Remnant patrols and hunts. Craft already flying a lane that a swarm crosses keep flying; only their ends divert |
| Dogfights | Untested in game. Not verified: whether NPC fleet variants carry their final wings before the fleet is inflated near the player, how long NPC battles last in practice. The check fight is a meat grinder: fighters live about 10 s and a side that falls behind loses each new group soon after launch |
| Run over | Craft do not get out of the way of fleets at 40 su/s or more, so run over can hit any craft in a fast fleet's way; how often is open for the live test |
| Checks | `ShuttleTrafficChecks` covers paths, cruise circuits, the budget split, the departure cap, states, trail length, service hops, the speed cap and slew, a dogfight, rush hour range and the meteor swarm distance, not flights, reactions, hits or rendering. It runs on its own, not in `ReleaseBuild` |
| Performance | Live smoke `trafficperf` group (2026-11-12, `--perf`, after stage 10): worst case in Askonia at 120 (about 100 craft, 32 pods, 2 dogfights with 30 fighters, 6 fleet routes) ran at 5.5 ms per frame against 6.2 ms with traffic off in the same scene; all of `shuttletraffic` 0.9% of CPU and 1.4% of allocation over the run. Frame time is too noisy to show the feature's own cost, and JFR does not see GL driver time |
| Density | Live smoke `traffic` and `trafficapproach` groups (2026-11-12, setting 80). Parked 500 su off the market, mean craft within radius + 800 su: Chicomoztoc 30, Sindria 38, Jangala 24, Citadel Arcadia 8.5 (lanes passing included). Flying in after 60 s far out: Chicomoztoc 32 on arrival, Sindria 26, Jangala 25. Untested in game by eye: whether that reads as a hub against a fringe station, and how surface hops look |

## Checks

After the compile gate (`java jars/src/indevo/tools/ReleaseBuild.java compile`), from the repository root:

```sh
java -cp "out/release-build-<n>/classes:lib/*" indevo.tools.ShuttleTrafficChecks
```

Use `;` in place of `:` on Windows. It prints `Shuttle traffic: N checks passed` or every failure and exits 1. It refuses to run inside the game. In IntelliJ: an Application run configuration with main class `indevo.tools.ShuttleTrafficChecks`, module `IndEvo`, working directory the repository root.

The native smoke `shuttle` group ([SMOKE_TESTS.md](../SMOKE_TESTS.md)) reads the craft list through `ShuttleTrafficRenderer.getTraffic().getShuttles()`: ms per frame, craft per route kind, and a check that local flights still launch. The `traffic` group counts route demand, max and active per market and the craft near each market; `trafficperf` is the worst case for performance.

## Maintenance

Update this doc when a flight kind, the anchor rules, the hull pool, the CSV columns of either file or the setting changes. Setting mechanics belong in [Settings](SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
