# Architecture

Overview of the current implementation: areas, the feature docs, registration and lifecycle, save data, data layout and the contracts that cross features. Code paths are relative to `jars/src/`; data paths are repository-relative. Feature internals live in the feature docs; this file routes to them. Search the named owner before adding another implementation.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [SETTINGS.md](core/SETTINGS.md) | Settings reader, LunaLib tabs, `settings.json`, whitelists |
| [SETTINGS_REWORK.md](core/SETTINGS_REWORK.md) | Approved move to a typed, cached settings model with Quick Settings; not implemented |
| [UTILS.md](core/UTILS.md) | Shared helpers in `indevo/utils` |
| [COMPATIBILITY.md](core/COMPATIBILITY.md) | Hard and optional mod dependencies and every integration |
| [RULE_COMMANDS.md](dialogue/RULE_COMMANDS.md) | `rules.csv` layout and every rule command class |
| [RELEASE.md](RELEASE.md) | Versions, build, release steps |
| [CHANGELOG.md](CHANGELOG.md) | Change-note format for every implementation task |

## Feature documents

One doc per feature. Every IE industry id is mapped to its class and doc in the [industry index](industries/SMALL_INDUSTRIES.md).

| Area | Doc | Feature | Package |
|---|---|---|---|
| Industries | [ACADEMY](industries/ACADEMY.md) | Officer and admin training, Galatia Academy market | `industries/academy` |
| | [ARTILLERY](industries/ARTILLERY.md) | Artillery stations, watchtowers, campaign projectiles | `industries/artillery` |
| | [ASSEMBLER](industries/ASSEMBLER.md) | Variable manufactories and the commodity forge (VPC items) | `industries/assembler` |
| | [CHANGELING](industries/CHANGELING.md) | Switchable population (governments), mining and refining | `industries/changeling` |
| | [COURIER_PORT](industries/COURIER_PORT.md) | Shipping contracts between colonies | `industries/courierport` |
| | [DERELICTS](industries/DERELICTS.md) | Ruins, ancient lab, hull deconstructor, hull forge, rift generator, forge templates | `industries/derelicts` |
| | [EMBASSY](industries/EMBASSY.md) | Ambassadors and faction relations | `industries/embassy` |
| | [ENGINEERING_HUB](industries/ENGINEERING_HUB.md) | Reverse engineering of hulls | `industries/engineeringhub` |
| | [MUSEUM](industries/MUSEUM.md) | Ship exhibits, parade fleets | `industries/museum` |
| | [PETS](industries/PETS.md) | Pet shop, pets, pet management ability | `industries/petshop`, `abilities/pets` |
| | [PRIVATEER](industries/PRIVATEER.md) | Privateer base raids | `industries/privateer` |
| | [RELAY](industries/RELAY.md) | Military and interstellar relays, fleet size sharing | `industries/relay` |
| | [RUINED_INFRASTRUCTURE](industries/RUINED_INFRASTRUCTURE.md) | Ruined infrastructure conditions and restoration | `industries/ruinfra` |
| | [SALVAGE_YARDS](industries/SALVAGE_YARDS.md) | Salvage yards, custom production, D-mod transfer | `industries/salvageyards` |
| | [SENATE](industries/SENATE.md) | Senate and edicts | `industries/senate` |
| | [SMALL_INDUSTRIES](industries/SMALL_INDUSTRIES.md) | Industry index; bureau, supercomputer, restoration docks, requisitions center, commerce balance patches, shield, shared storage | `industries/*.java` |
| | [WAREHOUSES](industries/WAREHOUSES.md) | Configurable extra storages | `industries/warehouses` |
| | [WORLD_WONDER](industries/WORLD_WONDER.md) | World wonders | `industries/worldwonder` |
| Exploration | [CRUCIBLE](exploration/CRUCIBLE.md) | Crucible and catapults (dormant) | `exploration/crucible` |
| | [DISTRESS](exploration/DISTRESS.md) | Ghost ship distress calls (excluded, does not compile) | `exploration/distress` |
| | [GACHA](exploration/GACHA.md) | Automated shipyard stations | `exploration/gacha` |
| | [METEOR](exploration/METEOR.md) | Meteor swarms and treasure | `exploration/meteor` |
| | [MINEFIELDS](exploration/MINEFIELDS.md) | Mine belts and mine fields | `exploration/minefields` |
| | [SALVAGE](exploration/SALVAGE.md) | Salvage specials | `exploration/salvage` |
| | [STATIONS](exploration/STATIONS.md) | Derelict lab, arsenal and pet center stations | `exploration/stations` |
| | [SURVEY_STORIES](exploration/SURVEY_STORIES.md) | Survey story texts | `exploration/surveystories` |
| Abilities | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) | Detachments | `abilities/splitfleet` |
| | [SKILLS](abilities/SKILLS.md) | IE skills, remote industry item management | `abilities/skills` |
| Items | [CONSUMABLES](items/CONSUMABLES.md) | Consumable items and their abilities, missiles | `items/consumables` |
| | [INSTALLABLES](items/INSTALLABLES.md) | Installable special items, item effects, loot listeners, forge template and VPC item plugins | `items/*` (rest) |
| Dialogue | [RESEARCH](dialogue/RESEARCH.md) | Research projects | `dialogue/research` |
| | [BEACONS](dialogue/BEACONS.md) | Warning beacon dismantling | `dialogue/beacons` |
| | [SIDE_PANEL](dialogue/SIDE_PANEL.md) | Custom side panels in dialogs (UI) | `dialogue/sidepanel` |
| | [RULE_COMMANDS](dialogue/RULE_COMMANDS.md) | Generic rule commands, `rules.csv` map | `dialogue/rules` |
| Core | [ECONOMY](core/ECONOMY.md) | Resource condition, parts, relic build time reduction | `economy` |
| | [SUBMARKETS](core/SUBMARKETS.md) | Submarket plugins, adding and removing submarkets | `submarkets` |
| | [COMBAT](core/COMBAT.md) | Weapon scripts, missile AI, combat terrain | `data/scripts` |
| | [CONSOLE](core/CONSOLE.md) | Console Commands | `console` |
| | [MISC](core/MISC.md) | Unregistered leftovers | `other` |
| | [WIP](core/WIP.md) | Mobile colony, converters | `WIP` |

## Start here

| Change / symptom | Route |
|---|---|
| Anything that happens once per day | `TimeTracker.advance -> onNewDay -> NewDayListener.onNewDay` ([UTILS](core/UTILS.md)) |
| Something missing after load, or registered twice | `ModPlugin.onGameLoad -> setListenersIfNeeded / setScriptsIfNeeded` ([below](#registration-and-lifecycle)) |
| Content missing in a new game (ruins, artillery, stations, minefields, gacha) | `ModPlugin.onNewGameAfterEconomyLoad` chain ([below](#registration-and-lifecycle)) |
| An industry not buildable | Industry `isAvailableToBuild()` -> its `Settings` toggle ([SETTINGS](core/SETTINGS.md)) -> feature doc |
| A colony option or menu missing | `*OptionProvider.register()` -> rules `IndEvo_FireBase*OptionList` ([RULE_COMMANDS](dialogue/RULE_COMMANDS.md)) |
| A special item does nothing | `SpecialItemEffectsRepo.ITEM_EFFECTS` or the industry's `getInstallableItems()` ([INSTALLABLES](items/INSTALLABLES.md)); consumables: `ItemAbilityHelper.ID_MAP` ([CONSUMABLES](items/CONSUMABLES.md)) |
| A storage or submarket wrong or not removed | `SubMarketAddOrRemovePlugin` ([SUBMARKETS](core/SUBMARKETS.md)) -> feature doc |
| Parts, rare parts, supercomputer income | `ResourceConditionApplicator -> ResourceCondition.apply` ([ECONOMY](core/ECONOMY.md)) |
| A ruin turns into the wrong structure | `$IndEvo_ruinsIndustryId` -> `RuinsManager` ([DERELICTS](industries/DERELICTS.md)) |
| `getIndustry("population" / "mining" / "refining")` returns an IE class | Switchables return the vanilla id ([contract](#industries-and-markets)) |
| Detachment AI | `Behaviour.changeBehaviourAndUpdateAI -> BaseSplinterFleetAssignmentAIV2` ([SPLIT_FLEET](abilities/SPLIT_FLEET.md)) |
| Artillery not firing | `CampaignAttackScript -> EyeIndicatorScript / WatchtowerEntityPlugin -> ArtilleryStationScript` ([ARTILLERY](industries/ARTILLERY.md)) |
| Meteor shower spawn | `MeteorSwarmManager.reportEconomyTick -> spawnShower -> <Type>SwarmSpawner` ([METEOR](exploration/METEOR.md)) |
| Dialogue or rules row | `rules.csv` block map -> command class ([RULE_COMMANDS](dialogue/RULE_COMMANDS.md)) |

## Registration and lifecycle

`mod_info.json`: id `IndEvo`, plugin `indevo.utils.ModPlugin`, jar `jars/IndEvo.jar`, hard dependencies LazyLib, MagicLib, LunaLib, GraphicsLib, PatchLib. Hook order and what exists at each hook: knowledge skill `docs/concepts/lifecycle.md`.

| Hook | What IE does | Line |
|---|---|---|
| `onApplicationLoad()` | GraphicsLib `ShaderLib.init()`, light and texture CSVs from `data/lights/`; `SpecialItemEffectsRepo.addItemEffectsToVanillaRepo()` | `utils/ModPlugin.java:116` |
| `onAboutToStartGeneratingCodex()` | Preloads the nanites icon texture | `:129` |
| `onNewGameAfterEconomyLoad()` | One-time world generation (chain below) | `:297` |
| `onGameLoad(newGame)` | Every-load registration and fixes (chain below) | `:143` |
| `beforeGameSave()` | Resets `OnKeyPressAbilityInputListener`; removes untouched mine entities from their locations | `:324` |
| `afterGameSave()`, `onGameSaveFailed()` | Puts the removed mines back | `:349`, `:354` |
| `pickMissileAI()` | `IndEvo_mortarProjectileAI`, `IndEvo_missileProjectileAI` at `MOD_SPECIFIC` ([COMBAT](core/COMBAT.md)) | `:384` |

New game, once (`:300` to `:316`):

`LocatorSystemRatingUpdater.updateAllSystems -> RuinsManager.forceCleanCoreRuins -> NewGameIndustryPlacer.run -> ArtilleryStationPlacer.placeDerelictArtilleries -> placeCoreWorldArtilleries -> GachaStationSpawner.spawn -> createAcademyMarket -> [IndEvo_Enable_Derelicts] DerelictStationPlacer.init -> IndEvo_SalvageSpecialAssigner.init -> spawnMineFields -> DerelictInfrastructurePlacer.placeRuinedInfrastructure -> RuinsManager.placeIndustrialRuins`

Every load (`:149` to `:190`):

`createAcademyMarket -> setListenersIfNeeded -> setScriptsIfNeeded -> ResourceConditionApplicator.applyResourceConditionToAllMarkets -> loadTransientMemory -> OrbitFocus.removeUnusedOrbitFoci -> addTypePrefaceToIndustrySpecs -> LocatorSystemRatingUpdater.updateAllSystems -> resetDerelictRep -> [newGame] ArtilleryStationReplacer.register -> pet_management ability add or remove [IndEvo_pets] -> MissileActivationManager reset`

### Registration patterns

| Pattern | Meaning | Example |
|---|---|---|
| `X.register()` | `getListenerManager().addListener(new X(), true)`: transient, added on every load, not saved | `DepositMessage.register()` |
| `X.getInstanceOrRegister()` | Returns the saved instance from `getListeners(X.class)` or adds a persistent one | `ShippingManager`, `AdminGovernTimeTracker`; `RelayNetworkBrain` is the transient variant, see below |
| `registerPlugin(new XCampaignPlugin())` | Campaign plugin; all five IE plugins are transient (`BaseCampaignPlugin.isTransient()` returns true) | `setScriptsIfNeeded()` `:503` to `:507` |
| `@Patch` class | PatchLib finds it in `jars/IndEvo.jar` at startup, no registration. A hook installs when its target class loads | [Patches](#patches) |
| `sector.addScript` guarded by `hasScript` | Saved script, added once | `TimeTracker` `:509` |
| Industry adds itself in `apply()` | `addListener(this)` from the industry; the listener manager skips an instance already present (obf `ObjectRepository.java:87`, per the knowledge skill) | Assembler, engineering hub |
| CSV plugin column | Industries, conditions, abilities, items, entities, terrain, hullmods, weapons | `data/campaign/*.csv`, `custom_entities.json`, `terrain.json` |
| `ruleCommandPackages` | Rule commands found by package | `settings.json:236` ([RULE_COMMANDS](dialogue/RULE_COMMANDS.md)) |

`setListenersIfNeeded()` (`:436` to `:499`) by feature: item effects and loot (`SpecialItemEffectsRepo.initEffectListeners`, `ShipComponentLootManager`, `SpecialItemDropsListener`, `ConsumableItemDropListener`, `ConsumableItemMarketAdder`), economy (`ResourceConditionApplicator`, `RelicBuildTimeReductionButtonAdder`), assembler (`DepositMessage`), embassy (`AmbassadorPersonManager`, `HostileActivityEventSubRegisterListener`), privateer and raids (`RaidTimeout`), derelicts (`RuinsManager.*`, `AncientLabCommoditySwitchOptionProvider`, `RiftGenOptionProvider`, `RemoveIndustryOptionWarningProvider`), courier (`ShippingManager`, `CourierPortOptionProvider`), split fleet (`DetachmentAbilityAdder`), consumables (`LocatorSystemRatingUpdater`, `SpooferItemKeypressListener`, `OnKeyPressAbilityInputListener`, `FleetConsumableInventoryManager`, `MissileActivationManager`), minefields (`RecentJumpListener`, `InterdictionPulseAbilityListener`), world wonder, changeling, pets, ruinfra, artillery (`WatchtowerFactionResetListener`), research unlock checkers, skills, salvage yards, senate, academy, beacons, Cloud Painter shield visuals, relay (`MilitaryRelay.RelayItemRemovalButtonListener`, `RelayNetworkBrain`), meteor (`MeteorSwarmManager`), warehouses, museum. Nexerelin only: `ManagedDemocracyNexerelinListenerPlugin`. Commented out: `DistressCallManager`, `HullmodTimeTracker`.

When adding a listener, add its `register()` call here in the feature's group and list it in the feature doc's Registration section.

### Patches

PatchLib patches change vanilla code where the API has no hook. Each lives in a `patches` package of the feature that owns it; the feature doc describes the behaviour. Rules for writing them: the `patchlib` skill. Never target `BaseIndustry`: PatchLib 1.0.3 notes that patching it breaks with Ashes of the Domain.

| Patch | Target | Kind | Feature doc |
|---|---|---|---|
| `industries/artillery/patches/WatchtowerStableLocationPatch` | `Misc.getNumStableLocations` (API) | `@After` | [ARTILLERY](industries/ARTILLERY.md#stable-locations) |
| `industries/privateer/patches/PrivateerIndustrySlotPatch` | `Misc.getNumIndustries` (API) | `@After` | [PRIVATEER](industries/PRIVATEER.md#contracts) |
| `industries/patches/CommerceBalancePatch` | vanilla `TradeCenter` (API): six methods, `IMPROVE_BONUS` reads | `@Before`, `@After`, `@RedirectFieldRead` | [SMALL_INDUSTRIES](industries/SMALL_INDUSTRIES.md#commerce-balance-patches-commerce) |
| `abilities/skills/patches/MicromanagementRemoteItemPatch` | `StarfarerSettings` `(String) -> boolean` call in the industry option menu constructor (obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred) | `@RedirectMethodCall` | [SKILLS](abilities/SKILLS.md#flow) |
| `industries/engineeringhub/patches/RelicInstallDialogPatch` | install item dialog (obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred): `getStacksCopy` in its `(CargoAPI, List, String)` collector, `CargoAPI.addItems` calls | `@RedirectMethodCall` | [ENGINEERING_HUB](industries/ENGINEERING_HUB.md#relic-components-in-the-install-dialog) |
| `industries/patches/DealmakerHolosuitePatch` | `ItemEffectsRepo.DEALMAKER_INCOME_PERCENT_BONUS` reads in `BaseInstallableItemEffect` subclasses (API) | `@RedirectFieldRead` | [SMALL_INDUSTRIES](industries/SMALL_INDUSTRIES.md#commerce-balance-patches-commerce) |
| `industries/worldwonder/patches/CloudPainterShieldPatch` | `applyVisuals` call in `PlanetaryShield.apply` (API) | `@RedirectMethodCall` | [WORLD_WONDER](industries/WORLD_WONDER.md#cloud-painter-shield-visuals) |

After a game update, check inferred targets first (any target outside `com.fs.starfarer.api`). In game, `starsector.log` shows `[PatchLib] Discovered patch ...` at startup and `Installed a ... patch site ...` when the target loads; Console Commands `TogglePatch <class>` switches one off for a test.

## Save data

IE is released and players keep saves across patch versions. 4.1.c does not load 4.1.b saves (`Changelog` note), so IE carries no migration code for them. Starsector saves the sector with XStream; what persists is in the knowledge skill `docs/concepts/saves-persistence.md`.

- Saved: fields of persistent listeners (`getInstanceOrRegister`), saved scripts (`TimeTracker`, feature scripts added with `addScript`), intel, industries and conditions on markets, custom entities and their plugins, memory values, tags.
- Not saved: transient listeners (`register()`), campaign plugins, `SessionTransientMemory` (whitelist sets, VPC item set).
- Renaming or moving a saved class, or changing the type of a saved field, breaks old saves. Changeling government variants are anonymous classes in static lists; reordering those lists breaks saves (inferred, see [CHANGELING](industries/CHANGELING.md)).
- `TimeTracker` stores time passed in market tags (`<ident><days>`), so those tags are save data.
- `ModPlugin.beforeGameSave()` strips untouched `IndEvo_Mine` entities to keep saves small and restores them in `afterGameSave()`. `restoreRemovedEntities()` returns early when `IndEvo_Enable_minefields` is off, which leaves any stripped mines out ([MINEFIELDS](exploration/MINEFIELDS.md)).

## Data layout

| Path | Content | Owner |
|---|---|---|
| `data/campaign/industries.csv` | IE industries | [Industry index](industries/SMALL_INDUSTRIES.md) |
| `data/campaign/market_conditions.csv` | Edicts, privateer subpopulation, ruins, parade, minefield, artillery, cryorevival disabler, resource, relay, ruined infra, world wonder | Feature docs |
| `data/campaign/special_items.csv`, `commodities.csv` | Installable and consumable items, parts | [INSTALLABLES](items/INSTALLABLES.md), [CONSUMABLES](items/CONSUMABLES.md), [ECONOMY](core/ECONOMY.md) |
| `data/campaign/abilities.csv` | Detachments, consumable abilities, pet management | [SPLIT_FLEET](abilities/SPLIT_FLEET.md), [CONSUMABLES](items/CONSUMABLES.md), [PETS](industries/PETS.md) |
| `data/campaign/rules.csv` | All dialogue | [RULE_COMMANDS](dialogue/RULE_COMMANDS.md) has the block map |
| `data/campaign/submarkets.csv` | IE submarkets | [SUBMARKETS](core/SUBMARKETS.md) |
| `data/campaign/pets.csv` | Pet species | [PETS](industries/PETS.md) |
| `data/campaign/stories.csv` | Survey stories (`stories_TODO.csv` unread) | [SURVEY_STORIES](exploration/SURVEY_STORIES.md) |
| `data/campaign/terrain.json`, `data/config/custom_entities.json` | Terrain and entity plugins | Feature docs |
| `data/campaign/procgen/` | Salvage entity gen, drop groups (`drop_groups_old.csv` legacy) | [STATIONS](exploration/STATIONS.md), [CONSUMABLES](items/CONSUMABLES.md) |
| `data/hulls`, `data/variants`, `data/weapons` | Artillery stations, Brimir, campaign projectiles | [ARTILLERY](industries/ARTILLERY.md), [COMBAT](core/COMBAT.md) |
| `data/hullmods/hull_mods.csv` | Pet, printing defect, changeling, ghost ship hullmods | Feature docs |
| `data/characters/skills` | IE skills | [SKILLS](abilities/SKILLS.md) |
| `data/config/settings.json`, `LunaSettings.csv`, `indEvo/*.csv` | Config and settings | [SETTINGS](core/SETTINGS.md) |
| `data/config/exerelinFactionConfig`, `vayraBounties`, `prism`, `BetterColonyConfig`, `modFiles`, `version` | Other mods' config | [COMPATIBILITY](core/COMPATIBILITY.md) |
| `data/console/commands.csv` | Console commands | [CONSOLE](core/CONSOLE.md) |
| `data/lights`, `data/shaders` | GraphicsLib lights, IE shaders | [COMBAT](core/COMBAT.md), [UTILS](core/UTILS.md) |
| `data/strings` | Descriptions, tips, name lists | Feature docs |
| `data/world/factions` | `IndEvo_derelict` ([ARTILLERY](industries/ARTILLERY.md)), `IndEvo_couriers` ([COURIER_PORT](industries/COURIER_PORT.md)), `IndEvo_converters` ([WIP](core/WIP.md)) | Feature docs |

### Ids

- Shared ids are in `indevo/ids/Ids.java` (factions, whitelist paths, tags, submarkets, industries, conditions, hullmods, entities, skills, abilities, research projects) and `ItemIds.java` (special items, commodities). Feature-local keys stay in the feature.
- Prefixes: `IndEvo_` for ids and tags, `$IndEvo_` for memory keys. Older content uses other prefixes that are now stable ids and must not be renamed: `indevo_` (skills, research projects), `indEvo_` (ghost ship hullmods, drop groups), `splinter_fleet`, `pet_management`, `$oldstory_`, `$splinterFleet...`.
- `Ids.SKILL_*` constants do not match the skill files and are unused ([SKILLS](abilities/SKILLS.md)).

## Build

- IntelliJ module `IndEvo.iml`: source root `jars/src`, JDK 17, output `jars/` (`.idea/misc.xml`), artifact `IndEvo:jar` writes `jars/IndEvo.jar` (gitignored). Libraries are IntelliJ application libraries; the same jars for agent builds are in `lib/`. Full dependency list and gate: [CLAUDE.md](../CLAUDE.md#building).
- Release builder `indevo/tools/ReleaseBuild.java` (run as a single-file program, never shipped): `compile` is the agent compile gate, `build` writes the committed release jar `jars/release/IndEvo.jar`, `package` writes the release zip. It compiles the same files as the IntelliJ module by reading its `excludeFolder` entries. [RELEASE.md](RELEASE.md).
- Java only, no Kotlin ([CLAUDE.md](../CLAUDE.md#code-layout)).
- `IndEvo.iml` `excludeFolder` entries that exist: `WIP/converters`, `exploration/WIP`, `exploration/distress`, `exploration/salvage/specials/WIP`, `items/consumables/itemAbilities/WIP`, `industries/academy/rules/WIP`, `industries/derelicts/industry/WIP`, `industries/warehouses/plugin/wi`. The others point at old paths. Excluded code does not compile and must not be referenced. The release builder reads the same entries, so change exclusions in `IndEvo.iml` only.
- IE imports obfuscated game classes in a few places (`com.fs.starfarer.campaign`, `com.fs.starfarer.combat.CombatEngine`, `com.fs.graphics` and `com.fs.state.AppDriver`). Those break on game updates; check them first after one.
- `jars/src/data/scripts` holds combat classes in the vanilla `data.scripts` package ([COMBAT](core/COMBAT.md)).

## Contracts

### Lifecycle and timing

- Daily logic goes through `NewDayListener`. `TimeTracker.onNewDay()` collects listeners from both `getListenerManager().getListeners(NewDayListener.class)` and `getAllListeners()` and calls each once (`utils/timers/TimeTracker.java:125`). It does not run while paused (`runWhilePaused()` returns false).
- Monthly and economy logic uses vanilla `EconomyTickListener` (`reportEconomyTick`, `reportEconomyMonthEnd`) on industries and listeners.
- `register()` listeners are transient and re-added on every load; never make them persistent without removing the `register()` call, or they pile up. `RelayNetworkBrain` is a transient `CampaignEventListener` and looks itself up in `SectorAPI.getAllListeners()`, not the listener manager ([RELAY](industries/RELAY.md)).
- 16 classes read settings into `static final` fields at class load; those settings need a restart ([SETTINGS](core/SETTINGS.md)).

### Industries and markets

- Switchable population, mining and refining return the vanilla id from `getId()`. `Market.getIndustry` and `hasIndustry` match on `getId()`, so vanilla and other mods get the IE class for `population`, `mining` and `refining`.
- `IndustrySpec` objects are shared by every instance of an industry. Never write per market values onto one. `Ruins` (per market upgrade), forge template users and the rift generator (no downgrade) return an `IndustrySpecView` (`indevo/industries/derelicts/utils`) from `getSpec()` ([DERELICTS](industries/DERELICTS.md)).
- `BaseIndustry.buildTime` (protected) is read by reflection from `RelicBuildTimeReductionButtonAdder` and read and written from `Edict_FastBuild`.
- `ModPlugin.addTypePrefaceToIndustrySpecs()` prefixes every industry description (vanilla and other mods) with `Type: ...` based on tags.
- `createAcademyMarket()` adds a hidden independent market `ACADEMY_MARKET_ID` on `station_galatia_academy` on new game and on load if missing.

### Storage and submarkets

- `IndEvo_SharedStore` (`SharedSubmarketUser`) is shared by the supercomputer, salvage yards, engineering hub, commodity forge and forge template users. Forge templates move between derelict industries as cargo ([DERELICTS](industries/DERELICTS.md), [INSTALLABLES](items/INSTALLABLES.md)).
- `MiscIE.getStorage` falls back to RAT `rat_settlement_storage` ([UTILS](core/UTILS.md)).
- Player submarkets are added and removed only through `SubMarketAddOrRemovePlugin` ([SUBMARKETS](core/SUBMARKETS.md)).

### Cross-feature coupling

| From | Uses | Where |
|---|---|---|
| Privateer | Current changeling government; `EngineeringHub.isTiandong/isRoider` | `PrivateerBase.java:71,194,203,322` |
| Courier port | Gacha stations as targets; RAT settlements | `ShippingTargetHelper.java:56-57`, `ShippingContract.java:88,93` |
| Split fleet | Courier `ShippingTargetHelper` for targets | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) |
| Pets | `Academy.COLOURS_BY_PERSONALITY` | `Pet.java:56` |
| Changeling, distress | `SelfRepairingBuiltInHullmod`, `PetHullmod` from the pet shop | [PETS](industries/PETS.md) |
| Museum | Pet shop escape interceptor | [MUSEUM](industries/MUSEUM.md) |
| Requisitions center, salvage specials, `IndEvo_notAtWar` | Embassy ambassador | [EMBASSY](industries/EMBASSY.md) |
| Supercomputer | Income applied through `ResourceCondition` | `economy/conditions/ResourceCondition.java:39,47` |
| Relay | `$IndEvo_RelayUnlocked` set by the hypertransmitter item effect | `SpecialItemEffectsRepo.java:165` |
| Rift generator | Skips planets with artillery | `RuinsManager.java:171-207` |
| Consumables (remote, decoy) | Artillery `forceTarget`, `ArtilleryTerrain` | [CONSUMABLES](items/CONSUMABLES.md) |
| Meteor | `MissileMemFlags.MEM_CAUGHT_BY_MISSILE` from consumables; spawns lab, arsenal and pet center stations | `MeteorEntity.java:71` |
| Stations | Arsenal stations add the minefield condition | `DerelictStationPlacer.java:143-157` |
| Crucible | Artillery `VariableExplosionEntityPlugin`; `TAG_ENABLED` read by a utils renderer | [CRUCIBLE](exploration/CRUCIBLE.md) |
| Research (Navi) | Unlocks fairy pet sales | `ModPlugin.java:173` |
| Beacons, scoop, smoke cloud, mine belt | Split fleet `OrbitFocus` entity | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) |
| `other/legioNuclearOption` | Research `RefitUIOpenChecker` | [MISC](core/MISC.md) |
| `$IndEvo_ruinsIndustryId` | Read by supercomputer, `LocatorSystemRatingUpdater`, `ModUpdater` | [DERELICTS](industries/DERELICTS.md) |

### Shared cross-file constraints

- `Settings` is the only reader for LunaLib fields; `MiscIE.getCSVSetFromMemory` the only reader for whitelists ([SETTINGS](core/SETTINGS.md)).
- Imports from Nexerelin, RAT and Shadowyards must sit behind `isModEnabled` checks so the classes load lazily ([COMPATIBILITY](core/COMPATIBILITY.md)).
- Check [UTILS](core/UTILS.md) before adding a helper; `MiscIE` already holds most market, cargo and memory helpers.

## Dead or dormant

| Component | State | Doc |
|---|---|---|
| Crucible | Spawner commented out (`ModPlugin.java:220`, `:306`); data rows still load | [CRUCIBLE](exploration/CRUCIBLE.md) |
| Distress calls, ghost ships | Excluded, does not compile; hullmod rows and rules still load | [DISTRESS](exploration/DISTRESS.md) |
| Mobile colony | Compiled and its campaign plugin registered (`:505`), never created | [WIP](core/WIP.md) |
| Converters | Excluded, does not compile | [WIP](core/WIP.md) |
| Wormhole complex (`IndEvo_Warphouse`) | CSV row and item commented out | [WAREHOUSES](industries/WAREHOUSES.md) |
| `ArtilleryStationReplacer` | Never matches: checks `contains("artillery")` against `IndEvo_Artillery_*` ids | [ARTILLERY](industries/ARTILLERY.md) |
| Detachment V3 AI, DELIVER mode, `PlayerFleetFollower` | Stub or unregistered | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) |
| `indevo/other` | Unregistered except the nuclear option checker | [MISC](core/MISC.md) |
| `compatibility/TechTree`, `ModUpdater` | No references | [SMALL_INDUSTRIES](industries/SMALL_INDUSTRIES.md), [UTILS](core/UTILS.md) |
| `devActions()` | Disabled (`&& false`, `ModPlugin.java:146`) | This file |

Each feature doc lists its own dormant classes and suspected bugs under Known issues.

## Maintenance

Update this file when a hook in `ModPlugin` changes, a feature doc is added or renamed, a registration pattern or save rule changes, or a coupling between two features is added or removed. Feature internals belong in the feature doc. Follow [CLAUDE.md](../CLAUDE.md#documentation-upkeep).
