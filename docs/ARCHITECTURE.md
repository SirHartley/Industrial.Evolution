# Architecture

Overview of the current implementation: areas, the feature docs, registration and lifecycle, save data, data layout and the contracts that cross features. Code paths are relative to `jars/src/`; data paths are repository-relative. Feature internals live in the feature docs; this file routes to them. Search the named owner before adding another implementation.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [SETTINGS.md](core/SETTINGS.md) | Settings cache (`config`), LunaLib tabs, `settings.json`, whitelists |
| [SETTINGS_REWORK.md](core/SETTINGS_REWORK.md) | Decisions behind the typed, cached settings model and Quick Settings; implemented, the current mechanism is in SETTINGS.md |
| [NEW_FEATURES.md](NEW_FEATURES.md) | Plans for new features (fleet side quests, Catch.Release lockout); not implemented |
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
| | [ARTILLERY](industries/ARTILLERY.md) | Artillery stations, vision (watchtowers, lighthouses, searchlights, drone nets), campaign projectiles | `industries/artillery` |
| | [ASSEMBLER](industries/ASSEMBLER.md) | Variable manufactories and the commodity forge (VPC items) | `industries/assembler` |
| | [CHANGELING](industries/CHANGELING.md) | Governments (market condition) and mining or refining specializations, applied by an industry apply listener | `industries/changeling` |
| | [COURIER_PORT](industries/COURIER_PORT.md) | Shipping contracts between colonies | `industries/courierport` |
| | [DERELICTS](industries/DERELICTS.md) | Ruins, ancient lab, hull deconstructor, hull forge, rift generator, forge templates | `industries/derelicts` |
| | [EMBASSY](industries/EMBASSY.md) | Ambassadors and faction relations | `industries/embassy` |
| | [ENGINEERING_HUB](industries/ENGINEERING_HUB.md) | Reverse engineering of hulls | `industries/engineeringhub` |
| | [MUSEUM](industries/MUSEUM.md) | Ship exhibits, parade fleets | `industries/museum` |
| | [ORBITAL_WORKS_SLOT](industries/ORBITAL_WORKS_SLOT.md) | Relic Lattice Work: extra S-mod slot at vanilla Orbital Works | `industries/orbitalworks` |
| | [PETS](industries/PETS.md) | Pet shop, pets, pet management ability | `industries/petshop`, `abilities/pets` |
| | [PRIVATEER](industries/PRIVATEER.md) | Privateer base raids | `industries/privateer` |
| | [RAT_SETTLEMENTS](industries/RAT_SETTLEMENTS.md) | IE facilities for RAT settlements, Engineering Annex panel | `industries/ratsettlement` |
| | [RELAY](industries/RELAY.md) | Military and interstellar relays, fleet size sharing | `industries/relay` |
| | [RUINED_INFRASTRUCTURE](industries/RUINED_INFRASTRUCTURE.md) | Ruined infrastructure conditions and restoration | `industries/ruinfra` |
| | [SALVAGE_YARDS](industries/SALVAGE_YARDS.md) | Salvage yards, custom production, D-mod transfer | `industries/salvageyards` |
| | [SENATE](industries/SENATE.md) | Senate and edicts | `industries/senate` |
| | [SMALL_INDUSTRIES](industries/SMALL_INDUSTRIES.md) | Industry index; bureau, supercomputer, restoration docks, requisitions center, commerce balance patches, shield, shared storage | `industries/*.java` |
| | [WAREHOUSES](industries/WAREHOUSES.md) | Configurable extra storages | `industries/warehouses` |
| | [WORLD_WONDER](industries/WORLD_WONDER.md) | World wonders | `industries/worldwonder` |
| Exploration | [CRUCIBLE](exploration/CRUCIBLE.md) | Crucible and catapults (dormant) | `exploration/crucible` |
| | [DISTRESS](exploration/DISTRESS.md) | Ghost ship distress calls (excluded, does not compile) | `exploration/distress` |
| | [FOG](exploration/FOG.md) | Fog weather: fog spells and banks, sensor cut, fluid solver and shader, lurker ambush fleets | `exploration/fog` |
| | [GACHA](exploration/GACHA.md) | Automated shipyard stations | `exploration/gacha` |
| | [METEOR](exploration/METEOR.md) | Meteor swarms, rogue asteroids and treasure | `exploration/meteor` |
| | [MINEFIELDS](exploration/MINEFIELDS.md) | Mine belts and mine fields | `exploration/minefields` |
| | [SALVAGE](exploration/SALVAGE.md) | Salvage specials, relic recovery of story point derelicts | `exploration/salvage` |
| | [STATIONS](exploration/STATIONS.md) | Derelict lab, arsenal and pet center stations | `exploration/stations` |
| | [SURVEY_STORIES](exploration/SURVEY_STORIES.md) | Survey story texts | `exploration/surveystories` |
| Abilities | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) | Detachments | `abilities/splitfleet` |
| | [SKILLS](abilities/SKILLS.md) | IE skills, remote industry item management | `abilities/skills` |
| Items | [CONSUMABLES](items/CONSUMABLES.md) | Consumable items and their abilities, missiles | `items/consumables` |
| | [INSTALLABLES](items/INSTALLABLES.md) | Installable special items, item effects, loot listeners, forge template and VPC item plugins | `items/*` (rest) |
| Dialogue | [RESEARCH](dialogue/RESEARCH.md) | Research projects | `dialogue/research` |
| | [BEACONS](dialogue/BEACONS.md) | Warning beacon dismantling | `dialogue/beacons` |
| | [SIDE_PANEL](dialogue/SIDE_PANEL.md) | Button objects of the older dialog popups, `?` help icon, ESC hook | `dialogue/sidepanel` |
| | [RULE_COMMANDS](dialogue/RULE_COMMANDS.md) | Generic rule commands, `rules.csv` map | `dialogue/rules` |
| Core | [ECONOMY](core/ECONOMY.md) | Resource condition, parts, relic build time reduction, custom production Express Delivery, relic refit build in, Tech Mining relic boost | `economy` |
| | [SUBMARKETS](core/SUBMARKETS.md) | Submarket plugins, adding and removing submarkets | `submarkets` |
| | [UI](core/UI.md) | Shared UI kit and layout contract for IE panels | `utils/ui` |
| | [COMBAT](core/COMBAT.md) | Weapon scripts, missile AI, combat terrain | `data/scripts` |
| | [Skillshot README](../jars/src/indevo/skillshot/README.md) | Aimed campaign abilities framework (missile targeting), shared design and cross mod lock with Catch.Release | `skillshot` |
| | [CONSOLE](core/CONSOLE.md) | Console Commands | `console` |
| | [MISC](core/MISC.md) | Unregistered leftovers | `other` |
| | [WIP](core/WIP.md) | Converters | `WIP` |
| Ambience | [SHUTTLE_TRAFFIC](core/SHUTTLE_TRAFFIC.md) | Visual only shuttle traffic in the current system: lanes, pleasure cruises, patrols, mining, salvage, fleet craft, dogfights around NPC battles, faction profiles, economy tells, event flights, IE industry flights, convoys, slow traffic that eases around slow fleets and flees hostile ones, hits, escape pods | `ambience/shuttletraffic` |

## Start here

| Change / symptom | Route |
|---|---|
| Anything that happens once per day | `TimeTracker.advance -> onNewDay -> NewDayListener.onNewDay` ([UTILS](core/UTILS.md)) |
| Something missing after load, or registered twice | `ModPlugin.onGameLoad -> setListenersIfNeeded / setScriptsIfNeeded` ([below](#registration-and-lifecycle)) |
| Content missing in a new game (ruins, artillery, stations, minefields, gacha) | `ModPlugin.onNewGameAfterEconomyLoad` chain ([below](#registration-and-lifecycle)) |
| An industry not buildable | Industry `isAvailableToBuild()` -> its `Setting` toggle ([SETTINGS](core/SETTINGS.md)) -> feature doc |
| A colony option or menu missing | `*OptionProvider.register()` -> rules `IndEvo_FireBase*OptionList` ([RULE_COMMANDS](dialogue/RULE_COMMANDS.md)) |
| A special item does nothing | `SpecialItemEffectsRepo.ITEM_EFFECTS` or the industry's `getInstallableItems()` ([INSTALLABLES](items/INSTALLABLES.md)); consumables: `ItemAbilityHelper.ID_MAP` ([CONSUMABLES](items/CONSUMABLES.md)) |
| A storage or submarket wrong or not removed | `IndustrySubmarkets` ([SUBMARKETS](core/SUBMARKETS.md)) -> feature doc |
| Parts, rare parts, supercomputer income | `ResourceConditionApplicator -> ResourceCondition.apply` ([ECONOMY](core/ECONOMY.md)) |
| A ruin turns into the wrong structure | `$IndEvo_ruinsIndustryId` -> `RuinsManager` ([DERELICTS](industries/DERELICTS.md)) |
| Government or specialization not applied | `ChangelingApplyListener` -> `GovernmentCondition` / `Specialization` ([CHANGELING](industries/CHANGELING.md#condition-and-listener)) |
| Detachment AI | `Behaviour.changeBehaviourAndUpdateAI -> DetachmentAI` ([SPLIT_FLEET](abilities/SPLIT_FLEET.md)) |
| Artillery not firing | `CampaignAttackScript -> EyeIndicatorScript / WatchtowerEntityPlugin / LighthouseEntityPlugin / SearchlightEntityPlugin / DroneNetScript -> ArtilleryStationScript` ([ARTILLERY](industries/ARTILLERY.md)) |
| Meteor shower or rogue asteroid spawn | `MeteorSwarmManager.reportEconomyTick -> spawnShower -> <Type>SwarmSpawner`, or `-> spawnRogue -> RogueAsteroidSpawner` ([METEOR](exploration/METEOR.md)) |
| Fog spawn, sensor cut or look | `FogWeatherManager.reportEconomyTick -> spawn -> FogTerrainPlugin.advance / applyEffect / render -> FogGrid.step, FogRenderer` ([FOG](exploration/FOG.md)) |
| Dialogue or rules row | `rules.csv` block map -> command class ([RULE_COMMANDS](dialogue/RULE_COMMANDS.md)) |

## Registration and lifecycle

`mod_info.json`: id `IndEvo`, plugin `indevo.utils.ModPlugin`, jar `jars/IndEvo.jar`, hard dependencies LazyLib, MagicLib, LunaLib, GraphicsLib, PatchLib. Hook order and what exists at each hook: knowledge skill `docs/concepts/lifecycle.md`.

| Hook | What IE does | Line |
|---|---|---|
| `onApplicationLoad()` | `PlayerSettings.install()` first ([SETTINGS](core/SETTINGS.md#reading-a-setting)); GraphicsLib `ShaderLib.init()`, light and texture CSVs from `data/lights/`; `SpecialItemEffectsRepo.addItemEffectsToVanillaRepo()` | `utils/ModPlugin.java:116` |
| `onAboutToStartGeneratingCodex()` | Preloads the nanites icon texture | `:132` |
| `onNewGame()` | `PlayerSettings.reload()`: picks up the new game settings panel | `:279` |
| `onNewGameAfterEconomyLoad()` | One-time world generation (chain below) | `:285` |
| `onGameLoad(newGame)` | `PlayerSettings.install()` first, then every-load registration and fixes (chain below) | `:146` |
| `beforeGameSave()` | `SkillshotFramework.reset()` (cancels aiming, releases the cross mod lock); removes untouched mine entities from their locations | `:312` |
| `afterGameSave()`, `onGameSaveFailed()` | Puts the removed mines back | `:337`, `:342` |
| `pickMissileAI()` | `IndEvo_mortarProjectileAI`, `IndEvo_missileProjectileAI` at `MOD_SPECIFIC` ([COMBAT](core/COMBAT.md)) | `:372` |

New game, once (`:288` to `:304`):

`LocatorSystemRatingUpdater.updateAllSystems -> RuinsManager.forceCleanCoreRuins -> NewGameIndustryPlacer.run -> ArtilleryStationPlacer.placeDerelictArtilleries -> placeCoreWorldArtilleries -> GachaStationSpawner.spawn -> createAcademyMarket -> [IndEvo_Enable_Derelicts] DerelictStationPlacer.init -> IndEvo_SalvageSpecialAssigner.init -> spawnMineFields -> DerelictInfrastructurePlacer.placeRuinedInfrastructure -> RuinsManager.placeIndustrialRuins`

Every load (`:153` to `:173`):

`createAcademyMarket -> setListenersIfNeeded -> setScriptsIfNeeded -> IndustrySubmarkets.removeOrphans -> ResourceConditionApplicator.applyResourceConditionToAllMarkets -> loadTransientMemory -> OrbitFocus.removeUnusedOrbitFoci -> addTypePrefaceToIndustrySpecs -> LocatorSystemRatingUpdater.updateAllSystems -> resetDerelictRep -> PlayerSettings.applyCampaignSettings -> [newGame, IndEvo_pets] addLordFoogRep -> [IndEvo_pets] PetLongevity.placeSyringes`

### Registration patterns

| Pattern | Meaning | Example |
|---|---|---|
| `X.register()` | `getListenerManager().addListener(new X(), true)`: transient, added on every load, not saved | `DepositMessage.register()` |
| `X.getInstanceOrRegister()` | Returns the saved instance from `getListeners(X.class)` or adds a persistent one | `ShippingManager`, `AdminGovernTimeTracker`, `RiftTearManager` (on the first rift jump, [DERELICTS](industries/DERELICTS.md#registration-and-lifecycle)); `RelayNetworkBrain` is the transient variant, see below |
| `registerPlugin(new XCampaignPlugin())` | Campaign plugin; all four IE plugins are transient (`BaseCampaignPlugin.isTransient()` returns true) | `setScriptsIfNeeded()` `:516` to `:519` |
| `@Patch` class | PatchLib finds it in `jars/IndEvo.jar` at startup, no registration. A hook installs when its target class loads | [Patches](#patches) |
| `sector.addScript` guarded by `hasScript` | Saved script, added once | `TimeTracker` `:522` |
| `system.addScript` guarded by a system memory key | Saved script on one star system, added at placement | `DroneNetScript.getInstanceOrRegister` ([ARTILLERY](industries/ARTILLERY.md#drone-net)) |
| `LunaCampaignRenderer.addTransientRenderer` guarded by `hasRendererOfClass` or `getRendererOfClass` | Transient LunaLib campaign renderer, one per session, not saved | `ShuttleTrafficRenderer.register()` ([SHUTTLE_TRAFFIC](core/SHUTTLE_TRAFFIC.md)), `RogueRockDustRenderer.register()` ([METEOR](exploration/METEOR.md#rogue-asteroids)), `ArtilleryFireRenderer.getInstanceOrRegister` on the first shot ([ARTILLERY](industries/ARTILLERY.md#fire-animation)) |
| Industry adds itself in `apply()` | `addListener(this)` from the industry; the listener manager skips an instance already present (obf `ObjectRepository.java:87`, per the knowledge skill) | Assembler, engineering hub; RAT settlement facilities add themselves as `NewDayListener` in `apply()` and remove themselves in `unapply()` ([RAT_SETTLEMENTS](industries/RAT_SETTLEMENTS.md)) |
| CSV plugin column | Industries, conditions, abilities, items, entities, terrain, hullmods, weapons | `data/campaign/*.csv`, `custom_entities.json`, `terrain.json` |
| `ruleCommandPackages` | Rule commands found by package | `settings.json:30` ([RULE_COMMANDS](dialogue/RULE_COMMANDS.md)) |

`setListenersIfNeeded()` (`:441` to `:518`) by feature: item effects and loot (`SpecialItemEffectsRepo.initEffectListeners`, `ShipComponentLootManager`, `SpecialItemDropsListener`, `VPCLootResolver`, `ConsumableItemDropListener`, `ConsumableItemMarketAdder`), economy (`ResourceConditionApplicator`, `RelicBuildTimeReductionButtonAdder`, `RelicSpeedUpOptionProvider`, `TechMiningRelicOptionProvider`), orbital works (`OrbitalWorksSlotManager.getInstanceOrRegister`, `OrbitalWorksSlotOptionProvider`), assembler (`DepositMessage`), embassy (`AmbassadorPersonManager`, `HostileActivityEventSubRegisterListener`), privateer and raids (`RaidTimeout`), derelicts (`RuinsManager.*`, `AncientLabCommoditySwitchOptionProvider`, `AncientLabRelicOptionProvider`, `RiftGenOptionProvider`, `RemoveIndustryOptionWarningProvider`), courier (`ShippingManager`, `CourierPortOptionProvider`), split fleet (`DetachmentAbilityAdder`, `DetachmentPayroll`), consumables (`LocatorSystemRatingUpdater`, `SpooferItemKeypressListener`, `SkillshotFramework.register()`, `FleetConsumableInventoryManager`), minefields (`RecentJumpListener`, `InterdictionPulseAbilityListener`), world wonder, changeling, pets, ruinfra, artillery (`WatchtowerFactionResetListener`), research unlock checkers, skills, salvage yards (`SalvageYardsOptionProvider`, `SalvageDronesBattleListener`), senate, academy, engineering hub (`EngineeringHubOptionProvider`), beacons, Cloud Painter shield visuals, relay (`MilitaryRelay.RelayItemRemovalButtonListener`, `RelayNetworkBrain`), meteor (`MeteorSwarmManager`, `RogueRockDustRenderer`), fog (`FogWeatherManager`), shuttle traffic (`ShuttleTrafficRenderer`, a transient LunaLib renderer, and its transient `ShuttleCampaignListener` and `ShuttleColonyListener`, the latter also an `ObjectiveEventListener`, `PlayerColonizationListener` and `ColonyDecivListener`), warehouses, museum. Nexerelin only: `ManagedDemocracyNexerelinListenerPlugin`. Commented out: `DistressCallManager`, `HullmodTimeTracker`.

When adding a listener, add its `register()` call here in the feature's group and list it in the feature doc's Registration section.

### Patches

PatchLib patches change vanilla code where the API has no hook. Each lives in a `patches` package of the feature that owns it; the feature doc describes the behaviour. Rules for writing them: the `patchlib` skill. `BaseIndustry` targets were avoided after PatchLib 1.0.3 notes on Ashes of the Domain; Philipp allowed them again (2026-11-21), and IE runs with AOTD and its `BaseIndustry` patches in the 0.98.5a smoke. Never patch another mod's classes; where another mod replaces a vanilla screen, IE's patches only have to stay crash free there. Reflection inside a patch (`getField`, `getMethod`, `hasField`, `hasMethod`) takes a `FieldQuerySpec` or `MethodQuerySpec` built once into a `private static final` field of the patch class, never the `String` overloads (deprecated in the PatchLib release after 1.0.3, slower).

| Patch | Target | Kind | Feature doc |
|---|---|---|---|
| `industries/artillery/patches/WatchtowerStableLocationPatch` | `Misc.getNumStableLocations` (API) | `@After` | [ARTILLERY](industries/ARTILLERY.md#stable-locations) |
| `industries/privateer/patches/PrivateerIndustrySlotPatch` | `Misc.getNumIndustries` (API) | `@After` | [PRIVATEER](industries/PRIVATEER.md#contracts) |
| `industries/patches/CommerceBalancePatch` | vanilla `TradeCenter` (API): six methods, `IMPROVE_BONUS` reads | `@Before`, `@After`, `@RedirectFieldRead` | [SMALL_INDUSTRIES](industries/SMALL_INDUSTRIES.md#commerce-balance-patches-commerce) |
| `abilities/skills/patches/MicromanagementRemoteItemPatch` | `StarfarerSettings` `(String) -> boolean` call in the industry option menu constructor (obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred) | `@RedirectMethodCall` | [SKILLS](abilities/SKILLS.md#flow) |
| `industries/changeling/patches/GovernmentPatherInterestPatch` | `Industry.getPatherInterest()` call in `LuddicPathBaseManager.getLuddicPathMarketInterest` (API) | `@RedirectMethodCall` | [CHANGELING](industries/CHANGELING.md#condition-and-listener) |
| `industries/changeling/patches/ChangelingImagePatch` | `getCurrentImage()` on every `BaseIndustry` subtype (API impl), filtered to overrides the changeling set | `@After` | [CHANGELING](industries/CHANGELING.md#condition-and-listener) |
| `industries/changeling/patches/ChangelingTooltipPatch` | `getDescriptionOverride()` call in `BaseIndustry.createTooltip`, `BaseIndustry.isTooltipExpandable()` (API impl), filtered to a governed population | `@RedirectMethodCall`, `@After` | [CHANGELING](industries/CHANGELING.md#condition-and-listener) |
| `industries/orbitalworks/patches/OrbitalWorksRemovedPatch` | `BaseIndustry.notifyBeingRemoved(mode, forUpgrade)` (API impl), filtered to `orbitalworks` | `@After` | [ORBITAL_WORKS_SLOT](industries/ORBITAL_WORKS_SLOT.md#flow) |
| `industries/patches/DealmakerHolosuitePatch` | `ItemEffectsRepo.DEALMAKER_INCOME_PERCENT_BONUS` reads in `BaseInstallableItemEffect` subclasses (API) | `@RedirectFieldRead` | [SMALL_INDUSTRIES](industries/SMALL_INDUSTRIES.md#commerce-balance-patches-commerce) |
| `industries/senate/patches/FastBuildPickerRowPatch` | build menu row (obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred, picked by `getSortBuildTime`): label `create` call in `sizeChanged`, `renderImpl` | `@RedirectMethodCall`, `@After` | [SENATE](industries/SENATE.md#build-time-display) |
| `industries/senate/patches/FastBuildQueuedIndustryPatch` | industry card (obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred, picked by `addCostTimeLabels`): `IndustrySpecAPI.getBuildTime` call in `addCostTimeLabels` | `@RedirectMethodCall` | [SENATE](industries/SENATE.md#build-time-display) |
| `industries/senate/patches/FastBuildTooltipPatch` | industry tooltip (anonymous `StandardTooltipV2Expandable` in obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred): `Industry.createTooltip` call in `createImpl` | `@RedirectMethodCall` | [SENATE](industries/SENATE.md#build-time-display) |
| `industries/senate/patches/FastBuildUpgradeConfirmPatch` | industry option menu (obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred, `DialogCreatorUI`): `Industry.getBuildTime` call in `actionPerformed` | `@RedirectMethodCall` | [SENATE](industries/SENATE.md#build-time-display) |
| `abilities/splitfleet/patches/DetachmentCargoPickerPatch` | cargo picker dialog (obf `com.fs.starfarer.ui.newui`, inferred, picked by its `CargoPickerListener` field): constructor, `advance(float)` | `@After` | [SPLIT_FLEET](abilities/SPLIT_FLEET.md#load-buttons) |
| `industries/worldwonder/patches/CloudPainterShieldPatch` | `applyVisuals` call in `PlanetaryShield.apply`, `PlanetaryShield.addPostDemandSection` (API) | `@RedirectMethodCall`, `@After` | [WORLD_WONDER](industries/WORLD_WONDER.md#cloud-painter-shield-visuals) |
| `economy/patches/RelicBuildInPatch` | refit build in dialog `com.fs.starfarer.coreui.refit.ModPickerDialogV3` (readable class in obf package, inferred): `createUI`, `getStoryPoints` and `addMessage` calls in `tableRowSelected`, `spendStoryPoints` call in `actionPerformed` | `@After`, `@RedirectMethodCall` | [ECONOMY](core/ECONOMY.md#relic-build-in) |
| `economy/patches/RelicBuildInCounterPatch` | its inner class `ModPickerDialogV3$PermaModInfoWidget` (inferred): `getStoryPoints` and `LabelAPI.setText` calls in `advanceImpl` | `@RedirectMethodCall` | [ECONOMY](core/ECONOMY.md#relic-build-in) |
| `economy/patches/RelicBuildInListPatch` | its inner class `ModPickerDialogV3$PermaModInfoWidget` (inferred): `advanceImpl`; reflection on the effect scroll panel (`getContentContainer`, `getChildrenCopy`, `setContentSize`) and `getRefitPanel().getShipDisplay().getShip()` | `@After` | [ECONOMY](core/ECONOMY.md#relic-build-in) |
| `industries/petshop/patches/PetScuttlePatch` | `FleetDataAPI.scuttle(FleetMemberAPI)` (API interface, vanilla `FleetData`) | `@After` | [PETS](industries/PETS.md#hand-over-and-menagerie) |
| `industries/petshop/patches/PetFleetCardPatch` | fleet screen ship card (obf `com.fs.starfarer.coreui`, inferred, picked by `addTooltipsForPips`): `addTooltipsForPips`; calls `getMember`, `getFleetPanel().updateListContents()` | `@After` | [PETS](industries/PETS.md#pet-on-its-ship) |
| `industries/petshop/patches/PetShipTooltipPatch` | full ship tooltip (anonymous `StandardTooltipV2` of `createFleetMemberTooltipPreDeploy`, inferred): `sendToBottomWithinItself` call in `beforeShown` | `@RedirectMethodCall` | [PETS](industries/PETS.md#pet-on-its-ship) |
| `industries/petshop/patches/PetRefitRowPatch` | refit hull mod row (obf `com.fs.starfarer.coreui.refit`, inferred, picked by its `HullModSpecAPI` field): `createHullModTooltip` call in the constructor; reflection `getChildrenCopy`, `getParent`, refit panel `isEditedSinceSave`, `saveCurrentVariant`, `recreateUI` | `@RedirectMethodCall` | [PETS](industries/PETS.md#pet-on-its-ship) |
| `industries/worldwonder/patches/PlanetBackgroundRefreshPatch` | colony screen planet background (obf `com.fs.starfarer.campaign.ui.marketinfo`, inferred, picked by `setForce2dMode`): `advanceImpl`, calls `getPlanet` and `setPlanet` | `@After` | [WORLD_WONDER](industries/WORLD_WONDER.md#colony-screen-planet) |
| `exploration/salvage/patches/RelicDerelictRecoveryPatch` | vanilla `ShipRecoverySpecial` (API): `addStoryOptions`, `isStoryPointRecovery`, `optionSelected` | `@Before`, `@After` | [SALVAGE](exploration/SALVAGE.md#relic-recovery) |

After a game update, check inferred targets first (any target outside `com.fs.starfarer.api`). In game, `starsector.log` shows `[PatchLib] Discovered patch ...` at startup and `Installed a ... patch site ...` when the target loads; Console Commands `TogglePatch <class>` switches one off for a test.

## Save data

IE is released and players keep saves across patch versions. 4.2.a does not load 4.1.b saves (`Changelog` note), so IE carries no migration code for them. Starsector saves the sector with XStream; what persists is in the knowledge skill `docs/concepts/saves-persistence.md`.

- Saved: fields of persistent listeners (`getInstanceOrRegister`), saved scripts (`TimeTracker`, feature scripts added with `addScript`), intel, industries and conditions on markets, custom entities and their plugins, memory values, tags.
- Not saved: transient listeners (`register()`), campaign plugins, `SessionTransientMemory` (whitelist sets, VPC commodity and package tables).
- Renaming or moving a saved class, or changing the type of a saved field, breaks old saves. Government variants are saved inside the `IndEvo_government` condition; renaming a variant class breaks saves ([CHANGELING](industries/CHANGELING.md#contracts)).
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
| `data/config/indEvo/shuttle_traffic.csv` | Base craft for shuttle traffic | [SHUTTLE_TRAFFIC](core/SHUTTLE_TRAFFIC.md) |
| `data/config/indEvo/shuttle_factions.csv` | Shuttle traffic flying style per faction | [SHUTTLE_TRAFFIC](core/SHUTTLE_TRAFFIC.md#faction-profiles) |
| `data/config/exerelinFactionConfig`, `vayraBounties`, `prism`, `BetterColonyConfig`, `modFiles`, `version`, `orbit_entity_blacklist.csv` | Other mods' config | [COMPATIBILITY](core/COMPATIBILITY.md) |
| `data/console/commands.csv` | Console commands | [CONSOLE](core/CONSOLE.md) |
| `data/lights`, `data/shaders` | GraphicsLib lights, IE shaders | [COMBAT](core/COMBAT.md), [UTILS](core/UTILS.md), [FOG](exploration/FOG.md) (`indevo_fog*.shader`) |
| `data/strings` | Descriptions, tips, name lists | Feature docs |
| `data/world/factions` | `IndEvo_derelict` ([ARTILLERY](industries/ARTILLERY.md)), `IndEvo_couriers` ([COURIER_PORT](industries/COURIER_PORT.md)), `IndEvo_converters` ([WIP](core/WIP.md)) | Feature docs |

### Ids

- Shared ids are in `indevo/ids/Ids.java` (factions, whitelist paths, tags, submarkets, industries, conditions, hullmods, entities, skills, abilities, research projects) and `ItemIds.java` (special items, commodities). Feature-local keys stay in the feature.
- Prefixes: `IndEvo_` for ids and tags, `$IndEvo_` for memory keys. Older content uses other prefixes that are now stable ids and must not be renamed: `indevo_` (skills, research projects), `indEvo_` (ghost ship hullmods, drop groups), `splinter_fleet`, `pet_management`, `$oldstory_`, `$splinterFleet...`.
- `Ids.SKILL_*` constants do not match the skill files and are unused ([SKILLS](abilities/SKILLS.md)).

## Build

- IntelliJ module `IndEvo.iml`: source root `jars/src`, JDK 17, output `jars/` (`.idea/misc.xml`), artifact `IndEvo:jar` writes `jars/IndEvo.jar` (gitignored). Libraries are IntelliJ application libraries; the same jars for agent builds are in `lib/`. Full dependency list and gate: [CLAUDE.md](../CLAUDE.md#building).
- Release builder `indevo/tools/ReleaseBuild.java` (run as a single-file program, never shipped): `compile` is the agent compile gate and runs `indevo/tools/SettingsChecks` (standalone checks of the CSV, the `Setting` enum, `sanitize` and the pair rule against the compiled classes; they do not start the game or LunaLib and do not check the rendered menu), `build` writes the committed release jar `jars/release/IndEvo.jar`, `package` writes the release zip. It compiles the same files as the IntelliJ module by reading its `excludeFolder` entries. [RELEASE.md](RELEASE.md).
- `indevo/tools/ShuttleTrafficChecks.java`: headless shuttle path, state, dodge, steering and dogfight checks, run by hand after the compile gate, never shipped. [SHUTTLE_TRAFFIC](core/SHUTTLE_TRAFFIC.md#checks).
- `indevo/tools/RogueAsteroidChecks.java`: headless rogue asteroid shape, path, placement and meteor dodge checks, run by hand after the compile gate, never shipped. [METEOR](exploration/METEOR.md#checks).
- `indevo/tools/PetChecks.java`: headless pet data and pet line checks (ids, bodies, line coverage per species and mood, pick weights), run by hand after the compile gate, never shipped. [PETS](industries/PETS.md#checks).
- Java only, no Kotlin ([CLAUDE.md](../CLAUDE.md#code-layout)).
- `native-smoke/`: test-only probes, the smoke test wrapper and the scripted live checks (`native-smoke/live/`). Never compiled by the builder or shipped. [SMOKE_TESTS.md](SMOKE_TESTS.md).
- `IndEvo.iml` `excludeFolder` entries that exist: `WIP/converters`, `exploration/WIP`, `exploration/distress`, `exploration/salvage/specials/WIP`, `items/consumables/itemAbilities/WIP`, `industries/academy/rules/WIP`, `industries/derelicts/industry/WIP`, `industries/warehouses/plugin/wi`. Excluded code does not compile and must not be referenced. The release builder reads the same entries, so change exclusions in `IndEvo.iml` only.
- IE imports a few non API game classes. `CircularOrbitPointDown` (`RiftRelocator`), `FleetMemberStatus` (`NanitesAbilityPlugin`), `DynamicParticleGroup` and `SmoothParticle` (`data/scripts/weapons/IndEvo_missileProjectileOnFireEffect.java`) are `DoNotObfuscate` in the game jar. `com.fs.starfarer.combat.CombatEngine` (smoke particles, same file) and `ModPickerDialogV3` (relic build-in patches, `indevo/economy/patches/`) are not, and have no API equivalent. Check them first after a game update; use the API where one exists (clock, terrain, memory and script lists all have one).
- `jars/src/data/scripts` holds combat classes in the vanilla `data.scripts` package ([COMBAT](core/COMBAT.md)).

## Contracts

### Lifecycle and timing

- Daily logic goes through `NewDayListener`. `TimeTracker.onNewDay()` collects listeners from both `getListenerManager().getListeners(NewDayListener.class)` and `getAllListeners()` and calls each once (`utils/timers/TimeTracker.java:125`). It does not run while paused (`runWhilePaused()` returns false).
- Monthly and economy logic uses vanilla `EconomyTickListener` (`reportEconomyTick`, `reportEconomyMonthEnd`) on industries and listeners.
- `register()` listeners are transient and re-added on every load; never make them persistent without removing the `register()` call, or they pile up. `RelayNetworkBrain` is a transient `CampaignEventListener` and looks itself up in `SectorAPI.getAllListeners()`, not the listener manager ([RELAY](industries/RELAY.md)).
- Settings are read at use time from the `PlayerSettings` cache, so a Save in the LunaLib menu applies on the next read; saved objects that copy a value at creation keep it ([SETTINGS](core/SETTINGS.md#reading-a-setting)).

### Industries and markets

- IE never replaces `population`, `mining` or `refining`. Governments and specializations sit on the market and are applied as deltas by `ChangelingApplyListener` ([CHANGELING](industries/CHANGELING.md)).
- Buildable IE industries start `isAvailableToBuild()` and `showWhenUnavailable()` from `super` (vanilla: no build on `MARKET_NO_INDUSTRIES_ALLOWED` markets, needs `population`), then add their own checks. Never buildable industries (`Ruins`, `RuinedInfrastructure`, forge template users, rift generator) return false; `ChamelionIndustry` asks the target plugin.
- `IndustrySpec` objects are shared by every instance of an industry. Never write per market values onto one. `Ruins` (per market upgrade), forge template users and the rift generator (no downgrade) return an `IndustrySpecView` (`indevo/industries/derelicts/utils`) from `getSpec()` ([DERELICTS](industries/DERELICTS.md)).
- IE costs and income go through `BillingHelper` ([UTILS](core/UTILS.md#billinghelper)): own monthly report lines, and `canAfford` then `chargeNow` for dialog purchases. Never write vanilla report nodes or subtract credits directly. Loot and refunds add credits directly.
- `BaseIndustry.buildTime` (protected) is read by reflection from `RelicBuildTimeReductionButtonAdder` and read and written from `Edict_FastBuild`.
- `ModPlugin.addTypePrefaceToIndustrySpecs()` prefixes every industry description (vanilla and other mods) with `Type: ...` based on tags.
- `createAcademyMarket()` adds a hidden independent market `Ids.ACADEMY_MARKET_ID` on `station_galatia_academy` on new game and on load if missing.

### Storage and submarkets

- `IndEvo_SharedStore` (`SharedSubmarketUser`) is shared by the supercomputer, salvage yards, engineering hub, commodity forge and forge template users. Forge templates move between derelict industries as cargo ([DERELICTS](industries/DERELICTS.md), [INSTALLABLES](items/INSTALLABLES.md)).
- `MiscIE.getStorage` falls back to RAT `rat_settlement_storage` ([UTILS](core/UTILS.md)).
- RAT settlement facilities add `IndEvo_ReqMarket` and the museum submarket to the settlement market from `apply()` and remove them with `IndustrySubmarkets.remove` on demolish; the settlement market is not in the economy, so `removeOrphans` never sees it ([RAT_SETTLEMENTS](industries/RAT_SETTLEMENTS.md)).
- Industry submarkets are added and removed only through `IndustrySubmarkets`: `ensure` from the owner's `advance()`, `release` from its `notifyBeingRemoved()`, never from `unapply()` ([SUBMARKETS](core/SUBMARKETS.md)).

### Cross-feature coupling

| From | Uses | Where |
|---|---|---|
| Privateer | Current government (`GovernmentCondition.getGovernment`); `EngineeringHub.isTiandong/isRoider` | `PrivateerBase.java:71,194,203,322` |
| Courier port | Gacha stations as targets; RAT settlements | `ShippingTargetHelper.java:56-57`, `ShippingContract.java:88,93` |
| RAT settlements | Parent industry code: `Academy`, `ReverseEngineering`, Salvage Yards and Courier panels, `Museum` and `RestorationDocks` static helpers, Requisitions Center submarket, `VariableAssembler` deposits, `AmbassadorPersonManager`, `PetShop` (`HabitatPetShop`, `planCenter`) and the Pets panel | [RAT_SETTLEMENTS](industries/RAT_SETTLEMENTS.md) |
| Courier port | Contracts allowed and billed with a RAT Courier Office | `ShippingManager.java:44`, `:101` |
| Split fleet | Courier `ShippingTargetHelper` for targets | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) |
| Pets | `Academy.COLOURS_BY_PERSONALITY` | `PetCard.java:462` |
| Split fleet | Skips the pet captain (`Ids.TAG_PET_CAPTAIN`) as an officer | [PETS](industries/PETS.md#contracts) |
| Changeling, distress | `SelfRepairingBuiltInHullmod`, `PetHullmod` from the pet shop | [PETS](industries/PETS.md) |
| Museum | Pet shop escape interceptor | [MUSEUM](industries/MUSEUM.md) |
| Requisitions center, salvage specials, `IndEvo_notAtWar` | Embassy ambassador | [EMBASSY](industries/EMBASSY.md) |
| Supercomputer | Income applied through `ResourceCondition` | `economy/conditions/ResourceCondition.java:39,47` |
| Relay | `$IndEvo_RelayUnlocked` set by the hypertransmitter item effect | `TransmitterEffect.java:26` |
| Rift generator | Skips planets with artillery | `RuinsManager.java:171-207` |
| Consumables (remote, decoy) | Artillery `forceTarget`, `ArtilleryTerrain` | [CONSUMABLES](items/CONSUMABLES.md) |
| Meteor | `MissileMemFlags.MEM_CAUGHT_BY_MISSILE` from consumables; spawns lab, arsenal and pet center stations | `MeteorEntity.java:100` |
| Stations | Arsenal stations add the minefield condition | `DerelictStationPlacer.java:143-157` |
| Crucible | Artillery `VariableExplosionEntityPlugin`; `TAG_ENABLED` read by a utils renderer | [CRUCIBLE](exploration/CRUCIBLE.md) |
| Research (Navi) | Unlocks fairy pet sales | `PetManagementAbilityPlugin.java:34` |
| Beacons, scoop, smoke cloud, mine belt | Split fleet `OrbitFocus` entity | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) |
| `other/legioNuclearOption` | Research `RefitUIOpenChecker` | [MISC](core/MISC.md) |
| `$IndEvo_ruinsIndustryId` | Read by supercomputer, `LocatorSystemRatingUpdater` | [DERELICTS](industries/DERELICTS.md) |

### Survival mode

Vanilla Survival mode (extended mode in code, `Misc.isExtended()`) scales prices, drops and pay at its own call sites; IE code that bypasses those sites applies the same `ext_` key itself through `ExtModeHelper` ([UTILS](core/UTILS.md#helper)), tested at call time, never a copied number. Exceptions are noted per feature. Already exempt on purpose: research drops (`DropDataCreator.java:17`), nest relics (`DroneNetScript.java:258`), the hullmod relic price (`RecoveredHullmods.java:65`).

| Feature | Survival mode factor | Doc |
|---|---|---|
| Hull Forge print cost | `ext_shipBuyPriceMult` | [DERELICTS](industries/DERELICTS.md#forge-template-loop) |
| Academy hire rolls at markets the player does not own | `ext_officersAvailableFraction`, `ext_adminAvailableFraction` | [ACADEMY](industries/ACADEMY.md#hireable-spawns-and-officer-quality) |
| Detachment crew and marine pay | `ext_crewSalaryMult`, `ext_marineSalaryMult` (with `Misc.computeFleetMemberCrewSalary`) | [SPLIT_FLEET](abilities/SPLIT_FLEET.md) |
| Ship recovery weights in IE special tables (caches, arsenal station, pet center) | `Misc.getDerelictRecoverableFraction()` | [SALVAGE](exploration/SALVAGE.md#assignment) |
| Credit Stash payout | 30%, fixed (no vanilla key) | [SALVAGE](exploration/SALVAGE.md#specials) |
| Privateer Base weapon and wing loot | 30%, fixed (no vanilla key) | [PRIVATEER](industries/PRIVATEER.md#loot) |
| Salvage Yards order budget and scrap value | `ext_playerIndustryCustomProductionMaxValueMult`, `ext_shipBuyPriceMult` | [SALVAGE_YARDS](industries/SALVAGE_YARDS.md) |

### Shared cross-file constraints

- `PlayerSettings` is the only reader for LunaLib fields; `MiscIE.getCSVSetFromMemory` the only reader for whitelists ([SETTINGS](core/SETTINGS.md)).
- Imports from Nexerelin, RAT and Shadowyards must sit behind `isModEnabled` checks so the classes load lazily ([COMPATIBILITY](core/COMPATIBILITY.md)). Ashes of the Domain classes are imported by `indevo/industries/compatibility/AotdManager.java` only, inside its nested `Vok` class.
- Every buildable IE industry checks `AotdManager.isResearched(this)` in `isAvailableToBuild()` and returns `AotdManager.getMissingResearchReason(this)` first in `getUnavailableReason()`; without Vaults of Knowledge both pass.
- Check [UTILS](core/UTILS.md) before adding a helper; `MiscIE` already holds most market, cargo and memory helpers.

## Dead or dormant

| Component | State | Doc |
|---|---|---|
| Crucible | Spawner commented out (`ModPlugin.java:199`, `:291`); data rows still load | [CRUCIBLE](exploration/CRUCIBLE.md) |
| Distress calls, ghost ships | Excluded, does not compile; hullmod rows and rules still load | [DISTRESS](exploration/DISTRESS.md) |
| Converters | Excluded, does not compile | [WIP](core/WIP.md) |
| Wormhole complex (`IndEvo_Warphouse`) | CSV row and item commented out | [WAREHOUSES](industries/WAREHOUSES.md) |
| `indevo/other` | Unregistered snippets and templates for other mods | [MISC](core/MISC.md) |
| `devActions()` | Disabled (`&& false`, `ModPlugin.java:149`) | This file |

Each feature doc lists its own dormant classes and suspected bugs under Known issues.

## Maintenance

Update this file when a hook in `ModPlugin` changes, a feature doc is added or renamed, a registration pattern or save rule changes, or a coupling between two features is added or removed. Feature internals belong in the feature doc. Follow [CLAUDE.md](../CLAUDE.md#documentation-upkeep).
