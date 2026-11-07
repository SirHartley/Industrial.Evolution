# Senate

The Senate industry (`IndEvo_senate`) and Edicts: market conditions the player enacts on own colonies, their runtime and removal penalties, and the Edict panel. Read it before adding or changing an Edict or the Senate menu. It does not cover the Neuroconditioning Compounds item effect itself; see [Installables](../items/INSTALLABLES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

A Senate allows one Edict per player colony in its star system (one Senate per system per faction). With Neuroconditioning Compounds (`IndEvo_neurals`) installed, a Senate also allows Edicts on player colonies within 12 LY and halves early removal unrest. An Edict is a market condition (`IndEvo_edict_*`, tag `IndEvo_edict`) with a minimum runtime (default 186 days); removing it earlier adds unrest equal to the remaining months. Some Edicts end themselves and apply a finish penalty. Edicts are enacted and removed in the Edict panel, a custom dialog opened from the Senate industry button or the market menu.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/senate/industry/Senate.java` | Industry plugin: build limit, AI core effects, `EDICT_RUNTIME_DAY_RED` 31, `getNearestSenateWithItem()`. Nested `SenateFactor` (`ColonyOtherFactorsListener`, colony info text) |
| `EdictRepo.java` | Edict specs: every market condition tagged `IndEvo_edict`, sorted by `order` then name, cached in `SessionTransientMemory` key `$IndEvo_EdictSpecs`. `isEdict(id)`, `getShortName()` (drops the `Edict: ` prefix) |
| `EdictHelper.java` | Senate range (`getSystemSenate`, `getCompoundsSenateInRange`, `isInSenateRange`, `getSenateRequirement`), beta core check, active Edict lookup, `getColoniesCoveredBy(senateMarket)`, `enact()` |
| `EdictRequirement.java` | One requirement line: `text`, `shortText`, `met` |
| `conditions/EdictAPI.java` | Interface used by the panel: requirements, presence check, effect and runtime text into a `TooltipMakerAPI` for any colony, runtime and unrest values, removal |
| `conditions/BaseEdict.java` | Base condition: runtime tracking via market time tags, daily presence check, `removeWithPenalty`/`removeWithoutPenalty`, Senate requirement, shared requirement helpers, condition tooltip. `NewDayListener` |
| `conditions/Edict_*.java` (12) | One class per Edict, see table below |
| `dialogue/EdictPanelDelegate.java` | The Edict panel (`ButtonReportingDialogueDelegate`), see [Edict panel](#edict-panel) |
| `patches/FastBuildPickerRowPatch.java`, `FastBuildQueuedIndustryPatch.java`, `FastBuildTooltipPatch.java`, `FastBuildUpgradeConfirmPatch.java` | PatchLib patches that show Mandated Overtime's halved days in the build menu, on queued industry cards, in industry tooltips and in the upgrade confirm text, see [Build time display](#build-time-display) |
| `plugins/SenateOptionProvider.java` | Industry "Visit the Senate..." button on functional player Senates, docked or remote; opens the panel with `DialogCreatorUI.showDialog(w, h, delegate)` |
| `rules/IndEvo_OpenEdictPanel.java` | Rule command: opens the panel for the docked colony with `dialog.showCustomDialog(w, h, delegate)` |
| `rules/IndEvo_systemHasSenate.java` | Rule condition: player owned and `EdictHelper.isInSenateRange()` |

### Edicts

| Condition id | Class | Requires (besides Senate presence) | Effect | Runtime / end |
|---|---|---|---|---|
| `IndEvo_edict_forced_reloc` | `Edict_ForcedRelocation` | military, unique in system, 2+ colonies, size 4 to max-1 | stability -3 in system, growth -10 x size here, + ceil(size / others x 10) elsewhere | auto-ends at min runtime or max size, +3 unrest |
| `IndEvo_edict_ind_incent` | `Edict_Incentives` | none | industries supply and demand +1, upkeep x2 | 186 days |
| `IndEvo_edict_labour_rest` | `Edict_LabourRestrictions` | not free port | stability +4, industries supply and demand -1 | 186 days |
| `IndEvo_edict_cleanse` | `Edict_Cleanse` | military, a condition named "...ubpopulation" | income x0.7, stability -(size-1), growth -size | size x 31 days, then removes subpopulation conditions, unrest ceil(size/3) |
| `IndEvo_edict_lockdown` | `Edict_Lockdown` | military | stability +5, fleet spawn rate x2, fleet size +1 flat, ground defense x3, all supply and non-military demand x0.3 | 93 days, auto-ends, +1 unrest; early end +3 extra |
| `IndEvo_edict_customs` | `Edict_Customs` | Megaport, unique in system, 2+ colonies | accessibility +5% per other colony, -7% on the others | 186 days |
| `IndEvo_edict_conscription` | `Edict_Conscription` | military | upkeep x0.4, growth -2 x size, stability -4 dropping 1 per month (tag `edict_stab_penalty<n>`) | 186 days |
| `IndEvo_edict_deadlines` | `Edict_Deadlines` | ship hull supply | ship supply +30% per industry, production quality -0.2 on all player markets | 186 days |
| `IndEvo_edict_clearances` | `Edict_Clearances` | ship hull supply | ship supply -30% (if > 1), production quality +0.2 on all player markets | 186 days |
| `IndEvo_edict_lockColonySize` | `Edict_LockColonySize` | none | population weight pinned to the current size weight | 186 days |
| `IndEvo_edict_open_market` | `Edict_OpenMarket` | none | adds an open market submarket (if no Commerce), stability -2; on removal takes the open market away only if there is no Commerce | 186 days |
| `IndEvo_edict_fastBuild` | `Edict_FastBuild` | none | building and upgrade time halved via reflection on `BaseIndustry.buildTime`, supply and demand x0.5; build menu and queued cards show the halved days | 186 days |

Rows: `data/campaign/market_conditions.csv:3-14`, tags `show_in_codex, IndEvo_edict`. Military presence is `MiscIE.marketHasMilitaryIncludeRelays()`. Every Edict also requires a Senate in range (`EdictHelper.getSenateRequirement()`).

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_senate` (`Ids.SENATE`) | `data/campaign/industries.csv:31` |
| Conditions | `IndEvo_edict_*` (12), tag `IndEvo_edict` (`EdictRepo.EDICT_TAG`) | `data/campaign/market_conditions.csv` |
| Item | `IndEvo_neurals` (Neuroconditioning Compounds) | `data/campaign/special_items.csv:18`, effect in `SpecialItemEffectsRepo` |
| Constants | `RANGE_LY_TWELVE` 12, `NEURAL_COMPOUNDS_UNREST_RED` 0.5 | `indevo/items/installable/SpecialItemEffectsRepo.java:44-45` |
| Setting | `IndEvo_senate` (toggle) | `data/config/LunaSettings.csv:100`, `Senate.isAvailableToBuild()` |
| Market tags | `<conditionModId><days>` runtime counter; `edict_stab_penalty<n>` | `TimeTracker`, `Edict_Conscription` |
| Market memory | `$IndEvo_WeightMemory` (saved population weight) | `Edict_LockColonySize.java:13` |
| Session cache | `$IndEvo_EdictSpecs` (sorted Edict specs) | `EdictRepo` |
| Rules | `IndEvo_SenateMenuPoint(2)`, `IndEvo_EdictMenuPoint(2)`, `IndEvo_openEdictPanel` | `data/campaign/rules.csv` block `# Senate` |

## Registration and lifecycle

- Industry and condition plugins from CSV plugin columns; rule commands from `ruleCommandPackages` `indevo.industries.senate.rules` (`data/config/settings.json:33`).
- `SenateOptionProvider.register()` from `ModPlugin.setListenersIfNeeded()` (`ModPlugin.java:468`). `Senate.SenateFactor` is registered by `SpecialItemEffectsRepo.initEffectListeners()` (`SpecialItemEffectsRepo.java:30`), called at `ModPlugin.java:426`.
- `BaseEdict.apply()` adds the condition as a transient listener (deduped) and a `TimeTracker` market tag tracker; skips both on markets without a primary entity and in the codex (`BaseEdict.java:38`). `TimeTracker` increments the tag daily and calls `onNewDay()`.

Chain: `EdictPanelDelegate.enactSelected -> EdictHelper.enact -> market.addCondition -> BaseEdict.apply -> TimeTracker tag -> onNewDay (presence check, auto end) -> removeWithPenalty/removeWithoutPenalty -> market.removeSpecificCondition`.

## Flow

### Entry points

| Entry | Shown when | Label | Panel scope |
|---|---|---|---|
| Senate industry button (`SenateOptionProvider`) | functional Senate on a player colony, docked or from a remote colony screen | "Visit the Senate..." | colonies the Senate covers |
| Market menu (`IndEvo_SenateMenuPoint`) | docked, player owned, `IndEvo_systemHasSenate`, `IndEvo_marketHasIndustry IndEvo_senate` | "Visit the Senate" | colonies the Senate covers |
| Market menu (`IndEvo_EdictMenuPoint`) | docked, player owned, `IndEvo_systemHasSenate`, no functional Senate on the colony | "Enact an Edict" | the docked colony only |

With `IndEvo_requiresColonyMenuPoint` the two market options move under "Colony options" (`IndEvo_SenateMenuPoint2`, `IndEvo_EdictMenuPoint2`). All three open the same `EdictPanelDelegate`; the docked ones through `IndEvo_openEdictPanel -> IndEvo_OpenEdictPanel`. Vanilla lists industry options in remote colony screens; the base `SingleIndustrySimpifiedOptionProvider` docked check is the only thing that blocked it, so `SenateOptionProvider` overrides `isSuitable`.

`EdictHelper.getColoniesCoveredBy(senateMarket)`: player colonies in the Senate's star system with the Senate's faction, plus, with Compounds installed, player colonies within 12 LY of the Senate. Sorted by star system, Senate colony first, then size and name.

### Edict panel

`EdictPanelDelegate` (layout B from the approved mockup, `EDICT_UI_PLAN.md`):

- Size: panel `min(984, screen width - 40)` x `min(795, screen height - 300)` (`EdictPanelDelegate.java:37-38`, `getPanelWidth`/`getPanelHeight`). The custom dialog adds 20 x 55 for its frame and Return button, so the dialog never exceeds the vanilla cargo tab (1004 x 850, the smallest capped core UI panel).
- Header: the Senate and its range, or for a colony without a Senate the Senate that grants it Edicts.
- Columns: colonies (name, size, location, active Edict with day count and a runtime bar), Edicts for the selected colony (status: active, needs, one Edict per colony, available), detail (description from the CSV, effects on the selected colony, requirement checklist with marks, runtime and removal unrest).
- Actions: Enact Edict (disabled while requirements are unmet or another Edict is active) and Remove Edict, each with an inline confirm. A message line shows the last action. Return (confirm) and Esc (cancel) close the panel.
- Rebuild: every click rebuilds the panel (`recreatePanel`) and keeps the three scroll offsets; the detail offset resets when the selection changes.
- Preview: one dummy market per panel (`Global.getFactory().createMarket`) holds every Edict condition. `BaseEdict.apply` skips markets without a primary entity, so the dummy applies nothing; the panel calls the dummy plugins with the selected colony as parameter (`getRequirements(market)`, `addEffectText(tooltip, market, pad)`, `addRuntimeText`, `getMinRuntime(market)`). An active Edict uses its real plugin.
- Graphics: `RuntimeBarPlugin` (two GL quads) and `RequirementMarkPlugin` (check or cross in GL lines).

### Condition tooltip

`BaseEdict.createTooltipAfterDescription` shows the remaining time (`getRemainingTimeFormat()`, overridden by the Edicts that end themselves), `addEffectText` for the colony and the requirement list. In the codex it shows `getShortDesc()` only.

### Runtime and penalties

- `daysPassed` = time tag value (`BaseEdict.java:46`, `:61`). `getMinRuntime(market)` = 186, -31 if the Senate in the colony's star system has a beta core (`EdictHelper.senateHasBetaCore`). `Edict_Lockdown` (93) and `Edict_Cleanse` (size x 31) override it.
- Daily: if `isPresenceConditionMet()` fails, `removeWithPenalty()`. `isPresenceConditionMet` is true when every `getRequirements()` line is met, so the panel checklist and the check share one source.
- `removeWithPenalty()`: `applyFinishPenalty()` (`getFinishUnrest`: Forced Relocation 3, Cleanse ceil(size/3), Lockdown 1 on auto end), then if runtime not passed, `RecentUnrest +getEarlyRemovalUnrest()` = ceil(remaining days / 31 x 0.5 with a Compounds Senate in range, else 1). Lockdown overrides it: early end adds the early unrest +3 and no finish penalty.
- `getRemovalUnrest()` is the total a removal now would cause; the panel shows it before the confirm.

### Build time display

While `IndEvo_edict_fastBuild` is on the market (`Edict_FastBuild.isActive`), four patches show the halved time. Both use `Edict_FastBuild.getReducedDays` (`days x FAST_BUILD_MULT`, truncated like the vanilla "days left" text, at least 1). Edict apply/unapply are unchanged; the patches only change text.

- Build menu (`IndustryPickerDialog` rows): `FastBuildPickerRowPatch` redirects the days label `create` call in the row's `sizeChanged` to `"<vanilla>  <halved>"`, vanilla days grey, halved days in the vanilla colour, and an `@After` on `renderImpl` draws a grey line over the vanilla days. Rows tagged `parent_item` and 1 day builds are left alone. The label is found by text: it must equal `getSortBuildTime()`.
- Queued industry cards: `FastBuildQueuedIndustryPatch` redirects the `IndustrySpecAPI.getBuildTime()` call in the card's `addCostTimeLabels` (only queued items reach it) and returns the halved days. Number only, no strike (Philipp's call).
- Picking only queues the industry; it starts in `Market.advance` (`BaseIndustry.buildNextInQueue`). `Market.addIndustry` does not reapply conditions, so a started build shows the full time until the next economy step runs `reapplyConditions`, normally within a day.
- Tooltips: every vanilla industry tooltip comes from `createIndustryTooltip(mode, industry)` in the industry card class, an anonymous `StandardTooltipV2Expandable` whose `createImpl` calls `industry.createTooltip`. `FastBuildTooltipPatch` redirects that call for `ADD_INDUSTRY` (build menu), `QUEUED` and `UPGRADE`: it sets the industry's spec build time to the halved days with `IndustrySpecAPI.setBuildTime`, calls down and restores the spec in `finally`. `BaseIndustry.createTooltip` reads the days from the spec (`getBuildTime()`, `api/impl/campaign/econ/impl/BaseIndustry.java:1125`; queued `:1088`), so the vanilla text and highlight show the halved number. The tooltip itself is built as before (industry overrides, `addToIndustryTooltip` listeners, other patches); only spec reads during that call see the halved value. A mod industry that overrides `getBuildTime()` keeps its own value. `IndustrySpecView` delegates `setBuildTime` to the shared spec.
- Upgrade confirm text ("will cost X and take N days"): `FastBuildUpgradeConfirmPatch` redirects the `Industry.getBuildTime()` call in the industry option menu's `actionPerformed` and returns the halved days. The upgrade itself still reads the spec in `startUpgrading`; the edict halves it once it runs.
- Building Menu Overhaul (and Ashes of the Domain, which requires it) dismisses the vanilla build menu and opens its own, so its menu and its tooltips (built in BMO code) show vanilla days. IE does not patch it. The row patch still runs on the dismissed vanilla rows; both patches only read, check `hasField`/`hasMethod` first and call down on every path. Queued cards are vanilla and show the halved days with BMO too. See [Compatibility](../core/COMPATIBILITY.md).

### Senate AI cores

| Core | Effect |
|---|---|
| Alpha | stability +1 on the Senate market |
| Beta | Edict minimum runtime -31 days |
| Gamma | upkeep x0.9 |

## Contracts

- An Edict is a market condition tagged `IndEvo_edict` whose plugin implements `EdictAPI`. A tagged condition with another plugin is skipped with one warning (`EdictHelper.asEdict`). Use `BaseEdict` and guard `apply`/`unapply` with `market.getPrimaryEntity() == null`: the panel preview adds every Edict to a dummy market.
- Text and requirement methods take the colony as parameter and must not read the plugin's own `market` or instance state set in `apply`, or the preview shows the dummy. Only the remaining time and removal values (`getRemainingDays`, `getRemovalUnrest`, `getDaysPassed`) read the plugin's market.
- `Market.addCondition()` calls `plugin.apply()` immediately and `removeCondition()` calls `unapply()` (`obf/campaign/econ/Market.java:1088`, `:1160`).
- One Edict per colony: `EdictHelper.enact()` throws `IllegalStateException` when the colony already has one; the panel disables Enact first.
- Instance fields of the Edict classes are saved with the market. Keep their names and types, even unused ones (`Edict_Cleanse.stabPenalty`), or old saves fail to load.
- Production quality changes from Deadlines and Clearances go on every player market, not only the local one.
- `Edict_FastBuild` reads and writes `BaseIndustry.buildTime` (`api/impl/campaign/econ/impl/BaseIndustry.java:103`) through `StarReflectionUtils.get` and `ReflectionUtils.setWithSuper` ([Utils](../core/UTILS.md)). `apply()` stores each building industry's current time in `originalBuildTimes` (by industry id, saved with the condition) and sets half of it, so custom times such as a [Ruined Infrastructure](RUINED_INFRASTRUCTURE.md) restore are halved, not replaced by a spec time. `unapply()` writes the stored time back only while the industry still holds the halved value (a build started in between keeps its own time) and clears the map. `reapplyConditions` runs unapply before apply, so the halving never stacks.

## Known issues and dormant code

| Item | State |
|---|---|
| Stale class comments | `Edict_LabourRestrictions` and `Edict_OpenMarket` carry the "Substance Legalization" design note |
| Deadlines and Clearances text | Says "Ship quality" / "Local ship quality" while the quality change applies to every player market |
| Remote colony screen | After enacting or removing from a remote colony screen, the conditions row may only refresh when the screen is reopened (untested) |

## Maintenance

Update this doc when an Edict is added, removed or retuned, or the Senate menu rules change. Time tags and `TimeTracker` live in [Utils](../core/UTILS.md); the Compounds item in [Installables](../items/INSTALLABLES.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
