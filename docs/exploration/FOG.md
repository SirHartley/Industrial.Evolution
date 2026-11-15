# Fog

Fog weather in star systems near the player: the roll, the fog terrain and its sensor cut, the fluid solver that moves the fog, the shader that draws it, and the lurker fleets that ambush inside it. Read it before touching `indevo/exploration/fog/` or `IndEvo_fog_enabled`. It does not cover the Chaff Cloud consumable ([Consumables](../items/CONSUMABLES.md)), which keeps its own terrain and renderer.

Checked against Starsector 0.98a-RC8 (knowledge skill build 0.98.5a-RC4) and IE `55b1c98`. Recheck engine facts after a game update. Not yet seen in game.

## Overview

On each economy tick `FogWeatherManager` may add fog to one star system within 20 light years of the player. Core systems only get banks, never a spell. Banks are common (about one system in three near the player), spells rare (at most 3 at a time). Fog comes in two kinds:

| Kind | Size and place | Timing | Lurkers | Cache |
|---|---|---|---|---|
| Spell | Whole system: centred on the system centre, radius = farthest planet or jump point + 2500, at least 8000 | Rolls in over 2 days, holds 25..35 days, fades over 8 days | `SPELL_AMBUSH_CHANCE` 35%: 1 or 2 fleets, else none | Never |
| Bank | 8000..14000 across (outer reach), a misshapen cluster of bent lobes. 60% wrapped round a gas giant and moving with it, else on a slow orbit round the star between planet orbits | Rolls in over 2 days, holds 90..180 days, fades over 8 days | `BANK_AMBUSH_CHANCE` 10%: 1 fleet, else none | `BANK_CACHE_CHANCE` 15%: one small vanilla cache hidden inside, removed with the bank |

Inside fog the sensor range of a fleet and the range at which it is detected both drop to `1 - 0.9 * density`, so two fleets in thick fog only see each other when they almost touch. Moving fleets, planets and stations push the fog, fleets leave trails, Emergency Burn blows a hole and Interdiction Pulse an expanding ring. The fog and its movement are the only sign of fleets the player cannot see.

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/fog/FogWeatherManager.java` | Transient `EconomyTickListener`. Roll, system choice, spell and bank placement, lurker and cache spawn, the list of active fog in persistent data |
| `sim/FogNoise.java` | Hash, value noise, fbm, smoothstep. No engine imports |
| `sim/FogGrid.java` | Fluid solver on a scrolling window, port of the mockup's `sim_inline.js`. Nested `Shape` (rest mask and ambient curl, a pure function of local position, seed and time) and `Mover`. No engine imports, runs headless |
| `terrain/FogTerrainPlugin.java` | `BaseTerrain` for `IndEvo_fog`. Saved: kind, radius, seed, start timestamp, roll in, hold, fade, the bank's cache. Strength from the clock, sensor cut, density lookup, sound factor, feeds movers, abilities and clear patches into the grid, map outline, tooltip, hides its cache, removes itself and the cache when over |
| `renderers/FogRenderer.java` | Static drawing helper called from the terrain's `render`: grid packed into one RGBA texture per frame, GraphicsLib shader program, plain vertex coloured mesh when the program is 0 |
| `fleet/FogLurkerAssignmentAI.java` | `EveryFrameScript` on a lurker fleet: hold a post in the fog, chase a strong trail, return, leave and despawn when the fog ends |
| `indevo/console/SpawnFog.java` | Console command `indevo_spawnFog [spell\|bank] [ambush] [cache]` for testing ([Console](../core/CONSOLE.md)) |
| `data/shaders/indevo_fogVertex.shader`, `indevo_fogFragment.shader` | Fog shader, desktop GLSL port of the mockup's `FS`. Works in bank local coordinates |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Setting | `IndEvo_fog_enabled` (true), Quick Settings | `data/config/LunaSettings.csv:9`, `indevo/config/Setting.java:23` |
| Terrain | `IndEvo_fog` -> `FogTerrainPlugin`, music suppression 1, loop `terrain_nebula` | `data/campaign/terrain.json:47` |
| Description | `IndEvo_fog` `TERRAIN` (tooltip text) | `data/strings/descriptions.csv:73` |
| Effect category | `IndEvo_fog` (`$terrain_IndEvo_fog` per fleet) | `FogTerrainPlugin.EFFECT_CATEGORY` |
| Stat mods | `IndEvo_fog_stat_mod_1` (detected range), `_2` (sensor range), temporary mults for 0.1 days | `FogTerrainPlugin.applyEffect` |
| Stat mod | `IndEvo_fog_cache` (mult on the cache's detected range) | `FogTerrainPlugin.CACHE_MOD_ID` |
| Persistent data | `IndEvo_fogTerrains` (`List<SectorEntityToken>` of fog terrain entities) | `FogWeatherManager.DATA_KEY` |
| Memory key | `$IndEvo_fogLurker` (true on lurker fleets) | `FogLurkerAssignmentAI.MEM_LURKER` |

Tuning constants are public statics on their owner: roll chances, caps, durations, sizes, ambush chances, lurker size, cache chance and types on `FogWeatherManager`; sensor cut, cache sensor cut, density threshold, window size, keep disc margins on `FogTerrainPlugin`; solver constants on `FogGrid`; notice range and chase time on `FogLurkerAssignmentAI`.

## Registration and lifecycle

- `ModPlugin.onGameLoad() -> setListenersIfNeeded() -> FogWeatherManager.register()` adds a transient listener if none is there. Old saves get fog from the next economy tick.
- Fog is a terrain entity added with `addTerrain`, saved with its plugin. It orbits the system centre or a gas giant (`setCircularOrbit(focus, 0, 0, 100)` like vanilla magnetic fields, or a star orbit with period `0.1 * radius` days).
- Lurkers are fleets with a saved `FogLurkerAssignmentAI` script.
- A bank's cache is a vanilla salvage entity on an orbit round the fog entity; the plugin keeps it in the saved field `cache` and expires it when the fog is over.
- The grid, movers, rings, clear patches and the keep discs are transient fields of the plugin, built while the player is in the system and dropped when the player leaves. GL objects are static in `FogRenderer`, created once per application.

`reportEconomyTick -> getActiveFog (prune) -> [IndEvo_fog_enabled] roll -> spawnNearPlayer -> isValidSystem -> spawn -> addTerrain + placeBank -> spawnCache -> spawnLurker -> FogTerrainPlugin.advance -> BaseTerrain.advance (applyEffect) -> advanceView -> FogGrid.step -> render -> FogRenderer.render`

## Flow

### Roll

- `reportEconomyTick` (`FogWeatherManager.java:81`): prunes dead entries, returns if the setting is off, counts spells and banks. Each tick (10 per month, `economyIterPerMonth`): spell 15% while fewer than 3 are active; bank 50% while fewer than 40 are active and, in `spawnNearPlayer`, fewer than `BANK_SHARE` 33% of the systems in range that can hold a bank (banked plus valid) have one. Near a player who stays in one area that is a new bank about every 6 days until one system in three has one.
- `isValidSystem(system, kind)` (`:122`): spells never in `THEME_CORE` systems, banks are allowed there; for both not `THEME_HIDDEN`, `THEME_SPECIAL`, `SYSTEM_ABYSSAL`, `SYSTEM_CUT_OFF_FROM_HYPER`, no fog yet, and nebula cover at most `MAX_NEBULA_COVER` 10%. `getNebulaCover` (`:131`) counts the tiles of every `nebula` terrain (`BaseTiledTerrain.getTiles`, 400 units each) whose centre lies in the disc a spell would fill (`getSystemRadius`: outermost planet or jump point plus 2500, at least 8000) against that disc's area, so nebula in the outskirts does not count. Systems under the limit take the nebula's colour. Markets do not block fog: core colonies can get a bank, player colonies outside the core a bank or a spell. Candidates within 20 light years of the player fleet's hyperspace position, one picked at random.
- `spawn` (`:152`) throws if the system already has fog.
- `placeBank` (`:204`): a random gas giant with 60%, else a star orbit at the midpoint between two adjacent planet orbits or beyond the outermost, at least star radius + bank radius + 500 out.

### Strength and density

- Strength: 0 to 1 over the roll in, 1 during hold, 1 to 0 over the fade, from `getElapsedDaysSince(start)`. At the end the terrain removes its entity and the manager drops it on the next tick.
- Rest mask (`FogGrid.Shape.mask`): in bank local coordinates (position minus the terrain entity's location), time = elapsed days times seconds per day. Bank (`bankBody`): 5..7 stretched lobes from the seed (`buildLobes`, lobe scale = radius / `Shape.LOBE_REACH` 2.4) in a slowly drifting warped space. The distance to the nearest lobe, in lobe sizes, is scaled by a slow outline noise (0.45..1.75) and the body is solid inside `EDGE_IN` 0.62 and gone at `EDGE_OUT` 1.3, so the middle of every lobe is full fog and only the outline is thin; ridged noise adds bent arms out to `ARM_REACH` 2.6 lobe sizes. Trails, the sensor cut and the sound need the solid body: do not thin it to shape the outline. The outline reaches about 1.1..1.3 radius. Spell (`spellBody`): ragged disc, edge at 0.85..1.15 radius. Both times a 0.5..1.2 inner noise and the keep discs. Keep discs: spells keep planet bodies clear (planets poke out); banks also keep jump points (radius + 400), stations and market entities (radius + 300) clear. Refreshed every 0.1 days.
- `getDensityAt` (`FogTerrainPlugin.java:167`): the grid inside its window, else rest mask times strength. Stats, the tooltip, `containsPoint` (density above 0.05) and lurkers all read it, so they match what the player sees.

### Sensor cut

`applyEffect` (`:236`) adds `1 - 0.9 * density` as a 0.1 day temporary mult to `getDetectedRangeMod()` and `getSensorRangeMod()`, as the vanilla abyss does. While a fleet's Active Sensor Burst is active or in progress the sensor range mult is skipped; the detected range mult stays. `BaseTerrain.advance` applies one terrain per effect category per fleet per frame, so fog never stacks with itself.

### Sound

`terrain.json` gives `IndEvo_fog` the vanilla nebula's sound: `musicSuppression` 1 and `loopOne` `terrain_nebula`. `BaseTerrain.advance` plays them while the player fleet is inside (density above 0.05). `getProximitySoundFactor` scales both by the density at the player, full at `FULL_SOUND_DENSITY` 0.6, so music fades out and the hum fades in with the fog's thickness.

### Grid and drawing

- `advanceView` (`:400`), only in the player's location and, for a bank, while the view centre is within the render range. 128 x 128 cells of 80 units in bank local coordinates, recentred by whole cells when the view centre drifts more than 8 cells; cells that scroll in start at the rest mask, border cells are held at the rest mask. One solver step per frame, `dt` clamped to `FogGrid.MAX_DT` (0.1 s).
- Movers (`feedMovers`, `:440`): every fleet in the window with its velocity minus the bank's own velocity; planets and stations with their frame to frame speed (jumps over 600 units count as no motion); meteor shower and rogue rocks (`MeteorSwarmManager.METEOR_TAG`) the same way, with half their entity radius (the hit radius is twice the sprite) and `ROCK_CUT` 0.8.
- Body wakes (`FogGrid.wake`): a mover's cut scales with speed / 170, and a moon at about 15 units per second cannot outrun the refill. Planets and stations are fed with `Mover.body`: full cut at any speed above 2, plus a tapering cut at up to `WAKE_SAMPLES` 12 points kept along the path behind, spaced half a wake radius (carve radius, at least `WAKE_MIN_CELLS` 2.2 cells). About 1000 units for a small moon. Rocks and fleets are fast enough and get none.
- Abilities (`readAbilities`, `:495`): Emergency Burn blasts when its level reaches 1; Interdiction Pulse rings out to `InterdictionPulseAbility.getRange` over 0.6 s when `$justDidInterdictionPulse` appears. Polled per fleet in the window: no callback reports NPC ability use, and the pulse's level is 1 for at most a frame (duration 0.001 days, `abilities.csv`), so the memory flag is the reliable signal.
- Clear patches: the player fleet always, other fleets while the player can see them (fade in 0.5 s, out 0.8 s), up to 16, radius 2.6 x fleet radius.
- `render` draws on `TERRAIN_8` (above `FLEETS`) one world space quad over the visible part of the window. The texture packs density / 1.3, the advected detail offset and the flow speed. Alpha rises over a long soft ramp (`cover / 0.85`, smoothstep, power 1.1). Fine wisps: ridged noise in warped space, drawn only in the thin fog of the outer edge (density 0.02..0.5 and a mean density below 0.55 over eight samples 900 units out), so trails and blown holes inside the fog stay empty. Colours: `FogRenderer.coloursFor` takes the average colour of the system's first `nebula` terrain texture (`BaseTiledTerrain.getParams()` cat and key, `SpriteAPI.getAverageColor`), scaled to a dark, body and light shade; `FogRenderer.GREY` without a nebula. Cached per plugin while loaded.
- `renderOnMap` draws the rest mask on a 40 x 40 grid out to 1.4 radius in the light shade, alpha 0.35 x mask x strength, so the map shows the real outline.

### Cache

- `spawnCache` (`FogWeatherManager.java:257`): banks only, `BANK_CACHE_CHANCE` 15%. One of `supply_cache_small`, `equipment_cache_small`, `weapons_cache_small` through `BaseThemeGenerator.addSalvageEntity` (faction neutral, vanilla salvage and discovery). Placed at the thickest rest mask of up to 30 tries, 0.1..0.35 radius from the centre, on a 300 day orbit round the fog entity so it moves with the bank.
- `hideCache` (`FogTerrainPlugin.java:203`), each frame in the player's location and once at spawn: `1 - 0.7 * density` as a mult on the cache's `getDetectedRangeMod()`. Density is `getDensityAt`, so a hole blown next to the cache shows it; during the roll in the rest mask is used without the strength, so the cache never sits in the open first. During the fade the cut shrinks with the fog.
- Spot range of a small cache is `(sensor strength + profile 1 + 1200) x cache mult x own sensor mult` (`BaseCampaignEntity.getBaseSensorRangeToDetect`, `getMaxSensorRangeToDetect`, knowledge skill `obf/campaign/BaseCampaignEntity.java:1089`; detection range 1200 from `salvage_entity_gen_data.csv`). Estimate for a fleet sensor strength of 450..1050: 1650..2250 in the open, 500..675 from outside thick fog, 50..70 from inside it, about 1400..1600 with Active Sensor Burst (3000 flat, own cut skipped).
- When the fog is over the plugin calls `Misc.fadeAndExpire` on a cache that is still there. A looted cache expires the vanilla way and the plugin drops its reference.

### Lurkers

- Built with `FleetFactoryV3`, pirates 70% else Luddic Path, 30..70 combat points, fleet type `patrolMedium`, transponder off, `$IndEvo_fogLurker`. The post is a random point in the fog (best rest mask of 30 tries, 0.2..0.7 radius out), kept as an offset from the fog centre so it moves with a bank.
- `FogLurkerAssignmentAI.advance` (`FogLurkerAssignmentAI.java:53`): `HOLD` at the post (re-issued when the post moved more than 600 units). A trail is noticed (`noticesTrail`, `:105`) only for the player fleet, within 1500 units, not `Misc.isSlowMoving`, inside fog (density above 0.05), and only if `fleet.isHostileTo(player)`. Then `INTERCEPT` the player. The lurker cannot see the player in fog, and the vanilla tactical module sends a fleet with an unseen target to `$ai_lastSeenTargetLoc`, or to a random point 3000 units away when that is unset (knowledge skill `obf/campaign/ai/TacticalModule.java:573`), so while the trail is noticed the script writes the player's position to `FleetAIFlags.LAST_SEEN_TARGET_LOC` and clears `DAYS_TARGET_UNSEEN` each frame. When the trail stops the vanilla search pattern runs round the last point; 2 days after the last sighting the lurker returns to the post. When the fog is gone it goes to the nearest jump point (else the system centre) with `GO_TO_LOCATION_AND_DESPAWN`.
- `HOLD` never engages, so a player going slow or dark past a lurker is safe; the vanilla game never reads `TerrainAIFlags`, so this script is the only fog behaviour NPCs have.

## Contracts

- `FogGrid` and `FogNoise` import nothing from the game. Keep it that way: the headless check compiles them alone.
- The rest mask is a pure function of local position, seed and time, so the shape follows an orbiting bank and is the same after a load. The grid is never saved.
- Strength, the end of the fog and the mask time come only from the saved start timestamp and the campaign clock.
- Effects run for fleets within `getRenderRange()` (`:252`) of the fog centre: `BaseTerrain.advance` skips fleets farther out (knowledge skill `api/impl/campaign/terrain/BaseTerrain.java:89`), and `CampaignTerrain.render` only calls `render` when the centre is within it of the viewport (`obf/campaign/CampaignTerrain.java:88`).
- Spot range is `(observer sensor strength + target profile) x target detected range mods x observer sensor range mods` (knowledge skill `obf/campaign/BaseCampaignEntity.java:1089`), so the cut on both sides multiplies.
- `ShaderLib.loadShader` returns 0 when the shader fails (GraphicsLib 1.9.0). `FogRenderer` then logs a warning once and draws the plain mesh.
- Fog shader uniforms and the texture layout are coupled between `FogRenderer.renderShader`, `FogGrid.pack` and `indevo_fogFragment.shader`.
- Couplings: `MeteorSwarmManager.METEOR_TAG` ([Meteor](METEOR.md)); `MiscIE.getRandomInRange` ([Utils](../core/UTILS.md)); `Setting.FOG_ENABLED` ([Settings](../core/SETTINGS.md)).

## Known issues and dormant code

| Item | State |
|---|---|
| Not seen in game | Shader look, layer order against fleets and other terrain, frame time, window edge at full zoom out, ambush behaviour and the lurker chase are unverified |
| Window size | 128 x 80 = 10240 units. A view wider than about 10000 units shows the fog cut at the window edge |
| Chaff Cloud | Still uses its own renderer; moving it to the fog renderer is a later task |

## Maintenance

Update this doc when a fog kind, a timing, the sensor cut, the solver constants, the shader or the lurker behaviour changes. Setting mechanics belong in [Settings](../core/SETTINGS.md), console commands in [Console](../core/CONSOLE.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
