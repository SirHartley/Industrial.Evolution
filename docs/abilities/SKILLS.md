# Admin skills

Covers the four IE administrator skills and the Micromanagement remote item management hook. Read it before changing a `.skill` file under `data/characters/skills/` or anything in `indevo/abilities/skills/`. It does not cover how the Academy trains or stores admins; see [Academy](../industries/ACADEMY.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

IE adds four admin only skills, tagged `npc_only`, so they never show in the player skill tree but are rolled onto hired administrators. Three are plain `MarketSkillEffect`s on the governed colony (accessibility and fleet size, upkeep and income, ground defense and stability). Micromanagement has no stat effect: a PatchLib redirect makes the industry option menu treat the vanilla `allowRemoteIndustryItemManagement` setting as true for a colony whose admin has the skill and has governed it for more than 93 days.

## Files

| File | Owner / role |
|---|---|
| `indevo/abilities/skills/FleetLogistics.java` | `Market1`: +0.15 accessibility, +25% `COMBAT_FLEET_SIZE_MULT` (flat 0.25). |
| `indevo/abilities/skills/IndustrialPlanning.java` | `Level1B`: upkeep x0.9, income x1.1. Source label "Distributive Economics". |
| `indevo/abilities/skills/PlanetaryOperations.java` | `Level1B`: `GROUND_DEFENSES_MOD` x1.5, +1 stability. |
| `indevo/abilities/skills/MicroManagement.java` | `Level1`: description only, `apply` is empty. |
| `indevo/abilities/skills/scripts/AdminGovernTimeTracker.java` | Persistent `NewDayListener`; days in office per player market, reset when the admin id changes (`:48`). |
| `indevo/abilities/skills/patches/MicromanagementRemoteItemPatch.java` | PatchLib redirect of the setting read in the industry option menu constructor. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Skill | `indevo_fleet_logistics` (Fleet Logistics, aptitude `leadership`) | `data/characters/skills/skill_data.csv`, `indevo_fleet_logistics.skill` |
| Skill | `indevo_industrial_planning` (Distributive Economics, `industry`) | same, `indevo_industrial_planning.skill` |
| Skill | `indevo_planetary_operations` (Planetary Operations, `leadership`) | same, `indevo_planetary_operations.skill` |
| Skill | `indevo_Micromanagement` (Micromanagement, `industry`), `Ids.MICROMANAGEMENT` | same, `indevo_Micromanagement.skill`, `indevo/ids/Ids.java:137` |
| Skill tags | `npc_only, indevo`; `admin` column `TRUE` | `skill_data.csv` |
| Vanilla setting | `allowRemoteIndustryItemManagement` | game `data/config/settings.json:300` (default `false`) |
| Unused ids | `Ids.SKILL_FLEET_LOGISTICS`, `SKILL_INDUSTRIAL_PLANNING`, `SKILL_PLANETARY_OPERATIONS` (`indevo_FleetLogistics` etc.) | `Ids.java:134`; do not match the real skill ids, nothing reads them |

## Registration and lifecycle

- Skills load from `data/characters/skills/*.skill`; each has one `GOVERNED_OUTPOST` effect whose `script` names the inner class (`indevo.abilities.skills.FleetLogistics$Market1` etc.).
- Admins get them through vanilla `OfficerManagerEvent.createAdmin`, which picks from every `isAdminSkill()` skill that is not deprecated, `player_only` or `ai_core_only` (`api/impl/campaign/events/OfficerManagerEvent.java:322`). `npc_only` only hides them from the player tree (`api/util/SkillData.java:56`).
- `ModPlugin.setListenersIfNeeded` -> `AdminGovernTimeTracker.getInstanceOrRegister()` (`ModPlugin.java:472`, deduped by `hasListenerOfClass`), persistent.
- `MicromanagementRemoteItemPatch` is a `@Patch` class; PatchLib installs it when the option menu class loads ([Patches](../ARCHITECTURE.md#patches)).

## Flow

The vanilla industry option menu (obfuscated `b` in `com.fs.starfarer.campaign.ui.marketinfo`, implements `DialogCreatorUI`, skill `obf/campaign/ui/marketinfo/_c37.java:66`) reads `allowRemoteIndustryItemManagement` once in its constructor (`:112`) through a static `(String) -> boolean` call on `StarfarerSettings`, and uses that value for every item and AI core button in the menu.

`MicromanagementRemoteItemPatch.redirectSettingRead` runs on that call:

1. Calls the original. A `true` value (the user allows remote management globally) passes through.
2. For the key `allowRemoteIndustryItemManagement` only, finds the `Industry` among the constructor args and checks its market: player owned, admin has `indevo_Micromanagement`, and `AdminGovernTimeTracker.getValueForMarket > 93` or dev mode.
3. Returns `true` if that holds, else the original value.

## Contracts

- The patch never writes the setting; other code that reads it sees the user's value.
- The target is inferred: host matched by `subtype = DialogCreatorUI.class` plus package, call matched by shape (static, `String` in, `boolean` out, owner `StarfarerSettings` by name). No obfuscated names in code. Recheck after a game update: `starsector.log` must show the redirect installed for the option menu class.
- The tracker only counts while the game runs new days and only for player markets; days start at the first new day the tracker sees the market with that admin, not at hire time.
- Effect scripts are one shared instance per skill; they hold no state.

## Known issues and dormant code

| Item | State |
|---|---|
| `Ids.SKILL_*` constants | wrong ids, unused |
| `HullmodTimeTracker` (`indevo/exploration/distress/listener/HullmodTimeTracker.java:19`) | copy of the tracker pattern, checks `AdminGovernTimeTracker.class` but fetches its own class; folder excluded by `IndEvo.iml`, registration commented out in `ModPlugin` |
| Academy skill picks | Changelog_todo: "Academy can pick forbidden, event only admin skills and should really run off a whitelist" (see [Academy](../industries/ACADEMY.md)) |

## Maintenance

Update this doc when a skill is added, a value changes, or the Micromanagement patch stops installing after a game update. Admin training belongs in [Academy](../industries/ACADEMY.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
