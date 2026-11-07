# Utils

Shared helper reference for `indevo/utils/`. Search here for an existing owner before adding a helper. One section per subpackage, each helper with its main public members. It does not cover `indevo/utils/ModPlugin.java` (see [Architecture](../ARCHITECTURE.md)) or the setting mechanism (see [Settings](SETTINGS.md)).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

Paths below are relative to `jars/src/indevo/utils/`.

## animation

Staged campaign animations and LunaLib campaign particle renderers. Users: Crucible, Meteor, Derelicts warp effect, Consumables.

| Class | Members | Users |
|---|---|---|
| `animation/CampaignAnimationScript.java` | interface: `addStage(AnimationStage)`, `loadStages()` | `BaseStagedAnimationScript` |
| `animation/AnimationStage.java` | abstract stage: ctor `(runtime, delayBySeconds)`; `advance(amt)` waits for the delay, calls `runOnce()` once then `run(amt)` each frame until `isDone()` (`timePassed >= runtime`) | Crucible and derelicts animation scripts |
| `animation/BaseStagedAnimationScript.java` | `EveryFrameScript`; calls `loadStages()` on first advance, advances all stages in parallel, `done` when all stages are done; `runWhilePaused()` false | `derelicts/scripts/WarpEffectAnimationScript`, `crucible/scripts/CrucibleStartupAnimationScript`, `CrucibleMoveAnimationScript` |
| `animation/data/CirclePathData.java` | center, radius, start and end angle; ctor from two points and a radius via `TrigHelper.findTwoPointCircle` | none (dead) |
| `animation/particles/NebulaParticle.java` | one nebula sprite cell: `advance(amt)`, `getCurrentAlpha()` (0.5 s fade in, `smootherstep` fade out), `isExpired()`; inner `LocationData` (radius, angle, center) with `getLocation()`, `incrementRadius()` | the renderers below, `consumables/entities/SmokeCloudEntityPlugin`, `meteor/renderers/MeteorDebrisRenderer` |
| `animation/particles/RadialDustCloudEjectionRenderer.java` | `LunaCampaignRenderingPlugin`; spawns particles for `spawnDur` then pushes them out radially; fixed 6 s life (`DURATION`), layer `TERRAIN_6A` | Crucible scripts |
| `animation/particles/DeceleratingDustCloudEjectionRenderer.java` | `LunaCampaignRenderingPlugin`; spawns particles at 60 Hz with logistic slowdown (`logisticDecreasing`); never expires on its own (`expired` flag); pauses unless the center entity is alive, in the current location and tagged `BaseCrucibleEntityPlugin.TAG_ENABLED` | `CrucibleStartupAnimationScript` |
| `animation/particles/SmoothFadingParticleRenderer.java` | `LunaCampaignRenderingPlugin`; one sprite, alpha `1 - smootherstep` over `runtime` | `consumables/itemAbilities/missiles/ArtilleryRemoteAbilityPlugin` |
| `animation/particles/BaseCircleTrajectoryFollowingParticle.java` | abstract `LunaCampaignRenderingPlugin` moving along a circle arc between two points over `duration`; exposes `position`, `facing` | `crucible/scripts/CrucibleTrajectoryFollowingMote` |

Renderers keep the sprite in a `transient` field and fetch it lazily in `render`. They are added through LunaLib's `LunaCampaignRenderer` by the callers (see [Compatibility](COMPATIBILITY.md)).

## helper

| Class | Role |
|---|---|
| `helper/MiscIE.java` | General campaign helpers, table below |
| `helper/BillingHelper.java` | Monthly report lines and immediate credit charges, section below |
| `helper/StringHelper.java` | String lookup and token substitution, table below |
| `helper/TrigHelper.java` | Geometry, table below |
| `helper/Circle.java` | `center`, `radius`; `getPointForAngle(angle)`, `getAngleForPoint(point)` |
| `helper/CircularArc.java` | `Circle` plus normalized `startAngle`, `endAngle`; travel direction is the short way round (`getAngleTravelDir()` 1 or -1) |
| `helper/ReflectionUtils.java` | Static helpers (Lukas04 style) by field or method name: `get(name, instance)`, `set(name, instance, value)`, `setWithSuper(name, instance, value, limit, clazz)`, `invoke(name, instance, args, declared)`, `hasMethodOfName(name, instance, contains)`, `hasVariableOfName`, `instantiate`, `getFieldsOfType`, `findFieldsOfType`. Uses the method handles of `StarReflectionUtils`. User: `senate/conditions/Edict_FastBuild.java:92` |
| `helper/StarReflectionUtils.java` | Java port of Starficz's Kotlin reflection utils (LGPL-3.0, keep the header): type based matching for obfuscated members. Static `get(instance, name, type, searchSuperclass)`, `set(...)`, `invoke(instance or class, name, args...)`, `getFieldsMatching`, `getFieldsWithMethodsMatching`, `getMethodsMatching`, `getConstructorsMatching`, each with shorter overloads that drop trailing arguments; results cached per signature; wrappers `ReflectedField`, `ReflectedMethod`, `ReflectedConstructor`. Users: `economy/listeners/RelicBuildTimeReductionButtonAdder.java:60`, `senate/conditions/Edict_FastBuild.java:88` |

Two reflection helpers exist. Prefer `StarReflectionUtils` for new code (ambiguity checks, superclass search); inferred from the code, not a project rule. The mod classloader blocks `java.lang.reflect` by name, so both hold `Field`, `Method` and `Constructor` objects as `Object` only: never declare, cast to or pass a reflection type, and only touch members through the `MethodHandle`s (knowledge skill, `docs/concepts/blocked-classes.md`).

### MiscIE

| Area | Methods (line) |
|---|---|
| Player and dialog | `getPlayerCargo()` 41, `getCurrentInteractionTargetMarket()` 59 (market of the open dialog target or null) |
| Storage | `getStorage(market)` 66 (vanilla storage, falls back to RAT `rat_settlement_storage`), `getStorageCargo(market)` 76, `getIndustrialStorageCargo(market)` 111 (`Ids.SHAREDSTORAGE`), `getMarketForStorage(market)` 513 (gathering point if it has storage, else local) |
| Ship stripping | `stripShipNoCargo(member)` 227, `stripShipToCargoAndReturnVariant(member, cargo)` 396 (also modules, AI core captain), `stripAndReturnVariant(variant, cargo)` 416, `stripShipToCargoAndReturnVariant(member, market)` 447 (into `getMarketForStorage(market)` storage, else fleet cargo) |
| Fleet members | `finalizeAndUpdateFleetMember(member)` 118 (clone variant, clear original), `createFleetMemberClone(member)` 285, `convertFleetMemberListToIdList(list)` 289, `getCargoCopy(cargo)` 270 (copies mothballed ships too) |
| Hull lists (session cached) | `getAllLearnableShipHulls()` 128 (tag ending `_bp`, not fighter, not `IndEvo_no_ship_roulette`), `getAllRareShipHulls()` 145 (`rare_bp`), `isLearnable(spec)` 162, `getShipRoleListMap()` 789 (hull ids per role group from all factions) |
| Whitelists and mod lists (session cached) | `getCSVSetFromMemory(path)` 528 (merged CSV `id` column, master mod `IndEvo`), `getVPCItemSet()` 212, `getPrismBossShips()` 569, `getVayraBossShips()` 598, `canLoadShips(factionOrModId)` 564 |
| Market queries | `isMilitary(industry)` 80, `marketHasMilitary(market, onlyFunctional)` 649, `marketHasMilitaryIncludeRelays(market[, onlyFunctional])` 631, `getFactionMarkets(faction)` 386 (skips `SalvageMarket` ids), `getMarketsInLocation(loc[, factionId])` 488/499 (skips hidden), `getClosestMarketWithIndustry(market, id)` 299, `getClosestPlayerMarketWithIndustryFromSet` 317, `getAiCoreIdNotNull(ind)` 263 (`"none"`) |
| System industry counts | `systemHasIndustry(id, system, faction[, withUnfinished])` 690/720, `systemHasIndustryExcludeNotFunctional` 704, `getAmountOfIndustryInSystem` 739, `isOnlyInstanceInSystem` 763, `isOnlyInstanceInSystemExcludeMarket` 663 (both count the construction queue) |
| Economy | `applyDeficitToProduction(ind, index, deficit, commodities...)` 85. Billing and credits: [BillingHelper](#billinghelper) |
| Planets | `planetHasRings(planet or market)` 454/470 (uses `derelicts/scripts/PlanetMovingScript.getEntitiesWithOrbitTarget`), `getEntitiesInRange(entity, range)` 45 |
| Sector memory maps | `getMapFromMemory(key)` 335 (sorted copy, creates the key), `storeMapInMemory(map, key)` 355, `getClampedMap` 345, `sortByLargestValue` 364, `addOrIncrement(map, id, by)` 173/183 |
| Math and lists | `smootherstep(e0, e1, x)` 97, `customLog(base, x)` 359, `getRandomInRange(min, max, random)` 55, `splitList(list or set, perPage)` 194/207, `getDaysOfCurrentMonth()` 257 |

### BillingHelper

Owner of IE costs and income: monthly report lines and dialog purchases. Do not write vanilla report nodes or subtract credits directly; loot and refunds add credits directly.

| Member | Contract |
|---|---|
| `charge(ind, line, label, amount)` | Adds `amount` to the upkeep of the line node. Callable any time in the month; writes add up, nothing is reset. `amount <= 0` adds nothing |
| `pay(ind, line, label, amount)` | Same node, adds to income |
| `canAfford(amount)` | Player credits `>= amount`. Every dialog checks it before it offers a purchase |
| `chargeNow(amount)` | Subtracts credits immediately for dialog purchases; throws `IllegalStateException` when the player cannot afford it (the caller skipped `canAfford`) |

Node layout in `SharedData.getCurrentReport()`, node id `<industry id>_<line>`, `name` the label, `custom` the industry (the income tab then draws the industry image, shows the industry tooltip and opens the market on the map on click; obfuscated `com.fs.starfarer.campaign.command.IntelIncomePanel` and its row class):

| Market | Path |
|---|---|
| Player owned | `MonthlyReport.getMarketNode(market)` / line, next to vanilla "Industries & structures" and "Hazard pay" |
| Not player owned | `IndEvo_node_services` ("Services", top level) / market id (`custom` the market) / line |

- Vanilla settles the report at month end (`CoreScript.reportEconomyMonthEnd`); a negative total becomes debt, credits never go below 0. A charge after the month rolled over lands in the next month.
- The report keeps the `Industry` reference until it rolls out of the previous report, the same as vanilla industry nodes. No IE class is stored in the report.
- Steady costs that should scale with upkeep mults stay on `getUpkeep().modifyFlat` in `apply()` (Pet Shop on player markets) instead of a report line.

Users: Academy (storage; training on NPC markets is `chargeNow`, own markets train free), Restoration Docks (repairs, dock fees), Pet Shop (NPC market storage), Hull Forge (printing), Courier Port (shipping, billed at the closest port), Embassy (ambassador transfer), Supercomputer (income). `chargeNow`: Academy training at NPC markets, Changeling switch, Salvage Yards custom production and D-mod transfer, Requisitions Center unlock, spacer fine.

### StringHelper

Adapted from Histidine (Nexerelin). Default category is `IndEvo_misc` in `data/strings/strings.json:2`.

| Methods | Notes |
|---|---|
| `getString(category, id[, ucFirst])`, `getString(id[, ucFirst])` | Missing key logs a warning and returns `"[INVALID]" + id` |
| `substituteToken(s, token, replace[, ucFormToo])`, `substituteTokens(s, map or pair list)` | `replaceAll` with the `$` escaped |
| `getStringAndSubstituteToken(...)`, `getStringAndSubstituteTokens(...)` | Lookup plus substitution |
| `substituteFactionTokens(s, faction)`, `getFactionHighlights(faction)`, `getFactionShortName(faction)`, `addFactionNameTokensCustom(tokens, prefix, faction)`, `factionIdListToFactionNameList` | Faction name tokens `$faction`, `$theFaction` and capitalized forms |
| `getFleetAssignmentString(id, target[, missionType])` | Category `indEvo_fleetAssignments` |
| `getShipOrFleet(fleet)`, `getDayOrDays(n)`, `getHeOrShe`, `getHimOrHer`, `writeStringCollection(strings[, and, oxford])` | Grammar helpers |
| `getAbsPercentString(f, oneMinus)`, `getFloatToIntStrx100(f)`, `getRepIntTooltipPair(faction)` | Number formatting |
| `ucFirstIgnore$`, `lcFirst`, `flattenToAscii`, constant `HR` | Misc |

### TrigHelper

| Method | Notes |
|---|---|
| `findTwoPointCircle(p1, p2, radius)` | Center of a circle of `radius` through both points, as `Pair<center, radius>` |
| `findThreePointCircle(p1, p2, p3)` | `Circle` through three points |
| `findClosestCircleIntersectToPoint(c0, r0, c1, r1, target)` | Intersection nearest `target`, null if the circles do not touch |
| `getNormalDistributionCurve(x, s, m)` | Gaussian value |
| `randomWithinArc(arc)` | Uniform in `[-arc/2, arc/2)` |

### CircularArc

`getModifiedRadiusArc(r)`, `isPointOnArc(p)`, `getTraversalProgress(point or angle)` (0 to 1), `convertToDegreesPerSecond(units)`, `getArcCircumference()`, `getEvenlySpacedLocationsOnArc(dist)`, `getPerpendicularLineAtAngle(angle, len)`, `getDistanceFromCenterLine(p)`. Users: `exploration/meteor/movement/*`, `MeteorSwarmManager`, artillery `MissileShotScript`.

## memory

| Class | Members |
|---|---|
| `memory/SessionTransientMemory.java` | Transient `GenericPlugin` key value cache: `getInstance()`, `set(key, obj)` (key must start with `$`, else throws), `get`, `unset`, `contains`, `getSet`, `getString`, `getBoolean` |

`getInstance()` adds the plugin with `addPlugin(plugin, true)` (`memory/SessionTransientMemory.java:21`; `isTransient` overload in `api/campaign/GenericPluginManagerAPI.java:65`), so the cache is rebuilt each session and never saved. Use it for data derived from specs and CSVs. Users: `MiscIE`, `petshop/memory/PetDataRepo`, `surveystories/memory/FileLoader`, `senate/EdictRepo`. `ModPlugin.loadTransientMemory()` (`ModPlugin.java:405`) warms it on game load.

## plugins

Industry option buttons (the "..." menu on an industry) through `IndustryOptionProvider`.

| Class | Members |
|---|---|
| `plugins/SimplifiedIndustryOptionProvider.java` | interface: `isSuitable(ind, allowUnderConstruction)`, `onClick(opt, ui)`, `createTooltip(tooltip, opt)`, `getOptionLabel(ind)` |
| `plugins/BaseSimpleBaseIndustryOptionProvider.java` | base `IndustryOptionProvider`: one option per suitable industry with id `CUSTOM_PLUGIN`, colour `BASE_COLOUR` (150,100,255); overridable `optionEnabled(opt)`, `getOptionColour(opt)` |
| `plugins/SingleIndustrySimpifiedOptionProvider.java` | `isSuitable` = functional industry with `getTargetIndustryId()` on the market of the open dialog |
| `plugins/TagBasedSimplifiedIndustryOptionProvider.java` | `isSuitable` = industry spec tagged `getTargetTag()` on the market of the open dialog |

Subclasses register themselves as listeners from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:425`). Users: Courier Port, Senate (overrides `isSuitable` without the open dialog check, so it also shows on remote colony screens), Warehouses, Military Relay, Museum, derelicts `RemoveIndustryOptionWarningProvider`, [Economy](ECONOMY.md) `RelicBuildTimeReductionButtonAdder`.

## scripts

One shot `EveryFrameScript`s that defer a market change to the next frame (all `runWhilePaused()` true, done after one advance).

| Class | Behaviour | Users |
|---|---|---|
| `scripts/EntityRemovalScript.java` | Removes a custom entity if the market lacks the prerequisite industry or `forceRemoval` | `relay/industry/MilitaryRelay` |
| `scripts/SpriteDistorter.java` | `BaseCustomEntityPlugin` with a grid distortion stub behind `if (true) return;` | none (dead) |

Submarket add and remove has its own script: [Submarkets](SUBMARKETS.md).

## timers

| Class | Members |
|---|---|
| `timers/NewDayListener.java` | interface `onNewDay()` |
| `timers/TimeTracker.java` | Persistent sector script added once in `ModPlugin.setScriptsIfNeeded()` (`ModPlugin.java:499`). Detects a day change from `CampaignClockAPI.getDay()` and calls `onNewDay()` on every `NewDayListener` in the listener manager and in `getAllListeners()`. Also keeps market "time tags" (`ident + days`): `addMarketTimeTagTracker`, `removeMarketTimeTagTracker`, static `getTimeTagPassed`, `marketHasTimeTag`, `getTagNum`, `incrementTagNum`, `removeTimeTag`, `getTimeTrackerInstance()`. Trackers match by market and `ident.equals`, one entry per pair; `ident` is the condition mod id, a new `String` after every load |
| `timers/IntervalTracker.java` | Transient script with shared 1 s and 2 s `IntervalUtil`s, runs while paused: `getInstance()`, `isElapsed1s()`, `getIntervalFraction1s()`, `isElapsed2s()`, `getIntervalFraction2s()`. User: `consumables/itemPlugins/BaseConsumableItemPlugin` |
| `timers/RaidTimeout.java` | Transient `EconomyTickListener`: per system raid cooldown in months. `register()`, `addRaidedSystem(system, months, ai)` (keeps the smaller value), `containsSystem(system, ai)`. Decrements on the last economy tick of the month. Memory keys `$IndEvo_RaidTimeout`, `$IndEvo_RaidTimeout_AI` (sector memory, `Map<String, Float>`). User: [Privateer](../industries/PRIVATEER.md); console: `indevo_printRaidTimeout` |

To get a daily callback, implement `NewDayListener` and register the object as a listener; no own day polling. 26 classes do this (grep `NewDayListener`). Market time tags users: `senate/conditions/BaseEdict`, `Edict_Conscription`, `Edict_ForcedRelocation`.

## trails

Fixed copy of MagicLib's campaign trail plugin (Nicke535, fix by President Matt Damon).

| Class | Members |
|---|---|
| `trails/MagicCampaignTrailPlugin.java` | Persistent sector script, `register()` once (`ModPlugin.java:503`). Static API: `getUniqueID()`, `AddTrailMemberSimple(...)`, `AddTrailMemberAdvanced(...)`, `AddTrailMemberAnimated(...)`, `cutTrailsOnEntity(entity)` (call before teleporting). Builds `org.magiclib.util.MagicTrailObject`s; trails only tick and render in the player's location |
| `trails/MagicCustomCampaignTrailEntityScript.java` | Plugin of custom entity `IndEvo_campaign_trail_custom_entity` (`data/config/custom_entities.json`); forwards `render` to the trail plugin. The plugin moves this entity to the player's location each frame |

Users: artillery projectiles (`RailgunProjectileEntityPlugin`, `MissileSubmunitionEntity`, `MissileCarrierEntityPlugin`), `consumables/entities/BaseMissileEntityPlugin`, WIP `MobileColonyEntityPlugin`.

## update

| Class | Members |
|---|---|
| `update/NewGameIndustryPlacer.java` | `run()` from `ModPlugin.onNewGameAfterEconomyLoad()` (`ModPlugin.java:291`): places IE industries on fixed core world markets if the industry setting is on and the market is not player owned. Market ids and cores: `placeDryDocks` (13 markets), `placeAcademy` (`eochu_bres` beta, `fikenhild`), `placeReqCenter` (`baetis`), `placeSalvageYards` (`donn`, `agreus`, `suddene`), `placeManufactories` (`eochu_bres` with `IndEvo_vpc_IndEvo_parts`), `placePetShops` (`jangala`, `gilead`, `fikenhild`). `placeCourierPorts` is not called |
| `update/ModUpdater.java` | `run()`: removes every script and listener whose class name contains `IndEvo`, re-adds IE submarkets, industries and player market conditions, then runs `NewGameIndustryPlacer`. Not called anywhere (dead) |

## Known issues and dormant code

| Item | State |
|---|---|
| `CirclePathData`, `SpriteDistorter`, `ModUpdater` | Unreferenced |
| `MagicCampaignTrailPlugin.NicToyCustomCampaignTrailObject` | Unused inner class |
| `MiscIE.getStorage` (`helper/MiscIE.java:72`) | Logs every call in dev mode |
| `ModUpdater` | Class name filter `contains("IndEvo")` misses classes without `IndEvo` in the name (package is lowercase `indevo`); restores Academy and hub state onto the removed instance `ind` (`update/ModUpdater.java:302` to `315`) |

## Maintenance

Update this doc when a helper in `indevo/utils/` is added, removed or changes its public members. Feature specific helpers belong in the feature doc; settings belong in [Settings](SETTINGS.md); ModPlugin registration belongs in [Architecture](../ARCHITECTURE.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
