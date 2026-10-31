# Academy

The Academy industry (`IndEvo_Academy`): officer personality training, admin skill training, officer and admin storage, hireable person spawns, and the hidden Galatia Academy market. Read it before changing any of these or the Academy dialogue. It does not cover the Galatia research projects on the same station; see [Research](../dialogue/RESEARCH.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The Academy stores officers and admins taken out of the player fleet, trains one officer and one admin at a time, and spawns extra hireable officers and admins on its market. Officer training moves the personality one step along `timid -> cautious -> steady -> aggressive -> reckless`; admin training adds one random admin skill. NPC Academies charge a training fee and a monthly storage fee; player Academies only charge reduced salaries. IE also creates a hidden market `IndEvo_academyMarket` on the vanilla Galatia Academy station so the same menu works there. All interaction is rules.csv driven.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/academy/industry/Academy.java` | Industry plugin. Owns training state (`officerInTraining`, `adminInTraining`, day counters, `currentDirection`), both storage lists, fees, spawn rolls, officer quality mods, AI core effects. `NewDayListener` + `EconomyTickListener` |
| `indevo/industries/academy/plugins/AcademyOptionProvider.java` | Industry "Visit the Academy..." button; opens `RuleBasedInteractionDialogPluginImpl("IndEvo_FireBaseAcademyOptionList")` |
| `indevo/industries/academy/rules/IndEvo_AcademyVariables.java` | Memory key, option prefix, enum and market id constants shared by the rule commands |
| `rules/IndEvo_CreatePersonSelectionList.java` | Builds the eligible person list per action and person type, pages it by 4 into `$IndEvo_personList`; `check` param = condition only. Also `getMarket()` (Galatia special case) and `isInstalled()` |
| `rules/IndEvo_DesignateSubListPageToLoad.java`, `rules/IndEvo_DisplaySubListPage.java` | Page navigation and the person option list |
| `rules/IndEvo_OptionStartsWith.java` | Matches `IndEvo_SelectPerson_<personId>` options and stores the person in `$IndEvo_selectedPerson` |
| `rules/IndEvo_DisplayPersonActionPanel.java` | Person summary, cost and duration text, confirm options. `printPersonCharacterTooltip()` reused by the abort panel |
| `rules/IndEvo_MovePerson.java` | Executes train, store, retrieve or abort on `Academy` |
| `rules/IndEvo_PrepareForTrainingAbort.java` | Abort panel |
| `rules/IndEvo_DisplayTrainingStatus.java` | Status text in the base menu |
| `rules/IndEvo_MarketIsTraining.java`, `rules/IndEvo_PlayerHasOfficerCapacity.java` | Rule conditions |
| `rules/IndEvo_OfficerCurrentlyTraining.java` | Dead (see below) |
| `rules/WIP/IndEvo_InitPersonalityTransferMenu.java` | Excluded WIP (see below) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Academy` (`Ids.ACADEMY`) | `data/campaign/industries.csv:35`, tags `structure, unraidable` |
| Market | `IndEvo_academyMarket` on entity `station_galatia_academy` | `ModPlugin.createAcademyMarket()` (`indevo/utils/ModPlugin.java:405`) |
| Setting | `IndEvo_Academy` (toggle) | `data/config/LunaSettings.csv:95`, read in `Academy.isAvailableToBuild()` (`Academy.java:145`) |
| Setting | `IndEvo_dailyOfficerSpawnChance` 0.15, `IndEvo_dailyAdminSpawnChance` 0.10 | `LunaSettings.csv:163-164`, `Academy.onNewDay()` |
| Setting | `IndEvo_personalityTrainingDayCount` 93, `IndEvo_personalityTrainingCost` 30000 | `LunaSettings.csv:165-166` |
| Setting | `IndEvo_adminTrainingDayCount` 124, `IndEvo_adminTrainingCost` 20000 | `LunaSettings.csv:167-168` |
| Setting | `IndEvo_monthlyAIStorageCost` 2000 (per person, NPC markets) | `LunaSettings.csv:169` |
| Market memory | `$IndEvo_officerIsTraining`, `$IndEvo_adminIsTraining` | set by `Academy`, read by `IndEvo_MarketIsTraining` |
| Person memory | `$IndEvo_wasTrainedLevel` (training count) | `Academy.applyTrainingMemoryTag()` (`Academy.java:613`) |
| Person memory | `$ome_adminTier` incremented, `$ome_hireable`, `$ome_isAdmin` read | `Academy.java:412`, `:714` (vanilla `OfficerManagerEvent` keys) |
| Dialogue memory | `$IndEvo_personList`, `$IndEvo_subListPage`, `$IndEvo_actionType`, `$IndEvo_personType`, `$IndEvo_selectedPerson` (local, expire 0) | `IndEvo_AcademyVariables` |
| Entity memory | `$IndEvo_closeDialogueOnNextReturn` | `AcademyOptionProvider.onClick()` |
| Rules | `IndEvo_FireBaseAcademyOptionList`, `IndEvo_AcademyBaseMenu*`, `IndEvo_IntitiateAcademyMenu`, `IndEvo_Select*`, `IndEvo_personSelector_*`, `IndEvo_MovePersonOption*`, `IndEvo_LoadAbortDialogue_*`, `IndEvo_personWasSelected` | `data/campaign/rules.csv`, block `# Academy` to `#END Academy` |
| Sprite | `IndEvo/trainingdone` | `data/config/settings.json`, completion message icon |

## Registration and lifecycle

- Industry plugin from the `industries.csv` plugin column. Rule commands from `ruleCommandPackages` entry `indevo.industries.academy.rules` (`data/config/settings.json:238`).
- `ModPlugin.onGameLoad()` and `onNewGameAfterEconomyLoad()` call `createAcademyMarket()` (`ModPlugin.java:159`, `:319`): hidden size 3 independent market with Population and Academy, `NO_CONTACTS_ON_MARKET` and `NO_DECIV_KEY` set, only if the market is missing and `station_galatia_academy` exists.
- `AcademyOptionProvider.register()` from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:492`), transient, deduped by class.
- `Academy.apply()` adds itself as a transient listener (`Academy.java:88`); `unapply()` removes it. Daily ticks come from IE `TimeTracker`, which collects `NewDayListener` listeners ([Utils](../core/UTILS.md)).
- `NewGameIndustryPlacer.placeAcademy` seeds NPC Academies; `ModUpdater` restores Academy state on spec swap. Both in [Utils](../core/UTILS.md).

Load chain: `onGameLoad -> createAcademyMarket -> setListenersIfNeeded -> market reapply -> Academy.apply -> addListener(this, true) -> TimeTracker -> onNewDay`.

## Flow

### Menu

Entry points, all firing `IndEvo_AcademyBaseMenu`:
- Market menu option `IndEvo_IntitiateAcademyMenu` (rule `IndEvo_AcademyBaseMenu`, needs `IndEvo_marketHasIndustry IndEvo_Academy`, rep at worst inhospitable).
- Galatia station option "Visit the Academy proper" (rule `IndEvo_AcademyBaseMenu_GA`, `$id == station_galatia_academy`).
- IE colony option point `IndEvo_ColonyOptionPoint` and the industry button (`AcademyOptionProvider`).

`IndEvo_CreatePersonSelectionList.getMarket()` maps the Galatia station to `IndEvo_academyMarket` (`IndEvo_CreatePersonSelectionList.java:76`). Every other command uses `BaseCommandPlugin.getMarket(memoryMap)`.

Selection chain: `IndEvo_personSelector_<type>_<action> -> IndEvo_CreatePersonSelectionList -> IndEvo_DesignateSubListPageToLoad set 1 -> IndEvo_DisplaySubListPage -> IndEvo_personWasSelected (IndEvo_OptionStartsWith) -> IndEvo_DisplayPersonActionPanel -> IndEvo_MovePersonOption* -> IndEvo_MovePerson -> Academy`.

### Officer training

- Eligible: in fleet or officer storage, not `Misc.isUnremovable`, `$IndEvo_wasTrainedLevel` 0, or below 2 with an alpha core (`Academy.isOfficerTrainingAllowed(person)`, `:621`).
- `setOfficerForTraining()` (`:640`) removes the officer from fleet and ship command or from storage, sets direction, charges `IndEvo_personalityTrainingCost` immediately (`chargePlayer(..., false)` subtracts credits).
- `trainOfficerInTraining()` (`:492`) runs daily if functional and the market faction is not hostile to the player. After `getOfficerTrainingDays()` = setting + 1 - AI core reduction, `changeOfficerPersonality()` moves one step, increments `$IndEvo_wasTrainedLevel`, puts the officer into storage, sends a `MessageIntel`. Dev mode finishes on the next day.
- Direction limits: no `WEAKER` from timid, no `BETTER` from reckless (`isOfficerTrainingDirectionAllowed()`, `:476`), enforced only on the option buttons.

### Admin training

- Eligible: fewer than 2 admin skills and not governing a player market (`canTrainAdminPerson()`, `:363`).
- `setAdminInTraining()` (`:323`) removes from `CharacterDataAPI` admins or storage and charges `IndEvo_adminTrainingCost`.
- `addRandomAdminSkill()` (`:385`) picks a random admin skill the person lacks, skipping tags `ai_core_only`, `player_only` and id `ocua_Arc_Cognition`, and bumps `$ome_adminTier`.

### Storage and fees

- `storeOfficer`/`returnOfficer`, `storeAdmin`/`returnAdmin` move people between fleet or character data and the lists. Storing an admin who governs a player market sets the player as admin there (`storeAdmin()`, `:426`). Retrieval checks officer or admin capacity (dev mode skips the admin check).
- `reportEconomyTick()` on the last iteration of the month (`economyIterPerMonth - 1`) charges full officer salary, 10% admin salary and, on NPC markets, `IndEvo_monthlyAIStorageCost` per stored person (`:129`). NPC markets book it through `MiscIE.createMonthlyReportNode()`, player markets onto the industry's report node.

### Hireable spawns and officer quality

- Daily, on non-Galatia markets with no hireable officer (or admin) present, rolls the spawn chance and adds an `OfficerManagerEvent.AvailableOfficer` to the running vanilla officer manager event (`createHireablePerson()`, `:725`). Officers: level 1 to 3, independent, hire fee level x 2000. Admins: tier 0 (90%) or 1 (10%), hire fee from `adminHireTier<n>`.
- `apply()` on non-Galatia markets calls `increaseOfficerQuality()` (`:261`): same-faction markets in the location get `Stats.OFFICER_MAX_LEVEL_MOD` mult 1.5 (local) or 1.3 (others); the local market gets `Stats.OFFICER_PROB_MOD` flat +1.5.

### AI cores

| Core | Effect | Where |
|---|---|---|
| Alpha | officers may be trained a second time | `isOfficerTrainingAllowed()` |
| Beta | training days -31 (`betacoreReduction`) | `applyAICoreToIncomeAndUpkeep()`, `:846` |
| Gamma | upkeep x0.9 | same |

## Contracts

- All Academy state is plain fields on the industry instance and persists with the market. Stored `PersonAPI` objects exist only in those lists; removing the industry loses them (inferred: `notifyBeingRemoved` is not overridden).
- The dialogue memory keys use expire 0 in local memory and live only for the open dialogue.
- `Stats.OFFICER_PROB_MOD` on a market is read by vanilla `OfficerManagerEvent` (`api/impl/campaign/events/OfficerManagerEvent.java:118`). `Stats.OFFICER_MAX_LEVEL_MOD` is read from person stats by `FleetFactoryV3` (`api/impl/campaign/fleets/FleetFactoryV3.java:1050`); no read of the market level mod was found in `api/impl` or `obf`, so the max level mult likely has no effect (inferred).
- `Academy.COLOURS_BY_PERSONALITY` order is the personality ladder; [Pets](PETS.md) also use its key set for pet dispositions.
- `ListenerManager.addListener` dedupes by instance and drops transient listeners on save (`obf/campaign/ListenerManager.java:29`, `obf/_common/util/container/repo/ObjectRepository.java:87`), so re-adding in every `apply()` is safe.

## Known issues and dormant code

| Item | State |
|---|---|
| `rules/WIP/IndEvo_InitPersonalityTransferMenu.java` | Excluded by `IndEvo.iml:13`. Wrong package `indevo.industries.acardemy.rules.WIP`, uses removed classes (`IndEvo_Academy`, `IndEvo_ids`, Salvage Yards production code). Officer engram transfer concept only |
| `IndEvo_OfficerCurrentlyTraining` | Dead: no rule uses it, reads `$IndEvo_isTraining` which nothing sets |
| `IndEvo_MovePerson` `"better"` case | Missing `break` (`IndEvo_MovePerson.java:60-63`): falls through to `"worse"`, second `setOfficerForTraining()` changes nothing but charges the fee again |
| `isOfficerTrainingAllowed(person, direction)` | `||` with the direction check (`Academy.java:637`) lets an officer already trained twice pass whenever the direction is allowed |
| `playerHasOfficerCapacity()` | `i <= max` (`Academy.java:464`) reports capacity when the fleet is already full |
| `increaseOfficerQuality()` | `!=` on string ids (`:266`) and `return` instead of `continue` (`:269`) stop the loop at the first already modified market |
| Beta to gamma core swap | Gamma branch does not reset `currentAICoreDayReduction` (`:849`), beta reduction stays |
| Abort to fleet (admin) | No admin capacity check (`abortAdminTraining(false)`, `:344`) |
| Changelog_todo | "Academy can pick forbidden, event only admin skills and should really run off a whitelist"; "Rework academy UI"; "Story point option for academy, Recruit Local Talent" |

## Maintenance

Update this doc when the Academy menu rules, training rules, fees or settings change. Settings mechanism lives in [Settings](../core/SETTINGS.md); `TimeTracker`, `NewGameIndustryPlacer`, `ModUpdater` and option provider bases live in [Utils](../core/UTILS.md); Galatia research lives in [Research](../dialogue/RESEARCH.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
