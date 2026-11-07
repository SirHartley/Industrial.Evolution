# Compatibility

Every place IE touches another mod: hard dependencies, optional code paths, and data files read by other mods. Read it before changing a mod check, adding an integration, or after another mod renames an id. Feature behaviour behind each hook lives in the linked feature doc.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

Mod checks use `Global.getSettings().getModManager().isModEnabled(id)`. Code paths are relative to `jars/src/`.

## Hard dependencies

Declared in `mod_info.json` `dependencies`; the game refuses to load IE without them.

| Mod (id) | What IE uses | Where |
|---|---|---|
| LazyLib (`lw_lazylib`) | `org.lazywizard.lazylib` math, collision, combat and campaign utils | Imported in 73 files (grep), for example `data/scripts/weapons/ai/IndEvo_missileProjectileAI.java` |
| MagicLib (`MagicLib`) | `MagicRender` (mortar and missile AI, slow field), `MagicFakeBeamPlugin` (railgun targeting beam), `MagicTrailObject` (campaign trails), `MagicVariables` (unused tracker); projectile trails from `data/config/modFiles/magicTrail_data.csv` | `data/scripts/weapons/ai/IndEvo_mortarProjectileAI.java`, `IndEvo_missileProjectileAI.java`, `combatEntities/IndEvo_CombatSlowFieldTerrain.java`, `IndEvo_targetingBeamEffect.java`, `indevo/utils/trails/MagicCampaignTrailPlugin.java`, `indevo/other/PlayerHyperspaceTripTracker.java`. See [Combat](COMBAT.md) |
| LunaLib (`lunalib`) | Settings (`indevo.config.PlayerSettings`: `LunaSettings`, `LunaSettingsListener`, backend `LunaSettingsLoader.getSettings()` for the write back only; `data/config/LunaSettings.csv`); campaign renderers (`LunaCampaignRenderer`, `LunaCampaignRenderingPlugin`) for crucible, meteor, particles, consumable missiles | 24 files (grep); settings read only in `indevo/config/PlayerSettings.java:80-91`, no fallback. See [Settings](SETTINGS.md) |
| GraphicsLib (`shaderLib`) | `ShaderLib.init()`, lights and normal maps from `data/lights/IndEvo_lights.csv`, `IndEvo_textures.csv`; distortion in meteor radiation VFX | `indevo/utils/ModPlugin.java:121` (guarded), `indevo/exploration/meteor/renderers/RadiationVFXHandler.java:9` (unguarded import) |
| PatchLib (`patchlib`) | Runtime patches on vanilla classes (`patchlib.api`); compile against `PatchLibAPI.jar` only | `@Patch` classes, listed in [Architecture](../ARCHITECTURE.md#patches) |

The `shaderLib` and `lunalib` checks are redundant while both are hard dependencies; they only matter if a dependency is dropped from `mod_info.json`.

## Optional integrations

| Mod (id) | Kind | What IE uses | Where |
|---|---|---|---|
| Console Commands (`lw_console`) | Optional, data driven | 7 commands in `data/console/commands.csv`, classes import `org.lazywizard.console`. Loaded only by Console Commands, so no guard needed (inferred) | `indevo/console/`. See [Console](CONSOLE.md) |
| Nexerelin (`nexerelin`) | Optional, code | Changeling Managed Democracy invasion listener (`exerelin.campaign.InvasionRound`, `exerelin.utilities.InvasionListener`) | `indevo/utils/ModPlugin.java:473`, `indevo/industries/changeling/listener/ManagedDemocracyNexerelinListenerPlugin.java:13`, `:14`. See [Changeling](../industries/CHANGELING.md) |
| Nexerelin | Optional, code | Pirate subpopulation reads `NexConfig` / `NexFactionConfig` | `indevo/industries/privateer/conditions/PirateSubpopulationCondition.java:88` (imports `:14`, `:15`). See [Privateer](../industries/PRIVATEER.md) |
| Nexerelin | Optional, code | Ambassador uses `DiplomacyManager` | `indevo/industries/embassy/listeners/AmbassadorPersonManager.java:111` (import `:19`). See [Embassy](../industries/EMBASSY.md) |
| Nexerelin | Optional, id only | Rift Generator skips markets tagged `nex_playerOutpost`; Deconstructor refuses ships with buff `nex_mercBuff` unless `IndEvo_DeconAllowNexOmega` | `indevo/industries/derelicts/industry/RiftGenerator.java:279`, `indevo/submarkets/impl/DeconstructorSubmarketPlugin.java:73` |
| Nexerelin | Optional, data | Faction config for `IndEvo_converters` (WIP faction) | `data/config/exerelinFactionConfig/mod_factions.csv`, `IndEvo_converters.json`. See [WIP](WIP.md) |
| Random Assortment of Things (`assortment_of_things`) | Optional, code | Courier Port targets the RAT settlement market (`FrontiersUtils`, `SettlementData`) | `indevo/industries/courierport/ShippingContract.java:88` (import `:3`), `ShippingTargetHelper.java:27`, `:50` (imports `:3`, `:4`). See [Courier Port](../industries/COURIER_PORT.md) |
| Random Assortment of Things | Optional, id only | Storage fallback `rat_settlement_storage`; ship component loot weight for `rat_abyssals`; Hull Forge counts tag `rat_alteration` | `indevo/utils/helper/MiscIE.java:69`, `indevo/items/listeners/ShipComponentLootManager.java:35`, `indevo/industries/derelicts/industry/HullForge.java:303` |
| Shadowyards (`shadow_ships`) | Optional, code | `MS_modularFac`, `MS_fabUpgrader` in ship parts supply and demand and in Salvage Yards | `indevo/economy/conditions/ResourceCondition.java:69` (imports `:14` to `:16`), `indevo/industries/salvageyards/industry/SalvageYards.java:138` (imports `:25`, `:26`). See [Economy](ECONOMY.md), [Salvage Yards](../industries/SALVAGE_YARDS.md) |
| Ship/Weapon Pack (`swp`) | Optional, id only | Derelict faction friendly to `famous_bounty` | `indevo/utils/ModPlugin.java:519` |
| MagicLib bounties | Hard (MagicLib), id only | Derelict faction friendly to `ML_bounty`, no check | `indevo/utils/ModPlugin.java:516` |
| Alcoholism (`alcoholism`) | Optional, id only | Privateer Base commodity bonus | `indevo/industries/privateer/industry/PrivateerBase.java:709`. See [Privateer](../industries/PRIVATEER.md) |
| Tiandong Heavy Industries | Optional, id only | Special item `tiandong_retrofit_bp` handled by Engineering Hub, blueprint installable and Privateer loot; no mod check | `indevo/industries/engineeringhub/industry/EngineeringHub.java:234`, `:288`, `:1085`, `indevo/items/installable/BlueprintInstallableItemPlugin.java:103`, `indevo/industries/privateer/industry/PrivateerBase.java:323` |
| Roider Union (`roider`) | Optional, id only | Item `roider_retrofit_bp` and hull tag `roider_retrofit` (same places as Tiandong); meteor tutorial fleet uses faction `roider` when enabled | `EngineeringHub.java:284`, `:280`, `BlueprintInstallableItemPlugin.java:131`, `PrivateerBase.java:325`, `indevo/exploration/meteor/scripts/TutorialEncounterScript.java:41` |
| Secrets of the Frontier / Perilous Expanse (`sun_perilous_expanse`) | Optional, id only | Research project also requires commodity `sun_data` | `indevo/dialogue/research/ResearchProjectTemplateRepo.java:200`. See [Research](../dialogue/RESEARCH.md) |
| Hullmods containing `sun_sl` | Optional, id only | Stripped from variants made by forge templates | `indevo/items/ForgeTemplateItemPlugin.java:422` |
| Prism Freeport | Optional, data | Boss ship blacklist read for Deconstructor and Reverse Engineering | `data/config/prism/prism_boss_ships.csv`, `prism_ships_blacklist.csv` (placeholders), read by `indevo/utils/helper/MiscIE.java:571`. See [Submarkets](SUBMARKETS.md) |
| Vayra's Sector bounties | Optional, data | Unique bounty flagships blacklisted the same way (column `flagshipVariantId`) | `data/config/vayraBounties/unique_bounty_data.csv` (header only), `MiscIE.java:600` |
| Prism / Vayra rows from other mods | Optional, data | Rows from mods named in a row are only used if `canLoadShips(factionOrModId)` (faction exists or mod enabled) | `MiscIE.java:564`, `:587`, `:615` |
| Catch.Release (`catchrelease`) | Optional, id only, read by the other mod | Its harpoon finds meteors by tag `IndEvo_MeteorSwarm` and breaks the one it hits by setting `$missile_bonked` in its memory. Both mods ship the skillshot framework in their own package and share the sector memory lock `$sirhartley_skillshotActive` (owner id) so only one aims at a time; Catch.Release does not set it yet. IE holds no Catch.Release code | `MeteorSwarmManager.METEOR_TAG`, `MeteorEntity.init`, `MissileMemFlags.MEM_CAUGHT_BY_MISSILE`, `SkillshotSettings.LOCK_KEY`. See [Meteor swarms](../exploration/METEOR.md#contracts), [Skillshot README](../../jars/src/indevo/skillshot/README.md#cross-mod-lock) |
| Building Menu Overhaul (Kaysaar, required by Ashes of the Domain) | None, not patched | Replaces the vanilla build menu and builds its own tooltips, so Mandated Overtime's halved days do not show there. IE never patches it; `FastBuildPickerRowPatch` still runs on the vanilla rows BMO dismisses and stays read only | `indevo/industries/senate/patches/`. See [Senate](../industries/SENATE.md#build-time-display) |
| Better Colonies | Optional, data | AI industry spawn weights for IE industries | `data/config/BetterColonyConfig/bc_autospindustries.csv` |
| Version Checker | Optional, data | Update check (GitHub raw master file, thread 18011) | `IndEvo.version`, `data/config/version/version_files.csv` |

## Dead or excluded integrations

| Item | State |
|---|---|
| `indevo/industries/compatibility/TechTree.java:11` | Checks `Cryo_but_better`; class has no reference (dead) |
| `indevo/other/YourMod_PetName_PetHullmod.java:31`, `:48` | Checks `indEvo`, IE's id is `IndEvo` and the check is case sensitive, so the template never runs as written; unregistered. See [Misc](MISC.md) |
| `indevo/exploration/distress/hullmods/GhostShip.java:15`, `:16` | Old MagicLib packages (`data.scripts.plugins.MagicTrailPlugin`, `data.scripts.util.MagicRender`); folder excluded in `IndEvo.iml` |
| `indevo/WIP/converters/scripts/IndEvo_TakeoverScriptWithNex.java`, `PlayerFactionSetupNag.java` | Nexerelin imports, unguarded; excluded. See [WIP](WIP.md) |

## Contracts

- Code that imports another mod's classes must only run behind its `isModEnabled` check. The class itself may load fine (inferred: the JVM resolves references lazily), but any executed line touching the foreign class throws `NoClassDefFoundError` without that mod. Guarded today: Nexerelin, RAT, Shadowyards call sites above. Keep new integrations in a separate class (as `ManagedDemocracyNexerelinListenerPlugin`) so the guard sits at one call.
- Id only integrations (items, tags, factions, buffs) need no guard; they silently do nothing when the other mod is absent. A rename on the other side breaks them silently.
- IE ids another mod reads (the meteor tag, `$missile_bonked` and `$sirhartley_skillshotActive` for Catch.Release) work the same way in reverse: renaming one on the IE side breaks the other mod silently.
- `isModEnabled` compares ids with `String.equals`, so the check is case sensitive (knowledge skill, obf `launcher/ModManager.java:196`).
- Compile time: `IndEvo.iml` libraries `ExerelinCore`, `RandomAssortmentofThings`, `SHI` (`IndEvo.iml:28`; inferred: Shadowyards Heavy Industries, the only source of the imported `data.campaign.econ.industries.MS_*` classes) and `lw_Console` must be present to build the jar.
- Non API engine classes are used in a few places (for example `com.fs.starfarer.combat.CombatEngine`, `com.fs.graphics.particle.*` in `data/scripts/weapons/IndEvo_missileProjectileOnFireEffect.java:3`, `:4`, `:10`; `com.fs.starfarer.campaign.BaseCampaignEntity` in `indevo/console/PrintModData.java:8`). These are game version fragile, not mod fragile. See [Utils](UTILS.md) for the reflection helpers.

## Maintenance

Update this doc when a mod check, foreign import, foreign id or compat data file is added, removed or moved, and when `mod_info.json` dependencies change. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
