# Meteor swarms

Meteor showers in uninhabited systems: the roll, the four swarm spawners, meteor entities and treasure, collisions, radiation terrain and VFX, warnings, intel and the roider tutorial. Read it before touching `indevo/exploration/meteor/` or the `IndEvo_meteor_*` settings. It does not cover the missiles that shoot meteors; see [Consumables](../items/CONSUMABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

On each economy tick while the player is in a valid system, `MeteorSwarmManager` rolls a shower. A shower is an arc across the system (start and end on the grid boundary, through a point 3000..10000 from the centre); after a 2..5 day delay a spawner script moves meteors along it for a runtime in seconds. Meteors that touch fleets or stations push them, damage ships and break into fragments. Treasure meteors drop salvage on impact. The first valid tick also spawns a roider fleet that sells concussive missiles and schedules a tutorial shower.

Type roll weights (`MeteorSwarmManager.java:75-78`): `ASTEROID` 12, `ICEROID` 3, `IRRADIOID` 3, `PLANETOID` 3. The tutorial forces `ASTEROID`.

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/meteor/MeteorSwarmManager.java` | Transient `EconomyTickListener`. Roll, validity check, shower parameters and arc, spawner choice, timeouts, intel. Owns the settings fields and the shared `Random` |
| `spawners/MeteorSwarmSpawnerAPI.java`, `spawners/BaseArcingSwarmSpawner.java` | Spawner contract; base `EveryFrameScript` (runtime, seed, `init()` adds warning path and sign, plays `cr_allied_critical`) |
| `spawners/StandardSwarmSpawner.java` | Asteroids along the arc, normal distribution across width and over time; `TreasuroidEntity` between 20% and 80% of runtime |
| `spawners/IceSwarmSpawner.java` | Ice rocks (half the spawns, double size) and asteroids; `IceTreasureoidEntity` with a frozen wreck |
| `spawners/RadioactiveSwarmSpawner.java` | Adds `RadioactiveTerrain`; asteroids and 30% irradiated rocks; `TreasuradoidEntity` with entourage and loot station, blocks spawns inside its orbit circle |
| `spawners/PlanetoidSwarmSpawner.java` | Spawns everything at once: a temporary planet (vast ruins, very cold, no atmosphere, irradiated if density > 1.5), five dust rings, 0..2 loot stations, an orbiting rock field; planet follows the arc and despawns everything at the end |
| `entities/MeteorEntity.java` | Base meteor plugin: rotation, movement module, missile hit check, collision with stations and fleets near the viewport, fade at arc end |
| `entities/TreasuroidEntity.java` | Sparkling meteor, spawns a random supply/equipment/weapons cache on impact |
| `entities/IcyRockEntity.java` | Ice rock; radius >= 50 spawns orbiting splinters that fall back to arcing when their focus dies |
| `entities/IceTreasureoidEntity.java` | Ice rock carrying a derelict wreck (hull value > 20000, recoverable without story point) |
| `entities/SpicyRockEntity.java` | Irradiated rock: green glow, raises radiation on fleets within `radius * 10` |
| `entities/TreasuradoidEntity.java` | Large irradiated treasure rock (radius 400), entourage ring, `_loot` research or lab station that frees on impact |
| `movement/MeteorMovementModuleAPI.java`, `BaseMeteorMovementModule.java` | Movement contract (`init`, `advance`, `isMovementFinished`, arc, velocity) |
| `movement/ArcingMovementModule.java` | Moves along a `CircularArc` at a fixed speed, reports angle to the warning renderer |
| `movement/ExternalOrbitMovement.java` | For orbiting meteors: only tracks progress on the arc |
| `movement/ArcOnOrbitLossMovement.java` | Splinters: idle while orbiting, then arcs on a re-centred arc |
| `movement/EmptyMovement.java` | No movement (impact fragments) |
| `movement/RelativeArcingMovement.java` | Dead: no users |
| `scripts/MovementModuleRunner.java`, `PayloadOnFinishedModuleRunner.java` | Run a movement module on non-meteor entities (entourage focus, planetoid); payload runs a `Script` in the same `advance()` the arc ends, then `isDone()` |
| `scripts/PlanetoidDespawnScript.java` | Planetoid payload: fades the planet, its ring bands and everything orbiting it. `despawnStrandedPlanetoids()` runs on every load |
| `scripts/DespawningMovementModuleRunner.java` | Unused by new code; kept because 4.1.b saves hold it on planetoids, which `despawnStrandedPlanetoids()` converts on load |
| `scripts/MeteorImpact.java`, `IceroidImpact.java` | 0.2 s push on the hit entity, one ship damaged (weight by hull size, scaled by burn above go-slow), sound, glow, asteroid fragments; ice variant spawns ice fragments |
| `scripts/RadiationReductionScript.java` | NPC radiation decay |
| `scripts/TutorialEncounterScript.java` | Spawns "Panicked Roiders" (roider or independent `SCAVENGER_SMALL`), makes them intercept the player |
| `fleet/InterceptPlayerFleetAssignmentAI.java` | Script plus campaign listener: re-issues `INTERCEPT` until the player opens a dialog with the fleet, then returns it to source |
| `rules/IndEvo_RegisterTutorialMeteorIntel.java` | Rule command: adds `MeteorShowerSpawningLocationIntel` (intensity 4, `ASTEROID`, 3 days) |
| `intel/MeteorShowerLocationIntel.java` | `FleetLogIntel`: countdown, size hint, "unusual composition" for non-asteroid types; ends 20 days after the shower arrives |
| `intel/MeteorShowerSpawningLocationIntel.java` | Same, but calls `spawnShower` itself when the countdown ends |
| `terrain/RadioactiveTerrain.java` | `StarCoronaTerrainPlugin` subclass, radius 50000 at system centre; strength per fleet = radiation level in fleet memory; CR loss, peak time and CR decay buffs; removes itself when the warning renderer is gone |
| `renderers/RadiationEffectHandler.java` | Transient Luna renderer: player radiation decay, sound loops on the player fleet, music suppression, vignette, drives `RadiationVFXHandler` |
| `renderers/RadiationVFXHandler.java` | GraphicsLib screen shader (`data/shaders/indevo_baseVertex.shader`, `indevo_Fragment.shader`) by radiation level. Non transient Luna renderer, so it is saved: keep its field names and types |
| `renderers/MeteorSwarmWarningPathRenderer.java` | System script: warning sign entities every 2000 along the arc, red where meteors currently are; ends after 10 s without reports. Instance kept in location memory |
| `renderers/WarningSignNotificationRenderer.java` | 15 s warning sign and arrow next to the player fleet pointing at the arc |
| `renderers/MeteorDebrisRenderer.java` | Dust clouds at impact; transient Luna renderer, expires once all particles fade |
| `renderers/RadiationTrailRenderer.java` | Dead: only user commented out (`SpicyRockEntity.java:45`) |
| `helper/MeteorFactory.java` | Size -> custom entity id lookup per type |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Settings | `IndEvo_meteor_enabled` (true), `_baseChancePerEconomyTick` (0.02), `_immediateMode` (false), `_minIntensity` (1), `_maxIntensity` (3), `_baseShowerWidth` (5000), `_intensityWidthModifier` (1000), `_maxDensity` (2.5), `_maxRuntimeMult` (2.5), `_baseRuntime` (50 s), `_locationTimeoutAfterSpawnDays` (186), `_generalTimeoutAfterSpawnDays` (31), `_gridLimit` (26000), `_maxDistanceFromSun` (10000), `_minDistanceFromSun` (3000), `_maxAngle` (80), `_minAngle` (50) | `data/config/LunaSettings.csv:21-37`, ids in `indevo/utils/helper/Settings.java:129-145`, read in `MeteorSwarmManager.java:44-61`, `:192` |
| Custom entities | `IndEvo_meteor_1..4` (`MeteorEntity`), `IndEvo_icy_rock_1..6` (`IcyRockEntity`), `IndEvo_spicy_rock_1..4` (`SpicyRockEntity`), `IndEvo_spicy_rock_5` (`TreasuradoidEntity`), `IndEvo_meteor_treasure_1` (`TreasuroidEntity`), `IndEvo_meteor_treasure_2` (`IceTreasureoidEntity`), `IndEvo_warning_sign`, `IndEvo_token` | `data/config/custom_entities.json:30-316`, `:410` |
| Terrain | `IndEvo_radioactive_field` -> `RadioactiveTerrain` | `data/campaign/terrain.json:6` |
| Memory key | `$IndEvo_MeteorTimeout` (global and location, expiring) | `MeteorSwarmManager.java:41`, set `:195-196` |
| Memory key | `$IndEvo_MeteorTutorialComplete` (global) | `MeteorSwarmManager.java:39` |
| Persistent data | `$IndEvo_MeteorRandom` (shared `Random`) | `MeteorSwarmManager.java:40`, `:206` |
| Memory key | `$IndEvo_RadEffectLevel` (per fleet, 0..1) | `RadiationEffectHandler.java:29` |
| Memory key | `$IndEvo_warningRenderer` (location, the path renderer instance) | `MeteorSwarmWarningPathRenderer.java:23` |
| Memory key | `$IndEvo_roider_tutorial`, `$IndEvo_supplyTradeAmt` (tutorial fleet) | `TutorialEncounterScript.java:79-80` |
| Tags | `indevo_planetoid`, `IndEvo_IceRockNoSplinters`, `IndEvo_IceEntourage_<id>`, `IndEvo_TreasureoidEntrouage_<id>`; loot station id `<entity id>_loot` | spawners and entities |
| Rules | `IndEvo_roiderTutorial_*` (offer 5 `IndEvo_consumable_missile_concussive` for 10% of supplies, max 100) | `data/campaign/rules.csv:6-62` |
| Rule command package | `indevo.exploration.meteor.rules` | `data/config/settings.json:251` |
| Sounds | `IndEvo_radiation0..9` | `data/config/sounds.json:18` |
| Sprite | `IndEvo/meteor_storm` (intel icon) | `data/config/settings.json:369` |

## Registration and lifecycle

- `ModPlugin.onGameLoad() -> setListenersIfNeeded() -> MeteorSwarmManager.register()` (`indevo/utils/ModPlugin.java:490`) adds a transient listener each load (`MeteorSwarmManager.java:90`).
- Spawners, warning path renderer and delayed spawn are location scripts; meteors are custom entities with saved plugins.
- `ModPlugin.onGameLoad() -> PlanetoidDespawnScript.despawnStrandedPlanetoids()` (`indevo/utils/ModPlugin.java:156`): fades rings and stations whose planetoid is gone, despawns planetoids without a runner, swaps a 4.1.b `DespawningMovementModuleRunner` for a `PayloadOnFinishedModuleRunner` and restores a null payload.
- `RadiationEffectHandler.get()` adds itself as a transient Luna renderer on first use per session (`:36-45`).

`reportEconomyTick -> isValidMeteorLoc -> roll -> spawnShower -> DelayedActionScript(daysLeft) -> <Type>SwarmSpawner.advance -> init() (warning path, sign, terrain) -> advanceSpawner -> MeteorFactory / Treasure*.spawn -> MeteorEntity.advance -> setCollidingAndFade -> MeteorImpact + MeteorDebrisRenderer`

## Flow

### Roll and parameters

- `reportEconomyTick` (`MeteorSwarmManager.java:98`): skip if `IndEvo_meteor_enabled` false or location invalid. Chance per tick `IndEvo_meteor_baseChancePerEconomyTick` (0.1 in dev mode). First valid tick adds `TutorialEncounterScript` and sets `$IndEvo_MeteorTutorialComplete`.
- `isValidMeteorLoc` (`:118`): no pulsar planet, no global or location timeout, not hyperspace, no markets, not `SYSTEM_CUT_OFF_FROM_HYPER`, `THEME_HIDDEN`, `THEME_SPECIAL`, `SYSTEM_ABYSSAL`.
- `spawnShower` (`:142`): `width = baseWidth + modifier * intensity`, `lootAmt = max(1, round(intensity * treasureModifier))`, `density = min(maxDensity, intensity)`, `runtime = baseRuntime * min(maxRuntimeMult, intensity)` in seconds. Arc centre at random distance and angle; start and end at `angleToCenter +- (50..80)` intersected with the `gridLimit` square (`intersectWithGridBoundary`), circle through the three points (`TrigHelper.findThreePointCircle`). Delay `max(2, nextInt(6))` days, 0 in immediate mode.

### Collision

`MeteorEntity.advance` (`:65`): only when within 1000 of the viewport; targets are stations and fleets minus salvageable entities; hit when centre distance < meteor radius. A meteor hit by a consumable missile (`MissileMemFlags.MEM_CAUGHT_BY_MISSILE` in its memory) collides with a token at the missile location, so treasure drops without hitting a fleet (`:71-83`).

### Radiation

`SpicyRockEntity` raises `$IndEvo_RadEffectLevel` by 0.1/s on nearby fleets. Player decay in `RadiationEffectHandler.advance` (0.1/s after 1 s without exposure), NPC decay in `RadiationReductionScript`. `RadioactiveTerrain.applyEffect` turns the level into CR loss and peak performance and CR decay buffs, respecting `CORONA_EFFECT_MULT` and `PROTECTS_FROM_CORONA_IN_BATTLE`.

### Tutorial

`TutorialEncounterScript` -> roiders intercept -> `rules IndEvo_roiderTutorial_acceptOffer` -> `IndEvo_RegisterTutorialMeteorIntel` -> `MeteorShowerSpawningLocationIntel` counts down 3 days -> `spawnShower(loc, 4, ASTEROID, false)`, which adds its own 2..5 day delay unless immediate mode.

## Contracts

- All settings are `static final` fields read at class init (`MeteorSwarmManager.java:44-61`); changes apply after a restart. `IndEvo_meteor_immediateMode` is read per shower. Setting mechanics: [Settings](../core/SETTINGS.md).
- One swarm per location: the warning path renderer is a single instance in location memory, the last one wins (`MeteorSwarmWarningPathRenderer.java:16-18`). `RadioactiveTerrain` lives only while that key exists, and relies on being spawned after it (`RadioactiveTerrain.java:90`).
- `IntelManager.advance` does not advance intel (starsector-knowledge `obf/campaign/comms/v2/IntelManager.java:44`), so `MeteorShowerLocationIntel` adds itself as a sector script (`:31`).
- `BaseCampaignEntity.runScripts` drops a script as soon as `isDone()` is true (starsector-knowledge `obf/campaign/BaseCampaignEntity.java:636-647`), so `PayloadOnFinishedModuleRunner` runs its payload in the advance that ends the movement and reports done from its own flag (`PayloadOnFinishedModuleRunner.java:20-36`). Payloads are named `Script` classes: lambdas come back from the save as null.
- Planetoid swarm planets carry `PlanetoidSwarmSpawner.PLANETOID_TAG` (`indevo_planetoid`); the load cleanup finds them by it.
- Spawners use a seeded `Random` per shower; the manager uses one saved `Random` in persistent data.
- Java 17 features (switch expressions, `instanceof` patterns) are used; the project language level is `JDK_17` (`.idea/misc.xml:6`).
- Couplings: `MissileMemFlags` ([Consumables](../items/CONSUMABLES.md)); `CircularArc`, `TrigHelper`, `MiscIE`, `NebulaParticle` ([Utils](../core/UTILS.md)); `Ids.LAB_ENTITY`, `Ids.ARSENAL_ENTITY`, `Ids.ABANDONED_PETSHOP_ENTITY` loot stations ([Stations](STATIONS.md)); vanilla `AbyssalRogueStellarObjectEPEC.PLANETOID_TYPES` for planetoid types.

## Known issues and dormant code

| Item | State |
|---|---|
| `MeteorSwarmManager.METEOR_TAG` | Never added to any entity; only used in a commented block (`RadioactiveSwarmSpawner.java:103`) |
| `RelativeArcingMovement`, `RadiationTrailRenderer` | Dead code |
| Tutorial rules text | Promises a "core-fragment" swarm (`rules.csv:23`, `:37`) but the tutorial forces `ASTEROID` |
| Changelog_todo | "bump default meteor storm min treasure to 2" (`Changelog_todo:5`), "Meteor shower bar event" (`Changelog_todo:53`) |

## Maintenance

Update this doc when a swarm type, a setting, a meteor entity id, the collision rule or the tutorial changes. Missile behaviour belongs in [Consumables](../items/CONSUMABLES.md), setting mechanics in [Settings](../core/SETTINGS.md), arc and trig helpers in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
