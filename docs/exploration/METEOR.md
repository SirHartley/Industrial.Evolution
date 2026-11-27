# Meteor swarms

Meteor showers in uninhabited systems and rogue asteroids in any system: the rolls, the four swarm spawners, rogue formations, meteor entities and treasure, collisions and NPC dodging, radiation terrain and VFX, warnings, intel and the roider tutorial. Read it before touching `indevo/exploration/meteor/` or the `IndEvo_meteor_*` settings. It does not cover the missiles that shoot meteors; see [Consumables](../items/CONSUMABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

On each economy tick while the player is in a valid system, `MeteorSwarmManager` rolls a shower. A shower is an arc across the system (start and end on the grid boundary, through a point 3000..10000 from the centre); after a 2..5 day delay a spawner script moves meteors along it for a runtime in seconds. Meteors that touch fleets or stations push them, damage ships and break into fragments; station fleets only take the glow and fragments. NPC fleets steer out of a meteor's lane. Treasure meteors drop salvage on impact. The first valid tick also spawns a roider fleet that sells concussive missiles and schedules a tutorial shower.

Type roll weights (`MeteorSwarmManager.java:67-70`): `ASTEROID` 12, `ICEROID` 3, `IRRADIOID` 3, `PLANETOID` 3. The tutorial forces `ASTEROID`.

Rogue asteroids: on a tick with no shower roll, a second roll can send a small formation of rocks across the system on a nearly straight path, or start a drizzle of single small rocks around the player for 5 to 15 days. Inhabited systems are included; there is no warning, intel, sign or sound. Shapes and numbers are under [Rogue asteroids](#rogue-asteroids).

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/meteor/MeteorSwarmManager.java` | Transient `EconomyTickListener`. Shower and rogue rolls, validity checks (`isValidMeteorLoc`, `isValidRogueLoc`), shower parameters and arc, spawner choice, rogue shape and type pick, timeouts, intel. Owns the shared `Random` |
| `spawners/MeteorSwarmSpawnerAPI.java`, `spawners/BaseArcingSwarmSpawner.java` | Spawner contract; base `EveryFrameScript` (runtime, seed, `init()` adds warning path and sign, plays `cr_allied_critical`) |
| `spawners/StandardSwarmSpawner.java` | Asteroids along the arc, normal distribution across width and over time; `TreasuroidEntity` between 20% and 80% of runtime |
| `spawners/IceSwarmSpawner.java` | Ice rocks (half the spawns, double size) and asteroids; `IceTreasureoidEntity` with a frozen wreck |
| `spawners/RadioactiveSwarmSpawner.java` | Adds `RadioactiveTerrain`; asteroids and 30% irradiated rocks; `TreasuradoidEntity` with entourage and loot station, blocks spawns inside its orbit circle |
| `spawners/PlanetoidSwarmSpawner.java` | Spawns everything at once: a temporary planet (vast ruins, very cold, no atmosphere, irradiated if density > 1.5), five dust rings, 0..2 loot stations, an orbiting rock field; planet follows the arc and despawns everything at the end |
| `entities/MeteorEntity.java` | Base meteor plugin: rotation, movement module, missile hit check, collision with stations and fleets near the viewport, NPC fleet dodge, fade at arc end |
| `entities/TreasuroidEntity.java` | Sparkling meteor, spawns a random supply/equipment/weapons cache on impact |
| `entities/IcyRockEntity.java` | Ice rock; radius >= 50 spawns orbiting splinters that fall back to arcing when their focus dies |
| `entities/IceTreasureoidEntity.java` | Ice rock carrying a derelict wreck (hull value > 20000). Recovery is rolled by vanilla `SalvageSpecialAssigner.assignSpecialForBattleWreck` in both modes: a free recovery special (weight 10 times `Misc.getDerelictRecoverableFraction()`) or none (10), which leaves the wreck dialog's story point recovery and its Survival mode block (`SalvageEntity blockRecoveryIfExtendedMode`) |
| `entities/SpicyRockEntity.java` | Irradiated rock: green glow, raises radiation on fleets within `radius * 10` |
| `entities/TreasuradoidEntity.java` | Large irradiated treasure rock (radius 400), entourage ring, `_loot` research or lab station that frees on impact |
| `movement/MeteorMovementModuleAPI.java`, `BaseMeteorMovementModule.java` | Movement contract (`init`, `advance`, `isMovementFinished`, arc, velocity) |
| `movement/ArcingMovementModule.java` | Moves along a `CircularArc` at a fixed speed, reports its arc and angle to the warning renderer |
| `movement/ExternalOrbitMovement.java` | For orbiting meteors: only tracks progress on the arc |
| `movement/ArcOnOrbitLossMovement.java` | Splinters and rogue escort rocks: idle while orbiting, drop the orbit once the focus is gone, then arc on a re-centred arc |
| `movement/EmptyMovement.java` | No movement (impact fragments) |
| `movement/RelativeArcingMovement.java` | Dead: no users |
| `scripts/MovementModuleRunner.java`, `PayloadOnFinishedModuleRunner.java` | Run a movement module on non-meteor entities (entourage focus, planetoid); payload runs a `Script` in the same `advance()` the arc ends, then `isDone()` |
| `scripts/PlanetoidDespawnScript.java` | Planetoid payload: fades the planet, its ring bands and every non fleet entity orbiting it; fleets orbiting it lose the orbit and stay |
| `scripts/MeteorImpact.java`, `IceroidImpact.java` | 0.2 s push on the hit fleet, one ship damaged (weight by hull size, scaled by burn above go-slow and by `ext_environmentalDamageMult` in Survival mode, as vanilla `AsteroidImpact`), sound, glow, asteroid fragments; station fleets get no push and no damage; ice variant spawns ice fragments |
| `scripts/RadiationReductionScript.java` | NPC radiation decay |
| `scripts/TutorialEncounterScript.java` | Spawns "Panicked Roiders" (roider or independent `SCAVENGER_SMALL`), makes them intercept the player |
| `fleet/InterceptPlayerFleetAssignmentAI.java` | Script plus campaign listener: re-issues `INTERCEPT` until the player opens a dialog with the fleet, then returns it to source |
| `rules/IndEvo_RegisterTutorialMeteorIntel.java` | Rule command: reserves the player's location (`reserveForShower`) and adds `MeteorShowerSpawningLocationIntel` (intensity 4, `ASTEROID`, 3 days) |
| `intel/MeteorShowerLocationIntel.java` | `FleetLogIntel`: countdown, size hint, "unusual composition" for non-asteroid types; ends 20 days after the shower arrives |
| `intel/MeteorShowerSpawningLocationIntel.java` | Same, but calls `spawnShower` itself when the countdown ends |
| `terrain/RadioactiveTerrain.java` | `StarCoronaTerrainPlugin` subclass, radius 50000 at system centre; strength per fleet = radiation level in fleet memory; CR loss, peak time and CR decay buffs; removes itself when the warning renderer is gone |
| `renderers/RadiationEffectHandler.java` | Transient Luna renderer: player radiation decay, sound loops on the player fleet, music suppression, vignette, drives `RadiationVFXHandler` |
| `renderers/RadiationVFXHandler.java` | GraphicsLib screen shader (`data/shaders/indevo_baseVertex.shader`, `indevo_Fragment.shader`) by radiation level. Non transient Luna renderer, so it is saved: keep its field names and types |
| `renderers/MeteorSwarmWarningPathRenderer.java` | System script: warning sign entities every 2000 along the arc, red where meteors currently are; takes reports only from rocks on its own arc (same centre); ends after 10 s without reports. Instance kept in location memory |
| `renderers/WarningSignNotificationRenderer.java` | 15 s warning sign and arrow next to the player fleet pointing at the arc |
| `renderers/MeteorDebrisRenderer.java` | Dust clouds at impact; transient Luna renderer, expires once all particles fade |
| `renderers/RogueRockDustRenderer.java` | Dust halo, chips and glints around rogue rocks in the current location; transient Luna renderer, one per session ([Look](#rogue-asteroids)) |
| `renderers/RadiationTrailRenderer.java` | Dead: only user commented out (`SpicyRockEntity.java:45`) |
| `helper/MeteorFactory.java` | Size -> custom entity id lookup per type |
| `rogue/RogueFormation.java` | Enum of the seven formation shapes with their roll weight; `generate(Random)` gives each rock an offset along (`x`, lead at the front) and across (`y`) the path and a size; Escort rocks orbit the big rock. Ports of the approved mockup generators (project file `ie/ROGUE_FORMATIONS_REFERENCE.js`), kept as drawn: outlines filled at random, no even spacing |
| `rogue/RogueAsteroidSpawner.java` | Static. Aim point, path arc, start distance, rock types, treasure; places one formation at once |
| `rogue/RogueDrizzleSpawner.java` | Saved location script: a drizzle's lanes, end time and rocks; keeps a rock density around the viewport while the player is in the system |
| `indevo/console/SpawnRogue.java` | Console command `indevo_spawnRogue` ([Console](../core/CONSOLE.md)) |
| `indevo/tools/RogueAsteroidChecks.java` | Headless checks, never shipped ([Checks](#checks)) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Settings | `IndEvo_meteor_enabled` (true), `_baseChancePerEconomyTick` (0.02), `_immediateMode` (false), `_minIntensity` (1), `_maxIntensity` (3), `_baseShowerWidth` (5000), `_intensityWidthModifier` (1000), `_maxDensity` (2.5), `_maxRuntimeMult` (2.5), `_baseRuntime` (50 s), `_locationTimeoutAfterSpawnDays` (186), `_generalTimeoutAfterSpawnDays` (31), `_gridLimit` (26000), `_maxDistanceFromSun` (10000), `_minDistanceFromSun` (3000), `_maxAngle` (80), `_minAngle` (50), `_rogueChancePerEconomyTick` (0.1, 0 to 0.3, 0 turns rogues off) | `data/config/LunaSettings.csv:8`, `:41-57` (Fog & Meteors tab), constants in `indevo/config/Setting.java:25-42`, read at use time in `MeteorSwarmManager.java` and `RogueAsteroidSpawner.java` (`_gridLimit`) |
| Custom entities | `IndEvo_meteor_1..4` (`MeteorEntity`), `IndEvo_icy_rock_1..6` (`IcyRockEntity`), `IndEvo_spicy_rock_1..4` (`SpicyRockEntity`), `IndEvo_spicy_rock_5` (`TreasuradoidEntity`), `IndEvo_meteor_treasure_1` (`TreasuroidEntity`), `IndEvo_meteor_treasure_2` (`IceTreasureoidEntity`), `IndEvo_warning_sign`, `IndEvo_token` | `data/config/custom_entities.json:2-288`, `:382` |
| Terrain | `IndEvo_radioactive_field` -> `RadioactiveTerrain` | `data/campaign/terrain.json:6` |
| Memory key | `$IndEvo_MeteorTimeout` (global and location, expiring) | `MeteorSwarmManager.java:48`, set in `reserveForShower` `:258-259` |
| Memory key | `$IndEvo_MeteorShowerPending` (location, expiring: days until the shower starts plus 1) | `MeteorSwarmManager.java:50`, set in `reserveForShower` `:262` |
| Memory key | `$IndEvo_RogueMeteorTimeout` (location, expiring, 30 days) | `MeteorSwarmManager.java:49`, set `:166`, `:172` |
| Memory key | `$IndEvo_MeteorTutorialComplete` (global) | `MeteorSwarmManager.java:46` |
| Memory key | `$IndEvo_MeteorTutorialFleet` (global, the tutorial fleet) | `MeteorSwarmManager.java:51`, set `TutorialEncounterScript.java:81` |
| Persistent data | `$IndEvo_MeteorRandom` (shared `Random`) | `MeteorSwarmManager.java:47`, `:277` |
| Console | `indevo_spawnRogue <shape> [asteroid\|ice\|irradiated] [treasure]` | `data/console/commands.csv` |
| Memory key | `$IndEvo_RadEffectLevel` (per fleet, 0..1) | `RadiationEffectHandler.java:29` |
| Memory key | `$IndEvo_warningRenderer` (location, the path renderer instance) | `MeteorSwarmWarningPathRenderer.java:23` |
| Memory key | `$IndEvo_roider_tutorial`, `$IndEvo_supplyTradeAmt` (tutorial fleet) | `TutorialEncounterScript.java:79-80` |
| Tag | `IndEvo_MeteorSwarm` (`MeteorSwarmManager.METEOR_TAG`) on every meteor, added in `MeteorEntity.init(SectorEntityToken, Object)`; read by Catch.Release, by shuttle traffic ([SHUTTLE_TRAFFIC](../core/SHUTTLE_TRAFFIC.md#anchors-and-routes)) and by fog, where rocks push the fog ([FOG](FOG.md)) | `MeteorEntity`, `MeteorSwarmManager` |
| Tags | `indevo_planetoid`, `IndEvo_IceRockNoSplinters`, `IndEvo_DrizzleRock` (`RogueDrizzleSpawner.TAG_ROCK`, read by shuttle traffic), `IndEvo_RogueRock` (`RogueAsteroidSpawner.TAG_ROGUE_ROCK`, every formation and drizzle rock and the splinters of a tagged ice rock, read by `RogueRockDustRenderer`), `IndEvo_IceEntourage_<id>`, `IndEvo_TreasureoidEntrouage_<id>`; loot station id `<entity id>_loot` | spawners and entities |
| Rules | `IndEvo_roiderTutorial_*` (offer 5 `IndEvo_consumable_missile_concussive` for 10% of supplies, max 100) | `data/campaign/rules.csv:6-62` |
| Rule command package | `indevo.exploration.meteor.rules` | `data/config/settings.json:45` |
| Sounds | `IndEvo_radiation0..9` | `data/config/sounds.json:18` |
| Sprite | `IndEvo/meteor_storm` (intel icon) | `data/config/settings.json:168` |

## Registration and lifecycle

- `ModPlugin.onGameLoad() -> setListenersIfNeeded() -> MeteorSwarmManager.register()` (`indevo/utils/ModPlugin.java:490`) adds a transient listener each load (`MeteorSwarmManager.java:81`).
- `setListenersIfNeeded() -> RogueRockDustRenderer.register()` (`ModPlugin.java:491`): transient Luna renderer, once per session (`hasRendererOfClass`).
- Spawners, warning path renderer and delayed spawn are location scripts; meteors are custom entities with saved plugins.
- `RadiationEffectHandler.get()` adds itself as a transient Luna renderer on first use per session (`:36-45`).

`reportEconomyTick -> isValidMeteorLoc -> roll -> spawnShower -> DelayedActionScript(daysLeft) -> <Type>SwarmSpawner.advance -> init() (warning path, sign, terrain) -> advanceSpawner -> MeteorFactory / Treasure*.spawn -> MeteorEntity.advance -> setCollidingAndFade -> MeteorImpact + MeteorDebrisRenderer`

`reportEconomyTick (no shower rolled) -> isValidRogueLoc -> roll -> spawnRogue -> RogueAsteroidSpawner.spawn -> MeteorFactory / Treasure*.spawn (all rocks at once) -> MeteorEntity.advance`, or `spawnRogue -> isDrizzleRoll -> RogueDrizzleSpawner.start (location script) -> advance -> MeteorFactory.spawn`

## Flow

### Roll and parameters

- `reportEconomyTick` (`MeteorSwarmManager.java:90`): skip if `IndEvo_meteor_enabled` false. The first tick in a valid location adds `TutorialEncounterScript`, sets `$IndEvo_MeteorTutorialComplete` and rolls nothing else. While the tutorial is open (`isTutorialOpen`, `:118`) no tick rolls anything. Otherwise a shower rolls in a valid location, chance per tick `IndEvo_meteor_baseChancePerEconomyTick` (0.1 in dev mode).
- `isValidMeteorLoc` (`:125`): no pulsar planet, no global or location timeout, not hyperspace, no markets, not `SYSTEM_CUT_OFF_FROM_HYPER`, `THEME_HIDDEN`, `THEME_SPECIAL`, `SYSTEM_ABYSSAL`.
- `spawnShower` (`:199`): `width = baseWidth + modifier * intensity`, `lootAmt = max(1, round(intensity * treasureModifier))`, `density = min(maxDensity, intensity)`, `runtime = baseRuntime * min(maxRuntimeMult, intensity)` in seconds. Arc centre at random distance and angle; start and end at `angleToCenter +- (50..80)` intersected with the `gridLimit` square (`intersectWithGridBoundary`), circle through the three points (`TrigHelper.findThreePointCircle`). Delay `max(2, nextInt(6))` days, 0 in immediate mode.
- `reserveForShower(loc, daysUntilStart)` (`:255`): sets both `$IndEvo_MeteorTimeout` keys and `$IndEvo_MeteorShowerPending` on the location for `daysUntilStart + 1` days, never shortening a longer one. Called by `spawnShower` with its delay and by the tutorial on accept.

### Collision

`MeteorEntity.advance` (`:94`): only when within 1000 of the viewport; targets are stations and fleets minus salvageable entities, built once per frame and location and shared by all meteors (`getCollisionRelevantEntities`, `CollisionTargets`), since a tag lookup walks every entity in the location; hit when centre distance < meteor radius. A meteor hit by a consumable missile (`MissileMemFlags.MEM_CAUGHT_BY_MISSILE` in its memory) collides with a token at the missile location, so treasure drops without hitting a fleet (`:100-110`).

- Station fleets (`isStationMode`): the hit breaks the meteor with sound, glow and fragments, no damage and no push (`MeteorImpact.java:35`, `:67`, `:192`). The push would drop the station's orbit, and only market stations get it back (vanilla `OrbitalStation`).
- Dodge (`warnFleets`, `:149`): in the same near viewport block, every 0.2 s per meteor (`dodgeTimer`), for each fleet target that is not the player, not in station mode, not on an orbit and has a `ModularFleetAIAPI`: `getClosestApproach` (`:164`) takes the point on the meteor's straight course closest to the fleet. If the meteor gets there within 4 s and the point is closer than meteor radius + fleet radius + 150, the fleet gets `getNavModule().avoidLocation(loc, point, range, range + 1, 0.03 days)`. The avoid vector points across the lane; a band of 1 or less gives it weight 2 against the fleet's own heading (starsector-knowledge `obf/campaign/ai/NavigationModule.java:74-87`). `avoidLocation` adds a new entry per call, hence the interval; each entry lasts 0.3 s. Fleets on an orbit do not move by their nav module and still get hit.

### Radiation

`SpicyRockEntity` raises `$IndEvo_RadEffectLevel` by 0.01/s per rock on nearby fleets (`RADIATION_INCREASE_PER_SEC` 0.1 times `ACTIVITY_INCREASE_PER_SECOND` 0.1). Player decay in `RadiationEffectHandler.advance` (0.1/s after 1 s without exposure). NPC decay in one `RadiationReductionScript` per fleet (0.1/s with no delay, so an NPC fleet near fewer than ten rocks stays at 0). `RadioactiveTerrain.applyEffect` turns the level into CR loss and peak performance and CR decay buffs, respecting `CORONA_EFFECT_MULT` and `PROTECTS_FROM_CORONA_IN_BATTLE`.

### Tutorial

`TutorialEncounterScript` -> roiders intercept -> `rules IndEvo_roiderTutorial_acceptOffer` -> `IndEvo_RegisterTutorialMeteorIntel` -> `MeteorShowerSpawningLocationIntel` counts down 3 days -> `spawnShower(loc, 4, ASTEROID, false)`, which adds its own 2..5 day delay unless immediate mode.

The tutorial shower skips `isValidMeteorLoc`, so the tutorial holds every other roll back:

- Open (`MeteorSwarmManager.isTutorialOpen`): `TutorialEncounterScript` is still a sector script (it waits for a valid location), or the fleet in `$IndEvo_MeteorTutorialFleet` is alive without `$IndEvo_roider_tutorial_complete` (set by the accept and the final reject rule). No shower and no rogue rolls anywhere while it is open. A reject or a lost fleet ends it without a shower.
- Accept: `IndEvo_RegisterTutorialMeteorIntel` calls `reserveForShower(loc, 3)` on the player's location, so the timeouts and the pending key hold until `spawnShower` renews them.

### Rogue asteroids

- Roll (`MeteorSwarmManager.java:113`): only when no shower was rolled that tick and the tutorial is not open, chance `IndEvo_meteor_rogueChancePerEconomyTick`, also gated by `IndEvo_meteor_enabled`. At 0.1 that is one rogue per about 30 days spent in valid systems, about one aimed at the player per 100 days; the 30 day location timeout then holds the same system back.
- Location (`isValidRogueLoc`, `:136`): a star system, no pulsar, not hyperspace, `SYSTEM_CUT_OFF_FROM_HYPER`, `THEME_HIDDEN`, `THEME_SPECIAL` or `SYSTEM_ABYSSAL` (shared with showers in `isSuitableForRocks`), no `$IndEvo_warningRenderer` and no `$IndEvo_MeteorShowerPending` (no rogue during a shower or its warning days) and no `$IndEvo_RogueMeteorTimeout`. Markets and the shower timeouts do not matter. A rogue sets only its own 30 day location timeout, no shower timeout.
- Pick: a drizzle (`RogueDrizzleSpawner.WEIGHT` 10, `isDrizzleRoll`, `MeteorSwarmManager.java:176`) or a shape by `RogueFormation.weight` (Scatter 20, Cluster 20, Teardrop 20, Wall 10, Chevron 10, Pair 10, Escort 5); type `ASTEROID`, `ICEROID` or `IRRADIOID` by the shower weights (`pickRogueType`). `PLANETOID` is a shower, not a rock type. A drizzle sets the rogue timeout to its duration plus 30 days (`:166`).
- Aim (`RogueAsteroidSpawner.spawn`, `:66`): 30% aim at the player: a point 0 to 8000 su ahead on the course (`getHeadingTarget`: the current course target if it is in this system, else `getMoveDestination()`, which is the fleet itself when unset), never past that target, plus up to 1500 su spread (`getCourseAimPoint`). Otherwise a random point within 20000 su of the centre. The aim point is clamped to 500 su inside the `IndEvo_meteor_gridLimit` square.
- Path (`createPath`, `:215`): a circle of radius 150000 to 400000 su through the aim point with a random heading as its tangent there, cut where it leaves the grid square. The arc is nearly straight and all arc code works on it: movement, splinters, removal at the arc end.
- Start (`findStartDistance`, `:281`): aimed rogues start `speed x (fleet distance to the aim / fleet speed)` back from the aim point (0 for a fleet slower than 10 su/s), others at the path start on the system edge. The start then moves back until the whole formation is at least 2000 su outside the viewport, at most to the path start; if the path start is in view too, it moves ahead past the view instead; if nothing is out of view, nothing spawns.
- Formation (`getPlacement`, `:301`): the across offset becomes the arc radius (`getModifiedRadiusArc`, same centre), the along offset the start angle. One speed per formation, 150 to 250 su/s (burn 7.5 to 12.5 at 20 su/s per burn level), scaled by each rock's arc radius, so every rock has the same angular speed and the shape holds. Escort rocks orbit the big rock (`ArcOnOrbitLossMovement`) and fly on along the path once it breaks.
- Rock types (`assignKinds`, `:165`): as in the showers: an ice rogue makes half its non orbiting rocks ice rocks at double size (at most 200), an irradiated rogue makes 30% of its rocks irradiated, the rest are asteroids. Ice rocks below size 100 get `IndEvo_IceRockNoSplinters`; larger ones keep their splinters. Irradiated rogues add no `RadioactiveTerrain`: glow, radiation sound and screen effect, no CR loss. Sizes below 20 from the generators are raised to 20 (`MeteorEntity.MIN_SIZE`).
- Treasure: 20% per formation, on the solo rock (Escort) or the first rock that does not orbit: `TreasuroidEntity` for asteroid and irradiated rogues, `IceTreasureoidEntity` for ice. `TreasuradoidEntity` stays with the showers.
- Drizzle (`RogueDrizzleSpawner`): 5 to 15 days, no terrain or tooltip, never treasure. Rocks of size 20 to 48 (below the ice splinter size), speed 120 to 300 su/s each (burn 6 to 15), one type for the drizzle with the same mix as a formation (half ice, or 30% irradiated). Lanes are concentric arcs around a far centre (150000 to 400000 su out); two or three lane sets with centres rotated up to 8 degrees apart give the spread (`createLaneCentres`, `:175`). Every 0.25 s while the player is in the system (`advance`, `:90`): rocks that broke, faded or are more than 1000 su outside the margin go (`removeFarRocks`); the target is 1 rock per 500000 su2 of the view plus a 600 su margin, at most 80 (`getTargetCount`, `:190`, about 34 at 1920 x 1080 and zoom 2); new rocks spawn in the margin strips on lanes that run into the view (`pickSpawnPoint`, `:195`), at up to twice the rate at which rocks cross the view, so the view fills over a few seconds instead of in one wave. Cost depends on the view, not the system size. With the player elsewhere it removes its rocks and waits (`:100`); it ends by its timestamp, or as soon as a shower starts in the system (`$IndEvo_warningRenderer`, `:93-94`), and lets the last rocks fly out to their arc end.
- No warning: no intel, warning path, sign, sound or delay. Rocks show on the system map like shower rocks (`showIconOnMap`). Hits, station fleets and the dodge work as for shower rocks; in colonies station fleets break rocks without damage. Formation rocks close shuttle pads they pass like shower rocks; drizzle rocks carry `IndEvo_DrizzleRock` and never do, since a drizzle would keep every pad in view closed for its whole run ([Shuttle traffic](../core/SHUTTLE_TRAFFIC.md)).
- Look (`RogueRockDustRenderer`, registered in `ModPlugin.setListenersIfNeeded`): rogue rocks get a faint dust halo and small chips that travel with the rock; nothing trails behind it. Shower rocks get none. Per rock, seeded by its id: 3 dust puffs (`misc/nebula_particles`, normal blend, alpha 0.17 with a slow pulse) 0.45 to 1.15 x size from the centre, each `size x 1.6..2.5 + 22` su wide, slowly orbiting and turning; `3 + size / 30` chips (3 at size 20, 9 at size 200), 3 to 8 su four corner polygons drawn in GL at 0.9 alpha, orbiting at 0.75 to 1.9 x size + 8 su with a small wobble. Ice rocks add 1.2 and treasure rocks 4 glints per second (`campaignEntities/fusion_lamp_glow`, additive, 0.45 to 1.1 s, drifting at most 14 su/s from their spot on the rock). Colours per kind (asteroid, ice, irradiated, treasure) in `RogueRockDustRenderer.Kind`. Dust and chips draw in `TERRAIN_4` below the rocks (`ASTEROIDS`), glints in `TERRAIN_5` above. Alpha follows the rock's sensor faders like the engine's sprite draw (obf `CustomCampaignEntity.java:351-353`), so the look fades with a breaking rock. Nothing is saved: every 0.5 s and on a location change the renderer scans the current location for `IndEvo_RogueRock` (no callback exists for an entity entering a location, and rocks of a loaded save need a look too). Values are the mockup's defaults (project file `ie/ROGUE_ASTEROID_DUST_MOCKUP.html`, look C, faint dust, some debris).

### Checks

After the compile gate (`java jars/src/indevo/tools/ReleaseBuild.java compile`), from the repository root:

```sh
java -cp "out/release-build-<n>/classes:lib/*" indevo.tools.RogueAsteroidChecks
```

Use `;` in place of `:` on Windows. It prints `Rogue asteroids: N checks passed` or every failure and exits 1. It refuses to run inside the game. It covers the shape and drizzle weights, rock counts, bounds and sizes per shape over 2000 seeds, lead ordering of Teardrop and Chevron, the Escort orbits, rock types and ice sizes, the path (through the aim point, heading, start and end on the grid edge, nearly straight), the placement of every rock against its offsets, the start search, the course aim point, the closest approach maths of the dodge, and the drizzle rock count, lane sets, spawn points and type mix. It does not spawn entities or run anything in game.

## Contracts

- Settings are read at use time: the enable toggle and chance on each economy tick, the shape values when a shower is rolled, the timeouts when it spawns. A change applies to the next tick or shower; a shower already rolled keeps its shape. Setting mechanics: [Settings](../core/SETTINGS.md).
- One swarm per location: the warning path renderer is a single instance in location memory, the last one wins (`MeteorSwarmWarningPathRenderer.java:16-18`). Every shower goes through `reserveForShower`, and a shower that skips `isValidMeteorLoc` (the tutorial) must hold the other rolls back itself until it has reserved its location. `RadioactiveTerrain` lives only while that key exists, and relies on being spawned after it (`RadioactiveTerrain.java:90`).
- Movement modules report `(arc, angle)` through `MeteorSwarmWarningPathRenderer.reportAngle`; the renderer drops reports whose arc centre is more than 1 su from its own (`isOwnArc`, `:61`). Every rock of a swarm, its splinters and its entourage use arcs from `getModifiedRadiusArc` of the swarm arc, which keep the centre. A rock on any other arc (dispersed treasure entourage, any rock of another path) never keeps a swarm's path, signs or terrain alive.
- `IntelManager.advance` does not advance intel (starsector-knowledge `obf/campaign/comms/v2/IntelManager.java:44`), so `MeteorShowerLocationIntel` adds itself as a sector script (`:31`).
- `BaseCampaignEntity.runScripts` drops a script as soon as `isDone()` is true (starsector-knowledge `obf/campaign/BaseCampaignEntity.java:636-647`), so `PayloadOnFinishedModuleRunner` runs its payload in the advance that ends the movement and reports done from its own flag (`PayloadOnFinishedModuleRunner.java:20-37`). Payloads are named `Script` classes: lambdas come back from the save as null. A null payload at the arc end throws `IllegalStateException`, since only an IE bug can produce one.
- Planetoid swarm planets carry `PlanetoidSwarmSpawner.PLANETOID_TAG` (`indevo_planetoid`); the planetoid dialog rules key on it (`rules.csv` `IndEvo_isPlanetoid*`).
- Spawners use a seeded `Random` per shower; the manager uses one saved `Random` in persistent data, which rogue rolls and `RogueAsteroidSpawner` share.
- Rogue paths have arc radii of 150000 to 400000 su. `Circle.getAngleForPoint` uses strict `atan2` (`Misc.getAngleInDegreesStrict`): the game's fast angle lookup is off by up to about 0.06 degrees, a few hundred su on such an arc. Rogue rocks have arc centres far from any shower's, so they never report to a shower's warning path.
- Save: rogue rocks are ordinary meteor entities that save their movement module. `RogueFormation` and `RogueAsteroidSpawner` hold no saved state. `RogueDrizzleSpawner` is a new saved location script (lanes, end time, its rock list). Nothing saved before this changed shape apart from the field below. `MeteorEntity` gained the float `dodgeTimer`, which loads as 0 from older saves.
- Java 17 features (switch expressions, `instanceof` patterns) are used; the project language level is `JDK_17` (`.idea/misc.xml:6`).
- Other mods break a meteor by setting `MissileMemFlags.MEM_CAUGHT_BY_MISSILE` (`$missile_bonked`, a `Vector2f` hit point) in its memory. The next `MeteorEntity.advance` collides it with a token at that point, as for a missile: fragments, impact sound, treasure drop, no fleet damage. They find meteors by `METEOR_TAG`. Catch.Release's harpoon uses both as strings, so keep the key, the value type and the tag value. See [Compatibility](../core/COMPATIBILITY.md).
- Couplings: `MissileMemFlags` ([Consumables](../items/CONSUMABLES.md)); `CircularArc`, `TrigHelper`, `MiscIE`, `NebulaParticle` ([Utils](../core/UTILS.md)); `Ids.LAB_ENTITY`, `Ids.ARSENAL_ENTITY`, `Ids.ABANDONED_PETSHOP_ENTITY` loot stations ([Stations](STATIONS.md)); vanilla `AbyssalRogueStellarObjectEPEC.PLANETOID_TYPES` for planetoid types.
- `MeteorImpact` on the player fleet unlocks an achievement ([ACHIEVEMENTS](../core/ACHIEVEMENTS.md)).

## Known issues and dormant code

| Item | State |
|---|---|
| `RelativeArcingMovement`, `RadiationTrailRenderer` | Dead code |
| Rogue asteroids | Untested in game: all of it, including the drizzle, the dodge and the station fleet rule. Every NPC fleet in view dodges drizzle rocks too, which may make traffic in a drizzle look busy. Rocks of a rogue keep flying when the player leaves the system; off screen they move in the 60 frame steps of a far location |
| Rogue generator sizes | Teardrop tails and Chevron arms go down to size 16 in the approved generators; the spawner raises them to 20. `angle` in `build()` of the mockup reference is the travel direction on the mockup stage, not part of the shape; the game uses the path heading |
| Tutorial rules text | Promises a "core-fragment" swarm (`rules.csv:23`, `:37`) but the tutorial forces `ASTEROID` |
| Changelog_todo | "bump default meteor storm min treasure to 2" (`Changelog_todo:5`), "Meteor shower bar event" (`Changelog_todo:53`) |

## Maintenance

Update this doc when a swarm type, a setting, a meteor entity id, the collision rule or the tutorial changes. Missile behaviour belongs in [Consumables](../items/CONSUMABLES.md), setting mechanics in [Settings](../core/SETTINGS.md), arc and trig helpers in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
