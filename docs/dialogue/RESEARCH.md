# Research projects (Galatia Academy)

Covers the "Research Tracts" menu at the Galatian Academy: project definitions, donation and redeem flow, unlock listeners, the new projects intel, and the two hullmods given as rewards. Read it before adding or changing a project. It does not cover the IE Academy industry (officer and admin training); see [Academy](../industries/ACADEMY.md). The side panel widgets are in [Side panel](SIDE_PANEL.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

At `station_galatia_academy` the player opens a side panel listing visible research projects. Each project accepts specific items for points; donating fills progress, and a finished project is redeemed for a cargo reward (weapons and blueprints, hullmods, consumables, salvage rolls). Repeatable projects subtract the requirement on redeem and keep the rest; their Bank toggle (overflow) lets a hand-in go past 100%. Most projects stay hidden until an unlock listener sees a trigger (an omega weapon, an autopulse, a slow ship, a unique signature ship, hyperspace topography progress) and posts a "New Research Projects Available" intel.

## Files

| File | Owner / role |
|---|---|
| `indevo/dialogue/research/ResearchProjectTemplateRepo.java` | `RESEARCH_PROJECTS` static map: every project as an anonymous `ResearchProject`. `getRandomForProj` (anti save scum randoms, `:589`), `getNumProjectsPlayerCanContribute` (`:606`). |
| `dialogue/ResearchProject.java` | Abstract project: id, name, required points, repeatable. `getProgress()` lazily stores a `Progress` (points, redeemed, overflow) in sector memory (`:37`). `allowsOverflow()` is repeatable and overflow on (`:49`). |
| `dialogue/ResearchProjectAPI.java` | `display`, `getRewards`, `getRequiredItems`, short and long desc. |
| `dialogue/RequiredItem.java` | Accepted item: id, `CargoItemType`, points per unit. |
| `dialogue/DropDataCreator.java` | Builds a `SalvageEntityGenDataSpec.DropData` for reward rolls. |
| `dialogue/ResearchProjectDialoguePlugin.java` | Rule command and dialog plugin. Swaps itself in as the dialog plugin, draws text, returns to the Galatia menu via `gaMeetingEnd` (`:231`). Keeps its instance in `$IndEvo_tempDialogueInstance` (expire 0). |
| `dialogue/ResearchProjectSidePanelCreator.java` | One block per visible, unredeemed project sorted by points: Show info, Cargo, Redeem buttons, Bank toggle (repeatable only, `addOverflowToggle`, `:207`), progress bar, item count, short desc. Redeem logic lives here (`:157`). |
| `dialogue/ResearchProjectDonationCargoPicker.java` | Cargo picker filtered to accepted items; takes only what is needed to finish, or everything when `allowsOverflow()` (`:69`). |
| `intel/GalatiaNewProjectsIntel.java` | Important intel listing new project names; removes itself after 31 days (`:21`). |
| `intel/HyperspaceTopographyProjectFactor.java` | One-time factor for the vanilla Hyperspace Topography event, added on Prospector redeem. |
| `listeners/DoritoGunFoundChecker.java` | `ShowLootListener` + `EconomyTickListener`: any weapon tagged `omega` in loot, cargo or fitted unlocks Spitfire, Snowblind, Keyhole, Eureka, Parallax. |
| `listeners/AutopulseUseChecker.java` | Economy tick: fitted `autopulse` unlocks Pulse. |
| `listeners/HasUniqueShipChecker.java` | Economy tick: a fleet variant with `ship_unique_signature` unlocks Morgana. |
| `listeners/SlowShipInFleetChecker.java` | Economy tick: a member with base max burn <= 7 unlocks Sonic. |
| `listeners/HyperspaceTopoProgressChecker.java` | Economy tick: topography progress >= 100 posts the Prospector intel. |
| `listeners/CamouflageRefitListener.java` | On closing a market, swaps `ship_unique_signature` and `IndEvo_ship_unique_signature` depending on whether the Camouflage Emitter is installed. |
| `listeners/SurveyDataTooltipAmender.java` | `CommodityTooltipModifier`: "can be traded in at the Galatia Academy" on `survey_data_*`. |
| `scripts/RefitUIOpenChecker.java`, `scripts/RefitTabListener.java` | Transient polling script that reports refit tab open and close to `RefitTabListener`s. Only user: `indevo/other/legioNuclearOption/NuclearWeaponChecker.java`. |
| `hullmods/CamouflageFieldEmitter.java` | Hullmod `IndEvo_CamouflageFieldEmitter`; the stealth is done by the tag swap above. In combat attaches `ShipSpriteGlitcherOverlay` once per ship (`:19`). |
| `hullmods/ShipSpriteGlitcherOverlay.java` | Layered rendering plugin + `DamageTakenModifier`: hits >= 1000 damage show glitched sprite slices for up to 3.5 s (x0.33 on shields). |
| `hullmods/FieldModulation.java` | Hullmod `IndEvo_augfield` (Drive Field Tuning): +1 burn, +10% supply and fuel use; s-mod: -10% fuel use, x0.6 sensor profile. |
| `hullmods/BurstCapacitors.java` | Unfinished hullmod, no CSV row. Dead. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Projects | `indevo_proj_morgana`, `_pulse`, `_sanic`, `_prospector`, `_navi`, `_snowblind`, `_spitfire`, `_eureka`, `_parallax`, `_keyhole`, `_revival` (Spyglass), `_mjolnir`, `_transistor` | `indevo/ids/Ids.java:163` to `:176` |
| Hullmods | `IndEvo_augfield`, `IndEvo_CamouflageFieldEmitter` | `data/hullmods/hull_mods.csv:16`, `:17` |
| Tag | `IndEvo_ship_unique_signature` (parked vanilla tag) | `CamouflageFieldEmitter.TAG_UNIQUE` |
| Memory, sector | `$IndEvo_ResearchProgress_<id>` (`Progress`), `$IndEvo_ProjRand_<id>` (5 `Random`s) | `ResearchProject.java:39`, `ResearchProjectTemplateRepo.java:590` |
| Memory, sector | `$IndEvo_HasFoundDoritoGun`, `$IndEvo_HasFoundAutopulse`, `$IndEvo_HasUniqueShipChecker`, `$IndEvo_SlowShipFleetCheckerDone`, `$IndEvo_HyperTopoCheckerDone` | unlock listeners |
| Memory, combat engine custom data | `$IndEvo_GlitcherOverlayAttached_<shipId>` | `CamouflageFieldEmitter.java:14` |
| Settings | `IndEvo_pets` (Navi), `IndEvo_consumables_researchProject` (Mjolnir) | `data/config/LunaSettings.csv:11`, `:35`; read through `Setting` (see [Settings](../core/SETTINGS.md)) |
| Sprite | `IndEvo` / `galatia_proj` | `data/config/settings.json:197` |
| Rules | `IndEvo_JobMenuPoint`, `IndEvo_JobMenuInit` | `data/campaign/rules.csv:418`, `:419` |
| Entity | `station_galatia_academy` (vanilla) | intel map location, rules condition |

### Projects

| Project | Points | Repeat | Visible when | Inputs (points each) | Reward |
|---|---|---|---|---|---|
| Morgana | 250 | no | `HasUniqueShipChecker.isDone()` | `IndEvo_rare_parts` 1, `IndEvo_transmitter` 150 | `IndEvo_CamouflageFieldEmitter` |
| Pulse | 4 | no | autopulse found | `autopulse` 1 | 2x `hartpulse` + BP |
| Sonic | 5000 | no | slow ship found | fuel 1 | `IndEvo_augfield` |
| Prospector | 100 | yes | topography progress 101 to 999 | survey data 1/3/5/10/30 (+ `sun_data` 1 with `sun_perilous_expanse`) | topographic data item + 75 topography points |
| Navi | 200 | no | `IndEvo_pets` on | rare parts 1, `IndEvo_parts` 0.1, drone replicator 200 | `IndEvo_PetBox` fairy |
| Snowblind | 4 | no | omega gun found | `cryoflux` 2, `cryoblaster` 2 | 2x `IndEvo_cryoartillery` + BP |
| Spitfire | 4 | no | omega gun found | `amsrm` 1.5, `resonatormrm` 2, `disintegrator` 2 | 3x `IndEvo_degrader` + BP |
| Eureka | 4 | no | omega gun found | `disintegrator` 2, `rifttorpedo` 4, `realitydisruptor` 4 | 1x `IndEvo_causalitygun` + BP |
| Parallax | 4 | no | omega gun found | `minipulser` 1.5, `shockrepeater` 1.5, `vpdriver` 4 | 3x `IndEvo_pulsedcarbine` + BP |
| Keyhole | 4 | no | omega gun found | `riftlance`, `shockrepeater`, `amsrm` 1.5, `riftbeam` 2, `riftcascade` 4 | 3x `IndEvo_riftgun` + BP |
| Spyglass (`indevo_proj_revival`) | 500 | yes | always | rare parts 1 | best value of 5 salvage rolls (AI cores, blueprints, `indEvo_tech`, hullmods, rare tech); 1% chance of 9999 `vulcan` |
| Mjolnir | 120 | yes | `IndEvo_consumables_researchProject` on | rare parts 1 | 5 rolls of `indEvo_missiles_always` |
| Transistor | 80 | yes | always | rare parts 1 | 5 rolls of `indEvo_consumables_always` |

## Registration and lifecycle

- Entry: rules `IndEvo_JobMenuPoint` adds option 4 "Visit the Research Tracts" on `PopulateOptions` at `$id == station_galatia_academy`; `IndEvo_JobMenuInit` runs the command `ResearchProjectDialoguePlugin` (package `indevo.dialogue.research.dialogue` is in `ruleCommandPackages`, `data/config/settings.json:80`).
- `ModPlugin.setListenersIfNeeded` (`ModPlugin.java:431` to `:482`):

| Class | How | Persists |
|---|---|---|
| `DoritoGunFoundChecker` | `addListener(x, true)` every load | transient |
| `HyperspaceTopoProgressChecker` | `addListener(x, true)`, deduped by class | transient |
| `SlowShipInFleetChecker` | `addListener(x, true)` deduped | transient |
| `AutopulseUseChecker`, `HasUniqueShipChecker` | `addListener(x)` deduped | saved |
| `RefitUIOpenChecker` | `addTransientScript` deduped | transient |
| `CamouflageRefitListener` | `addTransientListener` | transient |
| `SurveyDataTooltipAmender` | `addListener(x, true)` | transient |

- `PetManagementAbilityPlugin.applySetting` reads Navi's progress to unlock fairy sales (`indevo/abilities/pets/PetManagementAbilityPlugin.java:34`, see [Pets](../industries/PETS.md)).

Order: `PopulateOptions -> IndEvo_JobMenuPoint -> option -> IndEvo_JobMenuInit -> ResearchProjectDialoguePlugin.execute -> dialog.setPlugin(this) -> ResearchProjectSidePanelCreator`.

## Flow

1. `execute` stores the original plugin, swaps in itself, `init` creates the side panel (`VisualCustomPanel.createPanel`) and text.
2. Show info -> `setProjectIdForInputs` prints long desc and per item progress, owned count and units to finish.
3. Cargo -> `ResearchProjectDonationCargoPicker.init`: copies matching stacks from player cargo, the picker preview shows percent per stack; on confirm each stack adds `size x points` or only the units needed to finish, removed from the real cargo (`:81`). With overflow every picked unit is taken. Cargo is disabled on a finished project unless overflow is on.
4. Redeem -> `project.getRewards()` added to cargo; repeatable subtracts the required points (floor 0), else `redeemed = true`; text shows `addTooltipOutputOnCompletion` and gain lines. Redeem stays enabled while points still cover another completion.
5. Bank toggle -> `addAreaCheckbox` (label `OVERFLOW_LABEL`) with a vanilla style hover tooltip (`TooltipCreator`, width 400, left), flips `Progress.overflow` and rebuilds the panel.
6. Return -> clear panel, restore original plugin, set `$option = gaMeetingEnd` and fire `DialogOptionSelected` (vanilla Galatia rule ends the conversation).

Unlock path: `listener tick -> set memory flag -> GalatiaNewProjectsIntel.notifyNewProjects(ids) -> project.display() true`.

## Contracts

- Project definitions live only in code; progress and reward randoms live in sector memory and persist. Renaming a project id orphans its progress.
- Overflow is per project and off by default; `Progress.overflow` is false on saves made before the field existed. Turning it off keeps points already past 100%. The progress bar is capped at full width, the label shows the real percentage.
- `getRandomForProj` stores five `java.util.Random` objects in memory, so a reload reproduces the same rolls (anti save scum). Each roll advances the stored randoms.
- Camouflage: the engine marks the player fleet as having a unique signature when any member variant has `ship_unique_signature` (`obf/campaign/fleet/CampaignFleet.java:963`). The emitter works by renaming that tag; the swap only runs on `reportPlayerClosedMarket`, so installing the hullmod takes effect after leaving the market.
- Shared helper: `indevo.industries.salvageyards.rules.IndEvo_InitSYCustomProductionDiag` is imported only for an `instanceof` check that is never true (`ResearchProjectDialoguePlugin.java:48`).

## Known issues and dormant code

| Item | State |
|---|---|
| `BurstCapacitors` | unregistered, unfinished tooltip; dead |
| Mjolnir rolls | uses `getRandomForProj(Ids.PROJ_TRANSISTOR)` (`ResearchProjectTemplateRepo.java:523`), shares randoms with Transistor (suspected copy paste) |
| Spyglass | `extra` stays null if all five rolls are worth 0; the 1% branch or the caller then hits null (`ResearchProjectTemplateRepo.java:487`, `:492`) |
| `GalatiaNewProjectsIntel` | `getMapLocation` uses entity id `station_galatia_academy`, null if absent |
| Prospector overflow | points past 100% stay in memory but stop showing once topography progress reaches 1000 and `display()` turns false |
| `RefitUIOpenChecker` | polls `getCurrentCoreTab()` every frame; no engine callback for refit open was checked for |

## Maintenance

Update this doc when a project, input, reward, unlock trigger or the entry rule changes. Hullmod behaviour beyond these two belongs in [Combat](../core/COMBAT.md); pet unlocks in [Pets](../industries/PETS.md); consumable drop groups in [Consumables](../items/CONSUMABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
