# Admin skills

Covers the four IE administrator skills and the Micromanagement remote item management hook. Read it before changing a `.skill` file under `data/characters/skills/` or anything in `indevo/abilities/skills/`. It does not cover how the Academy trains or stores admins; see [Academy](../industries/ACADEMY.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

IE adds four admin only skills, tagged `npc_only`, so they never show in the player skill tree but are rolled onto hired administrators. Three are plain `MarketSkillEffect`s on the governed colony (accessibility and fleet size, upkeep and income, ground defense and stability). Micromanagement has no stat effect: a script flips the vanilla `allowRemoteIndustryItemManagement` setting while the player looks at a colony whose admin has the skill and has governed it for more than 93 days.

## Files

| File | Owner / role |
|---|---|
| `indevo/abilities/skills/FleetLogistics.java` | `Market1`: +0.15 accessibility, +25% `COMBAT_FLEET_SIZE_MULT` (flat 0.25). |
| `indevo/abilities/skills/IndustrialPlanning.java` | `Level1B`: upkeep x0.9, income x1.1. Source label "Distributive Economics". |
| `indevo/abilities/skills/PlanetaryOperations.java` | `Level1B`: `GROUND_DEFENSES_MOD` x1.5, +1 stability. |
| `indevo/abilities/skills/MicroManagement.java` | `Level1`: description only, `apply` is empty. |
| `indevo/abilities/skills/scripts/AdminGovernTimeTracker.java` | Persistent `NewDayListener`; days in office per player market, reset when the admin id changes (`:48`). |
| `indevo/abilities/skills/scripts/MicromanagementSkillEffectScript.kt` | Persistent sector `EveryFrameScript`, runs while paused. Finds the market of the open industry panel by reflection and sets `allowRemoteIndustryItemManagement` (`:91`). |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Skill | `indevo_fleet_logistics` (Fleet Logistics, aptitude `leadership`) | `data/characters/skills/skill_data.csv`, `indevo_fleet_logistics.skill` |
| Skill | `indevo_industrial_planning` (Distributive Economics, `industry`) | same, `indevo_industrial_planning.skill` |
| Skill | `indevo_planetary_operations` (Planetary Operations, `leadership`) | same, `indevo_planetary_operations.skill` |
| Skill | `indevo_Micromanagement` (Micromanagement, `industry`), `Ids.MICROMANAGEMENT` | same, `indevo_Micromanagement.skill`, `indevo/ids/Ids.java:138` |
| Skill tags | `npc_only, indevo`; `admin` column `TRUE` | `skill_data.csv` |
| Vanilla setting | `allowRemoteIndustryItemManagement` | game `data/config/settings.json:300` (default `false`) |
| Memory, sector | `$IndEvo_BaseRemoteAllowed` (the setting's value at load) | set in `ModPlugin.java:199`, read in `MicromanagementSkillEffectScript.kt:92` |
| Unused ids | `Ids.SKILL_FLEET_LOGISTICS`, `SKILL_INDUSTRIAL_PLANNING`, `SKILL_PLANETARY_OPERATIONS` (`indevo_FleetLogistics` etc.) | `Ids.java:135`; do not match the real skill ids, nothing reads them |

## Registration and lifecycle

- Skills load from `data/characters/skills/*.skill`; each has one `GOVERNED_OUTPOST` effect whose `script` names the inner class (`indevo.abilities.skills.FleetLogistics$Market1` etc.).
- Admins get them through vanilla `OfficerManagerEvent.createAdmin`, which picks from every `isAdminSkill()` skill that is not deprecated, `player_only` or `ai_core_only` (`api/impl/campaign/events/OfficerManagerEvent.java:322`). `npc_only` only hides them from the player tree (`api/util/SkillData.java:56`).
- `ModPlugin.setListenersIfNeeded` -> `MicromanagementSkillEffectScript.register()` (`ModPlugin.java:482`, deduped by `hasScript`) and `AdminGovernTimeTracker.getInstanceOrRegister()` (`ModPlugin.java:483`, deduped by `hasListenerOfClass`). Both persist in the save.

Order: `onGameLoad -> $IndEvo_BaseRemoteAllowed = setting -> register script and tracker -> new day: tracker counts -> paused UI frame: script sets setting`.

## Flow

`MicromanagementSkillEffectScript.advance`:

1. Return if `$IndEvo_BaseRemoteAllowed` (the user already allows remote management globally).
2. Only while the sector is paused and after 2 frames.
3. `getMarket()` walks the campaign UI by reflection: `CampaignState.getEncounterDialog().getCoreUI()` or `getCore()`, current tab, child with `getOutpostPanelParams`, child with `showOverview`, child with `recreateWithEconUpdate`, then the `IndustryListPanel` child and its `market` field (`:32`).
4. If the admin has `indevo_Micromanagement` and `AdminGovernTimeTracker.getValueForMarket > 93` (or dev mode), set the setting `true`, else `false`.

The vanilla industry options dialog reads the setting when it is built (`obf/campaign/ui/marketinfo/_c37.java:112`, obfuscated class `b`), so the flag has to be right before the player opens an industry's menu.

## Contracts

- `Global.getSettings().setBoolean` changes the setting for the whole session. The script resets it to `false` on every other market it sees, which is why it bails out when the base value was `true`.
- The script imports game internals `com.fs.starfarer.campaign.CampaignState` and `com.fs.starfarer.campaign.ui.marketinfo.IndustryListPanel` (both exist with these names in 0.98a-RC8, `obf/campaign/CampaignState.java`, `obf/campaign/ui/marketinfo/IndustryListPanel.java`) and finds the rest by method name. Recheck after a game update. Reflection helper is `indevo.utils.helper.ReflectionUtils` (see [Utils](../core/UTILS.md)).
- The tracker only counts while the game runs new days and only for player markets; days start at the first new day after the skill script sees the market, not at hire time.
- Effect scripts are one shared instance per skill; they hold no state.

## Known issues and dormant code

| Item | State |
|---|---|
| `Ids.SKILL_*` constants | wrong ids, unused |
| `MicromanagementSkillEffectScript.newIndustryPanel`, `reset` | unused fields |
| `HullmodTimeTracker` (`indevo/exploration/distress/listener/HullmodTimeTracker.java:19`) | copy of the tracker pattern, checks `AdminGovernTimeTracker.class` but fetches its own class; folder excluded by `IndEvo.iml`, registration commented out in `ModPlugin` |
| Academy skill picks | Changelog_todo: "Academy can pick forbidden, event only admin skills and should really run off a whitelist" (see [Academy](../industries/ACADEMY.md)) |

## Maintenance

Update this doc when a skill is added, a value changes, or the Micromanagement reflection path breaks after a game update. Admin training belongs in [Academy](../industries/ACADEMY.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
