# Achievements

Covers IE's MagicLib achievements: the spec CSV, the script classes, the `Achievements` entry point and the hook in each feature that unlocks one. Read it when an achievement does not unlock, unlocks wrong, or a hooked method moves. It does not cover MagicLib's achievement intel or storage; those are MagicLib's.

Checked against MagicLib 1.5.1 (`lib/MagicLib.jar`, sources under `org/magiclib/achievements/`) and Starsector 0.98.5a-RC7.

## Overview

29 achievements, listed in MagicLib's Achievements intel. Unlocks are stored by MagicLib per install in a common file, not in the save, so they carry across saves. MagicLib's LunaLib setting `magiclib_enableAchievements` turns all of them off; every IE hook is then a no-op. Each feature calls one static method on `Achievements` at the moment the event happens; there is no IE state outside MagicLib except one officer memory key.

## Files

| File | Owner / role |
|---|---|
| `data/config/magic_achievements.csv` | Specs: `id,name,description,tooltip,script,image,spoilerLevel,rarity`. MagicLib loads it from every enabled mod (`MagicAchievementManager.java:40`, `:443`). `tooltip` is required by the loader and left empty. An empty `image` uses MagicLib's default icon. Art made only for an achievement lives in `graphics/icons/achievements/`. |
| `indevo/achievements/Achievements.java` | Ids, goals, targets and the entry points the hooks call (`complete`, `add`, `addKey`, `setTarget`, `addPersonality`, `artilleryHit`, `missileCaught`, `cargoValue`). `get(id)` returns null when achievements are off, the id is not loaded or the achievement is complete (`:168`). |
| `indevo/achievements/plugins/IEAchievement.java` | Script for plain achievements. Loads the image texture (the intel draws it with `addImage`) and sets the max progress from `Achievements.goal`. |
| `indevo/achievements/plugins/IETargetListAchievement.java` | Script for checklists (Collect Them All, Tourist Trap). Sets the targets from `Achievements.targets` on every application load; `setTargets` keeps the done state of existing keys and drops removed ones. |
| `indevo/achievements/plugins/RelicHoardAchievement.java` | Might Need It Later. Reads the player cargo in `advanceAfterInterval`. |
| `indevo/achievements/listeners/AchievementCampaignListener.java` | Transient campaign listener for the two achievements with no IE call site: Visibility Zero (battle) and It Belongs in a Museum (submarket transaction). |

## Data and ids

Ids are `IndEvo_ach_<name>`, constants in `Achievements.java:25` to `:53`. Spoiler levels and rarities are in the CSV.

| Achievement | Id | Unlocks when | Hook |
|---|---|---|---|
| You Monster | `youMonster` | Scuttled ship's pet finds no other ship | `industries/petshop/listener/PetStatusManager.java:622` |
| A Full Life | `fullLife` | Hamster dies of old age | `PetStatusManager.java:724` |
| The Immortal Hampter | `immortalHampter` | Hamster reaches `HAMSTER_DAYS` (5 cycles, about 6.5 hours of play) | `industries/petshop/memory/Pet.java:415` |
| Speedrun | `speedrun` | A brood rolls a hamster young with a life span under 0 (flaw levels cost 3 days each, see [PETS](../industries/PETS.md)) | `industries/petshop/industry/PetShop.java:242` |
| This Is a Warship | `warship` | `WARSHIP_PETS` (10) pets aboard player ships after a place | `Pet.java:126` |
| Priorities | `priorities` | Pet takes the escape pod | `PetStatusManager.java:936` |
| Who's a Good Captain | `goodCaptain` | Winning side has a deployed ship whose captain is its pet | `PetStatusManager.java:950` |
| You Had One Job | `oneJob` | Pet starves | `PetStatusManager.java:729` |
| Minesweeper | `minesweeper` | 25 mine hits on the player fleet (counter) | `exploration/minefields/MineImpact.java:39` |
| Collect Them All | `collectThemAll` | Player fleet hit by railgun, mortar and missile (targets) | `Achievements.artilleryHit`, see [Contracts](#contracts) |
| Danger Close | `dangerClose` | Player fleet hit by a strike the Artillery Remote called | same |
| Dinosaur | `dinosaur` | Meteor hits the player fleet | `exploration/meteor/scripts/MeteorImpact.java:35` |
| No Drilling Crew Needed | `noDrillingCrew` | Player missile catches a meteor | `Achievements.missileCaught`, see [Contracts](#contracts) |
| Visibility Zero | `visibilityZero` | Player battle with a fog lurker fleet (`FogLurkerAssignmentAI.MEM_LURKER`) | `AchievementCampaignListener.java:29` |
| Safety Third | `safetyThird` | Warning beacon dismantled | `dialogue/beacons/dialogue/IndEvo_RemoveCurrentInteractionTarget.java:29` |
| World Peace | `worldPeace` | Privateer Base has no enemies left | `industries/privateer/industry/PrivateerBase.java:172` |
| Feng Shui | `fengShui` | Rift jump done on a player market | `industries/derelicts/scripts/RiftJumpScript.java:87` |
| It's a Feature | `itsAFeature` | Hull Forge prints with `Ids.DEFECTS_HIGH` on a player market | `industries/derelicts/industry/HullForge.java:278` |
| Burnout | `burnout` | VPC burns out in a Commodity Forge | `industries/assembler/industry/CommodityForge.java:163` |
| It Belongs in a Museum | `museum` | Ship with `MUSEUM_DMODS` (5) or more D-mods sold to the museum submarket | `AchievementCampaignListener.java:41` |
| Free Shipping | `freeShipping` | Courier contract costs more than its cargo's base value | `industries/courierport/listeners/Shipment.java:57` |
| Tourist Trap | `touristTrap` | Every World Symbol built on a player market (targets) | `industries/worldwonder/industry/WorldWonder.java:87` |
| Identity Crisis | `identityCrisis` | One officer held all five personalities through Academy training | `industries/academy/industry/Academy.java:712` |
| Flip Flop | `flipFlop` | Edict removed by hand before its minimum runtime | `industries/senate/dialogue/EdictDetail.java:370` |
| Diplomatic Baggage | `diplomaticBaggage` | Ambassador item sold or jettisoned | `industries/embassy/listeners/AmbassadorItemTrackerPlugin.java:145`, `:159`, `:172` |
| Refund Policy | `refundPolicy` | Automated Shipyard returns a base hull that was among the fed ships | `exploration/gacha/GachaStationDialoguePlugin.java:378` |
| Might Need It Later | `relicHoard` | 2000 Relic Components in the player cargo | `RelicHoardAchievement` |
| For Ludd's Sake | `luddsSake` | `?` help opened in 10 different panels (distinct keys) | `dialogue/sidepanel/HelpIcon.java:61` |
| Delegation | `delegation` | Detachment destroyed in a battle without the player | `abilities/splitfleet/fleetManagement/CombatAndDerelictionScript.java:100` |

| Memory key | Where | Holds |
|---|---|---|
| `$IndEvo_ach_personalities` | Officer `PersonAPI` memory, in the save | Comma list of personalities the officer held |
| `IndEvo_keys` | MagicLib achievement memory | Comma list of distinct keys for `addKey` |
| `$IndEvo_ArtilleryRemote_target` | Remote target token memory, expires with the token | Marks an artillery target set by the Artillery Remote |

## Registration and lifecycle

- MagicLib loads the CSV and instantiates each script on application load. IE registers nothing for that.
- `ModPlugin.setListenersIfNeeded` -> `AchievementCampaignListener.register()` (`utils/ModPlugin.java:500`): `addTransientListener`, one instance per load, not saved.
- MagicLib runs `advanceAfterInterval` every 1 to 2 seconds in the campaign, only for achievements that are not complete.

## Contracts

- Hooks call `Achievements` only. Never call `completeAchievement` from feature code; `get(id)` handles disabled, missing and finished achievements.
- Progress counters (`add`) and distinct keys (`addKey`) complete at `Achievements.goal(id)`; the goal also sets the progress bar max in `IEAchievement.onApplicationLoaded`. Change a goal in `GOALS` only, and match the CSV text.
- Artillery hits: the Artillery Remote marks its target token with `$IndEvo_ArtilleryRemote_target` (`items/consumables/itemAbilities/missiles/ArtilleryRemoteAbilityPlugin.java:47`). The shot scripts read it with `isRemoteTarget` and pass a `remote` flag through the projectile params (mortar, railgun, missile carrier, submunition, ECM). Mortar and railgun set `VariableExplosionParams.artilleryType` and `remote`; `VariableExplosionEntityPlugin.applyDamageToFleet` (`industries/artillery/entities/VariableExplosionEntityPlugin.java:119`) reports the player fleet, once per explosion (vanilla `damagedAlready`). Missiles report from the ECM interdict loop (`industries/artillery/projectiles/ECMExplosion.java:124`). Other explosions leave `artilleryType` null.
- Missile meteor catches call `Achievements.missileCaught` right before setting `$missile_bonked` in the base, explosive and concussive missile plugins. Only the player fleet as `source`, a target without `FADING_OUT_AND_EXPIRING` and a `MeteorEntity` that is not already colliding count. The flag format is unchanged ([CONSUMABLES](../items/CONSUMABLES.md)).
- `HelpIcon.panelKey` keys each `?` by the class that built its `Content` lambda, cut at the first `$`. A panel that builds its help content in a shared helper class counts as that helper.
- Identity Crisis stores personalities on the officer, so it survives save and load but is per save.

## Known issues and dormant code

| Item | State |
|---|---|
| Might Need It Later polls | No callback reports a player cargo change. MagicLib's interval runs it every 1 to 2 seconds, one cargo read. |
| For Ludd's Sake | 11 panels have a `?` (12 with RAT), so 10 needs nearly all of them. |
| This Is a Warship | Counts only when a pet is placed; pets already aboard before the update count after the next place. |
| Free Shipping | Courier base fee is 1000, so cheap cargo unlocks it easily. |
| Untested | Not tested in game. |

## Maintenance

Update this doc when an achievement, its hook or a goal changes. Player text lives in the CSV and goes through the Artifact approval first. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
