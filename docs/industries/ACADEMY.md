# Academy

The Academy industry (`IndEvo_Academy`): officer personality training, admin skill training, officer and admin storage, hireable person spawns, and the hidden Galatia Academy market. Read it before changing any of these or the Academy dialogue. It does not cover the Galatia research projects on the same station; see [Research](../dialogue/RESEARCH.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The Academy stores officers and admins taken out of the player fleet, trains one officer and one admin at a time, and spawns extra hireable officers and admins on its market. Officer training moves the personality one or two steps along `timid -> cautious -> steady -> aggressive -> reckless`; admin training adds one random admin skill. NPC Academies charge a training fee and a monthly storage fee; player Academies only charge reduced salaries. IE also creates a hidden market `IndEvo_academyMarket` on the vanilla Galatia Academy station so the same panel works there. All interaction runs through one custom panel (`AcademyPanelDelegate`, built with the [UI kit](../core/UI.md)); rules.csv only adds the entry options.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/academy/industry/Academy.java` | Industry plugin. Owns training state (`officerInTraining`, `officerTrainingTarget`, `adminInTraining`, day counters, `currentDirection`), both storage lists, fees, spawn rolls, officer quality mods, AI core effects. `NewDayListener` + `EconomyTickListener`. Nested `TrainingSpeedUp` (`RelicSpeedUpTarget` for the officer or admin timer, `getTrainingSpeedUp(admin)`) |
| `indevo/industries/academy/plugins/AcademyOptionProvider.java` | Industry "Visit the Academy..." button; opens `AcademyDialogPlugin` standalone |
| `plugins/AcademyDialogPlugin.java` | Panel host: `InteractionDialogPlugin`, `EscapeBlockingDialoguePluginAPI`. Standalone (closes the dialog) or rules mode (returns to the rule dialog), handles ESC. Static `show(market, view)` opens the panel for code and tests |
| `plugins/AcademyView.java` | View state: tab (`admins`), selected person id, selected personality rung, scroll offsets (`LIST`, `DETAIL`) |
| `plugins/AcademyPanelDelegate.java` | The panel: header, tabs, list column (training strip, groups, rows), selection, help tooltip. Reference implementation of the UI kit |
| `plugins/AcademyDetail.java` | Detail column per selection: officer with personality ladder, administrator, trainee with Accelerate and Abort |
| `indevo/industries/academy/rules/IndEvo_ShowAcademyPanel.java` | Rule command `IndEvo_ShowAcademyPanel <returnOption>`: finds the Academy market (Galatia station maps to `Ids.ACADEMY_MARKET_ID`), opens `AcademyDialogPlugin` in rules mode |
| `rules/WIP/IndEvo_InitPersonalityTransferMenu.java` | Excluded WIP (see below) |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_Academy` (`Ids.ACADEMY`) | `data/campaign/industries.csv:35`, tags `structure, unraidable` |
| Market | `IndEvo_academyMarket` (`Ids.ACADEMY_MARKET_ID`) on entity `station_galatia_academy` | `ModPlugin.createAcademyMarket()` (`indevo/utils/ModPlugin.java:382`) |
| Setting | `IndEvo_Academy` (toggle) | `data/config/LunaSettings.csv:102`, read in `Academy.isAvailableToBuild()` (`Academy.java:147`) |
| Setting | `IndEvo_dailyOfficerSpawnChance` 0.15, `IndEvo_dailyAdminSpawnChance` 0.10 | `LunaSettings.csv:166-167`, `Academy.onNewDay()` |
| Setting | `IndEvo_personalityTrainingDayCount` 93, `IndEvo_personalityTrainingCost` 30000 | `LunaSettings.csv:168-169` |
| Setting | `IndEvo_adminTrainingDayCount` 124, `IndEvo_adminTrainingCost` 20000 | `LunaSettings.csv:170-171` |
| Setting | `IndEvo_monthlyAIStorageCost` 2000 (per person, NPC markets) | `LunaSettings.csv:172` |
| Whitelist | `academy_admin_skill_whitelist.csv`, key `IndEvo_academySkillList` (`Ids.ACADEMY_SKILL_LIST`) | `data/config/indEvo/`, [Settings](../core/SETTINGS.md#whitelists) |
| Market memory | `$IndEvo_officerIsTraining`, `$IndEvo_adminIsTraining` | set by `Academy`, no reader left |
| Person memory | `$IndEvo_wasTrainedLevel` (personality steps used) | `Academy.addTrainedSteps()` |
| Person memory | `$ome_adminTier` incremented, `$ome_hireable`, `$ome_isAdmin` read | `Academy.java:457`, `:734` (vanilla `OfficerManagerEvent` keys) |
| Rules | `IndEvo_AcademyBaseMenu`, `IndEvo_AcademyBaseMenu_GA`, `IndEvo_AcademyBaseColonyOptionMenu`, `IndEvo_IntitiateAcademyMenu`, `IndEvo_IntitiateAcademyMenu_GA` | `data/campaign/rules.csv`, block `# Academy` to `#END Academy` |
| Item | `IndEvo_holobench` (`ItemIds.HOLOBENCH`, Holobench, shown by its item name Neural Interface), params `IndEvo_Academy` | `data/campaign/special_items.csv`, effect text `HolobenchEffect`; [Installables](../items/INSTALLABLES.md) |
| Person memory | `$mentored` (vanilla `Misc.MENTORED`) set by the Holobench | `Academy.trainOfficerInTraining()` |
| Sprite | `IndEvo/trainingdone` | `data/config/settings.json`, completion message icon |

## Registration and lifecycle

- Industry plugin from the `industries.csv` plugin column. Rule commands from `ruleCommandPackages` entry `indevo.industries.academy.rules` (`data/config/settings.json:32`).
- `ModPlugin.onGameLoad()` and `onNewGameAfterEconomyLoad()` call `createAcademyMarket()` (`ModPlugin.java:154`, `:296`): hidden size 3 independent market with Population and Academy, `NO_CONTACTS_ON_MARKET` and `NO_DECIV_KEY` set, only if the market is missing and `station_galatia_academy` exists.
- `AcademyOptionProvider.register()` from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:470`), transient, deduped by class.
- `Academy.apply()` adds itself as a transient listener (`Academy.java:87`); `unapply()` removes it. Daily ticks come from IE `TimeTracker`, which collects `NewDayListener` listeners ([Utils](../core/UTILS.md)).
- `NewGameIndustryPlacer.placeAcademy` seeds NPC Academies; `ModUpdater` restores Academy state on spec swap. Both in [Utils](../core/UTILS.md).

Load chain: `onGameLoad -> createAcademyMarket -> setListenersIfNeeded -> market reapply -> Academy.apply -> addListener(this, true) -> TimeTracker -> onNewDay`.

## Flow

### Entry points

| Entry | Opens |
|---|---|
| Industry button "Visit the Academy..." (`AcademyOptionProvider`) | `AcademyDialogPlugin` standalone; Return and ESC close the dialog |
| Market menu option (rule `IndEvo_AcademyBaseMenu`, needs `IndEvo_marketHasIndustry IndEvo_Academy`, rep at worst inhospitable) and IE colony option point (`IndEvo_AcademyBaseColonyOptionMenu`) | `IndEvo_IntitiateAcademyMenu` runs `IndEvo_ShowAcademyPanel IndEvo_SelectreturnToMenu`; Return goes back to the market menu |
| Galatia station option "Visit the Academy proper" (rule `IndEvo_AcademyBaseMenu_GA`, `$id == station_galatia_academy`) | `IndEvo_IntitiateAcademyMenu_GA` runs `IndEvo_ShowAcademyPanel gaMeetingEnd`; Return ends the vanilla Galatia meeting |

Rules mode follows the Courier Port pattern: the command swaps in `AcademyDialogPlugin`, which on return restores the original plugin, shows the text and visual panels, runs `ShowDefaultVisual`, sets `$option` to the return option and fires `DialogOptionSelected` one frame after the panel is gone.

### Panel

`AcademyPanelDelegate`, a custom dialog of board size 1104 x 780 (`UiFrame.width/height`: `min(board, screen - 100)`, content 1084 x 725 at 1600 x 900, 1084 x 613 at 1280 x 768). Both tabs use the same size, so a tab switch rebuilds in place. Every click changes `AcademyView` and rebuilds the body (`UiRoot.refresh()`); list and detail keep their scroll offsets. Layout is absolute ([UI](../core/UI.md#layout-contract)): detail column 295 wide at the right edge, the list takes the rest.

- Header: title `<Academy name>  <market>` with the `?` help (training cost and time, step limits, Holobench, storage fees, abort loss; per tab and market owner). Tabs `Officers n / max` and `Administrators n / max` (210 x 24, top cut). Right side: credits; on the Officers tab also the installed Holobench and Alpha Core with tooltips (left out when they would reach the tabs).
- List column, one scroll list (`AcademyView.LIST`): `In training` with one strip row or one grey line, `In fleet` / `On staff` (`getFleetOfficers()`, unremovable ones left out, or `getStaffAdmins()`), `Stored here` (NPC market: storage fee note in the heading). Groups sorted by level or admin skill count; an empty group keeps its heading and one grey line. Rows are 50 high and select on click.
  - Officer row: portrait, name, level, personality in its colour with 5 pips, skill icons (up to 6 at 28 px, else two rows of 20 px icons and `+N` with the rest as tooltip; elite framed), then the commanded ship (sprite, name, class), `Unassigned`, or for stored officers `In storage / draws full salary`, with the Holobench `Reserve drills / +20,000 XP per month`, `Max level / No reserve drills` or `Mercenary / No reserve drills`; salary per month right.
  - Administrator row: portrait, name, `n of 2 skills`, two skill slots (dashed when empty), governed colony (planet, name, `Size n · <system>`), `Unassigned` or `In storage / draws 10% of salary`; salary per month right (10% while stored).
  - Person names fit 24 characters (162 px column in rows and the training strip); longer ones end in an ellipsis with the full name as tooltip.
  - A ship or colony name too long for its line wraps to the second line and drops the class or size line (`Ui.nameLines`); the size line falls back to the system name when both do not fit.
  - Training strip: portrait, name, level, from and to personality (admins: learns one random skill), progress bar `Day d of t` (standard bar colour, the vanilla warning colour while paused), `Done on <date>` or `Paused: <reason>` in red, days left and steps.
- Selection: `AcademyView.selected` (person id). None or unknown picks the trainee, else the first row. Selecting resets the detail scroll.
- Detail column (`AcademyDetail`): the information scrolls (`AcademyView.DETAIL`), the action block (fee, Train, Store or Retrieve; Accelerate and Abort for the trainee) is pinned at the bottom and never scrolls.
  - Officer: portrait, grid Level, Personality, Salary, Steps used, then ship, `Unassigned` or `Stored here`; `Personality training` ladder of 5 rungs in ladder order, each with a colour strip: current (highlight frame, `Current`), reachable (days and fee, fee red when short; click selects), `Needs an alpha core` or `Out of reach` (disabled). The nearest affordable rung is preselected. Every rung has its vanilla personality description as tooltip. Below: the skills list (Elite marked) last, the part that scrolls. Pinned at the bottom: Duration, `Leaves the fleet until` or `Done on` date and Fee (with a selected rung), `Train` (green) and `Store` or `Retrieve`. Train calls `setOfficerForTraining(person, rung)` at once.
  - Administrator: portrait, grid Skills, Salary, Governs (planet and size), `Stored here / draws 10% of salary` when stored; `Administrator training` with Gains, then skills when any; pinned at the bottom: Duration, `Away until` (only when trainable), Fee, `Train` (`setAdminInTraining`), `Store` or `Retrieve`; a governing administrator gets the highlight line `Storing them makes you governor of X.`
  - Trainee: info grid, `In training` with from and to and the bar (paused reason below), Days left, Done on, Fee paid, `Mentored on completion` (officers); `Accelerate`: cost in Relic Components with stock, Days left after, one click button (`RelicSpeedUpDialog.apply`), tooltip from `RelicSpeedUpDialog.addTooltip` with lore; `Abort`: `Abort to fleet` and `Abort to storage` (red pair), no refund (stated in their tooltips).
  - Fee: `Free own Academy` on player markets, else credits with the player's stock.
- Blocked buttons are grey with the reason in red below (reserved line): `getOfficerTrainBlocker()`, `getAdminTrainBlocker()`, `Not enough credits.` for the picked rung, `getOfficerSlotBlocker()` / `getAdminSlotBlocker()` for Retrieve and Abort to fleet; Accelerate `Unavailable for another N days.`, `Too few days left.`, `Not enough Relic Components.`. Store tooltips state the storage cost and the governor takeover.
- Personality colours are `Academy.COLOURS_BY_PERSONALITY` blended 40% towards white (`getPersonalityColor()`); the raw blue and red are too dark on the panel background.
- ESC and Return close the panel (standalone) or return to the rule dialog.
- Test entry: `AcademyDialogPlugin.show(market, new AcademyView(admins, personId, rungId))`.

### Officer training

- Steps: the distance on the personality ladder (`getPersonalitySteps()`). An officer has one lifetime step, two with an alpha core; a Holobench adds one, so up to 3 (`getMaxOfficerSteps(alpha)`, current state `getMaxOfficerSteps()`); `$IndEvo_wasTrainedLevel` counts the steps used (`getOfficerStepsLeft()`). One training may use all steps left at once.
- `setOfficerForTraining(person, targetId)` checks that no officer is training, the officer is removable, the target is 1 to steps left away and the fee is affordable, then removes the officer from fleet and ship command or from storage, stores the target in `officerTrainingTarget` and charges the fee. Fee and days are per step: `getOfficerTrainingFee(steps)` (`IndEvo_personalityTrainingCost` per step, 0 on player markets), `getOfficerTrainingDays(steps)` (`IndEvo_personalityTrainingDayCount` + 1 - AI core reduction, per step). The fee is taken at once with `BillingHelper.chargeNow` ([BillingHelper](../core/UTILS.md#billinghelper)).
- `trainOfficerInTraining()` runs daily if functional and the market faction is not hostile to the player. After `getCurrentOfficerTrainingDays()` it sets the target personality, adds the steps to `$IndEvo_wasTrainedLevel`, puts the officer into storage and sends a `MessageIntel`. Dev mode finishes on the next day.
- Holobench installed at completion (`hasHolobench()`): the officer leaves mentored (`Misc.setMentored`, vanilla `$mentored`), the message says so. Vanilla reads the flag for skill picks per level, 6 instead of 4 (`api/impl/campaign/OfficerLevelupPluginImpl.java:137`, `:200`); vanilla only sets it from the officer screen Mentor button for a story point (translation of obfuscated code, `obf/coreui/ooo0.java:983`). Already mentored officers are left alone.
- Saves from before `officerTrainingTarget` have it null; `getOfficerTrainingTarget()` then takes one step in `currentDirection`.

### Admin training

- Eligible: fewer than 2 admin skills and not governing a player market (`canTrainAdminPerson()`, `:356`).
- `setAdminInTraining()` (`:312`) removes from `CharacterDataAPI` admins or storage and charges `IndEvo_adminTrainingCost` through `chargeTraining()`; nothing is charged if training was not allowed.
- `addRandomAdminSkill()` picks a random admin skill the person lacks from `academy_admin_skill_whitelist.csv` (`Ids.ACADEMY_SKILL_LIST`, vanilla `industrial_planning` and the four IE admin skills; other mods add rows), skips skills tagged `deprecated`, `player_only` or `ai_core_only` like vanilla `OfficerManagerEvent.createAdmin()` (`api/impl/campaign/events/OfficerManagerEvent.java:331-333`), and bumps `$ome_adminTier`.

### Storage and fees

- `storeOfficer`/`returnOfficer`, `storeAdmin`/`returnAdmin` move people between fleet or character data and the lists. Storing an admin who governs a player market sets the player as admin there (`storeAdmin()`, `:471`). Retrieval checks officer or admin capacity (dev mode skips the admin check); blocked Retrieve buttons explain the capacity limit.
- `reportEconomyTick()` on the last iteration of the month (`economyIterPerMonth - 1`) charges full officer salary, 10% admin salary and, on NPC markets, `IndEvo_monthlyAIStorageCost` per stored person (`:132`), on the "Personnel storage" report line ([BillingHelper](../core/UTILS.md#billinghelper)).

### Hireable spawns and officer quality

- Daily, on non-Galatia markets with no hireable officer (or admin) present, rolls the spawn chance and adds an `OfficerManagerEvent.AvailableOfficer` to the running vanilla officer manager event (`createHireablePerson()`, `:745`). Officers: level 1 to 3, independent, hire fee level x 2000. Admins: tier 0 (90%) or 1 (10%), hire fee from `adminHireTier<n>`.
- `apply()` on non-Galatia markets calls `increaseOfficerQuality()` (`:250`): same-faction markets in the location get `Stats.OFFICER_MAX_LEVEL_MOD` mult 1.5 (local) or 1.3 (others); the local market gets `Stats.OFFICER_PROB_MOD` flat +1.5.

### AI cores

| Core | Effect | Where |
|---|---|---|
| Alpha | officers get a second personality step, usable at once (three with a Holobench) | `getMaxOfficerSteps()` |
| Beta | training days -31 (`betacoreReduction`) | `applyAICoreToIncomeAndUpkeep()`, `:866` |
| Gamma | upkeep x0.9 | same |

### Holobench

Installable item `IndEvo_holobench`, vanilla slot through `GenericSpecialItemPlugin` params (`BaseIndustry.wantsToUseSpecialItem`). The effect class only adds the description; the Academy reads `special` itself. Effects: one more lifetime personality step (Officer training), and mentoring on officer training (above).

Reserve drills: `reportEconomyMonthEnd()` gives every officer in this Academy's storage `HOLOBENCH_MONTHLY_XP` (20000, `SpecialItemEffectsRepo`) while the Academy is functional, has the Holobench and its market is not hostile. The XP goes through a throwaway `OfficerDataAPI` (`FactoryAPI.createOfficerData`) so vanilla clamps at max level and skips mercenaries (translation of obfuscated code, `obf/rpg/OfficerData.java:56-64`). No message, the officer is not in the fleet. Skill picks are made by the vanilla level up screen when picks are empty (translation of obfuscated code, `obf/coreui/B.java:70`). The officer in training gets none. At `officerXPRequiredMult` 4, level 2 takes about 3 months, level 5 about 23 months. The officers tab help lists it while installed.

## Contracts

- Storage help lists the monthly fee per person and the salary charged while stored. The help and the Abort section state the loss of the training fee and progress.

- All Academy state is plain fields on the industry instance and persists with the market. Stored `PersonAPI` objects exist only in those lists; removing the industry loses them (inferred: `notifyBeingRemoved` is not overridden).
- `Stats.OFFICER_PROB_MOD` on a market is read by vanilla `OfficerManagerEvent` (`api/impl/campaign/events/OfficerManagerEvent.java:118`). `Stats.OFFICER_MAX_LEVEL_MOD` is read from person stats by `FleetFactoryV3` (`api/impl/campaign/fleets/FleetFactoryV3.java:1050`); no read of the market level mod was found in `api/impl` or `obf`, so the max level mult likely has no effect (inferred).
- `Academy.COLOURS_BY_PERSONALITY` order is the personality ladder; [Pets](PETS.md) also use its key set for pet dispositions. Keys are personality ids: look up with `PersonalityAPI.getId()`, never the display name, which translations change (`data/characters/personalities.csv` `name` column).
- `ListenerManager.addListener` dedupes by instance and drops transient listeners on save (`obf/campaign/ListenerManager.java:29`, `obf/_common/util/container/repo/ObjectRepository.java:87`), so re-adding in every `apply()` is safe.

## Known issues and dormant code

| Item | State |
|---|---|
| `rules/WIP/IndEvo_InitPersonalityTransferMenu.java` | Excluded by `IndEvo.iml:13`. Wrong package `indevo.industries.acardemy.rules.WIP`, uses removed classes (`IndEvo_Academy`, `IndEvo_ids`, Salvage Yards production code). Officer engram transfer concept only |
| `increaseOfficerQuality()` | `!=` on string ids (`:255`) and `return` instead of `continue` (`:258`) stop the loop at the first already modified market |
| Changelog_todo | "Story point option for academy, Recruit Local Talent" (Philipp 2026-11-07: never) |

## Maintenance

Update this doc when the Academy panel, entry rules, training rules, fees or settings change. Settings mechanism lives in [Settings](../core/SETTINGS.md); `TimeTracker`, `NewGameIndustryPlacer`, `ModUpdater` and option provider bases live in [Utils](../core/UTILS.md); Galatia research lives in [Research](../dialogue/RESEARCH.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
