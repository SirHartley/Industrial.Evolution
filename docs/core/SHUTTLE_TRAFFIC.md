# Shuttle traffic

Visual only small craft that fly curved lanes between the markets of the player's current star system and between a planet and its orbital station. Read it before touching `indevo/ambience/shuttletraffic/`, `data/config/indEvo/shuttle_traffic.csv` or `IndEvo_shuttleTraffic`. It does not cover real fleets or trade routes; nothing here changes the economy.

Checked against Starsector 0.98a-RC8, LunaLib 2.0.1 (`lib/LunaLib.jar`) and IE `eec387b`. Recheck engine facts after a game update.

## Overview

One transient LunaLib campaign renderer owns everything: anchors, lanes, craft, trails. Nothing is an entity, nothing is clickable, nothing is saved. Only the player's current location is simulated; hyperspace has no traffic.

- Supply hops: a planet market to each of its orbital station entities and back. Small craft only.
- Market lanes: between every pair of markets in the system whose factions are not hostile and that are not pirate owned. Civilian craft only.
- Craft launch from the planet disc (shrinking out of it) or from under the station sprite, climb out, follow a gentle per lane arc around stars and planets, and descend into the destination at the far end. Planets have no queue; a station has one pad and late arrivals fly a holding circle.
- Craft swerve around slow fleets (the player always, others with 5 or more ships) and drift back onto their lane.

## Files

| File | Owner / role |
|---|---|
| `indevo/ambience/shuttletraffic/ShuttleTrafficRenderer.java` | `LunaCampaignRenderingPlugin`: registration, location change, daily rebuild, fleet scan, spawning, warm start, diversion from closed ports, render per layer |
| `ShuttleNetwork.java` | Anchors and routes for one location: market anchors, station anchors, supply and market routes, density, closed ports, refuge lookup, star and planet avoidance (`getBend`) |
| `ShuttleAnchor.java` | One planet or station a craft can launch from or land at: pad depth, landing length, pad busy time, `isUsable` |
| `ShuttleRoute.java` | One lane: kind (`SUPPLY`, `MARKET`), both anchors, bend, in flight limit, spawn timer, cap share |
| `ShuttlePath.java` | Segment chain with arc length tables: `BezierSegment`, `CircleSegment`; waypoints that follow an anchor (`PadWaypoint`) or stay fixed (`FixedWaypoint`) |
| `Shuttle.java` | One craft: progress, easing, jitter, fleet avoidance, facing, landing and docking look, docking queue, trail ring buffer, rendering |
| `ShuttleFleetScan.java` | Slow fleet obstacles near the player, rebuilt every 0.25 s |
| `ShuttleHull.java` | Sprite, size, speed, civilian and small flags, engine glow and trail colours for one hull |
| `ShuttleHullPool.java` | Base pool from the CSV, faction pools, crew rule, sprite loading, engine colours |
| `data/config/indEvo/shuttle_traffic.csv` | Base civilian craft: `id, hullId, weight, small` |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Setting | `IndEvo_shuttleTraffic` (Int, 40, 0 to 80): most craft in flight in the current system, 0 turns traffic off | `data/config/LunaSettings.csv:13`, `indevo/config/Setting.java:10`, read every frame in `ShuttleTrafficRenderer.advance` (`:69`) |
| CSV | `data/config/indEvo/shuttle_traffic.csv`, read with `getMergedSpreadsheetDataForMod("id", path, "IndEvo")`; rows starting with `#` are skipped | `ShuttleHullPool.getBasePool` (`:76`) |
| Sprites | `campaignEntities` / `fusion_lamp_glow` (engine glow and navigation lights); hull sprites from `ShipHullSpecAPI.getSpriteName()` | `ShuttleTrafficRenderer.java:37`, `ShuttleHullPool.loadSprite` |
| Config | `data/config/engine_styles.json` (merged): `engineCampaignColor` or `engineColor` for the glow, `contrailCampaignColor` or `contrailColor` at alpha 75 for the trail, `type` `SMOKE` for a normal blend trail | `ShuttleHullPool.getEngineLook` (`:215`) |

Base pool: `shuttlepod` 10 (small), `kite` 4 (small), `hermes` 2, `mudskipper` 2, `shepherd` 1, `ox` 1. Another mod adds rows by shipping the same path with its own ids.

## Registration and lifecycle

- `ModPlugin.onGameLoad() -> setListenersIfNeeded() -> ShuttleTrafficRenderer.register()` (`indevo/utils/ModPlugin.java:493`). Guarded with `LunaCampaignRenderer.hasRendererOfClass`; resets the hull pool caches and adds a transient renderer (`ShuttleTrafficRenderer.java:44`).
- LunaLib's `luna_campaign_renderer` entity renders the plugin in every layer and only advances it while that entity is in the player's location.

`advance -> [location changed] network.rebuild -> fleetScan.scan -> warmStart | [1 day] network.rebuild -> [0.25 s] fleetScan.scan -> divertFromClosed -> spawn -> Shuttle.advance`

## Flow

### Anchors and lanes

`ShuttleNetwork.rebuild` (`:52`) on location change and once per day:

- A market counts if its primary entity is in the location, `isInEconomy()`, not `isHidden()`, `hasSpaceport()` and the primary entity is not `isDiscoverable()` (`:203`). A primary entity tagged `Tags.STATION` is a station anchor, otherwise a planet anchor.
- A planet market's `getConnectedEntities()` tagged `Tags.STATION` in the same location become station anchors with a `SUPPLY` route, `max(1, round((1 + size / 3) * density))` craft (`:80`).
- Each pair of market anchors gets a `MARKET` route unless either is pirate owned or the factions are hostile, `max(1, round((sizeA + sizeB) / 4 * mean density))` craft (`:92`).
- Density (`:215`): Megaport x1.5, Commerce x1.25, stability `clamp(0.5 + stability / 20, 0.5, 1)`, x0.5 while `MemFlags.RECENTLY_RAIDED` or `RECENTLY_BOMBARDED`.
- A disrupted Spaceport or Megaport (`isDisrupted()`) closes the market anchor and its stations (`:209`). Nothing launches from or lands at a closed anchor.
- Anchors and routes not seen in a rebuild are dropped. Routes keep their key `kind|aId|bId`, bend and timer across rebuilds.

### Spawning

- Each route counts down its own timer; interval `(distance / 27 + 6) / max * (0.6..1.4)` s (`ShuttleTrafficRenderer.java:175`), so a lane holds about `max` craft without pulsing.
- A route spawns only while `active < max` and the total in flight is below `setting * capShare` (`SUPPLY` 1.0, `MARKET` 0.9), so supply hops keep flying when lanes fill the cap.
- Direction is random. Hulls: supply hops 80% base pool small craft, else a faction civilian hull; market lanes 60% base pool, else a faction civilian hull (`ShuttleHullPool.rollCargo`, `:61`).
- Warm start: on entering a system every route is filled to `max` at spread progress, trails pre-filled backwards along the path.
- Every 0.25 s, a craft whose destination is no longer usable diverts calmly to the nearest usable non hostile anchor (`ShuttleNetwork.findRefuge`, same faction preferred). Without one it keeps flying; it is dropped if the destination entity is gone.

### Paths

- One cubic Bezier per lane flight. Endpoints are pads on the anchors (`0.6 * radius` deep on planets, `0.15 * radius` on stations), evaluated live, so orbiting anchors carry the lane. Launch and landing angles are toward the other anchor +-40 degrees.
- Handles leave along the outward radial for `min(radius + 80, chord * 0.45)`: the craft climbs out before turning and comes in from above.
- Bend: both control points shift along the chord normal by `bendFrac * chord`, rolled once per route in +-0.125. The normal flips with direction, so the two directions of a lane fly on opposite sides.
- `ShuttleNetwork.getBend` (`:124`) pushes the bend to the far side of any star (corona or event horizon outer edge, else `1.5 * radius`) or planet (`1.3 * radius + 30`) the curve midpoint would cross, clamped to `+-0.6 * chord`.
- Jitter: up to +-10 su lateral offset scaled by `sin(pi * t)`.
- Arc length tables of 16 samples, rebuilt every 1 s; progress is distance based, so fast forward is safe.

### Fleet avoidance

`ShuttleFleetScan.scan` every 0.25 s: fleets that are not `isStationMode()`, slower than 40 su/s, within 3000 su of the player, and either the player fleet or 5 or more ships. `Shuttle.getAvoidanceTarget` (`:272`) pushes the craft along the path normal to clear `fleet radius + 40`, to the side it is already on. The offset eases at 40 su/s and fades out over the first and last 15% of a flight.

### Look

- Hull sprites at 0.10 (fighters) and 0.07 (frigates) of sprite size, below vanilla's campaign 0.15 and 0.11. Speed 30 su/s fighters, 24 su/s frigates, +-15% per craft, easing to 35% over launch and landing.
- Planet launch and landing over `1.2 * radius` (at most 40% of the path): scale to 0.35, alpha to 0, 30% darker, drawn in `TERRAIN_3`. Station docking over 60 su: scale to 0.7, alpha to 0, drawn in `BELOW_STATIONS`. Cruise in `TERRAIN_7`, below fleets.
- Trail: 16 point ring buffer, a point every 8 su, untextured `GL_QUAD_STRIP` 3 su wide, fading to the tail, engine style colour and blend.
- Engine glow at the rear, slight flicker, 1.5x for 2 s around launch and landing. Navigation lights (red port, green starboard, 1 Hz blink, random phase) when `getViewMult()` is below 0.75.
- Traffic fades out between view mult 2 and 3 (max campaign zoom is 3). Off viewport craft (300 su margin) are skipped.

### Docking queue

A station pad is busy while a craft is in its last 100 su and 1 s after. A craft that reaches that point while the pad is busy flies a 360 degree circle of radius 50 tangent to its heading and checks again after each lap; when free it claims the pad and flies a short Bezier in. Each holding craft flies its own circle.

### Hull pools

- Crew rule (`ShuttleHullPool.isCrewed`, `:161`): `getMinCrew() > 0`, no built in `HullMods.AUTOMATED`, no `Tags.AUTOMATED` hull tag; fighter wings tagged `Tags.AUTOMATED_FIGHTER` are skipped.
- Faction pool (`:117`), cached per faction id: hulls of `getKnownFighters()` (wing size clamped 1 to 3) and the frigates of `getKnownShips()` without `STATION` or `MODULE` hints or `Tags.RESTRICTED`. Civilian hints (`CIVILIAN`, `FREIGHTER`, `TANKER`, `LINER`, `TRANSPORT`) mark a hull civilian. Weights: CSV weight, else civilian 3, fighter 2, other 1.
- Engine colours: `getEngineSlots()` on the game's hull spec class and `getStyleId()` on the first slot, by name through `ReflectionUtils.invoke`; a `CUSTOM` or unknown style uses the slot colour. If reflection fails, manufacturer `Low Tech` / `High Tech` / anything else maps to `LOW_TECH` / `HIGH_TECH` / `MIDLINE`, with one warning.
- Unknown or uncrewed CSV hulls are dropped with a warning; a hull without a loadable sprite is skipped.

## Contracts

- Transient only: no entity, listener field, memory key or script lands in the save. Removing IE never breaks a save through this feature.
- Advancing stops while `Global.getSector().isPaused()` (`ShuttleTrafficRenderer.java:66`).
- `ShuttleHullPool` caches are static and reset on every `register()`; faction pools are keyed by faction id and built on first use.
- `getHullSpec` throws on unknown ids, so every CSV and `getKnownShips()` id is checked against `getAllShipHullSpecs()` first.
- The engine style reflection names (`getEngineSlots`, `getStyleId`) are unobfuscated public methods of the game's hull spec and `EngineSlot` classes (knowledge skill `obf/loading/specs/_c147.java`, renamed from `g.java`, and `EngineSlot.java:47`); never use an obfuscated name here.

## Known issues and dormant code

| Item | State |
|---|---|
| In game look | Untested: layer order against planet atmosphere, zoom thresholds, sprite scale, whether LunaLib advances while paused |

## Maintenance

Update this doc when a flight kind, the anchor rules, the hull pool, the CSV columns or the setting changes. Setting mechanics belong in [Settings](SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
