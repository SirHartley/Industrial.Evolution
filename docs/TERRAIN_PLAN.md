# Industrial.Evolution terrain plan

Tracking doc for new campaign terrain, kept like [NEW_FEATURES.md](NEW_FEATURES.md): numbered items, each with a status line, the plan and the open questions. Plan only, no code. Baseline: IE `dev` at `3410777`, Catch.Release (CR) `master` at `37c1c37` (2026-11-09).

- Engine facts: starsector-knowledge skill, Starsector 0.98a-RC8. Vanilla paths are relative to `com/fs/starfarer/api/`, IE paths to `jars/src/`.
- Nothing here was tested in game. "Confirmed" means traced in code, "inferred" means a reasoned guess.
- Standing rules: never patch other mods, and no class from CR is referenced by IE (plain id strings are fine). IE stays Java only. Saves may break; items only note it.

## 0. Shared

Every item below is a terrain plugin under a new package `indevo/exploration/terrain/<feature>/`, a `data/campaign/terrain.json` row and a `descriptions.csv` row, with its own feature doc under `docs/exploration/` once implemented.

- Placement: seeded in `ModPlugin.onNewGameAfterEconomyLoad` (`indevo/utils/ModPlugin.java:295`) for new games, never in core worlds (`theme_core`). Existing saves get nothing unless Q2 says otherwise.
- Simulation and rendering run only in the player's location, near the viewport. Everything that moves (fog density, rift heightmap phase, vortex debris) is transient and rebuilt from saved params and the clock on load, so saves only hold seeds and params.
- Layers: vanilla nebula draws in `TERRAIN_2` and `TERRAIN_8` (`impl/campaign/terrain/NebulaTerrainPlugin.java:49`). `TERRAIN_8` is above `FLEETS` (`campaign/CampaignEngineLayers.java`), which is what lets a terrain cover fleets.
- Each item gets a LunaLib toggle and an intensity setting in the exploration tab (`docs/core/SETTINGS.md`).
- Damage that hits ships follows the vanilla hyperspace storm strike (`impl/campaign/terrain/HyperspaceTerrainPlugin.java:1455-1550`): CR loss plus armour scaled hull damage, scaled by `Stats.CORONA_EFFECT_MULT` (Solar Shielding), hull never below 1%. Confirmed.
- Push and pull follow the vanilla corona wind and `WavefrontTerrainPlugin.applyEffect` (`impl/campaign/terrain/WavefrontTerrainPlugin.java:436-500`): a velocity nudge per frame plus a temporary burn modifier. Confirmed.

## 1. Planned terrain

### 1.1 Fog banks (nebula smoke)

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "nebula / smoke that reduces visibility and behaves like vid attached".

What the video shows: a thick, low lying green fog over a floor. It has slow swirling eddies and darker whorls, and it is opaque enough that the floor is hidden. Walking through it pushes it aside a little and leaves a soft wake. A spin attack blows it outward in a spiral and clears a full circle down to the floor; the fog flows back into the hole within about a second and the surface is smooth again. A sword beam cuts a short straight lane that also closes up.

Campaign version:

- Fog banks of 4000 to 12000 units, drawn over fleets (`TERRAIN_8`), so a fleet inside is actually hidden on screen, not only on sensors. Darker whorls drift and turn slowly.
- A fleet moving through the fog pushes it aside and leaves a wake that refills. Bigger and faster fleets push more.
- Burst actions blow a clear circle that refills over a few seconds, like the spin attack: Emergency Burn and Interdiction Pulse. Exact list in Q3.
- Effect scales with local density: detected range of fleets inside x0.5 at full density, and their own sensor range x0.5 (Q4). The nebula value is 0.5 (`NebulaTerrainPlugin.java:26`); abyssal hyperspace cuts own sensor range the same way (`HyperspaceTerrainPlugin.java:1398-1402`). Effect category `nebula-like`, so it does not stack with a nebula, same as the Chaff Cloud (`indevo/items/consumables/terrain/SmokeCloudTerrain.java:101`).
- The play: hide a fleet in the fog, or burn through it and give away where you are.

How (inferred):

- `FogBankTerrain extends BaseTerrain`. Saved: centre, radius, seed. Transient: a density grid (about 100 units per cell, so a 12000 bank is 120 x 120) and a velocity grid.
- Rest state is a seeded noise mask. Each frame the grid relaxes back to that mask, is moved by a slow curl noise flow (the swirl), and gets impulses: a radial push around each moving fleet in the bank and a strong radial blast for burst actions. Semi Lagrangian advection and a relax step are enough; no real fluid solver.
- Burst actions come from `CampaignEventListener.reportPlayerActivatedAbility` for the player; NPC Emergency Burn needs a check of active abilities on fleets inside the bank, only while the bank is on screen.
- Render: the density grid is uploaded as a small texture each frame and multiplied into a scrolling cloud texture in a shader. Two cloud layers at different scroll speeds give the depth seen in the video.
- `containsPoint` and the stat effect read the CPU grid, so what you see is what hides you.
- AI flag `REDUCES_DETECTABILITY` so NPC fleets use it the way they use nebulae.
- Off screen the grid is dropped; it is rebuilt at rest state when the player comes back.

### 1.2 Black hole maelstroms

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "proper whiring, moving debris whirlpools around black holes that rip and pull in the player and contain 'roids at different speeds".

What vanilla has (confirmed): a black hole gets an `event_horizon` terrain, a `StarCoronaTerrainPlugin` subclass with solar wind -10, which is the pull (`impl/campaign/terrain/EventHorizonPlugin.java`, `impl/campaign/procgen/StarSystemGenerator.java:880-900`, `data/campaign/procgen/star_gen_data.csv` row `black_hole`). The accretion disk is ring bands plus a plain ring terrain (`impl/campaign/procgen/AccretionDiskGenPlugin.java:85-96`). Nothing spins, nothing hits.

Plan:

- 2 to 4 whirlpools per black hole system, each 1500 to 3000 units across, orbiting the black hole outside the event horizon at different radii and speeds.
- Inside a whirlpool, debris and asteroids circle its centre, inner ones faster than outer ones.
- Fleets inside are dragged along the spin and pulled toward the centre, so a fleet that stops spirals in. Pull and drag are scaled down by burn level, so a fleet at full burn can cut across.
- The core rips: storm strike damage (section 0) while inside the inner third.
- Asteroids are IE meteors: `MeteorEntity` (`indevo/exploration/meteor/entities/MeteorEntity.java`) with an orbit movement module like `ExternalOrbitMovement`, so they really collide and use `MeteorImpact`. Vanilla belt impacts are only a dice roll per day (`impl/campaign/terrain/AsteroidBeltTerrainPlugin.java:165-186`), which would not give anything to dodge. Meteor collisions only run within 1000 units of the viewport (`MeteorEntity.java:107`), so off screen whirlpools cost little. Asteroids keep the `IndEvo_MeteorSwarm` tag, so CR harpoons and IE missiles work on them as they do on swarms.
- Debris: a transient Luna renderer drawing a particle spiral per whirlpool, only while on screen.
- `MaelstromTerrain extends BaseTerrain` with a circular orbit around the black hole; AI flag `AVOID_VERY_CAREFULLY` for the core like the event horizon (`EventHorizonPlugin.java:51`).

### 1.3 Meteor drizzle (system wide light showers)

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "full-system low volume meteor showers that just have small 'roids across the entire system and no treasureoids".

Plan:

- A thin, steady rain of small asteroids (`IndEvo_meteor_1` and `_2` only) across the whole system for some days. No treasureoids, no arc, no warning path or sign.
- Rocks cross the system on straight lines in one general direction per shower with some spread, at mixed speeds.
- Only rocks the player can see exist: a spawner keeps a target count of meteors in a ring just outside the viewport, spawns at the edge on lines that cross the view, and removes rocks once they are well off screen. Cost stays flat whatever the system size, and NPC fleets off screen are not hit, same as the big swarms (collision only near the viewport, `MeteorEntity.java:107`).
- A system wide terrain `IndEvo_meteor_drizzle` (radius 50000 at the centre, same as `RadioactiveTerrain`) carries the tooltip, the effect category and the end time, so the player can see it on hover and in the system. It removes itself and the spawner at the end.
- Rolled from `MeteorSwarmManager.reportEconomyTick` with its own chance and timeout; uses `isValidMeteorLoc` so it shares the swarm rules.
- Needs a movement module that does not report to the warning path. The rogue asteroid entry (NEW_FEATURES.md 1.3, PR #117) needs the same module; build it once.

### 1.4 Unstable fabric (hyperspace rifts)

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "unstable fabric: "pond-like" patches in large swathes of the system, going on them kicks you to HS; they udulate and move so you constantly have to dodge them - maybe use a heightmap(s) like we do for the rings in CR?"

How CR does its rings (confirmed, CR `master` `37c1c37`): `catchrelease/rendering/plugins/NoiseMappedCircularRingRenderer.java` draws a ring from a noise texture in a shader with `noiseScroll`, `noiseCutoff` and `noiseSoft` uniforms: the noise is the heightmap, the cutoff decides where the ring is, the soft band feathers the edge, and the scroll makes it move. Used by `rendering/renderers/RippleRingRenderer.java`. CR's ponds (`campaign/ponds/terrain/MaskedFishingPondTerrainPlugin.java`, terrain id `catchrelease_StaticPond`) use a stencil mask for their shape.

Plan:

- One rift field per chosen system, covering a large swathe (an annulus or a sector of the system, 10000 to 25000 units). Inside it, rifts are where a heightmap is above a cutoff. Between the soft line and the cutoff the fabric shimmers as a warning.
- The heightmap is two octaves of tiling noise scrolling in different directions plus a slow time offset, so the rifts swell, shrink, split, merge and drift. The player has to keep steering around them.
- Same idea as the CR rings, but the heightmap is generated in Java from a seed (a 256 x 256 float array), sampled on the CPU for `containsPoint`, and uploaded once as a texture for the shader. The two always agree: what you see is what kicks you. IE gets its own shader and renderer; no CR class is used.
- A keep clear mask multiplies the heightmap down around planets, stations, jump points, markets and gates, so rifts never cover them and a fleet arriving by jump point never lands in one.
- Entering a rift (inside past a short grace, Q14) throws the fleet to hyperspace with `SectorAPI.doHyperspaceTransition(fleet, token, JumpDestination)` (`campaign/SectorAPI.java:64`), landing near the system in hyperspace.
- Map: `renderOnMap` draws the current rift outline coarse, so the player can plan a route.
- `UnstableFabricTerrain extends BaseTerrain`. Saved: centre, shape, seed, scroll vectors. Phase comes from the clock, so it needs no saved state.

### 1.5 Solar flares (coronal ejections)

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "Solar flares - massive ejection clouds that damage the fleet on unstable suns".

What vanilla has (confirmed): coronas already flare. `FlareManager` stretches the corona outward in an arc, and fleets in an active flare lose CR twice as fast (`impl/campaign/terrain/StarCoronaTerrainPlugin.java:198-240`). Each corona rolls `flareProbability` at generation from `minFlare` and `maxFlare` in `star_gen_data.csv`: 0 to 1 for most stars, 0 to 3 for blue giants and supergiants, 0 for brown dwarfs and black holes. `StarCoronaTerrainPlugin.getFlareProbability()` reads it (`:552`). Vanilla also has an unused moving wave terrain, `WavefrontTerrainPlugin` (an arc that travels outward and widens, with a wind push), only referenced from commented code (`HyperspaceTerrainPlugin.java:909-938`).

Plan:

- Unstable stars are stars whose corona `flareProbability` is at or above a threshold (Q16). No new tag needed; it works on existing generation.
- Every few weeks an unstable star throws an ejection: a cloud front in a random direction, an arc of 30 to 60 degrees that leaves the star and crosses the system over a day or two, widening and thinning as it goes.
- Built on the `WavefrontTerrainPlugin` model: same arc, width and thickness growth and wind push, in an IE `SolarEjectionTerrain`. Fleets in the front take storm strikes (section 0) and are pushed outward.
- Planets cast a shadow: fleets behind a planet as seen from the star are safe. Vanilla's `RangeBlockerUtil` already does this for pulsar beams and coronas (`PulsarBeamTerrainPlugin.java:37-64`).
- Warning: the star brightens and the corona flares for about a day before the ejection while the player is in the system, plus a short campaign message.
- The front removes itself once it passes the system edge.

### 1.6 Currents (in-system debris rivers)

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "In-system currents: narrow debris rivers that move like vanilla slipstreams. They boost burn along the flow but shove you sideways at the bends. Fast lanes for anyone who reads them, with salvage drifting in them."

What vanilla has (confirmed):

- `SlipstreamTerrainPlugin2` (`impl/campaign/velfield/SlipstreamTerrainPlugin2.java`) is a segment terrain (`addSegment(loc, width)`, `:344`) built along curves by `SlipstreamBuilder` (`buildToDestination`, `velfield/SlipstreamBuilder.java:191-265`).
- Fleets inside get the stream's burn on top of their own (`:2305-2309`). Wrecks (`Entities.WRECK`) inside are carried too (`applyEffectToEntities`, `:2093-2110`, `applyEffectToWreck`, `:2531`).
- Its hyperspace only parts are guarded by `entity.isInHyperspace()` (`:669`); sprites and colours are params (`SlipstreamParams2`, `:52-72`). Vanilla only places streams in hyperspace.
- Inferred, not tested: it works in a star system as is. The first step of this item is a probe that adds one to a system.

Plan:

- 1 to 3 currents in some systems, on curved paths that sweep round the star and past planets, or as loops. Keep clear of markets, stations and jump points (same keep clear rule as 1.4).
- `CurrentTerrain extends SlipstreamTerrainPlugin2` with IE params and lower burn than hyperspace streams (Q21). Fuel use does not matter in systems.
- Visuals: much lighter and less obvious than a vanilla slipstream (Philipp, 2026-11-09). A current should be found by reading it, not seen from across the system:
  - `render` is overridden: none of the vanilla sprite layers, edges or wobble (`SlipstreamTerrainPlugin2.java:1060`). Only sparse, faint dust motes and small grit drifting along the flow at the flow's speed, low alpha, no glow. The moving dust and the drifting wrecks are the tell.
  - No fleet wind glow (`forceNoWindVisualEffectOnFleets`, `:55`, read at `:2400`) and no "Entering slipstream" text (`enteringSlipstreamTextOverride` set to `""`, skipped when empty, `:2187-2196`).
  - No loop sounds in the `terrain.json` row, or one very quiet loop.
  - Map: not drawn by default (Q31).
- Bend shove: where the path turns sharply, fleets in the outer half get a sideways push outward that grows with their speed, so a fast fleet gets thrown out of the bend unless it hugs the inner edge. Bends are found once from the segment directions and cached.
- Drifting salvage: a derelict wreck (vanilla `Entities.WRECK`) or a small debris field is put at the head of the current every few days. Vanilla code carries the wrecks; IE moves the debris fields the same way. Both are removed at the end of the current. Riding the current or waiting at its mouth both pay.

### 1.7 Nanite drift

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "Nanite drift: grey clouds that slowly repair hull but eat metals and heavy machinery from cargo. It ties into the IE Nanites ability."

What IE has (confirmed): the Nanites consumable (`indevo/items/consumables/itemAbilities/NanitesAbilityPlugin.java`) repairs a 25000 hull and armour budget, flagship first, then officer ships, then the rest, and adds 20% CR up to 50%. Armour goes through the obf `FleetMemberStatus` (`:72-74`, [CONSUMABLES](items/CONSUMABLES.md)).

Plan:

- Grey, glittering clouds 2000 to 5000 across, drifting slowly through the system (shared cloud shape and drift, Q30).
- Inside: each day, a hull and armour budget per fleet is repaired in the Nanites order. The repair code moves out of `NanitesAbilityPlugin` into a shared helper that both use.
- Cost: metals from cargo in proportion to the repair done, then heavy machinery once metals run out (Q25). A small flat feed is eaten each day while inside, even with nothing to repair.
- Nothing left to eat: the nanites start on the hull at the same rate (Q24).
- Tooltip shows repair per day and cargo eaten per day for the player's fleet.

### 1.8 Ion storms

**Status: proposed (Philipp, 2026-11-09). Not implemented.**

Philipp: "Ion storms: drifting clouds that switch off abilities while you're inside them (Go Dark, Sustained Burn, Sensor Burst). The transponder also flickers on now and then, so a storm is a bad place to sneak."

What vanilla has (confirmed):

- `AbilityPlugin.forceDisable()` makes an ability unusable for 1 to 2 frames (`characters/AbilityPlugin.java:122-125`, `impl/campaign/abilities/BaseAbilityPlugin.java:180-186`). Calling it every frame keeps the ability off; `deactivate()` stops one already running. Vanilla does exactly this while a slipsurge is generated (`GenerateSlipsurgeAbility.java:228-238`).
- The transponder is `SectorEntityToken.setTransponderOn` (`campaign/SectorEntityToken.java:167`), normally driven by the transponder toggle (`TransponderAbility.java:46-94`).
- Vanilla magnetic storms cut sensor range to x0.1 (`MagneticFieldTerrainPlugin.java:84`, `:191`). Ion storms do something else, so the two do not overlap.

Plan:

- Storm clouds 3000 to 8000 across with lightning arcs inside, drifting across the system over a few days.
- Every fleet inside: the blocked abilities (Q27) are deactivated and kept off with `forceDisable` from the terrain's `applyEffect`. Leaving the storm gives them back at once.
- Transponder flicker, player only: a lightning strike near the player's fleet with the transponder off turns the transponder ability on for 3 to 5 seconds, then off again. Nearby patrols see who it is, so a storm is a bad place to sneak.
- Strikes are visual and sound only (Q29).

## 2. Open questions

Each has a recommended answer.

1. Doc home: this separate doc (recommended, the five share placement, rendering and damage rules), or items in NEW_FEATURES.md?
2. Existing saves: new games only (recommended, saves may break anyway), or also seed systems on first entry in old saves?
3. Fog: which actions blow a hole? Recommended: fleet movement (small wake), Emergency Burn and Interdiction Pulse (big blast). Sensor Burst and Sustained Burn do nothing.
4. Fog: hide on screen and cut both detected range and own sensor range x0.5 at full density (recommended), or detected range only like a nebula?
5. Fog: where? Recommended: 1 to 3 banks in nebula systems and around gas giants, outside core worlds.
6. Fog: should the Chaff Cloud consumable later use the fog renderer? Recommended yes, as a follow up.
7. Maelstrom: damage only in the core with pull and drag everywhere in the whirlpool (recommended), or damage in the whole whirlpool?
8. Maelstrom: every black hole system (recommended, there are few), or a roll?
9. Maelstrom: asteroids as real IE meteors that collide (recommended), or vanilla belt dice rolls?
10. Drizzle: a timed event of 5 to 15 days rolled on the economy tick (recommended), or a permanent trait of some systems?
11. Drizzle: uninhabited systems only like the big swarms (recommended, same rule as rogue asteroids Q10 in NEW_FEATURES.md), or inhabited too?
12. Rifts: throw the player and their detachments only (recommended; NPC fleets get the avoid AI flag and are not thrown, so patrols and quest fleets do not vanish), or every fleet?
13. Rifts: cost of being thrown. Recommended: none besides the trip back, no fuel. Alternative: a small CR hit like a jump without a jump point.
14. Rifts: grace before the throw. Recommended 0.5 s inside with a strong shimmer and sound, so clipping an edge is not punished.
15. Rifts: CR calls its ponds ruptures and has `UnstableFabricRippleTerrainRenderer`. Keep the name "Unstable Fabric" (recommended, it is a different thing in a different mod), or rename to avoid confusion? Also keep rifts clear of `catchrelease_StaticPond` terrain by id string (recommended, read only, no CR class).
16. Flares: which stars are unstable? Recommended `flareProbability >= 0.8`: about one in five ordinary stars and most blue giants and supergiants.
17. Flares: storm strike damage with planet shadow (recommended), or CR loss only like vanilla flares?
18. Flares: warning by star flare plus campaign message (recommended), or an intel entry with a countdown?
19. Settings: one LunaLib toggle and one intensity per terrain (recommended)?
20. Currents: build on vanilla `SlipstreamTerrainPlugin2` (recommended, it carries wrecks and handles burn already), or an own terrain?
21. Currents: flow burn. Recommended 10 to 15, below hyperspace streams.
22. Currents: bends throw a fast fleet out (recommended), or only slow it down?
23. Currents: drifting salvage as wrecks and small debris fields every few days (recommended), or wrecks only?
24. Nanites: with nothing left to eat, they eat hull (recommended, it makes the cloud a choice), or they just stop repairing?
25. Nanites: rate. Recommended 2500 hull and armour per day per fleet (a tenth of the consumable), 1 metals per 50 points, heavy machinery counts as 5 metals.
26. Nanites: NPC fleets repaired too, with no cargo cost (recommended), or player only?
27. Ion storms: blocked abilities. Recommended Go Dark, Sustained Burn, Emergency Burn, Sensor Burst and Interdiction Pulse. Not Transverse Jump, so there is always a way out, and not IE consumables.
28. Ion storms: transponder flicker for the player only (recommended), or a short detected range spike for every fleet instead?
29. Ion storms: harmless apart from the blocks and flicker (recommended), or a small CR loss per strike?
30. Fog banks, nanite drift and ion storms share one cloud shape and drift helper, each with its own look and effect (recommended)?
31. Currents on the map: hidden (recommended, they are found by flying), a faint line once the player has ridden one, or always shown like vanilla streams?

## 3. Suggested order

1. Meteor drizzle (1.3): smallest, reuses the meteor code; build the no warning movement module with the rogue asteroids (NEW_FEATURES.md 1.3).
2. Ion storms (1.8): ability blocks are a few vanilla calls; builds the shared drifting cloud.
3. Nanite drift (1.7): reuses the cloud from step 2 and the Nanites repair code.
4. Solar flares (1.5): vanilla wavefront and storm strike models to copy.
5. Currents (1.6): starts with the probe that puts a vanilla stream in a system.
6. Black hole maelstroms (1.2): reuses meteors from step 1.
7. Unstable fabric (1.4): own heightmap, shader and jump handling.
8. Fog banks (1.1): the grid simulation and shader are the most work; tune against the video.

Each step: feature doc, ARCHITECTURE.md row, settings, changelog, live test by Philipp.
