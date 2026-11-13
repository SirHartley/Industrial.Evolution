# Artillery

Orbital artillery stations, watchtowers, the campaign projectiles they fire, the derelict defender fight and the player industry. Read it before touching anything in `indevo/industries/artillery/` or the artillery hulls and weapons. It does not cover the combat-side weapon scripts in `data/scripts/weapons` (see [Combat](../core/COMBAT.md)) or the consumables that target or trigger artillery (see [Consumables](../items/CONSUMABLES.md)).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Some planets carry the `IndEvo_ArtilleryStationCondition` condition and a station entity (`IndEvo_ArtilleryStation`) of one of three types: `mortar`, `railgun`, `missile`. The station fires campaign projectiles at hostile fleets in range once they are "seen" by a watchtower (`IndEvo_Watchtower`) or a hostile fleet. On uncolonized planets the station is derelict (`IndEvo_derelict` faction), guarded by a defender fleet, and blocks survey and salvage until beaten. After that the planet can be colonized and the station rebuilt as the industry `IndEvo_Artillery_<type>`, which then fires for the owner. Four core-world markets get one at new game.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/artillery/industry/ArtilleryStation.java` | Industry plugin for all `IndEvo_Artillery_*` rows. Owns the player/NPC station fleet, CR, commander, faction sync, improve range bonus, build check |
| `indevo/industries/artillery/conditions/ArtilleryStationCondition.java` | Market condition. On first `advance` calls `ArtilleryStationPlacer.addArtilleryToPlanet(.., true)`; stores destroyed flag `$IndEvo_ArtilleryStation_Destroyed` |
| `indevo/industries/artillery/scripts/ArtilleryStationScript.java` | Persistent script on the planet for the derelict (no industry) state: spawns station entity and fleet, picks type, tracks system faction, destroyed/broken station, debris field relocation |
| `indevo/industries/artillery/scripts/CampaignAttackScript.java` | Script on the station entity. Target list, cooldowns, safe spots, forced targets, range terrain. Decides when and what to fire |
| `indevo/industries/artillery/scripts/EyeIndicatorScript.java` | Sector script and `BaseCampaignEventListener`. Owns the player "seen" state (`$IndEvo_WasSeenByOtherEntity`) and the eye indicator |
| `indevo/industries/artillery/scripts/ArtilleryStationReplacer.java` | Transient one-shot that re-adds artillery industries on new game. Dormant, see below |
| `indevo/industries/artillery/entities/ArtilleryStationEntityPlugin.java` | Custom entity plugin for `IndEvo_ArtilleryStation`. Holds type and disrupted flag, attaches `CampaignAttackScript`, `placeAtMarket` factory, tooltips |
| `indevo/industries/artillery/entities/WatchtowerEntityPlugin.java` | Objective plugin for `IndEvo_Watchtower`. Marks hostile NPC fleets in range as seen, range ping, hack state |
| `indevo/industries/artillery/entities/LighthouseEntityPlugin.java` | Objective plugin for `IndEvo_Lighthouse`, a watchtower subclass. Owns its beam terrain and the lit state; lit it marks fleets in the beams, unlit it runs the watchtower code |
| `indevo/industries/artillery/terrain/LighthouseBeamTerrain.java` | Terrain `IndEvo_lighthouse_beam`: rotating beams drawn with the vanilla `PulsarRenderer`, planet shadows, the "is this point in a beam" check, map drawing |
| `indevo/industries/artillery/scripts/DroneNetScript.java` | System script, one per drone net system. Owns the nests, the spotter drones and their movement, what they see, the drone and nest explosions, the nest debris and the change to watchtowers |
| `indevo/industries/artillery/terrain/DroneConeTerrain.java` | Terrain `IndEvo_drone_cones`, one per drone net system: draws every spotter cone and nest zone in the system and on the map. No effect, no tooltip |
| `indevo/industries/artillery/utils/VisionBlockers.java` | Line of sight helper for cones, nest zones and lighthouse beams: planets and chaff clouds as circles, `isBlocked`, `getRayLength` |
| `indevo/industries/artillery/entities/WatchtowerEyeIndicator.java` | Custom entity `IndEvo_Eye` that draws the eye above the player fleet |
| `indevo/industries/artillery/entities/VariableExplosionEntityPlugin.java` | `ExplosionEntityPlugin` subclass with sound, size and blastwave params. Shared: also used by consumables and the crucible |
| `indevo/industries/artillery/projectiles/MortarShotScript.java`, `MortarProjectileEntityPlugin.java` | Mortar volley: N reticules around the predicted target position, shells fly at 1500 su/s with a short trail (`TRAIL_WIDTH`, `TRAIL_TIME`), explode with `VariableExplosion`. Holds `ArtilleryReactionScript` (NPC avoidance) |
| `indevo/industries/artillery/projectiles/RailgunShotEntity.java`, `RailgunProjectileEntityPlugin.java` | Railgun volley: danger sign plus N lead-predicted shots at 6000 su/s with a target line. Drawn sizes are the constants at the top of each projectile class (`LINE_WIDTH`, `GLOW_SIZE`, `TRAIL_WIDTH`, `PROJECTILE_WIDTH/HEIGHT`) |
| `indevo/industries/artillery/projectiles/MissileShotScript.java`, `MissileCarrierEntityPlugin.java`, `MissileSubmunitionEntity.java`, `ECMExplosion.java` | Missile volley: curved carriers split into 3 homing submunitions; each submunition or a direct hit spawns an `ECMExplosion` (interdict plus slow field terrain) |
| `indevo/industries/artillery/renderers/ArtilleryFireRenderer.java`, `StationFireEffect.java` | Fire animation on the station: wind-up glow, muzzle flash, smoke, heat glow, vents. Drawing only, see [Fire animation](#fire-animation) |
| `indevo/industries/artillery/projectiles/RenderTemporaryIndicator.java` | Debug marker entity. Only used from commented code |
| `indevo/industries/artillery/terrain/ArtilleryTerrain.java` | Ring terrain `IndEvo_artillery_range_terrain`: range circle, name, tooltip, safe-spot display |
| `indevo/industries/artillery/terrain/SlowFieldTerrain.java` | Ring terrain `IndEvo_slowfield`: burn penalty via `Misc.getBurnMultForTerrain` |
| `indevo/industries/artillery/terrain/WatchtowerTerrain.java` | Copy of `ArtilleryTerrain`, not in `terrain.json`. Dead |
| `indevo/industries/artillery/listener/WatchtowerFactionResetListener.java` | `ObjectiveEventListener` and `NewDayListener`. Hands a captured watchtower back to the nearest AI artillery faction after a timeout |
| `indevo/industries/artillery/patches/WatchtowerStableLocationPatch.java` | PatchLib `@After` on `Misc.getNumStableLocations`: watchtowers no longer count as stable locations |
| `indevo/industries/artillery/rules/IndEvo_SalvageDefenderInteraction.java` | Rule command. Runs the defender fight (`FleetInteractionDialogPluginImpl`) and the win/loss bookkeeping |
| `indevo/industries/artillery/rules/IndEvo_ArtilleryDefenderGen.java` | Helper (not a command): builds or reuses the defender fleet plus the station hull |
| `indevo/industries/artillery/rules/IndEvo_DroneNest.java` | Rule command for the nest dialog: `destroy`, `salvage` (loot screen), `debris` |
| `indevo/industries/artillery/utils/ArtilleryStationPlacer.java` | New-game placement (derelict and core worlds), `addArtilleryToPlanet`, vision pick per system (`placeVision`), watchtower, lighthouse and drone net placement, `ArtilleryStationInteractionConfigGen` |
| `indevo/industries/artillery/utils/FleetBasedArtilleryStationPlacer.java` | Older fleet-as-station variant. Dead |
| `indevo/industries/artillery/hullmods/MagicInvisibator.java` | Hullmod `IndEvo_magicInvisibator`: hides the deco module ship and kills it on the first combat frame |

Combat side, linked only: `data/scripts/weapons/IndEvo_targetingBeamEffect.java` (railgun `everyFrameEffect`), `IndEvo_mortarProjectileOnFireEffect.java`, `IndEvo_missileProjectileOnFireEffect.java`, `ai/IndEvo_mortarProjectileAI.java`, `ai/IndEvo_missileProjectileAI.java`, `combatEntities/IndEvo_CombatSlowFieldTerrain.java`, `combatEntities/IndEvo_ForcedOverload.java`. The two AIs are picked in `ModPlugin.pickMissileAI` (`indevo/utils/ModPlugin.java:373`). See [Combat](../core/COMBAT.md).

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Artillery_base` (parent), `IndEvo_Artillery_mortar`, `_missile`, `_railgun` | `data/campaign/industries.csv:64-85`. `data` column is JSON: `radius`, `variant`, `fleetName` |
| Condition | `IndEvo_ArtilleryStationCondition` | `data/campaign/market_conditions.csv:23`; `condition_gen_data.csv:4` (frequency 0, never rolled by procgen; inferred from the column) |
| Custom entities | `IndEvo_ArtilleryStation`, `IndEvo_DestroyedArtilleryStation` (no plugin), `IndEvo_Watchtower`, `IndEvo_Lighthouse`, `IndEvo_DroneNest` and `IndEvo_SpotterDrone` (no plugin, placeholder sprites: the watchtower and the vanilla derelict picket), `IndEvo_Eye`, `IndEvo_artillery_projectile`, `IndEvo_railgun_projectile`, `IndEvo_RailgunShotEntity`, `IndEvo_missileCarrierPlugin`, `IndEvo_missileSubmunition`, `IndEvo_ECMExplosion`, `IndEvo_VariableExplosion`, `IndEvo_tempIndicator` | `data/config/custom_entities.json` |
| Terrain | `IndEvo_artillery_range_terrain`, `IndEvo_lighthouse_beam`, `IndEvo_drone_cones`, `IndEvo_slowfield` | `data/campaign/terrain.json:21-36` |
| Hulls | `IndEvo_artilleryStation_base` plus skins `IndEvo_artilleryStation_mortar/_missile/_railgun` (builtin weapon on `WS0005`, `solar_shielding`); modules `IndEvo_artilleryStation_gunModule`, `_hangarModule`, `_mortarModule` (no variant uses it); deco modules `IndEvo_MortarDecoModule`, `IndEvo_MissileDecoModule`, `IndEvo_RailgunDecoModule` (builtin `IndEvo_magicInvisibator`) | `data/hulls/`, `data/hulls/skins/`, `data/hulls/ship_data.csv` |
| Variants | `IndEvo_artilleryStation_<type>_variant` (2 gun, 2 hangar modules, 1 deco module), module and deco variants | `data/variants/` |
| Weapons | `IndEvo_artillery_mortar`, `IndEvo_artillery_railgun`, `IndEvo_artillery_missile` (tags `SYSTEM`) and projectiles `IndEvo_mortar_projectile`, `IndEvo_railgun_projectile`, `IndEvo_missile_projectile` | `data/weapons/`, `data/weapons/proj/`, `weapon_data.csv` |
| Hullmod | `IndEvo_magicInvisibator` (`hide_in_codex`) | `data/hullmods/hull_mods.csv:12` |
| Faction | `IndEvo_derelict` (`Ids.DERELICT_FACTION_ID`) | `indevo/ids/Ids.java:8` |
| Tags | `IndEvo_Artillery` (station entity, market, skin hulls), `IndEvo_Entity_has_artillery` (planet), `IndEvo_ArtilleryFleet` (industry station fleet), `IndEvo_watchtower` (watchtowers and lighthouses), `IndEvo_lighthouse`, `IndEvo_droneNest` and `IndEvo_spotterDrone` (data only, `IndEvo_droneNest` is the rules condition), `IndEvo_SystemHasArtillery` (system) | `indevo/ids/Ids.java:31-35`, `custom_entities.json` |
| Memory, market | `$IndEvo_ArtilleryType` (type), `$IndEvo_ArtilleryStation_Destroyed` | `ArtilleryStationScript.java:61`, `ArtilleryStationCondition.java:14` |
| Memory, planet | `$IndEvo_ArtilleryStationScript` (script instance), `$IndEvo_ArtilleryStation`, `$hasDefenders`, `$defenderFleetDefeated`, `$IndEvo_debrisFieldList` | `ArtilleryStationScript.java:62-63, 236, 476-478` |
| Memory, station | `$ArtilleryStation`, `$IndEvo_ArtilleryStation`, `$hasDefenders`, `$defenderFleet` (10 days after a lost fight) | `ArtilleryStationEntityPlugin.java:98`, `IndEvo_SalvageDefenderInteraction.java:126` |
| Memory, fleets | `$IndEvo_WasSeenByOtherEntity` (seen), `$IndEvo_ForcedTarget`, `$IndEvo_FORCE` (forced target expiry), `$ArtyReaction_<id>`, `$RailReaction_<id>` | `EyeIndicatorScript.java:20`, `CampaignAttackScript.java:37-38`, `MortarProjectileEntityPlugin.java:151`, `RailgunProjectileEntityPlugin.java:118` |
| Memory, nests and spotters | `$missile_bonked` (read only, owned by [Consumables](../items/CONSUMABLES.md)) | `DroneNetScript.popBonked` |
| Memory, watchtower | `$IndEvo_isArtilleryActive` (AI artillery present, blocks hack/control in rules) | `WatchtowerEntityPlugin.java:35` |
| Memory, system | `$IndEvo_SystemDisableWatchtowers` (read, never written), `$IndEvo_artilleryVision` (vision type placed in the system, `ArtilleryStationPlacer.VisionType` name), `$IndEvo_droneNet` (the `DroneNetScript` instance) | `Ids.java:36-37`, `DroneNetScript.MEM_KEY` |
| Memory, sector | `$IndEvo_placedArtilleries`, `$IndEvo_placedDerelictArtilleries` (run-once guards), `$IndEvo_EyeScript` | `ArtilleryStationPlacer.java:42, 66`, `EyeIndicatorScript.java:19` |
| Settings | `IndEvo_Enable_Artillery`, `IndEvo_Artillery_spawnWeight`, `IndEvo_Artillery_starsPerStationInConstellation`, `IndEvo_Watchtower_faction_reset`, `IndEvo_Artillery_min/maxDelayBetweenShots`, `IndEvo_Artillery_min/maxCooldown`, `IndEvo_Artillery_cooldownNPCMult`, `IndEvo_Artillery_min/maxRange`, `IndEvo_Artillery_WatchtowerRange`, `IndEvo_Artillery_visionWeight_watchtower/_lighthouse/_drones`, `IndEvo_Artillery_lighthouse_beamLength/_beamWidth/_turnTime`, `IndEvo_Artillery_drone_coneLength`, `IndEvo_Artillery_defense_FP2`, `IndEvo_Artillery_mortar_*`, `_railgun_*`, `_missile_*` | `data/config/LunaSettings.csv:6`, `:15`, `:61-91`, constants in `indevo/config/Setting.java:48-79`. See [Settings](../core/SETTINGS.md) |
| Rules | `IndEvo_ArtilleryInitialStation`, `IndEvo_ArtilleryCombatInteraction`, `IndEvo_ArtilleryBeatPlanet`, `IndEvo_ArtilleryBeatStation`, `IndEvo_ArtilleryDefeatPopulateOptions`, `IndEvo_ArtilleryInitialPlanet_CircumventionProtection`, `_NexEventDisabler`, `_NexOutpostDisabler`, `_RuinDisabler`; watchtower `IndEvo_derelictWatchtower_notFunctional`, `cob_hackPrompt_watchtower`, `IndEvo_derelictWatchtower_disableControlUnderAI`; drone nest `IndEvo_droneNest_open`, `_fire`, `_salvage`, `_leave` | `data/campaign/rules.csv:362-427`. Command package `indevo.industries.artillery.rules` in `settings.json:37` |
| Drop group | `IndEvo_droneNest` (Relic Components only) | `data/campaign/procgen/drop_groups.csv:43` |
| Strings | descriptions `IndEvo_Watchtower`, `IndEvo_DroneNest`, `IndEvo_artilleryStation`, `IndEvo_Artillery_<type>`, weapon and hull descriptions, `IndEvo_slowfield` | `data/strings/descriptions.csv` |
| Sprites | `fx/IndEvo_eye_0..3`, `IndEvo_target`, `IndEvo_line`, `IndEvo_danger_notif`, `IndEvo_missile_target`, `IndEvo_carrier_missile`, `IndEvo_sub_missile*`, `IndEvo_trail_foggy`, `IndEvo_stream_core`; `IndEvo/invisibleHull` | `data/config/settings.json` graphics block |

## Registration and lifecycle

| Hook | What |
|---|---|
| `ModPlugin.onNewGameAfterEconomyLoad` (`ModPlugin.java:292-293`) | `ArtilleryStationPlacer.placeDerelictArtilleries()`, then `placeCoreWorldArtilleries()`. Both guarded by sector memory, both skip if `IndEvo_Enable_Artillery` is off |
| `ModPlugin.onGameLoad` (`ModPlugin.java:170`) | `ArtilleryStationReplacer.register()` if `newGame` (transient) |
| `ModPlugin.setListenersIfNeeded` (`ModPlugin.java:459`) | `WatchtowerFactionResetListener.register()` (persistent listener, class-deduped) |
| `ModPlugin.setScriptsIfNeeded` (`ModPlugin.java:505`) | `EyeIndicatorScript.register()` (persistent sector script, guarded by `$IndEvo_EyeScript`) |
| `ArtilleryStationPlacer.placeDroneNet` | `DroneNetScript.getInstanceOrRegister(system, ..)` (persistent system script via `system.addScript`, guarded by `$IndEvo_droneNet`) |
| First `reportIncoming` or `reportShot` after a load | `ArtilleryFireRenderer.getInstanceOrRegister()` (transient LunaLib renderer, one per session, found again with `LunaCampaignRenderer.getRendererOfClass`) |
| PatchLib | `WatchtowerStableLocationPatch`, found by PatchLib in the jar, no registration |
| `ModPlugin.pickMissileAI` (`ModPlugin.java:373`) | Combat AI for mortar and missile projectiles |
| CSV | industry plugin column, condition plugin, entity `pluginClass`, terrain `plugin`, hullmod class |

Derelict placement chain: `placeDerelictArtilleries -> addArtilleryToPlanet(planet, false) -> new ArtilleryStationScript(market)` added to the planet and stored under `$IndEvo_ArtilleryStationScript` `-> placeVision` (once per system, see [Vision types](#vision-types-and-lighthouses)) `-> addCondition(IndEvo_ArtilleryStationCondition)` (`ArtilleryStationPlacer.addArtilleryToPlanet`).

Core worlds (`ArtilleryStationPlacer.java:38-63`, only if `culann` exists): `eochu_bres` railgun with alpha core, `chicomoztoc` mortar, `kazeron` missile with gamma core, `sindria` missile. Each gets `addArtilleryToPlanet(.., true)` (script starts destroyed) plus the industry.

Industry chain: `ArtilleryStation.advance -> spawnStation` (first frame with no entity) `-> ArtilleryStationEntityPlugin.placeAtMarket(market, type, true) -> init -> entity.addScript(new CampaignAttackScript(entity, type))`.

## Flow

### Two owners for one station

A planet with artillery has one `ArtilleryStationScript` for its whole life. While the market is condition-only it runs `aliveActions` and owns the entity and fleet (`ArtilleryStationScript.java:203-231`). As soon as the market is a real colony, `aliveActions` calls `setDestroyedWithDestroyedActions` and from then on `destroyedActions` runs (`:209-211, 120-145`):

- market has an artillery industry or one in the construction queue (`marketHasArtilleryIndustry`, id match `contains("artillery")` on the lower-cased queue id): clear destroyed flag, remove broken station. The `ArtilleryStation` industry owns a new entity and fleet.
- otherwise: mark condition destroyed, spawn `IndEvo_DestroyedArtilleryStation` (matched to the orbital station orbit, opposite side), disable the plugin, drop the commander.

`ArtilleryStation.isFunctional` also needs another functional industry tagged `station` (`ArtilleryStation.java:84-96`); `isAvailableToBuild` needs the condition, a functional spaceport, an orbital station and `IndEvo_Enable_Artillery` (`:584-597`). `getType()` is the industry id minus `IndEvo_Artillery_` (`:329`).

The tooltip is vanilla `createTooltip`. `addRightAfterDescriptionSection` adds "Missing an orbital station to function!" in every mode but `QUEUED` while no functional station is on the market (`:148-155`); vanilla's own "Currently under construction" line also shows then, because `isFunctional` is false. The CR, ground defense and range lines come from `addPostDemandSection`.

### Station fleet

Both owners build the same station: `FleetFactoryV3` empty patrol fleet, `setStationMode(true)`, hidden, AI null, transponder on, orbiting the entity, one member from the spec JSON `variant` (`ArtilleryStation.java:278-398`, `ArtilleryStationScript.java:421-571`). Not functional or destroyed means "skeleton mode": modules without `vastbulk` are perma-detached and the fleet sits in `STATION_BASE_FLEET` instead of `STATION_FLEET`. Industry CR is `0.5 + 0.5 * min(supply fraction, ship quality)` from metals and heavy machinery demand (`ArtilleryStation.java:131-155`); derelict CR is 1. Alpha core gives a remnant AI commander, otherwise a `tier1StationOfficerLevel` officer.

Station destroyed in battle (`reportFleetDespawnedToListener`): the industry disrupts itself for half its build time and aborts the despawn (`ArtilleryStation.java:466-481`); the derelict script goes destroyed, spawns the broken station and moves the newest battle debris field onto it (`ArtilleryStationScript.java:619-644`).

### Derelict faction tracking

`ArtilleryStationScript.updateFaction` (`:147-187`): the station takes the faction of the first non-player market in the system (skipping `epta_tradebase_market`), else the first non-player station fleet; with neither and the player out of system it reverts to `IndEvo_derelict`. Non-player watchtowers follow. `$IndEvo_isArtilleryActive` on watchtowers is true while the station is derelict or remnant.

### Firing

`CampaignAttackScript.advance` (`:96-114`), only in the player's location. While `IndEvo_Enable_Artillery` is off it removes the range terrain and returns, so placed stations do not fire; the script stays attached and resumes when the setting is back on. Otherwise: add range terrain once, sync terrain range, then if not `disabled`: advance station interval, blocked areas and per-target cooldowns, `updateTargets`, `fireAtTargets`.

- Valid target (`isValid`, `:227-241`): hostile (`isHostileTo`; a null faction is hostile to all but remnants), alive, has AI or is the player, `MIN_RANGE <= dist <= getRange()`, `$IndEvo_WasSeenByOtherEntity` set, not `ENTITY_MISSION_IMPORTANT`.
- Per-target cooldown `IndEvo_Artillery_min/maxCooldown`, times `IndEvo_Artillery_cooldownNPCMult` for NPCs; one station-wide delay `min/maxDelayBetweenShots` between shots at different targets. `advanceStationFireInterval` rebuilds the interval when either delay setting changes (`:375-377`), which restarts the current wait.
- Skips: fleet in battle (retry in 10 s), mortar target area blocked by an earlier mortar volley within 400 su, safe spot: fleet or predicted position within `primary radius + 700` of any market primary entity (`isInSafeSpot`, `:335-362`).
- Forced targets (`forceTarget`, `:140-152`) bypass `isValid` and are cleared after one shot. Called by the decoy mine and the ancient remote ([Consumables](../items/CONSUMABLES.md)) and every 5 s on the player in dev mode (`:294-297`).
- Range: `getRange()` returns `IndEvo_Artillery_maxRange` plus `rangeBonus` (`:432-442`). `rangeBonus` is `IMPROVE_RANGE_BONUS` 4000 while the industry is improved; `applyImproveModifiers` resets and re-adds it on every industry `apply` (`ArtilleryStation.java:536-550`). The range terrain follows a changed range on the next frame (`matchTerrainRangeAndLoc`).

Volleys:

- Mortar: `MortarShotScript -> MortarProjectileEntityPlugin` x `IndEvo_Artillery_mortar_projectilesPerShot`, random points in a 250 to 700 su circle around the position predicted at impact time. NPC fleets near the reticule get `ArtilleryReactionScript -> ModularFleetAIAPI.getNavModule().avoidLocation`.
- Railgun: `RailgunShotEntity -> RailgunProjectileEntityPlugin` x `IndEvo_Artillery_railgun_projectilesPerShot`, alternating lead fuzz `IndEvo_Artillery_railgun_shotFuzzMult`. Explodes on any fleet within 50 su after 30% of flight time.
- Missile: `MissileShotScript` spawns an even number of `MissileCarrierEntityPlugin` on three-point arcs (`TrigHelper`, see [Utils](../core/UTILS.md)); a carrier launches at `impactSeconds - flight time`, but never before 0.3 to 0.7 s (`MIN_LAUNCH_DELAY`, `LAUNCH_DELAY_SPREAD`), so the station wind-up always shows and a long flight lands later; at 30% of the remaining distance each splits into 3 `MissileSubmunitionEntity` that home on hostiles within 600 su; each ends in `ECMExplosion` (interdict like the interdiction mine, plus `IndEvo_slowfield` terrain for `IndEvo_Artillery_missile_duration` seconds).

Mortar and railgun explosions use `ExplosionFleetDamage.LOW` (`MortarProjectileEntityPlugin.java:264`, `RailgunProjectileEntityPlugin.java:272`).

### Fire animation

Drawing only: no effect on hits, timing or the save. The station sprite never moves or turns; every position is the station's location plus an offset along the shot's angle.

Each projectile entity waits at the station until its own launch time (`projectileDelayTime`), so a volley leaves shot by shot. While it waits it calls `ArtilleryFireRenderer.reportIncoming(station, type, angle, secondsToShot)` every frame, and `reportShot(station, type, angle)` once, next to its fire sound (`MortarProjectileEntityPlugin.advance`, `RailgunProjectileEntityPlugin.advance`, `MissileCarrierEntityPlugin.advance`). Both return when the station is not in the player's location. The renderer keeps one `StationFireEffect` per station, advances it while the game is not paused, drops it when it is idle and draws it in `TERRAIN_7` (above the station, below fleets) with the station's sensor fade.

| Phase | What `StationFireEffect` draws |
|---|---|
| Wind-up | Glow at the muzzle over the last 0.5 s (mortar), 1 s (railgun) or 0.3 s (missile) before a shot (`incoming`) |
| Shot (`shot`) | Flash for 0.13 s (mortar star: one long streak forward, two short ones 54 degrees to the sides, a thin cross flare and a light on the station; railgun: a short faint lance and 22 sparks that fly 30 to 160 su; missile glow) and smoke out of a rear port and two side ports; the missile station puffs at the tube of each missile |
| Heat | Orange glow and rim on the station. Each shot adds `max / shots per volley` (max 1, missile 0.6) |
| Vent | Starts 0.45 s after the last shot once no projectile is waiting, a new shot stops it. Runs for 60% of `IndEvo_Artillery_minCooldown` (at least 4 s, 9 s by default) and cools the glow to 0. Mortar: 5 steam puffs out of the three ports, the glow drops a step with each. Railgun: two fast streams out the back for 2 s that take 75% of the glow. Missile: slow discharge all around the station that turns as it drifts out |

Smoke is the vanilla `nebula_particles` sheet in normal blend, glows are `fusion_lamp_glow`, sparks and the lance are `IndEvo_line`, the mortar streaks and the rim are plain GL. All sizes, counts and times are constants in `StationFireEffect`.

### Seen state and watchtowers

`EyeIndicatorScript.advance` (`:46-138`, every 0.5 s, only in systems tagged `IndEvo_SystemHasArtillery` with a station hostile to the player and `IndEvo_Enable_Artillery` on): any NPC fleet visible to a hostile AI fleet gets seen for 5 days; the player seen by a hostile fleet locks instantly. Range sources fill the eye: every source reports the share of its range the player is inside (`1 - dist / range`, a hostile unhacked watchtower with `IndEvo_Artillery_WatchtowerRange`, the drone net through `DroneNetScript.getPlayerFraction()`), the best share counts and the eye gains `1 + share` per second up to 20, so a lock takes 10 to 20 s inside a source; at full the player gets `$IndEvo_WasSeenByOtherEntity` for 0.5 days, renewed while full. Leaving range drains it; at 0 the flag is cleared. A player jump empties the eye and clears the flag (`reportFleetJumped`).

`WatchtowerEntityPlugin.advance` (`:91-122`): about once per 20 s in the player's location marks hostile NPC fleets within `IndEvo_Artillery_WatchtowerRange` as seen for 5 days and shows a range ping. Hacking (vanilla objective hack, rules row `cob_hackPrompt_watchtower`) lasts 30 to 60 days and resets the eye.

Watchtowers are placed per system at each jump point, each gate, one gap per gas giant and one gap in the first star-centred ring (`ArtilleryStationPlacer.placeTowers`), discoverable if the system has no markets. When a watchtower changes hands (`reportObjectiveChangedHands`), after `IndEvo_Watchtower_faction_reset` days it reverts to the nearest artillery faction if that is derelict or remnant (`WatchtowerFactionResetListener.java:32-79`).

### Vision types and lighthouses

Three types: watchtowers, lighthouses and the [drone net](#drone-net). `ArtilleryStationPlacer.placeVision(system, factionId)` runs once per artillery system and stores the pick in `$IndEvo_artilleryVision`. A system that already has the key or a watchtower is left alone. Derelict systems (`IndEvo_derelict`) roll a `VisionType` by the `IndEvo_Artillery_visionWeight_*` settings; all weights 0 gives watchtowers. Core world artillery always gets watchtowers.

Lighthouses (`VisionType.LIGHTHOUSE`):

- Placement: `placeLighthouses` uses the watchtower spots, then adds lighthouses on circular orbits around the system centre until there are `MIN_LIGHTHOUSES_PER_SYSTEM` (4), spread between 1500 su outside the star and the outermost planet or jump point (capped at 16000 su).
- Entity: `IndEvo_Lighthouse` carries the `objective` and `IndEvo_watchtower` tags and `LighthouseEntityPlugin extends WatchtowerEntityPlugin`, so hacking, the rules rows, `WatchtowerFactionResetListener` and `WatchtowerStableLocationPatch` treat it as a watchtower. It reuses the `IndEvo_Watchtower` description.
- Lit state (`LighthouseEntityPlugin.shouldBeLit`, checked every 0.5 s in the player's location): `IndEvo_Enable_Artillery` on, functional, not hacked, faction hostile to the player, no player owned market in the system, and a working artillery station hostile to the player in the system (`ArtilleryStationEntityPlugin.hasWorkingArtilleryHostileToPlayer`: not disrupted, so a beaten derelict station turns the beams off).
- Lit: only the beams see. The player inside a beam is the instant source in `EyeIndicatorScript` (full and locked like a hostile fleet sighting, then the normal drain). Hostile NPC fleets inside a beam are marked seen for 5 days (`markFleetsInBeam`). The watchtower range fill and range ping are skipped.
- Unlit: the beams are hidden and see nothing, and the lighthouse runs the plain watchtower code (`super.advanceVision`), so a captured lighthouse still spots for the owner's artillery by range.
- Beam: `LighthouseBeamTerrain` is created by the plugin on its first frame in the player's location and sits on the lighthouse. Each lighthouse rolls 1 or 2 beams once (`LighthouseEntityPlugin.beamCount`, the second at +180 degrees) and hands the count to its terrain. `IndEvo_Artillery_lighthouse_beamLength` (6000 su), `_beamWidth` (20 degrees), `_turnTime` (60 s per turn) are read at use time. Beam count, start angle and turn direction are saved.
- Drawing: one vanilla `PulsarRenderer` per beam with `getPulsarScrollSpeed()` 0 (no moving clouds) and no `applyEffect` (no push), texture `terrain/pulsar`, warm white tinted 35% to the faction colour. Brightness fades over 1 s on lit changes. `renderOnMap` draws each beam as a fading wedge on the system map.
- Shadows: a `RangeBlockerUtil` as the pulsar uses; planets cut the beam short for drawing and for `sees`. A chaff cloud (`SmokeCloudTerrain`) between the lighthouse and a fleet, or around the fleet, also blocks `sees` (`VisionBlockers.collectSmoke`); the drawn beam does not show that shadow. Go Dark has no effect on beams. The body the lighthouse orbits is excluded. The blocker is mandatory: `PulsarRenderer` builds its fade out strip from the blocker data and draws it at the centre without one.
- `sees(point, radius, checkSeconds)` widens the arc by the sweep during one check interval, so a fast or thin beam does not skip a fleet between two 0.5 s checks.

### Drone net

`VisionType.DRONES`: no towers, detection only. `ArtilleryStationPlacer.placeDroneNet` registers one `DroneNetScript` on the system and adds 3 to 6 nests (one per 4000 su of system extent) on circular orbits around the system centre, spread like the extra lighthouses. Nests launch spotter drones, spotters and nests see, the artillery fires. Nothing in the net fights or chases.

- Nest: entity `IndEvo_DroneNest` (no plugin, watchtower placeholder sprite, discoverable in systems without markets). Always on until destroyed. Keeps `SPOTTERS_PER_NEST` (2) spotters in the air and launches a replacement `RELAUNCH_DAYS` (3 days, 30 s of flight) after one is lost.
- Nest zone: `NEST_ZONE_RADIUS` (300 su) around every nest works like a watchtower range: the player inside reports `1 - dist / 300` to the eye, hostile NPC fleets inside are marked seen for 5 days.
- Spotter: entity `IndEvo_SpotterDrone` (no plugin, not interactable, vanilla derelict picket sprite as placeholder), no orbit. `DroneNetScript.move` sets its location and facing every frame in the player's location: burn 14 (280 su/s), 240 degrees per second turn, slowing inside 200 su of its goal, a thin trail. It roams between random points within `ROAM_RADIUS` (4000 su) of its nest. Spotters of a destroyed nest fly on and are not replaced.
- Cone: 50 degrees wide, `IndEvo_Artillery_drone_coneLength` (2000 su) times the player's detected range modifier clamped to 0.5 to 1.5, so Go Dark shortens every cone and sustained burn lengthens it. Roaming it points along the heading with a 30 degree sweep to each side.
- Seeing the player (`look`, every 0.25 s): inside the cone with a free line of sight. The spotter then follows: it keeps the cone on the player and holds 45% of the cone length away, backing off when approached. It reports `1 - dist / coneLength` to the eye, so the eye fills as under a watchtower; there is no instant lock. After 3 s without sight it flies to the last seen spot and roams again.
- Line of sight (`VisionBlockers`): a planet or star lights up to its middle and shadows everything behind it; a chaff cloud (`SmokeCloudTerrain`) stops the ray where it enters, so a fleet inside or behind one is not seen. Used for cones and nest zones, for the player and for NPC fleets.
- NPC fleets: a fleet hostile to the net's faction inside a cone or nest zone is marked seen for 5 days.
- Decoy: a spotter within `DecoyMineAbility.BASE_RANGE` (3000 su) of an `IndEvo_decoy_mine` flies to it and keeps its cone on it while the decoy exists, whatever else it sees.
- Gate: the net only runs in the player's location. It sees only while `ArtilleryStationEntityPlugin.needsVisionAgainstPlayer` holds (`IndEvo_Enable_Artillery` on, no player owned market in the system, a working artillery station hostile to the player) and `$IndEvo_SystemDisableWatchtowers` is unset. Otherwise cones and zones fade out and nothing is seen; the spotters keep roaming.
- Spotter explosions: a spotter explodes when it carries `$missile_bonked` (set by every IE campaign missile near its path or blast, shared with Catch.Release, never rename), or when `DroneNetScript.reportBlast(location, point, radius)` covers it: the interdiction mine (`InterdictionMineAbility.interdictFleetsInRange`), mortar and railgun shell blasts and the missile artillery's `ECMExplosion`. A vanilla interdiction pulse within `InterdictionPulseAbility.getRange` of the pulsing fleet does the same. An exploding spotter lights every spotter within `CHAIN_RADIUS` (400 su) after `CHAIN_DELAY` (0.2 s). Harmless `IndEvo_VariableExplosion`, no loot.
- Nest destruction: a nest carrying `$missile_bonked` explodes and leaves a debris field (150 su, 30 days, on the nest's orbit) holding 6 to 10 Relic Components from drop group `IndEvo_droneNest` (`DroneNetScript.createNestDrop`, shared with the dialog loot; vanilla salvage multipliers apply, easy mode gives 1.5x). Interdicts, shells and the chain do not hurt nests.
- Nest dialog (`rules.csv` `IndEvo_droneNest_open`, condition `$tag:IndEvo_droneNest`; the spec tag `has_interaction_dialog` makes vanilla open the rules dialog for an entity without a plugin): "Open fire on the nest" runs `IndEvo_DroneNest destroy`, then "Salvage the wreckage" opens the loot screen with the same drop roll and closes the dialog, "Leave" runs `IndEvo_DroneNest debris` and leaves the debris field instead.
- Player colony: with a player owned market in the system `convertToWatchtowers` removes every nest and spotter, sets `$IndEvo_artilleryVision` to `WATCHTOWER`, places watchtowers with `ArtilleryStationPlacer.placeTowers` and ends the script.
- Polling: the vanilla interdiction pulse has no listener and sets nothing on its targets, only the 0.1 day flag on the pulsing fleet (`InterdictionPulseAbility.java:230`), so the script scans the fleets in the player's location every 0.25 s. Decoys carry no tag and add no listener; the script scans the system's custom entities in the same check.
- Drawing: `DroneConeTerrain` reads the script from `$IndEvo_droneNet` and draws one additive triangle fan with an outline per spotter, amber, pulsing red while it sees the player. Every ray of the fan ends where `VisionBlockers.getRayLength` ends it, so the drawn shadow is the checked shadow. Nest zones of discovered nests are drawn as rings. `renderOnMap` draws the same on the system map. Cones are drawn for every spotter in the system.
- The script ends itself when no nest and no spotter is left.

### Defender fight

`rules.csv` `IndEvo_ArtilleryInitialStation` (station, condition-only market, not defeated) -> option `IndEvo_ArtilleryCombatInteraction -> IndEvo_SalvageDefenderInteraction` (`:27`). Defenders: existing `$defenderFleet` on the station or `createDefenderFleet` (`IndEvo_ArtilleryDefenderGen.java:81-109`): `PATROL_LARGE`, `IndEvo_Artillery_defense_FP2 * (1 + min(1, (200 - hazard) / 100))` FP, officers x1.5, seeded from the planet salvage seed, plus the station variant. Win: station fleet members removed with flash, script goes destroyed, `$defenderFleetDefeated`, `FireBest BeatDefendersContinue` (`:83-100`). Loss with damage: defenders persist 10 days via `FleetAdvanceScript`. Salvage adds AI cores per destroyed hull size. The `PopulateOptions` rows disable survey, salvage and Nexerelin outpost/deciv options while undefeated.

### Stable locations

Watchtowers carry the `objective` tag (`data/config/custom_entities.json`, entity `IndEvo_Watchtower`) so the comm relay style capture rules apply, but they sit at jump points, gates and gas giant orbits, never on a stable location. Vanilla `Misc.getNumStableLocations` counts every objective (`api/util/Misc.java:5278`), so each watchtower would block one "create stable location" use at the star and inflate the stable location count in the planet search and the intel planet list. `WatchtowerStableLocationPatch` subtracts the system's watchtowers from the result for every caller, vanilla and other mods. Systems without watchtowers are unchanged.

## Contracts

- Off-screen: `CampaignAttackScript`, `ArtilleryStationScript`, `DroneNetScript` and watchtower logic return early outside the player's location, so the off-screen 60-frame throttle (`docs/concepts/campaign-frame.md`) does not apply. Artillery never fires off-screen.
- Tuning settings are read at use time and apply to placed stations: delay between shots, reload, range, shot counts, impact times, fuzz, explosion sizes, watchtower range and defender FP apply to the next shot or fight. The improve bonus is `CampaignAttackScript.rangeBonus`, reset and set again on every industry apply (`ArtilleryStation.java:544-549`).
- `WatchtowerStableLocationPatch` targets `Misc` by API names only (stable). It needs PatchLib (hard dependency); without the patch watchtowers count as stable locations again.
- Save data: `ArtilleryStationScript` (planet script and memory), `CampaignAttackScript` (entity script, target maps keyed by entity id), the industry's `stationFleet`/`stationEntity` fields, `WatchtowerFactionResetListener.watchtowerTimeoutMap`, `EyeIndicatorScript`, each `LighthouseEntityPlugin` (beam reference, lit flag, beam count) and `LighthouseBeamTerrain` (angle, direction, lit flag, beam count), each `DroneNetScript` (system script and `$IndEvo_droneNet`: nest and spotter entities, per spotter movement state) all persist. The fire animation is not saved: `ArtilleryFireRenderer` is a transient renderer and a load drops heat and smoke. Vision placement is new game only; a save from before lighthouses and drone nets keeps its watchtowers. Projectile entities persist while alive.
- The industry and the derelict script must agree on the type: the script stores it in `$IndEvo_ArtilleryType`; the industry derives it from its id. The rebuild options come from the parent `IndEvo_Artillery_base` (`parent_item`).
- `ArtilleryStationScript.getScript(entity)` assumes a non-planet argument orbits the planet (`:79-89`).
- `$IndEvo_ArtilleryStation` is also set as a tag on defender fleets (`IndEvo_ArtilleryDefenderGen.java:107`); `ConsumableItemDropListener` checks that tag (`indevo/items/consumables/listeners/ConsumableItemDropListener.java:48`).
- `RuinsManager.setUpgradeSpec` excludes the rift generator on artillery planets ([Derelicts](DERELICTS.md)).
- `MagicInvisibator` sets alpha 0 and applies 1e6 damage every frame to the deco module so it never fights (`MagicInvisibator.java:20-38`).

## Known issues and dormant code

| Item | State |
|---|---|
| `ArtilleryStationReplacer` | Matches `ind.getId().contains("artillery")` but ids are `IndEvo_Artillery_*` (capital A), so it never replaces anything (`ArtilleryStationReplacer.java:36`) |
| `FleetBasedArtilleryStationPlacer`, `ArtilleryStationPlacer.ArtilleryStationInteractionConfigGen` | Dead. Only caller of `addTestArtilleryToPlanet` is commented out (`ArtilleryStationPlacer.java:119`) |
| `WatchtowerTerrain` | Dead, not in `terrain.json` |
| `RenderTemporaryIndicator`, entity `IndEvo_tempIndicator` | Debug only, callers commented out |
| `MissileSubmunitionEntity.advanceProjectileTowardsEnemy` | `@Deprecated`, unused |
| `$IndEvo_SystemDisableWatchtowers` | Read in `WatchtowerEntityPlugin`, `EyeIndicatorScript` and `DroneNetScript`, never set |
| `IndEvo_artilleryStation_mortarModule` hull, sprite `IndEvo_eye_0` | No user |
| `Changelog_todo` | "Artillery station needs text to clarify colonization block when non-hostile"; "Artillery can pull IBB fleets into combat"; "Allow gate hauler to move arty"; "Eye-Bots with vision cones & missile interactions"; "Watchtowers buildable in stable loc"; "Make artillery follow some kinda stealth system"; "add roaming derelict fleets to uninhabited arty systems" |

Suspected bugs (not fixed): see the return report of the documentation pass; listed here for upkeep.

- `RailgunProjectileEntityPlugin.advance` adds `amount` to `timePassedSeconds` twice (`:126, 135`), so the wait, line fade and the failsafe run at double speed. The wind-up report halves the remaining wait for it (`:161`); remove that with the fix.
- Railgun shells explode on any fleet, friendly included (`RailgunProjectileEntityPlugin.java:185-194`); carriers check hostility.
- `RailgunShotEntity` never expires; the danger-sign entity stays in the location at alpha 0.
- `MissileCarrierEntityPlugin` keeps looping after `finishing` and can spawn several ECM explosions in one frame (`:159-169`).
- `MissileCarrierEntityPlugin` uses `fleet.isHostileTo(origin)` with the station as origin; inferred fine, not checked in game.

## Maintenance

Update this doc when artillery placement, targeting rules, projectile types, the fire animation, ids, settings or the defender fight change. Combat weapon scripts belong in [Combat](../core/COMBAT.md), consumables that interact with artillery in [Consumables](../items/CONSUMABLES.md), the setting mechanism in [Settings](../core/SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
