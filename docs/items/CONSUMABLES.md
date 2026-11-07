# Consumables

Single-use items that fire a hidden ability: nanites, locator, spike, scoop, decoy, spoofer, relay, warning beacon, catapult, and the four campaign missiles plus the artillery remote. Covers the item and ability plugins, the entities and terrain they spawn, targeting input, NPC inventory and where items come from. Read it before adding a consumable or changing one. Artillery stations themselves are in [Artillery](../industries/ARTILLERY.md); beacon rules dialogue in [Beacons](../dialogue/BEACONS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Each consumable is a special item whose `plugin params` names an ability. Using the item (right-click in cargo, or the ability bar slot) adds that ability to the fleet if missing and activates it; activation removes one item. Abilities are `DURATION` abilities tagged `hide_in_codex`, so they only exist to carry cooldown, duration and the effect code. Missiles add a targeting step: a reticule follows the mouse (or a held hotkey), and release fires a `BaseMissileEntityPlugin` entity. NPC fleets get missile inventories but never fire them.

## Files

| File | Owner / role |
|---|---|
| `indevo/items/consumables/ItemAbilityHelper.java` | `ID_MAP` item id <-> ability id; `toggle()` maps either way |
| `itemPlugins/BaseConsumableItemPlugin.java` | Item plugin: ability lookup and add, right-click use, tooltip, cooldown overlay |
| `itemPlugins/SpooferConsumableItemPlugin.java`, `BeaconConsumableItemPlugin.java` | Spoofer faction display; beacon glow (render is dead, see issues) |
| `itemAbilities/SingleUseItemAbility.java` | Interface: `removeTriggerItem`, `getItemID`, `addTooltip` |
| `itemAbilities/BaseConsumableAbilityPlugin.java` | `BaseDurationAbility` base: usable check, item removal through `ItemAbilityHelper.getItemAmount` and `removeItem` (player cargo or NPC inventory) |
| `itemAbilities/*AbilityPlugin.java` | Nanites, Locator (+ `LocatorScanData`), Spike, Scoop, Decoy, Spoofer, Relay, Beacon effects |
| `itemAbilities/missiles/BaseMissileConsumableAbilityPlugin.java` | Missile base: extends the skillshot framework's `BaseSkillshotAbility`, adds the item count, removal (`onConsume`) and item tooltip |
| `itemAbilities/missiles/{Explosive,Concussive,Smoke,Intercept}MissileAbilityPlugin.java` | Pick a reticule (`createReticule`), spawn the matching missile entity (`onSkillshotFired`) |
| `itemAbilities/missiles/ArtilleryRemoteAbilityPlugin.java`, `SurgeAbilityPlugin.java` | Remote artillery targeting; slipstream catapult |
| `entities/*` | Missile entities, `SpikeEntityPlugin`, `DecoyEntityPlugin`, `BeaconEntityPlugin`, `SmokeCloudEntityPlugin`, `PlayerTrackingNebulaParticle` (scoop visuals) |
| `entityAbilities/InterdictionMineAbility.java`, `DecoyMineAbility.java`, `NoWellSlipstreamAbility.java` | Abilities given to spawned entities or temporarily to the player |
| `terrain/SmokeCloudTerrain.java` | Smoke cloud terrain effect |
| `listeners/ConsumableItemMarketAdder.java`, `ConsumableItemDropListener.java` | Item sources: markets and combat loot |
| `listeners/LocatorSystemRatingUpdater.java` | Per-system rating read by the locator |
| `listeners/SpooferItemKeypressListener.java` | Arrow keys cycle the spoofer faction |
| `fleet/FleetConsumableInventoryManager.java`, `MissileMemFlags.java` | NPC missile inventory and memory keys |
| `fleet/ExplosiveMissileAbilityAI.java`, `MissileAIReactionManager.java` | NPC missile AI, switched off |
| `dialogue/BeaconDelegateLaunchpadDialoguePlugin.java`, `BeaconMessageSetterDialogueDelegate.java` | Beacon message input dialog |
| `intel/DeployableWarningBeaconIntel.java` | Intel for player-placed beacons |
| `scripts/DelayedActionScriptRunWhilePaused.java`, `FlickerInstance.java` | Helper scripts |
| `itemAbilities/WIP/*` | Drones, Radar, Stabilizer, Supercharger stubs; excluded in `IndEvo.iml` |

All paths above are under `jars/src/indevo/items/consumables/`. Targeting input and reticules are the skillshot framework in `jars/src/indevo/skillshot/` ([README](../../jars/src/indevo/skillshot/README.md)).

## Data and ids

Items: `data/campaign/special_items.csv:48-61`, plugin `BaseConsumableItemPlugin` (spoofer and beacon use their own), tags `IndEvo_consumable, codex_unlockable`. Abilities: `data/campaign/abilities.csv:5-22`, all `DURATION`, tag `hide_in_codex`, empty `ai` column.

| Item id | Ability id | Price | Ability row |
|---|---|---|---|
| `IndEvo_consumable_nanites` | `IndEvo_ability_nanites` | 7500 | cd 1 |
| `IndEvo_consumable_locator` | `IndEvo_ability_locator` | 5000 | act 0.1, dur 10, cd 1 |
| `IndEvo_consumable_spike` | `IndEvo_ability_spike` | 3500 | cd 0.25 |
| `IndEvo_consumable_scoop` | `IndEvo_ability_scoop` | 12000 | act 0.5, dur 3, cd 1 |
| `IndEvo_consumable_decoy` | `IndEvo_ability_decoy` | 4000 | cd 1 |
| `IndEvo_consumable_spoofer` | `IndEvo_ability_spoofer` | 7500 | dur 4, cd 3 |
| `IndEvo_consumable_relay` | `IndEvo_ability_relay` | 8000 | dur 7, cd 3 |
| `IndEvo_consumable_missile_explosive` | `IndEvo_ability_missile_explosive` | 9000 | cd 3; `IndEvo_skillshot` |
| `IndEvo_consumable_missile_concussive` | `IndEvo_ability_missile_concussive` | 12000 | cd 1; `IndEvo_skillshot` |
| `IndEvo_consumable_missile_smoke` | `IndEvo_ability_missile_smoke` | 10000 | cd 0.15; `IndEvo_skillshot` |
| `IndEvo_consumable_missile_intercept` | `IndEvo_ability_missile_intercept` | 8000 | cd 2; `IndEvo_skillshot` |
| `IndEvo_consumable_artillery_remote` | `IndEvo_ability_artillery_target` | 8500 | cd 3; `IndEvo_skillshot` |
| `IndEvo_consumable_warning_beacon` | `IndEvo_ability_beacon` | 3500 | cd 0.25 |
| `IndEvo_consumable_catapult` | `IndEvo_ability_catapult` | 10000 | cd 3; `IndEvo_skillshot` |

Times in days. `IndEvo_skillshot` enables hotkey targeting; each ability picks its reticule in `createReticule()`: arrow for explosive, intercept and catapult, 400 unit area for concussive and smoke, validated area for the remote.

| Kind | Id / key | Where |
|---|---|---|
| Entity abilities | `IndEvo_ability_mine_interdict` (`InterdictionMineAbility`), `IndEvo_ability_mine_decoy` (`DecoyMineAbility`), temp `IndEvo_ability_temp_generate_slipstream` (`NoWellSlipstreamAbility`) | `abilities.csv:20-22` |
| Entities | `IndEvo_warning_beacon` (`:333`, tag `IndEvo_beacon`), missiles (`:354`, `:365`, `:376`, `:387`), `IndEvo_SmokeCloud` (`:398`), `IndEvo_decoy_mine` (`:502`), `IndEvo_field_spike` (`:995`) | `data/config/custom_entities.json` |
| Entities used, owned elsewhere | `IndEvo_VariableExplosion` (`:461`, [Artillery](../industries/ARTILLERY.md)), `IndEvo_campaign_trail_custom_entity` (`:485`, [Utils](../core/UTILS.md)) | `custom_entities.json` |
| Terrain | `IndEvo_SmokeCloud` (`SmokeCloudTerrain`) | `data/config/terrain.json:30`; description `descriptions.csv:71` |
| Sprites | transpoofer glow, beacon glow; missile sprites | `data/config/settings.json:106-107`, `:135-138` |
| Settings | `IndEvo_consumables_addToMarkets`, `IndEvo_consumables_addToFleets`, `IndEvo_consumables_researchProject`, all under the master switch `IndEvo_block_consumables` | `data/config/LunaSettings.csv:33-35`, `:6` ([Settings](../core/SETTINGS.md)) |
| Drop groups | `indEvo_consumables` (`:38-49`), `indEvo_consumables_always` (`:51-57`), `indEvo_missiles` (`:59-64`), `indEvo_missiles_always` (`:66-70`); tag rows `indEvo_tech_addition` (`:12`), `indEvo_mining_addition` (`:18`) | `data/campaign/procgen/drop_groups.csv` |
| Memory (fleet) | `$IndEvo_MissileInventory`, `MissileMemFlags` keys (`$missile_allowedToUse`, `$missile_bonked`, `$missile_recentlyUsed` and more) | `FleetConsumableInventoryManager`, `MissileMemFlags` |
| Memory (player) | `$IndEvo_consumable_currentFaction`, `$IndEvo_consumable_currentFactionList` (spoofer); `$IndEvo_ArtilleryRemote_hasBeenUsed` | `SpooferAbilityPlugin`, `ArtilleryRemoteAbilityPlugin` |
| Memory (system) | `$IndEvo_locatorSystemRating` | `LocatorSystemRatingUpdater` |

## Registration and lifecycle

| Hook | What |
|---|---|
| `ModPlugin.onAboutToStartGeneratingCodex` (`ModPlugin.java:133`) | Loads the nanites texture for the codex |
| `ModPlugin.java:167` (`onGameLoad`), `:289` (`onNewGameAfterEconomyLoad`) | `LocatorSystemRatingUpdater.updateAllSystems()` |
| `ModPlugin.java:441` | `LocatorSystemRatingUpdater`, transient |
| `ModPlugin.java:442` | `ConsumableItemDropListener`, persistent `ShowLootListener`, added only if absent |
| `ModPlugin.java:443` | `ConsumableItemMarketAdder`, transient |
| `ModPlugin.java:446` | `SpooferItemKeypressListener`, transient |
| `ModPlugin.java:472`, `:315` (`beforeGameSave`) | `SkillshotFramework.register()` (transient hotkey listener and activation manager, clears the cross mod lock); `SkillshotFramework.reset()` before save |
| `ModPlugin.java:473` | `FleetConsumableInventoryManager`, transient `FleetSpawnListener` |

`ListenerManager.addListener(Object)` is the persistent variant (`obf/campaign/ListenerManager.java:25-33` in the starsector-knowledge skill), so any listener added without `true` is saved with the game.

Abilities are added lazily: `BaseConsumableItemPlugin.getAbilityPlugin()` adds the ability to the player fleet and character data the first time it is needed (a "Codex Fleet" stands in when there is no player fleet).

## Flow

Non-missile use: right-click works in the cargo tab with no dialog open, only if `isUsable`. `activate()` runs `super.activate()` then `removeTriggerItem()`, which removes one item from player cargo or, for NPCs, calls `FleetConsumableInventoryManager.adjustInventoryAmount`. `isUsable` needs at least one item.

Missile targeting is the skillshot framework ([README](../../jars/src/indevo/skillshot/README.md#how-it-works)): click the ability and click the map, or hold its hotkey (1 to 9) and release. Firing runs `forceActivation()`: `onSkillshotFired(target, angle)`, `onConsume()` (removes one item) and the cooldown. Missile `isUsable` also needs an item, no live targeting session (in IE or a mod holding the [cross mod lock](../../jars/src/indevo/skillshot/README.md#cross-mod-lock)) and no open dialog or core tab. A slot ability tagged `IndEvo_skillshot` whose plugin is not a `SkillshotAbility` throws (`IllegalStateException`) on its hotkey, since only an IE bug gets there.

Missile entities (`BaseMissileEntityPlugin`): fly straight for 4 s, explode on `shouldExplode`, timeout, or reaching the target point (AOE types aim at the cursor). Entities within radius + 50 along the path get `$missile_bonked`; `MeteorEntity.java:71-72` reads it ([Meteor](../exploration/METEOR.md)). Trails use `MagicCampaignTrailPlugin`.

| Type | Speed | Effect |
|---|---|---|
| Explosive | 1000 | Hits a fleet within 10 + fleet radius; spawns `IndEvo_VariableExplosion` size 250, damage LOW, sound `IndEvo_mortar_hit` |
| Concussive | 2000, AOE | Explodes within 25 of target; vanilla `ShoveFleetScript` on fleets within 500, strength 2 times falloff, halved for the player; no damage |
| Smoke | 750, AOE | Spawns `IndEvo_SmokeCloud` entity (360 to 400 radius, 4 days), which adds the terrain: detected range x0.5, burn x`1 - 0.3*fleetRadiusMult` (min 0.1) |
| Intercept | 2000 | Every fleet within 300 except the firing fleet: `GoSlowScript` 2 s, sensor profile +1000 for 20 s with glow, interdict 6 s (turns off burn abilities and interdiction pulse, sets burn cooldown) |
| Remote | none | Needs system tag `IndEvo_SystemHasArtillery` and the fleet inside `ArtilleryTerrain` with a live `CampaignAttackScript`; calls `forceTarget` on a token at the cursor. Its reticule (`MarketProximityValidator`) rejects points within `INHABITED_AREA_SAFE_RADIUS` (700) plus entity radius of a market |
| Catapult | none | Hyperspace, not abyss. Adds the temp ability, `activateAtAngle`, removes it; `NoWellSlipstreamAbility` builds a slipstream (burn about 900) and calls vanilla `deductCost` (CR and fuel) |

Other effects:

- Nanites: 25000 hull and armor budget, flagship first, then officer ships, then the rest; +20% CR up to 50%. Uses obf `FleetMemberStatus` (`DoNotObfuscate`, `obf/campaign/fleet/FleetMemberStatus.java:23`).
- Locator: hyperspace only, 10 days, detected range +50%. Rings drawn from `LocatorScanData` with vanilla `GraviticScanData.GSPing`; strength from `$IndEvo_locatorSystemRating`, x(10000/dist) under 10000.
- Spike: drops `IndEvo_field_spike`, arms after 2 s, fires when a non-player fleet comes within 350. `InterdictionMineAbility`: range 600, interdict `6 + (800 - sensorStrength)/200` s, hits the player too; `IPReactionScript` makes NPCs avoid it.
- Decoy: drops `IndEvo_decoy_mine`, arms after 10 s, `DecoyMineAbility` sets it to the player faction and pings. Artillery in the location targets it for 20 s; fleets within 3000 that are not patrol, war or pirate, not busy, pass a 90% roll get INTERCEPT and GO_TO_LOCATION near it for 5 to 10 days.
- Scoop: not in hyperspace; star within 3000 or a nebula tile (`OrbitFocus.getClosestNebulaTilePosition`, [Split fleet](../abilities/SPLIT_FLEET.md)). 3 days, fuel every 1 s of `1000/(dur*10)` times a star type or age mult; sound `IndEvo_succ`.
- Relay: hyperspace only; `Tags.COMM_RELAY` on the player fleet for 7 days or until leaving hyperspace.
- Spoofer: sets the fleet faction to the picked one and turns the transponder on. Ends if the transponder goes off or a non-station fleet of another faction comes within radius + 100. Reverts the faction while a market is open (persistent listener while active).
- Beacon: hyperspace only. From the cargo tab, `showInteractionDialogFromCargo` opens `BeaconDelegateLaunchpadDialoguePlugin` over the cargo screen; otherwise a one-shot script retries `showInteractionDialog` until it opens. The dialog shows `BeaconMessageSetterDialogueDelegate` (cancel gives an empty message). `spawnBeacon` places `IndEvo_warning_beacon`, orbiting the nearest jump point within 400, and adds `DeployableWarningBeaconIntel`.

Sources:

- `ConsumableItemMarketAdder`, on `reportPlayerOpenedMarketAndCargoUpdated` when `getSinceSWUpdate() == 0`. Open or black market with luxury goods legal, or `IndEvo_ReqCenterMarket` ([Small industries](../industries/SMALL_INDUSTRIES.md)): 8 rolls, nanites, scoop and beacon 10; independents add locator, relay, catapult 5; pirates add decoy, spoofer 5; nothing 250. Military or requisitions (not pirates, Luddic Path, Luddic Church): spike 10, explosive 5, concussive, intercept and smoke 15, nothing 100 (50 for requisitions).
- `ConsumableItemDropListener`: setting first; artillery station targets (`ArtilleryStationScript.ARTILLERY_KEY`) add one remote; fleets not blacklisted (pirates, Luddic Path, omega, dweller, threat) give `floor(fpDestroyed/70)` rolls on `indEvo_consumables`, x1.2 if enemy FP is over 1.5x the player's.
- Salvage drop groups on IE stations ([Stations](../exploration/STATIONS.md)); research project `PROJ_MJOLNIR` gives 5 rolls of `indEvo_missiles_always` (`ResearchProjectTemplateRepo.java:518`, [Research](../dialogue/RESEARCH.md)); rule `IndEvo_roiderTutorial_acceptOffer` (`rules.csv:40`) gives a concussive missile.

NPCs: `FleetConsumableInventoryManager` gives spawned fleets the missile abilities and `$IndEvo_MissileInventory` with `ceil(FP/threshold)` per type when FP exceeds the threshold (explosive 100, intercept 50, concussive 70, smoke 40), and sets `$missile_allowedToUse` if the fleet cares about transponders.

## Contracts

- `ID_MAP`, the CSV `plugin params` column and the ability ids must agree; `toggle()` is the only lookup.
- Ability ids live in saves once added to the player fleet. Renaming one leaves a dead ability on old saves (inferred).
- New missiles: extend `BaseMissileConsumableAbilityPlugin`, implement `createReticule()` and `onSkillshotFired()`, tag the ability row `IndEvo_skillshot`, add a `BaseMissileEntityPlugin` entity.
- `$missile_bonked` is shared with [Meteor](../exploration/METEOR.md); `forceTarget` and `ArtilleryTerrain` with [Artillery](../industries/ARTILLERY.md).
- `LocatorSystemRatingUpdater.salvageEntityMap` rates `IndEvo_arsenalStation` 10, `IndEvo_orbitalLaboratory` 20, `IndEvo_GachaStation` 20; ruins conditions 3/5/7/10, `IndEvo_RuinsCondition` and `IndEvo_RuinedInfra` +2, debris +2, populated markets +10 x size.
- The beacon entity tag `IndEvo_beacon` hooks the rules dialogue in [Beacons](../dialogue/BEACONS.md).

## Known issues and dormant code

| Item | State |
|---|---|
| NPC missile AI | `ExplosiveMissileAbilityAI` unreferenced, `if(true) return` at `:59`; `MissileAIReactionManager.reportFleetUsedMissile` `if (true) return` at `:68`; `reportFleetHitByMissile` is a stub. NPC inventories are never used |
| `ItemAbilityHelper.getItemAmount` | NPC fleets always read 0 (the NPC inventory is never asked), so NPC `isUsable` is false |
| `InterceptMissileEntityPlugin.java:105-106` | Sets `angle` instead of `currentAngle`; harmless, the missile is finishing after `onExplosion` |
| `SpooferAbilityPlugin.java:93` | Render dead (`\|\| true`) |
| `SpooferItemKeypressListener` | Left calls `nextFaction`, right `prevFaction`; reacts to arrows whenever no dialog is open |
| `BeaconConsumableItemPlugin.java:14` | Render returns at once |
| `BeaconEntityPlugin.java:45-50` | Catches an NPE to re-init |
| `BeaconAbilityPlugin` | Removes the item on activate, before the message dialog |
| `DecoyEntityPlugin` | Adds and activates the ability every frame while FIRING |
| `ItemAbilityHelper.ID_MAP` | Maps drones, stabilizer, supercharger, which have no CSV rows |
| `itemAbilities/WIP/*` | Excluded in `IndEvo.iml`; stubs with a copied nanite tooltip |
| `custom_entities.json:485` | Trail entity uses `SpikeEntityPlugin` as its plugin class |
| Changelog_todo | Missile NPC usage and AI (`:74-76`), remote buff (`:62`), beacon hazard level (`:112`), missile NPC reactions (`:84`), eye-bots (`:97`), "look bigger" item idea (`:137`) |

## Maintenance

Update this doc when an item, ability row, entity, drop group, targeting input or source weight changes. Artillery behaviour goes in [Artillery](../industries/ARTILLERY.md), beacon dialogue in [Beacons](../dialogue/BEACONS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
