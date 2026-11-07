# Academy

The Academy industry (`IndEvo_Academy`): officer personality training, admin skill training, officer and admin storage, hireable person spawns, and the hidden Galatia Academy market. Read it before changing any of these or the Academy dialogue. It does not cover the Galatia research projects on the same station; see [Research](../dialogue/RESEARCH.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The Academy stores officers and admins taken out of the player fleet, trains one officer and one admin at a time, and spawns extra hireable officers and admins on its market. Officer training moves the personality one step along `timid -> cautious -> steady -> aggressive -> reckless`; admin training adds one random admin skill. NPC Academies charge a training fee and a monthly storage fee; player Academies only charge reduced salaries. IE also creates a hidden market `IndEvo_academyMarket` on the vanilla Galatia Academy station so the same menu works there. All interaction is rules.csv driven.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/academy/industry/Academy.java` | Industry plugin. Owns training state (`officerInTraining`, `officerTrainingTarget`, `adminInTraining`, day counters, `currentDirection`), both storage lists, fees, spawn rolls, officer quality mods, AI core effects. `NewDayListener` + `EconomyTickListener` |
| `indevo/industries/academy/plugins/AcademyOptionProvider.java` | Industry "Visit the Academy..." button; opens `AcademyDialogPlugin` standalone |
| `plugins/AcademyDialogPlugin.java` | Panel host: `InteractionDialogPlugin`, `EscapeBlockingDialoguePluginAPI`. Standalone (closes the dialog) or rules mode (returns to the rule dialog). Switches between panel and popup, handles ESC |
| `plugins/AcademyPanelDelegate.java` | The panel: tabs, fees line, training slot, person rows, buttons with tooltips. Sizes as constants |
| `plugins/AcademyTrainingPopupDelegate.java` | Officer training popup: five personality squares, summary, confirm |
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
| Market | `IndEvo_academyMarket` on entity `station_galatia_academy` | `ModPlugin.createAcademyMarket()` (`indevo/utils/ModPlugin.java:382`) |
| Setting | `IndEvo_Academy` (toggle) | `data/config/LunaSettings.csv:102`, read in `Academy.isAvailableToBuild()` (`Academy.java:142`) |
| Setting | `IndEvo_dailyOfficerSpawnChance` 0.15, `IndEvo_dailyAdminSpawnChance` 0.10 | `LunaSettings.csv:166-167`, `Academy.onNewDay()` |
| Setting | `IndEvo_personalityTrainingDayCount` 93, `IndEvo_personalityTrainingCost` 30000 | `LunaSettings.csv:168-169` |
| Setting | `IndEvo_adminTrainingDayCount` 124, `IndEvo_adminTrainingCost` 20000 | `LunaSettings.csv:170-171` |
| Setting | `IndEvo_monthlyAIStorageCost` 2000 (per person, NPC markets) | `LunaSettings.csv:172` |
| Market memory | `$IndEvo_officerIsTraining`, `$IndEvo_adminIsTraining` | set by `Academy`, read by `IndEvo_MarketIsTraining` |
| Person memory | `$IndEvo_wasTrainedLevel` (personality steps used) | `Academy.addTrainedSteps()` |
| Person memory | `$ome_adminTier` incremented, `$ome_hireable`, `$ome_isAdmin` read | `Academy.java:389`, `:691` (vanilla `OfficerManagerEvent` keys) |
| Dialogue memory | `$IndEvo_personList`, `$IndEvo_subListPage`, `$IndEvo_actionType`, `$IndEvo_personType`, `$IndEvo_selectedPerson` (local, expire 0) | `IndEvo_AcademyVariables` |
| Rules | `IndEvo_FireBaseAcademyOptionList`, `IndEvo_AcademyBaseMenu*`, `IndEvo_IntitiateAcademyMenu`, `IndEvo_Select*`, `IndEvo_personSelector_*`, `IndEvo_MovePersonOption*`, `IndEvo_LoadAbortDialogue_*`, `IndEvo_personWasSelected` | `data/campaign/rules.csv`, block `# Academy` to `#END Academy` |
| Sprite | `IndEvo/trainingdone` | `data/config/settings.json`, completion message icon |

## Registration and lifecycle

- Industry plugin from the `industries.csv` plugin column. Rule commands from `ruleCommandPackages` entry `indevo.industries.academy.rules` (`data/config/settings.json:72`).
- `ModPlugin.onGameLoad()` and `onNewGameAfterEconomyLoad()` call `createAcademyMarket()` (`ModPlugin.java:154`, `:296`): hidden size 3 independent market with Population and Academy, `NO_CONTACTS_ON_MARKET` and `NO_DECIV_KEY` set, only if the market is missing and `station_galatia_academy` exists.
- `AcademyOptionProvider.register()` from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:469`), transient, deduped by class.
- `Academy.apply()` adds itself as a transient listener (`Academy.java:86`); `unapply()` removes it. Daily ticks come from IE `TimeTracker`, which collects `NewDayListener` listeners ([Utils](../core/UTILS.md)).
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

### Panel

`AcademyPanelDelegate`, shown with `InteractionDialogAPI.showCustomDialog()` at `getWidth()` x `getHeight()`: `min(1104, screen width - 100)` and `min(850, screen height - 100)` for the whole dialog, minus the 20 x 55 px the custom dialog adds for its frame and button row (translation of obfuscated code, `obf/ui/newui/super.java:52-53`).

- Tabs `Officers n / max` and `Administrators n / max`, credits on the right. Fees line per tab and market owner.
- In training: the person of the active tab with target, days left and a bar, a paused note when the Academy is not functional or the market is hostile, `Abort to fleet` (disabled without a free slot) and `Abort to storage`. No refund.
- Rows: fleet officers (`getFleetOfficers()`, unremovable ones left out) or staff admins (`getStaffAdmins()`) first, then stored ones, each by level or admin skill count. Header `Name - In fleet`, `On staff` or `Stored here`. Assignment box left of the portrait: commanded ship (`getCommandedShip()`, `addShipList`), governed market (`getGovernedMarket()`, `showPlanetInfo`, faction crest for markets without a planet), else `Unassigned`. Skill icons with name tooltips, elite skills framed. `Train` plus `Store` or `Retrieve`.
- Every button is disabled with the reason from `Academy` as tooltip (`getOfficerTrainBlocker()`, `getAdminTrainBlocker()`, `getOfficerSlotBlocker()`, `getAdminSlotBlocker()`); enabled ones show cost and time. Store on a governing admin warns that the player takes over.
- Officer `Train` dismisses the panel and opens `AcademyTrainingPopupDelegate`: the five personalities in ladder order, current one marked, reachable and affordable ones selectable, the nearest preselected. Confirm calls `setOfficerForTraining(person, targetId)`. Confirm, Cancel and ESC reopen the panel on the next frame (`AcademyDialogPlugin.showPanelNextFrame()`); the custom dialog slot is only free after the dismiss.
- Admin `Train` starts at once. ESC and Return close the panel. Scroll position is kept across rebuilds and the popup.

### Officer training

- Steps: the distance on the personality ladder (`getPersonalitySteps()`). An officer has one lifetime step, two with an alpha core (`getMaxOfficerSteps()`); `$IndEvo_wasTrainedLevel` counts the steps used (`getOfficerStepsLeft()`). One training may use all steps left at once.
- `setOfficerForTraining(person, targetId)` checks that no officer is training, the officer is removable, the target is 1 to steps left away and the fee is affordable, then removes the officer from fleet and ship command or from storage, stores the target in `officerTrainingTarget` and charges the fee. Fee and days are per step: `getOfficerTrainingFee(steps)` (`IndEvo_personalityTrainingCost` per step, 0 on player markets), `getOfficerTrainingDays(steps)` (`IndEvo_personalityTrainingDayCount` + 1 - AI core reduction, per step). The fee is taken at once with `BillingHelper.chargeNow` ([BillingHelper](../core/UTILS.md#billinghelper)).
- `trainOfficerInTraining()` runs daily if functional and the market faction is not hostile to the player. After `getCurrentOfficerTrainingDays()` it sets the target personality, adds the steps to `$IndEvo_wasTrainedLevel`, puts the officer into storage and sends a `MessageIntel`. Dev mode finishes on the next day.
- Saves from before `officerTrainingTarget` have it null; `getOfficerTrainingTarget()` then takes one step in `currentDirection`.

### Admin training

- Eligible: fewer than 2 admin skills and not governing a player market (`canTrainAdminPerson()`, `:340`).
- `setAdminInTraining()` (`:300`) removes from `CharacterDataAPI` admins or storage and charges `IndEvo_adminTrainingCost` through `chargeTraining()`; nothing is charged if training was not allowed.
- `addRandomAdminSkill()` (`:362`) picks a random admin skill the person lacks, skipping tags `ai_core_only`, `player_only` and id `ocua_Arc_Cognition`, and bumps `$ome_adminTier`.

### Storage and fees

- `storeOfficer`/`returnOfficer`, `storeAdmin`/`returnAdmin` move people between fleet or character data and the lists. Storing an admin who governs a player market sets the player as admin there (`storeAdmin()`, `:403`). Retrieval checks officer or admin capacity (dev mode skips the admin check).
- `reportEconomyTick()` on the last iteration of the month (`economyIterPerMonth - 1`) charges full officer salary, 10% admin salary and, on NPC markets, `IndEvo_monthlyAIStorageCost` per stored person (`:127`), on the "Personnel storage" report line ([BillingHelper](../core/UTILS.md#billinghelper)).

### Hireable spawns and officer quality

- Daily, on non-Galatia markets with no hireable officer (or admin) present, rolls the spawn chance and adds an `OfficerManagerEvent.AvailableOfficer` to the running vanilla officer manager event (`createHireablePerson()`, `:702`). Officers: level 1 to 3, independent, hire fee level x 2000. Admins: tier 0 (90%) or 1 (10%), hire fee from `adminHireTier<n>`.
- `apply()` on non-Galatia markets calls `increaseOfficerQuality()` (`:238`): same-faction markets in the location get `Stats.OFFICER_MAX_LEVEL_MOD` mult 1.5 (local) or 1.3 (others); the local market gets `Stats.OFFICER_PROB_MOD` flat +1.5.

### AI cores

| Core | Effect | Where |
|---|---|---|
| Alpha | officers get a second personality step, usable at once | `getMaxOfficerSteps()` |
| Beta | training days -31 (`betacoreReduction`) | `applyAICoreToIncomeAndUpkeep()`, `:823` |
| Gamma | upkeep x0.9 | same |

## Contracts

- All Academy state is plain fields on the industry instance and persists with the market. Stored `PersonAPI` objects exist only in those lists; removing the industry loses them (inferred: `notifyBeingRemoved` is not overridden).
- The dialogue memory keys use expire 0 in local memory and live only for the open dialogue.
- `Stats.OFFICER_PROB_MOD` on a market is read by vanilla `OfficerManagerEvent` (`api/impl/campaign/events/OfficerManagerEvent.java:118`). `Stats.OFFICER_MAX_LEVEL_MOD` is read from person stats by `FleetFactoryV3` (`api/impl/campaign/fleets/FleetFactoryV3.java:1050`); no read of the market level mod was found in `api/impl` or `obf`, so the max level mult likely has no effect (inferred).
- `Academy.COLOURS_BY_PERSONALITY` order is the personality ladder; [Pets](PETS.md) also use its key set for pet dispositions. Keys are personality ids: look up with `PersonalityAPI.getId()`, never the display name, which translations change (`data/characters/personalities.csv` `name` column).
- `ListenerManager.addListener` dedupes by instance and drops transient listeners on save (`obf/campaign/ListenerManager.java:29`, `obf/_common/util/container/repo/ObjectRepository.java:87`), so re-adding in every `apply()` is safe.

## Known issues and dormant code

| Item | State |
|---|---|
| `rules/WIP/IndEvo_InitPersonalityTransferMenu.java` | Excluded by `IndEvo.iml:13`. Wrong package `indevo.industries.acardemy.rules.WIP`, uses removed classes (`IndEvo_Academy`, `IndEvo_ids`, Salvage Yards production code). Officer engram transfer concept only |
| `IndEvo_OfficerCurrentlyTraining` | Dead: no rule uses it, reads `$IndEvo_isTraining` which nothing sets |
| `increaseOfficerQuality()` | `!=` on string ids (`:243`) and `return` instead of `continue` (`:246`) stop the loop at the first already modified market |
| Changelog_todo | "Academy can pick forbidden, event only admin skills and should really run off a whitelist"; "Rework academy UI"; "Story point option for academy, Recruit Local Talent" |

## Maintenance

Update this doc when the Academy menu rules, training rules, fees or settings change. Settings mechanism lives in [Settings](../core/SETTINGS.md); `TimeTracker`, `NewGameIndustryPlacer`, `ModUpdater` and option provider bases live in [Utils](../core/UTILS.md); Galatia research lives in [Research](../dialogue/RESEARCH.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
