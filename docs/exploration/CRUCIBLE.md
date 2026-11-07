# Helios Crucible

The Helios Crucible superstructure: spawner, entity plugins, catapult (yeetopult) travel, startup and move animations, the crucible field terrain and its rules dialog. Read it before touching `indevo/exploration/crucible/` or the crucible rows in `rules.csv`. It does not cover the shared animation and particle helpers; see [Utils](../core/UTILS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

A large derelict station (`IndEvo_crucible_top` plus a bottom sprite and gears) placed in a planet-rich, uninhabited procgen system. Every planet gets a catapult (`IndEvo_yeetopult`) paired with a catapult orbiting the crucible; once the crucible is activated, flying into a catapult throws the fleet at its partner. The rules dialog explores, activates and colonizes the crucible.

State: dormant. Nothing calls `CrucibleSpawner.spawn()` (`indevo/utils/ModPlugin.java:295` and `:203` are commented out), so no crucible exists in a normal game. It can only be placed by console runcode (`CrucibleSpawner.spawnInCurrent()`). The package is compiled (not excluded by `IndEvo.iml`).

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/crucible/plugin/CrucibleSpawner.java` | System pick, placement, catapult pairs, gear rings. Owns `$IndEvo_hasPlacedCrucibles`. Static helpers, no state |
| `indevo/exploration/crucible/entities/BaseCrucibleEntityPlugin.java` | Crucible top entity: enabled tag, activity level, drone loop, mote spawning, warp/glow render. `enable()` starts startup anim, `disable()` starts move anim |
| `indevo/exploration/crucible/entities/CrucibleStationEntityPlugin.java`, `CrucibleSubStationEntityPlugin.java` | Set `CrucibleData` render constants for the large and sub crucible (`:10` each) |
| `indevo/exploration/crucible/entities/YeetopultEntityPlugin.java` | Catapult: target id, paired catapult id, colour, trigger on player approach, glow and fire anim, map tooltip |
| `indevo/exploration/crucible/entities/CrucibleMoteEntityPlugin.java` | Ambient mote: crucible -> crucible catapult -> paired planet catapult, then fade |
| `indevo/exploration/crucible/entities/CrucibleGearEntityPlugin.java` | Gear sprite, spin and orbit speed scaled by activity level |
| `indevo/exploration/crucible/entities/CrucibleArmEntityPlugin.java` | Arm sprite pinned to crucible, faces its catapult |
| `indevo/exploration/crucible/entities/CrucibleScaffoldRotationEntityPlugin.java` | Scaffold rotation. Dead: scaffold spawn is commented out (`CrucibleSpawner.java:194`) |
| `indevo/exploration/crucible/entities/CrucibleSpikeEntityPlugin.java` | Spike orbit. Dead: nothing spawns `IndEvo_crucible_spike` |
| `indevo/exploration/crucible/scripts/VariableOrbitScript.java` | Manual circular orbit with a speed `factor`; used for crucible parts, gears and crucible catapults |
| `indevo/exploration/crucible/scripts/CrucibleStartupAnimationScript.java` | `BaseStagedAnimationScript`: catapults on one by one, gears ramp, crucible on, field spawn |
| `indevo/exploration/crucible/scripts/CrucibleMoveAnimationScript.java` | `BaseStagedAnimationScript`: motes to centre, flare, then removes the whole assembly |
| `indevo/exploration/crucible/scripts/CrucibleTrajectoryFollowingMote.java` | Luna-rendered mote for the move anim |
| `indevo/exploration/crucible/ability/YeetScript.java` | Fleet script: forced high speed travel to the catapult target |
| `indevo/exploration/crucible/plugin/VignetteRenderer.java` | Screen vignette during `YeetScript` |
| `indevo/exploration/crucible/terrain/CrucibleFieldTerrainPlugin.java` | `MagneticFieldTerrainPlugin` subclass with an alpha ramp; `generate()` adds `crucible_field` |
| `indevo/exploration/crucible/YeetopultColourList.java` | 40 colours, picked without repeat per catapult pair |
| `indevo/exploration/crucible/dialogue/IndEvo_StartCrucible.java` | Rule command: `BaseCrucibleEntityPlugin.enable()` |
| `indevo/exploration/crucible/dialogue/IndEvo_ColonizeCrucible.java` | Rule command: player market on the crucible |
| `indevo/exploration/crucible/dialogue/IndEvo_HasGenericSpecialItemInCargo.java` | Rule condition: special item in player cargo or any submarket of the target's market |
| `indevo/exploration/crucible/dialogue/Todo` | Design notes (repair, move, gate, colour options), not code |
| `data/campaign/crucible/rules - crucible cargo exploration.csv`, `Crucible Exploration Dialogue.docx` | Draft exploration dialog (cargo hold branch only). Not loaded: the game reads rules from `data/campaign/rules.csv` only (starsector-knowledge `docs/concepts/rules-engine.md:3`) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Custom entity | `IndEvo_crucible_top` (plugin `CrucibleStationEntityPlugin`, tags `has_interaction_dialog`, `IndEvo_crucible`, `IndEvo_crucible_part`, `station`) | `data/config/custom_entities.json:581` |
| Custom entity | `IndEvo_crucible_bottom` (no plugin), `IndEvo_crucible_arm`, `IndEvo_crucible_gear`, `IndEvo_crucible_scaffold`, `IndEvo_crucible_spike` | `custom_entities.json:630`, `:649`, `:669`, `:610`, `:686` |
| Custom entity | `IndEvo_sub_crucible_top`, `_bottom`, `_arm` (sub unit, never spawned) | `custom_entities.json:514`, `:541`, `:559` |
| Custom entity | `IndEvo_yeetopult` (planet side, map icon), `IndEvo_yeetopult_no_icon` (crucible side, tag `IndEvo_orbits_crucible`), `IndEvo_CrucibleMote` | `custom_entities.json:706`, `:744`, `:732` |
| Tag | `IndEvo_yeetopult` (`Ids.TAG_YEETOPULT`), `IndEvo_crucible`, `IndEvo_crucible_part`, `IndEvo_crucible_gear`, `IndEvo_orbits_crucible` | `indevo/ids/Ids.java:37`, `custom_entities.json` |
| Tag | `crucible_enabled` (`TAG_ENABLED`), `IndEvo_animPlaying` (`TAG_ANIMATION_PLAYING`) | `BaseCrucibleEntityPlugin.java:44`, `:48` |
| Memory key | `$crucible_activity_Level` (0..1 per entity), `$crucible_catapult_num` (catapult index) | `BaseCrucibleEntityPlugin.java:45-46` |
| Memory key | `$IndEvo_crucible` (on top entity, rules match on it) | `CrucibleSpawner.java:233` |
| Persistent data | `$IndEvo_hasPlacedCrucibles` | `CrucibleSpawner.java:37` |
| Terrain | `crucible_field` -> `CrucibleFieldTerrainPlugin` | `data/campaign/terrain.json:35` |
| Constant | `CrucibleSpawner.AMOUNT` 1, extra non-nebula crucibles; no setting while crucibles are dormant | `CrucibleSpawner.java:38` |
| Sounds | `IndEvo_mote_yeet`, `IndEvo_crucible_start`, `_alarm`, `_drone`, `_off`, `_transit` | `data/config/sounds.json:121-146` |
| Sprites | `IndEvo/crucible_mass`, `IndEvo/whirl_round`, `IndEvo/gearwheel`, `IndEvo/vignette_square` | `data/config/settings.json:140` and graphics block |
| Descriptions | `IndEvo_crucible`, `IndEvo_yeetopult` (no row for `IndEvo_sub_crucible`) | `data/strings/descriptions.csv:27-29` |
| Rules | `IndEvo_crucible_*` (market and no-market menus, travel options, colonize, explore, activate) | `data/campaign/rules.csv:99-252` |
| Rule command package | `indevo.exploration.crucible.dialogue` | `data/config/settings.json:85` |
| Music | `IndEvo_Haplogynae_derelict_theme` via `MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY` | `CrucibleSpawner.java:232` |

## Registration and lifecycle

- Spawn: `CrucibleSpawner.spawn()` would run once per sector, guarded by `$IndEvo_hasPlacedCrucibles`. Call sites are commented out (`ModPlugin.java:295`, `:203`).
- Entities: `custom_entities.json` `pluginClass` columns. Plugin state is saved with the entity; sprites are re-fetched in `readResolve()` (`BaseCrucibleEntityPlugin.java:113`, `YeetopultEntityPlugin.java:68`).
- Rules: commands resolve through `ruleCommandPackages` (`settings.json:85`).
- Scripts: `VariableOrbitScript` on each part, startup anim on the crucible entity, move anim on the star system, `YeetScript` on the fleet. All saved (non-transient).

`spawn() -> getTargetSystem() -> getSpawnLoc2() -> spawnCrucible() (top, bottom, gears) -> spawnCatapults() (pairs, arms)`

## Flow

### Placement

- `getTargetSystem(nonNebulaOnly)` (`CrucibleSpawner.java:168`): skip systems with a yeetopult, any market, non-procgen, `THEME_CORE`, `THEME_SPECIAL`, `THEME_HIDDEN`, `SYSTEM_ABYSSAL`; take the one with most planets. First pick allows nebulae, the `IndEvo_CrucibleNum` extra picks do not (`:68-81`).
- `getSpawnLoc2()` (`:128`): ring search from radius 2000 (+ star radius) in steps of 500 until no planet is within `MIN_RADIUS_AROUND_CRUCIBLE` 1500 at any degree, then a random angle on that ring. Unbounded loop (comment says it hangs if nothing fits).
- `spawnCrucible()` (`:189`): top is discoverable (1000 XP, sensor profile 2000). With a star, parts use `VariableOrbitScript(..., moveWhenNotInLoc=false)` with period `-min(364, 31 / (1000 / dist))` days.
- `spawnCatapults()` (`:238`): one pair per non-star planet, moons of gas giants skipped. Planet catapult orbits at `radius + max(100, 0.2 * radius)`; crucible catapults ring the crucible at `radius + 45` with `VariableOrbitScript` factor 0.05 and get `$crucible_catapult_num` 1..n and an arm entity.

### Activation

`rules IndEvo_crucible_startActivate -> IndEvo_StartCrucible -> BaseCrucibleEntityPlugin.enable() -> CrucibleStartupAnimationScript`

Stages (`CrucibleStartupAnimationScript.java:64-291`): catapult pairs enabled one per second with fire anim and `IndEvo_crucible_off` -> dust bursts -> gears and crucible catapults ramp `$crucible_activity_Level` 0..1 over 8 s -> after 6 s crucible gets `crucible_enabled`, dust renderers, `CrucibleFieldTerrainPlugin.generate()` and an `IndEvo_VariableExplosion` (no damage) -> crucible and field ramp over 10 s -> `IndEvo_animPlaying` removed. The crucible is `NON_CLICKABLE` until its enable stage.

Enabled crucible (`BaseCrucibleEntityPlugin.advance`): drone loop by distance, facing spin by activity, a mote every 4..15 s once activity > 0.9 and no anim plays.

### Catapult travel

`YeetopultEntityPlugin.advance()` (`:78`): if enabled, the player's interaction target is this catapult and the fleet is within `radius + 30`, add `YeetScript(fleet, target)` and fire anim (`:108-111`). It only runs while the catapult is in the current location.

`YeetScript` (`ability/YeetScript.java:88`): sets velocity along the origin -> target line, travel time clamped to 1..3 s, last 25% decelerates (`DECEL_FRACTION`). Until 85% it moves the fleet itself, disables slipsurge-incompatible abilities, sets `setNoEngaging`, clears the course and multiplies sensor range by 0.1 (`SENSOR_MOD_ID` `slipsurge_sensor_penalty`). Ends at 98% distance. Plays `ui_slipsurge_travel_loop`, jitter and vignette by burn level.

### Move and removal

`BaseCrucibleEntityPlugin.disable() -> CrucibleMoveAnimationScript` (on the star system): alarm, motes from each crucible catapult to the centre, flare, then `CrucibleSpawner.removeFromtLoc()` removes every `IndEvo_crucible_part` and the magnetic field within 500 of the crucible. Nothing calls `disable()` except a runcode comment.

### Colony

`rules IndEvo_crucible_finalizeColonize -> IndEvo_ColonizeCrucible`: size 3 player market "The Crucible" with `population_3`, `ruins_vast`, `decivilized_subpop`, population industry, storage (paid) and local resources, spaceport queued, then the faction config dialog on the next frame (`IndEvo_ColonizeCrucible.java:21-72`). Rules require `crucible_enabled`.

## Contracts

- Activity level lives in entity memory (`$crucible_activity_Level`), not in the plugin; gears, catapults and the crucible read it every frame. `VariableOrbitScript.setFactor()` is how parts speed up (`CrucibleGearEntityPlugin.java:52`, `YeetopultEntityPlugin.java:116`).
- Catapult pairing is by entity id strings (`targetEntity`, `pairedCatapult`) resolved with `getContainingLocation().getEntityById()`; both ends must stay in the same location.
- `IndEvo_HasGenericSpecialItemInCargo` checks all submarkets of a non condition-only market first, then the player cargo (`:24-32`).
- Shared code: `BaseStagedAnimationScript`, `RadialDustCloudEjectionRenderer`, `DeceleratingDustCloudEjectionRenderer`, `BaseCircleTrajectoryFollowingParticle` ([Utils](../core/UTILS.md)); `VariableExplosionEntityPlugin` ([Artillery](../industries/ARTILLERY.md)). `DeceleratingDustCloudEjectionRenderer.java:70` stops on missing `BaseCrucibleEntityPlugin.TAG_ENABLED`, so that util is coupled to this package.
- `CrucibleSpawner.AMOUNT` is a plain constant. Add a setting row back when crucibles return.
- Mote speed borrows `RailgunProjectileEntityPlugin.PROJECTILE_VELOCITY` (`CrucibleMoteEntityPlugin.java:63`).

## Known issues and dormant code

| Item | State |
|---|---|
| `CrucibleSpawner.spawn()` | Unregistered. Both calls commented out (`ModPlugin.java:203`, `:295`) |
| `rules.csv:152` vs `:154` | Wormhole option shows on `wormhole_anchor`, disabled variant checks `!... coronal_portal`. Mismatched item ids |
| `rules.csv:158`, `:160` | Gate option checks item `coronal_tap`; the Hypershunt Tap id is `coronal_portal` (starsector-knowledge `data/campaign/special_items.csv:15`). Gate unlock can never show |
| Options `IndEvo_crucible_useWormhole_select`, `_useGate_select`, `_moveCrucible_select`, `_changeCatapultColour_select`, `IndEvo_c_hab_0`, `IndEvo_c_eng_0`, `IndEvo_c_cont_0`, `IndEvo_c_cargo_0` | No `DialogOptionSelected` rule in `rules.csv`; `IndEvo_c_cargo_0` exists only in the unloaded draft CSV |
| `CrucibleSpawner.removeFromtLoc(system)` | Ignores its parameter, uses the player's location and `get(0)` of `IndEvo_crucible` (`:40-53`) |
| `CrucibleSpawner.spawnCrucible()` nebula loop | `break` outside the `if`, only checks the first terrain (`:221-224`); result unused |
| `getSpawnLoc()`, sub crucible spawn path, `YeetopultEntityPlugin.changeColour()`, `CrucibleSpikeEntityPlugin`, `CrucibleScaffoldRotationEntityPlugin`, `YeetScript.setFleetVelocityNoForce()` | Dead code |
| `BaseCrucibleEntityPlugin.advance()` dev auto enable | Disabled by `false &&` (`:80`) |
| Per frame `ModPlugin.log` | `YeetopultEntityPlugin.java:102` while firing, `CrucibleMoveAnimationScript.java:179` during flare |
| Missing description `IndEvo_sub_crucible` | Referenced by `IndEvo_sub_crucible_top`, no row in `descriptions.csv` |
| Changelog_todo | "add gate & wormhole integration options", "check npc pathing", "colony establishment" (`Changelog_todo:78-84`); "Add thing to jump between systems", "add npc pathing" (`Changelog_todo:87-91`) |
| `indevo/exploration/WIP/dyson/IndEvo_SuperStructureManager.java` | Excluded by `IndEvo.iml`. Dyson swarm superstructure stub (second star, ring, `IndEvo_dysonSwarm` planet); package line `indevo.superstructures.WIP.dyson` does not match the folder. Nothing references it. Belongs in [WIP](../core/WIP.md) |

## Maintenance

Update this doc when crucible spawning is hooked up, a crucible rule or command changes, or a crucible entity id changes. Animation and particle helpers belong in [Utils](../core/UTILS.md), settings mechanics in [Settings](../core/SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
