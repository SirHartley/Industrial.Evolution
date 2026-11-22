# Compatibility

Every place IE touches another mod: hard dependencies, optional code paths, and data files read by other mods. Read it before changing a mod check, adding an integration, or after another mod renames an id. Feature behaviour behind each hook lives in the linked feature doc.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

Mod checks use `Global.getSettings().getModManager().isModEnabled(id)`. Code paths are relative to `jars/src/`.

## Hard dependencies

Declared in `mod_info.json` `dependencies`; the game refuses to load IE without them.

| Mod (id) | What IE uses | Where |
|---|---|---|
| LazyLib (`lw_lazylib`) | `org.lazywizard.lazylib` math, collision, combat and campaign utils | Imported in 73 files (grep), for example `data/scripts/weapons/ai/IndEvo_missileProjectileAI.java` |
| MagicLib (`MagicLib`) | `MagicRender` (mortar and missile AI, slow field), `MagicFakeBeamPlugin` (railgun targeting beam), `MagicTrailObject` (campaign trails), `MagicVariables` (unused tracker); projectile trails from `data/config/modFiles/magicTrail_data.csv`; achievements (`MagicAchievement`, `MagicTargetListAchievement`, specs in `data/config/magic_achievements.csv`) | `data/scripts/weapons/ai/IndEvo_mortarProjectileAI.java`, `IndEvo_missileProjectileAI.java`, `combatEntities/IndEvo_CombatSlowFieldTerrain.java`, `IndEvo_targetingBeamEffect.java`, `indevo/utils/trails/MagicCampaignTrailPlugin.java`, `indevo/other/PlayerHyperspaceTripTracker.java`, `indevo/achievements/`. See [Combat](COMBAT.md), [Achievements](ACHIEVEMENTS.md) |
| LunaLib (`lunalib`) | Settings (`indevo.config.PlayerSettings`: `LunaSettings`, `LunaSettingsListener`, backend `LunaSettingsLoader.getSettings()` for the write back only; `data/config/LunaSettings.csv`); campaign renderers (`LunaCampaignRenderer`, `LunaCampaignRenderingPlugin`) for crucible, meteor, particles, consumable missiles, shuttle traffic, the artillery fire animation | 28 files (grep); settings read only in `indevo/config/PlayerSettings.java:80-91`, no fallback. See [Settings](SETTINGS.md) |
| GraphicsLib (`shaderLib`) | `ShaderLib.init()`, lights and normal maps from `data/lights/IndEvo_lights.csv`, `IndEvo_textures.csv`; distortion in meteor radiation VFX | `indevo/utils/ModPlugin.java:119` (guarded), `indevo/exploration/meteor/renderers/RadiationVFXHandler.java:9` (unguarded import) |
| PatchLib (`patchlib`) | Runtime patches on vanilla classes (`patchlib.api`); compile against `PatchLibAPI.jar` only | `@Patch` classes, listed in [Architecture](../ARCHITECTURE.md#patches) |

The `shaderLib` and `lunalib` checks are redundant while both are hard dependencies; they only matter if a dependency is dropped from `mod_info.json`.

## Optional integrations

| Mod (id) | Kind | What IE uses | Where |
|---|---|---|---|
| Console Commands (`lw_console`) | Optional, data driven | 7 commands in `data/console/commands.csv`, classes import `org.lazywizard.console`. Loaded only by Console Commands, so no guard needed (inferred) | `indevo/console/`. See [Console](CONSOLE.md) |
| Nexerelin (`nexerelin`) | Optional, code | Changeling Managed Democracy invasion listener (`exerelin.campaign.InvasionRound`, `exerelin.utilities.InvasionListener`) | `indevo/utils/ModPlugin.java:470`, `indevo/industries/changeling/listener/ManagedDemocracyNexerelinListenerPlugin.java:13`, `:14`. See [Changeling](../industries/CHANGELING.md) |
| Nexerelin | Optional, code | Pirate subpopulation reads `NexConfig` / `NexFactionConfig` | `indevo/industries/privateer/conditions/PirateSubpopulationCondition.java:88` (imports `:14`, `:15`). See [Privateer](../industries/PRIVATEER.md) |
| Nexerelin | Optional, code | Ambassador uses `DiplomacyManager` | `indevo/industries/embassy/listeners/AmbassadorPersonManager.java:111` (import `:19`). See [Embassy](../industries/EMBASSY.md) |
| Nexerelin | Optional, id only | Rift Generator COLONY mode skips markets tagged `nex_playerOutpost`; Deconstructor refuses ships with buff `nex_mercBuff` unless `IndEvo_DeconAllowNexOmega` | `indevo/industries/derelicts/RiftTargets.java:137`, `indevo/submarkets/impl/DeconstructorSubmarketPlugin.java:71` |
| Nexerelin | Optional, data | Faction config for `IndEvo_converters` (WIP faction) | `data/config/exerelinFactionConfig/mod_factions.csv`, `IndEvo_converters.json`. See [WIP](WIP.md) |
| Random Assortment of Things (`assortment_of_things`) | Optional, code | Courier Port targets the RAT settlement market (`FrontiersUtils`, `SettlementData`) | `indevo/industries/courierport/ShippingContract.java:88` (import `:3`), `ShippingTargetHelper.java:27`, `:50` (imports `:3`, `:4`). See [Courier Port](../industries/COURIER_PORT.md) |
| Random Assortment of Things | Optional, code | Ten IE facilities for RAT settlements (`BaseSettlementFacility` subclasses, rows in `data/campaign/frontiers/rat_frontiers_facilities.csv`, read by RAT only); Courier Port contracts, the Requisitions Center plugin and the pet code (`PetShop.planCenter`, `PetsDialogPlugin.dockedShop`) ask `RatSettlements` | `indevo/industries/ratsettlement/`, `indevo/industries/courierport/listeners/ShippingManager.java:44`, `indevo/submarkets/impl/RequisitionsCenterSubmarketPlugin.java:96`. See [RAT settlements](../industries/RAT_SETTLEMENTS.md) |
| Random Assortment of Things | Optional, id only | Storage fallback `rat_settlement_storage`; ship component loot weight for `rat_abyssals`; Hull Forge counts tag `rat_alteration` | `indevo/utils/helper/MiscIE.java:68`, `indevo/items/listeners/ShipComponentLootManager.java:35`, `indevo/industries/derelicts/industry/HullForge.java:303` |
| Shadowyards (`shadow_ships`) | Optional, code | `MS_modularFac`, `MS_fabUpgrader` in ship parts supply and demand and in Salvage Yards | `indevo/economy/conditions/ResourceCondition.java:69` (imports `:14` to `:16`), `indevo/industries/salvageyards/industry/SalvageYards.java:138` (imports `:25`, `:26`). See [Economy](ECONOMY.md), [Salvage Yards](../industries/SALVAGE_YARDS.md) |
| Ship/Weapon Pack (`swp`) | Optional, id only | Derelict faction friendly to `famous_bounty` | `indevo/utils/ModPlugin.java:516` |
| MagicLib bounties | Hard (MagicLib), id only | Derelict faction friendly to `ML_bounty`, no check | `indevo/utils/ModPlugin.java:513` |
| Alcoholism (`alcoholism`) | Optional, id only | Privateer Base commodity bonus | `indevo/industries/privateer/industry/PrivateerBase.java:711`. See [Privateer](../industries/PRIVATEER.md) |
| Tiandong Heavy Industries | Optional, id only | Special item `tiandong_retrofit_bp` handled by Engineering Hub, blueprint installable and Privateer loot; no mod check | `indevo/industries/engineeringhub/ReverseEngineering.java:102`, `:132`, `:145`, `indevo/industries/engineeringhub/industry/EngineeringHub.java:624`, `indevo/items/installable/BlueprintInstallableItemPlugin.java:103`, `indevo/industries/privateer/industry/PrivateerBase.java:324` |
| Roider Union (`roider`) | Optional, id only | Item `roider_retrofit_bp` and hull tag `roider_retrofit` (same places as Tiandong); meteor tutorial fleet uses faction `roider` when enabled | `ReverseEngineering.java:102`, `:127`, `BlueprintInstallableItemPlugin.java:131`, `PrivateerBase.java:326`, `indevo/exploration/meteor/scripts/TutorialEncounterScript.java:41` |
| Secrets of the Frontier / Perilous Expanse (`sun_perilous_expanse`) | Optional, id only | Research project also requires commodity `sun_data` | `indevo/dialogue/research/ResearchProjectTemplateRepo.java:200`. See [Research](../dialogue/RESEARCH.md) |
| Hullmods containing `sun_sl` | Optional, id only | Stripped from variants made by forge templates | `indevo/items/ForgeTemplateItemPlugin.java:422` |
| Prism Freeport | Optional, data | Boss ship blacklist read for Deconstructor and Reverse Engineering | `data/config/prism/prism_boss_ships.csv`, `prism_ships_blacklist.csv` (placeholders), read by `indevo/utils/helper/MiscIE.java:552`. See [Submarkets](SUBMARKETS.md) |
| Vayra's Sector bounties | Optional, data | Unique bounty flagships blacklisted the same way (column `flagshipVariantId`) | `data/config/vayraBounties/unique_bounty_data.csv` (header only), `MiscIE.java:581` |
| Prism / Vayra rows from other mods | Optional, data | Rows from mods named in a row are only used if `canLoadShips(factionOrModId)` (faction exists or mod enabled) | `MiscIE.java:545`, `:568`, `:596` |
| Catch.Release (`catchrelease`) | Optional, id only, read by the other mod | Its harpoon finds meteors by tag `IndEvo_MeteorSwarm` and breaks the one it hits by setting `$missile_bonked` in its memory. Both mods ship the skillshot framework in their own package and share the sector memory lock `$sirhartley_skillshotActive` (owner id) so only one aims at a time; Catch.Release does not set it yet. IE holds no Catch.Release code | `MeteorSwarmManager.METEOR_TAG`, `MeteorEntity.init`, `MissileMemFlags.MEM_CAUGHT_BY_MISSILE`, `SkillshotSettings.LOCK_KEY`. See [Meteor swarms](../exploration/METEOR.md#contracts), [Skillshot README](../../jars/src/indevo/skillshot/README.md#cross-mod-lock) |
| Building Menu Overhaul (Kaysaar, required by Ashes of the Domain) | None, not patched | Replaces the vanilla build menu and builds its own tooltips, so Mandated Overtime's halved days do not show there. IE never patches it; `FastBuildPickerRowPatch` still runs on the vanilla rows BMO dismisses and stays read only | `indevo/industries/senate/patches/`. See [Senate](../industries/SENATE.md#build-time-display) |
| Celestial Orbits | Optional, data, read by the other mod | Its orbit lines skip every custom entity listed in `data/config/orbit_entity_blacklist.csv` (`customEntityTypeId,showWithAllCelestialObjects`, all `false`). Listed: short lived entities (meteors, mines, missiles, projectiles, explosions, indicators, drones, warp effects), invisible orbit focus tokens, and crucible parts whose orbit the crucible already shows. Not listed: stations, towers, nests and planet catapults that stay. A new short lived IE custom entity gets a row here | `data/config/orbit_entity_blacklist.csv` |
| Better Colonies | Optional, data | AI industry spawn weights for IE industries | `data/config/BetterColonyConfig/bc_autospindustries.csv` |
| Version Checker | Optional, data | Update check (GitHub raw master file, thread 18011) | `IndEvo.version`, `data/config/version/version_files.csv` |

## Ashes of the Domain

Theory of Toolbox (`aotd_theory_of_toolbox`), Vaults of Knowledge (`aotd_vok`), Seats of Power (`aotd_sop`), all optional. Plan, findings and asks for AOTD: `AOTD_COMPAT.md` in the project files. Only `indevo/industries/compatibility/AotdManager.java` imports AOTD classes, inside its nested `Vok` class; VoK is compile only (`lib/Vok.jar`, VoK 5.0.6, not shipped). IE never patches AOTD.

| Piece | Kind | What | Where |
|---|---|---|---|
| Research tree | VoK, data + code | 27 IE nodes in `data/campaign/aotd_tech_options.csv` (`modId` `IndEvo`, own tree in VoK's mod list), merged by VoK on `id`. Rewards are IE industries plus 13 never built `IndEvo_unlock_*` rows (`ResearchUnlockIndustry`) for specializations and governments; Advanced and up also cost Relic Components. Icons under `ui_icons_tech_tree` in `settings.json`, placeholders cut from the industry images: node icons by node id (`graphics/icons/aotd/`), reward icons as `<reward id>_sub` (`graphics/icons/aotd/rewards/`, VoK draws a star when one is missing) | `AotdManager.loadNodes` reads IE's own rows |
| Research gate | VoK, code | `AotdManager.isResearched(industryId, market)`: true without VoK, outside the campaign, on markets the player does not own (NPC and pirate builds stay as without AOTD), for ids no IE node rewards, nodes researched from start; else VoK's `isAvailableForThisMarket` (the player's research). Every buildable IE industry and the specialization / government dialog (`SubIndustryData.getUnlockId`) ask it | `isAvailableToBuild` / `getUnavailableReason` of each industry, `ChangelingIndustryDialogueDelegate` |
| Variants never replace the host | ToT, VoK, SoP | Governments are a market condition and specializations market memory, applied by an `IndustryApplyListener`; ToT's pop plugin, SoP's capital and VoK's tiers stay. Specializations run on any industry tagged `mining` / `refining` | [Changeling](../industries/CHANGELING.md) |
| Ship parts | ToT, data | `IndEvo_parts` row in `data/campaign/aotd_econ_sheet.csv` (supply mult 8, demand 0.7, like heavy machinery), merged by ToT on `commodityId` | |
| Tenebrian Nanoforge | VoK, id only | `aotd_shrouded_nanoforge` cancels its +6 on parts supply and lowers parts demand by 3 | `indevo/economy/conditions/ResourceCondition.java` |
| Objectives | ToT, id only | ToT swaps the `objective` tag for `aotd_objective`; shuttle maintenance, detachment targets and orbit focus check both | `AotdManager.isObjective`, `getObjectives` |
| Watchtower take control | ToT, rules | ToT row `aotd_cob_dissable_control` disables `COB_takeControl` on every objective. `IndEvo_watchtower_aotdOpenDialog` (`$tag:IndEvo_watchtower`, `$tag:aotd_objective`, beats ToT's open row by score) runs the vanilla open flow, then `FireBest IndEvo_WatchtowerTakeControl` turns it back on unless a vanilla or IE block applies, so rule load order does not matter | `data/campaign/rules.csv` |
| Item production | ToT, VoK, data | 6 IE colony items (`AotdManager.PRODUCTION_ITEMS`) appended to `aotd_item_prod` in `data/config/settings.json` (settings arrays merge by append); VPCs stay out, ToT hands produced items out without data and a VPC needs its rolled outputs. ToT's production screen (`AoTDProductionSpecManager`) lists an item once the faction knows it. `AotdManager.lockProductionItems` (application load) tags the six `aotd_ignore_standarization`, so Domain Blueprint Standardization alone no longer unlocks them; each needs `$aotd<id>` on the player faction, set by its Galatia project ([Research](../dialogue/RESEARCH.md)). Locked items are missing from ToT's list, ToT has no hook for a greyed entry. Per build cost is ToT's: days = base price / 2500, credits, advanced components and domain grade machinery from 10% of the base price. Commodity costs come only from colony exports, so a relic component cost per build is not possible (AOTD_COMPAT A7). VoK's `macro_industrial_complex.csv` is loaded but never read, IE ships no rows there | |
| Centralization Bureau | data | 20 AOTD industries on the bureau whitelist | [Small industries](../industries/SMALL_INDUSTRIES.md) |
| Rift Generator | VoK, id only | A planet whose memory holds a megastructure script (`$aotd_megastructure_*`, `AotdManager.hasMegastructure`) can not jump, nor one with a `bifrost_gate` or `aotd_pluto_station` in orbit (`rift_blocking_entities.csv`): their scripts keep the old star system. Other entities in orbit move with the planet | `indevo/industries/derelicts/RiftBlockers.java:20`, [Derelicts](../industries/DERELICTS.md#rift-generator-move) |
| Military relays | SoP, read by the other mod | SoP `AoTDRelay extends indevo.industries.relay.industry.MilitaryRelay` and swaps IE's relay plugins: never move or rename `MilitaryRelay` | |

## Dead or excluded integrations

| Item | State |
|---|---|
| `indevo/exploration/distress/hullmods/GhostShip.java:15`, `:16` | Old MagicLib packages (`data.scripts.plugins.MagicTrailPlugin`, `data.scripts.util.MagicRender`); folder excluded in `IndEvo.iml` |
| `indevo/WIP/converters/scripts/IndEvo_TakeoverScriptWithNex.java`, `PlayerFactionSetupNag.java` | Nexerelin imports, unguarded; excluded. See [WIP](WIP.md) |

## Contracts

- Code that imports another mod's classes must only run behind its `isModEnabled` check. The class itself may load fine (inferred: the JVM resolves references lazily), but any executed line touching the foreign class throws `NoClassDefFoundError` without that mod. Guarded today: Nexerelin, RAT, Shadowyards call sites above. Keep new integrations in a separate class (as `ManagedDemocracyNexerelinListenerPlugin`) so the guard sits at one call.
- Id only integrations (items, tags, factions, buffs) need no guard; they silently do nothing when the other mod is absent. A rename on the other side breaks them silently.
- IE ids another mod reads (the meteor tag, `$missile_bonked` and `$sirhartley_skillshotActive` for Catch.Release) work the same way in reverse: renaming one on the IE side breaks the other mod silently.
- `isModEnabled` compares ids with `String.equals`, so the check is case sensitive (knowledge skill, obf `launcher/ModManager.java:196`).
- Compile time: `IndEvo.iml` libraries `ExerelinCore`, `RandomAssortmentofThings`, `SHI` (`IndEvo.iml:28`; inferred: Shadowyards Heavy Industries, the only source of the imported `data.campaign.econ.industries.MS_*` classes) and `lw_Console` must be present to build the jar.
- Non API engine classes are used in a few places, listed in [Architecture](../ARCHITECTURE.md#build). These are game version fragile, not mod fragile. See [Utils](UTILS.md) for the reflection helpers.

## Maintenance

Update this doc when a mod check, foreign import, foreign id or compat data file is added, removed or moved, and when `mod_info.json` dependencies change. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
