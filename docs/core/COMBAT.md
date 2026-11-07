# Combat

IE's combat code in `data/scripts/` (weapon effect plugins, missile AI, combat plugins) and the weapons in `data/weapons/` that use it. Read it before changing a weapon, its projectile or its effect. It does not cover campaign artillery shots or the research projects that hand out the weapons; see [Artillery](../industries/ARTILLERY.md) and [Research](../dialogue/RESEARCH.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Ten weapons. Six are player weapons with tier and blueprint, given out by Galatia Academy research projects. Three are artillery station weapons (mortar, railgun, missile) fitted to `WS0005` of the artillery station hulls and skins, used when the player fights an artillery station in combat. `IndEvo_riftgun_minelayer` is an internal weapon spawned by the Riftpulse on hit effect. Effect plugins are bound in `.wpn` / `.proj` files; the two artillery missile AIs are picked in `ModPlugin.pickMissileAI`.

## Files

| File | Owner / role |
|---|---|
| `data/scripts/weapons/IndEvo_causalityGunEffect.java` | `BeamEffectPlugin` of `IndEvo_causalitygun`: nebula visuals, EMP arcs that pierce shields with chance `hardFlux - 0.1` (times `SHIELD_PIERCED_MULT`), arc damage 25% of beam damage, EMP 50% of flux component |
| `data/scripts/weapons/IndEvo_cryoOnFireEffect.java` | `OnFireEffectPlugin` of `IndEvo_Cryo_Shot`: muzzle nebula and flash |
| `data/scripts/weapons/IndEvo_degradeEffect.java` | `OnHitEffectPlugin` of `IndEvo_degrader_shot`: on hull or armor hit (not shield, not fading) adds a layered render plugin that removes 100 armor over 11 ticks (~0.9 s) in a 5x5 cell area without corners, scaled by armor damage taken and hull size damage mults; reverse subtract nebula render, `disintegrator_loop` sound |
| `data/scripts/weapons/IndEvo_riftgunOnFireEffect.java` | Muzzle effect of `IndEvo_riftgun_shot` |
| `data/scripts/weapons/IndEvo_riftgunOnHitEffect.java` | On hit of `IndEvo_riftgun_shot`: 20% chance on ships to spawn an `IndEvo_riftgun_minelayer` mine behind the impact (rift mine, `RiftBeamMineExplosion`) |
| `data/scripts/weapons/IndEvo_targetingBeamEffect.java` | `EveryFrameWeaponEffectPlugin` of `IndEvo_artillery_railgun`: MagicLib fake targeting beam and up to 6 cosmetic EMP arcs while charging |
| `data/scripts/weapons/IndEvo_mortarProjectileOnFireEffect.java` | On fire of `IndEvo_mortar_projectile`: sets max flight time to reach the current target (autofire target or ship target) |
| `data/scripts/weapons/IndEvo_missileProjectileOnFireEffect.java` | On fire of `IndEvo_missile_projectile`: launch smoke through the engine's internal smoke particle group |
| `data/scripts/weapons/ai/IndEvo_mortarProjectileAI.java` | `MissileAIPlugin` for `IndEvo_mortar_projectile`: becomes collidable near enemies; at end of flight a large HE `DamagingExplosionSpec` (radius 1000, core 200, 3500/750 damage, sound `IndEvo_mortar_hit`) plus MagicRender visuals; blinks while flying |
| `data/scripts/weapons/ai/IndEvo_missileProjectileAI.java` | `MissileAIPlugin` for `IndEvo_missile_projectile`: detonates at end of flight, with an enemy within 100, or with no enemy ahead and one beside within 500; HE explosion (sound `IndEvo_missile_hit`) and an `IndEvo_CombatSlowFieldTerrain` |
| `data/scripts/weapons/combatEntities/IndEvo_CombatSlowFieldTerrain.java` | `BaseEveryFrameCombatPlugin`: 15 s field, radius 1200 with 1 s ramp up; ships inside get max speed x0.5 (`IndEvo_slowfield_speed_mult`) and nebula flag; during ramp up ships hit are overloaded 0.5 s through `IndEvo_ForcedOverload` |
| `data/scripts/weapons/combatEntities/IndEvo_ForcedOverload.java` | `BaseEveryFrameCombatPlugin`: `forceOverload(dur)` on init, `stopOverload()` after `dur` |

## Data and ids

| Weapon id | Type / size | Script binding | Used by |
|---|---|---|---|
| `IndEvo_cryoartillery` | ballistic medium | proj `IndEvo_Cryo_Shot`: on hit vanilla `CryoblasterEffect`, on fire `IndEvo_cryoOnFireEffect`; MagicLib trails | Research `Ids.PROJ_SNOWBLIND` (`ResearchProjectTemplateRepo.java:258`) |
| `IndEvo_degrader` | ballistic small | proj `IndEvo_degrader_shot`: on hit `IndEvo_degradeEffect` | Research `PROJ_SPITFIRE` (`:295`) |
| `IndEvo_causalitygun` | energy beam large | `beamEffect` `IndEvo_causalityGunEffect`; GraphicsLib light | Research `PROJ_EUREKA` (`:332`) |
| `IndEvo_pulsedcarbine` | ballistic medium | none | Research `PROJ_PARALLAX` (`:369`) |
| `IndEvo_riftgun` | ballistic small | proj `IndEvo_riftgun_shot`: on hit and on fire riftgun effects; MagicLib trails | Research `PROJ_KEYHOLE` (`:406`) |
| `IndEvo_riftgun_minelayer` | missile medium, hint `SYSTEM` | proj `IndEvo_riftgun_minelayer_mine`, vanilla `RiftBeamMineExplosion` | `IndEvo_riftgunOnHitEffect.java:35` only |
| `hartpulse` | energy large | none; MagicLib trail | Research `PROJ_PULSE` (`:79`) |
| `IndEvo_artillery_mortar` | ballistic large, `SYSTEM` | proj `IndEvo_mortar_projectile` (missile): on fire effect, AI from `ModPlugin.pickMissileAI` | `data/hulls/IndEvo_artilleryStation_base.ship:21`, `skins/IndEvo_artilleryStation_mortar.skin:17` |
| `IndEvo_artillery_railgun` | ballistic large, `SYSTEM` | `everyFrameEffect` `IndEvo_targetingBeamEffect`; proj `IndEvo_railgun_projectile` (no script) | `skins/IndEvo_artilleryStation_railgun.skin:17` |
| `IndEvo_artillery_missile` | missile large, `SYSTEM` | proj `IndEvo_missile_projectile` (missile): on fire effect, AI from `ModPlugin.pickMissileAI` | `skins/IndEvo_artilleryStation_missile.skin:17` |

All rows in `data/weapons/weapon_data.csv`. Player weapons are tagged `no_dealer, restricted, codex_unlockable`; artillery weapons also `no_drop, no_drop_salvage`. `data/weapons/weapon_data_pre_nerf.csv` is not loaded by the engine (inferred: only `weapon_data.csv` is a vanilla spec file; nothing in IE reads the other).

| Other id | Where |
|---|---|
| Sounds `IndEvo_mortar_hit`, `IndEvo_missile_hit`, `IndEvo_hartpulse` | `data/config/sounds.json:101`, `:109`, `:113` |
| Sprites `fx/IndEvo_mortar_wave`, `_smoke`, `_glow`, `_burn` | `data/config/settings.json:121` to `:124` |
| Projectile trails | `data/config/modFiles/magicTrail_data.csv` (MagicLib) |
| Lights and normal maps | `data/lights/IndEvo_lights.csv`, `IndEvo_textures.csv` (GraphicsLib) |
| Stat modifier id | `IndEvo_slowfield_speed_mult` |

## Registration and lifecycle

- `.wpn` / `.proj` plugin keys -> engine instantiates the effect classes.
- `ModPlugin.pickMissileAI` (`indevo/utils/ModPlugin.java:373`): projectile spec `IndEvo_mortar_projectile` -> `IndEvo_mortarProjectileAI`, `IndEvo_missile_projectile` -> `IndEvo_missileProjectileAI`, priority `MOD_SPECIFIC`.
- `IndEvo_missileProjectileAI.splode()` -> `engine.addPlugin(new IndEvo_CombatSlowFieldTerrain(...))` -> per ship hit during ramp up `engine.addPlugin(new IndEvo_ForcedOverload(...))`.
- `IndEvo_degradeEffect.onHit` -> `engine.addLayeredRenderingPlugin(new IndEvo_degradeEffect(proj, target, offset))`.

## Contracts

- `EveryFrameCombatPlugin.advance` runs while paused with real frame time (knowledge base `docs/concepts/combat-frame.md`, `CombatEngine.java:1231`). `IndEvo_CombatSlowFieldTerrain` and `IndEvo_ForcedOverload` skip their timers while `engine.isPaused()`; a new combat plugin with a timer must do the same.
- `IndEvo_missileProjectileOnFireEffect` uses non API classes `com.fs.starfarer.combat.CombatEngine`, `com.fs.graphics.particle.DynamicParticleGroup`, `SmoothParticle` (`IndEvo_missileProjectileOnFireEffect.java:3`, `:4`, `:10`). Recheck after a game update.
- The campaign side of artillery (shots from stations on the campaign map) does not use these classes; it has its own projectile entities and a campaign `IndEvo_slowfield` terrain (`data/campaign/terrain.json:25`). See [Artillery](../industries/ARTILLERY.md).
- MagicLib (`MagicRender`, `MagicFakeBeamPlugin`, trails) and LazyLib are required at runtime by these scripts. See [Compatibility](COMPATIBILITY.md).

## Known issues and dormant code

| Item | State |
|---|---|
| `IndEvo_CombatSlowFieldTerrain` end (`:112`) | Unmodifies speed on every ship within 99999, also ships inside another active field |
| `IndEvo_causalityGunEffect.java:105` | `Math.random() == 0.5f` never true, dead branch |
| `IndEvo_mortarProjectileAI`, `IndEvo_missileProjectileAI` | `target` is stored but never used for guidance |
| `weapon_data_pre_nerf.csv` | Unused data file |

## Maintenance

Update this doc when a weapon, projectile binding, effect plugin or missile AI changes. Research rewards belong in [Research](../dialogue/RESEARCH.md); station hulls and campaign artillery in [Artillery](../industries/ARTILLERY.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
