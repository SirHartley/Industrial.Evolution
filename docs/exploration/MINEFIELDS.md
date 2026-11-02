# Minefields

Self-replicating minefields around planets: the market condition, the mine belt terrain, the mine entities, impact damage, interdiction pulse disabling and save handling. Read it before touching `indevo/exploration/minefields` or the `IndEvo_Minefield_*` settings. It does not cover the arsenal stations that seed some minefields; see [Stations](STATIONS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

A planet with the condition `IndEvo_mineFieldCondition` spawns a ring terrain `IndEvo_mine_belt` filled with `IndEvo_mine` custom entities. Fleets that are hostile to the controlling market (or the player without transponder) roll for mine hits each frame they are inside; a hit knocks the fleet off course and spawns a damaging explosion. Slow, small fleets pass safely. A player interdiction pulse disables the mines in range for a few seconds. The condition also multiplies ground defense.

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/minefields/conditions/MineFieldCondition.java` | Market condition: ground defense mult, spawns the belt once per market (`addMineField()`), owns the memory keys `PLANET_KEY`, `MINE_FIELD_ID_KEY`, `NO_ADD_BELT_VISUAL` |
| `indevo/exploration/minefields/MineBeltTerrainPlugin.java` | Terrain plugin for `IndEvo_mine_belt`: creates mines, hit roll (`applyEffect`), friend check, AI flag, tooltip, disabled areas. Implements vanilla `AsteroidSource` |
| `indevo/exploration/minefields/MineFieldTerrainPlugin.java` | Terrain plugin for `IndEvo_mine_field` (disc instead of ring). Nothing spawns it (dormant) |
| `indevo/exploration/minefields/MineCustomEntityPlugin.java` | Mine entity render: sprite `IndEvo/mine4`, glow `IndEvo/mineGlow`, glow colour red (hostile), green (friendly player within 300), cyan (disabled) |
| `indevo/exploration/minefields/MineImpact.java` | `EveryFrameScript` on the hit fleet: velocity kick, hit glow, then spawns the explosion after 0.1 s. Copy of vanilla `AsteroidImpact` |
| `indevo/exploration/minefields/MineExplosionEntityPlugin.java` | Extends vanilla `ExplosionEntityPlugin`; scales damage by `baseDamageMult` and always damages the triggering fleet |
| `indevo/exploration/minefields/DisabledArea.java` | Disabled zone: radius, remaining seconds, orbit focus token; list stored in location memory |
| `indevo/exploration/minefields/MineAPI.java` | Interface with rotation getter/setter. Not implemented or used anywhere (dead) |
| `indevo/exploration/minefields/listeners/InterdictionPulseAbilityListener.java` | Transient campaign listener: on player interdiction pulse deactivation, creates a `DisabledArea` in every belt in range |
| `indevo/exploration/minefields/listeners/RecentJumpListener.java` | Transient campaign listener: sets `$IndEvo_recentlyJumped` on any fleet that jumps, so arrivals are not hit |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Condition | `IndEvo_mineFieldCondition` (`Ids.COND_MINERING`) | `data/campaign/market_conditions.csv:22`; `data/campaign/procgen/condition_gen_data.csv:3` (no category weights, inferred: never rolled by procgen) |
| Terrain | `IndEvo_mine_belt`, `IndEvo_mine_field` | `data/campaign/terrain.json:10`, `:15`; descriptions `data/strings/descriptions.csv:68-69` |
| Custom entity | `IndEvo_mine` (tag `IndEvo_Mine`, layer `ASTEROIDS`) | `data/config/custom_entities.json:1006`. The tag is read by `ModPlugin.beforeGameSave`; the file marks it "DO NOT CHANGE" |
| Custom entity | `IndEvo_mine_explosion` | `data/config/custom_entities.json:473` |
| Custom entity | `SplinterFleet_OrbitFocus` (disabled area anchor) | `data/config/custom_entities.json:420`, owned by [Split fleet](../abilities/SPLIT_FLEET.md) |
| Sprites | `IndEvo/mine4`, `IndEvo/mineGlow` | `data/config/settings.json:188-189` |
| Setting | `IndEvo_Enable_minefields` (needs new game) | `LunaSettings.csv:42`; read in `MineBeltTerrainPlugin.createMines/applyEffect`, `ModPlugin.spawnMineFields/restoreRemovedEntities`, `DerelictStationPlacer` |
| Setting | `IndEvo_Minefield_HitChance_frigate/destroyer/cruiser/capital` (0.02/0.04/0.06/0.12) | `LunaSettings.csv:44-47`; `MineBeltTerrainPlugin.getBaseFleetHitChance` |
| Setting | `IndEvo_Minefield_NoHitUntilSum` (0.5) | `LunaSettings.csv:48`; `MAX_FLEET_SIZE_BEFORE_MALUS` |
| Setting | `IndEvo_Minefield_CivilianShipImpactMult` (0.5), `IndEvo_Minefield_PhaseShipImpactMult` (0.3) | `LunaSettings.csv:49-50` |
| Setting | `IndEvo_Minefield_disable_duration` (5 s) | `LunaSettings.csv:43`; read in `InterdictionPulseAbilityListener:40` |
| Memory (market) | `$IndEvo_MineFieldRefKey` = belt entity id; `$IndEvo_NoAsteroids` = skip ring band visual | `MineFieldCondition:19-20` |
| Memory (terrain, mine) | `$IndEvo_PlanetMinefieldKey` = controlling planet entity | `MineFieldCondition:21`, copied to every mine in `createMines` |
| Memory (location) | `$IndEvo_MinesDisabledLocMem` = `List<DisabledArea>` | `MineBeltTerrainPlugin:44` |
| Memory (fleet) | `$IndEvo_recentlyJumped` (2 s), `$mineImpactTimeout`, `$skippedImpacts`, `$recentImpact` | `MineBeltTerrainPlugin:42`, `:217-219` |
| Tips | three minefield loading tips | `data/strings/tips.json:81-82`, `:92` |

## Registration and lifecycle

- Spawn on new game: `ModPlugin.onNewGameAfterEconomyLoad()` -> `spawnMineFields()` (`indevo/utils/ModPlugin.java:326`, `:379-393`) adds the condition to `culann`, `chicomoztoc` and `kantas_den` (the latter two with `$IndEvo_NoAsteroids`). Arsenal stations in planet orbit add it too (`indevo/exploration/stations/DerelictStationPlacer.java:143-157`).
- Condition plugin from the CSV plugin column. `MineFieldCondition.advance()` calls `addMineField()` on the first tick where the market lacks `$IndEvo_MineFieldRefKey` (`MineFieldCondition.java:72-78`).
- Terrain plugins from `terrain.json`; entity plugins from `custom_entities.json`.
- Listeners: `RecentJumpListener.register()` and `InterdictionPulseAbilityListener.register()` in `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:466`, `:468`). Both are transient and re-added on each load.
- Save: `ModPlugin.beforeGameSave()` removes every `IndEvo_Mine` entity that has an asteroid source and empty memory, keeps the rest and calls `reportAsteroidPersisted` on them (`ModPlugin.java:336-358`); `afterGameSave`/`onGameSaveFailed` re-add them (`:370-387`). On load, vanilla `CoreLifecyclePluginImpl.onGameLoad` calls `regenerateAsteroids()` on every terrain plugin that is an `AsteroidSource` (starsector-knowledge `api/impl/campaign/CoreLifecyclePluginImpl.java:513-516`, `:1246-1254`), which re-creates the mines through `createMines()`.

Order: `onNewGameAfterEconomyLoad -> spawnMineFields -> addCondition -> MineFieldCondition.advance -> addMineField -> MineBeltTerrainPlugin.addMineBelt -> advance -> createMines`.

## Flow

### Belt creation

`MineFieldCondition.addMineField()` (`:42-69`): if a `BaseRingTerrain` in the system orbits this market's entity, the belt takes that terrain's radius; otherwise radius 500 and a `rings_dust0` ring band unless `$IndEvo_NoAsteroids`. Width 100, orbit 30 to 40 days, name "<planet> Minefield". `MineBeltTerrainPlugin.addMineBelt()` sets `numMines = round(orbitRadius / 3)` (`:72-80`). `createMines()` (`:115-160`) seeds `Random` with clock timestamp plus entity id hash, adds mines of size 2 to 8 on circular orbits inside the band and calls `Misc.setAsteroidSource(mine, this)`. Each mine rerolls its angle up to 30 times to stay out of the 10 degree windows of earlier mines, so only the first 36 or so are spread out.

### Hit roll

`MineBeltTerrainPlugin.applyEffect()` (`:200-267`), per fleet in the terrain:

1. Slow-moving fleets get a 0.1 detected-range mult (hiding like vanilla ring terrain).
2. Skip if `isFriend()`, in battle, recently jumped, or inside a `DisabledArea` of this belt.
3. If neither `$mineImpactTimeout` nor `$recentImpact` is set: `chance = skips/0.1 * 0.3 + max(hullSum - NoHitUntilSum, 0) + burn^2 * 0.003` (doubled burn term at burn 10+). `hullSum` is the per-hull-size setting summed over members, scaled by the civilian and phase mults (`getBaseFleetHitChance`, `:299-323`).
4. Slow fleets with `hullSum < NoHitUntilSum` are never hit. On hit: `fleet.addScript(new MineImpact(fleet))`, reset skips, set `$recentImpact` (player 0.05 to 1.05, NPC 5 to 7 days). On miss: grow `$skippedImpacts`.
5. Always set `$mineImpactTimeout` (player 0.05 to 0.15, NPC 1 to 2 days).

`isFriend()` (`:269-285`): friend if the controlling market is player-owned and the fleet is the player, or same faction, or relationship at worst `INHOSPITABLE`; the player without transponder at a non-player market is never a friend; fleets with `MemFlags.MEMORY_KEY_MISSION_IMPORTANT` are always friends. `getPrimary()` resolves the market from `$IndEvo_PlanetMinefieldKey` or the belt's orbit focus.

`hasAIFlag()` returns `DANGEROUS_UNLESS_GO_SLOW` for non-friends without `$recentImpact` (`:409-413`).

### Impact

`MineImpact` (`:36-134`): picks a kick angle opposite the velocity (arc 120 minus 60 times fleet radius mult), spawns a short-lived mine and hit glow if the fleet is in the current location, applies the velocity change for 0.3 s, then after 0.1 s spawns `IndEvo_mine_explosion` with `ExplosionFleetDamage.LOW`, radius 150 and `baseDamageMult = 0.5 * clamp(hullSum, 0, 1)`. Off-screen fleets get the kick but no explosion (`mineLoc` is only set in the current location).

### Interdiction pulse disable

`InterdictionPulseAbilityListener.reportPlayerDeactivatedAbility()` (`:27-46`) uses vanilla `InterdictionPulseAbility.getRange(fleet)` and, for every `MineBeltTerrainPlugin` whose outer circle intersects the pulse circle, calls `generateDisabledArea(fleet, range, duration)` (`MineBeltTerrainPlugin:436-447`). The area is a `SplinterFleet_OrbitFocus` token orbiting the belt at the fleet position; `DisabledArea.advance()` counts down in seconds from the terrain's `advance()` and removes the token at zero.

## Contracts

- Mines are custom entities, not vanilla asteroids, so vanilla `beforeGameSave` (which only walks `loc.getAsteroids()`) does not strip them; IE copies that logic for the `IndEvo_Mine` tag (starsector-knowledge `api/impl/campaign/CoreLifecyclePluginImpl.java:1259-1290`; `ModPlugin.java:336-358`). Keep the tag and the `AsteroidSource` interface or saves grow with every mine.
- Mines with non-empty memory are saved. Condition-spawned belts copy `$IndEvo_PlanetMinefieldKey` into every mine, so (inferred) those mines always persist, `reportAsteroidPersisted` drops `numMines` to 0 on the first save, and later loads regenerate nothing; the persisted mines keep working because their orbit focus is the belt.
- Terrain `applyEffect` runs per frame for fleets inside; off-screen locations advance at reduced frequency with scaled `amount` (starsector-knowledge `docs/concepts/campaign-frame.md`). The timeouts are memory expirations in days, so the roll rate is frame independent.
- `RECENT_JUMP_KEY` expiry is `2 / CampaignClock.SECONDS_PER_GAME_DAY` days, that is 2 s (`RecentJumpListener.java:27`; `SECONDS_PER_GAME_DAY = 10` in starsector-knowledge `obf/campaign/CampaignClock.java:8`). `RecentJumpListener` imports obfuscated `com.fs.starfarer.campaign.CampaignClock`; `MineBeltTerrainPlugin` and `MineFieldTerrainPlugin` cast to obfuscated `com.fs.starfarer.campaign.BaseLocation` to call `addTerrain`.
- `DisabledArea` objects live in location memory and are saved; the token entity is a normal saved custom entity.
- The disabled-state glow in `MineCustomEntityPlugin.render()` reads the same location memory list; the green friendly glow uses its own friend rule (relationship and transponder only), which differs from `isFriend()`.

## Known issues and dormant code

| Item | State |
|---|---|
| `MineFieldTerrainPlugin`, `addMineField(...)` | Dormant: no caller. Its own `params` field shadows the parent's, so the parent `params` stays null; `InterdictionPulseAbilityListener:39` would NPE on such a terrain (inferred) |
| `MineAPI` | Dead, no implementation |
| Condition-spawned belt persistence | See Contracts; inferred, not tested in game |
| `MineFieldCondition.log` | Logger created for `CombatAndDerelictionScript.class` (`:24`), cosmetic |
| Changelog_todo | No open minefield entries |

## Maintenance

Update this doc when the minefield code, the condition, the terrain or entity rows, the `IndEvo_Minefield_*` settings or the save hooks in `ModPlugin` change. Arsenal station placement belongs in [Stations](STATIONS.md); the settings mechanism in [Settings](../core/SETTINGS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
