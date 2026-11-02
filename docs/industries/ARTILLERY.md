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
| `indevo/industries/artillery/entities/WatchtowerEyeIndicator.java` | Custom entity `IndEvo_Eye` that draws the eye above the player fleet |
| `indevo/industries/artillery/entities/VariableExplosionEntityPlugin.java` | `ExplosionEntityPlugin` subclass with sound, size and blastwave params. Shared: also used by consumables and the crucible |
| `indevo/industries/artillery/projectiles/MortarShotScript.java`, `MortarProjectileEntityPlugin.java` | Mortar volley: N reticules around the predicted target position, shells fly at 1500 su/s, explode with `VariableExplosion`. Holds `ArtilleryReactionScript` (NPC avoidance) |
| `indevo/industries/artillery/projectiles/RailgunShotEntity.java`, `RailgunProjectileEntityPlugin.java` | Railgun volley: danger sign plus N lead-predicted shots at 6000 su/s with a target line |
| `indevo/industries/artillery/projectiles/MissileShotScript.java`, `MissileCarrierEntityPlugin.java`, `MissileSubmunitionEntity.java`, `ECMExplosion.java` | Missile volley: curved carriers split into 3 homing submunitions; each submunition or a direct hit spawns an `ECMExplosion` (interdict plus slow field terrain) |
| `indevo/industries/artillery/projectiles/RenderTemporaryIndicator.java` | Debug marker entity. Only used from commented code |
| `indevo/industries/artillery/terrain/ArtilleryTerrain.java` | Ring terrain `IndEvo_artillery_range_terrain`: range circle, name, tooltip, safe-spot display |
| `indevo/industries/artillery/terrain/SlowFieldTerrain.java` | Ring terrain `IndEvo_slowfield`: burn penalty via `Misc.getBurnMultForTerrain` |
| `indevo/industries/artillery/terrain/WatchtowerTerrain.java` | Copy of `ArtilleryTerrain`, not in `terrain.json`. Dead |
| `indevo/industries/artillery/listener/WatchtowerFactionResetListener.java` | `ObjectiveEventListener` and `NewDayListener`. Hands a captured watchtower back to the nearest AI artillery faction after a timeout |
| `indevo/industries/artillery/patches/WatchtowerStableLocationPatch.java` | PatchLib `@After` on `Misc.getNumStableLocations`: watchtowers no longer count as stable locations |
| `indevo/industries/artillery/rules/IndEvo_SalvageDefenderInteraction.java` | Rule command. Runs the defender fight (`FleetInteractionDialogPluginImpl`) and the win/loss bookkeeping |
| `indevo/industries/artillery/rules/IndEvo_ArtilleryDefenderGen.java` | Helper (not a command): builds or reuses the defender fleet plus the station hull |
| `indevo/industries/artillery/utils/ArtilleryStationPlacer.java` | New-game placement (derelict and core worlds), `addArtilleryToPlanet`, watchtower placement, `ArtilleryStationInteractionConfigGen` |
| `indevo/industries/artillery/utils/FleetBasedArtilleryStationPlacer.java` | Older fleet-as-station variant. Dead |
| `indevo/industries/artillery/hullmods/MagicInvisibator.java` | Hullmod `IndEvo_magicInvisibator`: hides the deco module ship and kills it on the first combat frame |

Combat side, linked only: `data/scripts/weapons/IndEvo_targetingBeamEffect.java` (railgun `everyFrameEffect`), `IndEvo_mortarProjectileOnFireEffect.java`, `IndEvo_missileProjectileOnFireEffect.java`, `ai/IndEvo_mortarProjectileAI.java`, `ai/IndEvo_missileProjectileAI.java`, `combatEntities/IndEvo_CombatSlowFieldTerrain.java`, `combatEntities/IndEvo_ForcedOverload.java`. The two AIs are picked in `ModPlugin.pickMissileAI` (`indevo/utils/ModPlugin.java:375`). See [Combat](../core/COMBAT.md).

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Artillery_base` (parent), `IndEvo_Artillery_mortar`, `_missile`, `_railgun` | `data/campaign/industries.csv:64-85`. `data` column is JSON: `radius`, `variant`, `fleetName` |
| Condition | `IndEvo_ArtilleryStationCondition` | `data/campaign/market_conditions.csv:23`; `condition_gen_data.csv:4` (frequency 0, never rolled by procgen; inferred from the column) |
| Custom entities | `IndEvo_ArtilleryStation`, `IndEvo_DestroyedArtilleryStation` (no plugin), `IndEvo_Watchtower`, `IndEvo_Eye`, `IndEvo_artillery_projectile`, `IndEvo_railgun_projectile`, `IndEvo_RailgunShotEntity`, `IndEvo_missileCarrierPlugin`, `IndEvo_missileSubmunition`, `IndEvo_ECMExplosion`, `IndEvo_VariableExplosion`, `IndEvo_tempIndicator` | `data/config/custom_entities.json` |
| Terrain | `IndEvo_artillery_range_terrain`, `IndEvo_slowfield` | `data/campaign/terrain.json:21-28` |
| Hulls | `IndEvo_artilleryStation_base` plus skins `IndEvo_artilleryStation_mortar/_missile/_railgun` (builtin weapon on `WS0005`, `solar_shielding`); modules `IndEvo_artilleryStation_gunModule`, `_hangarModule`, `_mortarModule` (no variant uses it); deco modules `IndEvo_MortarDecoModule`, `IndEvo_MissileDecoModule`, `IndEvo_RailgunDecoModule` (builtin `IndEvo_magicInvisibator`) | `data/hulls/`, `data/hulls/skins/`, `data/hulls/ship_data.csv` |
| Variants | `IndEvo_artilleryStation_<type>_variant` (2 gun, 2 hangar modules, 1 deco module), module and deco variants | `data/variants/` |
| Weapons | `IndEvo_artillery_mortar`, `IndEvo_artillery_railgun`, `IndEvo_artillery_missile` (tags `SYSTEM`) and projectiles `IndEvo_mortar_projectile`, `IndEvo_railgun_projectile`, `IndEvo_missile_projectile` | `data/weapons/`, `data/weapons/proj/`, `weapon_data.csv` |
| Hullmod | `IndEvo_magicInvisibator` (`hide_in_codex`) | `data/hullmods/hull_mods.csv:12` |
| Faction | `IndEvo_derelict` (`Ids.DERELICT_FACTION_ID`) | `indevo/ids/Ids.java:8` |
| Tags | `IndEvo_Artillery` (station entity, market, skin hulls), `IndEvo_Entity_has_artillery` (planet), `IndEvo_ArtilleryFleet` (industry station fleet), `IndEvo_watchtower`, `IndEvo_SystemHasArtillery` (system) | `indevo/ids/Ids.java:27-31` |
| Memory, market | `$IndEvo_ArtilleryType` (type), `$IndEvo_ArtilleryStation_Destroyed` | `ArtilleryStationScript.java:61`, `ArtilleryStationCondition.java:14` |
| Memory, planet | `$IndEvo_ArtilleryStationScript` (script instance), `$IndEvo_ArtilleryStation`, `$hasDefenders`, `$defenderFleetDefeated`, `$IndEvo_debrisFieldList` | `ArtilleryStationScript.java:62-63, 236, 476-478` |
| Memory, station | `$ArtilleryStation`, `$IndEvo_ArtilleryStation`, `$hasDefenders`, `$defenderFleet` (10 days after a lost fight) | `ArtilleryStationEntityPlugin.java:98`, `IndEvo_SalvageDefenderInteraction.java:126` |
| Memory, fleets | `$IndEvo_WasSeenByOtherEntity` (seen), `$IndEvo_ForcedTarget`, `$IndEvo_FORCE` (forced target expiry), `$ArtyReaction_<id>`, `$RailReaction_<id>` | `EyeIndicatorScript.java:20`, `CampaignAttackScript.java:37-38`, `MortarProjectileEntityPlugin.java:151`, `RailgunProjectileEntityPlugin.java:118` |
| Memory, watchtower | `$IndEvo_isArtilleryActive` (AI artillery present, blocks hack/control in rules) | `WatchtowerEntityPlugin.java:35` |
| Memory, system | `$IndEvo_SystemDisableWatchtowers` (read, never written) | `Ids.java:32` |
| Memory, sector | `$IndEvo_placedArtilleries`, `$IndEvo_placedDerelictArtilleries` (run-once guards), `$IndEvo_EyeScript` | `ArtilleryStationPlacer.java:42, 66`, `EyeIndicatorScript.java:19` |
| Settings | `IndEvo_Enable_Artillery`, `IndEvo_Artillery_spawnWeight`, `IndEvo_Artillery_starsPerStationInConstellation`, `IndEvo_Watchtower_faction_reset`, `IndEvo_Artillery_min/maxDelayBetweenShots`, `IndEvo_Artillery_min/maxCooldown`, `IndEvo_Artillery_cooldownNPCMult`, `IndEvo_Artillery_min/maxRange`, `IndEvo_Artillery_WatchtowerRange`, `IndEvo_Artillery_defense_FP2`, `IndEvo_Artillery_mortar_*`, `_railgun_*`, `_missile_*` | `data/config/LunaSettings.csv:8`, `:16`, `:65-88`, constants in `indevo/config/Setting.java:46-70`. See [Settings](../core/SETTINGS.md) |
| Rules | `IndEvo_ArtilleryInitialStation`, `IndEvo_ArtilleryCombatInteraction`, `IndEvo_ArtilleryBeatPlanet`, `IndEvo_ArtilleryBeatStation`, `IndEvo_ArtilleryDefeatPopulateOptions`, `IndEvo_ArtilleryInitialPlanet_CircumventionProtection`, `_NexEventDisabler`, `_NexOutpostDisabler`, `_RuinDisabler`; watchtower `IndEvo_derelictWatchtower_notFunctional`, `cob_hackPrompt_watchtower`, `IndEvo_derelictWatchtower_disableControlUnderAI` | `data/campaign/rules.csv:362-414`. Command package `indevo.industries.artillery.rules` in `settings.json:77` |
| Strings | descriptions `IndEvo_Watchtower`, `IndEvo_artilleryStation`, `IndEvo_Artillery_<type>`, weapon and hull descriptions, `IndEvo_slowfield` | `data/strings/descriptions.csv` |
| Sprites | `fx/IndEvo_eye_0..3`, `IndEvo_target`, `IndEvo_line`, `IndEvo_danger_notif`, `IndEvo_missile_target`, `IndEvo_carrier_missile`, `IndEvo_sub_missile*`, `IndEvo_trail_foggy`, `IndEvo_stream_core`; `IndEvo/invisibleHull` | `data/config/settings.json` graphics block |

## Registration and lifecycle

| Hook | What |
|---|---|
| `ModPlugin.onNewGameAfterEconomyLoad` (`ModPlugin.java:294-295`) | `ArtilleryStationPlacer.placeDerelictArtilleries()`, then `placeCoreWorldArtilleries()`. Both guarded by sector memory, both skip if `IndEvo_Enable_Artillery` is off |
| `ModPlugin.onGameLoad` (`ModPlugin.java:169`) | `ArtilleryStationReplacer.register()` if `newGame` (transient) |
| `ModPlugin.setListenersIfNeeded` (`ModPlugin.java:459`) | `WatchtowerFactionResetListener.register()` (persistent listener, class-deduped) |
| `ModPlugin.setScriptsIfNeeded` (`ModPlugin.java:505`) | `EyeIndicatorScript.register()` (persistent sector script, guarded by `$IndEvo_EyeScript`) |
| PatchLib | `WatchtowerStableLocationPatch`, found by PatchLib in the jar, no registration |
| `ModPlugin.pickMissileAI` (`ModPlugin.java:375`) | Combat AI for mortar and missile projectiles |
| CSV | industry plugin column, condition plugin, entity `pluginClass`, terrain `plugin`, hullmod class |

Derelict placement chain: `placeDerelictArtilleries -> addArtilleryToPlanet(planet, false) -> new ArtilleryStationScript(market)` added to the planet and stored under `$IndEvo_ArtilleryStationScript` `-> placeWatchtowers` (once per system) `-> addCondition(IndEvo_ArtilleryStationCondition)` (`ArtilleryStationPlacer.java:134-157`).

Core worlds (`ArtilleryStationPlacer.java:38-63`, only if `culann` exists): `eochu_bres` railgun with alpha core, `chicomoztoc` mortar, `kazeron` missile with gamma core, `sindria` missile. Each gets `addArtilleryToPlanet(.., true)` (script starts destroyed) plus the industry.

Industry chain: `ArtilleryStation.advance -> spawnStation` (first frame with no entity) `-> ArtilleryStationEntityPlugin.placeAtMarket(market, type, true) -> init -> entity.addScript(new CampaignAttackScript(entity, type))`.

## Flow

### Two owners for one station

A planet with artillery has one `ArtilleryStationScript` for its whole life. While the market is condition-only it runs `aliveActions` and owns the entity and fleet (`ArtilleryStationScript.java:203-231`). As soon as the market is a real colony, `aliveActions` calls `setDestroyedWithDestroyedActions` and from then on `destroyedActions` runs (`:209-211, 120-145`):

- market has an artillery industry or one in the construction queue (`marketHasArtilleryIndustry`, id match `contains("artillery")` on the lower-cased queue id): clear destroyed flag, remove broken station. The `ArtilleryStation` industry owns a new entity and fleet.
- otherwise: mark condition destroyed, spawn `IndEvo_DestroyedArtilleryStation` (matched to the orbital station orbit, opposite side), disable the plugin, drop the commander.

`ArtilleryStation.isFunctional` also needs another functional industry tagged `station` (`ArtilleryStation.java:90-105`); `isAvailableToBuild` needs the condition, a functional spaceport, an orbital station and `IndEvo_Enable_Artillery` (`:596-609`). `getType()` is the industry id minus `IndEvo_Artillery_` (`:329`).

### Station fleet

Both owners build the same station: `FleetFactoryV3` empty patrol fleet, `setStationMode(true)`, hidden, AI null, transponder on, orbiting the entity, one member from the spec JSON `variant` (`ArtilleryStation.java:278-398`, `ArtilleryStationScript.java:421-571`). Not functional or destroyed means "skeleton mode": modules without `vastbulk` are perma-detached and the fleet sits in `STATION_BASE_FLEET` instead of `STATION_FLEET`. Industry CR is `0.5 + 0.5 * min(supply fraction, ship quality)` from metals and heavy machinery demand (`ArtilleryStation.java:140-155`); derelict CR is 1. Alpha core gives a remnant AI commander, otherwise a `tier1StationOfficerLevel` officer.

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
- Missile: `MissileShotScript` spawns an even number of `MissileCarrierEntityPlugin` on three-point arcs (`TrigHelper`, see [Utils](../core/UTILS.md)); at 30% of the remaining distance each splits into 3 `MissileSubmunitionEntity` that home on hostiles within 600 su; each ends in `ECMExplosion` (interdict like the interdiction mine, plus `IndEvo_slowfield` terrain for `IndEvo_Artillery_missile_duration` seconds).

Mortar and railgun explosions use `ExplosionFleetDamage.LOW` (`MortarProjectileEntityPlugin.java:233`, `RailgunProjectileEntityPlugin.java:264`).

### Seen state and watchtowers

`EyeIndicatorScript.advance` (`:46-138`, every 0.5 s, only in systems tagged `IndEvo_SystemHasArtillery` with a station hostile to the player and `IndEvo_Enable_Artillery` on): any NPC fleet visible to a hostile AI fleet gets seen for 5 days; the player seen by a hostile fleet locks instantly. Near a hostile, unhacked watchtower the eye fills over 20 s (faster when closer); at full the player gets `$IndEvo_WasSeenByOtherEntity` for 0.5 days, renewed while full. Leaving range drains it; at 0 the flag is cleared. Jumping clears it (`reportFleetJumped`).

`WatchtowerEntityPlugin.advance` (`:91-122`): about once per 20 s in the player's location marks hostile NPC fleets within `IndEvo_Artillery_WatchtowerRange` as seen for 5 days and shows a range ping. Hacking (vanilla objective hack, rules row `cob_hackPrompt_watchtower`) lasts 30 to 60 days and resets the eye.

Watchtowers are placed per system at each jump point, each gate, one gap per gas giant and one gap in the first star-centred ring (`ArtilleryStationPlacer.java:185-240`), discoverable if the system has no markets. When a watchtower changes hands (`reportObjectiveChangedHands`), after `IndEvo_Watchtower_faction_reset` days it reverts to the nearest artillery faction if that is derelict or remnant (`WatchtowerFactionResetListener.java:32-79`).

### Defender fight

`rules.csv` `IndEvo_ArtilleryInitialStation` (station, condition-only market, not defeated) -> option `IndEvo_ArtilleryCombatInteraction -> IndEvo_SalvageDefenderInteraction` (`:27`). Defenders: existing `$defenderFleet` on the station or `createDefenderFleet` (`IndEvo_ArtilleryDefenderGen.java:81-109`): `PATROL_LARGE`, `IndEvo_Artillery_defense_FP2 * (1 + min(1, (200 - hazard) / 100))` FP, officers x1.5, seeded from the planet salvage seed, plus the station variant. Win: station fleet members removed with flash, script goes destroyed, `$defenderFleetDefeated`, `FireBest BeatDefendersContinue` (`:83-100`). Loss with damage: defenders persist 10 days via `FleetAdvanceScript`. Salvage adds AI cores per destroyed hull size. The `PopulateOptions` rows disable survey, salvage and Nexerelin outpost/deciv options while undefeated.

### Stable locations

Watchtowers carry the `objective` tag (`data/config/custom_entities.json`, entity `IndEvo_Watchtower`) so the comm relay style capture rules apply, but they sit at jump points, gates and gas giant orbits, never on a stable location. Vanilla `Misc.getNumStableLocations` counts every objective (`api/util/Misc.java:5278`), so each watchtower would block one "create stable location" use at the star and inflate the stable location count in the planet search and the intel planet list. `WatchtowerStableLocationPatch` subtracts the system's watchtowers from the result for every caller, vanilla and other mods. Systems without watchtowers are unchanged.

## Contracts

- Off-screen: `CampaignAttackScript`, `ArtilleryStationScript` and watchtower logic return early outside the player's location, so the off-screen 60-frame throttle (`docs/concepts/campaign-frame.md`) does not apply. Artillery never fires off-screen.
- Tuning settings are read at use time and apply to placed stations: delay between shots, reload, range, shot counts, impact times, fuzz, explosion sizes, watchtower range and defender FP apply to the next shot or fight. The saved field `CampaignAttackScript.range` is unused and stays so older saves load. Saves without `rangeBonus` load it as 0; vanilla reapplies all industries on load (`CoreLifecyclePluginImpl.econPostSaveRestore`, skill `api/impl/campaign/CoreLifecyclePluginImpl.java:1306-1318`), which adds the improve bonus back.
- `WatchtowerStableLocationPatch` targets `Misc` by API names only (stable). It needs PatchLib (hard dependency); without the patch watchtowers count as stable locations again.
- Save data: `ArtilleryStationScript` (planet script and memory), `CampaignAttackScript` (entity script, target maps keyed by entity id), the industry's `stationFleet`/`stationEntity` fields, `WatchtowerFactionResetListener.watchtowerTimeoutMap`, `EyeIndicatorScript` all persist. Projectile entities persist while alive.
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
| `$IndEvo_SystemDisableWatchtowers` | Read in `WatchtowerEntityPlugin` and `EyeIndicatorScript`, never set |
| `IndEvo_artilleryStation_mortarModule` hull, sprite `IndEvo_eye_0` | No user |
| `Changelog_todo` | "Artillery station needs text to clarify colonization block when non-hostile"; "Artillery can pull IBB fleets into combat"; "Allow gate hauler to move arty"; "Eye-Bots with vision cones & missile interactions"; "Watchtowers buildable in stable loc"; "Make artillery follow some kinda stealth system"; "add roaming derelict fleets to uninhabited arty systems" |

Suspected bugs (not fixed): see the return report of the documentation pass; listed here for upkeep.

- `RailgunProjectileEntityPlugin.advance` adds `amount` to `timePassedSeconds` twice (`:122, 131`), so line fade and the failsafe run at double speed.
- Railgun shells explode on any fleet, friendly included (`RailgunProjectileEntityPlugin.java:178-186`); carriers check hostility.
- `RailgunShotEntity` never expires; the danger-sign entity stays in the location at alpha 0.
- `EyeIndicatorScript` picks the farthest tower in range (`closestDist = Float.MIN_VALUE`, `dist > closestDist`, `:110-122`), so fill speed uses the wrong distance.
- `MissileCarrierEntityPlugin` keeps looping after `finishing` and can spawn several ECM explosions in one frame (`:149-159`).
- `MissileCarrierEntityPlugin` uses `fleet.isHostileTo(origin)` with the station as origin; inferred fine, not checked in game.

## Maintenance

Update this doc when artillery placement, targeting rules, projectile types, ids, settings or the defender fight change. Combat weapon scripts belong in [Combat](../core/COMBAT.md), consumables that interact with artillery in [Consumables](../items/CONSUMABLES.md), the setting mechanism in [Settings](../core/SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
