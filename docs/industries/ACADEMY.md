# Academy

The Academy industry (`IndEvo_Academy`): officer personality training, admin skill training, officer and admin storage, hireable person spawns, and the hidden Galatia Academy market. Read it before changing any of these or the Academy dialogue. It does not cover the Galatia research projects on the same station; see [Research](../dialogue/RESEARCH.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

The Academy stores officers and admins taken out of the player fleet, trains one officer and one admin at a time, and spawns extra hireable officers and admins on its market. Officer training moves the personality one or two steps along `timid -> cautious -> steady -> aggressive -> reckless`; admin training adds one random admin skill. NPC Academies charge a training fee and a monthly storage fee; player Academies only charge reduced salaries. IE also creates a hidden market `IndEvo_academyMarket` on the vanilla Galatia Academy station so the same panel works there. All interaction runs through one custom panel (`AcademyPanelDelegate`); rules.csv only adds the entry options.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/academy/industry/Academy.java` | Industry plugin. Owns training state (`officerInTraining`, `officerTrainingTarget`, `adminInTraining`, day counters, `currentDirection`), both storage lists, fees, spawn rolls, officer quality mods, AI core effects. `NewDayListener` + `EconomyTickListener`. Nested `TrainingSpeedUp` (`RelicSpeedUpTarget` for the officer or admin timer, `getTrainingSpeedUp(admin)`) |
| `indevo/industries/academy/plugins/AcademyOptionProvider.java` | Industry "Visit the Academy..." button; opens `AcademyDialogPlugin` standalone |
| `plugins/AcademyDialogPlugin.java` | Panel host: `InteractionDialogPlugin`, `EscapeBlockingDialoguePluginAPI`. Standalone (closes the dialog) or rules mode (returns to the rule dialog). Switches between panel, training popup, admin training confirmation (`showAdminTraining`) and the relic speed up dialog (`showRelicDialog`), handles ESC |
| `plugins/AcademyPanelDelegate.java` | The panel: tabs, `HelpIcon` with fees and rules, training slot, person cards, buttons with tooltips. Sizes as constants |
| `plugins/AcademyTrainingPopupDelegate.java` | Officer training popup: five personality tiles in card style, summary, confirm |
| `plugins/AcademyAdminTrainingDialog.java` | Admin training confirmation: interaction dialog with effect, time, cost and `Train` / `Cancel` options |
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
| Item | `IndEvo_holobench` (`ItemIds.HOLOBENCH`, Holobench), params `IndEvo_Academy` | `data/campaign/special_items.csv`, effect text `HolobenchEffect`; [Installables](../items/INSTALLABLES.md) |
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

`AcademyPanelDelegate`, shown with `InteractionDialogAPI.showCustomDialog()` at `getWidth()` x `getHeight()`: `min(1104, screen width - 100)` and `min(850, screen height - 100)` for the whole dialog, minus the 20 x 55 px the custom dialog adds for its frame and button row (translation of obfuscated code, `obf/ui/newui/super.java:52-53`).

- Title heading with a `HelpIcon` in the top right corner: training cost and time, step limit and storage fees per tab and market owner.
- Tabs `Officers n / max` and `Administrators n / max`, credits on the right.
- In training: the person of the active tab with target, days left and a bar, a paused note when the Academy is not functional or the market is hostile, `Abort to fleet` (disabled without a free slot) and `Abort to storage`. No refund. `Accelerate` left of them: dismisses the panel and opens the shared `RelicSpeedUpDialog` for the timer of the active tab; Confirm, Cancel and ESC reopen the panel on the next frame. Disabled while its 60 day cooldown runs or with 1 day or less left; the tooltip shows effect, cost and lore. Halves the days left for 1 Relic Component per remaining day, paid from the Academy market's storage, then fleet cargo. See [Economy](../core/ECONOMY.md#relic-speed-up).
- Cards, 4 per row (`COLUMNS`, 260 x 178 px at the 1084 px panel width), under the headings `In fleet` or `On staff` (`getFleetOfficers()`, unremovable ones left out, or `getStaffAdmins()`) and `Stored here`, each group by level or admin skill count. Each row of cards is one custom panel in the scrolling list. Card header bar: the name, dark player colour, grey when stored. Square assignment box (portrait size) left of the portrait: commanded ship icon (`getCommandedShip()`, `addShipList`) or governed market (`getGovernedMarket()`, `showPlanetInfo`, faction crest for markets without a planet), its name below in one line of `VICTOR_10`, cut with `...` when too long; else `Unassigned` in the box. Text right of the portrait: officers `Level`, personality in its colour, `Steps <used> of <max>`, salary; admins `Skills n of 2`, salary (10% and `while stored` when stored). Skill icons with name tooltips, elite skills framed. `Train` (green, as the Courier Port confirm) and `Store` or `Retrieve` side by side; their tooltips open above.
- Every button is disabled with the reason from `Academy` as tooltip (`getOfficerTrainBlocker()`, `getAdminTrainBlocker()`, `getOfficerSlotBlocker()`, `getAdminSlotBlocker()`); enabled ones show cost and time. Store on a governing admin warns that the player takes over.
- Officer `Train` dismisses the panel and opens `AcademyTrainingPopupDelegate`: five square tiles in ladder order, each an area checkbox in card style: header bar with the name (grey when out of reach), a strip in the personality colour below it (`Academy.COLOURS_BY_PERSONALITY`, dimmed when out of reach), then days and fee for reachable ones (fee in the negative colour when not affordable), `Current`, `Needs an alpha core` or `Out of reach`. Reachable and affordable tiles are selectable, the nearest is preselected. Below: current to target with days and fee, and the target's vanilla description. Confirm (`Train`) calls `setOfficerForTraining(person, targetId)`.
- Admin `Train` opens `AcademyAdminTrainingDialog` over the panel with `CampaignUIAPI.showInteractionDialogFromCargo` (`showConfirmDialog` returns false while an interaction dialog is up, translation of obfuscated code `CampaignState.showConfirmDialog`): admin portrait, effect, time and cost, options `Train` and `Cancel` (also ESC). `Train` calls `setAdminInTraining(person)`; on dismiss the panel rebuilds (`AcademyPanelDelegate.refresh()`). The Academy plugin stops claiming ESC while it is open.
- Popups: Confirm, Cancel and ESC reopen the panel on the next frame (`AcademyDialogPlugin.showPanelNextFrame()`); the custom dialog slot is only free after the dismiss. ESC and Return close the panel. Scroll position is kept across rebuilds and popups.
- Tooltip elements indent their first item by 5 px (`StandardTooltipV2Expandable.addCustom`, translation of obfuscated code); elements placed by absolute position subtract `TOOLTIP_INSET`.

### Officer training

- Steps: the distance on the personality ladder (`getPersonalitySteps()`). An officer has one lifetime step, two with an alpha core; a Holobench adds one, two with an alpha core, so 2 or 4 (`getMaxOfficerSteps(alpha)`, current state `getMaxOfficerSteps()`); `$IndEvo_wasTrainedLevel` counts the steps used (`getOfficerStepsLeft()`). One training may use all steps left at once.
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
| Alpha | officers get a second personality step, usable at once (four with a Holobench) | `getMaxOfficerSteps()` |
| Beta | training days -31 (`betacoreReduction`) | `applyAICoreToIncomeAndUpkeep()`, `:866` |
| Gamma | upkeep x0.9 | same |

### Holobench

Installable item `IndEvo_holobench`, vanilla slot through `GenericSpecialItemPlugin` params (`BaseIndustry.wantsToUseSpecialItem`). The effect class only adds the description; the Academy reads `special` itself. Effects: one more lifetime personality step, two with an alpha core (Officer training), and mentoring on officer training (above).

Reserve drills: `reportEconomyMonthEnd()` gives every officer in this Academy's storage `HOLOBENCH_MONTHLY_XP` (20000, `SpecialItemEffectsRepo`) while the Academy is functional, has the Holobench and its market is not hostile. The XP goes through a throwaway `OfficerDataAPI` (`FactoryAPI.createOfficerData`) so vanilla clamps at max level and skips mercenaries (translation of obfuscated code, `obf/rpg/OfficerData.java:56-64`). No message, the officer is not in the fleet. Skill picks are made by the vanilla level up screen when picks are empty (translation of obfuscated code, `obf/coreui/B.java:70`). The officer in training gets none. At `officerXPRequiredMult` 4, level 2 takes about 3 months, level 5 about 23 months. The officers tab help lists it while installed.

## Contracts

- Storage help lists the monthly fee per person and the salary charged while stored. Abort tooltips state the loss of the training fee and progress.

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
